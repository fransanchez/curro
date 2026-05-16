# Brief — US-041 / SF-6.1: `ConfidencePolicy` + `SettingsRepository` (DataStore) + threshold defaults

## Metadata

| Field | Value |
|---|---|
| **Feature** | `ConfidencePolicy` + DataStore-backed thresholds + always-confirm key |
| **US ID** | US-041 |
| **SF ID** | SF-6.1 |
| **Phase** | 6 — Confidence-graded confirmation |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst (Opus) |
| **Implementer** | voice-pipeline-engineer (Opus) |
| **Size** | M |
| **Depends on** | US-036 (SF-5.2 — `AssistantCoordinator`), US-039 (SF-5.5 — overlay routing) |
| **Unblocks** | US-042 (SF-6.2), US-043 (SF-6.3), US-044 (SF-6.4) |

---

## Summary

Wire the **confidence-graded confirmation policy** from spec §4.3 into the assistant
pipeline. Today (after Phase 5) the coordinator auto-confirms every
`NeedsConfirmation` and never enters `Confirming`. SF-6.1 introduces:

1. A `ConfidencePolicy` in `assistant/` that maps `(CatalogFunction.needsConfirmation,
   confidence, isAmbiguous, alwaysConfirmToggle, executeThreshold, confirmThreshold)`
   to one of `Execute | Confirm | Clarify`.
2. A `SettingsRepository` interface in `domain/repository/` and a DataStore-backed
   `SettingsDataStore` in `data/local/` that exposes the **execute / confirm / always-
   confirm** keys (default `0.85 / 0.60 / false`). This is the **first activation of
   DataStore in Curro** — the scaffolding (dependency, Hilt module, file location,
   delegate pattern) is set here for Phase 7's alias work and Phase 8's settings UI to
   reuse.
3. The coordinator's `decideAndDispatch` is restructured so every successful
   `FunctionCall` flows through the policy *before* it reaches the handler dispatch.
   `Execute` proceeds as today; `Confirm` emits `FunctionCallReady(needsConfirmation =
   true, …)` and the FSM lands in `Confirming`; `Clarify` lands in
   `ErrorRecovery(copy_clarify_intent, failureCount = 0)` and goes home.

After SF-6.1, the `Confirming` state Phase 5 wired but never reached is reachable for
the first time. SF-6.2 builds its UI; SF-6.3 specializes it for the disambiguation
case; SF-6.4 plumbs the always-confirm toggle through.

User benefit (Fran's father): the prototype stops calling Pepito on a half-heard
"llámame a Pepe" — when FunctionGemma is unsure, Curro asks; when it is unsure of
*who* (the 3-Marías), Curro shows the candidates. The thresholds Fran will later tune
from the config menu (Phase 8) are now persisted and read from a single place.

Spec source: §4.3 (the normative confidence policy), §6 flow 2 (the 0.60–0.85
confirm branch) and §9 (the config-menu thresholds + always-confirm toggle).

---

## Scope

### In scope

- New `domain/repository/SettingsRepository.kt` interface with three `Flow` getters
  (`executeThreshold: Flow<Float>`, `confirmThreshold: Flow<Float>`, `alwaysConfirm:
  Flow<Boolean>`) and three `suspend` setters.
- New `data/local/SettingsDataStore.kt` implementing the repository against
  `androidx.datastore.preferences`. Clamps writes; emits defaults on first read.
- New `di/SettingsModule.kt` — `@Binds` `SettingsRepository` to `SettingsDataStore`;
  `@Provides` the singleton `DataStore<Preferences>` instance.
- Activate the version-catalog entry `datastore-preferences` (`1.1.1` already pinned
  under `datastore = "1.1.1"`; flagged "Activated in SF-7.1" — re-flag to SF-6.1 since
  this phase ships first).
- New `assistant/ConfidencePolicy.kt` — pure function (`@Singleton` class with one
  `fun decide(inputs: PolicyInputs): ConfidenceDecision`). No I/O, no Flow. The
  coordinator reads the settings once per turn (`.first()` on each Flow) and passes
  primitives into the policy — this keeps the policy deterministically testable.
- New `assistant/PolicyInputs.kt` (or co-located in `ConfidencePolicy.kt`) — input
  data class.
- New `assistant/ConfidenceDecision.kt` (or co-located) — `enum class
  ConfidenceDecision { Execute, Confirm, Clarify }`.
- Coordinator changes (the architectural delta):
  - `decideAndDispatch` evaluates `ConfidencePolicy` immediately after the validator
    succeeds and **before** `dispatcher.dispatch`.
  - `Execute` → dispatch as today.
  - `Confirm` → emit `FunctionCallReady(needsConfirmation = true, …)` with a built
    `PendingAction(functionName, onConfirm)` whose `onConfirm` runs
    `dispatcher.dispatch(call)` and recurses through `renderHandlerResult`. The FSM
    lands in `Confirming`. The actual SÍ/NO wiring (`onUserConfirmed`,
    `onUserRejected`) is SF-6.2's body.
  - `Clarify` → emit `LowConfidenceClarify` (new event) → FSM transitions to
    `ErrorRecovery(copy_clarify_intent, failureCount = 0)`. The 1-2-3 STT counter is
    NOT touched — STT succeeded; this is a decision-layer recovery (the
    `failureCount = 0` sentinel is already in `ErrorRecovery` per Phase 5).
- The Phase-5 short-circuit in `renderHandlerResult` that auto-recurses into
  `NeedsConfirmation.onConfirm()` is **removed**. Handler-returned
  `NeedsConfirmation` (currently unused by any Fase-1 handler after SF-6.1's wiring,
  but kept for future `send_whatsapp_reply` etc.) now also routes through the FSM's
  `Confirming` path with the same `PendingAction` shape.
- Telemetry: register the existing whitelist key `confidence_below_threshold`
  (`function, threshold, delta`) — it's already in `TelemetryGuardrail.ALLOWED_PROPS`
  but unused. Emit it when the policy returns `Clarify`. Also register a new
  `policy_decided` event (`function_name`, `decision`, `confidence_bucket`,
  `always_confirm_on`) — every policy invocation emits this. Add fixture cases in
  `TelemetryGuardrailTest`.
- Tests: ~36 cases in `ConfidencePolicyTest` (JVM), ~10 cases in
  `SettingsDataStoreTest` (Robolectric), 6 new cases appended to
  `AssistantCoordinatorTest`.

### Out of scope

- The `Confirming`-state overlay UI → SF-6.2.
- The disambiguation flow (3-Marías) and `ContactPickerOverlay` → SF-6.3.
- Wiring the always-confirm flag from settings into the policy at the coordinator
  layer → SF-6.4. (SF-6.1 introduces the **key**; SF-6.4 wires it through the
  coordinator. SF-6.1's policy reads the value from `PolicyInputs` so the unit tests
  cover both values, but the coordinator passes `false` until SF-6.4.)
- The settings-menu UI (sliders, toggle) → Phase 8.
- Room database / aliases → Phase 7.
- The constrained-vocabulary STT for SÍ/NO → SF-6.2.

---

## User Flows

### Flow 1 — High-confidence path (regression — current Phase 5 behaviour)

1. User taps mic → `Listening`.
2. "Llama a Pepito" → STT → `Processing`.
3. FunctionGemma → `{action: call_contact, params: {contact: "Pepito"}, confidence:
   0.92}` → validator OK.
4. `ConfidencePolicy.decide(…)` with `function.needsConfirmation = CONDITIONAL`,
   `confidence = 0.92`, `executeThreshold = 0.85`, `confirmThreshold = 0.60`,
   `isAmbiguous = false`, `alwaysConfirmToggle = false` → `Execute`.
5. `dispatcher.dispatch(call)` → `HandlerResult.Spoken("Llamando a Pepito.")` (no
   ambiguity, single match in Phase 4's stub).
6. `Executing` → TTS speaks → `Idle`. **No behavioural change from Phase 5.**

### Flow 2 — Medium-confidence path (NEW — `Confirming` is reachable)

1. User taps mic → `Listening`.
2. "Llámame a Pepe" (mumbled) → STT → `Processing`.
3. FunctionGemma → `{action: call_contact, params: {contact: "Pepe"}, confidence:
   0.71}` → validator OK.
4. `ConfidencePolicy.decide(…)` with `confidence = 0.71` → `Confirm`.
5. Coordinator emits `FunctionCallReady(needsConfirmation = true, prompt = "¿Llamo
   a Pepe?", expiresAtMs = now + 10_000, pendingAction = PendingAction(…))`. FSM →
   `Confirming`.
6. **Phase-6 boundary — SF-6.2 takes over here.** SF-6.1's coordinator stops at this
   transition; the overlay, the 10-s timer, and the SÍ/NO handling are SF-6.2.

### Flow 3 — Low-confidence clarify (NEW)

1. User taps mic → `Listening`.
2. "Mmmhpf llama no espera Pepe" → STT → `Processing`.
3. FunctionGemma → `{action: call_contact, params: {contact: "Pepe"}, confidence:
   0.40}` → validator OK.
4. `ConfidencePolicy.decide(…)` with `confidence = 0.40` → `Clarify`.
5. Coordinator emits `LowConfidenceClarify("¿Quieres llamar a alguien?")` → FSM →
   `ErrorRecovery(copy_clarify_intent, failureCount = 0)`.
6. TTS speaks `copy_clarify_intent` → `Idle`. **The STT failure counter is NOT
   incremented** (`failureCount = 0` is the sentinel — see Phase 5's
   `ErrorRecovery.failureCount` Kdoc).

### Flow 4 — Reversible action stays uninterrupted

1. User taps mic → `Listening`.
2. "Qué hora es" → STT → `Processing`.
3. FunctionGemma → `{action: tell_time, params: {what: "all"}, confidence: 0.94}`.
4. `ConfidencePolicy.decide(…)` with `function.needsConfirmation = NO`, `confidence
   = 0.94` → `Execute`. (`NO` always executes — see "Policy table" below.)
5. Dispatch → `Spoken(...)` → `Executing` → `Idle`. **No regression for reversible
   actions** even when confidence is low.

### Flow 5 — Reversible action under low confidence (edge case — clarify still wins)

1. User taps mic → `Listening`.
2. "Mmm horra" → STT → `Processing`.
3. FunctionGemma → `{action: tell_time, params: {what: "time"}, confidence: 0.40}`.
4. `ConfidencePolicy.decide(…)` with `function.needsConfirmation = NO`, `confidence
   = 0.40` → `Clarify`. (Even reversible actions clarify under <0.60 — at that
   confidence the model probably picked the wrong action; the spec is explicit at
   §4.3 that the `<0.60` rule applies to *the model's certainty* irrespective of the
   action type. Pinned: low confidence → clarify, regardless of
   `needs_confirmation`.)
5. → `ErrorRecovery(copy_clarify_intent, 0)` → `Idle`.

---

## Function-catalog Impact

**No catalog changes.** SF-6.1 consumes the catalog's existing
`CatalogFunction.needsConfirmation: NeedsConfirmation` enum (`NO | YES |
CONDITIONAL`) — already pinned in `domain/catalog/CatalogFunction.kt` (verified
on-disk).

Mapping is already correct for Phase 1:
- `tell_time`, `open_app`, `calculate`, `help`, `read_last_whatsapp`,
  `read_all_unread_whatsapp` → `NO`.
- `call_contact` → `CONDITIONAL`.
- No Phase-1 function uses `YES` (it is reserved for Phase-2 `send_whatsapp_reply`).

SF-6.1 verifies this is the on-disk state before proceeding; if the values drift
during implementation, restore them (and not the other way round).

---

## FSM States Touched

- **`Processing`** — exit point gains a third branch. After validator success:
  `policy.decide(…)` produces one of `Execute | Confirm | Clarify`, which the
  coordinator turns into `FunctionCallReady(needsConfirmation = false)`,
  `FunctionCallReady(needsConfirmation = true)`, or `LowConfidenceClarify`. No
  changes inside `AssistantStateMachine.computeNext` are needed for the first two
  branches (Phase 5 already validates the invariants). The third branch adds a new
  event:
  ```kotlin
  data class LowConfidenceClarify(val message: String) : AssistantEvent
  ```
  with the transition `Processing → ErrorRecovery(message, failureCount = 0)`.
  Append-only — no existing transition rewrites.
- **`Confirming`** — now reachable for the first time. The state and its `prompt
  / expiresAtMs / pendingAction` fields exist (Phase 5). SF-6.1 does NOT add the
  10-s timer, the overlay, or the SÍ/NO event handling. Those are SF-6.2.
- **`ErrorRecovery`** — gains a new way in (decision-layer clarify with
  `failureCount = 0`). No change to the existing STT-recovery path, the 1/2/3
  counter, or the `RecoverySpoken` exit.
- **`Idle / Listening / Executing`** — untouched.

Append `LowConfidenceClarify` to the `AssistantEvent` enumeration. Two new cases
in `computeNext` (the event itself; and a defensive `null` for any state other
than `Processing`).

---

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none new) | — | — | — |

DataStore needs no permission. The constrained-vocabulary STT for SÍ/NO arrives in
SF-6.2 and reuses the existing `RECORD_AUDIO`.

`androidx.datastore.preferences` adds no manifest entries.

---

## On-device-model Impact

**No model impact.** The policy consumes the `confidence` the model already returns;
it does not change the prompt, the catalog, or the validator. No Gemma 3n use.

Latency impact is negligible — the policy is a `when` on five primitives and a
constant-time DataStore read (the Flow values are emitted from in-memory state
once the first read materialises). Budget: < 1 ms wall-clock added per turn.

---

## Android Specification

### Files added

| Path | Purpose |
|---|---|
| `app/src/main/java/com/curro/app/domain/repository/SettingsRepository.kt` | Interface — three Flow getters + three suspend setters. |
| `app/src/main/java/com/curro/app/data/local/SettingsDataStore.kt` | DataStore impl. `@Singleton`. Clamps writes. |
| `app/src/main/java/com/curro/app/di/SettingsModule.kt` | `@Binds SettingsRepository`; `@Provides DataStore<Preferences>` singleton. |
| `app/src/main/java/com/curro/app/assistant/ConfidencePolicy.kt` | `@Singleton class` with one `decide(...)`. |
| `app/src/main/java/com/curro/app/assistant/PolicyInputs.kt` | Input data class. May co-locate in `ConfidencePolicy.kt` — see "Decision: file layout". |
| `app/src/main/java/com/curro/app/assistant/ConfidenceDecision.kt` | `enum class`. May co-locate. |

### Files modified

| Path | Change |
|---|---|
| `gradle/libs.versions.toml` | Re-flag `datastore-preferences` comment from `# Activated in SF-7.1` → `# Activated in SF-6.1`. No version bump. |
| `app/build.gradle.kts` | Add `implementation(libs.datastore.preferences)` line under the existing dependencies block. |
| `app/src/main/java/com/curro/app/assistant/AssistantEvent.kt` | Append `data class LowConfidenceClarify(val message: String) : AssistantEvent`. |
| `app/src/main/java/com/curro/app/assistant/AssistantStateMachine.kt` | Add `is AssistantEvent.LowConfidenceClarify -> when (current) { is Processing -> ErrorRecovery(event.message, 0); else -> null }` in `computeNext`. |
| `app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt` | Inject `SettingsRepository`, `ConfidencePolicy`. In `onDecisionSuccess`: read thresholds, call `policy.decide(...)`, branch. **Remove** the auto-recursion in `renderHandlerResult.NeedsConfirmation` branch — replace with the same `Confirming` transition. Move the `executeAndFinish(...)` direct-execute path into the `Execute` branch. |
| `app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt` | Add `"policy_decided" to setOf("function_name", "decision", "confidence_bucket", "always_confirm_on")`. Verify `confidence_below_threshold` is still listed (it is, on line 64). |
| `app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailTest.kt` | Add fixture cases for `policy_decided` (allow, reject on unknown key, reject on PII shape). |

### Decision: file layout

Co-locate `PolicyInputs` and `ConfidenceDecision` in `ConfidencePolicy.kt` as
top-level classes (not nested) — keeps the policy module a single file like
`SttFailureCounter.kt` and `TimeProvider.kt` already do. Mirror the file naming
the implementer prefers; pin "one file is fine" so review doesn't bikeshed.

### `SettingsRepository` interface

```kotlin
package com.curro.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * On-device user settings (spec §9). Phase 6 wires three keys; Phase 7 adds
 * favorite-app overrides; Phase 8 adds TTS voice/rate/pitch + the incoming-call
 * toggle + "send failures to Fran".
 *
 * Defaults (returned on first read when the DataStore file does not yet contain
 * the key): executeThreshold = 0.85f, confirmThreshold = 0.60f,
 * alwaysConfirm = false. Pinned by spec §4.3 / §9.
 *
 * Validation: setters clamp out-of-range values. Out-of-order writes
 * (`confirm > execute`) are logged at WARN and clamped at the setter; the policy
 * never sees an inconsistent pair.
 */
interface SettingsRepository {
    /** ≥ this confidence → execute directly. Default 0.85f. Range [0.0, 1.0]. */
    val executeThreshold: Flow<Float>

    /** [this, executeThreshold) confidence → confirm. Default 0.60f. Range [0.0, executeThreshold]. */
    val confirmThreshold: Flow<Float>

    /**
     * When true, every `CONDITIONAL` function escalates to confirmation
     * regardless of confidence (spec §4.3 always-escalate case #3 + spec §9).
     * Default false. Phase 8 surfaces the toggle in the config menu; SF-6.4
     * wires it through the coordinator.
     */
    val alwaysConfirm: Flow<Boolean>

    /** Clamps to [0.0f, 1.0f]. If value < confirmThreshold, also raises confirm to value. */
    suspend fun setExecuteThreshold(value: Float)

    /** Clamps to [0.0f, executeThreshold]. If value > executeThreshold, also lowers execute to value. */
    suspend fun setConfirmThreshold(value: Float)

    suspend fun setAlwaysConfirm(value: Boolean)
}
```

### `SettingsDataStore` impl

```kotlin
package com.curro.app.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.curro.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module-private extension property that materialises the DataStore singleton —
 * the standard `preferencesDataStore(name)` pattern (per the AndroidX docs).
 *
 * The first activation of DataStore in Curro (SF-6.1). Phase 7 adds the alias
 * Room database alongside; Phase 8 reuses this same file for more setting keys.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "curro_settings",
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    override val executeThreshold: Flow<Float> =
        context.dataStore.data.map { it[Keys.EXECUTE] ?: Defaults.EXECUTE }

    override val confirmThreshold: Flow<Float> =
        context.dataStore.data.map { it[Keys.CONFIRM] ?: Defaults.CONFIRM }

    override val alwaysConfirm: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ALWAYS_CONFIRM] ?: Defaults.ALWAYS_CONFIRM }

    override suspend fun setExecuteThreshold(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        val currentConfirm = confirmThreshold.first()
        context.dataStore.edit { prefs ->
            prefs[Keys.EXECUTE] = clamped
            if (currentConfirm > clamped) {
                Log.w(TAG, "setExecuteThreshold: confirm ($currentConfirm) > execute ($clamped); clamping confirm.")
                prefs[Keys.CONFIRM] = clamped
            }
        }
    }

    override suspend fun setConfirmThreshold(value: Float) {
        val currentExecute = executeThreshold.first()
        val clamped = value.coerceIn(0f, currentExecute)
        if (clamped != value) {
            Log.w(TAG, "setConfirmThreshold: value $value out of [0, $currentExecute]; clamped to $clamped.")
        }
        context.dataStore.edit { prefs -> prefs[Keys.CONFIRM] = clamped }
    }

    override suspend fun setAlwaysConfirm(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ALWAYS_CONFIRM] = value }
    }

    private object Keys {
        val EXECUTE = floatPreferencesKey("confidence_execute_min")
        val CONFIRM = floatPreferencesKey("confidence_confirm_min")
        val ALWAYS_CONFIRM = booleanPreferencesKey("always_confirm")
    }

    private object Defaults {
        const val EXECUTE = 0.85f
        const val CONFIRM = 0.60f
        const val ALWAYS_CONFIRM = false
    }

    private companion object {
        const val TAG = "Curro/Settings"
    }
}
```

### `SettingsModule`

```kotlin
package com.curro.app.di

import com.curro.app.data.local.SettingsDataStore
import com.curro.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsDataStore): SettingsRepository
}
```

The DataStore singleton itself is provided by the extension property — no
`@Provides` needed (this is the AndroidX-recommended idiom for app-singleton
DataStores).

### `ConfidencePolicy`

```kotlin
package com.curro.app.assistant

import com.curro.app.domain.catalog.NeedsConfirmation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inputs the policy needs to produce a decision. Pure primitives so the policy
 * is deterministic and trivial to unit-test (no Flows, no I/O).
 *
 * The coordinator builds this once per turn: it reads the thresholds and the
 * always-confirm flag from [com.curro.app.domain.repository.SettingsRepository]
 * via `.first()`, then calls [ConfidencePolicy.decide].
 */
data class PolicyInputs(
    val needsConfirmation: NeedsConfirmation,
    val confidence: Float,
    val isAmbiguous: Boolean,
    val alwaysConfirmToggle: Boolean,
    val executeThreshold: Float,
    val confirmThreshold: Float,
)

enum class ConfidenceDecision { Execute, Confirm, Clarify }

/**
 * Spec §4.3 — the confidence-graded confirmation policy.
 *
 * Precedence (top → bottom):
 *   1. Ambiguous param → Confirm (always-escalate case #1).
 *   2. needs_confirmation = YES → Confirm.
 *   3. confidence < confirmThreshold → Clarify (applies to NO and CONDITIONAL alike;
 *      see User Flow #5).
 *   4. needs_confirmation = NO → Execute.
 *   5. needs_confirmation = CONDITIONAL + alwaysConfirmToggle → Confirm
 *      (always-escalate case #3).
 *   6. needs_confirmation = CONDITIONAL + confidence ≥ executeThreshold → Execute.
 *   7. needs_confirmation = CONDITIONAL + confidence in [confirmThreshold, executeThreshold) → Confirm.
 */
@Singleton
class ConfidencePolicy @Inject constructor() {
    fun decide(inputs: PolicyInputs): ConfidenceDecision = when {
        inputs.isAmbiguous -> ConfidenceDecision.Confirm
        inputs.needsConfirmation == NeedsConfirmation.YES -> ConfidenceDecision.Confirm
        inputs.confidence < inputs.confirmThreshold -> ConfidenceDecision.Clarify
        inputs.needsConfirmation == NeedsConfirmation.NO -> ConfidenceDecision.Execute
        // CONDITIONAL from here on.
        inputs.alwaysConfirmToggle -> ConfidenceDecision.Confirm
        inputs.confidence >= inputs.executeThreshold -> ConfidenceDecision.Execute
        else -> ConfidenceDecision.Confirm
    }
}
```

**Note on rule precedence** (re-verified against spec §4.3 + `function-catalog`
"always-escalate cases"):
- The two always-escalate cases that apply at SF-6.1 are #1 (ambiguity) and #3
  (always-confirm toggle). Case #2 (irreversible cost) does not exist among the
  Fase-1 catalog functions — only future Fase-2+ functions trigger it (e.g.
  `send_whatsapp_reply`'s `needsConfirmation = YES`). The policy does not encode
  "irreversibility" as a separate flag; the catalog's `YES` value is the encoding.
- Rule #3 of `function-catalog` skill ("when 'always confirm' is on, every
  `conditional` function escalates regardless of confidence") is enforced at rule
  precedence step 5 above.

### `AssistantCoordinator` change (the architectural delta)

**Current** (Phase 5, lines 210–251 of `AssistantCoordinator.kt`):
```kotlin
private suspend fun onDecisionSuccess(call, latencyMs, transcript) {
    emitDecideTelemetry("success", latencyMs)
    if (BuildConfig.DEBUG) emit ShowDebugJson
    pendingFunctionCall = call
    pendingTranscript = transcript
    val result = dispatcher.dispatch(call)
    renderHandlerResult(result, call)
}

private suspend fun renderHandlerResult(result, call) {
    when (result) {
        is Spoken -> executeAndFinish(result.speech, result.screen)
        is NeedsConfirmation -> { val inner = result.onConfirm(); renderHandlerResult(inner, call) }  // ← auto-confirm
        is Failed -> ...
    }
}
```

**After SF-6.1**:
```kotlin
private suspend fun onDecisionSuccess(call, latencyMs, transcript) {
    emitDecideTelemetry("success", latencyMs)
    if (BuildConfig.DEBUG) emit ShowDebugJson
    pendingFunctionCall = call
    pendingTranscript = transcript

    val catalogFunction = Fase1Catalog.functions.firstOrNull { it.name == call.action }
        ?: run {
            // Validator already filtered unknown functions; defensive fall-through to clarify.
            return clarify(R.string.copy_clarify_intent)
        }

    val inputs = PolicyInputs(
        needsConfirmation = catalogFunction.needsConfirmation,
        confidence = call.confidence,
        isAmbiguous = false,                   // SF-6.3 wires the ambiguity signal
        alwaysConfirmToggle = false,           // SF-6.4 wires the toggle
        executeThreshold = settings.executeThreshold.first(),
        confirmThreshold = settings.confirmThreshold.first(),
    )

    val decision = policy.decide(inputs)
    emitPolicyTelemetry(call.action, decision, call.confidence, inputs.alwaysConfirmToggle)

    when (decision) {
        ConfidenceDecision.Execute -> {
            val result = dispatcher.dispatch(call)
            renderHandlerResult(result, call)
        }
        ConfidenceDecision.Confirm -> {
            // Build the PendingAction; the FSM lands in Confirming.
            val prompt = buildConfirmPrompt(call)
            val pendingAction = PendingAction(
                functionName = call.action,
                onConfirm = {
                    val r = dispatcher.dispatch(call)
                    r
                },
            )
            stateMachine.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = true,
                    speech = "",                    // unused for confirming branch
                    screen = null,
                    prompt = prompt,
                    expiresAtMs = timeProvider.now() + CONFIRM_TIMEOUT_MS,
                    pendingAction = pendingAction,
                ),
            )
            ttsClient.speak(prompt)
            // SF-6.1 STOPS HERE. SF-6.2 wires the 10-s timer + onUserConfirmed/onUserRejected/
            // ConfirmationTimedOut transitions.
        }
        ConfidenceDecision.Clarify -> {
            clarify(R.string.copy_clarify_intent)
        }
    }
}

private suspend fun renderHandlerResult(result, call) {
    when (result) {
        is Spoken -> executeAndFinish(result.speech, result.screen)
        is NeedsConfirmation -> {
            // The handler chose to escalate (e.g. a future send_whatsapp_reply that
            // wants to show the user the rewritten text first). Route through the FSM,
            // NOT auto-recurse. The Phase-5 short-circuit is removed here.
            val pendingAction = PendingAction(call.action, onConfirm = result.onConfirm)
            stateMachine.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = true,
                    speech = "",
                    screen = null,
                    prompt = result.prompt,
                    expiresAtMs = timeProvider.now() + CONFIRM_TIMEOUT_MS,
                    pendingAction = pendingAction,
                ),
            )
            ttsClient.speak(result.prompt)
        }
        is Failed -> /* unchanged */
    }
}

private suspend fun clarify(@StringRes copyId: Int) {
    val msg = appContext.getString(copyId)
    stateMachine.transition(AssistantEvent.LowConfidenceClarify(msg))
    ttsClient.speak(msg)
    stateMachine.transition(AssistantEvent.RecoverySpoken)
}

private fun buildConfirmPrompt(call: FunctionCall): String = when (call.action) {
    "call_contact" -> appContext.getString(
        R.string.copy_confirm_call,
        call.params["contact"] as? String ?: "",
    )
    // Phase-2 send_whatsapp_reply etc. land their own copies.
    else -> appContext.getString(R.string.copy_clarify_intent)   // defensive
}

private fun emitPolicyTelemetry(
    fn: String,
    decision: ConfidenceDecision,
    confidence: Float,
    alwaysConfirm: Boolean,
) {
    telemetry.event(
        "policy_decided",
        mapOf(
            "function_name" to fn,
            "decision" to decision.name.lowercase(),
            "confidence_bucket" to bucket(confidence),       // "<0.60" | "0.60-0.85" | ">=0.85"
            "always_confirm_on" to alwaysConfirm,
        ),
    )
}

private companion object {
    /* existing entries */
    const val CONFIRM_TIMEOUT_MS = 10_000L
}
```

The build/Lint-passable form is the implementer's job (the example above sketches
the shape). Pin:
- The catalog lookup is `Fase1Catalog.functions.firstOrNull { it.name == call.action }`
  — there is no `CatalogLookup` repository today and SF-6.1 does not introduce one.
- The `pendingAction.onConfirm` lambda captures `call` by reference; that's fine
  because the coordinator's lifetime > the lambda's. Tests inject a fake
  `HandlerDispatcher` that returns a deterministic `HandlerResult`.
- `confirm_bucket()` is a private helper that returns one of three strings: keep
  short, ≤ 8 chars each, ensure they pass `TelemetryGuardrail`'s length heuristic.

### Strings (verify and add)

| Key | Spanish | Status |
|---|---|---|
| `copy_confirm_call` | `¿Llamo a %1$s?` | **Exists** — verified on-disk (line 24 of `strings.xml`). Used by `buildConfirmPrompt` for the `Confirm` branch. |
| `copy_clarify_intent` | `No te he entendido bien, ¿quieres llamar a alguien?` | **NEW — add.** Spec §4.3 verbatim. The `<0.60` clarify line. |

Add the `copy_clarify_intent` entry under "Confirmation prompts" in
`app/src/main/res/values/strings.xml` with the comment block:
```xml
<!-- confidence < 0.60 → clarify (spec §4.3) — SF-6.1 -->
<string name="copy_clarify_intent">No te he entendido bien, ¿quieres llamar a alguien?</string>
```

> **Note:** the clarify line is currently `call`-shaped because `call_contact` is
> the only `CONDITIONAL` function in Phase 1, and the only function that practically
> reaches the clarify branch under realistic load. When Phase 2 adds
> `send_whatsapp_reply`, the architect may want to parameterise this — but not now;
> SF-6.1 keeps the spec's literal text.

The SÍ / NO button copies (`copy_yes`, `copy_no`) and the `copy_confirm_timeout`,
`copy_cancel_no_call`, `copy_calling_confirmed` lines already exist on-disk for
SF-6.2.

### Build files

`gradle/libs.versions.toml` — re-flag the comment on line 67 / 126:
```toml
datastore        = "1.1.1"        # Activated in SF-6.1
...
datastore-preferences          = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" } # Activated in SF-6.1
```

`app/build.gradle.kts` — add inside the `dependencies { … }` block, grouped with
other AndroidX entries:
```kotlin
implementation(libs.datastore.preferences)
```

No KSP / kapt changes — DataStore Preferences needs nothing beyond the runtime
artifact.

---

## Acceptance Criteria

### Build & static checks
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug` green on a fresh clone.
- [ ] `gradle/libs.versions.toml` shows `datastore-preferences` activated in SF-6.1
      and `app/build.gradle.kts` has `implementation(libs.datastore.preferences)`.
- [ ] No new permissions, no manifest changes.

### `ConfidencePolicy` correctness
- [ ] `ConfidencePolicy.decide(...)` returns `Execute` when
      `needsConfirmation = CONDITIONAL`, `confidence = 0.95`, `executeThreshold =
      0.85`, `confirmThreshold = 0.60`, `isAmbiguous = false`, `alwaysConfirmToggle
      = false`.
- [ ] Same inputs but `confidence = 0.72` → `Confirm`.
- [ ] Same inputs but `confidence = 0.40` → `Clarify`.
- [ ] Same inputs but `isAmbiguous = true` (any confidence) → `Confirm`.
- [ ] Same inputs but `alwaysConfirmToggle = true`, `confidence = 0.95` → `Confirm`.
- [ ] `needsConfirmation = YES` always returns `Confirm` (any confidence, any
      toggle, any ambiguity).
- [ ] `needsConfirmation = NO` returns `Execute` when `confidence ≥ confirmThreshold`.
- [ ] `needsConfirmation = NO` returns `Clarify` when `confidence < confirmThreshold`.
- [ ] All 36 canonical cases in `ConfidencePolicyTest` pass — see "Testing
      Requirements" for the table.

### `SettingsRepository` / `SettingsDataStore` correctness
- [ ] Defaults on first read: `executeThreshold = 0.85f`, `confirmThreshold =
      0.60f`, `alwaysConfirm = false`.
- [ ] `setExecuteThreshold(0.9f)` round-trips to `0.9f`; subsequent
      `executeThreshold.first()` is `0.9f`.
- [ ] `setExecuteThreshold(1.5f)` clamps to `1.0f` (out-of-range high).
- [ ] `setExecuteThreshold(-0.1f)` clamps to `0.0f` (out-of-range low).
- [ ] `setExecuteThreshold(0.5f)` when `confirmThreshold = 0.6f` also lowers
      `confirmThreshold` to `0.5f` (consistency rule).
- [ ] `setConfirmThreshold(0.95f)` when `executeThreshold = 0.85f` clamps confirm
      to `0.85f` and logs a WARN (not an exception).
- [ ] `setAlwaysConfirm(true)` round-trips.
- [ ] Concurrent reads from `executeThreshold.first()` and a parallel
      `setExecuteThreshold(0.9f)` are race-free (DataStore's serialization
      guarantee — covered by Robolectric test with `runTest`).

### Coordinator integration
- [ ] `call_contact` confidence `0.95` (defaults, no ambiguity, no toggle) → FSM
      ends in `Idle` after `Executing` (no `Confirming`).
- [ ] `call_contact` confidence `0.72` → FSM transitions through `Processing →
      Confirming`, and stays in `Confirming` after `onDecisionSuccess` returns
      (SF-6.2 takes over from here).
- [ ] `call_contact` confidence `0.40` → FSM transitions through `Processing →
      ErrorRecovery(copy_clarify_intent, failureCount = 0) → Idle`. The STT
      failure counter is NOT incremented (`sttFailureCounter.recordFailure` is
      never called).
- [ ] `call_contact` confidence `0.95` with `alwaysConfirm = false` (SF-6.4 not
      yet wired — the coordinator hard-codes `false` until SF-6.4) → `Execute`.
- [ ] `tell_time` confidence `0.40` → `Clarify` → `ErrorRecovery → Idle`.
- [ ] `tell_time` confidence `0.94` → `Execute` (unchanged from Phase 5).

### FSM
- [ ] `AssistantEvent.LowConfidenceClarify(msg)` from `Processing` → `ErrorRecovery(msg,
      failureCount = 0)`.
- [ ] `AssistantEvent.LowConfidenceClarify(msg)` from any other state → throws
      `IllegalAssistantTransition` (no silent acceptance).

### Telemetry
- [ ] Every `onDecisionSuccess` call emits exactly one `policy_decided` event
      (no double-emit, no skip).
- [ ] `policy_decided` props: `function_name` (snake_case), `decision`
      (`"execute" | "confirm" | "clarify"`), `confidence_bucket` (`"low" |
      "mid" | "high"`), `always_confirm_on` (Boolean).
- [ ] `TelemetryGuardrail` accepts the event and rejects unknown keys; covered
      by 3 new fixture cases in `TelemetryGuardrailTest`.

### Regression
- [ ] Every Phase-5 test in `AssistantCoordinatorTest` still passes (the change is
      append-and-replace, never structural — confirm with a clean run).
- [ ] Every Phase-4 handler test still passes (handlers are not touched by SF-6.1).

---

## Senior-UX & Copy

The only new string in SF-6.1 is `copy_clarify_intent` (added above). No new
spoken-vs-shown asymmetry — the clarify line goes through TTS the same way the
STT-failure lines do. The `Confirming`-state overlay is SF-6.2's job.

Curro's voice for the clarify line is **honest, not apologetic**: "No te he
entendido bien, ¿quieres llamar a alguien?" — no "lo siento", no codes. This
matches `brand-design`'s rule "errors are plain + offer an alternative" — the
alternative is implicit (try again).

---

## Performance Considerations

- Reading three Flows with `.first()` on every turn is **constant-time** after
  DataStore's first materialisation. The first `.first()` after process start
  reads the file from disk; subsequent reads hit the in-memory replay cache.
  Budget impact: < 5 ms cold, < 0.1 ms warm.
- The policy itself is a `when` on five primitives — sub-microsecond.
- No additional coroutine launches per turn (the existing `onDecisionSuccess`
  is already in a coroutine).
- `SettingsDataStore` is `@Singleton` — one DataStore handle per process, the
  AndroidX-recommended pattern.

---

## Testing Requirements

### `ConfidencePolicyTest` (JVM, JUnit 5 — ~36 cases)

Group A — `needsConfirmation = NO` (6 cases):
1. `NO, confidence = 0.95, !ambig, !toggle, 0.85, 0.60 → Execute`
2. `NO, confidence = 0.72, !ambig, !toggle, 0.85, 0.60 → Execute` (NO ignores the
    execute threshold — only the confirm threshold protects against clarify)
3. `NO, confidence = 0.40, !ambig, !toggle, 0.85, 0.60 → Clarify`
4. `NO, confidence = 0.95, ambig, !toggle, 0.85, 0.60 → Confirm` (ambig wins)
5. `NO, confidence = 0.95, !ambig, toggle, 0.85, 0.60 → Execute` (toggle only
    affects CONDITIONAL — pinned)
6. `NO, confidence = 0.59, !ambig, !toggle, 0.85, 0.60 → Clarify` (boundary)

Group B — `needsConfirmation = YES` (4 cases):
7. `YES, 0.95, !ambig, !toggle → Confirm`
8. `YES, 0.40, !ambig, !toggle → Confirm`
9. `YES, 0.95, ambig, !toggle → Confirm`
10. `YES, 0.95, !ambig, toggle → Confirm`

Group C — `needsConfirmation = CONDITIONAL`, no ambig, no toggle (6 cases):
11. `CONDITIONAL, 0.95, …, 0.85, 0.60 → Execute`
12. `CONDITIONAL, 0.85, …, 0.85, 0.60 → Execute` (boundary)
13. `CONDITIONAL, 0.72, …, 0.85, 0.60 → Confirm`
14. `CONDITIONAL, 0.60, …, 0.85, 0.60 → Confirm` (boundary)
15. `CONDITIONAL, 0.59, …, 0.85, 0.60 → Clarify` (just below)
16. `CONDITIONAL, 0.40, …, 0.85, 0.60 → Clarify`

Group D — `needsConfirmation = CONDITIONAL`, ambig precedence (4 cases):
17. `CONDITIONAL, 0.95, ambig, !toggle → Confirm` (ambig wins over Execute)
18. `CONDITIONAL, 0.72, ambig, !toggle → Confirm` (ambig confirms anyway)
19. `CONDITIONAL, 0.40, ambig, !toggle → Confirm` (ambig wins over Clarify — pin
    this — the spec §4.3 always-escalate case #1 supersedes clarify)
20. `CONDITIONAL, 0.95, ambig, toggle → Confirm`

Group E — `needsConfirmation = CONDITIONAL`, toggle precedence (4 cases):
21. `CONDITIONAL, 0.95, !ambig, toggle → Confirm` (toggle wins over Execute)
22. `CONDITIONAL, 0.72, !ambig, toggle → Confirm` (would confirm anyway)
23. `CONDITIONAL, 0.40, !ambig, toggle → Clarify` (toggle does NOT override
    clarify — pin this — confidence < `confirmThreshold` means the model is too
    unsure for the user to confirm anything meaningful; spec §4.3 implies the
    clarify branch is more protective than the toggle)
24. `CONDITIONAL, 0.95, ambig, toggle → Confirm` (both fire; result is Confirm)

Group F — custom thresholds (Fran tweaked them) (6 cases):
25. `CONDITIONAL, 0.80, …, 0.75, 0.50 → Execute` (executeThreshold lowered)
26. `CONDITIONAL, 0.70, …, 0.75, 0.50 → Confirm`
27. `CONDITIONAL, 0.49, …, 0.75, 0.50 → Clarify`
28. `CONDITIONAL, 0.95, …, 0.95, 0.80 → Execute` (executeThreshold raised)
29. `CONDITIONAL, 0.90, …, 0.95, 0.80 → Confirm`
30. `CONDITIONAL, 0.79, …, 0.95, 0.80 → Clarify`

Group G — defensive boundaries (6 cases):
31. `CONDITIONAL, 1.0, …, 0.85, 0.60 → Execute`
32. `CONDITIONAL, 0.0, …, 0.85, 0.60 → Clarify`
33. `CONDITIONAL, 0.85, !ambig, !toggle, 0.85, 0.60 → Execute` (exact-equal
    execute → execute)
34. `CONDITIONAL, 0.60, !ambig, !toggle, 0.85, 0.60 → Confirm` (exact-equal
    confirm → confirm)
35. `CONDITIONAL, 0.85, !ambig, !toggle, 0.85, 0.85 → Execute` (degenerate equal
    thresholds — execute path wins)
36. `YES, 0.0, ambig, toggle → Confirm` (every precedent points at Confirm)

Each case is one `@Test` (no parameterisation — explicit names like
`group_A_NO_high_confidence_executes` to make a CI failure obvious without a row
lookup).

### `SettingsDataStoreTest` (JVM with Robolectric — ~10 cases)

Use `runTest { … }` and `Robolectric.buildApplication(...)` for the `Context`; or
`androidx.test.platform.app.InstrumentationRegistry` if Robolectric proves
fragile in CI. Tests:

1. `firstRead_returnsDefaults` — fresh DataStore (clear file) → `executeThreshold
   = 0.85`, `confirmThreshold = 0.60`, `alwaysConfirm = false`.
2. `setExecute_roundTrips`.
3. `setConfirm_roundTrips`.
4. `setAlwaysConfirm_roundTrips`.
5. `setExecute_above1f_clampsTo1f`.
6. `setExecute_belowZero_clampsToZero`.
7. `setExecute_belowConfirm_alsoLowersConfirm` (consistency rule).
8. `setConfirm_aboveExecute_clampsToExecute`.
9. `setExecute_emitsToCollectors` — verify two concurrent collectors both observe
   the change (Turbine + `runTest`).
10. `defaults_areReused_acrossProcessRestart` — write the value, "restart" by
    instantiating a fresh `SettingsDataStore(context)`, read → the persisted
    value, not the default. (DataStore's whole point.)

### `AssistantCoordinatorTest` (JVM with fakes — 6 new cases appended)

Append to the existing test class (do not create a new file). The fakes
(`FakeStt`, `FakeTts`, `FakeFunctionCallEngine`, `FakeHandlerDispatcher`,
`FakeTimeProvider`) are already in place; add `FakeSettingsRepository` and
`FakeConfidencePolicy` (or use the real `ConfidencePolicy` with a controlled
`SettingsRepository` — recommend the real policy + a fake repo, since the policy
is pure and the test is then end-to-end).

The 6 cases:
1. `decisionSuccess_callContact_highConfidence_executes` — confidence 0.95, no
   ambiguity, no toggle → FSM goes `Listening → Processing → Executing → Idle`;
   `dispatcher.dispatch` called once; `ttsClient.speak("Llamando a Pepito.")`
   called.
2. `decisionSuccess_callContact_midConfidence_confirms` — confidence 0.72 → FSM
   ends in `Confirming(prompt = "¿Llamo a Pepito?", expiresAtMs = now +
   10_000)`; `dispatcher.dispatch` NOT called yet; `ttsClient.speak` called once
   with the prompt; no transition past `Confirming` in SF-6.1 (SF-6.2 wires the
   rest).
3. `decisionSuccess_callContact_lowConfidence_clarifies` — confidence 0.40 → FSM
   goes `Listening → Processing → ErrorRecovery(copy_clarify_intent, 0) → Idle`;
   `sttFailureCounter.recordFailure` is NOT called.
4. `decisionSuccess_tellTime_lowConfidence_clarifies` — confidence 0.40, NO
   confirm → same path as #3 (verifies the spec §4.3 rule that low confidence
   clarifies regardless of `needs_confirmation`).
5. `decisionSuccess_callContact_highConfidence_emitsPolicyTelemetry` — verify a
   `policy_decided` event with `decision = "execute"`, `confidence_bucket =
   "high"`, `always_confirm_on = false`.
6. `decisionSuccess_callContact_midConfidence_emitsPolicyTelemetry_confirm` —
   verify `decision = "confirm"`, `confidence_bucket = "mid"`.

### `TelemetryGuardrailTest` (JVM — 3 new cases appended)
- [ ] `policy_decided` with all whitelisted props → `Allow`.
- [ ] `policy_decided` with an unknown prop key → `Reject`.
- [ ] `policy_decided` with a long `function_name` (> 32 chars) → `Reject` (PII
      heuristic — guards against accidental param-value leakage).

### UI tests
- [ ] None for SF-6.1 — the only UI surface that changes is the launcher's
      `Confirming` branch routing, but its body is still `Unit` until SF-6.2.
      Verify visually (smoke) that triggering `Confirming` does not crash; no
      assertion needed.

### Manual smoke (Redmi 15)
- [ ] On a real device with `call_contact` (any single contact), say "llama a
      Pepito" clearly → call placed (`Execute` path verified end-to-end).
- [ ] Say "llámame a Pepe" mumbled to drop confidence into the 0.60–0.85 band
      (may need to fake the engine for deterministic results; alternatively use
      a debug-only key in `local.properties` that pins the engine confidence).
      Verify Curro speaks "¿Llamo a Pepe?" and the FSM enters `Confirming`. (The
      overlay UI is not yet built; the launcher home stays as-is. Pin: this is
      acceptable for SF-6.1 — SF-6.2 builds the overlay.)
- [ ] Verify the failure log does not contain `policy_decided` events (telemetry
      goes through the SDK; failure log is separate).

---

## Implementation Notes

**Order of changes (recommended commit sequence within this SF):**
1. `gradle/libs.versions.toml` + `app/build.gradle.kts` — DataStore dependency.
2. `domain/repository/SettingsRepository.kt` — interface.
3. `data/local/SettingsDataStore.kt` + `di/SettingsModule.kt` — impl + binding.
4. `SettingsDataStoreTest` — DataStore correctness.
5. `assistant/ConfidencePolicy.kt` + `PolicyInputs` + `ConfidenceDecision`.
6. `ConfidencePolicyTest` — the 36 cases.
7. `AssistantEvent.LowConfidenceClarify` + the `AssistantStateMachine` branch.
8. FSM tests for the new transition (positive + negatives).
9. `AssistantCoordinator` — the `onDecisionSuccess` rewrite + `clarify` helper +
   `buildConfirmPrompt` + the telemetry helper. Remove the auto-recurse in
   `renderHandlerResult`'s `NeedsConfirmation` branch (replace with the same
   `Confirming` transition).
10. 6 new `AssistantCoordinatorTest` cases.
11. `TelemetryGuardrail` whitelist + 3 new fixture cases.
12. `strings.xml` — `copy_clarify_intent`.

**Pinning for the implementer:**
- The `Fase1Catalog.functions.firstOrNull { it.name == call.action }` lookup is
  fine for Phase 6 — `Fase1Catalog` is a singleton `object` with 7 entries; the
  linear search is sub-microsecond. A future SF that lifts the catalog into a
  repository (e.g. for dynamic phase gating) can swap this without touching the
  policy.
- The 10-s confirmation timeout constant lives on the coordinator companion
  (`CONFIRM_TIMEOUT_MS = 10_000L`), not on the policy. The policy is timer-free
  on purpose. SF-6.2's timer reads this constant.
- The implementer must verify the `AssistantStateMachine.computeNext` `else`
  branch for the new event throws `IllegalAssistantTransition` — this is the
  established convention (Phase 5).

---

## Revision History

| Date | Author | Change |
|---|---|---|
| 2026-05-16 | android-product-analyst (Opus) | Initial brief — Phase 6 PM batch. |
