# US-019 — SF-3.1 · Model asset delivery: side-load via `adb push` (path-driven)

> **Spec trace:** spec §4.3 (decision layer — FunctionGemma on-device, kept warm),
> §14 "Riesgos identificados" (model-delivery decision is the third risk).
> **Master-plan:** SF-3.1
> **Phase:** 3 — FunctionGemma decision layer
> **Depends on:** US-001 (Gradle skeleton — the `local.properties` plumbing exists from US-008)
> **Size:** M

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | Model asset delivery — side-load via `adb push` (path-driven) |
| **US ID** | US-019 |
| **Phase** | 3 |
| **Status** | In Progress |
| **Created** | 2026-05-15 |
| **Modified** | 2026-05-15 |
| **PM Owner** | android-product-analyst |
| **Architect** | ondevice-ai-engineer |

---

## 1. Summary

For the prototype, the FunctionGemma 270M weights (~288 MB, `.task` format) are
side-loaded onto the device via `adb push function_gemma_270m.task
/data/local/tmp/curro-models/`. The model path is exposed at compile time via
`BuildConfig.MODEL_BASE_PATH` (default `/data/local/tmp/curro-models`) and at
runtime through a single `ModelFiles` object that hides every other call site
from the file system. `assembleDebug` on CI must keep working when the weights
are absent — `ModelFiles.isFunctionGemmaAvailable()` returns `false`, the engine
(US-020) reports `CurroError.ModelCold`, and the smoke loop (US-024) speaks
`copy_models_not_ready` ("Aún estoy preparando los modelos, dame un segundo.")
instead of crashing.

Why this matters for *this* user: he never sees this — but Fran needs to be able
to iterate on real-device latency without shipping 288 MB through CI on every
push, and he needs a clean swap from side-load to bundled delivery later without
touching engine code. The `ModelFiles` abstraction is the seam that makes the
future swap painless.

---

## 2. Scope

**In scope:**

- `local.properties` extension: optional `CURRO_MODEL_BASE_PATH` key (read by
  `app/build.gradle.kts`, fallback `/data/local/tmp/curro-models`).
- `BuildConfig.MODEL_BASE_PATH` field emitted in both `debug` and `release` build
  types.
- `data/ml/ModelFiles.kt` — the single source of truth for "where is the
  FunctionGemma `.task` on disk" and "is it readable".
- `.gitignore` defensive entry `*.task`.
- A short `docs/MODELS.md` documenting the side-load procedure, where to obtain
  the weights, and the HyperOS battery-whitelist setup steps (so the dev setting
  up the device has one canonical reference).
- A single new COPY entry `copy_models_not_ready` — added to `strings.xml` so
  US-024's smoke loop has the resource to reference.
- `docs/curro-spec-v1.0.md` §14 "Riesgos identificados" — the model-delivery
  paragraph is edited in place (no version bump; this is a documentation refresh,
  not a spec contract change).
- `CLAUDE.md` "On-device models" section — the model-delivery decision is
  recorded.

**Out of scope:**

- The MediaPipe wiring (`com.google.mediapipe:tasks-genai` dependency) — that's
  US-020. The build still compiles without MediaPipe after this SF.
- The `FunctionCallEngine` / `FunctionGemmaEngine` — US-020.
- Any progress UI, first-run downloader, or asset-pack setup — explicitly **not
  this SF**. Side-load is the prototype's chosen path; bundled delivery is a
  future SF.
- Gemma 3n delivery — Phase 9.
- Any change to the spec contract (§4.3, §5, §6 unchanged); the spec edit is
  only in §14's risks-identified paragraph.

---

## 3. User Flows

This SF is invisible to the end user. The only relevant flows are
developer-facing:

### Flow 1 — Fresh clone, no model file (CI default)

1. Developer clones the repo, runs `./gradlew assembleDebug`.
2. `local.properties` does not exist or does not contain `CURRO_MODEL_BASE_PATH`.
3. `BuildConfig.MODEL_BASE_PATH` is the default: `/data/local/tmp/curro-models`.
4. Build succeeds; APK installs; app launches; `ModelFiles.isFunctionGemmaAvailable()` returns `false`.
5. (Later, when US-024 lands) pressing the mic and saying "qué hora es" → Curro
   speaks `copy_models_not_ready` ("Aún estoy preparando los modelos…"). No
   crash.

### Flow 2 — Side-loading the weights on the Redmi 15

1. Developer obtains `function_gemma_270m.task` from the documented source (see
   `docs/MODELS.md`).
2. With the device connected via USB and `adb` available, runs:
   ```
   adb shell mkdir -p /data/local/tmp/curro-models/
   adb push function_gemma_270m.task /data/local/tmp/curro-models/
   adb shell ls -l /data/local/tmp/curro-models/
   ```
3. The file is now readable by the app at the default path.
4. Next press of the mic (after US-024 lands) produces a real
   `FunctionCall` JSON.

### Flow 3 — Custom path via `local.properties`

1. Developer wants to test from app-private storage (Phase-9 dry run).
2. Adds `CURRO_MODEL_BASE_PATH=/data/data/com.curro.app/files/models` to
   `local.properties`.
3. Rebuilds: `BuildConfig.MODEL_BASE_PATH` picks up the new value; `ModelFiles`
   resolves there.
4. Side-loads to the new path; the engine works.

---

## 4. Function-catalog Impact

**No catalog change.** This SF is infrastructure for the engine; it doesn't add
or modify any catalog function.

---

## 5. FSM States Touched

**None.** No new state. (The downstream side effect — US-024's smoke loop
producing `copy_models_not_ready` on a cold engine — is in US-024, not here.)

---

## 6. Android System Integrations & Permissions

**No new system integrations.** `ModelFiles` only reads a `File` from a path
string; no `Context`, no `PackageManager`, no permissions.

**No new permissions.** The `adb push` target `/data/local/tmp/` is
world-readable to any app on a debug build of Android, so reading the `.task`
file from there requires no permission. (This is part of why side-load is the
right prototype path: it sidesteps the storage-permissions story entirely.)

---

## 7. On-device-model Impact

This SF **is** the model-impact story. It pins **how** FunctionGemma's weights
get onto the device for the prototype, but does not yet load them. The
`isFunctionGemmaAvailable()` boolean is the contract every later SF reads to
decide whether to attempt inference.

**Latency budget:** N/A — no inference runs from this SF. (US-020's
`warmUp()` is the first inference, US-024 is where < 500 ms is measured.)

---

## 8. Android Specification

### 8.1 Files added

- `app/src/main/java/com/curro/app/data/ml/ModelFiles.kt` — new.
- `docs/MODELS.md` — new.
- `app/src/test/java/com/curro/app/data/ml/ModelFilesTest.kt` — new.

### 8.2 Files modified

- `app/build.gradle.kts` — `Properties` block extended to read
  `CURRO_MODEL_BASE_PATH`; `buildConfigField("String", "MODEL_BASE_PATH", "…")`
  added to **both** `debug` and `release` `buildTypes`.
- `app/src/main/res/values/strings.xml` — add `copy_models_not_ready`.
- `.gitignore` — add `*.task` line under the model-files section (or a fresh
  "# On-device model files (side-loaded; never in git)" comment block).
- `docs/curro-spec-v1.0.md` — §14 "Riesgos identificados" edited in place
  (paragraph below); **no version bump**.
- `CLAUDE.md` — "On-device models" section extended with the side-load
  paragraph.

### 8.3 The `ModelFiles` object — exact shape

```kotlin
package com.curro.app.data.ml

import com.curro.app.BuildConfig
import java.io.File

/**
 * Single source of truth for on-device model file paths.
 *
 * Phase 3 (US-019): the FunctionGemma 270M `.task` is side-loaded via
 *   `adb push function_gemma_270m.task /data/local/tmp/curro-models/`
 * and lives at `BuildConfig.MODEL_BASE_PATH/function_gemma_270m.task`.
 *
 * A future SF (post-prototype) will introduce bundled / Play Asset Delivery
 * for release without changing this abstraction's callers — only this object
 * needs to know how the file is delivered.
 */
object ModelFiles {

    /** Absolute path to the FunctionGemma 270M weights. May not exist. */
    fun functionGemma(): File =
        File(BuildConfig.MODEL_BASE_PATH, FUNCTION_GEMMA_FILENAME)

    /** True iff the weights exist and are readable by this process. */
    fun isFunctionGemmaAvailable(): Boolean =
        functionGemma().let { it.exists() && it.canRead() }

    private const val FUNCTION_GEMMA_FILENAME = "function_gemma_270m.task"
}
```

### 8.4 `app/build.gradle.kts` diff

The existing `Properties` block already reads `local.properties` (US-008). Add:

```kotlin
// US-019 (SF-3.1) — model asset delivery: side-load via adb push.
// Default path is /data/local/tmp/curro-models; configurable per-machine via
// local.properties (CURRO_MODEL_BASE_PATH) so a developer can test app-private
// storage without rewriting code.
val modelBasePath: String =
    localProps.getProperty("CURRO_MODEL_BASE_PATH")
        ?: "/data/local/tmp/curro-models"
```

Then, inside **both** `debug` and `release` build types:

```kotlin
buildConfigField("String", "MODEL_BASE_PATH", "\"$modelBasePath\"")
```

(The value is the same in both build types — there is no debug/release skew. The
field exists in `BuildConfig` for both so production code can reference it
without `#ifdef`-style branching.)

### 8.5 `docs/curro-spec-v1.0.md` §14 edit

Locate the "**Riesgos identificados:**" bullet list. Add (or replace, if a
placeholder line exists) a new bullet at the top:

> - **Entrega de modelos (decisión cerrada para prototipo):** side-load vía
>   `adb push` a `/data/local/tmp/curro-models/`. Ruta configurable en
>   `local.properties` (`CURRO_MODEL_BASE_PATH`), expuesta en runtime como
>   `BuildConfig.MODEL_BASE_PATH`. Un SF posterior (post-prototipo) introducirá
>   entrega empaquetada / Play Asset Delivery sin tocar `ModelFiles`.

**No version bump** — the spec contract (§4.3, §5, §6) is unchanged; this is
documentation of an implementation decision the spec previously left open. The
"Historial de revisiones" table is NOT touched.

### 8.6 `CLAUDE.md` "On-device models" edit

After the existing table (lines 273–276), add:

> **Side-load for the prototype** (US-019): weights live on the device at
> `/data/local/tmp/curro-models/function_gemma_270m.task`; the path is
> configurable via `local.properties` (`CURRO_MODEL_BASE_PATH`) and exposed at
> runtime as `BuildConfig.MODEL_BASE_PATH`. The single seam is
> `data/ml/ModelFiles.kt`. A future SF will introduce bundled / asset-pack
> delivery for release without changing the `ModelFiles` abstraction. The
> "release APK bundles ~2.3 GB of model weights" admonition still applies once
> delivery is bundled.

### 8.7 `docs/MODELS.md` (new file — outline)

```markdown
# Curro — On-device model files

> **Status:** prototype-only delivery (side-load via `adb push`). A future SF
> introduces bundled / Play Asset Delivery for release.

## FunctionGemma 270M

- **File**: `function_gemma_270m.task`  ·  ~288 MB  ·  int8 quant  ·  MediaPipe
  Tasks GenAI format.
- **Source**: <document where to obtain the weights — TODO link>.
- **Where it lives on the device**: `/data/local/tmp/curro-models/` (overridable
  via `local.properties` → `CURRO_MODEL_BASE_PATH`).

## Side-loading

```bash
# 1) Make sure adb sees the device
adb devices

# 2) Create the directory
adb shell mkdir -p /data/local/tmp/curro-models/

# 3) Push the file
adb push function_gemma_270m.task /data/local/tmp/curro-models/

# 4) Sanity-check
adb shell ls -l /data/local/tmp/curro-models/
```

## Verifying the app sees it

After install + push, in `adb logcat`:

```
Curro/Llm: warm-up took <ms>ms
```

(The line is emitted by `FunctionGemmaEngine.warmUp()` in US-020; it does not
appear in this SF.)

## Removing the file (clean-slate testing)

```bash
adb shell rm /data/local/tmp/curro-models/function_gemma_270m.task
```

`ModelFiles.isFunctionGemmaAvailable()` now returns `false`; the engine reports
`CurroError.ModelCold`; the smoke loop speaks `copy_models_not_ready`.

## HyperOS / Redmi 15 setup (required for the warm-up service to survive)

Curro relies on a foreground service (US-023) to keep FunctionGemma in memory.
HyperOS will kill that service unless:

1. Settings → Battery → App battery saver → Curro → "No restrictions".
2. Security app → Autostart → Curro: ON.

`launcher-app` skill documents the full list. These are device-side toggles, not
something the app can request programmatically.
```

### 8.8 `.gitignore` diff

Add a new block (or extend the existing models-related block if one exists):

```
# On-device model files — side-loaded via adb push; NEVER in git.
*.task
```

Place it after the existing `# Built application files` section, somewhere
visible. The `*.task` glob is defensive — the canonical path is on-device
anyway, but a developer might copy the file into the working tree accidentally.

### 8.9 Strings — exact entry

```xml
<!-- US-019 (SF-3.1) — spoken when FunctionGemma is not yet warm.
     Wired by US-024's smoke loop on CurroError.ModelCold. Curro's voice:
     warm, brief, no apology. -->
<string name="copy_models_not_ready">Aún estoy preparando los modelos, dame un segundo.</string>
```

### 8.10 Tests

`app/src/test/java/com/curro/app/data/ml/ModelFilesTest.kt`:

```kotlin
package com.curro.app.data.ml

import com.curro.app.BuildConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

class ModelFilesTest {

    @Test
    fun `BuildConfig MODEL_BASE_PATH defaults to data local tmp curro-models`() {
        // The default lands in BuildConfig when local.properties has no override.
        // CI environments without local.properties hit exactly this path.
        assertEquals("/data/local/tmp/curro-models", BuildConfig.MODEL_BASE_PATH)
    }

    @Test
    fun `isFunctionGemmaAvailable is false on a clean test machine`() {
        // JVM unit tests run without a model file at the configured path.
        // If a developer has the file at the system path (Linux dev box), this
        // test will be flaky — that's accepted as a non-issue for CI (no file there).
        assertFalse(ModelFiles.isFunctionGemmaAvailable())
    }

    @Test
    fun `functionGemma resolves to the expected filename`() {
        val f = ModelFiles.functionGemma()
        assertEquals("function_gemma_270m.task", f.name)
        assertEquals(BuildConfig.MODEL_BASE_PATH, f.parentFile?.absolutePath)
    }
}
```

---

## 9. Senior-UX & Copy

The user never sees anything from this SF directly. The only voice line wired
in this SF is:

- `copy_models_not_ready` = **"Aún estoy preparando los modelos, dame un segundo."**

Curro's voice: warm, brief, no apology, no jargon. The line is referenced by
US-024 (the smoke loop) when `CurroError.ModelCold` is raised.

---

## 10. Acceptance Criteria

(Mirrored verbatim from the PRD entry — checkable here for the implementer.)

- [ ] `BuildConfig.MODEL_BASE_PATH` resolves to `/data/local/tmp/curro-models`
  by default (verified by `ModelFilesTest`).
- [ ] `BuildConfig.MODEL_BASE_PATH` overridable via `local.properties` →
  `CURRO_MODEL_BASE_PATH`.
- [ ] `BuildConfig.MODEL_BASE_PATH` emitted in both `debug` and `release` build
  types with the same value.
- [ ] `data/ml/ModelFiles.kt` exists; `isFunctionGemmaAvailable()` returns
  `false` on a clean test machine.
- [ ] `.gitignore` has `*.task` defensively.
- [ ] `docs/curro-spec-v1.0.md` §14 "Riesgos identificados" includes the
  side-load decision paragraph (no version bump).
- [ ] `CLAUDE.md` "On-device models" section includes the side-load paragraph.
- [ ] `docs/MODELS.md` exists and documents the `adb push` procedure +
  HyperOS battery-whitelist steps.
- [ ] `strings.xml` has `copy_models_not_ready` = "Aún estoy preparando los
  modelos, dame un segundo."
- [ ] **No MediaPipe dependency activation in this SF** — `app/build.gradle.kts`
  dependencies block still has the `// MediaPipe → SF-3.1: …` reserved comment
  (US-020 will replace it).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
  green without any `.task` file present.

---

## 11. Performance Considerations

- `ModelFiles.functionGemma()` returns a fresh `File` object each call — that's
  intentional (cheap, no caching needed, no thread-safety to worry about). The
  file existence/readability check is also cheap; the engine can call it on
  every `warmUp()` attempt without concern.
- The `local.properties` read happens once at configuration time in Gradle, not
  at runtime.

---

## 12. Testing Requirements

- [ ] **Unit**: `ModelFilesTest.kt` — three tests as above (default path,
  isFunctionGemmaAvailable, functionGemma resolves expected filename).
- [ ] **Build**: `./gradlew assembleDebug` green on CI with no model file.
- [ ] **Manual on the Redmi 15** (developer): `adb push` the file, install the
  debug APK, verify (after US-020 lands) that `Log.i("Curro/Llm", "warm-up took
  …")` appears in logcat.
- [ ] **No new instrumented tests** — there's no UI to exercise.

---

## 13. Implementation Notes

### Order of operations (single-commit SF)

1. Add the `*.task` line to `.gitignore`.
2. Extend `app/build.gradle.kts`: read `CURRO_MODEL_BASE_PATH` from
   `localProps`; emit `buildConfigField` in both build types.
3. Add `data/ml/ModelFiles.kt`.
4. Add `copy_models_not_ready` to `strings.xml`.
5. Add `docs/MODELS.md`.
6. Edit `docs/curro-spec-v1.0.md` §14 paragraph (no version bump).
7. Edit `CLAUDE.md` "On-device models" section.
8. Add `ModelFilesTest.kt`.
9. Run `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` —
   green.

### Why side-load (not download-on-first-run, not asset-pack)

The orchestrator pinned this decision before the PM batch. Recorded here for
traceability:

- **Side-load** wins for the prototype because there's a single physical device,
  the developer is the only person who needs to put the file there, and CI
  doesn't need the file at all.
- **Download on first run** is the natural release story but requires a network
  permission Curro doesn't otherwise have in main (release-only INTERNET is for
  telemetry, scoped). A first-run downloader is the wrong shape for a prototype.
- **Asset pack / Play Asset Delivery** is the right shipping path eventually but
  adds Play-Store distribution complexity that doesn't earn anything during the
  prototype.

The `ModelFiles` seam is the swap point: a future SF replaces
`functionGemma()`'s body (`File(...)` → `Context.filesDir.resolve(…)` after a
downloader, or `Context.assets`-driven asset-pack lookup) without any change to
US-020's engine or US-023's service.

### Commit scope

`feat(model)` — per `git-workflow` skill, the model-asset wiring is its own
scope; this SF doesn't touch the LLM engine (`feat(llm)` is US-020).

---

## 14. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-15 | android-product-analyst | Initial draft for Phase-3 PM batch. |
