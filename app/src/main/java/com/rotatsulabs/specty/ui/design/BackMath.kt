package com.rotatsulabs.specty.ui.design

/**
 * The numbers behind the back gesture, kept free of Android so they can be tested. A screen's "pose" runs from 0
 * (fully shown) to 1 (gone). A full swipe only carries it part of the way, like the system's own back preview, and
 * letting go to commit finishes the rest with a spring from wherever the finger left it.
 */
object BackMath {
    /** How far a full swipe moves a sliding screen along its way out. */
    const val GESTURE_RANGE = 0.5f

    /** How far a full swipe closes a container transform (kept in its gentle range, see [container]). */
    const val CONTAINER_RANGE = 0.35f

    /** Below this openness a container leaves its settled pose and shrinks toward the element it grew from. */
    const val REST = 0.6f
    private const val SETTLE_SCALE = 0.9f

    private const val SHRINK = 0.2f
    private const val SHIFT = 0.16f
    private const val LIFT_GAIN = 0.35f

    /** The screen may follow the finger up or down by at most this share of its height. */
    const val LIFT_LIMIT = 0.08f
    private const val FADE_START = 0.5f
    private const val CORNER_RAMP = 3f

    /** Pose of a sliding or rising screen for a swipe [progress] of 0 to 1. */
    fun pose(progress: Float): Float = progress.coerceIn(0f, 1f) * GESTURE_RANGE

    /** Progress of a container transform (1 fully open) for a swipe [progress]. */
    fun containerOpen(progress: Float): Float = 1f - progress.coerceIn(0f, 1f) * CONTAINER_RANGE

    /** Where a container transform sits, in pixels, for [open] (1 fully open) and the element it grows from. */
    class ContainerBox(val scaleX: Float, val scaleY: Float, val x: Float, val y: Float)

    /**
     * Near fully open the container only settles back evenly (so text is never squashed during a swipe); below
     * [REST] it shrinks on toward the origin element's size and position.
     */
    fun container(open: Float, fromLeft: Float, fromTop: Float, fromW: Float, fromH: Float, w: Float, h: Float): ContainerBox {
        if (open >= REST) {
            val s = 1f - (1f - SETTLE_SCALE) * (1f - open) / (1f - REST)
            return ContainerBox(s, s, w * (1f - s) / 2f, h * (1f - s) / 2f)
        }
        val u = (open / REST).coerceAtMost(1f)
        fun mix(a: Float, b: Float) = a + (b - a) * u
        return ContainerBox(
            mix(fromW / w, SETTLE_SCALE), mix(fromH / h, SETTLE_SCALE),
            mix(fromLeft, w * (1f - SETTLE_SCALE) / 2f), mix(fromTop, h * (1f - SETTLE_SCALE) / 2f),
        )
    }

    /** Scale of the whole screen: it shrinks as it is pulled back, like the system's back preview. */
    fun scale(pose: Float): Float = 1f - SHRINK * pose

    /** Share of its width the screen has slid away from the edge the swipe began at. */
    fun shift(pose: Float): Float = SHIFT * pose

    /** Vertical follow of the finger: a damped share of how far it moved from where the swipe began, held to [limit]. */
    fun lift(dy: Float, limit: Float): Float = (dy * LIFT_GAIN).coerceIn(-limit, limit)

    /** Opaque through the whole swipe, then fading to nothing as the exit finishes. */
    fun alpha(pose: Float): Float = (1f - (pose - FADE_START) / (1f - FADE_START)).coerceIn(0f, 1f)

    /** Share of the full corner radius: square at rest, round after a short swipe. Rest stays unclipped. */
    fun cornerShare(pose: Float): Float = (pose * CORNER_RAMP).coerceIn(0f, 1f)

    /** How far the screen underneath settles back while one is open above it. */
    fun underlayScale(depth: Float): Float = 1f - 0.05f * depth.coerceIn(0f, 1f)
}
