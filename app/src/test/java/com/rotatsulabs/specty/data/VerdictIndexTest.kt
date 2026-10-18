package com.rotatsulabs.specty.data

import com.rotatsulabs.specty.audio.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VerdictIndexTest {
    private val header = "v1.3"

    private fun dir(): File = Files.createTempDirectory("verdicts").toFile().also { it.deleteOnExit() }
    private fun index(dir: File, max: Int = 20_000) = VerdictIndex(File(dir, "index.txt"), header, max)

    @Test
    fun aLargeLibraryIsSavedAndReadBackInFull() {
        val dir = dir()
        val index = index(dir)
        for (i in 0 until 2_000) index.put("key$i", if (i % 3 == 0) Verdict.GENUINE else Verdict.LOSSY_SOURCE)
        assertEquals(2_000, index.size)
        val again = index(dir)
        assertTrue(again.load())
        assertEquals(2_000, again.size)
        assertEquals(index.snapshot(), again.snapshot())
        assertEquals(2_001, File(dir, "index.txt").readLines().size)
    }

    @Test
    fun savingCostsTheSameWhenTheIndexIsBig() {
        val index = index(dir())
        val start = System.nanoTime()
        for (i in 0 until 6_000) index.put("key$i", Verdict.GENUINE)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 15_000)
    }

    @Test
    fun repeatedScansOfTheSameFilesDoNotGrowTheFile() {
        val dir = dir()
        val index = index(dir)
        repeat(8) { pass -> for (i in 0 until 500) index.put("key$i", if (pass % 2 == 0) Verdict.GENUINE else Verdict.UPSAMPLED) }
        assertEquals(500, index.size)
        assertTrue(File(dir, "index.txt").readLines().size < 500 * 2 + 1_100)
        val again = index(dir)
        again.load()
        assertEquals(Verdict.UPSAMPLED, again.snapshot()["key7"])
    }

    @Test
    fun aLineCutShortByARebootIsIgnored() {
        val dir = dir()
        val index = index(dir)
        for (i in 0 until 50) index.put("key$i", Verdict.GENUINE)
        File(dir, "index.txt").appendText("key50\tGENU")
        val again = index(dir)
        assertTrue(again.load())
        assertEquals(50, again.size)
        assertFalse("key50" in again)
        again.put("key51", Verdict.HI_RES)
        val third = index(dir)
        third.load()
        assertEquals(51, third.size)
    }

    @Test
    fun anIndexFromAnotherVersionIsDroppedAndReset() {
        val dir = dir()
        File(dir, "index.txt").writeText("v0.1\nold\tGENUINE\n")
        val index = index(dir)
        assertFalse(index.load())
        assertEquals(0, index.size)
        index.put("new", Verdict.GENUINE)
        val again = index(dir)
        assertTrue(again.load())
        assertEquals(setOf("new"), again.snapshot().keys)
    }

    @Test
    fun theOldestEntriesGoFirstWhenTheLimitIsPassed() {
        val dir = dir()
        val index = index(dir, max = 100)
        val dropped = ArrayList<String>()
        for (i in 0 until 150) dropped += index.put("key$i", Verdict.GENUINE)
        assertEquals(100, index.size)
        assertEquals((0 until 50).map { "key$it" }, dropped)
        val again = index(dir, max = 100)
        again.load()
        assertEquals(index.keysOldestFirst(), again.keysOldestFirst())
    }

    @Test
    fun anEntryScannedAgainBecomesTheNewest() {
        val index = index(dir(), max = 3)
        index.put("a", Verdict.GENUINE)
        index.put("b", Verdict.GENUINE)
        index.put("a", Verdict.GENUINE)
        val dropped = index.put("c", Verdict.GENUINE) + index.put("d", Verdict.GENUINE)
        assertEquals(listOf("b"), dropped)
        assertEquals(listOf("a", "c", "d"), index.keysOldestFirst())
    }

    @Test
    fun clearingEmptiesTheFile() {
        val dir = dir()
        val index = index(dir)
        for (i in 0 until 20) index.put("key$i", Verdict.GENUINE)
        index.clear()
        assertEquals(0, index.size)
        assertEquals(listOf(header), File(dir, "index.txt").readLines())
    }
}
