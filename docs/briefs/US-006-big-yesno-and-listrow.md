# US-006 — `BigYesNoRow` + `BigListRow` (the two punted shared big components)

> Implementation brief for **SF-0.5 (rest)** (`docs/master-plan.md` → Phase 0).
> US-004 shipped `BigPrimaryButton` and `BigCard` and deliberately deferred the
> other two shared big components (Q6 / A12). US-005 then locked the brand
> decision that was the open question gating `BigYesNoRow`: `primary` = SÍ
> (terracota), `secondary` = NO (olivo) — never `error`-red. With both
> predecessors in place, US-006 lands the punted components, completes the
> `launcher-ui` rule-4 set of shared bricks (`BigPrimaryButton`, `BigCard`,
> `BigYesNoRow`, `BigListRow`), and ships the two `copy_yes` / `copy_no`
> resource strings the canonical COPY table missed.
>
> **Architect involvement: NOT REQUIRED.** Every load-bearing decision was
> resolved upstream: US-004's A1–A14 pinned spacing/Dimens/haptic/clickable
> contracts; US-004 Q6 explicitly settled the punt rationale and US-005's brand
> fill-in settled the open SÍ-vs-NO colour treatment (`primary` / `secondary`,
> never `error`); US-005 lines 322–325 of `brand-design` explicitly say "NO is
> never `error`-coloured" and provide the canonical sketches at lines 349–404.
> US-006 ships those sketches as production code, with senior-first regression
> previews; nothing structural is open. The brief pins the open shape questions
> the user flagged (separate-vs-reuse, slot sizes, background) and the developer
> follows.

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `BigYesNoRow` + `BigListRow` shared big components |
| **US ID** | US-006 |
| **SF ID** | SF-0.5 (rest) — master-plan |
| **Phase** | 0 — Project foundation |
| **Status** | In Progress |
| **Created** | 2026-05-14 |
| **Modified** | 2026-05-14 |
| **PM Owner** | Fran (Claude `android-product-analyst`) |
| **Architect** | Not required — predecessors (US-004 A1–A14, US-005 brand fill-in) pinned every load-bearing decision; remaining choices are local to two ~30-line composables. |

## Summary

Land the two shared big components US-004 deliberately punted under Q6:
**`BigYesNoRow`** (the SÍ/NO confirmation-overlay buttons row) and
**`BigListRow`** (the leading-icon + title + optional-subtitle + trailing slot
row used by the contact picker, alias-learning list, message cards screen,
and the config menu). Both files live under
`app/src/main/java/com/curro/app/presentation/common/` alongside US-004's
`BigPrimaryButton.kt` and `BigCard.kt`. Both follow exactly the established
patterns: ≥ 96 dp interactive surface (`Dimens.MinTapTarget` /
`Dimens.BigButtonHeight` / `Dimens.BigRowHeight`), `HapticFeedbackType.LongPress`
on every clickable surface (US-004 A10), semantic-tokens-only access via
`MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` / `CurroSpacing.*`
/ `Dimens.*` (no raw `Color(0xFF…)` / `.sp` literals; the two leading/trailing
slot `.dp` values follow the A11 precedent BigCard set with
`Dimens.CardElevation` — see *Open shape questions, pinned* §3). Four canonical
`@Preview` variants (light, dark, `fontScale = 1.5f`, `fontScale = 2.0f`) on
`widthDp = 412` per file, with the 2.0× preview as the senior-first regression
guard.

On top of the two composables, US-006 adds two resource strings:
**`copy_yes = "SÍ"`** and **`copy_no = "NO"`**. These were absent from US-005's
54-entry canonical COPY table (US-005 owned the strings the FSM *speaks*; the
button-label strings the user *taps* slipped through). The brief documents them
as additions to that table — appended to the `brand-design` skill's
"Confirmation (Phase 6)" sub-section without re-editing the rest of the table.

US-006 ships **no real consumer**. The confirmation overlay
(`ConfirmationOverlay`, Phase 5), the contact picker (`ContactPickerScreen`,
Phase 4–5), the message cards screen (`MessageCardsScreen`, Phase 4), and the
config menu (`ConfigMenuScreen`, Phase 8) all land in later SFs and *will*
consume these components. US-006 is the final brick-laying step of Phase 0's
shared-component story; the surfaces are downstream.

Spec ref: `docs/curro-spec-v1.0.md` §11 (the visual surfaces — SÍ / NO
buttons huge and well-separated, the picker list of huge rows for the
3-Marías disambiguation, the message cards grouped by sender, the config
menu sections). Master-plan ref: SF-0.5 (rest). Upstream decisions consumed
verbatim: US-004 A1 (7-step spacing), A2 (`Dimens` object — including
`Dimens.CardElevation` precedent), A4 (contrast floors held by US-005's real
palette), A10 (`HapticFeedbackType.LongPress`), A11 (the nullable-onClick
shape — N/A here because both components are always-clickable, but the
*lint-posture* precedent for the inline `.dp` exception is what governs the
56/48 slot sizes; see §3 below), A12 (Q6 punt → US-006 lands it). Brand
upstream: `brand-design` lines 65–67 (`primary` = SÍ, `secondary` = NO),
lines 322–325 (NO is **never** `error`-coloured), lines 349–404 (the canonical
sketches the developer turns into production code).

## Scope

### In Scope

- **`app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt`** — a new
  file. The composable signature:

  ```kotlin
  /**
   * Curro's SÍ/NO confirmation buttons row — the brick for the confirmation
   * overlay (`¿Llamo a Pepito?`), every future `needs_confirmation = true` action
   * the FSM lands in `confirming`, and the alias-learning subflow's confirmation
   * step (`¿Es alguno de estos?` → yes/no fallback).
   *
   * Two filled buttons side-by-side. SÍ in `primary` (terracota — warm
   * affirmation); NO in `secondary` (olivo — calm rejection). NO is NEVER
   * `error`-coloured: saying "no" is not a failure condition (`brand-design`
   * line 322; spec §2 Curro's voice — fail comprehensibly, not punitively).
   *
   * Senior-first contract:
   * - Each button ≥ [Dimens.BigButtonHeight] tall via [Modifier.heightIn]
   *   (dp, independent of `fontScale`).
   * - [HapticFeedbackType.LongPress] on each press (US-004 A10): confirms the
   *   press registered with tactile certainty before the screen updates —
   *   essential for reduced fine motor control.
   * - Generous gap: `Arrangement.spacedBy(CurroSpacing.l)` (24 dp) between
   *   buttons, so a slip from SÍ does not land on NO and vice versa.
   * - Labels at [MaterialTheme.typography.titleLarge] (22 sp — comfortable
   *   button label, mirrors [BigPrimaryButton]).
   *
   * @param onYes Action fired (after the haptic) when SÍ is pressed.
   * @param onNo  Action fired (after the haptic) when NO is pressed.
   * @param modifier Applied to the [Row]; callers add outer padding / weight here.
   * @param yesText Override the default label ("SÍ" via [R.string.copy_yes]).
   *   Use when a non-default affirmation reads better — e.g. an alternate
   *   "VALE" if a future flow demands it. Leave defaulted in normal use.
   * @param noText Override the default label ("NO" via [R.string.copy_no]).
   * @param enabled When false both buttons are rendered disabled; haptic and
   *   onYes/onNo do not fire. Useful while a confirmation is being processed.
   */
  @Composable
  fun BigYesNoRow(
      onYes: () -> Unit,
      onNo: () -> Unit,
      modifier: Modifier = Modifier,
      yesText: String = stringResource(R.string.copy_yes),
      noText: String = stringResource(R.string.copy_no),
      enabled: Boolean = true,
  )
  ```

  Implementation shape:
  - `Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CurroSpacing.l))`.
  - Two `Button(...)`s, each with `Modifier.weight(1f).heightIn(min = Dimens.BigButtonHeight)`.
  - SÍ button: `colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)`.
  - NO button: `colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)`.
  - **Both** buttons fire `haptic.performHapticFeedback(HapticFeedbackType.LongPress)` inside their `onClick` lambda before invoking `onYes` / `onNo`.
  - Shape: `MaterialTheme.shapes.medium` (matches `BigPrimaryButton` / `BigCard`).
  - Label: `Text(text = yesText|noText, style = MaterialTheme.typography.titleLarge)`.
  - `enabled` propagated to both `Button`s' `enabled` parameter.

  **Why NOT reuse `BigPrimaryButton` internally for SÍ:** `BigPrimaryButton`
  hard-codes `MaterialTheme.colorScheme.primary` and is documented as "the
  primary CTA brick". Threading a `colorVariant: ButtonColorVariant` parameter
  through it to support secondary would muddle its KDoc contract and force
  every other consumer (the future "Más apps", "Hazme tu pantalla de inicio")
  to think about a colour they never need. **`BigYesNoRow` is a peer
  composition**, not a wrapper; the SÍ button it contains is internally
  *equivalent* to `BigPrimaryButton`'s body but inline, and the NO button is
  its `secondary`-coloured sibling. The 12-line duplication is the right cost.

  Required `@Preview`s (private, in the same file):
  - `BigYesNoRowLightPreview` — `widthDp = 412, heightDp = 200`
  - `BigYesNoRowDarkPreview` — `uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 200`
  - `BigYesNoRowLargeFontPreview` — `widthDp = 412, heightDp = 240, fontScale = 1.5f`
  - `BigYesNoRowHugeFontPreview` — `widthDp = 412, heightDp = 320, fontScale = 2.0f`

  Each preview: `CurroTheme { Surface(Modifier.padding(CurroSpacing.m)) { BigYesNoRow(onYes = {}, onNo = {}) } }`. The 2.0× preview is the senior-first regression: at 22 sp × 2.0 = 44 sp the two 2-character labels ("SÍ", "NO") still fit comfortably inside two `weight(1f)` buttons on a 412 dp width minus 32 dp horizontal padding minus 24 dp spacing — well over 170 dp per button. Confirmed by the developer with the live preview; recorded in the PR.

- **`app/src/main/java/com/curro/app/presentation/common/BigListRow.kt`** — a new
  file. The composable signature:

  ```kotlin
  /**
   * Curro's clickable big list row — the brick for the contact picker
   * (the 3-Marías disambiguation per spec §6 flow 3), the alias-learning
   * list (`¿Es alguno de estos?` per flow 4), the message cards screen
   * grouped by sender (flow 5), and the config menu sections (spec §9).
   *
   * Layout: optional leading slot (square ≥ 56 dp — room for a contact
   * photo via Coil [AsyncImage], an app icon, a glyph) + title (large,
   * primary text) + optional subtitle (smaller, secondary text) +
   * optional trailing slot (square ≥ 48 dp — chevron, count badge,
   * checkmark).
   *
   * Senior-first contract:
   * - Min height [Dimens.BigRowHeight] (96 dp) via [Modifier.heightIn] —
   *   independent of `fontScale`.
   * - [HapticFeedbackType.LongPress] on press (US-004 A10).
   * - Background [Color.Transparent] — the row inherits its parent's
   *   surface (a [LazyColumn] over `MaterialTheme.colorScheme.surface`,
   *   or the inside of a [BigCard] which uses `surfaceVariant`). A future
   *   "selected" variant can opt into a tinted background; US-006 does not
   *   ship that variant.
   * - Title at [MaterialTheme.typography.titleLarge] /
   *   [MaterialTheme.colorScheme.onSurface]; subtitle (if present) at
   *   [MaterialTheme.typography.bodyMedium] /
   *   [MaterialTheme.colorScheme.onSurfaceVariant].
   * - Horizontal padding [CurroSpacing.m]; vertical padding [CurroSpacing.s];
   *   the height enforcement does the heavy lifting, the padding only sets
   *   the text inset off the edges.
   *
   * @param title The primary text. Required; the row is meaningless without it.
   * @param onClick Action fired (after the haptic) when the row is pressed.
   * @param modifier Applied to the row; callers add outer padding here.
   * @param subtitle Optional secondary line below the title (e.g. a phone
   *   number, an app's last-used time, a config setting's current value).
   * @param leading Optional leading content slot (a contact photo, an app
   *   icon, a glyph). Rendered inside a [Modifier.size] square of
   *   [LeadingSlotSize] (56 dp) — the slot fixes the size, the caller fills
   *   the content. Null → no leading area, title aligns to the row's start.
   * @param trailing Optional trailing content slot (a chevron, a count
   *   badge, a checkmark, a "selected" tick). Rendered inside a
   *   [Modifier.size] square of [TrailingSlotSize] (48 dp). Null → no
   *   trailing area, the subtitle/title block extends to the row's end.
   * @param contentDescription Overrides the default content description
   *   (`title` + `subtitle` joined). Use when the row's voiced affordance
   *   differs from its visible text (e.g. an app row whose `title` is
   *   "WhatsApp" but whose screen-reader announcement should be
   *   "WhatsApp, abrir"). Default null → derived from `title`/`subtitle`.
   * @param enabled When false the row is rendered with reduced opacity and
   *   the click + haptic do not fire.
   */
  @Composable
  fun BigListRow(
      title: String,
      onClick: () -> Unit,
      modifier: Modifier = Modifier,
      subtitle: String? = null,
      leading: (@Composable () -> Unit)? = null,
      trailing: (@Composable () -> Unit)? = null,
      contentDescription: String? = null,
      enabled: Boolean = true,
  )
  ```

  Implementation shape:
  - File-private constants at the top of the file (see §3 below for why
    they live here and not on `Dimens`):
    ```kotlin
    private val LeadingSlotSize: Dp = 56.dp
    private val TrailingSlotSize: Dp = 48.dp
    ```
    Each carries a one-line KDoc explaining the senior-first rationale (a
    photo / icon big enough for an aged eye to recognise without strain;
    the trailing slot is smaller because chevrons + count badges read at
    a smaller size). If detekt's `MagicNumber` rule fires on `56` / `48`
    in `presentation/common/`, the developer adds `@Suppress("MagicNumber")`
    with a one-line `// US-006: leading/trailing slot — slot sizes are
    LOCAL to BigListRow, not cross-SF invariants; see brief §3` rationale.
    This mirrors US-004 A11's `BigCard` precedent where `2.dp` was
    initially considered for inline before being promoted to
    `Dimens.CardElevation` *because* it became a cross-component
    invariant — the slot sizes here are NOT cross-component; they live
    here.
  - The row body: `Row(modifier = modifier.fillMaxWidth().heightIn(min = Dimens.BigRowHeight).clickable(enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }.padding(horizontal = CurroSpacing.m, vertical = CurroSpacing.s).then(...semantics for contentDescription if non-null...), verticalAlignment = Alignment.CenterVertically)`.
  - Leading slot: if `leading != null`, render `Box(modifier = Modifier.size(LeadingSlotSize), contentAlignment = Alignment.Center) { leading() }` followed by `Spacer(Modifier.width(CurroSpacing.m))`.
  - Title/subtitle column: `Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CurroSpacing.xs))` containing the title `Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)` and, if `subtitle != null`, the subtitle `Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)`.
  - Trailing slot: if `trailing != null`, append `Spacer(Modifier.width(CurroSpacing.m))` then `Box(modifier = Modifier.size(TrailingSlotSize), contentAlignment = Alignment.Center) { trailing() }`.
  - Semantics: when `contentDescription` is non-null, apply
    `Modifier.semantics(mergeDescendants = true) { this.contentDescription = it }` on the row's `Modifier` chain.

  Required `@Preview`s (private, in the same file) — each renders **two
  rows in a `Column`** so the developer eyeballs both a row-with-everything
  (leading icon + title + subtitle + trailing chevron) and a row-with-the-minimum
  (title only). The two-rows-per-preview shape mirrors `BigCard`'s
  read-only + clickable preview pairing pattern:
  - `BigListRowLightPreview` — `widthDp = 412, heightDp = 400`
  - `BigListRowDarkPreview` — `uiMode = UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 400`
  - `BigListRowLargeFontPreview` — `widthDp = 412, heightDp = 520, fontScale = 1.5f`
  - `BigListRowHugeFontPreview` — `widthDp = 412, heightDp = 700, fontScale = 2.0f`

  Representative content for the previews (illustrative; English placeholder
  text — Spanish production copy comes through `stringResource` at the
  Phase 1+ call sites, NOT inside the preview, per the US-004 §Senior-UX &
  Copy rule):
  - Row 1 — a contact picker row: `leading = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(40.dp)) }`, `title = "María García"`, `subtitle = "+34 600 12 34 56"`, `trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(32.dp)) }`.
  - Row 2 — an app-list row with the minimum content: `leading = { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(40.dp)) }`, `title = "Sample app"`, no subtitle, no trailing.

  The Icon sizes (40 dp / 32 dp) inside the slot Boxes use raw `.dp` literals
  in the **preview code** (which is `private` and lives inside the same
  file as the composable). Detekt's `MagicNumber` exclude scope does NOT
  cover `presentation/common/` so these will fire; the developer either
  (a) extracts them to file-private constants `private val PreviewLeadingIconSize = 40.dp` /
  `PreviewTrailingIconSize = 32.dp` at the top of the file (preferred — keeps
  the preview self-documenting), or (b) wraps the preview composable bodies
  in `@Suppress("MagicNumber") /* preview content; not load-bearing */`.
  Pick one; the brief leaves the choice to the developer because it's
  preview-cost only, not production code.

- **`app/src/main/res/values/strings.xml`** — append two new entries to the
  COPY block, with provenance comments. Suggested placement: in a new
  "Confirmation buttons" sub-block immediately before the existing
  `<!-- Execution announcements -->` block, so the document order mirrors
  the COPY-table sub-section order in `brand-design`. The entries:

  ```xml
  <!-- Confirmation buttons -->
  <!-- BigYesNoRow SÍ default — US-006 / SF-0.5 (rest) -->
  <string name="copy_yes">SÍ</string>
  <!-- BigYesNoRow NO default — US-006 / SF-0.5 (rest); NO is secondary-coloured (olivo), never error-red — `brand-design` line 322 -->
  <string name="copy_no">NO</string>
  ```

  The all-caps form ("SÍ" / "NO" rather than "Sí" / "No") matches the
  visual weight `BigPrimaryButton`'s previews use ("PRIMARY ACTION" in the
  existing previews is mixed case, but spec §6's confirmation overlay
  example uses SÍ / NO in caps as the visible-label form — see also the
  launcher-ui sketch on lines 109–111 of the skill: `✅ SÍ / ❌ NO`).

- **`.claude/skills/brand-design/SKILL.md`** — a half-line append to the
  "Confirmation (Phase 6)" sub-section table (currently at lines 491–500
  of the skill, before the `copy_confirm_timeout` row). Add two rows:

  ```
  | `copy_yes` | SÍ | (NEW) US-006 — BigYesNoRow default affirmation label |
  | `copy_no` | NO | (NEW) US-006 — BigYesNoRow default rejection label; NO is `secondary`-coloured, never `error`-red |
  ```

  **Do NOT re-edit the rest of the skill.** US-005 owns the canonical
  COPY table; US-006 appends two rows. If a future SF (Phase 6's
  confirmation overlay) needs additional confirmation-button strings —
  e.g. "VALE" or "AHORA NO" — that SF appends; this SF does not
  pre-populate.

### Out of Scope (each is its own later SF)

- **The real consumers of these components.** The confirmation overlay
  (`ConfirmationOverlay` — Phase 5 / SF-5.x — exercises `BigYesNoRow` for
  the SÍ/NO buttons of `confirming` state); the contact picker
  (`ContactPickerScreen` — Phase 4 / SF-4.7 around `CallContactHandler`'s
  ambiguity flow — exercises `BigListRow` for the 3-Marías list); the
  message cards screen (`MessageCardsScreen` — Phase 4 / SF-4.5 around
  `read_all_unread_whatsapp` — uses `BigCard` for the message cards and
  may use `BigListRow` for the per-sender section header — TBD by SF-4.5);
  the config menu (`ConfigMenuScreen` — Phase 8 / SF-8.x — exercises
  `BigListRow` for each settings entry). None of these surfaces land in
  US-006.

- **`BigIconButton`, `BigSwitch`, `BigSlider`, `BigCheckbox`** and any
  other "Big*" component the config menu or future overlays may need.
  Out of scope. The config menu (Phase 8) can introduce these as it needs
  them; they're not part of `launcher-ui` rule 4's set.

- **A specialised `AppTile` composable** for the launcher home (Phase 1
  / SF-1.4). `AppTile` will compose on top of `BigCard` (or, if SF-1.4
  prefers a row layout, `BigListRow`); US-006 does **not** decide that
  layout — it's an open call SF-1.4 makes once it has a real consumer
  surface. US-006's `BigListRow` is the *generic* row; specialised tiles
  layer on top.

- **A "selected" / "highlighted" variant of `BigListRow`** (for the
  read-aloud-now highlight in `MessageCardsScreen`, or the
  currently-selected disambiguation candidate in `ContactPickerScreen`).
  The current message-card highlight pattern is `BigCard` with
  `surfaceVariant` background — `MessageCardsScreen` may not need a
  `BigListRow` variant at all. Defer the call to SF-4.5. Adding a
  `selected: Boolean` parameter to `BigListRow` now would commit Curro
  to a highlight visual treatment before its consumer exists; US-006
  ships the unselected base case only.

- **A two-line subtitle option** on `BigListRow`. Today the subtitle is
  `maxLines = 1, overflow = Ellipsis`. If a config-menu row needs two
  lines (e.g. a long "current value" string), SF-8.x adds a
  `subtitleMaxLines: Int = 1` parameter; US-006 ships the single-line case.

- **A `selected` state, a `loading` state, a `swipeable` shape** on either
  component — none of these are needed by any near-term consumer; YAGNI.

- **A `disabledAlpha` parameter or visual tuning for the `enabled = false`
  state** — Material's default disabled treatment is what ships; if the
  senior-first review on the real device shows it doesn't read clearly
  enough, SF-0.5-followup tunes it. Not now.

- **A custom detekt rule banning `MaterialTheme.colorScheme.error`
  on a Button container colour** — would mechanically enforce the
  "NO is never `error`-coloured" rule. Deferred alongside the
  No-Double-Padding rule and the raw-literal rule from US-003 / US-004 —
  same `tools/detekt-rules/` punt list. For US-006 the rule is enforced
  by the grep AC item.

- **A localisation / `values-es/` rework** — Spanish remains the default
  locale; no other locale lands. (US-005 set this precedent; US-006 does
  not revisit.)

- **Real-content `@Preview`s using Coil `AsyncImage`** — Coil is on the
  catalog (`CLAUDE.md` → Dependencies) but adding it to the preview-time
  classpath touches the Gradle catalog and risks an off-line preview
  failing in CI. The previews use `Icon` stand-ins (`Icons.Filled.Person`,
  `Icons.Filled.PlayArrow`, `Icons.AutoMirrored.Filled.KeyboardArrowRight`)
  — the runtime consumers (Phase 1+ surfaces) pass `AsyncImage` into the
  `leading = { }` slot and the component just renders whatever the caller
  puts there.

## Open shape questions, pinned

This section pins the four shape choices the brief makes for the developer.
Each was explicitly flagged in the parent request; each is decided in
this brief, no architect or PM-decision-after-the-fact step is required.

### 1. `BigYesNoRow` reuses `BigPrimaryButton` internally? — **No, separate implementation.**

`BigPrimaryButton`'s body hard-codes
`containerColor = MaterialTheme.colorScheme.primary` /
`contentColor = MaterialTheme.colorScheme.onPrimary` (US-004 lines 69–70).
The NO button needs `secondary` / `onSecondary`. Three options exist:

1. Add a `containerColor: Color = MaterialTheme.colorScheme.primary,
   contentColor: Color = MaterialTheme.colorScheme.onPrimary` parameter
   to `BigPrimaryButton` (or, less leakily, an
   `colorVariant: ButtonColorVariant` enum).
2. Add a separate `BigSecondaryButton` composable.
3. Inline both buttons inside `BigYesNoRow` as peer compositions (the
   pattern in `brand-design`'s sketch).

**Pinned: option 3, inline.** Reasons:
- `BigPrimaryButton`'s KDoc is "the primary CTA brick — SÍ, 'Más apps',
  'Hazme tu pantalla de inicio', and every overlay primary action". Adding
  a colour-variant parameter dilutes that single-purpose semantics: every
  future caller has to wonder "should I pass `colorVariant`?" Most
  shouldn't.
- A separate `BigSecondaryButton` adds a third top-level composable to
  the `presentation/common/` surface, when the *only* near-term consumer
  of "secondary-coloured big button" is `BigYesNoRow` itself. YAGNI.
- The 12-line duplication inside `BigYesNoRow` is local; both buttons
  read top-to-bottom in one file; a code-review eye catches any drift
  immediately. If a second non-`BigYesNoRow` consumer of a
  secondary-coloured big button ever appears (none plausible in
  Phases 1–8), promote the duplicated body to a private helper at that
  time. Reversibility: O(15 min).

### 2. NO button — filled `Button` with `secondary`/`onSecondary`, or `FilledTonalButton` with `secondaryContainer`/`onSecondaryContainer`? — **Filled `Button` with `secondary`.**

`brand-design`'s sketch (line 357) uses `FilledTonalButton` with
`secondaryContainer`. Two valid shapes:

- **A. Filled `Button` with `secondary` / `onSecondary`** — SÍ and NO are
  visually parallel (both are filled `Button`s, both saturated colours,
  only the colour distinguishes affirmation from rejection).
- **B. `FilledTonalButton` with `secondaryContainer` / `onSecondaryContainer`** —
  NO is slightly muted (a sage-green tonal fill) and SÍ is the louder
  call.

**Pinned: option A.** Reasons:
- **Visual parity matters more than visual hierarchy here.** In the
  confirmation overlay the FSM puts both buttons in front of the user
  with equal weight — the user picks one or the other. NO is not a
  "secondary action" in the M3 sense (the way a `TextButton` "Cancel"
  next to a `Button` "OK" is); it's a *peer* answer. Painting NO with
  a tonal fill subtly biases the user toward SÍ, which is the opposite
  of what spec §2 ("efficient, close, not servile") + spec §6 flow 2
  ("`needs_confirmation = true` for irreversible cost") want — the user
  must feel equally free to say no.
- **Contrast holds either way** (`onSecondary` on `secondary` = ~6.8:1
  light / ~9.0:1 dark; `onSecondaryContainer` on `secondaryContainer`
  = 12.6:1 light / 7.9:1 dark — both clear the floor). The choice is
  semantic, not contrast-driven.
- `brand-design`'s sketch ships as a *sketch* (line 349 — `(deferred to
  SF-0.5 — sketch for reference)`); US-006 is the SF that lands the
  production shape and is free to depart from the sketch with rationale.

Note: if real-device review of the senior-first regression flags the
saturated NO as "too loud", SF-0.5-followup can swap to option B (a
two-line colour change, no signature change). Reversibility: O(2 min).

### 3. `LeadingSlotSize` / `TrailingSlotSize` — `Dimens` entry or local `private val`? — **Local `private val` in `BigListRow.kt`.**

`Dimens.kt` is the load-bearing senior-first invariants file: every entry
is a "mechanical invariant that every touch target / mic-button / card /
list-row in the app must respect" (US-004 A2 KDoc). The entries today —
`MinTapTarget`, `MicButtonMinHeightFraction`, `BigButtonHeight`,
`BigRowHeight`, `LargeIconSize`, `CardElevation` — are each consumed by
≥ 2 components (or, in `MicButtonMinHeightFraction`'s case, by 1
component but pre-pinned as a spec-§11 number). The 56 dp / 48 dp slot
sizes here are consumed by **one** component (`BigListRow`); they're
implementation details of its layout, not cross-cutting invariants.

Adding them to `Dimens.kt` would:
- Touch a US-004 / US-005-pinned load-bearing file (the brief's AC has
  an explicit "`Dimens.kt` byte-identical to US-005 state" item).
- Signal to future SF authors that "leading slot size" is a global
  contract — it isn't.
- Force the next consumer of a "row with a leading slot" (e.g. SF-1.4's
  `AppTile`) to use this specific size, when SF-1.4 might want a
  bigger one for app icons (which are typically 48 dp adaptive — a 56 dp
  slot is generous; an 80 dp tile-slot might be better in the launcher).

**Pinned: local `private val`s.** When SF-1.4 lands and a real second
consumer exists, *that* SF decides whether to promote. Until then,
the slot sizes live where their semantics live: inside `BigListRow.kt`.

Detekt's `MagicNumber` rule will fire on `56` and `48`; the developer
either suppresses with a one-line rationale or — if the suppress reads
worse than alternatives — opens a per-rule micro-exclude scoped to
`**/presentation/common/BigListRow.kt`. The brief recommends the
suppress (less detekt-config churn). This mirrors the US-004 A11
discussion about `BigCard`'s elevation: the precedent was "promote to
`Dimens` if cross-component, inline-with-suppress if local". Here the
slot sizes are local.

### 4. `BigListRow` background — `Color.Transparent` or `MaterialTheme.colorScheme.surface`? — **`Color.Transparent`.**

Two shapes are both internally consistent:

- **A. `background = Color.Transparent`** — the row inherits its parent's
  surface (a `LazyColumn` over `MaterialTheme.colorScheme.surface`, or
  the inside of a `BigCard` which uses `surfaceVariant`).
- **B. `background = MaterialTheme.colorScheme.surface`** — the row paints
  its own background; consumers don't think about it.

**Pinned: option A.** Reasons:
- The contact picker (spec §6 flow 3) renders a list of `BigListRow`s in
  a `LazyColumn` whose Scaffold paints `surface` already. Painting `surface`
  on each row over `surface` is a no-op (and may trip M3's elevation overlay
  on dark mode if `surfaceTint` is configured — `brand-design` line 101 ties
  `surfaceTint = primary`).
- The alias-learning list and the config menu sit on `surface` too; same
  no-op.
- The future "selected" variant (out of scope per *Scope → Out of Scope*)
  will want to paint *something different* (likely
  `MaterialTheme.colorScheme.surfaceVariant` with reduced opacity); having
  the unselected case be `Transparent` means the variant is a clean
  opt-in (`if (selected) background(...) else <no-op>`), not a
  background-color-swap.
- The contrast contract holds against `surface` in both light and dark
  (title in `onSurface` over `surface` = 17.3:1 light / 16.0:1 dark — well
  above the ≥ 7:1 body aspirational floor; subtitle in `onSurfaceVariant`
  over `surface` ≈ 14.6:1 light / 13.9:1 dark from the same colour
  scheme).

Note: `BigCard` does the opposite (paints `surfaceVariant`) — but
`BigCard` is a *card* (a surface that stands out from its background by
design); `BigListRow` is a *row* (a line item *inside* a surface). The
two shapes are deliberately different.

## User Flows

US-006 has **no end-user flow**. It is developer-facing only — the only
"users" are a Curro developer rendering a Compose preview / running
Gradle locally and the CI runner on GitHub Actions.

### Flow 1: A developer adds the confirmation overlay in SF-5.x

(Demonstrates *why* US-006 is the precondition for the confirmation flows.)

1. SF-5.x developer creates `ConfirmationOverlay.kt`.
2. They render the prompt (`stringResource(R.string.copy_confirm_call, name)`)
   using `MaterialTheme.typography.displayMedium`.
3. Below the prompt, they drop a single line:
   `BigYesNoRow(onYes = onConfirm, onNo = onReject, modifier = Modifier.padding(CurroSpacing.l))`.
4. The SÍ button is terracota (`primary`), the NO button is olivo
   (`secondary`), both are 96 dp tall, both fire `LongPress` haptic, both
   labelled "SÍ" / "NO" via the resource strings US-006 shipped.
5. No new colour decision, no new typography decision, no new haptic
   wiring. The shared big brick is the contract.

### Flow 2: A developer adds the contact picker in SF-4.7 (the 3-Marías disambiguation)

1. SF-4.7 developer creates `ContactPickerScreen.kt`.
2. They render a `LazyColumn` of `BigListRow`s, one per candidate contact:
   ```kotlin
   items(candidates) { candidate ->
       BigListRow(
           title = candidate.displayName,
           subtitle = candidate.phoneNumber,
           leading = { AsyncImage(model = candidate.photoUri, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape)) },
           onClick = { onPick(candidate) },
       )
   }
   ```
3. Below the list, a "Ninguna de estas" row using the same `BigListRow`
   with `leading = null`, `subtitle = null`, `title = stringResource(R.string.copy_disambig_none_option)` (an existing US-005 string).
4. Every row is ≥ 96 dp tall, has haptic, has the title in `onSurface`,
   the phone-number subtitle in `onSurfaceVariant`. Senior-first by
   construction.

### Flow 3: A developer adds the config menu in SF-8.x

1. SF-8.x developer creates `ConfigMenuScreen.kt`.
2. Each settings entry is a `BigListRow`:
   ```kotlin
   BigListRow(
       title = "Voz del TTS",
       subtitle = "Voz: Lucía, velocidad: -10%",
       trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
       onClick = onOpenTtsSettings,
   )
   ```
3. Same shape, same contract, same haptic. Consistency across
   surfaces is the launcher-ui rule 4 payoff.

## Function-catalog Impact

**No catalog change.** US-006 ships no handler, no `CatalogFunction`, no
FunctionGemma prompt change, no JSON-schema entry. `domain/catalog/`
stays empty.

Cross-reference: the `function-catalog` skill is untouched until SF-3.x;
the first handler binding lands in SF-4.1 (`tell_time`).

## FSM States Touched

**None directly.** US-006 ships UI bricks, no FSM code. **Indirectly**:
every state-driven overlay that Phase 5 wires (`listening` /
`processing` / `confirming` / `executing` / `error_recovery`) will
consume `BigYesNoRow` and/or `BigListRow`:

- `confirming` (SF-5.x `ConfirmationOverlay`) — the canonical `BigYesNoRow`
  consumer.
- `executing` (SF-4.5 `MessageCardsScreen`) — likely uses `BigListRow` for
  per-sender section headers (TBD by SF-4.5).
- `confirming` / `error_recovery` (SF-4.7 `ContactPickerScreen` for the
  3-Marías disambiguation; SF-7 `AliasLearningPickerScreen` for the
  alias-learning subflow) — the canonical `BigListRow` consumers.

**None of those land in US-006; the components just enable them.**

Cross-reference: `voice-interaction` (the FSM is untouched), `launcher-ui`
(the surfaces are untouched).

## Android System Integrations & Permissions

**No system integrations**, **no runtime permissions** declared, **no
manifest change**. US-006 is pure UI Compose. The manifest stays exactly
as US-005 left it.

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| *(none in this SF)* | Each permission lands with the SF that needs it (spec §10) | N/A | N/A |

## On-device-model Impact

**No model impact.** US-006 ships no prompt change, no model loading,
no inference path, no `data/ml/` code. FunctionGemma / Gemma 3n are not
touched.

Cross-reference: `on-device-llm` (untouched).

## Android Specification

### Screens and Composables

US-006 ships **zero screens**. It ships **two shared big components**
that future screens consume. **File layout**:

```
presentation/common/
├── BigPrimaryButton.kt   # (US-004 — UNCHANGED)
├── BigCard.kt            # (US-004 — UNCHANGED)
├── BigYesNoRow.kt        # NEW — this SF
└── BigListRow.kt         # NEW — this SF

res/values/
└── strings.xml           # APPEND copy_yes + copy_no (two new lines)

.claude/skills/brand-design/
└── SKILL.md              # APPEND copy_yes + copy_no rows to "Confirmation (Phase 6)" sub-table
```

**No new ViewModel, no new screen, no new route, no new Hilt module.**

### ViewModels and State Management

No ViewModel changes. Both new composables are **stateless** — they
receive state (`title`, `subtitle`, `enabled`, slot composables) and
emit events (the `onYes` / `onNo` / `onClick` lambdas). The
`enabled` state, if it ever needs to be driven by a ViewModel, flows
from the caller's `UiState`.

### Navigation Routes

**No new routes.** Both components are consumed by state-driven overlays
(`ConfirmationOverlay` for `BigYesNoRow`) and existing screens
(`ContactPickerScreen`, `MessageCardsScreen`, `ConfigMenuScreen` —
each lands in its own future SF). The `CurroNavHost` shell is US-007's
job (SF-0.6).

### Hilt Modules

**No new Hilt module.** Both components are pure Compose; not DI.

### Composables by Feature (checklist)

- [ ] `BigYesNoRow.kt` (`presentation/common/`) → composable + 4 `@Preview`s
- [ ] `BigListRow.kt` (`presentation/common/`) → composable + 4 `@Preview`s (each preview renders ≥ 2 rows so the developer eyeballs both a row-with-everything and a row-with-the-minimum)
- [ ] `strings.xml` → 2 new `<string>` entries (`copy_yes`, `copy_no`) under a new "Confirmation buttons" sub-block
- [ ] `brand-design` skill → 2 new table rows under the "Confirmation (Phase 6)" COPY sub-section

### Material Design Components used

- `Button` (filled, primary slot for SÍ; filled, secondary slot for NO) — both wrapped by `BigYesNoRow`.
- `Row`, `Column`, `Box`, `Spacer` — for the `BigListRow` layout.
- `Text` — for title, subtitle, button labels.
- `LocalHapticFeedback` + `HapticFeedbackType.LongPress` — on every clickable surface (US-004 A10).
- `Modifier.heightIn`, `Modifier.fillMaxWidth`, `Modifier.weight`, `Modifier.clickable`, `Modifier.padding`, `Modifier.size`, `Modifier.semantics`.
- `Arrangement.spacedBy` — for the `BigYesNoRow` gap and the title/subtitle vertical spacing inside `BigListRow`.

**Not used (deliberately)** in this SF: `Card` (use `BigCard` if you need
a card — these are rows, not cards), `OutlinedButton`, `TextButton`,
`FilledTonalButton` (per §2 of *Open shape questions*), `Surface` (the
parent supplies it), `ListItem` (Material's `ListItem` doesn't honour
the senior-first 96 dp; `BigListRow` is the senior-first replacement).

## Acceptance Criteria

Concrete, checkable; expands the PRD AC list with the developer-facing
specifics:

- [ ] **`./gradlew assembleDebug` succeeds**, no new deprecation warnings introduced by US-006; the installed APK launches and `MainActivity` renders the text "Curro" (no regression vs US-005 — the call site is unchanged).
- [ ] **`./gradlew ktlintCheck detekt` is green** with the new files; the `MagicNumber` exclude on `**/presentation/theme/**` is **unchanged** (no widening). If detekt fires on the 56 dp / 48 dp slot sizes in `BigListRow.kt`, the developer adds a one-line `@Suppress("MagicNumber")` with KDoc rationale at the `private val` declaration — the suppress reads as the local-scope-only signal §3 of *Open shape questions, pinned* describes. **No new detekt rule entry, no new exclude path.**
- [ ] **`./gradlew testDebugUnitTest` is green** — US-006 ships no new JVM tests (the components are pure Compose; UI tests for them land in SF-0.5-followup / SF-5.x against real consumers). US-001's `SmokeTest` still passes.
- [ ] **`BigYesNoRow.kt` exists** at `app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt` with the signature documented in §Scope → In Scope, and:
  - `grep "MaterialTheme.colorScheme.primary" app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt` returns ≥ 1 match (SÍ).
  - `grep "MaterialTheme.colorScheme.secondary" app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt` returns ≥ 1 match (NO).
  - `grep "MaterialTheme.colorScheme.error" app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt` returns **0** matches (NO is never error-coloured — `brand-design` line 322).
  - `grep "HapticFeedbackType.LongPress" app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt` returns ≥ 2 matches (one per button).
  - `grep "Dimens.BigButtonHeight" app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt` returns ≥ 1 match.
  - `grep -E "Color\\(0xFF|\\.sp" app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt` returns **0** matches.
- [ ] **`BigListRow.kt` exists** at `app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` with the signature documented in §Scope → In Scope, and:
  - `grep "Dimens.BigRowHeight" app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` returns ≥ 1 match.
  - `grep "HapticFeedbackType.LongPress" app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` returns ≥ 1 match.
  - `grep "MaterialTheme.typography.titleLarge" app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` returns ≥ 1 match (title).
  - `grep "MaterialTheme.typography.bodyMedium" app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` returns ≥ 1 match (subtitle).
  - `grep "MaterialTheme.colorScheme.onSurface" app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` returns ≥ 1 match (title).
  - `grep "MaterialTheme.colorScheme.onSurfaceVariant" app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` returns ≥ 1 match (subtitle).
  - `grep -E "Color\\(0xFF|\\.sp" app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` returns **0** matches.
- [ ] **The leading + trailing slot sizes** are file-private `private val` declarations at the top of `BigListRow.kt` (`LeadingSlotSize = 56.dp`, `TrailingSlotSize = 48.dp`), each with a KDoc one-liner explaining its senior-first rationale; `grep -c "private val .*Size" app/src/main/java/com/curro/app/presentation/common/BigListRow.kt` returns ≥ 2.
- [ ] **`strings.xml` carries the two new entries** (`copy_yes = "SÍ"`, `copy_no = "NO"`) with provenance comments referencing US-006 / SF-0.5 (rest). `grep -E 'name="copy_yes"|name="copy_no"' app/src/main/res/values/strings.xml` returns 2 matches.
- [ ] **`brand-design` skill has 2 new rows** in the "Confirmation (Phase 6)" sub-table (lines 491–500 of the current skill — append before the `copy_confirm_timeout` row): `copy_yes` and `copy_no` rows with `(NEW) US-006` provenance and the NO row's explicit "secondary-coloured, never error-red" reminder. **No other line in the skill changes.**
- [ ] **Each component ships 4 `@Preview` variants** (light / dark / `fontScale = 1.5f` / `fontScale = 2.0f`) on `widthDp = 412`; every preview renders without clipping or layout collapse. The 2.0× preview is the senior-first regression: both `BigYesNoRow` buttons still render side-by-side ≥ 96 dp tall, and both `BigListRow` rows still render ≥ 96 dp tall with the title (and subtitle on row 1) legible. The developer eyeballs each preview in Android Studio and confirms in the PR description.
- [ ] **Haptic feedback fires** on each `BigYesNoRow` button press and on each `BigListRow` press — verified on the Redmi 15 / emulator with vibration enabled (manual; the developer states "haptic fires" in the PR).
- [ ] **`MainActivity.kt`, `CurroTheme.kt`, `Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Dimens.kt`, `BigPrimaryButton.kt`, `BigCard.kt`** are all byte-identical to their US-005 state — `git diff` against each returns no output. The "no token edits, no MainActivity touch, no canonical-brick touch" invariant is checked by the developer at commit time.
- [ ] **`grep -rn 'BigYesNoRow\|BigListRow' app/src/main/java | grep -v 'presentation/common/'`** returns **0 matches** — neither component has a real consumer in US-006 (consumers land Phase 1+). The previews inside `presentation/common/` are the only call sites.
- [ ] **Accessibility sweep on the two new files:** every `Icon` / `Image` placed inside a preview either has a `contentDescription` or `contentDescription = null` if decorative; `grep "Icon(" app/src/main/java/com/curro/app/presentation/common/BigListRow.kt app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt | grep -v "contentDescription"` returns **0** unaccounted lines.
- [ ] **No new permissions, no new Gradle dependency, no new Hilt module, no new manifest line, no new BuildConfig flag**. US-006 is pure UI Compose work plus 2 string resources plus a skill-doc append.
- [ ] **`verification-checklist`'s relevant sections pass**: Build Verification ✓; Lint and Code Quality ✓; Unit Tests ✓ (regression); UI Tests — not applicable (no instrumented test added — those land in SF-0.5-followup); Code Quality Checks ✓; Privacy & Permissions — N/A; Accessibility Review — the contract is codified in the two new files (≥ 96 dp via `Dimens.BigButtonHeight` / `BigRowHeight`, haptic on every click, semantic tokens only, `contentDescription` discipline on the preview slot stand-ins); Dark Mode Testing ✓ (the 4 dark previews per file).

## Design Notes

`brand-design` is the authority (now AUTHORITATIVE per US-005's lock —
zero TODOs remaining). US-006 ships the two production components the
skill sketched at lines 349–404. The sketch is reference; the production
shape is what this brief describes. Departures from the sketch:

1. **`BigYesNoRow` NO button**: brief uses **filled `Button` with `secondary`** (not `FilledTonalButton` with `secondaryContainer` as the sketch shows). Rationale in §Open shape questions, pinned §2.
2. **`BigListRow` icon-size and slot-size**: brief pins **56 dp leading / 48 dp trailing** as `private val`s (not the sketch's `CurroSpacing.xxl + CurroSpacing.m ≈ 64 dp`). Rationale: 56 dp is the senior-first minimum that comfortably renders a 40 dp icon centred with breathing room; 64 dp is generous but consumes a fraction more of the 96 dp row height, leaving less for the title/subtitle column. Tunable in SF-0.5-followup if real-device review wants 64 dp.
3. **`BigListRow` typography**: brief uses **`titleLarge` for title** (sketch uses `headlineMedium`). Rationale: `headlineMedium` is 28 sp SemiBold (per US-005 typography table — used for "card titles, list-row primary text — the most common large-text role"). `titleLarge` is 22 sp SemiBold (per US-005 — "sub-sections, short button labels"). For a list-row title that often runs to a contact's full name, 22 sp avoids excessive line-wrapping on `fontScale = 2.0×` (28 × 2 = 56 sp, which can wrap a 3-word name to 3 lines on a 412 dp width). The sketch's `headlineMedium` is a defensible alternative; the brief recommends `titleLarge` and the developer can deviate with a one-line rationale if real-device review prefers `headlineMedium`. Either choice meets the senior-first floor (≥ 22 sp); both are above the M3 `bodyLarge` defaults `BigPrimaryButton` uses.
4. **`BigListRow` row background**: brief pins **`Color.Transparent`**. Rationale in §Open shape questions, pinned §4.

Other principles, unchanged from US-004's pattern:
- **Read tokens, not literals** — `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*` / `CurroSpacing.*` / `Dimens.*`. The only exception in `presentation/common/` is `Dimens.CardElevation`-style local invariants — here `LeadingSlotSize` / `TrailingSlotSize`.
- **No fussy animation** — Material's standard ripple on both `Button` (in `BigYesNoRow`) and `clickable` (in `BigListRow`) is the only motion. No `Crossfade`, no `AnimatedVisibility`.
- **Predictable shape** — every component reads the same way every time; no per-launch randomisation.
- **Audio + visual together** — N/A for US-006 (no spoken interaction is wired in either component); the contract is preserved.
- **Colour is never the only signal** — SÍ vs NO carries text labels in addition to colours (terracota vs olivo); `BigListRow`'s clickable affordance has the haptic + the Material ripple in addition to layout.

## Senior-UX & Copy

**Two new Spanish strings**: `copy_yes` and `copy_no`. Both are short
shouty labels for the SÍ/NO confirmation buttons. They are appended to
the canonical COPY table (`brand-design` line 491–500 "Confirmation (Phase 6)"
sub-section) and to `strings.xml` with provenance comments. The brand-design
skill's table is the AUTHORITATIVE source; this brief's table here is the
implementation-side spec.

| ID | Spanish | Provenance | Notes |
|---|---|---|---|
| `copy_yes` | SÍ | (NEW) US-006 | Default `BigYesNoRow.yesText`; affirmation, terracota button label |
| `copy_no` | NO | (NEW) US-006 | Default `BigYesNoRow.noText`; rejection, olivo (`secondary`) button label — **never `error`-red** (`brand-design` line 322) |

All-caps form ("SÍ" / "NO") to match the visual weight launcher-ui sketches
the confirmation overlay with (lines 109–111 of the launcher-ui skill:
`✅ SÍ / ❌ NO`); the `Text` `style = titleLarge` renders them at 22 sp
SemiBold which carries the all-caps treatment cleanly.

The accent on "SÍ" is the affirmation form (`sí` as the affirmation
adverb, distinguished from `si` the conditional conjunction); without it
the word loses its meaning. The all-caps glyph rendering preserves the
accent (Spanish capitalisation always keeps accents — "SÍ", "MÁS",
"CÁMARA").

No other copy lands in US-006. The confirmation overlay's prompts
(`copy_confirm_call`, `copy_confirm_call_doublecheck`, etc.) are already
in `strings.xml` per US-005 and are the surface-level copy — `BigYesNoRow`
ships the button labels only.

## Performance Considerations

- **Pure Compose state**; no Hilt graph, no `StateFlow`, no I/O. Both
  components are `@Composable` functions that take parameters and emit
  events. Zero allocations beyond the standard `Modifier` chain and the
  composable's internal `Slot` storage.
- **`@Preview` cost** — 4 previews × 2 files = 8 previews land. Each
  preview is recompose-only in Android Studio; zero runtime cost. The
  build cost is negligible.
- **No animation, no transitions, no `LaunchedEffect`** — the only motion
  is Material's standard ripple on the `Button` / `clickable` surfaces;
  Compose batches recompositions and the haptic call is a one-shot
  effect with no recomposition implication.
- **Recomposition stability** — both components take simple value
  parameters (`String`, `() -> Unit`, `Boolean`, nullable composable
  slots) and a `Modifier`. Compose's autostable detection handles the
  primitives; the slot composables are passed by-reference. No
  recomposition leakage.
- **Memory** — two new top-level composables, two new string resources,
  one new SKILL.md table append. Total static state additions well under
  1 KB.

## Testing Requirements

US-006 ships **no new instrumented tests, no new unit tests beyond the
regression guard**. Aligning with US-004's pattern (US-004 also shipped
no tests; SF-0.5-followup / SF-5.x will land them against real
consumers):

- [ ] **`./gradlew testDebugUnitTest`** still passes — US-001's `SmokeTest`
      is unaffected (regression guard).
- [ ] **`./gradlew assembleDebug`** still passes.
- [ ] **`./gradlew ktlintCheck detekt`** passes on a fresh clone.
- [ ] **`@Preview` rendering check** — the developer opens `BigYesNoRow.kt`
      and `BigListRow.kt` in Android Studio with the preview pane visible,
      confirms each of the 4 + 4 = 8 previews renders without IDE error
      and without visible clipping / contrast failure / collapse. The
      2.0× previews are the senior-first regression. Screenshots in the
      PR description (or, at minimum, the developer states in the PR
      "all 8 previews rendered successfully").
- [ ] **Device dark-mode flip** — `adb shell cmd uimode night yes` and
      `adb shell cmd uimode night no` on a connected device / emulator
      with a temporary scratch composable; both components remain
      readable in both modes (proves the `secondary` / `onSecondary` and
      `onSurface` / `onSurfaceVariant` mappings flip correctly).
- [ ] **Manual haptic check** — the developer adds a temporary
      `BigYesNoRow(...)` to `MainActivity`'s scratch surface (or to a
      throwaway preview-driving Activity), confirms the haptic fires on
      each button press; reverts before commit. Same for `BigListRow`.
- [ ] **`verification-checklist` skill** — the relevant sections
      (Build, Lint, Unit Tests, Accessibility, Dark Mode) pass; Privacy
      & Permissions, On-device Model, Assistant FSM, Function Catalog
      sections are N/A.

**Future test coverage (not in US-006)**:
- SF-0.5-followup (a small UI-test SF) can land the first instrumented
  tests on `BigPrimaryButton`, `BigCard`, `BigYesNoRow`, `BigListRow`:
  - `BigYesNoRow` — `onYes` / `onNo` are called on the right press;
    haptic fires; disabled state blocks both; the two buttons together
    are ≥ 96 dp tall.
  - `BigListRow` — `onClick` is called on press; haptic fires; disabled
    blocks; the row's `Modifier.semantics.contentDescription` carries
    `title` + `subtitle` when no override is supplied; ≥ 96 dp tall.
  - Snapshot / screenshot tests at the four `fontScale` settings (per
    US-004 A9 + `testing-patterns`).
- SF-5.x lands the first real-consumer integration test
  (`ConfirmationOverlay` exercising `BigYesNoRow` end-to-end via the
  `AssistantStateMachine`'s `confirming` state).
- SF-4.7 lands the first real-consumer integration test for `BigListRow`
  via the 3-Marías disambiguation flow.

## Implementation Notes

### Order of operations (developer-facing checklist)

Branch policy: the PM-instruction (from the user, who is asleep —
autonomy) is **work on `main`, do not create a branch**. Commit only;
do not push, do not open a PR.

1. **`strings.xml`** — append the two new entries (`copy_yes`,
   `copy_no`) in the new "Confirmation buttons" sub-block before the
   existing "Execution announcements" block, with the provenance
   comments documented in §Scope.

2. **`BigYesNoRow.kt`** — create the file per the §Scope shape:
   - The composable: `Row(...) { Button(SÍ) ; Button(NO) }`.
   - Both buttons fire haptic `LongPress` before the lambda.
   - SÍ: `MaterialTheme.colorScheme.primary` / `onPrimary`.
   - NO: `MaterialTheme.colorScheme.secondary` / `onSecondary`.
   - 4 private `@Preview`s (light, dark, 1.5×, 2.0×).
   - Imports: `androidx.compose.material3.Button`, `ButtonDefaults`,
     `MaterialTheme`, `Text`; `androidx.compose.foundation.layout.{Row, Arrangement, fillMaxWidth, heightIn, weight}`;
     `androidx.compose.ui.hapticfeedback.HapticFeedbackType`,
     `androidx.compose.ui.platform.LocalHapticFeedback`;
     `androidx.compose.ui.res.stringResource`;
     theme/spacing imports;
     `com.curro.app.R`.

3. **`BigListRow.kt`** — create the file per the §Scope shape:
   - File-private `LeadingSlotSize = 56.dp`, `TrailingSlotSize = 48.dp` with KDoc.
   - The composable: `Row(heightIn(min = Dimens.BigRowHeight).clickable { haptic; onClick() }) { leading? ; titleColumn ; trailing? }`.
   - Haptic `LongPress` before the lambda.
   - 4 private `@Preview`s (light, dark, 1.5×, 2.0×), each rendering
     2 rows in a `Column` so both row-with-everything and
     row-with-minimum get eyeballed.
   - Imports: `androidx.compose.foundation.clickable`, `layout.{Row, Column, Box, Spacer, fillMaxWidth, heightIn, padding, size, width}`;
     `androidx.compose.material3.{MaterialTheme, Text}`;
     `androidx.compose.ui.semantics.{semantics, contentDescription}`;
     `androidx.compose.ui.text.style.TextOverflow`;
     `androidx.compose.ui.hapticfeedback.HapticFeedbackType`,
     `LocalHapticFeedback`;
     theme/spacing imports;
     `androidx.compose.material.icons.Icons` + the icon variants used
     in previews.

4. **`brand-design` skill — append two table rows** at the bottom of
   the "Confirmation (Phase 6)" sub-section (lines 491–500), before
   the existing `copy_confirm_timeout` row:
   ```
   | `copy_yes` | SÍ | (NEW) US-006 — BigYesNoRow default affirmation label |
   | `copy_no` | NO | (NEW) US-006 — BigYesNoRow default rejection label; NO is `secondary`-coloured, never `error`-red |
   ```
   **Do not edit anything else in the skill.** If the developer's `Edit`
   call would touch more than these two lines, abort and re-scope.

5. **Verify invariants — `git diff` should show only**:
   - New file: `app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt`
   - New file: `app/src/main/java/com/curro/app/presentation/common/BigListRow.kt`
   - Modified: `app/src/main/res/values/strings.xml` (2 new lines + comments)
   - Modified: `.claude/skills/brand-design/SKILL.md` (2 new table rows)
   - Modified: `docs/PRD.md` (US-006 entry — committed by the PM step)
   - New file: `docs/briefs/US-006-big-yesno-and-listrow.md` (committed by the PM step)
   - **No other file diffs.** Run:
     ```sh
     git diff app/src/main/java/com/curro/app/MainActivity.kt
     git diff app/src/main/java/com/curro/app/presentation/theme/
     git diff app/src/main/java/com/curro/app/presentation/common/BigPrimaryButton.kt
     git diff app/src/main/java/com/curro/app/presentation/common/BigCard.kt
     ```
     All four must return empty.

6. **Three commands green** (in order):
   1. `./gradlew assembleDebug` — APK builds; the smoke text still renders.
   2. `./gradlew ktlintCheck detekt` — lint green. If detekt fires on
      the 56/48 dp slot sizes in `BigListRow.kt`, add `@Suppress("MagicNumber")`
      with the KDoc rationale documented in §Scope. **Do not widen
      the existing `MagicNumber` exclude.**
   3. `./gradlew testDebugUnitTest` — `SmokeTest` still passes
      (regression guard).

7. **Manual device verification** (Redmi 15 / emulator, optional but
   recommended for the haptic check):
   - Temporarily add a `BigYesNoRow(...)` and a `BigListRow(...)` to
     `MainActivity`'s scratch surface; install on the device.
   - Tap each — confirm haptic fires.
   - `adb shell cmd uimode night yes`; confirm both components remain
     readable; back to `night no`.
   - Revert the scratch `MainActivity` change before commit (the
     byte-identical invariant).

8. **Commit on `main`** (no branch — per PM instruction):
   ```sh
   git add docs/PRD.md \
           docs/briefs/US-006-big-yesno-and-listrow.md \
           app/src/main/java/com/curro/app/presentation/common/BigYesNoRow.kt \
           app/src/main/java/com/curro/app/presentation/common/BigListRow.kt \
           app/src/main/res/values/strings.xml \
           .claude/skills/brand-design/SKILL.md
   git commit -m "$(cat <<'EOF'
   docs(prd): add US-006 — BigYesNoRow + BigListRow (the punted components)

   Co-Authored-By: Claude <noreply@anthropic.com>
   EOF
   )"
   ```
   **The PM commits the PRD + brief now (before development);
   `android-developer` commits the four implementation files in a
   follow-up commit when `/implement-feature US-006` runs.**

   No push. No PR. The user decides those at the end of development.

### Why no architect review was needed

The architect call is "is there a structural decision propagating to
every later composable Curro will ever ship?" Three reasons US-006
fails that test:

1. **Every load-bearing decision was made upstream.** US-004 (A1–A14)
   pinned the 7-step spacing scale, `Dimens` object, `LongPress`
   haptic, the local-vs-Dimens criterion for invariants (A2/A11), the
   contrast floor (A4), `surface == background` (A5), `respect AND
   amplify` fontScale (A6), and the no-Hilt-module / no-DI-binding
   contract (Q7). US-005 then pinned the real brand palette,
   typography numbers, and the canonical COPY table. US-006's
   composables consume those decisions; they don't make new ones.

2. **The remaining shape choices are local to two ~30-line
   composables.** "Reuse `BigPrimaryButton` or peer composition?"
   "FilledTonalButton or filled Button?" "Slot size as `Dimens` or
   `private val`?" "Background `Transparent` or `surface`?" — each is
   a one-component, one-screen-of-thought decision. The PM pinned all
   four in §Open shape questions, pinned. Reversal cost on any of
   them is < 20 min.

3. **No FSM, no LLM, no permission, no integration, no data, no
   migration.** US-006 is pure UI Compose plus a 2-line string
   resource append plus a 2-row skill-table append. The structural
   propagation surface is zero — the components are leaves of the
   composable tree; consumers depend on them, not the other way around.

If a future SF surfaces a concrete problem — e.g. SF-5.x finds the
`secondary`-coloured NO button reads too loud on the real Redmi 15 —
the fix is a two-line colour swap, not an architect escalation.

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-14 | Fran (Claude `android-product-analyst`) | Initial brief — US-006 / SF-0.5 (rest). Predecessors US-004 + US-005 resolved every load-bearing decision; this brief pins the four local shape questions (peer-vs-reuse, NO button colour role, slot-size location, row background) and lands the two punted shared big components. |
