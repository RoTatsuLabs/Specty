<div align="center">

<img src="docs/icon.svg" alt="Specty app icon" width="120" height="120">

# Specty

**Check what is really inside your audio files.**

Specty is an open-source audio analyzer for Android. It draws the spectrogram, measures loudness and dynamics, reads the codec details, and tells you whether a "lossless" file was ever lossless.

[![Release](https://img.shields.io/github/v/release/RoTatsuLabs/Specty?label=release)](https://github.com/RoTatsuLabs/Specty/releases/latest)
[![Code Quality](https://github.com/RoTatsuLabs/Specty/actions/workflows/quality.yml/badge.svg)](https://github.com/RoTatsuLabs/Specty/actions/workflows/quality.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

</div>

## Features

**Analysis**
- Codec, container, sample rate, bit depth, bitrate, channels and duration.
- Peak, true peak, RMS, integrated loudness (LUFS, ITU-R BS.1770-4), dynamic range, clipping and effective bit depth, per channel.
- Loudness and peaks over time, with stereo correlation, balance and DC offset.
- An authenticity check for a lossy or upsampled origin, with the cutoff frequency and where in the track it shows up.

**Spectrogram**
- All channels together or one at a time, with your choice of window size, window function, palette, frequency scale and level range.
- A full-screen viewer with zoom, pan and a readout of frequency, time and level.
- Playback with a cursor that follows the spectrogram.
- PNG export with axes, legend and title.

**Library**
- Pick the folders to include or exclude, then search, sort, group and filter by verdict.
- Browse by track, album or artist. Album and artist pages show how their tracks measured.
- Scan in the background, skip tracks you already checked, and reopen any result instantly.
- An insights page with totals and a duplicate finder that marks the best copy.
- Compare two tracks with a difference image, overlaid spectra and a metrics table.

Dolby Atmos in E-AC-3 files is recognised, and Dolby AC-4 is decoded with the device's own codec when it has one. Available in English, Indonesian and Simplified Chinese.

## Verdicts

| Verdict | What it means |
| --- | --- |
| Genuine | Content reaches the top of the spectrum with no cutoff or roll-off |
| Lossy source | A hard cutoff typical of an MP3 or AAC low-pass |
| Possible lossy source | A sharp cutoff near 20 kHz in a 44.1 or 48 kHz file. A high-bitrate lossy source or some masters can do this. |
| Upsampled | Content stops near 22 kHz in a file with a higher sample rate |
| Band limited | The spectrum fades out early, for example from a mastering filter |
| Lossy format | The codec is lossy by definition, so the spectrum is not judged |
| Dolby Atmos | Object audio in an E-AC-3 stream, not judged for lossy origin |
| Inconclusive | Too little evidence for a verdict |

A verdict is a judgement from measured evidence, not a guarantee. A sharp cutoff near 16 kHz is strong evidence of a lossy origin; one near 20 kHz is only a hint. Specty never reports the Nyquist frequency as a cutoff and answers Inconclusive when the evidence is thin.

## How it works

```
file -> FFprobe -> decode to 32-bit float PCM
                        |
        +---------------+----------------+
        v               v                v
  level measurement  spectrogram    authenticity spectrum
  peak, RMS, LUFS,   (your chosen   (fixed 4096-point
  true peak,         window size)    Hann transform)
  bit depth                               |
                                          v
                                  verdict and confidence
```

The verdict has its own fixed transform, so spectrogram settings never change it. Results are cached with an algorithm version, so a new release re-analyses files instead of showing stale verdicts. The code in `:core:audio` has no Android dependencies and is tested on the JVM.

## Install

Download an APK from the [latest release](https://github.com/RoTatsuLabs/Specty/releases/latest) and install it on a device running Android 8 or newer.

| File | Use it on |
| --- | --- |
| `Specty_<version>_arm64-v8a.apk` | Most phones and tablets |
| `Specty_<version>_armeabi-v7a.apk` | Older 32-bit ARM phones |
| `Specty_<version>_x86_64.apk` | x86 devices and emulators |
| `Specty_<version>_universal.apk` | Any of the above, at a larger size |

After the first launch, open the library menu, choose Library folders and pick where your music lives. To update, install a newer APK over the old one.

## Build from source

You need JDK 17, Android SDK 36 and Gradle 8.14 or newer.

```sh
gradle :core:audio:test testDebugUnitTest   # unit tests, every module
gradle :app:assembleDebug                   # debug APK in app/build/outputs/apk/debug/
```

The project is split into modules:

- `:core:audio` is plain Kotlin with no Android code: the FFT, loudness and dynamics meters, spectrogram, authenticity verdict and notes. Its tests run on the JVM.
- `:core:data` is the Android side of analysis and storage: decoding, the media probe, the library repository, the result cache and cover art.
- `:core:design` holds the Compose design system, the theme and the tonal colour maths.
- `:app` has the screens, the scan service and the report export.

The app is Kotlin with Jetpack Compose and targets Android 8.0 (API 26) and up on `arm64-v8a`, `armeabi-v7a` and `x86_64`. Audio is decoded with FFmpeg through [FFmpegKitNext](https://github.com/arthenica/ffmpeg-kit-next), the continuation of [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit), and with the device's `MediaCodec` for formats FFmpeg cannot decode. GitHub Actions runs lint, the unit tests and a debug build on every push to `main` and on every pull request.

## Contributing

Issues and pull requests are welcome. Run the unit tests first, and add a test with a synthetic signal for new analysis logic. Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).

## Star history

<a href="https://www.star-history.com/?repos=rotatsulabs%2Fspecty&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=rotatsulabs/specty&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=rotatsulabs/specty&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=rotatsulabs/specty&type=date&legend=top-left" />
 </picture>
</a>

## License

Specty is released under the [GNU General Public License v3.0](LICENSE). The About page lists every bundled library with its licence. FFmpeg reaches Specty through FFmpegKitNext (LGPL v3.0). The sample-and-hold, interpolation and 16-bit noise floor checks are Kotlin ports of the Hi-Res evidence code in [Mornye](https://github.com/mornyeapp/Mornye) (LGPL v3.0), used with credit.
