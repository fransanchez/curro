# US-013 Brief — SF-1.5: "Más apps" full-app-list screen

**Story**: As Fran's father, I can tap "Más apps" on the launcher home to see and launch any
installed app, so Curro works even when an app is not in the four favourites.

**Spec section**: §11 (launcher layout), §3 (senior-first: ≥96 dp tap targets), §2 (always spoken + shown)

**PRD**: US-013 Phase 1

---

## Acceptance criteria

| # | Criterion |
|---|-----------|
| AC-1 | Tapping "Más apps" on the launcher home navigates to the full-app-list screen. |
| AC-2 | The screen shows every installed launchable app in a scrollable `LazyColumn`. |
| AC-3 | Apps are sorted alphabetically with Spanish collation (handles ñ and accented vowels). |
| AC-4 | Each row shows the app icon and its label; minimum height ≥ 96 dp. |
| AC-5 | Tapping a row launches the app and returns to the launcher (same task stack). |
| AC-6 | A back chevron at TopStart (≥ 96 dp hit area) pops back to the launcher home. |
| AC-7 | No Scaffold / TopAppBar inside the screen — the `CurroNavHost` Scaffold pads it (No-Double-Padding rule). |
| AC-8 | `contentDescription` on every app icon. |

---

## Architecture

- **Domain model**: `LaunchableApp(packageName, label, icon)` — already added in SF-1.4.
- **Repository interface**: `InstalledAppsRepository.observeAllLaunchable(): Flow<List<LaunchableApp>>` — already added in SF-1.4.
- **Repository impl**: `InstalledAppsRepositoryImpl` — already added in SF-1.4.
- **Hilt binding**: `AppsModule.bindInstalledAppsRepository` — already in SF-1.4.
- **Nav route**: `CurroRoute.MoreApps("more_apps")` — already declared in SF-1.4.

New files for SF-1.5:
- `presentation/launcher/MoreAppsViewModel.kt` — `@HiltViewModel`, injects `InstalledAppsRepository`,
  exposes `uiState: StateFlow<MoreAppsUiState>` and `onEvent(MoreAppsEvent)`.
- `presentation/launcher/MoreAppsScreen.kt` — stateless `MoreAppsContent` + hoisting screen composable;
  wired into `CurroNavHost.composable(CurroRoute.MoreApps.value)`.

---

## UI layout

```
[ Back chevron (TopStart, Box overlay, 96 dp × 96 dp) ]
┌────────────────────────────────┐
│ LazyColumn (fillMaxSize)        │
│ ┌─────────────────────────────┐ │
│ │ BigListRow (icon + label)   │ │  ← per LaunchableApp, icon in leading slot
│ └─────────────────────────────┘ │
│  … (sorted list, scrollable)    │
└────────────────────────────────┘
```

- Root: `Box(fillMaxSize)` with `LazyColumn` filling the interior + `IconButton` chevron overlay at `Alignment.TopStart`.
- `BigListRow`: `leading` slot = 40 dp `Image` (icon bitmap) or fallback `Icon(Icons.Filled.Apps)`;
  `title` = app label; no `subtitle`; no `trailing`. Min height = `Dimens.BigRowHeight` (96 dp).
- `LazyColumn` top padding = `Dimens.MinTapTarget` (96 dp) so the list starts below the back chevron.

---

## ViewModel shape

```kotlin
@HiltViewModel
class MoreAppsViewModel @Inject constructor(
    appsRepo: InstalledAppsRepository,
) : ViewModel() {
    val uiState: StateFlow<MoreAppsUiState> = appsRepo
        .observeAllLaunchable()
        .map { MoreAppsUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), MoreAppsUiState.Loading)

    fun onEvent(event: MoreAppsEvent) { /* no mutable events in Phase 1 */ }
}

sealed interface MoreAppsUiState {
    data object Loading : MoreAppsUiState
    data class Ready(val apps: List<LaunchableApp>) : MoreAppsUiState
}

sealed interface MoreAppsEvent // empty in Phase 1 — tap handled in composable via context.startActivity
```

---

## Strings delta (SF-1.5 adds none — all needed strings exist)

- `copy_home_more_apps` ("Más apps") — already in strings.xml (SF-1.4).
- `cd_back` ("Volver") — already in strings.xml (US-007).
- App labels come from `LaunchableApp.label` (loaded from PackageManager, not resources).

---

## Files changed

| File | Action |
|------|--------|
| `presentation/launcher/MoreAppsViewModel.kt` | NEW |
| `presentation/launcher/MoreAppsScreen.kt` | NEW |
| `presentation/navigation/CurroNavHost.kt` | MODIFY — add `composable(CurroRoute.MoreApps.value)` block |
| `docs/PRD.md` | UPDATE — tick US-013 |

---

## Test plan

Unit test file: `app/src/test/java/com/curro/app/presentation/launcher/MoreAppsViewModelTest.kt`

- Loading state emitted before the repository emits.
- Ready state with a non-empty list once the repo emits.
- Empty-list edge case remains in `Ready` state (no special empty state in Phase 1).
