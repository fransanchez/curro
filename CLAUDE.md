# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Status:** the Claude Code tooling (`.claude/`), this file, `docs/`, and the
> CI workflow exist. The **Gradle project has not been generated yet** — no `app/`
> module, wrapper, or version catalog. The structure below is the *intended* shape;
> the first user story (`docs/PRD.md` Phase 0) is "generate the project skeleton".
>
> 📖 **`docs/curro-spec-v1.0.md` is the product source of truth.** Read it before
> implementing anything. When implementation surfaces an ambiguity, refine the spec
> (and bump its version) rather than guessing.

---

## What Curro is

**Curro is an Android launcher + on-device voice assistant for an elderly user.**
It replaces the phone's home screen with a simplified surface — big clock, one large
microphone button, a few huge app tiles — and lets the user run the most common
phone actions by voice: read WhatsApp messages, call a contact, open an app, do a
calculation, tell the time. It learns the user's contact aliases ("mi hija") with
use. **Everything runs on-device** — STT, the two Gemma models, the handlers. There
is **no backend** and the app needs no network for its core function.

- **Target user**: one validated profile — Fran's father, in Málaga, on a Redmi 15.
  Deteriorated-but-functional vision (needs large text + high contrast), good
  hearing (voice feedback works well), reduced fine motor control (tap targets
  ≥ 96 dp), very slow learning curve for new UIs (**the app must feel the same
  every day**). Castellano de España, registro coloquial.
- **The prototype's only question**: does his father actually use it and does it
  improve his day? Not a product yet — a validation instrument.

## Quick Reference

- **Type**: Home-screen **launcher** (`<category android:name="android.intent.category.HOME" />`) + on-device voice assistant
- **Platform**: Android — `minSdk` **31** (Android 12; required for offline `SpeechRecognizer`), `targetSdk`/`compileSdk` **35**
- **Language**: Kotlin 2.1+
- **UI**: Jetpack Compose + Material 3, **accessibility-first** (large type, high contrast, ≥ 96 dp tap targets, audio feedback always accompanies the screen)
- **On-device ML**: **FunctionGemma 270M** int8 (~288 MB) for intent → function-call JSON, **Gemma 3n E2B** int4 (~2 GB active) for natural-language generation — both via **LiteRT** + **MediaPipe LLM Inference API**
- **Voice**: `SpeechRecognizer` (STT, offline ES) → FunctionGemma → handlers → `TextToSpeech` (TTS, ES)
- **Architecture**: MVVM + Clean Architecture (UI / Domain / Data) — here "Data" = local persistence (Room + DataStore) and Android system integrations (`NotificationListenerService`, `TelecomManager`/`InCallService`, `PackageManager`, `AudioManager`, `ContactsContract`), **not REST**
- **DI**: Hilt
- **Build**: Gradle (Kotlin DSL) + Version Catalog
- **Package**: `com.curro.app`
- **Backend**: none. **Telemetry**: Firebase (Crashlytics/Analytics) + PostHog are kept — see [Privacy & telemetry](#privacy--telemetry) (this relaxes spec §12; spec to be revised to v1.1)
- **Target user device**: Xiaomi Redmi 15 5G — Snapdragon 6s Gen 3, 8 GB RAM, Android 15 + HyperOS 2/3 (⚠️ HyperOS kills background services aggressively — Curro must be battery-whitelisted). RAM variant on the actual unit not yet confirmed (4 / 6 / 8 GB).
- **Dev / test baseline**: Samsung Galaxy A53 5G — Exynos 1280, **6 GB RAM**, Android 13 (One UI). **This is the floor — Curro must work on this device.** If Gemma 3n cold-load + summarisation works on the A53, the Redmi 15 (assumed ≥ A53 in capability) is in the clear. Run the `Gemma3nSmokeTest` instrumented test against the A53 to capture real latencies before shipping anything that depends on Gemma 3n.

## The system in one picture

Five layers with airtight responsibilities; the contract between layers is a
structured object (`FunctionCall` / `HandlerResult`), so any layer can be replaced
without touching the others. (Full detail: `docs/curro-spec-v1.0.md` §4 and the
`function-catalog`, `voice-interaction`, `on-device-llm`, `platform-integrations`
skills.)

```
1. Capture     main button (≥40% of screen) — haptic + visual feedback → records audio
2. STT         SpeechRecognizer (offline ES) → plain text   (empty/error → "No te he oído bien…")
3. Decision    FunctionGemma 270M (on-device, kept warm)    text + function catalog + minimal context
               → { "action": "<fn>", "params": {…}, "confidence": 0.0–1.0 }   (validated vs the catalog's JSON Schema)
4. Content     Gemma 3n E2B (on-device, on demand)          only when NL generation is needed (summaries, rewrites)
5. Execution   native handlers (Kotlin)                     validate params → resolve refs → confirm if needed → run Intent/Telecom/… → semantic result
6. Output      TextToSpeech (ES) + on-screen support        every Curro→user message is spoken AND shown
```

### State machine (the heart of the app)

States: `idle` · `listening` · `processing` · `confirming` · `executing` · `error_recovery`.
**Any state is interruptible by a new button press** → cancels what's running, goes
back to `listening` (the user must be able to "cut off" Curro mid-read). After an
action, everything returns to `idle`. See the `voice-interaction` skill for the
diagram, the confidence-graduated confirmation policy (defaults 0.85 / 0.60,
adjustable in the config menu), the consecutive-failure recovery messages, and the
10-second silence-cancel rule.

### Function catalog

The `function-catalog` skill holds the canonical, machine-readable catalog (the
single source FunctionGemma is prompted with). Phase 1 (prototype MVP) — implement
**in this order** (first four are zero-risk, last three touch sensitive permissions):
`tell_time` → `open_app` → `calculate` → `help` → `read_last_whatsapp` →
`read_all_unread_whatsapp` → `call_contact`. Adding a function = `/add-function`
(updates the catalog, scaffolds a handler + tests, registers it).

## Slash Commands

### PRD workflow
- `/create-prd [description]` — add a user story to `docs/PRD.md`
- `/generate-brief US-XXX` — generate the implementation brief (spec + tasks)
- `/implement-feature US-XXX` — work the brief, ticking tasks

### Development
- `/plan-feature US-XXX` — Plan Mode: design the architecture before coding
- `/create-screen [Name]` — scaffold a Compose screen + ViewModel (Curro has few "screens" — most of the assistant UI is state-driven overlays; use this for the launcher home, the config menu, etc.)
- `/create-handler [Name]` — scaffold a function handler in the execution layer
- `/add-function [name]` — add a function to the catalog (handler + tests + registration)
- `/build [debug|release|clean]` — build
- `/test [unit|ui|all]` — run tests
- `/lint [fix]` — ktlint / detekt
- `/fixture [type]` — generate test fixtures (contacts, aliases, whatsapp-notifications, function-call-json, failed-commands, app-list)

### Git & PR
- `/generate-mr-description` — PR body from the diff against `main`

## Design Skills Hierarchy

When making a UI decision, consult in this order:

1. **brand-design** (AUTHORITATIVE) — non-negotiable brand specs: colors, typography, spacing, shapes, **and Curro's voice/Spanish copy**. ⚠️ *currently a template — fill it in (with the real Curro brand and the senior-first constraints) before shipping UI.*
2. **launcher-ui** — Curro's actual surfaces (home, listening overlay, processing, confirmation, message cards, contact picker, config menu) + the senior-first rules (≥ 96 dp targets, minimum text sizes, "feels the same every day", audio + visual together)
3. **accessibility-patterns** — Compose a11y mechanics (semantics, live regions, focus, TalkBack) on top of the senior-first baseline
4. **material-design** — Material 3 components, *scaled up* for this user
5. **compose-patterns** — Jetpack Compose implementation patterns

## Agents & Skills

**Subagents** (specialized helpers):

| Agent | Color | Model | Purpose |
|-------|-------|-------|---------|
| `android-product-analyst` | Blue | Opus | Requirements analysis, brief creation (spec ⇄ brief) |
| `android-architect` | Purple | Opus | System/Clean-Architecture design for features |
| `ondevice-ai-engineer` | Pink | Opus | FunctionGemma + Gemma 3n: LiteRT/MediaPipe, model warm-keeping, prompts, JSON-schema validation, latency/OOM |
| `voice-pipeline-engineer` | Cyan | Opus | STT/TTS pipeline, the main button, the state machine, confirmation policy, Curro's voice |
| `android-developer` | Orange | Sonnet | Feature implementation in Kotlin (handlers, launcher UI, data layer, glue) |
| `android-debugger` | Yellow | Opus | Root-cause analysis (Compose, coroutines, Hilt, LLM inference, NotificationListener, launcher/HyperOS quirks) |
| `android-qa-specialist` | Red | Sonnet | Tests — unit (JUnit5+Mockk+Turbine), UI (Compose test), integration; FSM + STT/TTS/LLM mocking |
| `kotlin-reviewer` | Cyan | Sonnet | Code review — Kotlin idioms, Compose, Clean Arch, Hilt, perf |
| `android-ui-designer` | Green | Sonnet | UI review — Material 3 *scaled for seniors*, brand compliance, accessibility |

**Skills** (auto-activated by context):

| Skill | Triggers | Purpose |
|-------|----------|---------|
| `brand-design` | colors, fonts, spacing, Curro's voice | **AUTHORITATIVE** brand + copy spec |
| `launcher-ui` | launcher home, overlays, message cards, config menu, senior UI | Curro's UI surfaces + senior-first rules |
| `function-catalog` | function catalog, intents, what Curro can do, adding a function | The canonical catalog + the FunctionGemma JSON contract |
| `voice-interaction` | state machine, listening/confirming, confidence, error recovery, Curro's tone | The FSM + confirmation policy + recovery + voice |
| `on-device-llm` | LiteRT, MediaPipe, FunctionGemma, Gemma 3n, model loading, inference latency | On-device LLM integration patterns |
| `platform-integrations` | NotificationListener/WhatsApp, calls/Telecom/InCallService, opening apps, volume, contacts/ambiguity | Android system integration patterns |
| `launcher-app` | CATEGORY_HOME, default launcher, HOME button, overlays, foreground service, HyperOS battery | Building an Android launcher |
| `local-data` | Room, DataStore, aliases, favorite apps, failed-commands log, learning, reset | Local persistence + the alias-learning subflow |
| `material-design` | Material 3, theming, components | Material 3 patterns (scaled up) |
| `compose-patterns` | @Composable, state, modifiers, ViewModel | Jetpack Compose implementation |
| `accessibility-patterns` | TalkBack, semantics, live regions, touch targets | Compose a11y mechanics |
| `navigation-patterns` | NavHost, routes | Navigation Compose (Curro's nav is minimal) |
| `testing-patterns` | JUnit, Mockk, Turbine, Compose testing, FSM tests | Test patterns |
| `verification-checklist` | task completion | Build → lint → test → run → privacy/permissions check |
| `git-workflow` | commits, branches | Git conventions (scopes for Curro) |
| `spec-template` | feature specs/briefs | Standard brief format |
| `api-integration`, `api-contract` | *(parked)* | Curro has no custom REST backend — kept only for a possible Phase 3 (`read_news_headlines`) or a future companion service |
| `adaptive-layout` | *(stub)* | Single fixed phone, portrait-locked — only the system-insets/orientation notes apply |

## PRD-Driven Workflow

```
docs/
├── curro-spec-v1.0.md   # product spec — the source of truth (read first)
├── PRD.md               # user stories, grouped into phases
└── briefs/              # implementation briefs (spec + tasks combined), one per story
    └── US-001-….md
```

1. Define a user story in `docs/PRD.md` with acceptance criteria — `/create-prd`.
2. `/generate-brief US-XXX` → `docs/briefs/US-XXX-<slug>.md`.
3. `/implement-feature US-XXX` → work the tasks, ticking them.
4. Verify with the `verification-checklist` skill (build → lint → test → run on the device → privacy check).

## Building & Running

```bash
./gradlew assembleDebug          # build debug APK
./gradlew assembleRelease        # build release APK
./gradlew installDebug           # install on the connected device / emulator
./gradlew test                   # unit tests (JVM)
./gradlew connectedAndroidTest   # instrumented tests (needs a device/emulator)
./gradlew ktlintCheck detekt     # lint
./gradlew ktlintFormat           # auto-fix Kotlin formatting
```

Set Curro as the default launcher on the device after install:

```bash
adb shell cmd package set-home-activity com.curro.app/.MainActivity
# or: open Settings → Apps → Default apps → Home app → Curro
adb shell am start -n com.curro.app/.MainActivity   # bring the launcher to front
```

> ⚠️ **The release APK bundles ~2.3 GB of model weights.** Decide early whether to
> ship the models inside the APK/AAB, download them on first run, or side-load them
> for the prototype. CI builds `assembleDebug`; the model files should be excluded
> from the debug build (or stubbed) so CI stays fast.

## Architecture

Clean Architecture, three layers. Curro is essentially **one large feature (the
assistant)** plus a couple of small ones (the launcher home, the hidden config
menu) — don't force a "feature folder per screen" split that doesn't exist.

```
app/src/main/java/com/curro/app/
├── CurroApp.kt                  # @HiltAndroidApp Application
├── MainActivity.kt              # the launcher Activity (CATEGORY_HOME), enableEdgeToEdge, setContent { CurroTheme { … } }
│
├── domain/                      # pure Kotlin — no Android imports
│   ├── model/                   # Command, Transcript, FunctionCall, Confidence, AssistantState, Contact, Alias, WhatsAppMessage, HandlerResult, …
│   ├── catalog/                 # the function-catalog definitions + JSON schema (mirrors the `function-catalog` skill)
│   ├── repository/              # interfaces: AliasRepository, ContactRepository, NotificationRepository, InstalledAppsRepository, SettingsRepository, FailedCommandLog
│   └── usecase/                 # ProcessCommandUseCase (orchestrator), ResolveContactUseCase, plus one per function: TellTimeUseCase, CalculateUseCase, ReadUnreadWhatsAppUseCase, …
│
├── data/                        # implementations + system integrations
│   ├── local/                   # Room (entities/daos/db) + DataStore (settings: TTS voice/rate, confidence thresholds, toggles)
│   ├── ml/                      # FunctionGemmaEngine, Gemma3nEngine (LiteRT/MediaPipe wrappers), prompt builders, FunctionCall validator
│   ├── voice/                   # SttClient (SpeechRecognizer wrapper), TtsClient (TextToSpeech wrapper)
│   ├── notification/            # CurroNotificationListenerService + WhatsApp parser + unread cache
│   ├── telephony/               # CallController; CurroInCallService (incoming-call assistant mode — opt-in, off by default)
│   ├── apps/                    # InstalledAppsProvider, AppLauncher (resolve a colloquial app name → component)
│   ├── contacts/                # ContactsProvider (resolve a name/alias → contact, surface ambiguity)
│   └── repository/              # repository implementations
│
├── handler/                     # the execution layer — one FunctionHandler per catalog function
│   ├── FunctionHandler.kt       # interface: suspend fun handle(call: FunctionCall): HandlerResult
│   ├── TellTimeHandler.kt · OpenAppHandler.kt · CalculateHandler.kt · HelpHandler.kt
│   ├── ReadLastWhatsAppHandler.kt · ReadAllUnreadWhatsAppHandler.kt · CallContactHandler.kt
│   └── …                        # Phase 2+: SendWhatsAppReplyHandler, SetVolumeHandler, …
│
├── assistant/                   # the brain
│   ├── AssistantStateMachine.kt # idle/listening/processing/confirming/executing/error_recovery + interrupt-by-button
│   ├── AssistantCoordinator.kt  # wires capture → STT → FunctionGemma → (Gemma 3n) → handler dispatch → TTS
│   └── ConfidencePolicy.kt      # the 0.85 / 0.60 thresholds + always-escalate cases (ambiguous param, irreversible cost, "always confirm" toggle)
│
├── service/
│   └── ModelWarmupService.kt    # foreground service keeping FunctionGemma warm (POST_NOTIFICATIONS)
│
├── presentation/
│   ├── theme/                   # CurroTheme, CurroColorScheme (light+dark), CurroTypography, CurroShapes, CurroSpacing — wired to brand-design
│   ├── launcher/                # LauncherScreen (clock + mic button + app grid + "más apps"), LauncherViewModel
│   ├── assistant/               # state-driven overlays: ListeningOverlay, ProcessingOverlay, ConfirmationOverlay (big SÍ/NO), MessageCardsScreen, ContactPickerScreen
│   ├── config/                  # ConfigMenuScreen (the hidden Fran menu — 5 taps on the clock), ConfigViewModel
│   ├── common/                  # shared composables (BigPrimaryButton, BigCard, …)
│   └── navigation/              # CurroNavHost — minimal: launcher home ⇄ config menu; the assistant overlays are state-driven, not nav routes
│
└── util/                        # Constants.kt, Extensions.kt, logging, etc.
```

Tests: `app/src/test/java/com/curro/app/` (JVM, JUnit5 + Mockk + Turbine + Robolectric), `app/src/androidTest/java/com/curro/app/` (instrumented, Compose UI test + Hilt test runner `com.curro.app.HiltTestRunner`).

### Permissions

Declared in the manifest; requested only when the corresponding capability is used.
(Full table with rationale: spec §10.) **No `INTERNET` permission for the core
app** — `INTERNET` is declared **only** in `app/src/release/AndroidManifest.xml` (never
in `src/main`) for the telemetry SDKs (Firebase + PostHog). The debug APK has no `INTERNET`.

| Permission | For | If denied |
|---|---|---|
| `RECORD_AUDIO` | STT | app is unusable |
| `READ_CONTACTS` | resolve names → contacts | no "call …" |
| `CALL_PHONE` | place calls | no "call …" |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | read WhatsApp | no "read WhatsApp" |
| `INTERNET` *(release only)* | Firebase Crashlytics/Analytics + PostHog | telemetry SDKs fail silently; app keeps working |
| `POST_NOTIFICATIONS` | model warm-up foreground service | model runs cold, more latency |
| `QUERY_ALL_PACKAGES` | list apps to open by name | only pre-configured apps |
| `READ_SMS` *(opt-in)* | read SMS | no "read SMS" |
| `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS` *(opt-in)* | incoming-call assistant mode | no assistant mode |
| `SYSTEM_ALERT_WINDOW` *(eval)* | feedback overlay over other apps | feedback only inside the launcher |

The launcher itself needs no permission — `<category android:name="android.intent.category.HOME">` + `<category android:name="android.intent.category.DEFAULT">` in the manifest; Android offers the user to make Curro the default home app.

### On-device models

| Model | Quant | Size | Role | Loading |
|---|---|---|---|---|
| FunctionGemma 270M | int8 | ~288 MB | text → `{action, params, confidence}` | kept **warm** in memory via a foreground service; target < 500 ms text→JSON |
| Gemma 4 E2B | int4 | ~2–3 GB active | NL generation (summaries, rewrites, open questions) | loaded **on demand**; if cold, say "Dame un segundo"; target 3–6 s typical (Apache 2.0; swapped in from Gemma 3n in May 2026 — see `docs/architecture/gemma-text-engine-decision.md`) |

Runtime: **LiteRT** (formerly TFLite) + **MediaPipe LLM Inference API**. See the
`on-device-llm` skill. Risk: the 4 GB-RAM variant of the Redmi 15 makes Gemma 3n
marginal — confirm the device variant before relying on Phase 3 features that need
it. In Phase 1, Gemma 3n may not be strictly necessary — evaluate before loading it.

**Side-load for the prototype** (US-019 / SF-3.1): the FunctionGemma `.task`
weights live on the device at `/data/local/tmp/curro-models/function_gemma_270m.task`;
the path is configurable via `local.properties` (`CURRO_MODEL_BASE_PATH`) and exposed
at runtime as `BuildConfig.MODEL_BASE_PATH`. The single seam every later caller
goes through is `data/ml/ModelFiles.kt`. A future SF will introduce bundled /
asset-pack delivery for release without changing that abstraction. The full
developer workflow (`adb push`, HyperOS battery whitelist) is documented in
[`models/README.md`](models/README.md). The "release APK bundles ~2.3 GB of model
weights" admonition still applies once delivery is bundled.

## Privacy & telemetry

Spec §12 (v1.0) said *nothing* leaves the device. **The project has since opted to
keep crash + product telemetry** (Firebase Crashlytics/Analytics + PostHog) — so:

- The app **does** send crash reports and product-analytics events off-device, and
  therefore **does** need `INTERNET` for those SDKs. Keep this isolated and clearly
  labelled (e.g., a separate process or a build-time flag) — it must never become a
  channel for the data below.
- These **still never leave the device**: recorded audio; transcribed text; the
  content of messages read aloud; the contact list and learned aliases; the command
  history / failed-commands log.
- The config menu still has the "send failures to Fran" toggle (off by default) for
  anonymized model-failure logs.
- **Action item:** revise `docs/curro-spec-v1.0.md` §12 to v1.1 to reflect this.

When using telemetry: never log message content, contact names, transcripts, or
audio. Event names/properties must be safe to read in a dashboard.

## Dependencies

> Intended stack — add to the version catalog as features need it.

**Core**: Jetpack Compose + Material 3 · Hilt · Navigation Compose (minimal) · Room · DataStore · Coroutines/Flow
**On-device ML**: LiteRT · MediaPipe Tasks GenAI (LLM Inference API) · (Gemma model files — see above)
**Media**: Coil (Compose image loading — contact photos, app icons)
**Telemetry**: Firebase Crashlytics + Firebase Analytics · PostHog *(see [Privacy & telemetry](#privacy--telemetry))* · Firebase Auth/FCM are *available* but unused (no accounts, no push)
**Testing**: JUnit 5 · Mockk · Turbine · Robolectric · Compose UI Test

There is **no** Retrofit / OkHttp / Kotlin-Serialization-for-network in the core
stack — Curro talks to no API of ours. (If Phase 3 `read_news_headlines` lands,
add a minimal HTTP fetch then, not before — see the parked `api-integration` skill.)

## Coding Standards

### General
- SOLID; composition over inheritance; short functions (< 20 lines); single level of abstraction.
- **English for all code and documentation.** **User-facing strings are Spanish** (the user's language) and live in resources / a copy module — see `brand-design` for Curro's voice. Never hard-code Spanish strings in composables.
- One class/interface per file.

### Naming
- **PascalCase**: classes, interfaces, objects, composables. **camelCase**: variables, functions, properties. **kebab-case**: resource files, module names. **SCREAMING_SNAKE_CASE**: constants, enum values.
- Prefixes: `is*`/`has*`/`can*`/`should*` (booleans), `use*` (state-managing composables), `on*` (event callbacks), `handle*` (ViewModel event handlers).

### Kotlin style
- `data class` for models; `sealed interface`/`sealed class` for states, events, results; `kotlin.Result` or a domain `sealed class` for operation outcomes.
- Prefer `StateFlow` over `LiveData`; `suspend fun` for async; `Flow` for streams.
- **Never `!!`** — use `?.let`, `?:`, or `requireNotNull(x) { "msg" }`.
- Read theme tokens via `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` / `CurroSpacing.*` / `CurroShapes.*` — **never** raw `Color(0xFF…)` / `.sp` / `.dp` literals in composables.

### Jetpack Compose
- Screens are `@Composable` functions; stateless content composables (receive state, emit events); state hoisted to a ViewModel via `StateFlow`.
- `remember` / `rememberSaveable` for local state; `LaunchedEffect` for side effects; `Modifier` as the first optional parameter; `@Preview` for every reusable component (light + dark + a large-font preview).

### ViewModel pattern

```kotlin
@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val observeClock: ObserveClockUseCase,
    private val observeFavoriteApps: ObserveFavoriteAppsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LauncherUiState>(LauncherUiState.Loading)
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    fun onEvent(event: LauncherEvent) {
        when (event) {
            is LauncherEvent.MicPressed   -> startListening()
            is LauncherEvent.AppTileTapped -> openApp(event.packageName)
            is LauncherEvent.ClockTapped   -> registerClockTap()   // 5 taps in 3 s → config menu
        }
    }
    // …
}

sealed interface LauncherUiState {
    data object Loading : LauncherUiState
    data class Ready(val clock: ClockState, val apps: List<AppTile>) : LauncherUiState
}

sealed interface LauncherEvent {
    data object MicPressed : LauncherEvent
    data class AppTileTapped(val packageName: String) : LauncherEvent
    data object ClockTapped : LauncherEvent
}
```

### Assistant flow (handlers + state machine)

```kotlin
// Domain — every catalog function has a handler with this shape:
interface FunctionHandler {
    val function: CatalogFunction                       // which catalog entry it serves
    suspend fun handle(call: FunctionCall): HandlerResult
}

sealed interface HandlerResult {
    /** Done. [speech] is what TTS should say (Spanish); [screen] is the optional supporting UI state. */
    data class Spoken(val speech: String, val screen: AssistantScreen? = null) : HandlerResult
    /** Needs confirmation before doing the irreversible part. */
    data class NeedsConfirmation(val prompt: String, val onConfirm: ConfirmableAction) : HandlerResult
    /** The handler couldn't do it — [speech] explains why in plain Spanish (never a code). */
    data class Failed(val speech: String, val reason: HandlerError) : HandlerResult
}

// The coordinator runs the FSM; ConfidencePolicy decides execute vs confirm vs clarify.
// Invalid FunctionGemma output → no automatic retry → "Eso no lo sé hacer todavía" + log it (spec flow 7).
```

### Error handling

There is no HTTP. Model the failures Curro actually has:

```kotlin
sealed interface CurroError {
    // Speech-to-text
    data object SttNoMatch : CurroError            // ERROR_NO_MATCH / empty
    data object SttTimeout : CurroError            // ERROR_SPEECH_TIMEOUT
    data class  SttError(val code: Int) : CurroError
    data object SttVoicePackMissing : CurroError   // isOnDeviceRecognitionAvailable=false / ERROR_LANGUAGE_*

    // Text-to-speech
    data object TtsLanguageMissing : CurroError    // setLanguage → LANG_MISSING_DATA / LANG_NOT_SUPPORTED
    data class  TtsError(val code: Int) : CurroError   // UtteranceProgressListener.onError

    // Decision layer
    data object ModelCold : CurroError             // FunctionGemma/Gemma3n not loaded yet → "Dame un segundo"
    data object InvalidFunctionCall : CurroError   // output failed JSON-schema validation (spec flow 7) — DO NOT auto-retry
    data class  UnknownFunction(val name: String) : CurroError   // valid JSON, function not in this phase
    data object OutOfMemory : CurroError

    // Execution layer
    data object PermissionDenied : CurroError
    data class  ContactNotFound(val query: String) : CurroError
    data class  AmbiguousContact(val matches: List<Contact>) : CurroError  // → always confirm (e.g. 3 Marías)
    data class  AppNotFound(val query: String) : CurroError
    data class  Calculation(val expression: String) : CurroError
    data object NotificationAccessMissing : CurroError
}
```

Every `CurroError` maps to a calm Spanish sentence + a proposed alternative — **never
a code, never silence** (spec §2: "Fallar de forma comprensible"). User-facing copy
lives with `brand-design`.

### Screen layout

`CurroNavHost` is a single `Scaffold` that applies `Modifier.padding(innerPadding)`
to the `NavHost`; **child screens do not add their own `Scaffold` / `TopAppBar` /
`statusBarsPadding()`** (that double-pads the top). The launcher home and the config
menu are the only routes; the assistant's listening/processing/confirming UI are
state-driven overlays, not routes. There are no per-screen back arrows on the
launcher; the config menu uses a `Box` + overlay `IconButton`
(`Icons.AutoMirrored.Filled.KeyboardArrowLeft`, large).

## Brand

The **`brand-design` skill is AUTHORITATIVE** for colors, typography, spacing,
shapes, **and Curro's voice / Spanish copy** — read it before any UI or copy
decision. ⚠️ It currently ships as a **template with `TODO`s** plus a temporary
Material-3 scaffold so the app compiles. Replace those with the real Curro brand and
the senior-first constraints (≥ 96 dp targets, minimum text sizes well above Material
defaults, high contrast, "feels the same every day"), and keep the skill in sync
with `presentation/theme/` (`CurroTheme`, `CurroColorScheme`, `CurroTypography`,
`CurroShapes`, `CurroSpacing`).

Curro's voice (spec §2): warm, Andalusian, coloquial — efficient and close, not
servile. *"Vale, llamando a Pepito"* — not *"claro, cómo no, ahora mismo"*. No
constant apologies. Errors are plain and honest.

## Environment Variables

`local.properties` (git-ignored — never commit):

```properties
sdk.dir=/path/to/Android/sdk

# Release signing
KEYSTORE_PATH=…
KEYSTORE_PASSWORD=…
KEY_ALIAS=…
KEY_PASSWORD=…
```

- `local.properties`, `*.keystore` / `*.jks`, `.env*`, and `google-services.json` are git-ignored and must never be committed.
- Firebase: put `google-services.json` in `app/`, add the Google Services Gradle plugin, and store the file base64-encoded as the `GOOGLE_SERVICES_JSON` CI secret (the decode step is in `.github/workflows/ci.yml`).
