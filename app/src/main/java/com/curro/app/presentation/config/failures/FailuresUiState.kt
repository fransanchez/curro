package com.curro.app.presentation.config.failures

import com.curro.app.data.local.FailureKind

/**
 * UI state for [FailuresScreen] (SF-8.6 / US-055).
 *
 * @param allFailures Full list of recent failures from [com.curro.app.domain.repository.FailedCommandLog].
 * @param activeFilter The [FailureKind] filter chip selected, or `null` for "all".
 * @param showClearDialog True when the "borrar log" confirmation dialog is visible.
 */
data class FailuresUiState(
    val allFailures: List<FailureView> = emptyList(),
    val activeFilter: FailureKind? = null,
    val showClearDialog: Boolean = false,
) {
    /** Failures after applying [activeFilter]. */
    val visibleFailures: List<FailureView>
        get() =
            if (activeFilter == null) {
                allFailures
            } else {
                allFailures.filter { it.kind == activeFilter }
            }
}
