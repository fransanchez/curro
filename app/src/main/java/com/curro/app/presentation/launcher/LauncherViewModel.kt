package com.curro.app.presentation.launcher

import android.Manifest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.assistant.AssistantCoordinator
import com.curro.app.assistant.AssistantSideEffect
import com.curro.app.assistant.AssistantState
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.domain.model.ClockState
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.repository.FavoriteAppsRepository
import com.curro.app.domain.usecase.ObserveClockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [LauncherPlaceholderScreen].
 *
 * **Phase 5 boundary** — this is a thin observer of [AssistantCoordinator].
 * Every voice-pipeline concern (STT/TTS/model/handler/permission glue) lives in
 * the coordinator now. The VM only:
 *  - exposes `coordinator.state` and the launcher-only state
 *    (clock, default-home, favourites, notif-access) as one [LauncherUiState];
 *  - forwards `LauncherEvent`s to the coordinator;
 *  - adapts the coordinator's [AssistantSideEffect]s to the
 *    [LauncherSideEffect]s the composable already knows;
 *  - owns the five-tap clock gesture, the app-tile-tap, and the
 *    notification-access `ON_RESUME` re-check.
 *
 * The Phase-2 / Phase-4 pre-refactor VM had 18 functions + a
 * `@Suppress("TooManyFunctions")`. Phase 5's refactor lands the VM at 5 named
 * functions — the suppression is gone.
 */
@HiltViewModel
class LauncherViewModel
    @Inject
    constructor(
        detector: DefaultLauncherDetector,
        observeClock: ObserveClockUseCase,
        favoritesRepo: FavoriteAppsRepository,
        private val coordinator: AssistantCoordinator,
        private val notifGate: NotificationAccessGate,
    ) : ViewModel() {
        private val notifGrantedFlow = MutableStateFlow(notifGate.isGranted())

        /**
         * Internal seam — same pattern as the pre-refactor VM. Tests substitute
         * a no-op or a `TestLifecycleOwner` so the `ON_RESUME` re-check is
         * driven deterministically. See [com.curro.app.data.launcher.DefaultLauncherDetectorImpl].
         */
        internal var lifecycleSource: () -> Lifecycle = {
            ProcessLifecycleOwner.get().lifecycle
        }

        private val resumeObserver =
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    notifGrantedFlow.value = notifGate.isGranted()
                }
            }

        init {
            viewModelScope.launch {
                try {
                    lifecycleSource().addObserver(resumeObserver)
                } catch (_: Exception) {
                    // ProcessLifecycleOwner not initialised in JVM unit tests — safe to skip.
                }
            }
            // Adapt coordinator side effects → launcher side effects so the composable's
            // existing `LaunchedEffect`-on-sideEffects pipeline keeps working unchanged.
            viewModelScope.launch {
                coordinator.sideEffects.collect { effect ->
                    when (effect) {
                        is AssistantSideEffect.RequestPermission ->
                            when (effect.permission) {
                                Manifest.permission.RECORD_AUDIO ->
                                    _sideEffects.send(LauncherSideEffect.RequestRecordAudio)
                                Manifest.permission.READ_CONTACTS ->
                                    _sideEffects.send(LauncherSideEffect.RequestReadContacts)
                                Manifest.permission.CALL_PHONE ->
                                    _sideEffects.send(LauncherSideEffect.RequestCallPhone)
                            }
                        is AssistantSideEffect.ShowDebugJson ->
                            _sideEffects.send(LauncherSideEffect.ShowDebugJson(effect.prettyJson))
                    }
                }
            }
        }

        override fun onCleared() {
            try {
                lifecycleSource().removeObserver(resumeObserver)
            } catch (_: Exception) {
                // Same guard — ProcessLifecycleOwner not available in unit tests.
            }
        }

        val uiState: StateFlow<LauncherUiState> =
            combine(
                detector.flow,
                observeClock(),
                favoritesRepo.observeFavorites(),
                coordinator.state,
                notifGrantedFlow,
            ) { isDefault, clock, favorites, assistant, notifGranted ->
                LauncherUiState(
                    isCurroDefault = isDefault,
                    clock = clock,
                    favorites = favorites,
                    assistantState = assistant,
                    isNotificationAccessGranted = notifGranted,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue =
                    LauncherUiState(
                        isCurroDefault = false,
                        clock = ClockState(timeText = "--:--", dateText = ""),
                        favorites = emptyList(),
                        assistantState = AssistantState.Idle,
                        isNotificationAccessGranted = false,
                    ),
            )

        private val _sideEffects = Channel<LauncherSideEffect>(Channel.BUFFERED)
        val sideEffects: Flow<LauncherSideEffect> = _sideEffects.receiveAsFlow()

        // SF-1.6 five-tap counter — same shape as the pre-refactor VM.
        private val clockTapTimes = mutableListOf<Long>()

        fun onEvent(event: LauncherEvent) {
            when (event) {
                is LauncherEvent.MicPressed -> coordinator.onMicPressed()
                is LauncherEvent.AppTileTapped -> onAppTileTapped(event.packageName)
                is LauncherEvent.ClockTapped -> onClockTapped()
                is LauncherEvent.RecordAudioPermissionResult ->
                    coordinator.onPermissionResult(Manifest.permission.RECORD_AUDIO, event.granted)
                is LauncherEvent.ReadContactsPermissionResult ->
                    coordinator.onPermissionResult(Manifest.permission.READ_CONTACTS, event.granted)
                is LauncherEvent.CallPhonePermissionResult ->
                    coordinator.onPermissionResult(Manifest.permission.CALL_PHONE, event.granted)
                is LauncherEvent.GrantNotifAccessRequested -> onGrantNotifAccessRequested()
                is LauncherEvent.UserConfirmed -> coordinator.onUserConfirmed()
                is LauncherEvent.UserRejected -> coordinator.onUserRejected()
                is LauncherEvent.PickerPicked -> coordinator.onPickerPicked(event.contact)
                is LauncherEvent.PickerNone -> coordinator.onPickerNone()
            }
        }

        private fun onAppTileTapped(packageName: String) {
            viewModelScope.launch {
                _sideEffects.send(LauncherSideEffect.LaunchApp(packageName))
            }
        }

        private fun onClockTapped() {
            val now = System.currentTimeMillis()
            clockTapTimes.add(now)
            clockTapTimes.removeAll { now - it > TAP_WINDOW_MS }
            if (clockTapTimes.size >= TAP_COUNT_THRESHOLD) {
                clockTapTimes.clear()
                viewModelScope.launch {
                    _sideEffects.send(LauncherSideEffect.OpenConfig)
                }
            }
        }

        private fun onGrantNotifAccessRequested() {
            viewModelScope.launch {
                _sideEffects.send(LauncherSideEffect.OpenNotificationAccessSettings)
            }
        }

        private companion object {
            const val SUBSCRIBE_TIMEOUT_MS = 5_000L
            const val TAP_WINDOW_MS = 3_000L
            const val TAP_COUNT_THRESHOLD = 5
        }
    }

/**
 * UI state for [LauncherPlaceholderScreen].
 *
 * - [isCurroDefault]: whether Curro is the resolved default home. Controls CTA visibility.
 * - [clock]: live-updating time + date strings from [ObserveClockUseCase] (SF-1.2).
 * - [favorites]: the four static favourite-app tiles (SF-1.4). Empty until the repository emits.
 * - [assistantState]: SF-5.1 / SF-5.2 — the FSM state. Drives the overlay routing in
 *   `LauncherPlaceholderContent` (SF-5.5 splits the overlays per-state).
 * - [isNotificationAccessGranted]: SF-4.6 (US-030) — false while notification-listener access
 *   is not granted; triggers the "Permitir leer mensajes" home CTA.
 */
data class LauncherUiState(
    val isCurroDefault: Boolean,
    val clock: ClockState,
    val favorites: List<FavoriteApp> = emptyList(),
    val assistantState: AssistantState = AssistantState.Idle,
    val isNotificationAccessGranted: Boolean = false,
)

/**
 * User or system events dispatched to [LauncherViewModel.onEvent].
 *
 * Sealed so exhaustive `when` is enforced at every call site — new events always
 * require a matching branch.
 */
sealed interface LauncherEvent {
    /** SF-1.3 — mic button pressed. Forwarded to [AssistantCoordinator.onMicPressed]. */
    data object MicPressed : LauncherEvent

    /**
     * SF-1.4 — an app tile was tapped.
     * @param packageName The resolved package to launch.
     */
    data class AppTileTapped(val packageName: String) : LauncherEvent

    /** SF-1.6 — clock block tapped; the five-tap counter is inside the ViewModel. */
    data object ClockTapped : LauncherEvent

    /** SF-2.3 (US-017) — result of the runtime RECORD_AUDIO request. */
    data class RecordAudioPermissionResult(val granted: Boolean) : LauncherEvent

    /**
     * SF-4.6 (US-030) — user tapped the "Permitir leer mensajes" CTA.
     * The ViewModel emits [LauncherSideEffect.OpenNotificationAccessSettings].
     */
    data object GrantNotifAccessRequested : LauncherEvent

    /** SF-4.10 (US-034) — result of the runtime READ_CONTACTS request. */
    data class ReadContactsPermissionResult(val granted: Boolean) : LauncherEvent

    /** SF-4.10 (US-034) — result of the runtime CALL_PHONE request. */
    data class CallPhonePermissionResult(val granted: Boolean) : LauncherEvent

    /** SF-6.2 (US-042) — user tapped SÍ in the [com.curro.app.presentation.assistant.ConfirmationOverlay]. */
    data object UserConfirmed : LauncherEvent

    /** SF-6.2 (US-042) — user tapped NO. */
    data object UserRejected : LauncherEvent

    /** SF-6.3 (US-043) — user tapped a candidate in [com.curro.app.presentation.assistant.ContactPickerOverlay]. */
    data class PickerPicked(val contact: com.curro.app.domain.model.Contact) : LauncherEvent

    /** SF-6.3 (US-043) — user tapped "Ninguna" in the picker. */
    data object PickerNone : LauncherEvent
}

/**
 * One-shot UI side effects emitted by [LauncherViewModel] and consumed exactly once
 * by the screen via a [kotlinx.coroutines.flow.collect] in a `LaunchedEffect`.
 */
sealed interface LauncherSideEffect {
    /** Show a [android.widget.Toast] with the given Android string resource ID. */
    data class ShowToast(val messageResId: Int) : LauncherSideEffect

    /** SF-1.4 — launch the app with the given package name. */
    data class LaunchApp(val packageName: String) : LauncherSideEffect

    /** SF-1.6 — five-tap clock gesture completed; navigate to the config menu route. */
    data object OpenConfig : LauncherSideEffect

    /** SF-2.3 (US-017) — ask the screen to fire its `ActivityResultLauncher` for RECORD_AUDIO. */
    data object RequestRecordAudio : LauncherSideEffect

    /**
     * SF-3.6 (US-024) — surface the parsed FunctionCall JSON to the listening
     * overlay for debug-only visual verification. Render only in `BuildConfig.DEBUG`.
     */
    data class ShowDebugJson(val prettyJson: String) : LauncherSideEffect

    /** SF-4.6 (US-030) — open HyperOS's notification-access settings page. */
    data object OpenNotificationAccessSettings : LauncherSideEffect

    /** SF-4.10 (US-034) — ask the screen to fire its `ActivityResultLauncher` for READ_CONTACTS. */
    data object RequestReadContacts : LauncherSideEffect

    /** SF-4.10 (US-034) — ask the screen to fire its `ActivityResultLauncher` for CALL_PHONE. */
    data object RequestCallPhone : LauncherSideEffect
}
