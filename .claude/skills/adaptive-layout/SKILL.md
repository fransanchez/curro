---
name: adaptive-layout
description: Edge-to-edge and system-insets handling for Curro. (Curro runs on one fixed phone, portrait-locked — there is no tablet/foldable/window-size-class story by design; only the insets bits below apply.)
triggers:
  - insets
  - edge-to-edge
  - system bars
  - status bar
  - navigation bar
  - WindowInsets
  - imePadding
  - displayCutout
  - orientation
---

# Adaptive Layout — *minimal for Curro*

Curro is a **launcher on one fixed device** (Xiaomi Redmi 15), **portrait-locked**
(`android:screenOrientation="portrait"` — see `launcher-app`). There is **no tablet,
foldable, or `WindowSizeClass` branching** — and that's deliberate: the spec's "it
must feel the same every day" rule is the *opposite* of adaptive (`launcher-ui` §
senior-first rules). So almost all the usual "adaptive layout" material doesn't apply.

What *does* apply is **edge-to-edge + system insets**:

- `MainActivity.onCreate()` calls `enableEdgeToEdge()`; the launcher draws under the
  system bars.
- `CurroNavHost` is a single `Scaffold` that applies its `innerPadding` to the
  `NavHost` (status-bar inset included) — **child screens don't add their own
  `Scaffold` / `TopAppBar` / `statusBarsPadding()`** (No-Double-Padding, `CLAUDE.md`).
- For full-bleed bits (the launcher background, the listening-overlay tint) use
  `Modifier.windowInsetsPadding(WindowInsets.systemBars)` (or `safeDrawing` if a
  cutout is in play) on the *content*, not nested inside the scaffold that already
  pads.
- The config menu (the only place with text input — alias editing) uses
  `Modifier.imePadding()` so fields stay above the keyboard.

Common insets, for reference:

| Inset | Use |
|---|---|
| `WindowInsets.statusBars` | status-bar area |
| `WindowInsets.navigationBars` | gesture / 3-button nav bar |
| `WindowInsets.systemBars` | status + navigation combined |
| `WindowInsets.safeDrawing` | all system UI incl. cutouts |
| `WindowInsets.ime` | soft keyboard |
| `WindowInsets.displayCutout` | notch / punch-hole |

If Curro ever needs to run on a second, very different device, revisit this — but for
the prototype, single phone, portrait, big and fixed.

(The previous full responsive/foldable/`NavigationSuiteScaffold`/`ListDetailPaneScaffold`
version is in git history; none of it fits a fixed-form-factor launcher.)
