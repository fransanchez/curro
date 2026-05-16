package com.curro.app.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.ConfirmationVoice
import com.curro.app.domain.repository.SttClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SttClient] backed by the Android framework's [SpeechRecognizer]
 * (SF-2.1 / US-015).
 *
 * **Offline Spanish.** `EXTRA_PREFER_OFFLINE = true`, `EXTRA_LANGUAGE = "es-ES"`. The
 * device must have the Spanish voice pack installed; absence surfaces as
 * [CurroError.SttVoicePackMissing] (see [hasOfflineSpanish] and the error mapping below).
 *
 * **CRITICAL — main-thread discipline (US-015 §11).** Two prior incidents (796b5f4,
 * b77d789) crashed on launch because a `callbackFlow` body that invoked main-thread-bound
 * Android APIs ran on `Dispatchers.IO` via a misplaced `flowOn`. `SpeechRecognizer` has
 * the same risk profile:
 * - `SpeechRecognizer.createSpeechRecognizer(context)` must be called on Main.
 * - `startListening`, `stopListening`, `cancel`, `destroy` must be called on Main.
 *
 * Therefore the `callbackFlow { … }` body below is force-marshalled to Main via the
 * terminal `flowOn(Dispatchers.Main.immediate)`. There is no other `flowOn` in the chain
 * and no `withContext(io)` wrapping any [SpeechRecognizer] method.
 *
 * @see SttClient
 */
@Singleton
internal class SystemSttClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SttClient {
        /**
         * The currently-active recogniser, if any. Mutated only from the `callbackFlow`
         * body (Main-thread); read from [cancel] which marshals via the framework
         * methods themselves (thread-safe in practice on Android 12+, but kept `@Volatile`
         * for memory-visibility belt-and-braces).
         */
        @Volatile private var current: SpeechRecognizer? = null

        // CRITICAL: SpeechRecognizer is main-thread-bound — see US-015 §11.
        override fun listen(): Flow<SttClient.Event> =
            callbackFlow {
                val sr = SpeechRecognizer.createSpeechRecognizer(context)
                current = sr

                val listener =
                    object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit

                        override fun onBeginningOfSpeech() = Unit

                        override fun onRmsChanged(rmsdB: Float) = Unit

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() = Unit

                        override fun onPartialResults(partialResults: Bundle?) {
                            val text =
                                partialResults
                                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull()
                                    .orEmpty()
                            if (text.isNotEmpty()) {
                                trySend(SttClient.Event.Partial(text))
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val text =
                                results
                                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull()
                                    .orEmpty()
                            if (text.isEmpty()) {
                                trySend(SttClient.Event.Failed(CurroError.SttNoMatch))
                            } else {
                                trySend(SttClient.Event.Final(text))
                            }
                            close()
                        }

                        override fun onError(error: Int) {
                            trySend(SttClient.Event.Failed(error.toCurroError()))
                            close()
                        }

                        override fun onEvent(
                            eventType: Int,
                            params: Bundle?,
                        ) = Unit
                    }

                sr.setRecognitionListener(listener)

                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LOCALE_ES_ES)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LOCALE_ES_ES)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    }

                sr.startListening(intent)

                awaitClose {
                    sr.cancel()
                    sr.destroy()
                    if (current === sr) current = null
                }
            }.flowOn(Dispatchers.Main.immediate)

        override fun cancel() {
            current?.cancel()
        }

        /**
         * SF-6.2 (US-042) — constrained yes/no confirmation pass. Same main-
         * thread discipline as [listen]; the result is post-processed into a
         * [ConfirmationVoice] via [mapToConfirmationVoice].
         */
        override fun listenForConfirmation(): Flow<ConfirmationVoice> =
            callbackFlow {
                val sr = SpeechRecognizer.createSpeechRecognizer(context)
                current = sr

                val listener =
                    object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit

                        override fun onBeginningOfSpeech() = Unit

                        override fun onRmsChanged(rmsdB: Float) = Unit

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() = Unit

                        override fun onPartialResults(partialResults: Bundle?) = Unit

                        override fun onResults(results: Bundle?) {
                            val text =
                                results
                                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull()
                                    .orEmpty()
                            if (text.isEmpty()) {
                                trySend(ConfirmationVoice.Failed(CurroError.SttNoMatch))
                            } else {
                                trySend(mapToConfirmationVoice(text))
                            }
                            close()
                        }

                        override fun onError(error: Int) {
                            trySend(ConfirmationVoice.Failed(error.toCurroError()))
                            close()
                        }

                        override fun onEvent(
                            eventType: Int,
                            params: Bundle?,
                        ) = Unit
                    }

                sr.setRecognitionListener(listener)

                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        // WEB_SEARCH biases the recogniser to short utterances — preferred for
                        // yes/no over FREE_FORM (which optimises for long sentences).
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH,
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LOCALE_ES_ES)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LOCALE_ES_ES)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    }

                sr.startListening(intent)

                awaitClose {
                    sr.cancel()
                    sr.destroy()
                    if (current === sr) current = null
                }
            }.flowOn(Dispatchers.Main.immediate)

        override suspend fun hasOfflineSpanish(): Boolean {
            // isOnDeviceRecognitionAvailable was added in API 31 (minSdk = 31 → always available).
            // Belt-and-braces guard kept for clarity.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
            return SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        }

        private companion object {
            const val LOCALE_ES_ES = "es-ES"
        }
    }

/**
 * Maps a [SpeechRecognizer] `ERROR_*` code to the corresponding [CurroError].
 *
 * Mapping table:
 * | Framework code                            | CurroError                  |
 * |-------------------------------------------|-----------------------------|
 * | ERROR_NO_MATCH                            | SttNoMatch                  |
 * | ERROR_SPEECH_TIMEOUT                      | SttTimeout                  |
 * | ERROR_INSUFFICIENT_PERMISSIONS            | PermissionDenied            |
 * | ERROR_LANGUAGE_NOT_SUPPORTED              | SttVoicePackMissing         |
 * | ERROR_LANGUAGE_UNAVAILABLE                | SttVoicePackMissing         |
 * | anything else (NETWORK / AUDIO / …)       | SttError(code)              |
 */
internal fun Int.toCurroError(): CurroError =
    when (this) {
        SpeechRecognizer.ERROR_NO_MATCH -> CurroError.SttNoMatch
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> CurroError.SttTimeout
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> CurroError.PermissionDenied
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> CurroError.SttVoicePackMissing
        else -> CurroError.SttError(this)
    }

/** SF-6.2 (US-042) — pinned Yes / No vocabularies. Lower-case, accent-stripped. */
private val YES_VOCAB =
    setOf(
        "si",
        "vale",
        "claro",
        "dale",
        "venga",
        "okay",
        "ok",
    )
private val NO_VOCAB =
    setOf(
        "no",
        "cancela",
        "cancelar",
        "dejalo",
        "no llames",
        "no quiero",
    )

/**
 * SF-6.2 (US-042) — post-hoc vocabulary match.
 *
 * Normalises [text] (lowercase + strip diacritics), then:
 *   - Yes if it equals any [YES_VOCAB] entry or starts with `"si "` (so "sí
 *     llama" → Yes).
 *   - No if it equals any [NO_VOCAB] entry or starts with `"no "` (so "no
 *     llames" / "no quiero" → No even when not exact).
 *   - Otherwise → [ConfirmationVoice.Other] with the original (unmodified)
 *     text so the coordinator's telemetry/logging can see what the user
 *     actually said.
 */
internal fun mapToConfirmationVoice(text: String): ConfirmationVoice {
    val normalised = normaliseEs(text)
    return when {
        normalised.isEmpty() -> ConfirmationVoice.Failed(CurroError.SttNoMatch)
        normalised in YES_VOCAB || normalised.startsWith("si ") -> ConfirmationVoice.Yes
        normalised in NO_VOCAB || normalised.startsWith("no ") -> ConfirmationVoice.No
        else -> ConfirmationVoice.Other(text)
    }
}

private fun normaliseEs(text: String): String {
    val nfd = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
    return nfd.replace(DIACRITIC_REGEX, "")
}

private val DIACRITIC_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
