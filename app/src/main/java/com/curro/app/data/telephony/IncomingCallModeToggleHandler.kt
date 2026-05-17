package com.curro.app.data.telephony

import com.curro.app.R
import com.curro.app.presentation.launcher.LauncherSideEffect
import com.curro.app.presentation.launcher.LauncherSideEffectBus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SF-8.7 (US-056) — orchestrates the toggle-flip flow for the incoming-call
 * assistant mode.
 *
 * The config menu's switch never calls [IncomingCallModeController] directly.
 * It routes through this handler, which decides whether to request the three
 * telephony permissions (enable path) or call the controller immediately
 * (disable path). The handler is stateless — no UI state, no ViewModel.
 *
 * **Enable path** ([handle] with `enable = true`):
 *   1. Publish [LauncherSideEffect.RequestPhonePermissions] to the
 *      [LauncherSideEffectBus]. The launcher screen (or MainActivity) collects
 *      it and fires an [androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions]
 *      for `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, and `MANAGE_OWN_CALLS`.
 *   2. The Activity's callback re-enters this handler via
 *      [onPermissionResult]. If all three are granted, the controller is
 *      enabled. If any is denied, the toggle stays OFF and a toast surfaces
 *      [R.string.copy_incoming_call_perm_needed].
 *
 * **Disable path** ([handle] with `enable = false`):
 *   - Call [IncomingCallModeController.disable] immediately. No permission
 *     flow is needed — revoking permissions is user-hostile and not what the
 *     toggle should do.
 *
 * The state is observable via [com.curro.app.domain.repository.SettingsRepository.incomingCallModeEnabled];
 * the controller is the single write-path.
 */
@Singleton
class IncomingCallModeToggleHandler
    @Inject
    constructor(
        private val controller: IncomingCallModeController,
        private val sideEffectBus: LauncherSideEffectBus,
    ) {
        /**
         * Entry point from the config menu when Fran flips the switch.
         *
         * - `enable = true` → publishes [LauncherSideEffect.RequestPhonePermissions]
         *   so the Activity launches the system permission dialog.
         * - `enable = false` → calls [IncomingCallModeController.disable]
         *   directly (no permission flow needed for the OFF path).
         */
        suspend fun handle(enable: Boolean) {
            if (enable) {
                sideEffectBus.emit(LauncherSideEffect.RequestPhonePermissions)
            } else {
                controller.disable()
            }
        }

        /**
         * Re-entered after the system permission dialog resolves.
         *
         * `grantedAll = true` → flip the component ON and persist the setting.
         * `grantedAll = false` → leave the setting OFF (the toggle will snap
         * back when the UI re-collects from the settings flow); surface a
         * toast asking Fran to grant the permissions.
         */
        suspend fun onPermissionResult(grantedAll: Boolean) {
            if (grantedAll) {
                controller.enable()
            } else {
                sideEffectBus.emit(
                    LauncherSideEffect.ShowToast(R.string.copy_incoming_call_perm_needed),
                )
            }
        }
    }
