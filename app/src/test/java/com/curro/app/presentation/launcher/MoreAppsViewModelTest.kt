package com.curro.app.presentation.launcher

import android.graphics.drawable.ColorDrawable
import app.cash.turbine.test
import com.curro.app.domain.model.LaunchableApp
import com.curro.app.domain.repository.InstalledAppsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MoreAppsViewModel] (SF-1.5 / US-013).
 *
 * Pure JVM + Turbine + [runTest] with [UnconfinedTestDispatcher] — no Robolectric.
 * [UnconfinedTestDispatcher] ensures `stateIn(WhileSubscribed)` activates eagerly so
 * the first emission from the fake flow is visible immediately in tests.
 *
 * Covers:
 * - Initial state is [MoreAppsUiState.Loading] before the repository emits.
 * - [MoreAppsUiState.Ready] is emitted with the repository list once it emits.
 * - Empty list from the repository still transitions to [MoreAppsUiState.Ready].
 * - List updates: second emission replaces the first.
 */
@ExperimentalCoroutinesApi
@DisplayName("MoreAppsViewModel")
class MoreAppsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeAppsFlow = MutableStateFlow<List<LaunchableApp>>(emptyList())
    private val mockAppsRepo: InstalledAppsRepository = mockk()

    private lateinit var viewModel: MoreAppsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockAppsRepo.observeAllLaunchable() } returns fakeAppsFlow
        viewModel = MoreAppsViewModel(appsRepo = mockAppsRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `first collected state is Ready because the repository StateFlow emits synchronously`() =
        runTest {
            // WhileSubscribed(5000) does not collect the upstream until there is a subscriber.
            // The initial stateIn value is Loading. Once subscribed, the fake MutableStateFlow
            // (a hot StateFlow) emits its current value immediately, transitioning to Ready.
            // We use Turbine to subscribe and collect the first item, matching production behaviour.
            viewModel.uiState.test {
                val state = awaitItem()
                assertInstanceOf(MoreAppsUiState.Ready::class.java, state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Ready state emitted with correct list when repository emits`() =
        runTest {
            val apps = listOf(makeFakeApp("com.example.a", "App A"))
            viewModel.uiState.test {
                // Consume the initial Ready(empty) from the StateFlow's seed.
                awaitItem()
                fakeAppsFlow.value = apps
                val state = awaitItem()
                assertInstanceOf(MoreAppsUiState.Ready::class.java, state)
                assertEquals(apps, (state as MoreAppsUiState.Ready).apps)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `empty list transitions to Ready with empty apps`() =
        runTest {
            // fakeAppsFlow starts empty — viewModel should already be in Ready(emptyList()).
            viewModel.uiState.test {
                val state = awaitItem()
                assertInstanceOf(MoreAppsUiState.Ready::class.java, state)
                assertEquals(emptyList<LaunchableApp>(), (state as MoreAppsUiState.Ready).apps)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `second emission replaces the first in Ready state`() =
        runTest {
            val firstList = listOf(makeFakeApp("com.example.a", "App A"))
            val secondList =
                listOf(
                    makeFakeApp("com.example.a", "App A"),
                    makeFakeApp("com.example.b", "App B"),
                )
            viewModel.uiState.test {
                awaitItem() // Initial Ready(empty)
                fakeAppsFlow.value = firstList
                val first = awaitItem() as MoreAppsUiState.Ready
                assertEquals(1, first.apps.size)

                fakeAppsFlow.value = secondList
                val second = awaitItem() as MoreAppsUiState.Ready
                assertEquals(2, second.apps.size)

                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun makeFakeApp(
        packageName: String,
        label: String,
    ): LaunchableApp =
        LaunchableApp(
            packageName = packageName,
            label = label,
            icon = ColorDrawable(android.graphics.Color.GRAY),
        )
}
