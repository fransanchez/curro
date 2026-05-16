package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.FunctionCall
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HelpHandler] (US-029 / SF-4.5).
 *
 * Context.getString is stubbed with Mockk (same pattern as other handler tests).
 */
@DisplayName("HelpHandler (SF-4.5)")
class HelpHandlerTest {
    private val context: Context = mockk()

    @BeforeEach
    fun setUp() {
        // Map each string resource ID to a short sentinel so we can assert which string was chosen.
        val strings =
            mapOf(
                R.string.copy_help_generic to "GENERIC",
                R.string.copy_help_topic_call to "CALL",
                R.string.copy_help_topic_whatsapp to "WHATSAPP",
                R.string.copy_help_topic_app to "APP",
                R.string.copy_help_topic_calculate to "CALCULATE",
                R.string.copy_help_topic_time to "TIME",
            )
        every { context.getString(any()) } answers { strings[arg<Int>(0)] ?: "" }
    }

    private fun handler() = HelpHandler(context)

    private fun call(vararg params: Pair<String, Any>): FunctionCall =
        FunctionCall("help", mapOf(*params), confidence = 0.9f)

    private fun assertSpoken(result: HandlerResult): String {
        assertInstanceOf(HandlerResult.Spoken::class.java, result)
        return (result as HandlerResult.Spoken).speech
    }

    // ── 1. empty params → generic ─────────────────────────────────────────────

    @Test
    fun `empty params returns generic`() =
        runTest {
            assertEquals("GENERIC", assertSpoken(handler().handle(call())))
        }

    // ── 2. llamadas → call ────────────────────────────────────────────────────

    @Test
    fun `topic llamadas returns call help`() =
        runTest {
            assertEquals("CALL", assertSpoken(handler().handle(call("topic" to "llamadas"))))
        }

    // ── 3. WhatsApp (mixed case) → whatsapp ──────────────────────────────────

    @Test
    fun `topic WhatsApp case-insensitive returns whatsapp help`() =
        runTest {
            assertEquals("WHATSAPP", assertSpoken(handler().handle(call("topic" to "WhatsApp"))))
        }

    // ── 4. apps → app ─────────────────────────────────────────────────────────

    @Test
    fun `topic apps returns app help`() =
        runTest {
            assertEquals("APP", assertSpoken(handler().handle(call("topic" to "apps"))))
        }

    // ── 5. calculadora → calculate ────────────────────────────────────────────

    @Test
    fun `topic calculadora returns calculate help`() =
        runTest {
            assertEquals("CALCULATE", assertSpoken(handler().handle(call("topic" to "calculadora"))))
        }

    // ── 6. matemáticas (accent-stripped) → calculate ─────────────────────────

    @Test
    fun `topic matemáticas accent-stripped returns calculate help`() =
        runTest {
            assertEquals("CALCULATE", assertSpoken(handler().handle(call("topic" to "matemáticas"))))
        }

    // ── 7. hora → time ────────────────────────────────────────────────────────

    @Test
    fun `topic hora returns time help`() =
        runTest {
            assertEquals("TIME", assertSpoken(handler().handle(call("topic" to "hora"))))
        }

    // ── 8. día (accent-stripped) → time ──────────────────────────────────────

    @Test
    fun `topic día accent-stripped returns time help`() =
        runTest {
            assertEquals("TIME", assertSpoken(handler().handle(call("topic" to "día"))))
        }

    // ── 9. unknown topic → generic ────────────────────────────────────────────

    @Test
    fun `unknown topic el tiempo returns generic`() =
        runTest {
            assertEquals("GENERIC", assertSpoken(handler().handle(call("topic" to "el tiempo"))))
        }

    // ── 10. empty string topic → generic ─────────────────────────────────────

    @Test
    fun `empty string topic returns generic`() =
        runTest {
            assertEquals("GENERIC", assertSpoken(handler().handle(call("topic" to ""))))
        }

    // ── extra variants ────────────────────────────────────────────────────────

    @Test
    fun `topic llamar returns call help`() =
        runTest {
            assertEquals("CALL", assertSpoken(handler().handle(call("topic" to "llamar"))))
        }

    @Test
    fun `topic wasap returns whatsapp help`() =
        runTest {
            assertEquals("WHATSAPP", assertSpoken(handler().handle(call("topic" to "wasap"))))
        }

    @Test
    fun `topic cuentas returns calculate help`() =
        runTest {
            assertEquals("CALCULATE", assertSpoken(handler().handle(call("topic" to "cuentas"))))
        }

    @Test
    fun `topic fecha returns time help`() =
        runTest {
            assertEquals("TIME", assertSpoken(handler().handle(call("topic" to "fecha"))))
        }
}
