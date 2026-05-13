package com.curro.app.di

import com.curro.app.data.telemetry.FirebaseAndPostHogSdkBootstrap
import com.curro.app.data.telemetry.FirebaseAndPostHogSink
import com.curro.app.data.telemetry.SdkBootstrap
import com.curro.app.domain.repository.TelemetrySink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Release-variant Hilt module. Binds the live Firebase + PostHog implementations.
 *
 * Paired with the debug-variant module at `src/debug/.../di/TelemetryModule.kt`.
 * Hilt sees exactly one [TelemetryModule] per variant at compile time — no runtime branch
 * (Q5-Resolved in docs/briefs/US-008-telemetry-plumbing.md).
 *
 * Both [FirebaseAndPostHogSink] and [FirebaseAndPostHogSdkBootstrap] live in `src/release/`
 * because they reference Firebase and PostHog SDK classes which are `releaseImplementation`-only
 * (Q1-Resolved). This module therefore also lives in `src/release/` — it references those
 * classes as concrete types in the `@Binds` method signatures.
 */
@Module
@InstallIn(SingletonComponent::class)
interface TelemetryModule {
    @Binds
    @Singleton
    fun bindTelemetrySink(impl: FirebaseAndPostHogSink): TelemetrySink

    @Binds
    @Singleton
    fun bindSdkBootstrap(impl: FirebaseAndPostHogSdkBootstrap): SdkBootstrap
}
