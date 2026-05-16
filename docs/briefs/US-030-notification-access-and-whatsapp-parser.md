# US-030 — SF-4.6 · Notification access infrastructure + `WhatsAppNotificationParser`

> **Spec trace:** spec §5 (catalog entries `read_last_whatsapp` and
> `read_all_unread_whatsapp` depend on this infrastructure), spec §6 flow 5
> (reading-messages flow), spec §10 (`BIND_NOTIFICATION_LISTENER_SERVICE`),
> spec §14 "Riesgos identificados" — WhatsApp parser is **explicitly
> called out** as the highest-risk piece in the prototype.
> **Master-plan:** SF-4.6 — Size **L**, the single longest brief in Phase 4.
> **Phase:** 4 — Fase 1 handlers.
> **Depends on:** US-025 (`FunctionHandler` — infrastructure-only here; the
> two consumer handlers ship in US-031 and US-032).
> **Size:** L.
> **Skills:** `platform-integrations` (rule 2 — the defensive WhatsApp parser
> is its centrepiece), `local-data` (the in-memory cache is the Phase-7
> stub-shape), `launcher-ui`, `accessibility-patterns`, `testing-patterns`,
> `brand-design`, `git-workflow`, `function-catalog`.

---

## Metadata

| Field | Value |
|-------|-------|
| **Feature** | `NotificationListenerService` + WhatsApp parser + unread cache + permission UX |
| **US ID** | US-030 |
| **Phase** | 4 |
| **Status** | In Progress |
| **Created** | 2026-05-16 |
| **Modified** | 2026-05-16 |
| **PM Owner** | android-product-analyst |
| **Architect** | android-developer |

---

## 1. Summary

The **single highest-risk piece in the Curro prototype** (master-plan
§Risks; spec §14 "Riesgos identificados"). This SF lays the WhatsApp-reading
infrastructure that SF-4.7 (`read_last_whatsapp`) and SF-4.8
(`read_all_unread_whatsapp`) consume:

- A `NotificationListenerService` registered for the WhatsApp and WhatsApp
  Business packages.
- A **three-tier defensive parser** (`MessagingStyle` → legacy `extras` →
  parse-miss) that never invents content; on any unknown shape, records a
  parse-miss counter and returns `null` (the consumer handlers downgrade to
  a clean Spanish "no he podido leerlo bien" line).
- An in-memory `UnreadMessageCache` exposing `Flow<List<WhatsAppMessage>>`
  and a `parseMissCount: Flow<Int>` — Phase-7 swaps in a Room-backed impl
  without touching the contract.
- A `BigPrimaryButton` on the launcher home that lights up **only when**
  Curro is the default AND notification access is missing, deep-linking to
  `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.

The **fixture suite is the deliverable** (`platform-integrations` rule 2):
without 20+ realistic notification fixtures, this SF is not done.

Why this matters for *this* user: WhatsApp is the single hardest app for
the user to read directly. SF-4.7 / SF-4.8 are why he might keep using
Curro. **If WhatsApp's notification format drifts and we silently read
garbage, the user loses trust and stops using Curro entirely.** The parser
must never invent content; the fallback line is the contract.

---

## 2. Scope

**In scope:**

- Manifest: `BIND_NOTIFICATION_LISTENER_SERVICE` permission + the
  `<service>` block with the magic intent filter.
- `data/notification/CurroNotificationListenerService.kt`.
- `data/notification/WhatsAppNotificationParser.kt` — the three-tier
  defensive parser.
- `data/notification/UnreadMessageCache.kt` — `@Singleton` in-memory cache
  implementing `NotificationRepository`.
- `domain/model/WhatsAppMessage.kt` + `Classification` enum.
- `domain/repository/NotificationRepository.kt`.
- `di/NotificationModule.kt`.
- `data/permissions/NotificationAccessGate.kt` — interface + impl.
- `LauncherViewModel` / `LauncherUiState` / `LauncherScreen` — add the
  `isNotificationAccessGranted` flag and the home CTA.
- New `CurroError.NotificationAccessMissing`.
- New `strings.xml` entry: `copy_grant_notif_access_cta`. Reuse
  `copy_no_unread`, `copy_many_unread`, `copy_whatsapp_parse_miss`,
  `copy_perm_missing_notifs`.
- ≥ 20 fixture-driven JVM tests on `WhatsAppNotificationParser`.
- ≥ 5 tests on `UnreadMessageCache`.
- Compose UI test for the home CTA.

**Out of scope (consumer handlers ship later in this Phase):**

- `ReadLastWhatsAppHandler` — US-031.
- `ReadAllUnreadWhatsAppHandler` — US-032.
- Replying to WhatsApp (`send_whatsapp_reply` — Fase 2).
- Persisting unread messages across process death (Phase 7 — Room-backed
  cache).
- A full "diagnostics" surface for `parseMissCount` (Phase 8 config menu).

---

## 3. User Flows

### Flow 1: First-time install — Curro becomes default → permission CTA

1. User installs Curro, sets it as default (US-009 flow).
2. Launcher home renders: clock + mic + favourites + **`"Permitir leer
   mensajes"`** button (NEW). `NotificationAccessGate.isGranted() == false`.
3. User taps the button → `Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)`
   opens HyperOS's notification-access list.
4. User toggles Curro on; Android binds `CurroNotificationListenerService`.
5. User returns to launcher → `ON_RESUME` re-evaluates the gate → the CTA
   hides.

### Flow 2: WhatsApp message arrives → parser → cache

1. Someone sends a WhatsApp to the user.
2. WhatsApp posts a notification → `CurroNotificationListenerService.onNotificationPosted(sbn)`.
3. Filter: `sbn.packageName ∈ {com.whatsapp, com.whatsapp.w4b}`.
4. `parser.parse(sbn)` runs the three tiers; on success → `WhatsAppMessage`;
   on miss → `null`.
5. `cache.upsert(msg)` or `cache.recordParseMiss(sbn.key)`.
6. Next time the user says `"léeme el último mensaje"` (US-031), the cache
   serves the right `WhatsAppMessage`.

### Flow 3: WhatsApp updates and breaks the format

1. WhatsApp pushes a new notification shape.
2. `parser.parse(sbn)` returns `null` — every tier failed.
3. `cache.recordParseMiss(sbn.key)` increments `parseMissCount`.
4. The user says `"léeme los mensajes"`; US-031/US-032 reads
   `parseMissCount.first() > 0` and `allUnread.first().isEmpty()`, and
   speaks `copy_whatsapp_parse_miss` ("Tienes mensajes nuevos pero no he
   podido leerlos bien.") — never silence, never invented content.

### Flow 4: Access revoked mid-life

1. The user / a system update revokes notification access.
2. `NotificationAccessGate.isGranted() == false` on the next `ON_RESUME`.
3. The CTA reappears.
4. If a handler (US-031/US-032) is called in this window, it returns
   `Failed(copy_perm_missing_notifs, NotificationAccessMissing)`.

---

## 4. Function-catalog Impact

**No catalog change** — `read_last_whatsapp` and `read_all_unread_whatsapp`
already exist (US-021). This SF is the infrastructure their handlers use.

---

## 5. FSM States Touched

This SF is **infrastructure-only** — no new FSM transition. The consumer
handlers (US-031, US-032) emit `Spoken` / `Failed` via the existing
`Processing → Speaking → Idle` provisional FSM.

---

## 6. Android System Integrations & Permissions

| Integration | Why |
|---|---|
| `NotificationListenerService` | Read WhatsApp notifications without WhatsApp's cooperation. |
| `NotificationCompat.MessagingStyle` | Extract structured per-message data (Tier 1). |
| `StatusBarNotification.notification.extras` | Legacy fallback (Tier 2). |
| `NotificationManagerCompat.getEnabledListenerPackages(ctx)` | Detect grant state. |
| `Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)` | Deep-link to HyperOS's settings page. |

| Permission | Why | Requested when | If denied |
|---|---|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Bind the listener — system-only permission, declared on the `<service>` element. | Granted by the **user**, in HyperOS Settings, via the deep link. Never via a runtime prompt. | The home CTA reappears; consumer handlers (US-031/US-032) return `Failed(NotificationAccessMissing)`. |

**Manifest additions** — `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    tools:ignore="ProtectedPermissions" />
```

and inside `<application>`:

```xml
<service
    android:name=".data.notification.CurroNotificationListenerService"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

Update the manifest's leading comment block: the
`BIND_NOTIFICATION_LISTENER_SERVICE` entry's "→ SF-4.6" pointer becomes "→ landed
in US-030 / SF-4.6".

---

## 7. On-device-model Impact

The Phase-3 `PromptContext.unreadMessagesSummary` is still empty in Phase 4
— the consumer handlers (US-031/US-032) read the cache directly, NOT through
the prompt. **Decision pinned**: the prompt context is updated in **Phase 5**
when the FSM-driven coordinator becomes the single owner of the LLM pipeline
and can read the cache cheaply on every press. Until then, the prompt budget
on the 270M model stays minimal.

No Gemma 3n.

---

## 8. Android Specification

### 8.1 Files added

```
app/src/main/java/com/curro/app/
├── data/
│   ├── notification/
│   │   ├── CurroNotificationListenerService.kt
│   │   ├── WhatsAppNotificationParser.kt
│   │   └── UnreadMessageCache.kt
│   └── permissions/
│       └── NotificationAccessGate.kt          // interface + impl
├── domain/
│   ├── model/WhatsAppMessage.kt
│   └── repository/NotificationRepository.kt
└── di/
    └── NotificationModule.kt
```

### 8.2 `WhatsAppMessage.kt`

```kotlin
package com.curro.app.domain.model

/**
 * One unread WhatsApp message, normalised across MessagingStyle and legacy-extras
 * notification shapes. Phase 7 swaps the cache backend to Room without changing
 * this class.
 */
data class WhatsAppMessage(
    val key: String,            // sbn.key — used to dedupe in the cache
    val sender: String,         // 1:1 chat title, or Person.name in a group
    val chatTitle: String,      // group chat name or sender for 1:1
    val text: String,           // body, normalised marker for emoji/audio/foto
    val isGroup: Boolean,
    val timestamp: Long,        // ms epoch from sbn.postTime
    val classification: Classification,
) {
    /** What kind of content is in [text]. The handlers speak different lines per classification. */
    enum class Classification { TEXT, EMOJI, VOICE_NOTE, IMAGE, OTHER }
}
```

### 8.3 `NotificationRepository.kt`

```kotlin
package com.curro.app.domain.repository

import com.curro.app.domain.model.WhatsAppMessage
import kotlinx.coroutines.flow.Flow

/**
 * The in-memory unread-message cache contract.
 *
 * Phase 4: backed by [com.curro.app.data.notification.UnreadMessageCache]
 *          (a `MutableStateFlow<Map<String, WhatsAppMessage>>`).
 * Phase 7: backed by Room (`local-data` skill) — same contract.
 *
 * Privacy: the cache lives in-process; nothing here is ever surfaced to telemetry.
 */
interface NotificationRepository {
    /** All unread messages, latest snapshot. Empty list when nothing's pending. */
    val allUnread: Flow<List<WhatsAppMessage>>

    /** Unread messages from a specific sender (normalised contains match). */
    fun unreadBySender(sender: String): Flow<List<WhatsAppMessage>>

    /**
     * Count of notifications the parser couldn't handle. > 0 means "there ARE
     * unread WhatsApps but their shape is unknown to us"; consumer handlers
     * speak [com.curro.app.R.string.copy_whatsapp_parse_miss].
     */
    val parseMissCount: Flow<Int>

    /** Drop everything from [sender]. Called when a chat is opened / dismissed. */
    fun clear(sender: String)
}
```

### 8.4 `WhatsAppNotificationParser.kt` — the three-tier algorithm

```kotlin
package com.curro.app.data.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.model.WhatsAppMessage.Classification
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Three-tier defensive WhatsApp notification parser.
 *
 *   Tier 1 — MessagingStyle (modern WhatsApp): structured per-message access.
 *   Tier 2 — legacy `extras` (older WhatsApp / WhatsApp Business): EXTRA_TITLE + EXTRA_TEXT / EXTRA_TEXT_LINES.
 *   Tier 3 — summary notification (FLAG_GROUP_SUMMARY): count-only, no bodies.
 *   Miss   — anything else → return null (caller records a parse-miss).
 *
 * NEVER invents content. NEVER guesses at the sender. If a tier produces an
 * empty body or null sender, the parser moves to the next tier or returns null.
 *
 * Single source for the WhatsApp parsing contract. Adding a new shape = adding
 * a fixture test + a parser branch.
 */
@Singleton
class WhatsAppNotificationParser
    @Inject
    constructor() {
        /**
         * @return One [WhatsAppMessage] per individual unread message the SBN encodes
         *         (a MessagingStyle SBN with 3 messages → 3 elements). Empty list
         *         means "this SBN is a summary / unsupported shape" — caller should
         *         `recordParseMiss(sbn.key)` ONLY if Tier 3 also missed.
         */
        @Suppress("ReturnCount", "ComplexMethod")
        fun parse(sbn: StatusBarNotification): List<WhatsAppMessage> {
            val n = sbn.notification ?: return emptyList()
            val extras = n.extras ?: return emptyList()

            // Tier 3 — summary notification first (cheap branch).
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return emptyList()

            // Tier 1 — MessagingStyle.
            val styled = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
            if (styled != null && styled.messages.isNotEmpty()) {
                val chatTitle =
                    styled.conversationTitle?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                        ?: return emptyList()
                val isGroup = styled.isGroupConversation
                return styled.messages.mapNotNull { msg ->
                    val rawText = msg.text?.toString() ?: return@mapNotNull null
                    val sender =
                        if (isGroup) msg.person?.name?.toString() ?: chatTitle else chatTitle
                    val (textOut, cls) = classify(rawText, extras)
                    WhatsAppMessage(
                        key = sbn.key + "#" + msg.timestamp,
                        sender = sender,
                        chatTitle = chatTitle,
                        text = textOut,
                        isGroup = isGroup,
                        timestamp = msg.timestamp,
                        classification = cls,
                    )
                }
            }

            // Tier 2 — legacy extras.
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val rawBody =
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: lastTextLine(extras)
                    ?: return emptyList()
            if (title.isNullOrEmpty() || rawBody.isEmpty()) return emptyList()
            val (textOut, cls) = classify(rawBody, extras)
            return listOf(
                WhatsAppMessage(
                    key = sbn.key,
                    sender = title,
                    chatTitle = title,
                    text = textOut,
                    isGroup = false,
                    timestamp = sbn.postTime,
                    classification = cls,
                ),
            )
        }

        private fun lastTextLine(extras: Bundle): String? {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: return null
            return lines.lastOrNull()?.toString()
        }

        /**
         * Classify a body into TEXT / EMOJI / VOICE_NOTE / IMAGE and produce the
         * normalised marker string for non-text classifications. Markers are NOT
         * spoken verbatim — the consumer handlers (US-031/US-032) format them
         * with "te ha mandado un emoji / audio / foto".
         */
        @Suppress("ReturnCount")
        private fun classify(body: String, extras: Bundle): Pair<String, Classification> {
            val info = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
            // Voice note — by extras hint OR body contains a known WhatsApp string.
            if (info.contains("Voice message", ignoreCase = true)) {
                return "[audio]" to Classification.VOICE_NOTE
            }
            if (VOICE_NOTE_RE.containsMatchIn(body)) {
                return "[audio]" to Classification.VOICE_NOTE
            }
            // Image — body matches a known photo marker.
            if (IMAGE_RE.containsMatchIn(body)) {
                return "[foto]" to Classification.IMAGE
            }
            // Emoji-only body — body is all "symbol" / "modifier" code points + whitespace.
            if (body.isNotEmpty() && body.matches(EMOJI_ONLY_RE)) {
                return "[emoji]" to Classification.EMOJI
            }
            return body to Classification.TEXT
        }

        private companion object {
            // \p{So} symbol-other, \p{Sk} modifier symbol, \p{Mn} mark-nonspacing,
            // \p{Cf} format (e.g. ZWJ), \p{Cn} unassigned. Plus whitespace.
            // Java's Pattern.UNICODE_CHARACTER_CLASS — `(?U)` — is the safe regex flag.
            val EMOJI_ONLY_RE: Regex = Regex("^[\\p{So}\\p{Sk}\\p{Mn}\\p{Cf}\\s]+$")

            // WhatsApp Spanish/English/voice-note markers.
            val VOICE_NOTE_RE: Regex = Regex(
                "(🎤\\s*(Voice message|Mensaje de voz|Nota de voz))|" +
                    "\\[(Voice message|Mensaje de voz|Nota de voz)\\]|" +
                    "🎤",
            )

            // WhatsApp image markers.
            val IMAGE_RE: Regex = Regex(
                "(📷\\s*(Photo|Foto|Imagen))|" +
                    "\\[(Photo|Foto|Imagen)\\]|" +
                    "📷",
            )
        }
    }
```

> **Pinned**: the parser returns a `List<WhatsAppMessage>`, not a single
> message — a MessagingStyle SBN with three messages expands to three list
> entries. The caller (`CurroNotificationListenerService`) iterates +
> `cache.upsert` per entry. **Empty list means parse miss.**

### 8.5 `CurroNotificationListenerService.kt`

```kotlin
package com.curro.app.data.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CurroNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var cache: UnreadMessageCache
    @Inject lateinit var parser: WhatsAppNotificationParser

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        scope.launch {
            val parsed = parser.parse(sbn)
            if (parsed.isEmpty()) {
                cache.recordParseMiss(sbn.key)
            } else {
                parsed.forEach(cache::upsert)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        scope.launch { cache.onRemoved(sbn.key) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
```

### 8.6 `UnreadMessageCache.kt`

```kotlin
package com.curro.app.data.notification

import com.curro.app.domain.model.WhatsAppMessage
import com.curro.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-4 in-memory implementation of [NotificationRepository]. Phase 7 swaps
 * in a Room-backed impl; the interface stays.
 *
 * Keying: cache key is `sbn.key + "#" + message-timestamp` for Tier-1
 * MessagingStyle entries (so a 3-message SBN keeps 3 cache rows), or
 * `sbn.key` for Tier-2 legacy entries. When a notification is removed,
 * every row whose key STARTS WITH `sbn.key` is dropped.
 */
@Singleton
class UnreadMessageCache
    @Inject
    constructor() : NotificationRepository {
        private val state = MutableStateFlow<Map<String, WhatsAppMessage>>(emptyMap())
        private val parseMisses = MutableStateFlow(0)
        private val parseMissKeys = mutableSetOf<String>()

        override val allUnread: Flow<List<WhatsAppMessage>> =
            state.asStateFlow().map { it.values.toList() }

        override fun unreadBySender(sender: String): Flow<List<WhatsAppMessage>> =
            state.asStateFlow().map { snapshot ->
                snapshot.values.filter {
                    it.sender.equals(sender, ignoreCase = true) ||
                        it.chatTitle.equals(sender, ignoreCase = true)
                }
            }

        override val parseMissCount: Flow<Int> = parseMisses.asStateFlow()

        fun upsert(msg: WhatsAppMessage) {
            state.update { it + (msg.key to msg) }
        }

        fun onRemoved(sbnKey: String) {
            state.update { snapshot -> snapshot.filterKeys { !it.startsWith(sbnKey) } }
            // Also forget any parse-miss tied to this sbnKey — the OS just dropped it.
            synchronized(parseMissKeys) {
                if (parseMissKeys.remove(sbnKey)) {
                    parseMisses.update { (it - 1).coerceAtLeast(0) }
                }
            }
        }

        fun recordParseMiss(sbnKey: String) {
            synchronized(parseMissKeys) {
                if (parseMissKeys.add(sbnKey)) {
                    parseMisses.update { it + 1 }
                }
            }
        }

        override fun clear(sender: String) {
            state.update { snapshot ->
                snapshot.filterValues {
                    !(it.sender.equals(sender, ignoreCase = true) ||
                        it.chatTitle.equals(sender, ignoreCase = true))
                }
            }
        }
    }
```

### 8.7 `NotificationAccessGate.kt`

```kotlin
package com.curro.app.data.permissions

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.curro.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface NotificationAccessGate {
    /** True iff the user has granted notification-listener access to Curro. */
    fun isGranted(): Boolean
}

class SystemNotificationAccessGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NotificationAccessGate {
        override fun isGranted(): Boolean =
            NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(BuildConfig.APPLICATION_ID)
    }
```

### 8.8 `NotificationModule.kt`

```kotlin
package com.curro.app.di

import com.curro.app.data.notification.UnreadMessageCache
import com.curro.app.data.permissions.NotificationAccessGate
import com.curro.app.data.permissions.SystemNotificationAccessGate
import com.curro.app.domain.repository.NotificationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds @Singleton
    abstract fun bindNotificationRepository(impl: UnreadMessageCache): NotificationRepository

    @Binds @Singleton
    abstract fun bindNotificationAccessGate(impl: SystemNotificationAccessGate): NotificationAccessGate
}
```

### 8.9 `LauncherViewModel` / `LauncherUiState` / home CTA

Add `isNotificationAccessGranted: Boolean` to `LauncherUiState`. Wire the
gate via `Hilt` constructor injection. Re-evaluate on `ON_RESUME` (the user
returns from Settings) — pin the implementation:

```kotlin
// LauncherViewModel — addition.
@Inject constructor(
    // … existing collaborators
    private val notifGate: NotificationAccessGate,
) : ViewModel(), DefaultLifecycleObserver {

    private val notifGrantedFlow = MutableStateFlow(notifGate.isGranted())

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    override fun onResume(owner: LifecycleOwner) { notifGrantedFlow.value = notifGate.isGranted() }
    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
    // The combine() that builds LauncherUiState gains notifGrantedFlow as another source.
}
```

`LauncherScreen` renders:

```kotlin
if (uiState.isCurroDefault && !uiState.isNotificationAccessGranted) {
    BigPrimaryButton(
        text = stringResource(R.string.copy_grant_notif_access_cta),
        onClick = { onEvent(LauncherEvent.GrantNotifAccessRequested) },
        modifier = Modifier.fillMaxWidth().padding(top = CurroSpacing.lg),
    )
}
```

…between the favourites grid and the "Más apps" link (pinned slot).

New event `LauncherEvent.GrantNotifAccessRequested → LauncherSideEffect.OpenNotificationAccessSettings`
(a `data object`). The screen consumes the side effect and starts
`Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(NEW_TASK)`.

### 8.10 `CurroError` addition

```kotlin
// ── Notification access (US-030 / SF-4.6) ─────────────────────────────────

/**
 * The handler ran but `BIND_NOTIFICATION_LISTENER_SERVICE` is not granted.
 * Speech: `copy_perm_missing_notifs`.
 */
data object NotificationAccessMissing : CurroError()
```

### 8.11 `strings.xml` — add / reuse

Reuse:

- `copy_no_unread` (`"No tienes mensajes nuevos."`).
- `copy_many_unread` (`"Tienes muchos mensajes. ¿Te los leo todos o solo
  los de alguien?"`).
- `copy_whatsapp_parse_miss` (`"Tienes mensajes nuevos pero no he podido
  leerlos bien."`).
- `copy_perm_missing_notifs` (`"Necesito que me dejes leer las
  notificaciones. Díselo a Fran."`).

New:

```xml
<!-- US-030 (SF-4.6) — home-screen CTA. Voice: short, imperative. -->
<string name="copy_grant_notif_access_cta">Permitir leer mensajes</string>
```

### 8.12 `TelemetryGuardrail`

No change — `handler_invoked` (added in US-025) covers the consumer
handlers. **NEVER** log sender names, chat titles, message bodies, parse-miss
content, or notification keys (which can include phone numbers).

The `parseMissCount` is safe as an int; if telemetry wants it later (Phase 8
diagnostics), add via a new whitelist row at that time.

---

## 9. Acceptance Criteria

- [ ] All seven new files exist at the documented paths.
- [ ] Manifest: `BIND_NOTIFICATION_LISTENER_SERVICE` declared with
      `tools:ignore="ProtectedPermissions"`; `CurroNotificationListenerService`
      declared with the magic intent filter and the right permission. Leading
      comment block updated.
- [ ] `NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(
      BuildConfig.APPLICATION_ID)` is the verified detection method.
- [ ] Home CTA appears **only when** `isCurroDefault && !isNotificationAccessGranted`;
      tap fires `OpenNotificationAccessSettings`.
- [ ] After granting access in HyperOS Settings + returning to Curro, the
      CTA disappears within one `ON_RESUME` cycle.
- [ ] `WhatsAppNotificationParserTest`: 20+ fixture cases, **every Tier
      1 / Tier 2 / parse-miss path covered** (see §13).
- [ ] `UnreadMessageCacheTest`: 5+ cases covering upsert / onRemoved /
      parseMiss / unreadBySender / clear.
- [ ] `parseMissCount` flow emits the right int on upsert vs. parse-miss
      sequences.
- [ ] `CurroError.NotificationAccessMissing` added.
- [ ] `copy_grant_notif_access_cta` added; the existing four reused copy
      entries are unchanged.
- [ ] On the Redmi 15: granting access then sending the user a WhatsApp
      results in a `WhatsAppMessage` in the cache (verify via a temporary
      debug Logcat tap or via US-031 once it ships).
- [ ] `./gradlew assembleDebug ktlintCheck detektDebug testDebugUnitTest` all
      green.

---

## 10. Senior-UX & Copy

| String ID | Spanish | Voice notes |
|---|---|---|
| `copy_grant_notif_access_cta` (NEW) | "Permitir leer mensajes" | Imperative, brief; the user understands what tapping does. |
| `copy_perm_missing_notifs` (existing) | "Necesito que me dejes leer las notificaciones. Díselo a Fran." | Reused — the handler-side line. |
| `copy_no_unread` (existing) | "No tienes mensajes nuevos." | Reused — US-031/US-032 speak this. |
| `copy_whatsapp_parse_miss` (existing) | "Tienes mensajes nuevos pero no he podido leerlos bien." | Reused — the parse-miss fallback. |

Voice: the user MUST hear an honest line when the parser drifts — not
silence, not invented content. The fallback is the contract.

### Home CTA layout (`launcher-ui`)

The `BigPrimaryButton` sits between the favourites grid and "Más apps", in
the same visual slot as the SF-1.1 "Hazme tu pantalla de inicio" button.
Both buttons obey the senior-first floor — ≥ 96 dp tap target, large text,
≥ 7:1 contrast. **Decision pinned**: the two CTAs never coexist — if Curro
isn't the default launcher, only that CTA shows; once Curro is default and
needs notification access, only this one shows.

---

## 11. Design Notes

`BigPrimaryButton` is reused from US-006 (no new component). The CTA uses
the same colour token (`MaterialTheme.colorScheme.primary` per
`brand-design`). No new visual surface.

---

## 12. Performance Considerations

- `onNotificationPosted` hops to `Dispatchers.IO` before parsing — the
  main-thread callback returns in microseconds.
- Parser does no I/O; ≤ 1 ms per SBN on the Redmi 15.
- `UnreadMessageCache.state` is a `MutableStateFlow<Map<…>>` — each
  upsert allocates a fresh `Map`; the cache is bounded by WhatsApp's own
  notification limits (typically < 50 unread messages on a single phone).
- `parseMissKeys` is a `mutableSetOf` guarded by a `synchronized` block —
  the parser is called on the IO dispatcher, but `onRemoved` can also touch
  the set; the lock keeps the int Flow consistent. Acceptable for the
  expected cadence (a few notifications per minute at worst).

---

## 13. Testing Requirements

### 13.1 `WhatsAppNotificationFixtures.kt` — builder helpers

Use Robolectric's `Notification.Builder` shadow to assemble realistic
`StatusBarNotification` instances. **Decision pinned**: Robolectric is the
right tool — `StatusBarNotification`'s `Notification` field requires real
`NotificationCompat.MessagingStyle` construction.

```kotlin
internal object WhatsAppNotificationFixtures {
    fun messagingStyle1to1(
        chatTitle: String = "Pepito",
        text: String = "Te espero a las siete",
        key: String = "0|com.whatsapp|1|tag|0",
        postTime: Long = 1_700_000_000_000L,
        timestamp: Long = postTime,
    ): StatusBarNotification = …

    fun messagingStyle1to1Multi(
        chatTitle: String = "Pepito",
        bodies: List<String>,
        key: String = "0|com.whatsapp|1|tag|0",
    ): StatusBarNotification = …

    fun messagingStyleGroup(
        groupName: String = "Familia",
        messages: List<Pair<String, String>>, // (senderName, body)
        key: String = "0|com.whatsapp|2|tag|0",
    ): StatusBarNotification = …

    fun legacyExtras1to1(
        title: String = "Pepito",
        body: String = "Te espero a las siete",
        key: String = "0|com.whatsapp|3|tag|0",
    ): StatusBarNotification = …

    fun legacyExtrasTextLines(
        title: String = "Pepito",
        lines: List<String>,
    ): StatusBarNotification = …

    fun groupSummary(
        key: String = "0|com.whatsapp|0|null|0",
    ): StatusBarNotification = …

    fun voiceNote(): StatusBarNotification = …  // body "🎤 Voice message"
    fun image(): StatusBarNotification = …      // body "📷 Photo"
    fun emojiOnly(): StatusBarNotification = …  // body "❤️"
    fun bigText(longBody: String = …): StatusBarNotification = …

    fun whatsappBusiness(): StatusBarNotification = …       // package = "com.whatsapp.w4b"
    fun unknownPackage(): StatusBarNotification = …         // package = "com.example.fake"

    fun missingTitle(): StatusBarNotification = …           // legacy with EXTRA_TITLE absent
    fun missingText(): StatusBarNotification = …            // legacy with EXTRA_TEXT absent
    fun nullExtras(): StatusBarNotification = …
    fun messagingStyleEmpty(): StatusBarNotification = …    // MessagingStyle with 0 messages

    fun groupChatNullPersonName(): StatusBarNotification = …  // Person.name == null
}
```

### 13.2 `WhatsAppNotificationParserTest.kt` — ≥ 20 cases

Each case is a single fixture → `parser.parse(sbn)` → assertion on the
result list.

1. **MS 1:1 single** — 1 message; `sender == chatTitle == "Pepito"`,
   `isGroup == false`, `text == "Te espero…"`, `Classification.TEXT`.
2. **MS 1:1 triple** — 3 messages; list size 3, all from "Pepito",
   timestamps strictly increasing.
3. **MS group two senders** — 2 messages; `isGroup == true`, distinct
   `sender` fields, `chatTitle == "Familia"`.
4. **MS group null Person.name** — sender falls back to `chatTitle`.
5. **Legacy extras 1:1** — single result; TEXT.
6. **Legacy extras `EXTRA_TEXT_LINES`** — single result with the last line
   as `text`.
7. **Summary** (`FLAG_GROUP_SUMMARY`) — empty list (Tier 3).
8. **Emoji only** (`"❤️"`) — `Classification.EMOJI`, `text == "[emoji]"`.
9. **Voice note via body** (`"🎤 Voice message"`) — `Classification.VOICE_NOTE`,
   `text == "[audio]"`.
10. **Voice note via `EXTRA_INFO_TEXT`** — VOICE_NOTE.
11. **Image via body** (`"📷 Photo"`) — `Classification.IMAGE`, `text == "[foto]"`.
12. **BigText long body** — TEXT, body preserved verbatim.
13. **WhatsApp Business** package — parsed identically to `com.whatsapp`
    (parser does NOT filter by package; the listener does).
14. **Unknown package** — for completeness, parser still parses; the
    package filter lives in the listener (verify in `CurroNotificationListenerServiceTest`).
15. **Missing `EXTRA_TITLE`, only `EXTRA_TEXT`** — Tier 2 needs both →
    empty list.
16. **Missing `EXTRA_TEXT`, only `EXTRA_TITLE`** — empty list.
17. **Null `extras`** — empty list.
18. **MS with empty `messages` list** — falls through to Tier 2; if Tier 2
    has data, parse; else empty.
19. **Multibyte-emoji mixed text** (`"🎉 Felicidades"`) — TEXT (has letters).
20. **Spanish characters in body** (`"¿Hablamos? ¡Vale!"`) — TEXT, body
    preserved.
21. **Voice note via Spanish marker** (`"🎤 Mensaje de voz"`) — VOICE_NOTE.
22. **Image via Spanish marker** (`"📷 Foto"`) — IMAGE.

### 13.3 `UnreadMessageCacheTest.kt` — ≥ 5 cases

1. Single `upsert` → `allUnread.first()` returns `[msg]`.
2. Two upserts with the same key → only one entry in the snapshot.
3. `onRemoved(sbnKey)` removes every row whose key starts with `sbnKey`
   (multi-message MS case).
4. `recordParseMiss` increments `parseMissCount`; same-key idempotent.
5. `unreadBySender("Pepito")` filters case-insensitively.
6. `clear("Pepito")` drops only Pepito's rows.
7. `onRemoved` of a parse-missed sbnKey decrements the counter.

### 13.4 Compose UI test — `LauncherScreenTest`

- Default launcher state: `(isCurroDefault=true, isNotificationAccessGranted=false)` →
  the CTA is visible.
- `(isCurroDefault=true, isNotificationAccessGranted=true)` → CTA absent.
- `(isCurroDefault=false, isNotificationAccessGranted=false)` → CTA absent
  (only the "Hazme tu pantalla de inicio" CTA shows — pinned).
- Tap → emits `LauncherEvent.GrantNotifAccessRequested`.

### 13.5 `CurroNotificationListenerServiceTest.kt`

- Robolectric `ServiceController<CurroNotificationListenerService>`.
- Verify `onNotificationPosted` filters by package (`unknownPackage()` →
  cache unchanged).
- Verify `parser.parse` empty → `cache.recordParseMiss` called.
- Verify `parser.parse` non-empty → one `cache.upsert` per entry.
- Verify `onNotificationRemoved` → `cache.onRemoved` called.

### 13.6 On-device verification (manual, on the Redmi 15)

1. Install + set as default.
2. Verify the "Permitir leer mensajes" CTA appears.
3. Tap → Settings opens to the notification-listener list.
4. Toggle Curro on; return to launcher.
5. CTA hides.
6. From another phone, send the user a WhatsApp text.
7. **Verify** (via `adb logcat -s Curro` if a debug log is added, or wait
   for US-031 to land): the cache holds the new `WhatsAppMessage`.
8. Send an emoji-only message → cache holds it with `Classification.EMOJI`.
9. Send a voice note → `Classification.VOICE_NOTE`.
10. Send an image → `Classification.IMAGE`.
11. Revoke the access in Settings; return to Curro → CTA reappears within
    one resume cycle.

---

## 14. Implementation Notes — Order of Operations

The dev pass for SF-4.6 is the **single longest** in Phase 4. Order:

1. Add `CurroError.NotificationAccessMissing`.
2. Add `copy_grant_notif_access_cta` to `strings.xml`.
3. Create `domain/model/WhatsAppMessage.kt` (+ `Classification`).
4. Create `domain/repository/NotificationRepository.kt`.
5. Create `data/permissions/NotificationAccessGate.kt` (interface + impl).
6. Create `data/notification/WhatsAppNotificationParser.kt`.
7. Create `data/notification/UnreadMessageCache.kt`.
8. Create `data/notification/CurroNotificationListenerService.kt`.
9. Create `di/NotificationModule.kt`.
10. Manifest: add the permission, the `<service>` block, and update the
    leading comment block.
11. Wire `NotificationAccessGate` into `LauncherViewModel`; extend
    `LauncherUiState` with `isNotificationAccessGranted`; add the new
    `LauncherEvent.GrantNotifAccessRequested` and
    `LauncherSideEffect.OpenNotificationAccessSettings`.
12. Extend `LauncherScreen` to render the CTA.
13. Write `WhatsAppNotificationFixtures.kt`.
14. Write `WhatsAppNotificationParserTest.kt` (≥ 20 cases).
15. Write `UnreadMessageCacheTest.kt` (≥ 5 cases).
16. Write `CurroNotificationListenerServiceTest.kt`.
17. Extend `LauncherScreenTest.kt` with the CTA cases.
18. `./gradlew ktlintCheck detektDebug testDebugUnitTest assembleDebug`.
19. Smoke-test on the Redmi 15: AC §9 bullets.
20. Commit as `feat: add notification listener + WhatsApp parser + access CTA (US-030 / SF-4.6)`.

---

## 15. Risk Register (carried from master-plan §Risks)

| Risk | Mitigation |
|---|---|
| WhatsApp updates the notification shape. | The three-tier parser + 20+ fixtures + parse-miss fallback. **Adding a new shape = adding a fixture + a parser branch in a focused commit.** |
| Notifications drift across HyperOS major versions. | The fixture suite covers stock-Android `MessagingStyle`. Add HyperOS-specific fixtures the first time real-device behaviour diverges. |
| The user grants access then HyperOS forgets it. | The CTA is **re-evaluated on every `ON_RESUME`**; the user sees the same prompt the moment Curro can't read again. |
| Listener service killed by HyperOS background-management. | Same battery-whitelist + autostart documentation the warm-up service already requires (SF-3.5 docs). |
| Cache lost on process death. | Phase-7 swap to Room. **Decision pinned**: in Phase 4 the loss is acceptable — unread notifications are still in WhatsApp's own shade, so the user can recover. |

---

## 16. Phase 5+ Hooks

- **Phase 5** — the coordinator owns the cache reads; `PromptContext.unreadMessagesSummary`
  becomes a short top-3-senders summary (≤ 80 chars) so the model can be
  smarter about resolving `sender` in `read_last_whatsapp`.
- **Phase 7** — Room-backed `UnreadMessageCache` for cross-process-death
  persistence; same interface.
- **Phase 8** — `parseMissCount` surfaced in the diagnostics screen as a
  Fran-visible counter.
- **Fase 2** — `send_whatsapp_reply` reuses the listener via
  `Notification.Action.RemoteInput` (replying TO the notification).

---

## 17. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-05-16 | android-product-analyst | Initial draft. |
