package com.curro.app.data.recovery

/**
 * Manages crash-loop detection and the "boot into Recovery Mode" flag.
 *
 * All operations are **synchronous** — the implementation uses plain
 * [android.content.SharedPreferences] with [android.content.SharedPreferences.Editor.commit].
 * This is intentional: [recordCrash] is called from
 * [Thread.UncaughtExceptionHandler.uncaughtException] where the JVM is about to die
 * and coroutines / [android.content.SharedPreferences.Editor.apply] may not flush in time.
 *
 * Recovery Mode activates when [CRASH_THRESHOLD] crashes are detected within
 * [CRASH_WINDOW_MS]. On the next launch [isRecoveryPending] returns `true` and
 * [MainActivity] routes to [RecoveryScreen] instead of [CurroNavHost].
 */
interface RecoveryStateRepository {
    /**
     * Records a crash at [nowMs]. Increments the in-window counter; sets the
     * "recovery pending" flag if [CRASH_THRESHOLD] is reached.
     *
     * Must **never** throw — the caller is inside an uncaught-exception handler.
     */
    fun recordCrash(nowMs: Long)

    /**
     * Returns `true` if Curro should boot into Recovery Mode on this launch.
     *
     * Called synchronously from [MainActivity.onCreate] before [setContent].
     */
    fun isRecoveryPending(): Boolean

    /**
     * Clears the "recovery pending" flag **and** the crash history.
     *
     * Called when the user taps either button on the Recovery screen (open
     * system settings OR retry Curro).
     */
    fun acknowledgeRecovery()

    /**
     * Clears the crash counter after a successful run.
     *
     * Called from [CurroApp] after [SUCCESSFUL_RUN_DELAY_MS] of crash-free
     * operation so that old crashes don't unfairly contribute to future windows.
     */
    fun recordSuccessfulRun()

    companion object {
        /** Number of crashes required to enter Recovery Mode. */
        const val CRASH_THRESHOLD: Int = 2

        /** Width of the sliding window in milliseconds. */
        const val CRASH_WINDOW_MS: Long = 60_000L

        /** How long Curro must run without a crash to clear the counter. */
        const val SUCCESSFUL_RUN_DELAY_MS: Long = 60_000L
    }
}
