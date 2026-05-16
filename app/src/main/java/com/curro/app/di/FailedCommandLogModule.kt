package com.curro.app.di

import com.curro.app.data.local.RoomFailedCommandLog
import com.curro.app.domain.repository.FailedCommandLog
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for [FailedCommandLog] → [RoomFailedCommandLog] (SF-7.5 / US-049).
 *
 * Kept as a separate module (not folded into [DatabaseModule]) so Phase-8's UI
 * can import this module independently and the scope is explicit.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("UnnecessaryAbstractClass") // Hilt requires `abstract class` + `@Binds` for interface binding.
abstract class FailedCommandLogModule {
    @Binds
    @Singleton
    abstract fun bindFailedCommandLog(impl: RoomFailedCommandLog): FailedCommandLog
}
