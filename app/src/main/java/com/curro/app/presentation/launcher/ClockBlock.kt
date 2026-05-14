package com.curro.app.presentation.launcher

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.domain.model.ClockState
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Launcher home clock and date display (SF-1.2 / US-010).
 *
 * Renders two lines centred in a tappable block:
 * - **Time** (`HH:mm`) in [MaterialTheme.typography.displayLarge] (72 sp ExtraBold) —
 *   the "clock" role in [com.curro.app.presentation.theme.CurroTypography], the visual
 *   focal point of the launcher home (spec §11, §3).
 * - **Date** (`"EEEE d MMMM"` in sentence case, e.g. *"Miércoles 13 mayo"*) in
 *   [MaterialTheme.typography.headlineLarge] (32 sp Bold).
 *
 * The entire block is [Modifier.clickable] so the five-tap gesture (SF-1.6, spec §9)
 * can count taps on the full area — not just on the text glyphs, which are a small
 * fraction of the block's physical size. For now [onClockTapped] is a no-op stub wired
 * in [CurroNavHost]; SF-1.6 replaces it with a tap-counter lambda.
 *
 * No [androidx.compose.material3.Scaffold] or `statusBarsPadding()` — this composable
 * lives inside [CurroNavHost]'s single [Scaffold] (No-Double-Padding rule, CLAUDE.md).
 *
 * @param clockState Pre-formatted time + date strings from [LauncherUiState.clock].
 * @param onClockTapped Fires on every tap — SF-1.6 wires the five-tap-within-3-s counter.
 * @param modifier Applied to the root [Column].
 */
@Composable
fun ClockBlock(
    clockState: ClockState,
    onClockTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_clock),
                    onClick = onClockTapped,
                )
                .padding(vertical = CurroSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = clockState.timeText,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (clockState.dateText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(CurroSpacing.xs))
            Text(
                text = clockState.dateText,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Previews (4 total: light / dark / large-font-1.5 / large-font-2.0) ---

private val previewClockState = ClockState(timeText = "12:47", dateText = "Miércoles 13 mayo")

@Preview(name = "ClockBlock — Light", widthDp = 412, heightDp = 200)
@Composable
private fun ClockBlockLightPreview() {
    CurroTheme {
        Surface {
            ClockBlock(
                clockState = previewClockState,
                onClockTapped = {},
            )
        }
    }
}

@Preview(
    name = "ClockBlock — Dark",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 200,
)
@Composable
private fun ClockBlockDarkPreview() {
    CurroTheme {
        Surface {
            ClockBlock(
                clockState = previewClockState,
                onClockTapped = {},
            )
        }
    }
}

@Preview(
    name = "ClockBlock — Large font (1.5×)",
    widthDp = 412,
    heightDp = 280,
    fontScale = 1.5f,
)
@Composable
private fun ClockBlockLargeFontPreview() {
    CurroTheme {
        Surface {
            ClockBlock(
                clockState = previewClockState,
                onClockTapped = {},
            )
        }
    }
}

@Preview(
    name = "ClockBlock — Huge font (2.0×, senior-first)",
    widthDp = 412,
    heightDp = 360,
    fontScale = 2.0f,
)
@Composable
private fun ClockBlockHugeFontPreview() {
    CurroTheme {
        Surface {
            ClockBlock(
                clockState = previewClockState,
                onClockTapped = {},
            )
        }
    }
}
