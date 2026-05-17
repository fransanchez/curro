package com.curro.app.data.telephony

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.curro.app.assistant.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [PackageManagerIncomingCallModeController] (SF-8.7 / US-056).
 *
 * Uses the real `PackageManager` exposed by [ApplicationProvider] — Robolectric
 * stubs it with a [org.robolectric.shadows.ShadowPackageManager] that honours
 * `setComponentEnabledSetting` calls. The structural OFF-state invariant
 * verification (queryIntentServices does not list the component) lives in the
 * instrumented `IncomingCallModeOffInvariantTest`; here we focus on the
 * controller's behaviour: the right `setComponentEnabledSetting` call is made,
 * the setting is persisted, and the order is component-first then setting.
 *
 * JUnit 4 style here matches the other Robolectric tests in the repo (see
 * `ContactAliasDaoTest`'s @Suppress note — JUnit 5's
 * `@ExtendWith(RobolectricExtension)` does not exist in Robolectric 4.x; the
 * JUnit vintage engine runs these inside the JUnit 5 platform).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PackageManagerIncomingCallModeControllerTest {
    private lateinit var controller: PackageManagerIncomingCallModeController
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var component: ComponentName

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        settingsRepo = FakeSettingsRepository()
        controller = PackageManagerIncomingCallModeController(context, settingsRepo)
        component = ComponentName(context, CurroInCallService::class.java)
    }

    @Test
    fun `enable sets component to ENABLED and writes the setting`() =
        runBlocking {
            controller.enable()
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                app.packageManager.getComponentEnabledSetting(component),
            )
            assertTrue(settingsRepo.incomingCallModeEnabled.first())
            assertEquals(listOf(true), settingsRepo.incomingCallModeSetCalls)
        }

    @Test
    fun `disable sets component to DISABLED and writes the setting`() =
        runBlocking {
            // Start enabled so the disable transition is observable.
            controller.enable()
            controller.disable()
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                app.packageManager.getComponentEnabledSetting(component),
            )
            assertFalse(settingsRepo.incomingCallModeEnabled.first())
            assertEquals(listOf(true, false), settingsRepo.incomingCallModeSetCalls)
        }

    @Test
    fun `isComponentEnabled reflects the current PackageManager state`() =
        runBlocking {
            assertFalse(controller.isComponentEnabled())
            controller.enable()
            assertTrue(controller.isComponentEnabled())
            controller.disable()
            assertFalse(controller.isComponentEnabled())
        }

    @Test
    fun `enable is idempotent across multiple calls`() =
        runBlocking {
            controller.enable()
            controller.enable()
            controller.enable()
            // Component stays ENABLED; setting is written each time (idempotent value).
            assertTrue(controller.isComponentEnabled())
            assertEquals(listOf(true, true, true), settingsRepo.incomingCallModeSetCalls)
        }

    @Test
    fun `disable is idempotent across multiple calls`() =
        runBlocking {
            controller.disable()
            controller.disable()
            assertFalse(controller.isComponentEnabled())
            assertEquals(listOf(false, false), settingsRepo.incomingCallModeSetCalls)
        }

    @Test
    fun `enable then disable returns the component to DISABLED`() =
        runBlocking {
            controller.enable()
            assertTrue(controller.isComponentEnabled())
            controller.disable()
            assertFalse(controller.isComponentEnabled())
            // OFF is the structural invariant after disable returns.
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                app.packageManager.getComponentEnabledSetting(component),
            )
        }
}
