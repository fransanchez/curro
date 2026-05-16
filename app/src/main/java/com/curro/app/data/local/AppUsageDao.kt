package com.curro.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Read/write API for [AppUsageEntity] (SF-7.1; SF-7.4 caller).
 *
 * **The upsert invariant (load-bearing)**: a naive `INSERT OR REPLACE` would
 * reset `openCount` to 1 on every call — wrong. Instead, [upsert] is a
 * `@Transaction` that:
 *  1. Calls [bumpExisting] (an `UPDATE` that increments `openCount` and sets
 *     `lastOpenedAtMs`); the return value is the number of rows affected.
 *  2. If `bumpExisting` returned 0 (no row existed), [insertIfMissing] runs
 *     with `IGNORE` strategy (defensive — concurrent inserts can't double-write).
 *
 * The transaction makes the bump-or-insert atomic; two concurrent
 * [upsert]s for the same package never lose an open count.
 */
@Dao
abstract class AppUsageDao {
    @Query("SELECT * FROM app_usage ORDER BY openCount DESC LIMIT :limit")
    abstract suspend fun topByOpenCount(limit: Int = 20): List<AppUsageEntity>

    @Query("SELECT * FROM app_usage ORDER BY openCount DESC LIMIT :limit")
    abstract fun observeTopByOpenCount(limit: Int = 20): Flow<List<AppUsageEntity>>

    @Query(
        """
        UPDATE app_usage
        SET openCount = openCount + 1, lastOpenedAtMs = :now
        WHERE packageName = :packageName
        """,
    )
    abstract suspend fun bumpExisting(
        packageName: String,
        now: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(entity: AppUsageEntity)

    /**
     * Atomic bump-or-insert. See class Kdoc for the invariant; do not bypass.
     */
    @Transaction
    open suspend fun upsert(
        packageName: String,
        now: Long,
    ) {
        if (bumpExisting(packageName, now) == 0) {
            insertIfMissing(AppUsageEntity(packageName = packageName, openCount = 1, lastOpenedAtMs = now))
        }
    }

    @Query("DELETE FROM app_usage")
    abstract suspend fun deleteAll()
}
