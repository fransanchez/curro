# US-022 — SF-3.4 · `FunctionCallValidator` — JSON Schema validation against the Fase-1 catalog

> **Spec trace:** spec flow 7 (invalid model output handling — no auto-retry,
> friendly fallback, log), spec §4.3 (the contract is a validated JSON
> object), `on-device-llm` skill "Output validation".
> **Master-plan:** SF-3.4
> **Phase:** 3 — FunctionGemma decision layer
> **Depends on:** US-021 (`Fase1Catalog` + `CatalogFunction` types).
> **Size:** M

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `FunctionCallValidator` — JSON Schema validation against the Fase-1 catalog |
| **US ID** | US-022 |
| **Phase** | 3 |
| **Status** | In Progress |
| **Created** | 2026-05-15 |
| **Modified** | 2026-05-15 |
| **PM Owner** | android-product-analyst |
| **Architect** | ondevice-ai-engineer |

---

## 1. Summary

`FunctionCallValidator.parseAndValidate(raw: String): Result<FunctionCall>`
turns FunctionGemma's raw output into a typed `FunctionCall` value or one of
two typed failures: `CurroError.InvalidFunctionCall` (the JSON is broken, the
shape is wrong, or a param's type is off) and
`CurroError.UnknownFunction(name)` (the JSON is valid and well-shaped but the
named action isn't in the current phase's catalog).

**There is no automatic retry on failure** (spec flow 7). A failed parse maps
to a friendly Spanish line — `copy_error_unknown_function` — and a `Log.w`
entry into a logcat-only failed-commands trail (Phase 7 promotes it to Room).

Why this matters for *this* user: the 270M model will produce garbage from
time to time — a malformed JSON, a fenced JSON, an action it invented, a
param type it picked wrongly. Every one of those failures must turn into "Eso
no lo sé hacer todavía. Pulsa el botón…", not a crash and not a stuck
spinner. The validator is the firewall.

---

## 2. Scope

**In scope:**

- `domain/model/FunctionCall.kt` — `data class FunctionCall(action, params,
  confidence)`. (If it already exists from an earlier SF, this SF reuses it;
  see the grep AC.)
- `data/ml/FunctionCallValidator.kt` — `@Singleton class` exposing
  `parseAndValidate(raw: String): Result<FunctionCall>`.
- Exhaustive JVM unit tests (≥ 20 cases) for every malformation listed in the
  algorithm.
- A bullet in the class doc that pins **"no automatic retry — spec flow 7"**.

**Out of scope:**

- Calling the validator from anywhere — that's US-024's smoke loop.
- The friendly-fallback Spanish line — already added in US-019
  (`copy_models_not_ready`) and US-024 (`copy_error_unknown_function`); not
  wired here.
- The failed-commands Room log — Phase 7; for now `Log.w` is enough (US-024
  wires the logcat line).
- Schema generation from `Fase1Catalog` — reading the catalog directly is
  faster and clearer; no JSON-Schema document is generated or persisted.

---

## 3. User Flows

This SF is invisible to the end user. Developer-facing flows:

### Flow 1 — Happy path (valid JSON, valid action, valid params)

1. Engine returns `raw = '{"action":"tell_time","params":{"what":"time"},"confidence":0.92}'`.
2. Validator: trim → no fence → `JSONObject` parse → `action = "tell_time"` →
   found in catalog → `confidence = 0.92` in range → `params = {"what":
   "time"}` matches `tell_time`'s only declared param (`what` is an enum
   matching `time`) → success.
3. Returns `Result.success(FunctionCall("tell_time", mapOf("what" to "time"),
   0.92f))`.

### Flow 2 — Fenced JSON

1. Engine returns:
   ````
   ```json
   {"action":"calculate","params":{"expression":"47 * 8"},"confidence":0.88}
   ```
   ````
2. Validator strips the fence → parses inner JSON → success.
3. Returns `Result.success(FunctionCall("calculate", mapOf("expression" to
   "47 * 8"), 0.88f))`.

### Flow 3 — Invalid JSON

1. Engine returns `'{action: foo}'` (unquoted keys).
2. `JSONObject(stripped)` throws `JSONException`.
3. Returns `Result.failure(CurroError.InvalidFunctionCall)`.

### Flow 4 — Unknown action

1. Engine returns `'{"action":"summon_dragon","params":{},"confidence":0.91}'`.
2. JSON parses; `action` is well-shaped; not in `Fase1Catalog.functions.map {
   it.name }`.
3. Returns `Result.failure(CurroError.UnknownFunction("summon_dragon"))`.

### Flow 5 — Missing required param

1. Engine returns `'{"action":"call_contact","params":{},"confidence":0.90}'`.
2. `call_contact` requires `contact: string`; missing.
3. Returns `Result.failure(CurroError.InvalidFunctionCall)`.

### Flow 6 — Wrong-typed param

1. Engine returns `'{"action":"tell_time","params":{"what":5},"confidence":0.9}'`.
2. `what` is enum-typed (`time|date|day|all`); the JSON value is an integer.
3. Returns `Result.failure(CurroError.InvalidFunctionCall)`.

---

## 4. Function-catalog Impact

**No catalog change.** The validator **reads** `Fase1Catalog.functions`
directly to verify action names, required params, and types. It does not
mutate the catalog or maintain a separate schema document.

---

## 5. FSM States Touched

**None directly.** The validator is a synchronous function. It's called from
US-024's smoke loop, which lives in the `processing` step of a provisional
`Listening → Processing → Speaking/Error → Idle` micro-FSM. The error mapping
("any failure → friendly fallback line") lives in US-024.

---

## 6. Android System Integrations & Permissions

**None.** Uses `org.json.JSONObject` / `org.json.JSONException` — both ship
with the Android SDK and are available on the JVM unit-test classpath
(`testOptions.unitTests.isReturnDefaultValues = true` is unrelated; `JSONObject`
is an actual class implemented in `core.jar`).

**No new permissions.**

---

## 7. On-device-model Impact

The validator is what makes the model's output **safe to dispatch**. Without
it, every handler in Phase 4 would have to defensively parse the same shape;
with it, every handler gets a typed `FunctionCall` and knows the action name
is in the catalog and the params are typed correctly.

**Latency**: microseconds. Trivial against the 500 ms inference budget.

**No automatic retry**: explicitly forbidden by spec flow 7 and the
`on-device-llm` skill "Output validation" §6. Retries loop and burn battery;
the user-facing fallback is honest.

---

## 8. Android Specification

### 8.1 Files added

- `app/src/main/java/com/curro/app/domain/model/FunctionCall.kt` (if not
  already present — grep the codebase first).
- `app/src/main/java/com/curro/app/data/ml/FunctionCallValidator.kt`.
- `app/src/test/java/com/curro/app/data/ml/FunctionCallValidatorTest.kt`.

### 8.2 `FunctionCall` — exact shape

```kotlin
package com.curro.app.domain.model

/**
 * The result of running FunctionGemma + the validator (spec §4.3, flow 7).
 *
 * Pure data. The validator constructs this; handlers consume it; the
 * coordinator carries it from one to the other.
 */
data class FunctionCall(
    /** snake_case; guaranteed to be a name in the current phase's catalog. */
    val action: String,
    /** Parsed and type-validated parameter map. Values are `String`, `Int`, or
     *  `String` (for enum params). Never null; an empty function call has
     *  an empty map. */
    val params: Map<String, Any>,
    /** Model's confidence, validated to be in `[0.0f, 1.0f]`. */
    val confidence: Float,
)
```

### 8.3 `FunctionCallValidator` — algorithm

```kotlin
package com.curro.app.data.ml

import com.curro.app.domain.catalog.CatalogFunction
import com.curro.app.domain.catalog.CatalogParam
import com.curro.app.domain.catalog.Fase1Catalog
import com.curro.app.domain.catalog.ParamType
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses and validates the raw FunctionGemma output against the Fase-1
 * catalog. **Spec flow 7: never retry on failure.** Every failure path returns
 * a typed [CurroError]; the caller (the launcher's smoke loop in US-024)
 * speaks the friendly fallback line and logs the utterance length.
 *
 * Algorithm:
 *   1. Trim the raw string.
 *   2. Strip an outer ```json…``` or ```…``` code fence if present.
 *   3. JSON-parse with `org.json.JSONObject`; JSONException ⇒ [CurroError.InvalidFunctionCall].
 *   4. `action`: must be a non-empty string.
 *   5. `action` must be one of `Fase1Catalog.functions.map { it.name }`;
 *      else [CurroError.UnknownFunction(action)].
 *   6. `confidence`: must be a number in `[0.0f, 1.0f]` and not NaN.
 *   7. `params`: must be a JSON object; missing ⇒ treated as empty.
 *   8. For the matched function:
 *      - every required param present;
 *      - every present param's type matches its declared `ParamType`;
 *      - no extra params beyond the declared ones.
 */
@Singleton
class FunctionCallValidator @Inject constructor() {

    fun parseAndValidate(raw: String): Result<FunctionCall> {
        val stripped = stripFence(raw.trim())
        val obj = try {
            JSONObject(stripped)
        } catch (e: JSONException) {
            return Result.failure(CurroError.InvalidFunctionCall)
        }

        val action = obj.optString("action").takeIf { it.isNotBlank() }
            ?: return Result.failure(CurroError.InvalidFunctionCall)

        val fn = Fase1Catalog.functions.firstOrNull { it.name == action }
            ?: return Result.failure(CurroError.UnknownFunction(action))

        val confidence = readConfidence(obj)
            ?: return Result.failure(CurroError.InvalidFunctionCall)

        val params = readParams(obj, fn)
            ?: return Result.failure(CurroError.InvalidFunctionCall)

        return Result.success(FunctionCall(action, params, confidence))
    }

    // --- helpers ---

    private fun stripFence(s: String): String {
        // Matches:
        //   ```json\n…\n```   or   ```\n…\n```   (with possible leading/trailing whitespace).
        // The body is captured non-greedily; we strip the surrounding fence.
        val fence = Regex("^```(?:json)?\\s*\\n(.*?)\\n```\\s*$", RegexOption.DOT_MATCHES_ALL)
        return fence.find(s)?.groupValues?.get(1)?.trim() ?: s
    }

    private fun readConfidence(obj: JSONObject): Float? {
        if (!obj.has("confidence")) return null
        val v = obj.opt("confidence")
        val f = when (v) {
            is Number -> v.toFloat()
            else -> return null
        }
        if (f.isNaN() || f < 0f || f > 1f) return null
        return f
    }

    private fun readParams(obj: JSONObject, fn: CatalogFunction): Map<String, Any>? {
        val paramsJson = when {
            !obj.has("params") -> JSONObject() // treat missing as empty
            else -> obj.optJSONObject("params") ?: return null
        }

        val declared = fn.params.associateBy { it.name }

        // Extra params → reject.
        for (key in paramsJson.keys()) {
            if (key !in declared) return null
        }

        // Required params present + types match.
        val out = mutableMapOf<String, Any>()
        for (param in fn.params) {
            if (!paramsJson.has(param.name)) {
                if (param.required) return null
                continue // optional param absent — fine.
            }
            val raw = paramsJson.opt(param.name)
            val typed = coerce(raw, param.type) ?: return null
            out[param.name] = typed
        }
        return out
    }

    private fun coerce(raw: Any?, type: ParamType): Any? = when (type) {
        is ParamType.Str -> (raw as? String)?.takeIf { it.isNotEmpty() }
        is ParamType.Int -> when (raw) {
            is Int -> raw
            is Long -> if (raw in Int.MIN_VALUE..Int.MAX_VALUE) raw.toInt() else null
            else -> null
        }
        is ParamType.Enum -> (raw as? String)?.takeIf { it in type.values }
    }
}
```

### 8.4 Tests — exhaustive table

`FunctionCallValidatorTest.kt` — JVM, JUnit 5, ≥ 20 cases. Parameterised where
it helps. (Snippet — the developer fills in the @ParameterizedTest body once
the table is in place.)

```kotlin
package com.curro.app.data.ml

import com.curro.app.domain.model.CurroError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class FunctionCallValidatorTest {

    private val v = FunctionCallValidator()

    // ---------- Good (7) ----------
    @Test fun `good - tell_time with what time`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{"what":"time"},"confidence":0.92}""")
        val call = r.getOrThrow()
        assertEquals("tell_time", call.action)
        assertEquals(mapOf("what" to "time"), call.params)
        assertEquals(0.92f, call.confidence)
    }

    @Test fun `good - tell_time with empty params object is success`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":0.7}""")
        val call = r.getOrThrow()
        assertEquals(emptyMap<String, Any>(), call.params)
    }

    @Test fun `good - help with no topic`() {
        val r = v.parseAndValidate("""{"action":"help","params":{},"confidence":0.95}""")
        assertTrue(r.isSuccess)
    }

    @Test fun `good - help with topic`() {
        val r = v.parseAndValidate("""{"action":"help","params":{"topic":"mensajes"},"confidence":0.8}""")
        assertEquals(mapOf("topic" to "mensajes"), r.getOrThrow().params)
    }

    @Test fun `good - open_app with app_name`() {
        val r = v.parseAndValidate("""{"action":"open_app","params":{"app_name":"WhatsApp"},"confidence":0.97}""")
        assertEquals(mapOf("app_name" to "WhatsApp"), r.getOrThrow().params)
    }

    @Test fun `good - calculate with expression`() {
        val r = v.parseAndValidate("""{"action":"calculate","params":{"expression":"47 * 8"},"confidence":0.85}""")
        assertEquals(mapOf("expression" to "47 * 8"), r.getOrThrow().params)
    }

    @Test fun `good - call_contact with contact`() {
        val r = v.parseAndValidate("""{"action":"call_contact","params":{"contact":"mi hija"},"confidence":0.88}""")
        assertEquals(mapOf("contact" to "mi hija"), r.getOrThrow().params)
    }

    @Test fun `good - read_last_whatsapp no sender`() {
        val r = v.parseAndValidate("""{"action":"read_last_whatsapp","params":{},"confidence":0.93}""")
        assertTrue(r.isSuccess)
    }

    @Test fun `good - read_last_whatsapp with sender`() {
        val r = v.parseAndValidate("""{"action":"read_last_whatsapp","params":{"sender":"Pepito"},"confidence":0.91}""")
        assertEquals(mapOf("sender" to "Pepito"), r.getOrThrow().params)
    }

    @Test fun `good - read_all_unread_whatsapp no params declared`() {
        val r = v.parseAndValidate("""{"action":"read_all_unread_whatsapp","params":{},"confidence":0.94}""")
        assertTrue(r.isSuccess)
    }

    // ---------- Code-fence stripping (2) ----------
    @Test fun `fence - json fence stripped`() {
        val raw = "```json\n{\"action\":\"tell_time\",\"params\":{},\"confidence\":0.9}\n```"
        assertTrue(v.parseAndValidate(raw).isSuccess)
    }

    @Test fun `fence - plain fence stripped`() {
        val raw = "```\n{\"action\":\"tell_time\",\"params\":{},\"confidence\":0.9}\n```"
        assertTrue(v.parseAndValidate(raw).isSuccess)
    }

    // ---------- Bad (12+) ----------
    @Test fun `bad - unquoted keys is not JSON`() {
        val r = v.parseAndValidate("""{action: foo}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - missing action key`() {
        val r = v.parseAndValidate("""{"params":{},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - empty action`() {
        val r = v.parseAndValidate("""{"action":"","params":{},"confidence":0.5}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - unknown action returns UnknownFunction`() {
        val r = v.parseAndValidate("""{"action":"summon_dragon","params":{},"confidence":0.9}""")
        val err = r.exceptionOrNull()
        assertTrue(err is CurroError.UnknownFunction)
        assertEquals("summon_dragon", (err as CurroError.UnknownFunction).name)
    }

    @Test fun `bad - call_contact missing required contact`() {
        val r = v.parseAndValidate("""{"action":"call_contact","params":{},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - tell_time what wrong type int`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{"what":5},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - call_contact contact wrong type int`() {
        val r = v.parseAndValidate("""{"action":"call_contact","params":{"contact":42},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - tell_time extra param`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{"what":"time","frobnicate":true},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - confidence above 1`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":1.5}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - confidence below 0`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":-0.1}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - confidence NaN`() {
        // JSONObject does not parse NaN as a number; verify the path nonetheless via JSON null.
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":null}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - confidence not a number`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":"high"}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - tell_time what not in enum values`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{"what":"yesterday"},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test fun `bad - params not an object`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":"oops","confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }
}
```

That's 27 cases — comfortably above the 20 floor.

### 8.5 No-mutation defensive read

The validator reads `Fase1Catalog.functions` once per call. Tests can verify
this is non-destructive with a simple "size before == size after" assert in
a teardown block — but in practice `Fase1Catalog.functions` is a `val` of an
immutable `List<CatalogFunction>` (and `CatalogFunction` is a `data class`),
so mutation is structurally impossible. Document the assumption in the
validator's class doc.

---

## 9. Senior-UX & Copy

No user-facing copy in this SF. The friendly-fallback line
(`copy_error_unknown_function`) is wired in US-024.

---

## 10. Acceptance Criteria

Mirroring PRD entry:

- [ ] `domain/model/FunctionCall.kt` exists with the exact shape above
  (`data class` of action: String, params: Map<String, Any>, confidence:
  Float). If already present from an earlier SF, verify it has this shape;
  do not change without a brief refresh.
- [ ] `data/ml/FunctionCallValidator.kt` exists, `@Singleton`, `@Inject`-able.
- [ ] The 8-step algorithm is implemented as documented in §8.3.
- [ ] Code fence stripping handles `\`\`\`json\n…\n\`\`\`` and `\`\`\`\n…\n\`\`\``.
- [ ] **No automatic retry** — explicit comment in the class doc + no retry
  logic anywhere in the file.
- [ ] **At least 20 unit tests** in `FunctionCallValidatorTest.kt` covering
  every malformation listed in PRD §US-022. The brief above ships 27; the
  developer can keep all of them.
- [ ] The validator reads `Fase1Catalog.functions` directly; no separate
  schema document is generated.
- [ ] No new permissions, no manifest change, no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green.

---

## 11. Performance Considerations

- One `JSONObject` construction per call; one `Regex` match for the fence
  strip; one linear scan of `Fase1Catalog.functions` (7 items). Microseconds.
- Allocations per call: ~1 `JSONObject`, ~1 `MutableMap` for the params, plus
  the `FunctionCall` data class. Negligible.
- The `Regex` is a `companion object`-level `val`? — keep it as a local `val`
  inside `stripFence` for clarity; the JIT inlines it.

---

## 12. Testing Requirements

- [ ] **Unit**: ≥ 20 cases in `FunctionCallValidatorTest.kt`. The 27-case
  table above is the recommended set.
- [ ] **Drift check**: a test asserting that for every function in
  `Fase1Catalog.functions`, a canonical "happy path" JSON parses cleanly.
  This catches the "you added a function to the catalog but forgot to think
  about whether it parses" failure mode. Loop body:
  ```kotlin
  @Test fun `every catalog function has a happy-path validator pass`() {
      Fase1Catalog.functions.forEach { fn ->
          val raw = buildCanonicalJson(fn) // helper builds {action, params with required ones, confidence: 0.9}
          val r = v.parseAndValidate(raw)
          assertTrue(r.isSuccess, "Canonical JSON for ${fn.name} failed: ${r.exceptionOrNull()}")
      }
  }
  ```
- [ ] **No instrumented test** — the validator is pure JVM.
- [ ] **No manual on-device gate** specifically for this SF — US-024 exercises
  the validator's failure paths on the device (the "tradúceme esto" → fallback
  AC).

---

## 13. Implementation Notes

### Why `org.json.JSONObject` instead of Moshi / Kotlinx-Serialization

`org.json.*` ships with the Android SDK and is on the JVM unit-test classpath
without any extra dependency. Moshi / Kotlinx-Serialization would each add
~200 KB to the APK for a use case this small. The validator's failure modes
are mapped explicitly (`JSONException` → `InvalidFunctionCall`); the lower-level
API actually fits the explicit-error style better than a typed deserializer
would.

### `coerce` for `Long` → `Int`

`JSONObject.opt("foo")` returns `Long` for whole-number JSON literals. The
validator accepts a `Long` that fits in `Int` range and downcasts. This
prevents a `tell_time` with a hypothetical int param from failing on
`"what":42` when the catalog declared `Int`. (Currently no Fase-1 function uses
`ParamType.Int`, but `set_volume` (Fase 2) does — keeping the coercion ready
costs nothing and avoids a Phase-2 follow-up.)

### Empty-string params

The validator rejects empty strings for `ParamType.Str` (`takeIf {
it.isNotEmpty() }`). This makes `{"action": "call_contact", "params":
{"contact": ""}, "confidence": 0.9}` invalid — which is the right call: an
empty contact name can't be resolved and is almost certainly a model error.

### `confidence` null handling

The `JSONObject.opt` API returns `JSONObject.NULL` (a sentinel) for explicit
`null` values, NOT Java `null`. The validator's `is Number` branch is exhaustive
on `Number`, so `JSONObject.NULL` (which is `Object`, not `Number`) falls to
the `else` branch → returns `null` → `InvalidFunctionCall`. The test
`bad - confidence NaN` exercises this path with an explicit `"confidence":null`.

### Order of operations

1. Verify `domain/model/FunctionCall.kt` doesn't exist; if it does, refresh
   its shape if needed (probably fine).
2. Add `data/ml/FunctionCallValidator.kt`.
3. Add `app/src/test/.../FunctionCallValidatorTest.kt` with the 27 cases.
4. Add the drift-check test.
5. Run `./gradlew testDebugUnitTest` — all green.
6. Run `./gradlew assembleDebug ktlintCheck detektDebug` — green.

### Commit scope

`feat(catalog)` — the validator is the schema's enforcement, so the same
scope as US-021. Alternative: `feat(llm)`; either is defensible.

---

## 14. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-15 | android-product-analyst | Initial draft for Phase-3 PM batch. |
