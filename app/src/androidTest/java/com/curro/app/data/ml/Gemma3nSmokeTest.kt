package com.curro.app.data.ml

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test for Gemma 3n E2B cold-load + first-inference latency
 * (US-060 / SF-9.1).
 *
 * **Scope of US-060 (this file at this commit):** scaffold only. Loads no
 * weights, runs no inference. The Hilt rule + the `assumeTrue` guard +
 * placeholder logs are in place so the file:
 *  - compiles end-to-end on `connectedAndroidTest` regardless of weights,
 *  - skips cleanly if `ModelFiles.isFunctionGemmaAvailable() == false` (the
 *    only check we can make in US-060; US-061 swaps this for
 *    `ModelFiles.isGemma3nAvailable()`),
 *  - emits the `Curro/Gemma3nSmoke` logcat tag so device runs are easy to
 *    grep (`adb logcat -s Curro/Gemma3nSmoke`).
 *
 * **What US-061 adds (next commit):** inject `TextGenEngine`, swap the
 * presence check to `ModelFiles.isGemma3nAvailable()`, uncomment the
 * real `engine.load()` and `engine.generate(...)` calls below. The
 * placeholder-zero latency assertions become real budget assertions.
 *
 * **CI safety:** this test is in `androidTest/` so it never runs in
 * `./gradlew testDebugUnitTest`. CI sees zero impact from this file.
 *
 * **A53 baseline:** the budgets (10 s cold load / 8 s first inference) are
 * calibrated for the Samsung Galaxy A53 5G (6 GB, Exynos 1280, Android 13 +
 * One UI) — Curro's hardware floor. See
 * `docs/architecture/gemma-3n-decision.md` §Latency target for the rollback
 * procedure if the budgets blow on the A53.
 *
 * Stays on JUnit 4 + AndroidJUnit4 — instrumented tests cannot use JUnit 5
 * (AGP limitation; see `MainActivityHiltSmokeTest`).
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class Gemma3nSmokeTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    // US-061 will add:
    //   @Inject lateinit var engine: TextGenEngine
    // No engine injected in US-060 — the scaffold doesn't talk to MediaPipe.

    @Before
    fun setUp() {
        // US-060 guard: skip if even FunctionGemma's weights are absent — there
        // is no point asserting Gemma 3n budgets on a clean device. US-061
        // swaps this to `ModelFiles.isGemma3nAvailable()` (the canonical
        // Gemma 3n presence check) and replaces the placeholder message with
        // the side-load instructions.
        assumeTrue(
            "Skipped: model weights not present at ${ModelFiles.functionGemma().absolutePath}. " +
                "Side-load via `adb push` before running. See models/README.md " +
                "(Cómo bajar los pesos — Gemma 3n E2B — Phase 9).",
            ModelFiles.isFunctionGemmaAvailable(),
        )
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        // US-061 will add:
        //   runBlocking { engine.unload() }
    }

    @Test
    fun cold_load_and_first_inference_meet_budgets() {
        val coldStart = System.currentTimeMillis()
        // US-061 will uncomment:
        //   runBlocking { engine.load().getOrThrow() }
        val coldLoadMs = System.currentTimeMillis() - coldStart
        Log.i(TAG, "cold-load = ${coldLoadMs}ms (placeholder — US-061 will hook the real engine)")

        val genStart = System.currentTimeMillis()
        // US-061 will uncomment:
        //   val out = runBlocking {
        //       engine.generate("Resume en una frase: 'Hola, ¿cómo estás?' Salida:").getOrThrow()
        //   }
        val out = ""
        val genMs = System.currentTimeMillis() - genStart
        Log.i(TAG, "first-inference = ${genMs}ms; output = ${out.length} chars")

        assertTrue(
            "cold-load blew the ${COLD_LOAD_BUDGET_MS}ms budget (${coldLoadMs}ms). " +
                "Rollback per docs/architecture/gemma-3n-decision.md §Latency target.",
            coldLoadMs <= COLD_LOAD_BUDGET_MS,
        )
        assertTrue(
            "first-inference blew the ${FIRST_INFERENCE_BUDGET_MS}ms budget (${genMs}ms). " +
                "Rollback per docs/architecture/gemma-3n-decision.md §Latency target.",
            genMs <= FIRST_INFERENCE_BUDGET_MS,
        )
    }

    private companion object {
        const val TAG = "Curro/Gemma3nSmoke"

        // Calibrated for the Samsung Galaxy A53 5G (6 GB, Exynos 1280, Android 13 + One UI),
        // Curro's hardware floor. Generous on purpose: production target is 3–6 s; these
        // budgets flag only the catastrophic outliers that trigger the rollback path.
        const val COLD_LOAD_BUDGET_MS = 10_000L
        const val FIRST_INFERENCE_BUDGET_MS = 8_000L
    }
}
