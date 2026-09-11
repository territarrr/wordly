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

- `learnedWindow(all, benchmark, window)` — the review slice (all learned, or the last N).
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

- **Review** (`SessionConfig.Review`): learned words, optionally shuffled, optionally
  windowed to "last N". On completing the last card, `Notifier.reviewFinished()` posts a
  notification — the benchmark is untouched.
- **NewWords** (`SessionConfig.NewWords`): the next batch after the benchmark. On finishing,
  the UI asks for confirmation: "Да" advances the benchmark to the batch's max
  `orderIndex` via `LearningEngine.nextBenchmark`; "Ещё нет" restarts the same batch with
  the benchmark untouched.

`SessionConfig` is serialized into primitive nav-route query params by `ui/nav/Routes.kt`
(`session?mode=...&dir=...&shuffle=...&window=...&batch=...&seed=...`) and reconstructed
from `SavedStateHandle` in `SessionViewModel` — there is no shared/parcelable nav arg type.
If you add a new session parameter, it needs updating in three places: `Routes.session()`,
the `navArgument` list in `WordlyNavHost`, and the `SavedStateHandle` reads in
`SessionViewModel`'s constructor properties.

### Dependency wiring

No Hilt/Koin. `WordlyApp` (Application subclass) builds one `AppContainer` on `onCreate()`
holding the Room database, Retrofit/OkHttp client, `WordRepository`, `Pronouncer`, and
`Notifier` for the process lifetime. Composables reach it via the `CreationExtras.container`
extension in `ui/common/ViewModelSupport.kt`, used inside `viewModel { ... }` initializer
lambdas (e.g. `viewModel { HomeViewModel(container.repository) }`), not through a manual
`ViewModelProvider.Factory` class per screen.

### Pronunciation

`audio/Pronouncer` picks between two backends depending on what a `Word` has:
`audio/AudioPlayer` (plain `MediaPlayer`) plays the mp3 URL from the dictionary API when
`Word.audioUrl` is set; otherwise `audio/Speaker` (platform `TextToSpeech`, en-GB with a
en-US fallback) speaks `Word.text`. `data/remote/DictionaryApi` hits
`api.dictionaryapi.dev` (free, no key) only from the add-word screen to pre-fill IPA
transcription + audio URL — lookup failures are swallowed into `Result.failure` and the
form falls back to manual entry; they must never block saving a word.

### Screens

Compose Navigation graph (`ui/nav/WordlyNavHost.kt`): `home` ↔ `words` (list, with a
"выучено до сюда" divider positioned by comparing each word's `orderIndex` to the
benchmark) ↔ `add` (duplicate check via `WordRepository.findDuplicate` before any lookup
or save) ↔ `session`. Each screen's ViewModel is small and screen-specific
(`ui/<screen>/<Screen>ViewModel.kt`); there is no shared app-wide ViewModel.
