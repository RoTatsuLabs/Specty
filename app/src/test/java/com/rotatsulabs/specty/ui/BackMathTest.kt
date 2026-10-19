package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.ui.design.BackMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackMathTest {
    private val steps = (0..100).map { it / 100f }

    @Test
    fun aScreenAtRestIsUntouched() {
        assertEquals(0f, BackMath.part(0f, 0), 0f)
        assertEquals(0f, BackMath.part(0f, 7), 0f)
        assertEquals(1f, BackMath.riseScale(0f), 0f)
        assertEquals(0f, BackMath.riseTravel(0f), 0f)
        assertEquals(1f, BackMath.alpha(0f), 0f)
        assertEquals(0f, BackMath.cornerShare(0f), 0f)
        assertEquals(1f, BackMath.containerOpen(0f), 0f)
        assertEquals(1f, BackMath.underlayScale(0f), 0f)
    }

    @Test
    fun aFullSwipePreviewsTheExitWithoutLeavingIt() {
        val pose = BackMath.pose(1f)
        assertTrue("a swipe stops short of gone", pose < 1f)
        assertTrue("still clearly visible", BackMath.alpha(pose) > 0.5f)
        assertTrue("not shrunk past recognition", BackMath.riseScale(pose) > 0.85f)
        assertTrue("the top of the screen is still mostly there", BackMath.part(pose, 0) < 0.5f)
        assertTrue("the container stays open enough to read", BackMath.containerOpen(1f) > 0.3f)
    }

    @Test
    fun theExitEndsInvisible() {
        assertEquals(0f, BackMath.alpha(1f), 0f)
        assertEquals(1f, BackMath.alpha(BackMath.GESTURE_RANGE), 0f)
        assertEquals(1f, BackMath.cornerShare(1f), 0f)
    }

    @Test
    fun everyCurveMovesOneWayAsTheSwipeGrows() {
        for ((a, b) in steps.zipWithNext()) {
            assertTrue(BackMath.riseScale(b) <= BackMath.riseScale(a))
            for (i in 0..8) assertTrue(BackMath.part(b, i) >= BackMath.part(a, i))
            assertTrue(BackMath.riseTravel(b) >= BackMath.riseTravel(a))
            assertTrue(BackMath.alpha(b) <= BackMath.alpha(a))
            assertTrue(BackMath.cornerShare(b) >= BackMath.cornerShare(a))
            assertTrue(BackMath.pose(b) >= BackMath.pose(a))
            assertTrue(BackMath.containerOpen(b) <= BackMath.containerOpen(a))
        }
    }

    @Test
    fun aSpringOvershootNeverClipsOrFades() {
        for (pose in listOf(-0.02f, -0.05f, -0.1f)) {
            assertEquals(1f, BackMath.alpha(pose), 0f)
            assertEquals(0f, BackMath.cornerShare(pose), 0f)
            for (i in 0..8) assertEquals(0f, BackMath.part(pose, i), 0f)
        }
    }

    @Test
    fun swipeValuesOutsideTheRangeAreHeld() {
        assertEquals(0f, BackMath.pose(-1f), 0f)
        assertEquals(BackMath.GESTURE_RANGE, BackMath.pose(3f), 0f)
        assertEquals(1f, BackMath.containerOpen(-1f), 0f)
        assertEquals(1f - BackMath.CONTAINER_RANGE, BackMath.containerOpen(3f), 0f)
    }

    @Test
    fun theLibraryEasesBackOnlyALittle() {
        assertEquals(0.95f, BackMath.underlayScale(1f), 0.0001f)
        assertEquals(0.95f, BackMath.underlayScale(7f), 0.0001f)
        assertEquals(1f, BackMath.underlayScale(-1f), 0f)
    }

    @Test
    fun theScreenComesApartFromTheBottom() {
        val mid = 0.4f
        assertTrue(BackMath.part(mid, 7) > BackMath.part(mid, 3))
        assertTrue(BackMath.part(mid, 3) > BackMath.part(mid, 0))
        for (i in 0..12) assertEquals(1f, BackMath.part(1f, i), 0f)
        assertEquals(BackMath.part(0.5f, 7), BackMath.part(0.5f, 40), 0f)
    }

    @Test
    fun aLeavingComponentLosesOpacityAndSizeTogether() {
        assertEquals(1f, BackMath.partAlpha(0f), 0f)
        assertEquals(0f, BackMath.partAlpha(1f), 0f)
        assertTrue(BackMath.partScale(1f) in 0.8f..0.95f)
        assertEquals(0f, BackMath.partDrift(0f), 0f)
        assertTrue(BackMath.partDrift(1f) > 0f)
    }

    @Test
    fun aSwipeNeverSquashesTheContainer() {
        val full = BackMath.containerOpen(1f)
        val b = BackMath.container(full, 100f, 1800f, 900f, 200f, 1080f, 2400f)
        assertEquals(b.scaleX, b.scaleY, 0f)
        assertTrue(b.scaleX > 0.85f)
    }

    @Test
    fun theContainerIsContinuousAndEndsAtItsOrigin() {
        val args = floatArrayOf(100f, 1800f, 900f, 200f, 1080f, 2400f)
        fun at(o: Float) = BackMath.container(o, args[0], args[1], args[2], args[3], args[4], args[5])
        val open = at(1f)
        assertEquals(1f, open.scaleX, 0f); assertEquals(0f, open.x, 0f); assertEquals(0f, open.y, 0f)
        val gone = at(0f)
        assertEquals(args[2] / args[4], gone.scaleX, 0.0001f)
        assertEquals(args[3] / args[5], gone.scaleY, 0.0001f)
        assertEquals(args[0], gone.x, 0.0001f)
        assertEquals(args[1], gone.y, 0.0001f)
        val a = at(BackMath.REST + 0.0001f); val b = at(BackMath.REST - 0.0001f)
        assertEquals(a.scaleX, b.scaleX, 0.001f); assertEquals(a.scaleY, b.scaleY, 0.001f)
        assertEquals(a.x, b.x, 1f); assertEquals(a.y, b.y, 1f)
    }
}
