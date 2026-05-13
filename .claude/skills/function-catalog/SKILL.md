---
name: function-catalog
description: The canonical machine-readable catalog of what Curro can do — the single source FunctionGemma is prompted with, the {action, params, confidence} output contract, the needs_confirmation semantics, and how to add a new function.
triggers:
  - function catalog
  - function catalogue
  - what can Curro do
  - what Curro does
  - FunctionGemma
  - intent
  - function call
  - action
  - needs_confirmation
  - add a function
  - new function
  - handler
  - capabilities
---

# Function Catalog

This is the **canonical source** for Curro's capabilities. It is what FunctionGemma
is prompted with, what `domain/catalog/` must mirror, and what `/add-function`
edits. It traces directly to `docs/curro-spec-v1.0.md` §5 — if you change something
here, change it there (and bump the spec version) and vice versa.

## The output contract

FunctionGemma's job: utterance text + this catalog + minimal context → **exactly one
JSON object**:

```json
{ "action": "<snake_case function name from this catalog>",
  "params":  { "<param>": <value>, … },
  "confidence": 0.0 }
```

- `confidence` ∈ [0, 1] — the model's own estimate.
- The object is **validated against this catalog's JSON Schema** before anything
  runs (action exists in the *current phase*, required params present, types match,
  confidence in range). Invalid → `CurroError.InvalidFunctionCall` → **no automatic
  retry** → speak "Eso no lo sé hacer todavía…" + log the utterance (spec flow 7).
  Valid JSON for a function not in this phase → `CurroError.UnknownFunction` (logged
  separately, so Fran can see "feature requested but not built yet").
- The model returns the confidence; the **`ConfidencePolicy`** (owned by
  `voice-pipeline-engineer`) decides what to do with it — see `voice-interaction`.

## `needs_confirmation` semantics (spec §4.3)

Each function declares one of:

- **`false`** — execute immediately, always. Reversible / consultative actions
  (read, calculate, open an app, tell the time).
- **`true`** — confirm before executing, always. High-criticality actions (e.g.
  sending a message; future: deleting something, sending money).
- **`conditional`** — depends on confidence:
  - `confidence ≥ 0.85` → execute directly ("Llamando a Pepito").
  - `0.60 ≤ confidence < 0.85` → confirm first ("Voy a llamar a Pepito, ¿confirmas?").
  - `confidence < 0.60` → ask to clarify ("No te he entendido bien, ¿quieres llamar a alguien?").

Thresholds (0.85 / 0.60) are **adjustable from the config menu**; these are defaults.

**A `conditional` function always escalates to mandatory confirmation, ignoring
confidence, when:** a param resolves to an **explicit ambiguity** (e.g. three Marías
in contacts, no alias to disambiguate); the action has an **immediate irreversible
cost** (future: a purchase, a money transfer); or the user enabled **"always
confirm"** in the config menu.

## Prompt context (what FunctionGemma sees besides the catalog)

Minimal, always:
- **Current local time** (and date).
- A short **unread-message summary** ("3 de Pepito, 1 de Lucía" — counts + senders, not bodies).
- The list of **known contact aliases** ("mi hija → Lucía Ruiz", …).

Keep it small — the model is 270M; every token of context competes with the catalog.

---

## Catalog — Fase 1 (prototype MVP)

> Implement **in this order** (spec §14): the first four validate the architecture at
> zero risk; the last three touch sensitive permissions.

```yaml
# 1
name: tell_time
description: "Dice en voz alta la hora actual, el día de la semana y/o la fecha."
params:
  - what: { type: "enum(time|date|day|all)", required: false, default: all, desc: "qué información dar" }
voice_examples: ["qué hora es", "qué día es hoy", "qué fecha es", "dime el día"]
needs_confirmation: false
handler: TellTimeHandler
phase: 1

# 2
name: open_app
description: "Abre cualquier app instalada en el teléfono, identificada por nombre coloquial."
params:
  - app_name: { type: string, required: true, desc: "nombre coloquial de la app (\"las fotos\", \"el correo\", \"WhatsApp\")" }
voice_examples: ["abre la cámara", "abre WhatsApp", "ponme las fotos", "abre el correo"]
needs_confirmation: false
handler: OpenAppHandler          # PackageManager + QUERY_ALL_PACKAGES; resolve colloquial name → component
phase: 1

# 3
name: calculate
description: "Resuelve una operación matemática expresada en lenguaje natural y la lee en voz alta."
params:
  - expression: { type: string, required: true, desc: "operación en lenguaje natural" }
voice_examples: ["cuánto es cuarenta y siete por ocho", "calcula mil dividido entre veinticinco", "cuánto suma quince y veintitrés", "el veintiuno por ciento de doscientos"]
needs_confirmation: false
handler: CalculateHandler
phase: 1

# 4
name: help
description: "Explica al usuario qué cosas puede hacer Curro."
params:
  - topic: { type: string, required: false, desc: "sobre qué quiere ayuda específicamente" }
voice_examples: ["qué puedes hacer", "ayuda", "qué sabes hacer", "cómo te pido cosas"]
needs_confirmation: false
handler: HelpHandler
phase: 1

# 5
name: read_last_whatsapp
description: "Lee en voz alta el último mensaje de WhatsApp recibido (opcionalmente de un remitente concreto)."
params:
  - sender: { type: string, required: false, desc: "nombre del remitente" }
voice_examples: ["léeme el último mensaje", "qué dice Pepito", "léeme lo de mi hija", "tengo mensajes nuevos"]
needs_confirmation: false
handler: ReadLastWhatsAppHandler   # NotificationListenerService cache
phase: 1

# 6
name: read_all_unread_whatsapp
description: "Lee todos los mensajes de WhatsApp no leídos, agrupados por remitente."
params: []
voice_examples: ["léeme todos los mensajes", "qué tengo sin leer", "qué mensajes hay"]
needs_confirmation: false
handler: ReadAllUnreadWhatsAppHandler
phase: 1

# 7
name: call_contact
description: "Inicia una llamada telefónica a un contacto resuelto por nombre o alias."
params:
  - contact: { type: string, required: true, desc: "nombre del contacto o alias aprendido" }
voice_examples: ["llama a Pepito", "llámame a mi hija", "ponme con el médico", "marca el número de Carmen"]
needs_confirmation: conditional   # see §4.3; ALWAYS confirm if the name is ambiguous
handler: CallContactHandler        # READ_CONTACTS + CALL_PHONE; resolve name/alias → contact, surface ambiguity
phase: 1
```

> Note: the spec §5 header says "8 funciones" but lists 7 — flag the discrepancy
> with `android-product-analyst` when it next touches the spec; don't invent an 8th.

## Catalog — Fase 2 (communication + device control)

```yaml
name: send_whatsapp_reply
description: "Responde por voz al último mensaje recibido de un contacto."
params:
  - contact: { type: string, required: true, desc: "a quién responder" }
  - message: { type: string, required: true, desc: "contenido dictado" }
voice_examples: ["responde a Pepito que voy en camino", "dile a mi hija que llego tarde", "contesta a Carmen"]
needs_confirmation: true
handler: SendWhatsAppReplyHandler   # NotificationListenerService reply action
phase: 2

name: set_volume
description: "Sube, baja o silencia el volumen del teléfono."
params:
  - direction: { type: "enum(up|down|mute|max)", required: true, desc: "dirección del cambio" }
  - amount:    { type: int, required: false, default: 2, desc: "cuántos pasos" }
voice_examples: ["sube el volumen", "baja el sonido", "más alto", "silencia", "ponlo al máximo"]
needs_confirmation: false
handler: SetVolumeHandler            # AudioManager
phase: 2

# Also planned for Fase 2 (not yet specified in detail): read_sms, set_reminder, read_reminders, dictate_voice_note.
```

## Catalog — Fase 3 / 4 (placeholders — not specified in detail)

- **Fase 3** (need Gemma 3n to reason over content): `summarize_whatsapp_thread`,
  `video_call_contact`, `read_news_headlines` *(needs internet — the only catalog
  function that does)*, `translate_text`, `medication_reminder`.
- **Fase 4** (proactive / contextual): `describe_received_photo` (Gemma 3n
  multimodal), `proactive_alerts`, `explain_current_screen` (Accessibility Service),
  `learn_routine`.

Define these fully (params, examples, `needs_confirmation`, handler) only when the
phase is actually being built.

---

## How to add a function (`/add-function <name>`)

1. **Spec it** with `android-product-analyst`: name (snake_case), one-line
   description, params (name / type / required / default / desc), `voice_examples`,
   `needs_confirmation` (and any always-escalate cases), the phase. Add it to
   `docs/curro-spec-v1.0.md` §5 and to this skill, in the same shape.
2. **Update `domain/catalog/`** so the prompt rendering and the JSON Schema include
   the new function. Keep the schema strict (no extra params, typed params).
3. **Scaffold the handler** (`/create-handler <Name>`): a `FunctionHandler` in
   `handler/` that validates params → resolves references → confirms if needed →
   runs the native action (Intent / Telecom / NotificationListener / AudioManager /
   …) → returns `HandlerResult.Spoken | NeedsConfirmation | Failed`. Register it in
   the handler map (Hilt multibinding keyed by function name).
4. **Tests** (`android-qa-specialist`): the validator accepts the new function's
   good JSON and rejects malformations; the handler maps each outcome (success,
   needs-confirmation, every `HandlerError`) to a plain-Spanish utterance; if it
   touches a permission, the permission-missing path is covered.
5. **Permissions**: if the handler needs a new permission, add it to the manifest +
   the table in `CLAUDE.md` + spec §10, and request it only when this function is
   used.
6. **Copy**: every line the handler can speak goes through resources / the copy
   module, in Curro's voice (`brand-design`) — never hard-coded.

## Rules

1. **One catalog, three places, always in sync**: this skill ⇄ `docs/curro-spec-v1.0.md` §5 ⇄ `domain/catalog/`.
2. **The model returns confidence; the policy decides** — don't bake thresholds into the catalog or the model.
3. **`conditional` always confirms on ambiguity / irreversible cost / "always confirm"** — encode these checks in the handler/coordinator, not just in the policy's number comparison.
4. **Invalid model output is never retried automatically** — surface a friendly fallback, log it, move on (spec flow 7).
5. **Don't add Fase 2/3/4 functions to the prompt before their phase** — every token competes with Fase-1 accuracy on a 270M model.
