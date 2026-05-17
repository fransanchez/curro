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

    // -----------------------------------------------------------------------
    // SF-8.1 (US-050) — Phase-8 new flows
    // -----------------------------------------------------------------------

    /**
     * Whether Curro's incoming-call assistant mode is enabled (spec §9).
     * Default `false`. SF-8.7 wires the setter caller; SF-8.1 declares both
     * so the [ConfigViewModel] is self-sufficient from day one.
     */
    val incomingCallModeEnabled: Flow<Boolean>

    /**
     * Whether the "Compartir fallos con Fran" export is enabled (spec §9).
     * Default `false`. SF-8.8 wires the setter caller.
     */
    val sendFailuresEnabled: Flow<Boolean>

    /**
     * Enables or disables the incoming-call assistant mode.
     * SF-8.7 calls this; SF-8.1 provides the declaration so the interface
     * is complete and SF-8.7 needs no extension.
     */
    suspend fun setIncomingCallModeEnabled(value: Boolean)

    /**
     * Enables or disables the "send failures to Fran" export.
     * SF-8.8 calls this.
     */
    suspend fun setSendFailuresEnabled(value: Boolean)

    // -----------------------------------------------------------------------
    // SF-8.3 (US-052) — launcher favourites override
    // -----------------------------------------------------------------------

    /**
     * Ordered list of package names that override the recency-scored home grid,
     * or `null` when the automatic scoring is active.
     *
     * `null` → automatic (SF-7.4 scoring + seed padding).
     * Non-null → exactly these packages in this order; SF-8.3's
     * [RecencyFavoriteAppsRepositoryImpl.loadFavorites] checks this before decay.
     */
    val launcherFavouritesOverride: Flow<List<String>?>

    /**
     * Persists a manual favourites override. Pass `null` to revert to automatic.
     * The DataStore stores a comma-joined string; an empty list or null both write
     * an empty string which reads back as `null`.
     */
    suspend fun setLauncherFavouritesOverride(packages: List<String>?)

    // -----------------------------------------------------------------------
    // SF-8.4 (US-053) — TTS voice / rate / pitch
    // -----------------------------------------------------------------------

    /**
     * The TTS voice name selected by Fran, or `null` for the system default
     * Spanish voice (SF-8.4). Default `null`.
     */
    val ttsVoiceName: Flow<String?>

    /**
     * TTS speech rate. Default `0.88f`. Range `[0.5f, 1.5f]` (clamped on write).
     */
    val ttsRate: Flow<Float>

    /**
     * TTS pitch. Default `1.0f`. Range `[0.5f, 2.0f]` (clamped on write).
     */
    val ttsPitch: Flow<Float>

    /** Persists the TTS voice name. `null` resets to the system default. */
    suspend fun setTtsVoiceName(name: String?)

    /** Persists the TTS speech rate. Clamped to `[0.5f, 1.5f]`. */
    suspend fun setTtsRate(rate: Float)

    /** Persists the TTS pitch. Clamped to `[0.5f, 2.0f]`. */
    suspend fun setTtsPitch(pitch: Float)
}
