package com.curro.app.presentation.config.favourites

/**
 * User-initiated events on [FavouritesScreen] (SF-8.3 / US-052).
 */
sealed interface FavouritesEvent {
    /**
     * Fran tapped an app in the list. If [packageName] is already in
     * [FavouritesUiState.selectedPackages], it is removed; otherwise it is
     * appended (up to [FavouritesUiState.maxCount]).
     */
    data class AppToggled(val packageName: String) : FavouritesEvent

    /**
     * Fran pressed "Usar detección automática" — clears the override
     * (sets [FavouritesUiState.selectedPackages] = null).
     */
    data object UseAutomatic : FavouritesEvent

    /**
     * Fran pressed "Guardar" — persists [FavouritesUiState.selectedPackages]
     * to DataStore via [SettingsRepository.setLauncherFavouritesOverride].
     */
    data object Save : FavouritesEvent
}
