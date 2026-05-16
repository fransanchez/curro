# US-034 — SF-4.10 · `call_contact` handler

> **Spec trace:** spec §5 (catalog entry `call_contact`), spec §6 flow 1
> (call with high confidence — happy path; **the** canonical Phase-4
> example), spec §10 (`READ_CONTACTS` + `CALL_PHONE` requested lazily),
> spec §7 (alias model — Phase-7 wire-up; Phase 4 reads the empty stub).
> **Master-plan:** SF-4.10.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-033 (`ContactsProvider` + `AliasRepository`), US-025
> (handler interface).
> **Size:** M.
> **Skills:** `function-catalog`, `platform-integrations` (TelecomManager
> section), `voice-interaction`, `brand-design`, `testing-patterns`,
> `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `call_contact` handler — place a call to a single-match contact |
| **US ID** | US-034 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-developer |

---

## 1. Summary

Place a phone call to a contact resolved by name (or alias — Phase 7).
Phase-4 scope is **deliberately single-match only** — ambiguity returns a
`Failed` with `AmbiguousContact` until Phase 6 wires the picker overlay.
Spec §6 flow 1 (the canonical "Llama a Pepito" happy path) is the
acceptance bar.

**The handler is `needs_confirmation: CONDITIONAL`** in the catalog —
Phase 6's `ConfidencePolicy` decides "execute / confirm / clarify" based
on the FunctionGemma confidence. For Phase 4, **the dispatcher's caller
(`LauncherViewModel.render`) auto-invokes `onConfirm`** (see US-025 §3
Flow 4 hook), so a single-match call **executes directly** in Phase 4.
Phase 6 inserts the gate.

Permissions:

- `READ_CONTACTS` (declared by US-033) is requested **on the first call
  attempt** — never at install, never proactively. Denied → `Failed(
  copy_perm_missing_contacts, ReadContactsPermissionMissing)`.
- `CALL_PHONE` (declared by THIS SF) is requested **on the place-call
  step** — denied → `Failed(copy_perm_missing_calls, PermissionDenied)`.

Why this matters for *this* user: the user has 200+ contacts and can't
read names on his stock phone. Saying "llama a Pepito" is the single
most-important Phase-1 capability.

---

## 2. Scope

**In scope:**

- `data/telephony/CallController.kt` — interface + `IntentCallController`
  impl (uses `ACTION_CALL`, NOT `ACTION_DIAL`).
- `data/permissions/CallPhonePermissionGate.kt` — interface + impl.
- `handler/CallContactHandler.kt`.
- `di/TelephonyModule.kt` — new module with the two `@Binds`.
- `HandlerModule.kt` — append the `@Binds @IntoMap @StringKey("call_contact")` line.
- Manifest: `<uses-permission android:name="android.permission.CALL_PHONE" />`.
- Runtime permission wiring in `LauncherViewModel` + `LauncherScreen` — new
  side effects `RequestReadContacts`, `RequestCallPhone`, and a one-shot
  auto-retry policy on grant.
- New `CurroError.CallPermissionMissing` (sibling of the generic
  `PermissionDenied` — pin in brief whether we introduce this or reuse
  `PermissionDenied`; decision: **reuse** `PermissionDenied`, the user-facing
  copy is the same line `copy_perm_missing_calls`. We DO add
  `ReadContactsPermissionMissing` per US-033 — that's already done.).
- ≥ 12 JVM tests on the handler with fakes for every collaborator.

**Out of scope:**

- The picker overlay for `AmbiguousContact` — Phase 6 / SF-6.x.
- Confidence-policy gating between `NeedsConfirmation` and `onConfirm` —
  Phase 6 / SF-6.x.
- Incoming-call assistant mode (`InCallService`) — spec §8, opt-in, deferred.
- Alias learning — Phase 7.

---

## 3. User Flows

### Flow 1: Spec §6 flow-1 happy path — single Pepito

1. User → "Llama a Pepito" → STT.
2. FunctionGemma → `{action: "call_contact", params: {contact: "Pepito"}, confidence: 0.92}`.
3. Validator OK; dispatcher routes to `CallContactHandler`.
4. **`READ_CONTACTS` gate**: granted (user has been through the home flow
   before) → proceed.
5. `aliases.resolveAlias("Pepito")` → `emptyList()` (Phase 4 stub).
6. `contacts.findByName("Pepito")` → 1 `Contact` ("Pepe García
   Hernández", phone "+34 600 …").
7. **`CALL_PHONE` gate**: granted → `callController.call("+34 600 …")`
   → returns `true`.
8. Handler → `Spoken("Llamando a Pepe García Hernández.")` via
   `copy_calling`.
9. Android takes over the dialer; state → `Idle` on Curro side.

### Flow 2: Three Marías — ambiguous (Phase 4 deliberate single-match scope)

1. User → "Llama a María".
2. FunctionGemma → `{action: "call_contact", params: {contact: "María"}}`.
3. `contacts.findByName("María")` → 3 `Contact`s.
4. Handler → `Failed(copy_contact_ambiguous_phase4, AmbiguousContact(matches))`.
5. Curro speaks `"Tienes varios contactos así; espera, todavía no sé
   elegir entre ellos."`.
6. **Phase 6 hook**: SF-6.x replaces this branch with the contact-picker
   overlay (spec §6 flow 3).

### Flow 3: No match

1. User → "Llama a Foobar".
2. `findByName` → empty.
3. → `Failed(context.getString(copy_contact_not_found, "Foobar"),
   ContactNotFound("Foobar"))`.

### Flow 4: `READ_CONTACTS` not yet granted

1. User → "Llama a Pepito" — for the first time ever.
2. Handler reads gate → false.
3. Returns `Failed(copy_perm_missing_contacts, ReadContactsPermissionMissing)`.
4. `LauncherViewModel.render` sees the failure type AND that the in-flight
   function is `call_contact` → fires a one-shot side effect
   `RequestReadContacts`.
5. The screen invokes `rememberLauncherForActivityResult(RequestPermission())`.
6. On grant → the ViewModel **auto-retries the last `FunctionCall` once**.
7. On denial → Curro speaks `copy_perm_missing_contacts`.

### Flow 5: `CALL_PHONE` not yet granted

1. `READ_CONTACTS` granted; `findByName` returns 1 contact.
2. `callPhoneGate.isGranted() == false` → `Failed(copy_perm_missing_calls,
   PermissionDenied)`.
3. `LauncherViewModel.render` fires `RequestCallPhone` side effect.
4. Same one-shot auto-retry on grant.

### Flow 6: Alias hit (Phase 7 preview — Phase 4 NEVER hits)

1. Phase 7 wires this; Phase 4's `EmptyAliasRepository` returns `emptyList()`
   always so this flow doesn't execute. The handler structure supports it:
   `val candidates = aliasMatches.ifEmpty { contacts.findByName(query) }`.

---

## 4. Function-catalog Impact

**No catalog change** — `call_contact` already exists with
`needs_confirmation: CONDITIONAL`. The handler returns `Spoken` directly on
the happy path (Phase 4); Phase 6's policy gate wraps the call site (NOT
the handler — see US-025).

---

## 5. FSM States Touched

`Processing → Speaking → Idle`. The `confirming` path enters Phase 6.
**Single-match ambiguity returns `Failed`** in Phase 4 — no `confirming`
yet.

---

## 6. Android System Integrations & Permissions

| Integration | Why |
|---|---|
| `Intent(Intent.ACTION_CALL, Uri.parse("tel:…"))` | Place the call directly (per spec §6 flow 1). **NOT `ACTION_DIAL`**, which would only open the dialer. |
| `Context.startActivity(intent.addFlags(NEW_TASK))` | Required when starting from a non-Activity context. |
| `ContactsProvider` (US-033) | Resolve name → contact + phone. |
| `AliasRepository` (US-033 stub; Phase 7 real) | Resolve `"mi hija"` → contact. |
| `ReadContactsPermissionGate` (US-033) | Gate check. |
| `CallPhonePermissionGate` (NEW this SF) | Gate check. |

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `READ_CONTACTS` | Resolve name → contact. | First `call_contact` attempt. | `Failed(copy_perm_missing_contacts, ReadContactsPermissionMissing)`. |
| `CALL_PHONE` | Fire `ACTION_CALL`. | First time `findByName` returns a single match and the gate is false. | `Failed(copy_perm_missing_calls, PermissionDenied)`. |

**Manifest** — append:

```xml
<!--
    SF-4.10 (US-034): CALL_PHONE is required by IntentCallController to fire
    ACTION_CALL directly (spec §6 flow 1: "Llamando a Pepito" — no extra tap).
    Runtime request is wired by the launcher VM on first place-call need.
    Denial maps to CurroError.PermissionDenied → copy_perm_missing_calls.
-->
<uses-permission android:name="android.permission.CALL_PHONE" />
```

---

## 7. On-device-model Impact

No prompt change. Phase 7's alias subsystem fills
`PromptContext.knownAliases`; Phase 4 leaves it empty.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/
├── data/
│   ├── telephony/
│   │   ├── CallController.kt              // interface + IntentCallController
│   │   └── IntentCallController.kt
│   └── permissions/
│       └── CallPhonePermissionGate.kt     // interface + impl
├── handler/
│   └── CallContactHandler.kt
└── di/
    └── TelephonyModule.kt
```

### 8.2 `CallController.kt` / `IntentCallController.kt`

```kotlin
package com.curro.app.data.telephony

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface CallController {
    /**
     * Fires `Intent.ACTION_CALL` to [number]. Returns `true` on success.
     * Returns `false` if `CALL_PHONE` is missing (SecurityException), there
     * is no Activity to handle the intent (ActivityNotFoundException), or the
     * number is unusable.
     *
     * Caller is responsible for resolving / cleaning the number before passing.
     */
    fun call(number: String): Boolean
}

class IntentCallController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CallController {
        override fun call(number: String): Boolean {
            val cleaned = number.trim().ifEmpty { return false }
            val uri = Uri.parse("tel:" + Uri.encode(cleaned))
            val intent = Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (_: SecurityException) {
                false
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
```

### 8.3 `CallPhonePermissionGate.kt`

```kotlin
package com.curro.app.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface CallPhonePermissionGate {
    fun isGranted(): Boolean
}

class SystemCallPhonePermissionGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CallPhonePermissionGate {
        override fun isGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
    }
```

### 8.4 `CallContactHandler.kt`

```kotlin
package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.data.telephony.CallController
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.Contact
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.ContactsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CallContactHandler
    @Inject
    constructor(
        private val contacts: ContactsProvider,
        private val aliases: AliasRepository,
        private val callController: CallController,
        private val readContactsGate: ReadContactsPermissionGate,
        private val callPhoneGate: CallPhonePermissionGate,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "call_contact"

        @Suppress("ReturnCount", "ComplexMethod")
        override suspend fun handle(call: FunctionCall): HandlerResult {
            val rawQuery = (call.params["contact"] as? String).orEmpty().trim()
            if (rawQuery.isEmpty()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_contact_not_found, ""),
                    CurroError.ContactNotFound(""),
                )
            }

            // READ_CONTACTS gate.
            if (!readContactsGate.isGranted()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_perm_missing_contacts),
                    CurroError.ReadContactsPermissionMissing,
                )
            }

            // Alias first (Phase 4 stub always empty; Phase 7 real).
            val aliasMatches = aliases.resolveAlias(rawQuery)
            val candidates: List<Contact> =
                if (aliasMatches.isNotEmpty()) aliasMatches else contacts.findByName(rawQuery)

            return when {
                candidates.isEmpty() ->
                    HandlerResult.Failed(
                        context.getString(R.string.copy_contact_not_found, rawQuery),
                        CurroError.ContactNotFound(rawQuery),
                    )
                candidates.size > 1 ->
                    HandlerResult.Failed(
                        context.getString(R.string.copy_contact_ambiguous_phase4),
                        CurroError.AmbiguousContact(candidates),
                    )
                else -> placeCallOrFail(candidates.first(), rawQuery)
            }
        }

        private fun placeCallOrFail(contact: Contact, originalQuery: String): HandlerResult {
            val number = contact.phoneNumbers.firstOrNull()
            if (number.isNullOrBlank()) {
                // Contact found but no phone number on file → graceful failure.
                return HandlerResult.Failed(
                    context.getString(R.string.copy_contact_not_found, originalQuery),
                    CurroError.ContactNotFound(originalQuery),
                )
            }
            if (!callPhoneGate.isGranted()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_perm_missing_calls),
                    CurroError.PermissionDenied,
                )
            }
            val ok = callController.call(number)
            return if (ok) {
                HandlerResult.Spoken(context.getString(R.string.copy_calling, contact.displayName))
            } else {
                HandlerResult.Failed(
                    context.getString(R.string.copy_perm_missing_calls),
                    CurroError.PermissionDenied,
                )
            }
        }
    }
```

### 8.5 `TelephonyModule.kt`

```kotlin
package com.curro.app.di

import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.SystemCallPhonePermissionGate
import com.curro.app.data.telephony.CallController
import com.curro.app.data.telephony.IntentCallController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelephonyModule {
    @Binds @Singleton
    abstract fun bindCallController(impl: IntentCallController): CallController

    @Binds @Singleton
    abstract fun bindCallPhonePermissionGate(
        impl: SystemCallPhonePermissionGate,
    ): CallPhonePermissionGate
}
```

### 8.6 `HandlerModule.kt` — append

```kotlin
@Binds
@IntoMap
@StringKey("call_contact")
abstract fun bindCallContactHandler(impl: CallContactHandler): FunctionHandler
```

### 8.7 `LauncherViewModel` runtime-permission wiring

Two new side effects + the auto-retry policy:

```kotlin
sealed interface LauncherSideEffect {
    // … existing
    data object RequestReadContacts : LauncherSideEffect    // NEW
    data object RequestCallPhone : LauncherSideEffect       // NEW
}

sealed interface LauncherEvent {
    // … existing
    data class ReadContactsPermissionResult(val granted: Boolean) : LauncherEvent
    data class CallPhonePermissionResult(val granted: Boolean) : LauncherEvent
}
```

In `LauncherViewModel.render(...)`, on `Failed(_, reason)`:

```kotlin
when (reason) {
    is CurroError.ReadContactsPermissionMissing -> {
        if (call.action == "call_contact" && !readContactsAutoRetried) {
            readContactsAutoRetried = true
            _sideEffects.send(LauncherSideEffect.RequestReadContacts)
            return  // Don't speak yet; if granted we auto-retry.
        }
    }
    is CurroError.PermissionDenied -> {
        if (call.action == "call_contact" && !callPhoneAutoRetried) {
            callPhoneAutoRetried = true
            _sideEffects.send(LauncherSideEffect.RequestCallPhone)
            return
        }
    }
    else -> Unit
}
// Default path: speak + log + Idle.
```

The `*AutoRetried` flags reset to `false` at the **start of every new
mic-press turn** (in `onMicPressed`), so each user interaction has at most
one auto-retry per permission.

On `ReadContactsPermissionResult(granted = true)` → re-dispatch the last
`FunctionCall` via the dispatcher. On denial → speak `copy_perm_missing_contacts`
+ Idle. Symmetric for `CallPhonePermissionResult`.

**Decision pinned**: this auto-retry policy is Phase-4-specific glue. Phase 5
moves it into `AssistantCoordinator` where the FSM owns retry semantics
cleanly. The brief loud-flags that the ViewModel-side glue is provisional.

### 8.8 `LauncherScreen` — two new `rememberLauncherForActivityResult`

Mirror the existing US-017 `RECORD_AUDIO` request flow: each side effect
launches its own `RequestPermission()` registration, and the result becomes
a `LauncherEvent.*Result(granted)` back to the ViewModel.

### 8.9 `strings.xml`

All required strings exist and are reused:

- `copy_calling` (`"Llamando a %1$s."`).
- `copy_contact_not_found` (`"No encuentro a %1$s en tus contactos."`).
- `copy_contact_ambiguous_phase4` (added by US-033).
- `copy_perm_missing_contacts`.
- `copy_perm_missing_calls`.

**No new strings in this SF.**

### 8.10 `TelemetryGuardrail`

No change. `handler_invoked` (US-025) covers this handler's events.
**Never** log phone numbers, contact names, queries — only the action name
(`call_contact`) and outcome (`success|failed|crash`).

---

## 9. Acceptance Criteria

- [ ] All five new files exist at the documented paths.
- [ ] Manifest gains `CALL_PHONE` with the documented comment.
- [ ] `HandlerModule` gains the `@Binds @IntoMap @StringKey("call_contact")` line.
- [ ] `TelephonyModule` binds `CallController` and `CallPhonePermissionGate`.
- [ ] **Single match by name → call is placed**: on the Redmi 15, "Llama a
      Pepito" (with one Pepito in contacts and both permissions granted) →
      `ACTION_CALL` fires; Curro speaks `"Llamando a Pepe García Hernández."`.
- [ ] **Multi-match → ambiguous failure**: "Llama a María" with 3 Marías →
      Curro speaks `copy_contact_ambiguous_phase4`. No call placed.
- [ ] **No match → not-found failure**: "Llama a Foobar" → Curro speaks
      `"No encuentro a Foobar en tus contactos."`. No call placed.
- [ ] **Empty `contact` param** → `ContactNotFound("")` + the empty-string
      copy.
- [ ] **`READ_CONTACTS` not granted**:
  - First attempt → handler returns `Failed(_,
    ReadContactsPermissionMissing)`; ViewModel auto-fires
    `RequestReadContacts` side effect; user grants → auto-retry → handler
    runs to completion.
  - User denies → Curro speaks `copy_perm_missing_contacts`.
  - On a second `call_contact` press in the same denial-state, **no further
    auto-prompt** — the `*AutoRetried` flag resets per turn but the gate is
    still false, so the line speaks immediately.
- [ ] **`CALL_PHONE` not granted**: same shape as `READ_CONTACTS` — one-shot
      auto-retry on grant; speak the line on denial.
- [ ] **`callController.call` returns false** (SecurityException edge) →
      `Failed(copy_perm_missing_calls, PermissionDenied)`.
- [ ] **Contact found with no phone number** on file →
      `Failed(copy_contact_not_found, ContactNotFound)`.
- [ ] **Contact with multiple phone numbers** → uses the FIRST. **Decision
      pinned**: Phase 6 may add a phone-number picker; Phase 4 takes the
      first.
- [ ] **Accent-stripping in resolution**: "Llama a jose" → resolves "José".
- [ ] **Alias hit short-circuits**: when `AliasRepository` returns a non-empty
      list (e.g. injected fake in test), the contacts query is NOT called.
- [ ] **No PII in `Log.w("Curro/FailedCommand", ...)`** — arg-captor test
      asserts the log line contains `utterance.len=<int>` and no contact
      name.
- [ ] **No PII in `handler_invoked` telemetry** — only `function_name=call_contact`
      and `outcome ∈ {success, failed}`.
- [ ] No new strings.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green.

---

## 10. Senior-UX & Copy

| String ID | Spanish | Voice notes |
|---|---|---|
| `copy_calling` | "Llamando a %1$s." | Reused. Spec §6 flow 1's canonical line. |
| `copy_contact_not_found` | "No encuentro a %1$s en tus contactos." | Reused. |
| `copy_contact_ambiguous_phase4` | "Tienes varios contactos así; espera, todavía no sé elegir entre ellos." | Added by US-033. Phase 6 deletes this path. |
| `copy_perm_missing_contacts` | "Necesito permiso para ver tus contactos. Díselo a Fran." | Reused. |
| `copy_perm_missing_calls` | "Necesito permiso para llamar. Díselo a Fran." | Reused. |

Voice: every line is a single sentence. The "espera, todavía no sé" wording
is deliberate — it tells the user this is a Phase-4 limitation that will
get better, not a permanent failure.

---

## 11. Design Notes

No new visual surface in Phase 4 — the picker overlay arrives Phase 6.

---

## 12. Performance Considerations

- `contacts.findByName` reads the resolver (~10 ms on a 500-contact device,
  per US-033).
- `aliases.resolveAlias` is `emptyList()` — `O(1)` in Phase 4.
- `callController.call` returns immediately; the OS takes over the dialer.

---

## 13. Testing Requirements

**`CallContactHandlerTest.kt`** — pure JVM with fakes:

- `FakeContactsProvider(byQuery: Map<String, List<Contact>>)`.
- `FakeAliasRepository(byAlias: Map<String, List<Contact>>)` — Phase 4 test
  default is empty; one test injects a non-empty hit to verify short-circuit.
- `FakeCallController(captureNumber: MutableList<String>, returnValue: Boolean)`.
- `FakeReadContactsPermissionGate(granted: Boolean)`.
- `FakeCallPhonePermissionGate(granted: Boolean)`.
- Robolectric `Context` for `getString`.

Cases (≥ 12):

1. **Happy path** — single match, both gates true, callController returns
   true → `Spoken("Llamando a Pepe García Hernández.")`; captured number ==
   the contact's first phone.
2. **Multi-match** — 3 contacts → `Failed(AmbiguousContact)`,
   `copy_contact_ambiguous_phase4`. `callController.call` NOT invoked.
3. **No match** → `Failed(ContactNotFound("Foobar"))`,
   `copy_contact_not_found` with `"Foobar"`.
4. **Empty `contact`** → `Failed(ContactNotFound(""))`.
5. **`READ_CONTACTS` denied** → `Failed(ReadContactsPermissionMissing)`,
   `copy_perm_missing_contacts`. **`contacts.findByName` NOT invoked** — the
   gate check short-circuits.
6. **`CALL_PHONE` denied** at place-call → `Failed(PermissionDenied)`,
   `copy_perm_missing_calls`. **`callController.call` NOT invoked**.
7. **`callController.call` returns false** (e.g. SecurityException) →
   `Failed(PermissionDenied)`, `copy_perm_missing_calls`.
8. **Contact with no phone numbers** → `Failed(ContactNotFound)` (graceful);
   the speech includes the original query, not the contact's name.
9. **Multiple phone numbers** → first wins; captured number == the first.
10. **Accent variant**: query `"jose"` resolves via `ContactsProvider`'s
    normalisation → 1 contact for "José" → call placed.
11. **Alias short-circuit**: `FakeAliasRepository` returns one match for
    "mi hija" → `contacts.findByName` is NOT called (verified by counter on
    the fake) → call placed against the alias-resolved contact.
12. **No-PII log assertion**: when the handler returns `Failed`,
    `LauncherViewModelTest` verifies `Log.w("Curro/FailedCommand", ...)` does
    NOT contain the contact name (already covered structurally by US-025;
    pin a `call_contact`-specific assertion here too).

**On-device verification** on the Redmi 15:

1. With a single Pepito contact and both permissions granted: "Llama a
   Pepito" → dialer takes over with the call ringing.
2. With three Marías: "Llama a María" → Curro speaks the ambiguous line.
3. With a no-match query: "Llama a Foobar" → Curro speaks the not-found line.
4. Permission-revocation cycle (Settings → revoke `READ_CONTACTS`): first
   `call_contact` after revocation → auto-prompt; deny → line spoken; grant →
   auto-retry → success.
5. Same for `CALL_PHONE`.

---

## 14. Implementation Notes — Order of Operations

1. Verify US-033 is committed (provides `ContactsProvider`, `AliasRepository`,
   `ReadContactsPermissionGate`, and `READ_CONTACTS` manifest entry).
2. Manifest: append `CALL_PHONE`.
3. Create `data/telephony/CallController.kt` + `IntentCallController.kt`.
4. Create `data/permissions/CallPhonePermissionGate.kt`.
5. Create `di/TelephonyModule.kt`.
6. Create `handler/CallContactHandler.kt`.
7. Append the handler `@Binds @IntoMap @StringKey("call_contact")` to
   `HandlerModule`.
8. Extend `LauncherViewModel` with the two new side effects + auto-retry
   policy + the `*AutoRetried` per-turn flags.
9. Extend `LauncherScreen` with the two new `rememberLauncherForActivityResult`
   handlers.
10. Write `CallContactHandlerTest` (12+).
11. Extend `LauncherViewModelTest` with the auto-retry policy cases.
12. `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
13. On-device verification per AC.
14. Commit as `feat: add call_contact handler + telephony + permission wiring (US-034 / SF-4.10)`.

---

## 15. Phase 5 / 6 / 7 Hooks

- **Phase 5** — auto-retry policy moves into `AssistantCoordinator`; the
  ViewModel becomes a passive observer.
- **Phase 6** — the `AmbiguousContact` failure branch is replaced with a
  picker overlay (spec §6 flow 3). The confidence-policy gate intercepts
  between `Spoken`-success and the actual call execution for `confidence <
  0.85`.
- **Phase 7** — `AliasRepository` is wired to its Room-backed impl; the
  alias-learning subflow fires on the first unresolved relational term
  (spec §6 flow 4).

---

## 16. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
