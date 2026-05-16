package com.curro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The single Room database for Curro (SF-7.1 / US-045).
 *
 * - **v1 entities**: [ContactAliasEntity] (SF-7.2 alias map), [AppUsageEntity]
 *   (SF-7.4 implicit favourites), [FailedCommandEntity] (SF-7.5 fail log).
 * - **Schema export** enabled — every future schema change generates a new JSON
 *   in `app/schemas/com.curro.app.data.local.CurroDatabase/<version>.json` for
 *   migration safety. The schema directory is committed.
 *
 * **Migration policy (prototype only)**: [androidx.room.Room.databaseBuilder] is
 * configured in [com.curro.app.di.DatabaseModule] with
 * `.fallbackToDestructiveMigration()`. This is intentional for the prototype —
 * no users yet, no real data to lose. **Before any public release**, replace
 * the fallback with real [androidx.room.migration.Migration] objects (the
 * schema-export JSON is the prerequisite for that work).
 *
 * **`InteractionLogEntity` deferred to Phase 8** — no caller writes it yet
 * (it's a Phase-4-proactive hook); deferring keeps the schema slim.
 *
 * **Threading**: every DAO method is `suspend` or `Flow`-returning. Callers
 * dispatch on `@IoDispatcher` — never the main thread. The in-memory Room
 * builder in tests uses `.allowMainThreadQueries()` for simplicity.
 */
@Database(
    entities = [
        ContactAliasEntity::class,
        AppUsageEntity::class,
        FailedCommandEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CurroTypeConverters::class)
abstract class CurroDatabase : RoomDatabase() {
    abstract fun contactAliasDao(): ContactAliasDao

    abstract fun appUsageDao(): AppUsageDao

    abstract fun failedCommandDao(): FailedCommandDao
}
