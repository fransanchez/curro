package com.curro.app.presentation.launcher

/**
 * Provisional listening/speaking state for SF-2.3 (US-017) — driven by [LauncherViewModel],
 * rendered by `ListeningOverlay` (SF-2.4 / US-018).
 *
 * PROVISIONAL (US-017) — Phase 5 replaces this with [com.curro.app.assistant.AssistantStateMachine].
 * The Phase-5 mapping is documented in US-017 §5; the `Event` shape from
 * [com.curro.app.domain.repository.SttClient] is forward-compatible so the Phase-5 swap is
 * a state-owner change, not a wire-protocol change.
 *
 * Visual modes rendered by `ListeningOverlay`:
 * - [Idle]: overlay not rendered at all (the `AnimatedVisibility` in
 *   `LauncherPlaceholderContent` is `visible = state !is Idle`).
 * - [Starting] / [Listening]: blue tint, "Te escucho…" headline, transcript line,
 *   ANIMATED audio-wave.
 * - [Speaking]: blue tint, "Te escucho…" still (no shift), the spoken text, STATIC audio-wave.
 * - [Error]: blue tint, the error MESSAGE replacing "Te escucho…", no transcript line,
 *   STATIC audio-wave.
 */
sealed interface ListeningState {
    /** No active listening session — the overlay is hidden. */
    data object Idle : ListeningState

    /** Permission granted, STT session not yet emitting partials. */
    data object Starting : ListeningState

    /** STT is emitting partials. [partialText] is the most recent. */
    data class Listening(val partialText: String) : ListeningState

    /**
     * SF-3.6 (US-024) — between Listening(final) and Speaking(echo). Driven by
     * [LauncherViewModel] during `engine.decide` + validator.
     *
     * PROVISIONAL — Phase 5 replaces this whole sealed interface with the
     * [com.curro.app.assistant.AssistantStateMachine]'s `processing` state.
     */
    data class Processing(val transcript: String) : ListeningState

    /** Curro is echoing [text] via TTS. */
    data class Speaking(val text: String) : ListeningState

    /** Recoverable error: [message] is the spoken+shown Spanish line. */
    data class Error(val message: String) : ListeningState
}
