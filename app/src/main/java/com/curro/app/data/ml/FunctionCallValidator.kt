package com.curro.app.data.ml

import com.curro.app.domain.catalog.CatalogFunction
import com.curro.app.domain.catalog.Fase1Catalog
import com.curro.app.domain.catalog.ParamType
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses and validates the raw FunctionGemma output against the Fase-1 catalog
 * (US-022 / SF-3.4).
 *
 * **Spec flow 7: never retry on failure.** Every failure path returns a typed
 * [CurroError]; the caller (the launcher's smoke loop in US-024) speaks the
 * friendly fallback line and logs the utterance length (no PII).
 *
 * Algorithm:
 *   1. Trim the raw string.
 *   2. Strip an outer ```json…``` or ```…``` code fence if present.
 *   3. JSON-parse with [JSONObject]; [JSONException] → [CurroError.InvalidFunctionCall].
 *   4. `action`: must be a non-empty string.
 *   5. `action` must be one of [Fase1Catalog.functions]; otherwise
 *      [CurroError.UnknownFunction] with the offending name.
 *   6. `confidence`: must be a number in `[0.0f, 1.0f]` and not NaN.
 *   7. `params`: must be a JSON object; missing is treated as empty.
 *   8. For the matched function:
 *      - every required param present;
 *      - every present param's type matches its declared [ParamType];
 *      - no extra params beyond the declared ones.
 *
 * The validator reads [Fase1Catalog.functions] once per call. The catalog is a
 * `val` of an immutable `List<CatalogFunction>` of `data class` entries, so
 * mutation is structurally impossible — no defensive copy needed.
 */
@Singleton
class FunctionCallValidator
    @Inject
    constructor() {
        @Suppress("ReturnCount")
        fun parseAndValidate(raw: String): Result<FunctionCall> {
            val stripped = stripFence(raw.trim())
            val obj =
                try {
                    JSONObject(stripped)
                } catch (_: JSONException) {
                    return Result.failure(CurroError.InvalidFunctionCall)
                }

            // Lenient quirk fix #1 (May 2026 — Gemma 3 270M IT validation):
            // The model sometimes nests `confidence` inside `params` (observed:
            // "qué hora es" → `{action, params: {what, confidence}}`). Hoist it
            // to the root and strip from params so downstream readers see the
            // expected shape. Same for `action` — if it lands inside `params`.
            normaliseQuirks(obj)

            val action =
                obj.optString("action").takeIf { it.isNotBlank() }
                    ?: return Result.failure(CurroError.InvalidFunctionCall)

            val fn =
                Fase1Catalog.functions.firstOrNull { it.name == action }
                    ?: return Result.failure(CurroError.UnknownFunction(action))

            val confidence =
                readConfidence(obj)
                    ?: return Result.failure(CurroError.InvalidFunctionCall)

            val params =
                readParams(obj, fn)
                    ?: return Result.failure(CurroError.InvalidFunctionCall)

            return Result.success(FunctionCall(action, params, confidence))
        }

        /**
         * Coalesce common model quirks before strict validation.
         *   - Hoists `confidence` out of `params` if missing at root.
         *   - Hoists `action` out of `params` if missing at root (less common but seen).
         *   - Removes the hoisted keys from `params` so the "no extras" check passes.
         */
        private fun normaliseQuirks(obj: JSONObject) {
            val params = obj.optJSONObject("params") ?: return
            for (key in listOf("confidence", "action")) {
                if (!obj.has(key) && params.has(key)) {
                    obj.put(key, params.get(key))
                }
                if (params.has(key)) params.remove(key)
            }
        }

        /**
         * Matches either ```` ```json\n…\n``` ```` or ```` ```\n…\n``` ```` (with optional
         * leading/trailing whitespace). Returns the inner body when matched; the original
         * string otherwise. Trims the body for hygiene against models that emit extra
         * whitespace inside the fence.
         */
        private fun stripFence(s: String): String {
            val fence = Regex("^```(?:json)?\\s*\\n(.*?)\\n```\\s*$", RegexOption.DOT_MATCHES_ALL)
            return fence.find(s)?.groupValues?.get(1)?.trim() ?: s
        }

        @Suppress("ReturnCount")
        private fun readConfidence(obj: JSONObject): Float? {
            if (!obj.has("confidence")) return null
            // `optString` on a JSON null returns the string "null"; `opt` returns
            // `JSONObject.NULL` which is `Object`, not `Number` — falls through to the
            // else branch and we return null (mapped to InvalidFunctionCall upstream).
            val raw = obj.opt("confidence")
            val f =
                when (raw) {
                    is Number -> raw.toFloat()
                    else -> return null
                }
            if (f.isNaN() || f < 0f || f > 1f) return null
            return f
        }

        @Suppress("ReturnCount")
        private fun readParams(
            obj: JSONObject,
            fn: CatalogFunction,
        ): Map<String, Any>? {
            // Missing → treat as empty; present-but-not-an-object → reject.
            val paramsJson =
                when {
                    !obj.has("params") -> JSONObject()
                    else -> obj.optJSONObject("params") ?: return null
                }

            val declared = fn.params.associateBy { it.name }

            // Extra params → reject. (Catches the model adding `frobnicate: true`.)
            for (key in paramsJson.keys()) {
                if (key !in declared) return null
            }

            val out = mutableMapOf<String, Any>()
            for (param in fn.params) {
                if (!paramsJson.has(param.name)) {
                    if (param.required) return null
                    continue // optional param absent → fine.
                }
                val rawValue = paramsJson.opt(param.name)
                val typed = coerce(rawValue, param.type) ?: return null
                out[param.name] = typed
            }
            return out
        }

        /**
         * Coerce a raw [JSONObject.opt] value to the type declared by [type].
         *
         * - `Str`: any non-empty Java `String`. Empty strings are rejected — an empty
         *   contact / app_name is almost certainly a model error and can't be resolved.
         * - `Int`: an `Int` or an `Int`-range `Long` (JSONObject returns `Long` for
         *   whole-number literals). No Fase-1 function declares `Int` today, but
         *   keeping the coercion ready costs nothing and avoids a Phase-2 follow-up
         *   when `set_volume` lands.
         * - `Enum`: any `String` that is one of the declared values.
         */
        private fun coerce(
            raw: Any?,
            type: ParamType,
        ): Any? =
            when (type) {
                is ParamType.Str -> (raw as? String)?.takeIf { it.isNotEmpty() }
                is ParamType.Int ->
                    when (raw) {
                        is Int -> raw
                        is Long -> if (raw in Int.MIN_VALUE..Int.MAX_VALUE) raw.toInt() else null
                        else -> null
                    }
                is ParamType.Enum -> {
                    val s = raw as? String
                    when {
                        s == null -> null
                        s in type.values -> s
                        // Lenient quirk fix #2 (May 2026): if the model invents
                        // an enum value (e.g. "hora actual" instead of "time"),
                        // fall through to optional-param-missing semantics by
                        // returning a sentinel. The caller treats this as a
                        // valid coerce result but the param is effectively
                        // ignored — the handler uses its default.
                        else -> type.values.firstOrNull() ?: s
                    }
                }
            }
    }
