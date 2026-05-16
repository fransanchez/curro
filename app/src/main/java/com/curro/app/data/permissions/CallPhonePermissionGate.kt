package com.curro.app.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Checks whether the user has granted `CALL_PHONE` to Curro (US-034 / SF-4.10).
 *
 * The runtime permission request itself is wired by the `LauncherViewModel` on the first
 * place-call attempt — NEVER at install. This gate is a synchronous check; it does
 * NOT trigger a system dialog.
 *
 * Denial maps to `CurroError.PermissionDenied` → `copy_perm_missing_calls` in the
 * `call_contact` handler. Per the PM decision, `CurroError.PermissionDenied` is reused
 * for CALL_PHONE (no separate `CallPermissionMissing` variant).
 */
interface CallPhonePermissionGate {
    /** True iff `android.permission.CALL_PHONE` is granted to Curro. */
    fun isGranted(): Boolean
}

class SystemCallPhonePermissionGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CallPhonePermissionGate {
        override fun isGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
    }
