package com.curro.app.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.curro.app.presentation.config.ConfigMenuPlaceholderScreen
import com.curro.app.presentation.launcher.LauncherPlaceholderScreen

/**
 * Curro's single navigation host.
 *
 * One [Scaffold] whose [innerPadding] wraps the [NavHost]. Child screens
 * NEVER add their own [Scaffold] / `TopAppBar` / `statusBarsPadding()` —
 * that doubles the top inset (the **No-Double-Padding rule**;
 * `navigation-patterns` rule 1; `CLAUDE.md` "Screen Layout"). Back
 * navigation in a child screen = a large overlay chevron at TopStart in
 * a [Box], not a [TopAppBar].
 *
 * Two routes, that's it (`navigation-patterns` rule 2):
 * - [CurroRoute.Launcher] — start destination. The launcher home (a
 *   placeholder in Phase 0; the real clock + mic button + app grid +
 *   "Más apps" lands across SF-1.1 → SF-1.5).
 * - [CurroRoute.ConfigMenu] — the hidden Fran-only menu (a placeholder
 *   in Phase 0; SF-8.x fills it). Opened from the launcher; in Phase 0
 *   the placeholder ships a debug `TextButton` that opens it directly,
 *   in Phase 1 the canonical entry is the 5-taps-on-clock gesture
 *   (SF-1.6).
 *
 * The assistant's listening / processing / confirming / message-cards /
 * picker UI are **state-driven overlays**, NOT new nav routes
 * (`navigation-patterns` rule 3; `voice-interaction`). They render on
 * top of the launcher route, selected by a `StateFlow<AssistantState>`
 * owned by `assistant/AssistantStateMachine` (Phase 5). Adding them as
 * routes would force them through navigation transitions; they're
 * UI-state changes, not navigation.
 *
 * No deep links, no bottom nav, no tabs, no [NavigationRail] / adaptive
 * nav scaffolds (`navigation-patterns` rule 4; `adaptive-layout` — Curro
 * is one fixed phone, portrait). Opening other apps from the launcher
 * is a `PackageManager` intent (SF-1.4), not in-app navigation.
 *
 * @param modifier Applied to the [Scaffold]. Callers typically pass
 *   [Modifier]; [MainActivity] does not pass anything extra.
 */
@Composable
fun CurroNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = CurroRoute.Launcher.value,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(CurroRoute.Launcher.value) {
                LauncherPlaceholderScreen(
                    onOpenConfig = { navController.navigate(CurroRoute.ConfigMenu.value) },
                )
            }
            composable(CurroRoute.ConfigMenu.value) {
                ConfigMenuPlaceholderScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Curro's nav route registry. Two routes, by design
 * (`navigation-patterns` rule 2).
 *
 * The string value is the on-the-wire route key used by
 * [androidx.navigation.compose.composable] and [NavController.navigate];
 * an [enum class] is the simplest shape that exposes both an
 * exhaustive-when surface and a stable string value.
 */
enum class CurroRoute(val value: String) {
    Launcher("launcher"),
    ConfigMenu("config"),
}
