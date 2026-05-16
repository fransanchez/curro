package com.curro.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * On-device user settings (spec §9). SF-6.1 wires three keys; Phase 7 will add
 * favourite-app overrides; Phase 8 will add TTS voice/rate/pitch + the
 * incoming-call assistant toggle + "send failures to Fran".
 *
 * Defaults (returned on first read when the DataStore file does not yet contain
 * the key): `executeThreshold = 0.85f`, `confirmThreshold = 0.60f`,
 * `alwaysConfirm = false`. Pinned by spec §4.3 / §9.
 *
 * Validation: setters clamp out-of-range values. Out-of-order writes
 * (`confirm > execute`) are clamped at the setter; the policy never sees an
 * inconsistent pair.
 *
 * SF-6.1 wires the read path; SF-6.4 wires `alwaysConfirm` into the coordinator;
 * Phase 8 surfaces the toggles in the config menu.
 */
interface SettingsRepository {
    /**
     * Confidence threshold (inclusive) at and above which a `CONDITIONAL`
     * function executes directly. Default `0.85f`. Range `[0.0f, 1.0f]`.
     */
    val executeThreshold: Flow<Float>

    /**
     * Confidence threshold (inclusive) at and above which a `CONDITIONAL`
     * function asks for confirmation (and below which Curro clarifies).
     * Default `0.60f`. Range `[0.0f, executeThreshold]`.
     */
    val confirmThreshold: Flow<Float>

    /**
     * When `true`, every `CONDITIONAL` function escalates to confirmation
     * regardless of confidence (spec §4.3 always-escalate case #3 + spec §9).
     * Default `false`. Phase 8 surfaces the toggle; SF-6.4 wires it through the
     * coordinator.
     */
    val alwaysConfirm: Flow<Boolean>

    /**
     * Clamps `value` to `[0.0f, 1.0f]`. If the clamped value is below the
     * current [confirmThreshold], also lowers `confirmThreshold` to keep the
     * invariant `confirm ≤ execute`.
     */
    suspend fun setExecuteThreshold(value: Float)

    /**
     * Clamps `value` to `[0.0f, executeThreshold]`. The invariant
     * `confirm ≤ execute` is preserved; out-of-range writes are clamped and
     * logged at `WARN`, never thrown.
     */
    suspend fun setConfirmThreshold(value: Float)

    /** Persists the always-confirm flag. Booleans cannot be out of range. */
    suspend fun setAlwaysConfirm(value: Boolean)
}
