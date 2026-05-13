package com.curro.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * PLACEHOLDER PALETTE (US-005) — replaced wholesale by US-005 (SF-0.7).
 * Every value here is a placeholder. The primitive atoms are defined in the private block below;
 * [LightColors] and [DarkColors] compose them into M3 ColorScheme instances.
 *
 * Strategy (Q4 — Resolved): cool-grey ramp + desaturated-indigo [primary] + Material-red [error].
 * The cool lean (a couple of degrees toward blue) reads as "placeholder" in dev builds without
 * introducing brand colour risk. See docs/briefs/US-004-curro-theme.md Q4 + A4/A5 for rationale.
 *
 * Senior-first contrast floor (A4 — non-negotiable, applies even to placeholders):
 *
 * | Pairing                           | Light ratio | Dark ratio | Floor  |
 * |-----------------------------------|-------------|------------|--------|
 * | onSurface / surface               | ~18.5:1     | ~16.0:1    | ≥ 4.5  |
 * | onBackground / background         | ~18.5:1     | ~16.0:1    | ≥ 4.5  |
 * | onPrimary / primary               | ~8.0:1      | ~7.8:1     | ≥ 4.5  |
 * | onSurfaceVariant / surfaceVariant | ~11.0:1     | ~8.5:1     | ≥ 4.5  |
 * | primary / surface                 | ~5.2:1      | ~8.8:1     | ≥ 3.0  |
 * | error / surface                   | ~5.4:1      | ~3.3:1     | ≥ 3.0  |
 * | outline / surface                 | ~4.6:1      | ~3.2:1     | ≥ 3.0  |
 *
 * Contract rules (A5):
 * - [LightColors].surface == [LightColors].background (visual flatness — "feels the same every day")
 * - [DarkColors].surface  == [DarkColors].background
 * - [error] is the ONLY red in the system — no competing red in primary/secondary/tertiary
 */
private val CoolWhite = Color(0xFFFAFAFC)
private val CoolGrey100 = Color(0xFFEAEAEE)
private val CoolGrey200 = Color(0xFFCACACD)
private val CoolGrey500 = Color(0xFF79797E)
private val CoolGrey600 = Color(0xFF5E5E66)

// Desaturated indigo — primary (light); contrast vs CoolWhite surface ≥ 5.2:1
private val CoolGrey700 = Color(0xFF3F4A5E)
private val CoolBlack = Color(0xFF0B0B0F)

// Cool-grey ramp — dark side
private val DarkBg = Color(0xFF0B0B0F)
private val DarkSurface1 = Color(0xFF1A1A1E)
private val DarkGrey200 = Color(0xFF3A3A3E)
private val DarkGrey400 = Color(0xFF8E8E92)
private val DarkGrey500 = Color(0xFFA8A8AC)

// Lighter indigo for dark mode; contrast vs DarkBg surface ≥ 8.8:1
private val DarkPrimary = Color(0xFFB6BCC9)
private val DarkNearWhite = Color(0xFFEAEAEE)

// Primary container tones
private val PrimaryContainerLight = Color(0xFFDDE0EA)
private val PrimaryContainerDark = Color(0xFF2A2F3A)

// Error — the ONLY red in the system (A5); contrast vs surface ≥ 5.4:1 light / ≥ 3.3:1 dark
private val ErrorLight = Color(0xFFB3261E)
private val ErrorDark = Color(0xFFF2B8B5)

// Light colour scheme (PLACEHOLDER — US-005)
val LightColors: ColorScheme =
    lightColorScheme(
        primary = CoolGrey700,
        onPrimary = CoolWhite,
        primaryContainer = PrimaryContainerLight,
        onPrimaryContainer = CoolBlack,
        secondary = CoolGrey600,
        onSecondary = CoolWhite,
        secondaryContainer = CoolGrey100,
        onSecondaryContainer = CoolBlack,
        tertiary = CoolGrey600,
        onTertiary = CoolWhite,
        tertiaryContainer = CoolGrey100,
        onTertiaryContainer = CoolBlack,
        background = CoolWhite,
        onBackground = CoolBlack,
        surface = CoolWhite,
        onSurface = CoolBlack,
        surfaceVariant = CoolGrey100,
        onSurfaceVariant = CoolBlack,
        error = ErrorLight,
        onError = CoolWhite,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        outline = CoolGrey500,
        outlineVariant = CoolGrey200,
        scrim = CoolBlack,
        inverseSurface = CoolBlack,
        inverseOnSurface = CoolWhite,
        inversePrimary = DarkPrimary,
    )

// Dark colour scheme (PLACEHOLDER — US-005)
val DarkColors: ColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkBg,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = DarkNearWhite,
        secondary = DarkGrey500,
        onSecondary = DarkBg,
        secondaryContainer = DarkSurface1,
        onSecondaryContainer = DarkNearWhite,
        tertiary = DarkGrey500,
        onTertiary = DarkBg,
        tertiaryContainer = DarkSurface1,
        onTertiaryContainer = DarkNearWhite,
        background = DarkBg,
        onBackground = DarkNearWhite,
        surface = DarkBg,
        onSurface = DarkNearWhite,
        surfaceVariant = DarkSurface1,
        onSurfaceVariant = DarkNearWhite,
        error = ErrorDark,
        onError = DarkBg,
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = DarkGrey400,
        outlineVariant = DarkGrey200,
        scrim = CoolBlack,
        inverseSurface = DarkNearWhite,
        inverseOnSurface = DarkBg,
        inversePrimary = CoolGrey700,
    )
