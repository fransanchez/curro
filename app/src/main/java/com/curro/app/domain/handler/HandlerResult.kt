package com.curro.app.domain.handler

import com.curro.app.domain.model.CurroError

/**
 * Closed set of outcomes a [FunctionHandler] can return.
 *
 * Phase 4 — every result terminates a turn directly. Phase 6 inserts the
 * confidence policy gate between the dispatcher and a [NeedsConfirmation]
 * branch (so far the only handler that emits [NeedsConfirmation] is
 * `call_contact`, and only Phase 6's policy decides whether to execute or
 * prompt).
 */
sealed interface HandlerResult {
    /**
     * The handler did its work. [speech] is the Spanish line TTS speaks (and
     * the UI shows). [screen] is an optional state-driven overlay payload;
     * Phase 5's FSM populates the [AssistantScreen] subclasses — Phase 4
     * always leaves this `null`.
     */
    data class Spoken(
        val speech: String,
        val screen: AssistantScreen? = null,
    ) : HandlerResult

    /**
     * The handler is ready to execute but the action is irreversible / ambiguous /
     * the user has "always confirm" on. Phase 4 — the dispatcher's caller
     * auto-invokes [onConfirm] and recurses. Phase 6 — the policy gate
     * intercepts this branch.
     */
    data class NeedsConfirmation(
        val prompt: String,
        val onConfirm: suspend () -> HandlerResult,
    ) : HandlerResult

    /**
     * The handler couldn't do it. [speech] explains why in plain Spanish
     * (never a code, never silence). [reason] is the typed [CurroError] for
     * the failed-command log + telemetry.
     */
    data class Failed(
        val speech: String,
        val reason: CurroError,
    ) : HandlerResult
}
