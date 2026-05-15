package com.curro.app.di

import com.curro.app.data.permissions.PermissionGate
import com.curro.app.data.permissions.RecordAudioPermissionGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for runtime-permission gates (SF-2.3 / US-017).
 *
 * Phase 2 ships [PermissionGate] bound to [RecordAudioPermissionGate]. Future
 * permissions (CALL_PHONE, READ_CONTACTS, BIND_NOTIFICATION_LISTENER_SERVICE, …) will be
 * surfaced through their own dedicated qualifier-annotated bindings — when more than one
 * permission is gated, this single binding becomes ambiguous and Hilt will surface the
 * conflict; that's the trigger to introduce a qualifier per permission.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface PermissionsModule {
    @Binds
    @Singleton
    fun bindRecordAudioGate(impl: RecordAudioPermissionGate): PermissionGate
}
