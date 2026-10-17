package com.rotatsulabs.specty.ui.egg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val CRACK_MS = 340f
private const val FALL_MS = 1500f
private const val RAYS = 14
private val RING_FRACTIONS = floatArrayOf(0f, 0.07f, 0.17f, 0.32f, 0.55f, 1f)

private class Shard(
    val path: Path,
    val centre: Offset,
    val delayMs: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val tint: Float,
)

/**
 * The screen freezes, cracks spread out from [impact], then every shard tumbles away under gravity.
 * [onShatter] fires when the shards start to fall (the game should be visible by then); [onDone] when none are left.
 */
@Composable
fun GlassBreak(
    snapshot: ImageBitmap,
    impact: Offset,
    onShatter: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = remember { Animatable(0f) }
    val total = CRACK_MS + FALL_MS + 400f
    LaunchedEffect(Unit) {
        // The shards start to fall once the cracks have finished spreading.
        launch { delay((CRACK_MS - 60f).toLong()); onShatter() }
        clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
        onDone()
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val point = if (impact == Offset.Zero) Offset(w * 0.2f, h * 0.08f) else impact
        val shards = remember(w, h, point) { buildShards(w, h, point) }
        val reach = remember(w, h, point) { farthestCorner(w, h, point) }

        Canvas(Modifier.fillMaxSize()) {
            val ms = clock.value
            if (ms < CRACK_MS) {
                val p = (ms / CRACK_MS).coerceIn(0f, 1f)
                val spread = 1f - (1f - p) * (1f - p)
                drawImage(snapshot)
                val circle = Path().apply { addOval(Rect(point, reach * spread)) }
                clipPath(circle) {
                    for (s in shards) {
                        drawPath(s.path, Color.White.copy(alpha = 0.75f), style = Stroke(width = 2.2f))
                        drawPath(s.path, Color.Black.copy(alpha = 0.25f), style = Stroke(width = 0.8f))
                    }
                }
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.7f * (1f - p)), Color.Transparent),
                        center = point,
                        radius = reach * 0.45f,
                    ),
                )
            } else {
                val fall = ms - CRACK_MS
                for (s in shards) {
                    val t = ((fall - s.delayMs) / 1000f).coerceAtLeast(0f)
                    val fade = ((t - 0.45f) / 0.75f).coerceIn(0f, 1f)
                    val alpha = 1f - fade * fade
                    if (alpha <= 0.01f) continue
                    val dx = s.vx * t
                    val dy = s.vy * t + 0.5f * h * 2.6f * t * t
                    val shrink = 1f - 0.25f * fade
                    withTransform({
                        translate(dx, dy)
                        rotate(s.spin * t, s.centre)
                        scale(shrink, shrink, s.centre)
                    }) {
                        clipPath(s.path) { drawImage(snapshot, alpha = alpha) }
                        drawPath(s.path, Color.White.copy(alpha = s.tint * alpha))
                        drawPath(s.path, Color.White.copy(alpha = 0.5f * alpha), style = Stroke(width = 1.6f))
                    }
                }
            }
        }
    }
}

private fun farthestCorner(w: Float, h: Float, p: Offset): Float =
    hypot(max(p.x, w - p.x), max(p.y, h - p.y)) * 1.05f

/** Radial glass: rays out of the impact point crossed by rings, with jittered corners so no two shards match. */
private fun buildShards(w: Float, h: Float, impact: Offset): List<Shard> {
    val random = Random(7)
    val reach = farthestCorner(w, h, impact)
    val step = (2.0 * PI / RAYS).toFloat()
    val angles = FloatArray(RAYS) { it * step + (random.nextFloat() - 0.5f) * step * 0.5f }

    // corners[ring][ray]; ring 0 is the impact point itself.
    val corners = Array(RING_FRACTIONS.size) { ring ->
        Array(RAYS) { ray ->
            if (ring == 0) {
                impact
            } else {
                val jitter = 1f + (random.nextFloat() - 0.5f) * 0.16f
                val r = reach * RING_FRACTIONS[ring] * jitter
                Offset(impact.x + cos(angles[ray]) * r, impact.y + sin(angles[ray]) * r)
            }
        }
    }

    val shards = ArrayList<Shard>()
    for (ring in 0 until RING_FRACTIONS.size - 1) {
        for (ray in 0 until RAYS) {
            val next = (ray + 1) % RAYS
            val points = if (ring == 0) {
                listOf(corners[0][ray], corners[1][ray], corners[1][next])
            } else {
                listOf(corners[ring][ray], corners[ring + 1][ray], corners[ring + 1][next], corners[ring][next])
            }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                close()
            }
            val centre = Offset(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
            val away = centre - impact
            val distance = away.getDistance().coerceAtLeast(1f)
            val speed = w * (0.08f + random.nextFloat() * 0.22f)
            shards += Shard(
                path = path,
                centre = centre,
                delayMs = distance / reach * 260f + random.nextFloat() * 70f,
                vx = away.x / distance * speed,
                vy = away.y / distance * speed * 0.5f - h * 0.05f,
                spin = (random.nextFloat() - 0.5f) * 2f * 220f,
                tint = random.nextFloat() * 0.12f,
            )
        }
    }
    return shards
}
