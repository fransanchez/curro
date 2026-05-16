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
        currentJob?.cancel()       // structural cancellation
        ttsClient.stop()            // halt in-flight TTS playback
        sttClient.cancel()          // halt in-flight STT session
        stateMachine.transition(AssistantEvent.MicPressed(timeProvider.now()))
        // … rest of the listen-loop bootstrap …
    }
}
```

In `AssistantCoordinator.kt` this is factored into a single private
`cancelInFlight()` helper that **`onMicPressed`** and **`onHomePressed`** both
call as their first line — see SF-5.6's HOME-press reset for why the helper is
shared (the rule applies to both entry points).

## Why HERE, not somewhere else

### Not in `LauncherViewModel`

The VM is one of many callers. If the coordinator were ever invoked from
outside the VM (e.g., a future `InCallService`, the foreground service, or
`MainActivity.onNewIntent` for HOME-press), each caller would need to remember
to cancel. Putting the glue in the coordinator means **every caller of
`onMicPressed`/`onHomePressed` gets cancellation for free.**

### Not inside `stateMachine.transition`

The FSM is a *pure state container* — it has no `Job`, no `TtsClient`, no
`SttClient` references. Moving cancellation into the FSM would entangle the
state container with the world. That's the opposite of what we want for
testability.

### Not as multiple separate "cancel" call sites

Splitting the three cancel calls across multiple methods invites the bug of
forgetting one of them in a future refactor. Inlining the trio at every public
entry point (via the `cancelInFlight()` helper) makes the rule structurally
unforgettable.

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
- `TextToSpeech.stop()` to release the audio output now (not 100 ms from now
  when the coroutine notices).

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
three lines via `cancelInFlight()`.

## Threading

`AssistantCoordinator.scope` is built from `@ApplicationScope` =
`SupervisorJob + Main.immediate`. **All three cancellation calls run on the
main thread** — which matches:

- `SpeechRecognizer.cancel()` is documented as having to be called from the
  same thread that called `startListening()`. `SystemSttClient` uses
  `Main.immediate` for the entire `callbackFlow` body (see US-015 §11 and
  the prior incidents 796b5f4 / b77d789); the coordinator's cancel call
  honours that.
- `TextToSpeech.stop()` is documented as thread-safe but is universally
  observed as fast on the main thread; no migration needed.
- `Job.cancel()` is thread-safe.

## Cross-references

- Spec §6 — the diagram + the load-bearing sentence.
- `voice-interaction` skill, rule 1.
- `master-plan.md` — Phase 5 Risks, item (a): "the interrupt rule is the
  easiest thing to forget when refactoring later."
- `docs/briefs/US-037-interrupt-by-button.md` — this SF's test list.
- `app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt` —
  `cancelInFlight()` and the two callers (`onMicPressed`, `onHomePressed`).
- `app/src/test/java/com/curro/app/assistant/AssistantCoordinatorTest.kt`,
  Group F — the 5 JVM tests that exist solely to lock the rule in place.
