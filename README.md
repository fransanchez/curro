# Curro

**An Android launcher + on-device voice assistant for an elderly user** — big clock,
big mic button, a few large app shortcuts; press the button, speak, and a fully
on-device pipeline (native STT → FunctionGemma 270M → native Kotlin handlers →
Gemma 3n E2B when generation is needed → native TTS) reads WhatsApp, makes calls,
opens apps, calculates, tells the time, and more. No cloud, no backend, no internet
dependency. Personality: warm, colloquial Castilian Spanish.

Stack: Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt ·
LiteRT + MediaPipe LLM Inference · Android 12+ (API 31+). Target device: Xiaomi Redmi 15.

## Status — not yet a Gradle project

This repo currently contains only the Claude Code tooling (`.claude/`), `CLAUDE.md`,
and `docs/`. The `app/` module / Gradle wrapper / version catalog haven't been
generated yet.

- 📄 **[`docs/curro-spec-v1.0.md`](docs/curro-spec-v1.0.md)** — the product spec (source of truth).
- 🧭 **[`docs/BOOTSTRAP.md`](docs/BOOTSTRAP.md)** — what's in the repo, what was done to set it up, and the full "what to initialize" checklist. **Start here.**
- 📋 **[`docs/PRD.md`](docs/PRD.md)** — the phased backlog (derived from the spec).
- 📐 **[`CLAUDE.md`](CLAUDE.md)** — architecture conventions + workflow (rewritten for Curro: the launcher, the 5-layer on-device pipeline, the agents/skills, the coding standards).

## Getting started

    cd ~/Projects/curro && claude

Then drive the PRD workflow (first stories: project skeleton → launcher base → voice pipeline):

    /create-prd "launcher home screen — big clock, mic button, favourites grid (spec §11)"
    /generate-brief US-001
    /implement-feature US-001

## Build (once the app module exists)

    ./gradlew assembleDebug      # build debug APK
    ./gradlew test               # unit tests
    ./gradlew ktlintCheck detekt # lint
    ./gradlew installDebug       # install on a connected device / emulator

## Layout

    .claude/                  Claude Code config — agents, skills, commands, hooks, settings
    CLAUDE.md                 Architecture conventions + workflow
    docs/curro-spec-v1.0.md   ⭐ Product spec — source of truth
    docs/BOOTSTRAP.md         What's here & what to initialize (start here)
    docs/PRD.md               Phased product backlog (user stories)
    docs/briefs/              Implementation briefs (spec + tasks), one per user story
