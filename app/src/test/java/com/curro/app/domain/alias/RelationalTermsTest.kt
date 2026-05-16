package com.curro.app.domain.alias

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Contract tests for [RelationalTerms] (SF-7.3 / US-047).
 *
 * The set is **curated and normative** — any drift is caught here. The
 * parameterised test checks that every entry is already in its normalised form
 * (lowercase, no accents, single internal spaces). If a new term is added with
 * accents it will immediately fail CI, enforcing the normalisation rule.
 */
@DisplayName("RelationalTerms (SF-7.3)")
class RelationalTermsTest {
    // ─────────────────────────────────────────────────────────────────────────
    // 1. Set is non-empty
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `all is non-empty`() {
        assertNotEquals(0, RelationalTerms.all.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Known relational terms are present
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `mi hija is in the set`() {
        assertTrue("mi hija" in RelationalTerms.all)
    }

    @Test
    fun `mi hijo is in the set`() {
        assertTrue("mi hijo" in RelationalTerms.all)
    }

    @Test
    fun `el medico is in the set`() {
        assertTrue("el medico" in RelationalTerms.all)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Proper names are NOT in the set
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Pepito is not a relational term`() {
        assertFalse("pepito" in RelationalTerms.all)
    }

    @Test
    fun `Maria is not a relational term`() {
        assertFalse("maria" in RelationalTerms.all)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Every entry is already normalised (parameterised — fails CI on new bad entries)
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "''{0}'' must be lowercase with no accents")
    @MethodSource("allTerms")
    fun `every entry is lowercase and accent-free`(term: String) {
        // Lowercase check.
        assertEquals(term.lowercase(), term)
        // Accent-free check: no character in common accent ranges (Spanish).
        val accentPattern = Regex("[áéíóúüñÁÉÍÓÚÜÑàèìòùÀÈÌÒÙ]")
        assertFalse(accentPattern.containsMatchIn(term), "term '$term' contains accented characters")
        // No leading/trailing/double spaces.
        assertEquals(term.trim(), term)
        assertFalse(term.contains("  "), "term '$term' has double spaces")
    }

    private fun assertEquals(
        expected: String,
        actual: String,
    ) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual)
    }

    companion object {
        @JvmStatic
        fun allTerms(): Stream<String> = RelationalTerms.all.stream()
    }
}
