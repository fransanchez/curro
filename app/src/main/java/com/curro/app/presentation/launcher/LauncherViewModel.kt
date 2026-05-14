package com.curro.app.presentation.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.R
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.domain.model.ClockState
import com.curro.app.domain.usecase.ObserveClockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [LauncherPlaceholderScreen] (US-009/SF-1.1 through US-014/SF-1.6).
 *
 * Combines upstream flows into a single [LauncherUiState]:
 * - [DefaultLauncherDetector.flow] — whether Curro is the resolved default home.
 * - [ObserveClockUseCase] — a live clock tick emitting every second.
 *
 * SF-1.3 adds the [LauncherEvent] / [LauncherSideEffect] plumbing:
 * - [onEvent] dispatches [LauncherEvent]s from the screen.
 * - [sideEffects] exposes a Channel-backed [Flow] of one-shot UI effects.
 *
 * SF-1.4 extends [LauncherUiState] with the [favorites] list.
 * SF-1.6 adds the [LauncherEvent.ClockTapped] five-tap counter.
 *
 * [SharingStarted.WhileSubscribed] with [SUBSCRIBE_TIMEOUT_MS]: flows pause when
 * Curro is fully backgrounded; the 5 s grace covers configuration changes.
 */
@HiltViewModel
class LauncherViewModel
    @Inject
    constructor(
        detector: DefaultLauncherDetector,
        observeClock: ObserveClockUseCase,
    ) : ViewModel() {
        val uiState: StateFlow<LauncherUiState> =
            combine(detector.flow, observeClock()) { isDefault, clock ->
                LauncherUiState(isCurroDefault = isDefault, clock = clock)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue =
                    LauncherUiState(
                        isCurroDefault = false,
                        clock = ClockState(timeText = "--:--", dateText = ""),
                    ),
            )

        private val _sideEffects = Channel<LauncherSideEffect>(Channel.BUFFERED)

        /**
         * One-shot UI effects — consumed exactly once by the screen via [LaunchedEffect].
         * Backed by a [Channel.BUFFERED] so rapid double-presses don't lose events.
         */
        val sideEffects: Flow<LauncherSideEffect> = _sideEffects.receiveAsFlow()

        /** Dispatch a user or system event to the ViewModel. Thread-safe. */
        fun onEvent(event: LauncherEvent) {
            when (event) {
                is LauncherEvent.MicPressed -> onMicPressed()
                is LauncherEvent.AppTileTapped -> onAppTileTapped(event.packageName)
                is LauncherEvent.ClockTapped -> onClockTapped()
            }
        }

        private fun onMicPressed() {
            // Phase 1 — inert: emit a toast. SF-2.x replaces this with FSM.startListening().
            viewModelScope.launch {
                _sideEffects.send(LauncherSideEffect.ShowToast(R.string.copy_mic_inert))
            }
        }

        private fun onAppTileTapped(packageName: String) {
            viewModelScope.launch {
                _sideEffects.send(LauncherSideEffect.LaunchApp(packageName))
            }
        }

        // SF-1.6 five-tap counter — maintained as a list of tap timestamps (ms).
        private val clockTapTimes = mutableListOf<Long>()

        private fun onClockTapped() {
            val now = System.currentTimeMillis()
            clockTapTimes.add(now)
            // Drop entries older than TAP_WINDOW_MS.
            clockTapTimes.removeAll { now - it > TAP_WINDOW_MS }
            if (clockTapTimes.size >= TAP_COUNT_THRESHOLD) {
                clockTapTimes.clear()
                viewModelScope.launch {
                    _sideEffects.send(LauncherSideEffect.OpenConfig)
                }
            }
        }

        private companion object {
            /** Grace period for [SharingStarted.WhileSubscribed] — survives configuration changes. */
            const val SUBSCRIBE_TIMEOUT_MS = 5_000L

            /** SF-1.6: rolling window for the clock five-tap gesture (milliseconds). */
            const val TAP_WINDOW_MS = 3_000L

            /** SF-1.6: number of taps required within [TAP_WINDOW_MS] to open config. */
            const val TAP_COUNT_THRESHOLD = 5
        }
    }

/**
 * UI state for [LauncherPlaceholderScreen].
 *
 * - [isCurroDefault]: whether Curro is the resolved default home. Controls CTA visibility.
 * - [clock]: live-updating time + date strings from [ObserveClockUseCase] (SF-1.2).
 *
 * SF-1.4 adds [favorites] (the static four-tile grid).
 */
data class LauncherUiState(
    val isCurroDefault: Boolean,
    val clock: ClockState,
)

/**
 * User or system events dispatched to [LauncherViewModel.onEvent].
 *
 * Sealed so exhaustive `when` is enforced at every call site — new events always
 * require a matching branch.
 */
sealed interface LauncherEvent {
    /** SF-1.3 — mic button pressed (Phase 1: inert; Phase 2: starts listening). */
    data object MicPressed : LauncherEvent

    /**
     * SF-1.4 — an app tile was tapped.
     * @param packageName The resolved package to launch; may be the fallback package if
     *   dynamic resolution found nothing.
     */
    data class AppTileTapped(val packageName: String) : LauncherEvent

    /** SF-1.6 — clock block tapped; the five-tap counter is inside the ViewModel. */
    data object ClockTapped : LauncherEvent
}

/**
 * One-shot UI side effects emitted by [LauncherViewModel] and consumed exactly once
 * by the screen via a [LaunchedEffect] / [Channel] pattern.
 *
 * These are events that the View must handle but that don't belong in [LauncherUiState]
 * (because they are ephemeral — a Toast should appear once, not re-appear on every
 * recomposition).
 */
sealed interface LauncherSideEffect {
    /**
     * Show a [android.widget.Toast] with the given Android string resource ID.
     * Phase-1-only: replaces real assistant feedback while voice pipeline is absent.
     *
     * @param messageResId `R.string.*` reference.
     */
    data class ShowToast(val messageResId: Int) : LauncherSideEffect

    /**
     * SF-1.4 — launch the app with the given package name via
     * `PackageManager.getLaunchIntentForPackage`.
     */
    data class LaunchApp(val packageName: String) : LauncherSideEffect

    /**
     * SF-1.6 — five-tap clock gesture completed; navigate to the config menu route.
     */
    data object OpenConfig : LauncherSideEffect
}
