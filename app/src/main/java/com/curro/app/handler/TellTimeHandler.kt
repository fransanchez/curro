package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.FunctionCall
import com.curro.app.handler.time.SpanishTimeFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Handles `tell_time` (Fase 1 catalog, US-026 / SF-4.2).
 *
 * Reads the `what` param (time | date | day | all, default all) and returns
 * a [HandlerResult.Spoken] with a colloquial Castilian phrase — never a
 * digital readout. Spec §6 canonical example: "Son las doce y cuarenta y
 * siete del miércoles trece de mayo."
 *
 * No permissions, no confirmation, no side effects — the zero-risk first
 * handler that validates the whole Phase-4 dispatch architecture.
 *
 * [clock] is injected via [TimeModule] so tests can pass `Clock.fixed(…)`
 * for deterministic assertions.
 */
class TellTimeHandler
    @Inject
    constructor(
        private val clock: Clock,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "tell_time"

        override suspend fun handle(call: FunctionCall): HandlerResult {
            val what = (call.params["what"] as? String) ?: "all"
            val now = LocalDateTime.now(clock)
            val timePhrase = SpanishTimeFormatter.formatTimePhrase(now.hour, now.minute)
            val dayPhrase = SpanishTimeFormatter.formatDayPhrase(now)
            val datePhrase = SpanishTimeFormatter.formatDatePhrase(now)
            val singular = SpanishTimeFormatter.isSingularHour(now.hour)

            val speech =
                when (what) {
                    "time" ->
                        context.getString(
                            if (singular) R.string.copy_time_one else R.string.copy_time_now,
                            timePhrase,
                        )
                    "day" -> context.getString(R.string.copy_time_day, dayPhrase)
                    "date" -> context.getString(R.string.copy_time_date, dayPhrase, datePhrase)
                    // "all" and any unknown value (validator already rejects out-of-enum, but
                    // defensive handling here) both fall through to the all-in-one phrase.
                    else ->
                        context.getString(
                            R.string.copy_time_all,
                            timePhrase,
                            dayPhrase,
                            datePhrase,
                        )
                }
            return HandlerResult.Spoken(speech)
        }
    }
