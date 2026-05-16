package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.apps.curroNormalize
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.FunctionCall
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Speaks a short list of what Curro can do — or a topic-specific how-to (US-029 / SF-4.5).
 *
 * The `topic` param (optional, produced by FunctionGemma) is normalised with [curroNormalize]
 * (imported from US-027's `data/apps/StringNormalization.kt`) and looked up in [TOPIC_MAP].
 * An unrecognised topic falls through to the generic capability list — Curro never says "I don't
 * know about that topic"; it just gives the full menu.
 *
 * `needs_confirmation: NO`. No permissions required.
 */
class HelpHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "help"

        override suspend fun handle(call: FunctionCall): HandlerResult {
            val topic = (call.params["topic"] as? String).orEmpty().trim().curroNormalize()
            val resId = TOPIC_MAP[topic] ?: R.string.copy_help_generic
            return HandlerResult.Spoken(context.getString(resId))
        }

        private companion object {
            /**
             * Maps normalised topic strings to string resource IDs.
             * Keys must be lowercase + accent-stripped (i.e. already curroNormalized).
             * Any topic not in the map falls through to [R.string.copy_help_generic].
             */
            val TOPIC_MAP: Map<String, Int> =
                mapOf(
                    // Calls
                    "llamada" to R.string.copy_help_topic_call,
                    "llamadas" to R.string.copy_help_topic_call,
                    "llamar" to R.string.copy_help_topic_call,
                    "telefono" to R.string.copy_help_topic_call,
                    // WhatsApp
                    "mensaje" to R.string.copy_help_topic_whatsapp,
                    "mensajes" to R.string.copy_help_topic_whatsapp,
                    "whatsapp" to R.string.copy_help_topic_whatsapp,
                    "wasap" to R.string.copy_help_topic_whatsapp,
                    // Apps
                    "app" to R.string.copy_help_topic_app,
                    "apps" to R.string.copy_help_topic_app,
                    "aplicacion" to R.string.copy_help_topic_app,
                    "aplicaciones" to R.string.copy_help_topic_app,
                    // Calculate
                    "calculo" to R.string.copy_help_topic_calculate,
                    "calcular" to R.string.copy_help_topic_calculate,
                    "calculadora" to R.string.copy_help_topic_calculate,
                    "cuentas" to R.string.copy_help_topic_calculate,
                    "cuenta" to R.string.copy_help_topic_calculate,
                    "matematicas" to R.string.copy_help_topic_calculate,
                    // Time
                    "hora" to R.string.copy_help_topic_time,
                    "dia" to R.string.copy_help_topic_time,
                    "fecha" to R.string.copy_help_topic_time,
                )
        }
    }
