---
name: voice-interaction
description: Curro's interaction model — the state machine (idle/listening/processing/confirming/executing/error_recovery), interruption-by-button, the confidence-graded confirmation policy (execute/confirm/clarify thresholds and the always-escalate cases), STT/TTS conventions, the consecutive-failure recovery messages, the 10-second silence cancel, and Curro's spoken voice/personality.
triggers:
  - state machine
  - FSM
  - listening
  - processing
  - confirming
  - error recovery
  - confidence
  - confirmation
  - SpeechRecognizer
  - STT
  - TextToSpeech
  - TTS
  - voice
  - personality
  - tone
  - interrupt
  - recovery
  - mic button
---

# Voice Interaction (the assistant FSM, confirmation, recovery, voice)

How Curro listens, decides whether to confirm, recovers from failure, and speaks.
Pairs with the `voice-pipeline-engineer` agent. Source: `docs/curro-spec-v1.0.md`
§2, §4.1, §4.2, §4.6, §6 (all flows + the diagram), §13. Curro's *copy* is owned by
`brand-design`; this skill is the *behaviour*.

## The state machine

States: **`idle` · `listening` · `processing` · `confirming` · `executing` · `error_recovery`**.

```
              ┌──── button press ──────────►  listening
              │                                  │
              │                              STT finishes
              │                                  │
              │                                  ▼
            idle ◄───── read/exec OK ──────  processing
              ▲                                  │
              │                          ┌───────┼───────┐
              │                          ▼       ▼       ▼
              │                    executing  confirming  error_recovery
              │                          │       │ sí/no  │
              └──────────────────────────┴───────┴────────┘
                          (everything ends in idle)
```

- **Interrupt rule (critical):** a button press in **any** state cancels in-flight
  work — the STT session, the model inference, TTS playback, a pending confirmation —
  and goes straight to `listening`. The user must be able to cut Curro off mid-read.
- After any action completes (or is cancelled), return to `idle`.
- Implement as a `sealed interface AssistantState` with a single owner of transitions
  (`AssistantStateMachine`); the launcher screen observes one `StateFlow<AssistantState>`
  and the listening/processing/confirming/message/picker overlays are *driven by it*,
  **not** by navigation routes.

### What each state shows / says (spec §11)

| State | Screen | Voice |
|---|---|---|
| `idle` | launcher home (clock + mic button + app grid) | silent |
| `listening` | screen tints light blue, "Te escucho…", live transcription in large text below | silent |
| `processing` | "Un momento…" with a **non-animated** indicator (no fussy animation) | silent |
| `confirming` | the resolved target + two **huge** buttons ✅ SÍ / ❌ NO (≥ 96 dp) | "¿Llamo a Pepe Martínez?" |
| `executing` | "Llamando a Pepito" / message cards / picker list — content shown, active item highlighted | "Llamando a Pepito" / reads the messages / "Tienes tres Marías. ¿Cuál?: …" |
| `error_recovery` | "No te he oído" / "No sé hacer eso todavía" | the recovery line (see below) |

The mic button: ≥ 40 % of the screen, big mic icon + large label, **haptic** on
press, colour change + audio-wave visual while `listening` (spec §4.1).

## Confidence-graded confirmation (`ConfidencePolicy`)

Each catalog function declares `needs_confirmation ∈ {false, true, conditional}`
(see `function-catalog`). The policy:

- **`false`** → execute immediately, always.
- **`true`** → confirm before executing, always.
- **`conditional`** → by confidence (defaults; **adjustable in the config menu**):
  - `confidence ≥ 0.85` → execute directly. *"Llamando a Pepito"* and dial.
  - `0.60 ≤ confidence < 0.85` → confirm first. *"Voy a llamar a Pepito, ¿confirmas?"*
  - `confidence < 0.60` → clarify. *"No te he entendido bien, ¿quieres llamar a alguien?"*

**`conditional` always escalates to mandatory confirmation, ignoring confidence,
when:**
1. a param resolves to an **explicit ambiguity** (e.g. three Marías in contacts, no
   alias to disambiguate) — show all candidates + a "Ninguna" option;
2. the action has an **immediate irreversible cost** (future: a purchase, a money
   transfer);
3. the user enabled **"always confirm"** in the config menu (useful early on).

Encode #1–#3 as explicit checks in the handler/coordinator, not just in the number
comparison. The model returns `confidence`; the policy decides — the
`ondevice-ai-engineer` never decides.

### `confirming` behaviour

- Accept **"sí" / "no"** by voice *and* the SÍ / NO taps.
- "no" / NO → "Vale, no llamo" → `idle`.
- **10 s of silence → "Cancelo entonces" → `idle`.** Never wait indefinitely with
  this user.
- For a disambiguation list, the user says/taps a name; a non-matching answer ("la
  primera", "mi prima") with low confidence → repeat the options **once**; second
  miss → give up honestly: "Mejor llámala desde la agenda, no me aclaro." (spec
  flow 3 — don't trap the user in a loop.)

## STT (`SpeechRecognizer`)

- Native, **offline Spanish** (works on Android 12+ with the voice pack installed —
  hence `minSdk 31`). Use `RecognizerIntent` / `SpeechRecognizer` with partial
  results for the live transcription.
- Empty result or `ERROR_NO_MATCH` / `ERROR_SPEECH_TIMEOUT` (or other errors) →
  `error_recovery` (don't show a code, ever).
- Consecutive-failure policy (counter resets on any success):
  - **1st fail:** "No te he oído bien, ¿puedes repetirlo?"
  - **2nd fail:** "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto."
  - **3rd fail:** "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo." → `idle`, counter reset.
  This kills the infinite "no te entiendo" loop — the worst experience for this user.
- For confirmations, a short STT pass that only listens for "sí"/"no" (or a name in
  a disambiguation list).

## TTS (`TextToSpeech`)

- Native, **Spanish voice**, **default speech rate ~10–15 % slower** (configurable —
  some seniors need it slower; pitch configurable too). Prototype = system default
  male voice (spec §14, closed decision); if it's robotic enough to bother the user,
  Plan B is ElevenLabs — but that would break "fully offline", so it's a deliberate
  later call.
- **Everything Curro says is spoken AND shown** — the screen reinforces the voice,
  it never replaces it.
- "Speak what you're doing": short, present-tense — "Llamando a Pepito", "De Lucía:
  'Mañana te llamo, papá'". When reading several messages, group by sender (not by
  time); if there are many (> 8), offer "Tienes muchos mensajes. ¿Te los leo todos o
  solo los de alguien?" (the first nod toward Gemma-3n summarization).
- A button press stops playback immediately (the interrupt rule).

## Curro's voice (spec §2 — non-negotiable; canonical copy lives in `brand-design`)

Warm, Andalusian, colloquial — **efficient and close, not servile**.
- ✅ "Vale, llamando a Pepito." ✅ "Un momento…" ✅ "Lo apunto: Lucía Ruiz es tu hija. Llamando." ✅ "No tienes mensajes nuevos."
- ❌ "Claro, cómo no, ahora mismo." ❌ constant "lo siento / disculpa". ❌ codes, jargon, silence. ❌ trapping the user in loops.
- **Fail comprehensibly:** every `CurroError` → a plain Spanish sentence + a proposed
  alternative. "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di
  'ayuda' para que te cuente lo que sí sé hacer."

## Wiring it together (the coordinator)

`AssistantCoordinator` sequences: `listening` (STT, live transcript) → `processing`
(FunctionGemma `decide()` → `ConfidencePolicy` → maybe `confirming`) → `executing`
(dispatch to the `FunctionHandler`, which may itself return `NeedsConfirmation`) →
TTS the result → `idle`. Invalid model output (spec flow 7): no auto-retry → speak
the fallback → log the utterance → `idle`. The coordinator and FSM live in
`assistant/`; STT/TTS in `data/voice/` behind interfaces (`SttClient`, `TtsClient`)
so tests use fakes.

## Testing (see `testing-patterns`)

- Every transition in the §6 diagram; the interrupt rule from each state; the
  1st/2nd/3rd STT-failure messages + the give-up + counter reset; the 10 s confirming
  timeout; `ConfidencePolicy` for ≥0.85 / 0.60–0.85 / <0.60 and each always-escalate
  case; "no"/timeout in `confirming` → `idle`; the disambiguation repeat-once-then-
  give-up.
- UI tests: `ListeningOverlay` renders the live transcript; `ConfirmationOverlay`'s
  SÍ/NO are ≥ 96 dp and fire the right events; mic press mid-read returns to listening.
- On the **real Redmi 15**: offline Spanish STT with no network; intelligible Spanish
  TTS at the slowed rate; press→speak→answer < 2 s for a simple flow; haptic + audio-
  wave feedback.

## Rules

1. **One FSM, one owner of transitions, the interrupt rule baked in** — not bolted on later.
2. **The model returns confidence; the policy decides** — and `conditional` *always* confirms on ambiguity / irreversible cost / "always confirm".
3. **Never loop on "no te entiendo"** — 3 strikes and stop.
4. **Never wait indefinitely in `confirming`** — 10 s, then "Cancelo entonces".
5. **Spoken + shown, always.** No silent failures, no codes — every error is a sentence + an alternative.
6. **Spanish strings come from resources / the copy module**, in Curro's voice — never hard-coded; new lines get canonicalised in `brand-design`.
7. **Overlays are state-driven, not nav routes.**
