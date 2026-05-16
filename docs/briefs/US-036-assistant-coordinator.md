# US-036 — SF-5.2 · `AssistantCoordinator` (rewire the pipeline through the FSM)

> **Spec trace:** spec §4 (the five-layer pipeline — this SF makes the coordinator
> own layers 1–6 end-to-end), spec §6 (every interaction state lives here),
> spec §4.6 ("toda comunicación de la app al usuario va por TTS, además de
> mostrarse en pantalla como apoyo visual"). The coordinator is the
> *one* class that knows the layered ordering.
> **Master-plan:** SF-5.2.
> **Phase:** 5 — State machine & interruption.
> **Depends on:** US-035 (`AssistantStateMachine`), US-024 (`FunctionCallEngine`
> + the smoke loop this SF replaces), US-025 (`HandlerDispatcher` +
> `HandlerResult`), US-015 (`SttClient`), US-016 (`TtsClient`), US-034 (the
> permission-side-effect glue, which moves out of the VM into here).
> **Size:** M.
> **Skills:** `voice-interaction` (the FSM is the spine; rule 1 — interrupt;
> rule 7 — overlays are state-driven), `on-device-llm` (the decision pipeline
> contract), `platform-integrations` (permission-gate result handling),
> `compose-patterns` (the VM thinning), `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `AssistantCoordinator` — the only place that wires capture → STT → FunctionGemma → handler → TTS through the FSM |
| **US ID** | US-036 |
| **Phase** | 5 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | voice-pipeline-engineer |

---

## 1. Summary

Replace the ad-hoc `LauncherViewModel.processMicResult` / `decideAndSpeak` /
`render` glue (introduced in US-024 / SF-3.6 and grown by every Phase-4 handler)
with a single `AssistantCoordinator` that drives the spec §4 pipeline through
the SF-5.1 FSM:

- `onMicPressed()` → cancel any in-flight job → `MicPressed` → start the listen
  loop (STT collects on the coordinator scope, **not** the VM scope) → on
  `FinalTranscript` → run FunctionGemma + validator → emit `FunctionCallReady`
  → on `NeedsConfirmation` go to `Confirming`, otherwise `Executing` → run the
  handler → speak → `ExecutionDone`.
- `onHomePressed()` → cancel any in-flight job + stop TTS → `HomePressed`.
  (Used by SF-5.6 from `MainActivity.onNewIntent`.)
- `onUserConfirmed()` / `onUserRejected()` → drive the `Confirming` transitions
  (the 10-s timeout is Phase 6's `ConfidencePolicy` — this SF just routes the
  events).

The coordinator is `@Singleton`. `LauncherViewModel` becomes a thin observer
that exposes `coordinator.state` to the UI and forwards `LauncherEvent.MicPressed`
to `coordinator.onMicPressed()`. The 18 functions + `@Suppress("TooManyFunctions")`
on the VM collapse to ≤ 8 functions.

Why this matters for *this* user: the Phase-2 → Phase-4 stack has six pieces of
state that all encode "is Curro talking right now?" (`ListeningState.Idle/...`,
`voiceJob?.isActive`, `lastFunctionCall != null`, the per-turn auto-retry
flags). Phase-5 is the first time anything cares — interrupt-by-button and HOME-press
both need *one* answer to "what is Curro doing?", and the FSM is that answer.
The coordinator is the only place that's allowed to know.

**Crucial pin (the architectural enforcement of SF-5.3 starts here)**: `onMicPressed`
**always** does `currentJob?.cancel(); ttsClient.stop(); sttClient.cancel()`
**before** issuing the `MicPressed` transition. There is **no condition** under
which a mic press leaves an in-flight job alive. SF-5.3 is mostly tests against
this rule — but the rule lives here.

---

## 2. Scope

**In scope:**

- `app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt` — the
  `@Singleton` orchestrator.
- `app/src/main/java/com/curro/app/assistant/AssistantSideEffect.kt` — a
  `sealed interface` for one-shot UI side effects emitted by the coordinator
  (permission requests, intent launches, the OpenConfig signal, etc. — see
  §6.3).
- **VM thinning** of
  `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt`:
  - Inject `AssistantCoordinator`. Stop injecting `SttClient`, `TtsClient`,
    `FunctionCallEngine`, `FunctionCallValidator`, `HandlerDispatcher`,
    `TelemetrySink`, `@ApplicationContext Context`, `ReadContactsPermissionGate`,
    `CallPhonePermissionGate`, and `PermissionGate`. **Keep**: `DefaultLauncherDetector`,
    `ObserveClockUseCase`, `FavoriteAppsRepository`, `NotificationAccessGate`.
  - Replace `private val listeningStateFlow = MutableStateFlow<ListeningState>(…)`
    with `private val assistantStateFlow = coordinator.state` (read directly).
  - `uiState: StateFlow<LauncherUiState>` is rebuilt with `combine(detector.flow,
    observeClock(), favoritesRepo.observeFavorites(), coordinator.state,
    notifGrantedFlow) { … }` and a new field `assistantState: AssistantState`
    on `LauncherUiState`. **Remove `listeningState: ListeningState`** from
    `LauncherUiState`. SF-5.5 keys overlays off `assistantState` instead.
  - Replace `private fun onMicPressed()` with `coordinator.onMicPressed()`.
    Drop `startListening`, `handleSttEvent`, `decideAndSpeak`,
    `handleDecisionSuccess`, `handleDecisionFailure`, `render`,
    `speakAndIdle`, `handleSttFailure`, `showTransientError`, `errorMessage`,
    `emitDecideEvent`, `buildContext`, `prettyPrint`, `jsonValue`,
    `tryRequestCallContactPermission`, `onReadContactsPermissionResult`,
    `onCallPhonePermissionResult`, `onPermissionResult`. All seventeen of
    these move to / are replaced by methods on the coordinator.
  - Keep: `onClockTapped`, `onAppTileTapped`, `onGrantNotifAccessRequested`,
    the `lifecycleSource` seam + `resumeObserver` for the notification-access
    gate re-check on `ON_RESUME`, `init {}`, `onCleared`. These are launcher
    concerns, not assistant concerns.
  - The post-refactor VM must have **≤ 8 functions** total (currently 18 with
    `@Suppress("TooManyFunctions")`). The suppress can be removed once the
    refactor lands.
- **`LauncherUiState`** gets `val assistantState: AssistantState` and **loses**
  `val listeningState: ListeningState`.
- **`LauncherEvent`** — keep `MicPressed`, `AppTileTapped`, `ClockTapped`,
  `GrantNotifAccessRequested`, `RecordAudioPermissionResult`,
  `ReadContactsPermissionResult`, `CallPhonePermissionResult`. The permission-result
  events still land on the VM (the `ActivityResultLauncher` is owned by the
  composable, which sees the side effect from the coordinator and fires the
  launcher — pinned in §6.3), but they are **forwarded** to
  `coordinator.onPermissionResult(...)`.
- **`LauncherSideEffect`** survives — the screen's `LaunchedEffect` consumes it
  the same way. The coordinator's `AssistantSideEffect` is **adapted to**
  `LauncherSideEffect` inside the VM (pinned in §6.3). This keeps the screen
  contract stable; only the VM-internal wiring changes.
- **The provisional `ListeningState` sealed interface** (file
  `presentation/launcher/ListeningState.kt`) — **delete** in this SF (it has
  no callers after the VM refactor). Its data has moved to `AssistantState`.
  Replace the Phase-2 `ListeningOverlay` reads of `ListeningState.Listening`
  etc. with reads of `AssistantState.Listening` (SF-5.5 fully takes that
  over; this SF just makes the source-set compile by adapting the existing
  overlay calls — the *visual refactor* into per-state overlays is SF-5.5).
- **Tests:**
  - **New:** `app/src/test/java/com/curro/app/assistant/AssistantCoordinatorTest.kt`
    — JVM, ≥ 20 cases (full happy paths per Phase-4 handler + every failure
    branch + each `Confirming` outcome). Uses fakes for `SttClient`, `TtsClient`,
    `FunctionCallEngine`, `FunctionCallValidator` (real), `HandlerDispatcher`
    (with fakes for handlers — or a real dispatcher with fake handlers; pin:
    real dispatcher + fake handlers, see §13.1).
  - **Updated:**
    `app/src/test/java/com/curro/app/presentation/launcher/LauncherViewModelTest.kt`
    — delete every test asserting on the old `ListeningState` shape (every
    "Listening → Speaking → Idle" race-condition test, every "STT failure
    surfaces in `Error` state" test, every "auto-retry on permission grant"
    test — these all move to `AssistantCoordinatorTest`). Keep: the 5-tap
    config-gesture tests, the app-tile-tap test, the notification-access
    ON_RESUME re-check test, the favourites + clock observation tests.
    Pinned deletion list in §13.2.
  - **No instrumented test changes here** — SF-5.3 adds the first new
    instrumented test in Phase 5.

**Out of scope:**

- The actual cancellation of in-flight TTS + STT — **the calls are wired
  here** (`currentJob?.cancel()`, `ttsClient.stop()`, `sttClient.cancel()`),
  but the *test* coverage that proves they fire from every state belongs to
  SF-5.3. SF-5.3 also adds the 150-ms-TTS-stop instrumented test.
- The consecutive-STT-failure counter (SF-5.4). For this SF, hardcode
  `failureCount = 1` for every STT failure (matching the Phase-2 behaviour
  where any STT failure → `copy_stt_fail_1`). SF-5.4 plugs in the real counter.
- The state-driven UI overlays as separate composables (SF-5.5). The existing
  Phase-2 `ListeningOverlay` rendering still works — keyed off the new
  `assistantState` flow — until SF-5.5 splits it cleanly per-state. Pin: in
  this SF, replace `if (uiState.listeningState !is ListeningState.Idle)` with
  `if (uiState.assistantState !is AssistantState.Idle)` and **delete
  `ListeningState.kt`**; the overlay's branching on
  `Starting/Listening/Speaking/Processing/Error` gets a sister `when` over
  `AssistantState`. Cosmetically identical; structurally the source of truth
  is now the FSM.
- The 10-s `Confirming` timeout (Phase 6).
- The `ConfidencePolicy` decision (Phase 6). This SF passes the handler's
  `NeedsConfirmation` straight through: if the handler returns
  `NeedsConfirmation`, emit `FunctionCallReady(needsConfirmation = true, …)`;
  otherwise `false`. Phase 4's `LauncherViewModel.render` auto-invoked
  `onConfirm` recursively — that auto-confirm **stays for Phase 5** (Phase 6
  inserts the policy gate) so the Phase-4 handler behaviour is unchanged
  end-to-end.
- The HOME-press handling in `MainActivity` (SF-5.6 — that SF wires
  `onNewIntent` into `coordinator.onHomePressed()`).

---

## 3. User Flows

### Flow 1: `tell_time` happy path (the smallest verification)

| # | Caller | Coordinator step | FSM event | Post-state |
|---|---|---|---|---|
| 1 | VM: `coordinator.onMicPressed()` | `currentJob?.cancel(); ttsClient.stop(); sttClient.cancel(); transition(MicPressed(now))`. Launch `runListenLoop()` on the coordinator scope. | `MicPressed(now)` | `Listening("", now)` |
| 2 | STT `Event.Partial("qué")` | `transition(PartialTranscript("qué"))` | `PartialTranscript("qué")` | `Listening("qué", …)` |
| 3 | STT `Event.Partial("qué hora es")` | `transition(PartialTranscript("qué hora es"))` | `PartialTranscript("qué hora es")` | `Listening("qué hora es", …)` |
| 4 | STT `Event.Final("qué hora es")` | `transition(FinalTranscript("qué hora es", now))` | `FinalTranscript(…)` | `Processing("qué hora es", now)` |
| 5 | Decision pipeline | `engine.decide → validator.parseAndValidate → call: FunctionCall("tell_time", {}, 0.95)`. `dispatcher.dispatch(call) → Spoken("Son las trece y cuarenta y siete.", null)`. `transition(FunctionCallReady(nc=false, speech="Son las…", screen=null, …))`. | `FunctionCallReady(nc=false, …)` | `Executing("Son las…", null)` |
| 6 | TTS | `ttsClient.speak("Son las…")` (suspending). On return, `transition(ExecutionDone)`. | `ExecutionDone` | `Idle` |

### Flow 2: `call_contact` with `READ_CONTACTS` not granted (the permission auto-retry)

| # | Step | FSM | Notes |
|---|---|---|---|
| 1–4 | (as Flow 1, transcript = "llama a Pepito") | `Idle → Listening → Processing` | |
| 5 | `dispatcher.dispatch(call) → Failed(copy_perm_missing_contacts, ReadContactsPermissionMissing)`. Coordinator detects: `call.action == "call_contact"` and `result.reason is ReadContactsPermissionMissing` and `!readContactsAutoRetried` → emit side effect `AssistantSideEffect.RequestPermission(READ_CONTACTS)`. Save `pendingFunctionCall = call`. Mark `readContactsAutoRetried = true`. **Do NOT speak yet.** Stay in `Processing` (FSM unchanged — the side effect is async). | `Processing` | The VM forwards `AssistantSideEffect.RequestPermission` as `LauncherSideEffect.RequestReadContacts` — the screen fires its `ActivityResultLauncher`. |
| 6 | User grants. `LauncherEvent.ReadContactsPermissionResult(granted=true)` → VM → `coordinator.onPermissionResult(READ_CONTACTS, true)`. Coordinator re-dispatches `pendingFunctionCall`. New result `Spoken("Llamando a Pepito.")`. `transition(FunctionCallReady(nc=false, speech="Llamando…"))`. | `Processing → Executing` | |
| 7 | TTS speaks. `transition(ExecutionDone)`. `pendingFunctionCall = null; readContactsAutoRetried = false`. | `Executing → Idle` | |

**Variant 6': user denies.** `coordinator.onPermissionResult(READ_CONTACTS, false)` → coordinator transitions `FunctionCallReady(nc=false, speech=copy_perm_missing_contacts, screen=null, …)` → `Executing` → TTS → `Idle`. The "the failure is comprehensible" rule of spec §2.

### Flow 3: STT no-match

| # | Step | FSM event | Post-state |
|---|---|---|---|
| 1 | `onMicPressed()` | `MicPressed` | `Listening` |
| 2 | STT `Event.Failed(CurroError.SttNoMatch)` | `SttFailed(message = copy_stt_fail_1, failureCount = 1)` (hardcoded 1; SF-5.4 plugs in the real counter) | `ErrorRecovery("No te he oído bien, ¿puedes repetirlo?", 1)` |
| 3 | `ttsClient.speak(message)`; on return → `transition(RecoverySpoken)` | `RecoverySpoken` | `Idle` |

### Flow 4: Interrupt-by-button (the architectural test)

| # | Step | Notes |
|---|---|---|
| 1 | Curro is in `Executing("Tienes 3 mensajes de Pepito…", null)`, TTS speaking a long string. | |
| 2 | User taps the mic. `coordinator.onMicPressed()` is called. | |
| 3 | `currentJob?.cancel()` — cancels the suspended TTS coroutine. `ttsClient.stop()` — Android-side stops playback (per US-017's verified `TextToSpeech.stop()`). `sttClient.cancel()` — no-op (no active session). | |
| 4 | `transition(MicPressed(now))` → `Listening("", now)`. `currentJob = scope.launch { runListenLoop() }`. | The interrupt path is **structurally** the same as the cold mic-press path. That is the whole point of SF-5.3 — there is no second code path for "interrupt"; mic press is mic press. |

### Flow 5: HOME press (the FSM end-of-turn)

Used by SF-5.6.

| # | Step | FSM event | Post-state |
|---|---|---|---|
| 1 | User in any non-`Idle` state. | | |
| 2 | `MainActivity.onNewIntent(homeIntent)` → `coordinator.onHomePressed()` | | |
| 3 | `currentJob?.cancel(); ttsClient.stop(); sttClient.cancel()` | | |
| 4 | `transition(HomePressed)` | `HomePressed` | `Idle` |

### Flow 6: `NeedsConfirmation` from the handler (Phase 6's seed)

| # | Step | FSM event | Post-state |
|---|---|---|---|
| 1–4 | (transcript = "envía a Pepito que voy") (hypothetical Phase-2 catalog entry; using the existing `call_contact` is fine since its catalog says `conditional`, but in **Phase 5** the auto-confirm short-circuits — pin: the auto-confirm REMAINS for Phase 5 to keep Phase-4 behaviour identical) | | |
| 2 | `dispatcher.dispatch(call) → NeedsConfirmation(prompt = "¿Mando este mensaje a Pepito?", onConfirm = …)` | Phase-5 behaviour: invoke `onConfirm` immediately, recurse on the result. **Do NOT transition to `Confirming`.** Pin in `runListenLoop`. | |
| 3 | (Recursed result `Spoken(...)` → `FunctionCallReady(nc=false, …)` → `Executing` → `Idle`.) | | |

> Phase 6's `ConfidencePolicy` replaces the recursion with a real
> `FunctionCallReady(nc=true, prompt, pendingAction)` emission → `Confirming`.
> The coordinator's `onUserConfirmed/onUserRejected` already exist (this SF
> wires them, but they're unreachable from the Phase-5 happy path). Phase 6
> just flips the recursion off and the `Confirming` path on. **Pinned in
> §10.**

---

## 4. Function-catalog Impact

No catalog change. This SF is plumbing.

---

## 5. FSM States Touched

**All six.** This SF is the one that exercises the FSM end-to-end. Specifically:

- `Idle → Listening`: `onMicPressed()`.
- `Listening → Listening` (partials): `PartialTranscript`.
- `Listening → Processing`: `FinalTranscript`.
- `Listening → ErrorRecovery`: `SttFailed`.
- `Processing → Executing`: `FunctionCallReady(nc = false)`.
- `Processing → Confirming`: `FunctionCallReady(nc = true)` (Phase 6 happy
  path; **Phase 5 never emits this** because of the auto-confirm short-circuit
  noted in Flow 6).
- `Confirming → Executing`: `UserConfirmed` (Phase-6 path; wired but unreachable
  in Phase 5).
- `Confirming → Idle`: `UserRejected` / `ConfirmationTimedOut` (same).
- `Executing → Idle`: `ExecutionDone` (always — after TTS).
- `ErrorRecovery → Idle`: `RecoverySpoken` (after TTS).
- `* → Listening`: `MicPressed` interrupt.
- `* → Idle`: `HomePressed`.

---

## 6. Android System Integrations & Permissions

No new integrations. **Permission requests** move out of `LauncherViewModel`
into the coordinator's `AssistantSideEffect.RequestPermission(...)` emission —
but the actual `ActivityResultLauncher` registration **stays in the composable**
(`LauncherPlaceholderScreen.kt`), because only an `Activity` can launch a
permission request. The wiring:

```
Composable: rememberLauncherForActivityResult(RequestPermission()) { granted ->
    viewModel.onEvent(LauncherEvent.ReadContactsPermissionResult(granted))
}

VM.onEvent(LauncherEvent.ReadContactsPermissionResult(granted)) -> coordinator.onPermissionResult(READ_CONTACTS, granted)
```

The coordinator emits `AssistantSideEffect.RequestPermission(perm)`; the VM
adapts to `LauncherSideEffect.RequestReadContacts` (or `RequestCallPhone`,
or `RequestRecordAudio`); the composable's existing `LaunchedEffect`-on-
`sideEffects` flow consumes it and fires the right `ActivityResultLauncher`.

### 6.1 Coordinator constructor injection

```kotlin
@Singleton
class AssistantCoordinator @Inject constructor(
    private val stateMachine: AssistantStateMachine,
    private val sttClient: SttClient,
    private val ttsClient: TtsClient,
    private val engine: FunctionCallEngine,
    private val validator: FunctionCallValidator,
    private val dispatcher: HandlerDispatcher,
    private val timeProvider: TimeProvider,
    private val telemetry: TelemetrySink,
    private val recordAudioGate: PermissionGate,                       // existing (US-017)
    private val readContactsGate: ReadContactsPermissionGate,          // existing (US-033)
    private val callPhoneGate: CallPhonePermissionGate,                // existing (US-034)
    @ApplicationContext private val appContext: Context,               // for `getString(copyId)`
    @ApplicationScope private val scope: CoroutineScope,               // existing (`di/CoroutineModule.kt`)
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,   // existing
)
```

Every collaborator is existing — this SF adds **zero** new modules. The
`@ApplicationScope` is `SupervisorJob() + Main.immediate` per `CoroutineModule.kt`
— a single `scope.launch { runListenLoop() }` per turn.

### 6.2 Threading

- `currentJob` is a `private var Job? = null`. Mutation is from the
  coordinator's public entry points (`onMicPressed`, `onHomePressed`,
  `onUserConfirmed`, `onUserRejected`, `onPermissionResult`). These are
  called from the **main thread** (the VM forwards from
  `viewModelScope.launch { … }` on Main.immediate, the activity calls from
  the main thread). **Pin**: every public entry point on the coordinator
  starts with a `withContext(mainDispatcher)` guard if not already on Main.
  Easiest pattern:

  ```kotlin
  fun onMicPressed() {
      scope.launch {                // ApplicationScope runs on Main.immediate
          currentJob?.cancel()
          ttsClient.stop()           // safe on Main
          sttClient.cancel()         // safe on Main
          stateMachine.transition(AssistantEvent.MicPressed(timeProvider.now()))
          currentJob = scope.launch { runListenLoop() }
      }
  }
  ```

  This means **mic-press from the VM** lands on the same dispatcher as the
  in-flight TTS-await — `cancel()` is observed deterministically. Pin in the
  brief: do not change `@ApplicationScope` from `Main.immediate` to any other
  dispatcher (it would race the cancel).

- Heavy work (model inference, STT/TTS audio loops, Room/file I/O) is already
  on `@IoDispatcher` inside each collaborator (`FunctionGemmaEngine` uses
  `@IoDispatcher`, `SystemSttClient` is on the recogniser's own thread, etc.).
  The coordinator's `scope` running on Main is **only** the orchestration
  surface — `engine.decide(...)` itself jumps to IO inside the engine.

### 6.3 `AssistantSideEffect`

```kotlin
package com.curro.app.assistant

sealed interface AssistantSideEffect {
    /**
     * Coordinator asks the UI to request a runtime permission. The VM adapts
     * this to the matching `LauncherSideEffect.Request*` and the composable's
     * `ActivityResultLauncher` fires it.
     *
     * @param permission Android manifest constant (e.g.
     *   `Manifest.permission.READ_CONTACTS`).
     */
    data class RequestPermission(val permission: String) : AssistantSideEffect

    /**
     * Coordinator asks the UI to open the notification-access settings
     * (HyperOS deep link, fired by SF-4.6's CTA path). Currently the VM owns
     * this — pinned: stays in the VM (it's a launcher concern, not an
     * assistant concern). **Not emitted by the coordinator in Phase 5.**
     */
    data object OpenNotificationAccessSettings : AssistantSideEffect

    /**
     * Debug-only: surface the parsed `FunctionCall` JSON to the listening
     * overlay (US-024). Coordinator emits this in `BuildConfig.DEBUG`. The
     * VM adapts to `LauncherSideEffect.ShowDebugJson`.
     */
    data class ShowDebugJson(val prettyJson: String) : AssistantSideEffect
}
```

### 6.4 Permissions table

No new permissions. The runtime-request *trigger* moves from the VM to the
coordinator; the manifest entries and the `ActivityResultLauncher`s stay.

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `RECORD_AUDIO` | STT in `runListenLoop`. | Coordinator's `onMicPressed`, if `!recordAudioGate.isGranted()` → emit `RequestPermission(RECORD_AUDIO)` and abort the listen-start. (Identical to current `LauncherViewModel` behaviour, just moved.) | Coordinator transitions `Listening → ErrorRecovery(copy_perm_missing_mic, 0)` → speak → `Idle`. |
| `READ_CONTACTS` | `call_contact` handler. | First time the handler returns `Failed(ReadContactsPermissionMissing)` AND `!readContactsAutoRetried`. | After auto-retry exhausted: speak `copy_perm_missing_contacts`. |
| `CALL_PHONE` | `call_contact` handler. | First time the handler returns `Failed(PermissionDenied)` AND `!callPhoneAutoRetried`. | After auto-retry exhausted: speak `copy_perm_missing_calls`. |

---

## 7. On-device-model Impact

No prompt change. No model contract change. The coordinator is **the** caller
of `engine.decide(...)` and `validator.parseAndValidate(...)` — moved out of
the VM. The `PromptContext` builder (currently
`LauncherViewModel.buildContext`) moves into a small helper in the coordinator:

```kotlin
private fun buildContext(): PromptContext = PromptContext(
    nowIso = LocalDateTime.now(timeProvider.clock).withNano(0).toString(),  // see below
    unreadMessagesSummary = "",
    knownAliases = emptyList(),
)
```

`TimeProvider.clock` doesn't exist — pin: add a `val clock: Clock` property to
`SystemTimeProvider` exposing the injected clock, OR inject `Clock` directly
into the coordinator (it's `@Singleton`, the binding exists). **Pin: inject
`Clock` directly** — keeps `TimeProvider` minimal (one method) and reuses the
existing binding from `TimeModule`. Two timestamps from two sources is fine —
`timeProvider.now()` for FSM events (epoch ms), `clock` for `LocalDateTime` for
the prompt.

The `model_decide` telemetry event still fires from the coordinator (with the
same shape: `model`, `outcome`, `latency_ms`).

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/assistant/
├── AssistantCoordinator.kt          // NEW — this SF
└── AssistantSideEffect.kt           // NEW — this SF
```

### 8.2 Files modified

```
app/src/main/java/com/curro/app/presentation/launcher/
├── LauncherViewModel.kt              // thinned ≤ 8 functions, drops 10 injections
├── LauncherPlaceholderScreen.kt      // overlay branching switches from ListeningState to AssistantState
```

### 8.3 Files deleted

```
app/src/main/java/com/curro/app/presentation/launcher/
└── ListeningState.kt                 // DELETED — no callers after refactor; SF-5.5 builds the per-state overlays
```

### 8.4 `AssistantCoordinator.kt` — full sketch

```kotlin
package com.curro.app.assistant

import android.Manifest
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.curro.app.BuildConfig
import com.curro.app.R
import com.curro.app.data.ml.FunctionCallValidator
import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.PermissionGate
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.di.ApplicationScope
import com.curro.app.di.MainDispatcher
import com.curro.app.domain.handler.HandlerDispatcher
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.PromptContext
import com.curro.app.domain.repository.FunctionCallEngine
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TtsClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("LongParameterList") // orthogonal collaborators; merging adds indirection
class AssistantCoordinator @Inject constructor(
    private val stateMachine: AssistantStateMachine,
    private val sttClient: SttClient,
    private val ttsClient: TtsClient,
    private val engine: FunctionCallEngine,
    private val validator: FunctionCallValidator,
    private val dispatcher: HandlerDispatcher,
    private val timeProvider: TimeProvider,
    private val telemetry: TelemetrySink,
    private val recordAudioGate: PermissionGate,
    private val readContactsGate: ReadContactsPermissionGate,
    private val callPhoneGate: CallPhonePermissionGate,
    private val clock: Clock,
    @ApplicationContext private val appContext: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {
    val state: StateFlow<AssistantState> = stateMachine.state

    private val mutableSideEffects =
        MutableSharedFlow<AssistantSideEffect>(replay = 0, extraBufferCapacity = 8)
    val sideEffects: SharedFlow<AssistantSideEffect> = mutableSideEffects

    /** The active turn's Job. Cancelled-and-replaced by every entry-point. */
    private var currentJob: Job? = null

    /** Per-turn permission-retry flags. Reset on every fresh `onMicPressed`. */
    private var readContactsAutoRetried = false
    private var callPhoneAutoRetried = false

    /** The last `FunctionCall` the validator produced, retained for permission auto-retry. */
    private var pendingFunctionCall: FunctionCall? = null
    private var pendingTranscript: String = ""

    // ─────────────────────────────── public API ───────────────────────────────

    fun onMicPressed() {
        scope.launch {
            currentJob?.cancel()
            ttsClient.stop()
            sttClient.cancel()
            // Fresh turn — reset retry bookkeeping.
            readContactsAutoRetried = false
            callPhoneAutoRetried = false
            pendingFunctionCall = null
            pendingTranscript = ""
            // The interrupt rule (SF-5.3): MicPressed is valid in every state.
            stateMachine.transition(AssistantEvent.MicPressed(timeProvider.now()))
            // RECORD_AUDIO gate: if denied, emit the request and abort.
            if (!recordAudioGate.isGranted()) {
                mutableSideEffects.emit(
                    AssistantSideEffect.RequestPermission(Manifest.permission.RECORD_AUDIO),
                )
                return@launch
            }
            currentJob = scope.launch { runListenLoop() }
        }
    }

    fun onHomePressed() {
        scope.launch {
            currentJob?.cancel()
            ttsClient.stop()
            sttClient.cancel()
            stateMachine.transition(AssistantEvent.HomePressed)
        }
    }

    /** SF-6.x. Wired here, unreachable in Phase 5 (the auto-confirm short-circuits). */
    fun onUserConfirmed() { /* Phase 6 fills the body */ }
    fun onUserRejected() { /* Phase 6 fills the body */ }

    fun onPermissionResult(permission: String, granted: Boolean) {
        scope.launch {
            when (permission) {
                Manifest.permission.RECORD_AUDIO -> handleRecordAudioResult(granted)
                Manifest.permission.READ_CONTACTS -> handleReadContactsResult(granted)
                Manifest.permission.CALL_PHONE -> handleCallPhoneResult(granted)
            }
        }
    }

    // ───────────────────────────── inner machinery ────────────────────────────

    private suspend fun runListenLoop() {
        // The exact `sttClient.listen().collect { … }` shape we had in
        // LauncherViewModel.startListening, but now collecting on the
        // coordinator scope, not the VM scope. On Final → decide & dispatch.
        // Cancellation is naturally propagated by the parent Job.
        sttClient.listen().collectLatest { event ->
            when (event) {
                is SttClient.Event.Partial ->
                    stateMachine.transition(AssistantEvent.PartialTranscript(event.text))
                is SttClient.Event.Final ->
                    onFinalTranscript(event.text)
                is SttClient.Event.Failed ->
                    onSttFailed(event.error)
            }
        }
    }

    private suspend fun onFinalTranscript(text: String) {
        stateMachine.transition(AssistantEvent.FinalTranscript(text, timeProvider.now()))
        decideAndDispatch(text)
    }

    private suspend fun decideAndDispatch(transcript: String) {
        val started = SystemClock.elapsedRealtime()
        val decision = engine.decide(transcript, buildContext())
        val parsed: Result<FunctionCall> = decision.fold(
            onSuccess = { validator.parseAndValidate(it) },
            onFailure = { Result.failure(it) },
        )
        val latencyMs = (SystemClock.elapsedRealtime() - started).toInt()
        parsed.fold(
            onSuccess = { call -> onDecisionSuccess(call, latencyMs, transcript) },
            onFailure = { err -> onDecisionFailure(err, latencyMs, transcript) },
        )
    }

    private suspend fun onDecisionSuccess(call: FunctionCall, latencyMs: Int, transcript: String) {
        emitDecideTelemetry(outcome = "success", latencyMs = latencyMs)
        if (BuildConfig.DEBUG) {
            mutableSideEffects.emit(AssistantSideEffect.ShowDebugJson(prettyPrint(call)))
        }
        pendingFunctionCall = call
        pendingTranscript = transcript
        val result = dispatcher.dispatch(call)
        renderHandlerResult(result, call)
    }

    /**
     * Phase 5 keeps the Phase-4 auto-confirm behaviour for `NeedsConfirmation`:
     * recurse into `onConfirm()` immediately. Phase 6 replaces this with a real
     * `FunctionCallReady(nc=true, ...)` emission → `Confirming`.
     */
    private suspend fun renderHandlerResult(result: HandlerResult, call: FunctionCall) {
        when (result) {
            is HandlerResult.Spoken -> {
                stateMachine.transition(
                    AssistantEvent.FunctionCallReady(
                        needsConfirmation = false,
                        speech = result.speech,
                        screen = result.screen,
                        prompt = null,
                        expiresAtMs = 0L,
                        pendingAction = null,
                    ),
                )
                ttsClient.speak(result.speech)
                stateMachine.transition(AssistantEvent.ExecutionDone)
            }
            is HandlerResult.NeedsConfirmation -> {
                // Phase 5 short-circuit (Phase 6 replaces this branch).
                val inner = result.onConfirm()
                renderHandlerResult(inner, call)
            }
            is HandlerResult.Failed -> {
                if (!tryAutoRetryOnPermission(call.action, result.reason)) {
                    // No retry possible / not a permission case → speak the failure and end the turn.
                    stateMachine.transition(
                        AssistantEvent.FunctionCallReady(
                            needsConfirmation = false,
                            speech = result.speech,
                            screen = null,
                            prompt = null,
                            expiresAtMs = 0L,
                            pendingAction = null,
                        ),
                    )
                    ttsClient.speak(result.speech)
                    stateMachine.transition(AssistantEvent.ExecutionDone)
                }
            }
        }
    }

    private suspend fun tryAutoRetryOnPermission(action: String, reason: CurroError): Boolean {
        if (action != "call_contact") return false
        return when (reason) {
            is CurroError.ReadContactsPermissionMissing -> {
                if (!readContactsAutoRetried) {
                    readContactsAutoRetried = true
                    mutableSideEffects.emit(
                        AssistantSideEffect.RequestPermission(Manifest.permission.READ_CONTACTS),
                    )
                    true
                } else false
            }
            is CurroError.PermissionDenied -> {
                if (!callPhoneAutoRetried) {
                    callPhoneAutoRetried = true
                    mutableSideEffects.emit(
                        AssistantSideEffect.RequestPermission(Manifest.permission.CALL_PHONE),
                    )
                    true
                } else false
            }
            else -> false
        }
    }

    private suspend fun handleRecordAudioResult(granted: Boolean) {
        if (granted) {
            // Continue the turn — start listening.
            currentJob?.cancel()
            currentJob = scope.launch { runListenLoop() }
        } else {
            val msg = appContext.getString(R.string.copy_perm_missing_mic)
            stateMachine.transition(
                AssistantEvent.SttFailed(message = msg, failureCount = 0),
            )
            ttsClient.speak(msg)
            stateMachine.transition(AssistantEvent.RecoverySpoken)
        }
    }

    private suspend fun handleReadContactsResult(granted: Boolean) {
        val pending = pendingFunctionCall
        if (granted && pending != null) {
            val result = dispatcher.dispatch(pending)
            renderHandlerResult(result, pending)
        } else if (!granted) {
            val msg = appContext.getString(R.string.copy_perm_missing_contacts)
            speakAndIdle(msg)
        }
    }

    private suspend fun handleCallPhoneResult(granted: Boolean) {
        val pending = pendingFunctionCall
        if (granted && pending != null) {
            val result = dispatcher.dispatch(pending)
            renderHandlerResult(result, pending)
        } else if (!granted) {
            val msg = appContext.getString(R.string.copy_perm_missing_calls)
            speakAndIdle(msg)
        }
    }

    private suspend fun onSttFailed(error: CurroError) {
        // Phase 5: hardcode failureCount = 1 (SF-5.4 plugs the real counter in).
        val msg = sttErrorMessage(error)
        stateMachine.transition(
            AssistantEvent.SttFailed(message = msg, failureCount = 1),
        )
        ttsClient.speak(msg)
        stateMachine.transition(AssistantEvent.RecoverySpoken)
    }

    private suspend fun onDecisionFailure(err: Throwable, latencyMs: Int, transcript: String) {
        val (copyId, outcomeLabel) = when (err) {
            is CurroError.ModelCold -> R.string.copy_models_not_ready to "model_cold"
            is CurroError.OutOfMemory -> R.string.copy_error_unknown_function to "oom"
            is CurroError.UnknownFunction -> R.string.copy_error_unknown_function to "unknown_function"
            is CurroError.InvalidFunctionCall -> R.string.copy_error_unknown_function to "invalid_json"
            else -> R.string.copy_error_unknown_function to "other"
        }
        Log.w(
            FAILED_TAG,
            "action=null error=${err::class.simpleName} utterance.len=${transcript.length}",
        )
        emitDecideTelemetry(outcome = outcomeLabel, latencyMs = latencyMs)
        speakAndIdle(appContext.getString(copyId))
    }

    private suspend fun speakAndIdle(speech: String) {
        stateMachine.transition(
            AssistantEvent.FunctionCallReady(
                needsConfirmation = false,
                speech = speech,
                screen = null,
                prompt = null,
                expiresAtMs = 0L,
                pendingAction = null,
            ),
        )
        ttsClient.speak(speech)
        stateMachine.transition(AssistantEvent.ExecutionDone)
    }

    private fun sttErrorMessage(error: CurroError): String =
        when (error) {
            is CurroError.SttVoicePackMissing -> appContext.getString(R.string.copy_stt_no_voice_pack)
            is CurroError.PermissionDenied -> appContext.getString(R.string.copy_perm_missing_mic)
            else -> appContext.getString(R.string.copy_stt_fail_1)
        }

    private fun emitDecideTelemetry(outcome: String, latencyMs: Int) {
        telemetry.event(
            "model_decide",
            mapOf(
                "model" to "function_gemma_270m",
                "outcome" to outcome,
                "latency_ms" to latencyMs,
            ),
        )
    }

    private fun buildContext(): PromptContext = PromptContext(
        nowIso = LocalDateTime.now(clock).withNano(0).toString(),
        unreadMessagesSummary = "",
        knownAliases = emptyList(),
    )

    private fun prettyPrint(call: FunctionCall): String = /* same as VM's prettyPrint — copy verbatim */ ""

    private companion object {
        const val FAILED_TAG = "Curro/FailedCommand"
    }
}
```

### 8.5 `LauncherViewModel` after refactor — pinned skeleton

```kotlin
@HiltViewModel
class LauncherViewModel @Inject constructor(
    detector: DefaultLauncherDetector,
    observeClock: ObserveClockUseCase,
    favoritesRepo: FavoriteAppsRepository,
    private val coordinator: AssistantCoordinator,
    private val notifGate: NotificationAccessGate,
) : ViewModel() {

    internal var lifecycleSource: () -> Lifecycle = { ProcessLifecycleOwner.get().lifecycle }

    private val notifGrantedFlow = MutableStateFlow(notifGate.isGranted())
    private val resumeObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            notifGrantedFlow.value = notifGate.isGranted()
        }
    }
    init { /* same observer-attach pattern as today */ }
    override fun onCleared() { /* same observer-detach */ }

    val uiState: StateFlow<LauncherUiState> =
        combine(
            detector.flow,
            observeClock(),
            favoritesRepo.observeFavorites(),
            coordinator.state,
            notifGrantedFlow,
        ) { isDefault, clock, favorites, assistant, notifGranted ->
            LauncherUiState(
                isCurroDefault = isDefault,
                clock = clock,
                favorites = favorites,
                assistantState = assistant,
                isNotificationAccessGranted = notifGranted,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
            initialValue = LauncherUiState(
                isCurroDefault = false,
                clock = ClockState(timeText = "--:--", dateText = ""),
                favorites = emptyList(),
                assistantState = AssistantState.Idle,
                isNotificationAccessGranted = false,
            ),
        )

    private val _sideEffects = Channel<LauncherSideEffect>(Channel.BUFFERED)
    val sideEffects: Flow<LauncherSideEffect> = _sideEffects.receiveAsFlow()

    init {
        // Adapt coordinator side effects → launcher side effects.
        viewModelScope.launch {
            coordinator.sideEffects.collect { effect ->
                when (effect) {
                    is AssistantSideEffect.RequestPermission -> when (effect.permission) {
                        Manifest.permission.RECORD_AUDIO -> _sideEffects.send(LauncherSideEffect.RequestRecordAudio)
                        Manifest.permission.READ_CONTACTS -> _sideEffects.send(LauncherSideEffect.RequestReadContacts)
                        Manifest.permission.CALL_PHONE -> _sideEffects.send(LauncherSideEffect.RequestCallPhone)
                    }
                    is AssistantSideEffect.ShowDebugJson -> _sideEffects.send(LauncherSideEffect.ShowDebugJson(effect.prettyJson))
                    AssistantSideEffect.OpenNotificationAccessSettings -> Unit
                }
            }
        }
    }

    fun onEvent(event: LauncherEvent) = when (event) {
        is LauncherEvent.MicPressed -> coordinator.onMicPressed()
        is LauncherEvent.AppTileTapped -> onAppTileTapped(event.packageName)
        is LauncherEvent.ClockTapped -> onClockTapped()
        is LauncherEvent.RecordAudioPermissionResult ->
            coordinator.onPermissionResult(Manifest.permission.RECORD_AUDIO, event.granted)
        is LauncherEvent.ReadContactsPermissionResult ->
            coordinator.onPermissionResult(Manifest.permission.READ_CONTACTS, event.granted)
        is LauncherEvent.CallPhonePermissionResult ->
            coordinator.onPermissionResult(Manifest.permission.CALL_PHONE, event.granted)
        is LauncherEvent.GrantNotifAccessRequested -> onGrantNotifAccessRequested()
    }

    private fun onAppTileTapped(packageName: String) { /* unchanged */ }

    private val clockTapTimes = mutableListOf<Long>()
    private fun onClockTapped() { /* unchanged */ }

    private fun onGrantNotifAccessRequested() {
        viewModelScope.launch { _sideEffects.send(LauncherSideEffect.OpenNotificationAccessSettings) }
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        const val TAP_WINDOW_MS = 3_000L
        const val TAP_COUNT_THRESHOLD = 5
    }
}

data class LauncherUiState(
    val isCurroDefault: Boolean,
    val clock: ClockState,
    val favorites: List<FavoriteApp> = emptyList(),
    val assistantState: AssistantState = AssistantState.Idle,
    val isNotificationAccessGranted: Boolean = false,
)
```

**Function count:** `onEvent`, `onAppTileTapped`, `onClockTapped`,
`onGrantNotifAccessRequested`, `onCleared`, plus the `init` block — **5 named
fns + the inits**. The `@Suppress("TooManyFunctions")` annotation can be
removed.

### 8.6 `LauncherPlaceholderScreen.kt` adjustments

Replace every reference to `uiState.listeningState: ListeningState` with
`uiState.assistantState: AssistantState`. The `AnimatedVisibility` shows the
overlay when `assistantState !is AssistantState.Idle`. The inner `when` over
`ListeningState.Starting/Listening/Speaking/Processing/Error` becomes a `when`
over `AssistantState.Listening/Processing/Executing/ErrorRecovery` (plus
`Confirming` which maps to `Unit` for Phase 5 — SF-5.5 wires `Confirming` to
its own overlay, Phase 6 fills the content). **No new composables** — SF-5.5
owns the per-state split.

### 8.7 Hilt wiring

Nothing new. All dependencies already have Hilt bindings:
- `AssistantStateMachine` — `@Singleton @Inject constructor()`.
- `SttClient`, `TtsClient`, `FunctionCallEngine`, `FunctionCallValidator`,
  `HandlerDispatcher`, `TimeProvider`, `TelemetrySink`, `PermissionGate`,
  `ReadContactsPermissionGate`, `CallPhonePermissionGate`, `Clock`,
  `@ApplicationContext Context`, `@ApplicationScope`, `@MainDispatcher` — all
  exist.
- `AssistantCoordinator` — `@Singleton @Inject constructor(...)` — Hilt
  resolves directly.

### 8.8 Navigation Routes

Unchanged.

### 8.9 Material Design Components

Unchanged.

---

## 9. Acceptance Criteria

- [ ] `AssistantCoordinator.kt` lives at `app/src/main/java/com/curro/app/assistant/`.
- [ ] `AssistantSideEffect.kt` lives at the same path.
- [ ] `LauncherViewModel.kt` injects `AssistantCoordinator` and **no** of:
  `SttClient`, `TtsClient`, `FunctionCallEngine`, `FunctionCallValidator`,
  `HandlerDispatcher`, `TelemetrySink`, `PermissionGate`,
  `ReadContactsPermissionGate`, `CallPhonePermissionGate`,
  `@ApplicationContext Context`. Grep-checkable: `grep -c "private val" LauncherViewModel.kt`
  must drop from 10+ to ≤ 4.
- [ ] `LauncherViewModel.kt` has **≤ 8 top-level methods** (currently 18).
  `@Suppress("TooManyFunctions")` removed.
- [ ] `LauncherUiState` has `assistantState: AssistantState` and **no**
  `listeningState`.
- [ ] `presentation/launcher/ListeningState.kt` is **deleted**.
- [ ] All seven Phase-4 handlers still work end-to-end via the coordinator —
  verifiable smoke list in §13.3 (one happy-path coordinator test per handler).
- [ ] The `tell_time` test runs through: `Idle → Listening → Processing →
  Executing → Idle`, observable via `coordinator.state` history (Turbine).
- [ ] STT failure runs through: `Idle → Listening → ErrorRecovery → Idle`.
- [ ] FunctionGemma invalid output runs through: `Idle → Listening →
  Processing → ErrorRecovery → Idle` (note: `Processing → ErrorRecovery` is
  achieved by emitting a synthetic `FunctionCallReady(nc=false,
  speech=copy_unknown_function, …)` and then `RecoverySpoken` — or, more
  cleanly, the brief pins: for decision-layer failures the coordinator
  emits `SttFailed` from `Processing`, which would be invalid per the FSM.
  Pin the resolved approach: **decision-layer failures route through
  `FunctionCallReady(speech=fail_copy, …) → Executing → ExecutionDone`** —
  the user-visible behaviour is identical and the FSM stays clean.)
- [ ] `call_contact` with `READ_CONTACTS` not granted runs through:
  permission-side-effect → grant → re-dispatch → `Executing` → `Idle`.
- [ ] `call_contact` with `READ_CONTACTS` denied runs through:
  permission-side-effect → deny → speak `copy_perm_missing_contacts` →
  `Idle`. The `Failed(speech)` is NOT spoken twice.
- [ ] `HandlerResult.NeedsConfirmation` still auto-confirms (Phase 5
  short-circuit). Pinned: a test demonstrates that returning
  `NeedsConfirmation(onConfirm = { Spoken("ok") })` ultimately reaches the
  `Spoken("ok")` execution path — Phase 6 replaces this with the
  `Confirming` transition.
- [ ] `onMicPressed` in any non-`Idle` state cancels `currentJob` and stops
  TTS **before** transitioning. Verifiable: a test where `Executing` holds a
  long-suspending `ttsClient.speak(...)` and a mic-press cancels the
  in-flight job. (SF-5.3 adds the full from-every-state grid; this SF
  proves the mechanism works once.)
- [ ] `onHomePressed` from any non-`Idle` state reaches `Idle` (full grid is
  SF-5.6).
- [ ] The `model_decide` telemetry event still fires with `{model,
  outcome, latency_ms}` — same shape as Phase 3.
- [ ] No new PII surface: handler crashes still log
  `action=… error=… utterance.len=<int>` (the existing Phase-4 pattern).
  Verifiable by the test that runs a forced `Failed(HandlerCrash)` and
  asserts on the captured log line.
- [ ] No new `CurroError` variant (pinned per the prompt; verified).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest`
  all green.
- [ ] **VM tests deleted** as documented in §13.2; the deletions are
  explicit, not silent.

---

## 10. Design Notes

- **Why the coordinator is `@Singleton`, not `@ActivityRetainedScoped`**:
  the launcher Activity is `singleTask`; it survives configuration changes
  (`stateNotNeeded="true"`). A user-visible turn lives at most a few seconds.
  A singleton-scoped coordinator outlives the activity (the model warm-up
  service does too) and is the right home for cross-activity state if a future
  SF (say, an opt-in `InCallService` invocation) needs to read or interrupt
  it.
- **Why `@ApplicationScope` (`Main.immediate`) and not a new
  `@AssistantScope`?** `Main.immediate` is already in use; it gives us the
  cancellation-deterministic property we need on Main. Heavy work jumps to
  IO inside the engines/clients, so the main-thread orchestration is cheap.
- **Why keep the Phase-5 auto-confirm short-circuit for `NeedsConfirmation`?**
  Phase 4's behaviour is the regression bar. If Phase 5 changes the user-visible
  outcome of a Phase-4 handler that returns `NeedsConfirmation`, it breaks
  the "no regression" promise. Phase 6 is *defined as* the SF that introduces
  the gate. Pinning the short-circuit here keeps the SF boundary clean.
- **Why route decision-layer failures through `FunctionCallReady(speech)` and
  not a synthetic `SttFailed`?** The FSM cleanly defines
  `Processing → Executing → ExecutionDone` as "the spoken outcome of the
  turn"; that's exactly what a decision-layer failure is (Curro speaks
  "Eso no lo sé hacer todavía…" then goes home). Reusing `SttFailed` would
  require widening its valid pre-states beyond `Listening` — a worse
  diagram.
- **Why retain `pendingFunctionCall` / `pendingTranscript` for permission
  retry?** The user said "llama a Pepito" once; we don't want them to repeat
  it after granting the permission. The flag scoping is per-turn (reset on
  every `onMicPressed` / `onHomePressed`). Phase 6 may reshape this into the
  `Confirming.pendingAction` field — pinned out of scope here.
- **Why `collectLatest` in `runListenLoop`?** Defensive — if SF-5.3's cancel
  semantics ever fail to propagate (e.g., a regression in `SttClient.listen()`
  fairness), `collectLatest` ensures only the most recent flow emission's
  work runs. The cancel from `currentJob?.cancel()` is the primary
  mechanism; `collectLatest` is belt-and-braces.

---

## 11. Senior-UX & Copy

No new Spanish strings. The coordinator reads from the existing COPY table:

- `copy_perm_missing_mic`
- `copy_perm_missing_contacts`
- `copy_perm_missing_calls`
- `copy_stt_fail_1`
- `copy_stt_no_voice_pack`
- `copy_error_unknown_function`
- `copy_models_not_ready`

All exist in `strings.xml` (verified). SF-5.4 adds the `fail_2` and `fail_3`
selection logic.

---

## 12. Performance Considerations

- The coordinator runs on `Main.immediate` — no thread hop on transitions.
  `engine.decide` jumps to IO inside the engine; `sttClient.listen()` runs
  off the recogniser's own thread; `ttsClient.speak()` suspends until the
  utterance completes (US-016's `UtteranceProgressListener` wiring).
- `MutableSharedFlow<AssistantSideEffect>` with `extraBufferCapacity = 8`
  and `replay = 0`. Side effects are one-shots; no buffering needed beyond
  burst tolerance.
- `currentJob?.cancel()` is O(1). The Job's children (the STT collect, the
  TTS speak suspension) propagate cancellation via structured concurrency —
  no manual unwinding needed.

---

## 13. Testing Requirements

### 13.1 `AssistantCoordinatorTest.kt` — JVM, ≥ 20 cases

Fakes used (pinned):
- `FakeSttClient`: emits a configurable sequence of `Event.Partial` / `Final` /
  `Failed`. Supports `cancel()` recording.
- `FakeTtsClient`: `speak(s)` records the utterance and suspends until a test
  helper releases it; `stop()` releases the suspended `speak`.
- `FakeFunctionCallEngine`: returns a configurable `Result<String>`.
- `FunctionCallValidator`: **real** instance (it's pure logic).
- `HandlerDispatcher`: **real** (it's a Hilt map adapter); the
  individual `FunctionHandler` instances are fakes returning the precise
  `HandlerResult` each test needs.
- `TestTimeProvider`: from US-035.
- `FakePermissionGate` / `FakeReadContactsPermissionGate` /
  `FakeCallPhonePermissionGate`: configurable `isGranted`.
- `RecordingTelemetrySink`: captures emitted events.

Cases:

**Group A — Per-handler happy paths (6 cases):** for each Phase-4 handler
(`tell_time`, `open_app`, `calculate`, `help`, `read_last_whatsapp`,
`read_all_unread_whatsapp`), wire a fake handler that returns
`Spoken(<expected line>)` and assert the FSM state sequence is
`Idle → Listening → Processing → Executing → Idle` via Turbine on
`coordinator.state`. (`call_contact` happy path is Group C; it has the
permission case to consider.)

**Group B — STT failure (3 cases):**
- `SttNoMatch` → `ErrorRecovery(copy_stt_fail_1, 1)` → speak → `Idle`.
- `SttTimeout` → same.
- `SttVoicePackMissing` → `ErrorRecovery(copy_stt_no_voice_pack, 1)` → speak
  → `Idle`.

**Group C — `call_contact` permission flow (5 cases):**
- Granted both → `Spoken("Llamando…")` → `Idle`.
- `READ_CONTACTS` denied (first time) → emit `RequestPermission(READ_CONTACTS)`.
- `READ_CONTACTS` then granted → re-dispatch → `Spoken("Llamando…")` → `Idle`.
- `READ_CONTACTS` then denied → speak `copy_perm_missing_contacts` → `Idle`.
- `CALL_PHONE` denied (after `READ_CONTACTS` granted) → emit
  `RequestPermission(CALL_PHONE)`.

**Group D — Decision-layer failures (4 cases):**
- `engine.decide` returns `Failure(CurroError.ModelCold)` → speak
  `copy_models_not_ready` → `Idle`.
- `engine.decide` returns `Failure(CurroError.OutOfMemory)` → speak
  `copy_error_unknown_function` → `Idle`.
- Validator returns `Failure(CurroError.UnknownFunction("tradúceme"))` → speak
  `copy_error_unknown_function` → `Idle`.
- Validator returns `Failure(CurroError.InvalidFunctionCall)` → speak
  `copy_error_unknown_function` → `Idle`.

**Group E — `HandlerResult.NeedsConfirmation` (Phase 5 auto-confirm, 2 cases):**
- Handler returns `NeedsConfirmation(prompt, onConfirm = { Spoken("ok") })`
  → coordinator recursively invokes `onConfirm` → `Spoken("ok") → Executing
  → Idle`. **No `Confirming` state ever entered.** (Phase 6's brief expects
  this test to be deleted or inverted.)
- Handler returns `NeedsConfirmation(prompt, onConfirm = { Failed("nope",
  HandlerCrash) })` → coordinator speaks "nope" → `Idle`.

**Group F — Interrupt during `Executing` (the mechanism test for SF-5.3, 1 case):**
- Coordinator is in `Executing("…long string…", null)`, `ttsClient.speak`
  suspended. Caller invokes `onMicPressed()`. Assert: the previous TTS Job
  is cancelled; `ttsClient.stop()` recorded; state transitions to
  `Listening`. (SF-5.3 adds the full 5-state grid.)

**Group G — `HomePressed` from a non-Idle state (1 case):**
- Coordinator in `Processing(...)`. Caller invokes `onHomePressed()`.
  Assert: in-flight Job cancelled; `Idle`.

**Group H — Telemetry shape (1 case):**
- Happy path produces exactly one `model_decide` event with `{model: function_gemma_270m,
  outcome: "success", latency_ms: <int>}`.

**Total: 23 named cases.** Pinned minimum bar: 20.

### 13.2 `LauncherViewModelTest.kt` — pinned deletion list

**Delete (assertions on the removed `ListeningState`):**
- Any test starting `listeningState becomes Listening/Speaking/Processing/Error`
- The "barge-in mid-speak cancels TTS" test (moves to `AssistantCoordinatorTest`,
  Group F).
- The "permission denied for RECORD_AUDIO surfaces transient error" test (moves
  to coordinator handlers).
- The "STT no-match → copy_stt_fail_1" test (moves to coordinator Group B).
- The "decision smoke loop telemetry" test (moves to coordinator Group H).
- The "READ_CONTACTS auto-retry on grant" test (moves to coordinator Group C).
- The "CALL_PHONE auto-retry on grant" test (moves to coordinator Group C).
- The "Spec flow 7 failed-command log line" test (moves to coordinator
  Group D).

**Keep (still launcher-VM concerns):**
- 5-tap config-gesture tests (`onClockTapped` × 5 within window).
- App-tile-tap → `LauncherSideEffect.LaunchApp` test.
- Notification-access ON_RESUME re-check test.
- Favourites + clock combine test.
- `GrantNotifAccessRequested` → `OpenNotificationAccessSettings` side-effect
  test.

The deletion is **part of the PR**; do not skip-tag.

### 13.3 Smoke test list — runs on real device (Redmi 15) after the refactor

Manual one-liners (`adb` push + observe):
1. `tell_time`: "qué hora es" → speak.
2. `open_app`: "abre la cámara" → camera opens.
3. `calculate`: "cuánto es siete por cuatro" → "veintiocho".
4. `help`: "ayuda" → help line.
5. `read_last_whatsapp`: with one fixture message → reads it.
6. `read_all_unread_whatsapp`: with two fixtures → reads grouped.
7. `call_contact`: "llama a Pepito" (with a single Pepito) → dialer.
8. Mic-interrupt mid-read: while `read_all_unread_whatsapp` is reading, tap
   mic → previous read stops in < 500 ms, `Listening` overlay re-shows.

If any of (1)–(8) regresses, the refactor is **rejected**; the implementer
investigates before the SF-5.3 commit.

---

## 14. Implementation Notes

**PM Owner wrote**: Metadata, Summary, Scope, User Flows, Function-catalog
Impact, Senior-UX & Copy, Acceptance Criteria, Design Notes.

**Architect / voice-pipeline-engineer fills in (during implementation)**: the
`AssistantSideEffect` adapter inside the VM if the exact `Manifest.permission.*`
constants need wrapping; any threading subtleties surfaced by Robolectric +
Turbine.

**Commit message (pinned)**:

```
refactor: route assistant pipeline through AssistantCoordinator (US-036 / SF-5.2)

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft — pinned every detail of the refactor, including the VM-thinning checklist and the test deletion list. |
