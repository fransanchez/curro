package com.curro.app.presentation.config.tts

import app.cash.turbine.test
import com.curro.app.assistant.FakeSettingsRepository
import com.curro.app.domain.repository.SpanishVoice
import com.curro.app.util.FakeSpanishVoiceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-8.4 (US-053) — [TtsSettingsViewModel] unit tests.
 *
 * Uses [FakeSettingsRepository] and [FakeSpanishVoiceProvider].
 * No Android dependencies — runs on JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("TtsSettingsViewModel (SF-8.4)")
class TtsSettingsViewModelTest {
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var voiceProvider: FakeSpanishVoiceProvider
    private lateinit var vm: TtsSettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settingsRepo = FakeSettingsRepository()
        voiceProvider = FakeSpanishVoiceProvider()
        vm = TtsSettingsViewModel(settingsRepo, voiceProvider)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects DataStore defaults and loads available voices`() =
        runTest {
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(TtsSettingsUiState.DEFAULT_RATE, state.rate)
                assertEquals(TtsSettingsUiState.DEFAULT_PITCH, state.pitch)
                assertNull(state.selectedVoiceName)
                assertEquals(FakeSpanishVoiceProvider.DEFAULT_VOICES, state.availableVoices)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `RateChanged persists new rate to settings repository`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(TtsSettingsEvent.RateChanged(1.2f))
                val state = awaitItem()
                assertEquals(1.2f, state.rate)
                val persisted = settingsRepo.ttsRate.first()
                assertEquals(1.2f, persisted)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `PitchChanged persists new pitch to settings repository`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(TtsSettingsEvent.PitchChanged(1.5f))
                val state = awaitItem()
                assertEquals(1.5f, state.pitch)
                val persisted = settingsRepo.ttsPitch.first()
                assertEquals(1.5f, persisted)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `VoiceSelected persists voice name to settings repository`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(TtsSettingsEvent.VoiceSelected("es-es-x-eem-local"))
                val state = awaitItem()
                assertEquals("es-es-x-eem-local", state.selectedVoiceName)
                val persisted = settingsRepo.ttsVoiceName.first()
                assertEquals("es-es-x-eem-local", persisted)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `VoiceSelected with null resets to system default`() =
        runTest {
            settingsRepo.setTtsVoiceName("es-es-x-eem-local")
            vm = TtsSettingsViewModel(settingsRepo, voiceProvider)
            vm.uiState.test {
                awaitItem()
                vm.onEvent(TtsSettingsEvent.VoiceSelected(null))
                val state = awaitItem()
                assertNull(state.selectedVoiceName)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `availableVoices is empty list when provider returns nothing`() =
        runTest {
            voiceProvider.voices = emptyList()
            vm = TtsSettingsViewModel(settingsRepo, voiceProvider)
            vm.uiState.test {
                val state = awaitItem()
                assertTrue(state.availableVoices.isEmpty())
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `initial selectedVoiceName reflects persisted DataStore value`() =
        runTest {
            settingsRepo.setTtsVoiceName("es-es-x-eef-local")
            vm = TtsSettingsViewModel(settingsRepo, voiceProvider)
            vm.uiState.test {
                val state = awaitItem()
                assertEquals("es-es-x-eef-local", state.selectedVoiceName)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `rate clamped to min when set below range`() =
        runTest {
            settingsRepo.setTtsRate(0.1f)
            vm = TtsSettingsViewModel(settingsRepo, voiceProvider)
            vm.uiState.test {
                val state = awaitItem()
                // FakeSettingsRepository clamps to 0.5f
                assertEquals(TtsSettingsUiState.RATE_MIN, state.rate)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `voice list is loaded from provider at construction time`() =
        runTest {
            val customVoice =
                SpanishVoice(
                    name = "es-mx-x-custom",
                    displayName = "Español (MX) · femenino",
                    isDefault = false,
                )
            voiceProvider.voices = listOf(customVoice)
            vm = TtsSettingsViewModel(settingsRepo, voiceProvider)
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(listOf(customVoice), state.availableVoices)
                cancelAndConsumeRemainingEvents()
            }
        }
}
