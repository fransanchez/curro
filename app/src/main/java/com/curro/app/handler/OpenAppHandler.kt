package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.apps.AppLauncher
import com.curro.app.data.apps.ColloquialAppAliases
import com.curro.app.data.apps.curroNormalize
import com.curro.app.data.apps.levenshtein
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.LaunchableApp
import com.curro.app.domain.repository.InstalledAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves a colloquial Spanish app name to an installed package and launches it (US-027 / SF-4.3).
 *
 * Resolution order:
 *  1. **Alias hit** — [ColloquialAppAliases.byColloquialName] keyed on the normalised query;
 *     the first installed package in the ordered candidate list wins (not ambiguous).
 *  2. **Substring `contains`** — the normalised query is contained in the normalised label.
 *     Single hit → launch. Multiple hits → [CurroError.AmbiguousApp] (not narrowed further).
 *  3. **Levenshtein ≤ [LEV_THRESHOLD]** — only when query length ≥ [LEV_MIN_QUERY_LEN].
 *     Single minimum-distance candidate → launch. Tied minimum → [CurroError.AmbiguousApp].
 *  4. No match → [CurroError.AppNotFound].
 *
 * `needs_confirmation: NO` — launching an app is reversible (user can press HOME), so no
 * confirmation dialog is shown.
 */
class OpenAppHandler
    @Inject
    constructor(
        private val installedApps: InstalledAppsRepository,
        private val launcher: AppLauncher,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "open_app"

        @Suppress("ReturnCount")
        override suspend fun handle(call: FunctionCall): HandlerResult {
            val rawInput = (call.params["app_name"] as? String).orEmpty().trim()
            if (rawInput.isEmpty()) {
                return HandlerResult.Failed(
                    speech = context.getString(R.string.copy_app_not_found),
                    reason = CurroError.AppNotFound(""),
                )
            }
            val query = rawInput.curroNormalize()
            val installed = installedApps.observeAllLaunchable().first()

            // ── 1. Alias hit ──────────────────────────────────────────────────
            aliasFirstInstalled(query, installed)?.let { app ->
                return launchOrFail(app, rawInput)
            }

            // ── 2. Substring contains ─────────────────────────────────────────
            val containsHits = installed.filter { query in it.label.curroNormalize() }
            when (containsHits.size) {
                0 -> Unit
                1 -> return launchOrFail(containsHits.first(), rawInput)
                else -> return ambiguousResult(containsHits)
            }

            // ── 3. Levenshtein ────────────────────────────────────────────────
            if (query.length >= LEV_MIN_QUERY_LEN) {
                val fuzzy =
                    installed
                        .map { it to levenshtein(query, it.label.curroNormalize()) }
                        .filter { (_, d) -> d <= LEV_THRESHOLD }
                        .sortedBy { (_, d) -> d }
                if (fuzzy.isNotEmpty()) {
                    val topDistance = fuzzy.first().second
                    val tied = fuzzy.filter { (_, d) -> d == topDistance }.map { (app, _) -> app }
                    return if (tied.size == 1) launchOrFail(tied.first(), rawInput) else ambiguousResult(tied)
                }
            }

            // ── 4. No match ───────────────────────────────────────────────────
            return HandlerResult.Failed(
                speech = context.getString(R.string.copy_app_not_found_named, rawInput),
                reason = CurroError.AppNotFound(rawInput),
            )
        }

        /**
         * Finds the first installed package from the alias map's candidate list for [normalisedQuery].
         * Returns `null` if the query is not in the alias map, or if no candidate is installed.
         */
        @Suppress("ReturnCount")
        private fun aliasFirstInstalled(
            normalisedQuery: String,
            installed: List<LaunchableApp>,
        ): LaunchableApp? {
            val candidates = ColloquialAppAliases.byColloquialName[normalisedQuery] ?: return null
            val installedByPackage = installed.associateBy { it.packageName }
            for (pkg in candidates) {
                installedByPackage[pkg]?.let { return it }
            }
            return null
        }

        private fun launchOrFail(
            app: LaunchableApp,
            rawInput: String,
        ): HandlerResult {
            val ok = launcher.launch(app.packageName)
            return if (ok) {
                HandlerResult.Spoken(context.getString(R.string.copy_app_opening, app.label))
            } else {
                HandlerResult.Failed(
                    speech = context.getString(R.string.copy_app_not_found_named, rawInput),
                    reason = CurroError.AppNotFound(rawInput),
                )
            }
        }

        private fun ambiguousResult(candidates: List<LaunchableApp>): HandlerResult =
            HandlerResult.Failed(
                speech = context.getString(R.string.copy_app_ambiguous),
                reason = CurroError.AmbiguousApp(candidates),
            )

        private companion object {
            const val LEV_THRESHOLD = 3
            const val LEV_MIN_QUERY_LEN = 4
        }
    }
