package com.curro.app.assistant

import com.curro.app.domain.handler.HandlerResult

/**
 * The action to invoke when the user confirms (`UserConfirmed` event). Phase 5
 * uses this only to carry the metadata; Phase 6 will likely refine this when it
 * adds the `ConfidencePolicy` decision.
 *
 * @param functionName the catalog snake_case name (used for telemetry only).
 * @param onConfirm suspending block that runs the irreversible part —
 *   typically re-dispatches the original
 *   [com.curro.app.domain.model.FunctionCall] (or wraps the
 *   [HandlerResult.NeedsConfirmation.onConfirm] lambda).
 */
data class PendingAction(
    val functionName: String,
    val onConfirm: suspend () -> HandlerResult,
)
