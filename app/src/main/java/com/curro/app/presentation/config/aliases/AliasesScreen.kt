package com.curro.app.presentation.config.aliases

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.curro.app.R
import com.curro.app.data.local.AliasSource
import com.curro.app.domain.repository.AliasView
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Alias management screen (SF-8.2 / US-051).
 *
 * Shows the list of stored aliases (alias → contact name + source badge).
 * Edit and delete icons on each row; a FAB to add a new alias manually.
 *
 * No `Scaffold` / `TopAppBar` — the parent `CurroNavHost`'s Scaffold provides
 * `Modifier.padding(innerPadding)` (No-Double-Padding rule). Back chevron at
 * TopStart in a Box overlay.
 *
 * State-driven dialogs: [AddOrEditAliasDialog] (add/edit) and
 * [DeleteAliasConfirmDialog] (delete confirmation). Both are driven by
 * [AliasesUiState] flags and dismissed via [AliasesEvent.DismissDialog].
 */
@Composable
fun AliasesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AliasesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AliasesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun AliasesContent(
    uiState: AliasesUiState,
    onEvent: (AliasesEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.aliases.isEmpty()) {
            EmptyAliasesState()
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = Dimens.MinTapTarget + CurroSpacing.s,
                            bottom = Dimens.MinTapTarget,
                        ),
            ) {
                items(uiState.aliases, key = { it.alias }) { aliasView ->
                    AliasRow(
                        aliasView = aliasView,
                        onEdit = { onEvent(AliasesEvent.EditPressed(aliasView)) },
                        onDelete = { onEvent(AliasesEvent.DeletePressed(aliasView)) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = CurroSpacing.m),
                    )
                }
            }
        }

        // Screen title — shown at top, padded to clear the back chevron.
        Text(
            text = stringResource(R.string.copy_aliases_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = CurroSpacing.l),
        )

        // Back chevron at TopStart — No-Double-Padding rule.
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = CurroSpacing.s, top = CurroSpacing.s)
                    .size(Dimens.MinTapTarget),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier.size(Dimens.LargeIconSize),
            )
        }

        // FAB — add alias — at BottomEnd.
        FloatingActionButton(
            onClick = { onEvent(AliasesEvent.AddPressed) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(CurroSpacing.l),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.copy_aliases_add),
            )
        }

        // State-driven dialogs.
        if (uiState.showAddDialog || uiState.editTarget != null) {
            AddOrEditAliasDialog(
                editTarget = uiState.editTarget,
                onSave = { alias, contactName ->
                    onEvent(AliasesEvent.SaveAlias(alias, contactName))
                },
                onDismiss = { onEvent(AliasesEvent.DismissDialog) },
            )
        }

        uiState.pendingDelete?.let { pending ->
            DeleteAliasConfirmDialog(
                aliasView = pending,
                onConfirm = { onEvent(AliasesEvent.ConfirmDelete) },
                onDismiss = { onEvent(AliasesEvent.DismissDialog) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Suppress("MagicNumber")
private val previewAliases =
    listOf(
        AliasView("mi hija", "María García", AliasSource.LEARNED, 5),
        AliasView("pepito", "José Pérez", AliasSource.EXPLICIT, 2),
        AliasView("el médico", "Dr. Rodríguez", AliasSource.LEARNED, 1),
    )

@Preview(name = "AliasesScreen — Light / with aliases", widthDp = 412, heightDp = 800)
@Composable
private fun AliasesLightPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            AliasesContent(
                uiState = AliasesUiState(aliases = previewAliases),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "AliasesScreen — Empty", widthDp = 412, heightDp = 800)
@Composable
private fun AliasesEmptyPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            AliasesContent(
                uiState = AliasesUiState(aliases = emptyList()),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "AliasesScreen — Dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 800)
@Composable
private fun AliasesDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            AliasesContent(
                uiState = AliasesUiState(aliases = previewAliases),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "AliasesScreen — Delete Dialog", widthDp = 412, heightDp = 800)
@Composable
private fun AliasesDeleteDialogPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            AliasesContent(
                uiState =
                    AliasesUiState(
                        aliases = previewAliases,
                        pendingDelete = previewAliases.first(),
                    ),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
