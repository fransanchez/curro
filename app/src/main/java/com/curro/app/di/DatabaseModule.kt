package com.curro.app.di

import android.content.Context
import androidx.room.Room
import com.curro.app.data.local.AppUsageDao
import com.curro.app.data.local.ContactAliasDao
import com.curro.app.data.local.CurroDatabase
import com.curro.app.data.local.FailedCommandDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the Room layer (SF-7.1 / US-045).
 *
 * - [CurroDatabase] is `@Singleton` — single connection-pool per process.
 * - The DAOs are NOT `@Singleton` themselves (Room's generated DAOs are stateless;
 *   the singleton lifetime lives on the database).
 *
 * **Migration policy (prototype only)**: `.fallbackToDestructiveMigration()`
 * means a schema-version bump wipes the database on next launch. This is
 * intentional for the prototype — no users yet. **Before any public release**,
 * replace with real [androidx.room.migration.Migration] objects. The schema
 * export at `app/schemas/com.curro.app.data.local.CurroDatabase/<v>.json` is
 * the prerequisite for that work.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideCurroDatabase(
        @ApplicationContext context: Context,
    ): CurroDatabase =
        Room
            .databaseBuilder(context, CurroDatabase::class.java, "curro.db")
            // TODO(post-prototype): replace with real Migration objects before public release.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideContactAliasDao(db: CurroDatabase): ContactAliasDao = db.contactAliasDao()

    @Provides
    fun provideAppUsageDao(db: CurroDatabase): AppUsageDao = db.appUsageDao()

    @Provides
    fun provideFailedCommandDao(db: CurroDatabase): FailedCommandDao = db.failedCommandDao()
}
