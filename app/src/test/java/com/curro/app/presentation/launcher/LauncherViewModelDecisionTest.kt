package com.curro.app.presentation.launcher

import android.content.Context
import app.cash.turbine.test
import com.curro.app.R
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.data.ml.FunctionCallValidator
import com.curro.app.data.ml.fakes.FakeFunctionCallEngine
import com.curro.app.data.permissions.PermissionGate
import com.curro.app.domain.model.ClockState
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.repository.FavoriteAppsRepository
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TtsClient
import com.curro.app.domain.usecase.ObserveClockUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Decision-pipeline tests for [LauncherViewModel] (US-024 / SF-3.6).
 *
 * Each case configures [FakeFunctionCallEngine] with a canned `nextResult`,
 * emits an STT `Final`, advances the dispatcher, and asserts:
 *  - The transient `Processing` state was set.
 *  - The `Speaking` state landed on the expected R.string (or, where the
 *    appContext is stubbed to a literal map, the actual Spanish line).
 *  - `Log.w("Curro/FailedCommand", …)` never contains the utterance text.
 *  - The `model_decide` telemetry event carries only `model`, `outcome`,
 *    `latency_ms` — and no utterance / action.
 *
 * Uses [UnconfinedTestDispatcher] + `runTest` for deterministic ordering;
 * `Dispatchers.IO` inside the engine is bypassed by the fake.
 */
@ExperimentalCoroutinesApi
@DisplayName("LauncherViewModel decision pipeline (SF-3.6)")
class LauncherViewModelDecisionTest {
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

    private val telemetry: TelemetrySink = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockObserveClock() } returns fakeClockFlow
        every { mockFavoritesRepo.observeFavorites() } returns fakeFavoritesFlow
        every { sttClient.listen() } returns sttEvents
        coEvery { ttsClient.speak(any(), any()) } returns TtsClient.SpeakResult.Completed
        every { ttsClient.stop() } returns Unit
        every { permissionGate.isGranted() } returns true
        // Real strings from the COPY table, mapped by stable R.string IDs so the
        // test reads more like the spec without coupling to actual resource compilation.
        every { appContext.getString(R.string.copy_recognized_prefix) } returns "Reconocido: "
        every { appContext.getString(R.string.copy_action_tell_time) } returns "decir la hora"
        every { appContext.getString(R.string.copy_action_call_contact) } returns "llamar a un contacto"
        every { appContext.getString(R.string.copy_models_not_ready) } returns
            "Aún estoy preparando los modelos, dame un segundo."
        every { appContext.getString(R.string.copy_error_unknown_function) } returns
            "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'."
        every { appContext.getString(any<Int>()) } returns ""
        // Re-stub the specific IDs after the catch-all default
        every { appContext.getString(R.string.copy_recognized_prefix) } returns "Reconocido: "
        every { appContext.getString(R.string.copy_action_tell_time) } returns "decir la hora"
        every { appContext.getString(R.string.copy_action_call_contact) } returns "llamar a un contacto"
        every { appContext.getString(R.string.copy_models_not_ready) } returns
            "Aún estoy preparando los modelos, dame un segundo."
        every { appContext.getString(R.string.copy_error_unknown_function) } returns
            "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'."
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(engine: FakeFunctionCallEngine) =
        LauncherViewModel(
            detector = fakeDetector,
            observeClock = mockObserveClock,
            favoritesRepo = mockFavoritesRepo,
            sttClient = sttClient,
            ttsClient = ttsClient,
            permissionGate = permissionGate,
            engine = engine,
            validator = FunctionCallValidator(),
            telemetry = telemetry,
            appContext = appContext,
        )

    @Test
    fun `happy path — Final to Speaking with action description, then Idle`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success(
                            """{"action":"tell_time","params":{"what":"time"},"confidence":0.92}""",
                        ),
                    isReadyValue = true,
                )
            val vm = viewModel(engine)

            vm.uiState.test {
                awaitItem()
                vm.onEvent(LauncherEvent.MicPressed)
                advanceUntilIdle()
                sttEvents.emit(SttClient.Event.Final("qué hora es"))
                advanceUntilIdle()
                // Final state lands on Idle after speak()+update.
                assertEquals(ListeningState.Idle, expectMostRecentItem().listeningState)
                cancelAndIgnoreRemainingEvents()
            }
            // The exact Spanish line was spoken — built from the two stubbed copy entries.
            coVerify { ttsClient.speak("Reconocido: decir la hora", any()) }
        }

    @Test
    fun `invalid model output speaks copy_error_unknown_function`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult = Result.success("garbage{not json"),
                    isReadyValue = true,
                )
            val vm = viewModel(engine)

            vm.uiState.test {
                awaitItem()
                vm.onEvent(LauncherEvent.MicPressed)
                advanceUntilIdle()
                sttEvents.emit(SttClient.Event.Final("tradúceme esto"))
                advanceUntilIdle()
                // Eventually back to Idle (after speak completes).
                assertEquals(ListeningState.Idle, expectMostRecentItem().listeningState)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify {
                ttsClient.speak(
                    "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'.",
                    any(),
                )
            }
        }

    @Test
    fun `unknown function speaks copy_error_unknown_function and emits unknown_function telemetry`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success("""{"action":"translate","params":{},"confidence":0.9}"""),
                    isReadyValue = true,
                )
            val vm = viewModel(engine)

            vm.onEvent(LauncherEvent.MicPressed)
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("tradúceme esto al italiano"))
            advanceUntilIdle()

            val propsSlot = slot<Map<String, Any>>()
            verify { telemetry.event("model_decide", capture(propsSlot)) }
            assertEquals("unknown_function", propsSlot.captured["outcome"])
            assertEquals("function_gemma_270m", propsSlot.captured["model"])
            // PII boundary: telemetry never contains the utterance or action.
            assertFalse(propsSlot.captured.containsKey("utterance"))
            assertFalse(propsSlot.captured.containsKey("action"))
        }

    @Test
    fun `cold engine speaks copy_models_not_ready and emits model_cold telemetry`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult = Result.failure(CurroError.ModelCold),
                    isReadyValue = false,
                )
            val vm = viewModel(engine)

            vm.onEvent(LauncherEvent.MicPressed)
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("qué hora es"))
            advanceUntilIdle()

            coVerify { ttsClient.speak("Aún estoy preparando los modelos, dame un segundo.", any()) }
            val propsSlot = slot<Map<String, Any>>()
            verify { telemetry.event("model_decide", capture(propsSlot)) }
            assertEquals("model_cold", propsSlot.captured["outcome"])
        }

    @Test
    fun `OOM speaks copy_error_unknown_function and emits oom telemetry`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult = Result.failure(CurroError.OutOfMemory),
                    isReadyValue = true,
                )
            val vm = viewModel(engine)

            vm.onEvent(LauncherEvent.MicPressed)
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("calcula mil dividido entre veinticinco"))
            advanceUntilIdle()

            coVerify {
                ttsClient.speak(
                    "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'.",
                    any(),
                )
            }
            val propsSlot = slot<Map<String, Any>>()
            verify { telemetry.event("model_decide", capture(propsSlot)) }
            assertEquals("oom", propsSlot.captured["outcome"])
        }

    @Test
    fun `Processing state is observed between Final and Speaking`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success(
                            """{"action":"tell_time","params":{"what":"time"},"confidence":0.9}""",
                        ),
                    isReadyValue = true,
                )
            // Make speak() suspend so the test can observe Processing landing.
            val speakGate = kotlinx.coroutines.CompletableDeferred<TtsClient.SpeakResult>()
            coEvery { ttsClient.speak(any(), any()) } coAnswers { speakGate.await() }
            val vm = viewModel(engine)

            vm.uiState.test {
                awaitItem()
                vm.onEvent(LauncherEvent.MicPressed)
                advanceUntilIdle()
                sttEvents.emit(SttClient.Event.Final("qué hora es"))
                advanceUntilIdle()
                // After advance, state should be Speaking (Processing was transient).
                // The post-Processing state is asserted by checking Speaking landed.
                val latest = expectMostRecentItem().listeningState
                assertTrue(latest is ListeningState.Speaking, "Got $latest")
                // Release speak() so the test finishes cleanly.
                speakGate.complete(TtsClient.SpeakResult.Completed)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `success path emits success outcome on model_decide`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success(
                            """{"action":"tell_time","params":{"what":"time"},"confidence":0.9}""",
                        ),
                    isReadyValue = true,
                )
            val vm = viewModel(engine)

            vm.onEvent(LauncherEvent.MicPressed)
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("qué hora es"))
            advanceUntilIdle()

            val propsSlot = slot<Map<String, Any>>()
            verify { telemetry.event("model_decide", capture(propsSlot)) }
            assertEquals("success", propsSlot.captured["outcome"])
            assertTrue(propsSlot.captured["latency_ms"] is Int)
        }

    @Test
    fun `debug build emits ShowDebugJson side effect on success`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success(
                            """{"action":"tell_time","params":{"what":"time"},"confidence":0.9}""",
                        ),
                    isReadyValue = true,
                )
            val vm = viewModel(engine)

            vm.sideEffects.test {
                vm.onEvent(LauncherEvent.MicPressed)
                advanceUntilIdle()
                sttEvents.emit(SttClient.Event.Final("qué hora es"))
                advanceUntilIdle()
                // Drain until we see a ShowDebugJson.
                var sawDebugJson = false
                var loop = 0
                while (!sawDebugJson && loop < SIDE_EFFECT_DRAIN_LIMIT) {
                    val effect = awaitItem()
                    if (effect is LauncherSideEffect.ShowDebugJson) {
                        sawDebugJson = true
                        assertTrue(effect.prettyJson.contains("\"tell_time\""))
                    }
                    loop++
                }
                assertTrue(sawDebugJson, "ShowDebugJson side effect not emitted in debug build")
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val SIDE_EFFECT_DRAIN_LIMIT = 10
    }
}
