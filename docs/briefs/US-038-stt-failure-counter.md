# US-038 — SF-5.4 · Consecutive-STT-failure policy

> **Spec trace:** spec §6 flow 6 — the consecutive-failure policy:
> 1st fail → "No te he oído bien, ¿puedes repetirlo?";
> 2nd → "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto.";
> 3rd → "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés
> listo." → cancel and return to `idle`. Counter resets on **any successful
> turn**.
> Spec §2: *"Fallar de forma comprensible"* — kills the infinite "no te
> entiendo" loop that frustrates a senior user.
> **Master-plan:** SF-5.4.
> **Phase:** 5 — State machine & interruption.
> **Depends on:** US-035 (FSM — `ErrorRecovery.failureCount` field), US-036
> (coordinator — the integration point).
> **Size:** S.
> **Skills:** `voice-interaction` (rule 3 — never loop on "no te entiendo";
> rule 4 — consecutive-failure policy), `brand-design` (the three COPY entries
> already exist), `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `SttFailureCounter` — the 1/2/3 counter + the coordinator wiring that picks copy |
| **US ID** | US-038 |
| **Phase** | 5 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | voice-pipeline-engineer |

---

## 1. Summary

Today (post-SF-5.2), every STT failure routes through `onSttFailed` with a
**hardcoded `failureCount = 1`** → always speaks `copy_stt_fail_1`. This SF
adds the real spec-§6-flow-6 policy:

- A `@Singleton SttFailureCounter` tracks consecutive STT failures.
  `recordFailure()` returns the new count (1, 2, 3, …); `recordSuccess()`
  resets to 0.
- Counter resets on **any successful turn** — defined as: the coordinator
  reaches `Executing` (handler success) **OR** `Confirming` (handler asked to
  confirm) **OR** any non-STT failure that produces a successfully-classified
  `FunctionCall` (e.g., `Failed(ContactNotFound(...))` — the STT *worked*,
  the handler couldn't find the contact). The counter only counts the
  *recognition* failing, not the downstream pipeline.
- The coordinator's `onSttFailed` becomes:
  ```kotlin
  val newCount = sttFailureCounter.recordFailure()
  val msg = pickFailMessage(newCount)
  stateMachine.transition(AssistantEvent.SttFailed(msg, newCount))
  ttsClient.speak(msg)
  if (newCount >= 3) sttFailureCounter.recordSuccess() // reset for the next press
  stateMachine.transition(AssistantEvent.RecoverySpoken)
  ```
- `pickFailMessage(n)`: `1 → copy_stt_fail_1`, `2 → copy_stt_fail_2`, `≥ 3 →
  copy_stt_fail_3`. (≥ 3 is a safety; the counter is reset on hitting 3 so
  the next press starts fresh — but if some pathological sequence reaches
  4+, fail-3 stays the message.)

Why this matters for *this* user: the spec is explicit — *"esto evita el
bucle infinito de 'no te entiendo' que es lo más frustrante para un usuario
mayor."* The 3-strike rule is the difference between "Curro is patient with
me" and "Curro is harassing me into giving up."

---

## 2. Scope

**In scope:**

- `app/src/main/java/com/curro/app/assistant/SttFailureCounter.kt` — the
  small `@Singleton` counter class.
- Coordinator (`AssistantCoordinator.kt`) integration: inject
  `SttFailureCounter`; replace the hardcoded `failureCount = 1` in
  `onSttFailed`; add `recordSuccess()` calls at the success-side
  emission points (see §6).
- 3 JVM tests on `SttFailureCounterTest.kt`.
- 5 new JVM tests on `AssistantCoordinatorTest.kt` covering the 1st/2nd/3rd
  copy choice + reset semantics.
- Verify the three COPY entries (`copy_stt_fail_1/2/3`) exist in
  `strings.xml`. **Verified — all three already exist** (lines 80–84). No
  string changes.

**Out of scope:**

- A per-user "give up after 5 strikes instead of 3" preference — Phase 8.
- The 10-s silence-cancel in `Confirming` (Phase 6 — a different timeout).
- Tracking STT failure rates in telemetry beyond the existing
  `model_decide(outcome="other")` — the brief pins **no new telemetry**
  in this SF (the failure count is in-memory and not externally observable
  beyond the spoken copy + the FSM `ErrorRecovery.failureCount` field).
- A future "speak more slowly" hint after a 2nd STT fail — that's already
  in the copy itself (`copy_stt_fail_2`: "Acércate un poco al teléfono y
  habla más alto").

---

## 3. User Flows

### Flow 1: Single failure, then user gives up

| # | Step | Counter | FSM event | What Curro says |
|---|---|---|---|---|
| 1 | User taps mic. | 0 | `MicPressed` | — |
| 2 | User mumbles. STT `Failed(SttNoMatch)`. | 1 (after `recordFailure`) | `SttFailed(copy_stt_fail_1, 1)` → `ErrorRecovery` | "No te he oído bien, ¿puedes repetirlo?" |
| 3 | `RecoverySpoken` → `Idle`. User walks away. | 1 | | — |
| 4 | Five minutes later, user taps mic again. | 1 | `MicPressed` | — |
| 5 | User mumbles again. STT `Failed`. | 2 (after `recordFailure`) | `SttFailed(copy_stt_fail_2, 2)` → `ErrorRecovery` | "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto." |

Pinned: the counter **does not** auto-reset on time. Only `recordSuccess()`
resets it. If five minutes pass with no successful turn, the next STT failure
is still "2nd". This matches the spec — the policy is *per-user-session*,
not *per-minute*.

### Flow 2: Three strikes, counter resets

| # | Step | Counter | FSM event | What Curro says |
|---|---|---|---|---|
| 1 | First STT fail. | 1 | `SttFailed(stt_fail_1, 1)` → `ErrorRecovery` → `Idle` | "No te he oído…" |
| 2 | Second STT fail. | 2 | `SttFailed(stt_fail_2, 2)` → `ErrorRecovery` → `Idle` | "Sigo sin…" |
| 3 | Third STT fail. | 3 | `SttFailed(stt_fail_3, 3)` → `ErrorRecovery` → coordinator resets counter (`recordSuccess`) → `Idle` | "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo." |
| 4 | Fourth STT fail (a few minutes later). | 1 (reset by step 3, then recordFailure → 1) | `SttFailed(stt_fail_1, 1)` → `ErrorRecovery` → `Idle` | "No te he oído…" |

### Flow 3: Successful turn resets the counter

| # | Step | Counter | FSM event |
|---|---|---|---|
| 1 | First STT fail. | 1 | `SttFailed(stt_fail_1, 1)` → `Idle` |
| 2 | Second STT fail. | 2 | `SttFailed(stt_fail_2, 2)` → `Idle` |
| 3 | User speaks "qué hora es" successfully. Handler returns `Spoken("Son las 13:47")`. **Coordinator calls `recordSuccess()` before transitioning to `Executing`.** | 0 (reset) | `FunctionCallReady(nc=false, "Son las 13:47", …)` → `Executing` → `ExecutionDone` → `Idle` |
| 4 | Later STT fail. | 1 (after recordFailure) | `SttFailed(stt_fail_1, 1)` |

### Flow 4: Handler-side failure does NOT reset the counter

| # | Step | Counter | What happens |
|---|---|---|---|
| 1 | First STT fail. | 1 | "No te he oído…" |
| 2 | Second STT fail. | 2 | "Sigo sin…" |
| 3 | User says "llama a Foobar" → STT works → handler returns `Failed(ContactNotFound("Foobar"))`. | **0 (reset — STT worked)** | "No encuentro a Foobar en tus contactos." |
| 4 | Next STT fail. | 1 | "No te he oído…" |

**Pin:** STT working = counter reset, even if downstream fails. The counter is
specifically about *recognition failure*, not about *task failure*. Spec §6
flow 6 is unambiguous on this — "counter resets on any successful turn", and
the surrounding context is the recognition layer.

### Flow 5: Decision-layer failure DOES reset

| # | Step | Counter | What happens |
|---|---|---|---|
| 1 | STT fail. | 1 | "No te he oído…" |
| 2 | User says "tradúceme esto al italiano" → STT works → engine fine → validator says `UnknownFunction("translate")`. | **0 (reset — STT worked)** | "Eso no lo sé hacer todavía…" |
| 3 | Next STT fail. | 1 | "No te he oído…" |

---

## 4. Function-catalog Impact

No catalog change.

---

## 5. FSM States Touched

`Listening → ErrorRecovery → Idle`. The `failureCount` field on `ErrorRecovery`
(US-035 §5) now carries the **real** counter value (1, 2, or 3) — previously
hardcoded to 1 by SF-5.2. UI-side (SF-5.5's `ErrorRecoveryOverlay`) may use
this to render slightly different visuals (e.g., a "we're giving up" hint at
3) — pinned out of scope here; for Phase 5 the overlay just displays
`message`.

---

## 6. Android System Integrations & Permissions

No new integrations / permissions.

### 6.1 Coordinator integration points

In `AssistantCoordinator.kt`:

**Inject** `SttFailureCounter` (Hilt resolves it directly — `@Singleton @Inject
constructor()`).

**Modify** `onSttFailed`:

```kotlin
private suspend fun onSttFailed(error: CurroError) {
    val newCount = sttFailureCounter.recordFailure()
    val msg = pickFailMessage(error, newCount)
    stateMachine.transition(
        AssistantEvent.SttFailed(message = msg, failureCount = newCount),
    )
    ttsClient.speak(msg)
    if (newCount >= GIVE_UP_THRESHOLD) {
        // Counter exhausted — reset so the next mic press starts at 1, not 4.
        sttFailureCounter.recordSuccess()
    }
    stateMachine.transition(AssistantEvent.RecoverySpoken)
}

private fun pickFailMessage(error: CurroError, count: Int): String {
    // Specific errors (voice-pack missing, permission denied) keep their
    // dedicated copy regardless of count — those are NOT 'I didn't hear you'
    // failures, they're install/config issues.
    return when (error) {
        is CurroError.SttVoicePackMissing -> appContext.getString(R.string.copy_stt_no_voice_pack)
        is CurroError.PermissionDenied -> appContext.getString(R.string.copy_perm_missing_mic)
        else -> when (count) {
            1 -> appContext.getString(R.string.copy_stt_fail_1)
            2 -> appContext.getString(R.string.copy_stt_fail_2)
            else -> appContext.getString(R.string.copy_stt_fail_3)
        }
    }
}

private companion object {
    const val FAILED_TAG = "Curro/FailedCommand"
    const val GIVE_UP_THRESHOLD = 3
}
```

**Modify** `onDecisionSuccess` and `onDecisionFailure` and (success-side of)
`renderHandlerResult` and `onPermissionResult`: every place that issues a
non-`SttFailed` event toward the user must call
`sttFailureCounter.recordSuccess()` **before** the FSM transition. The clean
seam: factor the success acknowledgment into a single helper:

```kotlin
private fun acknowledgeStt() = sttFailureCounter.recordSuccess()
```

Call sites:
1. `onDecisionSuccess(...)` — before the `dispatcher.dispatch(call)` call.
2. `onDecisionFailure(...)` — before the `speakAndIdle(...)` call.
   (Decision-layer failure means STT *worked* — see Flow 5.)
3. `handleRecordAudioResult(granted = false)` — the user denied the mic
   permission; this isn't an STT failure per se, but the STT was never
   *attempted*. Pin: **do not call `recordSuccess()` here**. The counter
   stays at its current value; the next mic press will still be
   "Nth failure".
4. `handleReadContactsResult` / `handleCallPhoneResult` (either branch) —
   STT worked; reset.

The pinned principle: **`recordSuccess()` is called exactly when STT produced
a final transcript that was successfully parsed by the validator**, regardless
of what the handler then does. The seam: call it inside `onFinalTranscript`
**after** the FSM transitions to `Processing`:

```kotlin
private suspend fun onFinalTranscript(text: String) {
    stateMachine.transition(AssistantEvent.FinalTranscript(text, timeProvider.now()))
    sttFailureCounter.recordSuccess()  // STT delivered a final transcript — counter resets here.
    decideAndDispatch(text)
}
```

Pin: **this is the single call site for `recordSuccess()`** (besides the
internal one in `onSttFailed` when count reaches 3). The factoring into a
helper above is the alternative pattern; the implementer picks one — but the
brief recommends the single-call-site at `onFinalTranscript` for clarity. **Pin
the single-call-site approach.**

---

## 7. On-device-model Impact

No model impact.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/assistant/
└── SttFailureCounter.kt                          // NEW — this SF

app/src/test/java/com/curro/app/assistant/
└── SttFailureCounterTest.kt                      // NEW — 3 JVM tests
```

### 8.2 Files modified

```
app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt
  - Inject SttFailureCounter
  - Replace hardcoded failureCount = 1 in onSttFailed with the counter
  - Add recordSuccess() in onFinalTranscript (after FSM transition)
  - Pick the copy from {copy_stt_fail_1, _2, _3}

app/src/test/java/com/curro/app/assistant/AssistantCoordinatorTest.kt
  - Add 5 new tests (group N below)
```

### 8.3 Files unchanged (verified)

```
app/src/main/res/values/strings.xml
  - copy_stt_fail_1 — exists, line 80: "No te he oído bien, ¿puedes repetirlo?"
  - copy_stt_fail_2 — exists, line 82: "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto."
  - copy_stt_fail_3 — exists, line 84: "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo."
```

Pin: **no strings.xml changes in this SF.**

### 8.4 `SttFailureCounter.kt` — full sketch

```kotlin
package com.curro.app.assistant

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks consecutive STT failures for the spec §6 flow 6 policy.
 *
 * - [recordFailure] increments the count and returns the new value.
 * - [recordSuccess] resets the count to 0.
 *
 * **Caller decides which message to speak.** This class is intentionally dumb —
 * it doesn't know about strings.xml; the coordinator picks the copy based on
 * the returned count. The counter has no upper bound (returns 4, 5, 6, … if
 * the caller forgets to reset on hitting 3) — the coordinator's
 * `GIVE_UP_THRESHOLD = 3` is the policy, this class is the mechanism.
 *
 * Thread-safety: the count is mutated only from the coordinator's `Main.immediate`
 * scope. No synchronisation needed. (If a future caller wires this from a
 * different thread, switch to an `AtomicInteger`. Pin: not now.)
 *
 * Lifetime: `@Singleton`. The counter survives the launcher's process death
 * intuitively — if the process dies, "consecutive" no longer applies; starting
 * fresh at 0 is correct.
 */
@Singleton
class SttFailureCounter @Inject constructor() {
    private var count: Int = 0

    /** Call after each STT failure. Returns the new count (≥ 1). */
    fun recordFailure(): Int {
        count += 1
        return count
    }

    /** Call after any successful turn (final transcript delivered + validated). Resets to 0. */
    fun recordSuccess() {
        count = 0
    }

    /** @VisibleForTesting */
    internal fun peek(): Int = count
}
```

### 8.5 Hilt wiring

Nothing new — `@Singleton @Inject constructor()`.

### 8.6 ViewModels / Composables / Navigation / Material

Unchanged.

---

## 9. Acceptance Criteria

- [ ] `SttFailureCounter.kt` lives at `app/src/main/java/com/curro/app/assistant/`.
- [ ] `recordFailure()` returns 1, 2, 3, 4, 5 on successive calls; no upper
  bound at the class level.
- [ ] `recordSuccess()` resets to 0.
- [ ] Sequence `fail, fail, success, fail` returns `1, 2, _, 1` (the third
  call is `recordSuccess()`, no return value to assert; the fourth is `1`).
- [ ] Coordinator's `onSttFailed` picks the right copy:
  - count = 1 → `copy_stt_fail_1`
  - count = 2 → `copy_stt_fail_2`
  - count = 3 → `copy_stt_fail_3`, counter reset to 0
  - count = 4 (would-be — but reset prevents) → never reached in practice
- [ ] `SttVoicePackMissing` STT error always speaks `copy_stt_no_voice_pack`
  regardless of count, and **the counter still increments** (the recognition
  *did* fail).
- [ ] `PermissionDenied` STT error always speaks `copy_perm_missing_mic`
  regardless of count, and **the counter does NOT increment** (the user denied
  the mic — the STT was never attempted; the next press starts at the same
  count, but a `recordSuccess` did NOT happen). **Pin**: this is a small
  policy call; the brief lands on **DO increment** here for simplicity (a
  denied permission still produced a `Failed` event from the perspective of
  the user; the counter is "consecutive moments of Curro not understanding
  you"). The implementer may revisit if the user feedback suggests
  otherwise. **Pinned: increment.**
- [ ] `recordSuccess()` is called exactly once per successful turn — at
  `onFinalTranscript`, after the FSM transitions to `Processing`. Verifiable
  by counting `counter.recordSuccess()` invocations in tests.
- [ ] No new strings in `strings.xml`.
- [ ] 3 JVM tests on `SttFailureCounterTest.kt` pass.
- [ ] 5 new JVM tests on `AssistantCoordinatorTest.kt` (Group N below) pass.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.

---

## 10. Design Notes

- **Why `@Singleton`, not stored in the FSM state?** The counter is shared
  across turns — it's *consecutive* failures. Putting it in `Listening` or
  `ErrorRecovery` would mean it dies on every transition to `Idle`, which is
  the opposite of what we want. `@Singleton` matches the longevity.
- **Why not put the counter inside the FSM?** The FSM is a pure state
  container. Counters are state with mutation semantics that don't belong in
  a `MutableStateFlow`. Same reasoning as for `currentJob` (SF-5.2): the
  coordinator owns the moving parts; the FSM owns the snapshots.
- **Why is `ErrorRecovery.failureCount` carried on the state at all if the
  counter lives in `SttFailureCounter`?** Because the UI (SF-5.5's
  `ErrorRecoveryOverlay`) is keyed off the FSM state alone — it can't read
  the counter directly without taking a dependency on `assistant/`. The
  `failureCount` field travels with the state so the UI can render
  count-aware visuals (e.g., a "we're giving up" tint at 3) without reaching
  back into the coordinator. **Phase 5 doesn't use this in the UI** — but
  the field is there for Phase 6+.
- **Why `recordSuccess()` at `onFinalTranscript`, not at `Executing`?** A
  successful final transcript is the right semantic moment — STT did its
  job. If we waited until `Executing`, a handler-side failure (e.g.,
  `ContactNotFound`) would leave the counter elevated, which then makes the
  *next* STT failure sound like the 2nd in a row — wrong per Flow 4.
- **Edge: what if the user taps mic mid-`ErrorRecovery` (interrupt)?** SF-5.3's
  test covers this — the FSM transitions to `Listening`. The counter is
  unchanged (no `recordSuccess`, no `recordFailure`). If the next STT fails,
  that's the (count+1)-th. Pin: this is correct — the interrupt is the
  user's attempt to retry; the consecutive-failure semantics persist
  across the interrupt. (Not stated in the spec; pinned here.)

---

## 11. Senior-UX & Copy

No new copy. The three lines are already in `strings.xml` from US-005's COPY
fill-in (`brand-design`'s table). Each is verbatim from spec §6 flow 6.

The user-facing escalation is **explicit and humane** — by the 3rd line Curro
doesn't say "I keep failing, your fault"; it says "let's stop, press again
when you're ready" — putting the user back in control. This is spec §2's
"Fallar de forma comprensible" in action.

---

## 12. Performance Considerations

`SttFailureCounter` is one int + two methods. Zero perf impact.

---

## 13. Testing Requirements

### 13.1 `SttFailureCounterTest.kt` — JVM, 3 cases

```kotlin
class SttFailureCounterTest {
    @Test fun `recordFailure increments and returns the new count`() {
        val c = SttFailureCounter()
        assertEquals(1, c.recordFailure())
        assertEquals(2, c.recordFailure())
        assertEquals(3, c.recordFailure())
        assertEquals(4, c.recordFailure())
        assertEquals(5, c.recordFailure())
    }

    @Test fun `recordSuccess resets to zero`() {
        val c = SttFailureCounter()
        c.recordFailure(); c.recordFailure()
        c.recordSuccess()
        assertEquals(1, c.recordFailure())
    }

    @Test fun `fail, fail, success, fail sequence returns 1, 2, 1`() {
        val c = SttFailureCounter()
        assertEquals(1, c.recordFailure())
        assertEquals(2, c.recordFailure())
        c.recordSuccess()
        assertEquals(1, c.recordFailure())
    }
}
```

### 13.2 `AssistantCoordinatorTest.kt` — 5 new cases (Group N)

```kotlin
// Group N — Consecutive-STT-failure policy

@Test fun `1st STT fail speaks copy_stt_fail_1 and sets failureCount=1`() {
    // setup: counter = 0. drive a turn whose STT returns Failed(SttNoMatch).
    // assert: ttsClient.spoken == [copy_stt_fail_1]; ErrorRecovery.failureCount == 1.
}

@Test fun `2nd STT fail in same session speaks copy_stt_fail_2 and sets failureCount=2`() {
    // setup: drive 1st fail; then 2nd fail.
    // assert: second utterance == copy_stt_fail_2; ErrorRecovery.failureCount == 2.
}

@Test fun `3rd STT fail speaks copy_stt_fail_3 then counter resets`() {
    // setup: drive 1st, 2nd, 3rd fail.
    // assert: third utterance == copy_stt_fail_3; ErrorRecovery.failureCount == 3.
    // assert: counter.peek() == 0 after the 3rd transition.
}

@Test fun `Successful turn after 2 fails resets counter`() {
    // setup: 1st fail, 2nd fail. Then a successful tell_time turn.
    // assert: after the success, counter.peek() == 0.
    // next: drive a 3rd STT fail. assert: utterance == copy_stt_fail_1 (NOT _3).
}

@Test fun `SttVoicePackMissing speaks dedicated copy and still increments counter`() {
    // setup: counter = 0. STT returns Failed(SttVoicePackMissing).
    // assert: utterance == copy_stt_no_voice_pack (NOT copy_stt_fail_1).
    // assert: counter.peek() == 1.
}
```

### 13.3 No instrumented tests in this SF

SF-5.3's instrumented test exercises the cancel path; SF-5.4 is purely the
counter mechanism + copy choice, both JVM-testable.

---

## 14. Implementation Notes

**PM Owner wrote**: every section.

**Architect / voice-pipeline-engineer fills in (during implementation)**:
whether to inline `recordSuccess()` at `onFinalTranscript` or wrap in
`acknowledgeStt()` — pinned: inline.

**Commit message (pinned)**:

```
feat: add 1/2/3 STT-failure recovery policy (US-038 / SF-5.4)

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft — pinned the counter API, the single-call-site reset, the increment-on-VoicePackMissing decision, and the test list. |
