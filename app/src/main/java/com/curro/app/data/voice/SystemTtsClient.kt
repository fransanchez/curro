package com.curro.app.data.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.TtsClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Production [TtsClient] backed by the Android framework's [TextToSpeech] (SF-2.2 /
 * US-016).
 *
 * **Spanish, slowed by ~12 %.** [SPEECH_RATE] = 0.88f sits mid-band of spec §14's
 * "10–15 % slower" decision. The male-voice preference is best-effort: the system default
 * for the locale is used if no male `es` voice is present.
 *
 * **Lifecycle.** Constructed once at injection time (`@Singleton`); the underlying
 * [TextToSpeech] is created in `init` via [TextToSpeechFactory.create]. There is no
 * explicit `shutdown()` — process kill releases the native AudioService binding.
 *
 * **Threading.** [TextToSpeech] construction is safe from any thread on modern Android;
 * the `OnInitListener` callback is delivered on Main. `tts.speak`, `tts.stop`,
 * `setLanguage`, `setVoice`, `setSpeechRate` are thread-safe.
 * [UtteranceProgressListener] callbacks are delivered on a synthesis thread (NOT Main) —
 * the suspendCancellableCoroutine in [speak] uses `cont.resume` which is thread-safe; the
 * listener does not touch UI state.
 *
 * **Barge-in.** [speak] uses [suspendCancellableCoroutine]; cancellation of the calling
 * coroutine fires `invokeOnCancellation { tts.stop() }`, which makes the framework emit
 * `onStop`, which resumes the continuation with [TtsClient.SpeakResult.Cancelled].
 */
@Singleton
internal class SystemTtsClient
    @Inject
    constructor(
        factory: TextToSpeechFactory,
    ) : TtsClient {
        private val initDeferred = CompletableDeferred<TtsInitResult>()

        @Volatile private var tts: TextToSpeech? = null

        init {
            // The OnInitListener runs asynchronously and reads `tts` via the property
            // reference, so the listener sees the assigned value even when the framework
            // delivers the callback after factory.create() returns.
            tts = factory.create { status -> onTtsInit(status) }
        }

        private fun onTtsInit(status: Int) {
            val result = configureForSpanish(status)
            initDeferred.complete(result)
        }

        /**
         * Validates the init [status], configures the framework instance for offline
         * Spanish at the slowed rate, and returns the appropriate [TtsInitResult]. Pure
         * function over [tts] — no `return` mid-method to keep detekt's ReturnCount happy
         * and the flow linear.
         */
        private fun configureForSpanish(status: Int): TtsInitResult {
            // Synchronous callback before factory.create() returned — extremely rare in
            // practice (the framework dispatches via the main looper), but defensive:
            // complete with failure rather than NPE.
            val ttsRef = tts
            val langResult = ttsRef?.setLanguage(Locale("es", "ES"))
            val failed =
                status != TextToSpeech.SUCCESS ||
                    ttsRef == null ||
                    langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
            return if (failed) {
                TtsInitResult.Failed(CurroError.TtsLanguageMissing)
            } else {
                ttsRef.setSpeechRate(SPEECH_RATE)
                ttsRef.setPitch(PITCH)
                preferMaleSpanishVoice(ttsRef)
                TtsInitResult.Ready
            }
        }

        /**
         * Best-effort: select a male Spanish voice when the device exposes one. Falls
         * through to the locale default set in [onTtsInit] if no match is found.
         */
        private fun preferMaleSpanishVoice(ttsRef: TextToSpeech) {
            val voices = ttsRef.voices ?: return
            val match =
                voices.firstOrNull { v ->
                    v.locale.language == "es" &&
                        v.name.contains("male", ignoreCase = true) &&
                        !v.name.contains("female", ignoreCase = true)
                }
            if (match != null) ttsRef.voice = match
        }

        override suspend fun speak(
            text: String,
            utteranceId: String,
        ): TtsClient.SpeakResult {
            require(text.isNotEmpty()) { "TtsClient.speak called with empty text" }
            val readyTts = awaitReady() ?: return TtsClient.SpeakResult.Failed(CurroError.TtsLanguageMissing)
            return suspendUntilTerminal(readyTts, text, utteranceId)
        }

        /**
         * Awaits initialisation and returns the ready [TextToSpeech], or null if init
         * failed or the underlying instance is no longer present.
         */
        private suspend fun awaitReady(): TextToSpeech? {
            val init = initDeferred.await()
            if (init is TtsInitResult.Failed) return null
            return tts
        }

        /**
         * Wraps the framework's [UtteranceProgressListener] in a single
         * [suspendCancellableCoroutine] that resolves to the matching [TtsClient.SpeakResult].
         */
        private suspend fun suspendUntilTerminal(
            current: TextToSpeech,
            text: String,
            utteranceId: String,
        ): TtsClient.SpeakResult =
            suspendCancellableCoroutine { cont ->
                current.setOnUtteranceProgressListener(progressListener(utteranceId, cont))
                current.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                cont.invokeOnCancellation { current.stop() }
            }

        private fun progressListener(
            expectedId: String,
            cont: kotlinx.coroutines.CancellableContinuation<TtsClient.SpeakResult>,
        ): UtteranceProgressListener =
            object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    if (id == expectedId && cont.isActive) {
                        cont.resume(TtsClient.SpeakResult.Completed)
                    }
                }

                @Deprecated(
                    "framework keeps this deprecated overload alive",
                    ReplaceWith("onError(utteranceId, errorCode)"),
                )
                override fun onError(utteranceId: String?) {
                    if (utteranceId == expectedId && cont.isActive) {
                        cont.resume(TtsClient.SpeakResult.Failed(CurroError.TtsError(TTS_ERROR_UNKNOWN)))
                    }
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    if (utteranceId == expectedId && cont.isActive) {
                        cont.resume(TtsClient.SpeakResult.Failed(CurroError.TtsError(errorCode)))
                    }
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean,
                ) {
                    if (utteranceId == expectedId && cont.isActive) {
                        cont.resume(TtsClient.SpeakResult.Cancelled)
                    }
                }
            }

        override fun stop() {
            tts?.stop()
        }

        override fun isSpeaking(): Boolean = tts?.isSpeaking == true

        private sealed interface TtsInitResult {
            data object Ready : TtsInitResult

            data class Failed(val error: CurroError) : TtsInitResult
        }

        private companion object {
            /** ~12 % slower than default (1.0). Spec §14 says "10–15 % slower". 0.88 sits mid-band. */
            const val SPEECH_RATE: Float = 0.88f

            /** Default pitch — neutral. Phase 8 may expose this in the config menu. */
            const val PITCH: Float = 1.0f

            /** Sentinel for the deprecated `onError(id)` overload that carries no code. */
            const val TTS_ERROR_UNKNOWN: Int = -1
        }
    }
