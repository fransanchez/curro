package com.curro.app.presentation.config

/**
 * User-initiated events dispatched to [ConfigViewModel.onEvent] (SF-8.1 / US-050).
 *
 * In SF-8.1 only [ToggleChanged] exists. The ViewModel's handler logs a `Log.w`
 * and does NOT mutate the setting (the behaviour for each toggle is wired in
 * the SF listed in [ConfigSection.Toggle.onChangeWillBeWiredInSF]).
 */
sealed interface ConfigEvent {
    /**
     * One of the two inline toggle rows was flipped.
     *
     * @param section The toggle section whose switch was changed.
     * @param newValue The user's requested new state.
     */
    data class ToggleChanged(
        val section: ConfigSection.Toggle,
        val newValue: Boolean,
    ) : ConfigEvent
}
