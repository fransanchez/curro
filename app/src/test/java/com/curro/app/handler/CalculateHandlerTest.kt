package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.handler.calculator.SpanishExpressionParser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CalculateHandler] (US-028 / SF-4.4).
 *
 * Uses Mockk for Context.getString (same pattern as TellTimeHandlerTest — no Robolectric).
 * [SpanishExpressionParser] is the real implementation (not mocked) so the spec examples
 * are verified end-to-end.
 */
@DisplayName("CalculateHandler (SF-4.4)")
class CalculateHandlerTest {
    private val context: Context = mockk()
    private val parser = SpanishExpressionParser()

    @BeforeEach
    fun setUp() {
        val templates =
            mapOf(
                R.string.copy_calc_result to "%s son %s.",
                R.string.copy_calc_failed to "No he podido hacer ese cálculo. ¿Lo repites más despacio?",
                R.string.copy_calc_div_zero to "No puedo dividir entre cero.",
            )

        // No-arg getString
        every { context.getString(any()) } answers {
            val resId = arg<Int>(0)
            templates[resId] ?: ""
        }

        // Vararg getString — same unwrapping pattern as TellTimeHandlerTest
        every { context.getString(any(), *anyVararg<Any>()) } answers {
            val resId = arg<Int>(0)
            val template = templates[resId] ?: ""
            val rawArg = if (args.size > 1) args[1] else null
            val formatArgs: Array<out Any?> =
                when (rawArg) {
                    is Array<*> -> rawArg
                    null -> emptyArray()
                    else -> arrayOf(rawArg)
                }
            if (formatArgs.isEmpty()) template else String.format(template, *formatArgs)
        }
    }

    private fun handler() = CalculateHandler(parser, context)

    private fun call(expression: String): FunctionCall =
        FunctionCall("calculate", mapOf("expression" to expression), confidence = 0.9f)

    private fun assertSpoken(result: HandlerResult): String {
        assertInstanceOf(HandlerResult.Spoken::class.java, result)
        return (result as HandlerResult.Spoken).speech
    }

    private fun assertFailed(result: HandlerResult): HandlerResult.Failed {
        assertInstanceOf(HandlerResult.Failed::class.java, result)
        return result as HandlerResult.Failed
    }

    // ── spec canonical examples ───────────────────────────────────────────────

    @Test
    fun `spec example 1 cuarenta y siete por ocho`() =
        runTest {
            val result = handler().handle(call("cuarenta y siete por ocho"))
            assertEquals("Cuarenta y siete por ocho son trescientos setenta y seis.", assertSpoken(result))
        }

    @Test
    fun `spec example 2 mil dividido entre veinticinco`() =
        runTest {
            val result = handler().handle(call("mil dividido entre veinticinco"))
            assertEquals("Mil dividido entre veinticinco son cuarenta.", assertSpoken(result))
        }

    @Test
    fun `spec example 3 quince y veintitres`() =
        runTest {
            val result = handler().handle(call("quince y veintitrés"))
            assertEquals("Quince y veintitrés son treinta y ocho.", assertSpoken(result))
        }

    @Test
    fun `spec example 4 el veintiuno por ciento de doscientos`() =
        runTest {
            val result = handler().handle(call("el veintiuno por ciento de doscientos"))
            assertEquals("El veintiuno por ciento de doscientos son cuarenta y dos.", assertSpoken(result))
        }

    // ── division by zero ──────────────────────────────────────────────────────

    @Test
    fun `cinco entre cero returns copy_calc_div_zero`() =
        runTest {
            val result = handler().handle(call("cinco entre cero"))
            val failed = assertFailed(result)
            assertEquals("No puedo dividir entre cero.", failed.speech)
            val reason = failed.reason as CurroError.Calculation
            assertEquals("div_zero", reason.reason)
        }

    // ── parse error ───────────────────────────────────────────────────────────

    @Test
    fun `parse error returns copy_calc_failed with reason parse`() =
        runTest {
            val result = handler().handle(call("cuántos billones tiene Pepito"))
            val failed = assertFailed(result)
            assertEquals("No he podido hacer ese cálculo. ¿Lo repites más despacio?", failed.speech)
            val reason = failed.reason as CurroError.Calculation
            assertEquals("parse", reason.reason)
        }

    @Test
    fun `un billon por dos is parse failure`() =
        runTest {
            val result = handler().handle(call("un billón por dos"))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.Calculation::class.java, failed.reason)
            assertEquals("parse", (failed.reason as CurroError.Calculation).reason)
        }

    // ── overflow ──────────────────────────────────────────────────────────────

    @Test
    fun `mil por mil por mil is overflow failure`() =
        runTest {
            val result = handler().handle(call("mil por mil por mil"))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.Calculation::class.java, failed.reason)
            assertEquals("overflow", (failed.reason as CurroError.Calculation).reason)
        }

    // ── empty expression ──────────────────────────────────────────────────────

    @Test
    fun `empty expression returns copy_calc_failed with reason empty`() =
        runTest {
            val result = handler().handle(call(""))
            val failed = assertFailed(result)
            assertEquals("No he podido hacer ese cálculo. ¿Lo repites más despacio?", failed.speech)
            val reason = failed.reason as CurroError.Calculation
            assertEquals("empty", reason.reason)
        }

    // ── subtraction ───────────────────────────────────────────────────────────

    @Test
    fun `diez menos tres returns siete`() =
        runTest {
            val result = handler().handle(call("diez menos tres"))
            assertEquals("Diez menos tres son siete.", assertSpoken(result))
        }

    // ── left-to-right precedence ──────────────────────────────────────────────

    @Test
    fun `diez más dos por tres is 36 not 16`() =
        runTest {
            val result = handler().handle(call("diez más dos por tres"))
            // (10 + 2) * 3 = 36 — flat left-to-right, no precedence.
            assertEquals("Diez más dos por tres son treinta y seis.", assertSpoken(result))
        }

    // ── five-digit result ─────────────────────────────────────────────────────

    @Test
    fun `cien por cien returns diez mil`() =
        runTest {
            val result = handler().handle(call("cien por cien"))
            assertEquals("Cien por cien son diez mil.", assertSpoken(result))
        }

    // ── accent-bearing operators ──────────────────────────────────────────────

    @Test
    fun `diez más cinco returns 15 with accent`() =
        runTest {
            val result = handler().handle(call("diez más cinco"))
            assertEquals("Diez más cinco son quince.", assertSpoken(result))
        }

    @Test
    fun `diez mas cinco returns 15 without accent`() =
        runTest {
            val result = handler().handle(call("diez mas cinco"))
            assertEquals("Diez mas cinco son quince.", assertSpoken(result))
        }

    // ── output constraints ────────────────────────────────────────────────────

    @Test
    fun `output ends with period`() =
        runTest {
            val result = handler().handle(call("cuarenta y siete por ocho"))
            assertEquals('.', assertSpoken(result).last())
        }

    @Test
    fun `output speech starts with capitalised first character`() =
        runTest {
            val result = handler().handle(call("cuarenta y siete por ocho"))
            val speech = assertSpoken(result)
            assertTrue(speech.first().isUpperCase())
        }

    // ── CurroError.Calculation carries the expression ─────────────────────────

    @Test
    fun `Calculation error carries the raw expression`() =
        runTest {
            val expr = "cinco entre cero"
            val result = handler().handle(call(expr))
            val failed = assertFailed(result)
            val calcError = failed.reason as CurroError.Calculation
            assertEquals(expr, calcError.expression)
        }

    // ── digit-form numbers ────────────────────────────────────────────────────

    @Test
    fun `digit form 5 por 8 fails to parse`() =
        runTest {
            val result = handler().handle(call("5 por 8"))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.Calculation::class.java, failed.reason)
        }

    // ── full phrase from spec ─────────────────────────────────────────────────

    @Test
    fun `cuánto es cuarenta y siete por ocho with lead-in returns correct phrase`() =
        runTest {
            val result = handler().handle(call("cuánto es cuarenta y siete por ocho"))
            assertEquals(
                "Cuánto es cuarenta y siete por ocho son trescientos setenta y seis.",
                assertSpoken(result),
            )
        }

    // ── single number causes eval failure (not a crash) ───────────────────────

    @Test
    fun `single number cuarenta is eval failure`() =
        runTest {
            val result = handler().handle(call("cuarenta"))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.Calculation::class.java, failed.reason)
        }

    // ── más/mas operator both work ────────────────────────────────────────────

    @Test
    fun `veinte más diez returns treinta`() =
        runTest {
            val result = handler().handle(call("veinte más diez"))
            assertEquals("Veinte más diez son treinta.", assertSpoken(result))
        }

    // ── multiplication with multiplicado por ──────────────────────────────────

    @Test
    fun `cinco multiplicado por seis returns treinta`() =
        runTest {
            val result = handler().handle(call("cinco multiplicado por seis"))
            assertEquals("Cinco multiplicado por seis son treinta.", assertSpoken(result))
        }
}
