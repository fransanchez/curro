# US-001 — Gradle skeleton & version catalog

> Implementation brief for **SF-0.1** (`docs/master-plan.md` → Phase 0). This brief
> is the *what to build*; `/implement-feature US-001` is the *how / when*. The
> brief follows `.claude/skills/spec-template/SKILL.md`.
>
> **Architect note.** US-001 is **mechanical project scaffolding**, not
> Clean-Architecture design — there is no domain/data/handler/assistant code to
> design because no feature code lands here. **However**, the scaffold has a
> handful of subtle, load-bearing build-system choices (Kotlin 2.x's separate
> Compose Compiler plugin, plugin application order for KSP+Hilt, JUnit 5's
> AGP-specific wiring, BuildConfig generation toggle in AGP 8+) that, if wrong,
> turn US-001 into a half-day of debugging. `android-architect` has therefore
> reviewed and enriched the Android Specification, Performance Considerations,
> Testing Requirements, and added an "Architect's notes & decisions" appendix
> calling each one out.

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Gradle skeleton & version catalog |
| **US ID** | US-001 |
| **SF ID** | SF-0.1 (master-plan) |
| **Phase** | 0 — Project foundation |
| **Status** | In Progress |
| **Created** | 2026-05-13 |
| **Modified** | 2026-05-13 (architect review pass) |
| **PM Owner** | Fran |
| **Architect** | Claude `android-architect` — build-system review only (no Clean-Architecture decisions in this SF; see Architect's notes appendix) |

## Summary

Generate the empty Android Gradle project that every subsequent Curro SF will plug
into: Kotlin 2.1 + Jetpack Compose + Material 3 + Hilt + KSP, a single `app` module,
`gradle/libs.versions.toml` as the canonical version source, ktlint/detekt plugins
wired, the `com.curro.app` package skeleton stamped out per `CLAUDE.md` →
"Architecture", and one trivial passing test in each source set. The acceptance
bar is the master-plan's: `./gradlew assembleDebug` produces an installable APK
on a fresh clone, with no model weights, no telemetry SDKs, and no `CATEGORY_HOME`
intent filter yet (those each land with the SF that needs them — SF-3.x for models,
SF-0.8 for telemetry, SF-1.1 for `CATEGORY_HOME`).

This story has **no user-visible value** for Fran's father — the user-facing payoff
arrives in Phase 1. The value here is operational: every other story is blocked on
this one. Keep it lean — do not over-engineer the package skeleton or the catalog,
and resist the temptation to pre-wire dependencies whose owning SF is still
in the future. Spec ref: `docs/curro-spec-v1.0.md` §14 (stack & build order).

## Scope

### In Scope

- **Gradle wrapper** at the repo root: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` — **Gradle 8.10+** (the floor for AGP 8.7+ on JDK 17; 8.11.x or whatever's latest-stable at build time is fine, but **do not** go below 8.10).
- **Root build config**: `settings.gradle.kts` (with `pluginManagement` + `dependencyResolutionManagement` + `enableFeaturePreview("VERSION_CATALOGS")` if AGP requires it on the chosen version), root `build.gradle.kts` (alias-only plugins, no inline versions).
- **Single `app` module** with `app/build.gradle.kts`:
  - `applicationId = "com.curro.app"`, `namespace = "com.curro.app"`
  - `minSdk = 31`, `compileSdk = 35`, `targetSdk = 35`
  - `versionCode = 1`, `versionName = "0.1.0"`
  - **Plugins, applied in this exact order** (order is load-bearing — see Architect's notes A2): `com.android.application` → `org.jetbrains.kotlin.android` → `org.jetbrains.kotlin.plugin.compose` → `com.google.devtools.ksp` → `dagger.hilt.android.plugin` → `de.mannodermaus.android-junit5` → `org.jlleitschuh.gradle.ktlint` → `io.gitlab.arturbosch.detekt`. Notice `org.jetbrains.kotlin.plugin.compose` is a **separate Kotlin plugin** since Kotlin 2.0 (it is no longer the AGP-bundled Compose-compiler-extension; see Architect's notes A1). There is **no `kapt`** anywhere — Hilt runs on KSP (A4).
  - `buildFeatures { compose = true ; buildConfig = true }` — the `compose` toggle is paired with applying `kotlin-compose`; the `buildConfig` toggle is **explicitly enabled** because AGP 8+ defaults it off and US-001 declares a `BuildConfig.TELEMETRY_ENABLED` field (A8).
  - JDK 17 source/target compatibility (`compileOptions { sourceCompatibility = JavaVersion.VERSION_17 ; targetCompatibility = JavaVersion.VERSION_17 }`), Kotlin JVM target 17 (`kotlinOptions { jvmTarget = "17" }`). Matches `setup-java` JDK 17 in `.github/workflows/ci.yml` (A9).
  - `buildConfigField("boolean", "TELEMETRY_ENABLED", "false" /* debug */ | "true" /* release */)` — SDK wiring (Firebase / PostHog deps + the `INTERNET` permission in the release manifest) is **SF-0.8**; this SF just declares the flag so SF-0.8 has something to branch on (A14).
  - Release signing config plumbing reading from `local.properties` (the actual signing keystore is not committed; if the properties are missing, release falls back to debug-signing so CI doesn't break — document this in the file's comment).
  - `proguardFiles` set up; an empty `proguard-rules.pro` lives at `app/proguard-rules.pro`. `isMinifyEnabled = false` in **both** debug and release — R8 tuning lands later (the release-flavour R8 story belongs with telemetry & models, not the scaffold).
  - `testInstrumentationRunner = "com.curro.app.HiltTestRunner"` declared even though the runner itself is SF-0.2's job — declaring it now means SF-0.2 only has to add the class.
  - **JUnit 5 wiring**: apply the **`de.mannodermaus.gradle.plugins.android-junit5`** Gradle plugin (the de-facto way to make JUnit 5 work for AGP's `testDebugUnitTest` task; calling `tasks.withType<Test>().configureEach { useJUnitPlatform() }` alone is **not enough** under AGP because AGP composes its own `Test` task and ignores top-level wiring unless this plugin is applied — see A5 for the gory detail). Instrumented tests stay on **JUnit 4** + `AndroidJUnit4` runner — different world, different deps.
- **Version catalog** at `gradle/libs.versions.toml`:
  - **Active entries** (referenced by `app/build.gradle.kts`):
    - **Build & language**: AGP **8.7+** (latest stable that pairs with the chosen Kotlin), Kotlin **2.1.x**, KSP **must use the paired version `<kotlin>-1.0.<n>`** (A3 — picking the wrong pair is the #1 cause of "Symbol not found" KSP errors).
    - **Compose**: the **Compose BOM** (latest stable as of build date — pick on the day; see A6). Individual artifacts (`compose.ui`, `compose.ui-graphics`, `compose.ui-tooling`, `compose.ui-tooling-preview`, `compose.material3`, `compose.foundation`, `compose.runtime`, `compose.ui-test-junit4`, `compose.ui-test-manifest`) resolve their version through the BOM — **never pin them inline**.
    - **Compose Compiler**: the plugin `org.jetbrains.kotlin.plugin.compose` versioned identically to Kotlin (A1). There is **no separate `composeCompiler` version entry** — the plugin tracks Kotlin's version, full stop.
    - **AndroidX core**: `androidx.activity:activity-compose`, `androidx.core:core-ktx`, `androidx.lifecycle:lifecycle-runtime-ktx`, `androidx.lifecycle:lifecycle-viewmodel-compose`.
    - **DI**: Hilt 2.5x (`hilt-android` runtime + `hilt-compiler` processor via KSP — A4); Hilt 2.5x supports KSP first-class so we deliberately skip `kapt` (no annotation-processor compile-time tax).
    - **Coroutines**: `kotlinx-coroutines-android` + `kotlinx-coroutines-test`.
    - **Test (JVM)**: `junit-jupiter-api`, `junit-jupiter-engine`, Mockk + `mockk-android`, Turbine, Robolectric, plus the Gradle **`de.mannodermaus.android-junit5`** plugin (A5 — this is the AGP-compatible way to surface JUnit 5 on `testDebugUnitTest`).
    - **Test (instrumented)**: `androidx.test.ext:junit`, `espresso-core`, the Compose UI test artifacts (via the BOM), `hilt-android-testing`. **Instrumented tests stay on JUnit 4** — JUnit 5 on instrumented Android is not supported by AGP at the time of writing; this is a hard line, not a preference (A5).
    - **Lint plugins**: `org.jlleitschuh.gradle.ktlint` **12.1.x** (K2-compatible), `io.gitlab.arturbosch.detekt` **1.23.x** (K2 experimental — enable explicitly when SF-0.3 tightens rules; here, defaults only — A10).
  - **Reserved entries** (declared in the `[versions]` and `[libraries]` blocks but **not yet referenced** from `app/build.gradle.kts`) for: Room, DataStore Preferences, MediaPipe Tasks GenAI / LiteRT, Coil (Compose), Firebase BoM + Crashlytics + Analytics, PostHog. Each reserved entry has a trailing comment `# Activated in SF-X.Y`. Reserving them now means later SFs only flip one switch.
- **Manifest** at `app/src/main/AndroidManifest.xml`:
  - `<application android:name=".CurroApp" android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round" android:label="@string/app_name" android:theme="@style/Theme.Curro">`
  - One `<activity android:name=".MainActivity" android:exported="true">` with the standard `MAIN` + `LAUNCHER` intent filter only
  - **No `CATEGORY_HOME` / `CATEGORY_DEFAULT`** (SF-1.1)
  - **No runtime permissions** (each lands per spec §10 with the SF that needs it)
  - **No `INTERNET` permission** (SF-0.8 adds it to the release manifest only)
- **Kotlin source**:
  - `app/src/main/java/com/curro/app/CurroApp.kt` — `@HiltAndroidApp class CurroApp : Application()` stub. (Hilt plugin is wired here in US-001; the dependency-graph wiring — modules, entry points, the test runner class — is SF-0.2.)
  - `app/src/main/java/com/curro/app/MainActivity.kt` — `ComponentActivity` that calls `enableEdgeToEdge()` and `setContent { CurroTheme { Surface(Modifier.fillMaxSize()) { Text("Curro") } } }`.
  - `app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt` — a **stub** `@Composable fun CurroTheme(content: @Composable () -> Unit) { MaterialTheme(content = content) }`. **No `CurroColorScheme` / `CurroTypography` / `CurroShapes` / `CurroSpacing` files yet** — those land in SF-0.4. The point of the stub is that US-001 is self-contained: the empty app boots and renders "Curro", and SF-0.4 swaps the stub for the real theme without touching `MainActivity`.
- **Resources** at `app/src/main/res/`:
  - `values/strings.xml` with `<string name="app_name">Curro</string>` and nothing else (no user-facing copy yet — that's a brand-design + senior-UX concern owned by SF-0.7).
  - `mipmap-*/ic_launcher*` — the default Android Studio adaptive icon set (placeholder; the real launcher icon is a brand decision, owned by SF-0.7).
  - `values/themes.xml` declaring `Theme.Curro` as a thin `Theme.Material3.DayNight.NoActionBar` so the manifest reference resolves; Compose drives the actual theming via `CurroTheme { }`. (`Theme.Curro` exists only so the splash/manifest theme has something to point at — it is **not** the design system.)
- **Package skeleton** (empty directories preserved with `.gitkeep`), per `CLAUDE.md` → "Architecture":
  ```
  app/src/main/java/com/curro/app/
  ├── domain/{model,catalog,repository,usecase}/.gitkeep
  ├── data/{local,ml,voice,notification,telephony,apps,contacts,repository}/.gitkeep
  ├── handler/.gitkeep
  ├── assistant/.gitkeep
  ├── service/.gitkeep
  ├── presentation/{theme,launcher,assistant,config,common,navigation}/.gitkeep
  ├── di/.gitkeep
  └── util/.gitkeep
  ```
- **Test source sets**:
  - `app/src/test/java/com/curro/app/SmokeTest.kt` — a single JUnit 5 test asserting `2 + 2 == 4` so `./gradlew test` has something to run.
  - `app/src/androidTest/java/com/curro/app/InstrumentedSmokeTest.kt` — a single `@Test` using `AndroidJUnit4` so `./gradlew connectedAndroidTest` discovers it. (Hilt-instrumented testing is SF-0.2 — this one does not yet depend on Hilt.)
- **Lint config files**:
  - `app/detekt.yml` — the detekt default config exported (`./gradlew detektGenerateConfig` style) with **no extra rules tuned**. SF-0.3 owns rule tuning, a baseline file, and the No-Double-Padding custom rule.
  - `.editorconfig` at the repo root with ktlint-compatible defaults (`indent_size = 4`, `max_line_length = 120`, `ktlint_standard_no-wildcard-imports = enabled`). Again — defaults only; SF-0.3 tightens.
- **Repo housekeeping**:
  - `.gitignore` already exists at repo root — verify it covers `local.properties`, `*.keystore`, `*.jks`, `.env*`, `google-services.json`, `app/build/`, `build/`, `.gradle/`, `.idea/` (the existing file should already be correct; if anything is missing, add it).
  - Confirm `.github/workflows/ci.yml` lint/build/test steps run successfully against this skeleton — the workflow file already exists and is hash-gated on `app/build.gradle.kts` for the Firebase decode step (which stays a no-op until SF-0.8).

### Out of Scope (each is its own SF)

- **Hilt DI graph** (modules, entry points, the actual `HiltTestRunner` class, an instrumented smoke test that injects something) → **SF-0.2** (US-002).
- **ktlint/detekt rule tuning, baseline file, the No-Double-Padding rule, pre-commit hooks** → **SF-0.3** (US-003).
- **Real `CurroTheme` / `CurroColorScheme` / `CurroTypography` / `CurroShapes` / `CurroSpacing`** (senior-first tokens, the `dynamicColor = false` lock, font scale previews) → **SF-0.4** (US-004).
- **Shared big components** (`BigPrimaryButton`, `BigYesNoRow`, `BigCard`, `BigListRow`) → **SF-0.5**.
- **`CurroNavHost` and `MainActivity` upgrades** (`singleTask`, portrait lock, the navigation shell, the two routes) → **SF-0.6**.
- **`brand-design` fill-in + the canonical Spanish `COPY.*` table** → **SF-0.7** (US-005).
- **Telemetry plumbing** (Firebase Crashlytics + Analytics, PostHog, the `TelemetrySink` interface, the `INTERNET` permission gated to release only, the no-PII guardrail test) → **SF-0.8**.
- **`CATEGORY_HOME` intent filter, `RoleManager` flow, "make me default" button** → **SF-1.1** (Phase 1).
- **Anything that needs a runtime permission** (`RECORD_AUDIO`, `READ_CONTACTS`, `CALL_PHONE`, `BIND_NOTIFICATION_LISTENER_SERVICE`, `QUERY_ALL_PACKAGES`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`) — each lands with its owning SF in Phase 1+.
- **Model weights, MediaPipe LLM Inference, LiteRT, Room, DataStore, Coil** — only catalog *entries* (commented "Activated in SF-X.Y") land here; the dependency lines in `app/build.gradle.kts` arrive with the owning SF.

## User Flows

US-001 has **no end-user flow**. It is developer-facing only — the only "user" is
a Curro developer (Fran, Claude) running Gradle locally and the CI runner on
GitHub Actions. The two flows are:

### Flow 1: Fresh-clone build

1. Developer: `git clone <repo> && cd curro`
2. Developer: `./gradlew assembleDebug`
3. Gradle resolves the catalog, downloads dependencies, builds the APK in `app/build/outputs/apk/debug/app-debug.apk`
4. Developer: `./gradlew installDebug` (with an emulator/device connected)
5. The app launches and renders the text "Curro" on a Material default background — no crash, no ANR

### Flow 2: CI pipeline run

1. `git push` to a branch or open a PR → `.github/workflows/ci.yml` triggers
2. The job sets up JDK 17 + Gradle, then runs the existing pipeline:
   - `Decode google-services.json` → no-op (the hash-guard requires `app/build.gradle.kts`, which now exists, but the secret isn't set yet — the workflow already prints "GOOGLE_SERVICES_JSON secret not set — skipping.")
   - `Lint (ktlint + detekt)` → `./gradlew ktlintCheck detekt` is green
   - `Build debug` → `./gradlew assembleDebug` is green
   - `Run unit tests` → `./gradlew testDebugUnitTest` is green; the smoke test passes
   - `Upload test results` → the artifact is uploaded
3. The PR is green — every future Curro SF lands on a known-green base

## Function-catalog Impact

**No catalog change.** SF-0.1 ships no handler, no `CatalogFunction`, no FunctionGemma
prompt, no JSON schema. The `domain/catalog/` directory is created empty (with a
`.gitkeep`) so SF-3.x has somewhere to put the catalog when it lands.

Cross-reference: the `function-catalog` skill stays untouched until SF-3.x; this
SF is only responsible for the directory existing.

## FSM States Touched

**None.** SF-0.1 ships no assistant code — no `AssistantStateMachine`, no
`AssistantCoordinator`, no `ConfidencePolicy`, no overlays. The `assistant/`
directory is created empty (`.gitkeep`) so Phase 3 + Phase 5 SFs have a home.

Cross-reference: the `voice-interaction` skill stays untouched; SF-5.x is the
first SF that produces FSM code.

## Android System Integrations & Permissions

**No system integrations**, **no runtime permissions** declared in this SF. The
manifest is minimal:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".CurroApp"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:theme="@style/Theme.Curro">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| *(none in this SF)* | Each permission is declared by the SF that needs it (spec §10) | N/A | N/A |

The lazy-permission discipline from spec §10 starts now: do **not** prophylactically
declare permissions in the manifest "for later". When SF-2.1 needs `RECORD_AUDIO`,
SF-2.1 declares it; when SF-4.6 needs `BIND_NOTIFICATION_LISTENER_SERVICE`, SF-4.6
declares it. Same for `INTERNET` — SF-0.8 adds it to **the release manifest only**.

Cross-reference: `platform-integrations` skill (no integrations yet), `launcher-app`
skill (`CATEGORY_HOME` intentionally deferred to SF-1.1 to avoid hijacking the dev
device's home screen before there's a real launcher home to show).

## On-device-model Impact

**No model impact.** SF-0.1 declares neither MediaPipe nor LiteRT dependencies in
`app/build.gradle.kts`; the version catalog reserves entries for both with a
trailing comment `# Activated in SF-3.1`, but **the lines in `app/build.gradle.kts`
that would pull them in do not exist yet**. The debug build therefore weighs in
at a handful of MB — well under any practical CI / dev-machine concern.

**No model weights are committed**, **no MediaPipe imports** appear anywhere in the
source, and there is no `data/ml/` Kotlin code (the directory exists empty). This
satisfies the `verification-checklist` "Builds without the model weights" gate by
construction — there is nothing model-shaped to break.

Cross-reference: `on-device-llm` skill — SF-3.1 will be the first SF to touch it.

## Android Specification

### Source files this SF lands

```
curro/
├── gradlew, gradlew.bat
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml                                  # canonical version source
├── settings.gradle.kts                                     # pluginManagement + dependencyResolutionManagement
├── build.gradle.kts                                        # root: alias-only plugins
├── .editorconfig                                           # ktlint-friendly defaults
├── app/
│   ├── build.gradle.kts                                    # the only module
│   ├── detekt.yml                                          # detekt defaults (rules tuned in SF-0.3)
│   ├── proguard-rules.pro                                  # empty
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml                         # minimal; CurroApp + MainActivity only
│       │   ├── java/com/curro/app/
│       │   │   ├── CurroApp.kt                             # @HiltAndroidApp stub
│       │   │   ├── MainActivity.kt                         # enableEdgeToEdge + Text("Curro")
│       │   │   ├── presentation/theme/CurroTheme.kt        # stub — full theme arrives in SF-0.4
│       │   │   ├── domain/{model,catalog,repository,usecase}/.gitkeep
│       │   │   ├── data/{local,ml,voice,notification,telephony,apps,contacts,repository}/.gitkeep
│       │   │   ├── handler/.gitkeep
│       │   │   ├── assistant/.gitkeep
│       │   │   ├── service/.gitkeep
│       │   │   ├── presentation/{launcher,assistant,config,common,navigation}/.gitkeep
│       │   │   ├── di/.gitkeep
│       │   │   └── util/.gitkeep
│       │   └── res/
│       │       ├── values/strings.xml                      # only <string name="app_name">Curro</string>
│       │       ├── values/themes.xml                       # Theme.Curro = thin Theme.Material3.DayNight.NoActionBar
│       │       └── mipmap-*/ic_launcher*                   # placeholder adaptive icon (real one in SF-0.7)
│       ├── test/java/com/curro/app/SmokeTest.kt            # JUnit 5: 2 + 2 == 4
│       └── androidTest/java/com/curro/app/InstrumentedSmokeTest.kt   # AndroidJUnit4: launches MainActivity
└── (existing) .github/workflows/ci.yml, README.md, .gitignore, docs/, CLAUDE.md, .claude/
```

### Screens and Composables

`CLAUDE.md` says Curro has very few "screens" — the launcher home and the config
menu are the only routes, and the assistant UI lives in state-driven overlays. In
US-001, none of those exist yet. What exists is **one trivial composable** that
renders "Curro" so the app boots and exits cleanly:

```kotlin
// app/src/main/java/com/curro/app/MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CurroTheme {
                Surface(Modifier.fillMaxSize()) {
                    Text(text = stringResource(R.string.app_name))
                }
            }
        }
    }
}

// app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt
// SF-0.4 will replace this stub with the real CurroColorScheme/Typography/Shapes/Spacing.
@Composable
fun CurroTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
```

**No `Content` composable split, no `ViewModel`, no state** — this is the
empty-launcher placeholder. Splitting `Screen` ⇄ `Content` and introducing a VM is
the right pattern but it has zero value in this SF and would be deleted/rewritten
by SF-0.4 anyway.

### ViewModels and State Management

**None in this SF.** The first ViewModel arrives in SF-1.2 (`LauncherViewModel`).

### Navigation Routes

**None in this SF.** `CurroNavHost` is SF-0.6. `MainActivity` here just hosts a
single composable; the nav graph (with `Launcher` ⇄ `ConfigMenu`) is built later.

### Hilt Modules

**None in this SF.** Hilt is *wired* (the `dagger.hilt.android.plugin` plugin
applied, the `@HiltAndroidApp` annotation on `CurroApp`, `ksp(libs.hilt.compiler)`
running) but **no `@Module` files** are added. The `di/` package directory is
created empty (carrying a `.gitkeep`) so SF-0.2 has somewhere to drop the first
`DatabaseModule` / `RepositoryModule` / `HandlerModule` / `MlModule` / `VoiceModule`.
SF-0.2 adds the first modules, the entry-point wiring on `MainActivity`
(`@AndroidEntryPoint`), and the `HiltTestRunner` class declared by
`testInstrumentationRunner` in this SF.

The `MainActivity` here already carries `@AndroidEntryPoint` so SF-0.2 is a
zero-friction continuation, not a rewrite — the annotation is harmless without any
`@Inject` constructors to satisfy. This is deliberate, and it is *the only*
Hilt-related work in US-001: plugin + the `@HiltAndroidApp` Application + the
`@AndroidEntryPoint` Activity. Anything beyond that (binding interfaces,
multibinding maps, the `FunctionHandler` map key, scopes) is SF-0.2's contract.

> **Forward signal to SF-0.2**: when you add the first modules, you do **not**
> need to revisit `app/build.gradle.kts` — KSP + the Hilt plugin are already
> wired here. SF-0.2 is purely additive on the Kotlin side.

### Composables by Feature (checklist)

- [x] `MainActivity` calls `setContent` with `CurroTheme { Text("Curro") }`
- [x] `CurroTheme` stub
- [ ] *(everything else is later SFs)*

### Material Design Components

- `MaterialTheme` (via `CurroTheme` stub) — picks up Compose BOM defaults.
- `Surface` + `Text` — the only components used in this SF.

The senior-first scale-up (≥ 96 dp tap targets, big text, high contrast, the fixed
palette / `dynamicColor = false` lock) belongs to SF-0.4. This SF deliberately
renders Material defaults because the placeholder is throwaway and we want SF-0.4
to be the one place where the senior contract lands.

### `gradle/libs.versions.toml` shape (worked example)

```toml
[versions]
# --- Build & language ---
agp                  = "8.7.x"          # MIN: 8.7 (Kotlin-2.1 / new Compose-Compiler plugin floor); use latest stable
kotlin               = "2.1.x"          # >= 2.1 (K2); pick the latest stable
ksp                  = "2.1.x-1.0.x"    # MUST be paired with kotlin above (form: <kotlin>-1.0.<n>) — see Architect's notes A3
java                 = "17"

# --- AndroidX core ---
coreKtx              = "1.13.x"
activityCompose      = "1.9.x"
lifecycle            = "2.8.x"

# --- Compose ---
composeBom           = "2025.xx.xx"     # pick the latest stable as of build date (May 2026); TODO at /implement-feature time
# Note: with Kotlin 2.x there is NO separate Compose Compiler version — the
# `org.jetbrains.kotlin.plugin.compose` plugin reuses the `kotlin` version above (A1).

# --- DI ---
hilt                 = "2.5x"           # Hilt 2.5x supports KSP; we deliberately do NOT use kapt (A4)

# --- Coroutines ---
coroutines           = "1.9.x"

# --- Test ---
junitJupiter         = "5.10.x"
androidJunit5Plugin  = "1.10.x"         # de.mannodermaus.android-junit5 — required for AGP `testDebugUnitTest` to see JUnit 5 (A5)
mockk                = "1.13.x"
turbine              = "1.1.x"
robolectric          = "4.13.x"
androidxTestExt      = "1.2.x"
espresso             = "3.6.x"

# --- Lint plugins ---
ktlintPlugin         = "12.1.x"         # K2-compatible
detektPlugin         = "1.23.x"         # K2 support is experimental; rule-tuning is SF-0.3, not here

# --- Reserved for later SFs ---
room             = "2.6.x"       # Activated in SF-7.1
datastore        = "1.1.x"       # Activated in SF-7.1
mediapipeGenai   = "0.10.x"      # Activated in SF-3.1
litert           = "1.0.x"       # Activated in SF-3.1
coil             = "2.7.x"       # Activated in SF-1.4 (app icons) / SF-4.10 (contact photos)
firebaseBom      = "33.x.x"      # Activated in SF-0.8
posthog          = "3.x.x"       # Activated in SF-0.8

[libraries]
# --- Active ---
androidx-core-ktx              = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose      = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }

compose-bom                    = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui                     = { module = "androidx.compose.ui:ui" }
compose-ui-graphics            = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling-preview     = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling             = { module = "androidx.compose.ui:ui-tooling" }
compose-material3              = { module = "androidx.compose.material3:material3" }
compose-ui-test-junit4         = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest       = { module = "androidx.compose.ui:ui-test-manifest" }

hilt-android                   = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler                  = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-android-testing           = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt" }

kotlinx-coroutines-android     = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test        = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

junit-jupiter-api              = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junitJupiter" }
junit-jupiter-engine           = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junitJupiter" }
mockk                          = { module = "io.mockk:mockk", version.ref = "mockk" }
mockk-android                  = { module = "io.mockk:mockk-android", version.ref = "mockk" }
turbine                        = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric                    = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-ext-junit        = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
espresso-core                  = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

# --- Reserved (NOT YET referenced from app/build.gradle.kts) ---
room-runtime                   = { module = "androidx.room:room-runtime", version.ref = "room" }                   # Activated in SF-7.1
room-ktx                       = { module = "androidx.room:room-ktx", version.ref = "room" }                       # Activated in SF-7.1
room-compiler                  = { module = "androidx.room:room-compiler", version.ref = "room" }                  # Activated in SF-7.1
datastore-preferences          = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" } # Activated in SF-7.1
mediapipe-tasks-genai          = { module = "com.google.mediapipe:tasks-genai", version.ref = "mediapipeGenai" }   # Activated in SF-3.1
coil-compose                   = { module = "io.coil-kt:coil-compose", version.ref = "coil" }                      # Activated in SF-1.4
firebase-bom                   = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }      # Activated in SF-0.8
firebase-crashlytics           = { module = "com.google.firebase:firebase-crashlytics" }                            # Activated in SF-0.8
firebase-analytics             = { module = "com.google.firebase:firebase-analytics" }                              # Activated in SF-0.8
posthog-android                = { module = "com.posthog:posthog-android", version.ref = "posthog" }               # Activated in SF-0.8

[plugins]
# ORDER MATTERS — see Architect's notes A2 for the rationale.
# In app/build.gradle.kts, apply these in this order:
#   android.application → kotlin.android → kotlin.compose → ksp → hilt → ktlint → detekt
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android      = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose      = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }   # A1 — Kotlin-2.x Compose Compiler is a separate plugin
ksp                 = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt                = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
android-junit5      = { id = "de.mannodermaus.android-junit5", version.ref = "androidJunit5Plugin" }   # A5 — JVM unit tests on JUnit 5
ktlint              = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlintPlugin" }
detekt              = { id = "io.gitlab.arturbosch.detekt", version.ref = "detektPlugin" }
# Reserved
google-services     = { id = "com.google.gms.google-services", version = "4.4.x" }                                 # Activated in SF-0.8
firebase-crashlytics-plugin = { id = "com.google.firebase.crashlytics", version = "3.x.x" }                       # Activated in SF-0.8
```

> Exact version digits (the `x` placeholders) are picked by the implementer when
> they run `/implement-feature US-001` — the **rule** is "the latest stable that
> AGP 8.x can swallow on JDK 17", **the values** are not load-bearing for this
> brief. What is load-bearing: catalog as single source of truth, reserved entries
> commented with their owning SF, plugins under `[plugins]`, libraries under
> `[libraries]`, version refs under `[versions]`.

### `app/build.gradle.kts` shape (worked example, abridged)

```kotlin
plugins {
    // ORDER MATTERS — see Architect's notes A2.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)      // A1 — Kotlin-2.x Compose Compiler plugin (replaces the old AGP-bundled extension)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.junit5)      // A5 — surfaces JUnit 5 to AGP's testDebugUnitTest task
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.curro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.curro.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.curro.app.HiltTestRunner"   // class arrives in SF-0.2
    }

    signingConfigs {
        create("release") {
            // Reads from local.properties; falls back to debug-signing if missing
            // so CI doesn't break. local.properties is git-ignored.
            // …
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false                                                    // R8 off in debug — keeps the APK lean & build fast
            buildConfigField("boolean", "TELEMETRY_ENABLED", "false")                  // SF-0.8 will branch on this
        }
        release {
            isMinifyEnabled = false                                                    // R8 tuning is later (post-Phase-0)
            buildConfigField("boolean", "TELEMETRY_ENABLED", "true")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true                                                                  // paired with `kotlin.plugin.compose` (A1)
        buildConfig = true                                                              // AGP 8+ defaults this OFF; we need it for TELEMETRY_ENABLED (A8)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17                                    // A9 — matches setup-java in CI
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests — JUnit 4 + AndroidJUnit4 runner (NOT JUnit 5; see A5).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

// JUnit 5 platform wiring is handled by the `android-junit5` Gradle plugin applied above (A5).
// The explicit configureEach below is belt-and-braces for any non-AGP Test tasks (e.g. plain Gradle tests).
tasks.withType<Test>().configureEach { useJUnitPlatform() }
```

> Note the **conspicuous absence** of: Room, DataStore, MediaPipe, Coil, Firebase,
> PostHog. Their catalog entries exist; their dependency lines do not. Each owning
> SF flips a switch — exactly one line per dependency.

## Acceptance Criteria

Each is checkable on a developer machine (macOS + JDK 17) — no real Redmi 15
required for this SF (US-001 is pre-device).

- [ ] **Fresh-clone build green.** `git clone <repo> && cd curro && ./gradlew assembleDebug` succeeds on a clean machine (JDK 17) without manual intervention; the build produces an installable `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **CI green.** GitHub Actions (`.github/workflows/ci.yml`) runs to completion: `Lint`, `Build debug`, `Run unit tests`, `Upload test results` all green; the `Decode google-services.json` step prints its no-op message (it stays a no-op until SF-0.8).
- [ ] **Version catalog is the single source of truth.** `grep -rn 'version[ ]*=[ ]*"[0-9]' app/build.gradle.kts build.gradle.kts settings.gradle.kts` finds zero inline version literals (excepting the AGP plugin-management bootstrap in `settings.gradle.kts` which has to live there). Every other version lives in `gradle/libs.versions.toml`.
- [ ] **App identity.** `applicationId = "com.curro.app"`, `namespace = "com.curro.app"`, `minSdk = 31`, `compileSdk = 35`, `targetSdk = 35`, `versionCode = 1`, `versionName = "0.1.0"`. Verified by `./gradlew :app:dependencies` and inspection of the generated manifest in `app/build/intermediates/merged_manifests/debug/AndroidManifest.xml`.
- [ ] **The empty app launches.** `./gradlew installDebug` on a Pixel-class Android 15 emulator → launching from the launcher screen shows a Material-default surface with the text "Curro" rendered in the centre/start. No crash, no ANR, no error in `adb logcat | grep -i curro`.
- [ ] **Hilt plugin is wired and inert.** `@HiltAndroidApp class CurroApp` compiles; the generated `Hilt_CurroApp` class is produced by KSP; the app boots. No DI modules exist yet (SF-0.2).
- [ ] **Lint plugins run to completion.** `./gradlew ktlintCheck detekt` finishes without crashing on the SF-0.1 source (it may report warnings on the default detekt config; **the AC for this SF is that the plugins run**, not that the codebase passes a tuned rule set — that's SF-0.3).
- [ ] **Unit tests discovered & green.** `./gradlew test` runs `app/src/test/java/com/curro/app/SmokeTest.kt` (JUnit 5) and reports 1+ tests passed, 0 failed; `useJUnitPlatform()` is enabled.
- [ ] **Instrumented tests discovered.** `./gradlew connectedAndroidTest` against a running emulator runs `app/src/androidTest/java/com/curro/app/InstrumentedSmokeTest.kt` and reports 1+ tests passed (this AC is checked locally; CI does not run instrumented tests).
- [ ] **Package layout matches `CLAUDE.md` → "Architecture".** A `find app/src/main/java/com/curro/app -type d | sort` listing matches the package skeleton in this brief's "Scope" section (every directory exists, each empty one carries a `.gitkeep`).
- [ ] **Manifest is minimal.** `app/src/main/AndroidManifest.xml` declares `CurroApp` + `MainActivity` only, with the standard `MAIN` + `LAUNCHER` intent filter. **No `CATEGORY_HOME` / `CATEGORY_DEFAULT`**, **no `<uses-permission>` lines**, **no `<uses-feature>` lines, no `<service>` / `<receiver>` declarations**.
- [ ] **`MainActivity` is a launcher placeholder, not a launcher.** The class extends `ComponentActivity`, calls `enableEdgeToEdge()`, sets `CurroTheme { Surface { Text("Curro") } }`. It is annotated `@AndroidEntryPoint` so SF-0.2's DI work is friction-free, but no field is `@Inject`-ed.
- [ ] **`CurroTheme` is an explicit stub.** `app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt` is a one-function file that wraps `MaterialTheme`. A KDoc comment on it says "Stub — full senior-first theme arrives in SF-0.4". No `CurroColorScheme` / `CurroTypography` / `CurroShapes` / `CurroSpacing` files exist yet.
- [ ] **`telemetryEnabled` BuildConfig flag.** `BuildConfig.TELEMETRY_ENABLED` is generated and equals `false` in `debug`, `true` in `release`. No code yet branches on it (SF-0.8).
- [ ] **No forbidden artifacts.** No model weight files (`*.task`, `*.tflite`, `*.bin`) in the repo; no `google-services.json` committed; no `local.properties` committed; no `*.keystore` / `*.jks` committed. `.gitignore` already covers these — verify nothing slipped in.
- [ ] **No `INTERNET` permission anywhere.** `grep -rn 'android.permission.INTERNET' app/src` returns no matches (SF-0.8 adds it to the release manifest only).
- [ ] **Reserved catalog entries do not leak into the build.** `./gradlew :app:dependencies | grep -E 'room|mediapipe|firebase|posthog|coil|datastore'` returns no matches — the reservations are catalog-only.

## Design Notes

US-001 has **no UI to design**. The placeholder text "Curro" rendered in Material
defaults is a *deliberate non-design* — the senior-first design contract (≥ 96 dp
tap targets, big text, high contrast, fixed palette / `dynamicColor = false`) lands
in SF-0.4 (`CurroTheme`) and SF-0.7 (brand-design fill-in). Rendering a Material
default here means SF-0.4 is the **first and only place** where the senior contract
is established — no prior visual state to migrate away from.

The `brand-design` skill is currently a template; do **not** read brand tokens
into US-001. The literal string "Curro" is also fine to put directly in
`strings.xml` because it is the app name, not user-facing copy in Curro's voice.

## Senior-UX & Copy

**No user-facing Spanish copy in this SF.** The only string is `app_name = "Curro"`
in `res/values/strings.xml` — that's a label, not copy. Curro's voice + the
canonical `COPY.*` table arrive in SF-0.7.

The senior-first contract (tap targets, text size, contrast, "feels the same
every day", audio + visual together, no fussy animation) does not apply to US-001
because there is no interactive UI for the senior user yet — the placeholder is
developer-facing. **But** the project layout must not foreclose the contract:

- `dynamicColor` is **not** enabled anywhere — SF-0.4 will lock it to `false` and any code that tries to flip it later will be flagged in review.
- `MainActivity` calls `enableEdgeToEdge()` so SF-0.4 starts from the correct insets baseline.
- The package directory `presentation/theme/` exists and is the only place the theme will be defined — no other file imports `Color(0xFF…)` / raw `.sp` / raw `.dp` from anywhere because no other UI file exists.
- Strings live in `res/values/strings.xml` from day 1 — there is no precedent for hard-coded literals in composables (the placeholder Text uses `stringResource(R.string.app_name)`, not `"Curro"`).

## Performance Considerations

- **Build time** is the only perf concern in this SF: keep `app/build.gradle.kts` small (no plugins applied that aren't used; no `kapt`, only `ksp`); rely on the Compose BOM so artifact resolution is fast.
- **APK size** stays small (single Activity, no third-party deps beyond Compose + Hilt + Coroutines + AndroidX core — well under 5 MB).
- **No model weights** in the APK (the whole "huge APK" risk in `CLAUDE.md` does not apply to this SF; it applies to the release flavour after SF-3.1 / SF-9.1).
- **Reserved catalog entries do not appear in `:app:dependencies`** — verified by an explicit AC. Reserving in TOML alone has zero build-time / runtime cost.

## Testing Requirements

US-001 has no feature code, so the test bar is operational, not behavioural. The
**two distinct test worlds** are spelled out explicitly because they are the most
commonly-conflated thing on Android (A5):

| | JVM unit tests | Instrumented tests |
|---|---|---|
| Source set | `app/src/test/java/com/curro/app/` | `app/src/androidTest/java/com/curro/app/` |
| Test framework | **JUnit 5** (Jupiter API + Engine) | **JUnit 4** + `AndroidJUnit4` runner |
| Runner / harness | The `de.mannodermaus.android-junit5` Gradle plugin surfaces JUnit 5 to AGP's `testDebugUnitTest` task | `com.curro.app.HiltTestRunner` (declared in `app/build.gradle.kts`; class arrives in SF-0.2) |
| Dependencies | `junit-jupiter-api` (`testImplementation`), `junit-jupiter-engine` (`testRuntimeOnly`); + Mockk / Turbine / Robolectric reserved for later SFs | `androidx.test.ext:junit`, `espresso-core`, `compose-ui-test-junit4`, `hilt-android-testing` |
| Task name | `./gradlew testDebugUnitTest` (precisely the name the CI workflow calls — A5) | `./gradlew connectedAndroidTest` (needs a device/emulator; not run by CI) |
| First test in this SF | `SmokeTest.kt` — `@Test fun two_plus_two() { assertEquals(4, 2 + 2) }` using `org.junit.jupiter.api.Test` | `InstrumentedSmokeTest.kt` — `@RunWith(AndroidJUnit4::class) class … { @Test fun activity_starts() = ActivityScenario.launch(MainActivity::class.java).use { … } }` using `org.junit.Test` |

The two worlds **cannot** be merged — JUnit 5 is not supported on instrumented
Android by AGP at the time of writing. **Do not** add `junit-jupiter-*` to
`androidTestImplementation`; **do not** add `androidx.test.ext:junit` to
`testImplementation`. If the developer is tempted to "unify" the two frameworks,
they should stop and re-read A5.

- [ ] **`SmokeTest.kt`** (JVM, JUnit 5): one method asserting a trivial truth. Purpose: prove the `android-junit5` plugin successfully surfaces JUnit 5 to AGP's `testDebugUnitTest` task.
- [ ] **`InstrumentedSmokeTest.kt`** (instrumented, JUnit 4 + AndroidJUnit4): one `@Test` method using `ActivityScenario.launch(MainActivity::class.java)` to confirm the Activity launches without crashing. (No Hilt injection here yet — SF-0.2 lands the Hilt-aware version on top of the same scaffolding.)
- [ ] **No FSM, handler, parser, Room, or LLM tests** in this SF — there is no feature code to cover. The `testing-patterns` Curro list (FSM transitions, LLM/STT/TTS fakes, the WhatsApp parser fixture suite, in-memory Room, `ConfidencePolicy`, the alias-learning subflow, the senior-UI compose tests) is bootstrapped here only to the extent that **the test source sets exist and run** — content lands with each owning SF.
- [ ] **`./gradlew testDebugUnitTest`** is the precise task name the CI workflow already calls — verify the `android-junit5` plugin makes it find the JUnit 5 test (not just the `test` lifecycle task).
- [ ] **No coverage thresholds yet.** Jacoco / coverage gates are deferred — SF-0.3 owns lint enforcement; coverage policy is a Phase-1 question once there is feature code to cover.
- [ ] **CI runs lint + build + unit tests** end-to-end on every push. Verified by pushing the SF-0.1 branch and seeing a green run.
- [ ] **`verification-checklist`** sweep: build / lint / unit tests / privacy & permissions section / no model weights / no telemetry SDKs / no `INTERNET`. The Accessibility / FSM / Real-Redmi-15 sections in the checklist are explicitly N/A for this SF and that should be recorded in the brief sign-off.

## Architect's notes & decisions

These are the load-bearing build-system decisions the architect reviewed for this
SF. Each note is referenced from the Scope / Android Specification / Testing
Requirements sections above. **All of them must be settled by the time
`/implement-feature US-001` writes `app/build.gradle.kts`** — there is no
"figure it out as you go" path that doesn't double the implementation time.

**A1. Kotlin 2.x has a separate Compose Compiler plugin.** Since Kotlin 2.0, the
Compose Compiler is no longer bundled with AGP — it is the standalone Kotlin
plugin `org.jetbrains.kotlin.plugin.compose`. Its version is the **same** as the
Kotlin version (the plugin tracks Kotlin 1:1; mismatched pairs crash at config
time). **Decision**: apply `org.jetbrains.kotlin.plugin.compose` on `:app`, use
`version.ref = "kotlin"` in the catalog (single source of truth), and **do not**
declare a separate `composeCompiler` version. The old `composeOptions {
kotlinCompilerExtensionVersion = "1.5.x" }` block in `android { … }` is
**forbidden** — it is the pre-Kotlin-2 mechanism and will be ignored at best,
conflict-with at worst.

**A2. Plugin application order is load-bearing.** Apply in this exact order
**both** in `gradle/libs.versions.toml`'s `[plugins]` table **and** in
`app/build.gradle.kts`'s `plugins { }` block:

```
com.android.application
org.jetbrains.kotlin.android
org.jetbrains.kotlin.plugin.compose
com.google.devtools.ksp
dagger.hilt.android.plugin
de.mannodermaus.android-junit5
org.jlleitschuh.gradle.ktlint
io.gitlab.arturbosch.detekt
```

Rationale: KSP must be applied **after** `kotlin.android` so it sees the Kotlin
source sets; Hilt's plugin must be applied **after** KSP so it registers its
generator with the correct processor (Hilt 2.5x autodetects KSP if KSP is
present); `android-junit5` must be applied **after** `kotlin.android` so it can
wire JUnit 5 onto the existing Kotlin test source set; ktlint/detekt last because
they only need the final compilation classpath. Apply in the wrong order and the
symptom is usually "no annotation processor found" or "no tests discovered" —
both diagnosed-as-something-else, both an hour-plus of poking.

**A3. KSP version must pair with Kotlin.** KSP is shipped as `<kotlin>-1.0.<n>`
(e.g. `2.1.0-1.0.29`, `2.1.10-1.0.30`). The `<kotlin>` half MUST match the
Kotlin version exactly, including patch. **Decision**: keep the `ksp` version in
the catalog as one paired ref (`"2.1.x-1.0.x"`) — never bump Kotlin without
bumping KSP, and vice versa. CI catches the mismatch but the error message is
opaque ("Symbol not found: …") and wastes an hour.

**A4. Hilt on KSP, not kapt.** Hilt 2.5x supports KSP first-class. **Decision**:
no `kapt` is applied anywhere in the project (now or ever, as far as US-001 is
concerned). Use `ksp(libs.hilt.compiler)` in `dependencies { }`. Kapt would add
~20% to compile time across the entire project lifetime and gives us nothing
back. Forward signal to SF-0.2: the `HiltTestRunner` and the first
`@Module`/`@InstallIn` files plug into the KSP processor that is already wired
here.

**A5. JUnit 5 on Android — the bear trap.** This is the single most-likely
trip-up. The standard Gradle-test convention `tasks.withType<Test>().configureEach
{ useJUnitPlatform() }` is **insufficient on Android** because AGP composes its
own `Test` subclass for `testDebugUnitTest` / `testReleaseUnitTest` and does not
honour the configureEach call for unit-test variants. **Decision**: apply the
**`de.mannodermaus.android-junit5`** Gradle plugin (catalog entry
`androidJunit5Plugin = "1.10.x"`, plugin id `de.mannodermaus.android-junit5`).
The plugin patches the AGP Test task to recognise the JUnit Platform Engine and
to discover `@org.junit.jupiter.api.Test` methods. Without it, `./gradlew
testDebugUnitTest` reports "no tests discovered" — silently, no error.
**Instrumented tests** (`connectedAndroidTest`) remain on **JUnit 4** with the
`AndroidJUnit4` runner. JUnit 5 on instrumented Android is not supported. This
is a hard, framework-level split, not a preference — see the table in *Testing
Requirements*.

**A6. Compose BOM.** Pin the latest stable BOM available at `/implement-feature`
time (the brief is dated 2026-05-13 — shortlist: `2025.05.xx`, `2025.06.xx`).
Use it for `compose-ui`, `compose-ui-graphics`, `compose-ui-tooling`,
`compose-ui-tooling-preview`, `compose-material3`, `compose-foundation`,
`compose-runtime`, `compose-ui-test-junit4`, `compose-ui-test-manifest`. **Do
not** pin any of those artifacts inline — the BOM is the single source of truth
for "what's a Compose-internally-consistent set". Leaving the catalog with
`"2025.xx.xx"` and a `TODO: pin to latest stable as of build date` is the
correct posture; the implementer picks on the day.

**A7. AGP 8.7+ is the floor.** AGP 8.7 is the lowest version that pairs cleanly
with Kotlin 2.1 + the standalone Compose Compiler plugin. **Decision**: pin AGP
to the latest stable 8.7+ available at implement-time. Pair Gradle 8.10+ with it
(see Scope). If the implementer can't get a clean build on 8.7, bump AGP
**before** trying anything else — version drift is the most common cause of
"works on my machine".

**A8. `buildFeatures { buildConfig = true }`.** In AGP 8+, `buildConfig`
generation is **off by default** — declaring `buildConfigField(…)` without
flipping the toggle silently no-ops. **Decision**: enable `buildConfig = true`
explicitly in `app/build.gradle.kts`. SF-0.8 will branch on `BuildConfig
.TELEMETRY_ENABLED`; we declare the field now so SF-0.8 is one PR (not "fix the
build first, then add the flag, then wire telemetry").

**A9. JDK 17 throughout.** `compileOptions.sourceCompatibility =
JavaVersion.VERSION_17` + `targetCompatibility = JavaVersion.VERSION_17`;
`kotlinOptions.jvmTarget = "17"`. Matches `setup-java@v4` JDK 17 in CI. Do not
bump to JDK 21 — Robolectric / Mockk / various AndroidX testing artifacts still
have rough edges on 21 at the time of writing.

**A10. ktlint / detekt — plugins wired, not enforced.** Pin
`org.jlleitschuh.gradle.ktlint` 12.1.x (K2-compatible) and
`io.gitlab.arturbosch.detekt` 1.23.x (K2 support is experimental — enable
explicitly when SF-0.3 tightens rules). **This SF's bar is "the plugins run to
completion".** No rule tuning, no fail-on-warning, no baseline file, no
No-Double-Padding custom rule, no pre-commit hook — those are SF-0.3. The
detekt config exported via `./gradlew detektGenerateConfig` is the **vanilla
default**; rule tuning is forbidden in this SF (to keep the SF-0.3 review small
and focused).

**A11. `MainActivity` is a placeholder, not a launcher.** Only `MAIN` +
`LAUNCHER` intent filter. **No `CATEGORY_HOME`, no `CATEGORY_DEFAULT`** — SF-1.1
adds those. Carrying `@AndroidEntryPoint` on `MainActivity` is fine and is
SF-0.2-friendly even with zero `@Inject` fields. The `CurroTheme { Surface {
Text(…) } }` body is intentionally a no-op so SF-0.4 owns the first "real"
theming decision.

**A12. Package layout** — the directory tree under
`app/src/main/java/com/curro/app/` follows `CLAUDE.md` → "Architecture"
*verbatim* (`domain/{model,catalog,repository,usecase}`, `data/{local,ml,voice,
notification,telephony,apps,contacts,repository}`, `handler/`, `assistant/`,
`service/`, `presentation/{theme,launcher,assistant,config,common,navigation}`,
`di/`, `util/`). Empty directories are preserved with `.gitkeep`. **No premature
classes** — only `CurroApp`, `MainActivity`, and the `CurroTheme` stub. Forward
signal: SF-0.2 fills `di/`, SF-0.4 fills `presentation/theme/`, SF-1.x onwards
fill the rest.

**A13. Test scaffolding — two trivial tests, two source sets.** One JVM test
(`SmokeTest.kt`, JUnit 5) + one instrumented test (`InstrumentedSmokeTest.kt`,
JUnit 4 + AndroidJUnit4). Both pass trivially. Both exist to prove the wiring,
not to cover behaviour. See *Testing Requirements* for the canonical table of
which framework goes where.

**A14. `BuildConfig.TELEMETRY_ENABLED`.** `boolean` flag (lowercase — the
Java-primitive form; AGP also accepts `Boolean` but `boolean` is the
documented signature). `debug` = `false`, `release` = `true`. **No code reads
this flag in US-001** — the flag's existence is the deliverable, not its
behaviour. SF-0.8 wires the SDKs (Firebase Crashlytics + Analytics + PostHog),
adds the `INTERNET` permission to the release manifest only, and adds the
`TelemetrySink` guardrail. None of that is in scope here.

**A15. Gradle wrapper version.** Gradle **8.10+** (the floor for AGP 8.7+). Run
`gradle wrapper --gradle-version 8.10.x` (or latest stable 8.x) once during
scaffolding; thereafter `./gradlew` is the only entry point. Do not commit a
pre-8.10 wrapper "to test"; CI's `setup-gradle@v4` uses whatever the wrapper
says.

**A16. Forbidden in this SF (verify with `grep`).** No `INTERNET` permission
anywhere; no runtime permissions in the manifest; no `google-services.json`
committed; no `*.task` / `*.tflite` / `*.bin` model weights; no Firebase /
PostHog / Coil / MediaPipe / LiteRT / Room / DataStore / Material-3-extras
dependency lines in `app/build.gradle.kts`. The catalog **reserves** entries
for them with `# Activated in SF-X.Y` trailing comments — those are inert until
SF-X.Y flips a single switch.

## Performance considerations (architect addendum)

The senior-UX `Performance Considerations` section above covers behavioural
perf (recomposition, dispatchers, list virtualization) — those concerns do not
apply to US-001 because there is no behaviour. The architect-relevant perf bar
for this SF is **build-system perf**:

- **Debug APK stays lean.** No R8 minification in debug (it slows the inner
  loop and gives no debugging value here); no Compose Compiler reports/metrics
  enabled (worth doing in Phase 1+ when there are composables to measure, not
  here); no model assets in the APK, no media assets beyond the default
  launcher icon.
- **No `kapt`** (A4) — saves ~20% compile time across the project's life.
- **No premature plugins.** Specifically: no Jacoco (coverage policy is
  deferred), no Kotlin-Serialization plugin (no JSON-over-HTTP yet — Curro has
  no backend), no Parcelize plugin (no parcelable models yet), no Room plugin
  (Room arrives in SF-7.1). Each plugin we don't apply is a faster cold build.
- **The Compose BOM is the only "version umbrella"** — keeps artifact
  resolution simple and avoids the cost of Gradle hunting through 30+
  individual Compose artifact versions.
- **Reserved catalog entries have zero build cost.** Verified by AC
  ("reserved entries do not leak into `:app:dependencies`"). They are TOML
  lines, not Gradle dependencies — Gradle never sees them until an `app/build
  .gradle.kts` line references the catalog alias.

## Execution plan (developer-facing checklist)

**Order of operations** when `/implement-feature US-001` runs. Each step is
verifiable in isolation; do not advance to step N+1 until step N is green.

1. **Gradle wrapper init**: `gradle wrapper --gradle-version 8.10.x` (or the
   latest stable 8.10+ at the time). Commit `gradlew`, `gradlew.bat`,
   `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.
   Verify: `./gradlew --version` reports JDK 17 + Gradle 8.10+.
2. **Version catalog**: write `gradle/libs.versions.toml` per the worked
   example above (active entries + reserved entries with `# Activated in
   SF-X.Y` comments).
3. **`settings.gradle.kts`**: `pluginManagement` (mavenCentral, gradlePluginPortal,
   google) + `dependencyResolutionManagement` (`repositoriesMode =
   FAIL_ON_PROJECT_REPOS`, mavenCentral + google). `rootProject.name = "Curro"`.
   `include(":app")`.
4. **Root `build.gradle.kts`**: alias-only plugins, all `apply false` (root
   declares them; `:app` applies them). Reserved plugins (`google-services`,
   `firebase-crashlytics-plugin`) commented out — SF-0.8 uncomments.
5. **`app/build.gradle.kts`**: the worked example above (plugins in the
   load-bearing order from A2; `android { … }` block; `dependencies { … }`
   block; the explicit `useJUnitPlatform()` belt-and-braces line). Verify:
   `./gradlew :app:tasks` lists `assembleDebug`, `testDebugUnitTest`,
   `connectedAndroidTest`, `ktlintCheck`, `detekt`.
6. **`AndroidManifest.xml`**: minimal — `CurroApp` + `MainActivity` only, the
   standard `MAIN`+`LAUNCHER` intent filter, no `CATEGORY_HOME`, no
   `<uses-permission>`, no `<uses-feature>`, no `<service>`, no `<receiver>`.
7. **The three Kotlin files**:
   - `app/src/main/java/com/curro/app/CurroApp.kt` — `@HiltAndroidApp class CurroApp : Application()`.
   - `app/src/main/java/com/curro/app/MainActivity.kt` — `@AndroidEntryPoint class MainActivity : ComponentActivity()` with `enableEdgeToEdge()` + `setContent { CurroTheme { Surface(Modifier.fillMaxSize()) { Text(stringResource(R.string.app_name)) } } }`.
   - `app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt` — `@Composable fun CurroTheme(content: @Composable () -> Unit) = MaterialTheme(content = content)` with a KDoc "Stub — full senior-first theme arrives in SF-0.4."
8. **Resources**: `res/values/strings.xml` (`<string name="app_name">Curro</string>`), `res/values/themes.xml` (`Theme.Curro` = thin `Theme.Material3.DayNight.NoActionBar`), default adaptive `mipmap-*` icons.
9. **Package skeleton** (`.gitkeep`s): every directory listed in A12 exists, every empty one carries a `.gitkeep`.
10. **The two tests**:
    - `app/src/test/java/com/curro/app/SmokeTest.kt` — JUnit 5 (`@org.junit.jupiter.api.Test`).
    - `app/src/androidTest/java/com/curro/app/InstrumentedSmokeTest.kt` — JUnit 4 + `@RunWith(AndroidJUnit4::class)` + `ActivityScenario.launch(MainActivity::class.java)`.
11. **Lint config files**: `app/detekt.yml` (defaults), `.editorconfig` (ktlint defaults).
12. **Verify three commands run green** (in this order — earlier ones produce
    artifacts the later ones depend on):
    1. `./gradlew assembleDebug` — produces `app/build/outputs/apk/debug/app-debug.apk`.
    2. `./gradlew ktlintCheck detekt` — finishes without crashing.
    3. `./gradlew testDebugUnitTest` — discovers and runs `SmokeTest`.
    4. (Optional, local only) `./gradlew connectedAndroidTest` — discovers and runs `InstrumentedSmokeTest` on a connected device.
13. **Tick the brief's AC checklist**; the `verification-checklist` skill's privacy/permissions pass; open the PR with `/generate-mr-description`.

**Hand-offs this brief triggers (none of them are this SF)**:
- SF-0.2 (`android-developer` + `android-qa-specialist`) → the first `@Module`s, the `HiltTestRunner` class, a Hilt-injected instrumented smoke test.
- SF-0.3 (`android-developer`) → ktlint/detekt rule tuning, the baseline file, the No-Double-Padding rule.
- SF-0.4 (`android-ui-designer` + `material-design` + `brand-design` + `compose-patterns`) → real `CurroTheme` / `CurroColorScheme` / `CurroTypography` / `CurroShapes` / `CurroSpacing`.
- SF-0.8 (`android-developer` + `kotlin-reviewer`) → Firebase + PostHog wiring, the `INTERNET` permission gated to release, the `TelemetrySink` guardrail.

## Implementation Notes

**Order of operations** when `/implement-feature US-001` runs:

The detailed step-by-step is in *Execution plan (developer-facing checklist)*
above. The short version: wrapper → catalog → `settings.gradle.kts` → root
`build.gradle.kts` → `app/build.gradle.kts` → manifest → 3 Kotlin files → 8
package skeleton → 2 tests → lint configs → `assembleDebug` / `ktlintCheck
detekt` / `testDebugUnitTest` all green → tick AC → open PR.

**Owner split.** PM (Fran) owns Metadata / Summary / Scope / Acceptance Criteria /
Senior-UX & Copy / Design Notes. **Architect (`android-architect`)** reviewed
the brief, enriched the Android Specification section (build-system specifics,
plugin-order rationale, JUnit-5-on-Android wiring), authored *Architect's notes
& decisions* (A1–A16), *Performance considerations (architect addendum)*,
*Execution plan (developer-facing checklist)*, and tightened the *Testing
Requirements* table. No Clean-Architecture design decision lands in US-001 — the
architect's role here is exclusively build-system hygiene.
`android-developer` implements; `android-qa-specialist` confirms the test source
sets work end-to-end; `kotlin-reviewer` reads the resulting Gradle files for
catalog hygiene and plugin-order correctness.

**Spec ambiguity noted** (no resolution required for this SF). `docs/curro-spec-v1.0.md`
§14 says "Min SDK: Android 12 (API 31)" and "Target SDK: Android 14 (API 34) or
superior". `CLAUDE.md`'s Quick Reference says `targetSdk = 35`. The master-plan
says `compileSdk = 35`. **We follow `CLAUDE.md` / master-plan (35)** because the
spec already says "o superior" — there is no contradiction, just an out-of-date
example value. **No spec bump required** for this SF; a coordinated v1.1 bump
(spec §5 "8/7 funciones", spec §12 "telemetry kept", spec §14 model-delivery
decision + the resolved 35-target) is queued for end-of-Phase-0 per the master
plan's "Cross-cutting work" section.

**Cross-references for the implementer**: `function-catalog` (no impact),
`voice-interaction` (no impact), `platform-integrations` (no impact),
`launcher-app` (impact deferred to SF-1.1 — do not add `CATEGORY_HOME`),
`launcher-ui` (no impact), `accessibility-patterns` (no impact — UI is throwaway),
`material-design` (Material 3 BOM only, defaults), `compose-patterns` (single
`setContent` call, no state, no ViewModel — patterns land in later SFs),
`on-device-llm` (catalog reservation only), `local-data` (catalog reservation
only), `brand-design` (do not read brand tokens — explicit stub), `spec-template`
(this document follows it), `git-workflow` (commit scope = `ci` for any
infrastructure tweaks, `theme` for the `CurroTheme` stub if split out — but US-001
ships as one composite commit by the master-plan workflow; see PRD commit at the
top of this branch).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-13 | Fran (Claude `android-product-analyst`) | Initial draft — generated from master-plan SF-0.1 + `CLAUDE.md` Architecture |
| 2026-05-13 | Claude `android-architect` | Architecture review: Kotlin 2.x Compose Compiler plugin (A1), plugin order (A2), JUnit 5 wiring via `android-junit5` plugin (A5), BuildConfig toggle rationale (A8), Gradle 8.10+ floor (A15). Added Architect's notes A1–A16, Performance considerations addendum, Execution plan checklist. Tightened Testing Requirements with the JVM-vs-instrumented framework table. |
