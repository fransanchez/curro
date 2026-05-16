# TTS settings — voice, rate, pitch — US-053 / SF-8.4

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Voice / rate / pitch picker for Curro's TextToSpeech |
| **US ID** | US-053 (master-plan SF-8.4) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

## Summary

Replace SF-8.1's `config/tts` placeholder with `TtsSettingsScreen` — three
controls (voice picker, rate slider, pitch slider) + a "Probar voz" button
that speaks one canonical line so Fran can hear the result before he
commits + a "Volver a los valores por defecto" button. Make the SF-2.2
hard-coded `SPEECH_RATE = 0.88f` and `PITCH = 1.0f` constants into
DataStore-backed defaults; the existing `SystemTtsClient` reads from
`SettingsRepository` on init AND on subsequent emissions, so a slider
change is audible on the next utterance without restarting the app. Add a
small `SpanishVoiceProvider` interface in front of `TextToSpeech.getVoices()`
so the screen can populate its picker without touching the framework
directly.

Spec reference: `docs/curro-spec-v1.0.md` §9 ("Voz del TTS") + §14 ("TTS
voice acceptability — we don't know until Phase 2 ships on the device"); the
SF lands the tuning surface so that §14's open question becomes answerable.

## Scope

- **In Scope**:
  - 3 new `SettingsRepository` flows + setters (`ttsVoiceName`, `ttsRate`,
    `ttsPitch`).
  - 3 new DataStore keys with the right defaults (rate 0.88, pitch 1.0,
    voice null = "system default for `es-ES`").
  - `SpanishVoiceProvider` interface + `SystemSpanishVoiceProvider` impl.
  - Modification of `SystemTtsClient` to consume the three flows.
  - `TtsSettingsScreen` + `TtsSettingsViewModel`.
  - 7 new strings.
  - Replacement of the `composable("config/tts")` placeholder.
- **Out of Scope**:
  - Bundling an ElevenLabs voice (spec §14 open question — defer until
    on-device-validated as unacceptable).
  - Bundling a custom non-system Spanish voice asset.
  - Per-action rate variation (e.g. "read messages slower than confirm").
  - A "test voice with a specific phrase you type" affordance.
  - Telemetry for TTS changes.

## User Flows

### Flow 1: Fran slows the rate

1. Fran opens config → "Voz y velocidad de habla".
2. `TtsSettingsScreen` renders. The rate slider is at the current value
   (0.88 by default; whatever Fran set last).
3. Fran moves the rate slider left → the label updates live ("Velocidad:
   0.75x"). The slider's `onValueChange` calls `settingsRepo.setTtsRate(value)`.
4. Fran taps "Probar voz" → the screen calls `ttsClient.speak(R.string.copy_config_tts_preview)`.
5. Curro speaks "Hola Pepe, así te voy a hablar." at the new rate (the
   `SystemTtsClient`'s `combine`-collector applied the new value on the
   prior flow tick — verify the rate is applied BEFORE the speak begins, not
   only on the next utterance).

### Flow 2: Fran picks a different voice

1. Fran taps the voice picker (a dropdown / bottom-sheet).
2. The list shows "Sistema (predeterminado)" at top + every Spanish voice
   from `TextToSpeech.getVoices().filter { it.locale.language == "es" }`,
   ordered by display name.
3. Fran taps a voice → the dropdown closes; `settingsRepo.setTtsVoiceName(name)`
   fires.
4. `SystemTtsClient.applyConfig` calls `tts.setVoice(...)`.
5. Fran taps "Probar voz" → Curro speaks in the new voice.

### Flow 3: Fran resets to defaults

1. Fran taps "Volver a los valores por defecto".
2. VM calls `setTtsRate(0.88f)`, `setTtsPitch(1.0f)`, `setTtsVoiceName(null)`
   in parallel.
3. The screen re-renders with the defaults visible.
4. Fran taps "Probar voz" → Curro speaks with the default (preferring a male
   Spanish voice via the existing `preferMaleSpanishVoice` fallback).

### Flow 4: No Spanish voices installed (edge)

1. The picker shows ONLY "Sistema (predeterminado)" (the `availableVoices`
   result is empty).
2. The picker label reads "Voz del sistema (no hay otras instaladas)" —
   pin: this string is one of the 7 new strings.

## Function-catalog Impact

No catalog change.

## FSM States Touched

None. The "Probar voz" button triggers a TTS speak that is NOT routed
through `AssistantCoordinator` — it goes directly to `TtsClient.speak` from
the VM. **Pin**: this means the speak is outside the FSM; if the user
happens to be mid-confirmation (a confirming overlay is up on the launcher),
the test-voice playback would compete with the assistant TTS — UNLIKELY,
because Fran is in the config menu (the launcher is in the background), but
documented. The existing `TtsClient.speak`'s `QUEUE_FLUSH` flushes any prior
utterance, so the worst case is "Fran's test voice interrupts a confirm";
acceptable.

## Android System Integrations & Permissions

No new permissions. Uses the existing `TextToSpeech` instance via
`TtsClient` / `TextToSpeechFactory`.

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none) | — | — | — |

## On-device-model Impact

No model impact.

## Android Specification

### Screens and Composables

- **`presentation/config/sections/tts/TtsSettingsScreen.kt`** —
  `@Composable fun TtsSettingsScreen(onBack: () -> Unit, viewModel: TtsSettingsViewModel = hiltViewModel())`.
  - Sections (a `Column` inside a `verticalScroll`):
    1. Voice picker — `ExposedDropdownMenuBox` (Material 3) wrapping a
       read-only `OutlinedTextField` + a `DropdownMenu` of all `availableVoices`
       + "Sistema (predeterminado)".
    2. Rate slider — label "Velocidad: %.2fx" with the current value, then
       `Slider(value = uiState.rate, onValueChange = { viewModel.onEvent(TtsSettingsEvent.SetRate(it)) }, valueRange = 0.5f..1.5f, steps = 19)` (20 ticks = 0.05 steps).
       Help line: `copy_config_tts_rate_help` ("Más a la izquierda = más
       despacio.").
    3. Pitch slider — label "Tono: %.2f" with the current value, then
       `Slider(value = uiState.pitch, onValueChange = { ... SetPitch }, valueRange = 0.5f..2.0f, steps = 14)` (15 ticks). Help line `copy_config_tts_pitch_help`.
    4. `BigPrimaryButton("Probar voz", onClick = { viewModel.onEvent(TtsSettingsEvent.Preview) })`.
    5. `TextButton("Volver a los valores por defecto", onClick = { ... ResetDefaults })`.
  - Back chevron at `TopStart`.

### ViewModels and State Management

```kotlin
@HiltViewModel
class TtsSettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val voiceProvider: SpanishVoiceProvider,
    private val ttsClient: TtsClient,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val availableVoices = MutableStateFlow<List<TtsVoice>>(emptyList())

    init {
        viewModelScope.launch(ioDispatcher) {
            availableVoices.value = voiceProvider.availableVoices()
        }
    }

    val uiState: StateFlow<TtsSettingsUiState> = combine(
        settingsRepo.ttsVoiceName,
        settingsRepo.ttsRate,
        settingsRepo.ttsPitch,
        availableVoices,
    ) { voice, rate, pitch, voices ->
        TtsSettingsUiState(currentVoiceName = voice, rate = rate, pitch = pitch, availableVoices = voices)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsSettingsUiState.Initial)

    fun onEvent(event: TtsSettingsEvent) {
        when (event) {
            is TtsSettingsEvent.SetVoice -> viewModelScope.launch { settingsRepo.setTtsVoiceName(event.voiceName) }
            is TtsSettingsEvent.SetRate -> viewModelScope.launch { settingsRepo.setTtsRate(event.rate) }
            is TtsSettingsEvent.SetPitch -> viewModelScope.launch { settingsRepo.setTtsPitch(event.pitch) }
            TtsSettingsEvent.Preview -> viewModelScope.launch {
                ttsClient.speak(context.getString(R.string.copy_config_tts_preview))
            }
            TtsSettingsEvent.ResetDefaults -> viewModelScope.launch {
                // Reset order doesn't matter here (independent setters); set in any sequence.
                listOf(
                    launch { settingsRepo.setTtsRate(DEFAULT_RATE) },
                    launch { settingsRepo.setTtsPitch(DEFAULT_PITCH) },
                    launch { settingsRepo.setTtsVoiceName(null) },
                ).joinAll()
            }
        }
    }

    private companion object {
        const val DEFAULT_RATE = 0.88f
        const val DEFAULT_PITCH = 1.0f
    }
}

data class TtsSettingsUiState(val currentVoiceName: String?, val rate: Float, val pitch: Float, val availableVoices: List<TtsVoice>) {
    companion object { val Initial = TtsSettingsUiState(null, 0.88f, 1.0f, emptyList()) }
}

sealed interface TtsSettingsEvent {
    data class SetVoice(val voiceName: String?) : TtsSettingsEvent
    data class SetRate(val rate: Float) : TtsSettingsEvent
    data class SetPitch(val pitch: Float) : TtsSettingsEvent
    data object Preview : TtsSettingsEvent
    data object ResetDefaults : TtsSettingsEvent
}
```

### Navigation Routes

- **MODIFIED**: replace `composable("config/tts") { ConfigSectionPlaceholder(...) }`
  with the real `TtsSettingsScreen`.
- No new routes.

### Hilt Modules

- **NEW** `app/src/main/java/com/curro/app/di/SpanishVoiceProviderModule.kt`
  — binds `SpanishVoiceProvider` to `SystemSpanishVoiceProvider`. The impl
  needs the `TextToSpeech` instance; it injects the existing
  `TextToSpeechFactory` and creates a transient `TextToSpeech` for the voice
  enumeration call OR — simpler — uses the same `TextToSpeechFactory` and
  awaits a `CompletableDeferred<TextToSpeech>` analogous to
  `SystemTtsClient`'s `initDeferred`. **Pin: the implementer chooses the
  simplest path; the brief recommends having `SystemTtsClient` expose a
  `suspend fun underlyingTts(): TextToSpeech?` accessor (internal-visibility)
  that `SystemSpanishVoiceProvider` can use, avoiding a second `TextToSpeech`
  instantiation.**

### Composables by Feature (checklist)

- [x] `TtsSettingsScreen` (collects the ViewModel)
- [x] Stateless `TtsSettingsContent` (for tests).
- [x] `VoicePicker` composable.
- [x] `RateSlider` + `PitchSlider` composables (or inline; pin a separate
      file if they share a `LabeledSlider` helper).
- [x] Dark + large-font previews.

### Material Design Components

- `ExposedDropdownMenuBox` for the voice picker.
- `Slider` for rate and pitch.
- `BigPrimaryButton` for "Probar voz".
- `TextButton` for "Volver a los valores por defecto".
- `OutlinedTextField` (read-only) inside the dropdown box.

## Acceptance Criteria

- [ ] **Voice picker lists installed Spanish voices** — every result from
      `voiceProvider.availableVoices()` appears.
- [ ] **"Sistema (predeterminado)" maps to `null`** — picking it persists
      `voiceName = null`; `SystemTtsClient` then falls back to
      `preferMaleSpanishVoice`.
- [ ] **Rate slider changes are audible** — after moving the rate, tapping
      "Probar voz" speaks at the new rate (manually verified on the device).
- [ ] **Pitch slider changes are audible** — same.
- [ ] **Defaults pinned**: rate `0.88f`, pitch `1.0f`, voice `null`.
- [ ] **Settings persist across restarts** — change → kill app → reopen → the
      values are the changed ones.
- [ ] **`SystemTtsClient` applies on change** — the existing assistant
      pipeline (e.g. an "abre WhatsApp" handler's TTS) reflects the new
      values on the next utterance, without restart.
- [ ] **No `Spanish voices` edge case** — if `availableVoices` is empty,
      the picker still renders "Sistema (predeterminado)" and the disabled
      "no hay otras instaladas" hint.
- [ ] **7 new strings** with the right IDs.
- [ ] **3 new DataStore keys** with the right defaults.
- [ ] **No new permissions, no new manifest entries, no new dependencies, no
      new telemetry event.**
- [ ] **Build is green**.

## Design Notes

- The slider's tick steps (20 for rate, 15 for pitch) give Fran ~0.05
  granularity — enough to notice but not so much he can't reproduce a
  setting. Pin: avoid `steps = 0` (continuous) — fine motor control isn't
  an issue here (this is Fran), but stepping matches the rest of Curro's
  "discrete and confident" aesthetic.
- The voice picker uses Material 3's `ExposedDropdownMenuBox` for its
  density; the picker is alphabetical (system locale's collator).
- The "Probar voz" button is a `BigPrimaryButton` (≥ 96 dp) for
  consistency with the rest of Curro's CTAs even though it's a Fran-only
  screen.

## Senior-UX & Copy

Fran-only — config-menu density.

**One new spoken (TTS) string**: `copy_config_tts_preview` is what Curro
speaks when Fran taps "Probar voz". The voice is warm, brief, and uses
Curro's voice (spec §2 — Curro's tone).

New entries in `app/src/main/res/values/strings.xml` (8 total — the brief's
PRD checklist said 7 but the empty-voices hint adds one; pin the correct
count):

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_tts_voice_label` | "Voz" | picker label |
| `copy_config_tts_voice_system_default` | "Sistema (predeterminado)" | top picker option |
| `copy_config_tts_voice_no_others` | "(no hay otras voces instaladas)" | inline hint when picker is empty |
| `copy_config_tts_rate_label` | "Velocidad: %.2fx" | slider label |
| `copy_config_tts_rate_help` | "Más a la izquierda = más despacio." | slider help |
| `copy_config_tts_pitch_label` | "Tono: %.2f" | slider label |
| `copy_config_tts_pitch_help` | "Más arriba = más agudo; más abajo = más grave." | slider help |
| `copy_config_tts_preview` | "Hola Pepe, así te voy a hablar." | spoken; **Curro's voice** |
| `copy_config_tts_preview_button` | "Probar voz" | button label |
| `copy_config_tts_reset` | "Volver a los valores por defecto" | reset button |

That's 10 strings; pin in implementation.

**`brand-design` COPY table**: add a "TTS settings (Phase 8 — SF-8.4)"
section with all 10 rows. The `copy_config_tts_preview` line is the
spoken one — pin its Curro-voice provenance: `(NEW — SF-8.4, Curro's voice
per spec §2)`.

## Performance Considerations

- The 3-flow `combine` is fine; emits on every slider tick are throttled by
  the natural UI debounce.
- `SystemTtsClient`'s `applyConfig` collector runs on the app's
  `@ApplicationScope` coroutine. Each emission calls `tts.setSpeechRate`,
  `tts.setPitch`, `tts.setVoice` — all thread-safe per the existing class
  Kdoc.
- `SystemSpanishVoiceProvider.availableVoices()` runs once on VM init (on
  the IO dispatcher).
- No per-tick recomposition concerns; sliders are local state in Compose.

## Testing Requirements

- [ ] **FSM**: N/A.
- [ ] **`SettingsRepository` TTS flows** —
      `SettingsDataStoreTtsTest` (extends existing, 6 cases):
      1. `default_ttsRate_is_0_88`.
      2. `default_ttsPitch_is_1_0`.
      3. `default_ttsVoiceName_isNull`.
      4. `setTtsRate_clamps_to_0_5_to_1_5`.
      5. `setTtsPitch_clamps_to_0_5_to_2_0`.
      6. `roundTrip_ttsVoiceName_string`.
- [ ] **`SystemTtsClient` config consumption** —
      `SystemTtsClientConfigTest` (Robolectric + Fake): 5 cases:
      1. `init_appliesDefaultRateAndPitch_fromSettings`.
      2. `settingsRepo_rateChange_appliesToTts_onCollect`.
      3. `settingsRepo_voiceChange_callsTtsSetVoice_withMatchingVoice`.
      4. `settingsRepo_voiceNull_fallsBackTo_preferMaleSpanishVoice`.
      5. `init_completesBeforeSettings_appliesOnce_doesNotDeadlock`.
- [ ] **`SystemSpanishVoiceProvider`** —
      `SystemSpanishVoiceProviderTest` (Robolectric, 3 cases):
      1. `availableVoices_filtersBy_es_locale`.
      2. `availableVoices_returnsEmpty_whenTtsHasNoSpanishVoices`.
      3. `availableVoices_returnsDisplayNameAndId`.
- [ ] **`TtsSettingsViewModel`** (7 cases) — JVM + fakes:
      1. `uiState_emitsDefaults_onInit`.
      2. `onSetRate_callsRepoSetTtsRate`.
      3. `onSetPitch_callsRepoSetTtsPitch`.
      4. `onSetVoice_callsRepoSetTtsVoiceName`.
      5. `onPreview_callsTtsClientSpeak_withPreviewText`.
      6. `onResetDefaults_setsAllThreeBack`.
      7. `availableVoices_loadedOnInit_viaProvider`.
- [ ] **Instrumented UI tests on `TtsSettingsContent`** (4 cases):
      1. `voicePicker_rendersSystemDefault_andEachAvailableVoice`.
      2. `rateSlider_value_movesOnUserDrag_andFiresOnEvent`.
      3. `pitchSlider_value_movesOnUserDrag_andFiresOnEvent`.
      4. `previewButton_fires_Preview_event`.
- [ ] **Dark + large-font previews**.
- [ ] **Real Redmi 15 smoke**:
      - Move rate slider left → tap "Probar voz" → audibly slower.
      - Move pitch slider up → tap "Probar voz" → audibly higher.
      - Pick a different installed Spanish voice → preview reflects it.
      - Reset → defaults restored.
      - Restart app → settings persist.
      - Ask Curro the time after changing rate → the time announcement
        uses the new rate (the assistant pipeline picks up the change).

## Implementation Notes

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/presentation/config/sections/tts/TtsSettingsScreen.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/tts/TtsSettingsViewModel.kt`
- `app/src/main/java/com/curro/app/data/voice/SpanishVoiceProvider.kt`
- `app/src/main/java/com/curro/app/data/voice/SystemSpanishVoiceProvider.kt`
- `app/src/main/java/com/curro/app/data/voice/TtsConfig.kt`
- `app/src/main/java/com/curro/app/di/SpanishVoiceProviderModule.kt`
- `app/src/test/java/com/curro/app/data/local/SettingsDataStoreTtsTest.kt`
- `app/src/test/java/com/curro/app/data/voice/SystemTtsClientConfigTest.kt`
- `app/src/test/java/com/curro/app/data/voice/SystemSpanishVoiceProviderTest.kt`
- `app/src/test/java/com/curro/app/presentation/config/sections/tts/TtsSettingsViewModelTest.kt`
- `app/src/androidTest/java/com/curro/app/presentation/config/sections/tts/TtsSettingsContentTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/domain/repository/SettingsRepository.kt`
  (+3 flows + 3 setters).
- `app/src/main/java/com/curro/app/data/local/SettingsDataStore.kt` (+3 keys
  + getters/setters).
- `app/src/main/java/com/curro/app/data/voice/SystemTtsClient.kt` (inject
  `SettingsRepository` + scope; remove the two `companion` constants; add
  `applyConfig`).
- `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
  (1 swap).
- `app/src/main/res/values/strings.xml` (+10 entries).
- `.claude/skills/brand-design/SKILL.md` (+10 rows in a new "TTS settings
  (Phase 8 — SF-8.4)" section).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.4 TTS settings (voice, rate, pitch). |
