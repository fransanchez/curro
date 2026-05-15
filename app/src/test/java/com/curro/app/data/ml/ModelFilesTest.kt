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
 */
class ModelFilesTest {
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
        assertFalse(ModelFiles.isFunctionGemmaAvailable())
    }

    @Test
    fun `functionGemma resolves to the expected filename and parent`() {
        val f = ModelFiles.functionGemma()
        assertEquals("function_gemma_270m.task", f.name)
        assertEquals(BuildConfig.MODEL_BASE_PATH, f.parentFile?.absolutePath)
    }
}
