---
name: compose-patterns
description: Jetpack Compose architectural patterns for Curro — ViewModel + StateFlow + sealed UiState/Event, stateless Content composables, LaunchedEffect side effects, remember/rememberSaveable, LazyColumn/LazyVerticalGrid, Coil AsyncImage, @Preview (light + dark + large-font), Material-3-with-CurroTheme, and the state-driven assistant overlays.
triggers:
  - "@Composable"
  - composable
  - state
  - StateFlow
  - UiState
  - ViewModel
  - hiltViewModel
  - remember
  - rememberSaveable
  - LaunchedEffect
  - modifier
  - LazyColumn
  - LazyVerticalGrid
  - AsyncImage
  - "@Preview"
  - recomposition
---

# Jetpack Compose Patterns (Curro)

Kotlin / Jetpack Compose architectural patterns for the Curro app. The visual tokens
(colours, type scale, radii) are owned by `brand-design` (a template until filled in);
the actual surfaces (launcher home, assistant overlays, config menu) by `launcher-ui`;
a11y mechanics by `accessibility-patterns`; the Material foundation by `material-design`;
the assistant FSM the overlays follow by `voice-interaction`. See `CLAUDE.md` for the
full ViewModel/Screen patterns and the package layout.

## Screen structure

### ViewModel via `hiltViewModel()`

```kotlin
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel = hiltViewModel(),
    onOpenConfig: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()

    LauncherContent(
        uiState = uiState,
        assistantState = assistantState,
        onEvent = viewModel::onEvent,
        onOpenConfig = onOpenConfig,
    )
}
```

### Stateless `Content` composable

```kotlin
@Composable
fun LauncherContent(
    uiState: LauncherUiState,
    assistantState: AssistantState,
    onEvent: (LauncherEvent) -> Unit,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // The fixed home: big clock + ≥40%-screen mic button + 4–6 huge app tiles
        Column(Modifier.fillMaxSize()) {
            ClockBlock(
                time = uiState.time, date = uiState.date,
                onFiveTaps = { onEvent(LauncherEvent.OpenConfigGesture) },   // 5 taps / 3 s — see launcher-ui
            )
            MicButton(onPress = { onEvent(LauncherEvent.MicPressed) })
            AppTileGrid(apps = uiState.favouriteApps, onTap = { onEvent(LauncherEvent.AppTileTapped(it)) })
            BigPrimaryButton("Más apps", onClick = { onEvent(LauncherEvent.OpenAllApps) })
        }
        // The assistant overlay for the current state — NOT a nav route (see below)
        AssistantOverlay(state = assistantState, onEvent = onEvent)
    }
}
```

## State management — sealed `UiState` / `Event`

```kotlin
sealed interface LauncherUiState {
    data object Loading : LauncherUiState
    data class Ready(
        val time: String,
        val date: String,
        val favouriteApps: List<AppEntry>,
        val unreadCount: Int,
    ) : LauncherUiState
}

sealed interface LauncherEvent {
    data object MicPressed : LauncherEvent
    data class AppTileTapped(val packageName: String) : LauncherEvent
    data object OpenAllApps : LauncherEvent
    data object OpenConfigGesture : LauncherEvent
    data object Refresh : LauncherEvent             // on onResume — see "no pull-to-refresh"
}
```

### ViewModel with `StateFlow`

```kotlin
@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val observeHomeState: ObserveHomeStateUseCase,   // clock + favourites + unread badge
    private val assistantStateMachine: AssistantStateMachine,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LauncherUiState>(LauncherUiState.Loading)
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    // The single source of truth for which overlay shows (owned by assistant/AssistantStateMachine)
    val assistantState: StateFlow<AssistantState> = assistantStateMachine.state

    init {
        viewModelScope.launch {
            observeHomeState().collect { home -> _uiState.value = LauncherUiState.Ready(home.time, home.date, home.favourites, home.unread) }
        }
    }

    fun onEvent(event: LauncherEvent) {
        when (event) {
            LauncherEvent.MicPressed       -> assistantStateMachine.onMicPressed()      // → listening (or interrupt)
            is LauncherEvent.AppTileTapped -> launchApp(event.packageName)
            LauncherEvent.OpenAllApps      -> /* navigate to MoreApps */ Unit
            LauncherEvent.OpenConfigGesture-> /* navigate to Config */ Unit
            LauncherEvent.Refresh          -> /* re-pull clock/favourites/unread */ Unit
        }
    }
}
```

## The assistant's UI states are state-driven overlays (not nav routes)

The `listening` / `processing` / `confirming` / message-cards / contact-picker UI are
**overlays on top of the launcher home**, selected by a single
`StateFlow<AssistantState>` owned by `assistant/AssistantStateMachine` (see
`voice-interaction`). The launcher screen *observes* it and renders the right overlay —
there is **no navigation** for them; the only nav routes are the launcher home and the
(Fran-only, hidden) config menu (see `navigation-patterns`).

```kotlin
@Composable
fun AssistantOverlay(state: AssistantState, onEvent: (LauncherEvent) -> Unit) {
    when (state) {
        AssistantState.Idle             -> Unit                       // home only
        is AssistantState.Listening     -> ListeningOverlay(partialTranscript = state.partial)
        AssistantState.Processing       -> ProcessingOverlay()        // "Un momento…" + static indicator
        is AssistantState.Confirming    -> ConfirmationOverlay(prompt = state.prompt, candidates = state.candidates)
        is AssistantState.Executing     -> ExecutingOverlay(content = state.content)   // message cards / "Llamando a…"
        is AssistantState.ErrorRecovery -> ErrorMessage(state.message)
    }
}
```

## Side effects with `LaunchedEffect`

```kotlin
@Composable
fun MoreAppsScreen(viewModel: MoreAppsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val effect by viewModel.effect.collectAsStateWithLifecycle(null)

    LaunchedEffect(Unit) { viewModel.onEvent(MoreAppsEvent.Load) }
    LaunchedEffect(effect) {
        when (effect) {
            is MoreAppsEffect.LaunchedApp -> onBack()      // app launched → return to home
            null -> Unit
        }
    }
    BackHandler { onBack() }

    MoreAppsContent(uiState = uiState, onEvent = viewModel::onEvent)
}
```

## State hoisting — `remember` / `rememberSaveable`

```kotlin
// The config menu (Fran-only) — local UI state that should survive rotation
@Composable
fun ConfigMenuContent(uiState: ConfigUiState, onEvent: (ConfigEvent) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }                 // alias-search box
    var editingAlias by remember { mutableStateOf<ContactAlias?>(null) } // transient: which row is open

    Column {
        SettingsSwitchRow(
            title = "Modo asistente de llamadas",
            checked = uiState.incomingCallMode,
            onCheckedChange = { onEvent(ConfigEvent.SetIncomingCallMode(it)) },
        )
        SettingsSliderRow(
            title = "Umbral de ejecución directa",
            value = uiState.confExecuteMin, range = 0f..1f,
            onValueChange = { onEvent(ConfigEvent.SetExecuteThreshold(it)) },
        )
        AliasList(aliases = uiState.aliases.filter { it.alias.contains(query) }, onEdit = { editingAlias = it })
    }
}
```

## Modifier best practices

```kotlin
@Composable
fun BigCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(CurroSpacing.Medium)
            .clip(CurroShapes.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = { Column(Modifier.padding(CurroSpacing.Large), content = content) },
    )
}

// Modifier is always the first optional parameter; callers chain onto it
BigCard(modifier = Modifier.padding(CurroSpacing.Large)) { /* … */ }
```

## `LazyColumn` / `LazyVerticalGrid`

### `LazyColumn` (e.g. the WhatsApp message cards, "Más apps" list)

```kotlin
@Composable
fun MessageCardsContent(groups: List<SenderMessages>, readingNow: MessageId?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CurroSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(CurroSpacing.Medium),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.sender}") {
                Text(group.sender, style = MaterialTheme.typography.headlineMedium)   // grouped by sender (not time)
            }
            items(group.messages, key = { it.id }) { msg ->
                MessageCard(message = msg, highlighted = msg.id == readingNow)         // read-aloud one highlighted
            }
        }
    }
}
```

If a list ever mixes item types, branch in the item block (`when (item) { … }`) —
keep `key` stable so recomposition stays cheap.

### `LazyVerticalGrid` (the launcher app-tile grid)

```kotlin
@Composable
fun AppTileGrid(apps: List<AppEntry>, onTap: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),                       // 2×2 or 2×3 — 4–6 huge tiles
        modifier = modifier,
        contentPadding = PaddingValues(CurroSpacing.Large),
        horizontalArrangement = Arrangement.spacedBy(CurroSpacing.XLarge),   // wide gaps — senior-first
        verticalArrangement = Arrangement.spacedBy(CurroSpacing.XLarge),
    ) {
        items(apps, key = { it.packageName }) { app -> AppTile(app = app, onTap = { onTap(app.packageName) }) }
    }
}
```

> **No pull-to-refresh.** Curro doesn't pull-to-refresh anything — the launcher home
> just refreshes on `onResume` (clock, unread badge, favourites grid; the favourites
> grid recomputes *occasionally*, not on every open — `local-data`). There is no
> swipe-down-to-reload anywhere.

## Compose previews — light + dark + large-font

```kotlin
@Preview(name = "Light", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 915)
@Preview(name = "Large font", showBackground = true, fontScale = 2.0f, widthDp = 412, heightDp = 915)
@Composable
private fun LauncherContentPreview() {
    CurroTheme {
        LauncherContent(
            uiState = LauncherUiState.Ready(time = "12:47", date = "Miércoles 13 mayo",
                favouriteApps = sampleApps(), unreadCount = 3),
            assistantState = AssistantState.Idle,
            onEvent = {}, onOpenConfig = {},
        )
    }
}
```

Every reusable component (`BigPrimaryButton`, `BigCard`, `BigListRow`, the overlays)
gets the same trio. The large-font variant is not optional — this user will have big
fonts on.

## Image loading with Coil (`AsyncImage`)

Curro shows little imagery — mostly contact photos and app icons:

```kotlin
@Composable
fun ContactPhoto(uri: Uri?, name: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(false).build(),
        contentDescription = null,                          // decorative — the name label says it
        modifier = modifier.size(CurroSpacing.XLarge * 2).clip(CurroShapes.Small),
        contentScale = ContentScale.Crop,
        error = rememberVectorPainter(Icons.Default.Person),
    )
}
```

No crossfade theatrics ("no fussy animation"); a calm fallback when there's no photo.

## Material 3 components with `CurroTheme`

```kotlin
@Composable
fun ConfigMenuChrome(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // No Scaffold / TopAppBar in a child screen (No-Double-Padding — CLAUDE.md): Box + overlay chevron
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(CurroSpacing.Large), content = content)
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).size(96.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Volver", Modifier.size(48.dp))
        }
    }
}

@Composable
fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(CurroSpacing.Large),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
```

Curro uses **very few** Material navigation components — no bottom nav, no tabs (it's a
launcher); only the config menu's back chevron. See `material-design` for the component
anatomy and `navigation-patterns` for what little routing there is.

## No hard-coded strings / literals

- Spanish strings come from resources / the copy module, in Curro's voice
  (`brand-design`) — **never** hard-coded in composables (the literals in the snippets
  above are illustrative; real code uses `stringResource(...)`).
- Tokens via `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` /
  `CurroSpacing.*` / `CurroShapes.*` — **never** raw `Color(0xFF…)` / `.sp` / `.dp`
  literals (except the few intrinsic ones like `0.dp`, icon glyph sizes — and even
  those should usually be a `CurroSpacing` value).
- `contentDescription` on every `Image`/`Icon` (or `null` if decorative).

## File locations (per `CLAUDE.md`)

- **Launcher / overlays UI**: `presentation/launcher/` (`LauncherScreen`, `LauncherViewModel`,
  `AppTileGrid`, `AppTile`, `ClockBlock`, `MicButton`, `MoreAppsScreen`) and the
  assistant overlays in `presentation/assistant/` (`ListeningOverlay`, `ProcessingOverlay`,
  `ConfirmationOverlay`, `MessageCardsScreen`, `ContactPickerOverlay`).
- **Config menu**: `presentation/config/` (`ConfigMenuScreen`, `ConfigViewModel`, …).
- **Shared big components**: `presentation/common/` (`BigPrimaryButton`, `BigYesNoRow`,
  `BigCard`, `BigListRow`, …).
- **Navigation**: `presentation/navigation/` (`CurroNavHost`, routes).
- **Theme**: `presentation/theme/` (`CurroTheme`, `CurroColorScheme`, `CurroTypography`,
  `CurroShapes`, `CurroSpacing`).
- **Domain / data**: `domain/` (models, repository interfaces, use cases),
  `data/` (`data/local/`, `data/voice/`, `data/notification/`, `data/telephony/`,
  `data/apps/`, `data/contacts/`, `data/ml/`).
- **Assistant orchestration**: `assistant/` (`AssistantStateMachine`, `AssistantCoordinator`,
  `ConfidencePolicy`); **handlers**: `handler/`; **services**: `service/`.

## Rules

1. **ViewModel + `StateFlow` + sealed `UiState`/`Event`; stateless `Content` composables that receive state and emit events** — `Modifier` first optional param; hoist state to the ViewModel.
2. **The assistant overlays are state-driven** — the launcher screen observes one `StateFlow<AssistantState>` (`assistant/AssistantStateMachine`) and renders the overlay for the current state; **not** navigation routes. Only the launcher home + config menu are routes.
3. **No pull-to-refresh anywhere** — the launcher refreshes on `onResume`; favourites recompute occasionally, not on every open.
4. **No hard-coded Spanish strings** (use resources / the copy module — `brand-design` owns the voice) and **no raw `Color`/`.sp`/`.dp` literals** (use `CurroTheme` tokens).
5. **`@Preview` every reusable component — light, dark, AND a large-font (`1.5f`/`2.0f`) variant**; it must survive the large-font case.
6. **No Scaffold/TopAppBar/statusBarsPadding() in child screens** (No-Double-Padding — `CLAUDE.md`); back navigation = `Box` + overlay chevron at `TopStart`, sized ≥ 96 dp.
