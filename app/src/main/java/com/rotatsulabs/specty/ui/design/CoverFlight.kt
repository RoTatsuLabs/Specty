package com.rotatsulabs.specty.ui.design

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.rotatsulabs.specty.data.CoverArt
import com.rotatsulabs.specty.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Where a tapped cover sits, how round it is and which bitmap size the list already has cached. */
class CoverSource(val bounds: Rect?, val corners: Corners, val sizePx: Int)

/**
 * Shared cover art between the library and the analysis screen. The library records the tapped cover, the analysis
 * hero reports its own position, and [CoverFlightLayer] draws one image that travels between the two while both
 * real covers stay hidden.
 */
@Stable
class CoverFlight {
    var uri: Uri? by mutableStateOf(null)
        private set
    var flying: Boolean by mutableStateOf(false)
    internal var source: CoverSource? = null
    internal var target: LayoutCoordinates? = null
    internal var lastTarget: Rect? = null

    fun begin(uri: Uri, source: CoverSource?) {
        this.source = source?.takeIf { it.bounds != null }
        this.uri = uri
        lastTarget = null
    }

    /** Forget the source, so the next open (for example from the dock) uses the plain container transform. */
    fun clear() {
        source = null
        lastTarget = null
    }

    /** True when [candidate] is the cover that will fly, so its destination should not animate in by itself. */
    fun shares(candidate: Uri) = source != null && uri == candidate

    /** True while [candidate]'s real cover must stay invisible because the flying copy stands in for it. */
    fun hides(candidate: Uri) = flying && uri == candidate
}

/** Marks the analysis cover as the landing spot of the flight and hides it while the flying copy is on screen. */
fun Modifier.coverTarget(flight: CoverFlight?, uri: Uri): Modifier =
    if (flight == null) {
        this
    } else {
        this
            .onGloballyPositioned { flight.target = it }
            .graphicsLayer { alpha = if (flight.hides(uri)) 0f else 1f }
    }

private const val HERO_PX = 768

/**
 * Runs the flight: forward while the analysis is [open], back when it closes. [currentUri] is the track the analysis
 * shows, so a stale tap on another track never flies.
 */
@Composable
fun CoverFlightLayer(flight: CoverFlight, open: Boolean, currentUri: Uri?, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open) {
        val uri = flight.uri
        if (uri == null || uri != currentUri || flight.source == null) {
            flight.flying = false
            progress.snapTo(if (open) 1f else 0f)
            return@LaunchedEffect
        }
        flight.flying = true
        progress.animateTo(if (open) 1f else 0f, Motion.spatial())
        // Let the analysis cover finish loading so the hand-over never shows its placeholder.
        if (open) withTimeoutOrNull(300) { while (CoverArt.peek(uri, HERO_PX) == null) delay(16) }
        flight.flying = false
    }
    val uri = flight.uri
    if (flight.flying && uri != null) FlyingCover(flight, uri, { progress.value }, modifier)
}

@Composable
private fun FlyingCover(flight: CoverFlight, uri: Uri, progress: () -> Float, modifier: Modifier) {
    val source = flight.source ?: return
    val from = source.bounds ?: return
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val bitmap by produceState<Bitmap?>(CoverArt.peek(uri, HERO_PX) ?: CoverArt.peek(uri, source.sizePx), uri) {
        value = CoverArt.load(context, uri, HERO_PX) ?: value
    }
    val live = flight.target?.takeIf { it.isAttached }?.boundsInRoot()
    if (live != null) flight.lastTarget = live
    val to = live ?: flight.lastTarget ?: from
    val p = progress()
    val left = from.left + (to.left - from.left) * p
    val top = from.top + (to.top - from.top) * p
    val width = (from.width + (to.width - from.width) * p).coerceAtLeast(1f)
    val height = (from.height + (to.height - from.height) * p).coerceAtLeast(1f)
    val shape = source.corners.lerpTo(SpectyShapes.Feature, p.coerceIn(0f, 1f)).toShape()
    Box(
        modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .clip(shape),
    ) {
        val image = bitmap
        if (image != null) {
            Image(image.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer))
        }
    }
}
