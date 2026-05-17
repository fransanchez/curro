package com.curro.app.presentation.config.failures

import com.curro.app.data.local.FailureKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Presentation-layer snapshot of a failed command row (SF-8.6 / US-055).
 *
 * Produced by mapping a [com.curro.app.data.local.FailedCommandEntity] inside the ViewModel
 * so the composables never import `data.local` types.
 *
 * @param id Stable key for LazyColumn — maps to [com.curro.app.data.local.FailedCommandEntity.id].
 * @param displayTime Formatted timestamp string (e.g. "17 may 12:34").
 * @param kind Which failure path fired.
 * @param details Diagnostic context — safe to display to Fran; NOT for telemetry.
 * @param sent Whether this entry has already been exported via the share sheet.
 */
data class FailureView(
    val id: Long,
    val displayTime: String,
    val kind: FailureKind,
    val details: String,
    val sent: Boolean,
) {
    companion object {
        /**
         * A short date+time format. No contact names or transcripts are included —
         * this is safe to display.
         */
        private val DISPLAY_FORMAT: DateTimeFormatter =
            DateTimeFormatter
                .ofPattern("d MMM HH:mm", Locale("es", "ES"))
                .withZone(ZoneId.systemDefault())

        /** Format [timestampMs] into a Spanish date-time string. */
        fun formatTime(timestampMs: Long): String = DISPLAY_FORMAT.format(Instant.ofEpochMilli(timestampMs))
    }
}
