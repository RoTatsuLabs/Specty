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
    fun highlights(ca: AuthenticityReport, la: LevelStats, cb: AuthenticityReport, lb: LevelStats): List<Note> {
        val out = ArrayList<Note>()
        if (ca.verdict != cb.verdict) {
            out += Note(NoteKey.VERDICTS_DIFFER, ca.verdict.name, cb.verdict.name)
        }
        val cutA = ca.cutoffHz
        val cutB = cb.cutoffHz
        if (cutA != null && cutB != null) {
            if (kotlin.math.abs(cutA - cutB) > 1000.0) {
                val hi = if (cutA >= cutB) "A" else "B"
                val diff = kotlin.math.abs(cutA - cutB) / 1000.0
                out += Note(NoteKey.MORE_TOP_END, hi, num(diff), num(cutA / 1000.0), num(cutB / 1000.0))
            }
        } else if (cutA != null && cb.shape == SpectrumShape.FULL_BAND) {
            out += Note(NoteKey.B_REACHES_TOP, num(cutA / 1000.0))
        } else if (cutB != null && ca.shape == SpectrumShape.FULL_BAND) {
            out += Note(NoteKey.A_REACHES_TOP, num(cutB / 1000.0))
        }
        val dra = la.drTt; val drb = lb.drTt
        if (dra != null && drb != null && kotlin.math.abs(dra - drb) >= 1.0) {
            val hi = if (dra > drb) "A" else "B"
            out += Note(NoteKey.MORE_DYNAMIC, hi, dra.toInt(), drb.toInt())
        }
        val lua = la.lufs; val lub = lb.lufs
        if (lua != null && lub != null && kotlin.math.abs(lua - lub) >= 1.0) {
            out += Note(NoteKey.LOUDER, if (lua > lub) "A" else "B", num(kotlin.math.abs(lua - lub)))
        }
        if (la.clippedSamples != lb.clippedSamples && max(la.clippedSamples, lb.clippedSamples) > 0) {
            out += Note(NoteKey.MORE_CLIPPING, if (la.clippedSamples > lb.clippedSamples) "A" else "B")
        }
        if (out.isEmpty()) out += Note(NoteKey.ALMOST_SAME)
        return out
    }

    private fun num(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
}
