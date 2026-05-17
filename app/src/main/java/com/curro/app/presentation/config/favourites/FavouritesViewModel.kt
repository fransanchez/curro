package com.curro.app.presentation.config.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.domain.repository.InstalledAppsRepository
import com.curro.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [FavouritesScreen] (SF-8.3 / US-052).
 *
 * Combines [InstalledAppsRepository.observeAllLaunchable] with
 * [SettingsRepository.launcherFavouritesOverride] to build the initial
 * [FavouritesUiState]. Once the user starts editing, [selectedPackages]
 * is held locally until [FavouritesEvent.Save] is fired.
 *
 * **No auto-save**: changes are staged in [_uiState] and only persisted when
 * Fran taps "Guardar". This prevents accidental overwrites if she navigates away.
 */
@HiltViewModel
class FavouritesViewModel
    @Inject
    constructor(
        private val installedAppsRepo: InstalledAppsRepository,
        private val settingsRepo: SettingsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FavouritesUiState())
        val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

        /**
         * Tracks whether the user has made any local change since the last load / save.
         * Separate from the combined flow so editing doesn't re-trigger a DataStore read.
         */
        private var persistedPackages: List<String>? = null

        init {
            viewModelScope.launch {
                combine(
                    installedAppsRepo.observeAllLaunchable(),
                    settingsRepo.launcherFavouritesOverride,
                ) { apps, override ->
                    persistedPackages = override
                    _uiState.value.copy(
                        allApps = apps,
                        selectedPackages = override,
                        hasUnsavedChanges = false,
                    )
                }.collect { state -> _uiState.value = state }
            }
        }

        fun onEvent(event: FavouritesEvent) {
            when (event) {
                is FavouritesEvent.AppToggled -> handleAppToggled(event.packageName)
                FavouritesEvent.UseAutomatic -> handleUseAutomatic()
                FavouritesEvent.Save -> handleSave()
            }
        }

        private fun handleAppToggled(packageName: String) {
            val current = _uiState.value.selectedPackages.orEmpty()
            val updated =
                if (current.contains(packageName)) {
                    current - packageName
                } else if (current.size < FavouritesUiState.MAX_FAVOURITES) {
                    current + packageName
                } else {
                    current // already at max — ignore the tap
                }
            _uiState.update {
                it.copy(
                    selectedPackages = updated,
                    hasUnsavedChanges = updated != persistedPackages,
                )
            }
        }

        private fun handleUseAutomatic() {
            _uiState.update {
                it.copy(
                    selectedPackages = null,
                    hasUnsavedChanges = null != persistedPackages,
                )
            }
        }

        private fun handleSave() {
            val packages = _uiState.value.selectedPackages
            viewModelScope.launch {
                settingsRepo.setLauncherFavouritesOverride(packages)
            }
            persistedPackages = packages
            _uiState.update { it.copy(hasUnsavedChanges = false) }
        }
    }
