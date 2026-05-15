package com.curro.app.data.voice

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.TtsClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SystemTtsClient] (US-016 / SF-2.2).
 *
 * The real [TextToSpeech] is replaced via a fake [TextToSpeechFactory] that returns a
 * Mockk-faked instance. The factory captures the [TextToSpeech.OnInitListener] so the test
 * can fire `onInit(SUCCESS)` or `onInit(ERROR)` synchronously; the
 * [UtteranceProgressListener] passed to `setOnUtteranceProgressListener` is likewise
 * captured so the test can drive each terminal state.
 *
 * The 5 cases from US-016 §10 plus a sixth that pins the init-listener language-missing path:
 * - T1 happy path: onDone → SpeakResult.Completed.
 * - T2 barge-in: coroutine cancellation invokes tts.stop(); onStop → SpeakResult.Cancelled.
 * - T3 native error: onError(id, 42) → SpeakResult.Failed(TtsError(42)).
 * - T4 language missing: setLanguage = LANG_NOT_SUPPORTED → speak() immediately Failed.
 * - T5 id mismatch: onDone("other") does NOT resume — the coroutine waits for its own id.
 * - T6 init status != SUCCESS → speak() returns Failed(TtsLanguageMissing).
 */
@ExperimentalCoroutinesApi
@DisplayName("SystemTtsClient — UtteranceProgressListener mapping")
class SystemTtsClientTest {
    private lateinit var fakeTts: TextToSpeech
    private lateinit var factory: FakeTextToSpeechFactory
    private val progressListenerSlot = slot<UtteranceProgressListener>()

    @BeforeEach
    fun setUp() {
        fakeTts = mockk(relaxed = true)
        every { fakeTts.setLanguage(any()) } returns TextToSpeech.LANG_AVAILABLE
        every { fakeTts.voices } returns emptySet()
        every { fakeTts.setOnUtteranceProgressListener(capture(progressListenerSlot)) } returns TextToSpeech.SUCCESS
        every { fakeTts.speak(any<CharSequence>(), any(), any(), any()) } returns TextToSpeech.SUCCESS
        factory = FakeTextToSpeechFactory(fakeTts)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T1 — happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T1 — onDone resumes with Completed`() =
        runTest {
            val client = SystemTtsClient(factory)
            factory.fireInitSuccess()

            val deferred = async { client.speak("hola", utteranceId = "utt-1") }
            advanceUntilIdle()

            progressListenerSlot.captured.onDone("utt-1")
            val result = deferred.await()

            assertEquals(TtsClient.SpeakResult.Completed, result)
            verify { fakeTts.speak("hola", TextToSpeech.QUEUE_FLUSH, null, "utt-1") }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // T2 — barge-in via cancellation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T2 — coroutine cancellation invokes tts stop and onStop resumes Cancelled`() =
        runTest {
            val client = SystemTtsClient(factory)
            factory.fireInitSuccess()

            var captured: TtsClient.SpeakResult? = null
            val job: Job =
                launch {
                    captured = client.speak("hola", utteranceId = "utt-2")
                }
            advanceUntilIdle()

            // Cancel mid-speak. The continuation's invokeOnCancellation fires tts.stop().
            job.cancel()
            advanceUntilIdle()

            verify { fakeTts.stop() }
            // The job is cancelled — captured may be null because the resumption races with
            // cancellation. The functional contract checked here is that tts.stop() ran.
            // The onStop-resumes-Cancelled path is exercised by the separate test below.
            assertTrue(captured == null || captured is TtsClient.SpeakResult.Cancelled)
        }

    @Test
    fun `T2b — onStop callback resumes Cancelled`() =
        runTest {
            val client = SystemTtsClient(factory)
            factory.fireInitSuccess()

            val deferred = async { client.speak("hola", utteranceId = "utt-2b") }
            advanceUntilIdle()

            progressListenerSlot.captured.onStop("utt-2b", true)
            val result = deferred.await()

            assertEquals(TtsClient.SpeakResult.Cancelled, result)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // T3 — native error
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T3 — onError with code resumes Failed with TtsError`() =
        runTest {
            val client = SystemTtsClient(factory)
            factory.fireInitSuccess()

            val deferred = async { client.speak("hola", utteranceId = "utt-3") }
            advanceUntilIdle()

            progressListenerSlot.captured.onError("utt-3", 42)
            val result = deferred.await()

            assertEquals(TtsClient.SpeakResult.Failed(CurroError.TtsError(42)), result)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // T4 — language missing
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T4 — setLanguage LANG_NOT_SUPPORTED resolves speak immediately with Failed`() =
        runTest {
            every { fakeTts.setLanguage(any()) } returns TextToSpeech.LANG_NOT_SUPPORTED
            val client = SystemTtsClient(factory)
            factory.fireInitSuccess()

            val result = client.speak("hola", utteranceId = "utt-4")

            assertEquals(TtsClient.SpeakResult.Failed(CurroError.TtsLanguageMissing), result)
        }

    @Test
    fun `T4b — setLanguage LANG_MISSING_DATA resolves speak immediately with Failed`() =
        runTest {
            every { fakeTts.setLanguage(any()) } returns TextToSpeech.LANG_MISSING_DATA
            val client = SystemTtsClient(factory)
            factory.fireInitSuccess()

            val result = client.speak("hola", utteranceId = "utt-4b")

            assertEquals(TtsClient.SpeakResult.Failed(CurroError.TtsLanguageMissing), result)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // T5 — id mismatch
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T5 — onDone with different id does NOT resume the coroutine`() =
        runTest {
            val client = SystemTtsClient(factory)
            factory.fireInitSuccess()

            val deferred = async { client.speak("hola", utteranceId = "mine") }
            advanceUntilIdle()

            // Fire onDone with a DIFFERENT id — should be ignored.
            progressListenerSlot.captured.onDone("not-mine")

            // The deferred should NOT be completed. Give time to ensure no surprise resume.
            delay(50)
            assertTrue(deferred.isActive, "Expected speak() to still be suspended for the wrong-id callback")

            // Now fire the correct id and observe completion.
            progressListenerSlot.captured.onDone("mine")
            assertEquals(TtsClient.SpeakResult.Completed, deferred.await())
        }

    // ─────────────────────────────────────────────────────────────────────────
    // T6 — init failed (status != SUCCESS)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T6 — init status non-SUCCESS resolves speak with Failed`() =
        runTest {
            val client = SystemTtsClient(factory)
            factory.fireInit(TextToSpeech.ERROR)

            val result = client.speak("hola", utteranceId = "utt-6")

            assertEquals(TtsClient.SpeakResult.Failed(CurroError.TtsLanguageMissing), result)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // empty text guard
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `empty text throws IllegalArgumentException`() =
        runTest {
            val client = SystemTtsClient(factory)
            factory.fireInitSuccess()

            try {
                client.speak("", utteranceId = "utt-empty")
                throw AssertionError("Expected IllegalArgumentException")
            } catch (expected: IllegalArgumentException) {
                // ok
            }
        }
}

/**
 * Test-only factory: holds the listener supplied by [SystemTtsClient]'s `init` block and
 * exposes [fireInitSuccess] / [fireInit] so the test fires the callback synchronously.
 */
private class FakeTextToSpeechFactory(
    private val tts: TextToSpeech,
) : TextToSpeechFactory {
    private lateinit var capturedListener: TextToSpeech.OnInitListener

    override fun create(listener: TextToSpeech.OnInitListener): TextToSpeech {
        capturedListener = listener
        return tts
    }

    fun fireInitSuccess() {
        capturedListener.onInit(TextToSpeech.SUCCESS)
    }

    fun fireInit(status: Int) {
        capturedListener.onInit(status)
    }
}
