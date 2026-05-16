package com.curro.app.handler.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Exhaustive tests for [SpanishNumbers] 0..99 (US-026 / SF-4.2).
 *
 * Spot-checks key boundaries and all special-case ranges. The parameterised
 * test covers representative values; the boundary test exercises -1 and 100
 * to confirm the guard throws.
 */
@DisplayName("SpanishNumbers (SF-4.2)")
class SpanishNumbersTest {
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
    fun `toWords maps n to expected Spanish word`(
        n: Int,
        expected: String,
    ) {
        assertEquals(expected, SpanishNumbers.toWords(n))
    }

    @Test
    fun `toWords(-1) throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpanishNumbers.toWords(-1)
        }
    }

    @Test
    fun `toWords(100) throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpanishNumbers.toWords(100)
        }
    }
}
