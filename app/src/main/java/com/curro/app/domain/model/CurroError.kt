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
 * [PermissionDenied]. Phase 3 (US-020 + US-022) adds the decision-layer variants
 * ([ModelCold], [InvalidFunctionCall], [UnknownFunction], [OutOfMemory]). Adding a
 * variant in a later phase is a one-line change here and a `when` arm anywhere
 * it's surfaced.
 *
 * **Why a `Throwable`** (US-022 / SF-3.4): the validator returns `Result<FunctionCall>`,
 * and Kotlin's `Result.failure` accepts only `Throwable`. Making the whole
 * hierarchy extend `Throwable` lets callers use the standard `Result` API
 * (`.fold`, `.exceptionOrNull()`, etc.) without an intermediate wrapper.
 * `CurroError` instances are NEVER thrown — only carried inside `Result.failure`.
 */
sealed class CurroError : Throwable() {
    // ── Speech-to-text ────────────────────────────────────────────────────────

    /** `RecognitionListener.onError(ERROR_NO_MATCH)` or `onResults` with an empty result list. */
    data object SttNoMatch : CurroError()

    /** `RecognitionListener.onError(ERROR_SPEECH_TIMEOUT)`. */
    data object SttTimeout : CurroError()

    /** Any other `RecognitionListener.onError(code)`. [code] is the raw `SpeechRecognizer.ERROR_*`. */
    data class SttError(val code: Int) : CurroError()

    /**
     * `SpeechRecognizer.isOnDeviceRecognitionAvailable` returned false, OR `onError`
     * reported `ERROR_LANGUAGE_NOT_SUPPORTED` / `ERROR_LANGUAGE_UNAVAILABLE`.
     * Means the Spanish offline voice pack is not installed on this device.
     */
    data object SttVoicePackMissing : CurroError()

    // ── Text-to-speech ────────────────────────────────────────────────────────

    /** `TextToSpeech.setLanguage` returned `LANG_MISSING_DATA` or `LANG_NOT_SUPPORTED`. */
    data object TtsLanguageMissing : CurroError()

    /** `UtteranceProgressListener.onError(_, code)` — a synthesis-layer failure. */
    data class TtsError(val code: Int) : CurroError()

    // ── Permissions ───────────────────────────────────────────────────────────

    /**
     * Any runtime permission was denied. The caller decides which permission
     * (RECORD_AUDIO, CALL_PHONE, READ_CONTACTS, …) by context; the screen distinguishes
     * via its own copy.
     */
    data object PermissionDenied : CurroError()

    // ── Decision layer (US-020 + US-022) ──────────────────────────────────────

    /**
     * FunctionGemma is not loaded yet (the .task is missing on disk, or the
     * warm-up service hasn't finished, or HyperOS killed the warm process).
     * The caller speaks `copy_models_not_ready` and the engine kicks `warmUp()`
     * as a side effect so the next call may succeed.
     */
    data object ModelCold : CurroError()

    /**
     * The raw model output failed JSON-schema validation against the current
     * phase's catalog (malformed JSON, missing/typed-wrong param, out-of-range
     * confidence, etc.). **Spec flow 7: no automatic retry.**
     */
    data object InvalidFunctionCall : CurroError()

    /**
     * The raw model output parsed as well-shaped JSON, but the named action is
     * not in the current phase's catalog (e.g. `translate` in Phase 1). The
     * separate variant lets logs distinguish "model invented a function" from
     * "model produced garbage JSON".
     */
    data class UnknownFunction(val name: String) : CurroError()

    /** Native OOM during a MediaPipe inference call. */
    data object OutOfMemory : CurroError()

    // ── Handler layer (US-025 / SF-4.1) ──────────────────────────────────────

    /** A handler threw despite the never-throw contract — surfaced via dispatcher's safety net. */
    data class HandlerCrash(val functionName: String, val throwable: Throwable) : CurroError()

    // ── Calculate handler (US-028 / SF-4.4) ───────────────────────────────────

    /**
     * A calculation failure. [expression] is the raw param (logged safely; no PII — it's an
     * arithmetic expression). [reason] is one of:
     *   "empty"    — expression param was blank.
     *   "parse"    — tokenizer / words-to-int couldn't resolve; out-of-scope inputs ("billones") hit this.
     *   "div_zero" — division by zero.
     *   "overflow" — intermediate or final result > 9_999_999 or < 0.
     */
    data class Calculation(
        val expression: String,
        val reason: String,
    ) : CurroError()

    // ── Open-app handler (US-027 / SF-4.3) ────────────────────────────────────

    /** Multiple installed apps matched the query and the handler couldn't pick one. */
    data class AmbiguousApp(val matches: List<LaunchableApp>) : CurroError()

    /** No installed app matched the query. [query] is the raw input for the log. */
    data class AppNotFound(val query: String) : CurroError()

    // ── Notification access (US-030 / SF-4.6) ─────────────────────────────────

    /**
     * The handler ran but `BIND_NOTIFICATION_LISTENER_SERVICE` is not granted.
     * Speech: [com.curro.app.R.string.copy_perm_missing_notifs].
     */
    data object NotificationAccessMissing : CurroError()

    // ── Contacts + telephony (US-033 / SF-4.9, US-034 / SF-4.10) ─────────────

    /**
     * A name resolved to multiple contacts. Phase 4: the handler speaks
     * [com.curro.app.R.string.copy_contact_ambiguous_phase4]. Phase 6: replaces
     * this path with the real picker overlay.
     */
    data class AmbiguousContact(val matches: List<Contact>) : CurroError()

    /**
     * No contact matched the query. [query] is the raw spoken name (safe to log —
     * it's what FunctionGemma extracted, not a real contact's identity in the address book).
     */
    data class ContactNotFound(val query: String) : CurroError()

    /**
     * `READ_CONTACTS` was not granted when the handler tried to resolve a name.
     * Speech: [com.curro.app.R.string.copy_perm_missing_contacts].
     */
    data object ReadContactsPermissionMissing : CurroError()
}
