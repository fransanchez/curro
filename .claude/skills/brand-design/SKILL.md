# Brand Design

> ⚠️ **TEMPLATE — fill this in before building real UI.**
> This file is the **AUTHORITATIVE** source for every design decision in the Curro
> app: colours, typography, spacing, shapes, **and Curro's spoken/written voice**.
> Until the `TODO`s are replaced with the real brand, treat any hard-coded colour /
> font size / spacing / Spanish string in code as "blocked on brand-design" rather
> than approving it — **and the real values must satisfy the senior-first constraints
> below, not a guessed Material palette.**

Kotlin / Jetpack Compose implementation of the Curro brand system, plus the canonical
copy. When this is complete, the other design skills (`material-design`,
`compose-patterns`, `launcher-ui`, `accessibility-patterns`) and the voice skill
(`voice-interaction`) defer to the values defined here. Source for the constraints:
`docs/curro-spec-v1.0.md` §2 (voice), §3 (the user), §11 (the surfaces).

---

## Senior-first constraints (non-negotiable — spec §3, §11)

Curro's only validated user: a man in Málaga (Fran's father) — deteriorated-but-
functional vision, good hearing, **reduced fine motor control**, very slow learning
curve for new UIs. The brand must be chosen around this. **These override Material 3
defaults wherever they conflict, and the `TODO` palette / type scale / radii below
must be picked to satisfy them.**

1. **Tap targets ≥ 96 dp.** (Spec §3's number — *not* Material's 48 dp.) He has
   reduced fine motor control; a 48 dp target is a miss waiting to happen. Generous
   spacing between targets so a neighbour isn't hit by accident. The main mic button
   on the launcher home is **≥ 40 % of the screen**. SÍ/NO confirmation buttons and
   app tiles are huge.
2. **Text well above Material defaults.** What is normally a *body* role should look
   like what is normally a *headline*; the launcher clock is enormous. Pick the type
   scale so the body-text role is large by default.
3. **Respect AND amplify the system font-scale setting.** Never cap `fontScale`; the
   layout must survive `1.5×`–`2.0×` (preview every reusable component at those
   scales — see `compose-patterns`, `accessibility-patterns`).
4. **Very high contrast.** WCAG AA (≥ 4.5:1 for body, ≥ 3:1 for large text / UI
   components) is the **floor, not the goal** — aim **≥ 7:1 for body** where the
   palette allows. Verify **every** brand pairing in **light AND dark**. Pick
   `onPrimary` / `onSurface` / etc. for contrast, not aesthetics.
5. **Colour is never the only signal.** Always pair colour with text/icon/shape (a
   "selected"/"active"/"error" state must read without colour vision).
6. **No fussy animation.** Calm, quick, quiet transitions; a static or very gentle
   indicator while `processing`. Nothing parallax, nothing distracting (spec §11).
7. **It feels the same every day.** The launcher layout is **fixed and predictable** —
   clock here, mic button there, the same app tiles in the same spots; the favourites
   grid recomputes *occasionally*, not on every open (`local-data`). New visual states
   appear only when *he* triggered them. No "smart" reordering, no A/B-ish variation.
8. **Audio + visual together, always.** Every Curro→user message is **spoken and
   shown** (spec §4.6). The screen reinforces the voice; it never replaces it.

The surfaces these constraints apply to are described in `launcher-ui` (the launcher
home, the state-driven assistant overlays, the hidden config menu).

---

## Curro's voice & Spanish copy (AUTHORITATIVE)

This skill is where Curro's **tone** is canonicalised: every line the TTS speaks and
every user-facing UI string gets its wording decided here, then lives in
`res/values-es/` (or a dedicated copy module) — **never hard-coded in composables or
handlers** (code/docs are English; user-facing strings are Castilian Spanish). The
*behaviour* that triggers each line is owned by `voice-interaction` (the FSM); this is
the *words*. Source: spec §2 + all of §6's flows.

### Tone (spec §2 — non-negotiable)

Warm, Andalusian, **colloquial Castilian Spanish** — **efficient and close, NOT
servile.** Like a friend who helps, not a butler.

- ✅ "Vale, llamando a Pepito." · ✅ "Un momento…" · ✅ "Lo apunto: Lucía Ruiz es tu
  hija. Llamando." · ✅ "No tienes mensajes nuevos."
- ❌ "Claro, cómo no, ahora mismo." · ❌ constant "lo siento / disculpa" · ❌ codes,
  jargon, technical terms · ❌ silence · ❌ trapping the user in loops.

### Fail comprehensibly (spec §2, §6 flows 6 & 7)

**Every error → a plain Spanish sentence + a proposed alternative.** Never a code,
never a stack of jargon, never silence. Examples already in the spec:
- `COPY.error_unknown_function` — "Eso no lo sé hacer todavía. Pulsa el botón y
  pídeme otra cosa, o di 'ayuda' para que te cuente lo que sí sé hacer." (flow 7)
- `COPY.disambig_give_up` — "Mejor llámala desde la agenda, no me aclaro." (flow 3 —
  after the disambiguation list misfires twice)
- `COPY.alias_defer_to_fran` — "Vale, no pasa nada. Dile a Fran que apunte quién es
  tu hija." (flow 4 — when the user picks "ninguno")
- `COPY.whatsapp_parse_miss` — "Tienes mensajes nuevos pero no he podido leerlos
  bien." (`platform-integrations` — WhatsApp notification couldn't be parsed)
- `COPY.call_permission_missing` — "Necesito permiso para llamar; dile a Fran que lo
  active." (and the equivalent for any revoked permission)

### Canonical lines (label them; reference by ID — keep them short)

| ID | Spanish | When (see `voice-interaction`) |
|---|---|---|
| `COPY.listening_prompt` | "Te escucho…" | entering `listening` |
| `COPY.processing` | "Un momento…" | entering `processing` |
| `COPY.confirm_call` | "¿Llamo a {nombre}?" / "Voy a llamar a {nombre}, ¿confirmas?" | `confirming` (call) |
| `COPY.confirm_no` | "Vale, no llamo." | user says/taps "no" in `confirming` |
| `COPY.confirm_timeout` | "Cancelo entonces." | 10 s of silence in `confirming` → `idle` |
| `COPY.stt_fail_1` | "No te he oído bien, ¿puedes repetirlo?" | 1st consecutive STT failure |
| `COPY.stt_fail_2` | "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto." | 2nd consecutive STT failure |
| `COPY.stt_fail_3` | "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo." | 3rd consecutive STT failure → `idle`, counter reset |
| `COPY.no_unread` | "No tienes mensajes nuevos." | `read_*_whatsapp` with nothing unread |
| `COPY.many_unread` | "Tienes muchos mensajes. ¿Te los leo todos o solo los de alguien?" | > 8 unread (a nod toward Gemma-3n summarization) |
| `COPY.calling` | "Llamando a {nombre}." | `executing` a call |
| `COPY.alias_ask` | "Aún no sé quién es {relación}. ¿Es alguno de estos contactos? Te los leo: {nombres…}" | alias-learning subflow (`local-data`) |
| `COPY.alias_saved` | "Vale, {nombre completo} es tu {relación}. Apuntado. Llamando ahora." | alias persisted, action proceeds |
| `COPY.disambig_ask` | "Tienes {N} {nombre}s. ¿Cuál de ellas?: {nombres…}" | multiple contacts match (`platform-integrations`) |
| `COPY.cold_model` | "Dame un segundo." | Gemma 3n cold-load (`on-device-llm`) |

Reading messages aloud is composed at runtime but in this voice — short, present
tense, **grouped by sender** (not by time): "Tienes 3 mensajes de Pepito y 1 mensaje
de Lucía. Empiezo con Pepito: 'Te espero a las siete'. … De Lucía: 'Mañana te llamo,
papá'." (spec flow 5).

> When you add a new catalog function, every line its handler can speak gets a `COPY.*`
> entry here, in this voice, before it ships (`function-catalog` → "how to add").

---

## Color Palette

> TODO: Define the real brand palette. The scaffold below uses placeholder values
> so the app compiles and `CurroTheme` resolves — **do not ship these**, and when you
> choose the real ones, satisfy the senior-first contrast rule (≥ 7:1 body where
> possible; verify light AND dark).

### Brand Colors
- **BrandPrimary**: `0xFF6750A4` — primary actions (mic button, SÍ), selected states — _TODO_
- **BrandSecondary**: `0xFF625B71` — secondary actions, supporting accents — _TODO_
- **BrandTertiary**: `0xFF7D5260` — special moments / highlights — _TODO_
- **BrandSurface**: `0xFFFFFBFE` — app / launcher background — _TODO_
- **BrandSurfaceVariant**: `0xFFE7E0EC` — cards, tiles, containers, dividers — _TODO_

### Neutral / Text Colors
- **TextPrimary**: `0xFF1C1B1F` — clock, headings, body text — _TODO_
- **TextSecondary**: `0xFF49454F` — secondary text — _TODO_
- **TextTertiary**: `0xFF79747E` — borders, disabled — use sparingly; "disabled" must
  still read at a distance — _TODO_

### Semantic Colors
- **Success**: `0xFF2E7D32` — _TODO_
- **Warning**: `0xFFF9A825` — _TODO_
- **Error**: `0xFFB3261E` — error states (keep distinct from any brand accent) — _TODO_

### Listening tint
- **ListeningTint**: a calm light-blue overlay/background applied while `listening`
  (spec §11). Pick it to keep the live-transcription text above ≥ 7:1 on it — _TODO_

### Dark Mode
> TODO: Map every token above to its dark-mode counterpart. Guidelines:
> - Text → light tints; backgrounds → warm/cool near-black (not pure `#000`)
> - Brand accents → desaturate / lighten slightly so they stay legible on dark
> - Re-check contrast for **every** pairing — the senior contrast floor applies in
>   dark too (and high-contrast system mode must still look right)

---

## Typography

> TODO: Decide the type family (system default vs. a bundled font) and the scale.
> **The body-text role must be large by default** (think `headline`-sized for what is
> normally body). The scaffold below is plain Material 3 with the system font and is
> NOT big enough — replace it.

| Role (Material slot)       | Size | Weight   | Usage                          |
|----------------------------|------|----------|--------------------------------|
| `displayLarge`             | _TODO (very large)_ | Bold | the launcher clock |
| `displayMedium`            | _TODO (large)_ | Bold | overlay headlines ("Te escucho…") |
| `headlineLarge`            | _TODO_ | Bold | screen titles, sender names on cards |
| `headlineMedium`           | _TODO_ | SemiBold | card titles, list-row primary text |
| `titleLarge`               | _TODO_ | SemiBold | sub-sections |
| `bodyLarge`                | _TODO (looks like a headline)_ | Regular | **body text** — message bodies, prompts |
| `bodyMedium`               | _TODO_ | Regular | secondary text |
| `labelLarge`               | _TODO_ | SemiBold | the *only* small role — button/chip text |

Exposed via `CurroTypography` (a Material 3 `Typography` instance) inside `CurroTheme`.
Composables use `MaterialTheme.typography.*` — never `.sp` literals.

---

## Spacing System

```kotlin
object CurroSpacing {
    val Small = 8.dp    // inner padding, tight gaps within a component
    val Medium = 16.dp  // standard padding
    val Large = 24.dp   // section spacing
    val XLarge = 32.dp  // screen-level padding, gaps BETWEEN tap targets
}
```
> TODO: Confirm the scale. The gap between adjacent tap targets must be generous
> (reduced fine motor control — `XLarge` or more between big buttons/tiles).
> Composables use `CurroSpacing.*` — never `.dp` literals.

---

## Corner Radius / Shapes

```kotlin
object CurroShapes {
    val Small = RoundedCornerShape(12.dp)   // tiles, list rows
    val Medium = RoundedCornerShape(16.dp)  // big buttons, cards, containers
    val Large = RoundedCornerShape(28.dp)   // large surfaces, the mic button
}
```
> TODO: Confirm radii. Wire these into `MaterialTheme.shapes` via `CurroTheme`.
> Composables use `CurroShapes.*` / `MaterialTheme.shapes.*` — never `.dp` literals.

---

## Theme Entry Point

All screens are wrapped in `CurroTheme { … }` (see `presentation/theme/`), which
supplies the colour scheme (`CurroColorScheme`, light + dark), `CurroTypography`, and
`CurroShapes`. Composables read tokens through `MaterialTheme.colorScheme.*` /
`MaterialTheme.typography.*` / `CurroSpacing.*` / `CurroShapes.*` — **never** raw
`Color(0xFF…)` / `.sp` / `.dp` literals. Keep this skill in sync with
`presentation/theme/`.

> TODO: Implement `CurroTheme`, `CurroColorScheme` (light + dark), `CurroTypography`,
> `CurroShapes`, `CurroSpacing`. (Dynamic wallpaper colour is *probably wrong* here —
> "feels the same every day" + the contrast floor argue for a fixed scheme; decide
> deliberately, don't default to `dynamicColor = true`.)

---

## Logo & Iconography

> TODO: Drop brand assets in `app/src/main/res/drawable/` and document them here.
- App icon / wordmark assets ("Curro") — _TODO_
- The mic icon used on the main button — must be unmistakably a microphone, large — _TODO_
- Usage rules (clear space, min size, no recolouring/rotation/effects) — _TODO_

---

## Component Patterns

> TODO: Replace with the app's real recipes once the brand is set. These are the
> Curro-flavoured starting points — built around the **shared big components** in
> `presentation/common/` (`BigPrimaryButton`, `BigYesNoRow`, `BigCard`, `BigListRow`)
> so sizing/contrast are consistent everywhere (see `launcher-ui`).

### Big primary button (e.g. SÍ, "Más apps", "Hazme tu pantalla de inicio")
```kotlin
Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),   // ≥ 96 dp — senior-first
    shape = CurroShapes.Medium,
) {
    Text("Sí", style = MaterialTheme.typography.headlineMedium)
}
```

### Big card (e.g. a WhatsApp message card)
```kotlin
Card(
    shape = CurroShapes.Medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(CurroSpacing.Large)) {
        Text(senderName, style = MaterialTheme.typography.headlineMedium)   // bold, large
        Spacer(Modifier.height(CurroSpacing.Small))
        Text(messageText, style = MaterialTheme.typography.bodyLarge)       // body = headline-sized
    }
}
```

### Big list row (e.g. an app tile / a contact-picker row)
```kotlin
Row(
    Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(CurroSpacing.Large),  // ≥ 96 dp
    verticalAlignment = Alignment.CenterVertically,
) {
    AsyncImage(
        model = iconOrPhoto,
        contentDescription = label,                 // never null when it carries meaning
        modifier = Modifier.size(CurroSpacing.XLarge * 2).clip(CurroShapes.Small),
    )
    Spacer(Modifier.width(CurroSpacing.Large))
    Text(label, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
}
```

---

## Image Aspect Ratios

> TODO: Define the ratios the product uses (likely just **1:1** for contact photos
> and app icons — Curro shows little imagery). Coil `AsyncImage` always sets
> `contentDescription` (or `null` only when purely decorative).

---

## Accessibility (non-negotiable — consistent with the senior-first constraints above)

- Minimum touch target: **96 dp × 96 dp** (NOT 48 dp — the user has reduced fine
  motor control). The mic button ≥ 40 % of the screen. Generous spacing between targets.
- Contrast: **WCAG AA is the floor** — ≥ 4.5:1 body, ≥ 3:1 large text / UI; **aim
  ≥ 7:1 for body** where the palette allows. Verify every pairing, light **and** dark.
- Colour is never the only signal — pair with text/icon/shape.
- Every `Image`/`Icon` has a `contentDescription` (or `null` if decorative).
- Respect and amplify the system font-scale setting — never cap it; survive `2.0×`.
- Pick `onPrimary` / `onSurface` / etc. for **contrast**, not aesthetics.
- See `accessibility-patterns` for the Compose mechanics; `launcher-ui` for how the
  surfaces apply all of this; `material-design` for the Material foundation Curro
  scales up; `voice-interaction` for the FSM that triggers the spoken copy above.
