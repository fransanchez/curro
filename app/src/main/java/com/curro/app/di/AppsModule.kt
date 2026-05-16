package com.curro.app.di

import com.curro.app.data.apps.AppLauncher
import com.curro.app.data.apps.AppUsageBumper
import com.curro.app.data.apps.CoroutineAppUsageBumper
import com.curro.app.data.apps.InstalledAppsRepositoryImpl
import com.curro.app.data.apps.IntentAppLauncher
import com.curro.app.data.apps.RecencyFavoriteAppsRepositoryImpl
import com.curro.app.domain.repository.FavoriteAppsRepository
import com.curro.app.domain.repository.InstalledAppsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for app-listing, favourite-tile, and app-launch concerns
 * (SF-1.4 / US-012, SF-1.5 / US-013, SF-4.3 / US-027, SF-7.4 / US-048).
 *
 * - [FavoriteAppsRepository] → [RecencyFavoriteAppsRepositoryImpl]: the top-4 tiles by
 *   recency-weighted usage (`openCount × max(0, 1 − daysSince/30)`), falling back to the
 *   Phase-1 seed apps (WhatsApp, Llamadas, Cámara, Fotos) when usage is sparse.
 *   Recomputed at most every 24 h for home-grid stability (master-plan §Phase-7 risk b).
 * - [InstalledAppsRepository] → [InstalledAppsRepositoryImpl]: the full list of installed
 *   launchable apps for the "Más apps" screen.
 * - [AppLauncher] → [IntentAppLauncher]: fires `getLaunchIntentForPackage` + `startActivity`;
 *   bumps [AppUsageBumper] on success (SF-7.4 single bump seam).
 * - [AppUsageBumper] → [CoroutineAppUsageBumper]: fire-and-forget usage write on
 *   [ApplicationScope] so the Room write survives `ViewModel.onCleared`.
 *
 * [com.curro.app.data.apps.SeedAppResolver] is `@Inject`-constructable; no explicit binding.
 */
@Module
@InstallIn(SingletonComponent::class)
interface AppsModule {
    @Binds
    @Singleton
    fun bindFavoriteAppsRepository(impl: RecencyFavoriteAppsRepositoryImpl): FavoriteAppsRepository

    @Binds
    @Singleton
    fun bindInstalledAppsRepository(impl: InstalledAppsRepositoryImpl): InstalledAppsRepository

    @Binds
    @Singleton
    fun bindAppLauncher(impl: IntentAppLauncher): AppLauncher

    @Binds
    @Singleton
    fun bindAppUsageBumper(impl: CoroutineAppUsageBumper): AppUsageBumper
}
