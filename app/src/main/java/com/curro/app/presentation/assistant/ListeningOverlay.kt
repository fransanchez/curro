package com.curro.app.presentation.assistant

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.curro.app.R
import com.curro.app.assistant.AssistantState
import com.curro.app.presentation.theme.CurroListeningTintDark
import com.curro.app.presentation.theme.CurroListeningTintLight
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Listening-state overlay (SF-5.5 / US-039).
 *
 * Renders on top of the launcher home (the home's mic button stays interactive —
 * the user can interrupt by pressing it again).
 *
 * Spec §11: blue tint (`CurroListeningTint*`), "Te escucho…" headline, live
 * transcript below in large text. Senior-first: no fussy animation — a single
 * static mic glyph above the headline.
 */
@Composable
fun ListeningOverlay(
    state: AssistantState.Listening,
    modifier: Modifier = Modifier,
) {
    ListeningOverlayContent(partial = state.partial, modifier = modifier)
}

@Composable
private fun ListeningOverlayContent(
    partial: String,
    modifier: Modifier = Modifier,
) {
    val tint = if (isSystemInDarkTheme()) CurroListeningTintDark else CurroListeningTintLight
    Column(
        modifier =
            modifier
                .testTag(TAG_OVERLAY)
                .fillMaxSize()
                .background(tint)
                .padding(CurroSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The headline below ("Te escucho…") is the accessible label, so the icon
        // omits a contentDescription to avoid TalkBack reading the cue twice.
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(MIC_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(CurroSpacing.l))
        Text(
            text = stringResource(R.string.copy_listening_prompt),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .testTag(TAG_HEADLINE)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (partial.isNotBlank()) {
            Spacer(Modifier.height(CurroSpacing.l))
            Text(
                text = partial,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .testTag(TAG_TRANSCRIPT)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

// ─── Test tags ────────────────────────────────────────────────────────────────

const val TAG_OVERLAY: String = "ListeningOverlay/Root"
const val TAG_HEADLINE: String = "ListeningOverlay/Headline"
const val TAG_TRANSCRIPT: String = "ListeningOverlay/Transcript"

private val MIC_ICON_SIZE = 96.dp

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "Listening — Light, partial transcript", widthDp = 412, heightDp = 800)
@Composable
private fun ListeningOverlayLightPreview() {
    CurroTheme {
        ListeningOverlayContent(partial = "Llama a Pepito")
    }
}

@Preview(
    name = "Listening — Dark, partial transcript",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun ListeningOverlayDarkPreview() {
    CurroTheme {
        ListeningOverlayContent(partial = "Llama a Pepito")
    }
}

@Preview(
    name = "Listening — Light, fontScale 2.0",
    widthDp = 412,
    heightDp = 800,
    fontScale = 2.0f,
)
@Composable
private fun ListeningOverlayLargeFontPreview() {
    CurroTheme {
        ListeningOverlayContent(partial = "Llama a Pepito el de los olivos")
    }
}
