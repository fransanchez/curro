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
import com.curro.app.domain.handler.HandlerDispatcher
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.PromptContext
import com.curro.app.domain.repository.FunctionCallEngine
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TtsClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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
@Suppress("LongParameterList", "TooManyFunctions")
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

        /** Phase 6 fills the body — wired here so the SF boundary stays clean. */
        fun onUserConfirmed() {
            // Phase 6 — ConfidencePolicy will drive the Confirming → Executing transition.
        }

        /** Phase 6 fills the body. */
        fun onUserRejected() {
            // Phase 6 — ConfidencePolicy will drive the Confirming → Idle transition.
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
            ttsClient.stop()
            sttClient.cancel()
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
            val result = dispatcher.dispatch(call)
            renderHandlerResult(result, call)
        }

        /**
         * Phase 5 keeps the Phase-4 auto-confirm behaviour for `NeedsConfirmation`:
         * recurse into `onConfirm()` immediately. Phase 6 replaces this with a real
         * `FunctionCallReady(nc = true, …)` emission → `Confirming`.
         */
        private suspend fun renderHandlerResult(
            result: HandlerResult,
            call: FunctionCall,
        ) {
            when (result) {
                is HandlerResult.Spoken -> executeAndFinish(result.speech, result.screen)
                is HandlerResult.NeedsConfirmation -> {
                    val inner = result.onConfirm()
                    renderHandlerResult(inner, call)
                }
                is HandlerResult.Failed -> {
                    if (!tryAutoRetryOnPermission(call.action, result.reason)) {
                        Log.w(
                            FAILED_TAG,
                            "action=${call.action} error=${result.reason::class.simpleName} " +
                                "utterance.len=${pendingTranscript.length}",
                        )
                        executeAndFinish(result.speech, screen = null)
                    }
                }
            }
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
            // Phase 5: hardcode failureCount = 1 (SF-5.4 plugs in the real counter).
            val msg = sttErrorMessage(error)
            stateMachine.transition(
                AssistantEvent.SttFailed(message = msg, failureCount = 1),
            )
            ttsClient.speak(msg)
            stateMachine.transition(AssistantEvent.RecoverySpoken)
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

        private fun sttErrorMessage(error: CurroError): String =
            when (error) {
                is CurroError.SttVoicePackMissing -> appContext.getString(R.string.copy_stt_no_voice_pack)
                is CurroError.PermissionDenied -> appContext.getString(R.string.copy_perm_missing_mic)
                else -> appContext.getString(R.string.copy_stt_fail_1)
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

        private fun buildContext(): PromptContext =
            PromptContext(
                nowIso = LocalDateTime.now(clock).withNano(0).toString(),
                unreadMessagesSummary = "",
                knownAliases = emptyList(),
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
        }
    }
