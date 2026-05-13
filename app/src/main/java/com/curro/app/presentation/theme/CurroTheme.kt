package com.curro.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// Deliberately NOT imported (Q5 / A7 — structural enforcement of no dynamic colour):
//   - androidx.compose.material3.dynamicLightColorScheme
//   - androidx.compose.material3.dynamicDarkColorScheme
// Re-enabling dynamic colour requires (1) adding an import, (2) adding a parameter,
// AND (3) wiring through MainActivity — three deliberate code changes, not a parameter flip.

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
 * call site through MainActivity — three deliberate code changes, not a
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
