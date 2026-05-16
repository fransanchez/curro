package com.curro.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-package open-count + last-opened-at (SF-7.4 implicit favourites).
 *
 * - [packageName] IS the primary key (no `id` autoGenerate). The
 *   bump-or-insert pattern in [AppUsageDao.upsert] (see Kdoc there) preserves
 *   [openCount] across upserts — a naive `INSERT OR REPLACE` would reset it,
 *   which would break the recency-weighted ranking in SF-7.4.
 * - [lastOpenedAtMs] enables the 30-day linear decay in SF-7.4's scoring:
 *   `score = openCount × max(0, 1 − daysSince/30)`.
 *
 * Re-computation cadence is 24 h (SF-7.4); see `local-data` rule 5 — the
 * home grid must not reshuffle on every open ("feels the same every day").
 */
@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey val packageName: String,
    val openCount: Int = 0,
    val lastOpenedAtMs: Long,
)
