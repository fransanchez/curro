# Verification Checklist

Complete this checklist before marking a task as done. It's the build → lint → test
→ run-on-the-device → privacy/permissions pass. Pair it with `testing-patterns`,
`accessibility-patterns`, `voice-interaction`, `on-device-llm`, `launcher-app`,
`function-catalog`, and `git-workflow`.

## Build Verification

```bash
./gradlew assembleDebug
```

- [ ] Build succeeds with no errors
- [ ] No new deprecation warnings
- [ ] No resource conflicts
- [ ] **Builds without the model weights** — `assembleDebug` succeeds even if the FunctionGemma / Gemma 3n files aren't present (the engine is guarded on file-present; the weights are not in git and not in the debug build — see `on-device-llm`). CI builds `assembleDebug`, so this must hold.

## Lint and Code Quality

```bash
./gradlew ktlintCheck detekt
```

- [ ] ktlint passes (Kotlin formatting)
- [ ] detekt passes (static analysis)
- [ ] No style violations

Fix formatting:
```bash
./gradlew ktlintFormat   # auto-fix Kotlin formatting
```

## Unit Tests

```bash
./gradlew test
```

- [ ] All tests pass
- [ ] No skipped tests
- [ ] New code is covered — for Curro work, the must-haves: `AssistantStateMachine` transitions + the interrupt rule + the consecutive-STT-failure recovery + the `confirming` 10 s timeout; `FunctionCallValidator` (every malformation → the right `CurroError`, no auto-retry); `WhatsAppNotificationParser` fixture suite; handlers against faked integrations; in-memory Room DAO + `SettingsRepository`; `ConfidencePolicy`; the alias-learning subflow (see `testing-patterns`)
- [ ] LLM / STT / TTS are **faked** in JVM tests — no real model weights loaded

Run one class / method:
```bash
./gradlew test --tests "com.curro.app.assistant.AssistantStateMachineTest"
./gradlew test --tests "*.WhatsAppNotificationParserTest.*group*"
```

## Emulator / Device Testing

Run on an emulator or, for anything voice/ML/launcher, **the real device** (see "On
the real Redmi 15" below):

- [ ] App launches without crash
- [ ] The feature works as intended
- [ ] No ANR (app not responding)
- [ ] Transitions are quick and quiet (no fussy animation — spec §11)
- [ ] Memory usage reasonable; no OOM under repeated use

Commands:
```bash
emulator -list-avds
emulator -avd Pixel_8_API_35
./gradlew installDebug

# Curro is a launcher — set it as the home app and bring it to front:
adb shell cmd package set-home-activity com.curro.app/.MainActivity
adb shell cmd package query-home-activities          # verify which app is the home activity
adb shell am start -n com.curro.app/.MainActivity
```

## UI Tests (Compose)

```bash
./gradlew connectedAndroidTest
```

- [ ] All UI tests pass (uses `com.curro.app.HiltTestRunner`)
- [ ] Compose previews render — including the `fontScale = 1.5f` / `2.0f` previews
- [ ] Touch interactions work; the 5-tap-on-clock config gesture works, a single tap doesn't
- [ ] Screen states (listening / processing / confirming / message cards / picker) render and are state-driven, not nav routes

## Code Quality Checks

### No force unwraps
- [ ] No `!!` in new code — use `?.let`, `?:`, or `requireNotNull(x) { "msg" }`

### Coroutine safety
- [ ] Coroutines launched in `viewModelScope` (or a service scope) — not `GlobalScope`
- [ ] No blocking work on the main thread — model inference, STT/TTS, ContentResolver queries, Room, file I/O run on `Dispatchers.IO` (or a dedicated dispatcher)
- [ ] UI state updates on the main thread

```kotlin
viewModelScope.launch {
    val result = withContext(Dispatchers.IO) { engine.decide(utterance, ctx) }
    _uiState.value = result.fold(::toReady, ::toError)
}
```

### Hilt injection
- [ ] ViewModels obtained via `hiltViewModel()` — no manual instantiation in composables
- [ ] All dependencies injected; no missing `@Provides`/`@Binds`
- [ ] Handlers registered in the function-name-keyed multibinding map
- [ ] Engines (`FunctionCallEngine`/`TextGenEngine`), STT/TTS clients, repositories bound to their `domain/repository/` interfaces

```bash
./gradlew kaptDebug   # surfaces Hilt component errors
```

### Memory / lifecycle
- [ ] ViewModels use `viewModelScope`; no leaked `Context`/`Activity`
- [ ] Listeners cleaned up (`NotificationListenerService`, `SpeechRecognizer`, `TextToSpeech`); the `ModelWarmupService` is `START_STICKY` and recovers if killed

## On-device Model Check (any LLM/ML work)

- [ ] Model weights are **not in git** and **not in the debug build** — guarded on file-present; `assembleDebug` builds and runs without them
- [ ] `FunctionGemmaEngine` / `Gemma3nEngine` sit behind `domain/repository/` interfaces; **MediaPipe is imported only in `data/ml/`** — nothing else references it; tests use fakes
- [ ] Inference runs off the main thread; `processing` shows a **non-animated** "Un momento…" indicator
- [ ] Invalid model output → friendly fallback ("Eso no lo sé hacer todavía…") + logged to the failed-commands log + **no auto-retry**
- [ ] Last inference latency surfaced in the config-menu diagnostics

## Privacy & Permissions Check

- [ ] **Telemetry SDKs (Firebase Crashlytics/Analytics, PostHog) receive no PII / transcripts / message content / contact names** — only event names + safe properties (see `CLAUDE.md` → Privacy & telemetry)
- [ ] The **core app declares no `INTERNET` permission** — only the telemetry path uses the network, kept isolated/feature-flagged; nothing else needs the network
- [ ] Permissions are **requested lazily**, per spec §10 — the user never sees a prompt for a capability they aren't using (`RECORD_AUDIO` on first listen; `READ_CONTACTS`/`CALL_PHONE` on first `call_contact`; notification access on first WhatsApp function; etc.)
- [ ] On a revoked permission, the failure is a **plain Spanish "díselo a Fran"** — never a crash, never a raw `SecurityException`, never a code
- [ ] Spanish strings come from resources / the copy module (in Curro's voice — `brand-design`); none hard-coded in composables
- [ ] No transcripts / contact data in `interaction_log`; the "send failures to Fran" toggle is off by default and only sends *anonymized* failure logs
- [ ] No `local.properties` / `*.keystore` / `*.jks` / `.env*` / `google-services.json` committed

## Accessibility Review — senior-first (any UI change)

The user has deteriorated-but-functional vision, good hearing, reduced fine motor
control, and a slow UI learning curve. Material's 48 dp / default type scale is the
**floor**, not the target (see `accessibility-patterns`, `launcher-ui`):

- [ ] All interactive elements ≥ **96 dp**; the mic button ≥ 40 % of the screen; SÍ/NO and app tiles are huge; generous spacing between targets
- [ ] Text is **big** — body text well above Material defaults; the clock is enormous; the layout still works at `fontScale = 1.5f` / `2.0f` (don't cap the system font setting)
- [ ] **High contrast** — ≥ 4.5:1 floor, ≥ 7:1 for body where possible; never colour-only signalling (pair with text/icon/shape)
- [ ] **Audio + visual together** — every Curro→user message is **spoken AND shown** (spec §4.6)
- [ ] `contentDescription` on every `Image`/`Icon` (or `null` if purely decorative)
- [ ] **No fussy animation** — calm/static indicators; quick, quiet transitions
- [ ] **It feels the same as last time** — the home layout is fixed; favourites recompute *occasionally*, not on every open; new visual states only appear when the user triggered them

Test with TalkBack:
```bash
adb shell svc power stayon true   # keep the screen on
# enable TalkBack: Settings → Accessibility → TalkBack
```

## Dark Mode Testing

- [ ] UI renders correctly in dark mode
- [ ] Colours come from the theme; no hard-coded colours
- [ ] Text is readable, contrast holds, in both modes

```bash
adb shell cmd uimode night yes
adb shell cmd uimode night no
adb shell cmd uimode night toggle
```

## The Assistant FSM (any voice/state work)

- [ ] Every transition in spec §6's diagram works (flows 1–7)
- [ ] A button press **interrupts any state** and returns to `listening` (cancels STT / inference / TTS / pending confirmation)
- [ ] The **10 s `confirming` timeout** fires → "Cancelo entonces" → `idle`; "no"/NO → "Vale, no llamo" → `idle`
- [ ] The 1st / 2nd / 3rd consecutive STT-failure messages fire, then the give-up, then the counter resets
- [ ] The disambiguation list repeats once then gives up honestly — no loop
- [ ] `onNewIntent` / HOME press resets the FSM to `idle`

## On the Real Redmi 15 (anything voice / ML / launcher)

- [ ] Offline Spanish STT works **with no network** (`SpeechRecognizer`, ES voice pack installed)
- [ ] The Spanish TTS voice is intelligible at the slowed rate (~10–15 % slower default)
- [ ] **Warm FunctionGemma latency < 500 ms** text→JSON — record the figure
- [ ] No OOM under repeated use; Gemma 3n (if loaded) is freed under memory pressure, FunctionGemma stays warm
- [ ] The `ModelWarmupService` survives a screen-off period, or recovers (reload + a one-off "Dame un segundo") — Curro must be battery-whitelisted on HyperOS (see `launcher-app`)
- [ ] Curro is / can be made the **default launcher**; the HOME button returns to a **fresh** launcher (FSM → `idle`)
- [ ] Notification access reads WhatsApp; `ACTION_CALL` dials directly; `open_app` opens the named app; incoming-call mode (when toggled on) announces & answers — and toggled **off**, incoming calls behave 100 % natively

## Performance Check

- [ ] No unused imports / dead code
- [ ] No oversized functions (< 20 lines)
- [ ] Compose recomposition reasonable (stable params, no needless lambdas in hot paths)
- [ ] No infinite loops, no leaks

Profile with the Android Studio Profiler (View → Tool Windows → Profiler) if perf is suspect.

## Documentation

- [ ] Code comments for non-obvious logic
- [ ] KDoc on public APIs that need it
- [ ] `CLAUDE.md` updated if architecture/layout changed
- [ ] The relevant skill updated if a pattern changed; the **`function-catalog` skill + `docs/curro-spec-v1.0.md` §5 + `domain/catalog/`** updated together if a catalog function changed
- [ ] `docs/briefs/US-XXX-….md` tasks ticked

## Final Pre-commit Checklist

- [ ] Branch name follows convention (off `main` — `feature/US-XXX-…`)
- [ ] All commits follow conventional commits with a Curro scope (`git-workflow`)
- [ ] Related user story referenced (US-XXX)
- [ ] Co-author credited if applicable
- [ ] PR description complete (`/generate-mr-description`)
- [ ] All checks above passed
- [ ] No sensitive data / no model weights committed
- [ ] Ready for code review
</content>
