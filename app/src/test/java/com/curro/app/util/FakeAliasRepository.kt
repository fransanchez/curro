package com.curro.app.util

import com.curro.app.data.local.AliasSource
import com.curro.app.domain.model.Contact
import com.curro.app.domain.repository.AliasRecord
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.AliasSnapshot
import com.curro.app.domain.repository.AliasView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared test fake for [AliasRepository] (SF-7.2 / US-046).
 *
 * Designed to be configurable from each test:
 * - [resolveAliasResult] — maps a normalised alias string to the contacts it should return.
 * - [topUsedSnapshotsResult] — canned list returned by [topUsedSnapshots]; set the limit
 *   capture via [topUsedSnapshotsLastLimit] for the "limit is capped at 10" coordinator tests.
 * - [findStoredAliasResult] — maps a normalised alias to the stored record (or null).
 * - [learnCalls] — every [learn] invocation appended here; SF-7.3 asserts that the
 *   disambiguation path never appends to this list.
 * - [observeAllStream] — backing [MutableStateFlow]; emit into it to drive [observeAll].
 *
 * **Normalisation note**: the production [com.curro.app.data.contacts.RoomAliasRepository]
 * normalises the alias before the DAO lookup. This fake does NOT normalise — test callers
 * must pre-normalise the keys to match.
 */
open class FakeAliasRepository : AliasRepository {
    val resolveAliasResult: MutableMap<String, List<Contact>> = mutableMapOf()
    var topUsedSnapshotsResult: List<AliasSnapshot> = emptyList()
    var topUsedSnapshotsLastLimit: Int? = null
    val findStoredAliasResult: MutableMap<String, AliasRecord?> = mutableMapOf()
    val learnCalls: MutableList<LearnInvocation> = mutableListOf()
    val observeAllStream: MutableStateFlow<List<AliasView>> = MutableStateFlow(emptyList())

    override suspend fun resolveAlias(alias: String): List<Contact> = resolveAliasResult[alias] ?: emptyList()

    override suspend fun learn(
        alias: String,
        contact: Contact,
        source: AliasSource,
    ) {
        learnCalls += LearnInvocation(alias = alias, contact = contact, source = source)
    }

    override fun observeAll(): Flow<List<AliasView>> = observeAllStream

    override suspend fun topUsedSnapshots(limit: Int): List<AliasSnapshot> {
        topUsedSnapshotsLastLimit = limit
        return topUsedSnapshotsResult
    }

    override suspend fun deleteAll() {
        learnCalls.clear()
        resolveAliasResult.clear()
        findStoredAliasResult.clear()
    }

    override suspend fun findStoredAlias(alias: String): AliasRecord? = findStoredAliasResult[alias]

    /** Record of a single [learn] invocation — inspected by SF-7.3 disambiguation tests. */
    data class LearnInvocation(
        val alias: String,
        val contact: Contact,
        val source: AliasSource,
    )
}
