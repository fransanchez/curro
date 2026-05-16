# Launcher favourites override UI — US-052 / SF-8.3

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Fran-facing override of the recency-weighted home grid (4–6 hand-picked apps) |
| **US ID** | US-052 (master-plan SF-8.3) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

## Summary

Replace SF-8.1's `config/favourites` placeholder with a two-screen flow that
lets Fran (a) see the current home-grid favourites + whether they're
auto-derived or manually overridden, and (b) edit the override list — pick
4–6 installed apps; "Guardar" persists; "Volver al automático" clears the
override and reverts to SF-7.4's recency-weighted scoring + seed-padding.

The mechanism is a new `launcherFavouritesOverride: Flow<List<String>?>`
setting in `SettingsRepository` (null = automatic; non-null = exactly these
packages, in this order). `RecencyFavoriteAppsRepositoryImpl.loadFavorites()`
checks the override BEFORE the decay scoring; the existing `merge(timerFlow,
recomputeTrigger)` gains a third source — the override flow — so changes
ripple to the home grid immediately (the seam from SF-7.4 was declared
exactly for this).

Spec reference: `docs/curro-spec-v1.0.md` §9 ("Apps favoritas del launcher")
+ `local-data` rule 5 + `launcher-ui` "feels the same every day".

## Scope

- **In Scope**:
  - `FavouritesScreen` (overview) + `FavouritesEditorScreen` (the picker).
  - `FavouritesViewModel` + `FavouritesEditorViewModel`.
  - New DataStore key `launcher_favourites_override` + the setter and getter
    in `SettingsRepository` / `SettingsDataStore`.
  - Modification of `RecencyFavoriteAppsRepositoryImpl.loadFavorites` +
    `observeFavorites` to consume the override.
  - 6 new strings.
  - Replacement of the `composable("config/favourites")` placeholder in
    `CurroNavHost` + a new nested route `config/favourites/editor`.
- **Out of Scope**:
  - Any change to the recency-scoring formula in
    `RecencyFavoriteAppsRepositoryImpl.scoreEntity` (SF-7.4 is authoritative).
  - The Phase-8 "Actualizar favoritas" force-recompute button (mentioned in
    SF-7.4's Kdoc as a Phase-8 hook — that's a separate, future SF outside
    Phase 8's listed 10 SFs; not for this brief).
  - Reordering of the selected apps within the editor (SF-8.3 ships with
    "selection order = displayed order" — the editor's list is alphabetical;
    Fran's selections appear in alphabetical order on the home grid. A
    Phase-8.x "drag to reorder" affordance is a future SF.) **Pin: the
    `LinkedHashSet`-backed selection in the editor preserves selection
    order across taps, so the on-disk override list is in tap order; the
    home grid renders in that order. If the user taps WhatsApp first, then
    Camera, the grid shows WhatsApp on tile 1 and Camera on tile 2.**
  - A search bar in the editor (the alphabetical list is plenty for ~30–50
    installed apps; a search bar is a future polish).
  - Telemetry for override changes.

## User Flows

### Flow 1: Fran sees the current favourites (auto)

1. Fran opens config → "Aplicaciones favoritas".
2. `FavouritesScreen` renders. Top half: a horizontal row of the current 4
   tiles (small icons + labels) — "WhatsApp", "Llamadas", "Cámara", "Fotos"
   (the SF-7.4 seeds on a fresh install).
3. Status line: "Automático — recalculado cada 24 h".
4. Below: "Editar manualmente" button.

### Flow 2: Fran sets an override

1. From `FavouritesScreen`, Fran taps "Editar manualmente".
2. `FavouritesEditorScreen` opens (nav route `config/favourites/editor`).
3. The full installed-app list renders alphabetically (from
   `InstalledAppsRepository.observeAllLaunchable`); each row is a checkbox +
   icon + label.
4. None pre-selected (no override yet).
5. Fran taps 5 apps in his preferred order. The Save button (disabled
   below 4 selections; enabled at 4; still enabled at 5; still at 6;
   disabled at 7+) becomes enabled.
6. Fran taps Guardar → VM calls
   `settingsRepo.setLauncherFavouritesOverride(selected.toList())` →
   `popBackStack` to `FavouritesScreen`.
7. `FavouritesScreen` re-renders with the 5 chosen apps in tap order;
   status line: "Manual — 5 apps fijadas"; "Volver al automático" button
   now visible.
8. The home grid (launcher home) **also** re-renders immediately — the
   `observeFavorites` flow re-emits because of the override change.

### Flow 3: Fran reverts to automatic

1. On `FavouritesScreen` with an override active, Fran taps "Volver al
   automático".
2. `FavouritesViewModel.onEvent(ResetToAuto)` →
   `settingsRepo.setLauncherFavouritesOverride(null)`.
3. The status line flips back to "Automático — recalculado cada 24 h"; the
   "Volver al automático" button disappears; the displayed favourites
   re-derive from the recency scoring + seed padding (SF-7.4).
4. The home grid re-renders to match.

### Flow 4: An override app gets uninstalled

1. Override is set to `[com.whatsapp, com.example.deleted-app, com.android.dialer]`.
2. The user uninstalls `com.example.deleted-app` via HyperOS settings.
3. Next time `loadFavorites()` runs, `seedAppResolver.toFavoriteApp("com.example.deleted-app")`
   returns `null`; the slot is silently dropped.
4. The home grid shows 2 tiles (WhatsApp + Dialer) instead of 3.
5. **Pin: no automatic seed-padding when the override is partial** — the
   override is Fran's explicit list; if a slot disappears, the grid shrinks
   until Fran re-edits. (Document this in the screen body? Probably not;
   it's an edge case that resolves naturally.)
6. Fran opens the favourites section → sees "Manual — 2 apps fijadas
   (estabas en 3)" — pin: the editor screen can show the override list
   with a warning row for the uninstalled package.

**Refined for the brief**: SF-8.3 ships the "silently drop missing slots"
behaviour AND a small banner on `FavouritesScreen` when `override.size !=
displayed.size`: `copy_config_favourites_app_missing` ("Falta una app que
tenías fijada — entra en \"Editar manualmente\" para arreglarlo.").

### Flow 5: Override at the editor open — pre-selection

1. From `FavouritesScreen` with an override of `[com.whatsapp, com.android.dialer]`,
   Fran taps "Editar manualmente".
2. `FavouritesEditorViewModel.init` loads `settingsRepo.launcherFavouritesOverride.first()`
   → pre-selects those two in the editor.
3. Fran sees the same apps checked; can add / remove.

## Function-catalog Impact

No catalog change. The `open_app` handler (US-027) is unaffected; this SF
only changes the home-grid favourites.

## FSM States Touched

None.

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `QUERY_ALL_PACKAGES` | already declared (SF-1.4) — `InstalledAppsRepository` uses it | always declared | the list is empty / partial; that's an existing condition |

No new permissions.

## On-device-model Impact

No model impact.

## Android Specification

### Screens and Composables

- **`presentation/config/sections/favourites/FavouritesScreen.kt`** —
  `@Composable fun FavouritesScreen(onBack: () -> Unit, onEditClick: () -> Unit, viewModel: FavouritesViewModel = hiltViewModel())`.
  - Sections:
    1. Status line: "Automático — recalculado cada 24 h" OR "Manual — %1$d
       apps fijadas".
    2. Current-favourites row: a `Row` of 4–6 small `AppTile`s (reuse the
       launcher's `AppTile` composable at a smaller size — pin: a new
       `MiniAppTile` variant if `AppTile` is opinionated about size; the
       implementer chooses).
    3. (Conditional) `WarningBanner` for missing apps.
    4. `BigPrimaryButton("Editar manualmente", onClick = onEditClick)`.
    5. (Conditional, when `uiState.hasOverride`) `BigPrimaryButton("Volver
       al automático", onClick = { viewModel.onEvent(FavouritesEvent.ResetToAuto) })`.
  - Back chevron at `TopStart`.

- **`presentation/config/sections/favourites/FavouritesEditorScreen.kt`** —
  `@Composable fun FavouritesEditorScreen(onBack: () -> Unit, onSaved: () -> Unit, viewModel: FavouritesEditorViewModel = hiltViewModel())`.
  - Layout:
    ```kotlin
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = Dimens.MinTapTarget + CurroSpacing.l, bottom = 96.dp)) {
            Text(stringResource(R.string.copy_config_favourites_limit_help, MIN_FAVOURITES, MAX_FAVOURITES), style = bodyMedium, modifier = Modifier.padding(CurroSpacing.m))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.apps, key = { it.packageName }) { app ->
                    AppCheckboxRow(app, uiState.selected.contains(app.packageName), onToggle = { viewModel.onEvent(FavouritesEditorEvent.Toggle(app.packageName)) })
                }
            }
        }
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 4.dp) {
            BigPrimaryButton(text = stringResource(R.string.copy_config_favourites_save), onClick = { viewModel.onEvent(FavouritesEditorEvent.Save) ; onSaved() }, enabled = uiState.canSave, modifier = Modifier.fillMaxWidth().padding(CurroSpacing.m))
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = CurroSpacing.s, top = CurroSpacing.s).size(Dimens.MinTapTarget)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_back), modifier = Modifier.size(Dimens.LargeIconSize))
        }
    }
    ```

- **`AppCheckboxRow.kt`** — `@Composable fun AppCheckboxRow(app: LaunchableApp, isChecked: Boolean, onToggle: () -> Unit)`. A `Row(heightIn(min = 72.dp).clickable(onClick = onToggle))` with a `Checkbox(checked = isChecked, onCheckedChange = null)` (the row handles the click), a Coil `AsyncImage` of the icon (48 dp), the label (`bodyLarge`), and the selection ordinal — pin: when the app is selected, the row displays its position number in the selection ("1", "2", …) so Fran sees the tap order that will become the grid order.

### ViewModels and State Management

```kotlin
@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val favoritesRepo: FavoriteAppsRepository,
) : ViewModel() {
    val uiState: StateFlow<FavouritesUiState> = combine(
        favoritesRepo.observeFavorites(),
        settingsRepo.launcherFavouritesOverride,
    ) { displayed, override ->
        FavouritesUiState(
            currentFavourites = displayed,
            hasOverride = override != null,
            overrideCount = override?.size ?: 0,
            missingFromOverride = (override?.size ?: 0) - displayed.size > 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavouritesUiState.Initial)

    fun onEvent(event: FavouritesEvent) {
        when (event) {
            FavouritesEvent.ResetToAuto -> viewModelScope.launch {
                settingsRepo.setLauncherFavouritesOverride(null)
            }
        }
    }
}

data class FavouritesUiState(val currentFavourites: List<FavoriteApp>, val hasOverride: Boolean, val overrideCount: Int, val missingFromOverride: Boolean) {
    companion object { val Initial = FavouritesUiState(emptyList(), false, 0, false) }
}

sealed interface FavouritesEvent {
    data object ResetToAuto : FavouritesEvent
}

@HiltViewModel
class FavouritesEditorViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val installedApps: InstalledAppsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val selected = MutableStateFlow<Set<String>>(emptySet())  // LinkedHashSet preserves order
    private val apps = MutableStateFlow<List<LaunchableApp>>(emptyList())

    init {
        viewModelScope.launch {
            val initialOverride = settingsRepo.launcherFavouritesOverride.first() ?: emptyList()
            selected.value = LinkedHashSet(initialOverride)
            installedApps.observeAllLaunchable().collect { list -> apps.value = list }
        }
    }

    val uiState: StateFlow<FavouritesEditorUiState> = combine(apps, selected) { list, sel ->
        FavouritesEditorUiState(
            apps = list,
            selected = sel,
            canSave = sel.size in MIN_FAVOURITES..MAX_FAVOURITES,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavouritesEditorUiState.Initial)

    fun onEvent(event: FavouritesEditorEvent) {
        when (event) {
            is FavouritesEditorEvent.Toggle -> selected.value = selected.value.toMutableSet().also {
                if (event.packageName in it) it.remove(event.packageName) else if (it.size < MAX_FAVOURITES) it.add(event.packageName)
            }.let { LinkedHashSet(it) }  // preserve insertion order
            FavouritesEditorEvent.Save -> viewModelScope.launch(ioDispatcher) {
                settingsRepo.setLauncherFavouritesOverride(selected.value.toList())
            }
        }
    }

    private companion object {
        const val MIN_FAVOURITES = 4
        const val MAX_FAVOURITES = 6
    }
}
```

**Pin: order-preservation across Toggle**: a `Set<String>` reorders on
mutation; the implementation uses a `LinkedHashSet` (or a `List` + uniqueness
check) to keep insertion order. The `toggle` operation either removes
(preserving the order of the others) or appends. **Pin: when `selected.size
== MAX_FAVOURITES` and the user taps an unselected app, the toggle is a
no-op** — the cap is the cap.

### Navigation Routes

- **MODIFIED**: replace `composable("config/favourites") { ConfigSectionPlaceholder(...) }`
  with the real `FavouritesScreen`.
- **NEW**: `composable("config/favourites/editor") { FavouritesEditorScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() }) }`.

### Hilt Modules

No new module. Both ViewModels are `@HiltViewModel`-injectable.

`FavoriteAppsRepository` is the existing binding (SF-7.4 — `Recency...Impl`);
this SF modifies the impl to accept `SettingsRepository`.

### Composables by Feature (checklist)

- [x] `FavouritesScreen` (overview + status + CTAs)
- [x] `FavouritesEditorScreen` (the picker)
- [x] `AppCheckboxRow` (a row in the editor)
- [x] `WarningBanner` (the "Falta una app …" line)
- [x] Empty / not-loaded state: while `uiState.apps.isEmpty()` AND
      `installedApps.observeAllLaunchable()` is still emitting its first
      value, show a small spinner. (Single composable
      `EditorLoadingState`.)
- [x] Dark + large-font previews.

### Material Design Components

- `Checkbox` (Material 3 default) — config-menu density.
- `BigPrimaryButton` for the two CTAs.
- `LazyColumn` for the editor's app list.
- `Surface(tonalElevation = 4.dp)` for the sticky Save bar at the bottom of
  the editor.

## Acceptance Criteria

- [ ] **Override read path**: `RecencyFavoriteAppsRepositoryImpl.loadFavorites()`
      checks `settingsRepo.launcherFavouritesOverride.first()` BEFORE the
      decay scoring. Non-null → builds the list from the override packages
      ONLY (skips scoring, skips seed-padding). Null → existing behaviour.
- [ ] **Override write path**: `setLauncherFavouritesOverride(packages)`
      writes the comma-joined string to DataStore key
      `launcher_favourites_override` (empty list / null → empty string).
- [ ] **Reactive recompute**: changing the override (set or clear) causes
      `observeFavorites` to re-emit within one Flow tick (verified via
      Turbine).
- [ ] **4-6 limit enforced** in the editor — Save disabled below 4 or above
      6 (above 6 is prevented by the toggle being a no-op).
- [ ] **Tap order preserved** — selecting WhatsApp first, then Camera,
      saving, opening the home: WhatsApp is tile 1, Camera tile 2.
- [ ] **Pre-selection on edit**: opening the editor with an existing
      override pre-checks those rows and shows their ordinal numbers.
- [ ] **Reset to auto**: clears the override; the home grid reverts to the
      recency + seed-padding flow within one Flow tick.
- [ ] **Missing-app handling**: if an override package is uninstalled, the
      slot drops silently AND a warning banner shows on `FavouritesScreen`.
- [ ] **6 new strings exist** with the right IDs.
- [ ] **No new permissions, no new manifest entries, no new dependencies.**
- [ ] **New DataStore key** `launcher_favourites_override` exists.
- [ ] **No new telemetry event.**
- [ ] **Build is green**.

## Design Notes

- Use `BigPrimaryButton` for both CTAs even though this is a config screen
  (Fran is the user; these are non-destructive). Visual consistency with
  the launcher matters less here than on the home, but `BigPrimaryButton`
  reads cleanly without re-skinning.
- The current-favourites row uses small icons (~48 dp icon, ~80 dp tile) —
  the editor row uses larger icons (~48 dp) per `BigListRow` style.
- The status line uses `MaterialTheme.colorScheme.onSurfaceVariant` for
  "Automático" and `MaterialTheme.colorScheme.primary` for "Manual" — the
  colour reinforces the state but doesn't carry it (the text is the
  primary signal; `brand-design` rule 5).
- The warning banner uses
  `MaterialTheme.colorScheme.errorContainer` background +
  `onErrorContainer` text — Fran needs to notice it.

## Senior-UX & Copy

Fran-only — config-menu density. Tap targets ≥ 72 dp; reuse
`BigPrimaryButton` for the CTAs which gives ≥ 96 dp regardless.

**No new spoken (TTS) strings.**

New entries in `app/src/main/res/values/strings.xml` (7 total — the brief's
checklist said 6 but the warning banner adds one):

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_favourites_auto` | "Automático — recalculado cada 24 h" | status line |
| `copy_config_favourites_manual_count` | "Manual — %1$d apps fijadas" | status line, positional arg |
| `copy_config_favourites_edit_cta` | "Editar manualmente" | CTA |
| `copy_config_favourites_reset_auto` | "Volver al automático" | CTA |
| `copy_config_favourites_save` | "Guardar" | save button |
| `copy_config_favourites_limit_help` | "Elige entre %1$d y %2$d apps" | editor help line |
| `copy_config_favourites_app_missing` | "Falta una app que tenías fijada — entra en \"Editar manualmente\" para arreglarlo." | warning banner |

**`brand-design` COPY table**: add a "Favourites override (Phase 8 — SF-8.3)"
section with all 7 rows; provenance `(NEW — SF-8.3)`.

## Performance Considerations

- The override change ripples through `observeFavorites` via a `merge` of
  the existing `timerFlow + recomputeTrigger` with the new
  `settingsRepo.launcherFavouritesOverride.drop(1).map { }` flow. **Pin:
  `drop(1)`** — the first emission is the initial-value already captured by
  the `emit(loadFavorites())` at the top of the flow body.
- `seedAppResolver.toFavoriteApp(packageName)` is the existing per-package
  resolution; the override path uses it `override.size` times (≤ 6 per
  resolve), bounded.
- The editor's full installed-app list comes from the existing
  `InstalledAppsRepository.observeAllLaunchable` which is a Coil + Hilt
  pipeline already tuned for "Más apps" (US-013).
- No new image-loading work.

## Testing Requirements

- [ ] **FSM**: N/A.
- [ ] **`SettingsRepository` override flow** —
      `SettingsDataStoreFavouritesOverrideTest` (5 cases):
      1. `default_isNull`.
      2. `setNonEmpty_persistsAndEmits`.
      3. `setEmpty_coercesToNull`.
      4. `setNull_emitsNull`.
      5. `commaSeparatorRoundTrip_preservesOrder`.
- [ ] **`RecencyFavoriteAppsRepositoryImpl` override path** —
      `RecencyFavoriteAppsRepositoryOverrideTest` extends existing (4 cases):
      1. `overridePresent_returns_exactlyTheOverridePackages_inOrder_skippingRecency`.
      2. `overridePresent_packageNotInstalled_silentlyDropsThatSlot`.
      3. `overrideNull_fallsBackToRecencyScoring_andSeedPadding`.
      4. `overrideChange_triggersRecompute_viaFlowMerge` (Turbine).
- [ ] **`FavouritesViewModel`** (5 cases) — JVM:
      1. `uiState_emitsCurrentFavourites_andOverrideState`.
      2. `uiState_hasOverride_trueWhen_settingsHasNonNull`.
      3. `onEvent_ResetToAuto_callsSetLauncherFavouritesOverride_withNull`.
      4. `uiState_missingFromOverride_trueWhen_overrideSize_gt_displayedSize`.
      5. `uiState_reactsTo_overrideChange`.
- [ ] **`FavouritesEditorViewModel`** (5 cases) — JVM + fakes:
      1. `loadOnInit_populatesAppList_andPreSelectsCurrentOverride`.
      2. `canSave_falseWhen_selectedSizeBelow4`.
      3. `canSave_falseWhen_selectedSizeAbove6` (forced via direct mutation
         in the test — the toggle blocks 7+, but the boundary test is
         legitimate).
      4. `canSave_trueWhen_selectedSizeIs4to6`.
      5. `onSave_callsSetLauncherFavouritesOverride_withSelectedPackagesInTapOrder`.
      6. `onToggle_atMaxCapacity_isNoOpForNewSelection`.
- [ ] **Instrumented UI tests**
      (`FavouritesEditorContentTest`, 4 cases):
      1. `appList_renders_allInstalledApps`.
      2. `tappingCheckbox_addsToSelection_andShowsOrdinal`.
      3. `saveButton_disabledUntil_4SelectionsReached`.
      4. `saveButton_disabled_when_above6`.
- [ ] **Dark + large-font previews** on `FavouritesScreen`,
      `FavouritesEditorScreen`, `AppCheckboxRow`, `WarningBanner`.
- [ ] **Real Redmi 15 smoke**:
      - Pick 4 apps in tap order → save → home grid changes immediately to
        exactly those 4, in tap order.
      - Add a 5th → grid shows 5.
      - "Volver al automático" → grid reverts to the recency-weighted
        default (4 seed tiles on a fresh install).
      - Uninstall an app in the override → reopen the section → warning
        banner appears; home grid shrinks.
      - Restart the app → override persists.

## Implementation Notes

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/presentation/config/sections/favourites/FavouritesScreen.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/favourites/FavouritesViewModel.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/favourites/FavouritesEditorScreen.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/favourites/FavouritesEditorViewModel.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/favourites/AppCheckboxRow.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/favourites/WarningBanner.kt`
- `app/src/test/java/com/curro/app/data/local/SettingsDataStoreFavouritesOverrideTest.kt`
- `app/src/test/java/com/curro/app/data/apps/RecencyFavoriteAppsRepositoryOverrideTest.kt`
- `app/src/test/java/com/curro/app/presentation/config/sections/favourites/FavouritesViewModelTest.kt`
- `app/src/test/java/com/curro/app/presentation/config/sections/favourites/FavouritesEditorViewModelTest.kt`
- `app/src/androidTest/java/com/curro/app/presentation/config/sections/favourites/FavouritesEditorContentTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/domain/repository/SettingsRepository.kt`
  (+1 flow + 1 setter).
- `app/src/main/java/com/curro/app/data/local/SettingsDataStore.kt` (+1 key
  + comma-joined string round-trip).
- `app/src/main/java/com/curro/app/data/apps/RecencyFavoriteAppsRepositoryImpl.kt`
  (inject `SettingsRepository`; modify `loadFavorites` + `observeFavorites`).
- `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
  (1 swap + 1 new route).
- `app/src/main/res/values/strings.xml` (+7 entries).
- `.claude/skills/brand-design/SKILL.md` (+7 rows in a new "Favourites
  override (Phase 8 — SF-8.3)" section).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.3 Launcher favourites override UI. |
