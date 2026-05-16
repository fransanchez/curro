package com.curro.app

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.curro.app.assistant.AssistantCoordinator
import com.curro.app.assistant.AssistantEvent
import com.curro.app.assistant.AssistantState
import com.curro.app.assistant.AssistantStateMachine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * SF-5.6 / US-040 — instrumented coverage of the HOME-press reset.
 *
 * `MainActivity` is `launchMode="singleTask"`: pressing HOME from any app
 * brings the existing instance back via `onNewIntent`. The test launches
 * `MainActivity`, drives the assistant FSM into a non-Idle state via legal
 * transitions, fires a synthetic HOME intent through `onNewIntent`, and
 * asserts the FSM ends in `Idle`.
 *
 * The full FSM coverage of `HomePressed` lives in
 * `AssistantStateMachineTest` (US-035 Group B); the coordinator-side
 * cancellation glue is verified in `AssistantCoordinatorTest` Group O. This
 * test specifically locks the activity-level wiring: `onNewIntent(CATEGORY_HOME)`
 * → `coordinator.onHomePressed()`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityOnNewIntentInstrumentedTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var coordinator: AssistantCoordinator

    @Inject lateinit var fsm: AssistantStateMachine

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun homeIntentResetsAssistantStateToIdle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Drive the FSM into Listening through legal transitions. The Hilt-singleton
            // FSM is the same instance the activity-injected coordinator observes.
            fsm.transition(AssistantEvent.MicPressed(timestamp = 1L))
            waitUntil(timeoutMs = TIMEOUT_MS) {
                coordinator.state.value is AssistantState.Listening
            }
            assertTrue(coordinator.state.value is AssistantState.Listening)

            // Fire a synthetic HOME intent.
            val homeIntent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
            scenario.onActivity { activity ->
                activity.onNewIntent(homeIntent)
            }

            waitUntil(timeoutMs = TIMEOUT_MS) {
                coordinator.state.value is AssistantState.Idle
            }
            assertTrue(coordinator.state.value is AssistantState.Idle)
        }
    }

    private fun waitUntil(
        timeoutMs: Long,
        predicate: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 2_000L
        const val POLL_INTERVAL_MS = 10L
    }
}
