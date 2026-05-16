package com.curro.app.domain.repository

import com.curro.app.domain.model.WhatsAppMessage
import kotlinx.coroutines.flow.Flow

/**
 * The in-memory unread-message cache contract (US-030 / SF-4.6).
 *
 * Phase 4: backed by [com.curro.app.data.notification.UnreadMessageCache]
 *          (a `MutableStateFlow<Map<String, WhatsAppMessage>>`).
 * Phase 7: backed by Room — same contract, no call-site changes.
 *
 * Privacy: the cache lives in-process; nothing here is ever surfaced to telemetry.
 */
interface NotificationRepository {
    /** All unread messages, latest snapshot. Empty list when nothing is pending. */
    val allUnread: Flow<List<WhatsAppMessage>>

    /** Unread messages from a specific sender (case-insensitive contains match on sender/chatTitle). */
    fun unreadBySender(sender: String): Flow<List<WhatsAppMessage>>

    /**
     * Count of notifications the parser could not handle. A value > 0 means "there ARE
     * unread WhatsApps but their shape is unknown"; consumer handlers (US-031/US-032) speak
     * [com.curro.app.R.string.copy_whatsapp_parse_miss].
     */
    val parseMissCount: Flow<Int>

    /**
     * Drop all entries from [sender]. Called when a chat is opened or dismissed.
     * Match is case-insensitive on both [WhatsAppMessage.sender] and [WhatsAppMessage.chatTitle].
     */
    fun clear(sender: String)
}
