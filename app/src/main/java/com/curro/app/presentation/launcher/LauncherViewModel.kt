package com.curro.app.presentation.launcher

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.curro.app.BuildConfig
import com.curro.app.R
import com.curro.app.data.launcher.DefaultLauncherDetector
import com.curro.app.data.ml.FunctionCallValidator
import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.data.permissions.PermissionGate
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.domain.handler.HandlerDispatcher
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.ClockState
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.PromptContext
import com.curro.app.domain.repository.FavoriteAppsRepository
import com.curro.app.domain.repository.FunctionCallEngine
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TelemetrySink
import com.curro.app.domain.repository.TtsClient
import com.curro.app.domain.usecase.ObserveClockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * ViewModel for [LauncherPlaceholderScreen] (US-009/SF-1.1 through US-017/SF-2.3).
 *
 * SF-2.3 (US-017) replaces the inert-toast onMicPressed with the real voice loop:
 *
 *   press → permission check → [SttClient.listen] → echo [Final] via [TtsClient.speak] →
 *   Idle.
 *
 * A second press in any non-Idle state (barge-in) cancels the active voice job and
 * restarts listening. The cancellation path is `voiceJob.cancel(); voiceJob.join()` —
 * the join is **load-bearing** so the previous SpeechRecognizer's `awaitClose` runs
 * before the new session starts (otherwise the framework reports
 * ERROR_RECOGNIZER_BUSY). See US-017 §11.
 *
 * PROVISIONAL (US-017) — Phase 5 (SF-5.1) replaces the per-screen [ListeningState] with a
 * global [com.curro.app.assistant.AssistantStateMachine]. The mapping is documented inline
 * on [ListeningState].
 *
 * `@Suppress("LongParameterList")`: each constructor parameter is an orthogonal
 * collaborator; merging them into a wrapper would only add indirection.
 */
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class LauncherViewModel
    @Inject
    constructor(
        detector: DefaultLauncherDetector,
        observeClock: ObserveClockUseCase,
        favoritesRepo: FavoriteAppsRepository,
        private val sttClient: SttClient,
        private val ttsClient: TtsClient,
        private val permissionGate: PermissionGate,
        // SF-3.6 (US-024) — the on-device decision pipeline. The engine wraps
        // MediaPipe; the validator turns its raw output into a typed FunctionCall.
        // Tests substitute FakeFunctionCallEngine + real FunctionCallValidator.
        private val engine: FunctionCallEngine,
        private val validator: FunctionCallValidator,
        private val telemetry: TelemetrySink,
        // SF-4.1 (US-025) — the handler dispatcher routes a validated FunctionCall to its
        // handler and returns a HandlerResult. Phase 5 moves this into AssistantCoordinator.
        private val dispatcher: HandlerDispatcher,
        // SF-4.6 (US-030) — notification-access gate; re-evaluated on every ON_RESUME.
        private val notifGate: NotificationAccessGate,
        // SF-4.10 (US-034) — injected for the permission side-effect auto-retry path.
        // These gates are also checked inside the handler; injecting here avoids
        // duplicating the check — the VM only needs them to decide which side effect to fire.
        private val readContactsGate: ReadContactsPermissionGate,
        private val callPhoneGate: CallPhonePermissionGate,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val listeningStateFlow = MutableStateFlow<ListeningState>(ListeningState.Idle)
        private val notifGrantedFlow = MutableStateFlow(notifGate.isGranted())

        /**
         * Internal seam: a factory that returns the [androidx.lifecycle.Lifecycle] whose
         * `ON_RESUME` events trigger a re-check of the notification-access gate.
         * Defaults to [ProcessLifecycleOwner]; tests supply a no-op (or a
         * [androidx.lifecycle.testing.TestLifecycleOwner]).
         *
         * This must be set BEFORE [viewModelScope] launches the registration coroutine —
         * because the coroutine runs on [UnconfinedTestDispatcher], it executes synchronously
         * on [newViewModel], which is after the `internal var` is set in tests.
         *
         * (Same pattern as [com.curro.app.data.launcher.DefaultLauncherDetectorImpl.lifecycleSource].)
         */
        internal var lifecycleSource: () -> androidx.lifecycle.Lifecycle = {
            ProcessLifecycleOwner.get().lifecycle
        }

        private val resumeObserver =
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    notifGrantedFlow.value = notifGate.isGranted()
                }
            }

        init {
            // Defer the addObserver call into a coroutine. With UnconfinedTestDispatcher
            // (set as Main before newViewModel() is called), this body executes synchronously
            // during the launch call — i.e., still inside newViewModel(). The seam is already
            // set to the default before init runs, so tests must replace it immediately after
            // newViewModel() returns and before any lifecycle event fires (which is always true
            // in unit tests since no real lifecycle events are sent unless the test drives them).
            viewModelScope.launch {
                try {
                    lifecycleSource().addObserver(resumeObserver)
                } catch (_: Exception) {
                    // ProcessLifecycleOwner not initialised in JVM unit tests — safe to skip.
                    // The notifGrantedFlow is already seeded with the initial value from
                    // notifGate.isGranted() in the field initialiser above.
                }
            }
        }

        override fun onCleared() {
            try {
                lifecycleSource().removeObserver(resumeObserver)
            } catch (_: Exception) {
                // Same guard — ProcessLifecycleOwner not available in unit tests.
            }
        }

        val uiState: StateFlow<LauncherUiState> =
            combine(
                detector.flow,
                observeClock(),
                favoritesRepo.observeFavorites(),
                listeningStateFlow,
                notifGrantedFlow,
            ) { isDefault, clock, favorites, listening, notifGranted ->
                LauncherUiState(
                    isCurroDefault = isDefault,
                    clock = clock,
                    favorites = favorites,
                    listeningState = listening,
                    isNotificationAccessGranted = notifGranted,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue =
                    LauncherUiState(
                        isCurroDefault = false,
                        clock = ClockState(timeText = "--:--", dateText = ""),
                        favorites = emptyList(),
                        listeningState = ListeningState.Idle,
                        isNotificationAccessGranted = false,
                    ),
            )

        private val _sideEffects = Channel<LauncherSideEffect>(Channel.BUFFERED)
        val sideEffects: Flow<LauncherSideEffect> = _sideEffects.receiveAsFlow()

        /** The active voice-loop job — cancelled-and-joined on barge-in (§11). */
        private var voiceJob: Job? = null

        /**
         * SF-4.10 (US-034) — per-turn auto-retry flags.
         *
         * When the `call_contact` handler returns `Failed(ReadContactsPermissionMissing)` or
         * `Failed(PermissionDenied)` we fire a one-shot `RequestReadContacts` / `RequestCallPhone`
         * side effect. The flag ensures we do NOT prompt a second time in the same turn.
         * Both flags reset to `false` in `onMicPressed` (each new mic press is a fresh turn).
         *
         * Decision pinned (brief §8.7): provisional Phase-4 glue; Phase 5 moves this into
         * `AssistantCoordinator` where the FSM owns retry semantics cleanly.
         */
        private var readContactsAutoRetried = false
        private var callPhoneAutoRetried = false

        /**
         * The last successfully-parsed `FunctionCall`. Kept so that after a permission grant we
         * can auto-retry the EXACT same call the user asked for, without re-running STT.
         */
        private var lastFunctionCall: FunctionCall? = null
        private var lastTranscript: String = ""

        fun onEvent(event: LauncherEvent) {
            when (event) {
                is LauncherEvent.MicPressed -> onMicPressed()
                is LauncherEvent.AppTileTapped -> onAppTileTapped(event.packageName)
                is LauncherEvent.ClockTapped -> onClockTapped()
                is LauncherEvent.RecordAudioPermissionResult -> onPermissionResult(event.granted)
                is LauncherEvent.GrantNotifAccessRequested -> onGrantNotifAccessRequested()
                // SF-4.10 (US-034) — permission result side effects for call_contact
                is LauncherEvent.ReadContactsPermissionResult -> onReadContactsPermissionResult(event.granted)
                is LauncherEvent.CallPhonePermissionResult -> onCallPhonePermissionResult(event.granted)
            }
        }

        private fun onGrantNotifAccessRequested() {
            viewModelScope.launch {
                _sideEffects.send(LauncherSideEffect.OpenNotificationAccessSettings)
            }
        }

        /**
         * Handles a mic press. If a previous voice session is active (barge-in), cancel
         * and join it before deciding whether to request permission or start a new
         * session. The cancel-then-join sequence is performed inside a coroutine so the
         * join is a real suspending wait, not a polling race (US-017 §11).
         */
        private fun onMicPressed() {
            // SF-4.10: a fresh mic press starts a new turn — reset per-turn auto-retry flags.
            readContactsAutoRetried = false
            callPhoneAutoRetried = false
            lastFunctionCall = null
            lastTranscript = ""
            viewModelScope.launch {
                if (listeningStateFlow.value !is ListeningState.Idle) {
                    voiceJob?.cancel()
                    voiceJob?.join()
                    voiceJob = null
                    listeningStateFlow.value = ListeningState.Idle
                }
                if (!permissionGate.isGranted()) {
                    _sideEffects.send(LauncherSideEffect.RequestRecordAudio)
                    return@launch
                }
                startListening()
            }
        }

        private fun onPermissionResult(granted: Boolean) {
            if (granted) {
                startListening()
            } else {
                showTransientError(R.string.copy_perm_missing_mic)
            }
        }

        /**
         * SF-4.10 (US-034) — READ_CONTACTS result after the one-shot auto-retry side effect.
         *
         * If granted: re-dispatch the stored [lastFunctionCall] — the same `call_contact`
         * call the user originally made, without re-running STT.
         * If denied: speak [R.string.copy_perm_missing_contacts] and return to Idle.
         *
         * Phase 5: this logic migrates into `AssistantCoordinator` where the FSM tracks it.
         */
        private fun onReadContactsPermissionResult(granted: Boolean) {
            if (granted) {
                val call = lastFunctionCall
                if (call != null) {
                    viewModelScope.launch { render(dispatcher.dispatch(call), call.action, lastTranscript) }
                } else {
                    startListening()
                }
            } else {
                showTransientError(R.string.copy_perm_missing_contacts)
            }
        }

        /**
         * SF-4.10 (US-034) — CALL_PHONE result after the one-shot auto-retry side effect.
         *
         * If granted: re-dispatch the stored [lastFunctionCall] so the call goes through.
         * If denied: speak [R.string.copy_perm_missing_calls] and return to Idle.
         *
         * Phase 5: this logic migrates into `AssistantCoordinator`.
         */
        private fun onCallPhonePermissionResult(granted: Boolean) {
            if (granted) {
                val call = lastFunctionCall
                if (call != null) {
                    viewModelScope.launch { render(dispatcher.dispatch(call), call.action, lastTranscript) }
                } else {
                    startListening()
                }
            } else {
                showTransientError(R.string.copy_perm_missing_calls)
            }
        }

        /**
         * Starts a new STT session, collecting partials into [ListeningState.Listening]
         * and on [SttClient.Event.Final] speaking the echo via [TtsClient.speak]. After
         * speak completes the state returns to [ListeningState.Idle] — but only if the
         * state is still in the matching speaking/error step (barge-in may have already
         * reset it).
         */
        private fun startListening() {
            listeningStateFlow.value = ListeningState.Starting
            voiceJob =
                sttClient
                    .listen()
                    .onEach { event -> handleSttEvent(event) }
                    .launchIn(viewModelScope)
                    .also { job ->
                        job.invokeOnCompletion { cause ->
                            // Cancellation from barge-in is normal — don't log.
                            if (cause != null && cause !is CancellationException) {
                                // Defensive: a non-cancellation throwable surfaces as a
                                // transient error. The SttClient contract emits Failed
                                // for known cases, so this is the unexpected-bug path.
                                listeningStateFlow.value = ListeningState.Idle
                            }
                        }
                    }
        }

        private suspend fun handleSttEvent(event: SttClient.Event) {
            when (event) {
                is SttClient.Event.Partial -> {
                    listeningStateFlow.value = ListeningState.Listening(event.text)
                }
                is SttClient.Event.Final -> {
                    // SF-3.6 (US-024) — replace the raw-transcript echo with the
                    // decision pipeline: Processing → engine.decide → validator →
                    // Speaking(action description) → Idle.
                    decideAndSpeak(event.text)
                }
                is SttClient.Event.Failed -> {
                    handleSttFailure(event.error)
                }
            }
        }

        /**
         * SF-3.6 (US-024) — the decision smoke loop.
         *
         * Wall-clock latency is measured around `engine.decide`; the engine
         * itself emits the per-call `Log.i("Curro/Llm", "decide latency: …")`
         * line that the on-device gate validates.
         *
         * All paths (success + every failure variant) terminate in `Idle` and
         * emit a `model_decide` telemetry event with `outcome` and `latency_ms`
         * — never the utterance, never the action.
         */
        private suspend fun decideAndSpeak(transcript: String) {
            listeningStateFlow.value = ListeningState.Processing(transcript)
            val ctx = buildContext()
            val started = SystemClock.elapsedRealtime()
            val decision = engine.decide(transcript, ctx)
            // Preserve the typed error on failure; on success, hand to the validator.
            val parsed: Result<FunctionCall> =
                decision.fold(
                    onSuccess = { raw -> validator.parseAndValidate(raw) },
                    onFailure = { Result.failure(it) },
                )
            val latencyMs = (SystemClock.elapsedRealtime() - started).toInt()

            parsed.fold(
                onSuccess = { call -> handleDecisionSuccess(call, latencyMs, transcript) },
                onFailure = { err -> handleDecisionFailure(err, latencyMs, transcript) },
            )
        }

        private suspend fun handleDecisionSuccess(
            call: FunctionCall,
            latencyMs: Int,
            transcript: String,
        ) {
            emitDecideEvent(outcome = "success", latencyMs = latencyMs)
            if (BuildConfig.DEBUG) {
                _sideEffects.send(LauncherSideEffect.ShowDebugJson(prettyPrint(call)))
            }
            // SF-4.10: save for potential auto-retry on permission grant.
            lastFunctionCall = call
            lastTranscript = transcript
            val result = dispatcher.dispatch(call)
            render(result, call.action, transcript)
        }

        /**
         * Routes a [HandlerResult] to TTS + state.
         *
         * Phase 4 auto-confirm: [HandlerResult.NeedsConfirmation] immediately invokes
         * [HandlerResult.NeedsConfirmation.onConfirm] and renders its result recursively.
         *
         * Phase 6 hook: replace the auto-invoke with ConfidencePolicy.evaluate(confidence, result)
         * and transition to the `confirming` state with the prompt.
         */
        private suspend fun render(
            result: HandlerResult,
            action: String,
            transcript: String,
        ) {
            when (result) {
                is HandlerResult.Spoken -> speakAndIdle(result.speech)
                is HandlerResult.NeedsConfirmation -> {
                    // Phase 6 inserts the ConfidencePolicy gate here.
                    val inner = result.onConfirm()
                    render(inner, action, transcript)
                }
                is HandlerResult.Failed -> {
                    Log.w(
                        FAILED_TAG,
                        "action=$action error=${result.reason::class.simpleName} utterance.len=${transcript.length}",
                    )
                    if (!tryRequestCallContactPermission(action, result.reason)) {
                        speakAndIdle(result.speech)
                    }
                }
            }
        }

        /**
         * SF-4.10 (US-034) — one-shot auto-retry on first permission denial for `call_contact`.
         *
         * Returns `true` if a [LauncherSideEffect.RequestReadContacts] or
         * [LauncherSideEffect.RequestCallPhone] side effect was fired (caller must NOT speak yet).
         * Returns `false` for every other case (caller should speak the failure line).
         *
         * Phase 5: moves into `AssistantCoordinator`.
         */
        private suspend fun tryRequestCallContactPermission(
            action: String,
            reason: CurroError,
        ): Boolean {
            if (action != "call_contact") return false
            return when (reason) {
                is CurroError.ReadContactsPermissionMissing -> {
                    if (!readContactsAutoRetried) {
                        readContactsAutoRetried = true
                        _sideEffects.send(LauncherSideEffect.RequestReadContacts)
                        true
                    } else {
                        false
                    }
                }
                is CurroError.PermissionDenied -> {
                    if (!callPhoneAutoRetried) {
                        callPhoneAutoRetried = true
                        _sideEffects.send(LauncherSideEffect.RequestCallPhone)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        private suspend fun speakAndIdle(message: String) {
            listeningStateFlow.value = ListeningState.Speaking(message)
            ttsClient.speak(message)
            listeningStateFlow.update { current ->
                if (current is ListeningState.Speaking) ListeningState.Idle else current
            }
        }

        private suspend fun handleDecisionFailure(
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
            // PII boundary: the utterance text is NEVER in the log line. Only its length,
            // the typed error class name, and the action label (already a catalog snake_case
            // name, never user input).
            Log.w(
                FAILED_TAG,
                "action=${actionLabel ?: "null"} error=${err::class.simpleName} utterance.len=${transcript.length}",
            )
            emitDecideEvent(outcome = outcomeLabel, latencyMs = latencyMs)
            speakAndIdle(appContext.getString(copyId))
        }

        private fun emitDecideEvent(
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

        /**
         * Phase-3 [PromptContext] is `nowIso` only — Phase 4 fills `unreadMessagesSummary`
         * (WhatsApp cache) and Phase 7 fills `knownAliases` (alias DB).
         */
        private fun buildContext(): PromptContext =
            PromptContext(
                nowIso = LocalDateTime.now().withNano(0).toString(),
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

        private suspend fun handleSttFailure(error: CurroError) {
            val msg = errorMessage(error)
            listeningStateFlow.value = ListeningState.Error(msg)
            // Speak the error message AND show it (spec §4.6 "audio + visual together").
            ttsClient.speak(msg)
            delay(ERROR_DISMISS_DELAY_MS)
            listeningStateFlow.update { current ->
                if (current is ListeningState.Error) ListeningState.Idle else current
            }
        }

        /**
         * Shows an Error state for [resId], speaks it, and clears after [ERROR_DISMISS_DELAY_MS].
         * Used by [onPermissionResult] when the permission is denied. Runs in its own
         * coroutine so the caller is non-suspending.
         *
         * @StringRes annotation kept: the param is a resource ID, and the annotation
         * allows lint to verify the call site passes a valid R.string reference.
         */
        private fun showTransientError(
            @StringRes resId: Int,
        ) {
            val msg = appContext.getString(resId)
            listeningStateFlow.value = ListeningState.Error(msg)
            viewModelScope.launch {
                ttsClient.speak(msg)
                delay(ERROR_DISMISS_DELAY_MS)
                listeningStateFlow.update { current ->
                    if (current is ListeningState.Error) ListeningState.Idle else current
                }
            }
        }

        /**
         * Maps a [CurroError] to the Spanish copy the user sees and hears.
         * Phase 2: every Stt* maps to copy_stt_fail_1; Phase 5's
         * AssistantStateMachine wires the 1st/2nd/3rd consecutive-failure counter.
         */
        private fun errorMessage(error: CurroError): String =
            when (error) {
                is CurroError.PermissionDenied -> appContext.getString(R.string.copy_perm_missing_mic)
                is CurroError.SttVoicePackMissing -> appContext.getString(R.string.copy_stt_no_voice_pack)
                else -> appContext.getString(R.string.copy_stt_fail_1)
            }

        private fun onAppTileTapped(packageName: String) {
            viewModelScope.launch {
                _sideEffects.send(LauncherSideEffect.LaunchApp(packageName))
            }
        }

        // SF-1.6 five-tap counter — maintained as a list of tap timestamps (ms).
        private val clockTapTimes = mutableListOf<Long>()

        private fun onClockTapped() {
            val now = System.currentTimeMillis()
            clockTapTimes.add(now)
            clockTapTimes.removeAll { now - it > TAP_WINDOW_MS }
            if (clockTapTimes.size >= TAP_COUNT_THRESHOLD) {
                clockTapTimes.clear()
                viewModelScope.launch {
                    _sideEffects.send(LauncherSideEffect.OpenConfig)
                }
            }
        }

        private companion object {
            const val SUBSCRIBE_TIMEOUT_MS = 5_000L
            const val TAP_WINDOW_MS = 3_000L
            const val TAP_COUNT_THRESHOLD = 5

            /**
             * After an [ListeningState.Error], wait this long before resetting to Idle so
             * the user has time to read the message AND so the TTS playback completes
             * (whichever finishes first wins the race; both lead to the same Idle reset).
             */
            const val ERROR_DISMISS_DELAY_MS = 2_500L

            /** Logcat tag for spec-flow-7 failed-command lines (no PII). */
            const val FAILED_TAG = "Curro/FailedCommand"
            // ACTION_DESCRIPTION_MAP and actionDescription removed in US-025 (SF-4.1).
            // The copy_action_* strings remain in strings.xml; orphan cleanup deferred to Phase 5.
        }
    }

/**
 * UI state for [LauncherPlaceholderScreen].
 *
 * - [isCurroDefault]: whether Curro is the resolved default home. Controls CTA visibility.
 * - [clock]: live-updating time + date strings from [ObserveClockUseCase] (SF-1.2).
 * - [favorites]: the four static favourite-app tiles (SF-1.4). Empty until the repository emits.
 * - [listeningState]: SF-2.3 (US-017) — drives the listening overlay and the MicButton colour.
 * - [isNotificationAccessGranted]: SF-4.6 (US-030) — false while notification-listener access
 *   is not granted; triggers the "Permitir leer mensajes" home CTA.
 */
data class LauncherUiState(
    val isCurroDefault: Boolean,
    val clock: ClockState,
    val favorites: List<FavoriteApp> = emptyList(),
    val listeningState: ListeningState = ListeningState.Idle,
    val isNotificationAccessGranted: Boolean = false,
)

/**
 * User or system events dispatched to [LauncherViewModel.onEvent].
 *
 * Sealed so exhaustive `when` is enforced at every call site — new events always
 * require a matching branch.
 */
sealed interface LauncherEvent {
    /** SF-1.3 — mic button pressed. SF-2.3 replaces the inert handler with the voice loop. */
    data object MicPressed : LauncherEvent

    /**
     * SF-1.4 — an app tile was tapped.
     * @param packageName The resolved package to launch; may be the fallback package if
     *   dynamic resolution found nothing.
     */
    data class AppTileTapped(val packageName: String) : LauncherEvent

    /** SF-1.6 — clock block tapped; the five-tap counter is inside the ViewModel. */
    data object ClockTapped : LauncherEvent

    /** SF-2.3 (US-017) — result of the runtime RECORD_AUDIO request. */
    data class RecordAudioPermissionResult(val granted: Boolean) : LauncherEvent

    /**
     * SF-4.6 (US-030) — user tapped the "Permitir leer mensajes" CTA.
     * The ViewModel emits [LauncherSideEffect.OpenNotificationAccessSettings].
     */
    data object GrantNotifAccessRequested : LauncherEvent

    /**
     * SF-4.10 (US-034) — result of the runtime READ_CONTACTS request.
     * Delivered back from [LauncherPlaceholderScreen]'s
     * `rememberLauncherForActivityResult` for [android.Manifest.permission.READ_CONTACTS].
     */
    data class ReadContactsPermissionResult(val granted: Boolean) : LauncherEvent

    /**
     * SF-4.10 (US-034) — result of the runtime CALL_PHONE request.
     * Delivered back from [LauncherPlaceholderScreen]'s
     * `rememberLauncherForActivityResult` for [android.Manifest.permission.CALL_PHONE].
     */
    data class CallPhonePermissionResult(val granted: Boolean) : LauncherEvent
}

/**
 * One-shot UI side effects emitted by [LauncherViewModel] and consumed exactly once
 * by the screen via a [LaunchedEffect] / [Channel] pattern.
 */
sealed interface LauncherSideEffect {
    /**
     * Show a [android.widget.Toast] with the given Android string resource ID.
     * Used for the uninstalled-app-tile case (SF-1.4) — the SF-2.3 RECORD_AUDIO denial
     * path uses [ListeningState.Error] instead (the screen shows + Curro speaks).
     *
     * @param messageResId `R.string.*` reference.
     */
    data class ShowToast(val messageResId: Int) : LauncherSideEffect

    /**
     * SF-1.4 — launch the app with the given package name via
     * `PackageManager.getLaunchIntentForPackage`.
     */
    data class LaunchApp(val packageName: String) : LauncherSideEffect

    /**
     * SF-1.6 — five-tap clock gesture completed; navigate to the config menu route.
     */
    data object OpenConfig : LauncherSideEffect

    /**
     * SF-2.3 (US-017) — ask the screen to fire its
     * `ActivityResultLauncher(RequestPermission())` for RECORD_AUDIO. The result is
     * delivered back via [LauncherEvent.RecordAudioPermissionResult].
     */
    data object RequestRecordAudio : LauncherSideEffect

    /**
     * SF-3.6 (US-024) — surface the parsed FunctionCall JSON to the listening
     * overlay for debug-only visual verification. Render only in
     * `BuildConfig.DEBUG`; never in release. Phase 5 removes this side effect
     * (the full FSM owns its own debug surface).
     */
    data class ShowDebugJson(val prettyJson: String) : LauncherSideEffect

    /**
     * SF-4.6 (US-030) — open HyperOS's notification-access settings page so the
     * user can grant [android.permission.BIND_NOTIFICATION_LISTENER_SERVICE].
     * The screen starts [android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS].
     */
    data object OpenNotificationAccessSettings : LauncherSideEffect

    /**
     * SF-4.10 (US-034) — ask the screen to fire its
     * `ActivityResultLauncher(RequestPermission())` for READ_CONTACTS.
     * Result delivered via [LauncherEvent.ReadContactsPermissionResult].
     */
    data object RequestReadContacts : LauncherSideEffect

    /**
     * SF-4.10 (US-034) — ask the screen to fire its
     * `ActivityResultLauncher(RequestPermission())` for CALL_PHONE.
     * Result delivered via [LauncherEvent.CallPhonePermissionResult].
     */
    data object RequestCallPhone : LauncherSideEffect
}
