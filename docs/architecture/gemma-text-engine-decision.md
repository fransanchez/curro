# Phase 9 — Large-text engine: decision and defensive runtime

> Curro ships the Phase-9 large-text engine optimistically — with safeguards
> (auto-unload on memory pressure / OOM, graceful fallback to the existing
> `copy_many_unread` flow) that turn the worst case into a no-op.
>
> **As of May 2026 the backing model is Gemma 4 E2B (Apache 2.0, ~2.5 GB on
> disk).** It replaces the original Gemma 3n E2B target. The architectural
> decision (optimistic-with-safeguards, A53 baseline, OOM / fallback contract)
> is unchanged across the swap — the model swap is a drop-in via the
> [`TextGenEngine`](../../app/src/main/java/com/curro/app/domain/repository/TextGenEngine.kt)
> interface; only the `data/ml/` implementation + the side-loaded weights file
> change.
>
> Audience: anyone landing / debugging US-061, US-062, or any Phase-9 latency
> work on the device. The class names retained from the original SF
> ([`Gemma3nEngine`](../../app/src/main/java/com/curro/app/data/ml/Gemma3nEngine.kt),
> `Gemma3nSmokeTest`, `ModelFilesGemma3nTest`, the `gemma3n*` method names on
> [`ModelFiles`](../../app/src/main/java/com/curro/app/data/ml/ModelFiles.kt) and
> [`EngineMetrics`](../../app/src/main/java/com/curro/app/domain/repository/EngineMetrics.kt))
> are diff-hygiene artefacts of the swap, not signs of a current Gemma-3n
> dependency. A future SF may rename them to `largeText*`.

---

## Why Gemma 4 (over Gemma 3n)

Swap applied May 2026, commit `refactor(llm): swap Gemma 3n → Gemma 4 E2B`.
Four reasons, in order of weight:

1. **Apache 2.0** licence. Gemma 3n shipped under the custom Gemma licence
   (acceptance gated on HF, propagation to derivative repos required). Gemma 4
   is fully Apache 2.0 — no acceptance, no token, no chain-of-derivatives
   tracking. Cleaner footprint for future distribution (Play Asset Delivery,
   signed sideload, etc.).
2. **Smaller on disk.** ~2.5 GB vs ~3.66 GB — saves ~1.16 GB on
   `/data/local/tmp/curro-models/` during sideload, on the AAB / asset pack
   once we bundle for release, and on the user's device storage.
3. **Better quality on reasoning benchmarks.** Google's published numbers show
   E2B Gemma 4 beating Gemma 3 27B on AIME, LiveCodeBench, Codeforces, and
   Tau2. Per-sender WhatsApp summarisation is a reasoning task; the upgrade is
   load-bearing for US-062's output quality, not just nice-to-have.
4. **PLE ("matformer") preserved.** The Per-Layer Embeddings trick that kept
   Gemma 3n's active RAM around 2 GB despite a ~4 GB on-disk size is preserved
   in Gemma 4. Active RAM stays in the ~2–3 GB envelope; the A53 6 GB budget
   (below) is unchanged across the swap.

Nothing about the safeguards below depends on which Gemma we use — the
contract is "an `OutOfMemoryError`, a cold model, or a malformed output all
fall back to `copy_many_unread`", and that contract holds whatever
`TextGenEngine` is backed by.

---

## Rationale (why optimistic-with-safeguards, not measure-first)

The master plan's SF-9.1 was a hard go/no-go gate: measure the dev-baseline
device's cold-load + first-inference latencies first; ship only if the numbers
clear the budget. We rejected that path for three reasons.

1. **Hardware logistics block the architectural work for an unbounded time.** The
   Redmi 15 5G is not in our hands yet; the Samsung Galaxy A53 5G (6 GB RAM,
   Exynos 1280, Android 13 + One UI) is. Waiting for the Redmi to land before
   *writing* US-061 / US-062 was the literal "measure-first" reading; instead, we
   develop and validate against the A53, the floor of our hardware spec.
2. **The architectural payoff of US-061 + US-062 is small.** US-061 is the
   `TextGenEngine` interface plus a ~150-line MediaPipe wrapper; US-062 is one
   handler branch (`> 8 unread → summarise`). Neither touches the FSM, the
   coordinator, the catalog, the launcher, or any other engine. They are
   self-contained additions.
3. **The runtime cost of being wrong is bounded.** `Gemma3nEngine` returns
   `Result.failure(CurroError.ModelCold)` (weights missing or load fails) or
   `Result.failure(CurroError.OutOfMemory)` (native OOM); the handler's fallback
   path then speaks the existing `copy_many_unread` line, which is *exactly*
   today's behaviour. The "if it doesn't work, it's identical to today" property
   is the load-bearing invariant of this decision.

Therefore: ship optimistically; let the engine's safeguards and the handler's
fallback handle the worst case at runtime.

---

## Dev / test baseline — Samsung Galaxy A53 5G (6 GB)

The dev and test floor is the **Samsung Galaxy A53 5G (6 GB RAM, Exynos 1280,
Android 13 + One UI)**, not the Redmi 15. The user does not yet have the Redmi.
The decision text is therefore: "we develop against the A53 6 GB; the safeguards
(OOM unload + warm-keep FunctionGemma) protect the worst case." Once the Redmi
lands — at ≥ A53 capability per Curro's hardware floor — it inherits the
safeguards for free. The Gemma 4 swap *improves* the A53 fit: ~1.16 GB less on
disk and the same active-RAM envelope thanks to preserved PLE.

Concretely:

- All `Gemma3nSmokeTest` runs in this SF and the next two land on the A53.
- The `models/README.md` HyperOS section still applies once the Redmi is in
  hand — both the foreground-service whitelist and the autostart toggle are
  HyperOS-specific gotchas; the A53 (One UI) has its own quirks but does not
  kill foreground services as aggressively.
- "Nunca funcionaremos en un teléfono más bajo que el A53" — if a SF requires
  thresholds the A53 cannot meet, we re-scope the SF, not the baseline.

---

## 6 GB worst case

The A53's 6 GB is meaningfully tighter than the originally-assumed 8 GB Redmi 15.
Budget at peak: OS + One UI + Compose + WhatsApp + FunctionGemma resident
(~288 MB int8) + Gemma 4 E2B active (~2–3 GB int4) = ~3–4 GB peak (the OS keeps
a chunk for itself). That fits — but leaves little headroom, so the
`onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` safeguard is REAL safety, not theatre.

Three defensive responses cover the entire failure surface:

- **`Gemma3nEngine.load()` returns `Result.failure(CurroError.OutOfMemory)`**
  when MediaPipe throws native OOM during `createFromOptions` → the handler
  falls back to `copy_many_unread`. The user gets the existing flow; no crash.
- **`Gemma3nEngine.generate()` catches `OutOfMemoryError` during inference** →
  unloads itself (releases the ~2–3 GB) + returns
  `Result.failure(CurroError.OutOfMemory)` → handler falls back to
  `copy_many_unread`. The next mic press starts cold again.
- **`CurroApp.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` proactively calls
  `textGenEngine.unload()`** the moment Android signals memory pressure → next
  `generate()` cold-reloads (or also OOMs → also falls back).
  **FunctionGemma stays warm throughout** — the assistant's function-calling
  brain never loses its load.

(The 8 GB Redmi 15 case is the easy one — Gemma 4 active fits comfortably; the
safeguards apply unchanged but are exercised less often.)

---

## Latency target

| Phase | Target on A53 6 GB | Rollback if |
|---|---|---|
| Cold load | ≤ 10 s | sustained > 10 s on the A53 (smoke test red) |
| Generate (warm) | ≤ 6 s (typical 3–6 s) | sustained > 8 s on the A53 (smoke test red) |
| End-to-end cold path | ~10–12 s including the `copy_cold_model` line | both above red |

If `Gemma3nSmokeTest` blows either budget sustainably on the A53, the rollback
is a one-line guard in `ReadAllUnreadWhatsAppHandler.summariseOrFallback` that
returns `Spoken(copy_many_unread)` unconditionally — i.e. revert to the pre-US-062
SF-4.8 branch. Triage: file an issue, remove the `summariseOrFallback` dispatch
(keep the engine; future SFs may use it elsewhere), ship the prototype without
summaries. The catalog and the FSM stay unchanged either way.

The smoke-test thresholds (10 s / 8 s) are deliberately generous; the production
target is 3–6 s. The smoke test only flags the catastrophic outliers that
trigger this rollback path. Targets are **unchanged across the Gemma 3n →
Gemma 4 swap** — PLE is preserved, same active-RAM envelope, no reason a priori
to expect different latencies.

---

## Smoke procedure

Once Gemma 4 E2B weights are present on the device (see `models/README.md` →
"Cómo bajar los pesos — Gemma 4 E2B — Phase 9"):

```bash
# Side-load the weights (once per device).
adb shell mkdir -p /data/local/tmp/curro-models
adb push models/gemma4_e2b.task /data/local/tmp/curro-models/

# Run the smoke test (instrumented; skips automatically if weights are missing).
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.curro.app.data.ml.Gemma3nSmokeTest

# Capture latencies.
adb logcat -s Curro/Gemma4Smoke
```

Expected log lines:

```
I/Curro/Gemma4Smoke: cold-load = <ms>ms          ← target ≤ 10000
I/Curro/Gemma4Smoke: first-inference = <ms>ms; output = <n> chars   ← target ≤ 8000
```

If either blows the budget, the test fails with a message pointing back to
this section. Apply the rollback procedure above.

### Measured latencies

| Date | Device variant | Model | Cold-load (ms) | First inference (ms) | Outcome |
|------|----------------|-------|----------------|----------------------|---------|
| _TBD_ | Samsung Galaxy A53 5G (6 GB, Exynos 1280, Android 13 + One UI) | Gemma 4 E2B | _TBD_ | _TBD_ | _TBD_ |

Engineer fills in this row after the first device run. Add a row per device
variant (A53 first; Redmi 15 once available). The "Model" column lets us
re-baseline cleanly if we ever swap again.

---

## Cross-references

- `docs/curro-spec-v1.0.md` §14 (open question on RAM variant) — annotated in
  v1.3 with a pointer to this doc; updated in v1.4 to name Gemma 4 E2B + the
  Apache 2.0 / smaller-disk rationale.
- `docs/curro-spec-v1.0.md` §4.4 (large-text engine role + cold-load
  behaviour) — unchanged in shape, model name updated.
- `app/src/androidTest/java/com/curro/app/data/ml/Gemma3nSmokeTest.kt` —
  the instrumented test that captures the latencies above. Class name kept
  for diff hygiene; tag is now `Curro/Gemma4Smoke`.
- `app/src/main/java/com/curro/app/data/ml/Gemma3nEngine.kt` (lands in
  US-061) — the engine that owns the OOM-aware safeguards. Class name kept
  for diff hygiene; backing model is Gemma 4 E2B.
- `app/src/main/java/com/curro/app/handler/ReadAllUnreadWhatsAppHandler.kt`
  (extended in US-062) — the only Phase-9 production caller.
- `models/README.md` "Cómo bajar los pesos — Gemma 4 E2B — Phase 9" — the
  side-load workflow (Apache 2.0; no HF token).
