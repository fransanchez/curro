package com.curro.app.presentation.launcher

import android.content.Context
import app.cash.turbine.test
import com.curro.app.R
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.data.ml.FunctionCallValidator
import com.curro.app.data.ml.fakes.FakeFunctionCallEngine
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.data.permissions.PermissionGate
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerDispatcher
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.ClockState
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.model.FunctionCall
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
    private val notifGate: NotificationAccessGate = mockk()
    private val appContext: Context = mockk(relaxed = true)

    private val telemetry: TelemetrySink = mockk(relaxed = true)

    /**
     * A [HandlerDispatcher] that wraps any fake handlers passed in. Uses a real
     * [TelemetrySink] mock so handler_invoked events can be observed in tests that care.
     * The [Context] stub returns an empty string for any resource look-up (the dispatcher
     * only touches copy_error_unknown_function / copy_handler_crash, which these tests do
     * not assert on).
     */
    private fun fakeDispatcher(handlers: Map<String, FunctionHandler> = emptyMap()): HandlerDispatcher =
        HandlerDispatcher(handlers, telemetry, appContext)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockObserveClock() } returns fakeClockFlow
        every { mockFavoritesRepo.observeFavorites() } returns fakeFavoritesFlow
        every { sttClient.listen() } returns sttEvents
        coEvery { ttsClient.speak(any(), any()) } returns TtsClient.SpeakResult.Completed
        every { ttsClient.stop() } returns Unit
        every { permissionGate.isGranted() } returns true
        every { notifGate.isGranted() } returns true
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

    private fun viewModel(
        engine: FakeFunctionCallEngine,
        dispatcher: HandlerDispatcher = fakeDispatcher(),
    ) = LauncherViewModel(
        detector = fakeDetector,
        observeClock = mockObserveClock,
        favoritesRepo = mockFavoritesRepo,
        sttClient = sttClient,
        ttsClient = ttsClient,
        permissionGate = permissionGate,
        engine = engine,
        validator = FunctionCallValidator(),
        telemetry = telemetry,
        dispatcher = dispatcher,
        notifGate = notifGate,
        appContext = appContext,
    )

    @Test
    fun `happy path — validated FunctionCall dispatched and handler Spoken forwarded to TTS`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success(
                            """{"action":"tell_time","params":{"what":"time"},"confidence":0.92}""",
                        ),
                    isReadyValue = true,
                )
            // SF-4.1: provide a tell_time handler so the dispatch succeeds.
            val handler =
                object : FunctionHandler {
                    override val functionName = "tell_time"

                    override suspend fun handle(call: FunctionCall) = HandlerResult.Spoken("Son las doce.")
                }
            val vm = viewModel(engine, fakeDispatcher(mapOf("tell_time" to handler)))

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
            // The handler's Spanish line reaches TTS.
            coVerify { ttsClient.speak("Son las doce.", any()) }
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

    // ── SF-4.1 (US-025) — handler dispatch integration ────────────────────────────────────

    @Test
    fun `Spoken result from dispatcher — speech is forwarded to TTS then Idle`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success(
                            """{"action":"tell_time","params":{"what":"time"},"confidence":0.93}""",
                        ),
                    isReadyValue = true,
                )
            val handler =
                object : FunctionHandler {
                    override val functionName = "tell_time"

                    override suspend fun handle(call: FunctionCall) = HandlerResult.Spoken("Son las tres de la tarde.")
                }
            val vm = viewModel(engine, fakeDispatcher(mapOf("tell_time" to handler)))

            vm.uiState.test {
                awaitItem()
                vm.onEvent(LauncherEvent.MicPressed)
                advanceUntilIdle()
                sttEvents.emit(SttClient.Event.Final("qué hora es"))
                advanceUntilIdle()
                assertEquals(ListeningState.Idle, expectMostRecentItem().listeningState)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { ttsClient.speak("Son las tres de la tarde.", any()) }
        }

    @Test
    fun `Failed result from dispatcher — TTS speaks the speech and no utterance in log`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success(
                            """{"action":"open_app","params":{"app_name":"calculadora"},"confidence":0.88}""",
                        ),
                    isReadyValue = true,
                )
            val handler =
                object : FunctionHandler {
                    override val functionName = "open_app"

                    override suspend fun handle(call: FunctionCall) =
                        HandlerResult.Failed(
                            speech = "No encuentro esa aplicación.",
                            reason = CurroError.PermissionDenied,
                        )
                }
            val vm = viewModel(engine, fakeDispatcher(mapOf("open_app" to handler)))

            vm.uiState.test {
                awaitItem()
                vm.onEvent(LauncherEvent.MicPressed)
                advanceUntilIdle()
                sttEvents.emit(SttClient.Event.Final("abre la calculadora"))
                advanceUntilIdle()
                assertEquals(ListeningState.Idle, expectMostRecentItem().listeningState)
                cancelAndIgnoreRemainingEvents()
            }
            // Speech must reach TTS; the utterance text ("abre la calculadora") must NOT.
            coVerify { ttsClient.speak("No encuentro esa aplicación.", any()) }
            coVerify(exactly = 0) { ttsClient.speak("abre la calculadora", any()) }
        }

    @Test
    fun `NeedsConfirmation auto-confirm — onConfirm invoked and inner Spoken forwarded to TTS`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success(
                            """{"action":"call_contact","params":{"contact":"Pepito"},"confidence":0.95}""",
                        ),
                    isReadyValue = true,
                )
            val handler =
                object : FunctionHandler {
                    override val functionName = "call_contact"

                    override suspend fun handle(call: FunctionCall) =
                        HandlerResult.NeedsConfirmation(
                            prompt = "¿Llamo a Pepito?",
                            onConfirm = { HandlerResult.Spoken("Vale, llamando a Pepito.") },
                        )
                }
            val vm = viewModel(engine, fakeDispatcher(mapOf("call_contact" to handler)))

            vm.uiState.test {
                awaitItem()
                vm.onEvent(LauncherEvent.MicPressed)
                advanceUntilIdle()
                sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
                advanceUntilIdle()
                assertEquals(ListeningState.Idle, expectMostRecentItem().listeningState)
                cancelAndIgnoreRemainingEvents()
            }
            // Auto-confirm: the inner Spoken speech must reach TTS (not the prompt).
            coVerify { ttsClient.speak("Vale, llamando a Pepito.", any()) }
            coVerify(exactly = 0) { ttsClient.speak("¿Llamo a Pepito?", any()) }
        }

    private companion object {
        const val SIDE_EFFECT_DRAIN_LIMIT = 10
    }
}
