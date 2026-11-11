package com.rotatsulabs.specty.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons for the terms in "How to read results", drawn in the same rounded style as the rest of the app. */
object TermIcons {
    private fun icon(name: String, vararg paths: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    /** A wave whose highest point touches a ceiling line. */
    val TruePeak: ImageVector by lazy { icon("TruePeak", "M3 4h18", "M3 15h3l2.5-9 3.5 14 2.5-8 1.5 3H21") }

    /** A short bar next to a tall one. */
    val DynamicRange: ImageVector by lazy { icon("DynamicRange", "M5 20h14", "M8 17v-3", "M12 17V6", "M16 17v-7") }

    /** A speaker giving off two arcs. */
    val Loudness: ImageVector by lazy { icon("Loudness", "M4 9.5v5h3.5L12 18.5v-13L7.5 9.5z", "M15.5 9a4 4 0 0 1 0 6", "M18 6.5a8 8 0 0 1 0 11") }

    /** A level that holds, then falls away. */
    val Cutoff: ImageVector by lazy { icon("Cutoff", "M3 7h9c3 0 3 10 9 10", "M15 4v16") }

    /** Steps climbing past where the signal ends. */
    val Upsampled: ImageVector by lazy { icon("Upsampled", "M4 19h4v-4h4v-4h4V7h4", "M17 4l3 3-3 3") }

    /** A band between two walls. */
    val BandLimited: ImageVector by lazy { icon("BandLimited", "M3 12h4", "M17 12h4", "M7 5v14", "M17 5v14", "M10 12h4") }
}
