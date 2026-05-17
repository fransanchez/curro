package com.curro.app.presentation.config.aliases

import app.cash.turbine.test
import com.curro.app.data.local.AliasSource
import com.curro.app.domain.repository.AliasView
import com.curro.app.util.FakeAliasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-8.2 (US-051) — [AliasesViewModel] unit tests.
 *
 * Uses [FakeAliasRepository] — no Room, no real DataStore. All 9 test cases
 * match the brief's §8.2 test specification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("AliasesViewModel (SF-8.2)")
class AliasesViewModelTest {
    private lateinit var aliasRepo: FakeAliasRepository
    private lateinit var vm: AliasesViewModel

    private val alice =
        AliasView(alias = "mi madre", displayName = "Ana García", source = AliasSource.LEARNED, useCount = 3)
    private val bob =
        AliasView(alias = "el médico", displayName = "Dr. López", source = AliasSource.EXPLICIT, useCount = 1)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        aliasRepo = FakeAliasRepository()
        vm = AliasesViewModel(aliasRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState aliases reflect observeAll emissions`() =
        runTest {
            aliasRepo.observeAllStream.value = listOf(alice, bob)
            vm.uiState.test {
                assertEquals(listOf(alice, bob), awaitItem().aliases)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `AddPressed sets showAddDialog true and clears editTarget`() =
        runTest {
            vm.uiState.test {
                awaitItem() // initial state

                vm.onEvent(AliasesEvent.EditPressed(alice)) // set editTarget first
                awaitItem()

                vm.onEvent(AliasesEvent.AddPressed)
                val state = awaitItem()
                assertTrue(state.showAddDialog)
                assertNull(state.editTarget)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `EditPressed sets editTarget and clears showAddDialog`() =
        runTest {
            vm.uiState.test {
                awaitItem() // initial
                vm.onEvent(AliasesEvent.EditPressed(alice))
                val state = awaitItem()
                assertEquals(alice, state.editTarget)
                assertFalse(state.showAddDialog)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `DeletePressed sets pendingDelete`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(AliasesEvent.DeletePressed(bob))
                val state = awaitItem()
                assertEquals(bob, state.pendingDelete)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `DismissDialog clears all dialog flags`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(AliasesEvent.DeletePressed(alice))
                awaitItem()
                vm.onEvent(AliasesEvent.DismissDialog)
                val state = awaitItem()
                assertNull(state.pendingDelete)
                assertNull(state.editTarget)
                assertFalse(state.showAddDialog)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `ConfirmDelete calls aliasRepo delete with pendingDelete alias`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(AliasesEvent.DeletePressed(alice))
                awaitItem()
                vm.onEvent(AliasesEvent.ConfirmDelete)
                val state = awaitItem()
                assertNull(state.pendingDelete)
                assertTrue(aliasRepo.deleteCalls.contains("mi madre"))
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `ConfirmDelete with no pendingDelete is a no-op`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(AliasesEvent.ConfirmDelete)
                // No second emission expected — state unchanged.
                assertTrue(aliasRepo.deleteCalls.isEmpty())
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `SaveAlias calls aliasRepo learn and closes dialog`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(AliasesEvent.AddPressed)
                awaitItem()
                vm.onEvent(AliasesEvent.SaveAlias(alias = "mi hija", contactName = "Lucía"))
                val state = awaitItem()
                assertFalse(state.showAddDialog)
                assertNull(state.editTarget)
                val learn = aliasRepo.learnCalls.firstOrNull()
                assertNotNull(learn)
                assertEquals("mi hija", learn!!.alias)
                assertEquals(AliasSource.EXPLICIT, learn.source)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `SaveAlias with blank alias is ignored`() =
        runTest {
            vm.uiState.test {
                awaitItem()
                vm.onEvent(AliasesEvent.SaveAlias(alias = "  ", contactName = "Lucía"))
                // No emission — state unchanged.
                assertTrue(aliasRepo.learnCalls.isEmpty())
                cancelAndConsumeRemainingEvents()
            }
        }
}
