# Git Workflow

Git conventions for the Curro project. Branch from `main`, conventional commits with
Curro scopes, ask before creating a branch or pushing, stage specific files. Pairs
with `verification-checklist` (run it before committing) and `generate-mr-description`
(for the PR body).

## Branch Naming

Curro has a single long-lived branch — **`main`**. Branch from `main` for every piece
of work:

```
feature/[US-XXX]-[description]
fix/[issue]-[description]
hotfix/[description]
chore/[description]
```

### Examples

```
feature/US-007-call-contact-handler
feature/US-003-launcher-home-screen
fix/ISSUE-42-whatsapp-group-chat-parsing
hotfix/model-warmup-service-crash
```

## Creating Branches

NEVER create a branch without asking the user first:

```
This should go on its own branch. Proposed name:

  feature/US-007-call-contact-handler

Proceed?
```

Wait for explicit confirmation:

```bash
git checkout main && git pull
git checkout -b feature/US-007-call-contact-handler
```

## Conventional Commits

```
<type>(<scope>): <subject>

<body>
```

### Commit types
- `feat` — new feature
- `fix` — bug fix
- `refactor` — restructure, no behaviour change
- `test` — adding or updating tests
- `chore` — maintenance (deps, CI, tooling)
- `docs` — documentation / spec / skills

### Commit scopes (Curro)

| Scope | Covers |
|---|---|
| `launcher` | the launcher home / `CATEGORY_HOME` / the app grid / "Más apps" |
| `voice` | the mic button + the STT/TTS pipeline glue |
| `stt` | the `SpeechRecognizer` wrapper / partials / offline-ES handling |
| `tts` | the `TextToSpeech` wrapper / voice / rate / pitch |
| `fsm` | the assistant state machine + the coordinator (idle/listening/processing/confirming/executing/error_recovery, interrupt-by-button, recovery) |
| `llm` | FunctionGemma / Gemma 3n / MediaPipe / prompt builders / validator / model warm-up |
| `handler` | a function handler in the execution layer |
| `catalog` | the function catalog (`domain/catalog/` + the `function-catalog` skill + spec §5) |
| `alias` | alias learning / the alias subflow |
| `config` | the Fran-only config menu |
| `data` | Room / DataStore persistence (DAOs, entities, settings) |
| `notif` | `NotificationListenerService` / WhatsApp parsing / unread cache |
| `telecom` | placing calls / `InCallService` / incoming-call mode |
| `apps` | open-app / `PackageManager` / installed-apps provider |
| `ui` | composables / overlays / shared big components |
| `vm` | ViewModels / UI state |
| `nav` | navigation (minimal — home ⇄ config) |
| `model` | domain models |
| `theme` | `CurroTheme` / colours / typography / shapes / spacing |
| `di` | Hilt modules |
| `ci` | CI / GitHub Actions / build scripts |

### Examples

```
feat(handler): implement tell_time handler
feat(fsm): add assistant state machine with interrupt-by-button
feat(launcher): home screen — clock, mic button, app grid
feat(llm): wire FunctionGemma inference + catalog prompt
feat(catalog): add open_app function
fix(notif): handle WhatsApp MessagingStyle group chats
fix(telecom): use ACTION_CALL so call_contact dials directly
refactor(vm): hoist clock state out of LauncherViewModel
test(fsm): cover consecutive-STT-failure recovery
test(notif): WhatsAppNotificationParser fixture suite
chore(ci): decode google-services.json from the CI secret
docs(catalog): document the {action, params, confidence} contract
```

## Commit Examples

### Single line (simple change)
```
feat(handler): implement calculate handler
```

### Multi-line (detailed change)
```
feat(fsm): add the assistant state machine

- States: idle / listening / processing / confirming / executing / error_recovery
- A button press in any state cancels in-flight work and returns to listening
- Consecutive-STT-failure recovery (1st / 2nd / 3rd message, then give up + reset)
- 10 s silence in `confirming` → "Cancelo entonces" → idle
- Single owner of transitions; the launcher observes one StateFlow<AssistantState>

Closes: US-005
```

### With Co-Authored-By
```
fix(notif): be defensive parsing WhatsApp notifications

- Prefer MessagingStyle; fall back to extras and to the summary notification
- Distinguish 1:1 vs group chats; voice notes / images → a short description
- Unknown shape → parse miss (no crash, no invented content); record it for diagnostics

Co-Authored-By: Claude <noreply@anthropic.com>
```

## Staging Files

Always stage specific files — **NEVER** `git add -A` / `git add .`:

```bash
# Good: specific files
git add app/src/main/java/com/curro/app/handler/TellTimeHandler.kt
git add app/src/test/java/com/curro/app/handler/TellTimeHandlerTest.kt

# Good: a directory
git add app/src/main/java/com/curro/app/assistant/

# Bad
git add -A
git add .
```

## Commit & Push Workflow

1. **Stage** — `git add path/to/file1.kt path/to/file2.kt`
2. **Verify** — `git status` then `git diff --staged`
3. **Commit** — `git commit -m "feat(handler): implement tell_time handler"`
4. **Review** — `git log -1` / `git log --oneline -5`
5. **Push** — NEVER push without asking:

```
Commit created:

  feat(handler): implement tell_time handler

Push to the remote branch?
```

Wait for confirmation:

```bash
git push -u origin feature/US-005-tell-time-handler
```

## Creating Pull Requests

Target branch: **`main`**.

PRs include: a descriptive title, a summary of changes, an acceptance-criteria
checklist, a link to the user story (US-XXX), screenshots for UI changes, testing
notes. Generate the body with `/generate-mr-description`.

```markdown
## Summary
Adds the `tell_time` handler — Curro says the current time / day / date out loud.
First handler in the execution layer; validates the architecture end-to-end at zero risk.

## Changes
- `TellTimeHandler` (handler/) registered in the function-name multibinding map
- `TellTimeUseCase` (domain/usecase/) over a `ClockProvider`
- Spanish copy in resources, in Curro's voice
- Unit tests: success for each `what` value (time / date / day / all)

## Acceptance Criteria
- [ ] "qué hora es" → speaks the time
- [ ] "qué día es hoy" → speaks the day
- [ ] Output is spoken AND shown
- [ ] No new permissions; no model weights required to build/test

## Related
Closes: US-005

## Testing
`./gradlew test` green; manually verified on the Redmi 15 (offline).
```

## Review Checklist (before opening a PR)

- Branch created from the latest `main`
- All commits follow the conventional format with a Curro scope
- `verification-checklist` passed (build / lint / tests / device / privacy)
- No sensitive data and no model weights in the commits
- Co-author credited if applicable
- Related user story referenced

## Undoing Changes

```bash
git reset --soft HEAD~1          # undo last commit, keep changes staged
git reset --hard HEAD~1          # undo last commit, discard changes
git reset HEAD path/to/file.kt   # unstage a file
git checkout -- path/to/file.kt  # discard unstaged changes to a file
```

## Common Issues

### Push rejected (need to pull first)
```bash
git pull origin feature/US-005-tell-time-handler
git push origin feature/US-005-tell-time-handler
```

### Merge conflicts
1. `git pull origin main` (or rebase onto `main`)
2. Resolve conflicts in the IDE
3. Stage the resolved files
4. Commit: `chore: resolve merge conflicts with main`

### Add another file to the last commit
```bash
git add path/to/file.kt
git commit --amend --no-edit
```
</content>
