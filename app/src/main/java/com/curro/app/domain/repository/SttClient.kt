package com.curro.app.domain.repository

import com.curro.app.domain.model.Contact
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
     * Short, constrained-vocabulary listening for a yes / no confirmation
     * (SF-6.2 / US-042). Emits exactly one [ConfirmationVoice] terminal event,
     * then the Flow closes. Cancelling the collecting coroutine releases the
     * native recogniser via `awaitClose`.
     *
     * Differences from [listen]:
     *  - shorter internal timeout (the recogniser auto-closes on ~5 s of
     *    silence; pinned in the impl);
     *  - no partial events — the screen already shows "¿Llamo a Pepe?" + the
     *    SÍ/NO buttons, so a live transcript here adds noise;
     *  - the result is mapped to [ConfirmationVoice] via a fixed Spanish
     *    vocabulary (sí/vale/claro/dale/venga/ok → Yes; no/cancela/déjalo →
     *    No; anything else → Other).
     */
    fun listenForConfirmation(): Flow<ConfirmationVoice>

    /**
     * Short, constrained-vocabulary listening for a contact pick (SF-6.3 /
     * US-043). Emits exactly one [PickerVoice] terminal event, then closes.
     *
     * Vocabulary, in order:
     *  - Each candidate's `displayName` (full) and `displayName.split(' ').first()`
     *    (first name).
     *  - The Spanish ordinals for the visible positions: `primero/primera`,
     *    `segundo/segunda`, `tercero/tercera` (with or without `"la "` /
     *    `"el "` prefix).
     *  - `ninguna` / `ninguno` / `ningún` / `nadie` → `PickerVoice.None`.
     *  - Anything else → `PickerVoice.Other(text)`.
     *  - Empty STT / `ERROR_NO_MATCH` → `PickerVoice.Failed`.
     *
     * **Pinned edge case**: two candidates sharing the same first name
     * ("María García" + "María López") with STT result "María" → `Other`.
     * The user must say the full name or the ordinal.
     */
    fun listenForPicker(candidates: List<Contact>): Flow<PickerVoice>

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

/**
 * Result of a [SttClient.listenForConfirmation] pass (SF-6.2 / US-042).
 *
 * The recogniser returns plain text; the mapper normalises (lowercase, strip
 * accents) and matches the Spanish vocabulary.
 *
 * Vocabulary (pinned in [com.curro.app.data.voice.SystemSttClient]):
 *   - Yes: "sí", "si", "vale", "claro", "dale", "venga", "okay", "ok"
 *   - No: "no", "cancela", "cancelar", "déjalo", "dejalo", "no llames",
 *     "no quiero"
 *   - Anything else → [Other]
 *   - Empty STT / ERROR_NO_MATCH → [Failed]
 */
sealed interface ConfirmationVoice {
    /** STT result matched the yes vocabulary. */
    data object Yes : ConfirmationVoice

    /** STT result matched the no vocabulary. */
    data object No : ConfirmationVoice

    /** STT returned something but it didn't match. The coordinator re-listens. */
    data class Other(val text: String) : ConfirmationVoice

    /** STT failed (timeout, error). The coordinator treats this as Other. */
    data class Failed(val error: CurroError) : ConfirmationVoice
}

/**
 * SF-6.3 (US-043) — result of a [SttClient.listenForPicker] pass.
 *
 * The recogniser returns plain text; the picker mapper normalises and matches
 * against candidate names + Spanish ordinals + "ninguna".
 */
sealed interface PickerVoice {
    /** The user named a specific candidate. */
    data class Pick(val contact: Contact) : PickerVoice

    /** The user said "ninguna" / "ninguno" / etc. */
    data object None : PickerVoice

    /** STT returned something but it didn't match. */
    data class Other(val text: String) : PickerVoice

    /** STT failed (timeout, error). */
    data class Failed(val error: CurroError) : PickerVoice
}
