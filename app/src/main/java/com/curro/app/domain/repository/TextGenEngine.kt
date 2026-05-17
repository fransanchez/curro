package com.curro.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * On-device natural-language generation engine for Curro (US-061 / SF-9.2).
 *
 * Backed by Gemma 3n E2B (int4, ~2 GB active) via MediaPipe `LlmInference`,
 * loaded **on demand only** (NEVER speculatively at startup — see the
 * `on-device-llm` skill Rule 3 and `docs/architecture/gemma-3n-decision.md`).
 *
 * Lifecycle:
 *  - [load] is idempotent. First call triggers MediaPipe initialisation
 *    (~3–6 s typical on the Samsung A53 6 GB / Redmi 15 8 GB). Subsequent
 *    calls return `Result.success(Unit)` immediately if already loaded.
 *  - [generate] auto-loads if not ready, then runs inference.
 *  - [unload] releases the LLM instance; called by:
 *      * `CurroApp.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` (memory pressure).
 *      * Internally by [generate] when inference throws `OutOfMemoryError`.
 *
 * Failure modes (all surfaced via `Result.failure(CurroError)`):
 *  - `CurroError.ModelCold` — weights absent or load failed (non-OOM).
 *  - `CurroError.OutOfMemory` — native OOM during load or generate (with
 *    auto-unload on the generate path so the memory is actually released).
 *  - `CurroError.InvalidFunctionCall` — non-OOM exception during generate
 *    (treated as "engine misbehaved"; the caller decides whether to fall
 *    back).
 *
 * Concurrency: implementations MUST serialise load / unload / generate via
 * an internal mutex. `LlmInference` is not documented thread-safe.
 *
 * Thread: [generate] MUST run inference off the main thread (MediaPipe's
 * `generateResponse` is blocking).
 *
 * Implementations: `data/ml/Gemma3nEngine` (production); a `FakeTextGenEngine`
 * fixture lands in US-062 for handler/coordinator tests.
 */
interface TextGenEngine {
    /**
     * Whether the LLM instance is currently resident in memory.
     *
     * Callers (e.g. `ReadAllUnreadWhatsAppHandler` in US-062) read this BEFORE
     * calling [generate] to decide whether to surface the `copy_cold_model`
     * ("Dame un segundo.") line.
     */
    val isReady: StateFlow<Boolean>

    /**
     * Load the model into memory. Idempotent: returns `Result.success(Unit)`
     * if already loaded.
     *
     * @return `Result.success(Unit)` on success;
     *         `Result.failure(CurroError.ModelCold)` if weights are absent or
     *         MediaPipe initialisation fails;
     *         `Result.failure(CurroError.OutOfMemory)` if MediaPipe throws
     *         `OutOfMemoryError` during creation.
     */
    suspend fun load(): Result<Unit>

    /**
     * Generate text for [prompt]. Auto-loads via [load] if not ready; if the
     * auto-load fails, propagates that failure.
     *
     * @return `Result.success(rawOutput)` on success;
     *         `Result.failure(CurroError.ModelCold)` if not loaded and the
     *         auto-load failed (non-OOM);
     *         `Result.failure(CurroError.OutOfMemory)` if OOM during load or
     *         generate (with auto-unload on the generate path so the memory
     *         is actually released);
     *         `Result.failure(CurroError.InvalidFunctionCall)` for any other
     *         non-OOM exception during inference.
     */
    suspend fun generate(prompt: String): Result<String>

    /**
     * Release the LLM instance and free its memory. Idempotent.
     *
     * Called by `CurroApp.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` on system
     * memory pressure. Also called internally by [generate] when inference
     * OOMs.
     */
    suspend fun unload()
}
