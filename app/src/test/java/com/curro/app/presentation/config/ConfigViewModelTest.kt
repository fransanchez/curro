package com.curro.app.presentation.config

import android.content.Context
import app.cash.turbine.test
import com.curro.app.R
import com.curro.app.assistant.FakeSettingsRepository
import com.curro.app.domain.repository.AliasView
import com.curro.app.util.FakeAliasRepository
import com.curro.app.util.FakeFailedCommandLog
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-8.1 (US-050) — [ConfigViewModel] unit tests.
 *
 * Uses [FakeAliasRepository], [FakeFailedCommandLog], and [FakeSettingsRepository]
 * to drive the `combine` flow without a real DataStore or Room.
 *
 * Six test cases as specified in the brief.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ConfigViewModel (SF-8.1)")
class ConfigViewModelTest {
    private lateinit var aliasRepo: FakeAliasRepository
    private lateinit var failedLog: FakeFailedCommandLog
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var context: Context
    private lateinit var vm: ConfigViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        aliasRepo = FakeAliasRepository()
        failedLog = FakeFailedCommandLog()
        settingsRepo = FakeSettingsRepository()
        context = mockk(relaxed = true)
        // Make context.getString return a predictable string for summary formatting.
        // Context.getString(resId, vararg args) — the vararg arg comes in as Object[]; use
        // anyVararg() to capture it and pull the count from args[0].
        every { context.getString(any<Int>(), *anyVararg()) } answers {
            val id = firstArg<Int>()
            val args = call.invocation.args
            val count = if (args.size > 1) (args[1] as? Array<*>)?.firstOrNull() as? Int ?: 0 else 0
            when (id) {
                R.string.copy_config_summary_aliases_count -> "$count alias guardados"
                R.string.copy_config_summary_failures_count -> "$count fallos sin revisar"
                else -> "unknown_string_$id"
            }
        }
        vm = ConfigViewModel(aliasRepo, failedLog, settingsRepo, context)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState emits nine sections initially`() =
        runTest {
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(9, state.sections.size)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState emits alias count summary when aliases emit 2 aliases`() =
        runTest {
            val twoAliases =
                listOf(
                    AliasView("mi hija", "María García", com.curro.app.data.local.AliasSource.LEARNED, 3),
                    AliasView("mi hijo", "Pedro García", com.curro.app.data.local.AliasSource.EXPLICIT, 1),
                )
            aliasRepo.observeAllStream.value = twoAliases
            vm.uiState.test {
                val state = awaitItem()
                val navigable = state.sections.filterIsInstance<ConfigSection.Navigable>()
                val aliasSection = navigable.first { it.route == "config/aliases" }
                assertEquals("2 alias guardados", aliasSection.summary)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState emits failures count summary when failedLog emits 5 failures`() =
        runTest {
            val entities =
                (1..5).map {
                    com.curro.app.data.local.FailedCommandEntity(
                        id = it.toLong(),
                        transcript = "test $it",
                        kind = com.curro.app.data.local.FailureKind.INVALID_OUTPUT,
                        timestampMs = System.currentTimeMillis(),
                    )
                }
            failedLog.emitEntities(entities)
            vm.uiState.test {
                val state = awaitItem()
                val navigable = state.sections.filterIsInstance<ConfigSection.Navigable>()
                val failSection = navigable.first { it.route == "config/failures" }
                assertEquals("5 fallos sin revisar", failSection.summary)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState incomingCallEnabled reflects settingsRepo flow`() =
        runTest {
            settingsRepo.setIncomingCallModeEnabled(true)
            vm.uiState.test {
                val state = awaitItem()
                assertTrue(state.incomingCallEnabled)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `uiState sendFailuresEnabled reflects settingsRepo flow`() =
        runTest {
            settingsRepo.setSendFailuresEnabled(true)
            vm.uiState.test {
                val state = awaitItem()
                assertTrue(state.sendFailuresEnabled)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `onEvent ToggleChanged logs warning and does NOT mutate settings`() =
        runTest {
            val toggleSection =
                ConfigSection.Toggle(
                    titleResId = R.string.copy_config_section_incoming_call,
                    helpResId = R.string.copy_config_incoming_call_help_short,
                    value = false,
                    onChangeWillBeWiredInSF = "SF-8.7",
                )
            vm.onEvent(ConfigEvent.ToggleChanged(toggleSection, true))
            // Settings setter must NOT have been called.
            assertTrue(settingsRepo.incomingCallModeSetCalls.isEmpty())
        }
}
