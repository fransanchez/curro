package com.curro.app.domain.repository

import com.curro.app.domain.model.PromptContext

/**
 * On-device decision layer (spec §4.3).
 *
 * Takes an utterance + minimal context and returns the **raw model output** as
 * a string. The string is validated against the function catalog by
 * [com.curro.app.data.ml.FunctionCallValidator] (US-022) — keeping the
 * validation out of this interface lets every test fake the engine with a
 * canned string, lets the validator's failure modes ship independently, and
 * lets future engines (e.g. a constrained-decoding alternative) plug in
 * without touching the validator.
 *
 * Concrete implementations: [com.curro.app.data.ml.FunctionGemmaEngine]
 * (MediaPipe-backed, production); `FakeFunctionCallEngine` (tests).
 *
 * Lifecycle: [warmUp] loads the model into memory; [isReady] reflects whether
 * the model is currently warm. Implementations may choose to lazy-warm on the
 * first [decide] call, but in production the [com.curro.app.service.ModelWarmupService]
 * (US-023) calls [warmUp] from `CurroApp.onCreate` so the first user-facing
 * press is already under the latency target.
 */
interface FunctionCallEngine {
    /**
     * Maps an utterance to a raw model output string (which the caller validates).
     *
     * @return [Result.success] with the raw model output, or [Result.failure] with:
     *   - [com.curro.app.domain.model.CurroError.ModelCold] — engine not warm; the
     *     impl also kicks [warmUp] as a side effect for next time.
     *   - [com.curro.app.domain.model.CurroError.OutOfMemory] — native OOM during
     *     inference.
     *   - [com.curro.app.domain.model.CurroError.InvalidFunctionCall] — unexpected
     *     native exception during inference (best-effort fallback so callers don't
     *     need a generic `Throwable` branch).
     *
     * Production implementations MUST run the actual inference off the main
     * thread (MediaPipe's `generateResponse` is blocking).
     */
    suspend fun decide(
        utterance: String,
        ctx: PromptContext,
    ): Result<String>

    /** Idempotent. Loads the model if not already loaded. Safe to call from any thread. */
    fun warmUp()

    /** True iff the model is currently loaded. */
    fun isReady(): Boolean
}
