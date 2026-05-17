package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.ml.ModelFiles
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.model.WhatsAppMessage.Classification
import com.curro.app.domain.repository.NotificationRepository
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TextGenEngine
import com.curro.app.domain.repository.TtsClient
import com.curro.app.handler.whatsapp.SummaryOutputCleaner
import com.curro.app.handler.whatsapp.WhatsAppSummaryPromptBuilder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ReadAllUnreadWhatsAppHandler] (US-032 / SF-4.8).
 *
 * Uses fake implementations of [NotificationRepository] and [NotificationAccessGate].
 * Context.getString is stubbed with Mockk using the same vararg-unwrap pattern
 * as TellTimeHandlerTest.
 */
@DisplayName("ReadAllUnreadWhatsAppHandler (SF-4.8)")
class ReadAllUnreadWhatsAppHandlerTest {
    private val context: Context = mockk()

    @BeforeEach
    fun setUp() {
        // Format templates that match the Android positional format spec.
        // Mockk passes vararg as a wrapped Object[] at args[1]; unwrap before String.format.
        val templates =
            mapOf(
                R.string.copy_no_unread to "NO_UNREAD",
                R.string.copy_whatsapp_parse_miss to "PARSE_MISS",
                R.string.copy_perm_missing_notifs to "PERM_MISSING",
                R.string.copy_many_unread to "MANY_UNREAD",
                R.string.copy_reading_summary_one to "ONE:%d:%s",
                R.string.copy_reading_summary_many to "MANY:%d:%s",
                R.string.copy_reading_summary_multi_sender to "MULTI:%d:%s:%d:%s",
                R.string.copy_reading_summary_three_plus to "3PLUS:%s:%s:%s",
                R.string.copy_reading_starts_with to "START:%s",
                R.string.copy_reading_from to "FROM:%s:%s",
                // US-062 / SF-9.3
                R.string.copy_summary_intro to "INTRO",
                R.string.copy_cold_model to "COLD",
            )
        every { context.getString(any()) } answers { templates[arg<Int>(0)] ?: "" }
        every { context.getString(any(), *anyVararg<Any>()) } answers {
            val template = templates[arg<Int>(0)] ?: ""
            val rawArg = if (args.size > 1) args[1] else null
            val formatArgs: Array<out Any?> =
                when (rawArg) {
                    is Array<*> -> rawArg
                    null -> emptyArray()
                    else -> arrayOf(rawArg)
                }
            if (formatArgs.isEmpty()) template else String.format(template, *formatArgs)
        }
    }

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeNotificationRepository(
        private val unread: List<WhatsAppMessage> = emptyList(),
        private val missCount: Int = 0,
    ) : NotificationRepository {
        override val allUnread: Flow<List<WhatsAppMessage>> = flowOf(unread)

        override fun unreadBySender(sender: String): Flow<List<WhatsAppMessage>> =
            flowOf(unread.filter { it.sender.equals(sender, ignoreCase = true) })

        override val parseMissCount: Flow<Int> = flowOf(missCount)

        override fun clear(sender: String) = Unit
    }

    private class FakeNotificationAccessGate(private val granted: Boolean) : NotificationAccessGate {
        override fun isGranted(): Boolean = granted
    }

    /**
     * Fake [TextGenEngine] (US-062 / SF-9.3). Test-scoped — never importing
     * MediaPipe.
     */
    private class FakeTextGenEngine(
        var nextResult: Result<String> = Result.failure(CurroError.ModelCold),
        ready: Boolean = false,
    ) : TextGenEngine {
        private val _isReady = MutableStateFlow(ready)
        override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
        var lastPrompt: String? = null
            private set
        var generateCallCount: Int = 0
            private set

        override suspend fun load(): Result<Unit> = Result.success(Unit)

        override suspend fun generate(prompt: String): Result<String> {
            generateCallCount++
            lastPrompt = prompt
            return nextResult
        }

        override suspend fun unload() {
            _isReady.value = false
        }
    }

    /**
     * Fake [ModelFiles] that lets the test control `isGemma3nAvailable()`
     * without touching the filesystem.
     */
    private class FakeModelFiles(var gemma3nPresent: Boolean = false) : ModelFiles() {
        override fun isGemma3nAvailable(): Boolean = gemma3nPresent
    }

    /**
     * Fake [TtsClient] that records every utterance. The handler calls
     * `ttsClient.speak(copy_cold_model)` directly — this records that call so
     * we can assert it happened (or did not) exactly the expected number of
     * times.
     */
    private class RecordingTtsClient : TtsClient {
        val spoken = mutableListOf<String>()

        override suspend fun speak(
            text: String,
            utteranceId: String,
        ): TtsClient.SpeakResult {
            spoken += text
            return TtsClient.SpeakResult.Completed
        }

        override fun stop() = Unit

        override fun isSpeaking(): Boolean = false
    }

    /**
     * Fake [TelemetrySink] that records every event for assertions.
     */
    private class RecordingTelemetrySink : TelemetrySink {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun event(
            name: String,
            props: Map<String, Any>,
        ) {
            events += name to props
        }

        override fun setUserProperty(
            key: String,
            value: String?,
        ) = Unit

        override fun logCrash(
            throwable: Throwable,
            fatal: Boolean,
        ) = Unit
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private data class Deps(
        val textGen: FakeTextGenEngine,
        val modelFiles: FakeModelFiles,
        val tts: RecordingTtsClient,
        val telemetry: RecordingTelemetrySink,
    )

    private fun handler(
        unread: List<WhatsAppMessage> = emptyList(),
        missCount: Int = 0,
        granted: Boolean = true,
        deps: Deps = Deps(FakeTextGenEngine(), FakeModelFiles(), RecordingTtsClient(), RecordingTelemetrySink()),
    ): ReadAllUnreadWhatsAppHandler =
        ReadAllUnreadWhatsAppHandler(
            notifications = FakeNotificationRepository(unread, missCount),
            accessGate = FakeNotificationAccessGate(granted),
            context = context,
            textGenEngine = deps.textGen,
            modelFiles = deps.modelFiles,
            promptBuilder = WhatsAppSummaryPromptBuilder(),
            cleaner = SummaryOutputCleaner(),
            ttsClient = deps.tts,
            telemetry = deps.telemetry,
        )

    /** Helper for US-062 / SF-9.3 tests that need to inspect the deps after the run. */
    private suspend fun handleWithDeps(
        unread: List<WhatsAppMessage>,
        deps: Deps,
    ): Pair<HandlerResult, Deps> {
        val h = handler(unread = unread, deps = deps)
        return h.handle(call()) to deps
    }

    private fun call(): FunctionCall = FunctionCall("read_all_unread_whatsapp", emptyMap(), confidence = 0.96f)

    private fun msg(
        sender: String,
        text: String,
        timestamp: Long = 1_000L,
        classification: Classification = Classification.TEXT,
    ) = WhatsAppMessage(
        key = "k-$sender-$timestamp",
        sender = sender,
        chatTitle = sender,
        text = text,
        isGroup = false,
        timestamp = timestamp,
        classification = classification,
    )

    private fun assertSpoken(result: HandlerResult): String {
        assertInstanceOf(HandlerResult.Spoken::class.java, result)
        return (result as HandlerResult.Spoken).speech
    }

    private fun assertFailed(result: HandlerResult): HandlerResult.Failed {
        assertInstanceOf(HandlerResult.Failed::class.java, result)
        return result as HandlerResult.Failed
    }

    // ── 1. Empty cache, no miss ───────────────────────────────────────────────

    @Test
    fun `empty cache no parse miss returns copy_no_unread`() =
        runTest {
            assertEquals("NO_UNREAD", assertSpoken(handler().handle(call())))
        }

    // ── 2. Empty cache + miss > 0 ─────────────────────────────────────────────

    @Test
    fun `empty cache with parse misses returns copy_whatsapp_parse_miss`() =
        runTest {
            assertEquals("PARSE_MISS", assertSpoken(handler(missCount = 2).handle(call())))
        }

    // ── 3. Access denied ──────────────────────────────────────────────────────

    @Test
    fun `gate denied returns Failed with NotificationAccessMissing`() =
        runTest {
            val result = handler(granted = false).handle(call())
            val failed = assertFailed(result)
            assertEquals("PERM_MISSING", failed.speech)
            assertInstanceOf(CurroError.NotificationAccessMissing::class.java, failed.reason)
        }

    // ── 4. > 8 unread → copy_many_unread ─────────────────────────────────────

    @Test
    fun `9 messages returns copy_many_unread`() =
        runTest {
            val messages = (1..9).map { msg("Pepito", "msg$it", timestamp = it.toLong()) }
            assertEquals("MANY_UNREAD", assertSpoken(handler(unread = messages).handle(call())))
        }

    // ── 5. Exactly 8 → grouped read, NOT many ─────────────────────────────────

    @Test
    fun `exactly 8 messages returns grouped read not many`() =
        runTest {
            val messages = (1..8).map { msg("Pepito", "msg$it", timestamp = it.toLong()) }
            val result = assertSpoken(handler(unread = messages).handle(call()))
            assertTrue(!result.contains("MANY_UNREAD"), "should NOT be the many-unread line")
            assertTrue(result.startsWith("MANY:8:Pepito"), "header should be MANY:8:Pepito")
        }

    // ── 6. 1 sender, 1 msg ────────────────────────────────────────────────────

    @Test
    fun `1 sender 1 msg returns one-header and single body`() =
        runTest {
            val result = assertSpoken(handler(unread = listOf(msg("Pepito", "Te espero a las siete"))).handle(call()))
            assertEquals("ONE:1:Pepito START:Pepito Te espero a las siete.", result)
        }

    // ── 7. 1 sender, 3 msgs — chronological bodies joined by ". " ──────────────

    @Test
    fun `1 sender 3 msgs returns many-header and chronological bodies`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "Te espero a las siete", timestamp = 1_000L),
                    msg("Pepito", "Trae el pan", timestamp = 2_000L),
                    msg("Pepito", "Y vino si puedes", timestamp = 3_000L),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            assertEquals(
                "MANY:3:Pepito START:Pepito Te espero a las siete. Trae el pan. Y vino si puedes.",
                result,
            )
        }

    // ── 8. 2 senders (3 + 1) — spec §6 flow-5 canonical ──────────────────────

    @Test
    fun `2 senders 3 plus 1 messages returns multi-sender header and two groups`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "Te espero a las siete", timestamp = 1_000L),
                    msg("Pepito", "Trae el pan", timestamp = 2_000L),
                    msg("Lucía", "Mañana te llamo", timestamp = 4_000L),
                    msg("Pepito", "Y vino si puedes", timestamp = 3_000L),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            // Lucía is most-recent (t=4000) → first group (START:), Pepito second (FROM:)
            val expected =
                "MULTI:1:Lucía:3:Pepito START:Lucía Mañana te llamo." +
                    " FROM:Pepito:Te espero a las siete. Trae el pan. Y vino si puedes."
            assertEquals(expected, result)
        }

    // ── 9. 3 senders → three-plus header + 3 group bodies ────────────────────

    @Test
    fun `3 senders returns three-plus header and three groups`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "hola", timestamp = 3_000L),
                    msg("Lucía", "que tal", timestamp = 2_000L),
                    msg("Carmen", "bien", timestamp = 1_000L),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            assertTrue(result.startsWith("3PLUS:Pepito:Lucía:Carmen"), "header should list all 3")
            assertTrue(result.contains("START:Pepito"), "Pepito first (most-recent)")
            assertTrue(result.contains("FROM:Lucía"), "Lucía second")
            assertTrue(result.contains("FROM:Carmen"), "Carmen third")
        }

    // ── 10. 4 senders → header reads first 3; body has all 4 groups ──────────

    @Test
    fun `4 senders header reads first 3 but body has all 4 groups`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "hola", timestamp = 4_000L),
                    msg("Lucía", "adios", timestamp = 3_000L),
                    msg("Carmen", "hasta luego", timestamp = 2_000L),
                    msg("Marisa", "nos vemos", timestamp = 1_000L),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            assertTrue(result.startsWith("3PLUS:Pepito:Lucía:Carmen"), "header: top-3 only")
            assertTrue(result.contains("FROM:Marisa"), "Marisa must appear in body")
        }

    // ── 11. All-emoji group ────────────────────────────────────────────────────

    @Test
    fun `all emoji messages from one sender`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "[emoji]", timestamp = 1_000L, classification = Classification.EMOJI),
                    msg("Pepito", "[emoji]", timestamp = 2_000L, classification = Classification.EMOJI),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            assertEquals(
                "MANY:2:Pepito START:Pepito te ha mandado un emoji. te ha mandado un emoji.",
                result,
            )
        }

    // ── 12. Mixed classifications ─────────────────────────────────────────────

    @Test
    fun `mixed classification group bodies are each correctly mapped`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "texto", timestamp = 1_000L, classification = Classification.TEXT),
                    msg("Pepito", "[emoji]", timestamp = 2_000L, classification = Classification.EMOJI),
                    msg("Pepito", "[audio]", timestamp = 3_000L, classification = Classification.VOICE_NOTE),
                    msg("Pepito", "[foto]", timestamp = 4_000L, classification = Classification.IMAGE),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            val expected =
                "MANY:4:Pepito START:Pepito texto." +
                    " te ha mandado un emoji. te ha mandado un audio. te ha mandado una foto."
            assertEquals(expected, result)
        }

    // ── 13. Group-chat: per-Person sender preserved ───────────────────────────

    @Test
    fun `group chat messages keyed by per-person sender are grouped correctly`() =
        runTest {
            val messages =
                listOf(
                    msg("Ana", "mensaje de Ana", timestamp = 1_000L),
                    msg("Ana", "otro de Ana", timestamp = 2_000L),
                    msg("Pepe", "de Pepe", timestamp = 3_000L),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            assertTrue(result.startsWith("MULTI:1:Pepe:2:Ana"), "Pepe most-recent, 2 from Ana")
        }

    // ── 14. Within-group chronological order ──────────────────────────────────

    @Test
    fun `within a group messages are in chronological order oldest first`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "tercero", timestamp = 3_000L),
                    msg("Pepito", "primero", timestamp = 1_000L),
                    msg("Pepito", "segundo", timestamp = 2_000L),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            // Check body order: primero → segundo → tercero
            val bodyPart = result.substringAfter("START:Pepito ")
            assertEquals("primero. segundo. tercero.", bodyPart)
        }

    // ── 15. Sender order: most-recently-active first ──────────────────────────

    @Test
    fun `groups are sorted most-recently-active first`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "old", timestamp = 1_000L),
                    msg("Lucía", "new", timestamp = 5_000L),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            // Lucía most-recent: MULTI:1:Lucía:1:Pepito
            assertTrue(result.startsWith("MULTI:1:Lucía:1:Pepito"), "Lucía should come first")
        }

    // ── 16. OTHER classification inline fallback ───────────────────────────────

    @Test
    fun `OTHER classification body is inline fallback not a handler-level miss`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "texto", timestamp = 1_000L, classification = Classification.TEXT),
                    msg("Pepito", "unknown", timestamp = 2_000L, classification = Classification.OTHER),
                )
            val result = assertSpoken(handler(unread = messages).handle(call()))
            assertEquals(
                "MANY:2:Pepito START:Pepito texto. no he podido leer ese mensaje.",
                result,
            )
        }

    // ── US-062 / SF-9.3 — summarisation branch (> 8 unread) ────────────────────

    private fun manyMessages(): List<WhatsAppMessage> =
        listOf(
            msg("Pepito", "Te espero a las siete", timestamp = 1_000L),
            msg("Pepito", "Trae el pan", timestamp = 2_000L),
            msg("Pepito", "Y vino si puedes", timestamp = 3_000L),
            msg("Lucía", "Mañana te llamo, papá", timestamp = 4_000L),
            msg("Lucía", "¿Estás en casa?", timestamp = 5_000L),
            msg("Carmen", "¿Cómo te encuentras?", timestamp = 6_000L),
            msg("Carmen", "¿Necesitas algo del super?", timestamp = 7_000L),
            msg("Pepito", "Y aceite", timestamp = 8_000L),
            msg("Pepito", "Sin falta", timestamp = 9_000L),
            msg("Carmen", "Voy yo", timestamp = 10_000L),
        )

    @Test
    fun `gt8 textGen ready and generate succeeds speaks intro plus cleaned summary, no cold line`() =
        runTest {
            val deps =
                Deps(
                    textGen =
                        FakeTextGenEngine(
                            nextResult = Result.success("De Carmen: voy yo. De Pepito: trae pan."),
                            ready = true,
                        ),
                    modelFiles = FakeModelFiles(gemma3nPresent = true),
                    tts = RecordingTtsClient(),
                    telemetry = RecordingTelemetrySink(),
                )
            val (result, d) = handleWithDeps(manyMessages(), deps)
            val speech = assertSpoken(result)
            assertEquals("INTRO De Carmen: voy yo. De Pepito: trae pan.", speech)
            assertTrue(d.tts.spoken.none { it == "COLD" }, "must NOT speak the cold-model line when engine ready")
            val ev = d.telemetry.events.single { it.first == "summary_generated" }.second
            assertEquals("success", ev["outcome"])
            assertEquals(false, ev["cold_spoken"])
        }

    @Test
    fun `gt8 textGen cold but weights present speaks cold model once then summary`() =
        runTest {
            val deps =
                Deps(
                    textGen =
                        FakeTextGenEngine(
                            nextResult = Result.success("De Pepito: vale"),
                            ready = false,
                        ),
                    modelFiles = FakeModelFiles(gemma3nPresent = true),
                    tts = RecordingTtsClient(),
                    telemetry = RecordingTelemetrySink(),
                )
            val (result, d) = handleWithDeps(manyMessages(), deps)
            assertEquals("INTRO De Pepito: vale", assertSpoken(result))
            assertEquals(1, d.tts.spoken.count { it == "COLD" }, "exactly one cold-model line")
            assertEquals(1, d.textGen.generateCallCount, "generate called exactly once")
            val ev = d.telemetry.events.single { it.first == "summary_generated" }.second
            assertEquals("success", ev["outcome"])
            assertEquals(true, ev["cold_spoken"])
        }

    @Test
    fun `gt8 textGen weights missing falls back to copy_many_unread no cold line`() =
        runTest {
            val deps =
                Deps(
                    textGen =
                        FakeTextGenEngine(
                            nextResult = Result.failure(CurroError.ModelCold),
                            ready = false,
                        ),
                    modelFiles = FakeModelFiles(gemma3nPresent = false),
                    tts = RecordingTtsClient(),
                    telemetry = RecordingTelemetrySink(),
                )
            val (result, d) = handleWithDeps(manyMessages(), deps)
            assertEquals("MANY_UNREAD", assertSpoken(result))
            assertTrue(
                d.tts.spoken.none { it == "COLD" },
                "no cold-model line when weights missing — would tease the user",
            )
            assertEquals(
                1,
                d.textGen.generateCallCount,
                "engine.generate is still called; the engine itself short-circuits",
            )
            val ev = d.telemetry.events.single { it.first == "summary_generated" }.second
            assertEquals("fallback_cold", ev["outcome"])
            assertEquals(false, ev["cold_spoken"])
        }

    @Test
    fun `gt8 generate returns OutOfMemory falls back to copy_many_unread`() =
        runTest {
            val deps =
                Deps(
                    textGen =
                        FakeTextGenEngine(
                            nextResult = Result.failure(CurroError.OutOfMemory),
                            ready = true,
                        ),
                    modelFiles = FakeModelFiles(gemma3nPresent = true),
                    tts = RecordingTtsClient(),
                    telemetry = RecordingTelemetrySink(),
                )
            val (result, d) = handleWithDeps(manyMessages(), deps)
            assertEquals("MANY_UNREAD", assertSpoken(result))
            val ev = d.telemetry.events.single { it.first == "summary_generated" }.second
            assertEquals("fallback_oom", ev["outcome"])
        }

    @Test
    fun `gt8 generate returns InvalidFunctionCall falls back to copy_many_unread`() =
        runTest {
            val deps =
                Deps(
                    textGen =
                        FakeTextGenEngine(
                            nextResult = Result.failure(CurroError.InvalidFunctionCall),
                            ready = true,
                        ),
                    modelFiles = FakeModelFiles(gemma3nPresent = true),
                    tts = RecordingTtsClient(),
                    telemetry = RecordingTelemetrySink(),
                )
            val (result, d) = handleWithDeps(manyMessages(), deps)
            assertEquals("MANY_UNREAD", assertSpoken(result))
            val ev = d.telemetry.events.single { it.first == "summary_generated" }.second
            assertEquals("fallback_invalid_output", ev["outcome"])
        }

    @Test
    fun `gt8 cold spoken never repeated within same invocation`() =
        runTest {
            // Regression: if a future refactor accidentally calls the cold-model
            // line twice (e.g. once in a pre-check, once before generate), this
            // test fails. Today the single call site is hard to misuse, but a
            // canary test is cheap insurance.
            val deps =
                Deps(
                    textGen =
                        FakeTextGenEngine(
                            nextResult = Result.success("De Pepito: vale"),
                            ready = false,
                        ),
                    modelFiles = FakeModelFiles(gemma3nPresent = true),
                    tts = RecordingTtsClient(),
                    telemetry = RecordingTelemetrySink(),
                )
            val (_, d) = handleWithDeps(manyMessages(), deps)
            assertEquals(
                1,
                d.tts.spoken.count { it == "COLD" },
                "cold-model line spoken AT MOST ONCE per handler invocation",
            )
        }

    @Test
    fun `gt8 telemetry sender_count_bucket and message_count_bucket are bucketed correctly`() =
        runTest {
            // 10 messages, 3 senders → buckets "3" + "9to12".
            val deps =
                Deps(
                    textGen =
                        FakeTextGenEngine(
                            nextResult = Result.success("..."),
                            ready = true,
                        ),
                    modelFiles = FakeModelFiles(gemma3nPresent = true),
                    tts = RecordingTtsClient(),
                    telemetry = RecordingTelemetrySink(),
                )
            val (_, d) = handleWithDeps(manyMessages(), deps)
            val ev = d.telemetry.events.single { it.first == "summary_generated" }.second
            assertEquals("3", ev["sender_count_bucket"], "3 distinct senders → bucket '3'")
            assertEquals("9to12", ev["message_count_bucket"], "10 messages → bucket '9to12'")
            // PII canary — none of the bucket props leak any name or body.
            assertFalse(ev.toString().contains("Pepito"))
            assertFalse(ev.toString().contains("Carmen"))
            assertFalse(ev.toString().contains("Lucía"))
        }

    @Test
    fun `gt8 prompt sent to engine groups by sender and includes system prompt`() =
        runTest {
            val deps =
                Deps(
                    textGen =
                        FakeTextGenEngine(
                            nextResult = Result.success("ok"),
                            ready = true,
                        ),
                    modelFiles = FakeModelFiles(gemma3nPresent = true),
                    tts = RecordingTtsClient(),
                    telemetry = RecordingTelemetrySink(),
                )
            val (_, d) = handleWithDeps(manyMessages(), deps)
            val sent = d.textGen.lastPrompt
            assertNotNull(sent, "engine.generate should have received a prompt")
            sent!!
            assertTrue(sent.contains("Eres Curro"), "system prompt header missing")
            assertTrue(sent.contains("De Pepito:"), "Pepito sender block missing")
            assertTrue(sent.contains("De Lucía:"), "Lucía sender block missing")
            assertTrue(sent.contains("De Carmen:"), "Carmen sender block missing")
        }
}
