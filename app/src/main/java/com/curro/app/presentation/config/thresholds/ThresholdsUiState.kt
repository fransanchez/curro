package com.curro.app.presentation.config.thresholds

/**
 * UI state for [ThresholdsScreen] (SF-8.5 / US-054).
 *
 * Mirrors the three settings from [com.curro.app.domain.repository.SettingsRepository]:
 * - [executeThreshold]: float in `[0.0, 1.0]`. A `CONDITIONAL` function executes directly
 *   when FunctionGemma's confidence is ≥ this value. Default `0.85f`.
 * - [confirmThreshold]: float in `[0.0, executeThreshold]`. A `CONDITIONAL` function asks
 *   for confirmation when confidence is in `[confirmThreshold, executeThreshold)`. Default `0.60f`.
 * - [alwaysConfirm]: when `true`, every `CONDITIONAL` function asks for confirmation
 *   regardless of confidence. Default `false`.
 *
 * The ViewModel keeps the invariant `confirmThreshold ≤ executeThreshold` by delegating to
 * [com.curro.app.data.local.SettingsDataStore]'s clamping setters — the UI state always
 * reflects the persisted (clamped) values.
 */
data class ThresholdsUiState(
    val executeThreshold: Float = DEFAULT_EXECUTE,
    val confirmThreshold: Float = DEFAULT_CONFIRM,
    val alwaysConfirm: Boolean = false,
) {
    companion object {
        const val DEFAULT_EXECUTE: Float = 0.85f
        const val DEFAULT_CONFIRM: Float = 0.60f
        const val THRESHOLD_MIN: Float = 0.0f
        const val THRESHOLD_MAX: Float = 1.0f
    }
}
