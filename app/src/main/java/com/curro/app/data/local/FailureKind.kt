package com.curro.app.data.local

/**
 * The three failure paths the SF-3.6 + SF-7.5 flow distinguishes (spec §6 flow 7).
 *
 * - [INVALID_OUTPUT]: FunctionGemma produced JSON that failed the validator's
 *   JSON-Schema check (spec flow 7 — "model output not valid"). No retry.
 * - [UNKNOWN_FUNCTION]: the JSON was valid but the `action` is not in the
 *   current phase's catalog (e.g. user asks for a Fase-2 function in Fase 1).
 * - [HANDLER_ERROR]: the dispatched handler threw OR returned
 *   [com.curro.app.domain.handler.HandlerResult.Failed].
 *
 * Stored in [FailedCommandEntity.kind]; surfaced to Fran in the Phase-8 fail-log UI.
 */
enum class FailureKind { INVALID_OUTPUT, UNKNOWN_FUNCTION, HANDLER_ERROR }
