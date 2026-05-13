---
name: launcher-ui
description: Curro's actual UI surfaces and the senior-first design rules — the launcher home (big clock, ≥40%-screen mic button, 4–6 huge app tiles, "Más apps"), the assistant overlays (listening / processing / confirmation / message cards / contact picker), the hidden config menu, and the non-negotiable senior constraints (≥96 dp tap targets, minimum text sizes, "feels the same every day", high contrast, audio always accompanies the screen).
triggers:
  - launcher home
  - home screen
  - mic button
  - app grid
  - app tiles
  - listening overlay
  - processing overlay
  - confirmation
  - message cards
  - contact picker
  - config menu
  - settings menu
  - senior UI
  - elderly
  - large text
  - touch target
  - card
  - list
---

# Launcher UI (Curro's screens + the senior-first rules)

This skill replaces the old generic "feed-design" — Curro has no feed; it has a
launcher home, a handful of state-driven overlays, and a hidden config menu. The
visual style (colours, type scale, radii) is owned by `brand-design` (currently a
template — fill it in); this skill is **what the surfaces are** and **the rules they
must obey for this user**. Source: `docs/curro-spec-v1.0.md` §3, §9, §11; flows
1–7. Compose mechanics: `compose-patterns`; a11y mechanics: `accessibility-patterns`.

## The senior-first rules (non-negotiable — spec §3, §11)

The user: deteriorated-but-functional vision, good hearing, reduced fine motor
control, very slow learning curve for new UIs. So:

1. **Tap targets ≥ 96 dp** (the spec's number — *not* Material's 48 dp). The mic
   button is ≥ 40 % of the screen. SÍ/NO confirmation buttons are huge. App tiles
   are huge. Spacing between targets generous (no fat-fingering a neighbour).
2. **Text is big.** Body text well above Material defaults (think `displayMedium`/
   `headlineLarge` sizes for what would normally be body); the clock is enormous.
   Respect the system font-size setting *on top of* that (don't cap it).
3. **High contrast.** WCAG AA is the floor, not the goal — aim higher (≥ 7:1 for
   body where you can). Never colour-only signalling — pair with text/icon/shape.
4. **It feels the same every day.** The home layout is **fixed** — clock here, mic
   button there, the same app tiles in the same spots. The favourites grid recomputes
   *occasionally*, not on every open (see `local-data`). No A/B-ish variation, no
   "smart" reordering that surprises him. New visual states only appear when *he*
   triggered them.
5. **Audio + visual together, always.** Every Curro→user message is spoken **and**
   shown (spec §4.6). The screen reinforces the voice; it never replaces it.
6. **No fussy animation.** A static or very calm indicator while `processing` — no
   spinners-of-spinners, no parallax, nothing that distracts (spec §11). Transitions
   are quick and quiet.
7. **One thing at a time, big.** Don't crowd. A screen does one job. The active
   message/option is visually distinct (highlighted), the rest recedes.

These override Material 3 defaults wherever they conflict. When `android-ui-designer`
or `material-design` say "48 dp / Material type scale", read it as "the *floor* —
Curro goes bigger".

## The surfaces

### 1. Launcher home (`idle` — always visible on HOME) — spec §11

```
┌─────────────────────────────┐
│            12:47            │  ← huge clock
│       Miércoles 13 mayo     │  ← date, large
│                             │
│   ┌─────────────────────┐   │
│   │                     │   │
│   │      🎤  CURRO      │   │  ← the main button: ≥40% of screen,
│   │                     │   │     big mic icon + large label,
│   └─────────────────────┘   │     haptic on press
│                             │
│   [ WhatsApp ] [ Llamadas ] │  ← 4–6 huge app tiles (icon + name),
│   [  Cámara  ] [  Fotos   ] │     favourites by use (stable!) or Fran-set
│                             │
│        [  Más apps  ]       │  ← secondary: full app list, big rows
└─────────────────────────────┘
```

- The clock: tapping it **5× within 3 s** opens the config menu (spec §9) — nothing
  visible hints at this; it's Fran's back door. A stray single tap does nothing.
- App tiles launch directly (`PackageManager` launch intent — see `platform-integrations`).
- "Más apps": a scrollable list of all launchable apps, big rows (icon + label),
  alphabetical, with a search-by-voice option (the mic works here too).
- Composables: `LauncherScreen` (collects `LauncherViewModel.uiState` and the
  assistant `StateFlow<AssistantState>`), `ClockBlock`, `MicButton`, `AppTileGrid`,
  `AppTile`, `MoreAppsScreen`.

### 2. Listening overlay (`listening`) — flows 1–6

Screen tints **light blue**, shows "Te escucho…" large, and the **live
transcription** below in big text as STT produces partials. The mic button changes
colour and shows audio-wave feedback. A button press here cancels and restarts
listening (the interrupt rule — `voice-interaction`).

### 3. Processing overlay (`processing`)

"Un momento…" with a **non-animated** indicator. That's it. (Spec §11 — complex
animation distracts.)

### 4. Confirmation overlay (`confirming`) — flows 2, 3

The resolved target stated plainly ("¿Llamo a **Pepe Martínez**?") + two **huge**
buttons: ✅ **SÍ** / ❌ **NO** (≥ 96 dp each, well separated, high contrast, clear
icons + text). Also accepts "sí"/"no" by voice. For a disambiguation (3 Marías):
one big button per candidate with **full name + photo** (if any) + a "**Ninguna**"
button; Curro reads up to ~3 by voice. 10 s of silence → "Cancelo entonces" → home.

### 5. Message cards (`executing`, reading WhatsApp) — flow 5

Big cards, scrollable, **grouped by sender** (not by time): sender name (large,
bold) + the message text (large). The message currently being read aloud is
**highlighted**. After the read finishes, the cards stay until the next interaction.
No new messages → "No tienes mensajes nuevos" (short, no card).

### 6. Contact picker (`confirming`, alias learning) — flow 4

A scrollable list of contacts, **big rows** (photo + full name), used both for the
3-Marías disambiguation and the alias-learning subflow ("¿Es alguno de estos?" —
reads up to 5). Always a "**Ninguno de estos**" / "**Ninguna**" row. Selecting one
proceeds; "Ninguno" → "Vale, dile a Fran que apunte quién es…" (`local-data`).

### 7. Config menu (Fran-only) — spec §9

Opened by the 5-tap-on-clock gesture. A plain, dense (it's for Fran, not the user),
scrollable settings screen — this one *can* use a normal layout (it's not for the
senior). A back chevron (`Icons.AutoMirrored.Filled.KeyboardArrowLeft`, large) at
`Alignment.TopStart` in a `Box` (no `TopAppBar` — see the No-Double-Padding rule in
`CLAUDE.md`). Sections (spec §9):
- **Alias de contactos** — list/add/edit/delete (`local-data`).
- **Apps favoritas** — which 4–6 show on home; auto-by-use, editable.
- **Voz del TTS** — installed-voice picker, speech-rate slider (~10–15 % slower
  default for seniors), pitch.
- **Modo asistente de llamadas** — toggle, off by default (spec §8).
- **Umbrales de confianza** — two sliders (0–1); defaults 0.85 / 0.60.
- **Confirma siempre** — toggle (forces confirmation for all `conditional` actions).
- **Logs de comandos fallidos** — last 50, with timestamps + kind.
- **Modo "envíame los fallos"** — toggle, off by default (anonymized).
- **Reset de aprendizaje** — clears learned aliases/favourites (confirm — destructive).
- **Versión y diagnóstico** — app version, model state (loaded? warm? last latency),
  am-I-the-default-launcher, granted permissions, the HyperOS battery-whitelist deep
  link (`launcher-app`).

## Composable conventions (Curro flavour)

- All screens wrapped in `CurroTheme { … }`; tokens via `MaterialTheme.colorScheme.*`
  / `MaterialTheme.typography.*` / `CurroSpacing.*` / `CurroShapes.*` — never raw
  `Color(0xFF…)` / `.sp` / `.dp` literals.
- Shared big components in `presentation/common/`: `BigPrimaryButton` (≥ 96 dp),
  `BigYesNoRow`, `BigCard`, `BigListRow` (icon/photo + large label). Build the rest
  of the UI from these so sizing is consistent everywhere.
- The launcher home + config menu are the only nav routes; listening/processing/
  confirming/message/picker are **state-driven overlays** on top of the home,
  selected by the `AssistantState` (`voice-interaction`) — not navigation.
- Spanish strings come from resources / the copy module, in Curro's voice
  (`brand-design`) — never hard-coded; supply `contentDescription` for every
  `Image`/`Icon` (or `null` if purely decorative).
- `@Preview` every reusable component — light, dark, **and a `fontScale = 1.5f` /
  `2.0f` preview** (this user will have large fonts on).

## Testing (see `testing-patterns`)

- UI tests on the `Content` composables (not the screens with ViewModels):
  - `LauncherScreen` renders clock + mic button + the favourites grid; the mic button
    is ≥ 96 dp (really ≥ 40 % screen); a tile tap fires `AppTileTapped`; 5 quick
    clock taps fire the config-open event, a single tap doesn't.
  - `ListeningOverlay` shows the live transcript; tints; mic press → cancel/restart.
  - `ConfirmationOverlay`: SÍ/NO are ≥ 96 dp, high contrast, fire the right events;
    disambiguation list shows N candidates + "Ninguna".
  - `MessageCardsScreen`: grouped by sender, the read-aloud one highlighted; empty →
    "No tienes mensajes nuevos".
  - `ConfigMenuScreen`: each section present; back chevron works.
  - Accessibility sweep: no `Image`/`Icon` without `contentDescription`; every
    `clickable` node ≥ 96 dp; text scales with `fontScale`.
- Visual check on the real Redmi 15 with large system font + high-contrast on.

## Rules

1. **Senior-first overrides Material defaults** — ≥ 96 dp targets, big text, high contrast, no fussy animation, fixed/stable layout, audio + visual together.
2. **The home is fixed and predictable** — favourites recompute occasionally, not on every open; new visual states only on user action.
3. **Overlays are state-driven, not nav routes**; only home + config menu are routes.
4. **Build everything from the shared big components** (`BigPrimaryButton`, `BigYesNoRow`, `BigCard`, `BigListRow`) so sizing/contrast are consistent.
5. **The config menu is the one place a normal layout is OK** (it's for Fran) — but still no `TopAppBar` in a child screen (No-Double-Padding); back = large chevron at TopStart.
6. **Spanish copy from resources, in Curro's voice; `contentDescription` on every image/icon; `@Preview` includes a large-font variant.**
7. **`brand-design` is authoritative** for the actual colours/type/radii — fill it in; this skill assumes those tokens exist.
