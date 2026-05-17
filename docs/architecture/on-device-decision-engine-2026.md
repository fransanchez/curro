# On-device decision engine — May 2026 field findings

**Status:** the on-device decision engine path is **not yet viable for production**
on Curro's hardware floor (Samsung Galaxy A53 5G, Exynos 1280, 6 GB RAM,
Android 13 + One UI). This note captures what we tried, what failed, what data
we collected, and what would need to change before revisiting.

The next step (decided 2026-05-17) is a cloud-backed `CloudFunctionCallEngine`
spike — see [§Path forward](#path-forward).

---

## What we tried, in order

### 1. FunctionGemma 270M Mobile-Actions (the original spec choice)

**Source:** `litert-community/functiongemma-270m-ft-mobile-actions` (Apache 2.0
license but Gemma-derived; 289 MB int8 `.litertlm`).

**Setup:** Phase 3 (US-019..024) wired this as the canonical `FunctionCallEngine`
implementation. Loaded on app start by `ModelWarmupService`, called per
mic-press by `AssistantCoordinator.runDecision()`.

**What we observed on the A53:**

- Cold load: 1.7 – 2.0 s (perfectly viable).
- Warm decide latency: 2 – 4 s (acceptable for prototype).
- **Output quality: unusable.** The fine-tune emits a proprietary token format
  (`<|tool_call>call:name{key:<|"|>value<|"|>}<tool_call|>`), is locked to
  English-only `developer` role system prompts, and expects function definitions
  as JSON Schema via the `tools=[...]` parameter (Python `apply_chat_template`)
  — none of which match Curro's Spanish-prompt + plain-JSON contract.

  In practice the model emitted `{"action": "<nombre>"}` literally (echoing the
  placeholder from our prompt template) for ~75 % of utterances. The fine-tune
  is rigid; it has lost most of base Gemma 3's instruction-following flexibility
  in exchange for that one specific format.

**Why it failed:** the fine-tune is **format-specific, not task-specific**. We
were trying to use a model trained for one I/O format with a different I/O
format. The 270 M parameter count is too small to generalise beyond what was in
the fine-tune dataset.

### 2. Gemma 4 E2B as the *sole* decision engine (probe)

**Source:** `litert-community/gemma-4-E2B-it-litert-lm` (Apache 2.0; 2.5 GB int4
`.litertlm` on disk, ~2.4 GB RSS when loaded).

**Setup:** transient `Gemma4FunctionCallEngine` (deleted post-mortem) that
delegated to the existing `TextGenEngine` (Gemma 4 backend). `MlModule` swap to
make it the bound `FunctionCallEngine`. `ModelWarmupService` now preloaded
Gemma 4 instead of FunctionGemma. `CurroApp.onTrimMemory` threshold raised to
`TRIM_MEMORY_RUNNING_CRITICAL` (from `RUNNING_LOW`) to avoid unload
ping-pong.

**What we observed on the A53:**

- Cold load *aislado* (Gemma3nSmokeTest, no other Curro infrastructure
  running): **2.0 s** — well within budget.
- Cold load *in normal Curro context* (with `ModelWarmupService`, Compose UI,
  foreground services): **7.5 s**, then `onTrimMemory` ping-pong, then
  Low Memory Killer.
- Even with `onTrimMemory` moved to `CRITICAL` only and FunctionGemma binding
  removed (saving 288 MB), the process was killed by LMK ~1.5 minutes after
  cold load, citing:
  ```
  lmkd: Reclaim 'com.curro.app' (15991) ... reason: device is low on swap
        (3203324kB < 419428kB) and thrashing (205%)
  ```

**Why it failed:** Gemma 4 E2B is mobile-optimised in principle (PLE, matformer
— ~2-3 GB active is the design target), but the **A53's 6 GB total RAM is
below the floor**. With ~2 GB for Android + One UI, ~500 MB for Compose +
Curro's own infrastructure, ~300 MB for SpeechRecognizer, leaving ~3 GB for the
model — the OS still thrashes swap and the LMK pre-emptively reclaims the
largest non-system process.

Per Google's own Gemma 4 docs, **the recommended phone is the Samsung S25
Ultra** (12-16 GB RAM). The A53 is two RAM tiers below that. There is no tuning
that closes this gap.

### 3. Base Gemma 3 270M IT (instruction-tuned, not fine-tuned for function calling)

**Source:** `litert-community/gemma-3-270m-it` (Apache 2.0; 290 MB int8
`.litertlm`).

**Setup:** kept the `FunctionGemmaEngine` wrapper, just pointed
`ModelFiles.FUNCTION_GEMMA_FILENAME` at the new model. Validator made
lenient (hoists nested `confidence` from `params` to root; tolerates enum
value drift). Prompt builder rewritten: English instruction header, Spanish
voice examples, two I/O examples per function in compact JSON.

**What we observed on the A53:**

- Cold load: 1.65 s ✓
- Warm decide latency: **8.8 – 9.7 s** ❌ (target was <500 ms; spec called this
  acceptable up to ~1.5 s; 9 s is 6× over)
- **Output quality: better than FunctionGemma but still wrong.** The model:
  - Did emit valid JSON structure (`{"action": ..., "params": {...},
    "confidence": ...}`) ✓
  - Did wrap it in `` ```json ... ``` `` markdown fences (validator strips
    these) ✓
  - **Picked the wrong action**: when asked "abre la cámara" it emitted
    `"action": "tell_time"`. ❌
  - Nested `confidence` inside `params` (validator now hoists this) ⚠
  - Invented enum values not in our catalog (e.g. `"what": "hora actual"`
    instead of one of `time|date|day|all`; validator now tolerates this) ⚠

**Why it failed:** 270 M parameters is too small for reliable multi-class
function calling in Spanish. The model is multilingual and instruction-tuned,
so it follows the format correctly, but its **reasoning capability cannot
distinguish reliably between Curro's 7 catalog functions**. This is a
capability ceiling of the model size, not a prompt-engineering problem.

**Latency** is a hardware ceiling: the A53's Exynos 1280 has no NPU support for
LiteRT; XNNPACK runs on 4 CPU cores at ~10-15 tokens/sec for a 270 M model.
For ~80 tokens of output, ~8 s is the floor on this device. There is no
prompt or quantisation tuning that fixes this.

---

## Why we cannot fix this with on-device tuning today

| What we'd need | Status (May 2026) |
|---|---|
| A model in `.litertlm` format that's small enough to keep warm (≤500 MB) AND multilingual AND instruction-tuned AND reliable at multi-class function calling | Doesn't exist publicly. Gemma 3 1B base might work (~1 GB RAM, multilingual) but latency on Exynos 1280 CPU would still be 5-15 s. Gemma 4 E2B fails on RAM. |
| Hardware floor at NPU-equipped device | Out of scope — the user's target is his father's Redmi 15 (consumer mid-range) and our dev floor is the A53. Locking the product to Pixel 8+ / Galaxy S24+ is not viable. |
| Custom Spanish function-calling fine-tune of a 270 M-1 B model | Possible but ~1-2 weeks of dataset + training + conversion work for a single person, and a continuous maintenance burden (every catalog addition = retrain). Trap for a one-person project. |
| MediaPipe NPU delegate for Exynos / general mid-range chips | Not announced. Currently NPU acceleration in MediaPipe LiteRT is Pixel-only (Tensor) and partial for Qualcomm flagships. |

We could revisit in 6-12 months when:
- Gemma 5 lands (rumoured smaller MoE variants for mobile)
- More vendor-specific `.litertlm` builds appear in `litert-community`
- LiteRT NPU support widens beyond flagship chips
- Or community fine-tunes of base Gemma 3 270M for function calling appear

---

## What we KEEP from this exploration (still useful)

Even though on-device decision is parked, the day's work produced solid
artefacts worth keeping:

### Validator quirk tolerance — keeps for cloud LLMs too

`FunctionCallValidator.normaliseQuirks()` hoists `confidence` out of `params`
when the model nests it; same with `action`. The lenient enum coerce returns
the first enum value as a safe default when the model invents an enum value.

These same quirks happen with **Claude / GPT / Gemini** outputs too (they
all sometimes emit ```json fences, occasionally nest `confidence`, sometimes
invent enum values close to but not matching declared ones). The validator is
now more robust regardless of what backs `FunctionCallEngine`.

### Improved error logging

`Log.w(TAG, "warm-up failed: ${t.javaClass.simpleName}: ${t.message}", t)` now
captures the full message + stacktrace, not just the exception class name. This
is what surfaced the `OUT_OF_RANGE: CalculatorGraph::Run()` MediaPipe error in
~10 seconds of logcat rather than 2 hours of guessing.

### MediaPipe 0.10.35 + `.litertlm` end-to-end path

We confirmed (the hard way) that:
- MediaPipe 0.10.20+ split `LlmInferenceOptions` (model wiring) from
  `LlmInferenceSession.LlmInferenceSessionOptions` (per-call generation).
- The loader branches by **file extension**: `.task` → ZIP-bundle, `.litertlm`
  → native flatbuffer. No magic-byte detection.
- Single-shot calls open a fresh `LlmInferenceSession` per turn (avoid context
  bleed across utterances).

This applies to the cloud path too: if we ever revisit on-device or use a
hybrid (cloud primary, on-device fallback), the integration is now wrong-format
proof and well-instrumented.

### Recovery Mode (commit `64cf025`)

The crash-loop Recovery Mode (≥2 crashes in 60 s → next launch shows a "Open
launcher chooser" screen) is in place. With cloud decision, the model itself
won't crash — but network timeouts, TLS handshake failures, JSON-parse
exceptions, etc. could. The same Recovery Mode catches those.

---

## Path forward — cloud-backed decision

**Next session:** spike a `CloudFunctionCallEngine` implementing the same
`FunctionCallEngine` interface, backed by **Gemini 1.5 Flash** (cheapest reliable
option at ~$0.001/turn for Curro-scale prompts) initially, with Claude Haiku as
an A/B comparison.

### What changes architecturally

Most of Curro stays. The `FunctionCallEngine` interface is engine-agnostic — we
just swap the impl:

| Component | Stays | Changes |
|---|---|---|
| FSM (`AssistantStateMachine`, `AssistantCoordinator`) | ✓ | — |
| Validator (`FunctionCallValidator`) | ✓ | — |
| Confidence policy + handler dispatch | ✓ | — |
| All 7 handlers (`TellTimeHandler`, `OpenAppHandler`, etc.) | ✓ | — |
| Alias learning (Room + `AliasRepository`) | ✓ | — |
| Config menu (Fran-only) | ✓ | — |
| Recovery Mode | ✓ | — |
| `MlModule.bindFunctionCallEngine` | — | Bind `CloudFunctionCallEngine` instead of `FunctionGemmaEngine` |
| `ModelWarmupService` | — | Becomes a noop / "connection prewarm ping" instead of model load |
| `data/ml/` | — | `FunctionGemmaEngine` + `Gemma3nEngine` stay as fallback / for summarisation; new `data/cloud/CloudFunctionCallEngine.kt` |
| `INTERNET` permission | release-only | Now needed in **debug** too |
| Spec §12 (privacy) | — | **Changes** — the user's utterance text leaves the device. Needs revision + spec v1.5 bump |

### Open questions for the cloud spike

1. **Provider**: Gemini 1.5 Flash vs Claude Haiku vs GPT-4o-mini — need to A/B
   on quality + latency + cost. Gemini is cheapest; Claude is usually best at
   following structured-output instructions; GPT is the safest "default" choice.
2. **API key management**: hardcoded for the prototype vs `local.properties`
   per-device vs a tiny Cloud Run proxy that the app calls (proxy holds the
   real key, app authenticates with a per-install ID). For Fran's father →
   prototype install, the simplest is `local.properties` + `BuildConfig`.
3. **Privacy**: what goes in the spec? Probably:
   - Utterance + alias list + catalog → to cloud provider (text only, no audio)
   - Provider retention policy: pin in spec (Anthropic 30-day default, opt-out
     with zero-retention agreement on enterprise; OpenAI similar; Gemini
     similar)
   - User notice: needed? Probably yes — Fran's call.
4. **Offline fallback**: when network is down, what happens?
   - Option A: speak `copy_no_connection` ("Necesito internet ahora, prueba
     dentro de un momento.")
   - Option B: have `FunctionGemmaEngine` (gemma-3-270m-it base) as fallback —
     it produces wrong answers ~30% of the time but for simple intents like
     `tell_time` it might be ok-ish offline.
   Decision deferred to spike.
5. **Latency target**: realistic with cloud → 800-1500 ms p99 (network +
   inference + back). Need to verify Curro's UX (listening → processing →
   speaking) holds up at that latency. Should be fine; user is OK with up to
   ~2 s wait per the spec §6 "Un momento…" pattern.

---

## What this means for the spec

`docs/curro-spec-v1.0.md` §12 (privacy: nothing leaves the device) needs revision.
The spec already accepted Firebase + PostHog telemetry as a deviation; the cloud
decision layer is a bigger deviation. Bump to v1.5 once the spike validates the
approach.

`docs/master-plan.md` may need a new phase (Phase 10 — cloud migration) or just
a new SF under Phase 11 (post-prototype hardening).

---

*Authored 2026-05-17 after the on-device validation push on Samsung A53.*
