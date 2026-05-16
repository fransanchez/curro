package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.model.WhatsAppMessage.Classification
import com.curro.app.domain.repository.NotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Reads every unread WhatsApp aloud, grouped by sender (US-032 / SF-4.8).
 *
 * Spec §6 flow 5: messages are grouped by sender, sorted by group's most-recent
 * timestamp (most-active sender first). Within each group, messages are in
 * chronological order. The header counts senders:
 *
 *   1 sender, 1 msg  → copy_reading_summary_one
 *   1 sender, N msgs → copy_reading_summary_many
 *   2 senders        → copy_reading_summary_multi_sender
 *   3+ senders       → copy_reading_summary_three_plus (first 3 names only)
 *
 * If the cache has > 8 messages → copy_many_unread (Phase-5/6 wires the follow-up).
 *
 * Gate check: if notification access is not granted → [HandlerResult.Failed] with
 * [CurroError.NotificationAccessMissing].
 *
 * Body joiner `". "` gives the TTS engine clear sentence breaks between bodies.
 * `needs_confirmation: NO`. No new permissions (US-030 added them).
 */
class ReadAllUnreadWhatsAppHandler
    @Inject
    constructor(
        private val notifications: NotificationRepository,
        private val accessGate: NotificationAccessGate,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "read_all_unread_whatsapp"

        @Suppress("ReturnCount", "CyclomaticComplexMethod")
        override suspend fun handle(call: FunctionCall): HandlerResult {
            if (!accessGate.isGranted()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_perm_missing_notifs),
                    CurroError.NotificationAccessMissing,
                )
            }
            val all = notifications.allUnread.first()
            val misses = notifications.parseMissCount.first()

            if (all.isEmpty() && misses > 0) {
                return HandlerResult.Spoken(context.getString(R.string.copy_whatsapp_parse_miss))
            }
            if (all.isEmpty()) {
                return HandlerResult.Spoken(context.getString(R.string.copy_no_unread))
            }
            if (all.size > MANY_THRESHOLD) {
                return HandlerResult.Spoken(context.getString(R.string.copy_many_unread))
            }

            // Group by sender, sorted by group's most-recent timestamp (desc).
            // Within each group, messages are in chronological order (oldest first).
            val groups: List<Pair<String, List<WhatsAppMessage>>> =
                all
                    .groupBy { it.sender }
                    .map { (sender, msgs) -> sender to msgs.sortedBy { it.timestamp } }
                    .sortedByDescending { (_, msgs) -> msgs.maxOf { it.timestamp } }

            val speech =
                buildString {
                    append(buildHeader(groups))
                    groups.forEachIndexed { idx, (sender, msgs) ->
                        append(' ')
                        append(buildGroupSpeech(sender, msgs, isFirst = idx == 0))
                    }
                }
            return HandlerResult.Spoken(speech)
        }

        /**
         * Builds the header phrase from the grouped senders.
         *
         *   1 sender, 1 msg  → copy_reading_summary_one    (%1$d count, %2$s sender)
         *   1 sender, N msgs → copy_reading_summary_many   (%1$d count, %2$s sender)
         *   2 senders        → copy_reading_summary_multi_sender (%1$d, %2$s, %3$d, %4$s)
         *   3+ senders       → copy_reading_summary_three_plus  (first 3: %1$s, %2$s, %3$s)
         */
        private fun buildHeader(groups: List<Pair<String, List<WhatsAppMessage>>>): String =
            when (groups.size) {
                1 -> {
                    val (sender, msgs) = groups.first()
                    val res =
                        if (msgs.size == 1) {
                            R.string.copy_reading_summary_one
                        } else {
                            R.string.copy_reading_summary_many
                        }
                    context.getString(res, msgs.size, sender)
                }
                2 -> {
                    val (s1, m1) = groups[0]
                    val (s2, m2) = groups[1]
                    context.getString(
                        R.string.copy_reading_summary_multi_sender,
                        m1.size,
                        s1,
                        m2.size,
                        s2,
                    )
                }
                else -> {
                    context.getString(
                        R.string.copy_reading_summary_three_plus,
                        groups[0].first,
                        groups[1].first,
                        groups[2].first,
                    )
                }
            }

        /**
         * Builds the spoken text for one sender's group.
         *
         * First group: "Empiezo con %s: <bodies>."  (copy_reading_starts_with)
         * Subsequent:  "De %s: <first body><rest>." (copy_reading_from)
         *
         * Bodies within a group are joined by ". " so the TTS engine pauses between messages.
         */
        private fun buildGroupSpeech(
            sender: String,
            msgs: List<WhatsAppMessage>,
            isFirst: Boolean,
        ): String {
            val bodies = msgs.joinToString(". ") { bodySpeech(it) }
            return if (isFirst) {
                context.getString(R.string.copy_reading_starts_with, sender) + ' ' + bodies + '.'
            } else {
                val first = msgs.first()
                val rest = msgs.drop(1).joinToString("") { ". ${bodySpeech(it)}" }
                context.getString(R.string.copy_reading_from, sender, bodySpeech(first)) + rest + '.'
            }
        }

        /**
         * Returns the spoken fragment for a single message body based on its classification.
         * These fragments are composed into the surrounding phrase — they are not standalone strings.
         * NON-TEXT markers use colloquial Spanish consistent with the single-message handler.
         */
        private fun bodySpeech(msg: WhatsAppMessage): String =
            when (msg.classification) {
                Classification.TEXT -> msg.text
                Classification.EMOJI -> "te ha mandado un emoji"
                Classification.VOICE_NOTE -> "te ha mandado un audio"
                Classification.IMAGE -> "te ha mandado una foto"
                Classification.OTHER -> "no he podido leer ese mensaje"
            }

        private companion object {
            const val MANY_THRESHOLD = 8
        }
    }
