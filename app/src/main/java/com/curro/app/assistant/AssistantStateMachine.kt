package com.curro.app.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single owner of [AssistantState] transitions. Exposes a read-only
 * [StateFlow]<[AssistantState]> and a single [transition] entry point that
 * validates the `(current, event)` pair against the spec §6 diagram.
 *
 * Invalid transitions throw [IllegalAssistantTransition] — callers (coordinator,
 * `MainActivity`) must only send events valid for the current state. The
 * Phase-5 coordinator achieves that by structure (each step in `runListenLoop`
 * knows which state it's transitioning from). Tests cover every invalid pair
 * to make sure the FSM rejects them.
 *
 * **There is no separate mutable-state instance.** The class is `@Singleton`;
 * the same instance is injected into the coordinator, the VM (read-only), and
 * `MainActivity`. The [state] flow is the truth.
 */
@Singleton
class AssistantStateMachine
    @Inject
    constructor() {
        private val mutableState = MutableStateFlow<AssistantState>(AssistantState.Idle)
        val state: StateFlow<AssistantState> = mutableState.asStateFlow()

        /**
         * Apply [event] and return the new state. Throws
         * [IllegalAssistantTransition] if the `(current, event)` pair is invalid
         * per spec §6.
         *
         * Thread-safety: [MutableStateFlow]'s value assignment is atomic. The
         * FSM does not cross-check that a thread-unsafe interleaving produced a
         * stale read — the coordinator's `currentJob` discipline (SF-5.2) means
         * only one sequence runs at a time per turn. Tests run on a single test
         * dispatcher.
         */
        fun transition(event: AssistantEvent): AssistantState {
            val current = mutableState.value
            val next =
                computeNext(current, event)
                    ?: throw IllegalAssistantTransition(current, event)
            mutableState.value = next
            return next
        }

        @Suppress("CyclomaticComplexMethod", "CognitiveComplexMethod", "LongMethod")
        private fun computeNext(
            current: AssistantState,
            event: AssistantEvent,
        ): AssistantState? =
            when (event) {
                is AssistantEvent.MicPressed ->
                    AssistantState.Listening(
                        partial = "",
                        startedAtMs = event.timestamp,
                    )
                is AssistantEvent.PartialTranscript ->
                    when (current) {
                        is AssistantState.Listening -> current.copy(partial = event.partial)
                        else -> null
                    }
                is AssistantEvent.FinalTranscript ->
                    when (current) {
                        is AssistantState.Listening ->
                            AssistantState.Processing(
                                transcript = event.transcript,
                                startedAtMs = event.timestamp,
                            )
                        else -> null
                    }
                is AssistantEvent.SttFailed ->
                    when (current) {
                        is AssistantState.Listening ->
                            AssistantState.ErrorRecovery(
                                message = event.message,
                                failureCount = event.failureCount,
                            )
                        else -> null
                    }
                is AssistantEvent.FunctionCallReady ->
                    when (current) {
                        is AssistantState.Processing ->
                            if (event.needsConfirmation) {
                                val prompt =
                                    requireNotNull(event.prompt) {
                                        "FunctionCallReady(needsConfirmation=true) requires a prompt"
                                    }
                                val pendingAction =
                                    requireNotNull(event.pendingAction) {
                                        "FunctionCallReady(needsConfirmation=true) requires a pendingAction"
                                    }
                                AssistantState.Confirming(
                                    prompt = prompt,
                                    expiresAtMs = event.expiresAtMs,
                                    pendingAction = pendingAction,
                                )
                            } else {
                                AssistantState.Executing(
                                    speech = event.speech,
                                    screen = event.screen,
                                )
                            }
                        else -> null
                    }
                is AssistantEvent.UserConfirmed ->
                    when (current) {
                        is AssistantState.Confirming ->
                            AssistantState.Executing(
                                speech = event.speech,
                                screen = event.screen,
                            )
                        else -> null
                    }
                is AssistantEvent.UserRejected ->
                    when (current) {
                        is AssistantState.Confirming ->
                            AssistantState.Executing(
                                speech = event.speech,
                                screen = event.screen,
                            )
                        else -> null
                    }
                is AssistantEvent.ConfirmationTimedOut ->
                    when (current) {
                        is AssistantState.Confirming ->
                            AssistantState.Executing(
                                speech = event.speech,
                                screen = null,
                            )
                        else -> null
                    }
                is AssistantEvent.LowConfidenceClarify ->
                    when (current) {
                        is AssistantState.Processing ->
                            AssistantState.ErrorRecovery(
                                message = event.message,
                                failureCount = 0,
                            )
                        else -> null
                    }
                AssistantEvent.ExecutionDone,
                AssistantEvent.RecoverySpoken,
                ->
                    when (current) {
                        is AssistantState.Executing,
                        is AssistantState.ErrorRecovery,
                        -> AssistantState.Idle
                        else -> null
                    }
                AssistantEvent.HomePressed -> AssistantState.Idle
            }
    }
