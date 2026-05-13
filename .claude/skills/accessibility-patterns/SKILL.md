---
name: accessibility-patterns
description: Accessibility for Curro — the senior-first baseline (≥96 dp tap targets, large text, system font-scale, ≥4.5:1/≥7:1 contrast, audio-always-with-the-screen, predictable UI) and the Compose a11y mechanics (semantics, liveRegion, customActions, focus, contentDescription, stateDescription, heading, TalkBack/Scanner testing).
triggers:
  - accessibility
  - a11y
  - contentDescription
  - semantics
  - liveRegion
  - TalkBack
  - screen reader
  - touch target
  - contrast
  - font scale
  - fontScale
  - large text
  - elderly
  - senior
  - customActions
  - focus order
---

# Accessibility Patterns (Curro)

Accessibility isn't a checkbox on Curro — it **is** the product. The whole app is
audio-first, big, high-contrast and fixed *because* of who it's for. Source:
`docs/curro-spec-v1.0.md` §3, §11. The visual tokens (colours, type scale, radii) are
owned by `brand-design` (currently a template); the surfaces by `launcher-ui`; the
Material foundation Curro scales up is `material-design`.

## Senior-first baseline (Curro) — read this first

The only validated user: a man in Málaga (Fran's father) — deteriorated-but-functional
vision, good hearing, **reduced fine motor control**, very slow learning curve for new
UIs. So, everywhere:

1. **Tap targets ≥ 96 dp** — *not* Material's 48 dp. A 48 dp target is a miss waiting
   to happen with reduced fine motor control. Generous spacing between targets so a
   neighbour isn't hit by accident. The launcher mic button is ≥ 40 % of the screen;
   SÍ/NO buttons, app tiles, picker rows are huge.
2. **Text well above Material defaults** — body-sized text reads like a headline;
   the launcher clock is enormous (`brand-design` owns the scale).
3. **Respect AND amplify the system font-scale setting** — never cap `fontScale`; the
   layout must survive `1.5×`–`2.0×`. `@Preview` every reusable component at those
   scales (see below + `compose-patterns`).
4. **Very high contrast** — WCAG AA (≥ 4.5:1 body, ≥ 3:1 large text / UI) is the
   **floor**; aim **≥ 7:1 for body** where the palette allows. Verify every pairing,
   light **and** dark.
5. **Colour is never the only signal** — pair it with text/icon/shape (active /
   selected / error / read-aloud-now states must read without colour vision).
6. **Audio feedback always accompanies the screen** — every Curro→user message is
   spoken **and** shown (spec §4.6). The screen *reinforces* the voice; it never
   replaces it. (Failures are spoken too — a plain Spanish sentence + an alternative,
   never a code; copy in `brand-design`, behaviour in `voice-interaction`.)
7. **No fussy animation; predictable, stable layout** — calm, quick transitions; the
   launcher home is fixed ("feels the same every day"); new visual states only appear
   when *he* triggered them.

> **TalkBack / screen-reader support is SECONDARY for Curro** — the user isn't a
> TalkBack user, and the app is audio-first by design (Curro narrates everything via
> TTS). But `contentDescription` on every `Image`/`Icon` (or `null` if purely
> decorative) is still **mandatory**, and all the semantics mechanics below still
> apply — they're cheap correctness, and they keep the door open if a future user does
> use a screen reader.

## contentDescription on every Image / Icon

```kotlin
// A big app tile on the launcher home (see launcher-ui)
@Composable
fun AppTile(app: AppEntry, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .sizeIn(minWidth = 96.dp, minHeight = 96.dp)   // ≥ 96 dp — senior-first
            .clip(CurroShapes.Small)
            .clickable(onClick = onTap)
            .padding(CurroSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = app.icon,
            contentDescription = null,                       // decorative — the label says it
            modifier = Modifier.size(CurroSpacing.XLarge * 2),
        )
        Spacer(Modifier.height(CurroSpacing.Small))
        Text(app.label, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
    }
}
```

Icons that *carry* meaning describe the action, not the glyph:

```kotlin
// The back chevron in the config menu (Fran-only screen — see navigation-patterns)
IconButton(
    onClick = onBack,
    modifier = Modifier.size(96.dp),                         // ≥ 96 dp
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        contentDescription = "Volver",                        // the action
        modifier = Modifier.size(48.dp),
    )
}
```

## Touch target minimum — 96 dp (not 48 dp)

```kotlin
@Composable
fun BigPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 96.dp),   // Curro: ≥ 96 dp
        shape = CurroShapes.Medium,
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium)
    }
}

// The SÍ / NO row in the confirmation overlay (voice-interaction)
@Composable
fun BigYesNoRow(onYes: () -> Unit, onNo: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CurroSpacing.XLarge)) {  // wide gap
        BigPrimaryButton("Sí", onYes, Modifier.weight(1f))
        BigPrimaryButton("No", onNo, Modifier.weight(1f))
    }
}
```

`IconButton` defaults to a 48 dp box — for Curro, size it up explicitly (96 dp) and
keep the glyph itself a comfortable size inside.

## Colour contrast

Verify against the real `brand-design` tokens — and remember Curro's floor is higher
than the usual AA minimum:

```kotlin
@Composable
fun AccessibleBodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,          // body = headline-sized
        color = MaterialTheme.colorScheme.onSurface,         // chosen for contrast in brand-design
        modifier = modifier,
    )
}
```

- Verify **≥ 4.5:1** for every body-text pairing (≥ 3:1 for large text / UI
  components); **this user should get ≥ 7:1 for body** wherever the palette allows.
- Check **light AND dark**, plus high-contrast system mode.
- Use Android Studio's contrast check / a contrast checker on the *actual* token
  values — don't approve hexes against a guessed palette (`brand-design` is the
  authority; it's a template until filled in).

## Live regions for dynamic content

Curro narrates everything via TTS, so live regions are belt-and-braces — but the
listening overlay's live transcript and an error message are the natural cases:

```kotlin
// The live transcription in the listening overlay (flows 1–6)
@Composable
fun LiveTranscript(partial: String, modifier: Modifier = Modifier) {
    Text(
        text = partial,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

// An error message (spec flows 6 & 7) — spoken AND shown
@Composable
fun ErrorMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,                                          // e.g. "No sé hacer eso todavía"
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}
```

## stateDescription / heading

```kotlin
// The message currently being read aloud is highlighted AND announced (flow 5)
Card(
    modifier = Modifier.semantics {
        if (isReadingNow) stateDescription = "Leyendo ahora"
    },
) { /* sender name + message text */ }

// Section headers in the config menu
Text(
    "Alias de contactos",
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.semantics { heading() },
)
```

## Custom actions for complex rows

A contact-picker row (the 3-Marías disambiguation / alias-learning subflow — `local-data`)
has one primary action; if it ever grows secondaries, expose them as custom actions
rather than cramming tiny buttons in:

```kotlin
@Composable
fun ContactPickerRow(
    contact: Contact,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clickable(onClick = onSelect)
            .padding(CurroSpacing.Large)
            .semantics(mergeDescendants = true) {
                onClick(label = "Elegir a ${contact.name}") { onSelect(); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(model = contact.photoUri, contentDescription = null,
            modifier = Modifier.size(CurroSpacing.XLarge * 2).clip(CurroShapes.Small))
        Spacer(Modifier.width(CurroSpacing.Large))
        Text(contact.name, style = MaterialTheme.typography.headlineMedium)
    }
}
```

## Focus order / focus management

The one screen with text input is the config menu (alias editing — Fran-only). Keep a
sensible top-to-bottom order; move focus to the first field when an edit sheet opens.
Compose handles most of this for a simple `Column`; use `focusOrder` / a
`FocusRequester` only where the visual order and the desired traversal order differ.

```kotlin
val firstField = remember { FocusRequester() }
LaunchedEffect(Unit) { firstField.requestFocus() }
TextField(value = alias, onValueChange = onAliasChange,
    label = { Text("Alias (\"mi hija\")") },
    modifier = Modifier.fillMaxWidth().focusRequester(firstField))
TextField(value = contactName, onValueChange = onContactNameChange,
    label = { Text("Contacto") }, modifier = Modifier.fillMaxWidth())
```

## Testing with TalkBack / Accessibility Scanner

Secondary for Curro, but a quick pass catches missing descriptions / undersized
targets:

```bash
# Enable TalkBack via adb
adb shell settings put secure enabled_accessibility_services com.google.android.marionette/.TalkBackService
# Keep the screen on while testing
adb shell svc power stayon true
```

Google **Accessibility Scanner** (Play Store): open Curro, tap the scan button,
fix flagged issues (small targets, low contrast, missing labels) before release.
Also test with **large system font + high-contrast mode on** on the real Redmi 15 —
that's the user's real configuration.

## Accessible Compose Preview (always include a large-font variant)

```kotlin
@Preview(name = "Light", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 915)
@Preview(name = "Large font", showBackground = true, fontScale = 1.5f, widthDp = 412, heightDp = 915)
@Composable
private fun ConfirmationOverlayPreview() {
    CurroTheme {
        ConfirmationOverlay(
            prompt = "¿Llamo a Pepe Martínez?",
            onYes = {}, onNo = {},
        )
    }
}
```

## Semantic modifiers — summary

```kotlin
Modifier.semantics {
    contentDescription = "Volver"               // for Image/Icon that carries meaning
    heading()                                   // section header
    onClick(label = "Elegir a Lucía") { true }  // labelled primary action
    stateDescription = "Leyendo ahora"          // current state of a node
    liveRegion = LiveRegionMode.Polite          // announce updates (Assertive for errors)
    customActions = listOf(/* CustomAccessibilityAction(...) */)
    // mergeDescendants = true on the parent Row/Column to read a row as one node
}
```

## Rules

1. **Senior-first baseline overrides Material defaults** — ≥ 96 dp targets, big text, ≥ 4.5:1 (aim ≥ 7:1) contrast, system font-scale respected/amplified, audio + visual together, predictable layout. This is the product, not a checkbox.
2. **`contentDescription` on every `Image`/`Icon`** (or `null` if decorative) — mandatory even though TalkBack is secondary.
3. **TalkBack/screen-reader support is secondary** — the app is audio-first by design; still keep the semantics mechanics correct.
4. **Verify contrast against the real `brand-design` tokens, light AND dark** — never approve hexes against a guessed palette.
5. **Every reusable component gets a `fontScale = 1.5f`/`2.0f` preview** — and must survive it without clipping.
6. **Spanish strings come from resources / the copy module** (`brand-design` owns the voice) — never hard-coded.
