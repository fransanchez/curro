package com.curro.app.di

import com.curro.app.data.ml.DefaultLlmInferenceFactory
import com.curro.app.data.ml.FunctionGemmaEngine
import com.curro.app.data.ml.Gemma3nEngine
import com.curro.app.data.ml.LlmInferenceFactory
import com.curro.app.domain.repository.EngineMetrics
import com.curro.app.domain.repository.FunctionCallEngine
import com.curro.app.domain.repository.TextGenEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the on-device LLM engines (US-020 / SF-3.2 for FunctionGemma,
 * US-061 / SF-9.2 for the large-text engine + the `LlmInferenceFactory` seam).
 *
 * `@Singleton` on every binding so the same `LlmInference` instance is reused
 * across the process — loading either model is expensive and we never want
 * two copies in memory.
 *
 * The same binding applies to debug + release. Tests substitute fakes via
 * Hilt's `@TestInstallIn` (Phase 5 onward); for pure-JVM tests the fake is
 * constructed manually.
 *
 * The canonical [EngineMetrics] binding stays on [FunctionGemmaEngine] —
 * [DiagnosticsViewModel] reads it. The large-text engine's additive metrics
 * methods on [EngineMetrics] are read by a future SF that injects the
 * [Gemma3nEngine] binding directly; see its KDoc (also note that the engine
 * class name predates the May 2026 swap from Gemma 3n to Gemma 4 E2B).
 */
@Module
@InstallIn(SingletonComponent::class)
interface MlModule {
    @Binds
    @Singleton
    fun bindFunctionCallEngine(impl: FunctionGemmaEngine): FunctionCallEngine

    @Binds
    @Singleton
    fun bindEngineMetrics(impl: FunctionGemmaEngine): EngineMetrics

    // US-061 / SF-9.2 — large-text on-demand generation (backed by Gemma 4 E2B).

    @Binds
    @Singleton
    fun bindTextGenEngine(impl: Gemma3nEngine): TextGenEngine

    @Binds
    @Singleton
    fun bindLlmInferenceFactory(impl: DefaultLlmInferenceFactory): LlmInferenceFactory
}
