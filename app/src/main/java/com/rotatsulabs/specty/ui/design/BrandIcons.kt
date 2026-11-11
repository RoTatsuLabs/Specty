package com.rotatsulabs.specty.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** One-colour marks for the credited libraries, drawn like the rest of the icons so they take the badge's colours. */
object BrandIcons {
    private fun build(name: String, strokes: List<String>, dots: List<Pair<Float, Float>> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            strokes.forEach {
                addPath(
                    addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
            dots.forEach { (x, y) ->
                addPath(addPathNodes("M${x - 1.6f},$y a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0z"), fill = SolidColor(Color.Black))
            }
        }.build()

    /** Pipes zig-zagging from corner to corner, after the FFmpeg mark. */
    val Ffmpeg: ImageVector by lazy {
        build(
            "Ffmpeg",
            listOf("M5 5h4.7L5 9.7V14.5", "M5 14.5L9.7 9.7 14.5 5H19", "M5 19L12 12 19 5", "M5 19h4.7L14.5 14.5 19 10", "M19 10v4.5L14.5 19H19"),
            listOf(5f to 5f, 9.7f to 9.7f, 14.5f to 5f, 19f to 5f, 5f to 19f, 12f to 12f, 19f to 10f, 19f to 19f, 14.5f to 19f),
        )
    }

    /** A download arrow with two ticks above it, from the Mornye badge. */
    val Mornye: ImageVector by lazy {
        build("Mornye", listOf("M12 5v13", "M7.5 14l4.5 4.5 4.5-4.5", "M7 8v2.5", "M17 8v2.5"))
    }
}
