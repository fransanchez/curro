# US-028 — SF-4.4 · `calculate` handler + Spanish-number expression parser

> **Spec trace:** spec §5 (catalog entry `calculate`, four canonical examples
> verbatim).
> **Master-plan:** SF-4.4.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-025 (`FunctionHandler` + `HandlerModule`), US-026 (`SpanishNumbers` 0..99 table).
> **Size:** M.
> **Skills:** `function-catalog`, `brand-design`, `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `calculate` handler — Spanish-language arithmetic |
| **US ID** | US-028 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-developer |

---

## 1. Summary

Parse a Spanish natural-language arithmetic expression (the user *speaks*
it, FunctionGemma passes the transcribed string verbatim as `expression`) and
read the result back in Spanish words: `"cuánto es cuarenta y siete por
ocho"` → `"Cuarenta y siete por ocho son trescientos setenta y seis."` All
four spec §5 canonical examples are the AC. The handler is **deterministic
and rules-based** — no LLM in the calculation path, by design (master-plan
SF-4.4: "deterministic, testable, no extra latency, bounded surface").

Why this matters for *this* user: the stock calculator demands tapping
small buttons in a precise order — exactly what the user can't do reliably.
A spoken `"cuánto es cuarenta y siete por ocho"` skips the manipulative
load entirely.

---

## 2. Scope

**In scope:**

- `handler/CalculateHandler.kt`.
- `handler/calculator/SpanishExpressionParser.kt` — tokenizer + evaluator.
- `handler/calculator/SpanishNumbers.kt` — **extend** the 0..99 table from
  SF-4.2 with the 100..999, 1000..9_999_999 ranges, plus `intToSpanishWords`.
  **Decision pinned**: the file lives at `handler/calculator/SpanishNumbers.kt`
  (moved from `handler/time/`); SF-4.2's import is updated in the same dev
  pass. Alternative pinned-rejected: duplicate code at two paths — rejected.
- `HandlerModule.kt` — append the `@Binds @IntoMap @StringKey("calculate")`
  line.
- New `CurroError` variant: `Calculation(expression: String, reason: String)`.
- New `strings.xml` entry: `copy_calc_div_zero`. Reuse `copy_calc_result`,
  `copy_calc_failed`.
- ≥ 30 JVM tests on the handler; ≥ 20 on `SpanishNumbers` extensions; ≥ 15
  on the parser.

**Out of scope:**

- Operator precedence — flat left-to-right (decision pinned: spec examples
  all match; adding precedence inflates bug surface for zero user benefit).
- Decimals, fractions, negatives, parentheses.
- Numbers ≥ 10 million.
- "Millones" / "billones" — pin "fuera de scope" verbatim in the parse-error
  copy if encountered.
- Trigonometry, square roots, log, etc.

---

## 3. User Flows

### Flow 1: "cuánto es cuarenta y siete por ocho" — multiplication

1. User → STT → `expression = "cuarenta y siete por ocho"` (FunctionGemma
   strips the "cuánto es" lead-in; if it doesn't, the parser strips it too).
2. Parser tokenizes: `[NUM(47), OP(*), NUM(8)]`.
3. Evaluator: `47 * 8 = 376`.
4. Output: `"Cuarenta y siete por ocho son trescientos setenta y seis."`
   via `copy_calc_result` template.
5. State → `Idle`.

### Flow 2: "calcula mil dividido entre veinticinco" — division

1. `expression = "mil dividido entre veinticinco"`.
2. Tokenize → `[NUM(1000), OP(/), NUM(25)]`.
3. Evaluate → 40. Output: `"Mil dividido entre veinticinco son cuarenta."`

### Flow 3: "el veintiuno por ciento de doscientos" — percent special form

1. `expression = "el veintiuno por ciento de doscientos"`.
2. Tokenize matches the **percent regex** `el <num> por ciento de <num>` →
   `[PERCENT(21, 200)]`.
3. Evaluate → 42. Output: `"El veintiuno por ciento de doscientos son cuarenta y dos."`

### Flow 4: "cuánto suma quince y veintitrés" — sum

1. `expression = "quince y veintitrés"` (the "cuánto suma" lead-in is
   stripped; the leading `"y"` between operands is the operator).
2. Tokenize → `[NUM(15), OP(+), NUM(23)]`.
3. Evaluate → 38. Output: `"Quince y veintitrés son treinta y ocho."`

### Flow 5: Division by zero

1. `expression = "cinco entre cero"` → `[NUM(5), OP(/), NUM(0)]`.
2. Evaluate → `Result.failure(div_zero)`.
3. Handler → `Failed(copy_calc_div_zero, Calculation("cinco entre cero", "div_zero"))`.
4. Curro speaks `"No puedo dividir entre cero."`

### Flow 6: Parse error

1. `expression = "cuántos billones tiene Pepito"`.
2. Tokenizer fails to map "billones" → `Result.failure(parse)`.
3. Handler → `Failed(copy_calc_failed, Calculation(expression, "parse"))`.
4. Curro speaks `"No he podido hacer ese cálculo. ¿Lo repites más despacio?"`.

---

## 4. Function-catalog Impact

**No catalog change** — `calculate` already exists.

---

## 5. FSM States Touched

Provisional FSM — `Processing → Speaking → Idle`. **`needs_confirmation: NO`**.

---

## 6. Android System Integrations & Permissions

| Integration / API | Why |
|---|---|
| `Context.getString(@StringRes, args)` | Build the spoken phrase. |

**No new permissions.** No manifest changes.

---

## 7. On-device-model Impact

**No model impact.** The parser is hand-written; FunctionGemma already emits
`calculate` JSON.

---

## 8. Android Specification

### 8.1 Files added / changed

```
app/src/main/java/com/curro/app/
├── handler/
│   ├── CalculateHandler.kt
│   └── calculator/
│       ├── SpanishExpressionParser.kt
│       └── SpanishNumbers.kt          // extended; replaces handler/time/SpanishNumbers.kt
└── domain/model/CurroError.kt         // + Calculation variant
```

**Migration**: SF-4.2's `handler/time/SpanishNumbers.kt` is moved to
`handler/calculator/SpanishNumbers.kt` in this SF and the import in
`SpanishTimeFormatter.kt` is updated. **Decision pinned**: keep ONE file —
move, don't duplicate.

### 8.2 `SpanishNumbers.kt` — full table

The extension adds: 100..999, 1000..9_999_999, and the inverse
`intToSpanishWords(n)`.

```kotlin
package com.curro.app.handler.calculator

internal object SpanishNumbers {
    private val units = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco",
        "seis", "siete", "ocho", "nueve",
    )
    private val teens = listOf(
        "diez", "once", "doce", "trece", "catorce", "quince",
        "dieciséis", "diecisiete", "dieciocho", "diecinueve",
    )
    // …
    private val twenties = listOf( /* veinte..veintinueve */ )
    private val tens = listOf( /* treinta..noventa */ )
    private val hundreds = mapOf(
        100 to "cien" /* alone only */,
        // 200, 300, … 900
    )

    fun toWords99(n: Int): String { /* 0..99 — moved from SF-4.2 */ }

    fun toWords999(n: Int): String {
        require(n in 0..999)
        // Returns "cien" for 100, "ciento <rest>" for 101..199, "doscientos…" for 200+.
    }

    /** Final word form for 0..9_999_999. */
    fun toWords(n: Int): String {
        require(n in 0..9_999_999)
        // Splits into millions / thousands / hundreds parts; composes.
    }
}

/** Public top-level helper for the handler's output. */
internal fun intToSpanishWords(n: Long): String = SpanishNumbers.toWords(n.toInt())
```

**Pinned int → words for the spec examples** (the dev verifies each):

- `intToSpanishWords(376) == "trescientos setenta y seis"`.
- `intToSpanishWords(40) == "cuarenta"`.
- `intToSpanishWords(42) == "cuarenta y dos"`.
- `intToSpanishWords(38) == "treinta y ocho"`.

### 8.3 `SpanishExpressionParser.kt`

```kotlin
package com.curro.app.handler.calculator

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpanishExpressionParser
    @Inject
    constructor() {
        sealed interface Token {
            data class Num(val value: Long) : Token
            data class Op(val op: Operator) : Token
            data class Percent(val pct: Long, val of: Long) : Token
        }

        enum class Operator { PLUS, MINUS, TIMES, DIVIDE }

        data class Parsed(val tokens: List<Token>) {
            fun evaluate(): Result<Long> {
                if (tokens.isEmpty()) return Result.failure(IllegalStateException("empty"))
                if (tokens.size == 1) {
                    val t = tokens.first()
                    return when (t) {
                        is Token.Percent -> Result.success(t.pct * t.of / 100)
                        is Token.Num -> Result.failure(IllegalStateException("single_num"))
                        is Token.Op -> Result.failure(IllegalStateException("single_op"))
                    }
                }
                // Flat left-to-right
                var acc: Long? = null
                var pendingOp: Operator? = null
                for (t in tokens) {
                    when (t) {
                        is Token.Num -> {
                            if (acc == null) {
                                acc = t.value
                            } else {
                                val op = pendingOp ?: return Result.failure(IllegalStateException("no_op"))
                                acc =
                                    when (op) {
                                        Operator.PLUS -> acc + t.value
                                        Operator.MINUS -> acc - t.value
                                        Operator.TIMES -> acc * t.value
                                        Operator.DIVIDE -> {
                                            if (t.value == 0L) return Result.failure(ArithmeticException("div_zero"))
                                            acc / t.value
                                        }
                                    }
                                pendingOp = null
                                if (acc > 9_999_999L || acc < 0L) return Result.failure(IllegalStateException("overflow"))
                            }
                        }
                        is Token.Op -> {
                            if (acc == null || pendingOp != null) return Result.failure(IllegalStateException("op_without_num"))
                            pendingOp = t.op
                        }
                        is Token.Percent -> return Result.failure(IllegalStateException("percent_only_alone"))
                    }
                }
                return acc?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("no_result"))
            }
        }

        /** Returns `Parsed` on success, `Result.failure` for unparseable / out-of-scope. */
        fun parse(input: String): Result<Parsed> {
            val cleaned = input.trim().lowercase(java.util.Locale("es"))
                .replace(",", "").replace(".", "")
                .let { stripLeadIn(it) }

            // Percent special form first.
            val percentMatch = PERCENT_RE.matchEntire(cleaned)
            if (percentMatch != null) {
                val pct = wordsToInt(percentMatch.groupValues[1])
                    ?: return Result.failure(IllegalStateException("parse"))
                val of = wordsToInt(percentMatch.groupValues[2])
                    ?: return Result.failure(IllegalStateException("parse"))
                return Result.success(Parsed(listOf(Token.Percent(pct, of))))
            }

            // General tokenization.
            val parts = cleaned.split(Regex("\\s+"))
            val tokens = mutableListOf<Token>()
            var i = 0
            while (i < parts.size) {
                // Try operator first.
                val opMatch = matchOperator(parts, i)
                if (opMatch != null) {
                    tokens.add(Token.Op(opMatch.first))
                    i += opMatch.second
                    continue
                }
                // Else accumulate number-words until next operator.
                val numWords = mutableListOf<String>()
                while (i < parts.size && matchOperator(parts, i) == null) {
                    numWords.add(parts[i])
                    i++
                }
                if (numWords.isEmpty()) return Result.failure(IllegalStateException("parse"))
                val v = wordsToInt(numWords.joinToString(" "))
                    ?: return Result.failure(IllegalStateException("parse"))
                tokens.add(Token.Num(v))
            }
            return Result.success(Parsed(tokens))
        }

        // Strip "cuánto es", "cuanto es", "calcula", "cuánto suma" etc.
        private fun stripLeadIn(s: String): String =
            s.replace(Regex("^(cuánto es|cuanto es|cuánto suma|cuanto suma|calcula|calcular|dime)\\s+"), "")

        // Operator dictionary — pinned exact phrases (multi-word matches checked first).
        private val OPERATOR_PHRASES: List<Pair<List<String>, Operator>> = listOf(
            listOf("multiplicado", "por") to Operator.TIMES,
            listOf("dividido", "entre") to Operator.DIVIDE,
            listOf("dividido", "por") to Operator.DIVIDE,
            listOf("sumado", "a") to Operator.PLUS,
            listOf("por") to Operator.TIMES,
            listOf("x") to Operator.TIMES,
            listOf("entre") to Operator.DIVIDE,
            listOf("más") to Operator.PLUS,
            listOf("mas") to Operator.PLUS,
            listOf("y") to Operator.PLUS,             // careful: "cuarenta y siete" treats "y" inside number-words; the
                                                     // number-tokenizer wordsToInt consumes "y" inside its scope before
                                                     // the operator scanner sees it. Pin in test.
            listOf("menos") to Operator.MINUS,
            listOf("resta") to Operator.MINUS,
        )

        private fun matchOperator(parts: List<String>, i: Int): Pair<Operator, Int>? {
            for ((phrase, op) in OPERATOR_PHRASES) {
                if (i + phrase.size > parts.size) continue
                if (parts.subList(i, i + phrase.size) == phrase) return op to phrase.size
            }
            return null
        }

        private companion object {
            val PERCENT_RE = Regex(
                "^el ([\\w\\s]+?) por ciento de ([\\w\\s]+?)$",
            )
        }

        /** Reverse of `intToSpanishWords` — limited to inputs the parser accepts. */
        internal fun wordsToInt(text: String): Long? {
            // Implementation: split into million-thousand-hundred parts by anchor words
            // "mil"/"millones"/"millón"; reject "millones" (out-of-scope). Within each
            // segment, parse 0..999 by stripping "y" as the in-number separator,
            // mapping units/teens/twenties/tens/hundreds via SpanishNumbers'
            // inverse tables. See dev notes; the dev writes this alongside the
            // SpanishNumbers extension.
            TODO("implemented per algorithm above")
        }
    }
```

> The dev pass implements `wordsToInt` by composing the same units/teens/
> twenties/tens/hundreds tables in reverse. Decision pinned: **reject
> "millón"/"millones"/"billón" verbatim** — return `null` → `Calculation
> ("parse")`. The brief expects 30+ tests against the parser to pin every
> edge case before declaring it done.

### 8.4 `CalculateHandler.kt`

```kotlin
package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.handler.calculator.SpanishExpressionParser
import com.curro.app.handler.calculator.intToSpanishWords
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CalculateHandler
    @Inject
    constructor(
        private val parser: SpanishExpressionParser,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "calculate"

        @Suppress("ReturnCount")
        override suspend fun handle(call: FunctionCall): HandlerResult {
            val expression = (call.params["expression"] as? String).orEmpty().trim()
            if (expression.isEmpty()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_calc_failed),
                    CurroError.Calculation(expression, "empty"),
                )
            }
            val parsed =
                parser.parse(expression)
                    .getOrElse {
                        return HandlerResult.Failed(
                            context.getString(R.string.copy_calc_failed),
                            CurroError.Calculation(expression, "parse"),
                        )
                    }
            val value =
                parsed.evaluate()
                    .getOrElse { e ->
                        return when {
                            e.message == "div_zero" ->
                                HandlerResult.Failed(
                                    context.getString(R.string.copy_calc_div_zero),
                                    CurroError.Calculation(expression, "div_zero"),
                                )
                            e.message == "overflow" ->
                                HandlerResult.Failed(
                                    context.getString(R.string.copy_calc_failed),
                                    CurroError.Calculation(expression, "overflow"),
                                )
                            else ->
                                HandlerResult.Failed(
                                    context.getString(R.string.copy_calc_failed),
                                    CurroError.Calculation(expression, e.message ?: "parse"),
                                )
                        }
                    }
            // Format: lowercase first letter is wrong — capitalise the first word of `expression`.
            val phrase = expression.replaceFirstChar { it.titlecase(java.util.Locale("es")) }
            val resultWords = intToSpanishWords(value)
            val speech = context.getString(R.string.copy_calc_result, phrase, resultWords)
            return HandlerResult.Spoken(speech)
        }
    }
```

**Decision pinned**: the spoken output capitalises the first character of
the user's expression (`"Cuarenta y siete por ocho son trescientos setenta
y seis."`) — even if FunctionGemma passes it lowercased. This satisfies the
spec §5 examples character-for-character.

### 8.5 `CurroError` addition

```kotlin
// ── Calculate handler (US-028 / SF-4.4) ───────────────────────────────────

/**
 * Calculation failure. [reason] is one of:
 *   "empty"     — empty expression param.
 *   "parse"     — tokenizer / words-to-int couldn't resolve. Out-of-scope inputs
 *                 like "billones" hit this path.
 *   "div_zero"  — division by zero.
 *   "overflow"  — result > 9_999_999 or < 0.
 */
data class Calculation(val expression: String, val reason: String) : CurroError()
```

### 8.6 `strings.xml` — add / reuse

Reuse:

- `copy_calc_result` (`"%1$s son %2$s."`).
- `copy_calc_failed` (`"No he podido hacer ese cálculo. ¿Lo repites más despacio?"`).

New:

```xml
<!-- US-028 (SF-4.4) — division by zero. Curro voice: blunt, honest, no apology. -->
<string name="copy_calc_div_zero">No puedo dividir entre cero.</string>
```

### 8.7 `HandlerModule.kt` — append

```kotlin
@Binds
@IntoMap
@StringKey("calculate")
abstract fun bindCalculateHandler(impl: CalculateHandler): FunctionHandler
```

---

## 9. Acceptance Criteria

- [ ] All four spec §5 canonical examples produce the exact Spanish output:
  - `"cuarenta y siete por ocho"` → `"Cuarenta y siete por ocho son trescientos setenta y seis."`
  - `"mil dividido entre veinticinco"` → `"Mil dividido entre veinticinco son cuarenta."`
  - `"quince y veintitrés"` → `"Quince y veintitrés son treinta y ocho."`
  - `"el veintiuno por ciento de doscientos"` → `"El veintiuno por ciento de doscientos son cuarenta y dos."`
- [ ] Division by zero (`"cinco entre cero"`) → `"No puedo dividir entre cero."`.
- [ ] Out-of-scope inputs (`"un billón por dos"`) → `"No he podido hacer ese cálculo. ¿Lo repites más despacio?"`.
- [ ] Overflow (`"mil por mil por mil"` = 10⁹) → parse failure or overflow → `copy_calc_failed`.
- [ ] Empty `expression` param → `copy_calc_failed`.
- [ ] `intToSpanishWords` round-trips for representative ints in
      `[0, 9_999_999]` (≥ 20 cases).
- [ ] `SpanishExpressionParser.parse` produces the right token list for ≥ 15
      pinned inputs.
- [ ] `HandlerModule` gains the `@Binds @IntoMap @StringKey("calculate")` line.
- [ ] `CurroError.Calculation(expression, reason)` added.
- [ ] `strings.xml` gains `copy_calc_div_zero`.
- [ ] `handler/time/SpanishNumbers.kt` no longer exists — moved to
      `handler/calculator/SpanishNumbers.kt`; `SpanishTimeFormatter` imports the
      new path.
- [ ] No new permissions; no manifest changes; no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green; **30+ calculator handler tests pass**.

---

## 10. Senior-UX & Copy

| String ID | Spanish | Provenance |
|---|---|---|
| `copy_calc_result` (existing) | "%1$s son %2$s." | Reused — composes `<expression>` + `<words>`. |
| `copy_calc_failed` (existing) | "No he podido hacer ese cálculo. ¿Lo repites más despacio?" | Reused — generic parse / overflow / out-of-scope path. |
| `copy_calc_div_zero` (NEW) | "No puedo dividir entre cero." | Specific path — Curro voice: direct, no padding. |

Voice: results are spoken as a single declarative sentence, "X por Y son Z."
— not "X multiplicado por Y es igual a Z." The user thinks in colloquial,
not maths-class. Failure is short and offers the speak-slower alternative.

---

## 11. Design Notes

No visual surface.

---

## 12. Performance Considerations

- Parsing 99% of inputs is < 1 ms (~10 tokens, table lookups).
- `intToSpanishWords` for ints up to 9_999_999 traverses at most 4 segments
  (millions, thousands, hundreds, units) — sub-millisecond.
- Allocation: one `Parsed` data class + a few token strings — negligible.

---

## 13. Testing Requirements

**`SpanishNumbersTest.kt`** (extended) — pure JVM:

- Every integer in 0..99 (already covered by SF-4.2; verify after the move).
- 100..199: `100 → "cien"`, `101 → "ciento uno"`, `115 → "ciento quince"`,
  `135 → "ciento treinta y cinco"`, `199 → "ciento noventa y nueve"`.
- 200..900 (one per 100): `200 → "doscientos"`, `300 → "trescientos"`,
  `400 → "cuatrocientos"`, `500 → "quinientos"`, `600 → "seiscientos"`,
  `700 → "setecientos"`, `800 → "ochocientos"`, `900 → "novecientos"`.
- Compound hundreds: `376 → "trescientos setenta y seis"`,
  `999 → "novecientos noventa y nueve"`.
- 1000..9999: `1000 → "mil"`, `1001 → "mil uno"`, `1500 → "mil quinientos"`,
  `2000 → "dos mil"`, `2026 → "dos mil veintiséis"`,
  `9999 → "nueve mil novecientos noventa y nueve"`.
- ≥ 10_000: representative cases — `10_000 → "diez mil"`, `25_000 → "veinticinco mil"`,
  `1_000_000 → "un millón"`, `9_999_999 → ...`. **Decision pinned**: million-range
  formatting required for `intToSpanishWords` to be honest (the parser
  rejects "millón" input, but the OUTPUT might land there from a multiplication
  result like `2500 × 4 = 10000`); the spec examples cap at 376 so this is
  a forward-looking guarantee.

**`SpanishExpressionParserTest.kt`** — pure JVM (≥ 15 cases):

- The four canonical examples — tokenization assertion.
- `"cinco entre cero"` → tokens `[NUM(5), OP(/), NUM(0)]`.
- `"mil por mil"` → tokens; evaluator → overflow.
- `"diez menos tres"` → 7.
- `"cinco y tres"` → 8 (NOTE: "y" between numbers triggers the operator
  scanner only when the number-word reader has already consumed the prior
  number's "y …" tail; pin via a small example: `"cuarenta y siete y tres"`
  → 50 — verify the parser handles this by scanning `cuarenta y siete`
  greedily first, then encountering `y` as the operator).
- `"un billón por dos"` → parse failure.
- `"calcula mil dividido entre veinticinco"` (with lead-in) → 40.
- Empty string → parse failure.
- Single number `"cuarenta y siete"` → eval failure (single_num).
- Pure operator `"por"` → eval failure (op_without_num).
- Accent-strip: `"el veintiun por ciento de doscientos"` (no accent on
  veintiún) — pin: the lead-in stripper handles accent variants; if not,
  parse failure (decision pinned: parse failure — the user heard the right
  word from STT 95% of the time).
- `"5 por 8"` — digit-form is NOT in the table; the parser rejects (parse
  failure). The user speaks words, not digits.

**`CalculateHandlerTest.kt`** — Robolectric (≥ 30 cases):

- All four spec examples — char-exact assertion of the output string.
- Division by zero.
- Parse error — generic failed line + `Calculation(reason="parse")`.
- Overflow path.
- Empty expression.
- Subtraction.
- Mixed ops (left-to-right, no precedence): `"diez más dos por tres"` → 36
  (not 16). Pin in test — this IS the left-to-right contract; if the user
  ever complains, Phase 5+ adds precedence.
- A 5-digit result that crosses the "thousands" boundary — verify
  formatting: `"cien por cien"` → `"diez mil"`.
- Accent-bearing operator: `"diez más cinco"` (with accent on más) → 15.
- Non-accent operator: `"diez mas cinco"` → 15 (both accepted).

**On-device verification** on the Redmi 15:

- Each spec example as a spoken utterance → verify Curro's reply matches the
  expected string exactly (audio + on-screen).

---

## 14. Implementation Notes — Order of Operations

1. Move `handler/time/SpanishNumbers.kt` → `handler/calculator/SpanishNumbers.kt`
   (update `SpanishTimeFormatter.kt`'s import).
2. Extend the `SpanishNumbers` tables to cover 100..9_999_999 + `toWords` +
   `intToSpanishWords`.
3. Add the new tests for the extended ranges.
4. Add `CurroError.Calculation`.
5. Add the `copy_calc_div_zero` string.
6. Create `handler/calculator/SpanishExpressionParser.kt` with the
   tokenizer + `wordsToInt` + evaluator.
7. Write `SpanishExpressionParserTest`.
8. Create `handler/CalculateHandler.kt`.
9. Append the `@Binds @IntoMap @StringKey("calculate")` to `HandlerModule`.
10. Write `CalculateHandlerTest` (30+).
11. `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
12. Smoke test on the Redmi 15: each spec example.
13. Commit as `feat: add calculate handler + Spanish expression parser (US-028 / SF-4.4)`.

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
