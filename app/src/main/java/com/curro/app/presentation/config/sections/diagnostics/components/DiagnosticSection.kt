package com.curro.app.presentation.config.sections.diagnostics.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * A section card in [DiagnosticsScreen] (US-059 / SF-8.10).
 *
 * Header text at [MaterialTheme.typography.bodyLarge] inside a [Card] followed by the
 * [content] slot. All five diagnostics sections share this layout.
 *
 * @param title String resource for the section header.
 * @param modifier Applied to the outer [Card].
 * @param content Section body — composable slot.
 */
@Composable
fun DiagnosticSection(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s),
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(CurroSpacing.xs))
            content()
        }
    }
}

@Preview(name = "DiagnosticSection — light")
@Preview(name = "DiagnosticSection — dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DiagnosticSectionPreview() {
    CurroTheme {
        Surface {
            DiagnosticSection(title = R.string.copy_config_diagnostics_section_app) {
                Text("Versión: 0.1.0 (1) — debug")
            }
        }
    }
}
