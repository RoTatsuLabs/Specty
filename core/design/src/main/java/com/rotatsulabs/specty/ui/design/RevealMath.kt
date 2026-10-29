package com.rotatsulabs.specty.ui.design

import kotlin.math.hypot
import kotlin.math.max

/** Geometry of the radial colour reveal, free of Android so it can be tested. */
object RevealMath {
    /** Distance from ([x], [y]) to the farthest corner of a [width] by [height] area: the radius that covers it all. */
    fun reach(x: Float, y: Float, width: Float, height: Float): Float {
        val dx = max(x, width - x)
        val dy = max(y, height - y)
        return hypot(dx, dy)
    }

    /** Keeps a point that landed outside the area (a touch at the edge, a stale position) inside it. */
    fun inside(value: Float, size: Float): Float = value.coerceIn(0f, size.coerceAtLeast(0f))
}
