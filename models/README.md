# Curro — modelos on-device

Esta carpeta es la **fuente de los pesos de los modelos en tu Mac de desarrollo**.
Los ficheros pesados (`.task`, `.litertlm`, `.bin`, `.tflite`, `.gguf`) están
**gitignored** — cada uno pesa cientos de MB y no entra en CI ni en git.
Cualquiera que clone el repo encuentra esta carpeta vacía + este README y sabe
qué hacer.

---

## Ficheros que van aquí

| Slot lógico | Origen confirmado | Tamaño | Formato real | Filename esperado por la app | Usado en |
|---|---|---|---|---|---|
| FunctionGemma 270M | [`litert-community/functiongemma-270m-ft-mobile-actions`](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions) | ~289 MB | **`.litertlm` (q8/int8)** | `function_gemma_270m.task` *(ver §"Formato")* | Phase 3 — FunctionGemma decision layer |
| Gemma 3n E2B | _TBD — confirmar slug HF_ | ~2 GB | `.task` (asumido) | `gemma3n_e2b.task` | Phase 9 — NL generation (US-061 / US-062) |

El fine-tune "Mobile Actions" es **exactamente** nuestro caso de uso (llamar
apps por voz, function-calling sobre intents móviles). Reporta 99.67% accuracy
token-level en su validation set después del fine-tuning.

---

## Cómo bajar los pesos (FunctionGemma — paso a paso)

El modelo está **gated** detrás de la licencia Gemma. Pasos:

### 1. Aceptar la licencia Gemma (una vez por cuenta HF)

1. Abrir [`google/functiongemma-270m-it`](https://huggingface.co/google/functiongemma-270m-it) con tu cuenta de Hugging Face.
2. En la cabecera de la página, "Acknowledge license to access" → click.
3. Aceptar los términos de uso de Gemma (form de Google).
4. La aceptación se propaga a TODOS los repos derivados, incluido `litert-community/functiongemma-270m-ft-mobile-actions`.

### 2. Generar un token de Hugging Face (lectura)

1. Abrir [`huggingface.co/settings/tokens`](https://huggingface.co/settings/tokens).
2. "New token" → role: **Read** (no Write — solo necesitas leer).
3. Copiar el token. Empieza por `hf_…`.

### 3. Bajar el fichero

```bash
# Desde la raíz del repo:
HF_TOKEN="hf_xxxxxxxxxxxxxxxxxxxx"   # tu token; NO lo commitees

curl -L \
  -H "Authorization: Bearer ${HF_TOKEN}" \
  -o models/mobile_actions_q8_ekv1024.litertlm \
  "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions/resolve/main/mobile_actions_q8_ekv1024.litertlm"

# Verifica tamaño (~289 MB):
ls -lh models/mobile_actions_q8_ekv1024.litertlm
```

> Alternativa: usar `huggingface-cli` (`pip install huggingface_hub`,
> `huggingface-cli login`, `huggingface-cli download litert-community/functiongemma-270m-ft-mobile-actions mobile_actions_q8_ekv1024.litertlm --local-dir models/`).

### 4. Renombrar (formato real vs filename esperado — ver §"Formato" abajo)

```bash
mv models/mobile_actions_q8_ekv1024.litertlm models/function_gemma_270m.task
```

…**o** actualiza el código (§"Formato"). La copia + rename es la ruta de
menos fricción para un primer smoke test.

---

## Formato: `.litertlm` vs `.task`

El spec original (US-019) asumió formato MediaPipe `.task` (lo que `MediaPipe
Tasks GenAI` aceptaba en `0.10.14`). La realidad de los releases gated en
`litert-community` a fecha de mayo 2026 es que están en **`.litertlm`** (formato
nuevo de LiteRT-LM que reemplaza al bundle `.task` original). Hay 3 opciones:

### (a) Renombrar sin tocar código — **prototipo, recomendado para empezar**

```bash
mv models/mobile_actions_q8_ekv1024.litertlm models/function_gemma_270m.task
```

`LlmInferenceOptions.builder().setModelPath(...)` en MediaPipe `0.10.14+` mira
los magic bytes, **no la extensión**. Probable que cargue. Si peta con un
`UnsupportedModelException` o equivalente, ir a la opción (b).

### (b) Actualizar el código para usar `.litertlm` nativo

Toques mínimos:

1. **`app/src/main/java/com/curro/app/data/ml/ModelFiles.kt`** — cambiar el
   filename:
   ```kotlin
   // antes:  File(BuildConfig.MODEL_BASE_PATH, "function_gemma_270m.task")
   // ahora:  File(BuildConfig.MODEL_BASE_PATH, "function_gemma_270m.litertlm")
   ```
2. **`gradle/libs.versions.toml`** — bump `mediapipeTasksGenai` a la versión
   que añadió soporte nativo `.litertlm` (verificar release notes: típicamente
   `0.10.20+`):
   ```toml
   mediapipeTasksGenai = "0.10.20"   # antes "0.10.14"
   ```
3. **CI** — `./gradlew assembleDebug` debe seguir verde sin el fichero (la
   guarda `ModelFiles.isFunctionGemmaAvailable()` cubre eso; nada más cambia).

### (c) Convertir `.litertlm` → `.task` con `ai-edge-torch`

Si MediaPipe no acepta el `.litertlm` y el bump no resuelve, hay que convertir
con [`ai-edge-torch`](https://github.com/google-ai-edge/ai-edge-torch) y el
notebook de Google: [`Convert_Gemma_3_270M_to_LiteRT_for_MediaPipe_LLM_Inference_API.ipynb`](https://colab.research.google.com/github/google-gemini/gemma-cookbook/blob/main/Demos/Emoji-Gemma-on-Web/resources/Convert_Gemma_3_270M_to_LiteRT_for_MediaPipe_LLM_Inference_API.ipynb).
Requiere GPU para reconvertir. Última ratio — empezar por (a) o (b).

---

## Cómo subirlos al dispositivo

Para el prototipo, pusheamos a `/data/local/tmp/` (escribible por `adb shell`
sin root):

```bash
adb shell mkdir -p /data/local/tmp/curro-models
adb push models/function_gemma_270m.task /data/local/tmp/curro-models/

# Verificar:
adb shell ls -lh /data/local/tmp/curro-models/
```

La app lee de `BuildConfig.MODEL_BASE_PATH` (default `/data/local/tmp/curro-models`),
sobrescribible con `CURRO_MODEL_BASE_PATH=<path>` en `local.properties` si
quieres otra ubicación.

### Logcat al primer arranque con pesos presentes

```
adb logcat -s Curro/Warmup Curro/Llm Curro/FailedCommand
```

Espera ver:
```
I Curro/Warmup: onCreate
I Curro/Llm:    warm-up took 800-1500 ms   ← cold start
I Curro/Warmup: warm-up scheduled — engine.isReady = true
I Curro/Llm:    decide latency: <ms>       ← target < 500 ms warm en Redmi 15
```

---

## Comportamiento sin los pesos

`assembleDebug` funciona sin el `.task`. `ModelFiles.isFunctionGemmaAvailable()`
devuelve `false`, el `ModelWarmupService` no carga nada, y la app habla
*"Aún estoy preparando los modelos, dame un segundo"* (`copy_models_not_ready`)
hasta que aparezcan. Esto deja CI verde sin las weights (no las ve nunca).

---

## Cómo bajar los pesos (Gemma 3n E2B — Phase 9)

> _TBD: confirmar slug + filename en HF antes de la implementación de US-061.
> La spec asume `gemma3n_e2b.task` ~2 GB activo._

| Slot lógico | Origen | Tamaño | Filename esperado |
|---|---|---|---|
| Gemma 3n E2B | _TBD — confirmar slug HF antes de subir pesos_ | ~2 GB | `gemma3n_e2b.task` (pinned en US-061) |

```bash
adb shell mkdir -p /data/local/tmp/curro-models
adb push models/gemma3n_e2b.task /data/local/tmp/curro-models/
adb shell ls -lh /data/local/tmp/curro-models/   # verifica que aparece + tamaño
```

`ModelFiles.isGemma3nAvailable()` (añadido en US-061) devuelve `false` si el
fichero no está; en ese caso US-062 cae al fallback `copy_many_unread` sin
intentar cargar nada. CI siempre corre sin estos pesos — el test JVM es 100 %
fallback.

### Smoke test (manual, una vez por dispositivo nuevo)

Con los pesos presentes:

```bash
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.curro.app.data.ml.Gemma3nSmokeTest
adb logcat -s Curro/Gemma3nSmoke
```

Espera ver:

```
I/Curro/Gemma3nSmoke: cold-load = <ms>ms          ← target ≤ 10000
I/Curro/Gemma3nSmoke: first-inference = <ms>ms; output = <n> chars  ← target ≤ 8000
```

Si alguno se sale del presupuesto, ver `docs/architecture/gemma-3n-decision.md`
§Latency target y aplicar el rollback (una línea en
`ReadAllUnreadWhatsAppHandler`). El dev/test baseline es el Samsung Galaxy
A53 5G (6 GB, Exynos 1280, Android 13 + One UI) — los presupuestos están
calibrados para ese suelo.

---

## HyperOS / Redmi 15 — whitelist obligatorio

Curro depende de un foreground service (`ModelWarmupService`, US-023) para
mantener FunctionGemma en memoria entre interacciones. HyperOS mata ese
servicio si Curro no está en la lista blanca, **incluso teniendo notificación
en barra**. Una vez por dispositivo:

1. Ajustes → Batería → Ahorro de batería por app → Curro → "Sin restricciones".
2. App de seguridad (Security) → Autostart → Curro: ON.

Sin estos toggles el modelo se queda frío con la pantalla apagada y el
primer mic-press de la mañana cae en la rama de recuperación
(`copy_models_not_ready`, "Aún estoy preparando los modelos…"). El segundo
press ya está caliente — pero el primero queda como UX feo.

---

## Futuro (release)

Para una distribución real (Play Store, sideload firmado para el padre de Fran)
esto cambia a **Play Asset Delivery** o bundling en un split-APK. Ese cambio se
hará en una SF posterior; el `ModelFiles` abstrai eso — solo cambia la
implementación de dónde sale el path.
