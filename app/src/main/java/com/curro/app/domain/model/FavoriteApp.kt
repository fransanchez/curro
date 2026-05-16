package com.curro.app.domain.model

import android.graphics.drawable.Drawable

/**
 * A favourite launcher app tile (SF-1.4 / US-012; SF-7.4 / US-048).
 *
 * The [label] is a sealed [AppLabel]:
 *  - [AppLabel.Resource] — for the four Phase-1 seed apps (WhatsApp, Llamadas, Cámara, Fotos);
 *    the composable resolves it via `stringResource(label.resId)`.
 *  - [AppLabel.Text] — for usage-derived tiles (apps opened by the user whose PackageManager
 *    label is fetched at runtime by [com.curro.app.data.apps.SeedAppResolver.toFavoriteApp]).
 *
 * Phase-8: Fran will be able to override labels from the config menu; both shapes are
 * forward-compatible.
 *
 * @param id Stable logical identifier used as a `LazyColumn` key ("whatsapp", or the
 *   package name for usage-derived tiles).
 * @param label Display label for the tile.
 * @param resolvedPackage The package name resolved at runtime; null if not installed.
 * @param icon App icon from `PackageManager.getApplicationIcon()`; null if not installed.
 */
data class FavoriteApp(
    val id: String,
    val label: AppLabel,
    val resolvedPackage: String?,
    val icon: Drawable?,
)
