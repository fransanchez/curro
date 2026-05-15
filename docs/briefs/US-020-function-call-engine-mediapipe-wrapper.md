# US-020 — SF-3.2 · `FunctionCallEngine` interface + `FunctionGemmaEngine` MediaPipe wrapper

> **Spec trace:** spec §4.3 (capa de decisión — FunctionGemma).
> **Master-plan:** SF-3.2
> **Phase:** 3 — FunctionGemma decision layer
> **Depends on:** US-019 (`ModelFiles` + `BuildConfig.MODEL_BASE_PATH`),
> US-021 (the `FunctionCallPromptBuilder` collaborator), US-002 (Hilt DI graph)
> **Size:** L

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `FunctionCallEngine` interface + `FunctionGemmaEngine` MediaPipe wrapper |
| **US ID** | US-020 |
| **Phase** | 3 |
| **Status** | In Progress |
| **Created** | 2026-05-15 |
| **Modified** | 2026-05-15 |
| **PM Owner** | android-product-analyst |
| **Architect** | ondevice-ai-engineer |

---

## 1. Summary

Introduce the boundary between "Curro wants a function call for this utterance"
and "MediaPipe runs an LLM on the device". The `FunctionCallEngine` interface
lives in `domain/repository/` (pure Kotlin, no Android), and the
`FunctionGemmaEngine` implementation in `data/ml/` wraps
`com.google.mediapipe.tasks.genai.llminference.LlmInference`. The engine
returns the **raw model output as a `String`** — validation against the
catalog is SF-3.4's `FunctionCallValidator`'s job. This separation lets every
test fake the engine, lets the validator's failure modes ship independently,
and contains MediaPipe to exactly one file.

Why this matters for *this* user: he never sees this layer, but every press of
the mic in Phase 3 onwards routes through it. The < 500 ms warm-latency target
on the Redmi 15 — the gate spec §14 calls out — lives in this engine's
`decide()` call.

---

## 2. Scope

**In scope:**

- `domain/repository/FunctionCallEngine.kt` — pure-Kotlin interface.
- `domain/model/PromptContext.kt` — small data class for the model's context
  inputs.
- `data/ml/FunctionGemmaEngine.kt` — MediaPipe-backed implementation.
- `di/MlModule.kt` — Hilt binding `FunctionCallEngine ↔ FunctionGemmaEngine`.
- `di/DispatcherModule.kt` + `@IoDispatcher` qualifier — if not already present
  in the repo from earlier SFs (verify before adding; if `@IoDispatcher`
  already exists, this SF reuses it).
- `gradle/libs.versions.toml` — activate the `mediapipe-tasks-genai` entry
  (the placeholder `mediapipeGenai = "0.10.14"` already exists from US-019's
  catalog reservation note).
- `app/build.gradle.kts` — `implementation(libs.mediapipe.tasks.genai)` added.
- JVM test fake `FakeFunctionCallEngine` in `app/src/test/.../fakes/`.
- JVM contract tests in `app/src/test/.../FunctionGemmaEngineContractTest.kt`
  exercising the interface against the fake.
- Latency log + telemetry event (model name + duration only — no PII).
- `CurroError.ModelCold` / `CurroError.OutOfMemory` confirmed in the taxonomy
  (they exist per CLAUDE.md; this SF wires the mappings).

**Out of scope:**

- The prompt template's exact rendering — that's US-021 (`FunctionCallPromptBuilder`
  is a constructor dependency, not implemented here).
- The validator — US-022.
- The warm-up service — US-023.
- The smoke loop / launcher integration — US-024.
- Gemma 3n's `TextGenEngine` — Phase 9.
- Any on-device latency measurement — the < 500 ms gate is asserted manually
  on the Redmi 15 in US-024; this SF only emits the latency line.

---

## 3. User Flows

This SF is invisible to the end user. Developer-facing flows:

### Flow 1 — Cold engine, weights absent (CI / first run)

1. `FunctionGemmaEngine` is constructed by Hilt as a `@Singleton`.
2. Something calls `engine.warmUp()`. `ModelFiles.isFunctionGemmaAvailable()`
   returns `false`. The engine logs once at INFO and leaves `llm = null`.
3. `engine.isReady()` returns `false`.
4. Some caller invokes `engine.decide("qué hora es", ctx)`. The engine
   immediately returns `Result.failure(CurroError.ModelCold)` and **kicks**
   `warmUp()` as a side effect (so the next call may succeed once the weights
   arrive).

### Flow 2 — Weights present, first warm

1. `engine.warmUp()` finds the file at
   `BuildConfig.MODEL_BASE_PATH/function_gemma_270m.task`.
2. Builds `LlmInferenceOptions` (`maxTokens = 256`, `temperature = 0.1f`,
   `topK = 1`, modelPath = the file).
3. `LlmInference.createFromOptions(context, options)` → stored in `llm`.
4. Logs `Log.i("Curro/Llm", "warm-up took ${ms}ms")`.

### Flow 3 — `decide()` warm path

1. Caller invokes `engine.decide("qué hora es", ctx)` on whatever dispatcher
   they like.
2. The engine does `withContext(io) { callMutex.withLock { … } }`.
3. Inside the lock: `promptBuilder.build(utterance, ctx)` → `prompt: String`;
   `llm!!.generateResponse(prompt)` → `raw: String` (blocking call;
   MediaPipe runs natively).
4. Catches `OutOfMemoryError` → `Result.failure(CurroError.OutOfMemory)`.
5. Catches any other `Throwable` → `Result.failure(CurroError.InvalidFunctionCall)`
   (best-effort fallback — the validator will surface the real shape failure
   when the caller pipes the raw string through it).
6. Success: emits `Log.i("Curro/Llm", "decide latency: ${ms}ms")` (latency
   only — **never** the utterance), and a `telemetry.event("model_inference",
   mapOf("model" to "function_gemma_270m", "latency_ms" to ms))`.
7. Returns `Result.success(raw)`.

### Flow 4 — Concurrent `decide()` (defensive)

1. Two coroutines call `engine.decide(...)` simultaneously.
2. The first acquires `callMutex`; the second suspends until the first releases.
3. Sequential execution — `LlmInference` is not thread-safe; the mutex is the
   contract. (In Phase 3 only the launcher calls the engine; contention is
   unlikely but the mutex is cheap insurance.)

---

## 4. Function-catalog Impact

**No catalog change.** The catalog is read by `FunctionCallPromptBuilder`
(US-021), not by the engine. The engine is catalog-agnostic — it returns raw
strings.

---

## 5. FSM States Touched

**None directly.** The engine is a collaborator of US-024's smoke loop, which
adds a `Processing` state to `ListeningState`. This SF only delivers the
collaborator; the state plumbing is US-024.

---

## 6. Android System Integrations & Permissions

**MediaPipe** is the new integration — `com.google.mediapipe:tasks-genai` —
encapsulated entirely in `data/ml/FunctionGemmaEngine.kt`. The library uses
JNI / native code; the Gradle dependency pulls in the necessary `.so` files
for the supported ABIs.

**No new Android permissions.** Reading the `.task` file from
`/data/local/tmp/curro-models/` does not require storage permission. The
foreground-service / `POST_NOTIFICATIONS` permission set lands in US-023.

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none added in this SF) | — | — | — |

---

## 7. On-device-model Impact

This SF **is** the model impact:

- **Model**: FunctionGemma 270M int8 (~288 MB) via MediaPipe LLM Inference.
- **Loading**: `warmUp()` once; held resident as `private var llm:
  LlmInference?`. US-023's foreground service is what calls `warmUp()` at app
  startup; this SF supports being called from anywhere (idempotent).
- **Latency budget**: < 500 ms warm `decide` on the Redmi 15. Measured and
  logged on every call. The < 500 ms assertion is exercised manually in
  US-024.
- **OOM handling**: `OutOfMemoryError` is caught and mapped to
  `CurroError.OutOfMemory`. The engine does NOT call `unload()` on OOM in
  Phase 3 (the Gemma 3n `TextGenEngine` will, in Phase 9 — FunctionGemma is
  too small to want to unload). The Gemma 3n risk on the 4 GB RAM variant of
  the Redmi 15 is unrelated to this SF.
- **MediaPipe boundary**: enforced by a `grep` AC — `grep -r
  "com.google.mediapipe" app/src/main/java/com/curro/app/` matches **only**
  `data/ml/FunctionGemmaEngine.kt`.

---

## 8. Android Specification

### 8.1 Files added

- `app/src/main/java/com/curro/app/domain/repository/FunctionCallEngine.kt`
- `app/src/main/java/com/curro/app/domain/model/PromptContext.kt`
- `app/src/main/java/com/curro/app/data/ml/FunctionGemmaEngine.kt`
- `app/src/main/java/com/curro/app/di/MlModule.kt`
- `app/src/main/java/com/curro/app/di/DispatcherModule.kt` (only if
  `@IoDispatcher` doesn't already exist; verify first)
- `app/src/test/java/com/curro/app/data/ml/fakes/FakeFunctionCallEngine.kt`
- `app/src/test/java/com/curro/app/data/ml/FunctionGemmaEngineContractTest.kt`

### 8.2 Files modified

- `gradle/libs.versions.toml` — activate the MediaPipe entry.
- `app/build.gradle.kts` — `implementation(libs.mediapipe.tasks.genai)` (the
  reserved-comment line is replaced with the activation).

### 8.3 `FunctionCallEngine` interface — exact shape

```kotlin
package com.curro.app.domain.repository

import com.curro.app.domain.model.PromptContext

/**
 * On-device decision layer (spec §4.3).
 *
 * Takes an utterance + minimal context and returns the **raw model output**
 * as a string. The string is validated against the function catalog by
 * [com.curro.app.data.ml.FunctionCallValidator] (US-022) — keeping the
 * validation out of this interface lets every test fake the engine with a
 * canned string, lets the validator's failure modes ship independently, and
 * lets future engines (e.g. a constrained-decoding alternative) plug in
 * without touching the validator.
 *
 * Concrete implementations: [com.curro.app.data.ml.FunctionGemmaEngine]
 * (MediaPipe-backed, production); FakeFunctionCallEngine (tests).
 *
 * Lifecycle: [warmUp] loads the model into memory; [isReady] reflects whether
 * the model is currently warm. Implementations may choose to lazy-warm on
 * the first [decide] call, but in production the [ModelWarmupService] (US-023)
 * calls [warmUp] from [CurroApp.onCreate] so the first user-facing press is
 * already under the latency target.
 */
interface FunctionCallEngine {

    /**
     * Maps an utterance to a raw model output string (which the caller validates).
     *
     * @return [Result.success] with the raw model output, or [Result.failure] with:
     *   - [com.curro.app.domain.model.CurroError.ModelCold] — engine not warm; the
     *     impl also kicks [warmUp] as a side effect for next time.
     *   - [com.curro.app.domain.model.CurroError.OutOfMemory] — native OOM during
     *     inference.
     *   - [com.curro.app.domain.model.CurroError.InvalidFunctionCall] — unexpected
     *     native exception during inference (best-effort fallback so callers don't
     *     need a generic `Throwable` branch).
     *
     * Production implementations MUST run the actual inference off the main
     * thread (the MediaPipe `generateResponse` is blocking).
     */
    suspend fun decide(utterance: String, ctx: PromptContext): Result<String>

    /** Idempotent. Loads the model if not already loaded. Safe to call from any thread. */
    fun warmUp()

    /** True iff the model is currently loaded. */
    fun isReady(): Boolean
}
```

### 8.4 `PromptContext` — exact shape

```kotlin
package com.curro.app.domain.model

/**
 * Minimal context surfaced to FunctionGemma alongside the utterance and the
 * catalog (spec §4.3, function-catalog skill "Prompt context").
 *
 * Kept small on purpose — every token competes with accuracy on a 270M model.
 *
 * Phase 3: [unreadMessagesSummary] and [knownAliases] are always empty (the
 * WhatsApp cache and the alias DB don't ship until Phase 4 / Phase 7). The
 * fields exist now so the prompt builder's golden tests pin the final shape;
 * later phases fill them.
 */
data class PromptContext(
    /** Local time in ISO-8601 with no offset, e.g. `2026-05-15T22:36:00`. */
    val nowIso: String,
    /** Short, count-and-senders only — never message bodies. Empty in Phase 3. */
    val unreadMessagesSummary: String,
    /** One per alias, e.g. `"mi hija → Lucía Ruiz"`. Empty in Phase 3. */
    val knownAliases: List<String>,
)
```

### 8.5 `FunctionGemmaEngine` — exact shape (sketch; check current MediaPipe API)

```kotlin
package com.curro.app.data.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.PromptContext
import com.curro.app.domain.repository.FunctionCallEngine
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FunctionGemmaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val promptBuilder: FunctionCallPromptBuilder,
    @IoDispatcher private val io: CoroutineDispatcher,
) : FunctionCallEngine {

    /** Held resident across the process lifetime; null until [warmUp] succeeds. */
    private var llm: LlmInference? = null

    /**
     * Single-flight guard. MediaPipe's [LlmInference] is not documented thread-safe;
     * the cheap mutex prevents a second caller racing into [generateResponse]
     * while a first is in flight. In Phase 3 only the launcher calls this engine
     * so contention is unlikely — the mutex is insurance.
     */
    private val callMutex = Mutex()

    override fun warmUp() {
        if (llm != null) return
        if (!ModelFiles.isFunctionGemmaAvailable()) {
            Log.i(TAG, "warm-up skipped — weights not present at ${ModelFiles.functionGemma().absolutePath}")
            return
        }
        val started = SystemClock.elapsedRealtime()
        runCatching {
            val opts = LlmInferenceOptions.builder()
                .setModelPath(ModelFiles.functionGemma().absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setTemperature(TEMPERATURE)
                .setTopK(TOP_K)
                .build()
            LlmInference.createFromOptions(context, opts)
        }.onSuccess { instance ->
            llm = instance
            val ms = SystemClock.elapsedRealtime() - started
            Log.i(TAG, "warm-up took ${ms}ms")
        }.onFailure { t ->
            Log.w(TAG, "warm-up failed: ${t.javaClass.simpleName}")
        }
    }

    override suspend fun decide(utterance: String, ctx: PromptContext): Result<String> {
        val engine = llm
        if (engine == null) {
            warmUp() // best-effort kick — next call may succeed
            return Result.failure(CurroError.ModelCold)
        }
        return withContext(io) {
            callMutex.withLock {
                val prompt = promptBuilder.build(utterance, ctx)
                val started = SystemClock.elapsedRealtime()
                try {
                    val raw = engine.generateResponse(prompt) // blocking
                    val ms = SystemClock.elapsedRealtime() - started
                    Log.i(TAG, "decide latency: ${ms}ms")
                    // PII boundary: `prompt`, `raw`, and `utterance` are NEVER logged or telemetry-sent.
                    // Only the latency and the model name are safe.
                    Result.success(raw)
                } catch (t: OutOfMemoryError) {
                    Log.w(TAG, "OOM during decide")
                    Result.failure<String>(CurroError.OutOfMemory)
                } catch (t: Throwable) {
                    Log.w(TAG, "decide failed: ${t.javaClass.simpleName}")
                    Result.failure<String>(CurroError.InvalidFunctionCall)
                }
            }
        }
    }

    override fun isReady(): Boolean = llm != null

    private companion object {
        const val TAG = "Curro/Llm"
        const val MAX_TOKENS = 256
        const val TEMPERATURE = 0.1f
        const val TOP_K = 1
    }
}
```

### 8.6 Hilt — `MlModule`

```kotlin
package com.curro.app.di

import com.curro.app.data.ml.FunctionGemmaEngine
import com.curro.app.domain.repository.FunctionCallEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {

    @Binds
    @Singleton
    abstract fun bindFunctionCallEngine(impl: FunctionGemmaEngine): FunctionCallEngine
}
```

### 8.7 `@IoDispatcher` qualifier

If `di/DispatcherModule.kt` doesn't already exist in the repo (verify before
adding):

```kotlin
package com.curro.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
```

(If a similar qualifier already exists from an earlier SF — likely under a
different name — reuse it; do not introduce a parallel qualifier.)

### 8.8 Gradle changes

`gradle/libs.versions.toml` — confirm the entry is present and version-pinned;
the developer may bump to the freshest stable `0.10.x` that is compatible with
Kotlin 2.1 and AGP 8.7. If uncertain, stick to `0.10.14`:

```toml
[versions]
mediapipeGenai = "0.10.14"

[libraries]
mediapipe-tasks-genai = { module = "com.google.mediapipe:tasks-genai", version.ref = "mediapipeGenai" }
```

`app/build.gradle.kts` — replace the reserved-comment line:

```kotlin
// Before:
// MediaPipe    → SF-3.1: implementation(libs.mediapipe.tasks.genai)

// After:
implementation(libs.mediapipe.tasks.genai) // US-020 (SF-3.2): on-device LLM runtime
```

### 8.9 Test fake

`app/src/test/java/com/curro/app/data/ml/fakes/FakeFunctionCallEngine.kt`:

```kotlin
package com.curro.app.data.ml.fakes

import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.PromptContext
import com.curro.app.domain.repository.FunctionCallEngine

/**
 * Test fake. Lives in test source set so production never imports it.
 * Reused by SF-3.6 (US-024) and every subsequent SF that has the engine as a
 * collaborator.
 */
class FakeFunctionCallEngine(
    var nextResult: Result<String> = Result.failure(CurroError.ModelCold),
    var isReadyValue: Boolean = false,
) : FunctionCallEngine {

    var lastUtterance: String? = null
        private set
    var lastContext: PromptContext? = null
        private set
    var warmUpCallCount: Int = 0
        private set

    override suspend fun decide(utterance: String, ctx: PromptContext): Result<String> {
        lastUtterance = utterance
        lastContext = ctx
        return nextResult
    }

    override fun warmUp() {
        warmUpCallCount++
        // Mirror real impl: warmUp sets isReady to whatever the test wants next.
        // Tests configure `isReadyValue` directly; warmUp doesn't flip it here.
    }

    override fun isReady(): Boolean = isReadyValue
}
```

### 8.10 Contract tests

`app/src/test/java/com/curro/app/data/ml/FunctionGemmaEngineContractTest.kt` —
exercises the `FunctionCallEngine` contract via `FakeFunctionCallEngine`.
**Does not import MediaPipe**. ≥ 6 cases:

1. **Cold engine → `CurroError.ModelCold`**: `nextResult` defaulted to
   `Result.failure(CurroError.ModelCold)`; `decide()` returns the failure.
2. **Ready engine returns raw success string**: set `nextResult =
   Result.success("""{"action":"tell_time","params":{"what":"time"},"confidence":0.92}""")`;
   `decide()` returns that exact string. Verifies the engine does not parse or
   modify the raw output.
3. **OOM mapped to `CurroError.OutOfMemory`**: set `nextResult =
   Result.failure(CurroError.OutOfMemory)`; `decide()` returns the failure.
4. **`warmUp()` idempotency**: call `warmUp()` twice; assert
   `warmUpCallCount == 2` on the fake (the **real** impl's idempotency is
   tested only indirectly — `isReady` flips on the first call). Also document:
   "the real impl returns early on second `warmUp()` when `llm != null` —
   verified by inspection of `FunctionGemmaEngine.warmUp`'s first line; the
   on-device gate is US-024's manual test."
5. **`isReady()` reflects the configured value**: set `isReadyValue = true`;
   `isReady() == true`; set to false; `isReady() == false`.
6. **`decide()` captures the (utterance, ctx) pair**: call
   `decide("qué hora es", ctx)`; assert `lastUtterance == "qué hora es"` and
   `lastContext == ctx`. This pins the contract for SF-3.6's coordinator code.

### 8.11 Real engine: why not JVM-testable

The `FunctionGemmaEngine.kt` file contains an explicit comment:

```kotlin
/**
 * NOT JVM-testable. The MediaPipe `LlmInference` requires native binaries
 * (`.so` files for the device's ABI) that are absent in JVM unit tests.
 * Real-engine verification is the on-device gate in SF-3.6 (US-024):
 *   - `Log.i("Curro/Llm", "warm-up took <ms>ms")` appears in logcat
 *   - `Log.i("Curro/Llm", "decide latency: <ms>ms")` shows < 500 ms warm
 *   - 10 consecutive "qué hora es" runs all under 500 ms
 *
 * For JVM tests, inject `FakeFunctionCallEngine` (see
 * test/.../fakes/FakeFunctionCallEngine.kt).
 */
```

---

## 9. Senior-UX & Copy

No new user-facing copy in this SF. The cold-engine spoken line
(`copy_models_not_ready`) was added in US-019; the smoke-loop wiring is in
US-024.

---

## 10. Acceptance Criteria

Mirroring the PRD entry (checkable):

- [ ] `FunctionCallEngine` interface in `domain/repository/` — pure Kotlin, no
  Android imports; `suspend fun decide(utterance, ctx): Result<String>` (raw
  string, not `FunctionCall`).
- [ ] `PromptContext` data class in `domain/model/` with the three fields.
- [ ] `FunctionGemmaEngine` in `data/ml/` with the exact shape above.
- [ ] `MlModule` Hilt binding `FunctionCallEngine → FunctionGemmaEngine`
  `@Singleton`.
- [ ] **MediaPipe imports ONLY in `data/ml/FunctionGemmaEngine.kt`** —
  verified by `grep -r "com.google.mediapipe"
  app/src/main/java/com/curro/app/`.
- [ ] **No MediaPipe imports in any test file** — verified by `grep -r
  "com.google.mediapipe" app/src/test/java/ app/src/androidTest/java/`.
- [ ] `gradle/libs.versions.toml` MediaPipe entry active with a pinned
  version (`0.10.14` if uncertain).
- [ ] `app/build.gradle.kts` has
  `implementation(libs.mediapipe.tasks.genai)`.
- [ ] `FakeFunctionCallEngine` in `app/src/test/java/com/curro/app/data/ml/fakes/`.
- [ ] `FunctionGemmaEngineContractTest` covers the 6 cases above.
- [ ] **Threading discipline**: `decide()` wraps the MediaPipe call in
  `withContext(io) { callMutex.withLock { … } }`. Production code comment makes
  the rationale explicit.
- [ ] Latency log on every `decide()`: `Log.i("Curro/Llm", "decide latency:
  ${ms}ms")` — only the duration, never the utterance.
- [ ] Telemetry event (via the existing `TelemetryGuardrail` from US-008):
  `event("model_inference", mapOf("model" to "function_gemma_270m",
  "latency_ms" to ms))` on success only. Failure variants get a separate
  outcome label in US-024's `model_decide` event (which is at a higher layer,
  not in this SF).
- [ ] `CurroError.ModelCold` and `CurroError.OutOfMemory` mappings verified.
- [ ] No new permissions, no manifest change.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green **without** the `.task` file present.

---

## 11. Performance Considerations

- The MediaPipe `generateResponse` is blocking and native — wrapping it in
  `withContext(io)` keeps the coroutine off the main thread; the actual
  inference work runs on whatever threads MediaPipe internally uses.
- `LlmInferenceOptions` is built lazily on `warmUp()`, not on construction —
  construction is cheap, Hilt can inject the engine wherever without paying
  the warm-up cost.
- The `Mutex` adds a microsecond-level overhead per call; negligible against
  a < 500 ms inference budget.
- `Log.i` latency logs are unconditional — DEBUG-only would lose them on
  release builds where we still want the line. The line contains no PII.
- The engine is `@Singleton` — one instance per process, one model in memory.
- Memory: 288 MB resident for the model file plus MediaPipe's runtime
  overhead. On the 8 GB Redmi 15 variant this is unremarkable; on the 4 GB
  variant it eats meaningful headroom. Phase 1's design doesn't load Gemma 3n
  alongside, so the 4 GB risk is on Phase 9, not here.

---

## 12. Testing Requirements

- [ ] **Unit**: the 6 contract tests above against `FakeFunctionCallEngine`.
- [ ] **Unit**: a separate sanity test that imports `FunctionGemmaEngine`
  reflectively (so the test compiles even though it never instantiates the
  class) to verify the field shape matches the interface — optional, only if
  the developer wants the safety net.
- [ ] **Manual on the Redmi 15** (after weights are side-loaded):
  - `adb logcat -s Curro/Llm` shows `warm-up took <ms>ms` on app startup
    once US-023 lands.
  - Triggering a `decide()` (via US-024's smoke loop) shows `decide
    latency: <ms>ms` < 500 ms warm.
  - Force-killing the app (`adb shell am force-stop com.curro.app`) and
    pressing the mic shows a `CurroError.ModelCold` failure on the first
    press, then a successful run on the second.
- [ ] **No instrumented test** for the real engine — MediaPipe needs the
  weights present on the device, which is a deployment story, not a CI story.

---

## 13. Implementation Notes

### Order of operations

1. **Skip MediaPipe activation first**: confirm the build is green without it
   by running `./gradlew assembleDebug` on the current main.
2. Add `domain/model/PromptContext.kt`.
3. Add `domain/repository/FunctionCallEngine.kt`.
4. Add `di/MlModule.kt` (with a placeholder binding pointing to a future
   `FunctionGemmaEngine` — this won't compile until step 7).
5. Activate the MediaPipe dependency in `app/build.gradle.kts`.
6. Verify `gradle/libs.versions.toml` MediaPipe entry is pinned and active.
7. Add `data/ml/FunctionGemmaEngine.kt`.
8. Verify `@IoDispatcher` exists; if not, add `di/DispatcherModule.kt`.
9. Add `app/src/test/.../fakes/FakeFunctionCallEngine.kt`.
10. Add `app/src/test/.../FunctionGemmaEngineContractTest.kt`.
11. Run `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` —
    expect green.
12. Side-load the model file on the Redmi 15 (per `docs/MODELS.md` from
    US-019).
13. Install the APK, verify the warm-up log line appears (will require US-023
    or a manual `engine.warmUp()` from a debug screen — this SF doesn't add
    a UI trigger; the warm-up is exercised end-to-end in US-024).

### `Result<String>` vs `Result<FunctionCall>` decision

The master-plan SF-3.2 paragraph reads "decide(utterance, ctx):
Result<FunctionCall>". This brief deliberately overrides that to
`Result<String>` because:

1. **Validation is a separate concern.** Pushing the validator inside the
   engine couples MediaPipe (in `data/ml/`) to the catalog (in
   `domain/catalog/`). Keeping the engine catalog-agnostic respects Clean
   Architecture.
2. **Test ergonomics.** Faking `Result<String>` in tests is trivial (any JSON
   literal); faking `Result<FunctionCall>` requires constructing a typed call
   object that's already validated, which is circular.
3. **SF-3.6 (the coordinator) is where parsing belongs.** The coordinator
   reads the raw string and calls the validator; that's the right seam.

The function-catalog skill's "Output contract" still applies: the model
**produces** the JSON; the validator **parses** it; the engine just **carries**
the bytes between them.

### Threading: why the mutex

`LlmInference` is not documented as thread-safe. The mutex is cheap; the
alternative (a `Semaphore(1)` or a single-thread dispatcher) adds complexity
for no benefit. Document the choice in the production code comment.

### Latency log format

`Log.i("Curro/Llm", "decide latency: ${ms}ms")` — chosen because:

- `Curro/Llm` namespacing matches the `Curro/FailedCommand` namespacing used
  elsewhere in the project (CLAUDE.md's "Error handling" comment block names
  this convention).
- `ms` units are unambiguous on logcat (microseconds would be misread).
- The format string is greppable: `adb logcat -s Curro/Llm` filters cleanly.

### Telemetry event name

`model_inference` — generic enough that the same event covers Gemma 3n
(Phase 9) by changing the `model` property. The properties are strictly
event-shape-safe (no PII): `model: "function_gemma_270m"`, `latency_ms: <int>`.

### Commit scope

`feat(llm)` — per `git-workflow` skill, the LLM engine and runtime wiring is
its own scope.

---

## 14. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-15 | android-product-analyst | Initial draft for Phase-3 PM batch. |
