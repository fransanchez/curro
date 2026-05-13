import java.util.Properties

// Plugin application order is load-bearing — Architect's notes A2.
// KSP after kotlin.android; Hilt after KSP; android-junit5 after kotlin.android.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // A1 — Kotlin-2.x Compose Compiler (separate plugin, not the old AGP extension)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.junit5) // A5 — surfaces JUnit 5 to AGP's testDebugUnitTest task
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Read local.properties for release signing and POSTHOG_API_KEY (Q6-Resolved).
// If the file or keys are absent, release signing falls back to debug-signing so CI does not break.
// local.properties is git-ignored and must never be committed.
val localProps =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }

// SF-0.8 (US-008) — Q3-Resolved: apply Firebase plugins only when google-services.json is present.
// Debug builds without the file succeed unconditionally (the SDK bytecode is not in the debug APK
// anyway — Q1-Resolved: releaseImplementation only).
// Release builds without the file will fail loudly with the google-services plugin's own error,
// which is intentional: a release build must never silently ship without telemetry wiring.
// The file is git-ignored (.gitignore); CI decodes it from the GOOGLE_SERVICES_JSON secret
// (step "Decode google-services.json" in .github/workflows/ci.yml).
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.plugin.get().pluginId)
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

        // Hilt test runner — class arrives in SF-0.2.
        // Declared now so SF-0.2 only needs to add the class itself.
        testInstrumentationRunner = "com.curro.app.HiltTestRunner"
    }

    signingConfigs {
        create("release") {
            // Reads KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD from local.properties.
            // If any key is missing, the release build falls back to the default debug signing config
            // so CI never breaks on a machine without the keystore.
            val keystorePath = localProps.getProperty("KEYSTORE_PATH")
            val keystorePassword = localProps.getProperty("KEYSTORE_PASSWORD")
            val keyAlias = localProps.getProperty("KEY_ALIAS")
            val keyPassword = localProps.getProperty("KEY_PASSWORD")
            if (keystorePath != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false // R8 off — keeps debug builds fast
            // SF-0.8 (US-008) — A10: TELEMETRY_ENABLED is the DEFAULT collection state, not a kill switch.
            // false in debug: no SDKs, no events, no network. The NoopTelemetrySink is the debug binding.
            buildConfigField("boolean", "TELEMETRY_ENABLED", "false")
            // Q6-Resolved: PostHog API key is empty in debug; NoopSdkBootstrap never reads it.
            // Field present for BuildConfig symmetry — src/main/ code can reference it without #ifdef.
            buildConfigField("String", "POSTHOG_API_KEY", "\"\"")
        }
        release {
            isMinifyEnabled = false // R8 tuning deferred (post-Phase-0)
            // SF-0.8 (US-008) — A10: true in release — SDKs initialise; runtime override via
            // TelemetryInitializer.setCollectionEnabled(false) can downgrade this (future SF:
            // the config menu "envíame los fallos" toggle, or an emergency kill switch).
            buildConfigField("boolean", "TELEMETRY_ENABLED", "true")
            // Q6-Resolved: local.properties first, env-var fallback (CI). Empty string is allowed at
            // build time; FirebaseAndPostHogSdkBootstrap.initialize() throws IllegalStateException
            // if it ends up "" at runtime — A6. This prevents a silent PostHog no-op in release.
            val posthogKey =
                localProps.getProperty("POSTHOG_API_KEY")
                    ?: System.getenv("POSTHOG_API_KEY")
                    ?: ""
            buildConfigField("String", "POSTHOG_API_KEY", "\"$posthogKey\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use release signing if keys are present; fall back to debug otherwise
            signingConfig =
                if (localProps.getProperty("KEYSTORE_PATH") != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    buildFeatures {
        compose = true // paired with the `kotlin.plugin.compose` plugin (A1)
        buildConfig = true // AGP 8+ defaults this OFF; we need it for TELEMETRY_ENABLED (A8)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // A9 — matches setup-java JDK 17 in CI
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // --- AndroidX core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // --- Compose (versions resolved via BOM — A6) ---
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // Navigation Compose — NOT BOM-resolved; pinned via `navigationCompose` in libs.versions.toml.
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // --- Hilt (KSP, not kapt — A4) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // --- Coroutines ---
    implementation(libs.kotlinx.coroutines.android)

    // --- JVM unit tests (JUnit 5 — A5) ---
    // Do NOT add junit-jupiter-* to androidTestImplementation — instrumented stays JUnit 4.
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params) // US-008: @ParameterizedTest support
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    // --- Instrumented tests (JUnit 4 + AndroidJUnit4 runner — NOT JUnit 5; see A5) ---
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    // --- SF-0.8 (US-008) — release-only telemetry SDKs (Q1-Resolved: Option A) ---
    // The SDK bytecode is ABSENT from the debug APK. This is the structural guarantee of the
    // privacy boundary: debug builds cannot call Firebase or PostHog because the bytecode is
    // not there. The runtime TELEMETRY_ENABLED flag is a belt-and-braces kill switch (A1).
    // Source-set-split Hilt modules (Q5-Resolved) ensure the debug classpath never references
    // FirebaseAndPostHogSink or FirebaseAndPostHogSdkBootstrap.
    releaseImplementation(platform(libs.firebase.bom))
    releaseImplementation(libs.firebase.crashlytics)
    releaseImplementation(libs.firebase.analytics)
    releaseImplementation(libs.posthog.android)

    // --- Reserved dependencies (not yet activated) ---
    // Room         → SF-7.1: implementation(libs.room.runtime), implementation(libs.room.ktx), ksp(libs.room.compiler)
    // DataStore    → SF-7.1: implementation(libs.datastore.preferences)
    // MediaPipe    → SF-3.1: implementation(libs.mediapipe.tasks.genai)
    // Coil         → SF-1.4: implementation(libs.coil.compose)
}

// JUnit 5 platform wiring is handled by the `android-junit5` plugin applied above (A5).
// The explicit configureEach below is belt-and-braces for any non-AGP Test tasks (e.g. plain Gradle tests).
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Detekt configuration — SF-0.3: config relocated to config/detekt/detekt.yml (canonical location),
// Curro-shaped rule tuning applied, baseline locks current state, rule violations not in baseline
// fail the build. The No-Double-Padding rule is documented in CLAUDE.md / launcher-ui skill but
// is not yet a custom detekt extension — that's a future tooling SF (SF-tooling.1 or similar,
// after CurroNavHost + real child screens exist).
detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = false
    baseline = rootProject.file("config/detekt/baseline.xml")

    // K2 compiler plugin (experimental in detekt 1.23.x): deliberately left OFF.
    // As of 2026-05-13, detekt's K2 support requires the compiler classpath to be
    // on the analysis classpath and still carries known false-positives for Android
    // projects (type-resolution differences vs the JVM frontend). The 1.23.x release
    // notes mark it experimental; defer to a future chore commit once it stabilises.
    // enableCompilerPlugin = true  // uncomment when K2 is stable in detekt
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true) // human-readable report
        xml.required.set(true) // consumed by IDE plugins (IntelliJ detekt plugin)
        sarif.required.set(true) // GitHub code-scanning SARIF upload (future CI enhancement)
    }
}
