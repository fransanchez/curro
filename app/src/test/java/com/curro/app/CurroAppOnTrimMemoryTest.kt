package com.curro.app

import android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
import com.curro.app.data.recovery.RecoveryStateRepository
import com.curro.app.data.telemetry.TelemetryInitializer
import com.curro.app.domain.repository.TextGenEngine
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pure-JVM (Robolectric) tests for the [CurroApp.onTrimMemory] safeguard
 * (US-061 / SF-9.2).
 *
 * Hilt is bypassed: the test instantiates the app via Robolectric's
 * [RuntimeEnvironment.getApplication], then assigns the `lateinit var`
 * collaborators directly. `onCreate` is NOT invoked — there is no foreground
 * service to start, no telemetry initialiser to run. The single contract
 * under test is the trim-memory level → `unload` mapping.
 *
 * The application-scoped coroutine is replaced with a test scope so we can
 * deterministically wait for the launched `unload()` to run before asserting
 * on the call count.
 *
 * We use a hand-rolled counting fake instead of `mockk<TextGenEngine>` so
 * the count assertion doesn't depend on MockK's `coVerify` interaction with
 * a launched coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CurroAppOnTrimMemoryTest {
    /**
     * Builds a [CurroApp] with hand-injected collaborators. The Application is
     * the Robolectric-provided `CurroApp` instance from the test manifest;
     * `onCreate` is NOT invoked.
     */
    private fun buildApp(
        textGen: TextGenEngine,
        scope: CoroutineScope,
    ): CurroApp =
        (RuntimeEnvironment.getApplication() as CurroApp).also { app ->
            app.textGenEngine = textGen
            app.appScope = scope
            // Neither `telemetryInitializer` nor `recoveryState` is reached by
            // onTrimMemory; we assign relaxed mocks so any accidental future reference
            // doesn't throw UninitializedPropertyAccessException.
            app.telemetryInitializer = mockk<TelemetryInitializer>(relaxed = true)
            app.recoveryState = mockk<RecoveryStateRepository>(relaxed = true)
        }

    @Test
    fun `onTrimMemory RUNNING_LOW launches textGenEngine unload`() =
        runTest {
            val textGen = CountingTextGenEngine()
            val app =
                buildApp(
                    textGen = textGen,
                    scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
                )

            app.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)
            runCurrent()

            assertEquals(1, textGen.unloadCount)
        }

    @Test
    fun `onTrimMemory COMPLETE also launches textGenEngine unload`() =
        runTest {
            val textGen = CountingTextGenEngine()
            val app =
                buildApp(
                    textGen = textGen,
                    scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler)),
                )

            app.onTrimMemory(TRIM_MEMORY_COMPLETE)
            runCurrent()

            assertEquals(1, textGen.unloadCount)
        }

    @Test
    fun `onTrimMemory RUNNING_MODERATE does NOT call unload`() =
        runTest {
            val textGen = CountingTextGenEngine()
            val app =
                buildApp(
                    textGen = textGen,
                    // Real scope is fine — we don't expect a launch.
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                )

            app.onTrimMemory(TRIM_MEMORY_RUNNING_MODERATE)

            assertEquals(0, textGen.unloadCount)
        }

    /**
     * Hand-rolled counting fake. The unload-call count is a real, easily-
     * asserted counter and the test doesn't depend on MockK's behaviour for
     * `coVerify` against a launched coroutine.
     */
    private class CountingTextGenEngine : TextGenEngine {
        private val _isReady = MutableStateFlow(false)
        override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

        var unloadCount: Int = 0
            private set

        override suspend fun load(): Result<Unit> = Result.success(Unit)

        override suspend fun generate(prompt: String): Result<String> = Result.success("")

        override suspend fun unload() {
            unloadCount++
        }
    }
}
