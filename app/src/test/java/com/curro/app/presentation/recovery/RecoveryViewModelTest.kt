package com.curro.app.presentation.recovery

import android.content.Intent
import android.provider.Settings
import com.curro.app.data.recovery.RecoveryStateRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [RecoveryViewModel].
 *
 * Robolectric is required because [RecoveryViewModel.onOpenSystemSettings] creates a
 * real Android [Intent] — the JVM stub returns null for [Intent.addFlags], causing a
 * not-null contract violation. Robolectric provides a real [Intent] implementation.
 *
 * [RecoveryStateRepository] is a hand-rolled fake so the test doesn't touch SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RecoveryViewModelTest {
    private class FakeRecovery : RecoveryStateRepository {
        var acknowledgeCallCount = 0
            private set

        override fun recordCrash(nowMs: Long) {}

        override fun isRecoveryPending(): Boolean = true

        override fun acknowledgeRecovery() {
            acknowledgeCallCount++
        }

        override fun recordSuccessfulRun() {}
    }

    private lateinit var recovery: FakeRecovery
    private lateinit var vm: RecoveryViewModel

    @Before
    fun setUp() {
        recovery = FakeRecovery()
        vm = RecoveryViewModel(recovery, mockk(relaxed = true))
    }

    @Test
    fun `onOpenSystemSettings calls acknowledgeRecovery`() {
        vm.onOpenSystemSettings()
        assertEquals(1, recovery.acknowledgeCallCount)
    }

    @Test
    fun `onOpenSystemSettings returns intent with ACTION_HOME_SETTINGS`() {
        val intent = vm.onOpenSystemSettings()
        assertEquals(Settings.ACTION_HOME_SETTINGS, intent.action)
    }

    @Test
    fun `onOpenSystemSettings intent has FLAG_ACTIVITY_NEW_TASK`() {
        val intent = vm.onOpenSystemSettings()
        val hasFlag = (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        assertTrue(hasFlag)
    }

    @Test
    fun `onRetry calls acknowledgeRecovery`() {
        vm.onRetry()
        assertEquals(1, recovery.acknowledgeCallCount)
    }

    @Test
    fun `onRetry does not call acknowledgeRecovery more than once`() {
        vm.onRetry()
        assertEquals(1, recovery.acknowledgeCallCount)
    }
}
