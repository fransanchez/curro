package com.curro.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A learned or pre-loaded mapping from a spoken alias ("mi hija") to a
 * specific contact identified by [android.provider.ContactsContract.Contacts.LOOKUP_KEY]
 * (SF-7.2).
 *
 * - [alias]: **normalised** form (`curroNormalize()` applied — lowercase,
 *   accent-stripped, trimmed). The unique index on this column means there is
 *   at most one row per normalised phrase; SF-7.3's re-learn flow exploits
 *   [androidx.room.OnConflictStrategy.REPLACE] in [ContactAliasDao.upsert] to
 *   overwrite a stale row when the user re-teaches the alias.
 * - [lookupKey]: `ContactsContract.Contacts.LOOKUP_KEY` — survives contact
 *   renames, merges, and provider re-indexing (`local-data` rule 1). The
 *   `_ID` is intentionally NOT stored — it can change on a re-import.
 * - [displayName]: cached at learning time so the config menu (SF-8.2) can
 *   render the alias list without a ContactsProvider round-trip per row, and
 *   the SF-7.3 re-learn prompt ("Antes me dijiste que mi hija era %s …") has
 *   the old name without re-resolving.
 * - [source]: [AliasSource] — for the Phase-8 UI to colour-code or filter.
 * - [useCount] + [lastUsedAtMs]: bumped on every successful
 *   [ContactAliasDao.bumpUsage]; drives the prompt-context top-N ordering
 *   (SF-7.2 injects the top-10 by `useCount DESC, lastUsedAtMs DESC`).
 *
 * **Privacy**: aliases stay on the device. Spec §12 — never serialised to
 * telemetry. The Phase-8 config menu reads them; no other surface.
 */
@Entity(
    tableName = "contact_aliases",
    indices = [Index(value = ["alias"], unique = true)],
)
data class ContactAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val lookupKey: String,
    val displayName: String,
    val source: AliasSource,
    val createdAtMs: Long,
    val lastUsedAtMs: Long,
    val useCount: Int = 0,
)
