---
description: Enter Plan Mode to design before implementing
---
Design the architecture for a Curro feature before writing code.

Arguments: `$ARGUMENTS` (required: US-XXX or a feature description)

Use the `android-architect` agent to:
1. Read the brief (`docs/briefs/US-XXX-*.md`) and the spec section(s) it cites.
2. Design the Clean Architecture layers per `CLAUDE.md`'s package layout (`domain/{model,catalog,repository,usecase}`, `data/{local,ml,voice,notification,telephony,apps,contacts,repository}`, `handler/`, `assistant/`, `service/`, `presentation/{theme,launcher,assistant,config,common,navigation}`) — Curro is one large assistant feature + small launcher/config screens, not a folder-per-screen.
3. Define the interfaces (`FunctionCallEngine`, `TextGenEngine`, `SttClient`, `TtsClient`, `FunctionHandler`, repositories) and the Hilt modules (`DatabaseModule`, `MlModule`, `VoiceModule`, the handler multibinding, repository binds).
4. Note function-catalog impact, FSM states touched, permissions, on-device-model impact, navigation changes (the launcher home and config menu are the only routes — assistant UI is state-driven overlays).
5. Hand off the LLM internals to `ondevice-ai-engineer` and the STT/TTS/FSM/confirmation internals to `voice-pipeline-engineer` (the architect places those components and defines their interfaces; it doesn't redesign them).
6. Produce an architecture document with an execution plan in phases — **no code**.
