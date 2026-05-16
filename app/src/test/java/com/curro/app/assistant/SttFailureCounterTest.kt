package com.curro.app.assistant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * SF-5.4 / US-038 — pure unit tests for the consecutive-failure counter.
 *
 * Coordinator integration (the 1st → fail_1, 2nd → fail_2, 3rd → fail_3 copy
 * choice and the reset-on-final-transcript rule) is covered by Group N tests
 * on `AssistantCoordinatorTest`.
 */
class SttFailureCounterTest {
    @Test
    fun `recordFailure increments and returns the new count`() {
        val c = SttFailureCounter()
        assertEquals(1, c.recordFailure())
        assertEquals(2, c.recordFailure())
        assertEquals(3, c.recordFailure())
        assertEquals(4, c.recordFailure())
        assertEquals(5, c.recordFailure())
    }

    @Test
    fun `recordSuccess resets to zero`() {
        val c = SttFailureCounter()
        c.recordFailure()
        c.recordFailure()
        c.recordSuccess()
        assertEquals(1, c.recordFailure())
        assertEquals(2, c.recordFailure())
    }

    @Test
    fun `fail fail success fail sequence returns 1 2 1`() {
        val c = SttFailureCounter()
        assertEquals(1, c.recordFailure())
        assertEquals(2, c.recordFailure())
        c.recordSuccess()
        assertEquals(1, c.recordFailure())
    }
}
