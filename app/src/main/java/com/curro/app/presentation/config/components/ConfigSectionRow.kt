package com.curro.app.presentation.config.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.curro.app.presentation.config.ConfigSection
import com.curro.app.presentation.theme.CurroSpacing

/**
 * A tappable config section row with a right chevron (SF-8.1 / US-050).
 *
 * Row height is 72 dp (not the senior-first 96 dp floor — this screen is for
 * Fran, `launcher-ui` rule 5). The minimum is a `heightIn(min = 72.dp)` so
 * content can grow with large font scales.
 *
 * Destructive rows (the reset section) render the title in
 * `MaterialTheme.colorScheme.error`. Colour alone is never the only signal —
 * the text "Reset de aprendizaje" makes the action explicit (`brand-design` rule 5).
 */
@Composable
fun ConfigSectionRow(
    section: ConfigSection.Navigable,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable { onNavigate(section.route) }
                .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(section.titleResId),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (section.destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            section.summary?.let { summary ->
                Spacer(modifier = Modifier.height(CurroSpacing.xs))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(CurroSpacing.l),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
