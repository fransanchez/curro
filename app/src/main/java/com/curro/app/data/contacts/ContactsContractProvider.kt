package com.curro.app.data.contacts

import com.curro.app.data.apps.curroNormalize
import com.curro.app.domain.model.Contact
import com.curro.app.domain.repository.ContactsProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [ContactsProvider] backed by [ContactsQueryRunner] (US-033 / SF-4.9).
 *
 * Matching strategy:
 *   - Single-token query: word-boundary regex (`\b<query>\b`) so "ana" does NOT match "Susana".
 *   - Multi-token query (contains a space): simple `contains` — word-boundary over a phrase
 *     is fragile and the multi-word case is usually a full name.
 *
 * Grouping: rows are grouped by `LOOKUP_KEY` so a contact with two phone numbers
 * appears as one [Contact] with both phones, deduped. Null phone rows are kept
 * (phone list is empty) — the caller (SF-4.10) decides "not callable".
 *
 * Thread safety: [ContactsQueryRunner.query] runs on IoDispatcher internally. This
 * wrapper is `@Singleton` but its state is immutable after construction.
 */
@Singleton
class ContactsContractProvider
    @Inject
    constructor(
        private val runner: ContactsQueryRunner,
    ) : ContactsProvider {
        @Suppress("ReturnCount")
        override suspend fun findByLookupKey(lookupKey: String): Contact? {
            if (lookupKey.isBlank()) return null
            val rows = runner.queryByLookupKey(lookupKey)
            if (rows.isEmpty()) return null
            val first = rows.first()
            return Contact(
                lookupKey = first.lookupKey,
                displayName = first.displayName,
                phoneNumbers =
                    rows
                        .mapNotNull { it.phoneNumber?.trim()?.takeIf { p -> p.isNotEmpty() } }
                        .distinct(),
                photoUri = rows.firstOrNull { it.photoUri != null }?.photoUri,
            )
        }

        override suspend fun findAll(): List<Contact> {
            val rows = runner.queryAll()
            if (rows.isEmpty()) return emptyList()
            return rows
                .groupBy { it.lookupKey }
                .map { (key, rowsForKey) ->
                    Contact(
                        lookupKey = key,
                        displayName = rowsForKey.first().displayName,
                        phoneNumbers =
                            rowsForKey
                                .mapNotNull { it.phoneNumber?.trim()?.takeIf { p -> p.isNotEmpty() } }
                                .distinct(),
                        photoUri = rowsForKey.firstOrNull { it.photoUri != null }?.photoUri,
                    )
                }
                .sortedBy { it.displayName.curroNormalize() }
        }

        @Suppress("ReturnCount")
        override suspend fun findByNumber(number: String): Contact? {
            val trimmed = number.trim()
            if (trimmed.isEmpty()) return null
            val rows = runner.queryByNumber(trimmed)
            val first = rows.firstOrNull() ?: return null
            return Contact(
                lookupKey = first.lookupKey,
                displayName = first.displayName,
                phoneNumbers =
                    rows
                        .mapNotNull { it.phoneNumber?.trim()?.takeIf { p -> p.isNotEmpty() } }
                        .distinct(),
                photoUri = first.photoUri,
            )
        }

        @Suppress("ReturnCount")
        override suspend fun findByName(query: String): List<Contact> {
            val q = query.trim()
            if (q.isEmpty()) return emptyList()
            val normalisedQuery = q.curroNormalize()

            val rows = runner.query()
            if (rows.isEmpty()) return emptyList()

            val isMultiToken = normalisedQuery.contains(' ')
            val matches =
                rows.filter { row ->
                    val name = row.displayName.curroNormalize()
                    if (isMultiToken) {
                        normalisedQuery in name
                    } else {
                        // Word-boundary mode: "ana" must not match "Susana".
                        // Regex.escape handles special chars in the query (apostrophes, hyphens).
                        val wb = Regex("\\b" + Regex.escape(normalisedQuery) + "\\b")
                        wb.containsMatchIn(name)
                    }
                }

            // Group by LOOKUP_KEY → one Contact per real person, with all phones deduplicated.
            return matches
                .groupBy { it.lookupKey }
                .map { (key, rowsForKey) ->
                    Contact(
                        lookupKey = key,
                        displayName = rowsForKey.first().displayName,
                        phoneNumbers =
                            rowsForKey
                                .mapNotNull { it.phoneNumber?.trim()?.takeIf { p -> p.isNotEmpty() } }
                                .distinct(),
                        photoUri = rowsForKey.firstOrNull { it.photoUri != null }?.photoUri,
                    )
                }
        }
    }
