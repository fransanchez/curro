package com.curro.app.presentation.assistant

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.assistant.AssistantState
import com.curro.app.assistant.PendingAction
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.Contact
import com.curro.app.presentation.common.BigListRow
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * SF-6.3 (US-043) — picker overlay for the 3-Marías disambiguation flow
 * (spec §6 flow 3).
 *
 * Painted by `LauncherPlaceholderScreen` when the `Confirming` state's
 * `pendingAction.kind` is [PendingAction.Kind.PickContact]. Shows up to 3
 * candidate rows + "Ninguna"; ≥ 4 candidates → top 3 + an expandable "Más"
 * row + "Ninguna".
 *
 * Senior-first contract:
 *  - prompt at `displaySmall` (one notch below `displayMedium` to leave room
 *    for the list);
 *  - each row ≥ `Dimens.BigRowHeight` (96 dp by `BigListRow`);
 *  - haptic on tap (inherited from `BigListRow`);
 *  - dark mode + 2× font scale supported via `CurroTheme`.
 *
 * Voice pick runs in parallel (the coordinator's `pickerListenerJob`); the
 * composable knows nothing about STT.
 */
@Composable
fun ContactPickerOverlay(
    state: AssistantState.Confirming,
    onPick: (Contact) -> Unit,
    onNone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = state.pendingAction.kind as? PendingAction.Kind.PickContact ?: return
    ContactPickerOverlayContent(
        prompt = state.prompt,
        candidates = kind.candidates,
        onPick = onPick,
        onNone = onNone,
        modifier = modifier,
    )
}

@Composable
internal fun ContactPickerOverlayContent(
    prompt: String,
    candidates: List<Contact>,
    onPick: (Contact) -> Unit,
    onNone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = candidates.take(VISIBLE_CANDIDATES)
    val overflow = candidates.drop(VISIBLE_CANDIDATES)
    var moreExpanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = CurroSpacing.l, vertical = CurroSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(modifier = Modifier.height(CurroSpacing.l))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(CurroSpacing.m),
            modifier = Modifier.weight(1f),
        ) {
            items(visible, key = { it.lookupKey }) { contact ->
                BigListRow(
                    title = contact.displayName,
                    onClick = { onPick(contact) },
                )
            }
            if (overflow.isNotEmpty() && !moreExpanded) {
                item(key = KEY_MORE) {
                    BigListRow(
                        title = stringResource(R.string.copy_disambig_more_label),
                        onClick = { moreExpanded = true },
                    )
                }
            }
            if (overflow.isNotEmpty() && moreExpanded) {
                items(overflow, key = { it.lookupKey }) { contact ->
                    BigListRow(
                        title = contact.displayName,
                        onClick = { onPick(contact) },
                    )
                }
            }
            item(key = KEY_NONE) {
                BigListRow(
                    title = stringResource(R.string.copy_disambig_none_option),
                    onClick = onNone,
                )
            }
        }
    }
}

private const val VISIBLE_CANDIDATES = 3
private const val KEY_MORE = "more"
private const val KEY_NONE = "none"

// ── Previews ──────────────────────────────────────────────────────────────────────────────────

private val previewThreeMarias =
    listOf(
        Contact(lookupKey = "k1", displayName = "María García", phoneNumbers = listOf("+1"), photoUri = null),
        Contact(lookupKey = "k2", displayName = "María López", phoneNumbers = listOf("+2"), photoUri = null),
        Contact(lookupKey = "k3", displayName = "María Ruiz", phoneNumbers = listOf("+3"), photoUri = null),
    )

private val previewFourMarias =
    previewThreeMarias +
        Contact(lookupKey = "k4", displayName = "María Sánchez", phoneNumbers = listOf("+4"), photoUri = null)

private const val PREVIEW_PROMPT_THREE =
    "Tienes 3 Marías. ¿Cuál de ellas?: María García, María López o María Ruiz."

private const val PREVIEW_PROMPT_FOUR =
    "Tienes 4 coincidencias para María. Las primeras son: María García, María López, María Ruiz. ¿Cuál?"

@Preview(name = "Picker — 3 candidates, Light", widthDp = 412, heightDp = 800)
@Composable
private fun ContactPickerOverlayThreePreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ContactPickerOverlayContent(
                prompt = PREVIEW_PROMPT_THREE,
                candidates = previewThreeMarias,
                onPick = {},
                onNone = {},
            )
        }
    }
}

@Preview(name = "Picker — 4 candidates, Light", widthDp = 412, heightDp = 800)
@Composable
private fun ContactPickerOverlayFourPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ContactPickerOverlayContent(
                prompt = PREVIEW_PROMPT_FOUR,
                candidates = previewFourMarias,
                onPick = {},
                onNone = {},
            )
        }
    }
}

@Preview(name = "Picker — Dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 800)
@Composable
private fun ContactPickerOverlayDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ContactPickerOverlayContent(
                prompt = PREVIEW_PROMPT_THREE,
                candidates = previewThreeMarias,
                onPick = {},
                onNone = {},
            )
        }
    }
}

@Preview(name = "Picker — Large font 1.5×", widthDp = 412, heightDp = 800, fontScale = 1.5f)
@Composable
private fun ContactPickerOverlayLargeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ContactPickerOverlayContent(
                prompt = PREVIEW_PROMPT_THREE,
                candidates = previewThreeMarias,
                onPick = {},
                onNone = {},
            )
        }
    }
}

@Preview(name = "Picker — Huge font 2.0× (senior-first)", widthDp = 412, heightDp = 800, fontScale = 2.0f)
@Composable
private fun ContactPickerOverlayHugeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ContactPickerOverlayContent(
                prompt = PREVIEW_PROMPT_THREE,
                candidates = previewThreeMarias,
                onPick = {},
                onNone = {},
            )
        }
    }
}

@Suppress("UNUSED")
private val previewStateExample =
    AssistantState.Confirming(
        prompt = PREVIEW_PROMPT_THREE,
        expiresAtMs = 0L,
        pendingAction =
            PendingAction(
                functionName = "call_contact",
                kind =
                    PendingAction.Kind.PickContact(
                        candidates = previewThreeMarias,
                        onPick = { HandlerResult.Spoken("ok") },
                    ),
            ),
    )
