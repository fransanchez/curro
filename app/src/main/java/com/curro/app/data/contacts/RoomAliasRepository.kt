package com.curro.app.data.contacts

import com.curro.app.assistant.TimeProvider
import com.curro.app.data.apps.curroNormalize
import com.curro.app.data.local.AliasSource
import com.curro.app.data.local.ContactAliasDao
import com.curro.app.data.local.ContactAliasEntity
import com.curro.app.domain.model.Contact
import com.curro.app.domain.repository.AliasRecord
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.AliasSnapshot
import com.curro.app.domain.repository.AliasView
import com.curro.app.domain.repository.ContactsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [AliasRepository] (SF-7.2 / US-046).
 *
 * Replaces SF-4.9's [EmptyAliasRepository]. The contract is identical for the
 * one Phase-4 caller (`CallContactHandler.handle`); new callers are
 * SF-7.3 (`learn`, `findStoredAlias`), SF-7.2 itself (`topUsedSnapshots`),
 * and SF-8.2 (`observeAll`, `deleteAll`).
 *
 * **The stale-`LOOKUP_KEY` path** (`local-data` rule 1): if the DAO has a row
 * but [ContactsProvider.findByLookupKey] returns `null`, this method returns
 * `emptyList()` — the caller is responsible for distinguishing "I never had
 * an alias for this" from "I had one and it broke" via [findStoredAlias].
 * The handler in SF-7.3 wires the distinction.
 *
 * **The bump on resolution** is the same row that drives the prompt-context
 * top-N ordering: every successful lookup nudges this alias higher in
 * FunctionGemma's view on the next turn.
 *
 * **Threading**: Room suspend DAOs auto-dispatch to the configured query/transaction
 * executor — no redundant `withContext` wrapper needed or wanted here.
 */
@Singleton
class RoomAliasRepository
    @Inject
    constructor(
        private val dao: ContactAliasDao,
        private val contactsProvider: ContactsProvider,
        private val timeProvider: TimeProvider,
    ) : AliasRepository {
        @Suppress("ReturnCount")
        override suspend fun resolveAlias(alias: String): List<Contact> {
            val normalised = alias.trim().lowercase().curroNormalize()
            if (normalised.isEmpty()) return emptyList()
            val entry = dao.findByAlias(normalised) ?: return emptyList()
            val contact = contactsProvider.findByLookupKey(entry.lookupKey) ?: return emptyList()
            dao.bumpUsage(normalised, timeProvider.now())
            return listOf(contact)
        }

        override suspend fun learn(
            alias: String,
            contact: Contact,
            source: AliasSource,
        ) {
            val normalised = alias.trim().lowercase().curroNormalize()
            if (normalised.isEmpty()) return
            val now = timeProvider.now()
            dao.upsert(
                ContactAliasEntity(
                    alias = normalised,
                    lookupKey = contact.lookupKey,
                    displayName = contact.displayName,
                    source = source,
                    createdAtMs = now,
                    lastUsedAtMs = now,
                    useCount = 0,
                ),
            )
        }

        override fun observeAll(): Flow<List<AliasView>> =
            dao.observeAll().map { list ->
                list.map { e ->
                    AliasView(
                        alias = e.alias,
                        displayName = e.displayName,
                        source = e.source,
                        useCount = e.useCount,
                    )
                }
            }

        override suspend fun topUsedSnapshots(limit: Int): List<AliasSnapshot> =
            dao.topUsed(limit).map { AliasSnapshot(alias = it.alias, displayName = it.displayName) }

        override suspend fun deleteAll() = dao.deleteAll()

        @Suppress("ReturnCount")
        override suspend fun findStoredAlias(alias: String): AliasRecord? {
            val normalised = alias.trim().lowercase().curroNormalize()
            if (normalised.isEmpty()) return null
            val entry = dao.findByAlias(normalised) ?: return null
            return AliasRecord(displayName = entry.displayName, source = entry.source)
        }
    }
