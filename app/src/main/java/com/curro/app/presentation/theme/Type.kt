package com.curro.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Curro's senior-first typography scale. Locked by US-005 (SF-0.7).
 *
 * Font family: [FontFamily.Default] (system font) — bundling a custom typeface is a future asset SF.
 *
 * ## Senior-first floors (US-004 A1 — binding contract: US-005 may raise, never lower)
 *
 * Every role is at or above its floor. Line heights are explicit — generous leading matters at
 * large sizes for elderly readability (US-005 addition over US-004 which left M3 defaults).
 *
 * | Role           | sp | FontWeight | lineHeight (sp) | Floor (sp) | Curro usage                            |
 * |----------------|----|------------|-----------------|------------|----------------------------------------|
 * | displayLarge   | 72 | ExtraBold  | 80              | ≥ 64       | Launcher clock (bumped from 64 sp floor)  |
 * | displayMedium  | 48 | Bold       | 56              | ≥ 48       | Overlay headlines ("Te escucho…")      |
 * | displaySmall   | 40 | Bold       | 48              | ≥ 40       | Rarely used                            |
 * | headlineLarge  | 32 | Bold       | 40              | ≥ 32       | Screen titles, sender names on cards   |
 * | headlineMedium | 28 | SemiBold   | 36              | ≥ 28       | Card titles, list-row primary text     |
 * | headlineSmall  | 24 | SemiBold   | 32              | ≥ 24       | Less used                              |
 * | titleLarge     | 22 | SemiBold   | 28              | ≥ 22       | Sub-sections, short button labels      |
 * | titleMedium    | 20 | Medium     | 26              | ≥ 20       | Sub-sections                           |
 * | titleSmall     | 18 | Medium     | 24              | ≥ 18       | Rare                                   |
 * | bodyLarge      | 20 | Normal     | 28              | ≥ 20       | Body text — message bodies, prompts    |
 * | bodyMedium     | 18 | Normal     | 26              | ≥ 18       | Secondary text                         |
 * | bodySmall      | 16 | Normal     | 24              | ≥ 16       | Floor; Curro almost never goes below   |
 * | labelLarge     | 18 | SemiBold   | 24              | ≥ 18       | Button text (SÍ / NO / "Más apps")     |
 * | labelMedium    | 16 | SemiBold   | 22              | ≥ 16       | Rare                                   |
 * | labelSmall     | 14 | Medium     | 20              | ≥ 14       | Avoid                                  |
 *
 * Compare M3 defaults: M3 `bodyLarge` = 16 sp; Curro = 20 sp (+25 %).
 * M3 `displayLarge` = 57 sp; Curro = 72 sp (+26 %).
 *
 * ## displayLarge 72 sp rationale
 *
 * The launcher clock is THE focal point of the home screen. 72 sp at `fontScale = 2.0` = 144 sp,
 * still fits `HH:MM` (5 glyphs) on the Redmi 15's 412 dp portrait width. The US-004 floor was
 * 64 sp; US-005 bumps it to 72 sp as the brand's approved value.
 *
 * ## respectAndAmplify (US-004 A6)
 *
 * `fontScale` is NEVER capped. Compose's [androidx.compose.ui.unit.Density] applies the system
 * font-scale natively when sp values are converted to px; the layout must survive 1.5× and 2.0×.
 * Every reusable component should be previewed at those scales (see `accessibility-patterns`).
 * The lower bound clamp is `fontScale = 1.0` (no shrinking below the base scale) — the system
 * enforces this; Curro does not artificially override it.
 */
val CurroTypography: Typography =
    Typography(
        // Launcher clock — the focal point of home; bumped from US-004 floor 64 sp to 72 sp
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 72.sp,
                lineHeight = 80.sp,
            ),
        // Overlay headlines ("Te escucho…")
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 48.sp,
            ),
        // Screen titles, sender names on cards
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        // Card titles, list-row primary text — the most common large-text role
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        // Sub-sections, short button labels
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        // Sub-sections
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        // Rare
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
        // Body text ("body that reads like a headline") — message bodies, prompts
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
        // Secondary text
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        // Floor; Curro almost never goes below this
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        // Button text — the SÍ / NO / "Más apps" labels
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
        // Rare
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        // Avoid
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
    )
