package com.rotatsulabs.specty.ui.design

/**
 * The numbers behind the back gesture, kept free of Android so they can be tested. A screen's "pose" runs from 0
 * (fully shown) to 1 (gone). A full swipe only carries it part of the way, like the system's own back preview, and
 * letting go to commit finishes the rest with a spring from wherever the finger left it.
 */
object BackMath {
    /** How far a full swipe moves a sliding screen along its way out. */
    const val GESTURE_RANGE = 0.5f

    /** How far a full swipe shrinks a container transform back toward the element it grew from. */
    const val CONTAINER_RANGE = 0.6f

    private const val SIDE_SHRINK = 0.16f
    private const val SIDE_TRAVEL = 0.55f
    private const val RISE_SHRINK = 0.22f
    private const val RISE_TRAVEL = 0.12f
    private const val FADE_START = 0.35f
    private const val CORNER_RAMP = 3f

    /** Pose of a sliding or rising screen for a swipe [progress] of 0 to 1. */
    fun pose(progress: Float): Float = progress.coerceIn(0f, 1f) * GESTURE_RANGE

    /** Progress of a container transform (1 fully open) for a swipe [progress]. */
    fun containerOpen(progress: Float): Float = 1f - progress.coerceIn(0f, 1f) * CONTAINER_RANGE

    fun sideScale(pose: Float): Float = 1f - SIDE_SHRINK * pose

    /** Share of the screen width the screen has travelled; the sign comes from the edge the swipe began at. */
    fun sideTravel(pose: Float): Float = SIDE_TRAVEL * pose

    fun riseScale(pose: Float): Float = 1f - RISE_SHRINK * pose

    fun riseTravel(pose: Float): Float = RISE_TRAVEL * pose

    /** Opaque until a third of the way out, then fading to nothing at the end. */
    fun alpha(pose: Float): Float = (1f - (pose - FADE_START) / (1f - FADE_START)).coerceIn(0f, 1f)

    /** Share of the full corner radius: square at rest, round after a short swipe. Rest stays unclipped. */
    fun cornerShare(pose: Float): Float = (pose * CORNER_RAMP).coerceIn(0f, 1f)

    /** How far the screen underneath settles back while one is open above it. */
    fun underlayScale(depth: Float): Float = 1f - 0.05f * depth.coerceIn(0f, 1f)
}
