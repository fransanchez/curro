package com.curro.app.domain.repository

/**
 * Lists the on-device Spanish TTS voices available for the user to choose from (SF-8.4 / US-053).
 *
 * The implementation lives in [com.curro.app.data.voice.SystemSpanishVoiceProvider] and wraps
 * [android.speech.tts.TextToSpeech.getVoices]. The interface lives here so the ViewModel and
 * tests depend only on the domain contract — the Android TTS API never escapes [data/voice].
 */
interface SpanishVoiceProvider {
    /**
     * Returns every [SpanishVoice] the system exposes for locale `es-*`, sorted so the most
     * useful choices appear first (female voices before male, higher quality first).
     *
     * An empty list means the device has no offline Spanish voices installed; the screen should
     * hide the voice-picker and show an informational message instead.
     */
    fun availableVoices(): List<SpanishVoice>
}

/**
 * A displayable snapshot of a single Spanish TTS voice.
 *
 * @param name The opaque voice identifier used by [android.speech.tts.TextToSpeech.setVoice] —
 *     this is the value stored in [com.curro.app.domain.repository.SettingsRepository.ttsVoiceName].
 * @param displayName A human-readable label (e.g. "Español · femenino"). Derived from the voice's
 *     locale + gender attributes; never the raw [name].
 * @param isDefault True when this voice is the current system default for the Spanish locale.
 */
data class SpanishVoice(
    val name: String,
    val displayName: String,
    val isDefault: Boolean,
)
