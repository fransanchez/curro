# US-029 — SF-4.5 · `help` handler

> **Spec trace:** spec §5 (catalog entry `help`), spec §2 ("Curro's voice")
> + spec §6 flow 7 (fallback line points to `help`).
> **Master-plan:** SF-4.5.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-025 (`FunctionHandler` + `HandlerModule`).
> **Size:** S.
> **Skills:** `function-catalog`, `brand-design`, `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `help` handler — Curro tells the user what it can do |
| **US ID** | US-029 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-developer |

---

## 1. Summary

Speak the short list of what Curro can do right now, in Curro's voice. The
optional `topic` param narrows the answer to one Fase-1 capability — call,
WhatsApp, apps, calculate, time. Spec §6 flow 7's friendly-fallback line
(`"Eso no lo sé hacer todavía. … o di 'ayuda'."`) explicitly redirects the
user here, so this handler is part of Curro's safety net: every "I can't do
that" path ends with a single, predictable way to find out what Curro CAN
do.

Why this matters for *this* user: the user does not have a phrasebook; he
learns by trying. `"ayuda"` is the one fallback he can always say to get an
honest answer back.

---

## 2. Scope

**In scope:**

- `handler/HelpHandler.kt`.
- `HandlerModule.kt` — append the `@Binds @IntoMap @StringKey("help")` line.
- New `strings.xml` entries: `copy_help_topic_calculate`,
  `copy_help_topic_time`. Reuse the existing `copy_help_generic`,
  `copy_help_topic_call`, `copy_help_topic_whatsapp`, `copy_help_topic_app`.
- ≥ 8 JVM tests on the handler.

**Out of scope:**

- Phase-aware text generation in code — the **strings themselves are the
  phase contract**. When Phase 5+ adds a function, the brief for that SF
  updates `copy_help_generic` and adds a topic-specific string.
- Gemma 3n NL generation. The list is short enough to hand-write.

---

## 3. User Flows

### Flow 1: bare "ayuda"

1. User → STT → `expression = "ayuda"`.
2. FunctionGemma → `{action: "help", params: {}, confidence: 0.97}`.
3. Handler reads `params["topic"]` → `null` → falls through to generic.
4. Speech = `copy_help_generic` ("Puedo llamar a tus contactos, leer tus
   mensajes de WhatsApp, abrir apps, hacer cálculos y decirte la hora.
   Pulsa el botón y dime lo que necesitas.").
5. State → `Idle`.

### Flow 2: "ayuda con las llamadas"

1. FunctionGemma → `{action: "help", params: {topic: "llamadas"}, confidence: 0.92}`.
2. Handler normalises `"llamadas"` → maps to `copy_help_topic_call`.
3. Speech = `"Para llamar, pulsa el botón y di 'llama a' y el nombre de la
   persona, o 'ponme con' y el nombre."`.

### Flow 3: "ayúdame con la calculadora"

1. FunctionGemma → `{action: "help", params: {topic: "calculadora"}}`.
2. Handler normalises → maps to `copy_help_topic_calculate` (NEW).

### Flow 4: unknown topic

1. FunctionGemma → `{action: "help", params: {topic: "el tiempo"}}` (Phase 4
   doesn't speak weather).
2. Handler can't match `"el tiempo"` → falls through to generic. (Pinned: no
   negative response — Curro doesn't say "no sé hablar del tiempo"; the
   user already got `unknown_function` if FunctionGemma had emitted
   `read_weather`. Here, since the model already chose `help`, the right
   move is to give the generic list rather than negating.)

---

## 4. Function-catalog Impact

**No catalog change** — `help` already exists.

---

## 5. FSM States Touched

`Processing → Speaking → Idle`. `needs_confirmation: NO`.

---

## 6. Android System Integrations & Permissions

| Integration / API | Why |
|---|---|
| `Context.getString(@StringRes)` | Read the right Spanish string. |

No new permissions. No manifest changes.

---

## 7. On-device-model Impact

**No model impact.** The strings ARE the phase contract.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/
└── handler/
    └── HelpHandler.kt
```

### 8.2 `HelpHandler.kt`

```kotlin
package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.apps.curroNormalize
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.FunctionCall
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class HelpHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "help"

        override suspend fun handle(call: FunctionCall): HandlerResult {
            val topic =
                (call.params["topic"] as? String).orEmpty().trim().curroNormalize()
            val resId = TOPIC_MAP[topic] ?: R.string.copy_help_generic
            return HandlerResult.Spoken(context.getString(resId))
        }

        private companion object {
            // Curro normalises both sides; keys must be lowercased + accent-stripped.
            val TOPIC_MAP: Map<String, Int> =
                mapOf(
                    // Calls
                    "llamada" to R.string.copy_help_topic_call,
                    "llamadas" to R.string.copy_help_topic_call,
                    "llamar" to R.string.copy_help_topic_call,
                    "telefono" to R.string.copy_help_topic_call,
                    // WhatsApp
                    "mensaje" to R.string.copy_help_topic_whatsapp,
                    "mensajes" to R.string.copy_help_topic_whatsapp,
                    "whatsapp" to R.string.copy_help_topic_whatsapp,
                    "wasap" to R.string.copy_help_topic_whatsapp,
                    // Apps
                    "app" to R.string.copy_help_topic_app,
                    "apps" to R.string.copy_help_topic_app,
                    "aplicacion" to R.string.copy_help_topic_app,
                    "aplicaciones" to R.string.copy_help_topic_app,
                    // Calculate
                    "calculo" to R.string.copy_help_topic_calculate,
                    "calcular" to R.string.copy_help_topic_calculate,
                    "calculadora" to R.string.copy_help_topic_calculate,
                    "cuentas" to R.string.copy_help_topic_calculate,
                    "cuenta" to R.string.copy_help_topic_calculate,
                    "matematicas" to R.string.copy_help_topic_calculate,
                    // Time
                    "hora" to R.string.copy_help_topic_time,
                    "dia" to R.string.copy_help_topic_time,
                    "fecha" to R.string.copy_help_topic_time,
                )
        }
    }
```

**Note**: imports `curroNormalize()` from `data/apps/StringNormalization.kt`
(US-027). If US-027 hasn't landed in the dev pass yet, ship a one-line
fallback (`String.lowercase(Locale("es"))`-only) and refactor when US-027
arrives. Decision pinned: implement US-027 BEFORE US-029 (the commit order
keeps them in §14 order anyway).

### 8.3 `strings.xml` — adds / reuses

Reuse without change:

- `copy_help_generic` — `"Puedo llamar a tus contactos, leer tus mensajes
  de WhatsApp, abrir apps, hacer cálculos y decirte la hora. Pulsa el botón
  y dime lo que necesitas."`
- `copy_help_topic_call` — `"Para llamar, pulsa el botón y di 'llama a' y el
  nombre de la persona, o 'ponme con' y el nombre."`
- `copy_help_topic_whatsapp` — `"Para leer tus mensajes de WhatsApp, pulsa
  el botón y di 'léeme los mensajes', o 'qué dice' y el nombre de quien te
  escribió."`
- `copy_help_topic_app` — `"Para abrir una app, pulsa el botón y di 'abre'
  y el nombre de la app."`

New:

```xml
<!-- US-029 (SF-4.5) — help topic: calculate. Voice: short, gives two example utterances. -->
<string name="copy_help_topic_calculate">Para hacer cuentas, pulsa el botón y dime la operación con palabras: «cuánto es cuarenta y siete por ocho», «calcula mil dividido entre veinticinco».</string>

<!-- US-029 (SF-4.5) — help topic: time/day/date. Voice: short, gives three example utterances. -->
<string name="copy_help_topic_time">Para saber la hora, el día o la fecha, pulsa el botón y di «qué hora es», «qué día es hoy» o «qué fecha es».</string>
```

> **Decision pinned**: the new strings use Spanish guillemets `«»` to wrap
> example utterances (consistent with Castilian typography). The validator
> normalises `«»` → `'` only inside the prompt (US-021 sanitiser); the user
> hears the line via TTS where the guillemets are silent. If TTS reads them
> aloud (unlikely on the system Spanish voice), revisit.

### 8.4 `HandlerModule.kt` — append

```kotlin
@Binds
@IntoMap
@StringKey("help")
abstract fun bindHelpHandler(impl: HelpHandler): FunctionHandler
```

---

## 9. Acceptance Criteria

- [ ] `handler/HelpHandler.kt` exists at the documented path.
- [ ] `HandlerModule` gains the `@Binds @IntoMap @StringKey("help")` line.
- [ ] `strings.xml` gains `copy_help_topic_calculate`, `copy_help_topic_time`.
      Existing entries unchanged.
- [ ] `handle(FunctionCall("help", emptyMap(), 0.95))` → `Spoken(copy_help_generic)`.
- [ ] `handle(FunctionCall("help", mapOf("topic" to "llamadas"), 0.92))` →
      `Spoken(copy_help_topic_call)`.
- [ ] `handle(FunctionCall("help", mapOf("topic" to "WhatsApp"), 0.94))` →
      after normalisation → `copy_help_topic_whatsapp`.
- [ ] `handle(FunctionCall("help", mapOf("topic" to "calculadora"), 0.95))` →
      `copy_help_topic_calculate`.
- [ ] `handle(FunctionCall("help", mapOf("topic" to "hora"), 0.91))` →
      `copy_help_topic_time`.
- [ ] Unknown topic → generic fallback.
- [ ] Accent-stripped: `"matemáticas"` → calculate; `"día"` → time.
- [ ] No new permissions, no manifest changes, no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green.

---

## 10. Senior-UX & Copy

The Curro voice rule for `help`: short, declarative, NEVER an apology. Every
topic string gives one or two example utterances so the user has a concrete
phrase to try next. The example utterances ARE the documentation.

| String ID | Spanish | Voice notes |
|---|---|---|
| `copy_help_generic` | "Puedo llamar a tus contactos, leer tus mensajes de WhatsApp, abrir apps, hacer cálculos y decirte la hora. Pulsa el botón y dime lo que necesitas." | Lists every Fase-1 capability + the action verb the user uses. |
| `copy_help_topic_call` | "Para llamar, pulsa el botón y di 'llama a' y el nombre de la persona, o 'ponme con' y el nombre." | Two phrasings — covers both spec §5 voice examples. |
| `copy_help_topic_whatsapp` | "Para leer tus mensajes de WhatsApp, pulsa el botón y di 'léeme los mensajes', o 'qué dice' y el nombre de quien te escribió." | Both `read_all_unread` and `read_last` example phrasings. |
| `copy_help_topic_app` | "Para abrir una app, pulsa el botón y di 'abre' y el nombre de la app." | Single canonical phrasing. |
| `copy_help_topic_calculate` (NEW) | "Para hacer cuentas, pulsa el botón y dime la operación con palabras: «cuánto es cuarenta y siete por ocho», «calcula mil dividido entre veinticinco»." | Two spec §5 example utterances. |
| `copy_help_topic_time` (NEW) | "Para saber la hora, el día o la fecha, pulsa el botón y di «qué hora es», «qué día es hoy» o «qué fecha es»." | Three spec §5 example utterances. |

---

## 11. Design Notes

No visual surface change. The Speaking overlay reads the same text.

---

## 12. Performance Considerations

- `TOPIC_MAP` is a hash lookup. `O(1)`.
- Total time per `handle()`: sub-millisecond.

---

## 13. Testing Requirements

**`HelpHandlerTest.kt`** — Robolectric (for `Context.getString`).

Cases (≥ 8):

1. Empty `params` → generic.
2. `topic = "llamadas"` → `copy_help_topic_call`.
3. `topic = "WhatsApp"` → `copy_help_topic_whatsapp` (case-insensitive).
4. `topic = "apps"` → `copy_help_topic_app`.
5. `topic = "calculadora"` → `copy_help_topic_calculate`.
6. `topic = "matemáticas"` → `copy_help_topic_calculate` (accent-strip).
7. `topic = "hora"` → `copy_help_topic_time`.
8. `topic = "día"` → `copy_help_topic_time`.
9. `topic = "el tiempo"` → generic (unknown topic, NOT a negative response).
10. `topic = ""` → generic (empty-string fallback).

**On-device verification** on the Redmi 15: `"ayuda"` → Curro speaks the
generic line; `"ayúdame con WhatsApp"` → Curro speaks the WhatsApp line.

---

## 14. Implementation Notes — Order of Operations

1. Verify US-027 is committed before starting (provides `curroNormalize()`).
   If parallel, ship a local lowercase-only fallback and refactor in the
   next commit.
2. Add the two new `strings.xml` entries.
3. Create `handler/HelpHandler.kt`.
4. Append the `@Binds @IntoMap @StringKey("help")` line to `HandlerModule`.
5. Write `HelpHandlerTest`.
6. `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
7. Smoke-test on the Redmi 15: `"ayuda"`, `"ayuda con las llamadas"`.
8. Commit as `feat: add help handler (US-029 / SF-4.5)`.

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
