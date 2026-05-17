package com.curro.app.util

import com.curro.app.domain.model.Contact
import com.curro.app.domain.repository.ContactsProvider

/**
 * Shared test fake for [ContactsProvider] (SF-7.3 / US-047).
 *
 * Configurable knobs:
 * - [findByNameResult] — maps a normalised query string to the contacts returned by [findByName].
 * - [findAllResult] — the full contact list returned by [findAll] (used by the learning subflow).
 * - [lookupKeyResult] — maps a lookupKey to the contact returned by [findByLookupKey].
 *
 * **Normalisation note**: the production [com.curro.app.data.contacts.ContactsContractProvider]
 * normalises the query before matching. This fake does NOT normalise — test callers must
 * pre-normalise the keys to match.
 */
class FakeContactsProvider : ContactsProvider {
    val findByNameResult: MutableMap<String, List<Contact>> = mutableMapOf()
    var findAllResult: List<Contact> = emptyList()
    val lookupKeyResult: MutableMap<String, Contact?> = mutableMapOf()

    /**
     * SF-8.7 (US-056) — maps a phone number string (as the test passes it) to
     * the [Contact] [findByNumber] should return. Absent keys return `null` —
     * the unknown-number path.
     */
    val findByNumberResult: MutableMap<String, Contact?> = mutableMapOf()

    override suspend fun findByName(query: String): List<Contact> = findByNameResult[query] ?: emptyList()

    override suspend fun findAll(): List<Contact> = findAllResult

    override suspend fun findByLookupKey(lookupKey: String): Contact? = lookupKeyResult[lookupKey]

    override suspend fun findByNumber(number: String): Contact? = findByNumberResult[number]
}
