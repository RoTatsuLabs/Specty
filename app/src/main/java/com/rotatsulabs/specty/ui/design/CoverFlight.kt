package com.rotatsulabs.specty.ui.design

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import com.rotatsulabs.specty.data.CoverArt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * The outline of a cover: the cookie, or a rounded rectangle. The radius is in dp so the same spec describes the same
 * look at any size; a radius larger than half the cover makes it a circle.
 */
@Stable
class CoverSpec private constructor(val cookie: Boolean, val radiusDp: Float) {
    companion object {
        val Cookie = CoverSpec(true, 0f)
        val Circle = CoverSpec(false, 10_000f)
        val Square = CoverSpec(false, 0f)
        fun rounded(radiusDp: Float) = CoverSpec(false, radiusDp)
    }

    /** The outline as a polygon for a cover whose short side is [shortSidePx], so two specs can morph into each other. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    internal fun polygon(shortSidePx: Float, density: Float): RoundedPolygon {
        if (cookie) return MaterialShapes.Cookie9Sided.normalized()
        val fraction = (radiusDp * density / shortSidePx.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
        val rounding = if (fraction <= 0.001f) CornerRounding.Unrounded else CornerRounding(2f * fraction)
        return RoundedPolygon.rectangle(2f, 2f, rounding).normalized()
    }
}

/** Where a tapped cover sits, what shape it has and which bitmap size the list already has cached. */
class CoverSource(val bounds: Rect?, val shape: CoverSpec, val sizePx: Int)

/**
 * The cover that travels between a list and a page. The list records the tapped cover, the page's own cover reports
 * where it will rest and in what shape, and [CoverFlightLayer] draws a single image that springs from one to the
 * other, morphing its outline as it goes, while both real covers stay hidden. Closing the page runs it backwards.
 */
@Stable
class CoverFlight {
    var uri: Uri? by mutableStateOf(null)
        private set

    /** True while the travelling copy is on screen and the real covers must stay invisible. */
    var flying: Boolean by mutableStateOf(false)
        internal set

    internal var source: CoverSource? = null
    internal var target: Rect? by mutableStateOf(null)

    /** Where the page's cover stood when it began to leave, which the flight home starts from. */
    internal var anchor: Rect? = null
    internal var targetShape: CoverSpec = CoverSpec.Square
    internal var targetPx: Int = 768
    internal val progress = Animatable(0f)

    fun begin(uri: Uri, source: CoverSource?) {
        this.source = source?.takeIf { it.bounds != null }
        this.uri = uri
        target = null
    }

    /** Forget the source, so the next open (from the dock, say) has no cover to follow. */
    fun clear() {
        source = null
        target = null
    }

    /** True when [candidate] is the cover that will fly, so nothing else should animate its arrival. */
    fun shares(candidate: Uri) = source != null && uri == candidate

    /** True while [candidate]'s real cover is invisible because the travelling copy stands in for it. */
    fun hides(candidate: Uri) = flying && uri == candidate

    /** True from the tap until the flight has landed: whatever should appear after the cover (a title) waits for this to end. */
    fun pending(candidate: Uri) = (flying || waiting) && uri == candidate
}

/** Marks a page's cover as where the flight lands, in [shape], and hides it while the travelling copy is on screen. */
fun Modifier.coverTarget(flight: CoverFlight?, uri: Uri, shape: CoverSpec, sizePx: Int): Modifier =
    if (flight == null) {
        this
    } else {
        this
            .onGloballyPositioned {
                flight.targetShape = shape
                flight.targetPx = sizePx
                flight.target = it.boundsInRoot()
            }
            .graphicsLayer { alpha = if (flight.hides(uri)) 0f else 1f }
    }

/**
 * Runs the flight: forward when the page [open]s, back when it closes. [currentUri] is the track the page shows, so a
 * stale tap on another track never flies.
 */
@Composable
fun CoverFlightLayer(flight: CoverFlight, open: Boolean, currentUri: Uri?, pose: OverlayPose? = null, modifier: Modifier = Modifier) {
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        val uri = flight.uri
        val source = flight.source
        if (open) {
            wasOpen = true
            if (uri == null || uri != currentUri || source == null) {
                flight.flying = false
                return@LaunchedEffect
            }
            flight.flying = true
            flight.progress.snapTo(0f)
            // The page cover reports where it will rest once the page has been laid out.
            val landed = withTimeoutOrNull(500) { snapshotFlow { flight.target }.first { it != null } }
            if (landed == null) {
                flight.flying = false
                return@LaunchedEffect
            }
            // The same bitmap the page cover starts from, so handing over never changes the picture.
            CoverArt.load(context, uri, flight.targetPx)
            flight.progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 300f))
            flight.flying = false
        } else if (wasOpen) {
            wasOpen = false
            val from = flight.target
            if (uri == null || source == null || from == null) {
                flight.flying = false
                return@LaunchedEffect
            }
            // Start where the page's own cover is drawn this instant: a swipe may already have shrunk the page.
            flight.anchor = pose?.map(from) ?: from
            flight.flying = true
            flight.progress.snapTo(1f)
            flight.progress.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 380f))
            flight.flying = false
            flight.anchor = null
        }
    }
    val uri = flight.uri
    if (flight.flying && uri != null) TravellingCover(flight, uri, modifier)
}

@Composable
private fun TravellingCover(flight: CoverFlight, uri: Uri, modifier: Modifier) {
    val source = flight.source ?: return
    val from = source.bounds ?: return
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current.density
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val bitmap by produceState<Bitmap?>(CoverArt.peekBest(uri, flight.targetPx), uri) {
        value = CoverArt.load(context, uri, flight.targetPx) ?: value
    }
    // The outline morphs from the list cover's shape to the page cover's, built once for this flight.
    val morph = remember(uri, source, flight.targetShape) {
        val to = flight.target ?: from
        Morph(
            source.shape.polygon(minOf(from.width, from.height), density),
            flight.targetShape.polygon(minOf(to.width, to.height), density),
        )
    }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier.fillMaxSize()) {
        val to = flight.anchor ?: flight.target ?: from
        val p = flight.progress.value
        // The position and size follow the spring, overshoot included; the outline stays within its two shapes.
        val left = from.left + (to.left - from.left) * p
        val top = from.top + (to.top - from.top) * p
        val width = (from.width + (to.width - from.width) * p).coerceAtLeast(1f)
        val height = (from.height + (to.height - from.height) * p).coerceAtLeast(1f)
        path.rewind()
        morph.toPath(p.coerceIn(0f, 1f), path)
        matrix.setScale(width, height)
        matrix.postTranslate(left, top)
        path.transform(matrix)
        clipPath(path.asComposePath()) {
            val image = bitmap
            if (image == null) {
                drawRect(tertiary, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(width, height))
            } else {
                // Fill the box the way a cropped image does: the largest centred part with the box's proportions.
                val box = width / height
                val whole = image.width.toFloat() / image.height
                val cropW = if (whole > box) image.height * box else image.width.toFloat()
                val cropH = if (whole > box) image.height.toFloat() else image.width / box
                drawImage(
                    image.asImageBitmap(),
                    srcOffset = IntOffset(((image.width - cropW) / 2f).roundToInt(), ((image.height - cropH) / 2f).roundToInt()),
                    srcSize = IntSize(cropW.roundToInt(), cropH.roundToInt()),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }
}
