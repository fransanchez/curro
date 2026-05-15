package com.curro.app.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin abstraction over `new TextToSpeech(context, listener)` so [SystemTtsClient] can be
 * unit-tested with a fake implementation that supplies a Mockk-fake [TextToSpeech] (the
 * codebase deliberately avoids Robolectric — see US-015 testing note).
 *
 * Production binding: [DefaultTextToSpeechFactory] constructs a real [TextToSpeech].
 * Test code injects a fake that returns a Mockk-faked [TextToSpeech] and exposes the
 * captured [TextToSpeech.OnInitListener].
 */
internal interface TextToSpeechFactory {
    /**
     * Constructs (and starts initialising) a [TextToSpeech] instance, supplying [listener]
     * for the init callback. Returns the constructed instance — the listener fires
     * asynchronously when the underlying engine is ready.
     */
    fun create(listener: TextToSpeech.OnInitListener): TextToSpeech
}

@Singleton
internal class DefaultTextToSpeechFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : TextToSpeechFactory {
        override fun create(listener: TextToSpeech.OnInitListener): TextToSpeech = TextToSpeech(context, listener)
    }

/**
 * Hilt binding for [TextToSpeechFactory]. Kept in its own module so [VoiceModule] stays
 * a pure `@Binds` interface (no `@Provides` mix-in).
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface TextToSpeechFactoryModule {
    @Binds
    @Singleton
    fun bindTextToSpeechFactory(impl: DefaultTextToSpeechFactory): TextToSpeechFactory
}
