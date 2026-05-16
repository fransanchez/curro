package com.curro.app.presentation.assistant

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * ErrorRecovery-state overlay (SF-5.5 / US-039).
 *
 * Spec §2 "Fallar de forma comprensible" — the line is displayed plainly in
 * `errorContainer` colours; the visual carries the "something didn't work"
 * mood without an alarming icon. Senior-first: no flashing, no shake, no
 * fussy animation.
 *
 * Phase 5 doesn't use `state.failureCount` in the UI (it just renders
 * `message`). The field is on the state so Phase 6+ can render count-aware
 * hints (e.g., "we're giving up" at 3) without re-signature.
 */
@Composable
fun ErrorRecoveryOverlay(
    state: AssistantState.ErrorRecovery,
    modifier: Modifier = Modifier,
) {
    ErrorRecoveryOverlayContent(message = state.message, modifier = modifier)
}

@Composable
private fun ErrorRecoveryOverlayContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .testTag(TAG_ERROR_OVERLAY)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(CurroSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .testTag(TAG_ERROR_MESSAGE)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

// ─── Test tags ────────────────────────────────────────────────────────────────

const val TAG_ERROR_OVERLAY: String = "ErrorRecoveryOverlay/Root"
const val TAG_ERROR_MESSAGE: String = "ErrorRecoveryOverlay/Message"

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "ErrorRecovery — Light, fail 1", widthDp = 412, heightDp = 800)
@Composable
private fun ErrorRecoveryOverlayLightPreview() {
    CurroTheme {
        ErrorRecoveryOverlayContent(message = "No te he oído bien, ¿puedes repetirlo?")
    }
}

@Preview(
    name = "ErrorRecovery — Dark, fail 2",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun ErrorRecoveryOverlayDarkPreview() {
    CurroTheme {
        ErrorRecoveryOverlayContent(
            message = "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto.",
        )
    }
}

@Preview(
    name = "ErrorRecovery — Light, fontScale 2.0, fail 3",
    widthDp = 412,
    heightDp = 800,
    fontScale = 2.0f,
)
@Composable
private fun ErrorRecoveryOverlayLargeFontPreview() {
    CurroTheme {
        ErrorRecoveryOverlayContent(
            message = "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo.",
        )
    }
}
