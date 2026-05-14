package com.curro.app.presentation.launcher

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Curro's main mic button — the dominant surface of the launcher home (SF-1.3 / US-011).
 *
 * Occupies ≥ [Dimens.MIC_BUTTON_MIN_HEIGHT_FRACTION] (40 %) of the available vertical
 * space and the full width (spec §11). It is the visual focal point: a full-width
 * terracotta block labelled "CURRO" with a large mic glyph.
 *
 * In Phase 1 the button is **inert** — pressing it shows a Spanish toast via the
 * [LauncherSideEffect.ShowToast] Channel pattern. The voice pipeline lands in Phase 2
 * (SF-2.x) and will replace the inert handler with `FSM.startListening()`.
 *
 * Senior-first contract:
 * - Touch target spans the full width × ≥ 40 % of screen height — vastly exceeding
 *   the ≥ 96 dp [Dimens.MinTapTarget] requirement (spec §3, §11).
 * - [HapticFeedbackType.LongPress] on every press (US-004 A10).
 * - `contentDescription` = the mic label ("CURRO") so TalkBack announces it clearly.
 * - Background [MaterialTheme.colorScheme.primary] (terracotta) / foreground
 *   [MaterialTheme.colorScheme.onPrimary] — high-contrast pair per brand-design.
 *
 * @param onPressed Called after haptic fires when the button is tapped.
 * @param modifier Applied to the root [Surface]. Callers should not override width/height.
 * @param enabled When false the tap target is disabled; haptic and [onPressed] do not fire.
 */
@Composable
fun MicButton(
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val micLabel = stringResource(R.string.copy_home_mic_label)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(Dimens.MIC_BUTTON_MIN_HEIGHT_FRACTION)
                .semantics {
                    contentDescription = micLabel
                    role = Role.Button
                }
                .clickable(enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPressed()
                },
        shape = MaterialTheme.shapes.large,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = Dimens.CardElevation,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // semantics on the Surface covers the contentDescription; icon is decorative.
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(Dimens.LargeIconSize * 2),
                tint =
                    if (enabled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Spacer(modifier = Modifier.height(CurroSpacing.s))
            Text(
                text = micLabel,
                style = MaterialTheme.typography.displaySmall,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Previews (4 total: light / dark / fontScale 1.5 / fontScale 2.0) ---

@Preview(name = "MicButton — Light", widthDp = 412, heightDp = 400)
@Composable
private fun MicButtonLightPreview() {
    CurroTheme {
        MicButton(onPressed = {})
    }
}

@Preview(
    name = "MicButton — Dark",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 400,
)
@Composable
private fun MicButtonDarkPreview() {
    CurroTheme {
        MicButton(onPressed = {})
    }
}

@Preview(
    name = "MicButton — Large font (1.5×)",
    widthDp = 412,
    heightDp = 440,
    fontScale = 1.5f,
)
@Composable
private fun MicButtonLargeFontPreview() {
    CurroTheme {
        MicButton(onPressed = {})
    }
}

@Preview(
    name = "MicButton — Huge font (2.0×, senior-first)",
    widthDp = 412,
    heightDp = 500,
    fontScale = 2.0f,
)
@Composable
private fun MicButtonHugeFontPreview() {
    // At fontScale = 2.0 the "CURRO" label grows. fillMaxHeight(fraction) keeps the button
    // occupying 40 % of the available height; the Column centres the content vertically so
    // neither the icon nor the label clips.
    CurroTheme {
        MicButton(onPressed = {})
    }
}
