package com.curro.app.data.notification

import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-4 in-memory implementation of [NotificationRepository] (US-030 / SF-4.6).
 *
 * Phase 7 swaps in a Room-backed impl without changing the [NotificationRepository] interface.
 *
 * Keying:
 *  - Tier-1 (MessagingStyle) entries: `sbn.key + "#" + message-timestamp`
 *    so a 3-message SBN occupies 3 cache rows.
 *  - Tier-2 (legacy) entries: `sbn.key`.
 *  When a notification is removed, every row whose key **starts with** `sbn.key` is dropped.
 *
 * Thread-safety: [state] is a [MutableStateFlow] and is safe without a lock.
 * [parseMissKeys] is a `mutableSetOf` accessed from the IO dispatcher and guarded
 * by `synchronized` to keep [parseMisses] consistent.
 */
@Singleton
class UnreadMessageCache
    @Inject
    constructor() : NotificationRepository {
        private val state = MutableStateFlow<Map<String, WhatsAppMessage>>(emptyMap())
        private val parseMisses = MutableStateFlow(0)
        private val parseMissKeys = mutableSetOf<String>()

        override val allUnread: Flow<List<WhatsAppMessage>> =
            state.asStateFlow().map { it.values.toList() }

        override fun unreadBySender(sender: String): Flow<List<WhatsAppMessage>> =
            state.asStateFlow().map { snapshot ->
                snapshot.values.filter {
                    it.sender.equals(sender, ignoreCase = true) ||
                        it.chatTitle.equals(sender, ignoreCase = true)
                }
            }

        override val parseMissCount: Flow<Int> = parseMisses.asStateFlow()

        /** Inserts or replaces [msg] in the cache, keyed by [WhatsAppMessage.key]. */
        fun upsert(msg: WhatsAppMessage) {
            state.update { it + (msg.key to msg) }
        }

        /**
         * Drops all cache entries whose key starts with [sbnKey] (covers both Tier-1 and
         * Tier-2 entries from the same SBN), and decrements [parseMissCount] if this SBN
         * had previously been recorded as a miss.
         */
        fun onRemoved(sbnKey: String) {
            state.update { snapshot -> snapshot.filterKeys { !it.startsWith(sbnKey) } }
            synchronized(parseMissKeys) {
                if (parseMissKeys.remove(sbnKey)) {
                    parseMisses.update { (it - 1).coerceAtLeast(0) }
                }
            }
        }

        /**
         * Records that a notification with [sbnKey] could not be parsed. Idempotent: calling
         * this twice with the same key increments the counter only once.
         */
        fun recordParseMiss(sbnKey: String) {
            synchronized(parseMissKeys) {
                if (parseMissKeys.add(sbnKey)) {
                    parseMisses.update { it + 1 }
                }
            }
        }

        override fun clear(sender: String) {
            state.update { snapshot ->
                snapshot.filterValues {
                    !(
                        it.sender.equals(sender, ignoreCase = true) ||
                            it.chatTitle.equals(sender, ignoreCase = true)
                    )
                }
            }
        }
    }
