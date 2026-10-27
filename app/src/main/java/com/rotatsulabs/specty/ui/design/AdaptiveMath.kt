package com.rotatsulabs.specty.ui.design

/** Width-driven layout decisions, kept free of Compose so they can be tested on the JVM. */
object AdaptiveMath {
    /** From this width (dp) a screen has room for two panes side by side. */
    const val WIDE = 720

    /** The widest a column of content grows before it is centred instead of stretched. */
    const val READABLE = 640

    /** The widest the library grows. */
    const val LIBRARY_MAX = 1100

    fun isWide(widthDp: Int) = widthDp >= WIDE

    /** Tiles per row for a grid that is [widthDp] wide, each at least [minTileDp] across. */
    fun columns(widthDp: Int, minTileDp: Int = 168, gutterDp: Int = 32, max: Int = 6): Int =
        ((widthDp.coerceAtMost(LIBRARY_MAX) - gutterDp) / minTileDp).coerceIn(2, max)
}
