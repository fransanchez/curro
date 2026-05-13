---
name: material-design
description: Material Design 3 foundation for Curro — the theming system (CurroTheme / colorScheme / typography / shapes), the M3 colour roles and type scale, and component anatomy — with the explicit caveat that Curro scales Material UP (≥96 dp tap targets, much larger text) for its elderly user. Material is the floor; brand-design + launcher-ui own the bigger numbers.
triggers:
  - Material 3
  - Material Design
  - "M3"
  - color roles
  - color scheme
  - typography scale
  - components
  - TopAppBar
  - Card
  - Button
  - chips
  - bottom sheet
  - theme
  - elevation
---

# Material Design 3 (Curro)

Material 3 is Curro's **foundation** — the component anatomy, the theming system, the
colour roles, the type scale. But:

> **Curro scales Material 3 UP.** The Material type scale and the **48 dp touch-target
> minimum are the FLOOR for normal apps**; Curro's user (an elderly man with reduced
> fine motor control and deteriorated vision — spec §3) needs much bigger: **tap
> targets ≥ 96 dp**, text well above the M3 defaults (body-sized text reads like a
> headline), generous spacing, very high contrast (≥ 4.5:1 floor, aim ≥ 7:1 body),
> no fussy animation, a fixed/predictable layout. **`brand-design` is authoritative for
> the concrete colours / type sizes / radii** (currently a template — fill it in to
> satisfy those constraints); `launcher-ui` describes the surfaces; `accessibility-patterns`
> the a11y mechanics. Where any of those conflict with a Material default, **they win.**

Material gives Curro: the **theming machinery** (`MaterialTheme.colorScheme.*` /
`.typography.*` / `.shapes`), the **colour roles** (primary / surface / error / their
`on*` pairs), the **component anatomy** (cards, buttons, switches, sliders, …), and
the **type-scale slots** (`displayLarge` … `labelSmall`). Curro overrides the
*dimensions* (target sizes, text sizes, spacing) and adds the senior-first rules.

## Theme setup with `CurroTheme`

`presentation/theme/Theme.kt` — a Material 3 `MaterialTheme` wired to Curro's tokens:

```kotlin
@Composable
fun CurroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Deliberately a FIXED scheme — "feels the same every day" + the high-contrast floor
    // argue against dynamic wallpaper colour. (Decide in brand-design; don't default to dynamic.)
    val colorScheme = if (darkTheme) CurroColorScheme.Dark else CurroColorScheme.Light

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CurroTypography,                 // body-text role is large by default — see brand-design
        shapes = MaterialTheme.shapes.copy(
            small = CurroShapes.Small,
            medium = CurroShapes.Medium,
            large = CurroShapes.Large,
        ),
        content = content,
    )
}
```

`CurroColorScheme` (light + dark `ColorScheme`s), `CurroTypography` (a `Typography`),
`CurroShapes`, `CurroSpacing` all live in `presentation/theme/` and are defined by
`brand-design`. Composables read tokens via `MaterialTheme.colorScheme.*` /
`MaterialTheme.typography.*` / `CurroSpacing.*` / `CurroShapes.*` — **never** raw
`Color(0xFF…)` / `.sp` / `.dp` literals.

## Colour roles (M3) — Curro's usage

| Role | Curro usage |
|---|---|
| `primary` / `onPrimary` | the mic button, the SÍ button, selected states — `onPrimary` chosen for **contrast** |
| `primaryContainer` / `onPrimaryContainer` | a softer primary surface (rarely needed) |
| `surface` / `onSurface` | launcher background, screen backgrounds, body text — the workhorse pairing; **aim ≥ 7:1** |
| `surfaceVariant` / `onSurfaceVariant` | cards, app tiles, list rows, dividers |
| `error` / `onError` | error states (kept distinct from any brand accent) |
| `outline` | borders — but a "disabled"/"inactive" thing must still read at a distance |

Concrete hex values are **not** in this skill — they're in `brand-design` (a template;
must satisfy the contrast floor in light **and** dark, and look right under
high-contrast system mode).

## Typography scale (M3 slots) — Curro's mapping

Curro uses the same M3 slots, but the *sizes* are larger than the M3 defaults:

| Slot | Curro role (see `brand-design` for sizes) |
|---|---|
| `displayLarge` | the launcher clock — enormous |
| `displayMedium` | overlay headlines ("Te escucho…") |
| `headlineLarge` / `headlineMedium` | screen titles, card titles, list-row primary text, sender names |
| `titleLarge` | sub-sections |
| `bodyLarge` | **body text** (message bodies, prompts) — looks like a headline |
| `bodyMedium` | secondary text |
| `labelLarge` | the only small slot — button / chip text |

`bodySmall` / `labelSmall` exist for completeness but Curro should almost never use
something that small. The system font-scale setting is respected **on top of** these
(never capped — `accessibility-patterns`).

## Component usage (Curro flavour)

### Card

```kotlin
@Composable
fun ExampleCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(CurroSpacing.Medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(CurroSpacing.Large)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(body, style = MaterialTheme.typography.bodyLarge)         // body = headline-sized
        }
    }
}
```

### Buttons (sized up — ≥ 96 dp)

```kotlin
@Composable
fun ButtonExamples(onPrimary: () -> Unit, onSecondary: () -> Unit) {
    Column(Modifier.padding(CurroSpacing.Large), verticalArrangement = Arrangement.spacedBy(CurroSpacing.Large)) {
        Button(                                         // filled — primary CTA (e.g. SÍ)
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),   // Curro: ≥ 96 dp, not M3's 48 dp
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("Sí", style = MaterialTheme.typography.headlineMedium) }

        OutlinedButton(                                 // secondary (e.g. NO)
            onClick = onSecondary,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        ) { Text("No", style = MaterialTheme.typography.headlineMedium) }
    }
}
```

Curro has **no FAB** (a launcher home doesn't need one) — the equivalent "primary
action" is the giant mic button (`launcher-ui`).

### Switches & sliders (the config menu — Fran-only)

```kotlin
@Composable
fun ConfigControls(state: ConfigUiState, onEvent: (ConfigEvent) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(CurroSpacing.Large),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Confirma siempre", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Switch(checked = state.alwaysConfirm, onCheckedChange = { onEvent(ConfigEvent.SetAlwaysConfirm(it)) })
        }
        Column(Modifier.padding(CurroSpacing.Large)) {
            Text("Velocidad de habla", style = MaterialTheme.typography.headlineMedium)
            Slider(value = state.ttsRate, valueRange = 0.6f..1.2f,
                onValueChange = { onEvent(ConfigEvent.SetTtsRate(it)) })   // default ~0.85–0.90 — slightly slow
        }
    }
}
```

The config menu is the **one** place a normal-density Material layout is OK (it's for
Fran, not the senior) — but still **no `TopAppBar` in a child screen** (No-Double-
Padding — `CLAUDE.md`); back navigation = `Box` + an overlay back chevron at
`Alignment.TopStart`, sized ≥ 96 dp (see `navigation-patterns`).

### Modal bottom sheet

Used sparingly — e.g. the "edit alias" sheet inside the config menu:

```kotlin
@Composable
fun EditAliasSheet(alias: ContactAlias?, onDismiss: () -> Unit, onSave: (ContactAlias) -> Unit) {
    if (alias != null) {
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(CurroSpacing.Large)) {
                Text("Editar alias", style = MaterialTheme.typography.headlineMedium)
                // text fields + a ≥ 96 dp "Guardar" button
            }
        }
    }
}
```

### Navigation components — almost none

Curro is a **launcher**: no `NavigationBar` (bottom nav), no tabs, no
`NavigationRail` / `NavigationSuiteScaffold` (single fixed phone — see `adaptive-layout`).
The only "navigation chrome" anywhere is the config menu's back chevron. The launcher
home is just a `Column` of big things; the assistant overlays are state-driven (not
nav routes — see `compose-patterns` / `voice-interaction` / `navigation-patterns`).

## Dynamic colour — probably *not*

Material 3 supports dynamic wallpaper colour on Android 12+. For Curro it's likely the
wrong call: "feels the same every day" + the high-contrast floor argue for a **fixed**
scheme. Decide deliberately in `brand-design`; don't default `dynamicColor` to `true`.

## Edge-to-edge & predictive back

- `MainActivity` calls `enableEdgeToEdge()`; `CurroNavHost` is a single `Scaffold`
  that applies `innerPadding` (status-bar inset included) to the `NavHost` — child
  screens don't re-pad (No-Double-Padding — `CLAUDE.md`; insets detail in
  `adaptive-layout`).
- Predictive back is handled by the framework on Android 13+ — nothing extra needed;
  child screens that can go back use `BackHandler` / `navController.popBackStack()`.

## Rules

1. **Material is the FLOOR; Curro scales it UP** — ≥ 96 dp targets (not 48 dp), text well above M3 defaults, ≥ 4.5:1 (aim ≥ 7:1) contrast, generous spacing, no fussy animation, fixed layout. `brand-design` + `launcher-ui` + `accessibility-patterns` win where they conflict with a Material default.
2. **Use the Material theming machinery, not raw values** — `MaterialTheme.colorScheme.*` / `.typography.*` / `.shapes`; concrete colours/sizes/radii come from `brand-design` (a template — fill it in).
3. **Fixed colour scheme, not dynamic** (decide in `brand-design`) — "feels the same every day" + the contrast floor.
4. **Very few Material navigation components** — no bottom nav, no tabs; the config menu's back chevron is it; the assistant UI is state-driven overlays.
5. **No `TopAppBar`/`Scaffold` in child screens** (No-Double-Padding — `CLAUDE.md`); back = `Box` + overlay chevron at `TopStart`, ≥ 96 dp.
6. **`onPrimary`/`onSurface`/etc. are chosen for contrast, not aesthetics** — verify every pairing light AND dark.
