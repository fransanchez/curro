package com.curro.app.domain.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Sanity tests for the Fase-1 catalog (US-021 / SF-3.3).
 *
 * The goldens in [com.curro.app.data.ml.FunctionCallPromptBuilderTest] pin the
 * Spanish description and example strings byte-for-byte; this file keeps a
 * narrower contract: the *shape* of the catalog (size, order, types, the one
 * CONDITIONAL function) — so a careless reordering or accidental drop catches
 * here even before the goldens fire.
 */
class Fase1CatalogTest {
    @Test
    fun `catalog has exactly 7 functions in spec 14 order`() {
        val expected =
            listOf(
                "tell_time",
                "open_app",
                "calculate",
                "help",
                "read_last_whatsapp",
                "read_all_unread_whatsapp",
                "call_contact",
            )
        assertEquals(expected, Fase1Catalog.functions.map { it.name })
    }

    @Test
    fun `every function has at least one voice example`() {
        Fase1Catalog.functions.forEach { fn ->
            assertTrue(fn.voiceExamples.isNotEmpty(), "${fn.name} has no examples")
        }
    }

    @Test
    fun `call_contact is the only CONDITIONAL function`() {
        val conditional =
            Fase1Catalog.functions
                .filter { it.needsConfirmation == NeedsConfirmation.CONDITIONAL }
        assertEquals(listOf("call_contact"), conditional.map { it.name })
    }

    @Test
    fun `read_all_unread_whatsapp is the only function with no params`() {
        val paramless = Fase1Catalog.functions.filter { it.params.isEmpty() }
        assertEquals(listOf("read_all_unread_whatsapp"), paramless.map { it.name })
    }

    @Test
    fun `tell_time what enum has exactly time date day all`() {
        val tellTime = Fase1Catalog.functions.first { it.name == "tell_time" }
        val whatParam = tellTime.params.first { it.name == "what" }
        val enum = whatParam.type as ParamType.Enum
        assertEquals(listOf("time", "date", "day", "all"), enum.values)
    }
}
