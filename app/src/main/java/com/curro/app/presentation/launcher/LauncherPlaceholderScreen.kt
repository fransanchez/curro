package com.curro.app.presentation.launcher

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.curro.app.R
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Phase-0 placeholder for the launcher home. Replaced piecewise across
 * SF-1.1 → SF-1.5 by the real surface — clock + date (SF-1.2), the
 * ≥ 40 % mic button (SF-1.3), the favourite-apps grid (SF-1.4), and the
 * "Más apps" entry point (SF-1.5). SF-1.6 wires the 5-taps-on-clock
 * gesture as the canonical config-menu entry; until then this screen's
 * debug [TextButton] is the only way to reach [CurroRoute.ConfigMenu].
 *
 * **This screen will be deleted at SF-1.1** — it has no carry-over.
 * The `R.string.launcher_placeholder_*` resources go with it.
 *
 * Senior-first contract: the title respects [MaterialTheme.typography.displayMedium]
 * (US-005's senior-first scale — 36 sp SemiBold) so even the placeholder
 * reads at the senior size if Fran's father sees it on an early
 * checkpoint build. The debug button uses `TextButton` deliberately
 * (not `BigPrimaryButton`) — it's NOT a CTA the user should see; the
 * subdued styling and the "(depuración)" label make its dev-only nature
 * explicit.
 *
 * No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()` —
 * [CurroNavHost]'s `Scaffold` already pads (No-Double-Padding rule).
 */
@Composable
fun LauncherPlaceholderScreen(
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.launcher_placeholder_title),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(CurroSpacing.xxl))
            // Phase-0 debug affordance — the canonical "5 taps on clock" gesture lands with SF-1.6.
            // This TextButton is removed at SF-1.1 along with the rest of the placeholder.
            TextButton(onClick = onOpenConfig) {
                Text(
                    text = stringResource(R.string.launcher_placeholder_open_config_debug),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Preview(name = "LauncherPlaceholder — Light", widthDp = 412, heightDp = 800)
@Composable
private fun LauncherPlaceholderLightPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderScreen(onOpenConfig = {})
        }
    }
}

@Preview(
    name = "LauncherPlaceholder — Dark",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun LauncherPlaceholderDarkPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderScreen(onOpenConfig = {})
        }
    }
}

@Preview(name = "LauncherPlaceholder — Large Font", widthDp = 412, heightDp = 800, fontScale = 1.5f)
@Composable
private fun LauncherPlaceholderLargeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderScreen(onOpenConfig = {})
        }
    }
}

@Preview(name = "LauncherPlaceholder — Huge Font (senior-first)", widthDp = 412, heightDp = 800, fontScale = 2.0f)
@Composable
private fun LauncherPlaceholderHugeFontPreview() {
    CurroTheme {
        Surface(Modifier.fillMaxSize()) {
            LauncherPlaceholderScreen(onOpenConfig = {})
        }
    }
}
