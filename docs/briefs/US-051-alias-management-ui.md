# Alias management UI — US-051 / SF-8.2

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Fran-facing UI to view, add, edit, and delete contact aliases |
| **US ID** | US-051 (master-plan SF-8.2) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

## Summary

Replace SF-8.1's `config/aliases` placeholder with the real
`AliasesScreen` — a list of every alias Curro has learned (SF-7.3) or that
Fran has added by hand, plus the operations to add a new one, edit an
existing one, or delete one. Fran's primary use case is **preloading aliases
his father hasn't said yet** ("mi nieta = María Pérez García") and **fixing
the rare wrong guess** ("mi hija is actually María not Lucía"). The new
add-via-dialog flow drives both: pick a contact from a searchable list, type
the relational term, save. The existing prompt-context injection from SF-7.2
picks up every change on the next FunctionGemma turn — no new wiring at the
decision layer.

The single API addition is `AliasRepository.delete(alias)` (and the matching
`ContactAliasDao.delete` method, which already exists from SF-7.1); the
edit-with-renamed-alias case becomes `delete(old) + learn(new, contact,
EXPLICIT)`. The new aliases land with `AliasSource.EXPLICIT`, distinct from
`LEARNED` (SF-7.3) and `PRELOADED_BY_FRAN` (reserved for a future onboarding
flow).

Spec reference: `docs/curro-spec-v1.0.md` §9 ("Alias de contactos") + §7
(alias model).

## Scope

- **In Scope**:
  - `AliasesScreen` + `AliasesViewModel` + the three sub-composables
    (`AliasRow`, `AddOrEditAliasDialog`, `DeleteAliasConfirmDialog`).
  - `AliasRepository.delete(alias: String)` + the `RoomAliasRepository` impl.
  - 11 new strings.
  - Replacement of the `composable("config/aliases")` placeholder in
    `CurroNavHost`.
- **Out of Scope** (explicit non-deliveries):
  - Any bulk-import / CSV / contacts-sweep onboarding (Phase 4 or later).
  - Voice-driven alias add (out of scope; the natural pathway is the
    alias-learning subflow SF-7.3 which is already shipped — this UI is for
    Fran on a maintenance pass, not the user).
  - Any change to the alias-learning subflow itself (SF-7.3).
  - Any change to the prompt-context injection (SF-7.2).
  - Any change to the failed-commands UI or other Phase-8 SFs.
  - A "swap two aliases" or "merge" affordance — out of scope; if a swap is
    needed, do it as delete + add.
  - Confirmation on `Save` — Save is non-destructive (overwrites if alias
    text matches an existing row via the `OnConflictStrategy.REPLACE` rule
    in SF-7.1 / SF-7.2). Delete has confirmation.
  - Telemetry for alias edits (out of scope — a future `config_changed
    {key=alias}` is reasonable but not required for SF-8.2).

## User Flows

### Flow 1: Fran preloads "mi nieta" before his father ever says it

1. Fran opens the config menu (5-tap clock) → taps "Alias de contactos" →
   `AliasesScreen` renders. If no aliases exist yet, the empty state ("No
   hay alias guardados todavía.") shows; the "Añadir alias" FAB is always
   present.
2. Fran taps "Añadir alias" → `AddOrEditAliasDialog` opens with two
   `TextField`s (alias text — "mi nieta" — at top; contact-search at bottom)
   and a scrollable contact list below the search.
3. Fran types "María" in the search → the contact list filters to all
   contacts whose `displayName.curroNormalize()` contains "maria".
4. Fran taps "María Pérez García" → row highlights.
5. Fran types "mi nieta" in the alias text field.
6. The "Guardar" button enables (it was disabled while either alias was
   blank or no contact was selected).
7. Fran taps Guardar → VM calls `aliasRepo.learn("mi nieta", contact,
   source = EXPLICIT)` → dialog dismisses → the new row appears in the
   list, with the source badge "lo apunté yo".
8. Future "llama a mi nieta" from his father resolves directly to María
   Pérez García (via the existing SF-7.2 `RoomAliasRepository.resolveAlias`
   path).

### Flow 2: Fran fixes a wrong alias ("mi hija" → wrong contact)

1. Fran opens the aliases section → sees a row "mi hija → Lucía Ruiz —
   aprendido" (Curro's SF-7.3 guess).
2. Fran taps the row → `AddOrEditAliasDialog` opens, pre-populated with
   "mi hija" + Lucía selected.
3. Fran searches for "María" → taps María Pérez García.
4. Fran taps Guardar → VM calls `aliasRepo.learn("mi hija", María,
   source = EXPLICIT)` (the alias text didn't change; `OnConflictStrategy.
   REPLACE` overwrites the row; `useCount` resets to 0 per the existing
   SF-7.1 DAO contract).
5. The row updates to "mi hija → María Pérez García — lo apunté yo".
6. Future "llama a mi hija" resolves to María.

### Flow 3: Fran renames an alias text (rare but supported)

1. Fran taps an existing "mi hija" row.
2. Edits the alias text to "mi nena", keeps the same contact.
3. Taps Guardar.
4. The VM detects the alias rename (`oldAlias != newAlias`) → calls
   `aliasRepo.delete("mi hija")` then `aliasRepo.learn("mi nena", contact,
   EXPLICIT)` in sequence.
5. List re-renders: "mi nena" appears, "mi hija" disappears.

**Pin** (in the brief): the two suspend calls are sequential, not in a
single Room `@Transaction`. The window of inconsistency between the delete
and the learn is small; if the app crashes between them, the alias is lost
(Fran re-adds). Document this trade-off; do not over-engineer for the
prototype.

### Flow 4: Fran deletes an alias

1. Fran long-presses an alias row → `DeleteAliasConfirmDialog` opens with
   the alias text in the title and `copy_config_alias_delete_confirm` in the
   body.
2. Fran taps "Sí, borrar" → VM calls `aliasRepo.delete(alias)` → row
   disappears.
3. Future utterances using that alias fall through to the regular
   `ContactsProvider.findByName` path (or, if a relational term, re-trigger
   the SF-7.3 learning subflow).

### Flow 5: Empty state

1. Fresh install, no aliases. Fran opens the section.
2. Centred message: "No hay alias guardados todavía." + the "Añadir alias"
   FAB.

## Function-catalog Impact

No catalog change. **Prompt-context impact**: the SF-7.2 prompt-context
injection (`AliasRepository.topUsedSnapshots(10)`) automatically picks up
every alias added/edited/deleted in this screen on the next FunctionGemma
turn. No new injection wiring.

## FSM States Touched

None. The aliases screen lives outside the assistant FSM.

## Android System Integrations & Permissions

`ContactsProvider.findAll()` (existing — SF-7.3) is used to populate the
contact picker in the add/edit dialog. It needs `READ_CONTACTS` (already
requested by US-034 on first call_contact). If the permission is denied at
the moment the dialog opens:

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `READ_CONTACTS` | populate the contact-picker list | already granted on first `call_contact` from US-034 | the dialog renders with an inline message `copy_perm_missing_contacts` and no contact list; the "Añadir alias" flow is unusable until the permission is granted (Fran sees the same "Díselo a Fran" line — but here Fran IS at the phone). **Pin: do NOT request the permission from inside the config menu** — the request belongs to the launcher's permission infrastructure. |

The brief pins: if `ContactsProvider.findAll()` returns `emptyList()` AND
the permission is denied (the gate `ReadContactsPermissionGate.isGranted()`
returns `false`), render the dialog with the permission-missing line + a
disabled Save button. If the permission is granted but the contacts table is
empty, render "No tienes contactos" (use the SF-7.3 string
`copy_alias_no_contacts`).

## On-device-model Impact

No model impact. The prompt-context injection from SF-7.2 picks up changes
automatically (`combine`-style flow inside the existing `AssistantCoordinator
.buildContext`); SF-8.2 makes no change there. The token-budget cap (10
aliases) is unchanged.

## Android Specification

### Screens and Composables

- **`presentation/config/sections/aliases/AliasesScreen.kt`** —
  `@Composable fun AliasesScreen(onBack: () -> Unit, viewModel: AliasesViewModel = hiltViewModel())`.
  - Layout:
    ```kotlin
    Box(Modifier.fillMaxSize()) {
        if (uiState.aliases.isEmpty()) {
            EmptyAliasesState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = Dimens.MinTapTarget + CurroSpacing.l, bottom = CurroSpacing.xl),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = CurroSpacing.m, end = CurroSpacing.m, top = Dimens.MinTapTarget + CurroSpacing.l, bottom = 96.dp + CurroSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(CurroSpacing.s),
            ) {
                items(uiState.aliases, key = { it.alias }) { alias ->
                    AliasRow(alias = alias, onTap = { viewModel.onEvent(AliasesEvent.StartEdit(alias)) }, onLongPress = { viewModel.onEvent(AliasesEvent.RequestDelete(alias)) })
                }
            }
        }
        // FAB
        ExtendedFloatingActionButton(
            onClick = { viewModel.onEvent(AliasesEvent.StartAdd) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(CurroSpacing.l),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.copy_config_alias_add_cta)) },
        )
        // Back chevron
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(start = CurroSpacing.s, top = CurroSpacing.s).size(Dimens.MinTapTarget)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_back), modifier = Modifier.size(Dimens.LargeIconSize))
        }
        // Dialogs
        when (val d = uiState.dialog) {
            is AliasesDialogState.AddOrEdit -> AddOrEditAliasDialog(initial = d.initial, contacts = d.contacts, onDismiss = { viewModel.onEvent(AliasesEvent.DismissDialog) }, onSave = { old, new, c -> viewModel.onEvent(AliasesEvent.Save(old, new, c)) })
            is AliasesDialogState.ConfirmDelete -> DeleteAliasConfirmDialog(alias = d.alias, onConfirm = { viewModel.onEvent(AliasesEvent.ConfirmDelete(d.alias)) }, onDismiss = { viewModel.onEvent(AliasesEvent.DismissDialog) })
            AliasesDialogState.None -> Unit
        }
    }
    ```
- **`AliasRow.kt`** — `@Composable fun AliasRow(alias: AliasView, onTap: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier)`.
  - A `Card` (using `BigCard` semantics but at 72 dp config-menu density):
    primary text = `alias.alias` (`bodyLarge`), secondary = "→ ${alias.displayName}" (`bodyMedium`), tail = source badge + use-count (`labelMedium`, `onSurfaceVariant`).
  - `combinedClickable(onClick = onTap, onLongClick = onLongPress)`.
- **`AddOrEditAliasDialog.kt`** —
  `@Composable fun AddOrEditAliasDialog(initial: AliasView?, contacts: List<Contact>, onDismiss: () -> Unit, onSave: (oldAlias: String?, newAlias: String, contact: Contact) -> Unit)`.
  - `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` wrapping a `Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(CurroSpacing.l).fillMaxHeight(0.85f))`.
  - Inside: `Column` with title ("Añadir alias" / "Editar alias"), `OutlinedTextField` for alias (label `copy_config_alias_relational_hint`, initial value `initial?.alias ?: ""`), `OutlinedTextField` for search (label `copy_config_alias_search_hint`), then a `LazyColumn` of contact rows (filtered by the search query), and a footer `Row` with "Cancelar" and "Guardar" buttons.
  - **Pin: contact pre-selection on edit** — if `initial != null`, the dialog's `selectedContact` state is initialised by finding the contact whose `displayName` matches `initial.displayName` in the `contacts` list (best-effort; the user can re-pick if no match — e.g. the contact was renamed).
- **`DeleteAliasConfirmDialog.kt`** — `@Composable fun DeleteAliasConfirmDialog(alias: String, onConfirm: () -> Unit, onDismiss: () -> Unit)`.
  - `AlertDialog(title = { Text(alias) }, text = { Text(stringResource(R.string.copy_config_alias_delete_confirm)) }, confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.copy_config_alias_delete_yes), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.copy_config_alias_cancel)) } })`.
- **`EmptyAliasesState.kt`** — a centered `Column` with the empty message + an explanatory line ("Pulsa Añadir alias para guardar el primero.").

### ViewModels and State Management

```kotlin
@HiltViewModel
class AliasesViewModel @Inject constructor(
    private val aliasRepo: AliasRepository,
    private val contactsProvider: ContactsProvider,
    private val contactsGate: ReadContactsPermissionGate,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val dialogState = MutableStateFlow<AliasesDialogState>(AliasesDialogState.None)

    val uiState: StateFlow<AliasesUiState> = combine(
        aliasRepo.observeAll(),
        dialogState,
    ) { aliases, dialog ->
        AliasesUiState(aliases = aliases, dialog = dialog)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = AliasesUiState(aliases = emptyList(), dialog = AliasesDialogState.None),
    )

    fun onEvent(event: AliasesEvent) {
        when (event) {
            AliasesEvent.StartAdd -> startAddOrEdit(initial = null)
            is AliasesEvent.StartEdit -> startAddOrEdit(initial = event.alias)
            is AliasesEvent.Save -> save(oldAlias = event.oldAlias, newAlias = event.newAlias, contact = event.contact)
            is AliasesEvent.RequestDelete -> dialogState.value = AliasesDialogState.ConfirmDelete(event.alias.alias)
            is AliasesEvent.ConfirmDelete -> confirmDelete(alias = event.alias)
            AliasesEvent.DismissDialog -> dialogState.value = AliasesDialogState.None
        }
    }

    private fun startAddOrEdit(initial: AliasView?) {
        viewModelScope.launch {
            val contacts = withContext(ioDispatcher) {
                if (contactsGate.isGranted()) contactsProvider.findAll() else emptyList()
            }
            dialogState.value = AliasesDialogState.AddOrEdit(initial = initial, contacts = contacts)
        }
    }

    private fun save(oldAlias: String?, newAlias: String, contact: Contact) {
        if (newAlias.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            if (oldAlias != null && oldAlias != newAlias) {
                aliasRepo.delete(oldAlias)
            }
            aliasRepo.learn(newAlias, contact, source = AliasSource.EXPLICIT)
            dialogState.value = AliasesDialogState.None
        }
    }

    private fun confirmDelete(alias: String) {
        viewModelScope.launch(ioDispatcher) {
            aliasRepo.delete(alias)
            dialogState.value = AliasesDialogState.None
        }
    }

    private companion object { const val SUBSCRIBE_TIMEOUT_MS = 5_000L }
}

data class AliasesUiState(val aliases: List<AliasView>, val dialog: AliasesDialogState)

sealed interface AliasesDialogState {
    data object None : AliasesDialogState
    data class AddOrEdit(val initial: AliasView?, val contacts: List<Contact>) : AliasesDialogState
    data class ConfirmDelete(val alias: String) : AliasesDialogState
}

sealed interface AliasesEvent {
    data object StartAdd : AliasesEvent
    data class StartEdit(val alias: AliasView) : AliasesEvent
    data class Save(val oldAlias: String?, val newAlias: String, val contact: Contact) : AliasesEvent
    data class RequestDelete(val alias: AliasView) : AliasesEvent
    data class ConfirmDelete(val alias: String) : AliasesEvent
    data object DismissDialog : AliasesEvent
}
```

### Navigation Routes

- **MODIFIED**: replace the SF-8.1 `composable("config/aliases") { ConfigSectionPlaceholder(...) }` with `composable("config/aliases") { AliasesScreen(onBack = { navController.popBackStack() }) }`.
- No new routes.

### Hilt Modules

No new module. `AliasesViewModel` is `@HiltViewModel`-injectable using the
already-bound `AliasRepository` (SF-7.2), `ContactsProvider` (SF-4.9),
`ReadContactsPermissionGate` (SF-4.10), and the `@IoDispatcher` qualifier
(SF-7.4).

The `AliasRepository.delete(alias)` addition does not require a new binding —
`RoomAliasRepository` implements it via the existing `ContactAliasDao.delete`
(already declared in SF-7.1).

### Composables by Feature (checklist)

- [x] `AliasesScreen` (collects the ViewModel)
- [x] Stateless `AliasesContent` (TBD as a refactor inside `AliasesScreen.kt`
      to support the instrumented test — pin in the implementation)
- [x] `AliasRow` (card row)
- [x] `AddOrEditAliasDialog` (the add/edit dialog)
- [x] `DeleteAliasConfirmDialog` (the delete confirm)
- [x] `EmptyAliasesState` (the empty state)
- [x] Permission-denied state inside the dialog (renders
      `copy_perm_missing_contacts` + a disabled Save).
- [x] Dark + large-font previews (`fontScale = 1.5f` / `2.0f`).

### Material Design Components

- `LazyColumn` for the alias list.
- `Card` for each alias row.
- `Dialog` + `Surface` for the add/edit dialog (custom-sized, not the
  default `AlertDialog`-derived width).
- `AlertDialog` for the delete confirm.
- `OutlinedTextField` for both inputs (alias + search).
- `ExtendedFloatingActionButton` for the "Añadir alias" CTA.
- `IconButton` + Material chevrons for back.

## Acceptance Criteria

- [ ] **Existing aliases render** — every row from `AliasRepository.observeAll()`
      appears, with the source badge (`LEARNED` → "aprendido", `EXPLICIT` →
      "lo apunté yo", `PRELOADED_BY_FRAN` → "precargado") and the use count.
- [ ] **Add from scratch persists + prompt context picks it up** — after
      Save, the new alias is in the list, AND a follow-up FunctionGemma turn
      reads the new alias in its context (verified manually + via the existing
      SF-7.2 prompt-builder test path that confirms `topUsedSnapshots` returns
      the new alias).
- [ ] **Editing with the same alias text + different contact** uses `learn`
      with `OnConflictStrategy.REPLACE` (single DAO call); the row updates.
- [ ] **Editing with a renamed alias text** uses `delete` + `learn`; the old
      alias text is gone, the new one is in.
- [ ] **Long-press → delete confirmation → delete** removes the row.
- [ ] **Empty state renders** when there are no aliases; the "Añadir alias"
      FAB is still tappable.
- [ ] **Contacts dialog filtering works** — typing in the search field
      filters the contact `LazyColumn` to matches by `displayName.curroNormalize()
      .contains(query.curroNormalize())`.
- [ ] **Save button enables only when both fields are populated** — empty
      alias OR no selected contact → disabled.
- [ ] **Permission-denied path is graceful** — if `READ_CONTACTS` was
      revoked, the dialog renders with the `copy_perm_missing_contacts` line
      and disabled Save. No crash. No silent failure.
- [ ] **No silent overwrites without UI feedback** — editing an alias to
      match an existing alias text overwrites cleanly (REPLACE) and the list
      re-renders to show the updated row; pin in the brief that this is
      acceptable Phase-8 behaviour (Fran is the user; he sees the result).
- [ ] **11 new strings exist** in `strings.xml` with the right IDs.
- [ ] **`AliasRepository.delete(alias)` exists** + `RoomAliasRepository` impl
      + tests. The `ContactAliasDao.delete` already exists (no DAO change).
- [ ] **No new permissions, no new manifest entries, no new DataStore keys,
      no new dependencies, no new telemetry event.**
- [ ] **Build is green**.

## Design Notes

- The dialog occupies ~85 % of the screen height (`fillMaxHeight(0.85f)`) —
  the contact list inside needs to be browsable; a small dialog would force
  excessive scrolling on the Redmi 15.
- The contact rows inside the dialog use a 64 dp `Modifier.heightIn(min =
  64.dp)` — between launcher density (96 dp) and dense-list density (48 dp).
  Pin: Fran will be tapping these; ~64 dp is the right config-menu choice.
- Source badges:
  - `LEARNED` → `MaterialTheme.colorScheme.tertiaryContainer` background +
    `onTertiaryContainer` text.
  - `EXPLICIT` → `MaterialTheme.colorScheme.primaryContainer` + `onPrimaryContainer`.
  - `PRELOADED_BY_FRAN` → `MaterialTheme.colorScheme.secondaryContainer` +
    `onSecondaryContainer`.
  All three are ≥ 7:1 contrast in light/dark per `brand-design`.
- The delete-confirm dialog's "Sí, borrar" button text is in
  `MaterialTheme.colorScheme.error` for the destructive signal; "Mejor no"
  uses the default tonal style.

## Senior-UX & Copy

This is a Fran-only screen — config-menu density. The launcher's senior-first
rules do not apply. Tap targets ≥ 64 dp in dialogs; ≥ 72 dp for the main
alias rows.

**No new spoken (TTS) strings.**

New entries in `app/src/main/res/values/strings.xml` (11 total; pin each with
a `<!-- SF-8.2 (US-051) — … -->` comment):

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_alias_empty` | "No hay alias guardados todavía." | empty-state message |
| `copy_config_alias_empty_hint` | "Pulsa Añadir alias para guardar el primero." | empty-state hint |
| `copy_config_alias_add_cta` | "Añadir alias" | FAB label |
| `copy_config_alias_relational_hint` | "¿Cómo le llama? (mi hija, el médico, …)" | alias text-field label |
| `copy_config_alias_search_hint` | "Buscar contacto" | search field label |
| `copy_config_alias_delete_confirm` | "¿Borrar este alias? No se puede deshacer." | delete dialog body |
| `copy_config_alias_delete_yes` | "Sí, borrar" | delete confirm button |
| `copy_config_alias_cancel` | "Cancelar" | shared cancel label |
| `copy_config_alias_save` | "Guardar" | save button |
| `copy_config_alias_source_learned` | "aprendido" | source badge |
| `copy_config_alias_source_explicit` | "lo apunté yo" | source badge |
| `copy_config_alias_source_preloaded` | "precargado" | source badge |
| `copy_config_alias_use_count` | "Usado %1$d veces" | use-count tail label |

That's 13 strings — pin: this brief authoritatively counts 13 (the brief's
checklist said "11"; the actual list above is 13 because two badge strings
were under-counted in the PRD line — pin the correct count). The PRD entry
should be updated to read "13 new strings" during implementation.

**`brand-design` COPY table update**: add a new "Aliases (Phase 8 — SF-8.2)"
section with all 13 rows; provenance `(NEW — SF-8.2)`.

## Performance Considerations

- `LazyColumn` for the alias list — handles hundreds of aliases cleanly
  (Fran is unlikely to have > 30, but the structure scales).
- `ContactsProvider.findAll()` loaded ONCE per dialog open, not per row.
  The result list is stable inside the dialog's lifetime; if the user
  modifies the contacts DB while the dialog is open, the next open picks
  it up (acceptable).
- Search filter applied with `remember(query) { contacts.filter { it.displayName.curroNormalize().contains(query.curroNormalize()) } }` — `curroNormalize` is the
  cheap utility from `data/apps/StringNormalization.kt`.
- All Room writes wrapped in `withContext(ioDispatcher)` (Room's suspend
  methods already dispatch; the explicit wrapper protects when the impl
  pre-computes anything else).
- Coil `AsyncImage` for contact photos (if `contactsProvider` exposes them
  — it does via `Contact.photoUri`).

## Testing Requirements

- [ ] **FSM**: N/A.
- [ ] **`AliasesViewModel`** — JVM with Turbine + fakes (`FakeAliasRepository`
      from SF-7.2/SF-7.3 + `FakeContactsProvider`); 8 cases:
      1. `uiState_emitsEmptyList_whenRepoEmpty`.
      2. `uiState_emits_aliases_inOrderFromRepo`.
      3. `onEvent_StartAdd_loadsContacts_andOpensDialog`.
      4. `onEvent_StartEdit_loadsContacts_andOpensDialog_withPreFilledInitial`.
      5. `onEvent_Save_withNoPrior_callsLearnWithEXPLICITSource`.
      6. `onEvent_Save_withSameAliasTextDifferentContact_callsLearnOnlyOnce`.
      7. `onEvent_Save_withRenamedAlias_callsDeleteThenLearn_inOrder`.
      8. `onEvent_Save_blankAlias_isNoOp_doesNotCallRepo`.
      9. `onEvent_ConfirmDelete_callsRepoDelete`.
      10. `onEvent_StartAdd_permissionDenied_opensDialog_withEmptyContactList`.
- [ ] **`RoomAliasRepository.delete`** —
      `app/src/test/java/com/curro/app/data/contacts/RoomAliasRepositoryDeleteTest.kt`:
      1. `delete_byAlias_removesRow`.
      2. `delete_byUnknownAlias_isNoOp_noThrow`.
      3. `delete_normalisesAliasBeforeLookup` (e.g. `delete("Mi HIJA")` matches
         the row stored as `"mi hija"`).
- [ ] **Instrumented UI tests on `AliasesContent`**
      (`app/src/androidTest/java/com/curro/app/presentation/config/sections/aliases/AliasesContentTest.kt`):
      1. `aliasRow_renders_alias_displayName_andSourceBadge`.
      2. `addCta_invokes_StartAdd_event`.
      3. `tappingAliasRow_invokes_StartEdit`.
      4. `longPressingAliasRow_invokes_RequestDelete`.
      5. `addOrEditDialog_searchField_filtersContactList`.
      6. `addOrEditDialog_saveButton_disabledWhenAliasBlank`.
      7. `addOrEditDialog_saveButton_disabledWhenNoContactSelected`.
      8. `deleteConfirmDialog_yesButton_invokes_ConfirmDelete`.
- [ ] **Dark-mode + large-font previews** on `AliasRow`,
      `AddOrEditAliasDialog`, `DeleteAliasConfirmDialog`, `EmptyAliasesState`.
- [ ] **Real Redmi 15 smoke**:
      - Add `mi nieta` → real contact → save → speak "llama a mi nieta" →
        Curro resolves it correctly.
      - Edit an existing alias → contact changes → row updates → next
        utterance resolves to the new contact.
      - Rename an alias text → old text no longer resolves; new text does.
      - Long-press → delete → confirm → row gone.
      - Revoke `READ_CONTACTS` (Settings → Apps → Curro → Permissions →
        Contacts → Disallow) → reopen the dialog → permission-missing line
        + disabled Save (no crash).

## Implementation Notes

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/presentation/config/sections/aliases/AliasesScreen.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/aliases/AliasesViewModel.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/aliases/AliasRow.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/aliases/AddOrEditAliasDialog.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/aliases/DeleteAliasConfirmDialog.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/aliases/EmptyAliasesState.kt`
- `app/src/test/java/com/curro/app/presentation/config/sections/aliases/AliasesViewModelTest.kt`
- `app/src/test/java/com/curro/app/data/contacts/RoomAliasRepositoryDeleteTest.kt`
- `app/src/androidTest/java/com/curro/app/presentation/config/sections/aliases/AliasesContentTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/domain/repository/AliasRepository.kt`
  (+1 method `delete(alias)`).
- `app/src/main/java/com/curro/app/data/contacts/RoomAliasRepository.kt`
  (+1 implementation).
- `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
  (1 placeholder swap).
- `app/src/main/res/values/strings.xml` (+13 entries).
- `.claude/skills/brand-design/SKILL.md` (+13 rows in a new "Aliases
  (Phase 8 — SF-8.2)" section).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.2 Alias management UI. |
