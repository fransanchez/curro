package com.curro.app.assistant

/**
 * Test-only [TimeProvider] with a mutable [nowMs]. Used by every JVM test
 * that constructs an [AssistantStateMachine] or `AssistantCoordinator` so
 * deadline / timer assertions are deterministic.
 *
 * @param nowMs initial epoch-ms; the tests mutate this directly to advance
 *   time.
 */
class TestTimeProvider(
    var nowMs: Long = 0L,
) : TimeProvider {
    override fun now(): Long = nowMs
}
