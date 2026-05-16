package com.curro.app.handler.time

import java.time.LocalDateTime
import java.time.Month

/**
 * Pure formatter: `LocalDateTime` → colloquial Castilian time / day / date phrases.
 *
 * All methods are stateless and allocation-minimal. No Android imports — fully
 * testable on the JVM without Robolectric.
 *
 * Design decisions (pinned for Phase 4):
 *  - 12-hour clock only: context disambiguates AM/PM for this user.
 *  - "y minutes" form only: no "menos cuarto" / "menos diez" alternative.
 *  - Minutes 0/15/30 get the canonical shortcuts ("en punto", "y cuarto", "y media").
 *  - Years 1000..9999 only — outside the practical range of `LocalDateTime`.
 *  - Phase 4: [SpanishNumbers] handles 0..99; SF-4.4 extends to 0..9_999_999.
 */
internal object SpanishTimeFormatter {
    private const val HOURS_IN_HALF_DAY = 12
    private const val ONE_OCLOCK_12H = 1
    private const val MINUTE_QUARTER = 15
    private const val MINUTE_HALF = 30
    private const val YEAR_MIN = 1000
    private const val YEAR_MAX = 9999
    private const val REMAINDER_MIN = 1
    private const val REMAINDER_MAX = 999
    private const val HUNDREDS_DIVISOR = 100
    private const val HUNDREDS_OFFSET = 1 // "cien"/"ciento" starts at hundreds==1
    private const val HUNDREDS_DOSCIENTOS = 2
    private const val HUNDREDS_TRESCIENTOS = 3
    private const val HUNDREDS_CUATROCIENTOS = 4
    private const val HUNDREDS_QUINIENTOS = 5
    private const val HUNDREDS_SEISCIENTOS = 6
    private const val HUNDREDS_SETECIENTOS = 7
    private const val HUNDREDS_OCHOCIENTOS = 8
    private const val HUNDREDS_NOVECIENTOS = 9

    /**
     * Hour phrase in 12-hour form:
     *   0 (midnight) → "las doce"
     *   1            → "la una"
     *   2..12        → "las dos" … "las doce"
     *   13           → "la una"
     *   14..23       → "las dos" … "las once"
     *
     * The caller wraps this in `copy_time_one` (singular `Es`) or `copy_time_now`
     * (plural `Son`) according to [isSingularHour].
     */
    fun formatHourWord(hour24: Int): String {
        val h12 = ((hour24 + HOURS_IN_HALF_DAY - ONE_OCLOCK_12H) % HOURS_IN_HALF_DAY) + ONE_OCLOCK_12H
        return if (h12 == ONE_OCLOCK_12H) "la una" else "las ${SpanishNumbers.toWords(h12)}"
    }

    /** True iff the 12-hour representation of [hour24] is one (singular: "Es la una"). */
    fun isSingularHour(hour24: Int): Boolean {
        val h12 = ((hour24 + HOURS_IN_HALF_DAY - ONE_OCLOCK_12H) % HOURS_IN_HALF_DAY) + ONE_OCLOCK_12H
        return h12 == ONE_OCLOCK_12H
    }

    /**
     * Minutes phrase appended after the hour word:
     *   0  → "en punto"
     *   15 → "y cuarto"
     *   30 → "y media"
     *   n  → "y <n-in-words>"
     */
    fun formatMinutesPhrase(minute: Int): String =
        when (minute) {
            0 -> "en punto"
            MINUTE_QUARTER -> "y cuarto"
            MINUTE_HALF -> "y media"
            else -> "y ${SpanishNumbers.toWords(minute)}"
        }

    /**
     * Full time phrase: "<hourWord> <minutesPhrase>".
     * Examples: "las doce y cuarenta y siete", "la una en punto".
     */
    fun formatTimePhrase(
        hour: Int,
        minute: Int,
    ): String = "${formatHourWord(hour)} ${formatMinutesPhrase(minute)}"

    private val weekdays =
        listOf(
            "lunes",
            "martes",
            "miércoles",
            "jueves",
            "viernes",
            "sábado",
            "domingo",
        )

    /**
     * Day-of-week phrase: "el miércoles".
     * [DayOfWeek.MONDAY] has value 1, [DayOfWeek.SUNDAY] has value 7.
     */
    fun formatDayPhrase(now: LocalDateTime): String = "el ${weekdays[now.dayOfWeek.value - 1]}"

    private val months =
        mapOf(
            Month.JANUARY to "enero",
            Month.FEBRUARY to "febrero",
            Month.MARCH to "marzo",
            Month.APRIL to "abril",
            Month.MAY to "mayo",
            Month.JUNE to "junio",
            Month.JULY to "julio",
            Month.AUGUST to "agosto",
            Month.SEPTEMBER to "septiembre",
            Month.OCTOBER to "octubre",
            Month.NOVEMBER to "noviembre",
            Month.DECEMBER to "diciembre",
        )

    /**
     * Date phrase: "trece de mayo de dos mil veintiséis".
     * Uses [SpanishNumbers.toWords] for day (1..31 fits in 0..99).
     */
    fun formatDatePhrase(now: LocalDateTime): String {
        val day = SpanishNumbers.toWords(now.dayOfMonth) // 1..31 fits in 0..99
        val month = months.getValue(now.month)
        val year = yearToWords(now.year)
        return "$day de $month de $year"
    }

    /**
     * Year-to-words for the range 1000..9999.
     *
     *  - 1000 → "mil"
     *  - 1001 → "mil uno"
     *  - 1989 → "mil novecientos ochenta y nueve"
     *  - 2000 → "dos mil"
     *  - 2026 → "dos mil veintiséis"
     *  - 9999 → "nueve mil novecientos noventa y nueve"
     */
    fun yearToWords(year: Int): String {
        require(year in YEAR_MIN..YEAR_MAX) { "yearToWords expects $YEAR_MIN..$YEAR_MAX, got $year" }
        val thousands = year / YEAR_MIN
        val remainder = year % YEAR_MIN
        val thousandsPart = if (thousands == ONE_OCLOCK_12H) "mil" else "${SpanishNumbers.toWords(thousands)} mil"
        if (remainder == 0) return thousandsPart
        return "$thousandsPart ${hundredsToWords(remainder)}"
    }

    /**
     * Spanish words for 1..999 (year remainders after dividing by 1000).
     * Phase 4 covers the full 1..999 range needed for years up to 9999.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun hundredsToWords(n: Int): String {
        require(n in REMAINDER_MIN..REMAINDER_MAX)
        val hundreds = n / HUNDREDS_DIVISOR
        val tail = n % HUNDREDS_DIVISOR
        val hundredsWord =
            when (hundreds) {
                0 -> ""
                HUNDREDS_OFFSET -> if (tail == 0) "cien" else "ciento"
                HUNDREDS_DOSCIENTOS -> "doscientos"
                HUNDREDS_TRESCIENTOS -> "trescientos"
                HUNDREDS_CUATROCIENTOS -> "cuatrocientos"
                HUNDREDS_QUINIENTOS -> "quinientos"
                HUNDREDS_SEISCIENTOS -> "seiscientos"
                HUNDREDS_SETECIENTOS -> "setecientos"
                HUNDREDS_OCHOCIENTOS -> "ochocientos"
                HUNDREDS_NOVECIENTOS -> "novecientos"
                else -> error("unreachable: hundreds=$hundreds")
            }
        val tailWord = if (tail == 0) "" else SpanishNumbers.toWords(tail)
        return listOf(hundredsWord, tailWord).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
