package com.curro.app.handler.time

/**
 * Integer-to-Spanish-words for the range 0..99.
 *
 * Used by [SpanishTimeFormatter] for minutes (0..59) and day-of-month (1..31).
 * SF-4.4 (US-028, calculate handler) moves this file to `handler/calculator/`
 * and extends the range to 0..9_999_999. At that point the import in
 * [SpanishTimeFormatter] is updated to the new package — there is deliberately
 * ONE copy of this object, never two.
 *
 * Phase 4 surface: 0..99 only. The `require` guard catches any future caller
 * passing a value outside this range.
 */
internal object SpanishNumbers {
    private const val UNITS_BOUNDARY = 10
    private const val TEENS_BOUNDARY = 20
    private const val TWENTIES_BOUNDARY = 30
    private const val TENS_OFFSET = 3 // tens list starts at "treinta" (index 0 = 30÷10 - 3)
    private const val MAX_SUPPORTED = 99

    private val units =
        listOf(
            "cero", "uno", "dos", "tres", "cuatro", "cinco",
            "seis", "siete", "ocho", "nueve",
        )
    private val teens =
        listOf(
            "diez", "once", "doce", "trece", "catorce", "quince",
            "dieciséis", "diecisiete", "dieciocho", "diecinueve",
        )
    private val twenties =
        listOf(
            "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro",
            "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve",
        )
    private val tens =
        listOf(
            "treinta",
            "cuarenta",
            "cincuenta",
            "sesenta",
            "setenta",
            "ochenta",
            "noventa",
        )

    /**
     * Converts [n] in 0..99 to its colloquial Castilian word form.
     * The caller is responsible for keeping [n] within the declared range;
     * passing a value outside it throws [IllegalArgumentException].
     */
    fun toWords(n: Int): String {
        require(n in 0..MAX_SUPPORTED) { "SpanishNumbers.toWords expects 0..$MAX_SUPPORTED, got $n" }
        return when {
            n < UNITS_BOUNDARY -> units[n]
            n < TEENS_BOUNDARY -> teens[n - UNITS_BOUNDARY]
            n < TWENTIES_BOUNDARY -> twenties[n - TEENS_BOUNDARY]
            else -> {
                val tensWord = tens[(n / UNITS_BOUNDARY) - TENS_OFFSET]
                val unit = n % UNITS_BOUNDARY
                if (unit == 0) tensWord else "$tensWord y ${units[unit]}"
            }
        }
    }
}
