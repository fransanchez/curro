package com.curro.app.presentation.launcher

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * 2×2 grid of favourite app tiles for the launcher home (SF-1.4 / US-012).
 *
 * Displays the four [FavoriteApp] entries in two rows of two tiles each.
 * Calls [onTileTapped] with the package name for installed apps; [onNotInstalled]
 * for apps that resolved to null.
 *
 * Phase-1: exactly four tiles are always shown (some may be greyed if not installed).
 * Phase-8: the list will be dynamic (editable by Fran from the config menu).
 *
 * @param favorites The four favourite apps (from [LauncherUiState.favorites]).
 * @param onTileTapped Called with the resolved package name when an installed tile is tapped.
 * @param onNotInstalled Called when a not-installed tile is tapped (shows a toast).
 * @param modifier Applied to the root [Column].
 */
@Composable
fun AppTileGrid(
    favorites: List<FavoriteApp>,
    onTileTapped: (String) -> Unit,
    onNotInstalled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chunk into rows of 2. With 4 tiles this always produces 2 rows.
    val rows = favorites.chunked(2)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CurroSpacing.l),
    ) {
        rows.forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CurroSpacing.l),
            ) {
                rowApps.forEach { app ->
                    AppTile(
                        app = app,
                        onClick = {
                            val pkg = app.resolvedPackage
                            if (pkg != null) onTileTapped(pkg)
                        },
                        onNotInstalled = onNotInstalled,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad with empty weight slots if the last row has < 2 tiles.
                repeat(2 - rowApps.size) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// --- Previews ---

private val previewFavorites =
    listOf(
        FavoriteApp("whatsapp", R.string.copy_app_label_whatsapp, "com.whatsapp", null),
        FavoriteApp("calls", R.string.copy_app_label_calls, "com.android.dialer", null),
        // camera: resolvedPackage = null → renders greyed (not installed preview)
        FavoriteApp("camera", R.string.copy_app_label_camera, null, null),
        FavoriteApp("photos", R.string.copy_app_label_photos, "com.miui.gallery", null),
    )

@Preview(name = "AppTileGrid — Light", widthDp = 412, heightDp = 300)
@Composable
private fun AppTileGridLightPreview() {
    CurroTheme {
        Surface {
            AppTileGrid(
                favorites = previewFavorites,
                onTileTapped = {},
                onNotInstalled = {},
            )
        }
    }
}

@Preview(
    name = "AppTileGrid — Dark",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 300,
)
@Composable
private fun AppTileGridDarkPreview() {
    CurroTheme {
        Surface {
            AppTileGrid(
                favorites = previewFavorites,
                onTileTapped = {},
                onNotInstalled = {},
            )
        }
    }
}

@Preview(
    name = "AppTileGrid — Large font 1.5×",
    widthDp = 412,
    heightDp = 360,
    fontScale = 1.5f,
)
@Composable
private fun AppTileGridLargeFontPreview() {
    CurroTheme {
        Surface {
            AppTileGrid(
                favorites = previewFavorites,
                onTileTapped = {},
                onNotInstalled = {},
            )
        }
    }
}

@Preview(
    name = "AppTileGrid — Huge font 2.0× (senior-first)",
    widthDp = 412,
    heightDp = 420,
    fontScale = 2.0f,
)
@Composable
private fun AppTileGridHugeFontPreview() {
    CurroTheme {
        Surface {
            AppTileGrid(
                favorites = previewFavorites,
                onTileTapped = {},
                onNotInstalled = {},
            )
        }
    }
}
