package com.curro.app.data.ml

import com.curro.app.domain.catalog.CatalogFunction
import com.curro.app.domain.catalog.CatalogParam
import com.curro.app.domain.catalog.Fase1Catalog
import com.curro.app.domain.catalog.ParamType
import com.curro.app.domain.model.PromptContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders the prompt for Curro's decision engine — Gemma 4 E2B (base instruction-tuned)
 * since the May 2026 swap (`MlModule.bindFunctionCallEngine` binds [Gemma4FunctionCallEngine]).
 *
 * Gemma 4 has function-calling as a first-class training objective (per Google's
 * blog post on Gemma 4 + the Tau2 benchmark: E2B Gemma 4 = 24.5% vs Gemma 3 27B
 * = 16.2%).  We bypass Gemma 4's proprietary `<|tool_call>...{...}<tool_call|>`
 * output token format and instead instruct it to emit straight JSON via clear
 * prompt instruction — the base IT model handles this reliably even though
 * it's not the chat-template default.
 *
 * Format (multilingual: instruction is English for the model, examples + user
 * query are Spanish):
 *
 * ```
 * You are Curro, a Spanish voice assistant for elderly users. ...
 *
 * Available actions:
 * - tell_time(what?: time|date|day|all): Tells the time, date or day.
 *   "qué hora es" -> {"action":"tell_time","params":{"what":"time"},"confidence":0.95}
 *   "qué día es hoy" -> {"action":"tell_time","params":{"what":"day"},"confidence":0.95}
 * - ...
 *
 * Context:
 * - Current time: 2026-05-17T14:23:00
 * - ...
 *
 * User query (Spanish): qué hora es
 * JSON:
 * ```
 *
 * Determinism: same inputs → same output, byte for byte. Pinned by golden
 * tests against `src/test/resources/golden/prompt_*.txt` (the golden fixtures
 * will need refreshing — May 2026 prompt is structurally different).
 *
 * Sanitisation: `«` and `»` in the input utterance are *replaced* (not
 * stripped) with `'` before interpolation, so they cannot collide with our
 * delimiter.
 */
@Singleton
@Suppress("ktlint:standard:max-line-length", "MaxLineLength")
class FunctionCallPromptBuilder
    @Inject
    constructor() {
        fun build(
            utterance: String,
            ctx: PromptContext,
        ): String {
            val safe = sanitise(utterance)
            return buildString {
                append(HEADER)
                append('\n')
                append(actionsBlock(Fase1Catalog.functions))
                append('\n')
                append(contextBlock(ctx))
                append('\n')
                append("User query (Spanish): ").append(safe).append('\n')
                append("JSON:")
            }
        }

        /** Replace `«` and `»` with `'` so they cannot collide with the delimiter. */
        private fun sanitise(utterance: String): String = utterance.replace('«', '\'').replace('»', '\'')

        private fun actionsBlock(fns: List<CatalogFunction>): String =
            buildString {
                append("Available actions (Spanish examples — match SEMANTIC intent, not literal words):\n")
                for (fn in fns) {
                    append("- ").append(fn.name)
                    if (fn.params.isNotEmpty()) {
                        append('(').append(renderParams(fn.params)).append(')')
                    } else {
                        append("()")
                    }
                    append(": ").append(fn.description).append('\n')
                    // Two concrete I/O examples per function — Gemma 4 generalises
                    // from these reliably; the previous abstract `<nombre>`
                    // placeholders confused FunctionGemma 270M (kept verbatim).
                    fn.voiceExamples.take(EXAMPLES_PER_FUNCTION).forEach { example ->
                        append("  \"").append(example).append("\" -> ")
                        append(exampleJsonFor(fn, example))
                        append('\n')
                    }
                }
            }

        /**
         * Returns a plausible JSON output for a `(functionName, voiceExampleIndex)` pair.
         * Hand-coded as a `Map` so determinism across runs is structural, not lexical —
         * and so detekt's CyclomaticComplexMethod stays happy.
         *
         * Each entry corresponds to one of the function's voice examples (typically
         * index 0 or 1). Missing entries fall through to a generic empty-params JSON.
         */
        private fun exampleJsonFor(
            fn: CatalogFunction,
            example: String,
        ): String {
            val idx = fn.voiceExamples.indexOf(example).takeIf { it >= 0 } ?: return generic(fn)
            return EXAMPLE_JSONS["${fn.name}#$idx"]
                ?.replace("__EXAMPLE__", example) // `calculate` substitutes the user phrase
                ?: generic(fn)
        }

        private fun generic(fn: CatalogFunction): String =
            "{\"action\":\"${fn.name}\",\"params\":{},\"confidence\":0.8}"

        private fun renderParams(params: List<CatalogParam>): String =
            params.joinToString(", ") { p ->
                val optMark = if (p.required) "" else "?"
                val typeStr =
                    when (val t = p.type) {
                        is ParamType.Str -> "string"
                        is ParamType.Int -> "int"
                        is ParamType.Enum -> t.values.joinToString("|")
                    }
                "${p.name}$optMark: $typeStr"
            }

        private fun contextBlock(ctx: PromptContext): String =
            buildString {
                append("Context:\n")
                append("- Current time: ").append(ctx.nowIso).append('\n')
                append("- Unread WhatsApp messages: ")
                    .append(ctx.unreadMessagesSummary.ifBlank { "none" })
                    .append('\n')
                append("- Known contact aliases: ")
                    .append(if (ctx.knownAliases.isEmpty()) "none" else ctx.knownAliases.joinToString("; "))
                    .append('\n')
            }

        private companion object {
            const val EXAMPLES_PER_FUNCTION = 2

            // English instruction header — Gemma 4 IT's function-calling training is
            // predominantly English; the model handles Spanish user queries fine but
            // follows English instructions more reliably for the OUTPUT format.
            // User-facing text (the spoken response Curro produces) is generated
            // by the handlers, NOT this prompt — so the user never sees English.
            const val HEADER =
                "You are Curro, a Spanish voice assistant for an elderly user.\n" +
                    "Given a SPANISH user query, output EXACTLY ONE LINE of compact JSON.\n" +
                    "Shape (action, params, confidence are SIBLINGS at the root):\n" +
                    "  {\"action\":\"<name>\",\"params\":{<key>:<value>,...},\"confidence\":<0.0-1.0>}\n" +
                    "\n" +
                    "Rules (read carefully):\n" +
                    "- ONE line of compact JSON only. No markdown, no fences, no extra text.\n" +
                    "- ROOT keys: exactly three — action, params, confidence. NEVER nest confidence.\n" +
                    "- params keys must match declared param names; values must match declared types.\n" +
                    "- For enum params, use one of the listed enum values verbatim; never invent new ones.\n" +
                    "- Omit optional params that do not apply (do not pass defaults).\n" +
                    "- Pick the action whose semantic intent best matches the query.\n" +
                    "- If unsure, set confidence below 0.3 and pick the closest action.\n" +
                    "- Query is Spanish (Castilian); examples below show typical phrasings.\n"

            // Mapping `functionName#voiceExampleIndex` -> JSON example string.
            // `__EXAMPLE__` placeholder is substituted with the actual voice phrase
            // for `calculate` (where the expression IS the user's phrase).
            val EXAMPLE_JSONS: Map<String, String> =
                mapOf(
                    "tell_time#0" to "{\"action\":\"tell_time\",\"params\":{\"what\":\"time\"},\"confidence\":0.95}",
                    "tell_time#1" to "{\"action\":\"tell_time\",\"params\":{\"what\":\"day\"},\"confidence\":0.95}",
                    "open_app#0" to "{\"action\":\"open_app\",\"params\":{\"app_name\":\"WhatsApp\"},\"confidence\":0.95}",
                    "open_app#1" to "{\"action\":\"open_app\",\"params\":{\"app_name\":\"cámara\"},\"confidence\":0.9}",
                    "calculate#0" to "{\"action\":\"calculate\",\"params\":{\"expression\":\"__EXAMPLE__\"},\"confidence\":0.95}",
                    "calculate#1" to "{\"action\":\"calculate\",\"params\":{\"expression\":\"__EXAMPLE__\"},\"confidence\":0.9}",
                    "help#0" to "{\"action\":\"help\",\"params\":{},\"confidence\":0.95}",
                    "help#1" to "{\"action\":\"help\",\"params\":{},\"confidence\":0.95}",
                    "read_last_whatsapp#0" to "{\"action\":\"read_last_whatsapp\",\"params\":{},\"confidence\":0.9}",
                    "read_last_whatsapp#1" to "{\"action\":\"read_last_whatsapp\",\"params\":{\"sender\":\"Pepito\"},\"confidence\":0.85}",
                    "read_all_unread_whatsapp#0" to "{\"action\":\"read_all_unread_whatsapp\",\"params\":{},\"confidence\":0.9}",
                    "read_all_unread_whatsapp#1" to "{\"action\":\"read_all_unread_whatsapp\",\"params\":{},\"confidence\":0.9}",
                    "call_contact#0" to "{\"action\":\"call_contact\",\"params\":{\"contact\":\"Pepito\"},\"confidence\":0.9}",
                    "call_contact#1" to "{\"action\":\"call_contact\",\"params\":{\"contact\":\"mi hija\"},\"confidence\":0.85}",
                )
        }
    }
