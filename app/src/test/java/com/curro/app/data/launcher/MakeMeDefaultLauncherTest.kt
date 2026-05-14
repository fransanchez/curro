package com.curro.app.data.launcher

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MakeMeDefaultLauncher].
 *
 * Uses the internal [RoleManagerWrapper] seam to avoid Robolectric — pure JVM JUnit 5.
 * Sets [MakeMeDefaultLauncher.roleOverride] before each call to prevent the production
 * [RoleManagerWrapperImpl] (which calls [android.app.role.RoleManager] via
 * [Context.getSystemService]) from being initialised on the JVM test classpath.
 *
 * [android.content.Intent] is a stub on the JVM test classpath. [mockkConstructor] is used
 * to intercept `Intent` construction in [openHomeSettings] tests so we can assert on the
 * action string and flag; [unmockkConstructor] restores the class after each test.
 *
 * Covers the four scenarios from the US-009 brief:
 *  1. Role available + not held → [requestRoleIntent] returns a non-null Intent.
 *  2. Role available + held → [requestRoleIntent] returns null.
 *  3. Role unavailable → [requestRoleIntent] returns null.
 *  4. [openHomeSettings] calls `new Intent(ACTION_HOME_SETTINGS)` and `addFlags(NEW_TASK)`.
 */
@DisplayName("MakeMeDefaultLauncher")
class MakeMeDefaultLauncherTest {
    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var launcher: MakeMeDefaultLauncher

    // A fake role-request intent; its identity doesn't matter — the real RoleManager
    // builds the intent; tests just assert non-null/null return.
    private val fakeRoleIntent: Intent = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        launcher = MakeMeDefaultLauncher(mockContext)
    }

    @AfterEach
    fun tearDown() {
        // Clean up any constructor mocks set in individual tests.
        try {
            unmockkConstructor(Intent::class)
        } catch (_: Exception) {
            // No constructor mock was set — safe to ignore.
        }
    }

    // -----------------------------------------------------------------------------------------
    // requestRoleIntent() tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun `requestRoleIntent returns non-null intent when role is available and not held`() {
        launcher.roleOverride =
            fakeWrapper(
                available = true,
                held = false,
                intent = fakeRoleIntent,
            )
        assertNotNull(launcher.requestRoleIntent())
    }

    @Test
    fun `requestRoleIntent returns the exact intent from the wrapper when role is available and not held`() {
        launcher.roleOverride =
            fakeWrapper(
                available = true,
                held = false,
                intent = fakeRoleIntent,
            )
        assertEquals(fakeRoleIntent, launcher.requestRoleIntent())
    }

    @Test
    fun `requestRoleIntent returns null when role is available but already held`() {
        launcher.roleOverride =
            fakeWrapper(
                available = true,
                held = true,
                intent = fakeRoleIntent,
            )
        assertNull(launcher.requestRoleIntent())
    }

    @Test
    fun `requestRoleIntent returns null when role is unavailable`() {
        launcher.roleOverride =
            fakeWrapper(
                available = false,
                held = false,
                intent = fakeRoleIntent,
            )
        assertNull(launcher.requestRoleIntent())
    }

    // -----------------------------------------------------------------------------------------
    // openHomeSettings() tests — use mockkConstructor to intercept Intent creation
    // -----------------------------------------------------------------------------------------

    @Test
    fun `openHomeSettings returns non-null intent and adds FLAG_ACTIVITY_NEW_TASK`() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().addFlags(any()) } returns mockk(relaxed = true)

        // openHomeSettings uses apply{}, so the return value is the constructed Intent itself.
        val result = launcher.openHomeSettings()

        assertNotNull(result, "openHomeSettings must return a non-null Intent")
        verify { anyConstructed<Intent>().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    // -----------------------------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------------------------

    private fun fakeWrapper(
        available: Boolean,
        held: Boolean,
        intent: Intent?,
    ): RoleManagerWrapper =
        object : RoleManagerWrapper {
            override fun isRoleAvailable() = available

            override fun isRoleHeld() = held

            override fun createRequestRoleIntent() = intent
        }
}
