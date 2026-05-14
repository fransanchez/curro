package com.curro.app.presentation.launcher

import app.cash.turbine.test
import com.curro.app.data.launcher.DefaultLauncherDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LauncherViewModel].
 *
 * Pure JVM + Turbine + [runTest] with [UnconfinedTestDispatcher] — no Robolectric, no Hilt.
 * [UnconfinedTestDispatcher] is used so that `stateIn(WhileSubscribed)` activates
 * eagerly and emissions from the fake flow propagate to the [StateFlow] without needing
 * explicit [kotlinx.coroutines.test.TestCoroutineScheduler.advanceUntilIdle] calls.
 * Uses a fake [DefaultLauncherDetector] whose [flow] is a [MutableSharedFlow].
 *
 * Covers the three scenarios from the US-009 brief:
 *  1. Initial state: [LauncherViewModel.uiState].value == [LauncherUiState](isCurroDefault = false).
 *  2. After the fake emits `true`: next uiState == [LauncherUiState](isCurroDefault = true).
 *  3. After the fake emits `false` again: back to [LauncherUiState](isCurroDefault = false).
 */
@ExperimentalCoroutinesApi
@DisplayName("LauncherViewModel")
class LauncherViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeFlow = MutableSharedFlow<Boolean>()
    private val fakeDetector =
        object : DefaultLauncherDetector {
            override fun isDefault(): Boolean = false

            override val flow: Flow<Boolean>
                get() = fakeFlow
        }

    private lateinit var viewModel: LauncherViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LauncherViewModel(fakeDetector)
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
                "stateIn initialValue should be LauncherUiState(isCurroDefault = false)",
            )
        }

    // -----------------------------------------------------------------------------------------
    // Scenarios 2–3 — reactor to flow emissions
    // -----------------------------------------------------------------------------------------

    @Test
    fun `uiState updates to isCurroDefault true when detector emits true`() =
        runTest {
            viewModel.uiState.test {
                // Consume initial state
                assertFalse(awaitItem().isCurroDefault, "Expected initial false")

                fakeFlow.emit(true)

                assertEquals(
                    LauncherUiState(isCurroDefault = true),
                    awaitItem(),
                    "Expected LauncherUiState(isCurroDefault = true) after detector emits true",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState updates back to isCurroDefault false when detector emits false after true`() =
        runTest {
            viewModel.uiState.test {
                // Consume initial state
                assertFalse(awaitItem().isCurroDefault, "Expected initial false")

                fakeFlow.emit(true)
                assertEquals(LauncherUiState(isCurroDefault = true), awaitItem())

                fakeFlow.emit(false)
                assertEquals(
                    LauncherUiState(isCurroDefault = false),
                    awaitItem(),
                    "Expected LauncherUiState(isCurroDefault = false) after detector emits false",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
}
