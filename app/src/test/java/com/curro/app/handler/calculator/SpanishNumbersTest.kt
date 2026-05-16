package com.curro.app.handler.calculator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Tests for [SpanishNumbers.toWords] covering the extended 0..9_999_999 range (US-028 / SF-4.4).
 *
 * The 0..99 range is verified first (regression from SF-4.2, after the move from
 * `handler/time/`). The new hundreds, thousands, and millions cases follow.
 */
@DisplayName("SpanishNumbers (SF-4.2 + SF-4.4 extended)")
class SpanishNumbersTest {
    // ── 0..99 (SF-4.2 regression) ─────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource(
        // units
        "0, cero", "1, uno", "2, dos", "3, tres", "4, cuatro",
        "5, cinco", "6, seis", "7, siete", "8, ocho", "9, nueve",
        // teens
        "10, diez", "11, once", "12, doce", "13, trece", "14, catorce",
        "15, quince", "16, dieciséis", "17, diecisiete", "18, dieciocho", "19, diecinueve",
        // twenties
        "20, veinte", "21, veintiuno", "22, veintidós", "23, veintitrés",
        "24, veinticuatro", "25, veinticinco", "26, veintiséis", "27, veintisiete",
        "28, veintiocho", "29, veintinueve",
        // tens
        "30, treinta", "40, cuarenta", "50, cincuenta",
        "60, sesenta", "70, setenta", "80, ochenta", "90, noventa",
        // compound thirties..nineties
        "31, treinta y uno", "32, treinta y dos", "47, cuarenta y siete",
        "59, cincuenta y nueve", "61, sesenta y uno", "75, setenta y cinco",
        "88, ochenta y ocho", "99, noventa y nueve",
    )
    fun `toWords 0-99 maps n to expected Spanish word`(
        n: Int,
        expected: String,
    ) {
        assertEquals(expected, SpanishNumbers.toWords(n))
    }

    // ── 100..999 (hundreds) ───────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource(
        "100, cien",
        "101, ciento uno",
        "115, ciento quince",
        "135, ciento treinta y cinco",
        "199, ciento noventa y nueve",
        "200, doscientos",
        "300, trescientos",
        "376, trescientos setenta y seis",
        "400, cuatrocientos",
        "500, quinientos",
        "600, seiscientos",
        "700, setecientos",
        "800, ochocientos",
        "900, novecientos",
        "999, novecientos noventa y nueve",
    )
    fun `toWords 100-999 maps n to expected hundreds phrase`(
        n: Int,
        expected: String,
    ) {
        assertEquals(expected, SpanishNumbers.toWords(n))
    }

    // ── 1000..9999 (thousands) ────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource(
        "1000, mil",
        "1001, mil uno",
        "1010, mil diez",
        "1100, mil cien",
        "1500, mil quinientos",
        "1989, mil novecientos ochenta y nueve",
        "2000, dos mil",
        "2026, dos mil veintiséis",
        "2100, dos mil cien",
        "2150, dos mil ciento cincuenta",
        "9999, nueve mil novecientos noventa y nueve",
    )
    fun `toWords 1000-9999 maps n to expected thousands phrase`(
        n: Int,
        expected: String,
    ) {
        assertEquals(expected, SpanishNumbers.toWords(n))
    }

    // ── 10_000..999_999 (tens-of-thousands / hundreds-of-thousands) ───────────

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource(
        "10000, diez mil",
        "25000, veinticinco mil",
        "100000, cien mil",
        "100001, cien mil uno",
        "500000, quinientos mil",
        "999999, novecientos noventa y nueve mil novecientos noventa y nueve",
    )
    fun `toWords 10000-999999 maps n to expected phrase`(
        n: Int,
        expected: String,
    ) {
        assertEquals(expected, SpanishNumbers.toWords(n))
    }

    // ── millions ──────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource(
        "1000000, un millón",
        "1000001, un millón uno",
        "2000000, dos millones",
        "5500000, cinco millones quinientos mil",
        "9999999, nueve millones novecientos noventa y nueve mil novecientos noventa y nueve",
    )
    fun `toWords millions maps n to expected phrase`(
        n: Int,
        expected: String,
    ) {
        assertEquals(expected, SpanishNumbers.toWords(n))
    }

    // ── spec example pin ──────────────────────────────────────────────────────

    @Test
    fun `spec example 376 returns trescientos setenta y seis`() {
        assertEquals("trescientos setenta y seis", SpanishNumbers.toWords(376))
    }

    @Test
    fun `spec example 40 returns cuarenta`() {
        assertEquals("cuarenta", SpanishNumbers.toWords(40))
    }

    @Test
    fun `spec example 42 returns cuarenta y dos`() {
        assertEquals("cuarenta y dos", SpanishNumbers.toWords(42))
    }

    @Test
    fun `spec example 38 returns treinta y ocho`() {
        assertEquals("treinta y ocho", SpanishNumbers.toWords(38))
    }

    @Test
    fun `intToSpanishWords 376 returns trescientos setenta y seis`() {
        assertEquals("trescientos setenta y seis", intToSpanishWords(376L))
    }

    // ── boundary guards ───────────────────────────────────────────────────────

    @Test
    fun `toWords(-1) throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpanishNumbers.toWords(-1)
        }
    }

    @Test
    fun `toWords(10_000_000) throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpanishNumbers.toWords(10_000_000)
        }
    }

    // ── thousands and hundreds boundary detail ────────────────────────────────

    @Test
    fun `ten thousand is diez mil`() {
        assertEquals("diez mil", SpanishNumbers.toWords(10_000))
    }

    @Test
    fun `cien por cien result is diez mil`() {
        // 100 × 100 = 10_000 → "diez mil"
        assertEquals("diez mil", SpanishNumbers.toWords(100 * 100))
    }
}
