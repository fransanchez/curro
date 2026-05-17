package com.curro.app.presentation.config.favourites

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
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
import com.curro.app.domain.model.LaunchableApp
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Favourites editor screen (SF-8.3 / US-052).
 *
 * Shows all installed apps; Fran checks up to 4 to pin on the launcher home grid.
 * A "Usar detección automática" chip clears the override. "Guardar" persists it.
 *
 * No `Scaffold` / `TopAppBar` — the parent `CurroNavHost`'s Scaffold provides
 * `Modifier.padding(innerPadding)` (No-Double-Padding rule).
 */
@Composable
fun FavouritesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavouritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FavouritesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Suppress("LongMethod")
@Composable
internal fun FavouritesContent(
    uiState: FavouritesUiState,
    onEvent: (FavouritesEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = Dimens.MinTapTarget + CurroSpacing.s,
                        bottom = Dimens.MinTapTarget,
                    ),
        ) {
            // Subtitle / instructions
            Text(
                text =
                    stringResource(
                        R.string.copy_favourites_subtitle,
                        uiState.maxCount,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )
            Spacer(modifier = Modifier.height(CurroSpacing.s))

            // "Usar detección automática" chip
            TextButton(
                onClick = { onEvent(FavouritesEvent.UseAutomatic) },
                modifier = Modifier.padding(horizontal = CurroSpacing.s),
            ) {
                Text(
                    text = stringResource(R.string.copy_favourites_use_automatic),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(CurroSpacing.s))

            // App list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.allApps, key = { it.packageName }) { app ->
                    val isSelected = uiState.selectedPackages?.contains(app.packageName) == true
                    AppSelectRow(
                        app = app,
                        isSelected = isSelected,
                        onToggle = { onEvent(FavouritesEvent.AppToggled(app.packageName)) },
                    )
                }
            }

            // Save button (only when there are unsaved changes)
            if (uiState.hasUnsavedChanges) {
                Button(
                    onClick = { onEvent(FavouritesEvent.Save) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s),
                ) {
                    Text(stringResource(R.string.copy_favourites_save))
                }
            }
        }

        // Screen title — at top centre.
        Text(
            text = stringResource(R.string.copy_favourites_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = CurroSpacing.l),
        )

        // Back chevron at TopStart.
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
    }
}

@Composable
private fun AppSelectRow(
    app: LaunchableApp,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(CurroSpacing.l),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val previewApps: List<LaunchableApp>
    get() = emptyList() // No real Drawable in preview — shows empty state.

@Preview(name = "FavouritesScreen — Light", widthDp = 412, heightDp = 800)
@Composable
private fun FavouritesLightPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            FavouritesContent(
                uiState = FavouritesUiState(allApps = previewApps),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "FavouritesScreen — Dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 800)
@Composable
private fun FavouritesDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            FavouritesContent(
                uiState = FavouritesUiState(allApps = previewApps, hasUnsavedChanges = true),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
