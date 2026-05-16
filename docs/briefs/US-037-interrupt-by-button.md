# US-037 — SF-5.3 · Interrupt-by-button (the rule that breaks if missed)

> **Spec trace:** spec §6 — the closing diagram paragraph: *"Cualquier estado
> puede ser interrumpido por una nueva pulsación de botón, que cancela lo en
> curso y vuelve a `listening`. Esto es importante: tu padre debe poder cortar
> a Curro si está leyendo algo largo y prefiere otra cosa."* The *load-bearing*
> sentence of Phase 5 (master-plan §Phase 5 Risks: "the interrupt rule is the
> easiest thing to forget when refactoring later — add a test for every state's
> 'press-while-in-X' path"). Spec §13: "Si la latencia o los fallos lo frustran
> y deja de usarla" — the prototype is *invalidated* if the user can't cut
> Curro off.
> **Master-plan:** SF-5.3.
> **Phase:** 5 — State machine & interruption.
> **Depends on:** US-035 (FSM), US-036 (coordinator — the cancellation
> mechanism is already wired there).
> **Size:** M.
> **Skills:** `voice-interaction` (rule 1 — interrupt-by-button; rule 7 —
> overlays state-driven), `launcher-ui`, `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Interrupt-by-button — mic press in any state cancels in-flight work and re-enters `listening` |
| **US ID** | US-037 |
| **Phase** | 5 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | voice-pipeline-engineer |

---

## 1. Summary

This SF is **mostly tests and architectural documentation**. The cancellation
glue itself lives at the top of `AssistantCoordinator.onMicPressed()` (SF-5.2):

```kotlin
fun onMicPressed() {
    scope.launch {
        currentJob?.cancel()       // structural cancellation
        ttsClient.stop()            // halt in-flight TTS playback
        sttClient.cancel()          // halt in-flight STT session
        stateMachine.transition(AssistantEvent.MicPressed(timeProvider.now()))
        // …
    }
}
```

What this SF does:

1. **Verifies (and patches if needed) `TtsClient.stop()` and `SttClient.cancel()`**
   are real — `TtsClient.stop()` calls `TextToSpeech.stop()` (per US-017),
   `SttClient.cancel()` calls `SpeechRecognizer.cancel()` (per US-016). Both
   exist on disk today. This SF's first task is verification on real hardware
   that they actually halt within ~150 ms — the spec's acceptance bar.
2. **Adds five new JVM tests** to `AssistantCoordinatorTest` — one per non-Idle
   state, asserting that `onMicPressed()` cancels the in-flight Job, stops TTS,
   stops STT (where applicable), and transitions to `Listening`.
3. **Adds one instrumented Compose UI test** in `androidTest/` —
   `LauncherInterruptInstrumentedTest.kt` — that runs on the emulator (and on
   the Redmi 15 manually): start a long TTS utterance in `Executing`, tap the
   mic, assert the previous utterance halts within ~150 ms wall-clock and the
   `ListeningOverlay` appears.
4. **Adds the architectural design note**
   `docs/architecture/interrupt-by-button.md` documenting WHY the cancellation
   lives at the top of `onMicPressed()` and not elsewhere — so any future
   refactor lands on the doc before re-architecting.

Why this matters for *this* user: spec §13's validation question is exactly
this. If the user starts a `read_all_unread_whatsapp` of eight messages and
realises after three he wants to *call* Pepito, he must be able to cut Curro
off mid-read. A 1-second-delayed cancellation would frustrate him; a 5-second
one (the worst-case current Android TTS queue) would end the prototype.

---

## 2. Scope

**In scope:**

- Verification on a real Redmi 15 that:
  - `TextToSpeech.stop()` (called from `SystemTtsClient.stop()`) halts a
    playing utterance within ~150 ms; the `UtteranceProgressListener.onDone`
    fires with `interrupted = true` (Android API behaviour).
  - `SpeechRecognizer.cancel()` (called from `SystemSttClient.cancel()`)
    halts the recogniser session. If either doesn't behave, **the brief
    flags a follow-up patch SF** — but as of US-017 / US-016 these are
    implemented; we expect they Just Work.
- Threading verification: both `stop()` and `cancel()` may need to be called
  from a specific thread per Android API contract. Pin: `TextToSpeech.stop()`
  is documented as thread-safe; `SpeechRecognizer.cancel()` **must be called
  from the same thread that called `startListening()`**, which is the main
  thread. The coordinator runs on `Main.immediate` (per SF-5.2's pinned
  threading), so the call is correct. **Verify on hardware**.
- 5 new JVM tests in `AssistantCoordinatorTest.kt`:
  - `mic press in Listening → re-enters Listening, cancels STT`
  - `mic press in Processing (FunctionGemma running) → cancels job, Listening`
  - `mic press in Confirming → cancels confirm wait, Listening`
  - `mic press in Executing (TTS speaking) → stops TTS, cancels job, Listening`
  - `mic press in ErrorRecovery (TTS speaking a recovery line) → stops TTS, Listening`
- 1 new instrumented Compose UI test in `androidTest/`:
  `app/src/androidTest/java/com/curro/app/presentation/launcher/LauncherInterruptInstrumentedTest.kt`
  — uses Hilt test runner + Compose test rule + a real `SystemTtsClient`
  (the test verifies *real* TTS-stop latency, not a fake).
- 1 new design note: `docs/architecture/interrupt-by-button.md` (`mkdir -p
  docs/architecture` first — the directory doesn't exist).

**Out of scope:**

- The full state-driven overlay split (SF-5.5).
- The HOME-press handling (SF-5.6 has its own from-every-state tests).
- A real-hardware automated benchmark of the 150-ms latency (manual measurement
  on the Redmi 15 is sufficient for the prototype). Pin: **the instrumented test
  asserts the post-cancel state, not a hard wall-clock latency** — JUnit5 on
  Android Test can't guarantee millisecond timing; the wall-clock SLA is verified
  manually in the smoke test list of US-036 §13.3 item (8).

---

## 3. User Flows

### Flow 1: Cut Curro off while he's reading a long message

| # | User action | Curro state | What happens |
|---|---|---|---|
| 1 | "Léeme los mensajes" → Curro starts: "Tienes 3 mensajes de Pepito y 1 mensaje de Lucía. Empiezo con Pepito: 'Te espero a las siete'. 'Trae el pan'…" | `Executing("Tienes 3 mensajes…", screen=null)` | TTS playback in progress (the suspended `ttsClient.speak(...)` call from the coordinator). |
| 2 | User taps the mic button while Curro is still speaking. | (about to transition) | `LauncherEvent.MicPressed` → `coordinator.onMicPressed()`. |
| 3 | Coordinator's `onMicPressed`: `currentJob?.cancel()`, `ttsClient.stop()`, `sttClient.cancel()`. TTS halts within ~150 ms on the Redmi 15. | (transitioning) | The previously-suspended `speak(...)` returns (Android's `UtteranceProgressListener.onDone(interrupted=true)`). |
| 4 | `transition(MicPressed(now))`. | `Listening("", now)` | `ListeningOverlay` re-shows; previous executing overlay disappears. |
| 5 | User says "llama a Pepito" → continues as normal. | `Listening → Processing → Executing` (a new call) | The cancelled turn is gone. No state leaked. |

### Flow 2: Cut Curro off during confirmation (Phase-6 preview)

Phase 5 doesn't reach `Confirming` in the happy path (auto-confirm short-circuit
per SF-5.2 §10). But the FSM accepts `MicPressed` from `Confirming` — this SF's
test covers that path so Phase 6 doesn't have to.

| # | User action | Curro state | What happens |
|---|---|---|---|
| 1 | (Phase 6 path:) Curro is in `Confirming("¿Llamo a Pepe Martínez?", expiresAtMs, …)`. | `Confirming(…)` | |
| 2 | User taps mic. | | Same: cancel + stop + transition to `Listening`. |

### Flow 3: Cut Curro off during STT (rare — user changes mind)

The user said "léeme" then immediately realises he meant "abre WhatsApp". He
taps the mic again before the STT-final fires.

| # | User action | Curro state | What happens |
|---|---|---|---|
| 1 | First press, says "léeme". | `Listening("léeme", …)` | STT has emitted a partial. |
| 2 | User taps mic again. | | `currentJob?.cancel()` → cancels the STT collect; `sttClient.cancel()` → `SpeechRecognizer.cancel()`. |
| 3 | `transition(MicPressed(new now))`. | `Listening("", new now)` | A fresh STT session begins. |

### Flow 4: Cut Curro off during processing (model running)

The user pressed and said "tradúceme esto al italiano" → STT final → coordinator
is in `decideAndDispatch` → `engine.decide` is running (~300–500 ms). User
realises and taps again.

| # | User action | Curro state | What happens |
|---|---|---|---|
| 1 | STT final; coordinator transitions to `Processing`. | `Processing("tradúceme…", …)` | `engine.decide` is a suspending call inside the coordinator scope. |
| 2 | User taps mic. | | `currentJob?.cancel()` → the suspended `engine.decide` is cancelled (the `withContext(io)` inside the engine is cooperative). |
| 3 | `transition(MicPressed)`. | `Listening` | Fresh turn. |

---

## 4. Function-catalog Impact

No catalog change.

---

## 5. FSM States Touched

**All non-Idle states** — `Listening`, `Processing`, `Confirming`, `Executing`,
`ErrorRecovery`. Each must accept `MicPressed` and re-enter `Listening`. This is
enforced by US-035's transition table (where `MicPressed` is valid in every
state) — this SF *verifies* that the **coordinator** honours it by cancelling
in-flight work, not just the FSM transition.

---

## 6. Android System Integrations & Permissions

No new permissions.

| Integration | Why | Notes |
|---|---|---|
| `TextToSpeech.stop()` | Halt in-flight TTS playback. | Android API: thread-safe; flushes the utterance queue. The `UtteranceProgressListener.onDone(utteranceId, interrupted=true)` is delivered to the listener installed by `SystemTtsClient`. |
| `SpeechRecognizer.cancel()` | Halt in-flight STT session. | Android API: **must be called from the same thread that called `startListening()`**. The coordinator runs on `Main.immediate`, which is also the thread `SystemSttClient` runs on. Pin: verify in code review. |
| `Job.cancel()` | Structural concurrency cancellation. | Cooperative — the suspending `engine.decide`, `validator.parseAndValidate`, `dispatcher.dispatch`, `ttsClient.speak` calls all check for cancellation at suspension points. `engine.decide` uses `withContext(io)` (cooperative); the dispatcher's `runCatching { handler.handle(call) }` is wrapped — pin: the **handler** must respect cancellation. Each Phase-4 handler ships with a `currentCoroutineContext().ensureActive()` at each await point per Kotlin idiom — this is already the case for the existing handlers via `withContext(io)` / `delay` etc. |

---

## 7. On-device-model Impact

No prompt change. The pinned behaviour: `engine.decide` IS cancellable
(it suspends inside MediaPipe's `generateResponse` — the SF-3.6 implementation
uses `withContext(io) { … }`, and MediaPipe's blocking call is interrupted on
`Thread.interrupt()` from Kotlin coroutine cancellation). **Verify on hardware**
that cancelling mid-decode actually frees the MediaPipe session — pin: if a
regression in `FunctionGemmaEngine` later swallows cancellation, surface it as
an immediate hotfix (the model keeping the CPU busy after the user has
moved on is a battery + UX bug).

---

## 8. Android Specification

### 8.1 Files added

```
app/src/androidTest/java/com/curro/app/presentation/launcher/
└── LauncherInterruptInstrumentedTest.kt           // NEW — this SF

docs/architecture/
└── interrupt-by-button.md                         // NEW — design note
```

### 8.2 Files modified

```
app/src/test/java/com/curro/app/assistant/
└── AssistantCoordinatorTest.kt                    // +5 new test methods (groups F.1–F.5)
```

### 8.3 Files unchanged (verified, no patch needed)

```
app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt   // cancellation already wired by SF-5.2
app/src/main/java/com/curro/app/data/voice/SystemTtsClient.kt       // .stop() exists, calls TextToSpeech.stop()
app/src/main/java/com/curro/app/data/voice/SystemSttClient.kt       // .cancel() exists, calls SpeechRecognizer.cancel()
app/src/main/java/com/curro/app/domain/repository/TtsClient.kt      // fun stop() declared
app/src/main/java/com/curro/app/domain/repository/SttClient.kt      // fun cancel() declared
```

### 8.4 `docs/architecture/interrupt-by-button.md` — pinned content

```markdown
# Interrupt-by-button: why it lives where it does

> **Status:** load-bearing. Don't move the cancellation glue without re-reading
> this doc.

## The rule (spec §6)

> Cualquier estado puede ser interrumpido por una nueva pulsación de botón,
> que cancela lo en curso y vuelve a `listening`.

A mic press in **any** state cancels:
- the in-flight coordinator turn (`currentJob.cancel()`);
- TTS playback (`ttsClient.stop()` → `TextToSpeech.stop()`);
- STT recognition (`sttClient.cancel()` → `SpeechRecognizer.cancel()`);

and transitions to `Listening`. **In that order, in that one place.**

## Where the glue lives

`AssistantCoordinator.onMicPressed()` — at the top, before the FSM transition:

```kotlin
fun onMicPressed() {
    scope.launch {
        currentJob?.cancel()
        ttsClient.stop()
        sttClient.cancel()
        stateMachine.transition(AssistantEvent.MicPressed(timeProvider.now()))
        // … rest of the listen-loop bootstrap …
    }
}
```

## Why HERE, not somewhere else

### Not in `LauncherViewModel`
The VM is one of many callers. If the coordinator were ever invoked from
outside the VM (e.g., a future `InCallService`, the foreground service,
or `MainActivity.onNewIntent` for HOME-press), each caller would need to
remember to cancel. Putting the glue in the coordinator means **every caller
of `onMicPressed` gets cancellation for free.**

### Not inside `stateMachine.transition`
The FSM is a *pure state container* — it has no `Job`, no `TtsClient`, no
`SttClient` references. Moving cancellation into the FSM would entangle the
state container with the world. That's the opposite of what we want for
testability.

### Not as a separate "cancel everything" function
Splitting `cancelInFlight()` from `onMicPressed()` invites the bug of
forgetting the cancel call in one of several places. Inlining at the entry
point makes the rule structurally unforgettable.

## Why `currentJob?.cancel()` FIRST

The Job's children (the STT collect, the TTS speak suspension, the engine
decide call) all check cancellation at their suspension points. Cancelling
the parent Job first means by the time `ttsClient.stop()` runs, the
suspended `ttsClient.speak(...)` is already cancelled — `.stop()` then just
ensures the audio output device is released.

The reverse order (`stop()` then `cancel()`) is **fine in practice** but
less self-documenting: a reader doesn't immediately see that the
cancellation drives the rest.

## Why both `cancel()` AND `stop()` / `cancel()`

`Job.cancel()` is **cooperative** — it sets a flag; the suspended call only
notices on the next suspension point. Android's `TextToSpeech.stop()` is
**imperative** — it halts the audio device immediately. We need both:
- `Job.cancel()` to unwind the coroutine structure cleanly.
- `TextToSpeech.stop()` to release the audio output now (not 100 ms from
  now when the coroutine notices).

`SpeechRecognizer.cancel()` similarly halts the audio capture device
immediately.

## The 150-ms acceptance bar

On the Redmi 15 (Snapdragon 6s Gen 3, Android 15), `TextToSpeech.stop()`
halts the system Spanish voice within ~150 ms. This is verified manually
during the SF-5.3 smoke test. If a regression ever pushes it above ~300 ms,
the prototype is invalidated — investigate immediately.

## How to extend this

The rule applies to **every entry point that means "the user wants Curro's
attention now"**:
- `onMicPressed()` (this doc).
- `onHomePressed()` (SF-5.6) — same shape, same three lines, then
  `transition(HomePressed)` instead of `MicPressed`.

If a future SF adds a third entry point (e.g., a hotword), it gets the same
three lines.

## Cross-references

- Spec §6 — the diagram + the load-bearing sentence.
- `voice-interaction` skill, rule 1.
- `master-plan.md` — Phase 5 Risks, item (a).
- `docs/briefs/US-037-interrupt-by-button.md` — this SF's test list.
```

### 8.5 `LauncherInterruptInstrumentedTest.kt` — pinned skeleton

```kotlin
package com.curro.app.presentation.launcher

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.curro.app.MainActivity
import com.curro.app.assistant.AssistantCoordinator
import com.curro.app.assistant.AssistantState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@HiltAndroidTest
class LauncherInterruptInstrumentedTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()
    @get:Rule(order = 2) val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Inject lateinit var coordinator: AssistantCoordinator

    @Test
    fun micPressDuringExecutingHaltsTtsAndReentersListening() = runBlocking {
        hiltRule.inject()

        // Force the coordinator into Executing with a long Spanish utterance.
        // We use a deliberately long string so the TTS playback is observable.
        val longUtterance = "Tienes muchísimos mensajes, te los voy a leer todos uno por uno…"
        // Drive the FSM directly — the SF-5.2 coordinator API exposes onMicPressed but
        // not "go to Executing now". For an instrumented test we use a test helper
        // that injects a synthetic FunctionCall via a fake handler — pin: the
        // implementer wires this in `androidTest/` by registering a
        // FakeLongTalkHandler that emits Spoken(longUtterance) when invoked.
        coordinator.testForceExecuting(longUtterance) // implementer adds this @VisibleForTesting helper

        composeRule.waitUntil(timeoutMillis = 1_000) {
            coordinator.state.value is AssistantState.Executing
        }

        // Tap the mic.
        val elapsed = measureTime {
            composeRule.onNodeWithText("CURRO").performClick()
            composeRule.waitUntil(timeoutMillis = 1_000) {
                coordinator.state.value is AssistantState.Listening
            }
        }
        // Don't assert on the wall-clock — instrumented tests can't promise milliseconds.
        // Just record it for the test log; the 150-ms bar is verified manually.
        println("Mic-press → Listening latency: $elapsed")

        // Assert on the resulting state.
        assertTrue(coordinator.state.value is AssistantState.Listening)
    }
}
```

> The `coordinator.testForceExecuting(...)` helper is pinned **`@VisibleForTesting`** —
> the implementer adds a small method on the coordinator gated to `BuildConfig.DEBUG`
> or annotated. Alternative (cleaner): the test installs a `FakeLongTalkHandler` via
> Hilt test bindings and drives the full happy-path
> (`onMicPressed → fake STT final → fake decision → handler → speak`). The brief
> leaves the seam choice to the implementer; **pin the simpler `testForceExecuting`
> for Phase 5** (it's only a test-only entry point) — Phase 8's diagnostics page can
> remove it if it ever leaks.

### 8.6 The 5 JVM tests (appended to `AssistantCoordinatorTest`)

```kotlin
// Group F — Interrupt-by-button (one per non-Idle state).

@Test fun `mic press while Listening cancels STT and re-enters Listening with fresh timestamp`() {
    // setup: drive a turn to Listening("hola", t0). Tap mic at t1.
    // assert: sttClient.wasCancelled == true; new state is Listening("", t1).
}

@Test fun `mic press while Processing cancels in-flight engine decode`() {
    // setup: make FakeFunctionCallEngine suspend forever on decide().
    // drive to Processing. Tap mic.
    // assert: the engine's job is cancelled; state is Listening.
}

@Test fun `mic press while Confirming cancels confirm wait`() {
    // setup: handler returns NeedsConfirmation that suspends in onConfirm.
    // But Phase 5 auto-confirms — so to reach Confirming, the test must
    // bypass the short-circuit by driving the FSM directly via the
    // stateMachine. PIN: the test injects AssistantStateMachine, drives
    // it to Confirming, then calls coordinator.onMicPressed() and asserts.
}

@Test fun `mic press while Executing stops TTS and re-enters Listening`() {
    // setup: handler returns Spoken("very long line"); ttsClient.speak suspends.
    // tap mic. assert: ttsClient.wasStopped == true; state is Listening.
}

@Test fun `mic press while ErrorRecovery stops the recovery TTS`() {
    // setup: drive an SttFailed → ErrorRecovery; ttsClient.speak suspends.
    // tap mic. assert: ttsClient.wasStopped == true; state is Listening.
}
```

Each test uses `FakeTtsClient` with a `var wasStopped: Boolean` and a settable
"the speak call returns when I let it" mechanism (e.g., a `CompletableDeferred`
released by `stop()`). Same `FakeSttClient` pattern with `var wasCancelled`.

### 8.7 Hilt wiring

Nothing new.

### 8.8 ViewModels / Composables / Navigation / Material

Unchanged.

---

## 9. Acceptance Criteria

- [ ] `TtsClient.stop()` verified on the Redmi 15 to halt a playing utterance
  within ~150 ms; the `UtteranceProgressListener.onDone(interrupted=true)`
  is observable.
- [ ] `SttClient.cancel()` verified on the Redmi 15 to halt a recognizer
  session (no further partials emitted).
- [ ] 5 new JVM tests (Group F, §8.6) pass.
- [ ] 1 new instrumented test (`LauncherInterruptInstrumentedTest`) passes
  on the emulator.
- [ ] The design note `docs/architecture/interrupt-by-button.md` exists and
  contains the four sections (the rule, where the glue lives, why-here,
  why-both-cancel-and-stop, the 150-ms bar, cross-references).
- [ ] Manual on-device smoke (Redmi 15): start a `read_all_unread_whatsapp`
  with ≥ 3 messages, tap mic mid-read; observe TTS halt and `ListeningOverlay`
  appearing — wall-clock perceptual latency feels "immediate" (< 250 ms).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest`
  green; `./gradlew connectedDebugAndroidTest` green on emulator.

---

## 10. Design Notes

- The architectural enforcement of the interrupt rule is **structural**:
  `currentJob?.cancel()` lives at the top of `onMicPressed`, not bolted on
  per-state. The 5 JVM tests verify the *consequence* of the structural
  rule (each non-Idle state cancels properly), not the rule itself.
- The instrumented test is the **only** place we can measure real
  `TextToSpeech.stop()` behaviour — the fake TTS in JVM tests can't tell
  us that the audio device actually releases on hardware.
- The design note is the **first** file in `docs/architecture/`. Subsequent
  Phase 5/6/7 notes (e.g., "why the FSM is `@Singleton`", "why the
  coordinator runs on `Main.immediate`") may land alongside it.
- **What we are NOT doing**: a Compose-side cancellation (e.g., a
  `LaunchedEffect(state)` that calls `coordinator.onMicPressed`). The UI is
  driven *by* the state; it doesn't drive cancellations. Reversing this is
  the classic Compose-side-effect trap; the brief explicitly rules it out.

---

## 11. Senior-UX & Copy

No new copy. Visually, the `ListeningOverlay` (existing Phase-2 component,
keyed off the new `AssistantState.Listening` per SF-5.2's adapter) reappears
the instant the mic press completes — the user sees the blue tint + "Te
escucho…" exactly as they would for a cold mic press. This is correct: the
user shouldn't perceive a difference between "fresh start" and "interrupt
restart"; the state is `Listening` either way.

---

## 12. Performance Considerations

- `currentJob?.cancel()` is O(1). Children unwind via structured concurrency.
- `TextToSpeech.stop()` is non-blocking; returns immediately. The audio
  device frees on the next audio-thread tick (10–30 ms).
- `SpeechRecognizer.cancel()` is similarly non-blocking.
- The full mic-press → `Listening` transition should complete in ≪ 150 ms
  on the Redmi 15. Anything above 300 ms is a regression.

---

## 13. Testing Requirements

See §8.5 (instrumented) + §8.6 (5 JVM tests). The other Phase-5 SFs already
cover the FSM-side correctness; this SF specifically verifies the
**coordinator-side cancellation** wiring.

---

## 14. Implementation Notes

**PM Owner wrote**: every section.

**Architect / voice-pipeline-engineer fills in (during implementation)**:
the precise `testForceExecuting` seam shape; whether the
`FakeLongTalkHandler` Hilt-test-binding alternative is preferred; any
threading subtlety surfaced on the Redmi 15.

**Commit message (pinned)**:

```
test: harden interrupt-by-button across all FSM states (US-037 / SF-5.3)

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft — pinned the 5 JVM tests, the instrumented test shape, and the design-note content. |
