package com.curro.app.domain.repository

import com.curro.app.domain.model.LaunchableApp
import kotlinx.coroutines.flow.Flow

/**
 * Repository that provides the full list of installed launchable apps (SF-1.5 / US-013).
 *
 * Used by the "Más apps" screen to present a scrollable alphabetical list of every
 * app installed on the device that responds to [android.content.Intent.ACTION_MAIN] +
 * [android.content.Intent.CATEGORY_LAUNCHER].
 *
 * Re-emits on `ProcessLifecycleOwner ON_RESUME` so the list reflects installs / removals
 * while the user is away.
 */
interface InstalledAppsRepository {
    /**
     * A [Flow] of all launchable apps, sorted alphabetically by [LaunchableApp.label]
     * using a Spanish-locale [java.text.Collator]. Emits immediately on subscription and
     * on every `ON_RESUME` thereafter.
     */
    fun observeAllLaunchable(): Flow<List<LaunchableApp>>
}
