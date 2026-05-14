# US-009 — `CATEGORY_HOME` + "Hazme tu pantalla de inicio" via `RoleManager`

> Implementation brief for **SF-1.1** (`docs/master-plan.md` → Phase 1 → §1).
> US-007 landed the navigation shell (`CurroNavHost`, `MainActivity` with
> `singleTask` + `clearTaskOnLaunch` + portrait, `LauncherPlaceholderScreen`,
> `ConfigMenuPlaceholderScreen`) deliberately **without** `CATEGORY_HOME` —
> exactly so SF-1.1 could ship the HOME intent filter atomically alongside the
> `RoleManager.ROLE_HOME` flow and the "Hazme tu pantalla de inicio"
> fallback CTA. US-009 flips Curro into being the actual home screen of the
> Redmi 15: the manifest declares the HOME category, `RoleManager` is wired
> to offer the system role-request chooser on first run, a
> `DefaultLauncherDetector` re-emits on `ON_RESUME` so HyperOS "forgetting the
> default" after an update is a *visible-affordance recovery* path (the CTA
> reappears, not a silent failure), and the launcher placeholder gains a
> `LauncherViewModel` so the `BigPrimaryButton` CTA is gated reactively on
> the resolved-default state. This is Phase 1's first sub-feature; SF-1.2
> through SF-1.5 then lay the real launcher home (clock, mic button, app
> grid, "Más apps") on top of the shell US-009 finishes.
>
> **Architect involvement: NOT REQUIRED.** Every load-bearing decision is
> resolved upstream: the `launcher-app` skill pins the full HOME intent-filter
> shape and the `RoleManager.ROLE_HOME` flow; spec §11 and §14 step 1 pin
> the "make me default" requirement and the validation gate; US-005 already
> landed the canonical Spanish string `copy_home_make_default` ("Hazme tu
> pantalla de inicio") that the brief REUSES (the prompt's
> suggested-new-ID `copy_make_me_default` is a duplicate — the brief flags
> the reuse explicitly so the developer does not add a second string);
> US-006 shipped `BigPrimaryButton` (the right brick for the CTA);
> US-007 pinned the No-Double-Padding rule that `LauncherPlaceholderScreen`
> already respects (no `Scaffold` / `TopAppBar` / `statusBarsPadding()` —
> only `CurroNavHost`'s root `Scaffold` pads). The shape decisions for the
> new files (interface vs class boundary on the detector, where the Flow's
> resume signal comes from, how the `ActivityResultLauncher` is wired into
> the screen) are pinned in §Implementation Notes and below in the file
> shapes — the developer follows.

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `CATEGORY_HOME` + `RoleManager.ROLE_HOME` flow + "Hazme tu pantalla de inicio" fallback CTA |
| **US ID** | US-009 |
| **SF ID** | SF-1.1 — master-plan |
| **Phase** | 1 — Launcher base |
| **Status** | In Progress |
| **Created** | 2026-05-14 |
| **Modified** | 2026-05-14 |
| **PM Owner** | Fran (Claude `android-product-analyst`) |
| **Architect** | Not required — `launcher-app` skill + spec §11/§14 + US-005's canonical copy + US-006's `BigPrimaryButton` + US-007's nav shell pin every load-bearing decision; shape details are pinned in §Implementation Notes below. |

## Summary

Flip Curro into being the actual Android launcher. Three load-bearing pieces:

1. **Manifest** — `MainActivity`'s existing intent-filter gains two new
   `<category>` lines: `android.intent.category.HOME` and
   `android.intent.category.DEFAULT`. Combined with the existing
   `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`,
   Curro becomes selectable in the system "Default home app" picker and
   keeps appearing in the app drawer. All US-007 `<activity>` attributes
   (`singleTask`, `clearTaskOnLaunch`, `stateNotNeeded`, portrait,
   `windowSoftInputMode="adjustResize"`) are byte-identical — US-007
   deliberately pre-shipped them so this SF reduces to a manifest two-line
   delta.

2. **`RoleManager.ROLE_HOME` flow** — a `DefaultLauncherDetector` resolves
   the system's current home Activity via `PackageManager.resolveActivity`
   and exposes both a snapshot `isDefault(): Boolean` and a
   `Flow<Boolean>` that re-emits on every `ProcessLifecycleOwner` `ON_RESUME`
   (so HyperOS "forgetting the default" after an update surfaces as the
   CTA reappearing, not as a silent failure). A `MakeMeDefaultLauncher`
   utility wraps `RoleManager.createRequestRoleIntent(ROLE_HOME)` and
   provides `Settings.ACTION_HOME_SETTINGS` as the fallback for OEMs
   that don't surface the chooser or for the "don't ask again" path.

3. **`LauncherPlaceholderScreen` gains a CTA** — a `LauncherViewModel`
   (`StateFlow<LauncherUiState>` keyed off the detector's flow) tells the
   placeholder whether to render the `BigPrimaryButton` rendering the
   existing canonical string `R.string.copy_home_make_default`
   ("Hazme tu pantalla de inicio") above the existing
   "Ajustes (depuración)" debug `TextButton`. The CTA appears **only**
   when Curro is not the resolved default; it disappears reactively the
   moment the resolver flips. Tapping it fires an
   `ActivityResultLauncher<Intent>` that prefers the role-request
   intent and falls back to the settings intent when the role-request
   intent is null.

**No new string resource is introduced.** US-005 already landed
`copy_home_make_default = "Hazme tu pantalla de inicio"` in `strings.xml`
(line 148) and in `brand-design/SKILL.md`'s canonical COPY table
(line 595). The brief REUSES the existing canonical ID. The prompt's
suggested `copy_make_me_default` is a duplicate that would split a single
canonical string across two IDs — explicitly rejected. The developer does
**not** edit `strings.xml` or `brand-design/SKILL.md` for this SF.

**No new permission is required.** `RoleManager` is consent-based (the
system role-request dialog is the user's grant — there is no manifest
`<uses-permission>` for `ROLE_HOME`); `PackageManager.resolveActivity`
needs none; `Settings.ACTION_HOME_SETTINGS` is a public intent.

**No FSM-reset-on-`onNewIntent`** is wired here — the FSM doesn't exist
yet (Phase 5). US-007 already established that `singleTask` +
`clearTaskOnLaunch="true"` + `stateNotNeeded="true"` deliver the
"HOME returns to a clean launcher" story for Phase 1; SF-5.x will
override `MainActivity.onNewIntent()` to reset the FSM to `idle`. This
brief documents that as the right hook so SF-5.x knows where to plug in.

**No telemetry event** is emitted — US-008 landed the plumbing but
deliberately did not wire any feature event yet; this SF inherits that
discipline. (Were one to be added later, the natural one is a `home_role_grant_result`
event with property `{granted: bool}` and no PII; the `TelemetryGuardrail`
would accept it.)

**Master-plan ref**: SF-1.1 ("`CATEGORY_HOME` & 'make me the default'") —
the acceptance bar is "on the real Redmi 15, the user can set Curro as
the default launcher in one flow; pressing HOME returns to Curro, not
the stock launcher." **Spec ref**: §11 (launcher UX — the home is the
start destination; this is what makes the home actually be the home);
§14 step 1 ("Launcher base con `CATEGORY_HOME` declarado"); §14
"Decisiones cerradas" — Curro is a launcher, not a normal app; §10 —
the launcher itself needs no permission, the HOME category in the
manifest is enough. **Skills consumed**: `launcher-app` (the
authoritative `CATEGORY_HOME` intent-filter shape + the
`RoleManager.ROLE_HOME` flow + the HyperOS "forgets after updates"
note); `launcher-ui` (the senior-first CTA — `BigPrimaryButton` at
≥ 96 dp, audio-not-required since this is one-time setup affordance, but
the visual contract still applies); `accessibility-patterns` (the CTA's
`contentDescription` flows through `BigPrimaryButton`'s existing slot);
`compose-patterns` (the new ViewModel + `StateFlow` + stateless
`Content` composable boundary); `navigation-patterns` (the No-Double-Padding
rule preserved — the new CTA renders inside the existing
`LauncherPlaceholderScreen` `Column`, no extra `Scaffold`).

## Scope

### In Scope

- **`app/src/main/AndroidManifest.xml`** — two-line `<category>` delta on
  the existing `MainActivity` intent-filter:

  ```xml
  <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.HOME" />        <!-- NEW: SF-1.1 -->
      <category android:name="android.intent.category.DEFAULT" />     <!-- NEW: SF-1.1 -->
      <category android:name="android.intent.category.LAUNCHER" />
  </intent-filter>
  ```

  The placement: `HOME` and `DEFAULT` go **between** `MAIN` and `LAUNCHER`,
  matching the `launcher-app` skill's canonical example (line 41–46).
  The existing inline comment block at the top of the manifest (the
  Privacy boundary block and the permission-per-SF roadmap) is preserved
  byte-identical; the `CATEGORY_HOME → SF-1.1 (makes Curro the default
  launcher)` bullet in that comment block is updated to read
  `CATEGORY_HOME → US-009 (SF-1.1) — landed; see MainActivity intent-filter`.
  No other manifest edit (no new permission, no new component, no
  `application`-level attribute change).

- **`app/src/main/java/com/curro/app/data/launcher/DefaultLauncherDetector.kt`**
  (interface) and **`DefaultLauncherDetectorImpl.kt`** (impl) — both new files
  in a new sub-package `data/launcher/` (chosen over `data/apps/` because
  this concern is about the *launcher itself*, not about enumerating
  installed apps — the latter lives in `data/apps/`; the
  `PackageManager.resolveActivity` use here is the launcher's
  *self-identity* check, not a listing). The interface lives in `data/`
  rather than `domain/repository/` because (a) it's a launcher-platform
  concern by nature, not a domain-level abstraction worth pulling into
  pure-Kotlin, and (b) Phase 1 has no use case that would consume it
  from the domain layer — the launcher ViewModel injects it directly.
  If Phase 5+ ever needs it from a use case, promote then.

  ```kotlin
  package com.curro.app.data.launcher

  import kotlinx.coroutines.flow.Flow

  /**
   * Resolves whether Curro is the current default home-resolved Activity on
   * the device. Re-emits on every [androidx.lifecycle.Lifecycle.Event.ON_RESUME]
   * of [androidx.lifecycle.ProcessLifecycleOwner] so the UI reacts when
   * HyperOS "forgets" the default launcher after an OS update
   * (`launcher-app` skill — Xiaomi/HyperOS section).
   */
  interface DefaultLauncherDetector {
      /** True iff Curro's package is the system's currently resolved home Activity. */
      fun isDefault(): Boolean

      /**
       * Cold-on-collection flow that emits the current `isDefault()` on subscribe
       * and re-emits on every [ProcessLifecycleOwner] `ON_RESUME` — so coming back
       * from `Settings` after granting/revoking the role updates downstream
       * `StateFlow`s without an explicit refresh call.
       */
      val flow: Flow<Boolean>
  }
  ```

  ```kotlin
  package com.curro.app.data.launcher

  import android.content.Context
  import android.content.Intent
  import android.content.pm.PackageManager
  import androidx.lifecycle.Lifecycle
  import androidx.lifecycle.ProcessLifecycleOwner
  import androidx.lifecycle.flowWithLifecycle
  import com.curro.app.BuildConfig
  import dagger.hilt.android.qualifiers.ApplicationContext
  import kotlinx.coroutines.channels.awaitClose
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.callbackFlow
  import kotlinx.coroutines.flow.distinctUntilChanged
  import kotlinx.coroutines.flow.flowOn
  import kotlinx.coroutines.flow.onStart
  import kotlinx.coroutines.Dispatchers
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Default implementation of [DefaultLauncherDetector].
   *
   * Uses [PackageManager.resolveActivity] with `MATCH_DEFAULT_ONLY` to find the
   * system's chosen home Activity; compares its package to [BuildConfig.APPLICATION_ID]
   * (resolved at build time — never hard-code "com.curro.app"). The flow source is
   * [ProcessLifecycleOwner.get().lifecycle] — every `ON_RESUME` triggers a re-read,
   * and the flow `distinctUntilChanged()`s the boolean before emitting so spurious
   * resumes do not cause downstream `StateFlow` churn.
   *
   * Why `ProcessLifecycleOwner` and not the launcher Activity's own lifecycle:
   * the user typically *leaves* Curro to grant the role (the system chooser is
   * its own Activity), then comes back — the *process* lifecycle's `ON_RESUME`
   * fires reliably on return. The launcher Activity's `ON_RESUME` also fires,
   * but `ProcessLifecycleOwner` is the canonical "the user is interacting with
   * the app again" signal that survives the chooser overlay correctly.
   */
  @Singleton
  class DefaultLauncherDetectorImpl @Inject constructor(
      @ApplicationContext private val context: Context,
  ) : DefaultLauncherDetector {

      override fun isDefault(): Boolean {
          val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
          val resolved = context.packageManager.resolveActivity(
              intent,
              PackageManager.MATCH_DEFAULT_ONLY,
          )
          val resolvedPackage = resolved?.activityInfo?.packageName
          return resolvedPackage == BuildConfig.APPLICATION_ID
      }

      override val flow: Flow<Boolean> =
          callbackFlow {
              val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                  if (event == Lifecycle.Event.ON_RESUME) {
                      trySend(isDefault())
                  }
              }
              ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
              awaitClose {
                  ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
              }
          }
              .onStart { emit(isDefault()) }
              .distinctUntilChanged()
              .flowOn(Dispatchers.Main.immediate) // ProcessLifecycleOwner is main-thread
  }
  ```

  Notes on the choices above:
  - `MATCH_DEFAULT_ONLY` is the right flag — without it,
    `resolveActivity` may return the resolver-chooser pseudo-activity when
    no default is set, which we'd misread as "not Curro" anyway, but the
    flag makes the intent explicit.
  - `BuildConfig.APPLICATION_ID` is the canonical "my package" reference
    — never the string literal `"com.curro.app"` (which would silently
    break for any future flavour / suffix).
  - `Dispatchers.Main.immediate` for `flowOn` because
    `ProcessLifecycleOwner` is main-thread-bound; the `PackageManager`
    query in `isDefault()` is cheap (single-shot resolver call), so the
    main-thread cost is acceptable and avoids a thread-hop on every
    resume.
  - `distinctUntilChanged()` AFTER `onStart` so the initial emission
    isn't deduped against an undefined previous value, and so two
    consecutive `ON_RESUME`s with the same answer don't churn the
    downstream `StateFlow`.

- **`app/src/main/java/com/curro/app/data/launcher/MakeMeDefaultLauncher.kt`**
  — a new file. Same `data/launcher/` sub-package; same rationale (a
  launcher-platform concern, not a domain abstraction). Constructed via
  `@Inject` with `@ApplicationContext` — no separate Hilt module needed
  (the implicit constructor binding is sufficient).

  ```kotlin
  package com.curro.app.data.launcher

  import android.app.role.RoleManager
  import android.content.Context
  import android.content.Intent
  import android.provider.Settings
  import dagger.hilt.android.qualifiers.ApplicationContext
  import javax.inject.Inject
  import javax.inject.Singleton

  /**
   * Wraps the two paths Curro uses to ask Android to make it the default
   * launcher (`launcher-app` skill — "Becoming / keeping the default launcher"):
   *
   * 1. [requestRoleIntent] — the canonical Android-10+ path via
   *    [RoleManager.ROLE_HOME]. The returned [Intent] must be launched via
   *    an `ActivityResultLauncher` (callers do NOT call `startActivity` on it;
   *    role-request intents are result-bearing). Returns `null` when the role
   *    is unavailable on this OS (impossible on Curro's `minSdk = 31`, but the
   *    `RoleManager` API exposes the check anyway) OR when Curro already holds
   *    the role (no point re-asking). In both null cases the caller falls back
   *    to [openHomeSettings].
   *
   * 2. [openHomeSettings] — the fallback path used when [requestRoleIntent]
   *    returns `null` (already default OR role unavailable) OR when the user
   *    has chosen "Don't ask again" on the role chooser. Opens the system
   *    Settings → Default apps → Home app surface. Launched via plain
   *    [Context.startActivity] (no result needed — the detector's flow
   *    re-emits on resume).
   */
  @Singleton
  class MakeMeDefaultLauncher @Inject constructor(
      @ApplicationContext private val context: Context,
  ) {
      fun requestRoleIntent(): Intent? {
          val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
          if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
          if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return null
          return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
      }

      fun openHomeSettings(): Intent =
          Intent(Settings.ACTION_HOME_SETTINGS)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  ```

  Notes:
  - `getSystemService(RoleManager::class.java)` can theoretically return
    `null` on stripped-down OS images; the `?: return null` covers it and
    routes to the settings fallback.
  - `FLAG_ACTIVITY_NEW_TASK` on the settings intent is required because
    the caller is `applicationContext` (not the current Activity) —
    omitting it would crash on `startActivity`.

- **`app/src/main/java/com/curro/app/di/LauncherModule.kt`** — a new Hilt
  module bound to `SingletonComponent`. Single binding: the
  `DefaultLauncherDetector` interface → `DefaultLauncherDetectorImpl`.
  `MakeMeDefaultLauncher` is `@Inject`-constructable and needs no
  explicit binding.

  ```kotlin
  package com.curro.app.di

  import com.curro.app.data.launcher.DefaultLauncherDetector
  import com.curro.app.data.launcher.DefaultLauncherDetectorImpl
  import dagger.Binds
  import dagger.Module
  import dagger.hilt.InstallIn
  import dagger.hilt.components.SingletonComponent
  import javax.inject.Singleton

  /**
   * DI bindings for launcher-platform concerns (US-009 / SF-1.1).
   *
   * Single binding: [DefaultLauncherDetector] → [DefaultLauncherDetectorImpl].
   * [com.curro.app.data.launcher.MakeMeDefaultLauncher] is `@Inject`-constructable
   * and needs no binding here. Subsequent launcher SFs (SF-1.4 app-tile launcher,
   * SF-8.x diagnostics) may add bindings to this module.
   */
  @Module
  @InstallIn(SingletonComponent::class)
  abstract class LauncherModule {

      @Binds
      @Singleton
      abstract fun bindDefaultLauncherDetector(
          impl: DefaultLauncherDetectorImpl,
      ): DefaultLauncherDetector
  }
  ```

- **`app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt`**
  — a new file. The launcher placeholder gets its first ViewModel.
  US-007 deliberately shipped `LauncherPlaceholderScreen` without one
  (no state to manage); US-009 introduces the first piece of state
  (`isCurroDefault: Boolean`), so the ViewModel arrives now and grows
  with SF-1.2 → SF-1.6.

  ```kotlin
  package com.curro.app.presentation.launcher

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import com.curro.app.data.launcher.DefaultLauncherDetector
  import dagger.hilt.android.lifecycle.HiltViewModel
  import javax.inject.Inject
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.map
  import kotlinx.coroutines.flow.stateIn

  /**
   * ViewModel for [LauncherPlaceholderScreen] (US-009 / SF-1.1).
   *
   * For now exposes a single field: whether Curro is the resolved default
   * home Activity. The CTA ("Hazme tu pantalla de inicio") on the
   * placeholder is gated on this field. Subsequent Phase-1 SFs grow this
   * ViewModel (SF-1.2 adds the clock tick state, SF-1.4 adds the favourite
   * apps list, SF-1.6 adds the 5-tap-on-clock gesture counter).
   *
   * `SharingStarted.WhileSubscribed(5_000L)` rather than `Eagerly`: the
   * detector's flow registers a lifecycle observer on subscription, so
   * leaking the subscription across Activity destruction is wasteful.
   * `5_000L` is the standard "survive configuration changes" timeout.
   */
  @HiltViewModel
  class LauncherViewModel @Inject constructor(
      detector: DefaultLauncherDetector,
  ) : ViewModel() {

      val uiState: StateFlow<LauncherUiState> =
          detector.flow
              .map { LauncherUiState(isCurroDefault = it) }
              .stateIn(
                  scope = viewModelScope,
                  started = SharingStarted.WhileSubscribed(5_000L),
                  initialValue = LauncherUiState(isCurroDefault = false),
              )
  }

  /**
   * UI state for [LauncherPlaceholderScreen].
   *
   * Phase-0/1 placeholder shape — replaced piecewise as SF-1.2 → SF-1.6
   * add real state (clock, favourites grid, mic state). The default
   * `isCurroDefault = false` is the safe choice: if the detector hasn't
   * answered yet, show the CTA (false positive = harmless one extra tap;
   * false negative = no recovery path if HyperOS forgot the default).
   */
  data class LauncherUiState(
      val isCurroDefault: Boolean,
  )
  ```

- **`app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt`**
  — modified. Signature gains `viewModel` + `onMakeDefault` parameters;
  the `Column` body gains a conditional `BigPrimaryButton` above the
  existing `TextButton`. Crucially, the `Content`-composable split
  (stateless body receiving state + callbacks, called by the
  ViewModel-collecting wrapper) lands now — US-007 deliberately did not
  introduce it because there was no state to flow; US-009 does, mirroring
  the `compose-patterns` skill's stateful/stateless split. The
  previews collapse onto the stateless `Content` composable (no
  fake-ViewModel needed in `@Preview`).

  ```kotlin
  package com.curro.app.presentation.launcher

  import android.content.res.Configuration.UI_MODE_NIGHT_YES
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.padding
  import androidx.compose.material3.MaterialTheme
  import androidx.compose.material3.Surface
  import androidx.compose.material3.Text
  import androidx.compose.material3.TextButton
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.res.stringResource
  import androidx.compose.ui.tooling.preview.Preview
  import androidx.compose.unit.dp
  import androidx.hilt.navigation.compose.hiltViewModel
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import com.curro.app.R
  import com.curro.app.presentation.common.BigPrimaryButton
  import com.curro.app.presentation.theme.CurroSpacing
  import com.curro.app.presentation.theme.CurroTheme

  /**
   * Phase-1 placeholder for the launcher home. US-007 shipped it without a
   * ViewModel; US-009 introduces [LauncherViewModel] (collecting the
   * default-launcher detector) and the
   * "Hazme tu pantalla de inicio" CTA that gates on its state.
   *
   * SF-1.2 → SF-1.5 replace this entire screen piecewise with the real
   * launcher home (clock, mic button, app grid, "Más apps"). The CTA
   * landed here survives into the real launcher home — it is a
   * permanent visible-affordance recovery path for the HyperOS
   * "forgets the default after updates" reality.
   */
  @Composable
  fun LauncherPlaceholderScreen(
      onOpenConfig: () -> Unit,
      onMakeDefault: () -> Unit,
      modifier: Modifier = Modifier,
      viewModel: LauncherViewModel = hiltViewModel(),
  ) {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      LauncherPlaceholderContent(
          uiState = uiState,
          onOpenConfig = onOpenConfig,
          onMakeDefault = onMakeDefault,
          modifier = modifier,
      )
  }

  /**
   * Stateless content composable for [LauncherPlaceholderScreen].
   *
   * Receives [uiState] and emits [onOpenConfig] / [onMakeDefault]. Previews
   * target this directly with hard-coded state. No `Scaffold`,
   * `TopAppBar`, or `statusBarsPadding()` — [com.curro.app.presentation.navigation.CurroNavHost]'s
   * `Scaffold` already pads (No-Double-Padding rule, US-007).
   */
  @Composable
  internal fun LauncherPlaceholderContent(
      uiState: LauncherUiState,
      onOpenConfig: () -> Unit,
      onMakeDefault: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                  text = stringResource(R.string.launcher_placeholder_title),
                  style = MaterialTheme.typography.displayMedium,
                  color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(CurroSpacing.xxl))
              // SF-1.1 CTA — visible only when Curro is NOT the resolved
              // default home. Disappears reactively when the detector's
              // flow re-emits `true` (post-role-grant, on resume).
              if (!uiState.isCurroDefault) {
                  BigPrimaryButton(
                      text = stringResource(R.string.copy_home_make_default),
                      onClick = onMakeDefault,
                      modifier = Modifier.padding(horizontal = CurroSpacing.l),
                  )
                  Spacer(modifier = Modifier.height(CurroSpacing.l))
              }
              // Phase-0 debug affordance — kept until SF-1.6 wires the
              // canonical 5-taps-on-clock gesture. Will disappear with
              // SF-1.1's eventual cleanup pass when the real launcher
              // home is in place.
              TextButton(onClick = onOpenConfig) {
                  Text(
                      text = stringResource(R.string.launcher_placeholder_open_config_debug),
                      style = MaterialTheme.typography.labelLarge,
                  )
              }
          }
      }
  }
  ```

  Notes on the diff vs US-007:
  - `androidx.hilt.navigation.compose.hiltViewModel` and
    `androidx.lifecycle.compose.collectAsStateWithLifecycle` are new
    imports — both already on the catalog (Hilt navigation Compose
    ships with the navigation Compose dependency US-007 wired;
    `collectAsStateWithLifecycle` ships with the lifecycle-Compose
    artefact). If the catalog doesn't pin
    `androidx-hilt-navigation-compose`, the developer adds it under
    the same pattern US-007 used for `androidx-navigation-compose`
    — a single new line in `gradle/libs.versions.toml` plus a
    `dependencies` entry in `app/build.gradle.kts`.
  - The stateless `LauncherPlaceholderContent` is `internal` (same
    module — the test in `app/src/test/` can drive it directly via
    Compose UI Test if the developer chooses that path; the brief
    does not mandate a Compose UI test for this SF — the manual
    on-emulator verification is the canonical acceptance path).
  - The CTA is rendered inside the existing centred `Column`, above
    the debug `TextButton`. Horizontal padding `CurroSpacing.l`
    (24 dp) so the button doesn't touch the screen edges; vertical
    spacer between the CTA and the debug `TextButton`.
  - **No new `.dp` literals** outside `Dimens` / `CurroSpacing`. The
    only sizing decision is the horizontal padding, which uses
    `CurroSpacing.l` — verifiable by grep.

- **`app/src/main/java/com/curro/app/presentation/navigation/CurroNavHost.kt`**
  — modified. The `Launcher` route's `composable { }` block grows two
  new callbacks. The `ActivityResultLauncher` registration lives **here**
  (not in `LauncherPlaceholderScreen`) so the launcher screen stays
  stateless on Android-platform side-effects; `CurroNavHost` already
  owns the `LocalContext` and the platform plumbing. The
  `MakeMeDefaultLauncher` is `@Inject`-constructable, so resolution is via
  a tiny Hilt-injected entry-point helper composable
  (`rememberMakeMeDefaultLauncher`) — see Implementation Notes for the
  exact shape.

  ```kotlin
  // Inside CurroNavHost, in the existing `composable(CurroRoute.Launcher.value) { ... }` block:
  composable(CurroRoute.Launcher.value) {
      val context = LocalContext.current
      val makeMeDefault = rememberMakeMeDefaultLauncher() // Hilt-resolves the @Inject class
      val roleRequestLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult(),
      ) { /* result intentionally ignored — DefaultLauncherDetector's flow re-emits on resume */ }

      LauncherPlaceholderScreen(
          onOpenConfig = { navController.navigate(CurroRoute.ConfigMenu.value) },
          onMakeDefault = {
              val intent = makeMeDefault.requestRoleIntent()
              if (intent != null) {
                  roleRequestLauncher.launch(intent)
              } else {
                  context.startActivity(makeMeDefault.openHomeSettings())
              }
          },
      )
  }
  ```

  The `rememberMakeMeDefaultLauncher()` helper is a tiny composable in
  the same file (or in a new `presentation/navigation/HiltEntryPoints.kt`
  — the developer picks; recommend keeping in `CurroNavHost.kt` for
  Phase-1-scale simplicity):

  ```kotlin
  /**
   * Composition-local-style resolver for [MakeMeDefaultLauncher] via Hilt's
   * entry-point API. Avoids hoisting yet another callback through
   * [CurroNavHost]'s signature for a single call site.
   */
  @Composable
  private fun rememberMakeMeDefaultLauncher(): MakeMeDefaultLauncher {
      val context = LocalContext.current
      return remember(context) {
          dagger.hilt.android.EntryPointAccessors
              .fromApplication(context.applicationContext, LauncherEntryPoint::class.java)
              .makeMeDefaultLauncher()
      }
  }

  @dagger.hilt.EntryPoint
  @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
  internal interface LauncherEntryPoint {
      fun makeMeDefaultLauncher(): MakeMeDefaultLauncher
  }
  ```

  Why the entry-point pattern and not `hiltViewModel()`: the consumer is
  a `composable { }` block, not a screen-level composable that owns a
  ViewModel; injecting through a tiny entry point keeps the
  `CurroNavHost` signature unchanged (no extra ctor parameter to thread
  through `MainActivity.setContent { }`).

- **`app/src/test/java/com/curro/app/data/launcher/DefaultLauncherDetectorImplTest.kt`**
  — a new JVM unit test (Robolectric — same pattern as US-008's tests).
  Robolectric over a thin wrapper interface because the surface area
  here is the `PackageManager.resolveActivity` answer + the
  `ProcessLifecycleOwner` `ON_RESUME` signal — both directly faked by
  Robolectric without a `PackageManagerWrapper` indirection that would
  only exist to make the test possible. The test covers:

  1. Curro is the resolved home → `isDefault() == true`.
  2. Stock launcher is the resolved home → `isDefault() == false`.
  3. `resolveActivity` returns `null` → `isDefault() == false`.
  4. The flow emits the current value on subscription
     (`onStart { emit(...) }`).
  5. The flow re-emits the new value when the underlying resolver
     answer changes between two simulated `ON_RESUME` events.
  6. The flow `distinctUntilChanged()`s — two consecutive
     `ON_RESUME`s with the same answer emit only once.

  Fixture: `Shadows.shadowOf(packageManager).addResolveInfoForIntent(...)`
  with a constructed `ResolveInfo` whose `activityInfo.packageName` is
  toggled between `"com.curro.app"` and `"com.android.launcher3"`.
  Turbine for the flow assertions.

- **`app/src/test/java/com/curro/app/data/launcher/MakeMeDefaultLauncherTest.kt`**
  — a new JVM unit test (Robolectric). Covers:

  1. Role available + not held → `requestRoleIntent()` returns a non-null
     `Intent` whose `action` matches what `RoleManager.createRequestRoleIntent`
     produces (asserted via the `Intent.toUri(...)` round-trip OR via the
     resolved `ComponentName` — pick whichever Robolectric supports
     cleanly; the spec is just "non-null intent that looks like a role
     request").
  2. Role available + held → `requestRoleIntent()` returns `null`.
  3. Role unavailable → `requestRoleIntent()` returns `null`.
  4. `openHomeSettings()` returns a non-null `Intent` with
     `Settings.ACTION_HOME_SETTINGS` and `FLAG_ACTIVITY_NEW_TASK`.

  Fixture: Robolectric's `ShadowRoleManager` (provided by
  `org.robolectric:shadows-framework`). The brief documents that
  approach — if the developer finds Robolectric's `RoleManager` shadow
  unsuitable, fall back to a thin wrapper interface
  (`RoleManagerWrapper`) + a fake; the test coverage stays the same.

- **`app/src/test/java/com/curro/app/presentation/launcher/LauncherViewModelTest.kt`**
  — a new JVM unit test. Uses Turbine + a fake `DefaultLauncherDetector`
  whose `flow` is a `MutableSharedFlow<Boolean>`. Covers:

  1. Initial state: `uiState.value == LauncherUiState(isCurroDefault = false)`
     (the `stateIn` initial value).
  2. After the fake emits `true`: the next `uiState` value is
     `LauncherUiState(isCurroDefault = true)`.
  3. After the fake emits `false` again: back to
     `LauncherUiState(isCurroDefault = false)`.
  4. Coroutines test dispatcher: `runTest { ... }` with
     `StandardTestDispatcher` + `Dispatchers.setMain(...)` for
     `viewModelScope` correctness.

  No Robolectric here — pure JVM, the ViewModel has no Android imports.

### Out of Scope

- **Real launcher home** — clock, mic button, app grid, "Más apps" — those
  are SF-1.2 (clock + date), SF-1.3 (mic button, inert), SF-1.4 (favourite
  apps grid), SF-1.5 ("Más apps" screen). The `LauncherPlaceholderScreen`
  stays a placeholder until SF-1.2 starts replacing it; the SF-1.1 CTA
  added here is the one piece of `LauncherPlaceholderScreen` that
  *survives* into the real launcher home (carried over into
  `LauncherScreen` when SF-1.2 ships it — at which point this brief's
  `LauncherPlaceholderContent` is renamed and grows).
- **5-taps-on-clock gesture** — SF-1.6. The debug "Ajustes (depuración)"
  `TextButton` US-007 shipped stays in place; SF-1.6 swaps it for the
  real gesture at the same time it lands the clock that takes the taps.
- **HyperOS battery-whitelist deep-link** — Phase 8 / SF-8.x diagnostics.
  US-009 does NOT surface "Curro might be killed by HyperOS" anywhere in
  the UI; the foreground service that needs that whitelist
  (`ModelWarmupService`) doesn't exist yet (SF-3.5). The
  visible-affordance recovery path the brief commits to is the
  `DefaultLauncherDetector` re-emitting on resume — that handles the
  "HyperOS forgot the default after an update" case; the
  "HyperOS killed the model warm-up service" case is SF-3.5's problem.
- **"Am I the default launcher?" diagnostic readout** — SF-8.x. The
  detector's `isDefault()` is wired here for the UI gate, but no
  diagnostics screen surfaces it.
- **First-run onboarding flow** — explicitly out of scope per the
  spec's prototype focus. The first launch shows the placeholder with
  the CTA visible; tapping the CTA opens the role chooser; that's the
  whole onboarding for SF-1.1.
- **FSM-reset-on-`onNewIntent`** — Phase 5 (the FSM doesn't exist yet).
  The brief documents that `MainActivity.onNewIntent()` is the right
  hook for SF-5.x to add the FSM-to-`idle` transition; US-009
  deliberately does not override `onNewIntent`.
- **Telemetry event for "user accepted/declined the role"** — out of
  scope. US-008 landed the plumbing but the discipline is "no feature
  emits events yet". If a future SF wants this, a single
  `home_role_grant_result` event with `{granted: bool}` would pass
  `TelemetryGuardrail`; left as a note for the future.
- **Editing `strings.xml` or `brand-design/SKILL.md`** — explicitly
  rejected. `copy_home_make_default` already exists in both, landed by
  US-005. The brief REUSES the canonical ID.

## User Flows

### Flow 1 — First launch on a device where the stock launcher is default

1. User installs Curro from a fresh APK (`adb install app-debug.apk`).
2. User opens Curro via the app-drawer tile (the `LAUNCHER` category
   keeps it surfaceable as a normal app even before it's the home).
3. `MainActivity` starts → `CurroTheme { CurroNavHost() }` →
   `LauncherPlaceholderScreen` collected from
   `LauncherViewModel`.
4. `DefaultLauncherDetector.flow.onStart { emit(isDefault()) }` →
   `isDefault()` resolves the system home → not Curro → emits `false`.
5. `LauncherViewModel.uiState.value = LauncherUiState(isCurroDefault = false)`.
6. The `BigPrimaryButton` rendering "Hazme tu pantalla de inicio" is
   shown above the debug `TextButton`.
7. User taps the `BigPrimaryButton` → `onMakeDefault` fires.
8. `CurroNavHost` calls `makeMeDefault.requestRoleIntent()` → returns a
   non-null `Intent` (role available, not held).
9. `roleRequestLauncher.launch(intent)` → the system role-chooser
   surfaces ("Choose your home app").
10. User selects Curro → system grants the `ROLE_HOME` role → Curro
    process becomes the resumed Activity again.
11. `ProcessLifecycleOwner` fires `ON_RESUME` → the detector's flow
    re-emits → `isDefault()` now returns `true` → the flow emits `true`.
12. `LauncherViewModel.uiState` recomputes → `isCurroDefault = true`.
13. `LauncherPlaceholderContent` recomposes → the
    `BigPrimaryButton` disappears.
14. User presses HOME from any other app (e.g. Settings) →
    `singleTask` + `clearTaskOnLaunch="true"` + the new
    `CATEGORY_HOME` intent-filter means Android resolves HOME to
    Curro's `MainActivity`; it comes to front with a fresh task.

### Flow 2 — `RoleManager` not available / "Don't ask again" / OEM doesn't show chooser

1. Steps 1–7 as Flow 1.
2. `makeMeDefault.requestRoleIntent()` returns `null` (role
   unavailable on this stripped OS image, OR Curro already holds the
   role — though in that case the CTA wouldn't have been visible in
   the first place, since the detector would have emitted `true`).
3. `CurroNavHost` falls back to
   `context.startActivity(makeMeDefault.openHomeSettings())`.
4. The system Settings → Default apps → Home app screen surfaces.
5. User selects Curro from the system list → goes back to Curro.
6. `ON_RESUME` fires → detector re-emits → CTA disappears.

### Flow 3 — HyperOS "forgets the default" after an OS update

1. Curro is the default launcher; the user has used it for weeks.
2. HyperOS pushes an OS update overnight → on reboot, the default home
   is reset to the stock HyperOS launcher.
3. User presses HOME → stock launcher appears → user, confused,
   opens Curro from the app drawer.
4. `MainActivity` starts → `LauncherPlaceholderScreen` renders →
   detector emits `false` (the stock launcher is now the resolved home)
   → the `BigPrimaryButton` "Hazme tu pantalla de inicio" reappears.
5. User taps it → Flow 1 from step 8 onward.

(Once SF-1.2 ships the real launcher home, the CTA appears in the
same screen real-estate that currently holds the placeholder text —
the recovery path is preserved.)

### Flow 4 — Cancel / dismiss the role-chooser

1. Steps 1–9 as Flow 1.
2. User taps "Cancel" or back from the role chooser → the
   `roleRequestLauncher` fires its `onResult` with `RESULT_CANCELED`,
   which we ignore.
3. `ON_RESUME` fires → detector re-emits → still `false` → CTA stays
   visible. The user can try again any time. No error UI, no
   "didn't work" message — just the affordance staying available.

## Function-catalog Impact

**No catalog change.** US-009 wires no FunctionGemma function, no
handler, no `domain/catalog/` entry. The "make me default" action is
a one-time setup affordance the user taps on the launcher home — it
never goes through STT → FunctionGemma → handler.

## FSM States Touched

**None — the FSM doesn't exist yet** (Phase 5 / SF-5.x). The brief
notes for SF-5.x's benefit: `MainActivity.onNewIntent()` is the right
hook for the FSM-to-`idle` transition that the `launcher-app` skill
calls out (rule 3 — "Reset the FSM to `idle` on `onNewIntent`/HOME").
US-009 deliberately does **not** override `onNewIntent` — `singleTask`
+ `clearTaskOnLaunch="true"` + `stateNotNeeded="true"` already deliver
the "HOME returns to a clean launcher" story for Phase 1 (no FSM to
reset), and adding the override now would mean an empty method that
SF-5.x would then have to fill — that's premature scaffolding.

When SF-5.x lands, the developer overrides `onNewIntent` in
`MainActivity` and routes the call through the
`AssistantCoordinator`'s "reset to idle" entry point. The detector's
flow re-emits on `ON_RESUME` regardless of whether `onNewIntent` is
overridden — the two mechanisms are independent.

## Android System Integrations & Permissions

Integrations:

- **`PackageManager.resolveActivity`** — read-only, no permission
  required. Used in `DefaultLauncherDetectorImpl.isDefault()` to find
  the system's currently-resolved home Activity. The `MATCH_DEFAULT_ONLY`
  flag makes the intent explicit (without it, the resolver may return
  the resolver-chooser pseudo-activity, which we'd misread as
  "not Curro" anyway, but the flag is the clean way).
- **`RoleManager.ROLE_HOME`** — Android-10+. Consent-based; no
  manifest `<uses-permission>` needed. The system role-request dialog
  is the user's grant. Used via
  `roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)` →
  `ActivityResultLauncher.launch(intent)`. On Curro's `minSdk = 31`
  the role is universally available; the impl still guards on
  `isRoleAvailable` for the defensive path.
- **`Settings.ACTION_HOME_SETTINGS`** — public Intent action,
  permission-less. The fallback path when `requestRoleIntent()`
  returns `null` (OEM doesn't surface the chooser, "Don't ask again",
  or role unavailable).
- **`ProcessLifecycleOwner`** — Jetpack lifecycle artefact. Used to
  observe app-wide `ON_RESUME` so the detector's flow re-emits when
  the user returns from the chooser or from Settings. Already on the
  catalog (lifecycle-process is part of the lifecycle BoM US-001
  reserved). If not pinned, add as a single new catalog entry.

Permissions table:

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| (none) | The launcher itself is permission-less (spec §10, §11). `RoleManager` is consent-based, not permission-gated; `PackageManager.resolveActivity` and `Settings.ACTION_HOME_SETTINGS` need none. The user's "grant" is choosing Curro in the system role-chooser. | The role-request dialog appears when the user taps the CTA — *not* on first launch, *not* on install. | If the user cancels the chooser, the CTA stays visible; the user can try again any time. No `Curro→user` error message — the affordance just stays available. |

**No new permission** is added to `app/src/main/AndroidManifest.xml`.

## On-device-model Impact

**No model impact.** No FunctionGemma prompt change, no Gemma 3n
load, no inference, no model file touched. The detector's
`PackageManager.resolveActivity` call is < 1 ms; the role-request
intent is built synchronously; the settings intent is a constant.

## Android Specification

### Screens and Composables

- **`presentation/launcher/LauncherPlaceholderScreen.kt`** (modified) —
  the `Screen` composable now collects `LauncherViewModel`'s
  `uiState` via `collectAsStateWithLifecycle()` and delegates to a
  new internal stateless `LauncherPlaceholderContent` composable that
  receives the `uiState` and emits `onOpenConfig` + `onMakeDefault`
  callbacks. The previews target the stateless `Content`. No
  `Scaffold` / `TopAppBar` / `statusBarsPadding()` — preserved from
  US-007.

- **`presentation/launcher/LauncherViewModel.kt`** (new) — first
  ViewModel on the launcher route. `@HiltViewModel`, injects
  `DefaultLauncherDetector`, exposes `StateFlow<LauncherUiState>`.

- **`presentation/navigation/CurroNavHost.kt`** (modified) — the
  `composable(CurroRoute.Launcher.value) { }` block grows the
  `ActivityResultLauncher<Intent>` registration and the
  `onMakeDefault` callback wiring; the `LauncherPlaceholderScreen`
  call site gains `onMakeDefault = ...`. The `ConfigMenu` route is
  unchanged. The single `Scaffold` around the `NavHost` is
  unchanged.

### ViewModels and State Management

```kotlin
data class LauncherUiState(val isCurroDefault: Boolean)

@HiltViewModel
class LauncherViewModel @Inject constructor(
    detector: DefaultLauncherDetector,
) : ViewModel() {
    val uiState: StateFlow<LauncherUiState>
}
```

No `Event` sealed interface yet — SF-1.6 introduces the first event
(`ClockTapped` for the 5-tap gesture). For now the screen-level
callbacks (`onOpenConfig`, `onMakeDefault`) are direct
`() -> Unit` parameters, which is the right shape for "one-off
effects fired by `CurroNavHost`'s platform glue", not "ViewModel
intent-handling". This matches `compose-patterns`' guidance for
"navigation/platform-effect callbacks vs ViewModel events".

### Navigation Routes

**No new routes.** The `CurroRoute` enum still has exactly two
entries (`Launcher`, `ConfigMenu`). The role-chooser is an external
Activity launched via `ActivityResultLauncher` — not a Compose
destination.

### Hilt Modules

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherModule {
    @Binds @Singleton
    abstract fun bindDefaultLauncherDetector(
        impl: DefaultLauncherDetectorImpl,
    ): DefaultLauncherDetector
}
```

Plus the `LauncherEntryPoint` inside `CurroNavHost.kt` for the
`MakeMeDefaultLauncher` resolution (entry-point pattern — no
separate module needed because `MakeMeDefaultLauncher` is
`@Inject`-constructable).

### Composables by Feature (checklist)

- [x] Main screen composable: `LauncherPlaceholderScreen` (collects ViewModel)
- [x] Stateless `Content` composable: `LauncherPlaceholderContent`
- [x] CTA built from `BigPrimaryButton` (US-006's shared big component)
- [ ] No new picker / confirmation composable
- [ ] No new loading / empty / error composable (the `false` initial
      state is "show the CTA", which is the right safe default)
- [x] `@Preview`s: light, dark, `fontScale = 1.5f`, `fontScale = 2.0f`
      — four canonical variants on a 412 dp wide frame, mirroring the
      US-007 / US-006 / US-004 pattern. Each preview renders the
      stateless `LauncherPlaceholderContent` directly with two
      hard-coded `LauncherUiState` variants (`isCurroDefault = false`
      → CTA visible; `isCurroDefault = true` → CTA hidden) so the
      reviewer can see both states without running the app. Total
      previews: 8 (4 variants × 2 states).

### Material Design Components

- `BigPrimaryButton` (US-006) — the SF-1.1 CTA.
- `TextButton` (Material 3) — the existing US-007 debug affordance,
  unchanged.
- `Text` (Material 3) — the existing US-007 title, unchanged.

## Acceptance Criteria

(Mirrors the PRD acceptance list — repeated here in brief form for the
developer's tick-as-you-go pass.)

- [ ] `app/src/main/AndroidManifest.xml`'s `MainActivity` intent-filter
      has all four categories (`MAIN`, `HOME`, `DEFAULT`, `LAUNCHER`).
      Verifiable: `grep -E 'category\.(HOME|DEFAULT|LAUNCHER)' app/src/main/AndroidManifest.xml | wc -l` → `3`.
- [ ] All US-007 `<activity>` attributes are byte-identical
      (`singleTask`, `clearTaskOnLaunch="true"`, `stateNotNeeded="true"`,
      portrait, `windowSoftInputMode="adjustResize"`, `exported="true"`).
- [ ] `data/launcher/DefaultLauncherDetector.kt` (interface) +
      `DefaultLauncherDetectorImpl.kt` (impl) exist; `isDefault()`
      resolves via `PackageManager.resolveActivity` with
      `MATCH_DEFAULT_ONLY` and compares against `BuildConfig.APPLICATION_ID`
      (never the literal `"com.curro.app"`); `flow` re-emits on
      `ProcessLifecycleOwner` `ON_RESUME` with
      `distinctUntilChanged()`.
- [ ] `data/launcher/MakeMeDefaultLauncher.kt` exists; `requestRoleIntent()`
      returns the role-request intent when available + not held,
      `null` otherwise; `openHomeSettings()` returns an intent with
      `Settings.ACTION_HOME_SETTINGS` + `FLAG_ACTIVITY_NEW_TASK`.
- [ ] `di/LauncherModule.kt` exists with the single
      `@Binds @Singleton` for the detector.
- [ ] `presentation/launcher/LauncherViewModel.kt` exists,
      `@HiltViewModel`, exposes `StateFlow<LauncherUiState>` via
      `detector.flow.map { … }.stateIn(viewModelScope, WhileSubscribed(5_000), …)`.
- [ ] `LauncherPlaceholderScreen.kt` is updated: signature gains
      `onMakeDefault: () -> Unit` and
      `viewModel: LauncherViewModel = hiltViewModel()`; an internal
      stateless `LauncherPlaceholderContent` composable receives
      `LauncherUiState` + the two callbacks; the
      `BigPrimaryButton` rendering
      `stringResource(R.string.copy_home_make_default)` appears
      **only** when `!uiState.isCurroDefault`; it is positioned
      **above** the existing "Ajustes (depuración)" `TextButton`
      with `CurroSpacing.l` vertical separation.
- [ ] `CurroNavHost.kt` wires the
      `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())`
      registration in the `Launcher` `composable { }` block; the
      `onMakeDefault` callback fires
      `requestRoleIntent()` first, falls back to
      `context.startActivity(openHomeSettings())` on null.
- [ ] **No new string resource** is added; the brief reuses
      `R.string.copy_home_make_default` (US-005 / line 148 of
      `strings.xml`). `git diff app/src/main/res/values/strings.xml`
      returns empty.
- [ ] **No edit to `brand-design/SKILL.md`** — the COPY table row
      for `copy_home_make_default` already exists (line 595).
      `git diff .claude/skills/brand-design/SKILL.md` returns empty.
- [ ] **No new permission** in `AndroidManifest.xml` —
      `grep '<uses-permission' app/src/main/AndroidManifest.xml`
      output is unchanged from US-008 (the inline comment-block
      bullet for `CATEGORY_HOME → SF-1.1` is updated to reflect
      "landed", but that's a comment, not a permission).
- [ ] Manual verification on the Pixel_10_Pro emulator (or Redmi 15
      hardware once available):
      - Fresh install → CTA visible → tap → role chooser surfaces.
      - Accept Curro → chooser dismisses → CTA disappears.
      - `adb shell input keyevent KEYCODE_HOME` from Settings →
        Curro's launcher route is foregrounded (not stock).
      - Manually set stock launcher back via
        `adb shell cmd package set-home-activity` → return to Curro
        via app-drawer or `adb shell am start` → CTA reappears
        within one frame of `ON_RESUME`.
- [ ] `app/src/test/java/com/curro/app/data/launcher/DefaultLauncherDetectorImplTest.kt`
      (Robolectric) passes — six scenarios (Curro-is-default,
      stock-is-default, no-home-resolved, initial emission,
      re-emission on resume after change,
      `distinctUntilChanged()` dedupe).
- [ ] `app/src/test/java/com/curro/app/data/launcher/MakeMeDefaultLauncherTest.kt`
      (Robolectric) passes — four scenarios (available + not held,
      available + held, unavailable, settings-fallback intent shape).
- [ ] `app/src/test/java/com/curro/app/presentation/launcher/LauncherViewModelTest.kt`
      (pure JVM + Turbine + `runTest`) passes — three scenarios
      (initial state, true emission, false emission).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest`
      are all green on the post-US-009 commit; no new detekt
      deprecation warnings; the `MagicNumber` exclude on
      `**/presentation/theme/**` is unchanged.
- [ ] Every `@Composable` shipped or modified in this SF has a
      `@Preview` set including light, dark, `fontScale = 1.5f`, and
      `fontScale = 2.0f` variants on a 412 dp wide frame. The
      `LauncherPlaceholderContent` previews cover both
      `isCurroDefault` states (CTA visible + CTA hidden) — 8 previews
      total.
- [ ] No FSM-reset-on-`onNewIntent`, no telemetry event, no real
      launcher home, no diagnostic readout — the explicit
      non-deliveries in §Out of Scope are honoured.

## Design Notes

- The `BigPrimaryButton` is the right brick: SF-1.1 is the **most
  important action a new user takes** in their entire relationship
  with Curro (it's the act of accepting Curro as their phone's
  launcher). The senior-first contract from US-004 (≥ 96 dp tap
  target, large text, high contrast) is non-negotiable for this CTA.
  Audio feedback is NOT required for this one-time setup affordance —
  the audio-always-with-the-screen rule applies to the assistant
  loop, not to the "set me up" moments. But the haptic feedback
  `BigPrimaryButton` already fires on tap (US-006 A6) is the right
  tactile signal.
- The CTA sits inside the existing centred `Column` of the
  placeholder. SF-1.2 will replace the placeholder with the real
  launcher home (clock + mic + grid + "Más apps") — the CTA gets
  re-placed there, and the recovery path is preserved.
- Spanish copy: `R.string.copy_home_make_default` =
  "Hazme tu pantalla de inicio". Curro's voice — direct, second-person
  informal, action-oriented. NOT "Configurar como pantalla de inicio"
  (servile), NOT "¿Quieres que sea tu pantalla principal?" (asks
  permission — Curro doesn't ask for permission, he proposes work).
  Locked by US-005; do not modify.
- No new colour, no new shape, no new typography role. The
  `BigPrimaryButton` carries
  `MaterialTheme.colorScheme.primary` (terracota) +
  `MaterialTheme.colorScheme.onPrimary` (cream) automatically
  (US-005); contrast on either light or dark is the
  brand-locked ≥ 7:1.

## Senior-UX & Copy

**No new Spanish copy.** The single user-facing string is the
already-canonical `copy_home_make_default` =
"Hazme tu pantalla de inicio" (US-005 / `strings.xml` L148 /
`brand-design` L595). No content description string is added — the
`BigPrimaryButton` consumes the text label directly as its accessible
label (US-006's shared component handles this).

The senior-first contract is preserved:
- Tap target ≥ 96 dp (via `BigPrimaryButton`'s built-in
  `Modifier.heightIn(min = Dimens.MinTapTarget)`).
- Text at the senior-first scale (the `BigPrimaryButton` renders its
  label at `titleLarge` or above per US-005 — ≥ 22 sp).
- High contrast (terracota-on-cream, ≥ 7:1).
- Audio NOT required for this one-time setup affordance.
- Layout predictable: the CTA appears in the same screen real-estate
  every time it's needed (above the debug `TextButton`), inside the
  centred `Column`.

## Performance Considerations

- `DefaultLauncherDetectorImpl.isDefault()` runs on the main thread
  (called from `Lifecycle` observer callbacks and from `onStart`).
  `PackageManager.resolveActivity` is a single Binder hop to the
  system package manager — measured in microseconds in normal
  conditions, well below the 16 ms frame budget. No `Dispatchers.IO`
  hop needed.
- `ProcessLifecycleOwner` lifecycle observation is process-wide and
  cheap. The `callbackFlow` adds a single lifecycle observer; the
  `awaitClose` removes it on cancellation. The
  `SharingStarted.WhileSubscribed(5_000L)` on `LauncherViewModel`
  means the observer is only active while a UI subscriber is alive
  (plus a 5 s grace for configuration changes) — no leak across
  Activity destruction.
- `distinctUntilChanged()` dedupes the boolean before
  `LauncherViewModel`'s `stateIn` sees it; `stateIn`'s downstream
  `StateFlow` only emits on actual state change → no recomposition
  storms.
- `BuildConfig.APPLICATION_ID` is a compile-time constant; the
  package-name comparison is `==` on `String`, no allocation.
- The role-request intent is built synchronously on the main thread
  (a constant-time `RoleManager` call); the settings intent is a
  literal `Intent(...)` constructor. Neither blocks for any
  measurable time.

## Testing Requirements

(Aligned with `testing-patterns` — the on-device-LLM / STT / TTS
fakes are not needed here; this SF touches none of those.)

- [x] **`DefaultLauncherDetectorImplTest`** (Robolectric, JVM):
      six scenarios — Curro-is-default / stock-is-default /
      no-home-resolved / initial emission / re-emission on resume
      after underlying change / `distinctUntilChanged` dedupe.
      Uses `Shadows.shadowOf(packageManager).addResolveInfoForIntent`
      for the resolver fixture; Turbine for the flow assertions;
      `Lifecycle.Event.ON_RESUME` simulated via
      `ProcessLifecycleOwner.get().lifecycle` event handlers
      directly OR via the `androidx.lifecycle.testing` helpers
      (developer picks).

- [x] **`MakeMeDefaultLauncherTest`** (Robolectric, JVM): four
      scenarios — role-available-not-held / role-held /
      role-unavailable / settings-fallback-intent-shape. Uses
      Robolectric's `ShadowRoleManager` (`shadows-framework`); if
      the shadow's `isRoleAvailable` / `isRoleHeld` /
      `createRequestRoleIntent` surface is incomplete on the
      Robolectric version pinned in the catalog, fall back to a
      thin `RoleManagerWrapper` interface + a fake (no
      Robolectric needed). The brief notes this as the
      developer-pick fallback.

- [x] **`LauncherViewModelTest`** (pure JVM + `runTest` + Turbine
      + `Dispatchers.setMain(...)`): three scenarios — initial
      state / detector emits `true` / detector emits `false`.
      Fake `DefaultLauncherDetector` whose `flow` is a
      `MutableSharedFlow<Boolean>`; `isDefault()` returns the
      flow's last-emitted value (or `false` if none).

- [ ] **No FSM tests** — no FSM yet.
- [ ] **No LLM / STT / TTS fakes** — none used.
- [ ] **No `WhatsAppNotificationParser` fixture** — N/A.
- [ ] **No in-memory Room test** — N/A.
- [ ] **No `ConfidencePolicy` test** — N/A.
- [ ] **No Compose UI test on the screen** — the four `@Preview`
      variants × two states give visual coverage; the stateless
      `LauncherPlaceholderContent`'s behaviour is implicit in the
      `LauncherViewModelTest` (the screen renders what the state
      says). A formal Compose UI test on
      `LauncherPlaceholderContent` is **optional**; the developer
      may add one if they're already in a UI-test mood, but the
      brief does not require it.
- [x] **Dark-mode verification** via the dark `@Preview` variants
      (both `isCurroDefault` states).
- [ ] **On the real Redmi 15**: the manual flow described in
      §Acceptance Criteria — fresh install, role chooser, HOME
      from another app, simulated HyperOS-forgets-default. The
      Pixel_10_Pro emulator is sufficient for SF-1.1's
      acceptance bar (the `RoleManager.ROLE_HOME` API is identical
      on stock Android 15 and HyperOS 2/3); real-Redmi-15
      verification can wait until SF-1.3 (mic button) — that's
      the first SF where the Redmi 15-specific story
      (HyperOS-killing-foreground-services, audio routing)
      matters.

## Implementation Notes

The PM owns Metadata, Summary, Scope, User Flows,
Function-catalog Impact, Senior-UX & Copy, Acceptance Criteria,
and Design Notes; the four open architecture questions that would
normally live here are pinned inline (no Open Questions section
per the user's "no architect on this SF" directive). The shape
decisions:

1. **Detector package: `data/launcher/`** (new sub-package), NOT
   `data/apps/`. Rationale: `data/apps/` is for enumerating
   installed apps (SF-1.4's `InstalledAppsProvider`); the detector
   here is about Curro's *own identity* as the launcher, which is
   a different concern.

2. **Detector interface in `data/`, NOT `domain/repository/`**.
   Rationale: it's a launcher-platform concern, not a
   domain-level abstraction; no use case will consume it from the
   domain layer in Phase 1. If Phase 5+ ever needs it from a use
   case, promote then. Trade-off: the launcher ViewModel
   directly imports a `data/` interface, which slightly bends the
   Clean Architecture purity — but the rule "platform-only
   concerns can live in `data/` without domain ceremony" is the
   right call at this scale.

3. **Detector flow uses `ProcessLifecycleOwner`**, NOT the
   launcher Activity's lifecycle. Rationale: the user *leaves*
   Curro to grant the role (the chooser is its own Activity),
   then comes back — `ProcessLifecycleOwner`'s `ON_RESUME` is
   the canonical "user is interacting again" signal that
   survives the chooser overlay correctly. The launcher
   Activity's `ON_RESUME` also fires on return; either would
   work, but the process-wide one is robust to "what if the
   chooser comes back to a different Activity for some weird
   OEM reason".

4. **`MakeMeDefaultLauncher.requestRoleIntent()` returns `null`
   on "already held"**, NOT the role-request intent. Rationale:
   if Curro already holds the role, the user shouldn't be
   prompted (the system would surface a redundant chooser).
   Returning `null` and routing to `openHomeSettings()` is the
   right behaviour — but the CTA wouldn't be visible in the
   first place (because the detector would have emitted `true`),
   so this branch is effectively unreachable in normal use; it's
   defensive for the race window between flow emission and tap.

5. **`ActivityResultLauncher` registration in `CurroNavHost`**,
   NOT in `LauncherPlaceholderScreen`. Rationale: the launcher
   screen stays platform-side-effect-free (it just emits
   `onMakeDefault`); the platform glue
   (`rememberLauncherForActivityResult` + the
   `MakeMeDefaultLauncher` resolution) belongs at the
   `NavHost`-route level. SF-1.2 → SF-1.6 will keep growing the
   launcher screen; keeping side-effects out of it pays.

6. **`MakeMeDefaultLauncher` resolved via
   `EntryPointAccessors`** in the `Launcher` `composable { }`,
   NOT hoisted as a constructor parameter through `CurroNavHost`.
   Rationale: avoids extending `CurroNavHost`'s signature for a
   single call site; the entry-point pattern is idiomatic Hilt
   for "get this `@Inject`-constructable thing from a non-DI
   surface".

7. **No override of `MainActivity.onNewIntent`**. Rationale:
   `singleTask` + `clearTaskOnLaunch="true"` +
   `stateNotNeeded="true"` (US-007) deliver the
   "HOME returns to a clean launcher" story for Phase 1. The FSM
   doesn't exist yet (Phase 5); adding an empty override now
   would be premature. The `launcher-app` skill rule 3 ("Reset
   the FSM to `idle` on `onNewIntent`/HOME") is documented as
   SF-5.x's hook.

8. **Robolectric over a `PackageManagerWrapper`** for the
   detector test, EXCEPT for the `RoleManager` test where the
   developer may fall back to a `RoleManagerWrapper` interface
   if Robolectric's `ShadowRoleManager` is incomplete on the
   pinned version. The acceptance criteria specify the
   test scenarios; the implementation approach is the
   developer's pick.

9. **`androidx-hilt-navigation-compose` catalog entry** — add
   if not already pinned. US-007 may have left this implicit;
   the developer adds a single new line to
   `gradle/libs.versions.toml` and a `dependencies` entry in
   `app/build.gradle.kts` following US-007's
   `androidx-navigation-compose` pattern. No version
   negotiation needed — pin to the current stable
   compatible with the Hilt version US-002 wired.

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-14 | PM (`android-product-analyst`) | Initial brief — SF-1.1 / US-009. |
