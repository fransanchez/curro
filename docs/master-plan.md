# Curro — Master Plan

> **Purpose.** This is the planning artifact between the descriptive product spec
> (`docs/curro-spec-v1.0.md`) and the per-story implementation briefs
> (`docs/briefs/US-XXX-*.md`). It breaks the spec down into concrete sub-features
> (SFs), one paragraph each, sized so every SF becomes a single `US-XXX` user story
> in `docs/PRD.md`. **Read the spec first.** When implementation surfaces a gap
> that contradicts the spec, refine the spec (and bump its version) — don't fork
> the decision into a brief.
>
> **Created:** 2026-05-13 — **Spec basis:** `curro-spec-v1.0.md` (v1.0, May 2026)

---

## 0. Framing

**Curro** is an Android home-screen launcher + on-device voice assistant for a
single validated user — Fran's father in Málaga, on a Xiaomi Redmi 15 (Android 15
+ HyperOS 2/3). The user presses one big mic button on the home screen, speaks in
colloquial Spanish, and Curro reads WhatsApp, calls a contact, opens an app,
calculates, or tells the time — entirely on-device, no network, no accounts (spec
§1, §11). The voice pipeline is: `SpeechRecognizer` (offline ES) → **FunctionGemma
270M** (decides which catalog function + params + confidence) → optional
**Gemma 3n E2B** (only when natural-language generation is needed) → native Kotlin
handler → `TextToSpeech` (ES). A six-state assistant FSM
(`idle/listening/processing/confirming/executing/error_recovery`) orchestrates it,
with hard interrupt-by-button (spec §6).

**The validation question (spec §13):** does Fran's father actually use this app,
and does it improve his day? The prototype is a validation instrument, not a
product. We will treat a feature as worth shipping only if it serves that question.

**Non-negotiable constraints (spec §2, §3, §11):**

- **On-device** — no cloud. STT, both Gemmas, the handlers, the data, everything.
- **Senior-first** — tap targets ≥ 96 dp (not Material's 48 dp), text well above
  Material defaults, ≥ 7:1 contrast where the palette allows, no fussy animation,
  layout that "feels the same every day", audio + visual together always.
- **Curro's voice** — warm, Andalusian, colloquial; efficient and close, not
  servile; fails comprehensibly (a sentence + an alternative, never a code, never
  silence).
- **No `INTERNET` permission in the core app.** The only network user is the
  telemetry SDKs — see deviations below.

**Deviations from spec v1.0 (must bump to v1.1):**

1. **Telemetry kept on.** Spec §12 says "nothing leaves the device". The project
   now keeps Firebase Crashlytics + Analytics and PostHog (`CLAUDE.md` → Privacy
   & telemetry). Audio, transcripts, message bodies, contacts, and aliases still
   never leave the device. The telemetry path must be isolated (separate process
   or build-flag) and must never receive transcripts, names, or message bodies.
   → Spec §12 to be revised; until then the project rulebook in `CLAUDE.md` wins.
2. **§5 header says "8 funciones" but lists 7.** Don't invent an 8th. Resolve in
   the v1.1 bump.

**Target hardware:** Xiaomi Redmi 15 5G, Snapdragon 6s Gen 3, Android 15 + HyperOS.
**Open hardware question:** the 4 GB vs. 8 GB RAM variant — confirm before
relying on Gemma 3n (Phase 9). HyperOS aggressively kills background services →
Curro must be battery-whitelisted and have autostart enabled (`launcher-app`).

---

## Phase 0 — Project foundation _(spec §14)_

**Goal.** Bring up an empty but production-shaped Android project so subsequent
phases have somewhere to plug in: Gradle (KTS) + version catalog; Hilt + a
`CurroApp`; a Material-3 `CurroTheme` (scaled up for seniors); a one-screen
nav shell; lint/format wired; LiteRT/MediaPipe deps declared (no models loaded
yet). At the end of Phase 0, `./gradlew assembleDebug` builds an installable APK
that opens a placeholder home screen.

**Sub-features.**

1. **SF-0.1 — Gradle skeleton & version catalog.** Generate the Gradle project
   (Kotlin DSL, Compose, single `app` module, AGP/Kotlin/Compose-BOM in
   `libs.versions.toml`); `compileSdk` 35, `minSdk` 31, `targetSdk` 35; signing
   config plumbing for release; `assembleDebug` green on CI. Implements spec §14
   stack. **Acceptance bar:** `./gradlew assembleDebug` produces an installable
   APK on a fresh clone. `[catalog: —] [FSM: —] [permissions: —] [integrations: —]
   [model: deps only, no weights] [UI: —] [copy: —]`. Agents/skills:
   `android-developer`, `launcher-app`. **Size: M.** Depends on: nothing.

2. **SF-0.2 — Hilt + `CurroApp` + base packages.** Wire Hilt (`@HiltAndroidApp
   CurroApp`), set up Hilt entry points, and stamp the package skeleton from
   `CLAUDE.md`'s "Architecture" section (`domain/`, `data/`, `handler/`,
   `assistant/`, `service/`, `presentation/`, `util/`) with `.gitkeep`s. Wire the
   Hilt test runner (`com.curro.app.HiltTestRunner`). **Acceptance bar:** the
   empty app installs and runs without crashing; `connectedAndroidTest` runs an
   empty Hilt-injected smoke test. `[UI: —] [copy: —]`. Agents/skills:
   `android-developer`, `android-qa-specialist`. **Size: S.** Depends on: SF-0.1.

3. **SF-0.3 — Lint, format & detekt.** Wire ktlint, detekt, the No-Double-Padding
   convention rule (custom detekt rule or doc note), and a pre-commit-friendly
   `./gradlew ktlintCheck detekt` task. CI runs them on every PR. **Acceptance
   bar:** `./gradlew ktlintCheck detekt` is green; CI fails on violation.
   Agents/skills: `android-developer`, `git-workflow`. **Size: S.** Depends on:
   SF-0.1.

4. **SF-0.4 — `CurroTheme` scaffold + senior-first tokens.** Build `CurroTheme`
   wrapping Material 3 with `CurroColorScheme` (light + dark, placeholder values
   from `brand-design` until SF-0.7 fills the real brand), `CurroTypography`
   (body-role *headline-sized* — not Material defaults), `CurroShapes`,
   `CurroSpacing`. Hard-coded `Color/.sp/.dp` literals **banned in composables**.
   `dynamicColor = false` — fixed palette, "feels the same every day". Implements
   spec §11. **Acceptance bar:** a smoke composable renders with all tokens, dark
   mode flips correctly, `fontScale = 2.0f` preview survives.
   `[UI: theme tokens] [copy: —]`. Agents/skills: `android-ui-designer`,
   `material-design`, `brand-design`, `compose-patterns`. **Size: M.** Depends on:
   SF-0.2.

5. **SF-0.5 — Shared big components.** Build `BigPrimaryButton` (≥ 96 dp),
   `BigYesNoRow`, `BigCard`, `BigListRow` in `presentation/common/`, each with
   light/dark/large-font `@Preview`s. These are the building blocks for every UI
   in later phases — sizing/contrast consistency starts here (`launcher-ui` rule
   4). **Acceptance bar:** previews look right at `fontScale = 2.0f`; accessibility
   sweep shows every clickable ≥ 96 dp, every `Icon`/`Image` has
   `contentDescription`. `[UI: components] [copy: —]`. Agents/skills:
   `android-ui-designer`, `launcher-ui`, `accessibility-patterns`. **Size: M.**
   Depends on: SF-0.4.

6. **SF-0.6 — `CurroNavHost` shell.** One `Scaffold` whose `innerPadding` wraps
   the `NavHost`; routes = `Launcher` (start) + `ConfigMenu` (placeholder); child
   screens never add their own `Scaffold`/`TopAppBar`/`statusBarsPadding()`
   (No-Double-Padding — `CLAUDE.md`). The launcher screen renders a placeholder
   ("Curro listo — Phase 0"); the config menu route is a stub. `MainActivity` is
   the launcher Activity with `singleTask`, `enableEdgeToEdge()`,
   `screenOrientation="portrait"`, **but the `CATEGORY_HOME` intent filter is not
   added yet** — that ships with Phase 1, deliberately, to avoid hijacking the
   home screen on the dev device before the real launcher home exists.
   **Acceptance bar:** the app installs, opens the placeholder, navigates to the
   stub config menu and back. `[UI: nav shell]`. Agents/skills:
   `android-developer`, `navigation-patterns`. **Size: S.** Depends on: SF-0.4.

7. **SF-0.7 — Brand-design fill-in.** Replace `brand-design`'s `TODO` palette /
   type scale / spacing / radii with the real Curro brand, satisfying the
   senior-first constraints (≥ 7:1 body where palette allows; light AND dark
   verified; body type role headline-sized). Wire the real values into
   `CurroTheme`. Also lock the **canonical `COPY.*` line table** (extending the
   one in `brand-design` already) with every Spanish string the FSM speaks today.
   This SF is the *content fill-in* of the brand skill — it deliberately does not
   touch components, just the tokens and the copy. Implements spec §2 (voice) +
   §11 (visual). **Acceptance bar:** every previously-`TODO` value in
   `brand-design` is resolved; contrast verified for every brand pairing in light
   + dark; the `COPY.*` table covers every line in spec §6. `[UI: brand] [copy:
   canonical lines]`. Agents/skills: `android-ui-designer`, `brand-design`,
   `voice-pipeline-engineer` (for the copy). **Size: M.** Depends on: SF-0.4.

8. **SF-0.8 — Telemetry plumbing (Firebase + PostHog), opt-in & isolated.** Wire
   Crashlytics + Analytics + PostHog with a build-flag `telemetryEnabled` (off in
   debug, on in release), `INTERNET` permission gated to the release manifest
   only, and a hard guardrail (a `TelemetrySink` interface) that **forbids** event
   properties from containing transcripts, contact names, phone numbers, or
   message bodies (unit-tested with a fixture that fails CI if violated).
   Implements the project's deliberate relaxation of spec §12 (→ to be reflected
   in v1.1). **Acceptance bar:** a crash in release is visible in Crashlytics; an
   event in PostHog has only safe properties; the debug APK has no `INTERNET`
   permission. `[permissions: INTERNET in release only] [copy: —]`. Agents/skills:
   `android-developer`, `kotlin-reviewer`. **Size: M.** Depends on: SF-0.2.

**Risks.** (a) Time spent on Phase 0 has no user-visible payoff — keep it lean,
don't over-engineer the package skeleton. (b) `brand-design` is *currently* a
template — SF-0.7 must finish before Phase 1 ships UI that goes on the device, or
visual regressions are guaranteed when the real palette lands.

---

## Phase 1 — Launcher base _(spec §11, §14 step 1)_

**Goal.** Curro becomes the home screen: clock + mic button + favourite-app grid +
"Más apps". **No assistant yet.** Validation goal: does Fran's father accept it as
a replacement for the stock HyperOS launcher? If "no" here, the rest of the
project is moot. (Spec §14 "Lo primero que validar".)

**Sub-features.**

1. **SF-1.1 — `CATEGORY_HOME` & "make me the default".** Add the HOME intent
   filter to `MainActivity` (with `DEFAULT` + `LAUNCHER`, `singleTask`, portrait,
   `clearTaskOnLaunch`, `stateNotNeeded` — `launcher-app`), use `RoleManager`
   (`ROLE_HOME`) to offer becoming default on first run, plus a fallback "Hazme tu
   pantalla de inicio" big button on the home screen when Curro isn't default.
   Detect HyperOS "forgetting" the default after updates (diagnostics will read
   this in Phase 8). **Acceptance bar:** on the real Redmi 15, the user can set
   Curro as the default launcher in one flow; pressing HOME returns to Curro,
   not the stock launcher. `[permissions: —, launcher needs none] [integrations:
   RoleManager] [UI: home + role-request prompt] [copy: "Hazme tu pantalla de
   inicio"]`. Agents/skills: `android-developer`, `launcher-app`. **Size: M.**
   Depends on: SF-0.6.

2. **SF-1.2 — Launcher home: clock + date.** Big-clock + date composable
   (`ClockBlock`, `displayLarge` for the time, `headlineLarge` for the date),
   localised to Spanish (`Miércoles 13 mayo`), updates on minute tick. Stable
   layout (always in the same spot). Implements spec §11. **Acceptance bar:** the
   clock updates correctly, survives `fontScale = 2.0f`, reads `> 7:1` contrast on
   both themes. `[UI: clock] [copy: Spanish date]`. Agents/skills:
   `android-developer`, `launcher-ui`. **Size: S.** Depends on: SF-0.5, SF-0.7.

3. **SF-1.3 — Main mic button (inert).** The `MicButton` composable: ≥ 40 % of
   the screen, big mic icon + large "CURRO" label, haptic on press, colour-change
   on touch. **Inert in Phase 1** (no STT yet) — pressing it just gives haptic +
   colour feedback and a Spanish toast "Aún no escucho — espera a la siguiente
   versión" (this is a Fran-facing dev string; it will be replaced when SF-2.1
   lands). Implements spec §4.1, §11. **Acceptance bar:** the button is ≥ 40 %
   screen on the Redmi 15 in portrait; haptic fires; no STT yet.
   `[UI: mic button]`. Agents/skills: `android-developer`, `launcher-ui`. **Size:
   S.** Depends on: SF-0.5.

4. **SF-1.4 — Static favourite-apps grid.** The 4–6 large app tiles
   (`AppTileGrid`, `AppTile`, `BigCard`-derived): icon + Spanish label, ≥ 96 dp
   each, generous spacing. **Static / Fran-set list for now** — the auto-learning
   from usage arrives in Phase 7. Tile tap → `PackageManager.getLaunchIntentForPackage`
   → opens the app (`platform-integrations`). `QUERY_ALL_PACKAGES` declared in
   the manifest. **Acceptance bar:** the 4 default tiles (WhatsApp, Llamadas,
   Cámara, Fotos) open the right apps when tapped; "feels the same every day"
   (the grid never reshuffles). `[permissions: QUERY_ALL_PACKAGES]
   [integrations: PackageManager] [UI: app grid] [copy: tile labels]`.
   Agents/skills: `android-developer`, `platform-integrations`. **Size: M.**
   Depends on: SF-1.2, SF-0.5.

5. **SF-1.5 — "Más apps" screen.** Big-row scrollable list (`LazyColumn` of
   `BigListRow`) of all launchable apps, alphabetical, with the user's favourites
   pinned at top. Implements spec §11. **Acceptance bar:** the full app list
   loads in < 1 s on the Redmi 15; rows ≥ 96 dp; back chevron at TopStart returns
   to home. `[UI: list]`. Agents/skills: `android-developer`, `launcher-ui`,
   `compose-patterns`. **Size: M.** Depends on: SF-1.4.

6. **SF-1.6 — Clock five-tap gesture.** Tapping the clock five times within 3 s
   opens the (still-empty) `ConfigMenu` route. A single tap does nothing. This is
   Fran's back door (spec §9); nothing visible advertises it. **Acceptance bar:**
   five taps within 3 s open the config-menu stub; a single tap does nothing;
   four taps in 5 s do nothing. `[UI: gesture]`. Agents/skills: `android-developer`,
   `launcher-ui`. **Size: S.** Depends on: SF-1.2, SF-0.6.

**Risks.** (a) The user might refuse the launcher entirely (the spec's first
validation gate — if this fails, stop and replan). (b) HyperOS forgetting the
default launcher after updates — surfaced in Phase 8 diagnostics, but the issue
exists in Phase 1; document the manual fix. (c) Phase 1 with the inert mic
button risks an unhappy first impression — be ready to fast-track Phase 2.

---

## Phase 2 — Voice pipeline _(spec §4.2, §4.6, §14 step 2)_

**Goal.** Press the mic button → record → transcribe (offline Spanish) → display
the transcript on screen → speak something back via TTS. **No decision model yet.**
Validation: does the loop feel responsive on the real Redmi 15, and is the TTS
voice intelligible enough? (Spec §14 "validar voz".)

**Sub-features.**

1. **SF-2.1 — `SttClient` (offline Spanish `SpeechRecognizer`).** Wrap Android's
   `SpeechRecognizer` behind a `domain/repository/SttClient` interface (so tests
   fake it). Offline Spanish only (`minSdk 31` permits this — confirm the voice
   pack is installed and surface a clear "Falta el paquete de voz español, dile a
   Fran" if not). Stream partial results as a `Flow<String>`. Map errors
   (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, others) to `CurroError.Stt*`.
   `RECORD_AUDIO` permission requested **on the first mic press**, not at install
   (spec §10). Implements spec §4.2. **Acceptance bar:** on the real Redmi 15
   with Spanish voice pack installed, "Hola Curro" is transcribed without
   network. `[permissions: RECORD_AUDIO on first press] [integrations:
   SpeechRecognizer] [model: —]`. Agents/skills: `voice-pipeline-engineer`,
   `platform-integrations`. **Size: M.** Depends on: SF-0.2.

2. **SF-2.2 — `TtsClient` (Spanish TTS, slowed default).** Wrap `TextToSpeech`
   behind `domain/repository/TtsClient`. Spanish voice (system default male
   selected, per spec §14 closed decision); default rate ~10–15 % slower
   (configurable later from the config menu); pitch configurable; `setLanguage`
   to `es_ES`; barge-in support (a button press cancels playback — preparing for
   the FSM's interrupt rule in Phase 5). Implements spec §4.6. **Acceptance bar:**
   speaks "Hola, soy Curro" in Spanish at the slowed rate; a button press while
   speaking cuts it off cleanly. `[integrations: TextToSpeech] [copy:
   COPY.tts_smoke_test]`. Agents/skills: `voice-pipeline-engineer`. **Size: M.**
   Depends on: SF-0.2.

3. **SF-2.3 — End-to-end loop (no decision yet).** Wire the mic button →
   `SttClient.listen()` → live transcript shown on screen (provisional
   `ListeningOverlay` — minimal version of the Phase-5 overlay: light-blue tint,
   "Te escucho…", live transcript in large text) → on final → echo the transcript
   via `TtsClient.speak(...)` and show it. **No FunctionGemma yet.** This is the
   pipeline smoke-test on real hardware. Implements spec §4.1 + §4.2 + §4.6.
   **Acceptance bar:** press → speak "qué hora es" → see the transcript → hear
   Curro repeat it. Latency < 1 s for press-to-listening on the Redmi 15.
   `[FSM: minimal — provisional listening + echo, full FSM in Phase 5] [UI:
   listening overlay v0]`. Agents/skills: `voice-pipeline-engineer`,
   `android-developer`. **Size: M.** Depends on: SF-2.1, SF-2.2, SF-1.3.

4. **SF-2.4 — Listening overlay (visual).** The proper `ListeningOverlay`
   composable (`launcher-ui` surface 2): screen tints light blue, "Te escucho…"
   in `displayMedium`, the live transcription below in `bodyLarge`
   (headline-sized), the mic button colour-changes and shows an audio-wave or
   level indicator. **No fussy animation** (spec §11). Driven by the same
   provisional state from SF-2.3 — the full state-driven overlay system arrives
   in Phase 5. **Acceptance bar:** the overlay renders correctly while STT is
   active; reads `≥ 7:1` contrast on the listening tint. `[UI: listening
   overlay] [copy: COPY.listening_prompt]`. Agents/skills: `android-ui-designer`,
   `launcher-ui`. **Size: S.** Depends on: SF-2.3.

**Risks.** (a) **TTS voice quality** — the system default Spanish male voice may
be robotic enough to fail the user-validation gate (spec §14 risk). Plan B is
ElevenLabs, but that breaks "on-device" — deliberate later call, deferred to
Phase 9 / the validation review. (b) Offline STT requires the voice pack — surface
its absence clearly; CI can't test this. (c) `RECORD_AUDIO` denial path must be
graceful from day one — fail with a plain Spanish "díselo a Fran".

---

## Phase 3 — FunctionGemma decision layer _(spec §4.3, §5 Fase 1, §14 step 3)_

**Goal.** Load FunctionGemma 270M (int8) via LiteRT + MediaPipe LLM Inference API,
keep it warm in a foreground service, and turn transcribed text into a validated
`{action, params, confidence}` JSON object — **without running any real handlers
yet** (each "handler" is a no-op that just shows the JSON on screen). Validation
goal: the model maps utterances to Fase-1 functions with > 90 % accuracy on a
hand-curated set, and warm latency is < 500 ms on the Redmi 15.

**Sub-features.**

1. **SF-3.1 — Model asset delivery decision & wiring.** Pick one of: download on
   first run, asset pack / Play Asset Delivery, or side-load for the prototype
   (`CLAUDE.md`). Implement it; document the choice in `CLAUDE.md` and the spec.
   The **debug build must build without weights** (guard on file-present —
   `on-device-llm`). Add a "modelos disponibles: sí/no" check used by the Phase-8
   diagnostics screen. **Acceptance bar:** the chosen delivery path gets
   FunctionGemma to a real Redmi 15; `assembleDebug` builds and runs on CI without
   weights; the app degrades gracefully when weights are absent ("Aún estoy
   preparando los modelos"). `[model: delivery] [permissions: —]`. Agents/skills:
   `ondevice-ai-engineer`, `android-developer`. **Size: M.** Depends on: SF-0.1.

2. **SF-3.2 — `FunctionCallEngine` + MediaPipe wrapper.** Implement the
   `domain/repository/FunctionCallEngine` interface and a `FunctionGemmaEngine`
   in `data/ml/` around `com.google.mediapipe.tasks.genai.llminference.LlmInference`
   — `warmUp()`, `isReady()`, `decide(utterance, ctx): Result<FunctionCall>` —
   with low temperature, `topK ≈ 1`, tight `maxTokens` (`on-device-llm`). Maps
   `OutOfMemoryError` → `CurroError.OutOfMemory`. **MediaPipe never imports
   outside `data/ml/`.** **Acceptance bar:** the engine loads, runs an
   utterance, returns a raw string in < 500 ms warm on the Redmi 15; tests use a
   fake engine — no MediaPipe imports in JVM tests. `[model: FunctionGemma]`.
   Agents/skills: `ondevice-ai-engineer`, `on-device-llm`. **Size: L.** Depends
   on: SF-3.1.

3. **SF-3.3 — `domain/catalog/` + prompt builder.** Define the Fase-1 catalog in
   `domain/catalog/` (mirroring the `function-catalog` skill — the canonical list
   of 7 functions, in the order spec §14 specifies). Build
   `FunctionCallPromptBuilder` that renders the prompt = current-phase catalog +
   minimal context (current time, unread-msg count+senders, known aliases — empty
   list in Phase 3). Golden-string tests for the rendered prompt. **Acceptance
   bar:** the prompt is short (every token competes on a 270M model — keep it
   tight); the unit tests pin the exact rendering. `[catalog: defined for Fase 1]
   [model: prompt]`. Agents/skills: `ondevice-ai-engineer`, `function-catalog`.
   **Size: M.** Depends on: SF-3.2.

4. **SF-3.4 — `FunctionCallValidator` (JSON Schema).** Implement the validator
   (`on-device-llm` §"Output validation"): trim → strip code fences → parse JSON
   → `action` is a string ∈ current-phase catalog → required params present →
   types match → confidence in [0, 1]. Each malformation maps to
   `CurroError.InvalidFunctionCall` or `CurroError.UnknownFunction(name)`. **No
   automatic retry on failure** — spec flow 7. **Acceptance bar:** the validator
   has exhaustive unit tests for every malformation (non-JSON, fenced JSON,
   missing/empty action, unknown action, missing param, mistyped param, extra
   param, confidence out of range / non-number). `[catalog: schema] [model:
   validation]`. Agents/skills: `ondevice-ai-engineer`, `function-catalog`,
   `testing-patterns`. **Size: M.** Depends on: SF-3.3.

5. **SF-3.5 — `ModelWarmupService` (foreground service).** A `@AndroidEntryPoint
   ModelWarmupService` that starts on `CurroApp.onCreate()` (or when the launcher
   first becomes visible), calls `engine.warmUp()`, posts a low-importance,
   no-sound ongoing notification (required scaffolding), `START_STICKY`.
   `POST_NOTIFICATIONS` requested on first start. Detect-and-recover when the
   service is killed (`isReady() == false` unexpectedly → reload + degrade with
   "Dame un segundo"). Implements spec §4.3, `launcher-app` HyperOS section.
   **Acceptance bar:** on the Redmi 15 with battery-whitelist on, the service
   stays alive across screen-off cycles; with whitelist off and HyperOS killing
   it, the detect-and-recover path works. `[permissions: POST_NOTIFICATIONS]
   [integrations: foreground service] [model: warm-keeping]`. Agents/skills:
   `ondevice-ai-engineer`, `launcher-app`. **Size: M.** Depends on: SF-3.2.

6. **SF-3.6 — Decision smoke loop (JSON on screen, no handlers).** Wire SF-2.3's
   STT output → `FunctionCallEngine.decide(...)` → `FunctionCallValidator` → on
   success, **display the parsed `FunctionCall` JSON on screen** (debug-only UI in
   the listening overlay) + TTS-echo the action name ("Reconocido: leer último
   mensaje"). On failure → spec flow 7 fallback: speak `COPY.error_unknown_function`
   ("Eso no lo sé hacer todavía…") + record into the failed-commands log (the log
   itself ships in Phase 7 — for now, log to `Log.w`). **No real handlers yet.**
   **Acceptance bar:** on the Redmi 15, "qué hora es" → `{action: tell_time, …}`
   visible on screen; "tradúceme esto" → the fallback line; warm latency < 500 ms
   recorded for at least 10 successive runs. `[catalog: Fase 1] [FSM: provisional
   processing] [model: end-to-end]`. Agents/skills: `voice-pipeline-engineer`,
   `ondevice-ai-engineer`. **Size: M.** Depends on: SF-3.2, SF-3.4, SF-2.3.

**Risks.** (a) **Latency** — 270M is small, but on a Snapdragon 6s Gen 3 the
< 500 ms target might be optimistic. Measure early; if blown, consider smaller
context, more aggressive `topK`, or shorter `maxTokens`. (b) **HyperOS killing
the warm-up service** — must be detected and surfaced in diagnostics. (c) **Model
delivery** — committing to a path (download / asset pack / side-load) shapes the
release story; decide before SF-3.2 ships.

---

## Phase 4 — Fase 1 handlers, one by one _(spec §5 Fase 1, §14 step 4 — in this exact order)_

**Goal.** Replace the no-op handlers from Phase 3 with real ones, in spec §14's
order: low-risk first (architecture validation, no sensitive permissions), then
the three permission-heavy ones. After this phase, the prototype answers every
Fase-1 utterance for real. (The state machine and the graded-confirmation policy
still arrive in Phases 5–6; until then, all `executing` is direct and all
`conditional`s execute without confirming — these handlers are written
*confirmation-aware*, but the policy that triggers confirmation lands later.)

**Sub-features.**

1. **SF-4.1 — `FunctionHandler` interface + Hilt multibinding.** Define the
   `domain/handler/FunctionHandler` interface (`suspend fun handle(call:
   FunctionCall): HandlerResult`), the `HandlerResult` sealed interface
   (`Spoken | NeedsConfirmation | Failed`), and the Hilt multibinding map (keyed
   by function name) that dispatches a validated `FunctionCall` to its handler.
   Update SF-3.6 to dispatch through this map. **Acceptance bar:** an unknown
   action returns `HandlerResult.Failed` cleanly; the dispatch is unit-tested
   with fake handlers. `[catalog: handler interface]`. Agents/skills:
   `android-architect`, `android-developer`. **Size: S.** Depends on: SF-3.6.

2. **SF-4.2 — `tell_time` handler.** The simplest handler: returns the current
   time/date/day in colloquial Spanish ("Son las doce y cuarenta y siete del
   miércoles trece de mayo"). Reads the `what` enum param (`time|date|day|all`,
   default `all`). No permissions, no system integrations beyond `Clock`.
   Implements spec §5. **Acceptance bar:** "qué hora es" → speaks the right
   time; "qué día es hoy" → speaks the right day. `[catalog: tell_time]
   [FSM: executing only] [permissions: —]`. Agents/skills: `android-developer`,
   `function-catalog`. **Size: S.** Depends on: SF-4.1.

3. **SF-4.3 — `open_app` handler + `InstalledAppsProvider`.** Enumerate launchable
   apps via `PackageManager.queryIntentActivities(MAIN+LAUNCHER)` (declared
   `QUERY_ALL_PACKAGES` already from SF-1.4). Build a small **colloquial alias
   map** (curated: "las fotos" → Galería/Fotos, "el correo" → user's email app,
   "la cámara" → Cámara, "WhatsApp" → com.whatsapp, "Teléfono", "Mensajes",
   "Ajustes"); fall back to fuzzy match (lowercase, strip accents,
   contains/Levenshtein) against installed labels. Multiple close matches →
   `CurroError.AmbiguousApp` → for now (no FSM confirmation yet) speak "Tengo
   varias apps que se llaman así, prueba con el nombre exacto"; in Phase 5+ this
   becomes a real ambiguity-confirm. No match → `CurroError.AppNotFound` → "No
   tengo ninguna app que se llame así." Implements spec §5 + `platform-integrations`.
   **Acceptance bar:** "abre WhatsApp", "abre la cámara", "ponme las fotos" all
   open the right app on the Redmi 15. `[catalog: open_app] [permissions:
   QUERY_ALL_PACKAGES — already declared] [integrations: PackageManager]`.
   Agents/skills: `android-developer`, `platform-integrations`. **Size: M.**
   Depends on: SF-4.1.

4. **SF-4.4 — `calculate` handler.** Parse a Spanish natural-language arithmetic
   expression ("cuarenta y siete por ocho", "el veintiuno por ciento de
   doscientos", "mil dividido entre veinticinco") → evaluate → speak the result
   in Spanish ("Cuarenta y siete por ocho son trescientos setenta y seis").
   Choice point: a hand-written Spanish-number parser vs. piping back through
   the LLM. **Recommend the hand-written parser** for Phase 1 — deterministic,
   testable, no extra latency, and the expression set is bounded (multiplication,
   division, addition, subtraction, percentage, fractions). Implements spec §5.
   **Acceptance bar:** the example utterances from spec §5 all evaluate correctly
   and are spoken in natural Spanish. `[catalog: calculate] [permissions: —]
   [model: no Gemma 3n — handler is rules-based]`. Agents/skills:
   `android-developer`. **Size: M.** Depends on: SF-4.1.

5. **SF-4.5 — `help` handler.** Speak a short Spanish list of what Curro can
   currently do, in Curro's voice ("Puedo leerte mensajes de WhatsApp, llamar a
   un contacto, abrir apps, hacer cuentas, o decirte la hora. ¿Qué te apetece?").
   Reads the optional `topic` param to focus the answer when given ("ayuda con
   las llamadas" → just the call lines). Phase-aware: the list reflects the
   current phase's catalog. Implements spec §5. **Acceptance bar:** "ayuda" /
   "qué puedes hacer" speaks the right list. `[catalog: help] [permissions: —]`.
   Agents/skills: `android-developer`, `brand-design` (for the copy).
   **Size: S.** Depends on: SF-4.1.

6. **SF-4.6 — Notification access infrastructure + `WhatsAppNotificationParser`.**
   Before the WhatsApp handlers can ship, set up the foundation:
   `CurroNotificationListenerService` with `BIND_NOTIFICATION_LISTENER_SERVICE`,
   a `NotificationRepository`/`UnreadMessageCache` interface, and the
   `WhatsAppNotificationParser` — defensive, prefers `MessagingStyle`, falls back
   to `extras` / summary notifications, handles 1:1 vs group, emoji-only, voice
   notes, images, and **unknown shapes** (records a "parse miss", never crashes,
   never invents content — spec risk in §14). Fran is asked to grant notification
   access from the home screen when this lands; deep link to
   `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. Fixture-driven tests are the
   highest-value test in the app (`platform-integrations` rule 2).
   **Acceptance bar:** the fixture suite covers MessagingStyle 1:1 & group,
   legacy extras, summary, emoji-only, voice note, image, malformed shape, and
   the parser produces correct output or a clean parse-miss for each; on the
   Redmi 15, an incoming WhatsApp lands in the cache. `[permissions:
   BIND_NOTIFICATION_LISTENER_SERVICE + notification access] [integrations:
   NotificationListenerService] [copy: COPY.whatsapp_parse_miss]`. Agents/skills:
   `android-developer`, `platform-integrations`. **Size: L.** Depends on: SF-4.1.

7. **SF-4.7 — `read_last_whatsapp` handler.** Reads the latest unread WhatsApp
   from the cache; optional `sender` param filters by sender (resolved against
   the parser's normalised sender strings — alias lookup happens later in Phase
   7). Empty cache → `COPY.no_unread` ("No tienes mensajes nuevos."). Implements
   spec §5. **Acceptance bar:** with an unread message in the cache, "léeme el
   último mensaje" reads it; with no unread, says so plainly. `[catalog:
   read_last_whatsapp] [integrations: notification cache]`. Agents/skills:
   `android-developer`, `platform-integrations`. **Size: S.** Depends on: SF-4.6.

8. **SF-4.8 — `read_all_unread_whatsapp` handler.** Reads all unread, **grouped
   by sender** (not by time — spec flow 5), in Curro's voice: "Tienes 3 mensajes
   de Pepito y 1 de Lucía. Empiezo con Pepito: …". For > 8 unread, speaks
   `COPY.many_unread` ("Tienes muchos mensajes. ¿Te los leo todos o solo los de
   alguien?") — this is the first nod toward Gemma 3n summarization (Phase 9 / 3+).
   For Phase 4, only the "leo todos" branch is wired; the "solo los de alguien"
   branch arrives later. Implements spec §5, flow 5. **Acceptance bar:** with a
   mixed-sender unread set, the right grouped Spanish is produced; the > 8 path
   triggers the offer. `[catalog: read_all_unread_whatsapp]`. Agents/skills:
   `android-developer`, `platform-integrations`. **Size: M.** Depends on: SF-4.6.

9. **SF-4.9 — `ContactsProvider` + alias lookup (no learning yet).** Resolve a
   spoken name to a contact via `ContactsContract` (`READ_CONTACTS`, requested on
   first `call_contact`). Returns one match, ambiguous matches, or no match.
   **Alias-learning is Phase 7**; for Phase 4, the alias *lookup* is wired (the
   repository interface is in place, returns empty for now). Aliases use
   `LOOKUP_KEY`, not raw contact id (`local-data`). **Acceptance bar:** "Llama a
   Carmen" with one Carmen in contacts resolves to her; with three Marías,
   returns `CurroError.AmbiguousContact(matches)`; with no match,
   `CurroError.ContactNotFound`. `[permissions: READ_CONTACTS on first call_contact]
   [integrations: ContactsContract]`. Agents/skills: `android-developer`,
   `platform-integrations`. **Size: M.** Depends on: SF-4.1.

10. **SF-4.10 — `call_contact` handler.** `CALL_PHONE` (requested on first use) +
    `ContactsProvider` from SF-4.9 + `CallController` (`ACTION_CALL`, not
    `ACTION_DIAL`). Single-match → place the call (Phase 5/6 will add the
    confidence-policy gate; for now in Phase 4 the call goes direct, so
    `call_contact` is **deliberately scoped to single, unambiguous matches** —
    ambiguity returns `HandlerResult.Failed` with "Tienes varios contactos así;
    espera, todavía no sé elegir entre ellos" until Phase 5 wires the picker).
    On revoked permission → "Necesito permiso para llamar; dile a Fran que lo
    active" (`COPY.call_permission_missing`). Implements spec §5, flow 1.
    **Acceptance bar:** "llama a Pepito" (single contact) places the call on the
    Redmi 15; ambiguous and not-found paths return clean Spanish failures.
    `[catalog: call_contact] [permissions: READ_CONTACTS + CALL_PHONE]
    [integrations: TelecomManager via ACTION_CALL]`. Agents/skills:
    `android-developer`, `platform-integrations`. **Size: M.** Depends on:
    SF-4.9.

**Risks.** (a) The WhatsApp parser is the single highest-risk piece in the
prototype — invest in the fixture suite. (b) Doing Phase 4 *before* Phase 5
means `call_contact` ships without confirmation in Phase 4 — keep its scope to
single-match for now, document this deviation and remove the guard once Phase 6
lands. (c) Calculator: rolling your own Spanish-number parser has a long tail of
edge cases — bound it tightly to the spec examples and document what's out of
scope.

---

## Phase 5 — State machine & interruption _(spec §6, §14 step 5)_

**Goal.** Replace the provisional FSM-ish glue from Phases 2–4 with the real,
single-owner state machine — `idle / listening / processing / confirming /
executing / error_recovery` — with **interruption-by-button from any state** and
the consecutive-failure recovery messages. After this phase, Curro feels
*coherent* across the pipeline.

**Sub-features.**

1. **SF-5.1 — `AssistantStateMachine` + `AssistantState` sealed interface.**
   `sealed interface AssistantState` with the six states, each carrying its
   needed data (`Listening(partial: String)`, `Confirming(prompt, action)`,
   `Executing(speech, screen)`, `ErrorRecovery(message)`). Single owner of
   transitions (`AssistantStateMachine`); exposes `StateFlow<AssistantState>`. No
   transition outside this class. Implements spec §6 diagram. **Acceptance bar:**
   unit tests exhaustively cover every transition in the diagram; invalid
   transitions throw. `[FSM: full]`. Agents/skills: `voice-pipeline-engineer`,
   `android-architect`. **Size: M.** Depends on: SF-4.1.

2. **SF-5.2 — `AssistantCoordinator` (rewire the pipeline through the FSM).**
   Replace the ad-hoc plumbing in SF-3.6 / SF-2.3 with an `AssistantCoordinator`
   that drives the FSM: button → `listening` (STT) → `processing` (FunctionGemma
   + validator) → optional `confirming` (Phase 6) → `executing` (dispatch to
   handler) → TTS → `idle`. Coordinator + FSM live in `assistant/`. **Acceptance
   bar:** every Phase-4 handler still works end-to-end on the Redmi 15, now
   through the proper state flow; the state changes are observable via the
   `StateFlow` (verified in a UI test). `[FSM: wired]`. Agents/skills:
   `voice-pipeline-engineer`. **Size: M.** Depends on: SF-5.1.

3. **SF-5.3 — Interrupt-by-button (the rule that breaks if missed).** A button
   press in **any state** cancels in-flight work (STT session, model inference,
   TTS playback, pending confirmation) and goes directly to `listening`. This
   must be hard-coded into the coordinator, not bolted on later (`voice-interaction`
   rule 1). Implements spec §6 critical note. **Acceptance bar:** while Curro is
   reading a long message, pressing the button stops the TTS within ~150 ms and
   re-enters `listening`; same from `processing`, `confirming`, `executing`,
   `error_recovery`. `[FSM: interrupt rule]`. Agents/skills:
   `voice-pipeline-engineer`. **Size: M.** Depends on: SF-5.2.

4. **SF-5.4 — Consecutive-STT-failure policy.** 1st fail → `COPY.stt_fail_1`
   ("No te he oído bien…"). 2nd → `COPY.stt_fail_2` ("Sigo sin entenderte…").
   3rd → `COPY.stt_fail_3` ("Vamos a dejarlo…") + `idle`, counter reset. Counter
   resets on **any successful turn**. Implements spec §6 flow 6. **Acceptance
   bar:** three successive STT failures produce exactly those three messages
   then stop; one success in between resets the counter. `[FSM: error_recovery]
   [copy: COPY.stt_fail_1/2/3]`. Agents/skills: `voice-pipeline-engineer`.
   **Size: S.** Depends on: SF-5.2.

5. **SF-5.5 — State-driven overlay routing.** Move the listening/processing/
   executing UI from Phase 2's ad-hoc rendering to *state-driven overlays* on top
   of the launcher home, keyed off `AssistantState` (`launcher-ui` rule 3): the
   `LauncherScreen` observes `StateFlow<AssistantState>` and renders the right
   overlay (`ListeningOverlay` / `ProcessingOverlay` / `ConfirmationOverlay` /
   `MessageCardsScreen` / `ContactPickerScreen`) without using `NavHost`. Build
   `ProcessingOverlay` ("Un momento…" with a non-animated indicator, spec §11) —
   the other overlays already exist provisionally and just get pointed at the
   new state. **Acceptance bar:** UI tests verify each state renders the right
   overlay; switching states cleanly transitions; no nav-route churn.
   `[FSM: UI mapping] [UI: state-driven overlays]`. Agents/skills:
   `android-developer`, `launcher-ui`. **Size: M.** Depends on: SF-5.2.

6. **SF-5.6 — HOME-press / `onNewIntent` resets to `idle`.** When the user
   returns to the launcher (HOME button from another app → `onNewIntent`), the
   FSM resets to `idle` and any pending overlay is cleared (`launcher-app` rule
   3). **Acceptance bar:** opening another app mid-listening, then pressing HOME,
   returns to a clean launcher idle state. `[FSM: lifecycle]`. Agents/skills:
   `android-developer`, `launcher-app`. **Size: S.** Depends on: SF-5.2.

**Risks.** (a) The interrupt rule is the easiest thing to forget when refactoring
later — add a test for every state's "press-while-in-X" path. (b) HyperOS may
freeze the app in unusual ways when returning HOME; verify on real hardware.

---

## Phase 6 — Confidence-graded confirmation _(spec §4.3, §14 step 6)_

**Goal.** Wire the `needs_confirmation` semantics — `false` / `true` /
`conditional` — plus the confidence-graded thresholds (default 0.85 / 0.60) and
the always-escalate cases. After this phase, `call_contact` behaves correctly
under ambiguity and low confidence (spec flows 1, 2, 3).

**Sub-features.**

1. **SF-6.1 — `ConfidencePolicy` + thresholds in DataStore.** `ConfidencePolicy`
   in `assistant/`: takes a `CatalogFunction` + `FunctionCall.confidence` +
   contextual flags (`isAmbiguous`, `alwaysConfirmToggle`) → returns
   `Execute | Confirm | Clarify`. Thresholds (`CONF_EXECUTE_MIN = 0.85`,
   `CONF_CONFIRM_MIN = 0.60`) come from `SettingsRepository` (DataStore — wired
   here for the first time, default values shipped). Implements spec §4.3.
   **Acceptance bar:** exhaustive unit tests for ≥0.85 / 0.60–0.85 / <0.60 across
   the three `needs_confirmation` values + each always-escalate flag. `[FSM:
   policy] [persistence: DataStore for thresholds]`. Agents/skills:
   `voice-pipeline-engineer`, `function-catalog`. **Size: M.** Depends on:
   SF-5.2.

2. **SF-6.2 — `ConfirmationOverlay` (SÍ / NO, 10-s silence).** The proper
   `confirming`-state overlay: the resolved target in big text + two huge
   buttons `BigYesNoRow` (≥ 96 dp each, well separated, high contrast, icon +
   text). Accepts "sí"/"no" via STT and the taps. 10-s silence → `COPY.confirm_timeout`
   ("Cancelo entonces.") → `idle`. Implements spec §6 flow 2 + `voice-interaction`
   "confirming behaviour". **Acceptance bar:** UI test verifies the buttons are
   ≥ 96 dp and fire the right events; integration test verifies the 10-s timeout
   and the "no" → "Vale, no llamo" path. `[FSM: confirming] [UI: confirmation
   overlay] [copy: COPY.confirm_call, COPY.confirm_no, COPY.confirm_timeout]`.
   Agents/skills: `android-developer`, `launcher-ui`. **Size: M.** Depends on:
   SF-6.1, SF-5.5.

3. **SF-6.3 — Disambiguation flow (3-Marías) + `ContactPickerScreen`.** When
   `CallContactHandler` returns `CurroError.AmbiguousContact(matches)`, the
   coordinator transitions to `confirming` with a `ContactPickerScreen` (big-row
   list of candidates with photo + full name + a "Ninguna" row, reads up to 3 by
   voice). Voice/tap of a candidate proceeds; a non-matching answer → repeat
   options **once** → second miss → `COPY.disambig_give_up` ("Mejor llámala desde
   la agenda, no me aclaro."). **Don't learn an alias mid-disambiguation** (spec
   flow 3 → flow 4 note; `local-data` rule 3). Now `call_contact` works for the
   multi-match case for real (removes the Phase-4 single-match guard).
   Implements spec §6 flow 3. **Acceptance bar:** "llama a María" with three
   Marías shows three big buttons + "Ninguna"; selecting one calls; the
   second-miss give-up message lands. `[FSM: confirming (disambig)] [UI: contact
   picker] [copy: COPY.disambig_ask, COPY.disambig_give_up]`. Agents/skills:
   `voice-pipeline-engineer`, `android-developer`, `platform-integrations`,
   `launcher-ui`. **Size: M.** Depends on: SF-6.2.

4. **SF-6.4 — "Always confirm" toggle integrated.** Plumb `SettingsRepository.alwaysConfirm`
   into `ConfidencePolicy` so that when it's on, every `conditional` function
   escalates to confirmation regardless of confidence (`function-catalog` rule
   3). The toggle's UI lands in Phase 8; here it's just wired into the policy
   with a default-off DataStore key. **Acceptance bar:** with the toggle on, a
   0.95-confidence `call_contact` still goes to `confirming`. `[FSM: policy
   override] [persistence: DataStore]`. Agents/skills:
   `voice-pipeline-engineer`. **Size: S.** Depends on: SF-6.1.

**Risks.** (a) The thresholds are guesses until real-world data exists — the
sliders in Phase 8 are essential, and so is the failed-commands log (Phase 7)
that lets Fran see when confidence was too low / too high. (b) Disambiguation
listening is a smaller STT pass; make sure the recognizer reliably picks names
out of a constrained vocabulary.

---

## Phase 7 — Alias learning & local persistence _(spec §7, flow 4, §14 step 7)_

**Goal.** Curro starts remembering: contact aliases learned on first use ("mi
hija" → Lucía Ruiz), implicit favourite-app usage that promotes apps to the home
grid, the failed-commands log Fran will review. Implements spec §7 + flow 4 +
`local-data`.

**Sub-features.**

1. **SF-7.1 — Room database + DAOs + migrations.** Set up `CurroDatabase`,
   `ContactAliasDao`, `AppUsageDao`, `InteractionLogDao`, `FailedCommandDao` per
   the schema in `local-data`. Aliases reference `LOOKUP_KEY`, not raw ids.
   In-memory Room tests cover each DAO. Hilt `DatabaseModule`. **Acceptance bar:**
   DAO tests pass; `failed_commands` capped at 50 (oldest trimmed on insert);
   uniqueness enforced on alias. `[persistence: Room]`. Agents/skills:
   `android-developer`, `local-data`. **Size: M.** Depends on: SF-0.2.

2. **SF-7.2 — `AliasRepository` + alias lookup in prompt context.** Replace the
   empty alias lookup from SF-4.9 with the real `AliasRepository` backed by
   `ContactAliasDao`. The FunctionGemma prompt-builder (`function-catalog` →
   "Prompt context") now includes the known aliases. Existing handlers that
   resolve contacts (`call_contact`) consult the alias map first
   (`platform-integrations` resolution order 1). **Acceptance bar:** with a
   pre-loaded alias "mi hija → Lucía Ruiz", "llama a mi hija" resolves to Lucía
   directly without prompting. `[catalog: prompt now includes aliases]
   [persistence: alias lookup]`. Agents/skills: `android-developer`,
   `platform-integrations`. **Size: M.** Depends on: SF-7.1.

3. **SF-7.3 — Alias-learning subflow (spec flow 4).** When a `call_contact`
   spoken `contact` looks like a relational term ("mi hija", "el médico", "mi
   nieta") **and** isn't in the alias map, enter learning mode: a
   `ContactPickerScreen` (re-using the SF-6.3 component) listing up to 5
   contacts; user picks one → persist `ContactAliasEntity(alias, lookupKey,
   source=LEARNED)` → speak `COPY.alias_saved` → proceed with the call. **One
   alias per interaction, never mid-disambiguation.** "Ninguno" →
   `COPY.alias_defer_to_fran` ("Vale, no pasa nada. Dile a Fran que apunte quién
   es tu hija."). Implements spec §7 + flow 4 + `local-data` rule 3.
   **Acceptance bar:** the first "llama a mi hija" triggers learning; future
   "mi hija" resolves directly; "Ninguno" pushes it to Fran. `[FSM: confirming
   (learning)] [persistence: ContactAliasEntity] [copy: COPY.alias_ask,
   COPY.alias_saved, COPY.alias_defer_to_fran]`. Agents/skills:
   `voice-pipeline-engineer`, `android-developer`, `local-data`. **Size: M.**
   Depends on: SF-7.2, SF-6.3.

4. **SF-7.4 — Implicit favourite apps → home grid.** Each `open_app` (handler
   *and* tile tap) bumps `AppUsageDao.upsert(packageName, lastOpenedAt=now)`.
   The favourites grid in Phase 1 swaps from a static list to a stable
   recency-weighted top-N from `AppUsageDao`, **recomputed occasionally** (e.g.
   once a day or on an explicit "actualizar favoritas"; not on every open —
   `local-data` rule 5). Fran can still override from the config menu (Phase 8).
   Implements spec §7. **Acceptance bar:** opening WhatsApp 20 times moves it
   into the top tiles; the grid doesn't reshuffle on each open. `[persistence:
   AppUsageEntity] [UI: dynamic but stable grid]`. Agents/skills:
   `android-developer`, `local-data`. **Size: M.** Depends on: SF-7.1, SF-1.4.

5. **SF-7.5 — `FailedCommandLog` (real, capped at 50).** Replace the `Log.w`
   stub from SF-3.6 with the real `FailedCommandLog` backed by Room. Distinguish
   `INVALID_OUTPUT` (the model didn't produce valid JSON) vs. `UNKNOWN_FUNCTION`
   (valid JSON, function not in this phase) vs. `HANDLER_ERROR` (handler failed
   at runtime). Capped at 50 (oldest trimmed). Visible from the config menu in
   Phase 8. **Acceptance bar:** each failure kind lands in the log with the
   right tag; the log is capped at 50. `[persistence: FailedCommandEntity]`.
   Agents/skills: `android-developer`, `local-data`. **Size: S.** Depends on:
   SF-7.1, SF-3.6.

**Risks.** (a) The alias-learning subflow is the most subtle interaction in the
spec — *one alias per interaction*, *not mid-disambiguation*, *defer to Fran on
"ninguno"*. Test each rule. (b) Favourite-app reshuffle stability is critical for
"feels the same every day" — over-recompute and the user loses his bearings.
(c) `LOOKUP_KEY` (not raw contact id) is the only safe reference — re-resolve at
call time; if it no longer resolves, tell the user plainly and offer to relearn.

---

## Phase 8 — Settings menu (Fran-only) _(spec §9, §14 step 8)_

**Goal.** The hidden config menu — opened by the 5-tap clock gesture from
Phase 1 — finally has its real contents: aliases, favourites, TTS settings,
incoming-call toggle (off by default), confidence-threshold sliders, "always
confirm" toggle, failed-commands log, "send failures" toggle, reset learning,
diagnostics. Implements spec §9.

**Sub-features.**

1. **SF-8.1 — `ConfigMenuScreen` scaffold.** The real config menu, opened from
   SF-1.6's gesture: a normal scrollable `LazyColumn` (denser than the
   senior-first UI; this screen is for Fran), big back chevron at `TopStart` (no
   `TopAppBar` — No-Double-Padding), sectioned. `ConfigViewModel` exposes the
   real `Settings` flow from `SettingsRepository`. **Acceptance bar:** the menu
   opens from the gesture, shows the sections as stubs (each section becomes a
   later SF), back chevron works. `[UI: config menu shell] [permissions: —]`.
   Agents/skills: `android-developer`, `launcher-ui`. **Size: S.** Depends on:
   SF-1.6, SF-7.1.

2. **SF-8.2 — Alias management UI.** List of `ContactAliasEntity`s (alias +
   display-name-at-learn-time + source), plus add ("añadir alias" → pick a
   contact → type a relational term), edit, delete. Adding is how Fran pre-loads
   aliases that the user hasn't said yet ("mi nieta", "mi hermano"). Implements
   spec §9 "Alias de contactos". **Acceptance bar:** Fran can add an alias from
   scratch; the alias lookup in the prompt context picks it up immediately.
   `[persistence: ContactAliasDao] [UI: alias list]`. Agents/skills:
   `android-developer`, `local-data`. **Size: M.** Depends on: SF-8.1, SF-7.2.

3. **SF-8.3 — Launcher favourites override UI.** Show the current top-N + a
   "edit" button that lets Fran pick the exact 4–6 apps to pin (override the
   auto-by-use). Stored in DataStore (`launcher_favourites_override`).
   Implements spec §9. **Acceptance bar:** Fran sets an explicit list; the home
   grid uses it; clearing the override reverts to auto-by-use. `[persistence:
   DataStore override list] [UI: favourites editor]`. Agents/skills:
   `android-developer`, `launcher-ui`. **Size: M.** Depends on: SF-8.1, SF-7.4.

4. **SF-8.4 — TTS settings (voice, rate, pitch).** Voice picker (installed
   Spanish voices from `TextToSpeech.getVoices()`), rate slider (default
   ~0.85–0.90 — `local-data`), pitch slider. Live-preview on change ("¿Me oyes
   bien?"). Persisted in DataStore; `TtsClient` reads from `SettingsRepository`.
   Implements spec §9. **Acceptance bar:** changing the rate is audible
   immediately; persists across restarts. `[persistence: DataStore TTS keys]
   [UI: voice/rate/pitch] [copy: TTS preview line]`. Agents/skills:
   `voice-pipeline-engineer`, `android-developer`. **Size: M.** Depends on:
   SF-8.1, SF-2.2.

5. **SF-8.5 — Confidence threshold sliders + "always confirm" toggle UI.** Two
   sliders for `CONF_EXECUTE_MIN` (default 0.85) and `CONF_CONFIRM_MIN` (default
   0.60) with the constraint `CONFIRM_MIN < EXECUTE_MIN`. A toggle for "Confirma
   siempre" (already wired into the policy in SF-6.4). Show a small "qué quiere
   decir esto" help line. Implements spec §9 + §4.3. **Acceptance bar:** moving
   the sliders changes the policy live; the toggle forces confirmation on a
   high-confidence call. `[UI: sliders + toggle] [persistence: DataStore]`.
   Agents/skills: `android-developer`, `voice-pipeline-engineer`. **Size: S.**
   Depends on: SF-8.1, SF-6.1.

6. **SF-8.6 — Failed-commands log viewer.** Show the last 50
   `FailedCommandEntity`s (timestamp, transcript, kind, sent?). Filter by kind.
   "Borrar log" button. **Surface the kind clearly** so Fran can distinguish "I
   didn't understand" from "that feature isn't built" (`local-data` rule 4).
   Implements spec §9. **Acceptance bar:** the log shows failures from real use
   on the Redmi 15, kinds are colour-coded + labelled, "borrar" clears it.
   `[persistence: FailedCommandDao] [UI: log list]`. Agents/skills:
   `android-developer`, `local-data`. **Size: M.** Depends on: SF-8.1, SF-7.5.

7. **SF-8.7 — "Modo asistente de llamadas" toggle (opt-in, off by default) +
   `CurroInCallService`.** A toggle in the config menu (off by default — spec
   §8). When on, request `READ_PHONE_STATE` + `ANSWER_PHONE_CALLS` and register
   `CurroInCallService` (`BIND_INCALL_SERVICE`); when off, the service is **not
   registered at all** so telephony is 100 % native (`platform-integrations`
   rule 4). The service announces incoming calls by voice ("Te está llamando
   Pepito" / "tu hija María" using aliases), accepts "sí"/"coge"/"responde" →
   `call.answer()` and "no"/"cuelga" → `call.disconnect()`; complements the
   manual tap, never replaces it. Implements spec §8. **Acceptance bar:** with
   the toggle off, incoming calls behave exactly like stock; with it on, an
   incoming call from a known contact is announced and answerable by voice.
   `[permissions: READ_PHONE_STATE + ANSWER_PHONE_CALLS — only on toggle]
   [integrations: InCallService] [FSM: announcement is outside the main FSM]
   [copy: "Te está llamando {nombre}"]`. Agents/skills: `voice-pipeline-engineer`,
   `platform-integrations`. **Size: L.** Depends on: SF-8.1, SF-7.2.

8. **SF-8.8 — "Send failures to Fran" toggle + anonymized export.** A toggle
   (off by default), and when on, a button "enviar fallos a Fran" that exports
   the unsent `FailedCommandEntity`s anonymized (strip/replace names; never
   message bodies; never audio) via a share intent (email / WhatsApp / etc.).
   Marks them `sent = true`. **This is the only thing in spec §12 that can leave
   the device, and only with this explicit consent**; product telemetry
   (Firebase/PostHog) never sees this content. Implements spec §9, §12 +
   `local-data`. **Acceptance bar:** with the toggle on, the export contains
   anonymized entries; with it off, the button is hidden. `[persistence:
   FailedCommandEntity.sent] [UI: toggle + export] [copy: anonymisation
   warnings]`. Agents/skills: `android-developer`, `local-data`,
   `kotlin-reviewer`. **Size: M.** Depends on: SF-8.1, SF-7.5.

9. **SF-8.9 — Reset learning.** A destructive "Reset de aprendizaje" button
   (clears `contact_aliases`, `app_usage`, `interaction_log`, `failed_commands`),
   with a clear "¿Seguro? Esto borra los alias y las favoritas aprendidas."
   confirmation. Implements spec §9 + `local-data`. **Acceptance bar:** after
   reset, every learning table is empty, aliases no longer resolve, the
   favourites grid reverts to defaults. `[persistence: clear tables] [UI:
   destructive confirm]`. Agents/skills: `android-developer`, `local-data`.
   **Size: S.** Depends on: SF-8.1, SF-7.1.

10. **SF-8.10 — Diagnostics screen.** "Versión y diagnóstico" section: app
    version, model state (loaded? warm? last latency in ms — surfaced from
    `FunctionCallEngine`), am-I-the-default-launcher (Y/N), granted permissions
    list, **deep link to HyperOS battery whitelist**
    (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) + a copy block with the
    autostart steps (`launcher-app` HyperOS section). Implements spec §9 +
    `launcher-app`. **Acceptance bar:** the screen shows accurate values on the
    Redmi 15; the deep link opens Curro's battery settings. `[UI: diagnostics]
    [integrations: Settings deep links]`. Agents/skills: `android-developer`,
    `launcher-app`. **Size: M.** Depends on: SF-8.1.

**Risks.** (a) The incoming-call mode is the most invasive system integration —
keep it strictly opt-in and absolutely sure that toggling it off leaves
telephony 100 % native. (b) Threshold sliders without grounding data are easy to
mis-tune — pair this with the failed-commands log so Fran has evidence.
(c) Reset-learning is destructive — confirm twice if you want; never run it
silently.

---

## Phase 9 — Gemma 3n content layer _(spec §4.4, §14 step 9 — evaluate first; may defer)_

**Goal.** Decide whether to load Gemma 3n E2B (int4, ~2 GB active) in the
prototype or defer to Fase 2. If yes: add the first genuine NL-generation path —
the "tienes muchos mensajes" summarization branch from `read_all_unread_whatsapp`
(SF-4.8). Spec §14 says explicitly *"In Phase 1 may not be strictly necessary;
evaluate."*

**Sub-features.**

1. **SF-9.1 — Hardware confirmation & decision: do we load Gemma 3n at all?**
   Confirm the Redmi 15 variant (4 GB or 8 GB RAM) on the user's actual unit —
   on 4 GB, Gemma 3n is marginal (spec §14 risk; `on-device-llm`). If 4 GB or
   latency is bad, **defer Phase 9 entirely to Fase 2** and ship the prototype
   without summaries. This SF is a deliberate go/no-go gate. **Acceptance bar:**
   a written decision in `CLAUDE.md` + the spec (v1.1) + a measured cold-load
   latency from a Gemma-3n smoke test on the actual device. `[model: go/no-go]`.
   Agents/skills: `ondevice-ai-engineer`. **Size: S.** Depends on: none — this
   should happen *before* SF-9.2.

2. **SF-9.2 — `TextGenEngine` + `Gemma3nEngine` (on-demand load, "Dame un
   segundo").** Implement the `domain/repository/TextGenEngine` interface and a
   `Gemma3nEngine` in `data/ml/` around MediaPipe `LlmInference` (different
   model, larger context, normal temperature). **Loaded on demand only** — never
   speculatively (`on-device-llm`). Cold → `CurroError.ModelCold` surfaced to
   coordinator → speak `COPY.cold_model` ("Dame un segundo.") → load → proceed.
   Free under memory pressure (`onTrimMemory` / `OutOfMemoryError`):
   `unload()` Gemma 3n, **keep FunctionGemma warm**. The app must keep working
   with FunctionGemma only. Implements spec §4.4. **Acceptance bar:** on the
   Redmi 15 (8 GB), first generation takes 3–6 s; subsequent generations are
   faster; memory pressure unloads cleanly. `[model: Gemma 3n] [copy:
   COPY.cold_model]`. Agents/skills: `ondevice-ai-engineer`. **Size: L.**
   Depends on: SF-9.1 (if "go"), SF-3.5.

3. **SF-9.3 — `summarize_whatsapp_thread` mini-flow (the > 8 unread branch).**
   When `read_all_unread_whatsapp` finds > 8 unread (already detected in SF-4.8),
   take the "te los leo todos o solo los de alguien" branch and use Gemma 3n to
   produce a one-sentence Spanish summary per sender, in Curro's voice
   (`on-device-llm` prompt template). This is a *handler-internal* use of Gemma
   3n — it does **not** add a new catalog function (the catalog still has
   `read_all_unread_whatsapp` only). The full `summarize_whatsapp_thread`
   catalog function lands in Fase 3. **Acceptance bar:** with 12 unread across
   three senders, the spoken response is a short, accurate Spanish summary per
   sender, generated in 3–6 s. `[model: Gemma 3n NL gen] [catalog: no change —
   internal to read_all_unread_whatsapp] [copy: generated, in Curro's voice]`.
   Agents/skills: `ondevice-ai-engineer`, `voice-pipeline-engineer`. **Size: M.**
   Depends on: SF-9.2, SF-4.8.

**Risks.** (a) **4 GB RAM** — if confirmed, the entire phase defers; build the
prototype without it. (b) **Cold-load latency** — 3–6 s is the target; if real
measurements blow it (10 s+) reconsider the architectural payoff. (c) The "Dame
un segundo" line must feel natural and not be repeated — once per cold load.

---

## Later — Fase 2+ _(spec §5, not for the prototype)_

These are tracked here only at the paragraph level — they become full SFs when
the prototype validates and a Fase-2 plan is written. Each will eventually map to
one or more `US-XXX` stories.

- **`send_whatsapp_reply` (Fase 2).** Voice reply to the last received message
  from a contact, using the notification's `RemoteInput` action (`platform-integrations`).
  `needs_confirmation: true` — always confirm recipient + dictated text before
  sending. Touches: the FSM `confirming` for a *content* confirmation (not just
  yes/no — playback of the dictated text); the existing notification
  infrastructure (SF-4.6); a Gemma-3n "rewrite for clarity" optional pass.
- **`set_volume` (Fase 2).** `AudioManager` adjust on ring + media streams
  together (the user's intent is "I want to hear better"); `needs_confirmation:
  false`. Small handler; spec already specifies params.
- **`read_sms`, `set_reminder`, `read_reminders`, `dictate_voice_note` (Fase
  2).** Each is a self-contained handler with its own permissions
  (`READ_SMS` opt-in for SMS; calendar provider for reminders; microphone for
  voice notes). Reminders are interesting because they need the proactive-alert
  scaffolding (Fase 4 precursor).
- **`summarize_whatsapp_thread` (full Fase 3 catalog function), `video_call_contact`,
  `read_news_headlines`, `translate_text`, `medication_reminder` (Fase 3).** Each
  needs Gemma 3n. `read_news_headlines` is the only catalog function that
  genuinely needs `INTERNET` — that's when the parked `api-integration` skill
  comes back. `medication_reminder` is the first Fase-3 function with strict
  confirmation semantics (the user must confirm they took the dose).
- **`describe_received_photo` (Fase 4).** Gemma 3n **multimodal** — describe a
  received WhatsApp photo by voice. Touches the NotificationListener parser
  ("image" hook already in `platform-integrations`).
- **`proactive_alerts` (Fase 4).** Notifications that *Curro* initiates ("mañana
  tienes médico"). Needs the `interaction_log` and an event scheduler. The
  proactive flow inverts the FSM (Curro talks first); design carefully.
- **`explain_current_screen` (Fase 4).** Read and explain the UI of any app the
  user is in, via `AccessibilityService`. **Most invasive integration in the
  whole product** — significant permission, significant UX risk; treat as a
  separate validation step.
- **`learn_routine` (Fase 4).** Pattern detection on `interaction_log` ("a esta
  hora sueles llamar a tu hija"). Tied to proactive alerts; tied to
  `usage_times` retention semantics — privacy review needed.

---

## Dependencies between phases

The critical path runs **vertically**: Phase 0 (foundation) blocks everything;
Phase 1 (launcher) blocks Phase 4's `open_app` tile path; Phase 2 (voice
pipeline) blocks Phase 3 (FunctionGemma plumbs into the STT output); Phase 3
blocks **every handler** in Phase 4 (no decision → no dispatch). Phase 4 ships
all seven Fase-1 handlers but two of them (`call_contact`, and the > 8-unread
branch of `read_all_unread_whatsapp`) only become *complete* after Phase 6
(confidence + disambiguation) and Phase 9 (Gemma 3n summary) respectively.

Phase 5 (the proper FSM) is technically blockable until late, but in practice it
should land **before** the more interactive Phase-4 handlers (`read_*_whatsapp`,
`call_contact`) ship — otherwise the interrupt-by-button rule becomes a
retrofit nightmare. A reasonable shipping order in real time is: 0 → 1 → 2 → 3 →
(low-risk Phase-4: tell_time, open_app, calculate, help) → 5 → (heavier Phase-4:
read_*_whatsapp, single-match call_contact) → 6 → 7 → 8 → 9 (if go). The plan
above documents this by deliberately scoping `call_contact` in SF-4.10 to
single-match only and unlocking the rest in Phase 6.

**Cross-cutting work** that isn't a single phase:

- **`brand-design` fill-in (SF-0.7)** — blocks any device-visible UI from Phase
  1 onward. Don't ship the placeholder Material palette.
- **Spec v1.1 revision** — needed before claiming "v1 done": fix §5 ("8/7
  funciones"), §12 (telemetry is on), §14 (record the model-delivery decision
  from SF-3.1 and the Phase-9 go/no-go from SF-9.1).
- **HyperOS battery whitelist + autostart documentation** — surfaces in SF-8.10
  (diagnostics) but the setup steps must be written down **before Phase 3
  ships** or the warm-up service won't survive Fran's first home visit.
- **Model-asset delivery decision** (SF-3.1) — must be made before any
  device-side model code is written.

---

## The first stories to start with (feed into `/create-prd`)

After this plan is reviewed and the open questions are resolved, the right first
stories are:

1. **US-001 = SF-0.1 — Gradle skeleton & version catalog.** Mandatory zeroth
   step; everything else is blocked.
2. **US-002 = SF-0.2 — Hilt + `CurroApp` + base packages.** Unblocks every
   feature that needs injection.
3. **US-003 = SF-0.3 — Lint, format & detekt.** Cheap; lands the quality bar
   before any feature code is written.
4. **US-004 = SF-0.4 — `CurroTheme` scaffold + senior-first tokens.** The first
   thing that has visible payoff and that sets the senior-first contract for
   every later piece of UI.
5. **US-005 = SF-0.7 — Brand-design fill-in.** Promoted out of pure-Phase-0
   sequence because **it blocks every UI-visible later SF**; doing it now means
   Phase 1 ships with the real palette and not the placeholder.

This is a deliberately tight five-story start: get the foundation green, lock
the brand + the senior contract, then enter Phase 1.

---

## Open questions to resolve before kicking off Phase 0

1. **Confirm the Redmi 15 RAM variant** on the actual unit. **Block** any work
   relying on Gemma 3n (Phase 9 / Fase 3) until this is answered. *(Spec §14
   "Decisiones explícitamente abiertas".)*
2. **Model-asset delivery (SF-3.1):** download on first run vs. asset pack /
   Play Asset Delivery vs. side-load (`adb push`). The simplest for a one-device
   prototype is side-load; long-term a real product needs Play Asset Delivery.
   Decide before SF-3.2.
3. **Spec v1.1 bump — when?** §12 (telemetry kept on), §5 ("8 funciones" vs.
   7), and §14 (record the answers to #1 and #2 above) all need a single
   coordinated revision. Recommend doing it at the end of Phase 0 so Phase 1
   starts from a clean spec.
4. **TTS voice acceptability (spec §14):** the system default Spanish male voice
   may not be intelligible enough for this user. We don't know until Phase 2
   ships on the device. If Plan B (ElevenLabs) is needed, **it breaks the
   "on-device" rule** — Fran should know this trade-off exists before we ship
   Phase 2 so we're ready to make the call quickly.
5. **`SYSTEM_ALERT_WINDOW` overlay over other apps (spec §10 *eval*):** decide
   whether the prototype needs assistant feedback *on top of* whatever app the
   user is in, or whether it's enough to live inside the launcher. Recommend
   **defer**; the launcher is enough for validation.
6. **Telemetry isolation strategy (SF-0.8):** separate process vs. build flag
   vs. both. The hard guardrail (no transcripts, names, message bodies ever)
   is non-negotiable; the *mechanism* is open.

---

## Numbering scheme + workflow ahead

**Proposal:** the 5-to-7 SFs in each Phase map to consecutive `US-XXX` numbers
in spec-§14 order — US-001 = SF-0.1, US-002 = SF-0.2, …, US-NNN = the last
Fase-1 handler. Concretely with the counts in this plan (Phase 0: 8 SFs, Phase
1: 6, Phase 2: 4, Phase 3: 6, Phase 4: 10, Phase 5: 6, Phase 6: 4, Phase 7: 5,
Phase 8: 10, Phase 9: 3 — total **62 SFs**), Fase-1 handlers occupy roughly
US-024 to US-033 and the prototype is feature-complete at around US-062.
Re-number as needed if a SF is split or merged during `/create-prd`; the SF-ID
↔ US-ID link is the source of traceability — record it in each story's
`Depends on` and in the brief's metadata.

**Workflow ahead, per SF:**

1. `/create-prd "<short description>"` → adds `US-XXX` to `docs/PRD.md` with the
   SF's content (this plan), the **acceptance bar** lifted into proper
   acceptance criteria, and the **dimension flags** turned into the brief
   spec-template sections.
2. `/generate-brief US-XXX` → creates `docs/briefs/US-XXX-<slug>.md` following
   `spec-template`. The brief is where the **architect's** sections (FSM,
   integrations & permissions, on-device-model impact, Android specification,
   performance, testing) are written.
3. `/implement-feature US-XXX` → work the tasks in the brief, ticking them.
4. Verify with the `verification-checklist` skill (build → lint → test → run on
   the real Redmi 15 for anything voice / ML / launcher / WhatsApp).

If a story surfaces a genuine spec gap, the right move is to refine
`docs/curro-spec-v1.0.md` (and bump its version) **before** finishing the
brief — never silently fork the answer into the brief.

---

*Master plan v1.0 — covers spec v1.0 + the agreed deviations. Revisit on every
spec-version bump.*
