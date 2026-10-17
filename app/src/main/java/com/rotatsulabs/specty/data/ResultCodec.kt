package com.rotatsulabs.specty.data

import com.rotatsulabs.specty.audio.AnalysisVersion
import com.rotatsulabs.specty.audio.AudioInfo
import com.rotatsulabs.specty.audio.AuthenticityReport
import com.rotatsulabs.specty.audio.BitDepthBasis
import com.rotatsulabs.specty.audio.ChannelStats
import com.rotatsulabs.specty.audio.DecodedFormat
import com.rotatsulabs.specty.audio.LevelStats
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.audio.SpectrumShape
import com.rotatsulabs.specty.audio.Timeline
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.audio.WindowFn
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest

/** Everything saved for one analysed file, apart from the uri it was read from. */
class StoredResult(
    val info: AudioInfo,
    val levels: LevelStats,
    val authenticity: AuthenticityReport,
    val spectrogram: Spectrogram,
    val decoded: DecodedFormat?,
)

/** What makes a saved result usable. A change to the layout or to any measuring rule makes older results unusable. */
object CacheVersion {
    /** "SPC" plus the schema number. */
    const val MAGIC = 0x53504300 or AnalysisVersion.SCHEMA

    /** First line of the index file; a different one means everything saved was made by another version. */
    val indexHeader: String get() = "v" + Integer.toHexString(MAGIC) + "." + AnalysisVersion.ALGORITHM

    fun isCurrentIndex(firstLine: String?): Boolean = firstLine == indexHeader
}

/** Cache keys name the file, how it was read and the version of the analysis, so a new version never finds an old result. */
object CacheKey {
    private val HEX = "0123456789abcdef".toCharArray()

    /** The default Hann window adds nothing to the key; any other window does. */
    fun of(uri: String, sizeBytes: Long, modified: Long, fftSize: Int, window: WindowFn = WindowFn.Hann): String {
        val suffix = if (window == WindowFn.Hann) "" else "|w=${window.name}"
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$uri|$sizeBytes|$modified|$fftSize$suffix|${AnalysisVersion.tag}".toByteArray())
        // Called once per track on every library refresh, so avoid String.format here.
        val hex = CharArray(digest.size * 2)
        digest.forEachIndexed { i, b ->
            hex[i * 2] = HEX[(b.toInt() shr 4) and 0xF]
            hex[i * 2 + 1] = HEX[b.toInt() and 0xF]
        }
        return String(hex)
    }
}

/** Binary form of a [StoredResult]. Reading refuses anything written by another schema or algorithm version. */
object ResultCodec {
    fun toBytes(r: StoredResult): ByteArray =
        ByteArrayOutputStream().also { DataOutputStream(it).use { out -> write(out, r) } }.toByteArray()

    /** Null for a corrupt, truncated or out-of-date result. */
    fun fromBytes(bytes: ByteArray): StoredResult? =
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { read(it) }
        } catch (_: Exception) {
            null
        }

    fun write(out: DataOutputStream, r: StoredResult) {
        out.writeInt(CacheVersion.MAGIC)
        out.writeInt(AnalysisVersion.ALGORITHM)
        with(r.info) {
            out.writeUTF(displayName); out.writeUTF(codec); writeStr(out, container)
            out.writeInt(sampleRate); out.writeInt(channels)
            out.writeInt(bitDepth ?: -1); writeStr(out, sampleFormat)
            out.writeLong(bitrateBps ?: -1L); out.writeDouble(durationSec); out.writeLong(sizeBytes)
            out.writeInt(tags.size)
            tags.forEach { out.writeUTF(it.first); writeLong(out, it.second) }
            writeStr(out, profile)
        }
        with(r.levels) {
            out.writeDouble(peakDb); out.writeDouble(rmsDb)
            out.writeBoolean(lufs != null); out.writeDouble(lufs ?: 0.0)
            out.writeDouble(truePeakDbtp); out.writeLong(clippedSamples); out.writeLong(framesPerChannel)
            out.writeInt(effectiveBitDepth ?: -1); out.writeUTF(bitDepthBasis.name)
            out.writeInt(channels.size)
            channels.forEach { out.writeDouble(it.peakDb); out.writeDouble(it.rmsDb) }
            out.writeDouble(drTt ?: Double.NaN)
            out.writeInt(dcOffsets.size); dcOffsets.forEach { out.writeDouble(it) }
            out.writeDouble(correlation ?: Double.NaN)
            out.writeDouble(balanceDb ?: Double.NaN)
            val t = timeline
            out.writeInt(t?.points ?: 0)
            if (t != null) {
                out.writeDouble(t.durationSec)
                t.loudness.forEach { out.writeFloat(it) }
                t.peakDb.forEach { out.writeFloat(it) }
                t.clips.forEach { out.writeInt(it) }
            }
        }
        with(r.authenticity) {
            out.writeUTF(verdict.name); out.writeDouble(cutoffHz ?: Double.NaN)
            out.writeDouble(cliffDropDb); out.writeUTF(note)
            out.writeInt(confidence)
            out.writeInt(segmentCutoffs.size); segmentCutoffs.forEach { out.writeFloat(it) }
            writeStr(out, encoderGuess)
            out.writeDouble(emptyAboveHz ?: Double.NaN)
            out.writeUTF(shape.name); out.writeInt(sectionsJudged); out.writeInt(sectionsAgreeing)
        }
        out.writeBoolean(r.decoded != null)
        r.decoded?.let { out.writeInt(it.sampleRate); out.writeInt(it.channels) }
        with(r.spectrogram) {
            out.writeInt(columns); out.writeInt(bins); out.writeInt(sampleRate); out.writeInt(fftSize)
            out.writeDouble(durationSec)
            out.writeInt(views.size)
            views.forEach { out.writeInt(it.size); out.write(it) }
            viewLabels.forEach { out.writeUTF(it) }
        }
    }

    fun read(inp: DataInputStream): StoredResult {
        if (inp.readInt() != CacheVersion.MAGIC) throw IOException("Saved by another format")
        if (inp.readInt() != AnalysisVersion.ALGORITHM) throw IOException("Saved by another analysis version")
        val info = AudioInfo(
            displayName = inp.readUTF(), codec = inp.readUTF(), container = readStr(inp),
            sampleRate = inp.readInt(), channels = inp.readInt(),
            bitDepth = inp.readInt().takeIf { it >= 0 }, sampleFormat = readStr(inp),
            bitrateBps = inp.readLong().takeIf { it >= 0 }, durationSec = inp.readDouble(), sizeBytes = inp.readLong(),
            tags = List(inp.readInt()) { inp.readUTF() to readLong(inp) },
            profile = readStr(inp),
        )
        val peak = inp.readDouble(); val rms = inp.readDouble()
        val lufs = inp.readBoolean().let { has -> inp.readDouble().takeIf { has } }
        val truePeak = inp.readDouble(); val clipped = inp.readLong(); val frames = inp.readLong()
        val effective = inp.readInt().takeIf { it >= 0 }
        val basis = BitDepthBasis.valueOf(inp.readUTF())
        val channels = List(inp.readInt()) { ChannelStats(inp.readDouble(), inp.readDouble()) }
        val dr = inp.readDouble().takeUnless { it.isNaN() }
        val dc = List(inp.readInt()) { inp.readDouble() }
        val corr = inp.readDouble().takeUnless { it.isNaN() }
        val balance = inp.readDouble().takeUnless { it.isNaN() }
        val points = inp.readInt()
        val timeline = if (points > 0) {
            val duration = inp.readDouble()
            val loud = FloatArray(points) { inp.readFloat() }
            val peaks = FloatArray(points) { inp.readFloat() }
            val clips = IntArray(points) { inp.readInt() }
            Timeline(loud, peaks, clips, duration)
        } else {
            null
        }
        val levels = LevelStats(peak, rms, lufs, truePeak, clipped, frames, effective, channels, dr, dc, corr, balance, timeline, basis)
        val authenticity = AuthenticityReport(
            verdict = Verdict.valueOf(inp.readUTF()), cutoffHz = inp.readDouble().takeUnless { it.isNaN() },
            cliffDropDb = inp.readDouble(), note = inp.readUTF(),
            confidence = inp.readInt(),
            segmentCutoffs = FloatArray(inp.readInt()).also { for (i in it.indices) it[i] = inp.readFloat() },
            encoderGuess = readStr(inp),
            emptyAboveHz = inp.readDouble().takeUnless { it.isNaN() },
            shape = SpectrumShape.valueOf(inp.readUTF()),
            sectionsJudged = inp.readInt(), sectionsAgreeing = inp.readInt(),
        )
        val decoded = if (inp.readBoolean()) DecodedFormat(inp.readInt(), inp.readInt()) else null
        val columns = inp.readInt(); val bins = inp.readInt(); val rate = inp.readInt(); val fft = inp.readInt()
        val duration = inp.readDouble()
        val views = List(inp.readInt()) { ByteArray(inp.readInt()).also { inp.readFully(it) } }
        val labels = List(views.size) { inp.readUTF() }
        return StoredResult(info, levels, authenticity, Spectrogram(columns, bins, rate, fft, duration, views, labels), decoded)
    }

    // writeUTF is limited to 64 KB; tag values are capped well below that, but encode as bytes to be safe.
    private fun writeLong(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readLong(inp: DataInputStream): String {
        val bytes = ByteArray(inp.readInt())
        inp.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun writeStr(out: DataOutputStream, s: String?) {
        out.writeBoolean(s != null); out.writeUTF(s ?: "")
    }

    private fun readStr(inp: DataInputStream): String? = inp.readBoolean().let { has -> inp.readUTF().takeIf { has } }
}
