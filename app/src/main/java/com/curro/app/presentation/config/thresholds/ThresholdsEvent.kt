package com.curro.app.presentation.config.thresholds

/**
 * User-initiated events on [ThresholdsScreen] (SF-8.5 / US-054).
 *
 * Every event is immediately persisted — there is no staged "Save" step.
 * [com.curro.app.data.local.SettingsDataStore] clamps values and preserves the invariant
 * `confirmThreshold ≤ executeThreshold`; the UI re-reads the clamped value from the
 * [com.curro.app.domain.repository.SettingsRepository] Flow.
 */
sealed interface ThresholdsEvent {
    /** Fran moved the execute-threshold slider. Clamped to `[0.0, 1.0]` by the DataStore setter. */
    data class ExecuteThresholdChanged(val value: Float) : ThresholdsEvent

    /** Fran moved the confirm-threshold slider. Clamped to `[0.0, executeThreshold]`. */
    data class ConfirmThresholdChanged(val value: Float) : ThresholdsEvent

    /** Fran toggled the "always confirm" switch. */
    data class AlwaysConfirmChanged(val enabled: Boolean) : ThresholdsEvent
}
