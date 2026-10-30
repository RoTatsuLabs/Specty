package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.screens.Duplicates
import com.rotatsulabs.specty.ui.screens.LibraryReport
import com.rotatsulabs.specty.ui.screens.ResultFilter
import com.rotatsulabs.specty.ui.screens.TrackFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private data class T(
        val title: String,
        val artist: String? = "Mia",
        val ms: Long = 200_000,
        val size: Long = 1,
        val format: String = "FLAC",
        val verdict: Verdict? = null,
    )

    private val fields = TrackFields<T>({ it.title }, { it.artist }, { null }, { it.ms }, { it.size }, { 0 }, { it.format }, { 0 })
    private val quality: (T) -> Int = { ResultFilter.of(it.verdict).quality }

    @Test
    fun normalizeIgnoresCaseSpacingPunctuationAndEditionMarks() {
        assertEquals(Duplicates.normalize("Hello, World!"), Duplicates.normalize("hello world"))
        assertEquals(Duplicates.normalize("Song"), Duplicates.normalize("Song (2011 Remaster)"))
        assertEquals(Duplicates.normalize("Song"), Duplicates.normalize("Song [Explicit]"))
        assertTrue(Duplicates.normalize("Song") != Duplicates.normalize("Song (Live)"))
    }

    @Test
    fun sameSongAtTwoQualitiesIsOneGroupAndTheBetterResultIsBest() {
        val lossy = T("Song", verdict = Verdict.LOSSY_CODEC, size = 9, format = "MP3")
        val real = T("song (Remastered)", verdict = Verdict.GENUINE, size = 30)
        val other = T("Another")
        val groups = Duplicates.find(listOf(lossy, other, real), fields, quality)
        assertEquals(1, groups.size)
        assertEquals(real, groups[0].best)
        assertEquals(listOf(real, lossy), groups[0].copies)
    }

    @Test
    fun whenResultsTieTheBiggerFileIsBest() {
        val small = T("Song", size = 10)
        val big = T("Song", size = 50)
        assertEquals(big, Duplicates.find(listOf(small, big), fields, quality).single().best)
    }

    @Test
    fun differentLengthsAndMissingArtistsAreNotMatched() {
        val a = T("Song", ms = 200_000)
        val longer = T("Song", ms = 260_000)
        assertTrue(Duplicates.find(listOf(a, longer), fields, quality).isEmpty())
        assertTrue(Duplicates.find(listOf(T("Song", artist = null), T("Song", artist = null)), fields, quality).isEmpty())
        assertTrue(Duplicates.find(listOf(T("Song", artist = "Mia"), T("Song", artist = "Abe")), fields, quality).isEmpty())
    }

    @Test
    fun threeCopiesStayTogetherWhenTheirLengthsAreClose() {
        val items = listOf(T("Song", ms = 200_000), T("Song", ms = 201_500), T("Song", ms = 202_900))
        assertEquals(3, Duplicates.find(items, fields, quality).single().copies.size)
    }

    @Test
    fun reportCountsResultsFormatsAndTotals() {
        val items = listOf(
            T("a", verdict = Verdict.GENUINE, size = 5, ms = 1000),
            T("b", verdict = Verdict.GENUINE, size = 5, ms = 2000, format = "flac"),
            T("c", verdict = Verdict.UPSAMPLED, size = 1, ms = 3000, format = "mp3"),
            T("d", size = 1, ms = 4000, format = ""),
        )
        val r = LibraryReport.build(items, fields) { it.verdict }
        assertEquals(4, r.total)
        assertEquals(3, r.checked)
        assertEquals(listOf(ResultFilter.NeedsLook to 1, ResultFilter.Lossless to 2, ResultFilter.Unchecked to 1), r.results)
        assertEquals(listOf("FLAC" to 2, "MP3" to 1), r.formats)
        assertEquals(12L, r.sizeBytes)
        assertEquals(10_000L, r.durationMs)
    }
}
