package com.curro.app.domain.alias

/**
 * The curated set of Spanish relational/role phrases that, when present in a
 * `call_contact` spoken `contact` param AND not yet mapped to an alias, trigger
 * the alias-learning subflow (SF-7.3, spec §7 + flow 4, `local-data` rule 3).
 *
 * Every entry is the **normalised** form: lowercase, accents stripped (via the
 * shared [com.curro.app.data.apps.curroNormalize] helper), single internal
 * spaces. The membership check at the call site is:
 *
 * ```
 * val normalisedQuery = rawQuery.trim().lowercase().curroNormalize()
 * if (normalisedQuery in RelationalTerms.all) { ... }
 * ```
 *
 * **Adding a term**: PR review. Two rules:
 *  1. The term must be a relational/role phrase a user would say in place of a
 *     proper name (NOT a name itself — "Pepito" is NOT a relational term).
 *  2. The term must be in its normalised form (lowercase, no accents).
 *
 * If the user says a term that's not in this set AND there are multiple
 * matches, they enter the regular SF-6.3 disambig (NOT the learning flow) —
 * that's correct: the user isn't teaching Curro who that person is; they
 * just want to pick one of N matches for this call.
 */
object RelationalTerms {
    val all: Set<String> =
        setOf(
            // Family (26)
            "mi hija", "mi hijo",
            "mi nieta", "mi nieto",
            "mi mujer", "mi marido", "mi esposa", "mi esposo",
            "mi madre", "mi padre", "mama", "papa",
            "mi hermana", "mi hermano",
            "mi suegra", "mi suegro",
            "mi yerno", "mi nuera",
            "mi tia", "mi tio",
            "mi prima", "mi primo",
            "mi sobrina", "mi sobrino",
            "mi cunada", "mi cunado",
            // Roles (14)
            "el medico", "la medico", "la medica",
            "la enfermera", "el enfermero",
            "el cura",
            "el dentista", "la dentista",
            "la farmaceutica", "el farmaceutico",
            "la del banco", "el del banco",
            "el abogado", "la abogada",
        )
}
