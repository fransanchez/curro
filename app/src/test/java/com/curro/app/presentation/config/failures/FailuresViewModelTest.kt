package com.curro.app.presentation.config.failures

import app.cash.turbine.test
import com.curro.app.data.local.FailedCommandEntity
import com.curro.app.data.local.FailureKind
import com.curro.app.util.FakeFailedCommandLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-8.6 (US-055) — [FailuresViewModel] unit tests.
 *
 * Uses [FakeFailedCommandLog]. No Android dependencies — runs on JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("FailuresViewModel (SF-8.6)")
class FailuresViewModelTest {
    private lateinit var log: FakeFailedCommandLog
    private lateinit var vm: FailuresViewModel

    @Suppress("MagicNumber")
    private val entity1 =
        FailedCommandEntity(
            id = 1L,
            transcript = "llama a mi hijo",
            kind = FailureKind.INVALID_OUTPUT,
            details = "SyntaxError",
            timestampMs = 1_000_000L,
            sent = false,
        )

    @Suppress("MagicNumber")
    private val entity2 =
        FailedCommandEntity(
            id = 2L,
            transcript = "ponme música",
            kind = FailureKind.UNKNOWN_FUNCTION,
            details = "play_music",
            timestampMs = 2_000_000L,
            sent = true,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        log = FakeFailedCommandLog()
        vm = FailuresViewModel(log)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty failure list and no filter`() =
        runTest {
            vm.uiState.test {
                val state = awaitItem()
                assertTrue(state.allFailures.isEmpty())
                assertNull(state.activeFilter)
                assertFalse(state.showClearDialog)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `entities are mapped to FailureView without exposing transcript`() =
        runTest {
            log.emitEntities(listOf(entity1))
            vm.uiState.test {
                val state = awaitItem()
                val view = state.allFailures.first()
                assertEquals(1L, view.id)
                assertEquals(FailureKind.INVALID_OUTPUT, view.kind)
                assertEquals("SyntaxError", view.details)
                assertFalse(view.sent)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `FilterChanged updates activeFilter and filters visible list`() =
        runTest {
            log.emitEntities(listOf(entity1, entity2))
            vm.uiState.test {
                awaitItem()
                vm.onEvent(FailuresEvent.FilterChanged(FailureKind.INVALID_OUTPUT))
                val state = awaitItem()
                assertEquals(FailureKind.INVALID_OUTPUT, state.activeFilter)
                assertEquals(1, state.visibleFailures.size)
                assertEquals(1L, state.visibleFailures.first().id)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `FilterChanged null shows all failures`() =
        runTest {
            log.emitEntities(listOf(entity1, entity2))
            vm.onEvent(FailuresEvent.FilterChanged(FailureKind.INVALID_OUTPUT))
            vm.uiState.test {
                awaitItem()
                vm.onEvent(FailuresEvent.FilterChanged(null))
                val state = awaitItem()
                assertNull(state.activeFilter)
                assertEquals(2, state.visibleFailures.size)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `ClearPressed sets showClearDialog true`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(FailuresEvent.ClearPressed)
                val state = awaitItem()
                assertTrue(state.showClearDialog)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `DismissClearDialog hides dialog without clearing`() =
        runTest {
            log.emitEntities(listOf(entity1))
            vm.uiState.test {
                awaitItem()
                vm.onEvent(FailuresEvent.ClearPressed)
                awaitItem()
                vm.onEvent(FailuresEvent.DismissClearDialog)
                val state = awaitItem()
                assertFalse(state.showClearDialog)
                assertEquals(1, state.allFailures.size)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `ConfirmClear deletes all entries and hides dialog`() =
        runTest {
            log.emitEntities(listOf(entity1, entity2))
            vm.uiState.test {
                awaitItem()
                vm.onEvent(FailuresEvent.ClearPressed)
                awaitItem()
                vm.onEvent(FailuresEvent.ConfirmClear)
                // ConfirmClear emits up to two updates: dialog dismissed, then allFailures cleared.
                // cancelAndConsumeRemainingEvents collects the rest; check the final value directly.
                cancelAndConsumeRemainingEvents()
            }
            // After ConfirmClear the stable state must have dialog=false and empty failures.
            val finalState = vm.uiState.value
            assertFalse(finalState.showClearDialog)
            assertTrue(finalState.allFailures.isEmpty())
        }

    @Test
    fun `new entity emission updates visible list`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                log.emitEntities(listOf(entity1))
                val state = awaitItem()
                assertEquals(1, state.allFailures.size)
                cancelAndConsumeRemainingEvents()
            }
        }
}
