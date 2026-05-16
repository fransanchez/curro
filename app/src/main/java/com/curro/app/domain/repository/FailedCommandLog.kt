package com.curro.app.domain.repository

import com.curro.app.data.local.FailedCommandEntity
import com.curro.app.data.local.FailureKind
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence of commands Curro couldn't act on (SF-7.5 / US-049, spec §6
 * flow 7 + §9 "Logs de comandos fallidos").
 *
 * The implementation ([com.curro.app.data.local.RoomFailedCommandLog]) caps the
 * table at 50 (`local-data` rule 4) via [com.curro.app.data.local.FailedCommandDao.insertAndTrim].
 *
 * **Privacy** (spec §12): the [FailedCommandEntity.transcript] field stays on
 * the device. The PostHog/Firebase `command_failed` telemetry event carries
 * `kind` + `function_name` ONLY — `transcript` is NOT on the
 * [com.curro.app.data.telemetry.TelemetryGuardrail.ALLOWED_PROPS] whitelist.
 * The Phase-8 config menu UI is the only surface that reads this table.
 *
 * **Single writer**: [com.curro.app.assistant.AssistantCoordinator] is the
 * only caller of [record]. The [com.curro.app.domain.handler.HandlerDispatcher]
 * does NOT touch this interface — it bubbles errors via
 * [com.curro.app.domain.handler.HandlerResult.Failed], which the coordinator
 * routes through [com.curro.app.assistant.AssistantCoordinator.renderHandlerFailure]
 * to record.
 */
interface FailedCommandLog {
    /**
     * Persist a failure. Atomic insert + trim-to-50.
     *
     * @param transcript the user's utterance (PII — stays on-device).
     * @param kind which of the three failure paths fired (see [FailureKind]).
     * @param details free-form diagnostic context (function name, error class
     *   simple name, etc. — anything safe to read in Fran's Phase-8 UI; **NOT**
     *   pushed to telemetry).
     */
    suspend fun record(
        transcript: String,
        kind: FailureKind,
        details: String = "",
    )

    /** Phase-8 UI subscription: top-[limit] by timestamp descending. */
    fun observeRecent(limit: Int = 50): Flow<List<FailedCommandEntity>>

    /** Total row count — Phase-8 UI may display the badge "50 / 50". */
    suspend fun count(): Int

    /** Phase-8 "borrar log" affordance. */
    suspend fun deleteAll()
}
