package com.curro.app.presentation.config.tts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.domain.repository.SettingsRepository
import com.curro.app.domain.repository.SpanishVoiceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [TtsSettingsScreen] (SF-8.4 / US-053).
 *
 * Combines [SettingsRepository.ttsVoiceName], [SettingsRepository.ttsRate], and
 * [SettingsRepository.ttsPitch] into a single [TtsSettingsUiState]. Every event is persisted
 * immediately — there is no staged "Save" step for this screen.
 *
 * [SpanishVoiceProvider.availableVoices] is a synchronous call that reads the on-device TTS
 * engine's voice list. It is called once in `init` on the ViewModel (already off the main
 * thread courtesy of `viewModelScope`'s coroutine context).
 */
@HiltViewModel
class TtsSettingsViewModel
    @Inject
    constructor(
        private val settingsRepo: SettingsRepository,
        private val voiceProvider: SpanishVoiceProvider,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TtsSettingsUiState())
        val uiState: StateFlow<TtsSettingsUiState> = _uiState.asStateFlow()

        init {
            val voices = voiceProvider.availableVoices()
            _uiState.update { it.copy(availableVoices = voices) }

            viewModelScope.launch {
                combine(
                    settingsRepo.ttsVoiceName,
                    settingsRepo.ttsRate,
                    settingsRepo.ttsPitch,
                ) { name, rate, pitch ->
                    _uiState.value.copy(
                        selectedVoiceName = name,
                        rate = rate,
                        pitch = pitch,
                    )
                }.collect { state -> _uiState.value = state }
            }
        }

        fun onEvent(event: TtsSettingsEvent) {
            when (event) {
                is TtsSettingsEvent.RateChanged -> handleRateChanged(event.rate)
                is TtsSettingsEvent.PitchChanged -> handlePitchChanged(event.pitch)
                is TtsSettingsEvent.VoiceSelected -> handleVoiceSelected(event.voiceName)
            }
        }

        private fun handleRateChanged(rate: Float) {
            viewModelScope.launch {
                settingsRepo.setTtsRate(rate)
            }
        }

        private fun handlePitchChanged(pitch: Float) {
            viewModelScope.launch {
                settingsRepo.setTtsPitch(pitch)
            }
        }

        private fun handleVoiceSelected(voiceName: String?) {
            viewModelScope.launch {
                settingsRepo.setTtsVoiceName(voiceName)
            }
        }
    }
