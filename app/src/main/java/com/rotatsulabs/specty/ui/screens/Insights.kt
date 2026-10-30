package com.rotatsulabs.specty.ui.screens

import com.rotatsulabs.specty.audio.Verdict

/** Copies of one song found in the library. [best] is the one to keep: the best result, then the biggest file. */
class DuplicateGroup<T>(val copies: List<T>, val best: T)

object Duplicates {
    /** Edition words that do not make a different song; other brackets, such as "(Live)" or "(Remix)", do. */
    private val EDITION = Regex("""[(\[][^)\]]*(remaster|explicit|deluxe|bonus|hi-?res)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

    /** A title or artist with edition marks, case, spacing and punctuation taken away, so two spellings match. */
    fun normalize(text: String): String =
        EDITION.replace(text, "").lowercase().filter { it.isLetterOrDigit() }

    /** Two copies are the same recording when their lengths differ by no more than this. */
    private fun toleranceMs(ms: Long): Long = maxOf(3_000L, ms * 3 / 100)

    /**
     * Songs that appear more than once: same normalised title and artist, and about the same length. A track with no
     * artist is never matched, since a bare title is shared by many songs. Groups come back A to Z by title.
     */
    fun <T> find(items: List<T>, f: TrackFields<T>, quality: (T) -> Int): List<DuplicateGroup<T>> {
        val buckets = LinkedHashMap<String, ArrayList<T>>()
        for (item in items) {
            val title = normalize(f.title(item))
            val artist = f.artist(item)?.let(::normalize).orEmpty()
            if (title.length < 2 || artist.isEmpty()) continue
            buckets.getOrPut("$artist|$title") { ArrayList() } += item
        }
        val out = ArrayList<DuplicateGroup<T>>()
        for (bucket in buckets.values) {
            if (bucket.size < 2) continue
            // Walk the copies by length and start a new group when one is too far from the group's first.
            var current = ArrayList<T>()
            var anchor = 0L
            for (item in bucket.sortedBy(f.durationMs)) {
                val ms = f.durationMs(item)
                if (current.isNotEmpty() && ms - anchor > toleranceMs(anchor)) {
                    if (current.size > 1) out += group(current, f, quality)
                    current = ArrayList()
                }
                if (current.isEmpty()) anchor = ms
                current += item
            }
            if (current.size > 1) out += group(current, f, quality)
        }
        return out.sortedBy { f.title(it.best).lowercase() }
    }

    private fun <T> group(copies: List<T>, f: TrackFields<T>, quality: (T) -> Int): DuplicateGroup<T> {
        val best = copies.maxWithOrNull(compareBy<T> { quality(it) }.thenBy { f.sizeBytes(it) })!!
        // Best first, then by quality, so the list reads from the copy to keep down to the ones to drop.
        val ordered = copies.sortedWith(compareByDescending<T> { quality(it) }.thenByDescending { f.sizeBytes(it) })
        return DuplicateGroup(ordered, best)
    }
}

/** A count of everything in the library: how much of it is checked, what the results are, and which formats. */
class LibraryReport(
    val total: Int,
    val checked: Int,
    val results: List<Pair<ResultFilter, Int>>,
    val formats: List<Pair<String, Int>>,
    val sizeBytes: Long,
    val durationMs: Long,
) {
    companion object {
        fun <T> build(items: List<T>, f: TrackFields<T>, verdictOf: (T) -> Verdict?): LibraryReport {
            val verdicts = items.map(verdictOf)
            val formats = items.mapNotNull { f.format(it).trim().uppercase().takeIf { name -> name.isNotEmpty() } }
                .groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key to it.value }
            return LibraryReport(
                total = items.size,
                checked = verdicts.count { it != null },
                results = ResultFilter.summary(verdicts),
                formats = formats,
                sizeBytes = items.sumOf(f.sizeBytes),
                durationMs = items.sumOf(f.durationMs),
            )
        }
    }
}
