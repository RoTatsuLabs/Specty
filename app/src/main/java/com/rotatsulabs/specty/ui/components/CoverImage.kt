package com.rotatsulabs.specty.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.rotatsulabs.specty.data.CoverArt

/**
 * Cover art for [uri]. While it loads the view shows only a quiet tone, never an icon; [fallback] is for a track that
 * truly has no art, and the cover cross-fades in from either.
 */
@Composable
fun CoverImage(
    uri: Uri,
    shape: Shape,
    modifier: Modifier = Modifier,
    sizePx: Int = 256,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val state by produceState(CoverState(CoverArt.peekBest(uri, sizePx), false), uri, sizePx) {
        val cached = CoverArt.peekBest(uri, sizePx)
        value = CoverState(cached, false)
        val loaded = CoverArt.load(context, uri, sizePx)
        value = if (loaded != null) CoverState(loaded, false) else CoverState(cached, cached == null)
    }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Crossfade(targetState = state, animationSpec = androidx.compose.animation.core.tween(160), label = "cover") { s ->
            val b = s.bitmap
            when {
                b != null -> Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                s.none -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() }
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

private data class CoverState(val bitmap: Bitmap?, val none: Boolean)
