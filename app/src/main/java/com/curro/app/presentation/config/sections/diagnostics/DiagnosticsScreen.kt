package com.curro.app.presentation.config.sections.diagnostics

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.curro.app.R
import com.curro.app.data.permissions.PermissionInfo
import com.curro.app.presentation.config.sections.diagnostics.components.DiagnosticSection
import com.curro.app.presentation.config.sections.diagnostics.components.HyperOsSection
import com.curro.app.presentation.config.sections.diagnostics.components.PermissionRow
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Diagnostics screen (US-059 / SF-8.10) — replaces the `config/diagnostics` placeholder.
 *
 * Five sections in a [LazyColumn]:
 * 1. App — version, version code, build type.
 * 2. Modelo — name, state (Loaded / Warming / Cold), last warm-up latency, last inference latency.
 * 3. Launcher — am-I-default?
 * 4. Permisos — all Curro permissions + notification-listener gate.
 * 5. HyperOS — battery deep-link button + autostart help.
 *
 * No [androidx.compose.material3.Scaffold] / TopAppBar — the parent [CurroNavHost] Scaffold
 * provides [Modifier.padding(innerPadding)] (No-Double-Padding rule). Back chevron at TopStart.
 *
 * Side effects from the ViewModel (opening Settings intents) are collected in a [LaunchedEffect].
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is DiagnosticsSideEffect.OpenBatterySettings -> context.startActivity(effect.intent)
            }
        }
    }

    DiagnosticsContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun DiagnosticsContent(
    uiState: DiagnosticsUiState,
    onEvent: (DiagnosticsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = Dimens.MinTapTarget + CurroSpacing.l,
                        start = CurroSpacing.m,
                        end = CurroSpacing.m,
                        bottom = CurroSpacing.xl,
                    ),
        ) {
            item {
                DiagnosticSection(title = R.string.copy_config_diagnostics_section_app) {
                    AppInfoRows(uiState.app)
                }
                Spacer(modifier = Modifier.height(CurroSpacing.m))
            }
            item {
                DiagnosticSection(title = R.string.copy_config_diagnostics_section_model) {
                    ModelInfoRows(uiState.model)
                }
                Spacer(modifier = Modifier.height(CurroSpacing.m))
            }
            item {
                DiagnosticSection(title = R.string.copy_config_diagnostics_section_launcher) {
                    LauncherInfoRow(uiState.isDefaultLauncher)
                }
                Spacer(modifier = Modifier.height(CurroSpacing.m))
            }
            item {
                DiagnosticSection(title = R.string.copy_config_diagnostics_section_permissions) {
                    PermissionsRows(uiState.permissions)
                }
                Spacer(modifier = Modifier.height(CurroSpacing.m))
            }
            item {
                DiagnosticSection(title = R.string.copy_config_diagnostics_section_hyperos) {
                    HyperOsSection(
                        onBatteryClick = { onEvent(DiagnosticsEvent.OpenBatterySettings) },
                    )
                }
            }
        }

        // Back chevron — TopStart overlay (No-Double-Padding rule).
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .size(Dimens.MinTapTarget),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier.size(Dimens.LargeIconSize),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── Private section-body composables ──────────────────────────────────────────────────────────

@Composable
private fun AppInfoRows(app: AppInfo) {
    Text(
        text = stringResource(R.string.copy_config_diagnostics_version, app.version, app.versionCode, app.buildType),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ModelInfoRows(model: ModelInfo) {
    Text(
        text = stringResource(R.string.copy_config_diagnostics_model_name, model.name),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val stateRes =
        when (model.state) {
            ModelState.Loaded -> R.string.copy_config_diagnostics_model_state_loaded
            ModelState.Warming -> R.string.copy_config_diagnostics_model_state_warming
            ModelState.Cold -> R.string.copy_config_diagnostics_model_state_cold
        }
    Text(
        text = stringResource(stateRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val warmUpText =
        model.lastWarmUpMs?.let {
            stringResource(R.string.copy_config_diagnostics_model_warmup_latency, it)
        } ?: stringResource(R.string.copy_config_diagnostics_model_latency_unknown)
    Text(
        text = warmUpText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val inferenceText =
        model.lastInferenceMs?.let {
            stringResource(R.string.copy_config_diagnostics_model_inference_latency, it)
        } ?: stringResource(R.string.copy_config_diagnostics_model_latency_unknown)
    Text(
        text = inferenceText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LauncherInfoRow(isDefault: Boolean) {
    val textRes =
        if (isDefault) {
            R.string.copy_config_diagnostics_default_yes
        } else {
            R.string.copy_config_diagnostics_default_no
        }
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun PermissionsRows(permissions: List<PermissionInfo>) {
    permissions.forEach { info ->
        PermissionRow(info = info)
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────────────────────

private val previewUiState =
    DiagnosticsUiState(
        app = AppInfo(version = "0.1.0", versionCode = 1, buildType = "debug"),
        model =
            ModelInfo(
                name = "FunctionGemma270M",
                state = ModelState.Loaded,
                lastWarmUpMs = 320L,
                lastInferenceMs = 180L,
            ),
        isDefaultLauncher = true,
        permissions =
            listOf(
                PermissionInfo("RECORD_AUDIO", R.string.copy_config_diagnostics_permission_record_audio, true),
                PermissionInfo("READ_CONTACTS", R.string.copy_config_diagnostics_permission_read_contacts, true),
                PermissionInfo("CALL_PHONE", R.string.copy_config_diagnostics_permission_call_phone, false),
                PermissionInfo(
                    "NOTIFICATION_LISTENER",
                    R.string.copy_config_diagnostics_permission_notification_listener,
                    false,
                ),
            ),
    )

@Preview(name = "DiagnosticsContent — light", widthDp = 412, heightDp = 900)
@Composable
private fun DiagnosticsContentLightPreview() {
    CurroTheme {
        Surface {
            DiagnosticsContent(
                uiState = previewUiState,
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "DiagnosticsContent — dark", widthDp = 412, heightDp = 900, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DiagnosticsContentDarkPreview() {
    CurroTheme {
        Surface {
            DiagnosticsContent(
                uiState = previewUiState,
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "DiagnosticsContent — large font 1.5×", widthDp = 412, heightDp = 900, fontScale = 1.5f)
@Composable
private fun DiagnosticsContentLargeFontPreview() {
    CurroTheme {
        Surface {
            DiagnosticsContent(
                uiState = previewUiState,
                onEvent = {},
                onBack = {},
            )
        }
    }
}
