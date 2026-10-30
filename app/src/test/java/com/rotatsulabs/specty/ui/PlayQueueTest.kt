package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.ui.player.PlayQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class PlayQueueTest {
    @Test
    fun movesForwardAndBackAndStopsAtTheEnds() {
        val q = PlayQueue.of(listOf("a", "b", "c"))!!
        assertEquals("a", q.current)
        assertNull(q.previous())
        val last = q.next()!!.next()!!
        assertEquals("c", last.current)
        assertNull(last.next())
        assertEquals("b", last.previous()!!.current)
    }

    @Test
    fun emptyListsMakeNoQueueAndTheStartIsClamped() {
        assertNull(PlayQueue.of(emptyList<String>()))
        assertEquals("c", PlayQueue.of(listOf("a", "b", "c"), 99)!!.current)
        assertEquals("a", PlayQueue.of(listOf("a", "b", "c"), -4)!!.current)
    }

    @Test
    fun shufflingKeepsEveryTrackAndStartsWithTheOneThatIsPlaying() {
        val items = (1..50).toList()
        val shuffled = PlayQueue(items, 7).shuffled(Random(1))
        assertEquals(8, shuffled.current)
        assertEquals(0, shuffled.index)
        assertEquals(items.toSet(), shuffled.items.toSet())
        assertEquals(50, shuffled.items.size)
        assertNotNull(shuffled.next())
    }
}
