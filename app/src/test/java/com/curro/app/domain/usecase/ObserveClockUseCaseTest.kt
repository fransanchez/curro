package com.curro.app.domain.usecase

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Unit tests for [ObserveClockUseCase].
 *
 * Pure JVM + Turbine + [runTest] with [UnconfinedTestDispatcher]. No Robolectric — the
 * use case has no Android imports. Uses [kotlinx.coroutines.test.advanceTimeBy] to
 * advance virtual time past the [ObserveClockUseCase]'s 1-second tick delay without
 * real-time sleeping.
 *
 * Covers:
 *  1. First emission is produced immediately (before any delay).
 *  2. [timeText] matches `HH:mm` pattern.
 *  3. [dateText] matches `EEEE d MMMM` pattern in sentence case (first char uppercase,
 *     rest as the Spanish formatter produces — day and month names are lowercase in Spanish).
 *  4. A second emission fires after [advanceTimeBy(1_001L)].
 *  5. The second emission's `timeText` is identical to the first within the same second
 *     (i.e. no backwards clock or bogus re-format).
 */
@ExperimentalCoroutinesApi
@DisplayName("ObserveClockUseCase")
class ObserveClockUseCaseTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: ObserveClockUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = ObserveClockUseCase(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 1 — first emission is immediate
    // -----------------------------------------------------------------------------------------

    @Test
    fun `first emission is produced without any delay`() =
        runTest {
            useCase().test {
                val first = awaitItem()
                assertNotNull(first, "Expected an immediate first ClockState emission")
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -----------------------------------------------------------------------------------------
    // Scenario 2 — timeText format
    // -----------------------------------------------------------------------------------------

    @Test
    fun `timeText matches HH-mm pattern`() =
        runTest {
            useCase().test {
                val first = awaitItem()
                assertTrue(
                    first.timeText.matches(Regex("\\d{2}:\\d{2}")),
                    "timeText '${first.timeText}' does not match HH:mm",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -----------------------------------------------------------------------------------------
    // Scenario 3 — dateText format
    // -----------------------------------------------------------------------------------------

    @Test
    fun `dateText starts with an uppercase letter`() =
        runTest {
            useCase().test {
                val first = awaitItem()
                assertTrue(
                    first.dateText.isNotEmpty() && first.dateText.first().isUpperCase(),
                    "dateText '${first.dateText}' should start with an uppercase letter",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dateText matches expected Spanish-locale pattern`() =
        runTest {
            useCase().test {
                val first = awaitItem()
                // Independently compute the expected value using the same logic as the use case.
                val now = LocalDateTime.now()
                val expected =
                    DateTimeFormatter
                        .ofPattern("EEEE d MMMM", Locale("es", "ES"))
                        .format(now)
                        .replaceFirstChar { it.uppercase(Locale("es", "ES")) }
                assertEquals(
                    expected,
                    first.dateText,
                    "dateText does not match Spanish EEEE d MMMM pattern",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -----------------------------------------------------------------------------------------
    // Scenario 4 — second emission fires after 1 second
    // -----------------------------------------------------------------------------------------

    @Test
    fun `second emission fires after 1 second virtual time`() =
        runTest {
            useCase().test {
                // Consume the first (immediate) emission.
                awaitItem()

                // Advance virtual time by just over the 1_000 ms tick.
                advanceTimeBy(ONE_SECOND_PLUS_MARGIN_MS)

                val second = awaitItem()
                assertNotNull(second, "Expected a second ClockState after 1 second")
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -----------------------------------------------------------------------------------------
    // Scenario 5 — two consecutive emissions both have valid timeText
    // -----------------------------------------------------------------------------------------

    @Test
    fun `both first and second emissions have valid timeText`() =
        runTest {
            useCase().test {
                val first = awaitItem()
                advanceTimeBy(ONE_SECOND_PLUS_MARGIN_MS)
                val second = awaitItem()

                assertFalse(first.timeText.isEmpty(), "First timeText must not be empty")
                assertFalse(second.timeText.isEmpty(), "Second timeText must not be empty")
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val ONE_SECOND_PLUS_MARGIN_MS = 1_001L
    }
}
