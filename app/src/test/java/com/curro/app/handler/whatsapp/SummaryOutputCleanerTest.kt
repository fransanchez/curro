package com.curro.app.handler.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SummaryOutputCleaner] (US-062 / SF-9.3).
 *
 * Each test pins one artefact-stripping rule. The cleaner is intentionally
 * conservative; aggressive cleaning would risk dropping useful fragments.
 */
@DisplayName("SummaryOutputCleaner (SF-9.3)")
class SummaryOutputCleanerTest {
    private val cleaner = SummaryOutputCleaner()

    @Test
    fun `clean strips leading and trailing whitespace`() {
        assertEquals(
            "De Pepito: vale",
            cleaner.clean("\n\n   De Pepito: vale   \n\n"),
        )
    }

    @Test
    fun `clean strips surrounding straight quotes`() {
        assertEquals(
            "De Pepito: vale",
            cleaner.clean("\"De Pepito: vale\""),
        )
    }

    @Test
    fun `clean strips surrounding curly quotes`() {
        assertEquals(
            "De Pepito: vale",
            cleaner.clean("“De Pepito: vale”"),
        )
    }

    @Test
    fun `clean strips leading bullet dash and rejoins lines with period space`() {
        val raw =
            """
            - De Pepito: te espera a las siete
            - De Lucía: te llama mañana
            """.trimIndent()
        assertEquals(
            "De Pepito: te espera a las siete. De Lucía: te llama mañana",
            cleaner.clean(raw),
        )
    }

    @Test
    fun `clean strips leading Resumen header before first sender block`() {
        val raw =
            """
            Resumen:
            De Pepito: te espera a las siete
            """.trimIndent()
        assertEquals(
            "De Pepito: te espera a las siete",
            cleaner.clean(raw),
        )
    }

    @Test
    fun `clean collapses runs of whitespace to single space`() {
        assertEquals(
            "De Pepito: vale, tres palabras",
            cleaner.clean("De  Pepito:   vale,    tres   palabras"),
        )
    }

    @Test
    fun `clean strips asterisk bullets and rejoins`() {
        val raw =
            """
            * De Pepito: vale
            * De Lucía: hola
            """.trimIndent()
        assertEquals(
            "De Pepito: vale. De Lucía: hola",
            cleaner.clean(raw),
        )
    }

    @Test
    fun `clean strips Resumen por persona header variant`() {
        val raw = "Resumen por persona:\nDe Pepito: ok"
        assertEquals("De Pepito: ok", cleaner.clean(raw))
    }
}
