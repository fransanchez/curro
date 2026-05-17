package com.curro.app.domain.repository

import com.curro.app.data.local.AliasSource
import com.curro.app.domain.model.Contact
import kotlinx.coroutines.flow.Flow

/**
 * Resolves a spoken alias (e.g. "mi hija") to the [Contact] it maps to, and
 * supports the alias-learning + Phase-8-config-menu operations (US-046 / SF-7.2,
 * spec §7 alias model, `local-data` rule 1).
 *
 * **Phase 4 implementation** ([com.curro.app.data.contacts.EmptyAliasRepository], removed in SF-7.2):
 * returned [emptyList] for `resolveAlias`. The `CallContactHandler` from SF-4.10 fell
 * through to [ContactsProvider.findByName].
 *
 * **Phase 7 implementation** ([com.curro.app.data.contacts.RoomAliasRepository]): backed by
 * [com.curro.app.data.local.ContactAliasDao]. The `LOOKUP_KEY` is re-resolved
 * to a current [Contact] at every call — if the stored lookup-key no longer
 * resolves (contact deleted), [resolveAlias] returns [emptyList] and SF-7.3's
 * handler detects this via [findStoredAlias] to trigger the re-learn flow.
 */
interface AliasRepository {
    /**
     * Return the [Contact] this [alias] maps to, or [emptyList] when no row
     * exists OR the stored `LOOKUP_KEY` no longer resolves. Bumps `useCount`
     * and `lastUsedAtMs` on a successful resolution.
     *
     * **Normalisation**: implementations apply `alias.trim().lowercase().curroNormalize()`
     * before the DAO lookup.
     */
    suspend fun resolveAlias(alias: String): List<Contact>

    /**
     * Persist a new alias mapping (SF-7.3). Used by the alias-learning subflow
     * (`AliasSource.LEARNED`) and the Phase-8 config menu (`AliasSource.EXPLICIT`).
     *
     * Same normalisation as [resolveAlias]. The unique index on `contact_aliases.alias`
     * + `OnConflictStrategy.REPLACE` means re-teaching an existing alias overwrites
     * the row (SF-7.3's re-learn flow exploits this).
     */
    suspend fun learn(
        alias: String,
        contact: Contact,
        source: AliasSource,
    )

    /**
     * Phase-8 config-menu read API. Emits in `useCount DESC, lastUsedAtMs DESC`
     * order — most-used first.
     */
    fun observeAll(): Flow<List<AliasView>>

    /**
     * Prompt-context injection (SF-7.2 caller: [com.curro.app.assistant.AssistantCoordinator.buildContext]).
     * Returns the top-[limit] aliases as `AliasSnapshot(alias, displayName)` —
     * exposed-shape limited so the coordinator never sees `LOOKUP_KEY` (keeps
     * the PromptContext clean).
     *
     * Default limit `10` is the prompt-budget cap (see SF-7.2 brief
     * "On-device-model Impact").
     */
    suspend fun topUsedSnapshots(limit: Int = 10): List<AliasSnapshot>

    /**
     * Phase-8 "reset learning" affordance. Clears all rows.
     */
    suspend fun deleteAll()

    /**
     * SF-8.2 (US-051) — delete a single alias by its (normalised) text.
     * Used by the alias-management UI edit and delete flows. A no-op if the
     * alias does not exist.
     */
    suspend fun delete(alias: String)

    /**
     * Returns the stored record for [alias] without re-resolving the
     * `LOOKUP_KEY`. Used by SF-7.3 to detect "row exists but contact is gone"
     * so the handler can speak `copy_alias_unresolved` and offer to re-learn.
     *
     * Returns `null` when no row exists.
     */
    suspend fun findStoredAlias(alias: String): AliasRecord?
}

/** UI-shaped projection for the Phase-8 config menu (SF-8.2). */
data class AliasView(
    val alias: String,
    val displayName: String,
    val source: AliasSource,
    val useCount: Int,
)

/** Prompt-context snapshot — exposed only as much as FunctionGemma needs. */
data class AliasSnapshot(
    val alias: String,
    val displayName: String,
)

/** Stale-alias detection record (SF-7.3). */
data class AliasRecord(
    val displayName: String,
    val source: AliasSource,
)
