package com.rotatsulabs.specty.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFoldersTest {
    @Test
    fun audioIsRecognisedByTypeOrByExtension() {
        assertTrue(AudioFiles.isAudio("a.bin", "audio/mpeg"))
        assertTrue(AudioFiles.isAudio("a.ogg", "application/ogg"))
        assertTrue(AudioFiles.isAudio("Song.FLAC", "application/octet-stream"))
        assertTrue(AudioFiles.isAudio("Song.m4a", null))
        assertTrue(AudioFiles.isAudio("Song.ac4", "application/octet-stream"))
    }

    @Test
    fun otherFilesAreLeftOut() {
        assertFalse(AudioFiles.isAudio("cover.jpg", "image/jpeg"))
        assertFalse(AudioFiles.isAudio("notes.txt", "text/plain"))
        assertFalse(AudioFiles.isAudio("clip.mp4", "video/mp4"))
        assertFalse(AudioFiles.isAudio("README", null))
    }

    @Test
    fun foldersAreNamedFromTheirDocumentId() {
        val albums = AudioFiles.folderLabel("primary:Music/Albums")
        assertEquals("Albums", albums.name)
        assertEquals("Internal storage/Music/Albums", albums.path)
        val root = AudioFiles.folderLabel("primary:")
        assertEquals("Internal storage", root.name)
        assertEquals("Internal storage", root.path)
        val card = AudioFiles.folderLabel("1A2B-3C4D:Flac/")
        assertEquals("Flac", card.name)
        assertEquals("1A2B-3C4D/Flac", card.path)
        assertEquals("Music", AudioFiles.folderLabel("/storage/Music").name)
    }

    @Test
    fun anExcludedFolderHidesItselfAndEverythingBelow() {
        val out = listOf("primary:Music/Podcasts")
        assertTrue(AudioFiles.isExcluded("primary:Music/Podcasts", out))
        assertTrue(AudioFiles.isExcluded("primary:Music/Podcasts/2024", out))
        assertFalse(AudioFiles.isExcluded("primary:Music/Podcasts2", out))
        assertFalse(AudioFiles.isExcluded("primary:Music", out))
        assertFalse(AudioFiles.isExcluded("primary:Music/Albums", out))
        assertFalse(AudioFiles.isExcluded("primary:Music/Albums", emptyList()))
    }

    @Test
    fun excludingAVolumeRootHidesTheWholeVolume() {
        assertTrue(AudioFiles.isExcluded("1A2B-3C4D:Flac", listOf("1A2B-3C4D:")))
        assertFalse(AudioFiles.isExcluded("primary:Flac", listOf("1A2B-3C4D:")))
    }

    @Test
    fun trackMetaSurvivesTheCacheFile() {
        val entries = mapOf(
            TrackMetaCodec.keyOf("content://x/1", 10L, 5L) to TrackMeta("Title", "Artist", "Album", 213000L),
            TrackMetaCodec.keyOf("content://x/2", 11L, 6L) to TrackMeta(null, null, null, 0L),
        )
        assertEquals(entries, TrackMetaCodec.decode(TrackMetaCodec.encode(entries)))
    }

    @Test
    fun tabsAndLineBreaksInTagsCannotBreakTheCache() {
        val key = TrackMetaCodec.keyOf("content://x/1", 1L, 1L)
        val back = TrackMetaCodec.decode(TrackMetaCodec.encode(mapOf(key to TrackMeta("A\tB\nC", "x", null, 5L))))
        assertEquals("A B C", back[key]?.title)
        assertEquals(1, back.size)
    }

    @Test
    fun garbledCacheLinesAreSkipped() {
        val text = "ok|1|1\tT\tA\tB\t9\nbroken line\nkey\tonly\nbad|1|1\tT\tA\tB\tnotanumber\n"
        val back = TrackMetaCodec.decode(text)
        assertEquals(setOf("ok|1|1"), back.keys)
        assertNull(back["bad|1|1"])
    }
}
