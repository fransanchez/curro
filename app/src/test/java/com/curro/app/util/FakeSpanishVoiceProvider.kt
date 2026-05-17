package com.curro.app.util

import com.curro.app.domain.repository.SpanishVoice
import com.curro.app.domain.repository.SpanishVoiceProvider

/**
 * In-memory [SpanishVoiceProvider] for JVM unit tests (SF-8.4 / US-053).
 *
 * Pre-loaded with two sample voices; tests can replace [voices] before the subject is created.
 */
class FakeSpanishVoiceProvider(
    var voices: List<SpanishVoice> = DEFAULT_VOICES,
) : SpanishVoiceProvider {
    override fun availableVoices(): List<SpanishVoice> = voices

    companion object {
        val DEFAULT_VOICES: List<SpanishVoice> =
            listOf(
                SpanishVoice(name = "es-es-x-eef-local", displayName = "Español (ES) · femenino", isDefault = true),
                SpanishVoice(name = "es-es-x-eem-local", displayName = "Español (ES) · masculino", isDefault = false),
            )
    }
}
