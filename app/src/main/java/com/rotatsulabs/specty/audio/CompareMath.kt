package com.rotatsulabs.specty.audio

import kotlin.math.max
import kotlin.math.min

/** Pure helpers behind the compare screen. */
object CompareMath {
    private const val SCALE = ((Spectrogram.MAX_DB - Spectrogram.MIN_DB) / 255.0)

    private fun dbOf(q: Byte): Float = (Spectrogram.MIN_DB + (q.toInt() and 0xFF) * SCALE).toFloat()

    /** Level of the mixed view at a normalised time (0..1) and frequency in Hz. */
    fun levelAt(s: Spectrogram, timeFrac: Double, hz: Double): Float {
        val col = (timeFrac * (s.columns - 1)).toInt().coerceIn(0, s.columns - 1)
        val bin = (hz / (s.sampleRate / 2.0) * (s.bins - 1)).toInt().coerceIn(0, s.bins - 1)
        return dbOf(s.views[0][col * s.bins + bin])
    }

    /**
     * Level of B minus level of A in dB on a [width] x [height] grid. Time is normalised to each file's own length and
     * frequency covers 0 to the lower Nyquist of the two. Row 0 is the top (highest frequency).
     */
    fun difference(a: Spectrogram, b: Spectrogram, width: Int, height: Int): FloatArray {
        val maxHz = min(a.sampleRate, b.sampleRate) / 2.0
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            val hz = (1.0 - (y + 0.5) / height) * maxHz
            for (x in 0 until width) {
                val t = (x + 0.5) / width
                out[y * width + x] = levelAt(b, t, hz) - levelAt(a, t, hz)
            }
        }
        return out
    }

    /** Mean level per bin over the audible columns (any bin above -60 dB), in dB. */
    fun averageSpectrum(s: Spectrogram): FloatArray {
        val sum = DoubleArray(s.bins)
        var count = 0
        val view = s.views[0]
        for (c in 0 until s.columns) {
            var loud = false
            for (k in 0 until s.bins) if (dbOf(view[c * s.bins + k]) > -60f) { loud = true; break }
            if (!loud) continue
            for (k in 0 until s.bins) sum[k] += dbOf(view[c * s.bins + k])
            count++
        }
        if (count == 0) return FloatArray(s.bins) { Spectrogram.MIN_DB.toFloat() }
        return FloatArray(s.bins) { (sum[it] / count).toFloat() }
    }

    /** Mean absolute difference over the grid, a single "how different do they look" number in dB. */
    fun meanAbsolute(diff: FloatArray): Double {
        if (diff.isEmpty()) return 0.0
        var sum = 0.0
        for (v in diff) sum += kotlin.math.abs(v)
        return sum / diff.size
    }

    /** Short plain-language differences between two analyses, most telling first. */
    fun highlights(ca: AuthenticityReport, la: LevelStats, cb: AuthenticityReport, lb: LevelStats): List<String> {
        val out = ArrayList<String>()
        if (ca.verdict != cb.verdict) {
            out += "Verdicts differ: A is ${label(ca.verdict)}, B is ${label(cb.verdict)}."
        }
        if (ca.hasCliff != cb.hasCliff || (ca.hasCliff && kotlin.math.abs(ca.cutoffHz - cb.cutoffHz) > 1000.0)) {
            val hi = if (ca.cutoffHz >= cb.cutoffHz) "A" else "B"
            val diff = kotlin.math.abs(ca.cutoffHz - cb.cutoffHz) / 1000.0
            out += "$hi keeps ${String.format(java.util.Locale.US, "%.1f", diff)} kHz more top end (cutoff ${khz(ca.cutoffHz)} vs ${khz(cb.cutoffHz)})."
        }
        val dra = la.drTt; val drb = lb.drTt
        if (dra != null && drb != null && kotlin.math.abs(dra - drb) >= 1.0) {
            val hi = if (dra > drb) "A" else "B"
            out += "$hi is more dynamic (DR${dra.toInt()} vs DR${drb.toInt()})."
        }
        val lua = la.lufs; val lub = lb.lufs
        if (lua != null && lub != null && kotlin.math.abs(lua - lub) >= 1.0) {
            out += "${if (lua > lub) "A" else "B"} is ${String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(lua - lub))} LU louder."
        }
        if (la.clippedSamples != lb.clippedSamples && max(la.clippedSamples, lb.clippedSamples) > 0) {
            out += "${if (la.clippedSamples > lb.clippedSamples) "A" else "B"} has more clipping."
        }
        if (out.isEmpty()) out += "These two files measure almost the same."
        return out
    }

    private fun khz(hz: Double) = String.format(java.util.Locale.US, "%.1f kHz", hz / 1000.0)

    private fun label(v: Verdict) = when (v) {
        Verdict.GENUINE -> "genuine"
        Verdict.LOSSY_SOURCE -> "from a lossy source"
        Verdict.POSSIBLE_LOSSY_SOURCE -> "possibly from a lossy source"
        Verdict.UPSAMPLED -> "upsampled"
        Verdict.BAND_LIMITED -> "band-limited"
        Verdict.LOSSY_CODEC -> "a lossy format"
        Verdict.INCONCLUSIVE -> "inconclusive"
    }
}
