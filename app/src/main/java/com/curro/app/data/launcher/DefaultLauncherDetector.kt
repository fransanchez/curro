package com.curro.app.data.launcher

import kotlinx.coroutines.flow.Flow

/**
 * Resolves whether Curro is the current default home-resolved Activity on the device.
 * Re-emits on every [androidx.lifecycle.Lifecycle.Event.ON_RESUME] of
 * [androidx.lifecycle.ProcessLifecycleOwner] so the UI reacts when HyperOS "forgets"
 * the default launcher after an OS update (`launcher-app` skill — Xiaomi/HyperOS section).
 *
 * Consumed directly by [com.curro.app.presentation.launcher.LauncherViewModel].
 * Lives in `data/` (not `domain/repository/`) because this is a launcher-platform
 * self-identity concern, not a domain-level abstraction. If a use case ever needs it
 * from the domain layer, promote to `domain/repository/` then.
 */
interface DefaultLauncherDetector {
    /**
     * True iff Curro's package is the system's currently resolved home Activity.
     *
     * Resolved via [android.content.pm.PackageManager.resolveActivity] with
     * [android.content.pm.PackageManager.MATCH_DEFAULT_ONLY].
     */
    fun isDefault(): Boolean

    /**
     * Cold-on-collection flow that emits the current [isDefault] value on subscribe and
     * re-emits on every [androidx.lifecycle.ProcessLifecycleOwner] `ON_RESUME` — so
     * coming back from Settings after granting / revoking the role updates downstream
     * [kotlinx.coroutines.flow.StateFlow]s without an explicit refresh call.
     *
     * Applies [kotlinx.coroutines.flow.distinctUntilChanged] so consecutive `ON_RESUME`
     * events with the same answer do not churn downstream recompositions.
     */
    val flow: Flow<Boolean>
}
