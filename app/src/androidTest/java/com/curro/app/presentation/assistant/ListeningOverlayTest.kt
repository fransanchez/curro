package com.curro.app.presentation.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.curro.app.presentation.launcher.ListeningState
import com.curro.app.presentation.theme.CurroTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI test for [ListeningOverlay] (SF-2.4 / US-018).
 *
 * Verifies the 4 cases from US-018 §10:
 * - T1 Listening("") → "Te escucho…" displayed.
 * - T2 Listening("hola") → transcript visible AND headline still anchored.
 * - T3 Error("No te he oído bien…") → error message displayed, no transcript.
 * - T4 Speaking("Hola Curro") → spoken text visible; static wave tag present.
 *
 * Uses [createComposeRule] (no Activity needed) — the overlay is a pure composable.
 */
class ListeningOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun t1ListeningEmptyShowsHeadline() {
        composeRule.setContent {
            CurroTheme {
                ListeningOverlay(state = ListeningState.Listening(""))
            }
        }
        composeRule.onNodeWithText("Te escucho…").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HEADLINE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_WAVE_ANIMATED).assertIsDisplayed()
    }

    @Test
    fun t2ListeningWithPartialShowsTranscriptAndHeadline() {
        composeRule.setContent {
            CurroTheme {
                ListeningOverlay(state = ListeningState.Listening("hola"))
            }
        }
        composeRule.onNodeWithText("Te escucho…").assertIsDisplayed()
        composeRule.onNodeWithText("hola").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TRANSCRIPT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HEADLINE).assertIsDisplayed()
    }

    @Test
    fun t3ErrorReplacesHeadlineNoTranscript() {
        composeRule.setContent {
            CurroTheme {
                ListeningOverlay(state = ListeningState.Error("No te he oído bien, ¿puedes repetirlo?"))
            }
        }
        composeRule.onNodeWithText("No te he oído bien, ¿puedes repetirlo?").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_HEADLINE).assertIsDisplayed()
        // No transcript line in Error state.
        composeRule.onNodeWithTag(TAG_TRANSCRIPT).assertDoesNotExist()
        // Static wave when in Error.
        composeRule.onNodeWithTag(TAG_WAVE_STATIC).assertIsDisplayed()
    }

    @Test
    fun t4SpeakingShowsTextAndStaticWave() {
        composeRule.setContent {
            CurroTheme {
                ListeningOverlay(state = ListeningState.Speaking("Hola Curro"))
            }
        }
        composeRule.onNodeWithText("Te escucho…").assertIsDisplayed()
        composeRule.onNodeWithText("Hola Curro").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_WAVE_STATIC).assertIsDisplayed()
    }
}
