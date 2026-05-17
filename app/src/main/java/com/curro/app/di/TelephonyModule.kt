package com.curro.app.di

import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.SystemCallPhonePermissionGate
import com.curro.app.data.telephony.CallController
import com.curro.app.data.telephony.IncomingCallModeController
import com.curro.app.data.telephony.IntentCallController
import com.curro.app.data.telephony.PackageManagerIncomingCallModeController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the telephony infrastructure (US-034 / SF-4.10 and
 * SF-8.7 / US-056 — the incoming-call assistant mode).
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

    /**
     * SF-8.7 (US-056) — single write-path for the toggle. The interface seam
     * lets the toggle-handler tests inject a fake that records the
     * enable/disable transitions without touching the real PackageManager.
     */
    @Binds
    @Singleton
    fun bindIncomingCallModeController(impl: PackageManagerIncomingCallModeController): IncomingCallModeController
}
