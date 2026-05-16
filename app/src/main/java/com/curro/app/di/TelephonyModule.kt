package com.curro.app.di

import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.SystemCallPhonePermissionGate
import com.curro.app.data.telephony.CallController
import com.curro.app.data.telephony.IntentCallController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the telephony infrastructure (US-034 / SF-4.10).
 *
 * Phase 5 consideration: if `InCallService` (spec §8) lands, extend this module.
 */
@Module
@InstallIn(SingletonComponent::class)
interface TelephonyModule {
    @Binds
    @Singleton
    fun bindCallController(impl: IntentCallController): CallController

    @Binds
    @Singleton
    fun bindCallPhonePermissionGate(impl: SystemCallPhonePermissionGate): CallPhonePermissionGate
}
