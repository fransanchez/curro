package com.curro.app.data.recovery

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [android.content.SharedPreferences]-backed implementation of [RecoveryStateRepository].
 *
 * Uses plain (unencrypted) SharedPreferences — there is no PII here, only crash
 * timestamps and a boolean flag. Encryption would add AndroidKeyStore overhead
 * with zero privacy benefit.
 *
 * **All writes use [SharedPreferences.Editor.commit]**, not `apply()`. The
 * `apply()` call is asynchronous; if the JVM dies before the background write
 * completes, the crash is silently lost. `commit()` blocks the calling thread
 * (the crash handler already controls it) and guarantees persistence before
 * the process terminates.
 *
 * The crash-timestamp list is stored as a JSON array of `Long` values. JSONArray
 * is available in production (Android SDK) and in JVM unit tests (via the
 * `org.json` artifact already on the test classpath).
 */
@Singleton
class SharedPreferencesRecoveryState
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : RecoveryStateRepository {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun recordCrash(nowMs: Long) {
            val timestamps = readTimestamps()
            timestamps.add(nowMs)
            val windowStart = nowMs - RecoveryStateRepository.CRASH_WINDOW_MS
            val inWindow = timestamps.filter { it >= windowStart }
            val editor = prefs.edit()
            editor.putString(KEY_CRASH_TIMESTAMPS, serializeTimestamps(inWindow))
            if (inWindow.size >= RecoveryStateRepository.CRASH_THRESHOLD) {
                editor.putBoolean(KEY_RECOVERY_PENDING, true)
            }
            editor.commit() // intentional: must persist before the JVM dies
        }

        override fun isRecoveryPending(): Boolean = prefs.getBoolean(KEY_RECOVERY_PENDING, false)

        override fun acknowledgeRecovery() {
            prefs
                .edit()
                .remove(KEY_RECOVERY_PENDING)
                .remove(KEY_CRASH_TIMESTAMPS)
                .commit() // intentional: synchronous write
        }

        override fun recordSuccessfulRun() {
            prefs
                .edit()
                .remove(KEY_CRASH_TIMESTAMPS)
                .commit() // intentional: synchronous write
        }

        private fun readTimestamps(): MutableList<Long> {
            val json = prefs.getString(KEY_CRASH_TIMESTAMPS, null) ?: return mutableListOf()
            return runCatching {
                val array = JSONArray(json)
                MutableList(array.length()) { i -> array.getLong(i) }
            }.getOrElse { mutableListOf() }
        }

        private fun serializeTimestamps(timestamps: List<Long>): String {
            val array = JSONArray()
            timestamps.forEach { array.put(it) }
            return array.toString()
        }

        private companion object {
            const val PREFS_NAME = "curro_recovery"
            const val KEY_CRASH_TIMESTAMPS = "crash_timestamps"
            const val KEY_RECOVERY_PENDING = "recovery_pending"
        }
    }
