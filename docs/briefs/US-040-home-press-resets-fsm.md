# US-040 — SF-5.6 · HOME-press / `onNewIntent` resets the FSM to `idle`

> **Spec trace:** spec §6 — the diagram closes "everything ends in idle";
> spec §11 — the launcher home is "always visible on HOME"; spec §14 ("Lo
> primero que validar… (1) que entiende el launcher (reloj + botón + apps)")
> — the home is where Curro starts every interaction.
> **`launcher-app` skill rule 3:** *"Reset the FSM to `idle` on
> `onNewIntent`/HOME — the user came home, start clean."*
> **Master-plan:** SF-5.6 — *"When the user returns to the launcher (HOME
> button from another app → `onNewIntent`), the FSM resets to `idle` and any
> pending overlay is cleared."*
> **Phase:** 5 — State machine & interruption.
> **Depends on:** US-035 (`HomePressed` event), US-036
> (`coordinator.onHomePressed()` already implemented).
> **Size:** S.
> **Skills:** `launcher-app` (rule 3 — HOME reset; `singleTask` +
> `onNewIntent` mechanics), `voice-interaction` (the FSM owns the reset),
> `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `MainActivity.onNewIntent` → `coordinator.onHomePressed()` → FSM `Idle` |
| **US ID** | US-040 |
| **Phase** | 5 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | voice-pipeline-engineer |

---

## 1. Summary

`MainActivity` is the launcher Activity with `launchMode="singleTask"`
(`AndroidManifest.xml`, US-009). Pressing HOME from any other app brings the
already-running `MainActivity` back to the front via `onNewIntent`, *not* a
new instance. Today, that `onNewIntent` is unimplemented — the FSM keeps
whatever state it was in (`Executing`, `Confirming`, etc.) and the user
returns to a launcher home with an out-of-place overlay still showing
(e.g., "Llamando a Pepito" five minutes after the call ended).

This SF overrides `onNewIntent` to:

1. Detect a HOME-launch intent (`Intent.categories.contains(Intent.CATEGORY_HOME)`).
2. Call `coordinator.onHomePressed()` (already wired in SF-5.2 — does the
   cancel-job-and-stop-TTS-and-STT then `transition(HomePressed)` dance).
3. Result: every overlay clears; the user sees the launcher home.

That's it. The architectural lift was in SF-5.1 (the `HomePressed` event)
and SF-5.2 (the coordinator's `onHomePressed` method). This SF is the
2-line activity-level wire-up plus tests.

Why this matters for *this* user: the user opens WhatsApp from the favourites
grid, scrolls a bit, presses HOME to come back. He should land on a clean
launcher home, not a half-cancelled "Llamando…" overlay he didn't trigger.
This is the "feels the same every day" principle (`brand-design` rule 7,
`launcher-ui` rule 1.4) operationalised.

---

## 2. Scope

**In scope:**

- Override `MainActivity.onNewIntent(intent: Intent)`:
  ```kotlin
  override fun onNewIntent(intent: Intent) {
      super.onNewIntent(intent)
      if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
          coordinator.onHomePressed()
      }
  }
  ```
- Inject `AssistantCoordinator` into `MainActivity` (Hilt `@Inject lateinit
  var`).
- 2 new JVM tests on `AssistantCoordinatorTest.kt`:
  - `onHomePressed from non-Idle state transitions to Idle` (parameterised
    over each non-Idle state).
  - `onHomePressed cancels currentJob and stops TTS + STT`.
- 1 new instrumented test in `androidTest/`:
  - `MainActivityOnNewIntentInstrumentedTest` — install MainActivity, drive
    the coordinator to `Listening`, fire a HOME intent via
    `MainActivity.onNewIntent(...)`, assert state returns to `Idle`.

**Out of scope:**

- The cancellation glue in `onHomePressed` — already implemented in
  SF-5.2. This SF only wires the activity entry point.
- The HOME-press detection from outside `singleTask` (e.g., a swipe-up
  recents → app switch). Android's launcher-lifecycle handling is via
  `onNewIntent`; recents-swipe drives `onResume` (no new intent). Pin:
  `onResume` does **not** trigger `onHomePressed` — that would clear the
  FSM every time the user idly returns to the launcher (e.g., the screen
  woke up). Only an explicit HOME-button press (which routes through
  `onNewIntent` with `CATEGORY_HOME`) triggers the reset.
- A toast / spoken acknowledgement of "I cleared the overlay because you
  came home" — pin: **no spoken feedback** on HOME reset. The user is
  navigating; no Curro voice is wanted.
- Tracking HOME-press telemetry — pin: no new telemetry.

---

## 3. User Flows

### Flow 1: Mid-call returns home

| # | User action | Curro state | Activity event |
|---|---|---|---|
| 1 | User taps mic, says "llama a Pepito", call places. | `Executing("Llamando a Pepito.", null)` then `Idle` (after TTS) | — |
| 2 | Android takes over with the dialer. | (Curro in `Idle`; Android handling the call) | `onPause` on MainActivity |
| 3 | Call ends; user presses HOME. | (still `Idle`) | `onNewIntent(intent with CATEGORY_HOME)` |
| 4 | `MainActivity.onNewIntent`: detect `CATEGORY_HOME` → `coordinator.onHomePressed()` → `transition(HomePressed)`. | `Idle` (was already; no visible change) | — |
| 5 | User sees the launcher home — clock, mic, favourites, no overlay. | `Idle` | — |

### Flow 2: User returns from WhatsApp mid-`Executing`

The edge that motivates this SF. The user said "léeme los mensajes", Curro
started reading, the user changed his mind and opened WhatsApp from the
favourites grid mid-read.

| # | User action | Curro state | Activity event |
|---|---|---|---|
| 1 | User taps mic, "léeme los mensajes". | `Executing("Tienes 3 mensajes…", null)` (TTS in flight) | — |
| 2 | User taps the WhatsApp tile in the favourites grid. | `Executing` (Curro doesn't know the user switched apps) | `LauncherViewModel.onAppTileTapped("com.whatsapp")` → `LauncherSideEffect.LaunchApp` → `startActivity(...)`. **TTS keeps playing in the background** — pin: this is current behaviour; the brief does **not** change it. |
| 3 | WhatsApp opens. | `Executing` (TTS in background — actually, on `onPause`, `TextToSpeech` typically continues; verify on Redmi 15) | `onPause` on MainActivity |
| 4 | User reads some WhatsApp, presses HOME. | `Executing` still (the turn never completed cleanly) | `onNewIntent(CATEGORY_HOME)` |
| 5 | `MainActivity.onNewIntent` → `coordinator.onHomePressed()` → cancel currentJob + `ttsClient.stop()` + `sttClient.cancel()` + `transition(HomePressed)`. | `Idle` | — |
| 6 | User sees a clean launcher home. | `Idle` | — |

**Pinned**: between step 2 (tap WhatsApp tile) and step 5 (return HOME),
the TTS may have already finished naturally — in which case `currentJob` is
already `null`, `ttsClient.stop()` is a no-op, and only the FSM transition
runs. The implementation handles both paths uniformly because `onHomePressed`
in SF-5.2 has no conditional branches on the current state.

### Flow 3: User opens config menu, presses HOME

The config menu is a separate nav route (`/config`) within Curro. Pressing
HOME while inside config also routes through `onNewIntent(CATEGORY_HOME)` —
because the launcher is `singleTask`, HOME from anywhere brings the same
activity to the front. The config menu's back-stack handling (Compose
Navigation) is independent of the FSM reset.

| # | User action | Activity event |
|---|---|---|
| 1 | User taps clock 5×; config menu opens (Compose `navController.navigate("config")`). | (no FSM change — config is outside the FSM) |
| 2 | User presses HOME. | `onNewIntent(CATEGORY_HOME)` |
| 3 | `coordinator.onHomePressed()` runs. FSM transitions to `Idle` (was already; no visible change for the FSM). | — |
| 4 | The Compose nav back-stack still shows `/config` on top — **pin: this SF does NOT pop the nav stack**. The user lands back at the config menu, which is correct (they pressed HOME, which historically Android *can* be configured to pop deep stacks or not; the safer prototype behaviour is "HOME brings the app to front; don't pop"). The Compose nav stack is the launcher's, not the FSM's; they are independent layers. | — |

Pinned: **HOME does not pop the config menu**. If a future SF wants HOME to
also `navController.popBackStack(route = "launcher", inclusive = false)`,
that's a separate decision; the brief does not.

---

## 4. Function-catalog Impact

No catalog change.

---

## 5. FSM States Touched

**All six.** `HomePressed` is valid in every state (US-035 transition
table); every pre-state collapses to `Idle`. SF-5.6 verifies the activity-level
wiring fires the event correctly.

---

## 6. Android System Integrations & Permissions

No new permissions.

| Integration | Why |
|---|---|
| `ComponentActivity.onNewIntent(intent: Intent)` | Detect HOME re-launch in a `singleTask` activity. |
| `Intent.categories.contains(Intent.CATEGORY_HOME)` | Distinguish a HOME-button re-launch from other re-entries (deep links, share intents — none of which exist in Curro today, but the filter is defensive). |
| `AssistantCoordinator` (existing) | The cancellation mechanism. |

The `Intent.categories` API may return `null` if the activity is started by
something exotic (a system service); the brief pins the safe-call:
`intent.categories?.contains(Intent.CATEGORY_HOME) == true`. If false (a
non-HOME re-entry), `onNewIntent` does nothing — current behaviour preserved.

---

## 7. On-device-model Impact

No model impact.

---

## 8. Android Specification

### 8.1 Files modified

```
app/src/main/java/com/curro/app/MainActivity.kt
  - Add @Inject lateinit var coordinator: AssistantCoordinator
  - Override onNewIntent(intent: Intent)
```

### 8.2 Files added

```
app/src/test/java/com/curro/app/assistant/AssistantCoordinatorTest.kt
  - Append Group O (2 tests) — onHomePressed coverage

app/src/androidTest/java/com/curro/app/MainActivityOnNewIntentInstrumentedTest.kt
  - NEW — 1 instrumented test
```

### 8.3 `MainActivity.kt` — post-refactor

```kotlin
package com.curro.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.curro.app.assistant.AssistantCoordinator
import com.curro.app.presentation.navigation.CurroNavHost
import com.curro.app.presentation.theme.CurroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Launcher Activity for Curro.
 *
 * - [enableEdgeToEdge] paints under the system bars; [CurroNavHost]'s
 *   Scaffold consumes the insets via its `innerPadding` (No-Double-Padding
 *   rule, `navigation-patterns` rule 1).
 * - [@AndroidEntryPoint] enables Hilt-injected ViewModels in any screen the
 *   nav graph hosts.
 * - `launchMode="singleTask"` (manifest, US-009) means pressing HOME from any
 *   app brings this Activity back via [onNewIntent], not a new instance.
 *
 * SF-5.6 (US-040): [onNewIntent] resets the assistant FSM to `Idle` on
 * a HOME-launch intent. Any in-flight TTS / STT / model decode is cancelled
 * (`coordinator.onHomePressed()` does the same cancel-everything dance as
 * `onMicPressed`).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var coordinator: AssistantCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CurroTheme {
                CurroNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME-press from any app routes through here (because launchMode="singleTask").
        // Detect the HOME category and reset the FSM. Other intent kinds (deep links,
        // share targets — none exist in Curro today) fall through unchanged.
        if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
            coordinator.onHomePressed()
        }
    }
}
```

### 8.4 The 2 new JVM tests (Group O, appended to `AssistantCoordinatorTest`)

```kotlin
// Group O — onHomePressed: HOME reset

@Test fun `onHomePressed from each non-Idle state transitions to Idle`() {
    // Parameterised over: Listening, Processing, Confirming, Executing, ErrorRecovery.
    listOf(
        AssistantState.Listening("hola", 100),
        AssistantState.Processing("hola", 100),
        AssistantState.Confirming("¿llamo?", 110, fakePendingAction()),
        AssistantState.Executing("Llamando.", null),
        AssistantState.ErrorRecovery("No te he oído", 1),
    ).forEach { pre ->
        // Drive the FSM to `pre` (use stateMachine.transition directly with bootstrap events).
        // Then: coordinator.onHomePressed().
        // Assert: stateMachine.state.value == AssistantState.Idle.
    }
}

@Test fun `onHomePressed cancels currentJob and stops TTS and STT`() {
    // setup: drive coordinator to Executing with FakeTtsClient.speak suspending forever.
    // assert: currentJob != null && !currentJob.isCancelled BEFORE the call.
    // act: coordinator.onHomePressed().
    // assert: currentJob.isCancelled == true; fakeTts.wasStopped == true; fakeStt.wasCancelled == true; state == Idle.
}
```

### 8.5 `MainActivityOnNewIntentInstrumentedTest.kt` — pinned skeleton

```kotlin
package com.curro.app

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import com.curro.app.assistant.AssistantCoordinator
import com.curro.app.assistant.AssistantState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class MainActivityOnNewIntentInstrumentedTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var coordinator: AssistantCoordinator

    @Test
    fun homeIntentResetsAssistantStateToIdle() {
        hiltRule.inject()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Drive the coordinator to Listening (use a test seam — pin:
            // coordinator.testForceListening() in addition to the
            // testForceExecuting() seam from SF-5.3).
            coordinator.testForceListening()
            // Wait for the state to settle.
            val deadline = SystemClock.elapsedRealtime() + 1_000
            while (coordinator.state.value !is AssistantState.Listening && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(10)
            }
            assertTrue(coordinator.state.value is AssistantState.Listening)

            // Fire a HOME intent via onNewIntent.
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            scenario.onActivity { activity ->
                activity.onNewIntent(homeIntent)
            }

            // Wait for the state to settle to Idle.
            val deadline2 = SystemClock.elapsedRealtime() + 1_000
            while (coordinator.state.value !is AssistantState.Idle && SystemClock.elapsedRealtime() < deadline2) {
                Thread.sleep(10)
            }
            assertTrue(coordinator.state.value is AssistantState.Idle)
        }
    }
}
```

`coordinator.testForceListening()` is the same kind of test seam as
`testForceExecuting` from SF-5.3 — `@VisibleForTesting`. Pin: the implementer
may consolidate the two into a single `coordinator.testForceState(state:
AssistantState)` helper.

### 8.6 Hilt wiring

`MainActivity` already has `@AndroidEntryPoint`. Adding `@Inject lateinit var
coordinator: AssistantCoordinator` is sufficient — Hilt resolves the
`@Singleton` instance.

### 8.7 Manifest

Unchanged. `launchMode="singleTask"` was set by US-009; `CATEGORY_HOME` is
in the intent filter; nothing new.

### 8.8 ViewModels / Composables / Navigation / Material

Unchanged.

---

## 9. Acceptance Criteria

- [ ] `MainActivity.onNewIntent` overridden and tests the `CATEGORY_HOME`
  intent category.
- [ ] `MainActivity` injects `AssistantCoordinator` (via Hilt `@Inject
  lateinit var`).
- [ ] `coordinator.onHomePressed()` is the only call site from
  `onNewIntent`; no inline cancellation glue (it all lives in the
  coordinator).
- [ ] `super.onNewIntent(intent)` is called first (Android lifecycle
  contract).
- [ ] Non-HOME intent categories (or `null` categories) are no-ops — the
  early `if` returns control to Android's default handling.
- [ ] 2 new JVM tests (Group O) pass: every non-Idle state → Idle; cancellation
  observed.
- [ ] 1 instrumented test (`MainActivityOnNewIntentInstrumentedTest`) passes
  on the emulator.
- [ ] Manual smoke (Redmi 15): start `read_all_unread_whatsapp`, open
  WhatsApp from the favourites tile mid-read, press HOME; the
  `ExecutingOverlay` is cleared on return; the launcher home is clean.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest`
  green; `./gradlew connectedDebugAndroidTest` green.

---

## 10. Design Notes

- **Why `onNewIntent` and not `onResume`?** `onResume` fires whenever the
  activity becomes foreground — including when the screen wakes up, when
  the user returns from an `ActivityResultLauncher`, etc. Clearing the FSM
  on `onResume` would cancel a permission-grant auto-retry in flight (the
  permission dialog `onPause`s the activity; granting `onResume`s it; we
  want the auto-retry to continue, not get cancelled). `onNewIntent` is
  fired *specifically* when a new `Intent` (HOME, deep link) re-enters the
  `singleTask` activity — that's the right hook.
- **Why check `CATEGORY_HOME` rather than reset unconditionally?** Curro
  has no other intent entry points today, but Phase 8 might add a deep
  link from a notification (the "tap the failed-commands log" flow). The
  filter is defensive: if a future intent comes in that isn't a HOME-press,
  we shouldn't auto-cancel whatever the user was doing.
- **Why not toast / speak "Vale, vuelvo al inicio" on HOME reset?** Two
  reasons:
  1. **HOME is a navigation gesture, not a user request.** Curro speaking
     here would feel invasive — the user wants to be left alone for the
     moment.
  2. **In the common case, the FSM was already `Idle`** (the user came
     home after a clean turn). A toast every HOME-press would be
     pure noise.
- **Why does this SF have 1 instrumented test plus 2 JVM tests, vs.
  US-035's 70+ JVM tests?** Because the *FSM* coverage is US-035's job
  (every `HomePressed` transition is tested there). This SF only covers
  the *activity-side wiring* — that the intent shape is detected and the
  coordinator's method is called. Two JVM tests for the coordinator's
  invariants + one instrumented test for the activity is the minimum that
  proves the wiring.
- **Compose nav stack vs. FSM reset are independent.** Pinned in Flow 3 —
  if the user presses HOME from inside the config menu, the FSM resets
  but the config-menu screen stays on top. This is correct prototype
  behaviour; the spec doesn't ask for HOME to pop the back-stack. Phase
  8 may revisit.

---

## 11. Senior-UX & Copy

No new copy. Spoken output: **none** on HOME-press. Visual: the launcher
home (which the user is now looking at) is unchanged; overlays disappear
because their state precondition (`assistantState !is Idle`) no longer
holds.

The "feels the same every day" rule applies: every HOME-press lands the
user on **exactly the same** launcher home — clock, mic, favourites, in
the same spots. No surprises.

---

## 12. Performance Considerations

- `onNewIntent` is a single comparison + a method call — sub-microsecond.
- `coordinator.onHomePressed()` is the same shape as `onMicPressed` (a
  scoped `launch` that does cancel + stop + transition) — single-digit
  milliseconds at worst, dominated by the structured-cancellation
  unwinding.

---

## 13. Testing Requirements

§8.4 + §8.5. The full multi-state coverage is in US-035 (every
`HomePressed` transition tested at the FSM level); this SF's tests cover
the coordinator-level dance + the activity-level wiring.

The manual smoke on the Redmi 15 verifies that the HyperOS
launcher-lifecycle handling (which can be quirky — `launcher-app` skill
notes "HyperOS sometimes 'forgets' the default launcher after updates")
plays well with our `onNewIntent`. If HyperOS routes HOME through a
different intent shape, the `CATEGORY_HOME` check may need to be widened
— flag as a follow-up if observed.

---

## 14. Implementation Notes

**PM Owner wrote**: every section.

**Architect / voice-pipeline-engineer fills in (during implementation)**:
the `coordinator.testForceListening` / `testForceState` seam shape
(consolidated with SF-5.3's `testForceExecuting`); any HyperOS quirk
surfaced on hardware.

**Commit message (pinned)**:

```
feat: HOME-press resets assistant FSM to idle (US-040 / SF-5.6)

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft — pinned the `onNewIntent` shape, the `CATEGORY_HOME` filter, the no-spoken-feedback policy, the 2 JVM + 1 instrumented test, and the Compose-nav-vs-FSM independence note. |
