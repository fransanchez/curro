package com.curro.app.assistant

import android.Manifest
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.curro.app.BuildConfig
import com.curro.app.R
import com.curro.app.data.ml.FunctionCallValidator
import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.PermissionGate
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.di.ApplicationScope
import com.curro.app.di.MainDispatcher
import com.curro.app.domain.catalog.Fase1Catalog
import com.curro.app.domain.handler.HandlerDispatcher
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.Contact
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.PromptContext
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.ConfirmationVoice
import com.curro.app.domain.repository.FunctionCallEngine
import com.curro.app.domain.repository.PickerVoice
import com.curro.app.domain.repository.SettingsRepository
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TtsClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SF-5.2 — the single owner of the spec §4 pipeline.
 *
 * Wires capture → STT → FunctionGemma → handler → TTS through the SF-5.1
 * [AssistantStateMachine]. **Every** mutation of in-flight work happens here.
 * Composables and the VM observe [state] (read-only) and call back through
 * the public entry points ([onMicPressed], [onHomePressed], [onPermissionResult],
 * and Phase-6's [onUserConfirmed]/[onUserRejected]).
 *
 * **The load-bearing rule (interrupt-by-button, spec §6 closing paragraph):**
 * every public entry point starts with `currentJob?.cancel(); ttsClient.stop();
 * sttClient.cancel()` **before** issuing the FSM transition. There is no
 * conditional branch that skips this — that is what makes the interrupt rule
 * non-bypassable (SF-5.3 covers this with the from-every-state grid).
 *
 * **Threading:** `@ApplicationScope` is `SupervisorJob + Main.immediate`
 * (see [com.curro.app.di.CoroutineModule]). Heavy work
 * (`engine.decide(...)`, `dispatcher.dispatch(...)`) jumps to IO inside the
 * collaborators; this class orchestrates on the main thread so the
 * `cancel()` propagation is deterministic.
 *
 * **Phase 5 boundary**: `HandlerResult.NeedsConfirmation` is **auto-confirmed**
 * here (the Phase-4 behaviour). Phase 6 replaces that branch with a real
 * `Confirming` transition driven by `ConfidencePolicy`. The
 * [onUserConfirmed]/[onUserRejected] stubs exist now so Phase 6 only fills the
 * bodies.
 */
@Singleton
@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
class AssistantCoordinator
    @Inject
    constructor(
        private val stateMachine: AssistantStateMachine,
        private val sttClient: SttClient,
        private val ttsClient: TtsClient,
        private val engine: FunctionCallEngine,
        private val validator: FunctionCallValidator,
        private val dispatcher: HandlerDispatcher,
        private val timeProvider: TimeProvider,
        private val telemetry: TelemetrySink,
        private val recordAudioGate: PermissionGate,
        private val readContactsGate: ReadContactsPermissionGate,
        private val callPhoneGate: CallPhonePermissionGate,
        private val clock: Clock,
        // SF-5.4 (US-038) — the consecutive-STT-failure counter. Reset at
        // `onFinalTranscript`; incremented at `onSttFailed` (1st/2nd/3rd → fail_1/2/3
        // copy; ≥ 3 resets so the next mic press starts at 1).
        private val sttFailureCounter: SttFailureCounter,
        // SF-6.1 (US-041) — confidence-graded confirmation policy. Pure
        // function: takes primitives, returns Execute|Confirm|Clarify.
        private val confidencePolicy: ConfidencePolicy,
        // SF-6.1 (US-041) — DataStore-backed execute/confirm thresholds + the
        // always-confirm toggle. Read once per turn via `.first()` (constant-
        // time after the first cold read).
        private val settingsRepository: SettingsRepository,
        // SF-7.2 (US-046) — Room-backed alias repository. Read in buildContext() to
        // inject the top-10 aliases into the FunctionGemma prompt context.
        private val aliasRepository: AliasRepository,
        @ApplicationContext private val appContext: Context,
        @ApplicationScope private val scope: CoroutineScope,
        @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    ) {
        /** Read-only view of the FSM. The VM exposes this to the launcher composable. */
        val state: StateFlow<AssistantState> = stateMachine.state

        private val mutableSideEffects =
            MutableSharedFlow<AssistantSideEffect>(replay = 0, extraBufferCapacity = SIDE_EFFECT_BUFFER)
        val sideEffects: SharedFlow<AssistantSideEffect> = mutableSideEffects

        /** The active turn's Job. Cancelled-and-replaced by every public entry point. */
        private var currentJob: Job? = null

        /** Per-turn permission-retry flags. Reset on every fresh [onMicPressed]. */
        private var readContactsAutoRetried = false
        private var callPhoneAutoRetried = false

        /** The last `FunctionCall` the validator produced, retained for permission auto-retry. */
        private var pendingFunctionCall: FunctionCall? = null
        private var pendingTranscript: String = ""

        // SF-6.2 (US-042) — confirmation jobs. Both are launched when the FSM
        // enters Confirming; whichever resolves first cancels the other. The
        // interrupt-by-button rule (SF-5.3) is extended through [cancelInFlight]
        // to also cancel these two.
        private var confirmationListenerJob: Job? = null
        private var confirmationTimeoutJob: Job? = null
        private var pendingActionRef: PendingAction? = null

        // SF-6.3 (US-043) — picker job. Reuses the SF-6.2 confirmationTimeoutJob
        // for the 10-s silence timer (the same semantic).
        private var pickerListenerJob: Job? = null

        /**
         * SF-6.3 — number of consecutive picker misses (`Other`/`Failed` from
         * the constrained STT pass). First miss: re-speak the prompt and
         * re-listen. Second miss: give up via `copy_disambig_give_up(_masc)`.
         * Reset every time a fresh `NeedsContactPick` lands.
         */
        private var disambigMissCount = 0

        // ─────────────────────────────── public API ───────────────────────────────

        /**
         * Start (or interrupt-then-restart) a turn. **Always** cancels in-flight work
         * before issuing `MicPressed` — this is the interrupt-by-button rule (SF-5.3
         * pins this code in place; do not bypass).
         */
        fun onMicPressed() {
            scope.launch {
                cancelInFlight()
                readContactsAutoRetried = false
                callPhoneAutoRetried = false
                pendingFunctionCall = null
                pendingTranscript = ""
                stateMachine.transition(AssistantEvent.MicPressed(timeProvider.now()))
                if (!recordAudioGate.isGranted()) {
                    mutableSideEffects.emit(
                        AssistantSideEffect.RequestPermission(Manifest.permission.RECORD_AUDIO),
                    )
                    return@launch
                }
                currentJob = scope.launch { runListenLoop() }
            }
        }

        /** Reset the FSM to `Idle`. Used by SF-5.6's `MainActivity.onNewIntent`. */
        fun onHomePressed() {
            scope.launch {
                cancelInFlight()
                stateMachine.transition(AssistantEvent.HomePressed)
            }
        }

        /**
         * SF-6.2 (US-042) — user tapped SÍ (or said "sí" via the constrained
         * STT pass). Only valid when `pendingAction.kind is YesNo`. Cancels
         * the confirmation jobs, transitions `Confirming →
         * Executing(copy_calling_confirmed)`, speaks the line, then invokes
         * the `onConfirm` lambda for the side effect.
         *
         * Pinned: TTS suspends to completion BEFORE the side-effect Intent
         * fires; otherwise the Android call screen overlays the "Vale,
         * llamando." TTS and the user gets a confusing audio jump.
         */
        fun onUserConfirmed() {
            scope.launch {
                val action = pendingActionRef ?: return@launch
                val kind = action.kind as? PendingAction.Kind.YesNo ?: return@launch
                cancelConfirmationJobs()
                val confirmedSpeech = appContext.getString(R.string.copy_calling_confirmed)
                stateMachine.transition(
                    AssistantEvent.UserConfirmed(speech = confirmedSpeech, screen = null),
                )
                ttsClient.speak(confirmedSpeech)
                kind.onConfirm()
                stateMachine.transition(AssistantEvent.ExecutionDone)
                pendingActionRef = null
            }
        }

        /**
         * SF-6.2 (US-042) — user tapped NO (or said "no" via voice). Speaks
         * "Vale, no llamo." and goes home. The `onConfirm` lambda is NOT
         * invoked (no call placed; no handler dispatched).
         */
        fun onUserRejected() {
            scope.launch {
                val action = pendingActionRef ?: return@launch
                action.kind as? PendingAction.Kind.YesNo ?: return@launch
                cancelConfirmationJobs()
                val rejectedSpeech = appContext.getString(R.string.copy_cancel_no_call)
                stateMachine.transition(
                    AssistantEvent.UserRejected(speech = rejectedSpeech, screen = null),
                )
                ttsClient.speak(rejectedSpeech)
                stateMachine.transition(AssistantEvent.ExecutionDone)
                pendingActionRef = null
            }
        }

        /**
         * SF-6.3 (US-043) — user picked a candidate via the picker overlay
         * (tap or voice). Invokes the handler's `onPick(contact)`; the
         * handler's `Spoken`/`Failed` result drives `Executing → Idle` with
         * the right Spanish line.
         */
        fun onPickerPicked(contact: Contact) {
            scope.launch {
                val action = pendingActionRef ?: return@launch
                val kind = action.kind as? PendingAction.Kind.PickContact ?: return@launch
                cancelPickerJobs()
                val result = kind.onPick(contact)
                renderPickerOutcome(result)
            }
        }

        /**
         * SF-6.3 (US-043) — user said "ninguna" or tapped the "Ninguna" row.
         * Invokes `onPick(null)`; the handler returns `copy_cancel_no_call`.
         */
        fun onPickerNone() {
            scope.launch {
                val action = pendingActionRef ?: return@launch
                val kind = action.kind as? PendingAction.Kind.PickContact ?: return@launch
                cancelPickerJobs()
                val result = kind.onPick(null)
                renderPickerOutcome(result)
            }
        }

        private suspend fun renderPickerOutcome(result: HandlerResult) {
            val speech =
                when (result) {
                    is HandlerResult.Spoken -> result.speech
                    is HandlerResult.Failed -> result.speech
                    else -> appContext.getString(R.string.copy_cancel_no_call)
                }
            stateMachine.transition(
                AssistantEvent.UserConfirmed(speech = speech, screen = null),
            )
            ttsClient.speak(speech)
            stateMachine.transition(AssistantEvent.ExecutionDone)
            pendingActionRef = null
        }

        /** Result of an [AssistantSideEffect.RequestPermission]. */
        fun onPermissionResult(
            permission: String,
            granted: Boolean,
        ) {
            scope.launch {
                when (permission) {
                    Manifest.permission.RECORD_AUDIO -> handleRecordAudioResult(granted)
                    Manifest.permission.READ_CONTACTS -> handleReadContactsResult(granted)
                    Manifest.permission.CALL_PHONE -> handleCallPhoneResult(granted)
                }
            }
        }

        // ───────────────────────────── inner machinery ────────────────────────────

        private fun cancelInFlight() {
            currentJob?.cancel()
            cancelConfirmationJobs()
            cancelPickerJobs()
            ttsClient.stop()
            sttClient.cancel()
        }

        /** SF-6.2 (US-042) — cancel the constrained STT pass + the 10-s timer. */
        private fun cancelConfirmationJobs() {
            confirmationListenerJob?.cancel()
            confirmationListenerJob = null
            confirmationTimeoutJob?.cancel()
            confirmationTimeoutJob = null
        }

        /** SF-6.3 (US-043) — cancel the picker STT pass + the 10-s timer. */
        private fun cancelPickerJobs() {
            pickerListenerJob?.cancel()
            pickerListenerJob = null
            // The 10-s timer is the same job slot as the SF-6.2 timer — same
            // semantic, just shared.
            confirmationTimeoutJob?.cancel()
            confirmationTimeoutJob = null
        }

        /**
         * SF-6.2 (US-042) — start the voice yes/no listener + the 10-s silence
         * timer. Called immediately after the prompt TTS finishes (the spec
         * requires the prompt to be spoken FIRST; the existing [ttsClient.speak]
         * is `suspend` so by the time this runs the audio is over).
         *
         * Both jobs race. Whichever resolves first cancels the other via
         * [cancelConfirmationJobs] inside `onUserConfirmed` / `onUserRejected` /
         * `onConfirmationTimedOut`. The interrupt-by-button rule (SF-5.3) also
         * cancels both via [cancelInFlight].
         */
        private fun startConfirmationListening(
            pendingAction: PendingAction,
            expiresAtMs: Long,
        ) {
            pendingActionRef = pendingAction
            confirmationListenerJob = scope.launch { runConfirmationListenerLoop() }
            confirmationTimeoutJob =
                scope.launch {
                    val remaining = (expiresAtMs - timeProvider.now()).coerceAtLeast(0L)
                    delay(remaining)
                    if (state.value is AssistantState.Confirming) {
                        onConfirmationTimedOut()
                    }
                }
        }

        /**
         * SF-6.2 helper for [startConfirmationListening]. Loops until either:
         *  - the user says yes/no (terminal events resolve via
         *    [onUserConfirmed] / [onUserRejected] and the outer caller cancels
         *    this job from there);
         *  - or the inner Flow closes without emitting anything (the user said
         *    nothing and the recogniser timed out internally) — in which case
         *    the 10-s outer timer wins.
         *
         * The `resolved` flag is *informative*: the resolution path cancels
         * this job via [cancelConfirmationJobs], so we exit on cooperative
         * cancellation. The `!sawAnyEvent` guard protects against busy-
         * spinning on an instantly-completing empty Flow (the default mockk
         * stub in tests).
         */
        private suspend fun runConfirmationListenerLoop() {
            while (currentCoroutineContext().isActive) {
                var sawAnyEvent = false
                var resolved = false
                sttClient.listenForConfirmation().collect { event ->
                    sawAnyEvent = true
                    if (handleConfirmationVoice(event)) {
                        resolved = true
                    }
                }
                // Exit on a resolution (Yes/No fired the cancel via the launched
                // onUserConfirmed/onUserRejected; we drop out of the loop here
                // so we don't immediately relaunch a fresh listen and race the
                // cancel).
                if (resolved) return
                // If the inner Flow closed without any event (recogniser
                // silently terminated — typical when the user said nothing),
                // stop the loop and let the 10-s timer win. Without this guard
                // an empty Flow would busy-spin.
                if (!sawAnyEvent) return
            }
        }

        /** Returns `true` if this voice event resolved the confirmation. */
        private fun handleConfirmationVoice(event: ConfirmationVoice): Boolean =
            when (event) {
                ConfirmationVoice.Yes -> {
                    onUserConfirmed()
                    true
                }
                ConfirmationVoice.No -> {
                    onUserRejected()
                    true
                }
                is ConfirmationVoice.Other,
                is ConfirmationVoice.Failed,
                -> false
            }

        /**
         * SF-6.2 (US-042) — 10-s silence wins. Speak "Cancelo entonces." and
         * go home. `pendingAction.onConfirm()` is NOT invoked.
         *
         * SF-6.3 reuses this helper for the picker timeout case (same line,
         * same FSM transition); the picker job is cancelled too.
         */
        private fun onConfirmationTimedOut() {
            scope.launch {
                cancelConfirmationJobs()
                cancelPickerJobs()
                val timeoutSpeech = appContext.getString(R.string.copy_confirm_timeout)
                stateMachine.transition(
                    AssistantEvent.ConfirmationTimedOut(speech = timeoutSpeech),
                )
                ttsClient.speak(timeoutSpeech)
                stateMachine.transition(AssistantEvent.ExecutionDone)
                pendingActionRef = null
            }
        }

        /**
         * SF-6.3 (US-043) — start the picker STT pass + the 10-s silence
         * timer. The timer slot is shared with SF-6.2's confirmation timer
         * (cancelInFlight cancels both anyway).
         */
        private fun startPickerListening(
            pendingAction: PendingAction,
            expiresAtMs: Long,
        ) {
            pendingActionRef = pendingAction
            val kind = pendingAction.kind as? PendingAction.Kind.PickContact ?: return
            pickerListenerJob = scope.launch { runPickerListenerLoop(kind.candidates) }
            confirmationTimeoutJob =
                scope.launch {
                    val remaining = (expiresAtMs - timeProvider.now()).coerceAtLeast(0L)
                    delay(remaining)
                    if (state.value is AssistantState.Confirming) {
                        onConfirmationTimedOut()
                    }
                }
        }

        /**
         * SF-6.3 — picker STT loop. Mirrors SF-6.2's
         * [runConfirmationListenerLoop] but uses the candidate list +
         * ordinals + "ninguna" vocabulary.
         */
        private suspend fun runPickerListenerLoop(candidates: List<Contact>) {
            while (currentCoroutineContext().isActive) {
                var sawAnyEvent = false
                var resolved = false
                sttClient.listenForPicker(candidates).collect { event ->
                    sawAnyEvent = true
                    if (handlePickerVoice(event, candidates)) {
                        resolved = true
                    }
                }
                if (resolved) return
                if (!sawAnyEvent) return
            }
        }

        /** Returns `true` if the event resolved the picker (exit the loop). */
        private fun handlePickerVoice(
            event: PickerVoice,
            candidates: List<Contact>,
        ): Boolean =
            when (event) {
                is PickerVoice.Pick -> {
                    onPickerPicked(event.contact)
                    true
                }
                PickerVoice.None -> {
                    onPickerNone()
                    true
                }
                is PickerVoice.Other,
                is PickerVoice.Failed,
                -> {
                    if (disambigMissCount == 0) {
                        disambigMissCount = 1
                        // Re-speak the prompt; the outer loop relaunches the inner Flow.
                        val currentState = state.value
                        if (currentState is AssistantState.Confirming) {
                            scope.launch { ttsClient.speak(currentState.prompt) }
                        }
                        false
                    } else {
                        onPickerGiveUp(candidates)
                        true
                    }
                }
            }

        /**
         * SF-6.3 (US-043) — second consecutive miss → speak
         * `copy_disambig_give_up(_masc)` and go home. No call placed; the
         * picker fades to Idle.
         */
        private fun onPickerGiveUp(candidates: List<Contact>) {
            scope.launch {
                cancelPickerJobs()
                val masculine = isMasculineDisplayName(candidates.firstOrNull()?.displayName.orEmpty())
                val resId =
                    if (masculine) R.string.copy_disambig_give_up_masc else R.string.copy_disambig_give_up
                val giveUp = appContext.getString(resId)
                stateMachine.transition(
                    AssistantEvent.UserConfirmed(speech = giveUp, screen = null),
                )
                ttsClient.speak(giveUp)
                stateMachine.transition(AssistantEvent.ExecutionDone)
                pendingActionRef = null
            }
        }

        /**
         * Heuristic gender from a Spanish display name's first-name suffix.
         * Ending in `"o"` → masculine ("Pepito Sánchez" → masc); else
         * feminine. Acceptable for the prototype; Phase 7 may override
         * per-contact.
         */
        private fun isMasculineDisplayName(name: String): Boolean {
            val first = name.split(' ').firstOrNull().orEmpty()
            if (first.isEmpty()) return false
            return first.lowercase().endsWith("o")
        }

        private suspend fun runListenLoop() {
            sttClient.listen().collect { event ->
                when (event) {
                    is SttClient.Event.Partial ->
                        stateMachine.transition(AssistantEvent.PartialTranscript(event.text))
                    is SttClient.Event.Final ->
                        onFinalTranscript(event.text)
                    is SttClient.Event.Failed ->
                        onSttFailed(event.error)
                }
            }
        }

        private suspend fun onFinalTranscript(text: String) {
            stateMachine.transition(AssistantEvent.FinalTranscript(text, timeProvider.now()))
            // SF-5.4 (US-038): STT delivered a final transcript → recognition succeeded.
            // Reset the consecutive-failure counter here, regardless of what the handler
            // does downstream (handler-side failures don't bump this counter — spec §6
            // flow 6 is about *recognition* failures, not task failures).
            sttFailureCounter.recordSuccess()
            decideAndDispatch(text)
        }

        private suspend fun decideAndDispatch(transcript: String) {
            val started = SystemClock.elapsedRealtime()
            val decision = engine.decide(transcript, buildContext())
            val parsed: Result<FunctionCall> =
                decision.fold(
                    onSuccess = { validator.parseAndValidate(it) },
                    onFailure = { Result.failure(it) },
                )
            val latencyMs = (SystemClock.elapsedRealtime() - started).toInt()
            parsed.fold(
                onSuccess = { call -> onDecisionSuccess(call, latencyMs, transcript) },
                onFailure = { err -> onDecisionFailure(err, latencyMs, transcript) },
            )
        }

        private suspend fun onDecisionSuccess(
            call: FunctionCall,
            latencyMs: Int,
            transcript: String,
        ) {
            emitDecideTelemetry(outcome = "success", latencyMs = latencyMs)
            if (BuildConfig.DEBUG) {
                mutableSideEffects.emit(AssistantSideEffect.ShowDebugJson(prettyPrint(call)))
            }
            pendingFunctionCall = call
            pendingTranscript = transcript

            // SF-6.1 (US-041) — every successful FunctionCall flows through the
            // ConfidencePolicy gate BEFORE the dispatcher runs. The catalog
            // lookup is a linear search over 7 entries; sub-microsecond.
            val catalogFunction =
                Fase1Catalog.functions.firstOrNull { it.name == call.action }
            if (catalogFunction == null) {
                // Defensive — the validator already rejects unknown functions.
                // If we ever reach this branch, clarify rather than crash.
                clarify()
                return
            }

            // SF-6.4 (US-044) — the DataStore-backed always-confirm flag now drives
            // the policy's case #3 (always-escalate every CONDITIONAL). Phase 8's
            // settings menu will surface the toggle to Fran.
            val alwaysConfirm = settingsRepository.alwaysConfirm.first()
            // SF-6.3 will wire `isAmbiguous` from a future signal; today the handler
            // does the ambiguity detection itself via HandlerResult.NeedsContactPick.
            val inputs =
                PolicyInputs(
                    needsConfirmation = catalogFunction.needsConfirmation,
                    confidence = call.confidence,
                    isAmbiguous = false,
                    alwaysConfirmToggle = alwaysConfirm,
                    executeThreshold = settingsRepository.executeThreshold.first(),
                    confirmThreshold = settingsRepository.confirmThreshold.first(),
                )
            val decision = confidencePolicy.decide(inputs)
            emitPolicyTelemetry(call.action, decision, call.confidence, alwaysConfirm)

            when (decision) {
                ConfidenceDecision.Execute -> {
                    val result = dispatcher.dispatch(call)
                    renderHandlerResult(result, call)
                }
                ConfidenceDecision.Confirm -> {
                    val prompt = buildConfirmPrompt(call)
                    val pendingAction =
                        PendingAction(
                            functionName = call.action,
                            kind = PendingAction.Kind.YesNo(onConfirm = { dispatcher.dispatch(call) }),
                        )
                    val expiresAtMs = timeProvider.now() + CONFIRM_TIMEOUT_MS
                    stateMachine.transition(
                        AssistantEvent.FunctionCallReady(
                            needsConfirmation = true,
                            speech = "",
                            screen = null,
                            prompt = prompt,
                            expiresAtMs = expiresAtMs,
                            pendingAction = pendingAction,
                        ),
                    )
                    ttsClient.speak(prompt)
                    startConfirmationListening(pendingAction, expiresAtMs)
                }
                ConfidenceDecision.Clarify -> clarify()
            }
        }

        /**
         * Phase 6+ — handlers may still return [HandlerResult.NeedsConfirmation]
         * for cases like Phase-2's `send_whatsapp_reply` where the handler chose
         * to escalate (e.g. show the rewritten message to the user first). The
         * coordinator routes this through the FSM's `Confirming` path — it does
         * NOT auto-recurse (the Phase-5 short-circuit is removed here).
         */
        private suspend fun renderHandlerResult(
            result: HandlerResult,
            call: FunctionCall,
        ) {
            when (result) {
                is HandlerResult.Spoken -> executeAndFinish(result.speech, result.screen)
                is HandlerResult.NeedsConfirmation -> enterConfirmingYesNo(call, result)
                is HandlerResult.NeedsContactPick -> enterConfirmingPicker(call, result)
                is HandlerResult.Failed -> renderHandlerFailure(call, result)
            }
        }

        private suspend fun enterConfirmingYesNo(
            call: FunctionCall,
            result: HandlerResult.NeedsConfirmation,
        ) {
            val pendingAction =
                PendingAction(
                    functionName = call.action,
                    kind = PendingAction.Kind.YesNo(onConfirm = result.onConfirm),
                )
            val expiresAtMs = timeProvider.now() + CONFIRM_TIMEOUT_MS
            stateMachine.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = true,
                    speech = "",
                    screen = null,
                    prompt = result.prompt,
                    expiresAtMs = expiresAtMs,
                    pendingAction = pendingAction,
                ),
            )
            ttsClient.speak(result.prompt)
            startConfirmationListening(pendingAction, expiresAtMs)
        }

        private suspend fun enterConfirmingPicker(
            call: FunctionCall,
            result: HandlerResult.NeedsContactPick,
        ) {
            disambigMissCount = 0
            val pendingAction =
                PendingAction(
                    functionName = call.action,
                    kind =
                        PendingAction.Kind.PickContact(
                            candidates = result.candidates,
                            onPick = result.onPick,
                        ),
                )
            val expiresAtMs = timeProvider.now() + CONFIRM_TIMEOUT_MS
            stateMachine.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = true,
                    speech = "",
                    screen = null,
                    prompt = result.prompt,
                    expiresAtMs = expiresAtMs,
                    pendingAction = pendingAction,
                ),
            )
            ttsClient.speak(result.prompt)
            startPickerListening(pendingAction, expiresAtMs)
        }

        private suspend fun renderHandlerFailure(
            call: FunctionCall,
            result: HandlerResult.Failed,
        ) {
            if (!tryAutoRetryOnPermission(call.action, result.reason)) {
                Log.w(
                    FAILED_TAG,
                    "action=${call.action} error=${result.reason::class.simpleName} " +
                        "utterance.len=${pendingTranscript.length}",
                )
                executeAndFinish(result.speech, screen = null)
            }
        }

        /**
         * SF-6.1 (US-041, spec §4.3) — low-confidence clarify branch. Speaks
         * `copy_clarify_intent` and lands in `ErrorRecovery(message,
         * failureCount = 0)` so SF-5.4's STT-failure counter is NOT touched
         * (STT succeeded; this is a model-certainty miss).
         */
        private suspend fun clarify() {
            val msg = appContext.getString(R.string.copy_clarify_intent)
            stateMachine.transition(AssistantEvent.LowConfidenceClarify(msg))
            ttsClient.speak(msg)
            stateMachine.transition(AssistantEvent.RecoverySpoken)
        }

        /**
         * SF-6.1 — build the Spanish prompt the `Confirming` overlay shows AND
         * Curro speaks. One string per turn; the overlay reads
         * [AssistantState.Confirming.prompt] (same value), so the spoken and
         * visible texts cannot drift.
         */
        private fun buildConfirmPrompt(call: FunctionCall): String =
            when (call.action) {
                "call_contact" ->
                    appContext.getString(
                        R.string.copy_confirm_call,
                        (call.params["contact"] as? String).orEmpty(),
                    )
                // Phase-2's send_whatsapp_reply etc. will land their own copies here.
                else -> appContext.getString(R.string.copy_clarify_intent)
            }

        private fun emitPolicyTelemetry(
            functionName: String,
            decision: ConfidenceDecision,
            confidence: Float,
            alwaysConfirmOn: Boolean,
        ) {
            telemetry.event(
                "policy_decided",
                mapOf(
                    "function_name" to functionName,
                    "decision" to decision.name.lowercase(),
                    "confidence_bucket" to confidenceBucket(confidence),
                    "always_confirm_on" to alwaysConfirmOn,
                ),
            )
        }

        /**
         * Bucketed confidence — `<0.60 → "low"`, `[0.60, 0.85) → "mid"`,
         * `≥ 0.85 → "high"`. Keeps the raw confidence value off the wire and
         * passes TelemetryGuardrail's PII heuristic comfortably (each label
         * is ≤ 8 characters).
         *
         * Note: thresholds here are the SPEC defaults, not the user-tuned
         * values. Bucketing on the spec defaults keeps event aggregation
         * comparable across users; the user-tuned thresholds drive the
         * policy decision but the bucket is for analytics only.
         */
        private fun confidenceBucket(confidence: Float): String =
            when {
                confidence < SPEC_CONFIRM_THRESHOLD -> "low"
                confidence < SPEC_EXECUTE_THRESHOLD -> "mid"
                else -> "high"
            }

        private suspend fun tryAutoRetryOnPermission(
            action: String,
            reason: CurroError,
        ): Boolean {
            if (action != "call_contact") return false
            return when (reason) {
                is CurroError.ReadContactsPermissionMissing -> {
                    if (!readContactsAutoRetried) {
                        readContactsAutoRetried = true
                        mutableSideEffects.emit(
                            AssistantSideEffect.RequestPermission(Manifest.permission.READ_CONTACTS),
                        )
                        true
                    } else {
                        false
                    }
                }
                is CurroError.PermissionDenied -> {
                    if (!callPhoneAutoRetried) {
                        callPhoneAutoRetried = true
                        mutableSideEffects.emit(
                            AssistantSideEffect.RequestPermission(Manifest.permission.CALL_PHONE),
                        )
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        private suspend fun handleRecordAudioResult(granted: Boolean) {
            if (granted) {
                currentJob?.cancel()
                currentJob = scope.launch { runListenLoop() }
            } else {
                val msg = appContext.getString(R.string.copy_perm_missing_mic)
                executeAndFinish(msg, screen = null)
            }
        }

        private suspend fun handleReadContactsResult(granted: Boolean) {
            val pending = pendingFunctionCall
            if (granted && pending != null) {
                val result = dispatcher.dispatch(pending)
                renderHandlerResult(result, pending)
            } else if (!granted) {
                val msg = appContext.getString(R.string.copy_perm_missing_contacts)
                executeAndFinish(msg, screen = null)
            }
        }

        private suspend fun handleCallPhoneResult(granted: Boolean) {
            val pending = pendingFunctionCall
            if (granted && pending != null) {
                val result = dispatcher.dispatch(pending)
                renderHandlerResult(result, pending)
            } else if (!granted) {
                val msg = appContext.getString(R.string.copy_perm_missing_calls)
                executeAndFinish(msg, screen = null)
            }
        }

        private suspend fun onSttFailed(error: CurroError) {
            // SF-5.4 (US-038): spec §6 flow 6 consecutive-failure policy.
            // recordFailure() returns the new count; pickFailMessage chooses the
            // copy_stt_fail_1/2/3 line. SttVoicePackMissing keeps its dedicated copy
            // regardless of the count (it's an install-time issue, not a recognition
            // miss). After the 3rd strike we recordSuccess() so the next press
            // restarts the count at 1 — the "vamos a dejarlo" line is the give-up
            // signal, not a permanent state.
            val newCount = sttFailureCounter.recordFailure()
            val msg = pickFailMessage(error, newCount)
            stateMachine.transition(
                AssistantEvent.SttFailed(message = msg, failureCount = newCount),
            )
            ttsClient.speak(msg)
            if (newCount >= GIVE_UP_THRESHOLD) {
                sttFailureCounter.recordSuccess()
            }
            stateMachine.transition(AssistantEvent.RecoverySpoken)
        }

        /**
         * SF-5.4 (US-038): map an STT error + failure count to the Spanish line.
         *
         * Install-time / permission errors keep their dedicated copy regardless of
         * count — those are *not* "I didn't hear you" failures and conflating them
         * with the 1/2/3 escalation would surface "Sigo sin entenderte" when the
         * actual problem is "you haven't installed the voice pack".
         */
        private fun pickFailMessage(
            error: CurroError,
            count: Int,
        ): String =
            when (error) {
                is CurroError.SttVoicePackMissing ->
                    appContext.getString(R.string.copy_stt_no_voice_pack)
                is CurroError.PermissionDenied ->
                    appContext.getString(R.string.copy_perm_missing_mic)
                else ->
                    when (count) {
                        1 -> appContext.getString(R.string.copy_stt_fail_1)
                        2 -> appContext.getString(R.string.copy_stt_fail_2)
                        else -> appContext.getString(R.string.copy_stt_fail_3)
                    }
            }

        private suspend fun onDecisionFailure(
            err: Throwable,
            latencyMs: Int,
            transcript: String,
        ) {
            val (copyId, outcomeLabel, actionLabel) =
                when (err) {
                    is CurroError.ModelCold -> Triple(R.string.copy_models_not_ready, "model_cold", null)
                    is CurroError.OutOfMemory -> Triple(R.string.copy_error_unknown_function, "oom", null)
                    is CurroError.UnknownFunction ->
                        Triple(R.string.copy_error_unknown_function, "unknown_function", err.name)
                    is CurroError.InvalidFunctionCall ->
                        Triple(R.string.copy_error_unknown_function, "invalid_json", null)
                    else -> Triple(R.string.copy_error_unknown_function, "other", null)
                }
            Log.w(
                FAILED_TAG,
                "action=${actionLabel ?: "null"} error=${err::class.simpleName} " +
                    "utterance.len=${transcript.length}",
            )
            emitDecideTelemetry(outcome = outcomeLabel, latencyMs = latencyMs)
            executeAndFinish(appContext.getString(copyId), screen = null)
        }

        /**
         * Spec-§4.6 "audio + visual together": transition to `Executing` (so the UI
         * shows what's being spoken), suspend on TTS, then `ExecutionDone` → `Idle`.
         */
        private suspend fun executeAndFinish(
            speech: String,
            screen: com.curro.app.domain.handler.AssistantScreen?,
        ) {
            stateMachine.transition(
                AssistantEvent.FunctionCallReady(
                    needsConfirmation = false,
                    speech = speech,
                    screen = screen,
                    prompt = null,
                    expiresAtMs = 0L,
                    pendingAction = null,
                ),
            )
            ttsClient.speak(speech)
            stateMachine.transition(AssistantEvent.ExecutionDone)
        }

        private fun emitDecideTelemetry(
            outcome: String,
            latencyMs: Int,
        ) {
            telemetry.event(
                "model_decide",
                mapOf(
                    "model" to "function_gemma_270m",
                    "outcome" to outcome,
                    "latency_ms" to latencyMs,
                ),
            )
        }

        private suspend fun buildContext(): PromptContext =
            PromptContext(
                nowIso = LocalDateTime.now(clock).withNano(0).toString(),
                unreadMessagesSummary = "",
                knownAliases =
                    aliasRepository
                        .topUsedSnapshots(PROMPT_ALIAS_LIMIT)
                        .map { "${it.alias} → ${it.displayName}" },
            )

        private fun prettyPrint(call: FunctionCall): String {
            val params =
                call.params.entries.joinToString(",\n") { (k, v) ->
                    "    \"$k\": ${jsonValue(v)}"
                }
            return buildString {
                append("{\n")
                append("  \"action\": \"").append(call.action).append("\",\n")
                if (params.isEmpty()) {
                    append("  \"params\": {},\n")
                } else {
                    append("  \"params\": {\n").append(params).append("\n  },\n")
                }
                append("  \"confidence\": ").append(call.confidence).append("\n")
                append("}")
            }
        }

        private fun jsonValue(v: Any): String =
            when (v) {
                is String -> "\"$v\""
                is Int -> v.toString()
                else -> "\"$v\""
            }

        private companion object {
            const val FAILED_TAG = "Curro/FailedCommand"
            const val SIDE_EFFECT_BUFFER = 8

            /**
             * SF-7.2 (US-046) — prompt-budget cap for alias injection. 10 aliases ≈
             * 150–250 tokens; the Phase-3 budget (< 600 tokens) absorbs this comfortably.
             */
            const val PROMPT_ALIAS_LIMIT = 10

            /** SF-5.4: after the 3rd consecutive STT failure, Curro gives up for the turn. */
            const val GIVE_UP_THRESHOLD = 3

            /**
             * SF-6.1 (spec §6 flow 2) — confirmation prompts time out after 10 s of
             * silence. SF-6.2 wires the actual timer; SF-6.1 stamps the deadline.
             */
            const val CONFIRM_TIMEOUT_MS = 10_000L

            /**
             * Spec defaults — used ONLY for telemetry bucketing
             * ([emitPolicyTelemetry]). The policy itself reads the user-tuned
             * values from [SettingsRepository].
             */
            const val SPEC_EXECUTE_THRESHOLD = 0.85f
            const val SPEC_CONFIRM_THRESHOLD = 0.60f
        }
    }
