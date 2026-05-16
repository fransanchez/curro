package com.curro.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Read/write API for [ContactAliasEntity] (SF-7.1; SF-7.2 + SF-7.3 + SF-8.2 callers).
 *
 * Ordering: [observeAll] and [topUsed] order by `useCount DESC, lastUsedAtMs DESC`
 * so the prompt-context injection (SF-7.2) picks the most-used recently-used
 * aliases first; ties broken by recency.
 *
 * Uniqueness: the entity's unique index on `alias` means [upsert] with the
 * `OnConflictStrategy.REPLACE` strategy overwrites a stale row when SF-7.3's
 * re-learn flow re-teaches "mi hija → <new contact>". The old `id`,
 * `createdAtMs`, `useCount`, `lastUsedAtMs` are reset to the new entity's
 * fields — pin: `useCount` resets to 0 on a re-learn (intentional —
 * statistics shouldn't survive a "Curro was wrong about who that is").
 */
@Dao
interface ContactAliasDao {
    @Query("SELECT * FROM contact_aliases ORDER BY useCount DESC, lastUsedAtMs DESC")
    fun observeAll(): Flow<List<ContactAliasEntity>>

    @Query("SELECT * FROM contact_aliases ORDER BY useCount DESC, lastUsedAtMs DESC LIMIT :limit")
    suspend fun topUsed(limit: Int): List<ContactAliasEntity>

    @Query("SELECT * FROM contact_aliases WHERE alias = :alias LIMIT 1")
    suspend fun findByAlias(alias: String): ContactAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContactAliasEntity)

    @Query("UPDATE contact_aliases SET lastUsedAtMs = :now, useCount = useCount + 1 WHERE alias = :alias")
    suspend fun bumpUsage(
        alias: String,
        now: Long,
    )

    @Query("DELETE FROM contact_aliases WHERE alias = :alias")
    suspend fun delete(alias: String)

    @Query("DELETE FROM contact_aliases")
    suspend fun deleteAll()
}
