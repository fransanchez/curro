package com.curro.app.data.apps

import app.cash.turbine.test
import com.curro.app.assistant.TestTimeProvider
import com.curro.app.data.local.AppUsageDao
import com.curro.app.data.local.AppUsageEntity
import com.curro.app.domain.model.AppLabel
import com.curro.app.domain.model.FavoriteApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [RecencyFavoriteAppsRepositoryImpl] (SF-7.4 / US-048).
 *
 * Test infra:
 *  - [FakeAppUsageDao] — a pure in-memory fake (no Room, no thread pool). Using a fake
 *    rather than in-memory Room avoids threading race conditions between Room's
 *    [androidx.room.TransactionExecutor] and the virtual-time scheduler.
 *  - [TestTimeProvider] with mutable [TestTimeProvider.nowMs].
 *  - [FakeSeedAppResolverInner] — returns hand-built seed list without touching [PackageManager].
 *  - [UnconfinedTestDispatcher] as `ioDispatcher` so `flowOn` doesn't leave the test
 *    scheduler — virtual-time advances via [advanceTimeBy] move the 24-h [delay].
 *  - Turbine [test] for Flow assertions.
 *
 * JUnit 4 style ([RobolectricTestRunner]) is required because [AppUsageEntity] uses
 * [android.content.Context] transitively via test infrastructure.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RecencyFavoriteAppsRepositoryImplTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val timeProvider = TestTimeProvider(nowMs = NOW)

    /** In-memory fake [AppUsageDao] — synchronous, no Room thread pool. */
    private class FakeAppUsageDao : AppUsageDao() {
        private val entities: MutableMap<String, AppUsageEntity> = mutableMapOf()

        override suspend fun topByOpenCount(limit: Int): List<AppUsageEntity> =
            entities.values.sortedByDescending { it.openCount }.take(limit)

        override fun observeTopByOpenCount(limit: Int): Flow<List<AppUsageEntity>> =
            flowOf(entities.values.sortedByDescending { it.openCount }.take(limit))

        override suspend fun bumpExisting(
            packageName: String,
            now: Long,
        ): Int {
            val existing = entities[packageName] ?: return 0
            entities[packageName] =
                existing.copy(
                    openCount = existing.openCount + 1,
                    lastOpenedAtMs = now,
                )
            return 1
        }

        override suspend fun insertIfMissing(entity: AppUsageEntity) {
            if (!entities.containsKey(entity.packageName)) {
                entities[entity.packageName] = entity
            }
        }

        override suspend fun deleteAll() {
            entities.clear()
        }

        /** Convenience: set [count] opens for [pkg] at [atMs] atomically. */
        fun set(
            pkg: String,
            count: Int,
            atMs: Long,
        ) {
            entities[pkg] = AppUsageEntity(packageName = pkg, openCount = count, lastOpenedAtMs = atMs)
        }
    }

    /** Seed apps returned by the fake. Packages match the IDs so resolvedPackage checks work. */
    private val fakeSeedList =
        listOf(
            FavoriteApp(
                id = "whatsapp",
                label = AppLabel.Resource(com.curro.app.R.string.copy_app_label_whatsapp),
                resolvedPackage = "com.whatsapp",
                icon = null,
            ),
            FavoriteApp(
                id = "calls",
                label = AppLabel.Resource(com.curro.app.R.string.copy_app_label_calls),
                resolvedPackage = "com.android.dialer",
                icon = null,
            ),
            FavoriteApp(
                id = "camera",
                label = AppLabel.Resource(com.curro.app.R.string.copy_app_label_camera),
                resolvedPackage = "com.android.camera",
                icon = null,
            ),
            FavoriteApp(
                id = "photos",
                label = AppLabel.Resource(com.curro.app.R.string.copy_app_label_photos),
                resolvedPackage = "com.miui.gallery",
                icon = null,
            ),
        )

    /**
     * Fake [SeedAppResolver] that bypasses [android.content.pm.PackageManager].
     *
     * [seedFavorites] returns [fakeSeedList].
     * [toFavoriteApp] returns a minimal [FavoriteApp] for any package in [knownPackages].
     */
    private inner class FakeSeedAppResolverInner(
        private val known: MutableSet<String> =
            mutableSetOf(
                "com.whatsapp",
                "com.android.dialer",
                "com.android.camera",
                "com.miui.gallery",
            ),
    ) : SeedAppResolver(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
        ) {
        fun registerPackage(pkg: String) {
            known.add(pkg)
        }

        override fun seedFavorites(): List<FavoriteApp> = fakeSeedList

        override fun toFavoriteApp(packageName: String): FavoriteApp? {
            if (packageName !in known) return null
            return fakeSeedList.find { it.resolvedPackage == packageName }
                ?: FavoriteApp(
                    id = packageName,
                    label = AppLabel.Text(packageName),
                    resolvedPackage = packageName,
                    icon = null,
                )
        }
    }

    private lateinit var fakeDao: FakeAppUsageDao
    private lateinit var seedResolver: FakeSeedAppResolverInner
    private lateinit var repo: RecencyFavoriteAppsRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeAppUsageDao()
        seedResolver = FakeSeedAppResolverInner()
        repo =
            RecencyFavoriteAppsRepositoryImpl(
                appUsageDao = fakeDao,
                timeProvider = timeProvider,
                seedAppResolver = seedResolver,
                ioDispatcher = testDispatcher,
            )
    }

    // ── T1. Empty usage → falls back to seeds ────────────────────────────────

    @Test
    fun `T1 empty usage falls back to all four seeds in canonical order`() =
        runTest(testDispatcher) {
            timeProvider.nowMs = NOW
            repo.observeFavorites().test {
                val first = awaitItem()
                assertEquals(4, first.size)
                assertEquals(listOf("whatsapp", "calls", "camera", "photos"), first.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── T2. One heavy user ranks first ──────────────────────────────────────

    @Test
    fun `T2 heavy usage of whatsapp ranks it first`() =
        runTest(testDispatcher) {
            timeProvider.nowMs = NOW
            fakeDao.set("com.whatsapp", 20, NOW)
            repo.observeFavorites().test {
                val first = awaitItem()
                assertEquals("com.whatsapp", first.first().resolvedPackage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── T3. Decay: old high-count outranks recent low-count when score is higher ─

    @Test
    fun `T3 old app with 50 opens 20 days ago outranks recent 5-open app`() =
        runTest(testDispatcher) {
            timeProvider.nowMs = NOW
            seedResolver.registerPackage("com.oldapp")
            fakeDao.set("com.oldapp", 50, NOW - 20 * DAY_MS)
            fakeDao.set("com.whatsapp", 5, NOW)

            repo.observeFavorites().test {
                val first = awaitItem()
                // oldapp score = 50 × (1 − 20/30) ≈ 16.6; whatsapp score = 5.0 → oldapp wins
                assertEquals("com.oldapp", first.first().resolvedPackage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── T4. 30+ days stale → score = 0 → seeds fill in ──────────────────────

    @Test
    fun `T4 app opened 35 days ago scores 0 and is excluded — seeds fill all 4 slots`() =
        runTest(testDispatcher) {
            timeProvider.nowMs = NOW
            seedResolver.registerPackage("com.example.test")
            fakeDao.set("com.example.test", 100, NOW - 35 * DAY_MS)

            repo.observeFavorites().test {
                val first = awaitItem()
                assertFalse(
                    "Stale app must not appear in the grid",
                    first.any { it.resolvedPackage == "com.example.test" },
                )
                assertEquals(listOf("whatsapp", "calls", "camera", "photos"), first.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── T5. Tie-breaker: higher count wins at same lastOpened ────────────────

    @Test
    fun `T5 higher open count ranks first when both opened at same time`() =
        runTest(testDispatcher) {
            timeProvider.nowMs = NOW
            seedResolver.registerPackage("com.appA")
            seedResolver.registerPackage("com.appB")
            fakeDao.set("com.appA", 10, NOW)
            fakeDao.set("com.appB", 5, NOW)

            repo.observeFavorites().test {
                val first = awaitItem()
                val indexA = first.indexOfFirst { it.resolvedPackage == "com.appA" }
                val indexB = first.indexOfFirst { it.resolvedPackage == "com.appB" }
                assertTrue(
                    "appA (10 opens) must rank above appB (5 opens) at same time",
                    indexA < indexB,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── T6. Seed padding: 3 usage-derived → 4th slot is first seed ──────────

    @Test
    fun `T6 three usage-derived apps padded with first non-overlapping seed`() =
        runTest(testDispatcher) {
            timeProvider.nowMs = NOW
            seedResolver.registerPackage("com.a")
            seedResolver.registerPackage("com.b")
            seedResolver.registerPackage("com.c")
            fakeDao.set("com.a", 10, NOW)
            fakeDao.set("com.b", 5, NOW)
            fakeDao.set("com.c", 2, NOW)

            repo.observeFavorites().test {
                val first = awaitItem()
                assertEquals(4, first.size)
                val top3 = first.subList(0, 3).map { it.resolvedPackage }
                assertTrue("com.a must be in top-3", top3.contains("com.a"))
                assertTrue("com.b must be in top-3", top3.contains("com.b"))
                assertTrue("com.c must be in top-3", top3.contains("com.c"))
                // 4th slot = first seed not already in the list (none overlap → "whatsapp")
                assertEquals("whatsapp", first[3].id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── T7. Stability: no re-emission within 24h despite DAO changes ──────────

    @Test
    fun `T7 heavy usage within 23h does NOT trigger a re-emission`() =
        runTest(testDispatcher) {
            timeProvider.nowMs = NOW
            fakeDao.set("com.whatsapp", 10, NOW)
            seedResolver.registerPackage("com.upstart")

            repo.observeFavorites().test {
                val first = awaitItem()
                assertEquals("com.whatsapp", first.first().resolvedPackage)

                // Simulate 23 h of heavy usage of com.upstart — DAO updated, grid must NOT re-emit.
                val t = NOW + 23 * 60L * 60 * 1_000
                timeProvider.nowMs = t
                fakeDao.set("com.upstart", 50 * 23, t) // 50 opens/hour × 23 hours

                // Advance virtual time to 23h — delay(24h) has NOT fired.
                advanceTimeBy(23L * 60 * 60 * 1_000)
                expectNoEvents() // stability invariant — no re-emission within 24h

                // Advance past 24h → the timer fires → re-emit with new order.
                timeProvider.nowMs = NOW + 25 * 60L * 60 * 1_000
                advanceTimeBy(2L * 60 * 60 * 1_000) // total: 25h virtual
                val second = awaitItem()
                assertEquals(
                    "com.upstart (1150 opens) must be first after 24h tick",
                    "com.upstart",
                    second.first().resolvedPackage,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── T8. recomputeTrigger forces immediate re-emission ────────────────────

    @Test
    fun `T8 recomputeTrigger forces immediate recompute without waiting 24h`() =
        runTest(testDispatcher) {
            timeProvider.nowMs = NOW
            fakeDao.set("com.whatsapp", 5, NOW)
            seedResolver.registerPackage("com.upstart")

            repo.observeFavorites().test {
                awaitItem() // initial emission

                // Add heavy usage of com.upstart without advancing past 24h.
                fakeDao.set("com.upstart", 20, NOW)
                repo.recomputeTrigger.emit(Unit)

                val second = awaitItem()
                assertEquals(
                    "recomputeTrigger must force immediate recompute",
                    "com.upstart",
                    second.first().resolvedPackage,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── scoreEntity formula ───────────────────────────────────────────────────

    @Test
    fun `scoreEntity at daysSince=0 returns openCount times 1_0`() {
        val entity = AppUsageEntity(packageName = "com.x", openCount = 7, lastOpenedAtMs = NOW)
        val score = repo.scoreEntity(entity, NOW)
        assertEquals(7.0, score, 0.001)
    }

    @Test
    fun `scoreEntity at exactly 30 days returns 0`() {
        val entity =
            AppUsageEntity(
                packageName = "com.x",
                openCount = 100,
                lastOpenedAtMs = NOW - 30 * DAY_MS,
            )
        val score = repo.scoreEntity(entity, NOW)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `scoreEntity beyond 30 days is clamped to 0 not negative`() {
        val entity =
            AppUsageEntity(
                packageName = "com.x",
                openCount = 100,
                lastOpenedAtMs = NOW - 35 * DAY_MS,
            )
        val score = repo.scoreEntity(entity, NOW)
        assertEquals(0.0, score, 0.001)
    }

    private companion object {
        const val NOW = 1_000_000_000L
        const val DAY_MS = 24L * 60 * 60 * 1_000
    }
}
