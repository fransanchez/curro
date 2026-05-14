# US-011 — SF-1.3 · Main mic button (inert)

> **Spec trace:** spec §11 (Curro launcher home — the mic button)
> **Master-plan:** SF-1.3
> **Phase:** 1 — Launcher base
> **Depends on:** US-010 (SF-1.2 — ClockBlock + ObserveClockUseCase)
> **Size:** S

---

## 1. Goal

Add the central mic button to the launcher home. In Phase 1 it is deliberately
**inert** — pressing it shows a Spanish toast explaining that voice is coming in
a later version. The full voice pipeline lands in Phase 2 (SF-2.x); this SF
exists to make the launcher *look* right and to wire the event plumbing
(`LauncherEvent` / `LauncherSideEffect` / Channel) that Phase 2 will extend.

---

## 2. Acceptance criteria

- [ ] `MicButton.kt` composable exists at
  `app/src/main/java/com/curro/app/presentation/launcher/MicButton.kt`
  with signature:
  ```kotlin
  @Composable
  fun MicButton(
      onPressed: () -> Unit,
      modifier: Modifier = Modifier,
      enabled: Boolean = true,
  )
  ```
- [ ] Button height ≥ 40 % of screen height via
  `Modifier.fillMaxHeight(Dimens.MIC_BUTTON_MIN_HEIGHT_FRACTION)` chained with
  `Modifier.fillMaxWidth()`
- [ ] `Icons.Filled.Mic` at `Dimens.LargeIconSize * 2` (96 dp) inside the
  button; label `stringResource(R.string.copy_home_mic_label)` at
  `MaterialTheme.typography.displaySmall`, colour `onPrimary` — neither icon
  nor label clips at `fontScale = 2.0`
- [ ] Background `MaterialTheme.colorScheme.primary`; shape
  `MaterialTheme.shapes.large`; elevation `Dimens.CardElevation`
- [ ] `HapticFeedbackType.LongPress` fires on press
- [ ] `LauncherEvent` sealed interface added to `LauncherViewModel.kt` with
  at minimum `data object MicPressed : LauncherEvent`
- [ ] `LauncherSideEffect` sealed interface added, Channel-backed:
  `private val _sideEffects = Channel<LauncherSideEffect>(Channel.BUFFERED)`;
  `val sideEffects: Flow<LauncherSideEffect> = _sideEffects.receiveAsFlow()`
- [ ] `ShowToast(messageResId: Int) : LauncherSideEffect` is the only entry
  in Phase 1
- [ ] `LauncherViewModel.onEvent(MicPressed)` emits
  `ShowToast(R.string.copy_mic_inert)` via the Channel
- [ ] `LauncherPlaceholderScreen` collects `sideEffects` via
  `LaunchedEffect(Unit) { viewModel.sideEffects.collect { effect -> … } }`
  and shows a `Toast` for `ShowToast`
- [ ] `MicButton` placed in `LauncherPlaceholderContent` BELOW `ClockBlock`
  (and the CTA when visible) — see layout order in §4
- [ ] 4 `@Preview` variants: light, dark, `fontScale = 1.5f`, `fontScale = 2.0f`
  each on a canvas tall enough to show the full button
- [ ] New string: `copy_mic_inert = "Aún no escucho — espera a la siguiente versión"`
  — **Phase-1-only dev string; NOT in the canonical COPY table; to be removed
  when STT lands in SF-2.x**
- [ ] Unit test in
  `app/src/test/java/com/curro/app/presentation/launcher/LauncherViewModelTest.kt`:
  `MicPressed event emits ShowToast side effect once`
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green

---

## 3. Scope — explicit non-deliveries

- No STT, no `SpeechRecognizer`, no audio recording — that is SF-2.x.
- No FSM state change — the FSM does not exist in Phase 1.
- No LLM call, no handler dispatch.
- No telemetry event.
- The `copy_mic_inert` string is a dev-only affordance; it will be removed
  (not repurposed) when SF-2.x lands the real listening flow.

---

## 4. Layout order in `LauncherPlaceholderContent` (post SF-1.3)

```
1. ClockBlock (SF-1.2)
2. Spacer(CurroSpacing.xxl)
3. BigPrimaryButton "Hazme tu pantalla de inicio" — only when !isCurroDefault
4. Spacer(CurroSpacing.l)           — only when !isCurroDefault
5. MicButton (SF-1.3)  ← NEW
6. Spacer(CurroSpacing.l)
7. TextButton "Ajustes (depuración)"  ← removed in SF-1.6
```

SF-1.4 inserts `AppTileGrid` between item 5 and the spacer after it.

---

## 5. Implementation notes

### 5.1 MicButton shape

`MicButton` is NOT a Material `Button` — the height fraction and the icon size
make a Material `Button` awkward. Implement as a `Surface` with `Modifier.clickable`:

```kotlin
val haptic = LocalHapticFeedback.current
Surface(
    modifier = modifier
        .fillMaxWidth()
        .fillMaxHeight(Dimens.MIC_BUTTON_MIN_HEIGHT_FRACTION)
        .clickable(enabled = enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onPressed()
        },
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.primary,
    shadowElevation = Dimens.CardElevation,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,          // label below provides the semantic; cd on button
            modifier = Modifier.size(Dimens.LargeIconSize * 2),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.height(CurroSpacing.s))
        Text(
            text = stringResource(R.string.copy_home_mic_label),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
    }
}
```

The outer `Surface`'s `Modifier.fillMaxHeight(fraction)` is the constraint that
enforces the 40 % spec. The `Column` inside centres content vertically, so at
`fontScale = 2.0` the text grows downward and the icon shrinks relatively, but
neither clips — `fillMaxHeight` adapts the row.

**Accessibility**: add a `semantics { contentDescription = … }` on the `Surface`'s
`Modifier` using the concatenation of the icon role and the label. Alternatively,
use `Modifier.semantics(mergeDescendants = true)` — the icon has `contentDescription
= null` so TalkBack reads the label only. Verify with TalkBack.

### 5.2 Channel pattern in ViewModel

```kotlin
private val _sideEffects = Channel<LauncherSideEffect>(Channel.BUFFERED)
val sideEffects: Flow<LauncherSideEffect> = _sideEffects.receiveAsFlow()

fun onEvent(event: LauncherEvent) {
    when (event) {
        is LauncherEvent.MicPressed -> viewModelScope.launch {
            _sideEffects.send(LauncherSideEffect.ShowToast(R.string.copy_mic_inert))
        }
        // SF-1.4 adds AppTileTapped; SF-1.6 adds ClockTapped
    }
}
```

The Channel is `BUFFERED` (capacity = 64) so a rapid double-press does not drop
the toast; `receiveAsFlow()` exposes a cold Flow that the screen subscribes to.

### 5.3 Screen side-effect collection

In `LauncherPlaceholderScreen`:

```kotlin
val context = LocalContext.current
LaunchedEffect(Unit) {
    viewModel.sideEffects.collect { effect ->
        when (effect) {
            is LauncherSideEffect.ShowToast ->
                Toast.makeText(context, effect.messageResId, Toast.LENGTH_SHORT).show()
            // SF-1.6 adds LaunchApp, OpenConfig
        }
    }
}
```

`LaunchedEffect(Unit)` runs once per composition lifetime — correct because the
Channel is long-lived. The toast is on the main thread (guaranteed by
`LaunchedEffect`'s dispatcher = `Main`).

---

## 6. Strings delta

| ID | Value | Notes |
|----|-------|-------|
| `copy_mic_inert` | `"Aún no escucho — espera a la siguiente versión"` | Phase-1 dev only; remove in SF-2.x |

`copy_home_mic_label` = `"CURRO"` already exists in `strings.xml` (US-005).

---

## 7. Test plan

**JVM unit tests (extend `LauncherViewModelTest.kt`):**

| # | Scenario | Assertion |
|---|----------|-----------|
| T1 | `onEvent(MicPressed)` called once | `sideEffects` emits exactly one `ShowToast(R.string.copy_mic_inert)` |
| T2 | `onEvent(MicPressed)` called twice | `sideEffects` emits two `ShowToast` in order (Channel is buffered) |

Use `turbine` `test { }` to consume the `sideEffects` Flow.

No new Compose UI test in Phase 1 for MicButton — Compose test infrastructure is
heavy; the Phase 1 bar is JVM unit tests.

---

## 8. Files changed

**New:**
- `app/src/main/java/com/curro/app/presentation/launcher/MicButton.kt`

**Modified:**
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt` — add `LauncherEvent`, `LauncherSideEffect`, `onEvent()`, Channel
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt` — add `MicButton`, wire `sideEffects` `LaunchedEffect`
- `app/src/main/res/values/strings.xml` — add `copy_mic_inert`
- `app/src/test/java/com/curro/app/presentation/launcher/LauncherViewModelTest.kt` — add T1, T2

**Not touched (locked):**
`Color.kt`, `Type.kt`, `Shape.kt`, `CurroSpacing.kt`, `Dimens.kt`,
`CurroTheme.kt`, `BigPrimaryButton.kt`, `BigCard.kt`, `BigYesNoRow.kt`,
`BigListRow.kt`, `DefaultLauncherDetector*.kt`, `MakeMeDefaultLauncher.kt`,
`ClockBlock.kt`, `ObserveClockUseCase.kt`.
