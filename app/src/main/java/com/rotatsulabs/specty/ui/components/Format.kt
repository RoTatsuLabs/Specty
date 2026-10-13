package com.rotatsulabs.specty.ui.components

import java.util.Locale
import kotlin.math.roundToInt

/** Number formatting shared by the screens. Always uses a dot decimal and ASCII minus. */
object Fmt {
    private const val DASH = "—"

    fun db(value: Double, decimals: Int = 2): String =
        if (value <= -150.0) "-inf dB" else String.format(Locale.US, "%.${decimals}f dB", value)

    fun dbtp(value: Double): String =
        if (value <= -150.0) "-inf dBTP" else String.format(Locale.US, "%.2f dBTP", value)

    fun lufs(value: Double?): String =
        if (value == null) DASH else String.format(Locale.US, "%.1f LUFS", value)

    fun short(value: Double): String = String.format(Locale.US, "%.1f", value)

    fun kHz(hz: Double): String = String.format(Locale.US, "%.1f kHz", hz / 1000.0)

    fun duration(seconds: Double): String {
        val total = seconds.roundToInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun size(bytes: Long): String {
        if (bytes <= 0) return DASH
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.1f MB", mb)
    }

    fun bitrate(bps: Long?): String = when {
        bps == null || bps <= 0 -> DASH
        bps >= 1_000_000 -> String.format(Locale.US, "%.2f Mbps", bps / 1_000_000.0)
        else -> String.format(Locale.US, "%d kbps", (bps / 1000.0).roundToInt())
    }

    fun count(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    fun perChannel(peak: Double, rms: Double, dr: Double): String =
        String.format(Locale.US, "P %.1f / R %.1f / DR %.1f", peak, rms, dr)
}
