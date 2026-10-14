package com.rotatsulabs.specty.data

import android.content.Context
import android.net.Uri
import com.rotatsulabs.specty.audio.AnalysisResult
import com.rotatsulabs.specty.audio.AudioInfo
import com.rotatsulabs.specty.audio.AuthenticityReport
import com.rotatsulabs.specty.audio.ChannelStats
import com.rotatsulabs.specty.audio.LevelStats
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.audio.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Persists finished analyses so a file is only measured once. Entries are keyed by uri, file size and
 * spectrogram window, so changing the file or the resolution setting triggers a fresh analysis.
 */
class ResultCache(context: Context) {
    private val dir = File(context.filesDir, "results").apply { mkdirs() }
    private val indexFile = File(dir, "index.txt")
    private val lock = Any()
    private val index = LinkedHashMap<String, Verdict>()

    private val _verdicts = MutableStateFlow<Map<String, Verdict>>(emptyMap())
    /** Verdict per cache key, for badges in the library. */
    val verdicts: StateFlow<Map<String, Verdict>> = _verdicts.asStateFlow()

    init {
        runCatching {
            if (indexFile.exists()) {
                indexFile.forEachLine { line ->
                    val parts = line.split('\t')
                    if (parts.size == 2 && File(dir, "${parts[0]}.bin").exists()) {
                        Verdict.entries.firstOrNull { it.name == parts[1] }?.let { index[parts[0]] = it }
                    }
                }
            }
        }
        _verdicts.value = index.toMap()
    }

    fun keyOf(uri: Uri, sizeBytes: Long, fftSize: Int): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$uri|$sizeBytes|$fftSize".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun load(key: String, uri: Uri): AnalysisResult? {
        val file = File(dir, "$key.bin")
        if (!file.exists()) return null
        return try {
            DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { read(it, uri) }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun save(key: String, result: AnalysisResult) {
        runCatching {
            val file = File(dir, "$key.bin")
            val tmp = File(dir, "$key.tmp")
            DataOutputStream(GZIPOutputStream(tmp.outputStream().buffered())).use { write(it, result) }
            if (!tmp.renameTo(file)) { tmp.delete(); return }
            synchronized(lock) {
                index.remove(key)
                index[key] = result.authenticity.verdict
                while (index.size > MAX_ENTRIES) {
                    val oldest = index.keys.first()
                    index.remove(oldest)
                    File(dir, "$oldest.bin").delete()
                }
                persistIndex()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            dir.listFiles()?.forEach { it.delete() }
            index.clear()
            _verdicts.value = emptyMap()
        }
    }

    private fun persistIndex() {
        indexFile.writeText(index.entries.joinToString("\n") { "${it.key}\t${it.value.name}" })
        _verdicts.value = index.toMap()
    }

    // ---- binary format ---------------------------------------------------------------------

    private fun write(out: DataOutputStream, r: AnalysisResult) {
        out.writeInt(MAGIC)
        with(r.info) {
            out.writeUTF(displayName); out.writeUTF(codec); writeStr(out, container)
            out.writeInt(sampleRate); out.writeInt(channels)
            out.writeInt(bitDepth ?: -1); writeStr(out, sampleFormat)
            out.writeLong(bitrateBps ?: -1L); out.writeDouble(durationSec); out.writeLong(sizeBytes)
            out.writeInt(tags.size)
            tags.forEach { out.writeUTF(it.first); writeLong(out, it.second) }
        }
        with(r.levels) {
            out.writeDouble(peakDb); out.writeDouble(rmsDb)
            out.writeBoolean(lufs != null); out.writeDouble(lufs ?: 0.0)
            out.writeDouble(truePeakDbtp); out.writeLong(clippedSamples); out.writeLong(framesPerChannel)
            out.writeInt(effectiveBitDepth ?: -1)
            out.writeInt(channels.size)
            channels.forEach { out.writeDouble(it.peakDb); out.writeDouble(it.rmsDb) }
        }
        with(r.authenticity) {
            out.writeUTF(verdict.name); out.writeDouble(cutoffHz); out.writeBoolean(hasCliff)
            out.writeDouble(cliffDropDb); out.writeUTF(note)
        }
        with(r.spectrogram) {
            out.writeInt(columns); out.writeInt(bins); out.writeInt(sampleRate); out.writeInt(fftSize)
            out.writeDouble(durationSec)
            out.writeInt(views.size)
            views.forEach { out.writeInt(it.size); out.write(it) }
            viewLabels.forEach { out.writeUTF(it) }
        }
    }

    private fun read(inp: DataInputStream, uri: Uri): AnalysisResult {
        require(inp.readInt() == MAGIC)
        val info = AudioInfo(
            displayName = inp.readUTF(), codec = inp.readUTF(), container = readStr(inp),
            sampleRate = inp.readInt(), channels = inp.readInt(),
            bitDepth = inp.readInt().takeIf { it >= 0 }, sampleFormat = readStr(inp),
            bitrateBps = inp.readLong().takeIf { it >= 0 }, durationSec = inp.readDouble(), sizeBytes = inp.readLong(),
            tags = List(inp.readInt()) { inp.readUTF() to readLong(inp) },
        )
        val peak = inp.readDouble(); val rms = inp.readDouble()
        val lufs = inp.readBoolean().let { has -> inp.readDouble().takeIf { has } }
        val truePeak = inp.readDouble(); val clipped = inp.readLong(); val frames = inp.readLong()
        val effective = inp.readInt().takeIf { it >= 0 }
        val channels = List(inp.readInt()) { ChannelStats(inp.readDouble(), inp.readDouble()) }
        val levels = LevelStats(peak, rms, lufs, truePeak, clipped, frames, effective, channels)
        val authenticity = AuthenticityReport(
            verdict = Verdict.valueOf(inp.readUTF()), cutoffHz = inp.readDouble(), hasCliff = inp.readBoolean(),
            cliffDropDb = inp.readDouble(), note = inp.readUTF(),
        )
        val columns = inp.readInt(); val bins = inp.readInt(); val rate = inp.readInt(); val fft = inp.readInt()
        val duration = inp.readDouble()
        val views = List(inp.readInt()) { ByteArray(inp.readInt()).also { inp.readFully(it) } }
        val labels = List(views.size) { inp.readUTF() }
        return AnalysisResult(uri, info, levels, authenticity, Spectrogram(columns, bins, rate, fft, duration, views, labels))
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

    private companion object {
        const val MAGIC = 0x53504332 // "SPC2"
        const val MAX_ENTRIES = 80
    }
}
