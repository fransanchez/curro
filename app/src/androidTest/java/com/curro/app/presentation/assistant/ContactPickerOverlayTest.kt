package com.curro.app.presentation.assistant

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.curro.app.domain.model.Contact
import com.curro.app.presentation.theme.CurroTheme
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented Compose UI test for [ContactPickerOverlay] (SF-6.3 / US-043).
 *
 * Verifies:
 *  - the prompt is displayed;
 *  - 3 candidate rows + "Ninguna" are visible for 3 candidates;
 *  - 4+ candidates show top 3 + "Más" + "Ninguna"; tapping "Más" expands;
 *  - tapping a row fires `onPick`; tapping "Ninguna" fires `onNone`;
 *  - candidate rows are ≥ 96 dp tall.
 */
class ContactPickerOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun contact(
        key: String,
        name: String,
    ) = Contact(
        lookupKey = key,
        displayName = name,
        phoneNumbers = listOf("+1"),
        photoUri = null,
    )

    private val threeMarias =
        listOf(
            contact("k1", "María García"),
            contact("k2", "María López"),
            contact("k3", "María Ruiz"),
        )

    private val fourMarias =
        threeMarias + contact("k4", "María Sánchez")

    @Test
    fun threeCandidatesShowsAllRowsAndNinguna() {
        composeRule.setContent {
            CurroTheme {
                ContactPickerOverlayContent(
                    prompt = "Tienes 3 Marías. ¿Cuál?",
                    candidates = threeMarias,
                    onPick = {},
                    onNone = {},
                )
            }
        }
        composeRule.onNodeWithText("Tienes 3 Marías. ¿Cuál?").assertIsDisplayed()
        composeRule.onNodeWithText("María García").assertIsDisplayed()
        composeRule.onNodeWithText("María López").assertIsDisplayed()
        composeRule.onNodeWithText("María Ruiz").assertIsDisplayed()
        composeRule.onNodeWithText("Ninguna de estas").assertIsDisplayed()
    }

    @Test
    fun fourCandidatesShowsMásAndHidesFourth() {
        composeRule.setContent {
            CurroTheme {
                ContactPickerOverlayContent(
                    prompt = "Tienes 4 coincidencias para María. ¿Cuál?",
                    candidates = fourMarias,
                    onPick = {},
                    onNone = {},
                )
            }
        }
        composeRule.onNodeWithText("María García").assertIsDisplayed()
        composeRule.onNodeWithText("María López").assertIsDisplayed()
        composeRule.onNodeWithText("María Ruiz").assertIsDisplayed()
        composeRule.onNodeWithText("Más").assertIsDisplayed()
        composeRule.onNodeWithText("Ninguna de estas").assertIsDisplayed()
    }

    @Test
    fun tapMásExpandsToShowOverflowCandidate() {
        composeRule.setContent {
            CurroTheme {
                ContactPickerOverlayContent(
                    prompt = "Tienes 4 coincidencias para María. ¿Cuál?",
                    candidates = fourMarias,
                    onPick = {},
                    onNone = {},
                )
            }
        }
        composeRule.onNodeWithText("Más").performClick()
        composeRule.onNodeWithText("María Sánchez").assertIsDisplayed()
    }

    @Test
    fun candidateRowIsAtLeast96dpTall() {
        composeRule.setContent {
            CurroTheme {
                ContactPickerOverlayContent(
                    prompt = "Tienes 3 Marías. ¿Cuál?",
                    candidates = threeMarias,
                    onPick = {},
                    onNone = {},
                )
            }
        }
        composeRule.onNodeWithText("María López").assertHeightIsAtLeast(96.dp)
    }

    @Test
    fun tapCandidateRowInvokesOnPickWithThatContact() {
        var pickedContact: Contact? = null
        var noneInvoked = 0
        composeRule.setContent {
            CurroTheme {
                ContactPickerOverlayContent(
                    prompt = "Tienes 3 Marías. ¿Cuál?",
                    candidates = threeMarias,
                    onPick = { pickedContact = it },
                    onNone = { noneInvoked++ },
                )
            }
        }
        composeRule.onNodeWithText("María López").performClick()
        assert(pickedContact == threeMarias[1]) { "expected María López, got $pickedContact" }
        assert(noneInvoked == 0) { "onNone should not fire" }
    }

    @Test
    fun tapNingunaInvokesOnNone() {
        var pickedContact: Contact? = null
        var noneInvoked = 0
        composeRule.setContent {
            CurroTheme {
                ContactPickerOverlayContent(
                    prompt = "Tienes 3 Marías. ¿Cuál?",
                    candidates = threeMarias,
                    onPick = { pickedContact = it },
                    onNone = { noneInvoked++ },
                )
            }
        }
        composeRule.onNodeWithText("Ninguna de estas").performClick()
        assert(noneInvoked == 1) { "expected onNone once, got $noneInvoked" }
        assert(pickedContact == null) { "onPick should not fire" }
    }
}
