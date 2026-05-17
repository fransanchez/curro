# Phase 9 — Gemma 3n: decision and defensive runtime

> Curro ships Gemma 3n E2B optimistically — with safeguards (auto-unload on memory
> pressure / OOM, graceful fallback to the existing `copy_many_unread` flow) that
> turn the worst case into a no-op. Audience: anyone landing US-061 / US-062 or
> debugging Phase-9 latency on the device.

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
safeguards for free.

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
(~288 MB int8) + Gemma 3n active (~2 GB int4) = ~3–4 GB peak (the OS keeps a
chunk for itself). That fits — but leaves little headroom, so the
`onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` safeguard is REAL safety, not theatre.

Three defensive responses cover the entire failure surface:

- **`Gemma3nEngine.load()` returns `Result.failure(CurroError.OutOfMemory)`**
  when MediaPipe throws native OOM during `createFromOptions` → the handler
  falls back to `copy_many_unread`. The user gets the existing flow; no crash.
- **`Gemma3nEngine.generate()` catches `OutOfMemoryError` during inference** →
  unloads itself (releases the ~2 GB) + returns
  `Result.failure(CurroError.OutOfMemory)` → handler falls back to
  `copy_many_unread`. The next mic press starts cold again.
- **`CurroApp.onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` proactively calls
  `textGenEngine.unload()`** the moment Android signals memory pressure → next
  `generate()` cold-reloads (or also OOMs → also falls back).
  **FunctionGemma stays warm throughout** — the assistant's function-calling
  brain never loses its load.

(The 8 GB Redmi 15 case is the easy one — Gemma 3n active fits comfortably; the
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
trigger this rollback path.

---

## Smoke procedure

Once Gemma 3n weights are present on the device (see `models/README.md` →
"Cómo bajar los pesos (Gemma 3n E2B — Phase 9)"):

```bash
# Side-load the weights (once per device).
adb shell mkdir -p /data/local/tmp/curro-models
adb push models/gemma3n_e2b.task /data/local/tmp/curro-models/

# Run the smoke test (instrumented; skips automatically if weights are missing).
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.curro.app.data.ml.Gemma3nSmokeTest

# Capture latencies.
adb logcat -s Curro/Gemma3nSmoke
```

Expected log lines:

```
I/Curro/Gemma3nSmoke: cold-load = <ms>ms          ← target ≤ 10000
I/Curro/Gemma3nSmoke: first-inference = <ms>ms; output = <n> chars   ← target ≤ 8000
```

If either blows the budget, the test fails with a message pointing back to
this section. Apply the rollback procedure above.

### Measured latencies

| Date | Device variant | Cold-load (ms) | First inference (ms) | Outcome |
|------|----------------|----------------|----------------------|---------|
| _TBD_ | Samsung Galaxy A53 5G (6 GB, Exynos 1280, Android 13 + One UI) | _TBD_ | _TBD_ | _TBD_ |

Engineer fills in this row after the first device run. Add a row per device
variant (A53 first; Redmi 15 once available).

---

## Cross-references

- `docs/curro-spec-v1.0.md` §14 (open question on RAM variant) — annotated in
  v1.3 with a pointer to this doc.
- `docs/curro-spec-v1.0.md` §4.4 (Gemma 3n role + cold-load behaviour) —
  unchanged.
- `app/src/androidTest/java/com/curro/app/data/ml/Gemma3nSmokeTest.kt` —
  the instrumented test that captures the latencies above.
- `app/src/main/java/com/curro/app/data/ml/Gemma3nEngine.kt` (lands in
  US-061) — the engine that owns the OOM-aware safeguards.
- `app/src/main/java/com/curro/app/handler/ReadAllUnreadWhatsAppHandler.kt`
  (extended in US-062) — the only Phase-9 production caller.
- `models/README.md` "Cómo bajar los pesos (Gemma 3n E2B — Phase 9)" — the
  side-load workflow.
