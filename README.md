# Specty

An expressive audio analyzer for Android, built with Material Design 3 Expressive.

Pick a file (or browse your library) and Specty shows what it really contains: codec and format facts, loudness and dynamics, per-channel stats, a spectrogram, and a check for lossy or upsampled sources hiding inside "lossless" files.

## Features

- **File and codec info**: codec, sample rate, bit depth, decoded sample format, bitrate, channels, duration, size.
- **Levels**: peak, true peak (4x oversampled), RMS, integrated loudness (LUFS, ITU-R BS.1770-4 with gating), dynamic range, clipping detection, all per channel.
- **Spectrogram**: STFT heatmap with a combined view and one tab per channel, adjustable window size (1024, 2048, 4096).
- **Authenticity check**: looks for a brick-wall cutoff in the long-term spectrum and tells you whether a lossless file looks genuine, transcoded from MP3/AAC, or upsampled from a lower sample rate. It also notices 16-bit audio padded into a 24-bit container.
- **Inputs**: system file picker, a scanned library (MediaStore), and "open with" from other apps.
- Everything runs on the device. Nothing is uploaded.

## How the analysis works

1. `MediaProbe` reads stream facts with FFprobe.
2. `AudioDecoder` decodes to raw 32-bit float PCM in the app cache (native sample rate and channel layout, no resampling).
3. `LevelAnalyzer` makes one streaming pass: peaks, RMS, K-weighted loudness, true peak, clipping runs and effective bit depth.
4. `SpectrogramAnalyzer` takes up to 1600 evenly spaced FFT frames per channel and builds a long-term average spectrum.
5. `AuthenticityAnalyzer` searches that spectrum for a steep drop that never recovers, then classifies it.

The DSP code in `app/src/main/java/com/rotatsulabs/specty/audio/` has no Android dependencies, so its tests run as plain JVM unit tests.

### Reading the numbers

- **Dynamic Range** is peak minus RMS in dB (a crest-factor style figure), not the DR-meter (TT) algorithm.
- **True Peak** uses a windowed-sinc interpolator and can read about 0.1 dB high on adversarial signals.
- **Spectral Cutoff** is where a detected cliff sits; it reads the Nyquist frequency when the file is full-band.
- The authenticity verdict is a heuristic. A sharp cutoff near 16 kHz is strong evidence of a lossy origin; a cutoff near 20 kHz is only suggestive, because some masters are filtered that way.

## Building

Requirements: JDK 17, Android SDK 36, Gradle 8.14+ (or open the folder in Android Studio).

```sh
gradle :app:testDebugUnitTest   # DSP unit tests
gradle :app:assembleDebug       # debug APK in app/build/outputs/apk/debug/
```

Min SDK is 33 (Android 13). Package ID: `com.rotatsulabs.specty`.

### Continuous integration

GitHub Actions (`.github/workflows/build.yml`) runs the unit tests and builds a debug APK on every push, uploaded as the `audexa-debug-apk` artifact.

## Decoding

Decoding uses FFmpeg through the maintained `dev.ffmpegkit-maintained:ffmpeg-kit-min` fork, a drop-in continuation of the retired `arthenica/ffmpeg-kit` (same `com.arthenica.ffmpegkit` API). FFmpeg's own native decoders handle ALAC, FLAC, MP3, AAC, Opus, Vorbis, WAV and others.

## Roadmap

- Streaming decode through a pipe instead of a temp file
- Zoom, pan and a time cursor on the spectrogram
- Selectable colour maps and dB range
- Batch scan of the library with a quality column
- Export a report (image / JSON)
- Track-level DR meter (TT) mode

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
