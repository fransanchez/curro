package com.curro.app.data.permissions

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.curro.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Checks whether the user has granted notification-listener access to Curro (US-030 / SF-4.6).
 *
 * Detection method: [NotificationManagerCompat.getEnabledListenerPackages] returning a set that
 * contains [BuildConfig.APPLICATION_ID]. This is the verified method per the brief §6.
 *
 * The implementation is injected into [com.curro.app.presentation.launcher.LauncherViewModel],
 * which re-evaluates [isGranted] on every `ON_RESUME` cycle (the user returns from Settings).
 */
interface NotificationAccessGate {
    /** True iff the user has granted notification-listener access to Curro. */
    fun isGranted(): Boolean
}

class SystemNotificationAccessGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NotificationAccessGate {
        override fun isGranted(): Boolean =
            NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(BuildConfig.APPLICATION_ID)
    }
