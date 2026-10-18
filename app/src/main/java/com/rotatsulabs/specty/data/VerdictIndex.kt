package com.rotatsulabs.specty.data

import com.rotatsulabs.specty.audio.Verdict
import java.io.File

/**
 * Which verdict is saved for which result key, oldest first. Every change is one appended line, so saving a result
 * costs the same with 20,000 entries as with none; the file is rewritten only to drop old entries or dead lines.
 * A line cut short by a crash or reboot is ignored on the next load.
 */
class VerdictIndex(
    private val file: File,
    private val header: String,
    private val maxEntries: Int = 20_000,
) {
    private val map = LinkedHashMap<String, Verdict>()
    private var lines = 0

    val size: Int get() = map.size

    operator fun contains(key: String): Boolean = map.containsKey(key)

    fun keysOldestFirst(): List<String> = map.keys.toList()

    fun snapshot(): Map<String, Verdict> = LinkedHashMap(map)

    /** Reads the file. False when it was written by another version, which leaves the index empty and reset. */
    fun load(): Boolean {
        map.clear()
        lines = 0
        val raw = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
        if (raw.isEmpty()) return true
        val text = raw.lines().let { if (raw.endsWith("\n")) it.dropLast(1) else it }
        // A write cut off by a crash leaves no final newline. Rewrite, or the next line would be glued onto it.
        val torn = !raw.endsWith("\n")
        if (text.first() != header) {
            rewrite()
            return false
        }
        for (line in text.drop(1)) {
            lines++
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val verdict = Verdict.entries.firstOrNull { it.name == line.substring(tab + 1) } ?: continue
            val key = line.substring(0, tab)
            map.remove(key)
            map[key] = verdict
        }
        if (torn || lines > map.size * 2 + COMPACT_SLACK) rewrite()
        return true
    }

    /** Records [verdict] for [key] and returns the keys dropped to stay within the limit. */
    fun put(key: String, verdict: Verdict): List<String> {
        map.remove(key)
        map[key] = verdict
        val dropped = ArrayList<String>()
        if (map.size > maxEntries) {
            val iterator = map.keys.iterator()
            while (map.size > maxEntries && iterator.hasNext()) {
                dropped += iterator.next()
                iterator.remove()
            }
        }
        if (dropped.isNotEmpty() || lines > map.size * 2 + COMPACT_SLACK) {
            rewrite()
        } else {
            runCatching {
                if (!file.exists() || file.length() == 0L) file.writeText(header + "\n")
                file.appendText("$key\t${verdict.name}\n")
                lines++
            }
        }
        return dropped
    }

    fun clear() {
        map.clear()
        rewrite()
    }

    private fun rewrite() {
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.bufferedWriter().use { out ->
                out.write(header)
                out.write("\n")
                for ((key, verdict) in map) {
                    out.write(key)
                    out.write("\t")
                    out.write(verdict.name)
                    out.write("\n")
                }
            }
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
            lines = map.size
        }
    }

    private companion object {
        const val COMPACT_SLACK = 1000
    }
}
