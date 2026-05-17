package com.curro.app.presentation.config.reset

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.curro.app.R
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Learning reset screen (SF-8.8 / US-058).
 *
 * Shows a warning + a destructive CTA. On confirmation, performs a four-way parallel reset
 * (aliases, app usage, failure log, favourites override) then auto-navigates back.
 *
 * No [androidx.compose.material3.Scaffold] / TopAppBar — the parent [CurroNavHost] Scaffold
 * provides [Modifier.padding(innerPadding)] (No-Double-Padding rule). Back chevron at TopStart.
 */
@Composable
fun ResetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResetViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Auto-navigate back when reset completes.
    LaunchedEffect(uiState.resetComplete) {
        if (uiState.resetComplete) onBack()
    }

    ResetContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun ResetContent(
    uiState: ResetUiState,
    onEvent: (ResetEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = Dimens.MinTapTarget, start = CurroSpacing.m, end = CurroSpacing.m),
        ) {
            Text(
                text = stringResource(R.string.copy_reset_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = CurroSpacing.s),
            )

            Spacer(modifier = Modifier.height(CurroSpacing.m))

            Text(
                text = stringResource(R.string.copy_reset_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(CurroSpacing.l))

            Text(
                text = stringResource(R.string.copy_reset_what_clears),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(CurroSpacing.s))
            Text(
                text = stringResource(R.string.copy_reset_bullet_aliases),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.copy_reset_bullet_usage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.copy_reset_bullet_log),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.copy_reset_bullet_favourites),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(CurroSpacing.xl))

            Button(
                onClick = { onEvent(ResetEvent.ResetPressed) },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.copy_reset_cta),
                    style = MaterialTheme.typography.bodyLarge,
                )
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

        // Confirmation dialog
        if (uiState.showConfirmDialog) {
            ResetConfirmDialog(
                onConfirm = { onEvent(ResetEvent.ConfirmReset) },
                onDismiss = { onEvent(ResetEvent.DismissDialog) },
            )
        }
    }
}

@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.copy_reset_confirm_title)) },
        text = { Text(stringResource(R.string.copy_reset_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.copy_reset_confirm_button),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.copy_reset_cancel_button))
            }
        },
    )
}

@Preview(name = "Reset — light", showBackground = true)
@Preview(name = "Reset — dark", uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ResetContentPreview() {
    CurroTheme {
        Surface {
            ResetContent(
                uiState = ResetUiState(),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
