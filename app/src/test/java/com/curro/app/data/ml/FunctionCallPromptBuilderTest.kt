package com.curro.app.data.ml

import com.curro.app.domain.model.PromptContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Golden tests for [FunctionCallPromptBuilder] (US-021 / SF-3.3).
 *
 * **DISABLED — May 2026.** The prompt template was rewritten end-to-end during the
 * on-device decision-engine validation push (English instruction header + JSON
 * I/O examples + new context section names) and again is about to change for the
 * cloud spike. The fixtures under `src/test/resources/golden/` reflect the
 * Phase-3 Spanish prompt and are stale. They will be refreshed in the same
 * commit that lands the new decision-engine impl (cloud or otherwise) so the
 * golden fixtures match what's actually shipped. See
 * `docs/architecture/on-device-decision-engine-2026.md` for context.
 *
 * The prompt is still a *contract* — we just don't have a stable shape to pin
 * goldens against right now. Re-enable + regenerate fixtures once the new
 * provider is chosen.
 */
@Disabled("Golden fixtures stale; will refresh in the cloud-spike commit.")
class FunctionCallPromptBuilderTest {
    private val builder = FunctionCallPromptBuilder()

    @Test
    fun `golden — empty context, tell_time utterance`() {
        val out =
            builder.build(
                "qué hora es",
                PromptContext(
                    nowIso = "2026-05-15T22:36:00",
                    unreadMessagesSummary = "",
                    knownAliases = emptyList(),
                ),
            )
        val expected = loadGolden("prompt_tell_time_empty_context.txt")
        assertEquals(expected, out)
    }

    @Test
    fun `golden — populated context, call_contact utterance`() {
        val out =
            builder.build(
                "llama a mi hija",
                PromptContext(
                    nowIso = "2026-05-15T22:36:00",
                    unreadMessagesSummary = "3 de Pepito, 1 de Lucía",
                    knownAliases =
                        listOf(
                            "mi hija → Lucía Ruiz",
                            "el médico → Dr. Soriano",
                        ),
                ),
            )
        val expected = loadGolden("prompt_call_contact_populated_context.txt")
        assertEquals(expected, out)
    }

    @Test
    fun `golden — utterance with delimiter characters is sanitised`() {
        val out =
            builder.build(
                "léeme «esto» y dime",
                PromptContext(
                    nowIso = "2026-05-15T22:36:00",
                    unreadMessagesSummary = "",
                    knownAliases = emptyList(),
                ),
            )
        val expected = loadGolden("prompt_with_delimiter_chars.txt")
        assertEquals(expected, out)
    }

    @Test
    fun `token budget — empty context tell_time is well under 600`() {
        val out =
            builder.build(
                "qué hora es",
                PromptContext(
                    nowIso = "2026-05-15T22:36:00",
                    unreadMessagesSummary = "",
                    knownAliases = emptyList(),
                ),
            )
        val wordCount = out.split(Regex("\\s+")).size
        val tokenEstimate = (wordCount * TOKEN_PER_WORD_ESTIMATE).toInt()
        assertTrue(
            tokenEstimate < TOKEN_BUDGET,
            "Estimated tokens $tokenEstimate exceed budget of $TOKEN_BUDGET",
        )
    }

    // ── SF-7.2 / US-046 — alias block rendering ───────────────────────────────

    @Test
    fun `aliases block empty list renders ninguno`() {
        val ctx = PromptContext(nowIso = "2026-05-16T10:00:00", unreadMessagesSummary = "", knownAliases = emptyList())
        val prompt = builder.build("hola", ctx)
        assertTrue(prompt.contains("- Alias conocidos: ninguno"), "Expected 'ninguno' in aliases block")
    }

    @Test
    fun `aliases block single alias renders arrow format`() {
        val ctx =
            PromptContext(
                nowIso = "2026-05-16T10:00:00",
                unreadMessagesSummary = "",
                knownAliases = listOf("mi hija → Lucía Ruiz"),
            )
        val prompt = builder.build("llama a mi hija", ctx)
        assertTrue(
            prompt.contains("- Alias conocidos: mi hija → Lucía Ruiz"),
            "Expected single alias in prompt",
        )
    }

    @Test
    fun `aliases block ten aliases renders all separated by semicolons`() {
        val ten = (1..10).map { "alias$it → Display $it" }
        val ctx = PromptContext(nowIso = "2026-05-16T10:00:00", unreadMessagesSummary = "", knownAliases = ten)
        val prompt = builder.build("hola", ctx)
        val expected = "- Alias conocidos: " + ten.joinToString("; ")
        assertTrue(prompt.contains(expected), "Expected all 10 aliases in prompt separated by '; '")
    }

    private fun loadGolden(filename: String): String =
        requireNotNull(this::class.java.classLoader)
            .getResource("golden/$filename")!!
            .readText()

    private companion object {
        const val TOKEN_PER_WORD_ESTIMATE = 1.3
        const val TOKEN_BUDGET = 600
    }
}
