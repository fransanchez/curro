package com.curro.app.domain.model

import android.graphics.drawable.Drawable

/**
 * A launchable app entry for the "Más apps" full-list screen (SF-1.5 / US-013).
 *
 * Every installed app that declares [android.content.Intent.ACTION_MAIN] +
 * [android.content.Intent.CATEGORY_LAUNCHER] is represented here.
 *
 * @param packageName The package name — used as the stable [androidx.compose.foundation.lazy.LazyColumn] key.
 * @param label The human-readable display name from [android.content.pm.PackageManager].
 * @param icon The launcher icon from [android.content.pm.PackageManager].
 */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)
