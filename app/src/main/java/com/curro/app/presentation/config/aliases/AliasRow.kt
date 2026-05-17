package com.curro.app.presentation.config.aliases

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.curro.app.R
import com.curro.app.data.local.AliasSource
import com.curro.app.domain.repository.AliasView
import com.curro.app.presentation.theme.CurroSpacing

/**
 * Single alias row in [AliasesScreen] (SF-8.2 / US-051).
 *
 * Displays alias → contact mapping with source badge ("Aprendido" / "Manual"),
 * use count, and two icon actions (edit, delete). The edit icon opens
 * [AddOrEditAliasDialog]; the delete icon opens [DeleteAliasConfirmDialog].
 *
 * Row height is 72 dp minimum (config-menu density — not the 96 dp senior floor).
 */
@Composable
fun AliasRow(
    aliasView: AliasView,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ALIAS_ROW_MIN_HEIGHT)
                .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = aliasView.alias,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SourceBadge(source = aliasView.source)
            }
            Spacer(modifier = Modifier.height(CurroSpacing.xs))
            Text(
                text = aliasView.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(CurroSpacing.xxl),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.cd_alias_edit, aliasView.alias),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(CurroSpacing.xxl),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.cd_alias_delete, aliasView.alias),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SourceBadge(
    source: AliasSource,
    modifier: Modifier = Modifier,
) {
    val label =
        when (source) {
            AliasSource.LEARNED -> stringResource(R.string.copy_aliases_source_learned)
            AliasSource.EXPLICIT -> stringResource(R.string.copy_aliases_source_explicit)
            AliasSource.SUGGESTED -> stringResource(R.string.copy_aliases_source_explicit)
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = CurroSpacing.s),
    )
}

// 72 dp — config-menu density, not the 96 dp senior floor.
private val ALIAS_ROW_MIN_HEIGHT = 72.dp
