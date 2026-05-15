# US-018 — SF-2.4 · `ListeningOverlay` composable (visual)

> **Spec trace:** spec §11 ("Mientras escucha: la pantalla se vuelve azul claro,
> aparece 'Te estoy escuchando…' y la transcripción en tiempo real abajo en texto
> grande"), launcher-ui surface 2
> **Master-plan:** SF-2.4
> **Phase:** 2 — Voice pipeline
> **Depends on:** US-017 (the screen wiring + `ListeningState`), US-005
> (`CurroListeningTintLight` / `CurroListeningTintDark` already in `Color.kt`)
> **Size:** S

---

## 1. Goal

Give the provisional listening state from US-017 its proper visual: a calm,
predictable, senior-first overlay that signals "I'm listening to you" or "I'm
speaking back to you" without movement that distracts. Live transcript visible
without making the layout shift. Audio-wave indicator is the *minimum needed* —
five thick bars with a slow pulse, pure Compose, no Lottie.

This is the smallest of the Phase-2 SFs by code volume — but it's also the one
the user will *see* for the next year. Make it feel right.

---

## 2. Scope

**In scope:**

- `ListeningOverlay.kt` composable in `presentation/assistant/`.
- A small `AudioWaveIndicator` sub-composable (also in
  `presentation/assistant/` — same file or `AudioWaveIndicator.kt`, dev's call).
- `MicButton.kt` extension: `isListening: Boolean = false` parameter that
  swaps the background colour to `MaterialTheme.colorScheme.secondary` (olive).
- 4 `@Preview` variants.
- UI test asserting the live-transcript update doesn't shift layout.

**Out of scope:**

- The full state-driven overlay system (Phase 5).
- The processing overlay ("Un momento…") — Phase 5.
- The confirmation overlay (SÍ/NO) — Phase 6.
- The message-cards screen — Phase 4.

---

## 3. User flow

There is no standalone flow — `ListeningOverlay` is the visual that accompanies
US-017's flows 1–4. When the launcher home's `LauncherUiState.listeningState !is
Idle`, the overlay covers the launcher home with `Modifier.fillMaxSize()`.

The overlay shows two visual modes:

- **Listening** (`ListeningState.Starting`, `Listening`): tint, "Te escucho…",
  partial transcript, **animated** audio-wave.
- **Speaking** (`ListeningState.Speaking`): tint, "Te escucho…" (kept, to avoid
  layout shift; signals "still in the conversation"), spoken text, **static**
  audio-wave (5 bars held at mid-height).
- **Error** (`ListeningState.Error`): tint, **error message** instead of "Te
  escucho…", no transcript line, **static** audio-wave.

`Idle` is not rendered — `AnimatedVisibility` in US-017 ensures the composable
is not in the tree.

---

## 4. Function-catalog impact

**No catalog change.**

---

## 5. FSM states touched

This SF **renders** US-017's provisional `ListeningState`. Phase 5 will replace
the consumer (the state owner), but the overlay's three visual modes
(listening / speaking / error) map straightforwardly to the eventual
`listening / executing / error_recovery` states. The composable's `state`
parameter is `ListeningState` (the US-017 type); Phase 5 introduces a wrapping
adapter (or a renamed sealed interface) and updates the call site.

---

## 6. Android system integrations & permissions

**None.** Pure Compose.

---

## 7. On-device-model impact

**No model impact.** The audio-wave is decorative — it does NOT reflect real
audio levels (no `RmsChanged` plumbing; `RmsChanged` would be a nice-to-have but
Phase 2's goal is "feels alive enough"; deferred).

---

## 8. Android specification

### 8.1 The composable — `presentation/assistant/ListeningOverlay.kt`

Sketch (the dev fills in the actual code):

```kotlin
package com.curro.app.presentation.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.curro.app.R
import com.curro.app.presentation.launcher.ListeningState
import com.curro.app.presentation.theme.CurroListeningTintDark
import com.curro.app.presentation.theme.CurroListeningTintLight
import com.curro.app.presentation.theme.CurroSpacing

@Composable
fun ListeningOverlay(
    state: ListeningState,
    modifier: Modifier = Modifier,
) {
    if (state is ListeningState.Idle) return  // defensive; US-017 already guards via AnimatedVisibility

    val tint = if (isSystemInDarkTheme()) CurroListeningTintDark else CurroListeningTintLight
    val isActive = state is ListeningState.Listening || state is ListeningState.Starting

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = CurroSpacing.l),
        ) {
            // Headline — "Te escucho…" stays visible across Listening and Speaking;
            // replaced by the error message when state is Error.
            val headline = when (state) {
                is ListeningState.Error -> state.message
                else -> stringResource(R.string.copy_listening_prompt)  // "Te escucho…"
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.displayMedium,                  // 48 sp
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    // Announces transcript updates to TalkBack as they appear.
                    liveRegion = LiveRegionMode.Polite
                },
            )

            Spacer(modifier = Modifier.height(CurroSpacing.l))

            // Transcript / spoken text — visible in Listening and Speaking; empty in Error.
            val transcript = when (state) {
                is ListeningState.Listening -> state.partialText
                is ListeningState.Speaking -> state.text
                else -> ""
            }
            if (transcript.isNotEmpty()) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodyLarge,                  // 20 sp
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                Spacer(modifier = Modifier.height(CurroSpacing.xl))
            }

            // Audio-wave indicator.
            AudioWaveIndicator(animated = isActive)
        }
    }
}
```

### 8.2 The audio-wave — `AudioWaveIndicator` (same file or its own)

```kotlin
@Composable
private fun AudioWaveIndicator(
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val barCount = 5
    val periodMs = 1_200                                    // slow — see §11
    val transition = rememberInfiniteTransition(label = "audiowave")

    Row(
        horizontalArrangement = Arrangement.spacedBy(CurroSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        repeat(barCount) { i ->
            val phase = i * (periodMs / barCount.toFloat()).toInt()
            val heightFraction = if (animated) {
                val fraction by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = periodMs,
                            easing = FastOutSlowInEasing,
                            delayMillis = phase,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "bar$i",
                )
                fraction
            } else {
                0.55f                                       // static mid-height
            }
            Box(
                modifier = Modifier
                    .width(CurroSpacing.m)                  // ≈12 dp; calibrate to feel right
                    .height((48.dp * heightFraction))       // peak ≈ 48 dp
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                    )
            )
        }
    }
}
```

Notes for the dev:

- Picking the **bar count** (5) and **period** (1.2 s) is non-negotiable from
  the brief: 5 bars are enough to feel alive, fewer feels sparse, more competes
  with the headline; 1.2 s is slow enough that the senior-first rule "no fussy
  animation" is respected (typical Lottie spinners run at 0.5–0.8 s — too fast).
- The bar **width** and **peak height** are calibrated values, not free
  parameters — the dev can fine-tune by ±20 % during preview review with the
  user; document the chosen final values in `Dimens.kt` if shared, or inline
  with a comment if local to this composable.
- The animation uses `rememberInfiniteTransition` which **never settles** — at
  recomposition cost, this is fine: Compose's animation system handles it
  efficiently on Android 12+.
- The **static** state (`animated = false`) holds all 5 bars at `0.55f` height.
  This is the "I'm speaking" visual — distinct from listening (active pulse)
  and distinct from idle (overlay not rendered at all). A constant non-zero
  visual signals "the assistant is engaged" while not implying it's *receiving*
  input.

### 8.3 `MicButton.kt` extension

The MicButton gets one new parameter:

```kotlin
@Composable
fun MicButton(
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isListening: Boolean = false,                          // NEW (US-018)
) {
    // ... existing body, with the colour selection changed to:
    color = when {
        !enabled       -> MaterialTheme.colorScheme.surfaceVariant
        isListening    -> MaterialTheme.colorScheme.secondary      // olive — "tap again to cancel"
        else           -> MaterialTheme.colorScheme.primary        // terracotta — default
    },
    // ... the icon and label tints likewise use onSecondary when isListening.
}
```

The colour swap signals two things at once:

- "Curro is engaged" (matches the overlay being up).
- "Tap me again to cancel/restart" — the colour change is the affordance.

Verify in the previews that `onSecondary` text on `secondary` background is
legible (per `Color.kt`'s docblock, secondary in light = OliveLight #4F5D2E,
onSecondary = CreamWhite; ratio ~6.8:1 — well above the floor).

---

## 9. Senior-UX & copy

Strings reused (no new strings):

- `copy_listening_prompt` — "Te escucho…" (already in `strings.xml`).

The Error state's message is supplied by US-017 (already a localised string
read in the ViewModel via `getString`); the overlay just renders it.

The composable upholds the senior-first rules:

- **Tap targets:** the overlay itself is not interactive — the MicButton
  underneath remains the (huge) tap target.
- **Text size:** `displayMedium` (48 sp) for "Te escucho…", `bodyLarge` (20 sp)
  for the transcript. Both well above Material defaults and configured by
  US-005.
- **Contrast:** `onBackground` (DarkText `#1A1410` light / LightText `#FFEBD9`
  dark) on the tint — 11.8:1 and 12.7:1 respectively (pre-measured in
  `Color.kt`).
- **No fussy animation:** the audio-wave is **slow** (1.2-s period) with a
  smooth ease curve; no spinners, no shimmer, no parallax.
- **Audio + visual together:** the overlay is the visual reinforcement of
  Curro's spoken state; the spec rule is upheld.
- **Predictable layout:** the overlay covers the entire screen, the headline is
  centered, the transcript wraps and ellipses at 4 lines — the layout does not
  shift as partials arrive.
- **TalkBack:** the headline and transcript are `liveRegion = Polite` so screen
  readers announce updates as they arrive — relevant if the user's vision
  worsens.

---

## 10. Acceptance criteria

- [ ] `ListeningOverlay.kt` exists at
  `app/src/main/java/com/curro/app/presentation/assistant/ListeningOverlay.kt`
  with the signature `fun ListeningOverlay(state: ListeningState, modifier:
  Modifier = Modifier)`.
- [ ] `AudioWaveIndicator` (private composable, same package) — 5 bars,
  period 1.2 s, phased; pure Compose, no external animation dep.
- [ ] Background colour reads `CurroListeningTintLight` in light mode,
  `CurroListeningTintDark` in dark — via `isSystemInDarkTheme()`. **`Color.kt`
  is NOT modified** (the tokens already exist from US-005).
- [ ] "Te escucho…" headline: `stringResource(R.string.copy_listening_prompt)`,
  `MaterialTheme.typography.displayMedium`, colour
  `MaterialTheme.colorScheme.onBackground`, centered.
- [ ] When `state is Error`, the headline is replaced by `state.message`.
- [ ] Transcript line: `state.partialText` for `Listening`, `state.text` for
  `Speaking`, empty otherwise; `MaterialTheme.typography.bodyLarge`,
  `maxLines = 4`, `overflow = TextOverflow.Ellipsis`, padded horizontally.
- [ ] `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on both
  headline and transcript.
- [ ] Audio-wave is **animated** when `state is Listening | Starting`; **static
  mid-height** when `state is Speaking`; **static mid-height** when `state is
  Error`.
- [ ] `MicButton` `isListening: Boolean = false` parameter added; when `true`,
  background is `secondary`, on-content uses `onSecondary`.
- [ ] `LauncherPlaceholderContent` (in US-017) passes `isListening =
  (listeningState !is Idle)` to `MicButton`.
- [ ] 4 `@Preview` variants in `ListeningOverlay.kt`:
  - "Light, Listening, short partial" — 1–2 word transcript.
  - "Dark, Listening, long partial" — 4-line transcript with ellipsis.
  - "Light, Speaking, medium text" — transcript visible, audio-wave static.
  - "fontScale = 2.0, Listening, long partial" — verify layout survives senior
    font size.
- [ ] UI test in
  `app/src/androidTest/java/com/curro/app/presentation/assistant/ListeningOverlayTest.kt`
  (or `app/src/test/` with Robolectric — dev's choice; emulator-instrumented is
  preferred since SF-2.4 is a UI-level SF):
  - T1 — `state = Listening("")` → "Te escucho…" is on screen.
  - T2 — recompose with `state = Listening("hola")` → "hola" appears below
    "Te escucho…", **same position** for "Te escucho…" (no shift; assert via
    semantic-tree position equality).
  - T3 — `state = Error("No te he oído bien…")` → the error text replaces
    "Te escucho…"; no transcript line.
  - T4 — `state = Speaking("Hola Curro")` → "Hola Curro" visible; audio-wave
    is in static mode (assert via a test-tag the dev adds to the indicator).
- [ ] Manual check on Redmi 15: the overlay enters and leaves with a single
  ~150 ms fade; the audio-wave does not visibly drop frames (verify with
  `dumpsys gfxinfo com.curro.app`; the per-frame time should stay under 16 ms).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green; if the UI test is instrumented,
  `./gradlew connectedDebugAndroidTest` also green.

---

## 11. Design rationale — the slow pulse

The senior-first rule "no fussy animation" (spec §11, `launcher-ui` rule 6)
deserves the longest argument in this brief:

- **Why animate at all?** Without any movement, the overlay reads as "frozen" —
  exactly what an elderly user fears when the app stops responding. A small,
  slow movement says "I'm alive, I'm listening" without grabbing attention.
- **Why 1.2 s period?** Material-spec spinner animations are typically 0.5–0.8
  s. That's too fast — it competes with the spoken "Te escucho…" and looks
  *busy*. 1.2 s is roughly the period of a calm breath; it feels reassuring,
  not impatient.
- **Why 5 bars, not 1 or 10?** One bar feels like a heartbeat (clinical); two
  feels like a pendulum (too literal); 3–5 reads as "voice activity" without
  literally claiming to be audio level. 5 with phased peaks is the smallest
  count where the pattern reads as "wave" not "bars".
- **Why pure Compose, not Lottie?** Lottie adds 250+ KB to the APK and a JSON
  asset. The 25-line `AudioWaveIndicator` is enough; it composes cleanly with
  the theme system, can be tested without a renderer, and won't break with a
  Compose update.
- **Why `MaterialTheme.colorScheme.primary` for the bars?** Terracotta on the
  blue tint reads as "Curro is here" — the brand colour amid the listening
  colour. The contrast (~5.4:1 between `primary` terracotta `#9A3E15` and the
  light tint `#B8D4E8`) is above the floor for graphical objects.

If the user-validation review (post-Phase 2) finds the pulse too prominent,
reducing the bars to 3 or lengthening the period to 1.5 s are both safe
tweaks — make them then, not now.

---

## 12. Strings delta

No new strings. All copy already in `strings.xml`.

---

## 13. Test plan

See §10 acceptance for the 4 UI test cases.

**No new unit tests** at the `ListeningOverlay` level — the composable is
stateless (state in, render out). The state-management tests are in US-017's
ViewModel test suite.

**Preview-driven design review:** before signing off, the dev should record a
screen capture of the overlay on the Redmi 15 (Android Studio's screen-recorder
suffices) and review it with Fran. The "calm vs. fussy" judgement is
subjective; record a 5-second loop of each preview state and watch them in
real time.

---

## 14. Files changed

**New:**

- `app/src/main/java/com/curro/app/presentation/assistant/ListeningOverlay.kt`
  (includes the private `AudioWaveIndicator` composable, or split it into
  `AudioWaveIndicator.kt` in the same package — dev's call).
- `app/src/androidTest/java/com/curro/app/presentation/assistant/ListeningOverlayTest.kt`
  (or Robolectric equivalent under `app/src/test/`).

**Modified:**

- `app/src/main/java/com/curro/app/presentation/launcher/MicButton.kt` — add the
  `isListening: Boolean = false` parameter and the conditional colour.
- (US-017 modifies `LauncherPlaceholderContent` to wrap the overlay; US-018
  does not re-modify the screen — the dev coordinates ordering so US-018's
  composable exists by the time US-017's screen wrapper references it.)

**Not touched:** `Color.kt` (`CurroListeningTint*` already exist from US-005),
`Type.kt`, `Shape.kt`, `CurroSpacing.kt`, `Dimens.kt`, `CurroTheme.kt`,
`strings.xml`, the manifest, any DI module, `ClockBlock.kt`, `AppTileGrid.kt`,
`AppTile.kt`, `MoreAppsScreen.kt`.

---

## 15. Reference skills

- `launcher-ui` — surface 2 (Listening overlay) — the canonical UX spec.
- `accessibility-patterns` — `liveRegion`, semantic-tree update on transcript
  changes; large font + high contrast already enforced by the theme.
- `compose-patterns` — `isSystemInDarkTheme`, `rememberInfiniteTransition`,
  `animateFloat` with phased delays, `Modifier.semantics`.
- `brand-design` — colour and type tokens (US-005 sourced).
- `voice-interaction` — the conceptual states this overlay represents.
- `git-workflow` — commit scope `feat(assistant):` (since the new package is
  `presentation/assistant/`).
