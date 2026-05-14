# US-010 — SF-1.2: Clock + date on launcher home

**Phase 1 · Sprint SF-1.2 · android-developer**

---

## User story

**As a** Curro developer,
**I want** the launcher home's placeholder text replaced with a real clock and date block
— a large time display (`HH:mm`) and a date line (`"EEEE d MMMM"` in Spanish, e.g.
*"miércoles 13 mayo"*) ticking live, centred at the top of the screen —
**so that** the first thing Fran's father sees when he presses HOME is the time and date
in enormous text, fulfilling the §11 launcher wireframe and spec §14 step 1 ("reloj
grande") without yet adding the mic button or app grid (SF-1.3 + SF-1.4).

---

## Spec traceability

- `docs/curro-spec-v1.0.md` §11 — launcher wireframe (clock top-of-screen, huge)
- `docs/curro-spec-v1.0.md` §3 — senior-first constraints (big text, `displayLarge` = 72 sp)
- `docs/curro-spec-v1.0.md` §9 — config menu opens on 5 taps on the clock (SF-1.6 —
  this SF stubs the callback, SF-1.6 wires the gesture counter)
- `docs/curro-spec-v1.0.md` §14 step 1 — "reloj grande" is item 1 of the launcher base

---

## Architecture decisions

### A1 — `ClockState` in `domain/model/`

A plain `data class ClockState(val timeText: String, val dateText: String)`.
Pure domain model; no Android imports. The ViewModel holds the formatted strings, not raw
`ZonedDateTime` — the composable never does formatting.

### A2 — `ObserveClockUseCase` in `domain/usecase/`

Emits a `Flow<ClockState>` that produces one element immediately, then one per second:

```kotlin
flow {
    while (true) {
        emit(buildClockState())
        delay(TICK_MS)
    }
}
.flowOn(defaultDispatcher)
```

`buildClockState()` reads `Clock.systemDefaultZone()` (not `System.currentTimeMillis()` —
testable). Time formatted with `DateTimeFormatter.ofPattern("HH:mm")`. Date formatted with
`DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("es", "ES"))` then `replaceFirstChar {
it.uppercase() }` for sentence-case capitalization (e.g. *"Miércoles 13 mayo"*).

Injected `@DefaultDispatcher` moves the ticker off `Main`. `WhileSubscribed` on the
ViewModel side stops it when the launcher is fully backgrounded (saves battery).

### A3 — `LauncherUiState` extended with `ClockState`

```kotlin
data class LauncherUiState(
    val isCurroDefault: Boolean,
    val clock: ClockState,
)
```

`LauncherViewModel` uses `combine(detector.flow, clock.flow)` to produce a single
`StateFlow<LauncherUiState>`. The initial value is `LauncherUiState(false, ClockState("--:--", ""))`.

### A4 — `ClockBlock` in `presentation/launcher/`

A stateless composable (time + date centred, no scaffold):

```kotlin
@Composable
fun ClockBlock(
    clockState: ClockState,
    onClockTapped: () -> Unit,    // stub for SF-1.6 five-tap gesture
    modifier: Modifier = Modifier,
)
```

- Time: `MaterialTheme.typography.displayLarge` (72 sp ExtraBold — the "clock" role per `Type.kt`)
- Date: `MaterialTheme.typography.headlineLarge` (32 sp Bold)
- Colors: `MaterialTheme.colorScheme.onBackground` for both
- `Modifier.clickable(onClick = onClockTapped, role = Role.Button)` on the entire block
  with `contentDescription = stringResource(R.string.cd_clock)`
- 4 previews: light/dark × CTA-visible/hidden (the `isCurroDefault` flag is on the screen level,
  not the `ClockBlock` itself — previews for `ClockBlock` are light/dark/large-font-1.5/large-font-2.0)

### A5 — `LauncherPlaceholderContent` updated

Replace the `Text(stringResource(R.string.launcher_placeholder_title))` with
`ClockBlock(clockState = uiState.clock, onClockTapped = onClockTapped)` at the top of the
`Column`. The debug `TextButton` (config open) stays below. The "Curro listo" string and its
string resource remain (they're still referenced in the brief comments and the strings.xml
comment says "Phase-0 only; replaced by SF-1.2" — update that comment's annotation).

The screen signature gains `onClockTapped: () -> Unit` (passed through from `CurroNavHost`
which passes `{}` for now — SF-1.6 will replace the lambda with the gesture counter).

### A6 — No new `@Suppress` / no locked files touched

`strings.xml` gains `cd_clock`. No other strings need to be added — time and date are
formatted dynamically in the use case, not from string resources (the format pattern is
code, not translatable copy).

### A7 — Tests

- `ObserveClockUseCaseTest` (JVM, Turbine): frozen `TestClock` via `Clock.fixed(...)`;
  asserts first emission has correct `timeText` and `dateText`; asserts second emission
  fires within 2 s (advanceTimeBy). Uses `UnconfinedTestDispatcher`.
- `LauncherViewModelTest` (extended from US-009): add test that `uiState.clock.timeText`
  is not blank on initial state and updates when `ObserveClockUseCase` emits a new value.
  A fake use-case returning `MutableSharedFlow<ClockState>` is the test double.

---

## Tasks

- [ ] **T1** — Add `ClockState` data class to `domain/model/ClockState.kt`
- [ ] **T2** — Add `ObserveClockUseCase` to `domain/usecase/ObserveClockUseCase.kt`
  (injected `@DefaultDispatcher`; `Flow<ClockState>` ticking every second)
- [ ] **T3** — Extend `LauncherUiState` with `val clock: ClockState`; update
  `LauncherViewModel` to `combine` detector + clock flows; update initial value
- [ ] **T4** — Add `ClockBlock` to `presentation/launcher/ClockBlock.kt`
  (4 previews: light / dark / 1.5× / 2.0×)
- [ ] **T5** — Update `LauncherPlaceholderContent` + screen signature
  (`onClockTapped: () -> Unit`); wire `ClockBlock`; update `CurroNavHost` to pass `{}`
- [ ] **T6** — Add `cd_clock` string resource to `strings.xml`
- [ ] **T7** — Add `ObserveClockUseCaseTest` (JVM + Turbine)
- [ ] **T8** — Update `LauncherViewModelTest` for the new `clock` field
- [ ] **T9** — `./gradlew ktlintFormat && ./gradlew ktlintCheck detekt && ./gradlew
  assembleDebug && ./gradlew testDebugUnitTest`

---

## Out of scope (explicit non-deliveries)

- Five-tap-on-clock gesture counter → SF-1.6
- `LauncherEvent` sealed interface → SF-1.6 (wires events when the mic button and gesture land)
- Mic button (≥40 % screen) → SF-1.3
- App tile grid + "Más apps" → SF-1.4 + SF-1.5
- `AssistantStateMachine` wiring → Phase 5
- Any change to locked files: `Color.kt`, `Type.kt`, `Shape.kt`, `CurroSpacing.kt`,
  `Dimens.kt`, `CurroTheme.kt`, `BigPrimaryButton.kt`, `BigCard.kt`, `BigYesNoRow.kt`,
  `BigListRow.kt`, all telemetry files, `ConfigMenuPlaceholderScreen.kt`
- Any change to `LauncherViewModel.SUBSCRIBE_TIMEOUT_MS` constant name or value

---

## Acceptance criteria

- [ ] HOME screen shows live-ticking `HH:mm` in `displayLarge` (72 sp ExtraBold) at top-center
- [ ] HOME screen shows `"EEEE d MMMM"` date in Spanish, sentence-case, in `headlineLarge`
- [ ] Clock updates every second (verified by waiting ~2 s on device / emulator)
- [ ] "Hazme tu pantalla de inicio" CTA still appears when `!isCurroDefault`
- [ ] `./gradlew assembleDebug` succeeds; `./gradlew testDebugUnitTest` green (≥ 54 tests)
- [ ] `./gradlew ktlintCheck detekt` clean
- [ ] No raw `.dp` / `.sp` / `Color(0xFF…)` literals introduced
- [ ] 4 `@Preview`s on `ClockBlock` (light, dark, 1.5×, 2.0×)
- [ ] `contentDescription` on the clock tap area (`cd_clock`)
