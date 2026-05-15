# US-023 — SF-3.5 · `ModelWarmupService` — foreground service keeping FunctionGemma warm

> **Spec trace:** spec §4.3 (FunctionGemma "se mantiene caliente en memoria
> mediante un foreground service"), §10 (permissions), §14 ("Riesgos
> identificados" — HyperOS killing services), `launcher-app` skill HyperOS
> section, `on-device-llm` skill "Warm-keeping FunctionGemma".
> **Master-plan:** SF-3.5
> **Phase:** 3 — FunctionGemma decision layer
> **Depends on:** US-020 (`FunctionCallEngine` to inject), US-008 (`CurroApp`
> bootstrap already exists with `TelemetryInitializer` — same pattern for the
> service start).
> **Size:** M

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `ModelWarmupService` — foreground service keeping FunctionGemma warm |
| **US ID** | US-023 |
| **Phase** | 3 |
| **Status** | In Progress |
| **Created** | 2026-05-15 |
| **Modified** | 2026-05-15 |
| **PM Owner** | android-product-analyst |
| **Architect** | ondevice-ai-engineer |

---

## 1. Summary

A `@AndroidEntryPoint Service` that starts on `CurroApp.onCreate()`, posts a
low-importance ongoing notification (the required scaffolding for a foreground
service on Android 8+), and calls `FunctionCallEngine.warmUp()` so
FunctionGemma is in memory before the user's first mic press. `START_STICKY`
so the system re-launches the service after a kill; the engine's
**check-on-call** strategy (already wired in US-020's `decide()`) provides
the second line of defence when HyperOS gets aggressive about background
processes.

Why this matters for *this* user: he expects the first press to be as fast as
every later press. Cold-starting a 288 MB model on a Snapdragon 6s Gen 3
takes a measurable beat — without this service, his first interaction every
morning would feel sluggish in a way no later interaction does. The
notification is a side effect Android demands; he never has to interact with
it.

---

## 2. Scope

**In scope:**

- `service/ModelWarmupService.kt` — `@AndroidEntryPoint` `Service` subclass.
- Manifest additions: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC`; `<service>` declaration with
  `foregroundServiceType="dataSync"`.
- A new notification channel `"curro_warmup"` (`IMPORTANCE_MIN`, no sound,
  no vibration), created in `CurroApp.onCreate()` (or a dedicated
  `NotificationChannelInitializer`).
- A new monochrome vector drawable `ic_curro_notification.xml` in
  `res/drawable/` — small mic outline.
- A new COPY entry `copy_warmup_ongoing` = "Curro está listo".
- Service start wired in `CurroApp.onCreate()` via
  `ContextCompat.startForegroundService(...)`.
- Spec §10 permissions table updated to include the three permissions.
- `docs/MODELS.md` (added in US-019) extended with the HyperOS
  battery-whitelist setup steps.
- Detect-and-recover **check-on-call** strategy: already in US-020's
  `FunctionGemmaEngine.decide()`; verified here as the chosen approach.

**Out of scope:**

- A `POST_NOTIFICATIONS` runtime permission prompt — Android 13+ silently
  allows the service to run without a visible notification icon when the
  permission is denied; this SF accepts that fall-back. A later UX SF may
  add a prompt if Fran wants the icon visible.
- A periodic-ping detect strategy — explicitly rejected; the
  check-on-call strategy in US-020 is sufficient and cheaper.
- A Battery-Optimisations-exemption deep link — documented in `docs/MODELS.md`
  as a manual device-side setup step; no programmatic request in Phase 3.
- A "Curro está caliente / Curro se está cargando" diagnostic in the config
  menu — Phase 8.
- Loading Gemma 3n — Phase 9.
- The smoke loop / launcher integration — US-024.

---

## 3. User Flows

The user **sees** nothing from this SF except possibly a small icon in the
notification shade. Developer-facing flows:

### Flow 1 — App start, weights present

1. Android boots → user unlocks → Android resolves the default launcher → if
   Curro is the default, `CurroApp.onCreate()` runs.
2. `CurroApp.onCreate()` initialises the notification channel (idempotent
   create), then calls `ContextCompat.startForegroundService(this,
   Intent(this, ModelWarmupService::class.java))`.
3. The service's `onStartCommand` runs: posts the ongoing notification ("Curro
   está listo"), launches a background coroutine that calls `engine.warmUp()`.
4. `engine.warmUp()` loads the model; `Log.i("Curro/Llm", "warm-up took
   <ms>ms")` appears in logcat.
5. The user later presses the mic → US-024's smoke loop runs → first `decide`
   is already warm → under 500 ms.

### Flow 2 — App start, weights absent (CI / first install)

1. As above, but `engine.warmUp()` finds `ModelFiles.isFunctionGemmaAvailable()
   == false`, logs once, leaves `llm = null`.
2. The notification is still posted ("Curro está listo" — the title is the
   user-facing claim; even on a cold model the launcher works for the
   non-voice tiles).
3. The user later presses the mic → `engine.decide()` returns
   `CurroError.ModelCold` → US-024's smoke loop speaks `copy_models_not_ready`.

### Flow 3 — HyperOS kills the service

1. Service is running, model is warm.
2. User backgrounds the app (screen-off, or other-app foregrounded).
3. HyperOS, in its zeal, kills `ModelWarmupService` and the `LlmInference`
   instance with it.
4. (Background) Android tries to restart the service due to `START_STICKY`.
   Sometimes it succeeds; sometimes HyperOS prevents it.
5. User wakes the phone, presses the mic.
6. `engine.isReady()` returns `false`. `decide()` returns
   `CurroError.ModelCold` AND kicks `warmUp()` asynchronously.
7. US-024's smoke loop speaks `copy_models_not_ready`.
8. By the time the user presses the mic again, warm-up has likely completed.

### Flow 4 — `POST_NOTIFICATIONS` denied on Android 13+

1. Service starts; `startForeground(NOTIF_ID, notification)` is called.
2. Without the permission, Android 13+ silently suppresses the notification
   icon but allows the service to run.
3. The model warms normally; the user has no UX impact other than a missing
   icon in the notification shade.

---

## 4. Function-catalog Impact

**No catalog change.**

---

## 5. FSM States Touched

**None.** The service is process-level infrastructure. The `CurroError.ModelCold`
emitted by `decide()` is handled by US-024's smoke loop, which lives in the
provisional `Processing` micro-state of `ListeningState`.

---

## 6. Android System Integrations & Permissions

### 6.1 Manifest permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `POST_NOTIFICATIONS` | Android 13+ requires this to show the foreground service notification icon | This SF does **not** request it at runtime; if not granted, the service runs without the icon | Service still runs; notification icon hidden — model warms normally |
| `FOREGROUND_SERVICE` | Required to host any FGS on Android 9+ | At install time (normal permission) | Service cannot run (manifest-required) |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ requires a typed FGS permission; `dataSync` is the closest match for model keep-alive | At install time | Service cannot run on Android 14+ |

Spec §10 table is updated to include these three rows. The rationale for
`dataSync` (as opposed to `mediaPlayback`, `connectedDevice`, etc.) is: the
service holds a large data structure (the model) in memory and keeps it
synchronised with the process — `dataSync` is the closest semantic match
among Android's allowed FGS types, and is the type Google's documentation
recommends for "background work that's not user-facing audio/video".

### 6.2 Service component

```xml
<service
    android:name=".service.ModelWarmupService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

Inside `<application>`. `exported="false"` because nothing outside our app
needs to bind it.

### 6.3 No other system integrations

The service is not bound; it doesn't talk to any system service beyond the
notification posting and starting itself in the foreground. `engine.warmUp()`
does its own integration (MediaPipe), already documented in US-020.

---

## 7. On-device-model Impact

- The service is the **mechanism** for keeping FunctionGemma warm.
- It does **not** change the engine contract or the catalog.
- **Latency contribution**: zero on the happy path — the model is already
  loaded when the user first presses. On the HyperOS-kill path, the second
  press is the warm one; the first press takes `copy_models_not_ready`.
- **Memory cost**: same 288 MB residency as without the service — the
  service doesn't add memory, it just prevents the OS from reclaiming it.
- **Battery cost**: low — `LlmInference` is idle between calls. The
  foreground service itself costs whatever the notification scaffolding
  costs (rounding-error). HyperOS's aggression against background services
  is precisely *because* most apps abuse this — Curro's foreground service
  is legitimate and is documented for user-side whitelisting.

---

## 8. Android Specification

### 8.1 Files added

- `app/src/main/java/com/curro/app/service/ModelWarmupService.kt`
- `app/src/main/res/drawable/ic_curro_notification.xml` (small mic outline
  vector drawable)
- (Conditional) `app/src/main/java/com/curro/app/util/NotificationChannels.kt`
  — a small helper if the codebase doesn't already have a central place to
  create channels.

### 8.2 Files modified

- `app/src/main/AndroidManifest.xml` — three permission rows + the `<service>`
  declaration.
- `app/src/main/java/com/curro/app/CurroApp.kt` — channel creation + service
  start, after the existing `TelemetryInitializer` boot.
- `app/src/main/res/values/strings.xml` — `copy_warmup_ongoing` = "Curro está
  listo".
- `docs/curro-spec-v1.0.md` §10 — three new rows in the permissions table.
- `docs/MODELS.md` (added in US-019) — HyperOS battery-whitelist steps.

### 8.3 `ModelWarmupService` — exact shape

```kotlin
package com.curro.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.curro.app.domain.repository.FunctionCallEngine
import com.curro.app.util.NotificationChannels
import com.curro.app.util.buildWarmupNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps FunctionGemma warm in memory across app idle periods (spec §4.3).
 *
 * Started from [com.curro.app.CurroApp.onCreate] via
 * `ContextCompat.startForegroundService(...)`. Posts the ongoing notification
 * synchronously inside [onStartCommand] (the platform requires the
 * `startForeground` call within ~5 s of `startForegroundService`); the actual
 * model load runs on a service-scoped IO coroutine so the main thread is
 * never blocked.
 *
 * `START_STICKY` so the platform restarts the service after a kill; on
 * HyperOS this often isn't enough on its own — `docs/MODELS.md` documents
 * the battery-whitelist + autostart toggles the user (= Fran) sets manually.
 *
 * Detect-and-recover: SF-3.2's [FunctionGemmaEngine.decide] is the
 * second line of defence — every call first checks `isReady()` and returns
 * `CurroError.ModelCold` if the engine got killed, kicking `warmUp()` as a
 * side effect for next time.
 */
@AndroidEntryPoint
class ModelWarmupService : Service() {

    @Inject lateinit var engine: FunctionCallEngine

    /** Service-scoped. Cancelled in [onDestroy] so a torn-down service does
     *  not leave a dangling `warmUp` in flight. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1) Platform contract: start the foreground notification first.
        startForeground(
            NotificationChannels.WARMUP_NOTIF_ID,
            buildWarmupNotification(this),
        )

        // 2) Warm the model off the main thread.
        scope.launch {
            engine.warmUp()
            Log.i(TAG, "warm-up scheduled — engine.isReady = ${engine.isReady()}")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Curro/Warmup"
    }
}
```

### 8.4 Notification channel + builder

`app/src/main/java/com/curro/app/util/NotificationChannels.kt`:

```kotlin
package com.curro.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.curro.app.MainActivity
import com.curro.app.R

object NotificationChannels {

    const val WARMUP_CHANNEL_ID = "curro_warmup"
    const val WARMUP_NOTIF_ID = 1001

    /** Idempotent — called from `CurroApp.onCreate`. */
    fun ensureWarmupChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(WARMUP_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            WARMUP_CHANNEL_ID,
            context.getString(R.string.copy_warmup_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.copy_warmup_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannel(channel)
    }
}

/** Public so [ModelWarmupService] can call it without exposing its companion. */
fun buildWarmupNotification(context: Context): Notification {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val pi = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return NotificationCompat.Builder(context, NotificationChannels.WARMUP_CHANNEL_ID)
        .setContentTitle(context.getString(R.string.copy_warmup_ongoing))
        .setSmallIcon(R.drawable.ic_curro_notification)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .setContentIntent(pi)
        .build()
}
```

### 8.5 `CurroApp.onCreate` additions

After the existing telemetry init, append:

```kotlin
// US-023 (SF-3.5) — keep FunctionGemma warm across app idle periods.
NotificationChannels.ensureWarmupChannel(this)
ContextCompat.startForegroundService(
    this,
    Intent(this, ModelWarmupService::class.java),
)
```

### 8.6 Manifest additions

After the existing permission block:

```xml
<!--
    SF-3.5 (US-023): foreground service keeping FunctionGemma warm in memory.
    - POST_NOTIFICATIONS (Android 13+) is runtime-optional. If denied the
      service runs without a visible icon — Android 13+ allows this.
    - FOREGROUND_SERVICE is required on Android 9+.
    - FOREGROUND_SERVICE_DATA_SYNC is required on Android 14+ alongside a
      typed `foregroundServiceType`. `dataSync` is the closest semantic match
      for keep-alive of a large in-memory data structure (the LLM weights).
-->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

Inside `<application>` (after the existing components):

```xml
<service
    android:name=".service.ModelWarmupService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

### 8.7 Drawable — `ic_curro_notification.xml`

`app/src/main/res/drawable/ic_curro_notification.xml`:

```xml
<!--
    SF-3.5 (US-023): notification icon for the model warm-up foreground service.
    Monochrome white (Android notification icons must be monochrome — colour
    is ignored on Lollipop+). 24x24 dp, simple microphone outline.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFFFF">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z" />
</vector>
```

(This is the Material `Icons.Filled.Mic` path — already used in `MicButton.kt`
from US-011. The brief specifies the file content literally so the developer
doesn't redesign it.)

### 8.8 Strings — exact entries

```xml
<!-- US-023 (SF-3.5) — foreground-service notification title. Curro's voice:
     warm, brief. The user sees this in the notification shade. -->
<string name="copy_warmup_ongoing">Curro está listo</string>

<!-- US-023 (SF-3.5) — notification channel name (shown in Settings → Apps →
     Curro → Notifications). User-visible label for "the channel that posts
     the warm-up notification". -->
<string name="copy_warmup_channel_name">Asistente activo</string>

<!-- US-023 (SF-3.5) — notification channel description. -->
<string name="copy_warmup_channel_desc">Curro está cargado y listo para escucharte.</string>
```

### 8.9 Spec §10 additions

In `docs/curro-spec-v1.0.md` §10 (permissions table), add three rows after
the existing ones:

| Permiso | Para | Si se deniega |
|---|---|---|
| `POST_NOTIFICATIONS` | Icono de notificación del foreground service de warm-up del modelo | El servicio sigue corriendo; el icono no aparece en la barra |
| `FOREGROUND_SERVICE` | Mantener el modelo cargado en memoria | El servicio no puede arrancar (manifest-required en Android 9+) |
| `FOREGROUND_SERVICE_DATA_SYNC` | Tipado obligatorio del servicio en Android 14+ | El servicio no puede arrancar en Android 14+ |

### 8.10 `docs/MODELS.md` extension (HyperOS section)

Already specified in US-019 (`docs/MODELS.md` outline includes the HyperOS
section). Re-verify it's present after this SF lands — if US-019 shipped
without it, add it now.

---

## 9. Senior-UX & Copy

The user only sees the notification:

- **Title**: `copy_warmup_ongoing` = "Curro está listo"
- **No body text** — less is more; the title alone communicates the state.
- The notification is **persistent** (`setOngoing(true)`) so the system shows
  it for as long as the service is running. The user cannot dismiss it
  manually (this is the cost of foreground services).

Curro's voice in the notification matches his voice elsewhere: short, warm,
no exclamation marks, no `¡Hola!`.

---

## 10. Acceptance Criteria

Mirroring PRD entry:

- [ ] `app/src/main/java/com/curro/app/service/ModelWarmupService.kt` exists
  as a `@AndroidEntryPoint Service` with the exact shape in §8.3.
- [ ] Manifest declares `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC`, plus the `<service>` with
  `foregroundServiceType="dataSync"`.
- [ ] Notification channel `"curro_warmup"` is created in `CurroApp.onCreate`
  with `IMPORTANCE_MIN`, no sound, no vibration, no badge.
- [ ] Notification builder uses `R.drawable.ic_curro_notification` (vector
  added) and `R.string.copy_warmup_ongoing`. `setOngoing(true)`,
  `setSilent(true)`.
- [ ] Tap on notification opens `MainActivity` via `PendingIntent`.
- [ ] `CurroApp.onCreate` calls `ContextCompat.startForegroundService(this,
  Intent(this, ModelWarmupService::class.java))` after the existing
  telemetry init.
- [ ] `onStartCommand` calls `startForeground` first, then launches the
  warm-up coroutine on `Dispatchers.IO`.
- [ ] `onDestroy` cancels the service scope.
- [ ] Returns `START_STICKY`.
- [ ] **No runtime permission prompt for `POST_NOTIFICATIONS`** — silent
  fall-back if denied.
- [ ] Detect-and-recover strategy **A — check-on-call** confirmed: every
  `FunctionGemmaEngine.decide()` first checks `isReady()` (already wired in
  US-020). This SF adds no extra polling loop.
- [ ] `docs/curro-spec-v1.0.md` §10 has three new permission rows.
- [ ] `docs/MODELS.md` includes the HyperOS battery-whitelist setup steps.
- [ ] No new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green.
- [ ] **No PII** in the notification — only `copy_warmup_ongoing`.

---

## 11. Performance Considerations

- The service's main thread does only the `startForeground` call (fast).
  The model load is on `Dispatchers.IO` — no main-thread blocking.
- `START_STICKY` lets Android re-create the service after kill; the cost is
  one extra notification re-post on each restart.
- The notification channel creation is idempotent and cheap; called once per
  process from `CurroApp.onCreate()`.
- `ContextCompat.startForegroundService` is the cross-API helper —
  guarantees `startForeground` is called within 5 s; `onStartCommand`'s
  first line satisfies that.

---

## 12. Testing Requirements

- [ ] **The Service itself is hard to JVM-test** — Robolectric scope, the
  lifecycle is mostly platform glue. **No JVM test** in this SF.
- [ ] **Manual on the Redmi 15** (after weights are side-loaded):
  - Install + launch the app → `Log.i("Curro/Warmup", "onCreate")` appears
    in logcat.
  - `Log.i("Curro/Llm", "warm-up took <ms>ms")` appears in logcat.
  - `engine.isReady()` returns `true` after the warm-up coroutine completes
    (verifiable indirectly by US-024's first press succeeding under 500 ms).
  - Notification icon appears in the shade with "Curro está listo".
  - Screen-off → screen-on (a few minutes later): notification icon still
    there; `engine.isReady() == true`; next `decide` is warm.
  - `adb shell am force-stop com.curro.app` → next launch: service restarts,
    notification re-posts, warm-up runs again.
  - HyperOS-specific check: after the device sits idle for >30 min with the
    screen off, verify (via `adb logcat`) whether the service stayed alive.
    If killed, document in `docs/MODELS.md`. (Without battery-whitelist, it
    WILL be killed eventually; the second-press recovery path is what
    matters.)
- [ ] **Permission-denied path**: revoke `POST_NOTIFICATIONS` via `adb shell
  pm revoke com.curro.app android.permission.POST_NOTIFICATIONS`, restart
  the app, verify the service still starts and the model still warms
  (notification icon absent).

---

## 13. Implementation Notes

### Why `dataSync`, not other FGS types

Android 14+ requires `foregroundServiceType` to be one of: `camera`,
`connectedDevice`, `dataSync`, `health`, `location`, `mediaPlayback`,
`mediaProjection`, `microphone`, `phoneCall`, `remoteMessaging`,
`specialUse`, `systemExempted`.

- `dataSync` semantically fits: "background work that keeps app data
  synchronised with another source" — read liberally, "keeps model weights
  loaded".
- `mediaPlayback` would be misleading and risk the Play Store rejecting
  abuse claims later.
- `specialUse` requires Play Store justification each release and is for
  cases that don't fit any other type.

`dataSync` is the most-permissive type Curro can credibly claim. If a future
Play Store submission rejects this, switch to `specialUse` with a
justification string (`android:foregroundServiceTypeSpecialUseDescription`).

### Why no `POST_NOTIFICATIONS` runtime prompt

Three reasons:

1. The user (Fran's father) is unlikely to read or understand a system
   permission dialog — adding one in Phase 3 risks confusing him before the
   voice loop even works.
2. The Android 13+ silent fall-back is good enough: the service runs, the
   model warms, only the icon is missing. Fran can grant the permission
   manually if he wants the icon visible.
3. If Phase 8's config menu wants to surface diagnostics ("Curro está
   caliente / cargando"), it can request the permission then with full
   context.

### Why check-on-call (Strategy A), not periodic ping (Strategy B)

- **Battery**: a periodic ping has to wake up the engine on a timer; on a
  Snapdragon 6s Gen 3 even an idle check has cost.
- **User-perception alignment**: the user only cares about latency at the
  moment of a press. Recovering on the press is exactly where the user
  expects to wait if needed.
- **Simpler code**: no scheduling, no `WorkManager`, no AlarmManager.

### Why `SetSilent(true)` AND `IMPORTANCE_MIN`

Belt-and-braces. On some OEM-skinned Androids (cough, HyperOS), the channel
importance is overridden by the user's per-app settings; `setSilent` makes
sure no sound plays regardless. On a low-importance channel `setOngoing` is
also belt-and-braces — the notification shade may already deprioritise it,
but the explicit flag avoids surprises.

### Order of operations

1. Add `app/src/main/res/values/strings.xml` entries.
2. Add `app/src/main/res/drawable/ic_curro_notification.xml`.
3. Add `app/src/main/java/com/curro/app/util/NotificationChannels.kt`.
4. Add `app/src/main/java/com/curro/app/service/ModelWarmupService.kt`.
5. Edit `app/src/main/AndroidManifest.xml` (three permissions + `<service>`).
6. Edit `app/src/main/java/com/curro/app/CurroApp.kt` (channel + service start).
7. Edit `docs/curro-spec-v1.0.md` §10 (three new permission rows).
8. Verify `docs/MODELS.md` HyperOS section (added in US-019).
9. Run `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` —
   green.
10. Install on the Redmi 15; verify the notification appears and the warm-up
    log lines fire.

### Commit scope

`feat(service)` — the foreground service is its own concern; the model engine
it warms up was `feat(llm)` in US-020.

---

## 14. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-15 | android-product-analyst | Initial draft for Phase-3 PM batch. |
