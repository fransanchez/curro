package com.curro.app.domain.model

/**
 * One unread WhatsApp message, normalised across MessagingStyle and legacy-extras
 * notification shapes (US-030 / SF-4.6).
 *
 * Phase 7 swaps the cache backend to Room without changing this class.
 *
 * Privacy: [sender], [chatTitle], and [text] are NEVER logged or surfaced to telemetry.
 */
data class WhatsAppMessage(
    /** `sbn.key` (Tier 2) or `sbn.key + "#" + timestamp` (Tier 1) — used to deduplicate in the cache. */
    val key: String,
    /** 1:1 chat: the contact's display name. Group chat: the individual sender's Person.name. */
    val sender: String,
    /** Group chat name, or same as [sender] for 1:1. */
    val chatTitle: String,
    /** Normalised body text, or a marker string for non-text classifications (e.g. "[audio]"). */
    val text: String,
    val isGroup: Boolean,
    /** Milliseconds epoch — from `msg.timestamp` (Tier 1) or `sbn.postTime` (Tier 2). */
    val timestamp: Long,
    val classification: Classification,
) {
    /** What kind of content is in [text]. Handlers speak different lines per classification. */
    enum class Classification {
        TEXT,
        EMOJI,
        VOICE_NOTE,
        IMAGE,
        OTHER,
    }
}
