package com.curro.app.assistant

import com.curro.app.domain.handler.AssistantScreen

/**
 * The six-state assistant FSM defined by spec §6.
 *
 * Owned by [AssistantStateMachine] (the single mutator). Read by the launcher
 * [com.curro.app.presentation.launcher.LauncherViewModel] (SF-5.2 wiring), the
 * `AssistantCoordinator` (SF-5.2), and `MainActivity.onNewIntent` (SF-5.6).
 *
 * Every state carries the data its UI overlay and behaviour need; nothing extra
 * (per spec §11 the overlays are state-driven, not navigation routes).
 *
 * **Phase 5 boundary** — this replaces the provisional
 * [com.curro.app.presentation.launcher.ListeningState] from SF-2.3. The fields
 * [Confirming.expiresAtMs] and [ErrorRecovery.failureCount] are present **on
 * purpose** in this SF so Phase 6's confirmation timer and SF-5.4's STT failure
 * counter don't have to reshape the state container — they only need to
 * populate the field on the event and consume it on the state.
 */
sealed interface AssistantState {
    /**
     * Launcher home is showing; nothing in flight. The only state at app start
     * and the canonical "we're done" state after every turn.
     */
    data object Idle : AssistantState

    /**
     * STT is active.
     *
     * @param partial the most recent partial transcript (`""` until the
     *   recogniser emits the first partial). Drives `ListeningOverlay`
     *   (SF-5.5).
     * @param startedAtMs epoch-ms when the mic was pressed
     *   (from [TimeProvider]). Used by SF-5.4's silence-cancel timer and by
     *   the overlay for the "still listening" affordance.
     */
    data class Listening(
        val partial: String,
        val startedAtMs: Long,
    ) : AssistantState

    /**
     * STT has emitted a final transcript; FunctionGemma + validator are
     * running.
     *
     * @param transcript the final STT transcript that triggered processing.
     * @param startedAtMs epoch-ms when processing started
     *   (from [TimeProvider]). Drives `ProcessingOverlay` (SF-5.5) — currently
     *   a static "Un momento…", but a future SF may want to show
     *   "Tardo más de lo normal" past a threshold.
     */
    data class Processing(
        val transcript: String,
        val startedAtMs: Long,
    ) : AssistantState

    /**
     * The handler decided this action needs explicit user confirmation
     * ([com.curro.app.domain.handler.HandlerResult.NeedsConfirmation] OR
     * Phase 6's `ConfidencePolicy` returned `Confirm`).
     *
     * @param prompt the Spanish line Curro speaks + shows
     *   (e.g. "¿Llamo a Pepe Martínez?").
     * @param expiresAtMs deadline (`TimeProvider.now() + 10_000`
     *   per spec §6 flow 2). Phase 6 enforces the timeout via a coroutine
     *   timer; this SF carries the field so Phase 6 doesn't have to refactor
     *   the state. Until Phase 6 wires the timer, this field is informational
     *   (the coordinator's `UserConfirmed`/`UserRejected`/
     *   `ConfirmationTimedOut` events drive the transitions out).
     * @param pendingAction opaque container for the action to invoke on
     *   confirmation.
     */
    data class Confirming(
        val prompt: String,
        val expiresAtMs: Long,
        val pendingAction: PendingAction,
    ) : AssistantState

    /**
     * Curro is executing + speaking the outcome of a handler.
     *
     * @param speech the Spanish line TTS is speaking (and that the
     *   `ExecutingOverlay` shows — SF-5.5).
     * @param screen optional state-driven overlay payload — currently always
     *   `null` for Phase 4 handlers (per `HandlerResult.Spoken.screen`);
     *   Phase 5 carries it through; Phase 6/7 fills `MessageCardsScreen` /
     *   `ContactPickerScreen` via [AssistantScreen] subclasses.
     */
    data class Executing(
        val speech: String,
        val screen: AssistantScreen?,
    ) : AssistantState

    /**
     * STT/decision/handler failure — Curro speaks a plain Spanish line + an
     * alternative (spec §2 "Fallar de forma comprensible").
     *
     * @param message the Spanish line being spoken + shown.
     * @param failureCount the value of `SttFailureCounter` *after* the
     *   incrementing failure (1, 2, or 3). SF-5.4 uses this to pick the right
     *   copy and to decide whether to give up. **Non-STT failures (decision
     *   layer / handler) pass `failureCount = 0`** so SF-5.4's counter is not
     *   touched.
     */
    data class ErrorRecovery(
        val message: String,
        val failureCount: Int,
    ) : AssistantState
}
