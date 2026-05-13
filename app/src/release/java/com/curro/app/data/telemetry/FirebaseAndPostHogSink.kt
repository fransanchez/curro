package com.curro.app.data.telemetry

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.curro.app.domain.repository.TelemetrySink
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.posthog.android.PostHog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Release-variant [TelemetrySink] binding.
 *
 * Routes every call through [TelemetryGuardrail] before forwarding to Firebase Analytics,
 * Firebase Crashlytics, and PostHog. A [TelemetryGuardrail.GuardrailResult.Reject] result
 * drops the event silently and logs a warning at [Log.w] — a leaked event is worse than a
 * missed one (privacy over completeness).
 *
 * Lives in `src/release/` per Q5-Resolved: this class references Firebase and PostHog SDK
 * classes, which are `releaseImplementation`-only (Q1-Resolved). The debug classpath does
 * not contain these classes, so this file cannot compile in debug — that is the point.
 *
 * SDKs are accessed as singletons after [TelemetryInitializer] has called
 * [FirebaseAndPostHogSdkBootstrap.initialize]. Calling [event] or [logCrash] before
 * initialisation is a developer error; Firebase / PostHog queue or drop events silently.
 *
 * See A1, A3, Q1, Q5 in docs/briefs/US-008-telemetry-plumbing.md.
 */
@Singleton
class FirebaseAndPostHogSink
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : TelemetrySink {
        private val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }
        private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

        override fun event(
            name: String,
            props: Map<String, Any>,
        ) {
            when (val result = TelemetryGuardrail.check(name, props)) {
                is TelemetryGuardrail.GuardrailResult.Reject -> {
                    // Drop silently — a leaked event is worse than a missed one. Log for devs.
                    Log.w(TAG, "event($name) REJECTED — ${result.reason}")
                    return
                }
                is TelemetryGuardrail.GuardrailResult.Allow -> Unit
            }
            // Firebase Analytics
            val bundle =
                Bundle().apply {
                    for ((k, v) in props) {
                        when (v) {
                            is String -> putString(k, v)
                            is Int -> putInt(k, v)
                            is Long -> putLong(k, v)
                            is Double -> putDouble(k, v)
                            is Boolean -> putBoolean(k, v)
                            else -> putString(k, v.toString())
                        }
                    }
                }
            analytics.logEvent(name, bundle)
            // PostHog
            PostHog.capture(event = name, properties = props.mapValues { (_, v) -> v })
        }

        override fun setUserProperty(
            key: String,
            value: String?,
        ) {
            when (val result = TelemetryGuardrail.check(key, value)) {
                is TelemetryGuardrail.GuardrailResult.Reject -> {
                    Log.w(TAG, "setUserProperty($key) REJECTED — ${result.reason}")
                    return
                }
                is TelemetryGuardrail.GuardrailResult.Allow -> Unit
            }
            analytics.setUserProperty(key, value)
            if (value != null) {
                PostHog.identify(distinctId = null, userProperties = mapOf(key to value))
            }
        }

        override fun logCrash(
            throwable: Throwable,
            fatal: Boolean,
        ) {
            crashlytics.recordException(throwable)
            if (fatal) {
                // Crashlytics has no separate "fatal" API for non-crash exceptions; recordException
                // always records as non-fatal. A truly fatal crash is captured automatically by
                // the SDK's UncaughtExceptionHandler installed during FirebaseApp.initializeApp.
                // Crashlytics marks auto-caught crashes as 'fatal'; non-fatal recordException
                // always shows as non-fatal in the dashboard regardless of the [fatal] flag.
                Log.e(TAG, "logCrash(fatal=true) — Crashlytics recorded", throwable)
            }
        }

        private companion object {
            private const val TAG = "CurroTelemetry"
        }
    }
