# US-039 — SF-5.5 · State-driven overlay routing

> **Spec trace:** spec §11 — the per-state UI: `listening` (blue tint + "Te
> escucho…" + live transcript), `processing` ("Un momento…" with a
> **non-animated** indicator), `confirming` (two huge SÍ/NO + the resolved
> target), `executing` (speak what you're doing), `error_recovery` (the
> recovery line). Spec §4.6: every Curro→user message is spoken **and** shown
> — the screen reinforces the voice, never replaces it.
> **Master-plan:** SF-5.5 — *"Move the listening/processing/executing UI
> from Phase 2's ad-hoc rendering to state-driven overlays on top of the
> launcher home, keyed off `AssistantState` (`launcher-ui` rule 3)."*
> **Phase:** 5 — State machine & interruption.
> **Depends on:** US-035 (FSM), US-036 (`uiState.assistantState`).
> **Size:** M.
> **Skills:** `launcher-ui` (rule 1 — senior-first; rule 3 — overlays are
> state-driven not nav routes; rule 4 — build everything from the shared
> big components), `accessibility-patterns` (≥ 96 dp targets, ≥ 7:1
> contrast, `contentDescription`, `liveRegion` for the Curro-speech text),
> `brand-design` (tokens; `ListeningTint` for the blue overlay),
> `compose-patterns` (stateless `Content` composables, `@Preview` triplet),
> `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | One overlay composable per non-Idle `AssistantState`, hosted in a `Box` over the launcher home |
| **US ID** | US-039 |
| **Phase** | 5 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | voice-pipeline-engineer |

---

## 1. Summary

The Phase-2 `ListeningOverlay` was a one-composable-fits-many situation:
`Starting`, `Listening`, `Speaking`, `Processing`, `Error` were branches in a
single function that toggled tints, headlines, transcripts, and audio-wave
states. With SF-5.2 collapsing those into `AssistantState`'s six values, the
overlay is one big `when` waiting to be split.

This SF splits it into **four per-state overlay composables**, hosted by a
single `Box` in `LauncherPlaceholderScreen`. The launcher home content
(`LauncherContent`: clock + mic button + favourites grid + "Más apps" + the
permission CTAs) is rendered first; the overlay is rendered on top when
`assistantState !is AssistantState.Idle`. `Confirming` is intentionally
mapped to `Unit` (no overlay yet) — Phase 6 owns its UI; the *routing* is
already in place so Phase 6 only writes the composable.

The four overlays:

| `AssistantState` | Composable file | What it shows |
|---|---|---|
| `Listening(partial, startedAtMs)` | `presentation/assistant/ListeningOverlay.kt` | Blue tint (`CurroListeningTint*`), "Te escucho…" headline, live partial transcript, static audio-wave glyph. |
| `Processing(transcript, startedAtMs)` | `presentation/assistant/ProcessingOverlay.kt` | "Un momento…" centred large, a **non-animated** indicator (spec §11). |
| `Executing(speech, screen)` | `presentation/assistant/ExecutingOverlay.kt` | The Spanish line large, on a `BigCard`. (Phase 7 will overlay `MessageCardsScreen` when `screen` is non-null; Phase 5 only renders `speech`.) |
| `ErrorRecovery(message, failureCount)` | `presentation/assistant/ErrorRecoveryOverlay.kt` | The recovery line in `errorContainer` colours. |

`LauncherPlaceholderScreen.kt`'s body shrinks dramatically — the overlay
branches move out. **Pinned target: the file is ≤ 200 lines after the
refactor** (currently 419; the previews + the launcher content stay, the
inline listening rendering goes).

Why this matters for *this* user: spec rule "feels the same every day" +
"audio + visual together" + "no fussy animation". A clean state-driven
overlay is the smallest blast radius for visual changes; it keeps each
overlay tight to its state's data, so a regression in Curro's per-state UI
is one file at a time, not one giant `if`/`when` ladder.

---

## 2. Scope

**In scope:**

- **4 new composable files** in
  `app/src/main/java/com/curro/app/presentation/assistant/`:
  - `ListeningOverlay.kt`
  - `ProcessingOverlay.kt`
  - `ExecutingOverlay.kt`
  - `ErrorRecoveryOverlay.kt`
- Each has:
  - Stateless `Content` composable (`<Name>Content`) — receives the data, emits
    nothing (no `onEvent` parameters; these overlays don't have buttons
    except the mic, which is in the launcher content below).
  - A top-level `<Name>` composable that wraps `<Name>Content` for the
    screen to use.
  - 3 `@Preview` functions: light, dark, `fontScale = 2.0f` (per
    `brand-design` rule 6).
  - `contentDescription` on every `Icon`/`Image`.
  - Live-region semantics on the spoken text (`stateDescription` + `liveRegion`
    where applicable per `accessibility-patterns`).
- **Refactor of** `LauncherPlaceholderScreen.kt`:
  - Wrap the existing `LauncherPlaceholderContent` in a `Box(Modifier.fillMaxSize())`.
  - After the launcher content, add the `when (val s = uiState.assistantState)`
    that dispatches to one of the four overlays.
  - Delete the inline listening-state rendering branches that
    `LauncherPlaceholderContent` currently owns. (The mic button and the
    permission CTAs stay in `LauncherPlaceholderContent`.)
- **6+ Compose UI tests** in
  `app/src/androidTest/java/com/curro/app/presentation/launcher/LauncherScreenStateTest.kt`:
  - `Idle → no overlay shown`
  - `Listening → ListeningOverlay visible, partial text displayed`
  - `Processing → ProcessingOverlay visible, "Un momento…" displayed`
  - `Executing → ExecutingOverlay visible, speech text displayed`
  - `ErrorRecovery → ErrorRecoveryOverlay visible, message displayed`
  - `state transition Listening → Processing cleanly swaps overlay`
- Verify `copy_processing` exists in `strings.xml`. **Verified — line 20:
  "Un momento…"**.

**Out of scope:**

- The `Confirming` overlay — pinned as `Unit` (the launcher renders nothing
  on top of the home in `Confirming`). Phase 6 (`SF-6.2 ConfirmationOverlay`)
  fills the body. The `when` branch must explicitly say
  `is AssistantState.Confirming -> Unit  // SF-6.2 owns this overlay`.
- `MessageCardsScreen` / `ContactPickerScreen` — those land in Phase 6
  (disambiguation) and Phase 7 (alias learning) when `Executing.screen` is
  non-null. For Phase 5, `screen` is **always null** (Phase-4 handlers don't
  populate it), so `ExecutingOverlay` only renders `speech`.
- Animation between states — pinned: no transition animation in Phase 5
  (`brand-design` rule 6: "no fussy animation"). A composable simply
  appears/disappears. Phase 8+ may evaluate a 100-ms cross-fade if user
  feedback requests it.
- The audio-wave indicator on `Listening` — pinned: a **static** mic glyph
  with a subtle dot pattern below it (or omit the wave entirely). The
  Phase-2 `ListeningOverlay` had an animated wave; for Phase 5 it becomes
  static per `brand-design` rule 6. **If** the implementer judges a static
  visual unintuitive, fall back to omitting the wave and showing just the
  partial transcript + the headline.

---

## 3. User Flows

### Flow 1: A turn through every overlay

Same Flow-1 as US-036, with the visual side called out:

| # | `AssistantState` | Overlay rendered | Visual |
|---|---|---|---|
| 1 | `Idle` | none | Launcher home — clock, mic, favourites. |
| 2 | `Listening("", t)` | `ListeningOverlay` | Blue tint over the home; "Te escucho…" headline; no transcript yet. |
| 3 | `Listening("qué", t)` | `ListeningOverlay` | Same overlay; partial "qué" appears under the headline in large text. |
| 4 | `Listening("qué hora es", t)` | `ListeningOverlay` | Partial updates. |
| 5 | `Processing("qué hora es", t')` | `ProcessingOverlay` | "Un momento…" centred; subtle static indicator. |
| 6 | `Executing("Son las trece y cuarenta y siete.", null)` | `ExecutingOverlay` | The line displayed large in a `BigCard`. |
| 7 | `Idle` | none | Back to home. The overlay disappears. |

### Flow 2: STT failure overlay

| # | `AssistantState` | Overlay | Visual |
|---|---|---|---|
| 1 | `Idle → Listening` | `ListeningOverlay` | (as above) |
| 2 | `ErrorRecovery("No te he oído…", 1)` | `ErrorRecoveryOverlay` | The line in `errorContainer` colours; no overlay tint change vs. listening's blue — the colour choice does the work. |
| 3 | `Idle` | none | |

### Flow 3: Interrupt (overlay swap)

Cosmetic Flow-4 from US-037:

| # | `AssistantState` | Overlay |
|---|---|---|
| 1 | `Executing("Tienes 3 mensajes…")` | `ExecutingOverlay` |
| 2 | (user taps mic) | |
| 3 | `Listening("", new t)` | `ListeningOverlay` (instant swap, no animation) |

The instant-swap is acceptable because (a) the *audio* changes too —
`ttsClient.stop()` is also called, and (b) the user explicitly triggered the
change. From a senior-UX standpoint this is "the screen reacts when I act",
which is exactly what we want.

---

## 4. Function-catalog Impact

No catalog change.

---

## 5. FSM States Touched

This SF reads `AssistantState`; it does not transition it. The
`when (state)` exhaustive branching is the surface area.

---

## 6. Android System Integrations & Permissions

No new integrations / permissions.

---

## 7. On-device-model Impact

No model impact.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/presentation/assistant/
├── ListeningOverlay.kt
├── ProcessingOverlay.kt
├── ExecutingOverlay.kt
└── ErrorRecoveryOverlay.kt

app/src/androidTest/java/com/curro/app/presentation/launcher/
└── LauncherScreenStateTest.kt           // NEW — 6 Compose UI tests
```

### 8.2 Files modified

```
app/src/main/java/com/curro/app/presentation/launcher/
└── LauncherPlaceholderScreen.kt          // ≤ 200 lines after refactor; overlay branches extracted
```

### 8.3 `ListeningOverlay.kt` — pinned sketch

```kotlin
package com.curro.app.presentation.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.curro.app.R
import com.curro.app.assistant.AssistantState
import com.curro.app.presentation.theme.CurroListeningTintDark
import com.curro.app.presentation.theme.CurroListeningTintLight
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Listening-state overlay. Renders on top of the launcher home (the home's
 * mic button stays interactive — the user can interrupt by pressing it again).
 *
 * Spec §11: blue tint, "Te escucho…", live transcript below in large text.
 * Senior-first: no fussy animation (brand-design rule 6 — the wave glyph
 * is a *static* mic icon).
 */
@Composable
fun ListeningOverlay(
    state: AssistantState.Listening,
    modifier: Modifier = Modifier,
) {
    ListeningOverlayContent(partial = state.partial, modifier = modifier)
}

@Composable
private fun ListeningOverlayContent(
    partial: String,
    modifier: Modifier = Modifier,
) {
    val tint = if (isSystemInDarkTheme()) CurroListeningTintDark else CurroListeningTintLight
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tint)
            .padding(CurroSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null, // the headline below is the label
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(CurroSpacing.l))
        Text(
            text = stringResource(R.string.copy_listening_prompt), // "Te escucho…"
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (partial.isNotBlank()) {
            Spacer(Modifier.height(CurroSpacing.l))
            Text(
                text = partial,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }
    }
}

// --- @Preview ---

@Preview(name = "Listening — Light, partial transcript", widthDp = 412, heightDp = 800)
@Composable
private fun ListeningOverlayLightPreview() {
    CurroTheme(darkTheme = false) {
        ListeningOverlayContent(partial = "Llama a Pepito")
    }
}

@Preview(name = "Listening — Dark, partial transcript", widthDp = 412, heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ListeningOverlayDarkPreview() {
    CurroTheme(darkTheme = true) {
        ListeningOverlayContent(partial = "Llama a Pepito")
    }
}

@Preview(name = "Listening — Light, fontScale 2.0", widthDp = 412, heightDp = 800, fontScale = 2.0f)
@Composable
private fun ListeningOverlayLargeFontPreview() {
    CurroTheme(darkTheme = false) {
        ListeningOverlayContent(partial = "Llama a Pepito")
    }
}
```

### 8.4 `ProcessingOverlay.kt` — pinned sketch

```kotlin
@Composable
fun ProcessingOverlay(modifier: Modifier = Modifier) {
    ProcessingOverlayContent(modifier = modifier)
}

@Composable
private fun ProcessingOverlayContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(CurroSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Static three-dot glyph — spec §11: NO fussy animation.
        // Pinned: a row of three filled circles (dp-sized via CurroSpacing.l), no animation.
        Row(horizontalArrangement = Arrangement.spacedBy(CurroSpacing.s)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(CurroSpacing.l)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = MaterialTheme.shapes.large,
                        ),
                )
            }
        }
        Spacer(Modifier.height(CurroSpacing.l))
        Text(
            text = stringResource(R.string.copy_processing),    // "Un momento…"
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

// 3 @Previews as ListeningOverlay
```

### 8.5 `ExecutingOverlay.kt` — pinned sketch

```kotlin
@Composable
fun ExecutingOverlay(
    state: AssistantState.Executing,
    modifier: Modifier = Modifier,
) {
    // For Phase 5, state.screen is always null; future phases overlay
    // MessageCardsScreen / ContactPickerScreen when non-null.
    ExecutingOverlayContent(speech = state.speech, modifier = modifier)
}

@Composable
private fun ExecutingOverlayContent(speech: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(CurroSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BigCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = speech,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}
```

(`BigCard` exists in `presentation/common/` from US-006.)

### 8.6 `ErrorRecoveryOverlay.kt` — pinned sketch

```kotlin
@Composable
fun ErrorRecoveryOverlay(
    state: AssistantState.ErrorRecovery,
    modifier: Modifier = Modifier,
) {
    ErrorRecoveryOverlayContent(
        message = state.message,
        // failureCount is on the state so Phase 6+ overlays can render a
        // count-aware hint — Phase 5 doesn't use it but receives it.
        failureCount = state.failureCount,
        modifier = modifier,
    )
}

@Composable
private fun ErrorRecoveryOverlayContent(
    message: String,
    failureCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(CurroSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}
```

### 8.7 `LauncherPlaceholderScreen.kt` refactor

The new body of `LauncherPlaceholderContent` (sketch — preserving the
existing launcher home structure):

```kotlin
@Composable
internal fun LauncherPlaceholderContent(
    uiState: LauncherUiState,
    onEvent: (LauncherEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // The launcher home — clock + mic + favourites + permission CTAs.
        LauncherHome(uiState = uiState, onEvent = onEvent)

        // State-driven assistant overlay on top.
        when (val s = uiState.assistantState) {
            AssistantState.Idle -> Unit
            is AssistantState.Listening -> ListeningOverlay(s)
            is AssistantState.Processing -> ProcessingOverlay()
            is AssistantState.Confirming -> Unit  // SF-6.2 (Phase 6) owns this overlay
            is AssistantState.Executing -> ExecutingOverlay(s)
            is AssistantState.ErrorRecovery -> ErrorRecoveryOverlay(s)
        }
    }
}
```

`LauncherHome` is the extracted home composable (the current
`LauncherPlaceholderContent` body without the listening overlay). The file
after the refactor should sit at ≤ 200 lines (including the previews if they
stay; the brief permits moving the previews into a sister
`LauncherHomePreviews.kt` if that keeps the screen file lean — pin: keep the
previews in the screen file unless line count overruns).

### 8.8 Tests — `LauncherScreenStateTest.kt`

```kotlin
package com.curro.app.presentation.launcher

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.curro.app.assistant.AssistantState
import com.curro.app.domain.model.ClockState
import org.junit.Rule
import org.junit.Test

class LauncherScreenStateTest {

    @get:Rule val composeRule = createComposeRule()

    private fun stateFor(assistant: AssistantState) = LauncherUiState(
        isCurroDefault = true,
        clock = ClockState("12:47", "Miércoles 13 mayo"),
        favorites = emptyList(),
        assistantState = assistant,
        isNotificationAccessGranted = true,
    )

    @Test fun `Idle shows no overlay`() {
        composeRule.setContent {
            LauncherPlaceholderContent(stateFor(AssistantState.Idle), onEvent = {})
        }
        composeRule.onNodeWithText("Te escucho…").assertDoesNotExist()
        composeRule.onNodeWithText("Un momento…").assertDoesNotExist()
    }

    @Test fun `Listening shows the listening overlay with partial transcript`() {
        composeRule.setContent {
            LauncherPlaceholderContent(
                stateFor(AssistantState.Listening(partial = "Llama a Pepito", startedAtMs = 100)),
                onEvent = {},
            )
        }
        composeRule.onNodeWithText("Te escucho…").assertIsDisplayed()
        composeRule.onNodeWithText("Llama a Pepito").assertIsDisplayed()
    }

    @Test fun `Processing shows Un momento`() {
        composeRule.setContent {
            LauncherPlaceholderContent(
                stateFor(AssistantState.Processing("qué hora es", 100)),
                onEvent = {},
            )
        }
        composeRule.onNodeWithText("Un momento…").assertIsDisplayed()
    }

    @Test fun `Executing shows the speech text`() {
        composeRule.setContent {
            LauncherPlaceholderContent(
                stateFor(AssistantState.Executing("Llamando a Pepito.", null)),
                onEvent = {},
            )
        }
        composeRule.onNodeWithText("Llamando a Pepito.").assertIsDisplayed()
    }

    @Test fun `ErrorRecovery shows the recovery message`() {
        composeRule.setContent {
            LauncherPlaceholderContent(
                stateFor(AssistantState.ErrorRecovery("No te he oído bien, ¿puedes repetirlo?", 1)),
                onEvent = {},
            )
        }
        composeRule.onNodeWithText("No te he oído bien, ¿puedes repetirlo?").assertIsDisplayed()
    }

    @Test fun `transition from Listening to Processing swaps overlays`() {
        val state = mutableStateOf<AssistantState>(AssistantState.Listening("Llama a Pepito", 100))
        composeRule.setContent {
            LauncherPlaceholderContent(stateFor(state.value), onEvent = {})
        }
        composeRule.onNodeWithText("Te escucho…").assertIsDisplayed()
        // swap state
        state.value = AssistantState.Processing("Llama a Pepito", 200)
        composeRule.onNodeWithText("Un momento…").assertIsDisplayed()
        composeRule.onNodeWithText("Te escucho…").assertDoesNotExist()
    }
}
```

Pinned: these tests use `createComposeRule()` (not `createAndroidComposeRule<MainActivity>`)
— they exercise the **`Content` composable** with controlled state, per
`testing-patterns` rule "UI tests on the Content composables, not the
Screens". They run on the JVM via Robolectric (already set up in this project)
**or** as instrumented tests on the emulator — the implementer picks. **Pin:
prefer Robolectric** for speed; if Robolectric chokes on the static
`Composable` rendering, fall back to instrumented (`androidTest/`).

### 8.9 Hilt wiring

Nothing new.

### 8.10 ViewModels / Navigation / Material

Unchanged. The overlays are pure composables; they don't take a ViewModel.

---

## 9. Acceptance Criteria

- [ ] 4 new files in `presentation/assistant/`, each containing the
  `<Name>` + `<Name>Content` + 3 `@Preview` functions (light, dark,
  `fontScale = 2.0f`).
- [ ] `LauncherPlaceholderScreen.kt` body shrinks; the file is ≤ 200 lines
  (or the previews moved out). The `when (state)` exhaustive dispatch lives
  in `LauncherPlaceholderContent`.
- [ ] `AssistantState.Confirming -> Unit` branch is **present and commented**:
  `// SF-6.2 (Phase 6) owns this overlay`.
- [ ] Every overlay obeys senior-first:
  - Background tints / colours from `MaterialTheme.colorScheme.*` (no raw
    `Color`).
  - Type from `MaterialTheme.typography.*` (no `.sp` literals).
  - Spacing from `CurroSpacing.*` (no `.dp` literals).
  - Body text ≥ `headlineMedium` (28 sp per `brand-design`'s scale).
  - Every `Icon`/`Image` has `contentDescription` (or `null` with rationale).
  - `liveRegion = LiveRegionMode.Polite` on the listening + processing
    overlays; `LiveRegionMode.Assertive` on executing + error recovery.
- [ ] Each overlay survives `fontScale = 2.0f` — verified in the preview at
  that scale (no text clipping, no overlap).
- [ ] Light + dark mode verified via previews + the test grid.
- [ ] 6 Compose UI tests pass.
- [ ] No new `Color(0xFF…)` / `.sp` / `.dp` literals outside
  `presentation/theme/` (detekt rule already enforces this; pinned to
  re-verify).
- [ ] `copy_processing` already exists in `strings.xml` — verified, no new
  strings added.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest`
  green; the Compose tests (Robolectric or instrumented) green.

---

## 10. Design Notes

- **Why per-state files, not one big overlay?** Two reasons:
  1. **Single-responsibility**: each file owns one visual state. A visual
     regression is one diff.
  2. **Future phases**: Phase 6 swaps `Confirming -> Unit` for a real
     overlay; Phase 7 adds the contact-picker variant of `Executing`.
     Per-file makes those PRs surgical.
- **Why `LiveRegionMode.Polite` for listening + processing, `Assertive` for
  executing + error?** The first two are "I'm still here, here's what I
  see"; the second two are "this is what Curro is saying right now". TalkBack
  isn't the primary a11y target for this user (he uses TTS, not TalkBack),
  but the semantics are correct hygiene per
  `accessibility-patterns`. **Pin** — and the implementer may revisit if
  TalkBack testing shows otherwise.
- **Why `CurroListeningTint*` for `Listening` only?** Spec §11: "la pantalla
  se vuelve azul claro" — explicitly for listening. Other states use
  surface / surfaceVariant / errorContainer (the brand-design palette
  already has these contrasts measured ≥ 7:1).
- **Why no per-state route?** `launcher-ui` rule 3 is explicit: "Overlays
  are state-driven, not nav routes; only home + config menu are routes."
  Routes break the interrupt rule (back-stack handling vs. FSM
  interrupt is two sources of truth — disaster).
- **Why is `ExecutingOverlay` "just a `BigCard`" and not the
  `MessageCardsScreen`?** Because Phase-4 handlers always return
  `HandlerResult.Spoken(screen = null)`. The `screen` non-null branch lights
  up in Phase 6/7. The brief leaves the `state.screen` parameter on the
  signature so Phase 6/7's add is `when (state.screen) { … }`, not a
  re-signature.
- **Why static "Un momento…" indicator (three dots, no animation)?** Spec
  §11: *"con un indicador visual no animado (animaciones complejas
  distraen)"*. The Phase-2 overlay had an animated wave; this SF removes
  the animation. Pin: the three dots are *static* — three filled circles
  in a row, full stop. No flicker, no bouncing.

---

## 11. Senior-UX & Copy

No new copy. Uses existing:
- `copy_listening_prompt` — "Te escucho…" (line 18 in `strings.xml`).
- `copy_processing` — "Un momento…" (line 20).
- `copy_stt_fail_1/2/3` — already wired (used as `state.message`).
- The various `copy_*` lines spoken by handlers — already wired
  (`state.speech`).

---

## 12. Performance Considerations

- The overlays are simple `Column` / `Row` / `Box` layouts — no lazy lists,
  no expensive measurement.
- The state-driven `when` recomposes only the active branch; transitions
  between states cause one composable to leave + one to enter, which is the
  standard Compose model.
- The `liveRegion` semantics add minimal overhead (TalkBack hint metadata).
- No `LaunchedEffect` chains, no `produceState`, no flow collection inside
  the overlays — they're pure transformations of state.

---

## 13. Testing Requirements

See §8.8 — 6 Compose tests, preferred Robolectric.

Also verified by the manual smoke list in US-036 §13.3 — driving the full
pipeline on a Redmi 15 should show each overlay correctly without "Te
escucho…" lingering after the partial finalises, etc.

---

## 14. Implementation Notes

**PM Owner wrote**: every section.

**Architect / voice-pipeline-engineer fills in (during implementation)**:
the precise `BigCard` width inside `ExecutingOverlay` (fillMaxWidth vs.
wrap); the exact static indicator drawing in `ProcessingOverlay` if "three
dots" feels off; whether the live transcript needs a `verticalScroll`
modifier on long partials (pin: not for Phase 5 — partials in normal use are
short).

**Commit message (pinned)**:

```
feat: state-driven assistant overlays (US-039 / SF-5.5)

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft — pinned the four per-state overlays, the `Confirming -> Unit` placeholder, the static "Un momento…" indicator, and the 6 Compose tests. |
