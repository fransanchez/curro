package com.curro.app.presentation.assistant

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.curro.app.presentation.theme.CurroTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI test for [ConfirmationOverlay] (SF-6.2 / US-042).
 *
 * Verifies:
 *  - the prompt is displayed;
 *  - SÍ / NO are each ≥ 96 dp wide and tall;
 *  - a tap on SÍ fires `onYes`; a tap on NO fires `onNo`.
 */
class ConfirmationOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun promptIsDisplayed() {
        composeRule.setContent {
            CurroTheme {
                ConfirmationOverlayContent(prompt = "¿Llamo a Pepe?", onYes = {}, onNo = {})
            }
        }
        composeRule.onNodeWithText("¿Llamo a Pepe?").assertIsDisplayed()
    }

    @Test
    fun yesButtonIsAtLeast96dpInBothDimensions() {
        composeRule.setContent {
            CurroTheme {
                ConfirmationOverlayContent(prompt = "¿Llamo a Pepe?", onYes = {}, onNo = {})
            }
        }
        composeRule.onNodeWithText("SÍ").assertWidthIsAtLeast(96.dp)
        composeRule.onNodeWithText("SÍ").assertHeightIsAtLeast(96.dp)
    }

    @Test
    fun noButtonIsAtLeast96dpInBothDimensions() {
        composeRule.setContent {
            CurroTheme {
                ConfirmationOverlayContent(prompt = "¿Llamo a Pepe?", onYes = {}, onNo = {})
            }
        }
        composeRule.onNodeWithText("NO").assertWidthIsAtLeast(96.dp)
        composeRule.onNodeWithText("NO").assertHeightIsAtLeast(96.dp)
    }

    @Test
    fun tapYesInvokesOnYes() {
        var yesCount = 0
        var noCount = 0
        composeRule.setContent {
            CurroTheme {
                ConfirmationOverlayContent(
                    prompt = "¿Llamo a Pepe?",
                    onYes = { yesCount++ },
                    onNo = { noCount++ },
                )
            }
        }
        composeRule.onNodeWithText("SÍ").performClick()
        assert(yesCount == 1) { "expected onYes once, got $yesCount" }
        assert(noCount == 0) { "expected onNo zero, got $noCount" }
    }

    @Test
    fun tapNoInvokesOnNo() {
        var yesCount = 0
        var noCount = 0
        composeRule.setContent {
            CurroTheme {
                ConfirmationOverlayContent(
                    prompt = "¿Llamo a Pepe?",
                    onYes = { yesCount++ },
                    onNo = { noCount++ },
                )
            }
        }
        composeRule.onNodeWithText("NO").performClick()
        assert(noCount == 1) { "expected onNo once, got $noCount" }
        assert(yesCount == 0) { "expected onYes zero, got $yesCount" }
    }
}
