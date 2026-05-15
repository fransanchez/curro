# US-024 — SF-3.6 · Decision smoke loop — STT → engine → validator → JSON on screen + TTS echo

> **Spec trace:** spec §4.3 (decision layer), flow 7 (invalid model output —
> friendly fallback, no auto-retry), §14 step 3 ("integración de FunctionGemma
> con el catálogo de Fase 1, sin handlers reales todavía. Salida visible en
> pantalla del JSON devuelto para verificar que las intenciones se mapean
> bien.")
> **Master-plan:** SF-3.6
> **Phase:** 3 — FunctionGemma decision layer
> **Depends on:** US-020 (`FunctionCallEngine`), US-022 (`FunctionCallValidator`),
> US-017 (`ListeningState` + voice loop in `LauncherViewModel`),
> US-018 (`ListeningOverlay`), US-023 (warm-up — makes the latency budget realistic,
> but the SF still works without it, just slower on the first press).
> **Size:** M

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Decision smoke loop — STT → engine → validator → JSON on screen + TTS echo |
| **US ID** | US-024 |
| **Phase** | 3 |
| **Status** | In Progress |
| **Created** | 2026-05-15 |
| **Modified** | 2026-05-15 |
| **PM Owner** | android-product-analyst |
| **Architect** | voice-pipeline-engineer / ondevice-ai-engineer |

---

## 1. Summary

Extend the existing voice loop in `LauncherViewModel` — the press → STT →
echo-via-TTS flow from US-017 — by piping the STT final transcript through
`FunctionCallEngine.decide()` (US-020) and `FunctionCallValidator.parseAndValidate()`
(US-022), and (a) speaking back the **action name** instead of the raw
transcript ("Reconocido: decir la hora"), (b) on success surfacing the parsed
JSON on the listening overlay in `BuildConfig.DEBUG` for visual verification,
and (c) on any failure speaking the spec-flow-7 friendly fallback ("Eso no
lo sé hacer todavía…").

**No real handlers run yet** — this SF is the gate spec §14 step 3 calls
out: prove the intent-mapping accuracy and the < 500 ms warm-latency target
on the Redmi 15 BEFORE building handlers in Phase 4. A new `Processing`
variant is added to `ListeningState`; Phase 5's full FSM will absorb it
unchanged.

Why this matters for *this* user: he eventually says "qué hora es" and
expects Curro to know what he meant. This SF is the moment Curro first
*understands* — even if he can't yet *act*. The smoke test is "did the
right action name come back" + "was it fast enough"; the answer determines
whether Phase 4's handler work is worth doing.

---

## 2. Scope

**In scope:**

- A new `Processing(transcript)` variant of `ListeningState`, inserted
  between `Listening(final)` and `Speaking(echo)`. **Provisional** —
  Phase 5's `AssistantStateMachine` replaces it.
- `LauncherViewModel.handleSttEvent(Event.Final)` rewritten: from "set
  Speaking → speak text → Idle" to "set Processing → decide → validate →
  set Speaking(echo or fallback) → speak → Idle".
- A `PromptContext` builder (`buildContext()`) in the ViewModel — produces an
  empty-context for Phase 3 (`nowIso = nowIsoLocal()`, others empty).
- Spanish action-description map (7 entries, one per Fase-1 function).
- Three new COPY entries (`copy_recognized_prefix`,
  `copy_error_unknown_function`, the 7 `copy_action_*` lines).
- `ListeningOverlay` extended with an optional `debugJson: String?` parameter;
  in `BuildConfig.DEBUG`-only, renders the pretty-printed JSON below the
  transcript.
- `LauncherSideEffect.ShowDebugJson(prettyJson)` — emitted on validator
  success; consumed by the overlay.
- `Log.w("Curro/FailedCommand", "action=<…> error=<…> utterance.len=<int>")`
  on every validator failure — **no utterance text**.
- A `telemetry.event("model_decide", mapOf("model" to "function_gemma_270m",
  "outcome" to <label>, "latency_ms" to ms))` — only outcome label + latency,
  **never** the utterance or the parsed action.
- JVM unit tests for the new transitions, the barge-in path, and the
  PII-boundary assertion on the `Log.w` line.

**Out of scope:**

- The real handlers — Phase 4 (one SF per function, in spec §14 order).
- The full `AssistantStateMachine` with all six states — Phase 5.
- The 1st/2nd/3rd consecutive-failure recovery counter — Phase 5.
- The 10-second silence cancel in `confirming` — Phase 5 (no `confirming`
  state in Phase 3).
- The graded-confidence policy (≥ 0.85 / 0.60 thresholds) — Phase 6. Phase 3
  does not branch on confidence; it always speaks the action description.
- The Room-backed failed-commands log — Phase 7. Phase 3 logs to `Log.w` only.
- Gemma 3n — Phase 9.

---

## 3. User Flows

### Flow 1 — Happy path (warm engine, valid function)

| # | User | System | Screen | Voice |
|---|---|---|---|---|
| 1 | Pulsa botón | `Idle → Starting → Listening("")` | Overlay aparece; "Te escucho…" | (silencio) |
| 2 | "Qué hora es" | STT emite `Partial("qué hora")` → `Partial("qué hora es")` → `Final("qué hora es")` | Transcripción en vivo | — |
| 3 | (espera) | `Listening → Processing("qué hora es")`. `engine.decide(...)` → raw JSON. `validator.parseAndValidate(...)` → `FunctionCall(tell_time, {what: time}, 0.92)`. | "Te escucho…" (debug: el JSON aparece debajo, mono-spaced) | — |
| 4 | (espera) | `Processing → Speaking("Reconocido: decir la hora")`. `ttsClient.speak(...)`. | El texto a hablar | "Reconocido: decir la hora" |
| 5 | (TTS termina) | `Speaking → Idle`. | Overlay desaparece. | (silencio) |

### Flow 2 — Invalid model output (spec flow 7)

| # | User | System | Screen | Voice |
|---|---|---|---|---|
| 1-2 | (igual al Flow 1) | — | — | — |
| 3 | (espera) | `Processing("tradúceme esto al italiano")`. `decide()` → raw. `validator` → `Result.failure(CurroError.UnknownFunction("translate"))` (model emitted a valid-shaped action that's not in Fase-1). | "Te escucho…" | — |
| 4 | (espera) | `Processing → Speaking(copy_error_unknown_function)`. `Log.w("Curro/FailedCommand", "action=translate error=UnknownFunction utterance.len=27")`. `telemetry.event("model_decide", "outcome" to "unknown_function", "latency_ms" to <ms>)`. | El texto del fallback | "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'." |
| 5 | (TTS termina) | `Speaking → Idle` | Overlay desaparece | (silencio) |

### Flow 3 — Cold engine (weights absent or HyperOS killed)

| # | User | System | Screen | Voice |
|---|---|---|---|---|
| 1-2 | (igual al Flow 1) | — | — | — |
| 3 | (espera) | `Processing(...)`. `decide()` → `Result.failure(CurroError.ModelCold)` (also kicks `warmUp()`). | "Te escucho…" | — |
| 4 | (espera) | `Processing → Speaking(copy_models_not_ready)`. `telemetry.event("model_decide", "outcome" to "model_cold", "latency_ms" to <ms>)`. | El texto del fallback | "Aún estoy preparando los modelos, dame un segundo." |
| 5 | (TTS termina) | `Idle`. | Overlay desaparece. | — |

### Flow 4 — Barge-in during `Processing`

| # | User | System | Screen | Voice |
|---|---|---|---|---|
| 1-2 | Pulsa botón, dice "qué hora es" | (igual al Flow 1, hasta `Processing`) | — | — |
| 3 | Pulsa botón otra vez (impaciente) | El job activo (`decide` en curso) se cancela vía `voiceJob.cancel(); voiceJob.join()`. `Processing → Idle → Starting → Listening("")`. La nueva sesión STT empieza. | "Te escucho…" (nuevo ciclo) | — |
| 4 | "Llama a Pepito" | (continúa como Flow 1) | — | — |

### Flow 5 — OOM during decide

| # | User | System | Screen | Voice |
|---|---|---|---|---|
| 1-2 | (igual al Flow 1) | — | — | — |
| 3 | (espera) | `decide()` lanza `OutOfMemoryError` → engine devuelve `Result.failure(CurroError.OutOfMemory)`. | "Te escucho…" | — |
| 4 | (espera) | `Processing → Speaking(copy_error_unknown_function)`. `telemetry.event("model_decide", "outcome" to "oom", "latency_ms" to <ms>)`. (Mensaje genérico — el usuario no necesita saber que es OOM.) | El texto del fallback | "Eso no lo sé hacer todavía. Pulsa el botón…" |

---

## 4. Function-catalog Impact

**No catalog change.** This SF consumes the catalog via the validator (which
reads `Fase1Catalog.functions`); it doesn't add or modify any function. The
action-description map (Spanish "decir la hora", etc.) is presentation copy,
not catalog data.

---

## 5. FSM States Touched

This SF extends the **provisional** Phase-2 `ListeningState` micro-FSM:

| State | Before this SF | After this SF |
|---|---|---|
| `Idle` | unchanged | unchanged |
| `Starting` | unchanged | unchanged |
| `Listening(partialText)` | unchanged | unchanged |
| **`Processing(transcript)`** | did not exist | **added** — between `Listening(final)` and `Speaking(echo)` |
| `Speaking(text)` | echo-of-transcript | now: action description ("Reconocido: …") on success, fallback line on failure |
| `Error(message)` | unchanged | unchanged — still used for STT-side failures (no STT, no voice pack, etc.) |

**Provisional notation**: a `// PROVISIONAL — Phase 5's AssistantStateMachine
absorbs this` comment is added to the new `Processing` variant; the
Phase-5 SF removes the per-VM `ListeningState` entirely.

Interrupt-by-button rule: any state including `Processing` is interruptible
by a new mic press → cancel the active job → re-enter `Listening`. The
existing barge-in code path in `onMicPressed()` already does this for the
voice job; this SF adds an `assertion`-style comment that the active job in
`Processing` is the `decide()` coroutine and is cancellable.

---

## 6. Android System Integrations & Permissions

**No new system integrations.** The SF wires existing collaborators:

- `SttClient` — already injected (US-015).
- `TtsClient` — already injected (US-016).
- `FunctionCallEngine` — injected from US-020.
- `FunctionCallValidator` — injected from US-022.

**No new permissions** beyond what US-023 already added.

---

## 7. On-device-model Impact

This SF is the on-device-model integration's **smoke test**:

- The latency budget (< 500 ms warm `decide` on the Redmi 15) is **measured
  here**, by reading the `Log.i("Curro/Llm", "decide latency: …")` line
  emitted by US-020's engine on every call.
- The accuracy gate (does "qué hora es" map to `tell_time`?) is **observed
  here**, by reading the JSON on the debug overlay.
- The recovery gate (cold engine → friendly fallback, no crash) is
  **exercised here**, by force-stopping the app and pressing again.
- The barge-in gate (in-flight `decide` cancellable) is **exercised here**.

**No new model loaded.** Gemma 3n is Phase 9.

---

## 8. Android Specification

### 8.1 Files added

- `app/src/test/java/com/curro/app/presentation/launcher/LauncherViewModelDecisionTest.kt`
  (or extend the existing `LauncherViewModelTest.kt`).
- 3 golden fixtures for tests (optional — JSON literals inline are fine too).

### 8.2 Files modified

- `app/src/main/java/com/curro/app/presentation/launcher/ListeningState.kt` —
  add `Processing` variant.
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt`
  — rewrite `handleSttEvent(Event.Final)`; add `buildContext()`;
  add `actionDescription(action)`; add error → COPY mapping;
  inject `FunctionCallEngine`, `FunctionCallValidator`, optionally a
  `TelemetrySink`.
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherSideEffect.kt`
  — add `ShowDebugJson(prettyJson)`.
- `app/src/main/java/com/curro/app/presentation/assistant/ListeningOverlay.kt`
  — add optional `debugJson: String?` parameter; render the mono-spaced
  JSON block in `BuildConfig.DEBUG` when `state is Processing` and
  `debugJson != null`.
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`
  — consume `ShowDebugJson` into a local `remember` so the next
  `ListeningOverlay` recomposition picks it up (clear it on `Idle`).
- `app/src/main/res/values/strings.xml` — 9 new entries.

### 8.3 `ListeningState` diff

```kotlin
sealed interface ListeningState {
    data object Idle : ListeningState
    data object Starting : ListeningState
    data class Listening(val partialText: String) : ListeningState

    /**
     * SF-3.6 (US-024) — between Listening(final) and Speaking(echo).
     * Driven by [LauncherViewModel] during `engine.decide` + validator.
     *
     * PROVISIONAL — Phase 5 replaces this whole sealed interface with the
     * [com.curro.app.assistant.AssistantStateMachine]'s `processing` state.
     */
    data class Processing(val transcript: String) : ListeningState

    data class Speaking(val text: String) : ListeningState
    data class Error(val message: String) : ListeningState
}
```

### 8.4 `LauncherViewModel.handleSttEvent` — new body

```kotlin
private suspend fun handleSttEvent(event: SttClient.Event) {
    when (event) {
        is SttClient.Event.Partial -> {
            listeningStateFlow.value = ListeningState.Listening(event.text)
        }
        is SttClient.Event.Final -> {
            decideAndSpeak(event.text)
        }
        is SttClient.Event.Failed -> {
            handleSttFailure(event.error)
        }
    }
}

private suspend fun decideAndSpeak(transcript: String) {
    listeningStateFlow.value = ListeningState.Processing(transcript)
    val ctx = buildContext()
    val decision = engine.decide(transcript, ctx) // already withContext(io) inside the engine.
    val parsed = decision.fold(
        onSuccess = { raw -> validator.parseAndValidate(raw) },
        onFailure = { Result.failure(it) }, // ModelCold / OOM / InvalidFunctionCall — keep the type.
    )
    parsed.fold(
        onSuccess = { call ->
            telemetry.event("model_decide", mapOf("model" to "function_gemma_270m", "outcome" to "success", "latency_ms" to /* read from engine log or pass through */ 0))
            val description = actionDescription(call.action)
            val speakText = appContext.getString(R.string.copy_recognized_prefix) + description
            if (BuildConfig.DEBUG) {
                _sideEffects.send(LauncherSideEffect.ShowDebugJson(prettyPrint(call)))
            }
            listeningStateFlow.value = ListeningState.Speaking(speakText)
            ttsClient.speak(speakText)
            listeningStateFlow.update { current ->
                if (current is ListeningState.Speaking) ListeningState.Idle else current
            }
        },
        onFailure = { err ->
            val (copyId, outcomeLabel, actionLabel) = when (err) {
                is CurroError.ModelCold -> Triple(R.string.copy_models_not_ready, "model_cold", null)
                is CurroError.OutOfMemory -> Triple(R.string.copy_error_unknown_function, "oom", null)
                is CurroError.UnknownFunction -> Triple(R.string.copy_error_unknown_function, "unknown_function", err.name)
                is CurroError.InvalidFunctionCall -> Triple(R.string.copy_error_unknown_function, "invalid_json", null)
                else -> Triple(R.string.copy_error_unknown_function, "other", null)
            }
            // PII boundary: NEVER log the transcript itself. Only its length.
            Log.w(
                "Curro/FailedCommand",
                "action=${actionLabel ?: "null"} error=${err::class.simpleName} utterance.len=${transcript.length}",
            )
            telemetry.event(
                "model_decide",
                mapOf(
                    "model" to "function_gemma_270m",
                    "outcome" to outcomeLabel,
                    "latency_ms" to 0, // placeholder until we wire the latency through
                ),
            )
            val msg = appContext.getString(copyId)
            listeningStateFlow.value = ListeningState.Speaking(msg)
            ttsClient.speak(msg)
            listeningStateFlow.update { current ->
                if (current is ListeningState.Speaking) ListeningState.Idle else current
            }
        },
    )
}

private fun buildContext(): PromptContext = PromptContext(
    nowIso = LocalDateTime.now().withNano(0).toString(),
    unreadMessagesSummary = "", // Phase 4 WhatsApp handlers will fill this.
    knownAliases = emptyList(), // Phase 7 alias subsystem will fill this.
)

private fun actionDescription(actionName: String): String {
    val resId = ACTION_DESCRIPTION_MAP[actionName] ?: return actionName
    return appContext.getString(resId)
}

private fun prettyPrint(call: FunctionCall): String {
    // 2-space indent; deterministic order: action / params / confidence.
    val params = call.params.entries.joinToString(",\n") { (k, v) ->
        "    \"$k\": ${jsonValue(v)}"
    }
    return buildString {
        appendLine("{")
        appendLine("  \"action\": \"${call.action}\",")
        appendLine("  \"params\": {${if (params.isEmpty()) "}" else "\n$params\n  }"},")
        appendLine("  \"confidence\": ${call.confidence}")
        append("}")
    }
}

private fun jsonValue(v: Any): String = when (v) {
    is String -> "\"$v\""
    is Int -> v.toString()
    else -> "\"$v\""
}

private companion object {
    val ACTION_DESCRIPTION_MAP = mapOf(
        "tell_time" to R.string.copy_action_tell_time,
        "open_app" to R.string.copy_action_open_app,
        "calculate" to R.string.copy_action_calculate,
        "help" to R.string.copy_action_help,
        "read_last_whatsapp" to R.string.copy_action_read_last_whatsapp,
        "read_all_unread_whatsapp" to R.string.copy_action_read_all_unread_whatsapp,
        "call_contact" to R.string.copy_action_call_contact,
    )
}
```

Constructor additions to `LauncherViewModel`:

```kotlin
private val engine: FunctionCallEngine,
private val validator: FunctionCallValidator,
private val telemetry: TelemetrySink, // already exists from US-008
```

(`@Suppress("LongParameterList")` is already on the class; new params keep it under control. If detekt's `LongParameterList` threshold is at risk, document and bump.)

### 8.5 `LauncherSideEffect` diff

```kotlin
sealed interface LauncherSideEffect {
    // … existing …

    /**
     * SF-3.6 (US-024) — surface the parsed FunctionCall JSON to the listening
     * overlay for debug-only visual verification. Render only in
     * `BuildConfig.DEBUG`; never in release. Phase 5 removes this side effect.
     */
    data class ShowDebugJson(val prettyJson: String) : LauncherSideEffect
}
```

### 8.6 `ListeningOverlay` diff

Add an optional `debugJson: String?` parameter. Inside the composable:

```kotlin
if (BuildConfig.DEBUG && state is ListeningState.Processing && debugJson != null) {
    Text(
        text = debugJson,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

The block is inside the existing column layout, below the transcript area
and above the audio-wave row.

### 8.7 `LauncherPlaceholderScreen` diff

After `collectSideEffects` is set up, hold a local `remember` for the latest
debug JSON:

```kotlin
var debugJson: String? by remember { mutableStateOf(null) }
LaunchedEffect(Unit) {
    viewModel.sideEffects.collect { effect ->
        when (effect) {
            is LauncherSideEffect.ShowDebugJson -> debugJson = effect.prettyJson
            // existing handlers …
        }
    }
}
// Clear when state returns to Idle:
LaunchedEffect(uiState.listeningState) {
    if (uiState.listeningState is ListeningState.Idle) debugJson = null
}

ListeningOverlay(
    state = uiState.listeningState,
    debugJson = debugJson,
    modifier = …,
)
```

### 8.8 Strings — exact entries

```xml
<!-- US-024 (SF-3.6) — prefix spoken before the action description on a
     successful FunctionCall parse. Trailing space is load-bearing
     (joined directly with the action description). -->
<string name="copy_recognized_prefix">Reconocido: </string>

<!-- US-024 (SF-3.6) — spec flow 7 friendly fallback for invalid model
     output / unknown function / OOM. Curro's voice: short, honest, offers
     an alternative. -->
<string name="copy_error_unknown_function">Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di \'ayuda\'.</string>

<!-- US-024 (SF-3.6) — action descriptions (Spanish, lowercase verb phrases).
     One per Fase-1 catalog function. Used by the smoke loop's TTS echo:
     "Reconocido: " + this string. -->
<string name="copy_action_tell_time">decir la hora</string>
<string name="copy_action_open_app">abrir una app</string>
<string name="copy_action_calculate">calcular</string>
<string name="copy_action_help">ayuda</string>
<string name="copy_action_read_last_whatsapp">leer el último mensaje</string>
<string name="copy_action_read_all_unread_whatsapp">leer todos los mensajes</string>
<string name="copy_action_call_contact">llamar a un contacto</string>
```

**Check first** — if `copy_error_unknown_function` already exists in
`strings.xml` from US-005's 54-entry COPY table, reuse the existing ID;
the brief flags this for the developer to verify before adding.

### 8.9 Tests

`LauncherViewModelDecisionTest.kt`:

```kotlin
class LauncherViewModelDecisionTest {

    @Test fun `happy path - Final to Processing to Speaking with action description`() = runTest {
        // Given:
        val engine = FakeFunctionCallEngine(
            nextResult = Result.success("""{"action":"tell_time","params":{"what":"time"},"confidence":0.92}"""),
            isReadyValue = true,
        )
        val validator = FunctionCallValidator()
        val tts = FakeTtsClient()
        val vm = buildViewModel(engine = engine, validator = validator, tts = tts)

        // When the SttClient emits Final:
        vm.simulateSttFinal("qué hora es")

        // Then:
        val states = vm.uiState.test { collectTransitions() }
        assertContains(states, ListeningState.Processing("qué hora es"))
        assertContains(states, ListeningState.Speaking("Reconocido: decir la hora"))
        assertEquals("Reconocido: decir la hora", tts.lastSpoken)
    }

    @Test fun `invalid model output - speaks copy_error_unknown_function`() = runTest {
        val engine = FakeFunctionCallEngine(
            nextResult = Result.success("""garbage{not json"""),
            isReadyValue = true,
        )
        val vm = buildViewModel(engine = engine)
        vm.simulateSttFinal("tradúceme esto")
        val finalSpeaking = vm.uiState.value.listeningState
        assertTrue(finalSpeaking is ListeningState.Speaking)
        assertEquals(
            appContext.getString(R.string.copy_error_unknown_function),
            (finalSpeaking as ListeningState.Speaking).text,
        )
    }

    @Test fun `unknown function - speaks copy_error_unknown_function and logs action label`() = runTest {
        val engine = FakeFunctionCallEngine(
            nextResult = Result.success("""{"action":"translate","params":{},"confidence":0.9}"""),
            isReadyValue = true,
        )
        val logSink = recordLogcat("Curro/FailedCommand")
        val vm = buildViewModel(engine = engine)
        vm.simulateSttFinal("tradúceme esto al italiano")

        val lastLog = logSink.lastLine
        assertContains(lastLog, "action=translate")
        assertContains(lastLog, "error=UnknownFunction")
        assertContains(lastLog, "utterance.len=27")
        // PII boundary: the utterance text is NEVER in the log line.
        assertFalse(lastLog.contains("tradúceme"), "Log line must not contain utterance text")
    }

    @Test fun `cold engine - speaks copy_models_not_ready`() = runTest {
        val engine = FakeFunctionCallEngine(
            nextResult = Result.failure(CurroError.ModelCold),
            isReadyValue = false,
        )
        val vm = buildViewModel(engine = engine)
        vm.simulateSttFinal("qué hora es")
        val final = vm.uiState.value.listeningState
        assertEquals(appContext.getString(R.string.copy_models_not_ready), (final as ListeningState.Speaking).text)
    }

    @Test fun `OOM - speaks copy_error_unknown_function`() = runTest {
        val engine = FakeFunctionCallEngine(
            nextResult = Result.failure(CurroError.OutOfMemory),
            isReadyValue = true,
        )
        val vm = buildViewModel(engine = engine)
        vm.simulateSttFinal("calcula mil dividido entre veinticinco")
        val final = vm.uiState.value.listeningState
        assertEquals(appContext.getString(R.string.copy_error_unknown_function), (final as ListeningState.Speaking).text)
    }

    @Test fun `barge-in during Processing cancels decide and restarts listening`() = runTest {
        val engine = FakeFunctionCallEngine(
            nextResult = Result.success("""{"action":"tell_time","params":{},"confidence":0.9}"""),
            isReadyValue = true,
        )
        // Make decide suspend artificially long via a Mutex; barge in mid-flight.
        // … verify voiceJob.cancel() was called, state returned to Listening.
    }

    @Test fun `debug build emits ShowDebugJson side effect on success`() = runTest {
        // Verify Build.DEBUG path; the SideEffect channel receives a ShowDebugJson
        // entry containing the FunctionCall's pretty-printed JSON.
    }
}
```

### 8.10 Telemetry event shape

```kotlin
telemetry.event(
    "model_decide",
    mapOf(
        "model" to "function_gemma_270m",
        "outcome" to "success" | "invalid_json" | "unknown_function" | "model_cold" | "oom" | "other",
        "latency_ms" to <int>,
    ),
)
```

**Allowed**: model name (a constant), outcome label (a finite enum), latency
(an int).

**Forbidden**: the utterance, the parsed action name, any param value, the
transcript length (yes, even the length — close-to-PII; logcat-only).

The `Log.w("Curro/FailedCommand", …)` line **does** include `utterance.len`
because it's a local-only debugging trail. The telemetry event does not.

---

## 9. Senior-UX & Copy

### 9.1 Spoken lines added

| Resource | Spanish | When |
|---|---|---|
| `copy_recognized_prefix` | "Reconocido: " | Smoke-loop echo prefix (joined with action description) |
| `copy_action_tell_time` | "decir la hora" | TTS echo for `tell_time` |
| `copy_action_open_app` | "abrir una app" | TTS echo for `open_app` |
| `copy_action_calculate` | "calcular" | TTS echo for `calculate` |
| `copy_action_help` | "ayuda" | TTS echo for `help` |
| `copy_action_read_last_whatsapp` | "leer el último mensaje" | TTS echo for `read_last_whatsapp` |
| `copy_action_read_all_unread_whatsapp` | "leer todos los mensajes" | TTS echo for `read_all_unread_whatsapp` |
| `copy_action_call_contact` | "llamar a un contacto" | TTS echo for `call_contact` |
| `copy_error_unknown_function` | "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda'." | Any validator failure other than ModelCold |

Curro's voice: each action description is a **lowercase verb phrase** so the
joined sentence reads naturally ("Reconocido: decir la hora" — no
capitalisation inside the colon-separated clause). The fallback line is the
spec's flow-7 wording — pinned and not re-litigated.

### 9.2 Visual

- **Listening overlay** unchanged from US-018 for the normal listening
  states. The only addition is the **debug-only** mono-spaced JSON block,
  rendered below the transcript when `BuildConfig.DEBUG && state is
  Processing && debugJson != null`. In release builds (when the user gets
  the APK), this block is dead code path — `BuildConfig.DEBUG == false`.
- **Tap targets, contrast, font scale**: unchanged — no new interactive UI.
- **"Feels the same every day"**: the only new visible thing for the user is
  the spoken echo changing from "qué hora es" (the raw transcript, US-017)
  to "Reconocido: decir la hora" (the action description, US-024). This is
  a *useful* change — the user starts hearing what Curro understood, which
  is a softer reveal of behaviour than the model actually doing something
  yet.

---

## 10. Acceptance Criteria

Mirroring PRD entry:

- [ ] `ListeningState.Processing(transcript)` added with a `PROVISIONAL`
  comment pointing at the Phase-5 absorption.
- [ ] `LauncherViewModel.handleSttEvent(Event.Final)` rewritten to call
  `engine.decide` → `validator.parseAndValidate` → `Speaking(echo or
  fallback)` → `Idle`.
- [ ] `buildContext()` returns an empty-fields `PromptContext` for Phase 3
  (`nowIso` filled with the current local time; the other two empty).
- [ ] Action-description map has 7 entries, one per Fase-1 function.
- [ ] `copy_recognized_prefix` + 7 `copy_action_*` + `copy_error_unknown_function`
  added to `strings.xml` (the last one only if not already present from
  US-005).
- [ ] `ListeningOverlay` accepts an optional `debugJson: String?` parameter;
  renders mono-spaced JSON below the transcript only when
  `BuildConfig.DEBUG && state is Processing && debugJson != null`.
- [ ] `LauncherSideEffect.ShowDebugJson(prettyJson)` emitted on validator
  success in debug builds; the screen consumes it into a local `remember`,
  clears on `Idle`.
- [ ] `Log.w("Curro/FailedCommand", "action=<action or null> error=<errClass>
  utterance.len=<int>")` on every validator failure. **The utterance text
  is never in the log line** — asserted by a test.
- [ ] Telemetry event `model_decide` emitted on every call (success and
  failure) with `model` + `outcome` + `latency_ms` only. Outcome labels
  are: `success`, `invalid_json`, `unknown_function`, `model_cold`, `oom`,
  `other`. **The utterance is never in any telemetry property** — asserted
  by a test that inspects the captured event payload.
- [ ] Cold-engine path speaks `copy_models_not_ready` (different from the
  generic invalid line).
- [ ] **Acceptance on the Redmi 15** (manual, weights side-loaded per US-019,
  service running per US-023):
  - "qué hora es" → JSON appears on the overlay (debug build);
    Curro says "Reconocido: decir la hora".
  - "tradúceme esto al italiano" → no JSON; Curro says
    `copy_error_unknown_function`.
  - 10 successive runs of "qué hora es" all show `Log.i("Curro/Llm",
    "decide latency: <ms>ms")` < 500 ms warm. Recorded by
    `adb logcat -s Curro/Llm | head -20`.
  - Press during `Processing` cancels the in-flight `decide` (no crash, no
    stuck `Processing`); state returns to `Listening`.
- [ ] Unit tests cover: happy path, invalid JSON, unknown function, cold
  engine, OOM, barge-in mid-Processing, debug-only side-effect emission,
  Log.w PII boundary.
- [ ] No new permissions; no manifest change; no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green.

---

## 11. Performance Considerations

- The smoke loop is on a coroutine launched from `viewModelScope`; the
  blocking `decide()` work is inside `withContext(io)` (US-020's engine
  contract). No main-thread blocking.
- The `prettyPrint(call)` builder is microseconds; runs only when
  `BuildConfig.DEBUG && validator success`.
- The action-description map is `O(1)` — one `R.string` lookup per call.
- Barge-in cancellation: `voiceJob.cancel()` propagates into the engine's
  `withContext(io)`. The MediaPipe `generateResponse` may continue to run
  natively; its result is discarded when the coroutine catches
  `CancellationException`. Memory cost: the next call starts fresh.

---

## 12. Testing Requirements

- [ ] **Unit (JVM)**: `LauncherViewModelDecisionTest.kt` — at least the 8
  tests listed in §8.9.
- [ ] **PII assertion**: a test verifying `Log.w` lines never contain the
  utterance text — uses Mockk's `verify` with an `argCaptor` (already used
  elsewhere in `LauncherViewModelTest`).
- [ ] **Telemetry assertion**: a test verifying the captured `model_decide`
  event's properties contain exactly the 3 expected keys and no utterance
  text.
- [ ] **Manual on the Redmi 15** (the gate for Phase 3 → Phase 4):
  - Weights side-loaded (US-019), service running (US-023).
  - 10 utterances tested for each of the 7 Fase-1 functions (70 total).
  - Track accuracy in a paper notebook (or a private gist): which utterance
    mapped to what action, with what confidence. The spec §13 acceptance is
    > 90 % on a hand-curated set; the brief recommends keeping the
    accuracy spreadsheet as the SF's *real* deliverable.
  - Spot-check the latency: 10 × "qué hora es" runs, all under 500 ms
    warm. Record max + median; share with Fran.
  - Force-stop the app; press; verify cold-engine path.
  - Side-load a junk file (`adb push /etc/hostname /data/local/tmp/curro-models/function_gemma_270m.task`);
    press; verify the engine fails gracefully (`Log.w("Curro/Llm",
    "warm-up failed: …")` + fallback line spoken). Restore the real
    weights afterwards.

---

## 13. Implementation Notes

### Latency in telemetry — where does the number come from?

US-020's engine logs `Log.i("Curro/Llm", "decide latency: <ms>ms")` internally
but does not expose the number through the `Result<String>` interface. Two
options for the smoke loop:

1. **Measure at the ViewModel layer** (chosen for Phase 3): wrap the
   `engine.decide(...)` call in a `SystemClock.elapsedRealtime()` pair and
   use that for the `latency_ms` property. This double-counts a few
   microseconds (coroutine switching, validator overhead) but is honest
   enough for a smoke test.
2. **Promote latency to the engine API** (deferred): change
   `Result<String>` to `Result<DecisionResult(rawString, latencyMs)>`. This
   is the right shape eventually, but it's a contract change to US-020 and
   doesn't earn anything for Phase 3 beyond ~5 µs of accuracy.

The brief picks **option 1** for Phase 3; a later SF (Phase 6 or Phase 8's
diagnostics) can promote it if needed.

### Why no confidence-based branching yet

Phase 6 implements the graded confirmation policy (≥ 0.85 / 0.60 / < 0.60).
Phase 3 deliberately treats every successful parse the same way: speak the
action description. This isolates the model+validator gate from the
confidence-policy gate so each phase has a clean validation question:

- **Phase 3 question**: "does the model produce a valid catalog action for
  realistic utterances?" If yes, proceed to Phase 4 (handlers).
- **Phase 6 question**: "does the confidence number actually correlate with
  correctness on real utterances?" If yes, ship the 0.85 / 0.60 thresholds.

### Why echo the action description and not the raw transcript

US-017's echo was the raw transcript ("qué hora es" → "qué hora es"). For
Phase 3 the echo changes to the action description ("qué hora es" →
"Reconocido: decir la hora") because:

1. It validates the **model's interpretation**, not just the STT's
   transcription. The user starts hearing what Curro thinks he meant.
2. It primes the user for Phase 4: when the real handler runs and Curro
   says "Son las diez y media", the "Reconocido: decir la hora" framing
   has already taught him that Curro is interpreting, not parroting.
3. It surfaces ambiguity. If "ponme las fotos" maps to `open_app` with
   `app_name = "fotos"`, the user hears "Reconocido: abrir una app" — a
   small signal that helps detect mis-mappings.

### Order of operations

1. Add the 9 strings to `strings.xml` (check `copy_error_unknown_function`
   exists first).
2. Edit `ListeningState.kt` to add `Processing`.
3. Edit `LauncherSideEffect` to add `ShowDebugJson`.
4. Edit `LauncherViewModel`: inject `FunctionCallEngine`,
   `FunctionCallValidator`; rewrite `handleSttEvent`; add helpers.
5. Edit `ListeningOverlay.kt` to accept and render `debugJson`.
6. Edit `LauncherPlaceholderScreen.kt` to consume `ShowDebugJson` into a
   local `remember`.
7. Add `LauncherViewModelDecisionTest.kt`.
8. Run `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` —
   green.
9. **On the Redmi 15** (the actual smoke test): side-load weights, install,
   verify the manual ACs.

### Commit scope

`feat(assistant)` — the smoke loop is the first wire-through of the
assistant. The catalog/engine/validator each had their own scope; this one
wires them into the UI.

---

## 14. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-15 | android-product-analyst | Initial draft for Phase-3 PM batch. |
