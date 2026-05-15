package com.curro.app.data.voice

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import app.cash.turbine.test
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.SttClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SystemSttClient] (US-015 / SF-2.1).
 *
 * These tests verify the error-code → [CurroError] mapping and the Flow's emit order. The
 * underlying [SpeechRecognizer] is mockk-statically replaced so the test captures the
 * [RecognitionListener] passed via `setRecognitionListener`, then fires its callbacks
 * manually to drive the Flow to each terminal state.
 *
 * The 12 cases from US-015 §10:
 * - T1  Event.Partial emitted for each onPartialResults non-empty.
 * - T2  Event.Final emitted on onResults non-empty.
 * - T3  onResults empty → Event.Failed(SttNoMatch).
 * - T4  onError(ERROR_NO_MATCH) → SttNoMatch.
 * - T5  onError(ERROR_SPEECH_TIMEOUT) → SttTimeout.
 * - T6  onError(ERROR_INSUFFICIENT_PERMISSIONS) → PermissionDenied.
 * - T7  onError(ERROR_LANGUAGE_NOT_SUPPORTED) → SttVoicePackMissing.
 * - T8  onError(ERROR_LANGUAGE_UNAVAILABLE) → SttVoicePackMissing.
 * - T9  onError(ERROR_NETWORK) → SttError(code = ERROR_NETWORK).
 * - T10 onError(ERROR_AUDIO) → SttError(code = ERROR_AUDIO).
 * - T11 Partial → Final order preserved.
 * - T12 Cancelling the collecting coroutine triggers awaitClose (verifies cancel() + destroy()).
 */
@ExperimentalCoroutinesApi
@DisplayName("SystemSttClient — RecognitionListener mapping")
class SystemSttClientTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var recognizer: SpeechRecognizer
    private val capturedListener = slot<RecognitionListener>()

    private lateinit var client: SystemSttClient

    @BeforeEach
    fun setUp() {
        // SttClient's callbackFlow runs on Main.immediate; the test dispatcher takes over.
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        recognizer = mockk(relaxed = true)
        every { recognizer.setRecognitionListener(capture(capturedListener)) } answers {}

        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(context) } returns recognizer

        client = SystemSttClient(context)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T1 — partial results
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T1 — onPartialResults non-empty emits Event Partial`() =
        runTest {
            client.listen().test {
                capturedListener.captured.onPartialResults(bundleWithResults("hola"))
                val event = awaitItem()
                assertTrue(event is SttClient.Event.Partial, "Expected Partial but got $event")
                assertEquals("hola", (event as SttClient.Event.Partial).text)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `T1b — onPartialResults empty does NOT emit anything`() =
        runTest {
            client.listen().test {
                capturedListener.captured.onPartialResults(bundleWithResults(""))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // T2/T3 — onResults
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T2 — onResults non-empty emits Event Final and closes`() =
        runTest {
            client.listen().test {
                capturedListener.captured.onResults(bundleWithResults("hola curro"))
                val event = awaitItem()
                assertTrue(event is SttClient.Event.Final)
                assertEquals("hola curro", (event as SttClient.Event.Final).text)
                awaitComplete()
            }
        }

    @Test
    fun `T3 — onResults empty emits SttNoMatch and closes`() =
        runTest {
            client.listen().test {
                capturedListener.captured.onResults(bundleWithResults(""))
                val event = awaitItem()
                assertTrue(event is SttClient.Event.Failed)
                assertEquals(CurroError.SttNoMatch, (event as SttClient.Event.Failed).error)
                awaitComplete()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // T4–T10 — onError mapping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T4 — ERROR_NO_MATCH maps to SttNoMatch`() =
        errorMappingCase(
            SpeechRecognizer.ERROR_NO_MATCH,
            CurroError.SttNoMatch,
        )

    @Test
    fun `T5 — ERROR_SPEECH_TIMEOUT maps to SttTimeout`() =
        errorMappingCase(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, CurroError.SttTimeout)

    @Test
    fun `T6 — ERROR_INSUFFICIENT_PERMISSIONS maps to PermissionDenied`() =
        errorMappingCase(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, CurroError.PermissionDenied)

    @Test
    fun `T7 — ERROR_LANGUAGE_NOT_SUPPORTED maps to SttVoicePackMissing`() =
        errorMappingCase(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, CurroError.SttVoicePackMissing)

    @Test
    fun `T8 — ERROR_LANGUAGE_UNAVAILABLE maps to SttVoicePackMissing`() =
        errorMappingCase(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, CurroError.SttVoicePackMissing)

    @Test
    fun `T9 — ERROR_NETWORK falls through to SttError`() =
        errorMappingCase(
            code = SpeechRecognizer.ERROR_NETWORK,
            expected = CurroError.SttError(SpeechRecognizer.ERROR_NETWORK),
        )

    @Test
    fun `T10 — ERROR_AUDIO falls through to SttError`() =
        errorMappingCase(
            code = SpeechRecognizer.ERROR_AUDIO,
            expected = CurroError.SttError(SpeechRecognizer.ERROR_AUDIO),
        )

    // ─────────────────────────────────────────────────────────────────────────
    // T11 — partial → final order preserved
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T11 — partial then final preserves order`() =
        runTest {
            client.listen().test {
                capturedListener.captured.onPartialResults(bundleWithResults("ho"))
                capturedListener.captured.onPartialResults(bundleWithResults("hola"))
                capturedListener.captured.onResults(bundleWithResults("hola curro"))

                val first = awaitItem()
                val second = awaitItem()
                val third = awaitItem()

                assertEquals("ho", (first as SttClient.Event.Partial).text)
                assertEquals("hola", (second as SttClient.Event.Partial).text)
                assertEquals("hola curro", (third as SttClient.Event.Final).text)

                awaitComplete()
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // T12 — collector cancellation triggers awaitClose cleanup
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `T12 — cancelling collector calls cancel and destroy on the recognizer`() =
        runTest {
            client.listen().test {
                // No events emitted; cancel the collector.
                cancelAndIgnoreRemainingEvents()
            }

            verify { recognizer.cancel() }
            verify { recognizer.destroy() }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    // (Intent-extras configuration — EXTRA_LANGUAGE / EXTRA_PREFER_OFFLINE /
    // EXTRA_PARTIAL_RESULTS — is verified by code review against US-015 §8.2,
    // not by JVM test: android.content.Intent.action and getStringExtra return
    // default values under unitTests.isReturnDefaultValues = true since the
    // codebase deliberately avoids Robolectric. The intent construction is a
    // single-statement, declarative configuration with no branching logic, so
    // a code-review verification is appropriate.)

    /**
     * Drives the Flow until the listener has been captured (which happens during
     * `setRecognitionListener`, called inside the `callbackFlow` body the first time the
     * Flow is collected), fires `onError(code)`, and asserts the single emitted [Failed]
     * event carries [expected].
     */
    private fun errorMappingCase(
        code: Int,
        expected: CurroError,
    ) = runTest {
        client.listen().test {
            capturedListener.captured.onError(code)
            val event = awaitItem()
            assertTrue(event is SttClient.Event.Failed)
            assertEquals(expected, (event as SttClient.Event.Failed).error)
            awaitComplete()
        }
    }

    /**
     * Builds a result Bundle as `RecognitionListener` does: a single
     * `RESULTS_RECOGNITION` ArrayList<String> with the supplied text as the head.
     * Empty [text] simulates `onResults` with an empty array (the no-match case).
     */
    private fun bundleWithResults(text: String): Bundle {
        val bundle = mockk<Bundle>(relaxed = true)
        val list = if (text.isEmpty()) arrayListOf() else arrayListOf(text)
        every { bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns list
        return bundle
    }
}
