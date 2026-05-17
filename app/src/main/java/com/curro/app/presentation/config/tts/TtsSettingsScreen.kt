package com.curro.app.presentation.config.tts

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.curro.app.R
import com.curro.app.domain.repository.SpanishVoice
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * TTS voice and speed settings screen (SF-8.4 / US-053).
 *
 * Lets Fran adjust speech rate, pitch, and choose from the available on-device Spanish voices.
 * Every change is persisted immediately via [TtsSettingsViewModel] — no staged "Save" button.
 *
 * No [androidx.compose.material3.Scaffold] / TopAppBar — the parent [CurroNavHost] Scaffold
 * provides [Modifier.padding(innerPadding)] (No-Double-Padding rule). Back chevron at TopStart
 * in a [Box] overlay.
 */
@Composable
fun TtsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TtsSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TtsSettingsContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@Suppress("LongMethod")
internal fun TtsSettingsContent(
    uiState: TtsSettingsUiState,
    onEvent: (TtsSettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = Dimens.MinTapTarget)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.copy_tts_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s),
            )

            Spacer(modifier = Modifier.height(CurroSpacing.m))

            // --- Rate slider ---
            val rateLabel = stringResource(R.string.copy_tts_rate_label)
            Text(
                text = rateLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )
            Slider(
                value = uiState.rate,
                onValueChange = { onEvent(TtsSettingsEvent.RateChanged(it)) },
                valueRange = TtsSettingsUiState.RATE_MIN..TtsSettingsUiState.RATE_MAX,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = CONFIG_ROW_MIN_HEIGHT)
                        .padding(horizontal = CurroSpacing.m)
                        .semantics { contentDescription = rateLabel },
            )
            Text(
                text = stringResource(R.string.copy_tts_rate_value, uiState.rate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = CurroSpacing.s))

            // --- Pitch slider ---
            val pitchLabel = stringResource(R.string.copy_tts_pitch_label)
            Text(
                text = pitchLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )
            Slider(
                value = uiState.pitch,
                onValueChange = { onEvent(TtsSettingsEvent.PitchChanged(it)) },
                valueRange = TtsSettingsUiState.PITCH_MIN..TtsSettingsUiState.PITCH_MAX,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = CONFIG_ROW_MIN_HEIGHT)
                        .padding(horizontal = CurroSpacing.m)
                        .semantics { contentDescription = pitchLabel },
            )
            Text(
                text = stringResource(R.string.copy_tts_pitch_value, uiState.pitch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = CurroSpacing.s))

            // --- Voice picker ---
            if (uiState.availableVoices.isNotEmpty()) {
                VoicePicker(
                    voices = uiState.availableVoices,
                    selectedVoiceName = uiState.selectedVoiceName,
                    onVoiceSelected = { name -> onEvent(TtsSettingsEvent.VoiceSelected(name)) },
                )
            } else {
                Text(
                    text = stringResource(R.string.copy_tts_no_voices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(CurroSpacing.m),
                )
            }

            Spacer(modifier = Modifier.height(CurroSpacing.xl))
        }

        // Back chevron — TopStart overlay (No-Double-Padding rule; same pattern as AliasesScreen).
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

@Composable
private fun VoicePicker(
    voices: List<SpanishVoice>,
    selectedVoiceName: String?,
    onVoiceSelected: (String?) -> Unit,
) {
    Text(
        text = stringResource(R.string.copy_tts_voice_label),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = CurroSpacing.m),
    )
    Spacer(modifier = Modifier.height(CurroSpacing.xs))
    voices.forEach { voice ->
        val isSelected = voice.name == selectedVoiceName || (selectedVoiceName == null && voice.isDefault)
        FilterChip(
            selected = isSelected,
            onClick = {
                val newName = if (isSelected) null else voice.name
                onVoiceSelected(newName)
            },
            label = {
                Text(
                    text = voice.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            modifier =
                Modifier
                    .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.xs)
                    .heightIn(min = CONFIG_ROW_MIN_HEIGHT),
        )
    }
}

// Config menu rows use 72 dp minimum height (Fran-screen density, not the 96 dp senior floor).
@Suppress("MagicNumber")
private val CONFIG_ROW_MIN_HEIGHT = 72.dp

@Preview(name = "TTS settings — light", showBackground = true)
@Preview(name = "TTS settings — dark", uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun TtsSettingsContentPreview() {
    CurroTheme {
        Surface {
            TtsSettingsContent(
                uiState =
                    TtsSettingsUiState(
                        selectedVoiceName = "es-es-x-eem-local",
                        rate = TtsSettingsUiState.DEFAULT_RATE,
                        pitch = TtsSettingsUiState.DEFAULT_PITCH,
                        availableVoices =
                            listOf(
                                SpanishVoice(
                                    name = "es-es-x-eef-local",
                                    displayName = "Español (ES) · femenino",
                                    isDefault = false,
                                ),
                                SpanishVoice(
                                    name = "es-es-x-eem-local",
                                    displayName = "Español (ES) · masculino",
                                    isDefault = true,
                                ),
                            ),
                    ),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "TTS settings — no voices")
@Composable
private fun TtsSettingsNoVoicesPreview() {
    CurroTheme {
        Surface {
            TtsSettingsContent(
                uiState = TtsSettingsUiState(availableVoices = emptyList()),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
