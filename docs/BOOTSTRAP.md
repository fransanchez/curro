# Bootstrap — what's here, what was done, what to initialize

> Created 2026-05-13. Read once, keep as a checklist, delete when no longer useful.

This repo currently contains **only**: the Claude Code tooling (`.claude/`),
`CLAUDE.md`, this `docs/` folder, a `README.md`, a `.gitignore` and a CI workflow.
There is **no Gradle project / `app/` module yet** — you generate that from here.

> 📄 **The product is fully specified in [`docs/curro-spec-v1.0.md`](curro-spec-v1.0.md)** — that document is the source of truth. This file only covers the repo scaffolding and the path from "empty repo" to "first build".

---

## 0. What Curro is (one paragraph)

Curro is an **Android launcher** (`CATEGORY_HOME`) for an elderly user — a simple
visual home screen (big clock, big mic button, a few large app shortcuts) plus an
**on-device voice assistant**: press button → speak → native STT → **FunctionGemma
270M** maps the utterance to a function call (JSON) → native Kotlin handler executes
it (read WhatsApp, call a contact, open an app, calculate, tell the time…) →
**Gemma 3n E2B** is invoked only when natural-language generation is needed → native
TTS speaks the result. **Everything runs on-device — no cloud, no backend, no
internet dependency.** Personality: warm, colloquial Castilian Spanish ("andaluza"),
helpful but not servile. Target hardware: Xiaomi Redmi 15. Inference runtime:
LiteRT + MediaPipe LLM Inference API. See the spec for the function catalogue,
interaction flows, the confidence-graded confirmation policy, the alias-learning
model, the (Fran-only) settings menu, permissions, and the prototype validation
criteria.

---

## 1. What's in the repo right now

> The `.claude/` tooling and `CLAUDE.md` have been **Curro-ized** — they're no
> longer the generic restaurant-app template they were copied from (see §2–§3).

```
.claude/
├── settings.json                 # PROJECT_NAME=Curro, PACKAGE_NAME=com.curro.app, minSdk=31, target/compile=35, tool permissions
├── agents/                       # 9 sub-agents (table below)
├── skills/                       # 19 skills (list below)
├── commands/                     # 12 slash commands
└── hooks/post-kotlin-edit.sh     # runs `./gradlew ktlintFormat` after editing .kt/.kts

CLAUDE.md                         # architecture conventions + PRD workflow — rewritten for Curro (launcher + on-device assistant, no backend)
README.md                         # short intro, points here
.gitignore                        # standard Android ignores (+ local.properties, google-services.json, .claude/projects, .claude/memory)
.github/workflows/ci.yml          # GitHub Actions: decode google-services.json (if present) → lint → assembleDebug → unit tests
docs/curro-spec-v1.0.md           # ⭐ THE PRODUCT SPEC — source of truth
docs/PRD.md                       # phased user-story backlog (the phases follow spec §14; stories TBD via /create-prd)
docs/briefs/                      # implementation briefs land here (empty)
docs/BOOTSTRAP.md                 # this file
```

### Sub-agents (`.claude/agents/`)

| Agent | Model | Purpose |
|-------|-------|---------|
| `android-product-analyst` | Opus | Turns spec sections / ideas into functional specs & briefs |
| `android-architect` | Opus | Designs Clean Architecture for features (the 5-layer pipeline, layers, Hilt, services, persistence, navigation, Compose) |
| `ondevice-ai-engineer` | Opus | FunctionGemma + Gemma 3n — LiteRT/MediaPipe, model warm-keeping, prompts, JSON-schema validation, latency/OOM |
| `voice-pipeline-engineer` | Opus | STT/TTS pipeline, the main button, the state machine, the confirmation policy, Curro's voice |
| `android-developer` | Sonnet | Implements features in Kotlin (handlers, launcher UI, data layer, glue) |
| `android-debugger` | Opus | Root-cause analysis (Compose, coroutines, Hilt, LLM inference, NotificationListener, launcher/HyperOS, R8…) |
| `android-qa-specialist` | Sonnet | Tests — unit (JUnit5+Mockk+Turbine), UI (Compose test), integration; FSM + STT/TTS/LLM mocking |
| `kotlin-reviewer` | Sonnet | Code review — Kotlin idioms, Compose, Clean Arch, Hilt, perf |
| `android-ui-designer` | Sonnet | UI review — Material 3 *scaled for seniors*, brand compliance, accessibility |

### Skills (`.claude/skills/`)

**Curro-specific:** `brand-design` *(AUTHORITATIVE — colors/type/spacing/shapes + Curro's voice; still a TODO template)*, `launcher-ui` *(Curro's surfaces + the senior-first rules; replaces the old `feed-design`)*, `function-catalog`, `voice-interaction`, `on-device-llm`, `launcher-app`, `platform-integrations`, `local-data`.
**General Android (now Curro-flavoured):** `material-design`, `compose-patterns`, `accessibility-patterns`, `navigation-patterns`, `testing-patterns`, `verification-checklist`, `git-workflow`, `spec-template`.
**Parked / stubbed:** `api-integration`, `api-contract` *(no custom REST backend — kept only for a possible Phase-3 news fetch / future companion service)*, `adaptive-layout` *(single fixed phone, portrait — only the system-insets bits apply)*.

### Slash commands (`.claude/commands/`)

`create-prd`, `generate-brief`, `implement-feature`, `plan-feature`, `create-screen`,
`create-handler`, `add-function`, `build`, `test`, `lint`, `fixture`, `generate-mr-description`.

---

## 2. What was done to create this repo (initial bootstrap — see §3 for the subsequent Curro-ization)

The `.claude/` tooling + `CLAUDE.md` were originally **copied from another Android
project** (`greenjacket-android`, a restaurant social app) and **genericized** as
below — then **rewritten for Curro** (§3):

- Package → `com.curro.app`; project name → `Curro` (`settings.json`, `CLAUDE.md`, agents, commands, skills).
- All `com.greenjacket.*` / `com.findthebutton.button` paths → `com/curro/app`.
- Design-system identifiers `ButtonSpacing`/`ButtonTheme`/`ButtonShapes`/`ButtonTypography`/`ButtonNavHost` → `CurroSpacing`/`CurroTheme`/`CurroShapes`/`CurroTypography`/`CurroNavHost`.
- API hostnames & dev domains → placeholders; backend-repo references → `../curro-backend/`.
- **`brand-design` skill** rewritten as a clean **template** with `TODO`s + a temporary Material-3 scaffold so things compile. **Not the real brand.**
- **`CLAUDE.md`** genericized: kept the Clean Architecture template, the PRD workflow, and all the Kotlin / ViewModel / Screen / Repository / error-handling coding standards; brand colors, "Key Domains", "Navigation Structure" and "Environments" are placeholders marked **TBD**.
- `android-ui-designer` agent: dropped the old hard-coded palette and the app-specific (5-tab / restaurants / NFC) guidelines; replaced with a generic checklist that defers to `brand-design`.
- Added `docs/PRD.md` (template), `docs/briefs/.gitkeep`, `README.md`, `.github/workflows/ci.yml`.
- Set `MIN_SDK=31` in `.claude/settings.json` (the spec requires Android 12 for offline STT — bumped from the generic default of 26).
- `git init` on branch `main` (nothing committed yet).

### Deliberately NOT copied
The Gradle project itself — `app/` module, `gradlew`/`gradlew.bat`, `gradle/wrapper/`, `build.gradle.kts`, `settings.gradle.kts`, version catalog, `google-services.json`, any Kotlin source/tests, the source project's real `docs/`, spreadsheets, screenshots.

---

## 3. ✅ The tooling has been Curro-ized — what changed

The §2 copy started as a generic restaurant-app template (REST backend, Firebase
auth, tabbed nav, a content feed). Curro is none of those, so the tooling was
rewritten:

| Area | Before (generic) | Now (Curro) |
|------|------------------|-------------|
| `CLAUDE.md` | generic REST-backed app, Firebase Auth flow, "Environments" table, restaurant framing | rewritten: launcher (`CATEGORY_HOME`), the 5-layer pipeline, on-device ML stack (LiteRT + MediaPipe + FunctionGemma + Gemma 3n), the FSM, the function catalog, the voice pipeline, no backend, senior-first principles, `minSdk 31`, HyperOS caveat, Privacy & telemetry (Firebase/PostHog kept — deviation from spec §12, to be revised to v1.1), the package layout, `CurroError` (not HTTP errors) |
| Backend / `api-integration` / `api-contract` | Retrofit + REST, `../curro-backend/`, staging/prod URLs | **no backend**; `api-integration` & `api-contract` *parked* (kept only for a possible Phase-3 news fetch / future companion service); no `../curro-backend/` anywhere |
| `feed-design` skill | restaurant card/list "feed" | **deleted** → replaced by `launcher-ui` (Curro's real surfaces: launcher home, the assistant overlays, message cards, contact picker, config menu) + the senior-first rules |
| New skills added | — | `function-catalog`, `voice-interaction`, `on-device-llm`, `launcher-app`, `platform-integrations`, `local-data` |
| New agents added | — | `ondevice-ai-engineer` (LiteRT/MediaPipe, model warm-keeping, prompts, JSON-schema validation), `voice-pipeline-engineer` (STT/TTS, the main button, the FSM, the confirmation policy, Curro's voice) |
| Existing agents/skills | restaurant examples, Retrofit, Firebase tokens, `develop` branch, `48dp` targets | de-restauranted; Curro examples; `main`-only branching; `≥96dp` targets; FSM/STT/TTS/LLM/NotificationListener/launcher coverage added to the debugger, QA, and dev agents; `android-ui-designer` upgraded to the senior-first bar |
| `verification-checklist` | had a `com.greenj.button` bug; staging/prod build variants | bug fixed (`com.curro.app`); variants removed; added privacy/permissions, on-device-model, FSM, and real-device checks |
| `git-workflow` | "matching backend and iOS", `develop` branch, restaurant scopes | `main`-only; Curro scopes (`launcher`, `voice`, `stt`, `tts`, `fsm`, `llm`, `handler`, `catalog`, `alias`, `config`, `data`, `notif`, `telecom`, `apps`, …) |
| New commands | — | `/create-handler` (scaffold a `FunctionHandler`), `/add-function` (add a catalog function end-to-end); `/fixture` & `/create-screen` re-pointed at Curro |
| `minSdk` | 26 (generic) | **31** in `.claude/settings.json` (Android 12 — offline `SpeechRecognizer`) — reflect it in `app/build.gradle.kts` when you generate the project |
| `adaptive-layout` skill | full responsive/tablet/foldable | stubbed — single fixed phone, portrait-locked; only the system-insets bits apply |
| `ci.yml` | google-services step commented out (no Firebase planned) | Firebase is in — the decode step is active (no-ops until the `GOOGLE_SERVICES_JSON` secret + the `app/` module exist) |

> Still **template / TODO**: `brand-design` (the real colours/type/spacing/shapes/voice
> — currently a Material-3 scaffold + `TODO`s; it now also carries the senior-first
> constraints and Curro's Spanish copy). And the spec itself should be bumped to **v1.1**
> to reflect the Firebase/PostHog telemetry decision (spec §12 said "nothing leaves the
> device").

---

## 4. What to initialize (build order from spec §14)

### 4.0 Gradle project skeleton (do this first)
Generate with Android Studio "New Project → Empty Activity (Compose)" **into this folder**, or have Claude Code scaffold it. Target state:

- [ ] `gradlew`/`gradlew.bat` + `gradle/wrapper/`; `settings.gradle.kts`, root `build.gradle.kts`, `app/build.gradle.kts`; `gradle/libs.versions.toml` (version catalog)
- [ ] `applicationId = "com.curro.app"`, `minSdk = 31`, `compileSdk`/`targetSdk` = 34+ (match `.claude/settings.json`; bump `COMPILE_SDK`/`TARGET_SDK` there if you go to 35/36)
- [ ] Jetpack Compose + Material 3 (`buildFeatures { compose = true }`); Hilt plugin + `@HiltAndroidApp class CurroApp`
- [ ] ktlint + detekt Gradle plugins (the `/lint` command and CI run `./gradlew ktlintCheck detekt`)
- [ ] Package layout per `CLAUDE.md`: `data/` (here = **local** persistence + system-integration handlers, not remote), `domain/`, `presentation/` (`navigation/`, `theme/`, `common/`, `feature/`), `di/`, `util/`
- [ ] `presentation/theme/`: `CurroTheme`/`CurroColorScheme`/`CurroTypography`/`CurroShapes`/`CurroSpacing` — accessibility-first values from the (updated) `brand-design` skill
- [ ] Add `LiteRT` / `MediaPipe LLM Inference` dependencies (and decide how model files are delivered — **not committed to git**)

### 4.1 Launcher base (spec §11) — no assistant yet
- [ ] `MainActivity` with `<intent-filter>`: `MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT`; `RoleManager` flow to offer "set as default launcher"
- [ ] Home screen: big clock + date, large mic button (≥40% of screen, haptic on press), 4–6 large app-shortcut tiles, "Más apps" secondary screen
- [ ] **Validate with the real user** that this works as a replacement for the stock launcher *before adding voice*

### 4.2 Voice pipeline (STT → on-screen log → TTS) — no decision model yet
- [ ] `RECORD_AUDIO`; native `SpeechRecognizer` (Spanish, offline); show live transcription
- [ ] Native `TextToSpeech` (Spanish voice); speed ~10–15% slower default
- [ ] Confirm the capture→response loop works on the real device

### 4.3 FunctionGemma integration (spec §4.3, §5 Fase 1 catalogue) — no real handlers yet
- [ ] Load FunctionGemma 270M (int8) via MediaPipe LLM Inference; keep warm in a foreground service (`POST_NOTIFICATIONS`); latency target <500 ms text→JSON
- [ ] Prompt = the Fase-1 function catalogue + minimal context (current time, unread messages, known aliases)
- [ ] Validate model output against the catalogue's **JSON schema**; show the returned JSON on screen for debugging; on invalid output → no auto-retry, speak a friendly fallback (flow 7)

### 4.4 Fase 1 handlers (spec §5 + §14, in this order — first four are low-risk)
- [ ] `tell_time` · [ ] `open_app` (`QUERY_ALL_PACKAGES`) · [ ] `calculate` · [ ] `help`
- [ ] `read_last_whatsapp` · [ ] `read_all_unread_whatsapp` (`NotificationListenerService` — robust parser + tests + fallback)
- [ ] `call_contact` (`READ_CONTACTS` + `CALL_PHONE`; contact/alias resolution)

### 4.5 State machine (spec §6)
- [ ] `idle`/`listening`/`processing`/`confirming`/`executing`/`error_recovery`; any new button press interrupts the current state and returns to `listening`; 10 s no-answer timeout in `confirming`; consecutive-STT-failure policy (1st/2nd/3rd message then give up)

### 4.6 Confidence-graded confirmation (spec §4.3)
- [ ] `needs_confirmation` ∈ {`false`, `true`, `conditional`}; for `conditional`: ≥0.85 execute · 0.60–0.85 confirm · <0.60 clarify; thresholds adjustable from the settings menu; always escalate to confirm on explicit ambiguity (e.g. three "Marías") or "always confirm" mode

### 4.7 Alias learning (spec §7 + flow 4)
- [ ] Local DB (Room/SQLite or DataStore): contact aliases, implicit favourite apps, usage times, failed-command log; learn **one alias per interaction**, never mid-call; aliases viewable/editable from the settings menu

### 4.8 Settings menu (spec §9) — Fran-only
- [ ] Hidden screen, opened by tapping the clock **5× within 3 s**; sections: aliases · launcher favourites · TTS voice/speed/pitch · incoming-call assistant toggle (§8, off by default) · confidence-threshold sliders · "always confirm" toggle · failed-command log (last 50) · "send me the failures" toggle · reset learning · version & diagnostics (model state, latencies, granted permissions)

### 4.9 Gemma 3n E2B (spec §4.4) — only where generation is genuinely needed
- [ ] Load on demand (int4, ~2 GB active); "Dame un segundo" while cold; latency 3–6 s typical; in Fase 1 it may not be needed at all — decide whether to wire it now or defer to Fase 2

### Fase 2+ (later, see spec §5)
`send_whatsapp_reply`, `set_volume`, `read_sms`, `set_reminder`, voice notes, then Fase 3 (thread summaries, video calls, translate, medication reminders) and Fase 4 (proactive alerts, "explain current screen" via Accessibility Service, routine learning).

### Cross-cutting / ops
- [ ] Manifest permissions per spec §10; request the optional ones (`READ_SMS`, `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `SYSTEM_ALERT_WINDOW`) only when the matching toggle is on
- [ ] HyperOS: document/automate adding Curro to the battery whitelist so the model's foreground service isn't killed
- [ ] `local.properties` (git-ignored): `sdk.dir`, signing config — see `CLAUDE.md` → "Environment Variables" (drop the API-URL lines; there's no backend)
- [ ] Decide model-asset delivery (download-on-first-run vs. bundled split APK / asset pack) — **do not commit ~2 GB of weights**

---

## 5. Workflow (from `CLAUDE.md`)

1. `cd ~/Projects/curro && claude` — confirm the 9 agents / 19 skills / 12 commands load.
2. `/create-prd "<spec section, e.g. 'launcher home screen — clock, mic button, favourites grid'>"` → adds a user story to `docs/PRD.md` (the story should cite the relevant `curro-spec-v1.0.md` section).
3. `/generate-brief US-XXX` → writes `docs/briefs/US-XXX-<slug>.md` (spec + tasks).
4. `/implement-feature US-XXX` → work the brief, ticking tasks.
5. Verify with the `verification-checklist` skill: **build → lint → test → run** (on the real Redmi 15 for anything voice/ML).
6. `/generate-mr-description` → PR body from the diff against `main`.

Suggested first stories: §4.0 (Gradle skeleton + theme + Hilt), §4.1 (launcher base), §4.2 (voice pipeline) — in that order, each independently demoable.

Design decisions: `brand-design` (authoritative, **rewrite it first** for accessibility) → `material-design` → `compose-patterns` → `accessibility-patterns`.

---

## 6. Open questions & risks (from spec §14 — resolve with real-device data)

- **Redmi 15 RAM variant (4 GB vs 8 GB)** — confirm before starting; on 4 GB, Gemma 3n is marginal and the architecture may need rethinking.
- **HyperOS background restrictions** may kill the model's foreground service — manual battery whitelist will be needed.
- **Native Spanish male TTS quality** is limited — Plan B is ElevenLabs (would break "fully offline"; decide if acceptable).
- **Gemma 3n latency on-device** may force deferring Fase 3 functions that depend on it.
- **Button-only trigger vs. hotword** — hotword is explicitly out of scope for the prototype; revisit only if real usage shows the button is a barrier.
- **WhatsApp notification format changes** can break parsing — robust parser + tests + "no he podido leer el mensaje" fallback.

### Closed decisions (per spec §14 — do not relitigate during implementation)
Button as the only trigger (no hotword) · name "Curro" · Android male TTS voice · everything on-device, no cloud · it's a launcher (not a normal app) · incoming-call assistant is opt-in & off by default · `set_volume` and `send_whatsapp_reply` are Fase 2, not the prototype.
