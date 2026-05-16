# US-033 — SF-4.9 · `ContactsProvider` + `AliasRepository` (Phase-4 stub)

> **Spec trace:** spec §5 (catalog entry `call_contact` depends on this),
> spec §6 flow 1 (call with high confidence — happy path), spec §7 (alias
> model — `LOOKUP_KEY`-based), spec §10 (`READ_CONTACTS` requested
> lazily).
> **Master-plan:** SF-4.9.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-025 (handler interface).
> **Size:** M.
> **Skills:** `platform-integrations` (ContactsContract section),
> `local-data` (LOOKUP_KEY rationale; alias-learning is the Phase-7 wire-up),
> `function-catalog`, `testing-patterns`, `git-workflow`, `brand-design`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `ContactsProvider` + `AliasRepository` (Phase-4 empty stub) |
| **US ID** | US-033 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-developer |

---

## 1. Summary

Resolve a spoken name to **0, 1, or many** `Contact` records via
`ContactsContract`. The provider returns rich enough information for
SF-4.10's `call_contact` handler to either place the call (single match)
or report the ambiguity / not-found / permission-missing failure modes.

The `AliasRepository` exists in Phase 4 only as an **empty stub**: the
Phase-7 alias-learning subsystem (spec §7 + flow 4) replaces it with a
Room-backed real implementation, **without touching SF-4.10**. The
interface is the seam.

Why this matters for *this* user: he says "llama a Pepito"; Curro
finds the right Pepito or honestly reports ambiguity. Without this
provider, `call_contact` is a stub.

---

## 2. Scope

**In scope:**

- `domain/model/Contact.kt` — `data class` carrying `lookupKey`, `displayName`,
  `phoneNumbers`, `photoUri`.
- `domain/repository/ContactsProvider.kt` — interface.
- `domain/repository/AliasRepository.kt` — interface.
- `data/contacts/ContactsContractProvider.kt` — production impl.
- `data/contacts/ContactsQueryRunner.kt` — interface + `ContentResolverContactsQueryRunner`
  impl. **The seam that makes the provider unit-testable** (the test fakes
  the runner, not the `ContentResolver`).
- `data/contacts/EmptyAliasRepository.kt` — Phase-4 stub.
- `data/permissions/ReadContactsPermissionGate.kt` — interface + impl.
- `di/ContactsModule.kt`.
- Manifest: `<uses-permission android:name="android.permission.READ_CONTACTS" />`.
- New `CurroError` variants: `AmbiguousContact(matches)`, `ContactNotFound(query)`,
  `ReadContactsPermissionMissing`.
- New `strings.xml` entry: `copy_contact_ambiguous_phase4`.
- ≥ 12 JVM tests on `ContactsContractProvider`.
- 1 trivial test on `EmptyAliasRepository`.

**Out of scope:**

- The `call_contact` handler — SF-4.10.
- Alias **learning** (spec §7 + flow 4) — Phase 7.
- Phase 6's ambiguity-picker UI — SF-4.10 returns the `AmbiguousContact`
  error; Phase 6 replaces with the real picker overlay.

---

## 3. User Flows

The provider is infrastructure. The user-visible flows live in SF-4.10.

### Flow 1: Single match — `findByName("Pepito")`

1. Caller passes `"Pepito"` (already-trimmed).
2. Provider lowercases + accent-strips both sides.
3. `ContactsQueryRunner.query()` returns rows for every phone-bearing contact.
4. Filter: rows whose normalised display name matches (`contains` + word-
   boundary regex).
5. Group rows by `LOOKUP_KEY` → 1 contact ("Pepe García Hernández"), 1 phone.
6. Return `[Contact(...)]`.

### Flow 2: Many matches — three Marías

1. `findByName("María")`.
2. Filter matches 3 rows with 3 distinct `LOOKUP_KEY`s.
3. Return list of 3 `Contact`s — SF-4.10 turns this into
   `AmbiguousContact(matches)`.

### Flow 3: No match

1. `findByName("Foobar")`.
2. Filter returns nothing → `emptyList()`.

### Flow 4: Alias lookup — Phase 4 stub

1. SF-4.10 calls `aliases.resolveAlias("mi hija")`.
2. `EmptyAliasRepository` returns `emptyList()` always.
3. SF-4.10 falls through to `contacts.findByName("mi hija")` — almost
   certainly empty (no contact is named "mi hija") → `ContactNotFound`. The
   Phase-7 alias subsystem fixes this.

---

## 4. Function-catalog Impact

**No catalog change.**

---

## 5. FSM States Touched

None directly — the provider is infrastructure. SF-4.10 uses the result
inside `Processing → Speaking → Idle` or `Failed`.

---

## 6. Android System Integrations & Permissions

| Integration | Why |
|---|---|
| `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` | Phone-bearing rows are the right surface for "callable contacts". |
| `Cursor` + the right projection | Read `LOOKUP_KEY`, `DISPLAY_NAME_PRIMARY`, `NUMBER`, `PHOTO_THUMBNAIL_URI`. |
| `LOOKUP_KEY` | Stable across contact merges — per `local-data` skill, **never use `_ID`**. |

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `READ_CONTACTS` | The query needs it. | Runtime — by SF-4.10's handler on first `call_contact`. Never at install. | Provider throws (when `Cursor.query` returns null); SF-4.10 surfaces `ReadContactsPermissionMissing` → `copy_perm_missing_contacts`. **Decision pinned**: the provider catches the `SecurityException` and returns `emptyList()`; the gate (separately) reports the permission state to SF-4.10. |

**Manifest** — append:

```xml
<!--
    SF-4.9 (US-033): READ_CONTACTS is required by ContactsContractProvider.
    Runtime request is wired by SF-4.10 (US-034) on first call_contact —
    NEVER at install. Denial maps to CurroError.ReadContactsPermissionMissing
    → copy_perm_missing_contacts.
-->
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

---

## 7. On-device-model Impact

The `knownAliases` field of `PromptContext` (US-020) stays empty in Phase 4
— the Phase-7 alias subsystem fills it.

No prompt change in this SF.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/
├── domain/
│   ├── model/Contact.kt
│   └── repository/
│       ├── ContactsProvider.kt
│       └── AliasRepository.kt
├── data/
│   ├── contacts/
│   │   ├── ContactsQueryRunner.kt                // interface + ContentResolver impl
│   │   ├── ContactsContractProvider.kt
│   │   └── EmptyAliasRepository.kt
│   └── permissions/
│       └── ReadContactsPermissionGate.kt         // interface + impl
└── di/
    └── ContactsModule.kt
```

### 8.2 `Contact.kt`

```kotlin
package com.curro.app.domain.model

data class Contact(
    /** Stable across contact merges — the right key per `local-data` skill. */
    val lookupKey: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val photoUri: String?,
)
```

### 8.3 `ContactsProvider.kt`

```kotlin
package com.curro.app.domain.repository

import com.curro.app.domain.model.Contact

interface ContactsProvider {
    /**
     * Resolve [query] (a spoken name) to 0, 1, or many [Contact] records.
     *
     * Normalisation: lowercase + accent-strip (Spanish locale) on both sides.
     * Matching: a row matches iff its normalised display name *word-boundary
     * contains* the normalised query (`\\bquery\\b`). Multi-token queries
     * fall back to substring contains.
     *
     * Empty / blank query → `emptyList()`.
     */
    suspend fun findByName(query: String): List<Contact>
}
```

### 8.4 `AliasRepository.kt`

```kotlin
package com.curro.app.domain.repository

import com.curro.app.domain.model.Contact

interface AliasRepository {
    /**
     * Phase 4: returns `emptyList()` always (the EmptyAliasRepository stub).
     *
     * Phase 7: the Room-backed implementation looks up [alias] in the user's
     * learned-alias table and returns the matched [Contact] (or list).
     */
    suspend fun resolveAlias(alias: String): List<Contact>
}
```

### 8.5 `ContactsQueryRunner.kt`

```kotlin
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
 * Thin testable wrapper around the `ContentResolver` query.
 *
 * Production impl runs the query on [ioDispatcher]. Tests provide a fake
 * runner that returns a hand-built list of [Row]s — the unit test never
 * touches a real `ContentResolver` or `Cursor`.
 */
interface ContactsQueryRunner {
    suspend fun query(): List<Row>

    data class Row(
        val lookupKey: String,
        val displayName: String,
        val phoneNumber: String?,
        val photoUri: String?,
    )
}

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
                        null, null, null,
                    )?.use { cursor ->
                        val keyIdx = cursor.getColumnIndexOrThrow(projection[0])
                        val nameIdx = cursor.getColumnIndexOrThrow(projection[1])
                        val phoneIdx = cursor.getColumnIndexOrThrow(projection[2])
                        val photoIdx = cursor.getColumnIndexOrThrow(projection[3])
                        while (cursor.moveToNext()) {
                            val key = cursor.getString(keyIdx) ?: continue
                            val name = cursor.getString(nameIdx) ?: continue
                            val phone = cursor.getString(phoneIdx)
                            val photo = cursor.getString(photoIdx)
                            out += ContactsQueryRunner.Row(key, name, phone, photo)
                        }
                    }
                } catch (_: SecurityException) {
                    // READ_CONTACTS not granted — provider returns emptyList().
                }
                out
            }
    }
```

### 8.6 `ContactsContractProvider.kt`

```kotlin
package com.curro.app.data.contacts

import com.curro.app.data.apps.curroNormalize
import com.curro.app.domain.model.Contact
import com.curro.app.domain.repository.ContactsProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsContractProvider
    @Inject
    constructor(
        private val runner: ContactsQueryRunner,
    ) : ContactsProvider {
        override suspend fun findByName(query: String): List<Contact> {
            val q = query.trim()
            if (q.isEmpty()) return emptyList()
            val normalisedQuery = q.curroNormalize()

            val rows = runner.query()
            if (rows.isEmpty()) return emptyList()

            // Decide on the match strategy from the query shape.
            val isMultiToken = normalisedQuery.contains(' ')
            val matches =
                rows.filter { row ->
                    val name = row.displayName.curroNormalize()
                    if (isMultiToken) {
                        normalisedQuery in name
                    } else {
                        // Word-boundary match — avoids "ana" matching "Susana".
                        val wb = Regex("\\b" + Regex.escape(normalisedQuery) + "\\b")
                        wb.containsMatchIn(name)
                    }
                }

            // Group by LOOKUP_KEY → one Contact per real person, with all phones.
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
```

### 8.7 `EmptyAliasRepository.kt`

```kotlin
package com.curro.app.data.contacts

import com.curro.app.domain.model.Contact
import com.curro.app.domain.repository.AliasRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-4 stub. Phase 7 (spec §7 + flow 4) replaces this with a Room-backed
 * implementation whose Hilt @Binds points at the new RoomAliasRepository —
 * the interface and every caller (including SF-4.10's `call_contact`
 * handler) stay unchanged.
 */
@Singleton
class EmptyAliasRepository
    @Inject
    constructor() : AliasRepository {
        override suspend fun resolveAlias(alias: String): List<Contact> = emptyList()
    }
```

### 8.8 `ReadContactsPermissionGate.kt`

```kotlin
package com.curro.app.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface ReadContactsPermissionGate {
    fun isGranted(): Boolean
}

class SystemReadContactsPermissionGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ReadContactsPermissionGate {
        override fun isGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
    }
```

### 8.9 `ContactsModule.kt`

```kotlin
package com.curro.app.di

import com.curro.app.data.contacts.ContactsContractProvider
import com.curro.app.data.contacts.ContactsQueryRunner
import com.curro.app.data.contacts.ContentResolverContactsQueryRunner
import com.curro.app.data.contacts.EmptyAliasRepository
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.data.permissions.SystemReadContactsPermissionGate
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.ContactsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContactsModule {
    @Binds @Singleton
    abstract fun bindContactsQueryRunner(impl: ContentResolverContactsQueryRunner): ContactsQueryRunner

    @Binds @Singleton
    abstract fun bindContactsProvider(impl: ContactsContractProvider): ContactsProvider

    @Binds @Singleton
    abstract fun bindAliasRepository(impl: EmptyAliasRepository): AliasRepository

    @Binds @Singleton
    abstract fun bindReadContactsPermissionGate(
        impl: SystemReadContactsPermissionGate,
    ): ReadContactsPermissionGate
}
```

### 8.10 `CurroError` additions

```kotlin
// ── Contacts + telephony (US-033 / SF-4.9, US-034 / SF-4.10) ──────────────

/** A name resolved to multiple contacts — Phase 4 returns this; Phase 6 surfaces a picker. */
data class AmbiguousContact(val matches: List<com.curro.app.domain.model.Contact>) : CurroError()

/** No contact matched the query. */
data class ContactNotFound(val query: String) : CurroError()

/** READ_CONTACTS not granted. Speech: copy_perm_missing_contacts. */
data object ReadContactsPermissionMissing : CurroError()
```

### 8.11 `strings.xml` — add / reuse

Reuse:

- `copy_perm_missing_contacts`.
- `copy_contact_not_found`.

New:

```xml
<!-- US-033 (SF-4.9) — Phase-4-only fallback for AmbiguousContact. Phase 6 replaces
     this path with the real picker overlay; the brief loudly flags this is
     provisional. Curro voice: honest about the limitation. -->
<string name="copy_contact_ambiguous_phase4">Tienes varios contactos así; espera, todavía no sé elegir entre ellos.</string>
```

---

## 9. Acceptance Criteria

- [ ] All seven new files exist at the documented paths.
- [ ] `Contact` data class uses `lookupKey`, not `_ID`.
- [ ] `ContactsProvider` returns 0 contacts for empty/blank query.
- [ ] `ContactsProvider` returns 0 contacts when the runner returns empty
      (no rows / `SecurityException` swallowed).
- [ ] **Single match by exact name** → 1 `Contact` with the right
      `lookupKey`, `displayName`, ≥ 1 phone number.
- [ ] **Single match by case-insensitive name** (`"pepito"` matches "Pepito").
- [ ] **Single match by accent-stripped name** (`"jose"` matches "José").
- [ ] **Three matches** (three Marías) → list of 3 `Contact`s with three
      distinct `lookupKey`s.
- [ ] **No match** → `emptyList()`.
- [ ] **Multi-token query** (`"maria garcia"`) matches `"María García"`.
- [ ] **Multi-phone contact** — same `LOOKUP_KEY` repeated across rows → one
      `Contact` with both phones, deduped.
- [ ] **Photo URI** propagated when present; `null` otherwise.
- [ ] **Null display-name row** → silently skipped.
- [ ] **Null phone-number row** → `Contact` with empty `phoneNumbers` (SF-4.10
      treats this as "not callable" and returns `ContactNotFound`).
- [ ] **Query with apostrophe** (`"d'angelo"`) — regex-safe (uses
      `Regex.escape`).
- [ ] `EmptyAliasRepository.resolveAlias(any)` → always `emptyList()`.
- [ ] `ContactsModule` binds all four interfaces.
- [ ] Manifest gains `READ_CONTACTS` with the documented comment.
- [ ] `CurroError.AmbiguousContact`, `ContactNotFound`,
      `ReadContactsPermissionMissing` added.
- [ ] `copy_contact_ambiguous_phase4` added.
- [ ] **The runtime permission request stays in SF-4.10** — this SF declares
      the permission but does NOT prompt the user.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green.

---

## 10. Senior-UX & Copy

| String ID | Spanish | Voice notes |
|---|---|---|
| `copy_contact_not_found` (existing) | "No encuentro a %1$s en tus contactos." | Reused. Repeats the user's word back. |
| `copy_perm_missing_contacts` (existing) | "Necesito permiso para ver tus contactos. Díselo a Fran." | Reused. |
| `copy_contact_ambiguous_phase4` (NEW) | "Tienes varios contactos así; espera, todavía no sé elegir entre ellos." | Honest, signals "this will improve". Phase 6 deletes this path in favour of the real picker. |

---

## 11. Design Notes

No visual surface in this SF. The SF-4.10 handler does the calling; this SF
is the lookup engine.

---

## 12. Performance Considerations

- The `ContentResolver` query runs on `IoDispatcher` and reads every
  phone-bearing row up front (one cursor traversal). On a 500-contact
  device, this is ~10 ms.
- Filtering + grouping is `O(n)`, sub-ms.
- Decision pinned: **no caching** in Phase 4 — every `findByName` re-queries
  the resolver. Phase 7 (alongside the alias DB) introduces a small in-mem
  cache invalidated on `ContactsContract` change notifications.

---

## 13. Testing Requirements

**`ContactsContractProviderTest.kt`** — pure JVM with a `FakeContactsQueryRunner`:

```kotlin
class FakeContactsQueryRunner(val rows: List<ContactsQueryRunner.Row>) : ContactsQueryRunner {
    override suspend fun query(): List<ContactsQueryRunner.Row> = rows
}
```

Cases (≥ 12):

1. Empty rows → `findByName("Pepito")` → empty.
2. Empty/blank query → empty.
3. Exact name match → 1 contact.
4. Case-insensitive name match → 1 contact.
5. Accent-stripped name match (`"jose"` vs "José") → 1 contact.
6. Three Marías (three rows with three different `lookupKey`s, same
   normalised name) → 3 contacts.
7. Multi-token query `"maria garcia"` → matches "María García" (only).
8. Same `lookupKey` × 2 rows × 2 phone numbers → 1 contact with both phones.
9. Same `lookupKey` × 2 rows × same phone number → 1 contact with deduped
   phone list (length 1).
10. Photo URI propagation.
11. Null display-name row → skipped without error.
12. Null phone-number row → contact with empty `phoneNumbers`.
13. Apostrophe in query (`"d'angelo"`) — `Regex.escape` keeps it safe.
14. Word-boundary mode: `findByName("ana")` does NOT match "Susana" (single-
    token query uses `\bana\b`).

**`EmptyAliasRepositoryTest.kt`** — 1 case: any input → empty.

**On-device verification** on the Redmi 15: deferred to SF-4.10 (which is
where the user-visible path lives). SF-4.9 ships with the unit tests as the
sole gate.

---

## 14. Implementation Notes — Order of Operations

1. Add the three `CurroError` variants.
2. Add `copy_contact_ambiguous_phase4` to `strings.xml`.
3. Create `domain/model/Contact.kt`.
4. Create `domain/repository/ContactsProvider.kt` and `AliasRepository.kt`.
5. Create `data/contacts/ContactsQueryRunner.kt` (interface + impl).
6. Create `data/contacts/ContactsContractProvider.kt`.
7. Create `data/contacts/EmptyAliasRepository.kt`.
8. Create `data/permissions/ReadContactsPermissionGate.kt`.
9. Create `di/ContactsModule.kt`.
10. Manifest: add `READ_CONTACTS` + the comment.
11. Write `ContactsContractProviderTest`.
12. Write `EmptyAliasRepositoryTest`.
13. `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
14. Commit as `feat: add ContactsProvider + AliasRepository stub (US-033 / SF-4.9)`.

---

## 15. Phase 7 Hook

- Replace `EmptyAliasRepository` with `RoomAliasRepository` (reads the
  `contact_aliases` table; matches normalised alias against the user's learned
  list; returns the linked `Contact`(s) via the `LOOKUP_KEY`).
- Add the **learning subflow** (spec §6 flow 4) — the moment a `call_contact`
  utterance contains an unknown alias, the flow asks "¿quién es <alias>?",
  reads the top 5 contacts, persists the chosen `LOOKUP_KEY → alias` row.
- `ContactsModule` swaps the `@Binds AliasRepository` from `EmptyAliasRepository`
  to `RoomAliasRepository`; no other code changes.

---

## 16. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
