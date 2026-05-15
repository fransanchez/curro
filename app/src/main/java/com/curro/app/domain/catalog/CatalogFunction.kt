package com.curro.app.domain.catalog

/**
 * One catalog function (spec §5). Mirrors the `function-catalog` skill
 * machine-readable shape.
 *
 * The catalog lives in **three places that must stay in sync** (skill
 * "Rules" §1): the `function-catalog` skill ⇄ `docs/curro-spec-v1.0.md` §5 ⇄
 * this file. `/add-function <name>` is the tool that keeps them aligned.
 */
data class CatalogFunction(
    /** snake_case, e.g. `"tell_time"`. */
    val name: String,
    /** One terse Spanish sentence — the model sees this. */
    val description: String,
    /** Declared parameters; order is documentation, not load-bearing. */
    val params: List<CatalogParam>,
    /** Confirmation policy (spec §4.3). */
    val needsConfirmation: NeedsConfirmation,
    /** 4–6 short Spanish phrases that exemplify how the user phrases this action. */
    val voiceExamples: List<String>,
)

/**
 * One declared parameter of a [CatalogFunction]. The validator (US-022) reads
 * [type] + [required] when checking model output; the prompt builder (US-021)
 * renders all four fields into the action signature line.
 */
data class CatalogParam(
    /** snake_case. */
    val name: String,
    val type: ParamType,
    val required: Boolean,
    /** One Spanish phrase — the model sees this. */
    val description: String,
    /** Default value as a JSON literal; `null` for required params. */
    val defaultValue: String? = null,
)

/**
 * The set of param types FunctionGemma is allowed to return. Kept small on
 * purpose — adding a new variant has cascading effects on the validator and
 * the prompt's signature line, so the choice is deliberate.
 */
sealed interface ParamType {
    /** A free-form string. */
    data object Str : ParamType

    /** A 32-bit integer. */
    data object Int : ParamType

    /** A string restricted to one of the declared values. */
    data class Enum(val values: List<String>) : ParamType
}

/**
 * Confirmation policy for a [CatalogFunction], per spec §4.3.
 *
 * - [NO]: execute always.
 * - [YES]: always confirm before executing.
 * - [CONDITIONAL]: graded on confidence — ≥ 0.85 execute, 0.60–0.85 confirm,
 *   < 0.60 clarify. The graded policy itself lives in Phase 6's
 *   `ConfidencePolicy`; this enum is the *catalog's* declaration of intent.
 */
enum class NeedsConfirmation {
    /** Execute always; no confirmation. */
    NO,

    /** Always confirm. */
    YES,

    /**
     * Confirmation depends on confidence (spec §4.3): ≥ 0.85 execute;
     * 0.60–0.85 confirm; < 0.60 clarify. Always escalates to mandatory
     * confirmation on ambiguity / irreversible cost / "always confirm" toggle.
     */
    CONDITIONAL,
}
