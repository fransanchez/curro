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
 * In-memory Room tests for [FailedCommandDao] (SF-7.1 / US-045).
 *
 * Key invariant under test: [FailedCommandDao.insertAndTrim] is the only write path,
 * and it keeps the table at most 50 rows, retaining the 50 newest by [FailedCommandEntity.timestampMs].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FailedCommandDaoTest {
    private lateinit var db: CurroDatabase
    private lateinit var dao: FailedCommandDao

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                CurroDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = db.failedCommandDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun entity(
        transcript: String,
        timestampMs: Long,
    ) = FailedCommandEntity(
        transcript = transcript,
        kind = FailureKind.INVALID_OUTPUT,
        details = "",
        timestampMs = timestampMs,
    )

    // ── 1. Empty state ─────────────────────────────────────────────────────────

    @Test
    fun `observeRecent emits empty list initially`() =
        runBlocking {
            assertTrue(dao.observeRecent(50).first().isEmpty())
        }

    @Test
    fun `count returns 0 initially`() =
        runBlocking {
            assertEquals(0, dao.count())
        }

    // ── 2. insertAndTrim: basic insert ─────────────────────────────────────────

    @Test
    fun `insertAndTrim inserts a row`() =
        runBlocking {
            dao.insertAndTrim(entity("leer whatsapp", timestampMs = 1000L))
            assertEquals(1, dao.count())
        }

    @Test
    fun `observeRecent returns newest row first`() =
        runBlocking {
            dao.insertAndTrim(entity("primero", timestampMs = 1000L))
            dao.insertAndTrim(entity("segundo", timestampMs = 2000L))
            val rows = dao.observeRecent(50).first()
            assertEquals("segundo", rows[0].transcript)
            assertEquals("primero", rows[1].transcript)
        }

    // ── 3. insertAndTrim: cap-at-50 invariant ─────────────────────────────────

    @Test
    fun `insertAndTrim caps table at 50 rows`() =
        runBlocking {
            repeat(55) { i ->
                dao.insertAndTrim(entity("utterance $i", timestampMs = i.toLong()))
            }
            assertEquals(50, dao.count())
        }

    @Test
    fun `insertAndTrim retains the 50 newest rows after a cap`() =
        runBlocking {
            repeat(55) { i ->
                dao.insertAndTrim(entity("utterance $i", timestampMs = i.toLong()))
            }
            // The 5 oldest rows (timestampMs 0..4) must be gone; the newest 50 (5..54) survive.
            val rows = dao.observeRecent(50).first()
            val timestamps = rows.map { it.timestampMs }.toSet()
            assertTrue((0L..4L).none { it in timestamps })
            assertTrue((5L..54L).all { it in timestamps })
        }

    @Test
    fun `count stays at exactly 50 after repeated inserts beyond the cap`() =
        runBlocking {
            repeat(70) { i ->
                dao.insertAndTrim(entity("u$i", timestampMs = i.toLong()))
            }
            assertEquals(50, dao.count())
        }

    // ── 4. observeRecent limit param ──────────────────────────────────────────

    @Test
    fun `observeRecent respects the limit parameter`() =
        runBlocking {
            repeat(10) { i ->
                dao.insertAndTrim(entity("u$i", timestampMs = i.toLong()))
            }
            val rows = dao.observeRecent(5).first()
            assertEquals(5, rows.size)
        }

    // ── 5. deleteAll ──────────────────────────────────────────────────────────

    @Test
    fun `deleteAll clears the table`() =
        runBlocking {
            dao.insertAndTrim(entity("one", timestampMs = 1L))
            dao.deleteAll()
            assertEquals(0, dao.count())
            assertTrue(dao.observeRecent(50).first().isEmpty())
        }
}
