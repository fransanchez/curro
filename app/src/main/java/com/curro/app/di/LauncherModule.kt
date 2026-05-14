package com.curro.app.di

import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.data.launcher.DefaultLauncherDetectorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for launcher-platform concerns (US-009 / SF-1.1).
 *
 * Single binding: [DefaultLauncherDetector] → [DefaultLauncherDetectorImpl].
 *
 * [com.curro.app.data.launcher.MakeMeDefaultLauncher] is `@Inject`-constructable and
 * needs no explicit binding here; it is resolved in non-ViewModel composables via the
 * [com.curro.app.presentation.navigation.LauncherEntryPoint] entry-point pattern.
 *
 * Subsequent launcher SFs (SF-1.4 app-tile launcher, SF-8.x diagnostics) may add
 * bindings to this module.
 */
@Module
@InstallIn(SingletonComponent::class)
interface LauncherModule {
    @Binds
    @Singleton
    fun bindDefaultLauncherDetector(impl: DefaultLauncherDetectorImpl): DefaultLauncherDetector
}
