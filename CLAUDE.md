# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Wordly — an Android app for learning English words with Russian translations, built as
Kotlin + Jetpack Compose, MVVM, Room, no DI framework (a hand-rolled `AppContainer`).
`minSdk 26`, `compileSdk`/`targetSdk 35`.

## Commands

Requires `JAVA_HOME` pointed at a JDK 17+ (AGP 8.6 needs it) and the Android SDK. On this
machine, Android Studio's bundled JBR and the SDK at `~/Library/Android/sdk` work:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="/Users/territar/Library/Android/sdk"
```

- Build debug APK: `./gradlew :app:assembleDebug`
- Install on a running emulator/device: `./gradlew :app:installDebug`
- Unit tests (pure JVM, no device): `./gradlew :app:testDebugUnitTest`
  - Single test class: `./gradlew :app:testDebugUnitTest --tests "com.wordly.app.domain.LearningEngineTest"`
  - Single test method: `./gradlew :app:testDebugUnitTest --tests "com.wordly.app.domain.LearningEngineTest.nextBenchmark advances to the last orderIndex of the batch"`
- Instrumented tests (need a running emulator/device): `./gradlew :app:connectedAndroidTest`
  - **Never run this against the owner's real phone**: AGP uninstalls the app after the run,
    wiping the word database. On a real device, install and run via `am instrument` instead:
    `./gradlew :app:installDebug :app:assembleDebugAndroidTest`, then
    `adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`,
    `adb shell am instrument -w com.wordly.app.test/androidx.test.runner.AndroidJUnitRunner`,
    `adb uninstall com.wordly.app.test` (only the test APK).
  - Room schemas are exported to `app/schemas/` (fed to `MigrationTestHelper`); every DB
    version bump needs a `MIGRATION_x_y` in `WordlyDatabase.kt` plus a case in `MigrationTest`.
- Lint: `./gradlew :app:lintDebug` (report at `app/build/reports/lint-results-debug.html`)

There is no configured AVD by default; `adb devices` / `emulator -list-avds` must show one
before `installDebug` or `connectedAndroidTest` will work.

## Architecture

### The benchmark model (the core domain concept)

Every `Word` has a strictly increasing `orderIndex`, assigned as `max(orderIndex) + 1` on
insert — this is "order of addition" and stays stable when other words are deleted (see
`data/entity/Word.kt`). A single-row `Progress` table stores `benchmarkOrderIndex`: words
with `orderIndex <= benchmark` are **learned**, words above it are **new**. Nothing else in
the schema encodes progress — nothing is deleted or archived, the benchmark just slides.

All selection/ordering logic for this model lives in **`domain/LearningEngine.kt`** as a
pure, Android-free Kotlin object operating on plain `List<Word>` — this is deliberate so the
core learning behavior is unit-testable without Robolectric/instrumentation
(`app/src/test/java/com/wordly/app/domain/LearningEngineTest.kt`):

- `learnedSlice(all, benchmark, scope)` — the review slice, narrowed by a `ReviewScope`
  (`domain/model/ReviewScope.kt`): `All`, `Window(count, fromStart)` (last/first N), or
  `Range(from, to)` (1-indexed inclusive positions among learned words, clamped to bounds).
- `newBatch(all, benchmark, batchSize)` — the next batch to learn, oldest-first.
- `orderSession(slice, shuffle, seed)` — presentation order (seeded shuffle or addition order).
- `nextBenchmark(batch, current)` — the benchmark after a confirmed batch.
- `normalize`/`isDuplicate` — duplicate-word detection (trim + lowercase + collapse whitespace).
- `favoritesSlice(all, scope)` — favorites, narrowed by the same `ReviewScope` rules.
- `hardSlice`/`isHard` — words with more misses than hits (`Word.correctCount`/`wrongCount`).
- `partialCommit(batch, knownIds, current)` — per-word batch confirmation: since the benchmark is
  a single cut, the batch's own `orderIndex` values are permuted so known words come first;
  nothing outside the batch moves.
- `searchByTranslation` — local reverse lookup (Russian → saved word) for the add-word screen.

Alongside it, also pure and unit-tested: `domain/Srs.kt` (Leitner spaced repetition *on top
of* the benchmark — it only decides when a **learned** word is due again, via `Word.srsBox`/
`dueAt`; `spreadBacklog` schedules learned words that have no schedule yet over 30 days),
`domain/Quiz.kt` (typed-answer checking with typo tolerance, multiple-choice options) and
`domain/StudyStats.kt` (streak over the `study_days` table).

`WordRepository` (in `data/`) is the only thing that touches Room/Retrofit; it loads full
lists and benchmark values and hands them to `LearningEngine` rather than expressing
windowing as SQL — keep it that way so the selection logic stays testable in one place.

### Study modes, one screen

`SessionScreen`/`SessionViewModel` (`ui/session/`) drive every mode from one
`SessionConfig` (`domain/model/SessionModels.kt`). Each config also carries a `QuizType`
(how cards are answered: flip + "Знаю/Не знаю", typing, multiple choice, listening). Every
answer goes through `WordRepository.recordAnswer` (counters, `Srs` schedule for learned words,
today's `study_days` count); a session's mistakes can be re-run without touching the schedule
again. Session progress (card ids, index, answers) is kept in `SavedStateHandle`.

- **Review** (`SessionConfig.Review`): learned words, optionally shuffled, narrowed by a
  `ReviewScope`. On completing the last card, `Notifier.reviewFinished()` posts a
  notification — the benchmark is untouched.
- **NewWords** (`SessionConfig.NewWords`): the next batch after the benchmark. On finishing,
  the UI lists the batch with checkboxes; the ticked words become learned via
  `WordRepository.commitBatch` → `LearningEngine.partialCommit`, the rest stay first in line.
  "Пройти пачку заново" restarts the same batch with the benchmark untouched.
- Any finished round can be re-run in the opposite direction (`SessionViewModel.repeatReversed`,
  re-shuffled, schedule not touched twice); a card's pencil opens the normal edit screen and
  `refreshCards()` re-reads the words on return.
- **Favorites**, **Due** (`Srs.dueSlice`, "На сегодня") and **Hard** (`hardSlice`) — like
  Review, they never move the benchmark.

`SessionConfig` is serialized into primitive nav-route query params by `ui/nav/Routes.kt`
(`session?mode=...&dir=...&quiz=...&shuffle=...&scopeType=...&scopeCount=...&scopeFromStart=...`
`&scopeFrom=...&scopeTo=...&batch=...&seed=...` — `ReviewScope` collapses to
`scopeType ∈ {all, window, range}` plus whichever of the other fields that type uses) and
reconstructed from `SavedStateHandle` in `SessionViewModel`'s `decodeScope()` — there is no
shared/parcelable nav arg type. If you add a new session parameter, it needs updating in
three places: `Routes.session()`/`encodeScope()`, the `navArgument` list in
`WordlyNavHost`, and `SessionViewModel`'s `decodeScope()`/constructor properties.

### Dependency wiring

No Hilt/Koin. `WordlyApp` (Application subclass) builds one `AppContainer` on `onCreate()`
holding the Room database, Retrofit/OkHttp client, `WordRepository`, `Pronouncer`,
`Notifier`, `ProfilePreferences` and `AppSettings` (theme, default batch, daily goal,
auto-pronounce, reminder — SharedPreferences-backed `StateFlow`s) for the process lifetime.
The daily reminder is a WorkManager job (`notify/ReminderWorker.kt`), rescheduled from Settings. Composables reach it via the `CreationExtras.container`
extension in `ui/common/ViewModelSupport.kt`, used inside `viewModel { ... }` initializer
lambdas (e.g. `viewModel { HomeViewModel(container.repository) }`), not through a manual
`ViewModelProvider.Factory` class per screen.

### Pronunciation

`audio/Pronouncer` picks between two backends depending on what a `Word` has:
`audio/AudioPlayer` (plain `MediaPlayer`) plays an mp3 URL when `Word.audioUrl` is set;
otherwise `audio/Speaker` (platform `TextToSpeech`, en-GB with an en-US fallback) speaks
`Word.text`. Because the TTS fallback always "succeeds", a person can't tell a failed
lookup from a successful one just by whether tapping 🔊 makes a sound — the add-word screen
tracks `lookedUp`/`audioUrl` separately and says explicitly which one is playing (see
`ui/addword/AddWordScreen.kt`).

### Word lookup (transcription/audio/translation)

`WordRepository.lookup()` fetches IPA transcription + pronunciation audio from **two
sources in parallel**, preferring whichever answered:
- `data/remote/WiktionaryApi` — en.wiktionary.org's MediaWiki API (Wikimedia-backed, free,
  no key). Two calls per lookup: `sections()` to find the "Pronunciation" subsection nested
  under the top-level "English" heading (`WiktionaryParsing.findEnglishPronunciationSectionIndex`
  — a page title mixes every language using that spelling, e.g. "chat" is English *and*
  French, so grabbing the first "Pronunciation" on the page would be wrong), then
  `sectionHtml()` for just that section, from which `WiktionaryParsing` regex-extracts the
  first `/.../`-or-`[...]`-delimited IPA span and the first audio `<source>` (preferring an
  already-transcoded `.mp3` over the raw `.ogg`).
- `data/remote/DictionaryApi` — api.dictionaryapi.dev (free, no key). Simpler/faster (one
  call, clean JSON) but a much smaller project with a real history of extended outages;
  kept as a second parallel attempt that fills in gaps Wiktionary's parsing didn't catch for
  a given word, not the primary source.

Both attempts are independently wrapped in `runCatching` inside `WordRepository.lookup()`
so one failing (or both) never throws — it only returns `Result.failure` when *neither*
source found anything.

`WordRepository.lookupTranslations()` fetches candidate Russian translations from **two
sources in parallel**, merging both into one ranked list:
- `translate/MlKitTranslator` — Google ML Kit's on-device English→Russian model. Runs fully
  locally after a one-time language-pack download, so unlike a cloud translation API it has
  no API key, request quota, or billing to manage. Always at most one answer.
- en.wiktionary.org's "Translations" table, via the same `WiktionaryApi`/`WiktionaryParsing`
  used for pronunciation: `findEnglishTranslationsSectionIndex` locates the first
  "Translations" subsection nested under the top-level "English" heading (it sits under a
  specific part-of-speech sense, e.g. "Noun", not directly under "English" — same
  cross-language pitfall as the Pronunciation lookup), then `extractRussianTranslations`
  regex-extracts every `<span class="Cyrl" lang="ru">` headword in that section's HTML,
  stripping the stress-mark diacritic Wiktionary adds to Cyrillic text. Unlike ML Kit this can
  surface several real dictionary senses/synonyms for one word.

(An earlier version used MyMemory, `api.mymemory.translated.net`, as a free/no-key cloud
source instead of Wiktionary — dropped for consistently poor translation quality, since
MyMemory is translation-*memory*, aligning phrases from a corpus of real sentences rather
than looking up dictionary entries.) `domain/TranslationRanking` still trims stray
punctuation and dedupes the merged result.

**Serbian** (`WordRepository.lookupSerbianTranslations`) can't use the English pipeline, and
ML Kit has **no Serbian model at all** (`"sr"` silently returns nothing). Sources, in priority
order: ru.wiktionary's Serbian/Serbo-Croatian/Croatian/Bosnian "Значение" section (direct
Russian; `WiktionaryParsing.extractRussianMeanings`), then en.wiktionary's "Serbo-Croatian"
glosses (`extractEnglishGlosses`) run through FreeDict (ML Kit en→ru only as a fallback — it
mistranslates lone words), plus ML Kit Croatian→Russian; the machine-assisted candidates are
ordered by how many sources agree. Each page is tried in both scripts (`domain/SerbianScript`),
ML Kit calls are capped at a few seconds (first use downloads a model), and MyMemory is only a
last resort. Parsers are tested against saved real HTML in `app/src/test/resources/wiktionary/`.
For Serbian the word's `transcription` field holds the **stressed form** instead of IPA
(`Profile.transcriptionLabel` = "Ударение"): en.wiktionary's accented headword ("ȕjāk"),
reduced by `SerbianScript.simplifyAccent` to a single Russian-style stress mark ("újak").
Reverse lookup (Russian → Serbian) uses the Serbian rows of ru.wiktionary's "Перевод" table
plus ML Kit ru→hr, in whichever script most of the profile's words use. The word list's
"Перепроверить переводы" (`ui/recheck/`, Serbian only) re-runs the lookup over saved words and
offers fixes for review — it never overwrites a translation unless one is picked.

All three lookups (transcription, audio, translation) run from the add-word screen only —
failures must never block saving a word, and are surfaced via `lookupResultMessage()`
(`ui/addword/AddWordViewModel.kt`) naming exactly which of translation/transcription/audio
came back, since claiming "transcription loaded" when only audio did is exactly the kind of
inaccuracy that caused confusion before.

### Screens

Compose Navigation graph (`ui/nav/WordlyNavHost.kt`): `home` ↔ `words` (list, with a
"выучено до сюда" divider positioned by comparing each word's `orderIndex` to the
benchmark, each row tappable to edit) ↔ `add`/`edit/{wordId}` (same `AddWordScreen`/
`AddWordViewModel`, `wordId == null` vs. a real id switches add vs. edit-in-place; duplicate
check via `WordRepository.findDuplicate`/`updateWord` excludes the word being edited itself
so it doesn't collide with its own unchanged text) ↔ `session`. Each screen's ViewModel is
small and screen-specific (`ui/<screen>/<Screen>ViewModel.kt`); there is no shared app-wide
ViewModel.
