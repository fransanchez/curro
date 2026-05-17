package com.curro.app.data.telephony

import android.telecom.Call
import android.telecom.VideoProfile
import androidx.test.core.app.ApplicationProvider
import com.curro.app.assistant.FakeSettingsRepository
import com.curro.app.data.local.AliasSource
import com.curro.app.domain.model.Contact
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.AliasView
import com.curro.app.domain.repository.CallResponseVoice
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TtsClient
import com.curro.app.util.FakeAliasRepository
import com.curro.app.util.FakeContactsProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [CurroInCallService] (SF-8.7 / US-056).
 *
 * The service's [android.telecom.InCallService.onCallAdded] callback delegates to
 * an internal `handleRinging(call)` suspend function — that's the seam these tests
 * drive (Robolectric does not fully simulate the Telecom binding lifecycle, so we
 * call the suspend handler directly).
 *
 * Eight cases per the brief:
 *   1. Known contact with alias → announces with alias + `call.answer()` on "sí".
 *   2. Known contact without alias → announces with display name.
 *   3. Unknown number → silent (no announce, no manipulation of `call`).
 *   4. Toggle off (defensive check) → returns early; no manipulation.
 *   5. "sí" → `call.answer(VideoProfile.STATE_AUDIO_ONLY)`.
 *   6. "no" → `call.disconnect()`.
 *   7. `Other` → no manipulation (let it ring).
 *   8. `Failed` (timeout) → no manipulation.
 *
 * `Call` is `final` in the Android framework, so we mock it via mockk's
 * `relaxed = true` (the Robolectric runner makes the JVM happy with the
 * type even though we never go through the system Telecom binder).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CurroInCallServiceTest {
    private lateinit var aliasRepo: FakeAliasRepository
    private lateinit var contactsProvider: FakeContactsProvider
    private lateinit var ttsClient: TtsClient
    private lateinit var sttClient: SttClient
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var telemetry: TelemetrySink
    private lateinit var service: CurroInCallService

    private val pepito =
        Contact(
            lookupKey = "lk-pepito",
            displayName = "Pepito Martínez",
            phoneNumbers = listOf("+34600123456"),
            photoUri = null,
        )

    private val lucia =
        Contact(
            lookupKey = "lk-lucia",
            displayName = "Lucía Ruiz",
            phoneNumbers = listOf("+34600999888"),
            photoUri = null,
        )

    @Before
    fun setUp() {
        aliasRepo = FakeAliasRepository()
        contactsProvider = FakeContactsProvider()
        ttsClient = mockk(relaxed = true)
        sttClient = mockk(relaxed = true)
        settingsRepo = FakeSettingsRepository()
        telemetry = mockk(relaxed = true)

        // Defensive: the service short-circuits if the setting is off. Start ON for
        // happy-path tests; flipping it off per-test is one line.
        runBlocking { settingsRepo.setIncomingCallModeEnabled(true) }
        // Clear the setter-tracking from the setup-time enable() so per-test
        // assertions see a clean slate.
        settingsRepo.incomingCallModeSetCalls.clear()

        service =
            CurroInCallService().apply {
                aliasRepo = this@CurroInCallServiceTest.aliasRepo
                contactsProvider = this@CurroInCallServiceTest.contactsProvider
                ttsClient = this@CurroInCallServiceTest.ttsClient
                sttClient = this@CurroInCallServiceTest.sttClient
                settingsRepo = this@CurroInCallServiceTest.settingsRepo
                telemetry = this@CurroInCallServiceTest.telemetry
                appContext = ApplicationProvider.getApplicationContext()
                // scope is unused by handleRinging (callers wrap it themselves) — but
                // the property is `lateinit`, so we must initialise it to satisfy Kotlin.
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            }
    }

    @Suppress("DEPRECATION") // Call.state is deprecated in API 31+ — see service-side
    // suppression. Both the production code and the mock use the same accessor.
    private fun fakeRingingCall(number: String? = "+34600123456"): Call {
        val call: Call = mockk(relaxed = true)
        val details: Call.Details = mockk(relaxed = true)
        coEvery { call.state } returns Call.STATE_RINGING
        coEvery { call.details } returns details
        if (number != null) {
            val handle = android.net.Uri.parse("tel:$number")
            coEvery { details.handle } returns handle
        } else {
            coEvery { details.handle } returns null
        }
        return call
    }

    // ── 1. Known contact + alias → announces with alias, answers on "sí" ──────

    @Test
    fun `known contact with alias announces with alias name`() =
        runBlocking {
            contactsProvider.findByNumberResult["+34600999888"] = lucia
            aliasRepo.observeAllStream.value =
                listOf(
                    AliasView(
                        alias = "mi hija",
                        displayName = "Lucía Ruiz",
                        source = AliasSource.LEARNED,
                        useCount = 3,
                    ),
                )
            coEvery { sttClient.listenForCallResponse() } returns flowOf(CallResponseVoice.Answer)

            service.handleRinging(fakeRingingCall("+34600999888"))

            coVerify { ttsClient.speak(match { it.contains("mi hija") }, any()) }
            verify { telemetry.event("incoming_call_announced", mapOf("outcome" to "answered")) }
        }

    // ── 2. Known contact without alias → announces with display name ──────────

    @Test
    fun `known contact without alias announces with display name`() =
        runBlocking {
            contactsProvider.findByNumberResult["+34600123456"] = pepito
            // No aliases in the stream.
            coEvery { sttClient.listenForCallResponse() } returns flowOf(CallResponseVoice.Answer)

            service.handleRinging(fakeRingingCall("+34600123456"))

            coVerify { ttsClient.speak(match { it.contains("Pepito Martínez") }, any()) }
        }

    // ── 3. Unknown number → silent ───────────────────────────────────────────

    @Test
    fun `unknown number does not announce or manipulate call`() =
        runBlocking {
            // findByNumberResult has no entry — returns null.
            val call = fakeRingingCall("+34900000000")

            service.handleRinging(call)

            coVerify(exactly = 0) { ttsClient.speak(any(), any()) }
            verify(exactly = 0) { call.answer(any()) }
            verify(exactly = 0) { call.disconnect() }
            // Telemetry: "other" bucket so Fran can see how many unknown calls came in.
            verify { telemetry.event("incoming_call_announced", mapOf("outcome" to "other")) }
        }

    // ── 4. Toggle off (defensive) → return early ──────────────────────────────

    @Test
    fun `setting disabled returns early even when called`() =
        runBlocking {
            settingsRepo.setIncomingCallModeEnabled(false)
            contactsProvider.findByNumberResult["+34600123456"] = pepito
            val call = fakeRingingCall()

            service.handleRinging(call)

            coVerify(exactly = 0) { ttsClient.speak(any(), any()) }
            coVerify(exactly = 0) { sttClient.listenForCallResponse() }
            verify(exactly = 0) { call.answer(any()) }
            verify(exactly = 0) { call.disconnect() }
            verify(exactly = 0) { telemetry.event(any(), any()) }
        }

    // ── 5. "sí" → call.answer(STATE_AUDIO_ONLY) ──────────────────────────────

    @Test
    fun `answer response calls call answer with STATE_AUDIO_ONLY`() =
        runBlocking {
            contactsProvider.findByNumberResult["+34600123456"] = pepito
            coEvery { sttClient.listenForCallResponse() } returns flowOf(CallResponseVoice.Answer)
            val call = fakeRingingCall()

            service.handleRinging(call)

            verify { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
            verify(exactly = 0) { call.disconnect() }
            verify { telemetry.event("incoming_call_announced", mapOf("outcome" to "answered")) }
        }

    // ── 6. "no" → call.disconnect ────────────────────────────────────────────

    @Test
    fun `decline response calls call disconnect`() =
        runBlocking {
            contactsProvider.findByNumberResult["+34600123456"] = pepito
            coEvery { sttClient.listenForCallResponse() } returns flowOf(CallResponseVoice.Decline)
            val call = fakeRingingCall()

            service.handleRinging(call)

            verify { call.disconnect() }
            verify(exactly = 0) { call.answer(any()) }
            verify { telemetry.event("incoming_call_announced", mapOf("outcome" to "declined")) }
        }

    // ── 7. Other → no manipulation (let it ring) ──────────────────────────────

    @Test
    fun `other response does not answer or disconnect`() =
        runBlocking {
            contactsProvider.findByNumberResult["+34600123456"] = pepito
            coEvery { sttClient.listenForCallResponse() } returns flowOf(CallResponseVoice.Other("qué hora es"))
            val call = fakeRingingCall()

            service.handleRinging(call)

            verify(exactly = 0) { call.answer(any()) }
            verify(exactly = 0) { call.disconnect() }
            verify { telemetry.event("incoming_call_announced", mapOf("outcome" to "other")) }
        }

    // ── 8. Failed (timeout/error) → no manipulation, telemetry timed_out ──────

    @Test
    fun `failed response does not answer or disconnect and reports timed_out`() =
        runBlocking {
            contactsProvider.findByNumberResult["+34600123456"] = pepito
            coEvery { sttClient.listenForCallResponse() } returns
                flowOf(CallResponseVoice.Failed(CurroError.SttTimeout))
            val call = fakeRingingCall()

            service.handleRinging(call)

            verify(exactly = 0) { call.answer(any()) }
            verify(exactly = 0) { call.disconnect() }
            verify { telemetry.event("incoming_call_announced", mapOf("outcome" to "timed_out")) }
        }

    // ── Defensive: null handle (some carriers / private numbers) ──────────────

    @Test
    fun `null handle returns early without announcing or telemetry`() =
        runBlocking {
            val call = fakeRingingCall(number = null)

            service.handleRinging(call)

            coVerify(exactly = 0) { ttsClient.speak(any(), any()) }
            verify(exactly = 0) { telemetry.event(any(), any()) }
        }

    // ── Defensive: the suspend handler is the only seam; assert tests can run ─

    @Test
    fun `service instantiates with all collaborators wired`() {
        // Smoke: a freshly-built service with the test wiring has all lateinit slots filled.
        // (lateinit access throws UninitializedPropertyAccessException if not set — these
        // calls are themselves the assertion.)
        assertEquals(this.aliasRepo, service.aliasRepo)
        assertEquals(this.contactsProvider, service.contactsProvider)
        assertEquals(this.ttsClient, service.ttsClient)
    }
}
