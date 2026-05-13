package com.curro.app.presentation.common

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Curro's SÍ/NO confirmation buttons row — the brick for the confirmation
 * overlay (`¿Llamo a Pepito?`), every future `needs_confirmation = true` action
 * the FSM lands in `confirming`, and the alias-learning subflow's confirmation
 * step (`¿Es alguno de estos?` → yes/no fallback).
 *
 * Two filled buttons side-by-side. SÍ in `primary` (terracota — warm
 * affirmation); NO in `secondary` (olivo — calm rejection). NO is NEVER
 * `error`-coloured: saying "no" is not a failure condition (`brand-design`
 * line 322; spec §2 Curro's voice — fail comprehensibly, not punitively).
 *
 * Senior-first contract:
 * - Each button ≥ [Dimens.BigButtonHeight] tall via [Modifier.heightIn]
 *   (dp, independent of `fontScale`).
 * - [HapticFeedbackType.LongPress] on each press (US-004 A10): confirms the
 *   press registered with tactile certainty before the screen updates —
 *   essential for reduced fine motor control.
 * - Generous gap: `Arrangement.spacedBy(CurroSpacing.l)` (24 dp) between
 *   buttons, so a slip from SÍ does not land on NO and vice versa.
 * - Labels at [MaterialTheme.typography.titleLarge] (22 sp — comfortable
 *   button label, mirrors [BigPrimaryButton]).
 *
 * @param onYes Action fired (after the haptic) when SÍ is pressed.
 * @param onNo  Action fired (after the haptic) when NO is pressed.
 * @param modifier Applied to the [Row]; callers add outer padding / weight here.
 * @param yesText Override the default label ("SÍ" via [R.string.copy_yes]).
 *   Use when a non-default affirmation reads better — e.g. an alternate
 *   "VALE" if a future flow demands it. Leave defaulted in normal use.
 * @param noText Override the default label ("NO" via [R.string.copy_no]).
 * @param enabled When false both buttons are rendered disabled; haptic and
 *   onYes/onNo do not fire. Useful while a confirmation is being processed.
 */
@Composable
fun BigYesNoRow(
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
    yesText: String = stringResource(R.string.copy_yes),
    noText: String = stringResource(R.string.copy_no),
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CurroSpacing.l),
    ) {
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onYes()
            },
            enabled = enabled,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.BigButtonHeight),
            shape = MaterialTheme.shapes.medium,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Text(
                text = yesText,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNo()
            },
            enabled = enabled,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.BigButtonHeight),
            shape = MaterialTheme.shapes.medium,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
        ) {
            Text(
                text = noText,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────────────────────

@Preview(name = "Light — 412 dp wide", widthDp = 412, heightDp = 200)
@Composable
private fun BigYesNoRowLightPreview() {
    CurroTheme {
        Surface(modifier = Modifier) {
            BigYesNoRow(
                onYes = {},
                onNo = {},
                modifier = Modifier,
            )
        }
    }
}

@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 200)
@Composable
private fun BigYesNoRowDarkPreview() {
    CurroTheme {
        Surface {
            BigYesNoRow(
                onYes = {},
                onNo = {},
            )
        }
    }
}

@Preview(name = "Large font 1.5×", widthDp = 412, heightDp = 240, fontScale = 1.5f)
@Composable
private fun BigYesNoRowLargeFontPreview() {
    CurroTheme {
        Surface {
            BigYesNoRow(
                onYes = {},
                onNo = {},
            )
        }
    }
}

@Preview(name = "Huge font 2.0× — senior-first regression", widthDp = 412, heightDp = 320, fontScale = 2.0f)
@Composable
private fun BigYesNoRowHugeFontPreview() {
    // At fontScale = 2.0 the label grows to 44 sp. The two 2-character labels ("SÍ", "NO")
    // fit comfortably inside two weight(1f) buttons on a 412 dp width minus 32 dp horizontal
    // padding from the parent surface minus 24 dp spacing: well over 170 dp per button.
    // The heightIn(min = Dimens.BigButtonHeight) keeps each button ≥ 96 dp. No clipping.
    CurroTheme {
        Surface {
            BigYesNoRow(
                onYes = {},
                onNo = {},
            )
        }
    }
}
