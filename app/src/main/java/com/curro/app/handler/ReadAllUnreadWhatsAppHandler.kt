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
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TextGenEngine
import com.curro.app.domain.repository.TtsClient
import com.curro.app.handler.whatsapp.SummaryOutputCleaner
import com.curro.app.handler.whatsapp.WhatsAppSummaryPromptBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Reads every unread WhatsApp aloud (US-032 / SF-4.8) — and, when there are
 * more than 8 unread, produces a per-sender summary via the on-device
 * large-text engine (US-062 / SF-9.3; backed by Gemma 4 E2B since the May 2026
 * swap — see [TextGenEngine] KDoc) with a graceful fallback to the existing
 * `copy_many_unread` line on any engine failure (weights missing, cold-load
 * fail, OOM, malformed output).
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
 * If the cache has > 8 messages → [summariseOrFallback] (US-062 / SF-9.3).
 *
 * Gate check: if notification access is not granted → [HandlerResult.Failed] with
 * [CurroError.NotificationAccessMissing].
 *
 * Body joiner `". "` gives the TTS engine clear sentence breaks between bodies.
 * `needs_confirmation: NO`. No new permissions (US-030 added them).
 *
 * **`@Suppress("LongParameterList")`**: this handler intentionally orchestrates
 * a wide surface — notification cache + access gate + Android context + the
 * entire US-062 / SF-9.3 summarisation pipeline (engine + weights probe +
 * prompt builder + cleaner + TTS for the cold-model line + telemetry).
 * Splitting it through a wrapper object would just rename the problem.
 */
@Suppress("LongParameterList")
class ReadAllUnreadWhatsAppHandler
    @Inject
    constructor(
        private val notifications: NotificationRepository,
        private val accessGate: NotificationAccessGate,
        @ApplicationContext private val context: Context,
        // US-062 / SF-9.3 — summarisation branch dependencies.
        private val textGenEngine: TextGenEngine,
        private val modelFiles: com.curro.app.data.ml.ModelFiles,
        private val promptBuilder: WhatsAppSummaryPromptBuilder,
        private val cleaner: SummaryOutputCleaner,
        private val ttsClient: TtsClient,
        private val telemetry: TelemetrySink,
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
                return summariseOrFallback(all)
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

        // ── US-062 / SF-9.3 — summarisation branch ────────────────────────────

        /**
         * Tries the large-text engine (Gemma 4 E2B); on any failure falls back
         * to the existing `copy_many_unread` line so the 4 GB / 6 GB worst case
         * is functionally identical to today's behaviour.
         *
         * Speaks `copy_cold_model` ("Dame un segundo.") via [ttsClient]
         * directly (not via the coordinator) when the load is *expected to
         * succeed* (weights present, engine cold). This is a deliberate
         * departure from the all-speech-via-coordinator pattern — see PM
         * brief US-062 §8.5 Pin. Rationale in short: the cold-model line is
         * pre-result; there is no `HandlerResult` to wrap it in; the
         * coordinator is in `Executing` and cannot interleave a TTS call;
         * the handler owns the latency knowledge.
         */
        private suspend fun summariseOrFallback(unread: List<WhatsAppMessage>): HandlerResult {
            val weightsPresent = modelFiles.isGemma3nAvailable()
            val isReady = textGenEngine.isReady.value
            var coldSpoken = false

            // Only tease the cold-model line if the load is expected to succeed.
            // If weights are missing we go straight to the fallback (telling the
            // user "Dame un segundo" and then falling back would be cruel).
            if (!isReady && weightsPresent) {
                ttsClient.speak(context.getString(R.string.copy_cold_model))
                coldSpoken = true
            }

            val prompt = promptBuilder.build(unread)
            val result = textGenEngine.generate(prompt)

            return result.fold(
                onSuccess = { rawOutput ->
                    val cleaned = cleaner.clean(rawOutput)
                    emitSummaryTelemetry(
                        outcome = "success",
                        senderCount = unread.groupBy { it.sender }.size,
                        messageCount = unread.size,
                        coldSpoken = coldSpoken,
                    )
                    HandlerResult.Spoken(
                        context.getString(R.string.copy_summary_intro) + " " + cleaned,
                    )
                },
                onFailure = { err ->
                    val outcome =
                        when (err) {
                            is CurroError.ModelCold -> "fallback_cold"
                            is CurroError.OutOfMemory -> "fallback_oom"
                            else -> "fallback_invalid_output"
                        }
                    emitSummaryTelemetry(
                        outcome = outcome,
                        senderCount = unread.groupBy { it.sender }.size,
                        messageCount = unread.size,
                        coldSpoken = coldSpoken,
                    )
                    HandlerResult.Spoken(context.getString(R.string.copy_many_unread))
                },
            )
        }

        private fun emitSummaryTelemetry(
            outcome: String,
            senderCount: Int,
            messageCount: Int,
            coldSpoken: Boolean,
        ) {
            telemetry.event(
                "summary_generated",
                mapOf(
                    "outcome" to outcome,
                    "sender_count_bucket" to bucketSenderCount(senderCount),
                    "message_count_bucket" to bucketMessageCount(messageCount),
                    "cold_spoken" to coldSpoken,
                ),
            )
        }

        private fun bucketSenderCount(n: Int): String =
            when {
                n <= SENDER_BUCKET_ONE -> "1"
                n == SENDER_BUCKET_TWO -> "2"
                n == SENDER_BUCKET_THREE -> "3"
                else -> "4plus"
            }

        private fun bucketMessageCount(n: Int): String =
            when {
                n <= MESSAGE_BUCKET_LO_HI -> "9to12"
                n <= MESSAGE_BUCKET_MID_HI -> "13to20"
                else -> "21plus"
            }

        // ── ≤ 8 unread — existing helpers, unchanged ──────────────────────────

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

            // Sender-count telemetry buckets — the small distinct cases (1, 2, 3)
            // are surfaced individually; everything else collapses to "4plus".
            const val SENDER_BUCKET_ONE = 1
            const val SENDER_BUCKET_TWO = 2
            const val SENDER_BUCKET_THREE = 3

            // Message-count telemetry buckets. The 9to12 / 13to20 / 21plus split keeps
            // the prototype's reasonable upper bound (~30 unread max) inside three
            // labels without leaking the exact count (spec §12 PII rule).
            const val MESSAGE_BUCKET_LO_HI = 12
            const val MESSAGE_BUCKET_MID_HI = 20
        }
    }
