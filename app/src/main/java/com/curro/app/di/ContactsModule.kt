package com.curro.app.di

import com.curro.app.data.contacts.ContactsContractProvider
import com.curro.app.data.contacts.ContactsQueryRunner
import com.curro.app.data.contacts.ContentResolverContactsQueryRunner
import com.curro.app.data.contacts.RoomAliasRepository
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.data.permissions.SystemReadContactsPermissionGate
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.ContactsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the contacts-lookup infrastructure (US-033 / SF-4.9).
 *
 * Phase-7 wired (US-046): aliases are Room-backed via [RoomAliasRepository].
 * EmptyAliasRepository was deleted.
 */
@Module
@InstallIn(SingletonComponent::class)
interface ContactsModule {
    @Binds
    @Singleton
    fun bindContactsQueryRunner(impl: ContentResolverContactsQueryRunner): ContactsQueryRunner

    @Binds
    @Singleton
    fun bindContactsProvider(impl: ContactsContractProvider): ContactsProvider

    @Binds
    @Singleton
    fun bindAliasRepository(impl: RoomAliasRepository): AliasRepository

    @Binds
    @Singleton
    fun bindReadContactsPermissionGate(impl: SystemReadContactsPermissionGate): ReadContactsPermissionGate
}
