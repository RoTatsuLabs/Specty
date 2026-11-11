# Changelog

All notable changes to Specty are listed here, newest version first. Entries sit under Additions, Changes, Fixes, or Other, and each release gets a heading like `## [1.0.0] - 2026-01-31`.

## [Unreleased]

TL;DR: A rebuilt Material 3 Expressive interface, clearer verdicts, a stricter upsampling check, an analysis queue and five new languages.

### Additions

- **Tabs and navigation bar:** Tracks, Albums, Artists and Search in a floating bar. Album and artist pages share their cover with the list.
- **Settings search:** find a setting by name and jump to it.
- **Analysis queue:** tap more tracks while one is running and they wait in line.
- **Roll-off verdict:** a gradual high-frequency fade is no longer blamed on a lossy source.
- **Evidence labels:** Strong, Moderate or Limited, shown in the app, Compare and exports.
- **Library insights:** totals for your library and a duplicate finder with a one-tap Compare against the best copy.
- **Sample checks:** Specty spots upsampling by repeated or interpolated samples and by a 16-bit noise floor, with checks ported from Mornye.
- **Open source licences:** a searchable page of every library, opened from About.
- **Languages:** Spanish, Brazilian Portuguese, Russian, Japanese and Traditional Chinese.
- **New settings:** Tab titles for the navigation bar and Reduce motion.

### Changes

- **New interface:** rebuilt on Material 3 Expressive, with animated tab switching and a stretching navigation pill.
- **Verdict badge:** an icon and a short word replace the chip.
- **Confidence:** shown as an evidence label instead of a percentage.
- **Sharp cutoff:** a sharp high-frequency edge is judged more carefully, and an edge that moves along the track is not proof of a lossy source.
- **Upsampled and Hi-Res:** Upsampled from a cutoff needs a second sign, and Hi-Res Lossless needs the resolution to hold up.
- **Library:** grouping by artist or album is replaced by their pages, and Sharp cutoff and Roll-off results are filed under Unclear.
- **Spectrogram card:** display options moved to Settings.
- **About:** a new layout.
- **Onboarding:** the introduction pages are gone.
- **Saved results:** measured again once after updating.

### Fixes

- **Repeat decoding:** tapping an already analysed track decoded it again. It now opens the saved result.
- **Rapid taps:** tapping a second track during an analysis could restart the work. It now waits its turn.

### Other

- **Modules:** the code is split into the analysis engine, data, design system and app.
- **Tooling:** the build uses Android Gradle Plugin 9.1 and Kotlin 2.4.

## [2.0.0] - 2026-10-28

TL;DR: Export is now a full page with three image formats, colour choices, a caption and the song's cover. Specty has landscape and tablet layouts, a 32-bit ARM build, and decodes with FFmpegKitNext.

### Additions

- An export page with a live preview. Pick a 9:16, 1:1 or 16:9 image and see what each format is good and bad at. Choose which sections appear (cover, file details, verdict, key levels, more measurements, loudness over time, spectrogram, footer), match the app's colours or use light, dark or AMOLED, and add an optional caption.
- The song's cover art shows in the exported report and in the saved spectrogram image.
- Landscape and tablet layouts. The analysis uses two columns on wide windows, the library grid adds columns as the window widens, export puts the preview beside its options, and the remaining screens stay in a centred column.
- A 32-bit ARM (armeabi-v7a) build.

### Changes

- Decoding and probing use FFmpegKitNext instead of the maintained FFmpegKit fork.
- Changing the app language fades the screen. It used to show a black frame.
- The export button opens the export page instead of a menu.

### Fixes

- The title was covered by the interface during the transition between the library and the analysis.
- The verdict card no longer repeats the file facts line, and the spectrogram buttons fill their row.

## [1.1.0] - 2026-10-28

TL;DR: Specty now runs on Android 8, comes in Indonesian and Simplified Chinese, and scans libraries faster. The library gains result filters, sorting, grouping, excluded folders and two-track compare, and you can play a track with a moving cursor on its spectrogram.

### Additions

- Support for Android 8 (API 26) and newer. Dynamic colour is available on Android 12 and newer.
- Indonesian and Simplified Chinese translations for the whole app, with a language setting (System default, English, Indonesian, Simplified Chinese). Android 13 and newer also list these languages in per-app language settings.
- Library filter by result, opened from a sheet beside search. Each kind (unchecked, lossless, hi-res, needs a look, lossy format, Atmos, unclear) has a description and a count, and chosen kinds appear as removable chips.
- Library sorting by album, modified date or result, and grouping by artist, album, result or format. The layout is kept between launches.
- A full-screen Library folders page where you can exclude folders from the library and from scans.
- Scan only the tracks without a saved result, with the count shown in the menu.
- Long-press two tracks to open them in Compare.
- Playback with a moving cursor on the spectrogram.
- An icon beside the title of explicit tracks. A trailing "[E]" or "(Explicit)" in the title is no longer shown.

### Changes

- Overlay screens follow the system predictive back gesture.
- The theme change reveal is easier to see, with a soft edge and an accent ring.
- The codec card shows only facts read from the file.
- Dialog and sheet buttons, menu items, filter chips, filter rows, segments and colour swatches now react when pressed.
- Library scans are faster and give the same results:
  - Fewer operations per FFT, and one logarithm per spectrogram bin instead of up to three.
  - PCM is read once per true-peak worker instead of once per channel.
  - Tracks with a saved result skip the thermal and battery check.
  - The pause after each track follows the device's heat (50, 150 or 600 ms) instead of a flat 150 ms.
  - Saved results are written with the fastest gzip level.
- Saved results are measured again once after updating, because the stored notes changed format.

### Other

- Removed the interface sounds and the Sound effects setting.
- Removed the metadata card, the track queue flow, the library logo and an Easter egg.

## [1.0.0] - 2026-10-20

Specty's first public release, focused on audio quality analysis and spectral inspection.

### Additions

- Audio authenticity analysis with evidence-based verdicts and confidence levels.
- Lossless, Hi-Res Lossless and Dolby Atmos identification.
- Detailed audio metrics, including dynamic range, loudness and stereo statistics.
- Spectrogram visualization with fullscreen zoom and custom palettes.
- Audio comparison with spectrum overlays and metric differences.
- Music library with background scanning, filtering and list and grid views.
- Analysis report export for saving and sharing.
