package com.curro.app.data.ml

import com.curro.app.BuildConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pure-JVM tests for the large-text-engine surface of [ModelFiles]
 * (US-061 / SF-9.2; backing model swapped to Gemma 4 E2B in May 2026 —
 * class name kept as `ModelFilesGemma3nTest` for diff hygiene, the
 * `gemma3n()` / `isGemma3nAvailable()` method names are similarly retained;
 * see [ModelFiles] KDoc).
 *
 * Sibling of [ModelFilesTest] (which covers the FunctionGemma surface). Both
 * land on the same path resolution under [BuildConfig.MODEL_BASE_PATH].
 */
class ModelFilesGemma3nTest {
    private val files = ModelFiles()

    /**
     * Tracks any file the per-test branch creates so [AfterEach] can clean it up
     * even if a test throws partway through. Without this, a failed assertion
     * in `isGemma3nAvailable reflects file existence` would leave the file on
     * disk and poison every subsequent run on the same machine.
     */
    private var createdForCleanup: File? = null

    @AfterEach
    fun tearDown() {
        createdForCleanup?.delete()
        createdForCleanup = null
    }

    @Test
    fun `gemma3n resolves to the expected filename and parent`() {
        val f = files.gemma3n()
        // Gemma 4 E2B since the May 2026 swap (was "gemma3n_e2b.task").
        assertEquals("gemma4_e2b.litertlm", f.name)
        assertEquals(BuildConfig.MODEL_BASE_PATH, f.parentFile?.absolutePath)
    }

    @Test
    fun `isGemma3nAvailable is false on a clean test machine`() {
        // CI default: no side-loaded weights at /data/local/tmp/curro-models.
        // We don't assert directly on the path string — only on the public contract.
        assertFalse(files.isGemma3nAvailable())
    }

    @Test
    fun `isGemma3nAvailable reflects file existence`() {
        // We can't write to /data/local/tmp on a Mac, so this test points
        // [ModelFiles] at a tmp dir via a subtype. The contract under test is the
        // "exists && canRead" predicate, not the path resolution.
        val tmp = createTempDir(prefix = "curro-model-test-")
        try {
            val rewired =
                object : ModelFiles() {
                    override fun gemma3n(): File = File(tmp, "gemma4_e2b.litertlm")
                }
            assertFalse(rewired.isGemma3nAvailable(), "no file → false")

            val weight = File(tmp, "gemma4_e2b.litertlm")
            createdForCleanup = weight
            weight.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
            assertTrue(rewired.isGemma3nAvailable(), "file present + readable → true")

            assertTrue(weight.delete(), "expected to delete the temp weight")
            createdForCleanup = null
            assertFalse(rewired.isGemma3nAvailable(), "file gone → false again")
        } finally {
            tmp.deleteRecursively()
        }
    }
}
