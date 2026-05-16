package com.curro.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.curro.app.assistant.TestTimeProvider
import kotlinx.coroutines.Dispatchers
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
 * In-memory Room tests for [RoomFailedCommandLog] (SF-7.5 / US-049).
 *
 * Uses [Dispatchers.Unconfined] as the IO dispatcher so calls run synchronously
 * on the test thread — same pattern as [FailedCommandDaoTest] which uses
 * `allowMainThreadQueries()`. The cap-at-50 invariant is verified through the
 * full [RoomFailedCommandLog.record] path (not via the DAO directly) to ensure
 * the impl wires `insertAndTrim` correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RoomFailedCommandLogTest {
    private lateinit var db: CurroDatabase
    private lateinit var dao: FailedCommandDao
    private val timeProvider = TestTimeProvider(nowMs = 1000L)
    private lateinit var log: RoomFailedCommandLog

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    CurroDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.failedCommandDao()
        log = RoomFailedCommandLog(dao, timeProvider, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── 1. record — timestamp comes from TimeProvider ─────────────────────────

    @Test
    fun `record persists row with timestamp from TimeProvider`() =
        runBlocking {
            log.record("hola", FailureKind.INVALID_OUTPUT)
            val rows = dao.observeRecent(50).first()
            assertEquals(1, rows.size)
            assertEquals(1000L, rows.first().timestampMs)
        }

    // ── 2. count reflects insert count ───────────────────────────────────────

    @Test
    fun `count reflects insert count`() =
        runBlocking {
            log.record("uno", FailureKind.HANDLER_ERROR)
            log.record("dos", FailureKind.UNKNOWN_FUNCTION)
            log.record("tres", FailureKind.INVALID_OUTPUT)
            assertEquals(3, log.count())
        }

    // ── 3. cap-at-50: exactly 50 rows after 50 records ───────────────────────

    @Test
    fun `record 50 times keeps count at 50`() =
        runBlocking {
            repeat(50) { i ->
                timeProvider.nowMs = i.toLong() * 100
                log.record("u$i", FailureKind.HANDLER_ERROR)
            }
            assertEquals(50, log.count())
        }

    // ── 4. cap-at-50: oldest 10 dropped after 60 records ─────────────────────

    @Test
    fun `record 60 times keeps the newest 50`() =
        runBlocking {
            repeat(60) { i ->
                timeProvider.nowMs = i.toLong() * 100
                log.record("u$i", FailureKind.HANDLER_ERROR)
            }
            assertEquals(50, log.count())
            val rows = dao.observeRecent(50).first()
            val timestamps = rows.map { it.timestampMs }.toSet()
            // Oldest 10 (timestamps 0..900) must be gone; newest 50 (1000..5900) survive.
            assertTrue((0L..900L step 100).none { it in timestamps })
            assertTrue((1000L..5900L step 100).all { it in timestamps })
        }

    // ── 5. observeRecent: emissions are newest-first ──────────────────────────

    @Test
    fun `observeRecent emits rows newest-first`() =
        runBlocking {
            timeProvider.nowMs = 1000L
            log.record("primero", FailureKind.INVALID_OUTPUT)
            timeProvider.nowMs = 2000L
            log.record("segundo", FailureKind.UNKNOWN_FUNCTION)
            val rows = dao.observeRecent(50).first()
            assertEquals(2, rows.size)
            assertEquals("segundo", rows[0].transcript)
            assertEquals("primero", rows[1].transcript)
        }

    // ── 6. deleteAll empties the table ────────────────────────────────────────

    @Test
    fun `deleteAll empties the table`() =
        runBlocking {
            repeat(5) { i ->
                timeProvider.nowMs = i.toLong() * 100
                log.record("u$i", FailureKind.HANDLER_ERROR)
            }
            log.deleteAll()
            assertEquals(0, log.count())
            assertTrue(dao.observeRecent(50).first().isEmpty())
        }
}
