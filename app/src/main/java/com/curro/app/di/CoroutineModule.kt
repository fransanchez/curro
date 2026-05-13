package com.curro.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Coroutine plumbing. Injected by everything async: model engines, STT/TTS clients,
 * Room DAOs, the FSM coordinator, ViewModels. Qualifier annotations (not a
 * DispatcherProvider interface) — see Q2 / A2 in US-002 brief.
 *
 * The [@ApplicationScope] parent is [Dispatchers.Main.immediate], not IO — see Q1 / A6 in
 * US-002 brief. Per-call IO/Default work is opted into via `withContext(io)` /
 * `withContext(default)`.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @MainDispatcher main: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + main + CoroutineName("CurroAppScope"))
}
