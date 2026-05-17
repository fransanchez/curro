package com.curro.app.presentation.config.failures

import com.curro.app.data.local.FailureKind

/**
 * User-initiated events on [FailuresScreen] (SF-8.6 / US-055).
 */
sealed interface FailuresEvent {
    /** Fran tapped a filter chip. `null` = "Todos" (show all). */
    data class FilterChanged(val kind: FailureKind?) : FailuresEvent

    /** Fran tapped "Borrar log" — show the confirmation dialog. */
    data object ClearPressed : FailuresEvent

    /** Fran confirmed "borrar log" in the dialog. */
    data object ConfirmClear : FailuresEvent

    /** Fran dismissed the clear confirmation dialog without clearing. */
    data object DismissClearDialog : FailuresEvent
}
