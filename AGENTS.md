# AGENTS.md

Persistent guidance for coding agents working on Specty. Keep it short and current. Update it only when a lasting rule or practice changes, not to log tasks or releases.

## Project

Specty is an Android audio quality analyzer: spectrogram, loudness and dynamics, codec facts, and an authenticity check for lossy or upsampled "lossless" files. Everything runs on the device.

- Package `com.rotatsulabs.specty`, GPL-3.0, Kotlin with Jetpack Compose and Material 3.
- minSdk 26, targetSdk and compileSdk 36, ABIs `arm64-v8a` and `x86_64`.
- Single module `:app`. Versions live in `gradle/libs.versions.toml`.
- Decoding uses FFmpeg through `dev.ffmpegkit-maintained:ffmpeg-kit-min`. Formats FFmpeg cannot decode (AC-4) go through the device's `MediaCodec`.
- No internet permission and no storage permission. The library is built from folders the user picks with the system folder picker.

## Build and test

There is no Gradle wrapper in the repo. Use JDK 17 and Gradle 8.14 or newer (CI uses 8.14.3).

```sh
gradle :app:testDebugUnitTest   # unit tests (plain JVM)
gradle :app:lintDebug           # lint
gradle :app:assembleDebug       # debug APK in app/build/outputs/apk/debug/
```

- CI (`.github/workflows/quality.yml`) runs lint, unit tests and a debug build on pushes to `main` and on pull requests. Run the tests before sending code changes.
- The debug build is minified and not debuggable on purpose, so it runs like release. Do not turn debuggable back on to "fix" something.
- Release and nightly APKs are not built here. They are built by workflows in the separate `RoTatsuLabs/Forge` repository, which read versions from `VERSION_NAME` and `VERSION_CODE`.

## Layout

```
app/src/main/java/com/rotatsulabs/specty/
├── about/   open source licence data (bundled texts in assets/licenses)
├── audio/   analysis: DSP, loudness, spectrogram, authenticity, plus FFprobe/FFmpeg/MediaCodec glue
├── batch/   background library scan: engine, cooldown and the foreground service
├── data/    picked folders, library scan, cover art, saved-result cache and verdict index
├── dev/     opt-in developer log
└── ui/
    ├── components/   shared UI pieces and formatters
    ├── design/       shapes, motion, back gesture, reusable components
    ├── screens/      library, analysis, compare, settings, about, onboarding
    ├── spectrogram/  renderer, palettes, card, full screen viewer, PNG exporter
    └── theme/        theme, appearance options, motion specs
```

`MainViewModel` owns app state. `SpectyApp` composes the screens and overlays.

## Rules

### Analysis code

- Keep DSP and analysis classes free of Android types so they run as JVM unit tests. Android, FFmpeg and `MediaCodec` access stays in the glue files (`MediaProbe`, `AudioDecoder`, `PlatformDecoder`, `StorageGuard`).
- The verdict is judged on a fixed 4096-point Hann spectrum (`AnalysisVersion.AUTH_FFT`). Spectrogram size or window settings must never change a verdict.
- Raise `AnalysisVersion.SCHEMA` when the saved result layout changes and `AnalysisVersion.ALGORITHM` when a rule or threshold can change a verdict or measurement. Both are part of every cache key, so old results are dropped, not reused.
- Engine sentences are data (`Note` with a `NoteKey` and values), not English text. The UI words them from string resources. `Notes.english` is for the report export and tests.
- New analysis logic needs a unit test with a synthetic signal, like the existing ones.
- Never report the Nyquist frequency as a cutoff. Confidence comes from measured evidence only.

### Library and batch scanning

- Saved results are keyed by uri, size, modified time, FFT size, window and version. Scans must skip tracks that already have a result and keep results when a scan is interrupted.
- Do not remove the thermal, battery, free-storage and memory protections in the batch scan. Tune cooldowns through `Cooldown`.
- Changes that claim to be faster must give identical output.

### UI

- Use the components in `ui/design` (`MorphSurface`, `ExpressiveButton`, `ExpressiveIconButton`, `ListRow`, `SettingGroup`, and so on) and the `Motion` specs instead of ad-hoc Material widgets and animation specs. Give pressable controls `pressScale` or an equivalent press response.
- Full-screen overlays use `OverlayScreen` with a `BackGesture` and `PredictiveBack`. The motion maths is in `BackMath` and has tests.
- Respect system animation settings. Effects that capture the screen (theme reveal) must fall back to a plain change.

### Strings and languages

- Every user-visible string lives in `res/values/strings.xml` and in both translations, `values-in` (Indonesian) and `values-zh-rCN` (Simplified Chinese). Add a key to all three in the same change. Only mark a string `translatable="false"` for names and codes.
- Do not hardcode text in composables or in the engine.

### Dependencies and licences

- Versions listed in `about/OpenSource.kt` must match `gradle/libs.versions.toml`. A unit test fails on drift, so update both when a dependency changes.
- Specty is GPL-3.0. Do not add a dependency whose licence is incompatible.
- Do not add the internet permission or any network code.

## Working style

- Match existing architecture, naming and formatting (`kotlin.code.style=official`). Avoid unrelated refactors, new dependencies and speculative changes.
- Verify with the commands above. Report anything you could not run.
- Commit messages follow Conventional Commits, are written in English and stay short and to the point.
- Do not add `Co-Authored-By` trailers, session URLs or other AI attribution to commits or pull request descriptions.
