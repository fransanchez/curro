package com.curro.app.domain.usecase

import com.curro.app.di.DefaultDispatcher
import com.curro.app.domain.model.ClockState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Emits a [ClockState] immediately and then once per second, on the [DefaultDispatcher].
 *
 * Runs entirely in the domain layer — no Android imports. [LocalDateTime.now()] is used
 * instead of [java.time.Clock] to avoid threading a `Clock` parameter through the graph
 * at this stage; for deterministic unit tests the use case is replaced by a fake
 * returning a [kotlinx.coroutines.flow.MutableSharedFlow] or
 * [kotlinx.coroutines.flow.flowOf].
 *
 * The [DefaultDispatcher] qualifier keeps the 1-second ticker off the Main thread.
 * Callers (the ViewModel) convert to a `StateFlow` via `stateIn(WhileSubscribed)` so
 * the ticker pauses when Curro is fully backgrounded.
 *
 * **Formatting:**
 * - Time: `HH:mm` (e.g. `"12:47"`) — 24-hour, no seconds.
 * - Date: `EEEE d MMMM` with `Locale("es", "ES")` then first character uppercased
 *   (e.g. `"Miércoles 13 mayo"`). The pattern intentionally omits the year — the clock
 *   face spec §11 shows day + month only.
 */
class ObserveClockUseCase
    @Inject
    constructor(
        @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        operator fun invoke(): Flow<ClockState> =
            flow {
                while (true) {
                    emit(buildClockState())
                    delay(TICK_MILLIS)
                }
            }.flowOn(dispatcher)

        private fun buildClockState(): ClockState {
            val now = LocalDateTime.now()
            val timeText = TIME_FORMATTER.format(now)
            val rawDate = DATE_FORMATTER.format(now)
            val dateText = rawDate.replaceFirstChar { it.uppercase(DATE_LOCALE) }
            return ClockState(timeText = timeText, dateText = dateText)
        }

        private companion object {
            const val TICK_MILLIS = 1_000L
            val DATE_LOCALE: Locale = Locale("es", "ES")
            val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", DATE_LOCALE)
        }
    }
