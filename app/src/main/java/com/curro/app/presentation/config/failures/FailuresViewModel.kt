package com.curro.app.presentation.config.failures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.data.local.FailureKind
import com.curro.app.domain.repository.FailedCommandLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [FailuresScreen] (SF-8.6 / US-055).
 *
 * Observes [FailedCommandLog.observeRecent] and maps each entity to a [FailureView].
 * Local filter state ([FailuresUiState.activeFilter]) is held in the ViewModel; the
 * filtered list is derived from [FailuresUiState.visibleFailures].
 *
 * **Privacy**: [com.curro.app.data.local.FailedCommandEntity.transcript] is NOT mapped
 * into [FailureView] — it stays in the Room entity and is never surfaced to the UI.
 */
@HiltViewModel
class FailuresViewModel
    @Inject
    constructor(
        private val failedLog: FailedCommandLog,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FailuresUiState())
        val uiState: StateFlow<FailuresUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                failedLog.observeRecent(limit = RECENT_LIMIT).collect { entities ->
                    val views =
                        entities.map { entity ->
                            FailureView(
                                id = entity.id,
                                displayTime = FailureView.formatTime(entity.timestampMs),
                                kind = entity.kind,
                                details = entity.details,
                                sent = entity.sent,
                            )
                        }
                    _uiState.update { it.copy(allFailures = views) }
                }
            }
        }

        fun onEvent(event: FailuresEvent) {
            when (event) {
                is FailuresEvent.FilterChanged -> handleFilterChanged(event.kind)
                FailuresEvent.ClearPressed -> _uiState.update { it.copy(showClearDialog = true) }
                FailuresEvent.ConfirmClear -> handleConfirmClear()
                FailuresEvent.DismissClearDialog -> _uiState.update { it.copy(showClearDialog = false) }
            }
        }

        private fun handleFilterChanged(kind: FailureKind?) {
            _uiState.update { it.copy(activeFilter = kind) }
        }

        private fun handleConfirmClear() {
            _uiState.update { it.copy(showClearDialog = false) }
            viewModelScope.launch {
                failedLog.deleteAll()
            }
        }

        private companion object {
            const val RECENT_LIMIT = 50
        }
    }
