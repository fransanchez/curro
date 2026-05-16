package com.curro.app.assistant

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single seam between the FSM (and the SF-5.2 coordinator) and the wall
 * clock. Production: [SystemTimeProvider] backed by `Clock.systemDefaultZone()`
 * (provided by `di/TimeModule.kt`). Tests: `TestTimeProvider` with a settable
 * `nowMs`, so deadline / timer assertions are deterministic.
 *
 * **DO NOT** call `System.currentTimeMillis()` or
 * `SystemClock.elapsedRealtime()` anywhere in the `assistant/` package —
 * every "what time is it" goes through this. (The Phase-3
 * `LauncherViewModel.decideAndSpeak` uses `SystemClock.elapsedRealtime` for
 * latency telemetry — that's outside the FSM and stays where it is; the new
 * code in the coordinator + FSM uses [TimeProvider].)
 */
interface TimeProvider {
    /** Epoch milliseconds ([Clock.millis]). */
    fun now(): Long
}

/**
 * Production [TimeProvider] — delegates to the injected [Clock] (provided by
 * `di/TimeModule.kt`). Co-located in the same file as the interface, matching
 * the `CallController` / `IntentCallController` pattern from US-034 §8.2.
 *
 * @see TimeProvider
 */
@Singleton
class SystemTimeProvider
    @Inject
    constructor(
        private val clock: Clock,
    ) : TimeProvider {
        override fun now(): Long = clock.millis()
    }
