package com.curro.app.data.ml

import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.TelemetrySink
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure-JVM tests for [Gemma3nEngine] (US-061 / SF-9.2).
 *
 * MediaPipe's native runtime is never touched here: the engine takes an
 * [LlmInferenceFactory] which the test substitutes with a fake that returns
 * a `mockk<LlmInference>()`. `ModelFiles` is a `@Singleton class` (migrated
 * in US-061) — substituted with a `FakeModelFiles` so the
 * weights-present / weights-missing branches are deterministic.
 *
 * Real-engine verification is `Gemma3nSmokeTest` (instrumented; needs a
 * device + side-loaded weights — see `models/README.md`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("Gemma3nEngine (SF-9.2)")
class Gemma3nEngineTest {
    private lateinit var modelFiles: FakeModelFiles
    private lateinit var factory: FakeLlmInferenceFactory
    private lateinit var telemetry: RecordingTelemetrySink

    @BeforeEach
    fun setUp() {
        modelFiles = FakeModelFiles(present = true)
        factory = FakeLlmInferenceFactory()
        telemetry = RecordingTelemetrySink()
    }

    private fun engine() =
        Gemma3nEngine(
            modelFiles = modelFiles,
            factory = factory,
            io = UnconfinedTestDispatcher(),
            telemetry = telemetry,
        )

    // ── load() ─────────────────────────────────────────────────────────────

    @Test
    fun `load succeeds when weights present and flips isReady true`() =
        runTest {
            val e = engine()
            val before = e.isReady.value
            val result = e.load()
            assertTrue(result.isSuccess, "expected Result.success(Unit) got $result")
            assertFalse(before, "isReady was true before load")
            assertTrue(e.isReady.value, "isReady should be true after load")
            assertEquals(1, factory.createCallCount)
        }

    @Test
    fun `load is idempotent - second call does not recreate LlmInference`() =
        runTest {
            val e = engine()
            e.load().getOrThrow()
            e.load().getOrThrow()
            assertEquals(1, factory.createCallCount, "factory.create should be called exactly once")
        }

    @Test
    fun `load returns ModelCold when weights absent and never calls factory`() =
        runTest {
            modelFiles.present = false
            val e = engine()
            val result = e.load()
            assertTrue(result.isFailure)
            assertInstanceOf(CurroError.ModelCold::class.java, result.exceptionOrNull())
            assertFalse(e.isReady.value)
            assertEquals(0, factory.createCallCount, "factory.create must not be called when weights are missing")
        }

    @Test
    fun `load returns OutOfMemory when factory throws OOM and leaves isReady false`() =
        runTest {
            factory.throwOnCreate = OutOfMemoryError("native alloc failed")
            val e = engine()
            val result = e.load()
            assertTrue(result.isFailure)
            assertInstanceOf(CurroError.OutOfMemory::class.java, result.exceptionOrNull())
            assertFalse(e.isReady.value)
        }

    @Test
    fun `load returns ModelCold when factory throws non-OOM`() =
        runTest {
            factory.throwOnCreate = IllegalStateException("MediaPipe bridge failed")
            val e = engine()
            val result = e.load()
            assertTrue(result.isFailure)
            assertInstanceOf(CurroError.ModelCold::class.java, result.exceptionOrNull())
            assertFalse(e.isReady.value)
        }

    // ── generate() ─────────────────────────────────────────────────────────

    @Test
    fun `generate auto-loads when not ready then returns raw output`() =
        runTest {
            factory.nextResponse = "De Pepito: te espera a las siete."
            val e = engine()
            val result = e.generate("any prompt")
            assertTrue(result.isSuccess)
            assertEquals("De Pepito: te espera a las siete.", result.getOrThrow())
            assertEquals(1, factory.createCallCount, "auto-load should create the engine once")
        }

    @Test
    fun `generate returns raw output when already loaded`() =
        runTest {
            factory.nextResponse = "  output verbatim  "
            val e = engine()
            e.load().getOrThrow()
            val result = e.generate("p")
            assertEquals("  output verbatim  ", result.getOrThrow())
            assertEquals(1, factory.createCallCount, "no second create on warm path")
        }

    @Test
    fun `generate OOM auto-unloads and returns OutOfMemory and leaves isReady false`() =
        runTest {
            factory.throwOnGenerate = OutOfMemoryError("inference OOM")
            val e = engine()
            e.load().getOrThrow()
            val result = e.generate("p")
            assertTrue(result.isFailure)
            assertInstanceOf(CurroError.OutOfMemory::class.java, result.exceptionOrNull())
            assertFalse(e.isReady.value, "engine must auto-unload after OOM during generate")
        }

    @Test
    fun `generate non-OOM throwable returns InvalidFunctionCall and stays ready`() =
        runTest {
            factory.throwOnGenerate = IllegalStateException("bridge died")
            val e = engine()
            e.load().getOrThrow()
            val result = e.generate("p")
            assertTrue(result.isFailure)
            assertInstanceOf(CurroError.InvalidFunctionCall::class.java, result.exceptionOrNull())
            assertTrue(e.isReady.value, "non-OOM throwable should NOT unload — the engine is still usable")
        }

    @Test
    fun `generate propagates ModelCold when weights absent and auto-load fails`() =
        runTest {
            modelFiles.present = false
            val e = engine()
            val result = e.generate("p")
            assertTrue(result.isFailure)
            assertInstanceOf(CurroError.ModelCold::class.java, result.exceptionOrNull())
        }

    // ── unload() ───────────────────────────────────────────────────────────

    @Test
    fun `unload clears llm and sets isReady false and calls close exactly once`() =
        runTest {
            val e = engine()
            e.load().getOrThrow()
            assertTrue(e.isReady.value)
            e.unload()
            assertFalse(e.isReady.value)
            verify(exactly = 1) { factory.lastCreated!!.close() }
        }

    @Test
    fun `unload is idempotent - second call is a no-op`() =
        runTest {
            val e = engine()
            e.load().getOrThrow()
            e.unload()
            e.unload()
            verify(exactly = 1) { factory.lastCreated!!.close() }
        }

    // ── EngineMetrics ──────────────────────────────────────────────────────

    @Test
    fun `gemma3nLastLoadLatencyMs is null until first load then non-null`() =
        runTest {
            val e = engine()
            assertNull(e.gemma3nLastLoadLatencyMs())
            e.load().getOrThrow()
            val captured = e.gemma3nLastLoadLatencyMs()
            assertNotNull(captured, "lastLoadMs should be captured after a successful load")
            assertTrue(captured!! >= 0)
        }

    @Test
    fun `gemma3nLastGenerateLatencyMs is null until first generate then non-null`() =
        runTest {
            factory.nextResponse = "x"
            val e = engine()
            assertNull(e.gemma3nLastGenerateLatencyMs())
            e.generate("p").getOrThrow()
            val captured = e.gemma3nLastGenerateLatencyMs()
            assertNotNull(captured, "lastGenerateMs should be captured after a successful generate")
            assertTrue(captured!! >= 0)
        }

    // ── Telemetry ──────────────────────────────────────────────────────────

    @Test
    fun `telemetry emits exactly one model_loaded event with gemma4_e2b model name`() =
        runTest {
            val e = engine()
            e.load().getOrThrow()
            val matching = telemetry.events.filter { it.first == "model_loaded" }
            assertEquals(1, matching.size, "exactly one model_loaded event")
            val props = matching.first().second
            // Gemma 4 E2B since the May 2026 swap (was "gemma3n_e2b"); class
            // name + test class name retained for diff hygiene.
            assertEquals("gemma4_e2b", props["model"])
            assertEquals(true, props["cold_start"])
            assertNotNull(props["load_ms"])
        }

    // ── Fakes ──────────────────────────────────────────────────────────────

    private class FakeModelFiles(var present: Boolean) : ModelFiles() {
        override fun isGemma3nAvailable(): Boolean = present
    }

    private class FakeLlmInferenceFactory : LlmInferenceFactory {
        var createCallCount: Int = 0
            private set
        var throwOnCreate: Throwable? = null
        var throwOnGenerate: Throwable? = null
        var nextResponse: String = ""
        var lastCreated: LlmSession? = null

        override fun create(
            modelPath: String,
            maxTokens: Int,
            topK: Int,
            temperature: Float,
        ): LlmSession {
            createCallCount++
            throwOnCreate?.let { throw it }
            val instance =
                mockk<LlmSession>(relaxed = false).also { mock ->
                    every { mock.generateResponse(any()) } answers {
                        throwOnGenerate?.let { throw it }
                        nextResponse
                    }
                    every { mock.close() } just Runs
                }
            lastCreated = instance
            return instance
        }
    }

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
}
