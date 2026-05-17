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
 * SF-8.1 (US-050) — `incomingCallModeEnabled` DataStore key.
 *
 * Three cases: default false, round-trip, emits on change.
 */
@DisplayName("SettingsDataStore — incomingCallModeEnabled (SF-8.1)")
class SettingsDataStoreIncomingCallModeTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var settings: SettingsDataStore

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val file = tempDir.resolve("settings_incoming.preferences_pb").toFile()
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        settings = SettingsDataStore(store)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `incomingCallModeEnabled defaults to false`() =
        runTest {
            assertEquals(false, settings.incomingCallModeEnabled.first())
        }

    @Test
    fun `setIncomingCallModeEnabled round-trips true`() =
        runTest {
            settings.setIncomingCallModeEnabled(true)
            assertEquals(true, settings.incomingCallModeEnabled.first())
        }

    @Test
    fun `incomingCallModeEnabled emits on change`() =
        runTest {
            val emissions = mutableListOf<Boolean>()
            val job =
                launch {
                    settings.incomingCallModeEnabled.take(3).toList(emissions)
                }
            // Allow initial emission.
            delay(50)
            settings.setIncomingCallModeEnabled(true)
            delay(50)
            settings.setIncomingCallModeEnabled(false)
            delay(50)
            job.cancel()
            // Must have at least: false (default), true, false.
            assertEquals(3, emissions.size)
            assertEquals(listOf(false, true, false), emissions)
        }
}
