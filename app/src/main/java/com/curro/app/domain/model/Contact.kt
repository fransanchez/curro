package com.curro.app.domain.model

/**
 * A phone-callable contact, resolved from ContactsContract (US-033 / SF-4.9).
 *
 * [lookupKey] (ContactsContract.Contacts.LOOKUP_KEY) is stable across contact merges and
 * re-imports — per the `local-data` skill, always use LOOKUP_KEY, never _ID.
 *
 * Phase 7 (alias-learning subsystem, spec §7) stores [lookupKey] in the alias table.
 * [displayName] is the user-visible name at lookup time, kept for TTS confirmation phrases.
 *
 * Privacy: [displayName] and [phoneNumbers] are never logged or surfaced to telemetry.
 */
data class Contact(
    /** Stable across contact merges — the right key per `local-data` skill. Never use _ID. */
    val lookupKey: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val photoUri: String?,
)
