package com.curro.app.presentation.config.sections.diagnostics.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.presentation.common.BigPrimaryButton
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Body of the "HyperOS" section in [DiagnosticsScreen] (US-059 / SF-8.10).
 *
 * Shows:
 * 1. A [BigPrimaryButton] — "Permitir en segundo plano" — which opens Curro's
 *    app-details Settings page (battery → sin restricciones).
 * 2. The multiline autostart instructions ([R.string.copy_config_diagnostics_autostart_help]).
 *
 * @param onBatteryClick Invoked when the button is pressed; the ViewModel converts this
 *   into a side-effect that launches the settings intent.
 */
@Composable
fun HyperOsSection(
    onBatteryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BigPrimaryButton(
            text = stringResource(R.string.copy_config_diagnostics_battery_cta),
            onClick = onBatteryClick,
        )
        Spacer(modifier = Modifier.height(CurroSpacing.s))
        Text(
            text = stringResource(R.string.copy_config_diagnostics_autostart_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(name = "HyperOsSection — light")
@Preview(name = "HyperOsSection — dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HyperOsSectionPreview() {
    CurroTheme {
        Surface {
            HyperOsSection(onBatteryClick = {})
        }
    }
}
