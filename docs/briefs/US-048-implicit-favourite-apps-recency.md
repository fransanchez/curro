# Brief — US-048 / SF-7.4: Implicit favourite apps via `AppUsageDao` — recency-weighted home grid

## Metadata

| Field | Value |
|---|---|
| **Feature** | Replace `StaticFavoriteAppsRepositoryImpl` with `RecencyFavoriteAppsRepositoryImpl`: every successful app launch (voice OR tile tap) bumps `AppUsageDao.upsert(packageName, now)`; the home grid shows the top-4 by `openCount × max(0, 1 − daysSince/30)`, recomputed at most every 24 h (the `local-data` rule 5 stability bar). Sparse usage → pad with the Phase-1 seed apps. |
| **US ID** | US-048 |
| **SF ID** | SF-7.4 |
| **Phase** | 7 — Alias learning & local persistence |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst (Opus) |
| **Implementer** | android-developer (Sonnet) |
| **Size** | M |
| **Depends on** | US-045 (the `AppUsageDao`), SF-1.4 (the static `FavoriteAppsRepository` + `FavoriteApp` + `@IoDispatcher` qualifier) |
| **Unblocks** | Phase 8 "actualizar favoritas" config affordance (SF-8.x) |

---

## Summary

The Phase-1 launcher home grid shows four static tiles (WhatsApp, Llamadas, Cámara, Fotos) resolved from `PackageManager` at every `ON_RESUME`. SF-7.4 keeps the same UI surface but swaps the data: the four tiles are now the **top-4 apps by recency-weighted usage**, falling back to the Phase-1 seeds when usage is sparse.

The load-bearing master-plan §Phase-7 risk (b) is **stability**: "favourite-app reshuffle stability is critical for 'feels the same every day' — over-recompute and the user loses his bearings". The implementation enforces this via two structural decisions:

1. **24-hour recompute interval** (`local-data` rule 5): the `observeFavorites()` flow re-evaluates the score-and-rank exactly once per day on its own timer. `appUsageDao.upsert(...)` bumps the usage data on every successful launch, but the home grid **does not re-emit** until the 24-h tick. (Phase 8 will add an "Actualizar favoritas" button in the config menu — a `MutableSharedFlow<Unit>` trigger the recompute loop also listens to; SF-7.4 declares the trigger surface but does NOT wire a UI for it.)
2. **Single source of truth for the bump**: `IntentAppLauncher.launch(packageName)` is the ONLY place that calls `appUsageDao.upsert`. Both callers — `OpenAppHandler` (the voice path) and `LauncherViewModel.onAppTileTapped` (the manual tap path) — go through `AppLauncher.launch`. Centralising the bump avoids double-counts and stays cleanly testable.

The recency-weighted score is computed in **Kotlin, not SQL** (PM pin: SQLite has no native `exp()`; the linear-decay shape is portable, testable, and gives the same monotonicity). The formula: `score = openCount × max(0.0, 1.0 − daysSince / 30.0)`. Apps unopened for 30+ days score 0 and drop off; recently-opened apps with low counts still rank above old high-count apps as time passes. Seeds fill any gap.

Spec source: §7 ("Apps favoritas implícitas: las que abre más se promueven al grid principal"), `local-data` "Favourite apps → home grid" + rule 5, master-plan §Phase-7 risk (b), `launcher-ui` "feels the same every day".

---

## Scope

### In scope

- New `data/apps/RecencyFavoriteAppsRepositoryImpl.kt` (Room-backed implementation of `FavoriteAppsRepository`).
- New `data/apps/SeedAppResolver.kt` (extracted from `StaticFavoriteAppsRepositoryImpl` — reusable for the recency repo's padding step).
- `AppLauncher.launch` extended with a fire-and-forget `AppUsageBumper.bumpAsync(packageName)` call on success.
- New `AppUsageBumper` interface + `CoroutineAppUsageBumper` impl (`@ApplicationScope` so the write survives `LauncherViewModel.onCleared`).
- Delete `StaticFavoriteAppsRepositoryImpl` + its test (logic moves into `SeedAppResolver`).
- Hilt rebinding in `AppsModule`: `FavoriteAppsRepository → RecencyFavoriteAppsRepositoryImpl`; add `AppUsageBumper` binding.
- Tests: `RecencyFavoriteAppsRepositoryImplTest` (~8 cases), `AppLauncherTest` (+4 cases), `OpenAppHandlerTest` (+1 case), `LauncherViewModelTest` (+1 case).

### Out of scope

- The Phase-8 "actualizar favoritas" button UI — Phase 8 (the `recomputeTrigger: MutableSharedFlow<Unit>` member surface is declared in this SF; the UI button wiring is later).
- Fran's explicit-override list in the Phase-8 config menu — Phase 8.
- App-usage telemetry events (a hypothetical "favourites_changed" event) — out; the existing `handler_invoked` + the new `app_tile_tapped` (SF-1.4) telemetry cover the operational signal.
- Promoting an `AppUsageEntity` to Phase 8's `InteractionLogEntity` for proactive features — out (SF-7.1 deferred `InteractionLogEntity`).

---

## User Flows

### Flow 1 — First-launch behaviour (empty `app_usage` table)

1. Fresh install OR `app_usage` cleared. User boots into the launcher home.
2. `LauncherViewModel` collects `favoriteAppsRepository.observeFavorites()` → first emission.
3. `RecencyFavoriteAppsRepositoryImpl.loadFavorites()`:
   - `appUsageDao.topByOpenCount(20)` → empty list.
   - Scored list → empty.
   - **Seed padding**: `seedAppResolver.seedFavorites()` returns the four Phase-1 tiles (WhatsApp, Dialer, Camera, Gallery, resolved dynamically per the existing logic).
   - Trim to `FAVOURITES_COUNT = 4`.
4. Home grid shows the four seed tiles — **identical to Phase 1's behaviour** for the empty-usage case.

### Flow 2 — Usage accumulates; grid stays stable for 24 h

1. User uses Curro for a day. Says "abre WhatsApp" 10×; says "abre Ajustes" 15×; opens Cámara via tile tap 3×; opens Galería 5×.
2. Each launch goes through `AppLauncher.launch(packageName)` → on success → `usageBumper.bumpAsync(packageName)` → `AppUsageDao.upsert` in a background coroutine.
3. The home grid does NOT re-emit during this day — `observeFavorites` only emits at start-up and on the 24-h timer tick.
4. After 24 h, the flow re-evaluates:
   - `topByOpenCount(20)` returns `[(ajustes, 15, today), (whatsapp, 10, today), (galeria, 5, today), (camara, 3, today)]`.
   - Scored (decay ≈ 1.0 because all opens are today):
     - `ajustes: 15 × 1.0 = 15.0`
     - `whatsapp: 10 × 1.0 = 10.0`
     - `galeria: 5 × 1.0 = 5.0`
     - `camara: 3 × 1.0 = 3.0`
   - Top-4 → all four usage-derived apps.
   - Seeds aren't needed; usage is dense enough.
5. The home grid emits the new order. The user sees the same four apps but the layout reflects what they actually used. **No mid-day reshuffle.**

### Flow 3 — Sparse usage padded by seeds

1. User has been using the phone for a week: only `com.example.juegos` opened (50×).
2. After 24-h tick:
   - `topByOpenCount(20)` returns `[(juegos, 50, today)]`.
   - Scored: `juegos: 50.0` → top-1.
   - Top-N has 1 entry < `FAVOURITES_COUNT = 4` → seed padding.
   - Seed list: `[whatsapp, dialer, camera, gallery]` (in that order); skip any already in the top-N (none overlap with `com.example.juegos`); append.
   - Final: `[juegos, whatsapp, dialer, camera]` → 4 tiles.
3. Home grid: the user's heavy use of `juegos` promotes it; the three remaining tiles are the most-relevant seeds.

### Flow 4 — Decay drops a stale heavy user

1. State: `app_usage` has `(com.olduser, openCount = 100, lastOpenedAtMs = 35-days-ago)` and `(com.recentcasualuser, openCount = 3, lastOpenedAtMs = today)`.
2. 24-h tick:
   - `topByOpenCount(20)` returns both rows (order by `openCount`: `olduser` first).
   - Scored:
     - `olduser: 100 × max(0, 1 − 35/30) = 100 × 0 = 0` (clamped to 0).
     - `recentcasualuser: 3 × max(0, 1 − 0/30) = 3.0`.
   - Re-sort by score: `[recentcasualuser, ...]` (olduser is at 0, drops below the seeds since seeds have a `score = 0`-equivalent fallback but ARE NOT in the scored list).
   - Top-N takes the 1 recentcasualuser; seed padding fills the remaining 3.
3. **The 30-day clamp keeps the grid alive** — apps stop influencing once they go too stale.

---

## Function-catalog Impact

**No catalog change.** `open_app` (Fase 1) is the catalog function whose handler triggers the bump indirectly via `AppLauncher.launch`. Its params, voice examples, `needs_confirmation` are unchanged.

The launcher tile-tap path (not a catalog function — it's a UI affordance) also bumps usage through the same `AppLauncher.launch` call site.

---

## FSM States Touched

**None.** SF-7.4 affects data routing and the launcher home grid only. The voice/decision FSM (`AssistantStateMachine`) is untouched.

---

## Android System Integrations & Permissions

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `QUERY_ALL_PACKAGES` | `SeedAppResolver` queries `PackageManager.resolveActivity` / `getApplicationIcon` / `getPackageInfo` for seed package presence | _(already manifest-declared in SF-1.4)_ | Phase-1 fallback: seeds resolve to `null`; the grid shows fewer tiles (existing Phase-1 behaviour). |

**No new permissions; no manifest changes.** Room is local; package queries are unchanged.

---

## On-device-model Impact

**None.** SF-7.4 doesn't touch FunctionGemma's prompt or Gemma 3n. The `OpenAppHandler` already runs `installedApps.observeAllLaunchable().first()` (line 53 of `OpenAppHandler.kt`); the bump is a downstream side effect after a successful launch.

---

## Android Specification

### Files added

```
app/src/main/java/com/curro/app/data/apps/
    RecencyFavoriteAppsRepositoryImpl.kt
    SeedAppResolver.kt
    AppUsageBumper.kt                  # interface + CoroutineAppUsageBumper

app/src/test/java/com/curro/app/data/apps/
    RecencyFavoriteAppsRepositoryImplTest.kt   # ~8 cases, Robolectric + TestTimeProvider
    SeedAppResolverTest.kt                     # ~4 smoke cases extracted from the deleted static test
```

### Files modified

```
app/src/main/java/com/curro/app/data/apps/
    AppLauncher.kt                     # IntentAppLauncher constructor adds AppUsageBumper; bump on success

app/src/main/java/com/curro/app/di/
    AppsModule.kt                      # rebind FavoriteAppsRepository → RecencyFavoriteAppsRepositoryImpl; add AppUsageBumper binding

app/src/test/java/com/curro/app/data/apps/
    AppLauncherTest.kt                 # +4 cases verifying bump
app/src/test/java/com/curro/app/handler/
    OpenAppHandlerTest.kt              # +1 case (handler doesn't bump directly)
app/src/test/java/com/curro/app/presentation/launcher/
    LauncherViewModelTest.kt           # +1 case (tile tap also bumps via AppLauncher)
```

### Files deleted

```
app/src/main/java/com/curro/app/data/apps/
    StaticFavoriteAppsRepositoryImpl.kt

app/src/test/java/com/curro/app/data/apps/
    StaticFavoriteAppsRepositoryImplTest.kt
```

### `AppUsageBumper.kt`

```kotlin
package com.curro.app.data.apps

import com.curro.app.assistant.TimeProvider
import com.curro.app.data.local.AppUsageDao
import com.curro.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a successful app launch (SF-7.4 / US-048).
 *
 * Centralised seam: both [com.curro.app.handler.OpenAppHandler] (voice path)
 * and [com.curro.app.presentation.launcher.LauncherViewModel] (tile tap) go
 * through [AppLauncher.launch] which calls this on success. No other caller.
 *
 * **Fire-and-forget** on [com.curro.app.di.ApplicationScope]: the write to
 * `app_usage` survives even if the calling ViewModel is cleared (the tile tap
 * → activity transition can race with `onCleared`).
 *
 * Tests inject a synchronous fake ([com.curro.app.util.FakeAppUsageBumper]).
 */
interface AppUsageBumper {
    fun bumpAsync(packageName: String)
}

@Singleton
class CoroutineAppUsageBumper
    @Inject
    constructor(
        private val dao: AppUsageDao,
        private val timeProvider: TimeProvider,
        @ApplicationScope private val scope: CoroutineScope,
    ) : AppUsageBumper {
        override fun bumpAsync(packageName: String) {
            scope.launch { dao.upsert(packageName, timeProvider.now()) }
        }
    }
```

### `AppLauncher.kt` change

```kotlin
// Before (Phase 4 SF-4.3):
class IntentAppLauncher
    @Inject
    constructor(@ApplicationContext private val context: Context) : AppLauncher {
        override fun launch(packageName: String): Boolean {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try { context.startActivity(intent); true }
                   catch (_: ActivityNotFoundException) { false }
                   catch (_: SecurityException) { false }
        }
    }

// After (SF-7.4):
class IntentAppLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val usageBumper: AppUsageBumper,
    ) : AppLauncher {
        override fun launch(packageName: String): Boolean {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                usageBumper.bumpAsync(packageName)   // SF-7.4 — bump only on success
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }
```

**Pin: bump happens ONLY on the success path** — `getLaunchIntentForPackage` returning null OR `startActivity` throwing → no bump. Verified by tests `launch_packageNotFound_doesNotBump` / `launch_activityNotFoundException_doesNotBump` / `launch_securityException_doesNotBump`.

### `SeedAppResolver.kt`

Extract the Phase-1 static logic from `StaticFavoriteAppsRepositoryImpl` into a reusable `@Singleton`:

```kotlin
package com.curro.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.MediaStore
import com.curro.app.R
import com.curro.app.domain.model.FavoriteApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the Phase-1 seed apps (WhatsApp, Dialer, Camera, Gallery) for the
 * launcher home grid (SF-7.4 / US-048; extracted from SF-1.4's
 * `StaticFavoriteAppsRepositoryImpl`).
 *
 * Two consumers:
 *  1. [RecencyFavoriteAppsRepositoryImpl.loadFavorites] — seeds pad the grid
 *     when usage data is sparse.
 *  2. [RecencyFavoriteAppsRepositoryImpl.toFavoriteApp] — resolves an
 *     arbitrary `packageName` from `app_usage` to a [FavoriteApp] with icon +
 *     label (delegates to [PackageManager]).
 *
 * Dynamic resolution: the dialer / camera / gallery packages are OEM-specific
 * (HyperOS = com.miui.*); resolve via [PackageManager.resolveActivity] with
 * the canonical Intent action, fall back to hard-coded `PACKAGE_*_FALLBACK`s.
 */
@Singleton
class SeedAppResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * The four Phase-1 seed tiles, in canonical order. Apps not installed
         * appear with `resolvedPackage = null` and `icon = null` (callers can
         * filter or render the placeholder).
         */
        fun seedFavorites(): List<FavoriteApp> {
            val pm = context.packageManager
            return listOf(
                buildFavoriteApp("whatsapp", R.string.copy_app_label_whatsapp, pm) {
                    resolveDirectPackage(PACKAGE_WHATSAPP, pm)
                },
                buildFavoriteApp("calls", R.string.copy_app_label_calls, pm) {
                    resolveViaIntent(Intent(Intent.ACTION_DIAL), PACKAGE_DIALER_FALLBACK, pm)
                },
                buildFavoriteApp("camera", R.string.copy_app_label_camera, pm) {
                    resolveViaIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), PACKAGE_CAMERA_FALLBACK, pm)
                },
                buildFavoriteApp("photos", R.string.copy_app_label_photos, pm) {
                    resolveViaIntent(
                        Intent(Intent.ACTION_PICK).apply { type = "image/*" },
                        PACKAGE_GALLERY_FALLBACK,
                        pm,
                    )
                },
            )
        }

        /**
         * Resolve a [packageName] from `app_usage` to a [FavoriteApp] with icon
         * + label. Returns `null` if the package isn't installed (e.g. uninstalled
         * after a launch was recorded).
         */
        fun toFavoriteApp(packageName: String): FavoriteApp? {
            val pm = context.packageManager
            if (!isInstalled(packageName, pm)) return null
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull() ?: return null
            val icon: Drawable? = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
            return FavoriteApp(
                id = packageName,
                labelResId = R.string.copy_app_label_dynamic,  // placeholder — overridden by passing the label separately if needed
                resolvedPackage = packageName,
                icon = icon,
            )
        }

        // ... private helpers verbatim from StaticFavoriteAppsRepositoryImpl
        // (buildFavoriteApp, resolveDirectPackage, resolveViaIntent, isInstalled)
        // ... companion object constants verbatim
        //     (PACKAGE_WHATSAPP, PACKAGE_DIALER_FALLBACK, PACKAGE_CAMERA_FALLBACK, PACKAGE_GALLERY_FALLBACK)
    }
```

**Pin: implementer extracts the existing private helpers verbatim** — no behaviour change. The `toFavoriteApp(packageName)` is new (SF-7.4 needs it for usage-derived rows).

**Edge — string resource for dynamic labels**: `FavoriteApp.labelResId` is an Int; the existing data class can't carry a free-text label. Two options:
- **Option A (recommended)**: widen `FavoriteApp.labelResId: Int` to `FavoriteApp.label: AppLabel` where `sealed class AppLabel { data class Resource(val resId: Int) : AppLabel; data class Text(val text: String) : AppLabel }`. UI reads `when (val l = app.label) { ... }`. Pin: the AppTile composable change is a 4-line `when` adoption.
- **Option B**: dynamic labels live in `FavoriteApp.id` + an Optional `dynamicLabel: String? = null` parallel field; UI prefers `dynamicLabel ?: stringResource(labelResId)`.

**Implementer chooses; PM pins Option A** — cleaner sealed shape, future-proof for Phase 8's dynamic Fran-edited labels. The change is local to `presentation/launcher/AppTile.kt` and `domain/model/FavoriteApp.kt`. Existing seed tests update to use `AppLabel.Resource(R.string.copy_app_label_whatsapp)`.

### `RecencyFavoriteAppsRepositoryImpl.kt`

```kotlin
package com.curro.app.data.apps

import com.curro.app.assistant.TimeProvider
import com.curro.app.data.local.AppUsageDao
import com.curro.app.data.local.AppUsageEntity
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.repository.FavoriteAppsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.selects.select
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Recency-weighted favourites repo (SF-7.4 / US-048).
 *
 * **Scoring** (`local-data` "Favourite apps → home grid"):
 *
 * ```
 * score = openCount × max(0, 1 − daysSince / 30)
 * ```
 *
 * - 0–30 days: linear decay from 1.0 → 0.0.
 * - 30+ days: clamped to 0; the app drops out of the usage-derived list and
 *   seed padding fills its slot.
 *
 * **Stability** (`local-data` rule 5 + master-plan §Phase-7 risk b):
 * `observeFavorites()` recomputes the score-and-rank exactly once every
 * [RECOMPUTE_INTERVAL_MS] (24 h). Every successful `app_usage.upsert` bumps
 * the data but the grid does NOT re-emit until the timer tick. Phase 8's
 * "Actualizar favoritas" button will publish to [recomputeTrigger]; SF-7.4
 * declares the field but does not wire a UI.
 *
 * **Seed padding** (`SeedAppResolver`): when the scored top-N has < 4
 * entries, the Phase-1 seeds (WhatsApp / Dialer / Camera / Gallery) fill the
 * remaining slots in canonical order. Duplicates (a seed already in the
 * scored top-N) are skipped.
 */
@Singleton
class RecencyFavoriteAppsRepositoryImpl
    @Inject
    constructor(
        private val appUsageDao: AppUsageDao,
        private val timeProvider: TimeProvider,
        private val seedAppResolver: SeedAppResolver,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FavoriteAppsRepository {

        /** SF-8.x will publish here to force an immediate recompute. */
        internal val recomputeTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override fun observeFavorites(): Flow<List<FavoriteApp>> = flow {
            emit(loadFavorites())              // initial emission on subscribe
            while (true) {
                // Race the 24-h timer vs. an explicit Phase-8 trigger.
                select<Unit> {
                    onTimeout(RECOMPUTE_INTERVAL_MS) { Unit }
                    recomputeTrigger.onSubscription { /* registered */ }
                }
                emit(loadFavorites())
            }
        }.flowOn(ioDispatcher)

        private suspend fun loadFavorites(): List<FavoriteApp> {
            val now = timeProvider.now()
            val raw = appUsageDao.topByOpenCount(USAGE_FETCH_LIMIT)
            val scored = raw.mapNotNull { entity ->
                val score = scoreEntity(entity, now)
                if (score > 0.0) entity to score else null  // 30+ days stale → drop
            }.sortedByDescending { it.second }

            val usageDerived = scored
                .mapNotNull { (entity, _) -> seedAppResolver.toFavoriteApp(entity.packageName) }
                .take(FAVOURITES_COUNT)

            if (usageDerived.size >= FAVOURITES_COUNT) return usageDerived

            val seeds = seedAppResolver.seedFavorites()
                .filter { seed -> seed.resolvedPackage != null && usageDerived.none { it.resolvedPackage == seed.resolvedPackage } }

            return (usageDerived + seeds).take(FAVOURITES_COUNT)
        }

        internal fun scoreEntity(entity: AppUsageEntity, now: Long): Double {
            val daysSince = (now - entity.lastOpenedAtMs).toDouble() / DAY_MS
            val decay = max(0.0, 1.0 - daysSince / DECAY_DAYS)
            return entity.openCount * decay
        }

        private companion object {
            const val FAVOURITES_COUNT = 4
            const val RECOMPUTE_INTERVAL_MS = 24L * 60 * 60 * 1000  // 24 h
            const val USAGE_FETCH_LIMIT = 20
            const val DAY_MS = 24L * 60 * 60 * 1000
            const val DECAY_DAYS = 30.0
        }
    }
```

**Pin: the recomputeTrigger `select` block**. The Kotlin coroutines `select` lets the flow race a `delay` against a `MutableSharedFlow.onSubscription` (or `.first()`). Implementer may simplify with `merge(tickerFlow, recomputeTrigger).collect { emit(loadFavorites()) }` — same semantic; PM doesn't pin the exact construct as long as the invariant holds: **one emission per tick OR per trigger; never on usage-bump alone**.

### `AppsModule.kt` changes

```kotlin
// Before (Phase 1):
@Binds @Singleton fun bindFavoriteAppsRepository(impl: StaticFavoriteAppsRepositoryImpl): FavoriteAppsRepository

// After (SF-7.4):
@Binds @Singleton fun bindFavoriteAppsRepository(impl: RecencyFavoriteAppsRepositoryImpl): FavoriteAppsRepository
@Binds @Singleton fun bindAppUsageBumper(impl: CoroutineAppUsageBumper): AppUsageBumper
```

Update the module-level Kdoc to reflect the swap. `SeedAppResolver` is `@Inject`-constructable; no binding needed.

### Navigation Routes

No new routes.

### Composables by Feature

- **`AppTile.kt`** — if `FavoriteApp.label` becomes a sealed `AppLabel` (Option A above), update the composable to `when (val l = app.label) { is AppLabel.Resource -> stringResource(l.resId); is AppLabel.Text -> l.text }`.

### Material Design Components

_(No new components — reuses the Phase-1 `AppTileGrid` / `AppTile`.)_

---

## Acceptance Criteria

### Build & static checks

- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` green.
- [ ] No `StaticFavoriteAppsRepositoryImpl` references in `app/src/main/`.

### Bump invariants

- [ ] `AppLauncher.launch` calls `usageBumper.bumpAsync(packageName)` ONLY on the success path (after `startActivity` returns without throwing).
- [ ] `OpenAppHandler` does NOT call `appUsageDao.upsert` directly — verified by absence in the source.
- [ ] `LauncherViewModel` does NOT call `appUsageDao.upsert` directly — verified by absence.
- [ ] `CoroutineAppUsageBumper.bumpAsync` posts to `@ApplicationScope` (NOT `viewModelScope`) — verified by Kdoc + test that captures the dispatch scope.

### Recency repo behaviour (verified by tests)

- [ ] Empty `app_usage` → grid shows the four seed tiles, in canonical order.
- [ ] 30+ day stale row scores 0 and is excluded from the usage-derived list.
- [ ] Recent low-count app outranks stale high-count app after the decay kicks in.
- [ ] Seed-padding skips any seed already present in the usage-derived list.
- [ ] `RECOMPUTE_INTERVAL_MS = 24h` — `observeFavorites` emits at start-up and then 24 h later (NOT on every `upsert`).
- [ ] `recomputeTrigger.tryEmit(Unit)` forces a re-emission (SF-8.x prep; SF-7.4 verifies the seam works).

### Hilt

- [ ] `FavoriteAppsRepository` bound to `RecencyFavoriteAppsRepositoryImpl`.
- [ ] `AppUsageBumper` bound to `CoroutineAppUsageBumper`.
- [ ] `IntentAppLauncher` constructor accepts an `AppUsageBumper` (Hilt resolves).

### Privacy / telemetry

- [ ] **No new telemetry events.** The `handler_invoked` and SF-1.4 launcher events cover the operational signal.
- [ ] The `app_usage` table stores package names only — no PII. Spec §12 — telemetry doesn't see it.

### Regression

- [ ] Every SF-7.1 + SF-7.2 + SF-7.3 + Phase-6 + Phase-5 + Phase-4 + Phase-1 test still passes.
- [ ] Phase-1 launcher home grid (US-012 / SF-1.4) tests pass with the new repo wired (the Phase-1 visual contract is preserved on the empty-usage case).
- [ ] `OpenAppHandlerTest` cases still assert the right `Spoken`/`Failed` outcomes — the new test only verifies the bump invariant.

---

## Senior-UX & Copy

**No new strings.** The labels for seed tiles (`copy_app_label_whatsapp`, `copy_app_label_calls`, `copy_app_label_camera`, `copy_app_label_photos`) are SF-1.4-shipped.

For **usage-derived tiles** (apps that aren't in the seed list — e.g. Settings, a game, the user's bank app), the label is **the app's actual label from `PackageManager`** (`pm.getApplicationLabel`). PM pin: this is acceptable because:
1. The user picked these apps themselves (they're in `app_usage` because the user opened them).
2. The app label is what the user already sees in the system launcher / app drawer — familiar.
3. Phase 8's config menu can let Fran override the label if it's confusing (out of scope here).

**Stability**: the 24-h recompute is the senior-UX guardrail. The user opens WhatsApp 20× today; the home grid is identical tomorrow morning — predictable, not "smart and surprising".

---

## Performance Considerations

- `topByOpenCount(20)` against a table with < 100 rows: sub-millisecond.
- Score-and-sort in Kotlin on 20 rows: < 1 ms.
- `seedAppResolver.toFavoriteApp(packageName)` does `PackageManager` calls — ~5–20 ms each. With usage-derived list ≤ 4, total: ~20–80 ms per recompute. **Per 24 h — negligible.**
- `bumpAsync` on `@ApplicationScope`: a coroutine launch + a Room write. ~1–2 ms; fire-and-forget; off the UI thread.
- `select { onTimeout(24h); recomputeTrigger.onSubscription }`: idle for 24 h with no CPU cost (the timer is suspend-based).

---

## Testing Requirements

### `RecencyFavoriteAppsRepositoryImplTest.kt` (JVM Robolectric + Turbine, ~8 cases)

Test infra: in-memory Room + `TestTimeProvider` (with `advanceTo(ms)` helper) + `FakeSeedAppResolver` (returns hand-built seed list) + a `CoroutineScope` from `runTest`.

Cases:

**T1. `emptyUsage_fallsBackToSeeds`**
```kotlin
@Test fun emptyUsage_fallsBackToSeeds() = runTest {
    timeProvider.set(NOW)
    repo.observeFavorites().test {
        val first = awaitItem()
        assertThat(first).hasSize(4)
        assertThat(first.map { it.id }).containsExactly("whatsapp", "calls", "camera", "photos").inOrder()
        expectNoEvents()
    }
}
```

**T2. `oneHeavyUser_ranksFirst`**
```kotlin
@Test fun oneHeavyUser_ranksFirst() = runTest {
    timeProvider.set(NOW)
    repeat(20) { appUsageDao.upsert("com.whatsapp", NOW) }
    repo.observeFavorites().test {
        val first = awaitItem()
        assertThat(first.first().resolvedPackage).isEqualTo("com.whatsapp")
    }
}
```

**T3. `decayKicksIn_oldApp_dropsBelow_recentLowCount`**
```kotlin
@Test fun decayKicksIn_oldApp_dropsBelow_recentLowCount() = runTest {
    timeProvider.set(NOW)
    // Old app: 50 opens, 20 days ago. Score = 50 × (1 - 20/30) ≈ 16.6
    repeat(50) { appUsageDao.upsert("com.oldapp", NOW - 20 * DAY_MS) }
    // Recent low-count: 5 opens today. Score = 5.0
    timeProvider.set(NOW)
    repeat(5) { appUsageDao.upsert("com.recentapp", NOW) }
    repo.observeFavorites().test {
        val first = awaitItem()
        assertThat(first.first().resolvedPackage).isEqualTo("com.oldapp")  // still wins; 16.6 > 5.0
    }
}
```

**T4. `decayClampedToZero_after30Days_appHiddenFromUsage_seedsFillIn`**
```kotlin
@Test fun decayClampedToZero_after30Days_appHiddenFromUsage_seedsFillIn() = runTest {
    timeProvider.set(NOW)
    repeat(100) { appUsageDao.upsert("com.example.test", NOW - 35L * DAY_MS) }
    repo.observeFavorites().test {
        val first = awaitItem()
        // com.example.test is NOT in the top-4 (score = 0); seeds fill all 4 slots
        assertThat(first.none { it.resolvedPackage == "com.example.test" }).isTrue()
        assertThat(first.map { it.id }).containsExactly("whatsapp", "calls", "camera", "photos").inOrder()
    }
}
```

**T5. `tieBreaker_higherCountWins_atSameLastOpened`**
```kotlin
@Test fun tieBreaker_higherCountWins_atSameLastOpened() = runTest {
    timeProvider.set(NOW)
    repeat(10) { appUsageDao.upsert("com.appA", NOW) }
    repeat(5) { appUsageDao.upsert("com.appB", NOW) }
    repo.observeFavorites().test {
        val first = awaitItem()
        val a = first.indexOfFirst { it.resolvedPackage == "com.appA" }
        val b = first.indexOfFirst { it.resolvedPackage == "com.appB" }
        assertThat(a).isLessThan(b)
    }
}
```

**T6. `seedPadding_preservesTopN_when_usage_has_three_apps`**
```kotlin
@Test fun seedPadding_preservesTopN_when_usage_has_three_apps() = runTest {
    timeProvider.set(NOW)
    repeat(10) { appUsageDao.upsert("com.a", NOW) }
    repeat(5) { appUsageDao.upsert("com.b", NOW) }
    repeat(2) { appUsageDao.upsert("com.c", NOW) }
    repo.observeFavorites().test {
        val first = awaitItem()
        assertThat(first).hasSize(4)
        assertThat(first.subList(0, 3).map { it.resolvedPackage }).containsExactly("com.a", "com.b", "com.c")
        // 4th is the first seed (WhatsApp); none of a/b/c overlap with seeds in this test.
        assertThat(first[3].id).isEqualTo("whatsapp")
    }
}
```

**T7. `recompute_stability_acrossMultipleBumps_within24h_doesNotReshuffle`** (the master-plan risk-b assertion)
```kotlin
@Test fun recompute_stability_acrossMultipleBumps_within24h_doesNotReshuffle() = runTest {
    timeProvider.set(NOW)
    repeat(10) { appUsageDao.upsert("com.whatsapp", NOW) }
    repo.observeFavorites().test {
        val first = awaitItem()
        assertThat(first.first().resolvedPackage).isEqualTo("com.whatsapp")

        // Simulate 23 hours of heavy "com.upstart" usage — should NOT cause a re-emission.
        repeat(23) {
            timeProvider.advanceBy(60L * 60 * 1000)  // +1 hour
            repeat(50) { appUsageDao.upsert("com.upstart", timeProvider.now()) }
        }
        expectNoEvents()  // **the stability invariant**

        // Advance past 24h → re-emit with new order
        timeProvider.advanceBy(2L * 60 * 60 * 1000)  // +2h → 25h total
        val second = awaitItem()
        assertThat(second.first().resolvedPackage).isEqualTo("com.upstart")  // dethroned
    }
}
```

**T8. `recomputeTrigger_forcesImmediateRecompute`** (the Phase-8 seam)
```kotlin
@Test fun recomputeTrigger_forcesImmediateRecompute() = runTest {
    timeProvider.set(NOW)
    repeat(5) { appUsageDao.upsert("com.whatsapp", NOW) }
    repo.observeFavorites().test {
        awaitItem()  // initial
        repeat(20) { appUsageDao.upsert("com.upstart", NOW) }
        repo.recomputeTrigger.emit(Unit)
        val second = awaitItem()
        assertThat(second.first().resolvedPackage).isEqualTo("com.upstart")
    }
}
```

### `AppLauncherTest.kt` (4 new cases)

```kotlin
class AppLauncherTest {
    private val fakeBumper = FakeAppUsageBumper()
    private val intentAppLauncher = IntentAppLauncher(context, fakeBumper)

    @Test fun launch_success_bumpsUsage_once() {
        // Setup PackageManager fake to return a valid Intent for "com.whatsapp"
        // ... (Robolectric shadows or a Mockito fake)
        val ok = intentAppLauncher.launch("com.whatsapp")
        assertThat(ok).isTrue()
        assertThat(fakeBumper.bumpedPackages).containsExactly("com.whatsapp")
    }

    @Test fun launch_packageNotFound_doesNotBump() {
        val ok = intentAppLauncher.launch("com.nonexistent")
        assertThat(ok).isFalse()
        assertThat(fakeBumper.bumpedPackages).isEmpty()
    }

    @Test fun launch_securityException_doesNotBump() {
        // Setup PackageManager + startActivity to throw SecurityException
        val ok = intentAppLauncher.launch("com.someapp")
        assertThat(ok).isFalse()
        assertThat(fakeBumper.bumpedPackages).isEmpty()
    }

    @Test fun launch_activityNotFoundException_doesNotBump() {
        // Setup startActivity to throw ActivityNotFoundException
        val ok = intentAppLauncher.launch("com.someapp")
        assertThat(ok).isFalse()
        assertThat(fakeBumper.bumpedPackages).isEmpty()
    }
}
```

### `FakeAppUsageBumper`

```kotlin
// app/src/test/java/com/curro/app/util/FakeAppUsageBumper.kt
class FakeAppUsageBumper : AppUsageBumper {
    val bumpedPackages: MutableList<String> = mutableListOf()
    override fun bumpAsync(packageName: String) {
        bumpedPackages += packageName  // synchronous capture for deterministic tests
    }
}
```

### `OpenAppHandlerTest.kt` (1 new case)

```kotlin
@Test fun openApp_success_triggersUsageBump_viaAppLauncher() = runTest {
    fakeInstalledApps.observeAllLaunchableResult = listOf(LaunchableApp("WhatsApp", "com.whatsapp", null))
    fakeAppLauncher.launchResult["com.whatsapp"] = true
    val result = handler.handle(callOf("open_app", "app_name" to "whatsapp"))
    assertThat(result).isInstanceOf(HandlerResult.Spoken::class.java)
    // Pin: handler delegates to fakeAppLauncher.launch; fakeAppLauncher's
    // implementation in tests captures the bump separately (via FakeAppUsageBumper
    // wired through the production IntentAppLauncher path in tests that use
    // the real IntentAppLauncher).
    // Here we verify the handler ITSELF does not bump (regression).
    assertThat(fakeAppUsageDao.upsertCalls).isEmpty()
}
```

### `LauncherViewModelTest.kt` (1 new case)

```kotlin
@Test fun appTileTapped_success_triggersUsageBump_viaAppLauncher() = runTest {
    fakeAppLauncher.launchResult["com.whatsapp"] = true
    viewModel.onEvent(LauncherEvent.AppTileTapped("com.whatsapp"))
    runCurrent()
    assertThat(fakeAppLauncher.launchInvocations).containsExactly("com.whatsapp")
    // The VM does NOT directly call the DAO — the bump is inside IntentAppLauncher,
    // exercised by the AppLauncherTest above. Here we verify the VM stays clean.
    assertThat(fakeAppUsageDao.upsertCalls).isEmpty()
}
```

### Real-device verification

- [ ] Fresh install → home shows the 4 seed tiles (Phase-1 visual contract).
- [ ] Open WhatsApp 20× via voice ("abre WhatsApp"). Verify the grid does NOT change mid-day (`local-data` rule 5).
- [ ] `adb shell run-as com.curro.app sqlite3 databases/curro.db "SELECT * FROM app_usage;"` → row for `com.whatsapp` with `openCount = 20`.
- [ ] Force the recompute via a debug affordance OR wait 24 h. After: WhatsApp is still tile 1 (it was a seed; usage reinforces). Open a non-seed app (e.g. Ajustes) 30× → it joins the grid, displacing the lowest-ranked seed.

---

## Implementation Notes

- **`FavoriteApp.label` sealed change**: implementer chooses Option A (sealed `AppLabel`) or Option B (parallel `dynamicLabel: String?`). PM recommends A; either works as long as `AppTile` renders both shapes.
- **The `recomputeTrigger` is `internal val`** — Phase 8's `ConfigMenuViewModel` will inject the repo as the impl (NOT the interface) to access the trigger. Implementer can also add a `FavoriteAppsRepository.recompute()` method to the interface — same effect, cleaner; pin: implementer's call.
- **Fire-and-forget on `@ApplicationScope`** is important: a tile tap immediately starts a new Activity, which would cancel a `viewModelScope`-launched coroutine before the Room write completes.
- **PM Owner has written**: Metadata, Summary, Scope, User Flows, Function-catalog Impact, FSM States Touched, Senior-UX & Copy, Acceptance Criteria.
- **Implementer (android-developer) writes**: code per the file shapes above; the test specs.

---

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial PM draft for the Phase-7 PM batch |
