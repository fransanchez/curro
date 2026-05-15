package com.curro.app.data.ml.fakes

import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.PromptContext
import com.curro.app.domain.repository.FunctionCallEngine

/**
 * Test fake for [FunctionCallEngine] (US-020 / SF-3.2).
 *
 * Lives in the test source set so production never imports it. Reused by
 * SF-3.6 (US-024) and every subsequent SF that has the engine as a
 * collaborator (handlers in Phase 4 onward).
 *
 * Configurable knobs:
 * - [nextResult] — whatever [decide] returns next; tests reassign per case.
 * - [isReadyValue] — what [isReady] returns. The fake does NOT flip this
 *   inside [warmUp] (tests configure it directly so the FSM transitions are
 *   deterministic).
 *
 * Observable knobs:
 * - [lastUtterance] / [lastContext] — the most recent (utterance, ctx) the
 *   smoke loop handed to [decide].
 * - [warmUpCallCount] — how many times [warmUp] was called (the smoke loop's
 *   cold-engine path kicks it as a side effect, so this is observed).
 */
class FakeFunctionCallEngine(
    var nextResult: Result<String> = Result.failure(CurroError.ModelCold),
    var isReadyValue: Boolean = false,
) : FunctionCallEngine {
    var lastUtterance: String? = null
        private set
    var lastContext: PromptContext? = null
        private set
    var warmUpCallCount: Int = 0
        private set

    override suspend fun decide(
        utterance: String,
        ctx: PromptContext,
    ): Result<String> {
        lastUtterance = utterance
        lastContext = ctx
        return nextResult
    }

    override fun warmUp() {
        warmUpCallCount++
        // Mirror real impl: warmUp does NOT flip isReady here — tests set
        // isReadyValue directly, since flipping it inside warmUp would couple
        // the fake to the real impl's "loaded on success" semantics.
    }

    override fun isReady(): Boolean = isReadyValue
}
