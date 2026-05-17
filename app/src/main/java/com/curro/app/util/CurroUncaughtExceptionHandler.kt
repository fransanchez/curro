package com.curro.app.util

import com.curro.app.data.recovery.RecoveryStateRepository

/**
 * Wraps the system's default [Thread.UncaughtExceptionHandler] to record a
 * crash before delegating to the OEM crash reporter.
 *
 * **Design invariants**:
 * - NEVER throw from [uncaughtException]. The JVM is already dying; a secondary
 *   exception would mask the original crash and prevent the system handler from
 *   running. Every call that could throw is wrapped in [runCatching].
 * - Always chain to [systemDefault] so OEM crash reporters (HyperOS, Firebase
 *   Crashlytics) still receive the exception.
 *
 * @param recovery Used to record the crash and possibly set the recovery flag.
 * @param systemDefault The handler that was installed before Curro replaced it.
 *   Null if there was no prior handler (rare in practice — the JVM always
 *   provides a default that prints the stack trace and terminates the process).
 * @param nowMs Time source — injectable for unit tests.
 */
class CurroUncaughtExceptionHandler(
    private val recovery: RecoveryStateRepository,
    private val systemDefault: Thread.UncaughtExceptionHandler?,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        t: Thread,
        e: Throwable,
    ) {
        runCatching { recovery.recordCrash(nowMs()) } // never throw — already crashing
        systemDefault?.uncaughtException(t, e)
    }
}
