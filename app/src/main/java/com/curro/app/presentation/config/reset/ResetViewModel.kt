package com.curro.app.presentation.config.reset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.FailedCommandLog
import com.curro.app.domain.repository.FavoriteAppsRepository
import com.curro.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [ResetScreen] (SF-8.8 / US-058).
 *
 * Performs a four-way parallel reset:
 * 1. Delete all aliases ([AliasRepository.deleteAll]).
 * 2. Clear app-usage data ([FavoriteAppsRepository.clearUsage]).
 * 3. Clear the failed-commands log ([FailedCommandLog.deleteAll]).
 * 4. Clear the launcher favourites override ([SettingsRepository.setLauncherFavouritesOverride] null).
 *
 * All four operations run concurrently in the same coroutine scope; [ResetUiState.resetComplete]
 * is set to `true` when ALL have completed. The screen auto-navigates back on `resetComplete`.
 *
 * **Privacy**: no telemetry event carries alias names, transcripts, or contact information.
 * The telemetry event is `learning_reset` with no PII properties.
 */
@HiltViewModel
class ResetViewModel
    @Inject
    constructor(
        private val aliasRepo: AliasRepository,
        private val favRepo: FavoriteAppsRepository,
        private val failedLog: FailedCommandLog,
        private val settingsRepo: SettingsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ResetUiState())
        val uiState: StateFlow<ResetUiState> = _uiState.asStateFlow()

        fun onEvent(event: ResetEvent) {
            when (event) {
                ResetEvent.ResetPressed -> _uiState.update { it.copy(showConfirmDialog = true) }
                ResetEvent.ConfirmReset -> handleConfirmReset()
                ResetEvent.DismissDialog -> _uiState.update { it.copy(showConfirmDialog = false) }
            }
        }

        private fun handleConfirmReset() {
            _uiState.update { it.copy(showConfirmDialog = false) }
            viewModelScope.launch {
                val aliasReset = async { aliasRepo.deleteAll() }
                val usageReset = async { favRepo.clearUsage() }
                val logReset = async { failedLog.deleteAll() }
                val overrideReset = async { settingsRepo.setLauncherFavouritesOverride(null) }
                aliasReset.await()
                usageReset.await()
                logReset.await()
                overrideReset.await()
                _uiState.update { it.copy(resetComplete = true) }
            }
        }
    }
