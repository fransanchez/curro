package com.curro.app.presentation.launcher

import android.content.Context
import app.cash.turbine.test
import com.curro.app.R
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.data.permissions.PermissionGate
import com.curro.app.domain.model.ClockState
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.repository.FavoriteAppsRepository
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TtsClient
import com.curro.app.domain.usecase.ObserveClockUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Unit tests for [LauncherViewModel].
 *
 * Pure JVM + Turbine + [runTest] with [UnconfinedTestDispatcher]. SF-2.3 (US-017)
 * extends the suite with the voice-loop transitions (T1–T10 in the brief's §10), using
 * mockk fakes for [SttClient], [TtsClient] and [PermissionGate]. The
 * `unitTests.isReturnDefaultValues = true` config in app/build.gradle.kts lets the
 * ViewModel call `appContext.getString(resId)` without Robolectric — the calls return
 * empty strings, which the tests don't assert on (the transition path matters here,
 * not the literal message).
 */
@ExperimentalCoroutinesApi
@DisplayName("LauncherViewModel")
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

    private val sttEvents = MutableSharedFlow<SttClient.Event>(extraBufferCapacity = 16)
    private val sttClient: SttClient = mockk(relaxed = true)
    private val ttsClient: TtsClient = mockk(relaxed = true)
    private val permissionGate: PermissionGate = mockk()
    private val appContext: Context = mockk(relaxed = true)

    private lateinit var viewModel: LauncherViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockObserveClock() } returns fakeClockFlow
        every { mockFavoritesRepo.observeFavorites() } returns fakeFavoritesFlow
        every { sttClient.listen() } returns sttEvents
        coEvery { ttsClient.speak(any(), any()) } returns TtsClient.SpeakResult.Completed
        every { ttsClient.stop() } returns Unit
        every { permissionGate.isGranted() } returns true
        every { appContext.getString(any<Int>()) } returns ""

        viewModel = newViewModel()
    }

    private fun newViewModel() =
        LauncherViewModel(
            detector = fakeDetector,
            observeClock = mockObserveClock,
            favoritesRepo = mockFavoritesRepo,
            sttClient = sttClient,
            ttsClient = ttsClient,
            permissionGate = permissionGate,
            appContext = appContext,
        )

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state + clock + detector (pre-existing SF-1.1/1.2/1.4/1.6 tests) ──

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
    fun `initial uiState listeningState is Idle`() =
        runTest {
            assertEquals(ListeningState.Idle, viewModel.uiState.value.listeningState)
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

    // ── SF-2.3 (US-017) — voice loop ─────────────────────────────────────────

    @Nested
    @DisplayName("SF-2.3 — voice loop")
    inner class VoiceLoopTests {
        /**
         * The uiState is backed by `stateIn(WhileSubscribed)` — its `.value` does not
         * reflect upstream changes unless a subscriber is collecting. The tests
         * subscribe inside `viewModel.uiState.test { … }` (via Turbine) and assert on
         * the latest emitted state. The TestScope's `UnconfinedTestDispatcher` makes
         * the propagation synchronous.
         */

        @Test
        fun `T1 — MicPressed with permission granted enters Starting`() =
            runTest {
                every { permissionGate.isGranted() } returns true

                viewModel.uiState.test {
                    awaitItem() // initial
                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()
                    val state = expectMostRecentItem().listeningState
                    assertTrue(
                        state is ListeningState.Starting || state is ListeningState.Listening,
                        "Expected Starting/Listening, got $state",
                    )
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `T2 — MicPressed with permission denied emits RequestRecordAudio and stays Idle`() =
            runTest {
                every { permissionGate.isGranted() } returns false

                viewModel.sideEffects.test {
                    viewModel.onEvent(LauncherEvent.MicPressed)
                    val effect = awaitItem()
                    assertTrue(effect is LauncherSideEffect.RequestRecordAudio)
                    cancelAndIgnoreRemainingEvents()
                }
                viewModel.uiState.test {
                    assertEquals(ListeningState.Idle, expectMostRecentItem().listeningState)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `T3 — RecordAudioPermissionResult granted starts listening`() =
            runTest {
                viewModel.uiState.test {
                    awaitItem()
                    viewModel.onEvent(LauncherEvent.RecordAudioPermissionResult(true))
                    advanceUntilIdle()
                    val state = expectMostRecentItem().listeningState
                    assertTrue(
                        state is ListeningState.Starting || state is ListeningState.Listening,
                        "Expected Starting/Listening, got $state",
                    )
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `T4 — RecordAudioPermissionResult denied surfaces Error then resets to Idle`() =
            runTest {
                // speak() suspends so we observe the Error state before the 2.5s reset.
                val speakGate = kotlinx.coroutines.CompletableDeferred<TtsClient.SpeakResult>()
                coEvery { ttsClient.speak(any(), any()) } coAnswers { speakGate.await() }

                viewModel.uiState.test {
                    awaitItem()
                    viewModel.onEvent(LauncherEvent.RecordAudioPermissionResult(false))
                    advanceUntilIdle()
                    assertTrue(expectMostRecentItem().listeningState is ListeningState.Error)

                    // Release speak() so the 2.5s delay runs.
                    speakGate.complete(TtsClient.SpeakResult.Completed)
                    advanceTimeBy(2_600)
                    advanceUntilIdle()
                    assertEquals(ListeningState.Idle, expectMostRecentItem().listeningState)
                    cancelAndIgnoreRemainingEvents()
                }
                coVerify { ttsClient.speak(any(), any()) }
            }

        @Test
        fun `T5 — STT Partial puts state into Listening with the partial text`() =
            runTest {
                viewModel.uiState.test {
                    awaitItem()
                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()
                    sttEvents.emit(SttClient.Event.Partial("hola"))
                    advanceUntilIdle()

                    val state = expectMostRecentItem().listeningState
                    assertTrue(state is ListeningState.Listening, "Got $state")
                    assertEquals("hola", (state as ListeningState.Listening).partialText)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `T6 — STT Final transitions to Speaking, speaks the text, returns to Idle`() =
            runTest {
                viewModel.uiState.test {
                    awaitItem()
                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()
                    sttEvents.emit(SttClient.Event.Final("hola curro"))
                    advanceUntilIdle()

                    assertEquals(ListeningState.Idle, expectMostRecentItem().listeningState)
                    cancelAndIgnoreRemainingEvents()
                }
                coVerify { ttsClient.speak("hola curro", any()) }
            }

        @Test
        fun `T7 — STT Failed surfaces Error and after 2_5s returns to Idle`() =
            runTest {
                val speakGate = kotlinx.coroutines.CompletableDeferred<TtsClient.SpeakResult>()
                coEvery { ttsClient.speak(any(), any()) } coAnswers { speakGate.await() }

                viewModel.uiState.test {
                    awaitItem()
                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()
                    sttEvents.emit(SttClient.Event.Failed(CurroError.SttNoMatch))
                    advanceUntilIdle()

                    assertTrue(expectMostRecentItem().listeningState is ListeningState.Error)
                    speakGate.complete(TtsClient.SpeakResult.Completed)
                    advanceTimeBy(2_600)
                    advanceUntilIdle()
                    assertEquals(ListeningState.Idle, expectMostRecentItem().listeningState)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `T8 — MicPressed while Listening cancels and restarts`() =
            runTest {
                viewModel.uiState.test {
                    awaitItem()
                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()
                    sttEvents.emit(SttClient.Event.Partial("hello"))
                    advanceUntilIdle()
                    assertTrue(expectMostRecentItem().listeningState is ListeningState.Listening)

                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()

                    val state = expectMostRecentItem().listeningState
                    assertTrue(
                        state is ListeningState.Starting || state is ListeningState.Listening,
                        "Expected restart after barge-in, got $state",
                    )
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `T9 — MicPressed while Speaking cancels TTS and restarts`() =
            runTest {
                coEvery { ttsClient.speak(any(), any()) } coAnswers {
                    kotlinx.coroutines.delay(Long.MAX_VALUE)
                    TtsClient.SpeakResult.Completed
                }

                viewModel.uiState.test {
                    awaitItem()
                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()
                    sttEvents.emit(SttClient.Event.Final("hola"))
                    advanceUntilIdle()
                    assertTrue(expectMostRecentItem().listeningState is ListeningState.Speaking)

                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()

                    val state = expectMostRecentItem().listeningState
                    assertTrue(
                        state is ListeningState.Starting || state is ListeningState.Listening,
                        "Expected restart after barge-in, got $state",
                    )
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `T10 — voiceJob cancellation propagates to the in-flight TTS`() =
            runTest {
                coEvery { ttsClient.speak(any(), any()) } coAnswers {
                    kotlinx.coroutines.delay(Long.MAX_VALUE)
                    TtsClient.SpeakResult.Completed
                }

                viewModel.uiState.test {
                    awaitItem()
                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()
                    sttEvents.emit(SttClient.Event.Final("hola"))
                    advanceUntilIdle()

                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()

                    // Previous Speaking gone; cancellation has reached the speak coroutine.
                    assertTrue(expectMostRecentItem().listeningState !is ListeningState.Speaking)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun `errorMessage maps SttVoicePackMissing to copy_stt_no_voice_pack`() =
            runTest {
                val speakGate = kotlinx.coroutines.CompletableDeferred<TtsClient.SpeakResult>()
                coEvery { ttsClient.speak(any(), any()) } coAnswers { speakGate.await() }

                viewModel.uiState.test {
                    awaitItem()
                    viewModel.onEvent(LauncherEvent.MicPressed)
                    advanceUntilIdle()
                    sttEvents.emit(SttClient.Event.Failed(CurroError.SttVoicePackMissing))
                    advanceUntilIdle()

                    assertTrue(expectMostRecentItem().listeningState is ListeningState.Error)
                    cancelAndIgnoreRemainingEvents()
                }
                io.mockk.verify { appContext.getString(R.string.copy_stt_no_voice_pack) }
                speakGate.complete(TtsClient.SpeakResult.Completed)
            }
    }
}
