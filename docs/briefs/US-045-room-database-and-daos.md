# Brief — US-045 / SF-7.1: Room database + DAOs + Hilt `DatabaseModule`

## Metadata

| Field | Value |
|---|---|
| **Feature** | First Room database in Curro — `CurroDatabase` v1 with three entities (`ContactAliasEntity`, `AppUsageEntity`, `FailedCommandEntity`), three DAOs (`ContactAliasDao`, `AppUsageDao`, `FailedCommandDao`), a Hilt `DatabaseModule`, schema export, and a prototype-only `fallbackToDestructiveMigration` escape hatch |
| **US ID** | US-045 |
| **SF ID** | SF-7.1 |
| **Phase** | 7 — Alias learning & local persistence |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst (Opus) |
| **Implementer** | android-developer (Sonnet) |
| **Size** | M |
| **Depends on** | SF-0.2 (Hilt graph) |
| **Unblocks** | SF-7.2 (RoomAliasRepository), SF-7.3 (alias-learning subflow), SF-7.4 (recency favourites), SF-7.5 (FailedCommandLog) |

---

## Summary

Phase 7's foundation. Curro has had a `data/local/` package since SF-6.1 (`SettingsDataStore` for DataStore-backed settings); Room sits next to it as a parallel sub-package handling structured/queryable data — aliases ("mi hija" → Lucía Ruiz' lookup key), app-usage counts (drives the implicit favourites grid in SF-7.4), and the failed-commands log (Fran's debugging window in Phase 8).

SF-7.1 ships **only the schema + DAOs + Hilt module + DAO tests**. No business logic, no behaviour change, no UI. The next four SFs depend on this contract being stable: SF-7.2 plugs `RoomAliasRepository` into the existing `AliasRepository` interface (replacing `EmptyAliasRepository`); SF-7.3 calls `aliasRepository.learn(...)` from the alias-learning subflow; SF-7.4 calls `appUsageDao.upsert(packageName, now)` from `IntentAppLauncher.launch`; SF-7.5 calls `failedCommandDao.insertAndTrim(...)` from `AssistantCoordinator`'s failure paths.

The three load-bearing invariants this SF locks (per `local-data` skill):
- **Aliases are keyed by `ContactsContract.Contacts.LOOKUP_KEY`**, never raw `_ID` — lookup keys survive contact renames/merges/provider re-indexing. The `Contact.kt` Kdoc already pins this.
- **`failed_commands` is capped at 50** — every insert path goes through `insertAndTrim` (transactional). Cap-at-50 is `local-data` rule 4.
- **`app_usage.upsert` is idempotent + transactional** — the "bump or insert" pattern preserves `openCount` across upserts (a naive `INSERT OR REPLACE` would reset it). `local-data` rule 5's stability bar (don't reshuffle the home grid on every open) depends on this.

Spec source: §7 (alias model + apps favoritas implícitas + failed commands), §9 (config menu reads these tables), §14 step 7. `local-data` skill: Room schema sketch + all five rules. The `InteractionLogEntity` from `local-data` is **deferred to Phase 8** — no caller writes it yet (it's a Phase-4-proactive hook); deferring keeps the schema slim.

---

## Scope

### In scope

- `gradle/libs.versions.toml`: verify Room entries activate (no version bump).
- `app/build.gradle.kts`: add three Room library deps + the KSP `room.schemaLocation` arg.
- `app/schemas/` directory (committed; the generated `1.json` is the source of truth for future migrations).
- `app/src/main/java/com/curro/app/data/local/`:
  - `CurroDatabase.kt` (the `@Database` definition + DAO accessors).
  - Three entities: `ContactAliasEntity`, `AppUsageEntity`, `FailedCommandEntity`.
  - Two enums: `AliasSource` (`LEARNED` / `EXPLICIT` / `SUGGESTED`), `FailureKind` (`INVALID_OUTPUT` / `UNKNOWN_FUNCTION` / `HANDLER_ERROR`).
  - `CurroTypeConverters.kt` (both enums round-trip via `.name` / `.valueOf`).
  - Three DAOs: `ContactAliasDao`, `AppUsageDao`, `FailedCommandDao`.
- `app/src/main/java/com/curro/app/di/DatabaseModule.kt` (Hilt bindings).
- Three Robolectric DAO test classes + `DatabaseModule` smoke test.

### Out of scope

- `RoomAliasRepository` real impl — SF-7.2.
- Alias-learning subflow + `RelationalTerms` + handler wiring — SF-7.3.
- Favourite-apps recency repo + `AppLauncher` bump — SF-7.4.
- `FailedCommandLog` interface + call-site migrations — SF-7.5.
- `InteractionLogEntity` — deferred (Phase 8 onwards; no caller yet).
- Real `Migration` objects — `fallbackToDestructiveMigration` for the prototype; future SF post-prototype.
- Phase-8 config-menu UI consuming these flows — Phase 8.

---

## User Flows

This is an infrastructure SF — no user-visible behaviour change. The flows below describe the **invariants** each DAO method enforces; the next four SFs exercise them.

### Flow 1 — Alias persistence (exercised in SF-7.2 / SF-7.3)

1. SF-7.3's learning subflow calls `aliasRepository.learn("mi hija", lucia, AliasSource.LEARNED)`.
2. `RoomAliasRepository.learn` (SF-7.2) builds a `ContactAliasEntity(alias = "mi hija", lookupKey = lucia.lookupKey, displayName = "Lucía Ruiz", source = LEARNED, createdAtMs = now, lastUsedAtMs = now, useCount = 0)`.
3. `ContactAliasDao.upsert(entity)` writes the row; the unique index on `alias` ensures one row per normalised alias.
4. Later, SF-7.2's `RoomAliasRepository.resolveAlias("mi hija")` calls `dao.findByAlias("mi hija")` → returns the entity; `dao.bumpUsage("mi hija", now)` increments `useCount` and updates `lastUsedAtMs`.
5. SF-7.2's coordinator integration calls `dao.topUsed(10)` to inject the top-10 aliases into FunctionGemma's `PromptContext.knownAliases`.

### Flow 2 — App-usage upsert (exercised in SF-7.4)

1. SF-7.4's `IntentAppLauncher.launch("com.whatsapp")` (after the activity starts) calls `appUsageDao.upsert("com.whatsapp", now)`.
2. The DAO's `@Transaction` wrapper: `bumpExisting("com.whatsapp", now)` returns `0` (row missing) → `insertIfMissing(AppUsageEntity("com.whatsapp", openCount = 1, lastOpenedAtMs = now))`. Subsequent calls: `bumpExisting` returns `1` (rows updated) → no insert. **Pin: never use `INSERT OR REPLACE`** — it would reset `openCount`.
3. SF-7.4's recency repo calls `dao.topByOpenCount(20)` once per 24 h; sorts in Kotlin by `openCount × max(0, 1 − daysSince/30)`; emits to the home grid.

### Flow 3 — Failed-command cap-at-50 (exercised in SF-7.5)

1. SF-7.5's `RoomFailedCommandLog.record(transcript, kind, details)` builds a `FailedCommandEntity(transcript, kind, details, timestampMs = now)`.
2. `FailedCommandDao.insertAndTrim(entity)` (transactional): inserts the row; runs `DELETE FROM failed_commands WHERE id NOT IN (SELECT id FROM failed_commands ORDER BY timestampMs DESC LIMIT 50)`. After 60 inserts, the table holds exactly the 50 newest.
3. Phase-8's UI subscribes to `dao.observeRecent(50)`; Fran sees the rows.

---

## Function-catalog Impact

**No catalog change.** This SF adds no function, changes no `needs_confirmation`, adds no handler. It plumbs the data layer that SF-7.2 will inject into the FunctionGemma `PromptContext.knownAliases` (already-declared field, populated empty since Phase 3).

---

## FSM States Touched

**None.** This SF is purely data-layer infrastructure. The state machine, the coordinator, and the handlers are untouched. (SF-7.2 wires the `AssistantCoordinator.buildContext()` change; SF-7.3 wires the `CallContactHandler` learning path; SF-7.4 wires the launcher tile path; SF-7.5 wires the coordinator's failure paths.)

---

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| _(none)_ | Room is purely local storage | _(N/A)_ | _(N/A)_ |

**No new permissions; no manifest changes.** Room writes to the app's private databases directory (`/data/data/com.curro.app/databases/curro.db`); no `INTERNET` change, no `WRITE_EXTERNAL_STORAGE`. The DAOs run on `Dispatchers.IO` (via `@IoDispatcher`) — no Android permission required.

---

## On-device-model Impact

**No model impact.** SF-7.1 ships data infrastructure only. SF-7.2 is where FunctionGemma's `PromptContext.knownAliases` finally fills (the prompt-builder template already renders the "Alias conocidos: …" line — see `FunctionCallPromptBuilder.contextBlock` line 96–98).

---

## Android Specification

### Files added

```
app/schemas/
    .gitkeep
    com.curro.app.data.local.CurroDatabase/  # generated after first build
        1.json                                # committed manually after first assembleDebug

app/src/main/java/com/curro/app/data/local/
    CurroDatabase.kt
    CurroTypeConverters.kt
    AliasSource.kt
    FailureKind.kt
    ContactAliasEntity.kt
    AppUsageEntity.kt
    FailedCommandEntity.kt
    ContactAliasDao.kt
    AppUsageDao.kt
    FailedCommandDao.kt

app/src/main/java/com/curro/app/di/
    DatabaseModule.kt

app/src/test/java/com/curro/app/data/local/
    ContactAliasDaoTest.kt           # ~10 cases, Robolectric, in-memory Room
    AppUsageDaoTest.kt               # ~8 cases
    FailedCommandDaoTest.kt          # ~10 cases

app/src/test/java/com/curro/app/di/
    DatabaseModuleTest.kt            # ~3 smoke cases
```

### Files modified

```
gradle/libs.versions.toml            # re-flag Room block; no version bump
app/build.gradle.kts                 # add libs.room.runtime/ktx/compiler deps + ksp room.schemaLocation arg
.gitignore                           # add app/schemas/com.curro.app.data.local.CurroDatabase/.gitkeep IF auto-gen subdir is empty pre-build
```

### `gradle/libs.versions.toml` change

The Room block is already pre-declared (lines 64, 123–125):

```toml
# [versions]
room             = "2.6.1"        # Activated in SF-7.1

# [libraries] — Reserved (NOT YET referenced from app/build.gradle.kts)
room-runtime                   = { module = "androidx.room:room-runtime", version.ref = "room" }                   # Activated in SF-7.1
room-ktx                       = { module = "androidx.room:room-ktx", version.ref = "room" }                       # Activated in SF-7.1
room-compiler                  = { module = "androidx.room:room-compiler", version.ref = "room" }                  # Activated in SF-7.1
```

**Action**: keep the version + library entries verbatim; the implementer ONLY rewrites the `# Activated in SF-7.1` comments to `# Activated SF-7.1 — US-045` (or moves them to the "active" section above). **No version change.**

### `app/build.gradle.kts` changes

**A. Add the three Room deps** in the dependencies block, replacing the reserved-comment line:

```kotlin
// In dependencies { ... }, replace this line:
//   // Room         → SF-7.1: implementation(libs.room.runtime), implementation(libs.room.ktx), ksp(libs.room.compiler)
// with:
implementation(libs.room.runtime)
implementation(libs.room.ktx)
ksp(libs.room.compiler)
```

**B. Add the schema-location KSP arg** at the top level (sibling to `android { }`):

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

**Pin: `$projectDir/schemas` resolves to `<repo>/app/schemas/`** — that's where Room writes `com.curro.app.data.local.CurroDatabase/1.json` after the first `assembleDebug`. Commit the generated JSON.

### `CurroDatabase.kt`

```kotlin
package com.curro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The single Room database for Curro (SF-7.1 / US-045).
 *
 * - **v1 entities**: [ContactAliasEntity] (SF-7.2 alias map), [AppUsageEntity]
 *   (SF-7.4 implicit favourites), [FailedCommandEntity] (SF-7.5 fail log).
 * - **Schema export** enabled — every future schema change generates a new JSON
 *   in `app/schemas/com.curro.app.data.local.CurroDatabase/<version>.json` for
 *   migration safety. The schema directory is committed.
 *
 * **Migration policy (prototype only)**: [androidx.room.Room.databaseBuilder] is
 * configured in [com.curro.app.di.DatabaseModule] with
 * `.fallbackToDestructiveMigration()`. This is intentional for the prototype —
 * no users yet, no real data to lose. **Before any public release**, replace
 * the fallback with real [androidx.room.migration.Migration] objects (the
 * schema-export JSON is the prerequisite for that work).
 *
 * **Threading**: every DAO method is `suspend` or `Flow`-returning. Callers
 * dispatch on `@IoDispatcher` — never the main thread. The in-memory Room
 * builder in tests uses `.allowMainThreadQueries()` for simplicity.
 */
@Database(
    entities = [
        ContactAliasEntity::class,
        AppUsageEntity::class,
        FailedCommandEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CurroTypeConverters::class)
abstract class CurroDatabase : RoomDatabase() {
    abstract fun contactAliasDao(): ContactAliasDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun failedCommandDao(): FailedCommandDao
}
```

### Entities

```kotlin
// AliasSource.kt
package com.curro.app.data.local

/**
 * Provenance of a stored [ContactAliasEntity] (spec §7).
 *
 * - [LEARNED]: persisted by the alias-learning subflow (SF-7.3, spec flow 4).
 *   The user said "mi hija", picked Lucía from the candidate list, Curro
 *   wrote the row.
 * - [EXPLICIT]: Fran pre-loaded the alias via the Phase-8 config menu
 *   (SF-8.2). Hand-typed mapping.
 * - [SUGGESTED]: deferred. Future Phase-8 onboarding wizard suggests aliases
 *   based on contact frequency.
 */
enum class AliasSource { LEARNED, EXPLICIT, SUGGESTED }


// FailureKind.kt
package com.curro.app.data.local

/**
 * The three failure paths the SF-3.6 + SF-7.5 flow distinguishes (spec §6 flow 7).
 *
 * - [INVALID_OUTPUT]: FunctionGemma produced JSON that failed the validator's
 *   JSON-Schema check (spec flow 7 — "model output not valid"). No retry.
 * - [UNKNOWN_FUNCTION]: the JSON was valid but the `action` is not in the
 *   current phase's catalog (e.g. user asks for a Fase-2 function in Fase 1).
 * - [HANDLER_ERROR]: the dispatched handler threw OR returned
 *   [com.curro.app.domain.handler.HandlerResult.Failed].
 *
 * Stored in [FailedCommandEntity.kind]; surfaced to Fran in the Phase-8 fail-log UI.
 */
enum class FailureKind { INVALID_OUTPUT, UNKNOWN_FUNCTION, HANDLER_ERROR }


// ContactAliasEntity.kt
package com.curro.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A learned or pre-loaded mapping from a spoken alias ("mi hija") to a
 * specific contact identified by [ContactsContract.Contacts.LOOKUP_KEY] (SF-7.2).
 *
 * - [alias]: **normalised** form (`curroNormalize()` applied — lowercase,
 *   accent-stripped, trimmed). The unique index on this column means there is
 *   at most one row per normalised phrase; SF-7.3's re-learn flow exploits
 *   [androidx.room.OnConflictStrategy.REPLACE] in [ContactAliasDao.upsert] to
 *   overwrite a stale row when the user re-teaches the alias.
 * - [lookupKey]: `ContactsContract.Contacts.LOOKUP_KEY` — survives contact
 *   renames, merges, and provider re-indexing (`local-data` rule 1). The
 *   `_ID` is intentionally NOT stored — it can change on a re-import.
 * - [displayName]: cached at learning time so the config menu (SF-8.2) can
 *   render the alias list without a ContactsProvider round-trip per row, and
 *   the SF-7.3 re-learn prompt ("Antes me dijiste que mi hija era %s …") has
 *   the old name without re-resolving.
 * - [source]: [AliasSource] — for the Phase-8 UI to colour-code or filter.
 * - [useCount] + [lastUsedAtMs]: bumped on every successful
 *   [ContactAliasDao.bumpUsage]; drives the prompt-context top-N ordering
 *   (SF-7.2 injects the top-10 by `useCount DESC, lastUsedAtMs DESC`).
 *
 * **Privacy**: aliases stay on the device. Spec §12 — never serialised to
 * telemetry. The Phase-8 config menu reads them; no other surface.
 */
@Entity(
    tableName = "contact_aliases",
    indices = [Index(value = ["alias"], unique = true)],
)
data class ContactAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val lookupKey: String,
    val displayName: String,
    val source: AliasSource,
    val createdAtMs: Long,
    val lastUsedAtMs: Long,
    val useCount: Int = 0,
)


// AppUsageEntity.kt
package com.curro.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-package open-count + last-opened-at (SF-7.4 implicit favourites).
 *
 * - [packageName] IS the primary key (no `id` autoGenerate). The
 *   bump-or-insert pattern in [AppUsageDao.upsert] (see Kdoc there) preserves
 *   [openCount] across upserts — a naive `INSERT OR REPLACE` would reset it,
 *   which would break the recency-weighted ranking in SF-7.4.
 * - [lastOpenedAtMs] enables the 30-day linear decay in SF-7.4's scoring:
 *   `score = openCount × max(0, 1 − daysSince/30)`.
 *
 * Re-computation cadence is 24 h (SF-7.4); see `local-data` rule 5 — the
 * home grid must not reshuffle on every open ("feels the same every day").
 */
@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey val packageName: String,
    val openCount: Int = 0,
    val lastOpenedAtMs: Long,
)


// FailedCommandEntity.kt
package com.curro.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user utterance Curro couldn't act on (SF-7.5, spec §6 flow 7 + §9 "Logs
 * de comandos fallidos").
 *
 * **Privacy (spec §12)**: [transcript] is PII. **It stays on the device.**
 * The PostHog/Firebase telemetry layer (`TelemetryGuardrail`) emits a
 * `command_failed` event with `kind` + `function_name` only — NEVER the
 * transcript. Fran's Phase-8 config menu UI is the only surface that reads
 * this table.
 *
 * - [kind]: the failure path ([FailureKind.INVALID_OUTPUT] /
 *   [FailureKind.UNKNOWN_FUNCTION] / [FailureKind.HANDLER_ERROR]) — Fran
 *   filters by this in the Phase-8 UI.
 * - [details]: an extra free-form column for diagnostic context (e.g. the
 *   raw model output for [FailureKind.INVALID_OUTPUT]; the
 *   `<action>/<error class>` for [FailureKind.HANDLER_ERROR]). Stays on
 *   device.
 * - [timestampMs]: epoch ms; descending order drives [FailedCommandDao.observeRecent].
 *
 * Capped at 50 (`local-data` rule 4) — every insert call goes through
 * [FailedCommandDao.insertAndTrim], which deletes anything older than the
 * 50 newest in the same transaction.
 */
@Entity(tableName = "failed_commands")
data class FailedCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcript: String,
    val kind: FailureKind,
    val details: String = "",
    val timestampMs: Long,
)
```

### `CurroTypeConverters.kt`

```kotlin
package com.curro.app.data.local

import androidx.room.TypeConverter

/**
 * Single converter registry for [AliasSource] and [FailureKind] (SF-7.1).
 *
 * Both enums round-trip via [Enum.name] (the JVM string form) ↔ `valueOf`.
 * **Pin: the wire form is the enum's source-level name** (`LEARNED`,
 * `INVALID_OUTPUT`, etc.). Renaming an enum constant is a schema migration —
 * future-Phase work must include a Room [androidx.room.migration.Migration]
 * step or a `replace_all` rename.
 *
 * The `static`/object form ensures Room sees these as a single
 * `@TypeConverters` set rather than duplicating per-entity.
 */
object CurroTypeConverters {
    @TypeConverter fun fromAliasSource(value: AliasSource): String = value.name
    @TypeConverter fun toAliasSource(value: String): AliasSource = AliasSource.valueOf(value)

    @TypeConverter fun fromFailureKind(value: FailureKind): String = value.name
    @TypeConverter fun toFailureKind(value: String): FailureKind = FailureKind.valueOf(value)
}
```

### DAOs

```kotlin
// ContactAliasDao.kt
package com.curro.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Read/write API for [ContactAliasEntity] (SF-7.1; SF-7.2 + SF-7.3 + SF-8.2 callers).
 *
 * Ordering: [observeAll] and [topUsed] order by `useCount DESC, lastUsedAtMs DESC`
 * so the prompt-context injection (SF-7.2) picks the most-used recently-used
 * aliases first; ties broken by recency.
 *
 * Uniqueness: the entity's unique index on `alias` means [upsert] with the
 * `OnConflictStrategy.REPLACE` strategy overwrites a stale row when SF-7.3's
 * re-learn flow re-teaches "mi hija → <new contact>". The old `id`,
 * `createdAtMs`, `useCount`, `lastUsedAtMs` are reset to the new entity's
 * fields — pin: `useCount` resets to 0 on a re-learn (intentional —
 * statistics shouldn't survive a "Curro was wrong about who that is").
 */
@Dao
interface ContactAliasDao {
    @Query("SELECT * FROM contact_aliases ORDER BY useCount DESC, lastUsedAtMs DESC")
    fun observeAll(): Flow<List<ContactAliasEntity>>

    @Query("SELECT * FROM contact_aliases ORDER BY useCount DESC, lastUsedAtMs DESC LIMIT :limit")
    suspend fun topUsed(limit: Int): List<ContactAliasEntity>

    @Query("SELECT * FROM contact_aliases WHERE alias = :alias LIMIT 1")
    suspend fun findByAlias(alias: String): ContactAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ContactAliasEntity)

    @Query("UPDATE contact_aliases SET lastUsedAtMs = :now, useCount = useCount + 1 WHERE alias = :alias")
    suspend fun bumpUsage(alias: String, now: Long)

    @Query("DELETE FROM contact_aliases WHERE alias = :alias")
    suspend fun delete(alias: String)

    @Query("DELETE FROM contact_aliases")
    suspend fun deleteAll()
}


// AppUsageDao.kt
package com.curro.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Read/write API for [AppUsageEntity] (SF-7.1; SF-7.4 caller).
 *
 * **The upsert invariant (load-bearing)**: a naive `INSERT OR REPLACE` would
 * reset `openCount` to 1 on every call — wrong. Instead, [upsert] is a
 * `@Transaction` that:
 *  1. Calls [bumpExisting] (an `UPDATE` that increments `openCount` and sets
 *     `lastOpenedAtMs`); the return value is the number of rows affected.
 *  2. If `bumpExisting` returned 0 (no row existed), [insertIfMissing] runs
 *     with `IGNORE` strategy (defensive — concurrent inserts can't double-write).
 *
 * The transaction makes the bump-or-insert atomic; two concurrent
 * [upsert]s for the same package never lose an open count.
 */
@Dao
abstract class AppUsageDao {
    @Query("SELECT * FROM app_usage ORDER BY openCount DESC LIMIT :limit")
    abstract suspend fun topByOpenCount(limit: Int = 20): List<AppUsageEntity>

    @Query("SELECT * FROM app_usage ORDER BY openCount DESC LIMIT :limit")
    abstract fun observeTopByOpenCount(limit: Int = 20): Flow<List<AppUsageEntity>>

    @Query(
        """
        UPDATE app_usage
        SET openCount = openCount + 1, lastOpenedAtMs = :now
        WHERE packageName = :packageName
        """,
    )
    abstract suspend fun bumpExisting(packageName: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(entity: AppUsageEntity)

    /**
     * Atomic bump-or-insert. See class Kdoc for the invariant; do not bypass.
     */
    @Transaction
    open suspend fun upsert(packageName: String, now: Long) {
        if (bumpExisting(packageName, now) == 0) {
            insertIfMissing(AppUsageEntity(packageName = packageName, openCount = 1, lastOpenedAtMs = now))
        }
    }

    @Query("DELETE FROM app_usage")
    abstract suspend fun deleteAll()
}


// FailedCommandDao.kt
package com.curro.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Read/write API for [FailedCommandEntity] (SF-7.1; SF-7.5 + SF-8.x callers).
 *
 * **The cap-at-50 invariant (`local-data` rule 4)**: [insertAndTrim] is the
 * only public write path. It inserts the row and then deletes anything
 * outside the 50 newest in the same transaction; the table size never
 * exceeds 50.
 *
 * Phase-8's failed-commands UI subscribes to [observeRecent].
 *
 * Privacy: see [FailedCommandEntity] Kdoc — the transcript stays here, never
 * on the telemetry wire.
 */
@Dao
abstract class FailedCommandDao {
    @Query("SELECT * FROM failed_commands ORDER BY timestampMs DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int = 50): Flow<List<FailedCommandEntity>>

    @Query("SELECT COUNT(*) FROM failed_commands")
    abstract suspend fun count(): Int

    @Insert
    abstract suspend fun insert(entity: FailedCommandEntity): Long

    @Query(
        """
        DELETE FROM failed_commands
        WHERE id NOT IN (
            SELECT id FROM failed_commands
            ORDER BY timestampMs DESC
            LIMIT 50
        )
        """,
    )
    abstract suspend fun trimToFifty()

    /** Atomic insert + trim. See class Kdoc; do not bypass. */
    @Transaction
    open suspend fun insertAndTrim(entity: FailedCommandEntity) {
        insert(entity)
        trimToFifty()
    }

    @Query("DELETE FROM failed_commands")
    abstract suspend fun deleteAll()
}
```

### `DatabaseModule.kt`

```kotlin
package com.curro.app.di

import android.content.Context
import androidx.room.Room
import com.curro.app.data.local.AppUsageDao
import com.curro.app.data.local.ContactAliasDao
import com.curro.app.data.local.CurroDatabase
import com.curro.app.data.local.FailedCommandDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the Room layer (SF-7.1 / US-045).
 *
 * - [CurroDatabase] is `@Singleton` — single connection-pool per process.
 * - The DAOs are NOT `@Singleton` themselves (Room's generated DAOs are stateless;
 *   the singleton lifetime lives on the database).
 *
 * **Migration policy (prototype only)**: `.fallbackToDestructiveMigration()`
 * means a schema-version bump wipes the database on next launch. This is
 * intentional for the prototype — no users yet. **Before any public release**,
 * replace with real [androidx.room.migration.Migration] objects. The schema
 * export at `app/schemas/com.curro.app.data.local.CurroDatabase/<v>.json` is
 * the prerequisite for that work.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideCurroDatabase(@ApplicationContext context: Context): CurroDatabase =
        Room.databaseBuilder(context, CurroDatabase::class.java, "curro.db")
            // TODO(post-prototype): replace with real Migration objects before public release.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideContactAliasDao(db: CurroDatabase): ContactAliasDao = db.contactAliasDao()

    @Provides
    fun provideAppUsageDao(db: CurroDatabase): AppUsageDao = db.appUsageDao()

    @Provides
    fun provideFailedCommandDao(db: CurroDatabase): FailedCommandDao = db.failedCommandDao()
}
```

### Navigation Routes

No new routes — uses no UI. (Phase 8's settings menu will navigate to a fail-log screen that reads from these DAOs.)

### Composables by Feature (checklist)

- [ ] _(none — this is a data-layer SF)_

### Material Design Components

_(N/A — no UI in this SF.)_

---

## Acceptance Criteria

### Build & static checks

- [ ] `./gradlew assembleDebug` builds clean.
- [ ] `./gradlew ktlintCheck detektDebug` green.
- [ ] `./gradlew testDebugUnitTest` green (every existing test + the ~31 new DAO tests).
- [ ] `app/schemas/com.curro.app.data.local.CurroDatabase/1.json` exists after the first build and is committed to git.

### `CurroDatabase` shape

- [ ] `@Database(entities = [ContactAliasEntity::class, AppUsageEntity::class, FailedCommandEntity::class], version = 1, exportSchema = true)`.
- [ ] `@TypeConverters(CurroTypeConverters::class)` annotation present.
- [ ] Three abstract DAO accessors: `contactAliasDao()`, `appUsageDao()`, `failedCommandDao()`.
- [ ] `InteractionLogEntity` is deferred (not in v1) — pin in Kdoc.

### Entity shapes

- [ ] `ContactAliasEntity`: `id` autoGenerate; `alias` unique index; `lookupKey`/`displayName`/`source`/`createdAtMs`/`lastUsedAtMs`/`useCount` columns; defaults: `useCount = 0`.
- [ ] `AppUsageEntity`: `packageName` IS the primary key (not autoGenerate); `openCount` (default 0), `lastOpenedAtMs`.
- [ ] `FailedCommandEntity`: `id` autoGenerate; `transcript`/`kind`/`details`/`timestampMs` columns; `details` defaults to empty string.

### DAO behaviour (verified by tests below)

- [ ] `ContactAliasDao.upsert` REPLACEs on duplicate `alias` (verified by inserting two rows with the same `alias`, asserting count == 1 + the second row's `displayName` wins).
- [ ] `ContactAliasDao.findByAlias` returns null when no row matches.
- [ ] `ContactAliasDao.bumpUsage` increments `useCount` AND updates `lastUsedAtMs` in a single statement.
- [ ] `ContactAliasDao.observeAll` emits in `useCount DESC, lastUsedAtMs DESC` order.
- [ ] `ContactAliasDao.topUsed(limit)` caps the result at `limit`.
- [ ] `AppUsageDao.upsert` is idempotent + transactional: first call for a package inserts with `openCount = 1`; subsequent calls bump `openCount` and update `lastOpenedAtMs` WITHOUT resetting.
- [ ] `AppUsageDao.topByOpenCount(limit)` orders by `openCount DESC` and caps at `limit`.
- [ ] `AppUsageDao.observeTopByOpenCount` emits a new value on each `upsert`.
- [ ] `FailedCommandDao.insertAndTrim` is atomic: after 51 inserts, `count() == 50`; after 60 inserts, `count() == 50` and the 50 newest by `timestampMs` survive.
- [ ] `FailedCommandDao.observeRecent(50)` emits in `timestampMs DESC` order.
- [ ] `FailureKind` and `AliasSource` round-trip through the type converters for all three / three variants.

### `DatabaseModule`

- [ ] `provideCurroDatabase` is `@Singleton`; the same instance is returned across two `EntryPoints.get()` (or two `@Inject` sites).
- [ ] DAO providers return the DAO from the singleton database.
- [ ] `.fallbackToDestructiveMigration()` is configured (verified by a Kdoc inspection OR a smoke test that bumps `version` to 2 in a test database and asserts the wipe behaviour — implementer chooses; PM pins: at least a Kdoc note flagging the prototype-only intent).

### Privacy / telemetry

- [ ] **No new telemetry events.** US-049 adds `command_failed` later.
- [ ] **Pin (in `FailedCommandEntity` Kdoc)**: `transcript` is PII (spec §12) — never serialised to telemetry. Verified structurally (no `transcript` prop in `TelemetryGuardrail.ALLOWED_PROPS`) and by SF-7.5's tightening.

### Regression

- [ ] Every Phase-6 + Phase-5 + Phase-4 test still passes.
- [ ] `EmptyAliasRepository` (SF-4.9) still binds to `AliasRepository`; SF-7.2 swaps it (NOT this SF).

---

## Senior-UX & Copy

**No copy in this SF.** (SF-7.3 adds `copy_alias_no_contacts` and `copy_alias_unresolved`; SF-7.1 ships zero new strings.)

The existing `local-data`-driven invariants this SF locks are senior-UX-relevant:
- **The home grid won't reshuffle on every open** (SF-7.4 enforces; SF-7.1 ships the `AppUsageDao` it depends on with the right upsert semantics).
- **The alias map is keyed by `LOOKUP_KEY`** so a contact rename never silently breaks "llama a mi hija" — SF-7.3's re-learn flow handles the stale case with the new `copy_alias_unresolved`.
- **The failed-commands log is bounded** so Fran's Phase-8 review is always 50 rows max — predictable size, fast load.

---

## Performance Considerations

- DAO calls run on `Dispatchers.IO` (callers use `@IoDispatcher`); never on the main thread.
- `@Transaction` on `AppUsageDao.upsert` and `FailedCommandDao.insertAndTrim` keeps the bump-or-insert and the insert-and-trim atomic. SQLite's `BEGIN` / `COMMIT` round-trip is sub-millisecond on a modern device; the transaction overhead is negligible vs the wrong-data risk.
- The unique index on `contact_aliases.alias` is the only index in v1 — keeps writes cheap. Phase-8 may add `(timestampMs DESC)` on `failed_commands` if the UI's `ORDER BY` becomes slow on the 50-row cap (unlikely).
- `fallbackToDestructiveMigration()` is a one-time prototype cost; the schema export keeps the migration door open for post-prototype work.

---

## Testing Requirements

### `ContactAliasDaoTest.kt` (JVM Robolectric, ~10 cases)

Test infra:
```kotlin
@RunWith(AndroidJUnit4::class)
class ContactAliasDaoTest {
    private lateinit var db: CurroDatabase
    private lateinit var dao: ContactAliasDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CurroDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.contactAliasDao()
    }

    @After fun tearDown() { db.close() }

    private fun alias(alias: String, lookupKey: String = "lk-$alias", count: Int = 0) =
        ContactAliasEntity(alias = alias, lookupKey = lookupKey, displayName = "Display $alias",
                          source = AliasSource.LEARNED, createdAtMs = 1L, lastUsedAtMs = 1L, useCount = count)
}
```

Cases:
1. `upsert_findByAlias_roundTrips` — insert one row; `findByAlias` returns it.
2. `upsert_sameAlias_replacesViaOnConflict` — insert "mi hija → lk-A"; insert "mi hija → lk-B" (different displayName); `findByAlias("mi hija")` returns the second; `observeAll().first().size == 1`.
3. `findByAlias_unknown_returnsNull` — `findByAlias("nadie")` → null on empty table.
4. `bumpUsage_incrementsCountAndUpdatesTimestamp` — insert row with `useCount = 3, lastUsedAtMs = 100`; `bumpUsage("mi hija", 999)`; verify `useCount == 4`, `lastUsedAtMs == 999`.
5. `bumpUsage_unknownAlias_isNoOp` — `bumpUsage("inexistente", 999)` on empty table; `observeAll().first().isEmpty()`.
6. `delete_singleAlias_removesIt` — insert two; `delete("mi hija")`; `observeAll().first().size == 1`.
7. `deleteAll_emptiesTable` — insert three; `deleteAll()`; `observeAll().first().isEmpty()`.
8. `observeAll_emitsInUseCountDescOrder` — insert three with counts `1`, `5`, `3` → returned order `5, 3, 1`.
9. `topUsed_limit3_returnsTopThree` — insert five with counts `1, 2, 3, 4, 5` → `topUsed(3)` returns counts `5, 4, 3`.
10. `aliasUniquenessIndex_doesNotThrowOnDuplicate_replacesInstead` — explicit regression for case 2's `OnConflictStrategy.REPLACE` (insert without REPLACE would throw; verify no throw).

### `AppUsageDaoTest.kt` (JVM Robolectric, ~8 cases)

Cases:
1. `upsert_newPackage_insertsWithCountOne` — `upsert("com.whatsapp", 100)`; `topByOpenCount().first().openCount == 1`, `lastOpenedAtMs == 100`.
2. `upsert_existingPackage_bumpsCount` — `upsert("com.whatsapp", 100)` then `upsert("com.whatsapp", 200)`; `openCount == 2`, `lastOpenedAtMs == 200`.
3. `upsert_existingPackage_updatesTimestamp` — covered above; an explicit case asserts the timestamp delta.
4. `upsert_isIdempotentPerInvocation_butCumulative_across` — 5 sequential upserts → `openCount == 5`.
5. `topByOpenCount_returnsDescendingOrder` — insert three packages with counts `2, 5, 3` → returned order `5, 3, 2`.
6. `topByOpenCount_limit5_capsResult` — insert 10 packages → `topByOpenCount(5).size == 5`.
7. `observeTopByOpenCount_emitsOnUpsert` (Turbine) — collect; upsert; assert new value emitted.
8. `deleteAll_emptiesTable` — insert three; `deleteAll()`; `topByOpenCount().isEmpty()`.

### `FailedCommandDaoTest.kt` (JVM Robolectric, ~10 cases)

Cases:
1. `insert_returnsAutoGeneratedId` — `insert(entity)` returns a positive `Long`.
2. `count_reflectsInsertCount` — insert 3 rows; `count() == 3`.
3. `insertAndTrim_keepsAllWhenUnder50` — insert 49; `count() == 49`.
4. `insertAndTrim_capsAt50WhenInserting51st` — insert 51 (each with monotonically-increasing `timestampMs`); `count() == 50`; the oldest is gone.
5. `insertAndTrim_capsAt50WhenInserting60_keepsNewest50` — insert 60; `count() == 50`; verify the 50 with the largest `timestampMs` survived.
6. `observeRecent_emitsDescendingTimestamp` (Turbine) — insert three with `timestampMs = 1, 2, 3`; the first emission's order is `3, 2, 1`.
7. `observeRecent_limit10_returnsTop10` — insert 30; `observeRecent(10).first().size == 10`.
8. `failureKind_roundTripsAllThreeVariants` — insert one row per `INVALID_OUTPUT` / `UNKNOWN_FUNCTION` / `HANDLER_ERROR`; `observeRecent.first()` returns three rows with matching `kind`s.
9. `transcript_storesUtf8WithAccents_andSpecialChars` — insert `transcript = "¡llama a mi hija María!"`; `observeRecent.first().first().transcript == "¡llama a mi hija María!"`.
10. `deleteAll_emptiesTable` — insert three; `deleteAll()`; `count() == 0`.

### `DatabaseModuleTest.kt` (JVM Robolectric, ~3 smoke cases)

Cases:
1. `provideCurroDatabase_returnsNonNull` — invoke `DatabaseModule.provideCurroDatabase(context)`; non-null; `database.openHelper.databaseName == "curro.db"`.
2. `provideContactAliasDao_returnsDao` — provider returns a non-null DAO; downcast to `ContactAliasDao`.
3. `databaseInstance_isSingleton_acrossInjections` — pin: Hilt test runner with `@HiltAndroidTest` (existing `HiltTestRunner`); inject `CurroDatabase` twice via `EntryPointAccessors`; assert reference-equality. (Alternative if the implementer wants to avoid Hilt-test overhead: assert via a plain `@Provides @Singleton` smoke that calling the provider twice WITH the same Hilt component returns the same instance.)

### Real-device verification

- [ ] Install on Redmi 15; first launch creates `databases/curro.db`; `adb shell run-as com.curro.app ls databases/` shows `curro.db` + `curro.db-journal` (or `-wal`/`-shm`).
- [ ] `adb shell run-as com.curro.app sqlite3 databases/curro.db ".schema"` shows three tables + the unique index on `contact_aliases.alias`.
- [ ] Insert a test row manually via SQL: `INSERT INTO contact_aliases (alias, lookupKey, displayName, source, createdAtMs, lastUsedAtMs, useCount) VALUES ('mi hija', 'lk-test', 'Lucía Ruiz', 'LEARNED', 0, 0, 0);` → `SELECT * FROM contact_aliases;` shows the row. (SF-7.2's smoke test will verify the alias actually resolves from a `call_contact` flow.)

---

## Implementation Notes

- **The schema-export step is the first migration safety-net Curro has.** Make sure `app/schemas/` is committed and the `1.json` lands in the next CI build. Future SFs that change the schema MUST bump `version` AND ship a real `Migration`; the `.fallbackToDestructiveMigration()` is the prototype-era safety wheel.
- **The `@Dao` interfaces are `abstract class` where transactions are needed** (`AppUsageDao.upsert`, `FailedCommandDao.insertAndTrim`) because `@Transaction` requires an `open` method. Other DAOs are plain `interface`.
- **Tests use `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`** — that's the Robolectric pattern. Real Hilt-injected production code never touches the main thread.
- **PM Owner has written**: Metadata, Summary, Scope, User Flows, Function-catalog Impact, FSM States Touched, Senior-UX & Copy, Acceptance Criteria.
- **Implementer (android-developer) writes**: code per the file shapes above, tests per the test specs.

---

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial PM draft for the Phase-7 PM batch |
