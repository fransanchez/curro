---
name: testing-patterns
description: Testing patterns for Curro (Android) — JUnit5 + Mockk + Turbine + Compose UI Test + Robolectric, plus the Curro-specific patterns (faking the on-device LLM / STT / TTS, the assistant state machine, the confidence policy, the WhatsApp notification parser fixtures, handlers against faked system integrations, in-memory Room, alias learning, senior-UI Compose tests).
triggers:
  - JUnit
  - test
  - mockk
  - turbine
  - compose test
  - unit test
  - ui test
  - robolectric
  - coverage
  - fake LLM
  - state machine test
  - parser fixtures
---

# Testing Patterns

Testing patterns for the Curro app — JUnit5 + Mockk + Turbine + Compose UI Test +
Robolectric. The general mechanics (below) are the same as any modern Android app;
the **Curro-specific patterns** further down are where the real coverage lives —
read those alongside `function-catalog`, `voice-interaction`, `on-device-llm`,
`platform-integrations`, and `local-data`. Verify completion with
`verification-checklist`.

## Stack

| Tool | Purpose |
|---|---|
| JUnit5 (`org.junit.jupiter.api.*`) | Unit test framework — **NOT JUnit4** |
| Mockk | Kotlin-first mocking (`coEvery`/`coVerify`) |
| Turbine | `Flow`/`StateFlow` testing |
| Coroutines Test | `runTest`, `advanceUntilIdle()`, `advanceTimeBy()`, test dispatchers |
| Robolectric | Android-dependent **unit** tests without an emulator (the state machine, parsers using framework types, Room in-memory, DataStore) |
| Compose UI Test (`createComposeRule()`) | Composable rendering + interaction |
| Hilt Testing (`com.curro.app.HiltTestRunner`) | DI in instrumented tests |

## File locations

```
app/src/
├── test/java/com/curro/app/              # Unit tests (JVM + Robolectric) — JUnit5
│   ├── TestDispatcherExtension.kt                     # shared JUnit5 extension (@RegisterExtension)
│   ├── assistant/
│   │   ├── AssistantStateMachineTest.kt
│   │   └── ConfidencePolicyTest.kt
│   ├── data/ml/
│   │   ├── FunctionCallPromptBuilderTest.kt           # golden-string test
│   │   └── FunctionCallValidatorTest.kt
│   ├── data/notification/
│   │   └── WhatsAppNotificationParserTest.kt          # the highest-value test
│   ├── handler/
│   │   └── <Function>HandlerTest.kt
│   ├── data/local/
│   │   ├── dao/<Entity>DaoTest.kt                     # in-memory Room
│   │   └── SettingsRepositoryTest.kt
│   ├── domain/usecase/
│   │   └── <UseCase>Test.kt
│   └── presentation/<surface>/
│       └── <Surface>ViewModelTest.kt
├── androidTest/java/com/curro/app/        # Instrumented tests (Compose UI test + Hilt)
│   ├── HiltTestRunner.kt
│   └── presentation/
│       ├── launcher/LauncherScreenTest.kt
│       ├── assistant/{ListeningOverlayTest,ConfirmationOverlayTest,MessageCardsScreenTest}.kt
│       └── config/ConfigMenuScreenTest.kt
```

(Instrumented test runner: `com.curro.app.HiltTestRunner`, set as `testInstrumentationRunner` in `app/build.gradle.kts`.)

## Test naming

Backtick method names describing behaviour:

```kotlin
@Test fun `emits Ready when clock and favourites load`() { }
@Test fun `rejects fenced JSON with InvalidFunctionCall`() { }
@Test fun `button press in confirming returns to listening`() { }
@Test fun `third consecutive STT failure gives up and resets the counter`() { }
```

## JUnit5 — always; never JUnit4

```kotlin
// CORRECT — JUnit5
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.RegisterExtension

// WRONG — JUnit4 (never in unit tests)
// import org.junit.Test ; import org.junit.Before ; import org.junit.Assert.*
```

## ViewModel testing — `TestDispatcherExtension`

Every ViewModel test uses the shared `TestDispatcherExtension` (a JUnit5 extension
that swaps `Dispatchers.Main` for a `TestDispatcher`):

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureViewModelTest {

    @JvmField
    @RegisterExtension
    val dispatcherExtension = TestDispatcherExtension()

    private val getFeature: GetFeatureUseCase = mockk()

    @Test
    fun `emits Success when data loads`() = runTest {
        val data = listOf(FeatureItem("1", "Test"))
        coEvery { getFeature() } returns Result.success(data)

        val viewModel = FeatureViewModel(getFeature)

        viewModel.uiState.test {
            assertEquals(FeatureUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertEquals(data, (awaitItem() as FeatureUiState.Success).items)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits Error when the use case fails`() = runTest {
        coEvery { getFeature() } returns Result.failure(IllegalStateException("boom"))
        val viewModel = FeatureViewModel(getFeature)
        viewModel.uiState.test {
            assertEquals(FeatureUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertIs<FeatureUiState.Error>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

## UseCase testing

```kotlin
class TellTimeUseCaseTest {
    private val clock: ClockProvider = mockk()
    private lateinit var useCase: TellTimeUseCase

    @BeforeEach fun setUp() { useCase = TellTimeUseCase(clock) }

    @Test fun `delegates to the clock provider`() = runTest {
        every { clock.now() } returns aFixedInstant
        val result = useCase(what = TimeQuery.ALL)
        assertTrue(result.isSuccess)
        verify(exactly = 1) { clock.now() }
    }
}
```

## Compose UI testing — test `Content`, not `Screen`

`Screen` composables inject ViewModels via `hiltViewModel()`. Test the **stateless
`Content` composable** (receives state, emits events). Wrap in `CurroTheme { }`.

```kotlin
class LauncherContentTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test fun `tapping an app tile fires AppTileTapped`() {
        var tapped: String? = null
        composeTestRule.setContent {
            CurroTheme {
                LauncherContent(
                    uiState = LauncherUiState.Ready(clock = aClockState, apps = listOf(AppTile("WhatsApp", "com.whatsapp"))),
                    assistantState = AssistantState.Idle,
                    onEvent = { if (it is LauncherEvent.AppTileTapped) tapped = it.packageName },
                )
            }
        }
        composeTestRule.onNodeWithText("WhatsApp").performClick()
        assertEquals("com.whatsapp", tapped)
    }
}
```

## Flow testing with Turbine

```kotlin
@Test fun `state stream goes Loading then Ready`() = runTest {
    val viewModel = LauncherViewModel(observeClock, observeFavoriteApps)
    viewModel.uiState.test {
        assertIs<LauncherUiState.Loading>(awaitItem())
        advanceUntilIdle()
        assertIs<LauncherUiState.Ready>(awaitItem())
        expectNoEvents()
    }
}
```

## Mockk patterns

```kotlin
coEvery { engine.decide(any(), any()) } returns Result.success(aFunctionCall)   // suspend
every { settings.thresholds } returns Thresholds(executeMin = 0.85f, confirmMin = 0.60f)
private val tts: TtsClient = mockk(relaxed = true)                              // relaxed
coVerify(exactly = 1) { handlerMap["call_contact"]!!.handle(any()) }
coVerify(exactly = 0) { callController.call(any()) }                            // never dialled
val slot = slot<FunctionCall>()
coEvery { handler.handle(capture(slot)) } returns HandlerResult.Spoken("ok")
assertEquals("call_contact", slot.captured.action)
```

---

# Curro-specific test patterns

## 1. Fake the on-device LLM — never load real models in JVM tests

The model engines are behind `domain/repository/` interfaces (`FunctionCallEngine`
for FunctionGemma, `TextGenEngine` for Gemma 3n — see `on-device-llm`). Tests use a
**fake** that returns canned `Result<FunctionCall>` / `Result<String>`:

```kotlin
class FakeFunctionCallEngine : FunctionCallEngine {
    var nextResult: Result<FunctionCall> = Result.success(FunctionCall("tell_time", emptyMap(), 0.9f))
    var ready = true
    override suspend fun decide(utterance: String, context: PromptContext) = nextResult
    override fun warmUp() { ready = true }
    override fun isReady() = ready
}
```

**Test the prompt builder** (`FunctionCallPromptBuilder`) — golden string for a known
catalog + context (current time, unread-msg summary, known aliases): the rendered
prompt is byte-for-byte the expected text. **Test the validator**
(`FunctionCallValidator.parseAndValidate(raw)`) — accepts well-formed JSON; rejects
each malformation with the right `CurroError`:

| Input | Expected |
|---|---|
| `{"action":"tell_time","params":{},"confidence":0.9}` | `Result.success(FunctionCall(...))` |
| not JSON / `"hola"` | `CurroError.InvalidFunctionCall` |
| ```` ```json {...} ``` ```` (fenced) | strip fences → success (or `InvalidFunctionCall` if still bad — pick one and test it) |
| missing `action` / `"action":""` | `CurroError.InvalidFunctionCall` |
| `"action":"order_pizza"` (not in this phase) | `CurroError.UnknownFunction("order_pizza")` |
| required param missing | `CurroError.InvalidFunctionCall` |
| param wrong type (`"amount":"two"`) | `CurroError.InvalidFunctionCall` |
| extra/unknown param | `CurroError.InvalidFunctionCall` |
| `"confidence":1.7` / non-number | `CurroError.InvalidFunctionCall` |
| engine not warmed | `decide()` → `Result.failure(CurroError.ModelCold)` |
| `OutOfMemoryError` thrown by the engine | mapped to `CurroError.OutOfMemory` |
| any invalid output | **no automatic retry** — assert `decide()` is invoked exactly once |

## 2. Fake STT / TTS

`SttClient` and `TtsClient` live in `data/voice/` behind interfaces. Use fakes:

```kotlin
class FakeSttClient : SttClient {
    var script: List<SttEvent> = emptyList()   // partials then final, or an error
    override fun listen() = script.asFlow()
    var cancelled = false
    override fun cancel() { cancelled = true }
}
class FakeTtsClient : TtsClient {
    val spoken = mutableListOf<String>()
    var stopped = false
    override suspend fun speak(text: String) { spoken += text }
    override fun stop() { stopped = true }
}
```

Assert: TTS is stopped (`stopped == true`) when the button is pressed mid-read (the
interrupt rule); the STT session is cancelled on interrupt; the strings spoken match
the expected Spanish utterances (the strings themselves come from resources — assert
the *resource keys* / formatted output, not hard-coded literals).

## 3. The assistant state machine (`AssistantStateMachine`)

Robolectric (it touches `Handler`/timers) + the fakes above. Cover **every transition
in spec §6's diagram and flows 1–7**:

- `idle` → `listening` on a button press; STT final → `processing`.
- `processing` → `executing` (via `ConfidencePolicy`: high confidence / `needs_confirmation: false`) → TTS → `idle`.
- `processing` → `confirming` (medium confidence / `needs_confirmation: true`) → "sí" → `executing`; "no" or 10 s silence → `idle` ("Vale, no llamo" / "Cancelo entonces").
- `processing` → `error_recovery` on STT failure or invalid model output.
- **Interrupt rule:** a button press in **each** of `listening` / `processing` / `confirming` / `executing` / `error_recovery` cancels the in-flight work and returns to `listening` — one test per source state.
- **Consecutive STT failures** (counter resets on any success): 1st fail → "No te he oído bien, ¿puedes repetirlo?"; 2nd → "Sigo sin entenderte. Acércate un poco al teléfono y habla más alto."; 3rd → "Vamos a dejarlo. Si quieres, pulsa el botón otra vez cuando estés listo." then `idle` and the counter resets.
- **`confirming` timeout:** 10 s of silence → "Cancelo entonces" → `idle` (use `advanceTimeBy(10_000)`).
- **Disambiguation** (3 Marías): non-matching answer → repeat the options **once**; a second miss → give up honestly ("Mejor llámala desde la agenda, no me aclaro") → `idle`. Don't loop.
- **Invalid model output** (spec flow 7): friendly fallback ("Eso no lo sé hacer todavía…") + the utterance is logged to the failed-commands log + **no retry** + `idle`.
- `onNewIntent` / HOME pressed → FSM resets to `idle`.

## 4. `ConfidencePolicy`

```kotlin
@Test fun `executes directly above the execute threshold`() { /* 0.92, conditional → Execute */ }
@Test fun `confirms in the middle band`() { /* 0.70, conditional → Confirm */ }
@Test fun `clarifies below the confirm threshold`() { /* 0.40, conditional → Clarify */ }
@Test fun `false always executes`() { /* needs_confirmation = false, any confidence → Execute */ }
@Test fun `true always confirms`() { /* needs_confirmation = true, confidence 0.99 → Confirm */ }
@Test fun `ambiguous contact always confirms`() { /* conditional + AmbiguousContact, confidence 0.99 → Confirm */ }
@Test fun `always-confirm toggle forces confirmation`() { /* conditional + setting on → Confirm */ }
@Test fun `irreversible cost always confirms`() { /* conditional + cost flag → Confirm */ }
@Test fun `uses edited thresholds from settings`() { /* executeMin=0.95 → 0.92 conditional now Confirm */ }
```

(Defaults: ≥ 0.85 execute / 0.60–0.85 confirm / < 0.60 clarify — editable from the config menu, see `local-data`.)

## 5. `WhatsAppNotificationParser` — the highest-value test (spec risk)

A **fixture suite** of `StatusBarNotification`s (build them with `Notification.Builder`
/ `NotificationCompat.MessagingStyle` under Robolectric; or use `/fixture
whatsapp-notifications`). Each fixture → assert the parsed `WhatsAppMessage`(s) **or**
a clean "parse miss" (returns `null`, no exception):

| Fixture | Expected |
|---|---|
| `MessagingStyle`, 1:1 chat, one message | one `WhatsAppMessage(sender, body, timestamp)` |
| `MessagingStyle`, group chat, several messages | per-message entries with the group + per-message sender, marked as a group |
| legacy `extras` (`EXTRA_TITLE` + `EXTRA_TEXT` / `EXTRA_TEXT_LINES`) | parsed from `extras` |
| summary notification ("3 mensajes nuevos de 2 chats") | recognised as "there *are* unread" but no fake bodies |
| emoji-only body | "[un emoji]" (or skipped) — never a crash |
| voice note | "te ha mandado un audio" |
| image / media | "te ha mandado una foto" |
| malformed / unknown shape | `null` (parse miss), no exception — and the cache records the miss |

Also: `UnreadMessageCache` upsert keyed by sender in arrival order; `onRemoved` clears
that sender's entries; `read_last_whatsapp` / `read_all_unread_whatsapp` read from it.

## 6. Handlers — against faked system integrations

Each `FunctionHandler` is tested against fakes of the integrations in
`platform-integrations` (`FakeContactsProvider`, `FakeInstalledAppsProvider` /
`FakeAppLauncher`, `FakeCallController`, `FakeUnreadMessageCache`, a fake
`AudioManager` wrapper, …). Map **every outcome**:

- success → `HandlerResult.Spoken(speech, screen?)` — assert the spoken string and any screen state;
- ambiguity → `HandlerResult.NeedsConfirmation(prompt, onConfirm)` (e.g. `CallContactHandler` with 3 Marías; `OpenAppHandler` with two close fuzzy matches);
- each `HandlerError` → `HandlerResult.Failed(speech, reason)` with a **plain-Spanish** `speech` (never a code): `ContactNotFound`, `AppNotFound`, `Calculation`, `NotificationAccessMissing`, `PermissionDenied`;
- **permission-missing path**: `CallContactHandler` when `CALL_PHONE`/`READ_CONTACTS` is denied → `Failed` with "Necesito permiso para llamar; dile a Fran que lo active" — never a crash, never a raw `SecurityException`.

```kotlin
class CallContactHandlerTest {
    private val contacts = FakeContactsProvider()
    private val aliases = FakeAliasRepository()
    private val caller = FakeCallController()
    private val handler = CallContactHandler(contacts, aliases, caller)

    @Test fun `dials directly for a single match`() = runTest {
        contacts.add(Contact(lookupKey = "k1", name = "Pepito", numbers = listOf("600111222")))
        val result = handler.handle(FunctionCall("call_contact", mapOf("contact" to "Pepito"), 0.95f))
        assertIs<HandlerResult.Spoken>(result)
        assertEquals("600111222", caller.lastNumber)
    }

    @Test fun `returns NeedsConfirmation for three Marías`() = runTest {
        repeat(3) { contacts.add(Contact("k$it", "María ${'A' + it}", listOf("60000000$it"))) }
        val result = handler.handle(FunctionCall("call_contact", mapOf("contact" to "María"), 0.95f))
        assertIs<HandlerResult.NeedsConfirmation>(result)
        assertNull(caller.lastNumber)
    }

    @Test fun `fails plainly when CALL_PHONE is denied`() = runTest {
        caller.throwOnCall = SecurityException()
        contacts.add(Contact("k1", "Pepito", listOf("600111222")))
        val result = handler.handle(FunctionCall("call_contact", mapOf("contact" to "Pepito"), 0.95f))
        assertIs<HandlerResult.Failed>(result)
        assertEquals(CurroError.PermissionDenied, result.reason)
    }
}
```

## 7. Room DAOs — in-memory database

```kotlin
class ContactAliasDaoTest {
    private lateinit var db: CurroDatabase
    private lateinit var dao: ContactAliasDao

    @BeforeEach fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CurroDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.contactAliasDao()
    }
    @AfterEach fun tearDown() { db.close() }

    @Test fun `alias is unique and stored normalised`() = runTest {
        dao.upsert(ContactAliasEntity(alias = "mi hija", contactLookupKey = "k1", displayNameAtLearnTime = "Lucía Ruiz", source = AliasSource.LEARNED, createdAt = 0))
        // re-inserting the same normalised alias replaces, doesn't duplicate
        dao.upsert(ContactAliasEntity(alias = "mi hija", contactLookupKey = "k2", displayNameAtLearnTime = "Otra", source = AliasSource.EDITED, createdAt = 1))
        assertEquals(1, dao.getAll().size)
        assertEquals("k2", dao.findByAlias("mi hija")?.contactLookupKey)
    }
}
```

Also: `FailedCommandDao` keeps only the last ~50 (oldest trimmed on insert);
`AppUsageDao` upsert/increment of `openCount` + `lastOpenedAt`; **"reset learning"**
clears `contact_aliases` + `app_usage` + `interaction_log` + `failed_commands` (and
nothing else). `SettingsRepository`: defaults (`executeMin = 0.85`, `confirmMin =
0.60`, TTS rate ~0.85–0.90, all toggles off), value round-trips, emits on change.

## 8. The alias-learning subflow (spec flow 4)

Fake `ContactsProvider` + fake `AliasRepository`:

- unmapped relational term ("mi hija") → handler enters learning mode → user picks "Lucía" → `ContactAliasEntity` persisted with `source = LEARNED` → a subsequent `call_contact{contact:"mi hija"}` resolves directly with no prompt;
- "ninguno de estos" → "Vale, dile a Fran que apunte quién es tu hija" and **nothing persisted** — and it doesn't keep asking;
- **never asks for more than one alias per interaction**;
- **doesn't learn an alias when the action came from an ambiguity prompt** (the 3-Marías path runs the call and skips the learning offer).

## 9. UI tests (Compose, instrumented — `Content` composables)

- **`LauncherScreen`**: renders the clock + the mic button + the favourites grid; the mic button node is ≥ 96 dp (in fact ≥ 40 % of the screen); a tile tap fires `AppTileTapped`; **5 quick taps on the clock** fire the config-open event, a **single** tap does not.
- **`ListeningOverlay`**: shows the live transcript text as partials arrive; tints; a mic press cancels/restarts.
- **`ConfirmationOverlay`**: SÍ / NO are ≥ 96 dp, high-contrast, fire the right events; the disambiguation list shows N candidate rows + a "Ninguna" row.
- **`MessageCardsScreen`**: cards **grouped by sender**; the message being read aloud is highlighted; empty state shows "No tienes mensajes nuevos".
- **`ConfigMenuScreen`**: each section present (alias, favourite apps, TTS voice, incoming-call mode, confidence thresholds, always-confirm, failed-commands log, send-failures, reset learning, version & diagnostics); the back chevron works.
- **Accessibility sweep** (run on every screen): no `Image`/`Icon` without a `contentDescription` (`hasNoContentDescription() and isImage()` → count 0); every node with a click action is ≥ 96 dp; text scales when the test is run with `fontScale = 1.5f` / `2.0f` (set via the activity's `Configuration`, or assert against a `fontScale` preview).

```kotlin
@Test fun `all clickable nodes are at least 96 dp`() {
    composeTestRule.setContent { CurroTheme { LauncherContent(uiState = readyState, assistantState = AssistantState.Idle, onEvent = {}) } }
    composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
        with(composeTestRule.density) {
            assertTrue(node.size.height.toDp() >= 96.dp, "touch target too small: ${node.size}")
        }
    }
}
```

## Privacy in tests

The telemetry SDKs (Firebase Crashlytics/Analytics, PostHog) and the failed-commands
log on disk must never receive **PII / transcripts / message content / contact
names** — only event names + safe properties. Where the code emits telemetry or
writes logs, assert it: e.g. that a "command failed" event carries the failure *kind*
("INVALID_OUTPUT") and not the transcript; that `interaction_log` rows carry only the
catalog function name, no params, no transcript. (See `CLAUDE.md` → Privacy &
telemetry, and `local-data`.)

## Fixtures (`/fixture <type>`)

`/fixture contacts`, `aliases`, `whatsapp-notifications`, `function-call-json`,
`failed-commands`, `app-list` — generate the test data above (contact lists with the
3-Marías case, alias rows, the `StatusBarNotification` zoo, good/bad FunctionGemma
JSON, capped failed-commands rows, installed-app lists). Keep fixtures small and
deterministic.

## Run tests

```bash
./gradlew test                                            # all unit tests (JVM + Robolectric)
./gradlew test --tests "com.curro.app.assistant.AssistantStateMachineTest"   # one class
./gradlew test --tests "*.WhatsAppNotificationParserTest.*group*"            # one method (glob)
./gradlew connectedAndroidTest                            # instrumented tests (needs a device/emulator) — uses com.curro.app.HiltTestRunner
./gradlew test connectedAndroidTest                       # both
```

## Rules

1. **ALWAYS** JUnit5 (`org.junit.jupiter.api.*`) in unit tests — never JUnit4 in the same file.
2. **ALWAYS** `TestDispatcherExtension` (`@RegisterExtension`) in ViewModel/coroutine tests; **never** `Thread.sleep()` — use `advanceUntilIdle()` / `advanceTimeBy()`.
3. **ALWAYS** wrap Compose test content in `CurroTheme { }`; test the stateless `Content` composable, not the `Screen`-with-ViewModel.
4. **ALWAYS** `cancelAndIgnoreRemainingEvents()` (or `expectNoEvents()`) after the last Turbine assertion.
5. **NEVER** load real model weights in JVM tests — fake `FunctionCallEngine` / `TextGenEngine` (and `SttClient` / `TtsClient`).
6. **NEVER** touch real `ContentResolver` / `NotificationManager` / `Telecom` / Room-on-disk in unit tests — fake the integration behind its `domain/repository/` interface; use in-memory Room.
7. **The state machine, the validator, and the WhatsApp parser are the must-have suites** — they encode the spec's risky bits (flows 1–7, the model contract, the notification format).
8. **Test behaviour through the public API** — not private fields, not implementation details.
9. **No PII / transcripts / message content / contact names** in anything the code logs or sends — assert it where it matters.
</content>
</invoke>
