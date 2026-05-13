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

/**
 * Release-variant [SdkBootstrap] implementation.
 *
 * Initialises Firebase (Crashlytics + Analytics) and PostHog with the privacy
 * settings mandated by US-008 A13:
 *  - Advertising ID collection: disabled (Curro has no ads; no AdId use).
 *  - PostHog session replay: disabled (would capture screen content — PII risk).
 *
 * Throws [IllegalStateException] on launch if [BuildConfig.POSTHOG_API_KEY] is blank
 * (Q8g-Resolved, A6). This is intentional: a release build shipping without a PostHog
 * key would silently discard all product-analytics events, which defeats the purpose.
 * The fix is one line in `local.properties` — see docs/briefs/US-008-telemetry-plumbing.md
 * Q6-Resolved.
 *
 * Lives in `src/release/` because it references Firebase and PostHog SDK classes, which
 * are `releaseImplementation`-only (Q1-Resolved). See A5 for the SdkBootstrap interface
 * rationale.
 */
@Singleton
class FirebaseAndPostHogSdkBootstrap
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SdkBootstrap {
        override fun initialize() {
            // A6 / Q8g-Resolved: fail fast if PostHog key is missing.
            // The check runs before FirebaseApp.initializeApp so that even the Crashlytics
            // UncaughtExceptionHandler is not installed — there's no Firebase project to report to
            // if the app is misconfigured. The crash goes to Android's standard error log.
            val posthogKey = BuildConfig.POSTHOG_API_KEY
            check(posthogKey.isNotEmpty()) {
                "POSTHOG_API_KEY is empty — set it in local.properties (key: POSTHOG_API_KEY) " +
                    "or as the POSTHOG_API_KEY environment variable. " +
                    "See docs/briefs/US-008-telemetry-plumbing.md Q6-Resolved + A6."
            }

            // Firebase initialisation. FirebaseApp.initializeApp reads google-services.json
            // which the google-services Gradle plugin compiles into the APK resources.
            // The plugin is only applied when the file exists (Q3-Resolved).
            FirebaseApp.initializeApp(context)

            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

            FirebaseAnalytics.getInstance(context).apply {
                setAnalyticsCollectionEnabled(true)
                // A13: AdId / GAID collection is off — Curro has no ads, no advertising use case.
                // Firebase Analytics 22.x automatically collects AdId unless the manifest carries
                // <meta-data android:name="google_analytics_adid_collection_enabled" android:value="false" />.
                // That meta-data is declared in app/src/main/AndroidManifest.xml (see the application block).
            }

            // PostHog setup — A13: session replay off (would capture screen content).
            PostHog.setup(
                context,
                PostHogAndroidConfig(apiKey = posthogKey).apply {
                    // Disable session replay: PostHog Android 3.x may enable it by default.
                    // Capturing screen content is a PII risk and violates spec §12 v1.1.
                    sessionReplay = false
                    // Curro is single-user, single-device — no meaningful distinct ID beyond the
                    // anonymous ID PostHog generates automatically. Do not collect device identifiers.
                    captureDeepLinks = false
                    captureScreenViews = false
                },
            )
        }

        override fun setCollectionEnabled(enabled: Boolean) {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
            if (enabled) {
                PostHog.optIn()
            } else {
                PostHog.optOut()
            }
        }
    }
