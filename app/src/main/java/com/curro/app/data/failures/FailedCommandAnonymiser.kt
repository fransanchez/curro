package com.curro.app.data.failures

import com.curro.app.data.local.FailedCommandEntity
import com.curro.app.data.local.FailureKind
import com.curro.app.presentation.config.failures.FailureView
import javax.inject.Inject

/**
 * Formats [FailedCommandEntity] instances into a safe, shareable string (SF-8.7 / US-057).
 *
 * **Privacy**: [FailedCommandEntity.transcript] is NEVER included in the output.
 * Only [FailedCommandEntity.timestampMs] (formatted), [FailedCommandEntity.kind], and
 * [FailedCommandEntity.details] are serialised — all three are on the
 * `TelemetryGuardrail.ALLOWED_PROPS` whitelist or equivalent. See spec §12.
 *
 * The output is plain text suitable for WhatsApp / email / any share-sheet target.
 */
class FailedCommandAnonymiser
    @Inject
    constructor() {
        /**
         * Produces a human-readable, Fran-friendly summary of [entities].
         *
         * Example output:
         * ```
         * Fallos de Curro — 2 entradas
         *
         * [17 may 12:34] Salida inválida — SyntaxError
         * [16 may 09:10] Error de acción — call_contact/ContactNotFound
         * ```
         */
        fun format(entities: List<FailedCommandEntity>): String {
            val header = "Fallos de Curro — ${entities.size} entr${if (entities.size == 1) "ada" else "adas"}"
            val lines =
                entities.joinToString(separator = "\n") { entity ->
                    val time = FailureView.formatTime(entity.timestampMs)
                    val kind = kindLabel(entity.kind)
                    val details = entity.details.ifBlank { "—" }
                    "[$time] $kind — $details"
                }
            return "$header\n\n$lines"
        }

        private fun kindLabel(kind: FailureKind): String =
            when (kind) {
                FailureKind.INVALID_OUTPUT -> "Salida inválida"
                FailureKind.UNKNOWN_FUNCTION -> "Función desconocida"
                FailureKind.HANDLER_ERROR -> "Error de acción"
            }
    }
