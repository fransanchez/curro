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
                        null,
                        null,
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
                out
            }

        private companion object {
            // Projection column indices (must match the projection array order above).
            const val IDX_KEY = 0
            const val IDX_NAME = 1
            const val IDX_PHONE = 2
            const val IDX_PHOTO = 3
        }
    }
