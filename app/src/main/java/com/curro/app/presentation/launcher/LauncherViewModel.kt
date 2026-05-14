package com.curro.app.presentation.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.domain.model.ClockState
import com.curro.app.domain.usecase.ObserveClockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for [LauncherPlaceholderScreen] (US-009 / SF-1.1, US-010 / SF-1.2).
 *
 * Combines two upstream flows into a single [LauncherUiState]:
 * - [DefaultLauncherDetector.flow] — whether Curro is the resolved default home.
 * - [ObserveClockUseCase] — a live clock tick emitting every second.
 *
 * The "Hazme tu pantalla de inicio" CTA is gated on [LauncherUiState.isCurroDefault];
 * the clock block reads [LauncherUiState.clock].
 *
 * Subsequent Phase-1 SFs grow this ViewModel:
 * - SF-1.3 adds the mic-button press event.
 * - SF-1.4 adds the favourite-apps list.
 * - SF-1.6 adds the 5-tap-on-clock gesture counter and a [LauncherEvent] sealed interface.
 *
 * [SharingStarted.WhileSubscribed] with [SUBSCRIBE_TIMEOUT_MS]: both the lifecycle
 * observer and the clock ticker pause when Curro is fully backgrounded. The 5 s grace
 * covers configuration changes without tearing down the flows.
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

        private companion object {
            /** Grace period for [SharingStarted.WhileSubscribed] — survives configuration changes. */
            const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        }
    }

/**
 * UI state for [LauncherPlaceholderScreen].
 *
 * Extended in SF-1.2 to carry a live [ClockState]. Extended further in SF-1.4 (favourites
 * grid) and SF-1.6 (mic state). The defaults are "safe": `isCurroDefault = false` shows the
 * CTA rather than hiding it (false positive is harmless); `clock` placeholder `"--:--"` is
 * visible until the first tick fires (< 1 ms after subscription on [Dispatchers.Default]).
 */
data class LauncherUiState(
    val isCurroDefault: Boolean,
    val clock: ClockState,
)
