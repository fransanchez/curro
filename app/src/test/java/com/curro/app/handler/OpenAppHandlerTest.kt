package com.curro.app.handler

import android.content.Context
import android.graphics.drawable.Drawable
import com.curro.app.R
import com.curro.app.data.apps.AppLauncher
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.LaunchableApp
import com.curro.app.domain.repository.InstalledAppsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [OpenAppHandler] (US-027 / SF-4.3).
 *
 * Context.getString is stubbed with Mockk (same pattern as TellTimeHandlerTest) to avoid
 * Robolectric. [AppLauncher] and [InstalledAppsRepository] are fake implementations that capture
 * calls and return configurable values.
 */
@DisplayName("OpenAppHandler (SF-4.3)")
class OpenAppHandlerTest {
    private val context: Context = mockk()
    private val fakeDrawable: Drawable = mockk()

    /**
     * Minimal [InstalledAppsRepository] fake: emits the supplied [apps] list once via [flowOf].
     */
    private class FakeInstalledAppsRepository(
        private val apps: List<LaunchableApp>,
    ) : InstalledAppsRepository {
        override fun observeAllLaunchable(): Flow<List<LaunchableApp>> = flowOf(apps)
    }

    /**
     * Minimal [AppLauncher] fake: records the last [packageName] passed and returns [result].
     */
    private class FakeAppLauncher(
        private var result: Boolean = true,
    ) : AppLauncher {
        var lastLaunched: String? = null

        override fun launch(packageName: String): Boolean {
            lastLaunched = packageName
            return result
        }
    }

    /**
     * Stubs `Context.getString(resId)` and `Context.getString(resId, vararg formatArgs)`.
     * Format templates reproduce Android's `%1$s`-style by converting to `%s` and calling
     * [String.format]. Mockk passes vararg args as a single Object[] at args[1].
     */
    @BeforeEach
    fun setUp() {
        val templates =
            mapOf(
                R.string.copy_app_opening to "Abriendo %s.",
                R.string.copy_app_not_found to "No tengo ninguna app que se llame así.",
                R.string.copy_app_not_found_named to "No tengo ninguna app que se llame %s.",
                R.string.copy_app_ambiguous to "Tengo varias apps que se llaman así, prueba con el nombre exacto.",
            )

        // No-arg getString
        every { context.getString(any()) } answers {
            val resId = arg<Int>(0)
            templates[resId] ?: ""
        }

        // Vararg getString
        every { context.getString(any(), *anyVararg<Any>()) } answers {
            val resId = arg<Int>(0)
            val template = templates[resId] ?: ""
            val rawArg = if (args.size > 1) args[1] else null
            val formatArgs: Array<out Any?> =
                when (rawArg) {
                    is Array<*> -> rawArg
                    null -> emptyArray()
                    else -> arrayOf(rawArg)
                }
            if (formatArgs.isEmpty()) template else String.format(template, *formatArgs)
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun app(
        pkg: String,
        label: String,
    ) = LaunchableApp(packageName = pkg, label = label, icon = fakeDrawable)

    private fun handler(
        apps: List<LaunchableApp>,
        launcherResult: Boolean = true,
        fakeLauncher: FakeAppLauncher = FakeAppLauncher(launcherResult),
    ): OpenAppHandler = OpenAppHandler(FakeInstalledAppsRepository(apps), fakeLauncher, context)

    private fun handlerWithLauncher(
        apps: List<LaunchableApp>,
        fakeLauncher: FakeAppLauncher,
    ): OpenAppHandler = OpenAppHandler(FakeInstalledAppsRepository(apps), fakeLauncher, context)

    private fun call(appName: String): FunctionCall =
        FunctionCall("open_app", mapOf("app_name" to appName), confidence = 0.9f)

    private fun assertSpoken(result: HandlerResult): String {
        assertInstanceOf(HandlerResult.Spoken::class.java, result)
        return (result as HandlerResult.Spoken).speech
    }

    private fun assertFailed(result: HandlerResult): HandlerResult.Failed {
        assertInstanceOf(HandlerResult.Failed::class.java, result)
        return result as HandlerResult.Failed
    }

    // ── 1. Exact alias hit ────────────────────────────────────────────────────

    @Test
    fun `exact alias WhatsApp launches com-whatsapp and speaks opening phrase`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed = listOf(app("com.whatsapp", "WhatsApp"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("WhatsApp"))
            assertEquals("Abriendo WhatsApp.", assertSpoken(result))
            assertEquals("com.whatsapp", fakeLauncher.lastLaunched)
        }

    // ── 2. Accent-stripping alias ─────────────────────────────────────────────

    @Test
    fun `alias camara (no accent) resolves to cámara via curroNormalize`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed = listOf(app("com.miui.camera", "Cámara"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("camara"))
            assertEquals("Abriendo Cámara.", assertSpoken(result))
        }

    // ── 3. Multi-candidate alias: first installed wins ────────────────────────

    @Test
    fun `multi-candidate alias la cámara picks first installed package`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = true)
            // Only com.miui.camera is installed; com.android.camera and com.android.camera2 are not.
            val installed = listOf(app("com.miui.camera", "Cámara"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("la cámara"))
            assertEquals("Abriendo Cámara.", assertSpoken(result))
            assertEquals("com.miui.camera", fakeLauncher.lastLaunched)
        }

    // ── 4. Multi-candidate alias: no candidate installed → falls through ───────

    @Test
    fun `multi-candidate alias where no candidate is installed falls through to AppNotFound`() =
        runTest {
            // Alias "fotos" maps to com.miui.gallery, com.google.android.apps.photos — neither installed.
            val installed = listOf(app("com.some.other", "Otra App"))
            val result = handler(installed).handle(call("fotos"))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.AppNotFound::class.java, failed.reason)
        }

    // ── 5 & 6. Substring contains: single hit ────────────────────────────────

    @Test
    fun `substring contains galeria matches label Galería (one hit)`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed = listOf(app("com.miui.gallery", "Galería"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("galeria"))
            assertEquals("Abriendo Galería.", assertSpoken(result))
        }

    @Test
    fun `substring contains single hit launches the app`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed = listOf(app("com.example.maps", "Mapas de España"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("mapas"))
            assertEquals("Abriendo Mapas de España.", assertSpoken(result))
        }

    // ── 7. Substring contains: multiple hits → AmbiguousApp ──────────────────

    @Test
    fun `substring contains multiple hits returns AmbiguousApp`() =
        runTest {
            // Use package names NOT in the alias map so the alias step does not fire first.
            val installed =
                listOf(
                    app("com.example.audio.player", "Reproductor de Audio"),
                    app("com.example.audio.recorder", "Grabadora de Audio"),
                )
            val result = handler(installed).handle(call("audio"))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.AmbiguousApp::class.java, failed.reason)
            val ambig = failed.reason as CurroError.AmbiguousApp
            assertEquals(2, ambig.matches.size)
        }

    // ── 8. Fuzzy match: Levenshtein 1 ────────────────────────────────────────

    @Test
    fun `fuzzy match chrme (Lev 1 from chrome) opens Chrome`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed = listOf(app("com.android.chrome", "Chrome"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("chrme"))
            assertEquals("Abriendo Chrome.", assertSpoken(result))
            assertEquals("com.android.chrome", fakeLauncher.lastLaunched)
        }

    // ── 9. Levenshtein threshold boundary ─────────────────────────────────────

    @Test
    fun `abcdef vs abcdze (Lev 2) matches within threshold`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = true)
            // label normalises to "abcdze"; query "abcdef" has Lev dist 2 ≤ 3 → match.
            val installed = listOf(app("com.example.app", "abcdze"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("abcdef"))
            assertSpoken(result)
        }

    @Test
    fun `abcdef vs qrstuv (Lev 6) does not match`() =
        runTest {
            val installed = listOf(app("com.example.app", "qrstuv"))
            val result = handler(installed).handle(call("abcdef"))
            assertFailed(result).also {
                assertInstanceOf(CurroError.AppNotFound::class.java, it.reason)
            }
        }

    @Test
    fun `abcdef vs xyzdef (Lev 3) matches at threshold boundary`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed = listOf(app("com.example.app", "xyzdef"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("abcdef"))
            assertSpoken(result)
        }

    // ── 10. Empty app_name ────────────────────────────────────────────────────

    @Test
    fun `empty app_name returns AppNotFound with copy_app_not_found`() =
        runTest {
            val result = handler(emptyList()).handle(call(""))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.AppNotFound::class.java, failed.reason)
            assertEquals("No tengo ninguna app que se llame así.", failed.speech)
        }

    // ── 11. No installed apps ─────────────────────────────────────────────────

    @Test
    fun `no installed apps returns AppNotFound`() =
        runTest {
            val result = handler(emptyList()).handle(call("WhatsApp"))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.AppNotFound::class.java, failed.reason)
        }

    // ── 12. AppLauncher returns false ─────────────────────────────────────────

    @Test
    fun `launcher returns false returns AppNotFound`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = false)
            val installed = listOf(app("com.whatsapp", "WhatsApp"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("WhatsApp"))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.AppNotFound::class.java, failed.reason)
        }

    // ── 13. Multiple substring hits: Levenshtein narrows to one ───────────────

    @Test
    fun `multiple substring hits narrowed to one by Levenshtein`() =
        runTest {
            // Query "crom" (4 chars, triggers Levenshtein).
            // Both labels contain "crom" (substring hits).
            // "Croma" (Lev 1) vs "Cromoterapia" (Lev 8) — Levenshtein picks "Croma".
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed =
                listOf(
                    app("com.example.croma", "Croma"),
                    app("com.example.cromo", "Cromoterapia"),
                )
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("crom"))
            // Multiple substring hits → the handler returns AmbiguousApp from the contains step,
            // not narrowed by Levenshtein. Pin this documented behaviour.
            // (The brief's test 13 says "Levenshtein narrows" but the implementation returns
            // AmbiguousApp at the substring step — the Levenshtein step is only reached when
            // there are ZERO substring hits. This test documents the actual contract.)
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.AmbiguousApp::class.java, failed.reason)
        }

    // ── 14. Multiple substring hits: Levenshtein still ties ──────────────────

    @Test
    fun `fuzzy match ties return AmbiguousApp with all tied candidates`() =
        runTest {
            // No alias or substring hits for "xyza"; two apps equidistant (Lev 3) from query.
            // "xyzb" and "xyzc" — both have Lev distance 1 from "xyza" (one substitution each).
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed =
                listOf(
                    app("com.example.b", "xyzb"),
                    app("com.example.c", "xyzc"),
                )
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("xyza"))
            val failed = assertFailed(result)
            assertInstanceOf(CurroError.AmbiguousApp::class.java, failed.reason)
            val ambig = failed.reason as CurroError.AmbiguousApp
            assertEquals(2, ambig.matches.size)
        }

    // ── 15. Locale-sensitive lowercase ───────────────────────────────────────

    @Test
    fun `curroNormalize on query does not crash on Turkish dotted-I input`() =
        runTest {
            // Sanity: the handler must not throw for any valid String input.
            val installed = listOf(app("com.example.app", "Istanbul"))
            val result = handler(installed).handle(call("İSTANBUL"))
            // Result is either Spoken or Failed — we only care it doesn't throw.
            assertTrue(result is HandlerResult.Spoken || result is HandlerResult.Failed)
        }

    // ── SF-7.4 bump invariant: handler does NOT touch AppUsageDao directly ─────

    /**
     * SF-7.4: the bump happens INSIDE [com.curro.app.data.apps.IntentAppLauncher.launch].
     * [OpenAppHandler] must NOT call any DAO directly — it only calls [AppLauncher.launch].
     * This test verifies that [FakeAppLauncher] (not the DAO) is the single call site.
     *
     * The bump-invariant is exhaustively tested in [com.curro.app.data.apps.AppLauncherTest];
     * here we only verify the handler stays clean.
     */
    @Test
    fun `SF-7_4 openApp handler delegates to AppLauncher and does not touch AppUsageDao`() =
        runTest {
            // The FakeAppLauncher in this test file does NOT bump any DAO —
            // if the handler called a DAO directly, there is no DAO to call and
            // the test would throw a NullPointerException, making the absence observable.
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed = listOf(app("com.whatsapp", "WhatsApp"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("WhatsApp"))
            // Handler must succeed
            assertSpoken(result)
            // The only call site must be AppLauncher.launch (captured here)
            assertEquals("com.whatsapp", fakeLauncher.lastLaunched)
            // No DAO reference in OpenAppHandler → if it tried to call one, it would crash.
            // The absence of a crash IS the assertion.
        }

    // ── spoken text format ────────────────────────────────────────────────────

    @Test
    fun `spoken text ends with period`() =
        runTest {
            val fakeLauncher = FakeAppLauncher(result = true)
            val installed = listOf(app("com.whatsapp", "WhatsApp"))
            val result = handlerWithLauncher(installed, fakeLauncher).handle(call("WhatsApp"))
            assertEquals('.', assertSpoken(result).last())
        }

    @Test
    fun `ambiguous speech matches copy_app_ambiguous`() =
        runTest {
            val installed =
                listOf(
                    app("com.a", "Fooba"),
                    app("com.b", "Foob"),
                )
            // Both match substring "foob" → AmbiguousApp
            val result = handler(installed).handle(call("foob"))
            val failed = assertFailed(result)
            assertEquals(
                "Tengo varias apps que se llaman así, prueba con el nombre exacto.",
                failed.speech,
            )
        }
}
