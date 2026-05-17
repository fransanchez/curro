package com.curro.app.data.failures

import com.curro.app.domain.repository.FailedCommandLog
import com.curro.app.presentation.launcher.LauncherSideEffect
import com.curro.app.presentation.launcher.LauncherSideEffectBus
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Orchestrates the "share failures with Fran" export flow (SF-8.7 / US-057).
 *
 * 1. Reads unsent [com.curro.app.data.local.FailedCommandEntity] instances from [failedLog].
 * 2. Anonymises them via [anonymiser] — transcripts are NEVER included.
 * 3. Emits a [LauncherSideEffect.ShareText] event to [sideEffectBus]; the launcher
 *    processes it and opens Android's share chooser.
 * 4. Marks the exported entries as sent in Room so they are not re-exported.
 *
 * Called by [com.curro.app.presentation.config.ConfigViewModel] when the
 * "Compartir fallos con Fran" toggle is turned on (SF-8.7 wires the setter).
 */
class FailedCommandExporter
    @Inject
    constructor(
        private val failedLog: FailedCommandLog,
        private val anonymiser: FailedCommandAnonymiser,
        private val sideEffectBus: LauncherSideEffectBus,
    ) {
        /**
         * Exports all unsent failures and marks them sent.
         *
         * A no-op when there are no unsent entries — no share sheet is opened.
         */
        suspend fun exportUnsent() {
            val unsent = failedLog.observeUnsent().first()
            if (unsent.isEmpty()) return

            val shareText = anonymiser.format(unsent)
            sideEffectBus.emit(LauncherSideEffect.ShareText(shareText))

            val ids = unsent.map { it.id }
            failedLog.markSent(ids)
        }
    }
