package com.curro.app.assistant

import com.curro.app.domain.catalog.NeedsConfirmation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * SF-6.1 (US-041) — exhaustive table of [ConfidencePolicy.decide] cases.
 *
 * The 36 cases are grouped A–G (one group per spec §4.3 precedence row + the
 * always-escalate cases + custom thresholds + defensive boundaries). Each
 * case is one `@Test` so a CI failure points at the exact rule.
 *
 * Defaults used unless overridden: `executeThreshold = 0.85f`,
 * `confirmThreshold = 0.60f`.
 */
@DisplayName("ConfidencePolicy (SF-6.1)")
@Suppress("LargeClass", "TooManyFunctions")
class ConfidencePolicyTest {
    private val policy = ConfidencePolicy()

    private fun inputs(
        nc: NeedsConfirmation,
        confidence: Float,
        ambig: Boolean = false,
        toggle: Boolean = false,
        executeThreshold: Float = 0.85f,
        confirmThreshold: Float = 0.60f,
    ): PolicyInputs =
        PolicyInputs(
            needsConfirmation = nc,
            confidence = confidence,
            isAmbiguous = ambig,
            alwaysConfirmToggle = toggle,
            executeThreshold = executeThreshold,
            confirmThreshold = confirmThreshold,
        )

    // ─────────────────────────────────────────────────────────────────────────
    // Group A — needsConfirmation = NO (6 cases)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `group_A1 — NO high confidence executes`() {
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(inputs(NeedsConfirmation.NO, confidence = 0.95f)),
        )
    }

    @Test
    fun `group_A2 — NO mid confidence executes (NO ignores execute threshold)`() {
        // NO only protects above the confirm threshold; below it, clarify wins.
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(inputs(NeedsConfirmation.NO, confidence = 0.72f)),
        )
    }

    @Test
    fun `group_A3 — NO low confidence clarifies`() {
        assertEquals(
            ConfidenceDecision.Clarify,
            policy.decide(inputs(NeedsConfirmation.NO, confidence = 0.40f)),
        )
    }

    @Test
    fun `group_A4 — NO high confidence but ambiguous confirms (ambig wins)`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(inputs(NeedsConfirmation.NO, confidence = 0.95f, ambig = true)),
        )
    }

    @Test
    fun `group_A5 — NO high confidence with toggle still executes (toggle only affects CONDITIONAL)`() {
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(inputs(NeedsConfirmation.NO, confidence = 0.95f, toggle = true)),
        )
    }

    @Test
    fun `group_A6 — NO confidence just below confirm threshold clarifies`() {
        assertEquals(
            ConfidenceDecision.Clarify,
            policy.decide(inputs(NeedsConfirmation.NO, confidence = 0.59f)),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group B — needsConfirmation = YES (4 cases — always Confirm)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `group_B7 — YES high confidence confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(inputs(NeedsConfirmation.YES, confidence = 0.95f)),
        )
    }

    @Test
    fun `group_B8 — YES low confidence still confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(inputs(NeedsConfirmation.YES, confidence = 0.40f)),
        )
    }

    @Test
    fun `group_B9 — YES ambiguous confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(inputs(NeedsConfirmation.YES, confidence = 0.95f, ambig = true)),
        )
    }

    @Test
    fun `group_B10 — YES toggle confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(inputs(NeedsConfirmation.YES, confidence = 0.95f, toggle = true)),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group C — CONDITIONAL, no ambig, no toggle (6 cases)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `group_C11 — CONDITIONAL 0_95 executes`() {
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.95f)),
        )
    }

    @Test
    fun `group_C12 — CONDITIONAL 0_85 boundary executes (exact-equal execute)`() {
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.85f)),
        )
    }

    @Test
    fun `group_C13 — CONDITIONAL 0_72 confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.72f)),
        )
    }

    @Test
    fun `group_C14 — CONDITIONAL 0_60 boundary confirms (exact-equal confirm)`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.60f)),
        )
    }

    @Test
    fun `group_C15 — CONDITIONAL 0_59 just below confirm clarifies`() {
        assertEquals(
            ConfidenceDecision.Clarify,
            policy.decide(inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.59f)),
        )
    }

    @Test
    fun `group_C16 — CONDITIONAL 0_40 clarifies`() {
        assertEquals(
            ConfidenceDecision.Clarify,
            policy.decide(inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.40f)),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group D — CONDITIONAL + ambig precedence (4 cases — ambig wins over Execute / Clarify)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `group_D17 — CONDITIONAL high confidence ambig confirms (ambig over execute)`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.95f, ambig = true),
            ),
        )
    }

    @Test
    fun `group_D18 — CONDITIONAL mid confidence ambig confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.72f, ambig = true),
            ),
        )
    }

    @Test
    fun `group_D19 — CONDITIONAL low confidence ambig confirms (ambig supersedes clarify)`() {
        // Spec §4.3 always-escalate case #1: ambiguity → Confirm regardless of confidence.
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.40f, ambig = true),
            ),
        )
    }

    @Test
    fun `group_D20 — CONDITIONAL high confidence ambig AND toggle confirms (both fire)`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.95f, ambig = true, toggle = true),
            ),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group E — CONDITIONAL + toggle precedence (4 cases)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `group_E21 — CONDITIONAL high confidence toggle confirms (toggle over execute)`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.95f, toggle = true),
            ),
        )
    }

    @Test
    fun `group_E22 — CONDITIONAL mid confidence toggle confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.72f, toggle = true),
            ),
        )
    }

    @Test
    fun `group_E23 — CONDITIONAL low confidence toggle still clarifies (clarify over toggle)`() {
        // Spec §4.3: low confidence means the model is too unsure for a confirm to be
        // meaningful — the clarify branch is more protective than the toggle.
        assertEquals(
            ConfidenceDecision.Clarify,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.40f, toggle = true),
            ),
        )
    }

    @Test
    fun `group_E24 — CONDITIONAL high confidence ambig toggle confirms (both)`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.95f, ambig = true, toggle = true),
            ),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group F — Custom thresholds (Fran tweaked them) (6 cases)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `group_F25 — CONDITIONAL 0_80 with executeThreshold lowered to 0_75 executes`() {
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(
                inputs(
                    NeedsConfirmation.CONDITIONAL,
                    confidence = 0.80f,
                    executeThreshold = 0.75f,
                    confirmThreshold = 0.50f,
                ),
            ),
        )
    }

    @Test
    fun `group_F26 — CONDITIONAL 0_70 with executeThreshold 0_75 confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(
                    NeedsConfirmation.CONDITIONAL,
                    confidence = 0.70f,
                    executeThreshold = 0.75f,
                    confirmThreshold = 0.50f,
                ),
            ),
        )
    }

    @Test
    fun `group_F27 — CONDITIONAL 0_49 with confirmThreshold 0_50 clarifies`() {
        assertEquals(
            ConfidenceDecision.Clarify,
            policy.decide(
                inputs(
                    NeedsConfirmation.CONDITIONAL,
                    confidence = 0.49f,
                    executeThreshold = 0.75f,
                    confirmThreshold = 0.50f,
                ),
            ),
        )
    }

    @Test
    fun `group_F28 — CONDITIONAL 0_95 with executeThreshold raised to 0_95 executes (exact-equal)`() {
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(
                inputs(
                    NeedsConfirmation.CONDITIONAL,
                    confidence = 0.95f,
                    executeThreshold = 0.95f,
                    confirmThreshold = 0.80f,
                ),
            ),
        )
    }

    @Test
    fun `group_F29 — CONDITIONAL 0_90 with executeThreshold raised to 0_95 confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(
                    NeedsConfirmation.CONDITIONAL,
                    confidence = 0.90f,
                    executeThreshold = 0.95f,
                    confirmThreshold = 0.80f,
                ),
            ),
        )
    }

    @Test
    fun `group_F30 — CONDITIONAL 0_79 with confirmThreshold raised to 0_80 clarifies`() {
        assertEquals(
            ConfidenceDecision.Clarify,
            policy.decide(
                inputs(
                    NeedsConfirmation.CONDITIONAL,
                    confidence = 0.79f,
                    executeThreshold = 0.95f,
                    confirmThreshold = 0.80f,
                ),
            ),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group G — Defensive boundaries (6 cases)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `group_G31 — CONDITIONAL 1_0 executes`() {
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(inputs(NeedsConfirmation.CONDITIONAL, confidence = 1.0f)),
        )
    }

    @Test
    fun `group_G32 — CONDITIONAL 0_0 clarifies`() {
        assertEquals(
            ConfidenceDecision.Clarify,
            policy.decide(inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.0f)),
        )
    }

    @Test
    fun `group_G33 — CONDITIONAL exact-equal execute threshold executes`() {
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.85f),
            ),
        )
    }

    @Test
    fun `group_G34 — CONDITIONAL exact-equal confirm threshold confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(NeedsConfirmation.CONDITIONAL, confidence = 0.60f),
            ),
        )
    }

    @Test
    fun `group_G35 — CONDITIONAL degenerate equal thresholds executes at boundary`() {
        // executeThreshold == confirmThreshold == 0.85 → 0.85 falls in execute path
        // (execute condition is `>= executeThreshold` which is true).
        assertEquals(
            ConfidenceDecision.Execute,
            policy.decide(
                inputs(
                    NeedsConfirmation.CONDITIONAL,
                    confidence = 0.85f,
                    executeThreshold = 0.85f,
                    confirmThreshold = 0.85f,
                ),
            ),
        )
    }

    @Test
    fun `group_G36 — YES with every escalator firing still just confirms`() {
        assertEquals(
            ConfidenceDecision.Confirm,
            policy.decide(
                inputs(
                    NeedsConfirmation.YES,
                    confidence = 0.0f,
                    ambig = true,
                    toggle = true,
                ),
            ),
        )
    }
}
