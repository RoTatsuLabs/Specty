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
    /** The song title from the tags, or the file name without its extension when the file carries none. */
    val title: String
        get() = tags.firstOrNull { it.first.equals("title", ignoreCase = true) }?.second?.trim()?.takeIf { it.isNotEmpty() }
            ?: displayName.substringBeforeLast('.', displayName)

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
        "mp3", "mp2", "aac", "opus", "vorbis", "wmav1", "wmav2", "ac3", "eac3", "ac4",
        "amr_nb", "amr_wb", "atrac3", "atrac3p", "musepack7", "musepack8",
    )
    /** Codecs FFmpeg cannot decode; they are tried with the device's own decoders instead. */
    private val platformOnly = setOf("ac4")
    private val lossless = setOf("alac", "flac", "wavpack", "ape", "tta", "tak", "mlp", "truehd", "shorten", "wmalossless")

    fun isLossy(codec: String): Boolean = codec.lowercase() in lossy

    fun needsPlatformDecoder(codec: String): Boolean = codec.lowercase() in platformOnly

    fun isLossless(codec: String): Boolean {
        val c = codec.lowercase()
        return c in lossless || c.startsWith("pcm_")
    }

    fun displayName(codec: String): String = when (val c = codec.lowercase()) {
        "alac" -> "ALAC"
        "ac3" -> "Dolby Digital"
        "eac3" -> "Dolby Digital Plus"
        "ac4" -> "Dolby AC-4"
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
    /** How [effectiveBitDepth] was established from the decoded samples. */
    val bitDepthBasis: BitDepthBasis = BitDepthBasis.UNKNOWN,
) {
    val dynamicRangeDb: Double get() = abs(peakDb - rmsDb)
}

/** What the decoded PCM samples reveal about the real resolution of the signal. */
enum class BitDepthBasis {
    /** Too few non-zero samples to say anything. */
    UNKNOWN,
    /** The low bits of the decoded samples are in use. This shows precision, not that the file was recorded at that depth. */
    ALL_BITS,
    /** The low bits are always zero: lower-bit-depth audio stored in a wider format. */
    ZERO_PADDED,
    /** Samples sit on a coarse grid that is not a power of two, e.g. 16-bit audio after a gain change. */
    LEVEL_GRID,
}

/** Reading the effective depth against what the file declares, shared by the verdict and the screens. */
object BitDepth {
    /** A float32 decode keeps 24 bits, so wider integer formats can never show more than that. */
    fun capacity(declared: Int?): Int? = declared?.let { minOf(it, 24) }

    /** True when the samples carry at least 4 bits fewer than the format can hold. */
    fun isPadded(declared: Int?, effective: Int?): Boolean {
        val cap = capacity(declared) ?: return false
        return effective != null && effective <= cap - 4
    }

    /** The verdict note for padded audio, or nothing when the depth is fine. */
    fun paddingNote(declared: Int?, effective: Int?): List<Note> =
        if (isPadded(declared, effective)) listOf(Note(NoteKey.BITS_PADDED, effective!!, declared!!)) else emptyList()

    /** Value for the "Effective depth" stat. */
    fun label(declared: Int?, effective: Int, basis: BitDepthBasis): String = when {
        isPadded(declared, effective) -> "$effective-bit data in $declared-bit file"
        basis == BitDepthBasis.ALL_BITS -> "Uses all ${capacity(declared) ?: effective} bits"
        else -> "$effective-bit"
    }
}

/** The PCM that was actually analysed: always 32-bit float, at the rate and layout the decoder produced. */
data class DecodedFormat(val sampleRate: Int, val channels: Int) {
    val summary: String
        get() = "32-bit float, ${String.format(java.util.Locale.US, "%.1f", sampleRate / 1000.0)} kHz, " +
            if (channels == 1) "mono" else if (channels == 2) "stereo" else "$channels ch"

    companion object {
        private val OUTPUT = Regex("""Output #0.*?Audio: pcm_f32le, (\d+) Hz, ([^,\n]+)""", RegexOption.DOT_MATCHES_ALL)
        private val COUNT = Regex("""(\d+) channels""")
        private val LAYOUT = Regex("""(\d+)\.(\d+)""")

        /** Reads rate and layout of the output stream from FFmpeg's log, or null when the log does not show them. */
        fun parse(log: String?): DecodedFormat? {
            val m = OUTPUT.find(log ?: return null) ?: return null
            val rate = m.groupValues[1].toIntOrNull() ?: return null
            val channels = channelsOf(m.groupValues[2].trim()) ?: return null
            return if (rate > 0 && channels > 0) DecodedFormat(rate, channels) else null
        }

        private fun channelsOf(layout: String): Int? {
            val l = layout.lowercase()
            COUNT.find(l)?.let { return it.groupValues[1].toIntOrNull() }
            LAYOUT.find(l)?.let { return it.groupValues[1].toInt() + it.groupValues[2].toInt() }
            return when {
                l == "mono" -> 1
                l == "stereo" -> 2
                l.startsWith("quad") -> 4
                else -> null
            }
        }
    }
}

/** Shape of the long-term spectrum towards its top end. */
enum class SpectrumShape {
    /** Content reaches the top of the band. */
    FULL_BAND,
    /** A steep, permanent drop: a brick-wall low-pass. */
    ABRUPT_CUTOFF,
    /** The top of the band is empty without a sharp edge. */
    GRADUAL_ROLLOFF,
    /** The top is faint, so noise and quiet content cannot be told apart. */
    UNCLEAR,
    /** Too little audible content to look at the spectrum. */
    NO_SIGNAL,
}

enum class Verdict {
    /** Full-band lossless audio at 48 kHz or below. */
    GENUINE,
    /** Full-band lossless audio above 48 kHz. */
    HI_RES,
    LOSSY_SOURCE,
    POSSIBLE_LOSSY_SOURCE,
    UPSAMPLED,
    BAND_LIMITED,
    LOSSY_CODEC,
    DOLBY_ATMOS,
    INCONCLUSIVE,
}

data class AuthenticityReport(
    val verdict: Verdict,
    /** The edge of an abrupt cutoff. Null when none was found: the Nyquist limit is never reported here. */
    val cutoffHz: Double?,
    val cliffDropDb: Double,
    /** Why the verdict was given, as data. The UI words them in the app language; [note] is the English text. */
    val notes: List<Note>,
    /** 0..100, how sure the verdict is. Zero when the verdict is inconclusive. */
    val confidence: Int = 0,
    /** Cutoff per equal slice of the track in Hz; 0 where no cliff was found. */
    val segmentCutoffs: FloatArray = FloatArray(0),
    /** Likely encoder / bitrate class matching the cutoff, when the file looks like a transcode. */
    val encoderGuess: String? = null,
    /** Frequency above which the spectrum is empty (at the noise floor); null if content reaches the top or it is unclear. */
    val emptyAboveHz: Double? = null,
    val shape: SpectrumShape = SpectrumShape.UNCLEAR,
    /** Sections of the track that could be judged, and how many of them agree with the overall shape. */
    val sectionsJudged: Int = 0,
    val sectionsAgreeing: Int = 0,
) {
    val hasCliff: Boolean get() = cutoffHz != null

    /** English text of [notes], for the report export. */
    val note: String get() = Notes.english(notes)
}

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
    /** Tab titles: the first view is the mix, and channels carry speaker names for the common layouts. */
    val displayLabels: List<String>
        get() {
            val channels = views.size - 1
            if (channels < 1) return viewLabels
            val names = when (channels) {
                2 -> listOf("L", "R")
                3 -> listOf("L", "R", "C")
                4 -> listOf("FL", "FR", "BL", "BR")
                5 -> listOf("L", "R", "C", "Ls", "Rs")
                6 -> listOf("L", "R", "C", "LFE", "Ls", "Rs")
                8 -> listOf("L", "R", "C", "LFE", "Lb", "Rb", "Ls", "Rs")
                else -> List(channels) { "Ch ${it + 1}" }
            }
            return listOf("Mix") + names
        }

    companion object {
        const val MIN_DB = -120.0
        const val MAX_DB = 0.0
    }
}
