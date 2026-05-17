package com.curro.app.data.ml

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.curro.app.domain.repository.TextGenEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * On-device smoke test for the large-text engine's cold-load + first-inference
 * latency (US-060 / SF-9.1, hooked up in US-061 / SF-9.2; backing model
 * swapped to **Gemma 4 E2B** in May 2026 — class name + filename kept for
 * diff hygiene, see [com.curro.app.data.ml.Gemma3nEngine] KDoc).
 *
 * Loads the engine once via Hilt, runs a tiny inference, captures wall-clock
 * latencies, fails (with a clear actionable message pointing to
 * `docs/architecture/gemma-text-engine-decision.md` §Latency target) when the
 * budgets blow.
 *
 * **CI safety:** lives in `androidTest/` — never runs in
 * `./gradlew testDebugUnitTest`. If `connectedAndroidTest` ever picks it up
 * without the weights present (the CI default), the `assumeTrue` guard skips
 * it cleanly.
 *
 * **A53 baseline:** the budgets (10 s cold load / 8 s first inference) are
 * calibrated for the Samsung Galaxy A53 5G (6 GB, Exynos 1280, Android 13 +
 * One UI) — Curro's hardware floor. Production target is 3–6 s; these budgets
 * flag only the catastrophic outliers that trigger the rollback path. Gemma 4
 * E2B fits inside the same envelope as Gemma 3n (PLE preserved); the budgets
 * are unchanged across the swap.
 *
 * Stays on JUnit 4 + AndroidJUnit4 — instrumented tests cannot use JUnit 5
 * (AGP limitation; see `MainActivityHiltSmokeTest`).
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class Gemma3nSmokeTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var engine: TextGenEngine

    @Inject
    lateinit var modelFiles: ModelFiles

    @Before
    fun setUp() {
        hiltRule.inject()
        assumeTrue(
            "Skipped: large-text weights (Gemma 4 E2B) not present at " +
                "${modelFiles.gemma3n().absolutePath}. " +
                "Side-load via `adb push` before running. See models/README.md " +
                "(Cómo bajar los pesos — Gemma 4 E2B — Phase 9).",
            modelFiles.isGemma3nAvailable(),
        )
    }

    @After
    fun tearDown() {
        runBlocking { engine.unload() }
    }

    @Test
    fun cold_load_and_first_inference_meet_budgets() {
        val coldStart = System.currentTimeMillis()
        runBlocking { engine.load().getOrThrow() }
        val coldLoadMs = System.currentTimeMillis() - coldStart
        Log.i(TAG, "cold-load = ${coldLoadMs}ms")

        val genStart = System.currentTimeMillis()
        val out =
            runBlocking {
                engine
                    .generate("Resume en una frase: 'Hola, ¿cómo estás?' Salida:")
                    .getOrThrow()
            }
        val genMs = System.currentTimeMillis() - genStart
        Log.i(TAG, "first-inference = ${genMs}ms; output = ${out.length} chars")

        assertTrue(
            "cold-load blew the ${COLD_LOAD_BUDGET_MS}ms budget (${coldLoadMs}ms). " +
                "Rollback per docs/architecture/gemma-text-engine-decision.md §Latency target.",
            coldLoadMs <= COLD_LOAD_BUDGET_MS,
        )
        assertTrue(
            "first-inference blew the ${FIRST_INFERENCE_BUDGET_MS}ms budget (${genMs}ms). " +
                "Rollback per docs/architecture/gemma-text-engine-decision.md §Latency target.",
            genMs <= FIRST_INFERENCE_BUDGET_MS,
        )
    }

    private companion object {
        const val TAG = "Curro/Gemma4Smoke"

        // Calibrated for the Samsung Galaxy A53 5G (6 GB, Exynos 1280, Android 13 + One UI),
        // Curro's hardware floor. Generous on purpose: production target is 3–6 s; these
        // budgets flag only the catastrophic outliers that trigger the rollback path.
        // Unchanged across the Gemma 3n → Gemma 4 E2B swap (PLE preserved).
        const val COLD_LOAD_BUDGET_MS = 10_000L
        const val FIRST_INFERENCE_BUDGET_MS = 8_000L
    }
}
