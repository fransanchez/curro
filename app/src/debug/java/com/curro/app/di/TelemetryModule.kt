package com.curro.app.di

import com.curro.app.data.telemetry.NoopSdkBootstrap
import com.curro.app.data.telemetry.NoopTelemetrySink
import com.curro.app.data.telemetry.SdkBootstrap
import com.curro.app.domain.repository.TelemetrySink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Debug-variant Hilt module. Binds no-op implementations of [TelemetrySink] and [SdkBootstrap].
 *
 * Paired with the release-variant module at `src/release/.../di/TelemetryModule.kt`.
 * Hilt sees exactly one [TelemetryModule] per variant at compile time — no runtime branch
 * (Q5-Resolved in docs/briefs/US-008-telemetry-plumbing.md).
 *
 * The debug APK has no Firebase / PostHog bytecode (Q1-Resolved: releaseImplementation).
 * This module's existence in `src/debug/` ensures the Hilt graph compiles even though
 * `FirebaseAndPostHogSink` and `FirebaseAndPostHogSdkBootstrap` don't exist on the debug
 * classpath.
 */
@Module
@InstallIn(SingletonComponent::class)
interface TelemetryModule {
    @Binds
    @Singleton
    fun bindTelemetrySink(impl: NoopTelemetrySink): TelemetrySink

    @Binds
    @Singleton
    fun bindSdkBootstrap(impl: NoopSdkBootstrap): SdkBootstrap
}
