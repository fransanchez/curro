package com.curro.app.data.permissions

import android.Manifest
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.curro.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot reader for every Curro-relevant permission (US-059 / SF-8.10).
 *
 * Called from [DiagnosticsViewModel] on each [ON_RESUME] refresh. All reads are O(1) —
 * [ContextCompat.checkSelfPermission] and [NotificationAccessGate.isGranted] are
 * synchronous and do not touch disk or IPC beyond a single Binder call.
 *
 * No new permissions are required: reading your own permission state does not require
 * any runtime permission.
 */
@Singleton
class GrantedPermissionsReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val notificationGate: NotificationAccessGate,
    ) {
        /**
         * Returns the current granted/denied state for every Curro permission.
         *
         * The list order matches the spec §10 table (record audio, contacts, call, notifications,
         * notification-listener, phone state, answer calls).
         */
        fun snapshot(): List<PermissionInfo> {
            val pm = android.content.pm.PackageManager.PERMISSION_GRANTED

            fun granted(perm: String): Boolean = ContextCompat.checkSelfPermission(context, perm) == pm

            return listOf(
                PermissionInfo(
                    permission = Manifest.permission.RECORD_AUDIO,
                    labelResId = R.string.copy_config_diagnostics_permission_record_audio,
                    isGranted = granted(Manifest.permission.RECORD_AUDIO),
                ),
                PermissionInfo(
                    permission = Manifest.permission.READ_CONTACTS,
                    labelResId = R.string.copy_config_diagnostics_permission_read_contacts,
                    isGranted = granted(Manifest.permission.READ_CONTACTS),
                ),
                PermissionInfo(
                    permission = Manifest.permission.CALL_PHONE,
                    labelResId = R.string.copy_config_diagnostics_permission_call_phone,
                    isGranted = granted(Manifest.permission.CALL_PHONE),
                ),
                PermissionInfo(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    labelResId = R.string.copy_config_diagnostics_permission_post_notifications,
                    isGranted = granted(Manifest.permission.POST_NOTIFICATIONS),
                ),
                PermissionInfo(
                    permission = PERMISSION_KEY_NOTIFICATION_LISTENER,
                    labelResId = R.string.copy_config_diagnostics_permission_notification_listener,
                    isGranted = notificationGate.isGranted(),
                ),
                PermissionInfo(
                    permission = Manifest.permission.READ_PHONE_STATE,
                    labelResId = R.string.copy_config_diagnostics_permission_read_phone_state,
                    isGranted = granted(Manifest.permission.READ_PHONE_STATE),
                ),
                PermissionInfo(
                    permission = Manifest.permission.ANSWER_PHONE_CALLS,
                    labelResId = R.string.copy_config_diagnostics_permission_answer_calls,
                    isGranted = granted(Manifest.permission.ANSWER_PHONE_CALLS),
                ),
            )
        }

        companion object {
            /**
             * Synthetic permission key for the notification-listener access.
             * `BIND_NOTIFICATION_LISTENER_SERVICE` is a signature-level permission;
             * the actual access gate uses [NotificationManagerCompat.getEnabledListenerPackages].
             */
            const val PERMISSION_KEY_NOTIFICATION_LISTENER = "NOTIFICATION_LISTENER"
        }
    }

/**
 * Single row in the diagnostics permissions section.
 *
 * @param permission Android Manifest permission string, or
 *   [GrantedPermissionsReader.PERMISSION_KEY_NOTIFICATION_LISTENER] for the listener access.
 * @param labelResId String resource for the human-readable label (Spanish).
 * @param isGranted Current granted/denied state.
 */
data class PermissionInfo(
    val permission: String,
    @StringRes val labelResId: Int,
    val isGranted: Boolean,
)
