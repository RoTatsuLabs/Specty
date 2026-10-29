package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.ui.design.RevealMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RevealMathTest {
    private val w = 1080f
    private val h = 2400f

    @Test
    fun fromTheMiddleItReachesTheCorners() {
        assertEquals(hypot(w / 2, h / 2), RevealMath.reach(w / 2, h / 2, w, h), 0.001f)
    }

    @Test
    fun fromACornerItReachesTheOppositeOne() {
        assertEquals(hypot(w, h), RevealMath.reach(0f, 0f, w, h), 0.001f)
        assertEquals(hypot(w, h), RevealMath.reach(w, h, w, h), 0.001f)
    }

    @Test
    fun theRadiusAlwaysCoversEveryCorner() {
        for (xi in 0..10) for (yi in 0..10) {
            val x = w * xi / 10
            val y = h * yi / 10
            val r = RevealMath.reach(x, y, w, h)
            for ((cx, cy) in listOf(0f to 0f, w to 0f, 0f to h, w to h)) {
                assertTrue("($x,$y)", r + 0.01f >= hypot(x - cx, y - cy))
            }
        }
    }

    @Test
    fun aSwatchNearTheTopStillRevealsTheBottom() {
        val r = RevealMath.reach(540f, 300f, w, h)
        assertTrue(r > h - 300f)
    }

    @Test
    fun aPointOutsideTheAreaIsHeldInside() {
        assertEquals(0f, RevealMath.inside(-20f, w), 0f)
        assertEquals(w, RevealMath.inside(w + 50f, w), 0f)
        assertEquals(300f, RevealMath.inside(300f, w), 0f)
        assertEquals(0f, RevealMath.inside(5f, -1f), 0f)
    }
}
