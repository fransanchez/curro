# Specification Template

The implementation-brief / feature-spec template for the Curro app (used by
`/generate-brief` → `docs/briefs/US-XXX-<slug>.md`). It mirrors `CLAUDE.md`'s
architecture and package layout; the Curro-relevant sections (function-catalog
impact, FSM states, Android integrations & permissions, on-device-model impact,
senior-UX & copy) replace what would be an "API contract" in a backend-backed app —
**Curro has no backend**. Cross-reference: `function-catalog`, `voice-interaction`,
`platform-integrations`, `local-data`, `launcher-ui`, `on-device-llm`,
`testing-patterns`, `brand-design`, `git-workflow`.

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | [Feature Name] |
| **US ID** | US-XXX |
| **Phase** | 0 / 1 / 2 / 3 / 4 (Curro phases — see `docs/PRD.md`) |
| **Status** | In Progress / Completed |
| **Created** | YYYY-MM-DD |
| **Modified** | YYYY-MM-DD |
| **PM Owner** | [Name] |
| **Architect** | [Name] |

## Summary

One paragraph: what the feature is and why it matters for *this* user (Fran's father —
elderly, low vision, good hearing, slow with new UIs; the app must feel the same
every day). Link the relevant spec section (`docs/curro-spec-v1.0.md` §…).

## Scope

- **In Scope**: [what this story delivers]
- **Out of Scope**: [explicitly excluded — push to a later phase or another story]

## User Flows

Describe the interactions. For voice flows, write them like the spec's §6 flows
(user utterance → STT → FunctionGemma → confirmation/clarify → handler → TTS), and
say which assistant state each step is in.

### Flow 1: [Primary flow]
1. User presses the mic button → `listening`
2. User says "…" → STT → `processing`
3. FunctionGemma → `{ "action": "…", "params": {…}, "confidence": … }`
4. `ConfidencePolicy` → execute / confirm / clarify
5. Handler runs → `HandlerResult.Spoken | NeedsConfirmation | Failed`
6. Curro speaks the result (and shows it) → `idle`

### Flow 2: [Error / recovery / ambiguity flow]
1. …

## Function-catalog Impact

Does this story **add or change a catalog function**? If yes:

- **Name** (snake_case), **description** (one line), **params** (name / type / required / default / desc)
- **`voice_examples`** (a handful of colloquial-Spanish utterances)
- **`needs_confirmation`**: `false` / `true` / `conditional` (+ any always-escalate cases — ambiguous param, irreversible cost, "always confirm" toggle)
- **Handler** (`<Name>Handler` in `handler/`), **phase**

> The catalog lives in **three places that must stay in sync**: the `function-catalog`
> skill ⇄ `docs/curro-spec-v1.0.md` §5 ⇄ `domain/catalog/` (prompt rendering + JSON
> schema). Update all three. Use `/add-function <name>` to scaffold. If this story
> doesn't touch the catalog, say "No catalog change."

## FSM States Touched

Which of **`idle` / `listening` / `processing` / `confirming` / `executing` /
`error_recovery`** does this feature affect, and how? (e.g. "adds a new
`executing`-state UI: the message-cards screen"; "adds a `confirming` branch for the
alias-learning subflow"; "changes nothing — it's a config-menu screen, outside the
assistant FSM".) Note any new always-escalate-to-confirmation condition. See
`voice-interaction`.

## Android System Integrations & Permissions

Which integrations does this feature use? (`NotificationListenerService` /
`TelecomManager` / `InCallService` / `PackageManager` / `ContactsContract` /
`AudioManager` / `RoleManager` / …) Put each one behind a `domain/repository/`
interface; the Android API stays in `data/`. List the **permissions** it needs and
**when each is requested** — lazily, per spec §10 (the user never sees a prompt for a
capability they aren't using; on revocation, fail with a plain Spanish "díselo a
Fran", never a crash). See `platform-integrations`.

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `…` | `…` | `…` | `…` |

(If the feature needs a *new* permission: add it to the manifest, the `CLAUDE.md`
table, and spec §10.)

## On-device-model Impact

- Does it change **FunctionGemma's prompt context** (the current time / unread-msg summary / known aliases — keep it tiny, every token competes on a 270M model)? Does it add a new function the model must learn to emit?
- Does it need **Gemma 3n** (NL generation — summaries, rewrites)? If so: load on demand, "Dame un segundo" while cold, free under memory pressure. In Phase 1, prefer not to need it.
- **Latency budget**: warm FunctionGemma text→JSON < 500 ms; Gemma 3n 3–6 s typical. Surface the figure in the config-menu diagnostics.
- MediaPipe stays in `data/ml/` behind `domain/repository/` interfaces; the debug build builds without the weights. See `on-device-llm`.

(If the feature is pure UI / persistence / a handler that doesn't touch the model: "No model impact.")

## Android Specification

### Screens and Composables

Curro has **few "screens"** — the launcher home and the config menu are the only nav
routes; the assistant's listening / processing / confirming / message-cards / picker
UI are **state-driven overlays** keyed off `AssistantState`, not routes (see
`launcher-ui`, `voice-interaction`). Use the right home for each:

- **`presentation/launcher/`** — `LauncherScreen` + `LauncherViewModel`, `ClockBlock`, `MicButton`, `AppTileGrid`, `AppTile`, `MoreAppsScreen`.
- **`presentation/assistant/`** — `ListeningOverlay`, `ProcessingOverlay`, `ConfirmationOverlay`, `MessageCardsScreen`, `ContactPickerScreen` (driven by the `StateFlow<AssistantState>`).
- **`presentation/config/`** — `ConfigMenuScreen` + `ConfigViewModel` (Fran-only; opened by 5 taps on the clock).
- **`presentation/common/`** — shared big components: `BigPrimaryButton` (≥ 96 dp), `BigYesNoRow`, `BigCard`, `BigListRow`.

For each new/modified screen: file path, ViewModel, purpose, the `Content` composable
(stateless — receives state, emits events) + sub-composables. Example:

```kotlin
@Composable
fun ConfigMenuScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel(),
)

@Composable
private fun ConfigMenuContent(
    uiState: ConfigUiState,
    onEvent: (ConfigEvent) -> Unit,
    onNavigateBack: () -> Unit,
)
```

(Child screens **do not** add their own `Scaffold` / `TopAppBar` / `statusBarsPadding()` — the No-Double-Padding rule in `CLAUDE.md`. Back navigation = a large chevron `Icons.AutoMirrored.Filled.KeyboardArrowLeft` at `Alignment.TopStart` in a `Box`.)

### ViewModels and State Management

```kotlin
sealed interface MessageCardsUiState {
    data object Loading : MessageCardsUiState
    data class Ready(val groups: List<SenderGroup>, val highlighted: MessageId?) : MessageCardsUiState
    data object Empty : MessageCardsUiState                       // → "No tienes mensajes nuevos"
}

sealed interface MessageCardsEvent {
    data object ReadAll : MessageCardsEvent
    data class ReadFrom(val sender: String) : MessageCardsEvent
}

@HiltViewModel
class MessageCardsViewModel @Inject constructor(
    private val readUnreadWhatsApp: ReadUnreadWhatsAppUseCase,
) : ViewModel() {
    val uiState: StateFlow<MessageCardsUiState>
    fun onEvent(event: MessageCardsEvent)
}
```

### Navigation Routes

Minimal — Curro's nav graph is `LauncherScreen` ⇄ `ConfigMenuScreen` only; the
assistant overlays are not routes. If this story genuinely needs a new route, define
it; otherwise: "No new routes — uses the assistant state overlays."

```kotlin
sealed interface CurroRoute {
    data object Launcher  : CurroRoute   // "launcher"
    data object ConfigMenu : CurroRoute  // "config"
}
```

### Hilt Modules

What needs binding/providing — repositories to their `domain/repository/` interfaces,
use cases, handlers into the function-name-keyed multibinding map, engines, STT/TTS
clients, DAOs:

```kotlin
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideCurroDatabase(@ApplicationContext ctx: Context): CurroDatabase =
        Room.databaseBuilder(ctx, CurroDatabase::class.java, "curro.db").build()

    @Provides fun provideContactAliasDao(db: CurroDatabase): ContactAliasDao = db.contactAliasDao()
}

@Module @InstallIn(SingletonComponent::class)
abstract class HandlerModule {
    @Binds @IntoMap @StringKey("tell_time")
    abstract fun bindTellTimeHandler(impl: TellTimeHandler): FunctionHandler
}

@Module @InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides @Singleton
    fun provideAliasRepository(impl: AliasRepositoryImpl): AliasRepository = impl
}
```

### Composables by Feature (checklist)

- [ ] Main screen composable (`*Screen` — collects the ViewModel + the assistant state)
- [ ] Stateless `Content` composable
- [ ] Card / list-row / tile composables (built from the shared big components)
- [ ] Any picker / confirmation composable
- [ ] Loading / empty / error composables (`Empty` → the right Spanish string)

### Material Design Components

List the M3 components used — **scaled up** for this user (`launcher-ui`,
`material-design` say "48 dp / default type scale" → read as the *floor*; Curro goes
bigger): e.g. `Button`/`FilledTonalButton` for the big SÍ/NO and tiles, `Card` for
message/picker rows, `LazyColumn` / `LazyVerticalGrid` for the app grid and "Más
apps", a high-contrast colour scheme, no fussy animation.

## Acceptance Criteria

Concrete, checkable:

- [ ] [behaviour 1 — e.g. "‘qué hora es’ → Curro speaks the current time"]
- [ ] [behaviour 2]
- [ ] Every Curro→user message is **spoken AND shown** (spec §4.6)
- [ ] On a missing/revoked permission, the failure is a plain Spanish "díselo a Fran" — no crash, no code
- [ ] All interactive elements ≥ 96 dp; text scales at `fontScale = 1.5f` / `2.0f`; high contrast; `contentDescription` on every image/icon
- [ ] Layout is fixed/predictable (no surprising reordering)
- [ ] Dark mode supported
- [ ] No new permissions beyond those listed; the debug build builds/tests without model weights

## Design Notes

Follow the **`brand-design` skill** (currently a template — fill it in) for colours,
typography, spacing, shapes, and Curro's voice/Spanish copy. **No hard-coded values**
— read tokens via `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` /
`CurroSpacing.*` / `CurroShapes.*`. ≥ 96 dp tap targets, big text, high contrast,
audio + visual together, "feels the same every day" (see `launcher-ui`'s senior-first
rules). Build the UI from the shared big components so sizing is consistent.

## Senior-UX & Copy

The new **Spanish strings / spoken utterances** this feature needs — list them (the
canonical wording lives in `brand-design`; they go through resources / the copy
module, never hard-coded). Curro's voice: warm, Andalusian, colloquial — efficient
and close, not servile; errors are plain + offer an alternative; never traps the user
in a loop. Examples for this feature:

- `"…"` (e.g. "Llamando a Pepito")
- `"…"` (e.g. the `Empty`-state line, the recovery line, the confirmation prompt)

## Performance Considerations

- `remember { }` for local state; `rememberSaveable { }` for what must survive rotation
- `LazyColumn` / `LazyVerticalGrid` for lists/grids (the app grid, "Más apps", message cards) — never a plain scrollable `Column` for long content
- Coil `AsyncImage` (with crossfade) for contact photos / app icons
- Model inference, STT/TTS, ContentResolver/Room/file I/O on `Dispatchers.IO` — never the main thread
- Stable composable params; avoid needless recomposition; keep functions small
- Don't reshuffle the home grid on every open — recompute favourites occasionally (`local-data`)

## Testing Requirements

Align with `testing-patterns`'s Curro list:

- [ ] **FSM**: the transitions this feature touches; the interrupt-by-button rule; the relevant recovery / timeout / disambiguation behaviour
- [ ] **Fake the LLM / STT / TTS** — no real models in JVM tests; if the feature changes the prompt or the validator, golden-string the prompt builder / cover each malformation → the right `CurroError` (no auto-retry)
- [ ] **Handler tests** against faked system integrations — every outcome (success → `Spoken`, ambiguity → `NeedsConfirmation`, each `HandlerError` → `Failed` with a plain-Spanish utterance); the permission-missing path
- [ ] **`WhatsAppNotificationParser`** fixture suite, if this feature touches notification parsing
- [ ] **In-memory Room** DAO tests + `SettingsRepository`, if it touches persistence; the alias-learning subflow, if it touches aliases
- [ ] **`ConfidencePolicy`**, if it touches confirmation
- [ ] **UI tests** on the `Content` composables (not the `Screen`s): the feature's interactions; an accessibility sweep (no image/icon without `contentDescription`; every clickable node ≥ 96 dp; text scales with `fontScale`)
- [ ] Dark-mode verification
- [ ] On the **real Redmi 15** for anything voice/ML/launcher (offline STT, intelligible TTS, < 500 ms warm latency, no OOM, default-launcher behaviour) — see `verification-checklist`

## Implementation Notes

**PM Owner writes**: Metadata, Summary, Scope, User Flows, Function-catalog Impact
(the *what* — name/params/examples/`needs_confirmation`), Senior-UX & Copy, Acceptance
Criteria, Design Notes.

**Architect writes**: FSM States Touched, Android System Integrations & Permissions,
On-device-model Impact, the Android Specification section, Performance Considerations,
Testing Requirements.

## Revision History

| Date | Author | Change |
|------|--------|--------|
| YYYY-MM-DD | [Name] | Initial draft |
| YYYY-MM-DD | [Name] | Reviewed and updated |
</content>
