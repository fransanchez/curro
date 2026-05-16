# "Modo asistente de llamadas" toggle + `CurroInCallService` — US-056 / SF-8.7

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Opt-in incoming-call announcement + voice-driven answer/decline |
| **US ID** | US-056 (master-plan SF-8.7) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |
| **Implementer** | **voice-pipeline-engineer** (split from the other 9 Phase-8 SFs) |

## Summary

The most invasive system integration in Curro. When Fran flips the "Modo
asistente de llamadas" toggle ON: Curro requests the three telephony
permissions (`READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `MANAGE_OWN_CALLS`),
enables the `CurroInCallService` component at runtime via
`PackageManager.setComponentEnabledSetting`, and from then on every incoming
call from a known contact gets announced by voice ("Te está llamando
Pepito" — or "Te está llamando tu hija María" if there's an alias) and can
be answered with "sí" / "coge" / "responde" or declined with "no" /
"cuelga". Unknown numbers ring native (no announcement). When the toggle is
OFF: the service is **manifest-declared `android:enabled="false"`** —
telephony is **100 % native**, structurally guaranteed by the framework's
service-binding rules (not by a runtime check).

This SF is **outside the main FSM** — the call-answering mini-flow is its
own constrained STT pass, not a state transition in `AssistantState`. The
launcher's `onMicPressed` path is unchanged.

Spec references: `docs/curro-spec-v1.0.md` §8 (opt-in incoming-call mode) +
§10 (permissions table) + `platform-integrations` "Incoming-call assistant
mode" + `voice-interaction` "outside the main FSM".

**Pin: this is the SF most likely to need spec changes.** §10's permission
table is missing the three new permissions; SF-8.7 lands them AND bumps the
spec to v1.2 with a revision-history row.

## Scope

- **In Scope**:
  - `incoming_call_mode` DataStore key + flow + setter (the read flow
    landed in SF-8.1 declared on `SettingsRepository`; the setter has no
    callers in SF-8.1 — SF-8.7 wires the controller that calls it).
  - `CurroInCallService` — Android `InCallService` subclass.
  - `SttClient.listenForCallResponse()` + `CallResponseVoice` sealed
    interface — the 3rd constrained-STT method (after `listenForConfirmation`
    and `listenForPicker`).
  - `ContactsProvider.findByNumber(number)` — the canonical
    `ContactsContract.PhoneLookup` lookup.
  - `IncomingCallModeController` — the single write-path that orchestrates
    `setComponentEnabledSetting` + `setIncomingCallModeEnabled`.
  - `IncomingCallModeToggleHandler` — the UI-layer logic that requests
    permissions then calls the controller.
  - Manifest changes: 3 new `<uses-permission>`s; 1 new
    `<service android:enabled="false">` block.
  - 4 new strings (the section title is reused from SF-8.1).
  - 1 new telemetry event (`incoming_call_announced` with `outcome` prop).
  - Spec v1.2 bump.
- **Out of Scope**:
  - Curro replacing the native dialer UI (`meta-data IN_CALL_SERVICE_UI`
    stays `false`).
  - Outgoing-call handling via `InCallService` (out of scope — placing
    calls already works via SF-4.10's `ACTION_CALL`).
  - Persistent on-screen UI during a call (the native HyperOS call UI is
    untouched; Curro only speaks).
  - Repeat-announce after a missed call (out of scope).
  - Call-screening (out of scope; complementary feature for Fase 3).

## User Flows

### Flow 1: Fran enables the mode

1. Fran opens config → flips "Modo asistente de llamadas" toggle ON.
2. The toggle's `onChange(true)` → `ConfigViewModel.onEvent(ToggleChanged(IncomingCallSection, true))`
   → routed through the NEW `IncomingCallModeToggleHandler.enable()`.
3. Handler publishes `LauncherSideEffect.RequestPhonePermissions` to the
   shared `LauncherSideEffectBus`.
4. `LauncherPlaceholderScreen` (or wherever the `ActivityResultLauncher`
   for permissions lives) catches the side effect → fires
   `requestPermissionsLauncher.launch(arrayOf(READ_PHONE_STATE, ANSWER_PHONE_CALLS, MANAGE_OWN_CALLS))`.
5. HyperOS shows the permission dialogs. Fran grants all three.
6. The result comes back through `LauncherEvent.PhonePermissionResult(grantedAll = true)`.
7. Handler calls `incomingCallModeController.enable()` →
   `setComponentEnabledSetting(ENABLED, DONT_KILL_APP)` →
   `settingsRepo.setIncomingCallModeEnabled(true)`.
8. The config menu's toggle row re-renders ON.

### Flow 2: Fran enables the mode but denies a permission

1. Steps 1–5 as above. Fran denies `ANSWER_PHONE_CALLS`.
2. `LauncherEvent.PhonePermissionResult(grantedAll = false)`.
3. Handler does NOT call `controller.enable()`. The toggle visually snaps
   back to OFF.
4. Toast / snackbar: `copy_config_incoming_call_perm_needed` ("Necesito
   permisos de teléfono para anunciar las llamadas. Otórgalos o el modo no
   se activa.").

### Flow 3: An incoming call from a known contact (mode ON)

1. The user's friend Pepito calls. The phone is in the user's pocket.
2. Android binds `CurroInCallService` (because the component is enabled and
   the intent-filter matches). `onCallAdded(call)` fires.
3. The service launches a coroutine in its `@ApplicationScope` scope:
   - Double-check `settingsRepo.incomingCallModeEnabled.first()` — true.
   - Extract `number = call.details.handle?.schemeSpecificPart` →
     `"+34600123456"`.
   - `contactsProvider.findByNumber("+34600123456")` → `Contact(displayName = "Pepito Martínez", ...)`.
   - Check aliases: `aliasRepo.observeAll().first().firstOrNull { ... }` →
     null (Pepito has no alias).
   - Speak via `ttsClient.speak("Te está llamando Pepito Martínez.")` (using
     `copy_incoming_call_announce` with the display name).
   - `sttClient.listenForCallResponse().first()` → `Answer` (user said
     "sí").
   - `call.answer(VideoProfile.STATE_AUDIO_ONLY)`.
   - Emit telemetry `incoming_call_announced(outcome = "answered")`.
4. Pepito and the user talk.

### Flow 4: An incoming call from a known contact with an alias

1. Lucía Ruiz calls. The user has aliased her as "mi hija".
2. Steps as above, but the alias lookup hits → spoken phrase becomes
   "Te está llamando mi hija, Lucía Ruiz." (using
   `copy_incoming_call_announce_with_alias`).
3. Same answer/decline flow.

### Flow 5: An incoming call from an unknown number

1. Telemarketer calls. Number not in contacts.
2. `contactsProvider.findByNumber(...)` → null.
3. Service returns immediately (no announcement, no STT). Phone rings
   native. User decides via the native UI.
4. Emit telemetry `incoming_call_announced(outcome = "ignored")` — pin: the
   "ignored" outcome is for "we saw the call but didn't speak". Optionally
   skip emitting at all to avoid noise — **PM decision: emit `ignored` so
   Fran can see how many unknown-number calls come in**.

### Flow 6: User says "no" — decline

1. Steps as Flow 3, but STT returns `Decline`.
2. `call.disconnect()`.
3. Telemetry `outcome = "declined"`.

### Flow 7: User says something else / silent — no response

1. Steps as Flow 3, but STT returns `Other` or `Failed`.
2. The service does nothing — the phone keeps ringing. User can tap
   manually.
3. Telemetry `outcome = "no_response"`.

### Flow 8: Fran disables the mode

1. Fran flips the toggle OFF.
2. Handler calls `incomingCallModeController.disable()` →
   `setComponentEnabledSetting(DISABLED, DONT_KILL_APP)` →
   `settingsRepo.setIncomingCallModeEnabled(false)`.
3. **Structural invariant**: from now until the next enable,
   `queryIntentServices(Intent("android.telecom.InCallService"))` does NOT
   list `CurroInCallService`. Telephony is 100 % native — verified by the
   instrumented test.

## Function-catalog Impact

No catalog change. The incoming-call flow is NOT a function call (no
FunctionGemma involvement).

## FSM States Touched

**None of `idle` / `listening` / `processing` / `confirming` / `executing` /
`error_recovery`.** The incoming-call mini-flow is its own state machine
inside `CurroInCallService` — `announce` → `listenForResponse` → `answer` /
`disconnect` / `ignore` → done. Documented in `voice-interaction` as
"outside the main FSM". The launcher's `onMicPressed` continues to work
during a ringing call (the user might press the mic to ask Curro something
while ignoring the ring — that's the existing FSM, untouched). **Pin in
brief**: if the user presses the mic while `CurroInCallService` is mid-
announcement, the main FSM's interrupt rule kicks in (the launcher's
`SttClient.listen()` cancels the previous `listenForCallResponse()` because
the underlying `SpeechRecognizer` is a single-session resource). Document
the contention: rare edge case; honest failure mode is "the call ring
continues; Curro starts listening for the launcher's intent". Acceptable.

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `READ_PHONE_STATE` | required by `InCallService` to read call state | only when Fran flips the toggle ON | the toggle reverts to OFF; toast `copy_config_incoming_call_perm_needed` |
| `ANSWER_PHONE_CALLS` | `call.answer()` to programmatically answer | same | same |
| `MANAGE_OWN_CALLS` | required on some HyperOS builds for `InCallService` operations | same | same |
| `BIND_INCALL_SERVICE` | the system-only permission required on the `<service>` declaration (set via `android:permission`, NOT a `<uses-permission>`) | always declared in manifest | the service wouldn't bind; pre-flight by the `IncomingCallModeOffInvariantTest` |

**Pin: these are listed in the manifest from install** — they show up in
HyperOS's app-info page as "asked-for permissions". The runtime request only
fires when Fran toggles. Spec §10 must list these (a missing detail until
SF-8.7).

## On-device-model Impact

No FunctionGemma impact. Gemma 3n not involved. The TTS call uses the
existing `TtsClient` (the configured voice/rate/pitch from SF-8.4 apply).

## Android Specification

### Screens and Composables

This SF mostly adds a non-UI component (the service); the only UI is the
toggle row in `ConfigMenuScreen` (already declared in SF-8.1) becoming
behavioural.

- **MODIFIED** `presentation/config/ConfigViewModel.kt` — the
  `onEvent(ConfigEvent.ToggleChanged)` for the incoming-call section dispatches
  to a NEW `IncomingCallModeToggleHandler.handle(enable: Boolean)`. The
  handler returns nothing; it publishes side effects to the
  `LauncherSideEffectBus`.

- **MODIFIED** `presentation/launcher/LauncherViewModel.kt` — add
  `data object RequestPhonePermissions : LauncherSideEffect` AND a matching
  `data class PhonePermissionResult(val grantedAll: Boolean) : LauncherEvent`.
  Route the event to the toggle handler.

- **MODIFIED** `presentation/launcher/LauncherPlaceholderScreen.kt` —
  register an `ActivityResultLauncher` for `RequestMultiplePermissions`;
  fire it on `RequestPhonePermissions`; report back via
  `viewModel.onEvent(LauncherEvent.PhonePermissionResult(grantedAll = result.all { it.value }))`.

### Service

- **NEW** `data/telephony/CurroInCallService.kt`:
  ```kotlin
  @AndroidEntryPoint
  class CurroInCallService : InCallService() {
      @Inject lateinit var aliasRepo: AliasRepository
      @Inject lateinit var contactsProvider: ContactsProvider
      @Inject lateinit var ttsClient: TtsClient
      @Inject lateinit var sttClient: SttClient
      @Inject lateinit var settingsRepo: SettingsRepository
      @Inject lateinit var telemetry: TelemetrySink
      @Inject @ApplicationScope lateinit var scope: CoroutineScope
      @Inject @ApplicationContext lateinit var appContext: Context

      override fun onCallAdded(call: Call) {
          super.onCallAdded(call)
          if (call.state != Call.STATE_RINGING) return
          scope.launch { handleRinging(call) }
      }

      private suspend fun handleRinging(call: Call) {
          // Defensive: even if the component is enabled, the setting must be on.
          if (!settingsRepo.incomingCallModeEnabled.first()) return

          val number = call.details.handle?.schemeSpecificPart ?: return
          val contact = contactsProvider.findByNumber(number) ?: run {
              telemetry.emit("incoming_call_announced", mapOf("outcome" to "ignored"))
              return
          }

          val alias = aliasRepo.observeAll().first()
              .firstOrNull { it.displayName == contact.displayName }
              ?.alias
          val phrase = if (alias != null) {
              appContext.getString(R.string.copy_incoming_call_announce_with_alias, alias, contact.displayName)
          } else {
              appContext.getString(R.string.copy_incoming_call_announce, contact.displayName)
          }
          ttsClient.speak(phrase)

          val response = sttClient.listenForCallResponse().first()
          val outcome = when (response) {
              CallResponseVoice.Answer -> {
                  call.answer(VideoProfile.STATE_AUDIO_ONLY)
                  "answered"
              }
              CallResponseVoice.Decline -> {
                  call.disconnect()
                  "declined"
              }
              is CallResponseVoice.Other, is CallResponseVoice.Failed -> "no_response"
          }
          telemetry.emit("incoming_call_announced", mapOf("outcome" to outcome))
      }
  }
  ```

### ViewModels and State Management

- `IncomingCallModeToggleHandler` is `@Inject`-constructable (not a ViewModel —
  it's a stateless orchestrator):
  ```kotlin
  @Singleton
  class IncomingCallModeToggleHandler @Inject constructor(
      private val controller: IncomingCallModeController,
      private val sideEffectBus: LauncherSideEffectBus,
  ) {
      suspend fun handle(enable: Boolean) {
          if (enable) {
              sideEffectBus.publish(LauncherSideEffect.RequestPhonePermissions)
          } else {
              controller.disable()
          }
      }

      suspend fun onPermissionResult(grantedAll: Boolean) {
          if (grantedAll) controller.enable()
          else sideEffectBus.publish(LauncherSideEffect.ShowToast(R.string.copy_config_incoming_call_perm_needed))
      }
  }
  ```
- `IncomingCallModeController` is the single write-path:
  ```kotlin
  @Singleton
  class IncomingCallModeController @Inject constructor(
      @ApplicationContext private val context: Context,
      private val settingsRepo: SettingsRepository,
  ) {
      suspend fun enable() {
          val cn = ComponentName(context, CurroInCallService::class.java)
          context.packageManager.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
          settingsRepo.setIncomingCallModeEnabled(true)
      }
      suspend fun disable() {
          val cn = ComponentName(context, CurroInCallService::class.java)
          context.packageManager.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
          settingsRepo.setIncomingCallModeEnabled(false)
      }
      fun isComponentEnabled(): Boolean {
          val cn = ComponentName(context, CurroInCallService::class.java)
          return when (context.packageManager.getComponentEnabledSetting(cn)) {
              PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
              else -> false
          }
      }
  }
  ```
- `LauncherSideEffectBus` — a NEW `@Singleton` `MutableSharedFlow<LauncherSideEffect>` published by `IncomingCallModeToggleHandler` (and by SF-8.8's exporter), collected by `LauncherViewModel` and merged into its existing `_sideEffects` Channel. **Pin: this is the cross-VM bridge** — SF-8.7 introduces it; SF-8.8 reuses.

### Navigation Routes

No new routes. The toggle row stays inline in `ConfigMenuScreen`.

### Hilt Modules

- **NEW** `di/TelephonyModule.kt` — binds `IncomingCallModeController` (it's
  `@Inject`-constructable, no binding strictly needed; module exists for
  group documentation).
- **MODIFIED** `di/CoordinatorModule.kt` (or wherever the
  `@ApplicationScope CoroutineScope` is provided) — verify that the scope
  is available for `CurroInCallService`'s `@Inject`-by-field pattern.

### Manifest

- **MODIFIED** `app/src/main/AndroidManifest.xml`:
  ```xml
  <!-- SF-8.7 (US-056) — requested at runtime ONLY when Fran flips the "Modo
       asistente de llamadas" toggle. With the toggle OFF, these are dormant
       manifest declarations: Curro never asks, the component is disabled,
       telephony is 100 % native. -->
  <uses-permission android:name="android.permission.READ_PHONE_STATE" />
  <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
  <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />

  <application ...>
      ...
      <!-- SF-8.7 (US-056) — incoming-call assistant. android:enabled="false"
           is the structural OFF-state: with the toggle off, the InCallService
           binding intent-filter is not matched, so telephony is 100 % native.
           IncomingCallModeController.setComponentEnabledSetting flips this
           at runtime when Fran toggles. -->
      <service
          android:name=".data.telephony.CurroInCallService"
          android:permission="android.permission.BIND_INCALL_SERVICE"
          android:enabled="false"
          android:exported="true"
          android:foregroundServiceType="phoneCall">
          <meta-data
              android:name="android.telecom.IN_CALL_SERVICE_UI"
              android:value="false" />
          <intent-filter>
              <action android:name="android.telecom.InCallService" />
          </intent-filter>
      </service>
  </application>
  ```

### Composables by Feature (checklist)

Minimal — this is mostly a non-UI SF.

- [x] No new screens — the toggle row in `ConfigMenuScreen` (from SF-8.1)
      becomes behavioural.
- [x] No new dialogs — the permission dialog is the system's.
- [x] No new previews.

## Acceptance Criteria

- [ ] **Toggle off by default** — fresh install: `incomingCallModeEnabled = false`,
      service component disabled in manifest, no permissions requested.
- [ ] **Enabling: permission flow** — flipping the toggle ON triggers a
      `RequestPermissionsLauncher` for the 3 permissions; on grantedAll,
      the controller enables the component AND persists the setting.
- [ ] **Enabling: permission denial** — on partial-grant, toggle reverts
      to OFF; toast shows `copy_config_incoming_call_perm_needed`.
- [ ] **Disabling** — flipping OFF re-disables the component AND clears
      the setting. No permission revoke (we keep them granted in HyperOS;
      revoking via toggle would be user-hostile and unusual).
- [ ] **Known contact + alias announces with alias name**: "Te está
      llamando mi hija, Lucía Ruiz."
- [ ] **Known contact without alias announces with display name**: "Te
      está llamando Pepito Martínez."
- [ ] **Unknown number**: NO announcement, NO STT. Telemetry
      `outcome = "ignored"`. Phone rings native.
- [ ] **Voice "sí" / "coge" / "responde" → `call.answer()`.** Telemetry
      `outcome = "answered"`.
- [ ] **Voice "no" / "cuelga" → `call.disconnect()`.** Telemetry
      `outcome = "declined"`.
- [ ] **Voice other / silent → no action**; phone keeps ringing.
      Telemetry `outcome = "no_response"`.
- [ ] **Toggle-OFF telephony invariant**: with the setting OFF,
      `queryIntentServices(Intent("android.telecom.InCallService"))` does
      NOT include `CurroInCallService`. Verified by the
      `IncomingCallModeOffInvariantTest`.
- [ ] **4 new strings** in `strings.xml`.
- [ ] **3 new manifest `<uses-permission>` entries** + 1 new `<service>` block.
- [ ] **1 new DataStore key** (`incoming_call_mode` — actually landed in SF-8.1
      already; SF-8.7 wires its setter callers).
- [ ] **1 new telemetry event** (`incoming_call_announced`) added to
      `TelemetryGuardrail.ALLOWED_PROPS` with the 4 outcome values. The
      guardrail tests verify no PII shape is permitted.
- [ ] **Spec v1.2 bump** — `docs/curro-spec-v1.0.md` §10 updated with the
      three rows; revision history row added.
- [ ] **Build is green** (clean assembly works with `android:enabled="false"`
      service).

## Design Notes

- The toggle row's `helpResId` in SF-8.1 was the short version
  (`copy_config_incoming_call_help_short`). SF-8.7 does NOT change the row
  shape — the long-form help line `copy_config_incoming_call_help` is shown
  as a `BottomSheet` or `Dialog` when Fran taps an info icon (out of scope
  for SF-8.7 — for now, the short help is enough; the long help is in the
  brief for reference).
- The TTS announcement uses the existing `TtsClient` so it honours
  SF-8.4's voice / rate / pitch settings — Curro speaks with the same
  voice for incoming-call announcements as for everything else.
- **Pin: do NOT call `super.onCallAdded(call)` AFTER the work** — the
  override calls `super` first (Android lifecycle convention), then
  launches the coroutine.

## Senior-UX & Copy

The user (Fran's father) hears the announcement; the screen does not
change (the native HyperOS call UI takes over). This is the rare Curro
moment where audio is the ONLY signal — but the user has the native
ringing UI as visual context.

**Spoken (TTS) strings — 2 new, in Curro's voice**:

| ID | Spanish | Notes |
|---|---|---|
| `copy_incoming_call_announce` | "Te está llamando %1$s." | unknown-alias case; %1$s = contact display name |
| `copy_incoming_call_announce_with_alias` | "Te está llamando %1$s, %2$s." | with-alias case; %1$s = alias, %2$s = display name |

**Visual / config-screen strings — 2 new**:

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_incoming_call_help` | "Curro avisa por voz quién te llama y responde con tu permiso. Activa solo si estás cómodo con que Curro toque el teléfono." | long help (referenced; not yet rendered) |
| `copy_config_incoming_call_perm_needed` | "Necesito permisos de teléfono para anunciar las llamadas. Otórgalos o el modo no se activa." | toast on permission denial |

4 new strings total. (The section title `copy_config_section_incoming_call`
and short help `copy_config_incoming_call_help_short` were landed in
SF-8.1.)

**`brand-design` COPY table**: add "Incoming-call mode (Phase 8 — SF-8.7)"
section with all 4 rows. Mark the two spoken lines as Curro's-voice
provenance per spec §2.

## Performance Considerations

- The `CurroInCallService` runs in the app's process (Android default).
  The launched coroutine uses the `@ApplicationScope` so it survives the
  service's `onCallRemoved` (the announcement must finish even if the
  call is rejected externally).
- `contactsProvider.findByNumber` uses `PhoneLookup` which is the optimised
  Contacts API path (indexed by normalised number — sub-millisecond).
- `aliasRepo.observeAll().first()` is a single Room query.
- `ttsClient.speak` is the existing suspending API — typical Spanish
  utterance latency 200–400 ms on the Redmi 15.
- `sttClient.listenForCallResponse()` — short STT pass; the impl pins a
  ~5 s silence-timeout (same as `listenForConfirmation` per `SttClient`'s
  existing contract).
- Total wall-clock budget from `onCallAdded` to `call.answer()`: ~3–5 s
  (the user has the ringtone playing through this — the announcement
  speaks OVER the ringtone, which is fine because the user is hearing
  Curro's voice first).

## Testing Requirements

- [ ] **FSM**: N/A (incoming-call mini-flow is outside the main FSM).
- [ ] **`CurroInCallService`** — Robolectric, 7 cases:
      1. `onCallAdded_ringing_knownContact_withAlias_announcesWithAlias`.
      2. `onCallAdded_ringing_knownContact_noAlias_announcesWithDisplayName`.
      3. `onCallAdded_ringing_unknownNumber_doesNotAnnounce_doesNotAnswer_doesNotDisconnect`.
      4. `onCallAdded_ringing_settingDisabled_returnsEarly_evenIfComponentEnabled`.
      5. `onCallAdded_ringing_userSaysSi_callsCallAnswer`.
      6. `onCallAdded_ringing_userSaysNo_callsCallDisconnect`.
      7. `onCallAdded_ringing_userSaysOther_doesNothing_letCallRing`.
      8. `onCallAdded_NOT_ringing_returnsEarly_doesNothing`.
- [ ] **`SystemSttClient.listenForCallResponse`** — 5 cases:
      1. `si_mapsTo_Answer`.
      2. `coge_mapsTo_Answer`.
      3. `responde_mapsTo_Answer`.
      4. `no_mapsTo_Decline`.
      5. `cuelga_mapsTo_Decline`.
      6. `random_text_mapsTo_Other`.
      7. `emptyResult_mapsTo_Failed`.
- [ ] **`ContactsContractProvider.findByNumber`** — Robolectric + shadow
      contacts, 4 cases:
      1. `findByNumber_existingContact_returnsContact`.
      2. `findByNumber_unknownNumber_returnsNull`.
      3. `findByNumber_securityException_returnsNull`.
      4. `findByNumber_normalisesWhitespace`.
- [ ] **`IncomingCallModeController`** — Robolectric + `ShadowPackageManager`,
      4 cases:
      1. `enable_setsComponentEnabled_AND_setsSettingTrue_inThatOrder`.
      2. `disable_setsComponentDisabled_AND_setsSettingFalse_inThatOrder`.
      3. `isComponentEnabled_returnsCurrentState`.
      4. `enable_thenDisable_returnsToDisabledComponent`.
- [ ] **`IncomingCallModeToggleHandler`** — 4 cases:
      1. `handle_enable_publishesRequestPhonePermissions`.
      2. `handle_disable_callsControllerDisable`.
      3. `onPermissionResult_grantedAll_callsControllerEnable`.
      4. `onPermissionResult_partialGrant_publishesShowToast`.
- [ ] **`TelemetryGuardrail` incoming-call event** — 5 cases:
      1. `incoming_call_announced_with_outcome_answered_allowed`.
      2. `_declined_allowed`.
      3. `_ignored_allowed`.
      4. `_no_response_allowed`.
      5. `_with_phone_number_prop_rejected`.
      6. `_with_contact_name_prop_rejected`.
- [ ] **`IncomingCallModeOffInvariantTest`** — instrumented, 2 cases:
      1. `componentDisabled_atFreshInstall_NOT_in_queryIntentServices_result`.
      2. `componentEnabled_afterControllerEnable_IS_in_queryIntentServices_result`.
- [ ] **Real Redmi 15 smoke** (the critical verifications):
      - With toggle OFF (fresh install), ask a friend to call → the phone
        rings native (HyperOS UI), no Curro voice, no Curro overlay.
        **Pin: this is the critical invariant — verify with conviction
        before signing off.**
      - `adb shell dumpsys telecom | grep -i curro` → no Curro service
        listed when toggle is OFF.
      - Flip toggle ON → grant the three permissions → ask the friend to
        call → Curro announces "Te está llamando [name]" → say "sí" → call
        answered.
      - Same flow but say "no" → call disconnected.
      - Same flow but say nothing → phone keeps ringing native; user can
        tap manually.
      - Unknown caller (e.g. from a test SIM) → no announcement; rings
        native. Telemetry shows `outcome = "ignored"`.
      - Flip toggle OFF again → ask the friend to call → phone rings
        native (verify the off-state invariant after a state cycle).

## Implementation Notes

**Implementer**: `voice-pipeline-engineer`. This SF deliberately splits from
the `android-developer` batch because telephony + STT vocabulary mapping +
the structural-off-state invariant are this agent's domain. Schedule the
SF-8.7 dev pass AFTER the other 9 Phase-8 SFs land, so:
1. SF-8.1's `incomingCallModeEnabled` flow exists.
2. SF-8.4's `TtsClient` config infrastructure is settled.
3. The SF-8.1 toggle row is already drawn and only its `onChange` needs
   the controller wiring.

**Spec update**: at implementation time, edit `docs/curro-spec-v1.0.md` §10
to add the three permission rows + a v1.2 row in the revision history:
"SF-8.7 (US-056) wired the opt-in incoming-call mode; §10 updated with
READ_PHONE_STATE / ANSWER_PHONE_CALLS / MANAGE_OWN_CALLS rows + the
BIND_INCALL_SERVICE service-binding permission note." Commit the spec
change in the same commit as the SF-8.7 implementation.

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/data/telephony/CurroInCallService.kt`
- `app/src/main/java/com/curro/app/data/telephony/IncomingCallModeController.kt`
- `app/src/main/java/com/curro/app/data/telephony/IncomingCallModeToggleHandler.kt`
- `app/src/main/java/com/curro/app/data/telephony/LauncherSideEffectBus.kt`
- `app/src/main/java/com/curro/app/di/TelephonyModule.kt`
- `app/src/test/java/com/curro/app/data/telephony/CurroInCallServiceTest.kt`
- `app/src/test/java/com/curro/app/data/telephony/IncomingCallModeControllerTest.kt`
- `app/src/test/java/com/curro/app/data/telephony/IncomingCallModeToggleHandlerTest.kt`
- `app/src/test/java/com/curro/app/data/voice/SystemSttClientCallResponseTest.kt`
- `app/src/test/java/com/curro/app/data/contacts/ContactsContractProviderFindByNumberTest.kt`
- `app/src/test/java/com/curro/app/data/telemetry/TelemetryGuardrailIncomingCallTest.kt`
- `app/src/androidTest/java/com/curro/app/data/telephony/IncomingCallModeOffInvariantTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/domain/repository/SttClient.kt` (+1 method
  `listenForCallResponse` + `CallResponseVoice` sealed interface).
- `app/src/main/java/com/curro/app/data/voice/SystemSttClient.kt` (impl).
- `app/src/main/java/com/curro/app/domain/repository/ContactsProvider.kt`
  (+1 method `findByNumber`).
- `app/src/main/java/com/curro/app/data/contacts/ContactsContractProvider.kt`
  (impl).
- `app/src/main/java/com/curro/app/presentation/config/ConfigViewModel.kt`
  (wire `onEvent(ToggleChanged for incoming-call)`).
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt`
  (+1 side effect, +1 event, collect from `LauncherSideEffectBus`).
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`
  (register `RequestMultiplePermissions` launcher).
- `app/src/main/AndroidManifest.xml` (3 permissions + 1 service block).
- `app/src/main/res/values/strings.xml` (+4 entries).
- `app/src/main/java/com/curro/app/data/telemetry/TelemetryGuardrail.kt`
  (+1 event in `ALLOWED_PROPS`).
- `.claude/skills/brand-design/SKILL.md` (+4 rows).
- `docs/curro-spec-v1.0.md` (§10 + revision-history → v1.2).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.7 Incoming-call mode + CurroInCallService. Size L; split to voice-pipeline-engineer. |
