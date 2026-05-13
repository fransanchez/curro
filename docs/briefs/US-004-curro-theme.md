# US-004 — `CurroTheme` scaffold + senior-first tokens

> Implementation brief for **SF-0.4** (`docs/master-plan.md` → Phase 0). This
> brief is the *what to build*; `/implement-feature US-004` is the *how / when*.
> The brief follows `.claude/skills/spec-template/SKILL.md`.
>
> **Architect involvement: RECOMMENDED (≈ 30 min before development).** US-004
> is not "drop in some Material defaults" — it bakes in five decisions that will
> propagate to *every* composable Curro will ever ship: (1) plain
> `object CurroSpacing` vs `CompositionLocal`-backed access; (2) `Dimens` object
> vs typography extension for the senior-first dp/Float contract; (3) how the
> system `fontScale` is amplified for this specific user (let the system do its
> job vs a `Density` override vs a wrapper composable); (4) the *shape* of the
> placeholder palette (clearly-grey-on-white so nobody mistakes it for the
> brand, vs lightly-tinted so the dev build doesn't look offensively raw); and
> (5) how `dynamicColor = false` is structurally enforced (no parameter, no
> reachable code path), not just commented. Each of these is mechanical to
> implement and hard to reverse after Phase 1 + 2 + 4 land. PM precedent: the
> Q1–Q5 architect pass on US-002 saved real refactoring downstream;
> the propagation surface here is larger, so the case for an architect pass is
> stronger, not weaker. **The architect resolves Q1–Q5 below before the
> developer is unblocked.**

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `CurroTheme` scaffold + senior-first tokens |
| **US ID** | US-004 |
| **SF ID** | SF-0.4 (master-plan) |
| **Phase** | 0 — Project foundation |
| **Status** | In Progress |
| **Created** | 2026-05-13 |
| **Modified** | 2026-05-13 |
| **PM Owner** | Fran (Claude `android-product-analyst`) |
| **Architect** | *(pending — `android-architect` to resolve Q1–Q5 before `/implement-feature US-004` runs; see brief preamble + Open Questions)* |

## Summary

Replace the US-001 no-op `CurroTheme` stub (currently
`MaterialTheme(content = content)` with a `TODO(SF-0.4)` marker) with the
real `presentation/theme/` package: light + dark `ColorScheme`s, a Curro-shaped
`Typography` instance where the body role is **headline-sized** (a Material-3
default `bodyLarge` of 16sp is the *floor*, not the target — Fran's father needs
≥ 20sp on body), the rounded-corner `Shapes`, the named `Spacing` scale, and a
new `Dimens` constants file that codifies the **senior-first dimension contract**
(≥ 96 dp tap targets, ≥ 40 % screen mic button) as named values composables
reference instead of magic numbers. `dynamicColor` is **hard-disabled** — there
is no parameter for it, the wallpaper-coloured paths are not even imported, and
the KDoc on `CurroTheme.kt` says why: "feels the same every day" + the
high-contrast floor outrank user-wallpaper personalisation. On top of the
tokens, ship the **first** of the shared big components every Phase-1 surface
will compose itself out of — `BigPrimaryButton` (mandatory, the bricks for SÍ /
"Más apps" / "Hazme tu pantalla de inicio" / overlay primary CTAs) and `BigCard`
(strongly preferred, the brick for WhatsApp message cards, contact-picker rows,
config-menu rows). `BigYesNoRow` and `BigListRow` are deferred to SF-0.5 with
explicit rationale (see *Scope → Out of Scope*).

**Crucially, this SF ships placeholder *values* with the senior-first *contract***.
The `LightColors`/`DarkColors` hexes, the concrete sp numbers in `CurroTypography`,
the dp numbers in `CurroSpacing` and `CurroShapes` — all stand in for the real
brand. US-005 (SF-0.7, promoted) replaces them without touching a single
composable consumer. The contract — semantic-tokens-only, ≥ 96 dp tap targets,
body-as-headline, dynamic-color-off, system fontScale respected and amplified —
ships **here**, lives forever, and is what every subsequent UI SF codes against.
This story has **no user-visible value** for Fran's father today; the
user-visible payoff arrives in Phase 1 when `LauncherScreen` consumes these
tokens.

Spec ref: `docs/curro-spec-v1.0.md` §3 (the user — reduced fine motor control,
deteriorated vision, slow UI learning curve, "the app must feel the same every
day") + §11 (the visual surfaces — big clock, ≥ 40 %-screen mic button, ≥ 96 dp
SÍ/NO/tile targets, high contrast, no fussy animation). Master-plan ref:
SF-0.4.

## Scope

### In Scope

- **`app/src/main/java/com/curro/app/presentation/theme/Color.kt`** — light + dark
  `ColorScheme` instances backed by `lightColorScheme(...)` / `darkColorScheme(...)`.
  Each M3 role (`primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`,
  `secondary`, `onSecondary`, `tertiary`, `onTertiary`, `background`, `onBackground`,
  `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `error`, `onError`,
  `outline`, `outlineVariant`) is **explicitly assigned a placeholder hex** —
  `lightColorScheme()` defaults are Material's purple-on-white which Curro
  inherits today through the stub; here we replace that with values **chosen by
  the architect (Q4) to be either (a) a deliberately-neutral greyscale ramp on
  near-white / near-black, instantly readable as a placeholder so nobody mistakes
  it for the brand, or (b) a single muted neutral tint** — whichever the architect
  picks, the **non-negotiable acceptance condition** is that the placeholder
  satisfies **≥ 4.5:1 contrast** for `onSurface` over `surface` (Light **and**
  Dark) and **≥ 3:1** for the large-text/UI pairings (`primary` over `surface`,
  `error` over `surface`). The KDoc at the top of `Color.kt` says clearly:
  "Placeholder values — replaced by the real Curro palette in US-005 (SF-0.7)
  without touching any composable. The senior-first contrast floor applies even
  to placeholders." Brand-tinted accent colours, "listening tint" (the
  light-blue overlay tint while `listening` — `brand-design`'s `ListeningTint`),
  any custom colour outside the M3 role table — all of these are **out of scope**;
  they land in US-005.

- **`app/src/main/java/com/curro/app/presentation/theme/Type.kt`** —
  `CurroTypography: Typography` built via Material-3's `Typography(...)`
  constructor with **every M3 role explicitly assigned a `TextStyle`**:
  `displayLarge`, `displayMedium`, `displaySmall`, `headlineLarge`,
  `headlineMedium`, `headlineSmall`, `titleLarge`, `titleMedium`, `titleSmall`,
  `bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge`, `labelMedium`,
  `labelSmall`. The **size floor** for each role is documented inline in the
  file's KDoc and enforced by the placeholder picks below — concrete brand
  numbers arrive in US-005, but the *floor* is the senior-first contract and is
  what this SF locks in:

  | Role | Floor (US-004 placeholder) | Curro usage (`brand-design`, `material-design`) |
  |---|---|---|
  | `displayLarge` | ≥ 64 sp, ExtraBold/Bold | launcher clock |
  | `displayMedium` | ≥ 48 sp, Bold | overlay headlines ("Te escucho…") |
  | `displaySmall` | ≥ 40 sp, Bold | (less used) |
  | `headlineLarge` | ≥ 32 sp, Bold | screen titles, sender names on cards |
  | `headlineMedium` | ≥ 28 sp, SemiBold | card titles, list-row primary text |
  | `headlineSmall` | ≥ 24 sp, SemiBold | (less used) |
  | `titleLarge` | ≥ 22 sp, SemiBold | sub-sections |
  | `titleMedium` | ≥ 20 sp, Medium | sub-sections |
  | `titleSmall` | ≥ 18 sp, Medium | (rare) |
  | `bodyLarge` | **≥ 20 sp**, Regular | **body text — message bodies, prompts ("body that reads like a headline")** |
  | `bodyMedium` | ≥ 18 sp, Regular | secondary text |
  | `bodySmall` | ≥ 16 sp, Regular | the floor below which Curro should almost never go |
  | `labelLarge` | ≥ 18 sp, SemiBold | the *only* "small" role — button / chip text |
  | `labelMedium` | ≥ 16 sp, SemiBold | (rare) |
  | `labelSmall` | ≥ 14 sp, Medium | (avoid) |

  Compare against M3 defaults to feel the gap: M3 `bodyLarge` is 16 sp; Curro's
  floor is **20 sp** (+25%); M3 `displayLarge` is 57 sp, Curro's floor is **64 sp**.
  The numbers picked here are *not* the brand numbers — US-005 may take them
  higher (and almost certainly will) but **never lower**. The font family is the
  **system default** (`FontFamily.Default`) in US-004; US-005 may swap in a
  bundled font.

  The KDoc on `Type.kt` says: "Placeholder values — replaced by the real Curro
  type scale in US-005 (SF-0.7) without touching any composable. The
  senior-first floor (per-role minimum sp) documented in the table above is the
  contract — US-005 may exceed it; it may not undercut it."

- **`app/src/main/java/com/curro/app/presentation/theme/Shape.kt`** — a
  `CurroShapes: Shapes` instance via M3's `Shapes(extraSmall=…, small=…,
  medium=…, large=…, extraLarge=…)` constructor with `RoundedCornerShape`
  placeholders (suggested floors, architect may pick larger):
  - `extraSmall = RoundedCornerShape(8.dp)`
  - `small = RoundedCornerShape(12.dp)`
  - `medium = RoundedCornerShape(16.dp)`
  - `large = RoundedCornerShape(24.dp)`
  - `extraLarge = RoundedCornerShape(32.dp)` *(use case: large surfaces, the
    mic button background — once SF-1.3 lands)*

  Composables read shapes via `MaterialTheme.shapes.medium` (etc.) **or**
  directly via `CurroShapes.medium` — both must work because `CurroTheme` wires
  `CurroShapes` into `MaterialTheme.shapes`. The KDoc says: "Placeholder values
  — replaced by the real Curro radii in US-005 (SF-0.7)."

- **`app/src/main/java/com/curro/app/presentation/theme/Spacing.kt`** — Curro's
  spacing scale. **The architect picks Q1 (plain `object` vs `CompositionLocal`)
  before the developer commits a shape**, but the *values* are the same either
  way:

  | Token | dp | Use |
  |---|---|---|
  | `none` | `0.dp` | the explicit-zero case for `Modifier.padding(...)` defaults |
  | `xs` | `4.dp` | tight inner gaps within a component (e.g. icon ↔ label inside a button) |
  | `s` | `8.dp` | inner padding, tight gaps within a card |
  | `m` | `16.dp` | standard padding (Material's "16-dp grid" baseline) |
  | `l` | `24.dp` | section spacing |
  | `xl` | `32.dp` | screen-level padding, gaps **between** tap targets (senior-first generosity) |
  | `xxl` | `48.dp` | extra-generous gap between adjacent big buttons / app tiles |

  **The Q1 choice — plain `object CurroSpacing` (top-level, immediate access)
  vs `LocalCurroSpacing` (`CompositionLocal`-backed, accessed via
  `LocalCurroSpacing.current.m`)** — is in *Open Questions* below. PM
  recommendation: plain `object` (industry precedent for things that don't vary
  by theme variant; spacing doesn't shift between light/dark/large-font). The
  architect may overrule and pick `CompositionLocal`; if so, `CurroTheme` wires
  the provider via `CompositionLocalProvider(LocalCurroSpacing provides ...)`.

  Composables read spacing via `CurroSpacing.m` (or `LocalCurroSpacing.current.m`
  if Q1 lands CL-backed) — **never** raw `.dp` literals.

- **`app/src/main/java/com/curro/app/presentation/theme/Dimens.kt`** — the
  **senior-first dimension contract** as named constants, separate from
  `Spacing` because these are *invariants*, not a scale:

  ```kotlin
  /**
   * Senior-first dimension contract for Curro (spec §3, §11).
   *
   * These are not a design "scale" — they are mechanical invariants that every
   * touch target / mic-button / card / list-row in the app must respect. The
   * brand may move colours and type around (US-005); these numbers do not move.
   *
   * Rationale: the only validated user (Fran's father) has reduced fine motor
   * control and deteriorated-but-functional vision. Material 3's 48 dp tap
   * target is the FLOOR for normal apps; Curro's user needs ≥ 96 dp.
   */
  object Dimens {
      /** Minimum tap-target size — twice Material 3's 48 dp. Spec §3, §11. */
      val MinTapTarget: Dp = 96.dp

      /** Main launcher mic button: ≥ 40 % of the screen height. Spec §11. */
      val MicButtonMinHeightFraction: Float = 0.40f

      /** Standard big primary button height — alias of [MinTapTarget] for readability. */
      val BigButtonHeight: Dp = 96.dp

      /** Big card / list-row minimum height. */
      val BigRowHeight: Dp = 96.dp

      /** Glyph size inside a 96 dp IconButton — the senior-first "icon you can see". */
      val LargeIconSize: Dp = 48.dp
  }
  ```

  Every component shipped by this SF references `Dimens.*` (no inline `96.dp`
  literal). The Q3 architect call (do these belong on `Dimens` as constants, or
  as a `TextStyle`/`Modifier`-builder extension on `Typography` — e.g.
  `Modifier.bigTapTarget()`) sits in *Open Questions*; PM recommendation: a
  plain `object Dimens` (more discoverable, no Compose-coupling, mirrors the
  shape of `Spacing`/`Shape`).

- **`app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt`** —
  **replaces** the US-001 stub. Final shape (modulo Q1's CompositionLocal pick):

  ```kotlin
  package com.curro.app.presentation.theme

  import androidx.compose.foundation.isSystemInDarkTheme
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.runtime.Composable
  // Deliberately NOT imported (Q5):
  //   - dynamicLightColorScheme
  //   - dynamicDarkColorScheme
  // Dynamic colour is disabled by design — see KDoc below.

  /**
   * Curro's app-wide theme.
   *
   * Wraps Material 3 with [CurroColorScheme] (light + dark, fixed/placeholder palette
   * — real values arrive in US-005/SF-0.7), [CurroTypography] (body-as-headline scale —
   * senior-first contract; placeholder sizes here), [CurroShapes] (rounded
   * corners), and (if Q1 lands CompositionLocal-backed) the [LocalCurroSpacing]
   * provider.
   *
   * **Dynamic colour is disabled by design.** Curro is a senior-first launcher
   * whose only validated user is Fran's father (spec §3): predictability ("feels
   * the same every day", spec §11) and the high-contrast floor outrank
   * user-wallpaper personalisation. The dynamic-colour APIs are deliberately
   * not imported in this file so accidentally re-enabling dynamic colour
   * requires a conscious code change, not a parameter flip.
   *
   * The dark/light branch follows the system [isSystemInDarkTheme] — Curro
   * does not own a per-user theme toggle in MVP (the config menu may add one
   * in Phase 8 if Fran's father has a preference; today, follow the system).
   */
  @Composable
  fun CurroTheme(
      darkTheme: Boolean = isSystemInDarkTheme(),
      content: @Composable () -> Unit,
  ) {
      val colorScheme = if (darkTheme) DarkColors else LightColors

      MaterialTheme(
          colorScheme = colorScheme,
          typography = CurroTypography,
          shapes = CurroShapes,
          content = content,
          // If Q1 resolves CompositionLocal-backed:
          //   wrap [content] in CompositionLocalProvider(LocalCurroSpacing provides CurroSpacing)
      )
  }
  ```

  **Q5 (architect): structural enforcement of `dynamicColor = false`**.
  Three options, listed easiest-to-hardest:
  1. KDoc note + missing parameter (above).
  2. **Plus** the deliberately-missing import (above).
  3. **Plus** a detekt custom rule "no `dynamicLightColorScheme` / `dynamicDarkColorScheme`
     anywhere" — but that's exactly the kind of custom rule US-003 punted, so
     **not in this SF**.

  PM recommendation: option 2 (KDoc + missing import + missing parameter).
  Anything stronger blocks on the deferred custom-rules tooling.

- **`app/src/main/java/com/curro/app/presentation/common/BigPrimaryButton.kt`** —
  the canonical big-CTA composable. Final shape:

  ```kotlin
  /**
   * Curro's primary call-to-action button — the brick for SÍ, "Más apps",
   * "Hazme tu pantalla de inicio", and every overlay primary action.
   *
   * Senior-first contract: ≥ [Dimens.MinTapTarget] tall, [haptic feedback] on
   * press (this user gets reduced fine motor control — the haptic confirms
   * the press registered before the screen updates), high-contrast primary /
   * onPrimary, label rendered at a typography role *big enough to read at
   * arm's length*.
   *
   * @param contentDescription overrides [text] for the semantics tree (use
   * when the button's visible label is symbolic, e.g. an emoji ✅ for "Sí",
   * and the screen reader / spoken-action layer needs the word). Default
   * null → [text] is used.
   */
  @Composable
  fun BigPrimaryButton(
      text: String,
      onClick: () -> Unit,
      modifier: Modifier = Modifier,
      enabled: Boolean = true,
      contentDescription: String? = null,
  ) {
      val haptic = LocalHapticFeedback.current
      Button(
          onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onClick()
          },
          enabled = enabled,
          modifier = modifier
              .fillMaxWidth()
              .heightIn(min = Dimens.MinTapTarget)
              .semantics { contentDescription?.let { this.contentDescription = it } },
          shape = MaterialTheme.shapes.medium,
          colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          // No content padding override — the Button default + the heightIn(min = MinTapTarget)
          // already give the big visual; the typography role does the rest.
      ) {
          Text(
              text = text,
              style = MaterialTheme.typography.titleLarge, // ≥ 22 sp — comfortable button label
          )
      }
  }
  ```

  Required `@Preview`s (in the same file, marked `private`):
  - `BigPrimaryButtonLightPreview` — `widthDp = 412, heightDp = 200`
  - `BigPrimaryButtonDarkPreview` — `uiMode = UI_MODE_NIGHT_YES`
  - `BigPrimaryButtonLargeFontPreview` — `fontScale = 1.5f`
  - `BigPrimaryButtonHugeFontPreview` — `fontScale = 2.0f`

  Each preview wraps the button in `CurroTheme { Surface { BigPrimaryButton(...) } }`.
  The `fontScale = 2.0f` preview is the senior-first regression test (must not
  clip, must remain ≥ 96 dp tall).

- **`app/src/main/java/com/curro/app/presentation/common/BigCard.kt`** — the
  canonical big-card composable. Final shape:

  ```kotlin
  /**
   * Curro's big card surface — the brick for WhatsApp message cards,
   * contact-picker rows, config-menu rows, and any "block of large readable
   * content" the app needs to show.
   *
   * When [onClick] is non-null, the card becomes a clickable surface ≥
   * [Dimens.MinTapTarget] tall with haptic feedback — useful for picker rows,
   * tappable list items, big actionable cards.
   *
   * Content slot is a [ColumnScope]: callers stack [Text] / [Icon] / [Image]
   * inside, separating with [Spacer]s sized via [CurroSpacing].
   */
  @Composable
  fun BigCard(
      modifier: Modifier = Modifier,
      onClick: (() -> Unit)? = null,
      content: @Composable ColumnScope.() -> Unit,
  ) {
      val haptic = LocalHapticFeedback.current
      val clickableMod = if (onClick != null) {
          Modifier
              .heightIn(min = Dimens.BigRowHeight)
              .clickable {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  onClick()
              }
      } else {
          Modifier
      }

      Card(
          modifier = modifier
              .fillMaxWidth()
              .then(clickableMod),
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant,
              contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // raw dp permitted in theme module ONLY
      ) {
          Column(modifier = Modifier.padding(CurroSpacing.l)) {
              content()
          }
      }
  }
  ```

  Required `@Preview`s, all four (light / dark / 1.5× / 2.0×), with two sample
  contents — one with `onClick = null` (read-only), one with `onClick = { }`
  (tappable picker-row style) — so the developer eyeballs both shapes.

- **Detekt teach-around — the theme-module exclude.** Add to
  `config/detekt/detekt.yml`:

  ```yaml
  style:
    MagicNumber:
      active: true  # kept active for the app at large
      excludes:
        - '**/test/**'
        - '**/androidTest/**'
        # … existing exclusions …
        - '**/presentation/theme/**'  # US-004: the theme module is THE one place
                                       # raw .sp / .dp / Color(0xFF…) literals
                                       # legitimately live — every other path
                                       # in app/src/main/ still triggers the rule.
  ```

  Plus any other detekt rule that fires on the theme module's literals — the
  developer runs `./gradlew detekt` once mid-SF, captures any rule names that
  fire, and adds the same `**/presentation/theme/**` exclude to those rules
  (likely candidates: none today on the default rule set — `MagicNumber` is the
  main one — but the developer verifies). The exclude pattern is **exact**
  (`**/presentation/theme/**`) so it does *not* spill into
  `presentation/common/`, `presentation/launcher/`, or anywhere else; the rule
  must continue to fire on `BigPrimaryButton.kt`, which is in
  `presentation/common/`. (`BigPrimaryButton` and `BigCard` therefore use
  `Dimens.MinTapTarget` and `CurroSpacing.l` — they do **not** write
  `96.dp` / `24.dp` inline. The only inline raw dp permitted in `presentation/common/`
  is `2.dp` for `CardDefaults.cardElevation(defaultElevation = 2.dp)` *if* the
  developer judges that adding `Dimens.CardElevation` is over-engineering for
  one number; if `MagicNumber` flags it, prefer adding `Dimens.CardElevation`
  over widening the exclude.)

- **`MainActivity.kt`** — **NOT MODIFIED**. The call site
  `CurroTheme { Surface(Modifier.fillMaxSize()) { Text(stringResource(R.string.app_name)) } }`
  stays as-is; only the theme's appearance changes. This is the load-bearing
  invariant of the brief: every later SF that adds a new screen reuses the
  same `CurroTheme { ... }` wrapper without parameter change. The developer
  verifies post-SF: `git diff app/src/main/java/com/curro/app/MainActivity.kt`
  produces no diff.

- **`app/src/main/res/values/themes.xml`** — review (not necessarily edit).
  Current state: `<style name="Theme.Curro" parent="Theme.AppCompat.DayNight.NoActionBar" />`
  is a thin Android-XML theme that exists so the manifest's
  `android:theme="@style/Theme.Curro"` reference resolves; the actual design
  system lives in Compose via `CurroTheme { }`. The developer:
  1. Reads `Color.kt`'s `LightColors.background` placeholder hex.
  2. Compares it to what `Theme.AppCompat.DayNight.NoActionBar` paints on the
     window background during cold launch (the "splash" frame before Compose
     paints).
  3. If they differ noticeably (a contrast flash visible on the Redmi 15),
     either:
     - **Option A (preferred for US-004):** override
       `android:windowBackground` in `themes.xml` to match
       `LightColors.background`'s placeholder hex.
     - **Option B:** accept the flash as US-005 technical debt (US-005 picks
       the real background colour anyway; both `themes.xml` and `Color.kt`
       move together then).
  4. Records the choice in the brief's Performance Considerations and PR body.

  **No new `themes.xml` resources, no `Theme.Curro.Light` / `Theme.Curro.Dark`
  variants** — Compose handles dark/light via `isSystemInDarkTheme()`; the XML
  theme is the splash frame only.

### Out of Scope (each is its own later SF — be ruthless)

- **The real Curro brand palette / type values / spacing scale / radii** — the
  hexes in `Color.kt`, the sp numbers in `Type.kt`, the dp numbers in
  `Spacing.kt` / `Shape.kt` are **placeholders** and explicitly labelled as
  such. Real brand fill-in is **US-005 (SF-0.7, promoted)**. US-005 swaps the
  values; **no composable consumer should need to change** (the contract here
  is: real brand fill-in must be a values-only patch).

- **`BigYesNoRow`** — the SÍ/NO confirmation buttons row, ≥ 96 dp each, wide
  gap, both fire the right events, both labelled. **Deferred to SF-0.5.**
  Rationale: it's a 10-line composition of two `BigPrimaryButton`s plus a `Row`
  with `Arrangement.spacedBy(CurroSpacing.xl)`. Including it in US-004 risks
  bleeding into "what's the visual treatment for SÍ vs NO?" (filled vs outlined?
  primary vs secondary?), which is a brand call (US-005), not a theme-scaffold
  call. SF-0.5 lands it once `BigPrimaryButton` exists *and* US-005 has decided
  whether NO is `secondary` or `outlinedButton`. Same applies to **`BigListRow`**
  (the icon + label row used by app tiles, picker rows, "Más apps" list) —
  deferred to SF-0.5, because the icon-size contract belongs in `Dimens` (here)
  but the layout (icon-left vs icon-top, photo-vs-glyph branch, two-line vs
  one-line label) is best decided alongside the first real consumer (SF-1.4
  `AppTileGrid`).

- **The launcher home, the assistant overlays, the config menu** — Phase 1 (
  launcher home: SF-1.x), Phase 5 (assistant overlays: SF-5.x), Phase 8 (
  config menu: SF-8.x). US-004 ships the *tokens and bricks*; it ships no
  *surface*.

- **The No-Double-Padding custom detekt rule** — already punted in US-003. Not
  reopened here.

- **A "no raw `Color(0xFF…)` / `.sp` / `.dp` literal outside theme" custom
  detekt rule** — would close the loop the brief is currently leaving open
  (a developer in `presentation/launcher/` could still write `Color(0xFF…)`
  and ktlint+detekt wouldn't catch it). **Deferred** alongside the
  No-Double-Padding rule — same `tools/detekt-rules/` module, same shape. The
  brief documents the *intent* (US-003's "Implementation Notes" → "the
  custom-rule punt" sketches the module shape; US-004 adds the rule to the
  punt list).

- **Brand assets** (logo, app icon, launcher icon, the mic glyph) — US-005 /
  a later asset-only SF. Today the app uses the default Android Studio
  adaptive-icon set; US-005 may keep, replace, or move them.

- **A per-user font-scale boost on top of the system fontScale** — i.e. a
  config-menu slider that says "even bigger than the system already gives".
  Punt to **SF-0.5 (shared big components final shape)** or **SF-8.x (config
  menu)**. US-004 *enables* it — `CurroTypography` respects the system
  fontScale through Material's standard machinery — but does not *implement*
  the extra knob.

- **A custom font family** (a bundled `Roboto`/`Inter`/etc. `FontFamily`) —
  US-005. US-004 uses `FontFamily.Default` (the system font) for every role.

- **Explicit theming for `selected` / `disabled` / `error` UI states beyond
  what the M3 `ColorScheme` already encodes** — US-005 may tune them; US-004
  uses the defaults `lightColorScheme(...)` / `darkColorScheme(...)` provide.

- **Animation / motion specs** — Curro is "no fussy animation" (spec §11). The
  theme module does not own a `MotionScheme`; the few transitions that exist
  (e.g. the launcher → overlay paint) live with the surfaces that use them
  (Phase 1+).

## User Flows

US-004 has **no end-user flow**. It is developer-facing only — the only
"users" are a Curro developer running a Compose preview / Gradle locally and
the CI runner on GitHub Actions.

### Flow 1: A developer adds a new composable in a later SF

(Demonstrates *why* US-004 is the precondition for every UI SF.)

1. SF-1.2 developer creates `LauncherScreen.kt` with `ClockBlock`.
2. They write `Text(time, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)`.
3. They wrap the screen in `CurroTheme { ... }` — same wrapper `MainActivity` uses today.
4. Light/dark/largeFont previews all render correctly because `CurroTheme` is wired end-to-end.
5. `./gradlew ktlintCheck detekt` is green — no raw `Color(0xFF…)` / `.sp` / `.dp` literal anywhere in `LauncherScreen.kt` (semantic tokens only).
6. When US-005 later swaps the placeholder palette → the real brand, **`LauncherScreen.kt` does not change.**

### Flow 2: Cold-launch splash → Compose paint

1. The user taps the Curro icon.
2. Android paints the window background using `Theme.Curro` (the XML theme) for ~50–150 ms while the Compose tree is being measured.
3. `MainActivity.onCreate` runs → `setContent { CurroTheme { ... } }` → first frame is painted by Compose with `LightColors.background` (or `DarkColors.background` if `isSystemInDarkTheme()`).
4. **The transition between (2) and (3) is invisible to the user** because `themes.xml`'s `android:windowBackground` matches `LightColors.background`'s placeholder hex (per the *In Scope* `themes.xml` review item).

### Flow 3: System-wide dark-mode toggle

1. User flips system dark mode (Settings → Display → Dark theme, or
   `adb shell cmd uimode night yes`).
2. `MainActivity` is re-created (`uiMode` config change) — or, if Curro adds
   `android:configChanges="uiMode"` later, `isSystemInDarkTheme()` simply
   recomposes.
3. `CurroTheme` reads the new value, swaps `LightColors` → `DarkColors`.
4. Every visible composable repaints with the dark palette; contrast still
   holds (≥ 4.5:1 body) because the developer verified both schemes in the
   acceptance pass.

### Flow 4: System fontScale set to 2.0

1. User (or accessibility tester) flips `Settings → Display → Font size → Largest`.
2. `MainActivity` recomposes; every `Text` rendered through `MaterialTheme.typography.*` scales by 2.0× the sp value in `CurroTypography`.
3. `BigPrimaryButton` remains ≥ 96 dp tall (the `heightIn(min = Dimens.MinTapTarget)` is in dp, which is independent of `fontScale`); its **label** grows; the button height grows further if the label needs more vertical space because of `wrapContentHeight()` semantics inside `Button`.
4. No clipping, no layout collapse, no horizontal scroll — verified by the `fontScale = 2.0f` `@Preview`.

## Function-catalog Impact

**No catalog change.** SF-0.4 ships no handler, no `CatalogFunction`, no
FunctionGemma prompt change, no JSON-schema entry. `domain/catalog/` stays
empty (kept alive by its `.gitkeep`).

Cross-reference: the `function-catalog` skill is untouched until SF-3.x; the
first handler binding lands in SF-4.1 (`tell_time`).

## FSM States Touched

**None.** SF-0.4 ships no assistant code — no `AssistantStateMachine`, no
`AssistantCoordinator`, no `ConfidencePolicy`, no overlays. The `assistant/`
package stays empty.

**Indirect**: every overlay later wired by Phase 5 (the `listening` /
`processing` / `confirming` / `executing` / `error_recovery` UIs) consumes the
tokens shipped here — `ListeningOverlay` uses `MaterialTheme.colorScheme.surface`
+ Curro's eventual `ListeningTint` (US-005), `ConfirmationOverlay` uses
`BigPrimaryButton` + `BigYesNoRow` (US-004 + SF-0.5), `ProcessingOverlay` uses a
non-animated indicator drawn with `MaterialTheme.colorScheme.primary`. **None of
those land in US-004; the theme just enables them.**

Cross-reference: `voice-interaction` (the FSM is untouched), `launcher-ui`
(the surfaces are untouched), `compose-patterns` (the state-driven-overlay
pattern is documented but not yet exercised).

## Android System Integrations & Permissions

**No system integrations**, **no runtime permissions** declared, **no manifest
change**. The manifest stays exactly as US-001 left it:

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| *(none in this SF)* | Each permission lands with the SF that needs it (spec §10) | N/A | N/A |

The manifest's `<application android:theme="@style/Theme.Curro" ...>`
attribute already points to the XML theme — unchanged. The Compose
`CurroTheme` is parallel infrastructure, not a manifest concern.

## On-device-model Impact

**No model impact.** US-004 ships no prompt change, no model loading, no
inference path, no `data/ml/` code. FunctionGemma / Gemma 3n are not touched.

Cross-reference: `on-device-llm` (untouched).

## Android Specification

### Screens and Composables

Curro has few "screens" — and US-004 ships **zero**. What it ships are the
*tokens* and *bricks* every Phase 1+ screen will compose itself from.

**File layout** (all under `app/src/main/java/com/curro/app/`):

```
presentation/theme/
├── Color.kt              # LightColors, DarkColors (M3 ColorScheme x 2)
├── Type.kt               # CurroTypography (M3 Typography)
├── Shape.kt              # CurroShapes (M3 Shapes)
├── Spacing.kt            # CurroSpacing (object — Q1 may move to CompositionLocal)
├── Dimens.kt             # Dimens (object — senior-first dp/Float invariants)
└── CurroTheme.kt         # @Composable fun CurroTheme(...) — replaces the US-001 stub

presentation/common/
├── BigPrimaryButton.kt   # the canonical big-CTA
└── BigCard.kt            # the canonical big-card surface
```

**No new ViewModel, no new screen, no new route.** `MainActivity.kt` is
unchanged (load-bearing invariant — see *Acceptance Criteria*).

### ViewModels and State Management

No ViewModel changes. The theme module is pure Compose; no `StateFlow`,
no `@HiltViewModel`. The two new composables (`BigPrimaryButton`, `BigCard`)
are **stateless** — they receive state (`text`, `enabled`, `onClick`,
`content`) and emit events (the `onClick` lambda).

### Navigation Routes

**No new routes.** `MainActivity` still renders a single Compose root; the
`CurroNavHost` shell lands in SF-0.6.

### Hilt Modules

**No new Hilt module.** Theme is pure Compose; not DI. The architect
explicitly notes (see *Open Questions → Q7*) that the theme deliberately does
not live behind a `domain/repository/` interface — there is no scenario in
which "swap the theme at runtime via DI" is a useful capability for this
single-user prototype.

### Composables by Feature (checklist)

- [ ] `Color.kt` → `LightColors`, `DarkColors` (placeholder, contrast-floor-compliant)
- [ ] `Type.kt` → `CurroTypography` (15 M3 roles, each ≥ the per-role floor in the *Scope* table)
- [ ] `Shape.kt` → `CurroShapes` (5 M3 shape roles, placeholder radii)
- [ ] `Spacing.kt` → `CurroSpacing` (7 named tokens; Q1 fixes the access shape)
- [ ] `Dimens.kt` → `Dimens.MinTapTarget`, `MicButtonMinHeightFraction`, `BigButtonHeight`, `BigRowHeight`, `LargeIconSize`
- [ ] `CurroTheme.kt` → real `@Composable fun CurroTheme(darkTheme, content)` (no `dynamicColor` parameter; deliberately missing dynamic-color imports; KDoc-documented)
- [ ] `BigPrimaryButton.kt` (`presentation/common/`) → composable + 4 `@Preview`s
- [ ] `BigCard.kt` (`presentation/common/`) → composable + 4 × 2 `@Preview`s (light/dark/1.5×/2.0× × {read-only, tappable})

### Material Design Components used

- `MaterialTheme` (entry point in `CurroTheme.kt`).
- `Button` (filled, primary) — wrapped in `BigPrimaryButton`.
- `Card` (filled) — wrapped in `BigCard`.
- `Typography(...)`, `Shapes(...)`, `lightColorScheme(...)`, `darkColorScheme(...)` constructors.
- `LocalHapticFeedback` + `HapticFeedbackType.LongPress` for the senior-friendly press confirmation.

**Not used (deliberately)** in this SF: `TopAppBar`, `Scaffold`,
`NavigationBar`, `FloatingActionButton`, `BottomSheet`, `Switch`, `Slider`,
`Checkbox`, `RadioButton`, dynamic-colour APIs.

## Acceptance Criteria

Concrete, checkable; copies and expands the PRD AC list with the developer-facing
specifics:

- [ ] **`./gradlew assembleDebug` succeeds**, no new deprecation warnings introduced by US-004, no resource conflicts; the installed APK launches and `MainActivity` renders the text "Curro" (no regression vs US-003 — the call site is untouched, only the visual treatment of `CurroTheme` is different).
- [ ] **`./gradlew ktlintCheck detekt` is green** with the new files; the theme-module `MagicNumber` exclude (`**/presentation/theme/**`) is added to `config/detekt/detekt.yml` with a one-line `# US-004: …` comment explaining why; **the same rule still fires on any raw `96.dp` / `0xFF` literal in `presentation/common/`** (verified by the developer with a deliberately-introduced violation in `BigPrimaryButton.kt`, captured-failing-then-reverted — same pattern US-003 used for `!!`).
- [ ] **`./gradlew testDebugUnitTest` is green** — no new JVM tests in US-004 (theme is pure Compose; UI tests live in `androidTest/`); US-001's `SmokeTest` still passes (regression guard).
- [ ] **Both `LightColors` and `DarkColors`** are explicitly assigned across all M3 roles (no relying on `lightColorScheme()` defaults beyond the developer's deliberate placeholder picks); `adb shell cmd uimode night yes` and `adb shell cmd uimode night no` flip the rendered theme correctly on the Redmi 15 / emulator without any composable change; the developer pastes the on-device screenshots of "Curro" in both modes into the PR description.
- [ ] **Contrast floor verified** for at least these pairings (computed ratios pasted into the PR description, per the WCAG formula):
  - `onSurface` ÷ `surface` ≥ **4.5:1** (Light)
  - `onSurface` ÷ `surface` ≥ **4.5:1** (Dark)
  - `onPrimary` ÷ `primary` ≥ **4.5:1** (Light)
  - `onPrimary` ÷ `primary` ≥ **4.5:1** (Dark)
  - `primary` ÷ `surface` ≥ **3:1** (UI element on background)
  - `error` ÷ `surface` ≥ **3:1**
  - If any pairing fails, the placeholder hex is adjusted until it passes — the senior contrast floor applies even to placeholders.
- [ ] **`dynamicColor` is structurally disabled** — `CurroTheme.kt` does not declare a `dynamicColor` parameter, does not import `dynamicLightColorScheme` / `dynamicDarkColorScheme`, and its KDoc carries the verbatim statement "Dynamic color disabled by design — predictability ('feels the same every day', spec §11) and the senior contrast floor outrank user-wallpaper personalisation." `grep -rn "dynamicLight\\|dynamicDark" app/src/main` returns zero matches.
- [ ] **`CurroTypography` has every M3 role assigned**; `grep -c "set\\|=" app/src/main/java/com/curro/app/presentation/theme/Type.kt` shows 15 explicit `TextStyle` assignments; each `TextStyle` carries at least `fontSize` and `fontWeight` (M3's lineHeight/letterSpacing accept the defaults Material picks for the slot — adjusting those is US-005's call). The size floor table from *Scope* is reproduced as KDoc at the top of the file so it survives independent of this brief.
- [ ] **`CurroSpacing` is reachable** from any composable via the syntax the architect picks at Q1: either `CurroSpacing.m` (plain object) or `LocalCurroSpacing.current.m` (CompositionLocal). Both `BigPrimaryButton` and `BigCard` use this syntax for every dp value they need (e.g. `BigCard`'s inner `Column(Modifier.padding(CurroSpacing.l))`).
- [ ] **`Dimens.MinTapTarget = 96.dp`** is the only place `96.dp` is written in the entire codebase outside the theme module; `BigPrimaryButton`'s `Modifier.heightIn(min = Dimens.MinTapTarget)` is the canonical consumer; `BigCard`'s clickable-min-height uses `Dimens.BigRowHeight`. `grep -rn "96\\.dp" app/src/main/java | grep -v "presentation/theme"` returns zero matches.
- [ ] **`Dimens.MicButtonMinHeightFraction = 0.40f`** is declared (consumer is SF-1.3 `MicButton`, not this SF — but the slot must exist so SF-1.3 inherits it).
- [ ] **`BigPrimaryButton` and `BigCard`** ship 4 `@Preview` variants each (light / dark / `fontScale = 1.5f` / `fontScale = 2.0f`); the developer eyeballs each in Android Studio's preview pane on a Pixel-class device frame (e.g. `widthDp = 412, heightDp = 200`), confirms (a) no clipping in `2.0f`, (b) ≥ 96 dp visible height of the button / clickable card row in `2.0f`, (c) dark mode renders without invisible text or invisible button. Screenshots pasted into the PR description (or, at minimum, the developer states in the PR "all 4×2 = 8 previews rendered successfully").
- [ ] **Haptic feedback fires on `BigPrimaryButton` tap**, verified on the Redmi 15 / emulator with vibration enabled.
- [ ] **The `TODO(SF-0.4)` comment** in the current `CurroTheme.kt` stub is removed; the stub body (`MaterialTheme(content = content)`) is replaced; `git diff` on `CurroTheme.kt` shows a net rewrite (small file → small-but-real file).
- [ ] **`MainActivity.kt` is byte-identical** to its US-003 state (no parameter additions to the `CurroTheme { ... }` call); `git diff app/src/main/java/com/curro/app/MainActivity.kt` returns no output.
- [ ] **`themes.xml` is reviewed** per the *In Scope* item; either `windowBackground` is aligned with `LightColors.background` (Option A) or the cold-launch flash is documented as US-005 technical debt (Option B). The developer's choice is in the PR description.
- [ ] **`verification-checklist`'s relevant sections pass**: Build Verification ✓; Lint and Code Quality ✓; Unit Tests ✓ (regression); UI Tests — not applicable (no instrumented test added); Code Quality Checks ✓ (no `!!`, no Hilt regression); Privacy & Permissions — N/A (no new permissions, no telemetry change); Accessibility Review — the senior-first invariants are now *codified in code* (`Dimens.MinTapTarget`, body-as-headline typography floors) rather than living only in skill docs; Dark Mode Testing ✓.
- [ ] **No new permissions, no new dependencies** (Material 3 + Compose were already on the catalog from US-001), **no new Gradle module**.

## Design Notes

**`brand-design` is the authority for the actual brand**; US-004 ships
*placeholders that satisfy the senior-first contract* and a *file skeleton*
that US-005 will fill in. The brief follows `brand-design`'s "Senior-first
constraints (non-negotiable)" section *as the contract*:

1. **Tap targets ≥ 96 dp** — codified in `Dimens.MinTapTarget`; every shared
   big component references it.
2. **Text well above Material defaults** — `CurroTypography` body roles start
   at 20 sp (M3 default: 16 sp); the per-role floor table in *Scope* is the
   contract.
3. **Respect AND amplify system fontScale** — Compose's standard `sp` units
   already respect `fontScale` linearly; US-004 *does not cap* it (no
   `Density` override), and the `@Preview(fontScale = 2.0f)` variants prove
   it survives. The "amplify on top of system" knob is **deferred** to SF-0.5
   / SF-8.x (out of scope here).
4. **Very high contrast** — verified in *Acceptance Criteria* for the body /
   primary / error pairings, light and dark.
5. **Colour is never the only signal** — `BigPrimaryButton` ships a `text`
   label (not just a colour); `BigCard`'s "clickable" affordance has a haptic
   plus the visual press state Material provides; no colour-only signalling
   is introduced.
6. **No fussy animation** — `BigPrimaryButton` does not animate beyond
   Material's standard ripple; `BigCard` does not animate; no `crossfade` /
   parallax / spinner-of-spinners anywhere.
7. **"It feels the same every day"** — `dynamicColor` is structurally
   disabled, so the palette doesn't shift with the wallpaper; no per-launch
   randomisation of any kind.
8. **Audio + visual together** — N/A in US-004 (no spoken interaction is
   wired); the contract is preserved for future SFs (no design decision here
   that would prevent Phase 2's TTS from being paired with every screen
   state).

**No raw values anywhere outside `presentation/theme/`** — read tokens via
`MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` / `CurroSpacing.*`
/ `MaterialTheme.shapes.*` / `Dimens.*`. The `presentation/theme/` module is
the *one* legitimate exception (codified by the detekt exclude on
`**/presentation/theme/**`).

## Senior-UX & Copy

**No new Spanish strings in this SF.** US-004 ships theme tokens and two
shared big components; no copy is rendered. The composables that *will*
render copy (the launcher home, the overlays, the message cards, the config
menu) land in Phase 1+; their copy is owned by `brand-design`'s `COPY.*`
table and fully fleshed out in **US-005 (SF-0.7)**.

The shared big components (`BigPrimaryButton`, `BigCard`) accept their
visible text as a parameter (e.g. `BigPrimaryButton(text = "Sí", ...)`); in
their `@Preview`s, the developer uses *English* placeholder text ("Primary
action", "Sample card content") *not* Spanish — because Spanish copy is
US-005's job and using it here risks a hard-coded Spanish string accidentally
escaping into real consumer code. The previews are devstand-ins; the
production consumers (Phase 1+) pass `stringResource(R.string.…)` in.

## Performance Considerations

- **Theme is pure-Compose state**; no Hilt graph, no `StateFlow`, no I/O. The
  `CurroTheme { ... }` wrapper is a constant-time composable that selects
  one of two `ColorScheme` instances and passes them to `MaterialTheme(...)`.
- **`isSystemInDarkTheme()`** is the only side-effect-ish call in
  `CurroTheme`; Compose handles its recomposition on `uiMode` config change
  for free.
- **`@Preview` cost** — 8+ previews land in `BigPrimaryButton.kt` + `BigCard.kt`.
  Each is recompose-only (no actual draw outside Android Studio's preview
  pane); zero runtime cost. The build cost is negligible (`@Preview` does
  not generate runtime code).
- **No animation, no transitions** — Material's standard ripple on `Button`
  is the only motion shipped; no `Crossfade`, no `AnimatedVisibility`, no
  `LaunchedEffect`-driven animation.
- **Memory**: two `ColorScheme` singletons (each ~30 `Color` objects), one
  `Typography` singleton (15 `TextStyle` instances), one `Shapes` singleton,
  one `Dimens` object, one `CurroSpacing` object. Total well under 10 KB of
  static state.
- **`themes.xml` cold-launch flash**: documented in *Scope* — the developer
  either aligns `windowBackground` with `LightColors.background` (Option A)
  or accepts the flash as US-005 debt (Option B). Either is fine for the
  prototype; the choice is recorded in the PR.

## Testing Requirements

US-004 ships **no instrumented tests, no unit tests beyond the regression
guard**. The testing for US-004 is:

- [ ] **`./gradlew testDebugUnitTest`** still passes — US-001's `SmokeTest`
      is unaffected (regression guard; not because US-004 adds tests).
- [ ] **`./gradlew assembleDebug`** still passes — the build itself is
      unaffected; only the theme's appearance has changed.
- [ ] **`./gradlew ktlintCheck detekt`** passes on a fresh clone — the
      `MagicNumber` exclude on `**/presentation/theme/**` is in place; the
      rule still fires elsewhere (proven by the deliberately-introduced
      violation in `BigPrimaryButton.kt` documented in AC).
- [ ] **`@Preview` rendering check** — the developer opens
      `BigPrimaryButton.kt` and `BigCard.kt` in Android Studio with the
      preview pane visible, confirms each of the 4 (button) and 4 × 2 (card)
      previews renders without IDE error and without visible clipping /
      contrast failure / collapse. Screenshots in the PR description.
- [ ] **Device dark-mode flip** — `adb shell cmd uimode night yes` and
      `adb shell cmd uimode night no` on a connected device / emulator; the
      "Curro" placeholder text remains readable in both modes (proves the
      `ColorScheme` flip works through `CurroTheme`).
- [ ] **Manual contrast check** — the developer computes WCAG ratios for the
      pairings listed in AC using the chosen placeholder hexes; pastes the
      numbers into the PR. (For US-004's placeholders, this is a sanity
      check; for US-005's real palette, it will be the gating verification.)
- [ ] **`verification-checklist` skill** — the relevant sections (Build, Lint,
      Unit Tests, Accessibility, Dark Mode) pass; Privacy & Permissions,
      On-device Model, Assistant FSM sections are N/A.

**Future test coverage (not in US-004)**:
- SF-0.5 lands the first UI test on `BigPrimaryButton` (verifying it's
  clickable, the haptic fires the right type, the label renders) and `BigCard`.
- SF-1.x lands accessibility-sweep tests on the launcher home that *consume*
  these tokens.
- US-005 lands real-brand contrast snapshot tests (no SDK required — just
  the WCAG formula over the `Color` values).

## Open Questions

The architect (`android-architect`) resolves Q1–Q5 below before
`/implement-feature US-004` runs. Each question has a PM recommendation; the
architect may overrule with documented rationale.

### Q1 — `CurroSpacing`: plain `object` vs `CompositionLocal`?

**The question.** Should `CurroSpacing.m` be a constant on a plain top-level
`object`, or accessed via `LocalCurroSpacing.current.m`?

**Trade-offs.**

| Option | Pros | Cons |
|---|---|---|
| Plain `object CurroSpacing` | Simpler; no provider wiring; no `LocalComposition*` import in every composable; mirrors `Dimens` shape; industry common for theme-invariant values | If we ever want to vary spacing by theme variant (e.g. denser-spacing dark mode), we have to refactor every consumer |
| `LocalCurroSpacing` (CompositionLocal) | Future-proof for theme-variant spacing; idiomatic when paired with a non-trivial `CurroTheme`; lets `CurroTheme` "own" spacing | Boilerplate at every consumer (`LocalCurroSpacing.current.m`); unnecessary for the actual Curro design (we don't intend to vary spacing by theme) |

**PM recommendation: plain `object CurroSpacing`.** Curro does not intend to
vary spacing by theme variant (dark/light spacing is the same; large-font
"spacing" is handled by `fontScale` scaling text dimensions, not the dp
literals themselves). The future-proofing benefit of `CompositionLocal` has
no concrete consumer in the master plan; the cost is at every consumer.

**Architect — to resolve.** If the architect picks `CompositionLocal`, the
brief absorbs the boilerplate (`CompositionLocalProvider(LocalCurroSpacing
provides CurroSpacing) { content() }` inside `CurroTheme`; every shared big
component reads `LocalCurroSpacing.current.l` instead of `CurroSpacing.l`).
**The decision is reversible** (the consumer surface in US-004 is tiny:
`BigPrimaryButton.kt`, `BigCard.kt`), so the cost of overrule is bounded.

### Q2 — `Dimens` object vs typography/modifier extension?

**The question.** Should `Dimens.MinTapTarget = 96.dp` live as a constant on a
plain `object`, or as a `Modifier` extension like `Modifier.bigTapTarget()`?

**Trade-offs.**

| Option | Pros | Cons |
|---|---|---|
| `object Dimens` (constants) | Discoverable; uniform with `Spacing` / `Shape`; trivially testable; no Compose-coupling | A consumer can still write `Modifier.heightIn(min = Dimens.MinTapTarget)` and forget the "min" — but that's idiomatic enough |
| `Modifier.bigTapTarget()` extension | Single-call ergonomics; harder to forget; expresses *intent* (this IS a tap target) | Less discoverable; couples a "constant" to a `Modifier` API; harder to use for non-`Modifier` consumers (e.g. `Dimens.MicButtonMinHeightFraction` is a `Float`, not a `Modifier`) |

**PM recommendation: `object Dimens`.** Mirrors `Spacing` / `Shape`. The
extension can be added later trivially as a thin wrapper.

**Architect — to resolve.**

### Q3 — Where does `MicButtonMinHeightFraction` live?

**The question.** `MicButtonMinHeightFraction = 0.40f` is a `Float`, not a
`Dp` — does it belong on `Dimens`, or somewhere else?

**Trade-offs.** A `Float` fraction next to `Dp` constants is mildly
inconsistent. Options:
1. Keep on `Dimens` (PM recommendation) — discoverability wins; `Dimens` is
   "senior-first dimensional invariants", which a fraction qualifies as.
2. New `object Fractions { val MicButtonMinHeight = 0.40f }` — pure but adds
   a third object to the theme module for one value.
3. Top-level `val MIC_BUTTON_MIN_HEIGHT_FRACTION = 0.40f` — old-school
   constant; cluttery if more land.

**PM recommendation: option 1 (keep on `Dimens`).**

**Architect — to resolve.**

### Q4 — Placeholder palette shape: neutral-grey vs muted-tint?

**The question.** Should `LightColors`/`DarkColors` use a deliberately-greyscale
palette ("clearly a placeholder; nobody will mistake it for the brand"), or a
single-muted-tint palette ("less raw; closer to what a typical Material app
looks like; risks looking 'good enough' and the brand fill-in (US-005) getting
deprioritised")?

**Trade-offs.**

| Option | Pros | Cons |
|---|---|---|
| Greyscale (`#000000` / `#1A1A1A` / `#FFFFFF` / shades) | Maximum contrast; clearly placeholder; impossible to mistake for the brand | Looks austere; an unguarded screenshot in flight (e.g. a Phase-1 demo) reads as "the app is greyscale" |
| Muted tint (e.g. teal-grey / slate-blue, low saturation) | Less raw; pleasanter; still satisfies contrast | Risks "looking finished" → US-005 deprioritised; risks accidentally setting brand expectation |

**PM recommendation: greyscale.** The placeholder's job is to enforce the
contract and not look like the brand. US-005 (SF-0.7) is a near-term
follow-up; the risk of "deprioritised" is low.

**Architect — to resolve.** If the architect picks muted-tint, the contrast
verification step in AC still applies — the placeholder's appearance must
not undercut the contrast floor.

### Q5 — How is `dynamicColor = false` *structurally* enforced?

**The question.** Three options stacked easiest-to-hardest:
1. KDoc note + missing `dynamicColor` parameter on `CurroTheme`.
2. **Plus** deliberately-missing imports for `dynamicLightColorScheme` /
   `dynamicDarkColorScheme` (so re-enabling dynamic colour requires an
   import + a code change, not a parameter flip).
3. **Plus** a custom detekt rule that bans the dynamic-colour API calls
   project-wide.

**PM recommendation: option 2.** Option 3 reopens the custom-rules tooling
US-003 explicitly punted (`tools/detekt-rules/`); option 1 alone is too easy
to override.

**Architect — to resolve.**

### Q6 — Are `BigPrimaryButton` and `BigCard` the right "first two"?

**The question.** US-004 ships **two** shared big components; SF-0.5 ships
the rest (`BigYesNoRow`, `BigListRow`). The PM's split rationale:
- `BigPrimaryButton` is mandatory — it's the brick for `BigYesNoRow` and for
  every primary CTA Phase 1 will land.
- `BigCard` is strongly preferred — it's the brick for the WhatsApp message
  cards (Phase 4), contact-picker rows (Phase 4), config-menu rows (Phase 8).

**Trade-offs of pulling more forward / pushing more back:**
- Pulling `BigYesNoRow` forward: 10 lines of code; risks bleeding into "filled
  vs outlined for NO" brand decision (US-005) without a clean answer here.
- Pulling `BigListRow` forward: heavier — icon + label + (optional photo) +
  the icon-size contract; better decided alongside the first real consumer
  (`AppTileGrid` in SF-1.4) so the contract isn't over-specified.
- Pushing `BigCard` back: leaves Phase 1+ developers without a `BigCard` for
  cards they'll need to compose; PM judgement says no, ship `BigCard` here.
- Pushing `BigPrimaryButton` back: pointless — it's the prerequisite for
  *everything*.

**PM recommendation: ship `BigPrimaryButton` + `BigCard`; defer
`BigYesNoRow` + `BigListRow` to SF-0.5.**

**Architect — to resolve.** If the architect pulls `BigYesNoRow` forward
(reasonable), it's two lines of additional code in `BigYesNoRow.kt`:

```kotlin
@Composable
fun BigYesNoRow(onYes: () -> Unit, onNo: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(CurroSpacing.xl)) {
        BigPrimaryButton("Sí", onYes, Modifier.weight(1f))
        BigPrimaryButton("No", onNo, Modifier.weight(1f))
    }
}
```

…plus 4 previews. The architect's call.

### Q7 — Does the theme module need a Hilt module / DI binding?

**The question.** Some apps wrap their theme behind a DI-provided
`ThemeProvider` so the theme can be swapped at runtime. Curro?

**PM recommendation: no.** No realistic scenario in which "swap theme at
runtime via DI" is useful for this single-user prototype. The theme is pure
Compose state; `CurroTheme { ... }` is the entry point; `MainActivity` is
the consumer; no DI needed.

**Architect — to confirm.** If the architect agrees (expected), the brief
needs no module addition.

## Implementation Notes

### Order of operations (developer-facing checklist)

Branch policy: the user explicitly requested **no new branch** for this SF —
work directly on `main`. PM verified before invoking the brief. If a future
revision wants a branch, follow `git-workflow`'s convention
(`feature/US-004-curro-theme`); for now, single commit on `main`.

1. **Architect pass first.** Do not skip. Run `android-architect` against
   *Open Questions Q1–Q7*; capture the resolution of each as a numbered
   `**Q# — Resolved: …**` block in this brief (same pattern US-002 used).
   Estimated time: ~30 min for someone fluent in Compose theming.

2. **`Color.kt`.** Create `app/src/main/java/com/curro/app/presentation/theme/Color.kt`
   per the resolved Q4 choice; assign every M3 role; KDoc the placeholder
   nature + the contrast floor.

3. **`Type.kt`.** Create `Type.kt`; assign every M3 role at or above the
   floor table in *Scope*; KDoc the floor table inline.

4. **`Shape.kt`.** Create `Shape.kt` per the placeholder values in *Scope*.

5. **`Spacing.kt`.** Create per the resolved Q1 choice — either plain
   `object CurroSpacing` or `LocalCurroSpacing` (CompositionLocal-backed).

6. **`Dimens.kt`.** Create per the resolved Q2 + Q3 choices.

7. **`CurroTheme.kt`.** **Replace** the stub. Remove the `TODO(SF-0.4)`
   comment. Per the resolved Q5 choice, ensure dynamic-color imports are
   absent. KDoc carries the verbatim "Dynamic color disabled by design …"
   statement (the wording matters for AC).

8. **Verify `MainActivity.kt` is untouched.** `git diff app/src/main/java/com/curro/app/MainActivity.kt`
   → empty. If it diffs, undo (the call site is invariant).

9. **Detekt exclude.** Edit `config/detekt/detekt.yml`, add the
   `**/presentation/theme/**` exclude to the `MagicNumber` rule's `excludes`
   list with the `# US-004: …` comment. Run `./gradlew detekt` mid-step to
   verify (a) the theme module no longer fires `MagicNumber` and (b) no
   *new* deprecation warnings appear (regression on US-003's clean state).

10. **`BigPrimaryButton.kt`.** Create at `presentation/common/`; reference
    `Dimens.MinTapTarget`, `MaterialTheme.typography.titleLarge`,
    `MaterialTheme.colorScheme.primary/onPrimary`, `MaterialTheme.shapes.medium`;
    fire haptic. Add the 4 `@Preview`s.

11. **`BigCard.kt`.** Create at `presentation/common/`; reference
    `Dimens.BigRowHeight`, `CurroSpacing.l`, `MaterialTheme.colorScheme.surfaceVariant/onSurfaceVariant`,
    `MaterialTheme.shapes.medium`. The `Card`'s elevation: use either
    `Dimens.CardElevation = 2.dp` (add to `Dimens.kt`) **or** accept the
    inline `2.dp` literal (theme module — permitted by the exclude). Pick one
    and KDoc the choice. Add the 8 `@Preview`s (4 × {`onClick = null`,
    `onClick = { }`}).

12. **Per resolved Q6**, optionally land `BigYesNoRow.kt` (10-line composition).

13. **Contrast computation.** Open
    https://webaim.org/resources/contrastchecker/ (or use the Material 3
    contrast tool / Android Studio's accessibility scanner) and compute every
    pairing in *Acceptance Criteria → Contrast floor verified*; record each
    ratio in the PR description. If any pairing fails, edit the placeholder
    hex in `Color.kt` and re-compute until it passes.

14. **`themes.xml` review.** Per the *In Scope* `themes.xml` item, decide
    Option A (align `windowBackground`) or Option B (accept flash as US-005
    debt). Record in PR.

15. **Three commands green** (in order):
    1. `./gradlew assembleDebug` — APK builds; "Curro" still renders.
    2. `./gradlew ktlintCheck detekt` — lint green; the `MagicNumber` exclude
       works as documented (verified by the deliberately-introduced
       violation: temporarily add `val x = 96.dp` to `BigPrimaryButton.kt`,
       confirm detekt fails; revert).
    3. `./gradlew testDebugUnitTest` — `SmokeTest` still passes (regression
       guard).

16. **Manual device verification** (Redmi 15 / emulator):
    - `adb shell cmd uimode night yes` then `night no` — theme flips; "Curro"
      remains readable.
    - `Settings → Display → Font size → Largest` (or `adb shell settings put
      system font_scale 2.0`) — "Curro" grows; layout doesn't collapse.
    - Haptic on `BigPrimaryButton` press fires (manual: build a tiny scratch
      composable in a `@Preview` or temporarily in `MainActivity` to
      verify; remove before commit).

17. **`@Preview` eyeball pass.** Open each of `BigPrimaryButton.kt` and
    `BigCard.kt` in Android Studio; confirm all previews render. Screenshot
    the 8 panels into the PR description (or, at minimum, state explicitly
    in the PR "all 4×{light/dark/1.5×/2.0×}×2 = 16 previews rendered
    successfully").

18. **Tick AC**; run `verification-checklist`'s relevant sections; commit on
    `main` with the conventional message (`docs(prd): add US-004 …` + the
    code commit scope `theme` per `git-workflow`'s scope table). The user
    has explicitly stated **do not push, do not open a PR** — commit only.

### Cross-SF dependencies

- **Depends on**: US-001 (the theme stub + the Material 3 dep + the package
  skeleton exist), US-002 (Hilt is up — even though US-004 adds no Hilt
  binding, the Hilt-test infrastructure US-002 added must stay green —
  regression guard), US-003 (lint enforcement — the `MagicNumber` exclude
  added here piggybacks on US-003's tuned `config/detekt/detekt.yml`).
- **Blocks**: every Phase 1+ UI SF (SF-0.5 onwards consume the tokens + the
  big components), and **US-005 (SF-0.7)** specifically — US-005 swaps
  values without touching consumer code, which is only possible because
  US-004 ships the semantic-token-only contract here.

### Why the architect review is recommended (and not skipped)

Five decisions in *Open Questions* propagate to *every* composable Curro will
ever ship:
- **Q1** (`object` vs `CompositionLocal` for spacing) touches every consumer's
  syntax — every `Modifier.padding(...)` call in the launcher home, every
  overlay, every config menu row.
- **Q2** (`object Dimens` vs `Modifier` extension) shapes how every shared big
  component spells "≥ 96 dp".
- **Q4** (placeholder palette shape) is read once but visible everywhere
  until US-005 lands; getting it visibly-placeholder is a soft-but-real risk
  control against US-005 slippage.
- **Q5** (structural enforcement of `dynamicColor = false`) determines how
  hard it is to accidentally re-enable user-wallpaper colouring.
- **Q6** (which shared big components ship in US-004 vs SF-0.5) bounds the
  scope here.

Each is mechanical to implement and **hard to reverse after Phase 1 + 2 + 4
land** — every reverse-decision would mean re-touching every composable.
US-002's Q1–Q5 pattern (architect resolves up-front; developer implements
the resolved choice) saved real refactoring downstream; US-004 has at least
as large a propagation surface, so the architect pass is more, not less,
warranted.

PM precedent (US-001's "build-system review" appendix; US-002's Q1–Q5
appendix) — same shape applies here.

### Spec ambiguities surfaced (none requiring a spec bump in US-004)

- `docs/curro-spec-v1.0.md` §11 prescribes "tap targets ≥ 96 dp" and "mic
  button ≥ 40 % of the screen"; it does not prescribe a typography scale.
  The body-as-headline numbers in *Scope* are this brief's interpretation of
  spec §3 + §11 + `brand-design`'s "Senior-first constraints" + `material-design`'s
  "Curro scales Material UP" — internally consistent across all three sources.
  No spec bump needed.

- `brand-design` is a template; its concrete colour / type / spacing /
  radius values are all TODO. That is the *expected* state of the world for
  US-004 — `brand-design` fill-in is US-005's job. The brief notes the
  TODO state explicitly so it doesn't surprise the developer.

- `accessibility-patterns` and `brand-design` agree on the numbers: ≥ 96 dp
  tap targets, system fontScale respected and amplified, ≥ 4.5:1 floor /
  ≥ 7:1 body where possible. **No spec / skill conflict surfaced by US-004.**

- The queued v1.1 spec bump (§5 "8 vs 7 funciones"; §12 "telemetry kept";
  §14 "compileSdk 35" example) is unaffected by US-004 and stays on the
  Phase-0 cross-cutting backlog.

### Reality vs master-plan cross-check (PM ran this before writing the brief)

- The current `CurroTheme.kt` stub at
  `app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt` matches
  what US-001's brief promised: a no-op `MaterialTheme(content = content)`
  wrapper with a `TODO(SF-0.4)` marker. ✓
- `MainActivity.kt` calls `CurroTheme { Surface(Modifier.fillMaxSize()) { Text(stringResource(R.string.app_name)) } }`
  exactly as planned; the call site is the load-bearing invariant US-004
  preserves. ✓
- `presentation/theme/` contains only the stub `CurroTheme.kt` (no
  `Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Dimens.kt` files yet);
  `presentation/common/` is empty (kept alive by `.gitkeep`). ✓
- `config/detekt/detekt.yml` carries `MagicNumber` active with a small
  ignore-numbers list (-1, 0, 1, 2); the rule will fire on `96.dp` /
  `0xFF…` literals in `Color.kt` / `Dimens.kt` without the new exclude. ✓
- `themes.xml` carries the thin `Theme.Curro` AppCompat scaffold; the
  manifest reference resolves to it; cold-launch flash is an open
  consideration. ✓
- No US-004 vs reality mismatch surfaced.

**Cross-references for the implementer**: `brand-design` (authoritative for
the values — currently a template; US-004 ships *placeholders* that satisfy
the senior-first contract; US-005 fills in the real values),
`launcher-ui` (the surfaces these tokens enable — none ship in US-004),
`accessibility-patterns` (the a11y mechanics; US-004 codifies the dimension
contract `≥ 96 dp` / `≥ 40 % screen`), `material-design` (the Material 3
foundation Curro scales up), `compose-patterns` (the `@Preview` light/dark/
large-font triad pattern US-004 inherits and exercises),
`function-catalog` (no impact), `voice-interaction` (no impact),
`platform-integrations` (no impact), `on-device-llm` (no impact),
`local-data` (no impact), `testing-patterns` (no JVM/instrumented tests
land in US-004; future test coverage notes above),
`spec-template` (this document follows it),
`git-workflow` (commit scope = `theme` for the source files; `docs` for the
PRD + brief commit; user has explicitly stated **do not push, do not open
a PR**).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-13 | Fran (Claude `android-product-analyst`) | Initial draft — generated from master-plan SF-0.4 + US-001..US-003 briefs + the actual SF-0.3 files on disk + `brand-design` / `launcher-ui` / `accessibility-patterns` / `material-design` / `compose-patterns` skills. **Architect pass strongly recommended** before `/implement-feature US-004`: Q1–Q7 in *Open Questions* must be resolved (PM provided a recommendation for each; the architect may overrule). |
