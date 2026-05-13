package com.curro.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Stub — full senior-first theme arrives in SF-0.4.
 *
 * SF-0.4 replaces this with the real CurroTheme backed by:
 *   - CurroColorScheme  (fixed palette, dynamicColor = false, ≥ 7:1 contrast)
 *   - CurroTypography   (text sizes well above Material defaults for Fran's father)
 *   - CurroShapes       (large, friendly shapes)
 *   - CurroSpacing      (≥ 96 dp tap targets throughout)
 *
 * The stub delegates to MaterialTheme so the app compiles and boots without any
 * senior-first tokens defined yet. No code outside this file should reference
 * Color(0xFF…), raw .sp, or raw .dp — those belong here once SF-0.4 lands.
 *
 * TODO(SF-0.4): replace with the real CurroTheme + senior-first tokens.
 */
@Composable
fun CurroTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
