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
| FunctionGemma 270M | [`litert-community/functiongemma-270m-ft-mobile-actions`](https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions) | ~289 MB | **`.litertlm` (q8/int8)** | `function_gemma_270m.litertlm` | Phase 3 — FunctionGemma decision layer (modelo Gemma 3 fine-tune Mobile-Actions; **NO se cambia con el swap de Gemma 4** — no hay variante 270M Mobile-Actions de Gemma 4 todavía) |
| Gemma 4 E2B | [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) (fichero `gemma-4-E2B-it.litertlm`) | ~2.5 GB | **`.litertlm` (int4)** | `gemma4_e2b.litertlm` | Phase 9 — NL generation (US-061 / US-062). **Apache 2.0**, sin gate. |

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

### 4. Renombrar al filename que el código espera

```bash
mv models/mobile_actions_q8_ekv1024.litertlm models/function_gemma_270m.litertlm
```

(Sí, mantiene la extensión `.litertlm` — ver §"Formato" abajo para el porqué.)

---

## Formato: `.litertlm` vs `.task`

El spec original (US-019) asumió formato MediaPipe `.task` (lo que `MediaPipe
Tasks GenAI` aceptaba en `0.10.14`). La realidad de los releases en
`litert-community` y `google/*-litert-lm` a fecha de mayo 2026 es que están en
**`.litertlm`** (formato nuevo de LiteRT-LM, **flatbuffer-based, NO un ZIP**).

**Hecho confirmado en hardware (Samsung A53, MediaPipe 0.10.35, mayo 2026)**:

- `setModelPath()` **no auto-detecta** por magic bytes — branch por extensión.
- `.task` → loader ZIP-bundle clásico (intenta deszipear → `UnsupportedFormatException`
  si el archivo no es ZIP).
- `.litertlm` → loader nativo LiteRT-LM (acepta los archivos del HF directos).

**Por eso `ModelFiles.kt` espera `.litertlm`**, y los archivos en
`/data/local/tmp/curro-models/` deben tener esa extensión.

### Si tienes archivos `.task` (antiguos / generados con `ai-edge-torch`)

Funcionan tal cual con `setModelPath("...task")` — son ZIP-bundles con
`model.tflite + tokenizer + metadata`. Si vuelves a ese formato, cambia
`ModelFiles.kt` para usar el sufijo `.task` (ambas extensiones son válidas, no
ambas a la vez).

### Si necesitas convertir `.litertlm` → `.task`

Última ratio: con [`ai-edge-torch`](https://github.com/google-ai-edge/ai-edge-torch)
y el notebook de Google
[`Convert_Gemma_3_270M_to_LiteRT_for_MediaPipe_LLM_Inference_API.ipynb`](https://colab.research.google.com/github/google-gemini/gemma-cookbook/blob/main/Demos/Emoji-Gemma-on-Web/resources/Convert_Gemma_3_270M_to_LiteRT_for_MediaPipe_LLM_Inference_API.ipynb).
Requiere GPU. Solo si el camino `.litertlm` falla en alguna versión futura.

---

## Cómo subirlos al dispositivo

Para el prototipo, pusheamos a `/data/local/tmp/` (escribible por `adb shell`
sin root):

```bash
adb shell mkdir -p /data/local/tmp/curro-models
adb push models/function_gemma_270m.litertlm /data/local/tmp/curro-models/

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

`assembleDebug` funciona sin los `.litertlm`. `ModelFiles.isFunctionGemmaAvailable()`
devuelve `false`, el `ModelWarmupService` no carga nada, y la app habla
*"Aún estoy preparando los modelos, dame un segundo"* (`copy_models_not_ready`)
hasta que aparezcan. Esto deja CI verde sin las weights (no las ve nunca).

---

## Cómo bajar los pesos (Gemma 4 E2B — Phase 9)

| Slot lógico | Origen | Tamaño | Filename esperado |
|---|---|---|---|
| Gemma 4 E2B | [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) (fichero `gemma-4-E2B-it.litertlm`) | ~2.5 GB | `gemma4_e2b.litertlm` (mantener extensión — ver §"Formato") |

Gemma 4 está bajo **licencia Apache 2.0**. No requiere aceptar términos en HF ni
token de descarga; cualquiera con `wget`/`curl` lo baja en directo:

```bash
# Desde la raíz del repo. ~2.5 GB — toma 5-15 min según conexión.
mkdir -p models
curl -L \
  -o models/gemma-4-E2B-it.litertlm \
  "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

# Verifica tamaño (~2.5 GB):
ls -lh models/gemma-4-E2B-it.litertlm
```

> Importante: **bajar la variante Android**, NO `gemma-4-E2B-it-web.task` (web)
> ni los `*_qualcomm_*.litertlm` (específicos de SoC, ~3 GB). El fichero
> Android genérico es `gemma-4-E2B-it.litertlm`.

Rename para alinear con el filename que espera `ModelFiles.gemma3n()`
(método retiene el nombre `gemma3n` por higiene de diff; la implementación
apunta a `gemma4_e2b.litertlm`):

```bash
mv models/gemma-4-E2B-it.litertlm models/gemma4_e2b.litertlm
```

**La extensión `.litertlm` importa** (ver §"Formato" arriba) — `setModelPath`
en MediaPipe 0.10.35 branch por extensión: `.litertlm` → loader nativo
LiteRT-LM, `.task` → loader ZIP-bundle (incompatible con los `.litertlm` que
ship Google en HF).

Empuja al dispositivo:

```bash
adb shell mkdir -p /data/local/tmp/curro-models
adb push models/gemma4_e2b.litertlm /data/local/tmp/curro-models/
adb shell ls -lh /data/local/tmp/curro-models/   # verifica que aparece + tamaño
```

`ModelFiles.isGemma3nAvailable()` (añadido en US-061; nombre retiene `gemma3n`
por higiene de diff — apunta al nuevo filename internamente) devuelve `false` si
el fichero no está; en ese caso US-062 cae al fallback `copy_many_unread` sin
intentar cargar nada. CI siempre corre sin estos pesos — el test JVM es 100 %
fallback.

### Por qué Gemma 4 (no 3n)

Swap aplicado en mayo 2026, vía `refactor(llm): swap Gemma 3n → Gemma 4 E2B`.

- **Licencia Apache 2.0** (vs el custom Gemma licence de 3n) — más limpio para
  distribución futura (Play Asset Delivery, sideload firmado, etc.); sin
  gate en HF, sin token.
- **~1.16 GB menos en disco** (~2.5 GB vs ~3.66 GB) — alivio en
  `/data/local/tmp/curro-models/` durante el sideload y, en el futuro, en el
  AAB / asset pack.
- **Mejor calidad** — benchmarks oficiales muestran E2B Gemma 4 ganando a
  Gemma 3 27B en AIME, LiveCodeBench, Codeforces y Tau2; mejor razonamiento
  para el resumen de WhatsApp.
- **PLE ("matformer") preservado** — el truco de Per-Layer Embeddings que
  hacía a Gemma 3n RAM-friendly sigue en Gemma 4, así que la RAM activa
  estimada se mantiene en ~2-3 GB (cabe en el A53 6 GB y, con holgura, en
  el Redmi 15 8 GB).

### Smoke test (manual, una vez por dispositivo nuevo)

Con los pesos presentes:

```bash
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.curro.app.data.ml.Gemma3nSmokeTest
adb logcat -s Curro/Gemma4Smoke
```

(El nombre de la clase de test sigue siendo `Gemma3nSmokeTest` por higiene de
diff; el tag de logcat **sí cambió** a `Curro/Gemma4Smoke` para que el filtrado
refleje el modelo real.)

Espera ver:

```
I/Curro/Gemma4Smoke: cold-load = <ms>ms          ← target ≤ 10000
I/Curro/Gemma4Smoke: first-inference = <ms>ms; output = <n> chars  ← target ≤ 8000
```

Si alguno se sale del presupuesto, ver
`docs/architecture/gemma-text-engine-decision.md` §Latency target y aplicar el
rollback (una línea en `ReadAllUnreadWhatsAppHandler`). El dev/test baseline es
el Samsung Galaxy A53 5G (6 GB, Exynos 1280, Android 13 + One UI) — los
presupuestos están calibrados para ese suelo. Gemma 4 cabe en el mismo envelope
que Gemma 3n (PLE preservado), así que los presupuestos no se tocan en el swap.

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
