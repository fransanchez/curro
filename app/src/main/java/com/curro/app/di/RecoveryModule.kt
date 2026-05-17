package com.curro.app.di

import com.curro.app.data.recovery.RecoveryStateRepository
import com.curro.app.data.recovery.SharedPreferencesRecoveryState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for the crash-loop detection repository.
 *
 * [SharedPreferencesRecoveryState] is `@Singleton` so the same instance is shared
 * between [CurroApp] (which installs the handler in `onCreate`) and [MainActivity]
 * (which reads [RecoveryStateRepository.isRecoveryPending] before `setContent`).
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("UnnecessaryAbstractClass") // Hilt requires `abstract class` + `@Binds` for interface binding.
abstract class RecoveryModule {
    @Binds
    @Singleton
    abstract fun bindRecovery(impl: SharedPreferencesRecoveryState): RecoveryStateRepository
}
