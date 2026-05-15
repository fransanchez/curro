package com.curro.app.domain.repository

import com.curro.app.domain.model.CurroError
import java.util.UUID

/**
 * Spanish text-to-speech (SF-2.2 / US-016).
 *
 * [speak] is a suspending function that resolves when the utterance reaches a terminal
 * state (completed normally, cancelled by [stop] / coroutine cancellation, or failed at
 * the synthesis layer). Cancelling the calling coroutine cancels the utterance via
 * `invokeOnCancellation { tts.stop() }`; [SpeakResult.Cancelled] is the result of either
 * an explicit [stop] or coroutine cancellation.
 *
 * Voice configuration (spec §14 — non-negotiable):
 * - Language: `es-ES`.
 * - Speech rate: ~12 % slower than default (0.88f). The spec says "10–15 % slower"; 0.88
 *   sits mid-band and is the value Phase 2 ships. Phase 8 will expose this in the config
 *   menu (alongside pitch and voice picker).
 * - Voice: best-effort male `es` voice from `TextToSpeech.voices`; falls back to the
 *   system default for the locale.
 */
interface TtsClient {
    /**
     * Speak [text] in Spanish at the configured rate. Suspends until the utterance
     * reaches a terminal state.
     *
     * @param text The Spanish utterance. Must not be empty (the implementation throws
     *     `IllegalArgumentException` on empty input).
     * @param utteranceId A unique identifier for this utterance (used by the framework's
     *     [android.speech.tts.UtteranceProgressListener]). Default generates a UUID.
     */
    suspend fun speak(
        text: String,
        utteranceId: String = UUID.randomUUID().toString(),
    ): SpeakResult

    /**
     * Interrupt any in-flight utterance synchronously. Idempotent — safe to call when
     * nothing is being spoken.
     *
     * Phase 2 does not invoke this directly (US-017 cancels the speak coroutine instead,
     * which propagates via `invokeOnCancellation`). Phase 5's `AssistantStateMachine`
     * uses it for the interrupt-by-button rule.
     */
    fun stop()

    /** True while an utterance is being synthesised or played. */
    fun isSpeaking(): Boolean

    sealed interface SpeakResult {
        /** Utterance completed normally — [android.speech.tts.UtteranceProgressListener.onDone]. */
        data object Completed : SpeakResult

        /** Utterance was cancelled — explicit [stop], coroutine cancellation, or onStop. */
        data object Cancelled : SpeakResult

        /** Synthesis failed — onError(id, code), or initialisation failed. */
        data class Failed(val error: CurroError) : SpeakResult
    }
}
