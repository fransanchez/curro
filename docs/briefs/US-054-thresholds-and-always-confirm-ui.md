# Confidence threshold sliders + "always confirm" toggle UI — US-054 / SF-8.5

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Two confidence sliders + the "Confirma siempre" toggle surfaced in the config menu |
| **US ID** | US-054 (master-plan SF-8.5) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

## Summary

Replace SF-8.1's `config/thresholds` placeholder with `ThresholdsScreen` —
two sliders for `executeThreshold` (default `0.85`) and `confirmThreshold`
(default `0.60`) with the invariant `confirmThreshold < executeThreshold`
enforced via a dynamic-max on the confirm slider (its `valueRange` upper
bound is the current execute value), the "Confirma siempre" toggle (already
wired through `ConfidencePolicy` by SF-6.4), and a "Volver a los valores por
defecto" button. The setters already exist (SF-6.1) and already clamp at
the DataStore layer; this SF is mostly UI. **Pin: SF-8.5 is the lightest of
the Phase-8 SFs because the backend was built ahead** (SF-6.1 + SF-6.4
deliberately landed before this so Phase 8 would be pure UI).

Spec reference: `docs/curro-spec-v1.0.md` §4.3 + §9.

## Scope

- **In Scope**:
  - `ThresholdsScreen` + `ThresholdsViewModel`.
  - 7 new strings.
  - Replacement of the `composable("config/thresholds")` placeholder.
- **Out of Scope**:
  - Any change to `ConfidencePolicy` (SF-6.1's logic stays).
  - Any change to `SettingsRepository` (the 3 flows + 3 setters exist).
  - A "test this threshold with a fake utterance" affordance.
  - Per-function thresholds (out of scope; a future Phase-X feature).
  - Telemetry for threshold changes.

## User Flows

### Flow 1: Fran observes Curro over-confirming and lowers `executeThreshold`

1. Fran opens config → "Cuándo confirmar antes de actuar".
2. `ThresholdsScreen` renders. Execute slider at 0.85; confirm slider at 0.60;
   "Confirma siempre" off.
3. Fran moves the execute slider to 0.75. The slider's `onValueChange` calls
   `settingsRepo.setExecuteThreshold(0.75f)`.
4. Confirm slider's `valueRange` upper bound was 0.85, now 0.75; if confirm
   was already < 0.75 it stays (the existing setter doesn't clamp when not
   needed).
5. Help line under execute updates: "Si Curro está muy seguro (por encima de
   75 %) actúa directamente."
6. Fran asks his father to say "llama a Lucía"; Curro returns confidence
   0.78 (formerly would have asked to confirm — now executes directly).

### Flow 2: Fran moves execute below current confirm — auto-clamp

1. Fran moves execute from 0.85 down to 0.55. Confirm is at 0.60.
2. The setter `setExecuteThreshold(0.55f)` clamps confirm to 0.55 (existing
   logic in `SettingsDataStore.setExecuteThreshold`).
3. The screen re-renders: execute at 0.55, confirm at 0.55, confirm slider's
   max now 0.55.
4. Help lines update.

### Flow 3: Fran flips "Confirma siempre" on

1. Fran taps the toggle. `setAlwaysConfirm(true)` fires.
2. `ConfidencePolicy` (already wired by SF-6.4) now confirms every
   `conditional` action regardless of confidence.
3. Fran asks his father to say "llama a Lucía" — even at confidence 0.94,
   Curro asks "¿Llamo a Lucía?".

### Flow 4: Reset to defaults

1. Fran taps "Volver a los valores por defecto".
2. VM calls (in order): `setExecuteThreshold(0.85f)`, then
   `setConfirmThreshold(0.60f)`, then `setAlwaysConfirm(false)`.
3. **Pin: order matters** — setting `execute` first lifts the ceiling so the
   subsequent `setConfirmThreshold(0.60)` is not clamped. If we set confirm
   first, and current execute is 0.50, the setter clamps confirm to 0.50.
4. Screen re-renders with defaults visible.

## Function-catalog Impact

No catalog change.

## FSM States Touched

None directly. The downstream behavioural change (the `ConfidencePolicy`
result) is already wired through `AssistantCoordinator` (SF-6.1 / SF-6.2);
SF-8.5 is the UI for changing the inputs.

## Android System Integrations & Permissions

No new integrations, no new permissions.

## On-device-model Impact

No model impact. The thresholds influence the policy that runs ON the
model's output, not the model itself.

## Android Specification

### Screens and Composables

- **`presentation/config/sections/thresholds/ThresholdsScreen.kt`** —
  `@Composable fun ThresholdsScreen(onBack: () -> Unit, viewModel: ThresholdsViewModel = hiltViewModel())`.
  - Layout:
    ```kotlin
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = Dimens.MinTapTarget + CurroSpacing.l, start = CurroSpacing.l, end = CurroSpacing.l).verticalScroll(rememberScrollState())) {
            // Execute threshold
            Text(stringResource(R.string.copy_config_thresholds_execute_label, (uiState.executeThreshold * 100).toInt()), style = bodyLarge)
            Slider(value = uiState.executeThreshold, onValueChange = { viewModel.onEvent(ThresholdsEvent.SetExecute(it)) }, valueRange = 0.5f..1.0f, steps = 49)
            Text(stringResource(R.string.copy_config_thresholds_execute_help, (uiState.executeThreshold * 100).toInt()), style = bodyMedium, color = onSurfaceVariant)
            Spacer(Modifier.height(CurroSpacing.l))

            // Confirm threshold — dynamic max from execute
            Text(stringResource(R.string.copy_config_thresholds_confirm_label, (uiState.confirmThreshold * 100).toInt()), style = bodyLarge)
            Slider(value = uiState.confirmThreshold, onValueChange = { viewModel.onEvent(ThresholdsEvent.SetConfirm(it)) }, valueRange = 0f..uiState.executeThreshold, steps = ((uiState.executeThreshold * 100).toInt() - 1).coerceAtLeast(0))
            Text(stringResource(R.string.copy_config_thresholds_confirm_help, (uiState.confirmThreshold * 100).toInt()), style = bodyMedium, color = onSurfaceVariant)
            Spacer(Modifier.height(CurroSpacing.xl))

            // Always confirm toggle
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { viewModel.onEvent(ThresholdsEvent.SetAlwaysConfirm(!uiState.alwaysConfirm)) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.copy_config_thresholds_always_label), style = bodyLarge)
                    Text(stringResource(R.string.copy_config_thresholds_always_help), style = bodyMedium, color = onSurfaceVariant)
                }
                Switch(checked = uiState.alwaysConfirm, onCheckedChange = { viewModel.onEvent(ThresholdsEvent.SetAlwaysConfirm(it)) })
            }
            Spacer(Modifier.height(CurroSpacing.xl))

            // Reset
            BigPrimaryButton(text = stringResource(R.string.copy_config_thresholds_reset), onClick = { viewModel.onEvent(ThresholdsEvent.ResetDefaults) })
        }
        IconButton(onClick = onBack, /* TopStart back chevron — same pattern */)
    }
    ```

### ViewModels and State Management

```kotlin
@HiltViewModel
class ThresholdsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<ThresholdsUiState> = combine(
        settingsRepo.executeThreshold,
        settingsRepo.confirmThreshold,
        settingsRepo.alwaysConfirm,
    ) { e, c, a -> ThresholdsUiState(executeThreshold = e, confirmThreshold = c, alwaysConfirm = a) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThresholdsUiState(0.85f, 0.60f, false))

    fun onEvent(event: ThresholdsEvent) {
        when (event) {
            is ThresholdsEvent.SetExecute -> viewModelScope.launch { settingsRepo.setExecuteThreshold(event.value) }
            is ThresholdsEvent.SetConfirm -> viewModelScope.launch { settingsRepo.setConfirmThreshold(event.value) }
            is ThresholdsEvent.SetAlwaysConfirm -> viewModelScope.launch { settingsRepo.setAlwaysConfirm(event.value) }
            ThresholdsEvent.ResetDefaults -> viewModelScope.launch {
                // Pin: execute MUST be reset first — see brief.
                settingsRepo.setExecuteThreshold(DEFAULT_EXECUTE)
                settingsRepo.setConfirmThreshold(DEFAULT_CONFIRM)
                settingsRepo.setAlwaysConfirm(false)
            }
        }
    }

    private companion object {
        const val DEFAULT_EXECUTE = 0.85f
        const val DEFAULT_CONFIRM = 0.60f
    }
}

data class ThresholdsUiState(val executeThreshold: Float, val confirmThreshold: Float, val alwaysConfirm: Boolean)

sealed interface ThresholdsEvent {
    data class SetExecute(val value: Float) : ThresholdsEvent
    data class SetConfirm(val value: Float) : ThresholdsEvent
    data class SetAlwaysConfirm(val value: Boolean) : ThresholdsEvent
    data object ResetDefaults : ThresholdsEvent
}
```

### Navigation Routes

- **MODIFIED**: replace `composable("config/thresholds")` placeholder with
  the real `ThresholdsScreen`.

### Hilt Modules

No new module. `ThresholdsViewModel` uses the existing `SettingsRepository`
binding.

### Composables by Feature (checklist)

- [x] `ThresholdsScreen` + stateless `ThresholdsContent`.
- [x] `LabeledSlider` (optional helper if extracted).
- [x] `AlwaysConfirmToggleRow` (optional helper if extracted).
- [x] Dark + large-font previews.

### Material Design Components

- `Slider` (Material 3) — 50 ticks on execute (0.5..1.0 / 49 steps); dynamic
  ticks on confirm.
- `Switch` for "Confirma siempre".
- `BigPrimaryButton` for "Volver a los valores por defecto".

## Acceptance Criteria

- [ ] **Sliders render with current values** — fresh install: execute 0.85,
      confirm 0.60.
- [ ] **Moving execute below current confirm clamps confirm visually** —
      the setter already clamps; the UI re-renders correctly because both
      flows are in the `combine`.
- [ ] **Confirm slider's max = current execute** — dynamic `valueRange`.
- [ ] **Always confirm toggle wires through `ConfidencePolicy`** — verified
      by speaking a high-confidence call with the toggle on and observing
      Curro confirms.
- [ ] **Reset to defaults** sets execute=0.85 first, then confirm=0.60,
      then alwaysConfirm=false. Order verified by test.
- [ ] **7 new strings** with the right IDs.
- [ ] **No new permissions, no new manifest entries, no new DataStore keys,
      no new dependencies, no new telemetry event.**
- [ ] **Build is green**.

## Design Notes

- The slider tick steps are integer percent (50 ticks on execute, dynamic
  on confirm). The display shows integer-percent (`(value * 100).toInt()`)
  — fine motor control is not a concern (Fran is operating this) but
  rounding to percent is the cleanest read.
- The help lines use the live integer-percent value so the cause-effect is
  obvious without re-reading the slider position.
- The "Confirma siempre" row uses a 72 dp `heightIn` with the help line
  visible (not collapsed into a tap-to-expand) — Fran needs to see the
  consequence at a glance.

## Senior-UX & Copy

Fran-only — config-menu density.

**No new spoken (TTS) strings.**

New entries in `app/src/main/res/values/strings.xml` (7 total):

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_thresholds_execute_label` | "Cuándo Curro actúa directamente: %1$d %%" | execute slider label |
| `copy_config_thresholds_confirm_label` | "Cuándo Curro pregunta antes: %1$d %%" | confirm slider label |
| `copy_config_thresholds_execute_help` | "Si Curro está muy seguro (por encima de %1$d %%) actúa directamente." | execute help |
| `copy_config_thresholds_confirm_help` | "Si está dudoso (por debajo de %1$d %%) te pregunta para aclarar." | confirm help |
| `copy_config_thresholds_always_label` | "Confirma siempre" | toggle label |
| `copy_config_thresholds_always_help` | "Si está activado, Curro pregunta antes de llamar incluso cuando está seguro." | toggle help |
| `copy_config_thresholds_reset` | "Volver a los valores por defecto" | reset button |

**`brand-design` COPY table**: add a "Confidence thresholds (Phase 8 —
SF-8.5)" section with all 7 rows.

## Performance Considerations

- `combine` of 3 flows is trivial.
- Slider's `onValueChange` fires on every drag-tick; each call dispatches
  to `viewModelScope.launch { settingsRepo.set...(value) }`. The DataStore
  write is buffered; this is the canonical Material Slider + DataStore
  pattern. **Pin: do NOT debounce** — the DataStore handles the throughput;
  the FSM picks up the new value on the next assistant turn anyway.

## Testing Requirements

- [ ] **FSM**: N/A.
- [ ] **`ThresholdsViewModel`** (6 cases) — JVM + Turbine + `FakeSettingsRepository`:
      1. `uiState_emitsDefaults_0_85_and_0_60_and_false`.
      2. `onSetExecute_callsRepoSetter_andClampsConfirmIfNeeded_viaRepo`.
      3. `onSetConfirm_callsRepoSetter`.
      4. `onSetAlwaysConfirm_callsRepoSetter`.
      5. `onResetDefaults_callsExecuteSetterFirst_then_ConfirmSetter_then_AlwaysConfirmFalse_inOrder`.
      6. `uiState_reflects_repoEmission_after_setExecute_to_0_55_clampsConfirm_to_0_55`.
- [ ] **Instrumented UI tests on `ThresholdsContent`** (4 cases):
      1. `bothSliders_render_with_currentValues`.
      2. `movingExecuteSlider_below_currentConfirm_clampsConfirm_visually`.
      3. `alwaysConfirmSwitch_toggles_andFiresEvent`.
      4. `resetButton_fires_ResetDefaults_event`.
- [ ] **Dark + large-font previews**.
- [ ] **Real Redmi 15 smoke**:
      - Lower `executeThreshold` to 0.75 → speak a call with mid-confidence;
        Curro now executes where it used to confirm.
      - Flip "Confirma siempre" on → a high-confidence call still goes
        through confirmation.
      - Reset → defaults restored.

## Implementation Notes

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/presentation/config/sections/thresholds/ThresholdsScreen.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/thresholds/ThresholdsViewModel.kt`
- `app/src/test/java/com/curro/app/presentation/config/sections/thresholds/ThresholdsViewModelTest.kt`
- `app/src/androidTest/java/com/curro/app/presentation/config/sections/thresholds/ThresholdsContentTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
  (1 swap).
- `app/src/main/res/values/strings.xml` (+7 entries).
- `.claude/skills/brand-design/SKILL.md` (+7 rows).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.5 Confidence thresholds + always-confirm UI. |
