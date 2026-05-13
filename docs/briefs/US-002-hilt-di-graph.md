# US-002 — Hilt DI graph & `HiltTestRunner`

> Implementation brief for **SF-0.2** (`docs/master-plan.md` → Phase 0). This brief
> is the *what to build*; `/implement-feature US-002` is the *how / when*. The
> brief follows `.claude/skills/spec-template/SKILL.md`.
>
> **Architect review: complete.** US-002 is not mechanical — it locks in the
> shape Curro's DI graph keeps for the next 20+ SFs (where dispatchers live,
> whether they're qualified or wrapped in a `DispatcherProvider`, where the
> `@ApplicationScope CoroutineScope` is hosted, whether an empty `AppModule`
> exists at all, and the JVM-Hilt-test trade-off). The architect pass resolved
> **Q1–Q5** (see each `**Q# — Resolved: …**` block under *Open Questions*) and
> added the **A1–A11** decisions under *Architect's notes & decisions* plus the
> *Execution plan (developer-facing checklist)*. `/implement-feature US-002`
> can pick up directly — no further architect review required unless a
> resolved choice hits a concrete obstacle.

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
| **Architect** | Claude `android-architect` — review complete (Q1–Q5 resolved, A1–A11 added, Execution plan authored) |

## Summary

Make the Hilt graph that US-001 *plumbed* (plugin applied, `@HiltAndroidApp class
CurroApp`, `@AndroidEntryPoint class MainActivity`, the `kspAndroidTest(libs.hilt.compiler)`
+ `androidTestImplementation(libs.hilt.android.testing)` lines already in
`app/build.gradle.kts`, `testInstrumentationRunner = "com.curro.app.HiltTestRunner"`
already declared in `defaultConfig`) actually **work end-to-end**: add the
`HiltTestRunner` class itself, add the dispatcher qualifiers that every later SF
will inject, add the `CoroutineModule` that provides them + the
`@ApplicationScope CoroutineScope` (parented on `Main.immediate` per Q1), and
Hilt-ify the instrumented smoke test so it boots `HiltTestApplication` and
asserts the app renders "Curro". (No `AppModule` placeholder — Q3 reversed the
PM's recommendation; later SFs add their own named modules per concern.)

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

- **`app/src/main/java/com/curro/app/di/CoroutineModule.kt`** — `@InstallIn(SingletonComponent::class) object` providing (final shape, post Q1 resolution):
  - `@Singleton @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO`
  - `@Singleton @MainDispatcher fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate`
  - `@Singleton @DefaultDispatcher fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default`
  - `@Singleton @ApplicationScope fun provideApplicationScope(@MainDispatcher main: CoroutineDispatcher): CoroutineScope = CoroutineScope(SupervisorJob() + main + CoroutineName("CurroAppScope"))`

  `Dispatchers.Main.immediate` (not `Dispatchers.Main`): avoids a one-frame
  re-post when already on the main thread (Phase 2 press-to-listening latency).
  See A6 for why the `@ApplicationScope` parent is `Main.immediate` and why
  IO/Default work is opted into per-call via `withContext(io)` /
  `withContext(default)`.

  Why a `@ApplicationScope` `CoroutineScope` is in scope for this SF: the
  `ModelWarmupService` (Phase 3+) needs one; the alias-learning subflow (Phase
  7) launches background writes from a handler; the launcher's "refresh
  installed apps" job (Phase 7) wants one too. Providing it once now means none
  of those SFs has to add it.

- **No `AppModule.kt`.** Resolved at Q3 below — the brief originally proposed
  an empty placeholder; the architect reversed that. Future SFs create named
  modules (`DatabaseModule`, `MlModule`, `VoiceModule`, …) directly in `di/`.

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

- **No JVM qualifier test.** Resolved at Q4 below — the architect dropped the
  optional file. The instrumented `MainActivityHiltSmokeTest` is the graph
  proof; the first meaningful JVM dispatcher test lands when a real consumer
  does (SF-1.2 `LauncherViewModel`).

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
3. No new JVM tests are added in SF-0.2 (Q4 resolved skip — first meaningful JVM dispatcher test lands with SF-1.2)
4. The task reports 1 passing, 0 failing

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
│       ├── Qualifiers.kt              # new — @IoDispatcher / @MainDispatcher / @DefaultDispatcher / @ApplicationScope (one file, Q5)
│       └── CoroutineModule.kt         # new — provides the three dispatchers + @ApplicationScope CoroutineScope (Q1, Q2)
└── androidTest/java/com/curro/app/
    ├── HiltTestRunner.kt              # new — AndroidJUnitRunner that boots HiltTestApplication
    ├── MainActivityHiltSmokeTest.kt   # new — replaces InstrumentedSmokeTest; @HiltAndroidTest + Compose UI test
    └── InstrumentedSmokeTest.kt       # DELETED — superseded by the Hilt-aware version above
```

No `AppModule.kt` (Q3). No JVM qualifier test under `app/src/test/` (Q4).
`app/src/main/java/com/curro/app/di/.gitkeep` from US-001 is **deleted** once
the two real files land (the directory is no longer empty).

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

/** Marks the application-lifetime [kotlinx.coroutines.CoroutineScope] (SupervisorJob + Main.immediate). See A6. */
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Coroutine plumbing. Injected by everything async: model engines, STT/TTS clients,
 * Room DAOs, the FSM coordinator, ViewModels. Qualifier annotations (not a
 * DispatcherProvider interface) — see A2 / Q2 below. The @ApplicationScope parent
 * is Main.immediate, not IO — see A6 / Q1 below; per-call IO/Default work is opted
 * into via `withContext(io)` / `withContext(default)`.
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
        @MainDispatcher main: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + main + CoroutineName("CurroAppScope"))
}
```

(No `AppModule.kt` ships in SF-0.2 — see Q3.)

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
- [ ] **`CoroutineModule` provides them.** `app/src/main/java/com/curro/app/di/CoroutineModule.kt` is `@Module @InstallIn(SingletonComponent::class) object` providing each of the three dispatchers (`Dispatchers.IO` / `.Main.immediate` / `.Default`) + a `@Singleton @ApplicationScope CoroutineScope` built on `SupervisorJob() + @MainDispatcher + CoroutineName("CurroAppScope")` (per Q1).
- [ ] **No `AppModule.kt`** (per Q3). The `di/` directory contains exactly the two real Kotlin files (`Qualifiers.kt`, `CoroutineModule.kt`); the US-001 `.gitkeep` is removed.
- [ ] **No premature DI.** No Room module, no MediaPipe module, no Notification / Tts / Stt / Telecom module, no repository module, no `HandlerModule`, no telemetry module. Confirmed by `find app/src/main/java/com/curro/app/di -type f -name '*.kt' | sort` matching exactly the two files above. Reviewer rejects the PR if any of the out-of-scope modules slipped in.
- [ ] **Only the four standard Hilt scopes are used** (A1). `grep -rn "@DefineComponent\\|@HiltAndroidModule\\|@InstallIn" app/src/main/java | grep -v -E "(SingletonComponent|ActivityRetainedComponent|ActivityComponent|ViewModelComponent)::class"` returns zero matches — no custom subcomponent.
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
SFs add will inject `@ApplicationScope CoroutineScope` (the cancellation root,
parented on `Main.immediate` per Q1) and use `withContext(@IoDispatcher)` for
the actual model-file IO. Both have hot paths in those SFs but trivial overhead
here. The warm-keeping latency target (< 500 ms warm FunctionGemma text→JSON)
does not depend on US-002 except in the trivial sense that it shares
dispatchers.

## Testing Requirements

US-002 has no feature code, so the test bar is operational — the existing
JUnit 5 + JUnit 4 framework split (US-001 brief Architect note A5) carries
over unchanged. The added tests are:

- [ ] **`MainActivityHiltSmokeTest`** (instrumented, JUnit 4 + AndroidJUnit4 + `HiltAndroidTest`): proves the Hilt graph compiles, `HiltTestApplication` boots, `MainActivity` (with `@AndroidEntryPoint`) launches, and the text "Curro" renders. Manual run on the emulator; CI will pick it up when SF-0.X wires emulator-in-CI. **This is the canonical shape for every later instrumented Hilt test in Curro** (see A2).
- [ ] **`InstrumentedSmokeTest` deleted** — strictly superseded.
- [ ] **No JVM qualifier test added** (Q4 resolved skip). The first meaningful JVM test that injects a dispatcher lands when a real consumer does — SF-1.2 (`LauncherViewModel`) at the earliest, with `runTest { … }` + `StandardTestDispatcher` + the project's `TestDispatcherExtension` (A8).
- [ ] **US-001's `SmokeTest` still passes** — no regression on the JVM side.
- [ ] **The instrumented test doesn't depend on a real Redmi 15** — a Pixel-class Android 15 emulator is enough. US-002 is still pre-device; real-Redmi-15 validation starts with Phase 1 (the launcher) and Phase 2 (voice).
- [ ] **`verification-checklist` sweep**: build / lint / unit tests pass; the instrumented test runs locally; **Privacy & permissions** section reads "no new permissions, no model weights, no INTERNET, no PII in fixtures"; the FSM / Accessibility / Real-Redmi-15 sections are explicitly N/A for this SF and the sign-off records that.

## Open Questions

**Q1 — Resolved: `SupervisorJob() + Dispatchers.Main.immediate` (via `@MainDispatcher`).**
Rationale: this matches the **Google / Now-in-Android default** and aligns with
how `CoroutineScope` is meant to be used at the *parent* level — the parent
dispatcher is the **resumption surface** (where `stateIn(scope)`, `shareIn(scope)`,
`launch { … }`-from-a-`@Composable`-callback land), not the work surface. Every
real worker in Curro (`ModelWarmupService`, the `NotificationListener` unread
cache, Room/DataStore Flows in `AliasRepository` / `FavoriteAppsRepository`, the
`InstalledAppsProvider` refresh) already wraps its IO with `withContext(io)` or
exposes a `Flow.flowOn(io)` at the data-layer boundary — that is the correct
place to opt into IO, **not** the scope's parent. Anchoring the scope to
`Main.immediate` means: (a) `stateIn(applicationScope)` flows that ViewModels /
overlays collect emit on Main, no extra dispatcher bounce, no one-frame jitter
in the press-to-listening latency budget (Phase 2: < 1 s); (b) TTS queueing
(`TtsClient` is Main-bound — `TextToSpeech.speak` must be invoked on the looper
thread it was constructed on) is a natural fit; (c) it makes the divergence
between "this is app-lifetime structural concurrency" (the scope) and "this is
where the work runs" (per-call `withContext(@IoDispatcher)`) explicit, which is
exactly the discipline Curro wants the developer to internalise from SF-0.2
forward.

Why not IO: a scope built on `IO` would let every consumer accidentally
`launch { … }` IO-on-IO and forget to swap dispatcher when touching UI state,
producing main-thread violations only at runtime on a real device. Why not
`Default`: same problem, plus we have no genuinely CPU-bound app-scope work
(parsing one WhatsApp notification is microseconds, not work that wants its own
parent thread pool).

Resolved snippet:

```kotlin
@Provides @Singleton @ApplicationScope
fun provideApplicationScope(
    @MainDispatcher main: CoroutineDispatcher,
): CoroutineScope = CoroutineScope(SupervisorJob() + main + CoroutineName("CurroAppScope"))
```

Note the `CoroutineName("CurroAppScope")` — costs nothing, makes stack-traces /
the coroutine-debugger / Crashlytics non-fatal logs immediately readable when
diagnosing "which scope leaked this?" later. Per-call work that needs IO uses
`withContext(io)` explicitly; per-call work that needs CPU uses
`withContext(default)`. **The scope is not where the dispatcher decision is
made** — it is the cancellation root.

**Q2 — Resolved: Hilt qualifier annotations (no `DispatcherProvider` interface).**
Rationale: the testability win of a `DispatcherProvider` is **already covered**
by Curro's existing test infrastructure. `testing-patterns` ships a
`TestDispatcherExtension` (JUnit 5) that swaps `Dispatchers.Main` globally for
all JVM tests, and the FSM / coordinator tests (`voice-pipeline-engineer`'s
turf) will run on a `StandardTestDispatcher` injected directly via
`@TestInstallIn` + a `TestCoroutineModule` (see A7 below). The marginal benefit
of one-`@BindValue`-instead-of-three is real but small, and it does **not**
justify the cost: a `DispatcherProvider` interface forces every call-site to
read `dispatchers.io` instead of injecting `@IoDispatcher` directly, which (a)
adds an indirection that does not exist anywhere else in Curro's domain layer,
(b) couples every constructor to a "bag of dispatchers" object even when it
only needs one, and (c) drifts from how `CLAUDE.md`'s example ViewModel /
handler / coordinator snippets read.

Qualifiers also map 1:1 onto how Hilt's documentation and the broader Android
community write this code — the developer doesn't have to learn a
Curro-specific abstraction. **One named thing per construction parameter**
beats "object with three properties" for code-review readability.

Resolved shape (call-site):

```kotlin
class ReadLastWhatsAppHandler @Inject constructor(
    private val notifications: NotificationRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : FunctionHandler { /* … */ }
```

Test override (instrumented, per-test override of the whole module — see A7):

```kotlin
@TestInstallIn(components = [SingletonComponent::class], replaces = [CoroutineModule::class])
@Module
object TestCoroutineModule {
    private val testDispatcher = StandardTestDispatcher()
    @Provides @Singleton @IoDispatcher      fun io(): CoroutineDispatcher = testDispatcher
    @Provides @Singleton @MainDispatcher    fun main(): CoroutineDispatcher = testDispatcher
    @Provides @Singleton @DefaultDispatcher fun default(): CoroutineDispatcher = testDispatcher
    @Provides @Singleton @ApplicationScope  fun scope(@MainDispatcher m: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + m)
}
```

For per-test single-fake swaps (e.g. only `@IoDispatcher` swapped), `@BindValue
@IoDispatcher val io: CoroutineDispatcher = TestCoroutineDispatcher()` inside
the test class is also available — both paths work, both are documented in A4
below.

**Q3 — Resolved: skip the empty `AppModule`.**
Rationale: I reverse the PM's recommendation here. The brief's own "Out of
Scope" list already enumerates **eight named modules** that later SFs will
create (`DatabaseModule`, `SettingsModule`, `MlModule`, `VoiceModule`,
`NotificationModule`, `TelecomModule`, `HandlerModule`, `TelemetryModule`).
That list is the answer to "where does this binding go?", not an empty
`AppModule`. An empty `AppModule` invites entropy: it becomes the default
landing pad for "I'm not sure where this goes" providers, and six SFs later
it's a 200-line grab-bag of unrelated bindings that should have been their own
named module. The discipline we want from the codebase is **one module per
domain concern, named after that concern** — `CoroutineModule` is the
exemplar.

The micro-decision cost the PM is trying to save ("where does this go?") is
already O(15 seconds) given the named-module list, and the file ban (AC
"`find` returns exactly three files") becomes one entry shorter and one less
thing to drift on. Six lines and a KDoc are not free — they are six lines the
next developer reads and asks "what is this for, do I need to know?" before
moving on.

Resolved file layout under `app/src/main/java/com/curro/app/di/`:

```
Qualifiers.kt        # the four @Qualifier annotations (see Q5)
CoroutineModule.kt   # the only module shipped in SF-0.2
```

Update the "No premature DI" AC and the *Order of operations* checklist to
match: only **two** files in `di/`, no `AppModule.kt`. The `.gitkeep` from
US-001 can be deleted (directory now has real files). When the next named
module lands (chronologically: `VoiceModule` in SF-2.1 / SF-2.2), it is created
fresh next to `CoroutineModule.kt`.

Reversibility: O(2 min) — if a later SF wants `AppModule` back, it adds one
file. We are not painting ourselves into a corner.

**Q4 — Resolved: skip the JVM qualifier test.**
Rationale: agreed with the PM. A non-Hilt JVM test that asserts
`Dispatchers.IO !== Dispatchers.Default` is testing the Kotlin standard library,
not Curro. A Hilt-on-JVM test would need `@HiltAndroidTest` + Robolectric, which
works but is overkill for a graph that has **four bindings** and is already
exercised end-to-end by `MainActivityHiltSmokeTest` (the Hilt rule's
`inject()` call fails fast if any binding in the graph is unsatisfiable).

The first time we will write a meaningful JVM test that exercises a dispatcher
is **SF-1.2** (`LauncherViewModel`'s `viewModelScope` + `@IoDispatcher`-driven
installed-apps refresh) — and that test will use `runTest { … }` +
`StandardTestDispatcher` + the project's `TestDispatcherExtension`. That is the
right level to test at; not at the qualifier-annotation level.

Resolved: **drop the optional file**. Update the "Source files this SF lands"
table and the *Order of operations* to not list it; update the Testing
Requirements bullet to be a definitive "no JVM test added in SF-0.2" rather
than "(optional, see Q4)".

The first JVM test that injects an `@IoDispatcher` lands when a real consumer
does — SF-1.2 at the earliest. Hilt-graph-shape regressions are caught by the
instrumented smoke test running on every PR once SF-0.X wires emulator-in-CI.

**Q5 — Resolved: one file (`Qualifiers.kt`), as a deliberate exception to
`CLAUDE.md`'s "one class/interface per file" rule.**
Rationale: the rule binds for **types with logic** — classes, interfaces with
methods, sealed hierarchies, ViewModels, repositories, handlers. It is the
right rule for those: it makes navigation deterministic (`Foo.kt` contains
`Foo`), keeps diffs surgical, and avoids the "junk drawer" file. **Empty
annotation declarations are not the rule's target.** A `@Qualifier` annotation
with no body is a marker: it has no methods, no state, no implementation, and
no test surface of its own. Splitting these four 4-line annotations into four
separate files gives nothing back and costs four IDE tabs every time the
developer looks at the dispatcher set.

The convention I'm cementing here, as a documented exception (see A9 below):

> **Annotations-with-no-bodies may live together in one file when they share a
> domain.** Examples Curro will accumulate: `Qualifiers.kt` (DI scopes),
> `FunctionKey.kt` (the single map-key annotation for `HandlerModule` — alone,
> stays alone) — and that's it for now. **No other type-with-logic file gets
> this exception.**

Resolved file (final form for SF-0.2):

```kotlin
// app/src/main/java/com/curro/app/di/Qualifiers.kt
package com.curro.app.di

import javax.inject.Qualifier

/** Marks the [kotlinx.coroutines.CoroutineDispatcher] backed by [kotlinx.coroutines.Dispatchers.IO]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

/** Marks the [kotlinx.coroutines.CoroutineDispatcher] backed by [kotlinx.coroutines.Dispatchers.Main.immediate]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher

/** Marks the [kotlinx.coroutines.CoroutineDispatcher] backed by [kotlinx.coroutines.Dispatchers.Default]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

/** Marks the application-lifetime [kotlinx.coroutines.CoroutineScope] (SupervisorJob + Main.immediate). See A6. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
```

`Retention.BINARY` is the correct choice — `RUNTIME` is heavier (annotation
metadata read by reflection) and we have no reflective consumer; Hilt's KSP
processor sees `BINARY` annotations fine. KDoc is one-line, references the
underlying `Dispatchers.*` constant so IDE-hover answers the question without
opening `CoroutineModule.kt`.

## Architect's notes & decisions

These are the load-bearing DI / coroutine decisions the architect locked in for
SF-0.2. Each note is referenced from the Scope / Specification / Acceptance
Criteria / Q-Resolved sections above, and from later-SF briefs. **All of them
must be settled by the time `/implement-feature US-002` writes the first
`@Module`** — they propagate to every later SF that adds a Hilt module or
injects a coroutine, so re-litigating them in SF-3.x or SF-4.x means a
multi-file refactor.

**A1. Four standard Hilt components only — no custom subcomponents.** Curro
uses exactly four Hilt components, and these are the only `@InstallIn` targets
allowed for the prototype:

- `SingletonComponent` — process-lifetime bindings (`CoroutineModule` lives
  here; `DatabaseModule`, `MlModule`, `VoiceModule`, `NotificationModule`,
  `HandlerModule`, `TelemetryModule` will all install here too)
- `ActivityRetainedComponent` — survives configuration changes; used by
  `@HiltViewModel` ViewModels under the hood (we rarely install here directly)
- `ActivityComponent` — for `@AndroidEntryPoint Activity`-scoped bindings (rare
  in Curro — `MainActivity` injects nothing for now)
- `ViewModelComponent` — auto-applied by `@HiltViewModel`

**No `@DefineComponent`, no custom subcomponent, no `@HiltAndroidModule` outside
these four scopes.** A prototype with one Activity, one launcher home, and a
state-machine-driven assistant has zero use for custom scopes. If a later SF
genuinely needs one (the spec doesn't suggest any will), that SF owns the
decision **and** the architect re-review — it's a non-trivial change to the
graph shape. Enforced by the AC `grep` line.

**A2. `@HiltAndroidTest` + `HiltAndroidRule(this)` is the only blessed pattern
for instrumented Hilt tests.** Every later instrumented test in Curro follows
*exactly* the shape `MainActivityHiltSmokeTest` ships:

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FooHandlerInstrumentedTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()
    @Before fun setUp() { hiltRule.inject() }
    // …
}
```

`order = 0` on the Hilt rule, `order = 1` on the Compose rule — non-negotiable.
Hilt must inject **before** the Activity launches, or `@AndroidEntryPoint`'s
generated parent class throws ("must be a subclass of HiltAndroidApp") because
`HiltTestApplication` hasn't yet been associated with the Activity's component
tree. `kotlin-reviewer` rejects any later test that ships with the rules in the
wrong order or without explicit `order` attributes.

**A3. `HiltTestApplication` is the test Application — Curro ships no custom
test Application class.** Hilt's stock `dagger.hilt.android.testing.HiltTestApplication`
is what `HiltTestRunner.newApplication(…)` swaps in for `CurroApp`. There is
no `CurroTestApp.kt`, no `@CustomTestApplication(CurroApp::class)`, no
`@HiltAndroidApp class TestCurroApp` parallel to `CurroApp` — none of these are
needed today and adding any of them prematurely would be a maintenance tax (two
Application classes to keep in sync). If a later SF adds non-DI behaviour to
`CurroApp` that *must* run in tests (e.g. a Crashlytics bootstrap that should
run in CI to verify wiring), that SF owns the decision to either (a) gate the
behaviour on `BuildConfig.DEBUG` / a flag, or (b) introduce a custom test app
via `@CustomTestApplication`. Default: don't.

**A4. `@TestInstallIn` vs `@BindValue` — when to reach for each.** Both swap
graph bindings in instrumented tests; they are not interchangeable.

| Use case | Mechanism | Lifetime |
|---|---|---|
| **Permanent test-graph replacement** — every test in `androidTest` source set sees the fake (e.g. a fake `FunctionCallEngine` for every UI test that boots a screen) | `@TestInstallIn(components = [SingletonComponent::class], replaces = [RealModule::class]) object FakeModule { @Provides fun fakeEngine(): FunctionCallEngine = FakeFunctionCallEngine() }` placed under `app/src/androidTest/.../di/` | Entire instrumented test source set |
| **Per-test override** — only *this* test class needs the fake (e.g. a specific `FakeSttClient` that returns a curated transcript for one FSM test) | `@HiltAndroidTest @UninstallModules(VoiceModule::class) class FooTest { @BindValue @JvmField val stt: SttClient = FakeSttClient(curatedTranscript) }` inside the test class | One test class |

The shorthand: **`@TestInstallIn` for the test sourceSet, `@BindValue` for the
test class.** Mixing them in the same test is allowed but confusing; prefer
the simpler form. SF-3.x onwards will lean on this heavily — every handler
test fakes its system-integration source via `@BindValue`, every overlay UI
test fakes the assistant coordinator via `@TestInstallIn` to a `FakeCoordinator`
exposing a controlled `StateFlow<AssistantState>`.

**A5. KSP everywhere — never kapt.** US-001 already wired `hilt-compiler` on
KSP for both `ksp(libs.hilt.compiler)` (main / debug / release) and
`kspAndroidTest(libs.hilt.compiler)` (instrumented). US-002 inherits this: the
new `CoroutineModule` is processed by KSP, and the `MainActivityHiltSmokeTest`
is too (Hilt generates `Hilt_MainActivityHiltSmokeTest` etc. on the instrumented
test classpath). Why this matters for SF-0.2: it is **easy to forget
`kspAndroidTest`** when adding a Hilt-aware test module — the symptom is
"missing binding" at runtime, hours of poking. US-001 got this right (per its
A4 note); US-002 just needs not to undo it. **Never add `kapt`** — it would
add ~20% to compile time across the project's life and gains nothing.

**A6. `viewModelScope` is the screen-scoped dispatch surface; `@ApplicationScope`
is for genuinely app-lifetime work only.** The `@ApplicationScope CoroutineScope`
that `CoroutineModule` ships is **not a general escape hatch**. Its legitimate
consumers are:

- `ModelWarmupService` — keeping FunctionGemma warm across the activity's
  lifecycle, surviving config changes and brief app backgrounding
- `CurroNotificationListenerService` — the unread-cache writer (Binder callback
  → repo write); the cache must survive the activity dying
- The launcher's "refresh installed apps" job (Phase 7) — runs every N hours,
  doesn't care about which Activity is foregrounded
- The alias-learning subflow background write — fire-and-forget from a handler

**Not legitimate consumers**: ViewModels (use `viewModelScope`), composables
(use `rememberCoroutineScope` for click-handler-scoped work), handlers (most
handler work is `suspend fun` called from the coordinator's scope — no scope
injection needed), the FSM coordinator (owns its own scope, parented on
`@ApplicationScope`'s `SupervisorJob` for cancellation chaining).

The parent dispatcher of `@ApplicationScope` is **`Main.immediate`** (Q1
resolved) — the scope is the cancellation root, not the work surface. Per-call
IO/Default work uses `withContext(io)` / `withContext(default)` explicitly.
This is the Google / Now-in-Android convention; it makes the divergence
between "structural concurrency" (scope) and "thread choice" (dispatcher)
explicit at every call site, which is the discipline we want.

**A7. `@TestInstallIn(SingletonComponent::class, replaces = [CoroutineModule::class]) object TestCoroutineModule` is the canonical pattern for swapping dispatchers in instrumented tests.** Show this in every later test brief that exercises a dispatcher under `@HiltAndroidTest`:

```kotlin
// app/src/androidTest/java/com/curro/app/di/TestCoroutineModule.kt — lands when the first instrumented
// test needs deterministic dispatcher control (NOT in SF-0.2 — the smoke test doesn't care)
@TestInstallIn(components = [SingletonComponent::class], replaces = [CoroutineModule::class])
@Module
object TestCoroutineModule {
    private val testDispatcher = StandardTestDispatcher()
    @Provides @Singleton @IoDispatcher       fun io(): CoroutineDispatcher = testDispatcher
    @Provides @Singleton @MainDispatcher     fun main(): CoroutineDispatcher = testDispatcher
    @Provides @Singleton @DefaultDispatcher  fun default(): CoroutineDispatcher = testDispatcher
    @Provides @Singleton @ApplicationScope   fun scope(@MainDispatcher m: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + m)
}
```

Notice all four bindings are re-provided — `@TestInstallIn(replaces = ...)`
replaces **the whole module**, so you must re-provide every binding it had.
Forgetting `@ApplicationScope` here is the most common bug — it fails late, at
the first injection that asks for it, not at graph-validation time. Documented
once here, referenced by SF-3.x / SF-4.x test briefs.

**A8. `runTest` + `StandardTestDispatcher` for JVM unit tests of code that
uses an injected `@IoDispatcher`.** The pattern, threaded with
`testing-patterns`' `TestDispatcherExtension`:

```kotlin
@ExtendWith(TestDispatcherExtension::class)   // swaps Dispatchers.Main globally
class SomeRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repo = SomeRepository(io = testDispatcher)   // direct constructor injection, no Hilt on JVM

    @Test fun `does the thing`() = runTest(testDispatcher) {
        repo.doTheThing()
        advanceUntilIdle()
        // assertions
    }
}
```

JVM unit tests in Curro **do not** boot the Hilt graph (`@HiltAndroidTest`
would require Robolectric — too heavy for the volume of unit tests we'll run
on every commit). They construct the SUT directly, pass the dispatcher as a
constructor arg, and let `runTest` + `advanceUntilIdle()` control time.
`TestDispatcherExtension` handles the `Dispatchers.Main` swap so `viewModelScope`-driven
ViewModel tests work without per-test boilerplate. The first SF that exercises
this pattern is SF-1.2 (`LauncherViewModel`); the canonical example will live
in `testing-patterns` once SF-1.2 lands.

**A9. Annotation-grouping exception to "one class/interface per file".**
`CLAUDE.md`'s "one class/interface per file" rule binds for types with logic —
classes, interfaces with methods, sealed hierarchies, ViewModels, repositories,
handlers, modules. It does **not** bind for empty-body `@Qualifier` /
`@MapKey` annotations. Documented exception (Q5 resolution): **annotations
with no bodies that share a domain may live together in one file**.

Curro's current and projected uses of this exception:

- `app/src/main/java/com/curro/app/di/Qualifiers.kt` — the four DI scope/dispatcher annotations (SF-0.2, this brief)
- `app/src/main/java/com/curro/app/handler/FunctionKey.kt` — the single `@MapKey` annotation for the `HandlerModule` multibinding map (SF-4.1) — alone, stays alone (it is the only annotation in its domain)

That's it. No other file gets this exception. `kotlin-reviewer` policy:
multiple `@Qualifier` / `@MapKey` annotations in one file = OK if they share a
domain (DI dispatchers, handler map keys, etc.); anything with a method body or
state = one per file.

**A10. `@AndroidEntryPoint` on `MainActivity` is harmless today and stays.**
US-001 already annotated `MainActivity` with `@AndroidEntryPoint` even though
it has zero `@Inject` fields. `MainActivityHiltSmokeTest` exercises the
Hilt → Activity boot path end-to-end, so when SF-1.2 adds the first injected
field (`@Inject lateinit var launcherViewModel: LauncherViewModel` — actually
no, ViewModels use `hiltViewModel()` in Compose; the first real `@Inject` on
`MainActivity` is more likely a top-level coordinator handle in SF-5.x), the
smoke test catches any graph break with the same shape it has today. **Do not
remove the annotation as an "unused" cleanup** — its presence is the
load-bearing guarantee that the graph compiles against a real Activity, not
just against the application class.

**A11. Reversibility checkpoint.** Of the five Q resolutions, four are
trivially reversible:

| Q | Resolution | Reversal cost |
|---|---|---|
| Q1 | `@ApplicationScope` parent = `Main.immediate` | O(5 min) — change one provider line; existing consumers are robust to it because they `withContext` for off-Main work |
| Q2 | Qualifiers (no `DispatcherProvider`) | O(15 min) — adding a wrapper later is mechanical; changes ~5 constructor signatures per SF |
| Q3 | Skip `AppModule` | O(2 min) — add the file back if a real grab-bag emerges |
| Q4 | Skip JVM qualifier test | O(0 — never needed) |
| Q5 | One `Qualifiers.kt` file | O(5 min) — splitting four annotations is mechanical |

Q1 is the only resolution with non-trivial **second-order** cost: if the parent
is later switched to `IO`, every consumer that *implicitly* depended on
Main-scoped emission (composable `collectAsState`, ViewModel `stateIn(scope)`)
needs an audit. Mitigation: A6 documents the discipline ("per-call `withContext`,
scope is just cancellation"); if every SF follows it, the parent-dispatcher
choice is genuinely cosmetic. **Pin the discipline; the choice is reversible.**

## Execution plan (developer-facing checklist)

This is the architect-cleaned, Q-resolved version of the *Order of operations*
the PM drafted. Each step is verifiable in isolation; do not advance to step
N+1 until step N is green.

1. **Qualifier file.** Create `app/src/main/java/com/curro/app/di/Qualifiers.kt`
   exactly per the snippet in Q5-Resolved (four `@Qualifier` annotations,
   `Retention.BINARY`, one-line KDocs, `ApplicationScope` KDoc references A6).
   Verify: `grep -c "@Qualifier" app/src/main/java/com/curro/app/di/Qualifiers.kt` → 4.

2. **Coroutine module.** Create `app/src/main/java/com/curro/app/di/CoroutineModule.kt`
   exactly per the Q1-Resolved snippet (`SupervisorJob() + @MainDispatcher +
   CoroutineName("CurroAppScope")`). Top-level KDoc references A2 / Q2 / A6 /
   Q1. Verify: `./gradlew :app:assembleDebug` — green (Hilt graph compiles).

3. **Delete `.gitkeep`.** `rm app/src/main/java/com/curro/app/di/.gitkeep` — the
   directory has two real Kotlin files now.

4. **No `AppModule.kt`** (Q3). **No JVM qualifier test** (Q4). Skip both.

5. **Hilt test runner.** Create `app/src/androidTest/java/com/curro/app/HiltTestRunner.kt`
   per the In Scope snippet. Verify: the FQN matches what `app/build.gradle.kts`
   already declared in `defaultConfig.testInstrumentationRunner` —
   `com.curro.app.HiltTestRunner` (US-001 wrote the line; US-002 makes the
   class real).

6. **Hilt-aware smoke test.** Create `app/src/androidTest/java/com/curro/app/MainActivityHiltSmokeTest.kt`
   per the In Scope snippet — `@HiltAndroidTest`, `@get:Rule(order = 0)` Hilt
   rule, `@get:Rule(order = 1)` Compose rule, `hiltRule.inject()` in `@Before`,
   `composeRule.onNodeWithText("Curro").assertIsDisplayed()` (A2).

7. **Delete the old smoke test.** `rm app/src/androidTest/java/com/curro/app/InstrumentedSmokeTest.kt`
   — strictly superseded by step 6.

8. **Three commands green** (in this order; earlier ones produce artifacts the
   later ones depend on):
   1. `./gradlew assembleDebug` — Hilt graph compiles (the only behaviour
      change in SF-0.2).
   2. `./gradlew ktlintCheck detekt` — finishes without crashing (plugin-level
      only, per US-001's A10).
   3. `./gradlew testDebugUnitTest` — discovers and runs `SmokeTest` (US-001's
      JUnit 5 JVM test) — green; no new JVM tests in SF-0.2.
   4. (Local only, manual on a Pixel-class Android 15 emulator)
      `./gradlew connectedAndroidTest` — `MainActivityHiltSmokeTest` discovered,
      booted with `HiltTestApplication`, `MainActivity` launched, text "Curro"
      asserted displayed. Record the run output in the PR description.

9. **Verify forbidden items did NOT slip in.**
   - `find app/src/main/java/com/curro/app/di -type f -name '*.kt' | sort` →
     exactly `Qualifiers.kt` + `CoroutineModule.kt` (no `AppModule.kt`).
   - `grep -rn "android.permission.INTERNET" app/src` → no match.
   - `grep -rn "DispatcherProvider" app/src` → no match (Q2 resolved against).
   - `grep -rn "@DefineComponent\|@HiltAndroidModule" app/src/main/java` → no
     match (A1 — no custom subcomponents).
   - `grep -rn "kapt" app/build.gradle.kts` → no match (A5 — KSP only).

10. **Tick AC**; run the `verification-checklist` skill's *Privacy &
    permissions* pass (Spec §12 — no new permissions, no model weights, no
    PII, telemetry untouched); open the PR with `/generate-mr-description`
    (commit scope `di` per `git-workflow`).

## Implementation Notes

**Order of operations.** Canonical step-by-step lives in *Execution plan
(developer-facing checklist)* above. Short version: `Qualifiers.kt` →
`CoroutineModule.kt` → delete `.gitkeep` → `HiltTestRunner.kt` →
`MainActivityHiltSmokeTest.kt` → delete `InstrumentedSmokeTest.kt` →
`assembleDebug` / `ktlintCheck detekt` / `testDebugUnitTest` green → manual
emulator run of `connectedAndroidTest` → tick AC → open PR.

**Owner split.** PM (Fran via `android-product-analyst`) owns Metadata / Summary /
Scope / Acceptance Criteria / Design Notes / Senior-UX & Copy. **Architect
(`android-architect`)** reviewed the brief, **resolved Q1–Q5**, authored the
*Architect's notes & decisions* (A1–A11) and the *Execution plan
(developer-facing checklist)* appendices, and tightened the Specification +
Testing Requirements + Acceptance Criteria sections to match the resolved
choices. The architect's role here is to lock in the DI / coroutine shape that
every later SF (SF-3.x ML, SF-4.x handlers, SF-7.x persistence, SF-0.8
telemetry, SF-5.x FSM) injects through; no Clean-Architecture decision lands
in US-002 beyond "qualifiers, not provider interface" and "Main.immediate
parent scope". `android-developer` implements per the Execution plan;
`android-qa-specialist` confirms `MainActivityHiltSmokeTest` runs end-to-end on
a Pixel-class Android 15 emulator; `kotlin-reviewer` reads the resulting
Kotlin for module hygiene, qualifier shape, and conformance with A1–A11.

**Architect involvement — status: complete.** US-002 is small but its
decisions are load-bearing for the next 20+ SFs (every SF that touches a
coroutine, every SF that adds a Hilt module, every instrumented test that
needs to swap a dependency). The architect pass resolved Q1–Q5 and added
A1–A11; **no further architect review is required before `/implement-feature
US-002`**. If the developer hits a concrete obstacle implementing one of the
resolved choices (e.g. `@MainDispatcher` parent breaks the smoke test in a way
the architect didn't foresee), the developer escalates back to the architect
for a re-review rather than silently flipping the choice.

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
| 2026-05-13 | Claude `android-architect` | Architecture review: resolved Q1 (`@ApplicationScope` parent = `Main.immediate` + `CoroutineName("CurroAppScope")`, IO/Default opted-into per-call), Q2 (Hilt qualifiers, no `DispatcherProvider` interface), Q3 (skip `AppModule` placeholder — reversed PM recommendation; named modules per concern instead), Q4 (skip JVM qualifier test), Q5 (one `Qualifiers.kt` file as documented exception to "one class/interface per file"). Added Architect's notes A1–A11 and Execution plan (developer-facing checklist). Updated Specification, Source files, Acceptance Criteria, Performance Considerations, Testing Requirements, Owner Split. |
