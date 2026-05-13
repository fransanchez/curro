package com.curro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.curro.app.presentation.navigation.CurroNavHost
import com.curro.app.presentation.theme.CurroTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Launcher Activity for Curro.
 *
 * - [enableEdgeToEdge] paints under the system bars; [CurroNavHost]'s
 *   Scaffold consumes the insets via its `innerPadding` (No-Double-Padding
 *   rule, `navigation-patterns` rule 1).
 * - [@AndroidEntryPoint] enables Hilt-injected ViewModels in any screen
 *   the nav graph hosts (US-002 wired the graph; the launcher placeholder
 *   has no ViewModel yet — SF-1.1+ adds them).
 *
 * SF-1.1 adds `CATEGORY_HOME` to the manifest intent-filter (making Curro
 * the default launcher) plus a `RoleManager.ROLE_HOME` flow + the
 * "Hazme tu pantalla de inicio" CTA. Until then, Curro appears only in
 * the app drawer (`MAIN + LAUNCHER` filter).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CurroTheme {
                CurroNavHost()
            }
        }
    }
}
