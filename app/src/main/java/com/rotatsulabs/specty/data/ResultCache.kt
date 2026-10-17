package com.rotatsulabs.specty.data

import android.content.Context
import android.net.Uri
import com.rotatsulabs.specty.audio.AnalysisResult
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.audio.WindowFn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Persists finished analyses so a file is only measured once. Entries are keyed by uri, file size, modified time,
 * spectrogram settings and the analysis version, so changing the file, a setting or the algorithm triggers a fresh
 * analysis. Results saved by another version are never read.
 */
class ResultCache private constructor(context: Context) {
    private val dir = File(context.filesDir, "results").apply { mkdirs() }
    private val indexFile = File(dir, "index.txt")
    private val lock = Any()
    private val index = LinkedHashMap<String, Verdict>()

    private val _verdicts = MutableStateFlow<Map<String, Verdict>>(emptyMap())
    /** Verdict per cache key, for badges in the library. */
    val verdicts: StateFlow<Map<String, Verdict>> = _verdicts.asStateFlow()

    init {
        runCatching {
            val lines = if (indexFile.exists()) indexFile.readLines() else emptyList()
            if (CacheVersion.isCurrentIndex(lines.firstOrNull())) {
                for (line in lines.drop(1)) {
                    val parts = line.split('\t')
                    // Entries stay even when their bulky result file was evicted: the verdict is still known.
                    if (parts.size == 2) {
                        Verdict.entries.firstOrNull { it.name == parts[1] }?.let { index[parts[0]] = it }
                    }
                }
            } else {
                // Saved by another format or analyzer version: its verdicts and results would be stale.
                dir.listFiles()?.forEach { it.delete() }
            }
        }
        _verdicts.value = index.toMap()
    }

    /** True when a verdict is known for [key], even if the full result has been evicted. */
    fun contains(key: String): Boolean = synchronized(lock) { index.containsKey(key) }

    /** Names the file, the settings it was read with and the analysis version; see [CacheKey]. */
    fun keyOf(uri: Uri, sizeBytes: Long, modified: Long, fftSize: Int, window: WindowFn = WindowFn.Hann): String =
        CacheKey.of(uri.toString(), sizeBytes, modified, fftSize, window)

    fun load(key: String, uri: Uri): AnalysisResult? {
        val file = File(dir, "$key.bin")
        if (!file.exists()) return null
        return try {
            DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { ResultCodec.read(it) }
                .let { AnalysisResult(uri, it.info, it.levels, it.authenticity, it.spectrogram, it.decoded) }
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun save(key: String, result: AnalysisResult) {
        runCatching {
            val file = File(dir, "$key.bin")
            val tmp = File(dir, "$key.tmp")
            val stored = StoredResult(result.info, result.levels, result.authenticity, result.spectrogram, result.decoded)
            DataOutputStream(GZIPOutputStream(tmp.outputStream().buffered())).use { ResultCodec.write(it, stored) }
            if (!tmp.renameTo(file)) { tmp.delete(); return }
            synchronized(lock) {
                index.remove(key)
                index[key] = result.authenticity.verdict
                while (index.size > MAX_ENTRIES) {
                    val oldest = index.keys.first()
                    index.remove(oldest)
                    File(dir, "$oldest.bin").delete()
                }
                trimToBudget()
                persistIndex()
            }
        }
    }

    /** Deletes the oldest result files (not their verdicts) until the folder fits the size budget. */
    private fun trimToBudget() {
        val files = dir.listFiles { f -> f.extension == "bin" } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (key in index.keys.toList()) {
            val f = File(dir, "$key.bin")
            if (!f.exists()) continue
            total -= f.length()
            f.delete()
            if (total <= MAX_BYTES) break
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
        indexFile.writeText((listOf(CacheVersion.indexHeader) + index.entries.map { "${it.key}\t${it.value.name}" }).joinToString("\n"))
        _verdicts.value = index.toMap()
    }

    companion object {
        /** Verdicts remembered for library badges. */
        private const val MAX_ENTRIES = 20_000
        /** Disk budget for full results (spectrograms dominate). */
        private const val MAX_BYTES = 600L * 1024 * 1024

        @Volatile private var instance: ResultCache? = null

        /** One cache per process, shared by the UI and the background batch scan. */
        fun get(context: Context): ResultCache =
            instance ?: synchronized(this) { instance ?: ResultCache(context.applicationContext).also { instance = it } }
    }
}
