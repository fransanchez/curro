# US-017 — SF-2.3 · End-to-end press → speak → see transcript → hear echo loop

> **Spec trace:** spec §4.1 (capture), §4.2 (STT), §4.6 (TTS), §6 closed
> "interrupt rule" (applies from Phase 5; this SF ships an early version that
> serves as the harness), §14 step 2 ("validar voz" gate — does the loop feel
> responsive on the real Redmi 15?)
> **Master-plan:** SF-2.3
> **Phase:** 2 — Voice pipeline
> **Depends on:** US-015 (SttClient), US-016 (TtsClient), US-011 (MicButton)
> **Size:** M

---

## 1. Goal

Wire the existing mic button to the real STT and TTS pipeline so a press on the
Redmi 15 starts listening, the partial transcript appears live, the final
transcript is spoken back by Curro, and a second press interrupts him. **No
FunctionGemma yet** — this SF echoes what was heard, deliberately.

The validation outcome is binary: does the loop *feel* responsive, and is the
Spanish TTS voice intelligible enough to keep using? Pass that gate and Phase 3
(FunctionGemma) becomes worth investing in; fail it and we re-think TTS (Plan B:
ElevenLabs) before doing anything else.

---

## 2. Scope

**In scope:**

- Provisional `ListeningState` sealed interface inside `LauncherUiState`.
- `LauncherViewModel.onMicPressed` replaced from "show toast" to "barge-in OR
  request permission OR start listening".
- New `LauncherEvent.RecordAudioPermissionResult(granted)`.
- New `LauncherSideEffect.RequestRecordAudio`.
- Permission-request launcher registered in `LauncherPlaceholderScreen`.
- `viewModelScope` coroutine that collects `SttClient.listen()` → updates
  `ListeningState` → on `Final` calls `TtsClient.speak(text)` → on speak result
  returns to `Idle`.
- Barge-in: second press while `listening` or `speaking` cancels the active job
  and restarts listening.
- Error display: any `CurroError` from the pipeline maps to a Spanish copy and
  shows for ~2.5 s, then returns to `Idle`. (Phase 5 replaces this with the
  1st/2nd/3rd-fail counter.)
- `AnimatedVisibility` wrap of the (US-018) `ListeningOverlay` on top of the
  launcher home.
- Deletion of `copy_mic_inert` (US-011 placeholder) + the `ShowToast`
  side-effect emission for it.

**Out of scope:**

- The full `AssistantStateMachine` (Phase 5).
- The proper 1st/2nd/3rd consecutive-failure counter (Phase 5).
- The 10-second silence cancel in `confirming` (no `confirming` state in Phase 2).
- `FunctionGemma`, `Gemma 3n`, any handler (Phase 3+).
- The visual polish of `ListeningOverlay` — its shell exists in this SF only as
  a placeholder; the proper composable lands in US-018.

---

## 3. User flows

### Flow 1 — Happy path: press → speak → echo

1. User presses the mic button → `LauncherEvent.MicPressed` → ViewModel checks
   `RECORD_AUDIO` permission → already granted → starts listening.
2. ViewModel sets `listeningState = ListeningState.Starting`; emits no side
   effect.
3. ViewModel launches a coroutine that calls `sttClient.listen().collect { … }`.
4. `Event.Partial(text)` → `listeningState = ListeningState.Listening(text)` —
   the overlay shows the partial transcript live.
5. User stops speaking → `Event.Final(text)` → coroutine sets
   `listeningState = ListeningState.Speaking(text)` and launches
   `ttsClient.speak(text)` (`Spanish, slowed`).
6. `SpeakResult.Completed` → `listeningState = ListeningState.Idle` — overlay
   fades out, launcher home is visible.

### Flow 2 — Barge-in: press again while Curro is speaking

1. From `Speaking(text)`: user presses mic again.
2. ViewModel cancels the active speak job (and any still-active STT job, though
   STT should already be closed here) and transitions to `Starting` →
   `Listening("")`.
3. New STT session starts. (The TTS `SpeakResult.Cancelled` is received and
   discarded — `Speaking` was already replaced.)

### Flow 3 — Permission missing on first press

1. From `Idle`, user presses mic, `RECORD_AUDIO` is denied.
2. ViewModel emits `LauncherSideEffect.RequestRecordAudio`.
3. Screen fires the `ActivityResultLauncher` for the permission.
4. System permission dialog shows. User taps Allow → `granted = true` →
   `LauncherEvent.RecordAudioPermissionResult(true)` → ViewModel enters the
   listen flow (Flow 1 from step 2).
5. Or user taps Deny → `granted = false` → `listeningState =
   ListeningState.Error(copy_perm_missing_mic)` → after 2.5 s → `Idle`. Curro
   also speaks the line via TTS (audio + visual together, spec §4.6).

### Flow 4 — STT empty / error

1. From `Listening(text)`: STT emits `Event.Failed(error)`.
2. ViewModel maps `error` → Spanish copy:
   - `SttNoMatch`, `SttTimeout`, any other `Stt*` → `copy_stt_fail_1` ("No te
     he oído bien, ¿puedes repetirlo?")
   - `PermissionDenied` → `copy_perm_missing_mic`
   - `SttVoicePackMissing` → `copy_stt_no_voice_pack`
3. `listeningState = ListeningState.Error(message)`; the overlay shows it AND
   Curro speaks it (via `ttsClient.speak(message)`).
4. After speak completes (or after a 2.5-s timeout, whichever first) →
   `listeningState = ListeningState.Idle`.

(Phase 5's 1st/2nd/3rd-fail counter replaces step 2's "always
`copy_stt_fail_1`". For Phase 2 the simple version suffices.)

### Flow 5 — Final returned but TTS fails

1. From `Speaking(text)`: `ttsClient.speak(text)` returns `Failed(error)`.
2. ViewModel logs to `Log.w("Curro", error)` and transitions silently to
   `Idle` — there is no good UX for "I heard you but I can't speak". (Phase 5
   surfaces this; Phase 2's goal is to validate the loop, and a silent failure
   in TTS is acceptable here — the transcript was already shown.)

---

## 4. Function-catalog impact

**No catalog change.**

---

## 5. FSM states touched

This SF is the **first time** the launcher carries assistant-pipeline state. It
introduces a **provisional FSM** scoped to the launcher screen, NOT the global
`AssistantStateMachine` from Phase 5.

The provisional `ListeningState` is documented in §6.1 below. Its states map to
the eventual full FSM as:

| Provisional `ListeningState` | Phase-5 `AssistantState` |
|---|---|
| `Idle` | `idle` |
| `Starting` | `listening` (sub-state, before audio capture begins) |
| `Listening(partialText)` | `listening` |
| `Speaking(text)` | `executing` (and `processing` becomes a separate state) |
| `Error(message)` | `error_recovery` |

The mapping is forward-compatible: Phase 5 absorbs and reshapes; no Phase-2 work
is wasted, but the provisional state lives on `LauncherUiState`, not on a global
flow. **Loudly flag in code:** every reference to `ListeningState` carries a
`// PROVISIONAL (US-017) — Phase 5 replaces with AssistantStateMachine` comment.

**Interrupt rule (early version):** the second-press barge-in is implemented in
this SF — Phase 5 expands it to all states. This is intentional: catching the
barge-in early means we discover edge cases (`SpeechRecognizer` already destroyed,
TTS `SpeakResult.Cancelled` racing with a new STT session) on Phase 2 hardware,
not Phase 5.

---

## 6. Android system integrations & permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `RECORD_AUDIO` | STT (US-015) | first mic press (in `Idle`, when not granted) | `Error(copy_perm_missing_mic)` → after 2.5 s → `Idle` |

The permission is **declared in the manifest by US-015**; this SF wires the
runtime request via `ActivityResultContracts.RequestPermission`.

**Integration delta:**

- `LauncherPlaceholderScreen` registers the permission `ActivityResultLauncher`
  inside a `LaunchedEffect`-paired pattern (registered with the activity result
  registry; survives configuration changes via `rememberLauncherForActivityResult`).

---

## 7. On-device-model impact

**No model impact.** Echo is just `ttsClient.speak(sttFinalText)`. FunctionGemma
lands in Phase 3.

---

## 8. Android specification

### 8.1 `ListeningState` sealed interface

Lives inside `LauncherUiState` (do NOT create a separate file; the state is
scoped to the launcher screen):

```kotlin
data class LauncherUiState(
    val isCurroDefault: Boolean,
    val clock: ClockState,
    val favorites: List<FavoriteApp> = emptyList(),
    val listeningState: ListeningState = ListeningState.Idle, // NEW (US-017)
)

/**
 * Provisional listening/speaking state for SF-2.3.
 * Phase 5 (SF-5.1) replaces this with the full AssistantStateMachine.
 */
sealed interface ListeningState {
    data object Idle : ListeningState

    /** Permission granted, STT session not yet emitting partials. */
    data object Starting : ListeningState

    /** STT is emitting partials. [partialText] is the most recent. */
    data class Listening(val partialText: String) : ListeningState

    /** Curro is echoing [text] via TTS. */
    data class Speaking(val text: String) : ListeningState

    /** Recoverable error: [message] is the spoken+shown Spanish line. */
    data class Error(val message: String) : ListeningState
}
```

### 8.2 `LauncherViewModel` changes

The full diff (sketch — the dev writes the actual code):

```kotlin
@HiltViewModel
class LauncherViewModel @Inject constructor(
    detector: DefaultLauncherDetector,
    observeClock: ObserveClockUseCase,
    favoritesRepo: FavoriteAppsRepository,
    private val sttClient: SttClient,            // NEW
    private val ttsClient: TtsClient,            // NEW
    private val recordAudioPermission: PermissionGate,  // NEW — see §8.4
) : ViewModel() {

    private val _uiState = MutableStateFlow(...)
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private var voiceJob: Job? = null

    fun onEvent(event: LauncherEvent) {
        when (event) {
            is LauncherEvent.MicPressed -> onMicPressed()
            is LauncherEvent.AppTileTapped -> onAppTileTapped(event.packageName)
            is LauncherEvent.ClockTapped -> onClockTapped()
            is LauncherEvent.RecordAudioPermissionResult -> onPermissionResult(event.granted)
        }
    }

    private fun onMicPressed() {
        val current = _uiState.value.listeningState

        // Barge-in: any non-Idle state cancels and restarts.
        if (current !is ListeningState.Idle) {
            voiceJob?.cancel()
            voiceJob = null
            _uiState.update { it.copy(listeningState = ListeningState.Idle) }
            // Start fresh — fall through to the granted/denied flow.
        }

        if (!recordAudioPermission.isGranted()) {
            viewModelScope.launch {
                _sideEffects.send(LauncherSideEffect.RequestRecordAudio)
            }
            return
        }
        startListening()
    }

    private fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startListening()
        } else {
            transientError(R.string.copy_perm_missing_mic)
        }
    }

    private fun startListening() {
        _uiState.update { it.copy(listeningState = ListeningState.Starting) }
        voiceJob = viewModelScope.launch {
            try {
                sttClient.listen().collect { event ->
                    when (event) {
                        is SttClient.Event.Partial ->
                            _uiState.update { it.copy(listeningState = ListeningState.Listening(event.text)) }
                        is SttClient.Event.Final -> {
                            _uiState.update { it.copy(listeningState = ListeningState.Speaking(event.text)) }
                            // Speak the echo. Cancellation propagates to the TTS via
                            // suspendCancellableCoroutine's invokeOnCancellation.
                            ttsClient.speak(event.text)
                            _uiState.update { it.copy(listeningState = ListeningState.Idle) }
                        }
                        is SttClient.Event.Failed -> {
                            val msg = errorMessage(event.error)
                            _uiState.update { it.copy(listeningState = ListeningState.Error(msg)) }
                            // Spoken + shown.
                            ttsClient.speak(msg)
                            delay(2500)
                            _uiState.update {
                                if (it.listeningState is ListeningState.Error) {
                                    it.copy(listeningState = ListeningState.Idle)
                                } else it
                            }
                        }
                    }
                }
            } catch (ce: CancellationException) {
                // Barge-in. Don't reset state — onMicPressed already did, or a new
                // startListening() is taking over.
                throw ce
            }
        }
    }

    private fun transientError(@StringRes resId: Int) {
        val msg = appContext.getString(resId)  // injected via @ApplicationContext
        _uiState.update { it.copy(listeningState = ListeningState.Error(msg)) }
        viewModelScope.launch {
            ttsClient.speak(msg)
            delay(2500)
            _uiState.update {
                if (it.listeningState is ListeningState.Error) {
                    it.copy(listeningState = ListeningState.Idle)
                } else it
            }
        }
    }

    @StringRes
    private fun errorMessage(error: CurroError): String = when (error) {
        is CurroError.PermissionDenied         -> appContext.getString(R.string.copy_perm_missing_mic)
        is CurroError.SttVoicePackMissing      -> appContext.getString(R.string.copy_stt_no_voice_pack)
        is CurroError.SttNoMatch,
        is CurroError.SttTimeout,
        is CurroError.SttError                 -> appContext.getString(R.string.copy_stt_fail_1)
        else                                   -> appContext.getString(R.string.copy_stt_fail_1)
    }
}
```

Notes the dev needs to handle:

- Inject `@ApplicationContext appContext: Context` into the ViewModel (Hilt
  pattern — `@ApplicationContext` qualifier on the constructor parameter). The
  ViewModel touches `getString(resId)` because the error message must be
  spoken *and* shown — both need a `String`. Acceptable in a ViewModel because
  resource access doesn't drag in lifecycle/UI.
- `voiceJob?.cancel()` must run before re-entry into `startListening()` — the
  barge-in path does this; verify via tests.

### 8.3 `LauncherEvent` and `LauncherSideEffect` additions

```kotlin
sealed interface LauncherEvent {
    data object MicPressed : LauncherEvent
    data class AppTileTapped(val packageName: String) : LauncherEvent
    data object ClockTapped : LauncherEvent
    /** SF-2.3 (US-017) — result of the runtime RECORD_AUDIO request. */
    data class RecordAudioPermissionResult(val granted: Boolean) : LauncherEvent
}

sealed interface LauncherSideEffect {
    data class ShowToast(val messageResId: Int) : LauncherSideEffect
    data class LaunchApp(val packageName: String) : LauncherSideEffect
    data object OpenConfig : LauncherSideEffect
    /** SF-2.3 (US-017) — ask the screen to fire its ActivityResultLauncher. */
    data object RequestRecordAudio : LauncherSideEffect
}
```

### 8.4 `PermissionGate` (small abstraction so the ViewModel doesn't import `Context`-bound permission code)

```kotlin
// data/permissions/PermissionGate.kt
interface PermissionGate {
    fun isGranted(): Boolean
}

// data/permissions/RecordAudioPermissionGate.kt
@Singleton
internal class RecordAudioPermissionGate @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionGate {
    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
```

Bound in a new `PermissionsModule` (or reuse `VoiceModule`):

```kotlin
@Module
@InstallIn(SingletonComponent::class)
interface PermissionsModule {
    @Binds @Singleton
    fun bindRecordAudioGate(impl: RecordAudioPermissionGate): PermissionGate
}
```

The dev may inline this in `VoiceModule` instead — naming is the dev's call;
what's binding is the interface so the ViewModel doesn't import
`ContextCompat`.

### 8.5 `LauncherPlaceholderScreen` changes

```kotlin
@Composable
fun LauncherPlaceholderScreen(
    onOpenConfig: () -> Unit,
    onMakeDefault: () -> Unit,
    onNavigateToMoreApps: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LauncherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // NEW: permission launcher (US-017).
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onEvent(LauncherEvent.RecordAudioPermissionResult(granted))
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is LauncherSideEffect.ShowToast ->
                    Toast.makeText(context, effect.messageResId, Toast.LENGTH_SHORT).show()
                is LauncherSideEffect.LaunchApp -> { /* existing */ }
                is LauncherSideEffect.OpenConfig -> onOpenConfig()
                // NEW (US-017):
                is LauncherSideEffect.RequestRecordAudio ->
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LauncherPlaceholderContent(
        uiState = uiState,
        onMakeDefault = onMakeDefault,
        onMicPressed = { viewModel.onEvent(LauncherEvent.MicPressed) },
        onClockTapped = { viewModel.onEvent(LauncherEvent.ClockTapped) },
        onTileTapped = { pkg -> viewModel.onEvent(LauncherEvent.AppTileTapped(pkg)) },
        onNotInstalled = {
            Toast.makeText(context, R.string.copy_app_not_installed, Toast.LENGTH_SHORT).show()
        },
        onNavigateToMoreApps = onNavigateToMoreApps,
        modifier = modifier,
    )
}
```

`LauncherPlaceholderContent` wraps the existing column body inside a `Box`,
overlays the `ListeningOverlay` (from US-018) with `AnimatedVisibility`:

```kotlin
@Composable
internal fun LauncherPlaceholderContent(
    uiState: LauncherUiState,
    onMakeDefault: () -> Unit,
    onMicPressed: () -> Unit,
    onClockTapped: () -> Unit,
    onTileTapped: (String) -> Unit = {},
    onNotInstalled: () -> Unit = {},
    onNavigateToMoreApps: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Existing column — unchanged except for passing isListening to MicButton.
        Column( ... ) {
            ClockBlock(...)
            // ...
            MicButton(
                onPressed = onMicPressed,
                isListening = uiState.listeningState !is ListeningState.Idle,
                modifier = Modifier.padding(horizontal = CurroSpacing.l),
            )
            // ...
        }

        // NEW (US-017 + US-018): the listening overlay.
        AnimatedVisibility(
            visible = uiState.listeningState !is ListeningState.Idle,
            enter = fadeIn(animationSpec = tween(150)),
            exit  = fadeOut(animationSpec = tween(150)),
        ) {
            ListeningOverlay(
                state = uiState.listeningState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
```

The `ListeningOverlay` composable is **defined in US-018**. For US-017 to build,
US-018 must land in the same SF-2.x batch (the user said: PM in one pass, dev
in one pass after).

### 8.6 Deletion of `copy_mic_inert`

`copy_mic_inert` was a Phase-1 placeholder (US-011 §6). SF-2.3 replaces the
behaviour entirely. Therefore:

- Delete `<string name="copy_mic_inert">…</string>` from `strings.xml`.
- Delete the `is LauncherSideEffect.ShowToast -> /* … */` arm that emits
  `ShowToast(R.string.copy_mic_inert)` (specifically the one in `onMicPressed`).
- `LauncherSideEffect.ShowToast` itself **remains** — it's still used for the
  uninstalled-app-tile toast (`R.string.copy_app_not_installed`).
- The comment about US-011 in `LauncherViewModel.onMicPressed` ("Phase 1 — inert
  …") is replaced by the SF-2.3 docblock.

---

## 9. Senior-UX & copy

Strings reused (all already in `strings.xml`):

- `copy_perm_missing_mic` — "Necesito permiso para escucharte. Díselo a Fran."
- `copy_stt_fail_1` — "No te he oído bien, ¿puedes repetirlo?"
- `copy_stt_no_voice_pack` — "Falta el paquete de voz español. Díselo a Fran."
  (added by US-015)

Strings deleted: `copy_mic_inert`.

No NEW strings in this SF. The flow is end-to-end with existing copy.

Senior-UX checklist:

- The visual overlay is large, blue-tinted, single-purpose (US-018).
- Every Curro→user message is spoken AND shown (Flow 3 step 5, Flow 4 step 3).
- Permission denial does not crash; it speaks a plain Spanish line.
- Barge-in is one button press (the same button the user already knows).
- No layout shift on the launcher home — the overlay sits *on top* (Box +
  `fillMaxSize`), not in-flow.

---

## 10. Acceptance criteria

- [ ] `ListeningState` sealed interface defined in `LauncherViewModel.kt`
  (alongside `LauncherUiState`); five variants — `Idle | Starting | Listening |
  Speaking | Error` — with the field types in §8.1.
- [ ] `LauncherUiState` gains `listeningState: ListeningState = Idle`.
- [ ] `LauncherEvent.RecordAudioPermissionResult(granted)` added.
- [ ] `LauncherSideEffect.RequestRecordAudio` added.
- [ ] `LauncherViewModel` injects `SttClient`, `TtsClient`, `PermissionGate`
  (`@ApplicationContext` for the gate impl), and `@ApplicationContext Context`
  for `getString`.
- [ ] `onMicPressed`: if `listeningState != Idle`, cancel `voiceJob`, set state
  to `Idle`, then continue with the permission check (barge-in restart). If
  granted, `startListening()`; if denied, emit `RequestRecordAudio`.
- [ ] `onPermissionResult(true)` → `startListening()`.
- [ ] `onPermissionResult(false)` → `transientError(R.string.copy_perm_missing_mic)`.
- [ ] `startListening()` launches a `viewModelScope` job that collects
  `sttClient.listen()`; partial → `Listening(text)`; final → `Speaking(text)`,
  then `ttsClient.speak(text)`, then `Idle`; failed → `Error(message)`, speak,
  delay 2.5 s, `Idle` (guard against state-already-changed-by-barge-in).
- [ ] `errorMessage(error)` maps `CurroError.PermissionDenied` → `copy_perm_missing_mic`;
  `SttVoicePackMissing` → `copy_stt_no_voice_pack`;
  `SttNoMatch`/`SttTimeout`/`SttError` → `copy_stt_fail_1`.
- [ ] `LauncherPlaceholderScreen` registers `rememberLauncherForActivityResult`
  for `ActivityResultContracts.RequestPermission()`; `RequestRecordAudio` side
  effect launches it for `Manifest.permission.RECORD_AUDIO`; result delivers
  `RecordAudioPermissionResult(granted)` event.
- [ ] `LauncherPlaceholderContent` wraps the existing column in a `Box` and
  overlays the `ListeningOverlay` via `AnimatedVisibility`
  (`visible = listeningState !is Idle`), `fadeIn`/`fadeOut`
  `tween(150)`.
- [ ] `MicButton` receives `isListening = (listeningState !is Idle)`; the
  `MicButton` `isListening` parameter is added in US-018.
- [ ] `copy_mic_inert` deleted from `strings.xml`; the `ShowToast(copy_mic_inert)`
  emission removed from `onMicPressed`.
- [ ] No regression: clock, mic button (still pressable, still haptic), app
  tiles, "Más apps" route, the five-tap-on-clock gesture — all behave as before
  the SF.
- [ ] Press-to-listening latency on the Redmi 15 < 1 s (manual stopwatch
  verification).
- [ ] On the Redmi 15, airplane mode ON: "Hola Curro" → see partial → see final
  → hear Curro say "Hola Curro" back, in Spanish, at the slowed rate.
- [ ] On the Redmi 15, during TTS playback: a second mic press cuts speech
  within ~150 ms (audio stops) and re-enters listening.
- [ ] Unit tests in `LauncherViewModelTest.kt`:
  - T1 — `MicPressed` from `Idle` with permission granted →
    `startListening()` is called; `listeningState` becomes `Starting`.
  - T2 — `MicPressed` from `Idle` with permission denied → `RequestRecordAudio`
    side effect emitted; `listeningState` stays `Idle`.
  - T3 — `RecordAudioPermissionResult(true)` → listening flow proceeds.
  - T4 — `RecordAudioPermissionResult(false)` → `Error(copy_perm_missing_mic)`;
    `ttsClient.speak` invoked with the message; after the speak completes (or
    after 2.5 s), state → `Idle`.
  - T5 — STT emits `Partial` → state becomes `Listening(text)`.
  - T6 — STT emits `Final` → state becomes `Speaking(text)`; `ttsClient.speak`
    invoked with the text; on `Completed`, state → `Idle`.
  - T7 — STT emits `Failed(SttNoMatch)` → state becomes `Error(copy_stt_fail_1)`;
    after 2.5 s → `Idle`.
  - T8 — `MicPressed` while in `Speaking` → previous `voiceJob` cancelled; state
    → `Idle`; if permission granted, a NEW `voiceJob` starts; state → `Starting`.
  - T9 — `MicPressed` while in `Listening` → previous `voiceJob` cancelled;
    same restart as T8.
  - T10 — `voiceJob.cancel()` propagates to the in-flight `ttsClient.speak()`
    (verified by Mockk verifying `ttsClient.stop()` is called).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green.

---

## 11. CRITICAL implementation note — cancellation correctness

The barge-in restart in `onMicPressed` is the easiest thing to get subtly wrong:

1. `voiceJob?.cancel()` is **non-blocking** — the job is marked cancelled but
   the suspending calls (`sttClient.listen().collect`, `ttsClient.speak`) only
   actually return on the next suspension point.
2. The new `startListening()` call must NOT run before the previous job has
   actually cleaned up its `SpeechRecognizer` (otherwise two recognisers compete
   for the mic and the second one fails with `ERROR_RECOGNIZER_BUSY`).

**Therefore:**

- `onMicPressed`'s cancellation path is:
  ```kotlin
  voiceJob?.cancel()
  voiceJob?.join()                 // ← wait for the old job's awaitClose to run
  voiceJob = null
  ```
- But `join()` is suspending — so the whole barge-in path must be inside a
  `viewModelScope.launch { … }`. The dev refactors `onMicPressed` to launch a
  coroutine that does cancel-then-join-then-restart sequentially.

The unit test T8/T9/T10 catches this — without the `join()`, the test sees the
new job's `Starting` state set, then the old job's `Idle` reset on cancellation
race in, and the final state is `Idle` not `Starting`. The dev will see this
flake immediately if they skip the `join()`.

---

## 12. Strings delta

| ID | Value | Status |
|----|-------|--------|
| `copy_mic_inert` | (was) `Aún no escucho — espera a la siguiente versión` | **DELETED** — US-011 placeholder replaced by real flow |

No new strings (the SF reuses existing copy).

---

## 13. Test plan

**JVM unit tests** (`LauncherViewModelTest.kt`):

The 10 cases in §10. Approach:

- Inject Mockk fakes for `SttClient`, `TtsClient`, `PermissionGate`.
- `SttClient.listen()` returns a `MutableSharedFlow<SttClient.Event>` (or a
  Turbine-friendly cold Flow); the test pumps `Event.Partial`, `Event.Final`,
  `Event.Failed` to drive transitions.
- `TtsClient.speak(any())` returns a `SpeakResult.Completed` by default;
  override per test for the `Failed` / `Cancelled` paths.
- `PermissionGate.isGranted()` returns the test's chosen Boolean.
- Use `runTest` + `StandardTestDispatcher`; advance time with
  `advanceTimeBy(2500)` to verify the 2.5-s `Error → Idle` reset.

**Manual on-device verification** (must pass for the SF to be considered done):

- Redmi 15, airplane mode ON, Spanish voice pack installed:
  1. First-launch permission grant flow works.
  2. "Hola Curro" → final transcript matches → Curro echoes "Hola Curro" in
     Spanish at the slowed rate.
  3. Mid-speech mic press → Curro stops within ~150 ms, listening restarts.
  4. Speaking gibberish or silence → `copy_stt_fail_1` is spoken and shown.
  5. Voice-pack absence (if reproducible) → `copy_stt_no_voice_pack` is spoken
     and shown.
- Press-to-listening latency < 1 s, recorded with a stopwatch (3 trials).

**No new instrumented Compose test in this SF** — UI testing is added in US-018
for the `ListeningOverlay`. The launcher-screen Compose test infrastructure is
left for Phase 5 or a separate SF.

---

## 14. Files changed

**New:**

- `app/src/main/java/com/curro/app/data/permissions/PermissionGate.kt` (interface)
- `app/src/main/java/com/curro/app/data/permissions/RecordAudioPermissionGate.kt` (impl)
- `app/src/main/java/com/curro/app/di/PermissionsModule.kt` (or extend `VoiceModule`)
- `app/src/main/java/com/curro/app/presentation/assistant/.gitkeep` (creates the
  package; the real `ListeningOverlay.kt` is added in US-018)

**Modified:**

- `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt` —
  add `ListeningState`, inject clients, replace `onMicPressed`, add the new
  event/side-effect, add `startListening`, `errorMessage`, `transientError`.
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt` —
  register the permission launcher; wire `RequestRecordAudio`; wrap content in
  `Box` + overlay `AnimatedVisibility` (using US-018's `ListeningOverlay`);
  pass `isListening` to `MicButton`.
- `app/src/main/java/com/curro/app/presentation/launcher/MicButton.kt` — add the
  `isListening: Boolean = false` parameter; when `true`, swap background colour
  to `MaterialTheme.colorScheme.secondary`. (US-018 also touches MicButton; the
  dev coordinates the two — recommend US-018's MicButton change lands first if
  in the same commit, since US-017 just needs the parameter to exist.)
- `app/src/main/res/values/strings.xml` — delete `copy_mic_inert`.
- `app/src/test/java/com/curro/app/presentation/launcher/LauncherViewModelTest.kt` —
  add T1–T10.

**Not touched:** `Color.kt`, `Type.kt`, `Shape.kt`, `CurroSpacing.kt`, `Dimens.kt`,
`CurroTheme.kt`, `ClockBlock.kt`, `AppTileGrid.kt`, `AppTile.kt`,
`MoreAppsScreen.kt`, `MoreAppsViewModel.kt`, `ObserveClockUseCase.kt`,
`InstalledAppsRepositoryImpl.kt`, `StaticFavoriteAppsRepositoryImpl.kt`,
`DefaultLauncherDetector*.kt`, `BigPrimaryButton.kt`, `BigCard.kt`,
`BigYesNoRow.kt`, `BigListRow.kt`, `MainActivity.kt`, `CurroApp.kt`, the manifest
(US-015 already added `RECORD_AUDIO`).

---

## 15. Reference skills

- `voice-interaction` — the FSM + interrupt rule (full implementation Phase 5;
  this SF previews it for the launcher only).
- `launcher-ui` — surface 2 (Listening overlay), permission-denial copy
  conventions.
- `platform-integrations` — `RECORD_AUDIO` permission gating.
- `accessibility-patterns` — TalkBack announces `ListeningState.Error.message`
  via the overlay's `liveRegion` semantics (US-018's responsibility; flagged
  here so the dev makes sure the Error state is reachable by TalkBack).
- `compose-patterns` — `rememberLauncherForActivityResult`, `AnimatedVisibility`,
  `LaunchedEffect(Unit) { sideEffects.collect { … } }`.
- `testing-patterns` — `runTest` + `StandardTestDispatcher` for the 2.5-s delay
  test; Turbine for the `StateFlow`.
- `git-workflow` — commit scope `feat(launcher):` (since the changes are mostly
  in `presentation/launcher/`).
