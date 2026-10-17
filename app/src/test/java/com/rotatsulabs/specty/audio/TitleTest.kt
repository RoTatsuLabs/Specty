package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleTest {
    private fun info(name: String, tags: List<Pair<String, String>>) =
        AudioInfo(name, "flac", null, 44100, 2, 16, null, null, 10.0, 1, tags = tags)

    @Test
    fun theSongTitleTagWinsOverTheFileName() {
        assertEquals("A Rush of Blood to the Head", info("10. A Rush of Blood to the Head.m4a", listOf("TITLE" to " A Rush of Blood to the Head ")).title)
        assertEquals("Song", info("track01.flac", listOf("artist" to "X", "Title" to "Song")).title)
    }

    @Test
    fun withoutAUsableTagTheFileNameLosesItsExtension() {
        assertEquals("track01", info("track01.flac", emptyList()).title)
        assertEquals("track01", info("track01.flac", listOf("title" to "  ")).title)
        assertEquals("no extension", info("no extension", emptyList()).title)
    }
}
