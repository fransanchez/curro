package com.curro.app.data.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.curro.app.di.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Listens for WhatsApp and WhatsApp Business notifications and feeds them into
 * [UnreadMessageCache] via [WhatsAppNotificationParser] (US-030 / SF-4.6).
 *
 * Package filter: only `com.whatsapp` and `com.whatsapp.w4b` are processed.
 * Parsing and cache mutation are dispatched to [Dispatchers.IO] — the
 * `onNotificationPosted` and `onNotificationRemoved` callbacks run on the main thread.
 *
 * HyperOS note: the listener service is subject to the same battery-whitelist requirement
 * as [com.curro.app.service.ModelWarmupService] (see `models/README.md`).
 */
@AndroidEntryPoint
class CurroNotificationListenerService : NotificationListenerService() {
    @Inject
    lateinit var cache: UnreadMessageCache

    @Inject
    lateinit var parser: WhatsAppNotificationParser

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        scope.launch {
            val parsed = parser.parse(sbn)
            if (parsed.isEmpty()) {
                cache.recordParseMiss(sbn.key)
            } else {
                parsed.forEach { msg -> cache.upsert(msg) }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        scope.launch { cache.onRemoved(sbn.key) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
