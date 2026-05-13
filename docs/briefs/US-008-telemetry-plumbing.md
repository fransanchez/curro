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
> **Architect involvement: COMPLETE (2026-05-14).** Q1–Q8 resolved;
> A1–A16 added. The eight load-bearing decisions:
>
> 1. **Q1 — Resolved**: release-only dependencies (`releaseImplementation`) + `BuildConfig.TELEMETRY_ENABLED` runtime kill switch (belt-and-braces).
> 2. **Q2 — Resolved**: NO defensive `src/debug/AndroidManifest.xml` (Q1 makes it structurally redundant).
> 3. **Q3 — Resolved**: conditional `apply(plugin = ...)` on `google-services.json` presence; release without the file fails loudly.
> 4. **Q4 — Resolved**: strict whitelist (`ALLOWED_PROPS` registry) AS the primary guard + value heuristic AS the secondary guard. No escape hatch.
> 5. **Q5 — Resolved**: separate source-set modules — `src/debug/.../di/TelemetryModule.kt` binds `Noop`, `src/release/.../di/TelemetryModule.kt` binds `FirebaseAndPostHog`. Pairs with Q1.
> 6. **Q6 — Resolved**: `local.properties` first, `System.getenv("POSTHOG_API_KEY")` fallback; release with empty key fails fast at `initialize()`.
> 7. **Q7 — Resolved**: TWO commits, code then spec, one push. Reversed the PM recommendation — spec-diff reviewability wins.
> 8. **Q8 — Resolved** (eight sub-items Q8a–Q8h): keep current SDK / plugin versions; v1.1 §12 wording carves out the failed-commands path explicitly with `FailedCommandsExporter` as a future SF; relax "exactly one INTERNET" AC to include the transitive `ACCESS_NETWORK_STATE` + `WAKE_LOCK`; refuse `AD_ID` via `tools:node="remove"` + meta-data flag (A13); release init fails fast on empty PostHog key; `@Inject lateinit var` in `CurroApp` per US-002 precedent.
>
> See *Open Questions → Resolved* below for full rationale + resolved
> code shapes; see *Architect's notes & decisions (A1–A16)* appendix for
> the cross-cutting decisions (Q1+Q5 coupling, merged-manifest as
> permission source-of-truth, no-escape-hatch policy, `SdkBootstrap`
> interface-per-variant shape, future kill-switch plumbing,
> `FailedCommandsExporter` deferral, CI implications, AD_ID refusal,
> reversibility table).
>
> **No further architect review is required before `/implement-feature
> US-008`.** Escalation rule: if the developer hits a concrete
> obstacle implementing a resolved choice (e.g. an AGP version that
> rejects the conditional `apply` pattern in Q3), the developer
> escalates back rather than silently flipping. The future
> `FailedCommandsExporter` SF (A7) gets its own architect pass.

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
| **Architect** | Claude `android-architect` — Q1–Q8 resolved 2026-05-14; A1–A16 added. See *Open Questions → Resolved* and *Architect's notes & decisions*. |

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

### Spec §12 v1.1 — final rewrite (architect-resolved)

> **Note:** the architect's pass (2026-05-14) replaces the PM's
> proposed §12 wording with a refined version that explicitly carves
> out the failed-commands-transcript-with-consent path and pins the
> `TelemetrySink` boundary in writing. **The text the developer commits
> is the one in Q8d-Resolved** (see *Open Questions → Q8 → Q8d*). The
> PM's original draft is preserved below for diff visibility.

#### PM original draft (superseded — preserved for diff visibility)

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

#### Architect-resolved final text

See **Q8d — Resolved** above for the final Spanish wording. The
material changes vs the PM draft:
1. Explicit "Texto transcrito (de cualquier utterance, exitoso o no — ver excepción para fallos abajo)" carve-out marker at the top.
2. Explicit "Ninguna ruta de la telemetría técnica transporta transcripciones de utterances" pin.
3. The failed-commands path is documented as going via a separate
   `FailedCommandsExporter` (TBD SF-8.x) — **NOT** the same
   `TelemetryGuardrail` pipe. The PM's draft conflated the two
   channels; the architect's draft separates them.
4. Explicit "Las transcripciones de comandos exitosos nunca salen,
   independientemente del toggle." closing line.

The developer commits the **architect-resolved** text in the
`docs(spec): bump to v1.1` commit (Q7-Resolved + A12).

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

**Q1 — Resolved: Option A (`releaseImplementation`) + a defence-in-depth `BuildConfig.TELEMETRY_ENABLED` runtime check in `TelemetryInitializer`. Belt-and-braces.**

Rationale: Curro's central privacy promise (`CLAUDE.md` → "Privacy & telemetry",
spec §12 v1.1) is *audible* to non-engineers — "no audio, no transcripts, no
message bodies, no contact names leave the device". The *structural* form of
that promise is "the debug variant of the APK has zero Firebase / PostHog
classes on its classpath". Option A delivers that structurally: the SDKs are
declared with `releaseImplementation` only; `assembleDebug` produces an APK
that **cannot** call Crashlytics or PostHog **because the bytecode is not
there**. That is a fundamentally stronger guarantee than "the runtime branch
is well-tested" — a future bug, an instrumentation test gone wrong, or a
copy-paste from a code-search result cannot exfiltrate from a classpath that
does not contain the SDK.

The belt-and-braces piece: even on the release variant, `TelemetryInitializer`
checks `BuildConfig.TELEMETRY_ENABLED` before initialising the SDKs. This is
**not** redundant with Option A — it is the *kill switch* a future SF
(SF-8.x: an emergency runtime-disable toggle; SF-8.x: the user-controlled
analytics opt-out per `setAnalyticsCollectionEnabled(false)` / `PostHog.optOut()`,
see A9) can flip without rebuilding. The name `TELEMETRY_ENABLED` reads as
"is telemetry on at all?"; the semantics are "is it on **by default**?"
(A10). Today the default is hard-coded `true` for release / `false` for debug;
tomorrow a settings repository can override it.

Option B was rejected because the marginal simplicity (one `implementation`
line instead of one `releaseImplementation` line) is dwarfed by the privacy
cost. Option C (hybrid) was rejected because there is no asymmetric reason —
Firebase and PostHog have the same "this SDK is for off-device telemetry"
posture and should share the same gating strategy.

Resolved shape in `app/build.gradle.kts`:

```kotlin
// Firebase plugins applied conditionally per Q3 (see Q3-Resolved).
// SDK dependencies declared per Q1 = Option A:
dependencies {
    // … existing main implementation lines …

    // Release-only: the SDK bytecode never lands in the debug APK.
    releaseImplementation(platform(libs.firebase.bom))
    releaseImplementation(libs.firebase.crashlytics)
    releaseImplementation(libs.firebase.analytics)
    releaseImplementation(libs.posthog.android)
}
```

Verification AC (in addition to the brief's existing AC):
- `./gradlew dependencies --configuration debugRuntimeClasspath | grep -Ei 'firebase|posthog'` → zero matches.
- `./gradlew dependencies --configuration releaseRuntimeClasspath | grep -Ei 'firebase|posthog'` → multiple matches (Firebase BOM, Crashlytics, Analytics, PostHog).

Couples directly to Q5 (Option B — source-set-split Hilt modules; the release
impl literally does not exist in the debug source set) and to Q3 (Option A —
the `google-services` / `firebase-crashlytics` Gradle plugins are applied
conditionally so debug builds don't require `google-services.json`).

Reversibility: O(15 min) — flip `releaseImplementation` to `implementation`
and drop the `Lazy` wrapper. The structural choice is hard to reverse only
in the sense that it shapes consumer SFs' assumptions ("the SDK isn't
there in debug, so don't try to mock it in debug"); the *build script* is
two minutes of editing. See A1 for the full propagation surface.

**Q2 — Resolved: NO. Skip the defensive `src/debug/AndroidManifest.xml`.**

Rationale: with Q1 = Option A (Firebase + PostHog in `releaseImplementation`
only), the SDKs' library manifests **don't enter the debug merged manifest at
all**. AGP's manifest-merger walks the dependency tree per variant; the
`releaseImplementation`-scoped libraries are absent from the `debug`
variant's resolution graph, so their `<uses-permission>` contributions (which
*do* exist — Firebase Analytics declares `INTERNET`, `ACCESS_NETWORK_STATE`,
`WAKE_LOCK`; PostHog declares `INTERNET`, `ACCESS_NETWORK_STATE` — see Q8e)
are never merged into `debug`. A `tools:node="remove"` overlay would be
removing permissions that **cannot be there**. Belt-and-braces in this case
is belt-and-suspenders-and-rope: redundant, and the file invites someone in
a future SF to "consolidate" it into something that *does* fire, drifting
the contract.

The `aapt dump permissions` AC catches any future regression at CI time —
that is the load-bearing guard. The structural guarantee from Q1 plus the
CI-level verification is sufficient.

There **is** an audit-trail value in pinning the privacy promise in a place
the reader sees first. We satisfy that with a top-of-file comment in
`app/src/main/AndroidManifest.xml` (already present at L4–L15, updated per
the brief's existing instruction) and the rationale comment in
`app/src/release/AndroidManifest.xml`. A `src/debug/` manifest would be a
*third* place to keep in sync; we don't add it.

If a future SF takes a dependency that **does** leak `INTERNET` into the
debug variant (a lint library? a Compose tooling preview library? an
analytics SDK that lands as plain `implementation`?), the `aapt dump
permissions` AC fires in CI and the SF that introduced the dependency owns
the fix — at *that* moment, the `tools:node="remove"` overlay is the right
hammer, and adding it is a 5-line one-time cost. Pre-empting it today adds
maintenance for no current win.

Reversibility: O(5 min) — drop a `src/debug/AndroidManifest.xml` file when
needed. We are not painting ourselves into a corner.

(See A2 for the merged-manifest verification AC the developer runs against
both variants.)

**Q3 — Resolved: Option A — apply the Firebase plugins conditionally on the file's presence (`file("app/google-services.json").exists()`). Debug builds without the file succeed unconditionally; release builds without the file fail loudly with a developer-readable error.**

Rationale: there are two real constraints — (a) `assembleDebug` on a
fresh clone (no `google-services.json`, no CI secret) **must** succeed
(US-001 invariant; AC pinned), and (b) `assembleRelease` **must not silently
ship without telemetry** (a stub file shipping to a real device would mean
"crashes go to a black-hole Firebase project nobody reads", which is worse
than "the release build refused to build"). Option A is the *only* shape
that satisfies both — gate the plugin application on the file's presence,
and let the release build *fail* if it's missing (the developer gets
a clear "google-services.json missing — see docs/briefs/US-008…" message,
not a silent ship).

Option B (stub committed) was rejected: a stub Firebase project ID in the
repo is a *misleading credential* that a future reader assumes is real.
The cost of a fake credential in git history is asymmetric — once committed,
even cleaning the file requires history rewriting; and if the stub ID
collides with an in-use project, telemetry quietly redirects. The fix is
worse than the disease.

Option C (gradle property `firebaseEnabled`) was rejected: it adds a
*third* signal (file presence + variant + property) where two suffice. The
file's presence **is** the signal; gating on the variant is implicit
because release is the only consumer.

Resolved shape in `app/build.gradle.kts` (after the existing `plugins { }`
block, before `android { }`):

```kotlin
// Apply Firebase plugins only if google-services.json is present.
// Debug builds without the file: skipped (no telemetry wired — Q1 keeps the SDKs out anyway).
// Release builds without the file: the developer is missing the secret; build fails loudly.
// The file is git-ignored (.gitignore L52); CI decodes from the GOOGLE_SERVICES_JSON secret
// in .github/workflows/ci.yml (step "Decode google-services.json" already in place).
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.plugin.get().pluginId)
}

// And in the top-level plugins { } block, the entries stay `apply false`-equivalent —
// i.e. they are declared in libs.versions.toml's [plugins] section (activated per Q3),
// but the alias(libs.plugins.google.services) call is NOT made at the top level here.
// They are applied via the `apply(plugin = …)` calls above, which means they enter the
// build only when the file exists.
```

Build-failure verification (`assembleRelease` without `google-services.json`):
the developer pre-flights this once locally — temporarily move the file aside,
run `./gradlew assembleRelease`, confirm the build fails with the
`google-services` plugin's stock error message ("File google-services.json
is missing"). Restore the file before commit.

CI implications: with Q1 Option A (release-only deps), `assembleDebug` in CI
**does not need** `google-services.json` — the decode step in `.github/workflows/ci.yml`
remains a no-op when the secret isn't set, exactly as today. If a future SF
adds `assembleRelease` to CI (e.g. signed-AAB-on-tag publishing), the decode
step becomes load-bearing — that SF owns adding the secret to the repo
settings (see A11 for the CI implications detail).

PostHog's API key is a separate decision (Q6) — that's a `buildConfigField`
read from `local.properties` + a CI env-var fallback, not a JSON file.

Reversibility: O(5 min) — the conditional apply collapses to an
unconditional `alias(libs.plugins.google-services)` if the project ever
ships a committed stub. The decision is one Gradle script edit.

**Q4 — Resolved: strict key whitelist (`ALLOWED_EVENTS` + `ALLOWED_PROPS` registry) AS THE PRIMARY GUARD, plus the value heuristic AS A SECONDARY GUARD. Both. No escape hatch.**

Rationale: the **whitelist is the privacy boundary**; the heuristic is the
seatbelt that catches the case where a whitelisted key receives a value with
PII embedded by accident (an `error_message` value that contains an email
the model echoed back; a `device_name` value the user has customised to
their own name). Option A (heuristic only) was rejected: heuristics are
*pattern recognition* on *strings* and they will always have false negatives
on shapes Curro hasn't seen — postal codes, IBANs, NIE numbers, free-text
locations like "casa de Pepito". The cost of a false negative is unbounded
(a transcript leaks); the cost of a false positive is bounded (a legitimate
event is rejected and the developer adds the value's shape to the
whitelist).

Option B (whitelist only) was rejected because the heuristic costs nothing
once written and adds a defence-in-depth layer against the unbounded-cost
failure above. Option C (heuristic + blocklist) was rejected as too lax:
"forbid these keys" admits "any other key is OK", which is the wrong default
for a system whose privacy budget is zero. The right default is "any key
not on the whitelist is forbidden".

No escape hatch (no per-event `@Suppress("PII")` marker, no `IS_BOUNDED_ID`
flag). Reason: every escape hatch in a privacy guardrail is a future foot-gun.
If a future SF *legitimately* needs a 60-character value (a UUID, a
hyphenated function-call action name with parameters), the answer is to add
that specific key + value-shape to the whitelist (e.g. `request_id` with
a value-validator that asserts `value.matches(uuidRegex)`), not to broadly
exempt the call. **Whitelisted, exact, regex-validated where the value
shape matters.** No suppression annotations. Reversibility is O(5 min) per
future addition.

Resolved shape (illustrative — the developer refines the registry as the
allowed examples list grows):

```kotlin
// app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt
package com.curro.app.data.telemetry

import android.util.Log

/**
 * Privacy boundary every telemetry call routes through. Two layers:
 *
 *  1. KEY WHITELIST  (primary)   — every (eventName -> propKey) pair must
 *                                  be registered in ALLOWED_PROPS below.
 *                                  Unknown event names AND unknown prop keys
 *                                  for a known event both reject.
 *  2. VALUE HEURISTIC (secondary) — even whitelisted values are inspected
 *                                  for PII shapes (email, phone, name-like,
 *                                  long-string). A reject here is a bug — the
 *                                  developer either shortens the value, hashes
 *                                  it, or narrows the whitelist's value-shape
 *                                  contract.
 *
 * No escape hatch — see brief Q4-Resolved + A3.
 *
 * See docs/curro-spec-v1.0.md §12 (v1.1) and CLAUDE.md → Privacy & telemetry.
 */
object TelemetryGuardrail {

    /** Pre-compiled to keep the hot path cheap. */
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("^\\+?\\d[\\d ()\\-]{6,}$")
    private val FULL_NAME = Regex("\\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+ [A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\b")
    private const val MAX_VALUE_LEN = 32

    /**
     * Whitelisted events. Each entry is (eventName, allowedPropKeys).
     * Adding a new event requires updating this map AND adding fixture cases to
     * TelemetryGuardrailTest. Code review surfaces both diffs.
     */
    private val ALLOWED_PROPS: Map<String, Set<String>> = mapOf(
        // SF-3.6 — FunctionGemma decision loop
        "function_called" to setOf("action", "confidence_bucket", "latency_ms", "from_warm"),
        // SF-2.x — STT failures
        "stt_failed" to setOf("error_code"),
        // SF-3.5 — model warm-up service
        "model_loaded" to setOf("model", "load_ms", "cold_start"),
        "model_killed_by_system" to setOf("model", "uptime_s"),
        // SF-1.x — launcher
        "launcher_set_default" to setOf("attempt"),
        // SF-4.x — handler outcome
        "handler_finished" to setOf("function", "outcome", "ambiguous"),
        // SF-5.x — confidence policy
        "confidence_below_threshold" to setOf("function", "threshold", "delta"),
        // SF-1.x — app lifecycle smoke
        "app_open" to emptySet(),
    )

    /** Whitelisted user-property keys (NEVER PII; scalar enums only). */
    private val ALLOWED_USER_PROPS: Set<String> = setOf(
        "locale", "device_variant", "hyperos_version",
    )

    sealed interface GuardrailResult {
        data object Allow : GuardrailResult
        data class Reject(val reason: String) : GuardrailResult
    }

    fun check(name: String, props: Map<String, Any>): GuardrailResult {
        val allowedKeys = ALLOWED_PROPS[name]
            ?: return Reject("event '$name' not on ALLOWED_PROPS whitelist")
        for ((key, value) in props) {
            if (key !in allowedKeys) {
                return Reject("event '$name': prop '$key' not on whitelist")
            }
            valueHeuristic(value)?.let { return Reject("event '$name'.'$key': $it") }
        }
        return Allow
    }

    fun check(userPropertyKey: String, value: String?): GuardrailResult {
        if (userPropertyKey !in ALLOWED_USER_PROPS) {
            return Reject("user property '$userPropertyKey' not on whitelist")
        }
        value ?: return Allow // null clears the property, always safe
        valueHeuristic(value)?.let { return Reject("user property '$userPropertyKey': $it") }
        return Allow
    }

    /** Returns the reason if the value LOOKS like PII; null if it's safe. */
    private fun valueHeuristic(value: Any): String? {
        val s = value.toString() // Int, Boolean, Long, Double all stringify safely
        if (s.length > MAX_VALUE_LEN) return "value exceeds $MAX_VALUE_LEN-char limit"
        if (EMAIL.containsMatchIn(s)) return "value matches email shape"
        if (PHONE.matches(s)) return "value matches phone shape"
        if (FULL_NAME.containsMatchIn(s)) return "value matches full-name shape"
        return null
    }
}
```

Note on `value.toString()` — non-`String` values (`Int`, `Boolean`, `Long`,
`Double`) stringify safely. The guard is against `props["something"] =
someObject` where `someObject.toString()` happens to render a transcript;
the heuristic catches it. The brief flagged this concern in *Testing
Requirements*; the guardrail addresses it.

Verification (the load-bearing CI test): the brief's existing
`TelemetryGuardrailTest` fixture is **right** — every forbidden example
rejects, every allowed example allows. The implementation must satisfy
those examples; if any forbidden one passes, the guardrail is wrong (not
the test). The test is in `src/test/`, runs on `./gradlew testDebugUnitTest`.

Coverage of "the guardrail unit-tests the rules, not the call sites" (the
brief's Flow 1 concern): the whitelist closes this. A developer adding a new
event MUST register it in `ALLOWED_PROPS`; code review of the registry diff
surfaces "you added `recipient_name` — what is that?".

Reversibility: O(0) for the whitelist (every new event is a registry
edit anyway); O(15 min) to drop the heuristic if it ever causes pain.
The whitelist is the load-bearing contract; the heuristic is removable
later if it becomes false-positive-heavy without weakening the privacy
boundary (the whitelist would still hold).

See A3 for the no-escape-hatch policy, A4 for the test fixture data-class
pattern.

**Q5 — Resolved: Option B (separate source-set modules). `src/debug/.../di/TelemetryModule.kt` binds `NoopTelemetrySink`; `src/release/.../di/TelemetryModule.kt` binds `FirebaseAndPostHogSink`. No `src/main/.../di/TelemetryModule.kt`.**

Rationale: coupled to Q1 Option A. With Firebase + PostHog in
`releaseImplementation` only, `FirebaseAndPostHogSink.kt` **cannot live in
`src/main/`** — its imports (`FirebaseCrashlytics`, `PostHog`) don't resolve
on the debug classpath. The Hilt module that binds it must therefore live
in `src/release/` too. The symmetric move — the `Noop` binder lives in
`src/debug/` — gives us a strictly compile-time-resolved Hilt graph: in
debug, the graph contains exactly one `TelemetrySink` binding (`Noop`); in
release, exactly one (`FirebaseAndPostHogSink`). No runtime branch, no
`Lazy<T>`, no "what does this evaluate to in tests?" ambiguity.

This pairs cleanly with the project's existing source-set hygiene (US-002 A4
documented `@TestInstallIn` and `@BindValue` as the test-graph mechanisms —
both work transparently here; an instrumented test that wants a fake sink
uses `@BindValue val sink: TelemetrySink = FakeSink()` and Hilt's
`@UninstallModules(TelemetryModule::class)` picks the right module per
variant). Option A (runtime branch) was rejected because (a) the runtime
branch is *redundant* with Q1's compile-time separation, and (b) a
runtime branch with `Lazy<FirebaseAndPostHogSink>` would force
`FirebaseAndPostHogSink.kt` into `src/main/`, which Q1 forbids.

Resolved file layout:

```
app/src/debug/java/com/curro/app/
├── data/telemetry/
│   └── NoopTelemetrySink.kt          // moved here from src/main/
└── di/
    └── TelemetryModule.kt            // @Binds TelemetrySink -> NoopTelemetrySink

app/src/release/java/com/curro/app/
├── data/telemetry/
│   └── FirebaseAndPostHogSink.kt     // moved here from src/main/
└── di/
    └── TelemetryModule.kt            // @Binds TelemetrySink -> FirebaseAndPostHogSink

app/src/main/java/com/curro/app/
├── data/telemetry/
│   ├── TelemetryGuardrail.kt         // shared — no SDK refs
│   └── TelemetryInitializer.kt       // shared — uses interfaces only (see A5)
└── domain/repository/
    └── TelemetrySink.kt              // shared — interface
```

`TelemetryInitializer.kt` lives in `src/main/` and takes a `TelemetrySink`
plus a `SdkBootstrap` interface (also `src/main/`) whose two impls
(`NoopSdkBootstrap` in debug, `FirebaseAndPostHogSdkBootstrap` in release)
do the SDK setup. The initialiser itself contains no SDK references.
See A5 for the full interface layout — this is the load-bearing piece for
making `CurroApp.onCreate()` and `TelemetryInitializer` symmetric across
variants.

Resolved module shape (release):

```kotlin
// app/src/release/java/com/curro/app/di/TelemetryModule.kt
package com.curro.app.di

import com.curro.app.data.telemetry.FirebaseAndPostHogSink
import com.curro.app.data.telemetry.FirebaseAndPostHogSdkBootstrap
import com.curro.app.data.telemetry.SdkBootstrap
import com.curro.app.domain.repository.TelemetrySink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds @Singleton
    abstract fun bindTelemetrySink(impl: FirebaseAndPostHogSink): TelemetrySink

    @Binds @Singleton
    abstract fun bindSdkBootstrap(impl: FirebaseAndPostHogSdkBootstrap): SdkBootstrap
}
```

And debug:

```kotlin
// app/src/debug/java/com/curro/app/di/TelemetryModule.kt
package com.curro.app.di

import com.curro.app.data.telemetry.NoopSdkBootstrap
import com.curro.app.data.telemetry.NoopTelemetrySink
import com.curro.app.data.telemetry.SdkBootstrap
import com.curro.app.domain.repository.TelemetrySink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds @Singleton
    abstract fun bindTelemetrySink(impl: NoopTelemetrySink): TelemetrySink

    @Binds @Singleton
    abstract fun bindSdkBootstrap(impl: NoopSdkBootstrap): SdkBootstrap
}
```

Both modules are named `TelemetryModule` and live in the same package
(`com.curro.app.di`). Hilt sees exactly one per variant — there is no
collision because they are in different source sets and the source-set
merge is per-variant. This is the standard Hilt pattern; the AGP /
Hilt processor handles it transparently.

Reversibility: O(20 min) — collapse the two modules into a single
`src/main/.../di/TelemetryModule.kt` with a runtime branch and move
the sinks back to `src/main/`. The compile-time guarantee would be
lost in the process. We do not anticipate reverting; the structure
is the design.

See A1 (Q1 / Q5 coupling), A5 (`TelemetryInitializer` + `SdkBootstrap`
shape).

**Q6 — Resolved: Option C — `local.properties` first, fall back to `System.getenv("POSTHOG_API_KEY")`. Empty string in debug; release with neither sink available fails fast at `TelemetryInitializer.initialize()` with a developer-readable error.**

Rationale: this matches the existing `KEYSTORE_PATH` plumbing pattern (the
`local.properties` read in `app/build.gradle.kts` L19–L23) and gives the
two real consumers what they need — the local-release-build path
(rare; a developer testing crash reporting against the real PostHog
project locally) and the CI release-build path (the future SF that adds
`assembleRelease` to CI sets the env var as a secret). Option A (local
only) was rejected: CI can't write `local.properties` cleanly. Option B
(CI only) was rejected: it makes local release builds impossible to
test against the real PostHog dashboard, which is the only smoke test
that proves the wiring works.

The release-without-key fail-fast is important: a release build that
runs `PostHog.setup(context, "")` silently no-ops PostHog forever
(events go nowhere). That's the same black-hole failure mode as a stub
`google-services.json` (Q3 rejected for the same reason). Instead, the
release variant of `FirebaseAndPostHogSdkBootstrap` (A5) checks for an
empty key and throws `IllegalStateException("POSTHOG_API_KEY missing —
see docs/briefs/US-008…")` from `initialize()` — the app crashes on
launch, the developer sees the message, the fix is one line in
`local.properties` (or one CI secret).

Resolved shape in `app/build.gradle.kts` (added to the `release { }`
block of `buildTypes`):

```kotlin
release {
    isMinifyEnabled = false
    buildConfigField("boolean", "TELEMETRY_ENABLED", "true")
    // Q6: local.properties first, env var fallback (CI). Empty string allowed at
    // build time; release initialiser throws if it ends up "" at runtime (A6).
    val posthogKey = localProps.getProperty("POSTHOG_API_KEY")
        ?: System.getenv("POSTHOG_API_KEY")
        ?: ""
    buildConfigField("String", "POSTHOG_API_KEY", "\"$posthogKey\"")
    // … existing signingConfig logic …
}
debug {
    isMinifyEnabled = false
    buildConfigField("boolean", "TELEMETRY_ENABLED", "false")
    // Q6: debug never reads the key — Noop sink doesn't call PostHog.setup.
    // Field present for symmetry so consumer code can read BuildConfig.POSTHOG_API_KEY
    // without a #ifdef-equivalent (though Noop never does).
    buildConfigField("String", "POSTHOG_API_KEY", "\"\"")
}
```

Note: the field is added to **both** debug and release — symmetric
`BuildConfig` fields prevent the surprising "BuildConfig.POSTHOG_API_KEY
doesn't exist in debug" error if someone references it from `src/main/`.
Curro's design today **doesn't reference it from `src/main/`** (the
release-only `FirebaseAndPostHogSdkBootstrap` is the sole consumer), so
the debug field is unused; the symmetry is hygiene for the next SF.

CI workflow implications: the existing `.github/workflows/ci.yml` runs
only `assembleDebug` today, so `POSTHOG_API_KEY` is never required.
When a future SF adds `assembleRelease` to CI, the workflow step adds
`POSTHOG_API_KEY: ${{ secrets.POSTHOG_API_KEY }}` in the env block of
that step. That SF owns the workflow edit; US-008 doesn't touch CI.

Reversibility: O(5 min) — switch to one source or the other if the dual
lookup becomes confusing. Option C is the most flexible; reverting to
A or B is mechanical.

See A6 (the release-time fail-fast on empty key), A11 (CI workflow
implications for a future release-in-CI SF).

**Q7 — Resolved: Option B — separate commits, both in the same push. The developer lands two commits at the end of US-008 implementation: (1) `feat(telemetry): SF-0.8 — Firebase + PostHog + INTERNET in release only`, then (2) `docs(spec): bump to v1.1 — telemetry kept, §12 revised`. Reverses the PM recommendation.**

Rationale: the PM's traceability argument (the bump *is* this SF's intent)
is real but secondary to **reviewability of the spec diff in isolation**.
Spec changes have a longer audit horizon than code: a year from now, when
Fran (or a future engineer) reads `docs/curro-spec-v1.0.md`'s git history
to understand "why does v1.1 say telemetry leaves the device?", they want
a commit whose diff is **just the spec change** — not a 30-file telemetry
+ spec mix in which the spec lines are dwarfed by `app/build.gradle.kts`
edits. `git log --follow docs/curro-spec-v1.0.md` should read as a
sequence of clean, single-purpose spec evolutions.

The PM's "the dependency direction is backwards" concern is the right
worry, resolved by **ordering**: the code commit lands first, the spec
commit lands second. The spec is bumped *after* the code that justifies
it exists in the tree. An external observer reading the spec commit alone
sees `git log --before=<spec-commit>` containing the SF-0.8 code commit —
the dependency direction reads correctly.

Both commits land **in the same push** (so CI sees them as one PR's worth
of work and the spec doesn't sit half-done if the developer is
interrupted). The developer's PR description references both:
"Spec v1.1 bump in commit (2); telemetry plumbing in commit (1)."

This also gives a clean rollback target if a privacy review surfaces a
problem with the spec wording itself without touching the code (revert
just the spec commit; the code still builds, the contract still holds —
the code is the *implementation* of the v1.1 §12 promise, and reverting
the §12 paragraph doesn't break anything *technically*; it just orphans
the wording-vs-code reconciliation until the next spec revision).

There is one exception to "two commits": if the spec wording itself
needs an iteration (a Fran review surfaces a Spanish-phrasing change),
the developer amends commit (2) — not commit (1). Commit (1) is the
*code* and is frozen once the implementation is correct; commit (2)
is the *language* and may iterate. This is the inverse of the usual
"don't amend code commits" rule applied to a multi-commit structure.

Recommended commit message bodies:

```
feat(telemetry): SF-0.8 — Firebase + PostHog + INTERNET in release only

Lands the telemetry stack per docs/briefs/US-008-telemetry-plumbing.md.

- Firebase Crashlytics + Analytics + PostHog declared in releaseImplementation
  only (Q1); debug APK has no SDK bytecode.
- INTERNET permission declared in app/src/release/AndroidManifest.xml only;
  debug APK has no INTERNET permission (verified via aapt dump permissions).
- TelemetrySink interface in domain/repository/; FirebaseAndPostHogSink (release)
  and NoopTelemetrySink (debug) implementations route every call through
  TelemetryGuardrail (Q4: ALLOWED_PROPS whitelist + value heuristic).
- TelemetryGuardrailTest pins the privacy contract as a CI-load-bearing
  fixture suite — every forbidden example rejects, every allowed example
  allows.
- TelemetryInitializer wired into CurroApp.onCreate(), gated on
  BuildConfig.TELEMETRY_ENABLED.
- No telemetry instrumented in any feature — that's later SFs' job.

Refs: docs/briefs/US-008-telemetry-plumbing.md (A1–A16 for the architect
decisions); CLAUDE.md → Privacy & telemetry; docs/master-plan.md → SF-0.8.

Co-Authored-By: Claude <noreply@anthropic.com>
```

```
docs(spec): bump to v1.1 — telemetry kept, §12 revised

The v1.0 spec said "nothing leaves the device". The project has since
opted to keep crash + product telemetry (Firebase Crashlytics/Analytics
+ PostHog) — see CLAUDE.md → Privacy & telemetry for the precedent.

v1.1 rewrites §12 to document the relaxation in writing:
- Audio, transcripts, message content, contact list / aliases, command
  history STILL never leave the device (unchanged).
- Crash reports + anonymised product-analytics events DO leave the
  device, gated on BuildConfig.TELEMETRY_ENABLED and the
  TelemetryGuardrail (whitelist of allowed event/property keys + a
  PII-shape heuristic; a violation breaks CI).
- INTERNET declared only in app/src/release/AndroidManifest.xml.
- Failure-log forwarding ("envíame los fallos", §9) still requires
  Fran's explicit toggle — and goes via a separate FailedCommandsExporter
  (TBD SF-8.x), NOT the TelemetrySink. The TelemetrySink never carries
  transcripts.

Implemented by US-008 (SF-0.8) in the preceding commit.

§5 "8 vs 7 funciones" and the §14 targetSdk cosmetic are deliberately
NOT in this revision — they queue for a separate spec-hygiene SF.

Co-Authored-By: Claude <noreply@anthropic.com>
```

(NOTE: this brief's commit — the PM-and-architect-pass commit — is
**neither** of those. This commit's scope is the brief itself; the
spec bump and the code land later, when `/implement-feature US-008`
runs.)

Reversibility: O(0) — commit structure is recorded once and read forever.
The decision affects only the developer's final two `git commit`
invocations.

See A12 (the relationship between code commits and spec evolution), A14
(the §5 / §14 items deliberately out of scope).

**Q8 — Resolved (sub-items below). The catch-all surfaced two genuinely consequential items (Q8d, Q8e) and six lower-cost items (Q8a–c, f–h). Each is resolved.**

---

**Q8a — Resolved: keep `firebaseBom = "33.7.0"`. Bump in a separate, audited chore SF if a security fix lands; do not bump opportunistically in US-008.**

Rationale: Firebase BOM 33.7.0 (Dec 2024 release window) ships
Crashlytics 19.4.x and Analytics 22.1.x — both stable, both fine for
Curro's surface (a single Activity that captures crashes + a handful
of anonymous events). The 33.x BOM line is a current LTS as of
2026-05-14; there's nothing in the 33.7 → "latest" window that materially
changes what Curro needs. SDK bumps are a separate operational concern
(security advisories, Play services compatibility) that should land in
a chore SF with their own changelog review — not folded into a privacy
SF where the version number isn't the load-bearing detail.

If the developer's pre-flight reveals 33.7.0 has a known CVE relevant
to Curro's surface, the developer bumps to the latest patched 33.x in
US-008 and updates this note. Otherwise: 33.7.0 ships.

**Q8b — Resolved: keep `posthog = "3.8.0"`.**

Same rationale as Q8a. PostHog Android 3.8.0 is current-enough for
Curro's anonymous event capture; bumping for the sake of bumping is
noise. If 3.8.0's manifest-contributed permissions or its session-replay
default-on (PostHog 3.x had session replay added in some release; check
at implementation time and **explicitly disable** in `PostHog.setup`
config — see A13) surfaces a concern, the developer bumps + adds a note.
Otherwise: 3.8.0 ships.

**Q8c — Resolved: keep `google-services = 4.4.2` and `firebase-crashlytics-plugin = 3.0.2`. Activate from the commented entries at L124–L125 of `libs.versions.toml`.**

Same rationale as Q8a/b. These two Gradle-plugin versions are the May
2026 stable line; bumping is a separate chore SF. The brief's
*In Scope* bullet that uncomments these is correct as written.

**Q8d — Resolved: the v1.1 §12 rewrite carves out an explicit failed-commands-transcript-with-consent paragraph. The transcript path is NOT the `TelemetrySink` — it's a separate `FailedCommandsExporter` (TBD SF-8.x). The §12 rewrite documents both the boundary and the carve-out; the carve-out's *implementation* is out of US-008 scope.**

Rationale: the user's instruction explicitly flagged this. The PM's
read is correct — the v1.0 §9 toggle "envíame los fallos" already
admits failed-commands logs leaving the device with Fran's consent;
the v1.0 §12 "Texto transcrito nunca sale" admits raw-utterance
transcripts never leaving. The two are consistent **only if**
"transcripciones de comandos fallidos" is treated as a separate
data class with its own user-controlled gate.

The v1.1 §12 wording (replacing the PM's proposed draft — small but
load-bearing additions):

```
## 12. Privacidad

Curro corre en el dispositivo. La promesa de privacidad sobre los datos
del usuario sigue siendo **nunca salen del dispositivo**:

- Audio grabado.
- Texto transcrito (de cualquier utterance, exitoso o no — ver excepción
  para fallos abajo).
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
(`TelemetryGuardrail`) que rechaza cualquier propiedad fuera de un
whitelist explícito de claves permitidas, y rechaza cualquier valor
con forma de transcripción, nombre completo, número de teléfono, email,
o cadena larga (> 32 caracteres). Una violación rompe CI en el commit
que la introduce. **Ninguna ruta de la telemetría técnica transporta
transcripciones de utterances.**

El permiso `INTERNET` se declara **únicamente** en
`app/src/release/AndroidManifest.xml`. La variante debug del APK no
tiene `INTERNET` en absoluto.

**Datos que solo salen con consentimiento explícito de Fran**, vía un
toggle del menú de configuración (sección 9):

- **Logs del registro de comandos fallidos**, incluyendo la
  transcripción del utterance que el modelo no supo mapear y la
  acción candidata (si existió). Útiles para depurar la app — qué cosas
  no entendió, qué función no existe en el catálogo. Estos logs **no
  pasan por el `TelemetrySink`** (Firebase + PostHog **nunca** los
  transportan); salen vía un canal dedicado, `FailedCommandsExporter`,
  cuya implementación llega en una SF futura (Fase 8). El toggle está
  desactivado por defecto. Las transcripciones de comandos *exitosos*
  nunca salen, independientemente del toggle.
```

The key additions vs the PM's draft:

1. `**Texto transcrito (de cualquier utterance, exitoso o no — ver excepción para fallos abajo).**` — names the carve-out at the
   top so the reader knows it exists before the bottom paragraph.
2. `**Ninguna ruta de la telemetría técnica transporta transcripciones de utterances.**` — pins the `TelemetrySink` boundary in writing.
3. `**Estos logs no pasan por el TelemetrySink**` — pins the
   separation between the analytics channel (Firebase + PostHog) and
   the failure-export channel (`FailedCommandsExporter`, TBD).
4. `cuya implementación llega en una SF futura (Fase 8)` — names the
   deferral so the reader doesn't think US-008 ships the exporter.
5. `Las transcripciones de comandos exitosos nunca salen, independientemente del toggle.` — closes the reading "the toggle
   enables transcripts to leave" by explicitly bounding it to *failed*
   commands.

This wording is the architect's recommendation; the developer commits
this verbatim in the spec-bump commit (Q7-Resolved).

The `FailedCommandsExporter` itself is **out of US-008 scope** — see A7
for the deferral note.

**Q8e — Resolved: `ACCESS_NETWORK_STATE` (and likely `WAKE_LOCK`) DO get pulled transitively by Firebase + PostHog. The AC "exactly one INTERNET" is relaxed to "exactly INTERNET, ACCESS_NETWORK_STATE, and WAKE_LOCK — and nothing else"; the merged release manifest is the source of truth and the developer documents its `aapt dump permissions` output in the PR.**

Rationale: the PM's read is correct. Firebase Analytics 22.x's library
manifest contributes `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`,
and `AD_ID` (the last of which we disable explicitly — see A13).
PostHog 3.x's library manifest contributes `INTERNET` and
`ACCESS_NETWORK_STATE`. AGP's manifest merger unions all of these into
the release variant's merged manifest. The brief's AC as written
("exactly one INTERNET") would fail.

Updated AC (replacing the brief's existing "exactly one INTERNET" item):

```
- [ ] `./gradlew assembleRelease` succeeds and produces an APK whose
  `aapt dump permissions app/build/outputs/apk/release/app-release.apk`
  output contains:
    - exactly one `android.permission.INTERNET` line, and
    - `android.permission.ACCESS_NETWORK_STATE` (transitively from Firebase
      Analytics + PostHog — read-only, used by both SDKs to gate event
      uploads on connectivity)
    - `android.permission.WAKE_LOCK` (transitively from Firebase
      Analytics; used internally for background scheduling)
    - NOTHING ELSE — no AD_ID (disabled per A13), no RECEIVE_BOOT_COMPLETED,
      no FOREGROUND_SERVICE (added by later SFs), no other transitive permission.
  The developer documents the exact `aapt dump permissions` output in the
  PR description so a future reader sees the merged-manifest state of
  the privacy boundary.
- [ ] `aapt dump permissions app/build/outputs/apk/debug/app-debug.apk`
  output contains **zero** permission lines (debug has none of the above —
  the SDKs are in releaseImplementation only per Q1).
```

The `WAKE_LOCK` is the one most likely to surprise the developer; flag
it in the PR. None of `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`
require runtime consent (all install-time normal permissions); the user
never sees a prompt.

**Q8f — Resolved: NO. Do not add `tools:node="remove"` for `ACCESS_NETWORK_STATE` in `src/main/`.**

Rationale: the permissions `Q8e` documents are *deliberately* in the
release manifest — Firebase + PostHog actually need them to gate event
uploads on connectivity. Removing `ACCESS_NETWORK_STATE` would either
break the SDKs or cause them to assume always-online and uselessly burn
battery retrying uploads on a no-network device. The right shape is
"document what's merged, accept it, move on" (Q8e), not "remove
permissions the SDKs need".

Same posture as Q2 (no defensive `src/debug/AndroidManifest.xml`) — the
`aapt dump permissions` AC catches any regression; pre-empting with
`tools:node="remove"` invites someone to drift the wrong direction.

**Q8g — Resolved: the release `FirebaseAndPostHogSdkBootstrap.initialize()` throws `IllegalStateException("POSTHOG_API_KEY missing — see docs/briefs/US-008…")` if `BuildConfig.POSTHOG_API_KEY` is empty. The release build refuses to function rather than silently no-op PostHog.**

Rationale: see Q6-Resolved (the symmetric `buildConfigField` + the
fail-fast). The PM's worry that `PostHog.setup(context, null)` would
silently no-op is correct — that's a black-hole failure mode and the
opposite of what we want. The fail-fast catches the misconfiguration
the first time the developer (or CI) runs `assembleRelease` without
the key set; the fix is one line.

Crashlytics has no equivalent key — the Firebase project is
`google-services.json`, which Q3 already gates. If the JSON is missing,
the `google-services` plugin itself fails at apply time (not at
runtime), so Q3's mechanism handles Crashlytics' "missing config" case
upstream.

The debug `NoopSdkBootstrap.initialize()` does nothing — `BuildConfig.POSTHOG_API_KEY`
is `""` and unread. No fail-fast in debug.

**Q8h — Resolved: `@Inject lateinit var telemetryInitializer: TelemetryInitializer` in `CurroApp`. Matches US-002 precedent.**

Rationale: Hilt allows both `@Inject lateinit var` field injection and
(via `@AndroidEntryPoint`) constructor-style for `Application` — but
`Application` cannot be constructor-injected (Android instantiates it
itself before Hilt is ready). `@Inject lateinit var` is the only working
shape for a member that Hilt provides into `Application`. US-002's brief
documented this precedent and the existing `CurroApp.kt` follows it
already (no member injections yet, but the pattern is established).

`CurroApp.onCreate()` injection sequence (resolved):

```kotlin
@HiltAndroidApp
class CurroApp : Application() {

    @Inject lateinit var telemetryInitializer: TelemetryInitializer

    override fun onCreate() {
        super.onCreate()
        // Hilt's generated Hilt_CurroApp.onCreate() runs as part of
        // super.onCreate() — by the time we get here, telemetryInitializer
        // is injected and ready. A5 details the order; we never call
        // telemetryInitializer before super.onCreate().
        telemetryInitializer.initialize()
    }
}
```

See A5 (the `TelemetryInitializer` + `SdkBootstrap` shape and the call
order), A8 (the explicit lifecycle pin).

## Architect's notes & decisions

These are the load-bearing telemetry / privacy decisions the architect locked
in for SF-0.8. Each note is referenced from the Scope / Specification /
Acceptance Criteria / Q-Resolved sections above, and is meant to be cited
verbatim by later-SF briefs that consume the telemetry contract (every
event-emitting SF in Phases 2 / 3 / 5 / 7 / 8, plus the SF that eventually
ships `FailedCommandsExporter`). **All of them must be settled by the time
`/implement-feature US-008` writes the first `TelemetrySink` line** — they
propagate to every later SF that `@Inject`s the sink or adds an entry to
`ALLOWED_PROPS`; a later reversal means re-touching every event emitter.

**A1. Q1 + Q5 are coupled — the privacy boundary IS the build configuration.**
Q1 (release-only deps) and Q5 (source-set-split Hilt modules) are not two
independent choices; they are one choice expressed in two places. The
intent: **the debug variant of the APK is structurally incapable of calling
Firebase or PostHog because the bytecode is not there and the Hilt graph
does not bind it.** A future developer who reads `data/telemetry/` looking
for a runtime branch (`if (BuildConfig.TELEMETRY_ENABLED) …`) finds none —
the branch is *the build*. This is the strongest form of "what cannot run
cannot leak". The runtime `BuildConfig.TELEMETRY_ENABLED` check in
`TelemetryInitializer` (Q1's belt-and-braces piece) is the **kill switch**,
not the gate; the gate is the classpath.

Downstream SFs that emit telemetry should never see this distinction —
they `@Inject val telemetry: TelemetrySink` and call `telemetry.event(...)`.
What they get back (Noop in debug, FirebaseAndPostHog in release) is
opaque. **No event-emitting SF reads `BuildConfig.TELEMETRY_ENABLED`
directly**; that flag is owned by `TelemetryInitializer` exclusively. If a
later SF wants conditional behaviour (e.g. "only emit this event in
release"), it does so by *adding* the event to the registry — the
registry's release-bound consumer is `FirebaseAndPostHogSink`, and the
debug-bound `NoopTelemetrySink` will log-and-drop. There is no read of
the flag outside `data/telemetry/`. Pin this. Reversibility: O(15 min)
combined with Q1/Q5 (see their resolutions).

**A2. The merged-manifest output IS the source of truth for shipped permissions.**
Q8e relaxes the AC. To verify in practice, the developer runs:

```bash
./gradlew assembleRelease
./gradlew assembleDebug

# Source of truth — the merged manifest, not src/release/AndroidManifest.xml
$ANDROID_HOME/build-tools/<latest>/aapt dump permissions \
    app/build/outputs/apk/release/app-release.apk
# expected output:
#   package: com.curro.app
#   uses-permission: name='android.permission.INTERNET'
#   uses-permission: name='android.permission.ACCESS_NETWORK_STATE'
#   uses-permission: name='android.permission.WAKE_LOCK'

$ANDROID_HOME/build-tools/<latest>/aapt dump permissions \
    app/build/outputs/apk/debug/app-debug.apk
# expected output:
#   package: com.curro.app
#   (no uses-permission lines)
```

The developer pastes both outputs verbatim into the PR description. Any
permission appearing in the release output that isn't on the {`INTERNET`,
`ACCESS_NETWORK_STATE`, `WAKE_LOCK`} set is a privacy review trigger
(architect re-review required before merge). Adding a permission later
goes through that same review.

The intermediate merged-manifest XML at
`app/build/intermediates/merged_manifests/release/AndroidManifest.xml` is
also useful — it shows which library contributed each permission via
`@android:authorities` or comment annotations. Worth a glance during the
first US-008 build to learn which SDK contributes which permission;
future SFs that consider new SDKs can use the same method.

**A3. `TelemetryGuardrail` has no escape hatch — and that's a feature, not a bug.**
Q4 resolves to whitelist + heuristic with **no `@Suppress("PII")` annotation,
no `IS_BOUNDED_ID` flag, no per-event opt-out**. Every escape hatch is a
future foot-gun: a developer in a hurry marks a call `@Suppress` to ship,
the privacy reviewer doesn't see the suppression because annotations are
easy to scroll past, and a transcript leaks.

The "but my legitimate 60-character UUID gets rejected" case has a
non-escape-hatch answer: **add the key to the whitelist with an explicit
value-shape validator**. Example: if a future SF needs to log a
`request_id` (a 36-character hyphenated UUID, which would fail the
`MAX_VALUE_LEN = 32` heuristic):

```kotlin
// In ALLOWED_PROPS — narrow the value contract:
"function_called" to setOf("action", "confidence_bucket", "latency_ms", "from_warm", "request_id"),

// And in valueHeuristic, the UUID shape is allowed-by-pattern:
private val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
// … check UUID before MAX_VALUE_LEN, return Allow if it matches …
```

This forces the privacy reviewer to look at the *shape* of every long
value, not at a `@Suppress` line. The friction is the point.

**A4. The PII guardrail test fixture is the FIRST privacy-boundary test in the project — document the pattern.**
`TelemetryGuardrailTest` is the first test that codifies "what cannot leave
Curro". It is also the **template every later privacy-boundary test
follows**. The shape (JUnit 5 + parameterised cases via a data class +
`@ParameterizedTest @MethodSource`):

```kotlin
// app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailTest.kt
package com.curro.app.data.telemetry

import com.curro.app.data.telemetry.TelemetryGuardrail.GuardrailResult.Allow
import com.curro.app.data.telemetry.TelemetryGuardrail.GuardrailResult.Reject
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@DisplayName("TelemetryGuardrail — privacy boundary")
class TelemetryGuardrailTest {

    /** A single event-check fixture row. */
    data class EventCase(
        val label: String,
        val name: String,
        val props: Map<String, Any>,
        val expectAllow: Boolean,
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventCases")
    fun `event check decisions`(case: EventCase) {
        val result = TelemetryGuardrail.check(case.name, case.props)
        if (case.expectAllow) {
            assertInstanceOf(Allow::class.java, result)
        } else {
            assertInstanceOf(Reject::class.java, result)
        }
    }

    companion object {
        @JvmStatic
        fun eventCases(): Stream<Arguments> = Stream.of(
            // FORBIDDEN — full name (two capital words)
            Arguments.of(EventCase("reject: full name in 'recipient'",
                "call_started", mapOf("recipient" to "María García"), expectAllow = false)),
            Arguments.of(EventCase("reject: full name in 'by'",
                "crash", mapOf("by" to "Pepe Martínez"), expectAllow = false)),

            // FORBIDDEN — phone number
            Arguments.of(EventCase("reject: international phone",
                "called", mapOf("number" to "+34 600 123 456"), expectAllow = false)),
            Arguments.of(EventCase("reject: digits-only phone",
                "called", mapOf("number" to "600123456"), expectAllow = false)),

            // FORBIDDEN — email
            Arguments.of(EventCase("reject: email",
                "crash", mapOf("contact" to "fran@example.com"), expectAllow = false)),

            // FORBIDDEN — transcript-shaped value on a whitelisted key (e.g. 'action' is whitelisted but a 50-char value is not)
            Arguments.of(EventCase("reject: transcript-shaped action",
                "function_called", mapOf("action" to "Te espero a las siete en el médico"), expectAllow = false)),

            // FORBIDDEN — unknown event name
            Arguments.of(EventCase("reject: unknown event",
                "totally_unknown_event", mapOf("key" to "value"), expectAllow = false)),

            // FORBIDDEN — unknown prop key on a known event
            Arguments.of(EventCase("reject: unknown prop key on known event",
                "function_called", mapOf("recipient_name" to "Pepito"), expectAllow = false)),

            // FORBIDDEN — forbidden keys via the "unknown prop key" path (no longer needs a separate blocklist)
            Arguments.of(EventCase("reject: 'message' key not on whitelist",
                "function_called", mapOf("message" to "ok"), expectAllow = false)),
            Arguments.of(EventCase("reject: 'transcript' key not on whitelist",
                "function_called", mapOf("transcript" to "qué hora es"), expectAllow = false)),

            // ALLOWED — function call (every prop key whitelisted, every value short and shape-safe)
            Arguments.of(EventCase("allow: function_called canonical",
                "function_called", mapOf(
                    "action" to "tell_time",
                    "confidence_bucket" to "high",
                    "latency_ms" to 380,
                    "from_warm" to true), expectAllow = true)),

            // ALLOWED — STT failure
            Arguments.of(EventCase("allow: stt_failed NO_MATCH",
                "stt_failed", mapOf("error_code" to "NO_MATCH"), expectAllow = true)),
            Arguments.of(EventCase("allow: stt_failed SPEECH_TIMEOUT",
                "stt_failed", mapOf("error_code" to "SPEECH_TIMEOUT"), expectAllow = true)),

            // ALLOWED — empty props
            Arguments.of(EventCase("allow: app_open empty props",
                "app_open", emptyMap<String, Any>(), expectAllow = true)),

            // …
        )
    }
}
```

Adding a forbidden / allowed example = adding one row to the `companion
object`'s `Stream.of(...)`. Reviewing the diff = reading the `label`s.
This is the canonical pattern for privacy-boundary tests in Curro; later
SFs that add to `ALLOWED_PROPS` MUST extend this fixture in the same PR.

**A5. `TelemetryInitializer` is `src/main/`; `SdkBootstrap` interface is `src/main/`; the two impls are per-variant. `TelemetryInitializer` contains zero SDK references.**

Q5's source-set split puts the *sinks* per-variant; it must also put the
*SDK bootstrap* per-variant. The wrong shape: `TelemetryInitializer` lives
in `src/main/` and conditionally calls `PostHog.setup` and
`FirebaseApp.initializeApp` — but those references don't compile in debug
under Q1. The right shape:

```kotlin
// app/src/main/java/com/curro/app/data/telemetry/SdkBootstrap.kt
package com.curro.app.data.telemetry

/** Lifecycle hook for telemetry SDK initialisation. Per-variant impl. */
interface SdkBootstrap {
    /** Called from CurroApp.onCreate(). Idempotent — safe to call once at app start. */
    fun initialize()
}

// app/src/main/java/com/curro/app/data/telemetry/TelemetryInitializer.kt
package com.curro.app.data.telemetry

import android.content.Context
import android.util.Log
import com.curro.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sdkBootstrap: SdkBootstrap,
) {
    fun initialize() {
        if (!BuildConfig.TELEMETRY_ENABLED) {
            Log.d("CurroTelemetry", "TELEMETRY_ENABLED=false — initializer is a no-op")
            return
        }
        sdkBootstrap.initialize()
        Log.d("CurroTelemetry", "telemetry initialised (variant=${BuildConfig.BUILD_TYPE})")
    }
}

// app/src/debug/java/com/curro/app/data/telemetry/NoopSdkBootstrap.kt
package com.curro.app.data.telemetry

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoopSdkBootstrap @Inject constructor() : SdkBootstrap {
    override fun initialize() { /* intentionally empty */ }
}

// app/src/release/java/com/curro/app/data/telemetry/FirebaseAndPostHogSdkBootstrap.kt
package com.curro.app.data.telemetry

import android.content.Context
import com.curro.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.posthog.android.PostHog
import com.posthog.android.PostHogAndroidConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAndPostHogSdkBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
) : SdkBootstrap {

    override fun initialize() {
        FirebaseApp.initializeApp(context)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        FirebaseAnalytics.getInstance(context).apply {
            setAnalyticsCollectionEnabled(true)
            // A13: AdId collection OFF — Curro has no ads, no AdId use.
            // Requires applying com.google.android.gms:play-services-ads-identifier OR
            // setting <meta-data> in the manifest; we use the latter (A13).
        }
        val posthogKey = BuildConfig.POSTHOG_API_KEY
        check(posthogKey.isNotEmpty()) {
            "POSTHOG_API_KEY is empty — set it in local.properties or as the " +
                "POSTHOG_API_KEY env var. See docs/briefs/US-008-telemetry-plumbing.md " +
                "Q6-Resolved + A6."
        }
        PostHog.setup(context, PostHogAndroidConfig(apiKey = posthogKey).apply {
            // A13: disable session replay (PostHog Android 3.x default may enable).
            sessionReplay = false
            // … other Curro-specific config (capture rates, etc.) lands per SF that emits.
        })
    }
}
```

`TelemetryInitializer` has the SDK-agnostic flow ("if the flag is on, ask
the bootstrap to run; log"), `SdkBootstrap` per-variant has the SDK-specific
calls. `CurroApp` injects `TelemetryInitializer` and calls
`initialize()` once. Clean, testable, no `if (variant) …` in
`src/main/`. See A8 for the call order.

**A6. Release-time fail-fast on empty `POSTHOG_API_KEY`.**

Q6 + Q8g resolve this: `FirebaseAndPostHogSdkBootstrap.initialize()` calls
`check(posthogKey.isNotEmpty())` (see snippet in A5). A release build
with no key set throws `IllegalStateException` on first launch with a
developer-readable message pointing at the brief. The crash IS the error
report — it's caught by Firebase Crashlytics? No: Crashlytics is initialised
*after* the PostHog check in the snippet above, so the crash goes to
Android's standard `Log.e` + system UI. **Re-order the call so PostHog
runs first** (the snippet does this correctly). If the developer wants
Crashlytics to also report a missing-PostHog-key crash, that's a
chicken-and-egg case the developer accepts — the Log.e is sufficient.

Alternative not chosen: silent fallback to a stub `NoopTelemetrySink` in
release. Rejected because it's exactly the "silent telemetry no-op" mode
Q3 + Q6 already refused — the failure should be visible.

**A7. `FailedCommandsExporter` is OUT of US-008 scope.**

Q8d's §12 v1.1 rewrite mentions `FailedCommandsExporter` as the channel
for transcript export (the user-controlled toggle path). **US-008 ships
none of that channel.** It ships:

- The `TelemetrySink` — which **never** carries transcripts. The
  guardrail enforces this; the test pins it; the v1.1 §12 wording
  documents it.
- That's it. No `FailedCommandsExporter` interface, no `data/export/`
  package, no stub.

The future SF that lands `FailedCommandsExporter` (call it SF-8.x —
sequenced with the config-menu "envíame los fallos" toggle) makes its own
architecture decisions: the transport mechanism (email? a Curro-controlled
HTTPS endpoint? Firebase Storage with a Cloud Function gating? out of
scope to decide here), the consent flow (the toggle UI), the anonymisation
pipeline (which fields are scrubbed from the log entries), the audit
trail. **What US-008 commits to is the negative:** Firebase + PostHog
never carry transcripts. The positive (the exporter that *does*) is a
separate brief, separate architect pass, separate v1.x spec implication.

Document the deferral in the v1.1 §12 wording (done in Q8d) so a reader
doesn't expect US-008 to deliver the carve-out. The brief's *Out of Scope*
list mentions this in passing; this note pins it.

**A8. Initialisation order: `super.onCreate()` → Hilt's generated `Hilt_CurroApp.onCreate()` runs as part of super → `telemetryInitializer.initialize()`.**

Pin the order. `Application.onCreate()` runs `super.onCreate()` first;
`Hilt_CurroApp` (which `CurroApp` extends, via `@HiltAndroidApp`) runs its
component initialisation in *its* `onCreate()`, which is called by
`super.onCreate()`. By the time control returns to `CurroApp.onCreate()`,
`@Inject lateinit var telemetryInitializer` is set. **Never call
`telemetryInitializer.initialize()` before `super.onCreate()`** — it
would `UninitializedPropertyAccessException`.

```kotlin
@HiltAndroidApp
class CurroApp : Application() {
    @Inject lateinit var telemetryInitializer: TelemetryInitializer

    override fun onCreate() {
        super.onCreate()                       // 1. Hilt initialises members
        telemetryInitializer.initialize()      // 2. We use them
    }
}
```

A future SF that adds more injected members (`@Inject lateinit var
crashlyticsKeyProvider`) follows the same pattern: all injections used
after `super.onCreate()`.

`TelemetryInitializer.initialize()` is **synchronous** but **fast**
(< 50 ms on the Redmi 15 per Firebase + PostHog docs). It does **not**
block the main thread meaningfully; both SDKs do their heavy lifting
internally on background threads. If a future SF measures > 100 ms on
the main thread, that SF moves the call to a `Dispatchers.IO` launch
inside `CurroApp.applicationScope` (US-002's `@ApplicationScope` —
which is `Main.immediate`, so the launch dispatches off-main via
`withContext(io)`).

**A9. `setAnalyticsCollectionEnabled` / `PostHog.optOut()` plumbed into `TelemetryInitializer` as a future-proofing seam — but no toggle UI yet.**

A future SF (the spec §9 config menu's "envíame los fallos" toggle, or
a hypothetical kill-switch in case of a privacy incident) needs to be
able to disable telemetry at runtime *in release*. The plumbing:

```kotlin
// app/src/main/java/com/curro/app/data/telemetry/TelemetryInitializer.kt
// (additions to the snippet in A5)
@Singleton
class TelemetryInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sdkBootstrap: SdkBootstrap,
) {
    fun initialize() { /* … as A5 … */ }

    /** Future SF hook — disable telemetry collection at runtime (release only). */
    fun setCollectionEnabled(enabled: Boolean) {
        if (!BuildConfig.TELEMETRY_ENABLED) return
        sdkBootstrap.setCollectionEnabled(enabled)
    }
}

// SdkBootstrap interface (additions):
interface SdkBootstrap {
    fun initialize()
    fun setCollectionEnabled(enabled: Boolean)
}

// FirebaseAndPostHogSdkBootstrap (additions):
override fun setCollectionEnabled(enabled: Boolean) {
    FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    if (enabled) PostHog.optIn() else PostHog.optOut()
}

// NoopSdkBootstrap: setCollectionEnabled is no-op.
```

**No SF-0.8 caller uses `setCollectionEnabled`.** It is plumbing for
the future. Document it; ship it; do not invoke it. The future SF that
adds the toggle UI also adds the call from its ViewModel. The plumbing
costs ~10 lines and pre-empts a future architect re-review.

**A10. `BuildConfig.TELEMETRY_ENABLED` is the DEFAULT, not the only switch.**

The name `TELEMETRY_ENABLED` reads as a binary kill switch. The
semantics are "is telemetry on **by default**?" — a future
`SettingsRepository`-backed override (the "always-confirm" toggle's
sibling) can disable it at runtime even if the build flag is true. The
name was set by US-001 and is kept (renaming would force a touch of
every reference); the docstring clarifies:

```kotlin
/**
 * The DEFAULT collection state for the build variant.
 * - `false` in debug — no SDKs, no events, no network.
 * - `true` in release — SDKs initialise; runtime override via
 *   TelemetryInitializer.setCollectionEnabled(false) can downgrade
 *   this (future SF: the config menu's "envíame los fallos" toggle,
 *   or an emergency kill switch).
 *
 * The name reads as a kill switch; the semantics are a default.
 * See docs/briefs/US-008 A10.
 */
buildConfigField("boolean", "TELEMETRY_ENABLED", "true")
```

Place this KDoc as a comment in `app/build.gradle.kts` near the
`buildConfigField` line so the next developer reads it.

**A11. CI workflow implications: with Q1+Q5, CI's `assembleDebug` is unaffected; a future release-in-CI SF requires `GOOGLE_SERVICES_JSON` (already wired) + `POSTHOG_API_KEY` (not yet wired).**

The existing `.github/workflows/ci.yml` runs `assembleDebug` only. With
Q1 (release-only deps) and Q3 (conditional plugin apply on the JSON
file's presence), CI's debug build:

- does NOT need `google-services.json` (file absent → plugin not applied
  → no telemetry wiring → debug variant doesn't use it anyway).
- does NOT need `POSTHOG_API_KEY` (the buildConfigField defaults to `""`;
  debug Noop sink doesn't read it).
- does NOT need the `INTERNET` permission (release-only manifest overlay).
- does NOT need any new CI workflow edit.

A **future** SF that adds `assembleRelease` to CI (signed-AAB publishing,
say) requires:

1. The existing `Decode google-services.json` step is already wired
   (`.github/workflows/ci.yml` L34–L43) — it no-ops without the secret
   today; with the secret set, it decodes to `app/google-services.json`.
2. **A new** `POSTHOG_API_KEY` env var on the relevant step:
   ```yaml
   - name: Build release
     env:
       POSTHOG_API_KEY: ${{ secrets.POSTHOG_API_KEY }}
     run: ./gradlew assembleRelease
   ```
3. Both secrets must be added to the repo Settings → Secrets and variables → Actions.

That SF owns the workflow edit. US-008 doesn't touch CI.

**A12. Spec evolution: code commits and spec bumps move in lockstep but live in separate commits.**

Q7 resolved: two commits, code then spec, in the same push. This is a
*pattern*, not a one-off — every future SF that requires a spec change
follows it. The pattern (recorded for the developer):

- The SF's code commit subject: `feat(<scope>): SF-<n> — <summary>`.
- The spec bump's commit subject: `docs(spec): bump to v<x.y> — <reason>`.
- The spec commit body cross-references the code commit's SF ID and
  cites the §section being touched.
- The two commits land in the same push so `main` never has a
  partially-bumped state (code present without spec doc; or spec doc
  present without code).
- If the spec change is genuinely independent of any SF (a typo fix,
  a clarification with no code consequence), it ships in a `docs(spec):`
  commit alone with no preceding code commit. Those are the only spec
  commits that ship alone.

`git log --follow docs/curro-spec-v1.0.md` then reads as a clean
sequence of spec evolutions with traceable code links.

**A13. Disable PostHog session replay; disable Firebase AdId collection. Explicit defaults.**

Firebase Analytics 22.x collects the Android Advertising ID (`AD_ID`) by
default and contributes the `com.google.android.gms.permission.AD_ID`
permission to the merged manifest. PostHog Android 3.x added session
replay; depending on the version's default, it may be enabled
out-of-the-box.

**Both must be explicitly disabled** for Curro:

- **AD_ID disabled** via a manifest entry in
  `app/src/release/AndroidManifest.xml`:

  ```xml
  <application>
      <meta-data
          android:name="google_analytics_adid_collection_enabled"
          android:value="false" />
  </application>
  ```

  Plus a `tools:node="remove"` for the AD_ID permission contributed by
  the SDK:

  ```xml
  <uses-permission
      android:name="com.google.android.gms.permission.AD_ID"
      tools:node="remove" />
  ```

  (This is the **one** legitimate `tools:node="remove"` Curro needs —
  the Q8f rationale doesn't apply here because AD_ID is *not* a
  permission the SDK needs to function for our use case, just one it
  defaults to. We refuse it.)

- **PostHog session replay disabled** in `PostHogAndroidConfig.apply { sessionReplay = false }` (see A5 snippet).

The updated `app/src/release/AndroidManifest.xml` shape (replacing the
brief's *In Scope* sketch):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Release-variant manifest overlay (AGP merges this onto src/main).

    INTERNET — required by Firebase Crashlytics / Analytics + PostHog.
    AD_ID — refused via tools:node="remove" + the meta-data flag below.
    ACCESS_NETWORK_STATE / WAKE_LOCK — contributed by the SDKs and accepted
      (see A2 / Q8e). Not declared here; AGP merges them in.

    Privacy boundary: docs/curro-spec-v1.0.md §12 (v1.1).
-->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission
        android:name="com.google.android.gms.permission.AD_ID"
        tools:node="remove" />

    <application>
        <meta-data
            android:name="google_analytics_adid_collection_enabled"
            android:value="false" />
    </application>

</manifest>
```

Note the `xmlns:tools="http://schemas.android.com/tools"` declaration —
required for the `tools:node` attribute to resolve. Add to the AC: the
release APK's `aapt dump permissions` output must NOT contain
`com.google.android.gms.permission.AD_ID` (A2 enumerates what should
appear; AD_ID is explicitly excluded).

**A14. The §5 / §14 spec items are queued, NOT folded into v1.1.**

The brief flags two other spec deviations the master-plan tracks: §5
"8 vs 7 funciones" cosmetic, §14 `targetSdk` cosmetic. **Neither lands in
US-008's spec commit.** The v1.1 bump in this SF resolves §12 *only*.
The §5 / §14 items stay queued for a separate spec-hygiene SF.

Reason: scope hygiene. US-008's privacy focus deserves a spec commit
whose `git diff` is just the §12 paragraph. Folding in §5 (a catalog
scope decision, mildly substantive) and §14 (a one-line doc-vs-code
drift) would dilute the privacy commit and entangle unrelated review
threads. The spec commit's Revision-history row says "§12 revised" and
nothing more.

The §5 / §14 items show up in a future `docs(spec): bump to v1.2 — spec
hygiene` commit (or whichever bump touches them). The brief's *Out of
Scope* list already mentions both; A14 pins it.

**A15. Detekt rule deferral — "never call `TelemetrySink.event(...)` outside an allowed call graph" is a future tooling SF.**

A custom detekt rule that enforces "the only files that may invoke
`telemetrySink.event(...)` are `data/telemetry/` itself, an
`@Inject`-receiving consumer in `assistant/` / `handler/` /
`presentation/`, and an explicit allowlist of future call sites" could
catch a category of mistakes that US-008's runtime guardrail doesn't:
calls *inside* `data/local/` or `data/notification/` that route around
the assistant coordinator and bypass review.

**US-008 does not ship this rule.** Reason: US-003 punted custom detekt
rules to a future tooling SF (the same one that will eventually host
the No-Double-Padding rule and the `dynamicColor`-banned rule). When
that SF lands (call it SF-tooling.1), it adds this rule. Until then,
the runtime `TelemetryGuardrail` + the test fixture + the source-set
split + the privacy review at PR time are the load-bearing guards.

Document the intent so the punt is named, not forgotten:
- Rule: `TelemetryOutsideAllowedCallGraph` (working name).
- Forbids: `telemetrySink.event(...)`, `telemetrySink.logCrash(...)`,
  `telemetrySink.setUserProperty(...)` from files matching paths
  `app/src/main/java/com/curro/app/data/local/**`,
  `app/src/main/java/com/curro/app/data/notification/**`,
  `app/src/main/java/com/curro/app/data/contacts/**`,
  `app/src/main/java/com/curro/app/data/telephony/**`,
  `app/src/main/java/com/curro/app/data/apps/**`,
  `app/src/main/java/com/curro/app/data/ml/**`.
- Reason: the *data* layer is the layer that touches PII (messages,
  contacts, transcripts, calls); routing telemetry from there is the
  one place a developer might accidentally co-pass a PII value
  alongside an `error_code`.
- Allowed: `assistant/`, `handler/`, `presentation/`, `service/`,
  `domain/usecase/`, and `data/telemetry/` itself.

This belongs in `tools/detekt-rules/` when that directory lands.

**A16. Reversibility checkpoint.**

Of the eight Q resolutions (Q8 counted as one):

| Q | Resolution | Reversal cost |
|---|---|---|
| Q1 | Release-only deps + runtime kill switch | O(30 min) — flip `releaseImplementation` to `implementation`, drop Q5's source-set split |
| Q2 | No defensive debug manifest | O(5 min) — drop a `src/debug/AndroidManifest.xml` later if a leak appears |
| Q3 | Conditional plugin apply on JSON-present | O(10 min) — collapse to unconditional `alias(...)` and commit a stub (rejected, but mechanically possible) |
| Q4 | Whitelist + heuristic, no escape hatch | O(15 min) — relax the registry; widen the heuristic |
| Q5 | Source-set-split Hilt modules | O(20 min) — collapse to a single module with a runtime branch (loses Q1's compile-time guarantee) |
| Q6 | local.properties first, env-var fallback, fail-fast | O(5 min) — pick one source |
| Q7 | Two commits (code, then spec), one push | O(0) — commit structure is recorded once |
| Q8 | Sub-items resolved per Q8a–h | O(varies) — each sub-item is O(< 15 min) independently |

The most expensive reversal is Q1+Q5 (they reverse together). Pin the
eight choices; the reversal cost is bounded.

### Owner split

**PM (`android-product-analyst`)** owns Metadata / Summary / Scope / User
Flows / Acceptance Criteria / Design Notes / Senior-UX & Copy / Performance
Considerations / Testing Requirements / Cross-SF dependencies / Spec
ambiguities / Reality cross-check / Revision History. PM authored the
initial draft (1371 lines) and the eight Open Questions with PM
recommendations.

**Architect (`android-architect`)** reviewed the brief, **resolved Q1–Q8**
(see *Open Questions → Resolved* blocks; Q8 expanded into Q8a–Q8h),
authored the *Architect's notes & decisions (A1–A16)* appendix, and
tightened the Specification / Acceptance Criteria where the resolved
choices required it (notably: the permission-AC relaxation for Q8e, the
`src/release/AndroidManifest.xml` expansion for the AD_ID refusal in A13,
the spec §12 v1.1 wording for Q8d, the commit structure for Q7). The
architect's role here is to lock in the privacy boundary as **build
configuration + source-set layout + Hilt graph shape**, not as a runtime
check — that distinction propagates to every event-emitting SF.

**`android-developer`** implements per the Execution plan in
*Implementation Notes → Order of operations* (the existing PM section
stands, refined where Q-resolutions tightened it);
**`ondevice-ai-engineer`** is **not in this SF's loop** (US-008 doesn't
touch the LLMs); **`voice-pipeline-engineer`** is **not in this SF's loop**
(US-008 doesn't touch the FSM or STT/TTS); **`kotlin-reviewer`** reads
the resulting Kotlin for hygiene (no SDK references in `src/main/`, the
guardrail's regex compilation is object-level, conformance with A1–A16);
**`android-qa-specialist`** reviews `TelemetryGuardrailTest`'s fixture
coverage against A4's pattern.

When the future SF lands that ships `FailedCommandsExporter` (the §9
toggle channel, A7), the architect re-engages — that SF makes its own
architecture decisions about the transport, the consent flow, and the
anonymisation pipeline. US-008 commits only to the negative (the
TelemetrySink never carries transcripts); the positive comes later.

### Why the architect review was needed (and is now complete)

Eight decisions in *Open Questions* shape every future telemetry call site:

- **Q1** + **Q5** together set the **structural privacy boundary** — debug
  cannot call the SDKs because they aren't there; Hilt cannot bind them
  because the module isn't in scope.
- **Q2** asks how paranoid to be about the boundary; **NO** because Q1
  already structurally precludes leaks.
- **Q3** sets the build-system shape for the JSON file (conditional apply
  on presence) so `assembleDebug` on a fresh clone keeps US-001's
  "fresh-clone-green" invariant.
- **Q4** locks the **PII guardrail** as a whitelist + heuristic with no
  escape hatch — every later event emitter inherits this contract.
- **Q6** sets the secret-supply mechanism (local.properties + env-var
  fallback + release-time fail-fast) so release builds without a key
  *crash visibly* rather than silently no-opping.
- **Q7** sets the commit pattern (two commits, code then spec, one push)
  for every future spec-bump SF.
- **Q8** surfaces the eight nuances the PM flagged — most consequential
  Q8d (the §12 v1.1 carve-out for failed-commands-with-consent) and Q8e
  (the merged-manifest permission surface beyond INTERNET).

Each is mechanical to implement and **hard to reverse after Phase 1+
features start `@Inject`-ing `TelemetrySink`** — the runtime call shape
is `telemetry.event(...)`, but its *meaning* (gated where? logged where?
PII-checked how?) depends on the eight resolutions. See A16 for the
reversibility table.

**Architect involvement — status: complete.** Q1–Q8 resolved; A1–A16
added. **No further architect review is required before
`/implement-feature US-008`.** If the developer hits a concrete obstacle
implementing one of the resolved choices (e.g. an AGP version that
rejects the conditional `apply(plugin = ...)` pattern in Q3; a
detekt rule that fires unexpectedly on `data/telemetry/`), the developer
escalates back to the architect rather than silently flipping the
choice. The future `FailedCommandsExporter` SF (A7) gets its own
architect pass.

### Spec ambiguities surfaced (resolved as part of US-008's v1.1 bump or queued)

- **Q8d**: spec §12 v1.0 "Texto transcrito nunca sale" vs §9 toggle
  "envíame los fallos" (which forwards failed-commands logs including
  the transcript of the un-mapped utterance). **Resolved by the v1.1
  §12 rewrite** (Q8d-Resolved + A7) — failed-commands transcripts are a
  separate data class with a separate transport (`FailedCommandsExporter`,
  TBD), explicitly gated by the user-controlled toggle. The
  `TelemetrySink` (Firebase + PostHog) **never** carries transcripts.
- **§5 "8 funciones" vs the 7-function list** (header-vs-list drift):
  queued for a separate spec-hygiene SF (A14). Not in v1.1.
- **§14 `targetSdk` cosmetic** (spec says 34, code is 35): queued for
  the same separate SF (A14). Not in v1.1.

**Cross-references for the implementer**: `function-catalog` (no impact —
US-008 doesn't touch the catalog), `voice-interaction` (no impact —
US-008 doesn't touch the FSM), `on-device-llm` (downstream consumer —
SF-3.x will be a heavy `TelemetrySink` emitter for model latency /
warm-keepalive / killed-by-system events; the whitelist will grow then),
`platform-integrations` (no direct impact, but consumer SFs touching
NotificationListener / Telecom / Contacts must NEVER log message
content / contact names / phone numbers via the sink — A15's deferred
detekt rule would enforce this), `local-data` (no impact —
`FailedCommandLog` is `data/local/`-scoped, written by handlers,
exported only via the future `FailedCommandsExporter`, not via the
`TelemetrySink`), `testing-patterns` (the JUnit 5 parameterised test
shape in A4 is the canonical pattern for privacy-boundary tests; later
SFs that add to `ALLOWED_PROPS` follow it), `accessibility-patterns`
(no impact — US-008 ships zero UI), `brand-design` (no impact — US-008
ships zero copy beyond the §12 v1.1 Spanish), `material-design` (no
impact), `compose-patterns` (no impact), `navigation-patterns` (no
impact), `launcher-ui` (no impact), `launcher-app` (no impact —
`INTERNET` is the only manifest concern; `CATEGORY_HOME` is SF-1.1's
job), `api-integration` (parked — US-008 is the closest thing to a
"network surface" Curro will have until a hypothetical Phase 3
`read_news_headlines`; the parked status stays), `api-contract`
(parked), `spec-template` (this document follows it), `git-workflow`
(commit scope = `telemetry` for the code commit; `docs(spec)` for the
spec commit; two commits, same push, per A12 / Q7).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-14 | Fran (PM agent) | Initial draft — Q1–Q8 open, awaiting architect resolution |
| 2026-05-14 | Claude `android-architect` | Architecture review: resolved Q1 (release-only deps + runtime kill switch — confirmed PM, added defence-in-depth), Q2 (no defensive debug manifest — reversed PM; Q1 makes it redundant), Q3 (conditional plugin apply on `google-services.json` presence — confirmed PM; release-without-file fails loudly), Q4 (whitelist + value heuristic with NO escape hatch — narrowed PM's Option C; primary guard is the whitelist), Q5 (source-set-split Hilt modules — confirmed PM; pairs with Q1), Q6 (local.properties first, env-var fallback, release-time fail-fast on empty PostHog key — confirmed PM with the fail-fast addition for Q8g), Q7 (two commits, code then spec, one push — reversed PM; spec reviewability wins), Q8a–Q8c (keep current Firebase BOM 33.7.0, PostHog 3.8.0, plugin versions 4.4.2 / 3.0.2 — bumps are a separate chore SF), Q8d (v1.1 §12 wording carves out failed-commands-transcript-with-consent path; the `FailedCommandsExporter` channel is OUT of US-008 scope; the `TelemetrySink` NEVER carries transcripts — full rewritten Spanish text in Q8d-Resolved), Q8e (relax "exactly one INTERNET" AC to INTERNET + ACCESS_NETWORK_STATE + WAKE_LOCK transitively from the SDKs; the merged-manifest output is the source of truth — A2), Q8f (no `tools:node="remove"` for ACCESS_NETWORK_STATE — the SDKs need it), Q8g (release initializer fails fast on empty POSTHOG_API_KEY — A6), Q8h (`@Inject lateinit var` for `TelemetryInitializer` in `CurroApp` per US-002 precedent). Added *Architect's notes & decisions (A1–A16)*: Q1+Q5 coupling as privacy boundary, merged-manifest as permission source-of-truth, no-escape-hatch policy, JUnit 5 parameterised-test fixture pattern, `SdkBootstrap`-interface-per-variant shape, release-time fail-fast on missing key, `FailedCommandsExporter` deferral, CurroApp init order, runtime collection-enabled hook (A9) for a future kill switch, `TELEMETRY_ENABLED` is the default not the only switch (A10), CI implications for a future release-in-CI SF (A11), spec-evolution commit pattern (A12), AD_ID refusal + PostHog session replay disabled (A13), §5 / §14 deliberately out of v1.1 (A14), detekt rule deferral (A15), reversibility table (A16). Added *Owner split* and *Why the architect review was needed* sections. |
