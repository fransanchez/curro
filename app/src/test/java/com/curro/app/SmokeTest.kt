package com.curro.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM smoke test — JUnit 5 (Jupiter API).
 *
 * Purpose: prove that the `de.mannodermaus.android-junit5` Gradle plugin successfully
 * surfaces JUnit 5 to AGP's `testDebugUnitTest` task (Architect's notes A5).
 *
 * Run with: ./gradlew testDebugUnitTest
 * Expected: 1 test discovered, 1 passed, 0 failed.
 *
 * All real feature tests land with their owning SF.
 */
class SmokeTest {
    @Test
    fun `two plus two equals four`() {
        assertEquals(4, 2 + 2)
    }
}
