package com.curro.app.presentation.config

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.curro.app.R
import com.curro.app.presentation.config.components.ConfigSectionActionRow
import com.curro.app.presentation.config.components.ConfigSectionRow
import com.curro.app.presentation.config.components.ConfigSectionToggleRow
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * The real Fran-only config menu (SF-8.1 / US-050), replacing the Phase-0
 * `ConfigMenuPlaceholderScreen`.
 *
 * Nine [ConfigSection] rows in a [LazyColumn]; seven navigate to sub-routes,
 * two are inline toggles (inert in SF-8.1 — their setters land in SF-8.7 /
 * SF-8.8). The back chevron at `TopStart` follows the No-Double-Padding rule
 * (`navigation-patterns` rule 1 — no `Scaffold`, no `TopAppBar`).
 *
 * **Density**: this screen is for Fran, not his father. Row height 72 dp
 * (`launcher-ui` rule 5 — the senior-first 96 dp floor does NOT apply here).
 *
 * **Ordering**: Alias / Aplicaciones favoritas / Voz y velocidad / Cuándo
 * confirmar / Lo que Curro no entendió / Modo asistente de llamadas /
 * Compartir fallos / Reset de aprendizaje / Versión y diagnóstico.
 */
@Composable
fun ConfigMenuScreen(
    onBack: () -> Unit,
    onNavigateToSection: (String) -> Unit,
    onOpenHomeSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Drain the openHomeSettings one-shot event emitted by the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.openHomeSettingsEvents.collect { onOpenHomeSettings() }
    }
    ConfigMenuContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onNavigateToSection = onNavigateToSection,
        modifier = modifier,
    )
}

@Composable
internal fun ConfigMenuContent(
    uiState: ConfigUiState,
    onEvent: (ConfigEvent) -> Unit,
    onBack: () -> Unit,
    onNavigateToSection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = CurroSpacing.xl,
                        end = CurroSpacing.xl,
                        top = Dimens.MinTapTarget + CurroSpacing.l,
                        bottom = CurroSpacing.xl,
                    ),
            verticalArrangement = Arrangement.spacedBy(CurroSpacing.s),
        ) {
            items(uiState.sections, key = { section -> sectionKey(section) }) { section ->
                when (section) {
                    is ConfigSection.Navigable ->
                        ConfigSectionRow(
                            section = section,
                            onNavigate = onNavigateToSection,
                        )
                    is ConfigSection.Toggle ->
                        ConfigSectionToggleRow(
                            section = section,
                            onEvent = onEvent,
                        )
                    is ConfigSection.Action ->
                        ConfigSectionActionRow(
                            section = section,
                            onEvent = onEvent,
                        )
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = CurroSpacing.s, top = CurroSpacing.s)
                    .size(Dimens.MinTapTarget),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier.size(Dimens.LargeIconSize),
            )
        }
    }
}

/**
 * Stable LazyColumn key for a [ConfigSection].
 *
 * Uses [ConfigSection.Navigable.titleResId] for navigable rows, an XOR with the
 * current boolean for toggle rows (stable across recompositions since the resId
 * never changes at runtime), and the plain resId for action rows.
 */
private fun sectionKey(section: ConfigSection): Int =
    when (section) {
        is ConfigSection.Navigable -> section.titleResId
        is ConfigSection.Toggle -> section.titleResId xor if (section.value) 1 else 0
        is ConfigSection.Action -> section.titleResId
    }

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Suppress("LongMethod")
private fun previewUiState(
    inCall: Boolean = false,
    sendFails: Boolean = false,
): ConfigUiState {
    val sections =
        listOf(
            ConfigSection.Navigable(
                titleResId = R.string.copy_config_section_aliases,
                summary = "3 alias guardados",
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
                summary = "2 fallos sin revisar",
                route = "config/failures",
            ),
            ConfigSection.Toggle(
                titleResId = R.string.copy_config_section_incoming_call,
                helpResId = R.string.copy_config_incoming_call_help_short,
                value = inCall,
                onChangeWillBeWiredInSF = "SF-8.7",
            ),
            ConfigSection.Toggle(
                titleResId = R.string.copy_config_section_send_failures,
                helpResId = R.string.copy_config_share_failures_help_short,
                value = sendFails,
                onChangeWillBeWiredInSF = "SF-8.8",
            ),
            ConfigSection.Navigable(
                titleResId = R.string.copy_config_section_reset,
                summary = null,
                route = "config/reset",
                destructive = true,
            ),
            ConfigSection.Action(
                titleResId = R.string.copy_config_open_home_settings,
                summaryResId = R.string.copy_config_open_home_settings_help,
                event = ConfigEvent.OpenHomeSettings,
            ),
            ConfigSection.Navigable(
                titleResId = R.string.copy_config_section_diagnostics,
                summary = null,
                route = "config/diagnostics",
            ),
        )
    return ConfigUiState(
        sections = sections,
        incomingCallEnabled = inCall,
        sendFailuresEnabled = sendFails,
    )
}

@Preview(name = "ConfigMenuScreen — Light", widthDp = 412, heightDp = 800)
@Composable
private fun ConfigMenuLightPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigMenuContent(
                uiState = previewUiState(),
                onEvent = {},
                onBack = {},
                onNavigateToSection = {},
            )
        }
    }
}

@Preview(name = "ConfigMenuScreen — Dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 800)
@Composable
private fun ConfigMenuDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigMenuContent(
                uiState = previewUiState(),
                onEvent = {},
                onBack = {},
                onNavigateToSection = {},
            )
        }
    }
}

@Preview(name = "ConfigMenuScreen — Large Font", widthDp = 412, heightDp = 800, fontScale = 1.5f)
@Composable
private fun ConfigMenuLargeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigMenuContent(
                uiState = previewUiState(),
                onEvent = {},
                onBack = {},
                onNavigateToSection = {},
            )
        }
    }
}

@Preview(name = "ConfigMenuScreen — Huge Font", widthDp = 412, heightDp = 800, fontScale = 2.0f)
@Composable
private fun ConfigMenuHugeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            ConfigMenuContent(
                uiState = previewUiState(),
                onEvent = {},
                onBack = {},
                onNavigateToSection = {},
            )
        }
    }
}
