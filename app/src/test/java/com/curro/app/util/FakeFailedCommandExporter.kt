package com.curro.app.util

import com.curro.app.data.failures.FailedCommandAnonymiser
import com.curro.app.data.failures.FailedCommandExporter
import com.curro.app.presentation.launcher.LauncherSideEffectBus

/**
 * Convenience factory for a [FailedCommandExporter] wired to test fakes (SF-8.7 / US-057).
 *
 * Creates the exporter with a fresh [FakeFailedCommandLog], a real [FailedCommandAnonymiser]
 * (pure function — no Android dependencies), and a new [LauncherSideEffectBus].
 *
 * Use this when the test just needs a non-crashing exporter (e.g. in [ConfigViewModel] tests
 * that exercise other code paths). For export-specific assertions, construct the exporter
 * directly with the test's shared [FakeFailedCommandLog] and [LauncherSideEffectBus].
 */
fun testExporter(
    log: FakeFailedCommandLog = FakeFailedCommandLog(),
    bus: LauncherSideEffectBus = LauncherSideEffectBus(),
): FailedCommandExporter = FailedCommandExporter(log, FailedCommandAnonymiser(), bus)
