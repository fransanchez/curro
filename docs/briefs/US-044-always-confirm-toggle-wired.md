# Brief — US-044 / SF-6.4: "Always confirm" toggle wired into `ConfidencePolicy`

## Metadata

| Field | Value |
|---|---|
| **Feature** | Plumb `SettingsRepository.alwaysConfirm` from DataStore through the coordinator into `ConfidencePolicy` |
| **US ID** | US-044 |
| **SF ID** | SF-6.4 |
| **Phase** | 6 — Confidence-graded confirmation |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst (Opus) |
| **Implementer** | voice-pipeline-engineer (Opus) |
| **Size** | S |
| **Depends on** | US-041 (SF-6.1 — the policy + the DataStore-backed key) |
| **Unblocks** | Phase 8 (SF-8.x — the settings menu UI that toggles this key) |

---

## Summary

SF-6.1 introduced the `always_confirm` DataStore key (default `false`) and
defined `ConfidencePolicy`'s precedence rule: when the user has "always
confirm" on, every `CONDITIONAL` function escalates to `Confirm` regardless
of confidence. SF-6.1 plumbed the flag through `PolicyInputs` but the
coordinator hard-coded `alwaysConfirmToggle = false`.

SF-6.4 removes that hard-code: the coordinator reads
`settingsRepository.alwaysConfirm.first()` once per turn and passes the actual
value into `PolicyInputs`. After SF-6.4, the always-confirm semantics work
end-to-end — the only missing piece is the **UI toggle**, which lands in
Phase 8 (the config menu). Until then, the only way to flip the flag is via
a developer affordance (`SettingsDataStore.setAlwaysConfirm(true)` from a
test or a debug-only utility — see "Pin: developer affordance").

User benefit: when Phase 8 ships the toggle, Fran will be able to enable
"always confirm" for the first weeks of his father's usage — and the policy
will start escalating every `call_contact` regardless of confidence. Phase 6
ensures this is a flip-a-flag affair, not a behavioural rewrite.

Spec source: §4.3 (always-escalate case #3), §9 ("Confirma siempre: toggle
que fuerza confirmación para toda acción `conditional`, ignorando la
confianza"), `function-catalog` rule 3.

---

## Scope

### In scope

- Modify `AssistantCoordinator`:
  - Inject `SettingsRepository` (already injected in SF-6.1 for the
    thresholds; SF-6.4 adds `alwaysConfirm` to the per-turn read).
  - In `onDecisionSuccess`, replace `alwaysConfirmToggle = false` with
    `alwaysConfirmToggle = settingsRepository.alwaysConfirm.first()`.
  - Propagate the value into the `policy_decided` telemetry event's
    `always_confirm_on` prop (already wired in SF-6.1 to `false` — SF-6.4
    propagates the real value).

- 2 new `AssistantCoordinatorTest` cases that drive the always-confirm flag
  through DataStore:
  - With `alwaysConfirm = false`, `call_contact` confidence 0.95 → `Execute`
    (the SF-6.1 path, now verified through the real DataStore read).
  - With `alwaysConfirm = true`, `call_contact` confidence 0.95 → `Confirm`
    (the actual SF-6.4 behaviour).

- 2 new `SettingsDataStoreTest` cases (or augment existing ones from SF-6.1):
  - `setAlwaysConfirm(true)` round-trips (verify against the persisted file).
  - The default is `false`.
  (Note: SF-6.1's brief already lists "set/get round-trip" and "defaults" for
  this key — verify the SF-6.1 tests cover this; if not, SF-6.4 adds them.
  Pin: SF-6.4's test list is the minimum additional coverage.)

### Out of scope

- **The toggle's UI in the config menu** — that's Phase 8's SF-8.x.
- A debug-build-only "tap-and-hold the clock for 5 s" affordance to flip the
  toggle without the menu — pinned out of Phase 6.
- Any other settings key (TTS rate, incoming-call assistant toggle, send-
  failures-to-Fran toggle) — those land in Phase 8.

---

## User Flows

### Flow 1 — `always_confirm = false` (default)

Same as US-041 / SF-6.1's Flow 1 (high-confidence path): `call_contact`
confidence 0.95 → `Execute` → call placed without confirmation.

### Flow 2 — `always_confirm = true`

1. Fran flips `always_confirm = true` (Phase 8 UI; meanwhile via developer
   affordance — see below).
2. Father presses mic → "Llama a Pepito" → STT → `Processing` →
   `FunctionGemma` returns `confidence = 0.95`.
3. `ConfidencePolicy.decide(...)` receives `alwaysConfirmToggle = true` →
   returns `Confirm` (even though `confidence ≥ executeThreshold`).
4. FSM → `Confirming` → SF-6.2's overlay paints. User taps SÍ or NO.
5. Outcomes identical to SF-6.2's confirmation flow.

### Flow 3 — `always_confirm = true` does NOT affect `NO`-confirmation functions

- "Qué hora es" with `confidence = 0.95` and `always_confirm = true` →
  `Execute` (tell_time is `NO`-confirm; the policy precedence is
  ambiguous → YES-confirm → low-confidence-clarify → NO-execute, etc.; the
  `alwaysConfirmToggle` only fires inside the `CONDITIONAL` branch). Verify
  this is the SF-6.1 policy precedence; it is.

### Flow 4 — `always_confirm = true` does NOT override a low-confidence clarify

- "Mmm llama mfh" with `confidence = 0.40` and `always_confirm = true` →
  `Clarify` (the SF-6.1 precedence rule "confidence < confirmThreshold →
  Clarify" fires before the toggle check). Pinned by SF-6.1 case #23 in the
  ConfidencePolicyTest table — the toggle does NOT escalate clarifies to
  confirms.

---

## Function-catalog Impact

**No catalog changes.**

---

## FSM States Touched

**No FSM changes.** SF-6.4 only changes a single primitive value the
coordinator passes into the policy. The FSM transitions, events, and states
are all unchanged from SF-6.2.

---

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none new) | — | — | — |

No new system APIs touched.

---

## On-device-model Impact

**No model impact.** The policy still runs in the same place with the same
inputs; only one input value changes (from a constant `false` to the
DataStore-backed Boolean).

Latency impact: the second `.first()` on the `Flow<Boolean>` adds < 1 ms warm,
~2 ms cold. Negligible.

---

## Android Specification

### Files modified

| Path | Change |
|---|---|
| `app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt` | In the `onDecisionSuccess` body (where SF-6.1 built `PolicyInputs`): replace `alwaysConfirmToggle = false` with `alwaysConfirmToggle = settingsRepository.alwaysConfirm.first()`. Propagate to the `policy_decided` telemetry event. |
| (no other source files) | — |

### `AssistantCoordinator` diff

**Before (SF-6.1):**
```kotlin
val inputs = PolicyInputs(
    needsConfirmation = catalogFunction.needsConfirmation,
    confidence = call.confidence,
    isAmbiguous = false,
    alwaysConfirmToggle = false,                                  // ← SF-6.1 hard-code
    executeThreshold = settingsRepository.executeThreshold.first(),
    confirmThreshold = settingsRepository.confirmThreshold.first(),
)
```

**After (SF-6.4):**
```kotlin
val alwaysConfirm = settingsRepository.alwaysConfirm.first()
val inputs = PolicyInputs(
    needsConfirmation = catalogFunction.needsConfirmation,
    confidence = call.confidence,
    isAmbiguous = false,
    alwaysConfirmToggle = alwaysConfirm,
    executeThreshold = settingsRepository.executeThreshold.first(),
    confirmThreshold = settingsRepository.confirmThreshold.first(),
)
```

The telemetry call already includes `always_confirm_on` (SF-6.1) — passing
the captured `alwaysConfirm` value into `emitPolicyTelemetry(...)`:

```kotlin
emitPolicyTelemetry(call.action, decision, call.confidence, alwaysConfirm)
```

### Pin: developer affordance

Since the toggle's UI ships in Phase 8, SF-6.4 leaves no in-app way to flip
the flag. For manual smoke on the Redmi 15, the implementer adds **one of**:

A. A debug-only ADB shell command: `am broadcast -a com.curro.app.DEBUG_TOGGLE_AC`
   that flips the flag. Implementation: a `BroadcastReceiver` only registered
   in the debug variant via `BuildConfig.DEBUG` check; calls
   `settingsRepository.setAlwaysConfirm(!current)`. **Pin: PREFER this**
   because it adds no UI surface and the broadcast receiver auto-disappears
   in release.

B. A hidden gesture (e.g. long-press the mic button for 5 s in debug builds
   only). Pin: REJECT — risks confusing the user during manual smoke.

C. A `local.properties` boolean that overrides the DataStore value at app
   start (read in `SettingsDataStore` constructor). Pin: REJECT — couples the
   storage layer to a build-time flag and breaks the "DataStore is the truth"
   model.

The implementer should ship affordance A as a tiny debug-only file:

```kotlin
// app/src/debug/java/com/curro/app/debug/AlwaysConfirmToggleReceiver.kt
class AlwaysConfirmToggleReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        scope.launch {
            val current = settings.alwaysConfirm.first()
            settings.setAlwaysConfirm(!current)
            Log.i("Curro/Debug", "alwaysConfirm flipped to ${!current}")
        }
    }
}
```

Registered in `app/src/debug/AndroidManifest.xml`:

```xml
<receiver
    android:name="com.curro.app.debug.AlwaysConfirmToggleReceiver"
    android:exported="true"
    android:enabled="true">
    <intent-filter>
        <action android:name="com.curro.app.DEBUG_TOGGLE_AC"/>
    </intent-filter>
</receiver>
```

(Hilt-injecting a `BroadcastReceiver` requires `@AndroidEntryPoint` and
boilerplate; the implementer may opt for the simpler `EntryPointAccessors`
pattern. Pin: implementer's choice; both work; document the chosen pattern
in the file's Kdoc.)

`exported="true"` is safe in the debug variant only — the receiver does not
exist in the release APK.

The affordance is **not** an acceptance criterion (no automated test); it
exists to enable Manual Smoke. The implementer may skip it if they have
another way to drive `setAlwaysConfirm(true)` during smoke (e.g. running a
Robolectric-driven shell from `connectedAndroidTest`).

### Strings

**No new strings.** SF-6.4 changes no user-visible copy.

---

## Acceptance Criteria

### Build & static checks
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug` green.
- [ ] No new permissions, no new manifest entries (other than the optional
      debug-variant `BroadcastReceiver` — strictly debug-only).
- [ ] No new strings; no new dependencies.

### Coordinator correctness
- [ ] With `settingsRepository.alwaysConfirm` emitting `false`:
      `call_contact` confidence 0.95 → `Execute` (FSM `Listening → Processing
      → Executing → Idle`, single-match call placed; no `Confirming` entry).
- [ ] With `settingsRepository.alwaysConfirm` emitting `true`: `call_contact`
      confidence 0.95 → `Confirm` (FSM `Listening → Processing → Confirming`;
      `pendingAction` carries the regular yes/no kind).
- [ ] With `alwaysConfirm = true`, `tell_time` confidence 0.95 → `Execute`
      (the toggle does NOT affect `NO`-confirmation functions).
- [ ] With `alwaysConfirm = true`, `call_contact` confidence 0.40 → `Clarify`
      (the toggle does NOT escalate low-confidence to confirm; the SF-6.1
      precedence rule still applies).
- [ ] `policy_decided` telemetry event's `always_confirm_on` prop reflects
      the actual flag value (not always `false`).

### `SettingsDataStore` correctness (already covered in SF-6.1 — verify)
- [ ] `setAlwaysConfirm(true)` round-trips.
- [ ] Default is `false`.

### Regression
- [ ] Every SF-6.1, SF-6.2, SF-6.3 test still passes.
- [ ] Existing handler tests pass (handlers do not read settings — no
      coupling).

### Manual smoke (Redmi 15)
- [ ] Default state: "llama a Pepito" with single-match → call placed
      (Execute path).
- [ ] Fire the debug broadcast: `adb shell am broadcast -a
      com.curro.app.DEBUG_TOGGLE_AC`. Verify the Logcat line "alwaysConfirm
      flipped to true".
- [ ] "Llama a Pepito" again → `Confirming` overlay appears. Tap SÍ → call
      placed.
- [ ] Fire the broadcast again → back to false; "llama a Pepito" goes direct.

---

## Senior-UX & Copy

**No UX surface changes in SF-6.4.** The toggle's user-facing effect is the
same `Confirming` overlay SF-6.2 built — its copy (`copy_confirm_call`,
SÍ/NO buttons, `copy_calling_confirmed`, etc.) is unchanged.

The toggle itself ships its UI in Phase 8. SF-6.4's brief does not specify
the menu UI; that's Phase 8's PM batch.

---

## Performance Considerations

- Adding a third `.first()` per turn (alongside `executeThreshold.first()`
  and `confirmThreshold.first()`) is a constant-time read against the
  in-memory DataStore replay cache. Budget: < 0.5 ms per turn.
- No memory impact.

---

## Testing Requirements

### `AssistantCoordinatorTest` — 2 new cases

(Append; do not replace SF-6.1's or SF-6.2's.)

21. `alwaysConfirmFalse_callContactHighConfidence_executes` — set
    `FakeSettingsRepository.alwaysConfirmValue = false`; drive coordinator
    with a `call_contact` confidence 0.95 result; assert FSM ends in `Idle`
    via `Executing`, `dispatcher.dispatch` called once, no `Confirming`
    state observed.
22. `alwaysConfirmTrue_callContactHighConfidence_confirms` — set
    `FakeSettingsRepository.alwaysConfirmValue = true`; same input; assert
    FSM ends in `Confirming` after `onDecisionSuccess` returns;
    `dispatcher.dispatch` NOT yet called; `pendingAction.kind` is `YesNo`.

The `FakeSettingsRepository` introduced in SF-6.1 needs an
`alwaysConfirmValue: Boolean` mutable property that backs the Flow — pin:
SF-6.4 does not introduce the fake (SF-6.1 did); SF-6.4 only adds two test
methods that flip the existing property.

### `SettingsDataStoreTest` — 2 cases (verify or add)

23. `setAlwaysConfirm_true_roundTrips`.
24. `alwaysConfirm_defaultsToFalse`.

Both are listed in SF-6.1's test plan. If SF-6.1's implementation covers them
(it should — they're in SF-6.1's AC list), SF-6.4 does not duplicate. If
SF-6.1's implementation skipped them for any reason, SF-6.4 adds them.

### Manual smoke

Covered in Acceptance Criteria above.

---

## Implementation Notes

**Order of changes within this SF:**
1. `AssistantCoordinator.onDecisionSuccess` — capture and propagate
   `alwaysConfirm`.
2. 2 new `AssistantCoordinatorTest` cases.
3. (Optional but recommended) Debug-variant `BroadcastReceiver` for manual
   smoke. The implementer may inline this in a separate commit since it is
   debug-only and adds no production code.
4. Manual smoke on Redmi 15.

**Pin: SF-6.4 is intentionally small.** The architectural work was done in
SF-6.1 (policy + DataStore + telemetry whitelist). SF-6.4 is the "remove the
hard-code" patch — one line of source + two tests. The size = S reflects this.

**Pin: do not introduce the toggle UI here.** Phase 8 owns the UI. If during
implementation the implementer feels tempted to add a quick toggle to the
existing `ConfigMenuPlaceholderScreen` (which is still a Phase-0 placeholder),
resist — Phase 8's PM batch will design the full settings menu shape and adding
ad-hoc toggles now risks reshape later.

---

## Revision History

| Date | Author | Change |
|---|---|---|
| 2026-05-16 | android-product-analyst (Opus) | Initial brief — Phase 6 PM batch. |
