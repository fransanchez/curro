package com.curro.app.data.ml

import com.curro.app.domain.catalog.CatalogParam
import com.curro.app.domain.catalog.Fase1Catalog
import com.curro.app.domain.catalog.ParamType
import com.curro.app.domain.model.CurroError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exhaustive tests for [FunctionCallValidator] (US-022 / SF-3.4).
 *
 * Pure JVM (JUnit 5). `org.json.JSONObject` ships as a real implementation in
 * the Android SDK's `core.jar` (Apache Harmony origin) — not a framework stub —
 * and is on the JVM unit-test classpath without Robolectric, so the validator's
 * JSON parsing behaves identically here and on-device.
 *
 * Spec flow 7 says: no automatic retry on failure. Each row below pins a
 * specific failure mode to its [CurroError] mapping so the smoke loop (US-024)
 * can dispatch on the typed result.
 */
class FunctionCallValidatorTest {
    private val v = FunctionCallValidator()

    // ---------- Good (10) ----------

    @Test
    fun `good - tell_time with what time`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{"what":"time"},"confidence":0.92}""")
        val call = r.getOrThrow()
        assertEquals("tell_time", call.action)
        assertEquals(mapOf("what" to "time"), call.params)
        assertEquals(0.92f, call.confidence)
    }

    @Test
    fun `good - tell_time with empty params object is success`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":0.7}""")
        val call = r.getOrThrow()
        assertEquals(emptyMap<String, Any>(), call.params)
    }

    @Test
    fun `good - help with no topic`() {
        val r = v.parseAndValidate("""{"action":"help","params":{},"confidence":0.95}""")
        assertTrue(r.isSuccess)
    }

    @Test
    fun `good - help with topic`() {
        val r = v.parseAndValidate("""{"action":"help","params":{"topic":"mensajes"},"confidence":0.8}""")
        assertEquals(mapOf("topic" to "mensajes"), r.getOrThrow().params)
    }

    @Test
    fun `good - open_app with app_name`() {
        val r =
            v.parseAndValidate("""{"action":"open_app","params":{"app_name":"WhatsApp"},"confidence":0.97}""")
        assertEquals(mapOf("app_name" to "WhatsApp"), r.getOrThrow().params)
    }

    @Test
    fun `good - calculate with expression`() {
        val r =
            v.parseAndValidate("""{"action":"calculate","params":{"expression":"47 * 8"},"confidence":0.85}""")
        assertEquals(mapOf("expression" to "47 * 8"), r.getOrThrow().params)
    }

    @Test
    fun `good - call_contact with contact`() {
        val r =
            v.parseAndValidate(
                """{"action":"call_contact","params":{"contact":"mi hija"},"confidence":0.88}""",
            )
        assertEquals(mapOf("contact" to "mi hija"), r.getOrThrow().params)
    }

    @Test
    fun `good - read_last_whatsapp no sender`() {
        val r =
            v.parseAndValidate("""{"action":"read_last_whatsapp","params":{},"confidence":0.93}""")
        assertTrue(r.isSuccess)
    }

    @Test
    fun `good - read_last_whatsapp with sender`() {
        val r =
            v.parseAndValidate(
                """{"action":"read_last_whatsapp","params":{"sender":"Pepito"},"confidence":0.91}""",
            )
        assertEquals(mapOf("sender" to "Pepito"), r.getOrThrow().params)
    }

    @Test
    fun `good - read_all_unread_whatsapp no params declared`() {
        val r =
            v.parseAndValidate(
                """{"action":"read_all_unread_whatsapp","params":{},"confidence":0.94}""",
            )
        assertTrue(r.isSuccess)
    }

    // ---------- Code-fence stripping (2) ----------

    @Test
    fun `fence - json fence stripped`() {
        val raw = "```json\n{\"action\":\"tell_time\",\"params\":{},\"confidence\":0.9}\n```"
        assertTrue(v.parseAndValidate(raw).isSuccess)
    }

    @Test
    fun `fence - plain fence stripped`() {
        val raw = "```\n{\"action\":\"tell_time\",\"params\":{},\"confidence\":0.9}\n```"
        assertTrue(v.parseAndValidate(raw).isSuccess)
    }

    // ---------- Bad — JSON / shape (5) ----------

    @Test
    fun `bad - malformed JSON (unterminated object)`() {
        // Both Android's org.json and the testImpl `org.json:json` reject this:
        // a missing closing brace and a stray comma is unambiguously malformed.
        val r = v.parseAndValidate("""{"action":"tell_time","params":{}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - not JSON at all`() {
        val r = v.parseAndValidate("hello world")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - missing action key`() {
        val r = v.parseAndValidate("""{"params":{},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - empty action`() {
        val r = v.parseAndValidate("""{"action":"","params":{},"confidence":0.5}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - params not an object`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":"oops","confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - completely empty string`() {
        val r = v.parseAndValidate("")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    // ---------- Bad — unknown / catalog (1) ----------

    @Test
    fun `bad - unknown action returns UnknownFunction`() {
        val r = v.parseAndValidate("""{"action":"summon_dragon","params":{},"confidence":0.9}""")
        val err = r.exceptionOrNull()
        assertTrue(err is CurroError.UnknownFunction)
        assertEquals("summon_dragon", (err as CurroError.UnknownFunction).name)
    }

    // ---------- Bad — required params / types (5) ----------

    @Test
    fun `bad - call_contact missing required contact`() {
        val r = v.parseAndValidate("""{"action":"call_contact","params":{},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - tell_time what wrong type int`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{"what":5},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - call_contact contact wrong type int`() {
        val r =
            v.parseAndValidate("""{"action":"call_contact","params":{"contact":42},"confidence":0.9}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - tell_time extra param`() {
        val r =
            v.parseAndValidate(
                """{"action":"tell_time","params":{"what":"time","frobnicate":true},"confidence":0.9}""",
            )
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - tell_time what not in enum values`() {
        val r =
            v.parseAndValidate(
                """{"action":"tell_time","params":{"what":"yesterday"},"confidence":0.9}""",
            )
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    // ---------- Bad — confidence (4) ----------

    @Test
    fun `bad - confidence above 1`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":1.5}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - confidence below 0`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":-0.1}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - confidence null`() {
        // JSON null surfaces as JSONObject.NULL, not Number → falls through coerce path.
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":null}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `bad - confidence not a number`() {
        val r = v.parseAndValidate("""{"action":"tell_time","params":{},"confidence":"high"}""")
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    // ---------- Drift check ----------

    @Test
    fun `drift - every catalog function has a happy-path validator pass`() {
        Fase1Catalog.functions.forEach { fn ->
            val raw = buildCanonicalJson(fn.name, fn.params)
            val r = v.parseAndValidate(raw)
            assertTrue(
                r.isSuccess,
                "Canonical JSON for ${fn.name} failed: ${r.exceptionOrNull()}",
            )
            assertNotNull(r.getOrThrow().params)
        }
    }

    @Test
    fun `drift - empty string params rejected for Str type`() {
        val r =
            v.parseAndValidate("""{"action":"call_contact","params":{"contact":""},"confidence":0.9}""")
        // An empty contact name can't be resolved; the validator rejects it.
        assertEquals(CurroError.InvalidFunctionCall, r.exceptionOrNull())
    }

    @Test
    fun `drift - validator never returns a CurroError other than the two LLM ones`() {
        // Any failure must be one of: InvalidFunctionCall or UnknownFunction.
        val r = v.parseAndValidate("garbage")
        val err = r.exceptionOrNull()
        assertFalse(err is CurroError.ModelCold)
        assertFalse(err is CurroError.OutOfMemory)
        assertFalse(err is CurroError.PermissionDenied)
    }

    private fun buildCanonicalJson(
        action: String,
        params: List<CatalogParam>,
    ): String {
        val paramsJson =
            params.filter { it.required }.joinToString(",") { p ->
                val v =
                    when (val t = p.type) {
                        is ParamType.Str -> "\"placeholder\""
                        is ParamType.Int -> "42"
                        is ParamType.Enum -> "\"${t.values.first()}\""
                    }
                "\"${p.name}\":$v"
            }
        return """{"action":"$action","params":{$paramsJson},"confidence":0.9}"""
    }
}
