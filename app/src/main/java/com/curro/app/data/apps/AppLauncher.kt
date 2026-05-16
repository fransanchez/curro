package com.curro.app.data.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Thin testable wrapper around `getLaunchIntentForPackage` + `startActivity` (US-027 / SF-4.3).
 *
 * Keeping the system call behind an interface lets [com.curro.app.handler.OpenAppHandler] tests
 * inject a fake without touching the Android framework.
 */
interface AppLauncher {
    /**
     * Fires the launcher intent for [packageName].
     *
     * @return `true` if the activity started successfully; `false` if no LAUNCHER activity was
     *   found, or if `startActivity` threw [ActivityNotFoundException] / [SecurityException].
     */
    fun launch(packageName: String): Boolean
}

/**
 * Production implementation — delegates to [android.content.pm.PackageManager] and
 * [Context.startActivity]. [Intent.FLAG_ACTIVITY_NEW_TASK] is added because the caller is an
 * application context, not an Activity context.
 */
class IntentAppLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppLauncher {
        override fun launch(packageName: String): Boolean {
            val intent =
                context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }
