package com.curro.app.handler.calculator

/**
 * Integer-to-Spanish-words for the range 0..9_999_999 (US-028 / SF-4.4).
 *
 * Extended from the SF-4.2 table (0..99, previously at `handler/time/SpanishNumbers.kt`)
 * to cover hundreds (100..999), thousands (1000..9999), tens-of-thousands (10_000..99_999),
 * hundreds-of-thousands (100_000..999_999), and up to 9_999_999.
 *
 * [SpanishTimeFormatter] imports from this package now; the old path at `handler/time/` is
 * retired in this commit. Decision pinned: ONE file, ONE copy — the time formatter and the
 * calculator share this single source.
 *
 * The parser ([SpanishExpressionParser]) uses the inverse function [wordsToInt]; this file
 * provides only the forward direction (int → words).
 */
internal object SpanishNumbers {
    // ── bounds ────────────────────────────────────────────────────────────────

    private const val MAX_SUPPORTED = 9_999_999
    private const val UNITS_BOUNDARY = 10
    private const val TEENS_BOUNDARY = 20
    private const val TWENTIES_BOUNDARY = 30
    private const val TENS_OFFSET = 3
    private const val HUNDREDS_BOUNDARY = 100
    private const val THOUSANDS_BOUNDARY = 1_000
    private const val MILLIONS_BOUNDARY = 1_000_000
    private const val MAX_BELOW_HUNDRED = 99

    // Hundreds digit constants (keys in the hundredsWords map).
    private const val HUNDREDS_DIGIT_1 = 1
    private const val HUNDREDS_DIGIT_2 = 2
    private const val HUNDREDS_DIGIT_3 = 3
    private const val HUNDREDS_DIGIT_4 = 4
    private const val HUNDREDS_DIGIT_5 = 5
    private const val HUNDREDS_DIGIT_6 = 6
    private const val HUNDREDS_DIGIT_7 = 7
    private const val HUNDREDS_DIGIT_8 = 8
    private const val HUNDREDS_DIGIT_9 = 9

    // Millions digit constant (used in the millions-path guard).
    private const val ONE_MILLION_DIGIT = 1

    // ── word lists ────────────────────────────────────────────────────────────

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

    // Hundreds words: key = the hundreds digit (1..9).
    private val hundredsWords =
        mapOf(
            HUNDREDS_DIGIT_1 to "ciento",
            HUNDREDS_DIGIT_2 to "doscientos",
            HUNDREDS_DIGIT_3 to "trescientos",
            HUNDREDS_DIGIT_4 to "cuatrocientos",
            HUNDREDS_DIGIT_5 to "quinientos",
            HUNDREDS_DIGIT_6 to "seiscientos",
            HUNDREDS_DIGIT_7 to "setecientos",
            HUNDREDS_DIGIT_8 to "ochocientos",
            HUNDREDS_DIGIT_9 to "novecientos",
        )

    // ── public entry point ────────────────────────────────────────────────────

    /**
     * Converts [n] (0..[MAX_SUPPORTED]) to colloquial Castilian words.
     *
     * Key rules:
     *  - 100 alone → "cien"; 101..199 → "ciento <rest>".
     *  - 1 → "uno" in isolation; but "un" before "millón" / "mil" is handled below.
     *  - 1_000_000 → "un millón"; 2_000_000..9_999_999 → "X millones <rest>".
     *  - Negative numbers and values > MAX_SUPPORTED throw [IllegalArgumentException].
     */
    @Suppress("CyclomaticComplexMethod")
    fun toWords(n: Int): String {
        require(n in 0..MAX_SUPPORTED) { "toWords expects 0..$MAX_SUPPORTED, got $n" }
        return buildWords(n)
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /** Core recursive builder for 0..MAX_SUPPORTED. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun buildWords(n: Int): String {
        if (n == 0) return "cero"

        val parts = mutableListOf<String>()

        // ── millions ──────────────────────────────────────────────────────────
        val millions = n / MILLIONS_BOUNDARY
        val afterMillions = n % MILLIONS_BOUNDARY
        if (millions > 0) {
            if (millions == ONE_MILLION_DIGIT) {
                parts += "un millón"
            } else {
                parts += "${buildWords(millions)} millones"
            }
            if (afterMillions == 0) return parts.joinToString(" ")
            parts += buildThousandsAndBelow(afterMillions)
            return parts.joinToString(" ")
        }

        // ── thousands and below ───────────────────────────────────────────────
        return buildThousandsAndBelow(n)
    }

    /** Handles 1..999_999 (no millions). */
    @Suppress("CyclomaticComplexMethod")
    private fun buildThousandsAndBelow(n: Int): String {
        if (n == 0) return ""
        val parts = mutableListOf<String>()

        val thousands = n / THOUSANDS_BOUNDARY
        val belowThousand = n % THOUSANDS_BOUNDARY
        if (thousands > 0) {
            when (thousands) {
                1 -> parts += "mil"
                else -> parts += "${buildBelow1000(thousands)} mil"
            }
        }
        if (belowThousand > 0) {
            parts += buildBelow1000(belowThousand)
        }
        return parts.joinToString(" ")
    }

    /** Handles 1..999. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun buildBelow1000(n: Int): String {
        if (n == 0) return ""
        if (n == HUNDREDS_BOUNDARY) return "cien"

        val hundredsDigit = n / HUNDREDS_BOUNDARY
        val rest = n % HUNDREDS_BOUNDARY
        return if (hundredsDigit == 0) {
            buildBelow100(n)
        } else {
            val hundredWord = hundredsWords.getValue(hundredsDigit)
            if (rest == 0) hundredWord else "$hundredWord ${buildBelow100(rest)}"
        }
    }

    /** Handles 0..99 — the original SF-4.2 table. */
    fun toWords99(n: Int): String {
        require(n in 0..MAX_BELOW_HUNDRED) { "toWords99 expects 0..$MAX_BELOW_HUNDRED, got $n" }
        return if (n == 0) "cero" else buildBelow100(n)
    }

    private fun buildBelow100(n: Int): String =
        when {
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

/**
 * Public helper: converts a [Long] (0..[SpanishNumbers.MAX_SUPPORTED]) to Spanish words.
 * Used by [com.curro.app.handler.CalculateHandler] to format the result of an expression.
 */
internal fun intToSpanishWords(n: Long): String = SpanishNumbers.toWords(n.toInt())
