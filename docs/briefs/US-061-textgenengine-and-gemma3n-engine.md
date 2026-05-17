# US-061 — SF-9.2 · `TextGenEngine` + `Gemma3nEngine` + on-demand load + memory-pressure safeguards

> **Spec trace:** spec §4.4 (Gemma 3n role + cold-load behaviour).
> **Master-plan:** SF-9.2.
> **Phase:** 9 — Gemma 3n content layer.
> **Depends on:** US-060 (decision doc + smoke-test scaffold),
> US-019 (sideload `ModelBasePath` plumbing), US-020 (`EngineMetrics`
> interface this story extends), US-059 (the canonical `EngineMetrics`
> binding for `FunctionGemmaEngine` — kept intact).
> **Size:** L.
> **Skills:** `on-device-llm`, `function-catalog`, `voice-interaction`,
> `testing-patterns`, `brand-design`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Domain interface `TextGenEngine` + MediaPipe-backed `Gemma3nEngine` (on-demand load, OOM-aware unload, `onTrimMemory` integration) |
| **US ID** | US-061 (master-plan SF-9.2) |
| **Phase** | 9 — Gemma 3n content layer |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | ondevice-ai-engineer |

---

## 1. Summary

Land the on-device Gemma 3n NL-generation engine. The contract: every
caller talks to a `TextGenEngine` interface in `domain/repository/`; the
implementation `Gemma3nEngine` lives in `data/ml/` and wraps MediaPipe's
`LlmInference` (same dep as `FunctionGemmaEngine`, no new dependency).

Three things make this SF Phase 9's *architectural* commit (US-060 is
docs, US-062 is one handler branch):

1. **On-demand load** — `Gemma3nEngine` is NEVER loaded at startup.
   `ModelWarmupService` only warms FunctionGemma (US-023) — this rule
   stays. First `generate()` call triggers `load()`, which surfaces
   `CurroError.ModelCold` so the caller can speak `copy_cold_model`
   ("Dame un segundo.") before the load resolves.
2. **Memory-pressure safeguards** — `CurroApp.onTrimMemory(level)`
   unloads Gemma 3n when `level >= TRIM_MEMORY_RUNNING_LOW`;
   `OutOfMemoryError` during load OR generate is caught and surfaced as
   `CurroError.OutOfMemory` (with auto-unload on the generate path so the
   memory is actually released). FunctionGemma stays warm throughout —
   the app must keep working as a function-calling assistant even when
   Gemma 3n is unavailable.
3. **Test-friendly seam** — `LlmInferenceFactory` interface so JVM tests
   substitute a fake without ever instantiating MediaPipe.

This SF lands no user-visible behaviour. The first user-visible use of
Gemma 3n is US-062 (the WhatsApp summarisation branch). This SF is
purely architectural — the next 5 SFs (Phase-9 placeholder, future
`summarize_whatsapp_thread` catalog function, rewriter, translator, etc.)
all build on this interface.

**Why this matters for *this* user**: the design choices in this SF
*directly determine* whether Curro keeps working when his Redmi 15
runs low on RAM. The unload path is the difference between "Curro
crashes" and "Curro speaks `copy_many_unread` and the user gets the
existing read flow". The on-demand load is the difference between
"Curro takes 20 s to boot because it speculatively loaded a model it
might never need" and "Curro boots fast and pays the load cost only on
the first turn that actually needs it".

---

## 2. Scope

**In scope:**

- `domain/repository/TextGenEngine.kt` (NEW interface).
- `data/ml/Gemma3nEngine.kt` (NEW impl, implements `TextGenEngine` +
  `EngineMetrics`).
- `data/ml/LlmInferenceFactory.kt` (NEW seam for tests).
- `data/ml/ModelFiles.kt` — extend with `gemma3n()` + `isGemma3nAvailable()`.
- `domain/repository/EngineMetrics.kt` — additive: 3 new methods with
  defaults so existing impls (just `FunctionGemmaEngine`) keep working.
- `di/MlModule.kt` — add `@Binds @Singleton fun bindTextGenEngine(impl:
  Gemma3nEngine): TextGenEngine` + `@Binds @Singleton fun
  bindLlmInferenceFactory(impl: DefaultLlmInferenceFactory):
  LlmInferenceFactory`.
- `CurroApp.kt` — inject `TextGenEngine` + `@ApplicationScope`; override
  `onTrimMemory` to unload on memory pressure.
- `models/README.md` — confirm the Gemma 3n filename in the section
  US-060 pre-created (replace the `_TBD_` for the filename with the
  pinned `gemma3n_e2b.task`; the HF URL slug may still be `_TBD_` —
  documented in the brief below).
- `TelemetryGuardrail.kt` — no new event, but document that the
  existing `model_loaded` event accepts `model = "gemma3n_e2b"`.
- ~12 unit tests (`Gemma3nEngineTest`, `CurroAppOnTrimMemoryTest`,
  `ModelFilesGemma3nTest`) — all JVM/Robolectric, no real MediaPipe.

**Out of scope:**

- The `read_all_unread_whatsapp` summarisation branch — US-062.
- A new catalog function `summarize_whatsapp_thread` — Fase 3, not
  Phase 9 (the spec is clear: §5 Fase 3 only).
- Adding Gemma 3n to the diagnostics screen (US-059) — a follow-up SF
  reads the additive `gemma3n*` methods on `EngineMetrics`; not in
  scope here. The methods exist so that follow-up is a 5-line UI
  change, not an interface change.
- Speculatively loading Gemma 3n at startup — explicitly forbidden by
  `on-device-llm` Rule 3.
- A second warm-up service — Gemma 3n stays on-demand-only.
- Refactoring `FunctionGemmaEngine` to use `LlmInferenceFactory` (the
  factory is shaped to apply to both, but the existing engine's MediaPipe
  call sites are not touched — that's a future cleanup).
- Persisting `lastLoadLatencyMs` / `lastGenerateLatencyMs` across
  process restarts — they're session-local `@Volatile` ints; the
  diagnostics screen is fine with that.

---

## 3. User Flows

### Flow 1: First Gemma 3n call of the session (cold path)

1. `[idle]` — user presses mic.
2. `[listening]` — user says "léeme los mensajes" → STT.
3. `[processing]` — FunctionGemma → `{action: "read_all_unread_whatsapp",
   ...}`.
4. `[executing]` — `ReadAllUnreadWhatsAppHandler.handle()` runs. Cache
   has 12 unread → enters the > 8 branch (US-062).
5. **US-062 logic** (described here for the flow, implemented in
   US-062): handler checks `textGenEngine.isReady.value` → `false`.
   Handler calls `ttsClient.speak(copy_cold_model)` → user hears "Dame
   un segundo."
6. **US-061 entry point**: handler calls `textGenEngine.generate(prompt)`.
   Engine's `generate()` sees `llm == null`, calls `loadNoLock()`,
   which:
   - Checks `ModelFiles.isGemma3nAvailable()` → `true`.
   - Calls `LlmInferenceFactory.create(options)` on the IO dispatcher.
   - On success → sets `llm = instance`, `lastLoadLatencyMs = wallMs`,
     `_isReady.value = true`, emits `telemetry.event("model_loaded",
     {model: "gemma3n_e2b", load_ms: <int>, cold_start: true})`.
7. Engine proceeds to `engine.generateResponse(prompt)` on the IO
   dispatcher, captures `lastGenerateLatencyMs`, returns
   `Result.success(rawOutput)`.
8. Handler (US-062) cleans output + speaks the summary → `[idle]`.

### Flow 2: Subsequent calls in the same session (warm path)

1. User asks for messages again 10 s later (12 unread again).
2. Handler enters > 8 branch.
3. `textGenEngine.isReady.value` → `true`. Handler does NOT speak
   `copy_cold_model`.
4. `generate()` → goes straight to `engine.generateResponse(prompt)`,
   returns in ~3 s (warm).
5. Handler speaks the summary.

### Flow 3: Weights missing (CI / device without sideload)

1. User asks for messages.
2. Handler enters > 8 branch.
3. `textGenEngine.isReady.value` → `false`. `ModelFiles.isGemma3nAvailable()`
   → `false`. Handler skips the `copy_cold_model` speech (pin: the
   cold-model line implies "I'm about to do the thing"; if we can't do
   it, lying is worse than the fallback).
4. `generate()` → returns `Result.failure(CurroError.ModelCold)`.
5. Handler falls back to `copy_many_unread` → user gets the existing
   SF-4.8 line.

### Flow 4: Memory pressure mid-session

1. User has just received the summary (Gemma 3n loaded, ~2 GB resident).
2. User opens WhatsApp directly (via the launcher's app tile).
3. Android fires `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` on Curro.
4. `CurroApp.onTrimMemory` sees `level >= RUNNING_LOW`, fires
   `appScope.launch { textGenEngine.unload() }`.
5. `Gemma3nEngine.unload()` → calls `llm?.close()`, sets `llm = null`,
   `_isReady.value = false`. **FunctionGemma is NOT touched** — the
   `ModelWarmupService` engine stays resident.
6. User returns to Curro 5 min later, asks for messages (> 8 again).
7. Handler enters > 8 branch → `isReady = false` → `copy_cold_model` →
   `generate()` cold-loads again (Flow 1 path).

### Flow 5: OOM during inference

1. User asks for messages. Gemma 3n already loaded.
2. Handler enters > 8 branch → `generate(prompt)`.
3. `engine.generateResponse(prompt)` throws `OutOfMemoryError`.
4. `Gemma3nEngine` catches it, calls `unloadNoLock()` (releases MediaPipe
   memory), returns `Result.failure(CurroError.OutOfMemory)`.
5. Handler falls back to `copy_many_unread` (US-062 logic).

### Flow 6: OOM during initial load

1. User asks for messages. Gemma 3n NOT loaded.
2. Handler enters > 8 branch → speaks `copy_cold_model` →
   `generate(prompt)` → `loadNoLock()`.
3. `LlmInferenceFactory.create(options)` throws `OutOfMemoryError`.
4. `Gemma3nEngine` catches it, returns `Result.failure(CurroError.OutOfMemory)`
   from `load()`. `llm` stays `null`, `_isReady.value` stays `false`.
5. `generate()` propagates the failure up.
6. Handler falls back to `copy_many_unread`.

---

## 4. Function-catalog Impact

**No catalog change.** Phase 9 is purely an internal capability — the
catalog FunctionGemma sees is unchanged.

---

## 5. FSM States Touched

**None directly.** The handler-internal call to `textGenEngine.generate()`
happens inside `Executing` (the coordinator's existing state for the
handler). The cold-model line `copy_cold_model` is spoken from the
handler directly (US-062 wires that — see US-062 brief for the
coordinator-vs-handler-speaks-cold-line discussion).

**Pin**: the `Executing` state remains the spec-defined post-decision
state; this SF does NOT add a new state. The user hears `copy_cold_model`
*inside* `Executing` as a pre-result utterance.

---

## 6. Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none) | Gemma 3n is local; no `INTERNET`; no new permission. | — | — |

No new manifest entries. No new Android system integration beyond what
MediaPipe `LlmInference` already requires (same as `FunctionGemmaEngine`).

---

## 7. On-device-model Impact

This SF IS the on-device-model impact. Key contract points:

- **Memory footprint**: Gemma 3n int4 ≈ 2 GB active when loaded;
  ~0 MB when unloaded.
- **Load latency**: target 3–6 s typical on Redmi 15 (8 GB); 10 s+
  triggers the rollback per US-060's decision doc.
- **Generate latency**: target < 6 s for the typical summarisation
  prompt (~12 messages, 3 senders); pinned by US-062.
- **Decoding parameters** (pinned in this brief): `maxTokens = 2048`,
  `maxTopK = 40`, `temperature = 0.7f`. The 0.7 temperature is the
  *opposite* of FunctionGemma's 0.1 — NL generation wants some sampling
  variety; function-calling wants deterministic JSON.
- **Concurrency**: a single `Mutex` (`stateMutex`) serialises load /
  unload / generate. MediaPipe's `LlmInference` is not documented
  thread-safe; the mutex is the same insurance pattern as
  `FunctionGemmaEngine.callMutex`.
- **PII boundary**: latency + model name are safe to log/telemeter; the
  prompt, the raw output, the message bodies, the sender names are NEVER
  logged or sent to telemetry. The engine itself never logs the prompt
  or the raw output (verified by code review of every `Log.*` call).

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/
├── domain/repository/
│   └── TextGenEngine.kt                              # NEW
└── data/ml/
    ├── Gemma3nEngine.kt                              # NEW
    └── LlmInferenceFactory.kt                        # NEW (+ DefaultLlmInferenceFactory in same file)

app/src/test/java/com/curro/app/
├── data/ml/
│   ├── Gemma3nEngineTest.kt                          # NEW
│   └── ModelFilesGemma3nTest.kt                      # NEW
└── CurroAppOnTrimMemoryTest.kt                       # NEW
```

### 8.2 Files modified

```
app/src/main/java/com/curro/app/
├── CurroApp.kt                                       # MODIFIED (+ onTrimMemory + 2 injects)
├── data/ml/ModelFiles.kt                             # MODIFIED (+ gemma3n() + isGemma3nAvailable())
├── domain/repository/EngineMetrics.kt                # MODIFIED (+3 default methods)
├── data/telemetry/TelemetryGuardrail.kt              # MODIFIED (docstring note only — no new event)
└── di/MlModule.kt                                    # MODIFIED (+2 binds)

models/README.md                                      # MODIFIED (pin filename; HF URL may stay _TBD_)
app/src/test/java/com/curro/app/data/telemetry/
└── TelemetryGuardrailTest.kt                         # MODIFIED (+1 case for gemma3n model_loaded)
```

### 8.3 `domain/repository/TextGenEngine.kt`

```kotlin
package com.curro.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * On-device natural-language generation engine for Curro (US-061 / SF-9.2).
 *
 * Backed by Gemma 3n E2B (int4, ~2 GB active) via MediaPipe `LlmInference`,
 * loaded **on demand only** (NEVER speculatively at startup — see
 * `on-device-llm` Rule 3 and `docs/architecture/gemma-3n-decision.md`).
 *
 * Lifecycle:
 *  - `load()` is idempotent. First call triggers MediaPipe initialisation
 *    (~3–6 s typical on Redmi 15 8 GB). Subsequent calls return
 *    `Result.success(Unit)` immediately if already loaded.
 *  - `generate(prompt)` auto-loads if not ready, then runs inference.
 *  - `unload()` releases the LLM instance; called by:
 *      * `CurroApp.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` (memory
 *        pressure).
 *      * Internally by `generate()` when inference throws `OutOfMemoryError`.
 *
 * Failure modes (all surfaced via `Result.failure(CurroError)`):
 *  - `CurroError.ModelCold` — weights absent or load failed
 *    (non-OOM).
 *  - `CurroError.OutOfMemory` — native OOM during load or generate.
 *  - `CurroError.InvalidFunctionCall` — non-OOM exception during
 *    generate (treated as "engine misbehaved"; the caller decides
 *    whether to fall back).
 *
 * Concurrency: implementations MUST serialise load / unload / generate
 * via an internal mutex. `LlmInference` is not documented thread-safe.
 *
 * Thread: `generate()` MUST run inference off the main thread (MediaPipe's
 * `generateResponse` is blocking).
 *
 * Implementations: `data/ml/Gemma3nEngine` (production); a fake under
 * `app/src/test/java/com/curro/app/data/ml/FakeTextGenEngine.kt` for
 * upstream tests (created in US-062 — out of scope here but the shape is
 * implied).
 */
interface TextGenEngine {
    /**
     * Whether the LLM instance is currently resident in memory.
     * Callers (e.g. `ReadAllUnreadWhatsAppHandler` in US-062) read this
     * BEFORE calling [generate] to decide whether to surface the
     * `copy_cold_model` ("Dame un segundo.") line.
     */
    val isReady: StateFlow<Boolean>

    /**
     * Load the model into memory. Idempotent: returns
     * `Result.success(Unit)` if already loaded.
     *
     * @return `Result.success(Unit)` on success;
     *         `Result.failure(CurroError.ModelCold)` if weights are
     *         absent or MediaPipe initialisation fails;
     *         `Result.failure(CurroError.OutOfMemory)` if MediaPipe
     *         throws `OutOfMemoryError` during creation.
     */
    suspend fun load(): Result<Unit>

    /**
     * Generate text for [prompt]. Auto-loads via [load] if not ready;
     * if the auto-load fails, propagates that failure.
     *
     * @return `Result.success(rawOutput)` on success;
     *         `Result.failure(CurroError.ModelCold)` if not loaded and
     *         auto-load failed (non-OOM);
     *         `Result.failure(CurroError.OutOfMemory)` if OOM during
     *         load or generate (with auto-unload on the generate path
     *         so the memory is actually released);
     *         `Result.failure(CurroError.InvalidFunctionCall)` for any
     *         other non-OOM exception during inference.
     */
    suspend fun generate(prompt: String): Result<String>

    /**
     * Release the LLM instance and free its memory. Idempotent.
     * Called by `CurroApp.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` on
     * system memory pressure. Also called internally by [generate] when
     * inference OOMs.
     */
    suspend fun unload()
}
```

### 8.4 `data/ml/LlmInferenceFactory.kt`

```kotlin
package com.curro.app.data.ml

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indirection seam so JVM tests can substitute a fake `LlmInference`
 * without bringing the MediaPipe native runtime into the unit-test
 * classpath (US-061 / SF-9.2).
 *
 * The default impl delegates straight to
 * `LlmInference.createFromOptions(context, options)`. The same shape
 * applies to FunctionGemma; refactoring `FunctionGemmaEngine` to also
 * go through this factory is a future cleanup (out of scope for US-061).
 */
interface LlmInferenceFactory {
    fun create(options: LlmInferenceOptions): LlmInference
}

@Singleton
class DefaultLlmInferenceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : LlmInferenceFactory {
    override fun create(options: LlmInferenceOptions): LlmInference =
        LlmInference.createFromOptions(context, options)
}
```

### 8.5 `data/ml/Gemma3nEngine.kt`

```kotlin
package com.curro.app.data.ml

import android.os.SystemClock
import android.util.Log
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.EngineMetrics
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TextGenEngine
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaPipe-backed implementation of [TextGenEngine] (US-061 / SF-9.2).
 *
 * Wraps [LlmInference] running Gemma 3n E2B (int4, ~2 GB active). The
 * model file is resolved by [ModelFiles.gemma3n]; if absent, [load]
 * returns `Result.failure(CurroError.ModelCold)` and the rest of the
 * app keeps working with FunctionGemma only (see `data/ml/
 * FunctionGemmaEngine`).
 *
 * **NOT JVM-testable directly.** Use [Gemma3nEngineTest] via the
 * [LlmInferenceFactory] seam: tests substitute a `FakeLlmInferenceFactory`
 * that returns a `mockk<LlmInference>()` without touching the MediaPipe
 * native runtime. Real-engine verification is [Gemma3nSmokeTest]
 * (instrumented, US-060).
 *
 * **Concurrency**. A single [Mutex] (`stateMutex`) serialises load /
 * unload / generate. The blocking `generateResponse` runs inside
 * `withContext(io)`; the main thread is never blocked.
 *
 * **PII boundary** (same rule as `FunctionGemmaEngine`): latency + model
 * name are safe to log/telemeter. The prompt, the raw output, the
 * message bodies, the sender names: NEVER. Verified by inspection of
 * every `Log.*` and `telemetry.*` call below — none reference [prompt]
 * or the `raw` payload.
 *
 * **Lifecycle integration**. [CurroApp.onTrimMemory] calls [unload]
 * when `level >= TRIM_MEMORY_RUNNING_LOW`. [FunctionGemmaEngine] is NOT
 * unloaded under memory pressure (it's kept warm by [ModelWarmupService]
 * per US-023 — the app's function-calling stays alive even when Gemma 3n
 * is gone).
 */
@Singleton
class Gemma3nEngine @Inject constructor(
    private val modelFiles: ModelFiles, // see "Pin: ModelFiles" below
    private val factory: LlmInferenceFactory,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val telemetry: TelemetrySink,
) : TextGenEngine, EngineMetrics {
    private val stateMutex = Mutex()

    @Volatile private var llm: LlmInference? = null

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Wall-clock ms of the most recent successful load (null until first load). */
    @Volatile private var lastLoadMs: Long? = null

    /** Wall-clock ms of the most recent successful generate (null until first inference). */
    @Volatile private var lastGenerateMs: Long? = null

    override suspend fun load(): Result<Unit> = stateMutex.withLock { loadNoLock() }

    private suspend fun loadNoLock(): Result<Unit> {
        if (llm != null) return Result.success(Unit)
        if (!modelFiles.isGemma3nAvailable()) {
            Log.i(TAG, "load skipped — weights not present at ${modelFiles.gemma3n().absolutePath}")
            return Result.failure(CurroError.ModelCold)
        }
        val started = SystemClock.elapsedRealtime()
        return withContext(io) {
            runCatching {
                val opts = LlmInferenceOptions.builder()
                    .setModelPath(modelFiles.gemma3n().absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setMaxTopK(MAX_TOP_K)
                    .setTemperature(TEMPERATURE)
                    .build()
                factory.create(opts)
            }.fold(
                onSuccess = { instance ->
                    llm = instance
                    val ms = SystemClock.elapsedRealtime() - started
                    lastLoadMs = ms
                    _isReady.value = true
                    Log.i(TAG, "load took ${ms}ms")
                    telemetry.event(
                        "model_loaded",
                        mapOf(
                            "model" to MODEL_NAME,
                            "load_ms" to ms.toInt(),
                            "cold_start" to true,
                        ),
                    )
                    Result.success(Unit)
                },
                onFailure = { t ->
                    val mapped = when (t) {
                        is OutOfMemoryError -> CurroError.OutOfMemory
                        else -> CurroError.ModelCold
                    }
                    Log.w(TAG, "load failed: ${t.javaClass.simpleName} → $mapped")
                    Result.failure<Unit>(mapped)
                },
            )
        }
    }

    override suspend fun generate(prompt: String): Result<String> = stateMutex.withLock {
        if (llm == null) {
            val loadResult = loadNoLock()
            if (loadResult.isFailure) return Result.failure(loadResult.exceptionOrNull()!!)
        }
        val engine = llm ?: return Result.failure(CurroError.ModelCold)
        val started = SystemClock.elapsedRealtime()
        return withContext(io) {
            try {
                val raw = engine.generateResponse(prompt) // blocking
                val ms = SystemClock.elapsedRealtime() - started
                lastGenerateMs = ms
                Log.i(TAG, "generate latency: ${ms}ms")
                // PII boundary: `prompt`, `raw`, sender names are NEVER logged.
                Result.success(raw)
            } catch (_: OutOfMemoryError) {
                Log.w(TAG, "OOM during generate; auto-unloading")
                unloadNoLock()
                Result.failure<String>(CurroError.OutOfMemory)
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                // Mirror FunctionGemmaEngine's defensive catch-all: any non-OOM
                // throwable surfaces as InvalidFunctionCall so the caller can
                // fall back without a generic Throwable branch.
                Log.w(TAG, "generate failed: ${t.javaClass.simpleName}")
                Result.failure<String>(CurroError.InvalidFunctionCall)
            }
        }
    }

    override suspend fun unload() = stateMutex.withLock { unloadNoLock() }

    private fun unloadNoLock() {
        llm?.close()
        llm = null
        _isReady.value = false
        Log.i(TAG, "unloaded")
    }

    // EngineMetrics — Phase-9 additive methods.
    // The original 4 methods (isReady() / modelName() / lastWarmUpLatencyMs() /
    // lastInferenceLatencyMs()) belong to FunctionGemmaEngine; this impl uses
    // the defaults from the interface for those (returns false / "" / null /
    // null) since DiagnosticsViewModel does NOT inject the Gemma3n binding.
    // The diagnostics surface for Gemma 3n is a future SF that reads:

    override fun gemma3nIsReady(): Boolean = _isReady.value
    override suspend fun gemma3nLastLoadLatencyMs(): Long? = lastLoadMs
    override suspend fun gemma3nLastGenerateLatencyMs(): Long? = lastGenerateMs

    private companion object {
        const val TAG = "Curro/Gemma3n"
        const val MODEL_NAME = "gemma3n_e2b"
        // Decoding params — pinned in US-061.
        // 2048 tokens is enough for a ~3-sender summary; tighten if Redmi 15 memory pressure surfaces.
        const val MAX_TOKENS = 2048
        const val MAX_TOP_K = 40
        // 0.7 — NL generation wants sampling variety (vs FunctionGemma's 0.1 for deterministic JSON).
        const val TEMPERATURE = 0.7f
    }
}
```

> **Pin: `ModelFiles` injection.** `ModelFiles` is currently an `object`
> (US-019). To make `Gemma3nEngineTest` substitutable without static
> mocking, this SF converts `ModelFiles` to a `@Singleton class` and
> injects it. The two static call sites (`FunctionGemmaEngine`,
> `ModelWarmupService` direct calls) are migrated to inject the class.
> If the implementer judges that's too much churn, an alternative is to
> keep `ModelFiles` as an `object` and stub it in tests via
> `mockkObject(ModelFiles)` — the brief accepts either approach; pin
> the choice in the implementation commit.

### 8.6 `data/ml/ModelFiles.kt` — extend

If kept as an `object`:

```kotlin
object ModelFiles {
    fun functionGemma(): File = File(BuildConfig.MODEL_BASE_PATH, FUNCTION_GEMMA_FILENAME)
    fun isFunctionGemmaAvailable(): Boolean = functionGemma().let { it.exists() && it.canRead() }

    fun gemma3n(): File = File(BuildConfig.MODEL_BASE_PATH, GEMMA_3N_FILENAME)
    fun isGemma3nAvailable(): Boolean = gemma3n().let { it.exists() && it.canRead() }

    private const val FUNCTION_GEMMA_FILENAME = "function_gemma_270m.task"
    private const val GEMMA_3N_FILENAME = "gemma3n_e2b.task"
}
```

If migrated to a `@Singleton class` (the recommended path for testability):

```kotlin
@Singleton
class ModelFiles @Inject constructor() {
    fun functionGemma(): File = File(BuildConfig.MODEL_BASE_PATH, FUNCTION_GEMMA_FILENAME)
    fun isFunctionGemmaAvailable(): Boolean = functionGemma().let { it.exists() && it.canRead() }
    fun gemma3n(): File = File(BuildConfig.MODEL_BASE_PATH, GEMMA_3N_FILENAME)
    fun isGemma3nAvailable(): Boolean = gemma3n().let { it.exists() && it.canRead() }
    private companion object {
        const val FUNCTION_GEMMA_FILENAME = "function_gemma_270m.task"
        const val GEMMA_3N_FILENAME = "gemma3n_e2b.task"
    }
}
```

### 8.7 `domain/repository/EngineMetrics.kt` — extend

```kotlin
interface EngineMetrics {
    // Existing — FunctionGemma (US-059):
    fun isReady(): Boolean
    fun modelName(): String
    suspend fun lastWarmUpLatencyMs(): Long?
    suspend fun lastInferenceLatencyMs(): Long?

    // NEW — Gemma 3n (US-061). Defaults are conservative so existing
    // impls (FunctionGemmaEngine) don't need to implement them; the
    // diagnostics screen reads them only when a future SF injects the
    // Gemma3n binding.
    fun gemma3nIsReady(): Boolean = false
    suspend fun gemma3nLastLoadLatencyMs(): Long? = null
    suspend fun gemma3nLastGenerateLatencyMs(): Long? = null
}
```

> **Pin: why defaults instead of a second interface.** A `Gemma3nMetrics`
> sibling interface would be cleaner but doubles the Hilt-binding work
> and forces the diagnostics screen (US-059, future-extended) to inject
> two metrics interfaces and `combine` them. Adding the methods with
> defaults keeps the existing call sites unchanged and lets the future
> diagnostics SF be a 5-line UI change.

### 8.8 `CurroApp.kt` — extend

```kotlin
@HiltAndroidApp
class CurroApp : Application() {
    @Inject lateinit var telemetryInitializer: TelemetryInitializer
    @Inject lateinit var textGenEngine: TextGenEngine                 // NEW
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope   // NEW

    override fun onCreate() {
        super.onCreate()
        telemetryInitializer.initialize()
        NotificationChannels.ensureWarmupChannel(this)
        ContextCompat.startForegroundService(this, Intent(this, ModelWarmupService::class.java))
        // NOTE: NO Gemma 3n warm-up here. On-device-llm Rule 3.
    }

    override fun onTrimMemory(level: Int) {                            // NEW
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            // Fire-and-forget. Unload is idempotent + cheap (just close +
            // a flag flip); we don't wait. FunctionGemma is NOT unloaded —
            // the warm-up service keeps it resident.
            appScope.launch { textGenEngine.unload() }
        }
    }
}
```

### 8.9 `di/MlModule.kt` — extend

```kotlin
@Module
@InstallIn(SingletonComponent::class)
interface MlModule {
    @Binds @Singleton
    fun bindFunctionCallEngine(impl: FunctionGemmaEngine): FunctionCallEngine

    @Binds @Singleton
    fun bindEngineMetrics(impl: FunctionGemmaEngine): EngineMetrics      // unchanged — canonical for diagnostics

    // NEW (US-061 / SF-9.2):
    @Binds @Singleton
    fun bindTextGenEngine(impl: Gemma3nEngine): TextGenEngine

    @Binds @Singleton
    fun bindLlmInferenceFactory(impl: DefaultLlmInferenceFactory): LlmInferenceFactory
}
```

### 8.10 `data/telemetry/TelemetryGuardrail.kt` — docstring update

The existing `"model_loaded"` row already accepts `model` as a string
prop. Update the comment above it to enumerate both values explicitly:

```kotlin
// SF-3.5 / SF-9.2 — model warm-up & Gemma 3n on-demand load
//   model ∈ {"function_gemma_270m", "gemma3n_e2b"}
"model_loaded" to setOf("model", "load_ms", "cold_start"),
```

Add a fixture case to `TelemetryGuardrailTest`:

```kotlin
@Test fun model_loaded_acceptsGemma3nModelString() {
    val result = TelemetryGuardrail.check(
        "model_loaded",
        mapOf("model" to "gemma3n_e2b", "load_ms" to 4380, "cold_start" to true),
    )
    assertEquals(GuardrailResult.Allow, result)
}
```

### 8.11 Navigation

No nav change.

---

## 9. Senior-UX & Copy

**No new strings.** The `copy_cold_model` line ("Dame un segundo.")
already exists at `strings.xml:132` (added in an earlier phase). US-062
wires it into the handler.

**Pin** the canonical line `"Dame un segundo."` matches the
`brand-design` skill entry at "Model cold (Phase 9 — Gemma 3n)" — spec
§4.4 provenance.

---

## 10. Acceptance Criteria

- [ ] **`TextGenEngine` interface exists** with the 4 methods +
      `isReady` `StateFlow` per §8.3.
- [ ] **`Gemma3nEngine` class exists** with the documented threading
      (single `Mutex`), the documented decoding params (2048 / 40 /
      0.7), the documented failure mapping (OOM → `OutOfMemory`,
      non-OOM → `ModelCold` for load and `InvalidFunctionCall` for
      generate), and the documented PII boundary (latency + model name
      only).
- [ ] **`LlmInferenceFactory` interface + `DefaultLlmInferenceFactory`
      impl exist** and are bound via Hilt.
- [ ] **`ModelFiles.gemma3n()` + `isGemma3nAvailable()` exist** and the
      path resolves to `${BuildConfig.MODEL_BASE_PATH}/gemma3n_e2b.task`.
- [ ] **`EngineMetrics` interface extended** with the 3 Gemma-3n methods,
      all defaulted so `FunctionGemmaEngine` keeps compiling without
      changes. Existing `DiagnosticsViewModel` test suite stays green.
- [ ] **`CurroApp.onTrimMemory(level)` triggers `textGenEngine.unload()`**
      when `level >= TRIM_MEMORY_RUNNING_LOW`; does NOT trigger when
      `level < RUNNING_LOW`. `FunctionGemmaEngine` is NOT unloaded.
- [ ] **`ModelWarmupService` is UNCHANGED** — Gemma 3n stays
      on-demand-only.
- [ ] **Hilt bindings** for `TextGenEngine` and `LlmInferenceFactory`
      compile + are reachable from a test injection.
- [ ] **`load()` is idempotent** — verified by test.
- [ ] **`unload()` is idempotent** — verified by test.
- [ ] **OOM during load → `Result.failure(CurroError.OutOfMemory)`** —
      verified by test.
- [ ] **OOM during generate → auto-unload + `Result.failure(CurroError.OutOfMemory)`** —
      verified by test (assertion: `isReady.value == false` after).
- [ ] **Missing weights → `Result.failure(CurroError.ModelCold)`** —
      verified by test (`isGemma3nAvailable()` stubbed `false`).
- [ ] **Telemetry**: `model_loaded` accepts `model = "gemma3n_e2b"` per
      the guardrail update + the fixture test.
- [ ] **No new permissions, no new manifest entries, no new DataStore
      keys, no new dependencies.**
- [ ] **Build is green WITHOUT Gemma 3n weights present** — the
      `isGemma3nAvailable()` guard keeps `./gradlew assembleDebug
      ktlintCheck detektDebug testDebugUnitTest` green on a fresh clone.

---

## 11. Design Notes

- The engine class is ~150 lines, mirroring `FunctionGemmaEngine`'s
  shape and conventions (same `Log.i` style, same `runCatching`
  pattern, same `@Volatile var` metrics).
- **No new logging tags** beyond `Curro/Gemma3n` and `Curro/Gemma3nSmoke`
  (the latter from US-060).
- **The decoding params (2048 / 40 / 0.7) are pinned in this brief.**
  If a future SF tweaks them (e.g. the summarisation prompt regressing
  with 0.7), bump them in the engine companion object — not via a
  DataStore setting (this is engineering tuning, not user
  configuration).
- **`maxTokens = 2048` rationale**: a 3-sender summary at ≤ 1 sentence
  per sender fits comfortably in ≤ 200 tokens. The 2048 ceiling is
  generous insurance for the future rewriter and translator handlers
  that will share this engine. Tighten if Redmi 15 memory pressure
  appears.

---

## 12. Performance Considerations

- **Load latency**: 3–6 s typical on Redmi 15 8 GB. Captured in
  `lastLoadMs`; surfaced via the additive `EngineMetrics` methods.
- **Generate latency**: < 6 s for the US-062 summarisation prompt;
  surfaced via `lastGenerateMs`.
- **Memory**: ~2 GB active when loaded. `unload()` releases it (via
  `LlmInference.close()` + nulling the reference + setting the
  StateFlow). Verified by Android's heap profiler — out of scope as a
  test, in scope as a manual verification step on the device.
- **Concurrency**: the mutex is the right tool here because the four
  operations (load / unload / generate / generate) ARE mutually
  exclusive. The cost of serialisation is irrelevant (one user, one
  voice turn at a time).
- **Main thread**: every `generate()` call goes through
  `withContext(io)`. Verified by code review + by the test that the
  mutex never holds across `withContext`.

---

## 13. Testing Requirements

### 13.1 `Gemma3nEngineTest` (Robolectric, 12+ cases)

Setup: a `FakeLlmInferenceFactory` returning `mockk<LlmInference>()`; a
`FakeModelFiles` controlling `isGemma3nAvailable()`; a `FakeTelemetrySink`
capturing events.

- [ ] `load_succeeds_whenWeightsPresent_setsIsReadyTrue` — factory returns
      a mock; `isReady.value` flips `false → true`; `lastLoadMs > 0`.
- [ ] `load_isIdempotent_secondCallDoesNotRecreateLlm` — factory.create
      called exactly once across two `load()` invocations.
- [ ] `load_returnsModelCold_whenWeightsAbsent` — `isGemma3nAvailable() ==
      false`; `load()` returns `Result.failure(CurroError.ModelCold)`;
      `isReady.value` stays `false`; factory.create NEVER called.
- [ ] `load_returnsOutOfMemory_whenFactoryThrowsOOM` —
      `factory.create` throws `OutOfMemoryError`; `load()` returns
      `Result.failure(CurroError.OutOfMemory)`; `isReady.value` stays
      `false`.
- [ ] `load_returnsModelCold_whenFactoryThrowsNonOOM` — `factory.create`
      throws `IllegalStateException`; `load()` returns
      `Result.failure(CurroError.ModelCold)`.
- [ ] `generate_autoLoads_whenNotReady_succeeds` — first call to
      `generate("hi")` triggers `load()` then `generateResponse`;
      returns `Result.success("..." )`.
- [ ] `generate_returnsRawOutput_whenAlreadyLoaded` — after `load()`,
      `generate("hi")` calls `generateResponse` only; output preserved
      verbatim.
- [ ] `generate_returnsOutOfMemory_andUnloads_whenInferenceOOM` —
      `generateResponse` throws `OutOfMemoryError`; `generate()`
      returns `Result.failure(CurroError.OutOfMemory)`; `isReady.value
      == false` after; subsequent `generate()` triggers a fresh
      `load()`.
- [ ] `generate_returnsInvalidFunctionCall_whenInferenceThrowsNonOOM` —
      `generateResponse` throws `IllegalStateException`; `generate()`
      returns `Result.failure(CurroError.InvalidFunctionCall)`;
      `isReady.value` stays `true` (engine still usable; the throw was
      a one-off).
- [ ] `generate_returnsModelCold_whenAutoLoadFails` — weights missing;
      `generate("hi")` returns `Result.failure(CurroError.ModelCold)`
      (the load failure propagates).
- [ ] `unload_clearsLlm_andSetsIsReadyFalse` — after `load()` +
      `unload()`, `isReady.value == false`; `llm.close()` called.
- [ ] `unload_isIdempotent_secondCallNoOp` — `unload()` then `unload()`;
      `llm.close()` called exactly once.
- [ ] `gemma3nLastLoadLatencyMs_capturesLoadWallClock` — after `load()`,
      `gemma3nLastLoadLatencyMs() != null` and > 0.
- [ ] `gemma3nLastGenerateLatencyMs_capturesInferenceWallClock` — after
      `generate()`, `gemma3nLastGenerateLatencyMs() != null` and > 0.
- [ ] `telemetry_emitsModelLoaded_onSuccessfulLoad` — exactly one
      `model_loaded` event with `model = "gemma3n_e2b"`.

### 13.2 `CurroAppOnTrimMemoryTest` (Robolectric, 3 cases)

Setup: a `mockk<TextGenEngine>(relaxed = true)`; the application
instantiated via Robolectric with the mock injected via
`@HiltAndroidTest` + `@TestInstallIn`.

- [ ] `onTrimMemory_RUNNING_LOW_callsTextGenEngineUnload` — pass
      `TRIM_MEMORY_RUNNING_LOW` → `coVerify { textGenEngine.unload() }`
      (one call, eventually — `runBlocking` until the launched coroutine
      runs).
- [ ] `onTrimMemory_COMPLETE_callsTextGenEngineUnload` — pass
      `TRIM_MEMORY_COMPLETE` (level above RUNNING_LOW) → unload called.
- [ ] `onTrimMemory_RUNNING_MODERATE_doesNotCallUnload` — pass
      `TRIM_MEMORY_RUNNING_MODERATE` (level below RUNNING_LOW) →
      `coVerify(exactly = 0) { textGenEngine.unload() }`.

### 13.3 `ModelFilesGemma3nTest` (JVM, 2 cases)

- [ ] `gemma3n_returnsExpectedPath_underModelBasePath` — asserts the
      path is `${BuildConfig.MODEL_BASE_PATH}/gemma3n_e2b.task`.
- [ ] `isGemma3nAvailable_reflectsFileExistence` — write a temp file at
      the path → `true`; delete → `false`.

### 13.4 `TelemetryGuardrailTest` — extend with 1 case

Per §8.10 — `model_loaded_acceptsGemma3nModelString`.

### 13.5 Manual verification (Redmi 15, with weights present)

- [ ] After `adb push` of the Gemma 3n weights, `Gemma3nSmokeTest`
      (US-060) runs end-to-end and logs the latencies.
- [ ] Launch the app, force a memory-pressure event via
      `adb shell am send-trim-memory <pid> RUNNING_LOW` → `adb logcat
      -s Curro/Gemma3n` shows `unloaded` line.
- [ ] No regression in FunctionGemma — `adb logcat -s Curro/Llm` shows
      `decide latency: <ms>ms` after the unload event (i.e. function
      calling still works).

---

## 14. Implementation Notes

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/domain/repository/TextGenEngine.kt`
- `app/src/main/java/com/curro/app/data/ml/Gemma3nEngine.kt`
- `app/src/main/java/com/curro/app/data/ml/LlmInferenceFactory.kt`
  (contains both `LlmInferenceFactory` interface and
  `DefaultLlmInferenceFactory` impl).
- `app/src/test/java/com/curro/app/data/ml/Gemma3nEngineTest.kt`
- `app/src/test/java/com/curro/app/data/ml/ModelFilesGemma3nTest.kt`
- `app/src/test/java/com/curro/app/CurroAppOnTrimMemoryTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/CurroApp.kt` (+ `onTrimMemory` +
  2 injects).
- `app/src/main/java/com/curro/app/data/ml/ModelFiles.kt` (+
  `gemma3n()` + `isGemma3nAvailable()`; possibly migrated to a
  `@Singleton class` — see §8.5 pin).
- `app/src/main/java/com/curro/app/domain/repository/EngineMetrics.kt`
  (+3 default methods).
- `app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt`
  (docstring on `model_loaded` row).
- `app/src/main/java/com/curro/app/di/MlModule.kt` (+2 binds).
- `app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailTest.kt`
  (+1 case).
- `models/README.md` (pin the filename `gemma3n_e2b.task`; HF URL
  may remain `_TBD_` if not yet confirmed in HF).

**Sequencing pin**: this SF is the second commit in the Phase-9 batch
and the largest. Land it on its own; US-062 builds directly on it.

**Pre-emptive answer to the obvious "why not unload FunctionGemma too
under memory pressure?"**: because the cost of cold-loading FunctionGemma
hits the user on the very next mic press (every press is a function
call), whereas Gemma 3n cold-loads only hit users on the rare > 8
unread path. The asymmetry justifies the asymmetric policy. (Documented
also in `on-device-llm` Rule 3.)

**Cross-reference for US-062**: the next SF will inject `TextGenEngine`
+ `WhatsAppSummaryPromptBuilder` + `SummaryOutputCleaner` into
`ReadAllUnreadWhatsAppHandler` and wire the summarisation branch +
fallback. The cold-model speech (`copy_cold_model`) is spoken FROM the
handler (not the coordinator) — see US-062 brief for the rationale.

---

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-9.2 `TextGenEngine` + `Gemma3nEngine` + on-demand load + memory-pressure safeguards. |
