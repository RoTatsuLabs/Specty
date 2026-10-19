package com.rotatsulabs.specty.about

import androidx.annotation.StringRes
import com.rotatsulabs.specty.R

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
    @StringRes val summary: Int,
    /** What Specty uses it for, or a licence-specific note. */
    @StringRes val note: Int? = null,
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
        summary = R.string.lib_specty_summary,
        note = R.string.lib_specty_note,
    )

    val libraries: List<Library> = listOf(
        Library(
            "FFmpegKit (maintained fork)", "ffmpegkit-maintained", "8.1.9", LicenseKind.Lgpl3,
            "https://github.com/ffmpegkit-maintained/ffmpeg",
            R.string.lib_ffmpeg_summary,
            R.string.lib_ffmpeg_note,
        ),
        Library(
            "Jetpack Compose", "Google / Android Open Source Project", "BOM 2026.03.01", LicenseKind.Apache2,
            "https://developer.android.com/jetpack/compose",
            R.string.lib_compose_summary,
            R.string.lib_compose_note,
        ),
        Library(
            "Activity Compose", "Google / Android Open Source Project", "1.11.0", LicenseKind.Apache2,
            "https://developer.android.com/jetpack/androidx/releases/activity",
            R.string.lib_activity_summary,
        ),
        Library(
            "Lifecycle", "Google / Android Open Source Project", "2.9.4", LicenseKind.Apache2,
            "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            R.string.lib_lifecycle_summary,
        ),
        Library(
            "Core KTX", "Google / Android Open Source Project", "1.17.0", LicenseKind.Apache2,
            "https://developer.android.com/jetpack/androidx/releases/core",
            R.string.lib_core_summary,
        ),
        Library(
            "Other AndroidX components", "Google / Android Open Source Project", null, LicenseKind.Apache2,
            "https://developer.android.com/jetpack/androidx",
            R.string.lib_androidx_summary,
        ),
        Library(
            "Kotlin Standard Library", "JetBrains", "2.3.21", LicenseKind.Apache2,
            "https://kotlinlang.org",
            R.string.lib_kotlin_summary,
        ),
        Library(
            "kotlinx.coroutines", "JetBrains", "1.10.2", LicenseKind.Apache2,
            "https://github.com/Kotlin/kotlinx.coroutines",
            R.string.lib_coroutines_summary,
        ),
    )
}
