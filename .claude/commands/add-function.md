---
description: Add a function to Curro's function catalog (handler + tests + registration)
---
Add a new function to Curro's capability catalog end-to-end.

Arguments: `$ARGUMENTS` (required: function name in `snake_case`, e.g. `set_reminder`)

Follow the "How to add a function" steps in the `function-catalog` skill:

1. **Spec it** (use `android-product-analyst` if it's non-trivial): name, one-line
   description, params (name / type / required / default / desc), `voice_examples`,
   `needs_confirmation` (`false` | `true` | `conditional`) + any always-escalate
   cases, the phase. Add the entry **in the same YAML shape** to:
   - `docs/curro-spec-v1.0.md` §5 (the spec — bump the spec version),
   - the `function-catalog` skill (`.claude/skills/function-catalog/SKILL.md`),
   - `app/src/main/java/com/curro/app/domain/catalog/` (so the FunctionGemma prompt
     rendering **and** the JSON Schema include it).
2. **Scaffold the handler** — `/create-handler [Name]` (a `FunctionHandler` in
   `handler/`; `HandlerResult.Spoken | NeedsConfirmation | Failed`).
3. **Register it** — add the handler to the Hilt multibinding map keyed by the
   function name (`@IntoMap` / function-name key).
4. **Permissions** — if the handler needs a new Android permission, add it to the
   manifest + the table in `CLAUDE.md` + spec §10, and request it lazily (only when
   this function is used) — see `platform-integrations`.
5. **Copy** — every line the handler can speak goes through resources / the copy
   module in Curro's voice (`brand-design`), never hard-coded Spanish.
6. **Tests** (use `android-qa-specialist`): the validator accepts the new function's
   good JSON and rejects each malformation; the handler maps every outcome (success →
   `Spoken`, needs-confirmation → `NeedsConfirmation`, each `HandlerError` → `Failed`
   with a plain-Spanish utterance); the permission-missing path if applicable.
7. **Verify** with the `verification-checklist` skill (build → lint → test; on the
   real Redmi 15 if it touches STT/TTS/LLM/launcher).

Do **not** add Fase-2/3/4 functions to FunctionGemma's prompt before their phase —
every token competes with Fase-1 accuracy on a 270M model.
