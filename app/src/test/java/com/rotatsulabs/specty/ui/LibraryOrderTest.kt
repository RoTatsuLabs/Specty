package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.ui.screens.ComparePicks
import com.rotatsulabs.specty.ui.screens.GroupField
import com.rotatsulabs.specty.ui.screens.LibraryOrder
import com.rotatsulabs.specty.ui.screens.SortField
import com.rotatsulabs.specty.ui.screens.TrackFields
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrderTest {
    private data class T(
        val title: String,
        val artist: String? = null,
        val album: String? = null,
        val ms: Long = 0,
        val size: Long = 0,
        val modified: Long = 0,
        val format: String = "FLAC",
        val rank: Int = 0,
    )

    private val fields = TrackFields<T>({ it.title }, { it.artist }, { it.album }, { it.ms }, { it.size }, { it.modified }, { it.format }, { it.rank })

    private fun titles(list: List<T>) = list.map { it.title }

    @Test
    fun titleSortIgnoresCase() {
        val out = LibraryOrder.sort(listOf(T("beta"), T("Alpha"), T("charlie")), SortField.Title, true, fields)
        assertEquals(listOf("Alpha", "beta", "charlie"), titles(out))
    }

    @Test
    fun descendingReversesTheWholeOrder() {
        val out = LibraryOrder.sort(listOf(T("a"), T("b"), T("c")), SortField.Title, false, fields)
        assertEquals(listOf("c", "b", "a"), titles(out))
    }

    @Test
    fun tracksWithoutAnArtistSortLastAndTiesFallBackToTitle() {
        val items = listOf(T("z", null), T("b", "Mia"), T("a", "Mia"), T("m", "  "), T("c", "Abe"))
        val out = LibraryOrder.sort(items, SortField.Artist, true, fields)
        assertEquals(listOf("c", "a", "b", "m", "z"), titles(out))
    }

    @Test
    fun albumSortWorksTheSameWay() {
        val items = listOf(T("x", album = "Two"), T("y", album = null), T("w", album = "One"))
        assertEquals(listOf("w", "x", "y"), titles(LibraryOrder.sort(items, SortField.Album, true, fields)))
    }

    @Test
    fun modifiedSortPutsTheOldestFirstAndUnknownDatesAtTheStart() {
        val items = listOf(T("new", modified = 300), T("none", modified = 0), T("old", modified = 100))
        assertEquals(listOf("none", "old", "new"), titles(LibraryOrder.sort(items, SortField.Modified, true, fields)))
        assertEquals(listOf("new", "old", "none"), titles(LibraryOrder.sort(items, SortField.Modified, false, fields)))
    }

    @Test
    fun resultSortFollowsTheRankThenTitle() {
        val items = listOf(T("b", rank = 2), T("a", rank = 2), T("z", rank = 0), T("m", rank = 1))
        assertEquals(listOf("z", "m", "a", "b"), titles(LibraryOrder.sort(items, SortField.Result, true, fields)))
    }

    @Test
    fun noGroupingGivesOneHeadingWithEverything() {
        val items = listOf(T("a"), T("b"))
        val groups = LibraryOrder.group(items, GroupField.None, fields)
        assertEquals(1, groups.size)
        assertEquals(items, groups[0].items)
    }

    @Test
    fun artistGroupsRunAtoZWithTheUnnamedOneLastAndKeepTheSortOrderInside() {
        val sorted = listOf(T("1", "Mia"), T("2", "abe"), T("3", null), T("4", "mia"), T("5", "Abe"))
        val groups = LibraryOrder.group(sorted, GroupField.Artist, fields)
        assertEquals(listOf("abe", "Mia", null), groups.map { it.key })
        assertEquals(listOf("2", "5"), titles(groups[0].items))
        assertEquals(listOf("1", "4"), titles(groups[1].items))
        assertEquals(listOf("3"), titles(groups[2].items))
    }

    @Test
    fun resultGroupsFollowTheRankNotTheAlphabet() {
        val sorted = listOf(T("a", rank = 3), T("b", rank = 0), T("c", rank = 3), T("d", rank = 1))
        val groups = LibraryOrder.group(sorted, GroupField.Result, fields)
        assertEquals(listOf(0, 1, 3), groups.map { it.rank })
        assertEquals(listOf("a", "c"), titles(groups[2].items))
    }

    @Test
    fun everyHeadingHasItsOwnId() {
        val sorted = listOf(T("1", "A"), T("2", null), T("3", "B"))
        val ids = LibraryOrder.group(sorted, GroupField.Artist, fields).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun aThirdPickDropsTheOldestAndAPickedTrackCanBeUnpicked() {
        var picks = emptyList<String>()
        picks = ComparePicks.toggle(picks, "a")
        picks = ComparePicks.toggle(picks, "b")
        assertEquals(listOf("a", "b"), picks)
        picks = ComparePicks.toggle(picks, "c")
        assertEquals(listOf("b", "c"), picks)
        picks = ComparePicks.toggle(picks, "b")
        assertEquals(listOf("c"), picks)
    }

    @Test
    fun picksSurviveBeingSavedAsText() {
        val picks = listOf("content://a/1", "content://b/2")
        assertEquals(picks, ComparePicks.decode(ComparePicks.encode(picks)))
        assertEquals(emptyList<String>(), ComparePicks.decode(""))
    }
}
