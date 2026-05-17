package com.curro.app.data.telephony

import app.cash.turbine.test
import com.curro.app.R
import com.curro.app.presentation.launcher.LauncherSideEffect
import com.curro.app.presentation.launcher.LauncherSideEffectBus
import com.curro.app.util.FakeIncomingCallModeController
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [IncomingCallModeToggleHandler] (SF-8.7 / US-056).
 *
 * The handler is a stateless orchestrator with two collaborators — the
 * [IncomingCallModeController] (faked) and the [LauncherSideEffectBus] (real
 * — it's a thin `@Singleton MutableSharedFlow` wrapper, no behaviour worth
 * mocking).
 *
 * Four cases per the brief:
 *   1. `handle(enable = true)` → publishes [LauncherSideEffect.RequestPhonePermissions].
 *   2. `handle(enable = false)` → calls [IncomingCallModeController.disable]
 *      immediately (no permission flow).
 *   3. `onPermissionResult(grantedAll = true)` → calls [IncomingCallModeController.enable].
 *   4. `onPermissionResult(grantedAll = false)` → publishes a
 *      [LauncherSideEffect.ShowToast] with [R.string.copy_incoming_call_perm_needed].
 */
@DisplayName("IncomingCallModeToggleHandler (SF-8.7)")
class IncomingCallModeToggleHandlerTest {
    private lateinit var controller: FakeIncomingCallModeController
    private lateinit var bus: LauncherSideEffectBus
    private lateinit var handler: IncomingCallModeToggleHandler

    @BeforeEach
    fun setUp() {
        controller = FakeIncomingCallModeController()
        bus = LauncherSideEffectBus()
        handler = IncomingCallModeToggleHandler(controller, bus)
    }

    @Test
    fun `handle enable publishes RequestPhonePermissions on the bus`() =
        runTest {
            bus.effects.test {
                handler.handle(enable = true)
                val emitted = awaitItem()
                assertEquals(LauncherSideEffect.RequestPhonePermissions, emitted)
                // Controller is NOT touched until the permission result comes back.
                assertTrue(controller.transitions.isEmpty())
                assertFalse(controller.isComponentEnabled())
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `handle disable calls controller disable directly without touching the bus`() =
        runTest {
            // Start in the ON state so disable has something to flip off.
            controller.enable()
            handler.handle(enable = false)
            assertEquals(
                listOf(
                    FakeIncomingCallModeController.Transition.Enable,
                    FakeIncomingCallModeController.Transition.Disable,
                ),
                controller.transitions,
            )
            assertFalse(controller.isComponentEnabled())
        }

    @Test
    fun `onPermissionResult grantedAll true calls controller enable`() =
        runTest {
            handler.onPermissionResult(grantedAll = true)
            assertTrue(controller.isComponentEnabled())
            assertEquals(
                listOf(FakeIncomingCallModeController.Transition.Enable),
                controller.transitions,
            )
        }

    @Test
    fun `onPermissionResult grantedAll false publishes ShowToast with perm-needed copy`() =
        runTest {
            bus.effects.test {
                handler.onPermissionResult(grantedAll = false)
                val emitted = awaitItem()
                assertTrue(emitted is LauncherSideEffect.ShowToast)
                assertEquals(
                    R.string.copy_incoming_call_perm_needed,
                    (emitted as LauncherSideEffect.ShowToast).messageResId,
                )
                // Controller is NOT enabled.
                assertFalse(controller.isComponentEnabled())
                assertTrue(controller.transitions.isEmpty())
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `onPermissionResult grantedAll true is idempotent across multiple calls`() =
        runTest {
            handler.onPermissionResult(grantedAll = true)
            handler.onPermissionResult(grantedAll = true)
            // Both calls produce an enable; the controller stays enabled.
            assertEquals(
                listOf(
                    FakeIncomingCallModeController.Transition.Enable,
                    FakeIncomingCallModeController.Transition.Enable,
                ),
                controller.transitions,
            )
            assertTrue(controller.isComponentEnabled())
        }
}
