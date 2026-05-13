package com.curro.app.presentation.config

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Phase-0 placeholder for the Fran-only config menu. SF-8.x fills it with
 * the real sections (aliases / favourite apps / TTS voice & rate /
 * confidence thresholds / "always confirm" toggle / failed-commands log /
 * "send failures to Fran" toggle / reset learning / version &
 * diagnostics — spec §9). For now it's a stub that proves the nav route
 * is reachable.
 *
 * **This screen will be replaced at SF-8.1**, not deleted — the file
 * name will move to `ConfigMenuScreen.kt` with a real `ConfigViewModel`,
 * but US-007's placeholder is the structural precedent for the back
 * chevron + Box-overlay pattern (`navigation-patterns`'s rule 1
 * back-navigation shape).
 *
 * Senior-first contract:
 * - Title at [MaterialTheme.typography.titleLarge] (US-005's
 *   22 sp SemiBold).
 * - Back chevron in a [Dimens.MinTapTarget] × [Dimens.MinTapTarget]
 *   IconButton wrapping a [Dimens.LargeIconSize] glyph
 *   (`navigation-patterns` rule 1 sizing).
 *
 * No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()` —
 * [CurroNavHost]'s `Scaffold` already pads (No-Double-Padding rule).
 * Back navigation = the overlay chevron; the system back action also
 * pops (Navigation Compose default behaviour).
 */
@Composable
fun ConfigMenuPlaceholderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Centred title.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.config_placeholder_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // Back chevron at TopStart — navigation-patterns' canonical pattern.
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = CurroSpacing.s, top = CurroSpacing.s)
                    .size(Dimens.MinTapTarget),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier.size(Dimens.LargeIconSize),
            )
        }
    }
}

@Preview(name = "ConfigMenuPlaceholder — Light", widthDp = 412, heightDp = 800)
@Composable
private fun ConfigMenuPlaceholderLightPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigMenuPlaceholderScreen(onBack = {})
        }
    }
}

@Preview(
    name = "ConfigMenuPlaceholder — Dark",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun ConfigMenuPlaceholderDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigMenuPlaceholderScreen(onBack = {})
        }
    }
}

@Preview(name = "ConfigMenuPlaceholder — Large Font", widthDp = 412, heightDp = 800, fontScale = 1.5f)
@Composable
private fun ConfigMenuPlaceholderLargeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigMenuPlaceholderScreen(onBack = {})
        }
    }
}

@Preview(name = "ConfigMenuPlaceholder — Huge Font (senior-first)", widthDp = 412, heightDp = 800, fontScale = 2.0f)
@Composable
private fun ConfigMenuPlaceholderHugeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigMenuPlaceholderScreen(onBack = {})
        }
    }
}
