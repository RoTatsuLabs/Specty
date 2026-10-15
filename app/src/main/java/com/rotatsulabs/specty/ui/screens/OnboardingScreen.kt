package com.rotatsulabs.specty.ui.screens

import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rotatsulabs.specty.ui.components.pressScale
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramPalette
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramRenderer
import com.rotatsulabs.specty.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val PAGES = 4

/**
 * First-run introduction: four swipeable pages with small looping illustrations, ending with the library
 * permission. [onFinish] is called when the person leaves it by any route; [onPermission] after the system
 * dialog so the library can reload.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, onPermission: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        onPermission(ok)
    }

    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onFinish()
    }

    Column(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // Swallow touches so nothing underneath reacts while the intro is up.
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            val last = pager.currentPage == PAGES - 1
            if (!last) ExpressiveButton("Skip", onFinish, kind = ButtonKind.Text)
        }

        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            val active = pager.currentPage == page
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.graphicsLayer {
                        translationX = offset * -140f
                        val s = 1f - abs(offset) * 0.2f
                        scaleX = s; scaleY = s
                        alpha = 1f - abs(offset).coerceAtMost(1f)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        0 -> BarsArt()
                        1 -> SpectrumArt()
                        2 -> VerdictArt()
                        else -> NumbersArt(active)
                    }
                }
                Spacer(Modifier.height(36.dp))
                Column(
                    Modifier.graphicsLayer { alpha = 1f - abs(offset).coerceAtMost(1f); translationX = offset * -60f },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val (title, body) = copy(page)
                    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    if (page == PAGES - 1) {
                        Spacer(Modifier.height(8.dp))
                        PermissionBlock(granted, onAllow = { launcher.launch(Manifest.permission.READ_MEDIA_AUDIO) })
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(PAGES) { i -> Dot(selected = pager.currentPage == i) }
            }
            val last = pager.currentPage == PAGES - 1
            ExpressiveButton(
                text = if (last) "Get started" else "Next",
                onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                icon = if (last) null else Icons.AutoMirrored.Rounded.ArrowForward,
                corners = Corners(topStart = 28.dp, topEnd = 14.dp, bottomEnd = 28.dp, bottomStart = 28.dp),
            )
        }
    }
}

private fun copy(page: Int): Pair<String, String> = when (page) {
    0 -> "Meet Specty" to "A quality lab for your music. Look inside any audio file and see what it really contains."
    1 -> "See the spectrum" to "The spectrogram shows every frequency over time. Pinch it open fullscreen, zoom into the top end and touch it to read exact values."
    2 -> "Catch fake lossless" to "A lossless file made from an MP3 keeps the MP3's cutoff. Specty finds that edge, tells you how sure it is and which encoder it resembles."
    else -> "Measure everything" to "Loudness, true peak, dynamic range, clipping and stereo health, plus a scan of your whole library in the background. All of it runs on your device."
}

@Composable
private fun Dot(selected: Boolean) {
    val width by animateDpAsState(if (selected) 30.dp else 9.dp, Motion.spatial(), label = "dotWidth")
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        tween(250), label = "dotColor",
    )
    Box(Modifier.size(width, 9.dp).clip(CircleShape).background(color))
}

@Composable
private fun PermissionBlock(granted: Boolean, onAllow: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    MorphSurface(Modifier.fillMaxWidth(), Corners(24.dp), scheme.surfaceContainerHigh, morphOnPress = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = scheme.primary)
                    Text("Library access allowed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text("Needed to list your music. Files never leave your device.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                ExpressiveButton("Allow library access", onAllow, kind = ButtonKind.Tonal)
            }
        }
    }
}

// ---- illustrations -----------------------------------------------------------------------------------

private val artShape = RoundedCornerShape(topStart = 72.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 72.dp)

@Composable
private fun BarsArt() {
    val transition = rememberInfiniteTransition(label = "bars")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val bars = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = artShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(220.dp)) {
        Canvas(Modifier.fillMaxSize().padding(48.dp)) {
            val count = 5
            val gap = size.width * 0.1f
            val w = (size.width - gap * (count - 1)) / count
            for (i in 0 until count) {
                val h = 0.25f + 0.75f * abs(sin(phase * 2f * PI.toFloat() + i * 0.8f))
                val bh = size.height * h
                drawRoundRect(bars, Offset(i * (w + gap), (size.height - bh) / 2f), Size(w, bh), CornerRadius(w / 2f))
            }
        }
    }
}

/** A drifting heat map whose top end stops at a dashed line, the way a transcoded file looks. */
@Composable
private fun SpectrumArt() {
    val transition = rememberInfiniteTransition(label = "spectrum")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val lut = remember { SpectrogramRenderer.lut(SpectrogramPalette.Inferno) }
    val label = MaterialTheme.colorScheme.tertiaryContainer
    val onLabel = MaterialTheme.colorScheme.onTertiaryContainer
    Box(contentAlignment = Alignment.TopEnd) {
        Surface(shape = artShape, color = Color.Black, modifier = Modifier.size(width = 300.dp, height = 200.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val cols = 30
                val rows = 16
                val cutRow = 11
                val cw = size.width / cols
                val ch = size.height / rows
                for (c in 0 until cols) {
                    for (r in 0 until rows) {
                        val t = c * 0.55f + phase * 2f * PI.toFloat() * 3f
                        val base = 0.95f - r * 0.065f
                        val wobble = 0.18f * sin(t + r * 0.9f) + 0.1f * sin(t * 0.5f - r * 1.7f)
                        val level = if (r >= cutRow) 0.02f else (base + wobble).coerceIn(0f, 1f)
                        drawRect(
                            Color(lut[(level * 255).toInt().coerceIn(0, 255)]),
                            Offset(c * cw, size.height - (r + 1) * ch),
                            Size(cw + 1f, ch + 1f),
                        )
                    }
                }
                val y = size.height - cutRow * ch
                drawLine(Color.White, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
            }
        }
        Surface(shape = CircleShape, color = label, contentColor = onLabel, modifier = Modifier.padding(top = 56.dp, end = 14.dp)) {
            Text("16 kHz cutoff", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

/** A sample verdict card that flips between a clean file and a suspicious one. */
@Composable
private fun VerdictArt() {
    var genuine by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2600)
            genuine = !genuine
        }
    }
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (genuine) scheme.tertiaryContainer else scheme.errorContainer, tween(400), label = "verdictBg")
    val content by animateColorAsState(if (genuine) scheme.onTertiaryContainer else scheme.onErrorContainer, tween(400), label = "verdictFg")
    val confidence by animateFloatAsState(if (genuine) 0.94f else 0.88f, Motion.spatial(), label = "confidence")
    Surface(shape = artShape, color = container, contentColor = content, modifier = Modifier.size(width = 300.dp, height = 200.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.Start) {
            AnimatedContent(
                targetState = genuine,
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "verdictText",
            ) { isGenuine ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (isGenuine) Icons.Rounded.Verified else Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(30.dp))
                    Column {
                        Text(if (isGenuine) "Genuine full-band" else "Likely transcoded", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (isGenuine) "Content reaches 22 kHz" else "Hard cutoff near 16 kHz", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${(confidence * 100).toInt()}% confidence", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                drawRoundRect(content.copy(alpha = 0.2f), size = size, cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(content, size = Size(size.width * confidence, size.height), cornerRadius = CornerRadius(size.height / 2))
            }
        }
    }
}

/** Three headline numbers counting up each time the page comes into view. */
@Composable
private fun NumbersArt(active: Boolean) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1200, delayMillis = 200, easing = FastOutSlowInEasing))
        }
    }
    val scheme = MaterialTheme.colorScheme
    val outer = 32.dp
    val inner = 10.dp
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Tile("Loudness", String.format(Locale.US, "%.1f", -14.2 * progress.value), "LUFS", scheme.secondaryContainer, scheme.onSecondaryContainer,
            RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner))
        Tile("Dynamics", "DR${(12 * progress.value).toInt()}", "meter", scheme.primaryContainer, scheme.onPrimaryContainer, RoundedCornerShape(inner))
        Tile("True peak", String.format(Locale.US, "%.1f", -0.8 * progress.value), "dBTP", scheme.tertiaryContainer, scheme.onTertiaryContainer,
            RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer))
    }
}

@Composable
private fun Tile(label: String, value: String, unit: String, container: Color, content: Color, shape: RoundedCornerShape, width: Dp = 104.dp) {
    Surface(shape = shape, color = container, contentColor = content, modifier = Modifier.size(width = width, height = 128.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(unit, style = MaterialTheme.typography.labelMedium)
        }
    }
}
