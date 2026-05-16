package com.curro.app.assistant

/**
 * Thrown when [AssistantStateMachine.transition] is called with an
 * `(state, event)` pair the spec §6 diagram does not allow. This is **always**
 * a caller bug — the coordinator and `MainActivity` must only send events
 * valid for the current state.
 *
 * The exception carries [state] and [event] so test failures can show the
 * offending pair without parsing the message.
 */
class IllegalAssistantTransition(
    val state: AssistantState,
    val event: AssistantEvent,
) : IllegalStateException("Invalid transition: $state + $event")
