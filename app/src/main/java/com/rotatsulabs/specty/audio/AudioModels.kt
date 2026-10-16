package com.rotatsulabs.specty.audio

import kotlin.math.abs

/** Static facts about the audio stream, taken from FFprobe. */
data class AudioInfo(
    val displayName: String,
    val codec: String,
    val container: String?,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int?,
    val sampleFormat: String?,
    val bitrateBps: Long?,
    val durationSec: Double,
    val sizeBytes: Long,
    /** Container and stream tags as read by FFprobe, lyrics excluded. Original key case is kept. */
    val tags: List<Pair<String, String>> = emptyList(),
    /** Codec profile as FFprobe reports it, e.g. "Dolby Digital Plus + Dolby Atmos". */
    val profile: String? = null,
) {
    /** Dolby Atmos is carried inside E-AC-3 (JOC) or TrueHD streams, and FFprobe names it in the profile. */
    val isDolbyAtmos: Boolean get() = profile?.contains("atmos", ignoreCase = true) == true
    val nyquistHz: Double get() = sampleRate / 2.0
    val isLossless: Boolean get() = Codecs.isLossless(codec)
    val isLossy: Boolean get() = Codecs.isLossy(codec)
    val channelLabel: String
        get() = when (channels) {
            1 -> "1 (mono)"
            2 -> "2 (stereo)"
            else -> channels.toString()
        }
}

object Codecs {
    private val lossy = setOf(
        "mp3", "mp2", "aac", "opus", "vorbis", "wmav1", "wmav2", "ac3", "eac3",
        "amr_nb", "amr_wb", "atrac3", "atrac3p", "musepack7", "musepack8",
    )
    private val lossless = setOf("alac", "flac", "wavpack", "ape", "tta", "tak", "mlp", "truehd", "shorten", "wmalossless")

    fun isLossy(codec: String): Boolean = codec.lowercase() in lossy

    fun isLossless(codec: String): Boolean {
        val c = codec.lowercase()
        return c in lossless || c.startsWith("pcm_")
    }

    fun displayName(codec: String): String = when (val c = codec.lowercase()) {
        "alac" -> "ALAC"
        "ac3" -> "Dolby Digital"
        "eac3" -> "Dolby Digital Plus"
        "truehd" -> "Dolby TrueHD"
        "flac" -> "FLAC"
        "mp3" -> "MP3"
        "aac" -> "AAC"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        else -> if (c.startsWith("pcm_")) "PCM" else codec.uppercase()
    }
}

data class ChannelStats(val peakDb: Double, val rmsDb: Double) {
    /** Peak minus RMS, matching the "DR" shown per channel. */
    val dynamicRangeDb: Double get() = abs(peakDb - rmsDb)
}

data class LevelStats(
    val peakDb: Double,
    val rmsDb: Double,
    val lufs: Double?,
    val truePeakDbtp: Double,
    val clippedSamples: Long,
    val framesPerChannel: Long,
    val effectiveBitDepth: Int?,
    val channels: List<ChannelStats>,
    /** Standard "DR" meter value (3 s blocks, top 20% loudest), averaged over channels. */
    val drTt: Double? = null,
    /** Mean sample value per channel as a fraction of full scale. */
    val dcOffsets: List<Double> = emptyList(),
    /** Pearson correlation of the first two channels, -1..1; null unless stereo or wider. */
    val correlation: Double? = null,
    /** Left RMS minus right RMS in dB (positive = left louder); null unless stereo or wider. */
    val balanceDb: Double? = null,
    val timeline: Timeline? = null,
) {
    val dynamicRangeDb: Double get() = abs(peakDb - rmsDb)
}

enum class Verdict {
    GENUINE,
    LOSSY_SOURCE,
    POSSIBLE_LOSSY_SOURCE,
    UPSAMPLED,
    BAND_LIMITED,
    LOSSY_CODEC,
    INCONCLUSIVE,
}

data class AuthenticityReport(
    val verdict: Verdict,
    val cutoffHz: Double,
    val hasCliff: Boolean,
    val cliffDropDb: Double,
    val note: String,
    /** 0..100, how sure the verdict is. */
    val confidence: Int = 0,
    /** Cutoff per equal slice of the track in Hz; 0 where no cliff was found. */
    val segmentCutoffs: FloatArray = FloatArray(0),
    /** Likely encoder / bitrate class matching the cutoff, when the file looks like a transcode. */
    val encoderGuess: String? = null,
    /** Frequency above which the spectrum is empty (at the noise floor); null if content reaches the top. */
    val emptyAboveHz: Double? = null,
)

/** Coarse time series (equal slices of the track) for the dynamics graph. */
class Timeline(
    /** Loudness per slice in LUFS (floored at -70). */
    val loudness: FloatArray,
    /** Sample peak per slice in dBFS. */
    val peakDb: FloatArray,
    /** Clipped samples per slice. */
    val clips: IntArray,
    val durationSec: Double,
) {
    val points: Int get() = loudness.size
}

/**
 * Quantised spectrogram. Each view holds [columns] x [bins] bytes (column-major),
 * where 0..255 maps linearly to MIN_DB..MAX_DB.
 */
class Spectrogram(
    val columns: Int,
    val bins: Int,
    val sampleRate: Int,
    val fftSize: Int,
    val durationSec: Double,
    val views: List<ByteArray>,
    val viewLabels: List<String>,
) {
    companion object {
        const val MIN_DB = -120.0
        const val MAX_DB = 0.0
    }
}
