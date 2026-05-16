# Brief — US-043 / SF-6.3: Disambiguation flow (3-Marías) + `ContactPickerOverlay`

## Metadata

| Field | Value |
|---|---|
| **Feature** | Multi-match contact disambiguation — picker overlay, voice pick, ordinals, tap pick, second-miss give-up |
| **US ID** | US-043 |
| **SF ID** | SF-6.3 |
| **Phase** | 6 — Confidence-graded confirmation |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst (Opus) |
| **Implementer** | voice-pipeline-engineer (Opus) |
| **Size** | M |
| **Depends on** | US-042 (SF-6.2 — the `Confirming`-state plumbing, 10-s timer, `cancelInFlight` extension) |
| **Unblocks** | Phase 7 (alias learning) — SF-7.3 reuses `ContactPickerOverlay` for the "¿es alguno de estos?" subflow |

---

## Summary

Today `CallContactHandler` returns `HandlerResult.Failed(copy_contact_ambiguous_phase4)`
when "llama a María" matches three Marías. SF-6.3 replaces that dead-end with
Curro's actual disambiguation flow (spec §6 flow 3):

1. `CallContactHandler` returns `HandlerResult.NeedsConfirmation(prompt, onPick)`
   carrying the candidate list — the same shape Phase-6 already uses for the
   regular confirmation path, but with a picker-shaped resolver instead of a
   yes/no lambda.
2. The coordinator routes this into `Confirming` with a `PendingAction` whose
   `Kind` is `PickContact(candidates, onPick)` instead of `YesNo(onConfirm)`.
3. `LauncherPlaceholderScreen` routes the `Confirming` state to one of two
   overlays based on the kind: `ConfirmationOverlay` (SF-6.2) for yes/no,
   `ContactPickerOverlay` (NEW — SF-6.3) for `PickContact`.
4. `ContactPickerOverlay` shows up to 3 candidates as huge `BigCard` rows (full
   name + photo if any) + a "Ninguna" row. For ≥ 4 candidates, top 3 + a "Más"
   row that expands into a `LazyColumn` of the rest (spec §6 flow 3 only
   addresses three; PM decision pins the 4+ behaviour — see "Pin: ≥ 4
   candidates").
5. A specialised STT pass (`listenForPicker(candidates)`) listens for: each
   candidate's first name; the Spanish ordinals "primero / primera / segundo /
   segunda / tercero / tercera"; "ninguna" / "ninguno". A non-matching answer
   triggers ONE re-ask of the options. A second miss triggers
   `copy_disambig_give_up` → `Idle`.
6. **No alias is learned during disambiguation** (spec §7 + flow 4 note + the
   `local-data` skill rule 3). Phase 7 wires alias learning to fire AFTER the
   call ends, on a separate trigger.
7. The Phase-4 single-match guard in `CallContactHandler` (the `candidates.size
   > 1 → Failed(copy_contact_ambiguous_phase4, AmbiguousContact)` branch) is
   removed.

User benefit: when the user's father says "llama a María" and the address book
has three Marías, Curro reads them aloud + shows three huge tap targets +
"Ninguna" — and accepts both voice and tap. If the user mumbles a fourth name,
Curro re-reads the options once; if still no match, "Mejor llámala desde la
agenda, no me aclaro." — honest, no loop.

Spec source: §6 flow 3 (the 3-Marías flow verbatim), §7 ("alias learning is NOT
attempted mid-disambiguation"), §4.3 (ambiguity → always-confirm escalation —
already implemented in `ConfidencePolicy` from SF-6.1).

---

## Scope

### In scope

- Refactor `assistant/PendingAction.kt`:
  - `PendingAction` becomes `(functionName, kind: Kind)`.
  - `sealed interface Kind`:
    - `data class YesNo(val onConfirm: suspend () -> HandlerResult) : Kind`
    - `data class PickContact(val candidates: List<Contact>, val onPick: suspend (Contact?) -> HandlerResult) : Kind`
    - `null` in `onPick`'s argument signals "user said ninguna".
- Modify `handler/CallContactHandler.kt`:
  - Remove the `candidates.size > 1 → Failed(...)` branch.
  - Replace with `NeedsConfirmation(prompt, onConfirm)` where the resolver is a
    `(Contact) -> HandlerResult` — but `HandlerResult.NeedsConfirmation.onConfirm`
    today is `suspend () -> HandlerResult` (no arg). Pin: do NOT change
    `HandlerResult.NeedsConfirmation`'s signature globally; instead, the handler
    returns a NEW result type `HandlerResult.NeedsContactPick(prompt, candidates,
    onPick: suspend (Contact?) -> HandlerResult)`. See "Pin: `HandlerResult`
    shape".
- Add `HandlerResult.NeedsContactPick` (the new case in the sealed interface).
- Modify `AssistantCoordinator.renderHandlerResult`:
  - The existing `NeedsConfirmation` branch (the one SF-6.2 wired to the
    `Confirming` state) stays for yes/no.
  - New `NeedsContactPick` branch: build a `PendingAction(functionName,
    Kind.PickContact(candidates, onPick))`; transition to `Confirming(prompt,
    expiresAtMs, pendingAction)`; speak the prompt.
- New `presentation/assistant/ContactPickerOverlay.kt`:
  - `Confirming` state's body when `pendingAction.kind is Kind.PickContact`.
  - Up to 3 candidate rows + "Ninguna"; ≥ 4 → top 3 + "Más" → expand.
  - `@Preview`s.
- Extend `SttClient` with `fun listenForPicker(candidates: List<Contact>):
  Flow<PickerVoice>`:
  - `PickerVoice.Pick(candidate: Contact)` — matched a candidate.
  - `PickerVoice.None` — user said "ninguna" / "ninguno".
  - `PickerVoice.Other(text: String)` — no match.
  - `PickerVoice.Failed(error: CurroError)`.
  - Vocabulary: each candidate's `displayName.split(' ').first()` (first name);
    each candidate's full `displayName`; the ordinals `primera/primero, segunda/
    segundo, tercera/tercero` keyed by position; "ninguna", "ninguno", "ningún".
- Coordinator changes for the picker:
  - On entering `Confirming` with `PickContact` kind, the coordinator launches
    `listenForPicker(candidates)` + the 10-s timer (the same one SF-6.2 built
    — pin: this is the unified `confirmationTimeoutJob`; the listener job is a
    NEW `pickerListenerJob` distinct from `confirmationListenerJob` to keep the
    state machines for each clean).
  - On first miss (Other), Curro re-speaks `copy_disambig_ask_three` (or
    `copy_disambig_ask_n` for ≥ 4) and re-opens `listenForPicker`. State:
    `disambigMissCount = 1`.
  - On second miss, Curro speaks `copy_disambig_give_up` (feminine) or
    `copy_disambig_give_up_masc` (masculine — pin "use feminine for `call_contact`
    by default; toggle by candidate-name gender via the `brand-design` rule —
    see "Pin: gender of the give-up line").
  - On a successful pick (voice or tap), the coordinator invokes
    `pendingAction.kind.onPick(contact)` which runs
    `CallContactHandler.placeCallOrFail(contact, originalQuery)` → returns
    `HandlerResult.Spoken(copy_calling)`. Transition `Confirming → Executing(speech
    = copy_calling("Pepito"), screen = null)` → `Idle`.
  - On "ninguna" (voice or tap), `pendingAction.kind.onPick(null)` is invoked;
    the handler returns `HandlerResult.Failed(copy_contact_not_found, ContactNotFound)`
    and the coordinator transitions through `Executing(copy_contact_not_found)` →
    `Idle`. Pin: "ninguna" is NOT a give-up — it's a clean cancellation.
- Modify `LauncherPlaceholderScreen.kt`:
  - The `is Confirming` overlay branch routes between `ConfirmationOverlay`
    (kind = `YesNo`) and `ContactPickerOverlay` (kind = `PickContact`).
- Strings: verify the on-disk entries (most exist); add 2 new ordinal strings
  + a "Más" label.

### Out of scope

- **Alias learning** ("Lo apunto: Lucía Ruiz es tu hija") — explicitly out per
  spec §7 + flow 4 note ("no aprender alias dentro de la desambiguación") and
  `local-data` skill rule 3. The Phase 7 SF-7.3 wires this; the trigger is
  "relational-term lookup that found 0 matches", NOT "name lookup that found
  multiple matches".
- The picker UI for `open_app` ambiguity (`copy_app_ambiguous`) — pinned out of
  Phase 6 because `open_app` is not `CONDITIONAL` in the catalog and does not
  flow through the confidence policy. Future SF.
- A "phone number picker" when one contact has multiple numbers — pinned out
  (the handler still uses `phoneNumbers.firstOrNull()`).
- The constrained-vocabulary STT impl details for languages other than Spanish.

### Pin: `HandlerResult` shape

Adding `HandlerResult.NeedsContactPick` instead of generalising
`NeedsConfirmation.onConfirm` to take an argument is a deliberate trade. Two
options were considered:

A. Generalise `NeedsConfirmation` to `NeedsConfirmation<T>(prompt, candidates:
   List<T>?, onConfirm: suspend (T?) -> HandlerResult)`. **Rejected**: generics
   on a sealed interface complicate `when` branching at the coordinator (the
   coordinator would need to know `T` to do anything useful) and Phase 7's
   alias subflow uses a different list shape (full alias candidate row, not a
   `Contact`).

B. Add a parallel case `NeedsContactPick(prompt, candidates: List<Contact>,
   onPick: suspend (Contact?) -> HandlerResult)`. **Selected**. Phase 7 can add
   `NeedsAliasPick(...)` separately when needed.

The cost is a tiny duplication in `renderHandlerResult`. Phase 7 may eventually
refactor; SF-6.3 does not.

### Pin: gender of the give-up line

`brand-design`'s copy table has both `copy_disambig_give_up` ("llámala") and
`copy_disambig_give_up_masc` ("llámalo"). To choose between them, the
coordinator inspects the first matched candidate's `displayName` — if the
first name ends in "o" → masculine, else feminine. This is heuristic but
acceptable for the prototype:

- "María García" → feminine → "Mejor llámala…"
- "Pepe López" → ends in "e" → feminine (incorrect for "Pepe" but the heuristic
  is acceptable; the user's father is far more likely to have multiple Marías
  than multiple Pepes — pinned as good-enough).
- "Pepito Sánchez" → ends in "o" → masculine → "Mejor llámalo…"

For Phase 6, this heuristic ships as-is. Phase 7 may add a contact-side
"gender" field if Fran wants to override. Document the heuristic in the
coordinator's source.

### Pin: ≥ 4 candidates

Spec §6 flow 3 covers 3 candidates verbatim. The current `brand-design` copy
table has `copy_disambig_ask_n` for > 3, suggesting the coordinator already
expected this case. PM decision:

- **3 candidates** → render 3 `BigCard` rows + "Ninguna" → 4 tap targets total.
  TTS uses `copy_disambig_ask_three` (or `_masc`): "Tienes 3 Marías. ¿Cuál de
  ellas?: María García, María López o María Ruiz."
- **2 candidates** → render 2 rows + "Ninguna" → 3 tap targets total. (Same
  three-options STT vocabulary, sans the third candidate's first name.) TTS:
  use a new `copy_disambig_ask_two` (pinned NEW — see "Strings" below) —
  "Tienes 2 Marías. ¿Cuál de ellas?: María García o María Ruiz." A 2-Marías
  case is plausible and the current `_three` template is wrong-arity for 2.
- **≥ 4 candidates** → render top 3 rows (sorted by usage frequency from
  `AppUsageEntity`-like signal — but contacts have no usage table in Phase 6,
  so sort by `displayName` ascending for determinism) + a "Más" row. Tap "Más"
  → expands into a vertical-scrolling `LazyColumn` of the rest (still big
  rows, ≥ 96 dp each). TTS reads only the top 3 ("Tienes 4 coincidencias para
  María. Las primeras son: María García, María López, María Ruiz. ¿Cuál?"
  using `copy_disambig_ask_n`) and the STT pass listens for *all* candidate
  first names (not just the top 3) so the user can voice-pick a non-displayed
  one by saying their first name.

The ≥ 4 case is rare (a senior's address book usually has 0–2 "Marías"). The
4+ design is **defensive**, not the main UX.

---

## User Flows

### Flow 1 — 3 Marías, tap pick

1. User → mic → "Llama a María".
2. STT → "llama a María" → `Processing`.
3. FunctionGemma → `{action: call_contact, params: {contact: "María"},
   confidence: 0.94}` → validator OK.
4. `ConfidencePolicy.decide(...)` is invoked. `isAmbiguous` is still **false**
   here because the policy runs *before* the handler runs and *before* the
   contact lookup. The policy returns `Execute` (high confidence, NO ambiguity
   detected yet).
5. Coordinator dispatches → `CallContactHandler` runs → looks up "María" via
   `aliases.resolveAlias("María")` (empty in Phase 6) →
   `contacts.findByName("María")` → 3 `Contact` matches.
6. Handler returns `HandlerResult.NeedsContactPick(prompt =
   "Tienes 3 Marías. ¿Cuál de ellas?: María García, María López o María
   Ruiz.", candidates = [Maria García, María López, María Ruiz], onPick =
   {contact -> placeCallOrFail(contact, "María") | failed("Ninguna")})`.
7. Coordinator's `renderHandlerResult` hits the new branch → builds
   `PendingAction(functionName = "call_contact", kind = PickContact(candidates,
   onPick))` → transitions to `Confirming(prompt, now + 10_000, pendingAction)`
   → speaks the prompt → starts the `pickerListenerJob` + the
   `confirmationTimeoutJob`.
8. `LauncherPlaceholderScreen` overlay routing sees `kind is PickContact` →
   renders `ContactPickerOverlay`.
9. User taps "María López" on the screen → `LauncherEvent.PickerPicked(contact
   = "María López")` → VM → `coordinator.onPickerPicked(contact)`.
10. Coordinator cancels both jobs; invokes `pendingAction.kind.onPick(contact)`;
    the lambda calls `placeCallOrFail(contact, "María")` → returns
    `Spoken(copy_calling("María López"))`. Transition → `Executing("Llamando
    a María López.", null)` → TTS → `Idle`. `ACTION_CALL` Intent fires; Android
    takes over.

### Flow 2 — 3 Marías, voice pick by first name

1–8 as Flow 1.
9. User says "María García" → STT (constrained pass) → `PickerVoice.Pick(María
   García)` → coordinator's `onPickerPicked(María García)` → same path as
   Flow 1.

### Flow 3 — 3 Marías, voice pick by ordinal ("la primera")

1–8 as Flow 1.
9. User says "la primera" → STT (constrained pass) recognises "primera" →
   `PickerVoice.Pick(candidates[0])` → same path. The coordinator picks the
   first candidate in the order the picker displayed (alphabetical by
   `displayName` for ≥ 4, otherwise insertion order from `findByName`).

### Flow 4 — 3 Marías, voice "ninguna" → no call

1–8 as Flow 1.
9. User says "ninguna" → STT (constrained pass) → `PickerVoice.None` →
   `coordinator.onPickerNone()` → cancels both jobs; invokes
   `pendingAction.kind.onPick(null)`; the handler returns `Failed(copy_contact_not_found,
   ContactNotFound)`. Transition → `Executing(copy_contact_not_found("María"),
   null)` → TTS speaks "No encuentro a María en tus contactos." → `Idle`.
   *Alternatively, pin a softer line:* the handler may return
   `Spoken(copy_cancel_no_call)` for this case ("Vale, no llamo.") — implementer
   pick. Pin: use **`copy_cancel_no_call`** ("Vale, no llamo.") to keep the
   voice consistent with the SF-6.2 NO-button path.

### Flow 5 — first miss, re-ask, then success

1–8 as Flow 1.
9. User says "la cuarta" → STT → `PickerVoice.Other("la cuarta")` (no fourth
   exists; not a candidate name).
10. Coordinator: `disambigMissCount++` → 1. Re-speak the options. Re-launch
    `pickerListenerJob` (the prompt is replayed; the 10-s timer was already
    counting from the FIRST entry into `Confirming` — pin: **the timer is NOT
    reset on a miss**; the same 10 s applies. If timer fires during re-asking,
    the timeout path wins).
11. User says "María García" → success → Flow 1's path #10.

### Flow 6 — second miss → give-up

1–10 as Flow 5.
11. User says "mi prima" → `Other("mi prima")`.
12. Coordinator: `disambigMissCount == 1` already → on second miss, fire
    `coordinator.onPickerGiveUp()` → speak `copy_disambig_give_up` → `Idle`.

### Flow 7 — 10-s silence in the picker

1–8 as Flow 1.
9. (10 s pass; no tap, no recognised voice.) `confirmationTimeoutJob` fires →
   `coordinator.onConfirmationTimedOut()` (same as SF-6.2) → speaks "Cancelo
   entonces." → `Idle`.

### Flow 8 — 4+ Marías

1. User → "Llama a María".
2. Handler returns `NeedsContactPick(prompt = "Tienes 4 coincidencias para
   María. Las primeras son: María García, María López, María Ruiz. ¿Cuál?",
   candidates = [García, López, Ruiz, Sánchez], onPick = …)`.
3. Picker overlay renders 3 rows + "Más" + "Ninguna". TTS speaks the prompt
   above (only top 3 listed). STT vocabulary covers all 4 first names + the
   ordinals + "ninguna".
4. User taps "Más" → the LazyColumn expands to show "María Sánchez" as a 4th
   row. The "Ninguna" row stays at the bottom. (No new TTS — visual-only
   expansion. Pinned: tap-only "Más"; voice users say the full name.)
5. User taps or says "María Sánchez" → handler invokes
   `onPick(María Sánchez)` → call placed.

### Flow 9 — interruption

1–8 as Flow 1.
9. User taps the mic → `cancelInFlight()` (SF-5.3 + SF-6.2 extension) cancels
   `pickerListenerJob`, `confirmationTimeoutJob`, TTS — FSM → `Listening`.

---

## Function-catalog Impact

**No catalog changes.** `call_contact.needsConfirmation` stays `CONDITIONAL`.

The `ConfidencePolicy.isAmbiguous` flag in `PolicyInputs` is not actually used
in the SF-6.3 flow described above (the handler does the resolution AFTER the
policy decided `Execute`). The `isAmbiguous` flag is still a useful semantic
slot for FUTURE scenarios where the coordinator might pre-detect ambiguity
(e.g. from the alias-learning subflow which exposes ambiguity at lookup time).
Pin: keep the flag in the `PolicyInputs` shape; the coordinator passes `false`
for SF-6.3.

Alternatively, the policy could re-run inside the handler's
`NeedsContactPick` return path with `isAmbiguous = true` and the "always-
escalate case #1" check (`isAmbiguous → Confirm`) would fire. This is
**unnecessary**: the handler's choice to return `NeedsContactPick` IS the
ambiguity signal; routing through the policy a second time would be a
no-op (the policy would say `Confirm`, which the coordinator already does).
Pin: skip the second policy call; trust the handler.

---

## FSM States Touched

- **`Confirming`** — its `pendingAction` payload now carries `Kind.YesNo` OR
  `Kind.PickContact`. The state itself is unchanged in shape; only the data
  inside `PendingAction` is richer.
- **`Executing`** — receives `copy_calling`, `copy_contact_not_found`,
  `copy_cancel_no_call`, `copy_disambig_give_up`. No structural change.
- **`Idle / Listening / Processing / ErrorRecovery`** — untouched.

No new `AssistantEvent` cases. SF-6.3 reuses `UserConfirmed` /
`ConfirmationTimedOut` from SF-6.2 with a different `speech` payload. The
coordinator's `onPickerPicked`, `onPickerNone`, `onPickerGiveUp` all emit
`UserConfirmed(speech, screen)` because their net effect is "the FSM left
Confirming via the user's decision" — the FSM doesn't care about the picker
semantics.

---

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none new) | — | — | — |

`ContactsContract` (already wired in SF-4.9) is the source of `Contact` objects
the picker renders. `Coil` is already in the dependencies for the contact
photo. The `ACTION_CALL` Intent is fired through the existing `CallController`.

---

## On-device-model Impact

**No FunctionGemma impact.** The disambiguation happens entirely after the
decision, inside `CallContactHandler`.

**No Gemma 3n.** The prompt is a runtime-formatted string from
`copy_disambig_ask_three` (or `_two` / `_n`). No NL generation.

The constrained picker STT pass reuses `SpeechRecognizer` (offline Spanish, on-
device) — same memory profile as SF-6.2.

---

## Android Specification

### Files added

| Path | Purpose |
|---|---|
| `app/src/main/java/com/curro/app/presentation/assistant/ContactPickerOverlay.kt` | Overlay composable + `@Preview`s. |
| `app/src/main/java/com/curro/app/domain/repository/PickerVoice.kt` | The sealed interface used by `SttClient.listenForPicker`. (Or co-locate in `SttClient.kt`.) |

### Files modified

| Path | Change |
|---|---|
| `app/src/main/java/com/curro/app/assistant/PendingAction.kt` | Refactor to `PendingAction(functionName, kind: Kind)` with `sealed interface Kind { YesNo, PickContact }`. |
| `app/src/main/java/com/curro/app/domain/handler/HandlerResult.kt` | Add `data class NeedsContactPick(val prompt: String, val candidates: List<Contact>, val onPick: suspend (Contact?) -> HandlerResult) : HandlerResult`. |
| `app/src/main/java/com/curro/app/handler/CallContactHandler.kt` | Remove the `candidates.size > 1 → Failed(...)` branch. Replace with the `NeedsContactPick` path. |
| `app/src/main/java/com/curro/app/domain/repository/SttClient.kt` | Add `fun listenForPicker(candidates: List<Contact>): Flow<PickerVoice>`. |
| `app/src/main/java/com/curro/app/data/voice/SpeechRecognizerSttClient.kt` | Implement `listenForPicker` with the candidate-name + ordinal + "ninguna" vocabulary. |
| `app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt` | New `renderHandlerResult` branch for `NeedsContactPick`. New `pickerListenerJob`, `onPickerPicked(contact)`, `onPickerNone()`, `onPickerGiveUp()`. `disambigMissCount: Int` state. Extend `cancelInFlight` to cancel `pickerListenerJob`. |
| `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt` | The `is Confirming` overlay branch now routes between `ConfirmationOverlay` and `ContactPickerOverlay` based on `state.pendingAction.kind`. |
| `app/src/main/java/com/curro/app/presentation/launcher/LauncherEvent.kt` | Add `data class PickerPicked(val contact: Contact) : LauncherEvent`; `data object PickerNone : LauncherEvent`. |
| `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt` | Wire the two new events. |
| `app/src/main/res/values/strings.xml` | Add `copy_disambig_ask_two`, `copy_disambig_more_label`, `copy_disambig_ordinal_first`, `copy_disambig_ordinal_second`, `copy_disambig_ordinal_third`. |

### `PendingAction` refactor

```kotlin
package com.curro.app.assistant

import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.Contact

data class PendingAction(
    val functionName: String,
    val kind: Kind,
) {
    sealed interface Kind {
        /** SF-6.2 — a yes/no confirmation (Phase-1 `call_contact` low-conf case). */
        data class YesNo(
            val onConfirm: suspend () -> HandlerResult,
        ) : Kind

        /**
         * SF-6.3 — multiple matches; show a picker. `onPick(null)` signals "user
         * said ninguna" → the handler returns a friendly cancellation.
         */
        data class PickContact(
            val candidates: List<Contact>,
            val onPick: suspend (Contact?) -> HandlerResult,
        ) : Kind
    }
}
```

### `HandlerResult.NeedsContactPick`

```kotlin
// Append to HandlerResult sealed interface:

/**
 * The handler resolved the user's request to a list of candidate contacts —
 * present a picker.
 *
 * Phase 6 — the coordinator routes this into Confirming with a
 * Kind.PickContact and runs listenForPicker(candidates). The handler does NOT
 * place the call itself; instead, [onPick] is invoked with the user's choice
 * (or null for "ninguna").
 *
 * Phase 7 — the alias-learning subflow may add a similar NeedsAliasPick.
 */
data class NeedsContactPick(
    val prompt: String,
    val candidates: List<Contact>,
    val onPick: suspend (Contact?) -> HandlerResult,
) : HandlerResult
```

### `CallContactHandler` change

```kotlin
// Replace the candidates.size > 1 branch:
candidates.size > 1 -> {
    val prompt = buildDisambigPrompt(rawQuery, candidates)
    HandlerResult.NeedsContactPick(
        prompt = prompt,
        candidates = candidates,
        onPick = { picked ->
            if (picked == null) {
                HandlerResult.Spoken(context.getString(R.string.copy_cancel_no_call))
            } else {
                placeCallOrFail(picked, rawQuery)
            }
        },
    )
}

private fun buildDisambigPrompt(query: String, candidates: List<Contact>): String = when (candidates.size) {
    2 -> context.getString(
        R.string.copy_disambig_ask_two,
        candidates.size,
        query,
        candidates[0].displayName,
        candidates[1].displayName,
    )
    3 -> {
        // Heuristic gender from the query (matches the visible candidates' first names
        // which all collide with the query). "María" → ends in 'a' → feminine.
        val masculine = query.lowercase().endsWith("o")
        val res = if (masculine) R.string.copy_disambig_ask_three_masc else R.string.copy_disambig_ask_three
        context.getString(
            res,
            candidates.size,
            query,
            candidates[0].displayName,
            candidates[1].displayName,
            candidates[2].displayName,
        )
    }
    else -> {
        val firstThree = candidates.take(3).joinToString(", ") { it.displayName }
        context.getString(R.string.copy_disambig_ask_n, candidates.size, query, firstThree)
    }
}
```

### `SttClient.listenForPicker`

```kotlin
fun listenForPicker(candidates: List<Contact>): Flow<PickerVoice>

sealed interface PickerVoice {
    data class Pick(val contact: Contact) : PickerVoice
    data object None : PickerVoice
    data class Other(val text: String) : PickerVoice
    data class Failed(val error: CurroError) : PickerVoice
}
```

The impl in `SpeechRecognizerSttClient` opens a recogniser session,
post-processes the result, and emits exactly one terminal event:

1. Normalise the result text: `text.lowercase().normalizeAccents()`.
2. Iterate candidates by display order; for each, match if any of:
   - `normalised text` equals `candidate.firstName.normalised`
   - `normalised text` equals `candidate.displayName.normalised`
   - `normalised text` matches the ordinal at this index (1 → "primera" /
     "primero" / "la primera" / "el primero"; 2 → "segunda" / "segundo"; 3 →
     "tercera" / "tercero").
3. If `normalised text` matches "ninguna" / "ninguno" / "ningún" / "nadie" /
   "ninguno de estos" / "ninguna de estas" → `None`.
4. Otherwise → `Other(text)`.

Edge case: if two candidates share the same first name ("María García",
"María López"), voice-only first-name pick is ambiguous → fall through to
`Other`. The user must say the full name or the ordinal. Pin in the impl.

### `ContactPickerOverlay`

```kotlin
@Composable
fun ContactPickerOverlay(
    state: AssistantState.Confirming,
    onPick: (Contact) -> Unit,
    onNone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = state.pendingAction.kind as? PendingAction.Kind.PickContact ?: return
    ContactPickerOverlayContent(
        prompt = state.prompt,
        candidates = kind.candidates,
        onPick = onPick,
        onNone = onNone,
        modifier = modifier,
    )
}

@Composable
internal fun ContactPickerOverlayContent(
    prompt: String,
    candidates: List<Contact>,
    onPick: (Contact) -> Unit,
    onNone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayCount = minOf(candidates.size, 3)
    val visible = candidates.take(displayCount)
    val overflow = candidates.drop(3)
    var moreExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = CurroSpacing.l, vertical = CurroSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(CurroSpacing.l))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(CurroSpacing.m),
            modifier = Modifier.weight(1f),
        ) {
            items(visible, key = { it.lookupKey }) { c ->
                ContactPickerRow(c, onClick = { onPick(c) })
            }
            if (overflow.isNotEmpty()) {
                item(key = "more") {
                    if (!moreExpanded) {
                        BigListRow(
                            label = stringResource(R.string.copy_disambig_more_label),
                            onClick = { moreExpanded = true },
                        )
                    }
                }
                if (moreExpanded) {
                    items(overflow, key = { it.lookupKey }) { c ->
                        ContactPickerRow(c, onClick = { onPick(c) })
                    }
                }
            }
            item(key = "none") {
                BigListRow(
                    label = stringResource(R.string.copy_disambig_none_option),
                    onClick = onNone,
                )
            }
        }
    }
}

@Composable
private fun ContactPickerRow(contact: Contact, onClick: () -> Unit) {
    BigCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.BigButtonHeight),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (contact.photoUri != null) {
                AsyncImage(
                    model = contact.photoUri,
                    contentDescription = null,    // decorative — name carries the meaning
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(CurroSpacing.m))
            }
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
```

(The implementer is free to adjust the inner row layout; pin the constraints:
≥ 96 dp tall, `displayName` at `headlineSmall` or larger, photo if present,
the row is one tap target.)

Edge: feminine-vs-masculine "Ninguna" label. The composable reads
`R.string.copy_disambig_none_option` (feminine, "Ninguna de estas"). For an
all-masculine candidate list (unusual: 3 Pepes), the implementer may pass
`noneText: String` as a parameter and the coordinator chooses
`copy_disambig_none_option_masc`. Pin: parameterise the label; the heuristic
matches the give-up-line gender heuristic.

### Coordinator: the picker flow

```kotlin
private var pickerListenerJob: Job? = null
private var disambigMissCount = 0

// New branch in renderHandlerResult:
is HandlerResult.NeedsContactPick -> {
    disambigMissCount = 0
    val pendingAction = PendingAction(
        functionName = call.action,
        kind = PendingAction.Kind.PickContact(result.candidates, result.onPick),
    )
    stateMachine.transition(
        AssistantEvent.FunctionCallReady(
            needsConfirmation = true,
            speech = "",
            screen = null,
            prompt = result.prompt,
            expiresAtMs = timeProvider.now() + CONFIRM_TIMEOUT_MS,
            pendingAction = pendingAction,
        ),
    )
    ttsClient.speak(result.prompt)
    startPickerListening(result.candidates, pendingAction, timeProvider.now() + CONFIRM_TIMEOUT_MS)
}

private fun startPickerListening(
    candidates: List<Contact>,
    pendingAction: PendingAction,
    expiresAtMs: Long,
) {
    pickerListenerJob = scope.launch {
        while (isActive) {
            sttClient.listenForPicker(candidates).collect { event ->
                when (event) {
                    is PickerVoice.Pick -> { onPickerPicked(event.contact, pendingAction); return@collect }
                    PickerVoice.None    -> { onPickerNone(pendingAction); return@collect }
                    is PickerVoice.Other,
                    is PickerVoice.Failed -> {
                        // First miss: re-ask. Second: give up.
                        if (disambigMissCount == 0) {
                            disambigMissCount = 1
                            // Re-speak prompt; the while-loop relaunches the inner Flow.
                            ttsClient.speak((state.value as AssistantState.Confirming).prompt)
                        } else {
                            onPickerGiveUp(pendingAction)
                            return@collect
                        }
                    }
                }
            }
        }
    }
    // SF-6.2's confirmationTimeoutJob is reused — start it here too if not already running.
    confirmationTimeoutJob = scope.launch {
        delay((expiresAtMs - timeProvider.now()).coerceAtLeast(0L))
        if (state.value is AssistantState.Confirming) {
            onConfirmationTimedOut()    // existing SF-6.2 path
        }
    }
}

fun onPickerPicked(contact: Contact, pendingAction: PendingAction) {
    scope.launch {
        cancelPickerJobs()
        val kind = pendingAction.kind as PendingAction.Kind.PickContact
        val result = kind.onPick(contact)
        // result is the handler's outcome — Spoken(copy_calling(...)) on success.
        when (result) {
            is HandlerResult.Spoken -> {
                stateMachine.transition(
                    AssistantEvent.UserConfirmed(speech = result.speech, screen = result.screen),
                )
                ttsClient.speak(result.speech)
                stateMachine.transition(AssistantEvent.ExecutionDone)
            }
            is HandlerResult.Failed -> {
                stateMachine.transition(
                    AssistantEvent.UserConfirmed(speech = result.speech, screen = null),
                )
                ttsClient.speak(result.speech)
                stateMachine.transition(AssistantEvent.ExecutionDone)
            }
            else -> { /* defensive: should not happen — placeCallOrFail returns Spoken|Failed */ }
        }
    }
}

fun onPickerNone(pendingAction: PendingAction) {
    scope.launch {
        cancelPickerJobs()
        val kind = pendingAction.kind as PendingAction.Kind.PickContact
        val result = kind.onPick(null)   // handler returns Spoken(copy_cancel_no_call)
        val speech = (result as? HandlerResult.Spoken)?.speech
            ?: appContext.getString(R.string.copy_cancel_no_call)
        stateMachine.transition(AssistantEvent.UserConfirmed(speech = speech, screen = null))
        ttsClient.speak(speech)
        stateMachine.transition(AssistantEvent.ExecutionDone)
    }
}

private fun onPickerGiveUp(pendingAction: PendingAction) {
    scope.launch {
        cancelPickerJobs()
        val kind = pendingAction.kind as PendingAction.Kind.PickContact
        val masculine = isMasculine(kind.candidates.firstOrNull()?.displayName.orEmpty())
        val giveUp = appContext.getString(
            if (masculine) R.string.copy_disambig_give_up_masc else R.string.copy_disambig_give_up,
        )
        stateMachine.transition(AssistantEvent.UserConfirmed(speech = giveUp, screen = null))
        ttsClient.speak(giveUp)
        stateMachine.transition(AssistantEvent.ExecutionDone)
    }
}

private fun cancelPickerJobs() {
    pickerListenerJob?.cancel()
    pickerListenerJob = null
    confirmationTimeoutJob?.cancel()
    confirmationTimeoutJob = null
    disambigMissCount = 0
}

// Extend cancelInFlight() to include cancelPickerJobs().
```

### Strings (verify + add)

| Key | Spanish | Status |
|---|---|---|
| `copy_disambig_ask_three` | "Tienes %1$d %2$ss. ¿Cuál de ellas?: %3$s, %4$s o %5$s." | **Exists** (line 92). |
| `copy_disambig_ask_three_masc` | "Tienes %1$d %2$ss. ¿Cuál de ellos?: %3$s, %4$s o %5$s." | **Exists** (line 94). |
| `copy_disambig_ask_n` | "Tienes %1$d coincidencias para %2$s. Las primeras son: %3$s. ¿Cuál?" | **Exists** (line 96). |
| `copy_disambig_give_up` | "Mejor llámala desde la agenda, no me aclaro." | **Exists** (line 98). |
| `copy_disambig_give_up_masc` | "Mejor llámalo desde la agenda, no me aclaro." | **Exists** (line 100). |
| `copy_disambig_none_option` | "Ninguna de estas" | **Exists** (line 102). |
| `copy_disambig_none_option_masc` | "Ninguno de estos" | **Exists** (line 104). |
| `copy_cancel_no_call` | "Vale, no llamo." | **Exists** (line 30). |
| `copy_contact_not_found` | "No encuentro a %1$s en tus contactos." | **Exists** (line 159). |
| `copy_calling` | "Llamando a %1$s." | **Exists** (line 44). |
| `copy_disambig_ask_two` | "Tienes %1$d %2$ss. ¿Cuál de ellas?: %3$s o %4$s." | **NEW — add.** |
| `copy_disambig_ask_two_masc` | "Tienes %1$d %2$ss. ¿Cuál de ellos?: %3$s o %4$s." | **NEW — add.** |
| `copy_disambig_more_label` | "Más" | **NEW — add.** Button label for the ≥ 4 row. |

Ordinal vocabulary (no string resources — used internally by
`SpeechRecognizerSttClient` to match STT output; the user never sees them):

| Index | Strings recognised |
|---|---|
| 0 | "primera", "primero", "la primera", "el primero" |
| 1 | "segunda", "segundo", "la segunda", "el segundo" |
| 2 | "tercera", "tercero", "la tercera", "el tercero" |

No string resources needed — the recogniser does the post-hoc match.

The Phase-4 `copy_contact_ambiguous_phase4` ("Tienes varios contactos así;
espera, todavía no sé elegir entre ellos.") **stays in `strings.xml`** for now —
keep it; deleting unused strings is out of Phase 6's scope and the resource is
small. Mark with a comment that SF-6.3 made it unreachable; Phase 8 may remove
in a clean-up SF.

---

## Acceptance Criteria

### Build & static checks
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug` green.
- [ ] No new permissions, no new manifest entries.
- [ ] 3 new strings added (`copy_disambig_ask_two`, `_two_masc`,
      `copy_disambig_more_label`).

### `CallContactHandler` correctness
- [ ] 1 match → `Spoken(copy_calling(...))` (regression from Phase 4 — still
      works).
- [ ] 0 matches → `Failed(copy_contact_not_found, ContactNotFound)` (regression).
- [ ] 2 matches → `NeedsContactPick` with `prompt = copy_disambig_ask_two(...)`,
      `candidates.size == 2`.
- [ ] 3 matches (feminine first names) → `NeedsContactPick` with `prompt =
      copy_disambig_ask_three(...)`.
- [ ] 3 matches (masculine, query ends in "o") → `NeedsContactPick` with
      `prompt = copy_disambig_ask_three_masc(...)`.
- [ ] 4+ matches → `NeedsContactPick` with `prompt = copy_disambig_ask_n(...)`,
      `candidates.size == 4`.
- [ ] `NeedsContactPick.onPick(picked)` → `Spoken(copy_calling(picked.displayName))`.
- [ ] `NeedsContactPick.onPick(null)` → `Spoken(copy_cancel_no_call)`.

### `ContactPickerOverlay` UI correctness (Compose UI tests)
- [ ] 3 candidates → 4 visible tap targets (3 candidate rows + "Ninguna").
- [ ] 4 candidates → 5 visible tap targets (3 candidate rows + "Más" +
      "Ninguna").
- [ ] Tap "Más" → "Más" row replaced by the 4th candidate row.
- [ ] Each candidate row is ≥ 96 dp tall.
- [ ] "Ninguna" row is ≥ 96 dp tall.
- [ ] Tap candidate "María López" → `onPick(María López)` invoked.
- [ ] Tap "Ninguna" → `onNone` invoked.
- [ ] Tap "Más" then tap an overflow candidate → `onPick` invoked.
- [ ] `@Preview`s render without warning (3 candidates / 4 candidates / dark /
      1.5× / 2.0×).

### Coordinator correctness (end-to-end with fakes)
- [ ] 3-Marías flow + tap on María López → call placed with María López's
      number; `kind.onPick` invoked once with María López; FSM `Confirming →
      Executing(copy_calling("María López")) → Idle`.
- [ ] 3-Marías + voice "María García" via `FakeStt.listenForPicker = flowOf(Pick(García))`
      → call placed with García's number; same FSM path.
- [ ] 3-Marías + voice "la primera" → picks `candidates[0]`.
- [ ] 3-Marías + voice "ninguna" → `kind.onPick(null)` invoked; TTS speaks
      "Vale, no llamo."; no call placed; FSM → `Idle`.
- [ ] 3-Marías + voice "la cuarta" → `disambigMissCount = 1`; TTS re-speaks
      the prompt; STT pass restarted.
- [ ] 3-Marías + voice "la cuarta" twice → second time fires `onPickerGiveUp`;
      TTS speaks `copy_disambig_give_up`; FSM → `Idle`.
- [ ] 3-Pepes (query "Pepito" — ends in "o") + voice "la cuarta" twice → give-
      up uses `copy_disambig_give_up_masc` ("Mejor llámalo…").
- [ ] 10-s silence during picker → `confirmationTimeoutJob` fires; TTS speaks
      "Cancelo entonces."; FSM → `Idle`.
- [ ] `MicPressed` mid-picker → `cancelInFlight` cancels `pickerListenerJob` +
      `confirmationTimeoutJob` + TTS + STT; FSM → `Listening`.

### `SttClient.listenForPicker` correctness (Robolectric)
- [ ] STT result "María García" → `Pick(María García)`.
- [ ] STT result "maria garcia" (accentless, lowercase) → `Pick(María García)`.
- [ ] STT result "la primera" → `Pick(candidates[0])`.
- [ ] STT result "tercero" → `Pick(candidates[2])`.
- [ ] STT result "ninguna" → `None`.
- [ ] STT result "Lucía" (not a candidate) → `Other("lucía")`.
- [ ] Two-candidate same-first-name ambiguity ("María García" + "María López",
      STT returns "María") → `Other("maría")` (must say full name or ordinal).

### Alias-learning constraint (regression for Phase 7)
- [ ] At no point during a picker resolution does the coordinator call any
      `AliasRepository.upsertAlias(...)` method. Verify with a fake repo that
      records every call — assert it stays empty across all picker flows.
      (This is the spec §7 + flow 4 note enforcement.)

### Telemetry
- [ ] Optionally emit a `picker_resolved` event with props `{outcome: "picked"
      | "ninguna" | "give_up" | "timeout", candidate_count: Int}`. **Pin: SKIP
      this in SF-6.3** to keep the telemetry surface small. The
      `policy_decided` event already captures the policy decision; the picker
      is downstream and Fran can infer the outcome from the call log.

### Manual smoke (Redmi 15)
- [ ] Add three "María …" contacts. Say "llama a María". Verify the overlay
      renders, the prompt is read aloud, and:
      - Tap "María López" → call placed.
      - Say "María García" → call placed.
      - Say "la primera" → call placed (whoever is first).
      - Say "ninguna" → "Vale, no llamo." + back to launcher.
      - Say "la cuarta" then "la quinta" → give-up line + back to launcher.
- [ ] Add four "María …" contacts. Same call: verify "Más" row appears and
      expands.

---

## Senior-UX & Copy

The visual rules:
- Up to 3 candidate rows + "Ninguna" → 4 huge tap targets on a single
  screen-height. Use `BigCard` per row; ≥ 96 dp tall each.
- For ≥ 4 candidates, "Más" row is at the END of the visible 3; tapping
  expands inline (no second screen). "Ninguna" stays at the bottom of the
  list.
- Contact photo (`Coil` `AsyncImage` with crossfade) is visual support; not
  load-bearing — the name is the primary label.
- Display name uses `headlineSmall` (visible at arm's length).
- The prompt text is at `displaySmall` (one notch below `ConfirmationOverlay`'s
  `displayMedium` to leave room for the list).
- High contrast: candidate rows use `surfaceContainerHigh` background; "Ninguna"
  uses `surfaceContainerLow` to visually separate "pick" vs "cancel".

The audio rules:
- TTS reads the prompt once. STT pass opens AFTER TTS finishes.
- On the first miss, TTS re-reads the prompt and the STT pass re-opens.
- On the second miss, TTS reads `copy_disambig_give_up(_masc)` and the FSM
  goes home.
- On "ninguna", TTS speaks "Vale, no llamo." (same line as the SF-6.2 NO-tap
  outcome — consistency).

The voice rules:
- Curro reads **up to 3** candidate names in the prompt (spec §6 flow 3 + the
  `copy_disambig_ask_n` template for > 3).
- Curro does NOT read every candidate when there are 4+ (would take too long
  for a senior — the visible list + the "Más" expansion is the UX).
- Curro does NOT volunteer "Lo apunto" or any alias-related line — pinned per
  spec §7 + flow 4 note. Phase 7 wires alias learning on a separate path.

---

## Performance Considerations

- `LazyColumn` for the candidate list (mandatory per `compose-patterns` rule:
  never a plain `Column` for variable-length content).
- `Coil` `AsyncImage` for the contact photo — already in dependencies, lazy-
  loaded, cancels on row scroll-off.
- The picker listener job has the same shape as SF-6.2's confirmation listener
  — no new performance concerns.
- The 10-s timer is the same one SF-6.2 introduced; no new timer.
- `Contact.displayName.split(' ').first()` is allocation-free for typical
  inputs; called once per candidate per STT result (small N, small cost).

---

## Testing Requirements

### `CallContactHandlerTest` — append cases

(Already exists; append to it.)

- `findByName_returnsTwoMatches_returnsNeedsContactPick` — fake
  `ContactsProvider.findByName` returns 2 Marías; assert
  `HandlerResult.NeedsContactPick`; prompt == `copy_disambig_ask_two(...)`;
  candidates == 2.
- `findByName_returnsThreeFemale_useFeminineCopy`.
- `findByName_returnsThreeMale_useMasculineCopy` (query "Pepito").
- `findByName_returnsFour_useAskN`.
- `onPick_validContact_callsController_returnsSpoken` (fake `CallController`).
- `onPick_null_returnsCancelNoCall`.

### `AssistantCoordinatorTest` — 7 new cases appended

(In addition to SF-6.1/6.2's appended cases.)

14. `pick_tapResolvesContact_placesCall`.
15. `pick_voiceFirstName_placesCall`.
16. `pick_voiceOrdinal_placesCall`.
17. `pick_voiceNinguna_speaksCancelNoCall`.
18. `pick_firstMissReAsks_secondMissGivesUp`.
19. `pick_timeoutFires_speaksCancelEntonces`.
20. `pick_micPressed_interrupts`.

### `ContactPickerOverlayTest` (instrumented, Compose UI test)

Standard senior-UX accessibility sweep + the SF-specific cases above.
≥ 4 candidate cases with "Más" expansion get their own test.

### `SpeechRecognizerSttClientTest` — 1 new parameterised case

14. `listenForPicker_mapsVocabularyCorrectly` — table-driven over the picker
    vocabulary (full names, first names, ordinals, "ninguna" / "ninguno",
    ambiguous first names → `Other`).

---

## Implementation Notes

**Order of changes within this SF:**
1. `HandlerResult.NeedsContactPick` (new variant).
2. `PendingAction.kind` refactor; YesNo/PickContact subtypes.
3. `CallContactHandler` — replace the multi-match branch; add the gender
   heuristic; add `copy_disambig_ask_two(_masc)`, `copy_disambig_more_label`.
4. `CallContactHandlerTest` — appended cases.
5. `SttClient.listenForPicker` + `PickerVoice`.
6. `SpeechRecognizerSttClient` impl + vocabulary mapping.
7. `SpeechRecognizerSttClientTest` — case 14.
8. `AssistantCoordinator` — new branch + jobs + `onPickerXxx` helpers + extend
   `cancelInFlight`.
9. 7 new `AssistantCoordinatorTest` cases.
10. `ContactPickerOverlay` composable + previews.
11. `LauncherEvent` + `LauncherViewModel` + `LauncherPlaceholderScreen` routing.
12. `ContactPickerOverlayTest` — instrumented.
13. Manual smoke on Redmi 15.

**Pin: the order of state transition vs. handler invocation.** The picker's
`onPick` callback runs `placeCallOrFail` which fires the `ACTION_CALL` Intent.
The FSM must reach `Executing(...)` and TTS must speak "Llamando a María
López." **before** the Intent fires — otherwise the call-screen overlays the
spoken TTS. Implementer pin: TTS-suspend, then return from the lambda; the
Intent fires inside the lambda body which means it fires before the lambda
returns. There is a tension. Resolution: `placeCallOrFail` is split — emit
`Spoken("Llamando a …")` first, let the coordinator suspend on TTS, then have
the coordinator call a separate `CallController.placeCall(number)` outside the
handler. Pin: refactor `placeCallOrFail` so the Intent firing is the
**second** step, separated from the `Spoken` building. Implementer's design
freedom for the exact shape; review the diff.

**Pin: no alias-learning side effect.** The implementer must ensure no
code path in SF-6.3 calls `aliases.upsertAlias(...)` / equivalent. Phase-7's
SF-7.3 is the only place that may. Add a test (above) that uses a fake
`AliasRepository` and asserts zero writes during the picker flow.

---

## Revision History

| Date | Author | Change |
|---|---|---|
| 2026-05-16 | android-product-analyst (Opus) | Initial brief — Phase 6 PM batch. |
