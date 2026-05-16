package com.curro.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for [ContactAliasDao] (SF-7.1 / US-045).
 *
 * Uses [RobolectricTestRunner] so [ApplicationProvider.getApplicationContext] produces
 * a real Android [android.app.Application] stub on the JVM — no device needed. The
 * in-memory database has no persistent state; [tearDown] closes it after each test.
 *
 * JUnit 4 style here is load-bearing: JUnit 5's `@ExtendWith(RobolectricExtension)` does
 * not exist in Robolectric 4.x. The JUnit vintage engine (`testRuntimeOnly` in
 * `build.gradle.kts`) lets these tests run within the JUnit 5 platform. All other JVM
 * tests in this project stay on JUnit 5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ContactAliasDaoTest {
    private lateinit var db: CurroDatabase
    private lateinit var dao: ContactAliasDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                CurroDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = db.contactAliasDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun entity(
        alias: String,
        lookupKey: String = "key_$alias",
        displayName: String = alias.replaceFirstChar { it.uppercaseChar() },
        source: AliasSource = AliasSource.LEARNED,
        useCount: Int = 0,
        lastUsedAtMs: Long = 1_000L,
    ) = ContactAliasEntity(
        alias = alias,
        lookupKey = lookupKey,
        displayName = displayName,
        source = source,
        createdAtMs = 0L,
        lastUsedAtMs = lastUsedAtMs,
        useCount = useCount,
    )

    // ── 1. Empty state ─────────────────────────────────────────────────────────

    @Test
    fun `observeAll emits empty list initially`() =
        runBlocking {
            assertTrue(dao.observeAll().first().isEmpty())
        }

    @Test
    fun `findByAlias returns null when table is empty`() =
        runBlocking {
            assertNull(dao.findByAlias("mi hijo"))
        }

    // ── 2. Upsert (insert) ─────────────────────────────────────────────────────

    @Test
    fun `upsert inserts new row and findByAlias returns it`() =
        runBlocking {
            dao.upsert(entity("mi hija", lookupKey = "lk1"))
            val found = dao.findByAlias("mi hija")
            assertNotNull(found)
            assertEquals("lk1", found!!.lookupKey)
        }

    @Test
    fun `upsert on duplicate alias replaces the row`() =
        runBlocking {
            dao.upsert(entity("mi hija", lookupKey = "old"))
            dao.upsert(entity("mi hija", lookupKey = "new"))
            val found = dao.findByAlias("mi hija")
            assertEquals("new", found!!.lookupKey)
        }

    @Test
    fun `different aliases coexist`() =
        runBlocking {
            dao.upsert(entity("mi hija", lookupKey = "k1"))
            dao.upsert(entity("mi hijo", lookupKey = "k2"))
            assertEquals(2, dao.observeAll().first().size)
        }

    // ── 3. bumpUsage ──────────────────────────────────────────────────────────

    @Test
    fun `bumpUsage increments useCount and updates lastUsedAtMs`() =
        runBlocking {
            dao.upsert(entity("mi hija", lookupKey = "k1", useCount = 0, lastUsedAtMs = 100L))
            dao.bumpUsage("mi hija", now = 999L)
            val updated = dao.findByAlias("mi hija")
            assertEquals(1, updated!!.useCount)
            assertEquals(999L, updated.lastUsedAtMs)
        }

    @Test
    fun `bumpUsage on missing alias is a no-op`() =
        runBlocking {
            dao.bumpUsage("nobody", now = 1L)
            assertTrue(dao.observeAll().first().isEmpty())
        }

    // ── 4. topUsed ────────────────────────────────────────────────────────────

    @Test
    fun `topUsed returns rows ordered by useCount desc`() =
        runBlocking {
            dao.upsert(entity("mi hija", useCount = 3))
            dao.upsert(entity("mi hijo", useCount = 1))
            dao.upsert(entity("mi madre", useCount = 10))
            val top = dao.topUsed(3)
            assertEquals("mi madre", top[0].alias)
            assertEquals("mi hija", top[1].alias)
            assertEquals("mi hijo", top[2].alias)
        }

    @Test
    fun `topUsed respects the limit`() =
        runBlocking {
            repeat(5) { i -> dao.upsert(entity("alias$i")) }
            assertEquals(3, dao.topUsed(3).size)
        }

    // ── 5. delete + deleteAll ──────────────────────────────────────────────────

    @Test
    fun `delete removes the specified alias`() =
        runBlocking {
            dao.upsert(entity("mi hija"))
            dao.delete("mi hija")
            assertNull(dao.findByAlias("mi hija"))
        }

    @Test
    fun `deleteAll clears the table`() =
        runBlocking {
            dao.upsert(entity("a1"))
            dao.upsert(entity("a2"))
            dao.deleteAll()
            assertTrue(dao.observeAll().first().isEmpty())
        }
}
