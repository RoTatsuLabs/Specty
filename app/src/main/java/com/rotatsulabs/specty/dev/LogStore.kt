package com.rotatsulabs.specty.dev

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A size-capped text log: [file], plus one older generation ([file].1) kept when it fills up. */
class LogStore(private val dir: File, private val maxBytes: Long = 512 * 1024) {
    private val file get() = File(dir, "specty.log")
    private val old get() = File(dir, "specty.log.1")

    @Synchronized
    fun append(line: String) {
        dir.mkdirs()
        val bytes = (line + "\n").toByteArray()
        if (file.length() + bytes.size > maxBytes) {
            old.delete()
            file.renameTo(old)
        }
        file.appendBytes(bytes)
    }

    /** The newest [maxChars] characters, starting on a line boundary. */
    @Synchronized
    fun tail(maxChars: Int = 100_000): String {
        val all = (if (old.exists()) old.readText() else "") + (if (file.exists()) file.readText() else "")
        if (all.length <= maxChars) return all
        val cut = all.substring(all.length - maxChars)
        val nl = cut.indexOf('\n')
        return if (nl in 0 until cut.lastIndex) cut.substring(nl + 1) else cut
    }

    @Synchronized
    fun clear() {
        file.delete()
        old.delete()
    }

    @Synchronized
    fun sizeBytes(): Long = file.length() + old.length()

    companion object {
        fun format(epochMs: Long, level: Char, tag: String, msg: String, error: Throwable? = null): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(epochMs))
            val head = "$time $level/$tag: $msg"
            return if (error == null) head else head + "\n" + error.stackTraceToString().trimEnd()
        }
    }
}
