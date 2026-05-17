package com.curro.app.presentation.config.reset

import app.cash.turbine.test
import com.curro.app.assistant.FakeSettingsRepository
import com.curro.app.util.FakeAliasRepository
import com.curro.app.util.FakeFailedCommandLog
import com.curro.app.util.FakeFavoriteAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * SF-8.8 (US-058) — [ResetViewModel] unit tests.
 *
 * Covers initial state, dialog show/dismiss, and the four-way parallel reset
 * (aliases, app-usage, failed-command log, favourites override).
 *
 * No Android dependencies — runs on JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ResetViewModel (SF-8.8)")
class ResetViewModelTest {
    private lateinit var aliasRepo: FakeAliasRepository
    private lateinit var favRepo: FakeFavoriteAppsRepository
    private lateinit var failedLog: FakeFailedCommandLog
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var vm: ResetViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        aliasRepo = FakeAliasRepository()
        favRepo = FakeFavoriteAppsRepository()
        failedLog = FakeFailedCommandLog()
        settingsRepo = FakeSettingsRepository()
        vm = ResetViewModel(aliasRepo, favRepo, failedLog, settingsRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has dialog hidden and reset not complete`() =
        runTest {
            vm.uiState.test {
                val state = awaitItem()
                assertFalse(state.showConfirmDialog)
                assertFalse(state.resetComplete)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `ResetPressed shows confirmation dialog`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(ResetEvent.ResetPressed)
                val state = awaitItem()
                assertTrue(state.showConfirmDialog)
                assertFalse(state.resetComplete)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `DismissDialog hides confirmation dialog without resetting`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(ResetEvent.ResetPressed)
                awaitItem()
                vm.onEvent(ResetEvent.DismissDialog)
                val state = awaitItem()
                assertFalse(state.showConfirmDialog)
                assertFalse(state.resetComplete)
                cancelAndConsumeRemainingEvents()
            }
            // No repository touched after dismiss.
            assertFalse(aliasRepo.deleteAllInvoked)
            assertEquals(0, favRepo.clearUsageCallCount)
            assertTrue(failedLog.records.isEmpty())
        }

    @Test
    fun `ConfirmReset invokes deleteAll on alias repository`() =
        runTest {
            vm.onEvent(ResetEvent.ResetPressed)
            vm.onEvent(ResetEvent.ConfirmReset)
            vm.uiState.test {
                cancelAndConsumeRemainingEvents()
            }
            assertTrue(aliasRepo.deleteAllInvoked)
        }

    @Test
    fun `ConfirmReset invokes clearUsage on favorite-apps repository`() =
        runTest {
            vm.onEvent(ResetEvent.ResetPressed)
            vm.onEvent(ResetEvent.ConfirmReset)
            vm.uiState.test {
                cancelAndConsumeRemainingEvents()
            }
            assertEquals(1, favRepo.clearUsageCallCount)
        }

    @Test
    fun `ConfirmReset invokes deleteAll on failed-command log`() =
        runTest {
            vm.onEvent(ResetEvent.ResetPressed)
            vm.onEvent(ResetEvent.ConfirmReset)
            vm.uiState.test {
                cancelAndConsumeRemainingEvents()
            }
            // FakeFailedCommandLog.deleteAll clears records and the internal flow.
            assertTrue(failedLog.records.isEmpty())
        }

    @Test
    fun `ConfirmReset clears launcher-favourites override in settings`() =
        runTest {
            settingsRepo.setLauncherFavouritesOverride(listOf("com.example.app"))
            vm.onEvent(ResetEvent.ResetPressed)
            vm.onEvent(ResetEvent.ConfirmReset)
            vm.uiState.test {
                cancelAndConsumeRemainingEvents()
            }
            // SettingsRepository.launcherFavouritesOverride should be null after reset.
            settingsRepo.launcherFavouritesOverride.test {
                val value = awaitItem()
                assertTrue(value == null)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `ConfirmReset sets resetComplete true after all operations finish`() =
        runTest {
            vm.onEvent(ResetEvent.ConfirmReset)
            vm.uiState.test {
                cancelAndConsumeRemainingEvents()
            }
            assertTrue(vm.uiState.value.resetComplete)
        }

    @Test
    fun `ConfirmReset hides dialog before marking reset complete`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(ResetEvent.ResetPressed)
                awaitItem() // showConfirmDialog = true
                vm.onEvent(ResetEvent.ConfirmReset)
                cancelAndConsumeRemainingEvents()
            }
            val finalState = vm.uiState.value
            assertFalse(finalState.showConfirmDialog)
            assertTrue(finalState.resetComplete)
        }
}
