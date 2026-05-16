# Reset learning — US-058 / SF-8.9

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Destructive button + confirmation to wipe everything Curro has learned |
| **US ID** | US-058 (master-plan SF-8.9) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

## Summary

Replace SF-8.1's `config/reset` placeholder with `ResetScreen` — one big red
destructive button, one confirmation dialog, and `awaitAll`-orchestrated
deletion of:
1. `contact_aliases` (via `AliasRepository.deleteAll`).
2. `app_usage` (via `AppUsageDao.deleteAll`).
3. `failed_commands` (via `FailedCommandLog.deleteAll`).
4. `launcher_favourites_override` (via `settingsRepo.setLauncherFavouritesOverride(null)`).

**Pin: settings Fran tuned (thresholds, TTS voice/rate/pitch, the two
toggles) are NOT reset.** Those are preferences, not learned data; reset
learning preserves them.

`local-data` rule 6 says "Reset learning" is destructive — confirm twice if
you want; never run it silently. SF-8.9 ships a single confirmation (one
confirm is canonical per `local-data`'s guidance + the `master-plan` risk
c — "confirm twice if you want; never run it silently"); pin one confirm,
clear copy.

Spec reference: `docs/curro-spec-v1.0.md` §9 ("Reset de aprendizaje") +
`local-data` rule 6.

## Scope

- **In Scope**:
  - `ResetScreen` + `ResetViewModel`.
  - The destructive confirmation dialog.
  - The 4-way parallel delete orchestration.
  - 7 new strings.
  - 1 new telemetry event (`learning_reset`, no props).
  - Replacement of the `composable("config/reset")` placeholder.
- **Out of Scope**:
  - Resetting any setting (thresholds / TTS / toggles) — preserved.
  - A "selectively reset" affordance (out of scope; if Fran wants finer
    control, he uses SF-8.2 to manage aliases, SF-8.3 for favourites,
    SF-8.6 to clear failures).
  - Granting "Reset learning" as a Curro voice command (out of scope —
    Fran-only).

## User Flows

### Flow 1: Fran resets after a wrong-learned alias couldn't be fixed easily

1. Fran opens config → "Reset de aprendizaje".
2. `ResetScreen` renders: title, long explainer, red button "Borrar todo el
   aprendizaje".
3. Fran taps the button → confirmation `AlertDialog` opens:
   - Title: "¿Seguro?"
   - Body: "Esto borra los alias y las favoritas aprendidas. No se puede
     deshacer."
   - "Sí, borrar" (red) / "Mejor no".
4. Fran taps "Sí, borrar" → VM orchestrates the 4 parallel deletes via
   `awaitAll`.
5. On completion → snackbar / toast: "Listo, todo borrado."
6. Telemetry event: `learning_reset` (no props).
7. Fran goes back to the launcher home → grid shows the 4 seed tiles
   (WhatsApp / Llamadas / Cámara / Fotos) — the recency table is empty,
   the override is null, so SF-7.4's seed-padding takes over.
8. Open Aliases section → empty.
9. Open Failures section → empty.

### Flow 2: Fran taps Reset by accident

1. Same as above through step 3.
2. Fran taps "Mejor no" → dialog dismisses; nothing happens.

### Flow 3: Concurrent reset (defensive)

1. Fran taps "Sí, borrar"; while the 4 deletes are in flight, he taps
   again somehow (unlikely, but pin defensive behaviour).
2. The VM has a `MutableStateFlow<Boolean>` `isResetting` that disables
   the button while in flight; a second tap is a no-op.

## Function-catalog Impact

No catalog change.

## FSM States Touched

None.

## Android System Integrations & Permissions

No new integrations, no new permissions.

## On-device-model Impact

No model impact (FunctionGemma's prompt context will pick up the now-empty
alias list on the next turn — but that's downstream).

## Android Specification

### Screens and Composables

- **`presentation/config/sections/reset/ResetScreen.kt`** —
  `@Composable fun ResetScreen(onBack: () -> Unit, viewModel: ResetViewModel = hiltViewModel())`.
  - Layout:
    ```kotlin
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = Dimens.MinTapTarget + CurroSpacing.l, start = CurroSpacing.l, end = CurroSpacing.l, bottom = CurroSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.copy_config_section_reset), style = headlineSmall)
            Spacer(Modifier.height(CurroSpacing.l))
            Text(stringResource(R.string.copy_config_reset_explainer), style = bodyMedium)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { viewModel.onEvent(ResetEvent.RequestReset) },
                modifier = Modifier.heightIn(min = Dimens.MinTapTarget).fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                enabled = !uiState.isResetting,
            ) {
                Text(stringResource(R.string.copy_config_reset_button), style = labelLarge)
            }
        }
        if (uiState.showConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(ResetEvent.DismissConfirm) },
                title = { Text(stringResource(R.string.copy_config_reset_confirm_title)) },
                text = { Text(stringResource(R.string.copy_config_reset_confirm)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.onEvent(ResetEvent.ConfirmReset) }) {
                        Text(stringResource(R.string.copy_config_reset_confirm_yes), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onEvent(ResetEvent.DismissConfirm) }) {
                        Text(stringResource(R.string.copy_config_reset_confirm_no))
                    }
                },
            )
        }
        IconButton(onClick = onBack, /* TopStart back chevron */)
    }
    // Snackbar / toast for "Listo, todo borrado." — collect via LaunchedEffect on uiEvents.
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event -> when (event) {
            ResetUiEvent.ResetDone -> { /* show toast via the launcher bus or local Toast */ }
        } }
    }
    ```

### ViewModels and State Management

```kotlin
@HiltViewModel
class ResetViewModel @Inject constructor(
    private val aliasRepo: AliasRepository,
    private val appUsageDao: AppUsageDao,
    private val failedLog: FailedCommandLog,
    private val settingsRepo: SettingsRepository,
    private val telemetry: TelemetrySink,
    private val sideEffectBus: LauncherSideEffectBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val showConfirm = MutableStateFlow(false)
    private val isResetting = MutableStateFlow(false)

    val uiState: StateFlow<ResetUiState> = combine(showConfirm, isResetting) { c, r ->
        ResetUiState(showConfirm = c, isResetting = r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResetUiState(false, false))

    private val _uiEvents = MutableSharedFlow<ResetUiEvent>()
    val uiEvents: SharedFlow<ResetUiEvent> = _uiEvents.asSharedFlow()

    fun onEvent(event: ResetEvent) {
        when (event) {
            ResetEvent.RequestReset -> showConfirm.value = true
            ResetEvent.DismissConfirm -> showConfirm.value = false
            ResetEvent.ConfirmReset -> performReset()
        }
    }

    private fun performReset() {
        if (isResetting.value) return  // defensive
        viewModelScope.launch(ioDispatcher) {
            isResetting.value = true
            showConfirm.value = false
            try {
                val a = async { aliasRepo.deleteAll() }
                val u = async { appUsageDao.deleteAll() }
                val f = async { failedLog.deleteAll() }
                val s = async { settingsRepo.setLauncherFavouritesOverride(null) }
                awaitAll(a, u, f, s)
                telemetry.emit("learning_reset", emptyMap())
                _uiEvents.emit(ResetUiEvent.ResetDone)
                sideEffectBus.publish(LauncherSideEffect.ShowToast(R.string.copy_config_reset_done))
            } finally {
                isResetting.value = false
            }
        }
    }
}

data class ResetUiState(val showConfirm: Boolean, val isResetting: Boolean)

sealed interface ResetEvent {
    data object RequestReset : ResetEvent
    data object DismissConfirm : ResetEvent
    data object ConfirmReset : ResetEvent
}

sealed interface ResetUiEvent {
    data object ResetDone : ResetUiEvent
}
```

### Navigation Routes

- **MODIFIED**: replace `composable("config/reset")` placeholder with the
  real `ResetScreen`.

### Hilt Modules

No new module. `ResetViewModel` uses already-bound dependencies. The
`AppUsageDao` is injectable via the existing `DatabaseModule` (SF-7.1
exposes it).

### Telemetry guardrail

- **MODIFIED** `TelemetryGuardrail.ALLOWED_PROPS` — add:
  ```kotlin
  "learning_reset" to emptySet(),
  ```

### Composables by Feature (checklist)

- [x] `ResetScreen` + stateless `ResetContent`.
- [x] Inline `AlertDialog` for confirmation.
- [x] Dark + large-font previews.

### Material Design Components

- `Button` (M3) with `colorScheme.error` for the destructive CTA.
- `AlertDialog` for confirmation.
- `TextButton` for the dialog's confirm/dismiss.

## Acceptance Criteria

- [ ] **Reset clears all 4 data sources**:
      - `aliasRepo.observeAll()` emits empty list after reset.
      - `appUsageDao.observeTopByOpenCount(20).first()` is empty.
      - `failedLog.observeRecent(50).first()` is empty.
      - `settingsRepo.launcherFavouritesOverride.first()` is null.
- [ ] **Confirmation prevents accidental click** — single tap requires the
      confirm dialog; tapping outside the dialog (`onDismissRequest`) does
      not perform the reset.
- [ ] **Defensive single-execution** — concurrent confirms (or accidental
      double-tap) do not run the reset twice.
- [ ] **Settings preserved** — after reset, `executeThreshold`,
      `confirmThreshold`, `alwaysConfirm`, `ttsVoiceName`, `ttsRate`,
      `ttsPitch`, `incomingCallModeEnabled`, `sendFailuresEnabled` retain
      their pre-reset values. Verified by tests.
- [ ] **Toast on success**: "Listo, todo borrado."
- [ ] **Telemetry event**: `learning_reset` with no props (verified via the
      guardrail test).
- [ ] **7 new strings** with the right IDs.
- [ ] **No new permissions, no new manifest entries, no new DataStore keys
      (the existing keys for thresholds / TTS / favourites override are used),
      no new dependencies.**
- [ ] **Build is green**.

## Design Notes

- The destructive button uses
  `MaterialTheme.colorScheme.error` background + `onError` text — clearly
  signalling danger.
- The confirm dialog's "Sí, borrar" uses `error` colour for the text; "Mejor
  no" uses the default tonal style.
- The body explainer enumerates exactly what gets wiped AND what's
  preserved — Fran shouldn't be surprised that his TTS rate stays.

## Senior-UX & Copy

Fran-only — config-menu density.

**No new spoken (TTS) strings.**

New entries in `app/src/main/res/values/strings.xml` (7 total):

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_reset_explainer` | "Esto borra: los alias aprendidos, las apps más usadas, el log de fallos y la lista de favoritas manual. La voz, los umbrales y los toggles se quedan como están." | body explainer |
| `copy_config_reset_button` | "Borrar todo el aprendizaje" | destructive CTA |
| `copy_config_reset_confirm_title` | "¿Seguro?" | dialog title |
| `copy_config_reset_confirm` | "Esto borra los alias y las favoritas aprendidas. No se puede deshacer." | dialog body |
| `copy_config_reset_confirm_yes` | "Sí, borrar" | dialog confirm |
| `copy_config_reset_confirm_no` | "Mejor no" | dialog dismiss |
| `copy_config_reset_done` | "Listo, todo borrado." | success toast |

**`brand-design` COPY table**: add "Reset learning (Phase 8 — SF-8.9)"
section with 7 rows.

## Performance Considerations

- `awaitAll` of 4 deletes — all are O(rows) in tiny tables (≤ 50 failures,
  ≤ hundreds of aliases / usages); milliseconds total.
- `isResetting` guard prevents re-entrancy without locks.

## Testing Requirements

- [ ] **`ResetViewModel`** (5 cases) — JVM + Turbine + fakes:
      1. `onEvent_RequestReset_setsShowConfirmTrue`.
      2. `onEvent_DismissConfirm_setsShowConfirmFalse`.
      3. `onEvent_ConfirmReset_callsAllFourDeleteOperations_inParallel`.
      4. `onEvent_ConfirmReset_emitsResetDone_andPublishesToast_afterAllDeletesComplete`.
      5. `onEvent_ConfirmReset_doesNOTTouch_executeThreshold_or_TtsRate_or_alwaysConfirm` (assert via Fakes that the 5+ unrelated setters are NOT called).
      6. `onEvent_ConfirmReset_concurrentInvocation_isIdempotent_runsExactlyOnce`.
- [ ] **`TelemetryGuardrailLearningResetTest`** (2 cases):
      1. `learning_reset_emptyMap_allowed`.
      2. `learning_reset_anyProp_rejected`.
- [ ] **Instrumented UI tests on `ResetContent`** (4 cases):
      1. `resetButton_visible_andDestructiveColored`.
      2. `resetButton_click_opensConfirmDialog`.
      3. `confirmDialog_yesButton_firesConfirmEvent`.
      4. `confirmDialog_dismiss_doesNotFireReset`.
- [ ] **Dark + large-font previews**.
- [ ] **Real Redmi 15 smoke**:
      - Train Curro on 3 aliases → set a launcher-favourites override of 5
        apps → accumulate 5 failed commands.
      - Open Reset → confirm → home grid reverts to the 4 seed tiles;
        Aliases section empty; Failures section empty.
      - Verify TTS rate / pitch unchanged (the existing TTS uses the
        previous value on the next utterance).
      - Verify the `alwaysConfirm` toggle state unchanged.

## Implementation Notes

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/presentation/config/sections/reset/ResetScreen.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/reset/ResetViewModel.kt`
- `app/src/test/java/com/curro/app/presentation/config/sections/reset/ResetViewModelTest.kt`
- `app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailLearningResetTest.kt`
- `app/src/androidTest/java/com/curro/app/presentation/config/sections/reset/ResetContentTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
  (1 swap).
- `app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt`
  (+1 event).
- `app/src/main/res/values/strings.xml` (+7 entries).
- `.claude/skills/brand-design/SKILL.md` (+7 rows).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.9 Reset learning. |
