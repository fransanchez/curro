package com.curro.app.data.voice

import android.speech.tts.TextToSpeech
import com.curro.app.domain.repository.SpanishVoice
import com.curro.app.domain.repository.SpanishVoiceProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SpanishVoiceProvider] backed by [TextToSpeech.getVoices] (SF-8.4 / US-053).
 *
 * Uses the same [TextToSpeechFactory]-created instance as [SystemTtsClient] — the factory is
 * shared via Hilt's [Singleton] scope. [tts] may be null before the TTS engine initialises;
 * [availableVoices] returns an empty list in that case (the UI shows an informational message).
 *
 * **Sort order**: female before male (this user's preference is a personal one; Fran can override
 * via the voice picker), then by quality descending. Within the same gender and quality, voices
 * are sorted alphabetically by name for stability.
 *
 * Only voices whose locale language is `"es"` are included.
 */
@Singleton
internal class SystemSpanishVoiceProvider
    @Inject
    constructor(
        factory: TextToSpeechFactory,
    ) : SpanishVoiceProvider {
        @Volatile private var tts: TextToSpeech? = null

        init {
            // The init result is not needed — we only call tts.voices, which is available
            // after the engine initialises. If the engine is not yet ready, availableVoices()
            // returns an empty list gracefully.
            tts = factory.create(TextToSpeech.OnInitListener { /* status not used */ })
        }

        override fun availableVoices(): List<SpanishVoice> {
            val current = tts ?: return emptyList()
            val defaultVoice = current.defaultVoice
            return current.voices
                .orEmpty()
                .filter { it.locale.language == "es" }
                .sortedWith(
                    compareBy(
                        { genderSortKey(it.name) },
                        { -it.quality },
                        { it.name },
                    ),
                ).map { voice ->
                    SpanishVoice(
                        name = voice.name,
                        displayName = buildDisplayName(voice.name, voice.locale.country),
                        isDefault = voice.name == defaultVoice?.name,
                    )
                }
        }

        private companion object {
            /**
             * Sort female voices before male. Returns 0 for female names, 1 for male, 2 for
             * unknown — so females float to the top.
             */
            private fun genderSortKey(voiceName: String): Int {
                val lower = voiceName.lowercase()
                return when {
                    lower.contains("female") -> 0
                    lower.contains("male") -> 1
                    else -> 2
                }
            }

            /**
             * Produces a short display label from the raw voice name and country code.
             * Examples: "Español (ES) · femenino", "Español (MX) · masculino".
             * Falls back to the raw voice name when neither gender word is present.
             */
            private fun buildDisplayName(
                name: String,
                country: String,
            ): String {
                val lower = name.lowercase()
                val locale = if (country.isNotBlank()) "($country)" else ""
                val gender =
                    when {
                        lower.contains("female") -> "femenino"
                        lower.contains("male") -> "masculino"
                        else -> null
                    }
                return if (gender != null) {
                    "Español $locale · $gender".trim()
                } else {
                    name
                }
            }
        }
    }
