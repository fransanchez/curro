package com.curro.app.util

import com.curro.app.data.recovery.RecoveryStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM (JUnit 5) tests for [CurroUncaughtExceptionHandler].
 *
 * No Android dependencies — the handler only calls [RecoveryStateRepository.recordCrash]
 * and delegates to a [Thread.UncaughtExceptionHandler], both of which are faked inline.
 */
class CurroUncaughtExceptionHandlerTest {
    // Fake RecoveryStateRepository that records calls without touching SharedPreferences.
    private class FakeRecovery(
        private val throwOnRecord: Boolean = false,
    ) : RecoveryStateRepository {
        val recordedTimestamps = mutableListOf<Long>()

        override fun recordCrash(nowMs: Long) {
            if (throwOnRecord) error("simulated recordCrash failure")
            recordedTimestamps.add(nowMs)
        }

        override fun isRecoveryPending(): Boolean = false

        override fun acknowledgeRecovery() {}

        override fun recordSuccessfulRun() {}
    }

    @Test
    fun `chains to system default after recording crash`() {
        val recovery = FakeRecovery()
        var systemDefaultCalled = false
        val systemDefault =
            Thread.UncaughtExceptionHandler { _, _ -> systemDefaultCalled = true }

        val handler = CurroUncaughtExceptionHandler(recovery, systemDefault)
        handler.uncaughtException(Thread.currentThread(), RuntimeException("test"))

        assertTrue(systemDefaultCalled)
        assertEquals(1, recovery.recordedTimestamps.size)
    }

    @Test
    fun `recordCrash failure is swallowed - system default still called`() {
        val recovery = FakeRecovery(throwOnRecord = true)
        var systemDefaultCalled = false
        val systemDefault =
            Thread.UncaughtExceptionHandler { _, _ -> systemDefaultCalled = true }

        val handler = CurroUncaughtExceptionHandler(recovery, systemDefault)
        // Must NOT throw even though recovery.recordCrash throws internally.
        handler.uncaughtException(Thread.currentThread(), RuntimeException("test"))

        assertTrue(systemDefaultCalled)
        // No timestamps because the recording failed — but no secondary exception either.
        assertTrue(recovery.recordedTimestamps.isEmpty())
    }

    @Test
    fun `nowMs injection passes the correct timestamp to recordCrash`() {
        val fixedNow = 1_700_000_000_000L
        val recovery = FakeRecovery()
        val handler =
            CurroUncaughtExceptionHandler(
                recovery = recovery,
                systemDefault = null,
                nowMs = { fixedNow },
            )

        handler.uncaughtException(Thread.currentThread(), RuntimeException("test"))

        assertEquals(listOf(fixedNow), recovery.recordedTimestamps)
    }

    @Test
    fun `null system default does not throw`() {
        val recovery = FakeRecovery()
        val handler = CurroUncaughtExceptionHandler(recovery, systemDefault = null)

        // Must not throw when systemDefault is null.
        handler.uncaughtException(Thread.currentThread(), RuntimeException("test"))

        assertEquals(1, recovery.recordedTimestamps.size)
    }

    @Test
    fun `multiple crashes each record a separate timestamp`() {
        val t1 = 1_000_000L
        val t2 = 2_000_000L
        var callIndex = 0
        val timestamps = longArrayOf(t1, t2)
        val recovery = FakeRecovery()
        val handler =
            CurroUncaughtExceptionHandler(
                recovery = recovery,
                systemDefault = null,
                nowMs = { timestamps[callIndex++] },
            )

        handler.uncaughtException(Thread.currentThread(), RuntimeException("first"))
        handler.uncaughtException(Thread.currentThread(), RuntimeException("second"))

        assertEquals(listOf(t1, t2), recovery.recordedTimestamps)
    }
}
