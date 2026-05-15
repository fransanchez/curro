package com.curro.app.data.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.PromptContext
import com.curro.app.domain.repository.FunctionCallEngine
import com.curro.app.domain.repository.TelemetrySink
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaPipe-backed implementation of [FunctionCallEngine] (US-020 / SF-3.2).
 *
 * Wraps [LlmInference] to run FunctionGemma 270M (int8, ~288 MB) entirely on
 * device. The model file is resolved by [ModelFiles] (US-019); when the .task
 * isn't present (CI, first install before side-load) `warmUp` is a no-op and
 * [decide] returns [CurroError.ModelCold].
 *
 * **NOT JVM-testable.** The MediaPipe runtime needs native binaries (.so) that
 * are absent on the JVM unit-test classpath. Real-engine verification is the
 * on-device gate in US-024 (smoke loop on the Redmi 15):
 *   - `Log.i("Curro/Llm", "warm-up took <ms>ms")` appears in logcat once
 *   - `Log.i("Curro/Llm", "decide latency: <ms>ms")` shows < 500 ms warm
 *   - 10 consecutive "qué hora es" runs all under 500 ms
 *
 * For JVM tests, inject `FakeFunctionCallEngine`.
 *
 * **Threading.** [LlmInference] is not documented thread-safe; a single-flight
 * [Mutex] (`callMutex`) serialises [decide] calls. The blocking
 * `generateResponse` runs inside `withContext(io)` so the main thread is never
 * blocked. The Phase-1 launcher is the only caller, so contention is unlikely
 * — the mutex is insurance.
 *
 * **PII boundary.** Latency + model name are safe to log/telemeter. The
 * prompt, the raw output, the utterance: never. Verified by inspection of
 * every log/telemetry call below — none reference [PromptContext] or the
 * `raw` payload.
 */
@Singleton
class FunctionGemmaEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val promptBuilder: FunctionCallPromptBuilder,
        @IoDispatcher private val io: CoroutineDispatcher,
        private val telemetry: TelemetrySink,
    ) : FunctionCallEngine {
        /** Held resident across the process lifetime; null until [warmUp] succeeds. */
        @Volatile
        private var llm: LlmInference? = null

        /**
         * Single-flight guard. MediaPipe's [LlmInference] is not documented thread-safe;
         * the cheap mutex prevents a second caller racing into [LlmInference.generateResponse]
         * while a first is in flight.
         */
        private val callMutex = Mutex()

        override fun warmUp() {
            if (llm != null) return
            if (!ModelFiles.isFunctionGemmaAvailable()) {
                Log.i(TAG, "warm-up skipped — weights not present at ${ModelFiles.functionGemma().absolutePath}")
                return
            }
            val started = SystemClock.elapsedRealtime()
            runCatching {
                val opts =
                    LlmInferenceOptions
                        .builder()
                        .setModelPath(ModelFiles.functionGemma().absolutePath)
                        .setMaxTokens(MAX_TOKENS)
                        .setTemperature(TEMPERATURE)
                        .setTopK(TOP_K)
                        .build()
                LlmInference.createFromOptions(context, opts)
            }.onSuccess { instance ->
                llm = instance
                val ms = SystemClock.elapsedRealtime() - started
                Log.i(TAG, "warm-up took ${ms}ms")
                telemetry.event(
                    "model_loaded",
                    mapOf(
                        "model" to MODEL_NAME,
                        "load_ms" to ms.toInt(),
                        "cold_start" to true,
                    ),
                )
            }.onFailure { t ->
                Log.w(TAG, "warm-up failed: ${t.javaClass.simpleName}")
            }
        }

        override suspend fun decide(
            utterance: String,
            ctx: PromptContext,
        ): Result<String> {
            val engine = llm
            if (engine == null) {
                warmUp() // best-effort kick — next call may succeed
                return Result.failure(CurroError.ModelCold)
            }
            return withContext(io) {
                callMutex.withLock {
                    val prompt = promptBuilder.build(utterance, ctx)
                    val started = SystemClock.elapsedRealtime()
                    try {
                        val raw = engine.generateResponse(prompt) // blocking
                        val ms = SystemClock.elapsedRealtime() - started
                        Log.i(TAG, "decide latency: ${ms}ms")
                        // PII boundary: `prompt`, `raw`, `utterance` are NEVER logged or
                        // telemetry-sent. Only the latency and the model name are safe.
                        Result.success(raw)
                    } catch (_: OutOfMemoryError) {
                        Log.w(TAG, "OOM during decide")
                        Result.failure<String>(CurroError.OutOfMemory)
                    } catch (
                        @Suppress("TooGenericExceptionCaught") t: Throwable,
                    ) {
                        // MediaPipe's native code can throw a variety of exception types
                        // (RuntimeException, IllegalStateException, native-bridged ones).
                        // We treat any non-OOM throwable as an "engine misbehaved" signal
                        // and let the validator-layer fallback handle the user message —
                        // catching everything is the explicit intent here.
                        Log.w(TAG, "decide failed: ${t.javaClass.simpleName}")
                        Result.failure<String>(CurroError.InvalidFunctionCall)
                    }
                }
            }
        }

        override fun isReady(): Boolean = llm != null

        private companion object {
            const val TAG = "Curro/Llm"
            const val MODEL_NAME = "function_gemma_270m"
            const val MAX_TOKENS = 256
            const val TEMPERATURE = 0.1f
            const val TOP_K = 1
        }
    }
