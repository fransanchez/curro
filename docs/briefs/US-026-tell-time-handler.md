# US-026 — SF-4.2 · `tell_time` handler

> **Spec trace:** spec §5 (catalog entry `tell_time`), spec §6 (flow-6 example
> "las doce y cuarenta y siete del miércoles trece de mayo").
> **Master-plan:** SF-4.2.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-025 (`FunctionHandler` + `HandlerModule`).
> **Size:** S.
> **Skills:** `function-catalog`, `brand-design`, `compose-patterns`, `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `tell_time` handler — speaks colloquial Castilian time/day/date |
| **US ID** | US-026 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-developer |

---

## 1. Summary

The first real Fase-1 handler. `tell_time` reads the `what` enum param
(`time | date | day | all`, default `all`) and produces a single spoken Spanish
phrase in colloquial Castilian — never a digital readout. Spec §6's
canonical example is "Son las doce y cuarenta y siete del miércoles trece de
mayo": this handler must produce exactly that shape for the `all` path on
the matching `LocalDateTime`.

Why this matters for *this* user: a digital "12:47" on a screen the user
struggles to read is no better than the stock clock. Spoken Spanish words
(`"Son las doce y cuarenta y siete"`) bypass the visual handicap entirely.
This is the zero-risk first handler that validates the whole Phase-4
architecture before any sensitive permission is touched.

---

## 2. Scope

**In scope:**

- `handler/TellTimeHandler.kt`.
- `handler/time/SpanishTimeFormatter.kt` (hour, minutes, day, date phrases).
- `handler/time/SpanishNumbers.kt` (0–59 int → words helper; the calculator's
  SF-4.4 file will subsume this — see Order of operations).
- `di/TimeModule.kt` — `@Provides Clock` (testable via `Clock.fixed(…)`).
- `HandlerModule.kt` — append the `@Binds @IntoMap @StringKey("tell_time")` line.
- New `strings.xml` entries: `copy_time_one`, `copy_time_day`,
  `copy_time_all`. Reuse existing `copy_time_now`, `copy_time_date`.
- ≥ 15 JVM tests in `TellTimeHandlerTest.kt` with `Clock.fixed`.
- ≥ 10 pure-Kotlin tests on the formatter helpers.

**Out of scope:**

- The "menos cuarto" / "menos diez" alternative phrasing — Phase 4 uses only
  the "y minutes" form (decision pinned).
- AM/PM disambiguation — 12-hour clock only; the user's context disambiguates.
- Years before 1000 or after 9999 — outside `LocalDateTime` typical range.
- Time zones other than the device default.

---

## 3. User Flows

### Flow 1: "qué hora es" — `what=time`

1. User presses mic → `listening`.
2. "qué hora es" → STT → `processing`.
3. FunctionGemma → `{action: "tell_time", params: {what: "time"}, confidence: 0.95}`.
4. Validator OK; dispatcher routes to `TellTimeHandler`.
5. Handler reads the clock, formats `"las doce y cuarenta y siete"`,
   wraps in `copy_time_now` → `"Son las doce y cuarenta y siete."`. Returns
   `Spoken(speech)`.
6. TTS speaks; state → `Idle`.

### Flow 2: "qué día es hoy" — `what=day`

1. As Flow 1, FunctionGemma emits `{action: "tell_time", params: {what: "day"}}`.
2. Handler → `"Hoy es el miércoles."` (via `copy_time_day`).

### Flow 3: "qué fecha es" — `what=date`

1. FunctionGemma emits `{params: {what: "date"}}`.
2. Handler → `"Hoy es el miércoles, trece de mayo de dos mil veintiséis."` (via
   existing `copy_time_date` template `"Hoy es %1$s, %2$s."`).

### Flow 4: default / no `what` — `what=all`

1. FunctionGemma emits `{action: "tell_time", params: {}, confidence: 0.95}`
   (or `{params: {what: "all"}}` — validator treats absent optional same as
   default).
2. Handler → `"Son las doce y cuarenta y siete del miércoles trece de mayo."`
   (via new `copy_time_all` template `"Son %1$s del %2$s %3$s."` with
   parts: time = "las doce y cuarenta y siete", day = "el miércoles", date =
   "trece de mayo de dos mil veintiséis"). For brevity the year is included
   only when `what="date"` or `what="all"`; if it becomes too long to feel
   right on real audio, the brief flags a future tweak (Phase 5/6 review).

### Flow 5: hour == 1

1. Handler at 01:00 → `"Es la una en punto."` via new `copy_time_one`.
2. Handler at 01:30 → `"Es la una y media."`.

---

## 4. Function-catalog Impact

**No catalog change** — `tell_time` already exists in
`domain/catalog/Fase1Catalog.kt` (US-021). This SF only implements its
handler.

Catalog snapshot for reference (verified against spec §5):

```yaml
nombre: tell_time
descripcion: "Dice en voz alta la hora actual, el día de la semana y/o la fecha."
params:
  - what: enum(time|date|day|all) / qué información dar / no (default: all)
needs_confirmation: NO
handler: TellTimeHandler
fase: 1
```

---

## 5. FSM States Touched

Provisional FSM (Phase 2/3) — `Processing → Speaking → Idle` via
`HandlerDispatcher`. No new states. **No always-escalate condition** (the
function is purely consultive — `needs_confirmation: NO`).

---

## 6. Android System Integrations & Permissions

| Integration / API | Why | Notes |
|---|---|---|
| `java.time.Clock` | Read the current `LocalDateTime`. | Injected via Hilt so tests can use `Clock.fixed(…)`. |
| Android `Context.getString(@StringRes, args)` | Format the spoken phrase. | The handler holds `@ApplicationContext` for this. |

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| _(none)_ | The handler has no system-permission dependency. | — | — |

No manifest changes.

---

## 7. On-device-model Impact

**No model impact.** Pure formatter — no Gemma 3n, no prompt-context change.
FunctionGemma already emits `tell_time` JSON from US-021's prompt; this SF
adds nothing to the prompt budget.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/
├── handler/
│   ├── TellTimeHandler.kt
│   └── time/
│       ├── SpanishTimeFormatter.kt
│       └── SpanishNumbers.kt          // 0–59 → words; SF-4.4 extends to 0..9_999_999
└── di/
    └── TimeModule.kt                  // @Provides Clock = Clock.systemDefaultZone()
```

### 8.2 `SpanishNumbers.kt` — 0–59 int → words

The handler needs minute-in-words for 0..59 and day-of-month-in-words for
1..31 (handled by the same function via the 0..59 table). SF-4.4 (calculate)
extends this to the 0..9_999_999 range; for SF-4.2 only the 0..59 surface is
needed. Decision pinned: **ship the 0..59 surface here, SF-4.4 extends**.

```kotlin
package com.curro.app.handler.time

internal object SpanishNumbers {
    private val units = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco",
        "seis", "siete", "ocho", "nueve",
    )
    private val teens = listOf(
        "diez", "once", "doce", "trece", "catorce", "quince",
        "dieciséis", "diecisiete", "dieciocho", "diecinueve",
    )
    private val twenties = listOf(
        "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro",
        "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve",
    )
    private val tens = listOf(
        "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa",
    )

    /** Caller's responsibility to keep [n] in [0, 99]. */
    fun toWords(n: Int): String {
        require(n in 0..99) { "SpanishNumbers.toWords expects 0..99, got $n" }
        return when {
            n < 10 -> units[n]
            n < 20 -> teens[n - 10]
            n < 30 -> twenties[n - 20]
            else -> {
                val tensWord = tens[(n / 10) - 3]
                val unit = n % 10
                if (unit == 0) tensWord else "$tensWord y ${units[unit]}"
            }
        }
    }
}
```

### 8.3 `SpanishTimeFormatter.kt`

```kotlin
package com.curro.app.handler.time

import java.time.LocalDateTime
import java.time.Month

internal object SpanishTimeFormatter {
    /**
     * Hour in 12-hour form. Returns "la una" for hour-of-day in {1, 13}, "las dos" …
     * "las doce" otherwise. Caller wraps this in `copy_time_one` (singular) or
     * `copy_time_now` (plural) depending on whether the hour is 1.
     */
    fun formatHourWord(hour24: Int): String {
        val h12 = ((hour24 + 11) % 12) + 1  // 0->12, 1->1, …, 12->12, 13->1, …, 23->11
        return if (h12 == 1) "la una" else "las ${SpanishNumbers.toWords(h12)}"
    }

    /** True iff the spoken hour is "la una" (singular). */
    fun isSingularHour(hour24: Int): Boolean {
        val h12 = ((hour24 + 11) % 12) + 1
        return h12 == 1
    }

    /**
     * Minutes phrase:
     *   0  → "en punto"
     *   15 → "y cuarto"
     *   30 → "y media"
     *   else → "y <minutes-in-words>"
     */
    fun formatMinutesPhrase(minute: Int): String =
        when (minute) {
            0 -> "en punto"
            15 -> "y cuarto"
            30 -> "y media"
            else -> "y ${SpanishNumbers.toWords(minute)}"
        }

    /** "la una en punto" / "las doce y cuarenta y siete" — caller wraps. */
    fun formatTimePhrase(hour: Int, minute: Int): String =
        "${formatHourWord(hour)} ${formatMinutesPhrase(minute)}"

    private val weekdays = listOf(
        "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo",
    )

    /** "el miércoles". */
    fun formatDayPhrase(now: LocalDateTime): String {
        // DayOfWeek.MONDAY.value == 1, … SUNDAY == 7.
        return "el ${weekdays[now.dayOfWeek.value - 1]}"
    }

    private val months = mapOf(
        Month.JANUARY to "enero", Month.FEBRUARY to "febrero", Month.MARCH to "marzo",
        Month.APRIL to "abril", Month.MAY to "mayo", Month.JUNE to "junio",
        Month.JULY to "julio", Month.AUGUST to "agosto", Month.SEPTEMBER to "septiembre",
        Month.OCTOBER to "octubre", Month.NOVEMBER to "noviembre", Month.DECEMBER to "diciembre",
    )

    /** "trece de mayo de dos mil veintiséis". */
    fun formatDatePhrase(now: LocalDateTime): String {
        val day = SpanishNumbers.toWords(now.dayOfMonth)        // 1..31 — fits in 0..99
        val month = months.getValue(now.month)
        val year = yearToWords(now.year)
        return "$day de $month de $year"
    }

    /**
     * Year-to-words. Pinned for Phase 4: handles 1000..9999 only — the range
     * `LocalDateTime` actually emits for this user.
     *
     *   1000..1999 → "mil <hundreds-tens-units>" (e.g. 1989 → "mil novecientos ochenta y nueve")
     *   2000..9999 → "<units> mil <hundreds-tens-units>"
     */
    fun yearToWords(year: Int): String {
        require(year in 1000..9999) { "yearToWords expects 1000..9999, got $year" }
        val thousands = year / 1000
        val remainder = year % 1000
        val thousandsPart = if (thousands == 1) "mil" else "${SpanishNumbers.toWords(thousands)} mil"
        if (remainder == 0) return thousandsPart
        return "$thousandsPart ${hundredsToWords(remainder)}"
    }

    private fun hundredsToWords(n: Int): String {
        require(n in 1..999)
        // Compact path for Phase 4 — handles 1..999 sufficient for year remainders.
        val hundreds = n / 100
        val tail = n % 100
        val hundredsWord = when (hundreds) {
            0 -> ""
            1 -> if (tail == 0) "cien" else "ciento"
            2 -> "doscientos"; 3 -> "trescientos"; 4 -> "cuatrocientos"
            5 -> "quinientos"; 6 -> "seiscientos"; 7 -> "setecientos"
            8 -> "ochocientos"; 9 -> "novecientos"
            else -> error("unreachable")
        }
        val tailWord = if (tail == 0) "" else SpanishNumbers.toWords(tail)
        return listOf(hundredsWord, tailWord).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
```

### 8.4 `TellTimeHandler.kt`

```kotlin
package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.FunctionCall
import com.curro.app.handler.time.SpanishTimeFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

class TellTimeHandler
    @Inject
    constructor(
        private val clock: Clock,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "tell_time"

        override suspend fun handle(call: FunctionCall): HandlerResult {
            val what = (call.params["what"] as? String) ?: "all"
            val now = LocalDateTime.now(clock)
            val timePhrase = SpanishTimeFormatter.formatTimePhrase(now.hour, now.minute)
            val dayPhrase = SpanishTimeFormatter.formatDayPhrase(now)
            val datePhrase = SpanishTimeFormatter.formatDatePhrase(now)
            val singular = SpanishTimeFormatter.isSingularHour(now.hour)

            val speech =
                when (what) {
                    "time" ->
                        context.getString(
                            if (singular) R.string.copy_time_one else R.string.copy_time_now,
                            timePhrase,
                        )
                    "day" -> context.getString(R.string.copy_time_day, dayPhrase)
                    "date" -> context.getString(R.string.copy_time_date, dayPhrase, datePhrase)
                    "all" ->
                        context.getString(
                            R.string.copy_time_all,
                            timePhrase,
                            dayPhrase,
                            datePhrase,
                        )
                    else ->
                        // Defensive — the validator rejects out-of-enum values; fall through to "all".
                        context.getString(R.string.copy_time_all, timePhrase, dayPhrase, datePhrase)
                }
            return HandlerResult.Spoken(speech)
        }
    }
```

### 8.5 `TimeModule.kt`

```kotlin
package com.curro.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
```

### 8.6 `HandlerModule.kt` — append

```kotlin
@Binds
@IntoMap
@StringKey("tell_time")
abstract fun bindTellTimeHandler(impl: TellTimeHandler): FunctionHandler
```

### 8.7 `strings.xml` — adds

```xml
<!-- US-026 (SF-4.2) — tell_time handler, hour-singular form ("Es la una en punto"). -->
<string name="copy_time_one">Es %1$s.</string>

<!-- US-026 (SF-4.2) — tell_time handler, day-only form. -->
<string name="copy_time_day">Hoy es %1$s.</string>

<!-- US-026 (SF-4.2) — tell_time handler, all-in-one phrasing for default what=all. -->
<string name="copy_time_all">Son %1$s del %2$s %3$s.</string>
```

Reused without change: `copy_time_now`, `copy_time_date`.

### 8.8 Composables / overlays / VM

None — pure handler. The dispatched speech flows through the existing
`ListeningOverlay`'s Speaking state (US-018).

---

## 9. Acceptance Criteria

- [ ] `handler/TellTimeHandler.kt`, `handler/time/SpanishTimeFormatter.kt`,
      `handler/time/SpanishNumbers.kt`, `di/TimeModule.kt` exist at the
      documented paths.
- [ ] `HandlerModule` gains the `@Binds @IntoMap @StringKey("tell_time")`
      line for `TellTimeHandler`.
- [ ] `strings.xml` gains `copy_time_one`, `copy_time_day`, `copy_time_all`.
      `copy_time_now` and `copy_time_date` are unchanged and reused.
- [ ] `dispatcher.dispatch(FunctionCall("tell_time", emptyMap(), 0.9))` →
      returns `Spoken(speech)` where `speech` ends with a `.` and contains
      no digit characters.
- [ ] At a fixed clock of `2026-05-13T12:47:00`:
  - `{what: "time"}` → `"Son las doce y cuarenta y siete."`
  - `{what: "day"}` → `"Hoy es el miércoles."`
  - `{what: "date"}` → `"Hoy es el miércoles, trece de mayo de dos mil veintiséis."`
  - `{what: "all"}` → `"Son las doce y cuarenta y siete del miércoles trece de mayo de dos mil veintiséis."`
  - `{}` (params absent) → same as `{what: "all"}`.
- [ ] At `2026-05-13T01:00:00`:
  - `{what: "time"}` → `"Es la una en punto."` (singular hour, `"en punto"`
    for minute zero).
- [ ] At `2026-05-13T03:15:00`: `"Son las tres y cuarto."`
- [ ] At `2026-05-13T05:30:00`: `"Son las cinco y media."`
- [ ] At `2026-05-13T00:00:00`: `"Son las doce en punto."` (midnight reads
      as twelve).
- [ ] At `2026-12-31T23:59:00`: `"Son las once y cincuenta y nueve."`
- [ ] Sanity-test each weekday (Mon..Sun) maps to the right Spanish word.
- [ ] Sanity-test each month (Jan..Dec) maps to the right Spanish word.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green.
- [ ] On the Redmi 15: press → "qué hora es" → Curro speaks the right time
      in Spanish. (Real-clock verification — value not pinned.)
- [ ] **No new permissions; no manifest changes; no new dependency.**

---

## 10. Senior-UX & Copy

| String ID | Spanish | Provenance |
|---|---|---|
| `copy_time_now` (existing) | "Son las %1$s." | Reused (US-005). |
| `copy_time_one` (NEW) | "Es %1$s." | Singular-hour form. |
| `copy_time_day` (NEW) | "Hoy es %1$s." | Day-only path. |
| `copy_time_date` (existing) | "Hoy es %1$s, %2$s." | Reused. |
| `copy_time_all` (NEW) | "Son %1$s del %2$s %3$s." | Spec §6 flow-6 form. |

Voice: Curro speaks the same phrase Spaniards say — `"Son las doce"`, not
`"Las 12:00"`. No apologies. The trailing `.` lets the TTS engine pace the
phrase naturally.

Every Curro→user message is **spoken AND shown** — preserved by the
`Speaking` overlay (US-018) reading the same string the handler returns.

---

## 11. Design Notes

No new UI. The existing overlay surface absorbs the speech.

---

## 12. Performance Considerations

- The handler runs on `viewModelScope`'s main dispatcher (via
  `HandlerDispatcher`). Clock reads, string lookups, and the formatter are
  all sub-millisecond.
- `SpanishTimeFormatter` is a pure `object` — no allocations beyond the
  returned `String`.
- `SpanishNumbers.toWords` is a `when`-chain over a `List` — `O(1)` per
  call.

---

## 13. Testing Requirements

**`SpanishNumbersTest.kt`** — pure JVM:

- Every integer in 0..99 round-trips through the `toWords` table (table-driven
  test, 100 cases).
- Out-of-range inputs throw `IllegalArgumentException` (verified for -1 and
  100).

**`SpanishTimeFormatterTest.kt`** — pure JVM:

- 12-hour mapping: 0→"las doce", 1→"la una", 11→"las once", 12→"las doce",
  13→"la una", 23→"las once".
- Minute phrase: 0→"en punto", 15→"y cuarto", 30→"y media", 1→"y uno",
  47→"y cuarenta y siete", 59→"y cincuenta y nueve".
- `formatDayPhrase` for each weekday (Mon..Sun).
- `formatDatePhrase` for `2026-05-13` → `"trece de mayo de dos mil veintiséis"`.
- `yearToWords` table: 1000→"mil", 1001→"mil uno", 1989→"mil novecientos
  ochenta y nueve", 2000→"dos mil", 2026→"dos mil veintiséis",
  2100→"dos mil cien", 2150→"dos mil ciento cincuenta", 9999→"nueve mil
  novecientos noventa y nueve".

**`TellTimeHandlerTest.kt`** — Robolectric (for `Context.getString`):

- Test base: `clock = Clock.fixed(Instant.parse("2026-05-13T12:47:00Z"),
  ZoneId.of("UTC"))`. Note: tests pin the zone so that `LocalDateTime.now(clock)`
  is deterministic.
- 15+ cases covering the AC bullets above. Each case asserts the exact
  Spanish output character-for-character.
- A "what is null" case: `handle(FunctionCall("tell_time", emptyMap(), 0.9))`
  → defaults to "all".
- A "what is unknown" case: `handle(FunctionCall("tell_time", mapOf("what"
  to "yesterday"), 0.9))` → falls through to "all" (defensive — though the
  validator already rejects out-of-enum values).

**On-device verification**:

- Install the debug APK on the Redmi 15.
- Press the mic, "qué hora es" → Curro speaks the right time.
- Repeat for "qué día es hoy", "qué fecha es", and just "qué".
- Verify no new permission prompts fire.

---

## 14. Implementation Notes — Order of Operations

1. Create `handler/time/SpanishNumbers.kt` (the 0..99 table).
2. Create `handler/time/SpanishTimeFormatter.kt` (uses `SpanishNumbers`).
3. Add the 3 new `strings.xml` entries (`copy_time_one`, `copy_time_day`,
   `copy_time_all`).
4. Create `di/TimeModule.kt`.
5. Create `handler/TellTimeHandler.kt`.
6. Append the `@Binds @IntoMap @StringKey("tell_time")` line to
   `HandlerModule.kt`.
7. Write the pure-Kotlin tests for `SpanishNumbers` and
   `SpanishTimeFormatter`.
8. Write the Robolectric tests for `TellTimeHandler`.
9. Run `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
10. Install on the Redmi 15; manual smoke test "qué hora es".
11. Commit as `feat: add tell_time handler (US-026 / SF-4.2)`.

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
