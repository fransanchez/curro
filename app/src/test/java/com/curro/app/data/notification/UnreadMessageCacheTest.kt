package com.curro.app.data.notification

import app.cash.turbine.test
import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.model.WhatsAppMessage.Classification
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [UnreadMessageCache] (US-030 / SF-4.6).
 *
 * Pure JVM — no Android, no Mockk. [UnreadMessageCache] depends only on
 * `kotlinx.coroutines` and [WhatsAppMessage].
 */
@DisplayName("UnreadMessageCache (SF-4.6)")
class UnreadMessageCacheTest {
    private lateinit var cache: UnreadMessageCache

    @BeforeEach
    fun setUp() {
        cache = UnreadMessageCache()
    }

    private fun msg(
        key: String = "sbn#1",
        sender: String = "Pepito",
        chatTitle: String = "Pepito",
        text: String = "Hola",
        isGroup: Boolean = false,
        timestamp: Long = 1_000L,
        classification: Classification = Classification.TEXT,
    ) = WhatsAppMessage(
        key = key,
        sender = sender,
        chatTitle = chatTitle,
        text = text,
        isGroup = isGroup,
        timestamp = timestamp,
        classification = classification,
    )

    // ── 1. Single upsert ─────────────────────────────────────────────────────

    @Test
    fun `single upsert appears in allUnread`() =
        runTest {
            val m = msg()
            cache.upsert(m)
            cache.allUnread.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals(m, list.first())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 2. Same-key upsert deduplicates ──────────────────────────────────────

    @Test
    fun `two upserts with the same key keep only the latest`() =
        runTest {
            cache.upsert(msg(key = "sbn#1", text = "Hola"))
            cache.upsert(msg(key = "sbn#1", text = "Adiós"))
            cache.allUnread.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("Adiós", list.first().text)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 3. onRemoved drops all keys starting with sbnKey ─────────────────────

    @Test
    fun `onRemoved drops every row whose key starts with sbnKey`() =
        runTest {
            cache.upsert(msg(key = "sbn#1"))
            cache.upsert(msg(key = "sbn#2", sender = "María"))
            cache.upsert(msg(key = "sbn#1extra"))

            // "sbn#1" is the sbnKey — both "sbn#1" and "sbn#1extra" must go.
            cache.onRemoved("sbn#1")

            cache.allUnread.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("María", list.first().sender)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 4. recordParseMiss increments; same-key idempotent ───────────────────

    @Test
    fun `recordParseMiss increments parseMissCount and is idempotent for the same key`() =
        runTest {
            cache.parseMissCount.test {
                assertEquals(0, awaitItem()) // initial emission

                cache.recordParseMiss("sbn-a")
                assertEquals(1, awaitItem())

                cache.recordParseMiss("sbn-b")
                assertEquals(2, awaitItem())

                // Same key again — must NOT increment.
                cache.recordParseMiss("sbn-a")
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 5. unreadBySender filters case-insensitively ──────────────────────────

    @Test
    fun `unreadBySender returns only messages matching sender case-insensitively`() =
        runTest {
            cache.upsert(msg(key = "k1", sender = "Pepito", chatTitle = "Pepito"))
            cache.upsert(msg(key = "k2", sender = "María", chatTitle = "María"))
            cache.unreadBySender("pepito").test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("Pepito", list.first().sender)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 6. clear drops only the named sender's rows ───────────────────────────

    @Test
    fun `clear removes only rows matching the given sender`() =
        runTest {
            cache.upsert(msg(key = "k1", sender = "Pepito", chatTitle = "Pepito"))
            cache.upsert(msg(key = "k2", sender = "María", chatTitle = "María"))
            cache.clear("Pepito")
            cache.allUnread.test {
                val list = awaitItem()
                assertEquals(1, list.size)
                assertEquals("María", list.first().sender)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 7. onRemoved decrements parseMissCount when the sbnKey was a miss ────

    @Test
    fun `onRemoved for a missed sbnKey decrements parseMissCount`() =
        runTest {
            cache.recordParseMiss("sbn-x")
            cache.parseMissCount.test {
                assertEquals(1, awaitItem())
                cache.onRemoved("sbn-x")
                assertEquals(0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── 8. parseMissCount does not go below zero ──────────────────────────────

    @Test
    fun `parseMissCount never goes below zero on spurious onRemoved`() =
        runTest {
            // No prior miss recorded for this key.
            cache.onRemoved("sbn-unknown")
            cache.parseMissCount.test {
                val count = awaitItem()
                assertTrue(count >= 0)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
