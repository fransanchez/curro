package com.curro.app.presentation.launcher

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.curro.app.R
import com.curro.app.domain.model.ClockState
import com.curro.app.presentation.common.BigPrimaryButton
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Phase-1 launcher home (SF-1.1 / US-009 + SF-1.2 / US-010).
 *
 * US-009 introduced [LauncherViewModel] and the "Hazme tu pantalla de inicio" CTA.
 * US-010 adds the live [ClockBlock] (time + date) at the top, replacing the Phase-0
 * "Curro listo" placeholder text.
 *
 * SF-1.3 → SF-1.5 add the mic button, app grid, and "Más apps" piecewise. The CTA and
 * clock block both survive into the real launcher home.
 *
 * No [androidx.compose.material3.Scaffold], no `TopAppBar`, no `statusBarsPadding()` —
 * [com.curro.app.presentation.navigation.CurroNavHost]'s [Scaffold] already pads
 * (No-Double-Padding rule, US-007 / CLAUDE.md "Screen Layout").
 *
 * @param onOpenConfig Opens the config menu (wired in [CurroNavHost]).
 * @param onMakeDefault Fires the role-request / settings fallback flow (wired in [CurroNavHost]).
 * @param onClockTapped Fires on every clock tap — SF-1.6 wires the five-tap gesture counter.
 *   [CurroNavHost] passes `{}` until SF-1.6 lands.
 * @param modifier Applied to the root [Box].
 * @param viewModel Injected via [hiltViewModel]; override in tests via Hilt test rules.
 */
@Composable
fun LauncherPlaceholderScreen(
    onOpenConfig: () -> Unit,
    onMakeDefault: () -> Unit,
    onClockTapped: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LauncherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LauncherPlaceholderContent(
        uiState = uiState,
        onOpenConfig = onOpenConfig,
        onMakeDefault = onMakeDefault,
        onClockTapped = onClockTapped,
        modifier = modifier,
    )
}

/**
 * Stateless content composable for [LauncherPlaceholderScreen].
 *
 * Receives [uiState] and emits [onOpenConfig] / [onMakeDefault] / [onClockTapped].
 * Previews target this directly with hard-coded state — no fake ViewModel needed.
 *
 * **Layout (top → bottom):**
 * 1. [ClockBlock] — live time + date; taps forwarded to [onClockTapped] (SF-1.6 counter).
 * 2. SF-1.1 CTA — [BigPrimaryButton] `copy_home_make_default`, visible only when
 *    `!uiState.isCurroDefault`. Disappears reactively when the detector re-emits `true`.
 * 3. Phase-0 debug affordance — a subdued [TextButton] opening the config menu until
 *    SF-1.6 wires the canonical 5-taps-on-clock gesture.
 */
@Composable
internal fun LauncherPlaceholderContent(
    uiState: LauncherUiState,
    onOpenConfig: () -> Unit,
    onMakeDefault: () -> Unit,
    onClockTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // SF-1.2: live clock + date — replaces the Phase-0 "Curro listo" text.
            ClockBlock(
                clockState = uiState.clock,
                onClockTapped = onClockTapped,
            )

            Spacer(modifier = Modifier.height(CurroSpacing.xxl))

            // SF-1.1 CTA — visible only when Curro is NOT the resolved default home.
            // Disappears reactively when the detector's flow re-emits `true` (post-
            // role-grant, on resume). Reappears if HyperOS resets the default after an
            // OS update — visible-affordance recovery path (`launcher-app` skill).
            if (!uiState.isCurroDefault) {
                BigPrimaryButton(
                    text = stringResource(R.string.copy_home_make_default),
                    onClick = onMakeDefault,
                    modifier = Modifier.padding(horizontal = CurroSpacing.l),
                )
                Spacer(modifier = Modifier.height(CurroSpacing.l))
            }

            // Phase-0 debug affordance — kept until SF-1.6 wires the canonical
            // 5-taps-on-clock gesture. Subdued TextButton styling makes its dev-only
            // nature explicit.
            TextButton(onClick = onOpenConfig) {
                Text(
                    text = stringResource(R.string.launcher_placeholder_open_config_debug),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// --- Previews (8 total: 4 variants × 2 isCurroDefault states) ---

private val previewClockState = ClockState(timeText = "12:47", dateText = "Miércoles 13 mayo")

@Preview(name = "Launcher — Light, CTA visible", widthDp = 412, heightDp = 800)
@Composable
private fun LauncherLightCtaVisiblePreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = false, clock = previewClockState),
                onOpenConfig = {},
                onMakeDefault = {},
                onClockTapped = {},
            )
        }
    }
}

@Preview(name = "Launcher — Light, CTA hidden", widthDp = 412, heightDp = 800)
@Composable
private fun LauncherLightCtaHiddenPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = true, clock = previewClockState),
                onOpenConfig = {},
                onMakeDefault = {},
                onClockTapped = {},
            )
        }
    }
}

@Preview(
    name = "Launcher — Dark, CTA visible",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun LauncherDarkCtaVisiblePreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = false, clock = previewClockState),
                onOpenConfig = {},
                onMakeDefault = {},
                onClockTapped = {},
            )
        }
    }
}

@Preview(
    name = "Launcher — Dark, CTA hidden",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun LauncherDarkCtaHiddenPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = true, clock = previewClockState),
                onOpenConfig = {},
                onMakeDefault = {},
                onClockTapped = {},
            )
        }
    }
}

@Preview(
    name = "Launcher — Large font (1.5×), CTA visible",
    widthDp = 412,
    heightDp = 800,
    fontScale = 1.5f,
)
@Composable
private fun LauncherLargeFontCtaVisiblePreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = false, clock = previewClockState),
                onOpenConfig = {},
                onMakeDefault = {},
                onClockTapped = {},
            )
        }
    }
}

@Preview(
    name = "Launcher — Large font (1.5×), CTA hidden",
    widthDp = 412,
    heightDp = 800,
    fontScale = 1.5f,
)
@Composable
private fun LauncherLargeFontCtaHiddenPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = true, clock = previewClockState),
                onOpenConfig = {},
                onMakeDefault = {},
                onClockTapped = {},
            )
        }
    }
}

@Preview(
    name = "Launcher — Huge font (2.0×, senior-first), CTA visible",
    widthDp = 412,
    heightDp = 800,
    fontScale = 2.0f,
)
@Composable
private fun LauncherHugeFontCtaVisiblePreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = false, clock = previewClockState),
                onOpenConfig = {},
                onMakeDefault = {},
                onClockTapped = {},
            )
        }
    }
}

@Preview(
    name = "Launcher — Huge font (2.0×, senior-first), CTA hidden",
    widthDp = 412,
    heightDp = 800,
    fontScale = 2.0f,
)
@Composable
private fun LauncherHugeFontCtaHiddenPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = true, clock = previewClockState),
                onOpenConfig = {},
                onMakeDefault = {},
                onClockTapped = {},
            )
        }
    }
}
