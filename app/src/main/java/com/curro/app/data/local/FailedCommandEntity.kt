package com.curro.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user utterance Curro couldn't act on (SF-7.5, spec §6 flow 7 + §9 "Logs
 * de comandos fallidos").
 *
 * **Privacy (spec §12)**: [transcript] is PII. **It stays on the device.**
 * The PostHog/Firebase telemetry layer (`TelemetryGuardrail`) emits a
 * `command_failed` event with `kind` + `function_name` only — NEVER the
 * transcript. Fran's Phase-8 config menu UI is the only surface that reads
 * this table.
 *
 * - [kind]: the failure path ([FailureKind.INVALID_OUTPUT] /
 *   [FailureKind.UNKNOWN_FUNCTION] / [FailureKind.HANDLER_ERROR]) — Fran
 *   filters by this in the Phase-8 UI.
 * - [details]: an extra free-form column for diagnostic context (e.g. the
 *   raw model output for [FailureKind.INVALID_OUTPUT]; the
 *   `<action>/<error class>` for [FailureKind.HANDLER_ERROR]). Stays on
 *   device.
 * - [timestampMs]: epoch ms; descending order drives [FailedCommandDao.observeRecent].
 *
 * Capped at 50 (`local-data` rule 4) — every insert call goes through
 * [FailedCommandDao.insertAndTrim], which deletes anything older than the
 * 50 newest in the same transaction.
 */
@Entity(tableName = "failed_commands")
data class FailedCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcript: String,
    val kind: FailureKind,
    val details: String = "",
    val timestampMs: Long,
)
