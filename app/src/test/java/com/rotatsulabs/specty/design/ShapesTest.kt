package com.rotatsulabs.specty.design

import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.groupCorners
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapesTest {
    @Test
    fun lerpEndpointsAreExact() {
        val a = Corners(40.dp)
        val b = Corners(8.dp)
        assertEquals(a, a.lerpTo(b, 0f))
        assertEquals(b, a.lerpTo(b, 1f))
        assertEquals(Corners(24.dp), a.lerpTo(b, 0.5f))
    }

    @Test
    fun pressedNeverGrowsACorner() {
        listOf(SpectyShapes.Card, SpectyShapes.Hero, SpectyShapes.Pill, SpectyShapes.TileEnd).forEach { c ->
            val p = c.pressed()
            assertTrue(p.topStart <= c.topStart && p.topEnd <= c.topEnd)
            assertTrue(p.bottomEnd <= c.bottomEnd && p.bottomStart <= c.bottomStart)
        }
    }

    @Test
    fun overshootNeverProducesANegativeRadius() {
        // Springs overshoot past 1, so the built shape must clamp.
        val c = Corners(40.dp).lerpTo(Corners(4.dp), 1.4f)
        c.toShape()
    }

    @Test
    fun groupedRowsAreRoundOnlyAtTheEnds() {
        val outer = SpectyShapes.Tile
        val first = groupCorners(0, 3)
        val middle = groupCorners(1, 3)
        val last = groupCorners(2, 3)
        assertEquals(outer.topStart, first.topStart)
        assertEquals(8.dp, first.bottomStart)
        assertEquals(Corners(8.dp), middle)
        assertEquals(8.dp, last.topEnd)
        assertEquals(outer.bottomEnd, last.bottomEnd)
        assertEquals(outer, groupCorners(0, 1))
    }
}
