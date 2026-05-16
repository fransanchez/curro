package com.curro.app.handler.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDateTime

/**
 * Pure-JVM tests for [SpanishTimeFormatter] (US-026 / SF-4.2).
 *
 * No Android dependency — all methods are stateless pure Kotlin.
 */
@DisplayName("SpanishTimeFormatter (SF-4.2)")
class SpanishTimeFormatterTest {
    // ── formatHourWord ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "hour24={0} → \"{1}\"")
    @CsvSource(
        // midnight → 12
        "0,  las doce",
        "1,  la una",
        "2,  las dos",
        "11, las once",
        "12, las doce",
        "13, la una",
        "14, las dos",
        "22, las diez",
        "23, las once",
    )
    fun `formatHourWord maps 24-hour to 12-hour Spanish phrase`(
        hour24: Int,
        expected: String,
    ) {
        assertEquals(expected.trim(), SpanishTimeFormatter.formatHourWord(hour24))
    }

    // ── isSingularHour ────────────────────────────────────────────────────────

    @Test
    fun `isSingularHour is true for hour 1`() {
        assertTrue(SpanishTimeFormatter.isSingularHour(1))
    }

    @Test
    fun `isSingularHour is true for hour 13`() {
        assertTrue(SpanishTimeFormatter.isSingularHour(13))
    }

    @Test
    fun `isSingularHour is false for hour 0 (midnight)`() {
        assertFalse(SpanishTimeFormatter.isSingularHour(0))
    }

    @Test
    fun `isSingularHour is false for hour 12`() {
        assertFalse(SpanishTimeFormatter.isSingularHour(12))
    }

    // ── formatMinutesPhrase ───────────────────────────────────────────────────

    @ParameterizedTest(name = "minute={0} → \"{1}\"")
    @CsvSource(
        "0,  en punto",
        "1,  y uno",
        "15, y cuarto",
        "30, y media",
        "47, y cuarenta y siete",
        "59, y cincuenta y nueve",
    )
    fun `formatMinutesPhrase maps minute to Spanish phrase`(
        minute: Int,
        expected: String,
    ) {
        assertEquals(expected.trim(), SpanishTimeFormatter.formatMinutesPhrase(minute))
    }

    // ── formatTimePhrase ──────────────────────────────────────────────────────

    @Test
    fun `formatTimePhrase combines hour and minutes correctly`() {
        // 12:47 → "las doce y cuarenta y siete"
        assertEquals("las doce y cuarenta y siete", SpanishTimeFormatter.formatTimePhrase(12, 47))
    }

    @Test
    fun `formatTimePhrase at midnight (0h00) returns las doce en punto`() {
        assertEquals("las doce en punto", SpanishTimeFormatter.formatTimePhrase(0, 0))
    }

    @Test
    fun `formatTimePhrase at 1h00 returns la una en punto`() {
        assertEquals("la una en punto", SpanishTimeFormatter.formatTimePhrase(1, 0))
    }

    // ── formatDayPhrase ───────────────────────────────────────────────────────

    @ParameterizedTest(name = "dayOfWeek={1} → \"{2}\"")
    @CsvSource(
        "2026-05-11, 1, el lunes",
        "2026-05-12, 2, el martes",
        "2026-05-13, 3, el miércoles",
        "2026-05-14, 4, el jueves",
        "2026-05-15, 5, el viernes",
        "2026-05-16, 6, el sábado",
        "2026-05-17, 0, el domingo",
    )
    fun `formatDayPhrase maps each weekday correctly`(
        dateStr: String,
        @Suppress("UNUSED_PARAMETER") dayOrdinal: Int,
        expected: String,
    ) {
        val dt = LocalDateTime.parse("${dateStr}T12:00:00")
        assertEquals(expected, SpanishTimeFormatter.formatDayPhrase(dt))
    }

    // ── formatDatePhrase ──────────────────────────────────────────────────────

    @Test
    fun `formatDatePhrase for 2026-05-13 returns expected phrase`() {
        val dt = LocalDateTime.parse("2026-05-13T12:47:00")
        assertEquals("trece de mayo de dos mil veintiséis", SpanishTimeFormatter.formatDatePhrase(dt))
    }

    @ParameterizedTest(name = "month {1} of {0} → \"{2}\"")
    @CsvSource(
        "2026-01-05, enero,      cinco de enero de dos mil veintiséis",
        "2026-02-14, febrero,    catorce de febrero de dos mil veintiséis",
        "2026-03-01, marzo,      uno de marzo de dos mil veintiséis",
        "2026-04-30, abril,      treinta de abril de dos mil veintiséis",
        "2026-06-21, junio,      veintiuno de junio de dos mil veintiséis",
        "2026-07-04, julio,      cuatro de julio de dos mil veintiséis",
        "2026-08-15, agosto,     quince de agosto de dos mil veintiséis",
        "2026-09-10, septiembre, diez de septiembre de dos mil veintiséis",
        "2026-10-31, octubre,    treinta y uno de octubre de dos mil veintiséis",
        "2026-11-02, noviembre,  dos de noviembre de dos mil veintiséis",
        "2026-12-25, diciembre,  veinticinco de diciembre de dos mil veintiséis",
    )
    fun `formatDatePhrase maps all months correctly`(
        dateStr: String,
        @Suppress("UNUSED_PARAMETER") monthName: String,
        expected: String,
    ) {
        val dt = LocalDateTime.parse("${dateStr}T00:00:00")
        assertEquals(expected, SpanishTimeFormatter.formatDatePhrase(dt))
    }

    // ── yearToWords ───────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource(
        "1000, mil",
        "1001, mil uno",
        "1010, mil diez",
        "1100, mil cien",
        "1989, mil novecientos ochenta y nueve",
        "2000, dos mil",
        "2026, dos mil veintiséis",
        "2100, dos mil cien",
        "2150, dos mil ciento cincuenta",
        "9999, nueve mil novecientos noventa y nueve",
    )
    fun `yearToWords maps year to Spanish words`(
        year: Int,
        expected: String,
    ) {
        assertEquals(expected, SpanishTimeFormatter.yearToWords(year))
    }

    @Test
    fun `yearToWords(999) throws IllegalArgumentException`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            SpanishTimeFormatter.yearToWords(999)
        }
    }

    @Test
    fun `yearToWords(10000) throws IllegalArgumentException`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            SpanishTimeFormatter.yearToWords(10000)
        }
    }
}
