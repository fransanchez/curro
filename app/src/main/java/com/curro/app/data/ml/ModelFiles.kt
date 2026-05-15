package com.curro.app.data.ml

import com.curro.app.BuildConfig
import java.io.File

/**
 * Single source of truth for on-device model file paths.
 *
 * Phase 3 (US-019 / SF-3.1) — the FunctionGemma 270M `.task` is side-loaded via
 *   `adb push function_gemma_270m.task /data/local/tmp/curro-models/`
 * and lives at `BuildConfig.MODEL_BASE_PATH/function_gemma_270m.task` (with
 * `MODEL_BASE_PATH` defaulting to `/data/local/tmp/curro-models` and overridable
 * per-machine via `local.properties` → `CURRO_MODEL_BASE_PATH`).
 *
 * A future SF (post-prototype) will introduce bundled / Play Asset Delivery for
 * release without changing this abstraction's callers — only this object needs
 * to know how the file is delivered. The downstream contract every later
 * consumer (US-020's engine, US-023's warm-up service, US-024's smoke loop)
 * reads is [isFunctionGemmaAvailable].
 */
object ModelFiles {
    /** Absolute path to the FunctionGemma 270M weights. May not exist on disk. */
    fun functionGemma(): File = File(BuildConfig.MODEL_BASE_PATH, FUNCTION_GEMMA_FILENAME)

    /** True iff the weights exist and are readable by this process. */
    fun isFunctionGemmaAvailable(): Boolean = functionGemma().let { it.exists() && it.canRead() }

    private const val FUNCTION_GEMMA_FILENAME = "function_gemma_270m.task"
}
