package com.curro.app.data.ml

import com.curro.app.BuildConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ModelFiles] (US-019 / SF-3.1).
 *
 * Pure JVM, no Android framework required — `BuildConfig` is a generated class and
 * `java.io.File` works without a device. The "no .task file present" path is the
 * CI default; the contract is that `assembleDebug` + `testDebugUnitTest` are green
 * without ever shipping or downloading the weights.
 *
 * Migrated to an instance class in US-061 / SF-9.2 (instantiated directly here;
 * Hilt is not in scope for pure-JVM tests).
 */
class ModelFilesTest {
    private val files = ModelFiles()

    @Test
    fun `BuildConfig MODEL_BASE_PATH defaults to data local tmp curro-models`() {
        // The default lands in BuildConfig when local.properties has no override.
        // CI environments without local.properties hit exactly this path.
        assertEquals("/data/local/tmp/curro-models", BuildConfig.MODEL_BASE_PATH)
    }

    @Test
    fun `isFunctionGemmaAvailable is false on a clean test machine`() {
        // JVM unit tests run without a model file at the configured path.
        // The CI machine (clean checkout, no adb push) hits exactly this branch.
        assertFalse(files.isFunctionGemmaAvailable())
    }

    @Test
    fun `functionGemma resolves to the expected filename and parent`() {
        val f = files.functionGemma()
        // May 2026 swap: was "function_gemma_270m.task" → "function_gemma_270m.litertlm"
        // → "gemma3_270m_it.litertlm" (the base IT model — see on-device-decision-engine-2026.md
        // for why the FunctionGemma 270M fine-tune was dropped).
        assertEquals("gemma3_270m_it.litertlm", f.name)
        assertEquals(BuildConfig.MODEL_BASE_PATH, f.parentFile?.absolutePath)
    }
}
