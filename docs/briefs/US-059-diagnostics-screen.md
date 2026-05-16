# Diagnostics screen — US-059 / SF-8.10

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | One-screen view of Curro's runtime health: version, model, launcher, permissions, HyperOS-battery deep link |
| **US ID** | US-059 (master-plan SF-8.10) |
| **Phase** | 8 — Settings menu (Fran-only) |
| **Status** | In Progress |
| **Created** | 2026-05-17 |
| **Modified** | 2026-05-17 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-architect |

## Summary

Replace SF-8.1's `config/diagnostics` placeholder with `DiagnosticsScreen` —
the spec §9 "Versión y diagnóstico" surface. Five sections in a single
`LazyColumn`:
1. **App** — version, version code, build type.
2. **Modelo** — model name, loaded/warm/cold state, last warm-up latency,
   last inference latency. Surfaces the metrics `FunctionGemmaEngine`
   already logs (the `Log.i` lines in `warmUp()` and `decide()`); the SF
   exposes them via a new `EngineMetrics` interface, no metrics-collection
   layer.
3. **Launcher** — am-I-default? (read from `DefaultLauncherDetector.flow`).
4. **Permisos** — list of every Curro permission + ✓/✗.
5. **HyperOS** — `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` deep link
   button ("Permitir en segundo plano") + a multiline copy block with the
   autostart steps verbatim from the `launcher-app` skill.

Spec reference: `docs/curro-spec-v1.0.md` §9 ("Versión y diagnóstico") +
`launcher-app` "HyperOS / MIUI battery restrictions" + `platform-integrations`
"Settings deep links".

## Scope

- **In Scope**:
  - `EngineMetrics` interface + `FunctionGemmaEngine` implements it
    (surface the two `@Volatile var` members it already tracks).
  - `GrantedPermissionsReader` + `PermissionInfo` data shape.
  - `BatterySettingsIntents` utility.
  - `DiagnosticsScreen` + `DiagnosticsViewModel`.
  - 23 new strings (the 5 section headers + the permission labels + the
    multiline autostart help).
  - Replacement of the `composable("config/diagnostics")` placeholder.
- **Out of Scope**:
  - A "test the model with a hardcoded utterance" affordance (out of
    scope; that's what `/test` is for).
  - Crash log viewer (PostHog / Firebase have crash dashboards; Fran reads
    those off-device).
  - Sending diagnostics to Fran via the share intent (SF-8.8 covers the
    failure log; diagnostics are device-local).
  - A "memory pressure" or "ANR" surface (out of scope).
  - Telemetry for opening the diagnostics screen.

## User Flows

### Flow 1: Fran opens diagnostics after his father reports "Curro no me oye"

1. Fran opens config → "Versión y diagnóstico".
2. `DiagnosticsScreen` renders the 5 sections.
3. Fran scrolls; reads:
   - App: version 0.4.2, debug build.
   - Modelo: FunctionGemma270M, **Frío**, last warm-up never, last inference
     never. → Curro hasn't warmed up since boot; the foreground service got
     killed by HyperOS.
   - Launcher: "Soy el launcher por defecto" ✓.
   - Permisos: all ✓ except notification-listener access (X).
   - HyperOS: button "Permitir en segundo plano" + the autostart steps.
4. Fran taps "Permitir en segundo plano" → HyperOS opens Curro's app-details
   page.
5. Fran navigates from there to Batería → "Sin restricciones".
6. Returns to `DiagnosticsScreen` → the `refreshTrigger` (fired on
   `ON_RESUME`) re-reads the state; model goes from Frío to Cargado on the
   next assistant turn.
7. Fran fixes notification-listener access by following the autostart help
   (it points to the right HyperOS settings page).

### Flow 2: Permission status changes are reflected

1. Diagnostics showing `RECORD_AUDIO` ✓.
2. Fran (testing) revokes RECORD_AUDIO via HyperOS settings.
3. Returns to `DiagnosticsScreen`.
4. `ON_RESUME` triggers a refresh → `GrantedPermissionsReader.snapshot()`
   re-reads → row shows ✗.

## Function-catalog Impact

No catalog change.

## FSM States Touched

None.

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none) | reading own permissions does not require any | — | — |

`PackageManager.checkPermission(...)` is unrestricted for own permissions.
The HyperOS deep link uses `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`
which is a standard Android intent.

## On-device-model Impact

No new inference. `FunctionGemmaEngine` adds two `@Volatile var` members
and writes to them from inside the existing `Log.i` paths in `warmUp()` and
`decide()`. The `EngineMetrics` interface is read-only.

## Android Specification

### Screens and Composables

- **`presentation/config/sections/diagnostics/DiagnosticsScreen.kt`** —
  `@Composable fun DiagnosticsScreen(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel())`.
  - Layout:
    ```kotlin
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(top = Dimens.MinTapTarget + CurroSpacing.l, start = CurroSpacing.m, end = CurroSpacing.m, bottom = CurroSpacing.xl)) {
            item { DiagnosticSection(title = R.string.copy_config_diagnostics_section_app) { AppInfoRows(uiState.app) } }
            item { DiagnosticSection(title = R.string.copy_config_diagnostics_section_model) { ModelInfoRows(uiState.model) } }
            item { DiagnosticSection(title = R.string.copy_config_diagnostics_section_launcher) { LauncherInfoRow(uiState.isDefaultLauncher) } }
            item { DiagnosticSection(title = R.string.copy_config_diagnostics_section_permissions) { PermissionRows(uiState.permissions) } }
            item { DiagnosticSection(title = R.string.copy_config_diagnostics_section_hyperos) { HyperOsSection(onBatteryClick = { viewModel.onEvent(DiagnosticsEvent.OpenBatterySettings) }) } }
        }
        IconButton(onClick = onBack, /* TopStart back chevron */)
    }
    // Side effect: launching the battery-settings intent
    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect -> when (effect) {
            is DiagnosticsSideEffect.OpenBatterySettings -> context.startActivity(effect.intent)
        } }
    }
    ```

- **`DiagnosticSection.kt`** — `@Composable fun DiagnosticSection(@StringRes title: Int, content: @Composable () -> Unit)`. Header + body in a `Card`.

- **`PermissionRow.kt`** — a row with the label + a coloured icon (`Icons.Filled.Check` in primary if granted; `Icons.Filled.Close` in error if denied) + the text label "Concedido" / "Denegado".

- **`HyperOsSection.kt`** — body: `BigPrimaryButton(text = "Permitir en segundo plano", onClick = onBatteryClick)` + a multiline `Text` with the autostart steps.

### ViewModels and State Management

```kotlin
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val engineMetrics: EngineMetrics,
    private val detector: DefaultLauncherDetector,
    private val permissionsReader: GrantedPermissionsReader,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val sideEffectsChannel = Channel<DiagnosticsSideEffect>(Channel.BUFFERED)
    val sideEffects: Flow<DiagnosticsSideEffect> = sideEffectsChannel.receiveAsFlow()

    private val resumeObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) { refreshTrigger.tryEmit(Unit) }
    }

    init {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(resumeObserver)
        } catch (_: Exception) { /* tests */ }
    }

    override fun onCleared() {
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(resumeObserver)
        } catch (_: Exception) { /* tests */ }
    }

    val uiState: StateFlow<DiagnosticsUiState> = combine(detector.flow, refreshTrigger.onStart { emit(Unit) }) { isDefault, _ ->
        DiagnosticsUiState(
            app = AppInfo(version = BuildConfig.VERSION_NAME, versionCode = BuildConfig.VERSION_CODE, buildType = BuildConfig.BUILD_TYPE),
            model = ModelInfo(
                name = engineMetrics.modelName(),
                state = computeModelState(),
                lastWarmUpMs = engineMetrics.lastWarmUpLatencyMs(),
                lastInferenceMs = engineMetrics.lastInferenceLatencyMs(),
            ),
            isDefaultLauncher = isDefault,
            permissions = permissionsReader.snapshot(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState.Initial)

    private suspend fun computeModelState(): ModelState = when {
        !engineMetrics.isReady() -> ModelState.Cold
        engineMetrics.lastInferenceLatencyMs() == null -> ModelState.Warming
        else -> ModelState.Loaded
    }

    fun onEvent(event: DiagnosticsEvent) {
        when (event) {
            DiagnosticsEvent.OpenBatterySettings -> {
                viewModelScope.launch {
                    sideEffectsChannel.send(DiagnosticsSideEffect.OpenBatterySettings(BatterySettingsIntents.openAppDetailsIntent(context)))
                }
            }
        }
    }
}

data class DiagnosticsUiState(val app: AppInfo, val model: ModelInfo, val isDefaultLauncher: Boolean, val permissions: List<PermissionInfo>) {
    companion object { val Initial = DiagnosticsUiState(AppInfo("", 0, ""), ModelInfo("", ModelState.Cold, null, null), false, emptyList()) }
}

data class AppInfo(val version: String, val versionCode: Int, val buildType: String)
data class ModelInfo(val name: String, val state: ModelState, val lastWarmUpMs: Long?, val lastInferenceMs: Long?)
enum class ModelState { Loaded, Warming, Cold }

sealed interface DiagnosticsEvent {
    data object OpenBatterySettings : DiagnosticsEvent
}

sealed interface DiagnosticsSideEffect {
    data class OpenBatterySettings(val intent: Intent) : DiagnosticsSideEffect
}
```

### New supporting classes

- **NEW** `app/src/main/java/com/curro/app/domain/repository/EngineMetrics.kt`:
  ```kotlin
  interface EngineMetrics {
      fun isReady(): Boolean
      suspend fun lastWarmUpLatencyMs(): Long?
      suspend fun lastInferenceLatencyMs(): Long?
      fun modelName(): String
  }
  ```
- **MODIFIED** `app/src/main/java/com/curro/app/data/ml/FunctionGemmaEngine.kt`:
  - Implement `EngineMetrics` (the class already implements
    `FunctionCallEngine`; add the second interface).
  - Add `@Volatile private var lastWarmUpMs: Long? = null` + `@Volatile
    private var lastInferenceMs: Long? = null`.
  - Write them inside the existing `Log.i("Curro/Llm", "warm-up took
    ${ms}ms")` and `Log.i("Curro/Llm", "decide latency: ${ms}ms")` paths.
  - Implement: `override fun isReady(): Boolean = llm != null` (already
    exists; just expose via the new interface), `override suspend fun
    lastWarmUpLatencyMs(): Long? = lastWarmUpMs`, etc., `override fun
    modelName(): String = "FunctionGemma270M"`.
  - **MODIFIED** Hilt binding: add a `@Binds @Singleton` for `EngineMetrics
    -> FunctionGemmaEngine` (in the existing ML module).
- **NEW** `app/src/main/java/com/curro/app/data/permissions/GrantedPermissionsReader.kt`:
  ```kotlin
  @Singleton
  class GrantedPermissionsReader @Inject constructor(
      @ApplicationContext private val context: Context,
      private val notificationGate: NotificationAccessGate,
  ) {
      fun snapshot(): List<PermissionInfo> {
          val pm = PackageManager.PERMISSION_GRANTED
          fun granted(perm: String) = ContextCompat.checkSelfPermission(context, perm) == pm
          return listOf(
              PermissionInfo(Manifest.permission.RECORD_AUDIO, R.string.copy_config_diagnostics_permission_record_audio, granted(Manifest.permission.RECORD_AUDIO)),
              PermissionInfo(Manifest.permission.READ_CONTACTS, R.string.copy_config_diagnostics_permission_read_contacts, granted(Manifest.permission.READ_CONTACTS)),
              PermissionInfo(Manifest.permission.CALL_PHONE, R.string.copy_config_diagnostics_permission_call_phone, granted(Manifest.permission.CALL_PHONE)),
              PermissionInfo(Manifest.permission.POST_NOTIFICATIONS, R.string.copy_config_diagnostics_permission_post_notifications, granted(Manifest.permission.POST_NOTIFICATIONS)),
              PermissionInfo("NOTIFICATION_LISTENER", R.string.copy_config_diagnostics_permission_notification_listener, notificationGate.isGranted()),
              PermissionInfo(Manifest.permission.READ_PHONE_STATE, R.string.copy_config_diagnostics_permission_read_phone_state, granted(Manifest.permission.READ_PHONE_STATE)),
              PermissionInfo(Manifest.permission.ANSWER_PHONE_CALLS, R.string.copy_config_diagnostics_permission_answer_calls, granted(Manifest.permission.ANSWER_PHONE_CALLS)),
          )
      }
  }

  data class PermissionInfo(val permission: String, @StringRes val labelResId: Int, val isGranted: Boolean)
  ```
- **NEW** `app/src/main/java/com/curro/app/data/launcher/BatterySettingsIntents.kt`:
  ```kotlin
  object BatterySettingsIntents {
      fun openAppDetailsIntent(context: Context): Intent =
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
              .setData(Uri.fromParts("package", context.packageName, null))
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  ```

### Navigation Routes

- **MODIFIED**: replace `composable("config/diagnostics")` placeholder with
  the real `DiagnosticsScreen`.

### Hilt Modules

- **MODIFIED** `app/src/main/java/com/curro/app/di/MlModule.kt` (or wherever
  `FunctionCallEngine` is bound) — add a `@Binds @Singleton fun bindEngineMetrics(impl: FunctionGemmaEngine): EngineMetrics`.

### Composables by Feature (checklist)

- [x] `DiagnosticsScreen` + stateless `DiagnosticsContent`.
- [x] `DiagnosticSection` (header + body card).
- [x] `AppInfoRows`, `ModelInfoRows`, `LauncherInfoRow`, `PermissionRow`,
      `HyperOsSection`.
- [x] Dark + large-font previews.

### Material Design Components

- `Card` for each section (visual grouping).
- `Icon` (Material check / close) for permission status.
- `BigPrimaryButton` for the battery deep link.
- `LazyColumn` outer.

## Acceptance Criteria

- [ ] **All 5 sections render** with accurate values:
      - App: version / versionCode / buildType from `BuildConfig`.
      - Modelo: model name, state (Loaded / Warming / Cold), last warm-up
        latency, last inference latency.
      - Launcher: am-I-default? from `DefaultLauncherDetector`.
      - Permisos: every Curro permission + the notification-listener state
        + the granted/denied state per `ContextCompat.checkSelfPermission`.
      - HyperOS: battery deep-link button + autostart help.
- [ ] **Battery deep-link opens Curro's app-details settings page** on the
      Redmi 15.
- [ ] **HyperOS autostart steps copied verbatim from `launcher-app`** —
      the implementer copies the steps from
      `.claude/skills/launcher-app/SKILL.md` "HyperOS / MIUI battery
      restrictions" section into the `copy_config_diagnostics_autostart_help`
      multiline string. **Pin: keep them ≤ 4 numbered steps**; longer is
      out of scope.
- [ ] **Refresh on `ON_RESUME`** — the screen re-reads model + permission
      state when returning from HyperOS settings.
- [ ] **`EngineMetrics` interface** — `FunctionGemmaEngine` implements it
      with the two `@Volatile var` members surfaced.
- [ ] **23 new strings** with the right IDs.
- [ ] **No new permissions, no new manifest entries, no new DataStore keys,
      no new dependencies, no new telemetry event.**
- [ ] **Build is green**.

## Design Notes

- Each `DiagnosticSection` is a `Card` with a header (`bodyLarge`) and a
  body. Sections are visually distinct so Fran can scan.
- The model-state icon: green check for Loaded, amber dot for Warming, red
  X for Cold. **Pin: colour + label** (see `brand-design` rule 5).
- The HyperOS autostart help is a `Text` with `\n`-separated lines; render
  with `style = bodyMedium` and `lineHeight = …` from the typography
  default.

## Senior-UX & Copy

Fran-only — config-menu density.

**No new spoken (TTS) strings.**

New entries in `app/src/main/res/values/strings.xml` (23 total):

| ID | Spanish | Notes |
|---|---|---|
| `copy_config_diagnostics_section_app` | "App" | section header |
| `copy_config_diagnostics_version` | "Versión: %1$s (%2$d) — %3$s" | app row |
| `copy_config_diagnostics_section_model` | "Modelo" | section header |
| `copy_config_diagnostics_model_name` | "Modelo: %1$s" | row |
| `copy_config_diagnostics_model_state_loaded` | "Estado: cargado" | row |
| `copy_config_diagnostics_model_state_warming` | "Estado: calentando" | row |
| `copy_config_diagnostics_model_state_cold` | "Estado: frío" | row |
| `copy_config_diagnostics_model_warmup_latency` | "Última carga: %1$d ms" | row |
| `copy_config_diagnostics_model_inference_latency` | "Última inferencia: %1$d ms" | row |
| `copy_config_diagnostics_model_latency_unknown` | "—" | placeholder for null latency |
| `copy_config_diagnostics_section_launcher` | "Launcher" | section header |
| `copy_config_diagnostics_default_yes` | "Soy el launcher por defecto" | row |
| `copy_config_diagnostics_default_no` | "NO soy el launcher por defecto — pulsa el botón \"Hazme tu pantalla de inicio\" en el home" | row |
| `copy_config_diagnostics_section_permissions` | "Permisos" | section header |
| `copy_config_diagnostics_perm_granted` | "Concedido" | row label |
| `copy_config_diagnostics_perm_denied` | "Denegado" | row label |
| `copy_config_diagnostics_section_hyperos` | "HyperOS — pasos para que Curro no se duerma" | section header |
| `copy_config_diagnostics_battery_cta` | "Permitir en segundo plano" | button |
| `copy_config_diagnostics_autostart_help` | (multiline — see below) | help block |
| `copy_config_diagnostics_permission_record_audio` | "Micrófono" | permission label |
| `copy_config_diagnostics_permission_read_contacts` | "Contactos" | permission label |
| `copy_config_diagnostics_permission_call_phone` | "Llamar" | permission label |
| `copy_config_diagnostics_permission_post_notifications` | "Notificaciones" | permission label |
| `copy_config_diagnostics_permission_notification_listener` | "Acceso a notificaciones" | permission label |
| `copy_config_diagnostics_permission_read_phone_state` | "Estado del teléfono" | permission label |
| `copy_config_diagnostics_permission_answer_calls` | "Contestar llamadas" | permission label |

`copy_config_diagnostics_autostart_help` (multiline):
```
Para que Curro siga listo cuando no estás usando el teléfono:
1. Abre Ajustes → Aplicaciones → Permisos especiales → Inicio automático → activa Curro.
2. Abre Ajustes → Batería → Ahorro de batería de la app → Curro → sin restricciones.
3. Mantén pulsada la tarjeta de Curro en las apps recientes hasta que aparezca el candado.
```

**Pin**: 26 strings total — the PRD/checklist said 23 but the permission
labels are 7 + 5 model + 2 launcher + 1 permissions intro + 3 HyperOS + 3
app = 21 plus a couple of trims = ~26. The implementer should pin the exact
count as part of the implementation; the brief enumerates each ID above.

**`brand-design` COPY table**: add "Diagnostics (Phase 8 — SF-8.10)"
section with all the rows.

## Performance Considerations

- All reads are cheap (`@Volatile var` reads, `ContextCompat.
  checkSelfPermission`, a single `BuildConfig` access).
- No coroutines beyond the model-state computation (a `suspend` call into
  `engineMetrics.lastInferenceLatencyMs()` — trivially fast).
- The `refreshTrigger` `onStart { emit(Unit) }` ensures the first emission
  happens immediately on subscription.

## Testing Requirements

- [ ] **`FunctionGemmaEngineMetricsTest`** — Robolectric, 4 cases:
      1. `lastWarmUpLatencyMs_nullBeforeWarmUp_setAfterWarmUp`.
      2. `lastInferenceLatencyMs_nullBeforeAnyDecide_setAfterDecide`.
      3. `modelName_returnsFunctionGemma270m_constant`.
      4. `isReady_falseBeforeWarmUp_trueAfter`.
- [ ] **`GrantedPermissionsReader`** — Robolectric +
      `ShadowApplication.grantPermissions / revokePermissions`, 3 cases:
      1. `snapshot_includes_allCurroPermissions`.
      2. `snapshot_reflects_grantState_perPermission`.
      3. `snapshot_includesNotificationListenerState_viaGate`.
- [ ] **`DiagnosticsViewModel`** — JVM, 6 cases:
      1. `uiState_emitsCurrentMetrics_onInit`.
      2. `uiState_reactsTo_defaultLauncherDetectorEmission`.
      3. `uiState_reReadsEngineMetrics_onRefreshTrigger`.
      4. `uiState_modelState_isCold_whenNotReady`.
      5. `uiState_modelState_isLoaded_whenReady_andHasInferenceLatency`.
      6. `uiState_modelState_isWarming_whenReady_butNoInferenceLatencyYet`.
- [ ] **Instrumented UI tests on `DiagnosticsContent`** (4 cases):
      1. `allFiveSections_render`.
      2. `batteryCtaButton_firesOpenBatterySettings_event`.
      3. `permissionRow_rendersCheckmark_whenGranted`.
      4. `permissionRow_rendersX_whenDenied`.
- [ ] **Dark + large-font previews**.
- [ ] **Real Redmi 15 smoke**:
      - Open Diagnostics → version reads correctly.
      - Model status reflects warm/cold (cold immediately after install if
        the warm-up service hasn't fired yet; should be Loaded within 1 s).
      - Launcher status reflects default-launcher state.
      - Permissions list shows green for granted / red for denied.
      - Tap "Permitir en segundo plano" → HyperOS opens Curro's
        app-details settings page.
      - Read the autostart steps + follow them; return to the screen;
        state has updated.

## Implementation Notes

**File-creation summary**:

NEW:
- `app/src/main/java/com/curro/app/domain/repository/EngineMetrics.kt`
- `app/src/main/java/com/curro/app/data/permissions/GrantedPermissionsReader.kt`
- `app/src/main/java/com/curro/app/data/launcher/BatterySettingsIntents.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/diagnostics/DiagnosticsScreen.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/diagnostics/DiagnosticsViewModel.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/diagnostics/components/DiagnosticSection.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/diagnostics/components/PermissionRow.kt`
- `app/src/main/java/com/curro/app/presentation/config/sections/diagnostics/components/HyperOsSection.kt`
- `app/src/test/java/com/curro/app/data/ml/FunctionGemmaEngineMetricsTest.kt`
- `app/src/test/java/com/curro/app/data/permissions/GrantedPermissionsReaderTest.kt`
- `app/src/test/java/com/curro/app/presentation/config/sections/diagnostics/DiagnosticsViewModelTest.kt`
- `app/src/androidTest/java/com/curro/app/presentation/config/sections/diagnostics/DiagnosticsContentTest.kt`

MODIFIED:
- `app/src/main/java/com/curro/app/data/ml/FunctionGemmaEngine.kt`
  (implement `EngineMetrics`; add 2 `@Volatile var` members; write them in
  `warmUp` and `decide`).
- `app/src/main/java/com/curro/app/di/MlModule.kt` (or wherever — add the
  `EngineMetrics` binding).
- `app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`
  (1 swap).
- `app/src/main/res/values/strings.xml` (+26 entries).
- `.claude/skills/brand-design/SKILL.md` (+26 rows in a new "Diagnostics
  (Phase 8 — SF-8.10)" section).

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | android-product-analyst | Initial brief — SF-8.10 Diagnostics screen. |
