package com.curro.app.domain.model

/**
 * Snapshot of the current time and date, pre-formatted for display on the launcher home.
 *
 * Both fields are formatted strings — composables never do date/time formatting; that
 * responsibility lives in [com.curro.app.domain.usecase.ObserveClockUseCase].
 *
 * @param timeText 24-hour time string, e.g. `"12:47"` (`HH:mm`).
 * @param dateText Localised date in sentence case, e.g. `"Miércoles 13 mayo"` (`EEEE d MMMM`,
 *   `Locale("es", "ES")`, first char uppercased). Empty string until the first tick fires.
 */
data class ClockState(
    val timeText: String,
    val dateText: String,
)
