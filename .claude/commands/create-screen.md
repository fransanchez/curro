---
description: Scaffold a new Screen composable with ViewModel
---
Scaffold a Compose screen + ViewModel following project patterns.

Arguments: `$ARGUMENTS` (required: screen name in PascalCase, e.g. `ConfigMenu`)

> ⚠️ Curro has **very few "screens"**. The launcher home and the (Fran-only) config
> menu are the only nav routes; the assistant's listening/processing/confirming/
> message-cards/contact-picker UI are **state-driven overlays** driven by
> `StateFlow<AssistantState>`, not routes — for those, add a composable under
> `presentation/assistant/` that the launcher renders for the relevant state, not a
> new "screen". Use this command for the genuine screens (launcher home, config
> menu, "Más apps") and reusable big components.

Create:
1. `app/src/main/java/com/curro/app/presentation/<area>/[Name]Screen.kt`
   - `@Composable fun [Name]Screen(viewModel: [Name]ViewModel = hiltViewModel(), …nav callbacks…)` — collects state, delegates to a stateless `[Name]Content`.
   - `@Composable private fun [Name]Content(uiState, onEvent, …)` — stateless; for back navigation use a `Box` + overlay `IconButton` (`Icons.AutoMirrored.Filled.KeyboardArrowLeft`, large) at `Alignment.TopStart` — **no `Scaffold`/`TopAppBar`/`statusBarsPadding()` in a child screen** (No-Double-Padding, see `CLAUDE.md`).
   - `@Preview` (light + dark + a `fontScale = 1.5f` variant).
2. `app/src/main/java/com/curro/app/presentation/<area>/[Name]ViewModel.kt`
   - `@HiltViewModel`; sealed `[Name]UiState` / `[Name]Event`; `StateFlow` + `onEvent(event)`.
3. `app/src/test/java/com/curro/app/presentation/<area>/[Name]ViewModelTest.kt`
   - JUnit5 + Mockk + Turbine + `TestDispatcherExtension` (see `testing-patterns`).
4. (if it's a real nav route) `app/src/androidTest/java/com/curro/app/presentation/<area>/[Name]ContentTest.kt`
   - Compose UI test on the `Content` composable.

`<area>` is one of `launcher`, `config`, `assistant`, `common`. Follow `compose-patterns`,
`launcher-ui`, `accessibility-patterns` (≥ 96 dp targets, big text), and `brand-design`
(use theme tokens, never raw `Color(0xFF…)`/`.sp`/`.dp`; Spanish strings via resources,
not hard-coded). After creating files: if it's a nav route, remind the user to register
it in `presentation/navigation/CurroNavHost.kt` (`navigation-patterns`).
