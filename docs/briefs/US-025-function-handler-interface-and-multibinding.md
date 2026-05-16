# US-025 — SF-4.1 · `FunctionHandler` interface + `HandlerResult` sealed + Hilt multibinding

> **Spec trace:** spec §4.5 (capa de ejecución — handlers nativos), spec §6
> (every state transition that ends in `executing` flows through what this SF
> defines).
> **Master-plan:** SF-4.1.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-024 (`LauncherViewModel`'s smoke loop is the rewire point),
> US-022 (`FunctionCall`).
> **Size:** S.
> **Skills:** `function-catalog`, `voice-interaction`, `compose-patterns`, `git-workflow`, `testing-patterns`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `FunctionHandler` interface + `HandlerResult` sealed + Hilt multibinding |
| **US ID** | US-025 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

---

## 1. Summary

SF-4.1 lays the foundation that every Phase-4 handler will plug into. It
introduces:

- `domain/handler/FunctionHandler` — the interface every handler implements.
- `domain/handler/HandlerResult` — the sealed contract every handler returns.
- `domain/handler/HandlerDispatcher` — a `@Singleton` that reads a Hilt-multibound
  `Map<String, FunctionHandler>` keyed by catalog function name and dispatches a
  validated `FunctionCall` to the right handler.
- `di/HandlerModule` — empty `@Module` (with a `@Multibinds` marker so the empty
  graph is valid); SF-4.2 through SF-4.10 each append one `@Binds @IntoMap @StringKey`
  line.
- `LauncherViewModel` rewire — the SF-3.6 smoke loop's `"Reconocido: <action_label>"`
  TTS echo is replaced by `dispatcher.dispatch(call)`; the dispatched
  `HandlerResult` drives TTS and the state return-to-Idle.

Why this matters for *this* user: every utterance that already routes through
FunctionGemma + the validator (SF-3.6) now lands in a real handler — and from
this commit on, every new function ships as **one Kotlin file + one `@Binds`
line**, no central `when` to forget to update. Fran's father gets the first
real Fase-1 behaviour (`tell_time`) the next SF.

---

## 2. Scope

**In scope:**

- The three `domain/handler/` files (`FunctionHandler`, `HandlerResult`,
  `AssistantScreen` marker, `HandlerDispatcher`).
- `di/HandlerModule.kt` — empty module + `@Multibinds`.
- `LauncherViewModel` rewire: replace the action-description echo with
  `dispatcher.dispatch(call)`; preserve the debug-JSON overlay; preserve the
  failed-command `Log.w`; remove `ACTION_DESCRIPTION_MAP` + `actionDescription`.
- `CurroError.HandlerCrash` new variant.
- `copy_handler_crash` new `strings.xml` entry.
- `TelemetryGuardrail` whitelist: `handler_invoked` event + fixture tests.
- ≥ 6 JVM tests on `HandlerDispatcher`; ≥ 3 added to `LauncherViewModelTest`.

**Out of scope:**

- Any real handler (SF-4.2 ships `tell_time`).
- Phase 6's `ConfidencePolicy` gating around `NeedsConfirmation` — for Phase 4
  the dispatcher's caller (`LauncherViewModel`) auto-invokes `onConfirm()`. The
  brief documents the exact Phase-6 hook point.
- `AssistantScreen` subclasses — Phase 5's FSM populates them. SF-4.1 ships
  the marker as an empty `sealed interface` so handlers can carry a `null`
  `screen`.

---

## 3. User Flows

This SF is plumbing — no new user-visible flow. It rewires SF-3.6's smoke
loop so that **future** SFs (4.2 onwards) become user-visible. The new
end-to-end flow is described as a sanity check.

### Flow 1: A future "qué hora es" — after SF-4.2 ships

1. User presses the mic button → `listening` (US-017 — unchanged).
2. STT transcribes "qué hora es" → `processing` (US-024 — unchanged).
3. `FunctionGemma` → `validator` → `Result.success(FunctionCall("tell_time", {}, 0.95))`
   (US-022 + US-024 — unchanged).
4. **NEW**: `LauncherViewModel.handleDecisionSuccess` calls
   `dispatcher.dispatch(call)`. Dispatcher reads the multibinding map, finds
   `TellTimeHandler`, calls `handler.handle(call)`.
5. **NEW**: Handler returns `Spoken("Son las doce y cuarenta y siete del miércoles trece de mayo.")`
   (in SF-4.2; for SF-4.1 itself the map is empty, so the dispatch returns
   `Failed(UnknownFunction)` and the user hears `copy_error_unknown_function`).
6. `LauncherViewModel` speaks the `speech` → returns to `Idle`.

### Flow 2: Unknown action — empty map (the SF-4.1 ship state)

1. User: "qué hora es".
2. Validator emits `FunctionCall("tell_time", {}, 0.95)`.
3. `dispatcher.dispatch` → map is empty → returns
   `Failed(copy_error_unknown_function, UnknownFunction("tell_time"))`.
4. ViewModel speaks the fallback line + logs `Curro/FailedCommand`.

### Flow 3: Handler throws

1. A future handler (post-4.2) throws an `IllegalStateException` mid-handle.
2. `HandlerDispatcher` catches via `runCatching` → returns
   `Failed(copy_handler_crash, HandlerCrash(action, e))`.
3. ViewModel speaks `copy_handler_crash` ("Algo se ha torcido por dentro.
   Inténtalo otra vez en un momento.") + logs.

### Flow 4: `NeedsConfirmation` — auto-confirm (Phase 4) / policy gate (Phase 6)

1. A future handler returns `NeedsConfirmation(prompt, onConfirm)`.
2. **Phase 4**: `LauncherViewModel` immediately invokes `onConfirm()` and
   recursively renders its result. This is the auto-confirm behaviour pinned
   here for Phase 4's locked scope.
3. **Phase 6 (not this SF — documented hook)**: SF-6.x's `ConfidencePolicy`
   wraps this branch — for `confidence < 0.85`, instead of auto-invoking
   `onConfirm()`, transition to `confirming` with the prompt; resume on user
   "sí"/"no".

---

## 4. Function-catalog Impact

**No catalog change.** This SF defines the **shape** every catalog function's
handler implements. Each Phase-4 handler SF (4.2–4.10) appends one
`@Binds @IntoMap @StringKey` line to `HandlerModule` and ships its handler.

---

## 5. FSM States Touched

Provisional FSM (Phase 2/3 — `ListeningState` enum) — no new states; the
existing `Processing → Speaking → Idle` path is preserved. The Phase 5 FSM
(SF-5.x) replaces `ListeningState` with `AssistantState`; the dispatcher and
its `HandlerResult` contract survive that swap untouched.

**No new always-escalate condition.** Phase 6 (SF-6.x) wires the policy gate
that runs **between** the dispatcher and the handler's `NeedsConfirmation`
branch.

---

## 6. Android System Integrations & Permissions

None new.

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| _(none)_ | This SF is pure code architecture. | — | — |

No manifest changes. No new system integration.

---

## 7. On-device-model Impact

**No model impact.** This SF lives downstream of `FunctionCallEngine` and
`FunctionCallValidator`; it doesn't touch the prompt, the LLM, the warm-up
service, or model selection.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/
├── domain/handler/
│   ├── FunctionHandler.kt
│   ├── HandlerResult.kt
│   ├── AssistantScreen.kt
│   └── HandlerDispatcher.kt
└── di/
    └── HandlerModule.kt
```

### 8.2 `FunctionHandler.kt`

```kotlin
package com.curro.app.domain.handler

import com.curro.app.domain.model.FunctionCall

/**
 * Every catalog function (spec §5, `function-catalog` skill) has exactly one
 * [FunctionHandler] that turns a validated [FunctionCall] into a [HandlerResult].
 *
 * Handlers are bound into a Hilt `Map<String, FunctionHandler>` keyed by
 * [functionName]; the [HandlerDispatcher] looks them up and dispatches.
 *
 * Implementations live in `handler/`. They must NEVER throw — every failure
 * routes through [HandlerResult.Failed] with a typed [CurroError] and a plain
 * Spanish [HandlerResult.Failed.speech] (spec §2: "Fallar de forma comprensible").
 * If a handler throws despite this contract, [HandlerDispatcher] catches it and
 * surfaces a [HandlerResult.Failed] with [CurroError.HandlerCrash].
 */
interface FunctionHandler {
    /** Catalog function name — used as the @StringKey for the Hilt multibinding. */
    val functionName: String

    suspend fun handle(call: FunctionCall): HandlerResult
}
```

### 8.3 `HandlerResult.kt`

```kotlin
package com.curro.app.domain.handler

import com.curro.app.domain.model.CurroError

/**
 * Closed set of outcomes a [FunctionHandler] can return.
 *
 * Phase 4 — every result terminates a turn directly. Phase 6 inserts the
 * confidence policy gate between the dispatcher and a [NeedsConfirmation]
 * branch (so far the only handler that emits [NeedsConfirmation] is
 * `call_contact`, and only Phase 6's policy decides whether to execute or
 * prompt).
 */
sealed interface HandlerResult {
    /**
     * The handler did its work. [speech] is the Spanish line TTS speaks (and
     * the UI shows). [screen] is an optional state-driven overlay payload;
     * Phase 5's FSM populates the [AssistantScreen] subclasses — Phase 4
     * always leaves this `null`.
     */
    data class Spoken(
        val speech: String,
        val screen: AssistantScreen? = null,
    ) : HandlerResult

    /**
     * The handler is ready to execute but the action is irreversible / ambiguous /
     * the user has "always confirm" on. Phase 4 — the dispatcher's caller
     * auto-invokes [onConfirm] and recurses. Phase 6 — the policy gate
     * intercepts this branch.
     */
    data class NeedsConfirmation(
        val prompt: String,
        val onConfirm: suspend () -> HandlerResult,
    ) : HandlerResult

    /**
     * The handler couldn't do it. [speech] explains why in plain Spanish
     * (never a code, never silence). [reason] is the typed [CurroError] for
     * the failed-command log + telemetry.
     */
    data class Failed(
        val speech: String,
        val reason: CurroError,
    ) : HandlerResult
}
```

### 8.4 `AssistantScreen.kt`

```kotlin
package com.curro.app.domain.handler

/**
 * Provisional marker — Phase 5's FSM (SF-5.1) replaces this with the real
 * state-driven overlay payloads (`MessagesScreen`, `ContactPickerScreen`,
 * etc.). For Phase 4, every [HandlerResult.Spoken.screen] is `null`.
 */
sealed interface AssistantScreen
```

### 8.5 `HandlerDispatcher.kt`

```kotlin
package com.curro.app.domain.handler

import android.content.Context
import androidx.annotation.StringRes
import com.curro.app.R
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.repository.TelemetrySink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a validated [FunctionCall] to the right [FunctionHandler] via a
 * Hilt-multibound map keyed by catalog function name.
 *
 * Failure modes:
 *  - Action not in the map → [HandlerResult.Failed] with [CurroError.UnknownFunction]
 *    and the Spanish line `copy_error_unknown_function`.
 *  - The handler throws → [HandlerResult.Failed] with [CurroError.HandlerCrash]
 *    and the Spanish line `copy_handler_crash`.
 *
 * Telemetry: emits `handler_invoked` with `function_name` + `outcome` ∈
 * {success, needs_confirmation, failed, crash}. Never the utterance, never any
 * param value.
 */
@Singleton
class HandlerDispatcher
    @Inject
    constructor(
        private val handlers: Map<String, @JvmSuppressWildcards FunctionHandler>,
        private val telemetry: TelemetrySink,
        @ApplicationContext private val context: Context,
    ) {
        suspend fun dispatch(call: FunctionCall): HandlerResult {
            val handler =
                handlers[call.action]
                    ?: return reportAndReturn(
                        call.action,
                        HandlerResult.Failed(
                            speech = context.getString(R.string.copy_error_unknown_function),
                            reason = CurroError.UnknownFunction(call.action),
                        ),
                    )
            val result =
                runCatching { handler.handle(call) }.getOrElse { e ->
                    HandlerResult.Failed(
                        speech = context.getString(R.string.copy_handler_crash),
                        reason = CurroError.HandlerCrash(call.action, e),
                    )
                }
            return reportAndReturn(call.action, result)
        }

        private fun reportAndReturn(action: String, result: HandlerResult): HandlerResult {
            val outcome =
                when (result) {
                    is HandlerResult.Spoken -> "success"
                    is HandlerResult.NeedsConfirmation -> "needs_confirmation"
                    is HandlerResult.Failed ->
                        if (result.reason is CurroError.HandlerCrash) "crash" else "failed"
                }
            telemetry.event(
                "handler_invoked",
                mapOf(
                    "function_name" to action,
                    "outcome" to outcome,
                ),
            )
            return result
        }
    }
```

### 8.6 `HandlerModule.kt`

```kotlin
package com.curro.app.di

import com.curro.app.domain.handler.FunctionHandler
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Empty in this SF — each subsequent Phase-4 handler SF appends a single
 *
 *   @Binds @IntoMap @StringKey("<function_name>")
 *   abstract fun bind<X>(impl: <X>Handler): FunctionHandler
 *
 * line. `@Multibinds` lets Hilt resolve `Map<String, FunctionHandler>` even
 * when no entries are bound — without it, the Phase-4 build (SF-4.1 only,
 * empty map) fails to compile.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HandlerModule {
    @Multibinds
    abstract fun handlerMap(): Map<String, FunctionHandler>
}
```

### 8.7 `LauncherViewModel` rewire

`LauncherViewModel.handleDecisionSuccess(call, latencyMs, transcript)` becomes:

```kotlin
private suspend fun handleDecisionSuccess(
    call: FunctionCall,
    latencyMs: Int,
    transcript: String,
) {
    emitDecideEvent(outcome = "success", latencyMs = latencyMs)
    if (BuildConfig.DEBUG) {
        _sideEffects.send(LauncherSideEffect.ShowDebugJson(prettyPrint(call)))
    }
    val result = dispatcher.dispatch(call)
    render(result, call.action, transcript)
}

private suspend fun render(
    result: HandlerResult,
    action: String,
    transcript: String,
) {
    when (result) {
        is HandlerResult.Spoken -> speakAndIdle(result.speech)
        is HandlerResult.NeedsConfirmation -> {
            // Phase 6 inserts the ConfidencePolicy gate here.
            val inner = result.onConfirm()
            render(inner, action, transcript)
        }
        is HandlerResult.Failed -> {
            Log.w(
                FAILED_TAG,
                "action=$action error=${result.reason::class.simpleName} utterance.len=${transcript.length}",
            )
            speakAndIdle(result.speech)
        }
    }
}

private suspend fun speakAndIdle(message: String) {
    listeningStateFlow.value = ListeningState.Speaking(message)
    ttsClient.speak(message)
    listeningStateFlow.update { current ->
        if (current is ListeningState.Speaking) ListeningState.Idle else current
    }
}
```

**Removed**: `ACTION_DESCRIPTION_MAP`, `actionDescription(action: String)`,
and the `"Reconocido: " + …` echo construction. `copy_recognized_prefix` and
the seven `copy_action_*` strings remain in `strings.xml` (no orphan-cleanup
this SF — Phase 5 reviews).

**Constructor**: gain `private val dispatcher: HandlerDispatcher` — Hilt
injects it.

### 8.8 `CurroError` addition

Append to `domain/model/CurroError.kt`:

```kotlin
/** A handler threw despite the never-throw contract — surfaced via dispatcher's safety net. */
data class HandlerCrash(val functionName: String, val cause: Throwable) : CurroError()
```

### 8.9 `strings.xml` addition

```xml
<!-- US-025 (SF-4.1) — HandlerDispatcher safety-net line when a handler throws. -->
<string name="copy_handler_crash">Algo se ha torcido por dentro. Inténtalo otra vez en un momento.</string>
```

### 8.10 `TelemetryGuardrail` whitelist addition

`ALLOWED_PROPS` gains:

```kotlin
"handler_invoked" to setOf("function_name", "outcome"),
```

(Replaces the SF-4.x placeholder `"handler_finished"` entry, which was speculative — pin in brief: delete it the same commit if it's still there.)

`outcome` values are `success | needs_confirmation | failed | crash`.

### 8.11 Hilt graph

The `LauncherViewModel` constructor gains `HandlerDispatcher`; Hilt resolves
it from the empty `Map<String, FunctionHandler>` provided by `HandlerModule`'s
`@Multibinds`. No `RepositoryModule` change.

### 8.12 ViewModels and State Management

No new ViewModel. `LauncherViewModel` is the only ViewModel touched; its
public `uiState` / `sideEffects` / `onEvent` surface is unchanged.

### 8.13 Navigation Routes

No new routes — the assistant overlay sequence is preserved.

---

## 9. Acceptance Criteria

- [ ] `domain/handler/FunctionHandler.kt`, `HandlerResult.kt`,
      `AssistantScreen.kt`, `HandlerDispatcher.kt` exist at the documented
      paths.
- [ ] `HandlerModule` exists in `di/`, is empty, has `@Multibinds` so the
      graph compiles.
- [ ] `LauncherViewModel.handleDecisionSuccess(...)` no longer constructs
      `"Reconocido: " + actionDescription(...)`. It calls
      `dispatcher.dispatch(call)` and routes the result through `render(...)`.
- [ ] `ACTION_DESCRIPTION_MAP` and `actionDescription(...)` are removed from
      `LauncherViewModel`. The seven `copy_action_*` strings remain in
      `strings.xml` (orphan cleanup deferred to Phase 5).
- [ ] `Spoken` → TTS speaks `speech`, state returns to `Idle`.
- [ ] `NeedsConfirmation` → `onConfirm()` immediately invoked, its result
      rendered (Phase-4 "auto-confirm" behaviour — Phase 6 hook commented).
- [ ] `Failed` → TTS speaks `speech`, `Log.w("Curro/FailedCommand",
      "action=$action error=${reason::class.simpleName} utterance.len=${transcript.length}")`,
      state returns to `Idle`. **The utterance text never appears in the log
      line.**
- [ ] An empty handler map (the SF-4.1 ship state) + any `FunctionCall` →
      `dispatcher.dispatch` returns `Failed(copy_error_unknown_function,
      UnknownFunction(action))`. The launcher speaks the fallback line.
- [ ] A handler that throws → `dispatcher.dispatch` catches it and returns
      `Failed(copy_handler_crash, HandlerCrash(action, cause))`.
- [ ] `CurroError.HandlerCrash(functionName, cause)` added; existing
      `UnknownFunction` variant reused.
- [ ] `strings.xml` gains `copy_handler_crash`; the existing
      `copy_error_unknown_function` is reused.
- [ ] `TelemetryGuardrail.ALLOWED_PROPS` gains `handler_invoked` →
      `{function_name, outcome}`. Any pre-existing speculative
      `handler_finished` entry is deleted.
- [ ] `TelemetryGuardrailTest` gains ≥ 4 fixtures: allow
      `(handler_invoked, function_name=tell_time, outcome=success)`; allow
      `outcome=needs_confirmation`; reject
      `function_name=<33-char-transcript-shaped-value>` (MAX_VALUE_LEN);
      reject extra key `phone_number`.
- [ ] `HandlerDispatcherTest` has ≥ 6 cases (empty map; unknown action;
      success path; needs-confirmation path; failed path; throw path).
- [ ] `LauncherViewModelTest` gains ≥ 3 cases (Spoken → TTS speaks; Failed →
      TTS speaks + log line without utterance; NeedsConfirmation →
      auto-confirm).
- [ ] **No PII** in the failed-command log line (verified by an arg-captor
      test on `Log.w`).
- [ ] **No PII** in `handler_invoked` telemetry — only the snake_case action
      name + the outcome enum.
- [ ] No new permissions; no manifest changes; no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green.

---

## 10. Senior-UX & Copy

| String ID | Spanish | Notes |
|---|---|---|
| `copy_handler_crash` (NEW) | "Algo se ha torcido por dentro. Inténtalo otra vez en un momento." | Curro voice: honest ("se ha torcido"), brief, offers retry, no code, no apology. |
| `copy_error_unknown_function` (existing) | "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'." | Reused for the unknown-action path. |

Every Curro→user message is **spoken AND shown** — preserved by reusing
`speakAndIdle` which sets `ListeningState.Speaking(message)` (the overlay
renders the same text) and calls `ttsClient.speak(message)`.

---

## 11. Design Notes

No new visual surface. The existing `ListeningOverlay`'s Speaking-state UI
absorbs the new handler-driven speech. Sizing, contrast, and typography are
inherited from US-018's overlay and US-006's shared big components.

---

## 12. Performance Considerations

- `HandlerDispatcher` is `@Singleton`; the `Map<String, FunctionHandler>` is
  resolved once by Hilt and survives the process. Dispatch is `O(1)` lookup
  + the handler's own suspend body.
- `LauncherViewModel.render(...)` is `suspend` and runs on `viewModelScope`'s
  main dispatcher — handlers themselves are responsible for hopping to `IO`
  for system I/O (each SF specifies this).
- `runCatching` adds one stack frame per dispatch; immeasurable on the
  Redmi 15.
- No additional allocations on the hot path beyond the `HandlerResult` data
  classes (already cheap immutables).

---

## 13. Testing Requirements

**`HandlerDispatcherTest.kt`** — pure JVM, no Robolectric:

- Test fakes: a `FakeFunctionHandler(functionName, behaviour)` where
  `behaviour` is one of `{Spoken, NeedsConfirmation, Failed, Throw}`. Each
  test wires a small map manually (Hilt is bypassed at unit level).
- A `FakeTelemetrySink` capturing every `(event, props)` for assertion.

Cases:

1. Empty map + any `FunctionCall("anything")` → `Failed(UnknownFunction)`,
   speech == `copy_error_unknown_function`. Telemetry event
   `handler_invoked(function_name=anything, outcome=failed)`.
2. Map with `{ "tell_time" to handlerThatReturnsSpoken }` + call with action
   `"tell_time"` → returns the `Spoken` unchanged. Telemetry event
   `outcome=success`.
3. Map with `{ "tell_time" to … }` + call with action `"other"` →
   `Failed(UnknownFunction("other"))`.
4. `NeedsConfirmation` path → returned unchanged (the dispatcher does NOT
   auto-invoke; that's the ViewModel's job). Telemetry
   `outcome=needs_confirmation`.
5. Handler throws → `Failed(HandlerCrash(action, throwable))`. Telemetry
   `outcome=crash`.
6. Concurrent dispatch of two calls — both succeed independently (sanity).

**`LauncherViewModelTest.kt`** additions — Robolectric-backed (it already is):

- A fake `HandlerDispatcher` with a programmable next-result.
- Cases:
  - `dispatcher.next = Spoken("Son las doce")` → TTS captured `"Son las doce"`,
    final state `Idle`.
  - `dispatcher.next = Failed("No tengo ninguna app", AppNotFound("foo"))` →
    TTS captured `"No tengo ninguna app"`, `Log.w("Curro/FailedCommand", ...)`
    captured (arg-captor) — substring assertion: contains `"utterance.len="`,
    does NOT contain `"foo bar baz"` (the original transcript). Final state
    `Idle`.
  - `dispatcher.next = NeedsConfirmation("¿lo hago?", onConfirm = { Spoken("hecho") })` →
    TTS captured `"hecho"` (auto-confirm path). Final state `Idle`.

**`TelemetryGuardrailTest.kt`** additions:

- `check("handler_invoked", mapOf("function_name" to "tell_time", "outcome" to "success"))` → Allow.
- `check("handler_invoked", mapOf("function_name" to "tell_time", "outcome" to "needs_confirmation"))` → Allow.
- `check("handler_invoked", mapOf("function_name" to ("a".repeat(33)), "outcome" to "success"))` → Reject (length).
- `check("handler_invoked", mapOf("function_name" to "tell_time", "outcome" to "success", "phone_number" to "+34600000000"))` → Reject (extra key).

**On-device verification**: deferred to SF-4.2 (which ships the first real
handler). SF-4.1's on-device sanity is just "the app still installs and the
smoke loop still degrades comprehensibly when the map is empty" — checked
manually on the Redmi 15.

---

## 14. Implementation Notes — Order of Operations

The dev pass for SF-4.1 should land in this order to keep the build green
at each step:

1. Add `CurroError.HandlerCrash` to `domain/model/CurroError.kt`.
2. Create `domain/handler/FunctionHandler.kt`, `HandlerResult.kt`,
   `AssistantScreen.kt`. No dependencies on anything else yet.
3. Add `copy_handler_crash` to `strings.xml`.
4. Create `HandlerDispatcher.kt` using `R.string.copy_handler_crash` +
   `R.string.copy_error_unknown_function`.
5. Create `di/HandlerModule.kt` with `@Multibinds`.
6. Add `handler_invoked` to `TelemetryGuardrail.ALLOWED_PROPS`; delete any
   pre-existing speculative `handler_finished`.
7. Add the fixture tests to `TelemetryGuardrailTest`.
8. Wire `HandlerDispatcher` into `LauncherViewModel`'s constructor.
9. Replace `handleDecisionSuccess(...)`'s body and add the `render(...)` +
   `speakAndIdle(...)` helpers.
10. Remove `ACTION_DESCRIPTION_MAP` + `actionDescription(...)` from
    `LauncherViewModel`.
11. Write `HandlerDispatcherTest`.
12. Extend `LauncherViewModelTest` with the 3 new cases.
13. Run `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
14. Smoke-test on the Redmi 15: press → "qué hora es" → fallback line spoken
    (because the map is empty in this SF — SF-4.2 wires the first real
    handler).
15. Commit as `feat: add FunctionHandler interface + HandlerDispatcher
    multibinding (US-025 / SF-4.1)`.

---

## 15. Phase 5/6 Hooks (documented for future SFs, no action this SF)

- `LauncherViewModel.render(...)`'s `NeedsConfirmation` branch is the
  insertion point for Phase 6's `ConfidencePolicy`. The Phase-6 brief will
  replace `val inner = result.onConfirm()` with
  `policy.evaluate(call.confidence, result)` and the transition into
  `confirming`.
- The whole `LauncherViewModel` smoke-loop layer is the Phase-5 swap point:
  SF-5.2's `AssistantCoordinator` owns this code; the ViewModel becomes a
  thin observer of `AssistantState`. The dispatcher contract survives that
  swap unchanged.

---

## 16. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
