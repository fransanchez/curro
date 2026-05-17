package com.curro.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * SF-8.1 (US-050) — `sendFailuresEnabled` DataStore key.
 *
 * Three cases: default false, round-trip, emits on change.
 */
@DisplayName("SettingsDataStore — sendFailuresEnabled (SF-8.1)")
class SettingsDataStoreSendFailuresTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var settings: SettingsDataStore

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val file = tempDir.resolve("settings_send_failures.preferences_pb").toFile()
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        settings = SettingsDataStore(store)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `sendFailuresEnabled defaults to false`() =
        runTest {
            assertEquals(false, settings.sendFailuresEnabled.first())
        }

    @Test
    fun `setSendFailuresEnabled round-trips true`() =
        runTest {
            settings.setSendFailuresEnabled(true)
            assertEquals(true, settings.sendFailuresEnabled.first())
        }

    @Test
    fun `sendFailuresEnabled emits on change`() =
        runTest {
            val emissions = mutableListOf<Boolean>()
            val job =
                launch {
                    settings.sendFailuresEnabled.take(3).toList(emissions)
                }
            delay(50)
            settings.setSendFailuresEnabled(true)
            delay(50)
            settings.setSendFailuresEnabled(false)
            delay(50)
            job.cancel()
            assertEquals(listOf(false, true, false), emissions)
        }
}
