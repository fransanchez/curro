package com.curro.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Curro brand palette — "Sol y olivar" (warm-Mediterranean: terracota / cream / olive / ochre / wine
 * red). Locked by US-005 (SF-0.7). Source of truth: docs/briefs/US-005-brand-design-fillin.md and
 * the `brand-design` skill.
 *
 * The primitive atoms (all private) are named after their brand role, not their sRGB position.
 * [LightColors] ("Sol y olivar") and [DarkColors] ("Olivar de noche") assemble them into M3
 * [ColorScheme] instances; the M3 role names are not negotiable (US-004 A5).
 *
 * ## Measured contrast ratios (WCAG 2.x sRGB → relative-luminance formula)
 *
 * All ratios measured with the standard formula: L = 0.2126 R + 0.7152 G + 0.0722 B (linearised).
 * Values marked "†" diverge > 0.3 from the brief's originally claimed numbers — measured values are
 * used here as they are derived directly from the hex codes.
 *
 * | Pairing                           | Light ratio | Dark ratio | Floor                  |
 * |-----------------------------------|-------------|------------|------------------------|
 * | onSurface / surface               | 17.3:1      | 16.0:1     | ≥ 4.5 body             |
 * | onBackground / background         | 17.3:1      | 16.0:1     | ≥ 4.5 body             |
 * | onPrimary / primary               | ~6.5:1      | ~9.1:1 †   | ≥ 3.0 (button/UI role) |
 * | onSecondary / secondary           | ~6.8:1      | ~9.0:1 †   | ≥ 3.0 (button/UI role) |
 * | onTertiary / tertiary             | ~6.9:1      | ~8.9:1 †   | ≥ 3.0 (button/UI role) |
 * | onSurfaceVariant / surfaceVariant | ~14.6:1 †   | ~13.9:1 †  | ≥ 4.5 body             |
 * | error / surface                   | ~7.6:1      | ~10.9:1    | ≥ 3.0 (UI role)        |
 * | outline / surface                 | ~4.7:1      | ~6.4:1 †   | ≥ 3.0 UI               |
 *
 * † Diverges > 0.3 from brief's originally-claimed value. The hexes are used verbatim per
 *   "Option B" (approved by the user): keep the PM's proposed hexes, document actual ratios.
 *
 * ## Contract rules (US-004 A5 — binding)
 *
 * - [LightColors].surface == [LightColors].background (visual flatness — "feels the same every day")
 * - [DarkColors].surface  == [DarkColors].background
 * - [error] is the ONLY red in the system — no competing red in primary/secondary/tertiary
 * - The three button-fill role pairings (onPrimary/primary, onSecondary/secondary,
 *   onTertiary/tertiary) target ≥ 3:1 WCAG AA large/UI, not the 7:1 body aspirational —
 *   they never render body text against their on* colour.
 *
 * ## ListeningTint (Curro extension — not an M3 ColorScheme slot)
 *
 * No M3 slot fits "tint applied while listening" (spec §11 "se vuelve azul claro"). Rather than
 * shoehorning it into an unrelated M3 role, it is exposed as two top-level vals below [DarkColors].
 * See [CurroListeningTintLight] / [CurroListeningTintDark].
 */

private val TerracotaLight = Color(0xFF9A3E15) // primary light — terracota, the SÍ colour

// Cream (onPrimary, onSecondary, onTertiary, onError, surface, background in light)
private val CreamWhite = Color(0xFFFFF8EE)

// Cream variant (surfaceVariant in light)
private val CreamVariant = Color(0xFFF0E5D0)

// Primary container — pale terracota tint
private val PrimaryContainerLight = Color(0xFFFFD9C2)

// On primary container — deep warm brown
private val OnPrimaryContainerLight = Color(0xFF3A1700)

// Olive (secondary in light) — the NO colour; calm rejection, not alarming
private val OliveLight = Color(0xFF4F5D2E)

// Secondary container — pale olive tint
private val SecondaryContainerLight = Color(0xFFDDE5C8)

// On secondary container — deep warm green-black
private val OnSecondaryContainerLight = Color(0xFF1A2300)

// Ochre (tertiary in light) — sun / warmth accent
private val OchreLight = Color(0xFF7A4D00)

// Tertiary container — pale golden tint
private val TertiaryContainerLight = Color(0xFFFFE2B0)

// On tertiary container — deep ochre-brown
private val OnTertiaryContainerLight = Color(0xFF3A2400)

// Wine red (error in light) — reserved exclusively for genuine failures
private val WineLight = Color(0xFFA11414)

// Error container — pale blush tint
private val ErrorContainerLight = Color(0xFFFCDAD6)

// On error container — deep wine
private val OnErrorContainerLight = Color(0xFF410E0B)

// Dark text for cream surfaces
private val DarkText = Color(0xFF1A1410)

// Outline — warm mid-grey
private val OutlineLight = Color(0xFF7A6E5C)

// Outline variant — divider; visible at distance
private val OutlineVariantLight = Color(0xFFD8CCB6)

// Deep warm brown (background / surface in dark)
private val DeepBrown = Color(0xFF1A120D)

// Slightly lighter warm brown (surfaceVariant in dark)
private val DeepBrownVariant = Color(0xFF2A1F17)

// Bright terracota for dark surfaces (primary in dark)
private val TerracotaDark = Color(0xFFFFB088)

// Primary container in dark — muted terracota
private val PrimaryContainerDark = Color(0xFF7A2D08)

// On primary container in dark — pale terracota
private val OnPrimaryContainerDark = Color(0xFFFFD9C2)

// Bright olive for dark surfaces (secondary in dark)
private val OliveDark = Color(0xFFBAC68E)

// Secondary container in dark — dark olive
private val SecondaryContainerDark = Color(0xFF3A4520)

// On secondary container in dark — pale sage
private val OnSecondaryContainerDark = Color(0xFFDDE5C8)

// Bright ochre for dark surfaces (tertiary in dark)
private val OchreDark = Color(0xFFF5C078)

// Tertiary container in dark — deep ochre
private val TertiaryContainerDark = Color(0xFF5C3800)

// On tertiary container in dark — pale gold
private val OnTertiaryContainerDark = Color(0xFFFFE2B0)

// Bright salmon (error in dark) — fixes US-004 dark error/surface ~3.3:1 → ~10.9:1
private val WineDark = Color(0xFFFFB4AB)

// Error container in dark — dark crimson
private val ErrorContainerDark = Color(0xFF93000A)

// On error container in dark — pale blush
private val OnErrorContainerDark = Color(0xFFFFDAD6)

// Light cream text for dark surfaces — slightly warmer than pure white
private val LightText = Color(0xFFFFEBD9)

// On-error text for dark — deep crimson
private val OnErrorDark = Color(0xFF690005)

// Outline — warm mid-beige
private val OutlineDark = Color(0xFFA8957D)

// Outline variant — dark divider
private val OutlineVariantDark = Color(0xFF4A3C2E)

val LightColors: ColorScheme =
    lightColorScheme(
        primary = TerracotaLight,
        onPrimary = CreamWhite,
        primaryContainer = PrimaryContainerLight,
        onPrimaryContainer = OnPrimaryContainerLight,
        secondary = OliveLight,
        onSecondary = CreamWhite,
        secondaryContainer = SecondaryContainerLight,
        onSecondaryContainer = OnSecondaryContainerLight,
        tertiary = OchreLight,
        onTertiary = CreamWhite,
        tertiaryContainer = TertiaryContainerLight,
        onTertiaryContainer = OnTertiaryContainerLight,
        background = CreamWhite,
        onBackground = DarkText,
        surface = CreamWhite,
        onSurface = DarkText,
        surfaceVariant = CreamVariant,
        onSurfaceVariant = DarkText,
        error = WineLight,
        onError = CreamWhite,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
        scrim = Color.Black,
        inverseSurface = DarkText,
        inverseOnSurface = CreamWhite,
        inversePrimary = TerracotaDark,
        surfaceTint = TerracotaLight,
    )

val DarkColors: ColorScheme =
    darkColorScheme(
        primary = TerracotaDark,
        // OnPrimaryContainerLight = #3A1700 — deep warm brown on bright terracota
        onPrimary = OnPrimaryContainerLight,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        secondary = OliveDark,
        // OnSecondaryContainerLight = #1A2300 — deep green-black on bright olive
        onSecondary = OnSecondaryContainerLight,
        secondaryContainer = SecondaryContainerDark,
        onSecondaryContainer = OnSecondaryContainerDark,
        tertiary = OchreDark,
        // OnTertiaryContainerLight = #3A2400 — deep ochre-brown on bright ochre
        onTertiary = OnTertiaryContainerLight,
        tertiaryContainer = TertiaryContainerDark,
        onTertiaryContainer = OnTertiaryContainerDark,
        background = DeepBrown,
        onBackground = LightText,
        surface = DeepBrown,
        onSurface = LightText,
        surfaceVariant = DeepBrownVariant,
        onSurfaceVariant = LightText,
        error = WineDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        scrim = Color.Black,
        inverseSurface = LightText,
        inverseOnSurface = DeepBrown,
        inversePrimary = TerracotaLight,
        surfaceTint = TerracotaDark,
    )

// ListeningTint — Curro extension (not part of M3 ColorScheme).
// The light-blue overlay applied while the FSM is in the `listening` state (spec §11 "se vuelve
// azul claro"). No M3 ColorScheme slot fits this concept, so it is a deliberate Curro extension —
// two standalone vals rather than a CompositionLocal or a wrapper object, to keep the reading
// site (`CurroListeningTintLight`) as direct as `MaterialTheme.colorScheme.primary`.
// Consumed by the ListeningOverlay composable (Phase 5).
// Chosen desaturated rather than vivid: a saturated sky-blue would visually clash with the warm
// cream palette; the muted tones harmonise while still delivering the "screen turned blue" signal.
// Contrast of live-transcription text on these tints:
//   Light: onSurface (#1A1410) on #B8D4E8 ≈ 11.8:1
//   Dark:  onSurface (#FFEBD9) on #1A2A38 ≈ 12.7:1

/** Dusty pale-blue overlay — `listening` state in light mode (spec §11). */
val CurroListeningTintLight: Color = Color(0xFFB8D4E8)

/** Deep dusty-blue overlay — `listening` state in dark mode (spec §11). */
val CurroListeningTintDark: Color = Color(0xFF1A2A38)
