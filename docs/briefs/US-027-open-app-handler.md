# US-027 — SF-4.3 · `open_app` handler + `AppLauncher` + colloquial alias map

> **Spec trace:** spec §5 (catalog entry `open_app`), spec §6 flow 1's vibe
> ("vale, abriendo …" / no confirmation needed).
> **Master-plan:** SF-4.3.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-025 (`FunctionHandler`), US-013 (`InstalledAppsRepository`).
> **Size:** M.
> **Skills:** `function-catalog`, `platform-integrations`, `compose-patterns`, `testing-patterns`, `git-workflow`, `brand-design`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `open_app` handler — colloquial Spanish → installed app |
| **US ID** | US-027 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-developer |

---

## 1. Summary

Resolve a colloquial Spanish app name to an installed package and launch it.
The handler chains three resolution strategies — **alias map**, **substring
contains**, **Levenshtein fuzzy match** — over the `InstalledAppsRepository`
that already feeds the "Más apps" screen (US-013). Spec §5 examples are the
contract: `"abre la cámara"`, `"abre WhatsApp"`, `"ponme las fotos"`, `"abre
el correo"`. Multiple matches return `Failed(AmbiguousApp)`; no match
returns `Failed(AppNotFound)`. Each terminates in a plain Spanish line.

Why this matters for *this* user: HyperOS gives the same launcher icon to
"la cámara" (built-in) and any sideloaded camera app — and the user can't
distinguish them visually. He says the colloquial name; Curro picks the
right install.

---

## 2. Scope

**In scope:**

- `data/apps/AppLauncher.kt` — interface + `IntentAppLauncher` impl.
- `data/apps/ColloquialAppAliases.kt` — curated alias map (pinned verbatim
  below).
- `data/apps/StringNormalization.kt` — accent-strip + Levenshtein.
- `handler/OpenAppHandler.kt`.
- `di/AppsModule.kt` — already exists from US-013; this SF adds one `@Binds`
  for `AppLauncher`.
- `HandlerModule.kt` — append the `@Binds @IntoMap @StringKey("open_app")`
  line.
- New `CurroError` variants: `AmbiguousApp(List<LaunchableApp>)`,
  `AppNotFound(String)`.
- New `strings.xml` entries: `copy_app_not_found_named`, `copy_app_ambiguous`.
  Reuse existing `copy_app_opening`, `copy_app_not_found`.
- ≥ 15 JVM tests on the handler; ≥ 10 on `StringNormalization`.

**Out of scope:**

- Phase-6's real ambiguity-picker UI for the "multiple matches" case (the
  handler emits the typed error; Phase 5/6 wires the overlay).
- Learning which app the user picks on ambiguity (Phase 7 alias-learning
  subsystem covers this for contacts; app-aliases are not Phase-1 scope).
- Internationalisation beyond Spanish.

---

## 3. User Flows

### Flow 1: "abre WhatsApp" — direct alias hit

1. User presses mic → "abre WhatsApp" → STT.
2. FunctionGemma → `{action: "open_app", params: {app_name: "WhatsApp"}, confidence: 0.96}`.
3. Handler normalises `"whatsapp"`; alias map → `["com.whatsapp"]`;
   `InstalledAppsRepository` confirms it's installed.
4. `AppLauncher.launch("com.whatsapp")` → fires the launch intent.
5. Curro speaks `"Abriendo WhatsApp."`.
6. State → `Idle`.

### Flow 2: "abre la cámara" — multi-candidate alias, first installed wins

1. Handler normalises `"la cámara"`; alias map → `["com.android.camera",
   "com.android.camera2", "com.miui.camera"]`.
2. `InstalledAppsRepository.observeAllLaunchable().first()` is checked
   sequentially; on the Redmi 15 `"com.miui.camera"` matches.
3. `AppLauncher.launch("com.miui.camera")` → Curro speaks `"Abriendo
   Cámara."` (the system label from `InstalledAppsRepository`).

### Flow 3: "abre calc" — fuzzy match

1. Normalised `"calc"` is not in the alias map.
2. Substring `contains` against installed labels → none.
3. Levenshtein: `"calc"` (length 4) — threshold 3 — matches `"calculadora"`
   (distance 7? — no; we measure against a TRUNCATED-or-substring window;
   pin: when the query is shorter than the label, the match is `label
   startsWith query` first, then Levenshtein on full strings).
4. One candidate → launch.
5. **Pin:** the test asserts `"calc"` opens the Calculadora app via the
   `startsWith` step, NOT via Levenshtein (the latter would not fit within
   threshold ≤ 3).

### Flow 4: "abre fooba" — no match

1. Normalised `"fooba"` doesn't hit any alias, substring, or fuzzy match.
2. Handler → `Failed("No tengo ninguna app que se llame fooba.",
   AppNotFound("fooba"))`.
3. Curro speaks the line.

### Flow 5: "abre mensajes" — multiple installed candidates

1. The user has both `"com.google.android.apps.messaging"` (label "Mensajes")
   and `"com.android.messaging"` (label "Mensajes") installed (rare but
   pinned in test).
2. **Alias-path bias** (decision pinned): the alias map's ordered candidate
   list resolves to the **first** installed package → unambiguous. The
   ambiguous path is reserved for **fuzzy-match** ties, NOT alias-map ties.
3. → `Spoken("Abriendo Mensajes.")`.

### Flow 6: Fuzzy match yields multiple

1. Hypothetical: two installed apps both have labels at Levenshtein distance
   ≤ 3 from the query.
2. Handler → `Failed(copy_app_ambiguous, AmbiguousApp(candidates))`.
3. Curro speaks `"Tengo varias apps que se llaman así, prueba con el nombre
   exacto."`.

---

## 4. Function-catalog Impact

**No catalog change** — `open_app` already exists. The handler binds to
`"open_app"` in `HandlerModule`.

---

## 5. FSM States Touched

Provisional FSM — `Processing → Speaking → Idle` (success) or
`Processing → Speaking → Idle` (failure with the spoken error line). No new
state. **`needs_confirmation: NO`** — open is reversible, no confirmation.

---

## 6. Android System Integrations & Permissions

| Integration | Why | Notes |
|---|---|---|
| `PackageManager.getLaunchIntentForPackage(pkg)` | Resolve the LAUNCHER activity. | Used inside `AppLauncher`. |
| `Context.startActivity(intent.addFlags(NEW_TASK))` | Fire the launch. | `NEW_TASK` required when starting from a non-Activity context. |
| `InstalledAppsRepository` (US-013) | Enumerate launchable apps for fuzzy match and "installed?" checks. | Reused — no new query path. |

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `QUERY_ALL_PACKAGES` | Already declared by SF-1.4 — needed to read every installed launcher. | Install time (manifest). | N/A — install-time. |

**No new permissions.** Manifest unchanged.

---

## 7. On-device-model Impact

No prompt or model change. FunctionGemma already emits `open_app` JSON.
This SF only consumes it.

---

## 8. Android Specification

### 8.1 Files added / changed

```
app/src/main/java/com/curro/app/
├── data/apps/
│   ├── AppLauncher.kt              // interface + IntentAppLauncher
│   ├── ColloquialAppAliases.kt     // verbatim curated map
│   └── StringNormalization.kt      // accent-strip + Levenshtein
├── handler/
│   └── OpenAppHandler.kt
├── di/
│   ├── AppsModule.kt               // already exists from US-013; add 1 @Binds
│   └── HandlerModule.kt            // append @Binds @IntoMap @StringKey("open_app")
└── domain/model/CurroError.kt      // add AmbiguousApp, AppNotFound variants
```

### 8.2 `AppLauncher.kt`

```kotlin
package com.curro.app.data.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Thin testable wrapper around `getLaunchIntentForPackage` + `startActivity`. */
interface AppLauncher {
    /** Returns true if the launch fired; false if no LAUNCHER activity / no rights. */
    fun launch(packageName: String): Boolean
}

class IntentAppLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppLauncher {
        override fun launch(packageName: String): Boolean {
            val pm = context.packageManager
            val intent =
                pm.getLaunchIntentForPackage(packageName)
                    ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }
```

### 8.3 `ColloquialAppAliases.kt` — verbatim curated map

The map is the **single source** for "which colloquial name maps to which
package list". Order inside each value list matters: the first installed
package wins.

```kotlin
package com.curro.app.data.apps

/**
 * Spanish colloquial app names → ordered candidate packages.
 *
 * Resolution order in [OpenAppHandler]:
 *   1. Exact alias hit (case + accent-insensitive on the key, raw on the value).
 *   2. Substring `contains` against installed labels.
 *   3. Levenshtein ≤ 3 (only when the query length ≥ 4).
 *
 * Adding an entry: keep the key lowercase, accents preserved; pad the value
 * list with realistic candidates (Xiaomi, Samsung, Google) so HyperOS variants
 * also resolve.
 */
object ColloquialAppAliases {
    val byColloquialName: Map<String, List<String>> =
        mapOf(
            "whatsapp" to listOf("com.whatsapp"),
            "wasap" to listOf("com.whatsapp"),
            "guasap" to listOf("com.whatsapp"),
            "guasá" to listOf("com.whatsapp"),
            "la cámara" to listOf("com.android.camera", "com.android.camera2", "com.miui.camera"),
            "cámara" to listOf("com.android.camera", "com.android.camera2", "com.miui.camera"),
            "las fotos" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
            "fotos" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
            "la galería" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
            "galería" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
            "el correo" to listOf("com.google.android.gm", "com.samsung.android.email.provider"),
            "correo" to listOf("com.google.android.gm", "com.samsung.android.email.provider"),
            "gmail" to listOf("com.google.android.gm"),
            "el teléfono" to listOf("com.google.android.dialer", "com.android.dialer", "com.android.contacts"),
            "teléfono" to listOf("com.google.android.dialer", "com.android.dialer"),
            "los contactos" to listOf("com.android.contacts", "com.google.android.contacts"),
            "contactos" to listOf("com.android.contacts", "com.google.android.contacts"),
            "los mensajes" to listOf("com.google.android.apps.messaging", "com.android.messaging"),
            "mensajes" to listOf("com.google.android.apps.messaging", "com.android.messaging"),
            "ajustes" to listOf("com.android.settings"),
            "los ajustes" to listOf("com.android.settings"),
            "configuración" to listOf("com.android.settings"),
            "youtube" to listOf("com.google.android.youtube"),
            "calculadora" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "la calculadora" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "el reloj" to listOf("com.android.deskclock", "com.google.android.deskclock"),
            "reloj" to listOf("com.android.deskclock", "com.google.android.deskclock"),
            "el navegador" to listOf("com.android.chrome", "org.mozilla.firefox"),
            "chrome" to listOf("com.android.chrome"),
        )
}
```

### 8.4 `StringNormalization.kt`

```kotlin
package com.curro.app.data.apps

import java.text.Normalizer
import java.util.Locale

/** NFD-strip combining diacritics + recompose. `"cámara"` → `"camara"`. */
internal fun String.normalizeAccents(): String {
    val nfd = Normalizer.normalize(this, Normalizer.Form.NFD)
    val stripped = nfd.replace(Regex("\\p{Mn}+"), "")
    return Normalizer.normalize(stripped, Normalizer.Form.NFC)
}

/** Lowercase (Spanish locale) + accent-strip. */
internal fun String.curroNormalize(): String =
    this.lowercase(Locale("es")).normalizeAccents()

/**
 * Classic 2-row Levenshtein distance. O(n*m) time, O(min(n, m)) space.
 * Returns the edit distance in chars. Both inputs assumed already-normalised
 * (lowercase, accent-stripped) by the caller.
 */
internal fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val (short, long) = if (a.length <= b.length) a to b else b to a
    var prev = IntArray(short.length + 1) { it }
    var curr = IntArray(short.length + 1)
    for (j in 1..long.length) {
        curr[0] = j
        for (i in 1..short.length) {
            val cost = if (short[i - 1] == long[j - 1]) 0 else 1
            curr[i] = minOf(
                prev[i] + 1,         // deletion
                curr[i - 1] + 1,     // insertion
                prev[i - 1] + cost,  // substitution
            )
        }
        val swap = prev; prev = curr; curr = swap
    }
    return prev[short.length]
}
```

### 8.5 `OpenAppHandler.kt`

```kotlin
package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.apps.AppLauncher
import com.curro.app.data.apps.ColloquialAppAliases
import com.curro.app.data.apps.curroNormalize
import com.curro.app.data.apps.levenshtein
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.LaunchableApp
import com.curro.app.domain.repository.InstalledAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class OpenAppHandler
    @Inject
    constructor(
        private val installedApps: InstalledAppsRepository,
        private val launcher: AppLauncher,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "open_app"

        @Suppress("ReturnCount")
        override suspend fun handle(call: FunctionCall): HandlerResult {
            val rawInput = (call.params["app_name"] as? String).orEmpty().trim()
            if (rawInput.isEmpty()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_app_not_found),
                    CurroError.AppNotFound(""),
                )
            }
            val query = rawInput.curroNormalize()
            val installed = installedApps.observeAllLaunchable().first()

            // 1. Alias hit
            aliasFirstInstalled(query, installed)?.let { app ->
                return launchOrFail(app)
            }

            // 2. Substring contains
            val containsHits =
                installed.filter { query in it.label.curroNormalize() }
            when (containsHits.size) {
                0 -> Unit
                1 -> return launchOrFail(containsHits.first())
                else -> {
                    // Multiple substring hits — fall through to Levenshtein to try to
                    // narrow; if Levenshtein also can't, return ambiguous.
                    return ambiguousResult(containsHits)
                }
            }

            // 3. Levenshtein ≤ 3, only when query length ≥ 4 (avoids meaningless
            //    distances on 2-char queries).
            if (query.length >= LEV_MIN_QUERY_LEN) {
                val fuzzy =
                    installed
                        .map { it to levenshtein(query, it.label.curroNormalize()) }
                        .filter { (_, d) -> d <= LEV_THRESHOLD }
                        .sortedBy { it.second }
                if (fuzzy.isNotEmpty()) {
                    val topDistance = fuzzy.first().second
                    val tied = fuzzy.filter { it.second == topDistance }.map { it.first }
                    return if (tied.size == 1) launchOrFail(tied.first()) else ambiguousResult(tied)
                }
            }

            // 4. No match.
            return HandlerResult.Failed(
                context.getString(R.string.copy_app_not_found_named, rawInput),
                CurroError.AppNotFound(rawInput),
            )
        }

        private fun aliasFirstInstalled(
            normalisedQuery: String,
            installed: List<LaunchableApp>,
        ): LaunchableApp? {
            val candidates =
                ColloquialAppAliases.byColloquialName[normalisedQuery]
                    ?: return null
            val installedPackages = installed.associateBy { it.packageName }
            for (pkg in candidates) {
                installedPackages[pkg]?.let { return it }
            }
            return null
        }

        private fun launchOrFail(app: LaunchableApp): HandlerResult {
            val ok = launcher.launch(app.packageName)
            return if (ok) {
                HandlerResult.Spoken(context.getString(R.string.copy_app_opening, app.label))
            } else {
                HandlerResult.Failed(
                    context.getString(R.string.copy_app_not_found_named, app.label),
                    CurroError.AppNotFound(app.label),
                )
            }
        }

        private fun ambiguousResult(candidates: List<LaunchableApp>): HandlerResult =
            HandlerResult.Failed(
                context.getString(R.string.copy_app_ambiguous),
                CurroError.AmbiguousApp(candidates),
            )

        private companion object {
            const val LEV_THRESHOLD = 3
            const val LEV_MIN_QUERY_LEN = 4
        }
    }
```

### 8.6 `CurroError` additions

Append to `domain/model/CurroError.kt`:

```kotlin
// ── Open-app handler (US-027 / SF-4.3) ────────────────────────────────────

/** Multiple installed apps matched the query and the handler couldn't pick one. */
data class AmbiguousApp(val matches: List<com.curro.app.domain.model.LaunchableApp>) : CurroError()

/** No installed app matched the query. [query] is the raw input for the log. */
data class AppNotFound(val query: String) : CurroError()
```

### 8.7 `strings.xml` — adds / reuses

Reuse without change:

- `copy_app_opening` (`"Abriendo %1$s."`).
- `copy_app_not_found` (`"No tengo ninguna app que se llame así."`) — used
  for the empty-query case.

New:

```xml
<!-- US-027 (SF-4.3) — named not-found case ("No tengo ninguna app que se llame foo."). -->
<string name="copy_app_not_found_named">No tengo ninguna app que se llame %1$s.</string>

<!-- US-027 (SF-4.3) — multiple fuzzy matches; Phase 5/6 may replace with a picker. -->
<string name="copy_app_ambiguous">Tengo varias apps que se llaman así, prueba con el nombre exacto.</string>
```

### 8.8 Hilt — `di/AppsModule.kt`

Append (preserving existing bindings):

```kotlin
@Binds
@Singleton
abstract fun bindAppLauncher(impl: IntentAppLauncher): AppLauncher
```

### 8.9 `HandlerModule.kt` — append

```kotlin
@Binds
@IntoMap
@StringKey("open_app")
abstract fun bindOpenAppHandler(impl: OpenAppHandler): FunctionHandler
```

---

## 9. Acceptance Criteria

- [ ] All five new files exist at the documented paths.
- [ ] `CurroError.AmbiguousApp` and `CurroError.AppNotFound` added.
- [ ] `strings.xml` gains `copy_app_not_found_named`, `copy_app_ambiguous`.
- [ ] `HandlerModule` gains the `@Binds @IntoMap @StringKey("open_app")` line.
- [ ] `AppsModule` gains `@Binds @Singleton bindAppLauncher(IntentAppLauncher): AppLauncher`.
- [ ] On the Redmi 15:
  - `"abre WhatsApp"` → WhatsApp opens; Curro speaks `"Abriendo WhatsApp."`.
  - `"abre la cámara"` → the resolved Cámara opens.
  - `"ponme las fotos"` → Galería/Fotos opens.
  - `"abre los ajustes"` → Settings opens.
  - `"abre calc"` → Calculadora opens via the substring `startsWith`-style
    contains step.
  - `"abre fooba"` → Curro speaks `"No tengo ninguna app que se llame fooba."`.
- [ ] Empty `app_name` (defensive — validator should reject) returns
      `Failed(AppNotFound(""))` with `copy_app_not_found`.
- [ ] No new permissions; no manifest changes; no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green.

---

## 10. Senior-UX & Copy

| String ID | Spanish | Provenance |
|---|---|---|
| `copy_app_opening` (existing) | "Abriendo %1$s." | Reused. |
| `copy_app_not_found` (existing) | "No tengo ninguna app que se llame así." | Reused for the empty-query path. |
| `copy_app_not_found_named` (NEW) | "No tengo ninguna app que se llame %1$s." | Named variant — repeats the user's word back so they know what Curro understood. |
| `copy_app_ambiguous` (NEW) | "Tengo varias apps que se llaman así, prueba con el nombre exacto." | Honest about the limitation; offers a concrete alternative. Phase 6 may replace with a picker. |

Voice: Curro never apologises, never silences — even the failure mode is a
single confident sentence + an alternative.

---

## 11. Design Notes

No new visual surface; spec §11 is satisfied by the existing speaking
overlay.

---

## 12. Performance Considerations

- `installedApps.observeAllLaunchable().first()` reads from the
  `Flow<List<LaunchableApp>>` US-013 already maintains; the upstream PM query
  is `flowOn(ioDispatcher)` so the suspend is cheap on the main path.
- Levenshtein is `O(n * m)` per pair; with ~150 installed apps × 10-char
  labels × 10-char query, ≈ 15 000 ops — sub-millisecond on the Redmi 15.
- Normalisation allocates per-label per-call; acceptable at handler
  cadence. Memo-table optimisation NOT pinned for Phase 4 — measure first.

---

## 13. Testing Requirements

**`StringNormalizationTest.kt`** — pure JVM:

- `"cámara".curroNormalize() == "camara"`.
- `"WhatsApp".curroNormalize() == "whatsapp"`.
- `"Niño".curroNormalize() == "nino"`.
- `levenshtein("calc", "calculadora") == 7` (sanity — fails threshold, not
  via fuzzy in handler).
- `levenshtein("guasap", "whatsapp") <= 3` ? (verify; if not, alias map
  carries `"guasap"` directly — pinned).
- `levenshtein("", "abc") == 3`.
- `levenshtein("abc", "") == 3`.
- `levenshtein("abc", "abc") == 0`.
- `levenshtein("abc", "abd") == 1`.
- `levenshtein("abc", "xyz") == 3`.

**`OpenAppHandlerTest.kt`** — pure JVM with fakes:

- `FakeInstalledAppsRepository(apps: List<LaunchableApp>)` — emits a single
  list once.
- `FakeAppLauncher` — captures the last `packageName` passed, returns a
  configurable boolean.
- Robolectric `Context` for `getString`. (Decision pinned: Robolectric — it
  is already required by `LauncherViewModelTest`.)

Cases (≥ 15):

1. Exact alias `"WhatsApp"` → launches `com.whatsapp`; speaks
   `"Abriendo WhatsApp."`.
2. Accent-stripping alias `"camara"` (no accent) → opens cámara.
3. Multi-candidate alias `"la cámara"` with only `com.miui.camera` installed
   → opens that one.
4. Multi-candidate alias where NO candidate is installed → falls through to
   substring/fuzzy; ends in `AppNotFound`.
5. Substring contains: query `"galería"` matches label `"Galería"`.
6. Substring contains with one hit → launches.
7. Substring contains with multiple hits → `AmbiguousApp`.
8. Fuzzy match: `"chrme"` (Lev 1 to "Chrome") → opens Chrome.
9. Fuzzy match threshold boundary: `"abcdef"` vs label `"abcdze"` (Lev 2) →
   matches; `"abcdef"` vs `"xyzdef"` (Lev 3) → matches; `"abcdef"` vs
   `"qrstuv"` (Lev 6) → no match.
10. Empty `app_name` → `AppNotFound("")`, `copy_app_not_found`.
11. No installed apps at all → `AppNotFound`.
12. `AppLauncher.launch` returns false (uninstalled mid-flow) →
    `AppNotFound`.
13. Multiple substring hits where Levenshtein narrows to one — returns the
    Levenshtein winner.
14. Multiple substring hits where Levenshtein also ties — `AmbiguousApp` with
    all tied candidates.
15. Locale-sensitive lower-case: `"İSTANBUL"` lowercased with `Locale("es")`
    behaves predictably (sanity — Turkish dotted-I corner case).

**On-device verification** on the Redmi 15:

- All AC bullets above. Capture the colloquial-name → package resolution
  for the brief's "decisions taken" annotation if any HyperOS package name
  differs from the map.

---

## 14. Implementation Notes — Order of Operations

1. Add the two `CurroError` variants.
2. Create `data/apps/StringNormalization.kt` + its tests (pure-Kotlin).
3. Create `data/apps/ColloquialAppAliases.kt` (verbatim from §8.3).
4. Create `data/apps/AppLauncher.kt` (interface + impl).
5. Append the `AppLauncher` `@Binds` in `AppsModule`.
6. Add the two new `strings.xml` entries.
7. Create `handler/OpenAppHandler.kt`.
8. Append the handler `@Binds @IntoMap @StringKey("open_app")` in
   `HandlerModule`.
9. Write `OpenAppHandlerTest`.
10. `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
11. Smoke-test on the Redmi 15.
12. Commit as `feat: add open_app handler + colloquial alias map (US-027 / SF-4.3)`.

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
