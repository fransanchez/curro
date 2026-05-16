package com.curro.app.assistant

import app.cash.turbine.test
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * SF-5.1 / US-035 — exhaustive coverage of [AssistantStateMachine].
 *
 * Covers every `(state, event)` pair in spec §6's diagram:
 * - valid transitions assert the resulting state shape verbatim;
 * - invalid transitions assert [IllegalAssistantTransition] is thrown carrying
 *   both the pre-state and the event (so test-failure messages are useful).
 *
 * No coroutines / no I/O — the FSM is synchronous. The few coroutine-bearing
 * cases (Group L) use [runTest] for Turbine integration only.
 */
@Suppress("LargeClass", "TooManyFunctions")
class AssistantStateMachineTest {
    // ─────────────────────────────────────────────────────────────────────────
    // Fixtures
    // ─────────────────────────────────────────────────────────────────────────

    private fun newFsm(): AssistantStateMachine = AssistantStateMachine()

    /** Forcibly seed the FSM into a target state via legal transitions. */
    private fun newFsmAt(state: AssistantState): AssistantStateMachine {
        val fsm = newFsm()
        when (state) {
            AssistantState.Idle -> Unit // initial state
            is AssistantState.Listening -> {
                fsm.transition(AssistantEvent.MicPressed(state.startedAtMs))
                if (state.partial.isNotEmpty()) {
                    fsm.transition(AssistantEvent.PartialTranscript(state.partial))
                }
            }
            is AssistantState.Processing -> {
                fsm.transition(AssistantEvent.MicPressed(state.startedAtMs))
                fsm.transition(
                    AssistantEvent.FinalTranscript(state.transcript, state.startedAtMs),
                )
            }
            is AssistantState.Confirming -> {
                fsm.transition(AssistantEvent.MicPressed(100L))
                fsm.transition(AssistantEvent.FinalTranscript("…", 100L))
                fsm.transition(
                    AssistantEvent.FunctionCallReady(
                        needsConfirmation = true,
                        speech = "",
                        screen = null,
                        prompt = state.prompt,
                        expiresAtMs = state.expiresAtMs,
                        pendingAction = state.pendingAction,
                    ),
                )
            }
            is AssistantState.Executing -> {
                fsm.transition(AssistantEvent.MicPressed(100L))
                fsm.transition(AssistantEvent.FinalTranscript("…", 100L))
                fsm.transition(
                    AssistantEvent.FunctionCallReady(
                        needsConfirmation = false,
                        speech = state.speech,
                        screen = state.screen,
                        prompt = null,
                        expiresAtMs = 0L,
                        pendingAction = null,
                    ),
                )
            }
            is AssistantState.ErrorRecovery -> {
                fsm.transition(AssistantEvent.MicPressed(100L))
                fsm.transition(
                    AssistantEvent.SttFailed(state.message, state.failureCount),
                )
            }
        }
        return fsm
    }

    private val noopPendingAction =
        PendingAction(
            functionName = "tell_time",
            onConfirm = { HandlerResult.Spoken("ok") },
        )

    private val idleSample = AssistantState.Idle
    private val listeningSample = AssistantState.Listening(partial = "hola", startedAtMs = 100L)
    private val processingSample = AssistantState.Processing(transcript = "qué hora", startedAtMs = 200L)
    private val confirmingSample =
        AssistantState.Confirming(
            prompt = "¿Llamo?",
            expiresAtMs = 10_000L,
            pendingAction = noopPendingAction,
        )
    private val executingSample = AssistantState.Executing(speech = "Llamando.", screen = null)
    private val errorRecoverySample = AssistantState.ErrorRecovery(message = "No te oí.", failureCount = 1)

    // ─────────────────────────────────────────────────────────────────────────
    // Group A — MicPressed is the interrupt rule (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `mic press from Idle starts Listening`() {
        val fsm = newFsmAt(idleSample)
        val next = fsm.transition(AssistantEvent.MicPressed(300L))
        assertEquals(AssistantState.Listening(partial = "", startedAtMs = 300L), next)
    }

    @Test
    fun `mic press from Listening restarts Listening with new timestamp`() {
        val fsm = newFsmAt(listeningSample)
        val next = fsm.transition(AssistantEvent.MicPressed(500L))
        assertEquals(AssistantState.Listening(partial = "", startedAtMs = 500L), next)
    }

    @Test
    fun `mic press from Processing restarts Listening`() {
        val fsm = newFsmAt(processingSample)
        val next = fsm.transition(AssistantEvent.MicPressed(700L))
        assertEquals(AssistantState.Listening(partial = "", startedAtMs = 700L), next)
    }

    @Test
    fun `mic press from Confirming restarts Listening`() {
        val fsm = newFsmAt(confirmingSample)
        val next = fsm.transition(AssistantEvent.MicPressed(900L))
        assertEquals(AssistantState.Listening(partial = "", startedAtMs = 900L), next)
    }

    @Test
    fun `mic press from Executing restarts Listening`() {
        val fsm = newFsmAt(executingSample)
        val next = fsm.transition(AssistantEvent.MicPressed(1100L))
        assertEquals(AssistantState.Listening(partial = "", startedAtMs = 1100L), next)
    }

    @Test
    fun `mic press from ErrorRecovery restarts Listening`() {
        val fsm = newFsmAt(errorRecoverySample)
        val next = fsm.transition(AssistantEvent.MicPressed(1300L))
        assertEquals(AssistantState.Listening(partial = "", startedAtMs = 1300L), next)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group B — HomePressed resets everywhere (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `home press from Idle stays Idle`() {
        val fsm = newFsmAt(idleSample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.HomePressed))
    }

    @Test
    fun `home press from Listening returns to Idle`() {
        val fsm = newFsmAt(listeningSample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.HomePressed))
    }

    @Test
    fun `home press from Processing returns to Idle`() {
        val fsm = newFsmAt(processingSample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.HomePressed))
    }

    @Test
    fun `home press from Confirming returns to Idle`() {
        val fsm = newFsmAt(confirmingSample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.HomePressed))
    }

    @Test
    fun `home press from Executing returns to Idle`() {
        val fsm = newFsmAt(executingSample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.HomePressed))
    }

    @Test
    fun `home press from ErrorRecovery returns to Idle`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.HomePressed))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group C — PartialTranscript (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `partial transcript from Listening updates partial and preserves startedAtMs`() {
        val fsm = newFsmAt(AssistantState.Listening(partial = "", startedAtMs = 42L))
        val next = fsm.transition(AssistantEvent.PartialTranscript("llama"))
        assertEquals(AssistantState.Listening(partial = "llama", startedAtMs = 42L), next)
    }

    @Test
    fun `partial transcript from Idle throws IllegalAssistantTransition`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.PartialTranscript("x"))
        }
    }

    @Test
    fun `partial transcript from Processing throws`() {
        val fsm = newFsmAt(processingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.PartialTranscript("x"))
        }
    }

    @Test
    fun `partial transcript from Confirming throws`() {
        val fsm = newFsmAt(confirmingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.PartialTranscript("x"))
        }
    }

    @Test
    fun `partial transcript from Executing throws`() {
        val fsm = newFsmAt(executingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.PartialTranscript("x"))
        }
    }

    @Test
    fun `partial transcript from ErrorRecovery throws`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.PartialTranscript("x"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group D — FinalTranscript (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `final transcript from Listening transitions to Processing`() {
        val fsm = newFsmAt(AssistantState.Listening(partial = "ll", startedAtMs = 100L))
        val next = fsm.transition(AssistantEvent.FinalTranscript("llama a Pepito", 250L))
        assertEquals(AssistantState.Processing(transcript = "llama a Pepito", startedAtMs = 250L), next)
    }

    @Test
    fun `final transcript from Idle throws`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.FinalTranscript("x", 1L))
        }
    }

    @Test
    fun `final transcript from Processing throws`() {
        val fsm = newFsmAt(processingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.FinalTranscript("x", 1L))
        }
    }

    @Test
    fun `final transcript from Confirming throws`() {
        val fsm = newFsmAt(confirmingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.FinalTranscript("x", 1L))
        }
    }

    @Test
    fun `final transcript from Executing throws`() {
        val fsm = newFsmAt(executingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.FinalTranscript("x", 1L))
        }
    }

    @Test
    fun `final transcript from ErrorRecovery throws`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.FinalTranscript("x", 1L))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group E — SttFailed (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `stt failed from Listening transitions to ErrorRecovery with copy and count`() {
        val fsm = newFsmAt(AssistantState.Listening(partial = "", startedAtMs = 1L))
        val next = fsm.transition(AssistantEvent.SttFailed("No te he oído bien.", 1))
        assertEquals(
            AssistantState.ErrorRecovery(message = "No te he oído bien.", failureCount = 1),
            next,
        )
    }

    @Test
    fun `stt failed from Idle throws`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.SttFailed("x", 1))
        }
    }

    @Test
    fun `stt failed from Processing throws`() {
        val fsm = newFsmAt(processingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.SttFailed("x", 1))
        }
    }

    @Test
    fun `stt failed from Confirming throws`() {
        val fsm = newFsmAt(confirmingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.SttFailed("x", 1))
        }
    }

    @Test
    fun `stt failed from Executing throws`() {
        val fsm = newFsmAt(executingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.SttFailed("x", 1))
        }
    }

    @Test
    fun `stt failed from ErrorRecovery throws`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.SttFailed("x", 1))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group F — FunctionCallReady (8 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `function call ready from Processing without confirmation goes to Executing`() {
        val fsm = newFsmAt(processingSample)
        val next =
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = false,
                    speech = "Son las 9.",
                    screen = null,
                    prompt = null,
                    expiresAtMs = 0L,
                    pendingAction = null,
                ),
            )
        assertEquals(AssistantState.Executing(speech = "Son las 9.", screen = null), next)
    }

    @Test
    fun `function call ready from Processing with confirmation goes to Confirming`() {
        val fsm = newFsmAt(processingSample)
        val next =
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = true,
                    speech = "",
                    screen = null,
                    prompt = "¿Llamo a Pepe Martínez?",
                    expiresAtMs = 12_345L,
                    pendingAction = noopPendingAction,
                ),
            )
        assertEquals(
            AssistantState.Confirming(
                prompt = "¿Llamo a Pepe Martínez?",
                expiresAtMs = 12_345L,
                pendingAction = noopPendingAction,
            ),
            next,
        )
    }

    @Test
    fun `function call ready with confirmation but null prompt throws IllegalArgumentException`() {
        val fsm = newFsmAt(processingSample)
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                fsm.transition(
                    AssistantEvent.FunctionCallReady(
                        needsConfirmation = true,
                        speech = "",
                        screen = null,
                        prompt = null,
                        expiresAtMs = 0L,
                        pendingAction = noopPendingAction,
                    ),
                )
            }
        assertTrue(
            ex.message?.contains("prompt") == true,
            "expected message to mention prompt; got: ${ex.message}",
        )
    }

    @Test
    fun `function call ready with confirmation but null pendingAction throws IllegalArgumentException`() {
        val fsm = newFsmAt(processingSample)
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                fsm.transition(
                    AssistantEvent.FunctionCallReady(
                        needsConfirmation = true,
                        speech = "",
                        screen = null,
                        prompt = "¿llamo?",
                        expiresAtMs = 0L,
                        pendingAction = null,
                    ),
                )
            }
        assertTrue(
            ex.message?.contains("pendingAction") == true,
            "expected message to mention pendingAction; got: ${ex.message}",
        )
    }

    @Test
    fun `function call ready from Idle throws IllegalAssistantTransition`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = false,
                    speech = "x",
                    screen = null,
                    prompt = null,
                    expiresAtMs = 0L,
                    pendingAction = null,
                ),
            )
        }
    }

    @Test
    fun `function call ready from Listening throws`() {
        val fsm = newFsmAt(listeningSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = false,
                    speech = "x",
                    screen = null,
                    prompt = null,
                    expiresAtMs = 0L,
                    pendingAction = null,
                ),
            )
        }
    }

    @Test
    fun `function call ready from Confirming throws`() {
        val fsm = newFsmAt(confirmingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = false,
                    speech = "x",
                    screen = null,
                    prompt = null,
                    expiresAtMs = 0L,
                    pendingAction = null,
                ),
            )
        }
    }

    @Test
    fun `function call ready from Executing throws`() {
        val fsm = newFsmAt(executingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = false,
                    speech = "x",
                    screen = null,
                    prompt = null,
                    expiresAtMs = 0L,
                    pendingAction = null,
                ),
            )
        }
    }

    @Test
    fun `function call ready from ErrorRecovery throws`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = false,
                    speech = "x",
                    screen = null,
                    prompt = null,
                    expiresAtMs = 0L,
                    pendingAction = null,
                ),
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group G — UserConfirmed (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `user confirmed from Confirming goes to Executing with the supplied speech`() {
        val fsm = newFsmAt(confirmingSample)
        val next = fsm.transition(AssistantEvent.UserConfirmed("Vale, llamando.", null))
        assertEquals(AssistantState.Executing(speech = "Vale, llamando.", screen = null), next)
    }

    @Test
    fun `user confirmed from Idle throws`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserConfirmed("x", null))
        }
    }

    @Test
    fun `user confirmed from Listening throws`() {
        val fsm = newFsmAt(listeningSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserConfirmed("x", null))
        }
    }

    @Test
    fun `user confirmed from Processing throws`() {
        val fsm = newFsmAt(processingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserConfirmed("x", null))
        }
    }

    @Test
    fun `user confirmed from Executing throws`() {
        val fsm = newFsmAt(executingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserConfirmed("x", null))
        }
    }

    @Test
    fun `user confirmed from ErrorRecovery throws`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserConfirmed("x", null))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group H — UserRejected (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `user rejected from Confirming returns to Idle`() {
        val fsm = newFsmAt(confirmingSample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.UserRejected))
    }

    @Test
    fun `user rejected from Idle throws`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserRejected)
        }
    }

    @Test
    fun `user rejected from Listening throws`() {
        val fsm = newFsmAt(listeningSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserRejected)
        }
    }

    @Test
    fun `user rejected from Processing throws`() {
        val fsm = newFsmAt(processingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserRejected)
        }
    }

    @Test
    fun `user rejected from Executing throws`() {
        val fsm = newFsmAt(executingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserRejected)
        }
    }

    @Test
    fun `user rejected from ErrorRecovery throws`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.UserRejected)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group I — ConfirmationTimedOut (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `confirmation timed out from Confirming returns to Idle`() {
        val fsm = newFsmAt(confirmingSample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.ConfirmationTimedOut))
    }

    @Test
    fun `confirmation timed out from Idle throws`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.ConfirmationTimedOut)
        }
    }

    @Test
    fun `confirmation timed out from Listening throws`() {
        val fsm = newFsmAt(listeningSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.ConfirmationTimedOut)
        }
    }

    @Test
    fun `confirmation timed out from Processing throws`() {
        val fsm = newFsmAt(processingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.ConfirmationTimedOut)
        }
    }

    @Test
    fun `confirmation timed out from Executing throws`() {
        val fsm = newFsmAt(executingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.ConfirmationTimedOut)
        }
    }

    @Test
    fun `confirmation timed out from ErrorRecovery throws`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.ConfirmationTimedOut)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group J — ExecutionDone (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `execution done from Executing returns to Idle`() {
        val fsm = newFsmAt(executingSample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.ExecutionDone))
    }

    @Test
    fun `execution done from ErrorRecovery returns to Idle`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.ExecutionDone))
    }

    @Test
    fun `execution done from Idle throws`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.ExecutionDone)
        }
    }

    @Test
    fun `execution done from Listening throws`() {
        val fsm = newFsmAt(listeningSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.ExecutionDone)
        }
    }

    @Test
    fun `execution done from Processing throws`() {
        val fsm = newFsmAt(processingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.ExecutionDone)
        }
    }

    @Test
    fun `execution done from Confirming throws`() {
        val fsm = newFsmAt(confirmingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.ExecutionDone)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group K — RecoverySpoken (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `recovery spoken from ErrorRecovery returns to Idle`() {
        val fsm = newFsmAt(errorRecoverySample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.RecoverySpoken))
    }

    @Test
    fun `recovery spoken from Executing returns to Idle`() {
        val fsm = newFsmAt(executingSample)
        assertEquals(AssistantState.Idle, fsm.transition(AssistantEvent.RecoverySpoken))
    }

    @Test
    fun `recovery spoken from Idle throws`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.RecoverySpoken)
        }
    }

    @Test
    fun `recovery spoken from Listening throws`() {
        val fsm = newFsmAt(listeningSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.RecoverySpoken)
        }
    }

    @Test
    fun `recovery spoken from Processing throws`() {
        val fsm = newFsmAt(processingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.RecoverySpoken)
        }
    }

    @Test
    fun `recovery spoken from Confirming throws`() {
        val fsm = newFsmAt(confirmingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.RecoverySpoken)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group L — StateFlow semantics (3 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle`() {
        assertEquals(AssistantState.Idle, newFsm().state.value)
    }

    @Test
    fun `transition updates state synchronously before returning`() {
        val fsm = newFsm()
        val next = fsm.transition(AssistantEvent.MicPressed(100L))
        // The returned value must match the live StateFlow value — no yield needed.
        assertSame(next, fsm.state.value)
        assertEquals(AssistantState.Listening(partial = "", startedAtMs = 100L), fsm.state.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `redundant identical partial transcripts do not re-emit on StateFlow`() =
        runTest {
            val fsm = newFsm()
            fsm.transition(AssistantEvent.MicPressed(100L))
            fsm.state.test {
                // Initial emission is the current Listening("", 100L).
                assertEquals(AssistantState.Listening(partial = "", startedAtMs = 100L), awaitItem())
                fsm.transition(AssistantEvent.PartialTranscript("hola"))
                assertEquals(AssistantState.Listening(partial = "hola", startedAtMs = 100L), awaitItem())
                // Same partial → same data-class value → MutableStateFlow conflates it.
                fsm.transition(AssistantEvent.PartialTranscript("hola"))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Group M — IllegalAssistantTransition payload (1 test)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `illegal transition exception carries the offending state and event`() {
        val fsm = newFsmAt(idleSample)
        val event = AssistantEvent.UserRejected
        val ex =
            assertThrows(IllegalAssistantTransition::class.java) {
                fsm.transition(event)
            }
        assertEquals(AssistantState.Idle, ex.state)
        assertSame(event, ex.event)
        assertNotNull(ex.message)
        assertTrue(
            ex.message!!.contains("Idle") && ex.message!!.contains("UserRejected"),
            "expected message to reference state + event; got: ${ex.message}",
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group N — Sanity end-to-end normal-turn shape (1 test)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow 1 — full happy path Idle to Listening to Processing to Executing to Idle`() {
        val fsm = newFsm()
        fsm.transition(AssistantEvent.MicPressed(100L))
        fsm.transition(AssistantEvent.PartialTranscript("ll"))
        fsm.transition(AssistantEvent.PartialTranscript("llama a pep"))
        fsm.transition(AssistantEvent.FinalTranscript("llama a Pepito", 200L))
        fsm.transition(
            AssistantEvent.FunctionCallReady(
                needsConfirmation = false,
                speech = "Llamando a Pepito.",
                screen = null,
                prompt = null,
                expiresAtMs = 0L,
                pendingAction = null,
            ),
        )
        assertEquals(AssistantState.Executing(speech = "Llamando a Pepito.", screen = null), fsm.state.value)
        fsm.transition(AssistantEvent.ExecutionDone)
        assertEquals(AssistantState.Idle, fsm.state.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group O — Sanity STT-failure recovery shape (1 test)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow 3 — STT failure goes through ErrorRecovery to Idle`() {
        val fsm = newFsm()
        fsm.transition(AssistantEvent.MicPressed(100L))
        fsm.transition(
            AssistantEvent.SttFailed("No te he oído bien, ¿puedes repetirlo?", 1),
        )
        assertEquals(
            AssistantState.ErrorRecovery(
                message = "No te he oído bien, ¿puedes repetirlo?",
                failureCount = 1,
            ),
            fsm.state.value,
        )
        fsm.transition(AssistantEvent.RecoverySpoken)
        assertEquals(AssistantState.Idle, fsm.state.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group P — Confirmation reject / timeout shape (2 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `flow 2b — UserRejected from Confirming goes to Idle`() {
        val fsm = newFsmAt(confirmingSample)
        fsm.transition(AssistantEvent.UserRejected)
        assertEquals(AssistantState.Idle, fsm.state.value)
    }

    @Test
    fun `flow 2c — ConfirmationTimedOut from Confirming goes to Idle`() {
        val fsm = newFsmAt(confirmingSample)
        fsm.transition(AssistantEvent.ConfirmationTimedOut)
        assertEquals(AssistantState.Idle, fsm.state.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group Q — STT-failure copy with non-STT failureCount sentinel (1 test)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `error recovery message with failureCount zero (non-STT origin) is accepted`() {
        // Non-STT failures (decision layer / handler) pass failureCount = 0 so SF-5.4's
        // counter is not touched. The FSM itself does not interpret the integer.
        val fsm = newFsm()
        fsm.transition(AssistantEvent.MicPressed(1L))
        val next = fsm.transition(AssistantEvent.SttFailed("Eso no lo sé hacer todavía.", 0))
        assertEquals(
            AssistantState.ErrorRecovery(message = "Eso no lo sé hacer todavía.", failureCount = 0),
            next,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group R — PendingAction round-trip through Confirming (1 test)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `pending action survives the Confirming to Executing transition`() {
        val pending =
            PendingAction(
                functionName = "call_contact",
                onConfirm = { HandlerResult.Failed("x", CurroError.AppNotFound("y")) },
            )
        val fsm = newFsm()
        fsm.transition(AssistantEvent.MicPressed(1L))
        fsm.transition(AssistantEvent.FinalTranscript("llama", 2L))
        val confirming =
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = true,
                    speech = "",
                    screen = null,
                    prompt = "¿llamo?",
                    expiresAtMs = 12L,
                    pendingAction = pending,
                ),
            )
        // The PendingAction is stored verbatim — same instance, same lambda.
        val confirmingState = confirming as AssistantState.Confirming
        assertSame(pending, confirmingState.pendingAction)
        assertEquals("call_contact", confirmingState.pendingAction.functionName)
        fsm.transition(AssistantEvent.UserConfirmed("Vale.", null))
        // After confirmation, we're in Executing — the PendingAction has done its job
        // (the coordinator would invoke onConfirm before issuing UserConfirmed).
        assertEquals(AssistantState.Executing(speech = "Vale.", screen = null), fsm.state.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group O — SF-6.1 (US-041) LowConfidenceClarify (5 tests)
    //
    // The clarify event is only valid from Processing; from any other state it
    // throws. The resulting ErrorRecovery carries `failureCount = 0` — pinned
    // so SF-5.4's STT-failure counter (which uses positive integers) is left
    // untouched.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `LowConfidenceClarify from Processing transitions to ErrorRecovery with failureCount=0`() {
        val fsm = newFsmAt(processingSample)
        val next =
            fsm.transition(
                AssistantEvent.LowConfidenceClarify("No te he entendido bien, ¿quieres llamar a alguien?"),
            )
        val expected =
            AssistantState.ErrorRecovery(
                message = "No te he entendido bien, ¿quieres llamar a alguien?",
                failureCount = 0,
            )
        assertEquals(expected, next)
        assertEquals(expected, fsm.state.value)
    }

    @Test
    fun `LowConfidenceClarify from Idle throws`() {
        val fsm = newFsmAt(idleSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.LowConfidenceClarify("x"))
        }
    }

    @Test
    fun `LowConfidenceClarify from Listening throws`() {
        val fsm = newFsmAt(listeningSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.LowConfidenceClarify("x"))
        }
    }

    @Test
    fun `LowConfidenceClarify from Confirming throws`() {
        val fsm = newFsmAt(confirmingSample)
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.LowConfidenceClarify("x"))
        }
    }

    @Test
    fun `LowConfidenceClarify from ErrorRecovery throws (no re-entry)`() {
        val fsm = newFsmAt(AssistantState.ErrorRecovery("prev", failureCount = 1))
        assertThrows(IllegalAssistantTransition::class.java) {
            fsm.transition(AssistantEvent.LowConfidenceClarify("x"))
        }
    }
}
