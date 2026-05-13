---
name: platform-integrations
description: Android system integrations that power Curro's handlers — NotificationListenerService (reading/replying to WhatsApp), placing calls (ACTION_CALL / TelecomManager) and the opt-in incoming-call assistant (InCallService), opening apps by colloquial name (PackageManager + QUERY_ALL_PACKAGES), volume (AudioManager), and resolving contacts (ContactsContract) including the ambiguity flow.
triggers:
  - NotificationListenerService
  - notification listener
  - WhatsApp
  - read message
  - reply
  - call
  - ACTION_CALL
  - TelecomManager
  - InCallService
  - incoming call
  - open app
  - PackageManager
  - QUERY_ALL_PACKAGES
  - volume
  - AudioManager
  - contacts
  - ContactsContract
  - ambiguity
  - disambiguation
---

# Platform Integrations (the handlers' system layer)

How Curro's `FunctionHandler`s actually do things on the phone. Each integration is
a thin component in `data/` behind a `domain/repository/` interface, so handlers and
tests don't touch Android system APIs directly. Source: `docs/curro-spec-v1.0.md`
§4.5, §5, §8, §10; the catalog is in `function-catalog`.

## WhatsApp via `NotificationListenerService` (read / reply)

Functions: `read_last_whatsapp`, `read_all_unread_whatsapp` (Fase 1),
`send_whatsapp_reply` (Fase 2). Permission: `BIND_NOTIFICATION_LISTENER_SERVICE` +
the user must grant notification access in Settings (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`).

```kotlin
// data/notification/CurroNotificationListenerService.kt
@AndroidEntryPoint
class CurroNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var cache: UnreadMessageCache
    @Inject lateinit var parser: WhatsAppNotificationParser

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp") return            // also: com.whatsapp.w4b (Business)
        parser.parse(sbn)?.let(cache::upsert)                    // null → couldn't parse → record a "parse miss" for diagnostics
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.whatsapp") cache.onRemoved(sbn.key)
    }
}
```

**Parsing — be defensive (spec risk: WhatsApp changes its notification format).**
- Prefer `MessagingStyle` (`NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)`): it gives per-message `text`, `timestamp`, and the sender's `Person`. Fall back to `extras` (`EXTRA_TITLE` = chat/sender, `EXTRA_TEXT`/`EXTRA_TEXT_LINES` = body) and to the summary notification for grouped chats.
- Distinguish 1:1 vs group chats; ignore "X mensajes nuevos de Y chats" summaries except to know there *are* unread.
- Strip WhatsApp's own decorations ("WhatsApp: ", emoji-only bodies → "[un emoji]" or skip, voice notes → "te ha mandado un audio", images → "te ha mandado una foto" — that last one is a Fase-4 hook for Gemma 3n multimodal).
- **If you can't parse it: don't crash, don't make something up.** The cache records a miss; the handler says "Tienes mensajes nuevos pero no he podido leerlos bien" (spec: robust parser + tests + fallback).
- Keep an in-memory `UnreadMessageCache` keyed by sender, in arrival order; `read_*` handlers read from it. Clear a sender's entries when the user opens that chat (notification removed).

**Replying** (`send_whatsapp_reply`, Fase 2): use the notification's reply `Action`
(`Notification.Action` with a `RemoteInput`) — `action.actionIntent.send()` with the
`RemoteInput` bundle filled with the dictated text. `needs_confirmation: true` — always
confirm the recipient + the text before sending. If the message expired from the
shade, say so honestly.

## Placing calls (`call_contact`, Fase 1)

Permissions: `READ_CONTACTS` + `CALL_PHONE`. Resolve the spoken name/alias → a
contact → a number (see Contacts below), then:

```kotlin
// data/telephony/CallController.kt
fun call(number: String) {
    context.startActivity(
        Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
```

- `ACTION_CALL` (not `ACTION_DIAL`) so it dials directly — the user shouldn't have
  to press the green button. Android's in-call UI takes over the screen; Curro
  returns to `idle` when the call ends.
- `needs_confirmation: conditional` (see `voice-interaction`) — and **always confirm
  on ambiguity** (multiple contacts match, no alias). On `SecurityException`
  (permission revoked) → "Necesito permiso para llamar; dile a Fran que lo active".

## Incoming-call assistant mode (opt-in, OFF by default — spec §8)

Permissions: `READ_PHONE_STATE` (+ `ANSWER_PHONE_CALLS` to answer by voice).
**Implemented but shipped disabled** — Fran enables it from the config menu. Default
behaviour (toggle off): Android handles incoming calls exactly as normal — do not
register the `InCallService` at all, or register it inert.

When on, a `CurroInCallService` (declared with `<service>` + `BIND_INCALL_SERVICE` +
`SUPPORTS_SELF_MANAGED_CALLING` not needed — this is a UI-only InCallService over the
system telephony):
- On `onCallAdded`, announce by voice: "Te está llamando Pepito" (resolved from
  contacts, or "número desconocido"); if there's a learned alias, use it: "Te está
  llamando tu hija María".
- Accept "sí"/"coge"/"responde" → `call.answer(...)`; "no"/"cuelga" → `call.disconnect()`.
  Also accept the manual on-screen tap — complement, never replace, the native UI.
- This is more invasive at the system level — that's exactly why it's opt-in: if it
  misbehaves, it must not break the phone's basic ability to receive calls.

## Opening apps by colloquial name (`open_app`, Fase 1)

Permission: `QUERY_ALL_PACKAGES` (declared; needed to enumerate apps). Also drives
the launcher's static app-grid tiles.

```kotlin
// data/apps/InstalledAppsProvider.kt — enumerate launchable apps
val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
val apps = packageManager.queryIntentActivities(intent, 0)
    .map { AppEntry(label = it.loadLabel(packageManager).toString().lowercase(), pkg = it.activityInfo.packageName, icon = it.loadIcon(packageManager)) }

// data/apps/AppLauncher.kt — launch by package
packageManager.getLaunchIntentForPackage(pkg)
    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ?.let(context::startActivity)
```

**Name resolution** ("las fotos", "el correo", "la cámara", "WhatsApp"): match the
spoken `app_name` against (a) a small **alias map** of colloquial names → packages
(curated for the common ones — Cámara, Galería/Fotos, WhatsApp, Teléfono, Mensajes,
Ajustes, the user's preferred email/browser), then (b) fuzzy match against installed
app labels (normalised: lowercase, strip accents, contains/Levenshtein). Multiple
matches with similar scores → treat as ambiguous → confirm/clarify. No match →
"No tengo ninguna app que se llame así" (and log it — maybe it deserves an alias).
Favourite apps are promoted to the home grid by use (see `local-data`).

## Volume (`set_volume`, Fase 2)

Permission: none. Use `AudioManager`:

```kotlin
val am = context.getSystemService(AudioManager::class.java)
when (direction) {
    Up   -> repeat(amount) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
    Down -> repeat(amount) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
    Max  -> am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
    Mute -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
}
```

Decide which stream(s) to act on (media vs. ring vs. "everything") — for this user
"sube el volumen" almost certainly means *I want to hear things louder*; bumping ring
+ media together is probably right. Confirm by voice + TTS at the new level so they
hear the effect. `needs_confirmation: false`.

## Resolving contacts + the ambiguity flow (`ContactsContract`)

Permission: `READ_CONTACTS`. Used by `call_contact` (and the alias-learning subflow —
see `local-data`).

```kotlin
// data/contacts/ContactsProvider.kt
fun findByName(query: String): List<Contact> = context.contentResolver.query(
    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
    arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI),
    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$query%"), null,
)?.use { /* map rows → Contact(id, name, numbers, photoUri) */ } ?: emptyList()
```

**Resolution order for a spoken `contact`:**
1. Exact/learned **alias** match ("mi hija" → Lucía Ruiz) → resolve directly, no prompt.
2. Single contact whose name matches → use it (subject to the confidence policy).
3. **Several matches** ("María" → 3 Marías, none aliased) → `CurroError.AmbiguousContact(matches)`
   → **always confirm** (ignore confidence): show all candidates with full name +
   photo + a "Ninguna" option; read up to ~3 by voice ("Tienes tres Marías. ¿Cuál?:
   María García, María López o María Ruiz"). A non-matching answer → repeat once →
   then give up honestly ("Mejor llámala desde la agenda, no me aclaro"). Don't learn
   an alias mid-call (the learning offer is deferred — see `local-data`).
4. **Relational term not yet mapped** ("mi hija") with no alias → trigger the
   alias-learning subflow (`local-data` / spec flow 4): ask once "¿Es alguno de
   estos?", read up to 5, "dime su nombre" if more, "Ninguno" → "Vale, dile a Fran
   que apunte quién es tu hija".
5. **No match at all** → `CurroError.ContactNotFound(query)` → "No encuentro a … en
   tus contactos."

Multiple numbers for one contact → prefer mobile; if genuinely ambiguous, that's
another (small) confirmation.

## Testing (see `testing-patterns`)

- Put every integration behind a `domain/repository/` interface; **fake it** in
  unit tests. Handlers are tested against fakes — never against real
  ContentResolver / NotificationManager / Telecom.
- `WhatsAppNotificationParser`: a fixture suite of `StatusBarNotification`s —
  MessagingStyle 1:1, MessagingStyle group, legacy `extras`, summary notification,
  emoji-only body, voice note, image, malformed/unknown shape → asserts the parsed
  output (or a clean "parse miss" with no crash). This is the highest-value test in
  the app (spec risk).
- `open_app` name resolution: alias hits, fuzzy hits, accent-insensitivity, ties →
  ambiguous, no match → `AppNotFound`.
- Contacts: alias-first, single match, 3-Marías → `AmbiguousContact`, relational-not-
  mapped → learning subflow, no match → `ContactNotFound`, multiple numbers.
- On the **real Redmi 15**: notification access actually reads WhatsApp; `ACTION_CALL`
  dials; `open_app` opens the named app; volume changes are audible; the incoming-
  call mode (when toggled on) announces and answers — and toggled *off*, incoming
  calls behave 100 % natively.

## Permissions summary (request only when the capability is used — spec §10)

| Permission | Integration | Requested when |
|---|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` + notification access | WhatsApp read/reply | first WhatsApp function used |
| `READ_CONTACTS` | contact resolution | first `call_contact` |
| `CALL_PHONE` | placing calls | first `call_contact` |
| `QUERY_ALL_PACKAGES` (manifest-declared) | open apps by name | always declared |
| `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS` *(opt-in)* | incoming-call assistant | only when Fran enables the toggle |
| `READ_SMS` *(opt-in)* | `read_sms` (Fase 2) | only when that feature is used |
| `SYSTEM_ALERT_WINDOW` *(eval)* | overlay over other apps | only if the overlay feature is enabled |

## Rules

1. **Every integration behind an interface; handlers + tests never touch Android system APIs directly.**
2. **The WhatsApp parser is defensive** — prefer `MessagingStyle`, fall back gracefully, never crash, never invent content; record parse misses for diagnostics.
3. **`ACTION_CALL`, not `ACTION_DIAL`** — Curro dials; but `call_contact` always confirms on ambiguity.
4. **Incoming-call mode is opt-in, OFF by default** — when off, telephony is 100 % native; it must never degrade the phone's ability to receive calls.
5. **Request permissions lazily**, per the table — the user never sees a prompt for a capability they aren't using; on revocation, fail with a plain Spanish "díselo a Fran".
6. **Ambiguity always confirms** — multiple contacts/apps → list them + a "Ninguna"/"Ninguno" out; don't loop; don't learn aliases mid-call.
