package com.curro.app.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

        private object Keys {
            val EXECUTE = floatPreferencesKey("confidence_execute_min")
            val CONFIRM = floatPreferencesKey("confidence_confirm_min")
            val ALWAYS_CONFIRM = booleanPreferencesKey("always_confirm")
        }

        private object Defaults {
            const val EXECUTE = 0.85f
            const val CONFIRM = 0.60f
            const val ALWAYS_CONFIRM = false
        }

        private companion object {
            const val TAG = "Curro/Settings"
        }
    }
