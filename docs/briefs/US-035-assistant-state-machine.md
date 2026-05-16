# US-035 — SF-5.1 · `AssistantStateMachine` + `AssistantState` sealed interface

> **Spec trace:** spec §4 (the layered pipeline — the FSM is the spine of layers
> 5–6 + the capture/output ends), spec §6 (the canonical FSM — every state, every
> transition, the 10-s `confirming` timeout, the 3-strike STT recovery, the
> interrupt rule called out as *"Cualquier estado puede ser interrumpido por una
> nueva pulsación del botón → vuelve a `escuchando`"* — the load-bearing
> sentence), spec §11 (per-state UI).
> **Master-plan:** SF-5.1.
> **Phase:** 5 — State machine & interruption.
> **Depends on:** US-025 (handler interface, `HandlerResult`, `AssistantScreen`).
> **Size:** M.
> **Skills:** `voice-interaction` (rule 1: interrupt-by-button; rule 4:
> consecutive-failure policy; rule 5: 10-s `confirming` timeout — Phase 6 wires
> the timeout, but this SF's state shape **already** carries the deadline so
> Phase 6 doesn't refactor), `launcher-ui` (rule 3: state-driven overlays),
> `brand-design` (COPY table), `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `AssistantStateMachine` — the single owner of the six-state assistant FSM |
| **US ID** | US-035 |
| **Phase** | 5 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | voice-pipeline-engineer |

---

## 1. Summary

The single, authoritative owner of the assistant state machine. `AssistantState`
is a `sealed interface` with the six spec-§6 states (`Idle`, `Listening`,
`Processing`, `Confirming`, `Executing`, `ErrorRecovery`), each carrying the data
its UI overlay and behaviour need. `AssistantStateMachine` exposes
`StateFlow<AssistantState>` and a single `transition(event)` entry point that
validates the `(current, event)` pair against the spec §6 diagram and updates
the state atomically.

**No transition outside this class.** Every Phase-5+ pipeline component
(`AssistantCoordinator`, the launcher VM, `MainActivity.onNewIntent`) sends
events; only the FSM sets state. Invalid transitions throw
`IllegalAssistantTransition` — caught by the test suite, never by the
coordinator (a thrown invalid transition is a bug in the caller).

This SF builds the **pure state container** only — no STT, TTS, model, or
handler wire-up. SF-5.2 (`AssistantCoordinator`) drives it; SF-5.5 keys the UI
off its `StateFlow`.

Why this matters for *this* user: Phases 2–4 grew an ad-hoc `ListeningState`
(`Idle/Starting/Listening/Processing/Speaking/Error`) inside the launcher VM.
That worked for the smoke loop, but the spec §6 diagram has **six** states with
strict transitions, an interrupt-anywhere rule (spec §6 closing paragraph), a
10-s `confirming` timeout (spec §6 flow 2), and a 3-strike STT recovery (spec §6
flow 6). Trying to bolt those on the provisional `ListeningState` would yield a
tangle. This SF stops that.

---

## 2. Scope

**In scope:**

- `app/src/main/java/com/curro/app/assistant/AssistantState.kt` — the
  `sealed interface` with six states.
- `app/src/main/java/com/curro/app/assistant/AssistantEvent.kt` — the
  `sealed interface` with the 11 events.
- `app/src/main/java/com/curro/app/assistant/AssistantStateMachine.kt` — the
  `@Singleton` owner of `StateFlow<AssistantState>` and `transition(event)`.
- `app/src/main/java/com/curro/app/assistant/TimeProvider.kt` — small interface
  + production impl backed by the existing injected `java.time.Clock` (from
  `di/TimeModule.kt`). Pinned: do **not** call `System.currentTimeMillis()`
  anywhere in the FSM.
- `app/src/main/java/com/curro/app/assistant/IllegalAssistantTransition.kt` —
  exception type for the invalid-pair throw.
- `app/src/test/java/com/curro/app/assistant/AssistantStateMachineTest.kt` — 40+
  JVM tests covering every valid transition + every invalid pair.
- `app/src/test/java/com/curro/app/assistant/TestTimeProvider.kt` — a fake
  `TimeProvider` with a settable `nowMs` for tests (in the `test/` source set).
- No Hilt module needed — `@Singleton` + `@Inject constructor()` is enough
  (the `Clock` provider already exists in `di/TimeModule.kt`).

**Out of scope:**

- The coordinator (SF-5.2) — no STT/TTS/model/handler wiring lives here.
- The interrupt-by-button cancellation glue (SF-5.3) — the FSM only honours the
  `MicPressed` transition; the cancellation of in-flight Jobs lives in the
  coordinator.
- The consecutive-STT-failure counter (SF-5.4) — its **shape** (`failureCount`
  on `ErrorRecovery`) is here so SF-5.4 doesn't have to reshape the state; the
  counter itself lives in `SttFailureCounter` (SF-5.4).
- The 10-s `confirming` timeout enforcement — Phase 6 owns the timer; this SF
  only carries `expiresAtMs` on `Confirming` so Phase 6 doesn't refactor.
- The state-driven UI overlays — SF-5.5.
- The HOME-press hook into `MainActivity` — SF-5.6 (the FSM already accepts
  `HomePressed`, but the activity-level wiring is its SF).

---

## 3. User Flows

This SF has **no user-visible behaviour** on its own — it's a pure state
container. The flows below show how each Phase-5+ SF uses it. Each step
calls `stateMachine.transition(event)`; the FSM validates and returns the new
state.

### Flow 1: A normal turn (used by SF-5.2)

| # | Event sent | Pre-state | Post-state |
|---|---|---|---|
| 1 | `MicPressed(now)` | `Idle` | `Listening(partial = "", startedAtMs = now)` |
| 2 | `PartialTranscript("ll")` | `Listening` | `Listening(partial = "ll", …)` |
| 3 | `PartialTranscript("llama a pep")` | `Listening` | `Listening(partial = "llama a pep", …)` |
| 4 | `FinalTranscript("llama a Pepito", now)` | `Listening` | `Processing(transcript = "llama a Pepito", startedAtMs = now)` |
| 5 | `FunctionCallReady(needsConfirmation = false, speech = "Llamando a Pepito.", screen = null, …)` | `Processing` | `Executing(speech = "Llamando a Pepito.", screen = null)` |
| 6 | `ExecutionDone` | `Executing` | `Idle` |

### Flow 2: A `Confirming` turn (used by SF-5.2 + Phase 6)

| # | Event sent | Pre-state | Post-state |
|---|---|---|---|
| 1 | `MicPressed(now)` | `Idle` | `Listening(…)` |
| 2 | `FinalTranscript(…)` | `Listening` | `Processing(…)` |
| 3 | `FunctionCallReady(needsConfirmation = true, prompt = "¿Llamo a Pepe Martínez?", expiresAtMs = now + 10_000, pendingAction = …)` | `Processing` | `Confirming(prompt = …, expiresAtMs = …, pendingAction = …)` |
| 4a | `UserConfirmed(speech = "Vale, llamando.", screen = null)` | `Confirming` | `Executing(…)` |
| 4b | `UserRejected` | `Confirming` | `Idle` |
| 4c | `ConfirmationTimedOut` | `Confirming` | `Idle` |

### Flow 3: STT failure → recovery (used by SF-5.2 + SF-5.4)

| # | Event sent | Pre-state | Post-state |
|---|---|---|---|
| 1 | `MicPressed(now)` | `Idle` | `Listening(…)` |
| 2 | `SttFailed(message = "No te he oído bien, ¿puedes repetirlo?", failureCount = 1)` | `Listening` | `ErrorRecovery(message = …, failureCount = 1)` |
| 3 | `RecoverySpoken` | `ErrorRecovery` | `Idle` |

### Flow 4: Interrupt-by-button (used by SF-5.3)

`MicPressed(now)` is **valid in every state**. Every pre-state collapses to
`Listening(partial = "", startedAtMs = now)`. The FSM does **not** cancel
in-flight work — that is SF-5.3's job in the coordinator (`currentJob?.cancel()`
before `transition`). The FSM merely accepts the transition.

### Flow 5: HOME press (used by SF-5.6)

`HomePressed` is **valid in every state**. Every pre-state collapses to
`Idle`. Again, the coordinator-side cancellation (SF-5.6's
`MainActivity.onNewIntent`) calls `coordinator.onHomePressed()` which cancels
in-flight work **then** issues the FSM transition.

---

## 4. Function-catalog Impact

No catalog change. The FSM is plumbing.

---

## 5. FSM States Touched

**This SF defines all six.** The full state shape:

```kotlin
package com.curro.app.assistant

import com.curro.app.domain.handler.AssistantScreen
import com.curro.app.domain.handler.HandlerResult

sealed interface AssistantState {
    /** Launcher home is showing; nothing in flight. The only state at app start. */
    data object Idle : AssistantState

    /**
     * STT is active.
     *
     * @param partial the most recent partial transcript (`""` until the recogniser
     *   emits the first partial). Used by `ListeningOverlay` (SF-5.5).
     * @param startedAtMs epoch-ms when the mic was pressed (from `TimeProvider`).
     *   Used by SF-5.4's silence-cancel timer and by `ListeningOverlay` for the
     *   "still listening" affordance.
     */
    data class Listening(
        val partial: String,
        val startedAtMs: Long,
    ) : AssistantState

    /**
     * STT has emitted a final transcript; FunctionGemma + validator are running.
     *
     * @param transcript the final STT transcript that triggered processing.
     * @param startedAtMs epoch-ms when processing started (from `TimeProvider`).
     *   Drives `ProcessingOverlay` (SF-5.5) — currently a static "Un momento…",
     *   but a future SF may want to show "Tardo más de lo normal" past a
     *   threshold.
     */
    data class Processing(
        val transcript: String,
        val startedAtMs: Long,
    ) : AssistantState

    /**
     * The handler decided this action needs explicit user confirmation
     * (`HandlerResult.NeedsConfirmation` OR Phase 6's `ConfidencePolicy` returned
     * `Confirm`).
     *
     * @param prompt the Spanish line Curro speaks + shows (e.g. "¿Llamo a Pepe Martínez?").
     * @param expiresAtMs deadline (`TimeProvider.now() + 10_000` per spec §6 flow 2).
     *   **Phase 6 enforces the timeout via a coroutine timer; this SF carries
     *   the field so Phase 6 doesn't have to refactor the state.** Until Phase 6
     *   wires the timer, this field is informational (the coordinator's
     *   `onUserConfirmed/onUserRejected/ConfirmationTimedOut` events drive the
     *   transitions out).
     * @param pendingAction opaque container for the action to invoke on
     *   confirmation. Phase 5 only needs the function name + an `onConfirm`
     *   suspend block; Phase 6 may evolve this.
     */
    data class Confirming(
        val prompt: String,
        val expiresAtMs: Long,
        val pendingAction: PendingAction,
    ) : AssistantState

    /**
     * Curro is executing + speaking the outcome of a handler.
     *
     * @param speech the Spanish line TTS is speaking (and that the
     *   `ExecutingOverlay` shows — SF-5.5).
     * @param screen optional state-driven overlay payload — currently always
     *   `null` for Phase 4 handlers (per `HandlerResult.Spoken.screen`); Phase 5
     *   carries it through; Phase 6/7 fills `MessageCardsScreen` /
     *   `ContactPickerScreen` via `AssistantScreen` subclasses.
     */
    data class Executing(
        val speech: String,
        val screen: AssistantScreen?,
    ) : AssistantState

    /**
     * STT/decision/handler failure — Curro speaks a plain Spanish line + an
     * alternative (spec §2 "Fallar de forma comprensible").
     *
     * @param message the Spanish line being spoken + shown.
     * @param failureCount the value of `SttFailureCounter` *after* the
     *   incrementing failure (1, 2, or 3). SF-5.4 uses this to pick the right
     *   copy and to decide whether to give up. **Non-STT failures (decision
     *   layer / handler) pass `failureCount = 0`** so SF-5.4's counter is not
     *   touched.
     */
    data class ErrorRecovery(
        val message: String,
        val failureCount: Int,
    ) : AssistantState
}

/**
 * The action to invoke when the user confirms (`UserConfirmed` event). Phase 5
 * uses this only to carry the metadata; Phase 6 will likely refine this when it
 * adds the `ConfidencePolicy` decision.
 *
 * @param functionName the catalog snake_case name (used for telemetry only).
 * @param onConfirm suspending block that runs the irreversible part — typically
 *   re-dispatches the original `FunctionCall` (or wraps the
 *   `HandlerResult.NeedsConfirmation.onConfirm` lambda).
 */
data class PendingAction(
    val functionName: String,
    val onConfirm: suspend () -> HandlerResult,
)
```

The transition table the FSM enforces (this is what
`AssistantStateMachineTest` covers exhaustively):

| Event | Valid pre-states | Post-state |
|---|---|---|
| `MicPressed(ts)` | **All six** (interrupt rule) | `Listening(partial = "", startedAtMs = ts)` |
| `PartialTranscript(p)` | `Listening` only | `Listening(partial = p, …)` (preserves `startedAtMs`) |
| `FinalTranscript(t, ts)` | `Listening` only | `Processing(transcript = t, startedAtMs = ts)` |
| `SttFailed(m, c)` | `Listening` only | `ErrorRecovery(message = m, failureCount = c)` |
| `FunctionCallReady(nc, s, sc, p, e, pa)` | `Processing` only | If `nc == false` → `Executing(speech = s, screen = sc)`; if `nc == true` → `Confirming(prompt = p!!, expiresAtMs = e, pendingAction = pa!!)` |
| `UserConfirmed(s, sc)` | `Confirming` only | `Executing(speech = s, screen = sc)` |
| `UserRejected` | `Confirming` only | `Idle` |
| `ConfirmationTimedOut` | `Confirming` only | `Idle` |
| `ExecutionDone` | `Executing` or `ErrorRecovery` | `Idle` |
| `RecoverySpoken` | `Executing` or `ErrorRecovery` | `Idle` |
| `HomePressed` | **All six** (HOME reset rule) | `Idle` |

Every other `(state, event)` pair is invalid → `IllegalAssistantTransition`.

---

## 6. Android System Integrations & Permissions

No new integrations, no new permissions. Pure Kotlin + coroutines + Hilt.

---

## 7. On-device-model Impact

No model impact. The FSM is upstream of the model; the model is fed by SF-5.2's
coordinator.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/assistant/
├── AssistantState.kt
├── AssistantEvent.kt
├── AssistantStateMachine.kt
├── IllegalAssistantTransition.kt
├── PendingAction.kt              // (or co-located in AssistantState.kt — pin: top-level in AssistantState.kt for one-class-per-file rule, see below)
└── TimeProvider.kt
```

**One-class-per-file rule (CLAUDE.md "Coding Standards"):** one top-level
declaration per file. The brief pins:

- `AssistantState.kt` holds **only** the `sealed interface AssistantState` + its
  inner data-state types (these are part of the same sealed hierarchy — that's
  one declaration).
- `PendingAction.kt` is its own file.
- `AssistantEvent.kt` holds **only** the `sealed interface AssistantEvent` + its
  inner event types.
- `AssistantStateMachine.kt` holds **only** the class.
- `IllegalAssistantTransition.kt` holds **only** the exception class.
- `TimeProvider.kt` holds the interface **and** the production impl
  `SystemTimeProvider` — same pattern as `CallController.kt` /
  `IntentCallController.kt` in US-034 §8.2 (interface + the single production
  impl, co-located, no separate file for the impl).

### 8.2 `TimeProvider.kt`

```kotlin
package com.curro.app.assistant

import dagger.hilt.android.scopes.ActivityRetainedScoped
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single seam between the FSM (and the SF-5.2 coordinator) and the wall
 * clock. Production: `SystemTimeProvider` backed by `Clock.systemDefaultZone()`
 * (provided by `di/TimeModule.kt`). Tests: `TestTimeProvider` with a settable
 * `nowMs`, so deadline / timer assertions are deterministic.
 *
 * **DO NOT** call `System.currentTimeMillis()` or `SystemClock.elapsedRealtime()`
 * anywhere in the `assistant/` package — every "what time is it" goes through
 * this. (The Phase-3 `LauncherViewModel.decideAndSpeak` uses
 * `SystemClock.elapsedRealtime` for latency telemetry — that's outside the FSM
 * and stays where it is; the new code in the coordinator + FSM uses
 * `TimeProvider`.)
 */
interface TimeProvider {
    /** Epoch milliseconds (`Clock.millis()`). */
    fun now(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor(
    private val clock: Clock,
) : TimeProvider {
    override fun now(): Long = clock.millis()
}
```

Hilt binding — append to **`di/TimeModule.kt`** (or a new module — pin: append
to `TimeModule.kt`, it's the natural home):

```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class TimeProviderModule {
    @Binds @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
```

(`TimeModule.kt` is currently an `object` with `@Provides`; the `abstract
class` `@Binds` module is a sibling — either co-locate as a nested abstract
class inside the file or create `TimeProviderModule.kt`. **Pin: create
`TimeProviderModule.kt`** — keeps `TimeModule.kt`'s `object` shape intact.)

### 8.3 `AssistantEvent.kt`

```kotlin
package com.curro.app.assistant

import com.curro.app.domain.handler.AssistantScreen

/**
 * The 11 events the FSM understands. Sent by `AssistantCoordinator` (SF-5.2),
 * `MainActivity.onNewIntent` (SF-5.6), and tests. **Never sent by composables
 * directly** — composables send `LauncherEvent`s to the VM; the VM forwards to
 * the coordinator; the coordinator builds these.
 *
 * Timestamps are epoch-ms from `TimeProvider.now()`.
 */
sealed interface AssistantEvent {
    /** User tapped the mic button. Valid in every state (interrupt rule, SF-5.3). */
    data class MicPressed(val timestamp: Long) : AssistantEvent

    /** STT emitted a partial — only meaningful while `Listening`. */
    data class PartialTranscript(val partial: String) : AssistantEvent

    /** STT emitted a final — transitions to `Processing`. */
    data class FinalTranscript(val transcript: String, val timestamp: Long) : AssistantEvent

    /**
     * STT failed (no-match, timeout, recoverable error).
     *
     * @param message the Spanish line to speak + show (already chosen by SF-5.4
     *   from the COPY table based on [failureCount]).
     * @param failureCount the new counter value after the failure (1, 2, or 3).
     */
    data class SttFailed(val message: String, val failureCount: Int) : AssistantEvent

    /**
     * The decision pipeline finished. Either ready to execute (if
     * `needsConfirmation == false`) or to ask the user (if `true`).
     *
     * Invariants enforced by the FSM (failing them throws
     * [IllegalArgumentException] inside `computeNext`):
     *   - `needsConfirmation == true` ⇒ [prompt], [pendingAction] non-null.
     *   - `needsConfirmation == false` ⇒ [speech] non-null.
     */
    data class FunctionCallReady(
        val needsConfirmation: Boolean,
        val speech: String,
        val screen: AssistantScreen?,
        val prompt: String?,
        val expiresAtMs: Long,
        val pendingAction: PendingAction?,
    ) : AssistantEvent

    /** User pressed SÍ (or said "sí") in `Confirming`. */
    data class UserConfirmed(val speech: String, val screen: AssistantScreen?) : AssistantEvent

    /** User pressed NO (or said "no") in `Confirming`. */
    data object UserRejected : AssistantEvent

    /** 10-s silence in `Confirming` (spec §6 flow 2). Phase 6 fires this. */
    data object ConfirmationTimedOut : AssistantEvent

    /** `Executing`'s TTS+handler finished — go home. */
    data object ExecutionDone : AssistantEvent

    /** `ErrorRecovery`'s TTS finished — go home. */
    data object RecoverySpoken : AssistantEvent

    /** HOME button pressed (`MainActivity.onNewIntent`, SF-5.6). Valid in every state. */
    data object HomePressed : AssistantEvent
}
```

### 8.4 `AssistantStateMachine.kt`

```kotlin
package com.curro.app.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of `AssistantState` transitions. Exposes a read-only
 * `StateFlow<AssistantState>` and a single `transition(event)` entry point that
 * validates the `(current, event)` pair against the spec §6 diagram.
 *
 * Invalid transitions throw [IllegalAssistantTransition] — callers (coordinator,
 * `MainActivity`) must only send events valid for the current state. The Phase-5
 * coordinator achieves that by structure (each step in `runListenLoop` knows
 * which state it's transitioning from). Tests cover every invalid pair to make
 * sure the FSM rejects them.
 *
 * **There is no separate mutable-state instance.** The class is `@Singleton`;
 * the same instance is injected into the coordinator, the VM (read-only), and
 * `MainActivity`. The `StateFlow` is the truth.
 */
@Singleton
class AssistantStateMachine @Inject constructor() {

    private val mutableState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val state: StateFlow<AssistantState> = mutableState.asStateFlow()

    /**
     * Apply [event] and return the new state. Throws [IllegalAssistantTransition]
     * if the `(current, event)` pair is invalid per spec §6.
     *
     * Thread-safety: `MutableStateFlow.value =` is atomic. The FSM does not
     * cross-check that a thread-unsafe interleaving produced a stale read —
     * the coordinator's `currentJob` discipline (SF-5.2) means only one
     * sequence runs at a time per turn. Tests run on a single test
     * dispatcher.
     */
    fun transition(event: AssistantEvent): AssistantState {
        val current = mutableState.value
        val next = computeNext(current, event)
            ?: throw IllegalAssistantTransition(current, event)
        mutableState.value = next
        return next
    }

    private fun computeNext(current: AssistantState, event: AssistantEvent): AssistantState? =
        when (event) {
            is AssistantEvent.MicPressed -> AssistantState.Listening(
                partial = "",
                startedAtMs = event.timestamp,
            )
            is AssistantEvent.PartialTranscript -> when (current) {
                is AssistantState.Listening -> current.copy(partial = event.partial)
                else -> null
            }
            is AssistantEvent.FinalTranscript -> when (current) {
                is AssistantState.Listening -> AssistantState.Processing(
                    transcript = event.transcript,
                    startedAtMs = event.timestamp,
                )
                else -> null
            }
            is AssistantEvent.SttFailed -> when (current) {
                is AssistantState.Listening -> AssistantState.ErrorRecovery(
                    message = event.message,
                    failureCount = event.failureCount,
                )
                else -> null
            }
            is AssistantEvent.FunctionCallReady -> when (current) {
                is AssistantState.Processing -> if (event.needsConfirmation) {
                    val prompt = requireNotNull(event.prompt) {
                        "FunctionCallReady(needsConfirmation=true) requires a prompt"
                    }
                    val pendingAction = requireNotNull(event.pendingAction) {
                        "FunctionCallReady(needsConfirmation=true) requires a pendingAction"
                    }
                    AssistantState.Confirming(
                        prompt = prompt,
                        expiresAtMs = event.expiresAtMs,
                        pendingAction = pendingAction,
                    )
                } else {
                    AssistantState.Executing(
                        speech = event.speech,
                        screen = event.screen,
                    )
                }
                else -> null
            }
            is AssistantEvent.UserConfirmed -> when (current) {
                is AssistantState.Confirming -> AssistantState.Executing(
                    speech = event.speech,
                    screen = event.screen,
                )
                else -> null
            }
            AssistantEvent.UserRejected,
            AssistantEvent.ConfirmationTimedOut -> when (current) {
                is AssistantState.Confirming -> AssistantState.Idle
                else -> null
            }
            AssistantEvent.ExecutionDone,
            AssistantEvent.RecoverySpoken -> when (current) {
                is AssistantState.Executing,
                is AssistantState.ErrorRecovery -> AssistantState.Idle
                else -> null
            }
            AssistantEvent.HomePressed -> AssistantState.Idle
        }
}
```

### 8.5 `IllegalAssistantTransition.kt`

```kotlin
package com.curro.app.assistant

/**
 * Thrown when `AssistantStateMachine.transition(event)` is called with an
 * `(state, event)` pair the spec §6 diagram does not allow. This is **always**
 * a caller bug — the coordinator and the activity must only send events valid
 * for the current state.
 */
class IllegalAssistantTransition(
    val state: AssistantState,
    val event: AssistantEvent,
) : IllegalStateException("Invalid transition: $state + $event")
```

### 8.6 Hilt wiring

Nothing new. The FSM is `@Singleton class AssistantStateMachine @Inject
constructor()` — Hilt resolves it directly. `TimeProvider` gets the `@Binds`
module described in §8.2.

### 8.7 ViewModels and State Management

No ViewModel in this SF. The FSM is **read** by `LauncherViewModel` and
`AssistantCoordinator` (SF-5.2) and `MainActivity` (SF-5.6).

### 8.8 Composables by Feature

No composables. State-driven UI is SF-5.5.

### 8.9 Navigation Routes

No new routes. The FSM drives **overlays**, not navigation.

### 8.10 Material Design Components

None.

---

## 9. Acceptance Criteria

- [ ] **All six states** (`Idle`, `Listening`, `Processing`, `Confirming`,
  `Executing`, `ErrorRecovery`) defined as documented in §5 — with their data
  shapes verbatim, including the timestamp fields (`startedAtMs`,
  `expiresAtMs`) and the `failureCount`.
- [ ] **All eleven events** defined as documented in §8.3.
- [ ] `AssistantStateMachine` exposes `state: StateFlow<AssistantState>`
  (read-only); the underlying `MutableStateFlow` is private.
- [ ] `transition(event): AssistantState` is the **only** mutation entry
  point. No other public method mutates state. (Verifiable: a grep test in
  `AssistantStateMachineTest` parses the class and asserts no other
  non-private setters / mutating method names.)
- [ ] `MicPressed(ts)` transitions **every** pre-state to `Listening(partial =
  "", startedAtMs = ts)` — exhaustive unit-test coverage (6 cases).
- [ ] `HomePressed` transitions **every** pre-state to `Idle` — exhaustive
  unit-test coverage (6 cases).
- [ ] `PartialTranscript` is only valid in `Listening` — 1 valid + 5 invalid
  cases.
- [ ] `FinalTranscript` is only valid in `Listening` — 1 valid + 5 invalid
  cases.
- [ ] `SttFailed` is only valid in `Listening` — 1 valid + 5 invalid cases.
- [ ] `FunctionCallReady` is only valid in `Processing` — 1 valid (`nc = false`)
  + 1 valid (`nc = true`) + 5 invalid cases.
- [ ] `FunctionCallReady(needsConfirmation = true)` with `prompt = null` or
  `pendingAction = null` throws `IllegalArgumentException` (from
  `requireNotNull`).
- [ ] `UserConfirmed`, `UserRejected`, `ConfirmationTimedOut` are only valid
  in `Confirming` — 3 × (1 valid + 5 invalid) = 18 cases.
- [ ] `ExecutionDone`, `RecoverySpoken` are valid in `Executing` or
  `ErrorRecovery` — 2 × (2 valid + 4 invalid) = 12 cases.
- [ ] Every invalid `(state, event)` pair throws
  `IllegalAssistantTransition(state, event)` (the exception carries both, for
  test assertions).
- [ ] `TimeProvider` is the only external dependency of the FSM (the FSM itself
  doesn't inject it — it receives timestamps in events; the **coordinator** (SF-5.2)
  injects `TimeProvider` and constructs events with `timeProvider.now()`).
- [ ] No call to `System.currentTimeMillis()` or `SystemClock.elapsedRealtime()`
  anywhere in `app/src/main/java/com/curro/app/assistant/`. (Verifiable: ktlint
  custom rule or a simple regex test.)
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green.

---

## 10. Design Notes

- The FSM is **passive**. It does not start coroutines, schedule timers, call
  STT/TTS, or talk to handlers. SF-5.2 owns all that.
- The `data class` states' equality is structural — two `Listening("ll", 100)`
  are `==`. This matters for `StateFlow` distinct emission: a redundant
  `PartialTranscript("ll")` followed by another `PartialTranscript("ll")` will
  not re-emit (good — avoids needless Compose recomposition).
- Why `expiresAtMs` on `Confirming` if Phase 6 owns the timer? Because moving
  the field into `Confirming` in Phase 6 would force the SF-5.5
  `ConfirmationOverlay` (provisional in Phase 5, real in Phase 6) to be
  rewritten. Carrying the field now means Phase 6 only adds the timer
  coroutine and the `ConfirmationTimedOut` event emitter. Same reasoning for
  `failureCount` on `ErrorRecovery`.
- Why `failureCount = 0` for non-STT errors? SF-5.4's counter must only react
  to STT failures (spec §6 flow 6). A `Failed(HandlerCrash)` should not bump
  the STT counter. `0` is a sentinel "this didn't come from STT".
- Why is `MicPressed` valid in every state? **The interrupt-by-button rule
  (spec §6, voice-interaction rule 1)** — this is the load-bearing decision
  for Phase 5. Forgetting it is the #1 risk per the master plan §Phase 5
  Risks.
- Why is `HomePressed` valid in every state? **SF-5.6's HOME-press reset** —
  same architectural reason: it must be honoured everywhere, no exceptions.
- Why no `Cancelled` state? The spec §6 diagram has no such state — every
  cancellation ends in `Idle` (the user came home). Don't add states the spec
  doesn't have.

---

## 11. Senior-UX & Copy

No new copy in this SF — the FSM is pure plumbing. The Spanish lines referenced
by `AssistantState.message`, `Confirming.prompt`, and
`Executing.speech` are produced by SF-5.2/5.4 from the existing
`brand-design` COPY table (`copy_stt_fail_1/2/3`, `copy_processing`,
`copy_calling`, `copy_unknown_function`, …).

---

## 12. Performance Considerations

- `MutableStateFlow` is conflated — redundant identical states do not
  re-emit. Good for Compose.
- The FSM is allocation-light: each transition creates one `data class`
  (small). On the hot path (`PartialTranscript`) `current.copy(partial = …)`
  reuses the timestamp; no extra allocations.
- No coroutines, no I/O — every transition is synchronous and lock-free.

---

## 13. Testing Requirements

### 13.1 `AssistantStateMachineTest.kt` (JVM, JUnit 5)

Pinned target: **≥ 40 tests**. Structure:

- **Helper:** a `TestTimeProvider` in `test/` source set:
  ```kotlin
  class TestTimeProvider(var nowMs: Long = 0L) : TimeProvider {
      override fun now(): Long = nowMs
  }
  ```
  Plus a `fun newFsm(): AssistantStateMachine = AssistantStateMachine()` builder.

- **Group A — `MicPressed` is the interrupt rule (6 tests):**
  - `mic press from idle → Listening(partial="", startedAtMs=ts)`.
  - `mic press from Listening("hola", 100) → Listening("", new ts)`.
  - `mic press from Processing("hola", 100) → Listening("", new ts)`.
  - `mic press from Confirming → Listening("", new ts)`.
  - `mic press from Executing → Listening("", new ts)`.
  - `mic press from ErrorRecovery → Listening("", new ts)`.

- **Group B — `HomePressed` resets everywhere (6 tests):** same shape, each
  pre-state → `Idle`.

- **Group C — `PartialTranscript` (6 tests):** 1 valid (`Listening` →
  `Listening` with updated partial, **same `startedAtMs`**) + 5 invalid
  (`Idle/Processing/Confirming/Executing/ErrorRecovery` → throws).

- **Group D — `FinalTranscript` (6 tests):** 1 valid + 5 invalid.

- **Group E — `SttFailed` (6 tests):** 1 valid + 5 invalid.

- **Group F — `FunctionCallReady` (8 tests):**
  - From `Processing` with `nc = false` → `Executing(speech, screen)`.
  - From `Processing` with `nc = true, prompt, pendingAction` →
    `Confirming(prompt, expiresAtMs, pendingAction)`.
  - From `Processing` with `nc = true, prompt = null` → throws
    `IllegalArgumentException` ("requires a prompt").
  - From `Processing` with `nc = true, pendingAction = null` → throws
    `IllegalArgumentException` ("requires a pendingAction").
  - 5 invalid pre-states → `IllegalAssistantTransition`.

  > Note: the prompt/pendingAction-null-with-nc=true cases throw
  > `IllegalArgumentException` from `requireNotNull` — *not*
  > `IllegalAssistantTransition`. The state is valid; the event's invariants
  > aren't. Pin in tests with `assertThrows<IllegalArgumentException>`.

- **Group G — `UserConfirmed` (6 tests):** 1 valid + 5 invalid.

- **Group H — `UserRejected` (6 tests):** 1 valid + 5 invalid.

- **Group I — `ConfirmationTimedOut` (6 tests):** 1 valid + 5 invalid.

- **Group J — `ExecutionDone` (6 tests):** 2 valid (`Executing` → `Idle`,
  `ErrorRecovery` → `Idle`) + 4 invalid.

- **Group K — `RecoverySpoken` (6 tests):** 2 valid + 4 invalid.

- **Group L — `StateFlow` semantics (3 tests):**
  - Initial value is `Idle`.
  - `transition` updates `state.value` synchronously (assert before the next
    coroutine yield).
  - Redundant emission: applying `PartialTranscript("hola")` twice from
    `Listening("hola", 100)` results in only one `state` emission (use
    Turbine).

- **Group M — `IllegalAssistantTransition` payload (1 test):** asserts that the
  thrown exception's `state` and `event` properties match what was passed in
  (so test failures can show the offending pair).

**Total: 70+ cases.** (The prompt promised 40+; the exhaustive grid lands
higher because every event × non-listening-state invalid pair counts.) The brief
sets the **minimum bar at 40**; the implementer is welcome to merge equivalent
invalid-state groups into parameterised tests if 70 individual cases feel
excessive.

### 13.2 No `androidTest` for this SF

`assistant/` is pure Kotlin — JVM tests only. SF-5.2's coordinator gets the
first instrumented test in Phase 5 (SF-5.3's UI test).

---

## 14. Implementation Notes

**PM Owner wrote**: Metadata, Summary, Scope, User Flows, Function-catalog
Impact, Senior-UX & Copy, Acceptance Criteria, Design Notes.

**Architect / voice-pipeline-engineer fills in (during SF-5.2 / SF-5.3
implementation)**: Performance Considerations refinements if profiling
surfaces them; nothing else in this brief should change.

**Commit message (pinned)**:

```
feat: add AssistantStateMachine + AssistantState FSM (US-035 / SF-5.1)

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft — pinned every detail. |
