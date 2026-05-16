package com.curro.app.data.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.model.WhatsAppMessage.Classification
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Three-tier defensive WhatsApp notification parser (US-030 / SF-4.6).
 *
 *   Tier 1 — MessagingStyle (modern WhatsApp): structured per-message access.
 *   Tier 2 — legacy `extras` (older WhatsApp / WhatsApp Business): EXTRA_TITLE + EXTRA_TEXT / EXTRA_TEXT_LINES.
 *   Tier 3 — summary notification (FLAG_GROUP_SUMMARY): count-only, no bodies → empty list.
 *   Miss   — anything else → empty list; caller records a parse-miss.
 *
 * NEVER invents content. NEVER guesses at the sender. If a tier produces an empty body
 * or a null sender, the parser moves to the next tier or returns an empty list.
 *
 * Single source for the WhatsApp parsing contract. Adding a new shape = adding a fixture
 * test + a parser branch.
 *
 * [messagingStyleExtractor] is an internal seam for unit testing: production code leaves
 * it at the default (which delegates to the real `NotificationCompat.MessagingStyle`
 * static call), and tests inject a lambda that returns a pre-built mock style object.
 * This avoids `mockkStatic` on a Java class (which requires the MockK instrumentation agent).
 */
@Singleton
class WhatsAppNotificationParser
    @Inject
    constructor() {
        /**
         * Internal seam: extracts a [NotificationCompat.MessagingStyle] from a [Notification].
         * Override in tests to avoid the static `NotificationCompat.MessagingStyle` call.
         */
        internal var messagingStyleExtractor: (Notification) -> NotificationCompat.MessagingStyle? = { n ->
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }

        /**
         * Parses [sbn] into a list of [WhatsAppMessage] instances.
         *
         * @return One [WhatsAppMessage] per individual unread message the SBN encodes.
         *   A MessagingStyle SBN with 3 messages returns 3 elements.
         *   An empty list means "this SBN is a summary / unsupported shape"; the caller
         *   should call `cache.recordParseMiss(sbn.key)` only if Tier 3 also returned empty.
         */
        @Suppress("ReturnCount", "CyclomaticComplexMethod")
        fun parse(sbn: StatusBarNotification): List<WhatsAppMessage> {
            val n = sbn.notification ?: return emptyList()
            val extras = n.extras ?: return emptyList()

            // Tier 3 — summary notification first (cheap branch, avoids deeper parsing).
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return emptyList()

            // Tier 1 — MessagingStyle.
            val styled = messagingStyleExtractor(n)
            if (styled != null && styled.messages.isNotEmpty()) {
                val chatTitle =
                    styled.conversationTitle?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                        ?: return emptyList()
                val isGroup = styled.isGroupConversation
                return styled.messages.mapNotNull { msg ->
                    val rawText = msg.text?.toString() ?: return@mapNotNull null
                    val sender =
                        if (isGroup) msg.person?.name?.toString() ?: chatTitle else chatTitle
                    val (textOut, cls) = classify(rawText, extras)
                    WhatsAppMessage(
                        key = sbn.key + "#" + msg.timestamp,
                        sender = sender,
                        chatTitle = chatTitle,
                        text = textOut,
                        isGroup = isGroup,
                        timestamp = msg.timestamp,
                        classification = cls,
                    )
                }
            }

            // Tier 2 — legacy extras.
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val rawBody =
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: lastTextLine(extras)
                    ?: return emptyList()
            if (title.isNullOrEmpty() || rawBody.isEmpty()) return emptyList()
            val (textOut, cls) = classify(rawBody, extras)
            return listOf(
                WhatsAppMessage(
                    key = sbn.key,
                    sender = title,
                    chatTitle = title,
                    text = textOut,
                    isGroup = false,
                    timestamp = sbn.postTime,
                    classification = cls,
                ),
            )
        }

        private fun lastTextLine(extras: Bundle): String? {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: return null
            return lines.lastOrNull()?.toString()
        }

        /**
         * Classifies a notification body into [Classification] and produces the normalised
         * marker string for non-text bodies. Markers are NOT spoken verbatim — consumer handlers
         * (US-031/US-032) produce the final speech line per classification.
         */
        @Suppress("ReturnCount")
        private fun classify(
            body: String,
            extras: Bundle,
        ): Pair<String, Classification> {
            val info = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
            // Voice note — by extras hint OR by body containing a known WhatsApp voice-note marker.
            if (info.contains("Voice message", ignoreCase = true)) {
                return "[audio]" to Classification.VOICE_NOTE
            }
            if (VOICE_NOTE_RE.containsMatchIn(body)) {
                return "[audio]" to Classification.VOICE_NOTE
            }
            // Image — body matches a known photo marker.
            if (IMAGE_RE.containsMatchIn(body)) {
                return "[foto]" to Classification.IMAGE
            }
            // Emoji-only body — body is all "symbol" / "modifier" code points + whitespace.
            if (body.isNotEmpty() && body.matches(EMOJI_ONLY_RE)) {
                return "[emoji]" to Classification.EMOJI
            }
            return body to Classification.TEXT
        }

        private companion object {
            // \p{So} symbol-other, \p{Sk} modifier symbol, \p{Mn} mark-nonspacing,
            // \p{Cf} format (e.g. ZWJ), whitespace.
            // Java's UNICODE_CHARACTER_CLASS flag is the safe flag for emoji ranges on all JVMs.
            val EMOJI_ONLY_RE: Regex = Regex("^[\\p{So}\\p{Sk}\\p{Mn}\\p{Cf}\\s]+$")

            // WhatsApp voice-note body markers (Spanish + English variants).
            val VOICE_NOTE_RE: Regex =
                Regex(
                    "(🎤\\s*(Voice message|Mensaje de voz|Nota de voz))|" +
                        "\\[(Voice message|Mensaje de voz|Nota de voz)\\]|" +
                        "🎤",
                )

            // WhatsApp image/photo body markers (Spanish + English variants).
            val IMAGE_RE: Regex =
                Regex(
                    "(📷\\s*(Photo|Foto|Imagen))|" +
                        "\\[(Photo|Foto|Imagen)\\]|" +
                        "📷",
                )
        }
    }
