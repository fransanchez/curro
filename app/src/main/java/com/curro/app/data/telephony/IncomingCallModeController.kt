package com.curro.app.data.telephony

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.curro.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SF-8.7 (US-056) — single write-path for the incoming-call assistant mode toggle.
 *
 * **The non-negotiable rule** (master-plan §Risks-a): when the toggle is OFF,
 * telephony must be 100 % native. We enforce this structurally — not with a
 * runtime check — by flipping the manifest's `<service android:enabled="false">`
 * via [PackageManager.setComponentEnabledSetting]:
 *
 *   - [enable] → `COMPONENT_ENABLED_STATE_ENABLED` + persist
 *     `incomingCallModeEnabled = true` in DataStore.
 *   - [disable] → `COMPONENT_ENABLED_STATE_DISABLED` + persist
 *     `incomingCallModeEnabled = false`.
 *
 * With the component disabled, `queryIntentServices(Intent("android.telecom.InCallService"))`
 * does NOT list [CurroInCallService] and the Telecom framework binds the
 * system's native InCallService. Verified by `IncomingCallModeOffInvariantTest`.
 *
 * Interface seam (this file): the production binding is
 * [PackageManagerIncomingCallModeController]; tests inject a
 * `FakeIncomingCallModeController` that records the enable/disable transitions.
 *
 * The only caller is [IncomingCallModeToggleHandler]; the config UI never
 * touches `SettingsRepository.setIncomingCallModeEnabled` directly for this
 * key.
 */
interface IncomingCallModeController {
    /**
     * Enables the InCallService component (manifest flip) AND persists
     * `incomingCallModeEnabled = true`. Order: component, then setting.
     *
     * Safe to call when already enabled — both operations are idempotent.
     * After this returns, the Telecom framework will route the next
     * incoming-call binding through [CurroInCallService].
     */
    suspend fun enable()

    /**
     * Disables the InCallService component AND clears the setting.
     * Order: component, then setting. After this returns, telephony is
     * 100 % native (the next incoming call is bound by the system's
     * default InCallService, not Curro's).
     */
    suspend fun disable()

    /**
     * Belt-and-braces probe — reads the PackageManager's current
     * component-enabled state. Returns `true` only if the state is
     * explicitly `COMPONENT_ENABLED_STATE_ENABLED` (we never rely on
     * the default state, which would defer to the manifest's
     * `android:enabled="false"`).
     */
    fun isComponentEnabled(): Boolean
}

/**
 * Production [IncomingCallModeController] backed by the framework's
 * [PackageManager].
 *
 * **Idempotency**: both [enable] and [disable] are safe to call multiple
 * times. The PackageManager API itself is idempotent for a no-op transition
 * (ENABLED → ENABLED) and the DataStore setter writes the same value.
 *
 * **Order matters**: [enable] flips the component FIRST, then persists the
 * setting. This guarantees the component is structurally ready before the
 * setting flow signals ON to any observer. [disable] reverses in the same
 * order — component first, setting second — so the OFF invariant is structural
 * the moment the setting reads false.
 *
 * `DONT_KILL_APP` keeps the app process alive across the flip — the config
 * menu UI stays responsive.
 */
@Singleton
class PackageManagerIncomingCallModeController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepo: SettingsRepository,
    ) : IncomingCallModeController {
        override suspend fun enable() {
            setComponentEnabled(true)
            settingsRepo.setIncomingCallModeEnabled(true)
        }

        override suspend fun disable() {
            setComponentEnabled(false)
            settingsRepo.setIncomingCallModeEnabled(false)
        }

        override fun isComponentEnabled(): Boolean {
            val cn = ComponentName(context, CurroInCallService::class.java)
            return context.packageManager.getComponentEnabledSetting(cn) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        private fun setComponentEnabled(enabled: Boolean) {
            val cn = ComponentName(context, CurroInCallService::class.java)
            val newState =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            context.packageManager.setComponentEnabledSetting(
                cn,
                newState,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
