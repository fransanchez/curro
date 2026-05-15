package com.curro.app.domain.model

/**
 * Minimal context surfaced to FunctionGemma alongside the utterance and the
 * catalog (spec §4.3, `on-device-llm` / `function-catalog` skills).
 *
 * Kept small on purpose — every token competes with accuracy on a 270M model.
 *
 * Phase 3: [unreadMessagesSummary] and [knownAliases] are always empty (the
 * WhatsApp cache and the alias DB don't ship until Phase 4 / Phase 7). The
 * fields exist now so the prompt builder's golden tests pin the final shape;
 * later phases fill them.
 */
data class PromptContext(
    /** Local time in ISO-8601 with no offset, e.g. `2026-05-15T22:36:00`. */
    val nowIso: String,
    /** Short, count-and-senders only — never message bodies. Empty in Phase 3. */
    val unreadMessagesSummary: String,
    /** One per alias, e.g. `"mi hija → Lucía Ruiz"`. Empty in Phase 3. */
    val knownAliases: List<String>,
)
