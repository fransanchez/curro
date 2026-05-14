package com.curro.app.data.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.curro.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [DefaultLauncherDetector].
 *
 * Uses [PackageManager.resolveActivity] with [PackageManager.MATCH_DEFAULT_ONLY] to find
 * the system's chosen home Activity; compares its package to [BuildConfig.APPLICATION_ID]
 * (resolved at build time — never hard-code `"com.curro.app"`).
 *
 * Flow source is [ProcessLifecycleOwner.get().lifecycle] — every `ON_RESUME` triggers a
 * re-read. [distinctUntilChanged] prevents spurious downstream `StateFlow` churn when the
 * answer stays the same across consecutive resumes (e.g. normal navigation back from Settings).
 *
 * Why [ProcessLifecycleOwner] and not the launcher Activity's own lifecycle: the user
 * typically leaves Curro to grant the role (the system chooser is its own Activity), then
 * returns — the process lifecycle's `ON_RESUME` fires reliably on return. The launcher
 * Activity's `ON_RESUME` also fires, but [ProcessLifecycleOwner] is the canonical
 * "the user is interacting with the app again" signal that survives the chooser overlay.
 *
 * [Dispatchers.Main.immediate] for [flowOn] because [ProcessLifecycleOwner] is
 * main-thread-bound. The [PackageManager.resolveActivity] call in [isDefault] is a single
 * Binder hop (microseconds), well within the 16 ms frame budget.
 *
 * Testing: [homeActivityResolver] and [lifecycleSource] are internal seams injected by
 * tests without Robolectric — production code uses the default constructor path
 * ([DefaultLauncherDetectorImpl.create] / Hilt's `@Inject` ctor) which wires the real
 * `PackageManager` and `ProcessLifecycleOwner`.
 */
@Singleton
class DefaultLauncherDetectorImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DefaultLauncherDetector {
        /**
         * Internal seam: resolves the package name of the currently-set home Activity.
         * Returns `null` when no home Activity is resolved (e.g. no default set yet).
         * Defaults to querying [PackageManager] via [Context.packageManager].
         */
        internal var homeActivityResolver: () -> String? = {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }

        /**
         * Internal seam: the [Lifecycle] whose `ON_RESUME` events drive the flow.
         * Defaults to [ProcessLifecycleOwner.get().lifecycle].
         * Tests supply a [androidx.lifecycle.testing.TestLifecycleOwner]'s lifecycle.
         */
        internal var lifecycleSource: () -> Lifecycle = {
            ProcessLifecycleOwner.get().lifecycle
        }

        override fun isDefault(): Boolean = homeActivityResolver() == BuildConfig.APPLICATION_ID

        override val flow: Flow<Boolean>
            get() =
                callbackFlow {
                    val observer =
                        LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                trySend(isDefault())
                            }
                        }
                    val lifecycle = lifecycleSource()
                    lifecycle.addObserver(observer)
                    awaitClose { lifecycle.removeObserver(observer) }
                }
                    .onStart { emit(isDefault()) }
                    .distinctUntilChanged()
                    .flowOn(Dispatchers.Main.immediate)
    }
