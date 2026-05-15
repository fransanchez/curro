# Curro — modelos on-device

Esta carpeta es la **fuente de los pesos de FunctionGemma + Gemma 3n en tu Mac de
desarrollo**. Los ficheros `.task` aquí están **gitignored** (cada uno pesa
cientos de MB y no entra en CI ni en git). Cualquiera que clone el repo encuentra
esta carpeta vacía + este README y sabe qué hacer.

## Ficheros que van aquí

| Fichero | Tamaño | Origen | Usado en |
|---|---|---|---|
| `function_gemma_270m.task` | ~288 MB | (TBD — apuntar al release de Google AI Edge) | Phase 3 — FunctionGemma decision layer |
| `gemma3n_e2b.task` | ~2 GB | (TBD — apuntar al release de Google AI Edge) | Phase 9 — generación NL (a evaluar) |

> Las URLs de descarga van aquí cuando se confirmen los releases de Google AI
> Edge para LiteRT/MediaPipe Tasks GenAI. De momento (prototipo) se obtiene la
> versión cuando se vaya a probar en el Redmi 15 real.

## Cómo subirlos al dispositivo

MediaPipe LLM Inference necesita los ficheros en una ruta legible por la app en
el propio dispositivo. Para el prototipo, los pusheamos a `/data/local/tmp/`
(escribible por `adb shell` sin root):

```bash
# Una vez que tengas `function_gemma_270m.task` en esta carpeta:
adb shell mkdir -p /data/local/tmp/curro-models
adb push models/function_gemma_270m.task /data/local/tmp/curro-models/

# Para verificar:
adb shell ls -lh /data/local/tmp/curro-models/
```

La app lee de `BuildConfig.MODEL_BASE_PATH` (default `/data/local/tmp/curro-models`),
sobrescribible con `CURRO_MODEL_BASE_PATH=<path>` en `local.properties` si quieres
otra ubicación.

## Comportamiento sin los pesos

`assembleDebug` funciona sin el `.task`. `ModelFiles.isFunctionGemmaAvailable()`
devuelve `false`, el `ModelWarmupService` no carga nada, y la app habla
*"Aún estoy preparando los modelos, dame un segundo"* (`copy_models_not_ready`)
hasta que aparezcan. Esto deja CI verde sin las weights (no las ve nunca).

## Futuro (release)

Para una distribución real (Play Store, sideload firmado para el padre de Fran)
esto cambia a **Play Asset Delivery** o bundling en un split-APK. Ese cambio se
hará en una SF posterior; el `ModelFiles` abstrai eso — solo cambia la
implementación de dónde sale el path.
