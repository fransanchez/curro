package com.curro.app.data.telephony

import android.content.Context
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.curro.app.R
import com.curro.app.di.ApplicationScope
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.CallResponseVoice
import com.curro.app.domain.repository.ContactsProvider
import com.curro.app.domain.repository.SettingsRepository
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TtsClient
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SF-8.7 (US-056) — Curro's opt-in incoming-call assistant.
 *
 * **Outside the main FSM.** This service runs its own tiny state machine —
 * `onCallAdded(STATE_RINGING)` → resolve contact → announce by TTS → listen
 * for "sí"/"no" → answer / disconnect / ignore — totally separate from
 * [com.curro.app.assistant.AssistantStateMachine]. The launcher's
 * `onMicPressed` flow is unchanged; if the user happens to press the mic
 * mid-announcement, the main FSM's interrupt rule kicks in (the underlying
 * `SpeechRecognizer` is a single-session resource — the launcher's
 * `listen()` cancels this service's `listenForCallResponse()`).
 *
 * **Structural OFF guarantee.** This component is declared
 * `android:enabled="false"` in the manifest. With the toggle off, the
 * Telecom framework never binds it — no runtime check needed.
 * [IncomingCallModeController.enable] / [disable] flip it via
 * `setComponentEnabledSetting` at runtime when Fran toggles the config menu.
 * The defensive `settingsRepo.incomingCallModeEnabled.first()` check below
 * is belt-and-braces — it catches the rare race where Android still binds the
 * service after the component-disable call returns but before the OS reads
 * the new state.
 *
 * **Unknown numbers ring native.** When [ContactsProvider.findByNumber]
 * returns `null`, the service returns early — no announcement, no STT, no
 * manipulation of [Call]. The HyperOS call UI keeps ringing.
 *
 * **Privacy.** The telemetry event `incoming_call_announced` carries only
 * the `outcome` enum ("answered" / "declined" / "timed_out" / "other") —
 * never the number, name, or alias. Verified by
 * `TelemetryGuardrailIncomingCallTest`.
 */
@AndroidEntryPoint
class CurroInCallService : InCallService() {
    @Inject lateinit var aliasRepo: AliasRepository

    @Inject lateinit var contactsProvider: ContactsProvider

    @Inject lateinit var ttsClient: TtsClient

    @Inject lateinit var sttClient: SttClient

    @Inject lateinit var settingsRepo: SettingsRepository

    @Inject lateinit var telemetry: TelemetrySink

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject @ApplicationContext
    lateinit var appContext: Context

    /**
     * Telecom framework callback — fires when a new call is presented to
     * this InCallService. Always invoked on the Main thread per Android docs;
     * the actual work is delegated to a coroutine on [ApplicationScope]
     * (Main.immediate) so the announcement + listen pair can outlive a
     * potential `onCallRemoved` from a parallel decline.
     */
    @Suppress("DEPRECATION") // Call.state is deprecated in API 31+ in favour of details.state,
    // but Call.Details.state behaves identically and both paths exist on minSdk = 31. Sticking
    // with Call.state keeps the call-site readable; the deprecation marker is informational only.
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        if (call.state != Call.STATE_RINGING) return
        scope.launch { handleRinging(call) }
    }

    /**
     * Runs the announce-then-listen mini-flow for [call]. Internal so the
     * Robolectric test can drive it without going through the real Telecom
     * binding (which Robolectric doesn't simulate fully).
     *
     * Defensive checks (in order):
     *   1. Setting is still ON — bail if Fran toggled OFF between
     *      `onCallAdded` and the coroutine running.
     *   2. Phone number is extractable — bail if `handle` is null or the
     *      scheme isn't `tel:`.
     *   3. Contact lookup succeeds — emit `outcome = "other"` (the only
     *      bucket on the privacy whitelist for "we saw but didn't act") and
     *      bail. The phone keeps ringing native.
     *
     * On success: speak "Te está llamando X" with the alias if present,
     * else the display name. Then listen for "sí"/"no" and call
     * `call.answer()` / `call.disconnect()`. Anything else → no-op.
     */
    @Suppress("ReturnCount")
    internal suspend fun handleRinging(call: Call) {
        if (!settingsRepo.incomingCallModeEnabled.first()) return

        val number = call.details.handle?.schemeSpecificPart ?: return
        val contact =
            contactsProvider.findByNumber(number) ?: run {
                telemetry.event(
                    EVENT_INCOMING_CALL_ANNOUNCED,
                    mapOf(PROP_OUTCOME to OUTCOME_OTHER),
                )
                return
            }

        val alias =
            aliasRepo.observeAll().first()
                .firstOrNull { it.displayName == contact.displayName }
                ?.alias
        val spokenName = alias ?: contact.displayName
        val phrase = appContext.getString(R.string.copy_incoming_call_announce, spokenName)
        ttsClient.speak(phrase)

        val outcome =
            when (sttClient.listenForCallResponse().first()) {
                CallResponseVoice.Answer -> {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    OUTCOME_ANSWERED
                }
                CallResponseVoice.Decline -> {
                    call.disconnect()
                    OUTCOME_DECLINED
                }
                is CallResponseVoice.Other -> OUTCOME_OTHER
                is CallResponseVoice.Failed -> OUTCOME_TIMED_OUT
            }
        telemetry.event(
            EVENT_INCOMING_CALL_ANNOUNCED,
            mapOf(PROP_OUTCOME to outcome),
        )
    }

    private companion object {
        const val EVENT_INCOMING_CALL_ANNOUNCED = "incoming_call_announced"
        const val PROP_OUTCOME = "outcome"
        const val OUTCOME_ANSWERED = "answered"
        const val OUTCOME_DECLINED = "declined"
        const val OUTCOME_TIMED_OUT = "timed_out"
        const val OUTCOME_OTHER = "other"
    }
}
