package com.curro.app.assistant

import com.curro.app.domain.catalog.NeedsConfirmation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inputs the [ConfidencePolicy] needs to produce a decision (SF-6.1 / US-041).
 *
 * Pure primitives so the policy is deterministic and trivial to unit-test (no
 * Flows, no I/O). The coordinator builds this once per turn: it reads the
 * thresholds and the always-confirm flag from
 * [com.curro.app.domain.repository.SettingsRepository] via `.first()`, then
 * calls [ConfidencePolicy.decide].
 *
 * @property needsConfirmation the catalog function's confirmation policy.
 * @property confidence FunctionGemma's `confidence ∈ [0, 1]`.
 * @property isAmbiguous SF-6.1 always passes `false`; SF-6.3 wires the
 *   ambiguity signal for the disambiguation flow.
 * @property alwaysConfirmToggle SF-6.1 always passes `false`; SF-6.4 wires the
 *   real DataStore-backed flag.
 * @property executeThreshold read from settings; default `0.85f`.
 * @property confirmThreshold read from settings; default `0.60f`.
 */
data class PolicyInputs(
    val needsConfirmation: NeedsConfirmation,
    val confidence: Float,
    val isAmbiguous: Boolean,
    val alwaysConfirmToggle: Boolean,
    val executeThreshold: Float,
    val confirmThreshold: Float,
)

/**
 * One of the three outcomes [ConfidencePolicy.decide] can produce.
 *
 * - [Execute] — proceed to dispatch the handler.
 * - [Confirm] — emit `FunctionCallReady(needsConfirmation = true)` and land in
 *   `Confirming`. SF-6.2 wires the SÍ/NO overlay + the 10-s timer.
 * - [Clarify] — confidence is below `confirmThreshold`; speak the clarify line
 *   and land in `ErrorRecovery(message, failureCount = 0)` so the STT-failure
 *   counter (SF-5.4) is not touched.
 */
enum class ConfidenceDecision { Execute, Confirm, Clarify }

/**
 * Spec §4.3 — the confidence-graded confirmation policy.
 *
 * Precedence (top → bottom):
 *   1. Ambiguous param → `Confirm` (always-escalate case #1).
 *   2. `needs_confirmation = YES` → `Confirm`.
 *   3. `confidence < confirmThreshold` → `Clarify`. Applies to NO and
 *      CONDITIONAL alike — low confidence means the model probably picked the
 *      wrong action, irrespective of whether the action itself is reversible.
 *   4. `needs_confirmation = NO` → `Execute`.
 *   5. CONDITIONAL + `alwaysConfirmToggle = true` → `Confirm` (always-escalate
 *      case #3).
 *   6. CONDITIONAL + `confidence ≥ executeThreshold` → `Execute`.
 *   7. CONDITIONAL + `confidence ∈ [confirmThreshold, executeThreshold)` →
 *      `Confirm`.
 *
 * Notes on always-escalate cases (`function-catalog` skill):
 *  - Case #1 (ambiguity) is enforced at step 1.
 *  - Case #2 (irreversible cost) is encoded in the catalog as `YES`; no
 *    separate flag is needed in [PolicyInputs]. The only Phase-1 function
 *    affected would be Phase-2's `send_whatsapp_reply`.
 *  - Case #3 (always-confirm toggle) is enforced at step 5 — pinned: the
 *    toggle does NOT override the clarify branch (step 3 wins; a model that
 *    isn't sure can't be confirmed meaningfully — spec §4.3).
 */
@Singleton
class ConfidencePolicy
    @Inject
    constructor() {
        fun decide(inputs: PolicyInputs): ConfidenceDecision =
            when {
                inputs.isAmbiguous -> ConfidenceDecision.Confirm
                inputs.needsConfirmation == NeedsConfirmation.YES -> ConfidenceDecision.Confirm
                inputs.confidence < inputs.confirmThreshold -> ConfidenceDecision.Clarify
                inputs.needsConfirmation == NeedsConfirmation.NO -> ConfidenceDecision.Execute
                // CONDITIONAL from here on.
                inputs.alwaysConfirmToggle -> ConfidenceDecision.Confirm
                inputs.confidence >= inputs.executeThreshold -> ConfidenceDecision.Execute
                else -> ConfidenceDecision.Confirm
            }
    }
