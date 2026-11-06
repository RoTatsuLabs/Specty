package com.rotatsulabs.specty.ui.design

import androidx.compose.ui.unit.dp

/** The spacing scale: every gap, inset and gutter in the app is one of these, so screens share one rhythm. */
object Spacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val Huge = 48.dp

    /** The side inset of a screen. */
    val Gutter = 16.dp

    /** The smallest size of anything that can be tapped. */
    val MinTarget = 48.dp
}

/** Icon sizes. One size per role, so icons keep the same rhythm everywhere. */
object IconSize {
    val Small = 18.dp
    val Medium = 24.dp
    val Large = 32.dp
}
