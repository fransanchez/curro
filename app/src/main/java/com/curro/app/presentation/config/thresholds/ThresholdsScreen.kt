package com.curro.app.presentation.config.thresholds

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Confidence-threshold settings screen (SF-8.5 / US-054).
 *
 * Lets Fran adjust `executeThreshold`, `confirmThreshold`, and `alwaysConfirm`.
 * Every change is persisted immediately — no staged "Save" step.
 *
 * No [androidx.compose.material3.Scaffold] / TopAppBar — the parent [CurroNavHost] Scaffold
 * provides [Modifier.padding(innerPadding)] (No-Double-Padding rule). Back chevron at TopStart.
 */
@Composable
fun ThresholdsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThresholdsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ThresholdsContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@Suppress("LongMethod")
internal fun ThresholdsContent(
    uiState: ThresholdsUiState,
    onEvent: (ThresholdsEvent) -> Unit,
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
                text = stringResource(R.string.copy_thresholds_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s),
            )
            Text(
                text = stringResource(R.string.copy_thresholds_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.xs),
            )

            Spacer(modifier = Modifier.height(CurroSpacing.m))

            // --- Execute threshold slider ---
            val executeLabel = stringResource(R.string.copy_thresholds_execute_label)
            Text(
                text = executeLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )
            Text(
                text = stringResource(R.string.copy_thresholds_execute_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )
            Slider(
                value = uiState.executeThreshold,
                onValueChange = { onEvent(ThresholdsEvent.ExecuteThresholdChanged(it)) },
                valueRange = ThresholdsUiState.THRESHOLD_MIN..ThresholdsUiState.THRESHOLD_MAX,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = CONFIG_ROW_MIN_HEIGHT)
                        .padding(horizontal = CurroSpacing.m)
                        .semantics { contentDescription = executeLabel },
            )
            Text(
                text = stringResource(R.string.copy_thresholds_value, uiState.executeThreshold),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = CurroSpacing.s))

            // --- Confirm threshold slider ---
            val confirmLabel = stringResource(R.string.copy_thresholds_confirm_label)
            Text(
                text = confirmLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )
            Text(
                text = stringResource(R.string.copy_thresholds_confirm_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )
            Slider(
                value = uiState.confirmThreshold,
                onValueChange = { onEvent(ThresholdsEvent.ConfirmThresholdChanged(it)) },
                valueRange = ThresholdsUiState.THRESHOLD_MIN..uiState.executeThreshold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = CONFIG_ROW_MIN_HEIGHT)
                        .padding(horizontal = CurroSpacing.m)
                        .semantics { contentDescription = confirmLabel },
            )
            Text(
                text = stringResource(R.string.copy_thresholds_value, uiState.confirmThreshold),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CurroSpacing.m),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = CurroSpacing.s))

            // --- Always confirm toggle ---
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = CONFIG_ROW_MIN_HEIGHT)
                        .padding(horizontal = CurroSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.copy_thresholds_always_confirm_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.copy_thresholds_always_confirm_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val switchCd = stringResource(R.string.copy_thresholds_always_confirm_label)
                Switch(
                    checked = uiState.alwaysConfirm,
                    onCheckedChange = { onEvent(ThresholdsEvent.AlwaysConfirmChanged(it)) },
                    modifier = Modifier.semantics { contentDescription = switchCd },
                )
            }

            Spacer(modifier = Modifier.height(CurroSpacing.xl))
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

// Config menu rows use 72 dp minimum height (Fran-screen density, not the 96 dp senior floor).
@Suppress("MagicNumber")
private val CONFIG_ROW_MIN_HEIGHT = 72.dp

@Preview(name = "Thresholds — light", showBackground = true)
@Preview(name = "Thresholds — dark", uiMode = UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ThresholdsContentPreview() {
    CurroTheme {
        Surface {
            ThresholdsContent(
                uiState =
                    ThresholdsUiState(
                        executeThreshold = ThresholdsUiState.DEFAULT_EXECUTE,
                        confirmThreshold = ThresholdsUiState.DEFAULT_CONFIRM,
                        alwaysConfirm = false,
                    ),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
