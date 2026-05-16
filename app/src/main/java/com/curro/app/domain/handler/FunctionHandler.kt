package com.curro.app.domain.handler

import com.curro.app.domain.model.FunctionCall

/**
 * Every catalog function (spec §5, `function-catalog` skill) has exactly one
 * [FunctionHandler] that turns a validated [FunctionCall] into a [HandlerResult].
 *
 * Handlers are bound into a Hilt `Map<String, FunctionHandler>` keyed by
 * [functionName]; the [HandlerDispatcher] looks them up and dispatches.
 *
 * Implementations live in `handler/`. They must NEVER throw — every failure
 * routes through [HandlerResult.Failed] with a typed [CurroError] and a plain
 * Spanish [HandlerResult.Failed.speech] (spec §2: "Fallar de forma comprensible").
 * If a handler throws despite this contract, [HandlerDispatcher] catches it and
 * surfaces a [HandlerResult.Failed] with [CurroError.HandlerCrash].
 */
interface FunctionHandler {
    /** Catalog function name — used as the @StringKey for the Hilt multibinding. */
    val functionName: String

    suspend fun handle(call: FunctionCall): HandlerResult
}
