package com.curro.app.domain.repository

/**
 * Read-only view of on-device-model runtime health (US-059 / SF-8.10).
 *
 * Implemented by `data/ml/FunctionGemmaEngine` (canonical binding for the
 * diagnostics screen) and `data/ml/Gemma3nEngine` (the large-text surface,
 * US-061 — backed by Gemma 4 E2B since the May 2026 swap; see its KDoc).
 * Consumed by `DiagnosticsViewModel` to populate the "Modelo" section.
 *
 * All reads are O(1) — backed by `@Volatile var` members written in the
 * warm-up / load / inference paths. The `suspend` modifier on the latency
 * accessors is an affordance for future storage-backed implementations; the
 * current impls return immediately.
 *
 * The 3 large-text methods at the bottom (added in US-061 / SF-9.2) are
 * defaulted so existing impls (`FunctionGemmaEngine`) keep compiling without
 * changes. A future SF (post-US-061) will inject the [Gemma3nEngine] binding
 * into `DiagnosticsViewModel` and surface the values in the UI; until then
 * the defaults return `false` / `null` and the diagnostics screen ignores
 * them.
 *
 * Why default methods instead of a sibling `LargeTextMetrics` interface: a
 * second interface would double the Hilt-binding work and force the
 * diagnostics screen to inject + `combine` two metrics surfaces. Defaults
 * keep the existing call sites unchanged.
 *
 * The `gemma3n*` method names are retained for diff hygiene after the
 * Gemma 3n → Gemma 4 swap — they report whichever large-text model
 * [Gemma3nEngine] currently backs. A future SF may rename them to
 * `largeText*`.
 */
interface EngineMetrics {
    /** True iff the engine's LLM instance is resident in memory and ready to infer. */
    fun isReady(): Boolean

    /** Display name for the model, e.g. `"FunctionGemma270M"`. */
    fun modelName(): String

    /** Wall-clock milliseconds for the last successful warm-up, or null if never warmed up this session. */
    suspend fun lastWarmUpLatencyMs(): Long?

    /** Wall-clock milliseconds for the last successful `decide` call, or null if no inference yet this session. */
    suspend fun lastInferenceLatencyMs(): Long?

    // ── Large-text model (US-061 / SF-9.2 — Gemma 4 E2B since May 2026) ──────
    //
    // Defaults are conservative so existing impls (FunctionGemmaEngine) don't
    // need to implement them. The diagnostics screen reads them only when a
    // future SF injects the [Gemma3nEngine] binding.

    /** True iff the large-text model is currently loaded. Default: `false`. */
    fun gemma3nIsReady(): Boolean = false

    /** Wall-clock milliseconds for the last successful large-text load, or `null` if never loaded. */
    suspend fun gemma3nLastLoadLatencyMs(): Long? = null

    /** Wall-clock milliseconds for the last successful large-text generate, or `null` if no inference yet. */
    suspend fun gemma3nLastGenerateLatencyMs(): Long? = null
}
