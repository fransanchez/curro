package com.curro.app.data.ml

import com.curro.app.domain.catalog.CatalogFunction
import com.curro.app.domain.catalog.CatalogParam
import com.curro.app.domain.catalog.Fase1Catalog
import com.curro.app.domain.catalog.ParamType
import com.curro.app.domain.model.PromptContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders FunctionGemma's prompt: a header, the current-phase catalog, minimal
 * context, and the user's utterance.
 *
 * Determinism is a contract. Given the same inputs, the output is identical —
 * pinned by byte-for-byte golden tests against
 * `src/test/resources/golden/prompt_*.txt`.
 *
 * Token budget: < 600 model-tokens on the empty-context happy path (estimated
 * via word-count × 1.3). Every line costs accuracy on a 270M model; do not
 * add lines without measurement.
 *
 * Sanitisation: `«` and `»` in the input utterance are *replaced* (not stripped)
 * with `'` before interpolation, so they cannot collide with the delimiter.
 * Replacing preserves the audible content; stripping risks losing tokens the
 * user actually said.
 */
@Singleton
class FunctionCallPromptBuilder
    @Inject
    constructor() {
        /**
         * Render the full prompt for FunctionGemma.
         *
         * Pure: same inputs → same output, byte for byte. Microseconds; no
         * coroutine, no IO. The caller (US-020's engine) hands this string to
         * `LlmInference.generateResponse`.
         */
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
                append("Frase del usuario: «").append(safe).append("»\n")
                append('\n')
                append("JSON:")
            }
        }

        /** Replace `«` and `»` with `'` so they cannot collide with the delimiter. */
        private fun sanitise(utterance: String): String = utterance.replace('«', '\'').replace('»', '\'')

        private fun actionsBlock(fns: List<CatalogFunction>): String =
            buildString {
                append("Acciones disponibles:\n")
                for (fn in fns) {
                    append("- ").append(fn.name)
                    if (fn.params.isNotEmpty()) {
                        append('(').append(renderParams(fn.params)).append(')')
                    } else {
                        append("()")
                    }
                    append(": ").append(fn.description).append('\n')
                    append("  Ejemplos: ")
                    append(fn.voiceExamples.joinToString(", ") { "\"$it\"" })
                    append('\n')
                }
            }

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
                append("Contexto:\n")
                append("- Hora actual: ").append(ctx.nowIso).append('\n')
                append("- Mensajes sin leer: ")
                    .append(ctx.unreadMessagesSummary.ifBlank { "ninguno" })
                    .append('\n')
                append("- Alias conocidos: ")
                    .append(if (ctx.knownAliases.isEmpty()) "ninguno" else ctx.knownAliases.joinToString("; "))
                    .append('\n')
            }

        private companion object {
            // Note: terminates with `\n` so the `append('\n')` in `build` produces the
            // blank line the golden tests expect. Every other block in `build` is
            // already `\n`-terminated by construction.
            const val HEADER =
                "Eres Curro. Dada una frase del usuario, devuelves UN ÚNICO JSON con la forma:\n" +
                    "{\"action\": \"<nombre>\", \"params\": {…}, \"confidence\": <0.0-1.0>}\n" +
                    "\n" +
                    "Si la frase no encaja con ninguna acción, devuelve confidence < 0.3 con la mejor adivinanza.\n"
        }
    }
