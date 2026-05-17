package com.curro.app.presentation.config.tts

/**
 * User-initiated events on [TtsSettingsScreen] (SF-8.4 / US-053).
 *
 * Every event is immediately persisted — there is no staged "save" step for this screen.
 * DataStore writes are fire-and-forget inside `viewModelScope`; the Flow back-pressure from
 * [com.curro.app.domain.repository.SettingsRepository] ensures the UI stays consistent.
 */
sealed interface TtsSettingsEvent {
    /** Fran moved the rate slider to [rate]. Clamped to RATE_MIN..RATE_MAX in [TtsSettingsUiState]. */
    data class RateChanged(val rate: Float) : TtsSettingsEvent

    /** Fran moved the pitch slider to [pitch]. Clamped to PITCH_MIN..PITCH_MAX in [TtsSettingsUiState]. */
    data class PitchChanged(val pitch: Float) : TtsSettingsEvent

    /** Fran tapped a voice chip. Passing `null` resets to the system default voice. */
    data class VoiceSelected(val voiceName: String?) : TtsSettingsEvent
}
