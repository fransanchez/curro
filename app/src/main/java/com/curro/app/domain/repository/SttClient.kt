package com.curro.app.domain.repository

import com.curro.app.domain.model.CurroError
import kotlinx.coroutines.flow.Flow

/**
 * Offline Spanish speech-to-text (SF-2.1 / US-015).
 *
 * Consumers collect [listen]; the Flow emits zero-to-many [Event.Partial], then exactly
 * one terminal event — either [Event.Final] (success) or [Event.Failed] (any error).
 * Cancelling the collecting coroutine releases the native recogniser session via
 * `awaitClose` in the implementation.
 *
 * The implementation:
 * - MUST run its `callbackFlow` body on `Dispatchers.Main.immediate`
 *   (`SpeechRecognizer` is main-thread-bound; see US-015 §11 + the prior incidents
 *   796b5f4 and b77d789).
 * - MUST never call any network API and MUST set `EXTRA_PREFER_OFFLINE = true`.
 * - MUST configure `EXTRA_LANGUAGE = "es-ES"` and `EXTRA_PARTIAL_RESULTS = true`.
 * - MUST not retain a `Context` outside its `@Singleton` scope.
 */
interface SttClient {
    /**
     * Start a recognition session. Emits zero-to-many [Event.Partial] followed by exactly
     * one [Event.Final] or [Event.Failed], then closes.
     */
    fun listen(): Flow<Event>

    /**
     * Cancel any in-flight [listen] session. Idempotent. Safe to call from any thread —
     * the implementation marshals to Main if needed.
     *
     * Phase 2 does not invoke this directly (US-017 cancels the collecting coroutine,
     * which triggers `awaitClose`); Phase 5's FSM uses it for the interrupt-by-button
     * rule.
     */
    fun cancel()

    /**
     * Best-effort probe — true iff the device claims it can run STT for Spanish locally
     * (`SpeechRecognizer.isOnDeviceRecognitionAvailable` is true on Android 12+).
     */
    suspend fun hasOfflineSpanish(): Boolean

    /** Terminal-or-partial events emitted by [listen]. */
    sealed interface Event {
        /** A live partial transcript chunk. Always non-empty. May be emitted many times per session. */
        data class Partial(val text: String) : Event

        /** Final recognised text. Always non-empty. Followed by Flow closure. */
        data class Final(val text: String) : Event

        /** Recognition ended with an error. Followed by Flow closure. */
        data class Failed(val error: CurroError) : Event
    }
}
