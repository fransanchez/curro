package com.curro.app

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.curro.app.data.telemetry.TelemetryInitializer
import com.curro.app.service.ModelWarmupService
import com.curro.app.util.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Curro.
 *
 * Hilt is wired here in SF-0.1. DI modules (MlModule from US-020 onward, etc.)
 * are registered via `@InstallIn(SingletonComponent::class)`.
 *
 * SF-0.8 (US-008): [TelemetryInitializer] is injected and called from [onCreate].
 * SF-3.5 (US-023): [ModelWarmupService] is started here so FunctionGemma is
 * warm before the user's first mic press.
 *
 * Call order:
 *  1. `super.onCreate()` — Hilt_CurroApp.onCreate() completes member injection.
 *  2. `telemetryInitializer.initialize()` — safe to call; injection is complete.
 *  3. Notification channel registration — idempotent; needed before the
 *     warm-up service posts its ongoing notification.
 *  4. Start the warm-up foreground service — its onStartCommand calls
 *     `startForeground` synchronously and then schedules `engine.warmUp` on
 *     `Dispatchers.IO`. Main thread is never blocked.
 *
 * Never call any injected field before [super.onCreate] — it would throw
 * [UninitializedPropertyAccessException].
 */
@HiltAndroidApp
class CurroApp : Application() {
    @Inject
    lateinit var telemetryInitializer: TelemetryInitializer

    override fun onCreate() {
        super.onCreate() // 1. Hilt initialises all @Inject members
        telemetryInitializer.initialize() // 2. Safe to call: injection is complete

        // 3. + 4. SF-3.5 (US-023) — keep FunctionGemma warm across app idle periods.
        NotificationChannels.ensureWarmupChannel(this)
        ContextCompat.startForegroundService(
            this,
            Intent(this, ModelWarmupService::class.java),
        )
    }
}
