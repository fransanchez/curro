package com.curro.app.di

import com.curro.app.data.notification.UnreadMessageCache
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.data.permissions.SystemNotificationAccessGate
import com.curro.app.domain.repository.NotificationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the notification-listener infrastructure (US-030 / SF-4.6).
 *
 * - [NotificationRepository] → [UnreadMessageCache] (Phase-4 in-memory; Phase-7 replaces with Room).
 * - [NotificationAccessGate] → [SystemNotificationAccessGate].
 */
@Module
@InstallIn(SingletonComponent::class)
interface NotificationModule {
    @Binds
    @Singleton
    fun bindNotificationRepository(impl: UnreadMessageCache): NotificationRepository

    @Binds
    @Singleton
    fun bindNotificationAccessGate(impl: SystemNotificationAccessGate): NotificationAccessGate
}
