package com.curro.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test — JUnit 4 + AndroidJUnit4 runner.
 *
 * Purpose: prove the instrumented test source set is wired and can launch the Activity.
 * Intentionally does NOT use Hilt — the Hilt-aware version (with HiltAndroidRule) arrives
 * in SF-0.2 once HiltTestRunner is defined.
 *
 * Run with: ./gradlew connectedAndroidTest (needs a running device/emulator)
 *
 * Note: instrumented tests stay on JUnit 4. JUnit 5 is NOT supported on instrumented
 * Android by AGP at the time of writing — this is a hard framework split (Architect's notes A5).
 * Do NOT add junit-jupiter-* to androidTestImplementation.
 */
@RunWith(AndroidJUnit4::class)
class InstrumentedSmokeTest {
    @Test
    fun activityLaunchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // The scenario launches and closes cleanly — no crash = pass.
        }
    }
}
