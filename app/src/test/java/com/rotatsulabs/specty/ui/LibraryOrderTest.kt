package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.ui.screens.BrowseKind
import com.rotatsulabs.specty.ui.screens.Browse
import com.rotatsulabs.specty.ui.screens.ComparePicks
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

    @Test
    fun albumsRunAtoZWithTheUntaggedOneLastAndShareOneArtistWhenAllAgree() {
        val items = listOf(
            T("1", "Mia", "Two"), T("2", "Mia", "two"), T("3", "Abe", "One"), T("4", null, null), T("5", "Abe", "One"),
        )
        val albums = Browse.albums(items, fields)
        assertEquals(listOf("One", "Two", null), albums.map { it.key })
        assertEquals(listOf("Abe", "Mia", null), albums.map { it.artist })
        assertEquals(listOf(2, 2, 1), albums.map { it.items.size })
        assertEquals(listOf(false, false, false), albums.map { it.various })
    }

    @Test
    fun anAlbumWithSeveralArtistsIsVariousAndNamesNone() {
        val albums = Browse.albums(listOf(T("1", "Mia", "Mix"), T("2", "Abe", "Mix")), fields)
        assertEquals(1, albums.size)
        assertEquals(true, albums[0].various)
        assertEquals(null, albums[0].artist)
    }

    @Test
    fun artistsGroupWithoutRegardToCaseAndFindTheirPage() {
        val artists = Browse.artists(listOf(T("1", "Mia"), T("2", "mia"), T("3", "Abe")), fields)
        assertEquals(listOf("Abe", "Mia"), artists.map { it.key })
        val mia = Browse.find(artists, Browse.id(BrowseKind.Artists, "MIA"))!!
        assertEquals(listOf("1", "2"), titles(mia.items))
        assertEquals(null, Browse.find(artists, Browse.id(BrowseKind.Albums, "Mia")))
    }

    @Test
    fun collectionIdsAreUniquePerKind() {
        val a = Browse.albums(listOf(T("1", "X", "X"), T("2", null, null)), fields).map { it.id }
        assertEquals(a.size, a.toSet().size)
        assertEquals(false, Browse.id(BrowseKind.Albums, "x") == Browse.id(BrowseKind.Artists, "x"))
    }
}
