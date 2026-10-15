package com.rotatsulabs.specty.ui.spectrogram

import android.content.SharedPreferences

/** Level window mapped onto the colour ramp. A tighter window brings out faint detail. */
enum class DbRange(val label: String, val floor: Int, val ceil: Int) {
    Wide("Wide", -120, 0),
    Standard("Standard", -100, -10),
    Tight("Tight", -80, -20),
}

/** How the spectrogram image is drawn; the analysis itself is unaffected. Saved so every view agrees. */
data class SpecOptions(
    val log: Boolean = false,
    val range: DbRange = DbRange.Wide,
    val cutoffLine: Boolean = true,
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_LOG, log).putInt(KEY_RANGE, range.ordinal).putBoolean(KEY_CUTOFF, cutoffLine).apply()
    }

    companion object {
        private const val KEY_LOG = "spectrogram_log"
        private const val KEY_RANGE = "spectrogram_range"
        private const val KEY_CUTOFF = "spectrogram_cutoff"

        fun load(prefs: SharedPreferences) = SpecOptions(
            log = prefs.getBoolean(KEY_LOG, false),
            range = DbRange.entries.getOrElse(prefs.getInt(KEY_RANGE, 0)) { DbRange.Wide },
            cutoffLine = prefs.getBoolean(KEY_CUTOFF, true),
        )
    }
}
