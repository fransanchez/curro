package com.curro.app.di

import com.curro.app.assistant.SystemTimeProvider
import com.curro.app.assistant.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for the assistant [TimeProvider] interface — the single seam
 * between the assistant FSM/coordinator and the wall clock.
 *
 * Kept separate from [TimeModule] to preserve that module's `object +
 * @Provides` shape; this one is `abstract class + @Binds` because
 * [SystemTimeProvider] is constructor-injected.
 *
 * @see TimeProvider
 * @see SystemTimeProvider
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("UnnecessaryAbstractClass") // Hilt requires `abstract class` + `@Binds` for interface binding.
abstract class TimeProviderModule {
    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
