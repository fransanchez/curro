package com.curro.app.di

import com.curro.app.data.apps.InstalledAppsRepositoryImpl
import com.curro.app.data.apps.StaticFavoriteAppsRepositoryImpl
import com.curro.app.domain.repository.FavoriteAppsRepository
import com.curro.app.domain.repository.InstalledAppsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for app-listing and favourite-tile concerns (SF-1.4 / US-012, SF-1.5 / US-013).
 *
 * - [FavoriteAppsRepository] → [StaticFavoriteAppsRepositoryImpl]: the four static tiles
 *   (WhatsApp, Llamadas, Cámara, Fotos) resolved from [android.content.pm.PackageManager].
 * - [InstalledAppsRepository] → [InstalledAppsRepositoryImpl]: the full list of installed
 *   launchable apps for the "Más apps" screen.
 *
 * Both are [Singleton] because they cache the PackageManager query result and re-emit
 * only on `ON_RESUME` — creating multiple instances would duplicate the lifecycle observer.
 */
@Module
@InstallIn(SingletonComponent::class)
interface AppsModule {
    @Binds
    @Singleton
    fun bindFavoriteAppsRepository(impl: StaticFavoriteAppsRepositoryImpl): FavoriteAppsRepository

    @Binds
    @Singleton
    fun bindInstalledAppsRepository(impl: InstalledAppsRepositoryImpl): InstalledAppsRepository
}
