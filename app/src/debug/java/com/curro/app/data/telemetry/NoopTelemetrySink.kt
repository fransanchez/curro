package com.curro.app.data.telemetry

import android.util.Log
import com.curro.app.domain.repository.TelemetrySink
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-variant [TelemetrySink] binding. No network calls — ever.
 *
 * Every call still routes through [TelemetryGuardrail] so the developer sees violations
 * in Logcat during local development, even though no SDK is in play. An [Allow] result
 * logs at `Log.d`; a [Reject] result logs at `Log.w` so it stands out in the filter.
 *
 * Lives in `src/debug/` per Q5-Resolved (source-set-split Hilt module): the debug Hilt
 * graph binds this class; the release graph binds `FirebaseAndPostHogSink`. There is no
 * runtime branch — the classpath is the gate (A1 in
 * docs/briefs/US-008-telemetry-plumbing.md).
 */
@Singleton
class NoopTelemetrySink
    @Inject
    constructor() : TelemetrySink {
        override fun event(
            name: String,
            props: Map<String, Any>,
        ) {
            when (val result = TelemetryGuardrail.check(name, props)) {
                is TelemetryGuardrail.GuardrailResult.Allow -> {
                    Log.d(TAG, "event($name, $props)")
                }
                is TelemetryGuardrail.GuardrailResult.Reject -> {
                    Log.w(TAG, "event($name) REJECTED by guardrail — ${result.reason}")
                }
            }
        }

        override fun setUserProperty(
            key: String,
            value: String?,
        ) {
            when (val result = TelemetryGuardrail.check(key, value)) {
                is TelemetryGuardrail.GuardrailResult.Allow -> {
                    Log.d(TAG, "setUserProperty($key, $value)")
                }
                is TelemetryGuardrail.GuardrailResult.Reject -> {
                    Log.w(TAG, "setUserProperty($key) REJECTED by guardrail — ${result.reason}")
                }
            }
        }

        override fun logCrash(
            throwable: Throwable,
            fatal: Boolean,
        ) {
            Log.e(TAG, "logCrash(fatal=$fatal)", throwable)
        }

        private companion object {
            private const val TAG = "CurroTelemetry"
        }
    }
