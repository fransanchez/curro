package com.curro.app.di

import com.curro.app.domain.handler.FunctionHandler
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Empty in this SF — each subsequent Phase-4 handler SF appends a single
 *
 *   @Binds @IntoMap @StringKey("<function_name>")
 *   abstract fun bind<X>(impl: <X>Handler): FunctionHandler
 *
 * line. `@Multibinds` lets Hilt resolve `Map<String, FunctionHandler>` even
 * when no entries are bound — without it, the Phase-4 build (SF-4.1 only,
 * empty map) fails to compile.
 */
@Module
@InstallIn(SingletonComponent::class)
interface HandlerModule {
    @Multibinds
    fun handlerMap(): Map<String, FunctionHandler>
}
