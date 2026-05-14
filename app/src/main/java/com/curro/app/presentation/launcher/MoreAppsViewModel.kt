package com.curro.app.presentation.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.domain.model.LaunchableApp
import com.curro.app.domain.repository.InstalledAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Timeout before [SharingStarted.WhileSubscribed] cancels the upstream Flow (5 s). */
private const val SHARING_STOP_TIMEOUT_MS = 5_000L

/**
 * ViewModel for the "Más apps" full-app-list screen (SF-1.5 / US-013).
 *
 * Exposes a [StateFlow] of [MoreAppsUiState] derived from
 * [InstalledAppsRepository.observeAllLaunchable], which re-emits on each
 * [ProcessLifecycleOwner] ON_RESUME so the list stays current after the user
 * installs or removes an app while Curro is backgrounded.
 *
 * The only action in Phase 1 is launching an app; that side effect is handled
 * directly in the composable via [android.content.Context.startActivity], keeping
 * this ViewModel stateless beyond the list data.
 *
 * @param appsRepo Provides the sorted, deduplicated list of all launchable apps.
 */
@HiltViewModel
class MoreAppsViewModel
    @Inject
    constructor(
        appsRepo: InstalledAppsRepository,
    ) : ViewModel() {
        /**
         * Current UI state — [MoreAppsUiState.Loading] until the first list emission,
         * then [MoreAppsUiState.Ready] forever (even when the list is empty).
         */
        val uiState: StateFlow<MoreAppsUiState> =
            appsRepo
                .observeAllLaunchable()
                .map { MoreAppsUiState.Ready(it) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SHARING_STOP_TIMEOUT_MS),
                    initialValue = MoreAppsUiState.Loading,
                )
    }

/**
 * UI state for the "Más apps" screen.
 *
 * [Loading] is brief — the repository emits synchronously on subscription via `onStart`.
 * [Ready] holds the sorted list of all installed launchable apps; an empty list
 * is valid (device with no user-installed apps) and renders an empty [LazyColumn].
 */
sealed interface MoreAppsUiState {
    data object Loading : MoreAppsUiState

    data class Ready(
        val apps: List<LaunchableApp>,
    ) : MoreAppsUiState
}
