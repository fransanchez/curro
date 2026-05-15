# US-016 — SF-2.2 · `TtsClient` (Spanish `TextToSpeech`, slowed, barge-in)

> **Spec trace:** spec §4.6 (TTS layer), §14 (closed decision: male system default,
> ~10–15 % slower default rate), §6 (interrupt rule — applies in Phase 5; Phase 2
> just ships `stop()`)
> **Master-plan:** SF-2.2
> **Phase:** 2 — Voice pipeline
> **Depends on:** US-002 (Hilt DI), US-007 (CurroNavHost shell)
> **Size:** M

---

## 1. Goal

Wrap Android's `TextToSpeech` behind a `domain/repository/TtsClient` interface that
exposes a clean `suspend speak(text): SpeakResult` and a `stop()` for barge-in.
Spanish, **slowed by ~12 %** by default (the user benefits from slow speech), male
system voice preferred (spec §14 closed decision). The interrupt rule from spec §6
is not fully implemented until Phase 5 — Phase 2 ships only the `stop()` primitive
that Phase 5 calls.

This is the mirror of US-015. US-017 uses both clients together; US-018 paints the
visual.

---

## 2. Scope

**In scope:**

- The `TtsClient` interface in `domain/repository/`.
- A `SystemTtsClient` implementation in `data/voice/` around `TextToSpeech`.
- Spanish language, ~12 % slowed speech-rate default, system default male voice
  best-effort.
- `suspend fun speak(text, utteranceId): SpeakResult` semantics — `Completed`,
  `Cancelled` (barge-in), `Failed(CurroError)`.
- Hilt binding inside the existing `VoiceModule` (US-015 created it).
- Unit tests covering all four `SpeakResult` paths.

**Out of scope:**

- The settings-menu voice/rate/pitch picker (Phase 8).
- The Phase-5 interrupt-by-button orchestration (Phase 5 calls `stop()`).
- ElevenLabs / cloud fallback (Plan B, deferred to Phase 9 / validation review per
  spec §14).
- TTS-side language-pack auto-install — if Spanish is missing, surface the error;
  Fran installs it manually (decided here; documented in §9).

---

## 3. User flow (the only flow this SF participates in)

US-017 wires the actual flow. End-to-end shape:

1. (US-017) STT returns `Final(text)` → ViewModel transitions to `Speaking(text)`.
2. (US-016) `ttsClient.speak(text)` is called inside a coroutine.
3. (US-016) `SystemTtsClient` calls `tts.speak(text, QUEUE_FLUSH, params, utteranceId)`.
4. (US-016) `UtteranceProgressListener.onDone(id)` fires → the `suspend speak()`
   resumes with `SpeakResult.Completed`.
5. (US-017) ViewModel resolves `Speaking` → `Idle`.
6. Or (US-017) User presses mic again → barge-in: ViewModel cancels the speak
   coroutine → `SystemTtsClient` calls `tts.stop()` → `speak()` resumes with
   `SpeakResult.Cancelled`.

---

## 4. Function-catalog impact

**No catalog change.**

---

## 5. FSM states touched

**Provisional only.** Phase 2 uses a `ListeningState.Speaking(text)` (defined in
US-017). Phase 5's full FSM will reuse `ttsClient.speak()` from the `executing`
state and call `ttsClient.stop()` on interrupt. No interface change is anticipated
between Phase 2 and Phase 5.

---

## 6. Android system integrations & permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| *(none)* | `TextToSpeech` needs no permission | — | — |

**No manifest delta.**

**Integration:** `android.speech.tts.TextToSpeech` (framework) +
`UtteranceProgressListener`. `setLanguage(Locale("es", "ES"))`. No external dep.

---

## 7. On-device-model impact

**No model impact.** TTS is Android-framework speech synthesis, not a Gemma model.

---

## 8. Android specification

### 8.1 The interface — `domain/repository/TtsClient.kt`

```kotlin
package com.curro.app.domain.repository

import com.curro.app.domain.model.CurroError

/**
 * Spanish text-to-speech.
 *
 * `speak` is suspending and resolves when the utterance finishes, is cancelled by
 * `stop` (barge-in), or errors. The implementation handles main-thread
 * marshalling for the underlying TextToSpeech.
 */
interface TtsClient {

    /**
     * Speak [text] in Spanish at the configured rate. Suspends until the
     * utterance reaches a terminal state.
     *
     * @param text The Spanish utterance. Must not be empty.
     * @param utteranceId A unique identifier for this utterance (used by the
     *     framework's progress listener). Default generates a UUID.
     */
    suspend fun speak(text: String, utteranceId: String = java.util.UUID.randomUUID().toString()): SpeakResult

    /** Interrupt any in-flight utterance. Idempotent. */
    fun stop()

    /** True while an utterance is being synthesised/played. */
    fun isSpeaking(): Boolean

    sealed interface SpeakResult {
        data object Completed : SpeakResult
        data object Cancelled : SpeakResult                          // barge-in
        data class Failed(val error: CurroError) : SpeakResult
    }
}
```

### 8.2 The implementation — `data/voice/SystemTtsClient.kt`

```kotlin
package com.curro.app.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.repository.TtsClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
internal class SystemTtsClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsClient {

    private val initDeferred = CompletableDeferred<TtsInitResult>()
    private var tts: TextToSpeech? = null

    init {
        // TextToSpeech construction must happen on main thread for safety on some OEMs.
        // The init callback also fires on main.
        tts = TextToSpeech(context) { status ->
            val ttsRef = tts
            if (status != TextToSpeech.SUCCESS || ttsRef == null) {
                initDeferred.complete(TtsInitResult.Failed(CurroError.TtsLanguageMissing))
                return@TextToSpeech
            }
            val langResult = ttsRef.setLanguage(Locale("es", "ES"))
            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                initDeferred.complete(TtsInitResult.Failed(CurroError.TtsLanguageMissing))
                return@TextToSpeech
            }
            ttsRef.setSpeechRate(SPEECH_RATE)
            ttsRef.setPitch(1.0f)

            // Best-effort: prefer a male Spanish voice.
            ttsRef.voices?.firstOrNull { v ->
                v.locale.language == "es" &&
                    v.name.contains("male", ignoreCase = true)
            }?.let { ttsRef.voice = it }

            initDeferred.complete(TtsInitResult.Ready)
        }
    }

    override suspend fun speak(text: String, utteranceId: String): TtsClient.SpeakResult {
        require(text.isNotEmpty()) { "TtsClient.speak called with empty text" }
        val init = initDeferred.await()
        if (init is TtsInitResult.Failed) return TtsClient.SpeakResult.Failed(init.error)

        val current = tts ?: return TtsClient.SpeakResult.Failed(CurroError.TtsLanguageMissing)

        return suspendCancellableCoroutine { cont ->
            val listener = object : UtteranceProgressListener() {
                override fun onStart(id: String) = Unit
                override fun onDone(id: String) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(TtsClient.SpeakResult.Completed)
                    }
                }
                @Deprecated("framework keeps this deprecated overload alive")
                override fun onError(id: String) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(TtsClient.SpeakResult.Failed(CurroError.TtsError(-1)))
                    }
                }
                override fun onError(id: String, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(TtsClient.SpeakResult.Failed(CurroError.TtsError(errorCode)))
                    }
                }
                override fun onStop(id: String, interrupted: Boolean) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resume(TtsClient.SpeakResult.Cancelled)
                    }
                }
            }
            current.setOnUtteranceProgressListener(listener)
            current.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

            cont.invokeOnCancellation { current.stop() }
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun isSpeaking(): Boolean = tts?.isSpeaking == true

    private sealed interface TtsInitResult {
        data object Ready : TtsInitResult
        data class Failed(val error: CurroError) : TtsInitResult
    }

    private companion object {
        /** ~12 % slower than default (1.0). Spec §14 says "10–15 % slower". 0.88 sits mid-band. */
        const val SPEECH_RATE: Float = 0.88f
    }
}
```

The dev may simplify (e.g. drop `isSpeaking()` to a stub if Phase 2 doesn't need
it — US-017's barge-in uses cancellation, not a poll). What's binding: the
suspend semantics, the four `SpeakResult` paths, the rate of 0.88, the male-voice
best-effort selection.

### 8.3 New `CurroError` variants

CLAUDE.md "Error handling" table currently has no TTS variants. **Add two:**

```kotlin
data object TtsLanguageMissing : CurroError       // setLanguage returned LANG_MISSING_DATA / LANG_NOT_SUPPORTED
data class  TtsError(val code: Int) : CurroError  // UtteranceProgressListener.onError
```

These map to:

- `TtsLanguageMissing` → user-facing copy: "Falta la voz española. Díselo a Fran."
  (new `copy_tts_no_voice_pack` if surfaced to user; deferred to whoever first
  needs to surface it — Phase 2 logs to `Log.w` and US-017's `Error` state shows
  the closest existing line).
- `TtsError(code)` → user-facing copy: silent if Phase 2 (just log). Phase 5 will
  surface it.

Add the variants now; surface them on demand.

### 8.4 Hilt module — extend `VoiceModule`

`di/VoiceModule.kt` (created in US-015) adds:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
interface VoiceModule {
    @Binds @Singleton
    fun bindSttClient(impl: SystemSttClient): SttClient

    // US-016 addition:
    @Binds @Singleton
    fun bindTtsClient(impl: SystemTtsClient): TtsClient
}
```

### 8.5 Lifecycle — `shutdown()`?

`TextToSpeech.shutdown()` releases native resources. Because `SystemTtsClient` is
`@Singleton` and lives for the process lifetime, the Android process kill releases
the native resources automatically.

**Decision (pinned here, not deferred):** no explicit `shutdown()` hook. The
`@Singleton` lives until process death; the native instance is released by the
process kill; the loss is negligible (a few KB of native memory plus the audio
service binding). If a future task needs deterministic shutdown (e.g. a tear-down
hook for tests), the dev adds a `dispose()` method and a `@PreDestroy`-like
pattern — not in this SF.

---

## 9. Senior-UX & copy

This SF ships **one new Spanish string** (Phase-2 dev affordance — retired in
Phase 5):

| ID | Value | Notes |
|----|-------|-------|
| `copy_tts_smoke_test` | `Hola, soy Curro.` | NEW (US-016). Phase-2-only smoke test — used by US-017's startup test or by a debug menu (the dev decides). NOT in canonical COPY table; flagged for deletion in Phase 5 when the real assistant flow replaces the smoke test. |

Voice character (spec §2 — non-negotiable): warm, Andalusian, colloquial. The
`0.88` rate is the only quantitative dial; the qualitative character ("close, not
servile") is set by the COPY table (US-005). TTS doesn't choose words — it speaks
them. The strings already in the COPY table (`copy_calling`, `copy_no_unread`,
etc.) are in Curro's voice; TTS faithfully renders them.

---

## 10. Acceptance criteria

- [ ] `TtsClient.kt` interface exists at
  `app/src/main/java/com/curro/app/domain/repository/TtsClient.kt` with the
  signature in §8.1 (`suspend speak`, `stop`, `isSpeaking`, sealed `SpeakResult`).
- [ ] `SystemTtsClient.kt` exists at
  `app/src/main/java/com/curro/app/data/voice/SystemTtsClient.kt`, `@Singleton`,
  `@Inject constructor(@ApplicationContext context: Context)`.
- [ ] `TextToSpeech` is lazy-inited in the `init` block; init callback drives the
  `initDeferred` `CompletableDeferred`; `speak` `await()`s it before invoking
  `tts.speak(...)`.
- [ ] `setLanguage(Locale("es", "ES"))` is called; `LANG_MISSING_DATA` /
  `LANG_NOT_SUPPORTED` resolves `initDeferred` with `TtsInitResult.Failed`.
- [ ] `setSpeechRate(0.88f)`; `setPitch(1.0f)`.
- [ ] Voice selection best-effort: prefer a male `es` voice via
  `tts.voices.firstOrNull { … }`; fall back to the system default if none.
- [ ] `speak(text, utteranceId)` uses `suspendCancellableCoroutine`;
  `UtteranceProgressListener.onDone` → `Completed`; `onError(id, code)` →
  `Failed(TtsError(code))`; `onStop(_, interrupted)` → `Cancelled`;
  `invokeOnCancellation { tts.stop() }`.
- [ ] `stop()` calls `tts.stop()` synchronously.
- [ ] `CurroError.TtsLanguageMissing` and `CurroError.TtsError(code)` variants
  added to the sealed hierarchy and CLAUDE.md "Error handling".
- [ ] `di/VoiceModule.kt` adds `@Binds @Singleton TtsClient -> SystemTtsClient`
  (same module US-015 created).
- [ ] `strings.xml`: `copy_tts_smoke_test` added with a comment marking it as
  Phase-2-only.
- [ ] Unit tests in `app/src/test/java/com/curro/app/data/voice/SystemTtsClientTest.kt`:
  - T1 — happy path: `onDone(id)` resumes `SpeakResult.Completed`.
  - T2 — barge-in: coroutine cancellation invokes `tts.stop()`; `onStop` resumes
    `SpeakResult.Cancelled`.
  - T3 — native error: `onError(id, 42)` resumes `SpeakResult.Failed(TtsError(42))`.
  - T4 — language missing: init callback with `LANG_NOT_SUPPORTED` →
    `speak(...)` resolves `Failed(TtsLanguageMissing)` immediately.
  - T5 — id mismatch: `onDone("other-id")` does NOT resume — the coroutine waits
    for its own id.
- [ ] On the real Redmi 15: `tts.speak("Hola, soy Curro")` is intelligible, at
  the slowed rate; a `stop()` call within speech ends audio within ~50 ms
  (manual verification step; SF-2.3 wires the harness that exercises it).
- [ ] No new permission, no manifest change.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green.

---

## 11. CRITICAL implementation note — main-thread discipline

`TextToSpeech` is **less** strict than `SpeechRecognizer`, but the same family of
risk applies:

- `TextToSpeech(context, onInitListener)` construction is safe from any thread,
  but the `OnInitListener` callback is delivered on the main thread.
- `tts.speak`, `tts.stop`, `setLanguage`, `setVoice`, `setSpeechRate` are all
  thread-safe in modern Android, but `setOnUtteranceProgressListener`'s callbacks
  are delivered on a synthesis thread (NOT main).

**Therefore:**

- The `UtteranceProgressListener` callbacks in the `suspendCancellableCoroutine`
  must use `cont.resume(...)` — which is thread-safe — without touching any UI
  state. They do already; this is just the explicit guarantee.
- Construction in the `init` block is fine — it's a `@Singleton` construction
  context, runs once at injection time.
- No `flowOn(Dispatchers.Main.immediate)` needed (no `callbackFlow` involved).

This is **less stringent** than US-015 — TTS is the safer half of the pair. The
critical-note section is short here on purpose; the SttClient brief carries the
heavy version of the lesson.

---

## 12. Strings delta

| ID | Value | Status |
|----|-------|--------|
| `copy_tts_smoke_test` | `Hola, soy Curro.` | **NEW** — Phase-2 dev affordance; NOT in canonical COPY table; flagged for deletion in Phase 5 |

---

## 13. Test plan

**JVM unit tests** (`app/src/test/java/com/curro/app/data/voice/SystemTtsClientTest.kt`):

Mockk-fake the `TextToSpeech` instance. Capture the `UtteranceProgressListener`
passed to `setOnUtteranceProgressListener`; the test fires the listener callbacks
to drive the `suspend speak` to its four terminal states. The 5 cases in §10.

**Approach:** the dev injects a `TextToSpeech` factory (an interface with a single
`create(context, listener): TextToSpeech` method) into `SystemTtsClient`'s
constructor so the test can supply a Mockk fake. The production binding provides
the real `TextToSpeech(context, listener)` constructor.

**Note:** if the dev prefers Robolectric over a TTS factory abstraction, that's
fine — Robolectric shadows `TextToSpeech`. Either approach passes the acceptance
criteria; tests are clearer with the factory pattern.

**No instrumented test** for this SF — the on-device behaviour is verified
manually as part of US-017's acceptance.

---

## 14. Files changed

**New:**

- `app/src/main/java/com/curro/app/domain/repository/TtsClient.kt`
- `app/src/main/java/com/curro/app/data/voice/SystemTtsClient.kt`
- `app/src/test/java/com/curro/app/data/voice/SystemTtsClientTest.kt`
- (Optional) `app/src/main/java/com/curro/app/data/voice/TextToSpeechFactory.kt` if
  the dev chooses the factory-abstraction approach.

**Modified:**

- `app/src/main/java/com/curro/app/di/VoiceModule.kt` — add the `TtsClient`
  binding.
- `app/src/main/res/values/strings.xml` — add `copy_tts_smoke_test`.
- `app/src/main/java/com/curro/app/domain/model/CurroError.kt` (or wherever the
  sealed hierarchy lives) — add `TtsLanguageMissing`, `TtsError(code)`.
- `CLAUDE.md` — update the `CurroError` block in the "Error handling" section.

**Not touched:** `AndroidManifest.xml` (no permission), `Color.kt`, `Type.kt`, any
launcher composable.

---

## 15. Reference skills

- `voice-interaction` — TTS conventions, the slowed-rate decision, the barge-in
  rule (full implementation Phase 5; this SF ships `stop()`).
- `platform-integrations` — N/A directly; `TextToSpeech` is in the broader Android
  framework, not a Curro-specific integration.
- `accessibility-patterns` — N/A (no UI).
- `testing-patterns` — fake-listener pattern; factory abstraction or Robolectric.
- `git-workflow` — commit scope `feat(voice):`.
