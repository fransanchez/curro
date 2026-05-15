package com.curro.app.domain.model

/**
 * Curro's failure model — the closed set of things that can go wrong end-to-end.
 *
 * There is no HTTP / REST in Curro: failures are speech-to-text errors, on-device-model
 * outputs that don't pass validation, missing system permissions, and handler-level
 * "I-can't-do-that" cases. Every variant maps to a calm Spanish sentence (in `strings.xml`
 * / the `brand-design` COPY table) — never a code, never silence (spec §2:
 * "Fallar de forma comprensible").
 *
 * Phase 2 (US-015 + US-016) lands the STT and TTS variants and the generic
 * [PermissionDenied]. The Decision-layer and Execution-layer variants are stubbed here so
 * downstream code (handlers, the FSM, error-display) can already exhaustively `when` against
 * the sealed hierarchy. Adding a variant in a later phase is a one-line change here and a
 * `when` arm anywhere it's surfaced.
 */
sealed interface CurroError {
    // ── Speech-to-text ────────────────────────────────────────────────────────

    /** `RecognitionListener.onError(ERROR_NO_MATCH)` or `onResults` with an empty result list. */
    data object SttNoMatch : CurroError

    /** `RecognitionListener.onError(ERROR_SPEECH_TIMEOUT)`. */
    data object SttTimeout : CurroError

    /** Any other `RecognitionListener.onError(code)`. [code] is the raw `SpeechRecognizer.ERROR_*`. */
    data class SttError(val code: Int) : CurroError

    /**
     * `SpeechRecognizer.isOnDeviceRecognitionAvailable` returned false, OR `onError`
     * reported `ERROR_LANGUAGE_NOT_SUPPORTED` / `ERROR_LANGUAGE_UNAVAILABLE`.
     * Means the Spanish offline voice pack is not installed on this device.
     */
    data object SttVoicePackMissing : CurroError

    // ── Text-to-speech ────────────────────────────────────────────────────────

    /** `TextToSpeech.setLanguage` returned `LANG_MISSING_DATA` or `LANG_NOT_SUPPORTED`. */
    data object TtsLanguageMissing : CurroError

    /** `UtteranceProgressListener.onError(_, code)` — a synthesis-layer failure. */
    data class TtsError(val code: Int) : CurroError

    // ── Permissions ───────────────────────────────────────────────────────────

    /**
     * Any runtime permission was denied. The caller decides which permission
     * (RECORD_AUDIO, CALL_PHONE, READ_CONTACTS, …) by context; the screen distinguishes
     * via its own copy.
     */
    data object PermissionDenied : CurroError
}
