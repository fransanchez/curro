package com.curro.app.presentation.config.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
 * An inline-toggle config row (SF-8.1 / US-050).
 *
 * The title and short help line are always visible (not collapsed). In SF-8.1
 * the [Switch.onCheckedChange] fires [ConfigEvent.ToggleChanged] which the ViewModel
 * handles by logging `Log.w` — the toggle visually snaps back to the DataStore value
 * on the next emission because neither toggle's setter is wired in SF-8.1.
 *
 * Row height minimum is 72 dp (config-menu density, not the senior-first 96 dp floor).
 */
@Composable
fun ConfigSectionToggleRow(
    section: ConfigSection.Toggle,
    onEvent: (ConfigEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(section.titleResId),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(CurroSpacing.xs))
            Text(
                text = stringResource(section.helpResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = section.value,
            onCheckedChange = { newValue ->
                onEvent(ConfigEvent.ToggleChanged(section, newValue))
            },
        )
    }
}
