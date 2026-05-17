package com.curro.app.presentation.config.sections

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
 * Shared placeholder destination for the seven config sub-sections that are
 * not yet implemented (SF-8.1 / US-050). Each later SF replaces the
 * `composable("config/<name>")` block in [CurroNavHost] with the real screen
 * — this composable is deleted when the last placeholder is replaced.
 *
 * Layout follows the same back-chevron + Box-overlay pattern as
 * `ConfigMenuPlaceholderScreen` (the Phase-0 ancestor, now deleted).
 *
 * No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()` — the parent
 * `CurroNavHost`'s Scaffold provides `Modifier.padding(innerPadding)`
 * (No-Double-Padding rule).
 *
 * @param onBack Pops the nav back stack.
 */
@Composable
fun ConfigSectionPlaceholder(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.copy_config_section_placeholder),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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

@Preview(name = "ConfigSectionPlaceholder — Light", widthDp = 412, heightDp = 800)
@Composable
private fun ConfigSectionPlaceholderLightPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigSectionPlaceholder(onBack = {})
        }
    }
}

@Preview(name = "ConfigSectionPlaceholder — Dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 800)
@Composable
private fun ConfigSectionPlaceholderDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigSectionPlaceholder(onBack = {})
        }
    }
}

@Preview(name = "ConfigSectionPlaceholder — Large Font", widthDp = 412, heightDp = 800, fontScale = 1.5f)
@Composable
private fun ConfigSectionPlaceholderLargeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigSectionPlaceholder(onBack = {})
        }
    }
}

@Preview(name = "ConfigSectionPlaceholder — Huge Font", widthDp = 412, heightDp = 800, fontScale = 2.0f)
@Composable
private fun ConfigSectionPlaceholderHugeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigSectionPlaceholder(onBack = {})
        }
    }
}
