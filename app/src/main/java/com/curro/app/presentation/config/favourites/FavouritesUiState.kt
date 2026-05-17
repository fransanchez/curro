package com.curro.app.presentation.config.favourites

import com.curro.app.domain.model.LaunchableApp

/**
 * UI state for [FavouritesScreen] (SF-8.3 / US-052).
 *
 * [allApps] — the full sorted list from [InstalledAppsRepository.observeAllLaunchable].
 * [selectedPackages] — the current set of package names Fran has selected; null means
 *   "use automatic detection" (the DataStore override is not set). An empty list is a
 *   valid override (no apps pinned — falls back to empty grid or seeds in the launcher).
 * [hasUnsavedChanges] — true if [selectedPackages] differs from the persisted DataStore value.
 *   Used to show/hide the Save button.
 * [maxCount] — the launcher's slot limit (4).
 */
data class FavouritesUiState(
    val allApps: List<LaunchableApp> = emptyList(),
    val selectedPackages: List<String>? = null,
    val hasUnsavedChanges: Boolean = false,
    val maxCount: Int = MAX_FAVOURITES,
) {
    companion object {
        const val MAX_FAVOURITES = 4
    }
}
