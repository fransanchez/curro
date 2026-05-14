package com.curro.app.data.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the two paths Curro uses to ask Android to make it the default launcher
 * (`launcher-app` skill — "Becoming / keeping the default launcher"):
 *
 * 1. [requestRoleIntent] — the canonical Android-10+ path via [RoleManager.ROLE_HOME].
 *    The returned [Intent] must be launched via an `ActivityResultLauncher` (callers do
 *    NOT call `startActivity` on it — role-request intents are result-bearing). Returns
 *    `null` when the role is unavailable on this OS (impossible on Curro's `minSdk = 31`,
 *    but the [RoleManager] API exposes the check anyway) OR when Curro already holds the
 *    role. In both null cases the caller falls back to [openHomeSettings].
 *
 * 2. [openHomeSettings] — the fallback path used when [requestRoleIntent] returns `null`
 *    (already default OR role unavailable) OR when the user has chosen "Don't ask again"
 *    on the role chooser. Opens Settings → Default apps → Home app. Launched via plain
 *    [Context.startActivity] (no result needed — the detector's flow re-emits on resume).
 *
 * [MakeMeDefaultLauncher] is `@Inject`-constructable; no explicit Hilt binding is needed in
 * [com.curro.app.di.LauncherModule]. Resolution in non-ViewModel composables uses the
 * [com.curro.app.presentation.navigation.LauncherEntryPoint] entry-point pattern.
 *
 * Testing: assign [roleOverride] to a fake [RoleManagerWrapper] before calling
 * [requestRoleIntent]. The production wrapper initialises lazily on first [requestRoleIntent]
 * call so no Android system service is touched during object construction in JVM unit tests.
 */
@Singleton
class MakeMeDefaultLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Internal seam: overrides the [RoleManagerWrapper] used by [requestRoleIntent].
         * When `null` (production default), the lazily-initialised [RoleManagerWrapperImpl]
         * backed by the real [RoleManager] is used. Tests set this to a fake before any call.
         */
        internal var roleOverride: RoleManagerWrapper? = null

        private val productionWrapper: RoleManagerWrapper by lazy {
            RoleManagerWrapperImpl(context)
        }

        private val activeWrapper: RoleManagerWrapper
            get() = roleOverride ?: productionWrapper

        /**
         * Returns a role-request [Intent] when the `ROLE_HOME` role is available and Curro does
         * not already hold it; `null` otherwise. The caller must launch this via an
         * `ActivityResultLauncher` — never via plain [Context.startActivity].
         *
         * The two guard conditions (unavailable + already held) are merged into one `canRequest`
         * check to stay within detekt's ReturnCount limit of 2.
         */
        fun requestRoleIntent(): Intent? {
            val canRequest = activeWrapper.isRoleAvailable() && !activeWrapper.isRoleHeld()
            return if (canRequest) activeWrapper.createRequestRoleIntent() else null
        }

        /**
         * Returns an [Intent] that opens Settings → Default apps → Home app.
         *
         * [Intent.FLAG_ACTIVITY_NEW_TASK] is required because the caller is [context]
         * (the application context, not a current Activity). Omitting it would throw on
         * [Context.startActivity].
         */
        fun openHomeSettings(): Intent =
            Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }

/**
 * Internal abstraction over [RoleManager] for the `ROLE_HOME` role.
 * Enables unit testing [MakeMeDefaultLauncher] without Robolectric.
 */
internal interface RoleManagerWrapper {
    fun isRoleAvailable(): Boolean

    fun isRoleHeld(): Boolean

    fun createRequestRoleIntent(): Intent?
}

/**
 * Production implementation backed by the real [RoleManager] system service.
 * [RoleManager.getSystemService] can theoretically return `null` on stripped-down OS
 * images; all methods return the safe "not available" value in that case.
 */
internal class RoleManagerWrapperImpl(context: Context) : RoleManagerWrapper {
    private val roleManager: RoleManager? = context.getSystemService(RoleManager::class.java)

    override fun isRoleAvailable(): Boolean = roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) ?: false

    override fun isRoleHeld(): Boolean = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) ?: false

    override fun createRequestRoleIntent(): Intent? = roleManager?.createRequestRoleIntent(RoleManager.ROLE_HOME)
}
