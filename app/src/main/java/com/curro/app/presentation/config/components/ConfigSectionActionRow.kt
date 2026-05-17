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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.curro.app.presentation.config.ConfigEvent
import com.curro.app.presentation.config.ConfigSection
import com.curro.app.presentation.theme.CurroSpacing

/**
 * A tappable config row that fires a [ConfigEvent] instead of navigating to a sub-route.
 *
 * Used for the "Devolver el launcher al sistema" row — tapping it dispatches
 * [ConfigEvent.OpenHomeSettings] to [ConfigViewModel.onEvent]. The row renders a
 * leading exit icon and a persistent help subtitle (unlike [ConfigSectionRow] whose
 * summary is optional).
 *
 * Row height is 72 dp (not the senior-first 96 dp floor — this screen is for
 * Fran, `launcher-ui` rule 5). The minimum is a `heightIn(min = 72.dp)` so
 * content can grow with large font scales.
 */
@Composable
fun ConfigSectionActionRow(
    section: ConfigSection.Action,
    onEvent: (ConfigEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable { onEvent(section.event) }
                .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = null,
            modifier = Modifier.size(CurroSpacing.l),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(CurroSpacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(section.titleResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(CurroSpacing.xs))
            Text(
                text = stringResource(section.summaryResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
