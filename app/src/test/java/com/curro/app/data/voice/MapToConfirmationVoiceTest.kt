package com.curro.app.data.voice

import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.ConfirmationVoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-6.2 (US-042) — vocabulary mapping for the constrained yes/no STT pass.
 *
 * The recogniser returns plain Spanish text; [mapToConfirmationVoice] is the
 * post-hoc match. Lowercase + accent-stripped; the original text is preserved
 * in [ConfirmationVoice.Other] for logging.
 */
@DisplayName("mapToConfirmationVoice (SF-6.2)")
class MapToConfirmationVoiceTest {
    @Test
    fun `sí maps to Yes`() {
        assertEquals(ConfirmationVoice.Yes, mapToConfirmationVoice("sí"))
    }

    @Test
    fun `si maps to Yes`() {
        assertEquals(ConfirmationVoice.Yes, mapToConfirmationVoice("si"))
    }

    @Test
    fun `vale maps to Yes`() {
        assertEquals(ConfirmationVoice.Yes, mapToConfirmationVoice("vale"))
    }

    @Test
    fun `claro maps to Yes`() {
        assertEquals(ConfirmationVoice.Yes, mapToConfirmationVoice("claro"))
    }

    @Test
    fun `dale maps to Yes`() {
        assertEquals(ConfirmationVoice.Yes, mapToConfirmationVoice("dale"))
    }

    @Test
    fun `venga maps to Yes`() {
        assertEquals(ConfirmationVoice.Yes, mapToConfirmationVoice("venga"))
    }

    @Test
    fun `Sí (capitalised with accent) maps to Yes`() {
        assertEquals(ConfirmationVoice.Yes, mapToConfirmationVoice("Sí"))
    }

    @Test
    fun `no maps to No`() {
        assertEquals(ConfirmationVoice.No, mapToConfirmationVoice("no"))
    }

    @Test
    fun `cancela maps to No`() {
        assertEquals(ConfirmationVoice.No, mapToConfirmationVoice("cancela"))
    }

    @Test
    fun `dejalo (accentless) maps to No`() {
        assertEquals(ConfirmationVoice.No, mapToConfirmationVoice("déjalo"))
    }

    @Test
    fun `no llames maps to No`() {
        assertEquals(ConfirmationVoice.No, mapToConfirmationVoice("no llames"))
    }

    @Test
    fun `unrelated speech maps to Other (preserves original text)`() {
        val result = mapToConfirmationVoice("hola Lucía")
        assertTrue(result is ConfirmationVoice.Other)
        assertEquals("hola Lucía", (result as ConfirmationVoice.Other).text)
    }

    @Test
    fun `empty input maps to Failed(SttNoMatch)`() {
        val result = mapToConfirmationVoice("")
        assertTrue(result is ConfirmationVoice.Failed)
        assertEquals(CurroError.SttNoMatch, (result as ConfirmationVoice.Failed).error)
    }
}
