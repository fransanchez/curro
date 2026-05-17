package com.curro.app.presentation.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.curro.app.data.launcher.MakeMeDefaultLauncher
import com.curro.app.presentation.config.ConfigMenuScreen
import com.curro.app.presentation.config.aliases.AliasesScreen
import com.curro.app.presentation.config.favourites.FavouritesScreen
import com.curro.app.presentation.config.sections.ConfigSectionPlaceholder
import com.curro.app.presentation.config.tts.TtsSettingsScreen
import com.curro.app.presentation.launcher.LauncherPlaceholderScreen
import com.curro.app.presentation.launcher.MoreAppsScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Curro's single navigation host.
 *
 * One [Scaffold] whose [innerPadding] wraps the [NavHost]. Child screens NEVER add their
 * own [Scaffold] / `TopAppBar` / `statusBarsPadding()` — that doubles the top inset
 * (the **No-Double-Padding rule**; `navigation-patterns` rule 1; CLAUDE.md "Screen Layout").
 * Back navigation in a child screen = a large overlay chevron at TopStart in a [Box],
 * not a [TopAppBar].
 *
 * Two routes, that's it (`navigation-patterns` rule 2):
 * - [CurroRoute.Launcher] — start destination. The launcher home (a placeholder in Phase 0;
 *   the real clock + mic button + app grid + "Más apps" lands across SF-1.1 → SF-1.5).
 * - [CurroRoute.ConfigMenu] — the hidden Fran-only menu (a placeholder in Phase 0; SF-8.x
 *   fills it). In Phase 0/1 the placeholder ships a debug [TextButton] that opens it; in
 *   Phase 1 the canonical entry is the 5-taps-on-clock gesture (SF-1.6).
 *
 * The assistant's listening / processing / confirming / message-cards / picker UI are
 * **state-driven overlays**, NOT new nav routes (`navigation-patterns` rule 3; `voice-interaction`).
 *
 * The [rememberLauncherForActivityResult] registration for the `RoleManager.ROLE_HOME` flow
 * lives here (in the `Launcher` composable block) rather than inside
 * [LauncherPlaceholderScreen] — keeping the screen composable free of Android-platform
 * side-effects and `ActivityResult` plumbing (SF-1.1 / US-009 decision).
 *
 * @param modifier Applied to the [Scaffold]. [MainActivity] does not pass anything extra.
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
                val context = LocalContext.current

                // Resolve MakeMeDefaultLauncher via Hilt entry-point: this composable block
                // is not a @HiltViewModel call-site, so hiltViewModel() doesn't apply here.
                // EntryPointAccessors is the canonical Hilt-friendly pattern for resolving
                // @Inject-constructable objects outside a ViewModel or @AndroidEntryPoint scope.
                val makeMeDefault = rememberMakeMeDefaultLauncher()

                // Register the ActivityResultLauncher for the role-request Intent.
                // The result is intentionally ignored — DefaultLauncherDetector's flow
                // re-emits on ProcessLifecycleOwner ON_RESUME, which fires when the
                // role-chooser Activity returns to Curro regardless of the result.
                val roleRequestLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult(),
                    ) { /* result ignored — detector flow re-emits on ON_RESUME */ }

                LauncherPlaceholderScreen(
                    onOpenConfig = { navController.navigate(CurroRoute.ConfigMenu.value) },
                    onMakeDefault = {
                        val roleIntent = makeMeDefault.requestRoleIntent()
                        if (roleIntent != null) {
                            roleRequestLauncher.launch(roleIntent)
                        } else {
                            // Fallback: role unavailable / already held / "Don't ask again".
                            // Opens Settings → Default apps → Home app.
                            context.startActivity(makeMeDefault.openHomeSettings())
                        }
                    },
                    // SF-1.5: navigate to the "Más apps" full-list screen.
                    onNavigateToMoreApps = { navController.navigate(CurroRoute.MoreApps.value) },
                )
            }
            // SF-8.1 (US-050) — real config menu replaces the Phase-0 placeholder.
            composable(CurroRoute.ConfigMenu.value) {
                ConfigMenuScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToSection = { route -> navController.navigate(route) },
                )
            }
            // SF-8.1 (US-050) — 7 placeholder routes; each replaced inline by SF-8.2 → SF-8.10.
            // SF-8.2 (US-051) — real alias management screen.
            composable("config/aliases") {
                AliasesScreen(onBack = { navController.popBackStack() })
            }
            // SF-8.3 (US-052) — real favourites editor.
            composable("config/favourites") {
                FavouritesScreen(onBack = { navController.popBackStack() })
            }
            // SF-8.4 (US-053) — real TTS voice + speed settings screen.
            composable("config/tts") {
                TtsSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("config/thresholds") {
                ConfigSectionPlaceholder(onBack = { navController.popBackStack() })
            }
            composable("config/failures") {
                ConfigSectionPlaceholder(onBack = { navController.popBackStack() })
            }
            composable("config/reset") {
                ConfigSectionPlaceholder(onBack = { navController.popBackStack() })
            }
            composable("config/diagnostics") {
                ConfigSectionPlaceholder(onBack = { navController.popBackStack() })
            }
            // SF-1.5 (US-013) — full list of all installed launchable apps.
            composable(CurroRoute.MoreApps.value) {
                MoreAppsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Composition-local-style resolver for [MakeMeDefaultLauncher] via Hilt's entry-point API.
 *
 * [MakeMeDefaultLauncher] is `@Inject`-constructable and `@Singleton`; the entry-point
 * resolves the singleton instance without threading a parameter through [CurroNavHost]'s
 * call-site in `MainActivity.setContent { }`. [remember] keyed on [context] ensures the
 * instance is stable across recompositions.
 */
@Composable
private fun rememberMakeMeDefaultLauncher(): MakeMeDefaultLauncher {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, LauncherEntryPoint::class.java)
            .makeMeDefaultLauncher()
    }
}

/**
 * Hilt entry-point for resolving [MakeMeDefaultLauncher] in composable blocks that are
 * not [androidx.hilt.navigation.compose.hiltViewModel] call-sites (i.e. in the
 * `composable { }` block of [CurroNavHost], not inside a screen composable).
 *
 * Co-located with [CurroNavHost] for Phase-1-scale simplicity; can be moved to a
 * dedicated `HiltEntryPoints.kt` in `presentation/navigation/` if more entry-points accumulate.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface LauncherEntryPoint {
    fun makeMeDefaultLauncher(): MakeMeDefaultLauncher
}

/**
 * Curro's nav route registry. Two routes, by design (`navigation-patterns` rule 2).
 *
 * The string value is the on-the-wire route key used by
 * [androidx.navigation.compose.composable] and [androidx.navigation.NavController.navigate];
 * an [enum class] is the simplest shape that exposes both an exhaustive-when surface and a
 * stable string value.
 */
enum class CurroRoute(val value: String) {
    Launcher("launcher"),
    ConfigMenu("config"),

    /** SF-1.5 (US-013) — full scrollable list of all installed launchable apps. */
    MoreApps("more_apps"),
}
