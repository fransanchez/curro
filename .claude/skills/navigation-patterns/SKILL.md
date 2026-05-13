---
name: navigation-patterns
description: Curro's (minimal) navigation — CurroNavHost is one Scaffold whose innerPadding wraps the NavHost (No-Double-Padding); the only two routes are the launcher home (start) and the Fran-only hidden config menu; the assistant's listening/processing/confirming/message-cards/contact-picker UI are state-driven overlays, not routes; no deep links, no bottom nav, no tabs; opening other apps is PackageManager intents, not in-app navigation.
triggers:
  - navigation
  - NavHost
  - NavController
  - route
  - composable route
  - back navigation
  - popBackStack
  - BackHandler
  - deep link
  - "No Double Padding"
  - config menu
  - launcher home
---

# Navigation Patterns (Curro)

Curro's navigation is **tiny on purpose**. It's a launcher, not a multi-screen app:
the home is always there, the assistant is overlays, and the only real "screen" you
navigate *to* is a hidden config menu for Fran. Source: `docs/curro-spec-v1.0.md`
§9, §11; the surfaces are in `launcher-ui`; the assistant FSM in `voice-interaction`;
the launcher/HOME mechanics in `launcher-app`; insets in `adaptive-layout`.

## The whole picture

- **`CurroNavHost`** is a single `Scaffold` that applies its `innerPadding`
  (status-bar inset included) to the `NavHost`. **Child screens never add their own
  `Scaffold` / `TopAppBar` / `statusBarsPadding()`** — that doubles the top padding
  (the **"No Double Padding"** rule — see `CLAUDE.md`).
- **Two nav routes, that's it:**
  - **`launcher`** — the launcher home (clock + ≥40%-screen mic button + 4–6 huge app
    tiles + "Más apps"). The **start destination**. (`MoreApps` — the full app list —
    can be a third route off `launcher`, or just an overlay on it; either is fine.)
  - **`config`** — the config menu. **Fran-only, hidden**: opened by tapping the
    launcher clock **5× within 3 s** (spec §9) — not a button, not a normal nav
    action; the launcher emits an event when it detects the gesture and *then* you
    `navController.navigate("config")`.
- **The assistant UI is state-driven overlays, NOT routes.** `listening` /
  `processing` / `confirming` / message-cards / contact-picker render *on top of* the
  launcher home, selected by a `StateFlow<AssistantState>` (owned by
  `assistant/AssistantStateMachine` — see `voice-interaction` / `compose-patterns`).
  No `composable("listening")`, no navigation between them — the FSM drives them.
- **No deep links.** It's a launcher; nothing links into it. (No `navDeepLink`, no
  `VIEW`/`BROWSABLE` intent filters for in-app destinations.)
- **No bottom navigation, no tabs, no `NavigationRail` / `NavigationSuiteScaffold` /
  `ListDetailPaneScaffold`** — single fixed phone, portrait, "feels the same every
  day" (see `adaptive-layout`).
- **Opening other apps is not in-app navigation** — it's a `PackageManager` launch
  intent (`packageManager.getLaunchIntentForPackage(pkg)` + `FLAG_ACTIVITY_NEW_TASK`),
  see `platform-integrations` / `launcher-app`. The app-grid tiles, "Más apps", and
  the `open_app` handler all do this.

## Routes

```kotlin
// presentation/navigation/Route.kt
object Routes {
    const val LAUNCHER = "launcher"     // start destination — the home
    const val MORE_APPS = "more_apps"   // (optional) the full app list, off the launcher
    const val CONFIG = "config"         // Fran-only; opened by the 5-tap-on-clock gesture
}
```

(The config menu *may* take a "section" enum arg if you want it to deep-jump to a
section — `config?section=aliases` — but it's optional; the simplest version takes
none and just shows the scrollable list.)

## `CurroNavHost`

```kotlin
// presentation/navigation/CurroNavHost.kt
@Composable
fun CurroNavHost(navController: NavHostController = rememberNavController()) {
    Scaffold { innerPadding ->                              // the ONE Scaffold — child screens don't add theirs
        NavHost(
            navController = navController,
            startDestination = Routes.LAUNCHER,
            modifier = Modifier.padding(innerPadding),      // status-bar inset applied here, once
        ) {
            composable(Routes.LAUNCHER) {
                LauncherScreen(
                    onOpenAllApps = { navController.navigate(Routes.MORE_APPS) },
                    onOpenConfig  = { navController.navigate(Routes.CONFIG) },   // fired by the 5-tap-on-clock gesture
                )
            }
            composable(Routes.MORE_APPS) {
                MoreAppsScreen(onBack = { navController.popBackStack() })        // launching an app also pops back to home
            }
            composable(Routes.CONFIG) {
                ConfigMenuScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
```

`MainActivity`: `enableEdgeToEdge()` + `setContent { CurroTheme { CurroNavHost() } }`
(see `launcher-app`). On `onNewIntent` / HOME, reset the assistant FSM to `idle` —
but the *nav* back stack should also be back at `launcher` (popping `config`/`more_apps`
when the user presses HOME is fine; he came home).

## Back navigation in a child screen — `Box` + overlay chevron (never a `TopAppBar`)

```kotlin
// e.g. ConfigMenuScreen — no Scaffold/TopAppBar (No-Double-Padding); back = a big chevron at TopStart
@Composable
fun ConfigMenuScreen(onBack: () -> Unit, viewModel: ConfigViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler { onBack() }                                // system back also leaves

    Box(Modifier.fillMaxSize()) {
        ConfigMenuContent(                                  // a scrollable Column of settings (this screen
            uiState = uiState, onEvent = viewModel::onEvent,//   can use a normal-density layout — it's for Fran)
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).size(96.dp),   // ≥ 96 dp — senior-first sizing still applies
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,   // chevron — 32 dp glyph
                contentDescription = "Volver",
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
```

`MoreAppsScreen` follows the same shape (back chevron at `TopStart`, `BackHandler`,
no `TopAppBar`). The launcher home (`LauncherScreen`) is the start destination — it
has nothing to go "back" to; pressing HOME from elsewhere just returns to it (handled
by the launcher's `singleTask` + `onNewIntent` — `launcher-app`).

## Navigating from a ViewModel — emit events, don't call `navController`

```kotlin
// The launcher VM exposes the config-open gesture as an event; the screen does the navigation.
sealed interface LauncherEffect {
    data object OpenConfig : LauncherEffect       // emitted when the clock-tap gesture completes
    data object OpenAllApps : LauncherEffect
}

@Composable
fun LauncherScreen(onOpenConfig: () -> Unit, onOpenAllApps: () -> Unit, viewModel: LauncherViewModel = hiltViewModel()) {
    val effect by viewModel.effect.collectAsStateWithLifecycle(null)
    LaunchedEffect(effect) {
        when (effect) {
            LauncherEffect.OpenConfig  -> onOpenConfig()
            LauncherEffect.OpenAllApps -> onOpenAllApps()
            null -> Unit
        }
    }
    LauncherContent(/* … */)
}
```

(The assistant overlays are *not* navigated this way — they're rendered from the
`AssistantState` `StateFlow` directly; see `compose-patterns` / `voice-interaction`.)

## Rules

1. **"No Double Padding"** — `CurroNavHost` is the one `Scaffold`; it pads the `NavHost`; child screens never add their own `Scaffold` / `TopAppBar` / `statusBarsPadding()`. Back navigation = `Box` + overlay `Icons.AutoMirrored.Filled.KeyboardArrowLeft` at `Alignment.TopStart`, sized ≥ 96 dp with a ~32 dp glyph.
2. **Two routes only** — `launcher` (start) and `config` (Fran-only, opened by the 5-tap-on-clock gesture; not a normal nav action). (`more_apps` is an optional third off the launcher.)
3. **The assistant UI is state-driven overlays, not nav routes** — driven by `StateFlow<AssistantState>` (`assistant/AssistantStateMachine`).
4. **No deep links, no bottom nav, no tabs, no adaptive nav scaffolds** — single fixed phone; opening other apps is `PackageManager` intents, not in-app navigation.
5. **ViewModels emit navigation events; the screen calls `navController`** — and the config-open is a *gesture*, surfaced as an event.
6. **HOME / `onNewIntent` resets to a clean state** — assistant FSM → `idle`, back stack → `launcher` (see `launcher-app`).
