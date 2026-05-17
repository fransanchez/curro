package com.curro.app.presentation.config.reset

/**
 * User-initiated events on [ResetScreen] (SF-8.8 / US-058).
 */
sealed interface ResetEvent {
    /** Fran tapped the "Borrar todo el aprendizaje" CTA — show the confirmation dialog. */
    data object ResetPressed : ResetEvent

    /** Fran confirmed the destructive reset in the dialog. */
    data object ConfirmReset : ResetEvent

    /** Fran dismissed the confirmation dialog without resetting. */
    data object DismissDialog : ResetEvent
}
