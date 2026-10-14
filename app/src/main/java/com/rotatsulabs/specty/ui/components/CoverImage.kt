package com.rotatsulabs.specty.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
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

/** Cover art for [uri], cross-fading from [fallback] once it has loaded. */
@Composable
fun CoverImage(
    uri: Uri,
    shape: Shape,
    modifier: Modifier = Modifier,
    sizePx: Int = 256,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(CoverArt.peek(uri, sizePx), uri, sizePx) {
        value = CoverArt.load(context, uri, sizePx)
    }
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        Crossfade(targetState = bitmap, label = "cover") { b ->
            if (b != null) {
                Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() }
            }
        }
    }
}
