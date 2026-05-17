# Curro — Especificación del Prototipo

**Producto:** Curro, launcher asistente para personas mayores
**Versión:** 1.1
**Propietario:** Fran
**Estado:** Spec cerrada para implementación de prototipo
**Fecha:** Mayo 2026

---

## Índice

1. [Visión](#1-visión)
2. [Principios de diseño](#2-principios-de-diseño)
3. [Usuario objetivo](#3-usuario-objetivo)
4. [Arquitectura del sistema](#4-arquitectura-del-sistema)
5. [Catálogo de funciones](#5-catálogo-de-funciones)
6. [Flujos de interacción](#6-flujos-de-interacción)
7. [Modelo de aprendizaje](#7-modelo-de-aprendizaje-alias-y-preferencias)
8. [Manejo de llamadas entrantes](#8-manejo-de-llamadas-entrantes-función-opcional)
9. [Menú de configuración](#9-menú-de-configuración-acceso-para-fran)
10. [Permisos Android](#10-permisos-android-requeridos)
11. [Diseño UX del launcher](#11-diseño-ux-del-launcher)
12. [Privacidad](#12-privacidad)
13. [Criterios de validación del prototipo](#13-criterios-de-validación-del-prototipo)
14. [Resumen ejecutivo para implementación](#14-resumen-ejecutivo-para-implementación)

---

## 1. Visión

**Curro** es un launcher de Android orientado a personas mayores que combina una interfaz visual simplificada (reloj, accesos grandes a apps frecuentes) con un asistente de voz local capaz de ejecutar las acciones más comunes del teléfono: leer mensajes, llamar a contactos, abrir apps, hacer cálculos y, progresivamente, más.

El asistente corre íntegramente en el dispositivo. No depende de conexión a internet ni envía datos a servidores externos. Esto es decisión deliberada por privacidad, latencia y resiliencia ante mala cobertura.

**El prototipo no busca ser un producto.** Busca responder una sola pregunta: *¿usa mi padre esta app, le sirve, y mejora su día a día respecto al teléfono tal cual está hoy?* Si la respuesta es sí, se invierte en MVP. Si es no, se aprende qué falló antes de invertir más.

## 2. Principios de diseño

- **Local siempre que sea viable.** FunctionGemma y Gemma 3n corren on-device. Nada de cloud por defecto.
- **Manos libres total como objetivo, pantalla como apoyo.** El usuario debe poder completar cualquier acción crítica sin tocar la pantalla, pero la pantalla refuerza visualmente lo que está pasando.
- **Confirmar lo irreversible, ejecutar lo reversible.** Llamar y enviar mensaje requieren "¿confirmas?". Leer un mensaje, abrir una app o calcular no.
- **La app aprende sobre el usuario.** Alias de contactos, preferencias de voz, atajos personales se descubren con uso, no se configuran de entrada.
- **Sin reentrenamiento del usuario.** Si tu padre dice "llama a Pepito" o "llámame a Pepito" o "ponme con Pepito", la app entiende. La fluidez la pone el modelo, no se le pide al usuario aprender una sintaxis.
- **Fallar de forma comprensible.** Si algo no se entiende, la app dice qué pasó en lenguaje natural y propone alternativa. Nunca silencio ni mensaje de error críptico.
- **Personalidad cálida y andaluza, no servil.** Curro habla con naturalidad, en castellano coloquial, con calidez pero sin ser pelota ni pedir disculpas constantemente. No dice "claro, cómo no, ahora mismo" sino "vale, llamando a Pepito". Es eficiente y cercano, como un amigo que ayuda, no como un mayordomo.

## 3. Usuario objetivo

**Perfil único validado:** un hombre mayor en Málaga, padre de Fran. Acaba de comprar un Redmi 15. Le cuesta:

- Ver mensajes de WhatsApp por tamaño de texto y densidad de la UI.
- Usar la calculadora del teléfono (encontrarla, manejarla).
- Localizar y seleccionar el contacto correcto entre muchos.

Lo que sí sabe hacer hoy: encender el teléfono, descolgar llamadas entrantes, hacer llamadas si alguien le pasa el contacto en grande, y en general manejarse cuando algo está suficientemente claro y grande.

**Asunciones de diseño:**
- Visión deteriorada pero funcional con texto grande y contraste alto.
- Audición funcional. Puede recibir feedback por voz sin problemas.
- Motricidad fina reducida: los toques deben ser sobre áreas grandes (>96dp).
- Memoria episódica intacta pero curva de aprendizaje de nuevas UIs muy lenta. La app debe sentirse igual cada día.
- Castellano de España, registro coloquial.

## 4. Arquitectura del sistema

Cinco capas con responsabilidades estancas. El contrato entre capas es un JSON estructurado, lo que permite reemplazar cualquier capa sin tocar las demás.

### 4.1 Capa de captura

Disparador único en el prototipo: **botón principal en el launcher**.

- Ocupa al menos el 40% de la pantalla.
- Etiquetado con texto grande y un icono claro de micrófono.
- Retroalimentación háptica al pulsar (vibración breve).
- Retroalimentación visual: cambia de color al activarse, muestra ondas de audio mientras escucha.

Una vez pulsado, captura audio del micrófono y lo pasa al STT.

**Hotword fuera de scope para el prototipo.** Se evalúa para fase posterior si la validación con usuario lo justifica. Razones para excluirlo ahora: complejidad adicional, consumo de batería continuo, falsos positivos, y necesidad de un servicio en foreground permanente. Validar primero si el flujo "pulsar botón → hablar" funciona en su día a día.

### 4.2 Capa de transcripción (STT)

`SpeechRecognizer` nativo de Android en español. Funciona offline en Android 12+ con el paquete de voz instalado. Devuelve texto plano.

Si STT falla o devuelve cadena vacía, la app responde por voz: "No te he oído bien, ¿puedes repetirlo?". Sin pantalla de error, sin códigos.

### 4.3 Capa de decisión (FunctionGemma 270M, on-device)

Recibe:
- Texto transcrito.
- Catálogo de funciones disponibles (ver sección 5).
- Contexto mínimo: hora actual, últimos N mensajes recibidos sin leer, alias de contactos conocidos.

Devuelve un objeto JSON con la forma:

```json
{
  "action": "<function_name>",
  "params": { ... },
  "confidence": 0.0-1.0
}
```

**Política de confirmación basada en confianza.** Las funciones declaran su exigencia con uno de tres valores en `needs_confirmation`:

- `false`: ejecutar sin confirmar, siempre. Aplica a acciones reversibles o consultivas (leer, calcular, abrir app).
- `true`: confirmar siempre antes de ejecutar. Reservado para acciones de alta criticidad si se añaden en el futuro (ej.: borrar algo, enviar a múltiples destinatarios).
- `conditional`: el comportamiento depende de la confianza del modelo:
  - `confidence >= 0.85`: ejecutar directamente. *"Llamando a Pepito"* y marca.
  - `0.60 <= confidence < 0.85`: confirmar antes. *"Voy a llamar a Pepito, ¿confirmas?"*.
  - `confidence < 0.60`: pedir aclaración. *"No te he entendido bien, ¿quieres llamar a alguien?"*.

Los umbrales son ajustables desde el menú de configuración para que Fran pueda afinarlos según observe a su padre. Por defecto, los valores anteriores.

**Casos en los que `conditional` siempre escala a confirmación obligatoria**, ignorando la confianza:
- El parámetro resuelve a una **ambigüedad explícita** (ej.: hay tres Marías en contactos y no hay alias que desambigüe).
- La acción tiene **coste irreversible inmediato** (ej.: en futuro, una compra o un envío de dinero).
- El usuario lo ha pedido en configuración (modo "confirma siempre", útil al principio del uso).

FunctionGemma se mantiene caliente en memoria mediante un foreground service. Latencia objetivo: <500ms desde texto a JSON.

### 4.4 Capa de contenido (Gemma 3n E2B, on-device)

Solo se invoca cuando una acción requiere generación de lenguaje natural. Ejemplos:
- Resumir 8 WhatsApps en una frase.
- Reescribir una respuesta dictada para que sea más clara.
- Responder a una pregunta abierta del usuario ("¿qué tiempo va a hacer?" no aplica porque no hay internet, pero "¿qué día es hoy?" sí).

Cargado bajo demanda. Si está fría, se avisa por voz: "Dame un segundo". Latencia objetivo en uso típico: 3-6s.

### 4.5 Capa de ejecución (handlers nativos)

Cada función del catálogo tiene un handler en Kotlin que:
1. Valida los parámetros.
2. Resuelve referencias (ej.: "Pepito" → contacto concreto).
3. Si la acción requiere confirmación, dispara el flujo de confirmación.
4. Ejecuta la acción nativa (Intent, NotificationListener, TelecomManager, etc.).
5. Devuelve un resultado o un error semántico que la capa de voz pueda comunicar.

### 4.6 Capa de salida (TTS)

`TextToSpeech` nativo de Android, voz española. Toda comunicación de la app al usuario va por aquí, además de mostrarse en pantalla como apoyo visual.

En prototipo, voz por defecto del sistema. Si tu padre la encuentra robótica o difícil de entender, se evalúa migrar a ElevenLabs o similar.

## 5. Catálogo de funciones

Cada función se declara con la siguiente estructura, que sirve tanto de documentación como de input al prompt de FunctionGemma:

```yaml
nombre: <snake_case>
descripcion: <una frase en lenguaje natural>
params:
  - nombre: tipo / descripción / requerido (sí/no)
ejemplos_voz:
  - "frase típica que diría el usuario"
needs_confirmation: <bool>
handler: <clase Kotlin responsable>
fase: <1|2|3|4>
```

### Fase 1 — MVP del prototipo (8 funciones)

Conjunto mínimo para validar el concepto con usuario real. Cubre los tres dolores principales que motivaron el proyecto (leer WhatsApp, llamar a contactos, usar funciones básicas del teléfono) más utilidades sencillas que dan sensación de "esto entiende lo que le digo".

```yaml
nombre: read_last_whatsapp
descripcion: "Lee en voz alta el último mensaje de WhatsApp recibido"
params:
  - sender: string / nombre del remitente / no
ejemplos_voz:
  - "léeme el último mensaje"
  - "qué dice Pepito"
  - "léeme lo de mi hija"
  - "tengo mensajes nuevos"
needs_confirmation: false
handler: WhatsAppNotificationHandler.readLast
fase: 1
```

```yaml
nombre: read_all_unread_whatsapp
descripcion: "Lee todos los mensajes de WhatsApp no leídos, agrupados por remitente"
params: []
ejemplos_voz:
  - "léeme todos los mensajes"
  - "qué tengo sin leer"
  - "qué mensajes hay"
needs_confirmation: false
handler: WhatsAppNotificationHandler.readAllUnread
fase: 1
```

```yaml
nombre: call_contact
descripcion: "Inicia una llamada telefónica a un contacto resuelto por nombre o alias"
params:
  - contact: string / nombre del contacto o alias aprendido / sí
ejemplos_voz:
  - "llama a Pepito"
  - "llámame a mi hija"
  - "ponme con el médico"
  - "marca el número de Carmen"
needs_confirmation: conditional  # ver sección 4.3
handler: CallHandler.callContact
fase: 1
```

```yaml
nombre: open_app
descripcion: "Abre cualquier app instalada en el teléfono, identificada por nombre"
params:
  - app_name: string / nombre coloquial de la app / sí
ejemplos_voz:
  - "abre la cámara"
  - "abre WhatsApp"
  - "ponme las fotos"
  - "abre el correo"
needs_confirmation: false
handler: LaunchAppHandler.openByName
fase: 1
```

```yaml
nombre: calculate
descripcion: "Resuelve una operación matemática expresada en lenguaje natural y la lee en voz alta"
params:
  - expression: string / operación en lenguaje natural / sí
ejemplos_voz:
  - "cuánto es cuarenta y siete por ocho"
  - "calcula mil dividido entre veinticinco"
  - "cuánto suma quince y veintitrés"
  - "el veintiuno por ciento de doscientos"
needs_confirmation: false
handler: CalculatorHandler.evaluate
fase: 1
```

```yaml
nombre: tell_time
descripcion: "Dice en voz alta la hora actual, el día de la semana y/o la fecha"
params:
  - what: enum(time|date|day|all) / qué información dar / no (default: all)
ejemplos_voz:
  - "qué hora es"
  - "qué día es hoy"
  - "qué fecha es"
  - "dime el día"
needs_confirmation: false
handler: TimeHandler.tell
fase: 1
```

```yaml
nombre: help
descripcion: "Explica al usuario qué cosas puede hacer Curro"
params:
  - topic: string / sobre qué quiere ayuda específicamente / no
ejemplos_voz:
  - "qué puedes hacer"
  - "ayuda"
  - "qué sabes hacer"
  - "cómo te pido cosas"
needs_confirmation: false
handler: HelpHandler.explain
fase: 1
```

### Fase 2 — Comunicación bidireccional y control del dispositivo

Añade capacidad de responder mensajes y controlar funciones básicas del teléfono.

```yaml
nombre: send_whatsapp_reply
descripcion: "Responde por voz al último mensaje recibido de un contacto"
params:
  - contact: string / a quién responder / sí
  - message: string / contenido dictado / sí
ejemplos_voz:
  - "responde a Pepito que voy en camino"
  - "dile a mi hija que llego tarde"
  - "contesta a Carmen"
needs_confirmation: true
handler: WhatsAppNotificationHandler.sendReply
fase: 2
```

```yaml
nombre: set_volume
descripcion: "Sube, baja o silencia el volumen del teléfono"
params:
  - direction: enum(up|down|mute|max) / dirección del cambio / sí
  - amount: int / cuánto cambiar (en pasos) / no (default: 2)
ejemplos_voz:
  - "sube el volumen"
  - "baja el sonido"
  - "más alto"
  - "silencia"
  - "ponlo al máximo"
needs_confirmation: false
handler: VolumeHandler.adjust
fase: 2
```

Otras funciones previstas para Fase 2: `read_sms`, `set_reminder`, `read_reminders`, `dictate_voice_note`.

### Fase 3 — Apoyo a la vida diaria

Funciones más sofisticadas que requieren Gemma 3n para razonar sobre contenido.

- `summarize_whatsapp_thread` — resumir una conversación larga.
- `video_call_contact` — videollamada por WhatsApp o Google Meet.
- `read_news_headlines` — leer titulares (requiere acceso a internet).
- `translate_text` — traducir algo dictado o leído de pantalla.
- `medication_reminder` — recordatorio de medicación con confirmación.

### Fase 4 — Funciones proactivas y contextuales

- `describe_received_photo` — describir foto recibida en WhatsApp usando Gemma 3n multimodal.
- `proactive_alerts` — avisos sin que el usuario pregunte ("mañana tienes médico").
- `explain_current_screen` — leer y explicar la UI de cualquier app abierta usando Accessibility Service.
- `learn_routine` — detectar patrones ("a esta hora sueles llamar a tu hija, ¿quieres hacerlo?").

## 6. Flujos de interacción

Cada flujo se documenta como una secuencia de pasos numerados con cuatro columnas conceptuales: **Usuario** (qué dice o hace), **Sistema** (qué procesa internamente), **Pantalla** (qué se muestra) y **Voz** (qué dice Curro). El símbolo `→` indica transición al siguiente paso, y `[estado]` indica el estado interno de la app.

Los estados posibles de la app son: `idle`, `listening`, `processing`, `confirming`, `executing`, `error_recovery`.

### Flujo 1 — Llamada con confianza alta (camino feliz)

| # | Usuario | Sistema | Pantalla | Voz |
|---|---|---|---|---|
| 1 | Pulsa botón principal | `idle → listening`. Inicia `SpeechRecognizer`. | Botón cambia a azul claro, "Te escucho..." | (silencio) |
| 2 | "Llama a Pepito" | STT transcribe → texto al modelo | Transcripción en tiempo real abajo | (silencio) |
| 3 | (espera) | `listening → processing`. FunctionGemma evalúa. Resultado: `{action: call_contact, params: {contact: "Pepito"}, confidence: 0.92}`. Resuelve "Pepito" → único contacto existente. Confianza alta + sin ambigüedad → ejecución directa. | "Un momento..." | (silencio) |
| 4 | (espera) | `processing → executing`. Handler dispara `Intent.ACTION_CALL`. | "Llamando a Pepito" | "Llamando a Pepito" |
| 5 | (responde Pepito) | Android toma el control de la pantalla de llamada. App vuelve a `idle` cuando la llamada termina. | UI nativa de llamada | — |

**Latencia objetivo end-to-end** (pasos 2-4): <2 segundos.

### Flujo 2 — Llamada con confianza media (confirmación)

| # | Usuario | Sistema | Pantalla | Voz |
|---|---|---|---|---|
| 1 | Pulsa botón | `idle → listening` | "Te escucho..." | — |
| 2 | "Llámame a Pepe" (susurrado, mala dicción) | STT transcribe con score bajo. Texto: "llámame a Pepe". | Transcripción | — |
| 3 | (espera) | FunctionGemma: `{action: call_contact, params: {contact: "Pepe"}, confidence: 0.71}`. Resuelve "Pepe" → "Pepe Martínez" (único con ese nombre). Confianza media → confirmación. `processing → confirming`. | "Un momento..." | — |
| 4 | (espera) | — | "¿Llamo a Pepe Martínez?" con dos botones grandes: ✅ SÍ / ❌ NO | "¿Llamo a Pepe Martínez?" |
| 5 | "Sí" (o pulsa botón) | STT corto, espera respuesta sí/no. | — | — |
| 6 | (espera) | `confirming → executing`. Dispara llamada. | "Llamando a Pepe Martínez" | "Vale, llamando" |

**Variante:** si en el paso 5 el usuario dice "no" o pulsa NO, vuelve a `idle` con un breve "Vale, no llamo".

**Variante:** si en el paso 5 el usuario no dice nada en 10 segundos, Curro dice "Cancelo entonces" y vuelve a `idle`. No esperar indefinidamente con un usuario mayor.

### Flujo 3 — Llamada con ambigüedad (varias Marías)

| # | Usuario | Sistema | Pantalla | Voz |
|---|---|---|---|---|
| 1 | Pulsa botón | `idle → listening` | "Te escucho..." | — |
| 2 | "Llama a María" | STT transcribe correctamente. | Transcripción | — |
| 3 | (espera) | FunctionGemma: `{action: call_contact, params: {contact: "María"}, confidence: 0.94}`. Handler busca en contactos. Encuentra TRES Marías sin alias aprendido que desambigüe. **Ambigüedad explícita → siempre confirma**, independientemente de la confianza. `processing → confirming`. | "Un momento..." | — |
| 4 | (espera) | — | Tres botones grandes con nombre completo y foto si la hay: "María García", "María López", "María Ruiz". Más botón "Ninguna". | "Tienes tres Marías. ¿Cuál de ellas?: María García, María López, o María Ruiz" |
| 5 | "María García" (o pulsa) | STT espera nombre. Match contra las tres opciones. | — | — |
| 6 | (espera) | `confirming → executing`. **Oferta de aprendizaje aplazada** (ver flujo 4): no se aprende en mitad de una llamada para no entorpecer la acción. | "Llamando a María García" | "Llamando a María García" |

**Caso límite:** si en el paso 5 el usuario dice algo que no matchea ninguna ("la primera", "mi prima") con confianza media-baja, Curro repite las opciones una vez. Si vuelve a fallar, cancela: *"Mejor llámala desde la agenda, no me aclaro"*. Esto es honesto y no atrapa al usuario en un bucle.

### Flujo 4 — Aprendizaje de alias

Este flujo se dispara la primera vez que un comando incluye un término relacional ("mi hija", "el médico", "mi nieta") que no está mapeado a un contacto concreto.

| # | Usuario | Sistema | Pantalla | Voz |
|---|---|---|---|---|
| 1 | Pulsa botón | `idle → listening` | "Te escucho..." | — |
| 2 | "Llama a mi hija" | STT transcribe. | Transcripción | — |
| 3 | (espera) | FunctionGemma: `{action: call_contact, params: {contact: "mi hija"}, confidence: 0.88}`. Handler busca alias "mi hija" en BBDD local → no existe. Dispara **subflujo de aprendizaje**. `processing → confirming` (modo aprendizaje). | "Un momento..." | — |
| 4 | (espera) | — | Lista scrollable de contactos en texto grande + botón "Ninguno de estos" | "Aún no sé quién es tu hija. ¿Es alguno de estos contactos? Te los leo: María García, Carmen Pérez, Lucía Ruiz..." (lee máximo 5; si hay más, ofrece "dime su nombre") |
| 5 | "Lucía" o pulsa Lucía Ruiz | Match. | — | — |
| 6 | (espera) | Guarda alias `mi hija → Lucía Ruiz` en BBDD. → `executing`. | "Lo apunto: Lucía Ruiz es tu hija. Llamando." | "Vale, Lucía Ruiz es tu hija. Apuntado. Llamando ahora" |
| 7 | (futuro) | Cualquier futuro "mi hija" resuelve directo a Lucía Ruiz sin preguntar. | — | — |

**Notas de diseño:**

- El sistema **nunca pregunta por más de un alias en la misma interacción**. Aprende uno cada vez.
- Si el usuario dice "ninguno de estos" o "no es ninguno", Curro responde: *"Vale, no pasa nada. Dile a Fran que apunte quién es tu hija"*. Esto traslada la configuración al menú de Fran sin frustrar al usuario.
- Los alias se pueden ver y editar desde el menú de configuración (sección 9).

### Flujo 5 — Lectura de mensajes (caso típico)

| # | Usuario | Sistema | Pantalla | Voz |
|---|---|---|---|---|
| 1 | Pulsa botón | `idle → listening` | "Te escucho..." | — |
| 2 | "Léeme los mensajes" | STT transcribe. | Transcripción | — |
| 3 | (espera) | FunctionGemma: `{action: read_all_unread_whatsapp, confidence: 0.96}`. Handler consulta cache de `NotificationListenerService`. Resultado: 3 mensajes de Pepito, 1 mensaje de Lucía. `processing → executing`. | "Un momento..." | — |
| 4 | (espera) | — | Tarjetas grandes con remitente y texto, scrollable. Mensaje activo resaltado. | "Tienes 3 mensajes de Pepito y 1 mensaje de Lucía. Empiezo con Pepito: 'Te espero a las siete'. 'Trae el pan'. 'Y vino si puedes'. De Lucía: 'Mañana te llamo, papá'." |
| 5 | (lectura termina) | Vuelve a `idle`. | Tarjetas siguen visibles hasta nueva interacción. | (silencio tras la lectura) |

**Decisión de diseño sobre el orden:** se agrupan los mensajes por remitente, no por hora. Si hay muchos (>8) Curro resume: *"Tienes muchos mensajes. ¿Te los leo todos o solo los de alguien en concreto?"*. Esto es el primer guiño a Fase 3 (resumir con Gemma 3n).

**Caso sin mensajes:** *"No tienes mensajes nuevos"*. Corto y claro, sin floritura.

### Flujo 6 — STT no entiende (recuperación)

| # | Usuario | Sistema | Pantalla | Voz |
|---|---|---|---|---|
| 1 | Pulsa botón | `idle → listening` | "Te escucho..." | — |
| 2 | (ruido de fondo, susurro, silencio) | STT devuelve cadena vacía o error `ERROR_NO_MATCH` / `ERROR_SPEECH_TIMEOUT`. | "..." | — |
| 3 | — | `listening → error_recovery`. Counter de fallos consecutivos +1. | "No te he oído" | "No te he oído bien, ¿puedes repetirlo?" |
| 4 | Pulsa botón otra vez | `error_recovery → listening` | "Te escucho..." | — |
| 5 | "Llama a Pepito" | (continúa como flujo 1) | — | — |

**Política de fallos consecutivos:**
- 1er fallo: "No te he oído bien, ¿puedes repetirlo?".
- 2do fallo: "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto".
- 3er fallo: "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo". Cancela el ciclo, vuelve a `idle`.

Esto evita el bucle infinito de "no te entiendo" que es lo más frustrante para un usuario mayor.

### Flujo 7 — FunctionGemma devuelve JSON inválido o acción inexistente

Este flujo es para errores internos del modelo. No debe ocurrir en condiciones normales, pero es el "salvavidas" que evita que la app se cuelgue ante una salida malformada.

| # | Usuario | Sistema | Pantalla | Voz |
|---|---|---|---|---|
| 1 | Pulsa botón | `idle → listening` | "Te escucho..." | — |
| 2 | "Tradúceme esto al italiano" (función no existe en Fase 1) | STT transcribe. | Transcripción | — |
| 3 | (espera) | FunctionGemma intenta mapear → puede devolver: (a) acción inexistente, (b) JSON con campos faltantes, (c) JSON sintácticamente roto. Validador detecta el problema. `processing → error_recovery`. | "Un momento..." | — |
| 4 | (espera) | — | "No sé hacer eso todavía" | "Eso no lo sé hacer todavía. Pulsa el botón y pídeme otra cosa, o di 'ayuda' para que te cuente lo que sí sé hacer" |
| 5 | (decisión del usuario) | Si pulsa de nuevo y dice "ayuda", se invoca `help`. | — | — |

**Manejo técnico:**
- El parser valida la salida de FunctionGemma contra el JSON Schema del catálogo de funciones.
- Si falla la validación, **no se reintenta automáticamente** (puede producir bucles caros). Se informa al usuario.
- El comando fallido se guarda en el log de comandos fallidos (visible desde el menú de configuración) para que Fran lo revise y, si es una función que sí debería existir, la añada al catálogo de la próxima versión.

**Distinción importante:** este flujo cubre el caso "el modelo no me da algo válido". El caso "el modelo me da algo válido pero la función no existe en Fase 1" se trata igual de cara al usuario, pero el log lo etiqueta distinto para análisis posterior.

### Estados y transiciones (diagrama de máquina)

```
              ┌──── pulsa botón ───────────►  listening
              │                                  │
              │                              STT termina
              │                                  │
              │                                  ▼
            idle ◄───── lectura/ejec OK ──── processing
              ▲                                  │
              │                          ┌───────┼───────┐
              │                          │       │       │
              │                          ▼       ▼       ▼
              │                    executing  confirming error_recovery
              │                          │       │       │
              │                          │   sí/no       │
              │                          │       │       │
              └──────────────────────────┴───────┴───────┘
                          (todo termina en idle)
```

Cualquier estado puede ser interrumpido por una nueva pulsación de botón, que cancela lo en curso y vuelve a `listening`. Esto es importante: tu padre debe poder "cortar" a Curro si está leyendo algo largo y prefiere otra cosa.

## 7. Modelo de aprendizaje (alias y preferencias)

La app mantiene una base de datos local (SQLite o DataStore) con:

- **Alias de contactos**: la primera vez que tu padre dice "llama a mi hija", la app pregunta una vez "¿quién es tu hija de tus contactos?". Se almacena el alias. No vuelve a preguntar.
- **Apps favoritas implícitas**: las que abre más se promueven al grid principal del launcher.
- **Horarios de uso**: para futuras funciones proactivas (ej.: recordatorios).
- **Comandos fallidos**: se guardan localmente para depuración. Útil cuando Fran revise qué cosas no entendió la app.

**Fase de onboarding (post-prototipo):** un asistente guiado que pregunta por las personas importantes y sus relaciones. Pero en el prototipo, la app aprende sobre la marcha y Fran puede pre-cargar alias manualmente vía el menú de configuración (sección 8).

## 8. Manejo de llamadas entrantes (función opcional)

Por defecto, **el prototipo no se mete en las llamadas entrantes**. Android las maneja como siempre y suena el ringtone del sistema con la pantalla nativa de llamada.

Hay una opción configurable, **desactivada por defecto**, que activa el "modo asistente de llamadas":

- Cuando entra una llamada, la app anuncia por voz quién llama: *"Te está llamando Pepito"* (resuelto desde contactos, o "número desconocido" si no está guardado).
- Si tiene alias aprendido, lo usa: *"Te está llamando tu hija María"*.
- El usuario puede contestar diciendo "sí" / "coge" / "responde" o rechazar con "no" / "cuelga".
- Acepta el toque manual también, no sustituye, complementa.

Requiere los permisos `READ_PHONE_STATE` y un `InCallService` registrado. Esto es más invasivo a nivel de sistema y por eso queda opt-in: si algo falla, no afecta a la capacidad básica del teléfono de recibir llamadas.

**Decisión de prototipo:** se implementa la opción pero se entrega desactivada. Fran la activa manualmente desde el menú de configuración solo cuando quiera probarla con su padre. Así se valida primero lo crítico (botón, voz, acciones básicas) antes de tocar el flujo de llamadas.

## 9. Menú de configuración (acceso para Fran)

Pantalla oculta a usuario final, accesible mediante gesture deliberada: **pulsar 5 veces seguidas el reloj del launcher** en menos de 3 segundos. Esto evita que tu padre la abra por accidente y a la vez permite a Fran entrar rápido cuando esté en casa.

Contenido del menú:

- **Alias de contactos**: lista de aliases aprendidos o pre-cargados. Permite añadir manualmente ("mi nieta = María Pérez García"), editar, eliminar.
- **Apps favoritas del launcher**: qué 4-6 apps aparecen grandes en home. Por defecto auto-detectadas por uso, editables manualmente.
- **Voz del TTS**: selector de voz instalada, velocidad de habla (en mayores conviene ralentizar ~10-15%), tono.
- **Modo asistente de llamadas**: toggle on/off (sección 8).
- **Umbrales de confianza**: dos sliders (0-1) para los valores que separan ejecución directa, confirmación, y aclaración. Defaults: 0.85 y 0.60. Útil si Fran observa que Curro confirma demasiado o demasiado poco.
- **Confirma siempre**: toggle que fuerza confirmación para toda acción `conditional`, ignorando la confianza. Útil los primeros días de uso, hasta ganar confianza en el sistema.
- **Logs de comandos fallidos**: últimos 50 comandos que la app no entendió o ejecutó mal, con timestamp. Útil para depurar.
- **Modo "envíame los fallos"**: toggle on/off para enviar logs anonimizados a Fran (sección 11).
- **Reset de aprendizaje**: borra alias y preferencias aprendidas. Útil para volver a estado limpio si algo se ha aprendido mal.
- **Versión y diagnóstico**: versión de la app, estado de los modelos (cargados, en memoria, latencia última inferencia), permisos concedidos.

## 10. Permisos Android requeridos

| Permiso | Justificación | Si lo deniega |
|---|---|---|
| `RECORD_AUDIO` | STT | Sin app |
| `READ_CONTACTS` | Resolver nombres a contactos | Sin función "llamar a..." |
| `CALL_PHONE` | Iniciar llamadas | Sin función "llamar a..." |
| `READ_SMS` (opcional) | Leer SMS | Sin función "leer SMS" |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Leer WhatsApp | Sin función "leer WhatsApp" |
| `POST_NOTIFICATIONS` | Icono de notificación del foreground service de warm-up del modelo | El servicio sigue corriendo; el icono no aparece en la barra |
| `FOREGROUND_SERVICE` | Mantener el modelo cargado en memoria (US-023 / SF-3.5) | El servicio no puede arrancar (manifest-required en Android 9+) |
| `FOREGROUND_SERVICE_DATA_SYNC` | Tipado obligatorio del foreground service en Android 14+ (US-023 / SF-3.5) | El servicio no puede arrancar en Android 14+ |
| `SYSTEM_ALERT_WINDOW` (eval) | Mostrar feedback sobre cualquier app | Feedback solo en launcher |
| `QUERY_ALL_PACKAGES` | Listar apps para abrirlas por nombre | Solo apps pre-configuradas |
| `READ_PHONE_STATE` (opcional) | Detectar llamadas entrantes | Sin modo asistente de llamadas |
| `ANSWER_PHONE_CALLS` (opcional) | Contestar por voz | Sin modo asistente de llamadas |
| `MANAGE_OWN_CALLS` (opcional) | Operaciones de `InCallService` requeridas en algunas builds de HyperOS para que el binding sobreviva. Solicitado junto con los otros dos al activar el modo asistente de llamadas. | Sin modo asistente de llamadas |
| `BIND_INCALL_SERVICE` *(declarada en el `<service>`, no en `<uses-permission>`)* | Permiso de sistema requerido por el atributo `android:permission` del `CurroInCallService`. Garantiza que solo el subsistema Telecom puede hacer bind. No aparece en el panel de permisos del usuario. | El servicio no puede ser bindeado por Telecom |
| `INTERNET` *(solo release)* | Firebase Crashlytics/Analytics + PostHog — ver §12 | SDKs de telemetría fallan; la app sigue funcionando |
| `ACCESS_NETWORK_STATE` *(solo release)* | Requerido transitivamente por los SDKs de telemetría | SDKs de telemetría fallan; la app sigue funcionando |
| `WAKE_LOCK` *(solo release)* | Requerido transitivamente por los SDKs de telemetría | SDKs de telemetría fallan; la app sigue funcionando |

El launcher en sí no requiere permiso explícito; se declara con `CATEGORY_HOME` en el manifest y Android propone al usuario hacerlo default.

Los permisos opcionales solo se solicitan si Fran activa el toggle correspondiente en el menú de configuración. El usuario final nunca ve un prompt de permiso para algo que no esté usando.

**Modo asistente de llamadas — garantía estructural de OFF** (SF-8.7 / US-056): el `CurroInCallService` se declara con `android:enabled="false"` en el manifest. Mientras el toggle esté apagado, el framework Telecom **no lo descubre** (no aparece en `queryIntentServices(Intent("android.telecom.InCallService"))`) y la telefonía es 100 % nativa por construcción, no por un check de runtime. Al activar el toggle, `IncomingCallModeController.enable()` invoca `PackageManager.setComponentEnabledSetting(..., COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP)`; al desactivarlo, el mismo controlador llama con `COMPONENT_ENABLED_STATE_DISABLED`. El `IncomingCallModeOffInvariantTest` instrumentado verifica el invariante en cada cambio de estado.

## 11. Diseño UX del launcher

**Pantalla principal (siempre visible al pulsar home):**

```
┌─────────────────────────────┐
│         12:47               │  ← reloj grande
│      Miércoles 13 mayo      │
│                             │
│   ┌─────────────────────┐   │
│   │                     │   │
│   │     🎤 CURRO        │   │  ← botón principal
│   │                     │   │
│   └─────────────────────┘   │
│                             │
│   [WhatsApp]  [Llamadas]    │  ← 4-6 apps grandes
│   [Cámara]    [Fotos]       │
│                             │
│   [Más apps]                │  ← acceso secundario
└─────────────────────────────┘
```

**Mientras escucha:** la pantalla se vuelve azul claro, aparece "Te estoy escuchando..." y la transcripción en tiempo real abajo en texto grande.

**Mientras procesa:** "Un momento..." con un indicador visual no animado (animaciones complejas distraen).

**Mientras ejecuta:** habla por voz lo que está haciendo. "Llamando a Pepito" / "Marcos te dice: te espero a las siete".

**Confirmación:** "Voy a llamar a Pepito. ¿Confirmas?". Tu padre dice "sí" o "no". También aparecen dos botones gigantes en pantalla por si prefiere tocar.

## 12. Privacidad

*(Revisado en v1.1 — US-008. La v1.0 decía "nada sale del dispositivo". Esta sección reemplaza esa afirmación.)*

### 12.1 Datos que nunca salen del dispositivo

Los siguientes datos **no se transmiten nunca**, en ningún build, bajo ninguna circunstancia:

- Audio grabado.
- Texto transcrito (el resultado del STT).
- Contenido de mensajes leídos en voz alta.
- Lista de contactos y alias aprendidos.
- Historial de comandos (incluyendo el log de comandos fallidos).
- Números de teléfono, direcciones de correo, o cualquier otro dato de identificación personal del usuario o sus contactos.

Esto no es una opción configurable: estos datos están estructuralmente excluidos de los eventos de telemetría mediante una lista blanca de propiedades permitidas (`TelemetryGuardrail`) que rechaza cualquier valor con forma de PII antes de que llegue al SDK.

### 12.2 Telemetría de crashes y producto (nueva en v1.1)

El prototipo incluye **Firebase Crashlytics, Firebase Analytics y PostHog** para telemetría de crashes y producto. Esta decisión se toma de forma explícita y con las siguientes salvaguardas:

- Los SDKs de telemetría **solo están presentes en builds de release**. El bytecode de Firebase y PostHog no existe en el APK de debug — la separación es estructural (`releaseImplementation`), no solo una flag de runtime.
- El permiso `INTERNET` se declara **únicamente en el manifest de la variante release** (`app/src/release/AndroidManifest.xml`). El APK de debug no tiene permiso `INTERNET`.
- Todos los eventos pasan por `TelemetryGuardrail` antes de llegar al SDK. Si un evento incluye una propiedad no registrada en la lista blanca, o cuyo valor parece PII (email, teléfono, nombre completo, o cadena larga que podría ser una transcripción), el evento se descarta. No hay escape hatch.
- La colección de AdId de Google Analytics está desactivada (`google_analytics_adid_collection_enabled = false` en el manifest).
- PostHog tiene desactivados Session Replay, deep link capture y screen view capture (`sessionReplay = false`, `captureDeepLinks = false`, `captureScreenViews = false`).

Los eventos de telemetría actuales cubren exclusivamente métricas técnicas de ingeniería: tiempos de carga del modelo, tasas de error del STT, resultados de handlers (sin contenido), y la acción clasificada por FunctionGemma (sin el texto transcrito). La lista completa y actualizada se encuentra en `TelemetryGuardrail.ALLOWED_PROPS`.

### 12.3 Exportador de fallos anonimizados (aplazado)

La spec v1.0 preveía un modo "envíame los fallos" en el menú de configuración para que Fran recibiera logs anonimizados. Esta funcionalidad **se aplaza a una fase posterior** (etiquetada `FailedCommandsExporter` en el backlog). La telemetría de Firebase/PostHog cubre las necesidades de depuración del prototipo sin necesidad de un canal adicional. Cuando se implemente, pasará por los mismos controles de `TelemetryGuardrail` y requerirá consentimiento explícito de Fran (no del usuario final).

## 13. Criterios de validación del prototipo

El prototipo se considera **validado** si, tras una semana de uso real por parte de tu padre:

- Usa la app al menos 5 veces al día sin que Fran se lo recuerde.
- Completa con éxito al menos 3 de los siguientes flujos sin ayuda: leer WhatsApp, llamar a un contacto, abrir una app, hacer un cálculo, saber la hora/fecha.
- Reporta verbalmente (a Fran, en visita o llamada) que le resulta útil.

Se considera **no validado** si:
- La latencia o los fallos lo frustran y deja de usarla.
- La voz robótica le resulta desagradable hasta el punto de evitar la app.
- Las acciones críticas (llamar, enviar) fallan con frecuencia que él no perdona.

## 14. Resumen ejecutivo para implementación

Para arrancar el prototipo con tu sistema de subagentes, lo esencial:

**Stack técnico previsto:**
- Lenguaje: Kotlin.
- Min SDK: Android 12 (API 31) — STT offline disponible y APIs modernas de notificaciones.
- Target SDK: Android 14 (API 34) o superior.
- Modelos: FunctionGemma 270M int8 (~288 MB) + Gemma 3n E2B int4 (~2 GB activos).
- Runtime de inferencia: LiteRT (anteriormente TFLite) + MediaPipe LLM Inference API.
- Hardware target: Xiaomi Redmi 15 5G (Snapdragon 6s Gen 3, 8 GB RAM, Android 15 + HyperOS 2/3).

**Componentes a construir (orden sugerido):**

1. **Launcher base** con `CATEGORY_HOME` declarado, reloj grande, botón principal, grid de apps favoritas. Sin asistente todavía. Validar que tu padre lo entiende como reemplazo del launcher de fábrica.
2. **Pipeline de voz** (STT → log en pantalla → TTS). Sin modelo de decisión. Solo confirmar que el ciclo de captura y respuesta funciona en el dispositivo real.
3. **Integración de FunctionGemma** con el catálogo de Fase 1, sin handlers reales todavía. Salida visible en pantalla del JSON devuelto para verificar que las intenciones se mapean bien.
4. **Handlers de Fase 1** uno por uno, en este orden: `tell_time`, `open_app`, `calculate`, `help`, `read_last_whatsapp`, `read_all_unread_whatsapp`, `call_contact`. Los cuatro primeros validan la arquitectura sin riesgo. Los tres últimos tocan permisos sensibles.
5. **Máquina de estados completa** (idle/listening/processing/confirming/executing/error_recovery) con interrupción por pulsación.
6. **Sistema de confianza graduada** (sección 4.3) y flujo de confirmación.
7. **Sistema de alias y aprendizaje** (sección 7 + flujo 4).
8. **Menú de configuración** (sección 9) con gesture de 5 toques en el reloj.
9. **Gemma 3n** solo en pasos donde realmente se necesite generación. En Fase 1 puede no ser estrictamente necesario; evalúa si vale la pena cargarlo o esperar a Fase 2.

**Decisiones cerradas en esta spec que NO se replantean durante implementación:**
- Botón como único disparador (no hotword).
- Curro como nombre.
- Voz masculina del TTS de Android.
- Todo on-device, sin cloud.
- Launcher (no app normal).
- Llamadas entrantes opt-in, desactivadas por defecto.
- `set_volume` y `send_whatsapp_reply` en Fase 2, no en prototipo.

**Decisiones explícitamente abiertas, esperando datos reales del prototipo:**
- Umbrales exactos de confianza (defaults 0.85 y 0.60, ajustables).
- Calidad de voz del TTS nativo (¿hace falta ElevenLabs?).
- Latencia real de Gemma 3n en Redmi 15 (puede obligar a aplazar funciones de Fase 3 que dependan de él).
- Si el flujo "botón → hablar" es natural o tu padre acabaría prefiriendo hotword.
- Variante exacta del Redmi 15 (4GB vs 8GB RAM) — confirmar antes de empezar.

**Riesgos identificados:**
- **Entrega de modelos (decisión cerrada para prototipo, US-019 / SF-3.1):** side-load vía `adb push` a `/data/local/tmp/curro-models/`. Ruta configurable en `local.properties` (`CURRO_MODEL_BASE_PATH`), expuesta en runtime como `BuildConfig.MODEL_BASE_PATH`. Un SF posterior (post-prototipo) introducirá entrega empaquetada / Play Asset Delivery sin tocar el seam `data/ml/ModelFiles.kt`. Procedimiento completo en `models/README.md`.
- Variante de 4GB RAM del Redmi 15: Gemma 3n marginal, replantear arquitectura si es el caso.
- HyperOS de Xiaomi tiene restricciones agresivas de background processes que pueden matar el foreground service del modelo. Whitelist manual de Curro en ajustes de batería será necesaria.
- Voces masculinas españolas del TTS de Android son limitadas en calidad. Plan B: ElevenLabs.
- WhatsApp puede cambiar el formato de sus notificaciones, rompiendo el parsing. Defensa: parser robusto con tests y fallback a "no he podido leer el mensaje".

**Lo primero que validar con tu padre:**
1. Que entiende el launcher (reloj + botón + apps).
2. Que pulsa el botón y habla sin instrucciones.
3. Que la voz de Curro le resulta agradable y comprensible.
4. Que las tres primeras funciones que pruebe (sugerencia: `tell_time`, `open_app` con su WhatsApp, `call_contact` a un contacto sin ambigüedad) funcionan a la primera.

Si esos cuatro puntos pasan, el resto del prototipo es ampliación. Si alguno falla, hay que parar y replantear antes de seguir.

---

*Spec lista para iteración con Claude Code. Cualquier ambigüedad descubierta durante implementación → volver a este documento y refinar la sección correspondiente, manteniendo trazabilidad de versiones.*

---

## Historial de revisiones

| Versión | Fecha | Autor | Cambios |
|---|---|---|---|
| 1.0 | Mayo 2026 | Fran | Spec inicial del prototipo — arquitectura, catálogo de funciones Fase 1–4, flujos, permisos, UX, privacidad |
| 1.1 | Mayo 2026 | android-developer (US-008) | §12 reescrito: telemetría Firebase + PostHog mantenida con salvaguardas estructurales (release-only, `TelemetryGuardrail`, INTERNET solo en release manifest, AdId off). `FailedCommandsExporter` aplazado. §10 añadidas filas INTERNET / ACCESS_NETWORK_STATE / WAKE_LOCK (solo release). |
| 1.2 | Mayo 2026 | voice-pipeline-engineer (US-056 / SF-8.7) | §10 amplía las filas del modo asistente de llamadas: `MANAGE_OWN_CALLS` (nuevo), `BIND_INCALL_SERVICE` (declarado en el `<service>`, no en `<uses-permission>`). Añadida la nota "garantía estructural de OFF" — el `CurroInCallService` se declara `android:enabled="false"` y se activa en runtime vía `setComponentEnabledSetting`, garantizando que con el toggle apagado Telecom no lo descubre y la telefonía es 100 % nativa por construcción. |
