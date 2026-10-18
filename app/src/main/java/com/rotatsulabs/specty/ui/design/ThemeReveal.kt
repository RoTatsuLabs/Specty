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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
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
            radius.snapTo(0f)
            snapshot = picture
            change()
            try {
                radius.animateTo(
                    RevealMath.reach(center.x, center.y, area.width.toFloat(), area.height.toFloat()),
                    tween(DURATION_MS, easing = MotionTokens.Emphasized),
                )
            } finally {
                snapshot = null
            }
        }
    }

    private companion object {
        const val DURATION_MS = 700
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
            Canvas(Modifier.fillMaxSize()) {
                // The old look everywhere except inside the growing circle, where the new theme shows through.
                val hole = Path().apply { addOval(Rect(reveal.center, reveal.radius.value)) }
                clipPath(hole, clipOp = ClipOp.Difference) { drawImage(picture) }
            }
        }
    }
}
