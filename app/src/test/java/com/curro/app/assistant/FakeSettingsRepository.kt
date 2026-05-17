package com.curro.app.assistant

import com.curro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [SettingsRepository] for JVM coordinator tests (SF-6.1+).
 *
 * Each property is a [MutableStateFlow] so a test can mutate the value (e.g.
 * SF-6.4's `alwaysConfirmValue = true`) and the next `.first()` in the
 * coordinator picks the new value up.
 *
 * Defaults match the SF-6.1 / spec §4.3 values (`0.85f / 0.60f / false`).
 * Phase-8 new keys are also represented with sensible defaults.
 */
class FakeSettingsRepository(
    executeThreshold: Float = DEFAULT_EXECUTE,
    confirmThreshold: Float = DEFAULT_CONFIRM,
    alwaysConfirm: Boolean = false,
) : SettingsRepository {
    private val executeFlow = MutableStateFlow(executeThreshold)
    private val confirmFlow = MutableStateFlow(confirmThreshold)
    private val alwaysConfirmFlow = MutableStateFlow(alwaysConfirm)
    private val incomingCallFlow = MutableStateFlow(false)
    private val sendFailuresFlow = MutableStateFlow(false)
    private val launcherFavouritesOverrideFlow = MutableStateFlow<List<String>?>(null)
    private val ttsVoiceNameFlow = MutableStateFlow<String?>(null)
    private val ttsRateFlow = MutableStateFlow(DEFAULT_TTS_RATE)
    private val ttsPitchFlow = MutableStateFlow(DEFAULT_TTS_PITCH)

    /** Mutable backing — SF-6.4 tests flip this to drive the toggle branch. */
    var alwaysConfirmValue: Boolean
        get() = alwaysConfirmFlow.value
        set(value) {
            alwaysConfirmFlow.value = value
        }

    // Setter-call tracking for tests (SF-8.1)
    var incomingCallModeSetCalls: MutableList<Boolean> = mutableListOf()
    var sendFailuresSetCalls: MutableList<Boolean> = mutableListOf()

    override val executeThreshold: Flow<Float> = executeFlow.asStateFlow()
    override val confirmThreshold: Flow<Float> = confirmFlow.asStateFlow()
    override val alwaysConfirm: Flow<Boolean> = alwaysConfirmFlow.asStateFlow()
    override val incomingCallModeEnabled: Flow<Boolean> = incomingCallFlow.asStateFlow()
    override val sendFailuresEnabled: Flow<Boolean> = sendFailuresFlow.asStateFlow()
    override val launcherFavouritesOverride: Flow<List<String>?> = launcherFavouritesOverrideFlow.asStateFlow()
    override val ttsVoiceName: Flow<String?> = ttsVoiceNameFlow.asStateFlow()
    override val ttsRate: Flow<Float> = ttsRateFlow.asStateFlow()
    override val ttsPitch: Flow<Float> = ttsPitchFlow.asStateFlow()

    override suspend fun setExecuteThreshold(value: Float) {
        executeFlow.value = value.coerceIn(0f, 1f)
    }

    override suspend fun setConfirmThreshold(value: Float) {
        confirmFlow.value = value.coerceIn(0f, executeFlow.value)
    }

    override suspend fun setAlwaysConfirm(value: Boolean) {
        alwaysConfirmFlow.value = value
    }

    override suspend fun setIncomingCallModeEnabled(value: Boolean) {
        incomingCallModeSetCalls += value
        incomingCallFlow.value = value
    }

    override suspend fun setSendFailuresEnabled(value: Boolean) {
        sendFailuresSetCalls += value
        sendFailuresFlow.value = value
    }

    override suspend fun setLauncherFavouritesOverride(packages: List<String>?) {
        launcherFavouritesOverrideFlow.value = packages
    }

    override suspend fun setTtsVoiceName(name: String?) {
        ttsVoiceNameFlow.value = name
    }

    override suspend fun setTtsRate(rate: Float) {
        ttsRateFlow.value = rate.coerceIn(0.5f, 1.5f)
    }

    override suspend fun setTtsPitch(pitch: Float) {
        ttsPitchFlow.value = pitch.coerceIn(0.5f, 2.0f)
    }

    private companion object {
        const val DEFAULT_EXECUTE = 0.85f
        const val DEFAULT_CONFIRM = 0.60f
        const val DEFAULT_TTS_RATE = 0.88f
        const val DEFAULT_TTS_PITCH = 1.0f
    }
}
