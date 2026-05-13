# US-007 — `CurroNavHost` shell + `MainActivity` launcher Activity

> Implementation brief for **SF-0.6** (`docs/master-plan.md` → Phase 0 → §6).
> US-006 finished the four shared big bricks (`BigPrimaryButton`, `BigCard`,
> `BigYesNoRow`, `BigListRow`) under `presentation/common/`. US-007 lands the
> navigation shell that every Phase 1+ UI surface will plug into: one
> `CurroNavHost` Scaffold wrapping a two-route `NavHost`
> (`launcher` start + `config` stub), `MainActivity` upgraded to the launcher
> Activity contract (singleTask + portrait + edge-to-edge — but **NOT**
> `CATEGORY_HOME` yet; that ships with SF-1.1), and two placeholder screens
> that exercise the route graph end-to-end on the emulator. This is the final
> structural step of Phase 0; SF-0.8 (telemetry) is the only Phase 0 work that
> follows, and Phase 1 then starts laying the real launcher home on top of
> the shell US-007 lays here.
>
> **Architect involvement: NOT REQUIRED.** Every load-bearing decision was
> resolved upstream: `navigation-patterns` rules 1–6 pin the shape (one
> `Scaffold`, two routes, No-Double-Padding, `Box` + overlay chevron for
> back, state-driven assistant overlays, no deep links / bottom nav);
> `launcher-app` "Manifest — declaring the launcher" pins the Activity
> attributes (`singleTask`, `clearTaskOnLaunch`, `stateNotNeeded`, portrait —
> with the explicit "no `CATEGORY_HOME` in this SF" carve-out documented in
> master-plan SF-0.6); US-004 A2 pins `Dimens.MinTapTarget = 96.dp` and
> `Dimens.LargeIconSize = 48.dp` (the back chevron uses both); US-005 fixed
> the `cd_*` content-description naming convention is documented here as
> the new precedent (the four new strings are labels/affordances, not
> spec-§6 voice copy, so they live in `strings.xml` but not in
> `brand-design`'s canonical COPY table); US-006 ships the precedent of
> "value-only files, no consumer wiring beyond previews/scaffold" that
> US-007 mirrors. The four shape questions (`enum` vs `sealed interface`
> for the route registry; how to surface the config route from the
> placeholder; whether the placeholder screens get `BackHandler`; how to
> name the back-chevron content-description string) are pinned in §Open
> shape questions, pinned below; the developer follows.

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `CurroNavHost` shell + `MainActivity` launcher Activity |
| **US ID** | US-007 |
| **SF ID** | SF-0.6 — master-plan |
| **Phase** | 0 — Project foundation |
| **Status** | In Progress |
| **Created** | 2026-05-14 |
| **Modified** | 2026-05-14 |
| **PM Owner** | Fran (Claude `android-product-analyst`) |
| **Architect** | Not required — `navigation-patterns` rules 1–6 + `launcher-app` "Manifest — declaring the launcher" pin every load-bearing decision; the four open shape questions are pinned in §Open shape questions, pinned. |

## Summary

Land the navigation shell every Phase 1+ UI surface will plug into.
**`CurroNavHost`** — a single `Scaffold` whose `innerPadding` wraps a
two-route `NavHost`. **`LauncherPlaceholderScreen`** — a stand-in
"Curro listo" screen that Phase 1 (SF-1.1 onwards) gradually replaces
with the real launcher home (clock + mic button + app tiles + "Más
apps"); for now it carries a tiny debug `TextButton` that fires
`onOpenConfig()` so the shell is actually verifiable end-to-end on the
emulator (the real "5 taps on clock" gesture lands with SF-1.6).
**`ConfigMenuPlaceholderScreen`** — a stand-in "Menú de Fran — vacío en
Phase 0" stub with a senior-first ≥ 96 dp back chevron at TopStart per
`navigation-patterns`'s explicit pattern; Phase 8 SF-8.x fills it with
real sections (aliases / favourites / TTS settings / confidence sliders /
failed-commands log / diagnostics).

**`MainActivity`** is upgraded with `launchMode="singleTask"`,
`clearTaskOnLaunch="true"`, `stateNotNeeded="true"`,
`screenOrientation="portrait"`, `windowSoftInputMode="adjustResize"`,
and its `setContent { }` body switches from the US-001 invariant
(`Surface { Text(stringResource(R.string.app_name)) }`) to
`CurroNavHost()`. The US-001 invariant that froze `MainActivity` for
six SFs is deliberately **lifted** here — US-007 is the first SF since
US-001 allowed to touch `MainActivity`, because the *only* reason for
the freeze was "wait until the nav shell exists, then plug it in at
exactly one call site". That moment is now.

**`CATEGORY_HOME` is deliberately NOT added to the manifest yet.**
Master-plan SF-0.6 calls this out in writing: landing the HOME intent
filter (and the `RoleManager.ROLE_HOME` flow that goes with it) before
Curro has a real launcher home to offer would hijack the dev device's
actual launcher and break the dev's ability to use the phone normally
between commits. SF-1.1 ships `CATEGORY_HOME` + the
"Hazme tu pantalla de inicio" CTA atomically; until then, Curro
appears in the app drawer only (the `MAIN + LAUNCHER` filter is
sufficient for that).

**Navigation Compose** is activated from its US-001-reserved slot in
`gradle/libs.versions.toml`. US-001 reserved a number of versions for
later SFs (Room, DataStore, MediaPipe, Coil, Firebase, PostHog) but —
on re-reading the actual catalog — Navigation Compose was **not**
pre-reserved. US-007 introduces both the version pin and the library
entry, mirroring the reserved-pattern shape so the catalog stays
internally consistent.

**Four new string resources** land in `strings.xml`: the placeholder
title, the placeholder debug-affordance label, the config stub title,
and `cd_back` (the back-chevron content description). The first three
are Phase-0-only and explicitly flagged for retirement at SF-1.1 /
SF-8.x (the `<!-- comment -->` over each documents the SF that
replaces it); `cd_back` is the one that survives — every back chevron
from US-007 onward (SF-1.5's `MoreAppsScreen`, SF-8.x's `ConfigMenuScreen`)
uses it. The `cd_*` prefix formalises the convention "content
descriptions live alongside copy strings but follow a
content-description naming, not a copy-table naming". **None of the
four go into `brand-design`'s canonical COPY table** — they're not
Curro's spoken voice (the COPY table covers what Curro says out loud
in spec §6's flows); they're chrome/affordance text. The brief
documents this distinction explicitly so US-005's COPY discipline
isn't accidentally widened.

US-007 ships **no real consumer** of anything from `presentation/common/`
beyond a single `TextButton` (Material's built-in) in
`LauncherPlaceholderScreen`. `BigPrimaryButton` / `BigCard` /
`BigYesNoRow` / `BigListRow` stay unused at the call-site level — the
SFs that consume them (SF-1.1 with `BigPrimaryButton`, SF-1.5 with
`BigListRow`, SF-5.x with `BigYesNoRow`, etc.) all land downstream of
SF-0.6. US-007 is the final structural brick of Phase 0; the consumer
surfaces are the whole story of Phase 1+.

Spec ref: `docs/curro-spec-v1.0.md` §11 (launcher UX — the home is
the start destination; the config menu is "Fran-only, hidden"). Master-plan
ref: SF-0.6 ("`CurroNavHost` shell"; "the `CATEGORY_HOME` intent filter
is not added yet — that ships with Phase 1, deliberately, to avoid
hijacking the home screen on the dev device before the real launcher
home exists"). Skills consumed: `navigation-patterns` (the
authoritative `CurroNavHost` shape — single `Scaffold` whose
`innerPadding` wraps the `NavHost`, two routes, No-Double-Padding rule,
`Box` + overlay chevron for back, state-driven overlays not routes,
no deep links / bottom nav / tabs); `launcher-app` (the Activity
attributes — minus `CATEGORY_HOME`); `compose-patterns` (stateless
`Content` composables receiving callbacks; previews); `accessibility-patterns`
(the back chevron's 96 dp tap target wrapping a 48 dp glyph,
`contentDescription` via `cd_back`); `launcher-ui` (the senior-first
sizing the placeholders still respect, even though they're
debug-only).

## Scope

### In Scope

- **`app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`** —
  a new file. The composable + the route registry live in the same file
  (`enum class CurroRoute`); both are top-level (the route registry is
  reused by future SFs and shouldn't be nested in the composable's body).

  ```kotlin
  /**
   * Curro's single navigation host.
   *
   * One [Scaffold] whose [innerPadding] wraps the [NavHost]. Child screens
   * NEVER add their own [Scaffold] / `TopAppBar` / `statusBarsPadding()` —
   * that doubles the top inset (the **No-Double-Padding rule**;
   * `navigation-patterns` rule 1; `CLAUDE.md` "Screen Layout"). Back
   * navigation in a child screen = a large overlay chevron at TopStart in
   * a [Box], not a [TopAppBar].
   *
   * Two routes, that's it (`navigation-patterns` rule 2):
   * - [CurroRoute.Launcher] — start destination. The launcher home (a
   *   placeholder in Phase 0; the real clock + mic button + app grid +
   *   "Más apps" lands across SF-1.1 → SF-1.5).
   * - [CurroRoute.ConfigMenu] — the hidden Fran-only menu (a placeholder
   *   in Phase 0; SF-8.x fills it). Opened from the launcher; in Phase 0
   *   the placeholder ships a debug `TextButton` that opens it directly,
   *   in Phase 1 the canonical entry is the 5-taps-on-clock gesture
   *   (SF-1.6).
   *
   * The assistant's listening / processing / confirming / message-cards /
   * picker UI are **state-driven overlays**, NOT new nav routes
   * (`navigation-patterns` rule 3; `voice-interaction`). They render on
   * top of the launcher route, selected by a `StateFlow<AssistantState>`
   * owned by `assistant/AssistantStateMachine` (Phase 5). Adding them as
   * routes would force them through navigation transitions; they're
   * UI-state changes, not navigation.
   *
   * No deep links, no bottom nav, no tabs, no [NavigationRail] / adaptive
   * nav scaffolds (`navigation-patterns` rule 4; `adaptive-layout` — Curro
   * is one fixed phone, portrait). Opening other apps from the launcher
   * is a `PackageManager` intent (SF-1.4), not in-app navigation.
   *
   * @param modifier Applied to the [Scaffold]. Callers typically pass
   *   [Modifier]; [MainActivity] does not pass anything extra.
   */
  @Composable
  fun CurroNavHost(modifier: Modifier = Modifier) {
      val navController = rememberNavController()
      Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
          NavHost(
              navController = navController,
              startDestination = CurroRoute.Launcher.value,
              modifier = Modifier.padding(innerPadding),
          ) {
              composable(CurroRoute.Launcher.value) {
                  LauncherPlaceholderScreen(
                      onOpenConfig = { navController.navigate(CurroRoute.ConfigMenu.value) },
                  )
              }
              composable(CurroRoute.ConfigMenu.value) {
                  ConfigMenuPlaceholderScreen(
                      onBack = { navController.popBackStack() },
                  )
              }
          }
      }
  }

  /**
   * Curro's nav route registry. Two routes, by design
   * (`navigation-patterns` rule 2).
   *
   * The string value is the on-the-wire route key used by
   * [androidx.navigation.compose.composable] and [NavController.navigate];
   * an [enum class] is the simplest shape that exposes both an
   * exhaustive-when surface and a stable string value (Q1 of §Open shape
   * questions, pinned below).
   */
  enum class CurroRoute(val value: String) {
      Launcher("launcher"),
      ConfigMenu("config"),
  }
  ```

  Implementation notes:
  - `Scaffold` paints `MaterialTheme.colorScheme.background` by default —
    no `Surface` wrap needed at this level or in `MainActivity`. The
    placeholder screens render directly inside the `Scaffold`'s content
    slot via the `NavHost`.
  - **No `BackHandler` in `CurroNavHost`** — the system back action
    already pops `config` back to `launcher` via Navigation Compose's
    default behaviour. The back chevron in `ConfigMenuPlaceholderScreen`
    is the visible affordance; the system back is the redundant one. See
    Q3 of §Open shape questions, pinned.
  - The `rememberNavController()` lives inside the composable, not hoisted
    — Phase-0 simplicity. Future SFs that need to navigate from a
    `ViewModel` will follow the `navigation-patterns` pattern (ViewModels
    emit `LauncherEffect.OpenConfig` events; the screen collects them and
    calls `navController.navigate(...)`), which doesn't require hoisting
    the controller above this level.

- **`app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`** —
  a new file. The Phase-0-end placeholder. Renders a centred "Curro
  listo" title in `displayMedium` over the surface; below the title, a
  small `TextButton` reading "Ajustes (depuración)" that fires
  `onOpenConfig()`. The debug button is the *only* way to reach the
  config route in Phase 0 — the canonical "5 taps on clock" gesture
  lands with SF-1.6, and SF-1.6 is downstream of the entire Phase 0
  scaffolding work US-007 finishes. Without the debug button, the nav
  graph would be unreachable end-to-end and the AC "navigates to the
  stub config menu and back" would have no manual verification path.

  ```kotlin
  /**
   * Phase-0 placeholder for the launcher home. Replaced piecewise across
   * SF-1.1 → SF-1.5 by the real surface — clock + date (SF-1.2), the
   * ≥ 40 % mic button (SF-1.3), the favourite-apps grid (SF-1.4), and the
   * "Más apps" entry point (SF-1.5). SF-1.6 wires the 5-taps-on-clock
   * gesture as the canonical config-menu entry; until then this screen's
   * debug [TextButton] is the only way to reach [CurroRoute.ConfigMenu].
   *
   * **This screen will be deleted at SF-1.1** — it has no carry-over.
   * The `R.string.launcher_placeholder_*` resources go with it.
   *
   * Senior-first contract: the title respects [MaterialTheme.typography.displayMedium]
   * (US-005's senior-first scale — 36 sp SemiBold) so even the placeholder
   * reads at the senior size if Fran's father sees it on an early
   * checkpoint build. The debug button uses `TextButton` deliberately
   * (not `BigPrimaryButton`) — it's NOT a CTA the user should see; the
   * subdued styling and the "(depuración)" label make its dev-only nature
   * explicit.
   *
   * No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()` —
   * [CurroNavHost]'s `Scaffold` already pads (No-Double-Padding rule).
   */
  @Composable
  fun LauncherPlaceholderScreen(
      onOpenConfig: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                  text = stringResource(R.string.launcher_placeholder_title),
                  style = MaterialTheme.typography.displayMedium,
                  color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(CurroSpacing.xxl))
              // Phase-0 debug affordance — the canonical "5 taps on clock" gesture lands with SF-1.6.
              // This TextButton is removed at SF-1.1 along with the rest of the placeholder.
              TextButton(onClick = onOpenConfig) {
                  Text(
                      text = stringResource(R.string.launcher_placeholder_open_config_debug),
                      style = MaterialTheme.typography.labelLarge,
                  )
              }
          }
      }
  }
  ```

  Required `@Preview`s (private, in the same file) — four canonical
  variants mirroring US-006's pattern:
  - `LauncherPlaceholderLightPreview` — `widthDp = 412, heightDp = 800`
  - `LauncherPlaceholderDarkPreview` — `uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 800`
  - `LauncherPlaceholderLargeFontPreview` — `widthDp = 412, heightDp = 800, fontScale = 1.5f`
  - `LauncherPlaceholderHugeFontPreview` — `widthDp = 412, heightDp = 800, fontScale = 2.0f`

  Each preview body: `CurroTheme { Surface(Modifier.fillMaxSize()) { LauncherPlaceholderScreen(onOpenConfig = {}) } }`.
  The 2.0× preview is the senior-first regression — `displayMedium` at
  2.0× = ~72 sp, the title still fits on one line at 412 dp width
  (verifiable in the preview pane).

- **`app/src/main/java/com/curro/app/presentation/config/ConfigMenuPlaceholderScreen.kt`** —
  a new file. The Phase-0 placeholder for the Fran-only config menu.
  Per `navigation-patterns`'s back-navigation pattern: a `Box` body with
  a centred "Menú de Fran — vacío en Phase 0" title and an overlay
  `IconButton` at TopStart carrying a `Icons.AutoMirrored.Filled.KeyboardArrowLeft`
  glyph. The IconButton's tap surface is exactly `Dimens.MinTapTarget`
  (96 dp); the chevron glyph inside it is exactly `Dimens.LargeIconSize`
  (48 dp). Both `Dimens` entries already exist from US-004 A2.

  ```kotlin
  /**
   * Phase-0 placeholder for the Fran-only config menu. SF-8.x fills it with
   * the real sections (aliases / favourite apps / TTS voice & rate /
   * confidence thresholds / "always confirm" toggle / failed-commands log /
   * "send failures to Fran" toggle / reset learning / version &
   * diagnostics — spec §9). For now it's a stub that proves the nav route
   * is reachable.
   *
   * **This screen will be replaced at SF-8.1**, not deleted — the file
   * name will move to `ConfigMenuScreen.kt` with a real `ConfigViewModel`,
   * but US-007's placeholder is the structural precedent for the back
   * chevron + Box-overlay pattern (`navigation-patterns`'s rule 1
   * back-navigation shape).
   *
   * Senior-first contract:
   * - Title at [MaterialTheme.typography.titleLarge] (US-005's
   *   22 sp SemiBold).
   * - Back chevron in a [Dimens.MinTapTarget] × [Dimens.MinTapTarget]
   *   IconButton wrapping a [Dimens.LargeIconSize] glyph
   *   (`navigation-patterns` rule 1 sizing).
   * - Chevron tap fires the system [LocalHapticFeedback] (TODO: deferred —
   *   `IconButton` inherits Material's standard ripple but no haptic by
   *   default; US-006's `BigPrimaryButton` adds `LongPress` haptic
   *   explicitly. The chevron is dev-only Phase-0 chrome, so the brief
   *   does NOT require haptic here; SF-8.1 may add it when the real
   *   menu lands. See Q4 of §Open shape questions, pinned.)
   *
   * No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()` —
   * [CurroNavHost]'s `Scaffold` already pads (No-Double-Padding rule).
   * Back navigation = the overlay chevron; the system back action also
   * pops (Navigation Compose default behaviour).
   */
  @Composable
  fun ConfigMenuPlaceholderScreen(
      onBack: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      Box(modifier = modifier.fillMaxSize()) {
          // Centred title.
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                  text = stringResource(R.string.config_placeholder_title),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
              )
          }
          // Back chevron at TopStart — navigation-patterns' canonical pattern.
          IconButton(
              onClick = onBack,
              modifier = Modifier
                  .align(Alignment.TopStart)
                  .padding(start = CurroSpacing.s, top = CurroSpacing.s)
                  .size(Dimens.MinTapTarget),
          ) {
              Icon(
                  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                  contentDescription = stringResource(R.string.cd_back),
                  modifier = Modifier.size(Dimens.LargeIconSize),
              )
          }
      }
  }
  ```

  Required `@Preview`s (private, in the same file) — the same four-variant
  pattern:
  - `ConfigMenuPlaceholderLightPreview` — `widthDp = 412, heightDp = 800`
  - `ConfigMenuPlaceholderDarkPreview` — `uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 800`
  - `ConfigMenuPlaceholderLargeFontPreview` — `widthDp = 412, heightDp = 800, fontScale = 1.5f`
  - `ConfigMenuPlaceholderHugeFontPreview` — `widthDp = 412, heightDp = 800, fontScale = 2.0f`

  Each preview body: `CurroTheme { Surface(Modifier.fillMaxSize()) { ConfigMenuPlaceholderScreen(onBack = {}) } }`.
  The 2.0× preview verifies the chevron stays at 96 dp (it's defined in
  `Dimens`, which is dp-independent of fontScale) and the title at
  `titleLarge × 2.0 = ~44 sp` doesn't collide with the chevron — the
  centred Box body is `fillMaxSize()` and the chevron is a TopStart
  overlay, so they layer rather than fight.

- **`app/src/main/java/com/curro/app/MainActivity.kt`** — modified. The
  US-001 invariant that has frozen this file for six SFs **lifts at
  US-007** (the only SF — by master-plan SF-0.6's design — where the
  freeze was supposed to lift, because the *only* reason for the freeze
  was "wait until the nav shell exists, then plug it in"). The change is
  minimal: the `setContent { CurroTheme { Surface { Text(stringResource(R.string.app_name)) } } }`
  body switches to `setContent { CurroTheme { CurroNavHost() } }`.
  `@AndroidEntryPoint`, `enableEdgeToEdge()`, and the override `onCreate`
  signature are kept as-is. The `Surface` wrapping disappears at the
  Activity level (the `Scaffold` inside `CurroNavHost` provides the
  surface). The KDoc comments documenting "SF-0.6 upgrades this Activity"
  and "SF-1.1 adds CATEGORY_HOME" are updated to reflect that SF-0.6 is
  now done (the SF-0.6 line is removed; the SF-1.1 forward-reference
  stays).

  Final state:

  ```kotlin
  package com.curro.app

  import android.os.Bundle
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.activity.enableEdgeToEdge
  import com.curro.app.presentation.navigation.CurroNavHost
  import com.curro.app.presentation.theme.CurroTheme
  import dagger.hilt.android.AndroidEntryPoint

  /**
   * Launcher Activity for Curro.
   *
   * - [enableEdgeToEdge] paints under the system bars; [CurroNavHost]'s
   *   Scaffold consumes the insets via its `innerPadding` (No-Double-Padding
   *   rule, `navigation-patterns` rule 1).
   * - [@AndroidEntryPoint] enables Hilt-injected ViewModels in any screen
   *   the nav graph hosts (US-002 wired the graph; the launcher placeholder
   *   has no ViewModel yet — SF-1.1+ adds them).
   *
   * SF-1.1 adds `CATEGORY_HOME` to the manifest intent-filter (making Curro
   * the default launcher) plus a `RoleManager.ROLE_HOME` flow + the
   * "Hazme tu pantalla de inicio" CTA. Until then, Curro appears only in
   * the app drawer (`MAIN + LAUNCHER` filter).
   */
  @AndroidEntryPoint
  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          enableEdgeToEdge()
          setContent {
              CurroTheme {
                  CurroNavHost()
              }
          }
      }
  }
  ```

  The `import androidx.compose.foundation.layout.fillMaxSize`, `Surface`,
  `Text`, `Modifier`, `stringResource` imports from the US-001 state are
  removed (they're no longer used). The `R` import is dropped (no
  `R.string.app_name` reference at the Activity level — the placeholder
  uses its own strings).

- **`app/src/main/AndroidManifest.xml`** — modified. Five new
  `MainActivity` attributes:
  - `android:launchMode="singleTask"`
  - `android:clearTaskOnLaunch="true"`
  - `android:stateNotNeeded="true"`
  - `android:screenOrientation="portrait"`
  - `android:windowSoftInputMode="adjustResize"`

  All five are documented in `launcher-app` and master-plan SF-0.6 as the
  Phase-0 baseline for the launcher Activity contract. The intent-filter
  stays exactly `MAIN + LAUNCHER` — `CATEGORY_HOME` does **not** land
  here; the comment block at the top of the manifest already documents
  "`CATEGORY_HOME` → SF-1.1" and stays unchanged. `android:exported="true"`
  is also unchanged from US-001.

  Final shape of the `<activity>` block:

  ```xml
  <activity
      android:name=".MainActivity"
      android:exported="true"
      android:launchMode="singleTask"
      android:clearTaskOnLaunch="true"
      android:stateNotNeeded="true"
      android:screenOrientation="portrait"
      android:windowSoftInputMode="adjustResize">
      <intent-filter>
          <action android:name="android.intent.action.MAIN" />
          <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
  </activity>
  ```

  No other manifest changes — no permission additions, no
  `<service>`/`<receiver>` additions, no `tools:` namespace additions, no
  `application` attribute additions. The "Permissions and additional
  components are added per-SF" comment block at the top of the manifest
  is unchanged.

- **`app/src/main/res/values/strings.xml`** — append four new entries
  under a new `<!-- Phase-0 nav shell (US-007) -->` sub-block, placed
  after the existing `<!-- Launcher home -->` block (so document order
  reads "real launcher home copy" → "Phase-0 placeholder copy" →
  end-of-file; the placeholders are visibly second-class and easier to
  remove at SF-1.1 / SF-8.1 as a contiguous block).

  ```xml
  <!-- Phase-0 nav shell (US-007) -->
  <!-- LauncherPlaceholderScreen title — Phase-0 only; replaced by SF-1.1's real launcher home -->
  <string name="launcher_placeholder_title">Curro listo</string>
  <!-- LauncherPlaceholderScreen debug affordance — Phase-0 only; replaced by SF-1.6's 5-taps-on-clock gesture -->
  <string name="launcher_placeholder_open_config_debug">Ajustes (depuración)</string>
  <!-- ConfigMenuPlaceholderScreen title — Phase-0 only; replaced by SF-8.1's real config menu -->
  <string name="config_placeholder_title">Menú de Fran — vacío en Phase 0</string>
  <!-- Content description for the back chevron in any child screen — SURVIVES Phase 0 (every chevron uses this). cd_* prefix = content-description naming convention. -->
  <string name="cd_back">Volver</string>
  ```

  **None of the four are added to `brand-design`'s canonical COPY table.**
  Distinction:
  - The COPY table covers **what Curro says out loud** — every line in
    spec §6's flows, the FSM's spoken utterances, the alias-learning
    voice prompts, the recovery messages. US-005 locked this with 54
    entries; US-006 added 2 (`copy_yes` / `copy_no`); the table is the
    spec-§6 voice catalogue.
  - The four US-007 strings are **chrome / affordance / a11y text** —
    they're shown on screen, not spoken; they're labels and content
    descriptions, not Curro's voice. Three of the four are debug-only
    Phase-0 placeholders that vanish; the fourth (`cd_back`) is a
    content description that lives forever but doesn't appear in spec
    §6's voice flows.
  - **Naming**: the COPY-table strings use the `copy_*` prefix
    (US-005's lock); US-007's debug placeholders use plain
    `<surface>_<purpose>_*` (`launcher_placeholder_title`,
    `config_placeholder_title`, `launcher_placeholder_open_config_debug`);
    the content description uses `cd_*` (a new convention this brief
    formalises — "content description string for an icon-only
    affordance"). The naming difference signals the role difference at a
    glance.

  The brief documents this distinction in §Senior-UX & Copy so a future
  COPY review knows to skip the four new strings when sweeping the
  spec-§6 voice catalogue, and to retire three of them at SF-1.1 / SF-1.6
  / SF-8.1 alongside the placeholders.

- **`gradle/libs.versions.toml`** — modified. Two new entries:
  1. Under `[versions]`, after the existing `lifecycle = "2.8.7"` line
     (Compose-stack versions block):
     ```toml
     # --- Navigation Compose (activated in SF-0.6 / US-007) ---
     navigationCompose    = "2.8.5"
     ```
  2. Under `[libraries]`, after the existing
     `androidx-lifecycle-viewmodel-compose = ...` line (the AndroidX block):
     ```toml
     androidx-navigation-compose          = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
     ```

  The version pin: `2.8.5` is the current latest stable as of 2026-05-14
  (the Navigation 2.8.x line tracks Compose-2025.01 BOM; if a 2.8.6 or
  2.9.x lands before US-007 is merged, the developer may bump — record
  the chosen version in the PR description). **NO inline version literal
  in `app/build.gradle.kts`** (US-001 ACs forbid this and ktlint/detekt
  via `libs.versions.toml` enforces it).

  US-001 explicitly reserved several versions for later SFs (Room,
  DataStore, MediaPipe, Coil, Firebase, PostHog) but did **NOT**
  pre-reserve `navigationCompose`. US-007 adds it as a regular active
  entry, not as a "reserved-then-activated" entry, because the catalog's
  reserved pattern (`# Activated in SF-X.Y` comment under `[versions]`,
  `# Activated in SF-X.Y` comment under `[libraries]`) is for entries
  added speculatively in US-001; entries that join the catalog at the
  moment of activation skip the reservation rite.

- **`app/build.gradle.kts`** — modified. One new line under the
  `// --- Compose (versions resolved via BOM — A6) ---` block in the
  `dependencies { }` block, after the existing `implementation(libs.compose.material3)`:

  ```kotlin
  implementation(libs.androidx.navigation.compose)
  ```

  Note: Navigation Compose is **not** part of the Compose BOM; it has its
  own version pin in the catalog (above). The placement under the Compose
  block reflects "this is the Compose-flavoured Navigation library",
  not "BOM-resolved". An inline comment above the line documents the
  non-BOM nature:

  ```kotlin
  // Navigation Compose — NOT BOM-resolved; pinned via `navigationCompose` in libs.versions.toml.
  implementation(libs.androidx.navigation.compose)
  ```

### Out of Scope (each is its own later SF)

- **`CATEGORY_HOME` intent filter on `MainActivity`** — SF-1.1's job.
  Master-plan SF-0.6 explicitly carves this out in writing: "the
  `CATEGORY_HOME` intent filter is not added yet — that ships with Phase
  1, deliberately, to avoid hijacking the home screen on the dev device
  before the real launcher home exists." Landing it here would force the
  dev to set Curro as their default launcher and immediately see a
  placeholder "Curro listo" screen every time they press HOME, which
  breaks the dev's ability to use their phone normally between commits.

- **`RoleManager.ROLE_HOME` flow** — SF-1.1. The
  "Hazme tu pantalla de inicio" CTA (already a `strings.xml` entry from
  US-005 — `copy_home_make_default`) is a `BigPrimaryButton` on the real
  launcher home that fires
  `roleManager.createRequestRoleIntent(ROLE_HOME)`; the placeholder home
  of US-007 doesn't render it (it doesn't yet have the launcher-app
  context to make the prompt meaningful).

- **`requestIgnoreBatteryOptimizations()`** — SF-1.1 or SF-8.x's
  diagnostics. HyperOS's aggressive battery management is a real risk
  (`launcher-app` §"HyperOS / MIUI battery restrictions") but the
  mitigation isn't a manifest change; it's a runtime prompt + a
  diagnostics surface. Phase 0 is too early.

- **The real launcher home — clock, mic button, app grid, "Más apps"** —
  SF-1.2 (clock + date), SF-1.3 (mic button), SF-1.4 (app grid), SF-1.5
  ("Más apps" `LazyColumn` of `BigListRow`). Each is its own SF; each
  replaces a piece of `LauncherPlaceholderScreen`.

- **`MoreAppsScreen`** + the `more_apps` nav route — SF-1.5.
  `navigation-patterns` flags `more_apps` as an *optional* third route
  off the launcher; SF-1.5 makes the call. US-007 deliberately ships
  the minimum (`launcher` + `config`); SF-1.5 adds `more_apps` either
  as a third route or as an overlay on the launcher.

- **The 5-taps-on-clock gesture wiring** — SF-1.6. The canonical entry
  point for `CurroRoute.ConfigMenu`. Until SF-1.6 lands, the only way to
  reach the config route is the debug `TextButton` in
  `LauncherPlaceholderScreen`; SF-1.1 (which deletes the placeholder)
  must land at the same time as SF-1.6 or there's a window where the
  config route is reachable in code but not from the UI. Master-plan
  SF-1.1 depends on SF-0.6; SF-1.6 depends on SF-1.2 + SF-0.6 — the
  dependency ordering ensures SF-1.6's wiring is in place before
  SF-1.1's placeholder removal.

- **The real config menu — aliases / favourite apps / TTS settings /
  confidence sliders / failed-commands log / "send failures to Fran"
  toggle / reset learning / version & diagnostics** — SF-8.x. Spec §9.
  Phase 8 is the dedicated phase; SF-8.1 fills the placeholder with the
  real `ConfigMenuScreen` + `ConfigViewModel`.

- **`AssistantStateMachine` + the state-driven assistant overlays** —
  Phase 5. The `voice-interaction` skill is the authority; the overlays
  (`ListeningOverlay`, `ProcessingOverlay`, `ConfirmationOverlay`,
  `MessageCardsScreen`, `ContactPickerScreen`) render on top of the
  `launcher` route as `StateFlow<AssistantState>` selects between them.
  US-007 deliberately commits to the "overlays are not nav routes" shape
  by having only two routes; that commitment is the structural enabler
  for Phase 5's overlay-rendering pattern.

- **FSM reset on `onNewIntent` / HOME** — Phase 5. `launcher-app` rule 3
  ("Reset the FSM to `idle` on `onNewIntent`/HOME — the user came home,
  start clean") describes what `MainActivity` will eventually do when
  the FSM exists. Phase 0 has no FSM, so there's nothing to reset;
  `clearTaskOnLaunch = "true"` + `stateNotNeeded = "true"` alone handle
  the "HOME returns to a clean launcher" story until the FSM lands.

- **`SYSTEM_ALERT_WINDOW` overlay over other apps** — opt-in, off by
  default, deferred. `launcher-app` §"Overlays over other apps"
  documents the trade-off. The prototype is fine with the assistant UI
  showing only inside the launcher.

- **`BackHandler` on either placeholder** — Q3 of §Open shape questions,
  pinned. Navigation Compose's default system-back behaviour pops
  `config` to `launcher` automatically; the back chevron is the visible
  affordance. Adding `BackHandler { onBack() }` is redundant and would
  fire the haptic + the callback twice when the system back is pressed
  (once via Navigation's internal handler, once via the explicit
  `BackHandler`). SF-8.1's real config menu may add `BackHandler` if
  it needs to intercept back for unsaved-changes flows; the placeholder
  doesn't.

- **A custom detekt rule banning child-screen `Scaffold` /
  `TopAppBar` / `statusBarsPadding()` in `presentation/launcher/` and
  `presentation/config/`** — would mechanically enforce the
  No-Double-Padding rule. Deferred alongside US-003's punt list (the
  tools/detekt-rules/ slot) — for US-007 the rule is enforced by the
  grep AC item.

- **Instrumented UI test of the nav graph** — defer to SF-0.5-followup
  (which is also where US-004 / US-005 / US-006's first UI tests will
  land). At this scaffold stage a manual `./gradlew installDebug` +
  open app + tap debug button + tap chevron sequence on the Pixel_10_Pro
  emulator is sufficient. The brief lists the manual flow explicitly in
  §Testing Requirements.

- **A `MainViewModel`** — US-007 ships no ViewModel. `MainActivity`
  remains a thin shell; the Hilt graph (US-002) is in place but unused
  at the Activity level. Future SFs that need ViewModel-scoped state
  per route (e.g. SF-1.1's `LauncherViewModel`) add them at the
  `composable { }` block of `CurroNavHost`, not at the Activity.

- **Edge-to-edge inset verification beyond what `enableEdgeToEdge()` +
  the Scaffold provides** — `adaptive-layout` covers the system-insets
  handling. The placeholder screens don't need anything beyond the
  default Scaffold behaviour because they don't have content near the
  edges; SF-1.1+ may add `Modifier.windowInsetsPadding(...)` at
  specific call sites as needed, never at the screen-root level.

- **Multi-locale / `values-es/`** — Spanish remains the default locale
  per US-005's lock; no other locale lands. The four new strings are
  Spanish.

## Open shape questions, pinned

This section pins the four shape choices the brief makes for the
developer. Each is decided here; no architect or PM-decision-after-the-fact
step is required.

### 1. Route registry: `enum class CurroRoute(val value: String)` or `sealed interface CurroRoute`? — **`enum class`.**

`navigation-patterns` (line 61) sketches a `object Routes { const val LAUNCHER = "launcher"; ... }`
shape — three plain string constants. `spec-template` (line 167) sketches
a `sealed interface CurroRoute { data object Launcher : CurroRoute; ... }`
shape. Both are valid; the brief picks a third option (`enum`) for
specific reasons:

- **`enum class`** carries both a stable string value (the on-the-wire
  route key Navigation Compose needs) AND an exhaustive-when surface
  (the developer can `when (route) { Launcher -> ...; ConfigMenu -> ... }`
  with a compile-time guarantee that every route is handled), in
  fewer lines of code than either alternative.
- `sealed interface` requires each route to be a separate `data object`
  declaration with a redundant `val value: String` override; verbose at
  this size.
- `object Routes { const val ... }` loses the exhaustive-when (it's
  just three string constants); future SFs that switch on the route
  type lose compile-time safety.
- The Phase 5 assistant FSM uses `sealed interface AssistantState` for
  its own state hierarchy (which is the right shape there — different
  states carry different parameters); the nav-route registry is a flat
  enumeration without per-route parameters, so the simpler `enum`
  shape fits.

**Pinned: `enum class CurroRoute(val value: String) { Launcher("launcher"), ConfigMenu("config") }`.**
Future SFs that need per-route parameters (e.g. a config-section deep-link
`config?section=aliases` — `navigation-patterns` line 67 mentions this
as optional) promote at that time; for now, no route carries parameters.

### 2. How does the placeholder reach the config route in Phase 0? — **A debug `TextButton` inside `LauncherPlaceholderScreen`.**

The canonical config-menu entry is the 5-taps-on-clock gesture (spec §9,
master-plan SF-1.6). SF-1.6 lands much later and depends on SF-1.2's
real clock composable, which depends on SF-0.5 + SF-0.7 — all of which
land in Phase 0 + Phase 1, not in SF-0.6. Without an affordance, the
config route is reachable in code but not from the UI; the AC
"navigates to the stub config menu and back" has no manual verification
path.

Three options were considered:

- **A. Debug `TextButton` on the placeholder.** Subdued styling, label
  reads "Ajustes (depuración)" — explicit about its dev-only nature.
  Removed at SF-1.1 along with the rest of the placeholder.
- **B. A long-press anywhere on the placeholder.** Hidden, but
  undiscoverable for the developer too — they'd have to read this brief
  to know how to test the nav graph.
- **C. No affordance; verify via `adb shell am start` or via a
  scratch test.** Possible but actively user-hostile; the verification
  step at AC time isn't a build-or-test step, it's a click-through.

**Pinned: option A.** Reasons:
- The verification AC is "the app installs, opens the placeholder,
  navigates to the stub config menu and back" — option A is the only
  one that makes this verifiable without writing test infrastructure.
- The "(depuración)" label is unmistakably dev-only; if a user
  somehow saw it (they shouldn't — Curro isn't installed on a real
  device until SF-1.1 at the earliest), it's self-documenting as
  "not for you".
- It's deleted in the same commit as the rest of the placeholder
  (SF-1.1 removes `LauncherPlaceholderScreen.kt`, and the three
  `launcher_placeholder_*` strings with it). Zero carry-over cost.

### 3. `BackHandler` on either placeholder? — **No.**

`navigation-patterns` (line 111) shows `BackHandler { onBack() }` in
the canonical `ConfigMenuScreen` sketch. The sketch is for the *real*
config menu, not for a placeholder.

Three options:

- **A. `BackHandler { onBack() }` on `ConfigMenuPlaceholderScreen`.**
  Explicit; system back triggers the same callback as the chevron.
- **B. No `BackHandler`; rely on Navigation Compose's default
  pop-on-system-back.** The system back action already pops `config`
  → `launcher` via Navigation's internal handler.
- **C. `BackHandler { onBack() }` with `onBack` calling something
  *more* than `navController.popBackStack()`** — e.g. unsaved-changes
  confirmation. The placeholder has no state, so nothing to confirm.

**Pinned: option B.** Reasons:
- Navigation Compose's default pop-on-system-back is *exactly* what
  the placeholder needs. Adding `BackHandler` on top is redundant.
- Worse: adding `BackHandler { onBack() }` where `onBack` is `{ navController.popBackStack() }`
  creates a subtle bug — `BackHandler` intercepts the back press, calls
  `onBack`, which calls `popBackStack`, which… Navigation Compose
  *also* fires its default handler on, depending on the order of
  registration. The chevron is the visible affordance; system back
  follows Navigation's default. Two affordances, one handler.
- SF-8.1's real config menu may add `BackHandler` when it needs to
  intercept back for unsaved-changes flows; the placeholder doesn't.

If `BackHandler` is ever wanted on the placeholder for dev-time
testing (e.g. to log "user pressed back from config"), the developer
adds it temporarily and reverts; the brief does NOT pre-commit to it.

### 4. Haptic feedback on the back chevron? — **No (Phase 0); defer to SF-8.1.**

US-006's `BigPrimaryButton` / `BigYesNoRow` / `BigListRow` all fire
`HapticFeedbackType.LongPress` on press (US-004 A10 contract).
Material's `IconButton` does NOT fire haptic by default — only the
standard ripple visual.

Three options:

- **A. Add `LocalHapticFeedback.current.performHapticFeedback(LongPress)`
  inside the chevron's `onClick` lambda.** Consistent with US-006's
  contract for every clickable surface.
- **B. Don't add haptic.** Material's `IconButton` ripple is the only
  feedback; matches stock Android's "chevron in IconButton" idiom.
- **C. Wait for SF-8.1 to decide.**

**Pinned: option B for Phase 0.** Reasons:
- The chevron in the placeholder is dev-only Phase-0 chrome; the user
  will never see it. The senior-first haptic contract exists to give
  Fran's father tactile certainty on the *real* user surfaces; the
  placeholder isn't one.
- Material's `IconButton` is the standard Compose primitive; deviating
  for a placeholder sets a precedent that every later `IconButton`
  needs custom haptic wiring. SF-8.1's real config menu can decide
  whether the back chevron in `ConfigMenuScreen` warrants haptic
  (likely yes — it's a user-facing affordance — and at that point the
  developer adds it explicitly, perhaps by promoting the chevron into
  a shared `BigBackButton` composable in `presentation/common/`).
- US-006's `launcher-ui` rule 4 set is `BigPrimaryButton` + `BigCard`
  + `BigYesNoRow` + `BigListRow` — *not* `BigIconButton` or
  `BigBackButton`. The chevron-icon-button isn't part of the shared
  set yet; it's a one-off pattern that `navigation-patterns` describes.

If real-device review at SF-8.1 says "the chevron needs haptic", the
developer adds it (one line in `ConfigMenuScreen.kt`) at that SF.
Reversibility: O(1 min).

## User Flows

US-007 has **no end-user flow**. It is developer-facing infrastructure
— the only "users" are a Curro developer rendering Compose previews,
running Gradle locally, and manually testing the nav graph on the
Pixel_10_Pro emulator.

### Flow 1: A developer verifies the nav graph manually on the emulator

(The AC "navigates to the stub config menu and back".)

1. Developer runs `./gradlew installDebug` on the connected Pixel_10_Pro
   emulator.
2. Developer launches Curro from the app drawer (`MAIN + LAUNCHER`
   filter; no `CATEGORY_HOME` yet so it doesn't appear in the
   "Home app" picker).
3. `MainActivity.onCreate` calls `enableEdgeToEdge()` and
   `setContent { CurroTheme { CurroNavHost() } }`.
4. `CurroNavHost` renders its single `Scaffold` and the `NavHost`'s
   start destination — `LauncherPlaceholderScreen`.
5. The developer sees a centred "Curro listo" title in
   `displayMedium` over `MaterialTheme.colorScheme.surface`, with a
   small "Ajustes (depuración)" `TextButton` below.
6. Developer taps the debug `TextButton`. `onOpenConfig()` fires →
   `navController.navigate(CurroRoute.ConfigMenu.value)`.
7. Navigation Compose pushes `ConfigMenuPlaceholderScreen` onto the
   back stack. The developer sees a centred "Menú de Fran — vacío en
   Phase 0" title in `titleLarge` with a chevron-left `IconButton` at
   TopStart.
8. Developer taps the chevron. `onBack()` fires →
   `navController.popBackStack()`.
9. The placeholder returns. Loop complete.
10. Developer presses the system back button as a redundancy check.
    Navigation Compose's default handler pops once more — but the
    back stack is already at the start destination, so nothing
    happens (the system shows the launcher chooser, since
    `CATEGORY_HOME` isn't set, Curro exits to the stock launcher).

### Flow 2: A developer plugs in SF-1.1's real launcher home

(Demonstrates *why* US-007 is the precondition for Phase 1.)

1. SF-1.1 developer opens `LauncherPlaceholderScreen.kt`.
2. They rename the file (or replace it wholesale) with
   `LauncherScreen.kt` + a `LauncherViewModel` per `compose-patterns`.
3. They update `CurroNavHost`'s `composable(CurroRoute.Launcher.value)`
   block to host `LauncherScreen` instead of
   `LauncherPlaceholderScreen`.
4. **The Scaffold doesn't move; the route doesn't change; the
   `MainActivity` doesn't change.** Only the `composable { }` body
   changes.
5. The three `launcher_placeholder_*` strings are deleted (the SF-1.1
   commit includes `strings.xml` deletions). `cd_back` stays.
6. SF-1.1 also adds the `CATEGORY_HOME` intent filter + the
   `RoleManager.ROLE_HOME` flow. From now on, pressing HOME returns
   to Curro.

### Flow 3: A developer plugs in SF-8.1's real config menu

(Same shape, mirror image.)

1. SF-8.1 developer opens `ConfigMenuPlaceholderScreen.kt`.
2. They rename / replace with `ConfigMenuScreen.kt` + a
   `ConfigViewModel`, keeping the `Box` + overlay-chevron pattern
   exactly as US-007 ships it (the placeholder *is* the canonical
   pattern for a back-chevron-equipped child screen — SF-8.1
   inherits, doesn't invent).
3. `CurroNavHost`'s `composable(CurroRoute.ConfigMenu.value)` block
   updates to host `ConfigMenuScreen` instead of
   `ConfigMenuPlaceholderScreen`.
4. The `config_placeholder_title` string is deleted; `cd_back` is
   reused by the new `ConfigMenuScreen`.

## Function-catalog Impact

**No catalog change.** US-007 ships no handler, no `CatalogFunction`,
no FunctionGemma prompt change, no JSON-schema entry. `domain/catalog/`
stays empty.

Cross-reference: the `function-catalog` skill is untouched until
SF-3.x; the first handler binding lands in SF-4.1 (`tell_time`).

## FSM States Touched

**None.** US-007 ships no FSM code. The `AssistantStateMachine` lands
in Phase 5; US-007 is structurally compatible with it by virtue of
deliberately committing to the "state-driven overlays, not nav routes"
shape (only two nav routes — `launcher` + `config` — and no
listening / processing / confirming routes).

Phase 5+ implication, documented here so the Phase 5 developer doesn't
have to rediscover it: the assistant overlays render on top of the
`launcher` route, selected by a `StateFlow<AssistantState>` owned by
`assistant/AssistantStateMachine`. Adding them as nav routes would
force them through Navigation Compose's transitions and back stack —
they're UI-state changes, not navigation. `navigation-patterns` rule 3
is the authority; US-007's nav graph is the structural enabler. See
also `voice-interaction` for the FSM diagram and the interrupt-by-button
rule.

Cross-reference: `voice-interaction` (the FSM; untouched here),
`compose-patterns` (the `StateFlow<UiState>` pattern; untouched here).

## Android System Integrations & Permissions

**No new system integrations**, **no new runtime permissions**, **no
new dangerous-permission manifest declarations**. The `MAIN + LAUNCHER`
intent filter alone needs no permission (the launcher itself needs
none per `launcher-app` line 51); `screenOrientation="portrait"` is a
manifest declaration, not a runtime permission.

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| *(none in this SF)* | Each permission lands with the SF that needs it (spec §10). `RECORD_AUDIO` (SF-2.1), `READ_CONTACTS` / `CALL_PHONE` (SF-4.x), `BIND_NOTIFICATION_LISTENER_SERVICE` (SF-4.6), `POST_NOTIFICATIONS` (SF-3.x), `QUERY_ALL_PACKAGES` (SF-1.4), `INTERNET` (SF-0.8, release-only). | N/A | N/A |

The manifest's existing top-of-file comment block enumerating the
deferred permissions stays unchanged. `CATEGORY_HOME` is the only
"permission-like" manifest item that's punted to a specific SF
(SF-1.1) — the brief documents this carve-out separately because it's
the structural reason US-007 exists as its own SF.

## On-device-model Impact

**No model impact.** US-007 ships no prompt change, no model loading,
no inference path, no `data/ml/` code, no `service/ModelWarmupService`.
FunctionGemma / Gemma 3n are not touched. The MediaPipe / LiteRT
catalog entries (`gradle/libs.versions.toml` reserved block) stay
reserved.

Cross-reference: `on-device-llm` (untouched).

## Android Specification

### Screens and Composables

US-007 ships **three new composable files** and modifies two existing
files. **File layout**:

```
presentation/
├── navigation/
│   └── CurroNavHost.kt              # NEW — this SF (composable + CurroRoute enum)
├── launcher/
│   └── LauncherPlaceholderScreen.kt  # NEW — this SF (Phase-0 placeholder; deleted at SF-1.1)
├── config/
│   └── ConfigMenuPlaceholderScreen.kt # NEW — this SF (Phase-0 placeholder; replaced at SF-8.1)
├── theme/                             # UNCHANGED
├── common/                            # UNCHANGED
└── assistant/                         # still empty (Phase 5)

MainActivity.kt                        # MODIFIED — US-001 invariant lifts; setContent body switches to CurroNavHost()
res/values/strings.xml                  # APPEND — 4 new strings under "Phase-0 nav shell (US-007)" sub-block
AndroidManifest.xml                    # MODIFIED — 5 new MainActivity attributes; NO CATEGORY_HOME
gradle/libs.versions.toml               # MODIFIED — activate Navigation Compose (1 version + 1 library entry)
app/build.gradle.kts                    # MODIFIED — implementation(libs.androidx.navigation.compose) under the Compose block
```

**No new ViewModel, no new repository, no new Hilt module, no new
domain code.**

### ViewModels and State Management

No ViewModel changes. All three new composables are **stateless** —
they receive callbacks (`onOpenConfig`, `onBack`) and emit no events
beyond firing those callbacks on tap. The `enabled` state, the
selected route, and the back-stack state all live inside
`CurroNavHost` (via `rememberNavController()`); none of it is hoisted
to a ViewModel.

When Phase 1 SF-1.1 lands `LauncherScreen` with a `LauncherViewModel`,
the ViewModel emits `LauncherEffect.OpenConfig` events (per
`navigation-patterns` line 142) and `LauncherScreen` collects them
and calls `onOpenConfig()` — the wiring at the `composable { }` level
in `CurroNavHost` doesn't change. US-007's two-route structure is
ViewModel-ready by virtue of the screen → callback → NavController
hand-off pattern.

### Navigation Routes

The complete route registry:

```kotlin
enum class CurroRoute(val value: String) {
    Launcher("launcher"),    // start destination
    ConfigMenu("config"),    // hidden Fran-only
}
```

**No new routes beyond these two.** No `more_apps` route (SF-1.5
decides), no `listening` / `processing` / `confirming` /
`message_cards` / `contact_picker` routes (those are state-driven
overlays per `navigation-patterns` rule 3).

The route string values (`"launcher"`, `"config"`) are stable; future
SFs that consume them MUST go through the enum (`CurroRoute.Launcher.value`,
not a hardcoded `"launcher"` string). This is enforced socially in
US-007; a future detekt rule could mechanically enforce it (deferred
alongside the No-Double-Padding rule punt).

### Hilt Modules

**No new Hilt module.** `MainActivity` keeps `@AndroidEntryPoint` for
when SF-1.1+ injects ViewModels at the `composable { }` level via
`hiltViewModel()`; nothing is injected at the Activity level today.

### Composables by Feature (checklist)

- [ ] `CurroNavHost.kt` (`presentation/navigation/`) → top-level
      `@Composable fun CurroNavHost(modifier: Modifier = Modifier)` +
      `enum class CurroRoute(val value: String)`; no preview (the host
      can't render meaningfully in isolation — it composes the two child
      screens which have their own previews).
- [ ] `LauncherPlaceholderScreen.kt` (`presentation/launcher/`) →
      `@Composable fun LauncherPlaceholderScreen(onOpenConfig: () -> Unit, modifier: Modifier = Modifier)`
      + 4 `@Preview` variants.
- [ ] `ConfigMenuPlaceholderScreen.kt` (`presentation/config/`) →
      `@Composable fun ConfigMenuPlaceholderScreen(onBack: () -> Unit, modifier: Modifier = Modifier)`
      + 4 `@Preview` variants.
- [ ] `MainActivity.kt` modified — `setContent { CurroTheme { CurroNavHost() } }`.
- [ ] `AndroidManifest.xml` modified — 5 new `<activity>` attributes.
- [ ] `strings.xml` modified — 4 new `<string>` entries.
- [ ] `gradle/libs.versions.toml` modified — 1 new `[versions]` entry,
      1 new `[libraries]` entry.
- [ ] `app/build.gradle.kts` modified — 1 new `implementation(libs.androidx.navigation.compose)`
      line under the Compose dependencies block.

### Material Design Components

- `Scaffold` (Material 3) — exactly **one** instance, in `CurroNavHost`.
  No child screen adds another.
- `NavHost` + `composable { }` (Navigation Compose) — the two route
  bindings.
- `Box`, `Column`, `Spacer` — for placeholder layouts.
- `Text` (Material 3) — placeholder titles.
- `TextButton` (Material 3) — the Phase-0 debug affordance on the
  launcher placeholder.
- `IconButton` + `Icon` (Material 3) — the back chevron on the config
  placeholder.
- `Icons.AutoMirrored.Filled.KeyboardArrowLeft` (Material Icons
  Extended) — the chevron glyph. The `AutoMirrored` namespace is
  important: in an RTL locale the chevron would mirror automatically.
  Curro is Spanish-only and Spanish is LTR, but the `AutoMirrored`
  variant is the canonical M3 idiom for "back" chevrons; using it
  matches `navigation-patterns` line 124 verbatim.

**Not used (deliberately)** in this SF:
- `TopAppBar` — No-Double-Padding rule.
- `Surface` at the screen-root level — `Scaffold` already paints
  background.
- `BottomNavigation` / `NavigationBar` / `NavigationRail` /
  `NavigationSuiteScaffold` — `navigation-patterns` rule 4.
- `BigPrimaryButton` / `BigCard` / `BigYesNoRow` / `BigListRow` — no
  real consumer yet; the debug `TextButton` is a Material primitive,
  not a Curro big-brick. Using `BigPrimaryButton` for the debug
  affordance would visually elevate it to "primary CTA" status, which
  it isn't.
- `BackHandler` — Q3 of §Open shape questions, pinned.

## Acceptance Criteria

Concrete, checkable; expands the PRD AC list with the developer-facing
specifics:

### Build & lint
- [ ] **`./gradlew assembleDebug` succeeds** on a fresh clone (JDK 17),
  produces an installable APK; the installed APK launches on the
  connected Pixel_10_Pro emulator without crashing.
- [ ] **`./gradlew ktlintCheck detekt testDebugUnitTest` all pass** —
  US-001's `SmokeTest` plus any later regression guards still green;
  no new detekt deprecation warnings; the `MagicNumber` exclude on
  `**/presentation/theme/**` is unchanged (no widening). US-007 ships
  no raw `.dp` / `.sp` / `Color(0xFF…)` literals outside
  `presentation/theme/`.
- [ ] **Navigation Compose imports resolve** — `gradle/libs.versions.toml`
  carries the `navigationCompose` version pin and the
  `androidx-navigation-compose` library entry; `app/build.gradle.kts`
  references `libs.androidx.navigation.compose`; `./gradlew dependencies | grep navigation-compose`
  shows exactly one entry resolved (no Maven version conflict).

### Manifest contract
- [ ] **`MainActivity` carries exactly these five new attributes** —
  `android:launchMode="singleTask"`, `android:clearTaskOnLaunch="true"`,
  `android:stateNotNeeded="true"`, `android:screenOrientation="portrait"`,
  `android:windowSoftInputMode="adjustResize"`. Verifiable:
  `grep -E 'launchMode|clearTaskOnLaunch|stateNotNeeded|screenOrientation|windowSoftInputMode' app/src/main/AndroidManifest.xml`
  returns 5 matches.
- [ ] **`MainActivity` intent-filter is exactly `MAIN + LAUNCHER`** — no
  `CATEGORY_HOME`, no `CATEGORY_DEFAULT`. Verifiable:
  `grep -E 'category.HOME|CATEGORY_HOME' app/src/main/AndroidManifest.xml`
  returns **0** matches; `grep -E 'category.LAUNCHER|category.DEFAULT|action.MAIN' app/src/main/AndroidManifest.xml`
  returns exactly 1 of each first two and 1 of `MAIN`.
- [ ] **`android:exported="true"` stays** (US-001 invariant on this
  attribute; required because the intent filter has an action — Android
  12+ rule).
- [ ] **The manifest's top-of-file comment block** that enumerates
  deferred permissions/components is **unchanged** — including the
  `CATEGORY_HOME → SF-1.1` line. The "Permissions and additional
  components are added per-SF" comment stays.

### `MainActivity` contract
- [ ] **`MainActivity.kt` body** is `@AndroidEntryPoint` +
  `class MainActivity : ComponentActivity()` + `override fun onCreate(savedInstanceState: Bundle?)` +
  `super.onCreate(savedInstanceState)` + `enableEdgeToEdge()` +
  `setContent { CurroTheme { CurroNavHost() } }`. **No `Surface` wrapper
  at the Activity level**, no `Modifier.fillMaxSize()` chained anywhere
  in the Activity (the `Scaffold` inside `CurroNavHost` handles it), no
  `R.string.app_name` reference.
- [ ] **The US-001 invariant on `MainActivity` lifts** — this is the
  first SF since US-001 allowed to modify `MainActivity.kt`. The brief
  documents this explicitly so the developer doesn't second-guess the
  change. Predecessor invariants on other files (theme tokens, common
  components) **stay** — see "regression guards" below.
- [ ] **KDoc on `MainActivity`** is updated — the SF-0.4 / SF-0.6 lines
  are removed (those SFs are done); the SF-1.1 forward-reference line
  ("SF-1.1 adds CATEGORY_HOME…") stays. Verifiable: the KDoc no longer
  mentions SF-0.4 or SF-0.6.

### `CurroNavHost` contract
- [ ] **`CurroNavHost.kt`** exists at
  `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
  with:
  - A single top-level `@Composable fun CurroNavHost(modifier: Modifier = Modifier)`.
  - A single top-level `enum class CurroRoute(val value: String)` with
    exactly two entries: `Launcher("launcher")` and `ConfigMenu("config")`.
  - The composable body: `val navController = rememberNavController()` +
    a single `Scaffold(modifier = modifier.fillMaxSize())` + a
    `NavHost(navController, startDestination = CurroRoute.Launcher.value, modifier = Modifier.padding(innerPadding))`
    + exactly two `composable { }` blocks (one per route).
  - **No `BackHandler`** anywhere in the file.
  - **No raw `.dp` literals** anywhere in the file (no `Modifier.padding(8.dp)`
    or similar; the padding comes from `innerPadding`).
  - Verifiable:
    `grep -c 'Scaffold(' app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
    returns 1; `grep -c 'composable(' app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
    returns 2; `grep -c 'BackHandler' app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
    returns 0.

### `LauncherPlaceholderScreen` contract
- [ ] **`LauncherPlaceholderScreen.kt`** exists at
  `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`
  with:
  - `@Composable fun LauncherPlaceholderScreen(onOpenConfig: () -> Unit, modifier: Modifier = Modifier)`.
  - `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column { Text(title) ; Spacer(CurroSpacing.xxl) ; TextButton(onClick = onOpenConfig) { Text(label) } } }`.
  - Title `Text` uses `MaterialTheme.typography.displayMedium` and
    `MaterialTheme.colorScheme.onSurface`.
  - Label `Text` (inside the `TextButton`) uses
    `MaterialTheme.typography.labelLarge`.
  - 4 `@Preview` variants — light / dark / `fontScale = 1.5f` / `fontScale = 2.0f` —
    all at `widthDp = 412, heightDp = 800`.
  - **No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()`** —
    verifiable:
    `grep -E 'Scaffold|TopAppBar|statusBarsPadding' app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`
    returns **0** matches.
  - **No raw `.dp` / `.sp` / `Color(0xFF…)` literals** — verifiable:
    `grep -E '[0-9]+\.dp|[0-9]+\.sp|Color\\(0xFF' app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`
    returns 0 matches.

### `ConfigMenuPlaceholderScreen` contract
- [ ] **`ConfigMenuPlaceholderScreen.kt`** exists at
  `app/src/main/java/com/curro/app/presentation/config/ConfigMenuPlaceholderScreen.kt`
  with:
  - `@Composable fun ConfigMenuPlaceholderScreen(onBack: () -> Unit, modifier: Modifier = Modifier)`.
  - `Box(Modifier.fillMaxSize()) { centred title Box ; overlay IconButton at TopStart }`.
  - The IconButton's modifier chain is exactly
    `Modifier.align(Alignment.TopStart).padding(start = CurroSpacing.s, top = CurroSpacing.s).size(Dimens.MinTapTarget)`.
  - The Icon inside is
    `Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_back), modifier = Modifier.size(Dimens.LargeIconSize))`.
  - Title `Text` uses `MaterialTheme.typography.titleLarge` and
    `MaterialTheme.colorScheme.onSurface`.
  - 4 `@Preview` variants — light / dark / 1.5× / 2.0× — at
    `widthDp = 412, heightDp = 800`.
  - **No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()`** —
    verifiable: same grep returns 0 matches.
  - **No `BackHandler`** — verifiable: `grep BackHandler` returns 0.
  - **No raw `.dp` / `.sp` / `Color(0xFF…)` literals** — verifiable as
    above.
  - Verifiable for the chevron sizing:
    `grep 'Dimens.MinTapTarget' app/src/main/java/com/curro/app/presentation/config/ConfigMenuPlaceholderScreen.kt`
    returns ≥ 1; `grep 'Dimens.LargeIconSize' app/src/main/java/com/curro/app/presentation/config/ConfigMenuPlaceholderScreen.kt`
    returns ≥ 1.

### Strings & COPY table discipline
- [ ] **Four new `<string>` entries** in `strings.xml` under a new
  `<!-- Phase-0 nav shell (US-007) -->` sub-block placed after
  `<!-- Launcher home -->`. The four entries:
  - `launcher_placeholder_title` = `Curro listo`
  - `launcher_placeholder_open_config_debug` = `Ajustes (depuración)`
  - `config_placeholder_title` = `Menú de Fran — vacío en Phase 0`
  - `cd_back` = `Volver`

  Each carries an inline `<!-- comment -->` documenting (a) which SF
  replaces / retires it (SF-1.1 / SF-1.6 / SF-8.1 / "survives"
  respectively), and (b) that the `cd_*` prefix is the
  content-description naming convention. Verifiable:
  `grep -cE 'launcher_placeholder_title|launcher_placeholder_open_config_debug|config_placeholder_title|cd_back' app/src/main/res/values/strings.xml`
  returns 4.
- [ ] **None of the four strings are added to `brand-design`'s
  canonical COPY table** — `.claude/skills/brand-design/SKILL.md` is
  **byte-identical** to its US-006 state. Verifiable: `git diff .claude/skills/brand-design/SKILL.md`
  returns no output. The brief documents the rationale (chrome /
  affordance / a11y text, not spec-§6 voice).
- [ ] **The brief's COPY table** (in §Senior-UX & Copy) documents the
  four strings with provenance markers (3 are "(PHASE-0 ONLY)", 1 is
  "(NEW PERMANENT)") so a future review distinguishes them from US-005
  / US-006 COPY-table strings at a glance.

### Version catalog & build wiring
- [ ] **`gradle/libs.versions.toml`** has one new `[versions]` entry —
  `navigationCompose = "2.8.5"` (or the chosen latest stable — the
  developer records the version in the PR description) — placed under
  the existing `lifecycle` line; one new `[libraries]` entry —
  `androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }` —
  placed under the existing
  `androidx-lifecycle-viewmodel-compose` line. No "Activated in SF-X.Y"
  comment (the entry joins the catalog at the moment of activation,
  not as a US-001 reservation).
- [ ] **`app/build.gradle.kts`** has one new line —
  `implementation(libs.androidx.navigation.compose)` — under the Compose
  dependencies block, after `implementation(libs.compose.material3)`.
  An inline comment above documents "Navigation Compose — NOT
  BOM-resolved; pinned via `navigationCompose` in libs.versions.toml."
  **No inline `androidx.navigation:navigation-compose:X.Y.Z` literal.**

### Regression guards (predecessor invariants stay)
- [ ] **`git diff` against each of the following returns no output**
  (the corresponding US-004 / US-005 / US-006 invariants stay):
  - `app/src/main/java/com/curro/app/presentation/theme/Color.kt`
  - `app/src/main/java/com/curro/app/presentation/theme/Type.kt`
  - `app/src/main/java/com/curro/app/presentation/theme/Shape.kt`
  - `app/src/main/java/com/curro/app/presentation/theme/Dimens.kt`
  - `app/src/main/java/com/curro/app/presentation/theme/CurroSpacing.kt`
  - `app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt`
  - `app/src/main/java/com/curro/app/presentation/common/BigPrimaryButton.kt`
  - `app/src/main/java/com/curro/app/presentation/common/BigCard.kt`
  - `app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt`
  - `app/src/main/java/com/curro/app/presentation/common/BigListRow.kt`
  - `app/src/main/res/values/themes.xml`
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/values-night/colors.xml`
- [ ] **No new consumer of `BigPrimaryButton` / `BigCard` /
  `BigYesNoRow` / `BigListRow`** — verifiable:
  `grep -rn 'BigPrimaryButton\|BigCard\|BigYesNoRow\|BigListRow' app/src/main/java | grep -v 'presentation/common/'`
  returns **0 matches**. The debug affordance uses Material's plain
  `TextButton`, not `BigPrimaryButton`.

### Acceptance bar (manual)
- [ ] **End-to-end nav flow on the Pixel_10_Pro emulator**: after
  `./gradlew installDebug`, launching Curro shows the "Curro listo"
  placeholder; tapping "Ajustes (depuración)" navigates to the
  "Menú de Fran — vacío en Phase 0" stub; tapping the back chevron
  returns to the placeholder. Developer records the manual flow in the
  PR description (one screenshot per state is sufficient).
- [ ] **Edge-to-edge insets are correct** — the title in
  `LauncherPlaceholderScreen` doesn't render under the status bar or
  the navigation bar (the `Scaffold`'s `innerPadding` consumes both);
  the back chevron in `ConfigMenuPlaceholderScreen` is below the status
  bar (the `Scaffold` pads, then the chevron's TopStart alignment is
  measured against the padded area). Developer eyeballs this on the
  emulator and confirms in the PR description.
- [ ] **Dark mode flips correctly** — `adb shell cmd uimode night yes`
  and `adb shell cmd uimode night no` toggle both placeholder screens
  between light and dark; titles and the chevron remain readable in
  both modes.
- [ ] **`fontScale = 2.0f` preview survives** — both
  `LauncherPlaceholderScreen` and `ConfigMenuPlaceholderScreen` render
  their `fontScale = 2.0f` preview without clipping or layout collapse;
  the chevron's tap target stays at `Dimens.MinTapTarget` = 96 dp
  (independent of `fontScale`).

### Verification checklist sweep
- [ ] **`verification-checklist`'s relevant sections pass**: Build
  Verification ✓; Lint and Code Quality ✓; Unit Tests ✓ (regression);
  UI Tests — not applicable (no instrumented test added — defer to
  SF-0.5-followup); Code Quality Checks ✓; Privacy & Permissions —
  N/A; Accessibility Review — the chevron has `contentDescription` via
  `cd_back`, the title is plain `Text` (the visible label IS the
  accessible label), the `IconButton` is ≥ 96 dp; Dark Mode Testing ✓
  (the 4 dark previews per file plus the live-device adb sweep).

## Design Notes

Follow `brand-design` for tokens (now AUTHORITATIVE per US-005's lock).
US-007 ships the structural shell that future surfaces consume; it
reads tokens via `MaterialTheme.colorScheme.*` /
`MaterialTheme.typography.*` / `CurroSpacing.*` / `Dimens.*` only — no
raw literals outside `presentation/theme/`.

**Typography choices**:
- `LauncherPlaceholderScreen` title uses **`displayMedium`** — US-005's
  scale puts this at 45 sp SemiBold (or whatever US-005 locked it at;
  verify before commit). It's the "stand-in-for-the-real-clock" size:
  big enough that the placeholder reads as a real placeholder, not a
  debug splash. The clock in SF-1.2 uses `displayLarge` (US-005 set
  `displayLarge ≥ 64 sp`), so the placeholder is intentionally one
  size below the real clock — visually distinct.
- `LauncherPlaceholderScreen` debug `TextButton` label uses
  **`labelLarge`** — US-005's 18 sp SemiBold floor. Subdued vs the
  title; "this is a button, not a heading".
- `ConfigMenuPlaceholderScreen` title uses **`titleLarge`** — US-005's
  22 sp SemiBold. Smaller than the launcher placeholder's title
  because the config menu's real title (in SF-8.1) is a section heading,
  not a hero — `titleLarge` previews the right scale.

**Colour usage**:
- All `Text` colour is `MaterialTheme.colorScheme.onSurface`. The
  `Scaffold` paints `background` (which is `surface`-aligned in
  Material 3 default ColorScheme); `onSurface` on `background` clears
  US-005's ≥ 7:1 contrast contract in both light and dark.
- The chevron's `Icon` inherits its tint from `IconButton`'s default
  (which is `LocalContentColor.current`, derived from
  `onSurface` inside a `Scaffold` body). No explicit `tint` parameter
  — Material's default is correct.
- **No `MaterialTheme.colorScheme.error` anywhere** — error is reserved
  for genuine failure states per US-006's contract; the placeholder is
  not a failure state.

**Other principles, unchanged from US-006's pattern**:
- **Read tokens, not literals** — `MaterialTheme.colorScheme.*` /
  `MaterialTheme.typography.*` / `CurroSpacing.*` / `Dimens.*`.
- **No fussy animation** — Navigation Compose's default route
  transition (a slide; configurable but the default is fine) is the
  only motion. No `Crossfade`, no `AnimatedVisibility`, no custom
  `enterTransition` / `exitTransition`.
- **Predictable shape** — every screen reads the same way every time;
  no per-launch randomisation. The placeholders are deterministic.
- **Audio + visual together** — N/A for US-007 (no spoken interaction
  is wired in either placeholder; SF-2.x adds TTS); the contract is
  preserved.
- **Colour is never the only signal** — chevron has a glyph + the
  `cd_back` content description (Spanish "Volver"); the title is a
  text label; the debug button is text-only (no colour-only signal).

## Senior-UX & Copy

US-007 adds **four new Spanish strings** to `strings.xml`. Three are
Phase-0-only debug-affordance / placeholder text that vanishes at
SF-1.1 / SF-1.6 / SF-8.1; one is a content description for the back
chevron that survives Phase 0 forever. **None of the four go into
`brand-design`'s canonical COPY table** — distinction below.

### COPY table — NEW PERMANENT (1 entry)

| ID | Spanish | Provenance | Notes |
|---|---|---|---|
| `cd_back` | Volver | (NEW PERMANENT) US-007 | Content description for the back chevron `Icon` in every child screen with a back affordance. The `cd_*` prefix is the new content-description naming convention this brief formalises — **not** part of `brand-design`'s `copy_*` COPY table (which is for spoken voice). SF-1.5's `MoreAppsScreen`, SF-8.x's `ConfigMenuScreen`, and any future child-screen back chevron reuses this string. |

### COPY table — PHASE-0 ONLY (3 entries)

| ID | Spanish | Provenance | Retirement |
|---|---|---|---|
| `launcher_placeholder_title` | Curro listo | (PHASE-0 ONLY) US-007 | SF-1.1 deletes this string when `LauncherPlaceholderScreen.kt` is replaced by the real launcher home. |
| `launcher_placeholder_open_config_debug` | Ajustes (depuración) | (PHASE-0 ONLY) US-007 | SF-1.6 (the 5-taps-on-clock gesture) makes this affordance redundant; SF-1.1 deletes the `LauncherPlaceholderScreen.kt` and SF-1.6 ensures the canonical gesture is in place at the same time. |
| `config_placeholder_title` | Menú de Fran — vacío en Phase 0 | (PHASE-0 ONLY) US-007 | SF-8.1 deletes this string when `ConfigMenuPlaceholderScreen.kt` is replaced by the real `ConfigMenuScreen`. |

### Why these four strings are NOT in `brand-design`'s COPY table

US-005 locked the canonical COPY table to **what Curro says out loud**
— spec §6's voice catalogue. Every entry in the table is a TTS-spoken
line (or a confirmation-button label that's both seen and shouted via
its visual weight: `copy_yes` / `copy_no`). US-006's two additions
fit because the SÍ/NO buttons in the confirmation overlay are
*peer-spoken*: the FSM speaks the prompt while the buttons show the
labels, and a user who responds by voice ("sí" / "no") interacts with
the same words.

US-007's four strings are different:
- `launcher_placeholder_title` / `launcher_placeholder_open_config_debug` /
  `config_placeholder_title` are **debug-only Phase-0 placeholders**.
  They never get spoken; they're chrome on a screen that doesn't yet
  exist in Curro's voice (the real launcher home in SF-1.2+ has a
  clock — *that's* spoken in `tell_time` flows via the existing
  `copy_time_now` / `copy_time_date` strings — and the real config
  menu in SF-8.1 has section labels but no TTS-spoken interaction).
  Adding them to the canonical COPY table would mislead future SFs
  into thinking Curro speaks "Curro listo" out loud, which is wrong.
- `cd_back` is **a content description**, not spoken voice. TalkBack
  reads it out loud, but TalkBack is the OS accessibility service —
  it's not Curro speaking. Adding it to the COPY table would conflate
  Curro's voice with the OS's accessibility layer. The `cd_*` prefix
  signals "content description" structurally.

The brief formalises this distinction so future SFs adding
chrome / affordance / a11y strings know to keep them out of the
COPY table; the COPY table stays a clean spec-§6 voice catalogue.

### Curro's voice (unchanged from US-005's lock)

US-007 doesn't ship spoken interaction — the placeholders don't
exercise TTS. The canonical voice rules (warm, Andalusian, colloquial,
efficient and close, not servile; spec §2) stay locked by US-005 and
US-006; US-007 doesn't touch them. The three Phase-0-only strings
above are intentionally **flat / dev-tone** — they read as "we're not
done yet, don't worry about it", not as Curro's voice. The
"depuración" in the debug-button label is the explicit dev-tone
marker; "Menú de Fran — vacío en Phase 0" is a placeholder, not a
greeting.

## Performance Considerations

- **Pure Compose state**; no Hilt graph beyond `MainActivity`'s
  `@AndroidEntryPoint` (which `MainActivity` already had from US-002),
  no `StateFlow`, no I/O. All three new composables are pure
  parameter-in, event-out shapes.
- **`@Preview` cost** — 4 previews × 2 placeholder files = 8 previews
  land. Each preview is recompose-only in Android Studio; zero runtime
  cost. The build cost is negligible.
- **No animation** — Navigation Compose ships a default route-transition
  animation (a horizontal slide); US-007 doesn't override it. The
  slide is brief (~250 ms by default) and respects the system "Reduce
  motion" accessibility setting. No `Crossfade`, no
  `AnimatedVisibility`, no custom transitions.
- **No `LaunchedEffect`** — the placeholders have no side effects.
  `MainActivity.onCreate` calls `enableEdgeToEdge()` once; `setContent`
  is called once; the rest is pure UI.
- **Recomposition stability** — `CurroNavHost` takes only a `Modifier`
  parameter (Compose stable). The placeholders take a `() -> Unit`
  callback and a `Modifier` (both stable). No recomposition leakage.
- **Memory** — three new top-level composables, one new enum, four
  new string resources, one new Gradle dependency
  (`androidx.navigation:navigation-compose:2.8.5` is ~250 KB
  unminified). Total static state additions well under 500 KB.
- **APK size impact** — Navigation Compose adds ~250 KB (the runtime +
  the navigation-runtime transitive). Negligible at the Phase 0 APK
  size (the model weights are the real budget — SF-3.x).
- **Cold start** — `MainActivity.onCreate` does no heavy work; the
  `CurroNavHost` `Scaffold` + `NavHost` initial composition is < 50 ms
  on the Pixel_10_Pro emulator (eyeball check; not a hard gate at this
  stage). The window background is painted by `Theme.Curro` ahead of
  Compose's first frame so there's no flash (US-005 wired this).

## Testing Requirements

US-007 ships **no new instrumented tests, no new unit tests beyond
the regression guard**. Aligning with US-004 / US-005 / US-006's
pattern (none shipped tests; SF-0.5-followup / SF-5.x / SF-1.x will
land them against real consumers):

- [ ] **`./gradlew testDebugUnitTest`** still passes — US-001's
  `SmokeTest` is unaffected (regression guard).
- [ ] **`./gradlew assembleDebug`** still passes.
- [ ] **`./gradlew ktlintCheck detekt`** passes on a fresh clone.
- [ ] **`@Preview` rendering check** — the developer opens
  `LauncherPlaceholderScreen.kt` and `ConfigMenuPlaceholderScreen.kt`
  in Android Studio with the preview pane visible, confirms each of
  the 4 + 4 = 8 previews renders without IDE error and without visible
  clipping / contrast failure / collapse. The 2.0× previews are the
  senior-first regression. Screenshots in the PR description (or, at
  minimum, the developer states "all 8 previews rendered
  successfully").
- [ ] **Device dark-mode flip** — `adb shell cmd uimode night yes`
  and `adb shell cmd uimode night no` on the Pixel_10_Pro emulator;
  both placeholder screens remain readable in both modes.
- [ ] **Manual nav flow on the emulator** — see the §Acceptance Bar
  (manual) AC. The developer runs through the placeholder → debug
  button → config stub → back chevron → placeholder loop, plus the
  system-back redundancy check.
- [ ] **Manifest sanity check** — `./gradlew :app:processDebugManifest`
  succeeds; the merged manifest at `app/build/intermediates/merged_manifests/debug/AndroidManifest.xml`
  carries all 5 new `MainActivity` attributes and no `CATEGORY_HOME`.
  Verifiable: `grep -E 'launchMode|clearTaskOnLaunch|stateNotNeeded|screenOrientation' app/build/intermediates/merged_manifests/debug/AndroidManifest.xml`
  returns ≥ 4 matches; `grep CATEGORY_HOME app/build/intermediates/merged_manifests/debug/AndroidManifest.xml`
  returns 0.
- [ ] **Lint check on the manifest** — `./gradlew lintDebug` (if not
  prohibitively slow on Phase 0 — it can be skipped if the build time
  is > 60 s on the developer's machine and run only in CI). No new
  manifest-related lint warnings introduced by the 5 new attributes;
  Android Studio's manifest editor doesn't flag any of them as
  deprecated on `compileSdk = 35`.
- [ ] **`verification-checklist` skill** — the relevant sections
  (Build, Lint, Unit Tests, Accessibility, Dark Mode) pass; Privacy
  & Permissions, On-device Model, Assistant FSM, Function Catalog
  sections are N/A.

**Future test coverage (not in US-007)**:
- SF-0.5-followup (a small UI-test SF) can land the first instrumented
  tests on the nav graph:
  - `CurroNavHost` — start destination is `Launcher`; tapping the
    debug button navigates to `ConfigMenu`; tapping the chevron pops
    back; system-back also pops; the route enum's values match the
    on-the-wire strings.
  - `LauncherPlaceholderScreen` — title is rendered; debug button
    fires `onOpenConfig`; `fontScale = 2.0f` regression survives.
  - `ConfigMenuPlaceholderScreen` — title is rendered; chevron fires
    `onBack`; chevron's tap target is ≥ 96 dp (Espresso /
    `composeTestRule.onNodeWithContentDescription("Volver").assertHeightIsAtLeast(96.dp)`);
    `fontScale = 2.0f` regression survives.
  - Screenshot tests at the four `fontScale` settings (per US-004
    A9 + `testing-patterns`).
- SF-1.1 lands the first real-consumer integration test exercising
  `CATEGORY_HOME` + `RoleManager.ROLE_HOME` on a real device. Until
  then, the manifest + nav-graph contract is socially enforced.

## Implementation Notes

### Order of operations (developer-facing checklist)

Branch policy: the PM-instruction (from the user, who is asleep —
autonomy) is **work on `main`, do not create a branch**. Commit only;
do not push, do not open a PR.

1. **`gradle/libs.versions.toml`** — add the `navigationCompose`
   version entry under `[versions]` and the
   `androidx-navigation-compose` library entry under `[libraries]`,
   exactly as specified in §Scope. Verify with
   `./gradlew dependencies | grep navigation-compose` that the
   library resolves to the pinned version (no Maven conflict).

2. **`app/build.gradle.kts`** — add the
   `implementation(libs.androidx.navigation.compose)` line under the
   Compose dependencies block, with the inline comment about
   non-BOM resolution. Verify with `./gradlew assembleDebug` that the
   module compiles before any new Kotlin code lands.

3. **`app/src/main/res/values/strings.xml`** — append the new
   `<!-- Phase-0 nav shell (US-007) -->` sub-block with the four new
   `<string>` entries and their provenance comments. Verify with
   `./gradlew lintDebug` that no missing-locale warnings fire (Spanish
   is the default locale; there is no second locale to translate to).

4. **`app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`** —
   create the file per the §Scope shape: top-level composable +
   top-level `enum class CurroRoute`. Verify the file compiles
   in isolation before wiring it in `MainActivity`.

5. **`app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`** —
   create the file per the §Scope shape, including the 4 previews.
   Render the previews in Android Studio's preview pane; eyeball
   each for clipping / collapse.

6. **`app/src/main/java/com/curro/app/presentation/config/ConfigMenuPlaceholderScreen.kt`** —
   create the file per the §Scope shape, including the 4 previews.
   Same eyeball pass.

7. **`app/src/main/AndroidManifest.xml`** — add the 5 new
   `MainActivity` attributes. Verify the manifest stays well-formed
   XML; verify the merged manifest carries the attributes (see
   §Testing Requirements).

8. **`app/src/main/java/com/curro/app/MainActivity.kt`** — modify the
   `setContent { }` body to `CurroTheme { CurroNavHost() }`. Remove
   the unused `Surface`, `Text`, `Modifier`, `stringResource`, `R`
   imports. Update the KDoc to remove the SF-0.4 / SF-0.6 lines (the
   SFs are done); keep the SF-1.1 forward-reference. Verify with
   `./gradlew assembleDebug` then `./gradlew installDebug` on the
   emulator + the manual nav flow (§Acceptance Bar (manual) AC).

9. **Verify the regression guard** — run the `git diff` against each
   file in the regression-guard AC list; confirm no output.

10. **Commit on `main`** with the message documented in the parent
    request: `docs(prd): add US-007 — CurroNavHost shell + MainActivity launcher Activity` — though the actual code lands at `/implement-feature US-007` time, not in this brief. **For now: commit only `docs/PRD.md` and `docs/briefs/US-007-curro-navhost-shell.md`** per the parent request's "Suggested" commit subject. The code (per the developer order-of-operations 1–8 above) lands when `/implement-feature US-007` runs; this brief is the spec, not the implementation.

### How this brief sequences against subsequent SFs

- **SF-1.1** depends on SF-0.6 (US-007). SF-1.1 lands `CATEGORY_HOME`,
  the `RoleManager` flow, and starts replacing
  `LauncherPlaceholderScreen` with the real `LauncherScreen`.
- **SF-1.6** depends on SF-1.2 + SF-0.6 (US-007). SF-1.6 wires the
  5-taps-on-clock gesture; the moment SF-1.6 ships, the debug
  `TextButton` in `LauncherPlaceholderScreen` becomes redundant. The
  dependency graph ensures SF-1.6 lands at or after SF-1.1 — so the
  placeholder's deletion (SF-1.1) and the canonical gesture's wiring
  (SF-1.6) sequence cleanly without a window where the config route is
  unreachable.
- **SF-8.1** lands the real `ConfigMenuScreen` replacing
  `ConfigMenuPlaceholderScreen`. Until then the placeholder is the
  surface.
- **Phase 5** wires the assistant state machine + its overlays. The
  overlays render on top of `CurroRoute.Launcher`; no nav-graph change.

### Risks & mitigations

- **Risk**: Navigation Compose 2.8.5 (or whatever stable lands by
  2026-05-14) introduces a breaking API change vs the
  `navigation-patterns` skill's sketch (which uses
  `composable(route)` + `rememberNavController()`). **Mitigation**:
  Navigation 2.8.x is API-stable; the sketch is current. If 2.9.x is
  available by commit time the developer evaluates and either bumps
  or stays at 2.8.5 with a one-line rationale in the PR. The
  type-safe-routes API (2.8+ with `@Serializable` route classes) is
  not used here — string routes are simpler and match
  `navigation-patterns`'s sketch.
- **Risk**: The five new manifest attributes interact with HyperOS in
  unforeseen ways (e.g. HyperOS's launcher-lifecycle quirks documented
  in `launcher-app`). **Mitigation**: US-007 doesn't ship to a real
  Redmi 15 yet — testing is on the Pixel_10_Pro emulator. SF-1.1's
  device-installation step is the first time HyperOS-specific issues
  could surface; if they do, the manifest attributes can be revisited
  there.
- **Risk**: The debug `TextButton` accidentally becomes a permanent
  affordance because SF-1.1 forgets to remove it. **Mitigation**: the
  brief's `Out of Scope` section AND the
  `<!-- comment -->` over `launcher_placeholder_open_config_debug`
  in `strings.xml` both flag the retirement at SF-1.1. The KDoc on
  `LauncherPlaceholderScreen.kt` also flags "This screen will be
  deleted at SF-1.1". Three pointers.
- **Risk**: A future developer adds a child screen and accidentally
  re-introduces `Scaffold` / `TopAppBar`, violating the
  No-Double-Padding rule. **Mitigation**: US-007 establishes the
  pattern in two example screens; the brief's grep ACs codify the
  rule; a future custom detekt rule (deferred from US-003) will
  mechanically enforce it.
- **Risk**: The `enum class CurroRoute` choice locks Curro out of
  passing per-route parameters (e.g. `config?section=aliases`). **Mitigation**:
  the enum's `value: String` shape is forward-compatible — a future SF
  that needs parameters either (a) promotes to `sealed interface`
  + `data class`, or (b) keeps the enum and uses Compose Navigation's
  `arguments` block on the `composable { }` declaration with a query
  string. Either is O(15 min) reversibility.

### Cross-references

- **`navigation-patterns`** — authoritative for the `CurroNavHost`
  shape, the No-Double-Padding rule, the back-chevron pattern, the
  two-routes-only rule, the state-driven-overlays-not-routes rule.
- **`launcher-app`** — authoritative for `MainActivity`'s `singleTask`
  / `clearTaskOnLaunch` / `stateNotNeeded` / portrait attributes (minus
  `CATEGORY_HOME`, which is SF-1.1's job).
- **`compose-patterns`** — authoritative for the stateless `Content`
  composable pattern, `@Preview` conventions, and `LaunchedEffect`
  discipline (none used in US-007).
- **`accessibility-patterns`** — authoritative for the ≥ 96 dp tap
  target wrapping a 48 dp glyph (the back chevron), and the
  `contentDescription` discipline (`cd_back`).
- **`launcher-ui`** — describes the senior-first dimension contract;
  US-007 inherits via `Dimens.MinTapTarget` / `Dimens.LargeIconSize`
  from US-004.
- **`brand-design`** — locked by US-005 / US-006; US-007 doesn't touch
  the skill. The four new strings are NOT added to the canonical COPY
  table (rationale in §Senior-UX & Copy).
- **`spec-template`** — this brief's structural template.
- **`git-workflow`** — the commit conventions; US-007 follows the
  established `docs(prd):` / `feat(ui):` / `chore(...):` style; the
  brief-and-PRD commit suggested in the parent request is
  `docs(prd): add US-007 — CurroNavHost shell + MainActivity launcher Activity`.
- **`verification-checklist`** — the relevant sections (Build, Lint,
  Unit Tests, Accessibility, Dark Mode) pass; Privacy & Permissions,
  On-device Model, Assistant FSM, Function Catalog sections are N/A
  for US-007.

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-14 | Fran (Claude `android-product-analyst`) | Initial draft — US-007 / SF-0.6 — `CurroNavHost` shell + `MainActivity` launcher Activity (sans `CATEGORY_HOME`); four new string resources (1 permanent, 3 Phase-0 only); Navigation Compose activated from US-001-reserved slot; US-001 invariant on `MainActivity` lifts. |
