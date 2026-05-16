package com.curro.app.data.contacts

import com.curro.app.domain.model.Contact
import com.curro.app.domain.repository.AliasRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-4 stub [AliasRepository] (US-033 / SF-4.9).
 *
 * Always returns `emptyList()` — alias learning (spec §7 + flow 4) is Phase 7.
 * The `call_contact` handler (SF-4.10) falls through to [com.curro.app.domain.repository.ContactsProvider]
 * when this stub returns empty.
 *
 * **Phase-7 migration path**: replace the `@Binds AliasRepository` line in
 * `ContactsModule` to point at `RoomAliasRepository`. No other code changes needed.
 */
@Singleton
class EmptyAliasRepository
    @Inject
    constructor() : AliasRepository {
        override suspend fun resolveAlias(alias: String): List<Contact> = emptyList()
    }
