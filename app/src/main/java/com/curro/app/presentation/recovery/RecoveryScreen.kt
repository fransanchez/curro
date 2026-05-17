package com.curro.app.presentation.recovery

import android.app.Activity
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.curro.app.R
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme
import com.curro.app.presentation.theme.Dimens

/**
 * Recovery screen shown when Curro has crashed [RecoveryStateRepository.CRASH_THRESHOLD]
 * times within [RecoveryStateRepository.CRASH_WINDOW_MS].
 *
 * This screen is intentionally isolated from the normal Hilt graph:
 * - No [AssistantCoordinator] / [LauncherViewModel] — those may be what's crashing.
 * - Only [RecoveryViewModel] is instantiated, which only touches [RecoveryStateRepository].
 * - The background is [MaterialTheme.colorScheme.errorContainer] as a clear visual cue
 *   that this is a non-normal state.
 *
 * Two actions:
 * 1. **Primary**: open [android.provider.Settings.ACTION_HOME_SETTINGS] → the user can
 *    switch to Samsung's (or another) launcher without ADB.
 * 2. **Secondary**: clear the flag and [Activity.recreate] → [MainActivity.onCreate]
 *    will route to [CurroNavHost] this time (recovery is acknowledged).
 */
@Composable
fun RecoveryScreen(viewModel: RecoveryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    RecoveryContent(
        onOpenSystemSettings = {
            val intent = viewModel.onOpenSystemSettings()
            context.startActivity(intent)
        },
        onRetry = {
            viewModel.onRetry()
            (context as? Activity)?.recreate()
        },
    )
}

@Composable
internal fun RecoveryContent(
    onOpenSystemSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CurroSpacing.xl, vertical = CurroSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.copy_recovery_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(CurroSpacing.l))
            Text(
                text = stringResource(R.string.copy_recovery_explain),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(CurroSpacing.xxl))
            Button(
                onClick = onOpenSystemSettings,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.MinTapTarget),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.copy_recovery_open_settings),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(CurroSpacing.l))
            OutlinedButton(
                onClick = onRetry,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.MinTapTarget),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.copy_recovery_retry),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "RecoveryScreen — Light", widthDp = 412, heightDp = 800)
@Composable
private fun RecoveryLightPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            RecoveryContent(onOpenSystemSettings = {}, onRetry = {})
        }
    }
}

@Preview(name = "RecoveryScreen — Dark", uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 800)
@Composable
private fun RecoveryDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            RecoveryContent(onOpenSystemSettings = {}, onRetry = {})
        }
    }
}

@Preview(name = "RecoveryScreen — Large Font", widthDp = 412, heightDp = 800, fontScale = 1.5f)
@Composable
private fun RecoveryLargeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            RecoveryContent(onOpenSystemSettings = {}, onRetry = {})
        }
    }
}
