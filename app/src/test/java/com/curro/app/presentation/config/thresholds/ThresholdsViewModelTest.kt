package com.curro.app.presentation.config.thresholds

import app.cash.turbine.test
import com.curro.app.assistant.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-8.5 (US-054) — [ThresholdsViewModel] unit tests.
 *
 * Uses [FakeSettingsRepository]. No Android dependencies — runs on JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ThresholdsViewModel (SF-8.5)")
class ThresholdsViewModelTest {
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var vm: ThresholdsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settingsRepo = FakeSettingsRepository()
        vm = ThresholdsViewModel(settingsRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects DataStore defaults`() =
        runTest {
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(ThresholdsUiState.DEFAULT_EXECUTE, state.executeThreshold)
                assertEquals(ThresholdsUiState.DEFAULT_CONFIRM, state.confirmThreshold)
                assertFalse(state.alwaysConfirm)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `ExecuteThresholdChanged persists to settings repository`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(ThresholdsEvent.ExecuteThresholdChanged(0.9f))
                val state = awaitItem()
                assertEquals(0.9f, state.executeThreshold)
                val persisted = settingsRepo.executeThreshold.first()
                assertEquals(0.9f, persisted)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `ConfirmThresholdChanged persists to settings repository`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(ThresholdsEvent.ConfirmThresholdChanged(0.5f))
                val state = awaitItem()
                assertEquals(0.5f, state.confirmThreshold)
                val persisted = settingsRepo.confirmThreshold.first()
                assertEquals(0.5f, persisted)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `AlwaysConfirmChanged true persists to settings repository`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(ThresholdsEvent.AlwaysConfirmChanged(true))
                val state = awaitItem()
                assertTrue(state.alwaysConfirm)
                val persisted = settingsRepo.alwaysConfirm.first()
                assertTrue(persisted)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `AlwaysConfirmChanged false persists to settings repository`() =
        runTest {
            settingsRepo.setAlwaysConfirm(true)
            vm = ThresholdsViewModel(settingsRepo)
            vm.uiState.test {
                awaitItem()
                vm.onEvent(ThresholdsEvent.AlwaysConfirmChanged(false))
                val state = awaitItem()
                assertFalse(state.alwaysConfirm)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `initial state reflects persisted DataStore values`() =
        runTest {
            settingsRepo.setExecuteThreshold(0.95f)
            settingsRepo.setConfirmThreshold(0.7f)
            settingsRepo.setAlwaysConfirm(true)
            vm = ThresholdsViewModel(settingsRepo)
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(0.95f, state.executeThreshold)
                assertEquals(0.7f, state.confirmThreshold)
                assertTrue(state.alwaysConfirm)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `FakeSettingsRepository clamps executeThreshold to 1 when above max`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(ThresholdsEvent.ExecuteThresholdChanged(1.5f))
                val state = awaitItem()
                // FakeSettingsRepository delegates to coerceIn(0f, 1f)
                assertEquals(1.0f, state.executeThreshold)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `FakeSettingsRepository clamps confirmThreshold to executeThreshold when above it`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                // Execute is 0.85 by default; setting confirm above it should clamp.
                vm.onEvent(ThresholdsEvent.ConfirmThresholdChanged(0.95f))
                val state = awaitItem()
                // Clamped to executeThreshold (0.85)
                assertEquals(ThresholdsUiState.DEFAULT_EXECUTE, state.confirmThreshold)
                cancelAndConsumeRemainingEvents()
            }
        }
}
