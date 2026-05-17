package com.curro.app.data.ml

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
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
 * The minimal LLM surface [Gemma3nEngine] consumes. Production impl wraps
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
            val opts =
                LlmInferenceOptions
                    .builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(maxTokens)
                    .setTopK(topK)
                    .setTemperature(temperature)
                    .build()
            val llm = LlmInference.createFromOptions(context, opts)
            return MediaPipeLlmSession(llm)
        }
    }

private class MediaPipeLlmSession(
    private val llm: LlmInference,
) : LlmSession {
    override fun generateResponse(prompt: String): String = llm.generateResponse(prompt)

    override fun close() = llm.close()
}
