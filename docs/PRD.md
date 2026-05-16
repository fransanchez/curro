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

### US-004: `CurroTheme` scaffold + senior-first tokens  ·  _(spec §11, master-plan SF-0.4)_
**As a** Curro developer, **I want** the no-op `CurroTheme` stub replaced with a real Material-3 theme wired to Curro's semantic tokens (`CurroColorScheme` light + dark, `CurroTypography` with a body-as-headline scale, `CurroShapes`, `CurroSpacing`), the senior-first dimension contract codified as named constants (`Dimens.MinTapTarget = 96.dp`, `Dimens.MicButtonMinHeightFraction = 0.40f`), `dynamicColor` hard-disabled, the linter teach-around so the theme module is the *one* place raw `Color(0xFF…)` / `.sp` / `.dp` literals are tolerated, and the first one or two shared big components (`BigPrimaryButton` mandatory; `BigCard` strongly preferred) under `presentation/common/` ready for Phase 1 to consume — **all values placeholder, but contrast-floor-compliant**, so US-005 (SF-0.7) can swap the real brand palette/type/spacing in without touching a single composable consumer **so that** every UI SF from SF-0.5 onwards lands on a project where semantic theme tokens (`MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `CurroSpacing.*`, `CurroShapes.*`, `Dimens.*`) are the *only* permitted vocabulary, the senior-first contract (≥ 96 dp tap targets, body-as-headline text, system fontScale respected and amplified, ≥ 4.5:1 floor / ≥ 7:1 body where the palette allows, predictable dark-mode flip, no fussy animation, audio + visual together) is mechanically expressed in code rather than aspirationally documented, and brand fill-in (US-005) is a values-only swap.

**Acceptance Criteria**:
- [ ] `./gradlew assembleDebug` succeeds; `MainActivity` still renders the text "Curro" — call site unchanged, only the theme's appearance has changed
- [ ] `./gradlew ktlintCheck detekt` is green; `config/detekt/detekt.yml` carries an explicit, commented exclude for `MagicNumber` (and any other dp/sp/hex-color-touching rule that fires) scoped to `**/presentation/theme/**` so the theme module — and **only** the theme module — may use raw `Color(0xFF…)` / `.sp` / `.dp` literals; every other path in `app/src/main/` still triggers the rule on a raw literal
- [ ] `CurroTheme` provides `LightColors: ColorScheme` and `DarkColors: ColorScheme`; `adb shell cmd uimode night yes` and `adb shell cmd uimode night no` flip the rendered theme correctly without any composable change; **both schemes satisfy ≥ 4.5:1 contrast for `onSurface`-on-`surface` body pairings** (verified by the developer against the chosen placeholder hexes and recorded in the PR description with the computed ratio per pairing)
- [ ] **`dynamicColor` is hard-disabled** — `CurroTheme` does not accept a `dynamicColor` parameter, does not call `dynamicLightColorScheme` / `dynamicDarkColorScheme`, and the KDoc on `CurroTheme.kt` explicitly states "Dynamic color disabled by design — predictability ('feels the same every day') and the senior contrast floor outrank user wallpaper personalisation"
- [ ] `CurroTypography: Typography` is a Material-3 `Typography` instance with **every M3 role explicitly assigned** (display/headline/title/body/label × Large/Medium/Small); the size floor for each role is documented in `Type.kt`'s KDoc (e.g. `bodyLarge ≥ 20sp`, `bodyMedium ≥ 18sp`, `labelLarge ≥ 18sp`, `displayLarge ≥ 64sp`) and the placeholder values picked here are at or above each floor; concrete brand values arrive in US-005 (SF-0.7)
- [ ] `CurroSpacing` (`none/xs/s/m/l/xl/xxl`) and `CurroShapes` (M3 small/medium/large/extraSmall/extraLarge) are accessible from every composable through Curro-idiomatic syntax (the architect picks: plain top-level `object CurroSpacing` vs `CompositionLocal`-backed access; either is acceptable, but the choice is committed in the file with a one-line rationale comment)
- [ ] `Dimens.kt` declares **at minimum** `MinTapTarget = 96.dp`, `MicButtonMinHeightFraction = 0.40f`, and `BigButtonHeight = 96.dp`, with KDoc on each citing the senior-first rule it implements; every shared big component shipped in this SF references `Dimens.MinTapTarget` (no inline `96.dp` literal) and the eventual launcher `MicButton` SF will reference `Dimens.MicButtonMinHeightFraction` (US-004 just provides the slot)
- [ ] `BigPrimaryButton` exists at `app/src/main/java/com/curro/app/presentation/common/BigPrimaryButton.kt` with signature `@Composable fun BigPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentDescription: String? = null)`, enforces `Modifier.heightIn(min = Dimens.MinTapTarget)`, fires `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` on tap, uses `MaterialTheme.colorScheme.primary` / `onPrimary` for container/content, and renders its label via a Curro-large typography role
- [ ] `BigCard` exists at `app/src/main/java/com/curro/app/presentation/common/BigCard.kt` with signature `@Composable fun BigCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`, uses `CurroShapes.medium` (or `.large` — pick one and KDoc the choice), generous internal `CurroSpacing` padding, and when `onClick != null` exposes a clickable surface ≥ `Dimens.MinTapTarget` tall with the same haptic feedback
- [ ] Every new `@Composable` in this SF (`CurroTheme` smoke preview, `BigPrimaryButton`, `BigCard`) ships a `@Preview` set including **light**, **dark**, **`fontScale = 1.5f`**, and **`fontScale = 2.0f`** variants; each preview survives without clipping or layout collapse; the `fontScale = 2.0f` preview of `BigPrimaryButton` visibly renders ≥ 96 dp tall (the developer eyeballs this in Android Studio and confirms in the PR description)
- [ ] The `TODO(SF-0.4)` comment in the old `CurroTheme.kt` stub is removed; the stub `@Composable fun CurroTheme(content: @Composable () -> Unit) { MaterialTheme(content = content) }` body is gone; `MainActivity.kt` is untouched (compatible call site `CurroTheme { Surface { Text(...) } }`)
- [ ] `app/src/main/res/values/themes.xml`'s `Theme.Curro` is reviewed: its background colour is set to (or kept compatible with) `LightColors.background`'s placeholder so a cold launch does not flash a contrasting splash window before the Compose theme paints; if a mismatch exists, document it in the brief's Performance Considerations as accepted technical debt for US-005 to close
- [ ] **Out of scope, explicit non-deliveries** (so the developer doesn't drift): no real Curro brand palette (US-005); no real type-scale values (US-005); no `BigYesNoRow` / `BigListRow` (deferred to SF-0.5 unless the architect explicitly pulls them forward — the brief documents the choice); no launcher home or assistant overlays (Phase 1+); no `SettingsRepository`-driven extra font-scale boost (SF-0.5 / SF-8.x); no logo / app-icon assets (SF-0.7); no No-Double-Padding custom detekt rule (already punted in US-003)

**Size**: M  ·  **Depends on**: US-001, US-002, US-003

### US-005: Brand-design fill-in — palette, type, spacing, shapes, canonical Spanish copy  ·  _(master-plan SF-0.7, spec §2/§3/§11)_
**As a** Curro developer, **I want** the `brand-design` skill's `TODO`-laden template replaced with the real, considered Curro brand (warm Andalusian palette tuned to ≥ 7:1 body contrast in both light and dark, the per-role typography numbers locked at or above the US-004 senior-first floor, the 7-step lowercase spacing scale documented to match the code US-004 already shipped, the rounded-corner radii pinned, and the `ListeningTint` extension token defined) **plus** the **canonical Spanish COPY table** (every line from spec §2 and §6, with stable IDs, in Curro's voice — warm, Andalusian, colloquial, never servile) landed as Spanish `<string>` entries in `app/src/main/res/values/strings.xml` (Spanish IS the default locale — no `values-es/` directory), with the placeholder hexes / placeholder type values in `presentation/theme/Color.kt` and `presentation/theme/Type.kt` swapped wholesale to the real values and the matching `values/colors.xml` + `values-night/colors.xml` window-background hexes brought into sync — **so that** the device-visible UI shipped from Phase 1 onward lands on a real, deliberately-chosen brand rather than the placeholder cool-grey-on-white look US-004 was forced into, the FSM SFs in Phase 5/6 have a single source of truth for every Spanish phrase Curro speaks (no string lives twice; no string lives in code), and the `brand-design` skill itself stops being a TODO template and becomes the AUTHORITATIVE brand + voice spec all other design skills defer to.

**Acceptance Criteria**:
- [ ] Every `// PLACEHOLDER (US-005)` annotation in `app/src/main/java/com/curro/app/presentation/theme/{Color,Type,Shape,CurroSpacing}.kt` is removed; the values below them are the real brand. No composable consumer of these files is touched (US-005 is a values swap, not a structure change).
- [ ] **Light contrast contract verified and recorded in the PR description**: `LightColors.onPrimary` on `LightColors.primary` ≥ 7:1; `LightColors.onSurface` on `LightColors.surface` ≥ 7:1; `LightColors.onBackground` on `LightColors.background` ≥ 7:1; `LightColors.primary` on `LightColors.surface` ≥ 3:1 (UI floor — the SÍ-button-on-confirmation-overlay pairing); `LightColors.error` on `LightColors.surface` ≥ 4.5:1 (error text is body-sized in the recovery overlay).
- [ ] **Dark contrast contract verified and recorded** with the same role pairings; the US-004-flagged `error / surface ~3.3:1` is fixed (dark `error` chosen so it clears ≥ 7:1 on `surface`, since dark-mode error is rendered as text not as a fill).
- [ ] **Senior-first floor preserved (US-004 contract, A1)**: `CurroTypography.bodyLarge.fontSize.value >= 20f`, `bodyMedium >= 18f`, `labelLarge >= 18f`, `titleLarge >= 22f`, `headlineMedium >= 28f`, `displayLarge >= 64f`. Where US-005 raises a value (e.g. `displayLarge` 64 → 72 for the clock), the new value is documented in `Type.kt`'s KDoc with a one-line rationale.
- [ ] **`CurroSpacing` left exactly as US-004 shipped it** — the 7-step lowercase scale (`none = 0.dp`, `xs = 4.dp`, `s = 8.dp`, `m = 16.dp`, `l = 24.dp`, `xl = 32.dp`, `xxl = 48.dp`). The `brand-design` skill's spacing section is updated **to match this code** (the skill currently documents a 4-step PascalCase scale that no longer reflects reality — the skill is wrong, the code is right; this AC fixes the skill).
- [ ] **`Dimens` left untouched** — `MinTapTarget = 96.dp`, `MicButtonMinHeightFraction = 0.40f`, `BigButtonHeight = 96.dp`, `BigRowHeight = 96.dp`, `LargeIconSize = 48.dp`, `CardElevation = 2.dp`. US-005 is not the place to renegotiate the senior-first dimension contract (any change requires architect escalation).
- [ ] `CurroShapes` radii replaced with the real Curro values (warm/friendly bias — slightly bumped from M3 defaults); the values land in `Shape.kt` without touching `CurroTheme`'s wiring.
- [ ] **`ListeningTint` extension token** is defined (the "light blue" tint applied to the listening overlay per spec §11) — exposed somewhere alongside `LightColors` / `DarkColors` (the architect's call: a `CurroExtendedColors` object, a `CompositionLocal`, or a pair of named `Color` constants — the brief documents one approach; the developer applies it). The token is not part of M3 `ColorScheme` (no slot fits); it's a deliberate Curro extension.
- [ ] **`app/src/main/res/values/colors.xml` updated**: `curro_window_background` becomes the real Light `background` hex. **`app/src/main/res/values-night/colors.xml` updated**: same, for the Dark `background` hex. The "keep in sync" comment already in the XML stays; the comment's "US-005 (SF-0.7) updates this value" line is now history (replace the wording: "Synced with LightColors/DarkColors.background — keep in sync").
- [ ] **`app/src/main/res/values/strings.xml` carries every COPY ID** from the canonical table in the brief, with the Spanish string verbatim from spec §6 where the spec provides one (those are "closed decisions" per spec §14) and freshly-written in Curro's voice where the spec doesn't — every freshly-written line is flagged in the brief's COPY table with the marker `(NEW)` so the user can review them before commit. Naming convention: `<string name="copy_<lowercase_snake_id>">…</string>` (e.g. `copy_listening_prompt`, `copy_confirm_call`). Parameterised lines use Android string positional args: `<string name="copy_calling">Llamando a %1$s.</string>`.
- [ ] **No `values-es/` directory created** — Spanish IS the default locale for Curro. The brief documents this explicitly so the developer doesn't add a redundant `values-es/`.
- [ ] **No composable references the new COPY IDs in this SF** — the strings are *defined* here; the wiring at call sites lands with Phase 1/5/6 features that need each phrase. (US-005 is the lock; consumption is later.)
- [ ] `.claude/skills/brand-design/SKILL.md` has **zero `TODO` markers left** after this SF: the Color Palette section is filled with the real hexes (and the contrast ratios per pairing inline), the Typography table has concrete sp + FontWeight per role, the Spacing System section is rewritten to document the 7-step lowercase code reality (replacing the 4-step PascalCase template), the Corner Radius / Shapes section has the real dp values, the Logo & Iconography section documents the prototype reality ("text-only 'Curro' wordmark for now; bitmap launcher icon shipped with US-001 stands; SVG logo design is a future SF — out of scope for the prototype"), the Image Aspect Ratios section pins "1:1 only — contact photos circular via `CurroShapes`, app icons square with `CurroShapes.small` radius", and the Component Patterns section is aligned with US-004's shipped `BigPrimaryButton` + `BigCard` signatures.
- [ ] `.claude/skills/brand-design/SKILL.md` carries the **same canonical COPY table** as the brief and `strings.xml` — three copies of the truth in three different formats (one for AI agent reference, one for human review, one for code consumption); they must be triple-synced at commit time. The skill's existing partial COPY table (currently lines 94–110) is *replaced* with the full canonical version, not just appended-to.
- [ ] `./gradlew assembleDebug` succeeds with the new values; `./gradlew ktlintCheck detekt` passes — the `**/presentation/theme/**` exclude US-003 carved out for raw `Color(0xFF…)` / `.sp` / `.dp` literals stays as-is (no broadening). No new resource lints fire on `strings.xml` (no untranslated-locale warning — there is no second locale).
- [ ] **Eyeball check on the running app** (manual; the developer captures a screenshot in the PR): the smoke composable in `MainActivity` now reads as Curro (warm cream-on-terracotta or vice versa, depending on system theme) — not the cool-grey-on-white US-004 placeholder. Light and dark both look intentional.
- [ ] **Out of scope, explicit non-deliveries**: no real logo / wordmark asset design (a future SF — the prototype keeps US-001's bitmap launcher icon + a text-only "Curro" wordmark); no bundled font (system default stays; a custom font is a future SF); no composable consumer of the COPY table is wired (that lands per Phase 1/5/6); no rework of `BigPrimaryButton` / `BigCard` shipped in US-004; no `Dimens.kt` changes; no theme-toggle UI (the dark/light branch stays system-driven, per US-004 A6).

**Size**: M  ·  **Depends on**: US-004

### US-006: `BigYesNoRow` + `BigListRow` — the two punted shared big components  ·  _(master-plan SF-0.5 (rest), spec §11)_
**As a** Curro developer, **I want** the two shared big components US-004 deliberately deferred (`BigYesNoRow` for the SÍ/NO confirmation overlay rendered with `primary`-terracotta + `secondary`-olive — never `error`-red — and `BigListRow` for the contact picker / alias-learning list / message cards / config-menu rows, with leading-icon + title + optional-subtitle + trailing slots) shipped in `presentation/common/` alongside the existing `BigPrimaryButton` and `BigCard`, each respecting the senior-first contract (≥ 96 dp interactive surface via `Dimens.MinTapTarget` / `Dimens.BigButtonHeight` / `Dimens.BigRowHeight`, `HapticFeedbackType.LongPress` on every clickable surface per US-004 A10, semantic-tokens-only access — no raw `Color(0xFF…)` / `.sp` / `.dp` literals beyond what is forwarded from `Dimens` / `CurroSpacing`), each with the four canonical previews (light, dark, `fontScale = 1.5f`, `fontScale = 2.0f`) on a 412 dp wide frame, **plus** the two new resource strings `copy_yes = "SÍ"` and `copy_no = "NO"` appended to `strings.xml` and to `brand-design`'s canonical COPY table — **all without touching any consuming surface** (the confirmation overlay, contact picker, message cards screen, and config menu all land in Phase 1+) — **so that** Phase 1+ surfaces from SF-1.x onwards land on a complete set of shared big bricks (the four required by `launcher-ui` rule 4 — `BigPrimaryButton`, `BigCard`, `BigYesNoRow`, `BigListRow`), sizing/contrast/haptic consistency is locked across every interactive surface Curro will ever ship, and the brand decision US-005 settled (`primary` = SÍ-terracota, `secondary` = NO-olivo, `error` reserved for genuine failures only) is mechanically expressed in code rather than aspirationally documented.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt` and `app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` exist
- [ ] `BigYesNoRow` signature: `@Composable fun BigYesNoRow(onYes: () -> Unit, onNo: () -> Unit, modifier: Modifier = Modifier, yesText: String = stringResource(R.string.copy_yes), noText: String = stringResource(R.string.copy_no), enabled: Boolean = true)` — two filled `Button`s side-by-side in a `Row` with `Arrangement.spacedBy(CurroSpacing.l)`, each `Modifier.weight(1f).heightIn(min = Dimens.BigButtonHeight)`, SÍ uses `MaterialTheme.colorScheme.primary` + `onPrimary`, NO uses `MaterialTheme.colorScheme.secondary` + `onSecondary` (verified by grep — **NOT** `MaterialTheme.colorScheme.error`), each fires `HapticFeedbackType.LongPress` on press, label rendered at `MaterialTheme.typography.titleLarge`
- [ ] `BigListRow` signature: `@Composable fun BigListRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, contentDescription: String? = null, enabled: Boolean = true)` — clickable `Row` with `Modifier.heightIn(min = Dimens.BigRowHeight).clickable { haptic; onClick() }`, leading slot rendered in a `Box(Modifier.size(56.dp))` if non-null, title in `MaterialTheme.typography.titleLarge` + `MaterialTheme.colorScheme.onSurface`, subtitle (if non-null) in `MaterialTheme.typography.bodyMedium` + `MaterialTheme.colorScheme.onSurfaceVariant`, trailing slot in a `Box(Modifier.size(48.dp))` if non-null, background `Color.Transparent`, horizontal padding `CurroSpacing.m`, vertical padding `CurroSpacing.s`, `contentDescription` applied via `Modifier.semantics` when non-null
- [ ] `strings.xml` adds `<string name="copy_yes">SÍ</string>` and `<string name="copy_no">NO</string>` with provenance comments referencing US-006 / the BigYesNoRow consumer
- [ ] `.claude/skills/brand-design/SKILL.md`'s "Confirmation (Phase 6)" COPY table sub-section adds two rows: `copy_yes | SÍ | (NEW) US-006 — BigYesNoRow default label` and `copy_no | NO | (NEW) US-006 — BigYesNoRow default label` (table-row append only; the rest of the skill stays untouched)
- [ ] Both files ship four `@Preview` variants each (`light`, `dark` with `UI_MODE_NIGHT_YES`, `fontScale = 1.5f`, `fontScale = 2.0f`); every preview at `widthDp = 412` renders without clipping; the `BigListRow` previews include representative content (a contact name + phone-number subtitle, an app name with leading icon and no subtitle, a config-menu label with a current-value subtitle and a trailing chevron)
- [ ] `Dimens.kt` is **byte-identical** to its US-005 state (no new entry); the 56 dp leading-slot size and 48 dp trailing-slot size live as `private val`s in `BigListRow.kt` — `git diff app/src/main/java/com/curro/app/presentation/theme/Dimens.kt` returns no output
- [ ] `MainActivity.kt`, `CurroTheme.kt`, `CurroSpacing.kt` / `Spacing.kt`, `Type.kt`, `Shape.kt`, `Color.kt`, `BigPrimaryButton.kt`, `BigCard.kt` are all **byte-identical** to their US-005 state — `git diff` against each returns no output (US-005 + US-004 invariants preserved)
- [ ] `grep -rn 'Color(0xFF\|\.sp\|MaterialTheme.colorScheme.error' app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` returns zero matches — no raw colour literals, no raw sp, and (critically) no `error` colour anywhere on the NO button
- [ ] `./gradlew assembleDebug ktlintCheck detekt testDebugUnitTest` are all green; the `MagicNumber` detekt exclude on `**/presentation/theme/**` is unchanged from US-004 / US-005; the two 56/48 `.dp` literals in `BigListRow.kt` are caught by detekt **and** explicitly justified inline (KDoc rationale on the `private val`s — the only inline `.dp` in `presentation/common/` permitted by the lint posture, mirroring the US-004 / A11 `Dimens.CardElevation` precedent for `BigCard`). If detekt flags them, the developer either promotes to `Dimens` (signalling cross-SF reuse) or adds a one-line `@Suppress("MagicNumber")` with a KDoc rationale — the brief documents the chosen path
- [ ] No new permissions, no new Hilt module, no new manifest line, no new Gradle dependency, no FSM code, no LLM code, no real consumer of either composable wired (the consumers — confirmation overlay, contact picker, message cards, config menu — all land in Phase 1+)
- [ ] **Out of scope, explicit non-deliveries**: no `BigIconButton`, no `BigSwitch`, no `BigSlider`, no `AppTile`, no `MessageCard` (these are specialised consumers, deferred to their owning SFs); no theme-token edits (US-005 is canonical); no custom detekt rule for raw colour/sp/dp literals (still parked alongside the No-Double-Padding rule); no real consumer of `copy_yes` / `copy_no` (lands with Phase 5/6 confirmation overlay).

**Size**: S  ·  **Depends on**: US-004, US-005

### US-007: `CurroNavHost` shell + `MainActivity` launcher Activity  ·  _(master-plan SF-0.6, spec §11 launcher UX)_
**As a** Curro developer, **I want** the navigation shell that every Phase 1+ UI surface plugs into landed — a single `CurroNavHost` that is one `Scaffold` whose `innerPadding` wraps a two-route `NavHost` (`launcher` start destination + `config` Fran-only stub), two placeholder screens (`LauncherPlaceholderScreen` rendering "Curro listo" + a debug `TextButton` that opens the config route so the shell is actually verifiable in Phase 0, and `ConfigMenuPlaceholderScreen` showing "Menú de Fran — vacío en Phase 0" with a senior-first ≥ 96 dp back chevron at TopStart per the `navigation-patterns` skill), `MainActivity` upgraded with `singleTask` / `clearTaskOnLaunch` / `stateNotNeeded` / `screenOrientation="portrait"` / `windowSoftInputMode="adjustResize"` and its `setContent { }` body switched from the US-001 `Surface { Text(stringResource(R.string.app_name)) }` invariant to `CurroNavHost()`, and Navigation Compose activated from its US-001-reserved slot in the version catalog — **but with `CATEGORY_HOME` deliberately NOT added to the manifest yet** (SF-1.1 ships that with `RoleManager` + the "Hazme tu pantalla de inicio" CTA; landing it in SF-0.6 would hijack the dev device's actual launcher before Curro has a real launcher home to offer, breaking the dev's ability to use their phone normally between commits) **so that** every Phase 1+ UI SF lands on a project where the No-Double-Padding rule (`CLAUDE.md` "Screen Layout" + `navigation-patterns` rule 1) is mechanically expressed by `CurroNavHost`'s single `Scaffold` rather than aspirationally documented, the launcher Activity contract that `launcher-app` will need in SF-1.1 (`singleTask` + portrait + edge-to-edge — everything *except* the HOME intent filter and `RoleManager` flow) is already in place, the assistant overlays Phase 5+ will render are confirmed to be state-driven (a `StateFlow<AssistantState>` on top of the launcher route, not new nav routes) by virtue of the nav graph deliberately having no overlay routes, and SF-1.1 reduces to "add the `<category>` line + `RoleManager` flow".

**Acceptance Criteria**:
- [ ] `./gradlew assembleDebug` succeeds on a fresh clone; the produced APK installs and launches on the connected Pixel_10_Pro emulator without crashing; the placeholder screen ("Curro listo") renders; tapping the debug `TextButton` navigates to the config stub; tapping the back chevron returns to the placeholder (verifiable manually after `./gradlew installDebug` + launch + adb).
- [ ] `./gradlew ktlintCheck detekt testDebugUnitTest` are all green; the existing US-001 `SmokeTest` and any subsequent regression guards still pass; no new detekt deprecation warnings; the `MagicNumber` exclude on `**/presentation/theme/**` is unchanged (no widening) — US-007 ships no raw `.dp` / `.sp` / `Color(0xFF…)` literals outside `presentation/theme/`.
- [ ] **`MainActivity` manifest attributes** are exactly: `android:exported="true"`, `android:launchMode="singleTask"`, `android:clearTaskOnLaunch="true"`, `android:stateNotNeeded="true"`, `android:screenOrientation="portrait"`, `android:windowSoftInputMode="adjustResize"`. The intent-filter contains only `MAIN + LAUNCHER` (no `CATEGORY_HOME`, no `CATEGORY_DEFAULT`). Verifiable via `grep -E 'launchMode|clearTaskOnLaunch|stateNotNeeded|screenOrientation|windowSoftInputMode' app/src/main/AndroidManifest.xml` returning 5 matches AND `grep 'CATEGORY_HOME\|category.HOME' app/src/main/AndroidManifest.xml` returning 0 matches.
- [ ] **`MainActivity.kt` body** is `@AndroidEntryPoint` + `enableEdgeToEdge()` + `setContent { CurroTheme { CurroNavHost() } }`. No `Surface` wrapper at the Activity level (the `Scaffold` inside `CurroNavHost` paints the background); no `Text(stringResource(R.string.app_name))` (US-001 invariant lifts in SF-0.6 — this is the first SF since US-001 allowed to change `MainActivity`).
- [ ] **`CurroNavHost.kt`** exists at `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`. It is a single `Scaffold(modifier = modifier.fillMaxSize())` whose `innerPadding` is applied via `Modifier.padding(innerPadding)` on the `NavHost` (NOT on either child screen). The `NavHost` has exactly two `composable { }` blocks — `CurroRoute.Launcher` (start) and `CurroRoute.ConfigMenu`. The route registry is an `enum class CurroRoute(val value: String)` with two entries (`Launcher("launcher")`, `ConfigMenu("config")`) — chosen over `sealed interface` for simplicity at this size.
- [ ] **`LauncherPlaceholderScreen.kt`** exists at `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`. It is a `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)` containing a centred `Column` with the title `Text` (`R.string.launcher_placeholder_title` / `MaterialTheme.typography.displayMedium` / `MaterialTheme.colorScheme.onSurface`), a `Spacer(Modifier.height(CurroSpacing.xxl))`, and a `TextButton` rendering `R.string.launcher_placeholder_open_config_debug` at `MaterialTheme.typography.labelLarge` that fires `onOpenConfig()`. **No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()`** — verifiable via `grep -E 'Scaffold|TopAppBar|statusBarsPadding' app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt` returning 0 matches.
- [ ] **`ConfigMenuPlaceholderScreen.kt`** exists at `app/src/main/java/com/curro/app/presentation/config/ConfigMenuPlaceholderScreen.kt`. It is a `Box(Modifier.fillMaxSize())` containing (a) a centred title `Text` (`R.string.config_placeholder_title` / `MaterialTheme.typography.titleLarge`) and (b) an overlay `IconButton` at `Alignment.TopStart` with `Modifier.padding(start = CurroSpacing.s, top = CurroSpacing.s).size(Dimens.MinTapTarget)` containing an `Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_back), modifier = Modifier.size(Dimens.LargeIconSize))` firing `onBack()`. **No `Scaffold`, no `TopAppBar`, no `statusBarsPadding()`** — verifiable via the same grep returning 0 matches.
- [ ] **Four new string resources** in `strings.xml` under a new "Phase-0 nav shell (US-007)" sub-block: `launcher_placeholder_title = "Curro listo"`, `launcher_placeholder_open_config_debug = "Ajustes (depuración)"`, `config_placeholder_title = "Menú de Fran — vacío en Phase 0"`, `cd_back = "Volver"`. The first three are Phase-0 debug-only (vanish in Phase 1 — SF-1.1 / SF-8.1 ship the real launcher home and config menu) and the `<!-- comment -->` over each documents the planned retirement SF; `cd_back` is the only one that survives Phase 1 (every back chevron uses it as its `contentDescription`). The `cd_*` prefix formalises the "content description" naming convention.
- [ ] **None of the four new strings are added to `brand-design`'s canonical COPY table** — they're not COPY-table strings (they're labels/debug-affordance/content-description, not Curro's spoken voice). The brief documents this distinction explicitly so a future COPY review knows to skip them; `cd_back` will appear in every chevron from US-007 onward but doesn't belong in the spec §6 voice catalogue.
- [ ] **Navigation Compose is activated** from its US-001-reserved slot in `gradle/libs.versions.toml`. A new `androidx-navigation-compose = "2.8.5"` (or the current latest stable on 2026-05-14 — pinned to the catalog, not inline) version entry exists alongside the active `[versions]` block; a new `androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "..." }` library entry in `[libraries]`; and `implementation(libs.androidx.navigation.compose)` in `app/build.gradle.kts`'s `dependencies` block under "Compose". No inline version string in `app/build.gradle.kts`.
- [ ] **`MainActivity.kt`, `AndroidManifest.xml`, and the version catalog are the only files in `app/src/main/` modified** beyond the four new files (`CurroNavHost.kt`, `LauncherPlaceholderScreen.kt`, `ConfigMenuPlaceholderScreen.kt`, `strings.xml`). `git diff` against `CurroTheme.kt`, `Color.kt`, `Type.kt`, `Shape.kt`, `Dimens.kt`, `CurroSpacing.kt`, `BigPrimaryButton.kt`, `BigCard.kt`, `BigYesNoRow.kt`, `BigListRow.kt`, `themes.xml`, `colors.xml`, `values-night/colors.xml` returns **no output** — US-004 / US-005 / US-006 tokens are byte-identical.
- [ ] **No FSM-reset-on-`onNewIntent`, no `RoleManager` flow, no `requestIgnoreBatteryOptimizations`** — `singleTask` + `clearTaskOnLaunch = "true"` + `stateNotNeeded = "true"` alone handle the "HOME returns to a clean launcher" story for Phase 0 (the FSM doesn't exist yet — Phase 5 adds it; SF-1.1 wires the role-request flow; HyperOS-specific battery whitelisting is documented in `launcher-app` and surfaced via SF-8.x diagnostics, not here). The brief documents these deliberate non-deliveries.
- [ ] **No new permissions, no new Hilt module, no new BuildConfig flag, no telemetry, no theme edits, no new shared components beyond the two placeholder screens + `CurroNavHost` itself.** US-007 is pure nav-shell + Activity-attribute work plus 4 string resources plus a version-catalog activation.
- [ ] **Acceptance bar (from master-plan SF-0.6)**: the app installs, opens the placeholder ("Curro listo"), navigates to the stub config menu via the debug `TextButton`, and back via the chevron. Verified manually on the Pixel_10_Pro emulator after `./gradlew installDebug`.
- [ ] **Out of scope, explicit non-deliveries**: no `CATEGORY_HOME` intent filter (SF-1.1); no `RoleManager.ROLE_HOME` / "Hazme tu pantalla de inicio" CTA (SF-1.1); no real launcher home — clock, mic button, app grid, "Más apps" (SF-1.2–1.5); no real config menu sections — aliases / favourites / TTS settings / confidence sliders / failed-commands log / diagnostics (SF-8.x); no clock-five-tap gesture wiring (SF-1.6); no FSM-reset-on-`onNewIntent` (Phase 5 — the FSM doesn't exist yet); no `BackHandler` on the placeholders (the system back action already pops `config` → `launcher` via Navigation Compose's default; the chevron is the visible affordance); no deep links / `navDeepLink` / `VIEW` filters (`navigation-patterns` rule 4); no bottom nav, no tabs, no `NavigationRail`, no `NavigationSuiteScaffold` / `ListDetailPaneScaffold` (`navigation-patterns` rule 4 — single fixed phone, portrait); no `MoreApps` route (SF-1.5 introduces it); no instrumented test of the nav graph (a manual flow on the emulator is sufficient at this scaffold stage — formal UI tests land in SF-0.5-followup / SF-1.x).

**Size**: S  ·  **Depends on**: US-001, US-004, US-006

### US-008: Telemetry plumbing — Firebase + PostHog, `INTERNET` in release only, PII guardrail  ·  _(master-plan SF-0.8, spec §12 → v1.1)_
**As a** Curro developer, **I want** the telemetry stack landed end-to-end (Firebase Crashlytics + Analytics + PostHog SDKs activated from their US-001-reserved slots in `libs.versions.toml`, the `TELEMETRY_ENABLED` BuildConfig flag — `false` in debug, `true` in release — wired to a `TelemetryInitializer` called from `CurroApp.onCreate()`, the `INTERNET` permission **gated to a brand-new `app/src/release/AndroidManifest.xml` overlay** so the debug APK stays network-permission-less by construction, a `domain/repository/TelemetrySink` interface with two production implementations — `FirebaseAndPostHogSink` for release / `NoopTelemetrySink` for debug — bound through a `di/TelemetryModule`, **and a `TelemetryGuardrail` validator + CI-enforced fixture test that fails the build the moment any event property contains a transcript, a contact name, a phone number, an email, or a forbidden key (`transcript`, `message`, `body`, `content`, `contact_name`, `phone`, `phone_number`, `name`, `alias`, `address`)**) **plus** `docs/curro-spec-v1.0.md` bumped to **v1.1** with a revised §12 documenting the deliberate relaxation (crash + product analytics are kept off-device via Firebase + PostHog, gated by `TELEMETRY_ENABLED`, release-only; on-device privacy promise still applies to audio / transcripts / message content / contacts / aliases / command history; `TelemetryGuardrail` is the enforcement mechanism) — **without emitting a single telemetry event from any feature yet** (the first event-emitting SF is later — likely SF-3.x when FunctionGemma latency becomes a target) **so that** every Phase 1+ SF that wants to record a crash or a latency metric lands on a project where (a) the privacy boundary spec §12 v1.0 promised is mechanically preserved for everything Curro hears, transcribes, reads aloud, or learns; (b) `@Inject lateinit var telemetry: TelemetrySink` is the *only* shape any feature ever has to write — no SDK-specific code outside `data/telemetry/`; (c) a forbidden event property breaks CI on the same commit it's introduced, not in a privacy review six months later; (d) the deliberate relaxation of spec §12 stops being a "see `CLAUDE.md`" footnote and becomes spec v1.1's actual text, with the version-bump traceability spec §14 demands; and (e) Phase 0 closes with the final structural brick in place — Phase 1 starts on a project where the privacy contract is enforced in code, not aspirationally documented.

**Acceptance Criteria**:
- [ ] `./gradlew assembleDebug` succeeds on a fresh clone (no `app/google-services.json` present — the build does not require it; the developer documents the chosen mechanism per Q3) and produces an installable APK whose `aapt dump permissions app/build/outputs/apk/debug/app-debug.apk` output contains **zero** `INTERNET` permission lines
- [ ] `./gradlew assembleRelease` succeeds (with whatever the architect resolves for `google-services.json` and the PostHog API key supply per Q3 / Q6) and produces an APK whose `aapt dump permissions` output contains **exactly one** `android.permission.INTERNET` line
- [ ] `app/src/main/AndroidManifest.xml` contains **no** `<uses-permission android:name="android.permission.INTERNET" />` line; the existing inline comment that lists future permissions has its `INTERNET → SF-0.8 (release manifest only, for telemetry)` bullet updated to `INTERNET → release manifest only (US-008) — see app/src/release/AndroidManifest.xml`
- [ ] `app/src/release/AndroidManifest.xml` exists and is a build-variant overlay declaring **only** `<uses-permission android:name="android.permission.INTERNET" />` plus the surrounding `<manifest>` tag (AGP merges the rest from main); the file has a header comment block stating the privacy-boundary rationale
- [ ] `gradle/libs.versions.toml` activates the five reserved entries (`firebaseBom`, `firebase-bom`, `firebase-crashlytics`, `firebase-analytics`, `posthog-android`) by removing the "# Activated in SF-0.8" markers and adding the two Gradle-plugin entries (`google-services`, `firebase-crashlytics-plugin`) under `[plugins]`
- [ ] `app/build.gradle.kts` declares the two Firebase Gradle plugins (`alias(libs.plugins.google-services)`, `alias(libs.plugins.firebase-crashlytics-plugin)`) **per the gating strategy the architect resolves in Q1** (plugin-level vs runtime vs build-variant); the dependencies on the Firebase + PostHog SDKs are declared **per the same resolved strategy** (release-only `releaseImplementation` vs always-present-but-runtime-gated `implementation`)
- [ ] `app/src/main/java/com/curro/app/domain/repository/TelemetrySink.kt` exists with three methods — `fun event(name: String, props: Map<String, Any> = emptyMap())`, `fun setUserProperty(key: String, value: String?)`, `fun logCrash(throwable: Throwable, fatal: Boolean = false)` — all of which route through `TelemetryGuardrail` before reaching any SDK
- [ ] `app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt` exists with a `fun isSafe(name: String, props: Map<String, Any>): GuardrailResult` API; the heuristic + key whitelist / blocklist combination is the one the architect resolves in Q4
- [ ] `app/src/main/java/com/curro/app/data/telemetry/FirebaseAndPostHogSink.kt` (the release-bound impl) and `app/src/main/java/com/curro/app/data/telemetry/NoopTelemetrySink.kt` (the debug-bound impl, which logs to Logcat at `Log.d` so the developer can see the call shape locally without any SDK active) both exist
- [ ] `app/src/main/java/com/curro/app/data/telemetry/TelemetryInitializer.kt` exists and is called from `CurroApp.onCreate()` via `@Inject lateinit var telemetryInitializer: TelemetryInitializer` + `telemetryInitializer.initialize()`; the initializer checks `BuildConfig.TELEMETRY_ENABLED` before doing anything and is a no-op when the flag is `false`
- [ ] `app/src/main/java/com/curro/app/di/TelemetryModule.kt` exists, is `@InstallIn(SingletonComponent::class)`, and binds `TelemetrySink` to the right implementation **per the Hilt-shape strategy the architect resolves in Q5** (runtime branch in a single module vs separate `debug/` + `release/` source-set modules)
- [ ] `app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailTest.kt` runs in `./gradlew testDebugUnitTest` and (a) **fails** for every forbidden example in the brief's fixture table (full names, phone numbers, emails, message-body-shaped strings, forbidden keys); (b) **passes** for every allowed example (action-name enums, error codes, model identifiers, latency milliseconds, boolean flags); the test is wired so CI runs it as part of the existing `Run unit tests` step in `.github/workflows/ci.yml` — no CI workflow edit needed
- [ ] **No telemetry call is emitted from any production code yet** — `grep -rn 'telemetrySink.event\|telemetrySink.logCrash\|telemetrySink.setUserProperty' app/src/main/java/com/curro/app/` returns zero matches outside `data/telemetry/` itself (and any internal smoke probe inside `TelemetryInitializer`)
- [ ] `docs/curro-spec-v1.0.md` is bumped to **v1.1** in the same commit: the `**Versión:** 1.0` line at the top becomes `**Versión:** 1.1`; the document's footer paragraph that mentions "trazabilidad de versiones" is followed by a new `## Historial de revisiones` section with two rows — `v1.0 (May 2026) — Spec inicial.` and `v1.1 (May 2026) — §12 revisado: telemetría de fallos y producto (Firebase + PostHog) admitida fuera del dispositivo bajo el flag TELEMETRY_ENABLED y el guardrail TelemetryGuardrail. Las garantías on-device para audio, transcripciones, contenido de mensajes, contactos, alias e historial de comandos permanecen.`; §12 itself is rewritten per the architect-approved Spanish copy in the brief's Spec §12 v1.1 — proposed rewrite section
- [ ] `./gradlew ktlintCheck detekt` is green; the new files in `data/telemetry/`, `di/`, and `domain/repository/` follow the existing ktlint/detekt posture (no widening of `MagicNumber` excludes; no `!!` operators; no hard-coded strings beyond the SDK initialisation strings + the `TelemetryGuardrail` forbidden-key constants)
- [ ] `app/google-services.json` is git-ignored (verify the existing `.gitignore` line at L52 already covers it — no edit needed)
- [ ] **Architect resolves Q1–Q8** in §Open Questions before development; each gets a `**Q# — Resolved: …**` block in the brief (precedent: US-002, US-004)
- [ ] **Out of scope, explicit non-deliveries**: no telemetry instrumentation of any feature (no `latency_ms` events from FunctionGemma — Phase 3; no `stt_failed` events — Phase 2; no `command_failed` log forwarding — Phase 7); no FCM / push (spec §14 closed: no accounts, no push); no Firebase Auth, no Firebase Storage; no PostHog feature-flag plumbing (only event capture); no user-facing "send my failures to Fran" toggle (lands with the config menu in Phase 8 / SF-8.x — this SF is the plumbing, not the UI); no `INTERNET` permission anywhere outside `app/src/release/AndroidManifest.xml`; no spec edits beyond §12 + the revision-history row (the §5 "8 vs 7 funciones" cosmetic and the `targetSdk` cosmetic noted in master-plan are separate items in the v1.1 coordinated bump — queued, not folded into US-008); no `READ_PHONE_STATE` / `ANSWER_PHONE_CALLS` / any other permission as a side effect; no CI YAML edit (the existing `Decode google-services.json` step + `Run unit tests` step are sufficient).

**Size**: M  ·  **Depends on**: US-001, US-002

---

## Phase 1 — Launcher base _(spec §11, §14 step 1)_

> `MainActivity` as `CATEGORY_HOME` launcher · "set as default launcher" flow ·
> home screen: big clock+date, large mic button (≥40% screen, haptic), 4–6 large app
> tiles, "Más apps" screen. **No assistant yet.** Validate with the real user that
> this replaces the stock launcher.

### US-009: `CATEGORY_HOME` + "Hazme tu pantalla de inicio" via `RoleManager`  ·  _(master-plan SF-1.1, spec §11 / §14 step 1)_
**As a** Curro developer, **I want** Curro flipped into being the actual home screen of the Redmi 15 — the existing `MainActivity` intent-filter extended with `CATEGORY_HOME` + `CATEGORY_DEFAULT` (alongside the `MAIN` + `LAUNCHER` US-007 already shipped, so Curro keeps appearing in the app drawer), a `DefaultLauncherDetector` that resolves the system's current home-resolved Activity via `PackageManager.resolveActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME))` and re-emits on every `ON_RESUME` so the UI reacts when HyperOS "forgets" the default after an update, a `MakeMeDefaultLauncher` utility that wraps `RoleManager.ROLE_HOME` (`createRequestRoleIntent` first, `Settings.ACTION_HOME_SETTINGS` as the fallback for OEMs that don't surface the chooser or for the "don't ask again" path), a thin `LauncherViewModel` (`StateFlow<LauncherUiState>` keyed off the detector's flow) injected via Hilt into `LauncherPlaceholderScreen`, and a senior-first `BigPrimaryButton` rendering the existing canonical string `R.string.copy_home_make_default` ("Hazme tu pantalla de inicio") that appears **only when Curro is not the resolved default home** and disappears reactively once it is — the canonical "set as default" entry point until the real launcher home lands across SF-1.2 → SF-1.5 **so that** Phase 1's first validation gate (spec §14 step 1 — "Lo primero que validar: que tu padre lo entiende como reemplazo del launcher de fábrica") becomes mechanically achievable: pressing HOME from any other app brings Curro back to its placeholder route instead of stock HyperOS, Fran can flip the default with one big-button tap on the very first install, and the HyperOS "forgetting the default after updates" reality (master-plan SF-1.1 risk b) is already met with a visible-affordance recovery path before Phase 8 ships any diagnostics surface.

**Acceptance Criteria**:
- [ ] `app/src/main/AndroidManifest.xml`'s `<activity android:name=".MainActivity">` `<intent-filter>` contains exactly four `<category>` lines — `MAIN` (action) plus `android.intent.category.HOME`, `android.intent.category.DEFAULT`, `android.intent.category.LAUNCHER` — verifiable via `grep -E 'category\.(HOME|DEFAULT|LAUNCHER)' app/src/main/AndroidManifest.xml | wc -l` returning `3`; all US-007 `<activity>` attributes (`exported="true"`, `launchMode="singleTask"`, `clearTaskOnLaunch="true"`, `stateNotNeeded="true"`, `screenOrientation="portrait"`, `windowSoftInputMode="adjustResize"`) are byte-identical
- [ ] `app/src/main/java/com/curro/app/data/launcher/DefaultLauncherDetector.kt` (interface) and `DefaultLauncherDetectorImpl.kt` (implementation) exist; the impl queries `PackageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)`, compares the resolved `activityInfo.packageName` to `"com.curro.app"`, exposes `fun isDefault(): Boolean` and `val flow: Flow<Boolean>` (the Flow re-emits on every `Lifecycle.Event.ON_RESUME` of `ProcessLifecycleOwner` so the UI updates after the user comes back from `Settings`)
- [ ] `app/src/main/java/com/curro/app/data/launcher/MakeMeDefaultLauncher.kt` exists with two methods: `fun requestRoleIntent(): Intent?` (returns `roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)` when the role is available and not held, `null` otherwise — null means "already default OR role unavailable, fall back to settings"), `fun openHomeSettings(): Intent` (returns `Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)`); the brief's developer instructions specify "wire `requestRoleIntent()` first, `openHomeSettings()` as the fallback when it returns null"
- [ ] `app/src/main/java/com/curro/app/di/LauncherModule.kt` exists, is `@InstallIn(SingletonComponent::class)`, and binds `DefaultLauncherDetector` to `DefaultLauncherDetectorImpl` as `@Singleton`; `MakeMeDefaultLauncher` is `@Inject`-constructable with `@ApplicationContext` and needs no explicit binding
- [ ] `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt` exists, is `@HiltViewModel`, injects `DefaultLauncherDetector`, exposes `val uiState: StateFlow<LauncherUiState>` where `data class LauncherUiState(val isCurroDefault: Boolean)`; built via `detector.flow.map { LauncherUiState(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState(isCurroDefault = false))`
- [ ] `LauncherPlaceholderScreen.kt` is updated: the signature gains `viewModel: LauncherViewModel = hiltViewModel()` and `onMakeDefault: () -> Unit` parameters (or equivalent — the brief pins the exact shape); when `uiState.isCurroDefault == false` a `BigPrimaryButton` rendering `stringResource(R.string.copy_home_make_default)` is shown **above** the existing "Ajustes (depuración)" `TextButton`; when `isCurroDefault == true` the `BigPrimaryButton` is gone; verifiable by toggling default-launcher on the emulator and observing the CTA appear / disappear without restarting Curro
- [ ] `CurroNavHost.kt`'s `Launcher` route wires the new callbacks: `LauncherPlaceholderScreen(onOpenConfig = ..., onMakeDefault = ...)` where `onMakeDefault` fires an `ActivityResultLauncher<Intent>` that prefers `makeMeDefaultLauncher.requestRoleIntent()` and falls back to `context.startActivity(makeMeDefaultLauncher.openHomeSettings())` when null; the `ActivityResultLauncher` is registered via `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())` and its result is ignored (the detector's flow is the source of truth for "did it stick")
- [ ] **No new string resource is introduced** — the brief reuses the existing `R.string.copy_home_make_default = "Hazme tu pantalla de inicio"` (already landed in US-005 / `strings.xml` line 148; already canonicalised in `brand-design/SKILL.md` line 595). The brief documents this explicitly so the developer doesn't add a duplicate `copy_make_me_default` ID
- [ ] On a fresh emulator with the stock launcher as default: launching Curro, the `LauncherPlaceholderScreen` shows the "Hazme tu pantalla de inicio" `BigPrimaryButton`; tapping it surfaces the system `ROLE_HOME` chooser; accepting Curro returns to `LauncherPlaceholderScreen` with the CTA gone; pressing HOME from any other app (`adb shell input keyevent KEYCODE_HOME` after `adb shell am start -n com.android.settings/.Settings`) returns to Curro's launcher route, not the stock launcher
- [ ] On HyperOS-style "forget the default" simulation (set Curro as default, then manually set the stock launcher back via `adb shell cmd package set-home-activity`): the next `onResume` of Curro re-emits the detector flow with `isCurroDefault = false`, the CTA reappears within one frame, no app restart required
- [ ] `app/src/test/java/com/curro/app/data/launcher/DefaultLauncherDetectorImplTest.kt` exists and runs in `./gradlew testDebugUnitTest`: covers (a) "Curro is the resolved home" → `isDefault() == true`; (b) "stock launcher is the resolved home" → `isDefault() == false`; (c) "no home resolved" (resolver returns `null`) → `isDefault() == false`; (d) the flow emits the new value on a simulated `ON_RESUME` after the underlying `PackageManager` answer changes. The brief pins which approach (Robolectric `shadowOf(packageManager)` OR a JVM unit test against a thin `PackageManagerWrapper` interface) is used; either is acceptable
- [ ] `app/src/test/java/com/curro/app/data/launcher/MakeMeDefaultLauncherTest.kt` exists: covers (a) role available + not held → `requestRoleIntent()` returns a non-null `Intent` whose action matches `RoleManager.createRequestRoleIntent(ROLE_HOME)`; (b) role held → `requestRoleIntent()` returns `null`; (c) role unavailable → `requestRoleIntent()` returns `null`; (d) `openHomeSettings()` always returns a non-null `Intent` with `Settings.ACTION_HOME_SETTINGS` and `FLAG_ACTIVITY_NEW_TASK`. The brief pins whether `RoleManager` is faked via a wrapper interface or via Robolectric
- [ ] `app/src/test/java/com/curro/app/presentation/launcher/LauncherViewModelTest.kt` exists: covers the initial state, the state change on the detector emitting `true`, the state change on `false`, and Turbine-style flow assertions
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` are all green on the post-US-009 commit; the produced debug APK installs and launches on the Pixel_10_Pro emulator without crashing; manual verification: the CTA appears on first launch (stock launcher is default), tapping it opens the chooser, accepting Curro hides the CTA
- [ ] **No new permission** is added (`RoleManager` is consent-based, not permission-gated; `PackageManager.resolveActivity` needs none); the manifest gets only the two new `<category>` lines
- [ ] **No FSM, no STT, no LLM, no telemetry event** is wired in this SF — the FSM doesn't exist yet (Phase 5); the brief notes that `MainActivity.onNewIntent()` is the right hook for SF-5.x to add the FSM-to-`idle` transition, but US-009 deliberately leaves `onNewIntent()` un-overridden because `singleTask` + `clearTaskOnLaunch="true"` + `stateNotNeeded="true"` already deliver the "HOME returns to a clean launcher" story
- [ ] **Out of scope, explicit non-deliveries**: no real launcher home — clock, mic button, app grid, "Más apps" (SF-1.2 → SF-1.5); no 5-tap-on-clock gesture (SF-1.6); no HyperOS battery-whitelist deep-link (Phase 8 / SF-8.x diagnostics); no "am I default?" diagnostic readout in the config menu (SF-8.x diagnostics); no first-run onboarding flow (out of scope for the prototype); no `FSM-reset-on-onNewIntent` (Phase 5); no edit to `brand-design`'s COPY table or `strings.xml` beyond confirming the existing `copy_home_make_default` entry is the canonical source

**Size**: M  ·  **Depends on**: US-007

---

### US-010: Clock + date on launcher home  ·  _(master-plan SF-1.2, spec §11)_
**As a** Curro user, **I want** the launcher home to show a large live clock (hours + minutes) and the current date in Spanish **so that** glancing at the phone tells me the time without asking Curro.

**Acceptance Criteria**:
- [ ] `ClockBlock.kt` composable exists in `presentation/launcher/`; renders time at `MaterialTheme.typography.displayLarge` (72 sp ExtraBold) and date at `MaterialTheme.typography.headlineLarge` (32 sp Bold), both centred
- [ ] Clock ticks every second via `ObserveClockUseCase` (a `flow { while(true) { emit(…); delay(1_000) } }` on `Dispatchers.Default`); the composable never polls — it subscribes to the state flow from `LauncherViewModel`
- [ ] Date is formatted `"EEEE d MMMM"` in sentence case (e.g. "Miércoles 13 mayo") using `Locale("es")` and `DateTimeFormatter`
- [ ] `LauncherUiState` gains a `clock: ClockState` field; `LauncherViewModel` combines `detector.flow` and `observeClock()` via `combine()` into a single `StateFlow<LauncherUiState>`
- [ ] `ClockBlock` is the topmost element of `LauncherPlaceholderContent`, above the "Hazme tu pantalla de inicio" CTA (which is now below the clock)
- [ ] The entire `ClockBlock` area is `Modifier.clickable` with `cd_clock` as the click label — SF-1.6 will wire the five-tap counter to this callback; in SF-1.2 the `onClockTapped` lambda passed from `CurroNavHost` is a no-op
- [ ] Four `@Preview` variants on `ClockBlock`: light, dark, `fontScale = 1.5f`, `fontScale = 2.0f` (each on a 412 dp frame, height chosen so the clock isn't clipped)
- [ ] `app/src/test/java/com/curro/app/domain/usecase/ObserveClockUseCaseTest.kt` covers: first emission arrives within one second; time and date text are non-empty strings; date text contains the day name in Spanish
- [ ] New string `cd_clock = "Reloj"` added to `strings.xml` (content description for the clock tap area)
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

**Size**: S  ·  **Depends on**: US-009

---

### US-011: Main mic button (inert)  ·  _(master-plan SF-1.3, spec §11)_
**As a** Curro user, **I want** a large microphone button dominating the launcher home (≥ 40 % of screen height) **so that** I know exactly where to press to talk to Curro — even before the voice pipeline exists.

**Acceptance Criteria**:
- [ ] `MicButton.kt` composable in `presentation/launcher/`; `modifier`, `onPressed`, `enabled` signature
- [ ] Button height ≥ 40 % of screen via `Modifier.fillMaxHeight(Dimens.MIC_BUTTON_MIN_HEIGHT_FRACTION)` + `Modifier.fillMaxWidth()`
- [ ] `Icons.Filled.Mic` at ≥ `Dimens.LargeIconSize * 2` (96 dp), label `copy_home_mic_label` ("CURRO") at `MaterialTheme.typography.displaySmall` below icon — neither clips at `fontScale = 2.0`
- [ ] Background `MaterialTheme.colorScheme.primary`; shape `MaterialTheme.shapes.large`; elevation `Dimens.CardElevation`
- [ ] `HapticFeedbackType.LongPress` on press
- [ ] `LauncherEvent` sealed interface added to `LauncherViewModel.kt` with at minimum `MicPressed`
- [ ] `LauncherSideEffect` sealed interface with `ShowToast(messageResId: Int)` backed by a `Channel`; screen consumes via `LaunchedEffect`
- [ ] Tapping the button → `viewModel.onEvent(MicPressed)` → `ShowToast(R.string.copy_mic_inert)` emitted → screen shows a Toast
- [ ] New string `copy_mic_inert = "Aún no escucho — espera a la siguiente versión"` (Phase-1-only dev string, flagged in brief as not in canonical COPY table)
- [ ] `MicButton` placed below `ClockBlock` (and CTA when visible) in `LauncherPlaceholderContent`
- [ ] 4 previews: light, dark, `fontScale = 1.5f`, `fontScale = 2.0f`
- [ ] Unit test: `MicPressed` event emits `ShowToast` exactly once via the Channel
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

**Size**: S  ·  **Depends on**: US-010

---

### US-012: Static favourite-apps grid  ·  _(master-plan SF-1.4, spec §11)_
**As a** Curro user, **I want** four big app tiles for WhatsApp, Llamadas, Cámara, and Fotos always visible on the launcher home **so that** I can open the apps I use most with one tap, without having to speak.

**Acceptance Criteria**:
- [ ] `AppTileGrid.kt` and `AppTile.kt` composables in `presentation/launcher/`; 2×2 grid layout
- [ ] Each tile ≥ 96 dp height, app icon (Drawable → Bitmap → `ImageBitmap`, no Accompanist dep) + Spanish label below
- [ ] `FavoriteAppsRepository` interface in `domain/repository/`; `FavoriteApp` domain model with `id`, `labelResId`, `resolvedPackage`, `icon`
- [ ] `StaticFavoriteAppsRepositoryImpl` in `data/apps/` resolves WhatsApp (`com.whatsapp`), Llamadas (via `Intent.ACTION_DIAL` resolution + `com.android.dialer` fallback), Cámara (`Intent.ACTION_IMAGE_CAPTURE` + `com.android.camera`), Fotos (`Intent.ACTION_PICK` image + `com.miui.gallery`)
- [ ] `AppsModule` Hilt module in `di/` binds the interface
- [ ] `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` in `AndroidManifest.xml` with comment
- [ ] `LauncherUiState` gains `favorites: List<FavoriteApp>`; ViewModel combines three flows
- [ ] Tile tap → `LauncherEvent.AppTileTapped(app)` → `LauncherSideEffect.LaunchApp(packageName)` → `context.startActivity(getLaunchIntentForPackage(...))`
- [ ] Uninstalled app tile → disabled/greyed, tap → Toast `copy_app_not_installed`
- [ ] 4 new strings: `copy_app_label_whatsapp`, `copy_app_label_calls`, `copy_app_label_camera`, `copy_app_label_photos` + `copy_app_not_installed`
- [ ] `AppTileGrid` placed below `MicButton` in `LauncherPlaceholderContent`
- [ ] Unit tests for `StaticFavoriteAppsRepositoryImpl`: resolved/unresolved package, icon loading
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

**Size**: M  ·  **Depends on**: US-011

---

### US-013: "Más apps" full-list screen  ·  _(master-plan SF-1.5, spec §11)_
**As a** Curro user, **I want** to see and open any installed app from a scrollable big-row list **so that** I'm not limited to the four favourite tiles.

**Acceptance Criteria**:
- [ ] `MoreAppsScreen.kt` + `MoreAppsViewModel.kt` in `presentation/launcher/`
- [ ] `InstalledAppsRepository` interface + `InstalledAppsRepositoryImpl` in `data/apps/`; queries `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER)` on `Dispatchers.IO`; re-emits on `ON_RESUME`
- [ ] `LazyColumn` with `BigListRow` per app, icon in leading slot; alphabetical by Spanish display name via `Collator.getInstance(Locale("es"))`
- [ ] Back chevron `Icons.AutoMirrored.Filled.KeyboardArrowLeft` at TopStart, ≥ 96 dp tap target, `cd_back` label
- [ ] Row tap → `context.startActivity(getLaunchIntentForPackage(...))` directly from the screen
- [ ] `CurroRoute.MoreApps("more_apps")` added to the route enum; route registered in `CurroNavHost`
- [ ] "Más apps" `BigPrimaryButton` added to `LauncherPlaceholderContent` below `AppTileGrid`; uses `copy_home_more_apps` string (already exists)
- [ ] Full list loads in < 1 s on typical emulator; `LazyColumn` uses `key = { it.packageName }`
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

**Size**: S  ·  **Depends on**: US-012

---

### US-014: Clock five-tap gesture → config menu  ·  _(master-plan SF-1.6, spec §9)_
**As** Fran (the configurator), **I want** to open the hidden settings menu by tapping the clock five times within three seconds **so that** the elderly user cannot accidentally stumble into settings.

**Acceptance Criteria**:
- [ ] `LauncherEvent.ClockTapped` handled in `LauncherViewModel`; internal tap-time list, 3-second window, 5-tap threshold
- [ ] On 5 taps within 3 s: `LauncherSideEffect.OpenConfig` emitted, list cleared
- [ ] `CurroNavHost` wires `onClockTapped = { viewModel.onEvent(ClockTapped) }` (passing the ViewModel via the existing entry-point or the screen's own ViewModel)
- [ ] Screen's side-effect collector handles `OpenConfig` → `navController.navigate(CurroRoute.ConfigMenu.value)`
- [ ] Debug `TextButton` ("Ajustes (depuración)") removed from `LauncherPlaceholderContent`; `launcher_placeholder_open_config_debug` string removed from `strings.xml`
- [ ] Unit tests: 4 taps in 5 s → no effect; 5 taps in 3 s → `OpenConfig` emitted; 5 taps spread over 4 s → no effect
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

**Size**: S  ·  **Depends on**: US-013

---

## Phase 2 — Voice pipeline _(spec §4.2, §4.6, §14 step 2)_

> `RECORD_AUDIO` · native offline `SpeechRecognizer` (Spanish) with live transcription ·
> native `TextToSpeech` (Spanish, slowed ~10–15%) · the capture→response loop, no
> decision model yet. Confirm it works on-device.

### US-015: `SttClient` — offline Spanish `SpeechRecognizer` wrapper  ·  _(master-plan SF-2.1, spec §4.2)_
**As a** Curro user, **I want** Curro to transcribe what I say in Spanish without using the network **so that** my voice never leaves the device and I get a transcript even with the SIM out.

**Acceptance Criteria**:
- [ ] `SttClient.kt` interface in `domain/repository/`; emits `Flow<SttClient.Event>` with `Partial(text)`, `Final(text)`, `Failed(error: CurroError)`; `cancel()` releases the active session
- [ ] `SystemSttClient.kt` in `data/voice/` wraps `SpeechRecognizer.createSpeechRecognizer(context)` with `RecognizerIntent.EXTRA_LANGUAGE = "es-ES"`, `EXTRA_PREFER_OFFLINE = true`, `EXTRA_PARTIAL_RESULTS = true`, `EXTRA_CALLING_PACKAGE`
- [ ] `callbackFlow { … }` body executes on `Dispatchers.Main.immediate` via terminal `.flowOn(Dispatchers.Main.immediate)` (mirror commits `796b5f4` / `b77d789` — `SpeechRecognizer` is main-thread-bound and the lifecycle bug must not happen a third time)
- [ ] `RecognitionListener` maps `onPartialResults` → `Event.Partial`, `onResults` → `Event.Final` + close, `onError(code)` → `Event.Failed(CurroError.SttNoMatch | SttTimeout | SttError(code) | SttVoicePackMissing | PermissionDenied)` + close
- [ ] `awaitClose { sr.cancel(); sr.destroy() }` so cancellation of the collecting coroutine releases the native recogniser cleanly
- [ ] `hasOfflineSpanish(): Boolean` probe using `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` + Spanish-locale check; surface `CurroError.SttVoicePackMissing` from the Flow if absent at `startListening` time
- [ ] New `CurroError.SttVoicePackMissing` variant added to the `CurroError` taxonomy (CLAUDE.md "Error handling" table updated)
- [ ] `<uses-permission android:name="android.permission.RECORD_AUDIO" />` declared in `app/src/main/AndroidManifest.xml` with a comment pointing to SF-2.1 (manifest declaration only — runtime request is screen-side in SF-2.3)
- [ ] `VoiceModule.kt` Hilt module in `di/` binds `@Binds @Singleton SttClient -> SystemSttClient` inside `SingletonComponent`
- [ ] New string `copy_stt_no_voice_pack` = "Falta el paquete de voz español. Díselo a Fran."
- [ ] Unit tests in `app/src/test/java/com/curro/app/data/voice/SystemSttClientTest.kt`: fake `RecognitionListener` events → asserted `Event` mappings for every `ERROR_*` code (NO_MATCH, SPEECH_TIMEOUT, NETWORK, AUDIO, INSUFFICIENT_PERMISSIONS, CLIENT, SERVER, RECOGNIZER_BUSY) + empty-result final + partial-then-final + partial-then-error
- [ ] No network access — verified by reading `SystemSttClient.kt` (only `RecognizerIntent` + `SpeechRecognizer`); no `INTERNET` permission in main manifest
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

**Size**: M  ·  **Depends on**: US-007 (CurroNavHost), US-002 (Hilt DI)

---

### US-016: `TtsClient` — Spanish `TextToSpeech` wrapper with slowed rate and barge-in  ·  _(master-plan SF-2.2, spec §4.6, §14 closed decisions)_
**As a** Curro user, **I want** Curro to speak Spanish at a comfortable speed and stop instantly when I press the mic **so that** I can hear him clearly and cut him off when I want to talk.

**Acceptance Criteria**:
- [ ] `TtsClient.kt` interface in `domain/repository/`; `suspend fun speak(text, utteranceId): SpeakResult`, `fun stop()`, `fun isSpeaking(): Boolean`; `SpeakResult = Completed | Cancelled | Failed(CurroError)` sealed interface
- [ ] `SystemTtsClient.kt` in `data/voice/` lazy-inits `TextToSpeech(context, onInitListener)`, calls `setLanguage(Locale("es", "ES"))`, `setSpeechRate(0.88f)`, `setPitch(1.0f)`
- [ ] On init: `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` → expose via `isReady = false` and `speak()` immediately resolves `Failed(CurroError.TtsLanguageMissing)`
- [ ] Voice selection: prefer a male `es` voice (`tts.voices.firstOrNull { v -> v.locale.language == "es" && v.name.contains("male", ignoreCase = true) }`); fall back to the system default if none — best-effort, no crash
- [ ] `speak(...)` implemented as `suspendCancellableCoroutine` with `UtteranceProgressListener` — `onDone(id)` → `Completed`, `onError(id, code)` → `Failed(CurroError.TtsError(code))`, coroutine-cancellation → `tts.stop()` → `Cancelled`
- [ ] `stop()` calls `tts.stop()` synchronously and returns within ~50 ms (acceptance via instrumented test on emulator)
- [ ] New `CurroError.TtsLanguageMissing` and `CurroError.TtsError(code)` variants added to the `CurroError` taxonomy (CLAUDE.md "Error handling" table updated)
- [ ] `VoiceModule` binds `@Binds @Singleton TtsClient -> SystemTtsClient`; same module as US-015
- [ ] `tts.shutdown()` documented as relying on process-kill release (Singleton lifetime spans process lifetime; no Application teardown hook needed)
- [ ] New string `copy_tts_smoke_test` = "Hola, soy Curro." (Phase-2-only smoke-test string, retired in Phase 5; flagged in brief as not in canonical COPY table)
- [ ] Unit tests in `app/src/test/java/com/curro/app/data/voice/SystemTtsClientTest.kt`: complete path, cancel-mid-speak path, native-error path, language-missing path — all using a fake `TextToSpeech` via Mockk
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

**Size**: M  ·  **Depends on**: US-007 (CurroNavHost), US-002 (Hilt DI)

---

### US-017: Press → speak → see transcript → hear echo loop  ·  _(master-plan SF-2.3, spec §4.1 + §4.2 + §4.6, §14 step 2 validation gate)_
**As a** Curro user, **I want** to press the mic, speak in Spanish, see what Curro understood, and hear him say it back **so that** I (and Fran) can validate the voice loop on the real Redmi 15 before adding the decision model.

**Acceptance Criteria**:
- [ ] `LauncherUiState` gains a `listeningState: ListeningState` field; `ListeningState = Idle | Starting | Listening(partialText) | Speaking(text) | Error(message)` sealed interface — provisional (Phase 5's full FSM absorbs this)
- [ ] `LauncherViewModel.onMicPressed()` replaced: barge-in if `listeningState != Idle` (cancel STT/TTS, reset to `Idle`); else check `RECORD_AUDIO` and emit `LauncherSideEffect.RequestRecordAudio` if missing, or start listening
- [ ] `LauncherEvent` gains `RecordAudioPermissionResult(granted: Boolean)`; `LauncherSideEffect` gains `RequestRecordAudio`
- [ ] `viewModelScope` job collects `sttClient.listen()` → `Partial` updates `Listening(text)`; `Final(text)` transitions to `Speaking(text)` then launches `ttsClient.speak(text)`; any `SpeakResult` resolves back to `Idle`
- [ ] `Failed(error)` → `Error(message)` with the Spanish copy mapped from `CurroError` (`SttNoMatch`/`SttTimeout` → `copy_stt_fail_1`; `PermissionDenied` → `copy_perm_missing_mic`; `SttVoicePackMissing` → `copy_stt_no_voice_pack`; other `Stt*` → `copy_stt_fail_1`); auto-clears back to `Idle` after 2.5 s (provisional — Phase 5's 1st/2nd/3rd counter replaces it)
- [ ] `LauncherPlaceholderScreen` registers an `ActivityResultLauncher` for `ActivityResultContracts.RequestPermission(Manifest.permission.RECORD_AUDIO)`; consumes `RequestRecordAudio` to fire it; on result calls `viewModel.onEvent(LauncherEvent.RecordAudioPermissionResult(granted))`
- [ ] On permission denial: `Error("Necesito permiso para escucharte. Díselo a Fran.")` (via `copy_perm_missing_mic` — already in `strings.xml`)
- [ ] `LauncherPlaceholderContent` renders the `ListeningOverlay` (from US-018) via `AnimatedVisibility(listeningState !is ListeningState.Idle)` on top of the launcher home; single fast fade < 200 ms
- [ ] `copy_mic_inert` string + the `ShowToast(R.string.copy_mic_inert)` emission **deleted** from `strings.xml` + `LauncherViewModel.onMicPressed()` (US-011 placeholder; replaced by real flow)
- [ ] Press-to-listening latency on the Redmi 15 < 1 s (verified manually with a stopwatch)
- [ ] No regression: clock, mic button, app tiles, "Más apps", five-tap-on-clock all behave as in Phase 1
- [ ] Unit tests in `LauncherViewModelTest.kt`: every `ListeningState` transition; barge-in cancels the active job; permission-missing emits side effect; permission denial → `Error(copy_perm_missing_mic)`; `Final` → `Speaking` → TTS-completes → `Idle`
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

**Size**: M  ·  **Depends on**: US-015 (SttClient), US-016 (TtsClient), US-011 (MicButton)

---

### US-018: `ListeningOverlay` composable — visual surface of the listening state  ·  _(master-plan SF-2.4, spec §11, launcher-ui surface 2)_
**As a** Curro user, **I want** the screen to obviously change while Curro is listening — a blue tint, big "Te escucho…", my words appearing as I say them, a calm audio-wave **so that** I see and feel that he is hearing me, and so cutting him off is also visually obvious.

**Acceptance Criteria**:
- [ ] `ListeningOverlay.kt` composable in `presentation/assistant/`; signature: `fun ListeningOverlay(state: ListeningState, modifier: Modifier = Modifier)`
- [ ] Background colour: `CurroListeningTintLight` in light mode, `CurroListeningTintDark` in dark — read via `if (isSystemInDarkTheme()) CurroListeningTintDark else CurroListeningTintLight` (both tokens already exist in `Color.kt` from US-005 — `Color.kt` is NOT touched)
- [ ] "Te escucho…" headline: `stringResource(R.string.copy_listening_prompt)`, `MaterialTheme.typography.displayMedium` (48 sp), colour `MaterialTheme.colorScheme.onBackground`, centered
- [ ] Live transcript below: `state.partialText` if `state is Listening`, else the spoken text if `state is Speaking`; `MaterialTheme.typography.bodyLarge` (20 sp), `maxLines = 4`, `overflow = TextOverflow.Ellipsis`, padded horizontally
- [ ] Audio-wave indicator: 5 thick vertical bars in a horizontal `Row`, each bar's height animated by a slow sine wave with period ~1.2 s via `produceState`/`LaunchedEffect`; phase offset per bar so they don't all pulse together; pure Compose — no Lottie / external animation dep
- [ ] When `state is ListeningState.Speaking`, the audio-wave switches to a static "speaking" indicator (e.g. the 5 bars hold a fixed mid-height) — visually distinct from active listening
- [ ] `MicButton.kt` extended with `isListening: Boolean = false` param; when `true`, background colour shifts to `MaterialTheme.colorScheme.secondary` (olive) — signals "tap again to cancel"; `MicButton` callsite passes `isListening = (listeningState !is Idle)`
- [ ] Contrast on the tint: `onBackground` text on `CurroListeningTintLight` measured ≥ 7:1; on `CurroListeningTintDark` ≥ 7:1 (already pre-measured in `Color.kt`'s docblock: 11.8:1 light / 12.7:1 dark)
- [ ] `AnimatedVisibility` wrap in `LauncherPlaceholderContent` (added in US-017): `fadeIn(animationSpec = tween(150))` + `fadeOut(animationSpec = tween(150))` — single property, no slide/scale
- [ ] 4 `@Preview` variants: light Listening, dark Listening, light Speaking, `fontScale = 2.0f` Listening with a long partialText (verifies the 4-line ellipsis works without layout shift)
- [ ] UI test in `app/src/androidTest/java/com/curro/app/presentation/assistant/ListeningOverlayTest.kt` (or Robolectric equivalent if instrumentation is heavier than Phase 2 wants): asserts `displayMedium` "Te escucho…" is present and that updating `state.partialText` updates the visible transcript without layout shift
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

**Size**: S  ·  **Depends on**: US-017 (end-to-end loop), US-005 (`CurroListeningTint*` already in `Color.kt`)

---

## Phase 3 — FunctionGemma decision layer _(spec §4.3, §5 Fase 1 catalogue, §14 step 3)_

> Load FunctionGemma 270M (int8) via MediaPipe LLM Inference · keep warm in a
> foreground service (`POST_NOTIFICATIONS`), <500 ms text→JSON · prompt = Fase-1
> function catalogue + minimal context · validate output against the catalogue JSON
> schema · show returned JSON on screen for debugging · friendly fallback on invalid
> output (flow 7), no auto-retry.

### US-019: Model asset delivery — side-load via `adb push` (path-driven)  ·  _(master-plan SF-3.1, spec §4.3 + §14 "risks identified")_
**As a** Curro developer, **I want** the FunctionGemma 270M weights to live at a configurable on-device path (default `/data/local/tmp/curro-models/`) loaded via `adb push` for the prototype **so that** I can iterate on real device latency without bundling 288 MB into the APK, while keeping `assembleDebug` on CI green when the weights are absent.

**Acceptance Criteria**:
- [ ] Decision pinned: **side-load for the prototype** — `adb push function_gemma_270m.task /data/local/tmp/curro-models/`. A future SF (post-prototype) can swap to bundled / Play Asset Delivery without touching `ModelFiles` callers (the abstraction below is the seam).
- [ ] `app/build.gradle.kts` reads optional `CURRO_MODEL_BASE_PATH` from `local.properties` (same `Properties().apply { … }` pattern as `POSTHOG_API_KEY`); if absent, defaults to `/data/local/tmp/curro-models`.
- [ ] `buildConfigField("String", "MODEL_BASE_PATH", "\"<value>\"")` emitted in **both** `debug` and `release` `buildTypes` — same value, no debug/release skew (the runtime cares only about file-present, not build type).
- [ ] `app/src/main/java/com/curro/app/data/ml/ModelFiles.kt` — small object with `fun functionGemma(): File = File(BuildConfig.MODEL_BASE_PATH, "function_gemma_270m.task")` and `fun isFunctionGemmaAvailable(): Boolean = functionGemma().exists() && functionGemma().canRead()`. **This is the only place that talks about file paths**; SF-3.2's engine asks `ModelFiles.functionGemma()` for the absolute path.
- [ ] `.gitignore` gains `*.task` (defensive — no model file should ever land in git regardless of CWD).
- [ ] `docs/curro-spec-v1.0.md` §14 "Riesgos identificados": the "Modelos: …" line (added in this SF if missing, edited if present) reads "**Entrega de modelos (decisión cerrada para prototipo):** side-load vía `adb push` a `/data/local/tmp/curro-models/`. Ruta configurable en `local.properties` (`CURRO_MODEL_BASE_PATH`), expuesta en runtime como `BuildConfig.MODEL_BASE_PATH`. Un SF posterior (post-prototipo) introducirá entrega empaquetada / Play Asset Delivery sin tocar `ModelFiles`." — **no spec version bump** (documentation refresh, not a contract change)
- [ ] `CLAUDE.md` "On-device models" section ends with: "**Side-load for the prototype**: weights live on the device at `/data/local/tmp/curro-models/function_gemma_270m.task`; the path is configurable via `local.properties` (`CURRO_MODEL_BASE_PATH`) and exposed at runtime as `BuildConfig.MODEL_BASE_PATH`. A future SF will introduce bundled / asset-pack delivery for release without changing the `ModelFiles` abstraction." The "release APK bundles ~2.3 GB" admonition stays — it's still true once delivery is bundled.
- [ ] New `docs/MODELS.md` (short): how to side-load (`adb push function_gemma_270m.task /data/local/tmp/curro-models/`), where to obtain the weights, the prototype-only nature, and a sanity-check command (`adb shell ls -l /data/local/tmp/curro-models/`).
- [ ] **No new permissions, no manifest changes, no MediaPipe activation** (MediaPipe wiring is SF-3.2).
- [ ] **No new dependency** — the build still compiles without `libs.mediapipe.tasks.genai`. `ModelFiles` is pure Kotlin (`java.io.File` + `BuildConfig`).
- [ ] New COPY entry `copy_models_not_ready` = "Aún estoy preparando los modelos, dame un segundo." — added to `strings.xml` (SF-3.6 wires it; ship the string now so the smoke loop in US-024 references an existing resource).
- [ ] CI green without any model file: `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all pass. `ModelFiles.isFunctionGemmaAvailable()` returns `false`; callers degrade.
- [ ] JVM test in `app/src/test/java/com/curro/app/data/ml/ModelFilesTest.kt`: asserts `BuildConfig.MODEL_BASE_PATH == "/data/local/tmp/curro-models"`; asserts `isFunctionGemmaAvailable() == false` on a clean test machine (no file at the path).
- [ ] No PII at any boundary — `ModelFiles` neither reads nor logs anything beyond a path string.

**Size**: M  ·  **Depends on**: US-001 (Gradle skeleton — `local.properties` plumbing exists)

---

### US-020: `FunctionCallEngine` interface + `FunctionGemmaEngine` MediaPipe wrapper  ·  _(master-plan SF-3.2, spec §4.3)_
**As a** Curro developer, **I want** a clean `FunctionCallEngine` boundary in `domain/repository/` and a MediaPipe-backed `FunctionGemmaEngine` in `data/ml/` **so that** the rest of the codebase (and every JVM test) calls "give me a function call for this utterance" without ever importing MediaPipe — and so SF-3.5's warm-up service and SF-3.6's smoke loop have a single, testable seam.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/domain/repository/FunctionCallEngine.kt` — pure-Kotlin interface, no Android imports: `suspend fun decide(utterance: String, ctx: PromptContext): Result<String>`, `fun warmUp()`, `fun isReady(): Boolean`. **Returns `Result<String>` (raw model output)**, NOT `Result<FunctionCall>` — the validator (SF-3.4) is a separate collaborator, called by SF-3.6's coordinator. Keeping the engine output as `String` makes JVM tests trivial and lets the validator's failure modes ship before the real engine runs.
- [ ] `PromptContext` data class in `domain/model/`: `nowIso: String` (ISO-8601 local-time-no-offset, e.g. `2026-05-15T22:36:00`), `unreadMessagesSummary: String` (empty in Phase 3; SF-4.x's WhatsApp handlers will fill it; the engine doesn't care), `knownAliases: List<String>` (empty in Phase 3; SF-7.x's alias subsystem will fill it).
- [ ] `app/src/main/java/com/curro/app/data/ml/FunctionGemmaEngine.kt` — `@Singleton class FunctionGemmaEngine @Inject constructor(@ApplicationContext context: Context, promptBuilder: FunctionCallPromptBuilder, @IoDispatcher io: CoroutineDispatcher, modelFiles: ModelFiles) : FunctionCallEngine`. **MediaPipe imports live ONLY in this file**: `com.google.mediapipe.tasks.genai.llminference.LlmInference` + `LlmInferenceOptions`. `@IoDispatcher` qualifier is introduced in this SF if not already present (a small `di/DispatcherModule.kt` exposing `@IoDispatcher` → `Dispatchers.IO` is part of the SF if needed).
- [ ] `warmUp()`: if `ModelFiles.isFunctionGemmaAvailable() == false` → leave `llm = null`, no throw, log once at INFO; else build `LlmInferenceOptions.builder().setModelPath(ModelFiles.functionGemma().absolutePath).setMaxTokens(256).setTemperature(0.1f).setTopK(1).build()`, call `LlmInference.createFromOptions(context, options)`, store the result, log warm-time (`Log.i("Curro/Llm", "warm-up took ${ms}ms")`).
- [ ] `decide(utterance, ctx)`: if `llm == null` → `Result.failure(CurroError.ModelCold)` + side-effect-kick `warmUp()` (next call may succeed); else wrap in `withContext(io)` and call `llm!!.generateResponse(promptBuilder.build(utterance, ctx))`; catch `OutOfMemoryError` → `Result.failure(CurroError.OutOfMemory)`; success → `Result.success(rawString)` + `Log.i("Curro/Llm", "decide latency: ${ms}ms")` (latency only — **never** the utterance).
- [ ] **Threading**: `decide()` ALWAYS runs the MediaPipe `generateResponse` inside `withContext(io)`. The brief makes explicit: "do NOT repeat the Phase-1 callbackFlow-on-IO bug — `generateResponse` is blocking, the engine is not thread-safe; guard concurrent `decide()` with a `Mutex` (`private val callMutex = Mutex()` → `callMutex.withLock { … }`). In Phase 3 only the launcher calls it, so contention is unlikely, but the mutex is cheap insurance."
- [ ] `isReady()` returns `llm != null`. Pure-Kotlin, no suspension.
- [ ] New Hilt module `app/src/main/java/com/curro/app/di/MlModule.kt`: `@Module @InstallIn(SingletonComponent::class) abstract class MlModule { @Binds @Singleton abstract fun bindFunctionCallEngine(impl: FunctionGemmaEngine): FunctionCallEngine }`.
- [ ] `gradle/libs.versions.toml` MediaPipe entry **activated** (the placeholder `mediapipeGenai = "0.10.14"` already exists — the developer may bump to the freshest 0.10.x that is compatible with Kotlin 2.1 + AGP 8.7; if uncertain, stick to `0.10.14`. The decision is recorded in the brief, not the AC).
- [ ] `app/build.gradle.kts` dependencies block: replace the `// MediaPipe → SF-3.1: …` reserved comment with `implementation(libs.mediapipe.tasks.genai)`.
- [ ] **MediaPipe import boundary**: a grep AC — `grep -r "com.google.mediapipe" app/src/main/java/com/curro/app/` returns only `data/ml/FunctionGemmaEngine.kt`. **No tests import MediaPipe**.
- [ ] New `CurroError.OutOfMemory` and `CurroError.ModelCold` variants — both already exist per CLAUDE.md "Error handling" table; this SF verifies them and adds the mappings.
- [ ] **Test fake** at `app/src/test/java/com/curro/app/data/ml/FakeFunctionCallEngine.kt`: implements `FunctionCallEngine`; configurable `nextResult: Result<String>`, `isReadyValue: Boolean`, captures the last `(utterance, ctx)`. Lives in `test/` so production never sees it; SF-3.6 and Phase 5 reuse it.
- [ ] JVM unit tests in `app/src/test/java/com/curro/app/data/ml/FunctionGemmaEngineContractTest.kt` against `FakeFunctionCallEngine` (NOT the real impl — real impl needs native binaries): ≥ 6 cases — cold engine → `CurroError.ModelCold`; ready engine returns raw success string; OOM mapped to `CurroError.OutOfMemory`; `warmUp()` is idempotent (second call is a no-op when `llm != null`); `isReady()` reflects warm state; `decide()` calls `promptBuilder.build` exactly once per invocation.
- [ ] **The real `FunctionGemmaEngine` is not JVM-testable** — explicit comment in the file says so; real-engine verification is the on-device gate (SF-3.6's "qué hora es" → JSON-on-screen).
- [ ] **Latency log line** on every `decide()`: `Log.i("Curro/Llm", "decide latency: ${ms}ms")` — respects the `TelemetryGuardrail` from US-008 (no PII; only the duration). A separate `telemetry.event("model_inference", mapOf("model" to "function_gemma_270m", "latency_ms" to ms))` may be emitted — model name + latency are safe; **never the utterance or the action**.
- [ ] No new permissions, no manifest changes.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green **without** the `.task` file present.

**Size**: L  ·  **Depends on**: US-019 (`ModelFiles` + `BuildConfig.MODEL_BASE_PATH`), US-021 (`FunctionCallPromptBuilder` is the constructor collaborator), US-002 (Hilt DI graph)

---

### US-021: `domain/catalog/` Fase-1 catalog + `FunctionCallPromptBuilder`  ·  _(master-plan SF-3.3, spec §5 Fase 1, function-catalog skill)_
**As a** Curro developer, **I want** the Fase-1 function catalog declared once in pure Kotlin (`domain/catalog/`) and rendered into a tight, deterministic prompt by `FunctionCallPromptBuilder` **so that** the same 7 functions FunctionGemma is prompted with — `tell_time, open_app, calculate, help, read_last_whatsapp, read_all_unread_whatsapp, call_contact` — are what the validator validates against and what every Phase-4 handler will register against, with no drift.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/domain/catalog/CatalogFunction.kt` — pure-Kotlin data class + supporting types: `CatalogFunction(name, description, params, needsConfirmation, voiceExamples)`, `CatalogParam(name, type, required, description, defaultValue: String? = null)`, `sealed interface ParamType { object Str: ParamType; object Int: ParamType; data class Enum(val values: List<String>): ParamType }`, `enum class NeedsConfirmation { NO, YES, CONDITIONAL }`. No Android imports.
- [ ] `app/src/main/java/com/curro/app/domain/catalog/Fase1Catalog.kt` — `object Fase1Catalog { val functions: List<CatalogFunction> = listOf(tellTime, openApp, calculate, help, readLastWhatsApp, readAllUnreadWhatsApp, callContact) }`. **Order matters** — spec §14 implementation order. Every function declared as a `private val` mirroring the `function-catalog` skill EXACTLY (same name, same description, same params with types/required/desc/default, same `voice_examples` list, same `needs_confirmation`).
- [ ] `Fase1Catalog.functions.size == 7` — asserted by a test.
- [ ] Each function's content matches the `function-catalog` skill character-for-character on the Spanish strings (the skill is the canonical source; AC verifies via a side-by-side diff in the brief; runtime test asserts each `CatalogFunction.name` and `description`).
- [ ] `app/src/main/java/com/curro/app/data/ml/FunctionCallPromptBuilder.kt` — `@Singleton class FunctionCallPromptBuilder @Inject constructor() { fun build(utterance: String, ctx: PromptContext): String }`. Reads `Fase1Catalog.functions` directly (constructor-injecting it adds noise for no benefit at this phase).
- [ ] **The exact prompt template** is pinned in the brief (see Implementation Notes); the builder renders it deterministically. < 600 model-tokens budget on the empty-context, "qué hora es" case — verified by a word-count × 1.3 estimate in a test.
- [ ] Utterance is sanitised before interpolation: any `«` or `»` in the utterance is replaced with a single quote `'` (prevents the delimiter-confusion attack — though our user is not adversarial, the model still gets confused). Decision pinned: **replace**, do not strip; do not change delimiters.
- [ ] Empty `unreadMessagesSummary` → render the line as `Mensajes sin leer: ninguno`. Empty `knownAliases` → render the line as `Alias conocidos: ninguno`. Decision: render the line either way, never omit (keeps the prompt structurally stable so the model always sees the same 3 context lines).
- [ ] Golden-string test: `FunctionCallPromptBuilder.build("qué hora es", PromptContext(nowIso = "2026-05-15T22:36:00", unreadMessagesSummary = "", knownAliases = emptyList()))` asserted **byte-for-byte** against a frozen expected string in `app/src/test/resources/golden/prompt_tell_time_empty_context.txt`.
- [ ] Second golden test: a `call_contact`-flavoured utterance with a populated context (`unreadMessagesSummary = "3 de Pepito, 1 de Lucía"`, `knownAliases = listOf("mi hija → Lucía Ruiz", "el médico → Dr. Soriano")`). Verifies aliases and unread summary render correctly.
- [ ] Third golden test: utterance containing `«` and `»` is sanitised — exact rendered string asserted.
- [ ] `FunctionCallPromptBuilder` is `@Inject`-able and used by `FunctionGemmaEngine` (US-020).
- [ ] **No catalog mutation at runtime** — `Fase1Catalog.functions` is `val`, `List<CatalogFunction>`, the data class is immutable.
- [ ] No new dependency, no permission, no manifest change.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: M  ·  **Depends on**: US-020 (`PromptContext` is defined there; this SF could land first if `PromptContext` is moved to this SF — see recommended commit order)

---

### US-022: `FunctionCallValidator` — JSON Schema validation against the Fase-1 catalog  ·  _(master-plan SF-3.4, spec flow 7, on-device-llm "Output validation")_
**As a** Curro developer, **I want** every raw FunctionGemma string parsed and validated against the Fase-1 catalog — and every malformation mapped to a typed `CurroError` with **no automatic retry** — **so that** SF-3.6's smoke loop and Phase-4's handlers can trust the `FunctionCall` shape, and flow 7's friendly-fallback line gets fired with full diagnostic detail in the log.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/domain/model/FunctionCall.kt` — `data class FunctionCall(val action: String, val params: Map<String, Any>, val confidence: Float)`. Pure Kotlin, no Android imports. (Already referenced from CLAUDE.md "Architecture" — confirm via grep before creating; if it exists from an earlier SF, this SF only validates it.)
- [ ] `app/src/main/java/com/curro/app/data/ml/FunctionCallValidator.kt` — `@Singleton class FunctionCallValidator @Inject constructor() { fun parseAndValidate(raw: String): Result<FunctionCall> }`. Reads `Fase1Catalog.functions` directly. Pure Kotlin (uses `org.json.JSONObject` from the Android SDK which is JVM-available in unit tests; **no new dependency**).
- [ ] **Algorithm** (per `on-device-llm` skill "Output validation"):
  1. `raw.trim()`.
  2. Strip code fences: a regex-driven removal of an outer ```` ```json … ``` ```` or ```` ``` … ``` ```` wrapping.
  3. `JSONObject(stripped)` — `JSONException` → `Result.failure(CurroError.InvalidFunctionCall)`.
  4. `action`: must be a non-empty string; missing/empty/non-string → `InvalidFunctionCall`.
  5. `action` must match one of `Fase1Catalog.functions.map { it.name }`; unknown → `Result.failure(CurroError.UnknownFunction(action))`.
  6. `confidence`: must be a number in `[0.0f, 1.0f]`; missing / non-number / out-of-range / NaN → `InvalidFunctionCall`.
  7. `params`: must be a JSON object (`JSONObject`); missing → treated as empty `{}`; non-object → `InvalidFunctionCall`.
  8. For the matched `CatalogFunction`: every `required = true` param must be present in `params`; every present param must match its declared type (`Str` → string, `Int` → integer, `Enum(values)` → string ∈ values); **no extra params** allowed beyond those declared → `InvalidFunctionCall` on any violation.
  9. Success → `Result.success(FunctionCall(action, paramsMap, confidence))`.
- [ ] **No automatic retry** — explicit code comment on the function: "Spec flow 7: never retry. Caller logs and surfaces the friendly fallback."
- [ ] **Code-fence stripping**: handles both ```` ```json\n…\n``` ```` and ```` ```\n…\n``` ````; preserves a JSON object that has internal back-ticks intact (regex is greedy-then-anchored, not over-eager).
- [ ] JVM unit tests in `app/src/test/java/com/curro/app/data/ml/FunctionCallValidatorTest.kt` — exhaustive, table-driven (`@ParameterizedTest` where it helps). ≥ 20 cases:
  - **Good** (7 — one per Fase-1 function): each canonical successful parse, including `tell_time` with `{"what": "time"}`, `open_app` with `{"app_name": "WhatsApp"}`, `calculate`, `help` with no params, `help` with `{"topic": "mensajes"}`, `read_last_whatsapp` with no params, `read_last_whatsapp` with `{"sender": "Pepito"}`, `read_all_unread_whatsapp` with no params, `call_contact` with `{"contact": "mi hija"}`.
  - **Fence-stripping** (2): same good JSON wrapped in ```` ```json … ``` ```` and ```` ``` … ```` — both parse cleanly.
  - **Non-JSON** (1): `"{action: foo}"` (unquoted keys) → `InvalidFunctionCall`.
  - **Missing `action`** (1) → `InvalidFunctionCall`.
  - **Empty `action`** (1): `{"action": "", "params": {}, "confidence": 0.5}` → `InvalidFunctionCall`.
  - **Unknown `action`** (1): `{"action": "summon_dragon", "params": {}, "confidence": 0.9}` → `UnknownFunction("summon_dragon")`.
  - **Missing required param** (1): `call_contact` without `contact` → `InvalidFunctionCall`.
  - **Wrong-typed param** (2): `tell_time` with `{"what": 5}` (int instead of enum-string); `call_contact` with `{"contact": 42}` (int instead of string) → both `InvalidFunctionCall`.
  - **Extra param** (1): `tell_time` with `{"what": "time", "frobnicate": true}` → `InvalidFunctionCall`.
  - **Confidence out of range** (3): `1.5f`, `-0.1f`, `Float.NaN` → all `InvalidFunctionCall`.
  - **Confidence non-number** (1): `"confidence": "high"` → `InvalidFunctionCall`.
  - **Empty params object on a function with optional params** (2): `tell_time` with `{}` (the only param `what` is optional) → success with empty `params` map; `help` with `{}` → success.
  - **Enum value not in declared set** (1): `tell_time` with `{"what": "yesterday"}` (`what` enum is `time|date|day|all`) → `InvalidFunctionCall`.
- [ ] No catalog mutation: tests confirm `Fase1Catalog.functions` is unchanged before/after each validator call (defensive read of `.size`).
- [ ] No new permissions, no manifest changes, no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green; all 20+ validator tests pass.

**Size**: M  ·  **Depends on**: US-021 (`Fase1Catalog` + `CatalogFunction` types)

---

### US-023: `ModelWarmupService` — foreground service keeping FunctionGemma warm  ·  _(master-plan SF-3.5, spec §4.3, launcher-app HyperOS section)_
**As a** Curro user, **I want** FunctionGemma to already be in memory when I press the mic for the first time after unlocking my phone **so that** the first interaction is under 500 ms text→JSON like every subsequent one, and so that when HyperOS kills the service in the background Curro detects it and reloads quietly without ever crashing.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/service/ModelWarmupService.kt` — `@AndroidEntryPoint class ModelWarmupService : Service()`. `@Inject lateinit var engine: FunctionCallEngine`. `onBind() = null`. `onStartCommand`: post a low-importance ongoing notification, launch a service-scoped coroutine (`CoroutineScope(SupervisorJob() + Dispatchers.IO)` — stored as a private field; cancelled in `onDestroy`) that calls `engine.warmUp()`, returns `START_STICKY`.
- [ ] **Manifest additions** (`app/src/main/AndroidManifest.xml`):
  - `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` — Android 13+ runtime permission.
  - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />` — required for any FGS on Android 9+.
  - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />` — Android 14+ requires a typed FGS permission. Type `dataSync` is the closest match for model-keep-alive (no other type fits — `mediaPlayback` would be misleading, `connectedDevice` doesn't apply).
  - `<service android:name=".service.ModelWarmupService" android:foregroundServiceType="dataSync" android:exported="false" />` inside `<application>`.
  - Spec §10 permissions table updated: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` — for the model warm-up service; if denied, the service runs without a visible notification (Android 13+ allows this) and the model still warms; user impact is null beyond a missing icon in the notification shade.
- [ ] **Notification channel**: created in `CurroApp.onCreate()` (or a `NotificationChannelInitializer` from US-008's bootstrap pattern). Channel ID `"curro_warmup"`, name `"Curro modelo"`, `IMPORTANCE_MIN` (no sound, no vibration). `setOngoing(true)` on the notification.
- [ ] **Notification content**: `setSmallIcon(R.drawable.ic_curro_notification)` (a new monochrome vector drawable created in this SF — a simple microphone outline; the brief specifies the drawable XML literally so the developer doesn't redesign it). `setContentTitle(getString(R.string.copy_warmup_ongoing))` = "Curro está listo". `setContentText(...)` is left empty (the title alone is enough; less is more). `setContentIntent(piToLauncher)` opens `MainActivity` when tapped.
- [ ] New COPY entry `copy_warmup_ongoing` = "Curro está listo" — added to `strings.xml`.
- [ ] **Service start**: in `CurroApp.onCreate()` after `TelemetryInitializer` (existing from US-008), call `ContextCompat.startForegroundService(this, Intent(this, ModelWarmupService::class.java))`. On `POST_NOTIFICATIONS` denied (Android 13+): silent fall-back — the service still starts and `startForeground` succeeds without a visible notification icon. **No runtime permission prompt from this SF** (the FGS notification is "nice to have", not blocking; the permission may be requested in a later UX SF if Fran wants the icon visible).
- [ ] **Detect-and-recover** strategy pinned: **Strategy A — check-on-call**. Every `FunctionCallEngine.decide(...)` first checks `isReady()`. If false: emit `CurroError.ModelCold`, **side-effect-kick** `warmUp()`. The next call may succeed. This is already wired into SF-3.2's `FunctionGemmaEngine`; this SF adds no extra polling loop. (Strategy B — periodic ping — was considered; rejected because it burns battery and the check-on-call is sufficient for our usage shape.)
- [ ] **Service is hard to JVM-test** — explicit note in the brief; verification is manual on the Redmi 15 (screen-off cycle → re-foreground → first `decide()` under 500 ms; force-stop the service via `adb shell am force-stop com.curro.app` → next `decide()` returns `CurroError.ModelCold`, the service auto-restarts via `START_STICKY`, the call after that succeeds).
- [ ] **HyperOS battery-whitelist note**: the brief includes the user-facing setup steps (Settings → Battery → App battery saver → Curro → "No restrictions"; Security app → Autostart → Curro: ON). These are documented in `docs/MODELS.md` (added in US-019) so the developer setting up the device knows.
- [ ] No new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green; the service compiles and is registered in the manifest.
- [ ] **No PII in the notification** — title is `"Curro está listo"`, no user data ever in the FGS notification.

**Size**: M  ·  **Depends on**: US-020 (`FunctionCallEngine` to inject), US-008 (`CurroApp` already has the `TelemetryInitializer` bootstrap — same pattern for the service start)

---

### US-024: Decision smoke loop — STT → engine → validator → JSON on screen + TTS echo  ·  _(master-plan SF-3.6, spec flow 7, §14 step 2-3 validation gate)_
**As a** Curro developer, **I want** the existing voice loop from SF-2.3 extended end-to-end through SF-3.2's engine and SF-3.4's validator so "qué hora es" produces `{"action": "tell_time", ...}` visible on screen and "Reconocido: decir la hora" spoken back — **with no real handlers yet** — **so that** I can validate the < 500 ms warm-latency target on the real Redmi 15 and prove the prompt + validator pipeline is honest before Phase 4 builds the actual handlers.

**Acceptance Criteria**:
- [ ] `presentation/launcher/ListeningState.kt` gains a new variant: `data class Processing(val transcript: String) : ListeningState`. Inserted between `Listening(final)` and `Speaking(echo)`. Comment: **"Provisional — Phase 5's full FSM (`processing` state) replaces this."**
- [ ] `LauncherViewModel.handleSttEvent(Event.Final)` is replaced:
  1. Set `listeningStateFlow.value = Processing(text)`.
  2. Launch a child coroutine that calls `engine.decide(text, buildContext())` on `@IoDispatcher`. `buildContext()` returns a `PromptContext(nowIso = nowIsoLocal(), unreadMessagesSummary = "", knownAliases = emptyList())` — Phase 3 supplies an empty context; later phases fill it.
  3. `engine.decide` → `Result<String>` → if success, pipe through `validator.parseAndValidate(raw)` → `Result<FunctionCall>`.
  4. **Success** (`Result.success(call)`): set `listeningStateFlow.value = Speaking(speakText)` where `speakText = "${getString(copy_recognized_prefix)}${actionDescription(call.action)}"` (e.g. "Reconocido: decir la hora"); emit `LauncherSideEffect.ShowDebugJson(prettyJson)` (consumed by the `ListeningOverlay`); call `ttsClient.speak(speakText)` → on completion, return to `Idle`.
  5. **Failure** (any `CurroError`): set `listeningStateFlow.value = Speaking(fallbackText)` where `fallbackText = getString(copy_error_unknown_function)`; call `ttsClient.speak(fallbackText)` → on completion, return to `Idle`. Log a Curro/FailedCommand line (see below).
- [ ] **Action-description map** (7 entries, one per Fase-1 function) — added to `strings.xml` as 7 new resources:
  - `copy_action_tell_time` = "decir la hora"
  - `copy_action_open_app` = "abrir una app"
  - `copy_action_calculate` = "calcular"
  - `copy_action_help` = "ayuda"
  - `copy_action_read_last_whatsapp` = "leer el último mensaje"
  - `copy_action_read_all_unread_whatsapp` = "leer todos los mensajes"
  - `copy_action_call_contact` = "llamar a un contacto"
  - The mapping `actionName → @StringRes` lives in a private `Map<String, Int>` on `LauncherViewModel` (or a small `ActionDescription` object in `domain/catalog/`); pick the simpler.
- [ ] **New COPY** entries:
  - `copy_recognized_prefix` = "Reconocido: " — added to `strings.xml`. (The trailing space is load-bearing — it's the join character.)
  - `copy_error_unknown_function` = "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'." — **check `strings.xml` first**: if US-005's 54-entry COPY table already shipped this string under any ID, reuse that ID and don't add a duplicate; if not, add it under this ID. The brief flags this for the developer.
- [ ] **Debug-only JSON overlay**: `ListeningOverlay` extended with an optional `debugJson: String?` parameter (defaults to `null`). When `BuildConfig.DEBUG && state is Processing` and `debugJson != null`, the overlay renders the pretty-printed JSON in a small `Text(...)` block below the transcript area, `MaterialTheme.typography.bodyMedium`, `fontFamily = FontFamily.Monospace`, `color = MaterialTheme.colorScheme.onSurfaceVariant`. Release builds never render this (compile-time guard via `if (BuildConfig.DEBUG)`).
- [ ] `LauncherSideEffect` gains `data class ShowDebugJson(val prettyJson: String) : LauncherSideEffect` — Phase 5 removes it; Phase 3 wires it.
- [ ] **`Log.w("Curro/FailedCommand", ...)` line on every validator failure** — format: `"action=<actionOrNull> error=<CurroError class simpleName> utterance.len=<int>"`. **The utterance text itself is NEVER in this log line, NEVER in a telemetry event** — only its length. The full utterance lives in the local-only failed-commands log that ships in Phase 7 (Room-backed). Phase 3's `Log.w` is a placeholder that goes only to `logcat`. The brief loud-calls this distinction.
- [ ] **Telemetry that IS allowed** (via `TelemetryGuardrail` from US-008): `telemetry.event("model_decide", mapOf("model" to "function_gemma_270m", "outcome" to "success" | "invalid_json" | "unknown_function" | "model_cold" | "oom", "latency_ms" to ms))`. Model name + outcome label + latency only. **Never the utterance, never the action name, never any param value.** The outcome label "success" is fine; "tell_time" is NOT.
- [ ] **Cold-engine path** (`CurroError.ModelCold`): the friendly line for cold-model is `copy_models_not_ready` ("Aún estoy preparando los modelos, dame un segundo.") — different from the generic invalid-output line. The mapping in the ViewModel switches on the error type: `ModelCold` → `copy_models_not_ready`; `InvalidFunctionCall` / `UnknownFunction(*)` / `OutOfMemory` → `copy_error_unknown_function`.
- [ ] **Acceptance behaviour on the Redmi 15** (manual, with weights side-loaded per US-019):
  - Press → "qué hora es" → JSON on screen (`{"action": "tell_time", "params": {...}, "confidence": 0.9x}`) + Curro says "Reconocido: decir la hora".
  - Press → "tradúceme esto al italiano" → no JSON (`UnknownFunction("translate")` from the validator OR a low-confidence pass-through to `InvalidFunctionCall`) → Curro says `copy_error_unknown_function`.
  - 10 successive runs of "qué hora es": all `decide` latencies under 500 ms warm — verifiable in `adb logcat -s Curro/Llm` showing 10 `decide latency: <ms>` lines, all < 500.
  - Press during processing → barge-in cancels the in-flight `decide` (via `voiceJob.cancel()` — `withContext(io)` is cancellable; the MediaPipe call may finish but its result is ignored). The state returns to `Listening`. **No crash, no stuck Processing**.
- [ ] **Unit tests** in `LauncherViewModelTest.kt`:
  - `Listening(final) → Processing(transcript) → Speaking(echo) → Idle` happy path with `FakeFunctionCallEngine` returning valid `tell_time` JSON.
  - `Processing → Speaking(copy_error_unknown_function) → Idle` for each `CurroError` (`ModelCold` → `copy_models_not_ready`; `InvalidFunctionCall` → `copy_error_unknown_function`; `UnknownFunction("x")` → `copy_error_unknown_function`; `OutOfMemory` → `copy_error_unknown_function`).
  - Barge-in during `Processing` cancels the `decide` job and restarts listening.
  - Each test asserts the `Log.w("Curro/FailedCommand", ...)` line contains `utterance.len=` and **does NOT contain the utterance text** (Mockk's `verify { Log.w(...) }` with an arg-captor and a substring check).
- [ ] No new permissions; no manifest change beyond what US-023 already added (FGS-related); no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: M  ·  **Depends on**: US-020 (`FunctionCallEngine`), US-022 (`FunctionCallValidator`), US-017 (existing `ListeningState` + voice loop), US-018 (`ListeningOverlay` to extend), US-023 (warm-up service makes the latency budget realistic, but US-024 still works without it — just slower on first press)

---

## Phase 4 — Fase 1 handlers _(spec §5 Fase 1, §14 step 4 — in order)_

> Low-risk first: `tell_time` · `open_app` (`QUERY_ALL_PACKAGES`) · `calculate` ·
> `help`. Then sensitive permissions: `read_last_whatsapp` · `read_all_unread_whatsapp`
> (`NotificationListenerService`, robust parser + tests + fallback) · `call_contact`
> (`READ_CONTACTS` + `CALL_PHONE`, contact/alias resolution).

### US-025: `FunctionHandler` interface + `HandlerResult` sealed + Hilt multibinding  ·  _(master-plan SF-4.1, spec §4.5, function-catalog skill)_
**As a** Curro developer, **I want** a `FunctionHandler` interface in `domain/handler/`, a `HandlerResult` sealed contract, a `HandlerDispatcher` reading a Hilt multibinding map keyed by catalog function name, and `LauncherViewModel`'s SF-3.6 smoke loop rewired to dispatch through it (replacing the `"Reconocido: <action_label>"` echo) **so that** every Phase-4 handler SF lands by appending a single `@Binds @IntoMap @StringKey("…")` line and writing its handler — no glue code repeated, no central `when` statement to keep in sync with the catalog.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/domain/handler/FunctionHandler.kt` — pure Kotlin: `interface FunctionHandler { val functionName: String; suspend fun handle(call: FunctionCall): HandlerResult }`.
- [ ] `app/src/main/java/com/curro/app/domain/handler/HandlerResult.kt` — pure Kotlin: `sealed interface HandlerResult { data class Spoken(val speech: String, val screen: AssistantScreen? = null) : HandlerResult; data class NeedsConfirmation(val prompt: String, val onConfirm: suspend () -> HandlerResult) : HandlerResult; data class Failed(val speech: String, val reason: CurroError) : HandlerResult }`.
- [ ] `app/src/main/java/com/curro/app/domain/handler/AssistantScreen.kt` — pure Kotlin: `sealed interface AssistantScreen { /* Phase 5 populates: messages, contact picker, etc. */ }` — provisional empty marker; Phase 5 (SF-5.1) replaces with the FSM screens.
- [ ] `app/src/main/java/com/curro/app/domain/handler/HandlerDispatcher.kt` — `@Singleton class HandlerDispatcher @Inject constructor(private val handlers: Map<String, @JvmSuppressWildcards FunctionHandler>) { suspend fun dispatch(call: FunctionCall): HandlerResult }`. Unknown action → `Failed(copy_error_unknown_function, CurroError.UnknownFunction(call.action))`. Handler throws → `Failed(copy_handler_crash, CurroError.HandlerCrash(call.action, e))`.
- [ ] `app/src/main/java/com/curro/app/di/HandlerModule.kt` — `@Module @InstallIn(SingletonComponent::class) abstract class HandlerModule { /* @Binds @IntoMap @StringKey("name") abstract fun bind<X>(impl: <X>Handler): FunctionHandler — appended per handler SF; this SF ships the empty module + a `@Multibinds abstract fun handlerMap(): Map<String, FunctionHandler>` so the empty-map graph is valid */ }`.
- [ ] `LauncherViewModel.handleDecisionSuccess(...)` no longer constructs `"Reconocido: " + actionDescription(...)` for TTS. Instead: `val result = dispatcher.dispatch(call); render(result)`. `render`: `Spoken(speech, _) → tts.speak(speech) → Idle`; `NeedsConfirmation(prompt, onConfirm) → onConfirm()` immediately + recurse (Phase 6 inserts the policy gate); `Failed(speech, reason) → tts.speak(speech) + Log.w("Curro/FailedCommand", "action=${call.action} error=${reason::class.simpleName} utterance.len=${transcript.length}") → Idle`. The debug JSON overlay (`LauncherSideEffect.ShowDebugJson`) is preserved.
- [ ] `LauncherViewModel.ACTION_DESCRIPTION_MAP` and `actionDescription(...)` REMOVED — the dispatcher's `Spoken.speech` is now the source of truth for what Curro says. The 7 `copy_action_*` strings stay in `strings.xml` (no orphan-cleanup this SF — Phase 5 reviews) but are unreferenced from production code.
- [ ] New `CurroError` variant: `data class HandlerCrash(val functionName: String, val cause: Throwable) : CurroError()` — appended to `domain/model/CurroError.kt`.
- [ ] `UnknownFunction` already exists from Phase 3 — verified.
- [ ] New `strings.xml` entry: `copy_handler_crash` = `"Algo se ha torcido por dentro. Inténtalo otra vez en un momento."` — Curro voice: brief, honest, offers retry, no code.
- [ ] `TelemetryGuardrail.ALLOWED_PROPS` extended (in same PR per the privacy-gate contract): `"handler_invoked" to setOf("function_name", "outcome")` — `outcome ∈ {success, needs_confirmation, failed, crash}`. Wired in `HandlerDispatcher.dispatch`: telemetry event on every dispatch (before+after handler call). **Never** the utterance, never any param value.
- [ ] `TelemetryGuardrailTest` fixtures added: allow `function_name=tell_time`, allow `outcome=success`, reject `function_name=<full sentence longer than 32 chars>` (transcript-shaped value), reject extra key `phone_number`.
- [ ] JVM unit tests in `app/src/test/java/com/curro/app/domain/handler/HandlerDispatcherTest.kt` (≥ 6 cases): empty handler map + any action → `Failed(UnknownFunction)`; map with 1 fake handler → that handler invoked; unknown action with non-empty map → `Failed(UnknownFunction)`; handler returns `Spoken` → propagated; handler returns `NeedsConfirmation` → propagated unchanged (Phase 6's policy wraps it); handler throws → `Failed(HandlerCrash)` with `cause` matching.
- [ ] JVM unit tests added to `LauncherViewModelTest.kt` (≥ 3 cases): dispatcher returns `Spoken("Son las doce")` → TTS speaks `"Son las doce"`, state returns to Idle; dispatcher returns `Failed("No tengo ninguna app que se llame así", AppNotFound("foobar"))` → TTS speaks the Spanish, `Log.w("Curro/FailedCommand", ...)` line contains `error=AppNotFound utterance.len=N` and **does NOT contain the utterance text**; dispatcher returns `NeedsConfirmation` → `onConfirm()` invoked and its result rendered (Phase-4 "auto-confirm" behaviour pinned by test).
- [ ] No new permissions, no manifest changes, no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: S  ·  **Depends on**: US-024 (`LauncherViewModel`'s smoke loop is the rewire point), US-022 (`FunctionCall`).

---

### US-026: `tell_time` handler  ·  _(master-plan SF-4.2, spec §5 (tell_time entry), §6 flow 6)_
**As** Fran's father, **I want** Curro to tell me the time, the day or the date in plain colloquial Castilian when I ask **so that** I get an honest sentence ("Son las doce y cuarenta y siete del miércoles trece de mayo") instead of a digital readout on a screen I can barely see.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/handler/TellTimeHandler.kt` — `class TellTimeHandler @Inject constructor(private val clock: Clock, @ApplicationContext private val context: Context) : FunctionHandler { override val functionName = "tell_time"; override suspend fun handle(call: FunctionCall): HandlerResult }`. The `Clock` defaults to `Clock.systemDefaultZone()` via a Hilt `@Provides` in a new `app/src/main/java/com/curro/app/di/TimeModule.kt` (tests override with `Clock.fixed(...)`).
- [ ] `app/src/main/java/com/curro/app/handler/time/SpanishTimeFormatter.kt` — pure Kotlin helper: `formatTime(now: LocalDateTime): String`, `formatDay(now: LocalDateTime): String`, `formatDate(now: LocalDateTime): String`. **All Spanish word-form**, never digits.
- [ ] Time format — **simple "y minutes" form** (decision pinned: no "menos cuarto/diez" for Phase 4 — bounded surface, the spec's flow-6 example is "las doce y cuarenta y siete"):
  - Hour: `"la una"` for hour==1, `"las dos"` … `"las doce"` (12-hour clock, no AM/PM).
  - Minutes: 0 → `"en punto"` ("la una en punto"); 1–59 → `"y <minutes-in-words>"` ("las doce y cuarenta y siete"). Special: 15 → `"y cuarto"`; 30 → `"y media"`; 45 → `"y cuarenta y cinco"` (NOT "menos cuarto" — pinned).
- [ ] Day format: `"el <weekday-lowercase>"` — `lunes, martes, miércoles, jueves, viernes, sábado, domingo`.
- [ ] Date format: `"<day-in-words> de <month-lowercase> de <year-in-words>"` — `"trece de mayo de dos mil veintiséis"`. Months: `enero, febrero, marzo, abril, mayo, junio, julio, agosto, septiembre, octubre, noviembre, diciembre`. (Note: years are spelled out — pin in brief.)
- [ ] `handle(call)` reads `call.params["what"] as? String ?: "all"` (defaults to "all" per spec §5):
  - `"time"` → `"Son <time>."` or `"Es <time>."` for "la una"; uses `copy_time_now`.
  - `"day"` → `"Hoy es <day>."` — composed at runtime (no positional resource — the day phrase is composed; the wrapping uses a new `copy_time_day` entry).
  - `"date"` → `"Hoy es <date>."` — uses `copy_time_date` (the existing `%1$s, %2$s` template gets `day, date`; for `what="date"` the day is included by spec because the user wants "today" anchored).
  - `"all"` → `"Son <time> del <day> <date>."`  — composed at runtime; uses a new `copy_time_all` template `Son %1$s del %2$s %3$s.`.
  - Unknown `what` value → fall through to `"all"` (validator already rejects out-of-enum, so this is defensive).
- [ ] Result is always `HandlerResult.Spoken(speech)`. Never `Failed`, never `NeedsConfirmation` (this handler is unconditional per spec).
- [ ] New `strings.xml` entries (verify against the file before adding to avoid duplicates):
  - `copy_time_now` = `"Son las %1$s."` — verify exists; the existing entry uses `%1$s = time string`; the handler builds the full Spanish time and passes it. (For "la una": the handler emits the verbatim `"Es la una."` via a separate constant — pin in brief — or a sibling `copy_time_one` entry; choose the sibling for resource hygiene.)
  - `copy_time_one` = `"Es %1$s."` — NEW (for hour==1).
  - `copy_time_day` = `"Hoy es %1$s."` — NEW.
  - `copy_time_all` = `"Son %1$s del %2$s %3$s."` — NEW.
  - `copy_time_date` already exists (`"Hoy es %1$s, %2$s."`) — REUSED.
- [ ] **Hilt binding**: append to `HandlerModule.kt`:
  ```kotlin
  @Binds @IntoMap @StringKey("tell_time")
  abstract fun bindTellTime(impl: TellTimeHandler): FunctionHandler
  ```
- [ ] JVM tests in `app/src/test/java/com/curro/app/handler/TellTimeHandlerTest.kt` (≥ 15 cases) with `Clock.fixed(...)` — every `what` value, midnight ("Son las doce en punto"), noon, 01:00 ("Es la una en punto"), 02:30 ("Son las dos y media"), 03:15 ("Son las tres y cuarto"), 12:47 (spec §6 flow-6 case), every weekday (one each), every month (one each). Tests use a `Context` from Robolectric to load the string resources OR pin the resource-id → expected-format expectations via a fake `Context` interface (decision pinned: Robolectric for this SF — `JVM tests` already use it elsewhere).
- [ ] `SpanishTimeFormatterTest.kt` — pure-Kotlin tests for the formatter functions (no Robolectric needed — formatter returns the bare phrase, not the wrapping copy).
- [ ] Spanish-number-from-int helper for 0–59 — co-located in the formatter file as `private fun numberInWords(n: Int): String`. Pin the helper's table verbatim in the brief: `cero, uno, dos, tres, cuatro, cinco, seis, siete, ocho, nueve, diez, once, doce, trece, catorce, quince, dieciséis, diecisiete, dieciocho, diecinueve, veinte, veintiuno, veintidós, veintitrés, veinticuatro, veinticinco, veintiséis, veintisiete, veintiocho, veintinueve, treinta, treinta y uno, treinta y dos, …, cincuenta y nueve`. (Generated from 20–59 by `tens + " y " + units`.)
- [ ] No new permissions, no manifest changes, no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: S  ·  **Depends on**: US-025 (`FunctionHandler` interface + `HandlerModule`).

---

### US-027: `open_app` handler + `AppLauncher` + colloquial alias map  ·  _(master-plan SF-4.3, spec §5 (open_app entry), platform-integrations PackageManager section)_
**As** Fran's father, **I want** to say "abre WhatsApp" or "ponme las fotos" or "abre la cámara" and have the right app open **so that** I don't have to find the right icon on a grid full of look-alike icons.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/data/apps/AppLauncher.kt` — `class AppLauncher @Inject constructor(@ApplicationContext private val context: Context) { fun launch(packageName: String): Boolean }`. Returns `true` if `getLaunchIntentForPackage(packageName)` is non-null AND the `startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))` succeeds; `false` otherwise. Catches `ActivityNotFoundException` and `SecurityException`.
- [ ] `app/src/main/java/com/curro/app/data/apps/ColloquialAppAliases.kt` — pure-Kotlin singleton:
  ```kotlin
  object ColloquialAppAliases {
      val byColloquialName: Map<String, List<String>> = mapOf(
          "whatsapp" to listOf("com.whatsapp"),
          "wasap" to listOf("com.whatsapp"),
          "guasap" to listOf("com.whatsapp"),
          "guasá" to listOf("com.whatsapp"),
          "la cámara" to listOf("com.android.camera", "com.android.camera2", "com.miui.camera"),
          "cámara" to listOf("com.android.camera", "com.android.camera2", "com.miui.camera"),
          "las fotos" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
          "fotos" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
          "la galería" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
          "galería" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
          "el correo" to listOf("com.google.android.gm", "com.samsung.android.email.provider"),
          "correo" to listOf("com.google.android.gm", "com.samsung.android.email.provider"),
          "gmail" to listOf("com.google.android.gm"),
          "el teléfono" to listOf("com.google.android.dialer", "com.android.dialer", "com.android.contacts"),
          "teléfono" to listOf("com.google.android.dialer", "com.android.dialer"),
          "los contactos" to listOf("com.android.contacts", "com.google.android.contacts"),
          "contactos" to listOf("com.android.contacts", "com.google.android.contacts"),
          "los mensajes" to listOf("com.google.android.apps.messaging", "com.android.messaging"),
          "mensajes" to listOf("com.google.android.apps.messaging", "com.android.messaging"),
          "ajustes" to listOf("com.android.settings"),
          "los ajustes" to listOf("com.android.settings"),
          "configuración" to listOf("com.android.settings"),
          "youtube" to listOf("com.google.android.youtube"),
          "calculadora" to listOf("com.google.android.calculator", "com.android.calculator2"),
          "la calculadora" to listOf("com.google.android.calculator", "com.android.calculator2"),
          "el reloj" to listOf("com.android.deskclock", "com.google.android.deskclock"),
          "reloj" to listOf("com.android.deskclock", "com.google.android.deskclock"),
          "el navegador" to listOf("com.android.chrome", "org.mozilla.firefox"),
          "chrome" to listOf("com.android.chrome"),
      )
  }
  ```
  Keys are lowercase, accent-preserved (the handler normalises the input the same way).
- [ ] `app/src/main/java/com/curro/app/handler/OpenAppHandler.kt`:
  ```kotlin
  class OpenAppHandler @Inject constructor(
      private val installedApps: InstalledAppsRepository,
      private val launcher: AppLauncher,
      @ApplicationContext private val context: Context,
  ) : FunctionHandler {
      override val functionName = "open_app"
      override suspend fun handle(call: FunctionCall): HandlerResult { /* see algorithm */ }
  }
  ```
- [ ] **Resolution algorithm** — pinned:
  1. `val raw = (call.params["app_name"] as? String).orEmpty().trim().lowercase(Locale("es"))`. Empty → `Failed(copy_app_not_found("…"), AppNotFound(""))`.
  2. **Alias lookup**: for each entry in `ColloquialAppAliases.byColloquialName` whose key equals `raw` → pick the first candidate package that is installed (verified via `installedApps.observeAllLaunchable().first().any { it.packageName == candidate }`). On hit → `launcher.launch(pkg)` → `Spoken(context.getString(copy_app_opening, label))`.
  3. **Fuzzy match** against installed labels: `installedApps.observeAllLaunchable().first()`; normalise each label with `.lowercase(Locale("es")).normalizeAccents()`; then:
     - If exactly one label CONTAINS `raw` (after normalisation) → launch it.
     - Otherwise, Levenshtein-distance against each label; threshold pinned to ≤ 3 for `raw` length ≥ 4 (strings shorter than 4 chars get `containsOnly` — `Levenshtein` on 2-char strings is meaningless). Candidates with distance ≤ threshold form the candidate set.
  4. **Outcome**:
     - 0 candidates → `Failed(context.getString(copy_app_not_found, raw), CurroError.AppNotFound(raw))`.
     - 1 candidate → launch it → `Spoken(context.getString(copy_app_opening, label))`.
     - ≥ 2 candidates → `Failed(context.getString(copy_app_ambiguous), CurroError.AmbiguousApp(candidates))`.
- [ ] `app/src/main/java/com/curro/app/data/apps/StringNormalization.kt` — pure-Kotlin helpers: `fun String.normalizeAccents(): String` (NFD + strip diacritics, then NFC), `fun levenshtein(a: String, b: String): Int` (classic 2-row DP, O(n*m) time, O(min) space).
- [ ] **`AmbiguousApp(matches: List<LaunchableApp>)`** — NEW `CurroError` variant. (`LaunchableApp` already exists in `domain/model/`.)
- [ ] **`AppNotFound(query: String)`** — NEW `CurroError` variant.
- [ ] `strings.xml`:
  - `copy_app_opening` — already exists (`"Abriendo %1$s."`); REUSED.
  - `copy_app_not_found` — already exists (`"No tengo ninguna app que se llame así."`) — note: **no `%1$s` arg today**. **Add a sibling** `copy_app_not_found_named` = `"No tengo ninguna app que se llame %1$s."` for the named case; keep the existing entry for the empty case. Pin in brief.
  - `copy_app_ambiguous` — NEW: `"Tengo varias apps que se llaman así, prueba con el nombre exacto."`
- [ ] `TelemetryGuardrail.ALLOWED_PROPS` — `"handler_invoked"` already added in US-025; this SF requires no whitelist change (the dispatcher's event covers `function_name=open_app` and `outcome=success|failed`; **the app label, package name, query are NEVER on the wire**).
- [ ] **Hilt binding**: append to `HandlerModule.kt`:
  ```kotlin
  @Binds @IntoMap @StringKey("open_app")
  abstract fun bindOpenApp(impl: OpenAppHandler): FunctionHandler
  ```
- [ ] JVM tests in `app/src/test/java/com/curro/app/handler/OpenAppHandlerTest.kt` (≥ 15 cases) with a fake `InstalledAppsRepository` returning a curated `List<LaunchableApp>` and a fake `AppLauncher` (interface-wrapped — promote `AppLauncher` to an interface + impl if needed to avoid Robolectric in this SF; pin decision in brief: **promote** so the test stays pure JVM). Cases: exact alias `whatsapp`, accent-variant `cámara`, multi-word `la cámara` resolves to first installed candidate, fuzzy `calc` → calculadora (Levenshtein 3), case insensitive `WHATSAPP`, no match `pepito`, multiple matches `mensajes` when both `com.android.messaging` and `com.google.android.apps.messaging` are listed in the alias map (alias path → first-installed wins, NOT ambiguous), fuzzy ambiguity (two installed apps with distance ≤ threshold), empty `app_name`, accent stripping (`"camara"` → matches `"cámara"` after normalisation), `whatsapp.w4b` business variant absent → falls through to `com.whatsapp`.
- [ ] `StringNormalizationTest.kt` — Levenshtein + accent-strip table tests (≥ 10 cases each).
- [ ] No new permissions (`QUERY_ALL_PACKAGES` already declared by SF-1.4); no manifest changes; no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: M  ·  **Depends on**: US-025 (handler interface), US-013 (`InstalledAppsRepository`).

---

### US-028: `calculate` handler + Spanish-number expression parser  ·  _(master-plan SF-4.4, spec §5 (calculate entry))_
**As** Fran's father, **I want** to ask Curro `"cuánto es cuarenta y siete por ocho"` and hear `"Cuarenta y siete por ocho son trescientos setenta y seis"` **so that** I don't have to find and open the calculator app to do small sums.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/handler/calculator/SpanishExpressionParser.kt` — pure-Kotlin parser. Two phases:
  1. **Tokenize** — split on whitespace + a small punctuation regex (`,` `.` are stripped); fold Spanish number-words into integer operands; map operator-words to operator tokens.
  2. **Evaluate** — flat left-to-right pass with **no operator precedence** (decision pinned: Phase 4 expressions are typically one operator; the spec §5 examples all match — adding precedence inflates the bug surface for zero user benefit). Handles a special percent form: `el N por ciento de M` → `(N/100)*M`.
- [ ] **Spanish number table** (verbatim, hand-coded) in `SpanishNumbers.kt`:
  - 0–15: `cero, uno, dos, tres, cuatro, cinco, seis, siete, ocho, nueve, diez, once, doce, trece, catorce, quince`.
  - 16–19: `dieciséis, diecisiete, dieciocho, diecinueve` (accept also `diez y seis` etc. — the tokenizer recognises the multi-word form).
  - 20–29: `veinte, veintiuno, veintidós, veintitrés, veinticuatro, veinticinco, veintiséis, veintisiete, veintiocho, veintinueve`.
  - 30, 40, …, 90: `treinta, cuarenta, cincuenta, sesenta, setenta, ochenta, noventa`. + `y <unit>` → 31–39, 41–49, etc.
  - 100, 200, …, 900: `cien` (only on its own, for 100 exactly), `ciento` (used inside compound: `ciento treinta y cinco`), `doscientos, trescientos, cuatrocientos, quinientos, seiscientos, setecientos, ochocientos, novecientos`.
  - 1000, 2000, …, 9000: `mil, dos mil, tres mil, …, nueve mil`. (`mil` alone = 1000.)
  - **Out of scope, pinned**: millones, billones (negative numbers, decimals, fractions). The parser returns `Calculation` failure for those.
- [ ] **Operator table**:
  - Multiplication: `por` | `multiplicado por` | `x` → `*`.
  - Division: `entre` | `dividido entre` | `dividido por` → `/`.
  - Addition: `más` | `mas` | `y` (only between operands, never inside a number) | `suma` (treated as the leading operator of `cuánto suma X y Y`) | `sumado a` → `+`.
  - Subtraction: `menos` | `resta` → `-`.
  - Percent: regex `el <number> por ciento de <number>` matched **before** general tokenization; emitted as a single percent-token.
- [ ] **Output formatting** — `intToSpanishWords(n: Int): String` (also in `SpanishNumbers.kt`): the inverse of the table — handles 0..999_999 (sufficient given inputs cap at 9_999_999 from `nueve mil * nueve mil`; cap output at 9_999_999, return `Calculation` failure beyond). Pin in brief: `intToSpanishWords(376) = "trescientos setenta y seis"`, `intToSpanishWords(40) = "cuarenta"`, `intToSpanishWords(42) = "cuarenta y dos"`, `intToSpanishWords(38) = "treinta y ocho"`.
- [ ] `app/src/main/java/com/curro/app/handler/CalculateHandler.kt`:
  ```kotlin
  class CalculateHandler @Inject constructor(
      private val parser: SpanishExpressionParser,
      @ApplicationContext private val context: Context,
  ) : FunctionHandler {
      override val functionName = "calculate"
      override suspend fun handle(call: FunctionCall): HandlerResult { /* see flow */ }
  }
  ```
- [ ] **Flow**:
  1. `val expr = (call.params["expression"] as? String).orEmpty().trim().lowercase(Locale("es"))`. Empty → `Failed(copy_calc_failed, Calculation(expr, "empty"))`.
  2. `val result: Result<ParsedExpression> = parser.parse(expr)`. Failure → `Failed(copy_calc_failed, Calculation(expr, "parse"))`.
  3. `val value: Result<Long> = result.getOrNull()!!.evaluate()`. Division-by-zero → `Failed(copy_calc_div_zero, Calculation(expr, "div_zero"))`. Overflow > 9_999_999 → `Failed(copy_calc_failed, Calculation(expr, "overflow"))`.
  4. Success → `Spoken(context.getString(copy_calc_result, expr, intToSpanishWords(value)))` — uses the existing `copy_calc_result` template `"%1$s son %2$s."`.
- [ ] `ParsedExpression` data class: holds an operator + two operands; or a percent-form (n, m). `evaluate(): Result<Long>` does the math.
- [ ] **New `CurroError` variant**: `data class Calculation(val expression: String, val reason: String) : CurroError()` — `reason ∈ {empty, parse, div_zero, overflow}`. (Verify: the existing `CurroError.kt` does NOT contain `Calculation`; add it.)
- [ ] `strings.xml`:
  - `copy_calc_result` — already exists (`"%1$s son %2$s."`) — REUSED.
  - `copy_calc_failed` — already exists (`"No he podido hacer ese cálculo. ¿Lo repites más despacio?"`) — REUSED.
  - `copy_calc_div_zero` — NEW: `"No puedo dividir entre cero."`
- [ ] **Hilt binding**: append to `HandlerModule.kt`:
  ```kotlin
  @Binds @IntoMap @StringKey("calculate")
  abstract fun bindCalculate(impl: CalculateHandler): FunctionHandler
  ```
- [ ] JVM tests in `app/src/test/java/com/curro/app/handler/CalculateHandlerTest.kt` (≥ 30 cases). The **4 spec §5 examples** MUST pass verbatim:
  - `"cuánto es cuarenta y siete por ocho"` → "Cuarenta y siete por ocho son trescientos setenta y seis."
  - `"calcula mil dividido entre veinticinco"` → "Mil dividido entre veinticinco son cuarenta."
  - `"cuánto suma quince y veintitrés"` → "Quince y veintitrés son treinta y ocho."
  - `"el veintiuno por ciento de doscientos"` → "El veintiuno por ciento de doscientos son cuarenta y dos."
  - Additional cases: division-by-zero ("cinco entre cero" → `copy_calc_div_zero`); parse error ("cuántos billones tiene pepito" → `copy_calc_failed`); overflow (`mil por mil por mil` → `copy_calc_failed`); single-number expression ("cuarenta y siete" → `copy_calc_failed` — needs an operator); empty; subtraction ("diez menos tres"); the "más"/"y" ambiguity ("cinco y tres" → 8, "cinco más tres" → 8); accent variants ("decimoseis" is NOT a Spanish word — should fail; but "dieciseis" without accent should match `dieciséis` via the accent-strip normaliser); large compound ("doscientos cincuenta por tres" = 750).
- [ ] `SpanishNumbersTest.kt` — round-trip table (`intToSpanishWords` × parse) for representative ints in `[0, 9_999_999]`.
- [ ] No new permissions, no manifest changes, no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green; **30+ calculator tests pass**.

**Size**: M  ·  **Depends on**: US-025.

---

### US-029: `help` handler  ·  _(master-plan SF-4.5, spec §5 (help entry))_
**As** Fran's father, **I want** to ask Curro "ayuda" or "qué sabes hacer" and hear the short list of what it can do today, in its own voice **so that** I'm not forced to remember a phrasebook to use the phone.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/handler/HelpHandler.kt` — `class HelpHandler @Inject constructor(@ApplicationContext private val context: Context) : FunctionHandler`.
- [ ] **Behavior**:
  1. `val topic = (call.params["topic"] as? String).orEmpty().trim().lowercase(Locale("es")).normalizeAccents()`.
  2. If `topic.isEmpty()` → `Spoken(context.getString(copy_help_generic))` — uses existing entry.
  3. Else map `topic` to a sibling `copy_help_topic_*` string:
     - `"llamada" | "llamadas" | "llamar" | "telefono"` → `copy_help_topic_call` (exists).
     - `"mensajes" | "mensaje" | "whatsapp" | "wasap"` → `copy_help_topic_whatsapp` (exists).
     - `"app" | "apps" | "aplicacion" | "aplicaciones"` → `copy_help_topic_app` (exists).
     - `"calculo" | "calcular" | "cuentas" | "cuenta" | "matematicas"` → `copy_help_topic_calculate` (NEW).
     - `"hora" | "dia" | "fecha"` → `copy_help_topic_time` (NEW).
     - Anything else (including `"ayuda"` self-referential) → `copy_help_generic`.
- [ ] `strings.xml`:
  - `copy_help_generic` — already exists; REUSED.
  - `copy_help_topic_call` — already exists; REUSED.
  - `copy_help_topic_whatsapp` — already exists; REUSED.
  - `copy_help_topic_app` — already exists; REUSED.
  - `copy_help_topic_calculate` — NEW: `"Para hacer cuentas, pulsa el botón y dime la operación con palabras: \"cuánto es cuarenta y siete por ocho\", \"calcula mil dividido entre veinticinco\"."`
  - `copy_help_topic_time` — NEW: `"Para saber la hora, el día o la fecha, pulsa el botón y di \"qué hora es\", \"qué día es hoy\" o \"qué fecha es\"."`
- [ ] **Phase-aware text**: the generic line lists Phase-4 Fase-1 functions only; the brief flags that Phase 5+ updates this string when new functions land. No code-side phase-detection (decision pinned: the string IS the phase contract).
- [ ] **Hilt binding**: append to `HandlerModule.kt`:
  ```kotlin
  @Binds @IntoMap @StringKey("help")
  abstract fun bindHelp(impl: HelpHandler): FunctionHandler
  ```
- [ ] JVM tests in `app/src/test/java/com/curro/app/handler/HelpHandlerTest.kt` (≥ 8 cases) with a Robolectric `Context`: no topic, `"llamadas"` → call line, `"whatsapp"` → whatsapp line, `"apps"` → apps line, `"cuentas"` → calculate line, `"hora"` → time line, accent-stripping (`"matemáticas"` → calculate), unknown topic (`"el tiempo"` → generic).
- [ ] No new permissions, no manifest changes, no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: S  ·  **Depends on**: US-025.

---

### US-030: Notification access infrastructure + `WhatsAppNotificationParser`  ·  _(master-plan SF-4.6, spec §5 (read_*_whatsapp entries), spec §10, spec §14 "Riesgos identificados", platform-integrations skill rule 2)_
**As** Fran's father, **I want** Curro to know what's new on WhatsApp on my phone — and **as** Fran, **I want** the parser that extracts that to be a fixture-tested fortress **so that** SF-4.7/SF-4.8's handlers always read either real content or a clean "no he podido leerlo" — never silence, never invented words. This is the highest-risk piece in the prototype per master-plan §Risks.

**Acceptance Criteria**:
- [ ] `app/src/main/AndroidManifest.xml` — append:
  ```xml
  <uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
      tools:ignore="ProtectedPermissions" />
  ```
  and inside `<application>`:
  ```xml
  <service
      android:name=".data.notification.CurroNotificationListenerService"
      android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
      android:exported="true">
      <intent-filter>
          <action android:name="android.service.notification.NotificationListenerService" />
      </intent-filter>
  </service>
  ```
- [ ] `app/src/main/java/com/curro/app/data/notification/CurroNotificationListenerService.kt` — `@AndroidEntryPoint class CurroNotificationListenerService : NotificationListenerService()`. Filters `sbn.packageName ∈ {com.whatsapp, com.whatsapp.w4b}` (WhatsApp + WhatsApp Business). `onNotificationPosted` parses + `cache.upsert` or `cache.recordParseMiss(sbn.key)`. `onNotificationRemoved` calls `cache.onRemoved(sbn.key)`. Both methods immediately hop to `ioScope.launch { … }` — Android calls them on main; the cache update may touch a StateFlow but the parser work is bounded ms — pin the `ioScope` use anyway as a habit for Phase-7's Room swap.
- [ ] `app/src/main/java/com/curro/app/data/notification/WhatsAppNotificationParser.kt` — `@Singleton class WhatsAppNotificationParser @Inject constructor() { fun parse(sbn: StatusBarNotification): WhatsAppMessage? }`. **Three-tier algorithm** (defensive — `platform-integrations` rule 2):
  1. **Tier 1 — `MessagingStyle`**: `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)` → if non-null AND `messages.isNotEmpty()`:
     - For 1:1 (`isGroupConversation == false`): `chatTitle = conversationTitle ?: messages.last().person?.name ?: extractTitle(extras)`. `sender = chatTitle`. Take **the last** message per `read_last_whatsapp`; for `read_all_unread_whatsapp` the cache exposes all.
     - For group chats (`isGroupConversation == true`): `chatTitle = conversationTitle`; for each `message`, `sender = message.person?.name`; emit one `WhatsAppMessage` per `message` with `isGroup = true`.
     - Classify each message body:
       - emoji-only (regex `^[\p{So}\p{Cn}\p{Sk}\p{Mn}\p{Cf}\s]+$`) → `Classification.EMOJI`
       - body matches `"🎤 ?[Vv]oice message"` or `"\\[Voice message\\]"` (locale variants) OR `extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.contains("Voice message")` → `VOICE_NOTE`
       - body matches `"📷 ?[Ii]mage"` or `"\\[Photo\\]"` or `"📷"` only → `IMAGE`
       - otherwise → `TEXT`.
  2. **Tier 2 — Legacy `extras`**: if Tier 1 returned null AND extras has both `EXTRA_TITLE` and (`EXTRA_TEXT` or `EXTRA_TEXT_LINES`):
     - `sender = extras.getString(EXTRA_TITLE)`. `chatTitle = sender` (group vs 1:1 indistinguishable in this path → treat as 1:1, `isGroup = false`).
     - `text = extras.getString(EXTRA_TEXT) ?: extras.getCharSequenceArray(EXTRA_TEXT_LINES)?.lastOrNull()?.toString() ?: return null`.
     - Classify per Tier 1's rules.
  3. **Tier 3 — Summary notifications**: if `sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0` → return null AND `cache.recordSummary(sbn.key, count)` separately so the cache knows there are N unread without knowing their bodies. (This becomes a count-only signal; the unread sender list is still built from the per-message Tier 1/2 notifications that fire alongside.)
  4. **Parse miss**: any other shape → return null. The caller (`CurroNotificationListenerService.onNotificationPosted`) calls `cache.recordParseMiss(sbn.key)`.
- [ ] `app/src/main/java/com/curro/app/domain/model/WhatsAppMessage.kt`:
  ```kotlin
  data class WhatsAppMessage(
      val key: String,           // sbn.key
      val sender: String,        // 1:1 chat title, or Person.name in a group
      val chatTitle: String,     // group name or sender for 1:1
      val text: String,          // body, or "[emoji]" / "[audio]" / "[foto]" markers
      val isGroup: Boolean,
      val timestamp: Long,       // ms epoch from sbn.postTime
      val classification: Classification,
  ) {
      enum class Classification { TEXT, EMOJI, VOICE_NOTE, IMAGE, OTHER }
  }
  ```
- [ ] `app/src/main/java/com/curro/app/domain/repository/NotificationRepository.kt`:
  ```kotlin
  interface NotificationRepository {
      val allUnread: Flow<List<WhatsAppMessage>>
      fun unreadBySender(sender: String): Flow<List<WhatsAppMessage>>
      val parseMissCount: Flow<Int>
      fun clear(sender: String)
  }
  ```
- [ ] `app/src/main/java/com/curro/app/data/notification/UnreadMessageCache.kt` — `@Singleton class UnreadMessageCache @Inject constructor()` implementing `NotificationRepository`. In-memory `MutableStateFlow<Map<String, WhatsAppMessage>>` (keyed by `sbn.key`) + `MutableStateFlow<Int>` for parse-miss count. Phase 7 swaps the impl for a Room-backed one; the interface stays.
- [ ] `app/src/main/java/com/curro/app/di/NotificationModule.kt`:
  ```kotlin
  @Module @InstallIn(SingletonComponent::class)
  abstract class NotificationModule {
      @Binds @Singleton
      abstract fun bindNotificationRepository(impl: UnreadMessageCache): NotificationRepository
  }
  ```
- [ ] **Permission UX** — extend `LauncherUiState` / `LauncherViewModel`:
  - New field `val isNotificationAccessGranted: Boolean`. Detected via `NotificationManagerCompat.getEnabledListenerPackages(context).contains(BuildConfig.APPLICATION_ID)` — read in a fresh `data/permissions/NotificationAccessGate.kt` (interface + impl, same pattern as `RecordAudioPermissionGate.kt`).
  - Re-evaluated on `ON_RESUME` (the user comes back from Settings).
  - `LauncherScreen` renders a "Permitir leer mensajes" `BigPrimaryButton` (uses `copy_grant_notif_access_cta`) **only when**: Curro is the default launcher AND `!isNotificationAccessGranted`. Tap → `Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)` started via a new `LauncherSideEffect.OpenNotificationAccessSettings`. Layout slot pinned: directly below the favourites grid, above "Más apps" — visually equivalent to the SF-1.1 "Hazme tu pantalla de inicio" CTA pattern.
- [ ] `strings.xml`:
  - `copy_no_unread` — already exists (`"No tienes mensajes nuevos."`) — REUSED.
  - `copy_many_unread` — already exists (`"Tienes muchos mensajes…"`) — REUSED.
  - `copy_whatsapp_parse_miss` — already exists (`"Tienes mensajes nuevos pero no he podido leerlos bien."`) — REUSED.
  - `copy_perm_missing_notifs` — already exists (`"Necesito que me dejes leer las notificaciones. Díselo a Fran."`) — REUSED.
  - `copy_grant_notif_access_cta` — NEW: `"Permitir leer mensajes"` — used by the home-screen `BigPrimaryButton`.
- [ ] **`NotificationAccessMissing` `CurroError` variant** — NEW. (SF-4.7/SF-4.8 surface it when the handler runs with no access; the home CTA exists to prevent that path.)
- [ ] **Fixtures**: `app/src/test/java/com/curro/app/data/notification/WhatsAppNotificationFixtures.kt` — builder helpers that construct realistic `StatusBarNotification` instances via Robolectric's `Notification.Builder` shadow. Decision pinned: **use Robolectric** for these tests — `StatusBarNotification`'s constructor is hard-internalised and Robolectric provides the necessary `ShadowNotification`. The fixture file exports named builders per scenario.
- [ ] `app/src/test/java/com/curro/app/data/notification/WhatsAppNotificationParserTest.kt` — ≥ 20 cases:
  1. MessagingStyle 1:1 with one message → `WhatsAppMessage(text=..., isGroup=false, TEXT)`.
  2. MessagingStyle 1:1 with three messages → 3 emissions (or one "last" depending on what the cache stores — pin: cache stores all, parser emits each).
  3. MessagingStyle group chat with two senders → 2 emissions, both `isGroup=true`, different `sender`.
  4. Legacy extras 1:1 → TEXT.
  5. Legacy extras with `EXTRA_TEXT_LINES` (multi-line bundle) → TEXT (last line as `text`).
  6. Summary notification (`FLAG_GROUP_SUMMARY`) → null + a `cache.recordSummary` call.
  7. Emoji-only body (`"❤️"`) → EMOJI.
  8. Voice note (`"🎤 Voice message"`) → VOICE_NOTE.
  9. Voice note via `EXTRA_INFO_TEXT` → VOICE_NOTE.
  10. Image (`"📷 Photo"`) → IMAGE.
  11. BigText style with long body → TEXT (body intact).
  12. WhatsApp Business package (`com.whatsapp.w4b`) → parsed the same as `com.whatsapp`.
  13. Unknown package (`com.example.fake`) → null (parser doesn't filter; the listener does — test the listener separately).
  14. Missing `EXTRA_TITLE`, only `EXTRA_TEXT` → null (Tier 2 needs both).
  15. Missing `EXTRA_TEXT`, only `EXTRA_TITLE` → null.
  16. Null `extras` → null.
  17. MessagingStyle with empty `messages` list → null.
  18. Latin-1 special chars (`"¿Hablamos?"`) → TEXT, body preserved.
  19. Multibyte emoji at start of mixed text (`"🎉 Felicidades"`) → TEXT (not EMOJI — has letters).
  20. Group chat where `Person.name` is null → falls through to TIER 2 — pin behavior.
- [ ] `app/src/test/java/com/curro/app/data/notification/UnreadMessageCacheTest.kt` — ≥ 5 cases: `upsert` then `allUnread.first()` reflects it; `upsert` same key twice → not duplicated; `onRemoved(key)` removes; `recordParseMiss` increments the counter Flow; `unreadBySender` filters correctly.
- [ ] **Permission UX tests**: `LauncherScreenTest` (Robolectric Compose) — CTA visible when access not granted AND Curro is default; hidden when access granted; tap fires `OpenNotificationAccessSettings`.
- [ ] **This SF does NOT include the handlers themselves** — handlers ship in SF-4.7 + SF-4.8.
- [ ] **`TelemetryGuardrail`**: `"handler_invoked"` is already whitelisted (US-025); no new event needed. **Never** log notification bodies, sender names, chat titles, parse-miss text content. The `parseMissCount` is safe (an int).
- [ ] No new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green; the fixture suite is the deliverable.

**Size**: L  ·  **Depends on**: US-025.

---

### US-031: `read_last_whatsapp` handler  ·  _(master-plan SF-4.7, spec §5, spec §6 flow 5)_
**As** Fran's father, **I want** to say "léeme el último mensaje" and hear the latest unread WhatsApp out loud **so that** I don't have to squint at the notification shade.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/handler/ReadLastWhatsAppHandler.kt`:
  ```kotlin
  class ReadLastWhatsAppHandler @Inject constructor(
      private val notifications: NotificationRepository,
      private val accessGate: NotificationAccessGate,
      @ApplicationContext private val context: Context,
  ) : FunctionHandler {
      override val functionName = "read_last_whatsapp"
      override suspend fun handle(call: FunctionCall): HandlerResult { /* see flow */ }
  }
  ```
- [ ] **Flow**:
  1. If `!accessGate.isGranted()` → `Failed(context.getString(copy_perm_missing_notifs), CurroError.NotificationAccessMissing)`.
  2. `val all = notifications.allUnread.first()`. If `all.isEmpty()` AND `notifications.parseMissCount.first() == 0` → `Spoken(context.getString(copy_no_unread))`.
  3. If `all.isEmpty()` AND parseMissCount > 0 → `Spoken(context.getString(copy_whatsapp_parse_miss))`.
  4. `val sender = (call.params["sender"] as? String)?.trim()?.takeIf { it.isNotEmpty() }`. If non-null → filter `all` by `sender` (case-insensitive + accent-strip equality against `WhatsAppMessage.sender` and `chatTitle`). If filter yields empty → `Spoken(context.getString(copy_no_unread_from, sender))` (NEW string: `"No tienes mensajes nuevos de %1$s."`).
  5. `val latest = (filtered ?: all).maxByOrNull { it.timestamp } ?: return Spoken(copy_no_unread)`.
  6. Build the speech per `Classification`:
     - TEXT: `"Tienes un mensaje de %1$s: %2$s"` — uses NEW template `copy_read_last_text`.
     - EMOJI: `"Tienes un mensaje de %1$s: te ha mandado un emoji."` — NEW `copy_read_last_emoji`.
     - VOICE_NOTE: `"Tienes un mensaje de %1$s: te ha mandado un audio."` — NEW `copy_read_last_voice`.
     - IMAGE: `"Tienes un mensaje de %1$s: te ha mandado una foto."` — NEW `copy_read_last_image`.
     - OTHER: fall through to `copy_whatsapp_parse_miss`.
  7. Return `Spoken(speech)`.
- [ ] `strings.xml` NEW entries: `copy_read_last_text`, `copy_read_last_emoji`, `copy_read_last_voice`, `copy_read_last_image`, `copy_no_unread_from`.
- [ ] **Hilt binding**: append to `HandlerModule.kt`:
  ```kotlin
  @Binds @IntoMap @StringKey("read_last_whatsapp")
  abstract fun bindReadLastWhatsApp(impl: ReadLastWhatsAppHandler): FunctionHandler
  ```
- [ ] JVM tests in `app/src/test/java/com/curro/app/handler/ReadLastWhatsAppHandlerTest.kt` (≥ 10 cases) with a fake `NotificationRepository` and fake `NotificationAccessGate`: empty cache, single text msg, multi-msg from same sender (last wins), multi-sender (latest across all), filter by sender hit, filter by sender miss, emoji classification, voice classification, image classification, access denied path, parse-miss-only cache.
- [ ] No new permissions; no manifest changes beyond what US-030 added; no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: S  ·  **Depends on**: US-030.

---

### US-032: `read_all_unread_whatsapp` handler  ·  _(master-plan SF-4.8, spec §5, spec §6 flow 5)_
**As** Fran's father, **I want** to say "léeme los mensajes" and hear all my unread WhatsApp grouped by who sent them **so that** the order makes sense and I don't have to keep track of who said what.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/handler/ReadAllUnreadWhatsAppHandler.kt` — same constructor shape as SF-4.7.
- [ ] **Flow**:
  1. Access gate (identical to SF-4.7).
  2. `val all = notifications.allUnread.first()`. Empty + parseMissCount == 0 → `copy_no_unread`. Empty + parseMissCount > 0 → `copy_whatsapp_parse_miss`.
  3. **Threshold > 8 (decision pinned)**: `if (all.size > 8) → Spoken(copy_many_unread)`. Phase 5/6 wires the follow-up STT for "todos / solo de alguien"; Phase 4 ships only the offer.
  4. **Group**: `val grouped: Map<String, List<WhatsAppMessage>> = all.groupBy { it.sender }`. Order of senders: descending by the group's latest `timestamp` (the most-recent-active sender first). Within each group: ascending by `timestamp` (chronological — reading order).
  5. **Build the speech** per spec §6 flow 5:
     - **Header** (one sender): `copy_reading_summary_one` (`"Tienes %1$d mensaje de %2$s."`) if `groups[0].size == 1`, else `copy_reading_summary_many` (`"Tienes %1$d mensajes de %2$s."`).
     - **Header** (two senders): use existing `copy_reading_summary_multi_sender` (`"Tienes %1$d mensajes de %2$s y %3$d mensaje de %4$s."`). For 3+ senders, fall back to a generic header: NEW `copy_reading_summary_three_plus` = `"Tienes mensajes nuevos de %1$s, %2$s y %3$s."` (the first three sender names). Pin in brief: brief explicitly enumerates the 1/2/3+ branches.
     - **Body** (each group):
       - First group: `copy_reading_starts_with` (`"Empiezo con %1$s:"`) + each message body joined by `". "`.
       - Subsequent groups: `copy_reading_from` (`"De %1$s: %2$s"`) for the first message + remaining bodies joined by `". "`.
     - **Per-message body** uses the same `Classification` mapping as SF-4.7 (TEXT → body, EMOJI → "te ha mandado un emoji", VOICE_NOTE → "te ha mandado un audio", IMAGE → "te ha mandado una foto").
  6. Return `Spoken(speech)`.
- [ ] `strings.xml`:
  - Existing entries REUSED: `copy_reading_summary_one`, `copy_reading_summary_many`, `copy_reading_summary_multi_sender`, `copy_reading_starts_with`, `copy_reading_from`, `copy_reading_message`, `copy_no_unread`, `copy_many_unread`, `copy_whatsapp_parse_miss`, `copy_perm_missing_notifs`.
  - NEW: `copy_reading_summary_three_plus` = `"Tienes mensajes nuevos de %1$s, %2$s y %3$s."`.
- [ ] **Hilt binding**: append to `HandlerModule.kt`:
  ```kotlin
  @Binds @IntoMap @StringKey("read_all_unread_whatsapp")
  abstract fun bindReadAllUnreadWhatsApp(impl: ReadAllUnreadWhatsAppHandler): FunctionHandler
  ```
- [ ] JVM tests in `app/src/test/java/com/curro/app/handler/ReadAllUnreadWhatsAppHandlerTest.kt` (≥ 15 cases):
  - Empty cache.
  - Parse-miss-only cache.
  - Single sender, single text msg.
  - Single sender, three text msgs (chronological).
  - Two senders, mixed counts.
  - Three senders.
  - Threshold case: exactly 8 unread → grouped read. 9 unread → `copy_many_unread`.
  - All-emoji from one sender.
  - Mixed classifications (text + emoji + voice + image) — pin the exact speech in the test.
  - Group chat senders preserved (the Person.name from MessagingStyle).
  - Access denied path.
  - Sender order: latest-active first.
- [ ] No new permissions; no manifest changes beyond US-030; no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: M  ·  **Depends on**: US-030.

---

### US-033: `ContactsProvider` + alias-lookup stub  ·  _(master-plan SF-4.9, spec §7, spec §10, local-data skill)_
**As a** Curro developer, **I want** a `ContactsProvider` that resolves a spoken name to 0/1/many `Contact`s via `ContactsContract` — and an `AliasRepository` whose Phase-4 implementation returns empty — **so that** SF-4.10's `call_contact` handler can ship without the Phase-7 alias-learning subsystem, and Phase 7 just replaces the stub with the Room-backed real thing.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/domain/model/Contact.kt` — `data class Contact(val lookupKey: String, val displayName: String, val phoneNumbers: List<String>, val photoUri: String?)`. `lookupKey` is the stable `ContactsContract.Contacts.LOOKUP_KEY` (per `local-data` skill — survives contact merges).
- [ ] `app/src/main/java/com/curro/app/domain/repository/ContactsProvider.kt`:
  ```kotlin
  interface ContactsProvider {
      /** Returns 0, 1, or many matches for [query]. Normalisation: lowercase + accent-strip on both sides; case-insensitive contains-match on display name. */
      suspend fun findByName(query: String): List<Contact>
  }
  ```
- [ ] `app/src/main/java/com/curro/app/domain/repository/AliasRepository.kt`:
  ```kotlin
  interface AliasRepository {
      /** Phase 4 stub returns emptyList(). Phase 7 wires the Room-backed real implementation. */
      suspend fun resolveAlias(alias: String): List<Contact>
  }
  ```
- [ ] `app/src/main/java/com/curro/app/data/contacts/ContactsContractProvider.kt` — `class ContactsContractProvider @Inject constructor(@ApplicationContext private val context: Context, @IoDispatcher private val io: CoroutineDispatcher) : ContactsProvider`:
  - `withContext(io) { … }`.
  - Build the query: `URI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI`; `projection = [LOOKUP_KEY, DISPLAY_NAME_PRIMARY, NUMBER, PHOTO_THUMBNAIL_URI]`; selection: read **all** rows whose `DISPLAY_NAME_PRIMARY` is non-null, then filter in-memory (the spec's pathological case — three Marías — requires set logic over the names, and `LIKE` on accent-bearing display names is unreliable on stock Android).
  - Normalise: `String.lowercase(Locale("es")).normalizeAccents()` on both query and display name.
  - Match rule: a row matches iff its normalised display name `contains` the normalised query as a whole-word match (regex `\\b<query>\\b`). For multi-token queries (e.g. "maria garcía"), the whole-string contains-match is used instead.
  - Group rows by `LOOKUP_KEY`: one `Contact` per `LOOKUP_KEY`, with all phone numbers (deduped, normalised), one display name (first non-null), one photo URI.
- [ ] `app/src/main/java/com/curro/app/data/contacts/EmptyAliasRepository.kt` — `class EmptyAliasRepository @Inject constructor() : AliasRepository { override suspend fun resolveAlias(alias: String) = emptyList<Contact>() }`. **This is the Phase-4 stub**; Phase 7 replaces with `RoomAliasRepository`.
- [ ] **`ContentResolver` testability** — wrap `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` query behind a small interface:
  ```kotlin
  interface ContactsQueryRunner {
      suspend fun query(): List<ContactRow>
      data class ContactRow(val lookupKey: String, val displayName: String, val phoneNumber: String?, val photoUri: String?)
  }
  ```
  `ContentResolverContactsQueryRunner` is the production impl; tests provide a fake list.
- [ ] `app/src/main/java/com/curro/app/di/ContactsModule.kt`:
  ```kotlin
  @Module @InstallIn(SingletonComponent::class)
  abstract class ContactsModule {
      @Binds @Singleton abstract fun bindContactsProvider(impl: ContactsContractProvider): ContactsProvider
      @Binds @Singleton abstract fun bindAliasRepository(impl: EmptyAliasRepository): AliasRepository
      @Binds @Singleton abstract fun bindContactsQueryRunner(impl: ContentResolverContactsQueryRunner): ContactsQueryRunner
  }
  ```
- [ ] **Manifest**: `<uses-permission android:name="android.permission.READ_CONTACTS" />` added. The runtime permission is requested by SF-4.10's handler on first use — NOT at install, NOT pro-actively.
- [ ] **New `CurroError` variants**:
  - `data class AmbiguousContact(val matches: List<Contact>) : CurroError()` — NEW.
  - `data class ContactNotFound(val query: String) : CurroError()` — NEW.
  - `ReadContactsPermissionMissing : CurroError()` — NEW (`object` variant).
- [ ] `strings.xml`:
  - `copy_perm_missing_contacts` — exists; REUSED.
  - `copy_contact_not_found` — exists (`"No encuentro a %1$s en tus contactos."`); REUSED.
  - `copy_contact_ambiguous_phase4` — NEW: `"Tienes varios contactos así; espera, todavía no sé elegir entre ellos."` (Phase 6 replaces this path with the real picker; the brief loud-flags this is provisional.)
- [ ] JVM tests in `app/src/test/java/com/curro/app/data/contacts/ContactsContractProviderTest.kt` (≥ 12 cases) with a fake `ContactsQueryRunner` returning a curated row list:
  - Single match by exact name.
  - Single match by case-insensitive name.
  - Single match with accent stripping (`"maria"` matches "María").
  - Three matches (the spec's "three Marías" case) — all three Contacts returned.
  - No match.
  - Multi-token query (`"maria garcía"`) matches exactly one row.
  - Same `LOOKUP_KEY` with two phone numbers → one Contact with both phones.
  - Empty query → empty list.
  - Query with apostrophe (`"d'angelo"`) — pinned: regex-safe (the impl uses `Regex.escape`).
  - Photo URI propagated.
  - Display-name-null row → skipped (defensive).
  - Phone-number-null row → still produces a Contact with empty phone list (alias lookup may still resolve later).
- [ ] `EmptyAliasRepositoryTest.kt` — 1 case: any input → empty list.
- [ ] No telemetry change.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: M  ·  **Depends on**: US-025.

---

### US-034: `call_contact` handler  ·  _(master-plan SF-4.10, spec §5, spec §6 flow 1, platform-integrations TelecomManager section)_
**As** Fran's father, **I want** to say "llama a Pepito" and have Curro just call Pepito **so that** I don't have to scroll a contact list looking for him.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/data/telephony/CallController.kt`:
  ```kotlin
  interface CallController { fun call(number: String): Boolean }

  class IntentCallController @Inject constructor(@ApplicationContext private val context: Context) : CallController {
      override fun call(number: String): Boolean {
          val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          return try { context.startActivity(intent); true }
                 catch (_: SecurityException) { false }
                 catch (_: ActivityNotFoundException) { false }
      }
  }
  ```
  Uses **`ACTION_CALL`** (not `ACTION_DIAL`) per `platform-integrations` — places the call directly per spec §6 flow 1.
- [ ] `app/src/main/java/com/curro/app/handler/CallContactHandler.kt`:
  ```kotlin
  class CallContactHandler @Inject constructor(
      private val contacts: ContactsProvider,
      private val aliases: AliasRepository,
      private val callController: CallController,
      private val readContactsGate: ReadContactsPermissionGate,
      private val callPhoneGate: CallPhonePermissionGate,
      @ApplicationContext private val context: Context,
  ) : FunctionHandler {
      override val functionName = "call_contact"
      override suspend fun handle(call: FunctionCall): HandlerResult { /* see flow */ }
  }
  ```
- [ ] **Flow**:
  1. `val query = (call.params["contact"] as? String).orEmpty().trim()`. Empty → `Failed(copy_contact_not_found(""), ContactNotFound(""))`.
  2. **`READ_CONTACTS` gate**: if `!readContactsGate.isGranted()` → `Failed(copy_perm_missing_contacts, ReadContactsPermissionMissing)`. (The permission request is fired by the home screen via a new side effect; the handler reports the gap; Phase-6's policy may inline a prompt — pinned out of scope here.)
  3. **Alias lookup first** (`spec §7`): `val aliasMatches = aliases.resolveAlias(query)`. Phase 4 — this is always empty (Phase-7 contract). In Phase 7, a non-empty result short-circuits the next step.
  4. `val byName = if (aliasMatches.isEmpty()) contacts.findByName(query) else aliasMatches`.
  5. **Resolve**:
     - `byName.size == 0` → `Failed(context.getString(copy_contact_not_found, query), ContactNotFound(query))`.
     - `byName.size > 1` → `Failed(context.getString(copy_contact_ambiguous_phase4), AmbiguousContact(byName))`. **Pin in brief: Phase 6 replaces this branch with the picker; SF-4.10 deliberately stays single-match.**
     - `byName.size == 1` → continue.
  6. **`CALL_PHONE` gate**: if `!callPhoneGate.isGranted()` → `Failed(copy_perm_missing_calls, PermissionDenied)`. (Reuses the generic `PermissionDenied` variant.)
  7. **Place the call**: `val contact = byName.first(); val number = contact.phoneNumbers.firstOrNull() ?: return Failed(context.getString(copy_contact_not_found, query), ContactNotFound(query))`. Then `val ok = callController.call(number)`. If `!ok` → `Failed(copy_perm_missing_calls, PermissionDenied)` (the SecurityException case).
  8. Success → `Spoken(context.getString(copy_calling, contact.displayName))`.
- [ ] `app/src/main/java/com/curro/app/data/permissions/ReadContactsPermissionGate.kt` — interface + impl, same pattern as `RecordAudioPermissionGate.kt`. Same shape: `CallPhonePermissionGate.kt`.
- [ ] **Manifest**: `<uses-permission android:name="android.permission.CALL_PHONE" />`. `READ_CONTACTS` was already added by US-033.
- [ ] **Runtime permission wiring** — extend `LauncherViewModel` and `LauncherScreen`:
  - New side effect `data object RequestReadContacts : LauncherSideEffect`, `data object RequestCallPhone : LauncherSideEffect`. The screen registers two new `rememberLauncherForActivityResult(RequestPermission())` instances; the ViewModel emits the side effect when the handler returns `ReadContactsPermissionMissing` / `PermissionDenied` AND the function in flight is `call_contact`.
  - On grant → automatically retry the last `FunctionCall` (mark the policy explicitly: at most ONE auto-retry per turn; the brief loud-flags this). On denial → speak the existing copy.
  - **Decision pinned**: the auto-retry is acceptable for Phase 4 because there's no FSM-confirming step in between; Phase 5/6 makes this explicit through the FSM transitions.
- [ ] `strings.xml`:
  - `copy_calling` — exists (`"Llamando a %1$s."`); REUSED.
  - `copy_calling_confirmed` — exists; reserved for Phase 6.
  - `copy_perm_missing_contacts` — exists; REUSED.
  - `copy_perm_missing_calls` — exists; REUSED.
  - `copy_contact_not_found` — exists; REUSED.
  - `copy_contact_ambiguous_phase4` — added by US-033; REUSED.
  - No new strings.
- [ ] **Hilt bindings**: append to `HandlerModule.kt`:
  ```kotlin
  @Binds @IntoMap @StringKey("call_contact")
  abstract fun bindCallContact(impl: CallContactHandler): FunctionHandler
  ```
  Plus a new `app/src/main/java/com/curro/app/di/TelephonyModule.kt`:
  ```kotlin
  @Module @InstallIn(SingletonComponent::class)
  abstract class TelephonyModule {
      @Binds @Singleton abstract fun bindCallController(impl: IntentCallController): CallController
  }
  ```
- [ ] JVM tests in `app/src/test/java/com/curro/app/handler/CallContactHandlerTest.kt` (≥ 12 cases) with fakes for every collaborator:
  - Single match by name → `Spoken("Llamando a Pepito.")`; `callController.call` invoked with the right number.
  - Alias hit → contacts query skipped.
  - Three matches → `Failed(copy_contact_ambiguous_phase4, AmbiguousContact)`.
  - No match → `Failed(copy_contact_not_found, ContactNotFound)`.
  - Empty contact param → `Failed(ContactNotFound(""))`.
  - `READ_CONTACTS` denied → `Failed(copy_perm_missing_contacts, ReadContactsPermissionMissing)`; the contacts query is NEVER attempted.
  - `CALL_PHONE` denied at place-call time → `Failed(copy_perm_missing_calls, PermissionDenied)`.
  - `CallController.call` returns false (the SecurityException path) → `Failed(copy_perm_missing_calls, PermissionDenied)`.
  - Contact with no phone numbers → `Failed(copy_contact_not_found, ContactNotFound)` (graceful — pin in brief).
  - Contact with multiple phone numbers → first number used (Phase 6 may surface a picker; for Phase 4 the first wins).
  - Accent variant of contact name resolves (`"jose"` → "José") — verifies `ContactsProvider`'s normalisation pipeline.
  - **No PII in `Log.w` or telemetry**: a verify-once test asserts the failed-command log line (when a handler fails) contains `utterance.len=<int>` and no contact name.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: M  ·  **Depends on**: US-033 (`ContactsProvider` + `AliasRepository`), US-025 (handler interface).

---

## Phase 5 — State machine & interruption _(spec §6, §14 step 5)_

> `idle`/`listening`/`processing`/`confirming`/`executing`/`error_recovery` · new
> button press interrupts current state → `listening` · 10 s no-answer timeout in
> `confirming` (wired in Phase 6) · consecutive-STT-failure policy (1st/2nd/3rd
> message, then give up) · HOME-press resets the FSM. Replaces the ad-hoc
> `ListeningState` glue from Phases 2–4 with a single-owner state machine + a
> coordinator that drives the spec §4 pipeline through it.

---

### US-035: `AssistantStateMachine` + `AssistantState` sealed interface  ·  _(master-plan SF-5.1, spec §6, voice-interaction rule 1)_
**As a** Curro developer, **I want** a `sealed interface AssistantState` with the six spec §6 states (each carrying its needed data) and an `AssistantStateMachine` that is the single owner of `StateFlow<AssistantState>` plus a single `transition(event)` mutation entry point — **so that** every Phase-5+ pipeline component reads one source of truth, the spec §6 diagram is enforced (invalid transitions throw), and the interrupt-by-button + HOME-press rules can be honoured uniformly.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/assistant/AssistantState.kt` defining `sealed interface AssistantState` with: `data object Idle`, `data class Listening(partial: String, startedAtMs: Long)`, `data class Processing(transcript: String, startedAtMs: Long)`, `data class Confirming(prompt: String, expiresAtMs: Long, pendingAction: PendingAction)`, `data class Executing(speech: String, screen: AssistantScreen?)`, `data class ErrorRecovery(message: String, failureCount: Int)`. Pin: `expiresAtMs` and `failureCount` are carried **here** in Phase 5 even though Phase 6 (timeout enforcement) and SF-5.4 (counter) own their semantics — so neither phase has to reshape the state.
- [ ] `app/src/main/java/com/curro/app/assistant/PendingAction.kt`: `data class PendingAction(val functionName: String, val onConfirm: suspend () -> HandlerResult)`.
- [ ] `app/src/main/java/com/curro/app/assistant/AssistantEvent.kt` defining `sealed interface AssistantEvent` with 11 events: `MicPressed(timestamp)`, `PartialTranscript(partial)`, `FinalTranscript(transcript, timestamp)`, `SttFailed(message, failureCount)`, `FunctionCallReady(needsConfirmation, speech, screen, prompt?, expiresAtMs, pendingAction?)`, `UserConfirmed(speech, screen)`, `UserRejected`, `ConfirmationTimedOut`, `ExecutionDone`, `RecoverySpoken`, `HomePressed`.
- [ ] `app/src/main/java/com/curro/app/assistant/AssistantStateMachine.kt`: `@Singleton class @Inject constructor()`; exposes read-only `val state: StateFlow<AssistantState>` (initial value `Idle`); single mutation entry point `fun transition(event: AssistantEvent): AssistantState` that validates against the spec §6 diagram and throws `IllegalAssistantTransition(state, event)` on invalid pairs.
- [ ] `MicPressed(ts)` is valid in **every** state → `Listening("", startedAtMs = ts)` (the interrupt rule, voice-interaction rule 1). `HomePressed` is valid in **every** state → `Idle` (the HOME-reset rule, launcher-app rule 3).
- [ ] `FunctionCallReady(needsConfirmation = true, …)` requires `prompt` and `pendingAction` non-null (otherwise `IllegalArgumentException` from `requireNotNull`).
- [ ] `app/src/main/java/com/curro/app/assistant/IllegalAssistantTransition.kt`: `class IllegalAssistantTransition(val state, val event) : IllegalStateException(...)`.
- [ ] `app/src/main/java/com/curro/app/assistant/TimeProvider.kt`: `interface TimeProvider { fun now(): Long }` + `class SystemTimeProvider @Inject constructor(private val clock: Clock) : TimeProvider`. `app/src/main/java/com/curro/app/di/TimeProviderModule.kt`: `@Binds @Singleton bindTimeProvider(impl: SystemTimeProvider): TimeProvider`. Pin: **no `System.currentTimeMillis()` or `SystemClock.elapsedRealtime()` anywhere in `assistant/`** — every "what time is it" goes through `TimeProvider`.
- [ ] JVM tests in `app/src/test/java/com/curro/app/assistant/AssistantStateMachineTest.kt` (≥ 40, target ~70) covering every valid transition + every invalid `(state, event)` pair:
  - 6 cases for `MicPressed` from each pre-state → `Listening`.
  - 6 cases for `HomePressed` from each pre-state → `Idle`.
  - 6 cases each for `PartialTranscript`, `FinalTranscript`, `SttFailed` (1 valid + 5 invalid).
  - 8 cases for `FunctionCallReady` (2 valid + 2 invariant-throws + 5 invalid pre-state) — the prompt/pendingAction-null-with-`nc=true` cases throw `IllegalArgumentException`, **not** `IllegalAssistantTransition`.
  - 6 cases each for `UserConfirmed`, `UserRejected`, `ConfirmationTimedOut` (1 valid + 5 invalid).
  - 6 cases each for `ExecutionDone`, `RecoverySpoken` (2 valid pre-states `Executing | ErrorRecovery` + 4 invalid).
  - 3 `StateFlow` semantics tests: initial value `Idle`; synchronous `state.value` update; redundant identical `PartialTranscript` emissions deduplicate (Turbine).
  - 1 test asserting `IllegalAssistantTransition` carries the offending `state` and `event` properties for debug.
- [ ] `TestTimeProvider` (in `test/`): `class TestTimeProvider(var nowMs: Long = 0L) : TimeProvider` for deterministic test timestamps.
- [ ] No new permissions, no Android dependencies — `assistant/` package is pure Kotlin + coroutines + Hilt.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: M  ·  **Depends on**: US-025 (handler interface, `HandlerResult`, `AssistantScreen`).

---

### US-036: `AssistantCoordinator` — rewire the pipeline through the FSM  ·  _(master-plan SF-5.2, spec §4, voice-interaction rules 1+7)_
**As a** Curro developer, **I want** a single `@Singleton AssistantCoordinator` that owns the spec §4 pipeline (button → STT → FunctionGemma → handler → TTS) and drives the SF-5.1 FSM, while `LauncherViewModel` becomes a thin observer of `coordinator.state` — **so that** mic-press cancellation lives in one place (the architectural enforcement of the interrupt rule), the VM drops from 18 functions to ≤ 8, and every Phase-4 handler keeps working end-to-end through the proper FSM transitions.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt`: `@Singleton class @Inject constructor(...)` injecting `AssistantStateMachine`, `SttClient`, `TtsClient`, `FunctionCallEngine`, `FunctionCallValidator`, `HandlerDispatcher`, `TimeProvider`, `TelemetrySink`, `PermissionGate`, `ReadContactsPermissionGate`, `CallPhonePermissionGate`, `Clock`, `@ApplicationContext Context`, `@ApplicationScope CoroutineScope`, `@MainDispatcher CoroutineDispatcher`.
- [ ] Public API:
  - `val state: StateFlow<AssistantState>` (re-exposes `stateMachine.state`).
  - `val sideEffects: SharedFlow<AssistantSideEffect>` (replay = 0, extraBufferCapacity = 8).
  - `fun onMicPressed()`, `fun onHomePressed()`, `fun onUserConfirmed()` (Phase 6 wires the body), `fun onUserRejected()` (Phase 6), `fun onPermissionResult(permission: String, granted: Boolean)`.
- [ ] **`onMicPressed` does in order**: `currentJob?.cancel(); ttsClient.stop(); sttClient.cancel()` → reset per-turn retry bookkeeping (`readContactsAutoRetried = false`, `callPhoneAutoRetried = false`, `pendingFunctionCall = null`) → `stateMachine.transition(MicPressed(timeProvider.now()))` → permission gate check (if `!recordAudioGate.isGranted()` → emit `RequestPermission(RECORD_AUDIO)` side effect and return) → `currentJob = scope.launch { runListenLoop() }`. **The cancel-then-stop-then-transition order is the architectural enforcement of SF-5.3's interrupt rule; do not refactor without re-reading `docs/architecture/interrupt-by-button.md`** (added in SF-5.3).
- [ ] **`onHomePressed` does**: `currentJob?.cancel(); ttsClient.stop(); sttClient.cancel(); stateMachine.transition(HomePressed)`. Used by SF-5.6 from `MainActivity.onNewIntent`.
- [ ] `runListenLoop()`: collects `sttClient.listen()` with `collectLatest`. `Event.Partial` → `transition(PartialTranscript)`. `Event.Final` → `transition(FinalTranscript)` then `decideAndDispatch`. `Event.Failed` → `onSttFailed` (Phase-5 hardcodes `failureCount = 1`; SF-5.4 plugs in the real counter).
- [ ] `decideAndDispatch`: `engine.decide(transcript, buildContext())` → validator → on success `onDecisionSuccess` (telemetry + dispatch + `renderHandlerResult`); on failure `onDecisionFailure` (telemetry + speak `copy_error_unknown_function` / `copy_models_not_ready` per error type). Decision-layer failures route through `FunctionCallReady(needsConfirmation=false, speech=fail_copy, …) → Executing → ExecutionDone` — **not** through `SttFailed` (which would require widening `SttFailed`'s valid pre-states).
- [ ] `renderHandlerResult`: `Spoken` → `transition(FunctionCallReady(nc=false, speech, screen, …))` → `ttsClient.speak(speech)` → `transition(ExecutionDone)`. **`NeedsConfirmation` short-circuits with auto-confirm in Phase 5** (recurses into `onConfirm()`), matching the Phase-4 behaviour — Phase 6 replaces this branch with the `Confirming` transition; pin this in the brief explicitly. `Failed` → `tryAutoRetryOnPermission(action, reason)` (returns true if a `RequestPermission` side effect was fired); otherwise speak the failure line via `FunctionCallReady(speech=failure, …) → Executing → ExecutionDone`.
- [ ] `tryAutoRetryOnPermission`: only handles `action == "call_contact"`; for `ReadContactsPermissionMissing` and `PermissionDenied`, fire one-shot `RequestPermission(READ_CONTACTS)` or `RequestPermission(CALL_PHONE)` side effect, mark `*AutoRetried = true`. Subsequent same-turn failure no longer retries (caller speaks the failure line). On permission **grant** → `handleReadContactsResult`/`handleCallPhoneResult` re-dispatch `pendingFunctionCall`.
- [ ] `app/src/main/java/com/curro/app/assistant/AssistantSideEffect.kt`: `sealed interface AssistantSideEffect { data class RequestPermission(val permission: String); data object OpenNotificationAccessSettings; data class ShowDebugJson(val prettyJson: String) }`.
- [ ] **`LauncherViewModel` refactor**:
  - Drop injections: `SttClient`, `TtsClient`, `FunctionCallEngine`, `FunctionCallValidator`, `HandlerDispatcher`, `TelemetrySink`, `PermissionGate`, `ReadContactsPermissionGate`, `CallPhonePermissionGate`, `@ApplicationContext Context`. **Keep**: `DefaultLauncherDetector`, `ObserveClockUseCase`, `FavoriteAppsRepository`, `NotificationAccessGate`.
  - Inject `AssistantCoordinator`.
  - Replace `private val listeningStateFlow` with reads of `coordinator.state`. Rebuild `uiState: StateFlow<LauncherUiState>` via `combine(detector.flow, observeClock(), favoritesRepo.observeFavorites(), coordinator.state, notifGrantedFlow) { … }`. `LauncherUiState` gets `val assistantState: AssistantState = AssistantState.Idle` and **loses** `val listeningState: ListeningState`.
  - Adapt `coordinator.sideEffects` → `LauncherSideEffect` inside a `viewModelScope.launch { coordinator.sideEffects.collect { … } }` block: `RequestPermission(RECORD_AUDIO/READ_CONTACTS/CALL_PHONE)` → `LauncherSideEffect.RequestRecordAudio/RequestReadContacts/RequestCallPhone`; `ShowDebugJson` → `LauncherSideEffect.ShowDebugJson`.
  - `onEvent` becomes thin: `MicPressed → coordinator.onMicPressed()`, `AppTileTapped → onAppTileTapped`, `ClockTapped → onClockTapped`, `*PermissionResult → coordinator.onPermissionResult(...)`, `GrantNotifAccessRequested → onGrantNotifAccessRequested`.
  - Keep: `init { lifecycleSource() }`, `onCleared`, `onAppTileTapped`, `onClockTapped`, `onGrantNotifAccessRequested`. Drop: `startListening`, `handleSttEvent`, `decideAndSpeak`, `handleDecisionSuccess`, `handleDecisionFailure`, `render`, `speakAndIdle`, `handleSttFailure`, `showTransientError`, `errorMessage`, `emitDecideEvent`, `buildContext`, `prettyPrint`, `jsonValue`, `tryRequestCallContactPermission`, `onReadContactsPermissionResult`, `onCallPhonePermissionResult`, `onPermissionResult`.
  - **Function count ceiling: ≤ 8** post-refactor. Remove `@Suppress("TooManyFunctions")`.
- [ ] **Delete** `app/src/main/java/com/curro/app/presentation/launcher/ListeningState.kt` — no callers after the VM refactor.
- [ ] **`LauncherPlaceholderScreen.kt`**: replace `if (uiState.listeningState !is ListeningState.Idle)` with `if (uiState.assistantState !is AssistantState.Idle)`; switch the inner `when` over `Starting/Listening/Processing/Speaking/Error` to a `when` over `AssistantState.Listening/Processing/Executing/ErrorRecovery` plus `Confirming -> Unit`. **Cosmetic refactor only**; the per-state-overlay split is SF-5.5.
- [ ] **Smoke list**: every Phase-4 handler still works end-to-end on the Redmi 15 — `tell_time`, `open_app`, `calculate`, `help`, `read_last_whatsapp`, `read_all_unread_whatsapp`, `call_contact`. Pinned in the brief §13.3.
- [ ] JVM tests in `app/src/test/java/com/curro/app/assistant/AssistantCoordinatorTest.kt` (≥ 20 cases, target 23): one happy path per Phase-4 handler (6), 3 STT failure variants, 5 `call_contact` permission flow cases, 4 decision-layer failure cases, 2 `NeedsConfirmation`-auto-confirm cases, 1 mic-press-during-Executing interrupt mechanism case, 1 `onHomePressed` from non-Idle case, 1 telemetry-shape case. Fakes: `FakeSttClient`, `FakeTtsClient` (with `wasStopped`), `FakeFunctionCallEngine`, real `FunctionCallValidator`, real `HandlerDispatcher` + fake `FunctionHandler` instances, `TestTimeProvider`, configurable permission-gate fakes, `RecordingTelemetrySink`.
- [ ] **`LauncherViewModelTest.kt` deletions** (pinned in the brief): every assertion on the old `ListeningState` shape, the barge-in mid-speak test, the RECORD_AUDIO denial transient-error test, the STT no-match → `copy_stt_fail_1` test, the decision-smoke-loop telemetry test, both auto-retry-on-permission-grant tests, the failed-command log line test. Each of these moves to `AssistantCoordinatorTest`. Keep: 5-tap config gesture, app-tile-tap, notification-access ON_RESUME, favourites + clock combine, `GrantNotifAccessRequested` side effect.
- [ ] `model_decide` telemetry event still fires with `{model: "function_gemma_270m", outcome, latency_ms}` — same shape as Phase 3.
- [ ] No new `CurroError` variant; no new `Manifest.permission.*`; no new strings.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

**Size**: M  ·  **Depends on**: US-035 (FSM), US-024 (smoke-loop being replaced), US-025 (`HandlerDispatcher`), US-015 (`SttClient`), US-016 (`TtsClient`), US-034 (the permission-side-effect glue that moves into the coordinator).

---

### US-037: Interrupt-by-button hardening (the rule that breaks if missed)  ·  _(master-plan SF-5.3, spec §6 closing paragraph, voice-interaction rule 1)_
**As** Fran's father, **I want** to tap the mic button while Curro is reading a long message and have Curro stop immediately and listen to me **so that** I don't have to wait for him to finish before changing my mind. **As a** Curro developer, **I want** this rule hard-coded in `AssistantCoordinator.onMicPressed()` (not bolted on per-state) and exhaustively tested **so that** no future refactor accidentally removes it.

**Acceptance Criteria**:
- [ ] Verified on the real Redmi 15 that `TtsClient.stop()` (from US-017's `SystemTtsClient`) halts a playing utterance within ~150 ms wall-clock; the `UtteranceProgressListener.onDone(utteranceId, interrupted=true)` is observable. If a regression has crept in, **patch as part of this SF**.
- [ ] Verified on the real Redmi 15 that `SttClient.cancel()` (from US-016's `SystemSttClient`) halts a recognizer session (no further partials emitted). If broken, patch.
- [ ] `app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt` `onMicPressed` already does `currentJob?.cancel(); ttsClient.stop(); sttClient.cancel()` first (per SF-5.2). **This SF verifies and documents the rule** — it does not re-implement the cancellation.
- [ ] `app/src/test/java/com/curro/app/assistant/AssistantCoordinatorTest.kt` — append **Group F (5 new tests)**, one per non-Idle FSM state, each asserting that `onMicPressed()` cancels `currentJob` and stops TTS+STT and transitions to `Listening`:
  - mic press while `Listening` (STT actively running)
  - mic press while `Processing` (FunctionGemma's `engine.decide` suspended in a fake)
  - mic press while `Confirming` (state forced via `stateMachine.transition` to bypass the Phase-5 auto-confirm short-circuit; Phase 6 makes this reachable from the happy path)
  - mic press while `Executing` (TTS suspended on a long utterance)
  - mic press while `ErrorRecovery` (TTS suspended on the recovery line)
- [ ] `app/src/androidTest/java/com/curro/app/presentation/launcher/LauncherInterruptInstrumentedTest.kt`: instrumented Compose UI test. Drive the coordinator to `Executing` via a test-only `coordinator.testForceExecuting(longUtterance)` seam (or a fake long-talk handler installed via Hilt test bindings); tap the mic; assert the state returns to `Listening`. **Wall-clock 150-ms latency is verified manually on the Redmi 15** (in the smoke list of US-036) — the instrumented test asserts the final state, not the wall-clock latency (JUnit on Android Test can't promise milliseconds).
- [ ] `docs/architecture/interrupt-by-button.md` exists (a new `docs/architecture/` directory) and documents: the spec §6 rule verbatim, where the cancellation glue lives + why-here-not-elsewhere, why both `Job.cancel()` AND `TextToSpeech.stop()` / `SpeechRecognizer.cancel()`, the ~150-ms acceptance bar, the rule's extension to `onHomePressed`, and cross-references to spec §6 + `voice-interaction` rule 1 + master-plan Phase 5 Risks (a).
- [ ] Manual smoke (Redmi 15): with a fixture-loaded WhatsApp unread set (≥ 3 messages), trigger `read_all_unread_whatsapp`; mid-read tap the mic; observe the TTS halt and the `ListeningOverlay` appear — perceived latency must feel "immediate" (< 250 ms wall-clock).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green; `./gradlew connectedDebugAndroidTest` green on emulator.

**Size**: M  ·  **Depends on**: US-035, US-036.

---

### US-038: Consecutive-STT-failure policy (1st / 2nd / 3rd messages)  ·  _(master-plan SF-5.4, spec §6 flow 6, voice-interaction rules 3+4)_
**As** Fran's father, **I want** Curro to give me a different message on the 1st / 2nd / 3rd time I'm not understood — and to stop after three strikes instead of looping "no te entiendo" forever — **so that** the worst possible experience (an infinite "I don't understand you" loop) cannot happen.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/assistant/SttFailureCounter.kt`: `@Singleton class SttFailureCounter @Inject constructor()` with `fun recordFailure(): Int` (increments and returns the new count, no upper bound) and `fun recordSuccess()` (resets to 0); plus `internal fun peek(): Int` `@VisibleForTesting`. No synchronisation needed — only mutated from the coordinator's `Main.immediate` scope.
- [ ] `AssistantCoordinator` integration:
  - Inject `SttFailureCounter`.
  - Replace the hardcoded `failureCount = 1` in `onSttFailed` with `sttFailureCounter.recordFailure()`. The Spanish line is chosen via `pickFailMessage(error, count)`:
    - `SttVoicePackMissing` → `copy_stt_no_voice_pack` (always, regardless of count; counter still increments — the recognition *did* fail).
    - `PermissionDenied` → `copy_perm_missing_mic` (always, regardless of count; counter still increments).
    - everything else → `copy_stt_fail_1` if count == 1, `copy_stt_fail_2` if count == 2, `copy_stt_fail_3` otherwise (≥ 3).
  - On `count >= 3`, the coordinator calls `sttFailureCounter.recordSuccess()` **before** the `RecoverySpoken` transition — so the next mic press starts at count 1, not 4. Pin: `GIVE_UP_THRESHOLD = 3`.
  - **`recordSuccess()` is called from one site outside `onSttFailed`** — in `onFinalTranscript`, *after* the FSM transitions to `Processing` (i.e., immediately on a successful STT final). Pinned: do not call `recordSuccess` elsewhere; downstream handler failures (e.g., `ContactNotFound`) do NOT additionally call it.
- [ ] The three COPY entries already exist in `strings.xml` (verified, lines 80–84). **No new strings added.**
- [ ] JVM tests in `app/src/test/java/com/curro/app/assistant/SttFailureCounterTest.kt` (3 cases): `recordFailure` returns 1, 2, 3, 4, 5 on successive calls; `recordSuccess` resets; the sequence `fail, fail, success, fail` returns `1, 2, _, 1`.
- [ ] JVM tests in `AssistantCoordinatorTest.kt` — append **Group N (5 cases)**: 1st STT fail speaks `copy_stt_fail_1` + `ErrorRecovery.failureCount == 1`; 2nd in same session speaks `copy_stt_fail_2` + `failureCount == 2`; 3rd speaks `copy_stt_fail_3` + counter resets to 0; a successful turn after 2 fails resets the counter (next fail → `copy_stt_fail_1`); `SttVoicePackMissing` speaks `copy_stt_no_voice_pack` regardless and the counter still increments.
- [ ] No new telemetry.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.

**Size**: S  ·  **Depends on**: US-035 (`ErrorRecovery.failureCount` field), US-036 (coordinator integration point).

---

### US-039: State-driven assistant overlays  ·  _(master-plan SF-5.5, spec §11, launcher-ui rule 3)_
**As** Fran's father, **I want** the screen to clearly show me which state Curro is in — listening (blue tint + "Te escucho…"), processing ("Un momento…"), speaking the result, or recovering from an error — **so that** the visual reinforces the voice and I don't get lost. **As a** Curro developer, **I want** each state to have its own overlay composable file (keyed off `AssistantState`, not nav routes) **so that** future Phases 6/7 can swap one composable at a time without touching the others.

**Acceptance Criteria**:
- [ ] **4 new composable files** in `app/src/main/java/com/curro/app/presentation/assistant/`: `ListeningOverlay.kt`, `ProcessingOverlay.kt`, `ExecutingOverlay.kt`, `ErrorRecoveryOverlay.kt`. Each contains a public `<Name>(state, modifier)` composable + a private stateless `<Name>Content(...)` + 3 `@Preview` functions (light, dark, `fontScale = 2.0f` — per `brand-design` rule 6).
- [ ] **`ListeningOverlay`**: background = `CurroListeningTintLight` (or `CurroListeningTintDark` in dark mode) — Curro extension tokens from `brand-design`; "Te escucho…" headline (`copy_listening_prompt`, `displayMedium`); live partial transcript below in `headlineMedium` with `liveRegion = Polite`; static `Icons.Filled.Mic` icon ≥ 96 dp (no animation).
- [ ] **`ProcessingOverlay`**: background = `MaterialTheme.colorScheme.surfaceVariant`; centred "Un momento…" (`copy_processing`, `displayMedium`, `liveRegion = Polite`); **static three-dot indicator** (three filled circles in a `Row` — NO animation per spec §11).
- [ ] **`ExecutingOverlay`**: background = `MaterialTheme.colorScheme.surface`; the `state.speech` line wrapped in a `BigCard` (from `presentation/common/`, US-006), `headlineLarge`, `liveRegion = Assertive`. The `state.screen` parameter is on the signature for Phase 6/7 (`MessageCardsScreen` / `ContactPickerScreen`); for Phase 5 it's always `null`.
- [ ] **`ErrorRecoveryOverlay`**: background = `errorContainer`; the `state.message` in `onErrorContainer` colour, `headlineMedium`, `liveRegion = Assertive`. Receives `state.failureCount` on the signature (for Phase 6+ count-aware visuals); Phase 5 doesn't use it.
- [ ] Every `Icon`/`Image` has `contentDescription` (or `null` with rationale — the surrounding headline is the label).
- [ ] Every overlay reads tokens from `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` / `CurroSpacing.*` — **no raw `Color`/`.sp`/`.dp` literals outside `presentation/theme/`** (detekt-enforced).
- [ ] **`LauncherPlaceholderScreen.kt` refactor**: wrap `LauncherPlaceholderContent` in a `Box(Modifier.fillMaxSize())`; render `LauncherHome(uiState, onEvent)` (extracted from the current body); then `when (val s = uiState.assistantState) { AssistantState.Idle -> Unit; is AssistantState.Listening -> ListeningOverlay(s); is AssistantState.Processing -> ProcessingOverlay(); is AssistantState.Confirming -> Unit /* SF-6.2 (Phase 6) owns this overlay */; is AssistantState.Executing -> ExecutingOverlay(s); is AssistantState.ErrorRecovery -> ErrorRecoveryOverlay(s) }`. The `Confirming -> Unit` branch is **present and commented** so Phase 6's diff is clean.
- [ ] `LauncherPlaceholderScreen.kt` is **≤ 200 lines after the refactor** (currently 419); the previews may move into a sister `LauncherHomePreviews.kt` if needed.
- [ ] `copy_processing` already exists in `strings.xml` (verified, line 20). **No new strings added.**
- [ ] **6 Compose UI tests** in `app/src/androidTest/java/com/curro/app/presentation/launcher/LauncherScreenStateTest.kt` (or Robolectric if compatible — pin: prefer Robolectric for speed): `Idle` → no overlay; `Listening` → blue overlay with partial; `Processing` → "Un momento…"; `Executing` → speech text in a card; `ErrorRecovery` → recovery message; `Listening → Processing` transition cleanly swaps overlays.
- [ ] Manual visual sweep on the Redmi 15: every overlay is legible at `fontScale = 2.0f`, contrast ≥ 7:1 for body text (per `brand-design`), no clipping, no overlap with the launcher home below.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green; Compose tests green.

**Size**: M  ·  **Depends on**: US-035 (FSM), US-036 (`uiState.assistantState`).

---

### US-040: HOME-press / `onNewIntent` resets the FSM to `idle`  ·  _(master-plan SF-5.6, launcher-app rule 3, spec §6 + §11)_
**As** Fran's father, **I want** to press the HOME button from any app and land on a clean Curro home screen (clock + mic + favourites) — not a half-cancelled "Llamando…" overlay from minutes ago — **so that** Curro feels the same every time I come home.

**Acceptance Criteria**:
- [ ] `app/src/main/java/com/curro/app/MainActivity.kt`: add `@Inject lateinit var coordinator: AssistantCoordinator`; override `onNewIntent(intent: Intent)`:
  ```kotlin
  override fun onNewIntent(intent: Intent) {
      super.onNewIntent(intent)
      if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
          coordinator.onHomePressed()
      }
  }
  ```
- [ ] `super.onNewIntent(intent)` is called first (Android lifecycle contract).
- [ ] `coordinator.onHomePressed()` (implemented in SF-5.2) is the only call site from `onNewIntent` — no inline cancellation glue.
- [ ] Non-HOME intent categories (or `null` categories) are no-ops — defensive against future deep-link entry points.
- [ ] **No spoken feedback** on HOME-press (pinned — HOME is navigation, not a user request; Curro speaking here would feel invasive). Visual: overlays clear because their state precondition no longer holds; the launcher home was already there.
- [ ] **No Compose nav stack manipulation** — HOME does not pop the config menu if the user happens to be there; the Compose nav stack is independent of the FSM. Phase 8 may revisit.
- [ ] JVM tests in `AssistantCoordinatorTest.kt` — append **Group O (2 cases)**:
  - `onHomePressed` from each non-Idle state (`Listening`, `Processing`, `Confirming`, `Executing`, `ErrorRecovery`) → `Idle` (parameterised).
  - `onHomePressed` cancels `currentJob` and stops TTS + STT (assert `fakeTts.wasStopped == true`, `fakeStt.wasCancelled == true`, `currentJob.isCancelled == true`).
- [ ] Instrumented test in `app/src/androidTest/java/com/curro/app/MainActivityOnNewIntentInstrumentedTest.kt`: launch `MainActivity` via `ActivityScenario`; drive the coordinator to `Listening` (test seam — pin: `coordinator.testForceListening()` `@VisibleForTesting`, consolidated with SF-5.3's `testForceExecuting`); fire `Intent(ACTION_MAIN).addCategory(CATEGORY_HOME)` via `scenario.onActivity { it.onNewIntent(homeIntent) }`; assert `coordinator.state.value is AssistantState.Idle` within 1 s.
- [ ] Manual smoke (Redmi 15): start `read_all_unread_whatsapp` with ≥ 3 messages; tap WhatsApp tile on the favourites grid mid-read; in WhatsApp, press HOME; on return, the launcher home is clean (no `ExecutingOverlay` lingering); TTS playback is silenced.
- [ ] No new permissions; no manifest changes (`launchMode="singleTask"` + `CATEGORY_HOME` were added by US-009).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green; `./gradlew connectedDebugAndroidTest` green.

**Size**: S  ·  **Depends on**: US-035 (`HomePressed` event), US-036 (`coordinator.onHomePressed()`).

---

## Phase 6 — Confidence-graded confirmation _(spec §4.3, §14 step 6)_

> `needs_confirmation` ∈ {`false`,`true`,`conditional`} · `conditional`: ≥0.85
> execute / 0.60–0.85 confirm / <0.60 clarify · thresholds adjustable from settings ·
> always escalate to confirm on explicit ambiguity or "always confirm" mode.

### US-041: `ConfidencePolicy` + `SettingsRepository` (DataStore) + threshold defaults  ·  _(master-plan SF-6.1, spec §4.3 / §9, function-catalog rule 3, voice-interaction "confidence-graded confirmation", local-data "DataStore (settings)")_
**As** Fran's father, **I want** Curro to ask "¿llamo a Pepito?" when he isn't sure he heard me right (and to skip the question when he clearly did), **so that** I don't end up ringing the wrong person — and **so that** Fran can fine-tune that threshold later. **As a** Curro developer, **I want** a single `ConfidencePolicy` (pure function over `(needs_confirmation, confidence, isAmbiguous, alwaysConfirmToggle, thresholds)`) and the first DataStore-backed settings file (`curro_settings`) **so that** Phase 8's threshold sliders and "always confirm" toggle plug into something that already exists, and the policy can be unit-tested exhaustively without booting the FSM.

**Acceptance Criteria**:
- [ ] **NEW** `app/src/main/java/com/curro/app/domain/repository/SettingsRepository.kt` — interface with three Flow getters (`executeThreshold: Flow<Float>`, `confirmThreshold: Flow<Float>`, `alwaysConfirm: Flow<Boolean>`) and three suspend setters; setters clamp out-of-range values (`executeThreshold ∈ [0f, 1f]`; `confirmThreshold ∈ [0f, executeThreshold]`); the `setExecuteThreshold(value < currentConfirm)` path also lowers `confirmThreshold` to keep the pair consistent (logs at WARN, never throws).
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/SettingsDataStore.kt` — `@Singleton` DataStore-Preferences impl, file name `curro_settings`, keys `confidence_execute_min` / `confidence_confirm_min` / `always_confirm`, defaults `0.85f / 0.60f / false` returned on first read; uses the module-private `private val Context.dataStore by preferencesDataStore("curro_settings")` extension pattern (the canonical AndroidX idiom).
- [ ] **NEW** `app/src/main/java/com/curro/app/di/SettingsModule.kt` — `@Binds @Singleton SettingsRepository → SettingsDataStore`.
- [ ] **`gradle/libs.versions.toml`**: re-flag the existing `datastore` (1.1.1) + `datastore-preferences` comment from `# Activated in SF-7.1` to `# Activated in SF-6.1` (no version bump). **`app/build.gradle.kts`**: add `implementation(libs.datastore.preferences)` to the existing dependencies block.
- [ ] **NEW** `app/src/main/java/com/curro/app/assistant/ConfidencePolicy.kt` (with co-located `data class PolicyInputs(...)` + `enum class ConfidenceDecision { Execute, Confirm, Clarify }`): a `@Singleton class` with one pure `fun decide(inputs: PolicyInputs): ConfidenceDecision`. Precedence (top → bottom): `isAmbiguous → Confirm`; `needsConfirmation = YES → Confirm`; `confidence < confirmThreshold → Clarify` (applies to NO + CONDITIONAL alike); `needsConfirmation = NO → Execute`; (CONDITIONAL from here) `alwaysConfirmToggle → Confirm`; `confidence ≥ executeThreshold → Execute`; otherwise `Confirm`.
- [ ] **AssistantCoordinator changes**: inject `SettingsRepository` + `ConfidencePolicy`. In `onDecisionSuccess`, look up the `CatalogFunction` via `Fase1Catalog.functions.firstOrNull { it.name == call.action }`; build a `PolicyInputs(...)` (read thresholds via `.first()`; `isAmbiguous = false` and `alwaysConfirmToggle = false` until SF-6.3 / SF-6.4 wire them); call `policy.decide(...)`; branch on the result. `Execute` → dispatcher.dispatch + existing `renderHandlerResult`. `Confirm` → build `PendingAction(functionName, onConfirm = { dispatcher.dispatch(call) })`, emit `FunctionCallReady(needsConfirmation = true, prompt = buildConfirmPrompt(call), expiresAtMs = now + 10_000L, pendingAction)`, TTS the prompt — STOP HERE (SF-6.2 wires the SÍ/NO + timer). `Clarify` → emit the new `AssistantEvent.LowConfidenceClarify(copy_clarify_intent)` → TTS → `RecoverySpoken`.
- [ ] **Remove the Phase-5 auto-confirm short-circuit** in `renderHandlerResult.NeedsConfirmation` (auto-recursion into `result.onConfirm()`); replace with a real transition into `Confirming` via the same `FunctionCallReady(needsConfirmation = true, …)` path with `PendingAction(call.action, onConfirm = result.onConfirm)`. (No Phase-1 handler currently returns `NeedsConfirmation`; this is forward-prep for Phase-2 `send_whatsapp_reply`.)
- [ ] **NEW `AssistantEvent.LowConfidenceClarify(message: String)`** appended to `AssistantEvent.kt`. **`AssistantStateMachine.computeNext`**: `Processing + LowConfidenceClarify → ErrorRecovery(message, failureCount = 0)`; any other state → `null` (throws `IllegalAssistantTransition`).
- [ ] **`buildConfirmPrompt(call)`** in the coordinator: for `call_contact`, format `copy_confirm_call` with `params["contact"]`; for any other catalog function, defensive fallback to `copy_clarify_intent` (no Phase-1 function reaches this branch).
- [ ] **NEW string** `app/src/main/res/values/strings.xml`: `copy_clarify_intent` = "No te he entendido bien, ¿quieres llamar a alguien?" (verbatim spec §4.3). All other confirmation strings (`copy_confirm_call`, `copy_calling_confirmed`, `copy_cancel_no_call`, `copy_confirm_timeout`, `copy_yes`, `copy_no`) **already exist** — verified on-disk.
- [ ] **Telemetry**: register `"policy_decided" to setOf("function_name", "decision", "confidence_bucket", "always_confirm_on")` in `TelemetryGuardrail.ALLOWED_PROPS`. Coordinator emits this event on every successful policy decision (including the always-confirm-on case wired in SF-6.4). `confidence_bucket` ∈ {`low`, `mid`, `high`} (≤ 8 chars to clear the guardrail's 32-char heuristic). 3 new fixture cases in `TelemetryGuardrailTest`.
- [ ] **JVM tests** — `app/src/test/java/com/curro/app/assistant/ConfidencePolicyTest.kt`: ~36 cases across 7 groups: (A) `needsConfirmation = NO` × 3 confidence buckets × ambig/toggle variants — 6 cases; (B) `needsConfirmation = YES` × confidence/flags — 4 cases; (C) `CONDITIONAL` × 3 confidence buckets × no flags — 6 cases; (D) `CONDITIONAL` + ambig precedence — 4 cases (incl. `ambig + 0.40 → Confirm`, NOT `Clarify` — spec §4.3 always-escalate #1 supersedes clarify); (E) `CONDITIONAL` + toggle precedence — 4 cases (incl. `toggle + 0.40 → Clarify`, NOT `Confirm` — toggle does NOT override clarify); (F) custom thresholds — 6 cases; (G) defensive boundaries (`0.0`, `1.0`, exact-equal, degenerate equal thresholds) — 6 cases. Each `@Test` named explicitly (no parameterisation — readable CI failures).
- [ ] **JVM Robolectric tests** — `app/src/test/java/com/curro/app/data/local/SettingsDataStoreTest.kt`: ~10 cases — `firstRead_returnsDefaults`; `setExecute_roundTrips`; `setConfirm_roundTrips`; `setAlwaysConfirm_roundTrips`; `setExecute_above1f_clampsTo1f`; `setExecute_belowZero_clampsToZero`; `setExecute_belowConfirm_alsoLowersConfirm`; `setConfirm_aboveExecute_clampsToExecute`; `setExecute_emitsToCollectors` (Turbine); `defaults_areReused_acrossProcessRestart`.
- [ ] **`AssistantCoordinatorTest.kt` — 6 new cases**: `callContact_0.95_executes`; `callContact_0.72_confirms` (FSM stops in `Confirming`; `dispatcher.dispatch` NOT called yet); `callContact_0.40_clarifies` (FSM `Processing → ErrorRecovery(copy_clarify_intent, 0) → Idle`; `sttFailureCounter.recordFailure` NEVER called); `tellTime_0.40_clarifies` (NO-confirm action still clarifies under <0.60); `callContact_0.95_emitsPolicyTelemetry_execute`; `callContact_0.72_emitsPolicyTelemetry_confirm`. Use `FakeSettingsRepository` (introduced here for the Phase-6 batch).
- [ ] No new permissions, no manifest changes.
- [ ] Every Phase-5 + Phase-4 test still passes.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.

**Size**: M  ·  **Depends on**: US-036 (SF-5.2 `AssistantCoordinator`), US-039 (SF-5.5 overlay routing — Confirming branch is still `Unit`; SF-6.2 wires it).

---

### US-042: `ConfirmationOverlay` — SÍ / NO + voice yes/no + 10-s silence timer  ·  _(master-plan SF-6.2, spec §4.3 / §6 flow 2 / §11, voice-interaction "confirming behaviour", launcher-ui surface 3)_
**As** Fran's father, **I want** Curro to show me two huge buttons and ALSO accept "sí" / "no" by voice when he asks "¿llamo a Pepito?" — and to give up politely after 10 seconds of silence instead of nagging — **so that** I can confirm without having to look at the screen if I don't want to, and silence isn't treated as a problem. **As a** Curro developer, **I want** the `Confirming`-state overlay built on the existing `BigYesNoRow` brick, plus a constrained-vocabulary STT pass (`SttClient.listenForConfirmation`) racing with a `delay(10_000L)` timer — both cancelled by the SF-5.3 interrupt rule — **so that** the FSM's `Confirming` state finally has a body and Phase-2's future `send_whatsapp_reply` confirmation reuses the same overlay.

**Acceptance Criteria**:
- [ ] **NEW** `app/src/main/java/com/curro/app/presentation/assistant/ConfirmationOverlay.kt` — `ConfirmationOverlay(state: AssistantState.Confirming, onYes, onNo, modifier)` + stateless `ConfirmationOverlayContent(prompt, onYes, onNo, modifier)`. Layout: centred `Column` over `MaterialTheme.colorScheme.surface`; prompt at `displayMedium`, `liveRegion = Polite`, `TextAlign.Center`; `BigYesNoRow` (SF-0.5 brick) below. 4 `@Preview`s (light, dark, `fontScale = 1.5f`, `fontScale = 2.0f`).
- [ ] **`LauncherPlaceholderScreen.kt`**: replace the Phase-5 `is AssistantState.Confirming -> Unit` branch with `ConfirmationOverlay(state = s, onYes = { vm.onEvent(LauncherEvent.UserConfirmed) }, onNo = { vm.onEvent(LauncherEvent.UserRejected) }, modifier = Modifier.fillMaxSize())`. (SF-6.3 will route between this and `ContactPickerOverlay` based on `pendingAction.kind`.)
- [ ] **NEW `LauncherEvent.UserConfirmed`** and **`LauncherEvent.UserRejected`** (both `data object`s). `LauncherViewModel.onEvent` forwards each to `coordinator.onUserConfirmed()` / `onUserRejected()`.
- [ ] **`SttClient` extended** — `domain/repository/SttClient.kt`: add `fun listenForConfirmation(): Flow<ConfirmationVoice>` returning exactly one terminal event (`Yes` / `No` / `Other(text)` / `Failed(error)`). The impl in `SpeechRecognizerSttClient` opens a recogniser session with `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_WEB_SEARCH`, normalises the result (`lowercase + strip accents`), and maps to `ConfirmationVoice` via this vocabulary: **Yes** = `{sí, si, vale, claro, dale, venga, okay, ok}`; **No** = `{no, cancela, cancelar, déjalo, dejalo, no llames, no quiero}`; anything else → `Other(text)`; `ERROR_NO_MATCH` / empty → `Failed(SttNoMatch)`.
- [ ] **`AssistantEvent` refactor** — convert `data object UserRejected` → `data class UserRejected(val speech: String, val screen: AssistantScreen?)`; convert `data object ConfirmationTimedOut` → `data class ConfirmationTimedOut(val speech: String)`. Mirror `UserConfirmed`'s signature so all three Confirming-exit events transition through `Executing(speech, screen)` rather than direct-to-`Idle` — gives TTS a chance to speak the resolution line per spec §4.6.
- [ ] **`AssistantStateMachine.computeNext`** updates: `Confirming + UserRejected → Executing(event.speech, event.screen)`; `Confirming + ConfirmationTimedOut → Executing(event.speech, null)`. (The Phase-5 direct `Confirming → Idle` for both events is replaced.)
- [ ] **`AssistantCoordinator`** — bodies of `onUserConfirmed()` / `onUserRejected()` (Phase 5 stubs). New private state: `confirmationListenerJob: Job?`, `confirmationTimeoutJob: Job?`, `pendingActionRef: PendingAction?`. New private helper `startConfirmationListening(pendingAction, expiresAtMs)` — launches the `while (isActive) sttClient.listenForConfirmation().collect { … }` loop (Yes → onUserConfirmed; No → onUserRejected; Other/Failed → no-op, the outer loop relaunches; the 10-s timer counts independently) AND launches `delay((expiresAtMs - now).coerceAtLeast(0L))` → `onConfirmationTimedOut`. Extend `cancelInFlight()` to call `cancelConfirmationJobs()` (cancels both new jobs). **Pin: TTS the prompt suspends to completion BEFORE `startConfirmationListening` is called** (no audio feedback loop).
- [ ] **Spoken lines**: `onUserConfirmed` → `copy_calling_confirmed` ("Vale, llamando."); `onUserRejected` → `copy_cancel_no_call` ("Vale, no llamo."); `onConfirmationTimedOut` → `copy_confirm_timeout` ("Cancelo entonces."). All four strings **already exist** on-disk — no `strings.xml` changes.
- [ ] **`onUserConfirmed` invocation order**: emit `UserConfirmed` event → suspend on `ttsClient.speak(copy_calling_confirmed)` → invoke `pendingAction.onConfirm()` (which runs `dispatcher.dispatch(originalCall)` and fires `ACTION_CALL`) → emit `ExecutionDone`. The `Spoken` return value from the handler is **discarded** for TTS purposes — Android's call screen takes over. The pendingAction's side effect (the Intent) is what matters.
- [ ] **Interrupt rule extended** (regression for SF-5.3): pressing the mic in `Confirming` cancels `confirmationListenerJob`, `confirmationTimeoutJob`, TTS, AND any in-flight STT — verified with a test.
- [ ] **JVM tests** — append to `AssistantCoordinatorTest.kt` (5 new cases): `confirming_tapYes_executesPendingAction_speaksConfirmed`; `confirming_tapNo_skipsPendingAction_speaksCancelNoCall`; `confirming_voiceYes_sameAsTapYes` (use `FakeSttClient.confirmationFlow = flowOf(Yes)`); `confirming_voiceNo_sameAsTapNo`; `confirming_10sSilence_firesTimeout_speaksCancelEntonces` (uses `TestScope.testScheduler.advanceTimeBy(10_000)`). Plus **1 interruption test**: `confirming_micPressed_cancelsBothJobs_andTts` (under the existing SF-5.3 interruption suite).
- [ ] **JVM Robolectric test** — `SpeechRecognizerSttClientTest.listenForConfirmation_mapsVocabularyCorrectly`, parameterised: ("sí" → Yes), ("vale" → Yes), ("claro" → Yes), ("no" → No), ("cancela" → No), ("hola Lucía" → Other("hola lucía")), ("" → Failed(SttNoMatch)).
- [ ] **Compose UI tests** — `app/src/androidTest/java/com/curro/app/presentation/assistant/ConfirmationOverlayTest.kt`: `prompt_isVisible`; `yesButton_isAtLeast96dp_widthAndHeight`; `noButton_isAtLeast96dp_widthAndHeight`; `tapYes_invokesOnYes`; `tapNo_invokesOnNo`; `prompt_hasPoliteLiveRegion`; `darkMode_renders`; `fontScale_2_0_doesNotClip`.
- [ ] No new permissions, no new manifest entries, no new strings (verified — see line 24 / 30 / 34 / 38 / 40 / 46 of `strings.xml`).
- [ ] Every SF-6.1 + Phase-5 + Phase-4 test still passes.
- [ ] Manual smoke (Redmi 15): drive `Confirming` (any mid-confidence `call_contact`) and verify all four outcomes (tap SÍ, tap NO, voice "sí", voice "no", 10-s silence) end the FSM in `Idle` with the correct spoken+shown line; verify `ACTION_CALL` only fires on a SÍ path; verify TTS finishes the prompt before the recogniser opens (no audio loop).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green; `./gradlew connectedDebugAndroidTest` green.

**Size**: M  ·  **Depends on**: US-041 (`ConfidencePolicy` reaches `Confirming`), US-039 (overlay routing infrastructure).

---

### US-043: Disambiguation flow + `ContactPickerOverlay` — the 3-Marías case  ·  _(master-plan SF-6.3, spec §6 flow 3 + §7 "no aprender alias dentro de la desambiguación", function-catalog rule 3 / always-escalate case #1, voice-interaction "disambiguation list", launcher-ui surface 5, local-data rule 3)_
**As** Fran's father, **I want** Curro to show me a list of all three Marías when I say "llama a María" — with their full names, photos if they have them, and a clear "Ninguna" option — and **also** let me say a name or "la primera" by voice **so that** I don't get stuck calling the wrong one, and I don't have to read three names that look alike if I can just say one. **As a** Curro developer, **I want** the `Confirming`-state plumbing (SF-6.2) extended with a `PendingAction.Kind.PickContact(candidates, onPick)` shape, a new `HandlerResult.NeedsContactPick`, and a new `SttClient.listenForPicker(candidates)` pass that recognises first names, full names, ordinals, and "ninguna" — **so that** `CallContactHandler` finally handles multi-match for real and the same overlay can be reused by Phase 7's alias-learning subflow.

**Acceptance Criteria**:
- [ ] **NEW** `app/src/main/java/com/curro/app/presentation/assistant/ContactPickerOverlay.kt` — `ContactPickerOverlay(state: AssistantState.Confirming, onPick, onNone, modifier)` (early-returns if `state.pendingAction.kind !is Kind.PickContact`) + stateless `ContactPickerOverlayContent(prompt, candidates, onPick, onNone, modifier)`. Layout: prompt at `displaySmall` + `liveRegion = Polite`; `LazyColumn` of up to 3 `BigCard` rows (≥ 96 dp each; `Coil` `AsyncImage` for `photoUri` if present + display name at `headlineSmall`); for ≥ 4 candidates a "Más" `BigListRow` that expands the LazyColumn in place (`var moreExpanded by remember { mutableStateOf(false) }`); always-last "Ninguna" `BigListRow` styled distinctly (`surfaceContainerLow` background). 5 `@Preview`s (3-cand light, 3-cand dark, 4-cand collapsed, 4-cand expanded, `fontScale = 2.0f`).
- [ ] **`PendingAction` refactor** — `app/src/main/java/com/curro/app/assistant/PendingAction.kt`: `data class PendingAction(val functionName: String, val kind: Kind)`; `sealed interface Kind { data class YesNo(val onConfirm: suspend () -> HandlerResult) : Kind; data class PickContact(val candidates: List<Contact>, val onPick: suspend (Contact?) -> HandlerResult) : Kind }`. The SF-6.1 / SF-6.2 callers that built `PendingAction(functionName, onConfirm)` are migrated to `PendingAction(functionName, Kind.YesNo(onConfirm))`.
- [ ] **`HandlerResult.NeedsContactPick`** — append to the sealed interface in `domain/handler/HandlerResult.kt`: `data class NeedsContactPick(val prompt: String, val candidates: List<Contact>, val onPick: suspend (Contact?) -> HandlerResult) : HandlerResult`.
- [ ] **`CallContactHandler` change** — replace the Phase-4 `candidates.size > 1 → Failed(copy_contact_ambiguous_phase4, AmbiguousContact)` branch with a `NeedsContactPick` returning a `buildDisambigPrompt(rawQuery, candidates)` and an `onPick` lambda: `onPick(null)` → `Spoken(copy_cancel_no_call)`; `onPick(contact)` → `placeCallOrFail(contact, rawQuery)`. The prompt builder selects `copy_disambig_ask_two(_masc)` for size 2, `copy_disambig_ask_three(_masc)` for size 3, or `copy_disambig_ask_n` for size ≥ 4. **Gender heuristic**: query's lowercase last char `== 'o'` → masculine, else feminine (acceptable for prototype; document the heuristic's Kdoc on the helper).
- [ ] **NEW** `SttClient.listenForPicker(candidates: List<Contact>): Flow<PickerVoice>` returning exactly one terminal event (`Pick(contact)` / `None` / `Other(text)` / `Failed(error)`). Vocabulary: per index 0/1/2, ordinal triggers `{primera, primero, la primera, el primero}` / `{segunda, segundo, la segunda, el segundo}` / `{tercera, tercero, la tercera, el tercero}`; each candidate's `displayName.split(' ').first().normalised`; each candidate's full `displayName.normalised`; `None` triggers `{ninguna, ninguno, ningún, nadie, ninguno de estos, ninguna de estas}`. **Edge**: if two candidates share the same first-name normalised form, a first-name-only voice match falls through to `Other(text)` — user must say the full name or an ordinal. Pin in the impl.
- [ ] **`AssistantCoordinator.renderHandlerResult` new branch** for `HandlerResult.NeedsContactPick`: `disambigMissCount = 0`; build `PendingAction(call.action, Kind.PickContact(candidates, onPick))`; transition to `Confirming(prompt, now + 10_000L, pendingAction)`; suspend on TTS the prompt; call new `startPickerListening(candidates, pendingAction, now + 10_000L)`.
- [ ] **`startPickerListening`** — launches `pickerListenerJob` (a `while (isActive)` loop over `sttClient.listenForPicker(candidates)`; `Pick(c)` → `onPickerPicked(c, pendingAction)`; `None` → `onPickerNone(pendingAction)`; `Other`/`Failed` → if `disambigMissCount == 0` increment + re-TTS the prompt + relaunch inner collect, else `onPickerGiveUp(pendingAction)`) and reuses the SF-6.2 `confirmationTimeoutJob` (same 10-s timer; **NOT reset on a miss** — silence wins eventually).
- [ ] **`onPickerPicked(contact, pendingAction)`** — cancels both jobs; invokes `kind.onPick(contact)`; if `Spoken` emit `UserConfirmed(result.speech, result.screen)` + TTS; if `Failed` same path (`UserConfirmed(result.speech, null)`). **`onPickerNone(pendingAction)`** — cancels jobs; invokes `kind.onPick(null)` (returns `Spoken(copy_cancel_no_call)`); transitions via `UserConfirmed`. **`onPickerGiveUp(pendingAction)`** — cancels jobs; picks the give-up copy via the gender heuristic on `kind.candidates.first().displayName` (`endsWith("o")` → `copy_disambig_give_up_masc`, else `copy_disambig_give_up`); transitions via `UserConfirmed`.
- [ ] **`cancelInFlight` extended** to call `cancelPickerJobs()` (cancels `pickerListenerJob` + `confirmationTimeoutJob`, resets `disambigMissCount`).
- [ ] **`LauncherPlaceholderScreen.kt`** — the `is AssistantState.Confirming` overlay branch now routes: `when (val k = s.pendingAction.kind) { is Kind.YesNo -> ConfirmationOverlay(s, onYes, onNo, modifier); is Kind.PickContact -> ContactPickerOverlay(s, onPick = { c -> vm.onEvent(LauncherEvent.PickerPicked(c)) }, onNone = { vm.onEvent(LauncherEvent.PickerNone) }, modifier) }`.
- [ ] **NEW `LauncherEvent.PickerPicked(contact: Contact)`** + **`LauncherEvent.PickerNone`**. `LauncherViewModel` forwards to `coordinator.onPickerPicked(contact, currentPendingAction)` / `coordinator.onPickerNone(currentPendingAction)` (the VM reads the current state's pendingAction; pin in the impl).
- [ ] **Strings** — `app/src/main/res/values/strings.xml`: **NEW** `copy_disambig_ask_two` = "Tienes %1$d %2$ss. ¿Cuál de ellas?: %3$s o %4$s."; **NEW** `copy_disambig_ask_two_masc` = "Tienes %1$d %2$ss. ¿Cuál de ellos?: %3$s o %4$s."; **NEW** `copy_disambig_more_label` = "Más". `copy_disambig_ask_three(_masc)`, `_ask_n`, `_give_up(_masc)`, `_none_option(_masc)`, `copy_cancel_no_call`, `copy_calling`, `copy_contact_not_found` **already exist** — verified on-disk (lines 92–104, 30, 44, 159). Leave `copy_contact_ambiguous_phase4` in place (unreachable after this SF; comment it as "deleted by SF-6.3, removable in a Phase-8 cleanup").
- [ ] **No alias-learning side effect** — `aliases.upsertAlias(...)` (or equivalent write API on `AliasRepository`) is NEVER called during the picker flow. Verified by a fake `AliasRepository` recording writes (assert size == 0 across all picker tests). Spec §7 + flow 4 note + `local-data` rule 3 enforcement.
- [ ] **`CallContactHandlerTest` — 6 new cases appended**: `findByName_returnsTwoMatches_returnsNeedsContactPick_withAskTwoCopy`; `findByName_returnsThreeFemale_useAskThreeFeminineCopy`; `findByName_returnsThreeMale_useAskThreeMasculineCopy` (query "Pepito"); `findByName_returnsFour_useAskN`; `onPick_validContact_callsController_returnsSpokenCalling`; `onPick_null_returnsSpokenCancelNoCall`.
- [ ] **`AssistantCoordinatorTest` — 7 new cases appended**: `pick_tapResolvesContact_placesCall`; `pick_voiceFirstName_placesCall`; `pick_voiceOrdinal_primera_pickFirstCandidate`; `pick_voiceNinguna_speaksCancelNoCall_noCallPlaced`; `pick_firstMissReAsks_secondMissGivesUp_feminine`; `pick_timeoutFires_speaksCancelEntonces`; `pick_micPressed_cancelsAllJobs_returnsToListening`. Plus **`pick_neverCallsAliasRepository`** (zero-writes assertion across the suite).
- [ ] **`SpeechRecognizerSttClientTest.listenForPicker_mapsVocabularyCorrectly`** — parameterised over the picker vocabulary (full names, first names, ordinals × 3 indices, "ninguna" variants, ambiguous shared-first-name → `Other`).
- [ ] **`ContactPickerOverlayTest`** (instrumented Compose UI test): 3 candidates → 4 visible tap targets; 4 candidates → 3 rows + "Más" + "Ninguna"; tap "Más" → row expands; each row ≥ 96 dp tall; tap candidate → `onPick(c)`; tap "Ninguna" → `onNone`; large-font (`2.0f`) regression.
- [ ] **Pin: TTS order before `ACTION_CALL`** — the `placeCallOrFail` refactor in the handler must let the coordinator suspend on TTS (`copy_calling`) **before** the `CallController.placeCall(number)` Intent fires; otherwise Android's call screen overlays the spoken line. Implementer designs the exact shape; pin: verify with a manual-smoke test that "Llamando a María López." finishes before the call screen appears.
- [ ] No new permissions; no new manifest entries.
- [ ] Every SF-6.1 + SF-6.2 + Phase-5 + Phase-4 test still passes.
- [ ] Manual smoke (Redmi 15): three "María …" contacts → "llama a María" → picker overlay reads prompt + lists 3 rows + "Ninguna" → (a) tap María López → call placed; (b) voice "María García" → call placed; (c) voice "la primera" → call placed; (d) voice "ninguna" → "Vale, no llamo." + back to launcher; (e) voice "la cuarta" then "la quinta" → `copy_disambig_give_up` + back to launcher; (f) 10-s silence → "Cancelo entonces." + back to launcher. Four "María …" contacts → "Más" row appears + expands inline.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green; `./gradlew connectedDebugAndroidTest` green.

**Size**: M  ·  **Depends on**: US-042 (the `Confirming` overlay routing + 10-s timer + `cancelInFlight` extension).

---

### US-044: "Always confirm" toggle wired into `ConfidencePolicy`  ·  _(master-plan SF-6.4, spec §4.3 always-escalate case #3 / §9 "confirma siempre", function-catalog rule 3)_
**As** Fran (configuring his father's phone), **I want** a `DataStore` flag I can flip later (from Phase 8's settings menu) that forces every `CONDITIONAL` action to confirm regardless of FunctionGemma's confidence — **so that** during the first weeks of his usage Curro asks "¿llamo a Pepito?" even on high-confidence inputs and we can validate trust gradually. **As a** Curro developer, **I want** the SF-6.1 hard-coded `alwaysConfirmToggle = false` replaced with a `settingsRepository.alwaysConfirm.first()` read inside the coordinator — **so that** SF-6.1's policy precedence works end-to-end and Phase 8's UI is a pure UI change.

**Acceptance Criteria**:
- [ ] **`AssistantCoordinator.onDecisionSuccess`** — capture `val alwaysConfirm = settingsRepository.alwaysConfirm.first()` once per turn; pass it as `alwaysConfirmToggle` in `PolicyInputs(...)`; propagate it to `emitPolicyTelemetry(call.action, decision, call.confidence, alwaysConfirm)` so the `policy_decided` event's `always_confirm_on` prop reflects the real value.
- [ ] **No other source files change.** No new strings, no new permissions, no manifest entries, no new dependencies.
- [ ] **`AssistantCoordinatorTest` — 2 new cases appended**: `alwaysConfirmFalse_callContactHighConfidence_executes` (FakeSettingsRepository.alwaysConfirmValue = false; `call_contact` confidence 0.95; FSM ends in `Idle` via `Executing`; `dispatcher.dispatch` called once; no `Confirming` entry); `alwaysConfirmTrue_callContactHighConfidence_confirms` (FakeSettingsRepository.alwaysConfirmValue = true; same input; FSM ends in `Confirming` after `onDecisionSuccess`; `dispatcher.dispatch` NOT yet called; `pendingAction.kind` is `Kind.YesNo`). Also assert that `alwaysConfirm = true` + `tell_time` 0.95 still `Execute`s (the toggle only fires inside CONDITIONAL — regression for SF-6.1 precedence), and `alwaysConfirm = true` + `call_contact` 0.40 still `Clarify`s (the toggle does NOT escalate clarifies to confirms — regression for SF-6.1 case E #23).
- [ ] **`SettingsDataStoreTest`** — verify the SF-6.1 cases `setAlwaysConfirm_true_roundTrips` and `alwaysConfirm_defaultsToFalse` are present and green. (SF-6.1's brief lists them; SF-6.4 only verifies, does not duplicate.)
- [ ] **Optional debug-only affordance** (recommended but not an AC blocker): `app/src/debug/java/com/curro/app/debug/AlwaysConfirmToggleReceiver.kt` — a debug-variant `BroadcastReceiver` registered in `app/src/debug/AndroidManifest.xml` listening for `com.curro.app.DEBUG_TOGGLE_AC`. `onReceive` flips `settingsRepository.alwaysConfirm` and logs the new value. `exported="true"` is safe (debug variant only — receiver is absent from the release APK). The receiver enables manual smoke without booting Phase 8's UI. Implementer chooses `@AndroidEntryPoint` or `EntryPointAccessors` for the Hilt injection.
- [ ] Every SF-6.1 + SF-6.2 + SF-6.3 + Phase-5 + Phase-4 test still passes.
- [ ] Manual smoke (Redmi 15): with the debug receiver registered: default `alwaysConfirm = false` → "llama a Pepito" goes direct (Execute); fire `adb shell am broadcast -a com.curro.app.DEBUG_TOGGLE_AC` → flag flips to true; "llama a Pepito" → `Confirming` overlay paints; SÍ → call placed; broadcast again → flag flips back; "llama a Pepito" → direct again.
- [ ] **Pin: no settings-menu UI**. SF-6.4 is intentionally tiny — Phase 8's PM batch owns the menu shape. Resist the temptation to add a quick toggle to the Phase-0 `ConfigMenuPlaceholderScreen`.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.

**Size**: S  ·  **Depends on**: US-041 (the policy + DataStore key + telemetry whitelist all exist).

---

## Phase 7 — Alias learning & local persistence _(spec §7, flow 4, §14 step 7)_

> Local DB (Room/SQLite or DataStore): contact aliases, implicit favourite apps,
> usage times, failed-command log · learn **one alias per interaction**, never
> mid-call · aliases viewable/editable from the settings menu.

### US-045: Room database + DAOs + Hilt `DatabaseModule`  ·  _(master-plan SF-7.1, spec §7, local-data "Room schema (sketch)" / rules 1+4, function-catalog "Prompt context")_
**As** Fran's father, **I want** Curro to actually remember what I've taught it (the alias for "mi hija", the apps I open most, the commands it failed on) **so that** I don't have to teach it the same thing twice and Fran can see what's going wrong. **As a** Curro developer, **I want** the first Room database in Curro — `CurroDatabase` v1 with `ContactAliasEntity`/`AppUsageEntity`/`FailedCommandEntity`, three DAOs, a Hilt `DatabaseModule`, schema export to `app/schemas/`, and a `fallbackToDestructiveMigration` prototype escape hatch — **so that** SF-7.2/7.3/7.4/7.5 plug into a stable schema and DAO tests cover the gotchas (alias-uniqueness, fail-log cap-at-50, app-usage upsert idempotency) before any handler depends on them.

**Acceptance Criteria**:
- [ ] **`gradle/libs.versions.toml`**: re-flag the Room comment block from `# Activated in SF-7.1` (already wired pre-declaration) — verify `room = "2.6.1"` version and the three library entries `room-runtime` / `room-ktx` / `room-compiler` are present (they are, lines 123–125). **No version bump.** **`app/build.gradle.kts`**: in the dependencies block, replace the "Room → SF-7.1: …" reserved-comment line with three live entries: `implementation(libs.room.runtime)`, `implementation(libs.room.ktx)`, `ksp(libs.room.compiler)`. Also add the KSP arg `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` at the top level of the `android { }` closure's siblings (where the Detekt closure lives) — pin: schema location must resolve at configuration time.
- [ ] **NEW** `app/schemas/` directory + `.gitkeep` placeholder. After the first `assembleDebug`, `app/schemas/com.curro.app.data.local.CurroDatabase/1.json` is generated and committed (the generated JSON is the source of truth for future migrations).
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/CurroDatabase.kt` — `@Database(entities = [ContactAliasEntity::class, AppUsageEntity::class, FailedCommandEntity::class], version = 1, exportSchema = true)` `abstract class CurroDatabase : RoomDatabase()` with three abstract DAO accessors (`contactAliasDao()` / `appUsageDao()` / `failedCommandDao()`). `InteractionLogEntity` is **deferred to Phase 8** (`local-data` lists it as Phase-4-proactive hook; out of scope for Phase 7 since no caller writes it yet).
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/AliasSource.kt` — `enum class AliasSource { LEARNED, EXPLICIT, SUGGESTED }`. Spec §7: `LEARNED` = the alias-learning subflow (SF-7.3); `EXPLICIT` = Fran pre-loaded via the Phase-8 config menu; `SUGGESTED` = (deferred — Phase-8 onboarding wizard).
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/ContactAliasEntity.kt` — `@Entity(tableName = "contact_aliases", indices = [Index(value = ["alias"], unique = true)]) data class ContactAliasEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val alias: String, val lookupKey: String, val displayName: String, val source: AliasSource, val createdAtMs: Long, val lastUsedAtMs: Long, val useCount: Int = 0)`. **Pin: `alias` is the normalised form (lowercase + accent-stripped, via `curroNormalize()`)** — uniqueness enforces "one alias per spoken phrase". **Pin: `lookupKey` is `ContactsContract.Contacts.LOOKUP_KEY`** (per `local-data` rule 1 + `Contact.kt`'s Kdoc) — survives contact renames and provider re-indexing.
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/AppUsageEntity.kt` — `@Entity(tableName = "app_usage") data class AppUsageEntity(@PrimaryKey val packageName: String, val openCount: Int = 0, val lastOpenedAtMs: Long)`. Note: `packageName` IS the primary key (no autoGenerate id) — the upsert pattern is "bump by package".
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/FailureKind.kt` — `enum class FailureKind { INVALID_OUTPUT, UNKNOWN_FUNCTION, HANDLER_ERROR }`. Mirrors the three Phase-3+ failure paths in `AssistantCoordinator.onDecisionFailure` + `HandlerDispatcher`.
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/FailedCommandEntity.kt` — `@Entity(tableName = "failed_commands") data class FailedCommandEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val transcript: String, val kind: FailureKind, val details: String = "", val timestampMs: Long)`. **Privacy pin: `transcript` is PII per spec §12** — stored locally only, MUST NEVER be serialised to telemetry. The Phase-8 config menu reads this table for Fran's review only.
- [ ] **NEW** Three converter objects (or one) — co-locate in `app/src/main/java/com/curro/app/data/local/CurroTypeConverters.kt`: `@TypeConverter fun fromAliasSource(s: AliasSource): String = s.name` + `@TypeConverter fun toAliasSource(s: String): AliasSource = AliasSource.valueOf(s)`; ditto for `FailureKind`. Register via `@TypeConverters(CurroTypeConverters::class)` on `CurroDatabase`.
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/ContactAliasDao.kt` — `@Dao interface ContactAliasDao` with five methods: `@Query("SELECT * FROM contact_aliases ORDER BY useCount DESC, lastUsedAtMs DESC") fun observeAll(): Flow<List<ContactAliasEntity>>`; `@Query("SELECT * FROM contact_aliases ORDER BY useCount DESC, lastUsedAtMs DESC LIMIT :limit") suspend fun topUsed(limit: Int): List<ContactAliasEntity>` (used by SF-7.2's prompt-context injection; pin `limit = 10` at the call site); `@Query("SELECT * FROM contact_aliases WHERE alias = :alias LIMIT 1") suspend fun findByAlias(alias: String): ContactAliasEntity?`; `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: ContactAliasEntity)`; `@Query("UPDATE contact_aliases SET lastUsedAtMs = :now, useCount = useCount + 1 WHERE alias = :alias") suspend fun bumpUsage(alias: String, now: Long)`; `@Query("DELETE FROM contact_aliases WHERE alias = :alias") suspend fun delete(alias: String)`; `@Query("DELETE FROM contact_aliases") suspend fun deleteAll()` (Phase-8 reset-learning).
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/AppUsageDao.kt` — `@Dao interface AppUsageDao` with: `@Query("SELECT * FROM app_usage ORDER BY openCount DESC LIMIT :limit") suspend fun topByOpenCount(limit: Int = 20): List<AppUsageEntity>`; `@Query("SELECT * FROM app_usage ORDER BY openCount DESC LIMIT :limit") fun observeTopByOpenCount(limit: Int = 20): Flow<List<AppUsageEntity>>`; `@Query("UPDATE app_usage SET openCount = openCount + 1, lastOpenedAtMs = :now WHERE packageName = :packageName") suspend fun bumpExisting(packageName: String, now: Long): Int` (returns rows-affected); `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfMissing(entity: AppUsageEntity)`; `@Transaction open suspend fun upsert(packageName: String, now: Long) { if (bumpExisting(packageName, now) == 0) { insertIfMissing(AppUsageEntity(packageName, openCount = 1, lastOpenedAtMs = now)) } }` (pin: `open` so Room can subclass; SQLite-native `INSERT OR REPLACE` would reset `openCount` — the bump-or-insert pattern preserves it); `@Query("DELETE FROM app_usage") suspend fun deleteAll()`.
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/FailedCommandDao.kt` — `@Dao interface FailedCommandDao` with: `@Query("SELECT * FROM failed_commands ORDER BY timestampMs DESC LIMIT :limit") fun observeRecent(limit: Int = 50): Flow<List<FailedCommandEntity>>`; `@Query("SELECT COUNT(*) FROM failed_commands") suspend fun count(): Int`; `@Insert suspend fun insert(entity: FailedCommandEntity): Long`; `@Query("DELETE FROM failed_commands WHERE id NOT IN (SELECT id FROM failed_commands ORDER BY timestampMs DESC LIMIT 50)") suspend fun trimToFifty()`; `@Transaction open suspend fun insertAndTrim(entity: FailedCommandEntity) { insert(entity); trimToFifty() }`; `@Query("DELETE FROM failed_commands") suspend fun deleteAll()`. **Pin: cap-at-50 is the `local-data` rule 4 invariant** — every insert path goes through `insertAndTrim`.
- [ ] **NEW** `app/src/main/java/com/curro/app/di/DatabaseModule.kt` — `@Module @InstallIn(SingletonComponent::class) object DatabaseModule` with: `@Provides @Singleton fun provideCurroDatabase(@ApplicationContext context: Context): CurroDatabase = Room.databaseBuilder(context, CurroDatabase::class.java, "curro.db").fallbackToDestructiveMigration().build()`; `@Provides fun provideContactAliasDao(db: CurroDatabase): ContactAliasDao = db.contactAliasDao()`; `@Provides fun provideAppUsageDao(db: CurroDatabase): AppUsageDao = db.appUsageDao()`; `@Provides fun provideFailedCommandDao(db: CurroDatabase): FailedCommandDao = db.failedCommandDao()`. **Pin: `fallbackToDestructiveMigration()` is intentional for the prototype** (no users yet) — leave a Kdoc `// TODO(post-prototype): replace with real Migration objects before public release.` (the schema export to `app/schemas/` is the prerequisite for that future work).
- [ ] **JVM Robolectric tests** — `app/src/test/java/com/curro/app/data/local/ContactAliasDaoTest.kt` (~10 cases) using `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()` and `AndroidJUnit4` runner: `upsert_findByAlias_roundTrips`; `upsert_sameAlias_replacesViaOnConflict`; `findByAlias_unknown_returnsNull`; `bumpUsage_incrementsCountAndUpdatesTimestamp`; `bumpUsage_unknownAlias_isNoOp`; `delete_singleAlias_removesIt`; `deleteAll_emptiesTable`; `observeAll_emitsInUseCountDescOrder`; `topUsed_limit3_returnsTopThree`; `aliasUniquenessIndex_doesNotThrowOnDuplicate_replacesInstead` (verify the `REPLACE` semantics, since two aliases with the same `alias` would otherwise throw via the unique index; the `OnConflictStrategy.REPLACE` upgrades to UPDATE on duplicate `alias`).
- [ ] **JVM Robolectric tests** — `app/src/test/java/com/curro/app/data/local/AppUsageDaoTest.kt` (~8 cases): `upsert_newPackage_insertsWithCountOne`; `upsert_existingPackage_bumpsCount`; `upsert_existingPackage_updatesTimestamp`; `upsert_isIdempotentPerInvocation_butCumulative_across`; `topByOpenCount_returnsDescendingOrder`; `topByOpenCount_limit5_capsResult`; `observeTopByOpenCount_emitsOnUpsert` (Turbine); `deleteAll_emptiesTable`.
- [ ] **JVM Robolectric tests** — `app/src/test/java/com/curro/app/data/local/FailedCommandDaoTest.kt` (~10 cases): `insert_returnsAutoGeneratedId`; `count_reflectsInsertCount`; `insertAndTrim_keepsAllWhenUnder50`; `insertAndTrim_capsAt50WhenInserting51st` (insert 51, verify count = 50 + the oldest is gone); `insertAndTrim_capsAt50WhenInserting60_keepsNewest50` (insert 60 in a row, verify count = 50 + the 50 newest survive); `observeRecent_emitsDescendingTimestamp` (Turbine); `observeRecent_limit10_returnsTop10`; `failureKind_roundTripsAllThreeVariants` (one row per `INVALID_OUTPUT`/`UNKNOWN_FUNCTION`/`HANDLER_ERROR`); `transcript_storesUtf8WithAccents_andSpecialChars` ("¡llama a mi hija María!"); `deleteAll_emptiesTable`.
- [ ] **Build & migrations** — `./gradlew assembleDebug` succeeds; `app/schemas/com.curro.app.data.local.CurroDatabase/1.json` is generated; commit the generated file in this SF.
- [ ] **`DatabaseModule` smoke test** — `app/src/test/java/com/curro/app/di/DatabaseModuleTest.kt` (Robolectric, ~3 cases): `provideCurroDatabase_returnsNonNull`; `provideContactAliasDao_returnsDao`; `databaseInstance_isSingleton_acrossInjections` (verified via two `EntryPoints.get()` calls in a Hilt test — alternatively pin a simpler `@Provides` smoke that just asserts the DAO classes are returned).
- [ ] **No new permissions, no manifest changes.** Room is purely local; no network, no `INTERNET` change.
- [ ] **Telemetry**: no new events. (US-049 adds the `command_failed` event later; SF-7.1 is purely schema.)
- [ ] Every Phase-6 + Phase-5 + Phase-4 test still passes.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.

**Size**: M  ·  **Depends on**: SF-0.2 (Hilt graph for `DatabaseModule`).

---

### US-046: `RoomAliasRepository` real impl + alias injection into FunctionGemma prompt context  ·  _(master-plan SF-7.2, spec §7 + §6 flow 1, function-catalog "Prompt context", platform-integrations "Resolution order step 1", local-data rule 1)_
**As** Fran's father, **I want** "llama a mi hija" to call Lucía directly the next time I say it (after I've taught Curro who she is) — and for FunctionGemma to know about all my learned aliases when it tries to map my words to an action — **so that** Curro doesn't ask me twice and gets less confused on close-sounding phrases. **As a** Curro developer, **I want** `EmptyAliasRepository` swapped for `RoomAliasRepository` (queries `ContactAliasDao`, re-resolves `LOOKUP_KEY` via a new `ContactsProvider.findByLookupKey`, bumps usage on hit, returns empty on a stale lookup so the caller falls through), AND the top-10 aliases injected into `PromptContext.knownAliases` from `AssistantCoordinator.buildContext()` **so that** the existing prompt-builder template ("Alias conocidos: …") starts emitting real data.

**Acceptance Criteria**:
- [ ] **`app/src/main/java/com/curro/app/domain/repository/AliasRepository.kt`** — extend the Phase-4 interface (currently single-method `resolveAlias`) to four methods: `suspend fun resolveAlias(alias: String): List<Contact>` (unchanged signature; Phase-7 returns 0-or-1 element since each normalised alias maps to one `LOOKUP_KEY`); **NEW** `suspend fun learn(alias: String, contact: Contact, source: AliasSource): Unit` (used by SF-7.3); **NEW** `fun observeAll(): Flow<List<AliasView>>` (used by SF-8.2's alias-list UI); **NEW** `suspend fun topUsedSnapshots(limit: Int = 10): List<AliasSnapshot>` (used by the coordinator's prompt-context builder); **NEW** `suspend fun deleteAll(): Unit` (Phase-8 reset). **NEW** `data class AliasView(val alias: String, val displayName: String, val source: AliasSource, val useCount: Int)` and **NEW** `data class AliasSnapshot(val alias: String, val displayName: String)` co-located in `AliasRepository.kt`. **Pin: `Contact.lookupKey` is the wire form** — never expose `lookupKey` through `AliasView` (UI doesn't need it; isolates Phase-8 from the contacts schema).
- [ ] **`app/src/main/java/com/curro/app/domain/repository/ContactsProvider.kt`** — append a second method: `suspend fun findByLookupKey(lookupKey: String): Contact?`. Phase-4's `findByName(query)` stays. **`app/src/main/java/com/curro/app/data/contacts/ContactsContractProvider.kt`** — implement by adding a `runner.queryByLookupKey(lookupKey)` query restricted to `ContactsContract.Contacts.LOOKUP_KEY = ?`; group by `LOOKUP_KEY` (one match); return `null` if no row. **Pin: the existing `ContactsQueryRunner.query()` is widened** with `fun queryByLookupKey(lookupKey: String): List<ContactRow>` (or a single `query(lookupKeyFilter: String? = null)` overload — implementer chooses; pin the contract: returns rows matching one `LOOKUP_KEY`). Tests against `FakeContactsQueryRunner` return hand-built rows.
- [ ] **NEW** `app/src/main/java/com/curro/app/data/contacts/RoomAliasRepository.kt` — `@Singleton class RoomAliasRepository @Inject constructor(private val dao: ContactAliasDao, private val contactsProvider: ContactsProvider, private val timeProvider: TimeProvider) : AliasRepository`. `resolveAlias(alias)`: normalise (`alias.trim().lowercase().curroNormalize()`); `dao.findByAlias(normalised) ?: return emptyList()`; `contactsProvider.findByLookupKey(entry.lookupKey) ?: return emptyList()` (stale-lookup path — the caller in SF-7.3 detects this via the "alias row existed but resolveAlias returned empty" signature and triggers the re-learn flow); `dao.bumpUsage(normalised, timeProvider.now())`; `return listOf(contact)`. `learn(alias, contact, source)`: normalise; build `ContactAliasEntity(alias = normalised, lookupKey = contact.lookupKey, displayName = contact.displayName, source = source, createdAtMs = now, lastUsedAtMs = now, useCount = 0)`; `dao.upsert(entity)`. `observeAll()`: `dao.observeAll().map { it.map { e -> AliasView(e.alias, e.displayName, e.source, e.useCount) } }`. `topUsedSnapshots(limit)`: `dao.topUsed(limit).map { AliasSnapshot(it.alias, it.displayName) }`. `deleteAll()`: `dao.deleteAll()`.
- [ ] **`app/src/main/java/com/curro/app/di/ContactsModule.kt`** — replace `@Binds fun bindAliasRepository(impl: EmptyAliasRepository): AliasRepository` with `@Binds fun bindAliasRepository(impl: RoomAliasRepository): AliasRepository`. Update the Kdoc header from "Phase-7 migration: swap …" to "Phase-7 wired (US-046): aliases are Room-backed.".
- [ ] **DELETE** `app/src/main/java/com/curro/app/data/contacts/EmptyAliasRepository.kt` and its test `app/src/test/java/com/curro/app/data/contacts/EmptyAliasRepositoryTest.kt`. (Pin: the Phase-4 stub is no longer reachable; cleanup keeps the file count honest. The interface comments referencing "Phase 4 implementation" are also rewritten in `AliasRepository.kt`'s Kdoc.)
- [ ] **`app/src/main/java/com/curro/app/assistant/AssistantCoordinator.kt`** — inject `aliasRepository: AliasRepository` (Hilt resolves it; the dispatcher and `CallContactHandler` already depend on it indirectly). Modify `buildContext()` (line 949–954): replace `knownAliases = emptyList()` with `knownAliases = aliasRepository.topUsedSnapshots(PROMPT_ALIAS_LIMIT).map { "${it.alias} → ${it.displayName}" }`. **Pin: `PROMPT_ALIAS_LIMIT = 10`** — declared as a `private companion object` constant inside the coordinator. Top-10 by `useCount DESC, lastUsedAtMs DESC` (the DAO's ordering). **Pin: `buildContext()` becomes `suspend`** (now does a DAO read) — the only call site is `decideAndDispatch` which is already suspending; verify nothing else calls it.
- [ ] **`app/src/test/java/com/curro/app/data/contacts/RoomAliasRepositoryTest.kt`** (JVM Robolectric, in-memory Room + `FakeContactsProvider` + `TestTimeProvider`; ~10 cases): `resolveAlias_unknown_returnsEmpty`; `resolveAlias_known_returnsSingleContact`; `resolveAlias_knownButLookupKeyStale_returnsEmpty` (DAO has row, ContactsProvider returns null); `resolveAlias_known_bumpsUsageAndTimestamp` (verify `useCount` increments + `lastUsedAtMs` updates); `learn_persistsEntityWithSourceLEARNED`; `learn_sameAliasTwice_replacesViaUpsert` (the second `learn` overwrites the first row — `OnConflictStrategy.REPLACE` on the unique index); `observeAll_emitsAliasViews_inUseCountDescOrder` (Turbine); `topUsedSnapshots_limit3_returnsThree`; `topUsedSnapshots_empty_returnsEmpty`; `deleteAll_clearsTable_observeAllEmitsEmpty`.
- [ ] **`app/src/test/java/com/curro/app/data/ml/FunctionCallPromptBuilderTest.kt`** — append 3 cases: `aliasesBlock_emptyList_rendersNinguno` (regression — already in Phase 3); `aliasesBlock_oneAlias_rendersSingleArrowFormat` (e.g. `"mi hija → Lucía Ruiz"`); `aliasesBlock_tenAliases_rendersAllSeparatedBySemiColon` (verify the existing `"; "` separator from `FunctionCallPromptBuilder.contextBlock` handles 10 entries — golden compared byte-for-byte). **Pin: the existing `contextBlock` separator is `"; "`** (verified: line 97). The coordinator pre-formats each `AliasSnapshot` to `"alias → displayName"` so the prompt-builder doesn't need to change.
- [ ] **`app/src/test/java/com/curro/app/assistant/AssistantCoordinatorTest.kt`** — append 3 new cases (Group T — prompt-context alias injection): `buildContext_emptyAliasRepo_passesEmptyListToPromptBuilder`; `buildContext_threeAliases_passesAllToPromptBuilder` (set up `FakeAliasRepository` with 3 aliases; verify `engine.decide(transcript, context)` receives a `PromptContext` whose `knownAliases` contains all three in the right format); `buildContext_fifteenAliases_passesTopTenOnly` (set up 15; verify only top-10 by `useCount` reach the prompt). Use a new `FakeAliasRepository` (declared in `test/util/FakeAliasRepository.kt` for reuse by SF-7.3).
- [ ] **`app/src/test/java/com/curro/app/data/contacts/ContactsContractProviderTest.kt`** — append cases for `findByLookupKey`: `findByLookupKey_unknownKey_returnsNull`; `findByLookupKey_matchingRow_returnsContact` (verify `displayName`, `phoneNumbers`, `photoUri` round-trip); `findByLookupKey_emptyKey_returnsNull` (defensive — implementer can early-return on `lookupKey.isBlank()`).
- [ ] **`CallContactHandlerTest`** — verify the Phase-4 alias-first path now actually resolves: append `findByAlias_hit_callsContactDirectly_noFallback_toFindByName` using `FakeAliasRepository` pre-populated with `mi hija → Lucía Ruiz`. **Pin: this regression-tests `CallContactHandler.handle` lines 72–74** — `aliasMatches.isNotEmpty() → aliasMatches` short-circuit. The Phase-4 test currently asserts `EmptyAliasRepository` always falls through; that test is now obsolete (the `EmptyAliasRepositoryTest.kt` deletion removes it).
- [ ] **No new strings, no new permissions, no new manifest entries, no new dependencies.** Room is already added by SF-7.1.
- [ ] **Telemetry**: no new events. (The prompt-builder's golden tests prove the alias injection works; no metric needed at this layer.)
- [ ] Every SF-7.1 + Phase-6 + Phase-5 + Phase-4 test still passes.
- [ ] Manual smoke (Redmi 15): `adb shell` insert one alias via SQL (`INSERT INTO contact_aliases (alias, lookupKey, displayName, source, createdAtMs, lastUsedAtMs, useCount) VALUES ('mi hija', '<real lookup key>', 'Lucía Ruiz', 'LEARNED', 1700000000000, 1700000000000, 0);`) → press the mic, say "llama a mi hija" → call placed directly (no picker, no learning). Verify `useCount` bumps to 1 after the call (`adb shell run-as com.curro.app sqlite3 …`).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.

**Size**: M  ·  **Depends on**: US-045 (the DAO + entity), US-034 (the `CallContactHandler` alias-first lookup wiring from SF-4.10).

---

### US-047: Alias-learning subflow (spec flow 4) + relational-term detection  ·  _(master-plan SF-7.3, spec §7 + flow 4, voice-interaction "alias learning", platform-integrations "Resolution order step 4", local-data rules 1+3, brand-design copy_alias_*)_
**As** Fran's father, **I want** the first time I say "llama a mi hija" — when Curro doesn't yet know who "mi hija" is — for Curro to read me my contacts and let me pick one (by voice or by tap), then remember it for next time and place the call immediately — and **if** I say "ninguna", for Curro to send me politely to Fran without nagging — **so that** I teach Curro who's who in my life without ever feeling stuck or asked twice. **As a** Curro developer, **I want** `CallContactHandler` to detect relational terms ("mi hija", "el médico"…) that aren't in the alias map, return a new `HandlerResult.NeedsContactPick` with up to 5 candidates, learn ONLY through this code path (NEVER through SF-6.3's regular 3-Marías disambiguation, per `local-data` rule 3), AND re-learn when a stored alias points at a now-stale `LOOKUP_KEY` — **so that** Phase 7's most-subtle interaction works end-to-end with every rule from spec flow 4 enforced and tested.

**Acceptance Criteria**:
- [ ] **NEW** `app/src/main/java/com/curro/app/domain/alias/RelationalTerms.kt` — `object RelationalTerms { val all: Set<String> = setOf(...) }` containing the curated, exhaustively-listed relational/role terms (normalised lowercase, accents stripped — PM pins the full list verbatim):
  - **Family (20)**: `"mi hija"`, `"mi hijo"`, `"mi nieta"`, `"mi nieto"`, `"mi mujer"`, `"mi marido"`, `"mi esposa"`, `"mi esposo"`, `"mi madre"`, `"mi padre"`, `"mama"`, `"papa"`, `"mi hermana"`, `"mi hermano"`, `"mi suegra"`, `"mi suegro"`, `"mi yerno"`, `"mi nuera"`, `"mi tia"`, `"mi tio"`, `"mi prima"`, `"mi primo"`, `"mi sobrina"`, `"mi sobrino"`, `"mi cunada"`, `"mi cunado"`.
  - **Roles (10)**: `"el medico"`, `"la medico"`, `"la medica"`, `"la enfermera"`, `"el cura"`, `"el dentista"`, `"la farmaceutica"`, `"el farmaceutico"`, `"la del banco"`, `"el del banco"`, `"el abogado"`, `"la abogada"`.
  - **Pin: every entry is the normalised form** (`curroNormalize()` applied) — no accents, lowercase, single space between words. The check at the call site is `normalisedQuery in RelationalTerms.all`.
- [ ] **`app/src/main/java/com/curro/app/handler/CallContactHandler.kt`** — extend the `handle()` happy path. Current order (lines 72–84): (1) alias lookup; if empty, (2) `findByName(rawQuery)`; (3) zero/one/many. **NEW** order: (1) alias lookup; if non-empty → place call (unchanged). If empty: (1.5) **NEW** — check `if (rawQuery.curroNormalize() in RelationalTerms.all)` — if true, enter the **learning subflow** (see below). Else fall through to existing (2) `findByName(rawQuery)`.
- [ ] **The learning subflow** (inside `CallContactHandler`, in a new `private suspend fun enterLearningMode(rawQuery: String): HandlerResult` helper):
  1. `val all = contactsProvider.findAll()` — **NEW** method on `ContactsProvider` (`suspend fun findAll(): List<Contact>`; returns up to N contacts ordered by display-name). The implementation widens the `ContactsQueryRunner.query()` call to "no filter" (all rows); the wrapper groups by `LOOKUP_KEY`.
  2. If `all.isEmpty()` → return `HandlerResult.Failed(speech = getString(R.string.copy_alias_no_contacts), reason = CurroError.ContactNotFound(rawQuery))`. **NEW** copy: `copy_alias_no_contacts` = `"No tengo contactos para enseñarte. Pídele a Fran que te añada alguno."`.
  3. Take `candidates = all.take(LEARNING_CANDIDATE_LIMIT)` — **`LEARNING_CANDIDATE_LIMIT = 5`** (per spec flow 4 — "lee máximo 5"). The brand-design existing `copy_alias_ask` `"Aún no sé quién es %1$s. ¿Es alguno de estos contactos? Te los leo: %2$s."` accepts the names list as `%2$s` (concatenated names from candidates).
  4. If `all.size > LEARNING_CANDIDATE_LIMIT`, append `getString(R.string.copy_alias_ask_more)` (`"…o dime su nombre."` — already exists) to the prompt.
  5. Return `HandlerResult.NeedsContactPick(prompt = builtPrompt, candidates = candidates, onPick = { picked -> handleLearningPick(rawQuery, picked) })`.
- [ ] **The learning `onPick` callback** (private helper inside `CallContactHandler`):
  - `picked == null` (user said/tapped "ninguna") → `HandlerResult.Spoken(getString(R.string.copy_alias_defer_to_fran, rawQuery))`. **No alias saved.** No call placed.
  - `picked != null` → `aliasRepository.learn(rawQuery, picked, AliasSource.LEARNED)` THEN `placeCallOrFail(picked, rawQuery)`. **Pin: the speech is the existing `copy_alias_saved` `"Vale, %1$s es %2$s. Apuntado. Llamando ahora."`** (line 120 of strings.xml — already a saved-AND-calling combined line). The `placeCallOrFail` `Spoken` result's speech is OVERRIDDEN to this combined copy (the implementer wraps `placeCallOrFail` in `mapOf(success → Spoken(copy_alias_saved.format(rawQuery, picked.displayName)))`; on permission-fail, keep the underlying permission error copy — the user gets the better error message). **Decision: one TTS pass.**
- [ ] **Re-learn on stale `LOOKUP_KEY`** — extend `CallContactHandler` to detect the **"alias was stored but resolved to empty"** signature. The current Phase-7.2 path: `aliases.resolveAlias(rawQuery)` returns empty either because no alias row exists OR because the row exists but its `LOOKUP_KEY` no longer resolves. **NEW** the handler distinguishes them: add `suspend fun findStoredAlias(alias: String): AliasRecord?` to `AliasRepository` (returns a small `data class AliasRecord(val displayName: String, val source: AliasSource)` for the "I had it but the contact's gone" branch). **Pin: don't expose `lookupKey` outside the repo** — the record only carries the `displayName` (used in the re-learn prompt). Wiring in the handler: after the empty `resolveAlias` and BEFORE the `RelationalTerms` check, call `findStoredAlias(rawQuery.curroNormalize())`. If non-null, the alias row exists but is stale — enter the re-learn flow with the **NEW** `copy_alias_unresolved` prompt: `"Antes me dijiste que %1$s era %2$s, pero ya no la encuentro. ¿Quién es %1$s ahora?"`. Pass `oldName = record.displayName` and the regular candidates list. The user picks → alias is REPLACED (the `OnConflictStrategy.REPLACE` on the unique index does this); user picks "ninguna" → `copy_alias_defer_to_fran` (same as fresh learning).
- [ ] **NEW strings** in `app/src/main/res/values/strings.xml`:
  - `copy_alias_no_contacts` = `"No tengo contactos para enseñarte. Pídele a Fran que te añada alguno."`
  - `copy_alias_unresolved` = `"Antes me dijiste que %1$s era %2$s, pero ya no la encuentro. ¿Quién es %1$s ahora?"`
  - (The existing `copy_alias_ask`, `copy_alias_ask_more`, `copy_alias_saved`, `copy_alias_defer_to_fran` stay unchanged.)
- [ ] **Update `brand-design` COPY table** — add `copy_alias_no_contacts` and `copy_alias_unresolved` rows to the "Alias learning (Phase 7)" section of `.claude/skills/brand-design/SKILL.md` (provenance: `(NEW)` for the first; `(NEW — spec implied via §7 'si el alias no resuelve …')` for the second).
- [ ] **Rule 1 isolation — disambiguation ≠ learning** (`local-data` rule 3 + master-plan §Phase-7 Risks (a)). The SF-6.3 `CallContactHandler.buildPickResult` (the 3-Marías path, line 96–112) MUST NOT invoke `aliasRepository.learn(...)`. **Pin: two code paths, structurally distinct** — `enterLearningMode` (the SF-7.3 path) is the only function that calls `learn`. The SF-6.3 `buildPickResult` continues to return the same `NeedsContactPick(prompt = disambig prompt, candidates, onPick = { picked -> if (null) Spoken(copy_cancel_no_call) else placeCallOrFail(picked, rawQuery) })`. **No `aliasRepository.learn` in this branch — verified by test.**
- [ ] **`AliasRepository` extension** — add `suspend fun findStoredAlias(alias: String): AliasRecord?` to the interface (`data class AliasRecord(val displayName: String, val source: AliasSource)` co-located). `RoomAliasRepository.findStoredAlias(alias)`: `dao.findByAlias(alias.curroNormalize())?.let { AliasRecord(it.displayName, it.source) }`. No `bumpUsage` here — the row is stale by definition.
- [ ] **`ContactsProvider.findAll()`** — add `suspend fun findAll(): List<Contact>` to the interface; implement in `ContactsContractProvider` (widens `ContactsQueryRunner.query()` to fetch all rows when called without a name filter — same grouping by `LOOKUP_KEY`, same dedup). **Pin: ordering is alphabetical by `displayName.curroNormalize()`** so the candidate list is predictable. The wrapper takes the first `LEARNING_CANDIDATE_LIMIT` at the call site.
- [ ] **`CallContactHandlerTest.kt`** — append 11 new cases (Group L — alias learning):
  - `relationalTerm_aliasMiss_returnsNeedsContactPick_with_copy_alias_ask`;
  - `relationalTerm_userPicksCandidate_persistsAlias_speaksCopyAliasSaved_thenPlacesCall` (assert `aliasRepository.learn` invoked once with `source = LEARNED` AND the call is placed);
  - `relationalTerm_userPicksNinguna_speaks_copy_alias_defer_to_fran_noAliasSaved_noCallPlaced` (assert `aliasRepository.learn` is NOT invoked AND `callController.call` is NOT invoked);
  - `relationalTerm_zeroContacts_returns_copy_alias_no_contacts_failed`;
  - `relationalTerm_moreThan5Contacts_promptAppends_copy_alias_ask_more` (assert prompt ends with the "…o dime su nombre." extension);
  - `existingAlias_resolvesDirectly_noLearningOffered` (regression with `FakeAliasRepository` pre-populated);
  - `staleAlias_lookupKeyDoesNotResolve_entersReLearnFlow_with_copy_alias_unresolved` (FakeAliasRepository has the row; FakeContactsProvider.findByLookupKey returns null; assert the re-learn prompt format AND the candidates list is presented);
  - `staleAlias_reLearn_userPicksNewContact_REPLACEsAlias` (after the pick, `aliasRepository.learn` is invoked → the unique-index `OnConflictStrategy.REPLACE` overwrites the stale row);
  - `nonRelationalQuery_singleMatch_doesNotLearn` (regression — "llama a Pepito" path, no learning);
  - `nonRelationalQuery_threeMatches_returnsNeedsContactPick_butLearningNotInvoked` (the 3-Marías disambig path from SF-6.3 — assert `aliasRepository.learn` is NEVER invoked, even after `onPick` resolves a candidate);
  - `disambigPath_userPickValid_placesCallButDoesNotLearn` (the rule-3 invariant — picking from a disambig overlay is NOT an "I want to learn" signal).
- [ ] **`AssistantCoordinatorTest.kt`** — append 3 new cases (Group U — full pipeline learning): `relationalTerm_aliasLearningEnd2End` (mock STT "llama a mi hija" → mock FunctionGemma `{call_contact, contact: "mi hija", confidence: 0.88}` → assert the FSM enters `Confirming` with `PendingAction.Kind.PickContact` and the prompt is the `copy_alias_ask` format); `relationalTerm_userVoicePicksFirstCandidate_aliasPersisted_callPlaced` (assert the FSM ends in `Idle` AND `aliasRepository.learn` was called AND `callController.call` was called); `relationalTerm_userVoiceNinguna_speaksDeferToFran_noAliasPersisted` (assert the FSM ends in `Idle` AND `aliasRepository.learn` was NOT called).
- [ ] **`FakeAliasRepository`** in `test/util/FakeAliasRepository.kt` — extended to capture every `learn(...)` call into a `mutableListOf<LearnInvocation>(...)` so tests can assert "zero writes" easily. PM pins the shape: `data class LearnInvocation(val alias: String, val contactLookupKey: String, val source: AliasSource)`. Add accessors `val learnCalls: List<LearnInvocation>` + a `clearLearnCalls()` helper for tests that reuse the same fake across cases.
- [ ] **`brand-design` skill verification** — pin the new strings + voice in the COPY table; the implementer regenerates the table after this SF lands.
- [ ] **No new permissions** (uses the existing `READ_CONTACTS` permission gate that's already requested when `findByName` is called; `findAll` uses the same gate).
- [ ] **Telemetry**: no new events. The handler outcome (`needs_contact_pick` for learning, `success` after the call) already flows through `handler_invoked` in `HandlerDispatcher`. **Pin: never log the alias text or the contact name** — the existing `Log.w` calls in the coordinator are already counted-only (`utterance.len`).
- [ ] Every SF-7.1 + SF-7.2 + Phase-6 + Phase-5 + Phase-4 test still passes.
- [ ] Manual smoke (Redmi 15): wipe `contact_aliases` → press mic, say "llama a mi hija" → picker opens with the first 5 contacts + `copy_alias_ask` spoken → tap a candidate → `copy_alias_saved` spoken + call placed → end. Press mic again, "llama a mi hija" → call placed directly (no picker). Manually delete that contact from the phone (or rename its `LOOKUP_KEY` via a contact import/export round-trip) → press mic, "llama a mi hija" → `copy_alias_unresolved` spoken + re-learn prompt opens. Pick the same candidate → alias replaced + call placed.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.

**Size**: M  ·  **Depends on**: US-046 (the real `AliasRepository`), US-043 (the SF-6.3 `NeedsContactPick` + `ContactPickerOverlay` reused).

---

### US-048: Implicit favourite apps via `AppUsageDao` — recency-weighted home grid  ·  _(master-plan SF-7.4, spec §7 "Apps favoritas implícitas", local-data "Favourite apps → home grid" + rule 5, launcher-ui "feels the same every day")_
**As** Fran's father, **I want** the apps I open most by voice or by tapping the home grid to slowly become the four big tiles on my home screen — but **not** to keep moving around every time I use the phone — **so that** the apps I actually use are where my finger expects them, and the home screen doesn't rearrange itself behind my back. **As a** Curro developer, **I want** every successful `AppLauncher.launch(packageName)` (called from `OpenAppHandler` AND from `LauncherViewModel.onAppTileTapped`) to bump `AppUsageDao.upsert(packageName, now)` exactly once, AND `StaticFavoriteAppsRepositoryImpl` replaced with `RecencyFavoriteAppsRepositoryImpl` (top-N by `openCount × max(0, 1 − daysSince/30)`, padded with the Phase-1 seed apps when usage is sparse, recomputed at most every 24 h — `local-data` rule 5's stability bar) **so that** the home grid drifts toward Fran's father's real usage without surprise reshuffles.

**Acceptance Criteria**:
- [ ] **`app/src/main/java/com/curro/app/data/apps/AppLauncher.kt`** — centralise the usage bump. **`IntentAppLauncher.launch(packageName)`**: after the `context.startActivity(intent)` succeeds (the `return true` path on line 41), call `usageBumper.bumpAsync(packageName)`. **NEW** companion object `internal interface AppUsageBumper { fun bumpAsync(packageName: String): Unit }` co-located in the same file. Production impl `class CoroutineAppUsageBumper @Inject constructor(private val dao: AppUsageDao, private val timeProvider: TimeProvider, @ApplicationScope private val scope: CoroutineScope) : AppUsageBumper { override fun bumpAsync(pkg: String) { scope.launch { dao.upsert(pkg, timeProvider.now()) } } }`. **Pin: fire-and-forget on `@ApplicationScope`** — `AppLauncher.launch` is called from both `OpenAppHandler` (already suspending — so a suspend `bump(...)` would be cleaner) AND `LauncherViewModel.onEvent` (not suspending). Fire-and-forget keeps the launcher tap path unchanged. The `@ApplicationScope` ensures the write survives `LauncherViewModel.onCleared`. Tests use a fake `AppUsageBumper` that records calls synchronously.
- [ ] **`IntentAppLauncher` Hilt wiring** — add `private val usageBumper: AppUsageBumper` constructor parameter; bind in `AppsModule.kt` with `@Binds @Singleton fun bindAppUsageBumper(impl: CoroutineAppUsageBumper): AppUsageBumper`. **Pin: bump happens ONLY on the success path** — `getLaunchIntentForPackage` returning null OR `startActivity` throwing → no bump (the user never opened the app).
- [ ] **NO changes needed to `OpenAppHandler.kt` or `LauncherViewModel.kt`** beyond regression tests — they both call `AppLauncher.launch(packageName)` and the bump is centralised inside `IntentAppLauncher`. **Pin: this single source of truth is non-negotiable** (avoids double-bumps from "the handler bumps AND the VM bumps when the user uses voice + tile fallback").
- [ ] **NEW** `app/src/main/java/com/curro/app/data/apps/RecencyFavoriteAppsRepositoryImpl.kt` — `@Singleton class RecencyFavoriteAppsRepositoryImpl @Inject constructor(@ApplicationContext context: Context, private val appUsageDao: AppUsageDao, private val timeProvider: TimeProvider, @IoDispatcher ioDispatcher: CoroutineDispatcher) : FavoriteAppsRepository`. `observeFavorites()`: a `flow { while (true) { emit(loadFavorites()) ; delay(RECOMPUTE_INTERVAL_MS) } }.flowOn(ioDispatcher)`. **Pin: `RECOMPUTE_INTERVAL_MS = 24 × 60 × 60 × 1000L`** (24 h — the `local-data` rule 5 stability bar). The first emission happens immediately on subscription; subsequent emissions every 24 h. **Pin: this `delay`-based recompute is acceptable for the prototype** — the only collector is the home screen's `LauncherViewModel` whose lifecycle is the app's; a future "actualizar favoritas" config-menu button (Phase 8) will publish a `MutableSharedFlow` trigger that this flow also reacts to (declare the trigger surface now: an `internal val recomputeTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)` member that the flow's `while` body listens to via `select { onTimeout; onReceive(recomputeTrigger) }` — pin the structural design; Phase 8 wires the receiver).
- [ ] **`loadFavorites()` algorithm**:
  1. `val now = timeProvider.now()`; `val raw = appUsageDao.topByOpenCount(USAGE_FETCH_LIMIT)` (fetch top 20 by raw `openCount` — cheap SQL).
  2. **Score in Kotlin, not SQL** (PM pin: portable, testable; SQLite has no native `exp()` and the linear-decay shape is easy to reason about): for each `entity`, `daysSince = (now - entity.lastOpenedAtMs).toDouble() / DAY_MS`; `decay = max(0.0, 1.0 - daysSince / DECAY_DAYS)`; `score = entity.openCount * decay`.
  3. Sort by score descending; take top `FAVOURITES_COUNT` (= 4).
  4. **Seed padding**: the **Phase-1 static seeds** (`PACKAGE_WHATSAPP`, the dynamic Dialer / Camera / Gallery resolution from the deleted `StaticFavoriteAppsRepositoryImpl` — extract into a `SeedAppResolver` helper inside `data/apps/` so both repos share it) are appended to the result whenever the usage-derived list has fewer than `FAVOURITES_COUNT` rows AND a seed isn't already in the top-N. Final list trimmed to exactly `FAVOURITES_COUNT`.
  5. For each entry, resolve the `PackageManager.getApplicationIcon(pkg)` + `loadLabel` (mirror the `StaticFavoriteAppsRepositoryImpl.buildFavoriteApp` helper). **Pin: re-use the `SeedAppResolver` extracted from the static repo** so the icon/label resolution stays identical.
- [ ] **Constants** in `RecencyFavoriteAppsRepositoryImpl`: `FAVOURITES_COUNT = 4`; `RECOMPUTE_INTERVAL_MS = 24L * 60 * 60 * 1000`; `USAGE_FETCH_LIMIT = 20`; `DAY_MS = 24L * 60 * 60 * 1000`; `DECAY_DAYS = 30.0`. All on the `private companion object`.
- [ ] **`app/src/main/java/com/curro/app/data/apps/SeedAppResolver.kt`** — extract the Phase-1 static four-tile logic into a reusable `@Singleton class SeedAppResolver @Inject constructor(@ApplicationContext context: Context)`. Methods: `fun seedFavorites(): List<FavoriteApp>` (returns the four Phase-1 tiles); the dynamic-resolution code currently in `StaticFavoriteAppsRepositoryImpl` (`resolveDirectPackage`, `resolveViaIntent`, `buildFavoriteApp`, the four `PACKAGE_*` constants) moves here verbatim. **`RecencyFavoriteAppsRepositoryImpl`** injects `SeedAppResolver` and uses it for the seed-padding step (#4) AND for resolving icons/labels of usage-derived packages (a new method `fun toFavoriteApp(packageName: String): FavoriteApp?` that returns null if the package no longer installed).
- [ ] **DELETE** `app/src/main/java/com/curro/app/data/apps/StaticFavoriteAppsRepositoryImpl.kt` and its test `app/src/test/java/com/curro/app/data/apps/StaticFavoriteAppsRepositoryImplTest.kt`. (Pin: the static impl is now subsumed by `SeedAppResolver` + the recency repo's padding logic. If the implementer prefers to keep `StaticFavoriteAppsRepositoryImpl` as a debug-only fallback, they MUST move it to `app/src/debug/` and wire it only behind a debug-variant Hilt binding — recommend: just delete.)
- [ ] **`app/src/main/java/com/curro/app/di/AppsModule.kt`** — replace the `@Binds FavoriteAppsRepository → StaticFavoriteAppsRepositoryImpl` line with `@Binds @Singleton fun bindFavoriteAppsRepository(impl: RecencyFavoriteAppsRepositoryImpl): FavoriteAppsRepository`. Add `@Binds @Singleton fun bindAppUsageBumper(impl: CoroutineAppUsageBumper): AppUsageBumper`. (`SeedAppResolver` is `@Inject`-constructable; no binding needed.)
- [ ] **NEW** `app/src/test/java/com/curro/app/data/apps/RecencyFavoriteAppsRepositoryImplTest.kt` (JVM Robolectric — Room needs Android + `PackageManager` shadows; ~8 cases): `emptyUsage_fallsBackToSeeds`; `oneHeavyUser_ranksFirst` (insert 20 opens for `com.whatsapp` over 7 days, verify it's tile 1); `decayKicksIn_old_app_drops_below_recent_low_count` (insert 50 opens for app A at day 30, 5 opens for app B at day 0 — verify B wins because A's decay is 0); `decayClampedToZero_after30Days_appHiddenFromUsage_butSeedFallbacks_in` (insert 100 opens for `com.example.test` at day 35, verify it's NOT in the top 4 because score = 0; verify seeds fill instead); `tieBreaker_higherCountWins_atSameLastOpened` (two apps same `lastOpenedAtMs` — higher `openCount` wins); `seedPadding_preservesTopN_when_usage_has_three_apps` (3 usage rows + seeds = 4 tiles, no duplicates if a usage app overlaps with a seed); `recompute_stability_acrossMultipleBumps_within24h_doesNotReshuffle` (Turbine + `TestTimeProvider.advanceBy(1.hour) × 23` — the flow emits ONCE on subscription + NOT again within the 24h window — even though `appUsageDao.upsert` runs 100×; pin: the Turbine assertion is `expectMostRecentItem()` + `expectNoEvents()` for the next 23 h); `recompute_after24h_emits_newOrder` (advance time by 24h+1ms, the flow emits the new ordering).
- [ ] **`AppLauncherTest.kt`** — append 4 cases verifying the usage bump: `launch_success_bumpsUsage_once`; `launch_packageNotFound_doesNotBump`; `launch_securityException_doesNotBump`; `launch_activityNotFoundException_doesNotBump`. (Use a `FakeAppUsageBumper` that records calls; the existing tests stay unchanged.)
- [ ] **`OpenAppHandlerTest.kt`** — append 1 case `openApp_success_triggersUsageBump_viaAppLauncher` (regression: handler doesn't call the DAO directly — the bump goes through the centralised `IntentAppLauncher.launch`).
- [ ] **`LauncherViewModelTest.kt`** — append 1 case `appTileTapped_success_triggersUsageBump_viaAppLauncher` (same regression on the tile-tap path).
- [ ] **No new strings, no new permissions, no new manifest entries, no new dependencies.**
- [ ] **Telemetry**: no new events. (A future Phase-8 "favourites changed" diagnostic could land in §13 validation telemetry; out of scope here.)
- [ ] Every SF-7.1 + SF-7.2 + Phase-6 + Phase-5 + Phase-4 test still passes.
- [ ] Manual smoke (Redmi 15): wipe `app_usage` (or fresh install) → home grid shows the 4 seed tiles (WhatsApp / Llamadas / Cámara / Fotos). Open WhatsApp via voice 20× over a few minutes (use a debug receiver if convenient, or just say "abre WhatsApp"). Wait for the next 24h tick OR force-fire the recompute via a debug affordance (Phase 8 wires the real button). Verify WhatsApp is still tile 1 (it was a seed, and now has 20 usage rows). Open a non-seed app (e.g. Settings) 30× → after the recompute, Settings appears as one of the tiles, displacing one of the seeds (whichever has the lowest usage).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.

**Size**: M  ·  **Depends on**: US-045 (the `AppUsageDao`), SF-1.4 (the static favourites repo + `FavoriteApp` model + `IoDispatcher` qualifier).

---

### US-049: Real `FailedCommandLog` Room-backed + telemetry guardrail tightened  ·  _(master-plan SF-7.5, spec §6 flow 7 + §9 "Logs de comandos fallidos" + §12 privacy, local-data rule 4, function-catalog rule 4)_
**As** Fran (reviewing what went wrong on his father's phone every week), **I want** the last 50 commands Curro didn't understand (or failed on) stored locally with a clear kind tag (`INVALID_OUTPUT` / `UNKNOWN_FUNCTION` / `HANDLER_ERROR`) — and **I want** to know with certainty that those transcripts never leak to PostHog or Firebase — **so that** I can read the log in Phase 8 and decide which functions to add next. **As a** Curro developer, **I want** the SF-3.6 `Log.w("Curro/FailedCommand", …)` stub call sites replaced with `failedCommandLog.record(transcript, kind, details)` against a real Room-backed `FailedCommandLog`, AND `TelemetryGuardrail` extended with a new `command_failed` event whose property whitelist explicitly EXCLUDES `transcript` and `details` (and any other PII shape), so a code-review diff fails fast if a developer ever tries to add a transcript prop to a telemetry event.

**Acceptance Criteria**:
- [ ] **NEW** `app/src/main/java/com/curro/app/domain/repository/FailedCommandLog.kt` — `interface FailedCommandLog { suspend fun record(transcript: String, kind: FailureKind, details: String = "") ; fun observeRecent(limit: Int = 50): Flow<List<FailedCommandEntity>> ; suspend fun count(): Int ; suspend fun deleteAll() }`. (`FailureKind` and `FailedCommandEntity` are SF-7.1 types — already in `data/local/`; the domain interface re-exports them via plain imports — pin: don't shadow the entity with a domain DTO yet; Phase 8's UI may want the raw entity. If Phase 8 wants a UI-shaped projection, that's an SF-8.x decision.)
- [ ] **NEW** `app/src/main/java/com/curro/app/data/local/RoomFailedCommandLog.kt` — `@Singleton class RoomFailedCommandLog @Inject constructor(private val dao: FailedCommandDao, private val timeProvider: TimeProvider, @IoDispatcher private val ioDispatcher: CoroutineDispatcher) : FailedCommandLog`. Implementations: `record(transcript, kind, details) = withContext(ioDispatcher) { dao.insertAndTrim(FailedCommandEntity(transcript = transcript, kind = kind, details = details, timestampMs = timeProvider.now())) }`; `observeRecent(limit) = dao.observeRecent(limit)`; `count() = withContext(ioDispatcher) { dao.count() }`; `deleteAll() = withContext(ioDispatcher) { dao.deleteAll() }`.
- [ ] **NEW** `app/src/main/java/com/curro/app/di/FailedCommandLogModule.kt` — `@Module @InstallIn(SingletonComponent::class) abstract class FailedCommandLogModule { @Binds @Singleton abstract fun bindFailedCommandLog(impl: RoomFailedCommandLog): FailedCommandLog }`. (Could also nest into the existing `DatabaseModule` from SF-7.1 — implementer chooses; pin keeping it separate so SF-8's failed-commands UI imports stay scoped.)
- [ ] **Call site migration #1 — invalid JSON** in `AssistantCoordinator.onDecisionFailure` (line 889–911): inject `private val failedCommandLog: FailedCommandLog`. Replace the existing `Log.w(FAILED_TAG, "action=${actionLabel ?: "null"} error=${err::class.simpleName} utterance.len=${transcript.length}")` block with `failedCommandLog.record(transcript = transcript, kind = mapErrorToKind(err), details = err::class.simpleName ?: "unknown")`. **Mapping**: `CurroError.InvalidFunctionCall → FailureKind.INVALID_OUTPUT`; `CurroError.UnknownFunction → FailureKind.UNKNOWN_FUNCTION`; any other error class → `FailureKind.HANDLER_ERROR`. **Pin: keep the `Log.w` line for `adb logcat`-style debugging** (it's count-only; no PII), but ALSO write to the Room log. Both writes are part of the same `runCatching { ... }` wrapper so a Room failure can't block the user-facing TTS path.
- [ ] **Call site migration #2 — handler crash / failed result** in `AssistantCoordinator.renderHandlerFailure` (line 701–713): after `Log.w(...)`, call `failedCommandLog.record(transcript = pendingTranscript, kind = if (result.reason is CurroError.HandlerCrash) FailureKind.HANDLER_ERROR else FailureKind.HANDLER_ERROR, details = "${call.action}/${result.reason::class.simpleName}")`. **Both `Failed` reasons land as `HANDLER_ERROR`** — `INVALID_OUTPUT` and `UNKNOWN_FUNCTION` are the model-decision-time failure kinds, not handler-time. The `details` field carries the action + the error class so Fran's Phase-8 UI can group by error class.
- [ ] **Call site migration #3 — `HandlerDispatcher.dispatch` catch path** (current `runCatching { handler.handle(call) }.getOrElse { e -> ... }` line 45–50): this branch is already wired to return `HandlerResult.Failed(reason = CurroError.HandlerCrash(call.action, throwable = e))`. The coordinator's `renderHandlerResult → renderHandlerFailure` path therefore picks up the crash and records it (#2 above). **Pin: the dispatcher itself does NOT touch `FailedCommandLog`** — single-source-of-truth at the coordinator. Tests verify the round-trip (dispatcher throws → coordinator records).
- [ ] **`TelemetryGuardrail.ALLOWED_PROPS`** — append a new event: `"command_failed" to setOf("kind", "function_name")` (≤ 8 chars per value — `kind` ∈ `{invalid_output, unknown_function, handler_error}` is OK; `function_name` ∈ catalog snake_case is OK; **`transcript` and `details` are EXCLUDED**). The coordinator emits this event in BOTH migration call sites (`onDecisionFailure` and `renderHandlerFailure`) with `mapOf("kind" to kind.name.lowercase(), "function_name" to (call?.action ?: "unknown"))`. **Pin: `transcript` is NEVER on the wire**. The PostHog dashboard sees "count of `command_failed` events grouped by kind"; the transcripts stay local.
- [ ] **`TelemetryGuardrail` extra guards** — add 5 new fixture cases to `TelemetryGuardrailTest`: `command_failed_with_kind_invalid_output_allowed`; `command_failed_with_function_name_call_contact_allowed`; `command_failed_with_transcript_prop_rejected` (the implementer attempts to add `"transcript" to "llama a mi hija"`; the guardrail rejects with reason "prop key 'transcript' is not on the whitelist"); `command_failed_with_details_prop_rejected` (same — `"details"` not whitelisted); `command_failed_with_long_kind_value_rejected` (a 50-char `kind` value rejected by the 32-char heuristic — pin: this is the catch-net for "kind got injected with a real Spanish phrase" bugs).
- [ ] **`app/src/test/java/com/curro/app/data/local/RoomFailedCommandLogTest.kt`** (JVM Robolectric, in-memory Room + `TestTimeProvider`; ~6 cases): `record_persistsRowWith_timestampFromTimeProvider`; `record_callsInsertAndTrim_capsAt50`; `record_60times_keepsNewest50`; `count_reflectsInsertCount`; `observeRecent_emitsDescendingTimestamp` (Turbine); `deleteAll_emptiesTable`.
- [ ] **`AssistantCoordinatorTest.kt`** — append 4 new cases (Group V — failed-command logging): `invalidJson_decisionFailure_recordsAsINVALID_OUTPUT_in_failedCommandLog_with_transcript` (FakeFailedCommandLog records the call; verify `kind = INVALID_OUTPUT`, `transcript = "the user's utterance"`); `unknownFunction_decisionFailure_recordsAsUNKNOWN_FUNCTION`; `handlerCrash_recordsAsHANDLER_ERROR_withFunctionNameInDetails`; `handlerReturnsFailed_recordsAsHANDLER_ERROR_butNotAsCrash` (verify the discrimination: a `Failed` result is logged as `HANDLER_ERROR` but the telemetry event's `outcome` stays `failed`, not `crash` — the kind is decoupled from the outcome).
- [ ] **`HandlerDispatcherTest.kt`** — append 1 regression case: `dispatch_handlerThrows_returnsHandlerCrash_butDoesNotTouchFailedCommandLog` (verify the dispatcher itself does NOT inject `FailedCommandLog`; the coordinator does).
- [ ] **`FakeFailedCommandLog`** in `test/util/FakeFailedCommandLog.kt` — `class FakeFailedCommandLog : FailedCommandLog` with `val records: MutableList<RecordedCall>` capturing every `record` invocation (`data class RecordedCall(val transcript: String, val kind: FailureKind, val details: String)`). Tests assert against `records`.
- [ ] **Privacy verification — automated** — add `TelemetryGuardrailTest.command_failed_TranscriptOrDetailsPropAlwaysRejected` parameterised over the (key = "transcript", value = "any") and (key = "details", value = "any") inputs. **Pin: this test fails if a future PR adds either key to `ALLOWED_PROPS["command_failed"]`** — code review will catch the parallel change, but the test makes the contract enforceable in CI.
- [ ] **No new permissions, no manifest changes, no new strings, no new dependencies.**
- [ ] Every SF-7.1 + SF-7.2 + SF-7.3 + SF-7.4 + Phase-6 + Phase-5 + Phase-4 + SF-3.6 test still passes.
- [ ] Manual smoke (Redmi 15): say something Curro can't map (e.g. "tradúceme esto al italiano") → confirm the `copy_unknown_function` line is spoken AND `adb shell run-as com.curro.app sqlite3 databases/curro.db "SELECT kind, function_name, datetime(timestampMs/1000, 'unixepoch') FROM failed_commands ORDER BY timestampMs DESC LIMIT 5;"` shows the new row with the right kind. Repeat 60×; verify the table count never exceeds 50. Verify PostHog (release build) shows the `command_failed` event count rising but NEVER carries the transcript as a property (sanity-check via the network inspector).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.

**Size**: S  ·  **Depends on**: US-045 (the `FailedCommandDao` + entity), SF-3.6 (the `Log.w` stub call sites in the coordinator + the existing `model_decide` telemetry guardrail).

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
