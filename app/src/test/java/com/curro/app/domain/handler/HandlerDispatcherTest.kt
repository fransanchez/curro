package com.curro.app.domain.handler

import android.content.Context
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.repository.TelemetrySink
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HandlerDispatcher] (US-025 / SF-4.1).
 *
 * Coverage:
 *  - Empty map → Failed(UnknownFunction)
 *  - Unknown action in a non-empty map → Failed(UnknownFunction)
 *  - Spoken → telemetry outcome = "success"
 *  - NeedsConfirmation → telemetry outcome = "needs_confirmation"
 *  - Failed → telemetry outcome = "failed"
 *  - Handler throws → Failed(HandlerCrash) + telemetry outcome = "crash"
 *  - PII boundary: handler_invoked never carries anything beyond function_name + outcome
 */
@DisplayName("HandlerDispatcher (SF-4.1)")
class HandlerDispatcherTest {
    private val telemetry: TelemetrySink = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        // Return the key itself so assertions are readable without string literals.
        every { context.getString(any()) } returns "error_speech"
    }

    private fun dispatcher(handlers: Map<String, FunctionHandler>): HandlerDispatcher =
        HandlerDispatcher(handlers, telemetry, context)

    private fun call(action: String) = FunctionCall(action = action, params = emptyMap(), confidence = 0.9f)

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun fakeHandler(
        name: String,
        result: HandlerResult,
    ) = object : FunctionHandler {
        override val functionName = name

        override suspend fun handle(call: FunctionCall) = result
    }

    private fun throwingHandler(name: String) =
        object : FunctionHandler {
            override val functionName = name

            override suspend fun handle(call: FunctionCall): HandlerResult = error("simulated crash inside handler")
        }

    // ── test cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty map — unknown action returns Failed with UnknownFunction`() =
        runTest {
            val d = dispatcher(emptyMap())
            val result = d.dispatch(call("tell_time"))

            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val failed = result as HandlerResult.Failed
            assertInstanceOf(CurroError.UnknownFunction::class.java, failed.reason)
        }

    @Test
    fun `known map but unregistered action returns Failed with UnknownFunction`() =
        runTest {
            val d = dispatcher(mapOf("open_app" to fakeHandler("open_app", HandlerResult.Spoken("ok"))))
            val result = d.dispatch(call("tell_time"))

            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val failed = result as HandlerResult.Failed
            val reason = failed.reason
            assertInstanceOf(CurroError.UnknownFunction::class.java, reason)
            assertEquals("tell_time", (reason as CurroError.UnknownFunction).name)
        }

    @Test
    fun `Spoken result — telemetry outcome is success`() =
        runTest {
            val spoken = HandlerResult.Spoken("Son las tres.")
            val d = dispatcher(mapOf("tell_time" to fakeHandler("tell_time", spoken)))

            val result = d.dispatch(call("tell_time"))

            assertInstanceOf(HandlerResult.Spoken::class.java, result)
            val propsSlot = slot<Map<String, Any>>()
            verify { telemetry.event("handler_invoked", capture(propsSlot)) }
            assertEquals("tell_time", propsSlot.captured["function_name"])
            assertEquals("success", propsSlot.captured["outcome"])
        }

    @Test
    fun `NeedsConfirmation result — telemetry outcome is needs_confirmation`() =
        runTest {
            val confirm =
                HandlerResult.NeedsConfirmation(
                    prompt = "¿Llamo a Pepito?",
                    onConfirm = { HandlerResult.Spoken("Vale, llamando.") },
                )
            val d = dispatcher(mapOf("call_contact" to fakeHandler("call_contact", confirm)))

            val result = d.dispatch(call("call_contact"))

            assertInstanceOf(HandlerResult.NeedsConfirmation::class.java, result)
            val propsSlot = slot<Map<String, Any>>()
            verify { telemetry.event("handler_invoked", capture(propsSlot)) }
            assertEquals("call_contact", propsSlot.captured["function_name"])
            assertEquals("needs_confirmation", propsSlot.captured["outcome"])
        }

    @Test
    fun `Failed result — telemetry outcome is failed`() =
        runTest {
            val failed =
                HandlerResult.Failed(
                    speech = "No puedo hacer eso.",
                    reason = CurroError.PermissionDenied,
                )
            val d = dispatcher(mapOf("call_contact" to fakeHandler("call_contact", failed)))

            val result = d.dispatch(call("call_contact"))

            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val propsSlot = slot<Map<String, Any>>()
            verify { telemetry.event("handler_invoked", capture(propsSlot)) }
            assertEquals("call_contact", propsSlot.captured["function_name"])
            assertEquals("failed", propsSlot.captured["outcome"])
        }

    @Test
    fun `handler throws — result is Failed(HandlerCrash) and telemetry outcome is crash`() =
        runTest {
            val d = dispatcher(mapOf("open_app" to throwingHandler("open_app")))

            val result = d.dispatch(call("open_app"))

            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val failed = result as HandlerResult.Failed
            assertInstanceOf(CurroError.HandlerCrash::class.java, failed.reason)
            assertEquals("open_app", (failed.reason as CurroError.HandlerCrash).functionName)

            val propsSlot = slot<Map<String, Any>>()
            verify { telemetry.event("handler_invoked", capture(propsSlot)) }
            assertEquals("open_app", propsSlot.captured["function_name"])
            assertEquals("crash", propsSlot.captured["outcome"])
        }

    @Test
    fun `handler_invoked telemetry — never carries utterance, params, or other PII keys`() =
        runTest {
            val d =
                dispatcher(
                    mapOf("tell_time" to fakeHandler("tell_time", HandlerResult.Spoken("ok"))),
                )
            d.dispatch(call("tell_time"))

            val propsSlot = slot<Map<String, Any>>()
            verify { telemetry.event("handler_invoked", capture(propsSlot)) }
            val keys = propsSlot.captured.keys
            assertFalse(keys.contains("utterance"), "utterance must never appear in telemetry")
            assertFalse(keys.contains("params"), "params must never appear in telemetry")
            assertFalse(keys.contains("transcript"), "transcript must never appear in telemetry")
            assertFalse(keys.contains("contact_name"), "contact_name must never appear in telemetry")
            assertTrue(keys.containsAll(setOf("function_name", "outcome")))
            assertEquals(2, keys.size, "handler_invoked must have exactly 2 props")
        }

    /**
     * SF-7.5 pin: the dispatcher bubbles crashes as [HandlerResult.Failed] via
     * [CurroError.HandlerCrash]; it does NOT inject [FailedCommandLog].
     * Verified structurally — [HandlerDispatcher] has no such constructor param
     * and this test creates the dispatcher without one.
     */
    @Test
    fun `handler throws — dispatcher returns HandlerCrash but does not touch FailedCommandLog`() =
        runTest {
            // If HandlerDispatcher ever acquired a FailedCommandLog dep, this
            // construction would fail to compile (no such param available here).
            val d = dispatcher(mapOf("tell_time" to throwingHandler("tell_time")))

            val result = d.dispatch(call("tell_time"))

            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val failed = result as HandlerResult.Failed
            assertInstanceOf(CurroError.HandlerCrash::class.java, failed.reason)
            // Only telemetry.event("handler_invoked", ...) is called — no other event.
            val propsSlot = slot<Map<String, Any>>()
            verify(exactly = 1) { telemetry.event("handler_invoked", capture(propsSlot)) }
            assertEquals("crash", propsSlot.captured["outcome"])
        }
}
