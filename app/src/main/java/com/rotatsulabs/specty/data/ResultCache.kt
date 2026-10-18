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
    private val lock = Any()
    private val index = VerdictIndex(File(dir, "index.txt"), CacheVersion.indexHeader, MAX_ENTRIES)
    /** Size of each saved result file, so the disk budget is checked without touching the folder. */
    private val sizes = HashMap<String, Long>()
    private var totalBytes = 0L
    private var unpublished = 0
    private var lastPublish = 0L

    private val _verdicts = MutableStateFlow<Map<String, Verdict>>(emptyMap())
    /** Verdict per cache key, for badges in the library. */
    val verdicts: StateFlow<Map<String, Verdict>> = _verdicts.asStateFlow()

    init {
        runCatching {
            if (!index.load()) {
                // Saved by another format or analyzer version: its verdicts and results would be stale.
                dir.listFiles()?.filter { it.name != "index.txt" }?.forEach { it.delete() }
            }
            dir.listFiles()?.forEach { f ->
                if (f.extension == "bin") {
                    sizes[f.nameWithoutExtension] = f.length()
                    totalBytes += f.length()
                } else if (f.extension == "tmp") {
                    f.delete()
                }
            }
        }
        _verdicts.value = index.snapshot()
    }

    /** True when a verdict is known for [key], even if the full result has been evicted. */
    fun contains(key: String): Boolean = synchronized(lock) { key in index }

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
            synchronized(lock) { sizes.remove(key)?.let { totalBytes -= it } }
            null
        }
    }

    /**
     * Saves [result]. The library's badge list is told right away unless [publish] is false, which a long scan uses
     * to announce every few results with [publishNow] instead of rebuilding the list for each one.
     */
    fun save(key: String, result: AnalysisResult, publish: Boolean = true) {
        val file = File(dir, "$key.bin")
        val tmp = File(dir, "$key.tmp")
        try {
            val stored = StoredResult(result.info, result.levels, result.authenticity, result.spectrogram, result.decoded)
            DataOutputStream(GZIPOutputStream(tmp.outputStream().buffered())).use { ResultCodec.write(it, stored) }
            if (!tmp.renameTo(file)) {
                tmp.delete()
                return
            }
        } catch (_: Exception) {
            tmp.delete()
            return
        }
        synchronized(lock) {
            totalBytes += file.length() - (sizes.put(key, file.length()) ?: 0L)
            for (gone in index.put(key, result.authenticity.verdict)) {
                File(dir, "$gone.bin").delete()
                sizes.remove(gone)?.let { totalBytes -= it }
            }
            trimToBudget()
            unpublished++
            if (publish || unpublished >= PUBLISH_EVERY) publishLocked()
        }
    }

    /** Announces verdicts saved since the last announcement. */
    fun publishNow() = synchronized(lock) { if (unpublished > 0) publishLocked() }

    private fun publishLocked() {
        unpublished = 0
        _verdicts.value = index.snapshot()
    }

    /** Deletes the oldest result files (not their verdicts) once over budget, down to 90% so it does not repeat. */
    private fun trimToBudget() {
        if (totalBytes <= MAX_BYTES) return
        val target = MAX_BYTES / 10 * 9
        for (key in index.keysOldestFirst()) {
            if (totalBytes <= target) break
            val size = sizes.remove(key) ?: continue
            File(dir, "$key.bin").delete()
            totalBytes -= size
        }
    }

    fun clear() {
        synchronized(lock) {
            dir.listFiles()?.filter { it.name != "index.txt" }?.forEach { it.delete() }
            index.clear()
            sizes.clear()
            totalBytes = 0L
            unpublished = 0
            _verdicts.value = emptyMap()
        }
    }

    companion object {
        /** Verdicts remembered for library badges. */
        private const val MAX_ENTRIES = 20_000
        /** Disk budget for full results (spectrograms dominate). */
        private const val MAX_BYTES = 600L * 1024 * 1024
        /** During a scan the badge list is rebuilt once per this many saved results. */
        private const val PUBLISH_EVERY = 25

        @Volatile private var instance: ResultCache? = null

        /** One cache per process, shared by the UI and the background batch scan. */
        fun get(context: Context): ResultCache =
            instance ?: synchronized(this) { instance ?: ResultCache(context.applicationContext).also { instance = it } }
    }
}
