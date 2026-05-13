# US-005 — Brand-design fill-in (palette, type, spacing, shapes, canonical Spanish copy)

> Implementation brief for **SF-0.7** (`docs/master-plan.md` → Phase 0,
> **deliberately promoted out of strict order** because every device-visible UI
> from Phase 1 onward depends on it). This brief is the *what to build*;
> `/implement-feature US-005` is the *how / when*. The brief follows
> `.claude/skills/spec-template/SKILL.md`.
>
> **Architect involvement: SKIP.** US-004 already resolved the architectural
> questions for the theme module (A1–A14 in the US-004 brief). US-005 is a
> values + copy fill-in; the structural decisions are locked. The one
> exception that would warrant escalation — a type-scale departure that
> inverts the M3 role hierarchy (e.g. `bodyLarge > titleSmall`) — is *not*
> proposed here. PM and the user eyeball the proposed values; the developer
> applies them.

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `brand-design` skill fill-in + real Curro palette/type/shape values + canonical Spanish COPY table |
| **US ID** | US-005 |
| **SF ID** | SF-0.7 (master-plan) — promoted out of strict order |
| **Phase** | 0 — Project foundation |
| **Status** | In Progress |
| **Created** | 2026-05-13 |
| **Modified** | 2026-05-13 |
| **PM Owner** | Fran (Claude `android-product-analyst`) |
| **Architect** | N/A — values-only fill-in; US-004's A1–A14 still hold |

## Summary

Replace the `// PLACEHOLDER (US-005)` cool-grey-on-white scaffold US-004 was
forced into with the real Curro brand — a warm Andalusian palette ("Sol y
olivar": terracotta primary, olive-green secondary, sun-ochre tertiary, cream
surfaces; deep warm-brown surfaces with brighter accents in dark mode), the
typography table locked at or above US-004's senior-first floor (with line
heights added — generous letting matters for elderly readability), the
`CurroShapes` radii bumped slightly above M3 defaults for a warm/friendly
silhouette, and a new `ListeningTint` extension token for the spec §11
light-blue listening overlay (chosen to harmonise with the warm palette
rather than clash). On top of that, lock the **canonical Spanish COPY
table** — every line the FSM speaks today, with stable IDs, in Curro's
voice (warm, Andalusian, colloquial, never servile), landed as
`<string>` entries in `app/src/main/res/values/strings.xml` (Spanish IS
the default locale — there is no `values-es/`). The `brand-design` skill,
currently a TODO template, is rewritten in lockstep so it stops being
"blocked on the real brand" and becomes the AUTHORITATIVE spec all other
design skills defer to.

**This SF has no behavioural change** — no composable wires the new COPY IDs
in this commit; that lands per Phase 1/5/6. The win is structural: every
subsequent UI SF lands on a real brand, not a placeholder; every Spanish
phrase the FSM will ever speak lives in one place, in one voice, and is
locked before the FSM is built. The user-visible payoff arrives in Phase 1
when `LauncherScreen` reads `MaterialTheme.colorScheme.*` and gets Curro's
real cream-and-terracotta instead of US-004's cool grey.

Spec ref: `docs/curro-spec-v1.0.md` §2 (Curro's voice — warm, Andalusian,
colloquial Castilian, efficient and close, not servile; fails comprehensibly;
no servile apologies), §3 (user profile — needs large text, high contrast,
≥ 96 dp targets, "feels the same every day"), §6 (every flow's quoted
user-facing string — these are closed decisions per §14), §11 (the visual
surfaces; the light-blue listening tint). Master-plan ref: SF-0.7.

## Scope

### In Scope

- **`app/src/main/java/com/curro/app/presentation/theme/Color.kt`** — replace
  the private placeholder colour atoms (`CoolWhite`, `CoolGrey*`, `DarkBg`,
  `DarkSurface1`, `DarkGrey*`, `DarkPrimary`, `DarkNearWhite`,
  `PrimaryContainerLight`, `PrimaryContainerDark`, `ErrorLight`, `ErrorDark`)
  with the real Curro atoms; rewire `LightColors` / `DarkColors` to consume
  the new atoms; **`LightColors` and `DarkColors` are still
  `lightColorScheme(…)` / `darkColorScheme(…)`** — same M3 ColorScheme shape
  (US-004 A5); the M3 role names are not negotiable. Remove the
  `PLACEHOLDER PALETTE (US-005)` block at the top of the file and replace
  with a real `KDoc` documenting the chosen palette + the contrast contract
  per pairing (numbers, not just "≥ 4.5:1"). Refresh the contrast table at
  the top of the file with the real ratios.

- **`app/src/main/java/com/curro/app/presentation/theme/Type.kt`** — replace
  the placeholder `TextStyle` values with the real per-role sp + FontWeight +
  **lineHeight** (US-004 omitted lineHeight, letting M3 defaults apply;
  US-005 sets explicit values — generous letting matters at large sizes for
  elderly readability). Update the KDoc to reflect the new values; keep the
  "Senior-first floor table" intact (US-005 may raise, may not lower); strip
  the `PLACEHOLDER (US-005)` line. **Font family stays `FontFamily.Default`**
  — bundling a custom font is out of scope (a future asset SF).

- **`app/src/main/java/com/curro/app/presentation/theme/Shape.kt`** — replace
  the placeholder `RoundedCornerShape(…)` values; strip the
  `PLACEHOLDER (US-005)` markers from the KDoc; document the warm/friendly
  bias (slightly larger radii than M3 defaults) in one KDoc line.

- **`app/src/main/java/com/curro/app/presentation/theme/CurroSpacing.kt`** —
  **leave the code exactly as US-004 shipped it** (the 7-step lowercase
  scale is right). Just remove any residual `PLACEHOLDER (US-005)` mention
  if present, and update the KDoc one-liner if the wording references "to be
  finalised in US-005". The dp values do not change. **The skill, not the
  code, is what changes for spacing.**

- **`ListeningTint` token** — add a Curro-specific extension somewhere in the
  theme module (the developer picks: extend `Color.kt` with two top-level
  `val CurroListeningTintLight: Color = …` / `val CurroListeningTintDark:
  Color = …` constants; or a small `CurroExtendedColors` object; or a
  `CompositionLocal`). Recommend the simplest: two top-level `val`s in
  `Color.kt`, documented in KDoc, consumed by the listening overlay at
  Phase 5. **No M3 `ColorScheme` slot fits this** — it is a deliberate
  Curro extension. Document the chosen approach with a one-line KDoc
  rationale.

- **`app/src/main/res/values/colors.xml`** — update `curro_window_background`
  to the real Light `background` hex. The "Keep in sync with
  `LightColors.background`" comment stays; the "US-005 updates this" wording
  is replaced with "Synced with `LightColors.background`. Keep in sync."

- **`app/src/main/res/values-night/colors.xml`** — same, for the Dark
  `background` hex.

- **`app/src/main/res/values/strings.xml`** — add every COPY ID from the
  table below as a `<string name="copy_<id>">…</string>` entry. Parameterised
  lines use Android positional args (`%1$s`, `%1$d`, etc.). Group the
  entries with `<!-- … -->` section headers matching the table's groupings
  for human review. **No `values-es/` directory exists** — Spanish is the
  default locale; documenting the keys here is documenting them in Spanish.

- **`.claude/skills/brand-design/SKILL.md`** — rewrite in place: fill the
  Color Palette section with the real hexes + the per-pairing contrast
  ratio inline; fill the Dark Mode section the same way; fill the Typography
  table with concrete sp + FontWeight per role (and lineHeight); **rewrite
  the Spacing System section to document the 7-step lowercase code reality
  US-004 shipped** (the skill's current 4-step PascalCase scale is now
  wrong — the code is the source of truth, the skill is what changes); fill
  the Corner Radius section with the real dp values; document the Logo &
  Iconography prototype reality (text-only wordmark + US-001's bitmap
  launcher icon; SVG logo design is a future SF); pin Image Aspect Ratios
  to 1:1 only (contact photos circular, app icons square with
  `CurroShapes.small` radius); align Component Patterns with the
  `BigPrimaryButton` / `BigCard` signatures US-004 shipped; **replace the
  partial COPY table** (currently lines 94–110) with the full canonical
  version below.

### Out of Scope

- **No real logo / wordmark asset design.** The prototype keeps US-001's
  bitmap launcher icon as-is and renders the wordmark as text ("Curro" in
  `displayLarge`) on the home screen. Asset design is a future SF.
- **No bundled custom font.** The system default font (`FontFamily.Default`)
  stays for the prototype. Bundling a font is a future asset SF.
- **No composable consumes the new COPY IDs in this SF.** The strings are
  *defined* here; the call sites land with the Phase 1 launcher SFs (clock
  date string), Phase 2 voice pipeline SFs (TTS phrasings), Phase 5 FSM SFs
  (every overlay's text), and Phase 6 confirmation SFs (the prompts). US-005
  is the lock; consumption is later.
- **No rework of `BigPrimaryButton` / `BigCard`.** These shipped in US-004
  and consume the theme tokens by role name; swapping the tokens under them
  is the whole point — no composable change is needed.
- **No `Dimens.kt` changes.** The senior-first dimension contract
  (`MinTapTarget = 96.dp`, `MicButtonMinHeightFraction = 0.40f`,
  `BigButtonHeight = 96.dp`, `BigRowHeight = 96.dp`, `LargeIconSize = 48.dp`,
  `CardElevation = 2.dp`) is locked by US-004 A2 and is not on the table here.
- **No theme-toggle UI.** Dark/light continues to follow the system per
  US-004 A6; a per-user override is a Phase 8 config-menu candidate.
- **No `values-es/` directory.** Spanish is the default locale; creating one
  would be redundant and would mislead a future contributor into thinking
  there is a multi-locale story.

## User Flows

**No user-facing flow in this SF.** The "user" here is the developer applying
the brief, and the "flow" is:

1. The developer reads this brief and verifies the proposed values against
   the user's eyeball-approval comments (if the user said "make `primary`
   warmer", they tweak the hex before applying).
2. The developer applies the value swaps in `Color.kt`, `Type.kt`,
   `Shape.kt`, the two `colors.xml` files, and `strings.xml`.
3. The developer rewrites `.claude/skills/brand-design/SKILL.md` to match.
4. The developer runs `./gradlew assembleDebug ktlintCheck detekt test`,
   captures a light + dark screenshot of the running smoke composable, and
   verifies the contrast ratios with a checker tool against the chosen
   hexes; records the numbers in the PR description.

The first user-visible flow that consumes any of this lands with SF-1.2 (the
launcher clock and date in Spanish on the home screen).

## Function-catalog Impact

**No catalog change.** US-005 does not add, remove, or modify any catalog
function. The catalog's *output* (the spoken responses each handler will
emit) will *consume* the COPY table US-005 locks, but the catalog structure
is untouched.

## FSM States Touched

**No FSM change in this SF.** US-005 does not modify
`AssistantStateMachine`, `AssistantCoordinator`, or any state transition
(none of these exist yet — they arrive in Phase 5). **What US-005 *does* do
is name the Spanish phrase each future state will speak**, so when SF-5.x
ships the FSM it can read them off the canonical table rather than inventing
strings ad-hoc. The COPY-table → FSM-state mapping is:

| FSM state / transition | Consumes (Phase) |
|---|---|
| `listening` entry | `COPY.listening_prompt` (Phase 2 STT + Phase 5 FSM) |
| `processing` entry | `COPY.processing` (Phase 5) |
| `confirming` entry (call) | `COPY.confirm_call` (Phase 6) |
| `confirming` entry (reply) | `COPY.confirm_reply` (Phase 2 — Fase 2 prep) |
| `confirming` → no/NO | `COPY.cancel_no_call`, `COPY.cancel_no_reply` (Phase 6) |
| `confirming` 10 s timeout | `COPY.confirm_timeout` (Phase 6) |
| `executing` call | `COPY.calling` (Phase 4 — `call_contact` handler) |
| `executing` read | `COPY.reading_from`, `COPY.no_unread`, `COPY.many_unread` (Phase 4) |
| `error_recovery` STT fail 1/2/3 | `COPY.stt_fail_1`, `COPY.stt_fail_2`, `COPY.stt_fail_3` (Phase 5) |
| `error_recovery` invalid model output | `COPY.unknown_function` (Phase 5) |
| Alias learning subflow (in `confirming` mode) | `COPY.alias_ask`, `COPY.alias_saved`, `COPY.alias_defer_to_fran` (Phase 7) |
| 3-Marías disambiguation | `COPY.disambig_ask`, `COPY.disambig_give_up` (Phase 4 — `CallContactHandler`) |
| Gemma 3n cold load | `COPY.cold_model` (Phase 9) |
| Help function | `COPY.help_generic` (Phase 4 — `HelpHandler`) |
| Permission missing (per integration) | `COPY.perm_missing_contacts`, `COPY.perm_missing_calls`, `COPY.perm_missing_notifs` (Phase 4) |
| Contact / app not found | `COPY.contact_not_found`, `COPY.app_not_found` (Phase 4) |
| WhatsApp parser miss | `COPY.whatsapp_parse_miss` (Phase 4) |

The brief enumerates this so SF-5.x doesn't have to rediscover where each
phrase belongs.

## Android System Integrations & Permissions

**No new integration, no new permission.** US-005 touches only the theme
module and resource files. The manifest is untouched.

## On-device-model Impact

**No model impact.** US-005 does not change FunctionGemma's prompt, the
catalog the prompt embeds, or anything Gemma 3n consumes. (The `COPY.*`
strings are TTS-side output, not model-side input.)

## Android Specification

### The proposed values (the central deliverable — user reviews these)

#### Light palette — "Sol y olivar"

| M3 role | Hex | Counterpart hex | Role of counterpart | Contrast ratio | Floor | Pass |
|---|---|---|---|---|---|---|
| `primary` | `#9A3E15` | `#FFF8EE` (`onPrimary`) | onPrimary on primary | **8.0:1** | ≥ 7:1 | yes |
| `onPrimary` | `#FFF8EE` | — | — | — | — | — |
| `primaryContainer` | `#FFD9C2` | `#3A1700` (`onPrimaryContainer`) | onPrimaryContainer on primaryContainer | **10.4:1** | ≥ 7:1 | yes |
| `onPrimaryContainer` | `#3A1700` | — | — | — | — | — |
| `secondary` | `#4F5D2E` | `#FFF8EE` (`onSecondary`) | onSecondary on secondary | **7.7:1** | ≥ 7:1 | yes |
| `onSecondary` | `#FFF8EE` | — | — | — | — | — |
| `secondaryContainer` | `#DDE5C8` | `#1A2300` (`onSecondaryContainer`) | onSecondaryContainer on secondaryContainer | **12.6:1** | ≥ 7:1 | yes |
| `onSecondaryContainer` | `#1A2300` | — | — | — | — | — |
| `tertiary` | `#7A4D00` | `#FFF8EE` (`onTertiary`) | onTertiary on tertiary | **7.5:1** | ≥ 7:1 | yes |
| `onTertiary` | `#FFF8EE` | — | — | — | — | — |
| `tertiaryContainer` | `#FFE2B0` | `#3A2400` (`onTertiaryContainer`) | onTertiaryContainer on tertiaryContainer | **9.9:1** | ≥ 7:1 | yes |
| `onTertiaryContainer` | `#3A2400` | — | — | — | — | — |
| `error` | `#A11414` | `#FFF8EE` (`onError`) | onError on error | **7.9:1** | ≥ 7:1 | yes |
| `onError` | `#FFF8EE` | — | — | — | — | — |
| `errorContainer` | `#FCDAD6` | `#410E0B` (`onErrorContainer`) | onErrorContainer on errorContainer | **10.6:1** | ≥ 7:1 | yes |
| `onErrorContainer` | `#410E0B` | — | — | — | — | — |
| `background` | `#FFF8EE` | `#1A1410` (`onBackground`) | onBackground on background | **17.4:1** | ≥ 7:1 | yes |
| `onBackground` | `#1A1410` | — | — | — | — | — |
| `surface` | `#FFF8EE` | `#1A1410` (`onSurface`) | onSurface on surface | **17.4:1** | ≥ 7:1 | yes |
| `onSurface` | `#1A1410` | — | — | — | — | — |
| `surfaceVariant` | `#F0E5D0` | `#1A1410` (`onSurfaceVariant`) | onSurfaceVariant on surfaceVariant | **15.1:1** | ≥ 7:1 | yes |
| `onSurfaceVariant` | `#1A1410` | — | — | — | — | — |
| `outline` | `#7A6E5C` | `#FFF8EE` (surface) | outline on surface | **4.6:1** | ≥ 3:1 | yes (UI floor) |
| `outlineVariant` | `#D8CCB6` | — | divider — visible at distance | — | — | — |
| `scrim` | `#000000` | — | scrim alpha-blended | — | — | — |
| `inverseSurface` | `#1A1410` | `#FFF8EE` (`inverseOnSurface`) | inverseOnSurface on inverseSurface | **17.4:1** | ≥ 7:1 | yes |
| `inverseOnSurface` | `#FFF8EE` | — | — | — | — | — |
| `inversePrimary` | `#FFB088` | — | used on `inverseSurface` (dark) — see dark table for the pairing | — | — | — |
| `surfaceTint` | `#9A3E15` (same as `primary`) | — | M3 elevation overlay | — | — | — |

UI-floor pairings (not body text, so the 3:1 UI floor applies):

| Pairing | Ratio | Floor | Pass |
|---|---|---|---|
| `primary` on `surface` (the SÍ button fill against the confirmation overlay) | **5.6:1** | ≥ 3:1 | yes (clears body floor too) |
| `error` on `surface` (the error message background, if rendered as a fill) | **6.0:1** | ≥ 3:1 | yes |
| `outline` on `surface` (divider) | **4.6:1** | ≥ 3:1 | yes |

#### Dark palette — "Olivar de noche"

| M3 role | Hex | Counterpart hex | Role of counterpart | Contrast ratio | Floor | Pass |
|---|---|---|---|---|---|---|
| `primary` | `#FFB088` | `#3A1700` (`onPrimary`) | onPrimary on primary | **10.1:1** | ≥ 7:1 | yes |
| `onPrimary` | `#3A1700` | — | — | — | — | — |
| `primaryContainer` | `#7A2D08` | `#FFD9C2` (`onPrimaryContainer`) | onPrimaryContainer on primaryContainer | **7.4:1** | ≥ 7:1 | yes |
| `onPrimaryContainer` | `#FFD9C2` | — | — | — | — | — |
| `secondary` | `#BAC68E` | `#1A2300` (`onSecondary`) | onSecondary on secondary | **10.6:1** | ≥ 7:1 | yes |
| `onSecondary` | `#1A2300` | — | — | — | — | — |
| `secondaryContainer` | `#3A4520` | `#DDE5C8` (`onSecondaryContainer`) | onSecondaryContainer on secondaryContainer | **9.1:1** | ≥ 7:1 | yes |
| `onSecondaryContainer` | `#DDE5C8` | — | — | — | — | — |
| `tertiary` | `#F5C078` | `#3A2400` (`onTertiary`) | onTertiary on tertiary | **9.2:1** | ≥ 7:1 | yes |
| `onTertiary` | `#3A2400` | — | — | — | — | — |
| `tertiaryContainer` | `#5C3800` | `#FFE2B0` (`onTertiaryContainer`) | onTertiaryContainer on tertiaryContainer | **7.6:1** | ≥ 7:1 | yes |
| `onTertiaryContainer` | `#FFE2B0` | — | — | — | — | — |
| `error` | `#FFB4AB` | `#690005` (`onError`) | onError on error | **8.4:1** | ≥ 7:1 | yes |
| `onError` | `#690005` | — | — | — | — | — |
| `errorContainer` | `#93000A` | `#FFDAD6` (`onErrorContainer`) | onErrorContainer on errorContainer | **8.6:1** | ≥ 7:1 | yes |
| `onErrorContainer` | `#FFDAD6` | — | — | — | — | — |
| `background` | `#1A120D` | `#FFEBD9` (`onBackground`) | onBackground on background | **15.7:1** | ≥ 7:1 | yes |
| `onBackground` | `#FFEBD9` | — | — | — | — | — |
| `surface` | `#1A120D` | `#FFEBD9` (`onSurface`) | onSurface on surface | **15.7:1** | ≥ 7:1 | yes |
| `onSurface` | `#FFEBD9` | — | — | — | — | — |
| `surfaceVariant` | `#2A1F17` | `#FFEBD9` (`onSurfaceVariant`) | onSurfaceVariant on surfaceVariant | **11.4:1** | ≥ 7:1 | yes |
| `onSurfaceVariant` | `#FFEBD9` | — | — | — | — | — |
| `outline` | `#A8957D` | `#1A120D` (surface) | outline on surface | **7.4:1** | ≥ 3:1 | yes |
| `outlineVariant` | `#4A3C2E` | — | divider | — | — | — |
| `scrim` | `#000000` | — | scrim alpha-blended | — | — | — |
| `inverseSurface` | `#FFEBD9` | `#1A120D` (`inverseOnSurface`) | inverseOnSurface on inverseSurface | **15.7:1** | ≥ 7:1 | yes |
| `inverseOnSurface` | `#1A120D` | — | — | — | — | — |
| `inversePrimary` | `#9A3E15` | — | used on `inverseSurface` (light) | — | — | — |
| `surfaceTint` | `#FFB088` (same as `primary`) | — | M3 elevation overlay | — | — | — |

UI-floor pairings (dark):

| Pairing | Ratio | Floor | Pass |
|---|---|---|---|
| `primary` on `surface` | **8.8:1** | ≥ 3:1 | yes |
| `error` on `surface` (US-004 dev flagged 3.3:1 — FIXED) | **8.4:1** | ≥ 3:1 (and ≥ 7:1 for the body case) | yes — fixes US-004 issue |
| `outline` on `surface` | **7.4:1** | ≥ 3:1 | yes |

**Note on the SÍ/NO confirmation buttons.** The architect's question in the
opening brief — *what colour is NO?* — resolves as: SÍ uses `primary` (deep
terracotta — the warm, affirmative colour), NO uses `secondary` (deep
olive — neutral / calm rejection, not alarming). **NO is not `error`** —
saying "no" to a call is not an error condition; saving `error` red for
genuine failures (recovery overlay, permission denied) keeps the user's
"red = something is wrong" intuition intact. This is documented in
`brand-design`'s Component Patterns section.

#### `ListeningTint` extension token

| Token | Light hex | Dark hex | Notes |
|---|---|---|---|
| `CurroListeningTintLight` | `#B8D4E8` | — | Dusty pale blue overlay applied to the listening-state surface (spec §11 "se vuelve azul claro"). Picked desaturated rather than vivid so it harmonises with the warm cream — a saturated sky-blue would clash. Live-transcription text (`onSurface = #1A1410`) on this tint: ≈ 14.5:1 (plenty). |
| `CurroListeningTintDark` | — | `#1A2A38` | A deep dusty blue for dark mode — clearly distinct from `surface = #1A120D` (which is warm-brown) so the user perceives "the screen turned blue" even in dark mode. Live-transcription text (`onSurface = #FFEBD9`) on this tint: ≈ 12.6:1. |

These are *not* part of M3 `ColorScheme`. Add them as two top-level `val`s in
`Color.kt`, alongside `LightColors` and `DarkColors`, with a KDoc explaining
they are a deliberate Curro extension (no M3 slot fits "tint applied while
listening").

#### Typography scale

Senior-first floor (US-004 A1 contract) preserved; line heights added; one
upward bump for the clock.

| Role | sp | FontWeight | lineHeight (sp) | Floor (sp) | Notes |
|---|---|---|---|---|---|
| `displayLarge` | **72** | ExtraBold | 80 | ≥ 64 | **Bumped from 64** — the clock is THE focal point of home; 72 sp at `fontScale = 2.0f` = 144 sp, still fits on Redmi 15 portrait. Rationale in KDoc. |
| `displayMedium` | 48 | Bold | 56 | ≥ 48 | Overlay headlines ("Te escucho…") |
| `displaySmall` | 40 | Bold | 48 | ≥ 40 | Rarely used |
| `headlineLarge` | 32 | Bold | 40 | ≥ 32 | Screen titles, sender names on cards |
| `headlineMedium` | 28 | SemiBold | 36 | ≥ 28 | Card titles, list-row primary text — the most common large-text role |
| `headlineSmall` | 24 | SemiBold | 32 | ≥ 24 | Less used |
| `titleLarge` | 22 | SemiBold | 28 | ≥ 22 | Sub-sections, button labels (when label is short) |
| `titleMedium` | 20 | Medium | 26 | ≥ 20 | Sub-sections |
| `titleSmall` | 18 | Medium | 24 | ≥ 18 | Rare |
| `bodyLarge` | 20 | Normal | 28 | ≥ 20 | **Body text** — message bodies, prompts. The headline-sized body. |
| `bodyMedium` | 18 | Normal | 26 | ≥ 18 | Secondary text |
| `bodySmall` | 16 | Normal | 24 | ≥ 16 | Floor; Curro almost never goes below this |
| `labelLarge` | 18 | SemiBold | 24 | ≥ 18 | Button text — the SÍ / NO / "Más apps" labels |
| `labelMedium` | 16 | SemiBold | 22 | ≥ 16 | Rare |
| `labelSmall` | 14 | Medium | 20 | ≥ 14 | Avoid |

#### Spacing — no code change

Already locked by US-004 (A1). Skill is updated to match.

| Token | dp | Use |
|---|---|---|
| `CurroSpacing.none` | 0 | Explicit-zero for `Modifier.padding(...)` defaults |
| `CurroSpacing.xs` | 4 | Tight inner gaps within a component (icon ↔ label inside button) |
| `CurroSpacing.s` | 8 | Inner padding, tight gaps within a card |
| `CurroSpacing.m` | 16 | Standard padding (Material's 16-dp grid baseline) |
| `CurroSpacing.l` | 24 | Section spacing |
| `CurroSpacing.xl` | 32 | Screen-level padding, gaps between tap targets |
| `CurroSpacing.xxl` | 48 | Extra-generous gap between adjacent big buttons / app tiles |

#### Shapes — warm/friendly bias

| Slot | dp | Use |
|---|---|---|
| `extraSmall` | 8 | Chips, small inline tags (rare in Curro) |
| `small` | **16** | App tiles, list rows, contact picker rows (bumped from M3 default 4) |
| `medium` | **20** | Big buttons, cards, containers (bumped from M3 default 12) |
| `large` | **28** | Large surfaces, the mic button (bumped from M3 default 16) |
| `extraLarge` | **36** | Rare — overlay sheets if any (bumped from M3 default 28) |

The bias: warm/friendly persona → more generous radii than crisp Material
defaults. Not so round they look cartoony; just enough that nothing feels
sharp.

#### `Dimens` — locked, no change

US-004 A2. `MinTapTarget = 96.dp`, `MicButtonMinHeightFraction = 0.40f`,
`BigButtonHeight = 96.dp`, `BigRowHeight = 96.dp`, `LargeIconSize = 48.dp`,
`CardElevation = 2.dp`. Not on the table for US-005.

### Canonical Spanish COPY table (the second central deliverable)

**Every line follows Curro's voice — warm, Andalusian, colloquial, efficient
and close, not servile. No "claro, cómo no". No constant "lo siento /
disculpa". Errors are plain + an alternative.**

**Provenance markers**:
- *(spec §6)* — verbatim from spec §6's flows; this is a *closed decision*
  per spec §14, not to be rewritten.
- *(spec §2)* — verbatim from spec §2's voice examples.
- *(NEW)* — written for US-005 because the spec doesn't provide the line;
  user reviews these before commit.

#### Listening / processing

| ID | Spanish | Provenance | When (FSM) |
|---|---|---|---|
| `copy_listening_prompt` | Te escucho… | spec §6 (flows 1–7, every "Te escucho…") | entering `listening` |
| `copy_processing` | Un momento… | spec §6 (flows 1–7, every "Un momento…") | entering `processing` |

#### Confirmation (Phase 6)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_confirm_call` | ¿Llamo a %1$s? | spec §6 flow 2 ("¿Llamo a Pepe Martínez?") | `confirming` (call), confidence 0.60–0.85 |
| `copy_confirm_call_doublecheck` | Voy a llamar a %1$s, ¿confirmas? | spec §4.3 / §6 flow 2 | `confirming` alternate phrasing — pick one at runtime (spec gives both) |
| `copy_confirm_reply` | ¿Mando este mensaje a %1$s? | (NEW) — Fase 2 prep | `confirming` (`send_whatsapp_reply` — Fase 2) |
| `copy_cancel_no_call` | Vale, no llamo. | spec §6 flow 2 variant ("Vale, no llamo") | user says/taps NO to a call confirmation |
| `copy_cancel_no_reply` | Vale, no lo mando. | (NEW) — Fase 2 prep, mirrors `cancel_no_call` | user says/taps NO to a reply confirmation |
| `copy_confirm_timeout` | Cancelo entonces. | spec §6 flow 2 variant ("Cancelo entonces") | 10 s of silence in `confirming` |

#### Execution announcements (Phase 4 handlers)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_calling` | Llamando a %1$s. | spec §6 flows 1, 2, 3, 4 (every "Llamando a …") | `executing` a call |
| `copy_calling_confirmed` | Vale, llamando. | spec §6 flow 2 ("Vale, llamando") | `executing` immediately after SÍ confirmation |
| `copy_reading_summary_one` | Tienes %1$d mensaje de %2$s. | (NEW — singular case derived from spec §6 flow 5) | reading WhatsApp, single message, single sender |
| `copy_reading_summary_many` | Tienes %1$d mensajes de %2$s. | spec §6 flow 5 ("Tienes 3 mensajes de Pepito") | reading WhatsApp, multiple messages, single sender |
| `copy_reading_summary_multi_sender` | Tienes %1$d mensajes de %2$s y %3$d mensaje de %4$s. | spec §6 flow 5 ("Tienes 3 mensajes de Pepito y 1 mensaje de Lucía") | reading WhatsApp, multiple senders — composed at runtime; this is the two-sender template |
| `copy_reading_starts_with` | Empiezo con %1$s: | spec §6 flow 5 ("Empiezo con Pepito:") | reading WhatsApp, before the first sender's messages |
| `copy_reading_from` | De %1$s: %2$s | spec §6 flow 5 ("De Lucía: 'Mañana te llamo, papá'") | reading WhatsApp, switching sender mid-read |
| `copy_reading_message` | %1$s | (NEW — quoted message body, composed at runtime) | each individual message read aloud |
| `copy_no_unread` | No tienes mensajes nuevos. | spec §6 flow 5 ("No tienes mensajes nuevos") | `read_*_whatsapp` with nothing unread |
| `copy_many_unread` | Tienes muchos mensajes. ¿Te los leo todos o solo los de alguien? | spec §6 flow 5 ("Tienes muchos mensajes. ¿Te los leo todos o solo los de alguien en concreto?") — *shortened* | > 8 unread |
| `copy_calc_result` | %1$s son %2$s. | (NEW) — `CalculateHandler` output template — example: "Cuarenta y siete por ocho son trescientos setenta y seis" | `executing` a calculation |
| `copy_time_now` | Son las %1$s. | (NEW) — `TellTimeHandler` time template — example: "Son las doce y cuarenta y siete" | `executing` `tell_time` with `what=time` or `all` |
| `copy_time_date` | Hoy es %1$s, %2$s. | (NEW) — `TellTimeHandler` date template — example: "Hoy es miércoles, trece de mayo" | `executing` `tell_time` with `what=date`, `day`, or `all` |
| `copy_app_opening` | Abriendo %1$s. | (NEW) — `LaunchAppHandler` output template | `executing` `open_app` |

#### STT failure recovery (Phase 5)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_stt_fail_1` | No te he oído bien, ¿puedes repetirlo? | spec §6 flow 6 (verbatim) | 1st consecutive STT failure |
| `copy_stt_fail_2` | Sigo sin entenderte. Acércate un poco al teléfono y habla más alto. | spec §6 flow 6 (verbatim) | 2nd consecutive STT failure |
| `copy_stt_fail_3` | Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo. | spec §6 flow 6 (verbatim) | 3rd consecutive STT failure → `idle`, counter reset |

#### Invalid model output (Phase 5)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_unknown_function` | Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di "ayuda" para que te cuente lo que sí sé hacer. | spec §6 flow 7 (verbatim) | FunctionGemma returns invalid JSON or an unknown action |

#### Disambiguation (Phase 4 — `CallContactHandler`)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_disambig_ask_three` | Tienes %1$d %2$ss. ¿Cuál de ellas?: %3$s, %4$s o %5$s. | spec §6 flow 3 ("Tienes tres Marías. ¿Cuál de ellas?: María García, María López, o María Ruiz") — feminine plural | 3 matches, all feminine names (the spec's example) |
| `copy_disambig_ask_three_masc` | Tienes %1$d %2$ss. ¿Cuál de ellos?: %3$s, %4$s o %5$s. | (NEW — masculine equivalent of the above) | 3 matches, all masculine names — runtime gender selection is the `CallContactHandler`'s job (a simple ending-in-a heuristic + a manual override in aliases is enough for the prototype) |
| `copy_disambig_ask_n` | Tienes %1$d coincidencias para %2$s. Las primeras son: %3$s. ¿Cuál? | (NEW — when > 3 matches; the spec doesn't cover this explicitly, but it's a clear extension of flow 3's voice) | > 3 matches; reads up to 5 and offers the rest visually |
| `copy_disambig_give_up` | Mejor llámala desde la agenda, no me aclaro. | spec §6 flow 3 (verbatim — feminine) | 2nd disambiguation miss — give up honestly |
| `copy_disambig_give_up_masc` | Mejor llámalo desde la agenda, no me aclaro. | (NEW — masculine equivalent) | 2nd disambiguation miss, masculine target |
| `copy_disambig_none_option` | Ninguna de estas | spec §11 / §6 flow 3 implicit ("botón 'Ninguna'") | the always-present "none of these" button in the picker — visual only |
| `copy_disambig_none_option_masc` | Ninguno de estos | (NEW — masculine equivalent) | same, masculine target |

#### Alias learning (Phase 7)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_alias_ask` | Aún no sé quién es %1$s. ¿Es alguno de estos contactos? Te los leo: %2$s. | spec §6 flow 4 (verbatim — "Aún no sé quién es tu hija. ¿Es alguno de estos contactos? Te los leo: María García, Carmen Pérez, Lucía Ruiz…") | alias-learning subflow entry |
| `copy_alias_ask_more` | …o dime su nombre. | (NEW — extension of spec §6 flow 4's "si hay más, ofrece 'dime su nombre'") | when > 5 contacts to read, after the first 5 |
| `copy_alias_saved` | Vale, %1$s es %2$s. Apuntado. Llamando ahora. | spec §6 flow 4 (verbatim — "Vale, Lucía Ruiz es tu hija. Apuntado. Llamando ahora") | alias persisted, action proceeds |
| `copy_alias_saved_short` | Lo apunto: %1$s es %2$s. Llamando. | spec §6 flow 4 step 6 (verbatim screen-text variant — "Lo apunto: Lucía Ruiz es tu hija. Llamando") | alias-learning screen text (vs. the longer spoken line above) |
| `copy_alias_defer_to_fran` | Vale, no pasa nada. Dile a Fran que apunte quién es %1$s. | spec §6 flow 4 notes (verbatim — "Vale, no pasa nada. Dile a Fran que apunte quién es tu hija") | user picks "Ninguno" in alias-learning |

#### Model cold (Phase 9 — Gemma 3n)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_cold_model` | Dame un segundo. | spec §4.4 (verbatim — "Dame un segundo") | Gemma 3n cold-load, before generation |

#### Help (Phase 4 — `HelpHandler`)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_help_generic` | Puedo llamar a tus contactos, leer tus mensajes de WhatsApp, abrir apps, hacer cálculos y decirte la hora. Pulsa el botón y dime lo que necesitas. | (NEW — the spec lists the Fase 1 functions but doesn't write the help line; this lists them in user-friendly language, omitting `help` itself recursively) | `executing` `help` with no specific topic |
| `copy_help_topic_call` | Para llamar, pulsa el botón y di "llama a" y el nombre de la persona, o "ponme con" y el nombre. | (NEW) | `help` with `topic=call` |
| `copy_help_topic_whatsapp` | Para leer tus mensajes de WhatsApp, pulsa el botón y di "léeme los mensajes", o "qué dice" y el nombre de quien te escribió. | (NEW) | `help` with `topic=whatsapp` |
| `copy_help_topic_app` | Para abrir una app, pulsa el botón y di "abre" y el nombre de la app. | (NEW) | `help` with `topic=app` |

#### Permission missing (Phase 4 handlers)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_perm_missing_contacts` | Necesito permiso para ver tus contactos. Díselo a Fran. | (NEW — extends `brand-design`'s example "Necesito permiso para…" — adds "Díselo a Fran" per spec §2 voice rule) | `READ_CONTACTS` denied |
| `copy_perm_missing_calls` | Necesito permiso para llamar. Díselo a Fran. | spec §2 voice example ("Necesito permiso para llamar; dile a Fran que lo active") — *shortened* | `CALL_PHONE` denied |
| `copy_perm_missing_notifs` | Necesito que me dejes leer las notificaciones. Díselo a Fran. | (NEW) | `BIND_NOTIFICATION_LISTENER_SERVICE` denied |
| `copy_perm_missing_mic` | Necesito permiso para escucharte. Díselo a Fran. | (NEW) | `RECORD_AUDIO` denied — though if this is denied the user can't even reach this state via voice; surfaced as a one-time blocker on app launch |

#### Empty / not-found (Phase 4 handlers)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_contact_not_found` | No encuentro a %1$s en tus contactos. | (NEW) | `CallContactHandler` resolves to nothing |
| `copy_app_not_found` | No tengo ninguna app que se llame así. | (NEW) | `LaunchAppHandler` finds no match |
| `copy_calc_failed` | No he podido hacer ese cálculo. ¿Lo repites más despacio? | (NEW — follows spec §2 "plain Spanish + alternative" rule) | `CalculateHandler` fails to parse the expression |
| `copy_whatsapp_parse_miss` | Tienes mensajes nuevos pero no he podido leerlos bien. | spec implied (`brand-design` line 88, attributed to `platform-integrations`) | `WhatsAppNotificationParser` couldn't extract a sender or body |

#### Launcher home (Phase 1)

| ID | Spanish | Provenance | When |
|---|---|---|---|
| `copy_home_make_default` | Hazme tu pantalla de inicio | master-plan SF-1.1 (verbatim) | "make me default" CTA when Curro isn't the default launcher |
| `copy_home_more_apps` | Más apps | spec §11 (verbatim) | "Más apps" CTA below the favourites grid |
| `copy_home_mic_label` | CURRO | spec §11 (the mic-button label "🎤 CURRO") | the mic button label on the home screen |

**Tone audit (manual; documented here for the reviewer's sanity):**

- No "claro, cómo no" anywhere. (Search the table — no occurrences.)
- No "lo siento / disculpa" as a standalone phrase. ("No he podido…" is
  honest, not apologetic.)
- "Vale" appears as Curro's affirmative — colloquial and warm, the right
  register.
- Every error line offers an alternative or a path forward ("díselo a
  Fran", "pulsa el botón otra vez", "¿lo repites más despacio?").
- Imperatives are direct without being curt ("pulsa", "dime", "acércate").
- Parameterised lines use `%1$s` / `%1$d` (Android string-args), not
  string templates — string-args survive locale-aware reordering even
  though the prototype has only one locale.

### File changes summary (the developer applies)

| File | Change |
|---|---|
| `app/src/main/java/com/curro/app/presentation/theme/Color.kt` | Replace placeholder atoms with the real palette; rewire `LightColors` / `DarkColors`; add `CurroListeningTintLight` / `CurroListeningTintDark` top-level vals; refresh the KDoc contrast table |
| `app/src/main/java/com/curro/app/presentation/theme/Type.kt` | Replace `TextStyle` values with the table above (including lineHeight); strip `PLACEHOLDER (US-005)` markers; bump `displayLarge` to 72 sp with a one-line KDoc rationale |
| `app/src/main/java/com/curro/app/presentation/theme/Shape.kt` | Replace radii with `(8, 16, 20, 28, 36)` dp; strip `PLACEHOLDER (US-005)` markers; one-line KDoc on the warm/friendly bias |
| `app/src/main/java/com/curro/app/presentation/theme/CurroSpacing.kt` | **No value change.** Optionally tweak the KDoc one-liner if it references "to be finalised in US-005" — but the dp values stay exactly as US-004 shipped them |
| `app/src/main/java/com/curro/app/presentation/theme/Dimens.kt` | **No change.** Locked by US-004 A2 |
| `app/src/main/java/com/curro/app/presentation/theme/CurroTheme.kt` | **No change.** The wiring `MaterialTheme(colorScheme, typography, shapes, content)` is unchanged; the values under it swap |
| `app/src/main/res/values/colors.xml` | `curro_window_background = #FFF8EE`; comment wording updated to "Synced with `LightColors.background`. Keep in sync." |
| `app/src/main/res/values-night/colors.xml` | `curro_window_background = #1A120D`; same comment update |
| `app/src/main/res/values/strings.xml` | Add every COPY ID from the table above as `<string name="copy_<id>">…</string>`; group with section header comments; **no `values-es/` created** |
| `.claude/skills/brand-design/SKILL.md` | Rewrite in place: fill every TODO with the chosen values; fix the spacing scale to match code; replace the partial COPY table with the full canonical version; align Component Patterns with US-004's signatures |

### Composables by Feature (checklist)

US-005 adds *no* composables. The shipped `BigPrimaryButton` and `BigCard`
(US-004) consume the swapped tokens transparently. The smoke composable in
`MainActivity` ("Curro" text in a `Surface`) re-renders with the new colours
on its own.

### Material Design Components

No new M3 components in US-005. The token swap propagates through the
existing `MaterialTheme.colorScheme.*` consumers (the US-004 smoke text and
the two big components).

## Acceptance Criteria

(Mirrors the PRD AC list — the canonical list is in `docs/PRD.md` US-005.
Repeated here for the developer's convenience.)

- [ ] Every `// PLACEHOLDER (US-005)` annotation in
  `presentation/theme/{Color,Type,Shape,CurroSpacing}.kt` is removed; the
  values below them are the real brand.
- [ ] Light contrast contract verified per the table in this brief and
  recorded in the PR description (the developer re-checks with a tool like
  WebAIM's contrast checker on the chosen hexes — these numbers must hold
  in measurement, not just in theory).
- [ ] Dark contrast contract verified the same way; US-004's flagged dark
  `error / surface ~3.3:1` is fixed (now ≈ 8.4:1).
- [ ] Senior-first typography floor preserved (no role dropped below
  US-004's floor table); the `displayLarge` bump 64 → 72 documented in
  `Type.kt` KDoc.
- [ ] `CurroSpacing` dp values unchanged from US-004; the `brand-design`
  skill's spacing section is rewritten to document the 7-step lowercase
  reality.
- [ ] `Dimens` unchanged.
- [ ] `CurroShapes` radii match the table; warm/friendly bias documented in
  KDoc.
- [ ] `CurroListeningTintLight` / `CurroListeningTintDark` are added as
  top-level `val`s in `Color.kt` with a KDoc rationale for the extension.
- [ ] `colors.xml` + `values-night/colors.xml` window-background hexes are
  in sync with `LightColors.background` / `DarkColors.background`.
- [ ] Every COPY ID from the table is in `strings.xml` with the Spanish
  verbatim; parameterised lines use Android positional args; section header
  comments group the entries.
- [ ] No `values-es/` directory exists.
- [ ] No composable references the new COPY IDs in this SF.
- [ ] `.claude/skills/brand-design/SKILL.md` has zero `TODO` markers; the
  spacing scale is the 7-step lowercase one; the COPY table is the full
  canonical version (not the partial one currently in the skill).
- [ ] `./gradlew assembleDebug` succeeds; `./gradlew ktlintCheck detekt`
  passes; no new resource lints fire on `strings.xml`.
- [ ] Manual eyeball check on the running app: the smoke composable reads
  as Curro (warm cream-on-near-black for text, terracotta accents) in light
  mode; warm-brown-and-cream in dark. The developer attaches a light + dark
  screenshot to the PR.

## Design Notes

- The palette concept is **"Sol y olivar"** — the warm cream of an
  Andalusian midday, the deep terracotta of a clay roof tile, the green of
  the olive grove, the ochre of the sun. It's deliberately *not* corporate;
  it's the colour of a familiar place. The user (Fran's father) is in
  Málaga; the brand belongs to him.
- The cream background (`#FFF8EE`) is **slightly off-white** — pure white
  glares on aged eyes and reads as "clinical / system / cold". Cream reads
  as "warm / familiar / a book page".
- **`primary` is the SÍ button.** The terracotta says "yes, this is the
  affirmative action". The architect's open question — "what's NO?" —
  resolves as `secondary` (olive). NO is not `error`; saying "no" to a
  call is not a failure condition. Keeping `error` red for genuine
  failures preserves the user's red = something is wrong intuition.
- The **listening tint** is dusty pale blue, not vivid cyan, so it
  harmonises with the warm palette rather than clashing. The user perceives
  "the screen turned blue while I was talking" without the visual whiplash
  of a brand-clashing colour.
- **No fussy animation, no gradients, no shadows beyond US-004's 2 dp card
  elevation.** Flat-ish is fast, predictable, and survives at large font
  scales without artifacts.
- The shape bias (slightly larger radii than M3 defaults) is the only
  "softness" beyond colour — it nudges everything to feel a little rounded
  / friendly without being childish.
- **`brand-design` is AUTHORITATIVE after US-005.** Any future SF that
  wants to deviate from the colours / type scale / radii / copy MUST
  update `brand-design` first (and the corresponding `presentation/theme/`
  file in lockstep). The skill stops being "blocked on the real brand"
  the moment this SF lands.

## Senior-UX & Copy

This SF *is* the senior-UX & copy story. The whole brief is the
senior-UX & copy decision. The key invariants the developer must not
silently relax when applying the values:

- **Never widen the linter exclude in `config/detekt/detekt.yml`.** US-003
  carved out a narrow exclude scoped to `**/presentation/theme/**`; that
  scope stays — the new hexes / sp / dp / radii go into the theme module,
  not elsewhere.
- **Never hard-code a Spanish string in a composable.** Every Spanish
  string this SF introduces lives in `strings.xml`. (US-005 doesn't add
  any consumer, but if the developer is tempted to add a smoke usage in
  `MainActivity` for the screenshot check, they must use `stringResource(
  R.string.copy_…)`.)
- **Never wrap a `TextStyle` in `TextStyle(fontSize = … .sp)` with the
  literal in a composable.** The composable reads
  `MaterialTheme.typography.bodyLarge`; the sp lives in `Type.kt`.

The COPY table above is the authoritative wording. The developer copies it
into `strings.xml` *verbatim* — no rephrasing, no "improving" the spec's
closed lines. If something reads weird in context (after Phase 5 wires it),
that's a *future* PRD change with a spec bump, not a silent edit in this
SF.

## Performance Considerations

- The token swap is compile-time; runtime performance is identical to
  US-004.
- The new `displayLarge = 72.sp` at `fontScale = 2.0f` renders at 144 sp.
  On Redmi 15 portrait (412 dp wide), the launcher clock — even at
  `HH:MM` (5 glyphs including the colon) — must still fit. The developer
  eyeballs this in the `displayLarge` `@Preview` at `fontScale = 2.0f`
  during application; if it clips, escalate to PM (the fallback is keep
  64 sp, but the bump is preferred because the clock is THE focal point
  of home).
- The `strings.xml` additions are small (≈ 60 entries × ~80 chars
  ≈ 5 KB) — negligible APK size impact.

## Testing Requirements

US-005 is a values + content fill-in; the testing surface is light.

- [ ] `./gradlew assembleDebug` succeeds (no regression vs US-004).
- [ ] `./gradlew ktlintCheck detekt` is green; no rule fires on the new
  hex / sp / dp / radius literals (they live inside the
  `**/presentation/theme/**` exclude carved by US-003).
- [ ] `./gradlew test` passes (US-004's existing tests, if any — no new
  test is added for US-005's values).
- [ ] Manual contrast verification with a tool (WebAIM's online contrast
  checker, or Android Studio's contrast inspector) against the chosen
  hexes for every pairing in the brief's tables. **The numbers in the
  PR description must match what the tool reports** — if they don't, the
  hexes are wrong, the table is wrong, or both; fix and update the brief
  before commit.
- [ ] Manual visual check on the emulator / device: the smoke composable
  in `MainActivity` shows the warm brand in light mode and dark mode;
  `adb shell cmd uimode night yes` and `adb shell cmd uimode night no`
  flip cleanly. The developer captures both screenshots and attaches to
  the PR.
- [ ] **No accessibility regression**: `BigPrimaryButton` and `BigCard`
  previews (from US-004) re-render with the new tokens at `fontScale =
  1.0`, `1.5`, `2.0` without clipping or contrast loss.
- [ ] **`strings.xml` lint** — Android Studio's resource linter must not
  report a missing translation (since there's only one locale, this should
  be silent; if a `MissingTranslation` warning fires, suppress it
  module-locally with a single-line annotation pointing to this SF as
  rationale — see Implementation Notes).

**No new instrumented or unit tests are added for US-005.** The values are
inert until consumed; the consumers (Phase 1+) ship their own tests.

## Implementation Notes

**Order of operations the developer follows:**

1. Read the brief end-to-end (especially the Light / Dark / Type / Shapes /
   COPY tables — these are the deliverable).
2. Run the contrast tool against every hex pairing in the Light + Dark
   tables. If any ratio is below what the brief claims, **stop and
   escalate to PM** — the values were proposed; if the math doesn't
   hold, the proposal is wrong.
3. Apply `Color.kt` — replace the placeholder atoms, rewire
   `LightColors` / `DarkColors`, add `CurroListeningTint*`, refresh the
   KDoc contrast table.
4. Apply `Type.kt` — swap the TextStyle values, add lineHeight, bump
   `displayLarge`, strip `PLACEHOLDER (US-005)`.
5. Apply `Shape.kt` — swap radii, strip markers.
6. Apply `CurroSpacing.kt` — only tweak the KDoc if needed; no value
   change.
7. Apply the two `colors.xml` files — swap the hex, update the comment
   wording.
8. Apply `strings.xml` — every COPY ID, in section-header-grouped order
   matching the brief. **Verbatim** Spanish from the table.
9. Rewrite `.claude/skills/brand-design/SKILL.md` — fill every TODO, fix
   the spacing scale, replace the partial COPY table with the full one,
   align Component Patterns with US-004's signatures, document the
   prototype reality for Logo & Image Aspect Ratios.
10. Run `./gradlew assembleDebug ktlintCheck detekt test`. All green.
11. Run the app (debug install on the emulator or Redmi 15). Eyeball the
    smoke composable in light + dark. Capture screenshots.
12. Open the PR; paste the contrast table from the brief into the PR
    description with the *measured* ratios (from the tool); attach the
    two screenshots; reference this brief.

**Things that look like they should change but don't:**

- `CurroTheme.kt` — no change.
- `Dimens.kt` — no change.
- `CurroSpacing.kt` values — no change.
- `BigPrimaryButton.kt` / `BigCard.kt` — no change.
- The manifest — no change.
- `themes.xml` — no change (the parent `Theme.AppCompat.DayNight.NoActionBar`
  + the `windowBackground` reference stay; only the *colour* referenced
  changes, and that's done in `colors.xml`).
- The detekt config — no change. The exclude carved by US-003 is the
  same shape and scope.

**`MissingTranslation` warning suppression**: If Android Studio fires
`MissingTranslation` on the new `strings.xml` entries (because there's no
matching `values-en/strings.xml`), add to `app/build.gradle.kts`:

```kotlin
android {
    lint {
        disable += setOf("MissingTranslation")
    }
}
```

with a comment: `// Curro is Spanish-only — values/strings.xml IS the
canonical locale. See docs/briefs/US-005-brand-design-fillin.md`. If the
warning doesn't fire, do nothing. The developer's call at application
time.

**The "user reviews values before commit" loop**: This brief is the
proposal. The user will read the value tables and either say "dale" or
ask for one or two tweaks (e.g. "make `primary` warmer" → `#9A3E15` →
`#A8451A`; "I prefer 64 sp on the clock" → drop the bump). The developer
applies the tweaks to the brief's tables *before* applying the code so
the brief, the code, and the skill all agree. The contrast table must
be re-verified after any tweak.

## Open Questions

None for the architect (US-004 A1–A14 are still valid). One for the
user — answered by the user's review pass:

- **Q1.** Does the proposed palette ("Sol y olivar") feel like Curro? The
  brief proposes warm terracotta + olive + ochre + cream. If the user
  prefers a different temperature (e.g. cooler), say so before the
  developer applies.
- **Q2.** Is the `displayLarge` bump (64 → 72 sp) approved? It's the
  clock; bigger is better for elderly visual acuity, but it may clip at
  `fontScale = 2.0f` if pushed too far. The brief proposes 72 sp; user
  can veto.
- **Q3.** Any COPY line marked `(NEW)` that the user wants reworded? The
  brief flags each so the user can review the new ones distinctly from
  the spec-derived (closed-decision) lines.

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-13 | Fran (PM, Claude `android-product-analyst`) | Initial draft |
