package com.curro.app.di

import com.curro.app.data.voice.SystemSttClient
import com.curro.app.domain.repository.SttClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the voice pipeline (SF-2.1 / US-015 + SF-2.2 / US-016).
 *
 * Both clients are [Singleton] — the Android framework's [android.speech.SpeechRecognizer]
 * and [android.speech.tts.TextToSpeech] are heavy to instantiate (TextToSpeech holds a
 * native AudioService binding); the singleton wraps them and re-uses the native instance
 * across calls.
 *
 * US-016 (SF-2.2) will add the `TtsClient -> SystemTtsClient` binding to this same module.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface VoiceModule {
    @Binds
    @Singleton
    fun bindSttClient(impl: SystemSttClient): SttClient
}
