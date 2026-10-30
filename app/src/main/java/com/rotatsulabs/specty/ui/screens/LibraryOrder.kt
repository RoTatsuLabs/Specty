package com.rotatsulabs.specty.ui.screens

import android.content.SharedPreferences

enum class SortField { Title, Artist, Album, Duration, Size, Modified, Result }

enum class GroupField { None, Result, Format }

/** What the library lists: every track, or albums or artists that each open their own page. */
enum class BrowseKind { Tracks, Albums, Artists }

/** How to read a track's fields, so the ordering below can be tested without Android types. */
class TrackFields<T>(
    val title: (T) -> String,
    val artist: (T) -> String?,
    val album: (T) -> String?,
    val durationMs: (T) -> Long,
    val sizeBytes: (T) -> Long,
    val modifiedSec: (T) -> Long,
    val format: (T) -> String,
    /** Where the track's result sits in the order results are listed; lower comes first. */
    val resultRank: (T) -> Int,
)

/** One heading of a grouped library. [key] is the format, null when the track has none. */
class TrackGroup<T>(val id: String, val key: String?, val rank: Int, val items: List<T>)

object LibraryOrder {
    private const val LAST = "￿"

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /** Sorts [items]; a tie falls back to the title so the order never depends on how the library was read. */
    fun <T> sort(items: List<T>, field: SortField, ascending: Boolean, f: TrackFields<T>): List<T> {
        val byTitle = compareBy<T> { f.title(it).lowercase() }
        val order: Comparator<T> = when (field) {
            SortField.Title -> byTitle
            SortField.Artist -> compareBy<T> { f.artist(it).clean()?.lowercase() ?: LAST }.then(byTitle)
            SortField.Album -> compareBy<T> { f.album(it).clean()?.lowercase() ?: LAST }.then(byTitle)
            SortField.Duration -> compareBy<T> { f.durationMs(it) }.then(byTitle)
            SortField.Size -> compareBy<T> { f.sizeBytes(it) }.then(byTitle)
            SortField.Modified -> compareBy<T> { f.modifiedSec(it) }.then(byTitle)
            SortField.Result -> compareBy<T> { f.resultRank(it) }.then(byTitle)
        }
        val sorted = items.sortedWith(order)
        return if (ascending) sorted else sorted.reversed()
    }

    /**
     * Splits an already sorted list into headings, keeping each heading's tracks in that order. Headings run A to Z
     * with the unnamed one last, or by result order for [GroupField.Result].
     */
    fun <T> group(sorted: List<T>, by: GroupField, f: TrackFields<T>): List<TrackGroup<T>> {
        if (by == GroupField.None) return listOf(TrackGroup("all", null, 0, sorted))
        class Bucket(val id: String, val key: String?, val rank: Int, val sortKey: String) { val items = ArrayList<T>() }
        val buckets = LinkedHashMap<String, Bucket>()
        for (item in sorted) {
            val bucket = when (by) {
                GroupField.Result -> f.resultRank(item).let { r -> buckets.getOrPut("r$r") { Bucket("r$r", null, r, "") } }
                else -> {
                    val name = f.format(item).clean()
                    val id = if (name == null) "unknown" else "n" + name.lowercase()
                    buckets.getOrPut(id) { Bucket(id, name, 0, name?.lowercase() ?: LAST) }
                }
            }
            bucket.items += item
        }
        val ordered = if (by == GroupField.Result) buckets.values.sortedBy { it.rank } else buckets.values.sortedBy { it.sortKey }
        return ordered.map { TrackGroup(it.id, it.key, it.rank, it.items) }
    }
}

/** An album or an artist: the tracks it holds and what to call it. A null [key] is the tracks with no tag. */
class LibraryCollection<T>(
    val kind: BrowseKind,
    val key: String?,
    /** For an album, the artist when every track agrees on one; null otherwise. */
    val artist: String?,
    /** True for an album whose tracks name more than one artist. */
    val various: Boolean,
    val items: List<T>,
) {
    /** Stable across launches and searches, so a page can find its collection again. */
    val id: String get() = Browse.id(kind, key)
}

object Browse {
    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    fun id(kind: BrowseKind, key: String?): String = kind.name + ":" + (key?.lowercase() ?: "")

    /** Albums by name, A to Z with the untagged ones last. Tracks keep the order they came in. */
    fun <T> albums(items: List<T>, f: TrackFields<T>): List<LibraryCollection<T>> =
        build(items, BrowseKind.Albums, { f.album(it).clean() }, f)

    fun <T> artists(items: List<T>, f: TrackFields<T>): List<LibraryCollection<T>> =
        build(items, BrowseKind.Artists, { f.artist(it).clean() }, f)

    fun <T> find(all: List<LibraryCollection<T>>, id: String): LibraryCollection<T>? = all.firstOrNull { it.id == id }

    private fun <T> build(items: List<T>, kind: BrowseKind, key: (T) -> String?, f: TrackFields<T>): List<LibraryCollection<T>> {
        val buckets = LinkedHashMap<String, Pair<String?, ArrayList<T>>>()
        for (item in items) {
            val name = key(item)
            buckets.getOrPut(name?.lowercase() ?: "") { name to ArrayList() }.second += item
        }
        return buckets.values
            .sortedBy { (name, _) -> name?.lowercase() ?: "\uFFFF" }
            .map { (name, list) ->
                val artists = list.mapNotNull { f.artist(it).clean() }.map { it.lowercase() }.toSet()
                val one = if (artists.size == 1 && list.all { f.artist(it).clean() != null }) list.firstNotNullOf { f.artist(it).clean() } else null
                LibraryCollection(kind, name, one, kind == BrowseKind.Albums && artists.size > 1, list)
            }
    }
}

/** The two tracks chosen for a comparison: choosing a third drops the oldest pick, choosing a picked one drops it. */
object ComparePicks {
    const val MAX = 2

    fun toggle(current: List<String>, id: String): List<String> =
        if (id in current) current - id else (current + id).takeLast(MAX)

    fun encode(picks: List<String>): String = picks.joinToString("\n")

    fun decode(raw: String): List<String> = raw.split('\n').filter { it.isNotEmpty() }
}

/** How the library is laid out, remembered between launches. */
data class LibraryView(
    val sort: SortField = SortField.Title,
    val ascending: Boolean = true,
    val grid: Boolean = false,
    val group: GroupField = GroupField.None,
    val browse: BrowseKind = BrowseKind.Tracks,
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString(KEY_SORT, sort.name).putBoolean(KEY_ASC, ascending)
            .putBoolean(KEY_GRID, grid).putString(KEY_GROUP, group.name).putString(KEY_BROWSE, browse.name)
            .apply()
    }

    companion object {
        private const val KEY_SORT = "library_sort"
        private const val KEY_ASC = "library_ascending"
        private const val KEY_GRID = "library_grid"
        private const val KEY_GROUP = "library_group"
        private const val KEY_BROWSE = "library_browse"

        fun load(prefs: SharedPreferences) = LibraryView(
            sort = SortField.entries.firstOrNull { it.name == prefs.getString(KEY_SORT, null) } ?: SortField.Title,
            ascending = prefs.getBoolean(KEY_ASC, true),
            grid = prefs.getBoolean(KEY_GRID, false),
            group = GroupField.entries.firstOrNull { it.name == prefs.getString(KEY_GROUP, null) } ?: GroupField.None,
            browse = BrowseKind.entries.firstOrNull { it.name == prefs.getString(KEY_BROWSE, null) } ?: BrowseKind.Tracks,
        )
    }
}
