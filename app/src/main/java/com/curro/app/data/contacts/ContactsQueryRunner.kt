package com.curro.app.data.contacts

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.curro.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Thin testable wrapper around the `ContentResolver` phone-contacts query (US-033 / SF-4.9).
 *
 * The interface is the seam: production code uses [ContentResolverContactsQueryRunner] which
 * runs the real `ContentResolver` query on [IoDispatcher]; unit tests inject a
 * `FakeContactsQueryRunner` that returns pre-built [Row] lists — no `Cursor` or
 * `ContentProvider` is touched in tests.
 *
 * Projected columns: `LOOKUP_KEY`, `DISPLAY_NAME_PRIMARY`, `NUMBER`, `PHOTO_THUMBNAIL_URI`.
 */
interface ContactsQueryRunner {
    /** Returns one [Row] per phone row in `Phone.CONTENT_URI`. Empty if permission is denied. */
    suspend fun query(): List<Row>

    /**
     * Returns all [Row]s whose `LOOKUP_KEY` equals [lookupKey] (SF-7.2).
     * A contact with two phone numbers returns two rows — the caller groups them.
     * Returns empty when the key is not found or permission is denied.
     */
    suspend fun queryByLookupKey(lookupKey: String): List<Row>

    /**
     * Returns every row in `Phone.CONTENT_URI` with no filter (SF-7.3).
     * Used by the alias-learning subflow to collect the full contact list for
     * the candidate picker. One real contact can appear multiple times
     * (once per phone number) — the caller groups by [Row.lookupKey].
     * Returns empty if permission is denied.
     */
    suspend fun queryAll(): List<Row>

    /**
     * SF-8.7 (US-056) — reverse lookup via `PhoneLookup.CONTENT_FILTER_URI`.
     * Returns 0 or 1 [Row]s (one phone number resolves to at most one
     * contact). Returns empty when [number] is blank, no match, or
     * `READ_CONTACTS` is denied (`SecurityException` caught).
     *
     * Implementations should query the `PhoneLookup` table — its projection
     * differs from `Phone.CONTENT_URI` (uses `Contacts.LOOKUP_KEY` and
     * `Contacts.DISPLAY_NAME_PRIMARY`, with the phone NUMBER already part of
     * the URI), so the impl translates the row into the same [Row] shape.
     */
    suspend fun queryByNumber(number: String): List<Row>

    /**
     * One row from `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`.
     * A single real contact can appear multiple times (once per phone number).
     * [lookupKey] is the stable de-duplicate key.
     */
    data class Row(
        val lookupKey: String,
        val displayName: String,
        val phoneNumber: String?,
        val photoUri: String?,
    )
}

/**
 * Production [ContactsQueryRunner] that queries the real `ContentResolver`.
 * Runs on [ioDispatcher] (never on Main). `SecurityException` (READ_CONTACTS denied)
 * is swallowed — the gate check in SF-4.10 surfaces the denial separately.
 */
class ContentResolverContactsQueryRunner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ContactsQueryRunner {
        override suspend fun query(): List<ContactsQueryRunner.Row> =
            withContext(ioDispatcher) {
                runQuery(selection = null, selectionArgs = null)
            }

        override suspend fun queryByLookupKey(lookupKey: String): List<ContactsQueryRunner.Row> =
            withContext(ioDispatcher) {
                runQuery(
                    selection = "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} = ?",
                    selectionArgs = arrayOf(lookupKey),
                )
            }

        override suspend fun queryAll(): List<ContactsQueryRunner.Row> =
            withContext(ioDispatcher) {
                runQuery(selection = null, selectionArgs = null)
            }

        override suspend fun queryByNumber(number: String): List<ContactsQueryRunner.Row> =
            withContext(ioDispatcher) {
                runPhoneLookup(number)
            }

        /**
         * SF-8.7 (US-056) — reverse-lookup via [android.provider.ContactsContract.PhoneLookup].
         *
         * Uses the indexed `CONTENT_FILTER_URI` (constant-time on typical contact
         * tables). Returns 0 or 1 rows — one phone number resolves to at most one
         * contact via Android's own normalisation. The projection differs from
         * `Phone.CONTENT_URI`:
         *   - `LOOKUP_KEY` and `DISPLAY_NAME` come from the joined `Contacts` table.
         *   - The phone NUMBER is *not* returned (it's part of the URI), so we
         *     reuse the input [number] in the produced [Row].
         */
        @Suppress("NestedBlockDepth")
        private fun runPhoneLookup(number: String): List<ContactsQueryRunner.Row> {
            val trimmed = number.trim()
            if (trimmed.isEmpty()) return emptyList()
            val resolver: ContentResolver = context.contentResolver
            val uri =
                android.net.Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(trimmed),
                )
            val projection =
                arrayOf(
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                )
            val out = mutableListOf<ContactsQueryRunner.Row>()
            try {
                resolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val keyIdx = cursor.getColumnIndexOrThrow(projection[IDX_LOOKUP_KEY])
                    val nameIdx = cursor.getColumnIndexOrThrow(projection[IDX_DISPLAY_NAME])
                    val photoIdx = cursor.getColumnIndexOrThrow(projection[IDX_PHOTO_URI])
                    while (cursor.moveToNext()) {
                        val key = cursor.getString(keyIdx)
                        val name = cursor.getString(nameIdx)
                        if (key != null && name != null) {
                            out +=
                                ContactsQueryRunner.Row(
                                    lookupKey = key,
                                    displayName = name,
                                    // PhoneLookup does not return NUMBER — use the input.
                                    phoneNumber = trimmed,
                                    photoUri = cursor.getString(photoIdx),
                                )
                        }
                    }
                }
            } catch (_: SecurityException) {
                // READ_CONTACTS not granted — return emptyList(); CurroInCallService
                // falls through to native ring.
            }
            return out
        }

        /**
         * Shared cursor-walking logic for both query styles. [SecurityException] is
         * swallowed — the gate check at the handler layer is the primary denial surface.
         */
        @Suppress("NestedBlockDepth")
        private fun runQuery(
            selection: String?,
            selectionArgs: Array<String>?,
        ): List<ContactsQueryRunner.Row> {
            val resolver: ContentResolver = context.contentResolver
            val projection =
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                )
            val out = mutableListOf<ContactsQueryRunner.Row>()
            try {
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    val keyIdx = cursor.getColumnIndexOrThrow(projection[IDX_KEY])
                    val nameIdx = cursor.getColumnIndexOrThrow(projection[IDX_NAME])
                    val phoneIdx = cursor.getColumnIndexOrThrow(projection[IDX_PHONE])
                    val photoIdx = cursor.getColumnIndexOrThrow(projection[IDX_PHOTO])
                    while (cursor.moveToNext()) {
                        val key = cursor.getString(keyIdx)
                        val name = cursor.getString(nameIdx)
                        // Rows with null lookupKey or displayName are unusable — skip them.
                        if (key != null && name != null) {
                            out +=
                                ContactsQueryRunner.Row(
                                    lookupKey = key,
                                    displayName = name,
                                    phoneNumber = cursor.getString(phoneIdx),
                                    photoUri = cursor.getString(photoIdx),
                                )
                        }
                    }
                }
            } catch (_: SecurityException) {
                // READ_CONTACTS not granted — returns emptyList().
                // The ReadContactsPermissionGate in SF-4.10 surfaces the denial.
            }
            return out
        }

        private companion object {
            // Projection column indices (must match the projection array order above).
            const val IDX_KEY = 0
            const val IDX_NAME = 1
            const val IDX_PHONE = 2
            const val IDX_PHOTO = 3

            // PhoneLookup projection indices — different table, different columns.
            const val IDX_LOOKUP_KEY = 0
            const val IDX_DISPLAY_NAME = 1
            const val IDX_PHOTO_URI = 2
        }
    }
