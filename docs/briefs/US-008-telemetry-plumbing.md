# US-008 — Telemetry plumbing (Firebase + PostHog), `INTERNET` gated to release, PII guardrail

> Implementation brief for **SF-0.8** (`docs/master-plan.md` → Phase 0 → §8).
> US-007 finished the navigation shell (`CurroNavHost` + `MainActivity`
> launcher Activity, placeholders for launcher home + config menu). US-008
> closes Phase 0 by landing the telemetry stack — Firebase Crashlytics +
> Analytics + PostHog — with the **non-negotiable** privacy contract baked
> into the build: the `INTERNET` permission lives in a `src/release/`
> manifest overlay only, every event goes through a `TelemetryGuardrail` that
> forbids transcripts / message bodies / contact names / phone numbers /
> emails / a blocklist of keys, and a unit-test fixture **fails CI** the
> moment that contract is broken. This also bumps `docs/curro-spec-v1.0.md`
> from v1.0 → v1.1, retroactively legitimising the deviation that
> `CLAUDE.md` → "Privacy & telemetry" has been carrying as a footnote.
>
> **Architect involvement: REQUIRED.** Eight load-bearing decisions need
> resolution before development:
>
> 1. **Q1 — Telemetry-gating strategy** (plugin-level / runtime / build-variant — different trade-offs on APK size, build complexity, and the strength of the "debug never touches the network" guarantee).
> 2. **Q2 — Defensive `src/debug/AndroidManifest.xml`** (explicitly removes `INTERNET`) — needed or paranoid?
> 3. **Q3 — Debug build without a real `google-services.json`** (stub / opt-in plugin / skip in debug entirely — depends on Q1).
> 4. **Q4 — `TelemetryGuardrail` shape** (heuristic / strict key whitelist / both — safer vs more flexible).
> 5. **Q5 — Hilt module shape** (single module with `BuildConfig` runtime branch vs separate `debug/` + `release/` source-set modules).
> 6. **Q6 — PostHog API key supply** (release `buildConfigField` from `local.properties` vs CI env var injection vs both — the Firebase project lives in `google-services.json` separately).
> 7. **Q7 — Spec v1.1 bump in this commit or its own commit?** (PM recommends one commit; the bump *is* this SF's intent.)
> 8. **Q8 — anything else the architect finds** when reading master-plan + `CLAUDE.md` → "Privacy & telemetry" + `api-integration` (parked) and applying it to this brief.
>
> Each of Q1–Q5 is mechanical to implement and **hard to reverse** once
> Phase 1+ features start `@Inject`-ing `TelemetrySink` — the call sites
> compile against `TelemetrySink`, but the **shape** of debug-vs-release
> determines what those call sites mean for privacy. PM precedent: the
> Q1–Q7 architect pass on US-002 / US-004 saved real refactoring
> downstream; the propagation surface here is comparable. **The architect
> resolves Q1–Q8 below; A1–An follow in the *Architect's notes &
> decisions* appendix.**

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Telemetry plumbing (Firebase Crashlytics + Analytics + PostHog), `INTERNET` gated to release, PII guardrail, spec §12 → v1.1 |
| **US ID** | US-008 |
| **SF ID** | SF-0.8 (master-plan) |
| **Phase** | 0 — Project foundation (final SF) |
| **Status** | In Progress |
| **Created** | 2026-05-14 |
| **Modified** | 2026-05-14 |
| **PM Owner** | Fran (Claude `android-product-analyst`) |
| **Architect** | **Pending — Q1–Q8 require resolution.** Recommended pre-development pass ≈ 45 min. |

## Summary

Land the telemetry stack — Firebase Crashlytics + Firebase Analytics + PostHog
— end-to-end **without emitting a single event from any feature**. This SF is
plumbing: it installs the pipes, locks the privacy contract that runs through
them, and bumps the spec to legitimise the deviation. The first SF that *emits*
events lands later (Phase 3 — FunctionGemma latency metrics; Phase 2 — STT
failures; Phase 7 — the failed-commands log forwarding toggle).

**Four mechanical artefacts**, in roughly the order the developer touches them:

1. **The version catalog activates** five reserved entries (`firebase-bom`,
   `firebase-crashlytics`, `firebase-analytics`, `posthog-android`) and adds two
   new Gradle plugin entries (`google-services`, `firebase-crashlytics-plugin`).
   `app/build.gradle.kts` applies the plugins and declares the dependencies
   **per the architect's Q1 resolution** (release-only `releaseImplementation` /
   plain `implementation` with runtime gating / a hybrid).

2. **The manifest splits**. `app/src/main/AndroidManifest.xml` stays
   `INTERNET`-less (existing state — confirmed via Read). A new
   `app/src/release/AndroidManifest.xml` overlay declares **only**
   `<uses-permission android:name="android.permission.INTERNET" />` plus the
   surrounding `<manifest>` tag — AGP merges the rest. The privacy-boundary
   rationale lives as a top-of-file comment. (Q2 may add a defensive
   `app/src/debug/AndroidManifest.xml` that explicitly *removes* `INTERNET`;
   architect's call.)

3. **The Kotlin code** lands in three layers:
   - `domain/repository/TelemetrySink.kt` — the abstract interface every
     feature `@Inject`s. Three methods: `event`, `setUserProperty`, `logCrash`.
     Every call routes through `TelemetryGuardrail` before reaching any SDK.
   - `data/telemetry/` — `TelemetryGuardrail.kt` (the PII validator),
     `FirebaseAndPostHogSink.kt` (the release impl), `NoopTelemetrySink.kt`
     (the debug impl — Logcat-only, no SDK calls), `TelemetryInitializer.kt`
     (called from `CurroApp.onCreate()`, gated on `BuildConfig.TELEMETRY_ENABLED`).
   - `di/TelemetryModule.kt` — Hilt binding **per the architect's Q5 resolution**
     (runtime branch in a single module vs separate source-set modules).

4. **The test** — `app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailTest.kt`
   pins a fixture suite of *forbidden* example calls (full names, phone numbers,
   emails, message-body-shaped strings, forbidden keys) and *allowed* example
   calls (action enums, error codes, model identifiers, latency milliseconds,
   bool flags). It runs in `./gradlew testDebugUnitTest` — the existing CI step
   `Run unit tests` picks it up automatically, no workflow edit needed. The
   test is the **load-bearing guarantee**: a forbidden property breaks CI on
   the commit that introduces it, not in a privacy review six months later.

**And the spec moves to v1.1**: `docs/curro-spec-v1.0.md` ships a §12 revision
that documents the relaxation in writing — crash + product analytics ARE kept
off-device via Firebase + PostHog under `TELEMETRY_ENABLED` (release-only),
gated by `TelemetryGuardrail`; the on-device promise survives untouched for
audio, transcripts, message content, contacts, aliases, command history.

This SF has **no user-visible value** for Fran's father today. The
user-visible payoff lands later — when a crash on his Redmi 15 produces a
Crashlytics report Fran can act on without ever seeing what his father said,
or when a "FunctionGemma latency p95 = 720 ms" PostHog metric tells the team
the warm-keeping target is slipping. Phase 0's job is to make those future
moments possible without compromising the privacy contract.

Spec ref: `docs/curro-spec-v1.0.md` §12 (the v1.0 "nothing leaves the device"
claim that v1.1 explicitly relaxes — this brief is the relaxation's
implementation and the spec bump's source of truth). Master-plan ref: SF-0.8.
`CLAUDE.md` ref: "Privacy & telemetry" (the policy this brief mechanises in
code).

## Scope

### In Scope

- **Version-catalog activation** (`gradle/libs.versions.toml`):
  - The five entries currently marked `# Activated in SF-0.8` lose their
    "reserved" comment: `firebaseBom = "33.7.0"` (verify it's the current
    stable as of 2026-05-14 — the architect may bump in Q8 if a newer LTS
    is out), `firebase-bom`, `firebase-crashlytics`, `firebase-analytics`,
    `posthog-android = "3.8.0"` (same verify-and-maybe-bump policy).
  - Two **new** version pins under `[versions]`: `googleServicesPlugin`
    (Gradle plugin) and `firebaseCrashlyticsPlugin` (Gradle plugin). The
    versions currently commented at L124–L125 of `libs.versions.toml`
    (`google-services = 4.4.2`, `firebase-crashlytics-plugin = 3.0.2`)
    are uncommented and promoted to real entries; the developer
    verifies "current stable as of 2026-05-14" and bumps if needed (with
    the architect's blessing in Q8).
  - The two `[plugins]` entries — `google-services` and
    `firebase-crashlytics-plugin` — uncomment and reference the version
    pins above.

- **`app/build.gradle.kts`** (the architect's Q1 resolution shapes this
  whole section):
  - Apply the two Firebase Gradle plugins. **Per Q1's resolution**, this
    may be `alias(libs.plugins.google-services)` + `alias(libs.plugins.firebase-crashlytics-plugin)`
    in the top-level `plugins { }` block (always-applied; runtime-gated), OR
    conditionally applied only for the release variant (build-variant-gated),
    OR a different shape the architect prefers.
  - Declare the SDK dependencies. **Per Q1's resolution**:
    - Option A (release-only): `releaseImplementation(platform(libs.firebase.bom))` +
      `releaseImplementation(libs.firebase.crashlytics)` +
      `releaseImplementation(libs.firebase.analytics)` +
      `releaseImplementation(libs.posthog.android)`. Strongest "debug never
      touches the network" guarantee; doubles the APK contract surface
      slightly (debug build has no SDK classes — `data/telemetry/`
      release-only references must be source-set-split).
    - Option B (always-present, runtime-gated): `implementation(...)` for
      all four; `TelemetryInitializer` checks `BuildConfig.TELEMETRY_ENABLED`
      and no-ops otherwise. Simpler build; weaker guarantee (the SDK
      classes are linked even in debug, just dormant).
    - Option C (hybrid — e.g. Firebase via `releaseImplementation`, PostHog
      via `implementation`): possible but the architect must justify why.
  - The existing `TELEMETRY_ENABLED` BuildConfig fields (`false` in
    debug, `true` in release — already declared per US-001) stay as-is;
    `TelemetryInitializer.initialize()` reads them.
  - **If Q6 resolves "PostHog API key via `buildConfigField`"**: a
    `buildConfigField("String", "POSTHOG_API_KEY", ...)` is added to the
    `release { }` block (and an empty string `""` to `debug { }`),
    reading from `localProps.getProperty("POSTHOG_API_KEY")` exactly the
    way `KEYSTORE_PATH` already does (precedent at L46–L55).

- **`app/src/main/AndroidManifest.xml`**:
  - The existing comment block at L4–L15 is updated. The bullet
    `INTERNET → SF-0.8 (release manifest only, for telemetry)` is rewritten
    to: `INTERNET → release manifest only (US-008) — see app/src/release/AndroidManifest.xml`.
  - **No** `<uses-permission android:name="android.permission.INTERNET" />`
    line is added. The file's permission surface stays empty (confirmed via
    Read of L1–L40).
  - No other change.

- **NEW: `app/src/release/AndroidManifest.xml`**:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!--
      Release-variant manifest overlay (AGP merges this onto src/main).
      Declares INTERNET — required by Firebase Crashlytics / Analytics
      and PostHog. Debug builds never see this file and therefore never
      see the INTERNET permission, per the privacy boundary
      (docs/curro-spec-v1.0.md §12 v1.1: crash + product analytics may
      leave the device under TELEMETRY_ENABLED + TelemetryGuardrail;
      audio, transcripts, message content, contacts, aliases, and the
      failed-commands log do NOT leave the device).
  -->
  <manifest xmlns:android="http://schemas.android.com/apk/res/android">
      <uses-permission android:name="android.permission.INTERNET" />
  </manifest>
  ```
  No `<application>` tag (AGP merges it from main); no
  `<uses-permission>` beyond `INTERNET` (no `ACCESS_NETWORK_STATE` —
  Crashlytics + Analytics + PostHog do not need it on modern Android, and
  US-008 deliberately keeps the permission surface to the minimum that
  makes telemetry work; the architect may overrule in Q8 if measurement
  shows one of the SDKs misbehaves without it).

- **OPTIONAL (Q2): `app/src/debug/AndroidManifest.xml`** — a defensive
  overlay that uses
  `<uses-permission android:name="android.permission.INTERNET"
   tools:node="remove" />` to ensure no transitive library (Compose
  tooling, Robolectric resources, anything) accidentally drags INTERNET
  in. The architect resolves Q2 — it's belt-and-braces vs paranoid; the
  CI verification step (the `aapt dump permissions` AC above) will catch
  the leak if it happens, but the overlay catches it at the source.

- **`app/src/main/java/com/curro/app/domain/repository/TelemetrySink.kt`**:
  ```kotlin
  package com.curro.app.domain.repository

  /**
   * The privacy-safe telemetry boundary for Curro.
   *
   * Every feature that wants to record a crash, an event, or a user
   * property does so through this interface. Implementations route
   * every call through [com.curro.app.data.telemetry.TelemetryGuardrail]
   * before reaching any SDK. In debug builds the binding is
   * [com.curro.app.data.telemetry.NoopTelemetrySink] — calls are
   * logged to Logcat for developer visibility but never leave the
   * device. In release builds the binding is
   * [com.curro.app.data.telemetry.FirebaseAndPostHogSink] — calls
   * (after the guardrail) reach Firebase Crashlytics / Analytics and
   * PostHog over the network.
   *
   * The guardrail forbids any event name or property value that
   * contains a transcript, a message body, a contact name, a phone
   * number, an email, or a forbidden key (transcript, message, body,
   * content, contact_name, phone, phone_number, name, alias, address).
   * See the TelemetryGuardrailTest fixture in src/test/ for the
   * canonical allowed / forbidden examples — the test fails CI on any
   * forbidden call.
   *
   * See docs/curro-spec-v1.0.md §12 (v1.1) and CLAUDE.md → Privacy &
   * telemetry for the privacy contract.
   */
  interface TelemetrySink {
      /** Record a discrete event. [name] is a stable identifier; [props] are safe properties. */
      fun event(name: String, props: Map<String, Any> = emptyMap())

      /** Set a user-scoped property. Never PII. [value] = null clears the property. */
      fun setUserProperty(key: String, value: String?)

      /** Record a Throwable. [fatal] = true marks it as a crash; false marks it as a non-fatal. */
      fun logCrash(throwable: Throwable, fatal: Boolean = false)
  }
  ```
  Lives in `domain/repository/` because every feature consumes it as a
  pure abstraction — no Android imports, no SDK leakage. Implementations
  live in `data/telemetry/`.

- **`app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt`**:
  the PII validator. The shape is the architect's Q4 resolution; the
  PM's proposed sketch (refinable):
  ```kotlin
  package com.curro.app.data.telemetry

  /**
   * The privacy guardrail every telemetry call routes through. Forbids
   * any event name or property that could carry PII the spec §12 (v1.1)
   * privacy promise guarantees stays on-device.
   *
   * Two layers of defence (see Q4):
   *  1. KEY blocklist — these property keys are never allowed:
   *     transcript, message, body, content, contact_name,
   *     phone, phone_number, name, alias, address, utterance, query.
   *  2. VALUE heuristic — any String value matching:
   *     - contains '@' (email-ish)
   *     - matches /^\+?\d[\d ()-]{6,}$/ (phone-ish)
   *     - longer than 32 characters (transcript-ish; loose proxy)
   *     - matches /\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+ [A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\b/
   *       (two-capital-words; full-name-ish)
   *     is rejected.
   *
   * On rejection, [check] returns [GuardrailResult.Reject] with a
   * developer-facing reason. NoopTelemetrySink logs the rejection;
   * FirebaseAndPostHogSink drops the event silently (a leaked event is
   * worse than a missed one) and ALSO logs to Logcat so the developer
   * sees it during release-debug.
   *
   * The architect resolves Q4: heuristic-only (most flexible, may
   * false-positive on legitimate values) vs whitelist-only (safest,
   * needs an explicit allowed-keys list maintained alongside every
   * new feature) vs both (belt-and-braces — the proposed default).
   */
  object TelemetryGuardrail {
      fun check(name: String, props: Map<String, Any>): GuardrailResult { /* … */ }
      fun check(userPropertyKey: String, value: String?): GuardrailResult { /* … */ }
  }

  sealed interface GuardrailResult {
      data object Allow : GuardrailResult
      data class Reject(val reason: String) : GuardrailResult
  }
  ```
  The exact regexes and the heuristic/whitelist mix are the architect's
  call; the test fixture below pins the **behaviour** (which examples
  pass, which fail), the architect picks the **mechanism**.

- **`app/src/main/java/com/curro/app/data/telemetry/FirebaseAndPostHogSink.kt`**:
  the release-bound implementation. Wraps `FirebaseCrashlytics.getInstance()`
  and `PostHog.capture(...)` per the Q1 strategy. Every call routes through
  `TelemetryGuardrail.check(...)` first; a `Reject` result drops the
  event silently and logs the rejection to Logcat at `Log.w` with the
  developer-facing reason. Constructor `@Inject`-takes nothing the runtime
  doesn't already have (the SDKs are singletons after `TelemetryInitializer`).

- **`app/src/main/java/com/curro/app/data/telemetry/NoopTelemetrySink.kt`**:
  the debug-bound implementation. Every call **still routes through
  `TelemetryGuardrail`** (so the developer hears about violations during
  local development, even when no SDK is in play); a passing call logs to
  `Log.d("CurroTelemetry", "event(…)")` — visible in `adb logcat` so the
  developer can see exactly what shape a feature's telemetry call has,
  without any network call.

- **`app/src/main/java/com/curro/app/data/telemetry/TelemetryInitializer.kt`**:
  ```kotlin
  package com.curro.app.data.telemetry

  import android.content.Context
  import com.curro.app.BuildConfig
  import dagger.hilt.android.qualifiers.ApplicationContext
  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class TelemetryInitializer @Inject constructor(
      @ApplicationContext private val context: Context,
  ) {
      /**
       * Called from CurroApp.onCreate(). No-ops in debug
       * (BuildConfig.TELEMETRY_ENABLED == false); in release, initialises
       * FirebaseApp + PostHog. Does NOT emit any telemetry — that's the
       * downstream consumer SFs' job.
       */
      fun initialize() {
          if (!BuildConfig.TELEMETRY_ENABLED) return
          // FirebaseApp.initializeApp(context) — the google-services plugin
          //   handles this automatically as long as app/google-services.json
          //   is present (see Q3 resolution).
          // PostHog.setup(context, /* posthogApiKey per Q6 resolution */) …
      }
  }
  ```
  Wired into `CurroApp.onCreate()` via:
  ```kotlin
  @HiltAndroidApp
  class CurroApp : Application() {
      @Inject lateinit var telemetryInitializer: TelemetryInitializer
      override fun onCreate() {
          super.onCreate()
          telemetryInitializer.initialize()
      }
  }
  ```
  (US-008 is the first SF since US-001 allowed to touch `CurroApp` —
  the precedent is the US-007 lift of the `MainActivity` US-001
  invariant: US-001 froze these files because there was nothing to plug
  in; once there is, the freeze ends.)

- **`app/src/main/java/com/curro/app/di/TelemetryModule.kt`**: Hilt
  binding **per the architect's Q5 resolution**:
  - Option A (single module, runtime branch):
    ```kotlin
    @Module
    @InstallIn(SingletonComponent::class)
    object TelemetryModule {
        @Provides @Singleton
        fun provideTelemetrySink(
            firebaseAndPostHog: Lazy<FirebaseAndPostHogSink>,
            noop: NoopTelemetrySink,
        ): TelemetrySink =
            if (BuildConfig.TELEMETRY_ENABLED) firebaseAndPostHog.get() else noop
    }
    ```
  - Option B (separate source-set modules — `src/debug/java/com/curro/app/di/TelemetryModule.kt`
    binds `NoopTelemetrySink`; `src/release/java/com/curro/app/di/TelemetryModule.kt`
    binds `FirebaseAndPostHogSink` — Hilt sees exactly one at compile time).
  Option B is the cleaner "debug never even references the release impl"
  shape and pairs naturally with Q1's Option A (release-only deps);
  Option A is the simpler "one source of truth" shape. The architect
  picks; PM leans Option B for the strongest privacy guarantee
  (a release impl that doesn't compile into the debug APK can't leak).

- **`app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailTest.kt`**:
  the load-bearing CI guardrail test. Two fixture suites:

  **Forbidden examples — every one must REJECT**:
  ```kotlin
  // Full name (two capital words)
  guardrail.check("call_started", mapOf("recipient" to "María García"))  // REJECT
  guardrail.check("crash", mapOf("by" to "Pepe Martínez"))               // REJECT

  // Phone number
  guardrail.check("called", mapOf("number" to "+34 600 123 456"))        // REJECT
  guardrail.check("called", mapOf("number" to "600123456"))              // REJECT (digits-only ≥ 7)

  // Email
  guardrail.check("crash", mapOf("contact" to "fran@example.com"))       // REJECT

  // Transcript-shaped (long string)
  guardrail.check("stt_done", mapOf(
      "text" to "Te espero a las siete en la puerta del médico"))        // REJECT (> 32 chars)
  guardrail.check("stt_done", mapOf(
      "transcript" to "qué hora es"))                                     // REJECT (forbidden KEY)

  // Forbidden keys (even with safe values)
  guardrail.check("event", mapOf("message" to "ok"))                     // REJECT (key)
  guardrail.check("event", mapOf("body" to "ok"))                        // REJECT (key)
  guardrail.check("event", mapOf("content" to "ok"))                     // REJECT (key)
  guardrail.check("event", mapOf("contact_name" to "ok"))                // REJECT (key)
  guardrail.check("event", mapOf("phone" to "ok"))                       // REJECT (key)
  guardrail.check("event", mapOf("phone_number" to "ok"))                // REJECT (key)
  guardrail.check("event", mapOf("name" to "ok"))                        // REJECT (key)
  guardrail.check("event", mapOf("alias" to "ok"))                       // REJECT (key)
  guardrail.check("event", mapOf("address" to "ok"))                     // REJECT (key)
  guardrail.check("event", mapOf("utterance" to "ok"))                   // REJECT (key)
  guardrail.check("event", mapOf("query" to "ok"))                       // REJECT (key)

  // User property — never PII
  guardrail.check("user_email", "fran@example.com")                      // REJECT
  guardrail.check("user_name", "Fran Sánchez")                           // REJECT
  ```

  **Allowed examples — every one must ALLOW**:
  ```kotlin
  // Function-call latency
  guardrail.check("function_called", mapOf(
      "action" to "tell_time",
      "confidence_bucket" to "high",
      "latency_ms" to 380,
      "from_warm" to true))                                              // ALLOW

  // STT error
  guardrail.check("stt_failed", mapOf("error_code" to "NO_MATCH"))       // ALLOW
  guardrail.check("stt_failed", mapOf("error_code" to "SPEECH_TIMEOUT")) // ALLOW

  // Model load
  guardrail.check("model_loaded", mapOf(
      "model" to "function_gemma_270m",
      "load_ms" to 1200,
      "cold_start" to true))                                             // ALLOW

  // Handler outcome (no contact info — just the function name + result kind)
  guardrail.check("handler_finished", mapOf(
      "function" to "call_contact",
      "outcome" to "needs_confirmation",
      "ambiguous" to true))                                              // ALLOW

  // Launcher metrics
  guardrail.check("launcher_set_default", mapOf("attempt" to 1))         // ALLOW

  // Confidence policy
  guardrail.check("confidence_below_threshold", mapOf(
      "function" to "open_app",
      "threshold" to "execute",
      "delta" to 0.07))                                                  // ALLOW

  // User property — safe scalar enums only
  guardrail.check("locale", "es_ES")                                     // ALLOW
  guardrail.check("device_variant", "redmi_15_8gb")                      // ALLOW
  guardrail.check("hyperos_version", "2")                                // ALLOW

  // Edge — empty props
  guardrail.check("app_open", emptyMap())                                // ALLOW
  ```

  The test runs under `./gradlew testDebugUnitTest` (JUnit 5, the
  project's standard). It is the contract every event-emitting SF later
  has to satisfy.

- **NEW: `docs/curro-spec-v1.0.md` bumped to v1.1**:
  - Header L4: `**Versión:** 1.0` → `**Versión:** 1.1`.
  - §12 rewritten — see *Spec §12 v1.1 — proposed rewrite* below.
  - New `## Historial de revisiones` section appended after §14, before
    the closing italic line. Two rows:
    - `v1.0 (May 2026) — Spec inicial. Decisiones cerradas para
      implementación de prototipo (§14).`
    - `v1.1 (May 2026) — §12 revisado: telemetría de fallos y producto
      (Firebase Crashlytics + Analytics + PostHog) admitida fuera del
      dispositivo bajo el flag de build TELEMETRY_ENABLED y el guardrail
      TelemetryGuardrail. Las garantías on-device para audio,
      transcripciones, contenido de mensajes, contactos, alias e
      historial de comandos permanecen intactas. Permiso INTERNET
      limitado a la variante release vía src/release/AndroidManifest.xml.
      Implementado por US-008 (SF-0.8).`

  **Whether the spec bump lives in this commit or its own commit is
  Q7** — the PM recommends in-commit (the bump *is* this SF's intent;
  splitting would force a coordinated two-commit dance). The architect
  may overrule if there's a downstream auditing reason.

### Out of Scope

- **No telemetry instrumentation of any feature.** The first event-emitting
  SF is later — Phase 2's SF-2.1/2.3 (STT failure metrics), Phase 3's
  SF-3.5/3.6 (FunctionGemma latency, cold-start counts), Phase 7's
  SF-7.x (the failed-commands log forwarding toggle that finally exposes
  user-controlled telemetry per spec §12 v1.1's "envíame los fallos"
  toggle). US-008 ships zero call sites of `telemetrySink.event` outside
  `data/telemetry/` itself.
- **No FCM (push) / no Firebase Auth / no Firebase Storage.** Spec §14
  closed: "no accounts, no push". Curro is single-user, single-device.
- **No PostHog feature-flag plumbing.** PostHog has a feature-flag SDK;
  US-008 wires only event capture. Feature flags are a future SF if ever.
- **No user-facing "send my failures to Fran" toggle.** That's the
  spec-§9 config-menu toggle that ships with Phase 8 / SF-8.x. US-008
  is the plumbing; the user-facing affordance is a separate Phase-8
  story.
- **No `INTERNET` anywhere outside `app/src/release/AndroidManifest.xml`.**
  Not in main; not in androidTest (Robolectric runs without network);
  not in any conditional gradle block. The AC verifies via
  `aapt dump permissions`.
- **No spec edits beyond §12 + the new revision-history row.** The
  master-plan flags two other deviations queued for v1.1 — the §5
  "8 vs 7 funciones" cosmetic (the header says 8, the list has 7) and a
  `targetSdk` cosmetic. **Those are separate items**, deliberately not
  folded in. The brief documents them as "queued, separate fixes" in
  *Implementation Notes → Coordinated v1.1 bump items NOT in this SF*.
  Folding them in here would dilute US-008's privacy focus and entangle
  unrelated decisions.
- **No CI workflow edit.** The existing `.github/workflows/ci.yml`
  steps — `Decode google-services.json` (already in place, no-ops if
  the secret isn't set), `Lint`, `Build debug`, `Run unit tests` — are
  sufficient. The new `TelemetryGuardrailTest` runs as part of
  `Run unit tests` automatically.
- **No `READ_PHONE_STATE` / `ANSWER_PHONE_CALLS` / any permission
  side-effect.** US-008 adds exactly one permission, in exactly one
  manifest (the release overlay): `INTERNET`. Nothing else.
- **No custom detekt rule banning `Log.e(*, *, Throwable)` outside
  `data/telemetry/`.** A future SF might want such a rule (every
  recoverable error routed through `TelemetrySink.logCrash` instead of
  `Log.e`), but that's a tooling SF, not a privacy SF.

## User Flows

US-008 has **no user-facing flow** for Fran's father (or for Fran in
the config menu — that lands with SF-8.x). The only flows are
developer-facing and CI-facing.

### Flow 1 — Developer adds a feature that emits telemetry (future)

1. The feature's ViewModel `@Inject`s `TelemetrySink` (`private val telemetry: TelemetrySink`).
2. On a relevant event (e.g. function-call completion), it calls
   `telemetry.event("function_called", mapOf("action" to "tell_time", "latency_ms" to 380))`.
3. **Debug build**: `NoopTelemetrySink` routes the call through
   `TelemetryGuardrail.check(...)`. If `Allow`, logs
   `Log.d("CurroTelemetry", "event(function_called, {action=tell_time, latency_ms=380})")`.
   If `Reject(reason)`, logs `Log.w` with the reason. **No network call.**
4. **Release build**: `FirebaseAndPostHogSink` routes the call through
   the same guardrail. If `Allow`, forwards to PostHog
   `PostHog.capture("function_called", mapOf(...))` and Firebase
   Analytics `firebaseAnalytics.logEvent("function_called", bundle)`.
   If `Reject(reason)`, drops the event and logs `Log.w` with the reason.
5. The developer's CI run includes `./gradlew testDebugUnitTest` which
   runs `TelemetryGuardrailTest`. If the developer accidentally wrote a
   call that violates the contract (e.g. logged a contact name as an
   event property), the test catches it **as long as they also added a
   test fixture for the new call** — the guardrail unit-tests the
   *rules*, not every call site.
   - This is the load-bearing weakness of "static fixture" telemetry
     review: the test catches violations of the rules but not the
     *coverage* of the rules. **Q4's whitelist option closes this**:
     if every event name + property key must be on an explicit allow
     list, a developer adding a new event has to update the list, which
     surfaces in code review. The architect resolves which model US-008
     ships.

### Flow 2 — CI catches a privacy regression

1. A developer (or, more likely, a future AI agent) adds
   `telemetry.event("called", mapOf("recipient_name" to call.contact.displayName))` to a handler.
2. They run `./gradlew testDebugUnitTest` locally. **If they updated the
   `TelemetryGuardrailTest` fixture with this call**, the test fails
   because `recipient_name` contains "María García" → two-capital-words
   heuristic rejects → `GuardrailResult.Reject(...)`.
3. **If they did NOT update the fixture** — the test passes locally and
   in CI. **But the runtime guardrail still rejects** at the moment the
   event would be emitted (in release; in debug, the rejection is
   logged but the event never reaches the SDK either way). The release
   build is privacy-safe; the developer just doesn't get a CI failure.
   This is the limitation Q4's whitelist mode closes.
4. **If Q4 resolves "whitelist mode"**: there's an `ALLOWED_EVENTS` /
   `ALLOWED_PROPS` registry the developer must update; the
   `TelemetryGuardrail` rejects every call whose `(name, prop_key)` pair
   isn't in the registry. Adding a new event requires a registry edit,
   which surfaces in code review.

### Flow 3 — Phase 8 (future) — Fran toggles "send my father's failures to me"

This is the only **user-controlled** telemetry path. Lands with SF-8.x,
not here. Documented for context only:
1. Fran opens the config menu (5 taps on the clock).
2. He sees a toggle "Enviar fallos del modelo a mí (logs anonimizados)".
3. He turns it on. `SettingsRepository.setFailureLogForwardingEnabled(true)`.
4. From that point, `FailedCommandLog` entries (the §7 / SF-7.x feature)
   are forwarded as a PostHog event `command_failed` with safe properties
   only — never the raw transcript, never the contact involved, never the
   message body. The `TelemetryGuardrail` enforces this from US-008
   onwards even before the toggle UI exists.

## Function-catalog Impact

**No catalog change.** US-008 doesn't touch `domain/catalog/`, doesn't
add or modify a function, doesn't touch the FunctionGemma prompt
context, doesn't touch the `function-catalog` skill. The first SF that
*emits* telemetry tied to catalog functions is Phase 3 — at which point
the event names (`function_called`, `confidence_below_threshold`,
`function_unknown`) are *about* the catalog without being *part of* it.

## FSM States Touched

**None.** US-008 doesn't touch `assistant/`, doesn't touch the
`AssistantStateMachine` (which doesn't exist yet — Phase 5 builds it),
doesn't add any state-transition trigger. `TelemetrySink` is FSM-blind:
the `event` / `logCrash` / `setUserProperty` methods say nothing about
which assistant state the caller is in. If a future SF wants to record
"how often does Curro enter `error_recovery` from `processing`?", it
emits `state_transition` events with `from` / `to` as enum strings —
the FSM doesn't change shape to accommodate telemetry.

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied | Where declared |
|---|---|---|---|---|
| `INTERNET` | Firebase Crashlytics + Analytics + PostHog all need it to send events | **Granted at install time** (it's a normal permission, not runtime). The user never sees a prompt. | If the user revokes via Settings → Apps → Curro → Permissions (Android 13+ allows revoking normal perms), Firebase + PostHog fail silently — the telemetry SDKs catch network failures internally. The app keeps working. | `app/src/release/AndroidManifest.xml` (build-variant overlay) — **NOT** `app/src/main/AndroidManifest.xml`; the debug APK has no `INTERNET` permission at all |

**No other permissions added or modified.** `RECORD_AUDIO`,
`READ_CONTACTS`, `CALL_PHONE`, `BIND_NOTIFICATION_LISTENER_SERVICE`,
`POST_NOTIFICATIONS`, `QUERY_ALL_PACKAGES` all remain unset (each is
introduced by the SF that needs it per spec §10 lazy-permission
discipline). No `CATEGORY_HOME` (SF-1.1's job).

**No runtime permission request flow** — `INTERNET` is a normal
permission granted at install; nothing for the user to consent to.

**System integrations**: none on the Android side that the brief owns.
The Firebase Crashlytics SDK installs its own
`UncaughtExceptionHandler`; PostHog wires its own `ActivityLifecycleCallbacks`.
Both are autonomous after `TelemetryInitializer.initialize()`. US-008
doesn't write any `Service` / `BroadcastReceiver` / `ContentProvider`.

**The `CLAUDE.md` permission table** at *Permissions* (the one in
*Architecture → Permissions*) is updated: the `INTERNET` row is added
with the per-build-variant note. This is a `CLAUDE.md` edit the
developer makes alongside the code; the brief calls it out so it
doesn't get missed.

## On-device-model Impact

**No model impact today.** US-008 doesn't load a model, doesn't change
FunctionGemma's prompt context (no new tokens competing on the 270M
model), doesn't reach for Gemma 3n. The MediaPipe LLM Inference API
isn't touched (its libs.versions.toml entry stays `# Activated in SF-3.1`).

**Downstream consumer flag**: the on-device models *will* be primary
telemetry targets later. Specifically:
- SF-3.5 (the warm-up foreground service) wants to record
  `model_warm_keepalive` ticks + `model_killed_by_system` events when
  HyperOS reaps the service.
- SF-3.6 (the decision smoke loop) wants `function_decided` events with
  `latency_ms`, `action_name`, `confidence_bucket` (`high` / `medium` /
  `low` — the bucket, never the raw float beyond two decimals).
- SF-9.x (Gemma 3n loading) wants `nlg_invocation` events with the same
  shape + `cold_start: bool`.

The `TelemetryGuardrail` allowlist needs to cover these event names
when the SFs land — the architect's Q4 resolution determines whether
that's an explicit registry update or just a fixture-test extension.

## Android Specification

### Screens and Composables

**None.** US-008 doesn't ship a screen, doesn't ship a composable,
doesn't touch `presentation/` at all. The config-menu Phase 8 "send my
failures to Fran" toggle that finally exposes telemetry to the user is
a separate SF (SF-8.x).

### ViewModels and State Management

**None.** US-008 is pure plumbing — no ViewModel, no `StateFlow`, no
sealed-interface UI state. The closest thing is `TelemetryInitializer`,
which is a `@Singleton` injected into `CurroApp`; it has no observable
state, no flow, no UI. It exposes one `initialize()` method and nothing
else.

### Navigation Routes

**None.** US-007 locked `CurroRoute.Launcher` + `CurroRoute.ConfigMenu`
as the only two routes in Phase 0. US-008 adds zero routes.

### Hilt Modules

**One new module — `app/src/main/java/com/curro/app/di/TelemetryModule.kt`** —
plus, **if Q5 resolves "separate source-set modules"**, the module is
split into `src/debug/.../di/TelemetryModule.kt` (binds
`NoopTelemetrySink`) and `src/release/.../di/TelemetryModule.kt`
(binds `FirebaseAndPostHogSink`), with no main-source-set version.

The runtime-branch shape (Q5 Option A):
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {
    @Provides @Singleton
    fun provideTelemetrySink(
        firebaseAndPostHog: Lazy<FirebaseAndPostHogSink>,
        noop: NoopTelemetrySink,
    ): TelemetrySink =
        if (BuildConfig.TELEMETRY_ENABLED) firebaseAndPostHog.get() else noop
}
```
Note `Lazy<FirebaseAndPostHogSink>` — important when Q1 lands as
runtime-gated (so the release sink isn't constructed in debug, even if
it's compiled in).

The source-set-split shape (Q5 Option B):
```kotlin
// src/debug/java/com/curro/app/di/TelemetryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds @Singleton
    abstract fun bindTelemetrySink(impl: NoopTelemetrySink): TelemetrySink
}

// src/release/java/com/curro/app/di/TelemetryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds @Singleton
    abstract fun bindTelemetrySink(impl: FirebaseAndPostHogSink): TelemetrySink
}
```
This pairs naturally with Q1 Option A (release-only deps) — the release
impl literally doesn't exist in the debug source set. The PM leans this
for the strongest "debug never even references the release impl"
guarantee.

No other DI module changes. `CoroutineModule` (US-002) and `AppModule`
(US-002, placeholder) stay byte-identical.

### Composables by Feature (checklist)

- [ ] N/A — US-008 ships zero composables.

### Material Design Components

- [ ] N/A — US-008 ships zero UI.

## Acceptance Criteria

(Mirrors the PRD entry; verbatim here so the brief stands alone.)

- [ ] `./gradlew assembleDebug` succeeds on a fresh clone (no
  `app/google-services.json` present — the build does not require it;
  the developer documents the mechanism per Q3) and produces an
  installable APK whose
  `aapt dump permissions app/build/outputs/apk/debug/app-debug.apk`
  output contains **zero** `INTERNET` permission lines
- [ ] `./gradlew assembleRelease` succeeds (with whatever the architect
  resolves for `google-services.json` and the PostHog API key supply
  per Q3 / Q6) and produces an APK whose `aapt dump permissions`
  output contains **exactly one** `android.permission.INTERNET` line
- [ ] `app/src/main/AndroidManifest.xml` contains **no**
  `<uses-permission android:name="android.permission.INTERNET" />`
  line; the existing inline comment that lists future permissions has
  its `INTERNET → SF-0.8 (release manifest only, for telemetry)` bullet
  updated to
  `INTERNET → release manifest only (US-008) — see app/src/release/AndroidManifest.xml`
- [ ] `app/src/release/AndroidManifest.xml` exists and is a build-variant
  overlay declaring **only**
  `<uses-permission android:name="android.permission.INTERNET" />` plus
  the surrounding `<manifest>` tag (AGP merges the rest from main); the
  file has a header comment block stating the privacy-boundary rationale
- [ ] `gradle/libs.versions.toml` activates the five reserved entries
  (`firebaseBom`, `firebase-bom`, `firebase-crashlytics`,
  `firebase-analytics`, `posthog-android`) by removing the
  `# Activated in SF-0.8` markers and adding the two Gradle-plugin
  entries (`google-services`, `firebase-crashlytics-plugin`) under
  `[plugins]`
- [ ] `app/build.gradle.kts` declares the two Firebase Gradle plugins
  (`alias(libs.plugins.google-services)`,
  `alias(libs.plugins.firebase-crashlytics-plugin)`) **per the gating
  strategy the architect resolves in Q1**; the SDK dependencies are
  declared **per the same resolved strategy** (release-only
  `releaseImplementation` vs always-present-but-runtime-gated
  `implementation`)
- [ ] `app/src/main/java/com/curro/app/domain/repository/TelemetrySink.kt`
  exists with three methods — `event(name, props)`,
  `setUserProperty(key, value)`, `logCrash(throwable, fatal)` — all of
  which route through `TelemetryGuardrail` before reaching any SDK
- [ ] `app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt`
  exists with `check(name, props)` + `check(userPropertyKey, value)`;
  the heuristic + key whitelist / blocklist combination is the one the
  architect resolves in Q4
- [ ] `app/src/main/java/com/curro/app/data/telemetry/FirebaseAndPostHogSink.kt`
  (release-bound impl) and
  `app/src/main/java/com/curro/app/data/telemetry/NoopTelemetrySink.kt`
  (debug-bound impl — Logcat only, no SDK calls) both exist; both route
  every call through `TelemetryGuardrail`
- [ ] `app/src/main/java/com/curro/app/data/telemetry/TelemetryInitializer.kt`
  exists and is called from `CurroApp.onCreate()` via
  `@Inject lateinit var telemetryInitializer: TelemetryInitializer` +
  `telemetryInitializer.initialize()`; the initializer checks
  `BuildConfig.TELEMETRY_ENABLED` before doing anything and is a no-op
  when the flag is `false`
- [ ] `app/src/main/java/com/curro/app/di/TelemetryModule.kt` exists
  (or splits into `src/debug/…/di/TelemetryModule.kt` +
  `src/release/…/di/TelemetryModule.kt` per Q5) and binds
  `TelemetrySink` to the right implementation
- [ ] `app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailTest.kt`
  runs in `./gradlew testDebugUnitTest` and (a) **fails** for every
  forbidden example in the brief's fixture (full names, phone numbers,
  emails, message-body-shaped strings, forbidden keys); (b) **passes**
  for every allowed example (action enums, error codes, model
  identifiers, latency milliseconds, bool flags); the test is wired
  so CI runs it as part of the existing `Run unit tests` step in
  `.github/workflows/ci.yml` — no CI workflow edit needed
- [ ] **No telemetry call is emitted from any production code yet** —
  `grep -rn 'telemetrySink\.event\|telemetrySink\.logCrash\|telemetrySink\.setUserProperty' app/src/main/java/com/curro/app/`
  returns zero matches outside `data/telemetry/` itself (and any
  internal smoke probe inside `TelemetryInitializer`)
- [ ] `docs/curro-spec-v1.0.md` is bumped to **v1.1** in the same
  commit (per Q7 resolution): the `**Versión:** 1.0` line at the top
  becomes `**Versión:** 1.1`; the document grows a new
  `## Historial de revisiones` section with two rows (v1.0 / v1.1 —
  text in *Spec §12 v1.1 — proposed rewrite* below); §12 itself is
  rewritten per the same section
- [ ] `./gradlew ktlintCheck detekt` is green; the new files in
  `data/telemetry/`, `di/`, and `domain/repository/` follow the
  existing ktlint/detekt posture (no widening of `MagicNumber`
  excludes; no `!!`; no hard-coded strings beyond the SDK
  initialisation strings + the `TelemetryGuardrail` forbidden-key
  constants — which the developer marks with `@Suppress` or moves to a
  `private val` if detekt flags them, with a one-line KDoc rationale)
- [ ] `app/google-services.json` is git-ignored (verified — `.gitignore`
  L52 already covers it; the brief flags this but no edit is needed)
- [ ] **`CLAUDE.md` permission table updated**: a new `INTERNET` row is
  added under *Architecture → Permissions* with the note
  "release manifest only (build-variant overlay); for telemetry (Firebase
  + PostHog); revocation degrades telemetry, app keeps working"
- [ ] **Q1–Q8 each have a `**Q# — Resolved: …**` block in the brief**
  (precedent: US-002 Q1–Q5, US-004 Q1–Q7)
- [ ] Verified manually: install the debug APK on the Pixel_10_Pro
  emulator, run `adb shell pm list permissions -d -g | grep -i curro`
  (or `adb shell dumpsys package com.curro.app | grep -i internet`),
  confirm no `INTERNET` line for the debug variant

## Design Notes

US-008 ships zero UI; no design surface to honour. The
`brand-design`-authoritative tokens, the senior-first dimension
contract (`Dimens.MinTapTarget = 96.dp`), the canonical COPY table —
all untouched. The Spanish copy for §12's v1.1 rewrite (in the spec
itself) follows Curro's *technical-Spanish* register (the spec, unlike
the COPY table, is internal documentation in Spanish, not Curro's
spoken voice) — see *Spec §12 v1.1 — proposed rewrite* below.

## Senior-UX & Copy

**None.** US-008 has no user-facing string. The only Spanish strings
land in the spec itself (§12 v1.1 rewrite, the revision-history rows)
— technical documentation, not Curro's voice.

The user-facing telemetry copy ("Enviar fallos del modelo a mí",
"Reset de aprendizaje", etc.) all lives with the Phase 8 config-menu
SFs that finally render the toggles. US-008 is invisible to
Fran's father and (until Phase 8) invisible to Fran.

## Performance Considerations

- **`TelemetryInitializer.initialize()` is called once from
  `CurroApp.onCreate()`.** In debug, it returns immediately
  (`!BuildConfig.TELEMETRY_ENABLED → return`). In release, it does the
  Firebase/PostHog setup — both SDKs do their heavy lifting on a
  background thread internally. **No work on the main thread beyond
  the SDK constructors** (verify with a one-shot Trace.beginSection
  during development).
- **The guardrail is hot-path.** Every event in the future will pass
  through `TelemetryGuardrail.check(...)`. The regex-based heuristic
  (Q4) needs to be cheap — pre-compile any `Regex` objects (object-level
  `val`s, not per-call instantiation). The forbidden-key check is an
  `in` against a small `Set<String>` (O(1)).
- **`FirebaseAndPostHogSink` does no work on the main thread for the
  `event` / `logCrash` paths** — both SDKs are async internally;
  `logEvent` returns immediately.
- **No coroutines, no `Dispatchers.IO` switch** needed in US-008's own
  code — the SDKs handle threading. If a future event-emitting SF
  wants to batch or debounce, that's its concern.
- **No `INTERNET` in debug** means the debug build has measurably less
  surface for accidental network use during development. This is the
  point.

## Testing Requirements

- [ ] **JVM unit tests** (`./gradlew testDebugUnitTest`, JUnit 5,
  Mockk if needed but probably not — `TelemetryGuardrail` is a pure
  function, no mocks needed):
  - `TelemetryGuardrailTest` — the load-bearing fixture suite. **Every
    forbidden example** in this brief's *In Scope* section must
    produce `GuardrailResult.Reject`. **Every allowed example** must
    produce `GuardrailResult.Allow`. Failure is a CI break.
  - Additional unit tests for edge cases: empty event name (reject?
    architect's Q8 call); event name itself containing PII-shaped text
    (the guardrail's name parameter check); null vs missing value in
    `setUserProperty`; `Map<String, Any>` values that aren't strings
    (an `Int 380` for `latency_ms` is safe; an `Object#toString` for a
    random class that resolves to PII is the risk — pin the contract).
- [ ] **JVM unit test for `NoopTelemetrySink`**:
  `NoopTelemetrySinkTest` — confirms (using Robolectric's `ShadowLog`)
  that an `Allow` call logs `Log.d` once; a `Reject` call logs `Log.w`
  once and nothing else; no SDK class is touched (verified via a
  `try { Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics") }`
  probe — if it's available, the build was set up wrong per Q1; if
  Q1 is Option B "always present", this test is dropped).
- [ ] **JVM unit test for `TelemetryInitializer`** (Robolectric):
  - Debug variant (`BuildConfig.TELEMETRY_ENABLED == false`) →
    `initialize()` does nothing (verify via mock `FirebaseApp` /
    `PostHog` — both should be untouched).
  - Release-flag scenario (force `TELEMETRY_ENABLED` via a test
    qualifier or per-test `BuildConfigOverride`) → both SDKs initialised
    exactly once.
- [ ] **`CurroAppTest`** (instrumented or Robolectric) — confirms
  `telemetryInitializer.initialize()` is called from `onCreate()`
  exactly once. Mocking pattern: `@BindValue` a fake `TelemetryInitializer`
  and assert.
- [ ] **No instrumented test required** for US-008's core artefacts —
  the manifest split is verified by the `aapt dump permissions` AC;
  the SDK setup is exercised by the developer once on a real release
  build (or a release-debug build with a real Firebase project).
- [ ] **CI verification step** (informational, no workflow edit
  needed): the existing `./gradlew testDebugUnitTest` step in
  `.github/workflows/ci.yml` will run `TelemetryGuardrailTest` and
  break on any failure. The developer commits the test to the same
  PR that activates the SDKs. If Q1 lands as Option A (release-only
  deps), the test runs against the debug variant only — verify
  `TelemetryGuardrail` compiles into the debug variant (it doesn't
  reference any SDK class — only `kotlin.text.Regex` + `kotlin.collections.Set`).
- [ ] **Manual verification on the real Redmi 15** (deferred — not a
  blocker for US-008's merge, but the developer notes the
  before-Phase-1 task): install the release APK, force a crash,
  verify the crash shows up in Firebase Crashlytics (Fran's Firebase
  project — the developer needs the project credentials set up
  per Q3). Verify a PostHog event appears in the PostHog dashboard.
  This is the "is the wiring actually live?" smoke test that JVM
  tests can't do.

## Implementation Notes

### Spec §12 v1.1 — proposed rewrite

This is the PM's proposed Spanish text for the §12 rewrite. The
architect may refine; the developer commits the final version.

```
## 12. Privacidad

Curro corre en el dispositivo. La promesa de privacidad sobre los datos
del usuario sigue siendo **nunca salen del dispositivo**:

- Audio grabado.
- Texto transcrito.
- Contenido de mensajes leídos.
- Lista de contactos y alias aprendidos.
- Historial de comandos.

**Excepción admitida en v1.1: telemetría técnica.** Para que Fran pueda
detectar fallos del prototipo sin observar a su padre en persona, la
app envía dos clases de datos fuera del dispositivo:

- **Informes de fallos** (Firebase Crashlytics): pila de excepciones no
  capturadas, versión de la app, modelo de dispositivo, versión de
  Android. Sin transcripciones, sin nombres de contactos, sin contenido
  de mensajes.
- **Eventos de producto anónimos** (Firebase Analytics + PostHog):
  contadores de uso por función (`tell_time`, `open_app`, etc.),
  latencias, códigos de error de STT/modelo, bucket de confianza
  (alto/medio/bajo). Nunca el texto que el usuario dijo, nunca el
  destinatario de una llamada o de un mensaje.

Esta telemetría está controlada por un *build flag* (`TELEMETRY_ENABLED`,
falso en debug, verdadero en release) y por un *guardrail* de código
(`TelemetryGuardrail`) que rechaza cualquier propiedad que contenga una
transcripción, un nombre completo, un número de teléfono, un email, o
una clave prohibida (`transcript`, `message`, `body`, `content`,
`contact_name`, `phone`, `phone_number`, `name`, `alias`, `address`).
Una violación rompe CI en el commit que la introduce.

El permiso `INTERNET` se declara **únicamente** en
`app/src/release/AndroidManifest.xml`. La variante debug del APK no
tiene `INTERNET` en absoluto.

**Datos que solo salen con consentimiento explícito de Fran**, vía un
toggle del menú de configuración (sección 9):

- Logs del registro de comandos fallidos. Útiles para depurar la app —
  qué cosas no entendió, qué función no existe en el catálogo. El
  toggle está desactivado por defecto. Cuando se activa, los logs
  pasan por el mismo `TelemetryGuardrail` antes de salir.
```

### Coordinated v1.1 bump items NOT in this SF

The master-plan flags three deviations from spec v1.0 that need
resolution in v1.1. **US-008 closes only the telemetry one.** The
other two are queued, deliberately not folded in here:

1. **§5 header says "8 funciones" but the list has 7.** Two paths to
   resolve: (a) renumber the header to "(7 funciones)"; (b) add an
   8th function and design it properly. Either is a separate decision
   with a separate SF. Folding it into US-008 would mix a privacy
   bump with a catalog scope change.
2. **`targetSdk` cosmetic.** The spec §14 says "target SDK Android 14
   (API 34)" but `app/build.gradle.kts` already sets `targetSdk = 35`
   (Android 15) per US-001. This is a doc-vs-code drift that needs a
   one-line spec edit; deferred to whichever SF next touches §14
   (probably a "spec hygiene" chore SF).

The brief documents both as known-queued so the developer doesn't
spot them and panic, and so the architect knows they're not in scope.

### Q1's downstream shape decision (developer reference)

Q1's resolution shapes a lot of the brief. Quick guide for the
developer once the architect resolves:

- **Q1 → Option A (release-only deps)**: the SDK classes literally
  don't exist in the debug source set. `FirebaseAndPostHogSink.kt`
  must live in `app/src/release/java/.../data/telemetry/` (or
  reference the SDKs through an interface that has a debug-only impl).
  Pairs naturally with Q5 → Option B (source-set-split Hilt module).
- **Q1 → Option B (always-present + runtime gating)**: `FirebaseAndPostHogSink.kt`
  lives in `src/main/`. Pairs naturally with Q5 → Option A (single
  module with runtime branch).
- **Q1 → Option C (hybrid)**: case-by-case. The architect documents
  the rationale.

### CLAUDE.md edit — exact diff

Section *Architecture → Permissions*, the table at L172-ish (the
"`Permission | For | If denied`" table). Add a row:

```
| `INTERNET` *(release only)* | Firebase Crashlytics/Analytics + PostHog | telemetry SDKs fail silently; app keeps working |
```

The row goes between `BIND_NOTIFICATION_LISTENER_SERVICE` and
`POST_NOTIFICATIONS` (alphabetical-ish; the developer picks the spot
that reads cleanest). The trailing paragraph that says "No `INTERNET`
permission for the core app" gets a parenthetical: "(declared **only**
in `app/src/release/AndroidManifest.xml`, never in `src/main`)".

### File-by-file inventory

For `./gradlew assembleDebug` + `./gradlew assembleRelease` + CI to
all pass, US-008 creates / modifies exactly this set of files. The
developer ticks them off:

**Created:**
- `app/src/release/AndroidManifest.xml`
- `app/src/main/java/com/curro/app/domain/repository/TelemetrySink.kt`
- `app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt`
- `app/src/main/java/com/curro/app/data/telemetry/FirebaseAndPostHogSink.kt` *(may live in `src/release/` per Q1+Q5)*
- `app/src/main/java/com/curro/app/data/telemetry/NoopTelemetrySink.kt` *(may live in `src/debug/` per Q1+Q5)*
- `app/src/main/java/com/curro/app/data/telemetry/TelemetryInitializer.kt`
- `app/src/main/java/com/curro/app/di/TelemetryModule.kt` *(or split into `src/debug/…` + `src/release/…` per Q5)*
- `app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailTest.kt`
- `app/src/test/java/com/curro/app/data/telemetry/NoopTelemetrySinkTest.kt`
- `app/src/test/java/com/curro/app/data/telemetry/TelemetryInitializerTest.kt`
- `app/src/test/java/com/curro/app/CurroAppTest.kt` *(if not already present from US-002)*

**Optional (Q2):**
- `app/src/debug/AndroidManifest.xml`

**Modified:**
- `gradle/libs.versions.toml` — activate 5 entries + add 2 plugin
  pins + 2 `[plugins]` entries
- `app/build.gradle.kts` — apply 2 plugins (per Q1) + add 4
  dependencies (per Q1) + add `buildConfigField` for `POSTHOG_API_KEY`
  (per Q6)
- `app/src/main/AndroidManifest.xml` — update the inline comment only
- `app/src/main/java/com/curro/app/CurroApp.kt` — add the
  `@Inject lateinit var telemetryInitializer` field + the
  `telemetryInitializer.initialize()` call in `onCreate()`
- `docs/curro-spec-v1.0.md` — bump version L4 + rewrite §12 + add
  `## Historial de revisiones`
- `CLAUDE.md` — add the `INTERNET (release only)` row + the
  parenthetical clarification

**Byte-identical (verify with `git diff`):**
- Every file under `app/src/main/java/com/curro/app/presentation/`
- Every file under `app/src/main/java/com/curro/app/handler/`,
  `assistant/`, `service/`, `util/`, `domain/model/`, `domain/catalog/`,
  `domain/usecase/`, `data/` (everything *except* the new
  `data/telemetry/` directory)
- Every file under `app/src/main/res/`
- `MainActivity.kt` (US-007 just touched it; US-008 doesn't)
- `app/src/main/java/com/curro/app/di/CoroutineModule.kt`,
  `Qualifiers.kt`, `AppModule.kt` (US-002 set them; US-008 doesn't touch)

### Out-of-the-way concerns surfaced by the precedent

US-002 / US-004 / US-007 set the following precedents the developer
follows here:

- **Every test fixture must include both "happy path" and "boundary"
  cases.** US-006 added the `Color(0xFF…)` grep AC because the
  enforcement-via-grep precedent was set by US-002's `git diff`-based
  invariant ACs. US-008's `grep -rn 'telemetrySink\.event…'` AC
  mirrors that exact pattern.
- **"Out of scope, explicit non-deliveries"** — the bulleted list at
  the bottom of *Scope* is non-optional. US-005 / US-006 / US-007 all
  carried one; US-008 carries one (above).
- **Architect resolves Q-blocks before development** — the AC
  "Q# — Resolved" notation matches US-002 / US-004 precedent. The
  architect adds an *Architect's notes & decisions* appendix at the
  end of this file.
- **Spec bumps live with the SF that justifies them** — this is the
  PM's Q7 recommendation; aligned with the master-plan's "when
  implementation surfaces an ambiguity, refine the spec (and bump its
  version) — don't fork the decision".

## Open Questions

The architect resolves all eight before development. After resolution,
each gets a `**Q# — Resolved: …**` block.

### Q1 — Telemetry-gating strategy

Three options for how Firebase + PostHog reach (or fail to reach) the
debug variant:

- **Option A — release-only dependencies (`releaseImplementation`)**:
  Firebase BOM + Crashlytics + Analytics + PostHog declared with
  `releaseImplementation` only. Debug builds *don't have the SDK
  classes on the classpath at all*. Pros: strongest "debug never
  touches the network" guarantee; smallest debug APK; can't
  accidentally invoke a release SDK in debug. Cons: code in
  `src/main/` can't reference SDK classes (must split into
  `src/release/` for the release impl); slightly more source-set
  juggling.
- **Option B — always-present, runtime-gated**: plain `implementation`
  for all four; `TelemetryInitializer.initialize()` checks
  `BuildConfig.TELEMETRY_ENABLED` and no-ops in debug. Pros: simpler
  build; SDK classes available for debug testing if ever needed;
  symmetric source set. Cons: SDK classes linked in debug; APK
  slightly bigger; a future bug could accidentally invoke the
  release sink in debug if the runtime branch is bypassed.
- **Option C — hybrid**: Firebase via `releaseImplementation`, PostHog
  via `implementation` (or vice versa). The architect must justify why.

PM recommendation: **Option A.** Strongest privacy guarantee, pairs
cleanly with Q5 Option B (source-set-split Hilt module), and the
extra source-set juggling is one-time. The "I can't reference the
release sink from main" constraint is *desirable* — it forces the
debug-vs-release boundary to be structural, not commented.

### Q2 — Defensive `src/debug/AndroidManifest.xml`?

Should the brief land a defensive
`app/src/debug/AndroidManifest.xml` that uses
`<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`
to ensure no transitive library accidentally drags `INTERNET` into
the debug APK?

PM recommendation: **yes, belt-and-braces.** The `aapt dump permissions`
AC catches the leak, but the overlay catches it at the source. The
architect may overrule if there's a maintenance concern.

### Q3 — Debug build without `google-services.json`

The Firebase Gradle plugin (`com.google.gms.google-services`) requires
`app/google-services.json` to be present at build time. A fresh-clone
debug build (no `google-services.json`, no `GOOGLE_SERVICES_JSON` CI
secret) must still succeed (per the AC and per US-001's "fresh clone
green" invariant). Three paths:

- **Option A — make the plugin conditional**: apply the
  `google-services` plugin only for the release variant (via
  `apply false` at the top level + `plugins { apply(...) }` inside the
  `release` source-set hook, OR via a `if (file("app/google-services.json").exists())`
  gate in `app/build.gradle.kts`). Debug builds skip the plugin
  entirely.
- **Option B — commit a stub `google-services.json`** that has a
  syntactically-valid shape but points at a never-used Firebase
  project. The plugin is satisfied; debug builds don't crash; if the
  release build accidentally ships the stub, telemetry silently
  fails (acceptable degraded behaviour). Cons: a fake credential in
  the repo, which is misleading.
- **Option C — make the plugin opt-in via a CI env var**: like Option
  A but explicit — a `firebaseEnabled` Gradle property that defaults
  to false; CI sets it true; the developer enables it locally only
  when working on the release build.

PM recommendation: **Option A, gated on the variant**. The plugin
applies only to the release variant; debug builds don't see it.
Pairs naturally with Q1 Option A. The architect's call if there's a
plugin-mechanics reason this is hard (some Gradle plugins resist
per-variant application — the docs claim `google-services` supports
it but the architect verifies).

### Q4 — `TelemetryGuardrail` shape

The PM's proposed shape (heuristic + key blocklist combined). Three
options:

- **Option A — heuristic only**: regex-based value inspection
  (email-ish, phone-ish, two-capital-words, > 32-char string) + no
  key list. Pros: flexible — a new feature can emit `recipient: "ok"`
  if the value is safe. Cons: a false-negative on a new shape of PII
  (e.g. a postal code) goes through.
- **Option B — strict key whitelist**: an `ALLOWED_PROPS` registry of
  `(event_name → Set<prop_key>)`; everything not on the list is
  rejected. Pros: a developer adding a new event has to update the
  registry, which surfaces in code review. Cons: more bookkeeping;
  early SFs would need to seed the registry.
- **Option C — both (PM's default proposal)**: key blocklist (rejects
  obvious-PII keys regardless of value) + value heuristic (rejects
  PII-shaped values regardless of key). Pros: belt-and-braces. Cons:
  more code, more tests, more false-positives possible (a legitimate
  value > 32 chars gets rejected — the developer either shortens the
  value or marks it `@Suppress`-equivalent in the registry).

PM recommendation: **Option C — both**, with a clear escape hatch
documented (a per-event opt-out marker for cases like "I want to log
a 60-char ID that happens to exceed the heuristic"). The architect
picks; the test fixture pins the behaviour, the architect picks the
mechanism.

### Q5 — Hilt module shape

Two options:

- **Option A — single module with `BuildConfig.TELEMETRY_ENABLED`
  runtime branch**: one `TelemetryModule.kt` in `src/main/`, with
  `provideTelemetrySink` returning either impl based on the flag.
- **Option B — separate source-set modules**:
  `src/debug/java/.../di/TelemetryModule.kt` binds the noop;
  `src/release/java/.../di/TelemetryModule.kt` binds the
  Firebase+PostHog impl; no `src/main/` version. Hilt sees exactly
  one at compile time.

PM recommendation: **Option B.** Pairs with Q1 Option A (the
release impl literally doesn't exist in debug; the debug Hilt graph
can't bind it). The Hilt source-set merge is standard and well-tested.

### Q6 — PostHog API key supply

Where does the PostHog API key come from at build time?

- **Option A — `local.properties`** (per-dev), read via
  `localProps.getProperty("POSTHOG_API_KEY")` and surfaced via
  `buildConfigField`. Same pattern as the existing `KEYSTORE_PATH` etc.
  in `app/build.gradle.kts` L19–L23. Pros: no commit-worthy secret;
  CI passes the key via the same env-var-→-`local.properties`
  pattern. Cons: local-dev friction (each dev needs the key).
- **Option B — CI env var only**: `POSTHOG_API_KEY` injected by the CI
  workflow at build time; debug builds always get an empty string;
  release builds in CI get the real key. Pros: simpler local setup
  (devs never need the key — only CI does). Cons: hard to test a
  release-against-real-PostHog locally.
- **Option C — both**: try `local.properties` first, fall back to
  `System.getenv("POSTHOG_API_KEY")`. Pros: most flexible. Cons: most
  code paths.

(The Firebase project lives in `app/google-services.json`, which is a
separate decision — Q3 covers it.)

PM recommendation: **Option C.** Same pattern as the existing
`KEYSTORE_PATH` plumbing, with a CI fallback so the workflow doesn't
need to write `local.properties` on the runner. The architect's call.

### Q7 — Spec v1.1 bump in this commit or its own commit?

- **Option A — same commit**: the brief, the code, and the spec bump
  all land in one `docs(prd) + feat(telemetry) + docs(spec)` commit.
  Pros: traceability — the spec moves because *this* SF needs it.
  Cons: a larger commit; the spec change is mixed with code.
- **Option B — two commits, both in this PR / push**: first commit
  `docs(spec): bump to v1.1 — telemetry revision`; second commit
  `feat(telemetry): SF-0.8 — Firebase + PostHog + INTERNET in release only`.
  Pros: cleaner history; spec change reviewable in isolation. Cons:
  the dependency direction is technically backward (the spec is bumped
  *before* the code that justifies it lands), so an external observer
  reading just the first commit would be confused.

PM recommendation: **Option A — same commit.** The bump *is* this SF's
intent (master-plan SF-0.8 frames the spec revision as a deliverable).
The user's commit message hint —
`docs(prd): add US-008 — telemetry plumbing (Firebase + PostHog, INTERNET in release only)`
— is one commit message; the spec bump fits inside it.

(NOTE: the user's actual commit instruction is for the PRD + brief
**only** in this turn — the developer will make the *real* US-008
commit later, after architect resolution + implementation. Q7's
"same commit or separate" decision is about *that future commit*, not
this PRD/brief commit. The brief documents it for the developer's
benefit.)

### Q8 — anything else?

Catch-all for whatever the architect finds when reading the brief
against the master-plan, `CLAUDE.md` → "Privacy & telemetry", the
parked `api-integration` skill, and the `verification-checklist` skill.

Candidate Q8 items the PM has *noticed but is deferring to the
architect*:

- **a — Firebase BOM version**. Master-plan suggests "latest stable for
  May 2026"; the catalog has `firebaseBom = "33.7.0"`. Verify current.
- **b — PostHog Android SDK version**. Same. `posthog = "3.8.0"` —
  verify current.
- **c — The two Gradle plugin versions** commented at L124–L125 of
  `libs.versions.toml` (`google-services = 4.4.2`,
  `firebase-crashlytics-plugin = 3.0.2`) — verify current as of
  2026-05-14.
- **d — Spec ambiguity beyond the v1.1 items**. I noticed one while
  reading: spec §12 v1.0 lists "Texto transcrito" as a "datos que
  nunca salen" item; the §12 v1.1 rewrite keeps this verbatim. **But**
  the future spec-§9 toggle "envíame los fallos" includes the
  failed-commands log, which today *contains* the transcribed text
  that the model failed to map (spec §6 flow 7). So the v1.1 §12 in
  *some readings* is in tension with the v1.0 §9 "logs de comandos
  fallidos" — the question is whether "Texto transcrito" means "raw
  audio transcripts of every utterance" (which never leave) or also
  "the transcript of an utterance the model couldn't map" (which the
  user-controlled toggle *can* send). The PM's read is the latter
  *only with user consent*; the spec text should explicitly clarify.
  The architect resolves whether the v1.1 §12 rewrite needs an extra
  paragraph about "transcripciones de comandos fallidos pueden salir
  solo si Fran activa el toggle…".
- **e — `ACCESS_NETWORK_STATE` permission**. The release manifest
  declares `INTERNET` only. Firebase + PostHog historically request
  `ACCESS_NETWORK_STATE` via their own manifest manifests (transitive
  merge). The AC says "exactly one `INTERNET` line" — confirm whether
  the merged release manifest also gains `ACCESS_NETWORK_STATE` from
  the libraries. If yes: the AC needs adjustment; if no: the brief is
  fine.
- **f — `tools:node="remove"` on `ACCESS_NETWORK_STATE` in
  `src/main/`**. Defensive — same as Q2 but for the additional
  permission Q8e might surface.
- **g — The PostHog API key as a `String?` vs `String`**. If Q6 Option
  B (CI-only) lands, the debug build has no key — what does
  `PostHog.setup(context, null)` do? Probably nothing useful. The
  initializer needs a null-key short-circuit.
- **h — Whether `TelemetryInitializer` should be `@Inject lateinit`
  in `CurroApp` or constructor-injected**. Hilt allows both for
  `Application`; the existing US-002 precedent is `@Inject lateinit
  var` for the few things `CurroApp` will inject. The brief assumes
  this shape.

The architect picks; any additional Qs surface as Q9, Q10, etc.

## Architect's notes & decisions

> *(To be filled in by the architect before development begins.)*
> *Precedent: US-002 / US-004 use this section for A1–An — concrete
> decisions on each Q, plus mechanical notes the developer needs to
> avoid re-deriving (e.g. "Hilt requires `Lazy<T>` when the binding
> graph might pick either of two impls — that's why `provideTelemetrySink`
> takes `Lazy<FirebaseAndPostHogSink>` not `FirebaseAndPostHogSink`").*

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-14 | Fran (PM agent) | Initial draft — Q1–Q8 open, awaiting architect resolution |
