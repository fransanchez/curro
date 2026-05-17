package com.curro.app.presentation.config.aliases

import com.curro.app.domain.repository.AliasView

/**
 * UI state for [AliasesScreen] (SF-8.2 / US-051).
 *
 * [aliases] is always a stable, ordered list from `AliasRepository.observeAll()`
 * (useCount DESC, lastUsedAtMs DESC). An empty list renders [EmptyAliasesState].
 *
 * [pendingDelete] — when non-null, the [DeleteAliasConfirmDialog] is shown for
 * this alias. The confirmation fires [AliasesEvent.ConfirmDelete].
 *
 * [editTarget] — when non-null, the [AddOrEditAliasDialog] opens in edit mode for
 * this alias. Null opens it in add mode.
 *
 * [showAddDialog] — true when the FAB / add button has been pressed and the add
 * dialog should be shown with an empty alias and contact name.
 */
data class AliasesUiState(
    val aliases: List<AliasView> = emptyList(),
    val pendingDelete: AliasView? = null,
    val editTarget: AliasView? = null,
    val showAddDialog: Boolean = false,
)
