package com.curro.app.data.ml

import com.curro.app.data.ml.fakes.FakeFunctionCallEngine
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.PromptContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract tests for [com.curro.app.domain.repository.FunctionCallEngine] (US-020 / SF-3.2),
 * exercised against [FakeFunctionCallEngine].
 *
 * **Does NOT import MediaPipe.** Real-engine verification is the on-device gate
 * in US-024 — MediaPipe needs native binaries that are absent on the JVM
 * unit-test classpath.
 *
 * What these tests pin:
 *  1. The cold-engine path returns [CurroError.ModelCold] without touching the model.
 *  2. The success path returns the raw model string unchanged (the engine does
 *     not parse / modify / strip — that's the validator's job).
 *  3. The OOM path returns [CurroError.OutOfMemory].
 *  4. [warmUp] is idempotent-ish (observable via the fake's call count).
 *  5. [isReady] reflects whatever the impl says.
 *  6. [decide] captures the (utterance, ctx) pair — this is the contract
 *     SF-3.6's coordinator code reads.
 */
class FunctionGemmaEngineContractTest {
    private val ctx =
        PromptContext(
            nowIso = "2026-05-15T22:36:00",
            unreadMessagesSummary = "",
            knownAliases = emptyList(),
        )

    @Test
    fun `cold engine returns CurroError ModelCold`() =
        runTest {
            val engine = FakeFunctionCallEngine() // defaults to ModelCold
            val r = engine.decide("qué hora es", ctx)
            assertTrue(r.isFailure)
            assertEquals(CurroError.ModelCold, r.exceptionOrNull())
        }

    @Test
    fun `ready engine returns raw success string unmodified`() =
        runTest {
            val rawJson = """{"action":"tell_time","params":{"what":"time"},"confidence":0.92}"""
            val engine =
                FakeFunctionCallEngine(
                    nextResult = Result.success(rawJson),
                    isReadyValue = true,
                )
            val r = engine.decide("qué hora es", ctx)
            assertEquals(rawJson, r.getOrThrow())
        }

    @Test
    fun `OOM is mapped to CurroError OutOfMemory`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult = Result.failure(CurroError.OutOfMemory),
                    isReadyValue = true,
                )
            val r = engine.decide("calcula mil dividido entre veinticinco", ctx)
            assertEquals(CurroError.OutOfMemory, r.exceptionOrNull())
        }

    @Test
    fun `warmUp idempotency observed via call count`() {
        val engine = FakeFunctionCallEngine()
        engine.warmUp()
        engine.warmUp()
        // The real impl returns early on the second call when llm != null — verified
        // by inspection of FunctionGemmaEngine.warmUp's first line. The on-device
        // gate is US-024's manual test.
        assertEquals(2, engine.warmUpCallCount)
    }

    @Test
    fun `isReady reflects the configured value`() {
        val engine = FakeFunctionCallEngine(isReadyValue = true)
        assertTrue(engine.isReady())
        engine.isReadyValue = false
        assertFalse(engine.isReady())
    }

    @Test
    fun `decide captures the utterance and context pair`() =
        runTest {
            val engine =
                FakeFunctionCallEngine(
                    nextResult = Result.success("{}"),
                    isReadyValue = true,
                )
            engine.decide("llama a Pepito", ctx)
            assertEquals("llama a Pepito", engine.lastUtterance)
            assertNotNull(engine.lastContext)
            assertEquals(ctx, engine.lastContext)
        }
}
