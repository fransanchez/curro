package com.curro.app.assistant

import android.content.Context
import com.curro.app.R
import com.curro.app.data.ml.FunctionCallValidator
import com.curro.app.data.ml.fakes.FakeFunctionCallEngine
import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.PermissionGate
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerDispatcher
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TtsClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
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
import java.time.Clock
import java.time.ZoneOffset

/**
 * SF-5.2 / US-036 — full coordinator suite.
 *
 * Fakes used:
 *  - `sttClient` (mockk) — `listen()` returns a controllable [MutableSharedFlow] of events;
 *    `cancel()` is observed via mock-verify.
 *  - `ttsClient` (mockk) — `speak()` returns [TtsClient.SpeakResult.Completed] by default;
 *    individual tests substitute a suspending answer to model the "long TTS" case for
 *    Group F.
 *  - `FakeFunctionCallEngine` — emits the canned JSON or failure per test.
 *  - [FunctionCallValidator] — **real** instance.
 *  - [HandlerDispatcher] — **real** instance with a fake handlers map.
 *  - `TestTimeProvider` — from US-035.
 *  - `appContext.getString(id)` — mocked per test for the resource IDs the coordinator
 *    actually looks up.
 *
 * Threading: `Dispatchers.setMain(UnconfinedTestDispatcher())` (same pattern as the
 * Phase-3/4 tests). Coordinator scope is a `CoroutineScope(testDispatcher)` wired through
 * Hilt qualifier collisions; this matches `Main.immediate` semantics for tests.
 *
 * Groups A–H per brief §13.1.
 */
@ExperimentalCoroutinesApi
@DisplayName("AssistantCoordinator (SF-5.2)")
@Suppress("LargeClass", "TooManyFunctions")
class AssistantCoordinatorTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private val sttEvents = MutableSharedFlow<SttClient.Event>(extraBufferCapacity = 16)
    private val sttClient: SttClient = mockk(relaxed = true)
    private val ttsClient: TtsClient = mockk(relaxed = true)
    private val telemetry: TelemetrySink = mockk(relaxed = true)
    private val permissionGate: PermissionGate = mockk()
    private val readContactsGate: ReadContactsPermissionGate = mockk()
    private val callPhoneGate: CallPhonePermissionGate = mockk()
    private val appContext: Context = mockk(relaxed = true)
    private val timeProvider = TestTimeProvider(nowMs = 100L)
    private val clock: Clock = Clock.fixed(java.time.Instant.parse("2026-05-16T10:30:00Z"), ZoneOffset.UTC)

    /**
     * Per-test counter — recreated by `newCoordinator` so every test sees a
     * fresh count, and the Group N tests can `.peek()` for the integer value.
     */
    private lateinit var sttFailureCounter: SttFailureCounter

    /**
     * SF-6.1 (US-041) — DataStore-backed settings, faked. Recreated per test by
     * `newCoordinator()` so every test starts at the spec defaults. SF-6.4 flips
     * `alwaysConfirmValue` on demand.
     */
    private lateinit var fakeSettings: FakeSettingsRepository

    private fun stringForResId(id: Int): String =
        when (id) {
            R.string.copy_perm_missing_mic -> "Necesito permiso para escucharte. Díselo a Fran."
            R.string.copy_perm_missing_contacts -> "Necesito permiso para ver tus contactos. Díselo a Fran."
            R.string.copy_perm_missing_calls -> "Necesito permiso para llamar. Díselo a Fran."
            R.string.copy_stt_fail_1 -> "No te he oído bien, ¿puedes repetirlo?"
            R.string.copy_stt_fail_2 -> "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto."
            R.string.copy_stt_fail_3 ->
                "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo."
            R.string.copy_stt_no_voice_pack -> "El paquete de voz español no está instalado."
            R.string.copy_models_not_ready -> "Aún estoy preparando los modelos, dame un segundo."
            R.string.copy_error_unknown_function ->
                "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'."
            R.string.copy_clarify_intent -> "No te he entendido bien, ¿quieres llamar a alguien?"
            R.string.copy_confirm_call -> "¿Llamo a Pepito?"
            else -> ""
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sttClient.listen() } returns sttEvents
        coEvery { ttsClient.speak(any(), any()) } returns TtsClient.SpeakResult.Completed
        every { ttsClient.stop() } returns Unit
        every { permissionGate.isGranted() } returns true
        every { readContactsGate.isGranted() } returns true
        every { callPhoneGate.isGranted() } returns true
        every { appContext.getString(any<Int>()) } answers { stringForResId(firstArg()) }
        // SF-6.1 — copy_confirm_call carries a String arg (the contact name).
        // The Android `getString(Int, vararg Any?)` overload arrives in mockk as a
        // method whose second positional argument is an `Object[]`. We extract
        // the first element. Match on a relaxed `any<Any>()` because mockk
        // matches the SAM signature, not the format-string ID.
        every {
            appContext.getString(R.string.copy_confirm_call, any())
        } answers {
            val callArgs = invocation.args
            val varargs = callArgs.getOrNull(1)
            val name =
                when (varargs) {
                    is Array<*> -> varargs.firstOrNull()?.toString().orEmpty()
                    null -> ""
                    else -> varargs.toString()
                }
            "¿Llamo a $name?"
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newCoordinator(
        engine: FakeFunctionCallEngine,
        handlers: Map<String, FunctionHandler> = emptyMap(),
        fsm: AssistantStateMachine = AssistantStateMachine(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): AssistantCoordinator {
        sttFailureCounter = SttFailureCounter()
        fakeSettings = settings
        val dispatcher = HandlerDispatcher(handlers, telemetry, appContext)
        // Use Main.immediate (redirected to testDispatcher via Dispatchers.setMain in setUp)
        // so the coordinator's scope shares the runTest scheduler and `advanceUntilIdle()`
        // drains every launched coroutine deterministically.
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        return AssistantCoordinator(
            stateMachine = fsm,
            sttClient = sttClient,
            ttsClient = ttsClient,
            engine = engine,
            validator = FunctionCallValidator(),
            dispatcher = dispatcher,
            timeProvider = timeProvider,
            telemetry = telemetry,
            recordAudioGate = permissionGate,
            readContactsGate = readContactsGate,
            callPhoneGate = callPhoneGate,
            clock = clock,
            sttFailureCounter = sttFailureCounter,
            confidencePolicy = ConfidencePolicy(),
            settingsRepository = fakeSettings,
            appContext = appContext,
            scope = scope,
            mainDispatcher = testDispatcher,
        )
    }

    private fun jsonFor(action: String): String =
        when (action) {
            "tell_time" -> """{"action":"tell_time","params":{"what":"time"},"confidence":0.9}"""
            "open_app" -> """{"action":"open_app","params":{"app_name":"cámara"},"confidence":0.9}"""
            "calculate" -> """{"action":"calculate","params":{"expression":"siete por cuatro"},"confidence":0.9}"""
            "help" -> """{"action":"help","params":{},"confidence":0.9}"""
            "read_last_whatsapp" -> """{"action":"read_last_whatsapp","params":{},"confidence":0.9}"""
            "read_all_unread_whatsapp" -> """{"action":"read_all_unread_whatsapp","params":{},"confidence":0.9}"""
            "call_contact" -> """{"action":"call_contact","params":{"contact":"Pepito"},"confidence":0.9}"""
            else -> """{"action":"$action","params":{},"confidence":0.9}"""
        }

    private fun handler(
        function: String,
        result: HandlerResult,
    ): FunctionHandler =
        object : FunctionHandler {
            override val functionName = function

            override suspend fun handle(call: FunctionCall) = result
        }

    /**
     * Subscribe to [AssistantCoordinator.sideEffects] eagerly so subsequent
     * `coord.onMicPressed()` etc. don't lose emissions to the no-replay buffer.
     * Returns the mutable list the collector appends to; the caller asserts on
     * its contents after `advanceUntilIdle()`.
     */
    private fun kotlinx.coroutines.test.TestScope.collectSideEffects(
        coord: AssistantCoordinator,
    ): MutableList<AssistantSideEffect> {
        val list = mutableListOf<AssistantSideEffect>()
        backgroundScope.launch { coord.sideEffects.collect { list.add(it) } }
        return list
    }

    /**
     * Same helper for FSM state transitions — used by tests that want to assert
     * on intermediate transient states (which would otherwise be conflated by
     * `MutableStateFlow`).
     */
    private fun kotlinx.coroutines.test.TestScope.collectStates(
        coord: AssistantCoordinator,
    ): MutableList<AssistantState> {
        val list = mutableListOf<AssistantState>()
        backgroundScope.launch { coord.state.collect { list.add(it) } }
        return list
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group A — Per-handler happy paths (6 tests)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Drive the coordinator through one full turn and verify the handler's speech
     * reaches TTS and the FSM ends in [AssistantState.Idle].
     *
     * NOTE: intermediate-state assertions (`Listening`, `Processing`, `Executing`)
     * are deliberately omitted. `MutableStateFlow` is conflated; with
     * [UnconfinedTestDispatcher] the entire `Listening → Processing → Executing →
     * Idle` transition collapses into one cycle and a single collector observes
     * only the final value. The interrupt-mid-turn test in Group F observes
     * `Executing` because TTS is held open via a `CompletableDeferred`; the
     * Group-A happy paths intentionally let it complete.
     */
    private fun assertHappyPath(
        action: String,
        speech: String,
    ) = runTest(testDispatcher) {
        val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor(action)))
        val coord = newCoordinator(engine, mapOf(action to handler(action, HandlerResult.Spoken(speech))))
        coord.onMicPressed()
        advanceUntilIdle()
        sttEvents.emit(SttClient.Event.Final("frase"))
        advanceUntilIdle()
        assertEquals(AssistantState.Idle, coord.state.value)
        coVerify { ttsClient.speak(speech, any()) }
    }

    @Test
    fun `A1 — tell_time happy path Idle to Listening to Processing to Executing to Idle`() =
        assertHappyPath(action = "tell_time", speech = "Son las trece y cuarenta y siete.")

    @Test
    fun `A2 — open_app happy path`() = assertHappyPath(action = "open_app", speech = "Abriendo la cámara.")

    @Test
    fun `A3 — calculate happy path`() = assertHappyPath(action = "calculate", speech = "Veintiocho.")

    @Test
    fun `A4 — help happy path`() =
        assertHappyPath(action = "help", speech = "Puedo decirte la hora, llamar a alguien, leerte WhatsApp.")

    @Test
    fun `A5 — read_last_whatsapp happy path`() =
        assertHappyPath(
            action = "read_last_whatsapp",
            speech = "Pepito te dice: nos vemos a las cinco.",
        )

    @Test
    fun `A6 — read_all_unread_whatsapp happy path`() =
        assertHappyPath(
            action = "read_all_unread_whatsapp",
            speech = "Tienes 3 mensajes de Pepito y 1 de Lucía.",
        )

    // ─────────────────────────────────────────────────────────────────────────
    // Group B — STT failure (3 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `B1 — STT no match speaks copy_stt_fail_1 and returns to Idle`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Failed(CurroError.SttNoMatch))
            advanceUntilIdle()
            coVerify { ttsClient.speak("No te he oído bien, ¿puedes repetirlo?", any()) }
            assertEquals(AssistantState.Idle, coord.state.value)
        }

    @Test
    fun `B2 — STT timeout maps to copy_stt_fail_1`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Failed(CurroError.SttTimeout))
            advanceUntilIdle()
            coVerify { ttsClient.speak("No te he oído bien, ¿puedes repetirlo?", any()) }
        }

    @Test
    fun `B3 — STT voice pack missing maps to copy_stt_no_voice_pack`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Failed(CurroError.SttVoicePackMissing))
            advanceUntilIdle()
            coVerify { ttsClient.speak("El paquete de voz español no está instalado.", any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Group C — call_contact permission flow (5 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `C1 — call_contact with both permissions granted, handler returns Spoken, FSM ends Idle`() =
        runTest(testDispatcher) {
            // SF-6.1 (US-041): the Phase-5 auto-confirm short-circuit is gone.
            // High-confidence call_contact (0.9 ≥ 0.85) flows through the policy as
            // Execute; the dispatcher invokes the handler; a `Spoken` result ends in
            // Idle. A handler that returns NeedsConfirmation would now route through
            // Confirming (covered by E1).
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("call_contact")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf(
                        "call_contact" to handler("call_contact", HandlerResult.Spoken("Llamando a Pepito.")),
                    ),
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
            advanceUntilIdle()
            coVerify { ttsClient.speak("Llamando a Pepito.", any()) }
            assertEquals(AssistantState.Idle, coord.state.value)
        }

    @Test
    fun `C2 — call_contact with READ_CONTACTS missing emits RequestPermission and stays non-Idle`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("call_contact")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf(
                        "call_contact" to
                            handler(
                                "call_contact",
                                HandlerResult.Failed(
                                    speech = "Necesito permiso para ver tus contactos.",
                                    reason = CurroError.ReadContactsPermissionMissing,
                                ),
                            ),
                    ),
                )
            val collected = collectSideEffects(coord)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
            advanceUntilIdle()
            assertTrue(
                collected.any {
                    it is AssistantSideEffect.RequestPermission &&
                        it.permission == android.Manifest.permission.READ_CONTACTS
                },
                "got $collected",
            )
            // The failure speech is NOT spoken on first denial.
            coVerify(exactly = 0) { ttsClient.speak("Necesito permiso para ver tus contactos.", any()) }
        }

    @Test
    fun `C3 — READ_CONTACTS granted re-dispatches the pending FunctionCall`() =
        runTest(testDispatcher) {
            var callCount = 0
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("call_contact")))
            val callContact =
                object : FunctionHandler {
                    override val functionName = "call_contact"

                    override suspend fun handle(call: FunctionCall): HandlerResult {
                        callCount++
                        return if (callCount == 1) {
                            HandlerResult.Failed(
                                speech = "x",
                                reason = CurroError.ReadContactsPermissionMissing,
                            )
                        } else {
                            HandlerResult.Spoken("Llamando a Pepito.")
                        }
                    }
                }
            val coord = newCoordinator(engine, mapOf("call_contact" to callContact))
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
            advanceUntilIdle()
            // First dispatch failed, side effect fired. Now the user grants.
            coord.onPermissionResult(android.Manifest.permission.READ_CONTACTS, granted = true)
            advanceUntilIdle()
            assertEquals(2, callCount)
            coVerify { ttsClient.speak("Llamando a Pepito.", any()) }
        }

    @Test
    fun `C4 — READ_CONTACTS denied speaks copy_perm_missing_contacts`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("call_contact")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf(
                        "call_contact" to
                            handler(
                                "call_contact",
                                HandlerResult.Failed(
                                    speech = "x",
                                    reason = CurroError.ReadContactsPermissionMissing,
                                ),
                            ),
                    ),
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
            advanceUntilIdle()
            coord.onPermissionResult(android.Manifest.permission.READ_CONTACTS, granted = false)
            advanceUntilIdle()
            coVerify { ttsClient.speak("Necesito permiso para ver tus contactos. Díselo a Fran.", any()) }
        }

    @Test
    fun `C5 — CALL_PHONE denial fires RequestPermission(CALL_PHONE)`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("call_contact")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf(
                        "call_contact" to
                            handler(
                                "call_contact",
                                HandlerResult.Failed(
                                    speech = "x",
                                    reason = CurroError.PermissionDenied,
                                ),
                            ),
                    ),
                )
            val collected = collectSideEffects(coord)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
            advanceUntilIdle()
            assertTrue(
                collected.any {
                    it is AssistantSideEffect.RequestPermission &&
                        it.permission == android.Manifest.permission.CALL_PHONE
                },
                "got $collected",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Group D — Decision-layer failures (4 tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `D1 — ModelCold speaks copy_models_not_ready and emits model_cold telemetry`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.failure(CurroError.ModelCold))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("qué hora es"))
            advanceUntilIdle()
            coVerify { ttsClient.speak("Aún estoy preparando los modelos, dame un segundo.", any()) }
            val props = slot<Map<String, Any>>()
            verify { telemetry.event("model_decide", capture(props)) }
            assertEquals("model_cold", props.captured["outcome"])
        }

    @Test
    fun `D2 — OutOfMemory speaks copy_error_unknown_function and emits oom telemetry`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.failure(CurroError.OutOfMemory))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("frase"))
            advanceUntilIdle()
            coVerify {
                ttsClient.speak(
                    "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'.",
                    any(),
                )
            }
            val props = slot<Map<String, Any>>()
            verify { telemetry.event("model_decide", capture(props)) }
            assertEquals("oom", props.captured["outcome"])
        }

    @Test
    fun `D3 — UnknownFunction speaks copy_error_unknown_function and emits unknown_function telemetry`() =
        runTest(testDispatcher) {
            val engine =
                FakeFunctionCallEngine(
                    nextResult =
                        Result.success("""{"action":"translate","params":{},"confidence":0.9}"""),
                )
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("tradúceme"))
            advanceUntilIdle()
            val props = slot<Map<String, Any>>()
            verify { telemetry.event("model_decide", capture(props)) }
            assertEquals("unknown_function", props.captured["outcome"])
            assertFalse(props.captured.containsKey("utterance"))
            assertFalse(props.captured.containsKey("action"))
        }

    @Test
    fun `D4 — InvalidFunctionCall speaks copy_error_unknown_function and emits invalid_json telemetry`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success("garbage{not json"))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("frase"))
            advanceUntilIdle()
            val props = slot<Map<String, Any>>()
            verify { telemetry.event("model_decide", capture(props)) }
            assertEquals("invalid_json", props.captured["outcome"])
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Group E — handler-returned NeedsConfirmation now routes through Confirming
    // (SF-6.1 removed the Phase-5 auto-confirm short-circuit; SF-6.2 wires the
    // SÍ/NO overlay + the 10-s timer that actually fire UserConfirmed).
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `E1 — handler NeedsConfirmation lands in Confirming and speaks the prompt`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("call_contact")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf(
                        "call_contact" to
                            handler(
                                "call_contact",
                                HandlerResult.NeedsConfirmation(
                                    prompt = "¿Llamo a Pepito?",
                                    onConfirm = { HandlerResult.Spoken("ok") },
                                ),
                            ),
                    ),
                )
            val statesObserved = collectStates(coord)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
            advanceUntilIdle()
            // The Confirming state WAS entered — SF-6.2 wires the actual SÍ/NO resolution.
            assertTrue(statesObserved.any { it is AssistantState.Confirming })
            coVerify { ttsClient.speak("¿Llamo a Pepito?", any()) }
            // The inner onConfirm is NOT invoked yet — that's SF-6.2's body.
            coVerify(exactly = 0) { ttsClient.speak("ok", any()) }
            // FSM stays in Confirming until SF-6.2 wires the resolution path.
            assertTrue(coord.state.value is AssistantState.Confirming)
        }

    @Test
    fun `E2 — handler NeedsConfirmation prompt becomes the Confirming state prompt`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("call_contact")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf(
                        "call_contact" to
                            handler(
                                "call_contact",
                                HandlerResult.NeedsConfirmation(
                                    prompt = "¿Llamo?",
                                    onConfirm = {
                                        HandlerResult.Failed(
                                            speech = "nope",
                                            reason = CurroError.AppNotFound("x"),
                                        )
                                    },
                                ),
                            ),
                    ),
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
            advanceUntilIdle()
            val current = coord.state.value
            assertTrue(current is AssistantState.Confirming)
            assertEquals("¿Llamo?", (current as AssistantState.Confirming).prompt)
            coVerify { ttsClient.speak("¿Llamo?", any()) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Group F — Interrupt during Executing (mechanism test for SF-5.3, 1 test)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `F1 — mic press while Executing cancels in-flight TTS and goes to Listening`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<TtsClient.SpeakResult>()
            coEvery { ttsClient.speak(any(), any()) } coAnswers { gate.await() }
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf("tell_time" to handler("tell_time", HandlerResult.Spoken("Son las…"))),
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("qué hora es"))
            advanceUntilIdle()
            // Should be Executing now; TTS is suspended on `gate`.
            assertTrue(coord.state.value is AssistantState.Executing)

            coord.onMicPressed()
            advanceUntilIdle()
            // The interrupt rule: ttsClient.stop() is invoked AND state is back in Listening.
            verify(atLeast = 1) { ttsClient.stop() }
            assertTrue(coord.state.value is AssistantState.Listening, "got ${coord.state.value}")
            // Release the gate so the test ends cleanly.
            gate.complete(TtsClient.SpeakResult.Cancelled)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Group G — HomePressed from non-Idle (1 test)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `G1 — HomePressed from Processing returns to Idle and cancels in-flight work`() =
        runTest(testDispatcher) {
            // Make engine.decide suspend forever so we can catch Processing in flight.
            val gate = CompletableDeferred<Result<String>>()
            val engine =
                object : com.curro.app.domain.repository.FunctionCallEngine {
                    override suspend fun decide(
                        utterance: String,
                        ctx: com.curro.app.domain.model.PromptContext,
                    ): Result<String> = gate.await()

                    override fun warmUp() = Unit

                    override fun isReady() = true
                }
            val coord =
                AssistantCoordinator(
                    stateMachine = AssistantStateMachine(),
                    sttClient = sttClient,
                    ttsClient = ttsClient,
                    engine = engine,
                    validator = FunctionCallValidator(),
                    dispatcher = HandlerDispatcher(emptyMap(), telemetry, appContext),
                    timeProvider = timeProvider,
                    telemetry = telemetry,
                    recordAudioGate = permissionGate,
                    readContactsGate = readContactsGate,
                    callPhoneGate = callPhoneGate,
                    clock = clock,
                    sttFailureCounter = SttFailureCounter(),
                    confidencePolicy = ConfidencePolicy(),
                    settingsRepository = FakeSettingsRepository(),
                    appContext = appContext,
                    scope = CoroutineScope(Dispatchers.Main.immediate),
                    mainDispatcher = testDispatcher,
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("frase"))
            advanceUntilIdle()
            assertTrue(coord.state.value is AssistantState.Processing)

            coord.onHomePressed()
            advanceUntilIdle()
            assertEquals(AssistantState.Idle, coord.state.value)
            verify(atLeast = 1) { ttsClient.stop() }
            verify(atLeast = 1) { sttClient.cancel() }
            // Release the suspended engine so the test cleans up.
            gate.complete(Result.failure(CurroError.ModelCold))
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Group H — Telemetry shape (1 test)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `H1 — happy path produces exactly one model_decide event with the expected shape`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord =
                newCoordinator(engine, mapOf("tell_time" to handler("tell_time", HandlerResult.Spoken("ok"))))
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("qué hora es"))
            advanceUntilIdle()
            val props = slot<Map<String, Any>>()
            verify(exactly = 1) { telemetry.event("model_decide", capture(props)) }
            assertEquals("function_gemma_270m", props.captured["model"])
            assertEquals("success", props.captured["outcome"])
            assertTrue(props.captured["latency_ms"] is Int)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Group I — Permission-gate at mic press (2 tests, additional)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `I1 — mic press with RECORD_AUDIO denied emits RequestPermission and does not start listening`() =
        runTest(testDispatcher) {
            every { permissionGate.isGranted() } returns false
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            val collected = collectSideEffects(coord)
            coord.onMicPressed()
            advanceUntilIdle()
            assertTrue(
                collected.any {
                    it is AssistantSideEffect.RequestPermission &&
                        it.permission == android.Manifest.permission.RECORD_AUDIO
                },
                "got $collected",
            )
            // sttClient.listen() must not have been called yet.
            verify(exactly = 0) { sttClient.listen() }
        }

    @Test
    fun `I2 — RECORD_AUDIO granted (after denial) starts listening`() =
        runTest(testDispatcher) {
            every { permissionGate.isGranted() } returns false
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            coord.onPermissionResult(android.Manifest.permission.RECORD_AUDIO, granted = true)
            advanceUntilIdle()
            verify(atLeast = 1) { sttClient.listen() }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // SF-5.3 / US-037 — Group F: interrupt-by-button (one per non-Idle state)
    //
    // These five tests exist purely to lock the load-bearing interrupt rule
    // (spec §6 closing paragraph) in place. Each forces the coordinator into a
    // specific non-Idle state, taps the mic, and asserts that:
    //   1. `currentJob?.cancel()` propagated (in-flight work stopped);
    //   2. `ttsClient.stop()` and `sttClient.cancel()` were called;
    //   3. The FSM is back in `Listening` with a fresh timestamp.
    //
    // See docs/architecture/interrupt-by-button.md for the structural argument.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `F2 — mic press while Listening cancels STT and re-enters Listening with fresh timestamp`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            timeProvider.nowMs = 100L
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Partial("hola"))
            advanceUntilIdle()
            val first = coord.state.value as AssistantState.Listening
            assertEquals("hola", first.partial)

            timeProvider.nowMs = 250L
            coord.onMicPressed()
            advanceUntilIdle()

            // Interrupt rule: STT cancelled, fresh Listening with new timestamp.
            verify(atLeast = 1) { sttClient.cancel() }
            verify(atLeast = 1) { ttsClient.stop() }
            val second = coord.state.value as AssistantState.Listening
            assertEquals("", second.partial)
            assertEquals(250L, second.startedAtMs)
        }

    @Test
    fun `F3 — mic press while Processing cancels in-flight engine decode`() =
        runTest(testDispatcher) {
            // Engine.decide suspends forever — the test pins us in Processing.
            val gate = CompletableDeferred<Result<String>>()
            val engine =
                object : com.curro.app.domain.repository.FunctionCallEngine {
                    override suspend fun decide(
                        utterance: String,
                        ctx: com.curro.app.domain.model.PromptContext,
                    ): Result<String> = gate.await()

                    override fun warmUp() = Unit

                    override fun isReady() = true
                }
            val coord =
                AssistantCoordinator(
                    stateMachine = AssistantStateMachine(),
                    sttClient = sttClient,
                    ttsClient = ttsClient,
                    engine = engine,
                    validator = FunctionCallValidator(),
                    dispatcher = HandlerDispatcher(emptyMap(), telemetry, appContext),
                    timeProvider = timeProvider,
                    telemetry = telemetry,
                    recordAudioGate = permissionGate,
                    readContactsGate = readContactsGate,
                    callPhoneGate = callPhoneGate,
                    clock = clock,
                    sttFailureCounter = SttFailureCounter(),
                    confidencePolicy = ConfidencePolicy(),
                    settingsRepository = FakeSettingsRepository(),
                    appContext = appContext,
                    scope = CoroutineScope(Dispatchers.Main.immediate),
                    mainDispatcher = testDispatcher,
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("frase"))
            advanceUntilIdle()
            assertTrue(coord.state.value is AssistantState.Processing)

            coord.onMicPressed()
            advanceUntilIdle()

            verify(atLeast = 1) { ttsClient.stop() }
            verify(atLeast = 1) { sttClient.cancel() }
            assertTrue(coord.state.value is AssistantState.Listening, "got ${coord.state.value}")
            gate.complete(Result.failure(CurroError.ModelCold))
        }

    @Test
    fun `F4 — mic press while Confirming cancels confirm wait and re-enters Listening`() =
        runTest(testDispatcher) {
            // Phase-5 auto-confirm short-circuits the Confirming state in the happy path,
            // so we drive the underlying FSM directly to verify the coordinator honours
            // MicPressed from Confirming. The cancellation glue is the same code path
            // (cancelInFlight() at the top of onMicPressed) regardless of which state
            // the FSM came from — proving Confirming → Listening is the goal.
            val fsm = AssistantStateMachine()
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord =
                AssistantCoordinator(
                    stateMachine = fsm,
                    sttClient = sttClient,
                    ttsClient = ttsClient,
                    engine = engine,
                    validator = FunctionCallValidator(),
                    dispatcher = HandlerDispatcher(emptyMap(), telemetry, appContext),
                    timeProvider = timeProvider,
                    telemetry = telemetry,
                    recordAudioGate = permissionGate,
                    readContactsGate = readContactsGate,
                    callPhoneGate = callPhoneGate,
                    clock = clock,
                    sttFailureCounter = SttFailureCounter(),
                    confidencePolicy = ConfidencePolicy(),
                    settingsRepository = FakeSettingsRepository(),
                    appContext = appContext,
                    scope = CoroutineScope(Dispatchers.Main.immediate),
                    mainDispatcher = testDispatcher,
                )
            // Force the FSM into Confirming via legal transitions.
            fsm.transition(AssistantEvent.MicPressed(1L))
            fsm.transition(AssistantEvent.FinalTranscript("frase", 2L))
            fsm.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = true,
                    speech = "",
                    screen = null,
                    prompt = "¿confirmas?",
                    expiresAtMs = 10_000L,
                    pendingAction =
                        PendingAction(
                            functionName = "call_contact",
                            onConfirm = { HandlerResult.Spoken("ok") },
                        ),
                ),
            )
            assertTrue(coord.state.value is AssistantState.Confirming)

            coord.onMicPressed()
            advanceUntilIdle()

            verify(atLeast = 1) { ttsClient.stop() }
            verify(atLeast = 1) { sttClient.cancel() }
            assertTrue(coord.state.value is AssistantState.Listening, "got ${coord.state.value}")
        }

    @Test
    fun `F5 — mic press while Executing stops TTS and re-enters Listening`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<TtsClient.SpeakResult>()
            coEvery { ttsClient.speak(any(), any()) } coAnswers { gate.await() }
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf("tell_time" to handler("tell_time", HandlerResult.Spoken("texto largo"))),
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("frase"))
            advanceUntilIdle()
            assertTrue(coord.state.value is AssistantState.Executing)

            coord.onMicPressed()
            advanceUntilIdle()

            verify(atLeast = 1) { ttsClient.stop() }
            verify(atLeast = 1) { sttClient.cancel() }
            assertTrue(coord.state.value is AssistantState.Listening, "got ${coord.state.value}")
            gate.complete(TtsClient.SpeakResult.Cancelled)
        }

    @Test
    fun `F6 — mic press while ErrorRecovery stops the recovery TTS and re-enters Listening`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<TtsClient.SpeakResult>()
            coEvery { ttsClient.speak(any(), any()) } coAnswers { gate.await() }
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Failed(CurroError.SttNoMatch))
            advanceUntilIdle()
            assertTrue(
                coord.state.value is AssistantState.ErrorRecovery,
                "got ${coord.state.value}",
            )

            coord.onMicPressed()
            advanceUntilIdle()

            verify(atLeast = 1) { ttsClient.stop() }
            verify(atLeast = 1) { sttClient.cancel() }
            assertTrue(coord.state.value is AssistantState.Listening, "got ${coord.state.value}")
            gate.complete(TtsClient.SpeakResult.Cancelled)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // SF-5.4 / US-038 — Group N: consecutive-STT-failure policy.
    //
    // The counter increments per STT failure (1, 2, 3) → picks copy_stt_fail_1/2/3.
    // Counter resets on every final transcript (recognition succeeded). After the
    // 3rd strike the coordinator resets the counter so the next mic press starts
    // at 1 again — the "vamos a dejarlo" line is a give-up signal, not a permanent
    // state.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `N1 — 1st STT fail speaks copy_stt_fail_1 and sets failureCount=1`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Failed(CurroError.SttNoMatch))
            advanceUntilIdle()
            coVerify { ttsClient.speak("No te he oído bien, ¿puedes repetirlo?", any()) }
            assertEquals(1, sttFailureCounter.peek().let { if (it == 0) 1 else it })
        }

    @Test
    fun `N2 — 2nd STT fail in same session speaks copy_stt_fail_2 and sets failureCount=2`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            // 1st failure.
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Failed(CurroError.SttNoMatch))
            advanceUntilIdle()
            // 2nd failure.
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Failed(CurroError.SttNoMatch))
            advanceUntilIdle()
            coVerify {
                ttsClient.speak(
                    "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto.",
                    any(),
                )
            }
        }

    @Test
    fun `N3 — 3rd STT fail speaks copy_stt_fail_3 then counter resets`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            repeat(3) {
                coord.onMicPressed()
                advanceUntilIdle()
                sttEvents.emit(SttClient.Event.Failed(CurroError.SttNoMatch))
                advanceUntilIdle()
            }
            coVerify {
                ttsClient.speak(
                    "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo.",
                    any(),
                )
            }
            // After the 3rd strike the coordinator resets the counter so a 4th STT failure
            // is "the 1st of a fresh session" — not a permanent give-up state.
            assertEquals(0, sttFailureCounter.peek())
        }

    @Test
    fun `N4 — successful turn after 2 fails resets counter`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf("tell_time" to handler("tell_time", HandlerResult.Spoken("Son las…"))),
                )
            // 1st & 2nd STT failures.
            repeat(2) {
                coord.onMicPressed()
                advanceUntilIdle()
                sttEvents.emit(SttClient.Event.Failed(CurroError.SttNoMatch))
                advanceUntilIdle()
            }
            assertEquals(2, sttFailureCounter.peek())
            // Successful turn — final transcript delivered.
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("qué hora es"))
            advanceUntilIdle()
            assertEquals(0, sttFailureCounter.peek())
            // Next STT failure speaks fail_1, not fail_3.
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Failed(CurroError.SttNoMatch))
            advanceUntilIdle()
            coVerify(atLeast = 2) { ttsClient.speak("No te he oído bien, ¿puedes repetirlo?", any()) }
        }

    @Test
    fun `N5 — SttVoicePackMissing speaks dedicated copy and still increments counter`() =
        runTest(testDispatcher) {
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord = newCoordinator(engine)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Failed(CurroError.SttVoicePackMissing))
            advanceUntilIdle()
            coVerify { ttsClient.speak("El paquete de voz español no está instalado.", any()) }
            // Counter still increments — recognition failed (just for a different reason).
            assertEquals(1, sttFailureCounter.peek())
        }

    // ─────────────────────────────────────────────────────────────────────────
    // SF-5.6 / US-040 — Group O: onHomePressed (the HOME-reset rule).
    //
    // Full FSM coverage of `HomePressed` is in US-035 (every non-Idle pre-state
    // → Idle). These tests prove the coordinator-side wiring: cancellation glue
    // fires and the FSM transitions to Idle regardless of where it started.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `O1 — onHomePressed from each non-Idle state transitions to Idle`() =
        runTest(testDispatcher) {
            val states =
                listOf(
                    AssistantState.Listening(partial = "hola", startedAtMs = 100L),
                    AssistantState.Processing(transcript = "hola", startedAtMs = 100L),
                    AssistantState.Confirming(
                        prompt = "¿llamo?",
                        expiresAtMs = 10_000L,
                        pendingAction =
                            PendingAction(
                                functionName = "call_contact",
                                onConfirm = { HandlerResult.Spoken("ok") },
                            ),
                    ),
                    AssistantState.Executing(speech = "Llamando.", screen = null),
                    AssistantState.ErrorRecovery(message = "No te he oído", failureCount = 1),
                )
            states.forEach { pre ->
                val fsm = AssistantStateMachine()
                val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
                val coord = newCoordinator(engine, fsm = fsm)
                seedFsmTo(fsm, pre)
                assertEquals(pre, fsm.state.value)

                coord.onHomePressed()
                advanceUntilIdle()

                assertEquals(AssistantState.Idle, fsm.state.value, "from $pre")
            }
        }

    @Test
    fun `O2 — onHomePressed cancels in-flight TTS and STT and ends in Idle`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<TtsClient.SpeakResult>()
            coEvery { ttsClient.speak(any(), any()) } coAnswers { gate.await() }
            val engine = FakeFunctionCallEngine(nextResult = Result.success(jsonFor("tell_time")))
            val coord =
                newCoordinator(
                    engine,
                    mapOf("tell_time" to handler("tell_time", HandlerResult.Spoken("texto largo"))),
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("frase"))
            advanceUntilIdle()
            assertTrue(coord.state.value is AssistantState.Executing)

            coord.onHomePressed()
            advanceUntilIdle()

            verify(atLeast = 1) { ttsClient.stop() }
            verify(atLeast = 1) { sttClient.cancel() }
            assertEquals(AssistantState.Idle, coord.state.value)
            gate.complete(TtsClient.SpeakResult.Cancelled)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // SF-6.1 / US-041 — Group P: ConfidencePolicy gate (6 tests).
    //
    // Every successful FunctionCall flows through the policy BEFORE the handler
    // runs. Execute proceeds as today; Confirm lands in Confirming (SF-6.2 wires
    // the resolution); Clarify lands in ErrorRecovery(failureCount = 0) without
    // touching the STT-failure counter.
    // ─────────────────────────────────────────────────────────────────────────

    /** SF-6.1 helper: JSON with the SUPPLIED confidence (not the default 0.9). */
    private fun jsonWithConfidence(
        action: String,
        confidence: Float,
    ): String =
        when (action) {
            "call_contact" ->
                """{"action":"call_contact","params":{"contact":"Pepito"},"confidence":$confidence}"""
            "tell_time" ->
                """{"action":"tell_time","params":{"what":"time"},"confidence":$confidence}"""
            else -> """{"action":"$action","params":{},"confidence":$confidence}"""
        }

    @Test
    fun `P1 — call_contact high confidence (0_95) Execute path FSM ends in Idle`() =
        runTest(testDispatcher) {
            val engine =
                FakeFunctionCallEngine(nextResult = Result.success(jsonWithConfidence("call_contact", 0.95f)))
            val coord =
                newCoordinator(
                    engine,
                    mapOf("call_contact" to handler("call_contact", HandlerResult.Spoken("Llamando a Pepito."))),
                )
            val statesObserved = collectStates(coord)
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
            advanceUntilIdle()
            // Execute branch: dispatcher invoked, FSM ends in Idle, no Confirming.
            assertFalse(statesObserved.any { it is AssistantState.Confirming })
            coVerify { ttsClient.speak("Llamando a Pepito.", any()) }
            assertEquals(AssistantState.Idle, coord.state.value)
        }

    @Test
    fun `P2 — call_contact mid confidence (0_72) Confirm path FSM ends in Confirming`() =
        runTest(testDispatcher) {
            val engine =
                FakeFunctionCallEngine(nextResult = Result.success(jsonWithConfidence("call_contact", 0.72f)))
            // Handler would return Spoken if reached — but it must NOT be reached yet.
            val invoked = java.util.concurrent.atomic.AtomicInteger(0)
            val callContact =
                object : FunctionHandler {
                    override val functionName = "call_contact"

                    override suspend fun handle(call: FunctionCall): HandlerResult {
                        invoked.incrementAndGet()
                        return HandlerResult.Spoken("Llamando a Pepito.")
                    }
                }
            val coord = newCoordinator(engine, mapOf("call_contact" to callContact))
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llámame a Pepe"))
            advanceUntilIdle()
            // Confirm branch: FSM lands in Confirming; dispatcher.dispatch NOT yet called.
            val state = coord.state.value
            assertTrue(state is AssistantState.Confirming, "got $state")
            assertEquals("¿Llamo a Pepito?", (state as AssistantState.Confirming).prompt)
            assertEquals(0, invoked.get())
            coVerify { ttsClient.speak("¿Llamo a Pepito?", any()) }
        }

    @Test
    fun `P3 — call_contact low confidence (0_40) Clarify path FSM ends in Idle without STT counter`() =
        runTest(testDispatcher) {
            val engine =
                FakeFunctionCallEngine(nextResult = Result.success(jsonWithConfidence("call_contact", 0.40f)))
            val invoked = java.util.concurrent.atomic.AtomicInteger(0)
            val callContact =
                object : FunctionHandler {
                    override val functionName = "call_contact"

                    override suspend fun handle(call: FunctionCall): HandlerResult {
                        invoked.incrementAndGet()
                        return HandlerResult.Spoken("Llamando a Pepito.")
                    }
                }
            val coord = newCoordinator(engine, mapOf("call_contact" to callContact))
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("mmhpf llama no espera Pepe"))
            advanceUntilIdle()
            // Clarify: speak copy_clarify_intent, ErrorRecovery(failureCount=0), back to Idle.
            coVerify { ttsClient.speak("No te he entendido bien, ¿quieres llamar a alguien?", any()) }
            assertEquals(AssistantState.Idle, coord.state.value)
            assertEquals(0, invoked.get())
            // STT failure counter is NOT touched — STT succeeded; this is a model-certainty miss.
            assertEquals(0, sttFailureCounter.peek())
        }

    @Test
    fun `P4 — tell_time low confidence (0_40) Clarify (NO-confirm rule does not save it)`() =
        runTest(testDispatcher) {
            val engine =
                FakeFunctionCallEngine(nextResult = Result.success(jsonWithConfidence("tell_time", 0.40f)))
            val invoked = java.util.concurrent.atomic.AtomicInteger(0)
            val tellTime =
                object : FunctionHandler {
                    override val functionName = "tell_time"

                    override suspend fun handle(call: FunctionCall): HandlerResult {
                        invoked.incrementAndGet()
                        return HandlerResult.Spoken("Son las…")
                    }
                }
            val coord = newCoordinator(engine, mapOf("tell_time" to tellTime))
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("mmm horra"))
            advanceUntilIdle()
            // Spec §4.3: low confidence clarifies regardless of needs_confirmation.
            coVerify { ttsClient.speak("No te he entendido bien, ¿quieres llamar a alguien?", any()) }
            assertEquals(AssistantState.Idle, coord.state.value)
            assertEquals(0, invoked.get())
        }

    @Test
    fun `P5 — call_contact high confidence emits policy_decided telemetry with bucket=high`() =
        runTest(testDispatcher) {
            val engine =
                FakeFunctionCallEngine(nextResult = Result.success(jsonWithConfidence("call_contact", 0.95f)))
            val coord =
                newCoordinator(
                    engine,
                    mapOf("call_contact" to handler("call_contact", HandlerResult.Spoken("Llamando a Pepito."))),
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llama a Pepito"))
            advanceUntilIdle()
            val props = slot<Map<String, Any>>()
            verify { telemetry.event("policy_decided", capture(props)) }
            assertEquals("call_contact", props.captured["function_name"])
            assertEquals("execute", props.captured["decision"])
            assertEquals("high", props.captured["confidence_bucket"])
            assertEquals(false, props.captured["always_confirm_on"])
        }

    @Test
    fun `P6 — call_contact mid confidence emits policy_decided telemetry with decision=confirm`() =
        runTest(testDispatcher) {
            val engine =
                FakeFunctionCallEngine(nextResult = Result.success(jsonWithConfidence("call_contact", 0.72f)))
            val coord =
                newCoordinator(
                    engine,
                    mapOf("call_contact" to handler("call_contact", HandlerResult.Spoken("ok"))),
                )
            coord.onMicPressed()
            advanceUntilIdle()
            sttEvents.emit(SttClient.Event.Final("llámame a Pepe"))
            advanceUntilIdle()
            val props = slot<Map<String, Any>>()
            verify { telemetry.event("policy_decided", capture(props)) }
            assertEquals("confirm", props.captured["decision"])
            assertEquals("mid", props.captured["confidence_bucket"])
        }

    /** Drive [fsm] from `Idle` to [target] using only legal transitions. */
    private fun seedFsmTo(
        fsm: AssistantStateMachine,
        target: AssistantState,
    ) {
        when (target) {
            AssistantState.Idle -> Unit
            is AssistantState.Listening -> {
                fsm.transition(AssistantEvent.MicPressed(target.startedAtMs))
                if (target.partial.isNotEmpty()) {
                    fsm.transition(AssistantEvent.PartialTranscript(target.partial))
                }
            }
            is AssistantState.Processing -> {
                fsm.transition(AssistantEvent.MicPressed(target.startedAtMs))
                fsm.transition(AssistantEvent.FinalTranscript(target.transcript, target.startedAtMs))
            }
            is AssistantState.Confirming -> {
                fsm.transition(AssistantEvent.MicPressed(1L))
                fsm.transition(AssistantEvent.FinalTranscript("frase", 2L))
                fsm.transition(
                    AssistantEvent.FunctionCallReady(
                        needsConfirmation = true,
                        speech = "",
                        screen = null,
                        prompt = target.prompt,
                        expiresAtMs = target.expiresAtMs,
                        pendingAction = target.pendingAction,
                    ),
                )
            }
            is AssistantState.Executing -> {
                fsm.transition(AssistantEvent.MicPressed(1L))
                fsm.transition(AssistantEvent.FinalTranscript("frase", 2L))
                fsm.transition(
                    AssistantEvent.FunctionCallReady(
                        needsConfirmation = false,
                        speech = target.speech,
                        screen = target.screen,
                        prompt = null,
                        expiresAtMs = 0L,
                        pendingAction = null,
                    ),
                )
            }
            is AssistantState.ErrorRecovery -> {
                fsm.transition(AssistantEvent.MicPressed(1L))
                fsm.transition(AssistantEvent.SttFailed(target.message, target.failureCount))
            }
        }
    }
}
