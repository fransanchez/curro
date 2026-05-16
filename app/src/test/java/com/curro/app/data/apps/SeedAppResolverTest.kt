package com.curro.app.data.apps

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import com.curro.app.R
import com.curro.app.domain.model.AppLabel
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SeedAppResolver] (SF-7.4 / US-048).
 *
 * Extracted from the deleted [StaticFavoriteAppsRepositoryImplTest]; the logic is
 * identical — only the class under test changed.
 *
 * Uses Mockk to fake [PackageManager] and [Context]; no Robolectric needed.
 */
@DisplayName("SeedAppResolver (SF-7.4)")
class SeedAppResolverTest {
    private val mockPackageManager: PackageManager = mockk(relaxed = true)
    private val mockContext: Context = mockk()

    private lateinit var resolver: SeedAppResolver

    @BeforeEach
    fun setUp() {
        every { mockContext.packageManager } returns mockPackageManager

        // Default: all packages not installed → getPackageInfo throws.
        every {
            @Suppress("DEPRECATION")
            mockPackageManager.getPackageInfo(any<String>(), any<Int>())
        } throws PackageManager.NameNotFoundException()

        // Default: resolveActivity returns null.
        every {
            @Suppress("DEPRECATION")
            mockPackageManager.resolveActivity(any(), any<Int>())
        } returns null

        resolver = SeedAppResolver(context = mockContext)
    }

    // ── 1. seedFavorites emits 4 entries ─────────────────────────────────────

    @Test
    fun `seedFavorites returns exactly 4 entries`() {
        val seeds = resolver.seedFavorites()
        assertEquals(4, seeds.size, "Expected exactly 4 seed entries")
    }

    // ── 2. WhatsApp entry has the correct label resource ─────────────────────

    @Test
    fun `whatsapp entry has AppLabel Resource with correct resId`() {
        val seeds = resolver.seedFavorites()
        val whatsapp = seeds.find { it.id == "whatsapp" }
        assertNotNull(whatsapp, "WhatsApp entry must exist")
        val label = whatsapp!!.label
        assertTrue(label is AppLabel.Resource, "WhatsApp label must be AppLabel.Resource")
        assertEquals(R.string.copy_app_label_whatsapp, (label as AppLabel.Resource).resId)
    }

    // ── 3. Installed WhatsApp has non-null resolvedPackage and icon ──────────

    @Test
    fun `installed WhatsApp has non-null resolvedPackage and icon`() {
        @Suppress("DEPRECATION")
        every {
            mockPackageManager.getPackageInfo("com.whatsapp", 0)
        } returns PackageInfo()
        every {
            mockPackageManager.getApplicationIcon("com.whatsapp")
        } returns ColorDrawable(0)

        val seeds = resolver.seedFavorites()
        val whatsapp = seeds.find { it.id == "whatsapp" }!!
        assertNotNull(whatsapp.resolvedPackage, "resolvedPackage must be non-null when installed")
        assertNotNull(whatsapp.icon, "icon must be non-null when installed")
        assertEquals("com.whatsapp", whatsapp.resolvedPackage)
    }

    // ── 4. Not-installed app has null resolvedPackage and icon ───────────────

    @Test
    fun `not-installed app has null resolvedPackage and null icon`() {
        val seeds = resolver.seedFavorites()
        val whatsapp = seeds.find { it.id == "whatsapp" }!!
        assertNull(whatsapp.resolvedPackage, "resolvedPackage must be null when not installed")
        assertNull(whatsapp.icon, "icon must be null when not installed")
    }

    // ── 5. toFavoriteApp for unknown package returns null ────────────────────

    @Test
    fun `toFavoriteApp for non-installed package returns null`() {
        val result = resolver.toFavoriteApp("com.nonexistent")
        assertNull(result, "toFavoriteApp must return null for a non-installed package")
    }

    // ── 6. toFavoriteApp for installed package returns AppLabel.Text ─────────

    @Test
    @Suppress("DEPRECATION")
    fun `toFavoriteApp for installed package returns AppLabel Text`() {
        val pkg = "com.example.app"
        every { mockPackageManager.getPackageInfo(pkg, 0) } returns PackageInfo()
        every { mockPackageManager.getApplicationInfo(pkg, 0) } returns
            mockk {
                every { loadLabel(mockPackageManager) } returns "Mi App"
            }
        every { mockPackageManager.getApplicationLabel(any()) } returns "Mi App"
        every { mockPackageManager.getApplicationIcon(pkg) } returns ColorDrawable(0)

        val result = resolver.toFavoriteApp(pkg)
        assertNotNull(result, "toFavoriteApp must return non-null for an installed package")
        assertEquals(pkg, result!!.resolvedPackage)
        assertTrue(result.label is AppLabel.Text, "label must be AppLabel.Text for dynamic tile")
        assertEquals("Mi App", (result.label as AppLabel.Text).text)
    }

    // ── 7. All four seed entries have distinct ids ───────────────────────────

    @Test
    fun `all four seed entries have distinct ids`() {
        val seeds = resolver.seedFavorites()
        val ids = seeds.map { it.id }.toSet()
        assertEquals(4, ids.size, "All 4 seed entries must have distinct ids")
    }
}
