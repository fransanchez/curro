package com.curro.app.presentation.config.reset

/**
 * UI state for [ResetScreen] (SF-8.8 / US-058).
 *
 * @param showConfirmDialog True when the destructive confirmation dialog is visible.
 * @param resetComplete True once the four-way parallel reset has completed. The screen
 *     shows a success message and navigates back automatically.
 */
data class ResetUiState(
    val showConfirmDialog: Boolean = false,
    val resetComplete: Boolean = false,
)
