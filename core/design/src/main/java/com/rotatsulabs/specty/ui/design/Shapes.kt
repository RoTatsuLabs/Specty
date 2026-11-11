package com.rotatsulabs.specty.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Four corner radii that can be interpolated, which is what makes a shape morph. */
@Immutable
data class Corners(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp) {
    constructor(all: Dp) : this(all, all, all, all)

    fun toShape(): RoundedCornerShape = RoundedCornerShape(
        topStart = topStart.coerceAtLeast(0.dp),
        topEnd = topEnd.coerceAtLeast(0.dp),
        bottomEnd = bottomEnd.coerceAtLeast(0.dp),
        bottomStart = bottomStart.coerceAtLeast(0.dp),
    )

    fun lerpTo(other: Corners, fraction: Float) = Corners(
        lerp(topStart, other.topStart, fraction),
        lerp(topEnd, other.topEnd, fraction),
        lerp(bottomEnd, other.bottomEnd, fraction),
        lerp(bottomStart, other.bottomStart, fraction),
    )

    /** The squarer version of these corners shown while an element is pressed. */
    fun pressed(limit: Dp = 16.dp) = Corners(minOf(topStart, limit), minOf(topEnd, limit), minOf(bottomEnd, limit), minOf(bottomStart, limit))
}

private fun minOf(a: Dp, b: Dp) = if (a < b) a else b

/**
 * The shape vocabulary. Large radii with one deliberately tight corner give containers their expressive silhouette;
 * [TileStart], [TileMiddle] and [TileEnd] join into one segmented group.
 */
object SpectyShapes {
    val Pill = Corners(100.dp)
    val Card = Corners(24.dp)
    val CardMirrored = Corners(24.dp)
    val CardSoft = Corners(20.dp)
    val Hero = Corners(topStart = 40.dp, topEnd = 40.dp, bottomEnd = 40.dp, bottomStart = 16.dp)
    val Feature = Corners(topStart = 48.dp, topEnd = 20.dp, bottomEnd = 48.dp, bottomStart = 20.dp)
    val Field = Corners(20.dp)
    val Tile = Corners(20.dp)
    val TileStart = Corners(topStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp, bottomStart = 24.dp)
    val TileMiddle = Corners(6.dp)
    val TileEnd = Corners(topStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 6.dp)
    val Row = Corners(20.dp)
    val Cover = Corners(14.dp)
    val Segment = Corners(18.dp)
}

/** Material's own shape slots, tuned to the same scale so stock components agree with the custom ones. */
val SpectyMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * A rounded blob with [lobes] gentle bumps, like a cookie or scalloped badge. [amplitude] (0..0.5) is how deep the
 * dips are and [rotationDeg] spins it, so animating either one morphs the outline.
 */
class LobedShape(private val lobes: Int, private val amplitude: Float, private val rotationDeg: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) / 2f
        val steps = 240
        val spin = Math.toRadians(rotationDeg.toDouble())
        for (i in 0..steps) {
            val a = i / steps.toDouble() * 2.0 * PI
            val r = radius * (1f - amplitude * (0.5f - 0.5f * cos(lobes * a).toFloat()))
            val x = cx + r * cos(a + spin).toFloat()
            val y = cy + r * sin(a + spin).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
