package com.rotatsulabs.specty.ui.egg

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.sound.Fx
import com.rotatsulabs.specty.sound.SoundFx
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val PREFS = "specty"
private const val KEY_BEST = "flappy_best"

private enum class Mode { Ready, Playing, Dead }

private class Pipe(var x: Float, val gapY: Float, var passed: Boolean = false)

/** Game state in pixels, scaled from the screen height so it plays the same on every phone. */
@Stable
private class FlappyState(best: Int, private val onBest: (Int) -> Unit) {
    var mode by mutableStateOf(Mode.Ready)
    var score by mutableIntStateOf(0)
    var best by mutableIntStateOf(best)
    var newBest by mutableStateOf(false)

    /** Read by the canvas so it redraws every frame. */
    val frame = mutableIntStateOf(0)

    var w = 1f
    var h = 1f
    var time = 0f
    var birdY = 0f
    var vy = 0f
    var deadFor = 0f
    var ground = 0f
    val pipes = ArrayList<Pipe>()
    private var placed = false
    private val random = Random(System.nanoTime())

    val groundH get() = h * 0.10f
    val birdR get() = h * 0.026f
    val birdX get() = w * 0.30f
    val pipeW get() = h * 0.10f
    val gap get() = h * 0.25f
    private val gravity get() = h * 2.7f
    private val flapSpeed get() = -h * 0.82f
    private val speed get() = h * (0.30f + min(score, 30) * 0.004f)

    fun resize(width: Float, height: Float) {
        w = width
        h = height
        if (!placed) {
            birdY = height * 0.45f
            placed = true
        }
    }

    fun flap() {
        when (mode) {
            Mode.Ready -> {
                reset()
                mode = Mode.Playing
                vy = flapSpeed
                SoundFx.play(Fx.Flap)
            }
            Mode.Playing -> {
                vy = flapSpeed
                SoundFx.play(Fx.Flap)
            }
            Mode.Dead -> if (deadFor > 0.5f) {
                reset()
                mode = Mode.Ready
            }
        }
    }

    private fun reset() {
        pipes.clear()
        score = 0
        newBest = false
        birdY = h * 0.45f
        vy = 0f
        deadFor = 0f
    }

    private fun spawn(x: Float) {
        val top = h * 0.12f + gap / 2
        val bottom = h - groundH - h * 0.12f - gap / 2
        pipes += Pipe(x, top + random.nextFloat() * (bottom - top))
    }

    fun step(dt: Float) {
        time += dt
        when (mode) {
            Mode.Ready -> {
                birdY = h * 0.45f + sin(time * 4f) * h * 0.012f
                ground = (ground + h * 0.15f * dt) % (h * 0.06f)
            }
            Mode.Playing -> play(dt)
            Mode.Dead -> {
                deadFor += dt
                vy = min(vy + gravity * dt, h * 1.3f)
                birdY = min(birdY + vy * dt, h - groundH - birdR)
            }
        }
        frame.intValue = frame.intValue + 1
    }

    private fun play(dt: Float) {
        vy = min(vy + gravity * dt, h * 1.3f)
        birdY += vy * dt
        if (birdY < birdR) {
            birdY = birdR
            vy = 0f
        }
        ground = (ground + speed * dt) % (h * 0.06f)

        for (p in pipes) p.x -= speed * dt
        pipes.removeAll { it.x + pipeW < 0f }
        if (pipes.isEmpty()) spawn(w + h * 0.2f) else if (pipes.last().x < w - h * 0.52f) spawn(w + pipeW)

        val r = birdR * 0.85f
        for (p in pipes) {
            if (!p.passed && p.x + pipeW < birdX - r) {
                p.passed = true
                score++
                SoundFx.play(Fx.Point)
            }
            val overlapsX = birdX + r > p.x && birdX - r < p.x + pipeW
            if (overlapsX && (birdY - r < p.gapY - gap / 2 || birdY + r > p.gapY + gap / 2)) {
                die()
                return
            }
        }
        if (birdY + birdR >= h - groundH) {
            birdY = h - groundH - birdR
            die()
        }
    }

    private fun die() {
        SoundFx.play(Fx.Hit)
        mode = Mode.Dead
        deadFor = 0f
        if (score > best) {
            best = score
            newBest = true
            onBest(score)
        }
    }
}

/** A small Flappy Bird clone drawn with the app's own colours. Tap to flap; the close button or back leaves. */
@Composable
fun FlappyGame(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val game = remember {
        FlappyState(prefs.getInt(KEY_BEST, 0)) { prefs.edit().putInt(KEY_BEST, it).apply() }
    }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(game) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            game.step(((now - last) / 1_000_000_000f).coerceAtMost(0.033f))
            last = now
        }
    }

    Surface(modifier.fillMaxSize(), color = scheme.primaryContainer) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(game) { detectTapGestures(onPress = { game.flap() }) },
            ) {
                game.resize(size.width, size.height)
                game.frame.intValue // read, so every step redraws
                drawWorld(game, scheme.primaryContainer, scheme.tertiaryContainer, scheme.surface, scheme.primary, scheme.tertiary, scheme.secondary, scheme.surfaceContainerHighest, scheme.outline)
            }

            Text(
                "${game.score}",
                style = MaterialTheme.typography.displayLarge,
                color = scheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp),
            )

            AnimatedVisibility(
                visible = game.mode == Mode.Ready,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text("Tap to flap", style = MaterialTheme.typography.titleLarge, color = scheme.onPrimaryContainer)
            }

            AnimatedVisibility(
                visible = game.mode == Mode.Dead,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn() + scaleIn(spring(dampingRatio = 0.55f, stiffness = 300f), initialScale = 0.6f),
                exit = fadeOut(),
            ) {
                Surface(shape = RoundedCornerShape(28.dp), color = scheme.surface, shadowElevation = 6.dp) {
                    Column(
                        Modifier.padding(horizontal = 36.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Game over", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (game.newBest) "New best: ${game.score}" else "Score ${game.score}  ·  Best ${game.best}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (game.newBest) scheme.primary else scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text("Tap to retry", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }

            ExpressiveIconButton(
                Icons.Rounded.Close,
                "Close game",
                onExit,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            )
        }
    }
}

private fun DrawScope.drawWorld(
    g: FlappyState,
    skyTop: Color,
    skyBottom: Color,
    cloud: Color,
    pipe: Color,
    body: Color,
    beak: Color,
    groundColor: Color,
    groundLine: Color,
) {
    drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom)))

    // Clouds drift slower than the pipes, which reads as depth.
    for (i in 0 until 4) {
        val span = g.w + g.h * 0.4f
        val x = ((i * 0.27f * span - g.time * g.h * 0.02f * (1 + i % 2)) % span + span) % span - g.h * 0.2f
        val y = g.h * (0.12f + 0.16f * i)
        val r = g.h * (0.035f + 0.01f * (i % 3))
        val c = cloud.copy(alpha = 0.45f)
        drawCircle(c, r, Offset(x, y))
        drawCircle(c, r * 0.8f, Offset(x + r * 1.1f, y + r * 0.2f))
        drawCircle(c, r * 0.7f, Offset(x - r * 1.0f, y + r * 0.3f))
    }

    val pipeDark = lerp(pipe, Color.Black, 0.25f)
    val capH = g.h * 0.035f
    val capExtra = g.pipeW * 0.1f
    val corner = CornerRadius(g.pipeW * 0.12f)
    for (p in g.pipes) {
        val top = p.gapY - g.gap / 2
        val bottom = p.gapY + g.gap / 2
        val floor = g.h - g.groundH
        drawRoundRect(pipe, Offset(p.x, -corner.x), Size(g.pipeW, top + corner.x), corner)
        drawRoundRect(pipe, Offset(p.x, bottom), Size(g.pipeW, floor - bottom), corner)
        drawRoundRect(pipeDark, Offset(p.x - capExtra, top - capH), Size(g.pipeW + capExtra * 2, capH), corner)
        drawRoundRect(pipeDark, Offset(p.x - capExtra, bottom), Size(g.pipeW + capExtra * 2, capH), corner)
        drawRect(Color.White.copy(alpha = 0.18f), Offset(p.x + g.pipeW * 0.14f, 0f), Size(g.pipeW * 0.12f, top - capH))
        drawRect(Color.White.copy(alpha = 0.18f), Offset(p.x + g.pipeW * 0.14f, bottom + capH), Size(g.pipeW * 0.12f, floor - bottom - capH))
    }

    // Ground with scrolling stripes.
    val top = g.h - g.groundH
    drawRect(groundColor, Offset(0f, top), Size(g.w, g.groundH))
    drawRect(groundLine, Offset(0f, top), Size(g.w, g.h * 0.004f))
    val stripe = g.h * 0.06f
    var sx = -g.ground
    while (sx < g.w) {
        val path = Path().apply {
            moveTo(sx, top + g.h * 0.012f)
            lineTo(sx + stripe * 0.5f, top + g.h * 0.012f)
            lineTo(sx + stripe * 0.5f - g.h * 0.02f, g.h)
            lineTo(sx - g.h * 0.02f, g.h)
            close()
        }
        drawPath(path, groundLine.copy(alpha = 0.18f))
        sx += stripe
    }

    // Bird: tilts with its speed, flaps its wing while alive.
    val tilt = (g.vy / g.h * 38f).coerceIn(-25f, 80f)
    val r = g.birdR
    val c = Offset(g.birdX, g.birdY)
    rotate(tilt, c) {
        drawCircle(body, r, c)
        drawCircle(Color.White.copy(alpha = 0.25f), r * 0.6f, Offset(c.x + r * 0.15f, c.y + r * 0.35f))
        val flap = if (g.mode == Mode.Dead) 0.3f else 0.55f + 0.45f * sin(g.time * 18f)
        drawOval(
            Color.Black.copy(alpha = 0.22f),
            Offset(c.x - r * 0.85f, c.y - r * 0.15f * flap),
            Size(r * 1.0f, r * 0.7f * max(flap, 0.2f)),
        )
        drawCircle(Color.White, r * 0.36f, Offset(c.x + r * 0.45f, c.y - r * 0.3f))
        drawCircle(Color.Black, r * 0.17f, Offset(c.x + r * 0.55f, c.y - r * 0.3f))
        val beakPath = Path().apply {
            moveTo(c.x + r * 0.8f, c.y - r * 0.05f)
            lineTo(c.x + r * 1.55f, c.y + r * 0.2f)
            lineTo(c.x + r * 0.8f, c.y + r * 0.5f)
            close()
        }
        drawPath(beakPath, beak)
    }
}
