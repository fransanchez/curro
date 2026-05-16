# Failed-commands log viewer — US-055 / SF-8.6

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Fran-facing list of the last 50 failed commands with kind badges + filter + clear |
| **US ID** | US-055 (master-plan SF-8.6) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

## Summary

Replace SF-8.1's `config/failures` placeholder with `FailuresScreen` — a
scrollable list of the last 50 `FailedCommandEntity`s (Room-backed by SF-7.5),
each row showing the timestamp, the transcript, and a colour + text badge
for the kind (`INVALID_OUTPUT` → "Modelo no entendió"; `UNKNOWN_FUNCTION` →
"Función no existe"; `HANDLER_ERROR` → "Error al ejecutar"), plus a filter
chip row at the top to slice by kind and a "Borrar log" button at the
bottom. The transcript NEVER leaves the device (spec §12 + the existing
SF-7.5 telemetry guardrail tests verify this — SF-8.6 only renders what's
already on disk).

The kind taxonomy matters: `local-data` rule 4 calls out that Fran needs to
distinguish "I didn't understand" (`INVALID_OUTPUT` — the model returned
junk JSON), "that feature isn't built" (`UNKNOWN_FUNCTION` — the model
returned valid JSON for a function not in the Fase-1 catalogue), and "the
handler crashed" (`HANDLER_ERROR`) so he can decide what to add for Fase 2.
The visual design pairs each kind with a colour AND a label
(`brand-design` rule 5 — colour is never the only signal).

Spec reference: `docs/curro-spec-v1.0.md` §6 flow 7 + §9 + `local-data`
rule 4.

## Scope

- **In Scope**:
  - `FailuresScreen` + `FailuresViewModel`.
  - `FailureRow` + `FilterChipsRow` + `ClearLogConfirmDialog` composables.
  - The "Borrar log" → confirm → `failedLog.deleteAll()` flow.
  - 12 new strings.
  - Replacement of the `composable("config/failures")` placeholder.
- **Out of Scope**:
  - The "Enviar fallos a Fran" button — SF-8.8 adds it inside this screen.
  - Per-row "delete" — out of scope (the table caps at 50; bulk-clear is
    enough).
  - A `details` column expandable panel — out of scope; if `details` is
    non-empty, render it inline below the transcript in a smaller style.
  - Telemetry for the clear action.

## User Flows

### Flow 1: Fran reviews the last week's failures

1. Fran opens config → "Lo que Curro no ha entendido".
2. `FailuresScreen` renders. Top: filter chips (Todos / Modelo no entendió /
   Función no existe / Error al ejecutar) — Todos selected. The list shows
   the last 50 rows from `failedLog.observeRecent(50)`, newest first.
3. Each row: badge + timestamp + transcript + (optional) details.
4. Fran taps "Función no existe" chip → the list filters to
   `UNKNOWN_FUNCTION` rows only.
5. Fran reads them; notices "léeme las noticias" appears 3 times → decides
   to plan a `read_news_headlines` for Fase 2.

### Flow 2: Empty state

1. Fresh install or just-cleared log.
2. `FailuresScreen` renders. Filter chips at top, then a centered "Nada por
   aquí. Curro va bien." (in Curro's voice — short, warm).
3. The "Borrar log" button is disabled (nothing to clear).

### Flow 3: Empty state under a filter

1. Log has only `INVALID_OUTPUT` rows.
2. Fran taps "Función no existe" → the list is empty under the filter.
3. Show "No hay fallos en este filtro." (distinct from the global empty
   state so Fran knows the log isn't empty, just the slice).

### Flow 4: Clear the log

1. Fran taps "Borrar log" → `ClearLogConfirmDialog` opens with
   `copy_config_failures_clear_confirm`.
2. Fran taps "Sí, borrar" → `failedLog.deleteAll()`.
3. The list re-renders empty (the `observeRecent` flow emits an empty list).

## Function-catalog Impact

No catalog change.

## FSM States Touched

None.

## Android System Integrations & Permissions

No new integrations, no new permissions. Uses the existing
`FailedCommandLog` interface (SF-7.5).

## On-device-model Impact

No model impact.

## Android Specification

### Screens and Composables

- **`presentation/config/sections/failures/FailuresScreen.kt`** —
  `@Composable fun FailuresScreen(onBack: () -> Unit, viewModel: FailuresViewModel = hiltViewModel())`.
  - Layout:
    ```kotlin
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = Dimens.MinTapTarget + CurroSpacing.l)) {
            FilterChipsRow(uiState.filter, onChange = { viewModel.onEvent(FailuresEvent.SetFilter(it)) })
            when {
                uiState.allFailures.isEmpty() -> EmptyFailuresState()
                uiState.filteredFailures.isEmpty() -> EmptyFilteredState()
                else -> LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = CurroSpacing.m)) {
                    items(uiState.filteredFailures, key = { it.id }) { failure ->
                        FailureRow(failure)
                    }
                }
            }
            // Sticky bottom: Borrar log button (or a SendFailuresButton in SF-8.8 above it)
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp) {
                BigPrimaryButton(text = stringResource(R.string.copy_config_failures_clear_cta), onClick = { viewModel.onEvent(FailuresEvent.RequestClear) }, enabled = uiState.allFailures.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(CurroSpacing.m))
            }
        }
        IconButton(onClick = onBack, /* TopStart back chevron */)
        if (uiState.showClearConfirm) {
            ClearLogConfirmDialog(onConfirm = { viewModel.onEvent(FailuresEvent.ConfirmClear) }, onDismiss = { viewModel.onEvent(FailuresEvent.DismissConfirm) })
        }
    }
    ```

- **`FilterChipsRow.kt`** — a `Row` of 4 `FilterChip`s (Material 3): Todos /
  Modelo no entendió / Función no existe / Error al ejecutar. The selected
  chip shows the active state.

- **`FailureRow.kt`** — `@Composable fun FailureRow(failure: FailedCommandEntity)`:
  ```kotlin
  Card(modifier = Modifier.fillMaxWidth().padding(vertical = CurroSpacing.s)) {
      Column(Modifier.padding(CurroSpacing.m)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
              KindBadge(failure.kind)
              Spacer(Modifier.width(CurroSpacing.s))
              Text(formatTimestamp(failure.timestampMs), style = labelMedium, color = onSurfaceVariant)
          }
          Spacer(Modifier.height(CurroSpacing.s))
          Text(failure.transcript, style = bodyLarge)
          if (failure.details.isNotEmpty()) {
              Spacer(Modifier.height(CurroSpacing.xs))
              Text(failure.details, style = bodySmall, color = onSurfaceVariant)
          }
      }
  }
  ```

- **`KindBadge.kt`** — `@Composable fun KindBadge(kind: FailureKind)`:
  ```kotlin
  val (bg, fg, label) = when (kind) {
      FailureKind.INVALID_OUTPUT -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, stringResource(R.string.copy_failure_kind_invalid))
      FailureKind.UNKNOWN_FUNCTION -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, stringResource(R.string.copy_failure_kind_unknown))
      FailureKind.HANDLER_ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, stringResource(R.string.copy_failure_kind_error))
  }
  Box(modifier = Modifier.background(bg, shape = MaterialTheme.shapes.extraSmall).padding(horizontal = CurroSpacing.s, vertical = CurroSpacing.xs)) {
      Text(label, style = labelMedium, color = fg)
  }
  ```

- **`ClearLogConfirmDialog.kt`** — Standard `AlertDialog` with title "¿Borrar
  todo el log?", body `copy_config_failures_clear_confirm`, confirm button
  red-coloured "Sí, borrar", dismiss "Mejor no".

- **`EmptyFailuresState.kt`** + **`EmptyFilteredState.kt`** — centered
  `Text`s with the right strings.

### ViewModels and State Management

```kotlin
@HiltViewModel
class FailuresViewModel @Inject constructor(
    private val failedLog: FailedCommandLog,
) : ViewModel() {
    private val filter = MutableStateFlow<FailuresFilter>(FailuresFilter.All)
    private val showClearConfirm = MutableStateFlow(false)

    val uiState: StateFlow<FailuresUiState> = combine(
        failedLog.observeRecent(limit = 50),
        filter,
        showClearConfirm,
    ) { failures, f, showConfirm ->
        FailuresUiState(
            filter = f,
            allFailures = failures,
            filteredFailures = applyFilter(failures, f),
            showClearConfirm = showConfirm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FailuresUiState.Initial)

    fun onEvent(event: FailuresEvent) {
        when (event) {
            is FailuresEvent.SetFilter -> filter.value = event.filter
            FailuresEvent.RequestClear -> showClearConfirm.value = true
            FailuresEvent.DismissConfirm -> showClearConfirm.value = false
            FailuresEvent.ConfirmClear -> {
                viewModelScope.launch {
                    failedLog.deleteAll()
                    showClearConfirm.value = false
                }
            }
        }
    }

    private fun applyFilter(failures: List<FailedCommandEntity>, f: FailuresFilter): List<FailedCommandEntity> = when (f) {
        FailuresFilter.All -> failures
        FailuresFilter.Invalid -> failures.filter { it.kind == FailureKind.INVALID_OUTPUT }
        FailuresFilter.Unknown -> failures.filter { it.kind == FailureKind.UNKNOWN_FUNCTION }
        FailuresFilter.Error -> failures.filter { it.kind == FailureKind.HANDLER_ERROR }
    }
}

sealed interface FailuresFilter {
    data object All : FailuresFilter
    data object Invalid : FailuresFilter
    data object Unknown : FailuresFilter
    data object Error : FailuresFilter
}

data class FailuresUiState(val filter: FailuresFilter, val allFailures: List<FailedCommandEntity>, val filteredFailures: List<FailedCommandEntity>, val showClearConfirm: Boolean) {
    companion object { val Initial = FailuresUiState(FailuresFilter.All, emptyList(), emptyList(), false) }
}

sealed interface FailuresEvent {
    data class SetFilter(val filter: FailuresFilter) : FailuresEvent
    data object RequestClear : FailuresEvent
    data object DismissConfirm : FailuresEvent
    data object ConfirmClear : FailuresEvent
}
```

### Navigation Routes

- **MODIFIED**: replace `composable("config/failures")` placeholder with
  the real `FailuresScreen`.

### Hilt Modules

No new module. `FailuresViewModel` uses the existing `FailedCommandLog`
binding from SF-7.5.

### Composables by Feature (checklist)

- [x] `FailuresScreen` + stateless `FailuresContent`.
- [x] `FilterChipsRow`.
- [x] `FailureRow`.
- [x] `KindBadge`.
- [x] `ClearLogConfirmDialog`.
- [x] `EmptyFailuresState` + `EmptyFilteredState`.
- [x] Dark + large-font previews.

### Material Design Components

- `FilterChip` (Material 3) for the filter row.
- `Card` for each failure row.
- `AlertDialog` for the clear confirm.
- `BigPrimaryButton` for the "Borrar log" CTA.
- `LazyColumn` for the list.

## Acceptance Criteria

- [ ] **Log renders 50 entries, newest first** — verified via Turbine
      against a `FakeFailedCommandLog` returning a known list.
- [ ] **Filter chips work** — selecting each chip filters the list to the
      matching kind.
- [ ] **Empty global state**: "Nada por aquí. Curro va bien." (Curro's
      voice — warm).
- [ ] **Empty filtered state**: "No hay fallos en este filtro."
- [ ] **Kind badge: colour + text** — each badge shows both signals;
      `brand-design` rule 5 satisfied.
- [ ] **Clear log requires confirmation**, then clears.
- [ ] **Clear button disabled when log is empty.**
- [ ] **The transcript stays on-device** — no telemetry event in this SF
      carries any transcript; the existing SF-7.5 guardrail tests cover
      this; SF-8.6 does NOT add new events.
- [ ] **12 new strings** with the right IDs.
- [ ] **No new permissions, no new manifest entries, no new DataStore keys,
      no new dependencies, no new telemetry event.**
- [ ] **Build is green**.

## Design Notes

- Timestamp format: `"%1$tH:%1$tM"` (24-hour HH:MM) for today's failures;
  `"%1$td/%1$tm %1$tH:%1$tM"` (DD/MM HH:MM) for older. **Pin: implement as
  a small `formatTimestamp(ms: Long)` helper** so it's testable in
  isolation.
- The transcript text uses `bodyLarge` so Fran reads it cleanly even at
  arm's length.
- The `details` field, when present, uses `bodySmall` (the verbose context
  is secondary — the transcript is the primary signal).
- Card elevation 2 dp (`Dimens.CardElevation`) for visual grouping; spacing
  between cards `CurroSpacing.s`.

## Senior-UX & Copy

Fran-only — config-menu density.

**No new spoken (TTS) strings.**

New entries in `app/src/main/res/values/strings.xml` (12 total):

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_failures_empty` | "Nada por aquí. Curro va bien." | global empty; Curro's voice |
| `copy_config_failures_empty_filter` | "No hay fallos en este filtro." | filtered empty |
| `copy_config_failures_clear_cta` | "Borrar log" | bottom button |
| `copy_config_failures_clear_confirm_title` | "¿Borrar todo el log?" | dialog title |
| `copy_config_failures_clear_confirm` | "Esto borra los 50 fallos guardados. No se puede deshacer." | dialog body |
| `copy_config_failures_clear_confirm_yes` | "Sí, borrar" | dialog confirm |
| `copy_config_failures_clear_confirm_no` | "Mejor no" | dialog dismiss |
| `copy_config_failures_filter_all` | "Todos" | chip label |
| `copy_config_failures_filter_invalid` | "Modelo no entendió" | chip label |
| `copy_config_failures_filter_unknown` | "Función no existe" | chip label |
| `copy_config_failures_filter_error` | "Error al ejecutar" | chip label |
| `copy_failure_kind_invalid` | "Modelo no entendió" | badge label (mirrors chip) |
| `copy_failure_kind_unknown` | "Función no existe" | badge label |
| `copy_failure_kind_error` | "Error al ejecutar" | badge label |

That's 14 strings (the brief PRD said 12; pin the correct count). Pin:
chip and badge labels share text but distinct IDs (for future i18n
divergence).

**`brand-design` COPY table**: add a "Failed commands log (Phase 8 —
SF-8.6)" section with all 14 rows.

## Performance Considerations

- `LazyColumn` for the list.
- `combine` of 3 flows.
- `applyFilter` runs on every emission — bounded by ~50 entries, trivial.
- No image loading.

## Testing Requirements

- [ ] **FSM**: N/A.
- [ ] **`FailuresViewModel`** (8 cases) — JVM + Turbine + `FakeFailedCommandLog`:
      1. `uiState_emits_emptyList_whenRepoEmpty`.
      2. `uiState_emits_filteredByInvalid_whenSetFilterInvalid`.
      3. `uiState_emits_filteredByUnknown`.
      4. `uiState_emits_filteredByError`.
      5. `uiState_emits_all_whenFilterAll`.
      6. `onEvent_ConfirmClear_callsRepoDeleteAll_andDismissesConfirm`.
      7. `uiState_reactsTo_repoEmission_whenNewFailureAppears`.
      8. `applyFilter_preservesTimestampDescOrder`.
      9. `onEvent_RequestClear_setsShowClearConfirmTrue`.
      10. `onEvent_DismissConfirm_setsShowClearConfirmFalse`.
- [ ] **Instrumented UI tests on `FailuresContent`** (5 cases):
      1. `failureRow_rendersBadgeAndTranscript`.
      2. `filterChip_tappingInvalid_filtersList`.
      3. `emptyState_renders_when_noFailures`.
      4. `clearButton_disabled_whenLogEmpty`.
      5. `clearButton_opensConfirm_then_callsClear`.
      6. `kindBadge_renders_with_correctColorAndLabel_perKind` (parameterised
         across the 3 kinds).
- [ ] **`formatTimestamp` helper test** (3 cases): today / yesterday / older.
- [ ] **Dark + large-font previews**.
- [ ] **Real Redmi 15 smoke**:
      - Speak something unrecognised → open Failures → the new row appears
        at the top with the right kind.
      - Filter by "Modelo no entendió" → only `INVALID_OUTPUT` rows shown.
      - Clear → list empty.
      - Restart app → cleared log stays cleared.

## Implementation Notes

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/FailuresScreen.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/FailuresViewModel.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/FailureRow.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/KindBadge.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/FilterChipsRow.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/ClearLogConfirmDialog.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/EmptyFailuresState.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/formatTimestamp.kt` (helper + its test)
- `app/src/test/java/com/curro/app/presentation/config/sections/failures/FailuresViewModelTest.kt`
- `app/src/test/java/com/curro/app/presentation/config/sections/failures/FormatTimestampTest.kt`
- `app/src/androidTest/java/com/curro/app/presentation/config/sections/failures/FailuresContentTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
  (1 swap).
- `app/src/main/res/values/strings.xml` (+14 entries).
- `.claude/skills/brand-design/SKILL.md` (+14 rows).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.6 Failed-commands log viewer. |
