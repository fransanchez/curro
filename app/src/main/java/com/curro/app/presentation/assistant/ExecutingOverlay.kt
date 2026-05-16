package com.curro.app.presentation.assistant

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.assistant.AssistantState
import com.curro.app.presentation.common.BigCard
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Executing-state overlay (SF-5.5 / US-039).
 *
 * Renders Curro's spoken line large in a [BigCard]. Spec §4.6: every Curro→user
 * message is spoken **and** shown — the screen reinforces the voice, never
 * replaces it.
 *
 * Phase 5 only renders `state.speech`. Phase 6/7 will add a `screen` branch
 * when `state.screen` is non-null (e.g., the message-cards screen, the
 * contact-picker screen) — Phase 5 always sees `screen == null` because no
 * Phase-4 handler populates it.
 */
@Composable
fun ExecutingOverlay(
    state: AssistantState.Executing,
    modifier: Modifier = Modifier,
) {
    ExecutingOverlayContent(speech = state.speech, modifier = modifier)
}

@Composable
private fun ExecutingOverlayContent(
    speech: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .testTag(TAG_EXECUTING_OVERLAY)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(CurroSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BigCard(modifier = Modifier.fillMaxWidth(), onClick = null) {
            Text(
                text = speech,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .testTag(TAG_EXECUTING_SPEECH)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

// ─── Test tags ────────────────────────────────────────────────────────────────

const val TAG_EXECUTING_OVERLAY: String = "ExecutingOverlay/Root"
const val TAG_EXECUTING_SPEECH: String = "ExecutingOverlay/Speech"

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "Executing — Light, short speech", widthDp = 412, heightDp = 800)
@Composable
private fun ExecutingOverlayLightPreview() {
    CurroTheme {
        ExecutingOverlayContent(speech = "Llamando a Pepito.")
    }
}

@Preview(
    name = "Executing — Dark, longer speech",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun ExecutingOverlayDarkPreview() {
    CurroTheme {
        ExecutingOverlayContent(
            speech = "Son las trece y cuarenta y siete del miércoles 13 de mayo.",
        )
    }
}

@Preview(
    name = "Executing — Light, fontScale 2.0",
    widthDp = 412,
    heightDp = 800,
    fontScale = 2.0f,
)
@Composable
private fun ExecutingOverlayLargeFontPreview() {
    CurroTheme {
        ExecutingOverlayContent(speech = "Tienes 3 mensajes de Pepito y 1 de Lucía.")
    }
}
