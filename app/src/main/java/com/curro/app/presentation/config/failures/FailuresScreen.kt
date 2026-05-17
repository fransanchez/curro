package com.curro.app.presentation.config.failures

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.curro.app.R
import com.curro.app.data.local.FailureKind
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Failed-command log screen (SF-8.6 / US-055).
 *
 * Shows the 50 most recent failures; Fran can filter by [FailureKind] and clear the log.
 * Transcripts are NOT displayed — only [FailureView.displayTime], [FailureView.kind], and
 * [FailureView.details] are shown (privacy: spec §12).
 *
 * No [androidx.compose.material3.Scaffold] / TopAppBar — the parent [CurroNavHost] Scaffold
 * provides [Modifier.padding(innerPadding)] (No-Double-Padding rule). Back chevron at TopStart.
 */
@Composable
fun FailuresScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FailuresViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FailuresContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@Suppress("LongMethod")
internal fun FailuresContent(
    uiState: FailuresUiState,
    onEvent: (FailuresEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top spacing to clear the back chevron
            Spacer(modifier = Modifier.height(Dimens.MinTapTarget))

            // Screen title
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.copy_failures_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = { onEvent(FailuresEvent.ClearPressed) }) {
                    Text(
                        text = stringResource(R.string.copy_failures_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Filter chips row
            FilterChipsRow(
                activeFilter = uiState.activeFilter,
                onFilterChanged = { kind -> onEvent(FailuresEvent.FilterChanged(kind)) },
            )

            HorizontalDivider()

            // Failures list or empty state
            if (uiState.visibleFailures.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.copy_failures_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(CurroSpacing.m),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = uiState.visibleFailures, key = { it.id }) { failure ->
                        FailureRow(failure = failure)
                        HorizontalDivider()
                    }
                }
            }
        }

        // Back chevron — TopStart overlay (No-Double-Padding rule).
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .size(Dimens.MinTapTarget),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier.size(Dimens.LargeIconSize),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Clear confirmation dialog
        if (uiState.showClearDialog) {
            ClearLogConfirmDialog(
                onConfirm = { onEvent(FailuresEvent.ConfirmClear) },
                onDismiss = { onEvent(FailuresEvent.DismissClearDialog) },
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    activeFilter: FailureKind?,
    onFilterChanged: (FailureKind?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CurroSpacing.xs),
    ) {
        FilterChip(
            selected = activeFilter == null,
            onClick = { onFilterChanged(null) },
            label = { Text(stringResource(R.string.copy_failures_filter_all)) },
        )
        FailureKind.entries.forEach { kind ->
            FilterChip(
                selected = activeFilter == kind,
                onClick = { onFilterChanged(kind) },
                label = { Text(kindLabel(kind)) },
            )
        }
    }
}

@Composable
private fun kindLabel(kind: FailureKind): String =
    when (kind) {
        FailureKind.INVALID_OUTPUT -> stringResource(R.string.copy_failures_kind_invalid_output)
        FailureKind.UNKNOWN_FUNCTION -> stringResource(R.string.copy_failures_kind_unknown_function)
        FailureKind.HANDLER_ERROR -> stringResource(R.string.copy_failures_kind_handler_error)
    }

@Composable
private fun FailureRow(failure: FailureView) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CONFIG_ROW_MIN_HEIGHT)
                .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = failure.displayTime,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KindBadge(kind = failure.kind)
        }
        if (failure.details.isNotBlank()) {
            Spacer(modifier = Modifier.height(CurroSpacing.xs))
            Text(
                text = failure.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KindBadge(kind: FailureKind) {
    val label = kindLabel(kind)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color =
            when (kind) {
                FailureKind.INVALID_OUTPUT -> MaterialTheme.colorScheme.error
                FailureKind.UNKNOWN_FUNCTION -> MaterialTheme.colorScheme.secondary
                FailureKind.HANDLER_ERROR -> MaterialTheme.colorScheme.tertiary
            },
    )
}

@Composable
private fun ClearLogConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.copy_failures_clear_dialog_title)) },
        text = { Text(stringResource(R.string.copy_failures_clear_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.copy_failures_clear_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.copy_failures_clear_dialog_cancel))
            }
        },
    )
}

// Config menu rows use 72 dp minimum height (Fran-screen density, not the 96 dp senior floor).
@Suppress("MagicNumber")
private val CONFIG_ROW_MIN_HEIGHT = 72.dp

@Preview(name = "Failures — light with entries", showBackground = true)
@Preview(name = "Failures — dark", uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun FailuresContentPreview() {
    CurroTheme {
        Surface {
            FailuresContent(
                uiState =
                    FailuresUiState(
                        allFailures =
                            listOf(
                                FailureView(
                                    id = 1L,
                                    displayTime = "17 may 12:34",
                                    kind = FailureKind.INVALID_OUTPUT,
                                    details = "SyntaxError in model output",
                                    sent = false,
                                ),
                                FailureView(
                                    id = 2L,
                                    displayTime = "16 may 09:10",
                                    kind = FailureKind.HANDLER_ERROR,
                                    details = "call_contact/ContactNotFound",
                                    sent = true,
                                ),
                            ),
                    ),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Failures — empty")
@Composable
private fun FailuresEmptyPreview() {
    CurroTheme {
        Surface {
            FailuresContent(
                uiState = FailuresUiState(),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
