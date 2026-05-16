package com.curro.app.data.contacts

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ContactsContractProvider] (US-033 / SF-4.9).
 *
 * Uses [FakeContactsQueryRunner] — no real ContentResolver or Cursor is touched.
 */
@DisplayName("ContactsContractProvider (SF-4.9)")
class ContactsContractProviderTest {
    private class FakeContactsQueryRunner(
        val rows: List<ContactsQueryRunner.Row>,
        val lookupKeyRows: List<ContactsQueryRunner.Row> = emptyList(),
    ) : ContactsQueryRunner {
        override suspend fun query(): List<ContactsQueryRunner.Row> = rows

        override suspend fun queryByLookupKey(lookupKey: String): List<ContactsQueryRunner.Row> = lookupKeyRows
    }

    private fun row(
        lookupKey: String,
        displayName: String,
        phoneNumber: String? = "+34600000000",
        photoUri: String? = null,
    ) = ContactsQueryRunner.Row(lookupKey, displayName, phoneNumber, photoUri)

    private fun provider(rows: List<ContactsQueryRunner.Row>) = ContactsContractProvider(FakeContactsQueryRunner(rows))

    // ── 1. Empty rows ─────────────────────────────────────────────────────────

    @Test
    fun `empty rows returns emptyList`() =
        runTest {
            assertTrue(provider(emptyList()).findByName("Pepito").isEmpty())
        }

    // ── 2. Blank / empty query ─────────────────────────────────────────────────

    @Test
    fun `empty query returns emptyList without querying`() =
        runTest {
            val rows = listOf(row("k1", "Pepito"))
            assertTrue(provider(rows).findByName("").isEmpty())
        }

    @Test
    fun `blank query returns emptyList`() =
        runTest {
            val rows = listOf(row("k1", "Pepito"))
            assertTrue(provider(rows).findByName("   ").isEmpty())
        }

    // ── 3. Exact name match ────────────────────────────────────────────────────

    @Test
    fun `exact name match returns 1 contact`() =
        runTest {
            val rows = listOf(row("k1", "Pepito García"))
            val result = provider(rows).findByName("Pepito García")
            assertEquals(1, result.size)
            assertEquals("k1", result[0].lookupKey)
            assertEquals("Pepito García", result[0].displayName)
        }

    // ── 4. Case-insensitive match ──────────────────────────────────────────────

    @Test
    fun `case-insensitive query matches contact`() =
        runTest {
            val rows = listOf(row("k1", "Pepito"))
            val result = provider(rows).findByName("pepito")
            assertEquals(1, result.size)
        }

    // ── 5. Accent-stripped match ───────────────────────────────────────────────

    @Test
    fun `accent-stripped query matches accented display name`() =
        runTest {
            val rows = listOf(row("k1", "José"))
            val result = provider(rows).findByName("jose")
            assertEquals(1, result.size)
            assertEquals("José", result[0].displayName)
        }

    // ── 6. Three Marías — returns 3 contacts ──────────────────────────────────

    @Test
    fun `three Marias with distinct lookupKeys returns 3 contacts`() =
        runTest {
            val rows =
                listOf(
                    row("k1", "María López"),
                    row("k2", "María García"),
                    row("k3", "María Martínez"),
                )
            val result = provider(rows).findByName("María")
            assertEquals(3, result.size)
            assertEquals(setOf("k1", "k2", "k3"), result.map { it.lookupKey }.toSet())
        }

    // ── 7. Multi-token query matches full name ─────────────────────────────────

    @Test
    fun `multi-token query matches full name`() =
        runTest {
            val rows =
                listOf(
                    row("k1", "María García"),
                    row("k2", "María López"),
                )
            val result = provider(rows).findByName("maria garcia")
            assertEquals(1, result.size)
            assertEquals("k1", result[0].lookupKey)
        }

    // ── 8. Same lookupKey × 2 rows × 2 phones → 1 contact, 2 phones ──────────

    @Test
    fun `same lookupKey two rows returns 1 contact with both phone numbers`() =
        runTest {
            val rows =
                listOf(
                    row("k1", "Pepito", phoneNumber = "+34600000001"),
                    row("k1", "Pepito", phoneNumber = "+34600000002"),
                )
            val result = provider(rows).findByName("Pepito")
            assertEquals(1, result.size)
            assertEquals(listOf("+34600000001", "+34600000002"), result[0].phoneNumbers)
        }

    // ── 9. Same lookupKey × same phone → deduped to length 1 ─────────────────

    @Test
    fun `same phone number on two rows is deduplicated`() =
        runTest {
            val rows =
                listOf(
                    row("k1", "Pepito", phoneNumber = "+34600000000"),
                    row("k1", "Pepito", phoneNumber = "+34600000000"),
                )
            val result = provider(rows).findByName("Pepito")
            assertEquals(1, result.size)
            assertEquals(1, result[0].phoneNumbers.size)
        }

    // ── 10. Photo URI propagation ──────────────────────────────────────────────

    @Test
    fun `photo URI is propagated when present`() =
        runTest {
            val rows = listOf(row("k1", "Pepito", photoUri = "content://photo/1"))
            val result = provider(rows).findByName("Pepito")
            assertEquals("content://photo/1", result[0].photoUri)
        }

    @Test
    fun `photo URI is null when absent`() =
        runTest {
            val rows = listOf(row("k1", "Pepito", photoUri = null))
            val result = provider(rows).findByName("Pepito")
            assertNull(result[0].photoUri)
        }

    // ── 11. Null phone-number row → contact with empty phoneNumbers ───────────

    @Test
    fun `null phone number row yields contact with empty phoneNumbers`() =
        runTest {
            val rows = listOf(row("k1", "Pepito", phoneNumber = null))
            val result = provider(rows).findByName("Pepito")
            assertEquals(1, result.size)
            assertTrue(result[0].phoneNumbers.isEmpty())
        }

    // ── 12. No match → emptyList ──────────────────────────────────────────────

    @Test
    fun `no match returns emptyList`() =
        runTest {
            val rows = listOf(row("k1", "Pepito"))
            assertTrue(provider(rows).findByName("Foobar").isEmpty())
        }

    // ── 13. Apostrophe in query is regex-safe ─────────────────────────────────

    @Test
    fun `apostrophe in query is regex-safe and matches`() =
        runTest {
            val rows = listOf(row("k1", "D'Angelo"))
            val result = provider(rows).findByName("D'Angelo")
            assertEquals(1, result.size)
        }

    // ── 14. Word-boundary: "ana" does NOT match "Susana" ──────────────────────

    @Test
    fun `single token word-boundary ana does not match Susana`() =
        runTest {
            val rows = listOf(row("k1", "Susana"))
            assertTrue(provider(rows).findByName("ana").isEmpty())
        }

    // ── 15. Word-boundary: "Ana" DOES match "Ana García" ─────────────────────

    @Test
    fun `single token word-boundary Ana matches Ana Garcia`() =
        runTest {
            val rows = listOf(row("k1", "Ana García"))
            assertEquals(1, provider(rows).findByName("Ana").size)
        }

    // ── 16–18. SF-7.2 / US-046 — findByLookupKey ──────────────────────────────

    @Test
    fun `findByLookupKey unknown key returns null`() =
        runTest {
            // queryByLookupKey returns empty → findByLookupKey returns null
            val p = ContactsContractProvider(FakeContactsQueryRunner(rows = emptyList(), lookupKeyRows = emptyList()))
            assertNull(p.findByLookupKey("lk-x"))
        }

    @Test
    fun `findByLookupKey matching row returns Contact`() =
        runTest {
            val lkRow = row("lk-y", "Lucía Ruiz", "+34600000001")
            val p = ContactsContractProvider(FakeContactsQueryRunner(rows = emptyList(), lookupKeyRows = listOf(lkRow)))
            val contact = p.findByLookupKey("lk-y")
            assertEquals("lk-y", contact!!.lookupKey)
            assertEquals("Lucía Ruiz", contact.displayName)
            assertEquals(listOf("+34600000001"), contact.phoneNumbers)
        }

    @Test
    fun `findByLookupKey empty key returns null`() =
        runTest {
            val p = ContactsContractProvider(FakeContactsQueryRunner(rows = emptyList(), lookupKeyRows = emptyList()))
            assertNull(p.findByLookupKey(""))
        }
}
