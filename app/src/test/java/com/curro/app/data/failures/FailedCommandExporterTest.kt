package com.curro.app.data.failures

import app.cash.turbine.test
import com.curro.app.data.local.FailedCommandEntity
import com.curro.app.data.local.FailureKind
import com.curro.app.presentation.launcher.LauncherSideEffect
import com.curro.app.presentation.launcher.LauncherSideEffectBus
import com.curro.app.util.FakeFailedCommandLog
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-8.7 (US-057) — [FailedCommandExporter] unit tests.
 */
@DisplayName("FailedCommandExporter (SF-8.7)")
class FailedCommandExporterTest {
    private lateinit var log: FakeFailedCommandLog
    private lateinit var bus: LauncherSideEffectBus
    private lateinit var subject: FailedCommandExporter

    @Suppress("MagicNumber")
    private val unsentEntity =
        FailedCommandEntity(
            id = 10L,
            transcript = "abre el tiempo",
            kind = FailureKind.UNKNOWN_FUNCTION,
            details = "open_weather",
            timestampMs = 1_000_000L,
            sent = false,
        )

    @BeforeEach
    fun setUp() {
        log = FakeFailedCommandLog()
        bus = LauncherSideEffectBus()
        subject = FailedCommandExporter(log, FailedCommandAnonymiser(), bus)
    }

    @Test
    fun `exportUnsent emits ShareText to the side-effect bus`() =
        runTest {
            log.emitEntities(listOf(unsentEntity))
            bus.effects.test {
                subject.exportUnsent()
                val effect = awaitItem()
                assertTrue(effect is LauncherSideEffect.ShareText)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `exportUnsent share text does not contain transcript`() =
        runTest {
            log.emitEntities(listOf(unsentEntity))
            bus.effects.test {
                subject.exportUnsent()
                val effect = awaitItem() as LauncherSideEffect.ShareText
                assertFalse(effect.shareText.contains(unsentEntity.transcript))
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `exportUnsent marks exported entities as sent`() =
        runTest {
            log.emitEntities(listOf(unsentEntity))
            subject.exportUnsent()
            assertTrue(log.markedSentIds.contains(unsentEntity.id))
        }

    @Test
    fun `exportUnsent is a no-op when there are no unsent entries`() =
        runTest {
            val sentEntity = unsentEntity.copy(sent = true)
            log.emitEntities(listOf(sentEntity))
            subject.exportUnsent()
            assertTrue(log.markedSentIds.isEmpty())
        }

    @Test
    fun `exportUnsent share text contains kind label`() =
        runTest {
            log.emitEntities(listOf(unsentEntity))
            bus.effects.test {
                subject.exportUnsent()
                val effect = awaitItem() as LauncherSideEffect.ShareText
                assertTrue(effect.shareText.contains("Función desconocida"))
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `exportUnsent share text contains details`() =
        runTest {
            log.emitEntities(listOf(unsentEntity))
            bus.effects.test {
                subject.exportUnsent()
                val effect = awaitItem() as LauncherSideEffect.ShareText
                assertEquals(true, effect.shareText.contains(unsentEntity.details))
                cancelAndConsumeRemainingEvents()
            }
        }
}
