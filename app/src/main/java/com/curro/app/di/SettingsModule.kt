package com.curro.app.di

import com.curro.app.data.local.SettingsDataStore
import com.curro.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the DataStore-backed [SettingsRepository] (SF-6.1 / US-041).
 *
 * The DataStore singleton itself is provided by the
 * `Context.dataStore` extension property inside [SettingsDataStore] (the
 * AndroidX-recommended idiom for an app-singleton preferences DataStore), so
 * this module only needs to bind the interface to the impl.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("UnnecessaryAbstractClass") // Hilt requires `abstract class` + `@Binds` for interface binding.
abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsDataStore): SettingsRepository
}
