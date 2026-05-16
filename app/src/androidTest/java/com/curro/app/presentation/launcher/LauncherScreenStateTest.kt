package com.curro.app.presentation.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.curro.app.assistant.AssistantState
import com.curro.app.domain.model.ClockState
import com.curro.app.presentation.theme.CurroTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SF-5.5 / US-039 — Compose UI tests for the state-driven overlay routing.
 *
 * Exercises `LauncherPlaceholderContent` with controlled `AssistantState`
 * values and asserts the right overlay (or no overlay, for `Idle`) renders.
 *
 * Why instrumented (and not Robolectric in `test/`): the project's JVM source
 * set doesn't yet have a Compose test harness wired in — the dependencies
 * point to `androidTestImplementation(libs.compose.ui.test.junit4)`. The brief
 * permits the fallback to `androidTest/` if Robolectric isn't already
 * available (US-039 §8.8).
 */
@RunWith(AndroidJUnit4::class)
class LauncherScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun stateFor(assistant: AssistantState) =
        LauncherUiState(
            isCurroDefault = true,
            clock = ClockState(timeText = "12:47", dateText = "Miércoles 13 mayo"),
            favorites = emptyList(),
            assistantState = assistant,
            isNotificationAccessGranted = true,
        )

    @Test
    fun idle_shows_no_overlay() {
        composeRule.setContent {
            CurroTheme {
                LauncherPlaceholderContent(
                    uiState = stateFor(AssistantState.Idle),
                    onMakeDefault = {},
                    onMicPressed = {},
                    onClockTapped = {},
                )
            }
        }
        composeRule.onNodeWithText("Te escucho…").assertDoesNotExist()
        composeRule.onNodeWithText("Un momento…").assertDoesNotExist()
    }

    @Test
    fun listening_shows_overlay_with_partial_transcript() {
        composeRule.setContent {
            CurroTheme {
                LauncherPlaceholderContent(
                    uiState =
                        stateFor(
                            AssistantState.Listening(partial = "Llama a Pepito", startedAtMs = 100L),
                        ),
                    onMakeDefault = {},
                    onMicPressed = {},
                    onClockTapped = {},
                )
            }
        }
        composeRule.onNodeWithText("Te escucho…").assertIsDisplayed()
        composeRule.onNodeWithText("Llama a Pepito").assertIsDisplayed()
    }

    @Test
    fun processing_shows_un_momento() {
        composeRule.setContent {
            CurroTheme {
                LauncherPlaceholderContent(
                    uiState = stateFor(AssistantState.Processing(transcript = "qué hora es", startedAtMs = 100L)),
                    onMakeDefault = {},
                    onMicPressed = {},
                    onClockTapped = {},
                )
            }
        }
        composeRule.onNodeWithText("Un momento…").assertIsDisplayed()
    }

    @Test
    fun executing_shows_the_speech_text() {
        composeRule.setContent {
            CurroTheme {
                LauncherPlaceholderContent(
                    uiState =
                        stateFor(
                            AssistantState.Executing(speech = "Llamando a Pepito.", screen = null),
                        ),
                    onMakeDefault = {},
                    onMicPressed = {},
                    onClockTapped = {},
                )
            }
        }
        composeRule.onNodeWithText("Llamando a Pepito.").assertIsDisplayed()
    }

    @Test
    fun error_recovery_shows_the_recovery_message() {
        composeRule.setContent {
            CurroTheme {
                LauncherPlaceholderContent(
                    uiState =
                        stateFor(
                            AssistantState.ErrorRecovery(
                                message = "No te he oído bien, ¿puedes repetirlo?",
                                failureCount = 1,
                            ),
                        ),
                    onMakeDefault = {},
                    onMicPressed = {},
                    onClockTapped = {},
                )
            }
        }
        composeRule.onNodeWithText("No te he oído bien, ¿puedes repetirlo?").assertIsDisplayed()
    }

    @Test
    fun listening_to_processing_swaps_overlays() {
        var state by mutableStateOf<AssistantState>(
            AssistantState.Listening(partial = "Llama a Pepito", startedAtMs = 100L),
        )
        composeRule.setContent {
            CurroTheme {
                LauncherPlaceholderContent(
                    uiState = stateFor(state),
                    onMakeDefault = {},
                    onMicPressed = {},
                    onClockTapped = {},
                )
            }
        }
        composeRule.onNodeWithText("Te escucho…").assertIsDisplayed()
        composeRule.runOnUiThread {
            state = AssistantState.Processing(transcript = "Llama a Pepito", startedAtMs = 200L)
        }
        composeRule.onNodeWithText("Un momento…").assertIsDisplayed()
        composeRule.onNodeWithText("Te escucho…").assertDoesNotExist()
    }
}
