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
 * Shared test fake for [AliasRepository] (SF-7.2 / US-046, extended in SF-7.3 / US-047).
 *
 * Designed to be configurable from each test:
 * - [resolveAliasResult] — maps a normalised alias string to the contacts it should return.
 * - [findStoredAliasResult] — maps a normalised alias to the stored record (or null).
 * - [topUsedSnapshotsResult] — canned list returned by [topUsedSnapshots].
 * - [topUsedSnapshotsLastLimit] — captures the most recent limit argument.
 * - [learnCalls] — every [learn] invocation appended here; SF-7.3 asserts that the
 *   disambiguation path never appends to this list.
 * - [observeAllStream] — backing [MutableStateFlow]; emit into it to drive [observeAll].
 * - [deleteAllInvoked] — true after [deleteAll] is called.
 *
 * **Normalisation**: keys in [resolveAliasResult] and [findStoredAliasResult] should be
 * pre-normalised by the test (lowercase, trimmed) to match the production impl's contract.
 */
open class FakeAliasRepository : AliasRepository {
    val resolveAliasResult: MutableMap<String, List<Contact>> = mutableMapOf()
    val findStoredAliasResult: MutableMap<String, AliasRecord?> = mutableMapOf()
    var topUsedSnapshotsResult: List<AliasSnapshot> = emptyList()
    var topUsedSnapshotsLastLimit: Int? = null
    val learnCalls: MutableList<LearnInvocation> = mutableListOf()
    val observeAllStream: MutableStateFlow<List<AliasView>> = MutableStateFlow(emptyList())
    var deleteAllInvoked: Boolean = false

    override suspend fun resolveAlias(alias: String): List<Contact> =
        resolveAliasResult[alias.trim().lowercase()] ?: emptyList()

    override suspend fun learn(
        alias: String,
        contact: Contact,
        source: AliasSource,
    ) {
        learnCalls +=
            LearnInvocation(
                alias = alias.trim().lowercase(),
                contactLookupKey = contact.lookupKey,
                source = source,
            )
    }

    override fun observeAll(): Flow<List<AliasView>> = observeAllStream

    override suspend fun topUsedSnapshots(limit: Int): List<AliasSnapshot> {
        topUsedSnapshotsLastLimit = limit
        return topUsedSnapshotsResult.take(limit)
    }

    override suspend fun deleteAll() {
        deleteAllInvoked = true
        learnCalls.clear()
        resolveAliasResult.clear()
        findStoredAliasResult.clear()
    }

    override suspend fun findStoredAlias(alias: String): AliasRecord? = findStoredAliasResult[alias.trim().lowercase()]

    /** Record of a single [learn] invocation — inspected by SF-7.3 disambiguation tests. */
    data class LearnInvocation(
        val alias: String,
        val contactLookupKey: String,
        val source: AliasSource,
    )
}
