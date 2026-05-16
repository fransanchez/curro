# `ConfigMenuScreen` scaffold + section navigation — US-050 / SF-8.1

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `ConfigMenuScreen` scaffold + 9 section routes (placeholders for SF-8.2 → SF-8.10) |
| **US ID** | US-050 (master-plan SF-8.1) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

## Summary

Replace the Phase-0 `ConfigMenuPlaceholderScreen` (US-007 / SF-0.6) with the real,
sectioned `ConfigMenuScreen` — the hidden Fran-only screen described in spec §9
that's opened by the 5-tap-on-clock gesture (already wired through
`LauncherSideEffect.OpenConfig` → `CurroRoute.ConfigMenu` from US-014 / SF-1.6).
The screen is a normal-density `LazyColumn` (denser than the senior-first
launcher — `launcher-ui` rule 5: this screen is for Fran, not for his father), a
large back chevron at `Alignment.TopStart` (No-Double-Padding rule), nine
`ConfigSectionRow`s, and a `ConfigViewModel` that already wires up the
alias-count and failed-commands-count summary badges from the real repositories
(US-046 + US-049). The seven navigable sections each land on a
`ConfigSectionPlaceholder("…", onBack)` route that SF-8.2 → SF-8.10 replace
inline.

This SF is **not user-facing** in any way that the validation user (Fran's
father) will see — it's the entry point for the maintenance and tuning work Fran
does on visits. (Spec §13's validation criteria are unaffected by Phase 8.)

Spec reference: `docs/curro-spec-v1.0.md` §9.

## Scope

- **In Scope**:
  - `ConfigMenuScreen` composable + stateless `ConfigMenuContent`.
  - `ConfigViewModel` with a real `StateFlow<ConfigUiState>` keyed off
    `AliasRepository.observeAll`, `FailedCommandLog.observeRecent(50)`,
    `SettingsRepository.incomingCallModeEnabled`,
    `SettingsRepository.sendFailuresEnabled`.
  - `ConfigSectionRow` shared composable.
  - `ConfigSectionPlaceholder` shared placeholder destination.
  - 7 new nested nav routes (`config/aliases`, `config/favourites`,
    `config/tts`, `config/thresholds`, `config/failures`, `config/reset`,
    `config/diagnostics`) — each a placeholder.
  - Deletion of `ConfigMenuPlaceholderScreen.kt`.
  - 12 new strings (the 9 section titles + 2 count-summary templates + 1
    generic "Próximamente").
  - Two new `SettingsRepository` flow surfaces: `incomingCallModeEnabled` and
    `sendFailuresEnabled` (DataStore-backed; defaults `false`). **Pin: SF-8.1
    introduces the flows so SF-8.7 + SF-8.8 don't have to** — keeping the
    `ConfigViewModel` self-sufficient. Their setters land in SF-8.7 / SF-8.8;
    in SF-8.1 they're read-only (the toggles render the current value but the
    `onChange` is a no-op + a `Log.w("ConfigMenu", "incoming-call toggle wired in SF-8.7")`).
- **Out of Scope** (explicit non-deliveries):
  - The real contents of any section (lands in SF-8.2 → SF-8.10).
  - The functional toggle behaviour of "Modo asistente de llamadas" (SF-8.7)
    and "Compartir fallos con Fran" (SF-8.8) — the toggle rows render but their
    `onChange` is inert in SF-8.1.
  - Any change to the 5-tap clock gesture wiring (US-014 → already in place;
    SF-8.1 verifies but does not modify).
  - Any change to `LauncherViewModel` or `LauncherPlaceholderScreen`.
  - Any new permission.
  - Any new telemetry event.
  - Any change to `docs/curro-spec-v1.0.md` (the spec already documents §9 with
    the right shape; SF-8.7 will bump it for the incoming-call permissions).

## User Flows

### Flow 1: Fran opens the config menu and navigates to a placeholder section

1. Fran taps the clock 5× within 3 s on the launcher home (`idle`).
2. `LauncherViewModel.onClockTapped` increments the in-window counter; on the
   5th tap → emits `LauncherSideEffect.OpenConfig` (already in place).
3. `LauncherPlaceholderScreen`'s `LaunchedEffect` collects the side effect →
   invokes `onOpenConfig` → `CurroNavHost` calls
   `navController.navigate(CurroRoute.ConfigMenu.value)`.
4. `ConfigMenuScreen` renders. The 9 `ConfigSectionRow`s appear; the alias-
   count and failed-commands-count summaries are populated by the time the
   first frame draws (or right after — they're behind a `combine` Flow with an
   initial-value of `ConfigUiState(sections = listOf(... empty summaries ...),
   incomingCallEnabled = false, sendFailuresEnabled = false)`).
5. Fran taps "Alias de contactos" → `onNavigateToSection("config/aliases")` →
   `ConfigSectionPlaceholder` with title "Alias de contactos — próximamente".
6. Fran taps the back chevron → `popBackStack` → returns to the config menu.

### Flow 2: Fran taps the back chevron from the config menu

1. Fran is on `ConfigMenuScreen`.
2. Taps the back chevron → `popBackStack` → returns to the launcher home.
3. The launcher home re-renders in `idle`.

### Flow 3: An inline toggle row renders but is inert (SF-8.1 only)

1. Fran is on `ConfigMenuScreen`.
2. The "Modo asistente de llamadas" `Toggle` row shows OFF (its value comes
   from `settingsRepo.incomingCallModeEnabled.first()` — which is `false` by
   default in a fresh install).
3. Fran flips the toggle → the `onChange` callback fires
   `Log.w("Curro/Config", "Modo asistente de llamadas wired in SF-8.7 — toggle inert in SF-8.1")` 
   and does NOT mutate the setting. The UI snaps back to OFF.
4. (Pin in brief: this is the only acceptable form of "inert" in Curro —
   no silent ignore; a logcat warning surfaces the gap to the implementer
   testing SF-8.1 on the device.)

## Function-catalog Impact

No catalog change.

## FSM States Touched

None. The config menu lives **outside** the assistant FSM — opening it does not
change `AssistantState`. The launcher screen stays in `idle` while the user is
in the config menu (the navigation is a Compose-nav stack push; the FSM is
unaffected). Spec §6's diagram is unchanged.

## Android System Integrations & Permissions

No new integrations. No new permissions.

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none) | — | — | — |

The `ConfigViewModel` reads from already-injected repositories
(`AliasRepository`, `FailedCommandLog`, `SettingsRepository`) — all are
in-process and need no permission.

## On-device-model Impact

No model impact. SF-8.1 does not touch FunctionGemma's prompt context (the
alias-injection from SF-7.2 is unaffected). No latency budget concerns.

## Android Specification

### Screens and Composables

- **`presentation/config/ConfigMenuScreen.kt`** — the real menu (SF-8.1
  introduces; later SFs do NOT modify, only consume).
  - `@Composable fun ConfigMenuScreen(onBack: () -> Unit, onNavigateToSection: (String) -> Unit, viewModel: ConfigViewModel = hiltViewModel())`
  - Stateless `ConfigMenuContent(uiState: ConfigUiState, onEvent: (ConfigEvent) -> Unit, onBack: () -> Unit, onNavigateToSection: (String) -> Unit)`
  - Layout:
    ```kotlin
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = CurroSpacing.xl,
                    end = CurroSpacing.xl,
                    top = Dimens.MinTapTarget + CurroSpacing.l,  // leave room for the back chevron
                    bottom = CurroSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(CurroSpacing.s),
        ) {
            items(uiState.sections, key = ::sectionKey) { section ->
                when (section) {
                    is ConfigSection.Navigable -> ConfigSectionRow(section, onNavigateToSection)
                    is ConfigSection.Toggle -> ConfigSectionToggleRow(section, onEvent)
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = CurroSpacing.s, top = CurroSpacing.s)
                .size(Dimens.MinTapTarget),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_back), modifier = Modifier.size(Dimens.LargeIconSize))
        }
    }
    ```
  - **No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()`** — the parent
    `CurroNavHost`'s `Scaffold` already provides `Modifier.padding(innerPadding)`
    (No-Double-Padding rule, `navigation-patterns` rule 1; documented in
    `ConfigMenuPlaceholderScreen`'s Kdoc — preserved here).

- **`presentation/config/components/ConfigSectionRow.kt`** — shared composable
  for a navigable row.
  - `@Composable fun ConfigSectionRow(section: ConfigSection.Navigable, onNavigate: (String) -> Unit, modifier: Modifier = Modifier)`
  - Layout:
    ```kotlin
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)  // config-menu density: not the launcher's 96 dp floor
            .clickable { onNavigate(section.route) }
            .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(section.titleResId),
                style = MaterialTheme.typography.bodyLarge,
                color = if (section.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            section.summary?.let { summary ->
                Spacer(Modifier.height(CurroSpacing.xs))
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(CurroSpacing.l),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ```
  - **Pin: 72 dp `heightIn` minimum** (not `height` — content can grow).
  - **Destructive rows** (the reset section) render the title in
    `MaterialTheme.colorScheme.error` so colour + text together signal danger
    (`brand-design` rule 5: colour is never the only signal — the `Reset de
    aprendizaje` title still says what it does).

- **`presentation/config/components/ConfigSectionToggleRow.kt`** — shared
  composable for an inline-toggle row.
  - `@Composable fun ConfigSectionToggleRow(section: ConfigSection.Toggle, onEvent: (ConfigEvent) -> Unit, modifier: Modifier = Modifier)`
  - Layout: same baseline as `ConfigSectionRow` but with a `Switch(checked =
    section.value, onCheckedChange = { onEvent(ConfigEvent.ToggleChanged(section, it)) })`
    in place of the chevron, and the `helpResId` body line always visible
    (not just on tap).

- **`presentation/config/sections/ConfigSectionPlaceholder.kt`** — the single
  shared placeholder destination for the 7 not-yet-implemented sections.
  - `@Composable fun ConfigSectionPlaceholder(title: String, onBack: () -> Unit, modifier: Modifier = Modifier)`
  - Layout: `Box` with a centered `Text("Próximamente", style = titleLarge)`
    + the back chevron at `TopStart` (same pattern).

- **`presentation/config/ConfigUiState.kt`** — the data shapes.
  - `data class ConfigUiState(val sections: List<ConfigSection>, val incomingCallEnabled: Boolean, val sendFailuresEnabled: Boolean)`
  - `sealed interface ConfigSection`:
    - `data class Navigable(val titleResId: Int, val summary: String?, val route: String, val destructive: Boolean = false) : ConfigSection`
    - `data class Toggle(val titleResId: Int, val helpResId: Int, val value: Boolean, val onChangeWillBeWiredInSF: String) : ConfigSection`
      — the `onChangeWillBeWiredInSF` field is a documentation breadcrumb (the
      Kdoc explains: SF-8.7 wires `Toggle("Modo asistente de llamadas", ...)`'s
      `onChange`; SF-8.8 wires `Toggle("Compartir fallos con Fran", ...)`'s).
- **`presentation/config/ConfigEvent.kt`** — `sealed interface ConfigEvent { data class ToggleChanged(val section: ConfigSection.Toggle, val newValue: Boolean) : ConfigEvent }`. SF-8.1's `ConfigViewModel.onEvent(ToggleChanged)` logs a `Log.w` (inert); SF-8.7 / SF-8.8 wire the real behaviour.

### ViewModels and State Management

```kotlin
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val aliasRepo: AliasRepository,
    private val failedLog: FailedCommandLog,
    private val settingsRepo: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val uiState: StateFlow<ConfigUiState> = combine(
        aliasRepo.observeAll(),
        failedLog.observeRecent(limit = 50),
        settingsRepo.incomingCallModeEnabled,
        settingsRepo.sendFailuresEnabled,
    ) { aliases, failures, inCall, sendFails ->
        ConfigUiState(
            sections = buildSections(
                aliasCount = aliases.size,
                failureCount = failures.size,
                incomingCallEnabled = inCall,
                sendFailuresEnabled = sendFails,
            ),
            incomingCallEnabled = inCall,
            sendFailuresEnabled = sendFails,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = ConfigUiState(
            sections = buildSections(0, 0, incomingCallEnabled = false, sendFailuresEnabled = false),
            incomingCallEnabled = false,
            sendFailuresEnabled = false,
        ),
    )

    fun onEvent(event: ConfigEvent) {
        when (event) {
            is ConfigEvent.ToggleChanged -> {
                // SF-8.1 wires the row but not the behaviour — SF-8.7 / SF-8.8 land it.
                Log.w(TAG, "ToggleChanged(${event.section.titleResId}) — wired in ${event.section.onChangeWillBeWiredInSF}; inert in SF-8.1")
            }
        }
    }

    private fun buildSections(
        aliasCount: Int,
        failureCount: Int,
        incomingCallEnabled: Boolean,
        sendFailuresEnabled: Boolean,
    ): List<ConfigSection> = listOf(
        ConfigSection.Navigable(R.string.copy_config_section_aliases, summary = context.getString(R.string.copy_config_summary_aliases_count, aliasCount), route = "config/aliases"),
        ConfigSection.Navigable(R.string.copy_config_section_favourites, summary = null, route = "config/favourites"),
        ConfigSection.Navigable(R.string.copy_config_section_tts, summary = null, route = "config/tts"),
        ConfigSection.Navigable(R.string.copy_config_section_thresholds, summary = null, route = "config/thresholds"),
        ConfigSection.Navigable(R.string.copy_config_section_failures, summary = context.getString(R.string.copy_config_summary_failures_count, failureCount), route = "config/failures"),
        ConfigSection.Toggle(R.string.copy_config_section_incoming_call, helpResId = R.string.copy_config_incoming_call_help_short, value = incomingCallEnabled, onChangeWillBeWiredInSF = "SF-8.7"),
        ConfigSection.Toggle(R.string.copy_config_section_send_failures, helpResId = R.string.copy_config_share_failures_help_short, value = sendFailuresEnabled, onChangeWillBeWiredInSF = "SF-8.8"),
        ConfigSection.Navigable(R.string.copy_config_section_reset, summary = null, route = "config/reset", destructive = true),
        ConfigSection.Navigable(R.string.copy_config_section_diagnostics, summary = null, route = "config/diagnostics"),
    )

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        const val TAG = "Curro/Config"
    }
}
```

**Pin: the toggle rows' `helpResId` is a NEW short string** —
`copy_config_incoming_call_help_short` ("Curro avisa por voz quién te llama.")
and `copy_config_share_failures_help_short` ("Comparte con Fran lo que Curro
no entendió.") — distinct from the full help lines that land in SF-8.7 / SF-8.8.
The full help is on the detail screen / nested; the menu row keeps it short.

### Navigation Routes

```kotlin
// CurroNavHost.kt — MODIFIED
NavHost(navController = navController, startDestination = CurroRoute.Launcher.value) {
    composable(CurroRoute.Launcher.value) { /* unchanged */ }

    // SF-8.1 — the real config menu replaces the placeholder
    composable(CurroRoute.ConfigMenu.value) {
        ConfigMenuScreen(
            onBack = { navController.popBackStack() },
            onNavigateToSection = { route -> navController.navigate(route) },
        )
    }

    // SF-8.1 — 7 new nested routes, all placeholders
    composable("config/aliases") {
        ConfigSectionPlaceholder(
            title = stringResource(R.string.copy_config_section_aliases),
            onBack = { navController.popBackStack() },
        )
    }
    composable("config/favourites") { ConfigSectionPlaceholder(title = stringResource(R.string.copy_config_section_favourites), onBack = { navController.popBackStack() }) }
    composable("config/tts") { ConfigSectionPlaceholder(title = stringResource(R.string.copy_config_section_tts), onBack = { navController.popBackStack() }) }
    composable("config/thresholds") { ConfigSectionPlaceholder(title = stringResource(R.string.copy_config_section_thresholds), onBack = { navController.popBackStack() }) }
    composable("config/failures") { ConfigSectionPlaceholder(title = stringResource(R.string.copy_config_section_failures), onBack = { navController.popBackStack() }) }
    composable("config/reset") { ConfigSectionPlaceholder(title = stringResource(R.string.copy_config_section_reset), onBack = { navController.popBackStack() }) }
    composable("config/diagnostics") { ConfigSectionPlaceholder(title = stringResource(R.string.copy_config_section_diagnostics), onBack = { navController.popBackStack() }) }

    composable(CurroRoute.MoreApps.value) { /* unchanged */ }
}
```

**Pin: do NOT extend `CurroRoute` enum with the 7 nested routes** — they're
string literals because they're transient placeholders that each get replaced
inline by a single later SF. `navigation-patterns` rule 2 ("Curro's nav graph
is `LauncherScreen` ⇄ `ConfigMenuScreen` only") is preserved at the top-level
enum; the nested config routes are an implementation detail of the config
sub-graph.

### Hilt Modules

No new module. `ConfigViewModel` is `@HiltViewModel`-injectable using the
already-bound `AliasRepository` (US-046), `FailedCommandLog` (US-049), and
`SettingsRepository` (US-041 + this SF's two new flows).

`SettingsRepository`'s two new flows (`incomingCallModeEnabled`,
`sendFailuresEnabled`) are read-only in SF-8.1; their write-paths
(`setIncomingCallModeEnabled`, `setSendFailuresEnabled`) are also declared in
the interface so SF-8.7 / SF-8.8 don't have to extend it, but the SF-8.1 impl
in `SettingsDataStore` covers BOTH the read AND the write — the writes are
just not exercised by any caller in SF-8.1.

### Composables by Feature (checklist)

- [x] `ConfigMenuScreen` (collects the ViewModel + the nav callbacks)
- [x] Stateless `ConfigMenuContent`
- [x] `ConfigSectionRow` (navigable row)
- [x] `ConfigSectionToggleRow` (inline-toggle row)
- [x] `ConfigSectionPlaceholder` (the 7 not-yet-implemented destinations)
- [ ] (None of the other launcher/assistant composables touched.)
- [x] No empty-state needed in SF-8.1 — there are always 9 sections rendered.
- [x] Dark + large-font previews on `ConfigMenuScreen` and
      `ConfigSectionPlaceholder` (preserve the pattern from
      `ConfigMenuPlaceholderScreen`).

### Material Design Components

- `LazyColumn` for the 9 rows (don't use a plain `Column` — even 9 rows
  benefits from `LazyColumn`'s lifecycle hygiene, and Phase-8 sections grow).
- `Switch` for the two inline-toggle rows (Material 3's default; senior-first
  doesn't override here because the config menu is dense / for Fran).
- `IconButton` + `Icons.AutoMirrored.Filled.KeyboardArrowLeft` for back.
- `Icons.AutoMirrored.Filled.KeyboardArrowRight` for the section chevron.
- No card / no surface — the menu sits directly on `MaterialTheme.colorScheme.surface`.

## Acceptance Criteria

- [ ] **Opening from the gesture works end-to-end on the device** — 5 clock
      taps within 3 s → `ConfigMenuScreen` opens; the Phase-0 "Menú de Fran —
      vacío en Phase 0" string NEVER appears anywhere in the build.
- [ ] **All 9 sections render**, in the order: Alias / Apps favoritas / Voz y
      velocidad / Cuándo confirmar / Lo que Curro no entendió / Modo asistente
      de llamadas / Compartir fallos / Reset de aprendizaje / Versión y
      diagnóstico.
- [ ] **Counts populate from the real repositories** — adding an alias via a
      direct Room insert (or after SF-8.2 ships, via the alias UI) updates the
      "Alias de contactos" summary line within one Flow tick.
- [ ] **The back chevron at `TopStart` works**; no `TopAppBar`; no
      `statusBarsPadding` on the child screen.
- [ ] **All 7 navigable section rows route correctly** — tapping each opens a
      `ConfigSectionPlaceholder` with the right title; back returns to the menu.
- [ ] **The two inline toggle rows render the correct initial value** (both
      `false` on a fresh install) and the `onChange` is inert (a single
      `Log.w` line in logcat per flip; the toggle visually snaps back to the
      DataStore value).
- [ ] **`ConfigMenuPlaceholderScreen.kt` is deleted** — no references remain.
- [ ] **12 new strings exist in `app/src/main/res/values/strings.xml`** — no
      duplicates of existing IDs; each carries a `<!-- SF-8.1 (US-050) -->`
      comment.
- [ ] **The two new `SettingsRepository` keys exist and emit defaults** —
      `incoming_call_mode = false`, `send_failures = false`. Their setters
      compile but have no caller in SF-8.1.
- [ ] **Curro→user voice is unaffected** — the config menu is silent (it's
      Fran-only; no TTS). No new `copy_*` voice strings.
- [ ] **No new permissions, no new manifest entries, no new dependencies, no
      new telemetry event.**
- [ ] **Senior-first rules: N/A for this screen** (`launcher-ui` rule 5
      explicitly carves out the config menu from the ≥ 96 dp rule). Row height
      72 dp is documented as deliberate. Dark mode + `fontScale = 1.5f` /
      `2.0f` previews exist for `ConfigMenuScreen` and
      `ConfigSectionPlaceholder`.
- [ ] **No layout reshuffles** — section order is fixed and stable.
- [ ] **Build is green**: `./gradlew assembleDebug ktlintCheck detektDebug
      testDebugUnitTest`.

## Design Notes

This is a config screen — the only Curro surface where Material's default
densities apply. Follow `launcher-ui` rule 5 explicitly. Read tokens via
`MaterialTheme.typography.bodyLarge` for titles, `bodyMedium` for summaries,
`CurroSpacing.*` for gaps, `MaterialTheme.shapes.small` for row corners (only
if the row uses a `Card` surface — the proposed layout uses plain rows on
`surface`, no card).

The back chevron pattern is copy-paste from
`ConfigMenuPlaceholderScreen.kt` (deleted in this SF) and from
`MoreAppsScreen.kt` (kept). Verify both surfaces use exactly the same
chevron+padding shape — `navigation-patterns` rule 1's canonical pattern.

The destructive "Reset de aprendizaje" row uses
`MaterialTheme.colorScheme.error` for the title text — this is the only
section row that doesn't use `onSurface`. (`brand-design` rule 5: colour +
text together; the row title says "Reset de aprendizaje" so even if the
red tone is hard to read, the meaning is in the words.)

## Senior-UX & Copy

This screen is **not** senior-first — see `launcher-ui` rule 5. Fran is the
user; the visual scale is normal Material 3. The 9 section titles + 2 summary
templates + 1 placeholder string are below; the toggle help-line shorts land
here too, with the full help lines deferred to SF-8.7 / SF-8.8.

**No new spoken (TTS) strings** — the config menu is silent; Curro doesn't
talk inside it.

New entries in `app/src/main/res/values/strings.xml` (12 total; pin each
with a `<!-- SF-8.1 (US-050) — … -->` comment):

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_section_aliases` | "Alias de contactos" | section title |
| `copy_config_section_favourites` | "Aplicaciones favoritas" | section title |
| `copy_config_section_tts` | "Voz y velocidad de habla" | section title |
| `copy_config_section_thresholds` | "Cuándo confirmar antes de actuar" | section title |
| `copy_config_section_failures` | "Lo que Curro no ha entendido" | section title |
| `copy_config_section_incoming_call` | "Modo asistente de llamadas" | section title — reused in SF-8.7 |
| `copy_config_section_send_failures` | "Compartir fallos con Fran" | section title — reused in SF-8.8 |
| `copy_config_section_reset` | "Reset de aprendizaje" | section title — reused in SF-8.9 |
| `copy_config_section_diagnostics` | "Versión y diagnóstico" | section title — reused in SF-8.10 |
| `copy_config_summary_aliases_count` | "%1$d alias guardados" | summary; uses positional arg |
| `copy_config_summary_failures_count` | "%1$d fallos sin revisar" | summary; uses positional arg |
| `copy_config_section_placeholder` | "Próximamente" | the body line in `ConfigSectionPlaceholder` |
| `copy_config_incoming_call_help_short` | "Curro avisa por voz quién te llama." | SF-8.1's inline help; SF-8.7 adds the long form |
| `copy_config_share_failures_help_short` | "Comparte con Fran lo que Curro no entendió." | SF-8.1's inline help; SF-8.8 adds the long form |

That's 14 strings total — 12 SF-8.1-original + 2 short help lines pinned here
to avoid a backwards dependency from SF-8.1 on SF-8.7 / SF-8.8 (Fran reading
the menu in SF-8.1 needs the inline rows to be self-explanatory even before
the later SFs ship).

**Update the `brand-design` COPY table** — add the 14 rows to a new
"Config menu (Phase 8 — SF-8.1)" section with provenance `(NEW — SF-8.1)`.
Pin: the implementer MUST do this edit as part of the SF (not "later").

## Performance Considerations

- `LazyColumn` with 9 stable items — trivial. Use `key = ::sectionKey` (a
  function that returns the title `resId` for `Navigable` and the value of
  `value: Boolean` xor the resId for `Toggle`) to keep the recompositions
  stable across `ConfigUiState` updates.
- `combine` of 4 flows is fine — the `aliasRepo.observeAll()` and
  `failedLog.observeRecent(50)` are Room-backed and emit on actual data
  change; the two `settingsRepo` flows are DataStore-backed and emit on
  write. The `WhileSubscribed(5_000)` keeps the upstream alive for 5 s
  after the screen leaves composition (long enough for a quick back-and-
  forward navigation).
- The `initialValue` of `uiState` is a non-loading state with the
  correctly-built 9 sections (alias and failure counts shown as 0 until the
  first emission); this avoids a `Loading` UI flash.
- No image loading; no animations.

## Testing Requirements

Align with `testing-patterns`'s Curro list.

- [ ] **FSM**: N/A — no FSM transitions.
- [ ] **ConfigViewModel** — JVM with `MainDispatcherRule` + Turbine + fakes:
      - `FakeAliasRepository` (returns a controllable `Flow<List<AliasView>>`).
      - `FakeFailedCommandLog` (returns a controllable
        `Flow<List<FailedCommandEntity>>`).
      - `FakeSettingsRepository` (returns controllable `Flow<Boolean>` for the
        two new keys).
      - 6 cases:
        1. `uiState_emitsNineSections_initially`.
        2. `uiState_emits_aliasCountSummary_whenAliasesObservable_emits_2_aliases`.
        3. `uiState_emits_failuresCountSummary_whenFailedLog_emits_5_failures`.
        4. `uiState_incomingCallEnabled_reflects_settingsRepo_flow`.
        5. `uiState_sendFailuresEnabled_reflects_settingsRepo_flow`.
        6. `onEvent_ToggleChanged_logsWarning_doesNotMutateSettings` (assert
           `FakeSettingsRepository.setIncomingCallModeEnabled` is NEVER
           called).
- [ ] **`SettingsRepository`** — JVM tests for the two new flows:
      - `SettingsDataStoreIncomingCallModeTest` (3 cases): default `false`;
        round-trip; emits on change.
      - `SettingsDataStoreSendFailuresTest` (3 cases): default `false`;
        round-trip; emits on change.
- [ ] **No new handler tests** — no handler change.
- [ ] **No new `ConfidencePolicy` tests** — no policy change.
- [ ] **Instrumented UI tests on `ConfigMenuContent`**
      (`app/src/androidTest/java/com/curro/app/presentation/config/ConfigMenuContentTest.kt`):
      - 5 cases:
        1. `nineSectionRows_render`.
        2. `tappingAliasesRow_invokes_onNavigateToSection_with_config_aliases_route`.
        3. `backChevron_invokes_onBack`.
        4. `aliasCountSummary_renders_when_uiState_carries_2_aliases`.
        5. `incomingCallToggleRow_renders_with_off_state_by_default`.
      - Accessibility sweep: no `Image`/`Icon` without `contentDescription`;
        every `clickable` node has a non-zero touch target (config-menu rows
        are 72 dp, not 96 — pin the test asserts 72 specifically).
- [ ] **Dark-mode verification** — the existing previews on
      `ConfigMenuPlaceholderScreen` move to `ConfigMenuScreen` with the same
      `UI_MODE_NIGHT_YES` + `fontScale = 1.5f` + `fontScale = 2.0f` variants.
- [ ] **Real Redmi 15 smoke**:
      - 5 clock taps → menu opens.
      - All 9 rows visible without scrolling on the Redmi 15 (412 × 800 dp).
      - All 7 navigable rows route + back.
      - Both inline toggles render OFF on a fresh install; flipping logs the
        `Log.w` line in `adb logcat -s Curro/Config`.
      - Back to launcher works.

## Implementation Notes

**PM Owner**: this brief.

**Architect**: this brief contains the architect's sections (Android
Specification, Performance, Testing). Splitting between PM and architect for
this SF is artificial — the work is one composable + one VM + a manifest-
silent settings extension; one architect can write it in one sitting.

**Cross-SF coordination**:
- SF-8.2 → SF-8.10 each MUST replace exactly one `composable("config/<name>")`
  block; no nav-graph churn per SF. The 7 placeholder blocks land in SF-8.1.
- SF-8.7 wires the "Modo asistente de llamadas" toggle's behaviour: it
  changes `ConfigViewModel.onEvent` (or splits to a dedicated handler — pin
  the SF-8.7 brief). SF-8.1 declares the toggle but its `onChange` is inert.
- SF-8.8 wires "Compartir fallos con Fran" similarly.

**File-creation summary** (the implementer's checklist):

NEW:
- `app/src/main/java/com/curro/app/presentation/config/ConfigMenuScreen.kt`
- `app/src/main/java/com/curro/app/presentation/config/ConfigViewModel.kt`
- `app/src/main/java/com/curro/app/presentation/config/ConfigUiState.kt`
- `app/src/main/java/com/curro/app/presentation/config/ConfigEvent.kt`
- `app/src/main/java/com/curro/app/presentation/config/components/ConfigSectionRow.kt`
- `app/src/main/java/com/curro/app/presentation/config/components/ConfigSectionToggleRow.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/ConfigSectionPlaceholder.kt`
- `app/src/test/java/com/curro/app/presentation/config/ConfigViewModelTest.kt`
- `app/src/test/java/com/curro/app/data/local/SettingsDataStoreIncomingCallModeTest.kt`
- `app/src/test/java/com/curro/app/data/local/SettingsDataStoreSendFailuresTest.kt`
- `app/src/androidTest/java/com/curro/app/presentation/config/ConfigMenuContentTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
  (1 swap + 7 new placeholder routes).
- `app/src/main/java/com/curro/app/domain/repository/SettingsRepository.kt`
  (add 2 flows + 2 setters).
- `app/src/main/java/com/curro/app/data/local/SettingsDataStore.kt` (add 2
  keys + their getters/setters).
- `app/src/main/res/values/strings.xml` (+14 new entries).
- `.claude/skills/brand-design/SKILL.md` (+14 new rows in a new "Config menu
  (Phase 8 — SF-8.1)" section).

DELETED:
- `app/src/main/java/com/curro/app/presentation/config/ConfigMenuPlaceholderScreen.kt`
  (and its tests, if any).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.1 ConfigMenuScreen scaffold + 7 placeholder routes + 2 new SettingsRepository flows. |
