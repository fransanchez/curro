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
import com.curro.app.presentation.common.BigPrimaryButton
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Phase-1 placeholder for the launcher home (US-009 / SF-1.1).
 *
 * US-007 shipped this screen without a ViewModel (no state to manage). US-009 introduces
 * [LauncherViewModel] collecting [com.curro.app.data.launcher.DefaultLauncherDetector] and
 * the "Hazme tu pantalla de inicio" CTA that gates on its state.
 *
 * SF-1.2 → SF-1.5 replace this placeholder piecewise with the real launcher home (clock,
 * mic button, app grid, "Más apps"). The CTA landed here survives into the real launcher
 * home — it is a permanent visible-affordance recovery path for the HyperOS
 * "forgets the default after updates" reality (`launcher-app` skill § HyperOS).
 *
 * No [androidx.compose.material3.Scaffold], no `TopAppBar`, no `statusBarsPadding()` —
 * [com.curro.app.presentation.navigation.CurroNavHost]'s [Scaffold] already pads
 * (No-Double-Padding rule, US-007 / CLAUDE.md "Screen Layout").
 *
 * @param onOpenConfig Opens the config menu (wired in [com.curro.app.presentation.navigation.CurroNavHost]).
 * @param onMakeDefault Fires the role-request / settings fallback flow (wired in [CurroNavHost],
 *   not here — keeps this composable platform-side-effect-free).
 * @param modifier Applied to the root [Box].
 * @param viewModel Injected via [hiltViewModel]; override in tests via Hilt test rules.
 */
@Composable
fun LauncherPlaceholderScreen(
    onOpenConfig: () -> Unit,
    onMakeDefault: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LauncherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LauncherPlaceholderContent(
        uiState = uiState,
        onOpenConfig = onOpenConfig,
        onMakeDefault = onMakeDefault,
        modifier = modifier,
    )
}

/**
 * Stateless content composable for [LauncherPlaceholderScreen].
 *
 * Receives [uiState] and emits [onOpenConfig] / [onMakeDefault]. Previews target this
 * directly with hard-coded state — no fake ViewModel needed.
 *
 * The CTA ([BigPrimaryButton] rendering `copy_home_make_default`) is visible only when
 * `!uiState.isCurroDefault`; it disappears reactively when [LauncherViewModel.uiState]
 * recomputes after the detector's flow re-emits `true` on `ON_RESUME`.
 */
@Composable
internal fun LauncherPlaceholderContent(
    uiState: LauncherUiState,
    onOpenConfig: () -> Unit,
    onMakeDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.launcher_placeholder_title),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

@Preview(name = "Launcher — Light, CTA visible", widthDp = 412, heightDp = 800)
@Composable
private fun LauncherLightCtaVisiblePreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = false),
                onOpenConfig = {},
                onMakeDefault = {},
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
                uiState = LauncherUiState(isCurroDefault = true),
                onOpenConfig = {},
                onMakeDefault = {},
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
                uiState = LauncherUiState(isCurroDefault = false),
                onOpenConfig = {},
                onMakeDefault = {},
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
                uiState = LauncherUiState(isCurroDefault = true),
                onOpenConfig = {},
                onMakeDefault = {},
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
                uiState = LauncherUiState(isCurroDefault = false),
                onOpenConfig = {},
                onMakeDefault = {},
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
                uiState = LauncherUiState(isCurroDefault = true),
                onOpenConfig = {},
                onMakeDefault = {},
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
                uiState = LauncherUiState(isCurroDefault = false),
                onOpenConfig = {},
                onMakeDefault = {},
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
                uiState = LauncherUiState(isCurroDefault = true),
                onOpenConfig = {},
                onMakeDefault = {},
            )
        }
    }
}
