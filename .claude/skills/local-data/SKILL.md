---
name: local-data
description: Curro's local persistence — the Room schema for contact aliases, implicit favourite apps, usage times, and the failed-commands log; the DataStore-backed settings (TTS voice/rate/pitch, confidence thresholds, toggles); the alias-learning subflow (spec flow 4); and "reset learning".
triggers:
  - Room
  - database
  - DataStore
  - persistence
  - alias
  - aliases
  - favourite apps
  - favorite apps
  - usage
  - failed commands
  - failed-command log
  - learning
  - learn alias
  - reset learning
  - settings storage
---

# Local Data (aliases, favourites, failed commands, settings, learning)

Everything Curro remembers lives **on the device** — Room for structured/queryable
data, DataStore (Preferences) for simple settings. There is **no backend** and none
of this syncs anywhere (the "send failures to Fran" toggle is the only thing that
can leave, and only opt-in — see `CLAUDE.md` → Privacy & telemetry). Source:
`docs/curro-spec-v1.0.md` §7, §9, flow 4, §12.

## What's stored

| Data | Store | Why |
|---|---|---|
| **Contact aliases** ("mi hija" → contact lookup key) | Room | learned on first use (flow 4) or pre-loaded by Fran; queried on every "call …" |
| **Favourite apps (implicit)** — package + open count + last opened | Room | the most-opened apps get promoted to the launcher home grid |
| **Usage times** — coarse timestamps of interactions | Room | for future proactive features (Fase 4); keep minimal |
| **Failed-commands log** — last ~50: timestamp, transcript, failure kind (`InvalidFunctionCall` / `UnknownFunction(name)` / handler error), whether sent | Room | Fran reviews it in the config menu to see what Curro didn't get |
| **Settings** — TTS voice id / rate / pitch; confidence thresholds (default 0.85 / 0.60); "incoming-call assistant" toggle; "always confirm" toggle; "send failures to Fran" toggle; launcher-favourites override list | DataStore (Preferences) | small key/value config, read often |

> Aliases reference a **contact lookup key** (`ContactsContract.Contacts.LOOKUP_KEY`),
> not a raw contact id or phone number — lookup keys survive contact edits/merges.
> Re-resolve to a current contact at call time; if the alias no longer resolves
> ("Lucía" deleted), tell the user plainly and offer to relearn.

## Room schema (sketch)

```kotlin
@Entity(tableName = "contact_aliases", indices = [Index(value = ["alias"], unique = true)])
data class ContactAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,                 // normalised: lowercase, accents stripped — "mi hija", "el médico"
    val contactLookupKey: String,      // ContactsContract lookup key
    val displayNameAtLearnTime: String,// for showing in the config menu without a contacts query
    val source: AliasSource,           // LEARNED | PRELOADED_BY_FRAN | EDITED
    val createdAt: Long,
)

@Entity(tableName = "app_usage", indices = [Index(value = ["packageName"], unique = true)])
data class AppUsageEntity(
    @PrimaryKey val packageName: String,
    val openCount: Int,
    val lastOpenedAt: Long,
)

@Entity(tableName = "interaction_log")            // coarse usage times (Fase 4 hook) — keep tiny
data class InteractionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val action: String,                // catalog function name; NO params, NO transcript here
)

@Entity(tableName = "failed_commands")
data class FailedCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val transcript: String,            // what STT heard — stays on device unless "send failures" is on
    val kind: FailedCommandKind,       // INVALID_OUTPUT | UNKNOWN_FUNCTION | HANDLER_ERROR
    val detail: String?,               // e.g. the unknown function name, or the HandlerError name
    val sent: Boolean = false,
)
```

DAOs are suspend / `Flow`-returning; one Room `@Database` (`CurroDatabase`) in
`data/local/`, provided by a Hilt `DatabaseModule`. Cap `failed_commands` at ~50
(trim oldest on insert). Type converters for the enums.

Repository interfaces in `domain/repository/`: `AliasRepository`,
`FavoriteAppsRepository`, `FailedCommandLog`, `SettingsRepository` — handlers, the
coordinator, and tests depend on these, never on Room/DataStore directly.

## DataStore (settings)

```kotlin
// data/local/SettingsDataStore.kt
private val Context.dataStore by preferencesDataStore("curro_settings")

object SettingsKeys {
    val TTS_VOICE_ID         = stringPreferencesKey("tts_voice_id")
    val TTS_RATE             = floatPreferencesKey("tts_rate")          // 1.0 = normal; default ~0.85–0.90
    val TTS_PITCH            = floatPreferencesKey("tts_pitch")
    val CONF_EXECUTE_MIN     = floatPreferencesKey("conf_execute_min") // default 0.85
    val CONF_CONFIRM_MIN     = floatPreferencesKey("conf_confirm_min") // default 0.60
    val ALWAYS_CONFIRM       = booleanPreferencesKey("always_confirm")
    val INCOMING_CALL_MODE   = booleanPreferencesKey("incoming_call_mode")  // default false
    val SEND_FAILURES        = booleanPreferencesKey("send_failures")       // default false
}
```

`SettingsRepository` exposes these as a `StateFlow`/`Flow` of a `Settings` data
class; the `ConfidencePolicy`, `TtsClient`, the InCallService registration, and the
config menu all read from it.

## The alias-learning subflow (spec flow 4)

Triggered when a command uses a **relational term** ("mi hija", "el médico", "mi
nieta") that isn't mapped:

1. FunctionGemma returns e.g. `call_contact{contact:"mi hija"}`. The handler looks
   up "mi hija" in `contact_aliases` → not found → enters **learning mode**
   (`processing → confirming`).
2. Curro: "Aún no sé quién es tu hija. ¿Es alguno de estos contactos? Te los leo:
   María García, Carmen Pérez, Lucía Ruiz…" — reads **up to 5**; if there are more,
   offers "dime su nombre". The screen shows a scrollable big-text contact list +
   a "Ninguno de estos" button.
3. User says/taps "Lucía" → match against `ContactsProvider.findByName` results.
4. Persist `ContactAliasEntity(alias = "mi hija", contactLookupKey = …, source = LEARNED)`.
   Curro: "Vale, Lucía Ruiz es tu hija. Apuntado. Llamando ahora." → proceed.
5. Future "mi hija" resolves directly, no prompt.

Rules:
- **Never ask for more than one alias per interaction** — learn one at a time.
- **Never learn an alias mid-call/mid-action when the action came from an ambiguity
  prompt** (the 3-Marías case): do the action now, skip the learning offer (the
  learning offer is *deferred* — flow 3 → flow 4 note).
- "Ninguno de estos" / "no es ninguno" → "Vale, no pasa nada. Dile a Fran que apunte
  quién es tu hija." — push it to Fran's config menu rather than frustrating the
  user. Don't keep asking.
- Aliases are viewable/editable from the config menu: add manually ("mi nieta = María
  Pérez García"), edit, delete.

## Favourite apps → home grid

Each `open_app` (and each tap on a home tile) bumps `AppUsageEntity.openCount` /
`lastOpenedAt`. The launcher home shows the top N (4–6) by a simple score
(recency-weighted count), unless Fran has set an explicit override list in settings.
Keep it stable day-to-day — don't reshuffle the grid on every open; recompute
occasionally (e.g. once a day or on a deliberate "actualizar favoritas"), because
"feels the same every day" matters more than perfect freshness for this user.

## "Reset learning" (config menu)

A single action that clears `contact_aliases`, `app_usage`, `interaction_log`, and
`failed_commands` (and optionally resets settings to defaults — confirm with Fran
first, it's destructive). Useful when something was learned wrong. Surface a clear
"¿Seguro? Esto borra los alias y las favoritas aprendidas." confirmation.

## Privacy

- Transcripts in `failed_commands` and contact data in `contact_aliases` **stay on
  the device** unless Fran turns on "send failures to Fran" — in which case only
  *anonymized* failure logs leave (strip/replace names; never the message bodies,
  never audio). The product telemetry SDKs (Firebase/PostHog) must **never** receive
  any of this — they get event names/properties only (see `CLAUDE.md` → Privacy &
  telemetry).
- Don't put params or transcripts into `interaction_log` — it's just coarse "when
  did they use which function" for future proactive features.

## Testing (see `testing-patterns`)

- DAO tests with an **in-memory Room database**: insert/query aliases (uniqueness on
  `alias`, normalisation), `failed_commands` capped at 50 (oldest trimmed), app-usage
  upsert/increment, "reset learning" clears the right tables.
- `SettingsRepository`: defaults (0.85 / 0.60, rate ~0.85–0.90, all toggles off),
  round-trips, emits on change.
- The alias-learning subflow (with a fake `ContactsProvider` + fake `AliasRepository`):
  unmapped relational term → learning mode → match → persisted → next time resolves
  directly; "ninguno" → defer-to-Fran message, nothing persisted; never asks for two
  aliases at once; doesn't learn after an ambiguity-driven action.
- Favourite-apps scoring is deterministic for a given usage table; the grid doesn't
  reshuffle on a single open.

## Rules

1. **On-device only** — Room + DataStore; nothing syncs; aliases use the contact *lookup key*, re-resolved at use time.
2. **Repositories in `domain/`; Room/DataStore only in `data/local/`** — handlers and tests use the interfaces.
3. **One alias per interaction; never learn mid-ambiguity-action; "ninguno" → defer to Fran** — never loop the user.
4. **Cap the failed-commands log (~50); distinguish the failure kinds** so Fran can tell "I didn't understand" from "that feature isn't built".
5. **Keep the home grid stable** day-to-day — recompute favourites occasionally, not on every open.
6. **No transcripts / contact data in telemetry**, ever; "send failures" is opt-in and anonymized.
