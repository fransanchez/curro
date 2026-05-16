# Brief — US-049 / SF-7.5: Real `FailedCommandLog` Room-backed + telemetry guardrail tightened

## Metadata

| Field | Value |
|---|---|
| **Feature** | Replace SF-3.6's `Log.w("Curro/FailedCommand", ...)` stub with a real Room-backed `FailedCommandLog` (cap-at-50). Distinguish `INVALID_OUTPUT` / `UNKNOWN_FUNCTION` / `HANDLER_ERROR`. Register a new `command_failed` telemetry event whose property whitelist EXPLICITLY EXCLUDES `transcript` and `details` — Fran sees the failure log locally in Phase 8; PostHog sees only the counts. |
| **US ID** | US-049 |
| **SF ID** | SF-7.5 |
| **Phase** | 7 — Alias learning & local persistence |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst (Opus) |
| **Implementer** | android-developer (Sonnet) |
| **Size** | S |
| **Depends on** | US-045 (the `FailedCommandDao` + entity), SF-3.6 (the `Log.w` stub call sites in the coordinator + the existing `model_decide` telemetry guardrail) |
| **Unblocks** | SF-8.x failed-commands UI (Phase 8 reads `failedCommandLog.observeRecent(50)`) |

---

## Summary

SF-3.6 (US-024) introduced a placeholder for the failed-commands log: `Log.w("Curro/FailedCommand", "action=$X error=$Y utterance.len=$N")` at three call sites in the assistant pipeline. The comment was "real `FailedCommandLog` ships in Phase 7 — for now, log to `Log.w`" (verbatim from master-plan §SF-3.6).

SF-7.5 finally ships it. The `FailedCommandLog` interface lives in `domain/repository/`; `RoomFailedCommandLog` (the impl) writes to the SF-7.1 `FailedCommandDao` with `insertAndTrim` (capped at 50 per `local-data` rule 4). Three call sites move:

1. **`AssistantCoordinator.onDecisionFailure`** (line 889–911) — model returned invalid JSON, the validator rejected it, OR the function name isn't in the current phase's catalog. Mapping: `InvalidFunctionCall → INVALID_OUTPUT`; `UnknownFunction → UNKNOWN_FUNCTION`; any other → `HANDLER_ERROR`. **Pin: keep the `Log.w` line** (count-only, useful for `adb logcat` debugging) AND write to the Room log.
2. **`AssistantCoordinator.renderHandlerFailure`** (line 701–713) — a handler returned `HandlerResult.Failed` OR `HandlerDispatcher` caught a throw. Both record as `HANDLER_ERROR` with `details = "${call.action}/${result.reason::class.simpleName}"`.
3. **`HandlerDispatcher.dispatch`** catch path (line 45–50) — already wraps every `handler.handle` in `runCatching { ... }`. The thrown error becomes `HandlerResult.Failed(reason = CurroError.HandlerCrash)` and bubbles through call site #2. **Pin: the dispatcher itself does NOT touch `FailedCommandLog`** — single-source-of-truth at the coordinator. Test verifies the round-trip.

The second half of SF-7.5 is the **privacy fence**: `TelemetryGuardrail.ALLOWED_PROPS` gets one new event `"command_failed" to setOf("kind", "function_name")`. **`transcript` is NOT on the whitelist.** The coordinator emits the event on both failure call sites with `mapOf("kind" to kind.name.lowercase(), "function_name" to action ?: "unknown")`. A new parameterised test `command_failed_TranscriptOrDetailsPropAlwaysRejected` fails the build if any future PR adds either key — defence in depth alongside the existing 32-char heuristic.

Spec source: §6 flow 7 ("model gives invalid output — log it for Fran"), §9 ("Logs de comandos fallidos: últimos 50 comandos que la app no entendió"), §12 (privacy — transcripts stay local), `local-data` rule 4 (cap-at-50), `function-catalog` rule 4 (no auto-retry on invalid output).

---

## Scope

### In scope

- New `domain/repository/FailedCommandLog.kt` (interface + Kdoc + types re-exported).
- New `data/local/RoomFailedCommandLog.kt` (impl).
- New `di/FailedCommandLogModule.kt` (Hilt binding).
- Modify `AssistantCoordinator` to inject `FailedCommandLog`; replace the three call sites' bodies.
- Extend `TelemetryGuardrail.ALLOWED_PROPS` with the `command_failed` event.
- Tests: `RoomFailedCommandLogTest` (~6 cases), `AssistantCoordinatorTest` (+4 cases Group V), `HandlerDispatcherTest` (+1 case), `TelemetryGuardrailTest` (+5 cases).
- New `FakeFailedCommandLog` in `test/util/`.

### Out of scope

- The Phase-8 config-menu UI that surfaces the log — Phase 8.
- A "send failures to Fran" toggle — spec §12.3 explicitly defers `FailedCommandsExporter` to a post-prototype SF.
- Anonymisation of the transcript before storage — out: the transcript stays on-device per spec §12; no leakage path means no anonymisation needed for storage. (Phase-8's future export will need anonymisation.)
- A retry path on invalid output — `function-catalog` rule 4 explicitly forbids auto-retry; SF-7.5 only logs.

---

## User Flows

### Flow 1 — Invalid JSON (spec flow 7) — `INVALID_OUTPUT`

1. User says "tradúceme esto al italiano" → STT → "tradúceme esto al italiano" → FunctionGemma.
2. Model returns malformed JSON (or a JSON with an unknown action / missing required param).
3. `FunctionCallValidator.parseAndValidate` returns `Result.failure(CurroError.InvalidFunctionCall)`.
4. `AssistantCoordinator.onDecisionFailure(err = InvalidFunctionCall, latencyMs, transcript)`:
   - `mapErrorToKind(err)` returns `FailureKind.INVALID_OUTPUT`.
   - `failedCommandLog.record(transcript = "tradúceme esto al italiano", kind = INVALID_OUTPUT, details = "InvalidFunctionCall")`.
   - Telemetry: `telemetry.event("command_failed", mapOf("kind" to "invalid_output", "function_name" to "unknown"))` — note: NO transcript, NO details.
   - `Log.w("Curro/FailedCommand", "action=null error=InvalidFunctionCall utterance.len=29")` (unchanged Phase-3 line — for `adb logcat`).
   - User-facing: `copy_unknown_function` spoken ("Eso no lo sé hacer todavía…").
5. Phase 8 will surface this row: Fran sees "2026-05-16 14:30:00 — INVALID_OUTPUT — 'tradúceme esto al italiano'" in the config menu.

### Flow 2 — Unknown function — `UNKNOWN_FUNCTION`

1. User says "manda un mensaje a Pepito que voy en camino" → FunctionGemma returns `{action: send_whatsapp_reply, params: {...}, confidence: 0.91}`.
2. `FunctionCallValidator.parseAndValidate` rejects: the action is valid JSON but `send_whatsapp_reply` isn't in the Fase-1 catalog → `Result.failure(CurroError.UnknownFunction("send_whatsapp_reply"))`.
3. `AssistantCoordinator.onDecisionFailure`:
   - `mapErrorToKind` returns `FailureKind.UNKNOWN_FUNCTION`.
   - `failedCommandLog.record(transcript = "manda un mensaje a Pepito que voy en camino", kind = UNKNOWN_FUNCTION, details = "send_whatsapp_reply")`.
   - Telemetry: `command_failed` with `kind = "unknown_function"`, `function_name = "send_whatsapp_reply"`.
4. Fran's review: sees a `UNKNOWN_FUNCTION` row tagged with the function name → reasons "ah, my father wanted to send a reply — that's the Fase-2 feature".

### Flow 3 — Handler crash — `HANDLER_ERROR`

1. User says "abre WhatsApp" → handler runs → `PackageManager` throws an unexpected `RuntimeException`.
2. `HandlerDispatcher.dispatch` catches via `runCatching` → returns `HandlerResult.Failed(reason = CurroError.HandlerCrash("open_app", throwable = e))`.
3. `AssistantCoordinator.renderHandlerFailure(call, result)`:
   - `failedCommandLog.record(transcript = "abre WhatsApp", kind = HANDLER_ERROR, details = "open_app/HandlerCrash")`.
   - Telemetry: `command_failed` with `kind = "handler_error"`, `function_name = "open_app"`.
4. User-facing: the handler's `copy_handler_crash` line is spoken.

### Flow 4 — Handler returns `Failed` (non-crash) — `HANDLER_ERROR`

1. User says "llama a Pepito" → handler runs → `findByName("Pepito")` returns empty → handler returns `Failed(copy_contact_not_found, ContactNotFound)`.
2. `AssistantCoordinator.renderHandlerFailure`:
   - `failedCommandLog.record(transcript = "llama a Pepito", kind = HANDLER_ERROR, details = "call_contact/ContactNotFound")`.
   - Telemetry: `command_failed` with `kind = "handler_error"`, `function_name = "call_contact"`.
3. The DISTINCTION: this isn't a crash (the handler ran fine and decided "I can't"); but for the log, both are `HANDLER_ERROR` (Fran's UI groups by `details`). The telemetry `handler_invoked` event's `outcome` field still distinguishes `failed` vs `crash` (existing SF-3.6 logic).

### Flow 5 — Cap-at-50 enforcement

1. The user generates 60 failed commands over a week (say, mostly "tradúceme …" attempts).
2. Each `failedCommandLog.record(...)` runs `dao.insertAndTrim(entity)` — atomic insert + `DELETE WHERE id NOT IN (top 50 by timestamp DESC)`.
3. After all 60 inserts: `dao.count() == 50`; the 10 oldest are gone.
4. Fran opens Phase-8's UI: sees the 50 newest, ordered by timestamp descending.

---

## Function-catalog Impact

**No catalog change.** SF-7.5 wires existing failure paths to a new persistence layer; the catalog functions themselves are unchanged.

---

## FSM States Touched

**None.** SF-7.5 is purely a side effect inside two existing FSM-driven failure paths (`onDecisionFailure` and `renderHandlerFailure`). No new event, no new state.

---

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| _(none)_ | Room is purely local; no `INTERNET` for the log itself | _(N/A)_ | _(N/A)_ |
| `INTERNET` (release only) | The `command_failed` telemetry event ships via PostHog/Firebase to the release-only telemetry pipeline (same path as `handler_invoked`, `model_decide`, etc.) — already wired in SF-0.8 | _(release variant only)_ | Telemetry SDKs fail silently; log writes still work locally (the two are decoupled). |

**No new permissions; no manifest changes.**

---

## On-device-model Impact

**No model impact.** Failure logging is a side effect after FunctionGemma's output is rejected (or after a handler runs); it doesn't change the prompt, the validator, or the model loading.

---

## Android Specification

### Files added

```
app/src/main/java/com/curro/app/domain/repository/
    FailedCommandLog.kt                # interface

app/src/main/java/com/curro/app/data/local/
    RoomFailedCommandLog.kt            # impl

app/src/main/java/com/curro/app/di/
    FailedCommandLogModule.kt          # Hilt binding

app/src/test/java/com/curro/app/data/local/
    RoomFailedCommandLogTest.kt        # ~6 cases, Robolectric in-memory Room

app/src/test/java/com/curro/app/util/
    FakeFailedCommandLog.kt            # records calls; tests assert against captured list
```

### Files modified

```
app/src/main/java/com/curro/app/assistant/
    AssistantCoordinator.kt            # inject FailedCommandLog; replace 3 Log.w paths

app/src/main/java/com/curro/app/data/telemetry/
    TelemetryGuardrail.kt              # add "command_failed" event

app/src/test/java/com/curro/app/assistant/
    AssistantCoordinatorTest.kt        # +4 Group V cases (using FakeFailedCommandLog)

app/src/test/java/com/curro/app/domain/handler/
    HandlerDispatcherTest.kt           # +1 case (dispatcher doesn't touch log)

app/src/test/java/com/curro/app/data/telemetry/
    TelemetryGuardrailTest.kt          # +5 cases (whitelist + PII rejection)
```

### `FailedCommandLog.kt` (interface)

```kotlin
package com.curro.app.domain.repository

import com.curro.app.data.local.FailedCommandEntity
import com.curro.app.data.local.FailureKind
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence of commands Curro couldn't act on (SF-7.5 / US-049, spec §6
 * flow 7 + §9 "Logs de comandos fallidos").
 *
 * The implementation ([com.curro.app.data.local.RoomFailedCommandLog]) caps the
 * table at 50 (`local-data` rule 4) via [com.curro.app.data.local.FailedCommandDao.insertAndTrim].
 *
 * **Privacy** (spec §12): the [FailedCommandEntity.transcript] field stays on
 * the device. The PostHog/Firebase `command_failed` telemetry event carries
 * `kind` + `function_name` ONLY — `transcript` is NOT on the
 * [com.curro.app.data.telemetry.TelemetryGuardrail.ALLOWED_PROPS] whitelist.
 * The Phase-8 config menu UI is the only surface that reads this table.
 *
 * **Single writer**: [com.curro.app.assistant.AssistantCoordinator] is the
 * only caller of [record]. The [com.curro.app.domain.handler.HandlerDispatcher]
 * does NOT touch this interface — it bubbles errors via
 * [com.curro.app.domain.handler.HandlerResult.Failed], which the coordinator
 * routes through [com.curro.app.assistant.AssistantCoordinator.renderHandlerFailure]
 * to record.
 */
interface FailedCommandLog {
    /**
     * Persist a failure. Atomic insert + trim-to-50.
     *
     * @param transcript the user's utterance (PII — stays on-device).
     * @param kind which of the three failure paths fired (see [FailureKind]).
     * @param details free-form diagnostic context (function name, error class
     *   simple name, etc. — anything safe to read in Fran's Phase-8 UI; **NOT**
     *   pushed to telemetry).
     */
    suspend fun record(transcript: String, kind: FailureKind, details: String = "")

    /** Phase-8 UI subscription: top-[limit] by timestamp descending. */
    fun observeRecent(limit: Int = 50): Flow<List<FailedCommandEntity>>

    /** Total row count — Phase-8 UI may display the badge "50 / 50". */
    suspend fun count(): Int

    /** Phase-8 "borrar log" affordance. */
    suspend fun deleteAll()
}
```

### `RoomFailedCommandLog.kt` (impl)

```kotlin
package com.curro.app.data.local

import com.curro.app.assistant.TimeProvider
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.repository.FailedCommandLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [FailedCommandLog] (SF-7.5 / US-049).
 *
 * The cap-at-50 invariant is enforced by [FailedCommandDao.insertAndTrim] —
 * an atomic transaction. See [FailedCommandDao] Kdoc.
 */
@Singleton
class RoomFailedCommandLog
    @Inject
    constructor(
        private val dao: FailedCommandDao,
        private val timeProvider: TimeProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FailedCommandLog {

        override suspend fun record(transcript: String, kind: FailureKind, details: String) =
            withContext(ioDispatcher) {
                dao.insertAndTrim(
                    FailedCommandEntity(
                        transcript = transcript,
                        kind = kind,
                        details = details,
                        timestampMs = timeProvider.now(),
                    ),
                )
            }

        override fun observeRecent(limit: Int): Flow<List<FailedCommandEntity>> = dao.observeRecent(limit)

        override suspend fun count(): Int = withContext(ioDispatcher) { dao.count() }

        override suspend fun deleteAll() = withContext(ioDispatcher) { dao.deleteAll() }
    }
```

### `FailedCommandLogModule.kt`

```kotlin
package com.curro.app.di

import com.curro.app.data.local.RoomFailedCommandLog
import com.curro.app.domain.repository.FailedCommandLog
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FailedCommandLogModule {
    @Binds
    @Singleton
    abstract fun bindFailedCommandLog(impl: RoomFailedCommandLog): FailedCommandLog
}
```

**Pin: kept in a separate module** rather than folded into SF-7.1's `DatabaseModule` — SF-8.x's UI module imports `FailedCommandLogModule` for `@Provides`-style mocking; the scope is cleaner.

### `AssistantCoordinator` changes

**Inject** `private val failedCommandLog: FailedCommandLog`.

**Modify `renderHandlerFailure`** (line 701–713):

```kotlin
// Before:
private suspend fun renderHandlerFailure(
    call: FunctionCall,
    result: HandlerResult.Failed,
) {
    if (!tryAutoRetryOnPermission(call.action, result.reason)) {
        Log.w(
            FAILED_TAG,
            "action=${call.action} error=${result.reason::class.simpleName} " +
                "utterance.len=${pendingTranscript.length}",
        )
        executeAndFinish(result.speech, screen = null)
    }
}

// After (SF-7.5):
private suspend fun renderHandlerFailure(
    call: FunctionCall,
    result: HandlerResult.Failed,
) {
    if (!tryAutoRetryOnPermission(call.action, result.reason)) {
        Log.w(
            FAILED_TAG,
            "action=${call.action} error=${result.reason::class.simpleName} " +
                "utterance.len=${pendingTranscript.length}",
        )
        runCatching {
            failedCommandLog.record(
                transcript = pendingTranscript,
                kind = FailureKind.HANDLER_ERROR,
                details = "${call.action}/${result.reason::class.simpleName}",
            )
        }
        telemetry.event(
            "command_failed",
            mapOf("kind" to "handler_error", "function_name" to call.action),
        )
        executeAndFinish(result.speech, screen = null)
    }
}
```

**Modify `onDecisionFailure`** (line 889–911):

```kotlin
// Before:
private suspend fun onDecisionFailure(
    err: Throwable,
    latencyMs: Int,
    transcript: String,
) {
    val (copyId, outcomeLabel, actionLabel) =
        when (err) {
            is CurroError.ModelCold -> Triple(R.string.copy_models_not_ready, "model_cold", null)
            is CurroError.OutOfMemory -> Triple(R.string.copy_error_unknown_function, "oom", null)
            is CurroError.UnknownFunction -> Triple(R.string.copy_error_unknown_function, "unknown_function", err.name)
            is CurroError.InvalidFunctionCall -> Triple(R.string.copy_error_unknown_function, "invalid_json", null)
            else -> Triple(R.string.copy_error_unknown_function, "other", null)
        }
    Log.w(
        FAILED_TAG,
        "action=${actionLabel ?: "null"} error=${err::class.simpleName} " +
            "utterance.len=${transcript.length}",
    )
    emitDecideTelemetry(outcome = outcomeLabel, latencyMs = latencyMs)
    executeAndFinish(appContext.getString(copyId), screen = null)
}

// After (SF-7.5):
private suspend fun onDecisionFailure(
    err: Throwable,
    latencyMs: Int,
    transcript: String,
) {
    val (copyId, outcomeLabel, actionLabel) =
        when (err) {
            is CurroError.ModelCold -> Triple(R.string.copy_models_not_ready, "model_cold", null)
            is CurroError.OutOfMemory -> Triple(R.string.copy_error_unknown_function, "oom", null)
            is CurroError.UnknownFunction -> Triple(R.string.copy_error_unknown_function, "unknown_function", err.name)
            is CurroError.InvalidFunctionCall -> Triple(R.string.copy_error_unknown_function, "invalid_json", null)
            else -> Triple(R.string.copy_error_unknown_function, "other", null)
        }
    Log.w(
        FAILED_TAG,
        "action=${actionLabel ?: "null"} error=${err::class.simpleName} " +
            "utterance.len=${transcript.length}",
    )
    emitDecideTelemetry(outcome = outcomeLabel, latencyMs = latencyMs)
    // SF-7.5 — persist + telemetry.
    val kind = mapErrorToKind(err)
    runCatching {
        failedCommandLog.record(
            transcript = transcript,
            kind = kind,
            details = err::class.simpleName ?: "unknown",
        )
    }
    telemetry.event(
        "command_failed",
        mapOf(
            "kind" to kind.name.lowercase(),
            "function_name" to (actionLabel ?: "unknown"),
        ),
    )
    executeAndFinish(appContext.getString(copyId), screen = null)
}

private fun mapErrorToKind(err: Throwable): FailureKind =
    when (err) {
        is CurroError.InvalidFunctionCall -> FailureKind.INVALID_OUTPUT
        is CurroError.UnknownFunction -> FailureKind.UNKNOWN_FUNCTION
        else -> FailureKind.HANDLER_ERROR
    }
```

**Pin: `runCatching { failedCommandLog.record(...) }` wraps the Room write** so a (very unlikely) Room exception doesn't block the user-facing TTS path. The `Log.w` line is preserved AS WELL — `adb logcat`-style debugging stays useful.

**Pin: `HandlerDispatcher.dispatch` is UNCHANGED** — the existing `runCatching { handler.handle(call) }.getOrElse { e -> ... }` (line 45–50) bubbles errors as `HandlerResult.Failed(reason = CurroError.HandlerCrash)`; the coordinator's `renderHandlerFailure` then records. Dispatcher stays clean of the log dependency.

### `TelemetryGuardrail.ALLOWED_PROPS` extension

```kotlin
// In TelemetryGuardrail.ALLOWED_PROPS:
mapOf(
    // ... existing entries ...

    // SF-7.5 (US-049) — failure-log telemetry. The transcript and details are
    // PII (spec §12) — explicitly NOT on this whitelist. The Phase-8 UI reads
    // them from the local Room table; PostHog/Firebase see counts only.
    //   kind ∈ {invalid_output, unknown_function, handler_error} — ≤ 16 chars
    //   function_name ∈ catalog snake_case OR "unknown" — ≤ 32 chars
    "command_failed" to setOf("kind", "function_name"),
)
```

### Navigation Routes

No new routes (Phase 8 adds the failed-commands UI route).

### Composables by Feature

_(No new composables in this SF.)_

### Material Design Components

_(N/A.)_

---

## Acceptance Criteria

### Build & static checks

- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.
- [ ] No new permissions, no new manifest entries, no new strings, no new dependencies.

### Interface contract

- [ ] `FailedCommandLog` interface has four members: `record`, `observeRecent`, `count`, `deleteAll`.
- [ ] `RoomFailedCommandLog` is `@Singleton`; constructor takes the DAO, `TimeProvider`, `@IoDispatcher CoroutineDispatcher`.
- [ ] `FailedCommandLogModule` binds `FailedCommandLog → RoomFailedCommandLog`.

### Coordinator integration

- [ ] `AssistantCoordinator` injects `FailedCommandLog`.
- [ ] `onDecisionFailure` records every failure path (`InvalidFunctionCall → INVALID_OUTPUT`, `UnknownFunction → UNKNOWN_FUNCTION`, everything else → `HANDLER_ERROR`).
- [ ] `renderHandlerFailure` records every `HandlerResult.Failed` as `HANDLER_ERROR` with `details = "${action}/${reason::class.simpleName}"`.
- [ ] `HandlerDispatcher.dispatch` does NOT inject `FailedCommandLog` (verified by absence + regression test).
- [ ] **`Log.w` lines preserved** at both call sites (debugging affordance).
- [ ] `runCatching { failedCommandLog.record(...) }` wraps each Room write (Room failure does not break TTS).

### Cap-at-50 invariant

- [ ] After 60 `record` calls, `failedCommandDao.count() == 50` AND the 50 newest survive.
- [ ] Verified by `RoomFailedCommandLogTest.record_capsAt50_keepsNewest`.

### Telemetry

- [ ] `TelemetryGuardrail.ALLOWED_PROPS["command_failed"] == setOf("kind", "function_name")`.
- [ ] `command_failed` event emitted on every failure path (both `onDecisionFailure` and `renderHandlerFailure`) with the correct `kind` AND `function_name`.
- [ ] **`transcript` and `details` are NOT on the `command_failed` whitelist** — verified by parameterised test `command_failed_TranscriptOrDetailsPropAlwaysRejected`.

### Privacy

- [ ] `transcript` never appears in a telemetry event payload — verified by `TelemetryGuardrail.check("command_failed", mapOf("transcript" to "abc"))` returning `Reject(...)`.
- [ ] `details` never appears in a telemetry event payload — same test.
- [ ] No `Log.w` line in the new code includes the raw transcript (the existing `utterance.len=$N` line is kept; it's count-only).

### Regression

- [ ] Every SF-7.1 + SF-7.2 + SF-7.3 + SF-7.4 + Phase-6 + Phase-5 + Phase-4 + SF-3.6 test still passes.
- [ ] The Phase-3 `model_decide` telemetry event and its `Log.w` calls are unchanged.

---

## Senior-UX & Copy

**No new strings.** SF-7.5 is data infrastructure + telemetry plumbing. The Phase-8 config-menu UI (SF-8.x) will define the Spanish strings for the failed-commands review screen.

The user-visible UX is unchanged: the same `copy_unknown_function` / handler-specific failure copy is spoken; the log writes are silent. Fran sees the rows later.

---

## Performance Considerations

- `failedCommandLog.record` runs on `@IoDispatcher` (via `withContext` in `RoomFailedCommandLog.record`). The two `@Transaction` operations (`insert` + `trimToFifty`) are ~2–5 ms total.
- The `Log.w` line is synchronous but cheap.
- Telemetry `event(...)` goes through `TelemetryGuardrail.check` (Regex match × 4 — ~0.1 ms) then routes to the PostHog/Firebase SDK (~1–3 ms; off-thread internally).
- Total added overhead per failure: ~3–8 ms. Acceptable — these are already user-facing-failure turns where the user is hearing "Eso no lo sé hacer todavía…" (TTS dominates the perceived latency).

---

## Testing Requirements

### `RoomFailedCommandLogTest.kt` (JVM Robolectric, ~6 cases)

```kotlin
@RunWith(AndroidJUnit4::class)
class RoomFailedCommandLogTest {
    private lateinit var db: CurroDatabase
    private lateinit var dao: FailedCommandDao
    private val timeProvider = TestTimeProvider(initialNowMs = 1000L)
    private lateinit var log: RoomFailedCommandLog

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), CurroDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.failedCommandDao()
        log = RoomFailedCommandLog(dao, timeProvider, Dispatchers.Unconfined)
    }

    @After fun tearDown() { db.close() }
}
```

Cases:
1. `record_persistsRowWith_timestampFromTimeProvider` — `record("hello", INVALID_OUTPUT)`; `dao.observeRecent(50).first().first().timestampMs == 1000L`.
2. `record_callsInsertAndTrim_capsAt50` — record 50; `count() == 50`.
3. `record_60times_keepsNewest50` — record 60 with monotonically-increasing `timeProvider.advanceBy(100)`; `count() == 50`; the first (oldest) is gone.
4. `count_reflectsInsertCount` — record 3; `count() == 3`.
5. `observeRecent_emitsDescendingTimestamp` (Turbine) — record 3 with `advanceBy(100)`; the first emission has the most-recent first.
6. `deleteAll_emptiesTable` — record 5; `deleteAll`; `count() == 0`; Turbine sees the empty emission.

### `AssistantCoordinatorTest.kt` (Group V — 4 new cases)

Test infra: `FakeFailedCommandLog` injected into the existing coordinator test setup.

**V1. `invalidJson_decisionFailure_recordsAsINVALID_OUTPUT_in_failedCommandLog_with_transcript`**
```kotlin
@Test fun invalidJson_decisionFailure_recordsAsINVALID_OUTPUT_in_failedCommandLog_with_transcript() = runTest {
    fakeSttClient.events = listOf(SttClient.Event.Final("tradúceme esto al italiano"))
    fakeEngine.decideResult = Result.failure(CurroError.InvalidFunctionCall("raw=garbage"))
    coordinator.onMicPressed()
    runCurrent()
    assertThat(fakeFailedCommandLog.records).hasSize(1)
    val r = fakeFailedCommandLog.records.first()
    assertThat(r.transcript).isEqualTo("tradúceme esto al italiano")
    assertThat(r.kind).isEqualTo(FailureKind.INVALID_OUTPUT)
    assertThat(r.details).isEqualTo("InvalidFunctionCall")
    // Telemetry assertion:
    val events = fakeTelemetry.events.filter { it.first == "command_failed" }
    assertThat(events).hasSize(1)
    assertThat(events.first().second["kind"]).isEqualTo("invalid_output")
    assertThat(events.first().second["function_name"]).isEqualTo("unknown")
    // Pin: transcript is NOT on the wire.
    assertThat(events.first().second.containsKey("transcript")).isFalse()
}
```

**V2. `unknownFunction_decisionFailure_recordsAsUNKNOWN_FUNCTION`**
```kotlin
@Test fun unknownFunction_decisionFailure_recordsAsUNKNOWN_FUNCTION() = runTest {
    fakeSttClient.events = listOf(SttClient.Event.Final("manda un mensaje a Pepito"))
    fakeEngine.decideResult = Result.failure(CurroError.UnknownFunction("send_whatsapp_reply"))
    coordinator.onMicPressed()
    runCurrent()
    val r = fakeFailedCommandLog.records.single()
    assertThat(r.kind).isEqualTo(FailureKind.UNKNOWN_FUNCTION)
    assertThat(r.transcript).isEqualTo("manda un mensaje a Pepito")
    val event = fakeTelemetry.events.single { it.first == "command_failed" }
    assertThat(event.second["function_name"]).isEqualTo("send_whatsapp_reply")
}
```

**V3. `handlerCrash_recordsAsHANDLER_ERROR_withFunctionNameInDetails`**
```kotlin
@Test fun handlerCrash_recordsAsHANDLER_ERROR_withFunctionNameInDetails() = runTest {
    fakeSttClient.events = listOf(SttClient.Event.Final("abre WhatsApp"))
    fakeEngine.decideResult = Result.success(FunctionCall("open_app", mapOf("app_name" to "WhatsApp"), 0.95f))
    // Make the dispatcher's open_app handler throw:
    fakeOpenAppHandler.throwOnHandle = RuntimeException("boom")
    coordinator.onMicPressed()
    runCurrent()
    val r = fakeFailedCommandLog.records.single()
    assertThat(r.kind).isEqualTo(FailureKind.HANDLER_ERROR)
    assertThat(r.details).isEqualTo("open_app/HandlerCrash")
}
```

**V4. `handlerReturnsFailed_recordsAsHANDLER_ERROR_butNotAsCrash`**
```kotlin
@Test fun handlerReturnsFailed_recordsAsHANDLER_ERROR_butNotAsCrash() = runTest {
    fakeSttClient.events = listOf(SttClient.Event.Final("llama a Pepito"))
    fakeEngine.decideResult = Result.success(FunctionCall("call_contact", mapOf("contact" to "Pepito"), 0.95f))
    fakeCallContactHandler.handleResult = HandlerResult.Failed(
        "No encuentro a Pepito en tus contactos.",
        CurroError.ContactNotFound("Pepito"),
    )
    coordinator.onMicPressed()
    runCurrent()
    val r = fakeFailedCommandLog.records.single()
    assertThat(r.kind).isEqualTo(FailureKind.HANDLER_ERROR)
    assertThat(r.details).isEqualTo("call_contact/ContactNotFound")
    // The `handler_invoked` outcome stays "failed" (NOT "crash"):
    val invokedEvent = fakeTelemetry.events.single { it.first == "handler_invoked" }
    assertThat(invokedEvent.second["outcome"]).isEqualTo("failed")
}
```

### `HandlerDispatcherTest.kt` (1 new case)

```kotlin
@Test fun dispatch_handlerThrows_returnsHandlerCrash_butDoesNotTouchFailedCommandLog() = runTest {
    val throwingHandler = object : FunctionHandler {
        override val functionName = "tell_time"
        override suspend fun handle(call: FunctionCall): HandlerResult { throw RuntimeException("boom") }
    }
    val dispatcher = HandlerDispatcher(
        handlers = mapOf("tell_time" to throwingHandler),
        telemetry = fakeTelemetry,
        context = context,
    )
    val result = dispatcher.dispatch(FunctionCall("tell_time", emptyMap(), 0.95f))
    assertThat(result).isInstanceOf(HandlerResult.Failed::class.java)
    assertThat((result as HandlerResult.Failed).reason).isInstanceOf(CurroError.HandlerCrash::class.java)
    // Pin: dispatcher does NOT have FailedCommandLog in its constructor — verified
    // structurally by source inspection + by the lack of a record() invocation in
    // any test fake (there's no fake to expose because the dependency is absent).
}
```

### `TelemetryGuardrailTest.kt` (5 new cases)

```kotlin
@Test fun command_failed_with_kind_invalid_output_allowed() {
    val result = TelemetryGuardrail.check("command_failed", mapOf("kind" to "invalid_output", "function_name" to "call_contact"))
    assertThat(result).isEqualTo(TelemetryGuardrail.GuardrailResult.Allow)
}

@Test fun command_failed_with_function_name_call_contact_allowed() {
    val result = TelemetryGuardrail.check("command_failed", mapOf("kind" to "handler_error", "function_name" to "call_contact"))
    assertThat(result).isEqualTo(TelemetryGuardrail.GuardrailResult.Allow)
}

@Test fun command_failed_with_transcript_prop_rejected() {
    val result = TelemetryGuardrail.check("command_failed", mapOf("kind" to "invalid_output", "transcript" to "llama a mi hija"))
    assertThat(result).isInstanceOf(TelemetryGuardrail.GuardrailResult.Reject::class.java)
    val reason = (result as TelemetryGuardrail.GuardrailResult.Reject).reason
    assertThat(reason).contains("'transcript'")
}

@Test fun command_failed_with_details_prop_rejected() {
    val result = TelemetryGuardrail.check("command_failed", mapOf("kind" to "handler_error", "details" to "call_contact/ContactNotFound"))
    assertThat(result).isInstanceOf(TelemetryGuardrail.GuardrailResult.Reject::class.java)
}

@Test fun command_failed_with_long_kind_value_rejected() {
    // A 50-char kind value catches "kind got injected with a real Spanish phrase".
    val result = TelemetryGuardrail.check("command_failed", mapOf("kind" to "x".repeat(50), "function_name" to "call_contact"))
    assertThat(result).isInstanceOf(TelemetryGuardrail.GuardrailResult.Reject::class.java)
}

// Plus the parameterised invariant:
@ParameterizedTest
@CsvSource("transcript,abc", "details,abc")
fun command_failed_TranscriptOrDetailsPropAlwaysRejected(key: String, value: String) {
    val result = TelemetryGuardrail.check("command_failed", mapOf("kind" to "handler_error", key to value))
    assertThat(result).isInstanceOf(TelemetryGuardrail.GuardrailResult.Reject::class.java)
}
```

### `FakeFailedCommandLog`

```kotlin
// app/src/test/java/com/curro/app/util/FakeFailedCommandLog.kt
package com.curro.app.util

import com.curro.app.data.local.FailedCommandEntity
import com.curro.app.data.local.FailureKind
import com.curro.app.domain.repository.FailedCommandLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class RecordedCall(val transcript: String, val kind: FailureKind, val details: String)

class FakeFailedCommandLog : FailedCommandLog {
    val records: MutableList<RecordedCall> = mutableListOf()
    private val flow = MutableStateFlow<List<FailedCommandEntity>>(emptyList())

    override suspend fun record(transcript: String, kind: FailureKind, details: String) {
        records += RecordedCall(transcript, kind, details)
    }

    override fun observeRecent(limit: Int): Flow<List<FailedCommandEntity>> = flow
    override suspend fun count(): Int = records.size
    override suspend fun deleteAll() { records.clear(); flow.value = emptyList() }
}
```

### Real-device verification

- [ ] Build + install (release variant for telemetry path; debug for the local Room path).
- [ ] Say "tradúceme esto al italiano" → `copy_unknown_function` spoken; `adb shell run-as com.curro.app sqlite3 databases/curro.db "SELECT kind, transcript, details, datetime(timestampMs/1000, 'unixepoch') FROM failed_commands ORDER BY timestampMs DESC LIMIT 5;"` shows the row with `INVALID_OUTPUT`.
- [ ] Repeat 60×; verify `SELECT COUNT(*) FROM failed_commands;` → `50`.
- [ ] (Release build) Verify PostHog dashboard shows `command_failed` events with `kind=invalid_output` count rising, BUT NEVER `transcript` as a property. Spot-check via Charles Proxy or network inspector to confirm the wire payload.
- [ ] Say "abre una app inexistente" → handler returns `Failed(AppNotFound)`; verify a row with `kind=HANDLER_ERROR`, `details=open_app/AppNotFound`.

---

## Implementation Notes

- **`runCatching` wrap on the Room write**: protects the user-facing TTS path from a (very unlikely) Room exception. The wrap is intentional; do not remove.
- **`Log.w` line preservation**: the SF-3.6 `Log.w("Curro/FailedCommand", ...)` lines stay — they're handy for `adb logcat` during real-device debugging and contain no PII (count-only).
- **Single writer**: only `AssistantCoordinator` calls `failedCommandLog.record`. The `HandlerDispatcher` test (`dispatch_handlerThrows_returnsHandlerCrash_butDoesNotTouchFailedCommandLog`) enforces this invariant structurally.
- **The parameterised `TranscriptOrDetailsPropAlwaysRejected` test is the CI canary** — if a future PR adds `transcript` to `ALLOWED_PROPS["command_failed"]`, this test fails fast. Code review catches the parallel change.
- **PM Owner has written**: Metadata, Summary, Scope, User Flows (5 of them), Function-catalog Impact, FSM States Touched, Senior-UX & Copy, Acceptance Criteria.
- **Implementer (android-developer) writes**: the code per the file shapes above; the test specs as written.

---

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial PM draft for the Phase-7 PM batch |
