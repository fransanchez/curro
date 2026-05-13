# US-002 — Hilt DI graph & `HiltTestRunner`

> Implementation brief for **SF-0.2** (`docs/master-plan.md` → Phase 0). This brief
> is the *what to build*; `/implement-feature US-002` is the *how / when*. The
> brief follows `.claude/skills/spec-template/SKILL.md`.
>
> **Architect recommendation.** US-002 is **not** mechanical: it locks in the
> shape Curro's DI graph will keep for the next 20+ SFs (where dispatchers live,
> whether they're qualified or wrapped in a `DispatcherProvider`, where the
> `@ApplicationScope CoroutineScope` is hosted, whether an empty `AppModule`
> exists at all, and the JVM-Hilt-test trade-off — see *Open Questions*). The PM
> recommendation is **run `android-architect` for a short pass** between this
> brief and `android-developer` picking it up, because those four micro-choices
> propagate to every later SF that touches a coroutine. See *Implementation Notes*
> for the explicit hand-off.

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Hilt DI graph & `HiltTestRunner` |
| **US ID** | US-002 |
| **SF ID** | SF-0.2 (master-plan) |
| **Phase** | 0 — Project foundation |
| **Status** | In Progress |
| **Created** | 2026-05-13 |
| **Modified** | 2026-05-13 |
| **PM Owner** | Fran (Claude `android-product-analyst`) |
| **Architect** | Recommended — `android-architect` for a short Clean-Arch / DI-shape review before `android-developer` picks up |

## Summary

Make the Hilt graph that US-001 *plumbed* (plugin applied, `@HiltAndroidApp class
CurroApp`, `@AndroidEntryPoint class MainActivity`, the `kspAndroidTest(libs.hilt.compiler)`
+ `androidTestImplementation(libs.hilt.android.testing)` lines already in
`app/build.gradle.kts`, `testInstrumentationRunner = "com.curro.app.HiltTestRunner"`
already declared in `defaultConfig`) actually **work end-to-end**: add the
`HiltTestRunner` class itself, add the dispatcher qualifiers that every later SF
will inject, add the `CoroutineModule` that provides them + the
`@ApplicationScope CoroutineScope`, add a placeholder `AppModule` so future
app-scope bindings have an obvious home, and Hilt-ify the instrumented smoke
test so it boots `HiltTestApplication` and asserts the app renders "Curro".

This story has **no user-visible value** for Fran's father — the value is
operational. After it lands, every later SF (`@HiltViewModel` ViewModels,
repositories bound to `domain/repository/` interfaces, the function-name-keyed
handler multibinding map in SF-4.x, the WhatsApp parser tests with a faked
`NotificationListenerService` via `@UninstallModules` + `@BindValue`) can be
written *additively* — no SF after this one ever again touches the DI plumbing.

Spec ref: `docs/curro-spec-v1.0.md` §14 (stack & build order). The spec doesn't
prescribe DI specifics; this SF is mechanically wiring the DI framework the
project chose, against the on-device, no-network, no-Firebase-token shape of
the world (spec §1, §12).

## Scope

### In Scope

- **`app/src/androidTest/java/com/curro/app/HiltTestRunner.kt`** — a single Kotlin file extending `AndroidJUnitRunner`, overriding `newApplication(...)` to swap `CurroApp` for `HiltTestApplication`:
  ```kotlin
  class HiltTestRunner : AndroidJUnitRunner() {
      override fun newApplication(cl: ClassLoader?, name: String?, ctx: Context?): Application =
          super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
  }
  ```
  US-001 already declared the FQN `com.curro.app.HiltTestRunner` in
  `app/build.gradle.kts`'s `defaultConfig.testInstrumentationRunner`; US-002
  makes the class real.

- **`app/src/main/java/com/curro/app/di/Qualifiers.kt`** — one file holding the four short `@Qualifier` annotations, with a one-line KDoc each:
  - `@IoDispatcher` — for disk / network / Room / ContentResolver / model inference work
  - `@MainDispatcher` — for UI-state updates (rare — most things are `Dispatchers.Main.immediate` via Compose, but having it bound makes the test override trivial)
  - `@DefaultDispatcher` — for CPU-bound work (parsing, computation)
  - `@ApplicationScope` — qualifier on the application-lifetime `CoroutineScope` (see `CoroutineModule`)

  Rationale for one file (vs. four): they are short, semantically related, and
  read together. Splitting them would create four 4-line files. **Decision is
  the architect's** — the brief asserts the qualifiers exist; the file layout is
  not load-bearing.

- **`app/src/main/java/com/curro/app/di/CoroutineModule.kt`** — `@InstallIn(SingletonComponent::class) object` providing:
  - `@Singleton @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO`
  - `@Singleton @MainDispatcher fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate`
  - `@Singleton @DefaultDispatcher fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default`
  - `@Singleton @ApplicationScope fun provideApplicationScope(@IoDispatcher io: CoroutineDispatcher): CoroutineScope = CoroutineScope(SupervisorJob() + io)`

  Why `Dispatchers.Main.immediate` instead of `Dispatchers.Main`: it avoids a
  one-frame re-post when already on the main thread, which makes the
  press-to-listening latency budget in Phase 2 (< 1 s) easier to hit. Architect
  is free to override.

  Why a `@ApplicationScope` `CoroutineScope` is in scope for this SF: the
  `ModelWarmupService` (Phase 3+) needs one; the alias-learning subflow (Phase
  7) launches background writes from a handler; the launcher's "refresh
  installed apps" job (Phase 7) wants one too. Providing it once now means none
  of those SFs has to add it.

- **`app/src/main/java/com/curro/app/di/AppModule.kt`** — `@InstallIn(SingletonComponent::class) object AppModule { /* future app-scope providers */ }`. **Empty body with a KDoc** ("Home for app-scope providers. Real bindings land per-SF — Room in SF-7.1, MediaPipe in SF-3.1, etc."). This is borderline — see *Open Questions Q3* — but on balance the explicit file is worth the eight lines because it gives every later SF an unambiguous "is this where it goes?" answer.

- **`app/src/androidTest/java/com/curro/app/MainActivityHiltSmokeTest.kt`** — Hilt-ified replacement for US-001's `InstrumentedSmokeTest`:
  ```kotlin
  @HiltAndroidTest
  @RunWith(AndroidJUnit4::class)
  class MainActivityHiltSmokeTest {
      @get:Rule(order = 0)
      val hiltRule = HiltAndroidRule(this)

      @get:Rule(order = 1)
      val composeRule = createAndroidComposeRule<MainActivity>()

      @Before
      fun setUp() { hiltRule.inject() }

      @Test
      fun appBootsAndRendersCurro() {
          composeRule.onNodeWithText("Curro").assertIsDisplayed()
      }
  }
  ```
  **Rename, not duplicate.** US-001's `InstrumentedSmokeTest` is *deleted* —
  the Hilt-aware test proves a strict superset (it requires the graph to compile
  *and* the Activity to launch *and* the text to render). Keeping the old one
  would be dead weight.

- **(Optional, architect's call) `app/src/test/java/com/curro/app/di/CoroutineQualifierTest.kt`** — a small JVM unit test exercising the qualifier annotations / dispatcher identities **without** Hilt (Hilt unit-testing on JVM is awkward — the framework is Android-runtime-bound). Pragmatic alternative: rely on the instrumented Hilt-smoke test for the actual graph proof, and skip the JVM-Hilt-test rabbit hole. See *Open Questions Q4* — the brief leaves this as a developer/architect call, recommending the simpler path.

### Out of Scope (each is its own later SF)

- **Room / DataStore module** (the `CurroDatabase`, the DAOs, `DatabaseModule.provideCurroDatabase(...)`, the `SettingsRepository` over DataStore) → **SF-7.1**.
- **MediaPipe / LiteRT module** (the `FunctionGemmaEngine` / `Gemma3nEngine` providers, the `ModelWarmupService` injection) → **SF-3.1**.
- **`NotificationListenerService` module** (the unread-cache repository, the WhatsApp parser registration) → **SF-4.5 / SF-4.6**.
- **`TtsClient` / `SttClient` module** (the Android `TextToSpeech` / `SpeechRecognizer` wrappers, the `domain/repository/` interfaces, the voice-pack diagnostic) → **SF-2.1 / SF-2.2**.
- **`TelecomManager` / `InCallService` module** → **SF-4.7** (`call_contact`) and the opt-in incoming-call assistant later.
- **Repository module** (binding `AliasRepositoryImpl` to `AliasRepository`, etc.) → lands per-feature with whichever SF first needs each repo.
- **`HandlerModule` and the function-name-keyed multibinding map** (`@Binds @IntoMap @StringKey("tell_time")`) → **SF-4.1** (the first handler — `tell_time`).
- **Telemetry module** (`TelemetrySink` interface, Crashlytics + Analytics + PostHog providers, the no-PII guardrail) → **SF-0.8**.
- **A real `LauncherViewModel`** with `@HiltViewModel` → **SF-1.2** (the first ViewModel).
- **Hilt navigation extensions** (`hiltViewModel()` in the nav host) → **SF-0.6** (`CurroNavHost`).
- **Custom Hilt components / subcomponents** — explicitly **not** added; only the four standard scopes (`SingletonComponent`, `ActivityRetainedComponent`, `ActivityComponent`, `ViewModelComponent`) are used. Inventing custom scopes for a prototype is overreach; if a later SF genuinely needs one (the spec doesn't suggest any will), that SF owns the decision.

## User Flows

US-002 has **no end-user flow**. The "users" are a Curro developer running
Gradle / a Compose test, and the CI runner. The two flows are:

### Flow 1: Developer runs the instrumented smoke test locally

1. Developer: `./gradlew :app:installDebugAndroidTest connectedAndroidTest` against a running Pixel-class Android 15 emulator
2. AGP picks up `testInstrumentationRunner = "com.curro.app.HiltTestRunner"` from `app/build.gradle.kts`'s `defaultConfig`
3. The runner boots `HiltTestApplication` (not `CurroApp`) so the Hilt rule can swap modules in later SFs' tests
4. `MainActivityHiltSmokeTest` runs: `HiltAndroidRule` injects (no-op today — nothing requests injection — but it *would* fail if the graph didn't compile); `createAndroidComposeRule<MainActivity>` launches `MainActivity`; the test asserts the text "Curro" is displayed
5. The task reports 1 passed, 0 failed; the developer moves on

### Flow 2: Developer runs the JVM tests

1. Developer: `./gradlew testDebugUnitTest`
2. US-001's `SmokeTest` passes (no regression)
3. If the architect green-lights the optional JVM qualifier test (Q4), it also passes
4. The task reports ≥ 1 passing, 0 failing

### Flow 3: A later SF adds a test that needs to fake a real dependency

(Demonstrates *why* US-002 is the precondition for every later SF — not a flow
that runs in US-002.)

1. Later SF — say SF-4.6 — writes an instrumented test for `ReadLastWhatsAppHandler` that needs to fake the `NotificationListenerService` source
2. The test declares `@UninstallModules(NotificationModule::class)` + a nested `@Module` with `@BindValue val fakeNotifications: NotificationRepository = ...`
3. `HiltTestRunner` boots `HiltTestApplication`, the Hilt rule swaps the real `NotificationModule` for the fake, the test runs — **all of this only works because US-002 set up `HiltTestRunner` and `HiltTestApplication` lands via the testing dependency US-001 already wired**

## Function-catalog Impact

**No catalog change.** SF-0.2 ships no handler, no `CatalogFunction`, no
FunctionGemma prompt change, no JSON-schema entry. The `domain/catalog/`
directory stays empty (kept alive by its `.gitkeep` from US-001).

Cross-reference: the `function-catalog` skill is untouched until SF-3.x lands
the prompt + JSON-schema validator; SF-4.1 lands the first handler binding in
the multibinding map.

## FSM States Touched

**None.** SF-0.2 ships no assistant code — no `AssistantStateMachine`, no
`AssistantCoordinator`, no `ConfidencePolicy`, no overlays. The `assistant/`
package stays empty.

Cross-reference: the `voice-interaction` skill is untouched; SF-5.x produces
the first FSM code.

## Android System Integrations & Permissions

**No system integrations**, **no runtime permissions** declared, **no manifest
changes**.

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| *(none in this SF)* | Each permission is declared by the SF that needs it (spec §10) | N/A | N/A |

The lazy-permission discipline from US-001 holds: do **not** prophylactically
declare permissions in the manifest "for the DI graph". SF-2.1 declares
`RECORD_AUDIO`; SF-4.6 declares notification-listener binding; SF-0.8 declares
`INTERNET` in the release manifest only.

Cross-reference: `platform-integrations` skill (no integrations yet — the
modules that will inject them are all *out of scope* per the explicit list
above), `launcher-app` skill (`CATEGORY_HOME` still deferred to SF-1.1).

## On-device-model Impact

**No model impact.** SF-0.2 declares no MediaPipe / LiteRT dependency; no
`FunctionGemmaEngine` / `Gemma3nEngine` provider; no model weights. The
`data/ml/` package stays empty (`.gitkeep`). The reserved catalog entries
(`mediapipe-tasks-genai`, `litert`) US-001 declared in
`gradle/libs.versions.toml` remain catalog-only — they do not appear in
`./gradlew :app:dependencies`.

Cross-reference: `on-device-llm` skill — SF-3.1 will be the first SF that
touches it.

## Android Specification

### Source files this SF lands

```
app/src/
├── main/java/com/curro/app/
│   └── di/
│       ├── Qualifiers.kt              # new — @IoDispatcher / @MainDispatcher / @DefaultDispatcher / @ApplicationScope
│       ├── CoroutineModule.kt         # new — provides the three dispatchers + @ApplicationScope CoroutineScope
│       └── AppModule.kt               # new — placeholder, KDoc only; future app-scope bindings live here
├── test/java/com/curro/app/
│   └── di/
│       └── CoroutineQualifierTest.kt  # OPTIONAL — see Open Questions Q4; architect's call
└── androidTest/java/com/curro/app/
    ├── HiltTestRunner.kt              # new — AndroidJUnitRunner that boots HiltTestApplication
    ├── MainActivityHiltSmokeTest.kt   # new — replaces InstrumentedSmokeTest; @HiltAndroidTest + Compose UI test
    └── InstrumentedSmokeTest.kt       # DELETED — superseded by the Hilt-aware version above
```

`app/src/main/java/com/curro/app/di/.gitkeep` from US-001 can be deleted once
the real files land (or left in place — your call; once the directory has
real Kotlin files it's no longer "empty for git's sake").

### Screens and Composables

**None.** SF-0.2 is pure DI plumbing — no `*Screen`, no `Content` composable,
no `ViewModel`. The single visible composable in the codebase remains the
`Text("Curro")` from US-001's `MainActivity`. The first real screen
(`LauncherScreen`) lands in SF-1.2.

### ViewModels and State Management

**None in this SF.** No `@HiltViewModel` lands until SF-1.2
(`LauncherViewModel`). US-002 does ensure the *plumbing* is right so SF-1.2
just writes the VM and a binding in `presentation/launcher/` — no
`@HiltViewModel` infrastructure work needed in SF-1.2.

### Navigation Routes

**None in this SF.** `CurroNavHost` is SF-0.6. `MainActivity` here remains the
single-composable host from US-001.

### Hilt Modules

This is the heart of the SF. Spelled out per file:

```kotlin
// app/src/main/java/com/curro/app/di/Qualifiers.kt
package com.curro.app.di

import javax.inject.Qualifier

/** Marks a [kotlinx.coroutines.CoroutineDispatcher] backed by [kotlinx.coroutines.Dispatchers.IO]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

/** Marks a [kotlinx.coroutines.CoroutineDispatcher] backed by [kotlinx.coroutines.Dispatchers.Main]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher

/** Marks a [kotlinx.coroutines.CoroutineDispatcher] backed by [kotlinx.coroutines.Dispatchers.Default]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

/** Marks the application-lifetime [kotlinx.coroutines.CoroutineScope] (SupervisorJob + IoDispatcher). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
```

```kotlin
// app/src/main/java/com/curro/app/di/CoroutineModule.kt
package com.curro.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Coroutine plumbing. Injected by everything async: model engines, STT/TTS clients,
 * Room DAOs, the FSM coordinator, ViewModels. Using qualifiers (vs. a wrapper
 * interface) lets tests swap with `TestDispatcher` via `@UninstallModules` /
 * `@BindValue`. See Open Questions Q2 if the architect prefers a wrapper.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides @Singleton @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides @Singleton @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(
        @IoDispatcher io: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + io)
}
```

```kotlin
// app/src/main/java/com/curro/app/di/AppModule.kt
package com.curro.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Home for app-scope bindings that don't belong to a more specific module.
 *
 * Intentionally empty in SF-0.2: real bindings land per-SF (Room in SF-7.1,
 * MediaPipe in SF-3.1, NotificationListener in SF-4.5/4.6, repositories
 * per-feature, the handler multibinding map in SF-4.1).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
```

```kotlin
// app/src/androidTest/java/com/curro/app/HiltTestRunner.kt
package com.curro.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Boots [HiltTestApplication] instead of [CurroApp] for instrumented tests, so
 * the Hilt rule can swap modules via `@UninstallModules` + `@BindValue`.
 *
 * Wired by `app/build.gradle.kts` (`defaultConfig.testInstrumentationRunner`
 * declared in SF-0.1; class realised here in SF-0.2).
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        ctx: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
}
```

```kotlin
// app/src/androidTest/java/com/curro/app/MainActivityHiltSmokeTest.kt
package com.curro.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hilt-injected instrumented smoke test. Proves three things at once:
 *   1. The Hilt graph compiles end-to-end (HiltAndroidRule would otherwise fail).
 *   2. HiltTestApplication boots and MainActivity launches with @AndroidEntryPoint.
 *   3. The empty Compose tree from US-001 still renders "Curro".
 *
 * Supersedes the US-001 `InstrumentedSmokeTest` (delete that file). Stays on
 * JUnit 4 + AndroidJUnit4 — JUnit 5 is not supported on instrumented Android
 * by AGP (see US-001 brief's Architect note A5).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityHiltSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun appBootsAndRendersCurro() {
        composeRule.onNodeWithText("Curro").assertIsDisplayed()
    }
}
```

Notes on the worked example (for the architect / implementer):

- `@get:Rule(order = 0)` on the Hilt rule, `order = 1` on the Compose rule — this ordering matters: Hilt must inject before the Activity launches, otherwise the `@AndroidEntryPoint` Activity's parent class init throws (Hilt error: "must be a subclass of HiltAndroidApp").
- `createAndroidComposeRule<MainActivity>()` (not `createComposeRule()`) so the test launches the real `MainActivity` and exercises `@AndroidEntryPoint` end-to-end. Today the activity has zero `@Inject` fields, but that's the point of a smoke test — it must keep working as fields are added.
- `onNodeWithText("Curro")` is fine because US-001 uses `stringResource(R.string.app_name)` and the test runs in the same APK with the same resources. No string-resource lookup gymnastics needed in the test.

### Composables by Feature (checklist)

- [x] No new composables — `MainActivity`'s single `Text("Curro")` is reused as the smoke-test surface.

### Material Design Components

**None new.** Inherits the Material default `Text` + `Surface` from US-001.

## Acceptance Criteria

Each is checkable on a developer machine (macOS + JDK 17) or, for the
instrumented test, on a Pixel-class Android 15 emulator. No real Redmi 15
required for this SF (US-002 is still pre-device).

- [ ] **No build regression.** `./gradlew assembleDebug` succeeds on a fresh clone — the Hilt graph compiles end-to-end with the four new module files; no KSP / Hilt-codegen error.
- [ ] **Unit tests green.** `./gradlew testDebugUnitTest` reports ≥ 1 passing, 0 failing — US-001's `SmokeTest` still passes; any optional JVM test added under Q4 also passes.
- [ ] **Hilt-injected instrumented smoke test green (manual).** With a Pixel-class Android 15 emulator running, `./gradlew connectedAndroidTest` runs `MainActivityHiltSmokeTest`: `HiltAndroidRule` injects without error, `MainActivity` launches, the text "Curro" is asserted displayed. The PR description records the manual run output. CI doesn't run instrumented tests until a later SF wires emulator-in-CI.
- [ ] **`HiltTestRunner` exists at the right path.** `app/src/androidTest/java/com/curro/app/HiltTestRunner.kt` exists and its FQN matches `com.curro.app.HiltTestRunner` — the exact value already declared by US-001 in `app/build.gradle.kts`'s `defaultConfig.testInstrumentationRunner`. Confirmed by `grep -rn HiltTestRunner app/`.
- [ ] **Four qualifier annotations exist.** `app/src/main/java/com/curro/app/di/Qualifiers.kt` declares `@IoDispatcher`, `@MainDispatcher`, `@DefaultDispatcher`, `@ApplicationScope` (each `@Qualifier` + `@Retention(AnnotationRetention.BINARY)` + a one-line KDoc).
- [ ] **`CoroutineModule` provides them.** `app/src/main/java/com/curro/app/di/CoroutineModule.kt` is `@Module @InstallIn(SingletonComponent::class) object` providing each of the three dispatchers (`Dispatchers.IO` / `.Main.immediate` / `.Default`) + a `@Singleton @ApplicationScope CoroutineScope` built on `SupervisorJob() + @IoDispatcher`.
- [ ] **`AppModule` placeholder exists.** `app/src/main/java/com/curro/app/di/AppModule.kt` is `@Module @InstallIn(SingletonComponent::class) object AppModule` with a KDoc explaining the placeholder role and listing the SFs that will land real bindings (SF-7.1, SF-3.1, SF-4.5/4.6, SF-4.1, SF-0.8).
- [ ] **No premature DI.** No Room module, no MediaPipe module, no Notification / Tts / Stt / Telecom module, no repository module, no `HandlerModule`, no telemetry module. Confirmed by `find app/src/main/java/com/curro/app/di -type f -name '*.kt' | sort` matching exactly the three files above (plus the original `.gitkeep` if kept). Reviewer rejects the PR if any of the out-of-scope modules slipped in.
- [ ] **Only the four standard Hilt scopes are used.** `grep -rn "@DefineComponent\\|@HiltAndroidModule\\|@InstallIn" app/src/main/java | grep -v -E "(SingletonComponent|ActivityRetainedComponent|ActivityComponent|ViewModelComponent)::class"` returns zero matches — no custom subcomponent.
- [ ] **`InstrumentedSmokeTest` removed.** The US-001 file `app/src/androidTest/java/com/curro/app/InstrumentedSmokeTest.kt` no longer exists; `MainActivityHiltSmokeTest` is its strict superset.
- [ ] **Lint green.** `./gradlew ktlintCheck detekt` still completes without crashing on the new files (plugin-level only — rule tuning is SF-0.3).
- [ ] **No new permissions / dependencies.** `app/build.gradle.kts` is unchanged in its `dependencies { }` block (Hilt-testing was already wired by US-001 — `androidTestImplementation(libs.hilt.android.testing)` + `kspAndroidTest(libs.hilt.compiler)`). The manifest is unchanged.
- [ ] **No `INTERNET` permission, no model weights, no PII.** US-001's invariants hold: `grep -rn 'android.permission.INTERNET' app/src` returns no match; no `*.task` / `*.tflite` / `*.bin` weights in the repo; no transcript / contact name / message body in any test fixture (none expected — this SF has no fixtures).

## Design Notes

US-002 has **no UI to design** — the on-screen output remains the
`Text("Curro")` placeholder from US-001. The senior-first design contract
(≥ 96 dp tap targets, big text, high contrast, fixed palette) lands in SF-0.4
(`CurroTheme`) and SF-0.7 (brand-design fill-in). Rendering a Material default
here means SF-0.4 remains the first and only place where the senior contract
is established.

The `brand-design` skill is currently a template — do not read brand tokens
into US-002. No new strings (`app_name = "Curro"` from US-001 is the only
string the smoke test reads).

## Senior-UX & Copy

**No user-facing Spanish copy in this SF.** US-002 is developer-facing
plumbing; the only on-screen string is `app_name = "Curro"` (a label, not
copy). Curro's voice + the canonical `COPY.*` table arrive in SF-0.7.

The senior-first contract does not apply to US-002 (no interactive UI for the
senior user), but the SF must not foreclose it:

- `dynamicColor` stays *not enabled* anywhere — SF-0.4 will lock it to `false`.
- `MainActivity` continues to call `enableEdgeToEdge()` — US-002 doesn't touch it.
- The newly-added `di/` Kotlin files are pure plumbing — no raw `Color(0xFF…)` / `.sp` / `.dp` literals are introduced, by construction (no composables touched).

## Performance Considerations

The DI graph is wired *once* at process start (Hilt's `Hilt_CurroApp.onCreate`).
The decisions in this SF have a small but real perf surface:

- **`@Singleton` on the three dispatchers and the `@ApplicationScope CoroutineScope`** — these are cheap; they are bound objects (`Dispatchers.IO` is a singleton object anyway), so the Hilt cache wins on instantiation cost from the second injection onward.
- **No reflection.** Hilt + KSP generate the component classes at compile time — no per-injection reflection cost.
- **No eager initialisation of expensive things.** The two model engines, Room, the notification listener, the TTS client, the STT client — every one of those is *out of scope* for this SF; lazy initialisation discipline is set up correctly from day 1.
- **`Dispatchers.Main.immediate` over `Dispatchers.Main`** (see Specification): saves a frame-post when already on the main thread; small but free win. Architect is free to override if there's a reason (e.g. easier coroutine testing — `TestDispatcher` doesn't care which one you injected).
- **Build perf.** Adding three small Hilt modules and one runner class. KSP incremental processing handles small Hilt diffs well — no measurable hit. No `kapt` anywhere; US-001's discipline (Hilt on KSP — Architect note A4) holds.

The `ModelWarmupService` (Phase 3+) and any background scheduler that later
SFs add will inject `@ApplicationScope CoroutineScope` and `@IoDispatcher` from
this module — both have hot paths in those SFs but trivial overhead here. The
warm-keeping latency target (< 500 ms warm FunctionGemma text→JSON) does not
depend on US-002 except in the trivial sense that it shares dispatchers.

## Testing Requirements

US-002 has no feature code, so the test bar is operational — the existing
JUnit 5 + JUnit 4 framework split (US-001 brief Architect note A5) carries
over unchanged. The added tests are:

- [ ] **`MainActivityHiltSmokeTest`** (instrumented, JUnit 4 + AndroidJUnit4 + `HiltAndroidTest`): proves the Hilt graph compiles, `HiltTestApplication` boots, `MainActivity` (with `@AndroidEntryPoint`) launches, and the text "Curro" renders. Manual run on the emulator; CI will pick it up when SF-0.X wires emulator-in-CI.
- [ ] **`InstrumentedSmokeTest` deleted** — strictly superseded.
- [ ] **(Optional, see Q4) `CoroutineQualifierTest`** (JVM, JUnit 5): a sanity check that the qualifier annotations resolve to distinct dispatchers. Pragmatic implementation **without** Hilt — Hilt on JVM is awkward; this test exercises the *types*, not the graph (e.g. `assertNotEquals(Dispatchers.IO, Dispatchers.Default)` — somewhere between trivial and dead code; if it feels like dead code, skip it). The full graph proof is the instrumented test above.
- [ ] **US-001's `SmokeTest` still passes** — no regression on the JVM side.
- [ ] **The instrumented test doesn't depend on a real Redmi 15** — a Pixel-class Android 15 emulator is enough. US-002 is still pre-device; real-Redmi-15 validation starts with Phase 1 (the launcher) and Phase 2 (voice).
- [ ] **`verification-checklist` sweep**: build / lint / unit tests pass; the instrumented test runs locally; **Privacy & permissions** section reads "no new permissions, no model weights, no INTERNET, no PII in fixtures"; the FSM / Accessibility / Real-Redmi-15 sections are explicitly N/A for this SF and the sign-off records that.

## Open Questions

**Q1 — `@ApplicationScope` parent dispatcher.** The brief specifies the
`CoroutineScope` is `SupervisorJob() + @IoDispatcher`. Alternatives: `+
@DefaultDispatcher` (CPU-bound bias) or `+ @MainDispatcher` (UI-bias). IO is
the right default for Curro because the most common app-lifetime work is
`ModelWarmupService` (reads model files from disk → IO-bound), the
`NotificationListenerService` (Binder IPC → IO-bound), and Room writes
(IO-bound). **Architect decides.** Default if unanswered: IO.

**Q2 — Qualifiers vs `DispatcherProvider` interface.** The brief uses
Hilt qualifiers. An alternative is a `DispatcherProvider` interface
(`io: CoroutineDispatcher`, `main: CoroutineDispatcher`, `default:
CoroutineDispatcher`) bound by `@Binds` and injected as a single object —
easier to swap as a unit in tests (one `@BindValue`), more boilerplate per
call-site, slightly less idiomatic for Hilt. **PM recommendation: qualifiers**
(matches `CLAUDE.md`'s implicit Hilt idiom; tests can still swap with
`@BindValue` per qualifier; one fewer indirection in handler code). Final call:
architect.

**Q3 — `AppModule` placeholder: keep or skip.** The brief proposes an empty
`AppModule` so future SFs have an obvious home. Counter-argument: empty
modules read like dead code; future SFs can just add a new module when they
need one. **PM recommendation: keep** — the cost is eight lines, the value is
removing one micro-decision from every later SF's "where does this binding
go?" moment. Architect's call.

**Q4 — JVM qualifier test: write or skip.** See Specification + Testing
sections. Writing a Hilt unit test on the JVM is awkward (Hilt is
Android-runtime-bound); writing a non-Hilt qualifier test exercises the
*types*, not the graph, and verges on dead code. **PM recommendation: skip**
— the instrumented Hilt smoke test is a strict superset; adding a JVM test
adds maintenance for no extra coverage. Architect can override.

**Q5 — Single-file qualifiers vs four files.** The brief proposes one
`Qualifiers.kt` file with four `@Qualifier` annotations. Alternatives: four
separate files (`IoDispatcher.kt`, `MainDispatcher.kt`,
`DefaultDispatcher.kt`, `ApplicationScope.kt`) — more conventional in some
Kotlin codebases. **PM recommendation: one file** — the annotations are
short, semantically related, and read together; splitting creates four 4-line
files. Architect can split if there's a project-wide convention I'm missing.

## Implementation Notes

**Order of operations** when `/implement-feature US-002` runs:

1. Create `app/src/main/java/com/curro/app/di/Qualifiers.kt` (four `@Qualifier` annotations).
2. Create `app/src/main/java/com/curro/app/di/CoroutineModule.kt` (three dispatcher providers + `@ApplicationScope` scope).
3. Create `app/src/main/java/com/curro/app/di/AppModule.kt` (empty placeholder with KDoc).
4. Delete `app/src/main/java/com/curro/app/di/.gitkeep` (now superfluous — directory has real files) **or** leave it; minor.
5. Create `app/src/androidTest/java/com/curro/app/HiltTestRunner.kt` (the `AndroidJUnitRunner` subclass).
6. Create `app/src/androidTest/java/com/curro/app/MainActivityHiltSmokeTest.kt` (`@HiltAndroidTest` + Compose UI assert).
7. Delete `app/src/androidTest/java/com/curro/app/InstrumentedSmokeTest.kt` (strictly superseded).
8. **(Optional, per Q4)** Create `app/src/test/java/com/curro/app/di/CoroutineQualifierTest.kt` — skip unless the architect says otherwise.
9. Verify three commands run green:
   1. `./gradlew assembleDebug` — green (the Hilt graph compiles).
   2. `./gradlew ktlintCheck detekt` — green (plugin-level only).
   3. `./gradlew testDebugUnitTest` — green (`SmokeTest` still passes; the optional JVM test if added also passes).
   4. (Local only, manual on emulator) `./gradlew connectedAndroidTest` — `MainActivityHiltSmokeTest` passes; record the run in the PR description.
10. Tick the AC checklist; run the `verification-checklist` privacy/permissions section; open the PR with `/generate-mr-description`.

**Architect involvement — explicit recommendation.** US-002 is small but its
decisions are load-bearing for the next 20+ SFs (every SF that touches a
coroutine, every SF that adds a Hilt module, every instrumented test that
needs to swap a dependency). The PM-orchestrator's recommendation, per the
project's "architect involvement" criterion, is to **run `android-architect`
between this brief and `android-developer` picking it up** — a single short
pass to resolve Q1–Q5 (especially Q1 dispatcher parent and Q2 qualifiers vs.
provider interface). Cost: ~15 min of architect time. Value: avoids a refactor
six SFs later when the pattern is wrong and everything has already injected
through it.

If the architect green-lights the brief without changes, `android-developer`
implements per the *Order of operations* list; `android-qa-specialist`
confirms `MainActivityHiltSmokeTest` runs end-to-end; `kotlin-reviewer` reads
the new Kotlin files for Hilt-module hygiene and the qualifier shape.

**Hand-offs this brief triggers (none of them are this SF)**:

- SF-0.3 (`android-developer`) → ktlint/detekt rule tuning, baseline, the No-Double-Padding custom rule.
- SF-0.4 (`android-ui-designer` + `material-design` + `brand-design` + `compose-patterns`) → real `CurroTheme` / `CurroColorScheme` / `CurroTypography` / `CurroShapes` / `CurroSpacing`. *None* of these need a new Hilt module — theme is pure Compose state, not DI.
- SF-1.2 (`android-developer`) → `LauncherViewModel` (`@HiltViewModel`) — uses `@IoDispatcher` for the installed-apps refresh.
- SF-2.1 / SF-2.2 (`voice-pipeline-engineer`) → `SttClient` / `TtsClient` and the first non-coroutine module (`VoiceModule`, lives in `data/voice/` or `di/`).
- SF-3.1 (`ondevice-ai-engineer`) → `MlModule` provides `FunctionGemmaEngine`, injects `@IoDispatcher` for inference.
- SF-4.1 onwards (`android-developer`) → `HandlerModule` + the function-name `@StringKey` multibinding map — first binding lands with `tell_time`.
- SF-0.8 (`android-developer` + `kotlin-reviewer`) → `TelemetryModule` (Firebase + PostHog) gated by `BuildConfig.TELEMETRY_ENABLED`.
- SF-7.1 (`android-developer`) → `DatabaseModule` (Room) + `SettingsModule` (DataStore).

**Spec ambiguity noted** (no resolution required for this SF).
`docs/curro-spec-v1.0.md` does not prescribe a DI framework, dispatcher
strategy, or `CoroutineScope` ownership — those are all `CLAUDE.md` and
master-plan decisions. No spec bump triggered by US-002; the queued v1.1 bump
(spec §5 "8/7 funciones", §12 "telemetry kept", §14 "compileSdk 35" example)
remains for end-of-Phase-0 per the master plan's "Cross-cutting work".

**Reality vs US-001 cross-check** (PM ran this before writing the brief):
- US-001 wired `kspAndroidTest(libs.hilt.compiler)` + `androidTestImplementation(libs.hilt.android.testing)` in `app/build.gradle.kts` ✓
- US-001 declared `testInstrumentationRunner = "com.curro.app.HiltTestRunner"` in `defaultConfig` ✓
- US-001's `MainActivity` is `@AndroidEntryPoint` (annotation harmless with no `@Inject` fields, sets up SF-0.2 as additive) ✓
- US-001's `CurroApp` is `@HiltAndroidApp` ✓
- `app/src/main/java/com/curro/app/di/` exists with a `.gitkeep` (no real files yet) ✓
- `HiltTestRunner.kt` does **not** exist anywhere — that's exactly US-002's job ✓
- `InstrumentedSmokeTest.kt` exists and explicitly says in its KDoc "the Hilt-aware version arrives in SF-0.2" — perfect hand-off line; US-002 honours it by deletion + replacement ✓

No SF-0.2 vs reality mismatch.

**Cross-references for the implementer**: `function-catalog` (no impact),
`voice-interaction` (no impact), `platform-integrations` (no impact),
`launcher-app` (no impact — `CATEGORY_HOME` deferred to SF-1.1),
`launcher-ui` (no impact), `accessibility-patterns` (no impact),
`material-design` (no impact), `compose-patterns` (only the test reads the
Compose tree), `on-device-llm` (catalog reservation untouched),
`local-data` (catalog reservation untouched), `brand-design` (do not read
brand tokens), `testing-patterns` (instrumented Hilt-test pattern lands here
as the canonical example for every later instrumented test),
`spec-template` (this document follows it), `git-workflow` (commit scope =
`di` per `git-workflow`'s scope table).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-13 | Fran (Claude `android-product-analyst`) | Initial draft — generated from master-plan SF-0.2 + US-001 brief + the actual SF-0.1 files on disk. Architect pass recommended before implementation (see *Implementation Notes*). |
