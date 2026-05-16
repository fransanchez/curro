package com.curro.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * SF-6.1 (US-041) — DataStore behaviour for [SettingsDataStore].
 *
 * Uses [PreferenceDataStoreFactory.create] with a temp-file backing so the test
 * runs on plain JVM (no Robolectric extension needed). The wire-shape is
 * identical to the production `Context.dataStore` singleton; only the file
 * location differs. The test plugs the same [DataStore] handle into the
 * internal constructor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SettingsDataStore (SF-6.1)")
class SettingsDataStoreTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var settings: SettingsDataStore

    private fun makeStore(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val file = tempDir.resolve("settings.preferences_pb").toFile()
        store = makeStore(file)
        settings = SettingsDataStore(store)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `firstRead returns defaults`() =
        runTest {
            assertEquals(0.85f, settings.executeThreshold.first())
            assertEquals(0.60f, settings.confirmThreshold.first())
            assertEquals(false, settings.alwaysConfirm.first())
        }

    @Test
    fun `setExecute round-trips`() =
        runTest {
            settings.setExecuteThreshold(0.90f)
            assertEquals(0.90f, settings.executeThreshold.first())
        }

    @Test
    fun `setConfirm round-trips`() =
        runTest {
            settings.setConfirmThreshold(0.55f)
            assertEquals(0.55f, settings.confirmThreshold.first())
        }

    @Test
    fun `setAlwaysConfirm round-trips`() =
        runTest {
            settings.setAlwaysConfirm(true)
            assertEquals(true, settings.alwaysConfirm.first())
        }

    @Test
    fun `setExecute above 1f clamps to 1f`() =
        runTest {
            settings.setExecuteThreshold(1.5f)
            assertEquals(1.0f, settings.executeThreshold.first())
        }

    @Test
    fun `setExecute below zero clamps to zero (and lowers confirm)`() =
        runTest {
            settings.setExecuteThreshold(-0.1f)
            assertEquals(0.0f, settings.executeThreshold.first())
            // confirm default is 0.6 > 0.0 → consistency rule lowers it.
            assertEquals(0.0f, settings.confirmThreshold.first())
        }

    @Test
    fun `setExecute below confirm also lowers confirm`() =
        runTest {
            // Defaults: execute = 0.85, confirm = 0.60. Lower execute below confirm.
            settings.setExecuteThreshold(0.50f)
            assertEquals(0.50f, settings.executeThreshold.first())
            assertEquals(0.50f, settings.confirmThreshold.first())
        }

    @Test
    fun `setConfirm above execute clamps to execute`() =
        runTest {
            // Defaults: execute = 0.85. Try to set confirm = 0.95 → clamp to 0.85.
            settings.setConfirmThreshold(0.95f)
            assertEquals(0.85f, settings.confirmThreshold.first())
            assertEquals(0.85f, settings.executeThreshold.first()) // execute unchanged
        }

    @Test
    fun `concurrent collectors observe the same change`() =
        runTest {
            settings.setExecuteThreshold(0.92f)
            // Two independent collectors both see the latest value.
            val firstCollector = settings.executeThreshold.take(1).toList()
            val secondCollector = settings.executeThreshold.take(1).toList()
            assertEquals(0.92f, firstCollector.single())
            assertEquals(0.92f, secondCollector.single())
        }

    @Test
    fun `values persist across SettingsDataStore instance restart`() =
        runTest {
            settings.setExecuteThreshold(0.77f)
            settings.setAlwaysConfirm(true)
            // "Restart": instantiate a fresh SettingsDataStore over the SAME store.
            // (The store survives because it's the in-memory representation of the
            // same temp file; cancelling the scope would tear the file down too.)
            val fresh = SettingsDataStore(store)
            assertEquals(0.77f, fresh.executeThreshold.first())
            assertEquals(true, fresh.alwaysConfirm.first())
        }
}
