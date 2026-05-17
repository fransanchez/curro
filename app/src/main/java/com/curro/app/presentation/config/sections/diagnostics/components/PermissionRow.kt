package com.curro.app.presentation.config.sections.diagnostics.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.data.permissions.PermissionInfo
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Single row in the "Permisos" section of [DiagnosticsScreen] (US-059 / SF-8.10).
 *
 * Shows a permission label and a coloured status icon:
 * - Granted: [Icons.Filled.Check] in [MaterialTheme.colorScheme.primary] + "Concedido".
 * - Denied: [Icons.Filled.Close] in [MaterialTheme.colorScheme.error] + "Denegado".
 *
 * @param info The permission to display.
 */
@Composable
fun PermissionRow(
    info: PermissionInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = CurroSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(info.labelResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(CurroSpacing.s))
        if (info.isGranted) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.copy_config_diagnostics_perm_granted),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(CurroSpacing.m),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.copy_config_diagnostics_perm_denied),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(CurroSpacing.m),
            )
        }
    }
}

@Preview(name = "PermissionRow — granted — light")
@Preview(name = "PermissionRow — granted — dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PermissionRowGrantedPreview() {
    CurroTheme {
        Surface {
            PermissionRow(
                info =
                    PermissionInfo(
                        permission = "RECORD_AUDIO",
                        labelResId = R.string.copy_config_diagnostics_permission_record_audio,
                        isGranted = true,
                    ),
            )
        }
    }
}

@Preview(name = "PermissionRow — denied — light")
@Preview(name = "PermissionRow — denied — dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PermissionRowDeniedPreview() {
    CurroTheme {
        Surface {
            PermissionRow(
                info =
                    PermissionInfo(
                        permission = "NOTIFICATION_LISTENER",
                        labelResId = R.string.copy_config_diagnostics_permission_notification_listener,
                        isGranted = false,
                    ),
            )
        }
    }
}
