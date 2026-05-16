# Brief — US-047 / SF-7.3: Alias-learning subflow (spec flow 4) + relational-term detection

## Metadata

| Field | Value |
|---|---|
| **Feature** | Spec flow 4 — when the user says "llama a mi hija" and no alias exists, Curro lists up to 5 contacts, learns the user's pick (persisting `ContactAliasEntity(source = LEARNED)`), then places the call. "Ninguna" defers to Fran. Stale-`LOOKUP_KEY` aliases re-learn via `copy_alias_unresolved`. **Never learn during a regular 3-Marías disambig** (`local-data` rule 3 — the most subtle interaction in the spec). |
| **US ID** | US-047 |
| **SF ID** | SF-7.3 |
| **Phase** | 7 — Alias learning & local persistence |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst (Opus) |
| **Implementer** | voice-pipeline-engineer + android-developer (Opus + Sonnet) |
| **Size** | M |
| **Depends on** | US-046 (`RoomAliasRepository`, `findStoredAlias`, the `learn` API), US-043 (SF-6.3 `NeedsContactPick` + `ContactPickerOverlay` reused) |
| **Unblocks** | Phase-7 user-visible win — Curro now actually *learns* |

---

## Summary

Master-plan §Phase-7 Risks (a) calls this "the most subtle interaction in the spec" — three rules every test must enforce:

1. **One alias per interaction** (spec flow 4, design note 1) — the handler never asks two learning questions in the same turn.
2. **Never mid-disambiguation** (`local-data` rule 3) — picking María García from the SF-6.3 picker (because there are three Marías) MUST NOT save it as an alias. The user wasn't teaching Curro who María is; they were just picking one of three Marías for THIS call.
3. **"Ninguna" defers to Fran** (spec flow 4 design note 2) — Curro speaks `copy_alias_defer_to_fran` and returns to idle. No nagging; no second ask.

The implementation extends `CallContactHandler`:

- **Detection**: `RelationalTerms.all: Set<String>` (curated list of ~35 family/role phrases — `"mi hija"`, `"el médico"`, etc., all normalised). After alias lookup misses, the handler checks `if (normalisedQuery in RelationalTerms.all)`. Hit → enter learning mode. Miss → existing Phase-4/Phase-6 `findByName` path.
- **Learning mode**: returns `HandlerResult.NeedsContactPick(prompt = copy_alias_ask, candidates = first 5 contacts, onPick = learningPickCallback)`. The SF-6.3 `ContactPickerOverlay` renders it; the SF-6.3 `AssistantCoordinator.startPickerListening` listens for voice picks.
- **Learning callback**: `null` (Ninguna) → `Spoken(copy_alias_defer_to_fran)`. Non-null → `aliasRepository.learn(rawQuery, picked, LEARNED)` THEN places the call. The TTS speech is the combined existing `copy_alias_saved` ("Vale, mi hija es Lucía Ruiz. Apuntado. Llamando ahora.") — one TTS pass.
- **Stale-`LOOKUP_KEY` re-learn**: when `aliasRepository.resolveAlias(...)` returns empty BUT `aliasRepository.findStoredAlias(...)` returns non-null, the handler enters a re-learn flow with `copy_alias_unresolved` ("Antes me dijiste que mi hija era Lucía, pero ya no la encuentro. ¿Quién es mi hija ahora?"). User picks a new contact → row is REPLACEd (the unique-index `OnConflictStrategy.REPLACE` from SF-7.1's `ContactAliasDao.upsert` does this for free).

The structural invariant the brief pins (and tests enforce): **two distinct code paths in `CallContactHandler`**. The SF-7.3 learning path is the ONLY path that calls `aliasRepository.learn(...)`. The SF-6.3 `buildPickResult` (the regular 3-Marías path, line 96–112 of the current handler) MUST NEVER call `learn`. A test (`disambigPath_userPickValid_placesCallButDoesNotLearn`) asserts this with a `FakeAliasRepository.learnCalls` size check.

Spec source: §7, §6 flow 4 (every line normative), `local-data` rules 1+3, `voice-interaction` "alias learning subflow", `brand-design` Phase-7 COPY rows.

---

## Scope

### In scope

- New `domain/alias/RelationalTerms.kt` — the curated normalised set of relational phrases.
- New `ContactsProvider.findAll(): List<Contact>` — full alphabetical list (uses existing READ_CONTACTS gate).
- Extend `CallContactHandler` with two new branches: learning mode (RelationalTerms hit) and re-learn mode (stale `LOOKUP_KEY`). Centralise the candidate-fetch + the picker-prompt construction in private helpers.
- Two new strings: `copy_alias_no_contacts`, `copy_alias_unresolved`.
- Update `brand-design` COPY table with both new rows.
- Tests: `CallContactHandlerTest` (+11 cases), `AssistantCoordinatorTest` (+3 cases — full-pipeline learning), extend `FakeAliasRepository` to capture `learn` calls (size==0 regression assertions).

### Out of scope

- The Phase-8 config-menu UI that surfaces learned aliases — Phase 8.
- A "reset learning" config affordance — Phase 8.
- Voice-driven onboarding ("Hola, vamos a apuntar a las personas importantes") — explicitly deferred per spec §7 "post-prototipo".
- Multi-alias-per-interaction ("mi hija y mi nieta") — spec design note 1 forbids.
- Self-detection of relational terms via LLM (vs. the curated `RelationalTerms` set) — out; the set is simple, predictable, and cheap. Add terms via PR review.

---

## User Flows

### Flow 1 — First "llama a mi hija" (the happy path of flow 4)

1. State: empty `contact_aliases`; the user has 30 contacts including Lucía Ruiz.
2. User presses mic → `listening` → "llama a mi hija" → STT → `processing`.
3. FunctionGemma sees the prompt with `"- Alias conocidos: ninguno"` (empty Phase-7.2 list). Returns `{call_contact, contact: "mi hija", confidence: 0.88}`.
4. `ConfidencePolicy.decide` → `Execute` (CONDITIONAL + high confidence + no ambiguity yet — SF-6.3's `isAmbiguous` is `false` here because the handler handles disambig itself).
5. `HandlerDispatcher.dispatch` → `CallContactHandler.handle`:
   - `rawQuery = "mi hija"`.
   - `aliases.resolveAlias("mi hija")` → empty (no row).
   - `aliasRepository.findStoredAlias("mi hija")` → null (no stale row either).
   - `"mi hija" in RelationalTerms.all` → true.
   - `enterLearningMode("mi hija")`:
     - `contactsProvider.findAll()` → 30 contacts;
     - take first 5 (alphabetical) → `[Antonio Pérez, Carmen López, Lucía Ruiz, María García, Pepito Sánchez]`;
     - build prompt via `getString(R.string.copy_alias_ask, "mi hija", "Antonio Pérez, Carmen López, Lucía Ruiz, María García, Pepito Sánchez")` + ` ` + `getString(R.string.copy_alias_ask_more)` (because 30 > 5);
     - return `NeedsContactPick(prompt, candidates = first 5, onPick = learningPick("mi hija", _))`.
6. Coordinator's `renderHandlerResult` → `enterConfirmingPicker(call, result)`:
   - `pendingActionRef = PendingAction("call_contact", Kind.PickContact(candidates, onPick = learningPick))`;
   - FSM `Processing → Confirming(prompt)`;
   - TTS the prompt: "Aún no sé quién es mi hija. ¿Es alguno de estos contactos? Te los leo: Antonio Pérez, Carmen López, Lucía Ruiz, María García, Pepito Sánchez. …o dime su nombre.";
   - `startPickerListening` → `sttClient.listenForPicker(candidates)` + 10-s timer.
7. User says "Lucía" → `sttClient.listenForPicker` returns `Pick(luciaContact)`.
8. `AssistantCoordinator.onPickerPicked(luciaContact)` → `kind.onPick(luciaContact)` → `learningPick` lambda runs:
   - `aliasRepository.learn("mi hija", luciaContact, AliasSource.LEARNED)` → DAO row inserted;
   - `placeCallOrFail(luciaContact, "mi hija")` returns `Spoken(copy_calling.format("Lucía Ruiz"))` (call placed via `callController.call`);
   - **the speech is OVERRIDDEN** to `copy_alias_saved.format("mi hija", "Lucía Ruiz")` — the combined one-TTS-pass copy.
   - Final returned `HandlerResult.Spoken("Vale, mi hija es Lucía Ruiz. Apuntado. Llamando ahora.", screen = null)`.
9. Coordinator's `renderPickerOutcome` speaks it → `Executing → Idle` → call is now in progress (Android's call UI takes over).
10. Future "mi hija" → resolves directly via SF-7.2's `RoomAliasRepository.resolveAlias` → no picker. The learning bit is permanent (until a Phase-8 reset).

### Flow 2 — "Ninguna" defers to Fran

1. Same setup as Flow 1, but at step 7 the user says "ninguna" (or "no, ninguno").
2. `sttClient.listenForPicker` returns `None`.
3. `AssistantCoordinator.onPickerNone` → `kind.onPick(null)` → `learningPick(null)`:
   - **Pin: `aliasRepository.learn` is NOT called** (rule: "Ninguna" doesn't teach).
   - Returns `HandlerResult.Spoken(getString(R.string.copy_alias_defer_to_fran, "mi hija"))` = "Vale, no pasa nada. Dile a Fran que apunte quién es mi hija."
4. Coordinator's `renderPickerOutcome` speaks it → `Executing → Idle`.
5. Future "llama a mi hija" → triggers the learning mode AGAIN (no row was saved). The user can pick a contact later when they're ready. **Pin: never loop in the SAME turn** (rule 1 — one ask per interaction).

### Flow 3 — Stale `LOOKUP_KEY` re-learn

1. State: `contact_aliases` has `("mi hija", lookupKey = "lk-removed", displayName = "Lucía Ruiz")`. The user deleted Lucía from contacts.
2. User says "llama a mi hija".
3. Handler:
   - `aliases.resolveAlias("mi hija")` → empty (DAO has the row but `findByLookupKey` returns null per SF-7.2's contract).
   - `aliasRepository.findStoredAlias("mi hija")` → returns `AliasRecord(displayName = "Lucía Ruiz", source = LEARNED)`.
   - Re-learn mode: build prompt via `getString(R.string.copy_alias_unresolved, "mi hija", "Lucía Ruiz")` = "Antes me dijiste que mi hija era Lucía Ruiz, pero ya no la encuentro. ¿Quién es mi hija ahora?";
   - `contactsProvider.findAll()` → take first 5;
   - Return `NeedsContactPick(prompt, candidates = first 5, onPick = learningPick("mi hija", _))`.
4. User picks a new contact → `learn("mi hija", newContact, LEARNED)` runs; the unique-index `OnConflictStrategy.REPLACE` overwrites the stale row → `useCount` reset to 0. Combined `copy_alias_saved` speech + call placed.
5. (Alternative — user says "ninguna") → `copy_alias_defer_to_fran` spoken; stale row stays in the DB (it's harmless: next time the same flow fires; the user can re-teach when ready).

### Flow 4 — Regular 3-Marías disambig (the rule-3 invariant — NOT learning)

1. State: `contact_aliases` is empty. User has three Marías.
2. User says "llama a María" → STT → FunctionGemma → `{call_contact, contact: "María", confidence: 0.94}`.
3. Handler:
   - `aliases.resolveAlias("María")` → empty.
   - `aliasRepository.findStoredAlias("maria")` → null.
   - `"maria" in RelationalTerms.all` → **false** (Marías is a proper name; "mi hija" is the relational form).
   - Existing Phase-6 path: `contacts.findByName("María")` → 3 matches → `buildPickResult` returns `NeedsContactPick(prompt = copy_disambig_ask_three, candidates = 3 Marías, onPick = phase6Pick)`.
4. User picks "María García" → `phase6Pick(maríaGarcía)`:
   - **Pin: `aliasRepository.learn` is NOT called** — this is the regular disambig path, not the learning path.
   - Returns `placeCallOrFail(maríaGarcía, "María")` → call placed → `Spoken(copy_calling)`.
5. **Tested by**: `disambigPath_userPickValid_placesCallButDoesNotLearn` — assert `fakeAliasRepository.learnCalls.size == 0` after the pick.

### Flow 5 — Relational term + zero contacts

1. State: empty `contact_aliases`; empty contact list (rare — a brand-new phone).
2. User says "llama a mi hija".
3. Handler:
   - Alias miss; stale-alias miss.
   - `"mi hija" in RelationalTerms.all` → true.
   - `enterLearningMode("mi hija")` → `contactsProvider.findAll()` → empty.
   - Return `Failed(speech = getString(R.string.copy_alias_no_contacts), reason = ContactNotFound("mi hija"))` = "No tengo contactos para enseñarte. Pídele a Fran que te añada alguno."

---

## Function-catalog Impact

**The catalog itself is unchanged.** `call_contact`'s `params.contact` already accepts an alias or a name (line 199 of spec §5). The handler `CallContactHandler` is the catalog entry's wired backend; SF-7.3 enriches its branching logic without changing its signature, params, or `needs_confirmation`.

**Pin: `needs_confirmation` stays `conditional`** — the policy still decides Execute/Confirm/Clarify before the handler runs. The learning subflow is downstream of the policy.

---

## FSM States Touched

| State | What changes |
|---|---|
| `idle` | unchanged |
| `listening` | unchanged |
| `processing` | unchanged |
| `confirming` | **reused** with the SF-6.3 `Kind.PickContact` shape — no FSM change, no new event. The picker overlay (SF-6.3) renders the candidate list; the SF-6.3 STT pass (`listenForPicker`) recognises picks. **What's new is the `onPick` lambda's body** — it now runs `aliasRepository.learn(...)` in the SF-7.3 path. |
| `executing` | the combined `copy_alias_saved` line is spoken here (after the pick) — same code path as a Phase-6 confirmed call. |
| `error_recovery` | unchanged (no new error path; permission failures route through the existing handler-error machinery). |

**No new `AssistantEvent`, no new `AssistantState` shape change.** SF-7.3 reuses SF-6.3's `NeedsContactPick` + `PendingAction.Kind.PickContact` verbatim — the structural decision in Phase 6 (one shape for "picker waits for a contact choice") was deliberate to make Phase 7 a handler-only change.

---

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `READ_CONTACTS` | `contactsProvider.findAll()` for the candidate list; existing `findByLookupKey` for the re-learn path | _(already requested in Phase 4 SF-4.9 — gate via `ReadContactsPermissionGate.isGranted()` already at line 64 of `CallContactHandler`)_ | Same as Phase 4: `copy_perm_missing_contacts` |
| `CALL_PHONE` | the call is placed at the end of the learning flow (after the pick) | _(already requested in Phase 4 SF-4.10 — gate via `CallPhonePermissionGate.isGranted()` at line 174 of `CallContactHandler`)_ | Same as Phase 4: `copy_perm_missing_calls` |

**No new permissions; no manifest changes.** The new `findAll` method uses the same `READ_CONTACTS` gate as `findByName`.

---

## On-device-model Impact

**No prompt-context change in this SF.** The `knownAliases` field continues to be filled by SF-7.2's `aliasRepository.topUsedSnapshots(10)` integration. After the user picks the contact and `learn` runs, the next turn's prompt INCLUDES the just-learned alias — that's the SF-7.2 wiring kicking in for SF-7.3's persisted data.

**No Gemma 3n.** The picker prompt is composed from string resources (no LLM); the candidate list is from the local contacts provider.

**Latency**: the picker turn's pipeline = STT + FunctionGemma decide (~300–500 ms) + ContactsProvider.findAll (~5–50 ms depending on contact count) + TTS speak prompt + STT picker pass + Room insert (~1 ms) + ContentResolver query for `findByLookupKey` (~5 ms) + TTS speak `copy_alias_saved` + ACTION_CALL. Add ~600 ms total over Phase-6's bare disambig case — acceptable; the user gets a permanent shortcut after this single turn.

---

## Android Specification

### Files added

```
app/src/main/java/com/curro/app/domain/alias/
    RelationalTerms.kt
```

### Files modified

```
app/src/main/java/com/curro/app/handler/
    CallContactHandler.kt              # 2 new branches: learning + re-learn; ~80 LOC added

app/src/main/java/com/curro/app/domain/repository/
    ContactsProvider.kt                # adds findAll(): List<Contact>

app/src/main/java/com/curro/app/data/contacts/
    ContactsContractProvider.kt        # implements findAll()
    ContactsQueryRunner.kt             # widens or adds queryAll()

app/src/main/res/values/
    strings.xml                        # +2 strings (copy_alias_no_contacts, copy_alias_unresolved)

.claude/skills/brand-design/
    SKILL.md                            # +2 rows in the Alias-learning (Phase 7) COPY table

app/src/test/java/com/curro/app/util/
    FakeAliasRepository.kt              # +learnCalls capture (introduced in SF-7.2; extended here)
    FakeContactsProvider.kt             # +findAllResult

app/src/test/java/com/curro/app/handler/
    CallContactHandlerTest.kt           # +11 new cases (Group L)

app/src/test/java/com/curro/app/assistant/
    AssistantCoordinatorTest.kt         # +3 new cases (Group U — full pipeline)
```

### `RelationalTerms.kt`

```kotlin
package com.curro.app.domain.alias

/**
 * The curated set of Spanish relational/role phrases that, when present in a
 * `call_contact` spoken `contact` param AND not yet mapped to an alias, trigger
 * the alias-learning subflow (SF-7.3, spec §7 + flow 4, `local-data` rule 3).
 *
 * Every entry is the **normalised** form: lowercase, accents stripped (via the
 * shared [com.curro.app.data.apps.curroNormalize] helper), single internal
 * spaces. The membership check at the call site is:
 *
 * ```
 * val normalisedQuery = rawQuery.trim().lowercase().curroNormalize()
 * if (normalisedQuery in RelationalTerms.all) { ... }
 * ```
 *
 * **Adding a term**: PR review. Two rules:
 *  1. The term must be a relational/role phrase a user would say in place of a
 *     proper name (NOT a name itself — "Pepito" is NOT a relational term).
 *  2. The term must be in its normalised form (lowercase, no accents).
 *
 * If the user says a term that's not in this set AND there are multiple
 * matches, they enter the regular SF-6.3 disambig (NOT the learning flow) —
 * that's correct: the user isn't teaching Curro who that person is; they
 * just want to pick one of N matches for this call.
 */
object RelationalTerms {
    val all: Set<String> = setOf(
        // Family (26)
        "mi hija", "mi hijo",
        "mi nieta", "mi nieto",
        "mi mujer", "mi marido", "mi esposa", "mi esposo",
        "mi madre", "mi padre", "mama", "papa",
        "mi hermana", "mi hermano",
        "mi suegra", "mi suegro",
        "mi yerno", "mi nuera",
        "mi tia", "mi tio",
        "mi prima", "mi primo",
        "mi sobrina", "mi sobrino",
        "mi cunada", "mi cunado",
        // Roles (12)
        "el medico", "la medico", "la medica",
        "la enfermera", "el enfermero",
        "el cura",
        "el dentista", "la dentista",
        "la farmaceutica", "el farmaceutico",
        "la del banco", "el del banco",
        "el abogado", "la abogada",
    )
}
```

**Pin: the list above is the canonical curated set.** Implementer ships it verbatim. Future terms via PR review per the Kdoc rules.

### `CallContactHandler.kt` changes (the heart of SF-7.3)

The current handler (post-SF-6.3) flow at line 54–84:

```kotlin
override suspend fun handle(call: FunctionCall): HandlerResult {
    val rawQuery = (call.params["contact"] as? String).orEmpty().trim()
    if (rawQuery.isEmpty()) { return failContactNotFound("") }

    if (!readContactsGate.isGranted()) { return failPermContacts() }

    val aliasMatches = aliases.resolveAlias(rawQuery)
    val candidates: List<Contact> =
        if (aliasMatches.isNotEmpty()) aliasMatches else contacts.findByName(rawQuery)

    return when {
        candidates.isEmpty() -> failContactNotFound(rawQuery)
        candidates.size > 1 -> buildPickResult(rawQuery, candidates)   // SF-6.3 disambig
        else -> placeCallOrFail(candidates.first(), rawQuery)
    }
}
```

SF-7.3 inserts a **stale-alias detection step** and a **`RelationalTerms` learning branch** between the alias miss and the `findByName` fallback:

```kotlin
override suspend fun handle(call: FunctionCall): HandlerResult {
    val rawQuery = (call.params["contact"] as? String).orEmpty().trim()
    if (rawQuery.isEmpty()) { return failContactNotFound("") }
    if (!readContactsGate.isGranted()) { return failPermContacts() }

    val normalisedQuery = rawQuery.lowercase().curroNormalize()

    // 1. Alias hit → direct call (SF-7.2 path).
    val aliasMatches = aliases.resolveAlias(rawQuery)
    if (aliasMatches.isNotEmpty()) {
        return placeCallOrFail(aliasMatches.first(), rawQuery)
    }

    // 2. Stale-alias detection (SF-7.3 re-learn) — DAO has the row but the
    //    LOOKUP_KEY no longer resolves. Distinguished from "no alias at all"
    //    so we can speak `copy_alias_unresolved` instead of the generic ask.
    val storedAlias = aliases.findStoredAlias(rawQuery)
    if (storedAlias != null) {
        return enterReLearnMode(rawQuery, storedAlias.displayName)
    }

    // 3. Relational term, no alias yet → enter learning (SF-7.3 happy path).
    if (normalisedQuery in RelationalTerms.all) {
        return enterLearningMode(rawQuery)
    }

    // 4. Phase-4/Phase-6 path: name lookup + disambig.
    val candidates = contacts.findByName(rawQuery)
    return when {
        candidates.isEmpty() -> failContactNotFound(rawQuery)
        candidates.size > 1 -> buildPickResult(rawQuery, candidates)  // SF-6.3 — NOT learning
        else -> placeCallOrFail(candidates.first(), rawQuery)
    }
}

/**
 * SF-7.3 learning mode (spec flow 4 happy path).
 *
 * Reads up to 5 contacts (alphabetical), composes `copy_alias_ask` (+ `copy_alias_ask_more`
 * if more than 5), returns a `NeedsContactPick` whose `onPick` is bound to
 * [learningPickCallback]. **The only code path that calls `aliasRepository.learn`.**
 */
private suspend fun enterLearningMode(rawQuery: String): HandlerResult {
    val all = contacts.findAll()
    if (all.isEmpty()) {
        return HandlerResult.Failed(
            speech = context.getString(R.string.copy_alias_no_contacts),
            reason = CurroError.ContactNotFound(rawQuery),
        )
    }
    val candidates = all.take(LEARNING_CANDIDATE_LIMIT)
    val namesCsv = candidates.joinToString(", ") { it.displayName }
    val askPrompt = context.getString(R.string.copy_alias_ask, rawQuery, namesCsv)
    val prompt = if (all.size > LEARNING_CANDIDATE_LIMIT) {
        "$askPrompt ${context.getString(R.string.copy_alias_ask_more)}"
    } else {
        askPrompt
    }
    return HandlerResult.NeedsContactPick(
        prompt = prompt,
        candidates = candidates,
        onPick = { picked -> learningPickCallback(rawQuery, picked) },
    )
}

/**
 * SF-7.3 re-learn mode (stale-`LOOKUP_KEY` path).
 *
 * Speaks `copy_alias_unresolved` (with the old display name for context).
 * Reuses [learningPickCallback] — a successful pick triggers `aliasRepository.learn(...)`
 * which REPLACEs the stale row via the unique-index `OnConflictStrategy.REPLACE`.
 */
private suspend fun enterReLearnMode(rawQuery: String, oldDisplayName: String): HandlerResult {
    val all = contacts.findAll()
    if (all.isEmpty()) {
        // Edge: alias is stale AND contacts is empty. Same friendly miss.
        return HandlerResult.Failed(
            speech = context.getString(R.string.copy_alias_no_contacts),
            reason = CurroError.ContactNotFound(rawQuery),
        )
    }
    val candidates = all.take(LEARNING_CANDIDATE_LIMIT)
    val prompt = context.getString(R.string.copy_alias_unresolved, rawQuery, oldDisplayName)
    return HandlerResult.NeedsContactPick(
        prompt = prompt,
        candidates = candidates,
        onPick = { picked -> learningPickCallback(rawQuery, picked) },
    )
}

/**
 * SF-7.3 learning callback (shared between learning and re-learn modes).
 *
 * - `picked == null` (user said "ninguna") → defer-to-Fran copy; **no alias saved**.
 * - `picked != null` → `aliasRepository.learn(rawQuery, picked, LEARNED)` THEN place
 *   the call; the speech is the combined `copy_alias_saved` ("Vale, %1$s es %2$s.
 *   Apuntado. Llamando ahora."). One TTS pass.
 */
private suspend fun learningPickCallback(rawQuery: String, picked: Contact?): HandlerResult {
    if (picked == null) {
        return HandlerResult.Spoken(
            speech = context.getString(R.string.copy_alias_defer_to_fran, rawQuery),
        )
    }
    // Pin: learn FIRST, then call. If the call fails (permission), the alias
    // is still saved — the user has taught Curro who "mi hija" is even if the
    // immediate call doesn't go through.
    aliases.learn(rawQuery, picked, AliasSource.LEARNED)
    val callResult = placeCallOrFail(picked, rawQuery)
    return when (callResult) {
        is HandlerResult.Spoken -> HandlerResult.Spoken(
            speech = context.getString(R.string.copy_alias_saved, rawQuery, picked.displayName),
            screen = callResult.screen,
        )
        // Permission failure or other Failed — keep the underlying error copy
        // (it's more informative than the combined success line). The alias
        // is still saved though.
        is HandlerResult.Failed -> callResult
        else -> callResult  // defensive — placeCallOrFail returns only Spoken/Failed
    }
}

private companion object {
    const val DISAMBIG_TWO = 2
    const val DISAMBIG_THREE = 3
    const val LEARNING_CANDIDATE_LIMIT = 5   // spec flow 4 — "lee máximo 5"
}
```

**Pin: the order of the two SF-7.3 detections matters**. Stale-alias detection (step 2) runs BEFORE the `RelationalTerms` check (step 3) so a stale alias for a non-relational term (Phase-8 explicit alias for "Pepito" — a hypothetical Phase-8 case where Fran teaches an alias for a non-relational name) still gets the re-learn prompt rather than falling through to `findByName`.

**Pin: `buildPickResult` (the SF-6.3 disambig path, lines 96–112) is UNCHANGED** — it's the structural fence. Tests verify `aliasRepository.learn` is never called from this branch.

### `ContactsProvider.findAll()` extension

```kotlin
// ContactsProvider.kt
interface ContactsProvider {
    suspend fun findByName(query: String): List<Contact>         // Phase 4
    suspend fun findByLookupKey(lookupKey: String): Contact?     // Phase 7 — SF-7.2

    /**
     * Return every callable contact, alphabetically ordered by `displayName.curroNormalize()`.
     * Used by SF-7.3's learning subflow to pick the first N candidates for the user to choose from.
     *
     * The same `READ_CONTACTS` gate as [findByName] applies; the caller checks
     * [com.curro.app.data.permissions.ReadContactsPermissionGate.isGranted] before
     * invocation. Defensive: catches `SecurityException` and returns `emptyList()`.
     */
    suspend fun findAll(): List<Contact>
}

// ContactsContractProvider.kt — production impl
override suspend fun findAll(): List<Contact> {
    val rows = runner.queryAll()
    if (rows.isEmpty()) return emptyList()
    return rows
        .groupBy { it.lookupKey }
        .map { (key, rowsForKey) ->
            Contact(
                lookupKey = key,
                displayName = rowsForKey.first().displayName,
                phoneNumbers = rowsForKey
                    .mapNotNull { it.phoneNumber?.trim()?.takeIf { p -> p.isNotEmpty() } }
                    .distinct(),
                photoUri = rowsForKey.firstOrNull { it.photoUri != null }?.photoUri,
            )
        }
        .sortedBy { it.displayName.curroNormalize() }
}

// ContactsQueryRunner.kt — add a sister method (recommended, keeps Phase-4 signatures stable)
interface ContactsQueryRunner {
    suspend fun query(): List<ContactRow>                              // Phase 4
    suspend fun queryByLookupKey(lookupKey: String): List<ContactRow>  // SF-7.2
    suspend fun queryAll(): List<ContactRow>                           // SF-7.3
}
```

**Pin: `queryAll` is the same SQL as `query` but with no `LIMIT`/`WHERE` — implementer can implement `queryAll` as `query()` if `query()` already returns all rows.** Verify against the Phase-4 impl. Recommend: explicit separate method for clarity.

### Strings (NEW)

```xml
<!-- app/src/main/res/values/strings.xml — append to the Alias-learning section -->

<!-- SF-7.3 / US-047 — relational term + zero contacts (rare; brand-new phone). -->
<string name="copy_alias_no_contacts">No tengo contactos para enseñarte. Pídele a Fran que te añada alguno.</string>

<!-- SF-7.3 / US-047 — stale LOOKUP_KEY re-learn prompt.
     %1$s = alias ("mi hija"), %2$s = old display name ("Lucía Ruiz"). -->
<string name="copy_alias_unresolved">Antes me dijiste que %1$s era %2$s, pero ya no la encuentro. ¿Quién es %1$s ahora?</string>
```

**Brand-design table update** — append to `.claude/skills/brand-design/SKILL.md`'s "Alias learning (Phase 7)" table:

```markdown
| `copy_alias_no_contacts` | No tengo contactos para enseñarte. Pídele a Fran que te añada alguno. | (NEW — SF-7.3 / spec implied via §7 "alias map" + flow 4 edge case "candidate list empty") |
| `copy_alias_unresolved` | Antes me dijiste que %1$s era %2$s, pero ya no la encuentro. ¿Quién es %1$s ahora? | (NEW — SF-7.3 / spec implied via §7 + `local-data` rule 1 "if the alias no longer resolves, tell the user plainly and offer to relearn") |
```

### Navigation Routes

No new routes. The picker overlay (SF-6.3) renders the candidate list; the SF-7.3 prompts go through the existing `Confirming` state.

### Composables by Feature

_(No new composables. The SF-6.3 `ContactPickerOverlay` renders the candidate list verbatim — the prompt text changes but the layout doesn't.)_

### Material Design Components

_(N/A — no UI in this SF.)_

---

## Acceptance Criteria

### Build & static checks

- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.
- [ ] No new permissions, no new manifest entries, no new dependencies.

### `RelationalTerms`

- [ ] `app/src/main/java/com/curro/app/domain/alias/RelationalTerms.kt` exists; `RelationalTerms.all` is the curated set per the brief.
- [ ] Every entry is normalised (lowercase, no accents, single internal spaces) — verified by a parameterised test `RelationalTermsTest.everyEntryIsNormalised`.

### Handler behaviour (verified by Group L cases below)

- [ ] Relational term + alias miss + no stale alias → returns `NeedsContactPick(prompt = copy_alias_ask format)`.
- [ ] Relational term + 30 contacts → prompt appends `copy_alias_ask_more`.
- [ ] Relational term + 0 contacts → returns `Failed(copy_alias_no_contacts)`.
- [ ] Relational term + user picks candidate → `aliasRepository.learn` invoked with `source = LEARNED` AND call placed AND speech is `copy_alias_saved` format.
- [ ] Relational term + user picks "ninguna" → speech is `copy_alias_defer_to_fran` AND **no alias saved** AND **no call placed**.
- [ ] Stale alias (DAO row exists; lookup-key returns null) → returns `NeedsContactPick(prompt = copy_alias_unresolved format)`.
- [ ] Stale alias + user picks new contact → row REPLACEd (unique index) + call placed.
- [ ] Non-relational query + alias miss → falls through to Phase-4/Phase-6 path; no learning offered.
- [ ] Non-relational query + 3 matches → SF-6.3 disambig path; `aliasRepository.learn` NEVER called even after `onPick` resolves.
- [ ] Existing alias resolves directly (regression — SF-7.2's behaviour); no learning offered.

### Strings

- [ ] `copy_alias_no_contacts` and `copy_alias_unresolved` exist in `strings.xml`.
- [ ] `.claude/skills/brand-design/SKILL.md` Alias-learning table contains both new rows.

### Privacy

- [ ] **No telemetry sends contain the alias text or the contact name.** Pin: the SF-7.5 `command_failed` event whitelist (US-049) doesn't include `transcript` or `details` props; this SF doesn't add new telemetry — it relies on the existing `handler_invoked` event.
- [ ] The `Log.w` lines in the handler (if any added) reference `action` + `error::class.simpleName` only — never the alias or the name.

### Regression

- [ ] Every SF-7.1 + SF-7.2 + Phase-6 + Phase-5 + Phase-4 test still passes.
- [ ] The Phase-6 SF-6.3 disambig (3-Marías) flow STILL WORKS — verified by an unchanged existing test + the new `disambigPath_userPickValid_placesCallButDoesNotLearn` invariant.

---

## Senior-UX & Copy

### New strings

| ID | Spanish | Use |
|---|---|---|
| `copy_alias_no_contacts` | "No tengo contactos para enseñarte. Pídele a Fran que te añada alguno." | Relational term + empty contact list. |
| `copy_alias_unresolved` | "Antes me dijiste que %1$s era %2$s, pero ya no la encuentro. ¿Quién es %1$s ahora?" | Stale-`LOOKUP_KEY` re-learn prompt. |

### Existing strings consumed

- `copy_alias_ask` (Phase-7 brand-design): "Aún no sé quién es %1$s. ¿Es alguno de estos contactos? Te los leo: %2$s."
- `copy_alias_ask_more`: "…o dime su nombre."
- `copy_alias_saved`: "Vale, %1$s es %2$s. Apuntado. Llamando ahora." — the combined ONE TTS pass for the success case.
- `copy_alias_defer_to_fran`: "Vale, no pasa nada. Dile a Fran que apunte quién es %1$s."

### Voice rules

- **One ask per interaction** (spec note 1) — the handler never asks two learning questions in the same turn.
- **"Ninguna" doesn't loop** — speak the defer-to-Fran line and end. Future turn can try again.
- **No fussy animation** — the picker overlay (SF-6.3) is static.
- **Audio + visual together** — the candidate list is visible AND the names are read aloud by TTS.

### What NOT to say (Curro voice rules)

- ❌ "Lo siento, no he sabido quién es mi hija" — no constant apologies.
- ❌ "He guardado en la base de datos local …" — no jargon.
- ❌ Silence on a pick — every state speaks.

---

## Performance Considerations

- `ContactsProvider.findAll()` on 200 contacts: ~50–100 ms (ContentResolver bulk query). One-time cost per learning turn.
- `aliasRepository.learn(...)` Room insert: ~1 ms.
- The candidate list is capped at 5 in the prompt — keeps TTS speech length under ~6 seconds even on the longest names.
- **No new state in the coordinator** — the SF-6.3 `pickerListenerJob` + `confirmationTimeoutJob` slots handle the new prompts. SF-7.3 doesn't add per-turn allocations beyond the prompt string.

---

## Testing Requirements

### `CallContactHandlerTest.kt` (Group L — 11 new cases)

Test infra: existing fakes + `FakeAliasRepository` (extended with `learnCalls` + `findStoredAliasResult`).

```kotlin
class CallContactHandlerTest {
    private val fakeAliasRepository = FakeAliasRepository()
    private val fakeContacts = FakeContactsProvider()
    private val fakeCallController = FakeCallController()
    // ... existing setup ...

    @BeforeEach fun resetFakes() {
        fakeAliasRepository.learnCalls.clear()
        fakeAliasRepository.resolveAliasResult.clear()
        fakeAliasRepository.findStoredAliasResult.clear()
        fakeContacts.findByNameResult.clear()
        fakeContacts.findAllResult = emptyList()
        fakeCallController.lastCalledNumber = null
    }
}
```

Cases (numbered for the implementer):

**L1. `relationalTerm_aliasMiss_returnsNeedsContactPick_with_copy_alias_ask`**
```kotlin
@Test fun relationalTerm_aliasMiss_returnsNeedsContactPick_with_copy_alias_ask() = runTest {
    fakeContacts.findAllResult = listOf(antonio, carmen, lucia, mariag, pepito)  // exactly 5 → no _more suffix
    val result = handler.handle(callOf("call_contact", "contact" to "mi hija"))
    assertThat(result).isInstanceOf(HandlerResult.NeedsContactPick::class.java)
    val pick = result as HandlerResult.NeedsContactPick
    assertThat(pick.prompt).isEqualTo(
        "Aún no sé quién es mi hija. ¿Es alguno de estos contactos? Te los leo: " +
        "Antonio Pérez, Carmen López, Lucía Ruiz, María García, Pepito Sánchez.",
    )
    assertThat(pick.candidates).hasSize(5)
}
```

**L2. `relationalTerm_moreThan5Contacts_promptAppends_copy_alias_ask_more`**
```kotlin
@Test fun relationalTerm_moreThan5Contacts_promptAppends_copy_alias_ask_more() = runTest {
    fakeContacts.findAllResult = (1..30).map { Contact("lk-$it", "Nombre$it", listOf("+34$it"), null) }
    val result = handler.handle(callOf("call_contact", "contact" to "mi hija"))
    assertThat(result).isInstanceOf(HandlerResult.NeedsContactPick::class.java)
    val pick = result as HandlerResult.NeedsContactPick
    assertThat(pick.prompt).endsWith("…o dime su nombre.")
    assertThat(pick.candidates).hasSize(5)
}
```

**L3. `relationalTerm_userPicksCandidate_persistsAlias_speaksCopyAliasSaved_thenPlacesCall`**
```kotlin
@Test fun relationalTerm_userPicksCandidate_persistsAlias_speaksCopyAliasSaved_thenPlacesCall() = runTest {
    fakeContacts.findAllResult = listOf(antonio, lucia)
    fakeContacts.findByNameResult["mi hija"] = emptyList()  // shouldn't be queried but defensive
    val firstResult = handler.handle(callOf("call_contact", "contact" to "mi hija"))
    val pick = firstResult as HandlerResult.NeedsContactPick

    val onPickResult = pick.onPick(lucia)
    assertThat(onPickResult).isInstanceOf(HandlerResult.Spoken::class.java)
    val spoken = onPickResult as HandlerResult.Spoken
    assertThat(spoken.speech).isEqualTo("Vale, mi hija es Lucía Ruiz. Apuntado. Llamando ahora.")

    // Pin assertions:
    assertThat(fakeAliasRepository.learnCalls).hasSize(1)
    assertThat(fakeAliasRepository.learnCalls.first()).isEqualTo(
        LearnInvocation(alias = "mi hija", contactLookupKey = lucia.lookupKey, source = AliasSource.LEARNED),
    )
    assertThat(fakeCallController.lastCalledNumber).isEqualTo(lucia.phoneNumbers.first())
}
```

**L4. `relationalTerm_userPicksNinguna_speaks_copy_alias_defer_to_fran_noAliasSaved_noCallPlaced`**
```kotlin
@Test fun relationalTerm_userPicksNinguna_speaks_copy_alias_defer_to_fran_noAliasSaved_noCallPlaced() = runTest {
    fakeContacts.findAllResult = listOf(antonio, lucia)
    val firstResult = handler.handle(callOf("call_contact", "contact" to "mi hija"))
    val pick = firstResult as HandlerResult.NeedsContactPick

    val onPickResult = pick.onPick(null)  // "Ninguna"
    assertThat(onPickResult).isInstanceOf(HandlerResult.Spoken::class.java)
    val spoken = onPickResult as HandlerResult.Spoken
    assertThat(spoken.speech).isEqualTo("Vale, no pasa nada. Dile a Fran que apunte quién es mi hija.")

    // Pin assertions (the rule-3 enforcement):
    assertThat(fakeAliasRepository.learnCalls).isEmpty()
    assertThat(fakeCallController.lastCalledNumber).isNull()
}
```

**L5. `relationalTerm_zeroContacts_returns_copy_alias_no_contacts_failed`**
```kotlin
@Test fun relationalTerm_zeroContacts_returns_copy_alias_no_contacts_failed() = runTest {
    fakeContacts.findAllResult = emptyList()
    val result = handler.handle(callOf("call_contact", "contact" to "mi hija"))
    assertThat(result).isInstanceOf(HandlerResult.Failed::class.java)
    val failed = result as HandlerResult.Failed
    assertThat(failed.speech).isEqualTo("No tengo contactos para enseñarte. Pídele a Fran que te añada alguno.")
}
```

**L6. `existingAlias_resolvesDirectly_noLearningOffered`** (regression for SF-7.2)
```kotlin
@Test fun existingAlias_resolvesDirectly_noLearningOffered() = runTest {
    fakeAliasRepository.resolveAliasResult["mi hija"] = listOf(lucia)
    val result = handler.handle(callOf("call_contact", "contact" to "mi hija"))
    assertThat(result).isInstanceOf(HandlerResult.Spoken::class.java)
    assertThat(fakeAliasRepository.learnCalls).isEmpty()
    assertThat(fakeCallController.lastCalledNumber).isEqualTo(lucia.phoneNumbers.first())
}
```

**L7. `staleAlias_lookupKeyDoesNotResolve_entersReLearnFlow_with_copy_alias_unresolved`**
```kotlin
@Test fun staleAlias_lookupKeyDoesNotResolve_entersReLearnFlow_with_copy_alias_unresolved() = runTest {
    fakeAliasRepository.resolveAliasResult["mi hija"] = emptyList()  // stale
    fakeAliasRepository.findStoredAliasResult["mi hija"] = AliasRecord("Lucía Ruiz", AliasSource.LEARNED)
    fakeContacts.findAllResult = listOf(antonio, carmen, mariag)
    val result = handler.handle(callOf("call_contact", "contact" to "mi hija"))
    val pick = result as HandlerResult.NeedsContactPick
    assertThat(pick.prompt).isEqualTo(
        "Antes me dijiste que mi hija era Lucía Ruiz, pero ya no la encuentro. ¿Quién es mi hija ahora?",
    )
    assertThat(pick.candidates).hasSize(3)
}
```

**L8. `staleAlias_reLearn_userPicksNewContact_REPLACEsAlias`**
```kotlin
@Test fun staleAlias_reLearn_userPicksNewContact_REPLACEsAlias() = runTest {
    fakeAliasRepository.resolveAliasResult["mi hija"] = emptyList()
    fakeAliasRepository.findStoredAliasResult["mi hija"] = AliasRecord("Lucía Ruiz", AliasSource.LEARNED)
    fakeContacts.findAllResult = listOf(maria, carmen)
    val firstResult = handler.handle(callOf("call_contact", "contact" to "mi hija"))
    val pick = firstResult as HandlerResult.NeedsContactPick

    val onPickResult = pick.onPick(carmen)
    assertThat(fakeAliasRepository.learnCalls.first().contactLookupKey).isEqualTo(carmen.lookupKey)
    // OnConflictStrategy.REPLACE behaviour is verified by the SF-7.1 DAO test
    // `upsert_sameAlias_replacesViaOnConflict`; here we only verify learn() was invoked.
    assertThat((onPickResult as HandlerResult.Spoken).speech).contains("mi hija es Carmen")
}
```

**L9. `nonRelationalQuery_singleMatch_doesNotLearn`**
```kotlin
@Test fun nonRelationalQuery_singleMatch_doesNotLearn() = runTest {
    fakeContacts.findByNameResult["pepito"] = listOf(pepito)
    val result = handler.handle(callOf("call_contact", "contact" to "Pepito"))
    assertThat(result).isInstanceOf(HandlerResult.Spoken::class.java)
    assertThat(fakeAliasRepository.learnCalls).isEmpty()
}
```

**L10. `nonRelationalQuery_threeMatches_returnsNeedsContactPick_butLearningNotInvoked_evenOnPick`**
```kotlin
@Test fun nonRelationalQuery_threeMatches_returnsNeedsContactPick_butLearningNotInvoked_evenOnPick() = runTest {
    fakeContacts.findByNameResult["maria"] = listOf(mariag, marial, mariar)
    val firstResult = handler.handle(callOf("call_contact", "contact" to "María"))
    val pick = firstResult as HandlerResult.NeedsContactPick
    // The prompt is the SF-6.3 disambig, NOT the SF-7.3 learning prompt.
    assertThat(pick.prompt).contains("Tienes 3 marías")  // or copy_disambig_ask_three; verify against the actual format

    val onPickResult = pick.onPick(mariag)
    assertThat(onPickResult).isInstanceOf(HandlerResult.Spoken::class.java)
    // **The rule-3 invariant**:
    assertThat(fakeAliasRepository.learnCalls).isEmpty()
}
```

**L11. `disambigPath_userPickValid_placesCallButDoesNotLearn`** (an extra explicit regression name for case L10's invariant, with a different setup — verifying across two paths).

### `AssistantCoordinatorTest.kt` (Group U — 3 new full-pipeline cases)

**U1. `relationalTerm_aliasLearningEnd2End`** — mock STT → "llama a mi hija"; mock engine → `{call_contact, contact: "mi hija", confidence: 0.88}`; assert FSM enters `Confirming` with `PendingAction.Kind.PickContact` and the prompt matches `copy_alias_ask` format.

**U2. `relationalTerm_userVoicePicksFirstCandidate_aliasPersisted_callPlaced`** — extend U1: `sttClient.listenForPicker` returns `Pick(candidates[0])`; assert FSM ends in `Idle` AND `fakeAliasRepository.learnCalls.size == 1` AND `fakeCallController.lastCalledNumber != null`.

**U3. `relationalTerm_userVoiceNinguna_speaksDeferToFran_noAliasPersisted`** — extend U1: `sttClient.listenForPicker` returns `None`; assert FSM ends in `Idle` AND `fakeAliasRepository.learnCalls.isEmpty()` AND `fakeCallController.lastCalledNumber == null`.

### `FakeAliasRepository` extension

```kotlin
// app/src/test/java/com/curro/app/util/FakeAliasRepository.kt
data class LearnInvocation(
    val alias: String,
    val contactLookupKey: String,
    val source: AliasSource,
)

class FakeAliasRepository : AliasRepository {
    val resolveAliasResult: MutableMap<String, List<Contact>> = mutableMapOf()
    val findStoredAliasResult: MutableMap<String, AliasRecord?> = mutableMapOf()
    var topUsedSnapshotsResult: List<AliasSnapshot> = emptyList()
    var topUsedSnapshotsLimit: Int? = null
    val observeAllStream = MutableStateFlow<List<AliasView>>(emptyList())
    val learnCalls: MutableList<LearnInvocation> = mutableListOf()
    var deleteAllInvoked: Boolean = false

    override suspend fun resolveAlias(alias: String): List<Contact> =
        resolveAliasResult[alias.trim().lowercase()] ?: emptyList()

    override suspend fun learn(alias: String, contact: Contact, source: AliasSource) {
        learnCalls += LearnInvocation(alias.trim().lowercase(), contact.lookupKey, source)
    }

    override fun observeAll(): Flow<List<AliasView>> = observeAllStream

    override suspend fun topUsedSnapshots(limit: Int): List<AliasSnapshot> {
        topUsedSnapshotsLimit = limit
        return topUsedSnapshotsResult.take(limit)
    }

    override suspend fun deleteAll() { deleteAllInvoked = true }

    override suspend fun findStoredAlias(alias: String): AliasRecord? =
        findStoredAliasResult[alias.trim().lowercase()]
}
```

### Real-device verification

- [ ] Wipe `contact_aliases`. Press mic, say "llama a mi hija" → picker overlay shows first 5 contacts + `copy_alias_ask` spoken.
- [ ] Tap "Lucía Ruiz" → "Vale, mi hija es Lucía Ruiz. Apuntado. Llamando ahora." spoken; call screen opens.
- [ ] Hang up. Press mic, "llama a mi hija" → call placed directly (no picker).
- [ ] `adb shell run-as com.curro.app sqlite3 databases/curro.db "SELECT alias, displayName FROM contact_aliases;"` → row shows.
- [ ] Wipe again, "llama a mi hija" → picker, tap "Ninguna de estas" → "Vale, no pasa nada. Dile a Fran que apunte quién es mi hija." → idle. No row inserted.
- [ ] Manually corrupt the alias row's `lookupKey` (e.g. `UPDATE contact_aliases SET lookupKey = 'invalid'`) → "llama a mi hija" → `copy_alias_unresolved` prompt opens with "Lucía Ruiz" as the old name. Pick another contact → row REPLACEd.
- [ ] Three Marías test: "llama a María" → SF-6.3 disambig prompt; pick María García → call placed. Wipe nothing → `adb shell` SELECT confirms NO new alias row was inserted (rule-3 invariant on the device).

---

## Implementation Notes

- **Two distinct learning code paths**: `enterLearningMode` (relational miss) and `enterReLearnMode` (stale alias) — both end at `learningPickCallback`. The SF-6.3 `buildPickResult` (regular disambig) is a separate function that NEVER calls `learn`.
- **TTS pass count**: ONE per pick. The combined `copy_alias_saved` line replaces the underlying `copy_calling` line; the coordinator's `renderPickerOutcome` speaks the combined line once. The handler does NOT compose two TTS calls.
- **`learningPickCallback` is suspend** (it calls suspend functions: `learn`, `placeCallOrFail`).
- **Pin the order: learn BEFORE call**. If the call fails (revoked CALL_PHONE permission), the alias is still saved — Curro has learned who "mi hija" is even if the immediate call can't go through. The user can re-press the mic and try again; the next attempt resolves directly.
- **`FakeAliasRepository.learnCalls`** is the test invariant for the rule-3 enforcement — every disambig-path test asserts `learnCalls.isEmpty()` after a pick.
- **PM Owner has written**: Metadata, Summary, Scope, User Flows (5 of them), Function-catalog Impact, FSM States Touched, Senior-UX & Copy, Acceptance Criteria.
- **Implementer (voice-pipeline-engineer + android-developer) writes**: the code per the file shapes above; the test specs as written; updates `brand-design` skill.

---

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial PM draft for the Phase-7 PM batch |
