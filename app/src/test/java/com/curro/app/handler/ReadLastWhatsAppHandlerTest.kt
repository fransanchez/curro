package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.model.WhatsAppMessage.Classification
import com.curro.app.domain.repository.NotificationRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ReadLastWhatsAppHandler] (US-031 / SF-4.7).
 *
 * Uses fake implementations of [NotificationRepository] and [NotificationAccessGate]
 * to avoid Android framework dependencies. Context.getString is stubbed with Mockk.
 */
@DisplayName("ReadLastWhatsAppHandler (SF-4.7)")
class ReadLastWhatsAppHandlerTest {
    private val context: Context = mockk()

    @BeforeEach
    fun setUp() {
        // Sentinel format templates — assert which resource was chosen by inspecting the prefix.
        // Mockk passes vararg format args as a wrapped Object[] at args[1]; unwrap before String.format.
        val templates =
            mapOf(
                R.string.copy_no_unread to "NO_UNREAD",
                R.string.copy_whatsapp_parse_miss to "PARSE_MISS",
                R.string.copy_perm_missing_notifs to "PERM_MISSING",
                R.string.copy_read_last_text to "TEXT:%s:%s",
                R.string.copy_read_last_emoji to "EMOJI:%s",
                R.string.copy_read_last_voice to "VOICE:%s",
                R.string.copy_read_last_image to "IMAGE:%s",
                R.string.copy_no_unread_from to "NO_UNREAD_FROM:%s",
            )
        every { context.getString(any()) } answers { templates[arg<Int>(0)] ?: "" }
        every { context.getString(any(), *anyVararg<Any>()) } answers {
            val template = templates[arg<Int>(0)] ?: ""
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

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeNotificationRepository(
        private val unread: List<WhatsAppMessage> = emptyList(),
        private val missCount: Int = 0,
    ) : NotificationRepository {
        override val allUnread: Flow<List<WhatsAppMessage>> = flowOf(unread)

        override fun unreadBySender(sender: String): Flow<List<WhatsAppMessage>> =
            flowOf(unread.filter { it.sender.equals(sender, ignoreCase = true) })

        override val parseMissCount: Flow<Int> = flowOf(missCount)

        override fun clear(sender: String) = Unit
    }

    private class FakeNotificationAccessGate(private val granted: Boolean) : NotificationAccessGate {
        override fun isGranted(): Boolean = granted
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun handler(
        unread: List<WhatsAppMessage> = emptyList(),
        missCount: Int = 0,
        granted: Boolean = true,
    ) = ReadLastWhatsAppHandler(
        notifications = FakeNotificationRepository(unread, missCount),
        accessGate = FakeNotificationAccessGate(granted),
        context = context,
    )

    private fun call(vararg params: Pair<String, Any>): FunctionCall =
        FunctionCall("read_last_whatsapp", mapOf(*params), confidence = 0.95f)

    private fun msg(
        sender: String,
        text: String,
        timestamp: Long = 1_000L,
        classification: Classification = Classification.TEXT,
        chatTitle: String = sender,
    ) = WhatsAppMessage(
        key = "key-$sender-$timestamp",
        sender = sender,
        chatTitle = chatTitle,
        text = text,
        isGroup = false,
        timestamp = timestamp,
        classification = classification,
    )

    private fun assertSpoken(result: HandlerResult): String {
        assertInstanceOf(HandlerResult.Spoken::class.java, result)
        return (result as HandlerResult.Spoken).speech
    }

    private fun assertFailed(result: HandlerResult): HandlerResult.Failed {
        assertInstanceOf(HandlerResult.Failed::class.java, result)
        return result as HandlerResult.Failed
    }

    // ── 1. Gate denied ────────────────────────────────────────────────────────

    @Test
    fun `gate denied returns Failed with NotificationAccessMissing`() =
        runTest {
            val result = handler(granted = false).handle(call())
            val failed = assertFailed(result)
            assertEquals("PERM_MISSING", failed.speech)
            assertInstanceOf(CurroError.NotificationAccessMissing::class.java, failed.reason)
        }

    // ── 2. Empty cache, no parse-miss → copy_no_unread ───────────────────────

    @Test
    fun `empty cache and no parse-miss returns copy_no_unread`() =
        runTest {
            assertEquals("NO_UNREAD", assertSpoken(handler().handle(call())))
        }

    // ── 3. Empty cache, parse-miss > 0 → copy_whatsapp_parse_miss ────────────

    @Test
    fun `empty cache with parse misses returns copy_whatsapp_parse_miss`() =
        runTest {
            assertEquals("PARSE_MISS", assertSpoken(handler(missCount = 2).handle(call())))
        }

    // ── 4. Single TEXT message, no sender filter ───────────────────────────────

    @Test
    fun `single TEXT message no sender filter returns text speech`() =
        runTest {
            val result = handler(unread = listOf(msg("Pepito", "Te espero a las siete"))).handle(call())
            assertEquals("TEXT:Pepito:Te espero a las siete", assertSpoken(result))
        }

    // ── 5. 3 messages from same sender, no filter → returns most recent ────────

    @Test
    fun `multiple messages no filter returns most recent by timestamp`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "primero", timestamp = 1_000L),
                    msg("Pepito", "segundo", timestamp = 3_000L),
                    msg("Pepito", "tercero", timestamp = 2_000L),
                )
            val result = handler(unread = messages).handle(call())
            assertEquals("TEXT:Pepito:segundo", assertSpoken(result))
        }

    // ── 6. Sender filter hit → reads sender's latest ───────────────────────────

    @Test
    fun `sender filter hit reads that sender latest message`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "primero de Pepito", timestamp = 1_000L),
                    msg("Lucía", "de Lucia", timestamp = 5_000L),
                    msg("Pepito", "segundo de Pepito", timestamp = 2_000L),
                )
            val result = handler(unread = messages).handle(call("sender" to "Pepito"))
            assertEquals("TEXT:Pepito:segundo de Pepito", assertSpoken(result))
        }

    // ── 7. Sender filter case-insensitive ─────────────────────────────────────

    @Test
    fun `sender filter is case-insensitive`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "hola"),
                    msg("Lucía", "que tal"),
                )
            val result = handler(unread = messages).handle(call("sender" to "PEPITO"))
            assertEquals("TEXT:Pepito:hola", assertSpoken(result))
        }

    // ── 8. Sender filter accent-insensitive ───────────────────────────────────

    @Test
    fun `sender filter strips accents for matching`() =
        runTest {
            val messages =
                listOf(
                    msg("José", "sin acento"),
                    msg("Lucía", "otro"),
                )
            val result = handler(unread = messages).handle(call("sender" to "jose"))
            assertEquals("TEXT:José:sin acento", assertSpoken(result))
        }

    // ── 9. Sender filter miss → copy_no_unread_from ───────────────────────────

    @Test
    fun `sender filter miss returns copy_no_unread_from with sender name`() =
        runTest {
            val messages = listOf(msg("Pepito", "hola"))
            val result = handler(unread = messages).handle(call("sender" to "Lucía"))
            assertEquals("NO_UNREAD_FROM:Lucía", assertSpoken(result))
        }

    // ── 10. EMOJI classification ──────────────────────────────────────────────

    @Test
    fun `EMOJI classification returns copy_read_last_emoji`() =
        runTest {
            val result =
                handler(
                    unread = listOf(msg("Pepito", "[emoji]", classification = Classification.EMOJI)),
                ).handle(call())
            assertEquals("EMOJI:Pepito", assertSpoken(result))
        }

    // ── 11. VOICE_NOTE classification ────────────────────────────────────────

    @Test
    fun `VOICE_NOTE classification returns copy_read_last_voice`() =
        runTest {
            val result =
                handler(
                    unread = listOf(msg("Pepito", "[audio]", classification = Classification.VOICE_NOTE)),
                ).handle(call())
            assertEquals("VOICE:Pepito", assertSpoken(result))
        }

    // ── 12. IMAGE classification ──────────────────────────────────────────────

    @Test
    fun `IMAGE classification returns copy_read_last_image`() =
        runTest {
            val result =
                handler(
                    unread = listOf(msg("Pepito", "[foto]", classification = Classification.IMAGE)),
                ).handle(call())
            assertEquals("IMAGE:Pepito", assertSpoken(result))
        }

    // ── 13. OTHER classification → parse miss fallback ────────────────────────

    @Test
    fun `OTHER classification falls through to copy_whatsapp_parse_miss`() =
        runTest {
            val result =
                handler(
                    unread = listOf(msg("Pepito", "unknown", classification = Classification.OTHER)),
                ).handle(call())
            assertEquals("PARSE_MISS", assertSpoken(result))
        }

    // ── 14. Empty sender param treated as no filter ───────────────────────────

    @Test
    fun `empty sender param string treated as no sender filter`() =
        runTest {
            val messages =
                listOf(
                    msg("Pepito", "hola", timestamp = 2_000L),
                    msg("Lucía", "adios", timestamp = 1_000L),
                )
            // "" should not filter — returns overall latest (Pepito at t=2000)
            val result = handler(unread = messages).handle(call("sender" to ""))
            assertEquals("TEXT:Pepito:hola", assertSpoken(result))
        }
}
