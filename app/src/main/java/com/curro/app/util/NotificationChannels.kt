package com.curro.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.curro.app.MainActivity
import com.curro.app.R

/**
 * Curro's notification-channel and notification-builder helpers (US-023 / SF-3.5).
 *
 * Channels are owned here so the IDs, importance, and tone defaults stay in
 * one place. The Phase-3 release only declares the "warmup" channel; later
 * SFs (config-menu diagnostics, etc.) will add more entries.
 */
object NotificationChannels {
    /** Identifier for the warm-up foreground-service notification channel. */
    const val WARMUP_CHANNEL_ID: String = "curro_warmup"

    /** Stable notification ID for the warm-up FGS posting. */
    const val WARMUP_NOTIF_ID: Int = 1001

    /**
     * Idempotent — called from `CurroApp.onCreate`. Belt-and-braces on top of
     * the system's own dedup: createNotificationChannel re-registers safely,
     * but we still guard with a `getNotificationChannel` lookup so the channel
     * properties are not silently rewritten by a malformed redeclaration.
     */
    fun ensureWarmupChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(WARMUP_CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                WARMUP_CHANNEL_ID,
                context.getString(R.string.copy_warmup_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.copy_warmup_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
        manager.createNotificationChannel(channel)
    }
}

/**
 * Build the foreground-service notification for [com.curro.app.service.ModelWarmupService].
 *
 * Title only ("Curro está listo") — no body text. Tap opens MainActivity.
 * `setOngoing(true)` + `setSilent(true)` belt-and-braces the `IMPORTANCE_MIN`
 * channel: on HyperOS the channel importance can be overridden by the user's
 * per-app settings; `setSilent` ensures no sound regardless.
 */
fun buildWarmupNotification(context: Context): Notification {
    val intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    val pi =
        PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    return NotificationCompat
        .Builder(context, NotificationChannels.WARMUP_CHANNEL_ID)
        .setContentTitle(context.getString(R.string.copy_warmup_ongoing))
        .setSmallIcon(R.drawable.ic_curro_notification)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .setContentIntent(pi)
        .build()
}
