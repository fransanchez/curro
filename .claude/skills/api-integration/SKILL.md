# API Integration — *parked*

> **Curro has no custom REST backend.** The whole assistant runs on-device (STT,
> FunctionGemma, the handlers, TTS, Gemma 3n) — see `CLAUDE.md`. The core app
> doesn't even hold the `INTERNET` permission. This skill is **not currently used**;
> it's kept for the cases below.

## When this skill would come back

- **Phase 3 `read_news_headlines`** (spec §5, Fase 3) — the *only* catalog function
  that needs the internet: fetch public news headlines over HTTP and read them aloud.
  That's a simple HTTP `GET` of someone else's feed/API, not "our backend".
- A **future companion service** (none planned) — if Curro ever needs a
  Curro-operated server (e.g. centralised failure-log collection beyond the current
  opt-in email), the patterns here apply.

## What does NOT need this skill

- **Firebase (Crashlytics / Analytics) and PostHog** — these SDKs do their own
  networking. You don't write Retrofit for them; you configure the SDK and emit
  events. Their privacy boundary is in `CLAUDE.md` → "Privacy & telemetry": event
  names/properties only — no PII, no transcripts, no message content, no contact
  names.

## If you do add HTTP later — the shape

- Add `INTERNET` to the manifest **and document why** (it changes Curro's privacy
  profile — currently the only network user is the telemetry SDKs).
- Retrofit + OkHttp + `kotlinx.serialization` (or Moshi). One `OkHttpClient`
  (timeouts; a logging interceptor in debug only). DTOs in `data/remote/dto/`, the
  service interface in `data/remote/api/`, mapped to domain models in a repository
  behind a `domain/repository/` interface — same Clean-Architecture rules as
  everything else; nothing outside `data/remote/` touches Retrofit.
- Calls on `Dispatchers.IO`, wrapped in `runCatching` → a `CurroError` (add
  `CurroError.Network` etc. to the sealed type in `CLAUDE.md`), surfaced to the user
  as a plain Spanish sentence (for news: "Ahora mismo no puedo leerte las noticias,
  no tengo conexión").
- It stays **optional** to the app — the launcher and every Phase-1 function must
  keep working with no network.

(The previous restaurant-app-flavoured version of this skill is in git history — it
described a backend Curro doesn't have. See `api-contract`, also parked.)
