<p align="center">
  <img src="docs/icon.svg" alt="Specty app icon" width="112" />
</p>

<h1 align="center">Specty</h1>

<p align="center">
  An open-source audio quality analyzer for Android. See the spectrogram, loudness and dynamics, and codec facts, and check whether a "lossless" file started life as a lossy one.
</p>

<p align="center">
  <a href="https://github.com/RoTatsuLabs/Specty/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/RoTatsuLabs/Specty?style=for-the-badge&logo=github&label=latest"></a>
  <a href="https://github.com/RoTatsuLabs/Specty/releases"><img alt="Total downloads" src="https://img.shields.io/github/downloads/RoTatsuLabs/Specty/total?style=for-the-badge&logo=github&label=downloads"></a>
  <a href="https://github.com/RoTatsuLabs/Specty/actions/workflows/quality.yml"><img alt="Code quality" src="https://img.shields.io/github/actions/workflow/status/RoTatsuLabs/Specty/quality.yml?style=for-the-badge&logo=githubactions&logoColor=white&label=quality"></a>
  <a href="LICENSE"><img alt="GPLv3 license" src="https://img.shields.io/github/license/RoTatsuLabs/Specty?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="https://developer.android.com/"><img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white"></a>
  <a href="https://kotlinlang.org/"><img alt="Kotlin" src="https://img.shields.io/github/languages/top/RoTatsuLabs/Specty?style=for-the-badge&logo=kotlin&logoColor=white"></a>
  <a href="https://developer.android.com/compose"><img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white"></a>
  <a href="https://m3.material.io/"><img alt="Material 3" src="https://img.shields.io/badge/Material%203-Expressive-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white"></a>
</p>

<p align="center">
  <a href="https://github.com/RoTatsuLabs/Specty/releases/latest"><strong>Download APK</strong></a>
  |
  <a href="https://github.com/RoTatsuLabs/Specty/issues"><strong>Issues</strong></a>
</p>

---

## About

A file labelled FLAC or ALAC may have been transcoded from an MP3 or upsampled from a lower sample rate. Specty decodes the file on your device, measures it, and reports what it found, including how sure it is.

Everything runs locally. There is no account, the app declares no internet permission, and nothing is uploaded.

If you like Specty, a star on the repo helps more people find it.

## Highlights

- Authenticity check that finds a lossy or upsampled origin and gives a verdict with a confidence score.
- Spectrogram with per-channel views, adjustable settings and a full-screen viewer with zoom and pan.
- Peak, true peak, RMS, integrated loudness (LUFS), dynamic range, clipping and effective bit depth.
- Music library built from folders you pick, with a background scan and saved results.
- Compare two files side by side, with a difference image and overlaid spectra.
- No storage permission and no internet permission.
- Free and open-source under the GPLv3 license.

<details>
<summary><strong>Features</strong></summary>

- File and codec info: codec, container, sample rate, bit depth, bitrate, channels, duration and size.
- Levels and dynamics: peak, true peak, RMS, integrated loudness (ITU-R BS.1770-4), dynamic range, clipping detection and effective bit depth, per channel.
- Dynamics over time: loudness and peak graph with clipping markers, a DR meter value, stereo correlation and balance, and DC offset.
- Spectrogram with a combined view and one tab per channel. Choose the window size, window function, palette, frequency scale and level range.
- Full-screen spectrogram viewer with zoom and pan on each axis and a readout of frequency, time and level.
- Playback with a moving cursor on the spectrogram.
- Authenticity check with the cutoff frequency, where in the track it appears and which encoder low-pass it resembles. It also flags 16-bit audio padded into a 24-bit container.
- Compare two files with stacked spectrograms, a difference image, overlaid average spectra and a metrics table.
- Library with search, sorting, grouping, filtering by verdict, list and grid views, excluded folders and a background scan that skips tracks you already checked.
- Saved results stored on the device, so a checked file opens instantly.
- Dolby Atmos in E-AC-3 files is recognised. Dolby AC-4 is decoded with the device's own codec when it has one.
- Export the spectrogram as a PNG with axes, legend and title.
- Material 3 Expressive interface in English, Indonesian and Simplified Chinese.

</details>

<details>
<summary><strong>How the verdict works</strong></summary>

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

The verdict uses its own fixed transform, so changing the spectrogram settings never changes it. Results are cached with an algorithm version, so a new release re-analyses files instead of showing old verdicts.

| Verdict | Meaning |
| --- | --- |
| Genuine | Content reaches the top of the spectrum with no cutoff or roll-off |
| Lossy source | A hard cutoff typical of an MP3 or AAC low-pass |
| Possible lossy source | A sharp cutoff near 20 kHz in a 44.1 or 48 kHz file. A high-bitrate lossy source or some masters can do this. |
| Upsampled | Content stops near 22 kHz in a file with a higher sample rate |
| Band limited | The spectrum fades out early, for example from a mastering filter |
| Lossy format | The codec is lossy by definition, so the spectrum is not judged |
| Dolby Atmos | Object audio in an E-AC-3 stream, not judged for lossy origin |
| Inconclusive | Too little evidence for a verdict |

Specty reports measured evidence only. The Nyquist frequency is never reported as a cutoff, and the thresholds were tuned on synthetic spectra. A sharp cutoff near 16 kHz is strong evidence of a lossy origin. One near 20 kHz is only suggestive.

</details>

## Star History

<a href="https://www.star-history.com/?repos=rotatsulabs%2Fspecty&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=rotatsulabs/specty&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=rotatsulabs/specty&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=rotatsulabs/specty&type=date&legend=top-left" />
 </picture>
</a>

## Install

1. Open the [latest GitHub release](https://github.com/RoTatsuLabs/Specty/releases/latest).
2. Download the APK for your device. `arm64-v8a` suits most phones, `x86_64` suits x86 devices and emulators, and `universal` works on both but is larger.
3. Install it on a device running Android 8 or newer.
4. Open the library menu, choose Library folders, and pick the folders that hold your music.

Android may ask you to allow installs from your browser or file manager. That is normal for APKs downloaded outside the Play Store.

## FAQ

### Does Specty upload my music?
> No. Specty declares no internet permission, and analysis runs on your device.

### Does Specty need storage access?
> No. It reads only inside the folders you pick with the system folder picker, plus any single file you open.

### Is the verdict always right?
> No. It is a judgement from measured evidence, with a confidence score. Some masters and high-bitrate lossy sources can look alike, and Specty says Inconclusive when the evidence is thin.

### How do updates work?
> Specty has no in-app updater. Install the newest APK from the Releases page over the old one.

### Can I contribute?
> Yes. Pull requests for fixes and new features are welcome.

## Project structure

```plaintext
app/src/main/java/com/rotatsulabs/specty/
├── about/        # Open source licence data for the About page
├── audio/        # DSP and analysis (pure Kotlin), plus FFmpeg and MediaCodec glue
├── batch/        # Background library scan and its foreground service
├── data/         # Folder library, cover art loader, saved-result cache
├── dev/          # Opt-in developer log
└── ui/
    ├── components/   # Shared UI pieces and formatters
    ├── design/       # Shapes, motion and reusable components
    ├── screens/      # Library, analysis, compare, settings, about, onboarding
    ├── spectrogram/  # Renderer, palettes, card, full-screen viewer and PNG exporter
    └── theme/        # Theme and appearance options
```

## Contribute

You can send a Pull Request for your fixes or new features here.

1. Fork the repository.
2. Create a focused branch for your change.
3. Build and test locally with JDK 17, Android SDK 36 and Gradle 8.14 or newer:
   ```sh
   gradle :app:testDebugUnitTest
   gradle :app:assembleDebug
   ```
4. Open a Pull Request with a short explanation of what changed.

New analysis logic needs a unit test with a synthetic signal. Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/). Coding agents should read [AGENTS.md](AGENTS.md).

## Credits

Specty decodes audio with FFmpeg through [ffmpeg-kit](https://github.com/ffmpegkit-maintained/ffmpeg), a maintained continuation of the retired [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit). Thanks to the FFmpeg and ffmpeg-kit developers.

## License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

Specty is free software released under the [GNU General Public License v3.0](LICENSE). The About page in the app lists every bundled library with its licence and the full texts. FFmpeg reaches Specty through the ffmpeg-kit fork, which is LGPL v3.0.
