package com.curro.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.curro.app.assistant.AssistantCoordinator
import com.curro.app.data.recovery.RecoveryStateRepository
import com.curro.app.presentation.navigation.CurroNavHost
import com.curro.app.presentation.recovery.RecoveryScreen
import com.curro.app.presentation.theme.CurroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Launcher Activity for Curro.
 *
 * - [enableEdgeToEdge] paints under the system bars; [CurroNavHost]'s
 *   Scaffold consumes the insets via its `innerPadding` (No-Double-Padding
 *   rule, `navigation-patterns` rule 1).
 * - [@AndroidEntryPoint] enables Hilt-injected collaborators (the assistant
 *   coordinator here, plus ViewModels in any screen the nav graph hosts).
 * - `launchMode="singleTask"` (manifest, US-009) means pressing HOME from
 *   any app brings this Activity back via [onNewIntent], not a new instance.
 *
 * SF-5.6 (US-040): [onNewIntent] resets the assistant FSM to `Idle` on a
 * HOME-launch intent. Any in-flight TTS / STT / model decode is cancelled
 * — `coordinator.onHomePressed()` does the same cancel-everything dance as
 * `onMicPressed`. See `docs/architecture/interrupt-by-button.md`.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var coordinator: AssistantCoordinator

    // Injected to check the crash-loop flag BEFORE setContent is called.
    // Must be read synchronously — RecoveryStateRepository uses SharedPreferences
    // (commit(), not apply()) so this is safe from the main thread.
    @Inject lateinit var recovery: RecoveryStateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Check the crash-loop flag BEFORE instantiating the normal Hilt graph via
        // CurroNavHost. If the normal graph is what's crashing, we must never touch
        // it here — RecoveryScreen only instantiates RecoveryViewModel.
        if (recovery.isRecoveryPending()) {
            setContent {
                CurroTheme {
                    RecoveryScreen()
                }
            }
        } else {
            setContent {
                CurroTheme {
                    CurroNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME-press from any app routes through here (because launchMode="singleTask").
        // Detect the HOME category and reset the FSM. Other intent kinds (deep links,
        // share targets — none exist in Curro today) fall through unchanged so a
        // future SF can hang its own handling off the same hook without surprising
        // this code.
        if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
            coordinator.onHomePressed()
        }
    }
}
