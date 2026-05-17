package com.curro.app.handler.whatsapp

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strips known LLM artefacts from the large-text model's raw summary output
 * (US-062 / SF-9.3). The backing model is Gemma 4 E2B since the May 2026 swap
 * — see `Gemma3nEngine` KDoc — but the artefact list (echoed headers, bullet
 * markers, surrounding quotes) is shared family behaviour, so this cleaner is
 * model-agnostic by design. Extend [ECHOED_HEADERS] if Gemma 4 surfaces new
 * boilerplate the smoke test catches.
 *
 * Applied to every successful `TextGenEngine.generate` result before TTS.
 * Conservative — only removes things the model is *known* to add (quotes,
 * bullet markers, echoed headers, excess whitespace). Does NOT validate
 * semantic correctness; for the prototype that's acceptable (a single-user
 * validation instrument).
 *
 * Testing: golden-string cases in `SummaryOutputCleanerTest`.
 */
@Singleton
class SummaryOutputCleaner
    @Inject
    constructor() {
        fun clean(raw: String): String {
            var out = raw.trim()

            // Strip echoed headers.
            ECHOED_HEADERS.forEach { header ->
                if (out.startsWith(header, ignoreCase = true)) {
                    out = out.removePrefix(header).trimStart(':', ' ', '\n')
                }
            }

            // Split by newline, strip leading bullets per line, then re-join with ". ".
            val lines =
                out
                    .lines()
                    .map { line -> line.trim().removePrefix("- ").removePrefix("* ").trim() }
                    .filter { it.isNotBlank() }
            out = lines.joinToString(". ")

            // Strip surrounding quotes (straight + curly).
            SURROUNDING_QUOTES.forEach { (open, close) ->
                if (out.startsWith(open) && out.endsWith(close)) {
                    out = out.removePrefix(open.toString()).removeSuffix(close.toString())
                }
            }

            // Collapse runs of whitespace (newlines, tabs, multiple spaces) to one space.
            out = WHITESPACE_RUN.replace(out, " ").trim()

            return out
        }

        private companion object {
            val ECHOED_HEADERS = listOf("Resumen por persona", "Resumen", "Salida")
            val SURROUNDING_QUOTES = listOf('"' to '"', '“' to '”', '\'' to '\'')
            val WHITESPACE_RUN = Regex("\\s+")
        }
    }
