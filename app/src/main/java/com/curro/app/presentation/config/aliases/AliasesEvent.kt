package com.curro.app.presentation.config.aliases

import com.curro.app.domain.repository.AliasView

/**
 * User-initiated events on [AliasesScreen] (SF-8.2 / US-051).
 */
sealed interface AliasesEvent {
    /** FAB / add button tapped — opens [AddOrEditAliasDialog] in add mode. */
    data object AddPressed : AliasesEvent

    /** Edit icon on an [AliasView] row tapped — opens [AddOrEditAliasDialog] in edit mode. */
    data class EditPressed(val alias: AliasView) : AliasesEvent

    /** Delete icon on an [AliasView] row tapped — sets [AliasesUiState.pendingDelete]. */
    data class DeletePressed(val alias: AliasView) : AliasesEvent

    /** "Borrar" button in [DeleteAliasConfirmDialog] tapped — calls [AliasRepository.delete]. */
    data object ConfirmDelete : AliasesEvent

    /** Any "Cancelar" / dismiss in a dialog — clears all dialog state. */
    data object DismissDialog : AliasesEvent

    /**
     * "Guardar" in [AddOrEditAliasDialog] — persists the alias via [AliasRepository.learn].
     *
     * [alias] and [contactName] are the raw strings from the text fields. The ViewModel
     * normalises [alias] before calling [AliasRepository.learn].
     *
     * In SF-8.2 the contact is identified by name only (no picker — that lands in a future SF
     * when [ContactsProvider] integration is available from the config layer). The [contactName]
     * is stored as-is as the display name and a synthetic `LOOKUP_KEY` of `"manual:<normalised>"`
     * is used for EXPLICIT aliases written from the config menu.
     */
    data class SaveAlias(val alias: String, val contactName: String) : AliasesEvent
}
