package com.curro.app.domain.repository

/**
 * The privacy-safe telemetry boundary for Curro.
 *
 * Every feature that wants to record a crash, an event, or a user property
 * does so through this interface. Implementations route every call through
 * [com.curro.app.data.telemetry.TelemetryGuardrail] before reaching any SDK.
 *
 * In debug builds the binding is [com.curro.app.data.telemetry.NoopTelemetrySink] —
 * calls are logged to Logcat for developer visibility but never leave the device.
 * In release builds the binding is [com.curro.app.data.telemetry.FirebaseAndPostHogSink]
 * — calls (after the guardrail) reach Firebase Crashlytics / Analytics and PostHog
 * over the network.
 *
 * The guardrail forbids any event name or property value that contains a transcript,
 * a message body, a contact name, a phone number, an email, or an unlisted key.
 * See [com.curro.app.data.telemetry.TelemetryGuardrail] and `TelemetryGuardrailTest`
 * for the canonical allowed / forbidden examples — the test fails CI on any
 * forbidden call.
 *
 * No event-emitting feature reads `BuildConfig.TELEMETRY_ENABLED` directly; that
 * flag is owned by [com.curro.app.data.telemetry.TelemetryInitializer] exclusively.
 * Features `@Inject` this interface and call `event(...)` — the debug/release
 * binding is opaque to them (A1 in docs/briefs/US-008-telemetry-plumbing.md).
 *
 * See docs/curro-spec-v1.0.md §12 (v1.1) and CLAUDE.md → Privacy & telemetry.
 */
interface TelemetrySink {
    /**
     * Record a discrete event.
     *
     * [name] must be a stable snake_case identifier registered in
     * [com.curro.app.data.telemetry.TelemetryGuardrail]'s `ALLOWED_PROPS` map.
     * [props] must contain only whitelisted keys with short, non-PII values.
     * The guardrail enforces this; a violation logs a warning (debug) or drops
     * the event (release) rather than crashing the user's app.
     */
    fun event(
        name: String,
        props: Map<String, Any> = emptyMap(),
    )

    /**
     * Set a user-scoped property. Never PII.
     *
     * [key] must be in the [com.curro.app.data.telemetry.TelemetryGuardrail]'s
     * `ALLOWED_USER_PROPS` set. [value] = null clears the property.
     */
    fun setUserProperty(
        key: String,
        value: String?,
    )

    /**
     * Record a [Throwable].
     *
     * [fatal] = true marks it as a crash (maps to Crashlytics fatal);
     * [fatal] = false marks it as a non-fatal exception.
     * The message is the exception's own message — never inject transcripts
     * or contact names into exception messages before calling this.
     */
    fun logCrash(
        throwable: Throwable,
        fatal: Boolean = false,
    )
}
