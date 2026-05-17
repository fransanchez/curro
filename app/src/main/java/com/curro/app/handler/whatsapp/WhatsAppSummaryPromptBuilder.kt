package com.curro.app.handler.whatsapp

import com.curro.app.domain.model.WhatsAppMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the large-text-model prompt for the WhatsApp summarisation path
 * (US-062 / SF-9.3). Backed today by Gemma 4 E2B via [Gemma3nEngine] (see
 * its KDoc — the class name predates the May 2026 Gemma 3n → Gemma 4 swap).
 *
 * Groups messages by sender, sorted most-recent-active-first (same ordering as
 * `ReadAllUnreadWhatsAppHandler`'s existing flow so the summary mirrors the
 * verbatim-read flow). Wraps in a Spanish system prompt instructing Curro's
 * voice (colloquial, Andalusian, factual — no invention). The Gemma 4 IT
 * variant accepts the same chat-template family as Gemma 3 IT, so the prompt
 * shape did not need to change during the swap.
 *
 * **PII**: the prompt DOES contain message bodies and sender names. This is
 * unavoidable — that's the input to the summary. The prompt NEVER leaves the
 * device (the large-text model runs locally; see `Gemma3nEngine` PII boundary
 * docstring).
 *
 * Testing: golden-string match in `WhatsAppSummaryPromptBuilderTest`.
 */
@Singleton
class WhatsAppSummaryPromptBuilder
    @Inject
    constructor() {
        fun build(messages: List<WhatsAppMessage>): String {
            if (messages.isEmpty()) return EMPTY_FALLBACK_PROMPT
            val groups =
                messages
                    .groupBy { it.sender }
                    .map { (sender, msgs) -> sender to msgs.sortedBy { it.timestamp } }
                    .sortedByDescending { (_, msgs) -> msgs.maxOf { it.timestamp } }

            val context =
                groups.joinToString(separator = "\n\n") { (sender, msgs) ->
                    buildString {
                        append("De ").append(sender).append(":\n")
                        msgs.forEach { msg ->
                            append("- ").append(msg.text).append('\n')
                        }
                    }.trimEnd()
                }
            return PROMPT_TEMPLATE.format(context)
        }

        private companion object {
            // Pinned template — golden-string-tested.
            val PROMPT_TEMPLATE =
                """
                Eres Curro, un asistente para una persona mayor andaluza. Hablas en castellano
                coloquial, cálido y cercano, sin inventar nada.

                Te paso una lista de mensajes nuevos de WhatsApp, agrupados por remitente.
                Resume cada remitente en una sola frase corta. Empieza cada frase con
                "De <nombre>:" y termínala con punto. Separa los remitentes por un espacio.
                No añadas saludos, despedidas, ni comentarios tuyos. Solo el resumen.

                Mensajes:
                %s

                Resumen:
                """.trimIndent()

            // Defensive — the handler never calls this with an empty list, but the
            // builder doesn't crash either.
            const val EMPTY_FALLBACK_PROMPT = "Resume: (sin mensajes). Salida:"
        }
    }
