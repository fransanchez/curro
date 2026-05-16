package com.curro.app.domain.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.repository.TelemetrySink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a validated [FunctionCall] to the right [FunctionHandler] via a
 * Hilt-multibound map keyed by catalog function name.
 *
 * Failure modes:
 *  - Action not in the map → [HandlerResult.Failed] with [CurroError.UnknownFunction]
 *    and the Spanish line `copy_error_unknown_function`.
 *  - The handler throws → [HandlerResult.Failed] with [CurroError.HandlerCrash]
 *    and the Spanish line `copy_handler_crash`.
 *
 * Telemetry: emits `handler_invoked` with `function_name` + `outcome` ∈
 * {success, needs_confirmation, failed, crash}. Never the utterance, never any
 * param value.
 */
@Singleton
class HandlerDispatcher
    @Inject
    constructor(
        private val handlers: Map<String, @JvmSuppressWildcards FunctionHandler>,
        private val telemetry: TelemetrySink,
        @ApplicationContext private val context: Context,
    ) {
        suspend fun dispatch(call: FunctionCall): HandlerResult {
            val handler =
                handlers[call.action]
                    ?: return reportAndReturn(
                        call.action,
                        HandlerResult.Failed(
                            speech = context.getString(R.string.copy_error_unknown_function),
                            reason = CurroError.UnknownFunction(call.action),
                        ),
                    )
            val result =
                runCatching { handler.handle(call) }.getOrElse { e ->
                    HandlerResult.Failed(
                        speech = context.getString(R.string.copy_handler_crash),
                        reason = CurroError.HandlerCrash(call.action, throwable = e),
                    )
                }
            return reportAndReturn(call.action, result)
        }

        private fun reportAndReturn(
            action: String,
            result: HandlerResult,
        ): HandlerResult {
            val outcome =
                when (result) {
                    is HandlerResult.Spoken -> "success"
                    is HandlerResult.NeedsConfirmation -> "needs_confirmation"
                    is HandlerResult.NeedsContactPick -> "needs_contact_pick"
                    is HandlerResult.Failed ->
                        if (result.reason is CurroError.HandlerCrash) "crash" else "failed"
                }
            telemetry.event(
                "handler_invoked",
                mapOf(
                    "function_name" to action,
                    "outcome" to outcome,
                ),
            )
            return result
        }
    }
