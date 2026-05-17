package com.curro.app.presentation.config

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.R
import com.curro.app.data.failures.FailedCommandExporter
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.FailedCommandLog
import com.curro.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [ConfigMenuScreen] (SF-8.1 / US-050).
 *
 * Combines four flows (alias count, failures count, two toggles) into a stable
 * [ConfigUiState] whose 9 sections are rebuilt on every emission.
 *
 * **SF-8.1 toggle behaviour**: the inline toggles for "Modo asistente de llamadas"
 * (SF-8.7) and "Compartir fallos con Fran" (SF-8.8) render the current value from
 * `SettingsRepository` but their [onEvent] handler logs `Log.w` and returns without
 * mutating any setting. This is the only acceptable form of "inert" in Curro —
 * never silent, always visible to the implementer in logcat.
 *
 * **SF-8.8 wiring**: the `sendFailuresEnabled` setter is connected here in SF-8.8.
 */
@HiltViewModel
class ConfigViewModel
    @Inject
    constructor(
        private val aliasRepo: AliasRepository,
        private val failedLog: FailedCommandLog,
        private val settingsRepo: SettingsRepository,
        private val exporter: FailedCommandExporter,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        val uiState: StateFlow<ConfigUiState> =
            combine(
                aliasRepo.observeAll(),
                failedLog.observeRecent(limit = 50),
                settingsRepo.incomingCallModeEnabled,
                settingsRepo.sendFailuresEnabled,
            ) { aliases, failures, inCall, sendFails ->
                ConfigUiState(
                    sections =
                        buildSections(
                            aliasCount = aliases.size,
                            failureCount = failures.size,
                            incomingCallEnabled = inCall,
                            sendFailuresEnabled = sendFails,
                        ),
                    incomingCallEnabled = inCall,
                    sendFailuresEnabled = sendFails,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue =
                    ConfigUiState(
                        sections = buildSections(0, 0, incomingCallEnabled = false, sendFailuresEnabled = false),
                        incomingCallEnabled = false,
                        sendFailuresEnabled = false,
                    ),
            )

        fun onEvent(event: ConfigEvent) {
            when (event) {
                is ConfigEvent.ToggleChanged -> handleToggleChanged(event)
            }
        }

        private fun handleToggleChanged(event: ConfigEvent.ToggleChanged) {
            when (event.section.onChangeWillBeWiredInSF) {
                // SF-8.1 labeled the send-failures toggle "SF-8.8" (the SF that wires it).
                // SF-8.7 is the actual implementation SF — we match on the label in the section.
                "SF-8.8" ->
                    // "Compartir fallos con Fran" — persist the setting and trigger an export.
                    viewModelScope.launch {
                        settingsRepo.setSendFailuresEnabled(event.newValue)
                        if (event.newValue) exporter.exportUnsent()
                    }
                "SF-8.7" ->
                    // Incoming-call assistant mode — inert until a later SF wires it.
                    Log.w(TAG, "Modo asistente de llamadas — toggle not yet wired; onChangeWillBeWiredInSF=SF-8.7")
                else ->
                    Log.w(
                        TAG,
                        "ToggleChanged(${event.section.titleResId}) — wired in " +
                            "${event.section.onChangeWillBeWiredInSF}; inert in SF-8.7",
                    )
            }
        }

        private fun buildSections(
            aliasCount: Int,
            failureCount: Int,
            incomingCallEnabled: Boolean,
            sendFailuresEnabled: Boolean,
        ): List<ConfigSection> =
            listOf(
                ConfigSection.Navigable(
                    titleResId = R.string.copy_config_section_aliases,
                    summary = context.getString(R.string.copy_config_summary_aliases_count, aliasCount),
                    route = "config/aliases",
                ),
                ConfigSection.Navigable(
                    titleResId = R.string.copy_config_section_favourites,
                    summary = null,
                    route = "config/favourites",
                ),
                ConfigSection.Navigable(
                    titleResId = R.string.copy_config_section_tts,
                    summary = null,
                    route = "config/tts",
                ),
                ConfigSection.Navigable(
                    titleResId = R.string.copy_config_section_thresholds,
                    summary = null,
                    route = "config/thresholds",
                ),
                ConfigSection.Navigable(
                    titleResId = R.string.copy_config_section_failures,
                    summary = context.getString(R.string.copy_config_summary_failures_count, failureCount),
                    route = "config/failures",
                ),
                ConfigSection.Toggle(
                    titleResId = R.string.copy_config_section_incoming_call,
                    helpResId = R.string.copy_config_incoming_call_help_short,
                    value = incomingCallEnabled,
                    onChangeWillBeWiredInSF = "SF-8.7",
                ),
                ConfigSection.Toggle(
                    titleResId = R.string.copy_config_section_send_failures,
                    helpResId = R.string.copy_config_share_failures_help_short,
                    value = sendFailuresEnabled,
                    onChangeWillBeWiredInSF = "SF-8.8",
                ),
                ConfigSection.Navigable(
                    titleResId = R.string.copy_config_section_reset,
                    summary = null,
                    route = "config/reset",
                    destructive = true,
                ),
                ConfigSection.Navigable(
                    titleResId = R.string.copy_config_section_diagnostics,
                    summary = null,
                    route = "config/diagnostics",
                ),
            )

        private companion object {
            const val SUBSCRIBE_TIMEOUT_MS = 5_000L
            const val TAG = "Curro/Config"
        }
    }
