package com.curro.app.presentation.config.thresholds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [ThresholdsScreen] (SF-8.5 / US-054).
 *
 * Combines [SettingsRepository.executeThreshold], [SettingsRepository.confirmThreshold], and
 * [SettingsRepository.alwaysConfirm] into a [ThresholdsUiState]. Every event is persisted
 * immediately. The clamping invariant `confirm ≤ execute` is enforced by
 * [com.curro.app.data.local.SettingsDataStore]'s setters — the ViewModel is not responsible
 * for additional clamping.
 */
@HiltViewModel
class ThresholdsViewModel
    @Inject
    constructor(
        private val settingsRepo: SettingsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ThresholdsUiState())
        val uiState: StateFlow<ThresholdsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                combine(
                    settingsRepo.executeThreshold,
                    settingsRepo.confirmThreshold,
                    settingsRepo.alwaysConfirm,
                ) { execute, confirm, always ->
                    ThresholdsUiState(
                        executeThreshold = execute,
                        confirmThreshold = confirm,
                        alwaysConfirm = always,
                    )
                }.collect { state -> _uiState.value = state }
            }
        }

        fun onEvent(event: ThresholdsEvent) {
            when (event) {
                is ThresholdsEvent.ExecuteThresholdChanged -> handleExecuteChanged(event.value)
                is ThresholdsEvent.ConfirmThresholdChanged -> handleConfirmChanged(event.value)
                is ThresholdsEvent.AlwaysConfirmChanged -> handleAlwaysConfirmChanged(event.enabled)
            }
        }

        private fun handleExecuteChanged(value: Float) {
            viewModelScope.launch {
                settingsRepo.setExecuteThreshold(value)
            }
        }

        private fun handleConfirmChanged(value: Float) {
            viewModelScope.launch {
                settingsRepo.setConfirmThreshold(value)
            }
        }

        private fun handleAlwaysConfirmChanged(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepo.setAlwaysConfirm(enabled)
            }
        }
    }
