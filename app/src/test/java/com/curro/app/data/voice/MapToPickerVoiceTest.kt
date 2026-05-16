package com.curro.app.data.voice

import com.curro.app.domain.model.Contact
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.PickerVoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-6.3 (US-043) — vocabulary mapping for the constrained picker STT pass.
 *
 * The recogniser returns plain Spanish text; [mapToPickerVoice] is the
 * post-hoc match: full name → first name (unique) → ordinal → "ninguna".
 */
@DisplayName("mapToPickerVoice (SF-6.3)")
class MapToPickerVoiceTest {
    private val maria = makeContact("k1", "María García")
    private val mariaLopez = makeContact("k2", "María López")
    private val mariaRuiz = makeContact("k3", "María Ruiz")
    private val pepito = makeContact("k4", "Pepito Sánchez")

    private fun makeContact(
        key: String,
        name: String,
    ) = Contact(
        lookupKey = key,
        displayName = name,
        phoneNumbers = listOf("+1"),
        photoUri = null,
    )

    @Test
    fun `full name matches the candidate`() {
        val result = mapToPickerVoice("María López", listOf(maria, mariaLopez, mariaRuiz))
        assertEquals(PickerVoice.Pick(mariaLopez), result)
    }

    @Test
    fun `accentless full name matches the candidate`() {
        val result = mapToPickerVoice("maria lopez", listOf(maria, mariaLopez, mariaRuiz))
        assertEquals(PickerVoice.Pick(mariaLopez), result)
    }

    @Test
    fun `ordinal primera matches index 0`() {
        val result = mapToPickerVoice("la primera", listOf(maria, mariaLopez, mariaRuiz))
        assertEquals(PickerVoice.Pick(maria), result)
    }

    @Test
    fun `ordinal segunda matches index 1`() {
        val result = mapToPickerVoice("la segunda", listOf(maria, mariaLopez, mariaRuiz))
        assertEquals(PickerVoice.Pick(mariaLopez), result)
    }

    @Test
    fun `ordinal tercero matches index 2 (masculine ordinal works)`() {
        val result = mapToPickerVoice("tercero", listOf(maria, mariaLopez, mariaRuiz))
        assertEquals(PickerVoice.Pick(mariaRuiz), result)
    }

    @Test
    fun `ninguna maps to None`() {
        val result = mapToPickerVoice("ninguna", listOf(maria, mariaLopez))
        assertEquals(PickerVoice.None, result)
    }

    @Test
    fun `nadie maps to None`() {
        val result = mapToPickerVoice("nadie", listOf(maria, mariaLopez))
        assertEquals(PickerVoice.None, result)
    }

    @Test
    fun `unrelated speech maps to Other`() {
        val result = mapToPickerVoice("Lucía", listOf(maria, mariaLopez))
        assertTrue(result is PickerVoice.Other)
        assertEquals("Lucía", (result as PickerVoice.Other).text)
    }

    @Test
    fun `ambiguous first name (two Marías) maps to Other (must say full name)`() {
        val result = mapToPickerVoice("María", listOf(maria, mariaLopez))
        assertTrue(result is PickerVoice.Other, "expected Other for ambiguous first name, got $result")
    }

    @Test
    fun `unique first name in masculine list matches`() {
        val result = mapToPickerVoice("Pepito", listOf(pepito, mariaLopez))
        assertEquals(PickerVoice.Pick(pepito), result)
    }

    @Test
    fun `empty input maps to Failed(SttNoMatch)`() {
        val result = mapToPickerVoice("", listOf(maria))
        assertTrue(result is PickerVoice.Failed)
        assertEquals(CurroError.SttNoMatch, (result as PickerVoice.Failed).error)
    }
}
