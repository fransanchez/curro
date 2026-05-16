# Brief — US-042 / SF-6.2: `ConfirmationOverlay` (SÍ / NO buttons + voice yes/no + 10-s silence)

## Metadata

| Field | Value |
|---|---|
| **Feature** | `ConfirmationOverlay` — the real `Confirming` state UI + voice yes/no STT pass + 10-s timeout |
| **US ID** | US-042 |
| **SF ID** | SF-6.2 |
| **Phase** | 6 — Confidence-graded confirmation |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst (Opus) |
| **Implementer** | voice-pipeline-engineer (Opus) |
| **Size** | M |
| **Depends on** | US-041 (SF-6.1 — `ConfidencePolicy` + DataStore), US-039 (SF-5.5 — state-driven overlay routing) |
| **Unblocks** | US-043 (SF-6.3 — disambiguation reuses the `Confirming` plumbing + 10-s timer) |

---

## Summary

SF-6.1 made `Confirming` reachable; SF-6.2 makes it **usable**.

After SF-6.2:
- The Phase-5 `Confirming` branch in `LauncherPlaceholderScreen` (currently
  `Unit`) renders a new `ConfirmationOverlay` showing the resolved target in big
  text + a `BigYesNoRow` (SÍ / NO, the brick already built in
  `presentation/common/BigYesNoRow.kt`).
- When the FSM enters `Confirming`, the coordinator starts a **constrained-
  vocabulary STT pass** that listens for "sí / vale / claro" (→ yes) or
  "no / cancela / no llames / déjalo" (→ no), in parallel with a **10-s
  silence timer**.
- SÍ tap, voice "sí", and the timer expiring with neither resolution map to the
  three exit transitions out of `Confirming`:
  - **SÍ / yes** → `UserConfirmed(speech = copy_calling_confirmed, screen = null)`
    → `Executing` → runs `pendingAction.onConfirm()` → `Idle`.
  - **NO / no** → `UserRejected` (already in the FSM event set) →
    `Executing(speech = copy_cancel_no_call)` (so the line is spoken with TTS the
    spec-correct way) → `Idle`. *(Implementation pin: `UserRejected` in the FSM
    today goes `Confirming → Idle` directly without speaking — fix this to route
    through Executing for the spoken feedback. See "FSM States Touched" for the
    pinned change.)*
  - **10-s silence** → `ConfirmationTimedOut` → `Executing(speech =
    copy_confirm_timeout)` → `Idle`.

User benefit (Fran's father): when Curro is not sure, he asks — and Curro waits
without nagging. SÍ/NO is huge, tactile, and ALSO answerable by voice without
even looking at the screen. Silence is treated as polite cancellation, not as
ambiguity to re-prompt — "Cancelo entonces" is the senior-correct default.

Spec source: §4.3, §6 flow 2 (verbatim — the 10-s timeout, the "Vale, no llamo"
exit, the SÍ/NO buttons, the spoken+shown rule), §2 ("personalidad cálida y
andaluza, no servil"), §11 ("dos botones gigantes").

---

## Scope

### In scope

- New `presentation/assistant/ConfirmationOverlay.kt`. Stateless `Content`
  composable + a thin wrapper. `@Preview`s (light / dark / 1.5× / 2.0× font).
- Wire the Phase-5 `Unit` branch in `LauncherPlaceholderScreen.kt`'s overlay
  routing to `ConfirmationOverlay(...)`.
- Extend the `SttClient` interface with a second listening mode tailored to
  short yes/no confirmations (constrained vocabulary, ~5-s timeout). Pinned:
  add a separate method `listenForConfirmation(): Flow<ConfirmationVoice>` —
  do NOT overload `listen()` because the partial-event semantics differ.
- Coordinator additions:
  - `confirmationListenerJob: Job?` — the secondary STT for SÍ/NO.
  - `confirmationTimeoutJob: Job?` — the 10-s timer.
  - `onUserConfirmed()` body — runs the `PendingAction.onConfirm` lambda,
    transitions to `Executing(speech = copy_calling_confirmed)` (or whatever the
    handler's `Spoken` returns), then `Idle`.
  - `onUserRejected()` body — transitions to `Executing(speech =
    copy_cancel_no_call)` then `Idle`. **No call placed; no handler dispatched.**
  - A private `onConfirmationVoice(ConfirmationVoice)` that fans out to
    `onUserConfirmed` / `onUserRejected` / a no-op for unrecognized.
  - A private `startConfirmationTimer(expiresAtMs)` that races with the STT
    pass; whichever fires first cancels the other.
  - The existing `cancelInFlight()` extended to also cancel the
    `confirmationListenerJob` + `confirmationTimeoutJob` (so the interrupt-by-
    button rule from SF-5.3 still works while in `Confirming`).
- `LauncherViewModel` — new `LauncherEvent.UserConfirmed` /
  `LauncherEvent.UserRejected` fired from the overlay's `onYes` / `onNo`,
  forwarded to the coordinator.
- Strings: verify `copy_confirm_call` (exists), `copy_calling_confirmed`
  (exists), `copy_cancel_no_call` (exists), `copy_confirm_timeout` (exists).
  All four are on-disk already (lines 24, 46, 30, 34 of `strings.xml`).
- Constrained-vocabulary token list (Spanish): pin a small set per
  `ConfirmationVoice` value below.
- Tests: 5 new `AssistantCoordinatorTest` cases, 1 new `SttClientTest` case (or
  Robolectric test against the impl) for the new method, Compose UI tests on
  `ConfirmationOverlayContent` (dimension + tap-fires-event).

### Out of scope

- The `ContactPickerOverlay` for the 3-Marías case → SF-6.3.
- The "always confirm" toggle plumbing from `SettingsRepository` to the
  coordinator → SF-6.4.
- A confirmation overlay for any function other than `call_contact` — Phase-2's
  `send_whatsapp_reply` reuses the same overlay but is built then, not now.
- Phone-number disambiguation (a contact with multiple numbers) — pinned out of
  Phase 6.
- The constrained-vocabulary STT pass for the **picker** (which lists candidate
  names + ordinals) — that's SF-6.3.

---

## User Flows

### Flow 1 — Medium-confidence call, user confirms by tap

1. User taps mic → `Listening`.
2. "Llámame a Pepe" → STT → `Processing` → `ConfidencePolicy` →
   `Confirm` → coordinator emits `FunctionCallReady(needsConfirmation = true,
   prompt = "¿Llamo a Pepe?", expiresAtMs = now + 10_000, pendingAction = …)`.
3. FSM → `Confirming(...)`. Overlay paints: "¿Llamo a Pepe?" + `BigYesNoRow`.
   TTS speaks "¿Llamo a Pepe?". Coordinator launches:
   - `confirmationListenerJob = scope.launch { sttClient.listenForConfirmation().collect(...) }`
   - `confirmationTimeoutJob = scope.launch { delay(10_000); … if still in Confirming → ConfirmationTimedOut }`
4. User taps SÍ → `LauncherEvent.UserConfirmed` → VM → `coordinator.onUserConfirmed()`.
5. Coordinator cancels both jobs, runs `pendingAction.onConfirm()` (which calls
   `dispatcher.dispatch(originalCall)` and returns `HandlerResult.Spoken("Llamando a
   Pepe.")`). Transition → `Executing("Vale, llamando.", null)`. TTS speaks "Vale,
   llamando.". Then "Llamando a Pepe." comes from the handler's result (or — pin —
   use the `copy_calling_confirmed` line alone, see "Pin: speech on confirmation").
6. FSM → `Idle`.

### Flow 2 — Medium-confidence call, user rejects by tap

1. Steps 1–3 as above.
4. User taps NO → `LauncherEvent.UserRejected` → VM → `coordinator.onUserRejected()`.
5. Coordinator cancels both jobs. Transition → `Executing("Vale, no llamo.",
   null)` (via the same `UserConfirmed`-style event — see FSM change below). TTS
   speaks "Vale, no llamo.". No `dispatcher.dispatch`. No call placed.
6. FSM → `Idle`.

### Flow 3 — Medium-confidence call, user says "sí" by voice

1. Steps 1–3 as Flow 1.
4. (no tap) STT delivers `ConfirmationVoice.Yes` from the constrained pass
   (heard "sí"). The coordinator's `onConfirmationVoice(Yes)` → same path as the
   tap branch above (`onUserConfirmed`).

### Flow 4 — Medium-confidence call, user says "no" by voice

1. Steps 1–3 as Flow 1.
4. STT delivers `ConfirmationVoice.No`. Coordinator → `onUserRejected` path.

### Flow 5 — Medium-confidence call, 10 seconds of silence

1. Steps 1–3 as Flow 1.
2. (10 s pass with no SÍ/NO tap and no voice yes/no.) Timer fires →
   `ConfirmationTimedOut` event → FSM → `Executing("Cancelo entonces.", null)`
   → TTS → `Idle`.

### Flow 6 — User interrupts mid-confirmation

1. Steps 1–3 as Flow 1.
4. User taps the mic button (the senior may have wanted to ask something else
   entirely). The interrupt-by-button rule (SF-5.3, voice-interaction rule 1)
   fires: `cancelInFlight()` cancels both new jobs in addition to whatever
   else; FSM → `Listening`. Curro is now listening for a new utterance.

### Flow 7 — User says something unrelated by voice

1. Steps 1–3 as Flow 1.
4. STT delivers `ConfirmationVoice.Other("hola Lucía")` from the constrained
   pass — the confirmation STT did not match "sí" or "no". Coordinator's
   `onConfirmationVoice(Other)` → no-op (continue waiting for SÍ/NO via tap, voice
   again, or timeout). **The confirmation STT pass is restarted** so the user can
   try again ("¿llamamos o no, papa?"). The 10-s timer is NOT reset — silence wins
   eventually.

**Pin: speech on confirmation.** The TTS line spoken when the user picks SÍ is
**`copy_calling_confirmed`** = "Vale, llamando." — *not* the handler's own
`copy_calling` ("Llamando a Pepito."). Reason: the spec §6 flow 2 step 6 has Curro
say "Vale, llamando" *and* the action proceeds; the second voice line ("Llamando a
Pepe Martínez" on the system call screen) is Android's, not Curro's. So:

- `UserConfirmed.speech = copy_calling_confirmed`.
- `pendingAction.onConfirm()` is invoked AFTER the `UserConfirmed` transition;
  its return value (e.g. `Spoken("Llamando a Pepe.")`) is discarded for the TTS
  — only its side effect (the `ACTION_CALL` Intent) matters. (`CallController`
  fires the Intent and Android takes over; the `Spoken` text is the handler's
  default fallback for non-call flows.)
- This is the simplest model that matches the spec without inventing per-handler
  "confirmation speech" plumbing.

---

## Function-catalog Impact

**No catalog changes.** SF-6.2 only renders the confirmation that SF-6.1's
`ConfidencePolicy.Confirm` decision already produces.

---

## FSM States Touched

- **`Confirming`** — finally fully wired. The state's `prompt / expiresAtMs /
  pendingAction` fields (Phase 5) are now read by the overlay AND by the
  coordinator's timer.

- **`AssistantEvent.UserConfirmed`** — already in the event set
  (`AssistantEvent.kt` line 56) with signature `(speech, screen)`. SF-6.2 fires
  it with `speech = copy_calling_confirmed`.

- **`AssistantEvent.UserRejected`** — already in the event set (`data object
  UserRejected`). **Change**: the current FSM transition for `UserRejected` is
  `Confirming → Idle` directly (`AssistantStateMachine.kt` line 119–123). This
  is wrong for SF-6.2 because the "Vale, no llamo." line must be spoken AND
  shown (spec §4.6 rule). Pin the FSM change:
  - Convert `data object UserRejected` to `data class UserRejected(val speech:
    String, val screen: AssistantScreen?)` mirroring `UserConfirmed`.
  - `AssistantStateMachine.computeNext`: `UserRejected → Executing(speech,
    screen)` (same shape as `UserConfirmed`).
  - This is a small breaking change to anyone who fired `UserRejected` —
    today, nobody does (Phase 5 wired it but never triggered it). Safe.

- **`AssistantEvent.ConfirmationTimedOut`** — already in the event set (`data
  object ConfirmationTimedOut`). Same fix as `UserRejected`: convert to `data
  class ConfirmationTimedOut(val speech: String)` and transition to `Executing`,
  not `Idle`. (Speak "Cancelo entonces." before going home.) For consistency, do
  the same data-class refactor for both events.

- **`Executing`** — receives the new "Vale, no llamo." / "Cancelo entonces." /
  "Vale, llamando." entries. No structural change.

- **All other states** — untouched.

### FSM transition table (SF-6.2 view of `Confirming`'s outgoing edges)

| From | Event | To | TTS / side effect |
|---|---|---|---|
| `Confirming` | `UserConfirmed(speech, screen)` | `Executing(speech, screen)` | Speak `speech`. After TTS completes, coordinator invokes `pendingAction.onConfirm()` for the side effect (`ACTION_CALL`). |
| `Confirming` | `UserRejected(speech, screen)` | `Executing(speech, screen)` | Speak `speech`. No `onConfirm()` invocation. |
| `Confirming` | `ConfirmationTimedOut(speech)` | `Executing(speech, null)` | Speak `speech`. No `onConfirm()`. |
| `Confirming` | `MicPressed(t)` (interrupt) | `Listening(...)` | `cancelInFlight()` cancels both new jobs; TTS stops mid-prompt; new STT session starts. |
| `Confirming` | `HomePressed` | `Idle` | `cancelInFlight()`. Silent (per SF-5.6 rule "no spoken feedback on HOME"). |

The "After TTS completes, invoke `onConfirm()`" order is critical: invoking the
handler **before** TTS finishes would mean Android's call-screen comes up *over*
the still-speaking "Vale, llamando." — a confusing jump for a senior. The
implementer must `ttsClient.speak(...)` suspend-await before the handler.
Existing pattern in `executeAndFinish` already suspends on TTS the right way.

---

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none new) | — | — | — |

The constrained-vocabulary STT reuses the existing `RECORD_AUDIO` permission;
no new request. (`SpeechRecognizer` does not differentiate by use-case in its
permission model.)

### `SttClient` interface change

Add a second listening method to `domain/repository/SttClient.kt`:

```kotlin
/**
 * Short, constrained-vocabulary listening for a yes/no confirmation
 * (SF-6.2 / US-042). Emits exactly one [ConfirmationVoice] terminal event,
 * then the Flow closes. Cancelling the collecting coroutine releases the
 * native recogniser via awaitClose.
 *
 * Differences from [listen]:
 *  - shorter timeout (5 s of silence inside the recogniser, vs the default
 *    main-listen timeout);
 *  - no partial events (the caller doesn't show a live transcript here —
 *    the screen already shows "¿Llamo a Pepe?" + the SÍ/NO buttons);
 *  - the result is mapped to [ConfirmationVoice] via a fixed Spanish vocabulary.
 *
 * The mapping is implemented in [SpeechRecognizerSttClient.mapToConfirmationVoice]
 * — pin the canonical vocabulary in the impl, not in this interface.
 */
fun listenForConfirmation(): Flow<ConfirmationVoice>

sealed interface ConfirmationVoice {
    data object Yes : ConfirmationVoice
    data object No : ConfirmationVoice
    /** STT returned something but it didn't match the yes/no vocabulary. */
    data class Other(val text: String) : ConfirmationVoice
    /** STT failed (timeout, error). Coordinator treats this as Other (re-listen). */
    data class Failed(val error: CurroError) : ConfirmationVoice
}
```

Vocabulary (pinned, the impl uses these — case- and accent-insensitive match
after `text.lowercase().normalizeAccents()`):

| `Yes` triggers | `No` triggers |
|---|---|
| `sí`, `si`, `vale`, `claro`, `dale`, `venga`, `okay`, `ok` | `no`, `cancela`, `cancelar`, `déjalo`, `dejalo`, `no llames`, `no quiero` |

Anything else → `Other(text)`. Empty STT or `ERROR_NO_MATCH` →
`Failed(SttNoMatch)`. The impl uses `EXTRA_LANGUAGE_MODEL =
LANGUAGE_MODEL_WEB_SEARCH` (short utterances favoured) — pin in impl.

**No `EXTRA_HINT_PROMPTS` is set** for this Phase. (Some `SpeechRecognizer`
implementations accept a vocabulary biasing hint via vendor extras; this is not
specified by Android and varies by OEM. The Redmi 15's Google Voice Recognizer
does not document such an extra. Pin: do not rely on biasing; use the post-hoc
vocabulary match.)

### Other integrations

- `TextToSpeech` — already wired (SF-2.2 / US-016). Reused for the three new
  spoken lines, no API change.
- `NotificationListenerService`, `TelecomManager`, `PackageManager`,
  `ContactsContract`, `AudioManager` — not used by SF-6.2 directly.

---

## On-device-model Impact

**No FunctionGemma impact.** Confirmation lives entirely inside the FSM after
the decision. The model is not re-invoked.

**No Gemma 3n.** No NL generation here — the prompt comes from a string
resource (`copy_confirm_call`).

The constrained STT pass uses the native `SpeechRecognizer` (offline Spanish, on-
device). Same memory footprint as the main listen — there is no second model
loaded. The pass auto-closes within 5 s of silence or the first final result.

---

## Android Specification

### Files added

| Path | Purpose |
|---|---|
| `app/src/main/java/com/curro/app/presentation/assistant/ConfirmationOverlay.kt` | The overlay composable + `@Preview`s. |
| `app/src/main/java/com/curro/app/domain/repository/ConfirmationVoice.kt` | The sealed interface used by `SttClient.listenForConfirmation`. (Or co-locate in `SttClient.kt` — implementer's choice; pin: one file). |

### Files modified

| Path | Change |
|---|---|
| `app/src/main/java/com/curro/app/domain/repository/SttClient.kt` | Add `fun listenForConfirmation(): Flow<ConfirmationVoice>`. |
| `app/src/main/java/com/curro/app/data/voice/SpeechRecognizerSttClient.kt` | Implement `listenForConfirmation` with the constrained-vocabulary mapping. |
| `app/src/main/java/com/curro/app/assistant/AssistantEvent.kt` | Refactor `UserRejected` → `data class UserRejected(val speech: String, val screen: AssistantScreen?)`; refactor `ConfirmationTimedOut` → `data class ConfirmationTimedOut(val speech: String)`. |
| `app/src/main/java/com/curro/app/assistant/AssistantStateMachine.kt` | Update `computeNext`: `UserRejected → Executing(event.speech, event.screen)`; `ConfirmationTimedOut → Executing(event.speech, null)`. The current `Confirming → Idle` direct path for both events is replaced. |
| `app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt` | New `confirmationListenerJob` + `confirmationTimeoutJob`. Body of `onUserConfirmed` / `onUserRejected`. `startConfirmationListening(pendingAction, expiresAtMs)` private helper, called from `Confirming` entry. Extend `cancelInFlight()` to cancel both new jobs. |
| `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt` | Replace the `is AssistantState.Confirming -> Unit` branch with `ConfirmationOverlay(state = s, onYes = ..., onNo = ..., modifier = Modifier.fillMaxSize())`. Wire `onYes` / `onNo` via `LauncherEvent.UserConfirmed` / `LauncherEvent.UserRejected`. |
| `app/src/main/java/com/curro/app/presentation/launcher/LauncherEvent.kt` | Add `data object UserConfirmed : LauncherEvent`; `data object UserRejected : LauncherEvent`. |
| `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt` | Handle the new events — forward to `coordinator.onUserConfirmed()` / `onUserRejected()`. |

### `ConfirmationOverlay` composable

```kotlin
package com.curro.app.presentation.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.curro.app.assistant.AssistantState
import com.curro.app.presentation.common.BigYesNoRow
import com.curro.app.presentation.theme.CurroSpacing

/**
 * `Confirming`-state overlay (SF-6.2 / US-042; spec §6 flow 2 + §11).
 *
 * The overlay covers the launcher home and shows: the resolved prompt (e.g.
 * "¿Llamo a Pepe?") in extra-large text, then a [BigYesNoRow] with the SÍ /
 * NO buttons (≥ 96 dp each, brand colours — primary terracota / secondary olivo).
 *
 * Senior-first contract:
 * - Prompt at `displayMedium` (the largest non-clock type scale token in
 *   `CurroTheme` — readable at arm's length).
 * - `semantics { liveRegion = Polite }` on the prompt so TalkBack announces it.
 * - SÍ / NO via the shared `BigYesNoRow` — already ≥ 96 dp + haptic.
 *
 * The voice yes/no path is independent of this composable — the coordinator
 * runs `SttClient.listenForConfirmation()` concurrently with the overlay
 * being shown. The composable knows nothing about STT.
 *
 * @param state the FSM's `Confirming` state — supplies the prompt.
 * @param onYes called from the SÍ button (and from `BigYesNoRow`'s haptic).
 * @param onNo called from the NO button.
 */
@Composable
fun ConfirmationOverlay(
    state: AssistantState.Confirming,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmationOverlayContent(
        prompt = state.prompt,
        onYes = onYes,
        onNo = onNo,
        modifier = modifier,
    )
}

@Composable
internal fun ConfirmationOverlayContent(
    prompt: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = CurroSpacing.l),
        ) {
            Text(
                text = prompt,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(modifier = Modifier.height(CurroSpacing.xxl))
            BigYesNoRow(
                onYes = onYes,
                onNo = onNo,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// 4 @Preview entries: light, dark, fontScale 1.5×, fontScale 2.0×.
```

### Coordinator: the confirmation flow

```kotlin
// In AssistantCoordinator, after the SF-6.1 work landed.

private var confirmationListenerJob: Job? = null
private var confirmationTimeoutJob: Job? = null
private var pendingActionRef: PendingAction? = null

/**
 * Called from onDecisionSuccess after FunctionCallReady(needsConfirmation=true)
 * is emitted. Starts the constrained-STT pass + the 10-s silence timer.
 *
 * Both jobs race; whichever resolves first cancels the other. The
 * interrupt-by-button rule (SF-5.3) cancels both via cancelInFlight().
 */
private fun startConfirmationListening(
    pendingAction: PendingAction,
    expiresAtMs: Long,
) {
    pendingActionRef = pendingAction
    confirmationListenerJob = scope.launch {
        while (isActive) {
            sttClient.listenForConfirmation().collect { event ->
                when (event) {
                    ConfirmationVoice.Yes -> {
                        onUserConfirmed()
                        return@collect
                    }
                    ConfirmationVoice.No -> {
                        onUserRejected()
                        return@collect
                    }
                    is ConfirmationVoice.Other -> {
                        // Restart the listen (Flow 7) without resetting the timer.
                        // The collect block exits naturally when the inner Flow
                        // closes; the outer while-loop restarts.
                    }
                    is ConfirmationVoice.Failed -> {
                        // STT timed out internally before the 10-s outer timer;
                        // restart the listen, same as Other.
                    }
                }
            }
        }
    }
    confirmationTimeoutJob = scope.launch {
        val remaining = (expiresAtMs - timeProvider.now()).coerceAtLeast(0L)
        delay(remaining)
        // Defensive: only fire if still in Confirming.
        if (state.value is AssistantState.Confirming) {
            onConfirmationTimedOut()
        }
    }
}

fun onUserConfirmed() {
    scope.launch {
        val action = pendingActionRef ?: return@launch
        cancelConfirmationJobs()
        val confirmedSpeech = appContext.getString(R.string.copy_calling_confirmed)
        stateMachine.transition(
            AssistantEvent.UserConfirmed(speech = confirmedSpeech, screen = null),
        )
        ttsClient.speak(confirmedSpeech)
        // After the TTS finishes, run the side effect.
        action.onConfirm()
        stateMachine.transition(AssistantEvent.ExecutionDone)
        pendingActionRef = null
    }
}

fun onUserRejected() {
    scope.launch {
        cancelConfirmationJobs()
        val rejectedSpeech = appContext.getString(R.string.copy_cancel_no_call)
        stateMachine.transition(
            AssistantEvent.UserRejected(speech = rejectedSpeech, screen = null),
        )
        ttsClient.speak(rejectedSpeech)
        stateMachine.transition(AssistantEvent.ExecutionDone)
        pendingActionRef = null
    }
}

private fun onConfirmationTimedOut() {
    scope.launch {
        cancelConfirmationJobs()
        val timeoutSpeech = appContext.getString(R.string.copy_confirm_timeout)
        stateMachine.transition(AssistantEvent.ConfirmationTimedOut(timeoutSpeech))
        ttsClient.speak(timeoutSpeech)
        stateMachine.transition(AssistantEvent.ExecutionDone)
        pendingActionRef = null
    }
}

private fun cancelConfirmationJobs() {
    confirmationListenerJob?.cancel()
    confirmationListenerJob = null
    confirmationTimeoutJob?.cancel()
    confirmationTimeoutJob = null
}

// Existing cancelInFlight() — append the two new cancels.
private fun cancelInFlight() {
    currentJob?.cancel()
    cancelConfirmationJobs()
    ttsClient.stop()
    sttClient.cancel()
}
```

The "while (isActive) → re-collect" loop is the canonical way to handle the
Other-keep-listening case without nested coroutines. The Flow auto-closes when
the inner STT session resolves; the outer loop relaunches a fresh session.

`startConfirmationListening` is called from `onDecisionSuccess` immediately after
the `FunctionCallReady(needsConfirmation = true)` transition + the prompt TTS
(spec: speak the prompt, *then* listen — pin the order: do not start the listener
job before TTS finishes the prompt, else Curro hears its own voice).

### `LauncherEvent` + `LauncherViewModel`

```kotlin
// LauncherEvent.kt — append:
data object UserConfirmed : LauncherEvent
data object UserRejected : LauncherEvent

// LauncherViewModel.onEvent — append:
LauncherEvent.UserConfirmed -> coordinator.onUserConfirmed()
LauncherEvent.UserRejected -> coordinator.onUserRejected()
```

### `LauncherPlaceholderScreen` overlay branch

```kotlin
// Replace:
is AssistantState.Confirming -> Unit
// With:
is AssistantState.Confirming -> ConfirmationOverlay(
    state = s,
    onYes = { viewModel.onEvent(LauncherEvent.UserConfirmed) },
    onNo = { viewModel.onEvent(LauncherEvent.UserRejected) },
    modifier = Modifier.fillMaxSize(),
)
```

### Strings (verify; nothing new)

All four copies already exist on-disk:
- `copy_confirm_call` — "¿Llamo a %1$s?" (line 24).
- `copy_calling_confirmed` — "Vale, llamando." (line 46).
- `copy_cancel_no_call` — "Vale, no llamo." (line 30).
- `copy_confirm_timeout` — "Cancelo entonces." (line 34).

No additions. The brand-design copy table is already authoritative here.

---

## Acceptance Criteria

### Build & static checks
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug` green.
- [ ] No new permissions, no manifest changes.
- [ ] No new third-party dependencies.

### `ConfirmationOverlay` UI correctness
- [ ] Prompt visible at the `displayMedium` text style and centered.
- [ ] `BigYesNoRow` renders SÍ in primary (terracota) and NO in secondary (olivo).
- [ ] Each of SÍ / NO has `assertWidthIsAtLeast(96.dp)` AND
      `assertHeightIsAtLeast(96.dp)` in a Compose UI test (the row itself can be
      wider, but each button is independently > 96 dp by the
      `weight(1f).heightIn(min = Dimens.BigButtonHeight)` modifier — verify in
      a test on `BigYesNoRow`'s parent context).
- [ ] Tap SÍ → `onYes` invoked exactly once.
- [ ] Tap NO → `onNo` invoked exactly once.
- [ ] The prompt has `liveRegion = Polite` (TalkBack announces).
- [ ] 4 `@Preview`s render without warnings (light / dark / 1.5× / 2.0×).

### FSM correctness
- [ ] `UserConfirmed(speech, screen)` from `Confirming` → `Executing(speech, screen)`.
- [ ] `UserRejected(speech, screen)` from `Confirming` → `Executing(speech, screen)`.
- [ ] `ConfirmationTimedOut(speech)` from `Confirming` → `Executing(speech, null)`.
- [ ] All three above events from any non-`Confirming` state throw
      `IllegalAssistantTransition`.
- [ ] `MicPressed(t)` from `Confirming` → `Listening(...)` (unchanged from Phase 5).
- [ ] `HomePressed` from `Confirming` → `Idle` (unchanged from Phase 5).

### Coordinator correctness
- [ ] When the FSM enters `Confirming`, exactly two jobs are launched
      (`confirmationListenerJob`, `confirmationTimeoutJob`).
- [ ] User taps SÍ (test via `LauncherEvent.UserConfirmed` → coordinator):
      - Both confirmation jobs are cancelled.
      - TTS speaks "Vale, llamando.".
      - `pendingAction.onConfirm()` is invoked exactly once (a fake records the
        call).
      - FSM transitions `Confirming → Executing → Idle`.
- [ ] User taps NO:
      - Both confirmation jobs cancelled.
      - TTS speaks "Vale, no llamo.".
      - `pendingAction.onConfirm()` is **NOT** invoked.
      - FSM transitions `Confirming → Executing → Idle`.
- [ ] 10-s timeout (test with `TestScope.testScheduler.advanceTimeBy(10_000)`):
      - TTS speaks "Cancelo entonces.".
      - `pendingAction.onConfirm()` is **NOT** invoked.
      - FSM transitions `Confirming → Executing → Idle`.
- [ ] Voice "sí" from `listenForConfirmation` → same as SÍ tap.
- [ ] Voice "no" from `listenForConfirmation` → same as NO tap.
- [ ] Voice "tiempo de buenos aires" (`Other`) → no transition; STT pass
      restarts; the 10-s timer continues counting.
- [ ] `MicPressed` while in `Confirming` cancels both confirmation jobs +
      `ttsClient.stop()` + `sttClient.cancel()`; FSM → `Listening`. (The
      interrupt-by-button rule extended to cover the new jobs.)

### `SttClient.listenForConfirmation` correctness
- [ ] Returns `Yes` for "sí", "si", "vale", "claro" (case-insensitive).
- [ ] Returns `No` for "no", "cancela", "déjalo" (case-insensitive).
- [ ] Returns `Other("hola")` for unrelated speech.
- [ ] Returns `Failed(SttNoMatch)` when STT delivers `ERROR_NO_MATCH` / empty.
- [ ] Cancelling the collecting coroutine releases the recogniser
      (`awaitClose` runs; no leak).

### Telemetry
- [ ] No new telemetry events in SF-6.2. (Confirming-state outcomes are
      observable via the existing `policy_decided` event from SF-6.1 — the
      `decision = "confirm"` value tells Fran the policy escalated; SF-6.2 does
      not emit a separate event per user action. A "confirmation_resolved" event
      may land in a future SF if Fran needs it; pinned out of Phase 6.)

### Regression
- [ ] Every SF-6.1 test still passes.
- [ ] Every Phase-5 test still passes (the FSM event-data-class refactor is a
      breaking change for any caller of `UserRejected` / `ConfirmationTimedOut`
      — Phase 5 never fired either, so nothing breaks; verify the test suite
      has no callers).

### Manual smoke (Redmi 15)
- [ ] Trigger `Confirming` (use the SF-6.1 smoke path or a debug-only force
      utility). Verify:
      - The overlay paints with the prompt centred and the SÍ/NO row at the
        bottom.
      - Tap SÍ → "Vale, llamando." spoken → call placed (Android call UI takes
        over).
      - Tap NO → "Vale, no llamo." spoken → return to launcher home.
      - 10 s of silence → "Cancelo entonces." → return to launcher home.
      - Say "sí" → SÍ-path executes; say "no" → NO-path executes.
- [ ] Verify TTS finishes the prompt before the constrained-STT pass starts (no
      audio feedback loop — the user must not hear "¿Llamo a Pepe?" overlaid on
      his own voice).

---

## Senior-UX & Copy

The visual rules:
- Prompt at `displayMedium` (Curro's "very-large readable" tier from
  `CurroTypography`).
- Centered, max two lines (`call_contact` prompts are short; the spec example
  "¿Llamo a Pepe Martínez?" is comfortably one line at 412 dp width).
- SÍ in primary (terracota) — warm affirmation.
- NO in secondary (olivo) — **NOT error red**. Saying "no" is not a failure;
  pinned by `brand-design` line 322 + `BigYesNoRow.kt` comment.
- 24 dp gap between SÍ and NO (already enforced by `BigYesNoRow`).
- Haptic on each tap (already enforced by `BigYesNoRow`).

The audio rules:
- Curro speaks the prompt first; **then** the STT pass opens. No overlap.
- Curro speaks the resolution ("Vale, llamando." / "Vale, no llamo." /
  "Cancelo entonces.") before the FSM leaves `Executing` and ultimately reaches
  `Idle`. Spoken + shown together, per spec §4.6.
- 10 s is the spec-pinned silence threshold (§6 flow 2). Not 7 s, not 15 s.

The voice rules:
- "Vale, llamando." — efficient, no apology, present tense.
- "Vale, no llamo." — honest cancellation. Not "lo siento, ahora no llamo".
- "Cancelo entonces." — passive resolution. Senior-friendly default.

---

## Performance Considerations

- Two extra coroutines per `Confirming` entry, both single-purpose. Memory:
  ~couple of kB each. CPU: idle (one is waiting on a Flow collect, the other on
  `delay`).
- The constrained-STT pass auto-closes within 5 s of silence (internal
  recogniser timeout); the outer `while (isActive)` then relaunches if the
  user said `Other` rather than yes/no. No CPU spin.
- Cancellation is structured: `confirmationListenerJob.cancel()` propagates
  to the inner `callbackFlow` → `awaitClose` releases the recogniser.
- The 10-s timer uses `delay()`, which is cooperative — cancellation completes
  within microseconds.

---

## Testing Requirements

### `ConfirmationOverlayTest` (instrumented, Compose UI test)

Use `createAndroidComposeRule<ComponentActivity>()` with `CurroTheme { … }`.

1. `prompt_isVisible` — supply prompt = "¿Llamo a Pepito?"; assert visible.
2. `yesButton_isAtLeast96dp` — find by tag `cd_yes` (or by text "SÍ");
   `assertWidthIsAtLeast(96.dp)` + `assertHeightIsAtLeast(96.dp)`.
3. `noButton_isAtLeast96dp` — same for NO.
4. `tapYes_invokesOnYes` — set up a counter; tap SÍ; assert counter = 1.
5. `tapNo_invokesOnNo` — same for NO.
6. `prompt_hasPoliteLiveRegion` — `onNodeWithText(...).assert(hasLiveRegion(...))`.
7. `dark_mode_renders` — wrap with `CurroTheme { isInDarkTheme = true }`;
   prompt still visible.
8. `fontScale_2_0_doesNotClip` — wrap with
   `LocalConfiguration provides config(fontScale = 2.0f)`; prompt + buttons
   still fit on a 412×800 canvas.

### `AssistantCoordinatorTest` — 5 new cases appended

7. `confirming_userConfirmed_executesAndFinishes` — drive coordinator to
   `Confirming`; fire `coordinator.onUserConfirmed()`; verify
   `pendingAction.onConfirm` called once; TTS spoke `copy_calling_confirmed`;
   FSM → `Idle`.
8. `confirming_userRejected_speaksAndFinishes` — fire
   `coordinator.onUserRejected()`; verify `pendingAction.onConfirm` **NOT**
   called; TTS spoke `copy_cancel_no_call`; FSM → `Idle`.
9. `confirming_timeoutFires_speaksCancel` — use `TestScope.testScheduler`;
   advance virtual time by 10_000 ms; verify TTS spoke `copy_confirm_timeout`;
   `onConfirm` NOT called; FSM → `Idle`.
10. `confirming_voiceYes_sameAsTap` — make `FakeSttClient.listenForConfirmation`
    emit `ConfirmationVoice.Yes`; assert same outcomes as case 7.
11. `confirming_voiceNo_sameAsTap` — emit `ConfirmationVoice.No`; assert same
    outcomes as case 8.

Plus 1 interruption test (group it with the existing SF-5.3 interruption suite):

12. `confirming_micPressed_interrupts` — drive to `Confirming`; fire
    `coordinator.onMicPressed()`; assert `confirmationListenerJob.isCancelled`,
    `confirmationTimeoutJob.isCancelled`, `ttsClient.stopCount` incremented,
    FSM → `Listening`.

### `SpeechRecognizerSttClientTest` (Robolectric) — 1 new case

13. `listenForConfirmation_mapsVocabularyCorrectly` — parameterised over a table
    of (recogniser output, expected `ConfirmationVoice`) pairs. Cover:
    - "sí" → Yes
    - "vale" → Yes
    - "claro" → Yes
    - "no" → No
    - "cancela" → No
    - "hola Lucía" → Other("hola lucía")
    - "" → Failed(SttNoMatch)
    The test uses `Shadows.shadowOf(speechRecognizer).triggerResult(...)`
    helpers.

### Manual smoke — see "Acceptance Criteria → Manual smoke".

---

## Implementation Notes

**Order of changes within this SF:**
1. `SttClient.listenForConfirmation` interface + `ConfirmationVoice` sealed
   interface.
2. `SpeechRecognizerSttClient` impl + vocabulary mapping + tests.
3. `AssistantEvent.UserRejected` / `ConfirmationTimedOut` data-class refactor.
4. `AssistantStateMachine.computeNext` updates + FSM tests.
5. `AssistantCoordinator` confirmation flow (jobs, helpers, the `cancelInFlight`
   extension).
6. `ConfirmationOverlay` composable + previews.
7. `LauncherEvent` + `LauncherViewModel` + `LauncherPlaceholderScreen` overlay
   wiring.
8. 5 new `AssistantCoordinatorTest` cases + interruption test.
9. `ConfirmationOverlayTest` Compose UI tests.
10. Manual smoke on Redmi 15.

**Pin: avoid TTS / STT race.** The implementer must `ttsClient.speak(prompt)`
**suspend** to completion before `startConfirmationListening(...)`. The existing
`TtsClient.speak` is suspend (verified in `TtsClient.kt`); do not bypass with a
fire-and-forget launch.

**Pin: the prompt comes from the FSM, not re-formatted.** The prompt is built
in SF-6.1's `buildConfirmPrompt(call)` and stored in `Confirming.prompt`. The
overlay reads `state.prompt`; the TTS speaks `state.prompt`. There is exactly
**one** string — no risk of the visible and spoken texts drifting.

---

## Revision History

| Date | Author | Change |
|---|---|---|
| 2026-05-16 | android-product-analyst (Opus) | Initial brief — Phase 6 PM batch. |
