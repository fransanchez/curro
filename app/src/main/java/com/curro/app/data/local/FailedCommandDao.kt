package com.curro.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Read/write API for [FailedCommandEntity] (SF-7.1; SF-7.5 + SF-8.x callers).
 *
 * **The cap-at-50 invariant (`local-data` rule 4)**: [insertAndTrim] is the
 * only public write path. It inserts the row and then deletes anything
 * outside the 50 newest in the same transaction; the table size never
 * exceeds 50.
 *
 * Phase-8's failed-commands UI subscribes to [observeRecent].
 *
 * Privacy: see [FailedCommandEntity] Kdoc — the transcript stays here, never
 * on the telemetry wire.
 */
@Dao
abstract class FailedCommandDao {
    @Query("SELECT * FROM failed_commands ORDER BY timestampMs DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int = 50): Flow<List<FailedCommandEntity>>

    @Query("SELECT COUNT(*) FROM failed_commands")
    abstract suspend fun count(): Int

    @Insert
    abstract suspend fun insert(entity: FailedCommandEntity): Long

    @Query(
        """
        DELETE FROM failed_commands
        WHERE id NOT IN (
            SELECT id FROM failed_commands
            ORDER BY timestampMs DESC
            LIMIT 50
        )
        """,
    )
    abstract suspend fun trimToFifty()

    /** Atomic insert + trim. See class Kdoc; do not bypass. */
    @Transaction
    open suspend fun insertAndTrim(entity: FailedCommandEntity) {
        insert(entity)
        trimToFifty()
    }

    @Query("DELETE FROM failed_commands")
    abstract suspend fun deleteAll()
}
