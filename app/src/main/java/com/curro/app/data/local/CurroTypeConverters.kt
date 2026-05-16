package com.curro.app.data.local

import androidx.room.TypeConverter

/**
 * Single converter registry for [AliasSource] and [FailureKind] (SF-7.1).
 *
 * Both enums round-trip via [Enum.name] (the JVM string form) ↔ `valueOf`.
 * **Pin: the wire form is the enum's source-level name** (`LEARNED`,
 * `INVALID_OUTPUT`, etc.). Renaming an enum constant is a schema migration —
 * future-Phase work must include a Room [androidx.room.migration.Migration]
 * step or a `replace_all` rename.
 *
 * The `object` form ensures Room sees these as a single
 * `@TypeConverters` set rather than duplicating per-entity.
 */
object CurroTypeConverters {
    @TypeConverter
    fun fromAliasSource(value: AliasSource): String = value.name

    @TypeConverter
    fun toAliasSource(value: String): AliasSource = AliasSource.valueOf(value)

    @TypeConverter
    fun fromFailureKind(value: FailureKind): String = value.name

    @TypeConverter
    fun toFailureKind(value: String): FailureKind = FailureKind.valueOf(value)
}
