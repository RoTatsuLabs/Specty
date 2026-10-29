package com.rotatsulabs.specty.ui.design

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.rotatsulabs.specty.ui.theme.MotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A radial colour reveal for theme changes. The screen as it is now is captured and laid over the app, the new
 * theme is applied underneath, and a circle grows from the touched point to uncover the new colours.
 */
@Stable
class ThemeReveal internal constructor(
    internal val layer: GraphicsLayer,
    private val scope: CoroutineScope,
) {
    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set
    internal var center by mutableStateOf(Offset.Zero)
        private set
    internal val radius = Animatable(0f)

    /** Width of the soft edge of the circle, in pixels; set for each reveal. */
    internal var feather by mutableStateOf(0f)
        private set

    /** The new theme's accent, drawn as a ring on the edge so the spread is visible even between similar palettes. */
    internal var target by mutableStateOf(1f)
        private set

    internal var accent by mutableStateOf(Color.Unspecified)
    internal var size by mutableStateOf(IntSize.Zero)

    /** Where the last touch landed, for changes that are not made from a swatch. */
    var lastTouch: Offset = Offset.Unspecified
        internal set

    /** False when the system has animations turned off. */
    internal var enabled = true

    private var job: Job? = null

    /**
     * Runs [change] with a reveal growing from [origin] (the last touch when not given). If the capture is not
     * possible the change still happens, just without the effect.
     */
    fun start(origin: Offset = lastTouch, change: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            // A reveal still running is finished at once; this change draws over the current look.
            snapshot = null
            val area = size
            if (!enabled || area == IntSize.Zero) {
                change()
                return@launch
            }
            val picture = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { layer.toImageBitmap() }
            if (picture == null) {
                change()
                return@launch
            }
            val at = if (origin.isSpecified) origin else Offset(area.width / 2f, area.height / 2f)
            center = Offset(RevealMath.inside(at.x, area.width.toFloat()), RevealMath.inside(at.y, area.height.toFloat()))
            val reach = RevealMath.reach(center.x, center.y, area.width.toFloat(), area.height.toFloat())
            feather = reach * FEATHER_SHARE
            target = reach + feather
            radius.snapTo(0f)
            snapshot = picture
            change()
            try {
                // Runs on past the far corner by the feather width, so the soft edge leaves the screen before the old look is dropped.
                radius.animateTo(target, tween(DURATION_MS, easing = MotionTokens.Emphasized))
            } finally {
                snapshot = null
            }
        }
    }

    private companion object {
        const val DURATION_MS = 950
        const val FEATHER_SHARE = 0.22f
        const val CAPTURE_TIMEOUT_MS = 500L
    }
}

/** The reveal for the app, or null where there is none (previews). */
val LocalThemeReveal = compositionLocalOf<ThemeReveal?> { null }

@Composable
fun rememberThemeReveal(): ThemeReveal {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(layer, scope) {
        ThemeReveal(layer, scope).also {
            it.enabled = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
    }
}

/** Wraps the whole app: records it so it can be captured, remembers touches, and draws the reveal on top. */
@Composable
fun ThemeRevealHost(reveal: ThemeReveal, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { reveal.size = it }
            .pointerInput(reveal) {
                // Only watches: the touch still reaches whatever was pressed.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    reveal.lastTouch = down.position
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    reveal.layer.record { this@drawWithContent.drawContent() }
                    drawLayer(reveal.layer)
                },
        ) { content() }

        val picture = reveal.snapshot
        if (picture != null) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                // The old look, with a soft-edged circle erased from it so the new theme shows through and spreads.
                drawImage(picture)
                val r = reveal.radius.value
                if (r > 1f) {
                    val feather = reveal.feather.coerceAtMost(r)
                    val inner = ((r - feather) / r).coerceIn(0f, 0.999f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(0f to Color.Black, inner to Color.Black, 1f to Color.Transparent),
                            center = reveal.center,
                            radius = r,
                        ),
                        radius = r,
                        center = reveal.center,
                        blendMode = BlendMode.DstOut,
                    )
                    val accent = reveal.accent
                    if (accent.isSpecified && reveal.feather > 0f) {
                        val progress = r / reveal.target
                        drawCircle(
                            accent.copy(alpha = 0.55f * (1f - progress).coerceIn(0f, 1f)),
                            radius = (r - feather / 2f).coerceAtLeast(0f),
                            center = reveal.center,
                            style = Stroke(width = 5.dp.toPx().coerceAtLeast(feather * 0.12f)),
                        )
                    }
                }
            }
        }
    }
}

/** Tells the reveal which accent the current theme has; place it inside the theme. */
@Composable
fun ThemeRevealAccent(reveal: ThemeReveal) {
    val accent = MaterialTheme.colorScheme.primary
    SideEffect { reveal.accent = accent }
}
