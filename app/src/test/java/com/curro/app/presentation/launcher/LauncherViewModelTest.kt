package com.curro.app.presentation.launcher

import app.cash.turbine.test
import com.curro.app.assistant.AssistantCoordinator
import com.curro.app.assistant.AssistantSideEffect
import com.curro.app.assistant.AssistantState
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.data.telephony.IncomingCallModeToggleHandler
import com.curro.app.domain.model.ClockState
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.repository.FavoriteAppsRepository
import com.curro.app.domain.usecase.ObserveClockUseCase
import com.curro.app.util.FakeIncomingCallModeController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * SF-5.2 / US-036 — thinned [LauncherViewModel] tests.
 *
 * The pre-refactor VM owned the whole voice pipeline (STT/TTS/model/handler/permission glue);
 * the SF-5.2 refactor moves all of that into [AssistantCoordinator]. The VM is now a thin
 * observer of `coordinator.state` plus the launcher-only state (clock, default-home,
 * favourites, notification-access). Per the brief's §13.2 deletion list, every assertion
 * on the deleted `ListeningState` shape, the STT/TTS races, the auto-retry permission
 * flow, and the decision telemetry has migrated to `AssistantCoordinatorTest`.
 *
 * **Kept (launcher concerns):**
 * - Initial state / clock / detector observation
 * - SF-1.4 — `AppTileTapped` → `LaunchApp`
 * - SF-1.6 — five-tap clock gesture
 * - SF-4.6 — `GrantNotifAccessRequested` → `OpenNotificationAccessSettings`
 * - SF-5.2 — `MicPressed` forwards to `coordinator.onMicPressed()`
 * - SF-5.2 — `coordinator.state` shows through to `uiState.assistantState`
 * - SF-5.2 — `AssistantSideEffect.RequestPermission` adapts to the right `LauncherSideEffect.Request*`
 */
@ExperimentalCoroutinesApi
@DisplayName("LauncherViewModel (Phase 5)")
class LauncherViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeDetectorFlow = MutableStateFlow(false)
    private val fakeClockFlow = MutableStateFlow(ClockState(timeText = "--:--", dateText = ""))
    private val fakeFavoritesFlow = MutableStateFlow(emptyList<FavoriteApp>())

    private val fakeDetector =
        object : DefaultLauncherDetector {
            override fun isDefault(): Boolean = false

            override val flow: Flow<Boolean>
                get() = fakeDetectorFlow
        }

    private val mockObserveClock: ObserveClockUseCase = mockk()
    private val mockFavoritesRepo: FavoriteAppsRepository = mockk()
    private val notifGate: NotificationAccessGate = mockk()
    private val coordinator: AssistantCoordinator = mockk(relaxed = true)
    private val coordinatorStateFlow = MutableStateFlow<AssistantState>(AssistantState.Idle)
    private val coordinatorSideEffects = MutableSharedFlow<AssistantSideEffect>(extraBufferCapacity = 16)
    private val sideEffectBus = LauncherSideEffectBus()
    private val incomingCallController = FakeIncomingCallModeController()
    private val incomingCallToggleHandler = IncomingCallModeToggleHandler(incomingCallController, sideEffectBus)

    private lateinit var viewModel: LauncherViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockObserveClock() } returns fakeClockFlow
        every { mockFavoritesRepo.observeFavorites() } returns fakeFavoritesFlow
        every { notifGate.isGranted() } returns true
        every { coordinator.state } returns coordinatorStateFlow
        every { coordinator.sideEffects } returns coordinatorSideEffects

        viewModel = newViewModel()
    }

    private fun newViewModel() =
        LauncherViewModel(
            detector = fakeDetector,
            observeClock = mockObserveClock,
            favoritesRepo = mockFavoritesRepo,
            coordinator = coordinator,
            notifGate = notifGate,
            sideEffectBus = sideEffectBus,
            incomingCallToggleHandler = incomingCallToggleHandler,
        )

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state + clock + detector ────────────────────────────────────

    @Test
    fun `initial uiState has isCurroDefault false`() =
        runTest {
            assertFalse(viewModel.uiState.value.isCurroDefault)
        }

    @Test
    fun `initial uiState clock timeText is the placeholder`() =
        runTest {
            assertEquals("--:--", viewModel.uiState.value.clock.timeText)
        }

    @Test
    fun `initial uiState clock is not null`() =
        runTest {
            assertNotNull(viewModel.uiState.value.clock)
        }

    @Test
    fun `initial uiState assistantState is Idle`() =
        runTest {
            assertEquals(AssistantState.Idle, viewModel.uiState.value.assistantState)
        }

    @Test
    fun `uiState isCurroDefault becomes true when detector emits true`() =
        runTest {
            viewModel.uiState.test {
                assertFalse(awaitItem().isCurroDefault)
                fakeDetectorFlow.emit(true)
                assertEquals(true, awaitItem().isCurroDefault)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState isCurroDefault returns to false when detector emits false after true`() =
        runTest {
            viewModel.uiState.test {
                assertFalse(awaitItem().isCurroDefault)
                fakeDetectorFlow.emit(true)
                assertEquals(true, awaitItem().isCurroDefault)
                fakeDetectorFlow.emit(false)
                assertEquals(false, awaitItem().isCurroDefault)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState clock updates when ObserveClockUseCase emits a new ClockState`() =
        runTest {
            val newClock = ClockState(timeText = "14:30", dateText = "Jueves 14 mayo")
            viewModel.uiState.test {
                awaitItem()
                fakeClockFlow.emit(newClock)
                assertEquals(newClock, awaitItem().clock)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isCurroDefault is preserved when only the clock updates`() =
        runTest {
            viewModel.uiState.test {
                awaitItem()
                fakeDetectorFlow.emit(true)
                assertEquals(true, awaitItem().isCurroDefault)
                fakeClockFlow.emit(ClockState(timeText = "09:00", dateText = "Viernes 15 mayo"))
                assertEquals(true, awaitItem().isCurroDefault)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `LauncherUiState with same fields are equal`() =
        runTest {
            val clock = ClockState("12:00", "Lunes 11 mayo")
            val a = LauncherUiState(isCurroDefault = true, clock = clock)
            val b = LauncherUiState(isCurroDefault = true, clock = clock)
            assertEquals(a, b)
        }

    // ── SF-1.4: AppTileTapped ────────────────────────────────────────────────

    @Nested
    @DisplayName("SF-1.4 — AppTileTapped event")
    inner class AppTileTappedTests {
        @Test
        fun `AppTileTapped event emits LaunchApp side effect with correct package`() =
            runTest {
                val pkg = "com.whatsapp"
                viewModel.sideEffects.test {
                    viewModel.onEvent(LauncherEvent.AppTileTapped(pkg))
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.LaunchApp)
                    assertEquals(pkg, (effect as LauncherSideEffect.LaunchApp).packageName)
                    cancelAndIgnoreRemainingEvents()
                }
            }
    }

    // ── SF-1.6: five-tap clock ───────────────────────────────────────────────

    @Nested
    @DisplayName("SF-1.6 — ClockTapped five-tap gesture")
    inner class ClockTappedTests {
        @Test
        fun `5 rapid ClockTapped events emit OpenConfig`() =
            runTest {
                viewModel.sideEffects.test {
                    repeat(5) { viewModel.onEvent(LauncherEvent.ClockTapped) }
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.OpenConfig)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `4 ClockTapped events do not emit OpenConfig`() =
            runTest {
                viewModel.sideEffects.test {
                    repeat(4) { viewModel.onEvent(LauncherEvent.ClockTapped) }
                    advanceTimeBy(500)
                    expectNoEvents()
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `counter resets after OpenConfig so a new 5-tap sequence works`() =
            runTest {
                viewModel.sideEffects.test {
                    repeat(5) { viewModel.onEvent(LauncherEvent.ClockTapped) }
                    assertTrue(awaitItem() is LauncherSideEffect.OpenConfig)
                    repeat(5) { viewModel.onEvent(LauncherEvent.ClockTapped) }
                    assertTrue(awaitItem() is LauncherSideEffect.OpenConfig)
                    cancelAndIgnoreRemainingEvents()
                }
            }
    }

    // ── SF-4.6: notification-access CTA ──────────────────────────────────────

    @Nested
    @DisplayName("SF-4.6 — GrantNotifAccessRequested")
    inner class GrantNotifAccessRequestedTests {
        @Test
        fun `GrantNotifAccessRequested emits OpenNotificationAccessSettings`() =
            runTest {
                viewModel.sideEffects.test {
                    viewModel.onEvent(LauncherEvent.GrantNotifAccessRequested)
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.OpenNotificationAccessSettings)
                    cancelAndIgnoreRemainingEvents()
                }
            }
    }

    // ── SF-7.4: AppTileTapped bump invariant ────────────────────────────────

    /**
     * SF-7.4 invariant: [LauncherViewModel] does NOT call [AppUsageDao.upsert] directly.
     * The bump lives inside [com.curro.app.data.apps.IntentAppLauncher.launch]; the VM
     * delegates to [AppLauncher] via [LauncherSideEffect.LaunchApp].
     *
     * The VM emits [LauncherSideEffect.LaunchApp] → the screen's LaunchedEffect calls
     * [AppLauncher.launch] → [AppUsageBumper.bumpAsync] fires. The VM is NOT the bump call site.
     */
    @Test
    fun `SF-7_4 AppTileTapped emits LaunchApp but VM does not interact with AppUsageDao`() =
        runTest {
            // The VM has no reference to AppUsageDao — if it tried to use one, there is
            // nothing to inject and the test would fail to compile or crash.
            // Here we just verify the side effect is the single observable interaction.
            viewModel.sideEffects.test {
                viewModel.onEvent(LauncherEvent.AppTileTapped("com.whatsapp"))
                val effect = awaitItem()
                assertTrue(effect is LauncherSideEffect.LaunchApp)
                assertEquals("com.whatsapp", (effect as LauncherSideEffect.LaunchApp).packageName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── SF-5.2: VM observes coordinator ──────────────────────────────────────

    @Nested
    @DisplayName("SF-5.2 — VM observes coordinator")
    inner class CoordinatorObservationTests {
        @Test
        fun `MicPressed forwards to coordinator onMicPressed`() =
            runTest {
                viewModel.onEvent(LauncherEvent.MicPressed)
                verify { coordinator.onMicPressed() }
            }

        @Test
        fun `RecordAudioPermissionResult forwards to coordinator onPermissionResult`() =
            runTest {
                viewModel.onEvent(LauncherEvent.RecordAudioPermissionResult(granted = true))
                verify { coordinator.onPermissionResult(android.Manifest.permission.RECORD_AUDIO, true) }
            }

        @Test
        fun `ReadContactsPermissionResult forwards to coordinator onPermissionResult`() =
            runTest {
                viewModel.onEvent(LauncherEvent.ReadContactsPermissionResult(granted = false))
                verify { coordinator.onPermissionResult(android.Manifest.permission.READ_CONTACTS, false) }
            }

        @Test
        fun `CallPhonePermissionResult forwards to coordinator onPermissionResult`() =
            runTest {
                viewModel.onEvent(LauncherEvent.CallPhonePermissionResult(granted = true))
                verify { coordinator.onPermissionResult(android.Manifest.permission.CALL_PHONE, true) }
            }

        @Test
        fun `coordinator state changes propagate to uiState assistantState`() =
            runTest {
                viewModel.uiState.test {
                    awaitItem() // initial Idle
                    coordinatorStateFlow.emit(AssistantState.Listening(partial = "hola", startedAtMs = 1L))
                    val emitted = awaitItem().assistantState
                    assertTrue(emitted is AssistantState.Listening)
                    assertEquals("hola", (emitted as AssistantState.Listening).partial)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `AssistantSideEffect RequestPermission RECORD_AUDIO adapts to RequestRecordAudio`() =
            runTest {
                viewModel.sideEffects.test {
                    coordinatorSideEffects.emit(
                        AssistantSideEffect.RequestPermission(android.Manifest.permission.RECORD_AUDIO),
                    )
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.RequestRecordAudio)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `AssistantSideEffect RequestPermission READ_CONTACTS adapts to RequestReadContacts`() =
            runTest {
                viewModel.sideEffects.test {
                    coordinatorSideEffects.emit(
                        AssistantSideEffect.RequestPermission(android.Manifest.permission.READ_CONTACTS),
                    )
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.RequestReadContacts)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `AssistantSideEffect RequestPermission CALL_PHONE adapts to RequestCallPhone`() =
            runTest {
                viewModel.sideEffects.test {
                    coordinatorSideEffects.emit(
                        AssistantSideEffect.RequestPermission(android.Manifest.permission.CALL_PHONE),
                    )
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.RequestCallPhone)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `AssistantSideEffect ShowDebugJson adapts to LauncherSideEffect ShowDebugJson`() =
            runTest {
                viewModel.sideEffects.test {
                    coordinatorSideEffects.emit(AssistantSideEffect.ShowDebugJson("{}"))
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.ShowDebugJson)
                    assertEquals("{}", (effect as LauncherSideEffect.ShowDebugJson).prettyJson)
                    cancelAndIgnoreRemainingEvents()
                }
            }
    }

    // ── SF-8.7 / US-056 — bus → channel bridge + PhonePermissionsResult event ─

    @Nested
    @DisplayName("SF-8.7 (US-056) — incoming-call mode wiring")
    inner class IncomingCallModeTests {
        @Test
        fun `bus emit RequestPhonePermissions is bridged to the launcher sideEffects channel`() =
            runTest {
                viewModel.sideEffects.test {
                    sideEffectBus.emit(LauncherSideEffect.RequestPhonePermissions)
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.RequestPhonePermissions)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `PhonePermissionsResult granted true forwards to toggle handler and enables controller`() =
            runTest {
                viewModel.onEvent(LauncherEvent.PhonePermissionsResult(grantedAll = true))
                assertTrue(incomingCallController.isComponentEnabled())
            }

        @Test
        fun `PhonePermissionsResult granted false leaves controller disabled and emits a toast`() =
            runTest {
                viewModel.sideEffects.test {
                    viewModel.onEvent(LauncherEvent.PhonePermissionsResult(grantedAll = false))
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.ShowToast)
                    assertEquals(
                        com.curro.app.R.string.copy_incoming_call_perm_needed,
                        (effect as LauncherSideEffect.ShowToast).messageResId,
                    )
                    assertFalse(incomingCallController.isComponentEnabled())
                    cancelAndIgnoreRemainingEvents()
                }
            }
    }
}
