package com.curro.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for [AppUsageDao] (SF-7.1 / US-045).
 *
 * Key invariant under test: [AppUsageDao.upsert] is a bump-or-insert — never a
 * destructive REPLACE — so `openCount` accumulates correctly across multiple launches.
 * See [AppUsageDao] Kdoc for the two-step `bumpExisting` → `insertIfMissing` logic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AppUsageDaoTest {
    private lateinit var db: CurroDatabase
    private lateinit var dao: AppUsageDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                CurroDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = db.appUsageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── 1. Empty state ─────────────────────────────────────────────────────────

    @Test
    fun `topByOpenCount returns empty list when table is empty`() =
        runBlocking {
            assertTrue(dao.topByOpenCount(10).isEmpty())
        }

    @Test
    fun `observeTopByOpenCount emits empty list initially`() =
        runBlocking {
            assertTrue(dao.observeTopByOpenCount(10).first().isEmpty())
        }

    // ── 2. upsert: first call inserts with openCount = 1 ─────────────────────

    @Test
    fun `upsert of new package inserts with openCount 1`() =
        runBlocking {
            dao.upsert("com.example.app", now = 1000L)
            val row = dao.topByOpenCount(1).single()
            assertEquals("com.example.app", row.packageName)
            assertEquals(1, row.openCount)
            assertEquals(1000L, row.lastOpenedAtMs)
        }

    // ── 3. upsert: subsequent calls increment openCount (not reset) ───────────

    @Test
    fun `upsert twice increments openCount to 2`() =
        runBlocking {
            dao.upsert("com.example.app", now = 1000L)
            dao.upsert("com.example.app", now = 2000L)
            val row = dao.topByOpenCount(1).single()
            assertEquals(2, row.openCount)
            assertEquals(2000L, row.lastOpenedAtMs)
        }

    @Test
    fun `upsert five times results in openCount 5`() =
        runBlocking {
            repeat(5) { i -> dao.upsert("com.example.app", now = i.toLong()) }
            val row = dao.topByOpenCount(1).single()
            assertEquals(5, row.openCount)
        }

    // ── 4. topByOpenCount ordering + limit ───────────────────────────────────

    @Test
    fun `topByOpenCount orders by openCount desc`() =
        runBlocking {
            dao.upsert("com.a", now = 1L) // openCount = 1
            repeat(3) { dao.upsert("com.b", now = 2L) } // openCount = 3
            dao.upsert("com.c", now = 3L) // openCount = 1
            val top = dao.topByOpenCount(3)
            assertEquals("com.b", top[0].packageName)
        }

    @Test
    fun `topByOpenCount respects the limit`() =
        runBlocking {
            repeat(5) { i -> dao.upsert("com.pkg$i", now = 1L) }
            assertEquals(3, dao.topByOpenCount(3).size)
        }

    // ── 5. deleteAll ─────────────────────────────────────────────────────────

    @Test
    fun `deleteAll clears the table`() =
        runBlocking {
            dao.upsert("com.a", now = 1L)
            dao.upsert("com.b", now = 2L)
            dao.deleteAll()
            assertTrue(dao.topByOpenCount(10).isEmpty())
        }
}
