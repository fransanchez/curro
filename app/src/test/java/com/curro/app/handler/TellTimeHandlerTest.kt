package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.FunctionCall
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for [TellTimeHandler] (US-026 / SF-4.2).
 *
 * Avoids Robolectric: [Context.getString] is stubbed with Mockk using
 * `answers` closures that reproduce the Android format-string contract
 * (`String.format(args)`). Clock is pinned via `Clock.fixed(…)` so outputs
 * are deterministic.
 *
 * Reference clock: 2026-05-13T12:47:00 UTC (Wednesday, 13 May 2026, 12:47).
 */
@DisplayName("TellTimeHandler (SF-4.2)")
class TellTimeHandlerTest {
    private val context: Context = mockk()

    /**
     * Stub `Context.getString(resId, vararg formatArgs)` by mapping each resource ID
     * to its raw format template and applying `String.format` at call time. This
     * reproduces the Android formatting contract without Robolectric.
     *
     * Mockk note: `getString` takes `vararg Any?`; arg(0) = resId, arg(1..n) = format args.
     * `any()` in the matcher covers the vararg spread; inside `answers`, `arg(1)`, `arg(2)`,
     * etc. retrieve each positional format argument.
     */
    @BeforeEach
    fun setUp() {
        val templates =
            mapOf(
                R.string.copy_time_now to "Son %s.",
                R.string.copy_time_one to "Es %s.",
                R.string.copy_time_day to "Hoy es %s.",
                R.string.copy_time_date to "Hoy es %s, %s.",
                R.string.copy_time_all to "Son %s del %s %s.",
            )
        every { context.getString(any(), *anyVararg<Any>()) } answers {
            val resId = arg<Int>(0)
            val template = templates[resId] ?: ""
            // Mockk passes vararg args as a single wrapped Object[] at args[1].
            // Unwrap it so String.format gets the individual arguments.
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

    // ── reference clock helpers ───────────────────────────────────────────────

    /** Wednesday 2026-05-13 12:47:00 UTC. */
    private fun refClock(): Clock =
        Clock.fixed(
            Instant.parse("2026-05-13T12:47:00Z"),
            ZoneId.of("UTC"),
        )

    private fun handler(clock: Clock): TellTimeHandler = TellTimeHandler(clock, context)

    private fun call(vararg params: Pair<String, Any>): FunctionCall =
        FunctionCall("tell_time", mapOf(*params), confidence = 0.9f)

    // ── what=time ─────────────────────────────────────────────────────────────

    @Test
    fun `what=time at 12h47 returns plural-hour phrase`() =
        runTest {
            val result = handler(refClock()).handle(call("what" to "time"))
            assertSpoken(result, "Son las doce y cuarenta y siete.")
        }

    @Test
    fun `what=time at 01h00 returns singular-hour en-punto phrase`() =
        runTest {
            val clock = Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("UTC"))
            val result = handler(clock).handle(call("what" to "time"))
            assertSpoken(result, "Es la una en punto.")
        }

    @Test
    fun `what=time at 03h15 returns y cuarto`() =
        runTest {
            val clock = Clock.fixed(Instant.parse("2026-05-13T03:15:00Z"), ZoneId.of("UTC"))
            val result = handler(clock).handle(call("what" to "time"))
            assertSpoken(result, "Son las tres y cuarto.")
        }

    @Test
    fun `what=time at 05h30 returns y media`() =
        runTest {
            val clock = Clock.fixed(Instant.parse("2026-05-13T05:30:00Z"), ZoneId.of("UTC"))
            val result = handler(clock).handle(call("what" to "time"))
            assertSpoken(result, "Son las cinco y media.")
        }

    @Test
    fun `what=time at midnight (0h00) returns las doce en punto`() =
        runTest {
            val clock = Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneId.of("UTC"))
            val result = handler(clock).handle(call("what" to "time"))
            assertSpoken(result, "Son las doce en punto.")
        }

    @Test
    fun `what=time at 23h59 returns las once y cincuenta y nueve`() =
        runTest {
            val clock = Clock.fixed(Instant.parse("2026-12-31T23:59:00Z"), ZoneId.of("UTC"))
            val result = handler(clock).handle(call("what" to "time"))
            assertSpoken(result, "Son las once y cincuenta y nueve.")
        }

    // ── what=day ──────────────────────────────────────────────────────────────

    @Test
    fun `what=day returns el miércoles for Wednesday 2026-05-13`() =
        runTest {
            val result = handler(refClock()).handle(call("what" to "day"))
            assertSpoken(result, "Hoy es el miércoles.")
        }

    // ── what=date ─────────────────────────────────────────────────────────────

    @Test
    fun `what=date at 2026-05-13 returns full date phrase`() =
        runTest {
            val result = handler(refClock()).handle(call("what" to "date"))
            assertSpoken(result, "Hoy es el miércoles, trece de mayo de dos mil veintiséis.")
        }

    // ── what=all (default) ────────────────────────────────────────────────────

    @Test
    fun `what=all at 12h47 on Wednesday 13 May returns full all-phrase`() =
        runTest {
            val result = handler(refClock()).handle(call("what" to "all"))
            assertSpoken(
                result,
                "Son las doce y cuarenta y siete del el miércoles trece de mayo de dos mil veintiséis.",
            )
        }

    @Test
    fun `empty params defaults to what=all`() =
        runTest {
            val result = handler(refClock()).handle(call())
            val spoken = assertSpoken(result)
            // Must contain time, day, and date fragments.
            assertContainsAll(spoken, "las doce", "miércoles", "mayo")
        }

    @Test
    fun `unknown what value falls through to all`() =
        runTest {
            val result = handler(refClock()).handle(call("what" to "yesterday"))
            val spoken = assertSpoken(result)
            assertContainsAll(spoken, "las doce", "miércoles", "mayo")
        }

    // ── output constraints ────────────────────────────────────────────────────

    @Test
    fun `speech ends with a period`() =
        runTest {
            val result = handler(refClock()).handle(call("what" to "time"))
            val spoken = assertSpoken(result)
            assertEquals('.', spoken.last(), "Speech must end with '.'")
        }

    @Test
    fun `speech contains no digit characters`() =
        runTest {
            val result = handler(refClock()).handle(call("what" to "all"))
            val spoken = assertSpoken(result)
            assertFalse(spoken.any { it.isDigit() }, "Speech must not contain digit characters: $spoken")
        }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun assertSpoken(
        result: HandlerResult,
        expected: String,
    ): String {
        assertInstanceOf(HandlerResult.Spoken::class.java, result)
        val spoken = (result as HandlerResult.Spoken).speech
        assertEquals(expected, spoken)
        return spoken
    }

    private fun assertSpoken(result: HandlerResult): String {
        assertInstanceOf(HandlerResult.Spoken::class.java, result)
        return (result as HandlerResult.Spoken).speech
    }

    private fun assertContainsAll(
        text: String,
        vararg fragments: String,
    ) {
        fragments.forEach { frag ->
            assertFalse(
                !text.contains(frag),
                "Expected '$frag' in: $text",
            )
        }
    }
}
