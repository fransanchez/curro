package com.curro.app.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Checks whether the user has granted `READ_CONTACTS` to Curro (US-033 / SF-4.9).
 *
 * The runtime permission request itself is wired by SF-4.10 (`call_contact` handler) on
 * the first call attempt — NEVER at install. This gate is a synchronous check; it does
 * NOT trigger a system dialog.
 *
 * Denial maps to `CurroError.ReadContactsPermissionMissing` →
 * `copy_perm_missing_contacts` in the `call_contact` handler.
 */
interface ReadContactsPermissionGate {
    /** True iff `android.permission.READ_CONTACTS` is granted to Curro. */
    fun isGranted(): Boolean
}

class SystemReadContactsPermissionGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ReadContactsPermissionGate {
        override fun isGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
    }
