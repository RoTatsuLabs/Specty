<div align="center">

# Specty

**See what your audio files really contain.**

An open-source audio quality analyzer for Android: spectrogram, loudness and dynamics, codec facts, and a check for lossy or upsampled sources hiding inside "lossless" files.

[![Build](https://github.com/RoTatsuLabs/Specty/actions/workflows/build.yml/badge.svg)](https://github.com/RoTatsuLabs/Specty/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%2013%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

</div>

---

## Overview

A file labelled FLAC or ALAC is not necessarily lossless in any useful sense. It may have been transcoded from an MP3, or upsampled from a lower sample rate. Specty decodes a file on your device, measures it, and tells you what it found.

Everything runs locally. No account, no network access, nothing is uploaded.

## Features

| Area | What you get |
| --- | --- |
| **File and codec info** | Codec, container, sample rate, bit depth, decoded sample format, bitrate, channels, duration, size |
| **Levels and dynamics** | Peak, true peak (oversampled), RMS, integrated loudness (LUFS, ITU-R BS.1770-4), dynamic range, clipping detection, all per channel |
| **Spectrogram** | STFT heatmap at 16:9 with a combined view and one tab per channel, selectable window size (1024, 2048, 4096) and seven colour palettes |
| **Dynamics over time** | Loudness and peak graph with clipping markers, a DR meter value (3 s blocks, loudest 20%), stereo correlation and balance, DC offset |
| **Fullscreen spectrogram** | Landscape viewer with two-finger zoom and pan on each axis and a touch readout of frequency, time and level |
| **Compare two files** | Stacked spectrograms, a B minus A difference image, overlaid average spectra and a side-by-side metrics table |
| **Batch scan** | Analyses the whole library in the background with a progress notification; results land in the saved-results store |
| **Authenticity check** | Detects a brick-wall cutoff in the long-term spectrum and classifies the file as genuine, transcoded from a lossy source, or upsampled. Reports a confidence score, where in the track the cutoff appears and which encoder low-pass it resembles. Also flags 16-bit audio padded into a 24-bit container |
| **Library and files** | Scan the device library or pick any file with the system picker. "Open with" from other apps works too |
| **Saved results** | Finished analyses are stored on the device, so a file you already checked opens instantly. Library rows show a verdict badge |
| **Export** | Save the spectrogram as a PNG, with axes, legend and title, to `Pictures/Specty` |
| **Design** | Material 3 with large rounded shapes and spring-based motion, wallpaper colours (dynamic colour), light, dark and pure-black themes |

## How the analysis works

```
file ──▶ FFprobe ──▶ FFmpeg decode (f32 PCM, native rate, no resampling)
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
     level measurement          spectrogram (STFT)
  peak · RMS · LUFS · clipping   up to 1600 frames per channel
  true peak (one thread/channel)         │
              └───────────┬───────────┘
                          ▼
                authenticity analysis
              (spectral cliff detection)
```

1. `MediaProbe` reads stream facts with FFprobe.
2. `AudioDecoder` decodes to raw 32-bit float PCM in the app cache. Sample rate and channel layout are kept as they are.
3. `LevelAnalyzer` makes one streaming pass for peaks, RMS, K-weighted loudness, clipping runs and effective bit depth. True peak runs on one thread per channel.
4. `SpectrogramAnalyzer` takes evenly spaced FFT frames per channel and builds a long-term average spectrum. It runs alongside the level pass.
5. `AuthenticityAnalyzer` looks for a steep drop in that spectrum that never recovers, then classifies it.

The DSP code in `app/src/main/java/com/rotatsulabs/specty/audio/` has no Android dependencies, so its tests run as plain JVM unit tests.

### Reading the numbers

- **Dynamic Range** is peak minus RMS in dB, a crest-factor style figure. It is not the DR meter (TT) algorithm.
- **True Peak** uses a windowed-sinc interpolator and can read about 0.1 dB high on adversarial signals.
- **Spectral Cutoff** is where a detected cliff sits. It reads the Nyquist frequency when the file is full-band.
- **The verdict is a heuristic.** A sharp cutoff near 16 kHz is strong evidence of a lossy origin. A cutoff near 20 kHz is only suggestive, because some masters are filtered that way.

## Install

Debug builds are produced by CI on every push.

1. Open the latest successful run under the repository's **Actions** tab.
2. Download the `specty-debug-apk` artifact and unzip it.
3. Install the APK on a device running Android 13 or newer. You may need to allow installs from your browser or file manager.

## Build from source

Requirements: JDK 17, Android SDK 36, Gradle 8.14 or newer (or open the folder in Android Studio, which generates the wrapper).

```sh
gradle :app:testDebugUnitTest   # DSP unit tests
gradle :app:assembleDebug       # debug APK in app/build/outputs/apk/debug/
```

| | |
| --- | --- |
| Package ID | `com.rotatsulabs.specty` |
| Min SDK | 33 (Android 13) |
| Target / compile SDK | 36 |
| Language | Kotlin, Jetpack Compose |
| Decoding | FFmpeg through [`dev.ffmpegkit-maintained:ffmpeg-kit-min`](https://central.sonatype.com/search?q=ffmpeg-kit-maintained), a maintained continuation of the retired `arthenica/ffmpeg-kit` |
| ABIs | `arm64-v8a`, `x86_64` |

## Project layout

```
app/src/main/java/com/rotatsulabs/specty/
├── audio/        DSP and analysis (pure Kotlin) plus FFmpeg probe/decode glue
├── data/         MediaStore library scan, cover art loader, saved-result cache
└── ui/
    ├── screens/      Library, results and settings
    ├── components/   Cover image, stat grid, progress dock
    ├── spectrogram/  Renderer, palettes, card and PNG exporter
    └── theme/        Theme, appearance options, motion specs
```

## Privacy

Specty reads audio only to analyse it. The library scan needs the `READ_MEDIA_AUDIO` permission, and the system file picker needs none. Analyses and cover thumbnails stay on the device. You can clear saved results in Settings.

## Roadmap

- Streaming decode through a pipe instead of a temporary file
- Export a report as image or JSON
- Scan any folder, not only the MediaStore library
- Material 3 Expressive components once they are stable in the Compose version supported by the build

## Contributing

Issues and pull requests are welcome. For code changes, please run `gradle :app:testDebugUnitTest` first. New analysis logic should come with a unit test that uses a synthetic signal, as the existing tests do.

## License

Specty is free software released under the [GNU General Public License v3.0](LICENSE).

It uses FFmpeg through ffmpeg-kit. FFmpeg is licensed under the LGPL or GPL depending on how it is built. See the ffmpeg-kit-maintained project for the exact terms of the bundled build.

## Design system

Everything visual lives in `ui/design`: a shape system with morphing corners (`Shapes.kt`), the type scale with emphasized weights (`Typography.kt`), fade-through, shared-axis and container-transform motion (`Transitions.kt`), OKLCH tonal palettes used when dynamic colour is off (`tonal/Tonal.kt`), and the reusable components (`components/`: `SegmentedControl`, `MorphSurface`, `SpectyCard`, `ExpressiveButton`, `ListRow`, `SettingGroup`, `WavyProgressBar`, `ShapeLoader` and more).
