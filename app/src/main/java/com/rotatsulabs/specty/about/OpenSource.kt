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
    @param:StringRes val summary: Int,
    /** What Specty uses it for, or a licence-specific note. */
    @param:StringRes val note: Int? = null,
)

/**
 * The credits that need a note of their own: a library with a licence obligation, and code ported from another app.
 * Every library the build pulls in is listed on the licences page, which is generated from the dependencies.
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
            "FFmpegKitNext", "arthenica", "9.0.2-r1", LicenseKind.Lgpl3,
            "https://github.com/arthenica/ffmpeg-kit-next",
            R.string.lib_ffmpeg_summary,
            R.string.lib_ffmpeg_note,
        ),
        Library(
            "Mornye", "Mornye contributors", null, LicenseKind.Lgpl3,
            "https://github.com/mornyeapp/Mornye",
            R.string.lib_mornye_summary,
            R.string.lib_mornye_note,
        ),
    )
}
