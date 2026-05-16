package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.apps.curroNormalize
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
 * Reads the latest unread WhatsApp message aloud (US-031 / SF-4.7).
 *
 * Optional `sender` param (from FunctionGemma) filters the cache by sender name.
 * Matching is case + accent insensitive via [curroNormalize].
 *
 * Classification dispatch:
 *   TEXT       → "Tienes un mensaje de <sender>: <body>"
 *   EMOJI      → "Tienes un mensaje de <sender>: te ha mandado un emoji."
 *   VOICE_NOTE → "Tienes un mensaje de <sender>: te ha mandado un audio."
 *   IMAGE      → "Tienes un mensaje de <sender>: te ha mandado una foto."
 *   OTHER      → falls through to copy_whatsapp_parse_miss
 *
 * Gate check: if notification access is not granted, returns [HandlerResult.Failed]
 * with [CurroError.NotificationAccessMissing] — never a code.
 *
 * `needs_confirmation: NO`. No new permissions (US-030 added them).
 */
class ReadLastWhatsAppHandler
    @Inject
    constructor(
        private val notifications: NotificationRepository,
        private val accessGate: NotificationAccessGate,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "read_last_whatsapp"

        @Suppress("ReturnCount")
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

            val senderQuery = (call.params["sender"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val pool =
                if (senderQuery == null) {
                    all
                } else {
                    val normalised = senderQuery.curroNormalize()
                    all.filter {
                        it.sender.curroNormalize() == normalised ||
                            it.chatTitle.curroNormalize() == normalised ||
                            normalised in it.sender.curroNormalize() ||
                            normalised in it.chatTitle.curroNormalize()
                    }
                }

            if (pool.isEmpty() && senderQuery != null) {
                return HandlerResult.Spoken(
                    context.getString(R.string.copy_no_unread_from, senderQuery),
                )
            }
            if (pool.isEmpty()) {
                return HandlerResult.Spoken(context.getString(R.string.copy_no_unread))
            }

            val latest = pool.maxByOrNull { it.timestamp } ?: pool.first()
            return HandlerResult.Spoken(speechFor(latest))
        }

        private fun speechFor(msg: WhatsAppMessage): String =
            when (msg.classification) {
                Classification.TEXT ->
                    context.getString(R.string.copy_read_last_text, msg.sender, msg.text)
                Classification.EMOJI ->
                    context.getString(R.string.copy_read_last_emoji, msg.sender)
                Classification.VOICE_NOTE ->
                    context.getString(R.string.copy_read_last_voice, msg.sender)
                Classification.IMAGE ->
                    context.getString(R.string.copy_read_last_image, msg.sender)
                Classification.OTHER ->
                    context.getString(R.string.copy_whatsapp_parse_miss)
            }
    }
