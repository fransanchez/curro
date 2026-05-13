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
