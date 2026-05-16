package com.curro.app.data.contacts

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.curro.app.assistant.TestTimeProvider
import com.curro.app.data.local.AliasSource
import com.curro.app.data.local.ContactAliasDao
import com.curro.app.data.local.ContactAliasEntity
import com.curro.app.data.local.CurroDatabase
import com.curro.app.domain.model.Contact
import com.curro.app.domain.repository.ContactsProvider
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
 * In-memory Room tests for [RoomAliasRepository] (SF-7.2 / US-046).
 *
 * Uses a [FakeContactsProvider] to decouple from the real ContentResolver.
 * The stale-`LOOKUP_KEY` path is covered by setting the fake's response to
 * `null` for a specific lookup key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RoomAliasRepositoryTest {
    private lateinit var db: CurroDatabase
    private lateinit var dao: ContactAliasDao
    private val fakeContacts = FakeContactsProvider()
    private val timeProvider = TestTimeProvider(nowMs = 1_000L)
    private lateinit var repo: RoomAliasRepository

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                CurroDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = db.contactAliasDao()
        repo = RoomAliasRepository(dao, fakeContacts, timeProvider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun contact(
        lookupKey: String,
        displayName: String = lookupKey,
        phone: String = "+34600000000",
    ) = Contact(lookupKey = lookupKey, displayName = displayName, phoneNumbers = listOf(phone), photoUri = null)

    private fun seedRow(
        alias: String,
        lookupKey: String,
        displayName: String = lookupKey,
        useCount: Int = 0,
        lastUsedAtMs: Long = 1_000L,
        source: AliasSource = AliasSource.LEARNED,
    ) = runBlocking {
        dao.upsert(
            ContactAliasEntity(
                alias = alias,
                lookupKey = lookupKey,
                displayName = displayName,
                source = source,
                createdAtMs = 0L,
                lastUsedAtMs = lastUsedAtMs,
                useCount = useCount,
            ),
        )
    }

    // ── 1. resolveAlias — unknown alias ────────────────────────────────────────

    @Test
    fun `resolveAlias unknown returns empty`() =
        runBlocking {
            assertTrue(repo.resolveAlias("mi hija").isEmpty())
        }

    // ── 2. resolveAlias — known alias, contact found ───────────────────────────

    @Test
    fun `resolveAlias known returns single contact`() =
        runBlocking {
            val c = contact("lk1", "Lucía Ruiz")
            fakeContacts.lookupKeyResult["lk1"] = c
            seedRow("mi hija", "lk1", "Lucía Ruiz")

            val result = repo.resolveAlias("mi hija")
            assertEquals(1, result.size)
            assertEquals("Lucía Ruiz", result.first().displayName)
        }

    // ── 3. resolveAlias — normalisation (trim + lowercase) ────────────────────

    @Test
    fun `resolveAlias normalises the input before lookup`() =
        runBlocking {
            val c = contact("lk1", "Lucía Ruiz")
            fakeContacts.lookupKeyResult["lk1"] = c
            seedRow("mi hija", "lk1")

            // Extra spaces and uppercase — should still resolve.
            val result = repo.resolveAlias("  Mi Hija  ")
            assertEquals(1, result.size)
        }

    // ── 4. resolveAlias — stale LOOKUP_KEY (contact deleted) ──────────────────

    @Test
    fun `resolveAlias stale lookupKey returns empty`() =
        runBlocking {
            seedRow("mi hija", "lk-removed")
            fakeContacts.lookupKeyResult["lk-removed"] = null // contact was deleted

            assertTrue(repo.resolveAlias("mi hija").isEmpty())
        }

    // ── 5. resolveAlias — bump on success, no bump on stale ───────────────────

    @Test
    fun `resolveAlias bumps usage on success`() =
        runBlocking {
            val c = contact("lk1", "Lucía Ruiz")
            fakeContacts.lookupKeyResult["lk1"] = c
            seedRow("mi hija", "lk1", useCount = 3, lastUsedAtMs = 100L)
            timeProvider.nowMs = 2_000L

            repo.resolveAlias("mi hija")

            val row = dao.findByAlias("mi hija")
            assertEquals(4, row!!.useCount)
            assertEquals(2_000L, row.lastUsedAtMs)
        }

    @Test
    fun `resolveAlias stale row does not bump usage`() =
        runBlocking {
            seedRow("mi hija", "lk-removed", useCount = 3, lastUsedAtMs = 100L)
            fakeContacts.lookupKeyResult["lk-removed"] = null

            repo.resolveAlias("mi hija")

            val row = dao.findByAlias("mi hija")
            assertEquals(3, row!!.useCount)
            assertEquals(100L, row.lastUsedAtMs)
        }

    // ── 6. learn ──────────────────────────────────────────────────────────────

    @Test
    fun `learn persists entity with LEARNED source and zero useCount`() =
        runBlocking {
            val c = contact("lk1", "Lucía Ruiz")
            repo.learn("mi hija", c, AliasSource.LEARNED)

            val row = dao.findByAlias("mi hija")
            assertNotNull(row)
            assertEquals("lk1", row!!.lookupKey)
            assertEquals(AliasSource.LEARNED, row.source)
            assertEquals(0, row.useCount)
            assertEquals(1_000L, row.createdAtMs)
            assertEquals(1_000L, row.lastUsedAtMs)
        }

    @Test
    fun `learn same alias twice replaces via upsert`() =
        runBlocking {
            val cA = contact("lk-a", "Contact A")
            val cB = contact("lk-b", "Contact B")
            repo.learn("mi hija", cA, AliasSource.LEARNED)
            repo.learn("mi hija", cB, AliasSource.LEARNED)

            val row = dao.findByAlias("mi hija")
            assertEquals("lk-b", row!!.lookupKey)
            assertEquals(1, dao.topUsed(10).size)
        }

    // ── 7. topUsedSnapshots ───────────────────────────────────────────────────

    @Test
    fun `topUsedSnapshots limit 3 returns 3 snapshots`() =
        runBlocking {
            repeat(5) { i -> seedRow("alias$i", "lk$i") }
            val snapshots = repo.topUsedSnapshots(3)
            assertEquals(3, snapshots.size)
        }

    // ── 8. observeAll — order ─────────────────────────────────────────────────

    @Test
    fun `observeAll emits AliasViews ordered by useCount desc`() =
        runBlocking {
            seedRow("a_low", "lk1", useCount = 1)
            seedRow("b_high", "lk2", useCount = 10)
            seedRow("c_mid", "lk3", useCount = 5)

            repo.observeAll().test {
                val views = awaitItem()
                assertEquals("b_high", views[0].alias)
                assertEquals("c_mid", views[1].alias)
                assertEquals("a_low", views[2].alias)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 9. findStoredAlias ────────────────────────────────────────────────────

    @Test
    fun `findStoredAlias existing returns record with displayName and source`() =
        runBlocking {
            seedRow("mi hija", "lk1", displayName = "Lucía Ruiz", source = AliasSource.LEARNED)

            val record = repo.findStoredAlias("mi hija")
            assertNotNull(record)
            assertEquals("Lucía Ruiz", record!!.displayName)
            assertEquals(AliasSource.LEARNED, record.source)
        }

    @Test
    fun `findStoredAlias unknown returns null`() =
        runBlocking {
            assertNull(repo.findStoredAlias("inexistente"))
        }

    // ── 10. deleteAll ─────────────────────────────────────────────────────────

    @Test
    fun `deleteAll clears table and observeAll emits empty`() =
        runBlocking {
            seedRow("mi hija", "lk1")
            repo.deleteAll()

            repo.observeAll().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Fake collaborators ────────────────────────────────────────────────────

    /** Fake [ContactsProvider] for [RoomAliasRepositoryTest]: configurable per-lookupKey. */
    private class FakeContactsProvider : ContactsProvider {
        val lookupKeyResult: MutableMap<String, Contact?> = mutableMapOf()

        override suspend fun findByName(query: String): List<Contact> = emptyList()

        override suspend fun findByLookupKey(lookupKey: String): Contact? = lookupKeyResult[lookupKey]
    }
}
