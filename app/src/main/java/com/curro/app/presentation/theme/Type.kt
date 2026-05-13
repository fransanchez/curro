package com.curro.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Curro's senior-first typography scale.
 *
 * Placeholder values — replaced by the real Curro type scale in US-005 (SF-0.7)
 * without touching any composable. The senior-first floor (per-role minimum sp)
 * documented in the table below is the contract — US-005 may exceed it; it may
 * not undercut it.
 *
 * Font family: [FontFamily.Default] (system font) for US-004. US-005 may bundle a
 * custom font; nothing here needs to change when it does.
 *
 * Senior-first floor table (binding contract — US-005 may raise, never lower):
 *
 * | Role           | Floor  | FontWeight   | Curro usage                                     |
 * |----------------|--------|--------------|--------------------------------------------------|
 * | displayLarge   | 64 sp  | ExtraBold    | Launcher clock                                   |
 * | displayMedium  | 48 sp  | Bold         | Overlay headlines ("Te escucho…")                |
 * | displaySmall   | 40 sp  | Bold         | Less used                                        |
 * | headlineLarge  | 32 sp  | Bold         | Screen titles, sender names on cards             |
 * | headlineMedium | 28 sp  | SemiBold     | Card titles, list-row primary text               |
 * | headlineSmall  | 24 sp  | SemiBold     | Less used                                        |
 * | titleLarge     | 22 sp  | SemiBold     | Sub-sections, button labels                      |
 * | titleMedium    | 20 sp  | Medium       | Sub-sections                                     |
 * | titleSmall     | 18 sp  | Medium       | Rare                                             |
 * | bodyLarge      | 20 sp  | Normal       | Body text — message bodies, prompts              |
 * | bodyMedium     | 18 sp  | Normal       | Secondary text                                   |
 * | bodySmall      | 16 sp  | Normal       | Floor; Curro almost never goes below this        |
 * | labelLarge     | 18 sp  | SemiBold     | Button/chip text (only "small" role used)        |
 * | labelMedium    | 16 sp  | SemiBold     | Rare                                             |
 * | labelSmall     | 14 sp  | Medium       | Avoid                                            |
 *
 * Compare against M3 defaults: M3 bodyLarge = 16 sp; Curro's floor = 20 sp (+25%).
 * M3 displayLarge = 57 sp; Curro's floor = 64 sp.
 *
 * PLACEHOLDER (US-005) — all TextStyle values inside the constructor below.
 */
val CurroTypography: Typography =
    Typography(
        // Launcher clock
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 64.sp,
            ),
        // Overlay headlines ("Te escucho…")
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
            ),
        // Screen titles, sender names on cards
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            ),
        // Card titles, list-row primary text
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
            ),
        // Sub-sections, button labels
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            ),
        // Sub-sections
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
            ),
        // Rare
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
            ),
        // Body text ("body that reads like a headline")
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
            ),
        // Secondary text
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
            ),
        // Floor; Curro almost never goes below this
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
        // Button/chip text
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
        // Rare
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            ),
        // Avoid
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
    )
