# Curro Android — Product Requirements Document

> **App**: Curro — Android launcher + on-device voice assistant for an elderly user
> **Platform**: Android (API 31+ / Android 12+), target 34+
> **Package**: `com.curro.app`
> **Created**: 2026-05-13
>
> 📄 **Source of truth: [`curro-spec-v1.0.md`](curro-spec-v1.0.md)** — every story here
> should cite the section(s) of the spec it implements. See [`BOOTSTRAP.md`](BOOTSTRAP.md)
> for repo state and the full init checklist.

---

## How this document works

User stories are grouped into **phases** that follow the spec's suggested build
order (spec §14). Each phase is a shippable, demoable increment. Persistence, system
integrations, and ML pieces are introduced just-in-time per phase, not all upfront.
(Note: Curro is **fully on-device** — there's no backend, so the "data layer" here
means local storage + Android system integrations, not REST APIs.)

**Workflow** (see `CLAUDE.md`):
1. Add a user story below with acceptance criteria — `/create-prd "<description>"`.
2. `/generate-brief US-XXX` → produces `docs/briefs/US-XXX-<slug>.md` (spec + tasks).
3. `/implement-feature US-XXX` → work through the brief, ticking tasks as you go.
4. Verify with the `verification-checklist` skill (build → lint → test → run; on the real Redmi 15 for voice/ML).

**User story format**:

> ### US-XXX: <Short title>  ·  _(spec §X.Y)_
> **As a** <role>, **I want** <capability> **so that** <benefit>.
>
> **Acceptance Criteria**:
> - [ ] …
> - [ ] …
>
> **Size**: S | M | L  ·  **Depends on**: US-YYY _(optional)_

---

## Phase 0 — Project foundation _(spec §14, BOOTSTRAP §4.0)_

> Gradle project + version catalog · Hilt + `CurroApp` · Material-3 theme
> (`CurroTheme`/`CurroColorScheme`/`CurroTypography`/`CurroShapes`/`CurroSpacing`,
> accessibility-first) · ktlint+detekt · base package layout · LiteRT/MediaPipe deps
> wired (no models loaded yet). Add the stories.

### US-001: <project skeleton>
_To be filled in — `/create-prd`._

---

## Phase 1 — Launcher base _(spec §11, §14 step 1)_

> `MainActivity` as `CATEGORY_HOME` launcher · "set as default launcher" flow ·
> home screen: big clock+date, large mic button (≥40% screen, haptic), 4–6 large app
> tiles, "Más apps" screen. **No assistant yet.** Validate with the real user that
> this replaces the stock launcher.

_Stories TBD._

---

## Phase 2 — Voice pipeline _(spec §4.2, §4.6, §14 step 2)_

> `RECORD_AUDIO` · native offline `SpeechRecognizer` (Spanish) with live transcription ·
> native `TextToSpeech` (Spanish, slowed ~10–15%) · the capture→response loop, no
> decision model yet. Confirm it works on-device.

_Stories TBD._

---

## Phase 3 — FunctionGemma decision layer _(spec §4.3, §5 Fase 1 catalogue, §14 step 3)_

> Load FunctionGemma 270M (int8) via MediaPipe LLM Inference · keep warm in a
> foreground service (`POST_NOTIFICATIONS`), <500 ms text→JSON · prompt = Fase-1
> function catalogue + minimal context · validate output against the catalogue JSON
> schema · show returned JSON on screen for debugging · friendly fallback on invalid
> output (flow 7), no auto-retry.

_Stories TBD._

---

## Phase 4 — Fase 1 handlers _(spec §5 Fase 1, §14 step 4 — in order)_

> Low-risk first: `tell_time` · `open_app` (`QUERY_ALL_PACKAGES`) · `calculate` ·
> `help`. Then sensitive permissions: `read_last_whatsapp` · `read_all_unread_whatsapp`
> (`NotificationListenerService`, robust parser + tests + fallback) · `call_contact`
> (`READ_CONTACTS` + `CALL_PHONE`, contact/alias resolution).

_One story per handler — TBD._

---

## Phase 5 — State machine & interruption _(spec §6, §14 step 5)_

> `idle`/`listening`/`processing`/`confirming`/`executing`/`error_recovery` · new
> button press interrupts current state → `listening` · 10 s no-answer timeout in
> `confirming` · consecutive-STT-failure policy (1st/2nd/3rd message, then give up).

_Stories TBD._

---

## Phase 6 — Confidence-graded confirmation _(spec §4.3, §14 step 6)_

> `needs_confirmation` ∈ {`false`,`true`,`conditional`} · `conditional`: ≥0.85
> execute / 0.60–0.85 confirm / <0.60 clarify · thresholds adjustable from settings ·
> always escalate to confirm on explicit ambiguity or "always confirm" mode.

_Stories TBD._

---

## Phase 7 — Alias learning & local persistence _(spec §7, flow 4, §14 step 7)_

> Local DB (Room/SQLite or DataStore): contact aliases, implicit favourite apps,
> usage times, failed-command log · learn **one alias per interaction**, never
> mid-call · aliases viewable/editable from the settings menu.

_Stories TBD._

---

## Phase 8 — Settings menu (Fran-only) _(spec §9, §14 step 8)_

> Hidden screen opened by tapping the clock 5× within 3 s · aliases · launcher
> favourites · TTS voice/speed/pitch · incoming-call assistant toggle (§8, off by
> default) · confidence-threshold sliders · "always confirm" toggle · failed-command
> log (last 50) · "send me the failures" toggle · reset learning · version & diagnostics.

_Stories TBD._

---

## Phase 9 — Gemma 3n content layer _(spec §4.4, §14 step 9)_

> Load Gemma 3n E2B (int4) on demand · "Dame un segundo" while cold · 3–6 s typical ·
> may not be needed in Fase 1 — decide whether to wire now or defer to Fase 2.

_Stories TBD._

---

## Later — Fase 2+ _(spec §5)_

`send_whatsapp_reply`, `set_volume`, `read_sms`, `set_reminder`, voice notes → Fase 3
(thread summaries, video calls, translate, medication reminders) → Fase 4 (proactive
alerts, "explain current screen" via Accessibility Service, routine learning, incoming
photo description). Not for the prototype.
