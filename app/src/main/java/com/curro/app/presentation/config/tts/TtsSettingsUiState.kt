package com.curro.app.presentation.config.tts

import com.curro.app.domain.repository.SpanishVoice

/**
 * UI state for [TtsSettingsScreen] (SF-8.4 / US-053).
 *
 * All three DataStore-backed TTS settings plus the list of available on-device Spanish voices
 * are combined here. The screen is read-only until the user drags a slider or taps a voice chip;
 * every change is immediately persisted to [com.curro.app.domain.repository.SettingsRepository].
 *
 * @param selectedVoiceName The voice currently active in DataStore, or null for the system default.
 * @param rate Current speech rate in [0.5f, 1.5f]. Default 0.88f.
 * @param pitch Current pitch in [0.5f, 2.0f]. Default 1.0f.
 * @param availableVoices Spanish voices exposed by the system TTS engine. Empty when no offline
 *     Spanish voice is installed — the screen hides the picker in that case.
 */
data class TtsSettingsUiState(
    val selectedVoiceName: String? = null,
    val rate: Float = DEFAULT_RATE,
    val pitch: Float = DEFAULT_PITCH,
    val availableVoices: List<SpanishVoice> = emptyList(),
) {
    companion object {
        const val DEFAULT_RATE: Float = 0.88f
        const val DEFAULT_PITCH: Float = 1.0f
        const val RATE_MIN: Float = 0.5f
        const val RATE_MAX: Float = 1.5f
        const val PITCH_MIN: Float = 0.5f
        const val PITCH_MAX: Float = 2.0f
    }
}
