---
name: launcher-app
description: Building Curro as an Android home-screen launcher — the CATEGORY_HOME manifest setup, becoming/keeping the default launcher (RoleManager), HOME-button and lifecycle behaviour, overlays over other apps (SYSTEM_ALERT_WINDOW), foreground-service patterns, and the Xiaomi HyperOS battery-whitelist gotcha.
triggers:
  - launcher
  - CATEGORY_HOME
  - home app
  - default launcher
  - RoleManager
  - HOME button
  - overlay
  - SYSTEM_ALERT_WINDOW
  - foreground service
  - HyperOS
  - Xiaomi
  - battery whitelist
  - MIUI
  - recents
---

# Launcher App (Curro is the home screen)

Curro replaces the phone's launcher. That changes the Activity model, the lifecycle,
and the OS-relationship in ways a normal app doesn't have. Source:
`docs/curro-spec-v1.0.md` §11, §14, and the HyperOS risk in §14.

## Manifest — declaring the launcher

`MainActivity` carries the HOME intent filter (in addition to LAUNCHER, so it also
shows in the app drawer / Settings):

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"          <!-- one task; HOME re-enters this instance -->
    android:stateNotNeeded="true"
    android:clearTaskOnLaunch="true"          <!-- pressing HOME from elsewhere returns to a fresh launcher -->
    android:windowSoftInputMode="adjustResize"
    android:screenOrientation="portrait">     <!-- this user uses one phone, upright (see adaptive-layout stub) -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

- `MainActivity` is the launcher; `enableEdgeToEdge()` + `setContent { CurroTheme { CurroNavHost(...) } }`. The launcher home (clock + mic button + app grid) and the config menu are the only nav routes; the assistant's listening/processing/confirming UI are state-driven overlays (see `voice-interaction`).
- The launcher itself needs **no permission** — Android offers the user to make Curro the default home app once the filter is present.

## Becoming / keeping the default launcher

```kotlin
// Offer to set Curro as the home app (Android 10+)
val roleManager = getSystemService(RoleManager::class.java)
if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), RC_HOME)
}
// Fallback / re-offer: Settings → Apps → Default apps → Home app
startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
```

- For the **prototype on the physical device**, also fine to set it once via adb:
  `adb shell cmd package set-home-activity com.curro.app/.MainActivity`.
- If Curro is *not* currently the default launcher, the home screen should still
  surface a clear, big, one-tap "Hazme tu pantalla de inicio" prompt — don't assume
  it's set.
- HyperOS sometimes "forgets" the default launcher after updates; the diagnostics
  section of the config menu should show "soy el launcher por defecto: sí/no".

## HOME button & lifecycle

- With `singleTask`, pressing HOME from another app brings `MainActivity` back to the
  front (a new `onNewIntent` / `onResume`), not a new instance. On `onNewIntent`,
  reset the assistant FSM to `idle` (the user came home — start clean).
- The launcher is essentially always-resident; treat `onResume` as "refresh the
  clock, the unread-message badge, the favourite-apps grid". Don't hold heavy state
  across process death you can't rebuild — but the model warm-up service (below) is
  what keeps the heavy thing alive.
- Don't fight the recents/overview screen; Curro doesn't need to be excluded from it.

## Opening other apps

The `open_app` handler resolves a colloquial name → an installed component and fires
a launch intent — see `platform-integrations`. The launcher's static app-grid tiles
do the same with known package names. `QUERY_ALL_PACKAGES` is needed to enumerate
apps by name (declare it; spec §10).

## Overlays over other apps (eval — `SYSTEM_ALERT_WINDOW`)

The spec marks `SYSTEM_ALERT_WINDOW` as *to evaluate*: it would let Curro show
assistant feedback (the listening/processing overlay) *on top of whatever app the
user is in*, not just inside the launcher. If you implement it:
- Use a `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`), requested via
  `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` — never silently.
- It's invasive; gate it behind a config toggle, default off. Without it, the
  assistant feedback only shows when Curro (the launcher) is on screen — acceptable
  for the prototype.

## Foreground services (keeping the model warm)

`ModelWarmupService` is a foreground service that keeps FunctionGemma resident (see
`on-device-llm`). It needs `POST_NOTIFICATIONS` (the ongoing notification is
required scaffolding — low importance, no sound). Start it from `CurroApp.onCreate()`
or when the launcher first becomes visible; `START_STICKY`.

## ⚠️ HyperOS / MIUI battery restrictions (known risk — spec §14)

Xiaomi's HyperOS/MIUI aggressively kills background services and apps that aren't
"protected". Without action, `ModelWarmupService` gets killed and FunctionGemma goes
cold. Mitigations (do all that apply):
- **Battery whitelist**: Settings → Battery → App battery saver → Curro → "No
  restrictions". Document this in the config-menu diagnostics with a deep link
  (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for Curro), and tell Fran to do it
  on setup.
- **Autostart**: HyperOS has a separate "Autostart" toggle (Security app → Autostart
  → Curro = on). No standard API — surface it in onboarding docs.
- **Detect-and-recover**: when the model engine is unexpectedly not ready
  (`isReady() == false`), reload it; degrade with a one-off "Dame un segundo" rather
  than failing.
- **`requestIgnoreBatteryOptimizations()`** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
  helps on stock Android; on HyperOS it's necessary-but-not-sufficient — still need
  the per-app whitelist + autostart.

## Diagnostics (config menu)

The config menu's "Versión y diagnóstico" section (spec §9) should show: am I the
default launcher? is the warm-up service alive? last inference latency? which
permissions are granted? — so Fran can debug on a home visit in seconds.

## Rules

1. **`MainActivity` is the launcher** — `singleTask`, `CATEGORY_HOME` + `DEFAULT` + `LAUNCHER`, portrait.
2. **Never assume Curro is the default launcher** — show a big one-tap prompt when it isn't; check it in diagnostics.
3. **Reset the FSM to `idle` on `onNewIntent`/HOME** — the user came home, start clean.
4. **Keep the model warm via a foreground service** (`POST_NOTIFICATIONS`, low-importance notification, `START_STICKY`) — and assume HyperOS will kill it: detect, reload, degrade.
5. **`SYSTEM_ALERT_WINDOW` is opt-in, off by default** — request it explicitly; without it, assistant UI only shows inside the launcher.
6. **Document the HyperOS setup steps** (battery whitelist + autostart) — they're required for the prototype to work reliably; surface them in diagnostics.
