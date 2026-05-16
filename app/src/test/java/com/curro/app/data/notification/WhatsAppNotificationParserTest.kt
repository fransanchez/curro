package com.curro.app.data.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.curro.app.domain.model.WhatsAppMessage.Classification
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [WhatsAppNotificationParser] (US-030 / SF-4.6).
 *
 * [StatusBarNotification], [Notification], [Bundle], [NotificationCompat.MessagingStyle],
 * and [NotificationCompat.MessagingStyle.Message] are mocked with Mockk.
 *
 * Two seams are used to keep this a pure JVM test:
 *  1. [WhatsAppNotificationParser.messagingStyleExtractor] — replaces the static
 *     `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification` call with
 *     a lambda that returns a pre-built mock style object.
 *  2. [flaggedNotification] — creates a real [Notification] object with its `flags` field
 *     set via reflection, because `Notification.flags` is a Java public field (not a method)
 *     and cannot be stubbed with Mockk's `every { }`. The relaxed-mock approach returns 0
 *     by default, which is correct for non-summary notifications.
 *
 * 22 cases covering all three tiers and every classification variant.
 */
@DisplayName("WhatsAppNotificationParser (SF-4.6)")
class WhatsAppNotificationParserTest {
    private lateinit var parser: WhatsAppNotificationParser

    @BeforeEach
    fun setUp() {
        parser = WhatsAppNotificationParser()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a [Notification] mock whose `.extras` returns [bundle] and whose `.flags`
     * returns [flags]. Because [Notification.flags] is a Java public field (not a method),
     * we use reflection to set it on a real [Notification] instance, then wrap it in a Mockk
     * that delegates [Notification.extras] to [bundle]. For the normal (non-summary) path,
     * [flags] = 0 and a relaxed mock is sufficient.
     */
    private fun notificationWithExtras(
        bundle: Bundle,
        flags: Int = 0,
    ): Notification {
        // Use a real Notification object and set the flags field via reflection.
        // Notification can be constructed without arguments in unit tests.
        val n = Notification()
        n.flags = flags
        // Mockk cannot intercept field accesses, so we stub `extras` via a mock wrapper.
        // Instead of mocking Notification, we return the real one with the extras field set.
        n.extras = bundle
        return n
    }

    private fun sbn(
        key: String = "0|com.whatsapp|1|tag|0",
        packageName: String = "com.whatsapp",
        postTime: Long = 1_700_000_000_000L,
        notification: Notification,
    ): StatusBarNotification {
        val sbn = mockk<StatusBarNotification>(relaxed = true)
        every { sbn.key } returns key
        every { sbn.packageName } returns packageName
        every { sbn.postTime } returns postTime
        every { sbn.notification } returns notification
        return sbn
    }

    /**
     * Convenience builder: assembles a [Bundle] + [Notification] + [StatusBarNotification].
     * [flags] = 0 for normal, = [Notification.FLAG_GROUP_SUMMARY] for summary test.
     */
    private fun fakeSbn(
        key: String = "0|com.whatsapp|1|tag|0",
        packageName: String = "com.whatsapp",
        postTime: Long = 1_700_000_000_000L,
        flags: Int = 0,
        extraTitle: String? = null,
        extraText: String? = null,
        extraTextLines: Array<CharSequence>? = null,
        extraInfoText: String? = null,
    ): StatusBarNotification {
        val bundle = buildBundle(extraTitle, extraText, extraTextLines, extraInfoText)
        val notification = notificationWithExtras(bundle, flags)
        return sbn(key = key, packageName = packageName, postTime = postTime, notification = notification)
    }

    private fun buildBundle(
        title: String?,
        text: String?,
        textLines: Array<CharSequence>?,
        infoText: String?,
    ): Bundle {
        val bundle = mockk<Bundle>(relaxed = true)
        every { bundle.getCharSequence(Notification.EXTRA_TITLE) } returns title
        every { bundle.getCharSequence(Notification.EXTRA_TEXT) } returns text
        every { bundle.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) } returns textLines
        every { bundle.getCharSequence(Notification.EXTRA_INFO_TEXT) } returns infoText
        return bundle
    }

    private fun noMessagingStyle() {
        parser.messagingStyleExtractor = { null }
    }

    private fun withMessagingStyle(
        conversationTitle: CharSequence?,
        isGroup: Boolean,
        messages: List<NotificationCompat.MessagingStyle.Message>,
    ) {
        val style = mockk<NotificationCompat.MessagingStyle>(relaxed = true)
        every { style.conversationTitle } returns conversationTitle
        every { style.isGroupConversation } returns isGroup
        every { style.messages } returns messages
        parser.messagingStyleExtractor = { style }
    }

    private fun styleMessage(
        text: CharSequence,
        timestamp: Long,
        personName: CharSequence? = null,
    ): NotificationCompat.MessagingStyle.Message {
        val msg = mockk<NotificationCompat.MessagingStyle.Message>(relaxed = true)
        every { msg.text } returns text
        every { msg.timestamp } returns timestamp
        if (personName != null) {
            val person = mockk<Person>(relaxed = true)
            every { person.name } returns personName
            every { msg.person } returns person
        } else {
            every { msg.person } returns null
        }
        return msg
    }

    // ── Tier 1: MessagingStyle ─────────────────────────────────────────────────

    @Test
    fun `MS 1to1 single message returns one entry`() {
        val s = fakeSbn(extraTitle = "Pepito")
        withMessagingStyle(
            conversationTitle = null,
            isGroup = false,
            messages = listOf(styleMessage("Te espero a las siete", timestamp = 1_700L)),
        )
        val result = parser.parse(s)
        assertEquals(1, result.size)
        val msg = result.first()
        assertEquals("Pepito", msg.sender)
        assertEquals("Pepito", msg.chatTitle)
        assertEquals("Te espero a las siete", msg.text)
        assertEquals(false, msg.isGroup)
        assertEquals(Classification.TEXT, msg.classification)
    }

    @Test
    fun `MS 1to1 triple returns three entries with increasing timestamps`() {
        val s = fakeSbn(extraTitle = "Pepito")
        withMessagingStyle(
            conversationTitle = null,
            isGroup = false,
            messages =
                listOf(
                    styleMessage("Uno", timestamp = 100L),
                    styleMessage("Dos", timestamp = 200L),
                    styleMessage("Tres", timestamp = 300L),
                ),
        )
        val result = parser.parse(s)
        assertEquals(3, result.size)
        assertEquals(listOf(100L, 200L, 300L), result.map { it.timestamp })
        result.forEach { assertEquals("Pepito", it.sender) }
    }

    @Test
    fun `MS group two senders returns correct isGroup and distinct senders`() {
        val s = fakeSbn(extraTitle = "Familia")
        withMessagingStyle(
            conversationTitle = "Familia",
            isGroup = true,
            messages =
                listOf(
                    styleMessage("Hola", timestamp = 100L, personName = "Pedro"),
                    styleMessage("Buenas", timestamp = 200L, personName = "María"),
                ),
        )
        val result = parser.parse(s)
        assertEquals(2, result.size)
        assertTrue(result.all { it.isGroup })
        assertEquals("Familia", result[0].chatTitle)
        assertEquals("Pedro", result[0].sender)
        assertEquals("María", result[1].sender)
    }

    @Test
    fun `MS group null Person name falls back to chatTitle`() {
        val s = fakeSbn(extraTitle = "Familia")
        withMessagingStyle(
            conversationTitle = "Familia",
            isGroup = true,
            messages = listOf(styleMessage("Hola", timestamp = 100L, personName = null)),
        )
        val result = parser.parse(s)
        assertEquals(1, result.size)
        assertEquals("Familia", result.first().sender)
    }

    @Test
    fun `MS empty messages list falls through to Tier 2`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "Desde Tier 2")
        withMessagingStyle(conversationTitle = "Pepito", isGroup = false, messages = emptyList())
        val result = parser.parse(s)
        // Tier 2 data is present — should parse.
        assertEquals(1, result.size)
        assertEquals("Desde Tier 2", result.first().text)
    }

    // ── Tier 2: Legacy extras ──────────────────────────────────────────────────

    @Test
    fun `legacy extras 1to1 returns single TEXT result`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "Te espero a las siete")
        noMessagingStyle()
        val result = parser.parse(s)
        assertEquals(1, result.size)
        assertEquals("Pepito", result.first().sender)
        assertEquals("Te espero a las siete", result.first().text)
        assertEquals(Classification.TEXT, result.first().classification)
    }

    @Test
    fun `legacy extras EXTRA_TEXT_LINES returns last line as text`() {
        val lines: Array<CharSequence> = arrayOf("Uno", "Dos", "Tres")
        val s = fakeSbn(extraTitle = "Pepito", extraText = null, extraTextLines = lines)
        noMessagingStyle()
        val result = parser.parse(s)
        assertEquals(1, result.size)
        assertEquals("Tres", result.first().text)
    }

    @Test
    fun `missing EXTRA_TITLE with only EXTRA_TEXT returns empty list`() {
        val s = fakeSbn(extraTitle = null, extraText = "Hola")
        noMessagingStyle()
        assertTrue(parser.parse(s).isEmpty())
    }

    @Test
    fun `missing EXTRA_TEXT and no EXTRA_TEXT_LINES returns empty list`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = null, extraTextLines = null)
        noMessagingStyle()
        assertTrue(parser.parse(s).isEmpty())
    }

    // ── Tier 3: Summary ────────────────────────────────────────────────────────

    @Test
    fun `summary notification FLAG_GROUP_SUMMARY returns empty list`() {
        val s = fakeSbn(flags = Notification.FLAG_GROUP_SUMMARY)
        // Tier 3 gate returns before reaching the extractor — no need to configure it.
        assertTrue(parser.parse(s).isEmpty())
    }

    // ── Classification ─────────────────────────────────────────────────────────

    @Test
    fun `emoji-only body returns EMOJI and marker`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "❤️")
        noMessagingStyle()
        val result = parser.parse(s)
        assertEquals(1, result.size)
        assertEquals("[emoji]", result.first().text)
        assertEquals(Classification.EMOJI, result.first().classification)
    }

    @Test
    fun `voice note body via emoji marker returns VOICE_NOTE`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "🎤 Voice message")
        noMessagingStyle()
        val result = parser.parse(s)
        assertEquals("[audio]", result.first().text)
        assertEquals(Classification.VOICE_NOTE, result.first().classification)
    }

    @Test
    fun `voice note via EXTRA_INFO_TEXT hint returns VOICE_NOTE`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "Body irrelevant", extraInfoText = "Voice message, 0:07")
        noMessagingStyle()
        val result = parser.parse(s)
        assertEquals("[audio]", result.first().text)
        assertEquals(Classification.VOICE_NOTE, result.first().classification)
    }

    @Test
    fun `Spanish voice note marker returns VOICE_NOTE`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "🎤 Mensaje de voz")
        noMessagingStyle()
        assertEquals(Classification.VOICE_NOTE, parser.parse(s).first().classification)
    }

    @Test
    fun `image body via emoji marker returns IMAGE`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "📷 Photo")
        noMessagingStyle()
        val result = parser.parse(s)
        assertEquals("[foto]", result.first().text)
        assertEquals(Classification.IMAGE, result.first().classification)
    }

    @Test
    fun `Spanish image marker returns IMAGE`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "📷 Foto")
        noMessagingStyle()
        assertEquals(Classification.IMAGE, parser.parse(s).first().classification)
    }

    @Test
    fun `mixed emoji and text is classified as TEXT`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "🎉 Felicidades")
        noMessagingStyle()
        assertEquals(Classification.TEXT, parser.parse(s).first().classification)
        assertEquals("🎉 Felicidades", parser.parse(s).first().text)
    }

    @Test
    fun `Spanish characters in body are preserved verbatim`() {
        val s = fakeSbn(extraTitle = "Pepito", extraText = "¿Hablamos? ¡Vale!")
        noMessagingStyle()
        val result = parser.parse(s)
        assertEquals("¿Hablamos? ¡Vale!", result.first().text)
        assertEquals(Classification.TEXT, result.first().classification)
    }

    @Test
    fun `big text long body is preserved verbatim`() {
        val body =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
        val s = fakeSbn(extraTitle = "Pepito", extraText = body)
        noMessagingStyle()
        assertEquals(body, parser.parse(s).first().text)
    }

    @Test
    fun `WhatsApp Business package is parsed identically to com-whatsapp`() {
        val s = fakeSbn(packageName = "com.whatsapp.w4b", extraTitle = "Negocio", extraText = "Hola")
        noMessagingStyle()
        val result = parser.parse(s)
        assertEquals(1, result.size)
        assertEquals("Negocio", result.first().sender)
    }

    @Test
    fun `unknown package is still parsed by the parser — package filter lives in the listener`() {
        val s = fakeSbn(packageName = "com.example.fake", extraTitle = "Test", extraText = "Hola")
        noMessagingStyle()
        // The parser does NOT filter by package — that is the listener's job.
        assertEquals(1, parser.parse(s).size)
    }

    @Test
    fun `null notification returns empty list`() {
        val sbn = mockk<StatusBarNotification>(relaxed = true)
        every { sbn.notification } returns null
        assertTrue(parser.parse(sbn).isEmpty())
    }

    @Test
    fun `null extras returns empty list`() {
        val bundle = mockk<Bundle>(relaxed = true)
        every { bundle.getCharSequence(any()) } returns null
        every { bundle.getCharSequenceArray(any()) } returns null
        val notification = notificationWithExtras(bundle)
        val sbn = sbn(notification = notification)
        // With extractor returning null (default is real call, but extras.getCharSequence(EXTRA_TITLE) = null)
        noMessagingStyle()
        assertTrue(parser.parse(sbn).isEmpty())
    }
}
