package com.curro.app.domain.repository

/**
 * Read-only view of the on-device function-call engine's runtime health (US-059 / SF-8.10).
 *
 * Implemented by `data/ml/FunctionGemmaEngine`, bound in `di/MlModule`.
 * Consumed by `DiagnosticsViewModel` to populate the "Modelo" section.
 *
 * All reads are O(1) — backed by `@Volatile var` members written in the warm-up and
 * inference paths of [com.curro.app.data.ml.FunctionGemmaEngine]. The `suspend` modifier
 * on the latency accessors is an affordance for future storage-backed implementations;
 * the current impl returns immediately.
 */
interface EngineMetrics {
    /** True iff the engine's LLM instance is resident in memory and ready to infer. */
    fun isReady(): Boolean

    /** Display name for the model, e.g. `"FunctionGemma270M"`. */
    fun modelName(): String

    /** Wall-clock milliseconds for the last successful warm-up, or null if never warmed up this session. */
    suspend fun lastWarmUpLatencyMs(): Long?

    /** Wall-clock milliseconds for the last successful [decide] call, or null if no inference yet this session. */
    suspend fun lastInferenceLatencyMs(): Long?
}
