# US-031 — SF-4.7 · `read_last_whatsapp` handler

> **Spec trace:** spec §5 (catalog entry `read_last_whatsapp`), spec §6
> flow 5 (reading-messages flow — single-message variant).
> **Master-plan:** SF-4.7.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-030 (notification infrastructure), US-025 (handler interface).
> **Size:** S.
> **Skills:** `function-catalog`, `platform-integrations`, `brand-design`, `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `read_last_whatsapp` handler — speaks the latest unread WhatsApp |
| **US ID** | US-031 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-developer |

---

## 1. Summary

Read the latest unread WhatsApp aloud. Optional `sender` param filters the
cache by sender; absent → latest overall. Each `WhatsAppMessage.Classification`
maps to a distinct spoken line (TEXT → quoted body; EMOJI → "te ha mandado
un emoji"; VOICE_NOTE → "te ha mandado un audio"; IMAGE → "te ha mandado
una foto"). On a parse-miss-only cache → `copy_whatsapp_parse_miss`. On no
unread → `copy_no_unread`. On notification-access missing →
`copy_perm_missing_notifs`.

Why this matters for *this* user: this is the **one feature that justifies
the prototype**. Reading the latest WhatsApp is the user's most pressing
daily friction with the phone.

---

## 2. Scope

**In scope:**

- `handler/ReadLastWhatsAppHandler.kt`.
- `HandlerModule.kt` — append the `@Binds @IntoMap @StringKey("read_last_whatsapp")` line.
- New `strings.xml` entries: `copy_read_last_text`, `copy_read_last_emoji`,
  `copy_read_last_voice`, `copy_read_last_image`, `copy_no_unread_from`.
- ≥ 10 JVM tests with a fake `NotificationRepository` and fake
  `NotificationAccessGate`.

**Out of scope:**

- Reading multiple messages — SF-4.8 (`read_all_unread_whatsapp`).
- Resolving aliases ("léeme lo de mi hija") — Phase 7. Phase 4 uses the
  literal `sender` string from FunctionGemma.
- Marking a message read on the device — out of Curro's contract; the user
  re-reads in WhatsApp if needed.

---

## 3. User Flows

### Flow 1: "léeme el último mensaje" — happy path

1. User presses mic → STT → "léeme el último mensaje".
2. FunctionGemma → `{action: "read_last_whatsapp", params: {}, confidence: 0.95}`.
3. Handler: gate granted; `cache.allUnread.first()` returns `[msg]`.
4. Classification == TEXT → `"Tienes un mensaje de Pepito: Te espero a las
   siete"` via `copy_read_last_text`.
5. State → `Idle`.

### Flow 2: "qué dice Pepito" — with sender filter

1. FunctionGemma → `{params: {sender: "Pepito"}}`.
2. Handler filters cache by sender (case + accent insensitive).
3. → single most-recent message from Pepito → speaks per Classification.

### Flow 3: "qué dice mi hija" — Phase 4 sender = literal alias

1. FunctionGemma → `{params: {sender: "mi hija"}}` (the model didn't resolve
   the alias — Phase 7 wires that).
2. Handler filters by literal `"mi hija"` — finds nothing.
3. → `"No tienes mensajes nuevos de mi hija."` via `copy_no_unread_from`.

### Flow 4: Empty cache + parse-miss > 0

1. `cache.allUnread.first().isEmpty() == true` AND `parseMissCount.first() > 0`.
2. → `copy_whatsapp_parse_miss`. ("Tienes mensajes nuevos pero no he podido
   leerlos bien.")

### Flow 5: Empty everything

1. `allUnread` empty AND `parseMissCount == 0`.
2. → `copy_no_unread`. ("No tienes mensajes nuevos.")

### Flow 6: Notification access missing

1. `gate.isGranted() == false`.
2. → `Failed(copy_perm_missing_notifs, NotificationAccessMissing)`.

### Flow 7: Classification == EMOJI

1. `msg.classification == EMOJI`.
2. → `"Tienes un mensaje de Pepito: te ha mandado un emoji."` via
   `copy_read_last_emoji`.

---

## 4. Function-catalog Impact

**No catalog change** — `read_last_whatsapp` already exists.

---

## 5. FSM States Touched

`Processing → Speaking → Idle`. `needs_confirmation: NO`.

---

## 6. Android System Integrations & Permissions

| Integration | Why |
|---|---|
| `NotificationRepository` (US-030) | Read the cache. |
| `NotificationAccessGate` (US-030) | Gate check. |
| `Context.getString` | Build the spoken phrase. |

No new permissions; the `BIND_NOTIFICATION_LISTENER_SERVICE` already lands
in US-030.

---

## 7. On-device-model Impact

No model impact. Phase 5+ may add the cache summary to `PromptContext`.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/
└── handler/
    └── ReadLastWhatsAppHandler.kt
```

### 8.2 `ReadLastWhatsAppHandler.kt`

```kotlin
package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.apps.curroNormalize
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.model.WhatsAppMessage.Classification
import com.curro.app.domain.repository.NotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ReadLastWhatsAppHandler
    @Inject
    constructor(
        private val notifications: NotificationRepository,
        private val accessGate: NotificationAccessGate,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "read_last_whatsapp"

        @Suppress("ReturnCount")
        override suspend fun handle(call: FunctionCall): HandlerResult {
            if (!accessGate.isGranted()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_perm_missing_notifs),
                    CurroError.NotificationAccessMissing,
                )
            }

            val all = notifications.allUnread.first()
            val misses = notifications.parseMissCount.first()

            if (all.isEmpty() && misses > 0) {
                return HandlerResult.Spoken(context.getString(R.string.copy_whatsapp_parse_miss))
            }
            if (all.isEmpty()) {
                return HandlerResult.Spoken(context.getString(R.string.copy_no_unread))
            }

            val senderQuery = (call.params["sender"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val pool =
                if (senderQuery == null) {
                    all
                } else {
                    val normalised = senderQuery.curroNormalize()
                    all.filter {
                        it.sender.curroNormalize() == normalised ||
                            it.chatTitle.curroNormalize() == normalised ||
                            normalised in it.sender.curroNormalize() ||
                            normalised in it.chatTitle.curroNormalize()
                    }
                }
            if (pool.isEmpty() && senderQuery != null) {
                return HandlerResult.Spoken(
                    context.getString(R.string.copy_no_unread_from, senderQuery),
                )
            }
            if (pool.isEmpty()) {
                return HandlerResult.Spoken(context.getString(R.string.copy_no_unread))
            }
            val latest = pool.maxByOrNull { it.timestamp } ?: pool.first()
            return HandlerResult.Spoken(speechFor(latest))
        }

        private fun speechFor(msg: WhatsAppMessage): String =
            when (msg.classification) {
                Classification.TEXT ->
                    context.getString(R.string.copy_read_last_text, msg.sender, msg.text)
                Classification.EMOJI ->
                    context.getString(R.string.copy_read_last_emoji, msg.sender)
                Classification.VOICE_NOTE ->
                    context.getString(R.string.copy_read_last_voice, msg.sender)
                Classification.IMAGE ->
                    context.getString(R.string.copy_read_last_image, msg.sender)
                Classification.OTHER ->
                    context.getString(R.string.copy_whatsapp_parse_miss)
            }
    }
```

### 8.3 `strings.xml` — adds

```xml
<!-- US-031 (SF-4.7) — single-message read, text body. %1$s sender, %2$s body. -->
<string name="copy_read_last_text">Tienes un mensaje de %1$s: %2$s</string>

<!-- US-031 (SF-4.7) — single-message read, emoji-only body. %1$s sender. -->
<string name="copy_read_last_emoji">Tienes un mensaje de %1$s: te ha mandado un emoji.</string>

<!-- US-031 (SF-4.7) — single-message read, voice note. %1$s sender. -->
<string name="copy_read_last_voice">Tienes un mensaje de %1$s: te ha mandado un audio.</string>

<!-- US-031 (SF-4.7) — single-message read, image. %1$s sender. -->
<string name="copy_read_last_image">Tienes un mensaje de %1$s: te ha mandado una foto.</string>

<!-- US-031 (SF-4.7) — no unread from a specific sender. %1$s sender. -->
<string name="copy_no_unread_from">No tienes mensajes nuevos de %1$s.</string>
```

### 8.4 `HandlerModule.kt` — append

```kotlin
@Binds
@IntoMap
@StringKey("read_last_whatsapp")
abstract fun bindReadLastWhatsAppHandler(impl: ReadLastWhatsAppHandler): FunctionHandler
```

---

## 9. Acceptance Criteria

- [ ] `handler/ReadLastWhatsAppHandler.kt` exists at the documented path.
- [ ] `HandlerModule` gains the `@Binds @IntoMap @StringKey("read_last_whatsapp")` line.
- [ ] `strings.xml` gains the 5 new entries.
- [ ] Notification access missing → `Failed(copy_perm_missing_notifs,
      NotificationAccessMissing)`.
- [ ] Empty cache + no parse-miss → `copy_no_unread`.
- [ ] Empty cache + parse-miss > 0 → `copy_whatsapp_parse_miss`.
- [ ] Single TEXT msg, no sender filter → `"Tienes un mensaje de Pepito:
      Te espero a las siete"`.
- [ ] Sender filter hit → reads that sender's latest.
- [ ] Sender filter miss → `copy_no_unread_from` with the sender name.
- [ ] EMOJI msg → `"… te ha mandado un emoji."`.
- [ ] VOICE_NOTE → `"… te ha mandado un audio."`.
- [ ] IMAGE → `"… te ha mandado una foto."`.
- [ ] OTHER classification → falls through to `copy_whatsapp_parse_miss`.
- [ ] No new permissions; no manifest changes (US-030 added them); no new
      dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green.

---

## 10. Senior-UX & Copy

| String ID | Spanish | Voice notes |
|---|---|---|
| `copy_read_last_text` (NEW) | "Tienes un mensaje de %1$s: %2$s" | The body is read verbatim — preserves the user's friend's words. No trailing period (the body usually ends with its own punctuation; the TTS engine paces it). |
| `copy_read_last_emoji` (NEW) | "Tienes un mensaje de %1$s: te ha mandado un emoji." | Plain, honest, no apology. |
| `copy_read_last_voice` (NEW) | "Tienes un mensaje de %1$s: te ha mandado un audio." | "audio" (not "nota de voz") — the user's word. |
| `copy_read_last_image` (NEW) | "Tienes un mensaje de %1$s: te ha mandado una foto." | "foto" (not "imagen") — the user's word. |
| `copy_no_unread_from` (NEW) | "No tienes mensajes nuevos de %1$s." | Says the sender back so the user knows what Curro understood. |

Every line is **spoken AND shown** via the existing Speaking overlay.

---

## 11. Design Notes

No new visual surface.

---

## 12. Performance Considerations

- `notifications.allUnread.first()` reads the in-memory cache — sub-ms.
- Filter is `O(n)` over at most ~50 unread messages; sub-ms.
- No allocations of concern.

---

## 13. Testing Requirements

**`ReadLastWhatsAppHandlerTest.kt`** — pure JVM with Robolectric for
`Context.getString`.

Fakes:

- `FakeNotificationRepository(unread, missCount)`.
- `FakeNotificationAccessGate(granted)`.

Cases (≥ 10):

1. Gate granted, cache empty, miss == 0 → `copy_no_unread`.
2. Gate granted, cache empty, miss == 2 → `copy_whatsapp_parse_miss`.
3. Gate granted, cache has 1 TEXT msg → `copy_read_last_text` with the
   right sender + body.
4. Gate granted, cache has 3 msgs from Pepito (different timestamps), no
   sender param → returns the most-recent.
5. Gate granted, cache has msgs from Pepito + Lucía, `sender = "Pepito"`
   → Pepito's latest.
6. Gate granted, cache has msgs from Pepito + Lucía, `sender = "PEPITO"`
   (uppercase) → matches Pepito (case-insensitive).
7. Gate granted, cache has msgs from José, `sender = "jose"` (no accent)
   → matches (accent-stripped).
8. Gate granted, cache has msgs from Pepito only, `sender = "Lucía"` →
   `copy_no_unread_from` with `"Lucía"`.
9. Gate granted, cache has 1 EMOJI msg → `copy_read_last_emoji`.
10. Gate granted, cache has 1 VOICE_NOTE → `copy_read_last_voice`.
11. Gate granted, cache has 1 IMAGE → `copy_read_last_image`.
12. Gate granted, cache has 1 OTHER → `copy_whatsapp_parse_miss` (fallback).
13. Gate **denied** → `Failed(copy_perm_missing_notifs, NotificationAccessMissing)`.

**On-device verification** on the Redmi 15:

- Send a WhatsApp text → say "léeme el último mensaje" → Curro reads it.
- Send an emoji-only → Curro says "te ha mandado un emoji".
- Send a voice note → Curro says "te ha mandado un audio".
- Send an image → Curro says "te ha mandado una foto".
- Revoke access → say "léeme el último mensaje" → Curro speaks
  `copy_perm_missing_notifs`.

---

## 14. Implementation Notes — Order of Operations

1. Verify US-030 is committed.
2. Add the 5 new `strings.xml` entries.
3. Create `handler/ReadLastWhatsAppHandler.kt`.
4. Append the handler `@Binds @IntoMap @StringKey("read_last_whatsapp")` to
   `HandlerModule`.
5. Write `ReadLastWhatsAppHandlerTest`.
6. `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
7. Smoke-test on the Redmi 15.
8. Commit as `feat: add read_last_whatsapp handler (US-031 / SF-4.7)`.

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
