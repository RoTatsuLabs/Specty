package com.rotatsulabs.specty.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.SpectyShapes

enum class RowDensity { Comfortable, Compact }

enum class CoverShape { Rounded, Circle, Cookie }

/** How track lists look, chosen in Settings. Read through [LocalRowStyle] so a list does not need each option passed down. */
data class RowStyle(
    val density: RowDensity = RowDensity.Comfortable,
    val coverShape: CoverShape = CoverShape.Rounded,
    val showResult: Boolean = true,
    val showFormat: Boolean = true,
    val showFolder: Boolean = false,
) {
    val compact: Boolean get() = density == RowDensity.Compact
}

val LocalRowStyle = compositionLocalOf { RowStyle() }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoverShape.toShape(): Shape = when (this) {
    CoverShape.Rounded -> SpectyShapes.Cover.toShape()
    CoverShape.Circle -> CircleShape
    CoverShape.Cookie -> MaterialShapes.Cookie9Sided.toShape()
}

/** The outline a list cover of this style has, for the cover that flies out of it. */
fun rowCoverSpec(style: RowStyle): com.rotatsulabs.specty.ui.design.CoverSpec = when (style.coverShape) {
    CoverShape.Rounded -> com.rotatsulabs.specty.ui.design.CoverSpec.rounded(14f)
    CoverShape.Circle -> com.rotatsulabs.specty.ui.design.CoverSpec.Circle
    CoverShape.Cookie -> com.rotatsulabs.specty.ui.design.CoverSpec.Cookie
}

/** The outline of the Result cover: the chosen shape, with the rounded one a little softer at hero size. */
fun heroCoverSpec(style: RowStyle): com.rotatsulabs.specty.ui.design.CoverSpec =
    if (style.coverShape == CoverShape.Rounded) com.rotatsulabs.specty.ui.design.CoverSpec.rounded(24f) else rowCoverSpec(style)

/** The Compose shape for a [CoverSpec], matching what the flight draws. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun com.rotatsulabs.specty.ui.design.CoverSpec.toShape(): Shape =
    if (cookie) MaterialShapes.Cookie9Sided.toShape()
    else if (radiusDp >= 1000f) CircleShape
    else androidx.compose.foundation.shape.RoundedCornerShape(radiusDp.dp)
