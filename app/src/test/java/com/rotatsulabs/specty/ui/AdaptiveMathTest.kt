package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.ui.design.AdaptiveMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveMathTest {
    @Test
    fun phonesGetTwoColumns() {
        assertEquals(2, AdaptiveMath.columns(360))
        assertEquals(2, AdaptiveMath.columns(411))
    }

    @Test
    fun widerWindowsGetMoreColumns() {
        assertEquals(3, AdaptiveMath.columns(600))
        assertEquals(4, AdaptiveMath.columns(800))
    }

    @Test
    fun columnsStopGrowingPastTheLibraryCap() {
        assertEquals(AdaptiveMath.columns(1100), AdaptiveMath.columns(2400))
        assertTrue(AdaptiveMath.columns(5000) <= 6)
    }

    @Test
    fun twoPanesStartAtTheWideWidth() {
        assertFalse(AdaptiveMath.isWide(719))
        assertTrue(AdaptiveMath.isWide(720))
    }
}
