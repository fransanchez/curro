package com.curro.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.MediaStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.curro.app.R
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.repository.FavoriteAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-1 implementation of [FavoriteAppsRepository] (SF-1.4 / US-012).
 *
 * Returns a static four-tile list: WhatsApp, Llamadas, Cámara, Fotos. Package names
 * are resolved dynamically via [PackageManager] at each `ON_RESUME` so the correct OEM
 * dialer, camera, and gallery apps are picked on each device (HyperOS, stock Android, etc.).
 *
 * Re-emits on [ProcessLifecycleOwner] `ON_RESUME` (same pattern as
 * [com.curro.app.data.launcher.DefaultLauncherDetectorImpl]) so tiles update after apps
 * are installed or removed while Curro is in the background.
 *
 * Phase-8: a Room-backed implementation will allow Fran to edit the tile list.
 *
 * @param context Application context — used for [PackageManager].
 * @param ioDispatcher Background dispatcher for PM queries ([IoDispatcher]).
 */
@Singleton
class StaticFavoriteAppsRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FavoriteAppsRepository {
        /**
         * Internal seam for the lifecycle source (tests supply a [TestLifecycleOwner]).
         */
        internal var lifecycleSource: () -> Lifecycle = {
            ProcessLifecycleOwner.get().lifecycle
        }

        override fun observeFavorites(): Flow<List<FavoriteApp>> =
            callbackFlow {
                // The callback fires on the main thread (Lifecycle is main-bound). We can't
                // suspend in the LifecycleEventObserver, so launch a child coroutine to do the
                // PM work on ioDispatcher and trySend the result.
                val observer =
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            launch { trySend(withContext(ioDispatcher) { loadFavorites() }) }
                        }
                    }
                val lifecycle = lifecycleSource()
                // lifecycle.addObserver() and removeObserver() require the main thread —
                // the surrounding .flowOn(Dispatchers.Main.immediate) below guarantees that.
                lifecycle.addObserver(observer)
                awaitClose { lifecycle.removeObserver(observer) }
            }
                .onStart { emit(withContext(ioDispatcher) { loadFavorites() }) }
                .distinctUntilChanged()
                // Main.immediate so addObserver/removeObserver run on the main thread.
                // The actual PM-heavy work is dispatched to ioDispatcher inside loadFavorites
                // call sites (above) via withContext.
                .flowOn(Dispatchers.Main.immediate)

        /**
         * Loads the four static favourite apps. Runs on [ioDispatcher].
         *
         * For each entry: resolve the package name (direct or via intent), verify it is
         * installed, fetch its icon. Returns `resolvedPackage = null` for apps not present.
         */
        private fun loadFavorites(): List<FavoriteApp> {
            val pm = context.packageManager
            return listOf(
                buildFavoriteApp("whatsapp", R.string.copy_app_label_whatsapp, pm) {
                    resolveDirectPackage(PACKAGE_WHATSAPP, pm)
                },
                buildFavoriteApp("calls", R.string.copy_app_label_calls, pm) {
                    resolveViaIntent(Intent(Intent.ACTION_DIAL), PACKAGE_DIALER_FALLBACK, pm)
                },
                buildFavoriteApp("camera", R.string.copy_app_label_camera, pm) {
                    resolveViaIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), PACKAGE_CAMERA_FALLBACK, pm)
                },
                buildFavoriteApp("photos", R.string.copy_app_label_photos, pm) {
                    resolveViaIntent(
                        Intent(Intent.ACTION_PICK).apply { type = "image/*" },
                        PACKAGE_GALLERY_FALLBACK,
                        pm,
                    )
                },
            )
        }

        private fun buildFavoriteApp(
            id: String,
            labelResId: Int,
            pm: PackageManager,
            resolvePackage: () -> String?,
        ): FavoriteApp {
            val resolvedPackage = resolvePackage()
            val icon: Drawable? =
                resolvedPackage?.let { pkg ->
                    runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                }
            return FavoriteApp(id = id, labelResId = labelResId, resolvedPackage = resolvedPackage, icon = icon)
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
