package com.curro.app.handler.whatsapp

import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.model.WhatsAppMessage.Classification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [WhatsAppSummaryPromptBuilder] (US-062 / SF-9.3).
 *
 * Golden-string checks for the prompt template + grouping behaviour.
 * The prompt is FunctionGemma's input to summarisation; subtle drift here
 * would silently degrade the summary quality.
 */
@DisplayName("WhatsAppSummaryPromptBuilder (SF-9.3)")
class WhatsAppSummaryPromptBuilderTest {
    private val builder = WhatsAppSummaryPromptBuilder()

    private fun msg(
        sender: String,
        text: String,
        timestamp: Long = 1_000L,
        classification: Classification = Classification.TEXT,
    ) = WhatsAppMessage(
        key = "k-$sender-$timestamp",
        sender = sender,
        chatTitle = sender,
        text = text,
        isGroup = false,
        timestamp = timestamp,
        classification = classification,
    )

    @Test
    fun `build single sender single message produces expected template`() {
        val prompt =
            builder.build(
                listOf(msg("Pepito", "Te espero a las siete")),
            )
        val expected =
            """
            Eres Curro, un asistente para una persona mayor andaluza. Hablas en castellano
            coloquial, cálido y cercano, sin inventar nada.

            Te paso una lista de mensajes nuevos de WhatsApp, agrupados por remitente.
            Resume cada remitente en una sola frase corta. Empieza cada frase con
            "De <nombre>:" y termínala con punto. Separa los remitentes por un espacio.
            No añadas saludos, despedidas, ni comentarios tuyos. Solo el resumen.

            Mensajes:
            De Pepito:
            - Te espero a las siete

            Resumen:
            """.trimIndent()
        assertEquals(expected, prompt)
    }

    @Test
    fun `build multiple senders groups by sender most recent first`() {
        // Pepito at t=1000 + 2000; Lucía at t=4000 (most recent → first group);
        // Carmen at t=3000 (second).
        val prompt =
            builder.build(
                listOf(
                    msg("Pepito", "Hola", timestamp = 1_000L),
                    msg("Pepito", "Y trae pan", timestamp = 2_000L),
                    msg("Lucía", "Te llamo mañana", timestamp = 4_000L),
                    msg("Carmen", "¿Cómo estás?", timestamp = 3_000L),
                ),
            )
        // Expect Lucía → Carmen → Pepito ordering in the body.
        val luciaIdx = prompt.indexOf("De Lucía:")
        val carmenIdx = prompt.indexOf("De Carmen:")
        val pepitoIdx = prompt.indexOf("De Pepito:")
        assertTrue(luciaIdx >= 0 && carmenIdx >= 0 && pepitoIdx >= 0, "all senders should appear")
        assertTrue(luciaIdx < carmenIdx, "Lucía (t=4000) before Carmen (t=3000)")
        assertTrue(carmenIdx < pepitoIdx, "Carmen (t=3000) before Pepito (t=2000)")
        // Within Pepito's group, chronological (Hola before Y trae pan).
        val pepitoBlock = prompt.substring(pepitoIdx)
        val holaIdx = pepitoBlock.indexOf("- Hola")
        val panIdx = pepitoBlock.indexOf("- Y trae pan")
        assertTrue(holaIdx >= 0 && panIdx >= 0)
        assertTrue(holaIdx < panIdx, "within-group chronological order")
    }

    @Test
    fun `build includes system prompt header`() {
        val prompt = builder.build(listOf(msg("Pepito", "x")))
        assertTrue(prompt.contains("Eres Curro"), "system prompt 'Eres Curro' line missing")
        assertTrue(prompt.contains("castellano"), "voice instruction 'castellano' missing")
        assertTrue(prompt.contains("coloquial"), "voice instruction 'coloquial' missing")
        assertTrue(prompt.contains("sin inventar nada"), "no-invention rule missing")
        assertTrue(prompt.contains("Resumen:"), "trailing 'Resumen:' anchor missing")
    }

    @Test
    fun `build empty list defensive returns safe fallback`() {
        val prompt = builder.build(emptyList())
        assertEquals("Resume: (sin mensajes). Salida:", prompt)
    }

    @Test
    fun `build preserves accents enye and special characters`() {
        val prompt =
            builder.build(
                listOf(
                    msg("Iñaki", "El café cuesta 1.50€ — ¿vienes? Sí o sí."),
                ),
            )
        assertTrue(prompt.contains("De Iñaki:"), "ñ preserved in sender")
        assertTrue(prompt.contains("café"), "accent preserved")
        assertTrue(prompt.contains("1.50€"), "currency preserved")
        assertTrue(prompt.contains("¿"), "inverted question mark preserved")
        assertTrue(prompt.contains("—"), "em-dash preserved")
    }
}
