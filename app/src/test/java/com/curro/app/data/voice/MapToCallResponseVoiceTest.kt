package com.curro.app.data.voice

import com.curro.app.domain.repository.CallResponseVoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * SF-8.7 (US-056) — unit tests for the [mapToCallResponseVoice] post-hoc
 * vocabulary matcher used by [SystemSttClient.listenForCallResponse].
 *
 * The recogniser itself is integration-tested on the device — these JVM
 * tests pin the vocabulary contract. The same shape as
 * `MapToConfirmationVoiceTest` (SF-6.2) and the picker mapper tests
 * (SF-6.3).
 *
 * Vocabulary (pinned in [SystemSttClient]):
 *   - Answer: "sí", "si", "coge", "responde", "contesta", "vale" + prefix
 *     variants ("sí adelante", "coge la llamada", "responde por favor",
 *     "contesta ya").
 *   - Decline: "no", "cuelga", "rechaza", "no contestes", "no respondas"
 *     + prefix variants ("no quiero", "cuelga ya", "rechaza la llamada").
 *   - Other: anything else.
 *   - Failed: empty input.
 */
@DisplayName("mapToCallResponseVoice (SF-8.7)")
class MapToCallResponseVoiceTest {
    @ParameterizedTest
    @ValueSource(strings = ["sí", "si", "coge", "responde", "contesta", "vale"])
    fun `answer vocabulary maps to Answer`(text: String) {
        assertEquals(CallResponseVoice.Answer, mapToCallResponseVoice(text))
    }

    @ParameterizedTest
    @ValueSource(strings = ["Sí", "SI", "  sí  ", "Vale"])
    fun `answer vocabulary is case-insensitive and trimmed`(text: String) {
        assertEquals(CallResponseVoice.Answer, mapToCallResponseVoice(text))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "sí adelante",
            "coge la llamada",
            "responde por favor",
            "contesta ya",
        ],
    )
    fun `answer vocabulary matches prefix variants`(text: String) {
        assertEquals(CallResponseVoice.Answer, mapToCallResponseVoice(text))
    }

    @ParameterizedTest
    @ValueSource(strings = ["no", "cuelga", "rechaza", "no contestes", "no respondas"])
    fun `decline vocabulary maps to Decline`(text: String) {
        assertEquals(CallResponseVoice.Decline, mapToCallResponseVoice(text))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "no quiero hablar",
            "cuelga ya",
            "rechaza la llamada",
        ],
    )
    fun `decline vocabulary matches prefix variants`(text: String) {
        assertEquals(CallResponseVoice.Decline, mapToCallResponseVoice(text))
    }

    @ParameterizedTest
    @ValueSource(strings = ["qué hora es", "abre WhatsApp", "hola"])
    fun `non-matching utterances map to Other carrying the original text`(text: String) {
        val result = mapToCallResponseVoice(text)
        assertInstanceOf(CallResponseVoice.Other::class.java, result)
        assertEquals(text, (result as CallResponseVoice.Other).text)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `empty or blank input maps to Failed`(text: String) {
        val result = mapToCallResponseVoice(text)
        assertInstanceOf(CallResponseVoice.Failed::class.java, result)
    }

    @org.junit.jupiter.api.Test
    fun `accent-stripped si matches even without the diacritic`() {
        // The normaliser strips diacritics — "si" and "sí" both hit ANSWER_VOCAB.
        assertEquals(CallResponseVoice.Answer, mapToCallResponseVoice("si"))
        assertEquals(CallResponseVoice.Answer, mapToCallResponseVoice("sí"))
    }
}
