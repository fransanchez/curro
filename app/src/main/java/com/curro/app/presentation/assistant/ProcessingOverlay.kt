package com.curro.app.presentation.assistant

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.curro.app.R
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Processing-state overlay (SF-5.5 / US-039).
 *
 * Spec §11: "Un momento…" with a **non-animated** indicator. A row of three
 * static dots replaces the Phase-2 animated wave — animaciones complejas
 * distraen.
 *
 * The composable doesn't take a transcript — it's a "waiting" affordance, not
 * an echo of what the user said. (`AssistantState.Processing.transcript` is
 * available for future overlays that want it; this SF doesn't render it.)
 */
@Composable
fun ProcessingOverlay(modifier: Modifier = Modifier) {
    ProcessingOverlayContent(modifier = modifier)
}

@Composable
private fun ProcessingOverlayContent(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .testTag(TAG_PROCESSING_OVERLAY)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(CurroSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CurroSpacing.s)) {
            repeat(DOT_COUNT) {
                Box(
                    modifier =
                        Modifier
                            .size(CurroSpacing.l)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = CircleShape,
                            ),
                )
            }
        }
        Spacer(Modifier.height(CurroSpacing.l))
        Text(
            text = stringResource(R.string.copy_processing),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .testTag(TAG_PROCESSING_HEADLINE)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

// ─── Test tags ────────────────────────────────────────────────────────────────

const val TAG_PROCESSING_OVERLAY: String = "ProcessingOverlay/Root"
const val TAG_PROCESSING_HEADLINE: String = "ProcessingOverlay/Headline"

private const val DOT_COUNT: Int = 3

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "Processing — Light", widthDp = 412, heightDp = 800)
@Composable
private fun ProcessingOverlayLightPreview() {
    CurroTheme {
        ProcessingOverlayContent()
    }
}

@Preview(
    name = "Processing — Dark",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun ProcessingOverlayDarkPreview() {
    CurroTheme {
        ProcessingOverlayContent()
    }
}

@Preview(
    name = "Processing — Light, fontScale 2.0",
    widthDp = 412,
    heightDp = 800,
    fontScale = 2.0f,
)
@Composable
private fun ProcessingOverlayLargeFontPreview() {
    CurroTheme {
        ProcessingOverlayContent()
    }
}
