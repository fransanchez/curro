package com.curro.app.data.telemetry

/**
 * Lifecycle hook for telemetry SDK initialisation.
 *
 * Two per-variant implementations:
 *  - `NoopSdkBootstrap` (debug source set) — no-op; no SDK calls.
 *  - `FirebaseAndPostHogSdkBootstrap` (release source set) — initialises
 *    Firebase and PostHog, and configures privacy options (AD_ID off,
 *    session replay off — A13 in docs/briefs/US-008-telemetry-plumbing.md).
 *
 * [TelemetryInitializer] depends on this interface, not on any SDK class,
 * so it can live in src/main/ without referencing the release-only SDK bytecode.
 * See A5 in docs/briefs/US-008-telemetry-plumbing.md.
 */
interface SdkBootstrap {
    /**
     * Called from [TelemetryInitializer.initialize], which is called from
     * `CurroApp.onCreate()` after Hilt injection. Idempotent — safe to call
     * once at app start; implementations must not depend on being called more
     * than once.
     */
    fun initialize()

    /**
     * Enables or disables telemetry collection at runtime.
     *
     * No-op in the debug variant (Noop sink never collects anyway).
     * In the release variant, propagates to Firebase Analytics, Crashlytics,
     * and PostHog. Called by a future SF when the config-menu toggle lands
     * (SF-8.x — see A9 in docs/briefs/US-008-telemetry-plumbing.md).
     */
    fun setCollectionEnabled(enabled: Boolean)
}
