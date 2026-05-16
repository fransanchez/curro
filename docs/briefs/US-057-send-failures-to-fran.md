# "Compartir fallos con Fran" toggle + anonymized export — US-057 / SF-8.8

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Opt-in anonymised export of unsent `FailedCommandEntity`s via share intent |
| **US ID** | US-057 (master-plan SF-8.8) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

## Summary

The **only** mechanism by which content from spec §12's "stays on the
device" list can leave the device, and only with explicit Fran consent. Wire
the inline "Compartir fallos con Fran" toggle (declared in SF-8.1, inert
until now) AND a new "Enviar fallos a Fran" button inside `FailuresScreen`
(SF-8.6) that, when tapped, anonymises the unsent failures and hands them
to Android's share sheet. After the chooser is shown, the exported entries
are marked `sent = true` so they don't re-export on the next tap.

Anonymisation rules (`local-data` rule 4 + spec §12):
- Phone numbers in the transcript → `[teléfono]`.
- Contact names (first name + display name, case-insensitive, accent-
  insensitive) → `[contacto]`.
- `details` column with a JSON-shape value → `[modelo: <kind>]`.
- Message bodies → never present (only `failed_commands` is exported,
  not `unread_message_cache` or anything else).
- The `kind` and `timestampMs` survive intact.

A 10-case `FailedCommandAnonymiserTest` suite is the privacy-critical
test surface. New `failures_exported` telemetry event with a `count_bucket`
prop only — transcripts NEVER on the wire.

Spec references: `docs/curro-spec-v1.0.md` §9 ("Modo envíame los fallos")
+ §12 ("only thing in §12 that can leave the device, with explicit
consent") + `local-data` rule 6 (anonymisation).

## Scope

- **In Scope**:
  - `FailedCommandEntity.sent: Boolean` column (Room migration v1 → v2 via
    `fallbackToDestructiveMigration` — prototype only).
  - `FailedCommandDao.observeUnsent(50)` + `markSent(ids: List<Long>)`.
  - `FailedCommandLog.observeUnsent` + `markSent` in the interface +
    `RoomFailedCommandLog` impl.
  - `sendFailuresEnabled` flow setter (the read flow landed in SF-8.1).
  - `FailedCommandAnonymiser` (the privacy-critical class).
  - `FailedCommandExporter` (the formatting + mark-sent orchestrator).
  - `SendFailuresButton` inside `FailuresScreen` (SF-8.6 → modified).
  - `ConfigViewModel.onEvent(ToggleChanged for send-failures)` wired
    behaviourally.
  - `LauncherSideEffect.ShareText(subject, body)` — the new side effect
    that fires the share intent (reuses the `LauncherSideEffectBus` introduced
    in SF-8.7).
  - 6 new strings.
  - 1 new telemetry event (`failures_exported` with `count_bucket` prop).
- **Out of Scope**:
  - Any content beyond the failed-commands log (the toggle is specific to
    failures; future "send X to Fran" toggles would be separate SFs).
  - Email-channel pre-selection or any direct-send affordance — Android's
    `ACTION_SEND` chooser is the channel.
  - Encryption / signing of the export body (it's user-readable plain
    text; that's the point — Fran reads it).
  - A "preview before sending" screen — out of scope; the share chooser
    surfaces the body anyway.

## User Flows

### Flow 1: Fran turns on sharing for the first time

1. Fran opens config → flips "Compartir fallos con Fran" toggle ON.
2. `ConfigViewModel.onEvent(ToggleChanged for send-failures section)` →
   `settingsRepo.setSendFailuresEnabled(true)`.
3. No permission request (the share intent does not need one).
4. The toggle visually stays ON.

### Flow 2: Fran exports failures via the share sheet

1. Fran navigates to "Lo que Curro no ha entendido" (SF-8.6).
2. With the toggle ON, the `SendFailuresButton` is visible above "Borrar
   log".
3. Fran taps "Enviar fallos a Fran".
4. `FailuresViewModel.onEvent(RequestExport)` → `viewModelScope.launch`:
   - `result = exporter.exportUnsent()` → returns `ExportResult(body, ids)`.
   - If `ids.isEmpty()`: publish `LauncherSideEffect.ShowToast(R.string.copy_config_share_failures_empty_toast)`.
   - Else: publish `LauncherSideEffect.ShareText(subject = stringResource(R.string.copy_config_share_failures_subject), body = result.body)`; THEN call `failedLog.markSent(result.ids)`.
5. The launcher screen catches the side effect → fires
   `Intent.createChooser(Intent(ACTION_SEND).setType("text/plain").putExtra(EXTRA_SUBJECT, subject).putExtra(EXTRA_TEXT, body), null)`.
6. Android's share chooser appears. Fran picks WhatsApp / Gmail / wherever.
7. He sends the body to himself.
8. Telemetry event: `failures_exported(count_bucket = "1-5")`.

### Flow 3: Fran exports again — no unsent entries

1. After Flow 2, all 12 prior failures are marked `sent = true`.
2. Fran taps "Enviar fallos a Fran" again.
3. `exporter.exportUnsent()` returns `ExportResult(body = "", ids = emptyList())`.
4. VM publishes `ShowToast(R.string.copy_config_share_failures_empty_toast)`
   ("No hay fallos sin enviar.").
5. No share chooser opens; no new telemetry event (or
   `count_bucket = "0"` — pin: emit `0` for the metric, useful baseline).

### Flow 4: Fran turns the toggle off

1. Fran flips the toggle OFF.
2. `settingsRepo.setSendFailuresEnabled(false)`.
3. `FailuresViewModel` (via its own `combine` collector) re-emits with
   `uiState.sendFailuresEnabled = false`; the button disappears.

### Flow 5: A new failure arrives mid-export

1. Fran starts the export (10 unsent entries → `ids = [1..10]`).
2. Before `markSent(ids)` fires, a NEW failure lands (`id = 11`).
3. `markSent([1..10])` is called → only those 10 are marked.
4. The new entry `id = 11` stays `sent = false` → appears on the next
   export. **Pin: this is correct behaviour** — the export captures a
   snapshot; new failures after the snapshot get a future export.

## Function-catalog Impact

No catalog change.

## FSM States Touched

None.

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none) | `ACTION_SEND` does not require a permission | — | — |

No new permissions.

## On-device-model Impact

No model impact.

## Android Specification

### Schema migration

- **MODIFIED** `app/src/main/java/com/curro/app/data/local/FailedCommandEntity.kt`
  — add `val sent: Boolean = false` column.
- **MODIFIED** `app/src/main/java/com/curro/app/data/local/CurroDatabase.kt`
  — bump `version = 1` to `version = 2`. **Pin: no `Migration` class needed**
  because `fallbackToDestructiveMigration` is wired in the existing
  `DatabaseModule`. Document the prod path in the Kdoc: "before any public
  release, write `MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE failed_commands ADD COLUMN sent INTEGER NOT NULL DEFAULT 0") } }` AND register it via `.addMigrations(MIGRATION_1_2)`."

### DAO changes

- **MODIFIED** `app/src/main/java/com/curro/app/data/local/FailedCommandDao.kt`:
  ```kotlin
  @Query("SELECT * FROM failed_commands WHERE sent = 0 ORDER BY timestampMs DESC LIMIT :limit")
  abstract fun observeUnsent(limit: Int = 50): Flow<List<FailedCommandEntity>>

  @Query("UPDATE failed_commands SET sent = 1 WHERE id IN (:ids)")
  abstract suspend fun markSent(ids: List<Long>)
  ```

### Domain extension

- **MODIFIED** `app/src/main/java/com/curro/app/domain/repository/FailedCommandLog.kt`:
  ```kotlin
  fun observeUnsent(limit: Int = 50): Flow<List<FailedCommandEntity>>
  suspend fun markSent(ids: List<Long>)
  ```
- **MODIFIED** `app/src/main/java/com/curro/app/data/local/RoomFailedCommandLog.kt`:
  matching impls delegating to the DAO.

### Anonymiser

- **NEW** `app/src/main/java/com/curro/app/data/local/FailedCommandAnonymiser.kt`:
  ```kotlin
  @Singleton
  class FailedCommandAnonymiser @Inject constructor(
      private val contactsProvider: ContactsProvider,
      @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
  ) {
      /**
       * Anonymises [entry]'s transcript + details. Loads contacts ONCE per call
       * via [contactsProvider.findAll]; if the exporter calls this for many
       * entries in a tight loop, prefer [anonymiseBatch] (caches the contact list).
       */
      suspend fun anonymise(entry: FailedCommandEntity): AnonymisedEntry {
          val contacts = contactsProvider.findAll()
          return anonymiseWithContacts(entry, contacts)
      }

      suspend fun anonymiseBatch(entries: List<FailedCommandEntity>): List<AnonymisedEntry> {
          val contacts = contactsProvider.findAll()  // load once
          return entries.map { anonymiseWithContacts(it, contacts) }
      }

      private fun anonymiseWithContacts(entry: FailedCommandEntity, contacts: List<Contact>): AnonymisedEntry {
          var transcript = entry.transcript
          // (a) Phone numbers FIRST.
          transcript = PHONE_REGEX.replace(transcript) { "[teléfono]" }
          // (b) Contact names (display + first names), case- and accent-insensitive.
          val normalisedTranscript = transcript.curroNormalize()
          contacts.forEach { c ->
              val nameNorm = c.displayName.curroNormalize()
              val firstNameNorm = c.displayName.split(' ').firstOrNull()?.curroNormalize() ?: ""
              if (nameNorm.isNotBlank()) {
                  transcript = replaceWordBoundary(transcript, c.displayName, "[contacto]")
              }
              if (firstNameNorm.isNotBlank() && firstNameNorm != nameNorm) {
                  transcript = replaceWordBoundary(transcript, c.displayName.split(' ').first(), "[contacto]")
              }
          }
          // (c) Details: if it has a JSON shape, replace with [modelo: <kind>].
          val details = if (entry.details.contains('{') && entry.details.contains('}')) {
              "[modelo: ${entry.kind.name.lowercase()}]"
          } else {
              entry.details  // pass-through (already-short labels like "InvalidFunctionCall")
          }
          return AnonymisedEntry(timestampMs = entry.timestampMs, kind = entry.kind, transcript = transcript, details = details, id = entry.id)
      }

      private companion object {
          val PHONE_REGEX = Regex("""\+?\d[\d\s\-()]{6,}""")
      }
  }

  data class AnonymisedEntry(val id: Long, val timestampMs: Long, val kind: FailureKind, val transcript: String, val details: String)
  ```
- The `replaceWordBoundary(text, name, replacement)` helper does a
  case-insensitive, accent-insensitive substring replace at word boundaries.
  **Pin**: implement as a separate top-level function in the file (testable
  in isolation). Use the existing `curroNormalize` from `data/apps/StringNormalization.kt`.

### Exporter

- **NEW** `app/src/main/java/com/curro/app/data/local/FailedCommandExporter.kt`:
  ```kotlin
  @Singleton
  class FailedCommandExporter @Inject constructor(
      private val failedLog: FailedCommandLog,
      private val anonymiser: FailedCommandAnonymiser,
      private val timeProvider: TimeProvider,
      @ApplicationContext private val context: Context,
      @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
  ) {
      suspend fun exportUnsent(): ExportResult = withContext(ioDispatcher) {
          val unsent = failedLog.observeUnsent(50).first()
          if (unsent.isEmpty()) return@withContext ExportResult(body = "", ids = emptyList())
          val anonymised = anonymiser.anonymiseBatch(unsent)
          val dateStr = formatExportDate(timeProvider.now())
          val intro = context.getString(R.string.copy_config_share_failures_intro, dateStr)
          val lines = anonymised.joinToString(separator = "\n") { format(it) }
          ExportResult(body = intro + lines, ids = anonymised.map { it.id })
      }

      private fun format(e: AnonymisedEntry): String {
          val time = formatHHMM(e.timestampMs)
          val tail = if (e.details.isNotBlank()) " — ${e.details}" else ""
          return "[${e.kind.name}] $time — \"${e.transcript}\"$tail"
      }
  }

  data class ExportResult(val body: String, val ids: List<Long>)
  ```

### UI changes

- **NEW** `app/src/main/java/com/curro/app/presentation/config/sections/failures/SendFailuresButton.kt`:
  ```kotlin
  @Composable
  fun SendFailuresButton(unsentCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
      BigPrimaryButton(
          text = stringResource(R.string.copy_config_share_failures_button),
          onClick = onClick,
          enabled = unsentCount > 0,
          modifier = modifier,
      )
  }
  ```
- **MODIFIED** `app/src/main/java/com/curro/app/presentation/config/sections/failures/FailuresScreen.kt`:
  - `FailuresViewModel` adds `combine` of `settingsRepo.sendFailuresEnabled`
    AND `failedLog.observeUnsent()` to expose `sendFailuresEnabled` +
    `unsentCount`.
  - Inside the bottom `Surface`, render `SendFailuresButton(unsentCount =
    uiState.unsentCount, onClick = { viewModel.onEvent(FailuresEvent.RequestExport) })`
    above the "Borrar log" button ONLY when `uiState.sendFailuresEnabled`
    is true.
- **MODIFIED** `app/src/main/java/com/curro/app/presentation/config/ConfigViewModel.kt`:
  - The `onEvent(ToggleChanged)` for the send-failures section now calls
    `viewModelScope.launch { settingsRepo.setSendFailuresEnabled(newValue) }`.
- **MODIFIED** `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt`:
  - Add `data class ShareText(val subject: String, val body: String) : LauncherSideEffect`.
  - Collect from the `LauncherSideEffectBus` (introduced in SF-8.7) and
    merge into `_sideEffects`.
- **MODIFIED** `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`:
  - On `LauncherSideEffect.ShareText`, fire
    `context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body), null))`.
- **MODIFIED** `FailuresViewModel`:
  - Inject `FailedCommandExporter` and the `LauncherSideEffectBus`.
  - `onEvent(FailuresEvent.RequestExport)`:
    ```kotlin
    viewModelScope.launch {
        val result = exporter.exportUnsent()
        if (result.ids.isEmpty()) {
            bus.publish(LauncherSideEffect.ShowToast(R.string.copy_config_share_failures_empty_toast))
            telemetry.emit("failures_exported", mapOf("count_bucket" to "0"))
            return@launch
        }
        bus.publish(LauncherSideEffect.ShareText(subject = context.getString(R.string.copy_config_share_failures_subject), body = result.body))
        failedLog.markSent(result.ids)
        telemetry.emit("failures_exported", mapOf("count_bucket" to bucketize(result.ids.size)))
    }

    private fun bucketize(n: Int): String = when {
        n == 0 -> "0"
        n <= 5 -> "1-5"
        n <= 20 -> "6-20"
        else -> "21+"
    }
    ```

### Hilt Modules

- No new module. `FailedCommandAnonymiser` and `FailedCommandExporter` are
  `@Inject`-constructable.

### Telemetry guardrail

- **MODIFIED** `app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt`
  — add:
  ```kotlin
  "failures_exported" to setOf("count_bucket"),
  ```
  to `ALLOWED_PROPS`. Pin: `count_bucket` value is one of `"0" / "1-5" / "6-20" / "21+"` — short, no PII. Add the corresponding fixture tests.

## Acceptance Criteria

- [ ] **Toggle off by default; button hidden** — verified on a fresh
      install: the `SendFailuresButton` does not appear in `FailuresScreen`
      until the toggle is ON.
- [ ] **Toggle on → button visible** — verified live; the button enables
      only when `unsentCount > 0`.
- [ ] **Export anonymises per the rules** — all 10 anonymiser test cases
      pass.
- [ ] **After successful export, entries marked `sent = true`** — the next
      tap exports 0; the entries remain in the log (visible in
      `FailuresScreen`'s normal observeRecent(50) view, just with `sent =
      true`).
- [ ] **No-unsent toast**: tapping the button with `unsentCount = 0` shows
      `copy_config_share_failures_empty_toast`.
- [ ] **6 new strings** with the right IDs.
- [ ] **Room migration v1 → v2 via fallbackToDestructiveMigration** — on a
      fresh install OR an upgrade from a Phase-7 build, the schema migrates
      cleanly; no crash.
- [ ] **No new permissions, no new manifest entries, no new dependencies.**
- [ ] **1 new DataStore key** (already declared in SF-8.1 — SF-8.8 wires
      the setter caller).
- [ ] **1 new telemetry event** (`failures_exported`) — guardrail tests
      pass; transcripts NEVER on the wire.
- [ ] **Build is green**.

## Design Notes

- The exporter's plain-text format is human-readable. Fran reads it in a
  WhatsApp message or an email. The format is deliberate: kind in brackets
  for scannability, time in HH:MM for context, transcript in quotes for
  obvious boundaries.
- The intro line carries the date so Fran can correlate with what was
  happening that day.
- The `SendFailuresButton` uses `BigPrimaryButton` (≥ 96 dp) for visual
  parity with "Borrar log" — consistency matters more than density here
  even though it's a Fran-only screen.

## Senior-UX & Copy

Fran-only — config-menu density.

**No new spoken (TTS) strings.**

New entries in `app/src/main/res/values/strings.xml` (6 total):

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_share_failures_help` | "Si lo activas, podrás mandar a Fran los fallos para que Curro mejore. Nunca se mandan mensajes, audio ni nombres." | long help — replaces the short SF-8.1 help on the config menu inline row |
| `copy_config_share_failures_button` | "Enviar fallos a Fran" | button label |
| `copy_config_share_failures_subject` | "Fallos de Curro — %1$s" | share-intent subject; %1$s = date |
| `copy_config_share_failures_intro` | "Curro — fallos compartidos por Pepe\nFecha: %1$s\n\n" | body intro |
| `copy_config_share_failures_empty_toast` | "No hay fallos sin enviar." | toast on empty export |
| `copy_config_share_failures_done_toast` | "Listo, %1$d fallos preparados para enviar." | optional toast on successful export (PM: nice-to-have; pin in brief) |

**`brand-design` COPY table**: add "Send failures (Phase 8 — SF-8.8)"
section with 6 rows.

## Performance Considerations

- `contactsProvider.findAll()` called ONCE per export (in
  `anonymiseBatch`), not per entry.
- Anonymisation is O(entries × contacts × avg-transcript-len) — bounded by
  50 entries × ~30 contacts × ~50 chars = 75 k char-ops; trivial.
- The export runs on `IoDispatcher`; no UI freeze.
- The `Intent.createChooser` is the standard pattern; no perf concerns.

## Testing Requirements

- [ ] **`FailedCommandAnonymiser`** — the **privacy-critical** test suite,
      10 cases:
      1. `phoneNumber_inTranscript_replacedWith_telefono_placeholder`.
      2. `contactDisplayName_inTranscript_replacedWith_contacto_placeholder`.
      3. `contactFirstName_inTranscript_replacedWith_contacto_placeholder`.
      4. `caseInsensitiveContactMatch_replacesAllForms`.
      5. `accentInsensitiveContactMatch_replacesLuciaAndLucia`.
      6. `numbersThatAreNotPhones_eg_42_areNOTreplaced`.
      7. `detailsWithJsonShape_replacedWith_modelo_placeholder`.
      8. `detailsWithoutJsonShape_passedThroughUnchanged`.
      9. `kindField_preservedAsIs`.
      10. `timestamp_preservedAsIs`.
      11. `multipleContactMatches_inSameTranscript_allReplaced`.
- [ ] **`FailedCommandExporter`** — 5 cases:
      1. `exportUnsent_zeroEntries_returnsEmptyBody_andEmptyIds`.
      2. `exportUnsent_threeEntries_returnsBodyWith3LinesAnd3Ids`.
      3. `exportUnsent_anonymiserIsCalledForEachEntry`.
      4. `exportUnsent_formatsTimestampAsHHMM`.
      5. `exportUnsent_includesIntroLineWithFormattedDate`.
- [ ] **`FailedCommandLogUnsent`** — Robolectric + in-memory Room, 4 cases:
      1. `observeUnsent_returnsOnlyRowsWith_sent_eq_false`.
      2. `markSent_bulkUpdate_marksAllPassedIds`.
      3. `markSent_emptyList_isNoOp`.
      4. `unsent_thenMarkSent_emitsEmpty`.
- [ ] **`TelemetryGuardrailFailuresExportedTest`** — 5 cases:
      1. `failures_exported_with_count_bucket_allowed`.
      2. `failures_exported_with_transcript_rejected`.
      3. `failures_exported_with_details_rejected`.
      4. `failures_exported_with_unknown_prop_key_rejected`.
      5. `failures_exported_with_long_value_rejected`.
- [ ] **`FailuresViewModelExportTest`** (extends SF-8.6's test, 4 cases):
      1. `onEvent_RequestExport_zeroEntries_publishesShowToast_andEmits_count_0`.
      2. `onEvent_RequestExport_threeEntries_publishesShareText_andCallsMarkSent`.
      3. `onEvent_RequestExport_disablesUntilCompletion` (prevent double-tap
         re-export — pin: use a `MutableStateFlow<Boolean>` for in-flight).
      4. `uiState_sendFailuresEnabled_reflects_settingsRepo_flow`.
- [ ] **Real Redmi 15 smoke**:
      - Turn on the toggle → speak a few unrecognised commands → open
        Failures → "Enviar fallos a Fran" button visible → tap → Android
        share sheet appears with the anonymised body.
      - Send to yourself (e.g. via Gmail). Verify the body:
        - No raw contact names.
        - No raw phone numbers.
        - The `kind` and `timestampMs` survive.
      - Tap again → "No hay fallos sin enviar." toast.
      - Verify via `adb shell sqlite3` (or via reopening `FailuresScreen`
        and observing that the rows still appear in the normal log but
        their export bypasses them now).

## Implementation Notes

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/data/local/FailedCommandAnonymiser.kt`
- `app/src/main/java/com/curro/app/data/local/FailedCommandExporter.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/SendFailuresButton.kt`
- `app/src/test/java/com/curro/app/data/local/FailedCommandAnonymiserTest.kt`
- `app/src/test/java/com/curro/app/data/local/FailedCommandExporterTest.kt`
- `app/src/test/java/com/curro/app/data/local/FailedCommandLogUnsentTest.kt`
- `app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailFailuresExportedTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/data/local/FailedCommandEntity.kt`
  (+1 column `sent`).
- `app/src/main/java/com/curro/app/data/local/CurroDatabase.kt`
  (`version = 2`).
- `app/src/main/java/com/curro/app/data/local/FailedCommandDao.kt` (+2
  methods).
- `app/src/main/java/com/curro/app/domain/repository/FailedCommandLog.kt`
  (+2 methods).
- `app/src/main/java/com/curro/app/data/local/RoomFailedCommandLog.kt`
  (+2 impls).
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/FailuresScreen.kt`
  (render the button conditionally).
- `app/src/main/java/com/curro/app/presentation/config/sections/failures/FailuresViewModel.kt`
  (inject `FailedCommandExporter`, `LauncherSideEffectBus`, telemetry; add
  `RequestExport` event; extend `combine` for `sendFailuresEnabled` +
  `unsentCount`).
- `app/src/main/java/com/curro/app/presentation/config/ConfigViewModel.kt`
  (wire `onEvent(ToggleChanged for send-failures)`).
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt`
  (+1 side effect `ShareText`).
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`
  (fire `Intent.createChooser` on `ShareText`).
- `app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt`
  (+1 event).
- `app/src/main/res/values/strings.xml` (+6 entries).
- `.claude/skills/brand-design/SKILL.md` (+6 rows).
- `app/src/test/java/com/curro/app/presentation/config/sections/failures/FailuresViewModelTest.kt`
  (extend with the export test cases).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.8 Send failures to Fran (anonymised export). |
