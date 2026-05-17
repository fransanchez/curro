package com.curro.app.data.ml

import android.os.SystemClock
import android.util.Log
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.EngineMetrics
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TextGenEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaPipe-backed implementation of [TextGenEngine] (US-061 / SF-9.2).
 *
 * Wraps [LlmInference] running Gemma 3n E2B (int4, ~2 GB active). The model
 * file is resolved by [ModelFiles.gemma3n]; if absent, [load] returns
 * `Result.failure(CurroError.ModelCold)` and the rest of the app keeps
 * working with FunctionGemma only (see [FunctionGemmaEngine]).
 *
 * **NOT JVM-testable directly via MediaPipe.** Use `Gemma3nEngineTest` via
 * the [LlmInferenceFactory] seam: tests substitute a fake factory that
 * returns a `mockk<LlmInference>()` without touching the MediaPipe native
 * runtime. Real-engine verification is `Gemma3nSmokeTest` (instrumented,
 * US-060/US-061).
 *
 * **Concurrency**. A single [Mutex] ([stateMutex]) serialises load / unload /
 * generate. The blocking `generateResponse` runs inside `withContext(io)`;
 * the main thread is never blocked.
 *
 * **PII boundary** (same rule as [FunctionGemmaEngine]): latency + model name
 * are safe to log/telemeter. The prompt, the raw output, the message bodies,
 * the sender names: NEVER. Verified by inspection of every `Log.*` and
 * `telemetry.*` call below — none reference [prompt] or the `raw` payload.
 *
 * **Lifecycle integration**. `CurroApp.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)`
 * calls [unload]. [FunctionGemmaEngine] is NOT unloaded under memory pressure
 * — it stays warm via [com.curro.app.service.ModelWarmupService] (US-023) so
 * the app's function-calling brain keeps working even when Gemma 3n is gone.
 *
 * **`@Suppress("TooManyFunctions")`**: the class implements both [TextGenEngine]
 * and [EngineMetrics], plus the no-lock helpers + 3 Gemma-3n metric accessors.
 * Splitting further would obscure the lifecycle the mutex is protecting.
 */
@Suppress("TooManyFunctions")
@Singleton
class Gemma3nEngine
    @Inject
    constructor(
        private val modelFiles: ModelFiles,
        private val factory: LlmInferenceFactory,
        @IoDispatcher private val io: CoroutineDispatcher,
        private val telemetry: TelemetrySink,
    ) : TextGenEngine,
        EngineMetrics {
        private val stateMutex = Mutex()

        @Volatile private var llm: LlmSession? = null

        private val _isReady = MutableStateFlow(false)
        override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

        /** Wall-clock ms of the most recent successful load (null until first load). */
        @Volatile private var lastLoadMs: Long? = null

        /** Wall-clock ms of the most recent successful generate (null until first inference). */
        @Volatile private var lastGenerateMs: Long? = null

        override suspend fun load(): Result<Unit> = stateMutex.withLock { loadNoLock() }

        // Guard-style early returns are the right idiom for a load preflight:
        // (1) already loaded, (2) weights missing, (3) the success/failure split.
        @Suppress("ReturnCount")
        private suspend fun loadNoLock(): Result<Unit> {
            if (llm != null) return Result.success(Unit)
            if (!modelFiles.isGemma3nAvailable()) {
                Log.i(TAG, "load skipped — weights not present at ${modelFiles.gemma3n().absolutePath}")
                return Result.failure(CurroError.ModelCold)
            }
            val started = SystemClock.elapsedRealtime()
            return withContext(io) {
                runCatching {
                    factory.create(
                        modelPath = modelFiles.gemma3n().absolutePath,
                        maxTokens = MAX_TOKENS,
                        topK = TOP_K,
                        temperature = TEMPERATURE,
                    )
                }.fold(
                    onSuccess = { instance ->
                        llm = instance
                        val ms = SystemClock.elapsedRealtime() - started
                        lastLoadMs = ms
                        _isReady.value = true
                        Log.i(TAG, "load took ${ms}ms")
                        telemetry.event(
                            "model_loaded",
                            mapOf(
                                "model" to MODEL_NAME,
                                "load_ms" to ms.toInt(),
                                "cold_start" to true,
                            ),
                        )
                        Result.success(Unit)
                    },
                    onFailure = { t ->
                        val mapped =
                            when (t) {
                                is OutOfMemoryError -> CurroError.OutOfMemory
                                else -> CurroError.ModelCold
                            }
                        Log.w(TAG, "load failed: ${t.javaClass.simpleName} → $mapped")
                        Result.failure<Unit>(mapped)
                    },
                )
            }
        }

        override suspend fun generate(prompt: String): Result<String> =
            stateMutex.withLock {
                if (llm == null) {
                    val loadResult = loadNoLock()
                    val loadErr = loadResult.exceptionOrNull()
                    if (loadErr != null) {
                        return@withLock Result.failure(loadErr)
                    }
                }
                val engine = llm ?: return@withLock Result.failure(CurroError.ModelCold)
                val started = SystemClock.elapsedRealtime()
                withContext(io) {
                    try {
                        val raw = engine.generateResponse(prompt) // blocking
                        val ms = SystemClock.elapsedRealtime() - started
                        lastGenerateMs = ms
                        Log.i(TAG, "generate latency: ${ms}ms")
                        // PII boundary: `prompt`, `raw`, sender names are NEVER logged.
                        Result.success(raw)
                    } catch (_: OutOfMemoryError) {
                        Log.w(TAG, "OOM during generate; auto-unloading")
                        unloadNoLock()
                        Result.failure<String>(CurroError.OutOfMemory)
                    } catch (
                        @Suppress("TooGenericExceptionCaught") t: Throwable,
                    ) {
                        // Mirror FunctionGemmaEngine's defensive catch-all: any non-OOM
                        // throwable surfaces as InvalidFunctionCall so the caller can
                        // fall back without a generic Throwable branch.
                        Log.w(TAG, "generate failed: ${t.javaClass.simpleName}")
                        Result.failure<String>(CurroError.InvalidFunctionCall)
                    }
                }
            }

        override suspend fun unload() = stateMutex.withLock { unloadNoLock() }

        private fun unloadNoLock() {
            llm?.close()
            llm = null
            _isReady.value = false
            Log.i(TAG, "unloaded")
        }

        // ── EngineMetrics — FunctionGemma rows ────────────────────────────────
        //
        // FunctionGemmaEngine remains the canonical EngineMetrics binding
        // (DiagnosticsViewModel reads it). These four methods stay defaulted
        // here: they would always report Gemma 3n's status if read, which is
        // confusing when the diagnostics screen shows FunctionGemma. The
        // Gemma3n-specific surface is the three methods below.

        override fun isReady(): Boolean = _isReady.value

        override fun modelName(): String = MODEL_NAME_DISPLAY

        override suspend fun lastWarmUpLatencyMs(): Long? = lastLoadMs

        override suspend fun lastInferenceLatencyMs(): Long? = lastGenerateMs

        // ── EngineMetrics — Gemma 3n additive methods (US-061) ────────────────

        override fun gemma3nIsReady(): Boolean = _isReady.value

        override suspend fun gemma3nLastLoadLatencyMs(): Long? = lastLoadMs

        override suspend fun gemma3nLastGenerateLatencyMs(): Long? = lastGenerateMs

        private companion object {
            const val TAG = "Curro/Gemma3n"
            const val MODEL_NAME = "gemma3n_e2b"
            const val MODEL_NAME_DISPLAY = "Gemma3nE2B"

            // Decoding params — pinned in US-061.
            // 2048 tokens is enough for a ~3-sender summary; tighten if memory pressure surfaces.
            const val MAX_TOKENS = 2048

            // 40 — NL generation wants a broader sampling pool than function-calling.
            // FunctionGemma uses TOP_K = 1 (deterministic JSON).
            const val TOP_K = 40

            // 0.7 — NL generation wants sampling variety (vs FunctionGemma's 0.1 for deterministic JSON).
            const val TEMPERATURE = 0.7f
        }
    }
