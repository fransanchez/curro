package com.curro.app.data.apps

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import androidx.lifecycle.Lifecycle
import com.curro.app.R
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [StaticFavoriteAppsRepositoryImpl] (SF-1.4 / US-012).
 *
 * Uses MockK to fake [PackageManager] and [Context]. The [Lifecycle] seam is replaced
 * with a flow that never emits (the test only exercises the `onStart` emission).
 *
 * Covers:
 * - The flow emits 4 entries immediately on subscription (onStart).
 * - WhatsApp entry has the correct label resource ID.
 * - Installed package → `resolvedPackage` non-null, `icon` non-null.
 * - Not-installed package → `resolvedPackage` null, `icon` null.
 */
@ExperimentalCoroutinesApi
@DisplayName("StaticFavoriteAppsRepositoryImpl")
class StaticFavoriteAppsRepositoryImplTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private val mockPackageManager: PackageManager = mockk(relaxed = true)
    private val mockContext: Context = mockk()

    /**
     * A lifecycle that never resumes after the initial observation setup —
     * it stays in CREATED and never fires ON_RESUME. The `onStart` emission
     * (which always fires) is what our tests observe.
     */
    private val neverResumeLifecycle: Lifecycle =
        mockk<Lifecycle>().also { lifecycle ->
            every { lifecycle.addObserver(any()) } returns Unit
            every { lifecycle.removeObserver(any()) } returns Unit
        }

    private lateinit var repository: StaticFavoriteAppsRepositoryImpl

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockContext.packageManager } returns mockPackageManager

        // Default: all packages throw (not installed).
        every {
            @Suppress("DEPRECATION")
            mockPackageManager.getPackageInfo(any<String>(), any<Int>())
        } throws PackageManager.NameNotFoundException()

        // Default: resolveActivity returns null.
        every {
            @Suppress("DEPRECATION")
            mockPackageManager.resolveActivity(any(), any<Int>())
        } returns null

        repository =
            StaticFavoriteAppsRepositoryImpl(
                context = mockContext,
                ioDispatcher = testDispatcher,
            ).apply {
                lifecycleSource = { neverResumeLifecycle }
            }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observeFavorites emits 4 entries immediately on subscription`() =
        runTest {
            val favorites = repository.observeFavorites().first()
            assertEquals(4, favorites.size, "Expected exactly 4 favourite entries")
        }

    @Test
    fun `WhatsApp entry is present with correct label resource`() =
        runTest {
            val favorites = repository.observeFavorites().first()
            val whatsapp = favorites.find { it.id == "whatsapp" }
            assertNotNull(whatsapp, "WhatsApp entry must exist")
            assertEquals(R.string.copy_app_label_whatsapp, whatsapp!!.labelResId)
        }

    @Test
    fun `installed WhatsApp has non-null resolvedPackage and icon`() =
        runTest {
            // Simulate WhatsApp installed and icon available.
            @Suppress("DEPRECATION")
            every {
                mockPackageManager.getPackageInfo("com.whatsapp", 0)
            } returns PackageInfo()
            every {
                mockPackageManager.getApplicationIcon("com.whatsapp")
            } returns ColorDrawable(0)

            val favorites = repository.observeFavorites().first()
            val whatsapp = favorites.find { it.id == "whatsapp" }!!
            assertNotNull(whatsapp.resolvedPackage, "resolvedPackage must be non-null when installed")
            assertNotNull(whatsapp.icon, "icon must be non-null when installed")
            assertEquals("com.whatsapp", whatsapp.resolvedPackage)
        }

    @Test
    fun `not-installed app has null resolvedPackage and null icon`() =
        runTest {
            // Default setup: getPackageInfo throws → not installed.
            val favorites = repository.observeFavorites().first()
            val whatsapp = favorites.find { it.id == "whatsapp" }!!
            assertNull(whatsapp.resolvedPackage, "resolvedPackage must be null when not installed")
            assertNull(whatsapp.icon, "icon must be null when not installed")
        }

    @Test
    fun `all four entries have distinct ids`() =
        runTest {
            val favorites = repository.observeFavorites().first()
            val ids = favorites.map { it.id }.toSet()
            assertEquals(4, ids.size, "All 4 entries must have distinct ids")
        }
}
