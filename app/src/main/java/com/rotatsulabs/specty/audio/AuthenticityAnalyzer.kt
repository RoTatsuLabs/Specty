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
        val bins = averageDb.size
        val binHz = info.sampleRate.toDouble() / fftSize
        val nyquist = info.nyquistHz
        val s = smooth(averageDb, 3)

        val floorBins = max(8, bins * 3 / 100).coerceAtMost(bins)
        val floor = median(s.copyOfRange(bins - floorBins, bins))

        val k1 = (1500.0 / binHz).toInt().coerceIn(0, bins - 1)
        val k2 = (5000.0 / binHz).toInt().coerceIn(k1 + 1, bins)
        var reference = 0.0
        for (k in k1 until k2) reference += s[k]
        reference /= (k2 - k1)

        if (reference < SILENT_REFERENCE_DB) {
            return AuthenticityReport(
                Verdict.INCONCLUSIVE, nyquist, false, 0.0,
                "There is not enough audible content to judge the spectrum.",
            )
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
