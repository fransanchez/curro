package com.curro.app

import android.app.Application
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
import android.content.Intent
import androidx.core.content.ContextCompat
import com.curro.app.data.telemetry.TelemetryInitializer
import com.curro.app.di.ApplicationScope
import com.curro.app.domain.repository.TextGenEngine
import com.curro.app.service.ModelWarmupService
import com.curro.app.util.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
 * SF-9.2 (US-061): [TextGenEngine] is injected (NOT warmed) so we can release
 * it under memory pressure via [onTrimMemory].
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
 * **NO Gemma 3n warm-up.** Per `on-device-llm` Rule 3, Gemma 3n loads only
 * on demand. The first caller (US-062's `ReadAllUnreadWhatsAppHandler` > 8
 * branch) pays the load cost; the user hears `copy_cold_model` ("Dame un
 * segundo.") while it loads.
 *
 * Never call any injected field before [super.onCreate] — it would throw
 * [UninitializedPropertyAccessException].
 */
@HiltAndroidApp
class CurroApp : Application() {
    @Inject
    lateinit var telemetryInitializer: TelemetryInitializer

    // SF-9.2 (US-061) — held so [onTrimMemory] can unload Gemma 3n under
    // memory pressure. Never call [TextGenEngine.load] from here.
    @Inject
    lateinit var textGenEngine: TextGenEngine

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

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

    /**
     * Memory-pressure safeguard for Gemma 3n (US-061 / SF-9.2).
     *
     * When Android signals `TRIM_MEMORY_RUNNING_LOW` or worse, release the
     * ~2 GB Gemma 3n footprint so the OS has room to keep WhatsApp / the
     * dialer / Camera responsive. The next mic press that needs a summary
     * cold-loads again (and may OOM again, falling back to
     * `copy_many_unread` — the contract is graceful, not perfect).
     *
     * Fire-and-forget — `unload()` is idempotent + cheap (a `close()` call +
     * a flag flip); we don't wait. FunctionGemma is deliberately NOT touched
     * here: the warm-up service keeps it resident so the assistant's
     * function-calling brain stays alive even when memory is tight.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            appScope.launch { textGenEngine.unload() }
        }
    }
}
