package com.curro.app.handler.calculator

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses a Spanish natural-language arithmetic expression into a [Parsed] token sequence
 * and evaluates it (US-028 / SF-4.4).
 *
 * Constraints (pinned per brief §2):
 *  - Flat left-to-right evaluation (no operator precedence).
 *  - Integer arithmetic only — no decimals, fractions, or negatives.
 *  - Numbers 0..9_999_999 only; "millón"/"millones" is accepted in output but rejected in input
 *    (parse failure) because users speak small numbers.
 *  - "y" between number-words is consumed by [wordsToInt] BEFORE the operator scanner sees it,
 *    so "cuarenta y siete y tres" → [NUM(47), OP(+), NUM(3)] = 50.
 */
@Singleton
class SpanishExpressionParser
    @Inject
    constructor() {
        // ── Token types ───────────────────────────────────────────────────────

        sealed interface Token {
            data class Num(val value: Long) : Token

            data class Op(val op: Operator) : Token

            data class Percent(
                val pct: Long,
                val of: Long,
            ) : Token
        }

        enum class Operator {
            PLUS,
            MINUS,
            TIMES,
            DIVIDE,
        }

        // ── Parsed ────────────────────────────────────────────────────────────

        data class Parsed(
            val tokens: List<Token>,
        ) {
            /**
             * Evaluates the token list with flat left-to-right arithmetic.
             * Returns [Result.failure] for: div-by-zero, overflow (result > 9_999_999 or < 0),
             * or malformed token sequences.
             */
            @Suppress("ReturnCount", "CyclomaticComplexMethod", "CognitiveComplexMethod", "NestedBlockDepth")
            fun evaluate(): Result<Long> {
                if (tokens.isEmpty()) return Result.failure(IllegalStateException("empty"))

                // Single-token cases.
                if (tokens.size == 1) {
                    return when (val t = tokens.first()) {
                        is Token.Percent -> {
                            val result = t.pct * t.of / PERCENT_DIVISOR
                            Result.success(result)
                        }
                        is Token.Num -> Result.failure(IllegalStateException("single_num"))
                        is Token.Op -> Result.failure(IllegalStateException("single_op"))
                    }
                }

                // Flat left-to-right scan.
                var acc: Long? = null
                var pendingOp: Operator? = null
                for (t in tokens) {
                    when (t) {
                        is Token.Num -> {
                            if (acc == null) {
                                acc = t.value
                            } else {
                                val op = pendingOp ?: return Result.failure(IllegalStateException("no_op"))
                                val next = t.value
                                acc =
                                    when (op) {
                                        Operator.PLUS -> acc + next
                                        Operator.MINUS -> acc - next
                                        Operator.TIMES -> acc * next
                                        Operator.DIVIDE -> {
                                            if (next == 0L) return Result.failure(ArithmeticException("div_zero"))
                                            acc / next
                                        }
                                    }
                                pendingOp = null
                                if (acc > MAX_RESULT || acc < 0L) {
                                    return Result.failure(IllegalStateException("overflow"))
                                }
                            }
                        }
                        is Token.Op -> {
                            if (acc == null || pendingOp != null) {
                                return Result.failure(IllegalStateException("op_without_num"))
                            }
                            pendingOp = t.op
                        }
                        is Token.Percent -> return Result.failure(IllegalStateException("percent_only_alone"))
                    }
                }
                if (pendingOp != null) return Result.failure(IllegalStateException("trailing_op"))
                return acc?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("no_result"))
            }
        }

        // ── Public entry point ────────────────────────────────────────────────

        /**
         * Parses [input] into [Parsed]. Returns [Result.failure] if the expression cannot be
         * tokenized (unsupported words, digit-form numbers, "millones" in input, etc.).
         */
        @Suppress("ReturnCount", "CyclomaticComplexMethod")
        fun parse(input: String): Result<Parsed> {
            if (input.isBlank()) return Result.failure(IllegalStateException("parse"))

            val cleaned =
                input
                    .trim()
                    .lowercase(Locale("es"))
                    .replace(",", "")
                    .replace(".", "")
                    .let { stripLeadIn(it) }
                    .trim()

            if (cleaned.isEmpty()) return Result.failure(IllegalStateException("parse"))

            // Percent special form first ("el X por ciento de Y").
            val percentMatch = PERCENT_RE.matchEntire(cleaned)
            if (percentMatch != null) {
                val pct =
                    wordsToLong(percentMatch.groupValues[1].trim())
                        ?: return Result.failure(IllegalStateException("parse"))
                val of =
                    wordsToLong(percentMatch.groupValues[2].trim())
                        ?: return Result.failure(IllegalStateException("parse"))
                return Result.success(Parsed(listOf(Token.Percent(pct, of))))
            }

            // General tokenization.
            val parts = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val tokens = mutableListOf<Token>()
            var i = 0
            while (i < parts.size) {
                // Try multi-word operator first (e.g. "dividido entre").
                val opMatch = matchOperator(parts, i, prevNumberWord = null)
                if (opMatch != null) {
                    tokens += Token.Op(opMatch.first)
                    i += opMatch.second
                    continue
                }
                // Accumulate number-words until the next *inter-number* operator is seen.
                // "y" is only an inter-number operator when the preceding word is NOT a compound
                // tens-word (e.g. "cuarenta y siete" → the "y" belongs to the number, not the
                // expression; but "siete y tres" → "y" is the operator).
                val numWords = mutableListOf<String>()
                while (i < parts.size) {
                    val prevWord = numWords.lastOrNull()
                    val opHere = matchOperator(parts, i, prevNumberWord = prevWord)
                    if (opHere != null) break
                    numWords += parts[i]
                    i++
                }
                if (numWords.isEmpty()) return Result.failure(IllegalStateException("parse"))
                val v =
                    wordsToLong(numWords.joinToString(" "))
                        ?: return Result.failure(IllegalStateException("parse"))
                tokens += Token.Num(v)
            }
            if (tokens.isEmpty()) return Result.failure(IllegalStateException("parse"))
            return Result.success(Parsed(tokens))
        }

        // ── wordsToInt ────────────────────────────────────────────────────────

        /**
         * Converts a Spanish number phrase to a [Long]. Returns `null` if the phrase cannot be
         * parsed (unsupported word, "millón"/"millones" — out of scope, digit form, empty).
         *
         * Algorithm:
         *  1. Reject "millón" / "millones" (out of scope — we only output millions, never accept).
         *  2. Split on "mil" anchor: left part is the thousands multiplier (0 if absent), right part
         *     is the 0..999 remainder.
         *  3. Parse each segment via [parseBelow1000].
         */
        @Suppress("ReturnCount")
        internal fun wordsToLong(text: String): Long? {
            val words = text.trim().lowercase(Locale("es")).split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null

            // Reject out-of-scope words.
            if (words.any { it in OUT_OF_SCOPE_WORDS }) return null

            // Reject digit-form input.
            if (words.any { it.first().isDigit() }) return null

            // Split on the "mil" anchor.
            val milIdx = words.indexOf("mil")
            return if (milIdx < 0) {
                // No "mil" → parse the whole thing as 0..999.
                parseBelow1000(words)?.toLong()
            } else {
                val thousandsPart = words.subList(0, milIdx)
                val remainder = words.subList(milIdx + 1, words.size)
                val thousandsMultiplier =
                    if (thousandsPart.isEmpty()) {
                        1L // "mil" alone = 1000
                    } else {
                        parseBelow1000(thousandsPart)?.toLong() ?: return null
                    }
                val remainderValue =
                    if (remainder.isEmpty()) {
                        0L
                    } else {
                        parseBelow1000(remainder)?.toLong() ?: return null
                    }
                thousandsMultiplier * THOUSANDS_FACTOR + remainderValue
            }
        }

        /**
         * Parses a word list representing a number in 0..999.
         * Returns `null` on unrecognized words.
         */
        @Suppress("ReturnCount", "CyclomaticComplexMethod")
        private fun parseBelow1000(words: List<String>): Int? {
            if (words.isEmpty()) return 0

            // Strip leading "y" (can appear at head of the remainder after "mil").
            val effective = if (words.first() == "y") words.drop(1) else words

            // Single word?
            if (effective.size == 1) {
                return singleWordToInt(effective.first())
            }

            // Check for a hundreds prefix (cien/ciento/doscientos/…/novecientos).
            val (hundredsValue, restWords) =
                extractHundreds(effective)
                    ?: return parseBelow100(effective) // no hundreds prefix — try 0..99

            return if (restWords.isEmpty()) {
                hundredsValue
            } else {
                val restVal = parseBelow100(restWords) ?: return null
                hundredsValue + restVal
            }
        }

        /**
         * Extracts a leading hundreds word from [words] and returns its value (100..900) plus
         * the remaining words. Returns `null` if the first word is not a hundreds word.
         */
        private fun extractHundreds(words: List<String>): Pair<Int, List<String>>? {
            val head = words.first()
            val hundredsVal = HUNDREDS_WORDS_INVERSE[head] ?: return null
            val rest = if (words.size > 1 && words[1] == "y") words.drop(2) else words.drop(1)
            return hundredsVal to rest
        }

        /**
         * Parses a word list in 0..99. Returns `null` on failure.
         */
        @Suppress("ReturnCount")
        private fun parseBelow100(words: List<String>): Int? {
            val effective = if (words.firstOrNull() == "y") words.drop(1) else words
            if (effective.isEmpty()) return 0
            if (effective.size == 1) return singleWordToInt(effective.first())

            // "treinta y cinco" → [treinta, y, cinco] → 30 + 5.
            if (effective.size == TENS_AND_UNITS_WORD_COUNT && effective[1] == "y") {
                val tens = TENS_INVERSE[effective[0]] ?: return null
                val units = UNITS_INVERSE[effective[2]] ?: return null
                return tens + units
            }
            // "veintidós" etc. is a single token; shouldn't reach here with size > 1 unless input
            // is malformed.
            return null
        }

        /** Maps a single Spanish number word (0..99 boundaries + hundreds) to its int value. */
        @Suppress("ReturnCount")
        private fun singleWordToInt(word: String): Int? {
            UNITS_INVERSE[word]?.let { return it }
            TEENS_INVERSE[word]?.let { return it }
            TWENTIES_INVERSE[word]?.let { return it }
            TENS_INVERSE[word]?.let { return it }
            HUNDREDS_WORDS_INVERSE[word]?.let { return it }
            return null
        }

        // ── Operator matching ─────────────────────────────────────────────────

        /**
         * Operator phrase dictionary — multi-word phrases first so they are checked before their
         * single-word subsets. "y" is the last resort (it's also used inside number-words, but the
         * number-scanner has already consumed those by the time the operator scanner reaches a
         * standalone "y").
         */
        private val operatorPhrases: List<Pair<List<String>, Operator>> =
            listOf(
                listOf("multiplicado", "por") to Operator.TIMES,
                listOf("dividido", "entre") to Operator.DIVIDE,
                listOf("dividido", "por") to Operator.DIVIDE,
                listOf("sumado", "a") to Operator.PLUS,
                listOf("por") to Operator.TIMES,
                listOf("x") to Operator.TIMES,
                listOf("entre") to Operator.DIVIDE,
                listOf("mas") to Operator.PLUS,
                listOf("más") to Operator.PLUS,
                listOf("menos") to Operator.MINUS,
                listOf("resta") to Operator.MINUS,
                listOf("y") to Operator.PLUS,
            )

        /**
         * Matches an operator phrase at position [i] in [parts].
         *
         * [prevNumberWord] is the last word already accumulated into the current number token.
         * When it is a tens-word (treinta..noventa) or a hundreds word, the literal "y" at
         * position [i] is a compound connector ("cuarenta **y** siete"), NOT the PLUS operator —
         * so "y" is suppressed in that case.
         */
        private fun matchOperator(
            parts: List<String>,
            i: Int,
            prevNumberWord: String?,
        ): Pair<Operator, Int>? {
            for ((phrase, op) in operatorPhrases) {
                val fits = i + phrase.size <= parts.size && parts.subList(i, i + phrase.size) == phrase
                val suppressed =
                    fits && phrase == listOf("y") && prevNumberWord != null &&
                        (prevNumberWord in TENS_INVERSE || prevNumberWord in HUNDREDS_WORDS_INVERSE)
                if (fits && !suppressed) return op to phrase.size
            }
            return null
        }

        // ── Lead-in stripping ─────────────────────────────────────────────────

        private fun stripLeadIn(s: String): String = LEAD_IN_RE.replace(s, "")

        // ── Inverse lookup tables ─────────────────────────────────────────────

        private companion object {
            const val THOUSANDS_FACTOR = 1_000L
            const val MAX_RESULT = 9_999_999L
            const val PERCENT_DIVISOR = 100L

            // Tens+units parse: "treinta y cinco" → [tens, "y", units] = 3 words.
            const val TENS_AND_UNITS_WORD_COUNT = 3

            val LEAD_IN_RE =
                Regex(
                    "^(cu[aá]nto es|cu[aá]nto suma|cu[aá]nto son|calcula[r]?|dime)\\s+",
                )
            val PERCENT_RE =
                Regex("^el ([\\w\\s]+?) por ciento de ([\\w\\s]+?)$")

            val OUT_OF_SCOPE_WORDS =
                setOf(
                    "millón", "millon", "millones", "billón", "billon", "billones",
                    "trillón", "trillon", "trillones",
                )

            val UNITS_INVERSE: Map<String, Int> =
                mapOf(
                    "cero" to 0,
                    "un" to 1,
                    "uno" to 1,
                    "dos" to 2,
                    "tres" to 3,
                    "cuatro" to 4,
                    "cinco" to 5,
                    "seis" to 6,
                    "siete" to 7,
                    "ocho" to 8,
                    "nueve" to 9,
                )

            val TEENS_INVERSE: Map<String, Int> =
                mapOf(
                    "diez" to 10,
                    "once" to 11,
                    "doce" to 12,
                    "trece" to 13,
                    "catorce" to 14,
                    "quince" to 15,
                    "dieciséis" to 16,
                    "dieciseis" to 16,
                    "diecisiete" to 17,
                    "dieciocho" to 18,
                    "diecinueve" to 19,
                )

            val TWENTIES_INVERSE: Map<String, Int> =
                mapOf(
                    "veinte" to 20,
                    "veintiuno" to 21,
                    "veintiún" to 21,
                    "veintiun" to 21,
                    "veintidós" to 22,
                    "veintidos" to 22,
                    "veintitrés" to 23,
                    "veintitres" to 23,
                    "veinticuatro" to 24,
                    "veinticinco" to 25,
                    "veintiséis" to 26,
                    "veintiseis" to 26,
                    "veintisiete" to 27,
                    "veintiocho" to 28,
                    "veintinueve" to 29,
                )

            val TENS_INVERSE: Map<String, Int> =
                mapOf(
                    "treinta" to 30,
                    "cuarenta" to 40,
                    "cincuenta" to 50,
                    "sesenta" to 60,
                    "setenta" to 70,
                    "ochenta" to 80,
                    "noventa" to 90,
                )

            // Both "cien" (alone = 100) and "ciento" (prefix = 100) map to 100;
            // the remainder is added by [extractHundreds].
            val HUNDREDS_WORDS_INVERSE: Map<String, Int> =
                mapOf(
                    "cien" to 100,
                    "ciento" to 100,
                    "doscientos" to 200,
                    "trescientos" to 300,
                    "cuatrocientos" to 400,
                    "quinientos" to 500,
                    "seiscientos" to 600,
                    "setecientos" to 700,
                    "ochocientos" to 800,
                    "novecientos" to 900,
                )
        }
    }
