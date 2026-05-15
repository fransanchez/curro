# US-021 — SF-3.3 · `domain/catalog/` Fase-1 catalog + `FunctionCallPromptBuilder`

> **Spec trace:** spec §5 Fase 1 (the 7 catalog functions, in order),
> §4.3 (prompt context).
> **Master-plan:** SF-3.3
> **Phase:** 3 — FunctionGemma decision layer
> **Depends on:** US-020 only by name (`PromptContext` is co-defined with this
> SF — see Implementation Notes); otherwise stands alone.
> **Size:** M

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `domain/catalog/` Fase-1 catalog + `FunctionCallPromptBuilder` |
| **US ID** | US-021 |
| **Phase** | 3 |
| **Status** | In Progress |
| **Created** | 2026-05-15 |
| **Modified** | 2026-05-15 |
| **PM Owner** | android-product-analyst |
| **Architect** | ondevice-ai-engineer |

---

## 1. Summary

The Fase-1 function catalog — 7 entries: `tell_time, open_app, calculate, help,
read_last_whatsapp, read_all_unread_whatsapp, call_contact` — is mirrored from
the `function-catalog` skill into pure-Kotlin data in `domain/catalog/`. A
`FunctionCallPromptBuilder` renders that catalog plus minimal context
(`nowIso`, `unreadMessagesSummary`, `knownAliases`) into a deterministic
prompt for FunctionGemma. The prompt is short on purpose — every token
competes with accuracy on a 270M model — and is pinned by byte-for-byte golden
tests so the prompt template is a contract, not a knob.

Why this matters for *this* user: he doesn't see the prompt, but every spoken
phrase he produces gets passed through this catalog rendering. The clearer and
more compact this prompt, the higher the chance FunctionGemma maps "qué hora
es" to `tell_time` instead of hallucinating `translate`.

---

## 2. Scope

**In scope:**

- `domain/catalog/CatalogFunction.kt` — `CatalogFunction`, `CatalogParam`,
  `ParamType` (sealed), `NeedsConfirmation` (enum).
- `domain/catalog/Fase1Catalog.kt` — `object Fase1Catalog { val functions:
  List<CatalogFunction> = listOf(tellTime, openApp, …) }`. 7 entries, in spec
  §14 order.
- `data/ml/FunctionCallPromptBuilder.kt` — `@Singleton class …` with
  `fun build(utterance: String, ctx: PromptContext): String`.
- `domain/model/PromptContext.kt` — co-defined here (also in US-020's scope —
  whichever lands first defines it; the other treats it as existing). See
  Implementation Notes for the order-of-commits guidance.
- 3 golden-string tests pinning the prompt rendering byte-for-byte.
- 2 sanity tests asserting catalog shape (size, names, Spanish strings match
  the skill).

**Out of scope:**

- The MediaPipe engine — US-020 (this SF is consumed by it).
- The validator — US-022.
- Any Fase 2/3/4 catalog entry — explicitly **not** added to the prompt; every
  token competes with Fase-1 accuracy on a 270M model.
- The TTS / STT pipeline — already done in Phase 2.
- Internationalisation of the prompt — Curro is Spanish-only.

---

## 3. User Flows

This SF is invisible to the end user. Developer-facing flow:

### Flow 1 — Engine builds the prompt for a real utterance

1. The launcher's smoke loop (US-024) receives `Event.Final("qué hora es")`.
2. It constructs a `PromptContext(nowIso = "2026-05-15T22:36:00",
   unreadMessagesSummary = "", knownAliases = emptyList())`.
3. The injected `FunctionGemmaEngine` (US-020) calls
   `promptBuilder.build("qué hora es", ctx)`.
4. Builder returns the pinned template string (see §8.5 below).
5. The engine passes that string to MediaPipe's `generateResponse`.

### Flow 2 — Adversarial utterance with prompt-delimiter characters

1. User says something transcribed as `"léeme «esto»"`.
2. Builder sanitises the `«` and `»` to `'` before interpolation.
3. The rendered prompt has `Frase del usuario: «léeme 'esto'»` — no delimiter
   confusion. (Decision: replace, do not strip. Stripping risks losing tokens
   the user actually said; replacing preserves the audible content with a
   safe character.)

---

## 4. Function-catalog Impact

This SF **is** the catalog. The 7 Fase-1 functions are defined here in
Kotlin, exactly matching the `function-catalog` skill.

| Name | Description (verbatim) | Params | needs_confirmation | Phase |
|---|---|---|---|---|
| `tell_time` | "Dice en voz alta la hora actual, el día de la semana y/o la fecha." | `what: enum(time\|date\|day\|all)` optional default `all` | `NO` | 1 |
| `open_app` | "Abre cualquier app instalada en el teléfono, identificada por nombre coloquial." | `app_name: string` required | `NO` | 1 |
| `calculate` | "Resuelve una operación matemática expresada en lenguaje natural y la lee en voz alta." | `expression: string` required | `NO` | 1 |
| `help` | "Explica al usuario qué cosas puede hacer Curro." | `topic: string` optional | `NO` | 1 |
| `read_last_whatsapp` | "Lee en voz alta el último mensaje de WhatsApp recibido (opcionalmente de un remitente concreto)." | `sender: string` optional | `NO` | 1 |
| `read_all_unread_whatsapp` | "Lee todos los mensajes de WhatsApp no leídos, agrupados por remitente." | (none) | `NO` | 1 |
| `call_contact` | "Inicia una llamada telefónica a un contacto resuelto por nombre o alias." | `contact: string` required | `CONDITIONAL` | 1 |

`voice_examples` are taken verbatim from the `function-catalog` skill (the
exact lists are in §8.4 below). **Implementation order** (spec §14): `tell_time,
open_app, calculate, help, read_last_whatsapp, read_all_unread_whatsapp,
call_contact` — the `Fase1Catalog.functions` list preserves this order.

**Three-way sync rule** (function-catalog skill "Rules" §1): the skill, spec
§5, and `domain/catalog/` must agree. Verified by:

- A diff-style AC: the brief lists each Spanish string in §8.4; the developer
  copies them verbatim from the skill.
- A runtime test: `Fase1CatalogTest.kt` asserts each `CatalogFunction.name`
  matches a hand-coded list and each `description` matches a hand-coded
  string (so any drift in the implementation file vs. the test ID is caught
  at CI time).

---

## 5. FSM States Touched

**None.** Pure data + a string builder.

---

## 6. Android System Integrations & Permissions

**None.** `FunctionCallPromptBuilder` is pure Kotlin; no Android imports.

---

## 7. On-device-model Impact

- **Changes the prompt FunctionGemma sees.** Specifically: it defines what the
  prompt contains. Every line you add is paid for in accuracy.
- **Token budget**: target < 600 tokens on the empty-context "qué hora es"
  case. Estimated via `words × 1.3` (a rough English/Spanish factor for
  sub-word tokenisation) — the test enforces it.
- **No new model load.** The catalog is data; FunctionGemma is loaded by
  US-020.
- **Latency budget**: building the prompt is microseconds — negligible against
  the < 500 ms inference budget.

---

## 8. Android Specification

### 8.1 Files added

- `app/src/main/java/com/curro/app/domain/catalog/CatalogFunction.kt`
- `app/src/main/java/com/curro/app/domain/catalog/Fase1Catalog.kt`
- `app/src/main/java/com/curro/app/data/ml/FunctionCallPromptBuilder.kt`
- `app/src/main/java/com/curro/app/domain/model/PromptContext.kt` (only if
  US-020 hasn't landed it; see §13 for ordering)
- `app/src/test/java/com/curro/app/domain/catalog/Fase1CatalogTest.kt`
- `app/src/test/java/com/curro/app/data/ml/FunctionCallPromptBuilderTest.kt`
- `app/src/test/resources/golden/prompt_tell_time_empty_context.txt`
- `app/src/test/resources/golden/prompt_call_contact_populated_context.txt`
- `app/src/test/resources/golden/prompt_with_delimiter_chars.txt`

### 8.2 `CatalogFunction` — exact shape

```kotlin
package com.curro.app.domain.catalog

/**
 * One catalog function (spec §5). Mirrors the `function-catalog` skill
 * machine-readable shape.
 *
 * The catalog lives in **three places that must stay in sync** (skill
 * "Rules" §1): the `function-catalog` skill ⇄ `docs/curro-spec-v1.0.md` §5 ⇄
 * this file. `/add-function <name>` is the tool that keeps them aligned.
 */
data class CatalogFunction(
    /** snake_case, e.g. `"tell_time"`. */
    val name: String,
    /** One terse Spanish sentence — the model sees this. */
    val description: String,
    /** Declared parameters; order is documentation, not load-bearing. */
    val params: List<CatalogParam>,
    /** Confirmation policy (spec §4.3). */
    val needsConfirmation: NeedsConfirmation,
    /** 4–6 short Spanish phrases that exemplify how the user phrases this action. */
    val voiceExamples: List<String>,
)

data class CatalogParam(
    /** snake_case. */
    val name: String,
    val type: ParamType,
    val required: Boolean,
    /** One Spanish phrase — the model sees this. */
    val description: String,
    /** Default value as a JSON literal; `null` for required params. */
    val defaultValue: String? = null,
)

sealed interface ParamType {
    /** A free-form string. */
    data object Str : ParamType
    /** A 32-bit integer. */
    data object Int : ParamType
    /** A string restricted to one of the declared values. */
    data class Enum(val values: List<String>) : ParamType
}

enum class NeedsConfirmation {
    /** Execute always; no confirmation. */
    NO,

    /** Always confirm. */
    YES,

    /**
     * Confirmation depends on confidence (spec §4.3): ≥ 0.85 execute;
     * 0.60–0.85 confirm; < 0.60 clarify. Always escalates to mandatory
     * confirmation on ambiguity / irreversible cost / "always confirm" toggle.
     */
    CONDITIONAL,
}
```

### 8.3 `Fase1Catalog` — exact shape (Kotlin)

```kotlin
package com.curro.app.domain.catalog

/**
 * The Fase-1 (prototype MVP) function catalog (spec §5, §14, function-catalog
 * skill).
 *
 * **Order matters**: spec §14 implementation order — the first four validate
 * the architecture at zero risk, the last three touch sensitive permissions.
 *
 * Any change here MUST be mirrored in:
 *   1. The `function-catalog` skill (`.claude/skills/function-catalog/SKILL.md`).
 *   2. `docs/curro-spec-v1.0.md` §5.
 *
 * Use `/add-function <name>` to keep them aligned.
 */
object Fase1Catalog {

    val functions: List<CatalogFunction> = listOf(
        tellTime,
        openApp,
        calculate,
        help,
        readLastWhatsApp,
        readAllUnreadWhatsApp,
        callContact,
    )

    private val tellTime = CatalogFunction(
        name = "tell_time",
        description = "Dice en voz alta la hora actual, el día de la semana y/o la fecha.",
        params = listOf(
            CatalogParam(
                name = "what",
                type = ParamType.Enum(listOf("time", "date", "day", "all")),
                required = false,
                description = "qué información dar",
                defaultValue = "all",
            ),
        ),
        needsConfirmation = NeedsConfirmation.NO,
        voiceExamples = listOf(
            "qué hora es",
            "qué día es hoy",
            "qué fecha es",
            "dime el día",
        ),
    )

    private val openApp = CatalogFunction(
        name = "open_app",
        description = "Abre cualquier app instalada en el teléfono, identificada por nombre coloquial.",
        params = listOf(
            CatalogParam(
                name = "app_name",
                type = ParamType.Str,
                required = true,
                description = "nombre coloquial de la app (\"las fotos\", \"el correo\", \"WhatsApp\")",
            ),
        ),
        needsConfirmation = NeedsConfirmation.NO,
        voiceExamples = listOf(
            "abre la cámara",
            "abre WhatsApp",
            "ponme las fotos",
            "abre el correo",
        ),
    )

    private val calculate = CatalogFunction(
        name = "calculate",
        description = "Resuelve una operación matemática expresada en lenguaje natural y la lee en voz alta.",
        params = listOf(
            CatalogParam(
                name = "expression",
                type = ParamType.Str,
                required = true,
                description = "operación en lenguaje natural",
            ),
        ),
        needsConfirmation = NeedsConfirmation.NO,
        voiceExamples = listOf(
            "cuánto es cuarenta y siete por ocho",
            "calcula mil dividido entre veinticinco",
            "cuánto suma quince y veintitrés",
            "el veintiuno por ciento de doscientos",
        ),
    )

    private val help = CatalogFunction(
        name = "help",
        description = "Explica al usuario qué cosas puede hacer Curro.",
        params = listOf(
            CatalogParam(
                name = "topic",
                type = ParamType.Str,
                required = false,
                description = "sobre qué quiere ayuda específicamente",
            ),
        ),
        needsConfirmation = NeedsConfirmation.NO,
        voiceExamples = listOf(
            "qué puedes hacer",
            "ayuda",
            "qué sabes hacer",
            "cómo te pido cosas",
        ),
    )

    private val readLastWhatsApp = CatalogFunction(
        name = "read_last_whatsapp",
        description = "Lee en voz alta el último mensaje de WhatsApp recibido (opcionalmente de un remitente concreto).",
        params = listOf(
            CatalogParam(
                name = "sender",
                type = ParamType.Str,
                required = false,
                description = "nombre del remitente",
            ),
        ),
        needsConfirmation = NeedsConfirmation.NO,
        voiceExamples = listOf(
            "léeme el último mensaje",
            "qué dice Pepito",
            "léeme lo de mi hija",
            "tengo mensajes nuevos",
        ),
    )

    private val readAllUnreadWhatsApp = CatalogFunction(
        name = "read_all_unread_whatsapp",
        description = "Lee todos los mensajes de WhatsApp no leídos, agrupados por remitente.",
        params = emptyList(),
        needsConfirmation = NeedsConfirmation.NO,
        voiceExamples = listOf(
            "léeme todos los mensajes",
            "qué tengo sin leer",
            "qué mensajes hay",
        ),
    )

    private val callContact = CatalogFunction(
        name = "call_contact",
        description = "Inicia una llamada telefónica a un contacto resuelto por nombre o alias.",
        params = listOf(
            CatalogParam(
                name = "contact",
                type = ParamType.Str,
                required = true,
                description = "nombre del contacto o alias aprendido",
            ),
        ),
        needsConfirmation = NeedsConfirmation.CONDITIONAL,
        voiceExamples = listOf(
            "llama a Pepito",
            "llámame a mi hija",
            "ponme con el médico",
            "marca el número de Carmen",
        ),
    )
}
```

### 8.4 `FunctionCallPromptBuilder` — exact shape

```kotlin
package com.curro.app.data.ml

import com.curro.app.domain.catalog.CatalogFunction
import com.curro.app.domain.catalog.CatalogParam
import com.curro.app.domain.catalog.Fase1Catalog
import com.curro.app.domain.catalog.NeedsConfirmation
import com.curro.app.domain.catalog.ParamType
import com.curro.app.domain.model.PromptContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders FunctionGemma's prompt: a header, the current-phase catalog, minimal
 * context, and the user's utterance.
 *
 * Determinism is a contract. Given the same inputs, the output is identical
 * — pinned by byte-for-byte golden tests against
 * `src/test/resources/golden/prompt_*.txt`.
 *
 * Token budget: < 600 model-tokens on the empty-context happy path (estimated
 * via word-count × 1.3). Every line costs accuracy on a 270M model; do not
 * add lines without measurement.
 */
@Singleton
class FunctionCallPromptBuilder @Inject constructor() {

    fun build(utterance: String, ctx: PromptContext): String {
        val safe = sanitise(utterance)
        return buildString {
            append(HEADER)
            append('\n')
            append(actionsBlock(Fase1Catalog.functions))
            append('\n')
            append(contextBlock(ctx))
            append('\n')
            append("Frase del usuario: «").append(safe).append("»\n")
            append('\n')
            append("JSON:")
        }
    }

    /** Replace `«` and `»` with `'` so they cannot collide with the delimiter. */
    private fun sanitise(utterance: String): String =
        utterance.replace('«', '\'').replace('»', '\'')

    private fun actionsBlock(fns: List<CatalogFunction>): String = buildString {
        append("Acciones disponibles:\n")
        for (fn in fns) {
            append("- ").append(fn.name)
            if (fn.params.isNotEmpty()) {
                append('(').append(renderParams(fn.params)).append(')')
            } else {
                append("()")
            }
            append(": ").append(fn.description).append('\n')
            append("  Ejemplos: ")
            append(fn.voiceExamples.joinToString(", ") { "\"$it\"" })
            append('\n')
        }
    }

    private fun renderParams(params: List<CatalogParam>): String =
        params.joinToString(", ") { p ->
            val optMark = if (p.required) "" else "?"
            val typeStr = when (val t = p.type) {
                is ParamType.Str -> "string"
                is ParamType.Int -> "int"
                is ParamType.Enum -> t.values.joinToString("|")
            }
            "${p.name}$optMark: $typeStr"
        }

    private fun contextBlock(ctx: PromptContext): String = buildString {
        append("Contexto:\n")
        append("- Hora actual: ").append(ctx.nowIso).append('\n')
        append("- Mensajes sin leer: ")
            .append(ctx.unreadMessagesSummary.ifBlank { "ninguno" })
            .append('\n')
        append("- Alias conocidos: ")
            .append(if (ctx.knownAliases.isEmpty()) "ninguno" else ctx.knownAliases.joinToString("; "))
            .append('\n')
    }

    private companion object {
        const val HEADER = """Eres Curro. Dada una frase del usuario, devuelves UN ÚNICO JSON con la forma:
{"action": "<nombre>", "params": {…}, "confidence": <0.0-1.0>}

Si la frase no encaja con ninguna acción, devuelve confidence < 0.3 con la mejor adivinanza."""
    }
}
```

### 8.5 The pinned prompt template — example rendering

Given `build("qué hora es", PromptContext(nowIso = "2026-05-15T22:36:00",
unreadMessagesSummary = "", knownAliases = emptyList()))`, the exact byte-for-byte
output (stored in `src/test/resources/golden/prompt_tell_time_empty_context.txt`)
is:

```
Eres Curro. Dada una frase del usuario, devuelves UN ÚNICO JSON con la forma:
{"action": "<nombre>", "params": {…}, "confidence": <0.0-1.0>}

Si la frase no encaja con ninguna acción, devuelve confidence < 0.3 con la mejor adivinanza.

Acciones disponibles:
- tell_time(what?: time|date|day|all): Dice en voz alta la hora actual, el día de la semana y/o la fecha.
  Ejemplos: "qué hora es", "qué día es hoy", "qué fecha es", "dime el día"
- open_app(app_name: string): Abre cualquier app instalada en el teléfono, identificada por nombre coloquial.
  Ejemplos: "abre la cámara", "abre WhatsApp", "ponme las fotos", "abre el correo"
- calculate(expression: string): Resuelve una operación matemática expresada en lenguaje natural y la lee en voz alta.
  Ejemplos: "cuánto es cuarenta y siete por ocho", "calcula mil dividido entre veinticinco", "cuánto suma quince y veintitrés", "el veintiuno por ciento de doscientos"
- help(topic?: string): Explica al usuario qué cosas puede hacer Curro.
  Ejemplos: "qué puedes hacer", "ayuda", "qué sabes hacer", "cómo te pido cosas"
- read_last_whatsapp(sender?: string): Lee en voz alta el último mensaje de WhatsApp recibido (opcionalmente de un remitente concreto).
  Ejemplos: "léeme el último mensaje", "qué dice Pepito", "léeme lo de mi hija", "tengo mensajes nuevos"
- read_all_unread_whatsapp(): Lee todos los mensajes de WhatsApp no leídos, agrupados por remitente.
  Ejemplos: "léeme todos los mensajes", "qué tengo sin leer", "qué mensajes hay"
- call_contact(contact: string): Inicia una llamada telefónica a un contacto resuelto por nombre o alias.
  Ejemplos: "llama a Pepito", "llámame a mi hija", "ponme con el médico", "marca el número de Carmen"

Contexto:
- Hora actual: 2026-05-15T22:36:00
- Mensajes sin leer: ninguno
- Alias conocidos: ninguno

Frase del usuario: «qué hora es»

JSON:
```

**Token-budget estimate**: ~290 words × 1.3 ≈ 377 tokens — well under the 600
budget. (The test computes this from the actual rendered string.)

### 8.6 Second golden — populated context, `call_contact`

Given `build("llama a mi hija", PromptContext(nowIso = "2026-05-15T22:36:00",
unreadMessagesSummary = "3 de Pepito, 1 de Lucía", knownAliases =
listOf("mi hija → Lucía Ruiz", "el médico → Dr. Soriano")))`, the rendered
prompt differs only in the context block and the utterance:

```
Contexto:
- Hora actual: 2026-05-15T22:36:00
- Mensajes sin leer: 3 de Pepito, 1 de Lucía
- Alias conocidos: mi hija → Lucía Ruiz; el médico → Dr. Soriano

Frase del usuario: «llama a mi hija»
```

Stored verbatim in `prompt_call_contact_populated_context.txt`.

### 8.7 Third golden — delimiter-character sanitisation

Given `build("léeme «esto» y dime", PromptContext(nowIso =
"2026-05-15T22:36:00", unreadMessagesSummary = "", knownAliases =
emptyList()))`, the rendered utterance line is:

```
Frase del usuario: «léeme 'esto' y dime»
```

The `«` and `»` characters inside the utterance are replaced with `'`. Stored
verbatim in `prompt_with_delimiter_chars.txt`.

### 8.8 Tests

`Fase1CatalogTest.kt`:

```kotlin
package com.curro.app.domain.catalog

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class Fase1CatalogTest {

    @Test fun `catalog has exactly 7 functions in spec §14 order`() {
        val expected = listOf(
            "tell_time", "open_app", "calculate", "help",
            "read_last_whatsapp", "read_all_unread_whatsapp", "call_contact",
        )
        assertEquals(expected, Fase1Catalog.functions.map { it.name })
    }

    @Test fun `every function has at least one voice example`() {
        Fase1Catalog.functions.forEach { fn ->
            assertTrue(fn.voiceExamples.isNotEmpty(), "${fn.name} has no examples")
        }
    }

    @Test fun `call_contact is the only CONDITIONAL function`() {
        val conditional = Fase1Catalog.functions.filter { it.needsConfirmation == NeedsConfirmation.CONDITIONAL }
        assertEquals(listOf("call_contact"), conditional.map { it.name })
    }

    @Test fun `read_all_unread_whatsapp is the only function with no params`() {
        val paramless = Fase1Catalog.functions.filter { it.params.isEmpty() }
        assertEquals(listOf("read_all_unread_whatsapp"), paramless.map { it.name })
    }

    @Test fun `tell_time what enum has exactly time date day all`() {
        val tellTime = Fase1Catalog.functions.first { it.name == "tell_time" }
        val whatParam = tellTime.params.first { it.name == "what" }
        val enum = whatParam.type as ParamType.Enum
        assertEquals(listOf("time", "date", "day", "all"), enum.values)
    }
}
```

`FunctionCallPromptBuilderTest.kt`:

```kotlin
package com.curro.app.data.ml

import com.curro.app.domain.model.PromptContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class FunctionCallPromptBuilderTest {

    private val builder = FunctionCallPromptBuilder()

    @Test fun `golden — empty context, tell_time utterance`() {
        val out = builder.build(
            "qué hora es",
            PromptContext(nowIso = "2026-05-15T22:36:00", unreadMessagesSummary = "", knownAliases = emptyList()),
        )
        val expected = loadGolden("prompt_tell_time_empty_context.txt")
        assertEquals(expected, out)
    }

    @Test fun `golden — populated context, call_contact utterance`() {
        val out = builder.build(
            "llama a mi hija",
            PromptContext(
                nowIso = "2026-05-15T22:36:00",
                unreadMessagesSummary = "3 de Pepito, 1 de Lucía",
                knownAliases = listOf("mi hija → Lucía Ruiz", "el médico → Dr. Soriano"),
            ),
        )
        val expected = loadGolden("prompt_call_contact_populated_context.txt")
        assertEquals(expected, out)
    }

    @Test fun `golden — utterance with « and » is sanitised`() {
        val out = builder.build(
            "léeme «esto» y dime",
            PromptContext(nowIso = "2026-05-15T22:36:00", unreadMessagesSummary = "", knownAliases = emptyList()),
        )
        val expected = loadGolden("prompt_with_delimiter_chars.txt")
        assertEquals(expected, out)
    }

    @Test fun `token budget — empty context tell_time is well under 600`() {
        val out = builder.build(
            "qué hora es",
            PromptContext(nowIso = "2026-05-15T22:36:00", unreadMessagesSummary = "", knownAliases = emptyList()),
        )
        val wordCount = out.split(Regex("\\s+")).size
        val tokenEstimate = (wordCount * 1.3).toInt()
        assertTrue(tokenEstimate < 600, "Estimated tokens ${tokenEstimate} exceed budget of 600")
    }

    private fun loadGolden(filename: String): String {
        // KEEP this read trimEnd-free — golden files include the trailing newline.
        return this::class.java.classLoader!!.getResource("golden/$filename")!!.readText()
    }
}
```

---

## 9. Senior-UX & Copy

No user-facing copy in this SF. The prompt is *internal* — the user never sees
it, the model does. Spanish is used because the user speaks Spanish; the model
performs better when the prompt language matches the input language.

---

## 10. Acceptance Criteria

Mirroring PRD entry:

- [ ] `domain/catalog/CatalogFunction.kt` + sealed `ParamType` + `NeedsConfirmation`
  enum exist with the exact shape above.
- [ ] `domain/catalog/Fase1Catalog.kt` has `object Fase1Catalog { val
  functions: List<CatalogFunction> = listOf(7 entries) }` in spec §14 order.
- [ ] `Fase1Catalog.functions.size == 7`.
- [ ] Each function's Spanish description matches the `function-catalog` skill
  verbatim (the brief is the side-by-side source).
- [ ] Each function's `voice_examples` list matches the skill verbatim.
- [ ] `call_contact` is `CONDITIONAL`; the other six are `NO`.
- [ ] `data/ml/FunctionCallPromptBuilder.kt` is `@Singleton`, `@Inject`-able,
  pure Kotlin (no Android imports).
- [ ] Three golden tests pass byte-for-byte against the three fixture files
  in `src/test/resources/golden/`.
- [ ] Token-budget test asserts the empty-context "qué hora es" rendering is
  under 600 estimated tokens.
- [ ] Utterance sanitisation: `«` and `»` in the input replaced with `'`
  before interpolation. No stripping (the audible content is preserved).
- [ ] No new dependency, no permission, no manifest change.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green.

---

## 11. Performance Considerations

- Prompt building is pure string concatenation — microseconds. Not on a hot
  path that matters.
- `Fase1Catalog.functions` is a `val` resolved once at class-load time. No
  per-call allocation beyond the rendered string.
- `buildString` is the right primitive — internally a `StringBuilder` with no
  resizing overhead for the ~3 KB output.

---

## 12. Testing Requirements

- [ ] **Unit**: `Fase1CatalogTest` (5 cases), `FunctionCallPromptBuilderTest`
  (4 cases — 3 golden + 1 token budget).
- [ ] **Golden fixtures**: 3 `.txt` files in `src/test/resources/golden/`.
- [ ] **Manual on the Redmi 15**: not required for this SF. The on-device
  gate (US-024) exercises the prompt indirectly.
- [ ] **Drift check**: if the developer changes any Spanish string, the
  golden tests fail; the developer must consciously update the goldens. This
  is the desired behaviour — drift in the prompt is a contract break, not a
  refactor.

---

## 13. Implementation Notes

### `PromptContext` ownership

`PromptContext` is listed under both US-020 and US-021. Whichever ships
first owns the file; the other treats it as existing. Recommended order
(see "Recommended commit order" in the PM brief): **US-021 ships first**, so
`PromptContext` is added in this SF, in `domain/model/`. US-020 then imports
it as a pre-existing type.

### Why this prompt template

Three design choices, recorded for traceability:

1. **Spanish header**. Spanish input + Spanish output + Spanish prompt = the
   smallest prompt-language mismatch surface. 270M models are sensitive to
   language switches.
2. **Compact action list with single-line examples**. Each action gets a
   single line of signature + one-sentence description and one line of
   example utterances. This is denser than a YAML-style block but still
   parsable by the model; the goldens pin it so we don't drift toward
   verbosity over time.
3. **Always-rendered context block**. Even when `unreadMessagesSummary` and
   `knownAliases` are empty, the lines are rendered with `"ninguno"`. The
   prompt's structural shape is the same on every call, which helps the
   model learn the format from the first few in-domain examples in its
   training data. The cost is ~12 tokens for the two "ninguno" lines.

### `«»` delimiter rationale

The delimiter is `«»` (Spanish-Castilian quotation marks) because:

- It's visually distinctive in the prompt.
- The user's transcribed utterance is highly unlikely to contain `«` or `»`
  naturally — STT doesn't produce them, the user doesn't say them.
- If somehow it does appear, the sanitiser replaces it with `'` rather than
  stripping it, preserving the audible content.

`»` is **not** stripped, **not** removed, **not** escaped — replaced. Decision
pinned.

### Order of operations

1. Add `domain/model/PromptContext.kt`.
2. Add `domain/catalog/CatalogFunction.kt`.
3. Add `domain/catalog/Fase1Catalog.kt` with the 7 entries.
4. Add `data/ml/FunctionCallPromptBuilder.kt`.
5. Create the three golden fixture files.
6. Add the two test files.
7. Run `./gradlew testDebugUnitTest` — golden + catalog tests green.
8. Run `./gradlew assembleDebug ktlintCheck detektDebug` — all green.

### Commit scope

`feat(catalog)` — per `git-workflow` skill, the catalog and its prompt
rendering is its own scope.

---

## 14. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-15 | android-product-analyst | Initial draft for Phase-3 PM batch. |
