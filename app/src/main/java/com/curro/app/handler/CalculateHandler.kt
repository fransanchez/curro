package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.handler.calculator.SpanishExpressionParser
import com.curro.app.handler.calculator.intToSpanishWords
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/**
 * Evaluates a Spanish arithmetic expression and speaks the result (US-028 / SF-4.4).
 *
 * The `expression` param from [FunctionCall.params] is a natural-language string produced by
 * FunctionGemma (e.g. `"cuarenta y siete por ocho"`). This handler:
 *  1. Parses it via [SpanishExpressionParser.parse].
 *  2. Evaluates the token sequence.
 *  3. Formats the result with [intToSpanishWords].
 *  4. Returns `"<expression capitalised> son <result in words>."` via `copy_calc_result`.
 *
 * `needs_confirmation: NO` — arithmetic is reversible and non-destructive.
 *
 * Error mapping:
 *  - parse failure → `copy_calc_failed` + `CurroError.Calculation(reason="parse")`.
 *  - div-by-zero → `copy_calc_div_zero` + `CurroError.Calculation(reason="div_zero")`.
 *  - overflow → `copy_calc_failed` + `CurroError.Calculation(reason="overflow")`.
 *  - empty expression → `copy_calc_failed` + `CurroError.Calculation(reason="empty")`.
 */
class CalculateHandler
    @Inject
    constructor(
        private val parser: SpanishExpressionParser,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "calculate"

        @Suppress("ReturnCount")
        override suspend fun handle(call: FunctionCall): HandlerResult {
            val expression = (call.params["expression"] as? String).orEmpty().trim()
            if (expression.isEmpty()) {
                return HandlerResult.Failed(
                    speech = context.getString(R.string.copy_calc_failed),
                    reason = CurroError.Calculation(expression, "empty"),
                )
            }

            val parsed =
                parser.parse(expression).getOrElse {
                    return HandlerResult.Failed(
                        speech = context.getString(R.string.copy_calc_failed),
                        reason = CurroError.Calculation(expression, "parse"),
                    )
                }

            val value =
                parsed.evaluate().getOrElse { e ->
                    return when (e.message) {
                        "div_zero" ->
                            HandlerResult.Failed(
                                speech = context.getString(R.string.copy_calc_div_zero),
                                reason = CurroError.Calculation(expression, "div_zero"),
                            )
                        "overflow" ->
                            HandlerResult.Failed(
                                speech = context.getString(R.string.copy_calc_failed),
                                reason = CurroError.Calculation(expression, "overflow"),
                            )
                        else ->
                            HandlerResult.Failed(
                                speech = context.getString(R.string.copy_calc_failed),
                                reason = CurroError.Calculation(expression, e.message ?: "eval"),
                            )
                    }
                }

            // Capitalise the first character of the expression for the spoken phrase.
            val phrase = expression.replaceFirstChar { it.titlecase(Locale("es")) }
            val resultWords = intToSpanishWords(value)
            return HandlerResult.Spoken(context.getString(R.string.copy_calc_result, phrase, resultWords))
        }
    }
