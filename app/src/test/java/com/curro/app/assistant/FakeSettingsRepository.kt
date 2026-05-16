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
 */
class FakeSettingsRepository(
    executeThreshold: Float = DEFAULT_EXECUTE,
    confirmThreshold: Float = DEFAULT_CONFIRM,
    alwaysConfirm: Boolean = false,
) : SettingsRepository {
    private val executeFlow = MutableStateFlow(executeThreshold)
    private val confirmFlow = MutableStateFlow(confirmThreshold)
    private val alwaysConfirmFlow = MutableStateFlow(alwaysConfirm)

    /** Mutable backing — SF-6.4 tests flip this to drive the toggle branch. */
    var alwaysConfirmValue: Boolean
        get() = alwaysConfirmFlow.value
        set(value) {
            alwaysConfirmFlow.value = value
        }

    override val executeThreshold: Flow<Float> = executeFlow.asStateFlow()
    override val confirmThreshold: Flow<Float> = confirmFlow.asStateFlow()
    override val alwaysConfirm: Flow<Boolean> = alwaysConfirmFlow.asStateFlow()

    override suspend fun setExecuteThreshold(value: Float) {
        executeFlow.value = value.coerceIn(0f, 1f)
    }

    override suspend fun setConfirmThreshold(value: Float) {
        confirmFlow.value = value.coerceIn(0f, executeFlow.value)
    }

    override suspend fun setAlwaysConfirm(value: Boolean) {
        alwaysConfirmFlow.value = value
    }

    private companion object {
        const val DEFAULT_EXECUTE = 0.85f
        const val DEFAULT_CONFIRM = 0.60f
    }
}
