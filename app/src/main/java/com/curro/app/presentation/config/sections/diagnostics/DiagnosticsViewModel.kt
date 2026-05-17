package com.curro.app.presentation.config.sections.diagnostics

import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.BuildConfig
import com.curro.app.data.launcher.BatterySettingsIntents
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.data.permissions.GrantedPermissionsReader
import com.curro.app.data.permissions.PermissionInfo
import com.curro.app.domain.repository.EngineMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Diagnostics screen (US-059 / SF-8.10).
 *
 * Exposes a [uiState] that combines:
 * - [DefaultLauncherDetector.flow] — is-default?
 * - [refreshTrigger] — fires once on init and once per [ON_RESUME].
 *
 * On each trigger the ViewModel reads [EngineMetrics] and [GrantedPermissionsReader.snapshot]
 * synchronously (both are O(1) volatile reads / Binder calls).
 *
 * [ProcessLifecycleOwner] subscription is guarded by try/catch so the class is JVM-testable
 * without Robolectric's lifecycle machinery.
 */
@HiltViewModel
class DiagnosticsViewModel
    @Inject
    constructor(
        private val engineMetrics: EngineMetrics,
        private val detector: DefaultLauncherDetector,
        private val permissionsReader: GrantedPermissionsReader,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        private val sideEffectsChannel = Channel<DiagnosticsSideEffect>(Channel.BUFFERED)
        val sideEffects: Flow<DiagnosticsSideEffect> = sideEffectsChannel.receiveAsFlow()

        private val resumeObserver =
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    refreshTrigger.tryEmit(Unit)
                }
            }

        init {
            @Suppress("TooGenericExceptionCaught")
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(resumeObserver)
            } catch (_: Exception) {
                // Suppressed: tests run without ProcessLifecycleOwner; the refreshTrigger
                // is driven manually in tests via emit().
            }
        }

        override fun onCleared() {
            @Suppress("TooGenericExceptionCaught")
            try {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(resumeObserver)
            } catch (_: Exception) {
                // no-op outside process lifecycle
            }
        }

        val uiState =
            combine(
                detector.flow,
                refreshTrigger.onStart { emit(Unit) },
            ) { isDefault, _ ->
                DiagnosticsUiState(
                    app =
                        AppInfo(
                            version = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                            buildType = BuildConfig.BUILD_TYPE,
                        ),
                    model =
                        ModelInfo(
                            name = engineMetrics.modelName(),
                            state = computeModelState(),
                            lastWarmUpMs = engineMetrics.lastWarmUpLatencyMs(),
                            lastInferenceMs = engineMetrics.lastInferenceLatencyMs(),
                        ),
                    isDefaultLauncher = isDefault,
                    permissions = permissionsReader.snapshot(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT_MS),
                initialValue = DiagnosticsUiState.Initial,
            )

        fun onEvent(event: DiagnosticsEvent) {
            when (event) {
                DiagnosticsEvent.OpenBatterySettings -> {
                    viewModelScope.launch {
                        sideEffectsChannel.send(
                            DiagnosticsSideEffect.OpenBatterySettings(
                                BatterySettingsIntents.openAppDetailsIntent(context),
                            ),
                        )
                    }
                }
            }
        }

        private suspend fun computeModelState(): ModelState =
            when {
                !engineMetrics.isReady() -> ModelState.Cold
                engineMetrics.lastInferenceLatencyMs() == null -> ModelState.Warming
                else -> ModelState.Loaded
            }

        private companion object {
            /** Stop-timeout for [SharingStarted.WhileSubscribed] — 5 s. */
            const val SUBSCRIBE_STOP_TIMEOUT_MS = 5_000L
        }
    }

// ── State ─────────────────────────────────────────────────────────────────────────────────────

data class DiagnosticsUiState(
    val app: AppInfo,
    val model: ModelInfo,
    val isDefaultLauncher: Boolean,
    val permissions: List<PermissionInfo>,
) {
    companion object {
        val Initial =
            DiagnosticsUiState(
                app = AppInfo(version = "", versionCode = 0, buildType = ""),
                model = ModelInfo(name = "", state = ModelState.Cold, lastWarmUpMs = null, lastInferenceMs = null),
                isDefaultLauncher = false,
                permissions = emptyList(),
            )
    }
}

data class AppInfo(
    val version: String,
    val versionCode: Int,
    val buildType: String,
)

data class ModelInfo(
    val name: String,
    val state: ModelState,
    val lastWarmUpMs: Long?,
    val lastInferenceMs: Long?,
)

enum class ModelState {
    Loaded,
    Warming,
    Cold,
}

// ── Event / SideEffect ────────────────────────────────────────────────────────────────────────

sealed interface DiagnosticsEvent {
    data object OpenBatterySettings : DiagnosticsEvent
}

sealed interface DiagnosticsSideEffect {
    data class OpenBatterySettings(val intent: Intent) : DiagnosticsSideEffect
}
