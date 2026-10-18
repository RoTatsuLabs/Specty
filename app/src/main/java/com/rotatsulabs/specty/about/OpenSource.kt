package com.rotatsulabs.specty.about

/** Licences that apply to Specty and what it ships. The full texts are bundled under `assets/licenses`. */
enum class LicenseKind(val title: String, val spdx: String, val asset: String, val url: String) {
    Apache2("Apache License 2.0", "Apache-2.0", "licenses/Apache-2.0.txt", "https://www.apache.org/licenses/LICENSE-2.0"),
    Lgpl3("GNU Lesser General Public License v3.0", "LGPL-3.0", "licenses/LGPL-3.0.txt", "https://www.gnu.org/licenses/lgpl-3.0.html"),
    Gpl3("GNU General Public License v3.0", "GPL-3.0", "licenses/GPL-3.0.txt", "https://www.gnu.org/licenses/gpl-3.0.html"),
}

data class Library(
    val name: String,
    val author: String,
    val version: String?,
    val license: LicenseKind,
    val url: String,
    val summary: String,
    /** What Specty uses it for, or a licence-specific note. */
    val note: String? = null,
)

/**
 * Everything compiled into the app. Versions follow `gradle/libs.versions.toml`; a unit test fails when they drift.
 * Tools used only to build or test Specty are not shipped and are not listed.
 */
object OpenSource {
    const val SOURCE_URL = "https://github.com/RoTatsuLabs/Specty"

    val app = Library(
        name = "Specty",
        author = "RoTatsu Labs",
        version = null,
        license = LicenseKind.Gpl3,
        url = SOURCE_URL,
        summary = "Audio quality analyser for Android.",
        note = "Specty is free software under the GNU General Public License, version 3. You can use, study, change and share it under those terms.",
    )

    val libraries: List<Library> = listOf(
        Library(
            "FFmpegKit (maintained fork)", "ffmpegkit-maintained", "8.1.9", LicenseKind.Lgpl3,
            "https://github.com/ffmpegkit-maintained/ffmpeg",
            "FFmpeg for Android, a continuation of the retired Arthenica ffmpeg-kit.",
            "Reads and decodes every audio file Specty analyses. It includes the FFmpeg libraries (libavcodec, libavformat, " +
                "libavutil and others), also under the LGPL. The source is available from the link above.",
        ),
        Library(
            "Jetpack Compose", "Google / Android Open Source Project", "BOM 2026.03.01", LicenseKind.Apache2,
            "https://developer.android.com/jetpack/compose",
            "UI toolkit: UI, Graphics, Foundation, Material 3 and Material Icons Extended.",
            "Draws the whole interface. The icons come from the Material Icons set.",
        ),
        Library(
            "Activity Compose", "Google / Android Open Source Project", "1.11.0", LicenseKind.Apache2,
            "https://developer.android.com/jetpack/androidx/releases/activity",
            "Compose integration for activities, result contracts and edge-to-edge.",
        ),
        Library(
            "Lifecycle", "Google / Android Open Source Project", "2.9.4", LicenseKind.Apache2,
            "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            "ViewModel and lifecycle-aware state for Compose.",
        ),
        Library(
            "Core KTX", "Google / Android Open Source Project", "1.17.0", LicenseKind.Apache2,
            "https://developer.android.com/jetpack/androidx/releases/core",
            "Kotlin extensions for the Android framework.",
        ),
        Library(
            "Other AndroidX components", "Google / Android Open Source Project", null, LicenseKind.Apache2,
            "https://developer.android.com/jetpack/androidx",
            "Annotation, Collection, Saved State, Startup and other AndroidX libraries that the ones above depend on.",
        ),
        Library(
            "Kotlin Standard Library", "JetBrains", "2.3.21", LicenseKind.Apache2,
            "https://kotlinlang.org",
            "The Kotlin runtime.",
        ),
        Library(
            "kotlinx.coroutines", "JetBrains", "1.10.2", LicenseKind.Apache2,
            "https://github.com/Kotlin/kotlinx.coroutines",
            "Background work and cancellation for analysis and library scanning.",
        ),
    )
}
