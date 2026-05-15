package com.curro.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.repository.FunctionCallEngine
import com.curro.app.util.NotificationChannels
import com.curro.app.util.buildWarmupNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps FunctionGemma warm in memory across app idle periods (spec §4.3,
 * US-023 / SF-3.5).
 *
 * Started from `CurroApp.onCreate` via
 * `ContextCompat.startForegroundService(...)`. Posts the ongoing notification
 * synchronously inside [onStartCommand] (the platform requires the
 * `startForeground` call within ~5 s of `startForegroundService`); the actual
 * model load runs on a service-scoped IO coroutine so the main thread is
 * never blocked (the lesson from Phase 1's 796b5f4 + b77d789 incidents).
 *
 * `START_STICKY` so the platform restarts the service after a kill. On
 * HyperOS this often isn't enough on its own — `models/README.md` documents
 * the battery-whitelist + autostart toggles the user (= Fran) sets manually
 * before the launcher is reliable on the Redmi 15.
 *
 * Detect-and-recover: SF-3.2's [FunctionCallEngine.decide] is the second line
 * of defence — every call first checks `isReady()` and returns
 * `CurroError.ModelCold` if the engine got killed, kicking `warmUp()` as a
 * side effect for next time.
 */
@AndroidEntryPoint
class ModelWarmupService : Service() {
    @Inject
    lateinit var engine: FunctionCallEngine

    @Inject
    @IoDispatcher
    lateinit var io: CoroutineDispatcher

    /**
     * Service-scoped. Cancelled in [onDestroy] so a torn-down service does
     * not leave a dangling `warmUp` in flight. SupervisorJob isolates a
     * warm-up failure from any future child task.
     */
    private val scope by lazy { CoroutineScope(SupervisorJob() + io) }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // 1) Platform contract: start the foreground notification first (≤ 5 s
        //    from startForegroundService or Android kills us).
        startForeground(
            NotificationChannels.WARMUP_NOTIF_ID,
            buildWarmupNotification(this),
        )

        // 2) Warm the model off the main thread. The blocking
        //    LlmInference.createFromOptions runs natively on whatever thread
        //    MediaPipe chooses inside; we just need to not block Main.
        scope.launch {
            engine.warmUp()
            Log.i(TAG, "warm-up scheduled — engine.isReady = ${engine.isReady()}")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Curro/Warmup"
    }
}
