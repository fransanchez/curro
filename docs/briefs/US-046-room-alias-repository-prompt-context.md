# Brief — US-046 / SF-7.2: `RoomAliasRepository` real impl + alias injection into FunctionGemma prompt context

## Metadata

| Field | Value |
|---|---|
| **Feature** | Replace `EmptyAliasRepository` (Phase-4 stub) with `RoomAliasRepository` (Phase-7 real impl), add `ContactsProvider.findByLookupKey`, and finally fill the `PromptContext.knownAliases` field that FunctionGemma's prompt-builder has been rendering "ninguno" since Phase 3 |
| **US ID** | US-046 |
| **SF ID** | SF-7.2 |
| **Phase** | 7 — Alias learning & local persistence |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst (Opus) |
| **Implementer** | android-developer (Sonnet) |
| **Size** | M |
| **Depends on** | US-045 (the DAO + entity), US-034 (the `CallContactHandler` alias-first lookup wiring from SF-4.10) |
| **Unblocks** | SF-7.3 (alias learning subflow — depends on the `learn` API + the stale-`LOOKUP_KEY` round-trip), SF-8.2 (Phase 8 alias-management UI — reads `observeAll`) |

---

## Summary

SF-7.1 ships the Room schema; SF-7.2 wires it into the running app at two places that move the user-visible needle:

**Place 1 — `CallContactHandler`'s alias lookup**: line 72 of the handler today reads `val aliasMatches = aliases.resolveAlias(rawQuery)` against `EmptyAliasRepository` which always returns `emptyList()` (per its Kdoc: "Phase-7 migration path: replace the `@Binds AliasRepository` line in `ContactsModule` to point at `RoomAliasRepository`"). SF-7.2 does exactly that. After SF-7.2, a pre-loaded alias `mi hija → Lucía Ruiz` makes "llama a mi hija" resolve to Lucía directly (no picker, no learning — SF-7.3 adds the learning branch when no alias exists).

**Place 2 — FunctionGemma's prompt context**: `PromptContext.knownAliases: List<String>` is a Phase-3-shipped field that `FunctionCallPromptBuilder.contextBlock` already renders as `"- Alias conocidos: <semicolon-separated list> | ninguno"` (line 96–98). The coordinator's `buildContext()` (line 949–954) hard-codes `knownAliases = emptyList()`. SF-7.2 changes that one line to read the top-10 aliases from `RoomAliasRepository.topUsedSnapshots(10)` and format them as `"alias → displayName"` strings. After SF-7.2, FunctionGemma sees the user's learned aliases on every turn — `function-catalog` skill's "Prompt context" requirement finally met.

The third change is the `ContactsContract` integration that closes the `LOOKUP_KEY` round-trip: `ContactsProvider.findByName(query)` (SF-4.9) is matched by a new `ContactsProvider.findByLookupKey(lookupKey)` which `RoomAliasRepository.resolveAlias` calls to convert the stored `LOOKUP_KEY` into a current `Contact`. If the lookup-key no longer resolves (contact deleted), `findByLookupKey` returns null and `resolveAlias` returns `emptyList()` — the caller (SF-7.3's handler) detects this via a separate `findStoredAlias` API and triggers the re-learn flow with `copy_alias_unresolved`.

Spec source: §7 (alias model + "lookup key, not _ID"), `local-data` rules 1+3, `function-catalog` "Prompt context", `platform-integrations` "Resolution order step 1".

---

## Scope

### In scope

- Extend `AliasRepository` interface from one method to five (`resolveAlias`, `learn`, `observeAll`, `topUsedSnapshots`, `deleteAll`) + co-locate `AliasView` + `AliasSnapshot`.
- New `data/contacts/RoomAliasRepository.kt` (real impl backed by `ContactAliasDao`).
- Extend `ContactsProvider` with `findByLookupKey(lookupKey: String): Contact?`.
- Widen `ContactsQueryRunner` to support the new query (or add a sister method).
- Delete `EmptyAliasRepository.kt` + its test.
- Modify `ContactsModule` to bind `RoomAliasRepository`.
- Modify `AssistantCoordinator.buildContext()` to call `aliasRepository.topUsedSnapshots(10)` and format the `knownAliases` list.
- Tests: `RoomAliasRepositoryTest` (~10 cases), `FunctionCallPromptBuilderTest` (+3 cases), `AssistantCoordinatorTest` (+3 cases), `ContactsContractProviderTest` (+3 cases), `CallContactHandlerTest` (+1 case).

### Out of scope

- The alias-learning subflow + `RelationalTerms` + the `copy_alias_unresolved` re-learn copy — SF-7.3.
- The implicit favourites grid — SF-7.4.
- The failed-commands log — SF-7.5.
- The Phase-8 config-menu UI that consumes `observeAll` — Phase 8.
- A "reset learning" config affordance — Phase 8.

---

## User Flows

### Flow 1 — Pre-loaded alias short-circuits the disambig (the user-visible win)

1. Setup: SF-7.2 lands; Fran has not yet shipped Phase 8's UI, BUT the implementer adds one row manually via `adb shell` for smoke testing: `INSERT INTO contact_aliases (alias, lookupKey, displayName, source, createdAtMs, lastUsedAtMs, useCount) VALUES ('mi hija', '<real lk>', 'Lucía Ruiz', 'LEARNED', 0, 0, 0);`.
2. User presses mic, says "llama a mi hija" → `listening` → STT → `processing`.
3. FunctionGemma's prompt now contains `"- Alias conocidos: mi hija → Lucía Ruiz"` (Phase 7's first turn-of-the-key). Model returns `{call_contact, contact: "mi hija", confidence: 0.95}`.
4. `ConfidencePolicy.decide` → `Execute` (high confidence).
5. `CallContactHandler.handle`: `rawQuery = "mi hija"`; `aliasRepo.resolveAlias("mi hija")` → `RoomAliasRepository`:
   - normalise → `"mi hija"` (already normalised);
   - `dao.findByAlias("mi hija")` → returns the entity;
   - `contactsProvider.findByLookupKey("<lk>")` → returns `Lucía Ruiz` contact (1 phone);
   - `dao.bumpUsage("mi hija", now)` → `useCount: 0 → 1`, `lastUsedAtMs` updated;
   - returns `listOf(lucía)`.
6. `aliasMatches.isNotEmpty()` → handler picks the single match → `placeCallOrFail(lucía, "mi hija")` → call placed.
7. Curro speaks `copy_calling` ("Llamando a Lucía Ruiz.") → `executing` → `idle`.

### Flow 2 — Stored alias whose `LOOKUP_KEY` is now stale

1. Setup: a row exists for "mi hija" → `lookupKey = "lk-removed"`; the user deleted Lucía from contacts.
2. User says "llama a mi hija".
3. `CallContactHandler.handle`: `aliases.resolveAlias("mi hija")` →
   - `dao.findByAlias` returns the entity;
   - `contactsProvider.findByLookupKey("lk-removed")` returns `null`;
   - returns `emptyList()`.
4. Handler falls through to `contacts.findByName("mi hija")` (the Phase-4 path) → likely returns `emptyList()` (no contact called "mi hija" literally) → `Failed(copy_contact_not_found, ContactNotFound)`.
5. SF-7.3 will improve this: between step 3 and 4, the handler checks `aliasRepository.findStoredAlias("mi hija")` → non-null → enters the re-learn flow with `copy_alias_unresolved`. For SF-7.2 alone, the user gets the friendly `copy_contact_not_found` line.

### Flow 3 — FunctionGemma sees the aliases on every turn

1. Setup: three aliases pre-loaded (`mi hija → Lucía Ruiz`, `el médico → Dr. Javier Sánchez`, `el del banco → Antonio Pérez García`).
2. User says "llama al médico".
3. `AssistantCoordinator.decideAndDispatch("llama al médico")`:
   - `buildContext()` runs `aliasRepository.topUsedSnapshots(10)` → returns the three snapshots ordered by `useCount DESC, lastUsedAtMs DESC`.
   - Pre-formats: `["mi hija → Lucía Ruiz", "el médico → Dr. Javier Sánchez", "el del banco → Antonio Pérez García"]`.
   - `PromptContext.knownAliases = above list`.
4. `engine.decide(transcript, ctx)` → FunctionGemma sees the prompt block:
   ```
   - Alias conocidos: mi hija → Lucía Ruiz; el médico → Dr. Javier Sánchez; el del banco → Antonio Pérez García
   ```
   → returns `{call_contact, contact: "el médico", confidence: 0.92}` (the model now knows "el médico" maps to a real contact rather than guessing).
5. Handler resolves "el médico" via `aliasRepository.resolveAlias` directly. Call placed.

---

## Function-catalog Impact

**The catalog itself is unchanged.** `call_contact`'s `params.contact` already accepts an alias per the spec (line 199: `"nombre del contacto o alias aprendido"`). The catalog's "Prompt context" requirement (in `.claude/skills/function-catalog/SKILL.md`, lines 70–76: "the list of known contact aliases") is finally implemented — the field has been on the wire since Phase 3, this SF fills it.

**Pin: no new function. No `needs_confirmation` change.**

---

## FSM States Touched

**None directly.** SF-7.2 changes data routing but no state transitions. The `Confirming` state is unchanged; `Executing` is unchanged.

(SF-7.3 will add a new branch into `CallContactHandler` that returns `NeedsContactPick` for relational terms, which the FSM already handles via SF-6.3.)

---

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `READ_CONTACTS` | `ContactsProvider.findByLookupKey` queries `ContactsContract.Contacts` for one lookup key | _(already requested in Phase 4 SF-4.9 — `CallContactHandler` gates with `ReadContactsPermissionGate.isGranted()` before any provider call)_ | Same as Phase 4: `copy_perm_missing_contacts` |

**No new permissions; no manifest changes.** The `findByLookupKey` query uses the same gate as `findByName`.

**Pin: the gate is checked at the handler layer (line 64 of `CallContactHandler.handle`); the `RoomAliasRepository` does NOT gate** — it's a thin data adapter. If the gate were lifted at the handler layer, `findByLookupKey` would throw `SecurityException` and be caught by the existing `runner.queryByLookupKey` `try/catch` (or its substitute).

---

## On-device-model Impact

**Prompt-context shape change**: `PromptContext.knownAliases` (already declared) finally non-empty in production turns. The implementer must:

- **Keep the cap at 10**. Every additional alias is ~12–25 prompt tokens (`"mi hija → Lucía Ruiz; "`); 10 aliases ≈ 150–250 tokens. The Phase-3 prompt budget (per `FunctionCallPromptBuilder` Kdoc: < 600 tokens on empty-context happy path) absorbs 10 aliases comfortably.
- **Order by `useCount DESC, lastUsedAtMs DESC`** so the most-used aliases reach the model first; rarely-used aliases get dropped from the top-10.
- **The string format is `"alias → displayName"`** (formatted in the coordinator before reaching `PromptContext`). The prompt-builder's existing `"; "` join handles concatenation — no change needed there.
- **Latency**: `topUsed(10)` against an indexed table with < 50 rows is sub-millisecond. The DAO call adds ~1–2 ms to the per-turn pipeline (vs. the ~300 ms FunctionGemma inference). Acceptable.

**Pin: no Gemma 3n. No model loading. No prompt-engineering experimentation.** SF-7.2 is data plumbing — the prompt structure is locked by the existing golden tests.

---

## Android Specification

### Files added

```
app/src/main/java/com/curro/app/data/contacts/
    RoomAliasRepository.kt

app/src/test/java/com/curro/app/data/contacts/
    RoomAliasRepositoryTest.kt        # ~10 cases, Robolectric + in-memory Room + fakes

app/src/test/java/com/curro/app/util/
    FakeAliasRepository.kt             # reusable fake for SF-7.2 + SF-7.3 coordinator tests
```

### Files modified

```
app/src/main/java/com/curro/app/domain/repository/
    AliasRepository.kt                 # 1 method → 5 methods + AliasView + AliasSnapshot
    ContactsProvider.kt                # adds findByLookupKey

app/src/main/java/com/curro/app/data/contacts/
    ContactsContractProvider.kt        # implements findByLookupKey
    ContactsQueryRunner.kt             # adds queryByLookupKey (or widened query()) — implementer's choice

app/src/main/java/com/curro/app/assistant/
    AssistantCoordinator.kt            # buildContext() reads aliasRepository.topUsedSnapshots(10)

app/src/main/java/com/curro/app/di/
    ContactsModule.kt                  # bind RoomAliasRepository (not EmptyAliasRepository)

app/src/test/java/com/curro/app/data/ml/
    FunctionCallPromptBuilderTest.kt   # +3 cases

app/src/test/java/com/curro/app/assistant/
    AssistantCoordinatorTest.kt        # +3 cases (Group T)

app/src/test/java/com/curro/app/data/contacts/
    ContactsContractProviderTest.kt    # +3 cases for findByLookupKey

app/src/test/java/com/curro/app/handler/
    CallContactHandlerTest.kt          # +1 case (alias-first resolution)
```

### Files deleted

```
app/src/main/java/com/curro/app/data/contacts/
    EmptyAliasRepository.kt

app/src/test/java/com/curro/app/data/contacts/
    EmptyAliasRepositoryTest.kt
```

### `AliasRepository.kt` (extended interface)

```kotlin
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
    suspend fun learn(alias: String, contact: Contact, source: AliasSource)

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
```

### `ContactsProvider.kt` (extended)

```kotlin
package com.curro.app.domain.repository

import com.curro.app.domain.model.Contact

interface ContactsProvider {
    /** Phase-4 — existing API, unchanged. */
    suspend fun findByName(query: String): List<Contact>

    /**
     * Resolve a stored `ContactsContract.Contacts.LOOKUP_KEY` to its current
     * [Contact] (SF-7.2). Returns `null` when the contact has been deleted
     * OR the key no longer resolves (the user did a contacts re-import that
     * changed the key).
     *
     * The caller (`RoomAliasRepository.resolveAlias`) maps a `null` to an
     * `emptyList()` return; SF-7.3's handler detects this via
     * [AliasRepository.findStoredAlias] to trigger the re-learn flow.
     *
     * Defensive: catches `SecurityException` (READ_CONTACTS revoked) and
     * returns `null` (the gate at the handler layer is the primary check;
     * this is belt-and-braces).
     */
    suspend fun findByLookupKey(lookupKey: String): Contact?
}
```

### `RoomAliasRepository.kt` (the new real impl)

```kotlin
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
 * one Phase-4 caller (`CallContactHandler.handle` line 72); new callers are
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
 */
@Singleton
class RoomAliasRepository
    @Inject
    constructor(
        private val dao: ContactAliasDao,
        private val contactsProvider: ContactsProvider,
        private val timeProvider: TimeProvider,
    ) : AliasRepository {

        override suspend fun resolveAlias(alias: String): List<Contact> {
            val normalised = alias.trim().lowercase().curroNormalize()
            if (normalised.isEmpty()) return emptyList()
            val entry = dao.findByAlias(normalised) ?: return emptyList()
            val contact = contactsProvider.findByLookupKey(entry.lookupKey) ?: return emptyList()
            dao.bumpUsage(normalised, timeProvider.now())
            return listOf(contact)
        }

        override suspend fun learn(alias: String, contact: Contact, source: AliasSource) {
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

        override suspend fun findStoredAlias(alias: String): AliasRecord? {
            val normalised = alias.trim().lowercase().curroNormalize()
            if (normalised.isEmpty()) return null
            val entry = dao.findByAlias(normalised) ?: return null
            return AliasRecord(displayName = entry.displayName, source = entry.source)
        }
    }
```

### `ContactsContractProvider.findByLookupKey`

The Phase-4 `ContactsContractProvider` (`data/contacts/ContactsContractProvider.kt`) delegates row-fetching to `ContactsQueryRunner.query()` which returns all phone-numbered rows. SF-7.2 widens the runner API:

```kotlin
// ContactsQueryRunner.kt — add this method
interface ContactsQueryRunner {
    suspend fun query(): List<ContactRow>                    // Phase-4 (existing)
    suspend fun queryByLookupKey(lookupKey: String): List<ContactRow>  // Phase-7 NEW
}

// ContentResolverContactsQueryRunner.kt — production impl
override suspend fun queryByLookupKey(lookupKey: String): List<ContactRow> = withContext(ioDispatcher) {
    runCatching {
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            PROJECTION,
            "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} = ?",
            arrayOf(lookupKey),
            null,
        )?.use { it.mapToRows() } ?: emptyList()
    }.getOrElse { emptyList() }  // SecurityException + others swallowed defensively
}
```

```kotlin
// ContactsContractProvider.kt — add the method
override suspend fun findByLookupKey(lookupKey: String): Contact? {
    if (lookupKey.isBlank()) return null
    val rows = runner.queryByLookupKey(lookupKey)
    if (rows.isEmpty()) return null
    val first = rows.first()
    return Contact(
        lookupKey = first.lookupKey,
        displayName = first.displayName,
        phoneNumbers = rows
            .mapNotNull { it.phoneNumber?.trim()?.takeIf { p -> p.isNotEmpty() } }
            .distinct(),
        photoUri = rows.firstOrNull { it.photoUri != null }?.photoUri,
    )
}
```

**Pin: implementer chooses whether `ContactsQueryRunner.query()` is widened to accept a nullable `lookupKey: String? = null` parameter OR a sister `queryByLookupKey` method is added.** Recommend the sister method — keeps the existing `query()` signature stable for the Phase-4 tests. Either way, the FakeContactsQueryRunner in `test/util/` is updated.

### `AssistantCoordinator.buildContext()` change

```kotlin
// Before (Phase 3, lines 949-954):
private fun buildContext(): PromptContext =
    PromptContext(
        nowIso = LocalDateTime.now(clock).withNano(0).toString(),
        unreadMessagesSummary = "",
        knownAliases = emptyList(),
    )

// After (SF-7.2):
private suspend fun buildContext(): PromptContext =
    PromptContext(
        nowIso = LocalDateTime.now(clock).withNano(0).toString(),
        unreadMessagesSummary = "",
        knownAliases = aliasRepository
            .topUsedSnapshots(PROMPT_ALIAS_LIMIT)
            .map { "${it.alias} → ${it.displayName}" },
    )

// Add to the private companion object:
private companion object {
    // ... existing constants ...
    const val PROMPT_ALIAS_LIMIT = 10  // SF-7.2 / US-046 (prompt-budget cap)
}
```

**Pin: `buildContext()` becomes `suspend`** (it does a DAO read). The single caller is `decideAndDispatch` (line 542 — already suspending: `val decision = engine.decide(transcript, buildContext())`). The implementer verifies there's no other caller via `grep buildContext` in the project.

**Inject `aliasRepository: AliasRepository` into the coordinator's constructor** — the parameter list already includes 15+ Hilt-injected deps; one more lands cleanly. Existing `CallContactHandler` already takes `aliases: AliasRepository` (line 45) — Hilt picks the same `@Binds`-routed `RoomAliasRepository` impl.

### `ContactsModule.kt` change

```kotlin
// Before (Phase 4):
@Binds
@Singleton
fun bindAliasRepository(impl: EmptyAliasRepository): AliasRepository

// After (SF-7.2):
@Binds
@Singleton
fun bindAliasRepository(impl: RoomAliasRepository): AliasRepository
```

Update the module-level Kdoc from `"Phase-7 migration: swap [EmptyAliasRepository] → RoomAliasRepository in the bindAliasRepository line; all callers (including SF-4.10's call_contact) stay unchanged."` to `"Phase-7 wired (US-046): aliases are Room-backed via RoomAliasRepository. EmptyAliasRepository was deleted."`.

### Navigation Routes

No new routes.

### Composables by Feature

_(No new composables. SF-7.2 is data wiring + prompt-context plumbing.)_

### Material Design Components

_(N/A.)_

---

## Acceptance Criteria

### Build & static checks

- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.
- [ ] No `EmptyAliasRepository` references remain in `app/src/main/` (the file and its test are deleted).

### Interface contract

- [ ] `AliasRepository` has six members: `resolveAlias`, `learn`, `observeAll`, `topUsedSnapshots`, `deleteAll`, `findStoredAlias`. Two data classes co-located: `AliasView`, `AliasSnapshot`. One data class `AliasRecord` co-located (used by SF-7.3 via `findStoredAlias`).
- [ ] `ContactsProvider` has two methods: `findByName` (unchanged), `findByLookupKey` (NEW).
- [ ] `ContactsQueryRunner` exposes a method for lookup-key queries (implementer's choice of `queryByLookupKey` or widened `query(lookupKey: String? = null)`).

### Behaviour

- [ ] `RoomAliasRepository.resolveAlias("Mi  Hija ")` normalises to `"mi hija"` (trim + lowercase + `curroNormalize`); finds the row; resolves via `findByLookupKey`; bumps usage; returns the contact.
- [ ] `RoomAliasRepository.resolveAlias("mi hija")` when DAO has the row but `findByLookupKey` returns null → returns `emptyList()` AND does NOT bump usage (no point bumping a stale row's count).
- [ ] `RoomAliasRepository.findStoredAlias("mi hija")` on the same stale-row scenario → returns a non-null `AliasRecord(displayName, source)`.
- [ ] `RoomAliasRepository.learn("mi hija", contact, AliasSource.LEARNED)` inserts the row with `useCount = 0`, `createdAtMs = lastUsedAtMs = timeProvider.now()`.
- [ ] `RoomAliasRepository.topUsedSnapshots(10)` returns top-10 by `useCount DESC, lastUsedAtMs DESC` as `AliasSnapshot(alias, displayName)`.
- [ ] `ContactsContractProvider.findByLookupKey("lk-x")` queries `LOOKUP_KEY = ?` and returns the grouped `Contact`; `null` when no rows.
- [ ] `ContactsContractProvider.findByLookupKey("")` returns `null` defensively.

### Coordinator integration

- [ ] `AssistantCoordinator.buildContext()` is `suspend`; reads `aliasRepository.topUsedSnapshots(10)`; formats as `"alias → displayName"`; passes to `PromptContext.knownAliases`.
- [ ] `engine.decide(transcript, ctx)` receives the formatted list — verified via `AssistantCoordinatorTest` Group T cases.
- [ ] `PROMPT_ALIAS_LIMIT = 10` declared on the coordinator's companion object.

### Hilt

- [ ] `ContactsModule` binds `RoomAliasRepository` (NOT `EmptyAliasRepository`); the binding is `@Singleton`.
- [ ] `EmptyAliasRepository.kt` and its test file are deleted.
- [ ] `CallContactHandler` still compiles (constructor unchanged — it depends on `AliasRepository`, the Hilt-bound impl swap is invisible to the handler).

### Tests (acceptance)

- [ ] `RoomAliasRepositoryTest` — 10 cases pass.
- [ ] `FunctionCallPromptBuilderTest` — 3 new cases pass + every existing golden test still passes.
- [ ] `AssistantCoordinatorTest` — 3 new Group T cases pass.
- [ ] `ContactsContractProviderTest` — 3 new cases for `findByLookupKey` pass.
- [ ] `CallContactHandlerTest` — 1 new case (`findByAlias_hit_callsContactDirectly`) passes.

### Privacy

- [ ] `topUsedSnapshots` returns only `alias` + `displayName` — `LOOKUP_KEY` never exposed outside the data layer.
- [ ] No new telemetry events.
- [ ] `Log.w` and `Log.d` calls (if any added) MUST NOT include the alias text or the contact name — pin: search the diff before merge.

### Regression

- [ ] Every SF-7.1 + Phase-6 + Phase-5 + Phase-4 test still passes.
- [ ] The pre-SF-7.2 `EmptyAliasRepositoryTest` is deleted — no orphan test on the build.
- [ ] The Phase-4 `CallContactHandlerTest` cases that asserted "alias always returns empty" still pass (they used a `FakeAliasRepository`-equivalent — verify the existing fakes are compatible OR migrate them to the new `FakeAliasRepository`).

---

## Senior-UX & Copy

**No new copy in this SF.**

The user-visible UX win is FunctionGemma's improved accuracy on alias-bearing utterances ("llama a mi hija" / "ponme con el médico"): the model now sees the user's learned vocabulary in the prompt context and is significantly less likely to misinterpret. The handler path is also faster (the alias short-circuits the slower `findByName` LIKE-scan).

The only user-visible side effect of a wrong wiring would be a stuck "alias is not resolving" — handled by SF-7.3's re-learn flow with `copy_alias_unresolved`. SF-7.2 alone falls through to `copy_contact_not_found` if a stale alias is encountered AND the raw query doesn't match any name (the friendly Phase-4 line — still acceptable).

---

## Performance Considerations

- `topUsed(10)` against an indexed table with ≤ 100 rows: sub-millisecond. The per-turn pipeline cost is dominated by FunctionGemma inference (~300 ms), not the DAO read.
- `findByLookupKey` is a single-row `ContentResolver.query` with an indexed `LOOKUP_KEY = ?` predicate: ~5–15 ms on the Redmi 15.
- `buildContext()` is now `suspend` — the implementer verifies no caller blocks the main thread (the coordinator's call site is already inside a `scope.launch { ... }`).
- The DAO calls in `RoomAliasRepository` are NOT wrapped in `withContext(ioDispatcher)` — Room's suspend DAOs auto-dispatch to the configured `queryExecutor`/`transactionExecutor`. **Pin: verify the implementer doesn't add a redundant `withContext` wrapper** (would slow each call by a context-switch).

---

## Testing Requirements

### `RoomAliasRepositoryTest.kt` (JVM Robolectric, ~10 cases)

Test infra: in-memory Room + `FakeContactsProvider` + `TestTimeProvider`.

```kotlin
@RunWith(AndroidJUnit4::class)
class RoomAliasRepositoryTest {
    private lateinit var db: CurroDatabase
    private lateinit var dao: ContactAliasDao
    private val contactsProvider = FakeContactsProvider()
    private val timeProvider = TestTimeProvider(initialNowMs = 1000L)
    private lateinit var repo: RoomAliasRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), CurroDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.contactAliasDao()
        repo = RoomAliasRepository(dao, contactsProvider, timeProvider)
    }

    @After fun tearDown() { db.close() }
}
```

Cases:
1. `resolveAlias_unknown_returnsEmpty` — empty DAO; `resolveAlias("mi hija") == emptyList`.
2. `resolveAlias_known_returnsSingleContact` — pre-populate DAO + fake contacts; assert returned list size == 1 + correct contact.
3. `resolveAlias_knownButLookupKeyStale_returnsEmpty` — DAO has row; `FakeContactsProvider.findByLookupKeyResult = null` → `resolveAlias` returns empty.
4. `resolveAlias_known_bumpsUsageAndTimestamp` — pre-state `useCount = 3, lastUsedAtMs = 1L`; `timeProvider.advanceTo(2000L)`; `resolveAlias`; DAO row now `useCount = 4, lastUsedAtMs = 2000L`.
5. `resolveAlias_staleRow_doesNotBumpUsage` — covered by case 3; explicit assertion `useCount == initial` after the empty-return.
6. `learn_persistsEntityWithSourceLEARNED` — `learn("mi hija", contact, LEARNED)`; `dao.findByAlias("mi hija")` returns the row with `source == LEARNED`, `createdAtMs == lastUsedAtMs == 1000L`, `useCount == 0`.
7. `learn_sameAliasTwice_replacesViaUpsert` — `learn("mi hija", A, LEARNED)` then `learn("mi hija", B, LEARNED)`; `observeAll().first().size == 1`; the row's `lookupKey` is B's.
8. `observeAll_emitsAliasViews_inUseCountDescOrder` (Turbine) — insert three with counts `1, 5, 3` → first emission returns Views in `[5, 3, 1]` order.
9. `topUsedSnapshots_limit3_returnsThree` — insert 5; `topUsedSnapshots(3).size == 3`.
10. `findStoredAlias_existing_returnsRecord_with_displayName_and_source` — verify the record contents.
11. `findStoredAlias_unknown_returnsNull` — empty DAO; `findStoredAlias("inexistente") == null`.
12. `deleteAll_clearsTable_observeAllEmitsEmpty` — insert three; `deleteAll`; Turbine sees the empty list.

(PM allows 10–12 cases — implementer picks the most-informative subset; the brief lists 12 to be exhaustive.)

### `FunctionCallPromptBuilderTest.kt` (3 new cases)

```kotlin
@Test fun aliasesBlock_emptyList_rendersNinguno() {
    // Regression for Phase-3 behaviour — the line still says "ninguno" when knownAliases is empty.
    val ctx = PromptContext(nowIso = "2026-05-16T10:00:00", unreadMessagesSummary = "", knownAliases = emptyList())
    val prompt = builder.build("hola", ctx)
    assertThat(prompt).contains("- Alias conocidos: ninguno")
}

@Test fun aliasesBlock_oneAlias_rendersSingleArrowFormat() {
    val ctx = PromptContext(
        nowIso = "2026-05-16T10:00:00",
        unreadMessagesSummary = "",
        knownAliases = listOf("mi hija → Lucía Ruiz"),
    )
    val prompt = builder.build("llama a mi hija", ctx)
    assertThat(prompt).contains("- Alias conocidos: mi hija → Lucía Ruiz")
}

@Test fun aliasesBlock_tenAliases_rendersAllSeparatedBySemiColon() {
    val ten = (1..10).map { "alias$it → Display $it" }
    val ctx = PromptContext(
        nowIso = "2026-05-16T10:00:00",
        unreadMessagesSummary = "",
        knownAliases = ten,
    )
    val prompt = builder.build("hola", ctx)
    val expected = "- Alias conocidos: " + ten.joinToString("; ")
    assertThat(prompt).contains(expected)
}
```

### `AssistantCoordinatorTest.kt` (3 new Group T cases)

Uses `FakeAliasRepository` (new — `test/util/FakeAliasRepository.kt`).

```kotlin
@Test fun buildContext_emptyAliasRepo_passesEmptyListToPromptBuilder() = runTest {
    fakeAliasRepository.topUsedSnapshotsResult = emptyList()
    // act: run a full mic-press turn
    coordinator.onMicPressed()
    // ... fakes drive STT → "llama a Pepito" → engine.decide is called
    val capturedContext = fakeEngine.lastPromptContext  // FakeFunctionCallEngine records the ctx
    assertThat(capturedContext.knownAliases).isEmpty()
}

@Test fun buildContext_threeAliases_passesAllToPromptBuilder() = runTest {
    fakeAliasRepository.topUsedSnapshotsResult = listOf(
        AliasSnapshot("mi hija", "Lucía Ruiz"),
        AliasSnapshot("el medico", "Dr. Javier Sánchez"),
        AliasSnapshot("el del banco", "Antonio Pérez"),
    )
    coordinator.onMicPressed()
    val capturedContext = fakeEngine.lastPromptContext
    assertThat(capturedContext.knownAliases).containsExactly(
        "mi hija → Lucía Ruiz",
        "el medico → Dr. Javier Sánchez",
        "el del banco → Antonio Pérez",
    ).inOrder()
}

@Test fun buildContext_fifteenAliases_passesTopTenOnly() = runTest {
    fakeAliasRepository.topUsedSnapshotsLimit = null  // capture the limit arg
    fakeAliasRepository.topUsedSnapshotsResult = (1..10).map { AliasSnapshot("alias$it", "Display $it") }
    coordinator.onMicPressed()
    assertThat(fakeAliasRepository.topUsedSnapshotsLimit).isEqualTo(10)
    val capturedContext = fakeEngine.lastPromptContext
    assertThat(capturedContext.knownAliases.size).isEqualTo(10)
}
```

### `ContactsContractProviderTest.kt` (3 new cases)

Uses `FakeContactsQueryRunner`.

1. `findByLookupKey_unknownKey_returnsNull` — `runner.queryByLookupKeyResult = emptyList()`; `findByLookupKey("lk-x") == null`.
2. `findByLookupKey_matchingRow_returnsContact` — `runner.queryByLookupKeyResult = listOf(rowFor("lk-y", "Lucía Ruiz", "+34600"))`; `findByLookupKey("lk-y")` returns `Contact(lk-y, "Lucía Ruiz", ["+34600"], null)`.
3. `findByLookupKey_emptyKey_returnsNull` — `findByLookupKey("") == null` (defensive early-return).

### `CallContactHandlerTest.kt` (1 new case)

```kotlin
@Test fun findByAlias_hit_callsContactDirectly_noFallback_toFindByName() = runTest {
    fakeAliasRepository.resolveAliasResult["mi hija"] = listOf(luciaContact)
    val result = handler.handle(callOf("call_contact", "contact" to "mi hija"))
    assertThat(result).isInstanceOf(HandlerResult.Spoken::class.java)
    assertThat((result as HandlerResult.Spoken).speech).contains("Lucía Ruiz")
    assertThat(fakeContactsProvider.findByNameInvocations).isEmpty()  // pin: alias-first short-circuit
    assertThat(fakeCallController.lastCalledNumber).isEqualTo(luciaContact.phoneNumbers.first())
}
```

### Real-device verification

- [ ] Build + install on Redmi 15.
- [ ] Insert one alias via SQL (the manual-smoke from the Summary).
- [ ] Say "llama a mi hija" → call placed directly (no picker overlay).
- [ ] `adb shell run-as com.curro.app sqlite3 databases/curro.db "SELECT alias, useCount FROM contact_aliases;"` shows `useCount` incremented after the call.
- [ ] Insert 10 aliases manually; press mic, say anything; capture FunctionGemma's prompt via the debug-build JSON debug overlay (already wired in Phase 3 — `AssistantSideEffect.ShowDebugJson` only fires for the model response, not the prompt, BUT the implementer can add a temporary `Log.d("Curro/PromptDebug", prompt)` for one manual run, then revert before commit). Verify the prompt contains all 10 aliases separated by `"; "`.

---

## Implementation Notes

- **`FakeAliasRepository`** lives in `app/src/test/java/com/curro/app/util/FakeAliasRepository.kt` and is shared between SF-7.2 + SF-7.3 + SF-7.5 tests. PM pins the shape (capture every `learn` invocation into a `List<LearnInvocation>` — SF-7.3 uses this to verify "never learn during disambig"). SF-7.2 needs `resolveAliasResult: MutableMap<String, List<Contact>>`, `topUsedSnapshotsResult: List<AliasSnapshot>`, `findStoredAliasResult: MutableMap<String, AliasRecord?>`, `learnCalls: MutableList<LearnInvocation>`, `observeAllStream: MutableStateFlow<List<AliasView>>`.
- **Deleting `EmptyAliasRepository` + its test** is part of this SF — not a follow-up. The Hilt graph fails to compile if the binding still points at it; the test fails to compile if the class is gone. Two birds.
- **Reading the `local-data` skill** for the alias-normalisation rules — `curroNormalize()` already exists in `data/apps/` (used by `OpenAppHandler`). SF-7.2 reuses it on the alias side.
- **PM Owner has written**: Metadata, Summary, Scope, User Flows, Function-catalog Impact, FSM States Touched, Senior-UX & Copy, Acceptance Criteria.
- **Implementer (android-developer) writes**: the code per the file shapes above, the test specs as written.

---

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial PM draft for the Phase-7 PM batch |
