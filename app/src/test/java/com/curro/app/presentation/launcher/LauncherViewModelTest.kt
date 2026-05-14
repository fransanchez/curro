package com.curro.app.presentation.launcher

import app.cash.turbine.test
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.domain.model.ClockState
import com.curro.app.domain.usecase.ObserveClockUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LauncherViewModel].
 *
 * Pure JVM + Turbine + [runTest] with [UnconfinedTestDispatcher] — no Robolectric, no Hilt.
 * [UnconfinedTestDispatcher] is used so that `combine(...).stateIn(WhileSubscribed)` activates
 * eagerly and emissions from the fake flows propagate to the [StateFlow] without needing
 * explicit [kotlinx.coroutines.test.TestCoroutineScheduler.advanceUntilIdle] calls.
 *
 * Uses fake [DefaultLauncherDetector] (anonymous object with [MutableSharedFlow]) and a
 * MockK-backed [ObserveClockUseCase] that returns a controlled [MutableSharedFlow].
 *
 * Covers:
 *  1. Initial state: `isCurroDefault = false`, clock = `("--:--", "")` (VM default).
 *  2. After the detector emits `true`: `isCurroDefault = true`.
 *  3. After the detector emits `false` again: `isCurroDefault = false`.
 *  4. After the clock flow emits a [ClockState]: `clock` reflects the new value.
 *  5. Initial `clock.timeText` is `"--:--"` (the placeholder before the use case fires).
 */
@ExperimentalCoroutinesApi
@DisplayName("LauncherViewModel")
class LauncherViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    // MutableStateFlow so combine() sees both upstreams immediately on subscription,
    // matching production behaviour (detector emits current state; clock ticks once instantly).
    private val fakeDetectorFlow = MutableStateFlow(false)
    private val fakeClockFlow = MutableStateFlow(ClockState(timeText = "--:--", dateText = ""))

    private val fakeDetector =
        object : DefaultLauncherDetector {
            override fun isDefault(): Boolean = false

            override val flow: Flow<Boolean>
                get() = fakeDetectorFlow
        }

    private val mockObserveClock: ObserveClockUseCase = mockk()

    private lateinit var viewModel: LauncherViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockObserveClock() } returns fakeClockFlow
        viewModel = LauncherViewModel(fakeDetector, mockObserveClock)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 1 — initial state
    // -----------------------------------------------------------------------------------------

    @Test
    fun `initial uiState has isCurroDefault false`() =
        runTest {
            assertFalse(
                viewModel.uiState.value.isCurroDefault,
                "stateIn initialValue should have isCurroDefault = false",
            )
        }

    @Test
    fun `initial uiState clock timeText is the placeholder`() =
        runTest {
            assertEquals(
                "--:--",
                viewModel.uiState.value.clock.timeText,
                "Initial clock.timeText should be the placeholder '--:--'",
            )
        }

    @Test
    fun `initial uiState clock is not null`() =
        runTest {
            assertNotNull(
                viewModel.uiState.value.clock,
                "Initial clock must not be null",
            )
        }

    // -----------------------------------------------------------------------------------------
    // Scenarios 2–3 — reactor to detector flow emissions
    // -----------------------------------------------------------------------------------------

    @Test
    fun `uiState isCurroDefault becomes true when detector emits true`() =
        runTest {
            viewModel.uiState.test {
                // Consume initial state
                assertFalse(awaitItem().isCurroDefault, "Expected initial false")

                fakeDetectorFlow.emit(true)

                val next = awaitItem()
                assertEquals(
                    true,
                    next.isCurroDefault,
                    "Expected isCurroDefault = true after detector emits true",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState isCurroDefault returns to false when detector emits false after true`() =
        runTest {
            viewModel.uiState.test {
                // Consume initial state
                assertFalse(awaitItem().isCurroDefault, "Expected initial false")

                fakeDetectorFlow.emit(true)
                assertEquals(true, awaitItem().isCurroDefault)

                fakeDetectorFlow.emit(false)
                assertEquals(
                    false,
                    awaitItem().isCurroDefault,
                    "Expected isCurroDefault = false after detector emits false",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -----------------------------------------------------------------------------------------
    // Scenario 4 — reactor to clock flow emissions
    // -----------------------------------------------------------------------------------------

    @Test
    fun `uiState clock updates when ObserveClockUseCase emits a new ClockState`() =
        runTest {
            val newClock = ClockState(timeText = "14:30", dateText = "Jueves 14 mayo")

            viewModel.uiState.test {
                // Consume initial state (placeholder clock)
                awaitItem()

                fakeClockFlow.emit(newClock)

                val next = awaitItem()
                assertEquals(
                    newClock,
                    next.clock,
                    "Expected clock to update when ObserveClockUseCase emits",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isCurroDefault is preserved when only the clock updates`() =
        runTest {
            viewModel.uiState.test {
                // Consume initial state
                awaitItem()

                // Set isCurroDefault = true
                fakeDetectorFlow.emit(true)
                assertEquals(true, awaitItem().isCurroDefault)

                // Now only the clock updates — isCurroDefault should remain true
                fakeClockFlow.emit(ClockState(timeText = "09:00", dateText = "Viernes 15 mayo"))
                val next = awaitItem()
                assertEquals(
                    true,
                    next.isCurroDefault,
                    "isCurroDefault should remain true after a clock-only update",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -----------------------------------------------------------------------------------------
    // Compatibility: verify old-shape assertions on LauncherUiState still pass with clock field
    // -----------------------------------------------------------------------------------------

    @Test
    fun `LauncherUiState with same fields are equal`() =
        runTest {
            val clock = ClockState("12:00", "Lunes 11 mayo")
            val a = LauncherUiState(isCurroDefault = true, clock = clock)
            val b = LauncherUiState(isCurroDefault = true, clock = clock)
            assertEquals(a, b, "data class equality must hold for LauncherUiState")
        }
}
