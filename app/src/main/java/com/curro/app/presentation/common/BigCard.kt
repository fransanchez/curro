package com.curro.app.presentation.common

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Curro's big card surface — the brick for WhatsApp message cards,
 * contact-picker rows, config-menu rows, and any "block of large readable
 * content" the app needs to show.
 *
 * When [onClick] is non-null, the card becomes a clickable surface ≥
 * [Dimens.BigRowHeight] tall with haptic feedback ([HapticFeedbackType.LongPress],
 * A10) — useful for picker rows, tappable list items, big actionable cards.
 *
 * When [onClick] is null, the card is a pure display surface: no tap affordance,
 * no haptic, no [Modifier.clickable], no minimum height enforcement. The card
 * sizes to its content. Example: a WhatsApp message card while being read.
 *
 * Content slot is a [ColumnScope]: callers stack [Text] / [Icon] / [Image]
 * inside, separating with [androidx.compose.foundation.layout.Spacer]s sized
 * via [CurroSpacing].
 *
 * @param modifier Applied to the [Card] itself; callers add outer padding / weight here.
 * @param onClick Null → display surface. Non-null → clickable surface with haptic + min height.
 * @param content Content slot. Use [CurroSpacing] for internal spacing.
 */
@Composable
fun BigCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val clickableMod =
        if (onClick != null) {
            Modifier
                .heightIn(min = Dimens.BigRowHeight)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
        } else {
            Modifier // intentional: no minHeight, no clickable, no haptic
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickableMod),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation),
    ) {
        Column(modifier = Modifier.padding(CurroSpacing.l)) {
            content()
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────────────────────

@Preview(name = "Light — read-only card", widthDp = 412)
@Composable
private fun BigCardLightReadOnlyPreview() {
    CurroTheme {
        Surface {
            BigCard(onClick = null) {
                Text(text = "Sample card content", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Secondary line", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Light — clickable card", widthDp = 412)
@Composable
private fun BigCardLightClickablePreview() {
    CurroTheme {
        Surface {
            BigCard(onClick = {}) {
                Text(text = "Tappable picker row", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Tap to select", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Dark — read-only card", widthDp = 412, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BigCardDarkReadOnlyPreview() {
    CurroTheme {
        Surface {
            BigCard(onClick = null) {
                Text(text = "Sample card content", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Secondary line", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Dark — clickable card", widthDp = 412, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BigCardDarkClickablePreview() {
    CurroTheme {
        Surface {
            BigCard(onClick = {}) {
                Text(text = "Tappable picker row", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Tap to select", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Large font 1.5× — read-only card", widthDp = 412, fontScale = 1.5f)
@Composable
private fun BigCardLargeFontReadOnlyPreview() {
    CurroTheme {
        Surface {
            BigCard(onClick = null) {
                Text(text = "Sample card content", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Secondary line", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Large font 1.5× — clickable card", widthDp = 412, fontScale = 1.5f)
@Composable
private fun BigCardLargeFontClickablePreview() {
    CurroTheme {
        Surface {
            BigCard(onClick = {}) {
                Text(text = "Tappable picker row", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Tap to select", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Huge font 2.0× — read-only card (senior-first regression)", widthDp = 412, fontScale = 2.0f)
@Composable
private fun BigCardHugeFontReadOnlyPreview() {
    CurroTheme {
        Surface {
            BigCard(onClick = null) {
                Text(text = "Sample card content", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Secondary line", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Huge font 2.0× — clickable card (senior-first regression)", widthDp = 412, fontScale = 2.0f)
@Composable
private fun BigCardHugeFontClickablePreview() {
    CurroTheme {
        Surface {
            BigCard(onClick = {}) {
                Text(text = "Tappable picker row", style = MaterialTheme.typography.bodyLarge)
                Text(text = "Tap to select", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
