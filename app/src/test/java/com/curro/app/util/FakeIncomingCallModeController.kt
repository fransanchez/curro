package com.curro.app.util

import com.curro.app.data.telephony.IncomingCallModeController

/**
 * In-memory [IncomingCallModeController] fake for JVM unit tests (SF-8.7 / US-056).
 *
 * Records `enable()` / `disable()` invocations in [transitions] in call order,
 * and exposes [isEnabled] for the `isComponentEnabled()` probe. The
 * `FakeSettingsRepository` is wired separately by the toggle-handler test —
 * this fake does NOT touch any settings flow (it stays focused on the
 * structural component flip).
 *
 * Use in:
 *   - `IncomingCallModeToggleHandlerTest` — assert handler routes enable/disable
 *     and `onPermissionResult` correctly.
 *   - `ConfigViewModelTest` — assert that flipping the SF-8.7 toggle reaches
 *     the controller (it routes through the handler).
 *
 * The PackageManager-backed controller is tested separately in
 * `PackageManagerIncomingCallModeControllerTest` (Robolectric).
 */
class FakeIncomingCallModeController : IncomingCallModeController {
    /** Ordered log of `enable` / `disable` calls — assertable by tests. */
    val transitions: MutableList<Transition> = mutableListOf()

    /** Mirror of the last call; defaults to OFF. */
    var isEnabled: Boolean = false
        private set

    override suspend fun enable() {
        transitions += Transition.Enable
        isEnabled = true
    }

    override suspend fun disable() {
        transitions += Transition.Disable
        isEnabled = false
    }

    override fun isComponentEnabled(): Boolean = isEnabled

    /** A single enable / disable transition recorded by the fake. */
    sealed interface Transition {
        data object Enable : Transition

        data object Disable : Transition
    }
}
