package com.curro.app.data.ml

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indirection seam so JVM tests can substitute a fake `LlmInference` without
 * bringing the MediaPipe native runtime into the unit-test classpath
 * (US-061 / SF-9.2).
 *
 * Two layers of insulation:
 *  1. The [LlmSession] interface — the minimal surface our engine consumes
 *     (`generateResponse` + `close`). The production impl wraps
 *     `com.google.mediapipe.tasks.genai.llminference.LlmInference`; JVM
 *     tests substitute a `mockk<LlmSession>()` that never references
 *     `LlmInference` and therefore never triggers MediaPipe's native
 *     `<clinit>` (which calls `System.loadLibrary` and fails on the JVM).
 *  2. The factory ALSO owns the [LlmInferenceOptions] construction, because
 *     touching `LlmInferenceOptions.builder()` also loads MediaPipe classes.
 *
 * The same shape applies to FunctionGemma; refactoring `FunctionGemmaEngine`
 * to also go through this factory is a future cleanup (out of scope for
 * US-061 — `FunctionGemmaEngine` is JVM-tested at the [FunctionCallEngine]
 * interface via `FakeFunctionCallEngine`).
 */
interface LlmInferenceFactory {
    fun create(
        modelPath: String,
        maxTokens: Int,
        topK: Int,
        temperature: Float,
    ): LlmSession
}

/**
 * The minimal LLM surface [Gemma3nEngine] consumes (Gemma 4 E2B as of the
 * May 2026 swap — the engine class name predates it). Production impl wraps
 * MediaPipe's [LlmInference]; JVM-test impls are plain `mockk<LlmSession>()`.
 */
interface LlmSession : AutoCloseable {
    fun generateResponse(prompt: String): String

    override fun close()
}

@Singleton
class DefaultLlmInferenceFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LlmInferenceFactory {
        override fun create(
            modelPath: String,
            maxTokens: Int,
            topK: Int,
            temperature: Float,
        ): LlmSession {
            // MediaPipe 0.10.20+ split model options from session options:
            //   - LlmInferenceOptions = model wiring (path, max tokens, max top-K cap)
            //   - LlmInferenceSessionOptions = per-call generation (topK, temperature)
            val opts =
                LlmInferenceOptions
                    .builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(maxTokens)
                    .setMaxTopK(topK)
                    .build()
            val llm = LlmInference.createFromOptions(context, opts)
            return MediaPipeLlmSession(llm, topK, temperature)
        }
    }

private class MediaPipeLlmSession(
    private val llm: LlmInference,
    private val topK: Int,
    private val temperature: Float,
) : LlmSession {
    override fun generateResponse(prompt: String): String {
        // Single-shot: open a fresh session, add the prompt, generate, close.
        // Avoids context bleed between independent summarisations.
        val sessionOpts =
            LlmInferenceSessionOptions
                .builder()
                .setTopK(topK)
                .setTemperature(temperature)
                .build()
        return LlmInferenceSession.createFromOptions(llm, sessionOpts).use { session ->
            session.addQueryChunk(prompt)
            session.generateResponse()
        }
    }

    override fun close() = llm.close()
}
