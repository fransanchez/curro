package com.curro.app.presentation.launcher

import android.Manifest
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.curro.app.R
import com.curro.app.domain.model.ClockState
import com.curro.app.presentation.assistant.ListeningOverlay
import com.curro.app.presentation.common.BigPrimaryButton
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Phase-1 launcher home (SF-1.1/US-009 through SF-1.6/US-014) extended in SF-2.3
 * (US-017) with the listening-overlay wrapper.
 *
 * **Layout order (top → bottom):**
 * 1. [ClockBlock] — live time + date (SF-1.2); every tap feeds the five-tap counter (SF-1.6).
 * 2. [BigPrimaryButton] "Hazme tu pantalla de inicio" — visible only when `!isCurroDefault`.
 * 3. [MicButton] — the dominant launcher surface; SF-2.3 wires it to the voice loop, and
 *    SF-2.4 swaps its colour to olive while [LauncherUiState.listeningState] is non-Idle.
 * 4. [AppTileGrid] — four static favourite-app tiles (SF-1.4).
 * 5. "Más apps" [BigPrimaryButton] — opens the full app list (SF-1.5).
 *
 * SF-2.3 (US-017) **wraps** the column body inside a [Box] and overlays the
 * [ListeningOverlay] via [AnimatedVisibility] when `listeningState !is Idle`. The
 * overlay covers `fillMaxSize`; no layout shift on the underlying column.
 *
 * SF-2.3 also registers a [rememberLauncherForActivityResult] for
 * [ActivityResultContracts.RequestPermission] on `RECORD_AUDIO`; the
 * [LauncherSideEffect.RequestRecordAudio] side effect triggers it.
 *
 * @param onOpenConfig Opens the config menu (wired in CurroNavHost).
 * @param onMakeDefault Fires the role-request / settings fallback flow.
 * @param onNavigateToMoreApps Navigates to the "Más apps" screen.
 */
@Composable
fun LauncherPlaceholderScreen(
    onOpenConfig: () -> Unit,
    onMakeDefault: () -> Unit,
    onNavigateToMoreApps: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LauncherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SF-2.3 (US-017) — runtime permission launcher for RECORD_AUDIO.
    // Result is dispatched back as a LauncherEvent so the ViewModel owns the state machine.
    val recordAudioLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            viewModel.onEvent(LauncherEvent.RecordAudioPermissionResult(granted))
        }

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is LauncherSideEffect.ShowToast ->
                    Toast.makeText(context, effect.messageResId, Toast.LENGTH_SHORT).show()
                is LauncherSideEffect.LaunchApp -> {
                    val intent = context.packageManager.getLaunchIntentForPackage(effect.packageName)
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                }
                is LauncherSideEffect.OpenConfig -> onOpenConfig()
                is LauncherSideEffect.RequestRecordAudio ->
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LauncherPlaceholderContent(
        uiState = uiState,
        onMakeDefault = onMakeDefault,
        onMicPressed = { viewModel.onEvent(LauncherEvent.MicPressed) },
        onClockTapped = { viewModel.onEvent(LauncherEvent.ClockTapped) },
        onTileTapped = { pkg -> viewModel.onEvent(LauncherEvent.AppTileTapped(pkg)) },
        onNotInstalled = {
            Toast.makeText(context, R.string.copy_app_not_installed, Toast.LENGTH_SHORT).show()
        },
        onNavigateToMoreApps = onNavigateToMoreApps,
        modifier = modifier,
    )
}

/**
 * Stateless content composable for [LauncherPlaceholderScreen].
 *
 * Receives [uiState] and emits events via lambdas — no ViewModel reference, no side effects.
 * Previews target this directly with hard-coded state.
 */
@Composable
internal fun LauncherPlaceholderContent(
    uiState: LauncherUiState,
    onMakeDefault: () -> Unit,
    onMicPressed: () -> Unit,
    onClockTapped: () -> Unit,
    onTileTapped: (String) -> Unit = {},
    onNotInstalled: () -> Unit = {},
    onNavigateToMoreApps: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. SF-1.2: live clock + date. Every tap feeds the SF-1.6 five-tap counter.
            ClockBlock(
                clockState = uiState.clock,
                onClockTapped = onClockTapped,
            )

            Spacer(modifier = Modifier.height(CurroSpacing.xxl))

            // 2. SF-1.1 CTA — visible only when Curro is NOT the resolved default home.
            if (!uiState.isCurroDefault) {
                BigPrimaryButton(
                    text = stringResource(R.string.copy_home_make_default),
                    onClick = onMakeDefault,
                    modifier = Modifier.padding(horizontal = CurroSpacing.l),
                )
                Spacer(modifier = Modifier.height(CurroSpacing.l))
            }

            // 3. SF-1.3 + SF-2.4: main mic button. isListening swaps the colour to olive
            // while the voice session is active.
            MicButton(
                onPressed = onMicPressed,
                isListening = uiState.listeningState !is ListeningState.Idle,
                modifier = Modifier.padding(horizontal = CurroSpacing.l),
            )

            Spacer(modifier = Modifier.height(CurroSpacing.l))

            // 4. SF-1.4: static favourite-apps 2×2 grid.
            if (uiState.favorites.isNotEmpty()) {
                AppTileGrid(
                    favorites = uiState.favorites,
                    onTileTapped = onTileTapped,
                    onNotInstalled = onNotInstalled,
                    modifier = Modifier.padding(horizontal = CurroSpacing.l),
                )
                Spacer(modifier = Modifier.height(CurroSpacing.l))
            }

            // 5. SF-1.5: "Más apps" button — opens the full installed-app list.
            BigPrimaryButton(
                text = stringResource(R.string.copy_home_more_apps),
                onClick = onNavigateToMoreApps,
                modifier = Modifier.padding(horizontal = CurroSpacing.l),
            )
        }

        // SF-2.3 (US-017) + SF-2.4 (US-018): the listening overlay covers the launcher
        // home while any non-Idle listening state is active. fadeIn/fadeOut tween(150)
        // matches spec §11 "single ~150 ms fade".
        AnimatedVisibility(
            visible = uiState.listeningState !is ListeningState.Idle,
            enter = fadeIn(animationSpec = tween(OVERLAY_FADE_MS)),
            exit = fadeOut(animationSpec = tween(OVERLAY_FADE_MS)),
        ) {
            ListeningOverlay(
                state = uiState.listeningState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val OVERLAY_FADE_MS = 150

// --- Previews (8 total: 4 variants × 2 isCurroDefault states) ---

private val previewClockState = ClockState(timeText = "12:47", dateText = "Miércoles 13 mayo")

@Preview(name = "Launcher — Light, CTA visible", widthDp = 412, heightDp = 800)
@Composable
private fun LauncherLightCtaVisiblePreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderContent(
                uiState = LauncherUiState(isCurroDefault = false, clock = previewClockState),
                onMakeDefault = {},
                onMicPressed = {},
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
                onMakeDefault = {},
                onMicPressed = {},
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
                onMakeDefault = {},
                onMicPressed = {},
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
                onMakeDefault = {},
                onMicPressed = {},
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
                onMakeDefault = {},
                onMicPressed = {},
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
                onMakeDefault = {},
                onMicPressed = {},
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
                onMakeDefault = {},
                onMicPressed = {},
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
                onMakeDefault = {},
                onMicPressed = {},
                onClockTapped = {},
            )
        }
    }
}

// Listening-state preview lives on ListeningOverlay itself (US-018) — keeping it here
// would push this file over detekt's TooManyFunctions threshold (11). Reviewers can
// preview the overlay in isolation from ListeningOverlay.kt previews.
