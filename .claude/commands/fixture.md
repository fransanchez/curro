---
description: Generate test data fixtures
---
Generate Kotlin test-fixture objects for Curro.

Arguments: `$ARGUMENTS` (required: type — one of)
- `contacts` — `Contact` objects (id/lookup key, name, numbers, photo uri); include a "3 Marías" set for the ambiguity flow.
- `aliases` — `ContactAliasEntity` rows ("mi hija" → a lookup key, with `source`).
- `whatsapp-notifications` — `StatusBarNotification` builders for the `WhatsAppNotificationParser` suite: MessagingStyle 1:1, MessagingStyle group, legacy `extras`, summary notification, emoji-only body, voice note, image, malformed/unknown shape.
- `function-call-json` — raw FunctionGemma outputs (valid `{action,params,confidence}` for each Fase-1 function, plus the malformations: non-JSON, fenced JSON, missing/empty action, unknown action, missing param, mistyped param, extra param, confidence out of range).
- `failed-commands` — `FailedCommandEntity` rows (each `kind`: INVALID_OUTPUT / UNKNOWN_FUNCTION / HANDLER_ERROR).
- `app-list` — installed-app entries (label/package/icon) incl. the colloquial ones (Cámara, Galería/Fotos, WhatsApp, Teléfono, …) for `open_app` name-resolution tests.
- `whatsapp-messages` — parsed `WhatsAppMessage` objects grouped by sender (for the message-cards UI + read-aloud).

Create the fixtures in `app/src/test/java/com/curro/app/fixtures/` (Kotlin objects /
factory functions, realistic Spanish data). Match the domain models in `domain/model/`,
the catalog in the `function-catalog` skill, and the patterns in `testing-patterns`.
There is **no backend** — fixtures are for unit/UI tests, not "API response format".
