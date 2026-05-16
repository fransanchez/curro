package com.curro.app.domain.repository

import com.curro.app.domain.model.Contact

/**
 * Resolves a spoken alias (e.g. "mi hija", "papá") to the [Contact] records it maps to
 * (US-033 / SF-4.9, spec §7 alias model).
 *
 * **Phase 4 implementation**: [com.curro.app.data.contacts.EmptyAliasRepository] — always
 * returns `emptyList()`. The `call_contact` handler (SF-4.10) falls through to
 * [ContactsProvider.findByName] if the alias resolves to nothing.
 *
 * **Phase 7 implementation**: a Room-backed `RoomAliasRepository` that reads the
 * `contact_aliases` table. The interface and every caller (including SF-4.10) stay unchanged.
 * Only the `@Binds AliasRepository` line in `ContactsModule` changes.
 */
interface AliasRepository {
    /**
     * Phase 4: always returns `emptyList()`.
     * Phase 7: returns the [Contact] records the user has trained for [alias].
     */
    suspend fun resolveAlias(alias: String): List<Contact>
}
