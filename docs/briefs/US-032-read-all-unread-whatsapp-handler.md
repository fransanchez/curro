# US-032 — SF-4.8 · `read_all_unread_whatsapp` handler

> **Spec trace:** spec §5 (catalog entry `read_all_unread_whatsapp`), spec §6
> flow 5 (reading-messages flow, canonical grouped-by-sender example).
> **Master-plan:** SF-4.8.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-030 (notification infrastructure), US-025 (handler interface).
> **Size:** M.
> **Skills:** `function-catalog`, `platform-integrations`, `brand-design`, `testing-patterns`, `git-workflow`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `read_all_unread_whatsapp` handler — grouped-by-sender read of every unread WhatsApp |
| **US ID** | US-032 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-developer |

---

## 1. Summary

Read every unread WhatsApp aloud, **grouped by sender** (not by time, per
spec §6 flow 5). For ≤ 8 unread, build a single Spanish phrase: a header
that counts the messages per sender, then each group with its
sender-introducer ("Empiezo con Pepito:" / "De Lucía:"), then each
message body composed per `Classification`. For > 8 unread, speak
`copy_many_unread` and stop — the "todos / solo de alguien" follow-up is
Phase 5/6 (it needs a confirmation FSM round-trip).

Why this matters for *this* user: when the user catches up after a few
hours, he has 3–10 WhatsApps from 2–3 people. Curro reads them in the
order a human would — "3 de Pepito y 1 de Lucía; empiezo con Pepito: …".
That phrasing was hand-picked by the spec because it matches how the
user's daughter (Lucía) would tell him.

---

## 2. Scope

**In scope:**

- `handler/ReadAllUnreadWhatsAppHandler.kt`.
- `HandlerModule.kt` — append the `@Binds @IntoMap @StringKey("read_all_unread_whatsapp")` line.
- New `strings.xml` entry: `copy_reading_summary_three_plus`. Reuse the
  existing summary / starts-with / from / message / emoji / voice / image
  COPY entries from US-005 + US-031.
- ≥ 15 JVM tests with a fake `NotificationRepository` and gate.

**Out of scope:**

- The "solo los de alguien" follow-up STT for the > 8 path — Phase 5/6.
- Gemma 3n summarisation — Phase 9.
- Reading group-chat bodies (a single MS group SBN already expands per
  message in US-030's parser; SF-4.8 reads them like any 1:1 sender —
  the `sender` per `WhatsAppMessage` IS the per-message `Person.name`).

---

## 3. User Flows

### Flow 1: 4 unread, 2 senders — spec §6 flow-5 canonical

1. User → "léeme los mensajes" → STT.
2. FunctionGemma → `{action: "read_all_unread_whatsapp", confidence: 0.96}`.
3. Cache has 3 msgs from Pepito + 1 from Lucía.
4. Handler groups → header: `"Tienes 3 mensajes de Pepito y 1 mensaje de Lucía."`
   (`copy_reading_summary_multi_sender`).
5. First group: `"Empiezo con Pepito:"` + bodies joined by `". "`.
6. Next: `"De Lucía:"` + body.
7. Full output: `"Tienes 3 mensajes de Pepito y 1 mensaje de Lucía.
   Empiezo con Pepito: Te espero a las siete. Trae el pan. Y vino si
   puedes. De Lucía: Mañana te llamo, papá."`.

### Flow 2: 1 unread

1. Cache has 1 msg from Pepito.
2. Header: `copy_reading_summary_one` → `"Tienes 1 mensaje de Pepito."`.
3. Body: `"Empiezo con Pepito: Te espero a las siete."`.

### Flow 3: > 8 unread

1. Cache has 12 msgs.
2. Handler → `Spoken(copy_many_unread)` → `"Tienes muchos mensajes. ¿Te los
   leo todos o solo los de alguien?"`. STOP — no further reading. The
   Phase-5/6 confirm-FSM wires the follow-up; for Phase 4, this is the
   final spoken line.

### Flow 4: 3 senders

1. Cache has 1 msg each from Pepito, Lucía, Carmen.
2. Header: `copy_reading_summary_three_plus` (NEW) → `"Tienes mensajes
   nuevos de Pepito, Lucía y Carmen."`.
3. Body: per-sender reading order (most-recent group first), each with
   `copy_reading_starts_with` / `copy_reading_from`.

### Flow 5: Empty / parse-miss-only / access-missing

- Empty + miss == 0 → `copy_no_unread`.
- Empty + miss > 0 → `copy_whatsapp_parse_miss`.
- Access denied → `Failed(copy_perm_missing_notifs, NotificationAccessMissing)`.

### Flow 6: 6 unread from 4 senders (mixed)

1. Cache: 3 msgs from Pepito, 1 from Lucía, 1 from Carmen, 1 from Marisa.
2. Header — 4 senders → `copy_reading_summary_three_plus` reads the first
   three sender names (pinned: top-3 by most-recent timestamp): `"Tienes
   mensajes nuevos de Pepito, Lucía y Carmen."`. **Marisa's message still
   gets read in the body** — the header just summarises the first three.
3. Body iterates every group in most-recent-active-first order.

---

## 4. Function-catalog Impact

**No catalog change** — `read_all_unread_whatsapp` already exists.

---

## 5. FSM States Touched

`Processing → Speaking → Idle`. `needs_confirmation: NO`.

The Phase-5/6 follow-up flow for the > 8 path enters `confirming`; **not
this SF.**

---

## 6. Android System Integrations & Permissions

Same as US-031. No new permissions.

---

## 7. On-device-model Impact

No model impact.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/
└── handler/
    └── ReadAllUnreadWhatsAppHandler.kt
```

### 8.2 `ReadAllUnreadWhatsAppHandler.kt`

```kotlin
package com.curro.app.handler

import android.content.Context
import com.curro.app.R
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

class ReadAllUnreadWhatsAppHandler
    @Inject
    constructor(
        private val notifications: NotificationRepository,
        private val accessGate: NotificationAccessGate,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "read_all_unread_whatsapp"

        @Suppress("ReturnCount", "ComplexMethod")
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
            if (all.size > MANY_THRESHOLD) {
                return HandlerResult.Spoken(context.getString(R.string.copy_many_unread))
            }

            // Group by sender, sorted by group's most-recent timestamp (desc).
            val groups: List<Pair<String, List<WhatsAppMessage>>> =
                all.groupBy { it.sender }
                    .map { (sender, msgs) -> sender to msgs.sortedBy { it.timestamp } }
                    .sortedByDescending { (_, msgs) -> msgs.maxOf { it.timestamp } }

            val speech = buildString {
                append(buildHeader(groups))
                groups.forEachIndexed { idx, (sender, msgs) ->
                    append(' ')
                    append(buildGroupSpeech(sender, msgs, isFirst = idx == 0))
                }
            }
            return HandlerResult.Spoken(speech)
        }

        /**
         * Pinned grammar:
         *   1 sender, 1 msg  → "Tienes 1 mensaje de Pepito."   (copy_reading_summary_one)
         *   1 sender, N>1    → "Tienes N mensajes de Pepito."  (copy_reading_summary_many)
         *   2 senders        → "Tienes N mensajes de A y M mensajes/mensaje de B."
         *                      (copy_reading_summary_multi_sender)
         *   3+ senders       → "Tienes mensajes nuevos de A, B y C."
         *                      (copy_reading_summary_three_plus — only the first 3 in the header)
         */
        private fun buildHeader(groups: List<Pair<String, List<WhatsAppMessage>>>): String =
            when (groups.size) {
                1 -> {
                    val (sender, msgs) = groups.first()
                    val res =
                        if (msgs.size == 1) R.string.copy_reading_summary_one
                        else R.string.copy_reading_summary_many
                    context.getString(res, msgs.size, sender)
                }
                2 -> {
                    val (s1, m1) = groups[0]
                    val (s2, m2) = groups[1]
                    context.getString(
                        R.string.copy_reading_summary_multi_sender,
                        m1.size, s1, m2.size, s2,
                    )
                }
                else -> {
                    context.getString(
                        R.string.copy_reading_summary_three_plus,
                        groups[0].first, groups[1].first, groups[2].first,
                    )
                }
            }

        private fun buildGroupSpeech(
            sender: String,
            msgs: List<WhatsAppMessage>,
            isFirst: Boolean,
        ): String {
            val bodies = msgs.joinToString(". ") { bodySpeech(it) }
            return if (isFirst) {
                context.getString(R.string.copy_reading_starts_with, sender) + ' ' + bodies + '.'
            } else {
                // First body inlined into "De %1$s: %2$s" template, remaining bodies appended.
                val first = msgs.first()
                val rest = msgs.drop(1).joinToString("") { ". ${bodySpeech(it)}" }
                context.getString(R.string.copy_reading_from, sender, bodySpeech(first)) + rest + '.'
            }
        }

        /** Per-message text for the body. Markers → spoken paraphrase. */
        private fun bodySpeech(msg: WhatsAppMessage): String =
            when (msg.classification) {
                Classification.TEXT -> msg.text
                Classification.EMOJI -> "te ha mandado un emoji"
                Classification.VOICE_NOTE -> "te ha mandado un audio"
                Classification.IMAGE -> "te ha mandado una foto"
                Classification.OTHER -> "no he podido leer ese mensaje"
            }

        private companion object {
            const val MANY_THRESHOLD = 8
        }
    }
```

> **Pinned**: the body joiner is `". "` (period + space) so the TTS engine
> gets a clear sentence break between message bodies. The trailing `.`
> after each group ensures the next group's header doesn't run on.

### 8.3 `strings.xml` — adds / reuses

Reused:

- `copy_no_unread`.
- `copy_many_unread`.
- `copy_whatsapp_parse_miss`.
- `copy_perm_missing_notifs`.
- `copy_reading_summary_one` (`"Tienes %1$d mensaje de %2$s."`).
- `copy_reading_summary_many` (`"Tienes %1$d mensajes de %2$s."`).
- `copy_reading_summary_multi_sender` (`"Tienes %1$d mensajes de %2$s y %3$d mensaje de %4$s."`).
- `copy_reading_starts_with` (`"Empiezo con %1$s:"`).
- `copy_reading_from` (`"De %1$s: %2$s"`).
- `copy_reading_message`.

New:

```xml
<!-- US-032 (SF-4.8) — header for 3+ senders. %1$s/%2$s/%3$s = first three senders by most-recent. -->
<string name="copy_reading_summary_three_plus">Tienes mensajes nuevos de %1$s, %2$s y %3$s.</string>
```

### 8.4 `HandlerModule.kt` — append

```kotlin
@Binds
@IntoMap
@StringKey("read_all_unread_whatsapp")
abstract fun bindReadAllUnreadWhatsAppHandler(impl: ReadAllUnreadWhatsAppHandler): FunctionHandler
```

---

## 9. Acceptance Criteria

- [ ] `handler/ReadAllUnreadWhatsAppHandler.kt` exists at the documented path.
- [ ] `HandlerModule` gains the `@Binds @IntoMap @StringKey("read_all_unread_whatsapp")` line.
- [ ] `strings.xml` gains `copy_reading_summary_three_plus`.
- [ ] Empty cache + miss == 0 → `copy_no_unread`.
- [ ] Empty cache + miss > 0 → `copy_whatsapp_parse_miss`.
- [ ] Access denied → `Failed(copy_perm_missing_notifs, NotificationAccessMissing)`.
- [ ] **> 8 unread** → exactly `copy_many_unread`, no body.
- [ ] **1 sender, 1 msg** → `"Tienes 1 mensaje de Pepito. Empiezo con
      Pepito: Te espero a las siete."`.
- [ ] **1 sender, 3 msgs** → `"Tienes 3 mensajes de Pepito. Empiezo con
      Pepito: Te espero a las siete. Trae el pan. Y vino si puedes."`.
- [ ] **2 senders** (the spec §6 flow-5 example shape): output matches the
      pinned canonical phrasing.
- [ ] **3 senders** → `copy_reading_summary_three_plus` header + each
      group's body.
- [ ] **4 senders** → header reads only the first 3 by most-recent
      timestamp; ALL 4 groups still appear in the body.
- [ ] **Mixed classifications** in one group: each message's body comes from
      the per-classification mapping.
- [ ] **Sender order**: groups appear in body in most-recent-active-first
      order; WITHIN each group, messages are chronological (oldest first).
- [ ] No new permissions; no manifest changes; no new dependency.
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green.

---

## 10. Senior-UX & Copy

| String ID | Spanish | Voice notes |
|---|---|---|
| `copy_reading_summary_three_plus` (NEW) | "Tienes mensajes nuevos de %1$s, %2$s y %3$s." | Top-3 senders. Avoids overwhelming the user with N names. |
| `copy_many_unread` (existing) | "Tienes muchos mensajes. ¿Te los leo todos o solo los de alguien?" | The Phase-5/6 follow-up wires the answer; here it's a final spoken line. |

Voice: the grouped phrasing matches how the user's daughter would tell him
("3 de Pepito y 1 de Lucía…"). The body joiner `". "` gives the TTS engine
clear pauses.

---

## 11. Design Notes

No new visual surface. The message-cards screen mentioned in spec §6 flow 5
is **Phase 5 / SF-5.5**'s state-driven overlay — not this SF.

---

## 12. Performance Considerations

- Cache reads + grouping over ≤ 8 messages → sub-ms.
- `buildString` is the right tool for the long output; the entire phrase
  rarely exceeds 800 chars.
- The TTS engine handles 800-char Spanish phrases natively; no special
  chunking.

---

## 13. Testing Requirements

**`ReadAllUnreadWhatsAppHandlerTest.kt`** — Robolectric (≥ 15 cases):

Fakes:

- `FakeNotificationRepository(unread, missCount)`.
- `FakeNotificationAccessGate(granted)`.

Cases:

1. Empty cache → `copy_no_unread`.
2. Empty cache + miss=2 → `copy_whatsapp_parse_miss`.
3. Access denied → `Failed(NotificationAccessMissing)`.
4. 9 msgs → `copy_many_unread` (exact equality).
5. 8 msgs (boundary) → grouped read (NOT the many line).
6. 1 sender, 1 msg → exact pinned output.
7. 1 sender, 3 msgs → exact pinned output (chronological bodies joined by ". ").
8. 2 senders, mixed counts (3 + 1) → matches spec §6 flow-5 canonical.
9. 3 senders → three-plus header + 3 group bodies.
10. 4 senders → three-plus header reads first 3; body has all 4 groups.
11. All-emoji from Pepito → bodies all "te ha mandado un emoji" joined.
12. Mixed classifications (TEXT + EMOJI + VOICE_NOTE + IMAGE) — each
    body's contribution pinned.
13. Group-chat senders preserved (the per-Person.name comes through as
    `WhatsAppMessage.sender`).
14. Same sender with messages at different timestamps — chronological.
15. Sender-order verified: the group with the most-recent timestamp is
    spoken first.
16. OTHER classification body → "no he podido leer ese mensaje" inline (the
    surrounding read continues — only handler-wide all-OTHER would hit the
    parse-miss path, and that's not a Phase-4 scenario since at least one
    classification can be parsed for any non-empty cache).

**On-device verification** on the Redmi 15:

- Set up: send 3 WhatsApps from one person, 1 from another, all in ~30 seconds.
- "léeme los mensajes" → Curro reads the canonical phrase.
- Send 9 → "muchos mensajes" line, no body.
- Send 1 emoji, 1 voice, 1 image, 1 text — verify the mixed phrase.

---

## 14. Implementation Notes — Order of Operations

1. Verify US-030 and US-031 are committed.
2. Add `copy_reading_summary_three_plus` to `strings.xml`.
3. Create `handler/ReadAllUnreadWhatsAppHandler.kt`.
4. Append the handler `@Binds @IntoMap @StringKey("read_all_unread_whatsapp")` to `HandlerModule`.
5. Write `ReadAllUnreadWhatsAppHandlerTest`.
6. `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
7. Smoke-test on the Redmi 15 with the canned scenarios above.
8. Commit as `feat: add read_all_unread_whatsapp handler (US-032 / SF-4.8)`.

---

## 15. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
