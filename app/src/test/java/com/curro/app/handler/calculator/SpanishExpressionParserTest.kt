package com.curro.app.handler.calculator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure-JVM tests for [SpanishExpressionParser] (US-028 / SF-4.4).
 *
 * Tests cover tokenization, evaluation, wordsToLong, edge cases, and spec examples.
 */
@DisplayName("SpanishExpressionParser (SF-4.4)")
class SpanishExpressionParserTest {
    private val parser = SpanishExpressionParser()

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun parsed(input: String) = parser.parse(input).getOrThrow()

    private fun tokens(input: String) = parsed(input).tokens

    private fun eval(input: String): Long = parsed(input).evaluate().getOrThrow()

    private fun parseFails(input: String) =
        assertTrue(parser.parse(input).isFailure, "Expected parse failure for: $input")

    // ── spec canonical examples ───────────────────────────────────────────────

    @Test
    fun `spec example 1 cuarenta y siete por ocho tokens`() {
        val t = tokens("cuarenta y siete por ocho")
        assertEquals(3, t.size)
        assertInstanceOf(SpanishExpressionParser.Token.Num::class.java, t[0])
        assertEquals(47L, (t[0] as SpanishExpressionParser.Token.Num).value)
        assertEquals(SpanishExpressionParser.Operator.TIMES, (t[1] as SpanishExpressionParser.Token.Op).op)
        assertEquals(8L, (t[2] as SpanishExpressionParser.Token.Num).value)
    }

    @Test
    fun `spec example 1 cuarenta y siete por ocho evaluates to 376`() {
        assertEquals(376L, eval("cuarenta y siete por ocho"))
    }

    @Test
    fun `spec example 2 mil dividido entre veinticinco evaluates to 40`() {
        assertEquals(40L, eval("mil dividido entre veinticinco"))
    }

    @Test
    fun `spec example 3 quince y veintitres evaluates to 38`() {
        assertEquals(38L, eval("quince y veintitrés"))
    }

    @Test
    fun `spec example 4 percent form el veintiuno por ciento de doscientos evaluates to 42`() {
        assertEquals(42L, eval("el veintiuno por ciento de doscientos"))
    }

    // ── lead-in stripping ─────────────────────────────────────────────────────

    @Test
    fun `calcula mil dividido entre veinticinco evaluates to 40`() {
        assertEquals(40L, eval("calcula mil dividido entre veinticinco"))
    }

    @Test
    fun `cuánto es cuarenta y siete por ocho evaluates to 376`() {
        assertEquals(376L, eval("cuánto es cuarenta y siete por ocho"))
    }

    @Test
    fun `cuánto suma quince y veintitres evaluates to 38`() {
        assertEquals(38L, eval("cuánto suma quince y veintitrés"))
    }

    // ── division by zero ──────────────────────────────────────────────────────

    @Test
    fun `cinco entre cero yields div_zero error`() {
        val result = parsed("cinco entre cero").evaluate()
        assertTrue(result.isFailure)
        assertEquals("div_zero", result.exceptionOrNull()?.message)
    }

    @Test
    fun `cinco entre cero tokens are NUM(5) OP(DIVIDE) NUM(0)`() {
        val t = tokens("cinco entre cero")
        assertEquals(3, t.size)
        assertEquals(5L, (t[0] as SpanishExpressionParser.Token.Num).value)
        assertEquals(SpanishExpressionParser.Operator.DIVIDE, (t[1] as SpanishExpressionParser.Token.Op).op)
        assertEquals(0L, (t[2] as SpanishExpressionParser.Token.Num).value)
    }

    // ── overflow ──────────────────────────────────────────────────────────────

    @Test
    fun `mil por mil yields overflow error`() {
        // 1000 * 1000 = 1_000_000 which fits in range, but mil por mil por mil would overflow.
        val result = parsed("mil por mil por mil").evaluate()
        assertTrue(result.isFailure)
        assertEquals("overflow", result.exceptionOrNull()?.message)
    }

    // ── subtraction ───────────────────────────────────────────────────────────

    @Test
    fun `diez menos tres evaluates to 7`() {
        assertEquals(7L, eval("diez menos tres"))
    }

    // ── addition with "y" as operator ─────────────────────────────────────────

    @Test
    fun `cinco y tres evaluates to 8`() {
        assertEquals(8L, eval("cinco y tres"))
    }

    @Test
    fun `cuarenta y siete y tres evaluates to 50`() {
        // "cuarenta y siete" is scanned greedily as NUM(47), then "y" becomes OP(+), then NUM(3).
        assertEquals(50L, eval("cuarenta y siete y tres"))
    }

    // ── out-of-scope words ────────────────────────────────────────────────────

    @Test
    fun `un billon por dos fails to parse`() {
        parseFails("un billón por dos")
    }

    @Test
    fun `un millon por dos fails to parse`() {
        parseFails("un millón por dos")
    }

    // ── single number ─────────────────────────────────────────────────────────

    @Test
    fun `single number cuarenta y siete fails evaluation with single_num`() {
        val result = parsed("cuarenta y siete").evaluate()
        assertTrue(result.isFailure)
        assertEquals("single_num", result.exceptionOrNull()?.message)
    }

    // ── digit-form numbers ────────────────────────────────────────────────────

    @Test
    fun `digit form 5 por 8 fails to parse`() {
        parseFails("5 por 8")
    }

    // ── empty input ───────────────────────────────────────────────────────────

    @Test
    fun `empty string fails to parse`() {
        parseFails("")
    }

    // ── left-to-right precedence ──────────────────────────────────────────────

    @Test
    fun `diez más dos por tres is evaluated left to right as 36`() {
        // No precedence: (10 + 2) * 3 = 36, not 10 + (2 * 3) = 16.
        assertEquals(36L, eval("diez más dos por tres"))
    }

    // ── wordsToLong ───────────────────────────────────────────────────────────

    @Test
    fun `wordsToLong cuarenta y siete returns 47`() {
        assertEquals(47L, parser.wordsToLong("cuarenta y siete"))
    }

    @Test
    fun `wordsToLong mil returns 1000`() {
        assertEquals(1000L, parser.wordsToLong("mil"))
    }

    @Test
    fun `wordsToLong veinticinco returns 25`() {
        assertEquals(25L, parser.wordsToLong("veinticinco"))
    }

    @Test
    fun `wordsToLong cero returns 0`() {
        assertEquals(0L, parser.wordsToLong("cero"))
    }

    @Test
    fun `wordsToLong doscientos returns 200`() {
        assertEquals(200L, parser.wordsToLong("doscientos"))
    }

    @Test
    fun `wordsToLong mil quinientos returns 1500`() {
        assertEquals(1500L, parser.wordsToLong("mil quinientos"))
    }

    @Test
    fun `wordsToLong billones returns null`() {
        assertNull(parser.wordsToLong("billones"))
    }

    @Test
    fun `wordsToLong empty string returns null`() {
        assertNull(parser.wordsToLong(""))
    }

    // ── operator variants ─────────────────────────────────────────────────────

    @Test
    fun `diez más cinco returns 15 (accent on más)`() {
        assertEquals(15L, eval("diez más cinco"))
    }

    @Test
    fun `diez mas cinco returns 15 (no accent)`() {
        assertEquals(15L, eval("diez mas cinco"))
    }

    // ── percent token ─────────────────────────────────────────────────────────

    @Test
    fun `el veintiuno por ciento de doscientos produces single Percent token`() {
        val t = tokens("el veintiuno por ciento de doscientos")
        assertEquals(1, t.size)
        assertInstanceOf(SpanishExpressionParser.Token.Percent::class.java, t[0])
        val pct = t[0] as SpanishExpressionParser.Token.Percent
        assertEquals(21L, pct.pct)
        assertEquals(200L, pct.of)
    }

    // ── five digit result ─────────────────────────────────────────────────────

    @Test
    fun `cien por cien evaluates to 10000`() {
        assertEquals(10_000L, eval("cien por cien"))
    }

    // ── non-null tokens size sanity ───────────────────────────────────────────

    @Test
    fun `parsing calcula mil dividido entre veinticinco yields 3 tokens`() {
        val t = tokens("calcula mil dividido entre veinticinco")
        assertEquals(3, t.size)
        assertNotNull(t)
    }
}
