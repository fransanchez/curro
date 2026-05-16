package com.curro.app.presentation.launcher

import android.Manifest
import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.curro.app.assistant.AssistantState
import com.curro.app.domain.model.ClockState
import com.curro.app.presentation.assistant.ConfirmationOverlay
import com.curro.app.presentation.assistant.ErrorRecoveryOverlay
import com.curro.app.presentation.assistant.ExecutingOverlay
import com.curro.app.presentation.assistant.ListeningOverlay
import com.curro.app.presentation.assistant.ProcessingOverlay
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

    // SF-4.10 (US-034) — runtime permission launcher for READ_CONTACTS.
    // Fired by the one-shot auto-retry path in the ViewModel when call_contact
    // returns ReadContactsPermissionMissing on the first attempt.
    val readContactsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            viewModel.onEvent(LauncherEvent.ReadContactsPermissionResult(granted))
        }

    // SF-4.10 (US-034) — runtime permission launcher for CALL_PHONE.
    // Fired by the one-shot auto-retry path when call_contact returns PermissionDenied.
    val callPhoneLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            viewModel.onEvent(LauncherEvent.CallPhonePermissionResult(granted))
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
                is LauncherSideEffect.ShowDebugJson -> Unit // SF-5.5: debug JSON surface removed
                // SF-4.6 (US-030) — deep-link to HyperOS notification-access settings.
                is LauncherSideEffect.OpenNotificationAccessSettings -> {
                    val intent =
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                // SF-4.10 (US-034) — one-shot contact / call permission requests.
                is LauncherSideEffect.RequestReadContacts ->
                    readContactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                is LauncherSideEffect.RequestCallPhone ->
                    callPhoneLauncher.launch(Manifest.permission.CALL_PHONE)
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
        onGrantNotifAccess = { viewModel.onEvent(LauncherEvent.GrantNotifAccessRequested) },
        onUserConfirmed = { viewModel.onEvent(LauncherEvent.UserConfirmed) },
        onUserRejected = { viewModel.onEvent(LauncherEvent.UserRejected) },
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
    onGrantNotifAccess: () -> Unit = {},
    onUserConfirmed: () -> Unit = {},
    onUserRejected: () -> Unit = {},
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
            // SF-4.6: these two CTAs never coexist (pinned per §10): if Curro isn't
            // the default launcher, only the "make default" CTA shows; once Curro is
            // default and needs notification access, only the "grant access" CTA shows.
            if (!uiState.isCurroDefault) {
                BigPrimaryButton(
                    text = stringResource(R.string.copy_home_make_default),
                    onClick = onMakeDefault,
                    modifier = Modifier.padding(horizontal = CurroSpacing.l),
                )
                Spacer(modifier = Modifier.height(CurroSpacing.l))
            } else if (!uiState.isNotificationAccessGranted) {
                // SF-4.6 (US-030) — "Permitir leer mensajes" — shown while Curro is the
                // default launcher but notification-listener access has not been granted.
                BigPrimaryButton(
                    text = stringResource(R.string.copy_grant_notif_access_cta),
                    onClick = onGrantNotifAccess,
                    modifier = Modifier.padding(horizontal = CurroSpacing.l),
                )
                Spacer(modifier = Modifier.height(CurroSpacing.l))
            }

            // 3. SF-1.3 + SF-2.4: main mic button. isListening swaps the colour to olive
            // while the assistant FSM is in any non-Idle state (Phase 5 — SF-5.2).
            MicButton(
                onPressed = onMicPressed,
                isListening = uiState.assistantState !is AssistantState.Idle,
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

        // SF-5.5 (US-039): state-driven overlay routing. The launcher home (above) stays
        // mounted; the overlay paints on top depending on `assistantState`. No animation
        // (brand-design rule 6) — the overlay simply appears/disappears. The instant swap
        // is acceptable because the audio changes too (the FSM transition is paired with
        // ttsClient.stop() etc. in the coordinator) and the user explicitly triggered the
        // change.
        when (val s = uiState.assistantState) {
            AssistantState.Idle -> Unit
            is AssistantState.Listening -> ListeningOverlay(state = s, modifier = Modifier.fillMaxSize())
            is AssistantState.Processing -> ProcessingOverlay(modifier = Modifier.fillMaxSize())
            is AssistantState.Confirming ->
                ConfirmationOverlay(
                    state = s,
                    onYes = onUserConfirmed,
                    onNo = onUserRejected,
                    modifier = Modifier.fillMaxSize(),
                )
            is AssistantState.Executing -> ExecutingOverlay(state = s, modifier = Modifier.fillMaxSize())
            is AssistantState.ErrorRecovery -> ErrorRecoveryOverlay(state = s, modifier = Modifier.fillMaxSize())
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
