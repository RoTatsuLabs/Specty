package com.rotatsulabs.specty.ui.spectrogram

import com.rotatsulabs.specty.audio.AudioInfo
import com.rotatsulabs.specty.audio.Codecs
import com.rotatsulabs.specty.audio.LevelStats
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.components.Fmt
import java.util.Locale

/** The words and numbers on an exported report image, free of drawing code so they can be tested. */
object ReportFacts {
    data class Metric(val label: String, val value: String, val unit: String = "")

    /** The tag shown beside the file facts; Atmos wins over the lossless tags. */
    enum class Quality(val label: String) {
        Lossless("LOSSLESS"),
        HiRes("HI-RES LOSSLESS"),
        Atmos("DOLBY ATMOS"),
    }

    fun quality(info: AudioInfo, verdict: Verdict): Quality? = when {
        info.isDolbyAtmos -> Quality.Atmos
        verdict == Verdict.HI_RES -> Quality.HiRes
        verdict == Verdict.GENUINE -> Quality.Lossless
        else -> null
    }

    fun headline(verdict: Verdict): String = when (verdict) {
        Verdict.GENUINE -> "Lossless, no sign of a lossy origin"
        Verdict.HI_RES -> "Hi-Res lossless, no sign of a lossy origin"
        Verdict.LOSSY_SOURCE -> "Likely transcoded from a lossy source"
        Verdict.POSSIBLE_LOSSY_SOURCE -> "Possibly from a lossy source"
        Verdict.UPSAMPLED -> "Likely upsampled"
        Verdict.BAND_LIMITED -> "Band-limited spectrum"
        Verdict.LOSSY_CODEC -> "Lossy format"
        Verdict.DOLBY_ATMOS -> "Dolby Atmos"
        Verdict.INCONCLUSIVE -> "Inconclusive"
    }

    /** Short facts about the file, in reading order. */
    fun chips(info: AudioInfo): List<String> = listOfNotNull(
        Codecs.displayName(info.codec),
        Fmt.kHz(info.sampleRate.toDouble()),
        info.bitDepth?.let { "$it-bit" },
        when (info.channels) {
            1 -> "Mono"
            2 -> "Stereo"
            else -> "${info.channels} channels"
        },
        Fmt.duration(info.durationSec).takeIf { info.durationSec > 0 },
        Fmt.size(info.sizeBytes).takeIf { info.sizeBytes > 0 },
    )

    /** The measurements, with the three headline ones first. Values that were not measured are left out or dashed. */
    fun metrics(levels: LevelStats): List<Metric> {
        fun split(label: String, text: String): Metric {
            val unit = text.substringAfterLast(' ', "")
            return if (unit.isEmpty()) Metric(label, text) else Metric(label, text.substringBeforeLast(' '), unit)
        }
        return listOfNotNull(
            split("Dynamic Range", Fmt.db(levels.dynamicRangeDb)),
            split("Loudness", Fmt.lufs(levels.lufs)),
            split("True Peak", Fmt.dbtp(levels.truePeakDbtp)),
            split("Peak", Fmt.db(levels.peakDb)),
            split("RMS", Fmt.db(levels.rmsDb)),
            Metric("Effective Bit Depth", levels.effectiveBitDepth?.let { "$it" } ?: "—", if (levels.effectiveBitDepth != null) "bit" else ""),
            Metric("Clipped Samples", Fmt.count(levels.clippedSamples)),
            levels.drTt?.let { Metric("DR Meter", String.format(Locale.US, "DR%.0f", it)) },
            levels.correlation?.let { Metric("Stereo Correlation", String.format(Locale.US, "%.2f", it)) },
        )
    }

    /** A file name that keeps the title readable (any script) and drops what file systems refuse. */
    fun fileName(title: String, stamp: String): String {
        val cleaned = title
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)
            .trim()
            .trimEnd('.')
        return "${cleaned.ifEmpty { "analysis" }}-report-$stamp.png"
    }
}
