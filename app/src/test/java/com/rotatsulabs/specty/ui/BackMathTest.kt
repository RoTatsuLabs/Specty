package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.ui.design.BackMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackMathTest {
    private val steps = (0..100).map { it / 100f }

    @Test
    fun aScreenAtRestIsUntouched() {
        assertEquals(1f, BackMath.sideScale(0f), 0f)
        assertEquals(0f, BackMath.sideTravel(0f), 0f)
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
        assertTrue("not shrunk past recognition", BackMath.sideScale(pose) > 0.9f && BackMath.riseScale(pose) > 0.85f)
        assertTrue("the container stays open enough to read", BackMath.containerOpen(1f) > 0.3f)
    }

    @Test
    fun theExitEndsInvisible() {
        assertEquals(0f, BackMath.alpha(1f), 0f)
        assertEquals(1f, BackMath.alpha(0.35f), 0f)
        assertEquals(1f, BackMath.cornerShare(1f), 0f)
    }

    @Test
    fun everyCurveMovesOneWayAsTheSwipeGrows() {
        for ((a, b) in steps.zipWithNext()) {
            assertTrue(BackMath.sideScale(b) <= BackMath.sideScale(a))
            assertTrue(BackMath.riseScale(b) <= BackMath.riseScale(a))
            assertTrue(BackMath.sideTravel(b) >= BackMath.sideTravel(a))
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
            assertTrue(BackMath.sideScale(pose) < 1.02f)
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
}
