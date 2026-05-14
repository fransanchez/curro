# US-014 Brief — SF-1.6: Five-tap-on-clock gesture to open the config menu

**Story**: As Fran (the caregiver), I can open the config menu by tapping the clock five times in
three seconds, so access is discoverable only to me and invisible to the main user.

**Spec section**: §9 (config menu access), §11 (launcher layout)

**PRD**: US-014 Phase 1

---

## Acceptance criteria

| # | Criterion |
|---|-----------|
| AC-1 | Tapping the clock five times within 3 seconds emits `LauncherSideEffect.OpenConfig` and navigates to the config menu. |
| AC-2 | Tapping fewer than five times within the window does NOT open the config menu. |
| AC-3 | Taps spread over more than 3 seconds do NOT open the config menu (counter resets). |
| AC-4 | The debug `TextButton` ("Ajustes (depuración)") is removed from the launcher home. |
| AC-5 | The `launcher_placeholder_open_config_debug` string resource is removed. |
| AC-6 | The `onOpenConfig` parameter remains on `LauncherPlaceholderContent` (used by `LauncherPlaceholderScreen` to route from `sideEffects`). |

---

## Implementation notes

The five-tap counter is implemented in `LauncherViewModel.onClockTapped()`:
- `clockTapTimes: MutableList<Long>` accumulates `System.currentTimeMillis()` on each tap.
- On each tap: drop entries older than `TAP_WINDOW_MS = 3000 ms`.
- If `clockTapTimes.size >= TAP_COUNT_THRESHOLD = 5`: clear the list, emit `OpenConfig` via the Channel.

`ClockBlock` already forwards taps to `LauncherViewModel` via `onClockTapped`.

The screen's `LaunchedEffect(Unit)` already maps `LauncherSideEffect.OpenConfig → onOpenConfig()`.

This SF's work is therefore **only** removing the debug affordance:
- Remove `TextButton` + wrapping `Spacer` from `LauncherPlaceholderContent`.
- Remove unused imports: `TextButton`, `Text` (unless used elsewhere), `MaterialTheme` (if only used for debug label style).
- Remove `launcher_placeholder_open_config_debug` from `strings.xml`.
- Remove `onOpenConfig` parameter from `LauncherPlaceholderContent` (it was only used by the debug `TextButton`).

---

## Files changed

| File | Action |
|------|--------|
| `presentation/launcher/LauncherPlaceholderScreen.kt` | MODIFY — remove debug TextButton + its Spacer + now-unused `onOpenConfig` param of content |
| `app/src/main/res/values/strings.xml` | MODIFY — remove `launcher_placeholder_open_config_debug` |
| `docs/PRD.md` | UPDATE — tick US-014 |

---

## Test plan

Tests already in `LauncherViewModelTest`:
- `ClockTappedTests.five taps within window → OpenConfig emitted`
- `ClockTappedTests.four taps within window → no OpenConfig`
- `ClockTappedTests.five taps spread over > 3 s → no OpenConfig`

No new test file needed for this SF.
