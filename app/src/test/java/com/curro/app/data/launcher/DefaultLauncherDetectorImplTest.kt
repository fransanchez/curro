package com.curro.app.data.launcher

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultLauncherDetectorImpl].
 *
 * Uses internal seams ([DefaultLauncherDetectorImpl.homeActivityResolver] +
 * [DefaultLauncherDetectorImpl.lifecycleSource]) to avoid Robolectric — pure JVM JUnit 5.
 *
 * [LifecycleRegistry.createUnsafe] is used instead of `LifecycleRegistry(owner)` to bypass
 * the main-thread assertion in `setCurrentState` — safe in JVM tests where there is no
 * Android `Looper`.
 *
 * [UnconfinedTestDispatcher] is used so that the `callbackFlow` + `flowOn(Main.immediate)`
 * chain propagates emissions eagerly in tests without needing explicit `advanceUntilIdle`.
 *
 * Covers the six scenarios from the US-009 brief:
 *  1. Curro is the resolved home → [isDefault] == true.
 *  2. Stock launcher is the resolved home → [isDefault] == false.
 *  3. [resolveActivity] returns null → [isDefault] == false.
 *  4. Flow emits the current value on subscription (onStart).
 *  5. Flow re-emits the new value when the resolver answer changes on a simulated ON_RESUME.
 *  6. Flow [distinctUntilChanged] — two consecutive ON_RESUME events with the same answer
 *     emit only once.
 */
@ExperimentalCoroutinesApi
@DisplayName("DefaultLauncherDetectorImpl")
class DefaultLauncherDetectorImplTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    // The production BuildConfig.APPLICATION_ID is "com.curro.app" (verified from build output).
    private val curroPackage = "com.curro.app"

    private val mockContext: Context = mockk(relaxed = true)
    private lateinit var detector: DefaultLauncherDetectorImpl

    // LifecycleRegistry.createUnsafe skips main-thread assertion — safe for JVM tests.
    private val lifecycleOwner =
        object : LifecycleOwner {
            val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        detector = DefaultLauncherDetectorImpl(mockContext)
        detector.lifecycleSource = { lifecycleOwner.lifecycle }
        // Start the lifecycle in CREATED so observers can be added without ON_RESUME firing.
        lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------------------------
    // isDefault() snapshot tests (scenarios 1–3)
    // -----------------------------------------------------------------------------------------

    @Test
    fun `isDefault returns true when resolver returns Curro package`() {
        detector.homeActivityResolver = { curroPackage }
        assertTrue(detector.isDefault())
    }

    @Test
    fun `isDefault returns false when resolver returns a different package`() {
        detector.homeActivityResolver = { "com.android.launcher3" }
        assertFalse(detector.isDefault())
    }

    @Test
    fun `isDefault returns false when resolver returns null`() {
        detector.homeActivityResolver = { null }
        assertFalse(detector.isDefault())
    }

    // -----------------------------------------------------------------------------------------
    // Flow tests (scenarios 4–6) — use Turbine
    // -----------------------------------------------------------------------------------------

    @Test
    fun `flow emits current value on subscription (onStart)`() =
        runTest {
            detector.homeActivityResolver = { curroPackage } // Curro is default

            detector.flow.test {
                // Scenario 4: initial emission from onStart
                assertTrue(awaitItem(), "Expected true on subscription when Curro is default")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `flow re-emits new value when resolver changes between ON_RESUME events`() =
        runTest {
            // Start: Curro is NOT default
            detector.homeActivityResolver = { "com.android.launcher3" }

            detector.flow.test {
                // Scenario 4: initial emission — not default
                assertFalse(awaitItem(), "Expected false on subscription when Curro is not default")

                // Scenario 5: resolver changes to Curro, then ON_RESUME fires
                detector.homeActivityResolver = { curroPackage }
                simulateResume()

                assertTrue(awaitItem(), "Expected true after resolver changes to Curro and ON_RESUME fires")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `flow distinctUntilChanged — two consecutive ON_RESUME with same answer emit only once`() =
        runTest {
            detector.homeActivityResolver = { "com.android.launcher3" }

            detector.flow.test {
                // Scenario 4: initial emission — not default
                assertFalse(awaitItem(), "Expected false on subscription")

                // Scenario 6: two consecutive ON_RESUME events — answer stays the same
                simulateResume()
                simulateResume()

                // distinctUntilChanged: should not emit again (answer is still false)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -----------------------------------------------------------------------------------------
    // Helper: move lifecycle through RESUMED to trigger ON_RESUME observers
    // -----------------------------------------------------------------------------------------

    private fun simulateResume() {
        // LifecycleRegistry requires moving through STARTED before RESUMED if not already started.
        if (!lifecycleOwner.registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleOwner.registry.currentState = Lifecycle.State.STARTED
        }
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        // Move back to STARTED so consecutive calls can fire ON_RESUME again.
        lifecycleOwner.registry.currentState = Lifecycle.State.STARTED
    }
}
