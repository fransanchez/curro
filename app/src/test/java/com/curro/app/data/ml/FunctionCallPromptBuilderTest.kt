package com.curro.app.data.ml

import com.curro.app.domain.model.PromptContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden tests for [FunctionCallPromptBuilder] (US-021 / SF-3.3).
 *
 * The prompt is a *contract*, not a knob: any drift in catalog Spanish, in
 * context wording, or in the rendering itself is caught here. To update a
 * golden the developer must (1) verify the new rendering is intentional and
 * (2) edit the matching file under `src/test/resources/golden/`.
 *
 * Loaded with `readText()` (no trim, no normalisation) — golden files include
 * the trailing whitespace produced by the builder.
 */
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

    private fun loadGolden(filename: String): String =
        requireNotNull(this::class.java.classLoader)
            .getResource("golden/$filename")!!
            .readText()

    private companion object {
        const val TOKEN_PER_WORD_ESTIMATE = 1.3
        const val TOKEN_BUDGET = 600
    }
}
