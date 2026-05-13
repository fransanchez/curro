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
> stronger, not weaker. **The architect has resolved Q1–Q7 below; A1–A14
> follow in the *Architect's notes & decisions* appendix.**

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
| **Architect** | Claude `android-architect` — Q1–Q7 resolved 2026-05-13; A1–A14 added. See *Open Questions → Resolved* and *Architect's notes & decisions*. |

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

The architect (`android-architect`) has resolved Q1–Q7 below. Each resolution
includes the chosen option, the rationale, and (where applicable) the
code shape the developer follows. Cross-references to the *Architect's notes
& decisions (A1–A12)* appendix below are inline.

### Q1 — Resolved: plain `object CurroSpacing` (no `CompositionLocal`).

I'm with the PM here. Curro has **zero current and zero plausible-near-term**
consumers for theme-variant spacing — dark/light spacing is the same;
large-font "spacing" is already handled by `fontScale` scaling `sp` text
dimensions; the spec's "feels the same every day" rule (§11) actively
*argues against* density variants ever shipping. `CompositionLocal` is the
right tool when (a) the value genuinely varies by `CurroTheme` invocation,
**and** (b) the variation point is the composable tree's `CurroTheme` ancestor
— neither holds for Curro spacing.

The "future-proof for a Granddad-mode super-large spacing toggle" argument
falls apart on inspection: such a toggle, if it ever ships, lives in
`SettingsRepository` (a DataStore-backed `Flow<UiPreferences>`), is read by
`SettingsViewModel`, and propagates as part of `LauncherUiState` /
`ConfigUiState` — **not** by re-wrapping `CurroTheme` with a different
`CurroSpacing`. So `CompositionLocal` is not even the right plumbing for the
hypothetical it claims to enable. See A1 below.

Resolved syntax (every consumer):

```kotlin
import com.curro.app.presentation.theme.CurroSpacing

Column(Modifier.padding(CurroSpacing.l)) { /* … */ }
Spacer(Modifier.height(CurroSpacing.s))
Row(horizontalArrangement = Arrangement.spacedBy(CurroSpacing.xl)) { /* … */ }
```

Resolved file shape (`presentation/theme/Spacing.kt`):

```kotlin
package com.curro.app.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Curro's spacing scale (see A6). Senior-first generosity: the gap BETWEEN
 * tap targets is [xl] or [xxl], not [m]. Read via [CurroSpacing] directly;
 * never raw `.dp` literals outside this package.
 */
object CurroSpacing {
    val none: Dp = 0.dp
    val xs:   Dp = 4.dp
    val s:    Dp = 8.dp
    val m:    Dp = 16.dp
    val l:    Dp = 24.dp
    val xl:   Dp = 32.dp
    val xxl:  Dp = 48.dp
}
```

Reversibility: O(20 min) — if a future SF surfaces a real need for
CL-backed spacing (it won't), wrapping `object CurroSpacing` in
`compositionLocalOf { CurroSpacing }` and updating ~10 consumer sites
across `presentation/common/` + `presentation/launcher/` is mechanical.

### Q2 — Resolved: plain `object Dimens` (no theme extension, no `Modifier` extension).

PM's call stands. Three pieces of evidence:

1. **The values don't vary with theme.** `96.dp` is the senior-first tap-target
   contract — it is the *same* number in light, in dark, at `fontScale = 1.0`
   and at `fontScale = 2.0`. The only mechanic an M3 theme extension provides
   (via `CompositionLocal` plumbing inside `CurroTheme`) is "this value varies
   with theme"; we don't have that case.
2. **`MicButtonMinHeightFraction = 0.40f` is a `Float`, not a `Dp`.** A
   `Modifier.bigTapTarget()` extension can't naturally express a fraction
   constant; an M3 theme extension can but then the "this is a tap-target
   contract" framing leaks into "this is a value bag with a name", which is
   exactly what a plain `object` already is — without the boilerplate.
3. **`Modifier.bigTapTarget()` as a one-liner is plausible later** (it expresses
   intent, which a constant doesn't), but it's a **thin wrapper** over
   `Modifier.heightIn(min = Dimens.MinTapTarget)`. Trivially additive in
   SF-0.5+; not blocked by US-004's choice.

The `MaterialTheme.dimens` extension is a popular pattern in Now-in-Android
and similar codebases, but its real value shows up when (a) the dimension set
is large and visually-themed, or (b) downstream design-system consumers want
a single `MaterialTheme.*` namespace. Curro has five constants and one
consumer surface (`presentation/common/`); the indirection costs more than it
saves. See A2 below.

Resolved file shape (`presentation/theme/Dimens.kt`) — same as the PM's draft:

```kotlin
package com.curro.app.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Senior-first dimension contract for Curro (spec §3, §11; see A2).
 *
 * These are mechanical invariants, not a scale: every touch target / mic
 * button / card / list row in the app must respect them. US-005 (SF-0.7)
 * may move colours, typography, and shapes; these numbers do not move.
 */
object Dimens {
    /** Minimum tap-target size — twice Material 3's 48 dp. Spec §3, §11. */
    val MinTapTarget: Dp = 96.dp

    /** Main launcher mic button: ≥ 40 % of the screen height. Spec §11. See Q3 / A3. */
    val MicButtonMinHeightFraction: Float = 0.40f

    /** Standard big primary button height — alias of [MinTapTarget] for readability at the call site. */
    val BigButtonHeight: Dp = 96.dp

    /** Big card / list-row minimum height (clickable variant). */
    val BigRowHeight: Dp = 96.dp

    /** Glyph size inside a 96 dp IconButton — the senior-first "icon you can see". */
    val LargeIconSize: Dp = 48.dp

    /** Card elevation — pinned here so the `2.dp` literal in [BigCard] stays out of `presentation/common/`. See A11. */
    val CardElevation: Dp = 2.dp
}
```

Note **`Dimens.CardElevation = 2.dp` is added** so `BigCard.kt` does not need
an inline `2.dp` literal in `presentation/common/`. The detekt exclude (A8) is
*exact* (`**/presentation/theme/**`) and must not be widened to
`presentation/common/`; codifying the elevation as a named constant in
`Dimens` keeps the rule firing in `presentation/common/` for any future raw
literal that might sneak in. Reversibility: O(0) — adding it costs nothing
and removes the only ambiguity in the PM's `BigCard` draft.

### Q3 — Resolved: keep `MicButtonMinHeightFraction = 0.40f` on `Dimens` (PM's option 1).

Agreed with the PM. Two reasons:

1. **`Dimens` is named for the conceptual category, not the type.** The class
   name in this file is "senior-first dimensional invariants" — a fraction of
   the screen for the mic button's minimum height is exactly that. A
   separate `object Fractions` for one value is bureaucracy; a top-level
   `val MIC_BUTTON_MIN_HEIGHT_FRACTION` clutters the namespace and breaks
   the "everything theme-related is on a named object inside
   `presentation/theme/`" rule.
2. **The Kotlin type signature already disambiguates.** `Dp` and `Float` are
   different types — IDE auto-complete and the compiler will not let a
   consumer accidentally do `Modifier.heightIn(min = Dimens.MicButtonMinHeightFraction)`.

Document at the call site (when SF-1.3 lands `MicButton`):

```kotlin
// SF-1.3 MicButton.kt (illustrative — not in this SF):
BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val minHeight = maxHeight * Dimens.MicButtonMinHeightFraction   // 40% of available height
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(MaterialTheme.shapes.extraLarge)
            // …
    )
}
```

See A3 below for the spec-§11 link.

### Q4 — Resolved: neutral cool-grey ramp (greyscale, with the cool-leaning hue carve-out below).

I'm with the PM, with a small framing addition. The placeholder's **whole job
is to enforce the contract while being visibly not the brand**, so the brief
absorbs three guardrails:

1. **Cool-leaning neutral, not pure desaturated grey.** Pure `#FFFFFF` ↔
   `#000000` ↔ `#808080` reads as "we forgot a palette". A cool grey ramp
   (a couple of degrees toward blue: e.g. `#FAFAFC` / `#EAEAEE` /
   `#1A1A1E` / `#0B0B0F`) reads as "this is a placeholder", which is the
   correct affect for the dev build. Contrast wins are identical (the carve
   is within ~3 % of pure grey); the affect change is the point.
2. **`primary` and `error` get *one* hue each** — a desaturated indigo (e.g.
   `#3F4A5E`) for `primary` and the standard Material red `#B3261E` for
   `error`. The point of `primary` being a hue (not grey) in the placeholder
   is so contrast verification (AC) exercises a *coloured* `onPrimary` /
   `primary` pair the way the real brand will — if we use grey for `primary`
   too, US-005's contrast surprises won't surface until US-005.
3. **The placeholder hex KDoc carries a `// PLACEHOLDER (US-005)` comment on
   every value** so a grep for `PLACEHOLDER` lists them all when US-005 lands.

This is "neutral-grey" in the PM's framing — *not* the "muted tint" option,
which is the one I'm rejecting. The cool lean is purely an affective tweak,
not a brand colour. PM's risk argument (an unguarded screenshot reads "the
app is greyscale") is mitigated by the cool lean without adding brand-look
risk. See A4 below for the contrast-floor table.

Resolved palette shape (illustrative; final hexes are the developer's pick
subject to AC contrast verification):

```kotlin
package com.curro.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// PLACEHOLDER PALETTE — replaced wholesale by US-005 (SF-0.7).
// Every value is documented with its contrast partner and the achieved ratio
// (developer pastes the computed numbers into the PR; see AC + A4).

val LightColors: ColorScheme = lightColorScheme(
    primary           = Color(0xFF3F4A5E),  // PLACEHOLDER (US-005) — desaturated indigo
    onPrimary         = Color(0xFFFAFAFC),  // contrast vs primary ≥ 4.5:1
    primaryContainer  = Color(0xFFDDE0EA),  // PLACEHOLDER (US-005)
    onPrimaryContainer= Color(0xFF0B0B0F),
    secondary         = Color(0xFF5E5E66),  // PLACEHOLDER (US-005) — cool-grey
    onSecondary       = Color(0xFFFAFAFC),
    tertiary          = Color(0xFF5E5E66),  // PLACEHOLDER (US-005) — same as secondary in placeholder
    onTertiary        = Color(0xFFFAFAFC),
    background        = Color(0xFFFAFAFC),  // cool near-white
    onBackground      = Color(0xFF0B0B0F),  // contrast vs background ≥ 12:1 (well above 7:1 senior floor)
    surface           = Color(0xFFFAFAFC),  // background == surface in Curro (see A5)
    onSurface         = Color(0xFF0B0B0F),
    surfaceVariant    = Color(0xFFEAEAEE),  // cards, tiles
    onSurfaceVariant  = Color(0xFF1A1A1E),  // ≥ 4.5:1 vs surfaceVariant
    error             = Color(0xFFB3261E),  // PLACEHOLDER (US-005) — only red in the system (A5)
    onError           = Color(0xFFFAFAFC),
    outline           = Color(0xFF79797E),
    outlineVariant    = Color(0xFFCACACD),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary           = Color(0xFFB6BCC9),  // PLACEHOLDER — lighter indigo for dark mode
    onPrimary         = Color(0xFF0B0B0F),
    primaryContainer  = Color(0xFF2A2F3A),
    onPrimaryContainer= Color(0xFFEAEAEE),
    secondary         = Color(0xFFA8A8AC),
    onSecondary       = Color(0xFF0B0B0F),
    tertiary          = Color(0xFFA8A8AC),
    onTertiary        = Color(0xFF0B0B0F),
    background        = Color(0xFF0B0B0F),  // warm near-black (not pure #000)
    onBackground      = Color(0xFFEAEAEE),
    surface           = Color(0xFF0B0B0F),
    onSurface         = Color(0xFFEAEAEE),
    surfaceVariant    = Color(0xFF1A1A1E),
    onSurfaceVariant  = Color(0xFFCACACD),
    error             = Color(0xFFF2B8B5),  // lighter red for dark mode
    onError           = Color(0xFF0B0B0F),
    outline           = Color(0xFF8E8E92),
    outlineVariant    = Color(0xFF3A3A3E),
)
```

The exact hexes are the developer's pick — what's locked is (a) the cool-grey
ramp affect, (b) `primary` and `error` as the only two hues, (c) every value
is annotated `// PLACEHOLDER (US-005)`, and (d) every contrast pairing in AC
passes. If a hex fails contrast, adjust the value (not the policy).

### Q5 — Resolved: PM's option 2 (KDoc + missing parameter + missing import) — with a one-line `if (dynamicColor)` *intentionally absent* from the body.

PM's option 2 is the right ceiling for this SF. Option 1 (KDoc only) is too
soft — a developer reading `CurroTheme.kt` after a year sees "could add
`dynamicColor` here" and reaches for it; the missing import + missing
parameter together make the override require **three** deliberate keystrokes,
not one. Option 3 (custom detekt rule) reopens the `tools/detekt-rules/`
tooling US-003 explicitly punted; not in this SF.

What I'm adding to the PM's draft: the **`CurroTheme` signature is the strict
two-parameter form** below — no `dynamicColor: Boolean = false` default-on
parameter that someone could later set to `true`, no third overload. A
reviewer who sees `CurroTheme(darkTheme = …, dynamicColor = true) { … }` in a
diff is reading a compile error, not a runtime regression. See A7 below.

Resolved signature (final):

```kotlin
package com.curro.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// Deliberately NOT imported (see KDoc + A7):
//   - androidx.compose.material3.dynamicLightColorScheme
//   - androidx.compose.material3.dynamicDarkColorScheme

/**
 * Curro's app-wide theme.
 *
 * Wraps Material 3 with [LightColors] / [DarkColors] (placeholder palette —
 * real values arrive in US-005/SF-0.7), [CurroTypography] (body-as-headline
 * scale — senior-first contract; placeholder sizes here), and [CurroShapes]
 * (rounded corners).
 *
 * **Dynamic color disabled by design — predictability ("feels the same every
 * day", spec §11) and the senior contrast floor outrank user-wallpaper
 * personalisation.** The dynamic-color APIs are deliberately not imported in
 * this file so accidentally re-enabling dynamic colour requires (1) adding
 * an import, (2) adding a parameter to this function, AND (3) wiring the
 * call site through `MainActivity` — three deliberate code changes, not a
 * parameter flip.
 *
 * The dark/light branch follows the system [isSystemInDarkTheme] — Curro
 * does not own a per-user theme toggle in MVP (the config menu may add one
 * in Phase 8 if Fran's father has a preference; today, follow the system).
 *
 * @see Q5 / A7 in docs/briefs/US-004-curro-theme.md for the structural
 * enforcement rationale.
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
    )
}
```

The verification AC keeps the verbatim grep:
`grep -rn "dynamicLight\|dynamicDark" app/src/main` returns zero matches.
**Adding to that AC:** `grep -rn "dynamicColor" app/src/main` also returns zero
matches (the *parameter name* must not exist either — see A7).

### Q6 — Resolved: ship `BigPrimaryButton` + `BigCard` in US-004; defer `BigYesNoRow` + `BigListRow` to SF-0.5 (PM's split stands).

I'm with the PM. Two reasons that close the case:

1. **`BigYesNoRow` needs the brand decision US-005 owns.** Whether NO is
   `Button` (filled secondary), `OutlinedButton`, `FilledTonalButton`, or
   `TextButton` is a brand-design call — not a theme-scaffold call. SÍ is
   easy (it's `BigPrimaryButton`); NO is the open question. Shipping
   `BigYesNoRow` in US-004 forces an answer to a question that doesn't have
   to be answered yet. SF-0.5 lands it after US-005, with one of those four
   answers picked.
2. **`BigListRow` needs its first consumer.** The icon-size contract
   (`Dimens.LargeIconSize = 48.dp`) belongs in US-004 (it's there). But the
   *layout* — icon-left vs icon-top, contact-photo branch vs glyph branch,
   one-line label vs two-line — is best decided against the first real
   consumer (`AppTileGrid` in SF-1.4, where it's a 2-column big-tile grid).
   Designing the row in isolation in US-004 risks over-specifying it. SF-0.5
   lands the *generic* row; SF-1.4 may add a specialised `AppTile` on top.

Both punted components are 10–30 lines once their open questions resolve;
nothing structural blocks them. **The architect explicitly endorses the PM's
"first two" split.** See A12 below for the SF-0.5 hand-off.

If the developer feels productive friction without `BigYesNoRow` while
implementing US-004's `@Preview`s (unlikely — `BigPrimaryButton`'s previews
don't need it), the 10-line composition above can land as a private preview
helper in `BigPrimaryButton.kt`, but **must not** ship as a top-level
public composable until SF-0.5 — that would commit Curro to the SÍ/NO
visual treatment before US-005 has decided it.

### Q7 — Resolved: confirmed — no Hilt module, no DI binding for the theme.

PM's call confirmed. The theme is a Compose concept, not a runtime-swappable
dependency. Every consumer (every `@Composable` in the app; every UI test) is
already `CurroTheme { content }` — no construction-time injection point.
Runtime variants (light/dark + the hypothetical future Phase-8 user-theme
toggle) are handled by the `CurroTheme` composable's own parameter surface
(today: `darkTheme: Boolean = isSystemInDarkTheme()`; tomorrow potentially
also `themeOverride: ThemeOverride?` reading from `SettingsRepository` via a
`Flow` in a parent ViewModel and passing the resolved state down).

**No `ThemeModule.kt`** anywhere in `di/`. The "How to inject the theme in
tests" question doesn't arise because tests do `composeTestRule.setContent {
CurroTheme { content } }` directly — see A9 / `testing-patterns`.

If a future scenario *does* surface (e.g. a brand-A/B feature flag that
swaps colour schemes by build variant — there is no such scenario today),
that's a `BuildConfig.*` branch inside `CurroTheme`'s body, not a Hilt
injection. Reversibility: O(0) — adding a Hilt module later is mechanical.

## Architect's notes & decisions

These are the load-bearing theme decisions the architect locked in for SF-0.4.
Each note is referenced from the Scope / Specification / Q-Resolved sections
above, and is meant to be cited verbatim by later-SF briefs that consume the
theme contract (every UI SF in Phase 1+, plus US-005). **All of them must be
settled by the time `/implement-feature US-004` writes the first `Color.kt`
line** — they propagate to every later composable Curro will ever ship; a
later reversal means re-touching every composable.

**A1. The 7-step spacing scale (`none / xs / s / m / l / xl / xxl`) is the
contract.** PM proposed seven tokens; `brand-design`'s current scaffold shows
four (`Small / Medium / Large / XLarge`). I take the seven-step. Three reasons:

1. **`none`** is the explicit-zero case for `Modifier.padding(...)` defaults
   and for `Arrangement.spacedBy(...)` toggle-off — committing to a named
   `none` over an inline `0.dp` is consistent with "no raw `.dp` literals
   anywhere outside `presentation/theme/`" (the load-bearing rule of US-004).
2. **`xs`** (4 dp) is the tight inner gap between an icon and its label inside
   a button or between glyph and text inside a row; the Material spec uses
   4 dp for this routinely, and squashing it onto `s` (8 dp) makes inner
   compositions feel loose.
3. **`xxl`** (48 dp) is "gap between two adjacent ≥ 96 dp tap targets" —
   senior-first generosity demanded by spec §3, mid-`xl` (32 dp) is too
   tight when both sides are huge.

The four-step scale (`Small/Medium/Large/XLarge`) is `brand-design`'s legacy
template, written before US-002/-003/-004 sharpened the contract. **US-005
(SF-0.7) updates the `brand-design` Spacing section to the 7-step
`none/xs/s/m/l/xl/xxl` shape that ships here.** That update is part of
US-005's "fill in the brand skill" remit; this brief flags it. Until US-005
lands, the 7-step in code is the authoritative version; the skill is the
template-being-replaced.

Naming convention: **lowercase short tokens** (`none / xs / s / m / l / xl /
xxl`), not `Small / Medium / Large` PascalCase. Reason: read-site density.
`Modifier.padding(CurroSpacing.l)` reads better than
`Modifier.padding(CurroSpacing.Large)`, especially in `Arrangement.spacedBy(...)`
calls that are common in `LazyVerticalGrid` / `LazyColumn` blocks (see
`compose-patterns`). Reversibility: O(15 min) — `replace_all` across the
codebase. Pin the naming once; do not bike-shed.

**A2. `object Dimens` not `MaterialTheme.dimens`.** Resolved at Q2. The
key insight that wasn't in the PM's table: the dimens object's *consumer
contract is the M3 senior-first override*, not the M3 theme. `BigPrimaryButton`
reads `Dimens.MinTapTarget` because that's the spec-§3 number, not because
that's "the theme's tap target". If a future SF ever wants a *themed* tap
target (it won't, but if), it adds a separate `MaterialTheme.dimens`
extension layered *on top* of `Dimens`; the constants stay as the floor.
Reversibility: O(10 min). See also A11 for the `CardElevation` addition.

**A3. `MicButtonMinHeightFraction = 0.40f` is a `Float` on `Dimens`, but
SF-1.3's `MicButton` is the first consumer.** US-004 ships the slot
(`Dimens.MicButtonMinHeightFraction`); SF-1.3 implements `MicButton` against
a `BoxWithConstraints` so it can compute `maxHeight * Dimens.MicButtonMinHeightFraction`
at layout time. The `0.40f` value is the spec-§11 minimum — the *floor* — not
the target; SF-1.3 may pick a higher fraction (e.g. 0.45) if visual review
suggests it; the floor is "≥ 40 % screen", not "exactly 40 %". Pin the floor
in US-004; defer the target to SF-1.3. Reversibility: O(0) — adjusting one
fraction is a one-line change.

**A4. Contrast floor for the placeholder palette — strict; non-negotiable.**
Resolved at Q4. The contrast verification AC enumerates the pairings; this
note pins the *numerical* floor that every placeholder pairing must clear:

| Pairing | Light floor | Dark floor | Notes |
|---|---|---|---|
| `onSurface` / `surface` | **≥ 4.5:1** | **≥ 4.5:1** | body text on background — aim ≥ 7:1 if the cool-grey ramp allows it |
| `onBackground` / `background` | **≥ 4.5:1** | **≥ 4.5:1** | usually identical to `onSurface` / `surface` (see A5) |
| `onPrimary` / `primary` | **≥ 4.5:1** | **≥ 4.5:1** | mic-button label legibility |
| `onSurfaceVariant` / `surfaceVariant` | **≥ 4.5:1** | **≥ 4.5:1** | card body text |
| `primary` / `surface` | **≥ 3:1** | **≥ 3:1** | a coloured UI element on background (large text / icon pair) |
| `error` / `surface` | **≥ 3:1** | **≥ 3:1** | error indicator legibility |
| `outline` / `surface` | **≥ 3:1** | **≥ 3:1** | borders/dividers must be visible |

The senior-first **aspirational** floor is ≥ 7:1 for body
(`accessibility-patterns`); the placeholder palette must *not* undercut WCAG
AA (the ≥ 4.5:1 / ≥ 3:1 above). US-005 raises the body bar to ≥ 7:1 wherever
the real brand palette allows (`brand-design` rule). The developer pastes
**computed** ratios (not "looks contrasty") into the PR description; if any
pairing fails, the hex moves until it passes.

**A5. `surface == background` in Curro; `error` is the only red.** Two
mapping-contract decisions that propagate to every later UI SF:

1. **`surface` and `background` are the same colour** in both `LightColors`
   and `DarkColors`. Material 3 distinguishes them so apps can paint a "card
   surface" different from the "screen background"; Curro's "feels the same
   every day" rule (spec §11) argues for visual flatness — the launcher home,
   the overlays, the message cards, the config menu all share one background,
   and cards stand out via `surfaceVariant` (which IS different). Pinning
   `surface == background` in the placeholder makes US-005 honour the same
   relationship by default.
2. **`error` is the only red.** No brand accent in `primary` / `secondary` /
   `tertiary` may be in the red family (spec §11 "high contrast" + the
   colour-is-never-the-only-signal rule from `accessibility-patterns`).
   The placeholder uses `#B3261E` (light) / `#F2B8B5` (dark) for `error`;
   US-005's real brand palette must not introduce a competing red anywhere
   else, or the failure signalling collapses. This is a hard rule for US-005,
   flagged here.

`primary` is "the mic-button colour, the SÍ button colour, selected states"
(per `material-design`'s table). `tertiary` is "special moments / highlights"
in `brand-design`; for the placeholder I'm aliasing `tertiary` to
`secondary` (cool grey) so it doesn't accidentally read as a second brand
hue — US-005 picks a real `tertiary`.

**A6. `respect AND amplify` system `fontScale` — operationalised.** The
spec/brand-design rule "respect and amplify" needs a concrete operational
definition. Choice:

- **`fontScale` is honoured as-is. No clamping. No `Density` override. No
  ceiling.**
- A theoretical `fontScale = 3.0` would survive layout (Compose's `sp` units
  scale linearly); the `@Preview(fontScale = 2.0f)` is the *senior-first
  regression test* (the user's real configuration sits between `1.5×` and
  `2.0×` per the brand-design / accessibility-patterns guidance). Any
  composable that clips at `2.0×` is a layout bug, not a fontScale-cap call.
- "Amplify" means **the base type scale is already ~25 % above Material**
  (per the per-role floor table in *Scope* — `bodyLarge` at 20 sp vs M3's
  16 sp). The amplification is in the *scale*, not in the
  fontScale-multiplier; we don't multiply `fontScale * 1.25` on top.
- A future per-user "Granddad-mode" boost (an additional multiplier in
  config menu) is deferred to SF-0.5 / SF-8.x (out of scope per the PM's
  brief); when it lands, it lives in `SettingsRepository` and flows through
  the ViewModel, **not** as a parameter on `CurroTheme`.

Bottom line: **no caps, no clamps, no overrides** — the developer relies on
`sp` and the system's linear scaling. The `2.0×` preview is the regression
guard. Reversibility: this is the simplest possible choice; nothing to revert.

**A7. `dynamicColor` triple-guard.** Resolved at Q5. Codify the three guards
as the developer's regression checklist:

1. **Missing parameter.** `CurroTheme(darkTheme, content)` has no third
   parameter; `grep -rn "dynamicColor" app/src/main` returns zero matches
   (including the parameter name itself).
2. **Missing imports.** `dynamicLightColorScheme` and `dynamicDarkColorScheme`
   are not imported in `CurroTheme.kt`; `grep -rn "dynamicLight\|dynamicDark" app/src/main`
   returns zero matches.
3. **Verbatim KDoc.** The phrase `Dynamic color disabled by design` appears
   in `CurroTheme.kt`'s KDoc; `grep -c "Dynamic color disabled by design" app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt`
   returns ≥ 1.

The custom detekt rule banning the API project-wide is **deferred** to the
same SF that takes on `tools/detekt-rules/` (the No-Double-Padding rule
home, currently parked — see US-003). When that lands, this triple-guard
becomes belt-and-braces.

**A8. Detekt `MagicNumber` exclude — exact `**/presentation/theme/**`,
nothing else.** The exclude is added to
`config/detekt/detekt.yml`'s `MagicNumber.excludes` list with a one-line
`# US-004: theme is THE one place raw .sp/.dp/0xFF… literals live` comment.
**Do not widen** to `**/presentation/**` or `**/theme/**` — those paths catch
`presentation/common/` (where `BigPrimaryButton` lives — it must NOT be
excluded) and a hypothetical future `theme/` package elsewhere. The exclude
pattern is exact and tested by US-004's AC ("the rule still fires on any raw
`96.dp` / `0xFF` literal in `presentation/common/`").

If the developer hits a second detekt rule that fires on the theme module's
literals beyond `MagicNumber` (likely candidates: none today, but watch for
`LongParameterList` on `lightColorScheme(...)` if it crosses the threshold —
M3's `ColorScheme` has 30+ named parameters), add the same exact exclude to
that rule with the same `# US-004: …` comment. Per-rule, not blanket.

The `MagicNumber.ignoreNumbers` list in detekt.yml is **not** widened
(currently `-1, 0, 1, 2`). Widening it pre-emptively would let `.dp` /
`.sp` literals slip into feature code outside the theme module — exactly
what US-004 is preventing.

**A9. How tests use the theme — `CurroTheme { content }`, always.** Every
JVM (Robolectric) and instrumented Compose UI test that exercises a
composable wraps it in `CurroTheme { Surface { content } }` — no exceptions.
The `Surface` is there so `MaterialTheme.colorScheme.background` paints
correctly in the test (else `Box` shows transparent in screenshot diffs).

```kotlin
// Canonical Compose UI test setup for a senior-first preview-checked composable
composeRule.setContent {
    CurroTheme {
        Surface {
            BigPrimaryButton(text = "Sí", onClick = {})
        }
    }
}
```

For `fontScale` regression in tests, **do not** override `Configuration`
manually — use `@Preview(fontScale = 1.5f)` / `@Preview(fontScale = 2.0f)`
in Studio for the visual check (US-004's AC), and for runtime
`composeTestRule` tests use the `LocalDensity` override:

```kotlin
// Runtime fontScale regression — preferred over Configuration override
composeRule.setContent {
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density, fontScale = 2.0f)
    ) {
        CurroTheme {
            Surface {
                BigPrimaryButton(text = "Sí", onClick = {})
            }
        }
    }
}
```

Document this in `testing-patterns` when SF-0.5's first instrumented test
lands (the canonical example will live there). US-004 ships no UI tests, so
the snippet above is reference-only.

**A10. Haptic feedback on `BigPrimaryButton` — `HapticFeedbackType.LongPress`,
pinned.** Compose's `HapticFeedback` API ships several types; for Curro's
senior-first tap confirmation the right one is **`LongPress`**, not
`TextHandleMove` or `ContextClick`. Reasons:

- `LongPress` is the canonical "this user-action just registered with
  tactile certainty" type — it's the haptic Android uses for the system
  long-press menu, which is exactly the affect we want (firm, unmistakable,
  not a passing brush).
- `TextHandleMove` is too soft (it's tuned for text-cursor scrubs) — would
  feel like the press didn't register.
- `ContextClick` is too short and too quiet on most devices — also reads
  as "barely registered".

This propagates to **every** `BigPrimaryButton` consumer (and the future
`BigYesNoRow`, `BigListRow`). Pin the type in `BigPrimaryButton.kt`'s body
and KDoc; future haptic-tuning is a one-line change. The Redmi 15 has a
strong linear vibration motor; `LongPress` will feel good there. If the
real device feels harsh (unlikely), the SF-0.5 "shared big components final
shape" SF can review and tune — but the *baseline* is `LongPress`.
Reversibility: O(1 min) — change one constant.

**A11. `BigCard`'s clickable-or-not contract.** `BigCard`'s `onClick` is
`(() -> Unit)?` — nullable on purpose. The contract:

- **`onClick = null`** — the card is a **display surface**. It carries no tap
  affordance, no haptic, no `Modifier.clickable`. Examples: a WhatsApp
  message card while being read (the card itself isn't tappable; the user
  uses the mic button to interrupt). No `Dimens.BigRowHeight` minimum is
  enforced — the card sizes to its content.
- **`onClick = non-null`** — the card is a **clickable surface**.
  `Dimens.BigRowHeight = 96.dp` minimum height enforced; haptic
  (`LongPress`); `Modifier.clickable`. Examples: a contact-picker row, a
  config-menu row.

**Reject `Card(onClick = { /* no-op */ })`.** That pattern (a stateful
"clickable" with a no-op callback) reads as accidental in code review and is
the wrong shape — a card you can press but that doesn't react is worse than
a card you can't press. If a card needs to look clickable but do nothing
(e.g. while loading), pass `enabled = false` to a separate parameter and
have the haptic fire but the action no-op explicitly. US-004 ships no
`enabled` parameter on `BigCard` — defer to SF-0.5 if the consumer surface
demands it.

Implementation hint (the PM's snippet is correct; this pins the rule):

```kotlin
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
        Modifier  // intentional: no minHeight, no clickable, no haptic
    }
    Card(
        modifier = modifier.fillMaxWidth().then(clickableMod),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation),
    ) {
        Column(modifier = Modifier.padding(CurroSpacing.l)) {
            content()
        }
    }
}
```

The elevation uses `Dimens.CardElevation` (A2 / Q2 addition), so `BigCard.kt`
in `presentation/common/` carries **zero** raw `.dp` literals — the detekt
exclude (A8) is exact and the rule continues to fire here.

**A12. Cold-launch splash flash — `installSplashScreen()` deferred; US-004
takes the "align `windowBackground`" path (PM's Option A).** The PM's brief
left the choice open between (A) aligning `themes.xml` `windowBackground`
with `LightColors.background`, and (B) accepting the flash as US-005 debt.
**I'm picking A.** Reasons:

1. **The fix is two lines of XML** —
   `<item name="android:windowBackground">@color/curro_window_background</item>`
   plus a one-line `<color name="curro_window_background">#FFFAFAFC</color>`
   in `res/values/colors.xml`. (And the dark-mode counterpart in
   `res/values-night/colors.xml`.) Total: four files, < 30 lines.
2. **The cost of accepting the flash is high.** Cold-launch flash on a
   senior-first launcher reads as "the app is broken" — even briefly. The
   Redmi 15's cold-launch can be ~150–250 ms; that's long enough for the
   user to register a colour switch as a glitch.
3. **`installSplashScreen()` (the `androidx.core:core-splashscreen` API) is
   overkill** for what we need today. It buys us splash-screen icon
   animation, branded splash background, and Android 12+ splash-screen
   integration; we need *none* of that. It also adds a dependency.
   **`androidx.core:core-splashscreen` is deferred** to a future SF if we
   ever want a branded splash (unlikely; the launcher launches *into* the
   user's home — there's no entry point that needs a splash). For US-004,
   the XML `windowBackground` alignment is sufficient.

Resolved addition to `presentation/theme/` *or* `res/values/themes.xml`:
the `Theme.Curro` style gets a `windowBackground` item pointing to a
colour resource that matches `LightColors.background` (and
`values-night/themes.xml` mirrors for dark — the existing
`Theme.AppCompat.DayNight.NoActionBar` parent already handles day/night
resource resolution, so it's just `values/` + `values-night/` for the
colour). The developer records this in the PR description; AC item
"`themes.xml` is reviewed" is satisfied by Option A.

US-005 then **updates the colour resource value** when the real brand
palette lands (one change in `res/values/colors.xml` + one in
`res/values-night/colors.xml`) — same shape as updating `Color.kt`, no new
mechanism. The flash is closed at US-004; US-005 just refreshes the value.

**A13. Hand-off to consuming SFs.** US-004 ships *tokens and bricks*, not
*surfaces*. The downstream SFs that consume this contract:

- **SF-0.5** (`android-developer` + `android-ui-designer`) — `BigYesNoRow`,
  `BigListRow`; the first UI tests on `BigPrimaryButton` and `BigCard`. NO's
  visual treatment (filled secondary vs outlined vs tonal) decided after
  US-005 sets the brand `secondary`.
- **SF-0.6** (`android-developer`) — `CurroNavHost`: the launcher home + config
  menu routes. Consumes `MaterialTheme.colorScheme.background` for the
  `Scaffold`; no new theme tokens added.
- **US-005 / SF-0.7** (`android-ui-designer` + `voice-pipeline-engineer`) —
  fills in `brand-design`'s palette / type-scale / spacing-step-names /
  shape-radii TODOs with real Curro brand values; updates the colour
  resources in `res/values/colors.xml` to match `LightColors.background`'s
  new hex (per A12); locks the `COPY.*` line table.
- **SF-1.2** (`android-developer`) — `LauncherScreen`: consumes
  `displayLarge` for the clock, `titleLarge` for the app-tile labels,
  `Dimens.MicButtonMinHeightFraction` via `BoxWithConstraints` for the
  `MicButton`.
- **SF-1.3** (`android-developer`) — `MicButton`: the first real consumer of
  `Dimens.MicButtonMinHeightFraction`. The fraction *floor* is pinned here;
  the SF picks the actual visual height (must be ≥ 40 %).
- **SF-1.4** (`android-developer`) — `AppTileGrid` + `AppTile`: the first
  consumer that needs `BigListRow`'s contract (`AppTile` is a specialised
  `BigListRow` — see Q6 / A12 commentary).
- **SF-5.x** (`voice-pipeline-engineer`) — `ListeningOverlay` /
  `ProcessingOverlay` / `ConfirmationOverlay`: consume `BigPrimaryButton`,
  `BigCard`, `BigYesNoRow`, the `ListeningTint` (added in US-005), and the
  `displayMedium` / `headlineLarge` typography roles for overlay headlines.

No further architect review is required for these hand-offs **unless** a
later SF surfaces a real-brand or real-spec conflict with the contract
above (e.g. US-005 picks a `primary` whose contrast fails AC's pairings —
that's a brand call escalated back to the architect to re-litigate A4 /
A5). The contract is intended to be stable for the prototype's lifetime.

**A14. Reversibility checkpoint.** Of the seven Q resolutions:

| Q | Resolution | Reversal cost |
|---|---|---|
| Q1 | Plain `object CurroSpacing` | O(20 min) — wrap in `compositionLocalOf` + update ~10 consumer sites |
| Q2 | `object Dimens` (plus `Dimens.CardElevation = 2.dp` added) | O(10 min) — add `MaterialTheme.dimens` extension on top |
| Q3 | `MicButtonMinHeightFraction` on `Dimens` | O(0 — never needed) |
| Q4 | Cool-grey ramp placeholder + indigo `primary` + red `error` | O(0) — US-005 replaces these wholesale anyway |
| Q5 | KDoc + missing parameter + missing import + verbatim KDoc + name-grep AC | O(5 min) — adding `dynamicColor` later is mechanical (we just won't) |
| Q6 | Two components ship; two defer | O(20 min) — landing the two punted ones early is mechanical, but pre-empts US-005 brand decisions |
| Q7 | No Hilt module | O(15 min) — adding a `ThemeModule` later is mechanical; we don't need it |

The only resolution with non-trivial **second-order** cost is Q1 — but every
later SF's consumer surface for `CurroSpacing` is mechanical to update; we
are not painting ourselves into a corner. **Pin the seven choices; the
reversal cost is bounded.**

## Implementation Notes

### Order of operations (developer-facing checklist)

Branch policy: the user explicitly requested **no new branch** for this SF —
work directly on `main`. PM verified before invoking the brief. If a future
revision wants a branch, follow `git-workflow`'s convention
(`feature/US-004-curro-theme`); for now, single commit on `main`.

1. **Architect pass.** ✅ **Complete** — Q1–Q7 resolved and A1–A14 added
   (see *Open Questions* + *Architect's notes & decisions*). The developer
   reads both sections end-to-end before writing the first line of code —
   the resolved syntax / file shapes / detekt-exclude / contrast-floor /
   dynamic-color triple-guard / haptic type / `BigCard` clickable contract
   / cold-launch `windowBackground` alignment are all pinned. **No further
   architect review needed unless a concrete obstacle surfaces** (see
   *Why the architect review was needed* below for the escalation rule).

2. **`Color.kt`.** Create `app/src/main/java/com/curro/app/presentation/theme/Color.kt`
   per the **Q4-resolved** cool-grey-ramp shape (A4 + A5): every M3 role
   assigned; `// PLACEHOLDER (US-005)` comment on every value; KDoc
   reproduces the contrast-floor table from A4.

3. **`Type.kt`.** Create `Type.kt`; assign every M3 role at or above the
   floor table in *Scope*; KDoc the floor table inline; `FontFamily.Default`
   (system font) for every role (real font is US-005).

4. **`Shape.kt`.** Create `Shape.kt` per the placeholder values in *Scope*.

5. **`Spacing.kt`.** Create `object CurroSpacing` per **Q1-resolved**
   (plain object, 7-step `none/xs/s/m/l/xl/xxl` lowercase tokens — A1).

6. **`Dimens.kt`.** Create `object Dimens` per **Q2 + Q3-resolved** (plain
   object; `MinTapTarget`, `MicButtonMinHeightFraction`, `BigButtonHeight`,
   `BigRowHeight`, `LargeIconSize`, **and `Dimens.CardElevation = 2.dp`** — A2
   addition so `BigCard.kt` in `presentation/common/` carries no raw `.dp`).

7. **`CurroTheme.kt`.** **Replace** the stub per the **Q5-resolved**
   signature (the snippet under *Q5 — Resolved*; A7). Two-parameter form
   (`darkTheme`, `content`) — no `dynamicColor`. The dynamic-color imports
   are not added. KDoc carries the verbatim phrase
   `Dynamic color disabled by design`. AC verifies via
   `grep -rn "dynamicLight\|dynamicDark\|dynamicColor" app/src/main` → zero
   matches.

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

11. **`BigCard.kt`.** Create at `presentation/common/` per the
    **Q2-resolved** A11 contract: `onClick: (() -> Unit)?` nullable —
    non-null → `Dimens.BigRowHeight` minimum + haptic
    (`HapticFeedbackType.LongPress`, A10) + `Modifier.clickable`; null →
    display surface, no minHeight, no haptic. Elevation uses
    `Dimens.CardElevation` (NOT an inline `2.dp` literal — A11). References
    `MaterialTheme.colorScheme.surfaceVariant`/`onSurfaceVariant`,
    `MaterialTheme.shapes.medium`, `CurroSpacing.l` for the inner column
    padding. Add the 8 `@Preview`s (4 × {`onClick = null`, `onClick = { }`})
    per A9 (`CurroTheme { Surface { … } }` wrapper).

12. **Per Q6-resolved**: do NOT land `BigYesNoRow.kt` in US-004. SF-0.5 owns
    it (rationale: NO's visual treatment is a US-005 brand call — Q6 / A12).
    If the developer needs a SÍ/NO row in a preview to eyeball `BigPrimaryButton`,
    use a private preview helper inside `BigPrimaryButton.kt` (not a
    top-level composable).

13. **Contrast computation.** Open
    https://webaim.org/resources/contrastchecker/ (or use the Material 3
    contrast tool / Android Studio's accessibility scanner) and compute every
    pairing in *Acceptance Criteria → Contrast floor verified*; record each
    ratio in the PR description. If any pairing fails, edit the placeholder
    hex in `Color.kt` and re-compute until it passes.

14. **`themes.xml` — Option A pinned (A12).** Per the architect's A12
    decision, US-004 takes the "align `windowBackground`" path (not
    "accept flash as US-005 debt"). Add a `<color name="curro_window_background">`
    to `res/values/colors.xml` matching `LightColors.background`'s
    placeholder hex; mirror in `res/values-night/colors.xml` matching
    `DarkColors.background`. Update `res/values/themes.xml` to set
    `<item name="android:windowBackground">@color/curro_window_background</item>`
    inside `Theme.Curro`. `androidx.core:core-splashscreen` is deferred
    (A12 — not added to the dependency graph). Record the placeholder hexes
    chosen in the PR description.

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

### Owner split

**PM (`android-product-analyst`)** owns Metadata / Summary / Scope /
User Flows / Acceptance Criteria / Design Notes / Senior-UX & Copy /
Performance Considerations / Testing Requirements / Cross-SF dependencies /
Spec ambiguities / Reality cross-check / Revision History. PM authored the
initial draft (1198 lines) and the seven Open Questions with PM
recommendations.

**Architect (`android-architect`)** reviewed the brief, **resolved Q1–Q7**
(see *Open Questions → Resolved* blocks), authored the *Architect's notes &
decisions (A1–A14)* appendix, and tightened the Specification +
Implementation Notes ordering where the resolved choices required it. The
architect's role here is to lock in the theme tokens / dynamic-color policy
/ structural shape that every later SF (Phase 1+ UI, US-005 brand fill-in,
Phase 5+ overlays, Phase 8 config menu) consumes through;  no
Clean-Architecture decision lands in US-004 beyond "no Hilt for the theme",
"object-based access for `CurroSpacing` / `Dimens`", and the dynamic-color
triple-guard.

**`android-developer`** implements per the Execution plan in *Implementation
Notes → Order of operations*; **`android-ui-designer`** sanity-checks the
placeholder palette's contrast pass on the Redmi 15 / emulator;
**`kotlin-reviewer`** reads the resulting Kotlin for theme hygiene
(no raw literals outside `presentation/theme/`, no `dynamicColor`
re-introductions, conformance with A1–A14).

### Why the architect review was needed (and is now complete)

Seven decisions in *Open Questions* propagate to *every* composable Curro
will ever ship:
- **Q1** (`object` vs `CompositionLocal` for spacing) touches every
  consumer's syntax — every `Modifier.padding(...)` call in the launcher
  home, every overlay, every config menu row.
- **Q2** (`object Dimens` vs `Modifier` extension) shapes how every shared
  big component spells "≥ 96 dp"; also pins `Dimens.CardElevation` so the
  `presentation/common/` detekt-exclude stays narrow.
- **Q3** (`MicButtonMinHeightFraction` placement) sets the contract SF-1.3's
  `MicButton` consumes.
- **Q4** (placeholder palette shape) is read once but visible everywhere
  until US-005 lands; the cool-grey-ramp-with-indigo-`primary` decision
  enforces contrast exercising for `primary`/`onPrimary` *before* the brand
  palette arrives.
- **Q5** (structural enforcement of `dynamicColor = false`) determines how
  hard it is to accidentally re-enable user-wallpaper colouring;  the
  triple-guard (missing parameter + missing import + verbatim KDoc + grep AC)
  is locked.
- **Q6** (which shared big components ship in US-004 vs SF-0.5) bounds the
  scope here.
- **Q7** (Hilt for the theme) closes a recurring "should we make this
  injectable?" question — answer is no.

Each is mechanical to implement and **hard to reverse after Phase 1 + 2 + 4
land** — every reverse-decision would mean re-touching every composable
(see A14 for the reversibility table). US-002's Q1–Q5 pattern (architect
resolves up-front; developer implements the resolved choice) saved real
refactoring downstream; US-004 has at least as large a propagation surface,
so the architect pass was more, not less, warranted.

**Architect involvement — status: complete.** Q1–Q7 resolved; A1–A14 added.
**No further architect review is required before `/implement-feature
US-004`.** If the developer hits a concrete obstacle implementing one of
the resolved choices (e.g. a placeholder hex that fails contrast against
A4 even after iteration; a detekt rule that fires unexpectedly on the
theme module and resists the per-rule exclude policy in A8), the developer
escalates back to the architect for a re-review rather than silently
flipping the choice.

PM precedent (US-001's "build-system review" appendix; US-002's Q1–Q5 +
A1–A11 appendices) — same shape applies here.

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
| 2026-05-13 | Claude `android-architect` | Architecture review: resolved Q1 (plain `object CurroSpacing`, 7-step `none/xs/s/m/l/xl/xxl` — confirmed PM), Q2 (`object Dimens`, with `Dimens.CardElevation = 2.dp` added so `BigCard` carries no raw `.dp` literals — extended PM), Q3 (keep `MicButtonMinHeightFraction` on `Dimens` — confirmed PM), Q4 (cool-grey placeholder ramp with desaturated-indigo `primary` and Material-red `error` — narrowed PM's "greyscale" to a specific shape; rejected muted-tint), Q5 (PM's option 2 + `grep -rn "dynamicColor"` returns zero matches + verbatim KDoc — extended PM), Q6 (ship `BigPrimaryButton` + `BigCard`; defer `BigYesNoRow` + `BigListRow` to SF-0.5 — confirmed PM with explicit reasoning that NO's visual treatment is a US-005 brand call), Q7 (no Hilt module for the theme — confirmed PM). Added *Architect's notes & decisions (A1–A14)*: 7-step spacing naming, `Dimens` vs theme-extension framing, mic-fraction floor semantics, contrast-floor table, `surface == background` + `error`-is-only-red rules, `fontScale` operationalisation, `dynamicColor` triple-guard, detekt-exclude exactness, theme-in-tests pattern, haptic type pin, `BigCard` clickable contract, cold-launch splash via `windowBackground` alignment (Option A; `installSplashScreen()` deferred), downstream hand-offs, reversibility table. Added *Owner split* section. Updated *Why the architect review is recommended* → *Why the architect review was needed (and is now complete)*; appended A1–A14 cross-references throughout the Q-Resolved blocks. |
