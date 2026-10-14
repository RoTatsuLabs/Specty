package com.rotatsulabs.specty.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/** Expressive motion: springy for movement and shape, firm for colour and opacity. */
object Motion {
    fun <T> spatial() = spring<T>(dampingRatio = 0.72f, stiffness = 380f)
    fun <T> spatialFast() = spring<T>(dampingRatio = 0.62f, stiffness = 700f)
    fun <T> effects() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f)
}
