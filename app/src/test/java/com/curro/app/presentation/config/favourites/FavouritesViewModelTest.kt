package com.curro.app.presentation.config.favourites

import app.cash.turbine.test
import com.curro.app.assistant.FakeSettingsRepository
import com.curro.app.util.FakeInstalledAppsRepository
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-8.3 (US-052) — [FavouritesViewModel] unit tests.
 *
 * Uses [FakeInstalledAppsRepository] and [FakeSettingsRepository].
 * No Android dependencies — runs on JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("FavouritesViewModel (SF-8.3)")
class FavouritesViewModelTest {
    private lateinit var installedAppsRepo: FakeInstalledAppsRepository
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var vm: FavouritesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        installedAppsRepo = FakeInstalledAppsRepository()
        settingsRepo = FakeSettingsRepository()
        vm = FavouritesViewModel(installedAppsRepo, settingsRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has null selectedPackages and no unsaved changes`() =
        runTest {
            vm.uiState.test {
                val state = awaitItem()
                assertNull(state.selectedPackages)
                assertFalse(state.hasUnsavedChanges)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `AppToggled adds package to selectedPackages and marks unsaved`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(FavouritesEvent.AppToggled("com.whatsapp"))
                val state = awaitItem()
                assertTrue(state.selectedPackages?.contains("com.whatsapp") == true)
                assertTrue(state.hasUnsavedChanges)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `AppToggled removes package when already selected`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(FavouritesEvent.AppToggled("com.whatsapp"))
                awaitItem()
                vm.onEvent(FavouritesEvent.AppToggled("com.whatsapp"))
                val state = awaitItem()
                assertTrue(state.selectedPackages?.contains("com.whatsapp") == false)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `AppToggled ignores tap when max slots already selected`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                // Fill 4 slots.
                vm.onEvent(FavouritesEvent.AppToggled("a"))
                awaitItem()
                vm.onEvent(FavouritesEvent.AppToggled("b"))
                awaitItem()
                vm.onEvent(FavouritesEvent.AppToggled("c"))
                awaitItem()
                vm.onEvent(FavouritesEvent.AppToggled("d"))
                awaitItem()
                // Try to add a 5th — should be ignored.
                vm.onEvent(FavouritesEvent.AppToggled("e"))
                expectNoEvents() // no new emission
                val state = vm.uiState.value
                assertEquals(4, state.selectedPackages?.size)
                assertTrue(state.selectedPackages?.contains("e") == false)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `UseAutomatic sets selectedPackages to null and marks unsaved`() =
        runTest {
            // First set some packages.
            settingsRepo.setLauncherFavouritesOverride(listOf("com.a"))
            vm = FavouritesViewModel(installedAppsRepo, settingsRepo) // re-create after persisted change
            vm.uiState.test {
                awaitItem()
                vm.onEvent(FavouritesEvent.UseAutomatic)
                val state = awaitItem()
                assertNull(state.selectedPackages)
                assertTrue(state.hasUnsavedChanges)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `Save persists selectedPackages to settingsRepo`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(FavouritesEvent.AppToggled("com.x"))
                awaitItem()
                vm.onEvent(FavouritesEvent.Save)
                val state = awaitItem()
                assertFalse(state.hasUnsavedChanges)
                // Verify persisted to the repo.
                val lastValue = settingsRepo.launcherFavouritesOverride.first()
                assertEquals(listOf("com.x"), lastValue)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `selectedPackages reflects DataStore override on init`() =
        runTest {
            settingsRepo.setLauncherFavouritesOverride(listOf("com.whatsapp", "com.android.dialer"))
            vm = FavouritesViewModel(installedAppsRepo, settingsRepo)
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(listOf("com.whatsapp", "com.android.dialer"), state.selectedPackages)
                assertFalse(state.hasUnsavedChanges)
                cancelAndConsumeRemainingEvents()
            }
        }
}
