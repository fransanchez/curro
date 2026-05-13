package com.curro.app.data.telemetry

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-variant [SdkBootstrap] binding. No-op for both lifecycle methods.
 *
 * In debug builds, no SDK is initialised — the bytecode isn't even on the classpath
 * (Q1-Resolved: releaseImplementation). This class exists only to satisfy the Hilt
 * binding that [TelemetryInitializer] depends on.
 *
 * Lives in `src/debug/` per Q5-Resolved. See A5 in
 * docs/briefs/US-008-telemetry-plumbing.md for the SdkBootstrap interface rationale.
 */
@Singleton
class NoopSdkBootstrap
    @Inject
    constructor() : SdkBootstrap {
        override fun initialize() {
            // Intentionally empty — debug builds do not initialise any telemetry SDK.
        }

        override fun setCollectionEnabled(enabled: Boolean) {
            // Intentionally empty — debug builds have no SDK to toggle.
        }
    }
