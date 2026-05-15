package com.curro.app.domain.model

/**
 * The result of running FunctionGemma + the validator (spec §4.3, flow 7).
 *
 * Pure data. The validator (`data/ml/FunctionCallValidator`, US-022)
 * constructs this; handlers consume it; the coordinator carries it from one
 * to the other.
 *
 * Phase-3 invariants (validated by [com.curro.app.data.ml.FunctionCallValidator]):
 * - [action] is a name in the current phase's catalog (Fase 1 in Phase 3).
 * - [params] keys are exactly the declared param names; values are typed —
 *   `String` for `ParamType.Str` and `ParamType.Enum`, `Int` for `ParamType.Int`.
 *   No `null` values; absent optional params are simply not in the map.
 * - [confidence] is in `[0.0f, 1.0f]` and not NaN.
 */
data class FunctionCall(
    /** snake_case; guaranteed to be a name in the current phase's catalog. */
    val action: String,
    /** Parsed and type-validated parameter map. Never null; empty if no params declared. */
    val params: Map<String, Any>,
    /** Model's confidence, validated to be in `[0.0f, 1.0f]`. */
    val confidence: Float,
)
