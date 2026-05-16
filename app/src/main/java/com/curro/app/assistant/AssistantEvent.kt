package com.curro.app.assistant

import com.curro.app.domain.handler.AssistantScreen

/**
 * The 11 events the FSM understands. Sent by `AssistantCoordinator` (SF-5.2),
 * `MainActivity.onNewIntent` (SF-5.6), and tests. **Never sent by composables
 * directly** — composables send `LauncherEvent`s to the VM; the VM forwards to
 * the coordinator; the coordinator builds these.
 *
 * Timestamps are epoch-ms from [TimeProvider.now].
 */
sealed interface AssistantEvent {
    /**
     * User tapped the mic button. Valid in every state (interrupt rule, SF-5.3).
     *
     * The FSM only honours the *transition*; cancellation of in-flight Jobs
     * (STT/TTS/model inference) is the coordinator's responsibility — see
     * SF-5.3's `AssistantCoordinator.onMicPressed`.
     */
    data class MicPressed(val timestamp: Long) : AssistantEvent

    /** STT emitted a partial — only meaningful while `Listening`. */
    data class PartialTranscript(val partial: String) : AssistantEvent

    /** STT emitted a final — transitions to `Processing`. */
    data class FinalTranscript(val transcript: String, val timestamp: Long) : AssistantEvent

    /**
     * STT failed (no-match, timeout, recoverable error).
     *
     * @param message the Spanish line to speak + show (already chosen by
     *   SF-5.4 from the COPY table based on [failureCount]).
     * @param failureCount the new counter value after the failure (1, 2, or 3).
     */
    data class SttFailed(val message: String, val failureCount: Int) : AssistantEvent

    /**
     * The decision pipeline finished. Either ready to execute (if
     * `needsConfirmation == false`) or to ask the user (if `true`).
     *
     * Invariants enforced by the FSM (failing them throws
     * [IllegalArgumentException] inside `computeNext`):
     *   - `needsConfirmation == true` ⇒ [prompt], [pendingAction] non-null.
     *   - `needsConfirmation == false` ⇒ [speech] non-null.
     */
    data class FunctionCallReady(
        val needsConfirmation: Boolean,
        val speech: String,
        val screen: AssistantScreen?,
        val prompt: String?,
        val expiresAtMs: Long,
        val pendingAction: PendingAction?,
    ) : AssistantEvent

    /** User pressed SÍ (or said "sí") in `Confirming`. */
    data class UserConfirmed(val speech: String, val screen: AssistantScreen?) : AssistantEvent

    /** User pressed NO (or said "no") in `Confirming`. */
    data object UserRejected : AssistantEvent

    /** 10-s silence in `Confirming` (spec §6 flow 2). Phase 6 fires this. */
    data object ConfirmationTimedOut : AssistantEvent

    /**
     * Decision-layer clarify — `ConfidencePolicy` returned [ConfidenceDecision.Clarify]
     * because `confidence < confirmThreshold` (SF-6.1 / US-041, spec §4.3).
     *
     * Transitions `Processing → ErrorRecovery(message, failureCount = 0)`. The
     * `failureCount = 0` sentinel keeps SF-5.4's STT-failure counter untouched
     * — STT succeeded; this is a model-certainty miss, not a recognition miss.
     */
    data class LowConfidenceClarify(val message: String) : AssistantEvent

    /** `Executing`'s TTS+handler finished — go home. */
    data object ExecutionDone : AssistantEvent

    /** `ErrorRecovery`'s TTS finished — go home. */
    data object RecoverySpoken : AssistantEvent

    /**
     * HOME button pressed (`MainActivity.onNewIntent`, SF-5.6). Valid in
     * every state.
     */
    data object HomePressed : AssistantEvent
}
