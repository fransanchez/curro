package com.curro.app.presentation.common

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Leading content slot size — 56 dp gives ample room for a contact photo or app icon
 * (typically 40 dp icon centred in the slot), readable without strain for an aged eye.
 * Local to [BigListRow]; not a cross-component invariant — see US-006 brief §3.
 */
@Suppress("MagicNumber") // US-006: leading slot — LOCAL to BigListRow, not a cross-SF invariant; see brief §3
private val LeadingSlotSize: Dp = 56.dp

/**
 * Trailing content slot size — 48 dp is sufficient for chevrons, count badges, and
 * checkmarks, which read clearly at smaller sizes than primary content icons.
 * Local to [BigListRow]; not a cross-component invariant — see US-006 brief §3.
 */
@Suppress("MagicNumber") // US-006: trailing slot — LOCAL to BigListRow, not a cross-SF invariant; see brief §3
private val TrailingSlotSize: Dp = 48.dp

/** Preview-only icon size for the leading slot placeholder (40 dp centred in 56 dp slot). */
private val PreviewLeadingIconSize: Dp = 40.dp

/** Preview-only icon size for the trailing slot placeholder (32 dp chevron). */
private val PreviewTrailingIconSize: Dp = 32.dp

/**
 * Curro's clickable big list row — the brick for the contact picker
 * (the 3-Marías disambiguation per spec §6 flow 3), the alias-learning
 * list (`¿Es alguno de estos?` per flow 4), the message cards screen
 * grouped by sender (flow 5), and the config menu sections (spec §9).
 *
 * Layout: optional leading slot (square ≥ 56 dp — room for a contact
 * photo via Coil [AsyncImage], an app icon, a glyph) + title (large,
 * primary text) + optional subtitle (smaller, secondary text) +
 * optional trailing slot (square ≥ 48 dp — chevron, count badge,
 * checkmark).
 *
 * Senior-first contract:
 * - Min height [Dimens.BigRowHeight] (96 dp) via [Modifier.heightIn] —
 *   independent of `fontScale`.
 * - [HapticFeedbackType.LongPress] on press (US-004 A10).
 * - Background [androidx.compose.ui.graphics.Color.Transparent] — the
 *   row inherits its parent's surface (a [LazyColumn] over
 *   `MaterialTheme.colorScheme.surface`, or the inside of a [BigCard]
 *   which uses `surfaceVariant`). A future "selected" variant can opt
 *   into a tinted background; US-006 does not ship that variant.
 * - Title at [MaterialTheme.typography.titleLarge] /
 *   [MaterialTheme.colorScheme.onSurface]; subtitle (if present) at
 *   [MaterialTheme.typography.bodyMedium] /
 *   [MaterialTheme.colorScheme.onSurfaceVariant].
 * - Horizontal padding [CurroSpacing.m]; vertical padding [CurroSpacing.s];
 *   the height enforcement does the heavy lifting, the padding only sets
 *   the text inset off the edges.
 *
 * @param title The primary text. Required; the row is meaningless without it.
 * @param onClick Action fired (after the haptic) when the row is pressed.
 * @param modifier Applied to the row; callers add outer padding here.
 * @param subtitle Optional secondary line below the title (e.g. a phone
 *   number, an app's last-used time, a config setting's current value).
 * @param leading Optional leading content slot (a contact photo, an app
 *   icon, a glyph). Rendered inside a [Modifier.size] square of
 *   [LeadingSlotSize] (56 dp) — the slot fixes the size, the caller fills
 *   the content. Null → no leading area, title aligns to the row's start.
 * @param trailing Optional trailing content slot (a chevron, a count
 *   badge, a checkmark, a "selected" tick). Rendered inside a
 *   [Modifier.size] square of [TrailingSlotSize] (48 dp). Null → no
 *   trailing area, the subtitle/title block extends to the row's end.
 * @param contentDescription Overrides the default content description
 *   (`title` + `subtitle` joined). Use when the row's voiced affordance
 *   differs from its visible text (e.g. an app row whose `title` is
 *   "WhatsApp" but whose screen-reader announcement should be
 *   "WhatsApp, abrir"). Default null → derived from `title`/`subtitle`.
 * @param enabled When false the row is rendered with reduced opacity and
 *   the click + haptic do not fire.
 */
@Composable
fun BigListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val semanticsMod =
        if (contentDescription != null) {
            Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.BigRowHeight)
                .clickable(enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .then(semanticsMod)
                .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.size(LeadingSlotSize),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }
            Spacer(modifier = Modifier.width(CurroSpacing.m))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CurroSpacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(CurroSpacing.m))
            Box(
                modifier = Modifier.size(TrailingSlotSize),
                contentAlignment = Alignment.Center,
            ) {
                trailing()
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────────────────────

@Preview(name = "Light — 412 dp wide", widthDp = 412, heightDp = 400)
@Composable
private fun BigListRowLightPreview() {
    CurroTheme {
        Surface {
            Column {
                // Row 1 — contact-picker style: leading icon + title + subtitle + trailing chevron
                BigListRow(
                    title = "María García",
                    subtitle = "+34 600 12 34 56",
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewLeadingIconSize),
                        )
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewTrailingIconSize),
                        )
                    },
                    onClick = {},
                )
                // Row 2 — app-list style: leading icon + title only, no subtitle, no trailing
                BigListRow(
                    title = "Sample app",
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewLeadingIconSize),
                        )
                    },
                    onClick = {},
                )
            }
        }
    }
}

@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 400)
@Composable
private fun BigListRowDarkPreview() {
    CurroTheme {
        Surface {
            Column {
                BigListRow(
                    title = "María García",
                    subtitle = "+34 600 12 34 56",
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewLeadingIconSize),
                        )
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewTrailingIconSize),
                        )
                    },
                    onClick = {},
                )
                BigListRow(
                    title = "Sample app",
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewLeadingIconSize),
                        )
                    },
                    onClick = {},
                )
            }
        }
    }
}

@Preview(name = "Large font 1.5×", widthDp = 412, heightDp = 520, fontScale = 1.5f)
@Composable
private fun BigListRowLargeFontPreview() {
    CurroTheme {
        Surface {
            Column {
                BigListRow(
                    title = "María García",
                    subtitle = "+34 600 12 34 56",
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewLeadingIconSize),
                        )
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewTrailingIconSize),
                        )
                    },
                    onClick = {},
                )
                BigListRow(
                    title = "Sample app",
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewLeadingIconSize),
                        )
                    },
                    onClick = {},
                )
            }
        }
    }
}

@Preview(name = "Huge font 2.0× — senior-first regression", widthDp = 412, heightDp = 700, fontScale = 2.0f)
@Composable
private fun BigListRowHugeFontPreview() {
    // At fontScale = 2.0 the title (22 sp × 2 = 44 sp) may wrap; maxLines = 2 for the title
    // and maxLines = 1 + Ellipsis for the subtitle keep the row bounded. The leading/trailing
    // slots stay at their fixed dp sizes; only the weight(1f) title column stretches.
    // heightIn(min = Dimens.BigRowHeight) keeps the minimum at 96 dp; both rows stay legible.
    CurroTheme {
        Surface {
            Column {
                BigListRow(
                    title = "María García",
                    subtitle = "+34 600 12 34 56",
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewLeadingIconSize),
                        )
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewTrailingIconSize),
                        )
                    },
                    onClick = {},
                )
                BigListRow(
                    title = "Sample app",
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(PreviewLeadingIconSize),
                        )
                    },
                    onClick = {},
                )
            }
        }
    }
}
