# US-012 — SF-1.4 · Static favourite-apps grid

> **Spec trace:** spec §11 (Curro launcher home — app tiles)
> **Master-plan:** SF-1.4
> **Phase:** 1 — Launcher base
> **Depends on:** US-011 (SF-1.3 — MicButton + LauncherEvent/SideEffect plumbing)
> **Size:** M

---

## 1. Goal

Add four large app tiles to the launcher home: WhatsApp, Llamadas, Cámara, Fotos.
The list is static in Phase 1 — Fran preloads these four. Tapping a tile opens the
app. An uninstalled app shows a greyed disabled state and a toast.

This SF also adds `QUERY_ALL_PACKAGES` to the manifest (needed here and by the
`open_app` handler in Phase 4) and activates the Coil dep (reserved since US-001).

---

## 2. Acceptance criteria

- [ ] `FavoriteApp` domain model in `domain/model/`:
  ```kotlin
  data class FavoriteApp(
      val id: String,
      val labelResId: Int,
      val resolvedPackage: String?,
      val icon: Drawable?,
  )
  ```
- [ ] `FavoriteAppsRepository` interface in `domain/repository/`:
  ```kotlin
  interface FavoriteAppsRepository {
      fun observeFavorites(): Flow<List<FavoriteApp>>
  }
  ```
- [ ] `StaticFavoriteAppsRepositoryImpl` in `data/apps/`:
  - Resolves four apps: WhatsApp (`com.whatsapp`), Llamadas (via `Intent.ACTION_DIAL`
    + `com.android.dialer` fallback), Cámara (`Intent.ACTION_IMAGE_CAPTURE` +
    `com.android.camera` fallback), Fotos (`Intent.ACTION_PICK` image MIME +
    `com.miui.gallery` / `com.google.android.apps.photos` fallback).
  - Queries `packageManager.getApplicationIcon(packageName)` for each resolved package;
    returns `null` for icon when package is not installed.
  - Re-emits on `ProcessLifecycleOwner ON_RESUME` (apps can be installed/removed).
  - Uses `Dispatchers.IO` for PM queries (injected via `@IoDispatcher`).
- [ ] `AppsModule` Hilt interface module in `di/`:
  ```kotlin
  @Module @InstallIn(SingletonComponent::class) interface AppsModule {
      @Binds @Singleton fun bindFavoriteAppsRepository(…): FavoriteAppsRepository
  }
  ```
- [ ] `LauncherModule` extended to bind `InstalledAppsRepository` (SF-1.5 —
  or a new `AppsModule` covers both).
- [ ] `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />`
  added to `AndroidManifest.xml` with explanation comment.
- [ ] `LauncherUiState` gains `val favorites: List<FavoriteApp>` (default empty list).
  ViewModel combines three flows: detector + clock + favoritesRepo.
- [ ] `AppTile` composable in `presentation/launcher/AppTile.kt`:
  - Shows app icon (Drawable → `.toBitmap().asImageBitmap()`) + Spanish label.
  - ≥ 96 dp height (`Dimens.MinTapTarget`).
  - Haptic on press (`HapticFeedbackType.LongPress`).
  - `contentDescription` = label string.
  - Disabled/greyed when `app.resolvedPackage == null`.
- [ ] `AppTileGrid` composable in `presentation/launcher/AppTileGrid.kt`:
  - 2×2 `Column` of `Row`s holding `AppTile`s.
  - `CurroSpacing.l` gap between tiles.
  - Fills max width.
- [ ] Tile tap: `onTileTapped(app)` → ViewModel `onEvent(AppTileTapped(packageName))` →
  `LauncherSideEffect.LaunchApp(packageName)` → screen calls
  `context.packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }`.
  When `resolvedPackage == null`: tap emits `ShowToast(R.string.copy_app_not_installed)`.
- [ ] `AppTileGrid` placed in `LauncherPlaceholderContent` BELOW `MicButton`.
- [ ] 4 new strings in `strings.xml`:
  - `copy_app_label_whatsapp = "WhatsApp"`
  - `copy_app_label_calls = "Llamadas"`
  - `copy_app_label_camera = "Cámara"`
  - `copy_app_label_photos = "Fotos"`
  - `copy_app_not_installed = "Esa app no está instalada"`
- [ ] Coil dependency activated: `implementation(libs.coil.compose)` added in
  `app/build.gradle.kts`.
- [ ] Unit tests for `StaticFavoriteAppsRepositoryImpl` (Robolectric / fake PM):
  - Installed package → `resolvedPackage` is non-null.
  - Uninstalled package → `resolvedPackage` is null, `icon` is null.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all green.

---

## 3. Scope — explicit non-deliveries

- No dynamic favourite management (editing the four tiles) — that is Phase 8 (SF-8.x).
- No Coil `AsyncImage` for icons — Drawable → Bitmap → `asImageBitmap()` is dep-free
  and sufficient; Coil is activated here for contact photos in Phase 4 (SF-4.10).
- No usage tracking or reordering.

---

## 4. Package resolution strategy

| Logical name | Primary intent resolution | Fallback package |
|---|---|---|
| WhatsApp | `com.whatsapp` direct | — (null if not installed) |
| Llamadas | `Intent(ACTION_DIAL)` → `resolveActivity(MATCH_DEFAULT_ONLY)` | `com.android.dialer` |
| Cámara | `Intent(ACTION_IMAGE_CAPTURE)` → `resolveActivity(...)` | `com.android.camera` |
| Fotos | `Intent(ACTION_PICK).setType("image/*")` → `resolveActivity(...)` | `com.miui.gallery` |

For HyperOS the dialer is `com.miui.dialer`, camera is `com.android.camera` or
`com.miui.camera`. Dynamic resolution handles OEM variants; the fallback is only
used if `resolveActivity` returns null.

---

## 5. Implementation notes

### 5.1 Drawable → ImageBitmap conversion

```kotlin
val imageBitmap: ImageBitmap? = remember(app.icon) {
    app.icon?.toBitmap()?.asImageBitmap()
}
```

`Drawable.toBitmap()` is a `core-ktx` extension. No Accompanist dep needed.

### 5.2 ProcessLifecycleOwner re-emission

```kotlin
class StaticFavoriteAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FavoriteAppsRepository {
    override fun observeFavorites(): Flow<List<FavoriteApp>> =
        ProcessLifecycleOwner.get().lifecycle
            .asFlow()                                 // Lifecycle.Event stream
            .filter { it == Lifecycle.Event.ON_RESUME }
            .onStart { emit(Lifecycle.Event.ON_RESUME) } // emit once at subscription
            .map { loadFavorites() }
            .flowOn(ioDispatcher)
            .distinctUntilChanged()

    private fun loadFavorites(): List<FavoriteApp> { … }
}
```

---

## 6. Strings delta

| ID | Value | Notes |
|----|-------|-------|
| `copy_app_label_whatsapp` | `"WhatsApp"` | Phase-1 static label |
| `copy_app_label_calls` | `"Llamadas"` | Phase-1 static label |
| `copy_app_label_camera` | `"Cámara"` | Phase-1 static label |
| `copy_app_label_photos` | `"Fotos"` | Phase-1 static label |
| `copy_app_not_installed` | `"Esa app no está instalada"` | Toast for greyed tile |

---

## 7. Files changed

**New:**
- `app/src/main/java/com/curro/app/domain/model/FavoriteApp.kt`
- `app/src/main/java/com/curro/app/domain/repository/FavoriteAppsRepository.kt`
- `app/src/main/java/com/curro/app/data/apps/StaticFavoriteAppsRepositoryImpl.kt`
- `app/src/main/java/com/curro/app/presentation/launcher/AppTile.kt`
- `app/src/main/java/com/curro/app/presentation/launcher/AppTileGrid.kt`
- `app/src/main/java/com/curro/app/di/AppsModule.kt`
- `app/src/test/java/com/curro/app/data/apps/StaticFavoriteAppsRepositoryImplTest.kt`

**Modified:**
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherViewModel.kt` — add favorites
- `app/src/main/java/com/curro/app/presentation/launcher/LauncherPlaceholderScreen.kt` — add AppTileGrid
- `app/src/main/AndroidManifest.xml` — `QUERY_ALL_PACKAGES`
- `app/src/main/res/values/strings.xml` — 5 new strings
- `app/build.gradle.kts` — activate coil dependency
