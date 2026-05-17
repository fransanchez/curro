package com.curro.app.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.curro.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module-private extension property that materialises the DataStore singleton —
 * the standard `preferencesDataStore(name)` pattern (AndroidX docs).
 *
 * The first activation of DataStore in Curro (SF-6.1). Phase 7 will add the
 * alias Room database alongside; Phase 8 reuses this same file for more setting
 * keys.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "curro_settings",
)

/**
 * DataStore-backed [SettingsRepository] (SF-6.1 / US-041).
 *
 * Reads return defaults on first access (the file is created lazily). Setters
 * clamp out-of-range writes — the policy never sees an inconsistent pair.
 *
 * Singleton: one DataStore handle per process. The AndroidX-recommended idiom
 * is the property-delegate above, so no `@Provides DataStore<Preferences>` is
 * needed — Hilt only binds the [SettingsRepository] interface to this impl.
 */
@Singleton
class SettingsDataStore
    /**
     * Primary (test) constructor — takes the [DataStore] handle directly so JVM
     * tests can plug a temp-file backed instance without a real Android
     * [Context]. Marked `internal` to keep the public surface clean; the Hilt
     * graph uses the secondary `@Inject` constructor below.
     */
    internal constructor(
        private val store: DataStore<Preferences>,
    ) : SettingsRepository {
        /**
         * Production constructor — Hilt provides the [Context]; the
         * `Context.dataStore` extension materialises the singleton
         * [DataStore]. The AndroidX-recommended idiom.
         */
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(context.dataStore)

        override val executeThreshold: Flow<Float> =
            store.data.map { it[Keys.EXECUTE] ?: Defaults.EXECUTE }

        override val confirmThreshold: Flow<Float> =
            store.data.map { it[Keys.CONFIRM] ?: Defaults.CONFIRM }

        override val alwaysConfirm: Flow<Boolean> =
            store.data.map { it[Keys.ALWAYS_CONFIRM] ?: Defaults.ALWAYS_CONFIRM }

        // --- SF-8.1 (US-050) ---
        override val incomingCallModeEnabled: Flow<Boolean> =
            store.data.map { it[Keys.INCOMING_CALL_MODE] ?: Defaults.INCOMING_CALL_MODE }

        override val sendFailuresEnabled: Flow<Boolean> =
            store.data.map { it[Keys.SEND_FAILURES] ?: Defaults.SEND_FAILURES }

        // --- SF-8.3 (US-052) ---
        override val launcherFavouritesOverride: Flow<List<String>?> =
            store.data.map { prefs ->
                val raw = prefs[Keys.LAUNCHER_FAVOURITES_OVERRIDE] ?: ""
                if (raw.isEmpty()) null else raw.split(',').filter { it.isNotBlank() }
            }

        // --- SF-8.4 (US-053) ---
        override val ttsVoiceName: Flow<String?> =
            store.data.map { prefs ->
                val v = prefs[Keys.TTS_VOICE_NAME] ?: ""
                if (v.isEmpty()) null else v
            }

        override val ttsRate: Flow<Float> =
            store.data.map { it[Keys.TTS_RATE] ?: Defaults.TTS_RATE }

        override val ttsPitch: Flow<Float> =
            store.data.map { it[Keys.TTS_PITCH] ?: Defaults.TTS_PITCH }

        override suspend fun setExecuteThreshold(value: Float) {
            val clamped = value.coerceIn(0f, 1f)
            val currentConfirm = confirmThreshold.first()
            store.edit { prefs ->
                prefs[Keys.EXECUTE] = clamped
                if (currentConfirm > clamped) {
                    Log.w(
                        TAG,
                        "setExecuteThreshold: confirm ($currentConfirm) > execute ($clamped); clamping confirm.",
                    )
                    prefs[Keys.CONFIRM] = clamped
                }
            }
        }

        override suspend fun setConfirmThreshold(value: Float) {
            val currentExecute = executeThreshold.first()
            val clamped = value.coerceIn(0f, currentExecute)
            if (clamped != value) {
                Log.w(
                    TAG,
                    "setConfirmThreshold: value $value out of [0, $currentExecute]; clamped to $clamped.",
                )
            }
            store.edit { prefs -> prefs[Keys.CONFIRM] = clamped }
        }

        override suspend fun setAlwaysConfirm(value: Boolean) {
            store.edit { prefs -> prefs[Keys.ALWAYS_CONFIRM] = value }
        }

        // --- SF-8.1 (US-050) ---
        override suspend fun setIncomingCallModeEnabled(value: Boolean) {
            store.edit { prefs -> prefs[Keys.INCOMING_CALL_MODE] = value }
        }

        override suspend fun setSendFailuresEnabled(value: Boolean) {
            store.edit { prefs -> prefs[Keys.SEND_FAILURES] = value }
        }

        // --- SF-8.3 (US-052) ---
        override suspend fun setLauncherFavouritesOverride(packages: List<String>?) {
            store.edit { prefs ->
                if (packages.isNullOrEmpty()) {
                    prefs[Keys.LAUNCHER_FAVOURITES_OVERRIDE] = ""
                } else {
                    prefs[Keys.LAUNCHER_FAVOURITES_OVERRIDE] = packages.joinToString(",")
                }
            }
        }

        // --- SF-8.4 (US-053) ---
        override suspend fun setTtsVoiceName(name: String?) {
            store.edit { prefs ->
                prefs[Keys.TTS_VOICE_NAME] = name.orEmpty()
            }
        }

        override suspend fun setTtsRate(rate: Float) {
            store.edit { prefs ->
                prefs[Keys.TTS_RATE] = rate.coerceIn(Defaults.TTS_RATE_MIN, Defaults.TTS_RATE_MAX)
            }
        }

        override suspend fun setTtsPitch(pitch: Float) {
            store.edit { prefs ->
                prefs[Keys.TTS_PITCH] = pitch.coerceIn(Defaults.TTS_PITCH_MIN, Defaults.TTS_PITCH_MAX)
            }
        }

        private object Keys {
            val EXECUTE = floatPreferencesKey("confidence_execute_min")
            val CONFIRM = floatPreferencesKey("confidence_confirm_min")
            val ALWAYS_CONFIRM = booleanPreferencesKey("always_confirm")

            // SF-8.1 (US-050)
            val INCOMING_CALL_MODE = booleanPreferencesKey("incoming_call_mode")
            val SEND_FAILURES = booleanPreferencesKey("send_failures")

            // SF-8.3 (US-052)
            val LAUNCHER_FAVOURITES_OVERRIDE = stringPreferencesKey("launcher_favourites_override")

            // SF-8.4 (US-053)
            val TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
            val TTS_RATE = floatPreferencesKey("tts_rate")
            val TTS_PITCH = floatPreferencesKey("tts_pitch")
        }

        private object Defaults {
            const val EXECUTE = 0.85f
            const val CONFIRM = 0.60f
            const val ALWAYS_CONFIRM = false

            // SF-8.1 (US-050)
            const val INCOMING_CALL_MODE = false
            const val SEND_FAILURES = false

            // SF-8.4 (US-053)
            const val TTS_RATE = 0.88f
            const val TTS_PITCH = 1.0f
            const val TTS_RATE_MIN = 0.5f
            const val TTS_RATE_MAX = 1.5f
            const val TTS_PITCH_MIN = 0.5f
            const val TTS_PITCH_MAX = 2.0f
        }

        private companion object {
            const val TAG = "Curro/Settings"
        }
    }
