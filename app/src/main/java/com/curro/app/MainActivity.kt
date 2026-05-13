package com.curro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.curro.app.presentation.theme.CurroTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Launcher Activity for Curro.
 *
 * SF-0.1 placeholder — renders the app name so the skeleton boots and exits cleanly.
 * - [enableEdgeToEdge] is called so SF-0.4 starts from the correct insets baseline.
 * - [@AndroidEntryPoint] is harmless with zero @Inject fields and makes SF-0.2 a
 *   zero-friction continuation.
 *
 * SF-0.4 replaces the [Text] stub with the real launcher home surface.
 * SF-0.6 upgrades this Activity with singleTask, portrait lock, and the nav shell.
 * SF-1.1 adds CATEGORY_HOME to the manifest intent-filter (making Curro the default launcher).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CurroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(text = stringResource(R.string.app_name))
                }
            }
        }
    }
}
