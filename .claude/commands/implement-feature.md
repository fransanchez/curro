---
description: Implement a feature from its brief
---
Implement a Curro feature following its implementation brief.

Arguments: `$ARGUMENTS` (required: US-XXX identifier)

1. Read the brief from `docs/briefs/US-XXX-*.md` (and the spec section(s) it cites).
2. Work through the tasks sequentially.
3. Follow the project patterns — `compose-patterns`, `launcher-ui`, `function-catalog`, `platform-integrations`, `local-data`, `voice-interaction`, `on-device-llm`, `accessibility-patterns`, `brand-design`, and `CLAUDE.md`'s package layout / coding standards. Use the right agent for the layer: `android-developer` (handlers, UI, data, glue), `ondevice-ai-engineer` (FunctionGemma/Gemma 3n), `voice-pipeline-engineer` (STT/TTS/FSM/confirmation).
4. Run the `verification-checklist` skill after each major step (build → lint → test → run; on the real Redmi 15 for anything voice/ML/launcher; plus the privacy/permissions check).
5. Commit completed work (conventional commits with Curro scopes — see `git-workflow`); don't push or open a PR without permission.

This is `android-developer`'s job for most of it (the LLM and voice/FSM layers go to
the two specialist engineering agents). There is **no backend** — there's no
`api-integration` here; system integrations and on-device models instead.
