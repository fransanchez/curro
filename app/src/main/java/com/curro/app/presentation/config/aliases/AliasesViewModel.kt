package com.curro.app.presentation.config.aliases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.data.local.AliasSource
import com.curro.app.domain.model.Contact
import com.curro.app.domain.repository.AliasRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [AliasesScreen] (SF-8.2 / US-051).
 *
 * Drives a [AliasesUiState] sourced from [AliasRepository.observeAll] and augmented
 * with transient dialog-open flags ([pendingDelete], [editTarget], [showAddDialog]).
 *
 * **Save flow** ([AliasesEvent.SaveAlias]): in SF-8.2 the config menu has no contact
 * picker, so explicit aliases store the typed contact name as the `displayName` and a
 * synthetic `"manual:<normalised-alias>"` as the `lookupKey`. This is clearly labelled
 * [AliasSource.EXPLICIT] so the real DAO handler knows this lookup-key is not from
 * ContactsContract. Future SF adds contact-picker to the config and upgrades the
 * lookup-key to a real LOOKUP_KEY from the system contacts DB.
 *
 * **Threading**: all [AliasRepository] calls are `suspend`; they run in [viewModelScope].
 * Room's Flow-returning `observeAll()` is collected in `init {}` so it stays alive across
 * dialog open/close.
 */
@HiltViewModel
class AliasesViewModel
    @Inject
    constructor(
        private val aliasRepo: AliasRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AliasesUiState())
        val uiState: StateFlow<AliasesUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                aliasRepo.observeAll().collect { aliases ->
                    _uiState.update { it.copy(aliases = aliases) }
                }
            }
        }

        fun onEvent(event: AliasesEvent) {
            when (event) {
                is AliasesEvent.AddPressed -> _uiState.update { it.copy(showAddDialog = true, editTarget = null) }
                is AliasesEvent.EditPressed ->
                    _uiState.update {
                        it.copy(editTarget = event.alias, showAddDialog = false, pendingDelete = null)
                    }
                is AliasesEvent.DeletePressed ->
                    _uiState.update {
                        it.copy(pendingDelete = event.alias, editTarget = null, showAddDialog = false)
                    }
                is AliasesEvent.ConfirmDelete -> handleConfirmDelete()
                is AliasesEvent.DismissDialog ->
                    _uiState.update {
                        it.copy(pendingDelete = null, editTarget = null, showAddDialog = false)
                    }
                is AliasesEvent.SaveAlias -> handleSaveAlias(event)
            }
        }

        private fun handleConfirmDelete() {
            val target = _uiState.value.pendingDelete ?: return
            viewModelScope.launch {
                aliasRepo.delete(target.alias)
            }
            _uiState.update { it.copy(pendingDelete = null) }
        }

        private fun handleSaveAlias(event: AliasesEvent.SaveAlias) {
            val alias = event.alias.trim()
            val contactName = event.contactName.trim()
            if (alias.isBlank() || contactName.isBlank()) return

            // SF-8.2 uses a synthetic lookup-key for EXPLICIT config-menu aliases.
            // A real contact-picker integration comes in a future SF.
            val syntheticLookupKey = "manual:${alias.lowercase()}"
            val contact =
                Contact(
                    lookupKey = syntheticLookupKey,
                    displayName = contactName,
                    phoneNumbers = emptyList(),
                    photoUri = null,
                )
            viewModelScope.launch {
                aliasRepo.learn(
                    alias = alias,
                    contact = contact,
                    source = AliasSource.EXPLICIT,
                )
            }
            _uiState.update { it.copy(showAddDialog = false, editTarget = null) }
        }
    }
