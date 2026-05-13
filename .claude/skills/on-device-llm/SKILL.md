---
name: on-device-llm
description: On-device LLM integration for Curro — LiteRT + MediaPipe LLM Inference API, the two Gemma models (FunctionGemma 270M for function-calling, Gemma 3n E2B for NL generation), warm-keeping via a foreground service vs. on-demand loading, prompt templates, output validation, latency budgets, memory/OOM handling, and model delivery.
triggers:
  - LiteRT
  - TFLite
  - MediaPipe
  - LLM Inference
  - FunctionGemma
  - Gemma 3n
  - Gemma
  - on-device model
  - model loading
  - inference latency
  - foreground service
  - tasks-genai
  - quantization
  - model warm-up
---

# On-device LLM (FunctionGemma + Gemma 3n)

How Curro runs language models locally. Pairs with the `ondevice-ai-engineer` agent
and the `function-catalog` skill. Source: `docs/curro-spec-v1.0.md` §4.3, §4.4, §14.

## The two models

| Model | Quant | Footprint | Role | Loading | Latency target |
|---|---|---|---|---|---|
| **FunctionGemma 270M** | int8 | ~288 MB | utterance text → `{action, params, confidence}` JSON | **warm** — loaded once at startup, kept resident by a foreground service | **< 500 ms** text → JSON |
| **Gemma 3n E2B** | int4 | ~2 GB active | NL generation (summaries, rewrites, open answers) | **on demand** — load when a handler needs it; "Dame un segundo" while cold | 3–6 s typical |

Runtime: **LiteRT** (formerly TFLite) + the **MediaPipe LLM Inference API**.

## Dependencies (version catalog)

```toml
# libs.versions.toml
mediapipeTasksGenai = "0.10.x"   # check the latest; the LLM Inference API lives in tasks-genai

[libraries]
mediapipe-tasks-genai = { group = "com.google.mediapipe", name = "tasks-genai", version.ref = "mediapipeTasksGenai" }
```

(LiteRT comes in transitively via MediaPipe. If you ever drop to raw LiteRT for a
custom op, add `com.google.ai.edge.litert:litert` explicitly.)

## Model files — delivery (decide early)

The weights are **not in git** and the **release APK with both models is ~2.3 GB**.
Options, pick one and document it in `CLAUDE.md`:
- **Download on first run** into app-private storage (with a progress UI; the
  launcher works without the assistant until they're ready).
- **Asset pack / split APK** (Play Asset Delivery) — heavy, but bundled.
- **Side-load for the prototype** — push the files via `adb push` to app storage;
  simplest while there's a single physical device.

The **debug build must build without the weights** (stub the engine or guard on
file-present) so CI (`assembleDebug`) stays fast. Keep model files out of any
`assets/` that ships in debug.

## Engine wrapper pattern

Keep MediaPipe behind an interface in `domain/repository/` so the rest of the app —
and every test — never imports MediaPipe:

```kotlin
// domain/repository/FunctionCallEngine.kt
interface FunctionCallEngine {
    /** Maps an utterance to a validated function call, or a CurroError (cold model, invalid output, OOM…). */
    suspend fun decide(utterance: String, context: PromptContext): Result<FunctionCall>
    fun warmUp()           // load + keep resident; safe to call repeatedly
    fun isReady(): Boolean
}

// domain/repository/TextGenEngine.kt   (Gemma 3n)
interface TextGenEngine {
    suspend fun generate(prompt: String, maxTokens: Int = 256): Result<String>   // loads on demand; cold → CurroError.ModelCold surfaced upstream
    fun unload()           // free under memory pressure
}
```

Implementations in `data/ml/` (`FunctionGemmaEngine`, `Gemma3nEngine`), built around
`com.google.mediapipe.tasks.genai.llminference.LlmInference`:

```kotlin
// data/ml/FunctionGemmaEngine.kt  (sketch — not literal API; check the current MediaPipe docs)
class FunctionGemmaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val promptBuilder: FunctionCallPromptBuilder,
    private val validator: FunctionCallValidator,
    @IoDispatcher private val io: CoroutineDispatcher,
) : FunctionCallEngine {

    private var llm: LlmInference? = null

    override fun warmUp() {
        if (llm != null) return
        val modelPath = ModelFiles.functionGemma(context)   // app-private; null/absent → leave llm == null
        modelPath ?: return
        llm = LlmInference.createFromOptions(
            context,
            LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(/* tight — see decoding */ 256)
                .setTemperature(0.1f)        // function-calling wants low temperature
                .setTopK(1)
                .build(),
        )
    }

    override suspend fun decide(utterance: String, ctx: PromptContext): Result<FunctionCall> = withContext(io) {
        val engine = llm ?: return@withContext Result.failure(CurroError.ModelCold).also { warmUp() }
        val raw = runCatching { engine.generateResponse(promptBuilder.build(utterance, ctx)) }
            .getOrElse { return@withContext Result.failure(it.toCurroError()) }   // OutOfMemoryError → CurroError.OutOfMemory
        validator.parseAndValidate(raw)   // → Result<FunctionCall> or CurroError.InvalidFunctionCall / UnknownFunction
    }

    override fun isReady() = llm != null
}
```

## Warm-keeping FunctionGemma (foreground service)

```kotlin
// service/ModelWarmupService.kt
@AndroidEntryPoint
class ModelWarmupService : Service() {
    @Inject lateinit var engine: FunctionCallEngine
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildOngoingNotification())   // requires POST_NOTIFICATIONS
        engine.warmUp()
        return START_STICKY
    }
    override fun onBind(intent: Intent?) = null
}
```

- Started from `CurroApp.onCreate()` (or when the launcher first becomes visible).
- The notification is low-importance, no sound — it's required scaffolding, not a user-facing thing.
- **HyperOS will kill it** unless Curro is battery-whitelisted (see `launcher-app`). Detect a killed engine (`isReady() == false` when you expected `true`), reload, and degrade gracefully (one "Dame un segundo").

## Gemma 3n on demand

- Never load it speculatively in Fase 1 unless a feature genuinely needs it (spec
  §14 step 9 — evaluate latency first).
- When a handler needs generation: if `TextGenEngine` is cold, surface
  `CurroError.ModelCold` → the coordinator speaks "Dame un segundo", loads, then
  proceeds.
- Free it under memory pressure (`onTrimMemory` / catching `OutOfMemoryError`):
  `unload()` Gemma 3n, keep FunctionGemma. The app must keep working FunctionGemma-only.
- Risk: the **4 GB-RAM Redmi 15 variant** makes Gemma 3n marginal — confirm the
  device variant; design Fase-1 flows that don't depend on it.

## Prompt templates

**FunctionGemma** — see `function-catalog` for the contract. The prompt = a
rendering of the current-phase catalog + minimal context (current time, unread-msg
summary as counts+senders, known aliases). Keep it short — every token competes with
accuracy on a 270M model. Decode with low temperature, `topK ≈ 1`, a tight
`maxTokens`; strip code fences if the model adds them; expect **exactly one JSON
object** back.

**Gemma 3n** — task-specific, e.g. summarization:

```
Eres Curro. Resume estos mensajes de WhatsApp en una sola frase corta, en castellano coloquial, sin inventar nada:
De Pepito: "Te espero a las siete" / "Trae el pan" / "Y vino si puedes"
De Lucía: "Mañana te llamo, papá"
Resumen:
```

Keep generations short (`maxTokens` ~128–256); always pass through Curro's voice
expectations (`brand-design`); never include PII you don't need.

## Output validation (FunctionGemma)

`FunctionCallValidator.parseAndValidate(raw)`:
1. Trim, strip ```` ```json ```` fences, parse JSON. Not parseable → `CurroError.InvalidFunctionCall`.
2. `action` is a non-empty string → else invalid.
3. `action` ∈ the **current phase's** catalog → else `CurroError.UnknownFunction(action)`.
4. Required params present; param types match the catalog; no unknown params → else invalid.
5. `confidence` is a number in [0, 1] → else invalid (or clamp + warn — decide once).

**On any failure: do NOT retry automatically** (retries loop and burn battery) →
return the `CurroError` → coordinator speaks "Eso no lo sé hacer todavía. Pulsa el
botón y pídeme otra cosa, o di 'ayuda'…" → log the utterance to the failed-commands
log (distinguish *invalid output* vs. *valid-but-unknown function*).

## Threading & UX

- All inference runs on an IO/dedicated dispatcher — never the main thread.
- The UI shows "Un momento…" with a **non-animated** indicator while `processing`
  (complex animation distracts this user — spec §11).
- Surface latency in the config menu's diagnostics ("última inferencia: 380 ms").

## Testing

- **Unit (JVM)**: `FunctionCallPromptBuilder` produces the expected string for a
  known catalog + context; `FunctionCallValidator` accepts good JSON and rejects
  each malformation (non-JSON, fenced JSON, missing/empty action, unknown action,
  missing param, mistyped param, extra param, confidence out of range / non-number);
  the engine returns `CurroError.ModelCold` when not warmed; `OutOfMemoryError` →
  `CurroError.OutOfMemory`. Use a **fake `FunctionCallEngine`** everywhere upstream —
  do not load real models in JVM tests.
- **On-device (manual / instrumented on the real Redmi 15)**: warm FunctionGemma
  latency < 500 ms (record it); Gemma 3n typical latency (record it); no OOM over
  repeated use; the foreground service survives a screen-off period or recovers;
  `assembleDebug` builds and runs without the weights.

## Rules

1. **MediaPipe lives only in `data/ml/`** behind `domain/repository/` interfaces — nothing else imports it; tests use fakes.
2. **Debug builds without the weights** — guard on file-present; never ship 2 GB in the debug APK; keep CI fast.
3. **FunctionGemma warm, Gemma 3n on demand** — never block startup on Gemma 3n; free it under memory pressure.
4. **Never auto-retry invalid output** — surface a friendly fallback, log it (spec flow 7).
5. **Inference off the main thread**, "Un momento…" with a static indicator, latency shown in diagnostics.
6. **No PII in prompts or logs beyond what the task needs** — and never in telemetry.
