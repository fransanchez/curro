package com.curro.app.presentation.config.aliases

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.curro.app.R
import com.curro.app.domain.repository.AliasView

/**
 * Confirmation dialog before deleting an alias (SF-8.2 / US-051).
 *
 * Shows the alias text and the contact display name so Fran understands what
 * is being removed. [onConfirm] fires [AliasesEvent.ConfirmDelete];
 * [onDismiss] fires [AliasesEvent.DismissDialog].
 */
@Composable
fun DeleteAliasConfirmDialog(
    aliasView: AliasView,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = aliasView.alias,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text =
                    stringResource(
                        R.string.copy_aliases_delete_confirm,
                        aliasView.alias,
                        aliasView.displayName,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.copy_aliases_delete_confirm_button),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.copy_aliases_dialog_cancel))
            }
        },
    )
}
