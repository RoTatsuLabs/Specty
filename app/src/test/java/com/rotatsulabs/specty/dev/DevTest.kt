package com.rotatsulabs.specty.dev

import com.rotatsulabs.specty.dev.TapCounter.Result
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevTest {
    private fun tmp() = File.createTempFile("log", "d").let { it.delete(); it.mkdirs(); it }

    @Test
    fun eightQuickTapsUnlock() {
        val t = TapCounter()
        val r = (0 until 8).map { t.tap(it * 200L) }
        assertEquals(Result.Idle, r[0])
        assertEquals(Result.Idle, r[1])
        assertEquals(Result.Remaining(5), r[2])
        assertEquals(Result.Remaining(1), r[6])
        assertEquals(Result.Unlocked, r[7])
    }

    @Test
    fun aPauseStartsTheCountAgain() {
        val t = TapCounter()
        repeat(5) { t.tap(it * 100L) }
        assertEquals(Result.Idle, t.tap(10_000))
        assertEquals(Result.Idle, t.tap(10_100))
        assertEquals(Result.Remaining(5), t.tap(10_200))
    }

    @Test
    fun afterUnlockingTheCountStartsFresh() {
        val t = TapCounter()
        repeat(8) { t.tap(it * 100L) }
        assertEquals(Result.Idle, t.tap(900))
    }

    @Test
    fun linesAreWrittenAndTailKeepsWholeLines() {
        val s = LogStore(tmp())
        repeat(100) { s.append("line $it") }
        val tail = s.tail(60)
        assertTrue(tail.length <= 60)
        assertTrue(tail.lines().filter { it.isNotEmpty() }.all { it.startsWith("line ") })
        assertTrue(tail.trimEnd().endsWith("line 99"))
    }

    @Test
    fun theLogRotatesAndStaysBounded() {
        val d = tmp()
        val s = LogStore(d, maxBytes = 1000)
        repeat(500) { s.append("x".repeat(50)) }
        assertTrue(s.sizeBytes() <= 2000)
        assertTrue(File(d, "specty.log.1").exists())
        assertTrue(s.tail().isNotEmpty())
    }

    @Test
    fun clearRemovesEverything() {
        val s = LogStore(tmp(), maxBytes = 100)
        repeat(20) { s.append("hello world") }
        s.clear()
        assertEquals("", s.tail())
        assertEquals(0L, s.sizeBytes())
    }

    @Test
    fun formatIncludesLevelTagAndStack() {
        val plain = LogStore.format(0, 'I', "Scan", "started")
        assertTrue(plain.endsWith(" I/Scan: started"))
        val err = LogStore.format(0, 'E', "Scan", "failed", IllegalStateException("boom"))
        assertTrue(err.contains("IllegalStateException: boom"))
        assertFalse(plain.contains("\n"))
    }
}
