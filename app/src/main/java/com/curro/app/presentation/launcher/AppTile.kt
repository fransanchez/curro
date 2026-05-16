package com.curro.app.presentation.launcher

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.drawable.toBitmap
import com.curro.app.R
import com.curro.app.domain.model.AppLabel
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * A single favourite app tile for the launcher home grid (SF-1.4 / US-012).
 *
 * Displays the app's icon and Spanish label inside a [Card]. Tapping fires [onClick];
 * if the app is not installed ([FavoriteApp.resolvedPackage] == null), the tile is
 * visually greyed and tap fires [onNotInstalled] instead.
 *
 * Senior-first contract:
 * - ≥ [Dimens.MinTapTarget] (96 dp) height via [Modifier.heightIn].
 * - [HapticFeedbackType.LongPress] on tap (US-004 A10).
 * - `contentDescription` = the app's Spanish label.
 *
 * Icon rendering: [FavoriteApp.icon] ([android.graphics.drawable.Drawable]) is converted
 * to [androidx.compose.ui.graphics.ImageBitmap] via `drawable.toBitmap().asImageBitmap()`
 * — a dep-free `core-ktx` extension. When the icon is null a generic [Icon] placeholder
 * is shown.
 *
 * @param app The favourite app to display.
 * @param onClick Called when a **installed** tile is tapped.
 * @param onNotInstalled Called when a **not-installed** tile is tapped (shows a toast).
 * @param modifier Applied to the [Card].
 */
@Composable
fun AppTile(
    app: FavoriteApp,
    onClick: () -> Unit,
    onNotInstalled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val label =
        when (val l = app.label) {
            is AppLabel.Resource -> stringResource(l.resId)
            is AppLabel.Text -> l.text
        }
    val isInstalled = app.resolvedPackage != null

    val imageBitmap =
        remember(app.icon) {
            app.icon?.toBitmap()?.asImageBitmap()
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.MinTapTarget)
                .alpha(if (isInstalled) 1f else DISABLED_ALPHA)
                .semantics {
                    contentDescription = label
                    if (!isInstalled) disabled()
                }
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isInstalled) onClick() else onNotInstalled()
                },
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CurroSpacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (imageBitmap != null) {
                // contentDescription is null here — semantics on the Card covers accessibility
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.LargeIconSize),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.LargeIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(CurroSpacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.38f

// --- Previews ---

@Preview(name = "AppTile — Light (installed)", widthDp = 180, heightDp = 120)
@Composable
private fun AppTileLightInstalledPreview() {
    CurroTheme {
        Surface {
            AppTile(
                app =
                    FavoriteApp(
                        id = "whatsapp",
                        label = AppLabel.Resource(R.string.copy_app_label_whatsapp),
                        resolvedPackage = "com.whatsapp",
                        icon = null,
                    ),
                onClick = {},
                onNotInstalled = {},
            )
        }
    }
}

@Preview(name = "AppTile — Light (not installed)", widthDp = 180, heightDp = 120)
@Composable
private fun AppTileLightNotInstalledPreview() {
    CurroTheme {
        Surface {
            AppTile(
                app =
                    FavoriteApp(
                        id = "whatsapp",
                        label = AppLabel.Resource(R.string.copy_app_label_whatsapp),
                        resolvedPackage = null,
                        icon = null,
                    ),
                onClick = {},
                onNotInstalled = {},
            )
        }
    }
}

@Preview(
    name = "AppTile — Dark (installed)",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 180,
    heightDp = 120,
)
@Composable
private fun AppTileDarkInstalledPreview() {
    CurroTheme {
        Surface {
            AppTile(
                app =
                    FavoriteApp(
                        id = "calls",
                        label = AppLabel.Resource(R.string.copy_app_label_calls),
                        resolvedPackage = "com.android.dialer",
                        icon = null,
                    ),
                onClick = {},
                onNotInstalled = {},
            )
        }
    }
}

@Preview(
    name = "AppTile — Large font 1.5×",
    widthDp = 180,
    heightDp = 140,
    fontScale = 1.5f,
)
@Composable
private fun AppTileLargeFontPreview() {
    CurroTheme {
        Surface {
            AppTile(
                app =
                    FavoriteApp(
                        id = "camera",
                        label = AppLabel.Resource(R.string.copy_app_label_camera),
                        resolvedPackage = "com.android.camera",
                        icon = null,
                    ),
                onClick = {},
                onNotInstalled = {},
            )
        }
    }
}
