package com.curro.app

import android.app.Application
import com.curro.app.data.telemetry.TelemetryInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Curro.
 *
 * Hilt is wired here in SF-0.1. DI modules (DatabaseModule, RepositoryModule,
 * HandlerModule, MlModule, VoiceModule) arrive in SF-0.2.
 *
 * SF-0.8 (US-008): [TelemetryInitializer] is injected and called from [onCreate].
 * Call order (A8 in docs/briefs/US-008-telemetry-plumbing.md):
 *  1. `super.onCreate()` — Hilt_CurroApp.onCreate() completes member injection.
 *  2. `telemetryInitializer.initialize()` — safe to call; injection is complete.
 * Never call [telemetryInitializer] before [super.onCreate] — it would throw
 * [UninitializedPropertyAccessException].
 */
@HiltAndroidApp
class CurroApp : Application() {
    @Inject
    lateinit var telemetryInitializer: TelemetryInitializer

    override fun onCreate() {
        super.onCreate() // 1. Hilt initialises all @Inject members (including telemetryInitializer)
        telemetryInitializer.initialize() // 2. Safe to call: injection is complete
    }
}
