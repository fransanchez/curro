package com.curro.app.data.local

/**
 * Provenance of a stored [ContactAliasEntity] (spec §7).
 *
 * - [LEARNED]: persisted by the alias-learning subflow (SF-7.3, spec flow 4).
 *   The user said "mi hija", picked Lucía from the candidate list, Curro
 *   wrote the row.
 * - [EXPLICIT]: Fran pre-loaded the alias via the Phase-8 config menu
 *   (SF-8.2). Hand-typed mapping.
 * - [SUGGESTED]: deferred. Future Phase-8 onboarding wizard suggests aliases
 *   based on contact frequency.
 */
enum class AliasSource { LEARNED, EXPLICIT, SUGGESTED }
