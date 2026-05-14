package com.curro.app.presentation.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.data.launcher.DefaultLauncherDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for [LauncherPlaceholderScreen] (US-009 / SF-1.1).
 *
 * Exposes a single field for now: whether Curro is the resolved default home Activity.
 * The "Hazme tu pantalla de inicio" CTA is gated on this field — it disappears reactively
 * the moment [DefaultLauncherDetector.flow] emits `true` (post-role-grant, on resume).
 *
 * Subsequent Phase-1 SFs grow this ViewModel:
 * - SF-1.2 adds clock tick state.
 * - SF-1.4 adds the favourite-apps list.
 * - SF-1.6 adds the 5-tap-on-clock gesture counter and the [LauncherEvent] sealed interface.
 *
 * [SharingStarted.WhileSubscribed] with a [SUBSCRIBE_TIMEOUT_MS] timeout rather than
 * [SharingStarted.Eagerly]: the detector registers a lifecycle observer on subscription;
 * leaking across Activity destruction is wasteful. The 5 s grace covers configuration changes.
 */
@HiltViewModel
class LauncherViewModel
    @Inject
    constructor(
        detector: DefaultLauncherDetector,
    ) : ViewModel() {
        val uiState: StateFlow<LauncherUiState> =
            detector.flow
                .map { LauncherUiState(isCurroDefault = it) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                    initialValue = LauncherUiState(isCurroDefault = false),
                )

        private companion object {
            /** Grace period for [SharingStarted.WhileSubscribed] — survives configuration changes. */
            const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        }
    }

/**
 * UI state for [LauncherPlaceholderScreen].
 *
 * Phase-1 shape — extended piecewise as SF-1.2 → SF-1.6 add real state (clock, favourites
 * grid, mic state). The default `isCurroDefault = false` is the safe choice: if the
 * detector hasn't answered yet, show the CTA (false positive = harmless one extra tap;
 * false negative = no recovery path if HyperOS forgot the default).
 */
data class LauncherUiState(
    val isCurroDefault: Boolean,
)
