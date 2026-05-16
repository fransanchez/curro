package com.curro.app.presentation.assistant

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.assistant.AssistantState
import com.curro.app.assistant.PendingAction
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.presentation.common.BigYesNoRow
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * `Confirming`-state overlay (SF-6.2 / US-042; spec §6 flow 2 + §11).
 *
 * Covers the launcher home and shows: the resolved prompt (e.g. "¿Llamo a
 * Pepe?") in very-large text, then a [BigYesNoRow] with SÍ in primary
 * (terracota) and NO in secondary (olivo). Each button is ≥ 96 dp by the
 * shared `BigYesNoRow` modifier; haptic on tap.
 *
 * Senior-first contract:
 *  - prompt at `displayMedium`, the largest non-clock type-scale token —
 *    readable at arm's length;
 *  - `semantics { liveRegion = Polite }` so TalkBack reads the prompt when
 *    the overlay appears;
 *  - SÍ / NO ≥ 96 dp + haptic via [BigYesNoRow];
 *  - dark mode + 2× font scale supported via [CurroTheme] and the type
 *    scale.
 *
 * Voice yes/no is independent of this composable — the coordinator runs the
 * constrained-vocabulary STT in parallel. The composable knows nothing about
 * STT.
 */
@Composable
fun ConfirmationOverlay(
    state: AssistantState.Confirming,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmationOverlayContent(
        prompt = state.prompt,
        onYes = onYes,
        onNo = onNo,
        modifier = modifier,
    )
}

@Composable
internal fun ConfirmationOverlayContent(
    prompt: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = CurroSpacing.l),
        ) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(modifier = Modifier.height(CurroSpacing.xxl))
            BigYesNoRow(
                onYes = onYes,
                onNo = onNo,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────────────────────

private const val PREVIEW_PROMPT = "¿Llamo a Pepe?"

@Preview(name = "Confirmation — Light", widthDp = 412, heightDp = 800)
@Composable
private fun ConfirmationOverlayLightPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfirmationOverlayContent(prompt = PREVIEW_PROMPT, onYes = {}, onNo = {})
        }
    }
}

@Preview(
    name = "Confirmation — Dark",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun ConfirmationOverlayDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfirmationOverlayContent(prompt = PREVIEW_PROMPT, onYes = {}, onNo = {})
        }
    }
}

@Preview(name = "Confirmation — Large font 1.5×", widthDp = 412, heightDp = 800, fontScale = 1.5f)
@Composable
private fun ConfirmationOverlayLargeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfirmationOverlayContent(prompt = PREVIEW_PROMPT, onYes = {}, onNo = {})
        }
    }
}

@Preview(name = "Confirmation — Huge font 2.0× (senior-first)", widthDp = 412, heightDp = 800, fontScale = 2.0f)
@Composable
private fun ConfirmationOverlayHugeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            // Confirms text + buttons fit under fontScale = 2.0 — the
            // BigYesNoRow's heightIn(Dimens.BigButtonHeight) is independent
            // of fontScale, so each button stays ≥ 96 dp.
            ConfirmationOverlayContent(
                prompt = "¿Llamo a Pepe Martínez?",
                onYes = {},
                onNo = {},
            )
        }
    }
}

@Suppress("UNUSED")
private val previewStateExample =
    AssistantState.Confirming(
        prompt = PREVIEW_PROMPT,
        expiresAtMs = 0L,
        pendingAction =
            PendingAction(
                functionName = "call_contact",
                kind = PendingAction.Kind.YesNo(onConfirm = { HandlerResult.Spoken("ok") }),
            ),
    )
