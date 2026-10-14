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
) {
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
)

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
