---
description: Scaffold a function handler in the execution layer
---
Scaffold a `FunctionHandler` for a catalog function.

Arguments: `$ARGUMENTS` (required: handler name in PascalCase, e.g. `TellTime` → `TellTimeHandler`)

The function must already exist in the catalog (`function-catalog` skill + spec §5 +
`domain/catalog/`) — if not, run `/add-function` instead.

Create:
1. `app/src/main/java/com/curro/app/handler/[Name]Handler.kt`
   - implements `FunctionHandler` (`val function: CatalogFunction`; `suspend fun handle(call: FunctionCall): HandlerResult`).
   - validates `call.params` → resolves references (contact/app/etc., via the relevant
     `domain/repository/` interface — never touch Android system APIs directly; see
     `platform-integrations`) → if the catalog says confirm, return
     `HandlerResult.NeedsConfirmation(prompt, onConfirm)` → otherwise run the native
     action and return `HandlerResult.Spoken(speech, screen?)` → on any failure return
     `HandlerResult.Failed(speech, reason)` with a **plain Spanish** sentence + an
     alternative (never a code; see `brand-design` for the copy).
   - constructor-inject only domain interfaces (`@Inject constructor(...)`); the
     handler is annotated `@HandlerKey("[function_name]")` (or whatever the map key
     mechanism is) so Hilt multibinds it.
2. Register it in the Hilt handler-map module (`@Provides @IntoMap @StringKey("[function_name]")` or `@Binds @IntoMap …`).
3. `app/src/test/java/com/curro/app/handler/[Name]HandlerTest.kt`
   - JUnit5 + Mockk; fake the domain interfaces (`ContactsProvider`, `InstalledAppsProvider`, `CallController`, `UnreadMessageCache`, … — never the real Android APIs); cover: success → `Spoken`; ambiguity / catalog-says-confirm → `NeedsConfirmation`; each `HandlerError` → `Failed` with the expected utterance; permission-missing path. See `testing-patterns`.

Follow `function-catalog`, `platform-integrations`, `local-data`, `brand-design`,
`testing-patterns`. After creating files, remind the user the handler must be
registered in the Hilt map (step 2) and the spoken lines added to `res/values-es/`.
