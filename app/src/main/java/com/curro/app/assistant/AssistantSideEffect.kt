package com.curro.app.assistant

/**
 * One-shot side effects emitted by [AssistantCoordinator] that need UI cooperation
 * (an `ActivityResultLauncher`, a debug surface, etc.). Adapted by
 * `LauncherViewModel` into [com.curro.app.presentation.launcher.LauncherSideEffect]s
 * the launcher composable already knows how to consume.
 *
 * The coordinator is `@Singleton`; the composable is per-Activity. Side effects
 * flow `coordinator → VM → composable` via a `SharedFlow` (coordinator side) +
 * `Channel` (VM → composable side) — the coordinator never references an
 * Activity directly.
 */
sealed interface AssistantSideEffect {
    /**
     * Coordinator asks the UI to request a runtime permission. The VM adapts
     * this to the matching `LauncherSideEffect.Request*` and the composable's
     * `ActivityResultLauncher` fires it.
     *
     * @param permission Android manifest constant (e.g.
     *   `Manifest.permission.READ_CONTACTS`).
     */
    data class RequestPermission(val permission: String) : AssistantSideEffect

    /**
     * Debug-only: surface the parsed `FunctionCall` JSON to the listening
     * overlay (US-024). Coordinator emits this in `BuildConfig.DEBUG`. The
     * VM adapts to `LauncherSideEffect.ShowDebugJson`.
     */
    data class ShowDebugJson(val prettyJson: String) : AssistantSideEffect
}
