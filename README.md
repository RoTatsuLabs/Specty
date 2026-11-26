<div align="center">

<img src="docs/icon.svg" alt="Specty" width="128" height="128">

# Specty

**See what is actually inside your audio files.**

An open-source audio analyzer for Android. It draws the spectrogram, measures loudness and dynamics, and tells you whether a "lossless" file was ever lossless.

[![Release](https://img.shields.io/github/v/release/RoTatsuLabs/Specty?label=release&color=3D6BFF)](https://github.com/RoTatsuLabs/Specty/releases/latest)
[![Code Quality](https://github.com/RoTatsuLabs/Specty/actions/workflows/quality.yml/badge.svg)](https://github.com/RoTatsuLabs/Specty/actions/workflows/quality.yml)
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-FF4F87.svg)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

[Download](https://github.com/RoTatsuLabs/Specty/releases/latest) · [Report a problem](https://github.com/RoTatsuLabs/Specty/issues)

</div>

<br>

## What it does

- Open a file and the verdict comes first: genuine, lossy source, upsampled, a sharp cutoff or a gradual roll-off, along with the cutoff frequency and how strong the evidence is. Roll-off and sharp cutoff describe the spectrum and do not claim a source.
- The spectrogram opens full screen with zoom, pan and a readout of frequency, time and level. You can export it as a PNG.
- Measurements cover peak, true peak, loudness (LUFS), dynamic range, clipping and effective bit depth, per channel.
- The library scans your music folders in the background. Filter by verdict, compare two tracks or look for duplicates.

## Hi-Res

Specty treats a lossless file as Hi-Res when its sample rate is above 48 kHz, the rule Apple uses. The JAS logo asks for at least 24-bit at 96 kHz, which is stricter. A sample rate alone is not resolution, so files above 48 kHz get one of three states:

- **Verified**: real content above 24 kHz, or a noise floor below 16-bit.
- **Unconfirmed**: nothing contradicts it and nothing proves it. A 16-bit floor beside real content ends up here, because 16-bit dither and an analog floor of the same level read alike.
- **Not Hi-Res**: upsampled from a CD-rate source, 16-bit data (stored as 16-bit, or padded to 24), or a 16-bit floor with nothing above 24 kHz.

What the checks cannot do:

- A spectrogram does not show bit depth, and padding disappears after a gain change or dither.
- Finding no sign of a transcode is not proof of where a file came from.
- Sample-level upsample evidence only sees integer ratios from 44.1 and 48 kHz. High-quality resampling and ratios such as 44.1 to 96 kHz are recognised only by an edge in the spectrum at the source Nyquist, and a 16-bit dither floor can hide a small one.
- A peak-hold spectrum is built from at most 1,200 frames of a track, so content that lasts a few frames can be missed in long files.
- The thresholds were tuned on a synthetic corpus (`tools/hires-corpus`), not on a collection of real recordings.

## Install

Download an APK from the [latest release](https://github.com/RoTatsuLabs/Specty/releases/latest) and open it on a device running Android 8.0 or newer.

| File | For |
| --- | --- |
| `arm64-v8a` | Most phones and tablets |
| `armeabi-v7a` | Older 32-bit phones |
| `x86_64` | Emulators and x86 devices |
| `universal` | Any device, larger download |

## Build

You need JDK 17 and the Android SDK. The exact versions are in the build files.

```sh
gradle testDebugUnitTest      # unit tests
gradle :app:assembleDebug     # debug APK
```

The analysis code lives in `:core:audio`, plain Kotlin with no Android dependencies, so its tests run on the JVM. The app is Jetpack Compose with Material 3 Expressive. Issues and pull requests are welcome.

## License

Specty is released under the [GNU General Public License v3.0](LICENSE). The About page lists every bundled library with its licence.

## Acknowledgements

- [FFmpegKitNext](https://github.com/arthenica/ffmpeg-kit-next) (LGPL v3.0) decodes audio through FFmpeg.
- [Mornye](https://github.com/mornyeapp/Mornye) (LGPL v3.0) is the source of the Hi-Res evidence checks that Specty ports to Kotlin.
