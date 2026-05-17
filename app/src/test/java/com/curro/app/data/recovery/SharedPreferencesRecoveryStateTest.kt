package com.curro.app.data.recovery

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [SharedPreferencesRecoveryState].
 *
 * SharedPreferences requires an Android [Context], so Robolectric is used here
 * (same pattern as the DAO tests in [ContactAliasDaoTest]).
 *
 * Each test gets a fresh instance backed by a fresh SharedPreferences file — the
 * file is named "curro_recovery" by the impl; Robolectric's in-memory
 * SharedPreferences are cleared between tests via [clearPrefs].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SharedPreferencesRecoveryStateTest {
    private lateinit var repo: SharedPreferencesRecoveryState

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        // Clear any state left from a previous test — Robolectric re-uses the
        // Application instance across tests in the same class.
        context
            .getSharedPreferences("curro_recovery", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        repo = SharedPreferencesRecoveryState(context)
    }

    @Test
    fun `fresh install - isRecoveryPending returns false`() {
        assertFalse(repo.isRecoveryPending())
    }

    @Test
    fun `one crash - not pending`() {
        val now = System.currentTimeMillis()
        repo.recordCrash(now)
        assertFalse(repo.isRecoveryPending())
    }

    @Test
    fun `two crashes within 30 seconds - pending`() {
        val now = System.currentTimeMillis()
        repo.recordCrash(now)
        repo.recordCrash(now + 30_000L)
        assertTrue(repo.isRecoveryPending())
    }

    @Test
    fun `two crashes 90 seconds apart - NOT pending`() {
        val now = System.currentTimeMillis()
        repo.recordCrash(now)
        // Second crash is 90s later — outside the 60s window.
        repo.recordCrash(now + 90_000L)
        assertFalse(repo.isRecoveryPending())
    }

    @Test
    fun `acknowledgeRecovery clears pending flag and crash history`() {
        val now = System.currentTimeMillis()
        repo.recordCrash(now)
        repo.recordCrash(now + 5_000L)
        assertTrue(repo.isRecoveryPending())

        repo.acknowledgeRecovery()

        assertFalse(repo.isRecoveryPending())
        // The crash history is cleared — a fresh two-crash window starting now
        // from a new recordCrash call can trigger again (no stale timestamps).
        repo.recordCrash(System.currentTimeMillis())
        assertFalse(repo.isRecoveryPending()) // only 1 crash after clear
    }

    @Test
    fun `recordSuccessfulRun clears crash counter so previous crashes do not count`() {
        val now = System.currentTimeMillis()
        // Record one crash, then simulate a successful run which clears the counter.
        repo.recordCrash(now)
        repo.recordSuccessfulRun()

        // Now record one more crash — only 1 crash since the counter was cleared;
        // should NOT trigger recovery.
        repo.recordCrash(now + 1_000L)
        assertFalse(repo.isRecoveryPending())
    }

    @Test
    fun `recordSuccessfulRun does not affect pending flag already set`() {
        val now = System.currentTimeMillis()
        repo.recordCrash(now)
        repo.recordCrash(now + 5_000L)
        assertTrue(repo.isRecoveryPending())

        // A successful run only clears the crash history, not the pending flag
        // (the user still needs to acknowledge the recovery).
        repo.recordSuccessfulRun()
        assertTrue(repo.isRecoveryPending())
    }
}
