package com.curro.app.domain.model

import android.graphics.drawable.Drawable

/**
 * A favourite launcher app tile (SF-1.4 / US-012).
 *
 * Phase-1: the four tiles are static (WhatsApp, Llamadas, Cámara, Fotos).
 * Phase-8: Fran will be able to edit the list from the config menu.
 *
 * @param id Stable logical identifier (e.g. "whatsapp") used as a `LazyColumn` key.
 * @param labelResId Android string resource ID for the Spanish label shown below the icon.
 * @param resolvedPackage The package name resolved at runtime; null if not installed.
 * @param icon App icon from `PackageManager.getApplicationIcon()`; null if not installed.
 */
data class FavoriteApp(
    val id: String,
    val labelResId: Int,
    val resolvedPackage: String?,
    val icon: Drawable?,
)
