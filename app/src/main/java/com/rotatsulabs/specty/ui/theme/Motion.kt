package com.rotatsulabs.specty.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/** Expressive motion: springy for movement and shape, firm for colour and opacity. */
object Motion {
    fun <T> spatial() = spring<T>(dampingRatio = 0.72f, stiffness = 380f)
    fun <T> spatialFast() = spring<T>(dampingRatio = 0.62f, stiffness = 700f)
    fun <T> effects() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f)
}

/** Easing curves and durations from the Material motion spec, for the places a spring is not the right tool. */
object MotionTokens {
    val Emphasized = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val Short = 150
    const val Medium = 300
    const val Long = 450
    const val ExtraLong = 600
    /** Fade through: the outgoing content leaves first, then the incoming content arrives. */
    const val FadeOut = 90
    const val FadeIn = 210
}
