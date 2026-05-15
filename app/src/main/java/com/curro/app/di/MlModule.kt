package com.curro.app.di

import com.curro.app.data.ml.FunctionGemmaEngine
import com.curro.app.domain.repository.FunctionCallEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the on-device LLM engine (US-020 / SF-3.2).
 *
 * `@Singleton` so the same `LlmInference` instance is reused across the
 * process — loading FunctionGemma is expensive and we never want two copies
 * in memory.
 *
 * The same binding applies to debug + release. Tests substitute
 * `FakeFunctionCallEngine` via Hilt's `@TestInstallIn` (Phase 5 onward); for
 * pure-JVM tests the fake is constructed manually.
 */
@Module
@InstallIn(SingletonComponent::class)
interface MlModule {
    @Binds
    @Singleton
    fun bindFunctionCallEngine(impl: FunctionGemmaEngine): FunctionCallEngine
}
