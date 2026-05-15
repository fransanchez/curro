package com.curro.app.data.permissions

/**
 * Abstraction over a single runtime permission's "is granted?" check (SF-2.3 / US-017).
 *
 * Implementations bind to a specific permission (RECORD_AUDIO, CALL_PHONE,
 * READ_CONTACTS, …). The ViewModel injects the gate so it never imports
 * `ContextCompat.checkSelfPermission` or `Manifest.permission.*`.
 *
 * Phase 2 ships [RecordAudioPermissionGate]; future phases (call_contact,
 * read_whatsapp, …) add their own gates against this interface.
 */
interface PermissionGate {
    /** Returns true iff the corresponding runtime permission is currently granted. */
    fun isGranted(): Boolean
}
