package com.curro.app.data.failures

import com.curro.app.data.local.FailedCommandEntity
import com.curro.app.data.local.FailureKind
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-8.7 (US-057) — [FailedCommandAnonymiser] unit tests.
 *
 * Verifies that the formatted output NEVER contains transcripts and always includes the
 * expected safe fields (time, kind, details).
 */
@DisplayName("FailedCommandAnonymiser (SF-8.7)")
class FailedCommandAnonymiserTest {
    private val subject = FailedCommandAnonymiser()

    @Suppress("MagicNumber")
    private val entity =
        FailedCommandEntity(
            id = 1L,
            transcript = "llama a mi hijo Pedro",
            kind = FailureKind.INVALID_OUTPUT,
            details = "SyntaxError in JSON",
            timestampMs = 1_747_000_000_000L,
            sent = false,
        )

    @Test
    fun `transcript is never included in formatted output`() {
        val output = subject.format(listOf(entity))
        assertFalse(output.contains(entity.transcript))
        assertFalse(output.lowercase().contains("pedro"))
        assertFalse(output.lowercase().contains("hijo"))
    }

    @Test
    fun `details are included in formatted output`() {
        val output = subject.format(listOf(entity))
        assertTrue(output.contains(entity.details))
    }

    @Test
    fun `kind label is included in formatted output`() {
        val output = subject.format(listOf(entity))
        assertTrue(output.contains("Salida inválida"))
    }

    @Test
    fun `header shows correct count for single entry`() {
        val output = subject.format(listOf(entity))
        assertTrue(output.contains("1 entrada"))
    }

    @Test
    fun `header shows correct count for multiple entries`() {
        val output = subject.format(listOf(entity, entity.copy(id = 2L)))
        assertTrue(output.contains("2 entradas"))
    }

    @Test
    fun `empty list produces header with zero entries`() {
        val output = subject.format(emptyList())
        assertTrue(output.contains("0 entradas"))
    }

    @Test
    fun `UNKNOWN_FUNCTION kind maps to correct label`() {
        val output = subject.format(listOf(entity.copy(kind = FailureKind.UNKNOWN_FUNCTION)))
        assertTrue(output.contains("Función desconocida"))
    }

    @Test
    fun `HANDLER_ERROR kind maps to correct label`() {
        val output = subject.format(listOf(entity.copy(kind = FailureKind.HANDLER_ERROR)))
        assertTrue(output.contains("Error de acción"))
    }
}
