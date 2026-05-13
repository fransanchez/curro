package com.curro.app.data.telemetry

import android.util.Log
import com.curro.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for telemetry SDK lifecycle, called once from `CurroApp.onCreate()`.
 *
 * Delegates SDK-specific initialisation to [SdkBootstrap], whose per-variant implementations
 * live in the debug / release source sets respectively. This class itself contains zero SDK
 * references, so it compiles identically in both variants (A5 in
 * docs/briefs/US-008-telemetry-plumbing.md).
 *
 * Call order (A8):
 * 1. `super.onCreate()` in `CurroApp` — Hilt completes member injection.
 * 2. `telemetryInitializer.initialize()` — this method runs; Hilt has already set
 *    [sdkBootstrap], so no [UninitializedPropertyAccessException] risk.
 *
 * [BuildConfig.TELEMETRY_ENABLED] semantics (A10): this is the DEFAULT collection
 * state, not a binary kill switch. The runtime override path is [setCollectionEnabled].
 */
@Singleton
class TelemetryInitializer
    @Inject
    constructor(
        private val sdkBootstrap: SdkBootstrap,
    ) {
        /**
         * Initialises the telemetry SDKs if [BuildConfig.TELEMETRY_ENABLED] is true.
         *
         * In debug, the flag is false → logs and returns immediately.
         * In release, the flag is true → delegates to [SdkBootstrap.initialize].
         *
         * Does NOT emit any telemetry event — that is each event-emitting SF's job.
         */
        fun initialize() {
            if (!BuildConfig.TELEMETRY_ENABLED) {
                Log.d(TAG, "TELEMETRY_ENABLED=false — initializer is a no-op (debug build)")
                return
            }
            sdkBootstrap.initialize()
            Log.d(TAG, "telemetry initialised (BUILD_TYPE=${BuildConfig.BUILD_TYPE})")
        }

        /**
         * Enables or disables telemetry collection at runtime.
         *
         * No-op when [BuildConfig.TELEMETRY_ENABLED] is false (debug builds do not collect
         * regardless). In release, propagates to all SDKs via [SdkBootstrap.setCollectionEnabled].
         *
         * This method is plumbing for a future SF (SF-8.x: the config-menu
         * "envíame los fallos" toggle). No caller in US-008 invokes it — see A9.
         */
        fun setCollectionEnabled(enabled: Boolean) {
            if (!BuildConfig.TELEMETRY_ENABLED) return
            sdkBootstrap.setCollectionEnabled(enabled)
            Log.d(TAG, "telemetry collection enabled=$enabled")
        }

        private companion object {
            private const val TAG = "CurroTelemetry"
        }
    }
