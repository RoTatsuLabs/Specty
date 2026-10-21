<div align="center">

<img src="docs/icon.svg" alt="Specty app icon" width="128" height="128">

# Specty

See what your audio files really contain.

An open-source audio quality analyzer for Android. It shows the spectrogram, loudness and dynamics, and codec facts, and it checks whether a "lossless" file started life as a lossy one.

[![Code Quality](https://github.com/RoTatsuLabs/Specty/actions/workflows/quality.yml/badge.svg)](https://github.com/RoTatsuLabs/Specty/actions/workflows/quality.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%2013%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

</div>

## Overview

A file labelled FLAC or ALAC is not necessarily lossless in any useful sense. It may have been transcoded from an MP3 or upsampled from a lower sample rate. Specty decodes the file on your device, measures it, and reports what it found, including how sure it is.

Everything runs locally. There is no account, Specty declares no internet permission, and nothing is uploaded.

## Features

| Area | What you get |
| --- | --- |
| File and codec info | Codec, container, sample rate, bit depth, decoded PCM format, bitrate, channels, duration and size. Song titles come from the file's title tag. |
| Levels and dynamics | Peak, true peak (oversampled), RMS, integrated loudness (LUFS, ITU-R BS.1770-4), dynamic range, clipping detection and effective bit depth, with per-channel values |
| Spectrogram | STFT heatmap with a combined view and one tab per channel. Window size of 1024, 2048 or 4096, a choice of Hann, Hamming or Blackman-Harris, and six palettes (Fire, Inferno, Magma, Viridis, Rainbow, Ice). Frequency scale and level range are adjustable. |
| Full screen spectrogram | Landscape viewer with two-finger zoom and pan on each axis and a touch readout of frequency, time and level |
| Dynamics over time | Loudness and peak graph with clipping markers, a DR meter value (3 s blocks, loudest 20%), stereo correlation and balance, and DC offset |
| Authenticity check | Looks for a lossy or upsampled origin in the spectrum and gives a verdict with a confidence score, the cutoff frequency, where in the track it appears and which encoder low-pass it resembles. It also flags 16-bit audio padded into a 24-bit container. |
| Compare two files | Stacked spectrograms, a B minus A difference image, overlaid average spectra and a side-by-side metrics table |
| Library | Built from the folders you pick. Search, sort, filter by verdict, switch between list and grid, and scan the whole library in the background with a progress notification. |
| Saved results | Finished analyses are stored on the device, so a file you already checked opens instantly. Library rows show a verdict badge. |
| Dolby formats | Dolby Atmos in E-AC-3 MP4 files is recognised and labelled. Dolby AC-4 is decoded with the device's own codec when it has one. |
| Export | Save the spectrogram as a PNG with axes, legend and title to `Pictures/Specty` |
| Look and feel | Material 3 with large rounded shapes and spring-based motion. Wallpaper colours, six tonal palettes, and light, dark and pure-black themes. |

## How the analysis works

```
file ──▶ FFprobe ──▶ decode to f32 PCM at the native rate
                     (FFmpeg, or the device codec for AC-4)
                          │
          ┌───────────────┼────────────────┐
          ▼               ▼                ▼
   level measurement  spectrogram     authenticity spectrum
   peak, RMS, LUFS,   (your chosen    (fixed 4096-point
   true peak,         window size)     Hann transform)
   bit depth                               │
                                           ▼
                                   verdict and confidence
```

1. `MediaProbe` reads stream facts with FFprobe.
2. `AudioDecoder` decodes to raw 32-bit float PCM in the app cache and keeps the sample rate and channel layout the decoder produced. `PlatformDecoder` does the same through `MediaCodec` for formats FFmpeg cannot decode.
3. `LevelAnalyzer` makes one streaming pass for peaks, RMS, K-weighted loudness, clipping runs and effective bit depth. True peak runs on one thread per channel.
4. `SpectrogramAnalyzer` builds the spectrogram you see and, at the same time, the spectra the verdict is judged on.
5. `AuthenticityAnalyzer` reads those spectra for a steep drop that never recovers, checks it against sections of the track and assigns a verdict.

The verdict uses its own fixed 4096-point transform, so changing the spectrogram window size or function in Settings never changes it. Results are cached with an algorithm version, so a new version of Specty re-analyses files instead of showing old verdicts.

The DSP code in `app/src/main/java/com/rotatsulabs/specty/audio/` has no Android dependencies, so its tests run as plain JVM unit tests.

### Verdicts

| Verdict | Meaning |
| --- | --- |
| Genuine | Content reaches the top of the spectrum in enough sections of the track, with no cutoff or roll-off |
| Lossy source | A hard cutoff typical of an MP3 or AAC low-pass, most likely from a transcode |
| Possible lossy source | A sharp cutoff near 20 kHz in a 44.1 or 48 kHz file. A high-bitrate lossy source can do this, and so can some masters. |
| Upsampled | Content stops near 22 kHz inside a file with a higher sample rate |
| Band limited | The spectrum fades out or ends early, which can be a mastering filter or an older source |
| Lossy format | The codec is lossy by definition (MP3, AAC, Opus and similar), so the spectrum is not judged |
| Dolby Atmos | Object audio carried in an E-AC-3 stream, which is not judged for lossy origin |
| Inconclusive | Too little audible content, a faint top end, or sections that disagree. Specty gives no verdict in those cases. |

### Reading the numbers

- Dynamic Range is peak minus RMS in dB, a crest-factor style figure. The DR meter value is shown separately.
- True Peak uses a windowed-sinc interpolator and can read about 0.1 dB high on adversarial signals.
- Spectral Cutoff is the edge of a detected cliff. It stays empty when none is found, and the Nyquist frequency is never reported as a cutoff.
- Confidence comes from measured evidence only. Sections that cannot be read add nothing.
- Effective bit depth is estimated from the decoded samples. Resampled or float-processed audio fills every bit, so it reads as full depth.
- The thresholds were tuned on synthetic spectra. A sharp cutoff near 16 kHz is strong evidence of a lossy origin, and one near 20 kHz is only suggestive.

## Install

This repository does not publish APKs. Build Specty from source as described below, then install the APK on a device running Android 8 or newer. You may need to allow installs from your file manager.

## Using the library

Specty has no storage permission. Open the library menu, choose Library folders, and pick the folders that hold your music with the system folder picker. Subfolders are included. Specty reads only inside those folders, so ringtones and voice notes stay out. You can add or remove folders at any time, or pick a single file with the button on the library screen. Opening an audio file from another app works too.

## Build from source

You need JDK 17, Android SDK 36 and Gradle 8.14 or newer. Android Studio also works, and it generates the wrapper.

```sh
gradle :app:testDebugUnitTest   # unit tests
gradle :app:assembleDebug       # debug APK in app/build/outputs/apk/debug/
```

| | |
| --- | --- |
| Package ID | `com.rotatsulabs.specty` |
| Min SDK | 26 (Android 8) |
| Target and compile SDK | 36 |
| Language | Kotlin, Jetpack Compose |
| Decoding | FFmpeg through [`dev.ffmpegkit-maintained:ffmpeg-kit-min`](https://github.com/ffmpegkit-maintained/ffmpeg), a maintained continuation of the retired `arthenica/ffmpeg-kit`. Formats it cannot decode, such as AC-4, go through the device's `MediaCodec`. |
| ABIs | `arm64-v8a`, `x86_64` |

GitHub Actions runs one workflow, Code Quality. It runs lint, the unit tests and a debug build on every push to `main` and on pull requests.

## Project layout

```
app/src/main/java/com/rotatsulabs/specty/
├── about/        Open source licence data for the About page
├── audio/        DSP and analysis (pure Kotlin), plus FFmpeg and MediaCodec glue
├── batch/        Background library scan and its foreground service
├── data/         Folder library, cover art loader, saved-result cache
└── ui/
    ├── components/   Cover image, stat grid, progress dock
    ├── design/       Shapes, type, motion and reusable components
    ├── screens/      Library, results, compare, settings, about, onboarding
    ├── spectrogram/  Renderer, palettes, card, full screen viewer and PNG exporter
    └── theme/        Theme, appearance options, motion specs
```

## Privacy

Specty reads audio only to analyse it. The folder picker gives it access to the folders you choose and nothing else. It asks for the notification permission only to show progress during a library scan. Analyses and cover thumbnails stay on the device, and you can clear saved results in Settings.

## Roadmap

- Streaming decode through a pipe instead of a temporary file
- Export a report as an image or JSON

## Contributing

Issues and pull requests are welcome. Run `gradle :app:testDebugUnitTest` before sending code changes. New analysis logic should come with a unit test that uses a synthetic signal, as the existing tests do. Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).

## License

Specty is free software released under the [GNU General Public License v3.0](LICENSE).

The About page in the app lists every bundled library with its licence and includes the full texts. FFmpeg reaches Specty through the maintained ffmpeg-kit fork, which is licensed under the LGPL v3.0. The Jetpack, Kotlin and kotlinx.coroutines libraries are licensed under Apache 2.0.

## Design system

Everything visual lives in `ui/design`. It has a shape system with morphing corners (`Shapes.kt`), the type scale with emphasized weights (`Typography.kt`), fade-through, shared-axis and container-transform motion (`Transitions.kt`), OKLCH tonal palettes for when dynamic colour is off (`tonal/Tonal.kt`), and the reusable components in `components/`, such as `SegmentedControl`, `MorphSurface`, `SpectyCard`, `ExpressiveButton`, `ListRow`, `SettingGroup`, `WavyProgressBar` and `ShapeLoader`.
