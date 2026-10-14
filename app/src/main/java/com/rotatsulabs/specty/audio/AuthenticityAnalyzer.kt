package com.rotatsulabs.specty.audio

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decides whether a file's spectrum looks genuine or betrays a lossy / upsampled origin by looking
 * for an abrupt, brick-wall cutoff in the long-term average spectrum.
 */
object AuthenticityAnalyzer {
    private const val MIN_DYNAMIC_DB = 25.0
    private const val MIN_CLIFF_DB = 28.0
    private const val PERMANENT_DROP_DB = 20.0
    private const val SILENT_REFERENCE_DB = -90.0

    fun analyze(
        info: AudioInfo,
        averageDb: FloatArray,
        fftSize: Int,
        effectiveBitDepth: Int?,
    ): AuthenticityReport {
        val nyquist = info.nyquistHz
        val d = detect(averageDb, info.sampleRate, fftSize)

        if (d.reference < SILENT_REFERENCE_DB) {
            return AuthenticityReport(
                Verdict.INCONCLUSIVE, nyquist, false, 0.0,
                "There is not enough audible content to judge the spectrum.",
            )
        }
        val hasCliff = d.hasCliff
        val cutoff = d.cutoff
        val bestDrop = d.drop

        val cutoffKhz = fmt(cutoff / 1000.0)
        val bitDepthNote = bitDepthNote(info, effectiveBitDepth)

        if (info.isLossy) {
            val note = if (hasCliff) {
                "${Codecs.displayName(info.codec)} is a lossy codec; its encoder low-pass sits near $cutoffKhz kHz."
            } else {
                "${Codecs.displayName(info.codec)} is a lossy codec, so this is not a lossless file."
            }
            return AuthenticityReport(Verdict.LOSSY_CODEC, cutoff, hasCliff, bestDrop, note)
        }

        if (!hasCliff) {
            val note = "Content extends to the top of the spectrum with no abrupt cutoff.$bitDepthNote"
            return AuthenticityReport(Verdict.GENUINE, cutoff, false, bestDrop, note)
        }

        val rateKhz = fmt(info.sampleRate / 1000.0)
        val (verdict, note) = when {
            cutoff <= 16_800.0 -> Verdict.LOSSY_SOURCE to
                "Hard cutoff near $cutoffKhz kHz, typical of an MP3 or AAC low-pass. " +
                "This file was most likely transcoded from a lossy source."
            info.sampleRate > 48_000 && cutoff in 20_500.0..23_000.0 -> Verdict.UPSAMPLED to
                "Content stops near $cutoffKhz kHz inside a $rateKhz kHz file. " +
                "It was most likely upsampled from a 44.1 or 48 kHz source."
            info.sampleRate <= 48_000 && cutoff <= 20_800.0 -> Verdict.POSSIBLE_LOSSY_SOURCE to
                "Sharp cutoff near $cutoffKhz kHz. This can come from a high-bitrate lossy source, " +
                "but some masters are also filtered this way."
            else -> Verdict.BAND_LIMITED to
                "The spectrum is band-limited near $cutoffKhz kHz. That may be a mastering filter or an older source."
        }
        return AuthenticityReport(verdict, cutoff, true, bestDrop, note + bitDepthNote)
    }

    const val TIMELINE_SEGMENTS = 24

    /**
     * Adds confidence, a per-segment cutoff track, an encoder guess and the empty-band edge to a verdict.
     * [segmentSpectra] holds one average spectrum per slice of the track (null for silent slices).
     */
    fun withInsights(
        report: AuthenticityReport,
        info: AudioInfo,
        averageDb: FloatArray,
        fftSize: Int,
        segmentSpectra: List<FloatArray?>,
    ): AuthenticityReport {
        if (report.verdict == Verdict.INCONCLUSIVE) return report.copy(confidence = 0)
        val whole = detect(averageDb, info.sampleRate, fftSize)
        val cutoffs = FloatArray(segmentSpectra.size)
        var judged = 0
        var agreeing = 0
        for ((i, spectrum) in segmentSpectra.withIndex()) {
            if (spectrum == null) continue
            val d = detect(spectrum, info.sampleRate, fftSize)
            if (d.reference < SILENT_REFERENCE_DB) continue
            judged++
            if (d.hasCliff) cutoffs[i] = d.cutoff.toFloat()
            val consistent = if (report.hasCliff) {
                d.hasCliff && kotlin.math.abs(d.cutoff - report.cutoffHz) <= 1000.0
            } else {
                !d.hasCliff
            }
            if (consistent) agreeing++
        }
        val agree = if (judged == 0) 1.0 else agreeing.toDouble() / judged

        var confidence = if (report.hasCliff) {
            50.0 + (report.cliffDropDb - MIN_CLIFF_DB).coerceIn(0.0, 25.0) + 20.0 * agree
        } else {
            55.0 + 40.0 * agree
        }
        confidence -= when (report.verdict) {
            Verdict.POSSIBLE_LOSSY_SOURCE -> 12.0
            Verdict.BAND_LIMITED -> 15.0
            else -> 0.0
        }
        if (report.verdict == Verdict.LOSSY_CODEC) confidence = 99.0
        if (whole.dynamic < 35.0 && report.verdict != Verdict.LOSSY_CODEC) confidence -= 10.0

        val guess = if (report.hasCliff && report.verdict != Verdict.GENUINE && report.verdict != Verdict.UPSAMPLED) {
            encoderGuess(report.cutoffHz, info.codec)
        } else {
            null
        }
        return report.copy(
            confidence = confidence.roundToInt().coerceIn(5, 99),
            segmentCutoffs = cutoffs,
            encoderGuess = guess,
            emptyAboveHz = whole.emptyAboveHz,
        )
    }

    /** Typical low-pass frequencies of common encoders; a hint, since presets and encoders vary. */
    fun encoderGuess(cutoffHz: Double, codec: String): String? {
        val khz = cutoffHz / 1000.0
        val family = if (codec.equals("aac", true)) "AAC" else if (codec.equals("opus", true)) "Opus" else "MP3 / AAC"
        return when {
            khz < 10.0 -> null
            khz < 15.2 -> "$family at a low bitrate (about 96 kbps or less)"
            khz < 16.4 -> "$family around 112 to 128 kbps"
            khz < 17.6 -> "$family around 128 to 160 kbps (LAME 128 kbps low-passes near 17 kHz)"
            khz < 19.0 -> "$family around 160 to 192 kbps (LAME V2 is near 18.6 kHz)"
            khz < 20.2 -> "$family around 224 to 256 kbps (LAME V0 is near 19.5 kHz)"
            khz < 21.2 -> "$family at 256 to 320 kbps (LAME 320 kbps is near 20.5 kHz)"
            else -> null
        }
    }

    /**
     * Averages the spectrogram's mixed view into [segments] slices, ignoring quiet columns.
     * Returns an empty list when the spectrogram is too short to split.
     */
    fun segmentSpectra(spec: Spectrogram, segments: Int = TIMELINE_SEGMENTS): List<FloatArray?> {
        val count = minOf(segments, spec.columns / 6)
        if (count < 2) return emptyList()
        val view = spec.views[0]
        val bins = spec.bins
        val scale = ((Spectrogram.MAX_DB - Spectrogram.MIN_DB) / 255.0).toFloat()
        return List(count) { seg ->
            val from = seg * spec.columns / count
            val to = (seg + 1) * spec.columns / count
            val sum = FloatArray(bins)
            var active = 0
            for (col in from until to) {
                var loud = false
                for (k in 0 until bins) {
                    if ((view[col * bins + k].toInt() and 0xFF) * scale + Spectrogram.MIN_DB > -60.0) { loud = true; break }
                }
                if (!loud) continue
                for (k in 0 until bins) {
                    sum[k] += (view[col * bins + k].toInt() and 0xFF) * scale + Spectrogram.MIN_DB.toFloat()
                }
                active++
            }
            if (active == 0) {
                null
            } else {
                for (k in 0 until bins) sum[k] /= active
                sum
            }
        }
    }

    /** Result of searching an average spectrum for a brick-wall cutoff. */
    class Detection(
        val reference: Double,
        val dynamic: Double,
        val drop: Double,
        val hasCliff: Boolean,
        val cutoff: Double,
        val emptyAboveHz: Double?,
    )

    fun detect(averageDb: FloatArray, sampleRate: Int, fftSize: Int): Detection {
        val bins = averageDb.size
        val binHz = sampleRate.toDouble() / fftSize
        val nyquist = sampleRate / 2.0
        val s = smooth(averageDb, 3)

        val floorBins = max(8, bins * 3 / 100).coerceAtMost(bins)
        val floor = median(s.copyOfRange(bins - floorBins, bins))

        val k1 = (1500.0 / binHz).toInt().coerceIn(0, bins - 1)
        val k2 = (5000.0 / binHz).toInt().coerceIn(k1 + 1, bins)
        var reference = 0.0
        for (k in k1 until k2) reference += s[k]
        reference /= (k2 - k1)
        if (reference < SILENT_REFERENCE_DB) {
            return Detection(reference, 0.0, 0.0, false, nyquist, null)
        }

        val dynamic = reference - floor
        val window = max(3, (600.0 / binHz).roundToInt())
        val startK = (4000.0 / binHz).roundToInt()
        // A cliff is a steep drop over ~600 Hz after which the spectrum never climbs back up.
        // Notches and gradual roll-off do not qualify.
        val suffixMax = FloatArray(bins)
        run {
            var running = Float.NEGATIVE_INFINITY
            for (k in bins - 1 downTo 0) {
                if (s[k] > running) running = s[k]
                suffixMax[k] = running
            }
        }
        var bestDrop = 0.0
        var bestK = -1
        if (dynamic >= MIN_DYNAMIC_DB && startK + window < bins) {
            for (k in startK until bins - window) {
                val drop = (s[k] - s[k + window]).toDouble()
                if (drop > bestDrop && suffixMax[k + window] <= s[k] - PERMANENT_DROP_DB) {
                    bestDrop = drop
                    bestK = k
                }
            }
        }
        val hasCliff = bestDrop >= MIN_CLIFF_DB && bestK >= 0

        var cutoff = nyquist
        if (hasCliff) {
            // Edge = first bin that falls below the midpoint of the drop.
            val mid = (s[bestK] + s[bestK + window]) / 2f
            var edge = bestK
            while (edge < bestK + window && s[edge] > mid) edge++
            cutoff = (edge * binHz).coerceAtMost(nyquist)
        }

        // Highest frequency still above the noise floor: everything past it is empty.
        var emptyAbove: Double? = null
        val threshold = max(floor + 6.0, -110.0).toFloat()
        var top = bins - 1
        while (top > 0 && s[top] <= threshold) top--
        val topHz = (top + 1) * binHz
        if (top < bins - 1 && topHz < nyquist * 0.97 && topHz > 1000.0) emptyAbove = topHz

        return Detection(reference, dynamic, bestDrop, hasCliff, cutoff, emptyAbove)
    }

    private fun bitDepthNote(info: AudioInfo, effectiveBitDepth: Int?): String {
        val declared = info.bitDepth ?: return ""
        val effective = effectiveBitDepth ?: return ""
        if (!info.isLossless || declared < 24) return ""
        return if (effective <= 16) {
            " Only the top $effective bits carry data, so this is $effective-bit audio padded to $declared-bit."
        } else {
            ""
        }
    }

    private fun smooth(values: FloatArray, radius: Int): FloatArray {
        val out = FloatArray(values.size)
        for (i in values.indices) {
            val lo = max(0, i - radius)
            val hi = minOf(values.size - 1, i + radius)
            var sum = 0.0f
            for (j in lo..hi) sum += values[j]
            out[i] = sum / (hi - lo + 1)
        }
        return out
    }

    private fun median(values: FloatArray): Float {
        val sorted = values.copyOf()
        sorted.sort()
        return sorted[sorted.size / 2]
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
}
