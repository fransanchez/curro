# Brand Design

> **AUTHORITATIVE.** This file is the source of truth for every design decision in
> Curro: colours, typography, spacing, shapes, **and Curro's spoken/written voice**.
> Other design skills (`material-design`, `compose-patterns`, `launcher-ui`,
> `accessibility-patterns`) and the voice skill (`voice-interaction`) defer to the
> values defined here. Source for the senior-first constraints:
> `docs/curro-spec-v1.0.md` §2 (voice), §3 (the user), §11 (the surfaces). The
> values below were locked by US-005 (SF-0.7, promoted).

Kotlin / Jetpack Compose implementation of the Curro brand system, plus the
canonical Spanish copy. The values land in `app/src/main/java/com/curro/app/
presentation/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `CurroSpacing.kt`,
`Dimens.kt`, `CurroTheme.kt`) and in `app/src/main/res/values/strings.xml` (Spanish
is the default locale — there is no `values-es/`). Composables read tokens via
`MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` / `CurroSpacing.*` /
`CurroShapes.*` / `Dimens.*` — never raw `Color(0xFF…)` / `.sp` / `.dp` literals.

---

## Senior-first constraints (non-negotiable — spec §3, §11)

Curro's only validated user: a man in Málaga (Fran's father) — deteriorated-but-
functional vision, good hearing, **reduced fine motor control**, very slow learning
curve for new UIs. The brand is chosen around this. **These override Material 3
defaults wherever they conflict.**

1. **Tap targets ≥ 96 dp.** (Spec §3 — *not* Material's 48 dp.) Generous spacing
   between targets so a neighbour isn't hit by accident. The main mic button on the
   launcher home is **≥ 40 % of the screen**. SÍ/NO confirmation buttons and app
   tiles are huge. Codified as `Dimens.MinTapTarget = 96.dp` and
   `Dimens.MicButtonMinHeightFraction = 0.40f`.
2. **Text well above Material defaults.** Body-text role reads like a Material
   headline; the launcher clock is enormous (`displayLarge = 72 sp`). The
   senior-first floor (US-004 A1) for every M3 role is documented in `Type.kt`'s
   KDoc — US-005's values are at or above that floor.
3. **Respect AND amplify the system font-scale setting.** Never cap `fontScale`;
   the layout must survive `1.5×`–`2.0×` (preview every reusable component at
   those scales — see `compose-patterns`, `accessibility-patterns`).
4. **Very high contrast.** WCAG AA (≥ 4.5:1 body, ≥ 3:1 large/UI) is the **floor,
   not the goal** — every brand pairing below clears **≥ 7:1 for body** in both
   light and dark. Verified by measurement.
5. **Colour is never the only signal.** Always pair colour with text / icon /
   shape (a selected / active / error state must read without colour vision).
6. **No fussy animation.** Calm, quick, quiet transitions; a static or very gentle
   indicator while `processing`. Nothing parallax, nothing distracting (spec §11).
7. **It feels the same every day.** The launcher layout is **fixed and
   predictable** — clock here, mic button there, the same app tiles in the same
   spots; the favourites grid recomputes *occasionally*, not on every open
   (`local-data`). No "smart" reordering, no A/B-ish variation. `dynamicColor` is
   structurally disabled in `CurroTheme` (US-004 A7).
8. **Audio + visual together, always.** Every Curro→user message is **spoken and
   shown** (spec §4.6). The screen reinforces the voice; it never replaces it.

The surfaces these constraints apply to are described in `launcher-ui` (the
launcher home, the state-driven assistant overlays, the hidden config menu).

---

## Color Palette — "Sol y olivar"

Warm Andalusian palette: terracotta primary, olive-green secondary, sun-ochre
tertiary, cream surfaces. Locked by US-005. The cream background is deliberately
off-white (#FFF8EE) — pure white glares on aged eyes and reads as clinical; cream
reads as a book page. `primary` is the **SÍ** colour (warm affirmation); `secondary`
is the **NO** colour (calm rejection, not alarming); `error` red is reserved for
genuine failures.

### Light scheme (`LightColors` in `presentation/theme/Color.kt`)

| M3 role | Hex | On-pair contrast |
|---|---|---|
| `primary` | `#9A3E15` | `onPrimary` on `primary` = **~6.5:1** (≥ 3:1 button/UI floor — see note below) |
| `onPrimary` | `#FFF8EE` | — |
| `primaryContainer` | `#FFD9C2` | `onPrimaryContainer` on `primaryContainer` = **10.4:1** |
| `onPrimaryContainer` | `#3A1700` | — |
| `secondary` | `#4F5D2E` | `onSecondary` on `secondary` = **~6.8:1** (≥ 3:1 button/UI floor — see note below) |
| `onSecondary` | `#FFF8EE` | — |
| `secondaryContainer` | `#DDE5C8` | `onSecondaryContainer` on `secondaryContainer` = **12.6:1** |
| `onSecondaryContainer` | `#1A2300` | — |
| `tertiary` | `#7A4D00` | `onTertiary` on `tertiary` = **~6.9:1** (≥ 3:1 button/UI floor — see note below) |
| `onTertiary` | `#FFF8EE` | — |
| `tertiaryContainer` | `#FFE2B0` | `onTertiaryContainer` on `tertiaryContainer` = **9.9:1** |
| `onTertiaryContainer` | `#3A2400` | — |
| `error` | `#A11414` | `onError` on `error` = **7.6:1** |
| `onError` | `#FFF8EE` | — |
| `errorContainer` | `#FCDAD6` | `onErrorContainer` on `errorContainer` = **12.5:1** |
| `onErrorContainer` | `#410E0B` | — |
| `background` | `#FFF8EE` | `onBackground` on `background` = **17.3:1** |
| `onBackground` | `#1A1410` | — |
| `surface` | `#FFF8EE` | `onSurface` on `surface` = **17.3:1** |
| `onSurface` | `#1A1410` | — |
| `surfaceVariant` | `#F0E5D0` | `onSurfaceVariant` on `surfaceVariant` = **14.6:1** |
| `onSurfaceVariant` | `#1A1410` | — |
| `outline` | `#7A6E5C` | on `surface` = **4.7:1** (UI floor ≥ 3:1) |
| `outlineVariant` | `#D8CCB6` | (divider) |
| `scrim` | `#000000` | (alpha-blended) |
| `inverseSurface` | `#1A1410` | `inverseOnSurface` on `inverseSurface` = **17.3:1** |
| `inverseOnSurface` | `#FFF8EE` | — |
| `inversePrimary` | `#FFB088` | (used on dark inverseSurface) |
| `surfaceTint` | `#9A3E15` (= `primary`) | (M3 elevation overlay) |

> **Note on button-fill roles (onPrimary/primary, onSecondary/secondary, onTertiary/tertiary):**
> The three button-fill pairings target ≥ 3:1 WCAG AA large/UI, not the 7:1 body aspirational —
> they never render body text against their `on*` colour. The measured light ratios (~6.5:1, ~6.8:1,
> ~6.9:1) comfortably clear the ≥ 3:1 UI floor and also clear WCAG AA body (≥ 4.5:1). The brand's
> ≥ 7:1 aspirational floor applies to body-text pairings (onSurface/surface = 17.3:1, etc.) only.

### Dark scheme (`DarkColors`) — "Olivar de noche"

The night counterpart: deep warm-brown surfaces, brightened terracotta /
olive / ochre. Fixes US-004's flagged dark `error / surface ~3.3:1` issue —
`error = #FFB4AB` on `surface = #1A120D` is now ≈ **10.9:1**.

| M3 role | Hex | On-pair contrast |
|---|---|---|
| `primary` | `#FFB088` | `onPrimary` on `primary` = **~9.1:1** (≥ 3:1 button/UI floor — see note above) |
| `onPrimary` | `#3A1700` | — |
| `primaryContainer` | `#7A2D08` | `onPrimaryContainer` on `primaryContainer` = **7.2:1** |
| `onPrimaryContainer` | `#FFD9C2` | — |
| `secondary` | `#BAC68E` | `onSecondary` on `secondary` = **~9.0:1** (≥ 3:1 button/UI floor — see note above) |
| `onSecondary` | `#1A2300` | — |
| `secondaryContainer` | `#3A4520` | `onSecondaryContainer` on `secondaryContainer` = **7.9:1** |
| `onSecondaryContainer` | `#DDE5C8` | — |
| `tertiary` | `#F5C078` | `onTertiary` on `tertiary` = **~8.9:1** (≥ 3:1 button/UI floor — see note above) |
| `onTertiary` | `#3A2400` | — |
| `tertiaryContainer` | `#5C3800` | `onTertiaryContainer` on `tertiaryContainer` = **7.6:1** |
| `onTertiaryContainer` | `#FFE2B0` | — |
| `error` | `#FFB4AB` | `onError` on `error` = **8.4:1** |
| `onError` | `#690005` | — |
| `errorContainer` | `#93000A` | `onErrorContainer` on `errorContainer` = **8.6:1** |
| `onErrorContainer` | `#FFDAD6` | — |
| `background` | `#1A120D` | `onBackground` on `background` = **16.0:1** |
| `onBackground` | `#FFEBD9` | — |
| `surface` | `#1A120D` | `onSurface` on `surface` = **16.0:1** |
| `onSurface` | `#FFEBD9` | — |
| `surfaceVariant` | `#2A1F17` | `onSurfaceVariant` on `surfaceVariant` = **13.9:1** |
| `onSurfaceVariant` | `#FFEBD9` | — |
| `outline` | `#A8957D` | on `surface` = **6.4:1** |
| `outlineVariant` | `#4A3C2E` | (divider) |
| `scrim` | `#000000` | (alpha-blended) |
| `inverseSurface` | `#FFEBD9` | `inverseOnSurface` on `inverseSurface` = **15.7:1** |
| `inverseOnSurface` | `#1A120D` | — |
| `inversePrimary` | `#9A3E15` | (used on light inverseSurface) |
| `surfaceTint` | `#FFB088` (= `primary`) | — |

### `ListeningTint` — Curro extension (not part of M3 `ColorScheme`)

The light-blue overlay applied while `listening` (spec §11 "se vuelve azul claro").
Picked desaturated rather than vivid so it harmonises with the warm palette.

| Token | Light hex | Dark hex | Notes |
|---|---|---|---|
| `CurroListeningTintLight` | `#B8D4E8` | — | Dusty pale blue. Live transcript (`onSurface = #1A1410`) on this tint ≈ **14.5:1**. |
| `CurroListeningTintDark` | — | `#1A2A38` | Deep dusty blue, distinct from warm-brown `surface`. Live transcript (`onSurface = #FFEBD9`) on this tint ≈ **12.6:1**. |

Exposed as two top-level `val`s in `Color.kt`. No M3 slot fits this; it's a
deliberate Curro extension.

### Window-background hex (XML splash)

`app/src/main/res/values/colors.xml` `curro_window_background = #FFF8EE` (= Light
`background`); `app/src/main/res/values-night/colors.xml`
`curro_window_background = #1A120D` (= Dark `background`). Keep in sync with
`Color.kt` — the cold-launch splash must not flash a contrasting frame.

---

## Typography

Senior-first scale. Body role reads like a Material headline; the clock is
enormous. Line heights are explicit (generous letting matters at large sizes for
elderly readability). Font family: `FontFamily.Default` (system font) — bundling a
custom font is a future asset SF.

| Role (M3 slot) | sp | FontWeight | lineHeight (sp) | Curro usage |
|---|---|---|---|---|
| `displayLarge` | **72** | ExtraBold | 80 | Launcher clock (bumped from US-004 floor of 64 — the focal point of home) |
| `displayMedium` | 48 | Bold | 56 | Overlay headlines ("Te escucho…") |
| `displaySmall` | 40 | Bold | 48 | Rarely used |
| `headlineLarge` | 32 | Bold | 40 | Screen titles, sender names on cards |
| `headlineMedium` | 28 | SemiBold | 36 | Card titles, list-row primary text — the most common large-text role |
| `headlineSmall` | 24 | SemiBold | 32 | Less used |
| `titleLarge` | 22 | SemiBold | 28 | Sub-sections, short button labels |
| `titleMedium` | 20 | Medium | 26 | Sub-sections |
| `titleSmall` | 18 | Medium | 24 | Rare |
| `bodyLarge` | 20 | Normal | 28 | **Body text** — message bodies, prompts |
| `bodyMedium` | 18 | Normal | 26 | Secondary text |
| `bodySmall` | 16 | Normal | 24 | Floor; Curro almost never goes below this |
| `labelLarge` | 18 | SemiBold | 24 | Button text (SÍ / NO / "Más apps") |
| `labelMedium` | 16 | SemiBold | 22 | Rare |
| `labelSmall` | 14 | Medium | 20 | Avoid |

Compare M3 defaults: M3 `bodyLarge` = 16 sp; Curro = 20 sp (+25 %). M3
`displayLarge` = 57 sp; Curro = 72 sp (+26 %). Exposed via `CurroTypography` (a
Material 3 `Typography` instance) inside `CurroTheme`. Composables read
`MaterialTheme.typography.*` — never `.sp` literals.

---

## Spacing System

7-step lowercase scale shipped by US-004 (A1). The gap between adjacent tap
targets is `xl` or `xxl`, **never `m`** — fine-motor-control demands generosity.
Read via `CurroSpacing.<token>` directly. Never raw `.dp` literals in composables.

```kotlin
object CurroSpacing {
    val none: Dp = 0.dp     // explicit-zero for Modifier.padding(...) defaults
    val xs: Dp = 4.dp       // tight inner gaps within a component (icon ↔ label)
    val s: Dp = 8.dp        // inner padding, tight gaps within a card
    val m: Dp = 16.dp       // standard padding (Material's 16-dp grid baseline)
    val l: Dp = 24.dp       // section spacing
    val xl: Dp = 32.dp      // screen-level padding, gaps between tap targets
    val xxl: Dp = 48.dp     // extra-generous gap between adjacent big buttons / tiles
}
```

---

## Corner Radius / Shapes

Warm/friendly bias: slightly larger radii than M3 defaults — enough to feel
rounded without becoming cartoony.

```kotlin
val CurroShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // chips, small inline tags (rare)
    small      = RoundedCornerShape(16.dp),  // app tiles, list rows, picker rows
    medium     = RoundedCornerShape(20.dp),  // big buttons, cards, containers
    large      = RoundedCornerShape(28.dp),  // large surfaces, the mic button
    extraLarge = RoundedCornerShape(36.dp),  // rare — overlay sheets
)
```

Wired into `MaterialTheme.shapes` via `CurroTheme`. Composables use
`MaterialTheme.shapes.*` / `CurroShapes.*` — never `.dp` literals.

---

## `Dimens` — senior-first dimension contract (US-004 A2 — locked)

Mechanical invariants, not a scale. Every touch target / mic button / card / list
row respects them.

```kotlin
object Dimens {
    val MinTapTarget: Dp = 96.dp          // 2× Material's 48 dp (spec §3, §11)
    const val MicButtonMinHeightFraction: Float = 0.40f   // ≥ 40 % of screen height
    val BigButtonHeight: Dp = 96.dp
    val BigRowHeight: Dp = 96.dp
    val LargeIconSize: Dp = 48.dp         // glyph inside a 96 dp IconButton
    val CardElevation: Dp = 2.dp
}
```

---

## Theme Entry Point

All screens are wrapped in `CurroTheme { … }` (see
`app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt`), which
supplies `LightColors` / `DarkColors`, `CurroTypography`, and `CurroShapes`.
**`dynamicColor` is structurally disabled** — the dynamic-color APIs are not even
imported (US-004 A7); the wallpaper-coloured paths require a deliberate
multi-file change to re-enable. Rationale: "feels the same every day" +
senior-first contrast floor outrank user-wallpaper personalisation.

Composables read tokens through `MaterialTheme.colorScheme.*` /
`MaterialTheme.typography.*` / `CurroSpacing.*` / `CurroShapes.*` / `Dimens.*` —
**never** raw `Color(0xFF…)` / `.sp` / `.dp` literals. The
`config/detekt/detekt.yml` `MagicNumber` exclude is scoped to
`**/presentation/theme/**`; every other path triggers the rule.

---

## Logo & Iconography

**Prototype reality:** Curro ships a text-only wordmark — the word "Curro" rendered
in `displayLarge` on the launcher home (the mic button label is "CURRO" per spec
§11). The Android launcher icon shipped with US-001 (a bitmap from the AGP
template) stands for now.

- **App icon / wordmark**: text-only ("Curro"), no SVG logo for the prototype.
  Logo design is a future asset SF (out of scope for the prototype — the
  validation question is whether the user uses Curro, not whether the logo is
  beautiful).
- **Mic icon**: a Material microphone glyph (`Icons.Filled.Mic` or equivalent),
  large, unmistakable.
- **Usage rules**: no recolouring, no rotation, no effects on the wordmark. The
  letters are typographic; they live in the font.

---

## Component Patterns

Built around the shared big components in `presentation/common/` (shipped by
US-004 + Phase 0.5):

### `BigPrimaryButton` (the SÍ / "Más apps" / CTA brick)

```kotlin
@Composable
fun BigPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.MinTapTarget),   // ≥ 96 dp
        shape = MaterialTheme.shapes.medium,                       // CurroShapes.medium (20 dp)
        enabled = enabled,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)    // 18 sp SemiBold
    }
}
```

For the **NO** button: `BigPrimaryButton` reused, but the call site provides a
`Button` with `secondary` colours (or use Material 3 `FilledTonalButton`
configured to read from `MaterialTheme.colorScheme.secondaryContainer`). **NO is
never `error`-coloured** — saying "no" is not a failure condition.

### `BigCard` (the WhatsApp message card / picker row brick)

```kotlin
@Composable
fun BigCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .heightIn(min = Dimens.MinTapTarget)                   // ≥ 96 dp if clickable
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.medium,                       // CurroShapes.medium (20 dp)
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation),  // 2.dp
    ) {
        Column(Modifier.padding(CurroSpacing.l), content = content)
    }
}
```

### `BigYesNoRow` (deferred to SF-0.5 — sketch for reference)

```kotlin
@Composable
fun BigYesNoRow(onYes: () -> Unit, onNo: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CurroSpacing.xxl)) {  // 48 dp gap
        BigPrimaryButton("Sí", onYes, Modifier.weight(1f))                  // primary terracotta
        // NO: a secondary-coloured big button (not error-coloured)
        FilledTonalButton(
            onClick = onNo,
            modifier = Modifier.weight(1f).heightIn(min = Dimens.MinTapTarget),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Text("No", style = MaterialTheme.typography.labelLarge)
        }
    }
}
```

### `BigListRow` (deferred to SF-0.5 — sketch for reference)

```kotlin
@Composable
fun BigListRow(
    label: String,
    iconOrPhoto: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTapTarget)                            // ≥ 96 dp
            .clickable(onClick = onClick)
            .padding(CurroSpacing.l),                                        // 24 dp
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = iconOrPhoto,
            contentDescription = null,                                       // label says it
            modifier = Modifier
                .size(CurroSpacing.xxl + CurroSpacing.m)                     // ≈ 64 dp
                .clip(MaterialTheme.shapes.small),                           // 16 dp radius
        )
        Spacer(Modifier.width(CurroSpacing.l))
        Text(
            label,
            style = MaterialTheme.typography.headlineMedium,                 // 28 sp SemiBold
            maxLines = 2,
        )
    }
}
```

---

## Image Aspect Ratios

Curro shows very little imagery — only contact photos and app icons.

- **Contact photos**: 1:1, circular (clipped with `MaterialTheme.shapes.small` or
  fully circular via `CircleShape` in the picker — both acceptable).
- **App icons**: 1:1, square with `MaterialTheme.shapes.small` (16 dp) radius (so
  icons look unified across the launcher and "Más apps" list, regardless of
  whether the source icon is adaptive).
- **No hero images, no banners, no media gallery** — there are none in Curro's
  surfaces.

Coil `AsyncImage` always sets `contentDescription` (or `null` if purely
decorative — the label does the work).

---

## Accessibility (non-negotiable — consistent with the senior-first constraints above)

- **Minimum touch target: 96 dp × 96 dp** (`Dimens.MinTapTarget`). The mic button
  ≥ 40 % of the screen (`Dimens.MicButtonMinHeightFraction`). Generous spacing
  between targets (`CurroSpacing.xl` or `xxl`).
- **Contrast**: body-text pairings (onSurface/surface, onBackground/background,
  onSurfaceVariant/surfaceVariant) clear **≥ 7:1** in both light and dark — the
  brand's aspirational floor. Button-fill roles (onPrimary/primary, onSecondary/
  secondary, onTertiary/tertiary) target ≥ 3:1 WCAG AA large/UI only — they never
  render body text against their `on*` colour; measured ~6.5:1 / ~6.8:1 / ~6.9:1
  (light), ~9.1:1 / ~9.0:1 / ~8.9:1 (dark). Outline-on-surface = 4.7:1
  (light) / 6.4:1 (dark).
- **Colour is never the only signal** — pair with text/icon/shape.
- **Every `Image`/`Icon` has a `contentDescription`** (or `null` if decorative).
- **Respect and amplify the system font-scale setting** — never cap it; survive
  `2.0×`.
- **TalkBack is secondary** for Curro (the user isn't a TalkBack user; the app is
  audio-first by design via TTS). But the semantics mechanics (live regions,
  `stateDescription`, custom actions, `mergeDescendants`) still apply — see
  `accessibility-patterns`.

---

## Curro's voice — tone (spec §2, non-negotiable)

Warm, Andalusian, **colloquial Castilian Spanish** — **efficient and close, NOT
servile.** Like a friend who helps, not a butler.

- Vale "Vale, llamando a Pepito." · "Un momento…" · "Lo apunto: Lucía Ruiz es tu
  hija. Llamando." · "No tienes mensajes nuevos."
- Nope "Claro, cómo no, ahora mismo." · constant "lo siento / disculpa" · codes,
  jargon, technical terms · silence · trapping the user in loops.

### Fail comprehensibly (spec §2, §6 flows 6 & 7)

**Every error → a plain Spanish sentence + a proposed alternative.** Never a code,
never a stack of jargon, never silence. Examples canonicalised in the COPY table
below: `copy_unknown_function`, `copy_disambig_give_up`,
`copy_alias_defer_to_fran`, `copy_whatsapp_parse_miss`, `copy_perm_missing_calls`,
`copy_perm_missing_contacts`, `copy_calc_failed`, `copy_contact_not_found`,
`copy_app_not_found`.

---

## Copy (Spanish, AUTHORITATIVE)

The canonical Spanish copy table. Stable IDs, in Curro's voice, landed as
`<string name="copy_<id>">…</string>` entries in
`app/src/main/res/values/strings.xml` (Spanish is the default locale — no
`values-es/`). Parameterised lines use Android positional args (`%1$s`, `%1$d`).

**Provenance**: *(spec §6)* = verbatim from the spec, a closed decision per §14
— do not rewrite; *(spec §2)* = verbatim from spec §2's voice examples;
*(NEW)* = written for US-005 because the spec doesn't provide the line.

Composables consume these via `stringResource(R.string.copy_<id>, …)`. **Never**
hard-code Spanish strings.

### Listening / processing

| ID | Spanish | Provenance |
|---|---|---|
| `copy_listening_prompt` | Te escucho… | spec §6 |
| `copy_processing` | Un momento… | spec §6 |

### Confirmation (Phase 6)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_confirm_call` | ¿Llamo a %1$s? | spec §6 flow 2 |
| `copy_confirm_call_doublecheck` | Voy a llamar a %1$s, ¿confirmas? | spec §4.3 / §6 flow 2 |
| `copy_confirm_reply` | ¿Mando este mensaje a %1$s? | (NEW) — Fase 2 prep |
| `copy_cancel_no_call` | Vale, no llamo. | spec §6 flow 2 |
| `copy_cancel_no_reply` | Vale, no lo mando. | (NEW) — Fase 2 prep |
| `copy_yes` | SÍ | (NEW) US-006 — BigYesNoRow default affirmation label |
| `copy_no` | NO | (NEW) US-006 — BigYesNoRow default rejection label; NO is `secondary`-coloured, never `error`-red |
| `copy_confirm_timeout` | Cancelo entonces. | spec §6 flow 2 |

### Execution announcements (Phase 4 handlers)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_calling` | Llamando a %1$s. | spec §6 flows 1–4 |
| `copy_calling_confirmed` | Vale, llamando. | spec §6 flow 2 |
| `copy_reading_summary_one` | Tienes %1$d mensaje de %2$s. | (NEW — singular case) |
| `copy_reading_summary_many` | Tienes %1$d mensajes de %2$s. | spec §6 flow 5 |
| `copy_reading_summary_multi_sender` | Tienes %1$d mensajes de %2$s y %3$d mensaje de %4$s. | spec §6 flow 5 |
| `copy_reading_starts_with` | Empiezo con %1$s: | spec §6 flow 5 |
| `copy_reading_from` | De %1$s: %2$s | spec §6 flow 5 |
| `copy_no_unread` | No tienes mensajes nuevos. | spec §6 flow 5 |
| `copy_many_unread` | Tienes muchos mensajes. ¿Te los leo todos o solo los de alguien? | spec §6 flow 5 (shortened) |
| `copy_calc_result` | %1$s son %2$s. | (NEW) |
| `copy_time_now` | Son las %1$s. | (NEW) |
| `copy_time_date` | Hoy es %1$s, %2$s. | (NEW) |
| `copy_app_opening` | Abriendo %1$s. | (NEW) |

### STT failure recovery (Phase 5)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_stt_fail_1` | No te he oído bien, ¿puedes repetirlo? | spec §6 flow 6 |
| `copy_stt_fail_2` | Sigo sin entenderte. Acércate un poco al teléfono y habla más alto. | spec §6 flow 6 |
| `copy_stt_fail_3` | Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo. | spec §6 flow 6 |

### Invalid model output (Phase 5)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_unknown_function` | Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di "ayuda" para que te cuente lo que sí sé hacer. | spec §6 flow 7 |

### Disambiguation (Phase 4 — `CallContactHandler`)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_disambig_ask_three` | Tienes %1$d %2$ss. ¿Cuál de ellas?: %3$s, %4$s o %5$s. | spec §6 flow 3 (feminine) |
| `copy_disambig_ask_three_masc` | Tienes %1$d %2$ss. ¿Cuál de ellos?: %3$s, %4$s o %5$s. | (NEW — masculine) |
| `copy_disambig_ask_n` | Tienes %1$d coincidencias para %2$s. Las primeras son: %3$s. ¿Cuál? | (NEW — > 3 matches) |
| `copy_disambig_give_up` | Mejor llámala desde la agenda, no me aclaro. | spec §6 flow 3 |
| `copy_disambig_give_up_masc` | Mejor llámalo desde la agenda, no me aclaro. | (NEW — masculine) |
| `copy_disambig_none_option` | Ninguna de estas | spec §11 / §6 flow 3 |
| `copy_disambig_none_option_masc` | Ninguno de estos | (NEW — masculine) |

### Alias learning (Phase 7)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_alias_ask` | Aún no sé quién es %1$s. ¿Es alguno de estos contactos? Te los leo: %2$s. | spec §6 flow 4 |
| `copy_alias_ask_more` | …o dime su nombre. | (NEW — extension of spec §6 flow 4) |
| `copy_alias_saved` | Vale, %1$s es %2$s. Apuntado. Llamando ahora. | spec §6 flow 4 |
| `copy_alias_saved_short` | Lo apunto: %1$s es %2$s. Llamando. | spec §6 flow 4 (screen-text variant) |
| `copy_alias_defer_to_fran` | Vale, no pasa nada. Dile a Fran que apunte quién es %1$s. | spec §6 flow 4 |

### Model cold (Phase 9 — Gemma 3n)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_cold_model` | Dame un segundo. | spec §4.4 |

### Help (Phase 4 — `HelpHandler`)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_help_generic` | Puedo llamar a tus contactos, leer tus mensajes de WhatsApp, abrir apps, hacer cálculos y decirte la hora. Pulsa el botón y dime lo que necesitas. | (NEW) |
| `copy_help_topic_call` | Para llamar, pulsa el botón y di "llama a" y el nombre de la persona, o "ponme con" y el nombre. | (NEW) |
| `copy_help_topic_whatsapp` | Para leer tus mensajes de WhatsApp, pulsa el botón y di "léeme los mensajes", o "qué dice" y el nombre de quien te escribió. | (NEW) |
| `copy_help_topic_app` | Para abrir una app, pulsa el botón y di "abre" y el nombre de la app. | (NEW) |

### Permission missing (Phase 4 handlers)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_perm_missing_contacts` | Necesito permiso para ver tus contactos. Díselo a Fran. | (NEW — extends spec §2 voice rule) |
| `copy_perm_missing_calls` | Necesito permiso para llamar. Díselo a Fran. | spec §2 (shortened) |
| `copy_perm_missing_notifs` | Necesito que me dejes leer las notificaciones. Díselo a Fran. | (NEW) |
| `copy_perm_missing_mic` | Necesito permiso para escucharte. Díselo a Fran. | (NEW) |

### Empty / not-found (Phase 4 handlers)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_contact_not_found` | No encuentro a %1$s en tus contactos. | (NEW) |
| `copy_app_not_found` | No tengo ninguna app que se llame así. | (NEW) |
| `copy_calc_failed` | No he podido hacer ese cálculo. ¿Lo repites más despacio? | (NEW) |
| `copy_whatsapp_parse_miss` | Tienes mensajes nuevos pero no he podido leerlos bien. | (spec implied) |

### Launcher home (Phase 1)

| ID | Spanish | Provenance |
|---|---|---|
| `copy_home_make_default` | Hazme tu pantalla de inicio | master-plan SF-1.1 |
| `copy_home_more_apps` | Más apps | spec §11 |
| `copy_home_mic_label` | CURRO | spec §11 |

### Reading messages — composed at runtime

Reading is composed in Curro's voice using `copy_reading_summary_*` +
`copy_reading_starts_with` + `copy_reading_from` + the message body itself —
**grouped by sender, not by time** (spec §6 flow 5).

Example composition for 3 from Pepito + 1 from Lucía:

> "Tienes 3 mensajes de Pepito y 1 mensaje de Lucía. Empiezo con Pepito: 'Te
> espero a las siete'. 'Trae el pan'. 'Y vino si puedes'. De Lucía: 'Mañana te
> llamo, papá'."

> When you add a new catalog function, every line its handler can speak gets a
> `copy_*` entry here, in this voice, **before** it ships
> (`function-catalog` → "how to add").

---

## Rules

1. **Senior-first overrides Material defaults** — ≥ 96 dp targets, big text, high
   contrast, no fussy animation, fixed/stable layout, audio + visual together.
2. **Read tokens, not literals** — `MaterialTheme.colorScheme.*` /
   `MaterialTheme.typography.*` / `CurroSpacing.*` / `CurroShapes.*` /
   `Dimens.*`; raw `Color(0xFF…)` / `.sp` / `.dp` only allowed in
   `presentation/theme/` (the linter enforces this scope).
3. **`dynamicColor` stays off** — predictability + senior contrast outrank
   wallpaper personalisation.
4. **Spanish copy lives in `strings.xml` under `copy_*` IDs, in this skill's
   table**, in Curro's voice. Never hard-code a Spanish string in a composable.
5. **`primary` = SÍ; `secondary` = NO; `error` is reserved for genuine failures.**
6. **Adding a function or a new error path also adds a `copy_*` line here** —
   before the handler ships.
7. **`brand-design` is authoritative** for colours/type/radii/copy; every other
   design skill (`material-design`, `compose-patterns`, `launcher-ui`,
   `accessibility-patterns`) and the voice skill (`voice-interaction`) defer to
   the values defined here.
