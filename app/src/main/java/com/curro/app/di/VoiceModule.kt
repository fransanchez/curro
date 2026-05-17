package com.curro.app.di

import com.curro.app.data.voice.SystemSpanishVoiceProvider
import com.curro.app.data.voice.SystemSttClient
import com.curro.app.data.voice.SystemTtsClient
import com.curro.app.domain.repository.SpanishVoiceProvider
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TtsClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the voice pipeline (SF-2.1 / US-015 + SF-2.2 / US-016 + SF-8.4 / US-053).
 *
 * All three bindings are [Singleton] — the Android framework's [android.speech.SpeechRecognizer]
 * and [android.speech.tts.TextToSpeech] are heavy to instantiate (TextToSpeech holds a native
 * AudioService binding); the singletons wrap them and re-use the native instance across calls.
 * [SystemSpanishVoiceProvider] shares the same [TextToSpeechFactory] instance and is therefore
 * also a singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface VoiceModule {
    @Binds
    @Singleton
    fun bindSttClient(impl: SystemSttClient): SttClient

    @Binds
    @Singleton
    fun bindTtsClient(impl: SystemTtsClient): TtsClient

    @Binds
    @Singleton
    fun bindSpanishVoiceProvider(impl: SystemSpanishVoiceProvider): SpanishVoiceProvider
}
