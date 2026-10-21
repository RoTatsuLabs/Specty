package com.rotatsulabs.specty.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownTest {
    @Test
    fun aCoolPhoneRestsLessThanBefore() {
        assertTrue(Cooldown.after(0) < 150L)
        assertTrue(Cooldown.after(0) > 0L)
    }

    @Test
    fun aWarmingPhoneKeepsTheOriginalPause() {
        assertEquals(150L, Cooldown.after(1))
        assertEquals("no thermal report behaves as before", 150L, Cooldown.after(null))
    }

    @Test
    fun aHotterPhoneRestsLongerTheHotterItGets() {
        assertTrue(Cooldown.after(2) > Cooldown.after(1))
        assertEquals(Cooldown.after(2), Cooldown.after(3))
        assertEquals(Cooldown.after(2), Cooldown.after(6))
    }
}
