# US-015 — SF-2.1 · `SttClient` (offline Spanish `SpeechRecognizer`)

> **Spec trace:** spec §4.2 (STT layer), §10 (`RECORD_AUDIO` lazily), §14 (offline,
> closed decision)
> **Master-plan:** SF-2.1
> **Phase:** 2 — Voice pipeline
> **Depends on:** US-002 (Hilt DI), US-007 (CurroNavHost shell)
> **Size:** M

---

## 1. Goal

Wrap Android's `SpeechRecognizer` behind a clean `domain/repository/SttClient`
interface so the rest of Curro consumes a `Flow<SttClient.Event>` and never sees a
`RecognitionListener` callback. Spanish, **offline** (the Redmi 15 has the voice
pack; if it doesn't, surface that cleanly). No network, no `INTERNET` permission in
the main manifest.

This is one of the two Phase-2 foundations — US-016 (`TtsClient`) is its mirror;
US-017 wires them together end-to-end; US-018 paints the visual.

---

## 2. Scope

**In scope:**

- The `SttClient` interface in `domain/repository/`.
- A `SystemSttClient` implementation in `data/voice/` around `SpeechRecognizer`.
- Spanish offline configuration (`EXTRA_PREFER_OFFLINE = true`, `EXTRA_LANGUAGE =
  "es-ES"`, partial results enabled).
- Mapping every `ERROR_*` code from `RecognitionListener.onError` to the right
  `CurroError.Stt*` variant.
- Voice-pack detection (`isOnDeviceRecognitionAvailable`) → new
  `CurroError.SttVoicePackMissing`.
- Hilt binding via a new `VoiceModule`.
- Declare `RECORD_AUDIO` in the main `AndroidManifest.xml` (declaration only — the
  runtime request is screen-side in US-017).
- Unit tests covering every error → `Event` mapping.

**Out of scope:**

- Runtime permission request (US-017).
- Listening overlay (US-018).
- Hooking into any UI / ViewModel (US-017).
- The full FSM — only a `Flow` of events; no state-machine ownership lives in
  `SttClient`.

---

## 3. User flow (the only flow this SF participates in)

This SF is plumbing — there is no user-visible flow here. It is exercised by
US-017's end-to-end loop. The shape, end-to-end, is:

1. (US-017) Mic press → permission already granted → `viewModel` calls
   `sttClient.listen().collect { … }`.
2. (US-015) `SystemSttClient` starts a `SpeechRecognizer` session;
   `RecognitionListener.onPartialResults` fires every ~150–300 ms during speech.
3. (US-015) Each partial → `Event.Partial(text)` emitted onto the Flow.
4. (US-015) User stops speaking → `onResults` → `Event.Final(text)` → Flow closes
   normally.
5. (US-015) Or: `onError(code)` → `Event.Failed(CurroError.Stt*)` → Flow closes.
6. (US-017) Coroutine collecting the Flow gets the terminal event and updates
   `ListeningState`.

If the consumer cancels the collecting coroutine (US-017's barge-in flow), the
`callbackFlow`'s `awaitClose` block fires `sr.cancel(); sr.destroy()` — the native
session is released.

---

## 4. Function-catalog impact

**No catalog change.** Phase 2 ships no FunctionGemma and no handlers. The function
catalog is untouched.

---

## 5. FSM states touched

**Provisional only — the full FSM does not exist yet.** US-017 introduces a
provisional `ListeningState` sealed interface in `LauncherUiState`. This SF
delivers the *signal* (the Flow of events) that the ViewModel transitions on. The
states themselves live in US-017.

Phase 5's `AssistantStateMachine` will replace the provisional state and the same
`SttClient.listen()` Flow will then drive `listening → processing` instead of
`Listening → Speaking`. **No interface change** is anticipated between Phase 2 and
Phase 5 — the `Event` shape is forward-compatible.

---

## 6. Android system integrations & permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `RECORD_AUDIO` | feed audio to `SpeechRecognizer` | first mic press (lazy, in US-017) | `CurroError.PermissionDenied` → US-017 shows `copy_perm_missing_mic` |

**Manifest delta (this SF):**

```xml
<!-- SF-2.1 (US-015) — required by SpeechRecognizer; runtime request is in SF-2.3 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Declaration goes in `app/src/main/AndroidManifest.xml`. The release-overlay manifest
already declares `INTERNET` for telemetry — **do not** declare `INTERNET` for STT.
The whole point is offline.

**Integration:** `android.speech.SpeechRecognizer` (framework) +
`android.speech.RecognizerIntent` + `android.speech.RecognitionListener`. No
external dep. `minSdk 31` already covers `isOnDeviceRecognitionAvailable` and the
`EXTRA_PREFER_OFFLINE` honouring.

---

## 7. On-device-model impact

**No model impact.** FunctionGemma and Gemma 3n are Phase 3+. This SF does not
touch MediaPipe, LiteRT, or any prompt builder.

---

## 8. Android specification

### 8.1 The interface — `domain/repository/SttClient.kt`

```kotlin
package com.curro.app.domain.repository

import com.curro.app.domain.model.CurroError
import kotlinx.coroutines.flow.Flow

/**
 * Offline Spanish speech-to-text.
 *
 * Consumers collect [listen]; the Flow emits zero-to-many [Event.Partial], then
 * exactly one terminal event — either [Event.Final] (success) or [Event.Failed]
 * (any error). Cancelling the collecting coroutine releases the native session
 * (see [SystemSttClient.awaitClose]).
 *
 * Implementation must not retain a `Context` reference outside its `@Singleton`
 * scope, must not perform I/O off the framework's own threads, and must NEVER call
 * any network API.
 */
interface SttClient {

    /** Emits Partial* → Final | Failed. Closes after the terminal event. */
    fun listen(): Flow<Event>

    /**
     * Cancel any in-flight [listen] session. Idempotent. Safe to call from any
     * thread — implementation marshals to Main if needed.
     */
    fun cancel()

    /**
     * Best-effort probe — true iff the device claims it can run STT for Spanish
     * locally (`SpeechRecognizer.isOnDeviceRecognitionAvailable` is true AND a
     * Spanish locale is among the supported on-device locales).
     */
    suspend fun hasOfflineSpanish(): Boolean

    sealed interface Event {
        data class Partial(val text: String) : Event
        data class Final(val text: String) : Event
        data class Failed(val error: CurroError) : Event
    }
}
```

### 8.2 The implementation — `data/voice/SystemSttClient.kt`

Sketch (the dev fleshes it out — this is the contract):

```kotlin
package com.curro.app.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.SttClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SystemSttClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : SttClient {

    @Volatile private var current: SpeechRecognizer? = null

    override fun listen(): Flow<SttClient.Event> = callbackFlow {
        // ───── this body MUST run on Dispatchers.Main.immediate ─────
        // SpeechRecognizer.createSpeechRecognizer(...) and every method on it
        // require the main thread. See §11 for the why and the prior incident.
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        current = sr

        val listener = object : RecognitionListener {
            override fun onPartialResults(partial: Bundle) {
                val text = partial
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotEmpty()) trySend(SttClient.Event.Partial(text))
            }

            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isEmpty()) {
                    trySend(SttClient.Event.Failed(CurroError.SttNoMatch))
                } else {
                    trySend(SttClient.Event.Final(text))
                }
                close()
            }

            override fun onError(error: Int) {
                trySend(SttClient.Event.Failed(error.toCurroError()))
                close()
            }

            // The rest are no-ops:
            override fun onReadyForSpeech(params: Bundle) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        sr.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        sr.startListening(intent)

        awaitClose {
            sr.cancel()
            sr.destroy()
            if (current === sr) current = null
        }
    }.flowOn(Dispatchers.Main.immediate)
    //    ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲ — see §11 (CRITICAL) ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

    override fun cancel() {
        current?.cancel()
    }

    override suspend fun hasOfflineSpanish(): Boolean =
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context) // + a Spanish-locale follow-up if needed
}

private fun Int.toCurroError(): CurroError = when (this) {
    SpeechRecognizer.ERROR_NO_MATCH         -> CurroError.SttNoMatch
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT   -> CurroError.SttTimeout
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> CurroError.PermissionDenied
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE      -> CurroError.SttVoicePackMissing
    else -> CurroError.SttError(this)
}
```

The dev may simplify (e.g. drop the `@Volatile current` if `cancel()` is unused
in US-017's flow) — what's binding is: `flowOn(Dispatchers.Main.immediate)`, the
error mapping, the `awaitClose` cleanup, the `EXTRA_*` set.

### 8.3 New `CurroError` variants

CLAUDE.md "Error handling" table currently lists `SttNoMatch`, `SttTimeout`,
`SttError(code)`. **Add two:**

```kotlin
data object SttVoicePackMissing : CurroError       // ES voice pack not present
// existing: data object PermissionDenied : CurroError   — already covered
```

(`PermissionDenied` is generic and reused for `RECORD_AUDIO`, `CALL_PHONE`, etc.;
the screen distinguishes by context.)

Update CLAUDE.md "Error handling" section to add the new variant. Spec §10 already
implies this — no spec change needed.

### 8.4 Hilt module — `di/VoiceModule.kt`

```kotlin
package com.curro.app.di

import com.curro.app.data.voice.SystemSttClient
import com.curro.app.data.voice.SystemTtsClient
import com.curro.app.domain.repository.SttClient
import com.curro.app.domain.repository.TtsClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the voice pipeline (SF-2.1 / US-015 + SF-2.2 / US-016).
 *
 * Both clients are @Singleton — the Android framework's SpeechRecognizer and
 * TextToSpeech are heavy to instantiate; the singleton wraps them and reuses the
 * native instance across calls.
 */
@Module
@InstallIn(SingletonComponent::class)
interface VoiceModule {
    @Binds @Singleton
    fun bindSttClient(impl: SystemSttClient): SttClient

    // US-016 lands the TtsClient binding here too.
    // @Binds @Singleton
    // fun bindTtsClient(impl: SystemTtsClient): TtsClient
}
```

US-016 adds the `TtsClient` binding in the same module. SF-2.1 ships the `STT`
binding only (the `TtsClient` binding is the responsibility of US-016).

---

## 9. Senior-UX & copy

This SF ships **one new Spanish string**:

| ID | Value | Notes |
|----|-------|-------|
| `copy_stt_no_voice_pack` | `Falta el paquete de voz español. Díselo a Fran.` | NEW (US-015). Used when `hasOfflineSpanish()` returns false. Add to canonical COPY table in `brand-design/SKILL.md`. |

Existing strings reused by US-017 (when consuming `SttClient`):

- `copy_perm_missing_mic` (RECORD_AUDIO denied — already in `strings.xml`).
- `copy_stt_fail_1` (1st STT failure — already in `strings.xml`; Phase 2 uses it for
  every fail; Phase 5 wires the 1st/2nd/3rd counter).

---

## 10. Acceptance criteria

- [ ] `SttClient.kt` interface exists at
  `app/src/main/java/com/curro/app/domain/repository/SttClient.kt` with the
  `Flow<Event>` signature and the three methods of §8.1.
- [ ] `SystemSttClient.kt` exists at
  `app/src/main/java/com/curro/app/data/voice/SystemSttClient.kt`, `@Singleton`,
  `@Inject constructor(@ApplicationContext context: Context)`.
- [ ] `callbackFlow { … }.flowOn(Dispatchers.Main.immediate)` — terminal
  `flowOn` is **mandatory** (see §11).
- [ ] `RecognizerIntent` extras set: `EXTRA_LANGUAGE = "es-ES"`,
  `EXTRA_LANGUAGE_PREFERENCE`, `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`,
  `EXTRA_PREFER_OFFLINE = true`, `EXTRA_PARTIAL_RESULTS = true`,
  `EXTRA_CALLING_PACKAGE = context.packageName`.
- [ ] `onPartialResults` → `Event.Partial(text)` for non-empty text only.
- [ ] `onResults` empty → `Event.Failed(SttNoMatch) + close`; non-empty →
  `Event.Final(text) + close`.
- [ ] `onError(code)` → `Event.Failed` with the mapping in §8.2 + `close`.
- [ ] `awaitClose { sr.cancel(); sr.destroy() }` releases the native recogniser.
- [ ] `hasOfflineSpanish()` calls `SpeechRecognizer.isOnDeviceRecognitionAvailable`
  + Spanish-locale check.
- [ ] `CurroError.SttVoicePackMissing` variant added to the sealed hierarchy and
  to CLAUDE.md's "Error handling" table.
- [ ] `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
  declared in main `AndroidManifest.xml` with a comment pointing to SF-2.1.
- [ ] `di/VoiceModule.kt` exists with `@Binds @Singleton SttClient ->
  SystemSttClient` (US-016 adds `TtsClient` later).
- [ ] `strings.xml`: `copy_stt_no_voice_pack` added.
- [ ] Brand-design COPY table updated (the dev edits
  `.claude/skills/brand-design/SKILL.md` to add the row).
- [ ] Unit tests under `app/src/test/java/com/curro/app/data/voice/SystemSttClientTest.kt`:
  - T1 — `Event.Partial` emitted for each partial.
  - T2 — `Event.Final` emitted on `onResults` non-empty.
  - T3 — `Event.Failed(SttNoMatch)` for `onResults` empty.
  - T4 — `onError(ERROR_NO_MATCH)` → `SttNoMatch`.
  - T5 — `onError(ERROR_SPEECH_TIMEOUT)` → `SttTimeout`.
  - T6 — `onError(ERROR_INSUFFICIENT_PERMISSIONS)` → `PermissionDenied`.
  - T7 — `onError(ERROR_LANGUAGE_NOT_SUPPORTED)` → `SttVoicePackMissing`.
  - T8 — `onError(ERROR_LANGUAGE_UNAVAILABLE)` → `SttVoicePackMissing`.
  - T9 — `onError(ERROR_NETWORK)` → `SttError(code = ERROR_NETWORK)`.
  - T10 — `onError(ERROR_AUDIO)` → `SttError(code = ERROR_AUDIO)`.
  - T11 — Partial → Final order preserved (the Flow emits partial(s) then final).
  - T12 — Cancelling the collecting coroutine triggers `awaitClose` → `cancel()` +
    `destroy()` on the fake recogniser.
- [ ] No regression: `./gradlew assembleDebug ktlintCheck detektDebug
  testDebugUnitTest` all green.
- [ ] No `INTERNET` permission added to the main manifest (offline-only contract).
- [ ] On the real Redmi 15 with the Spanish voice pack installed: "Hola Curro" is
  transcribed within ~1 s, **with airplane mode on** (verifies offline) — manual
  verification step, recorded in the verification checklist.

---

## 11. CRITICAL implementation note — `Dispatchers.Main.immediate`

The launcher already shipped **two bugs** caused by `callbackFlow` + `flowOn(IO)`
combinations where a main-thread API was called off-main:

- `796b5f4` — `StaticFavoriteAppsRepositoryImpl.observeFavorites`:
  `lifecycle.addObserver(...)` crashed because the `callbackFlow` body was on IO.
- `b77d789` — `InstalledAppsRepositoryImpl.observeAllLaunchable`: same bug, same
  fix.

`SpeechRecognizer` has the **same risk profile**:

- `SpeechRecognizer.createSpeechRecognizer(context)` must be called on the main
  thread.
- `startListening`, `stopListening`, `cancel`, `destroy` must all be called on the
  main thread.
- `RecognitionListener` callbacks are delivered on the main thread by the
  framework — but the `callbackFlow` body is where we *call* the recogniser.

**Therefore:**

1. The `callbackFlow { … }` body in `SystemSttClient.listen()` **must** execute on
   `Dispatchers.Main.immediate`. The terminal operator is `.flowOn(Dispatchers.Main.immediate)`
   — and that's the ONLY `flowOn` in the chain. Do not chain `.flowOn(ioDispatcher)`
   after it, and do not put the SR construction inside a `withContext(io)`.
2. There is no heavy blocking I/O to push to IO inside this `callbackFlow` — the
   recogniser is event-driven. If the dev finds a need for IO (e.g. file-system
   probe inside `hasOfflineSpanish`), wrap *that one call* in
   `withContext(ioDispatcher)`, never the whole flow.
3. The unit test T12 exercises `awaitClose` — if it ran on IO, the test would not
   catch the bug, but the device would crash on first cancel. Keep the
   `Dispatchers.Main.immediate` invariant explicit in the test by setting the test
   dispatcher to a `StandardTestDispatcher` that exposes the main-thread
   requirement (the dev decides — Mockk on `SpeechRecognizer` is fine; the point is
   the production code is on Main).

This single rule is the difference between SF-2.1 working in one session and
needing a fix-commit the day after. Loudly visible in the code: a `// CRITICAL:
SpeechRecognizer is main-thread-bound — see US-015 §11.` comment above the
`flowOn`.

---

## 12. Strings delta

| ID | Value | Status |
|----|-------|--------|
| `copy_stt_no_voice_pack` | `Falta el paquete de voz español. Díselo a Fran.` | **NEW** — add to `strings.xml` and brand-design's COPY table |

---

## 13. Test plan

**JVM unit tests** (`app/src/test/java/com/curro/app/data/voice/SystemSttClientTest.kt`):

The 12 cases in §10. Approach: instantiate `SystemSttClient` with a context; mock
`SpeechRecognizer` static `createSpeechRecognizer` via Mockk (`mockkStatic`) and
return a `SpeechRecognizer` mock whose `setRecognitionListener` captures the
listener so the test can fire `onPartialResults` / `onResults` / `onError` and
assert the emitted `Event`s via Turbine.

**No instrumented test** for this SF — the framework's `SpeechRecognizer` is
notoriously hard to test on an emulator, and the value lives in the mapping (which
JVM tests cover) and the on-device behaviour (verified manually in §10's last
acceptance).

**Verification checklist (US-017 will catch this in its integration brief):**

- Redmi 15, airplane mode ON, Spanish voice pack installed → SF-2.1 isolated test
  (a debug-only screen invoking `sttClient.listen().take(1)` and logging) → "Hola
  Curro" is in the Final event within 1 s of speech end.

---

## 14. Files changed

**New:**

- `app/src/main/java/com/curro/app/domain/repository/SttClient.kt`
- `app/src/main/java/com/curro/app/data/voice/SystemSttClient.kt`
- `app/src/main/java/com/curro/app/di/VoiceModule.kt`
- `app/src/test/java/com/curro/app/data/voice/SystemSttClientTest.kt`

**Modified:**

- `app/src/main/AndroidManifest.xml` — add `RECORD_AUDIO` declaration with comment.
- `app/src/main/res/values/strings.xml` — add `copy_stt_no_voice_pack`.
- `app/src/main/java/com/curro/app/domain/model/CurroError.kt` (or wherever the
  sealed hierarchy lives in the actual codebase — check; if it doesn't exist yet,
  create it now as a domain model) — add `SttVoicePackMissing`.
- `CLAUDE.md` — update the "Error handling" `CurroError` block to include
  `SttVoicePackMissing`.
- `.claude/skills/brand-design/SKILL.md` — add `copy_stt_no_voice_pack` to the
  canonical COPY table.

**Not touched:** `Color.kt`, `Type.kt`, `Shape.kt`, `CurroSpacing.kt`, `Dimens.kt`,
`CurroTheme.kt`, the launcher composables (US-017 modifies the ViewModel + screen,
not this SF).

---

## 15. Reference skills

- `voice-interaction` — STT conventions, the consecutive-failure policy (Phase 5
  wires it; Phase 2 ships only the signal).
- `platform-integrations` — `SpeechRecognizer` system specifics.
- `accessibility-patterns` — N/A (no UI in this SF; the consuming screen lives in
  US-017/018).
- `testing-patterns` — fake-listener pattern for JVM tests.
- `git-workflow` — commit scope `feat(voice):`.
