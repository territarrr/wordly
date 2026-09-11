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

`WordRepository` (in `data/`) is the only thing that touches Room/Retrofit; it loads full
lists and benchmark values and hands them to `LearningEngine` rather than expressing
windowing as SQL — keep it that way so the selection logic stays testable in one place.

### Two study modes, one screen

`SessionScreen`/`SessionViewModel` (`ui/session/`) drive both modes from one
`SessionConfig` (`domain/model/SessionModels.kt`):

- **Review** (`SessionConfig.Review`): learned words, optionally shuffled, narrowed by a
  `ReviewScope`. On completing the last card, `Notifier.reviewFinished()` posts a
  notification — the benchmark is untouched.
- **NewWords** (`SessionConfig.NewWords`): the next batch after the benchmark. On finishing,
  the UI asks for confirmation: "Да" advances the benchmark to the batch's max
  `orderIndex` via `LearningEngine.nextBenchmark`; "Ещё нет" restarts the same batch with
  the benchmark untouched.

`SessionConfig` is serialized into primitive nav-route query params by `ui/nav/Routes.kt`
(`session?mode=...&dir=...&shuffle=...&scopeType=...&scopeCount=...&scopeFromStart=...`
`&scopeFrom=...&scopeTo=...&batch=...&seed=...` — `ReviewScope` collapses to
`scopeType ∈ {all, window, range}` plus whichever of the other fields that type uses) and
reconstructed from `SavedStateHandle` in `SessionViewModel`'s `decodeScope()` — there is no
shared/parcelable nav arg type. If you add a new session parameter, it needs updating in
three places: `Routes.session()`/`encodeScope()`, the `navArgument` list in
`WordlyNavHost`, and `SessionViewModel`'s `decodeScope()`/constructor properties.

### Dependency wiring

No Hilt/Koin. `WordlyApp` (Application subclass) builds one `AppContainer` on `onCreate()`
holding the Room database, Retrofit/OkHttp client, `WordRepository`, `Pronouncer`, and
`Notifier` for the process lifetime. Composables reach it via the `CreationExtras.container`
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
source found anything. `data/remote/TranslationApi` (MyMemory, free, no key) separately
fetches candidate Russian translations; because MyMemory is translation-*memory* (aligned
sentence pairs), not a dictionary, `domain/TranslationRanking` filters out multi-word/
preposition-led results that are really fragments of an example sentence rather than a
word-for-word translation. All three lookups run from the add-word screen only — failures
must never block saving a word, and are surfaced via `lookupResultMessage()`
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
