package com.curro.app.data.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Factory for Android Settings intents related to battery and app details (US-059 / SF-8.10).
 *
 * On Xiaomi HyperOS the `ACTION_APPLICATION_DETAILS_SETTINGS` page gives Fran direct access
 * to the battery restriction controls (Batería → Sin restricciones) and the autostart toggle.
 * The intent is opened from [DiagnosticsViewModel.onEvent] and launched via a side-effect in
 * [DiagnosticsScreen].
 */
object BatterySettingsIntents {
    /**
     * Returns an [Intent] for [Settings.ACTION_APPLICATION_DETAILS_SETTINGS] scoped to
     * [context.packageName].
     *
     * The [Intent.FLAG_ACTIVITY_NEW_TASK] flag is required because the intent is launched
     * outside an [Activity] context (from [Context.startActivity] in a side-effect handler).
     */
    fun openAppDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
