package com.curro.app.presentation.common

import android.content.res.Configuration.UI_MODE_NIGHT_YES
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Curro's primary call-to-action button — the brick for SÍ, "Más apps",
 * "Hazme tu pantalla de inicio", and every overlay primary action.
 *
 * Senior-first contract:
 * - ≥ [Dimens.MinTapTarget] tall via [Modifier.heightIn] (dp, independent of fontScale).
 * - [HapticFeedbackType.LongPress] on press (A10): confirms the press registered with
 *   tactile certainty before the screen updates — essential for reduced fine motor control.
 * - High-contrast primary / onPrimary from [MaterialTheme.colorScheme].
 * - Label rendered at [MaterialTheme.typography.titleLarge] (≥ 22 sp placeholder floor).
 *
 * @param text Visible label. Also serves as the accessibility content description unless
 *   [contentDescription] is overridden.
 * @param onClick Action executed after the haptic feedback fires.
 * @param modifier Applied to the [Button]; callers may add padding / weight here.
 * @param enabled When false the button is rendered disabled; haptic and onClick do not fire.
 * @param contentDescription Overrides [text] in the semantics tree. Use when the visible
 *   label is symbolic (e.g. an emoji) and the screen reader needs the word.
 */
@Composable
fun BigPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.MinTapTarget)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
        shape = MaterialTheme.shapes.medium,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────────────────────

@Preview(name = "Light — 412 dp wide", widthDp = 412, heightDp = 200)
@Composable
private fun BigPrimaryButtonLightPreview() {
    CurroTheme {
        Surface {
            BigPrimaryButton(text = "Primary action", onClick = {})
        }
    }
}

@Preview(name = "Dark", widthDp = 412, heightDp = 200, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BigPrimaryButtonDarkPreview() {
    CurroTheme {
        Surface {
            BigPrimaryButton(text = "Primary action", onClick = {})
        }
    }
}

@Preview(name = "Large font 1.5×", widthDp = 412, heightDp = 200, fontScale = 1.5f)
@Composable
private fun BigPrimaryButtonLargeFontPreview() {
    CurroTheme {
        Surface {
            BigPrimaryButton(text = "Primary action", onClick = {})
        }
    }
}

@Preview(name = "Huge font 2.0× — senior-first regression", widthDp = 412, heightDp = 300, fontScale = 2.0f)
@Composable
private fun BigPrimaryButtonHugeFontPreview() {
    // At fontScale = 2.0 the label grows. The heightIn(min = Dimens.MinTapTarget) keeps the
    // button ≥ 96 dp; the Button expands further to wrap the taller label. No clipping.
    CurroTheme {
        Surface {
            BigPrimaryButton(text = "Primary action", onClick = {})
        }
    }
}
