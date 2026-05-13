---
description: Generate implementation brief from user story
---
Generate a detailed implementation brief for a Curro feature.

Arguments: `$ARGUMENTS` (required: US-XXX identifier)

1. Read the user story from `docs/PRD.md` and the relevant section(s) of `docs/curro-spec-v1.0.md` it cites.
2. Analyze the codebase for similar patterns (or, while it's still empty, the architecture in `CLAUDE.md`).
3. Generate the brief in `docs/briefs/US-XXX-feature-name.md` using the `spec-template` skill.
4. Cover: files to create/modify (per `CLAUDE.md`'s package layout); the task list; **function-catalog impact** (new/changed catalog function?); **FSM states touched** (`idle/listening/processing/confirming/executing/error_recovery`?); **Android system integrations & permissions** (NotificationListener / Telecom / PackageManager / Contacts / AudioManager — requested when?); **on-device-model impact** (FunctionGemma prompt context / Gemma 3n / latency budget?); **senior-UX & copy** (≥ 96 dp targets, big text, audio + visual, new Spanish strings); navigation changes (the launcher home and config menu are the only routes — most assistant UI is state-driven overlays); the testing plan (per `testing-patterns`).
5. Save the brief and commit it (don't push / open a PR).

This is the `android-product-analyst` agent's job — invoke it for non-trivial stories.
There is **no backend** — don't write "API dependencies"; write the integration /
permission / model / catalog impact instead.
