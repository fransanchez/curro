package com.curro.app.data.apps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Pure-JVM tests for [curroNormalize] and [levenshtein] (US-027 / SF-4.3).
 *
 * No Android dependency — both functions are plain Kotlin using only [java.text.Normalizer].
 */
@DisplayName("StringNormalization (SF-4.3)")
class StringNormalizationTest {
    // ── curroNormalize ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource(
        "cámara, camara",
        "WhatsApp, whatsapp",
        "Niño, nino",
        "GALERÍA, galeria",
        "Teléfono, telefono",
        "Configuración, configuracion",
        "café, cafe",
        "señor, senor",
    )
    fun `curroNormalize strips accents and lowercases with Spanish locale`(
        input: String,
        expected: String,
    ) {
        assertEquals(expected, input.curroNormalize())
    }

    @Test
    fun `curroNormalize empty string returns empty string`() {
        assertEquals("", "".curroNormalize())
    }

    @Test
    fun `curroNormalize Turkish dotted-I lowercases predictably under Spanish locale`() {
        // Under Locale("es") 'İ' (İ) lowercases to 'i̇' then NFD-strips the combining dot.
        // The contract: the result is a non-empty lowercase ASCII string (no crash, no Turkish exception).
        val result = "İSTANBUL".curroNormalize()
        assertTrue(result.isNotEmpty()) { "Expected non-empty result for Turkish I input: $result" }
        assertTrue(result == result.lowercase()) { "Expected all-lowercase result: $result" }
    }

    // ── levenshtein ───────────────────────────────────────────────────────────

    @Test
    fun `levenshtein identical strings returns 0`() {
        assertEquals(0, levenshtein("abc", "abc"))
    }

    @Test
    fun `levenshtein empty a returns length of b`() {
        assertEquals(3, levenshtein("", "abc"))
    }

    @Test
    fun `levenshtein empty b returns length of a`() {
        assertEquals(3, levenshtein("abc", ""))
    }

    @Test
    fun `levenshtein single substitution returns 1`() {
        assertEquals(1, levenshtein("abc", "abd"))
    }

    @Test
    fun `levenshtein all different chars returns 3`() {
        assertEquals(3, levenshtein("abc", "xyz"))
    }

    @Test
    fun `levenshtein calc vs calculadora returns 7`() {
        // Sanity: "calc" is too far from "calculadora" (7 > LEV_THRESHOLD=3).
        // The handler resolves "calc" via the substring startsWith path, not Levenshtein.
        assertEquals(7, levenshtein("calc", "calculadora"))
    }

    @Test
    fun `levenshtein chrme vs chrome returns 1`() {
        // "chrme" (missing 'o') vs "chrome" — one insertion.
        assertEquals(1, levenshtein("chrme", "chrome"))
    }

    @Test
    fun `levenshtein guasap vs whatsapp is greater than 3`() {
        // "guasap" → "whatsapp": alias map carries this directly, Levenshtein not relied on.
        val d = levenshtein("guasap", "whatsapp")
        assertTrue(d > 3) { "Expected levenshtein(guasap, whatsapp) > 3, got $d" }
    }

    @Test
    fun `levenshtein abcdef vs abcdze returns 2`() {
        // 2 substitutions: e→z, f→e (or equivalently two changes).
        assertEquals(2, levenshtein("abcdef", "abcdze"))
    }

    @Test
    fun `levenshtein abcdef vs xyzdef returns 3`() {
        // 3 substitutions: a→x, b→y, c→z.
        assertEquals(3, levenshtein("abcdef", "xyzdef"))
    }

    @Test
    fun `levenshtein is symmetric`() {
        val a = "chrome"
        val b = "chrme"
        assertEquals(levenshtein(a, b), levenshtein(b, a))
    }
}
