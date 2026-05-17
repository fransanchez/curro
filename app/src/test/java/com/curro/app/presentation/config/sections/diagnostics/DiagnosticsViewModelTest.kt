package com.curro.app.presentation.config.sections.diagnostics

import app.cash.turbine.test
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.data.permissions.GrantedPermissionsReader
import com.curro.app.data.permissions.PermissionInfo
import com.curro.app.domain.repository.EngineMetrics
import io.mockk.coEvery
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-8.10 (US-059) — [DiagnosticsViewModel] unit tests.
 *
 * Uses Mockk for [EngineMetrics], [DefaultLauncherDetector], and [GrantedPermissionsReader]
 * to avoid Android platform dependencies. [ProcessLifecycleOwner] is not involved — the
 * [refreshTrigger] is never exercised from the lifecycle in these JVM tests (the constructor's
 * try/catch guards it), so we rely on the `onStart { emit(Unit) }` emission instead.
 *
 * No Android dependencies — runs on JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("DiagnosticsViewModel (SF-8.10)")
class DiagnosticsViewModelTest {
    private lateinit var engineMetrics: EngineMetrics
    private lateinit var detector: DefaultLauncherDetector
    private lateinit var permissionsReader: GrantedPermissionsReader
    private val detectorFlow = MutableStateFlow(false)

    private fun buildVm(): DiagnosticsViewModel =
        DiagnosticsViewModel(
            engineMetrics = engineMetrics,
            detector = detector,
            permissionsReader = permissionsReader,
            context = mockk(relaxed = true),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        engineMetrics = mockk()
        detector = mockk()
        permissionsReader = mockk()

        every { detector.flow } returns detectorFlow
        every { engineMetrics.isReady() } returns false
        every { engineMetrics.modelName() } returns "FunctionGemma270M"
        coEvery { engineMetrics.lastWarmUpLatencyMs() } returns null
        coEvery { engineMetrics.lastInferenceLatencyMs() } returns null
        every { permissionsReader.snapshot() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState emits current metrics on init`() =
        runTest {
            every { engineMetrics.isReady() } returns true
            coEvery { engineMetrics.lastWarmUpLatencyMs() } returns 320L
            coEvery { engineMetrics.lastInferenceLatencyMs() } returns 180L
            every { engineMetrics.modelName() } returns "FunctionGemma270M"

            val vm = buildVm()
            vm.uiState.test {
                val state = awaitItem()
                assertEquals("FunctionGemma270M", state.model.name)
                assertEquals(ModelState.Loaded, state.model.state)
                assertEquals(320L, state.model.lastWarmUpMs)
                assertEquals(180L, state.model.lastInferenceMs)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState reacts to defaultLauncherDetector emission`() =
        runTest {
            val vm = buildVm()
            vm.uiState.test {
                awaitItem() // initial false
                detectorFlow.value = true
                val updated = awaitItem()
                assertTrue(updated.isDefaultLauncher)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState modelState is Cold when not ready`() =
        runTest {
            every { engineMetrics.isReady() } returns false

            val vm = buildVm()
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(ModelState.Cold, state.model.state)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState modelState is Warming when ready but no inference latency yet`() =
        runTest {
            every { engineMetrics.isReady() } returns true
            coEvery { engineMetrics.lastInferenceLatencyMs() } returns null

            val vm = buildVm()
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(ModelState.Warming, state.model.state)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState modelState is Loaded when ready and has inference latency`() =
        runTest {
            every { engineMetrics.isReady() } returns true
            coEvery { engineMetrics.lastWarmUpLatencyMs() } returns 300L
            coEvery { engineMetrics.lastInferenceLatencyMs() } returns 150L

            val vm = buildVm()
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(ModelState.Loaded, state.model.state)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState permissions come from permissionsReader snapshot`() =
        runTest {
            val fakePermissions =
                listOf(
                    PermissionInfo("RECORD_AUDIO", labelResId = 0, isGranted = true),
                    PermissionInfo("READ_CONTACTS", labelResId = 0, isGranted = false),
                )
            every { permissionsReader.snapshot() } returns fakePermissions

            val vm = buildVm()
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(2, state.permissions.size)
                assertTrue(state.permissions[0].isGranted)
                assertFalse(state.permissions[1].isGranted)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState isDefaultLauncher is false initially when detector emits false`() =
        runTest {
            val vm = buildVm()
            vm.uiState.test {
                val state = awaitItem()
                assertFalse(state.isDefaultLauncher)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState warmUpMs is null before any warm-up`() =
        runTest {
            coEvery { engineMetrics.lastWarmUpLatencyMs() } returns null

            val vm = buildVm()
            vm.uiState.test {
                val state = awaitItem()
                assertNull(state.model.lastWarmUpMs)
                cancelAndConsumeRemainingEvents()
            }
        }
}
