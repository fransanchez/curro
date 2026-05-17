package com.curro.app.presentation.config.aliases

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.curro.app.R
import com.curro.app.domain.repository.AliasView
import com.curro.app.presentation.theme.CurroSpacing

/**
 * Add or edit an alias mapping (SF-8.2 / US-051).
 *
 * When [editTarget] is null, the dialog is in "add" mode (empty fields, title
 * "Nuevo alias"). When [editTarget] is non-null, the fields are pre-filled with
 * the existing alias and contact display name, and the title becomes "Editar alias".
 *
 * [onSave] fires with the raw (untrimmed) strings — the ViewModel normalises them.
 * [onDismiss] fires on "Cancelar" or the dialog's implicit dismiss.
 */
@Composable
fun AddOrEditAliasDialog(
    editTarget: AliasView?,
    onSave: (alias: String, contactName: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var aliasText by remember(editTarget) { mutableStateOf(editTarget?.alias.orEmpty()) }
    var contactText by remember(editTarget) { mutableStateOf(editTarget?.displayName.orEmpty()) }

    val title =
        if (editTarget == null) {
            stringResource(R.string.copy_aliases_dialog_add_title)
        } else {
            stringResource(R.string.copy_aliases_dialog_edit_title)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    label = {
                        Text(stringResource(R.string.copy_aliases_dialog_alias_label))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(CurroSpacing.m))
                OutlinedTextField(
                    value = contactText,
                    onValueChange = { contactText = it },
                    label = {
                        Text(stringResource(R.string.copy_aliases_dialog_contact_label))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(aliasText, contactText) },
                enabled = aliasText.isNotBlank() && contactText.isNotBlank(),
            ) {
                Text(stringResource(R.string.copy_aliases_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.copy_aliases_dialog_cancel))
            }
        },
    )
}
