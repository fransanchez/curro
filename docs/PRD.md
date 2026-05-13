# Curro Android — Product Requirements Document

> **App**: Curro — Android launcher + on-device voice assistant for an elderly user
> **Platform**: Android (API 31+ / Android 12+), target 34+
> **Package**: `com.curro.app`
> **Created**: 2026-05-13
>
> 📄 **Source of truth: [`curro-spec-v1.0.md`](curro-spec-v1.0.md)** — every story here
> should cite the section(s) of the spec it implements. See [`BOOTSTRAP.md`](BOOTSTRAP.md)
> for repo state and the full init checklist.

---

## How this document works

User stories are grouped into **phases** that follow the spec's suggested build
order (spec §14). Each phase is a shippable, demoable increment. Persistence, system
integrations, and ML pieces are introduced just-in-time per phase, not all upfront.
(Note: Curro is **fully on-device** — there's no backend, so the "data layer" here
means local storage + Android system integrations, not REST APIs.)

**Workflow** (see `CLAUDE.md`):
1. Add a user story below with acceptance criteria — `/create-prd "<description>"`.
2. `/generate-brief US-XXX` → produces `docs/briefs/US-XXX-<slug>.md` (spec + tasks).
3. `/implement-feature US-XXX` → work through the brief, ticking tasks as you go.
4. Verify with the `verification-checklist` skill (build → lint → test → run; on the real Redmi 15 for voice/ML).

**User story format**:

> ### US-XXX: <Short title>  ·  _(spec §X.Y)_
> **As a** <role>, **I want** <capability> **so that** <benefit>.
>
> **Acceptance Criteria**:
> - [ ] …
> - [ ] …
>
> **Size**: S | M | L  ·  **Depends on**: US-YYY _(optional)_

---

## Phase 0 — Project foundation _(spec §14, BOOTSTRAP §4.0)_

> Gradle project + version catalog · Hilt + `CurroApp` · Material-3 theme
> (`CurroTheme`/`CurroColorScheme`/`CurroTypography`/`CurroShapes`/`CurroSpacing`,
> accessibility-first) · ktlint+detekt · base package layout · LiteRT/MediaPipe deps
> wired (no models loaded yet). Add the stories.

### US-001: Gradle skeleton & version catalog  ·  _(spec §14, master-plan SF-0.1)_
**As a** Curro developer, **I want** an empty-but-compiling Gradle/Kotlin/Compose Android project (with the version catalog, Hilt/KSP/ktlint/detekt plugins wired and the `com.curro.app` package skeleton stamped out) **so that** every subsequent SF has somewhere to plug in and `./gradlew assembleDebug` produces an installable APK on a fresh clone.

**Acceptance Criteria**:
- [ ] `./gradlew assembleDebug` succeeds on a fresh clone (JDK 17) and produces an installable APK that launches without crashing on a Redmi 15 / Android 15 emulator
- [ ] `gradle/libs.versions.toml` is the single source of truth for versions — `build.gradle.kts` files declare **no** inline version literals (aside from the AGP plugin bootstrap line that has to live in `settings.gradle.kts`)
- [ ] `applicationId = "com.curro.app"`, `namespace = "com.curro.app"`, `minSdk = 31`, `compileSdk = 35`, `targetSdk = 35`, `versionCode = 1`, `versionName = "0.1.0"`, `buildFeatures { compose = true }`
- [ ] Hilt plugin wired (`@HiltAndroidApp class CurroApp` declared in the manifest as a stub — full DI graph is SF-0.2), KSP wired, Compose BOM in the catalog
- [ ] `./gradlew ktlintCheck detekt` runs to completion (plugin wiring only — the lint *enforcement policy / baseline* is SF-0.3)
- [ ] `./gradlew test` discovers and passes at least one trivial JUnit 5 unit test in `app/src/test/java/com/curro/app/`
- [ ] `./gradlew connectedAndroidTest` discovers at least one trivial instrumented test in `app/src/androidTest/java/com/curro/app/` (passes when run against an emulator)
- [ ] The package skeleton from `CLAUDE.md`'s "Architecture" section exists (`domain/{model,catalog,repository,usecase}`, `data/{local,ml,voice,notification,telephony,apps,contacts,repository}`, `handler/`, `assistant/`, `service/`, `presentation/{theme,launcher,assistant,config,common,navigation}`, `di/`, `util/`) — empty directories kept alive with `.gitkeep`
- [ ] `MainActivity` has the standard `MAIN` + `LAUNCHER` intent filter only — **no `CATEGORY_HOME`** (that's SF-1.1), no runtime permissions in the manifest (those land per spec §10 with the SFs that need them)
- [ ] The placeholder home renders the text "Curro" via a stub `CurroTheme { content() }` so the app is self-contained; the real theme arrives in SF-0.4
- [ ] No model weights, no Room/MediaPipe/Coil/Firebase/PostHog dependencies, no `INTERNET` permission, no `google-services.json` in this SF — they arrive with the SF that needs each
- [ ] A `telemetryEnabled` BuildConfig flag is declared (`false` in debug, `true` in release) so SF-0.8 can branch on it later; no telemetry SDK is added yet
- [ ] The existing `.github/workflows/ci.yml` now has something to build — `Lint`, `Build debug`, `Run unit tests` steps all succeed against this skeleton

**Size**: M  ·  **Depends on**: nothing (mandatory zeroth step)

### US-002: Hilt DI graph & `HiltTestRunner`  ·  _(spec §14, master-plan SF-0.2)_
**As a** Curro developer, **I want** the Hilt dependency-injection graph wired end-to-end (the `HiltTestRunner` class declared by `app/build.gradle.kts`, a small set of placeholder Hilt modules in `di/` with the four standard component scopes, the dispatcher qualifiers that every later SF will inject, and a Hilt-injected instrumented smoke test that boots `HiltTestApplication` and asserts "Curro" renders) **so that** every later SF only needs to add bindings — never to set up DI plumbing from scratch — and instrumented tests can swap modules via `@UninstallModules` / `@BindValue` from day 1.

**Acceptance Criteria**:
- [ ] `./gradlew assembleDebug` still succeeds (no regression vs US-001 — the Hilt graph compiles end-to-end with the new modules + qualifiers)
- [ ] `./gradlew testDebugUnitTest` passes — US-001's `SmokeTest` plus any new JVM test US-002 adds (≥ 1 passing, 0 failing)
- [ ] `./gradlew connectedAndroidTest` (manual, on an emulator/device; CI doesn't run instrumented tests yet) passes a Hilt-injected smoke test that boots `HiltTestApplication`, launches `MainActivity`, and asserts the on-screen text "Curro" renders
- [ ] `HiltTestRunner` exists at `app/src/androidTest/java/com/curro/app/HiltTestRunner.kt` and matches exactly the FQN declared by US-001 in `app/build.gradle.kts`'s `defaultConfig.testInstrumentationRunner`
- [ ] `app/src/main/java/com/curro/app/di/Qualifiers.kt` declares the four short `@Qualifier` annotations `@IoDispatcher`, `@MainDispatcher`, `@DefaultDispatcher`, `@ApplicationScope` (one KDoc line each)
- [ ] `app/src/main/java/com/curro/app/di/CoroutineModule.kt` is `@InstallIn(SingletonComponent::class)` and provides `@Singleton` `CoroutineDispatcher`s for the three qualifiers + a `@Singleton` `@ApplicationScope CoroutineScope` built on `SupervisorJob() + IoDispatcher`
- [ ] `app/src/main/java/com/curro/app/di/AppModule.kt` exists as a `@InstallIn(SingletonComponent::class)` placeholder (empty `object` with a KDoc explaining it is the home for future app-scope bindings) — no premature bindings
- [ ] No real bindings beyond dispatchers + application scope land here: no Room module, no MediaPipe module, no NotificationListener / Telecom / TTS / STT module, no repository module, no handler module (each lands with its owning SF)
- [ ] No custom Hilt components / subcomponents declared — only the four standard ones (`SingletonComponent`, `ActivityRetainedComponent`, `ActivityComponent`, `ViewModelComponent`)
- [ ] `./gradlew ktlintCheck detekt` is still green on the new files (plugin-level only — SF-0.3 tightens rules)
- [ ] No new permissions, no new manifest declarations, no new third-party dependencies (Hilt-testing was wired by US-001) — US-002 is pure Kotlin + Hilt module work

**Size**: S  ·  **Depends on**: US-001

### US-003: ktlint + detekt enforcement policy  ·  _(spec §14, master-plan SF-0.3)_
**As a** Curro developer, **I want** the lint/format toolchain (`ktlint` and `detekt`) actually enforce a tuned, Curro-shaped rule set — config moved to its canonical location, the 4 detekt deprecation warnings carried since US-001 resolved, a baseline file in place, ktlint and detekt set to fail the build on violations, and the "No Double Padding" custom rule explicitly punted (in writing) to a later SF — **so that** every feature SF from SF-0.4 onwards lands on a project where Kotlin style and code-quality regressions break CI on the same day they're introduced, rather than silently accumulating against the senior-UX contract the linters will eventually police.

**Acceptance Criteria**:
- [ ] The detekt config file lives at `config/detekt/detekt.yml` (moved from `app/detekt.yml`); the `detekt { config.setFrom(...) }` block in `app/build.gradle.kts` points to the new path; the old `app/detekt.yml` is deleted (no duplicate / stale copy left in the repo)
- [ ] `./gradlew detekt` runs to completion with **zero deprecation warnings on stdout** — the 4 detekt-1.23.x config-validator deprecations carried since US-001 are resolved (each removed/renamed key documented inline in `config/detekt/detekt.yml` with a `# was: <old-key>` comment so the next reader knows why the change was made)
- [ ] `config/detekt/detekt.yml` carries Curro-specific tuning over the default export: `max_line_length = 120` aligned with `.editorconfig`; `style.WildcardImport` enabled (kotlin's wildcard imports are already banned in `.editorconfig` — detekt is the belt to ktlint's braces); `naming.FunctionNaming` still excludes `@Composable` (so detekt doesn't double-flag what ktlint already ignores via `.editorconfig`); `potential-bugs.UnsafeCallOnNullableType` **active** (this is the rule that catches `!!` — CLAUDE.md forbids it); `style.MagicNumber` left active with the default ignored-numbers set; `formatting:` section explicitly disabled (ktlint already owns formatting — detekt-formatting would be a duplicate)
- [ ] A baseline file exists at `config/detekt/baseline.xml` (generated by `./gradlew detektBaseline` after the tuning pass); on the post-US-003 codebase it is **empty or near-empty** (the scaffold is small), and exists primarily to absorb future legitimate exceptions without weakening rules
- [ ] **Build-failure policy is documented and enforced**: `./gradlew ktlintCheck` fails the build on any violation (default behaviour of the plugin — no opt-out added); `./gradlew detekt` fails the build on any rule finding *not* in the baseline (achieved via the detekt extension's `build { maxIssues: 0 }` or equivalent — `Severity.Warning` findings still don't fail by default, but actively-tuned rules at `Severity.Error` do)
- [ ] A deliberately-introduced violation proves enforcement works end-to-end: e.g. adding `val x: String? = null; val y = x!!` to a non-test file causes `./gradlew detekt` to exit non-zero with a `UnsafeCallOnNullableType` finding; adding a `private val FooBar = "x"` (capital — wrong case for a non-const property) causes `./gradlew ktlintCheck` to exit non-zero (the brief lists the exact reproducer the developer must run as part of the acceptance pass)
- [ ] `.editorconfig` is unchanged from US-001 except for any new keys the developer adds with rationale in the brief (`ktlint_function_naming_ignore_when_annotated_with = Composable` stays; `max_line_length = 120` stays; new keys require a one-line comment explaining why)
- [ ] `./gradlew ktlintCheck detekt` is green on the post-US-003 codebase (after the developer runs `./gradlew ktlintFormat` once); on a fresh clone of the post-US-003 commit, the lint task must pass **without** needing to run `ktlintFormat` (i.e. the developer commits the formatted source)
- [ ] The detekt-K2 experimental flag is explicitly addressed in writing: either turned on (with a one-line note in `app/build.gradle.kts` justifying it as stable on detekt 1.23.x as of 2026-05-13) or explicitly left off with a one-line note saying so — silent reliance on whichever default is forbidden
- [ ] The "No Double Padding" custom detekt rule (children must not add their own `Scaffold` / `TopAppBar` / `statusBarsPadding()` — `CLAUDE.md`) is **not** implemented in US-003. The brief's "Implementation Notes" section documents this punt explicitly (target: a later SF, after the FSM + assistant overlays exist and the rule has real targets to test against — most likely SF-5.x or a dedicated `SF-tooling.x`); same documented punt for any other Curro-specific custom rules (`Color(0xFF…)` raw literals, hard-coded Spanish strings)
- [ ] CI workflow (`.github/workflows/ci.yml`) is **not modified** — the existing `./gradlew ktlintCheck detekt` step is sufficient. The brief explicitly states "no CI YAML edit needed in this SF" so the developer doesn't fish for one
- [ ] Build-time impact: `./gradlew clean ktlintCheck detekt` adds < 10 s on a warm Gradle cache compared to the pre-US-003 state on the same machine (measure once, record the figure in the brief's testing notes — informational, not a hard gate)
- [ ] No new permissions, no new dependencies, no new module — US-003 is config tuning, not architecture

**Size**: S  ·  **Depends on**: US-001, US-002

---

## Phase 1 — Launcher base _(spec §11, §14 step 1)_

> `MainActivity` as `CATEGORY_HOME` launcher · "set as default launcher" flow ·
> home screen: big clock+date, large mic button (≥40% screen, haptic), 4–6 large app
> tiles, "Más apps" screen. **No assistant yet.** Validate with the real user that
> this replaces the stock launcher.

_Stories TBD._

---

## Phase 2 — Voice pipeline _(spec §4.2, §4.6, §14 step 2)_

> `RECORD_AUDIO` · native offline `SpeechRecognizer` (Spanish) with live transcription ·
> native `TextToSpeech` (Spanish, slowed ~10–15%) · the capture→response loop, no
> decision model yet. Confirm it works on-device.

_Stories TBD._

---

## Phase 3 — FunctionGemma decision layer _(spec §4.3, §5 Fase 1 catalogue, §14 step 3)_

> Load FunctionGemma 270M (int8) via MediaPipe LLM Inference · keep warm in a
> foreground service (`POST_NOTIFICATIONS`), <500 ms text→JSON · prompt = Fase-1
> function catalogue + minimal context · validate output against the catalogue JSON
> schema · show returned JSON on screen for debugging · friendly fallback on invalid
> output (flow 7), no auto-retry.

_Stories TBD._

---

## Phase 4 — Fase 1 handlers _(spec §5 Fase 1, §14 step 4 — in order)_

> Low-risk first: `tell_time` · `open_app` (`QUERY_ALL_PACKAGES`) · `calculate` ·
> `help`. Then sensitive permissions: `read_last_whatsapp` · `read_all_unread_whatsapp`
> (`NotificationListenerService`, robust parser + tests + fallback) · `call_contact`
> (`READ_CONTACTS` + `CALL_PHONE`, contact/alias resolution).

_One story per handler — TBD._

---

## Phase 5 — State machine & interruption _(spec §6, §14 step 5)_

> `idle`/`listening`/`processing`/`confirming`/`executing`/`error_recovery` · new
> button press interrupts current state → `listening` · 10 s no-answer timeout in
> `confirming` · consecutive-STT-failure policy (1st/2nd/3rd message, then give up).

_Stories TBD._

---

## Phase 6 — Confidence-graded confirmation _(spec §4.3, §14 step 6)_

> `needs_confirmation` ∈ {`false`,`true`,`conditional`} · `conditional`: ≥0.85
> execute / 0.60–0.85 confirm / <0.60 clarify · thresholds adjustable from settings ·
> always escalate to confirm on explicit ambiguity or "always confirm" mode.

_Stories TBD._

---

## Phase 7 — Alias learning & local persistence _(spec §7, flow 4, §14 step 7)_

> Local DB (Room/SQLite or DataStore): contact aliases, implicit favourite apps,
> usage times, failed-command log · learn **one alias per interaction**, never
> mid-call · aliases viewable/editable from the settings menu.

_Stories TBD._

---

## Phase 8 — Settings menu (Fran-only) _(spec §9, §14 step 8)_

> Hidden screen opened by tapping the clock 5× within 3 s · aliases · launcher
> favourites · TTS voice/speed/pitch · incoming-call assistant toggle (§8, off by
> default) · confidence-threshold sliders · "always confirm" toggle · failed-command
> log (last 50) · "send me the failures" toggle · reset learning · version & diagnostics.

_Stories TBD._

---

## Phase 9 — Gemma 3n content layer _(spec §4.4, §14 step 9)_

> Load Gemma 3n E2B (int4) on demand · "Dame un segundo" while cold · 3–6 s typical ·
> may not be needed in Fase 1 — decide whether to wire now or defer to Fase 2.

_Stories TBD._

---

## Later — Fase 2+ _(spec §5)_

`send_whatsapp_reply`, `set_volume`, `read_sms`, `set_reminder`, voice notes → Fase 3
(thread summaries, video calls, translate, medication reminders) → Fase 4 (proactive
alerts, "explain current screen" via Accessibility Service, routine learning, incoming
photo description). Not for the prototype.
