package com.curro.app.data.ml

import com.curro.app.BuildConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for on-device model file paths.
 *
 * Phase 3 (US-019 / SF-3.1) — the FunctionGemma 270M `.task` is side-loaded via
 *   `adb push function_gemma_270m.task /data/local/tmp/curro-models/`
 * and lives at `BuildConfig.MODEL_BASE_PATH/function_gemma_270m.task` (with
 * `MODEL_BASE_PATH` defaulting to `/data/local/tmp/curro-models` and overridable
 * per-machine via `local.properties` → `CURRO_MODEL_BASE_PATH`).
 *
 * Phase 9 (US-061 / SF-9.2; **backing model swapped to Gemma 4 E2B in May
 * 2026**) — the large-text model file (~2.5 GB int4, Apache 2.0) joins the
 * party, side-loaded under the same base path. Adds [gemma3n] +
 * [isGemma3nAvailable]; CI stays green because `isGemma3nAvailable() == false`
 * is the CI default and `Gemma3nEngine.load()` short-circuits cleanly when it
 * is. The method names retain the `gemma3n` suffix for diff hygiene after the
 * Gemma 3n → Gemma 4 swap; only the filename string ([GEMMA_LARGE_TEXT_FILENAME])
 * changes. A future SF may rename to `largeText()` / `isLargeTextAvailable()`.
 *
 * **Migrated from `object` to `@Singleton class` in US-061 / SF-9.2** so the
 * (pure) presence check is injectable + substitutable in JVM tests without
 * static mocking. Hilt's default member-injection + the `@Inject` constructor
 * give us this for free; no module entry needed. Production behaviour is
 * unchanged (the methods still read [BuildConfig.MODEL_BASE_PATH] + delegate
 * to `java.io.File`).
 *
 * A future SF (post-prototype) will introduce bundled / Play Asset Delivery for
 * release without changing this abstraction's callers — only this class needs
 * to know how the file is delivered. The downstream contract every later
 * consumer reads is the pair of `is<Name>Available()` predicates.
 */
@Singleton
open class ModelFiles
    @Inject
    constructor() {
        // ── FunctionGemma 270M (Phase 3) ──────────────────────────────────────

        /** Absolute path to the FunctionGemma 270M weights. May not exist on disk. */
        open fun functionGemma(): File = File(BuildConfig.MODEL_BASE_PATH, FUNCTION_GEMMA_FILENAME)

        /** True iff the FunctionGemma weights exist and are readable by this process. */
        open fun isFunctionGemmaAvailable(): Boolean = functionGemma().let { it.exists() && it.canRead() }

        // ── Large-text generation model (Phase 9 — Gemma 4 E2B since May 2026) ─

        /**
         * Absolute path to the large-text generation weights (currently
         * Gemma 4 E2B, ~2.5 GB int4). May not exist on disk (CI default).
         *
         * Method name retains the `gemma3n` suffix for diff hygiene — the
         * filename string changed from `gemma3n_e2b.task` to `gemma4_e2b.task`
         * during the swap, but every caller already references `gemma3n()`.
         */
        open fun gemma3n(): File = File(BuildConfig.MODEL_BASE_PATH, GEMMA_LARGE_TEXT_FILENAME)

        /** True iff the large-text weights exist and are readable by this process. */
        open fun isGemma3nAvailable(): Boolean = gemma3n().let { it.exists() && it.canRead() }

        private companion object {
            // MediaPipe 0.10.35's loader branches by file extension:
            //   - `.task` → unzip into model.tflite + tokenizer + metadata (legacy bundle)
            //   - `.litertlm` → native LiteRT-LM flatbuffer loader (Gemma 4 + new FunctionGemma)
            // The HF distributions ship as `.litertlm` for both models we use; keep the
            // extension to avoid the "Unable to open zip archive" error from the ZIP loader.
            const val FUNCTION_GEMMA_FILENAME = "function_gemma_270m.litertlm"
            const val GEMMA_LARGE_TEXT_FILENAME = "gemma4_e2b.litertlm"
        }
    }
