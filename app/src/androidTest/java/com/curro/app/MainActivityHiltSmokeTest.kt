package com.curro.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hilt-injected instrumented smoke test. Proves three things at once:
 *   1. The Hilt graph compiles end-to-end (HiltAndroidRule would otherwise fail).
 *   2. HiltTestApplication boots and MainActivity launches with @AndroidEntryPoint.
 *   3. The Compose tree from US-001 still renders "Curro".
 *
 * Supersedes the US-001 `InstrumentedSmokeTest` (that file is deleted).
 *
 * Stays on JUnit 4 + AndroidJUnit4 — JUnit 5 is not supported on instrumented Android
 * by AGP (see US-001 brief Architect note A5). Do NOT add junit-jupiter-* here.
 *
 * Rule ordering is non-negotiable (see A2 in US-002 brief):
 *   order = 0  → HiltAndroidRule injects first
 *   order = 1  → createAndroidComposeRule launches the Activity after Hilt is ready
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityHiltSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun appBootsAndRendersCurro() {
        composeRule.onNodeWithText("Curro").assertIsDisplayed()
    }
}
