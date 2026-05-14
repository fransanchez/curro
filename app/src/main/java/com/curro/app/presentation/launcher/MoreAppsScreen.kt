package com.curro.app.presentation.launcher

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.curro.app.R
import com.curro.app.domain.model.LaunchableApp
import com.curro.app.presentation.common.BigListRow
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * "Más apps" full-app-list screen (SF-1.5 / US-013).
 *
 * Shows every installed launchable app in a [LazyColumn] sorted alphabetically with
 * Spanish collation. Tapping a row launches the app via [Context.startActivity].
 * A back chevron at [Alignment.TopStart] pops back to the launcher home.
 *
 * **No-Double-Padding rule**: no `Scaffold`, no `TopAppBar`, no `statusBarsPadding()` —
 * [com.curro.app.presentation.navigation.CurroNavHost]'s `Scaffold` already provides
 * the inset padding (`navigation-patterns` rule 1; CLAUDE.md "Screen Layout").
 *
 * @param onBack Fires when the back chevron is pressed (pops the nav back-stack).
 * @param modifier Applied to the root [Box].
 */
@Composable
fun MoreAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoreAppsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MoreAppsContent(uiState = uiState, onBack = onBack, modifier = modifier)
}

@Composable
internal fun MoreAppsContent(
    uiState: MoreAppsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is MoreAppsUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is MoreAppsUiState.Ready -> {
                // LazyColumn with top padding to clear the back chevron overlay (96 dp).
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            top = Dimens.MinTapTarget,
                            bottom = CurroSpacing.l,
                        ),
                ) {
                    items(
                        items = uiState.apps,
                        key = { it.packageName },
                    ) { app ->
                        AppListRow(
                            app = app,
                            onClick = {
                                val intent =
                                    context.packageManager
                                        .getLaunchIntentForPackage(app.packageName)
                                intent?.let { context.startActivity(it) }
                            },
                        )
                    }
                }
            }
        }

        // Back chevron — overlay at TopStart; 96 dp hit area (navigation-patterns rule 1).
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
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * A single row in the "Más apps" list — wraps [BigListRow] with the app's icon and label.
 *
 * Icon rendering: [LaunchableApp.icon] ([android.graphics.drawable.Drawable]) is converted
 * to [androidx.compose.ui.graphics.ImageBitmap] via `drawable.toBitmap().asImageBitmap()`
 * (dep-free `core-ktx` extension; same pattern as [AppTile]).
 * When conversion fails (theoretically impossible for icons from PackageManager but guarded
 * by `runCatching`) a generic [Icon] placeholder is shown.
 *
 * @param app The launchable app to display.
 * @param onClick Called when the row is tapped.
 */
@Composable
private fun AppListRow(
    app: LaunchableApp,
    onClick: () -> Unit,
) {
    val imageBitmap =
        remember(app.icon) {
            runCatching { app.icon.toBitmap().asImageBitmap() }.getOrNull()
        }

    BigListRow(
        title = app.label,
        onClick = onClick,
        leading = {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = app.label,
                    modifier = Modifier.size(Dimens.LargeIconSize),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = app.label,
                    modifier = Modifier.size(Dimens.LargeIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

// --- Previews ---

/** Smaller app count for the huge-font preview so all rows fit in the canvas. */
private const val PREVIEW_HUGE_FONT_APP_COUNT = 4

private fun fakeApps(count: Int = 8): List<LaunchableApp> =
    listOf(
        "Ajustes",
        "Cámara",
        "Calculadora",
        "Chrome",
        "Galería",
        "Maps",
        "Teléfono",
        "WhatsApp",
    )
        .take(count)
        .map { name ->
            LaunchableApp(
                packageName = "com.example.${name.lowercase()}",
                label = name,
                // ColorDrawable as a stand-in for a real Drawable in previews
                icon = ColorDrawable(android.graphics.Color.GRAY),
            )
        }

@Preview(name = "MoreApps — Light", widthDp = 412, heightDp = 800)
@Composable
private fun MoreAppsLightPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            MoreAppsContent(
                uiState = MoreAppsUiState.Ready(fakeApps()),
                onBack = {},
            )
        }
    }
}

@Preview(name = "MoreApps — Dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 800)
@Composable
private fun MoreAppsDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            MoreAppsContent(
                uiState = MoreAppsUiState.Ready(fakeApps()),
                onBack = {},
            )
        }
    }
}

@Preview(name = "MoreApps — Loading", widthDp = 412, heightDp = 800)
@Composable
private fun MoreAppsLoadingPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            MoreAppsContent(
                uiState = MoreAppsUiState.Loading,
                onBack = {},
            )
        }
    }
}

@Preview(name = "MoreApps — Large font 1.5×", widthDp = 412, heightDp = 800, fontScale = 1.5f)
@Composable
private fun MoreAppsLargeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            MoreAppsContent(
                uiState = MoreAppsUiState.Ready(fakeApps()),
                onBack = {},
            )
        }
    }
}

@Preview(
    name = "MoreApps — Huge font 2.0× (senior-first)",
    widthDp = 412,
    heightDp = 800,
    fontScale = 2.0f,
)
@Composable
private fun MoreAppsHugeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            MoreAppsContent(
                uiState = MoreAppsUiState.Ready(fakeApps(count = PREVIEW_HUGE_FONT_APP_COUNT)),
                onBack = {},
            )
        }
    }
}
