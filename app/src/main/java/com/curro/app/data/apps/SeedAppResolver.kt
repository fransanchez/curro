package com.curro.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.MediaStore
import com.curro.app.R
import com.curro.app.domain.model.AppLabel
import com.curro.app.domain.model.FavoriteApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the Phase-1 seed apps (WhatsApp, Dialer, Camera, Gallery) for the
 * launcher home grid (SF-7.4 / US-048; extracted from SF-1.4's
 * `StaticFavoriteAppsRepositoryImpl`).
 *
 * Two consumers:
 *  1. [RecencyFavoriteAppsRepositoryImpl.loadFavorites] — seeds pad the grid
 *     when usage data is sparse (< 4 usage-derived entries).
 *  2. [RecencyFavoriteAppsRepositoryImpl.loadFavorites] — usage-derived entries
 *     are resolved via [toFavoriteApp] to get an icon + label from PackageManager.
 *
 * Dynamic resolution: the dialer / camera / gallery packages are OEM-specific
 * (HyperOS = com.miui.*); resolve via [PackageManager.resolveActivity] with
 * the canonical Intent action, fall back to hard-coded [PACKAGE_*_FALLBACK]s.
 *
 * All PackageManager calls are synchronous and expected to run on an IO dispatcher
 * (called from [RecencyFavoriteAppsRepositoryImpl] which is [flowOn] IoDispatcher).
 */
@Singleton
open class SeedAppResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * The four Phase-1 seed tiles, in canonical order. Apps not installed
         * appear with [FavoriteApp.resolvedPackage] = null and [FavoriteApp.icon] = null
         * (callers may filter or render the placeholder).
         */
        open fun seedFavorites(): List<FavoriteApp> {
            val pm = context.packageManager
            return listOf(
                buildFavoriteApp("whatsapp", AppLabel.Resource(R.string.copy_app_label_whatsapp), pm) {
                    resolveDirectPackage(PACKAGE_WHATSAPP, pm)
                },
                buildFavoriteApp("calls", AppLabel.Resource(R.string.copy_app_label_calls), pm) {
                    resolveViaIntent(Intent(Intent.ACTION_DIAL), PACKAGE_DIALER_FALLBACK, pm)
                },
                buildFavoriteApp("camera", AppLabel.Resource(R.string.copy_app_label_camera), pm) {
                    resolveViaIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), PACKAGE_CAMERA_FALLBACK, pm)
                },
                buildFavoriteApp("photos", AppLabel.Resource(R.string.copy_app_label_photos), pm) {
                    resolveViaIntent(
                        Intent(Intent.ACTION_PICK).apply { type = "image/*" },
                        PACKAGE_GALLERY_FALLBACK,
                        pm,
                    )
                },
            )
        }

        /**
         * Resolves an arbitrary [packageName] from `app_usage` to a [FavoriteApp] with
         * the real app label + icon from [PackageManager].
         *
         * Returns null if the package is not installed (e.g. uninstalled after a launch
         * was recorded in `app_usage`).
         *
         * The [FavoriteApp.label] is [AppLabel.Text] carrying the localised label from
         * `PackageManager.getApplicationLabel` — what the user already sees everywhere on
         * the device. Phase 8's config menu can let Fran override it.
         */
        @Suppress("ReturnCount")
        open fun toFavoriteApp(packageName: String): FavoriteApp? {
            val pm = context.packageManager
            if (!isInstalled(packageName, pm)) return null
            val labelText =
                runCatching {
                    @Suppress("DEPRECATION")
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrNull() ?: return null
            val icon: Drawable? = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
            return FavoriteApp(
                id = packageName,
                label = AppLabel.Text(labelText),
                resolvedPackage = packageName,
                icon = icon,
            )
        }

        private fun buildFavoriteApp(
            id: String,
            label: AppLabel,
            pm: PackageManager,
            resolvePackage: () -> String?,
        ): FavoriteApp {
            val resolvedPackage = resolvePackage()
            val icon: Drawable? =
                resolvedPackage?.let { pkg ->
                    runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                }
            return FavoriteApp(id = id, label = label, resolvedPackage = resolvedPackage, icon = icon)
        }

        private fun resolveDirectPackage(
            packageName: String,
            pm: PackageManager,
        ): String? = packageName.takeIf { isInstalled(it, pm) }

        /**
         * Resolves the package for an [intent] via [PackageManager.resolveActivity].
         * Falls back to [fallbackPackage] if resolution returns null or the resolved package
         * is not installed.
         */
        private fun resolveViaIntent(
            intent: Intent,
            fallbackPackage: String,
            pm: PackageManager,
        ): String? =
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                ?: fallbackPackage.takeIf { isInstalled(it, pm) }

        private fun isInstalled(
            packageName: String,
            pm: PackageManager,
        ): Boolean =
            runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
                true
            }.getOrDefault(false)

        private companion object {
            const val PACKAGE_WHATSAPP = "com.whatsapp"

            /** HyperOS uses com.miui.dialer; stock Android com.android.dialer.
             * Dynamic resolution via ACTION_DIAL is the primary path; this is the fallback. */
            const val PACKAGE_DIALER_FALLBACK = "com.android.dialer"
            const val PACKAGE_CAMERA_FALLBACK = "com.android.camera"

            /** MIUI gallery first; Google Photos as secondary on non-MIUI devices. */
            const val PACKAGE_GALLERY_FALLBACK = "com.miui.gallery"
        }
    }
