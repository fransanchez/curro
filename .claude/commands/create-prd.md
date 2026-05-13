---
description: Add a new user story to PRD
---
Add a new user story to `docs/PRD.md`.

Arguments: `$ARGUMENTS` (description of the feature/story)

1. Read `docs/PRD.md` (create from the template if it doesn't exist) and identify which **phase** the story belongs to (the phases follow `docs/curro-spec-v1.0.md` §14's build order).
2. Determine the next `US-XXX` number.
3. Add the story in the document's format: ID, short title, `As a … I want … so that …`, acceptance criteria, size (S/M/L), `Depends on` (optional), and **the spec section(s) it implements** (every story cites `curro-spec-v1.0.md`).
4. Save and commit (`docs(prd): add US-XXX …`); don't push.

Next step is usually `/generate-brief US-XXX`.
