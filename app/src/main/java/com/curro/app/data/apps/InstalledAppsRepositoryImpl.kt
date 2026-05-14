package com.curro.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.model.LaunchableApp
import com.curro.app.domain.repository.InstalledAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [InstalledAppsRepository] for the "Más apps" full-list screen (SF-1.5 / US-013).
 *
 * Queries [PackageManager.queryIntentActivities] for all [Intent.ACTION_MAIN] +
 * [Intent.CATEGORY_LAUNCHER] apps, maps them to [LaunchableApp], sorts alphabetically
 * using a Spanish-locale [Collator] (handles accents and ñ correctly), and re-emits on
 * [ProcessLifecycleOwner] `ON_RESUME`.
 *
 * [flowOn(ioDispatcher)] ensures the PM query runs off the main thread.
 *
 * @param context Application context for [PackageManager] access.
 * @param ioDispatcher Background dispatcher injected via [IoDispatcher].
 */
@Singleton
class InstalledAppsRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : InstalledAppsRepository {
        /**
         * Internal seam for the lifecycle source (tests supply a [TestLifecycleOwner]).
         */
        internal var lifecycleSource: () -> Lifecycle = {
            ProcessLifecycleOwner.get().lifecycle
        }

        override fun observeAllLaunchable(): Flow<List<LaunchableApp>> =
            callbackFlow {
                val observer =
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            trySend(loadAllLaunchable())
                        }
                    }
                val lifecycle = lifecycleSource()
                lifecycle.addObserver(observer)
                awaitClose { lifecycle.removeObserver(observer) }
            }
                .onStart { emit(loadAllLaunchable()) }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        /**
         * Queries [PackageManager] for all installed launchable apps.
         *
         * [PackageManager.queryIntentActivities] returns each launchable Activity's
         * [android.content.pm.ResolveInfo]; we deduplicate by package name (some apps
         * declare multiple LAUNCHER Activities) and sort with a Spanish [Collator].
         */
        private fun loadAllLaunchable(): List<LaunchableApp> {
            val pm = context.packageManager
            val launcherIntent =
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

            @Suppress("DEPRECATION")
            val resolved = pm.queryIntentActivities(launcherIntent, 0)

            val collator =
                Collator.getInstance(Locale("es")).apply {
                    strength = Collator.SECONDARY // accent-insensitive sort, ñ in correct position
                }

            return resolved
                .distinctBy { it.activityInfo.packageName }
                .map { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    LaunchableApp(
                        packageName = pkg,
                        label = resolveInfo.loadLabel(pm).toString(),
                        icon = resolveInfo.loadIcon(pm),
                    )
                }
                .sortedWith(compareBy(collator) { it.label })
        }
    }
