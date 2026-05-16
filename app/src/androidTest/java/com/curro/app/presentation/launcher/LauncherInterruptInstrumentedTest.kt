package com.curro.app.presentation.launcher

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.curro.app.MainActivity
import com.curro.app.assistant.AssistantCoordinator
import com.curro.app.assistant.AssistantEvent
import com.curro.app.assistant.AssistantState
import com.curro.app.assistant.AssistantStateMachine
import com.curro.app.assistant.PendingAction
import com.curro.app.domain.handler.HandlerResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * SF-5.3 / US-037 — instrumented coverage of the interrupt-by-button rule.
 *
 * Drives the **real** `AssistantStateMachine` (the same Hilt-singleton the
 * activity wires) into a non-`Idle` state via legal FSM transitions, then
 * calls `coordinator.onMicPressed()` and asserts the FSM ends in `Listening`.
 *
 * Why instrumented:
 *  - The JVM tests in `AssistantCoordinatorTest` Group F lock the behaviour
 *    with fakes. This test runs the actual Hilt graph and the actual
 *    coordinator on a real device/emulator — the `SystemTtsClient` /
 *    `SystemSttClient` are real but the test does not need to assert on
 *    audio (the manual smoke test on the Redmi 15 covers the wall-clock
 *    150-ms bar; see US-037 §9 acceptance criteria).
 *
 * Why JUnit 4 (not 5): AGP doesn't support JUnit 5 on instrumented Android
 * (see US-001 brief Architect note A5).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LauncherInterruptInstrumentedTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.RECORD_AUDIO,
        )

    @Inject lateinit var coordinator: AssistantCoordinator

    @Inject lateinit var fsm: AssistantStateMachine

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun micPressDuringExecutingReentersListening() =
        runBlocking {
            // Force the singleton FSM into Executing via legal transitions. The same
            // FSM instance is the one the coordinator observes (Hilt singleton).
            fsm.transition(AssistantEvent.MicPressed(1L))
            fsm.transition(AssistantEvent.FinalTranscript("frase", 2L))
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = false,
                    speech = "texto",
                    screen = null,
                    prompt = null,
                    expiresAtMs = 0L,
                    pendingAction = null,
                ),
            )
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                coordinator.state.value is AssistantState.Executing
            }

            coordinator.onMicPressed()
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                coordinator.state.value is AssistantState.Listening
            }
            assertTrue(coordinator.state.value is AssistantState.Listening)
        }

    @Test
    fun micPressDuringConfirmingReentersListening() =
        runBlocking {
            // Drive the FSM into Confirming (the Phase-5 auto-confirm path
            // short-circuits the happy-path Confirming entry, so we use the FSM
            // directly — the goal of this test is the coordinator's
            // interrupt-during-Confirming behaviour, not the happy path).
            fsm.transition(AssistantEvent.MicPressed(1L))
            fsm.transition(AssistantEvent.FinalTranscript("frase", 2L))
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = true,
                    speech = "",
                    screen = null,
                    prompt = "¿confirmas?",
                    expiresAtMs = 10_000L,
                    pendingAction =
                        PendingAction(
                            functionName = "call_contact",
                            kind = PendingAction.Kind.YesNo(onConfirm = { HandlerResult.Spoken("ok") }),
                        ),
                ),
            )
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                coordinator.state.value is AssistantState.Confirming
            }

            coordinator.onMicPressed()
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                coordinator.state.value is AssistantState.Listening
            }
            assertTrue(coordinator.state.value is AssistantState.Listening)
        }

    private companion object {
        const val TIMEOUT_MS = 2_000L
    }
}
