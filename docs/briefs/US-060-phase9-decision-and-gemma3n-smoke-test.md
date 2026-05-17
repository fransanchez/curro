# US-060 — SF-9.1 · Phase 9 decision documented + Gemma 3n smoke test

> **Spec trace:** spec §4.4 (Gemma 3n role + cold-load behaviour),
> spec §14 "Decisiones explícitamente abiertas" point 1 (RAM variant) + risks
> (Gemma-3n marginal on 4 GB).
> **Master-plan:** SF-9.1.
> **Phase:** 9 — Gemma 3n content layer.
> **Depends on:** US-019 (sideload `ModelBasePath` plumbing + `ModelFiles`),
> US-020 (`FunctionGemmaEngine` pattern that `Gemma3nEngine` mirrors).
> **Size:** S.
> **Skills:** `on-device-llm`, `function-catalog`, `spec-template`,
> `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Decision doc + smoke-test scaffold for Phase 9 (optimistic-with-safeguards) |
| **US ID** | US-060 (master-plan SF-9.1) |
| **Phase** | 9 — Gemma 3n content layer |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | ondevice-ai-engineer |

---

## 1. Summary

The master-plan's SF-9.1 was written as a hard go/no-go gate — measure the
Redmi 15 RAM variant + cold-load latency before writing any Phase-9 code. We
have since chosen a different path: **implement Phase 9 optimistically and
make the runtime defensive enough that the worst case (4 GB device, 10 s+
cold-load) auto-falls back to the existing SF-4.8 "te los leo todos" branch
without crashing.** This SF lands the *paperwork* + a *device-side smoke
test* that captures cold-load + first-inference latencies once the user
plugs the Redmi 15 in.

Three artefacts:

1. **`docs/architecture/gemma-3n-decision.md`** — the rationale, the 4 GB
   worst-case behaviour (US-061's `onTrimMemory` unload + US-062's
   `copy_many_unread` fallback), the latency budget (3–6 s typical, 10 s+
   triggers the rollback path), and the smoke-test procedure.
2. **Spec §14 update + v1.3 revision-history row** — one sentence on the
   defensive Phase-9 strategy so the spec doesn't read as if Phase 9 is
   blocked.
3. **`Gemma3nSmokeTest`** — instrumented test that loads Gemma 3n once,
   runs a tiny inference, logs the latencies to `Curro/Gemma3nSmoke`, and
   fails (with a clear actionable message) if cold-load > 10 s OR first
   inference > 8 s. The test is `assumeTrue`-skipped when the weights are
   absent so CI / clones-without-weights stay green.

**Why this matters for *this* user**: Fran's father will *never* see this
SF. Its audience is Fran-the-PM (does the decision survive contact with
the hardware?) and the ondevice-ai-engineer who lands US-061/US-062 next.
But the user benefits directly: the defensive posture this SF documents is
what guarantees that — even on a 4 GB device — Curro doesn't crash when he
asks for his unread WhatsApps.

---

## 2. Scope

**In scope:**

- `docs/architecture/gemma-3n-decision.md` (NEW).
- Edit `docs/curro-spec-v1.0.md` §14 + add v1.3 row.
- `app/src/androidTest/java/com/curro/app/data/ml/Gemma3nSmokeTest.kt`
  (NEW instrumented test).
- Extend `models/README.md` with a Gemma 3n subsection (download URL is a
  `_TBD_` placeholder — the slug is confirmed in US-061; this SF just
  pre-creates the section + the `adb push` command).

**Out of scope:**

- The `TextGenEngine` interface, `Gemma3nEngine`, the `onTrimMemory`
  wiring — those land in US-061.
- The `read_all_unread_whatsapp` handler integration — US-062.
- Any production code (Kotlin in `app/src/main/`).
- Any new strings, permissions, telemetry events, DataStore keys.
- The Hugging Face download URL — `_TBD_` here; pinned in US-061.
- A new `FunctionGemmaSmokeTest` — out of scope; US-024 already covers the
  FunctionGemma cold-load smoke in production logcat.

---

## 3. User Flows

### Flow 1: Fran reads the decision doc before kicking off US-061

1. Fran opens `docs/architecture/gemma-3n-decision.md`.
2. Reads §Rationale → understands why we ship optimistically.
3. Reads §4 GB worst case → understands the unload + fallback strategy.
4. Reads §Latency target → understands the rollback trigger.
5. Reads §Smoke procedure → knows how to plug the device in and validate.
6. Triggers `/implement-feature US-061` with confidence.

### Flow 2: Smoke test runs on the device for the first time

1. ondevice-ai-engineer side-loads the Gemma 3n weights:
   `adb shell mkdir -p /data/local/tmp/curro-models && adb push models/gemma3n_e2b.task /data/local/tmp/curro-models/`.
2. Runs `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.curro.app.data.ml.Gemma3nSmokeTest`.
3. The test loads the engine, runs one tiny inference, logs:
   ```
   I/Curro/Gemma3nSmoke: cold-load = 4380ms
   I/Curro/Gemma3nSmoke: first-inference = 2150ms; output = 12 chars
   ```
4. If both budgets pass → test green. Engineer pastes the numbers into the
   "Measured latencies" table in `gemma-3n-decision.md`.
5. If either budget blows → test red with a message pointing to
   `gemma-3n-decision.md §Latency target` for the rollback procedure.

### Flow 3: CI runs without the weights (the default state)

1. CI runs `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest`.
2. The smoke test is `connectedAndroidTest`-only — it does NOT run in the
   JVM unit-test suite, so CI never sees it.
3. If CI ever DID try to run it (a future SF that wires it into CI), the
   `assumeTrue(ModelFiles.isGemma3nAvailable())` guard short-circuits it.

---

## 4. Function-catalog Impact

**No catalog change.** Phase 9 is purely an internal capability — the
catalog FunctionGemma sees in Phase 9 is identical to Phase 4's.

---

## 5. FSM States Touched

**None.** This SF is documentation + a smoke test; no runtime code in
`app/src/main/`.

---

## 6. Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none) | — | — | — |

The smoke test instantiates MediaPipe `LlmInference` directly via Hilt; no
permission is required to read a file at `/data/local/tmp/curro-models/`
(world-readable test directory).

---

## 7. On-device-model Impact

This SF is the *meta* impact: it documents the strategy for Gemma 3n. It
does NOT load the model in production code.

- **FunctionGemma**: unchanged. Continues to be warm-kept by
  `ModelWarmupService` per US-023.
- **Gemma 3n**: smoke-tested in instrumented context only; never auto-loaded
  in production (US-061 lands the on-demand path).

The smoke test itself loads Gemma 3n on the device once — the latencies it
captures are the *baseline truth* against which US-061's runtime targets
(3–6 s typical) are calibrated.

---

## 8. Android Specification

### 8.1 Files added

```
docs/
└── architecture/
    └── gemma-3n-decision.md          # NEW

app/src/androidTest/java/com/curro/app/data/ml/
└── Gemma3nSmokeTest.kt               # NEW
```

### 8.2 Files modified

```
docs/curro-spec-v1.0.md               # §14 update + v1.3 revision-history row
models/README.md                      # Gemma 3n subsection added
```

### 8.3 `docs/architecture/gemma-3n-decision.md` — required content

Five sections in this order:

1. **`# Phase 9 — Gemma 3n: decision and defensive runtime`** + a one-line
   summary.
2. **`## Rationale (why optimistic-with-safeguards, not measure-first)`** —
   one paragraph. Pinned content: the hard go/no-go would block Phase 9 for
   an unbounded time on hardware logistics; the architectural payoff of
   US-061 + US-062 is small (one handler branch); the runtime cost of being
   wrong is bounded by the unload + fallback path; the fallback path is
   identical to today's behaviour. Result: ship optimistically.
3. **`## 4 GB worst case`** — one paragraph + a 3-bullet list. Pinned
   content: on a 4 GB Redmi 15, RAM is tight (OS + Compose UI + WhatsApp +
   FunctionGemma ≈ 1.5 GB resident; Gemma 3n int4 ≈ 2 GB active leaves
   ~500 MB of headroom). Three defensive responses:
   - `Gemma3nEngine.load()` returning `Result.failure(CurroError.OutOfMemory)`
     when MediaPipe throws native OOM during creation → `ReadAllUnreadWhatsAppHandler`
     falls back to `copy_many_unread`.
   - `Gemma3nEngine.generate()` catching `OutOfMemoryError` during
     inference → unloads itself + returns `Result.failure(CurroError.OutOfMemory)`
     → handler falls back to `copy_many_unread`.
   - `CurroApp.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` proactively calls
     `textGenEngine.unload()` → next `generate()` call cold-reloads (or
     also OOMs → also falls back). FunctionGemma stays warm throughout.
4. **`## Latency target`** — pinned content: 3–6 s typical for the
   summarisation prompt on the 8 GB variant; if `lastGenerateLatencyMs`
   blows > 10 s sustainably on the Redmi 15, the rollback is a one-line
   guard in `ReadAllUnreadWhatsAppHandler` that reverts to the pre-US-062
   SF-4.8 branch. Triage: file an issue, remove the `summariseOrFallback`
   dispatch, ship the prototype without summaries.
5. **`## Smoke procedure`** — pinned content: a code block with the
   `adb push` command + the `connectedAndroidTest` command + the expected
   `adb logcat -s Curro/Gemma3nSmoke` output shape + a "Measured latencies"
   table with columns `Date | Device variant | Cold-load (ms) | First inference (ms) | Outcome`
   pre-seeded with one `_TBD_` row that the engineer fills in after the
   first device run.

### 8.4 `docs/curro-spec-v1.0.md` — exact edits

In §14 "Decisiones explícitamente abiertas, esperando datos reales del
prototipo", find the bullet:

> - Variante exacta del Redmi 15 (4GB vs 8GB RAM) — confirmar antes de empezar.

Append after that bullet (as a continuation of the same bullet, NOT a new
bullet):

> _v1.3 (US-060): RAM variant pending validation; Phase 9 implemented
> defensively — `Gemma3nEngine` auto-unloads on `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)`
> and `OutOfMemoryError`, and `ReadAllUnreadWhatsAppHandler` falls back to
> `copy_many_unread` on any `TextGenEngine.generate` failure. See
> `docs/architecture/gemma-3n-decision.md`._

In the "Historial de revisiones" table at the end, append a new row:

> | 1.3 | Mayo 2026 | android-product-analyst (US-060 / SF-9.1) | §14 Phase-9 open question annotated with the defensive runtime strategy. Pointer to `docs/architecture/gemma-3n-decision.md` for the full rationale + smoke procedure. |

Also bump the document header:

> **Versión:** 1.3

### 8.5 `Gemma3nSmokeTest.kt` — required shape

```kotlin
package com.curro.app.data.ml

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.curro.app.data.ml.Gemma3nEngine // expected after US-061; pre-existing in US-060? See pin below.
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log

private const val TAG = "Curro/Gemma3nSmoke"
private const val COLD_LOAD_BUDGET_MS = 10_000L
private const val FIRST_INFERENCE_BUDGET_MS = 8_000L

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class Gemma3nSmokeTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var engine: TextGenEngine // injected after US-061

    @Before fun setUp() {
        assumeTrue(
            "Skipped: Gemma 3n weights not present at ${ModelFiles.gemma3n().absolutePath}. " +
                "Side-load via adb push before running.",
            ModelFiles.isGemma3nAvailable(),
        )
        hiltRule.inject()
    }

    @After fun tearDown() { /* engine.unload() — runBlocking */ }

    @Test
    fun cold_load_and_first_inference_meet_budgets() {
        val coldStart = System.currentTimeMillis()
        // runBlocking { engine.load().getOrThrow() } — once US-061 lands
        val coldLoadMs = System.currentTimeMillis() - coldStart
        Log.i(TAG, "cold-load = ${coldLoadMs}ms")

        val genStart = System.currentTimeMillis()
        // val out = runBlocking { engine.generate("Resume en una frase: 'Hola, ¿cómo estás?' Salida:").getOrThrow() }
        val genMs = System.currentTimeMillis() - genStart
        Log.i(TAG, "first-inference = ${genMs}ms; output = ${"".length} chars")

        assertTrue(
            "cold-load blew the 10s budget (${coldLoadMs}ms). Rollback per docs/architecture/gemma-3n-decision.md §Latency target.",
            coldLoadMs <= COLD_LOAD_BUDGET_MS,
        )
        assertTrue(
            "first-inference blew the 8s budget (${genMs}ms). Rollback per docs/architecture/gemma-3n-decision.md §Latency target.",
            genMs <= FIRST_INFERENCE_BUDGET_MS,
        )
    }
}
```

> **Pin (interaction with US-061)**: the file *lands* in US-060 with the
> import line for `TextGenEngine` + `Gemma3nEngine` and the inference
> calls **commented out** (the test still compiles because the calls are
> the only references; the `Log.i` lines fire with placeholder values).
> When US-061 lands, the implementer uncomments the four marked lines —
> the test then exercises the real engine. This split keeps US-060 a
> docs-only commit (lower-risk) while pre-creating the test file so US-061
> doesn't pull double duty.
>
> If the ondevice-ai-engineer prefers an all-in-one US-060 + US-061 commit
> instead of pre-staging, that's fine — both produce the same end state.
> The brief documents the conservative split.

### 8.6 `models/README.md` — required additions

Append a new subsection after the existing "Cómo bajar los pesos
(FunctionGemma — paso a paso)" section:

```
## Cómo bajar los pesos (Gemma 3n E2B — Phase 9)

> _TBD: confirmar slug + filename en HF antes de la implementación de
> US-061. La spec asume `gemma3n_e2b.task` ~2 GB activo._

| Slot lógico | Origen | Tamaño | Filename esperado |
|---|---|---|---|
| Gemma 3n E2B | _TBD — pinned in US-061_ | ~2 GB | `gemma3n_e2b.task` |

```bash
adb shell mkdir -p /data/local/tmp/curro-models
adb push models/gemma3n_e2b.task /data/local/tmp/curro-models/
adb shell ls -lh /data/local/tmp/curro-models/   # verifica que aparece + tamaño
```

`ModelFiles.isGemma3nAvailable()` (añadido en US-061) devuelve `false` si
el fichero no está; en ese caso US-062 cae al fallback `copy_many_unread`
sin intentar cargar nada.

### Smoke test (manual, una vez por dispositivo nuevo)

Con los pesos presentes:

```bash
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.curro.app.data.ml.Gemma3nSmokeTest
adb logcat -s Curro/Gemma3nSmoke
```

Espera ver:

```
I/Curro/Gemma3nSmoke: cold-load = <ms>ms          ← target ≤ 10000
I/Curro/Gemma3nSmoke: first-inference = <ms>ms; output = <n> chars  ← target ≤ 8000
```

Si alguno blewa el presupuesto, ver `docs/architecture/gemma-3n-decision.md`
§Latency target y aplicar el rollback (una línea en
`ReadAllUnreadWhatsAppHandler`).
```

### 8.7 Hilt / DI

**No DI changes in this SF.** Hilt bindings for `TextGenEngine` land in
US-061's `MlModule` edit. The smoke test's `@HiltAndroidTest` annotation +
`HiltAndroidRule` are present so it works when US-061 lands; before
US-061, the `engine` field is unused (commented inference calls) and the
test compiles via the `TextGenEngine` import that resolves to US-061's new
interface.

### 8.8 Navigation

No nav change.

---

## 9. Senior-UX & Copy

**No new strings.** No spoken utterances added by this SF.

---

## 10. Acceptance Criteria

- [ ] **`docs/architecture/gemma-3n-decision.md` exists** with the 5
      sections (Rationale, 4 GB worst case, Latency target, Smoke
      procedure, Measured latencies table) per §8.3.
- [ ] **`docs/curro-spec-v1.0.md` bumped to v1.3** with the §14 annotation
      + the new revision-history row per §8.4.
- [ ] **`Gemma3nSmokeTest` instrumented test exists** with the
      `assumeTrue(ModelFiles.isGemma3nAvailable())` guard, the budget
      asserts, and the actionable failure messages pointing to
      `gemma-3n-decision.md §Latency target`.
- [ ] **CI is green** — `./gradlew assembleDebug ktlintCheck detektDebug
      testDebugUnitTest` compiles + passes; the smoke test is
      `connectedAndroidTest`-only, never runs in JVM CI.
- [ ] **`models/README.md` Gemma 3n subsection lands** with the `_TBD_`
      placeholder for the HF URL + the `adb push` command + the smoke
      invocation per §8.6.
- [ ] **No production Kotlin in `app/src/main/`** — this SF is
      docs + a test scaffold.
- [ ] **No new permissions, no new strings, no new telemetry events, no
      new dependencies.**

---

## 11. Design Notes

- The decision doc is written in English (developer audience) per
  `CLAUDE.md` "English for all code and documentation; user-facing strings
  Spanish".
- The spec edit is in Spanish (spec is in Spanish).
- The smoke-test budget numbers (10 s cold-load, 8 s first inference) are
  generous on purpose — the production target is 3–6 s; the smoke test
  flags only the *catastrophic* outliers that trigger the rollback path.

---

## 12. Performance Considerations

- The smoke test runs at most once per build/device — not a CI hot path.
- The decision doc is markdown — zero runtime cost.

---

## 13. Testing Requirements

- [ ] **No JVM tests** — there is no production code in `app/src/main/`
      to test.
- [ ] **`Gemma3nSmokeTest`** (instrumented, runs only when weights are
      present). Captures latencies; asserts they're within the 10 s /
      8 s budgets.
- [ ] **Manual verification**:
      - Open `gemma-3n-decision.md`; confirm the 5 sections render
        correctly + the table is present.
      - `grep "v1.3" docs/curro-spec-v1.0.md` returns the header bump +
        the revision row.
      - `grep "Gemma 3n E2B" models/README.md` returns the new
        subsection.

---

## 14. Implementation Notes

**File-creation summary**:

NEW:
- `docs/architecture/gemma-3n-decision.md`
- `app/src/androidTest/java/com/curro/app/data/ml/Gemma3nSmokeTest.kt`

MODIFIED:
- `docs/curro-spec-v1.0.md` (§14 annotation + v1.3 row + header bump)
- `models/README.md` (+ "Cómo bajar los pesos (Gemma 3n E2B — Phase 9)"
  subsection + smoke-test invocation)

**Sequencing pin**: this SF is the first commit in the Phase-9 batch and
deliberately the lowest-risk one. Land it on its own before US-061 so the
reviewer sees the *decision* in writing before the *implementation*.

**Cross-reference**: US-061 extends `ModelFiles.kt` with `gemma3n()` and
`isGemma3nAvailable()`. The smoke test in this SF imports them — if the
implementer prefers to land US-060 + the `ModelFiles` extension together
(and keep the engine + binding in US-061), that's an acceptable
optimisation. The brief documents the conservative split.

---

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-9.1 Phase 9 decision documented + Gemma 3n smoke test. |
