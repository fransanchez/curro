package com.curro.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides [Clock] as a singleton so production code uses the real system clock and
 * tests substitute `Clock.fixed(instant, zone)` for deterministic time assertions.
 *
 * The clock is exposed as `java.time.Clock` — callers create a `LocalDateTime`
 * via `LocalDateTime.now(clock)` so that any future need to change the time
 * source (e.g. device-local vs UTC for a test) only touches this module.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
