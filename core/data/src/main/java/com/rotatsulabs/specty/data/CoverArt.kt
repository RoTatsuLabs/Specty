package com.rotatsulabs.specty.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** Loads cover art for an audio uri: the MediaStore thumbnail first, then embedded tag art. */
object CoverArt {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val misses = HashSet<String>()
    private val lock = Any()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(4)

    private fun key(uri: Uri, sizePx: Int) = "$uri@$sizePx"

    fun peek(uri: Uri, sizePx: Int): Bitmap? = synchronized(lock) { cache.get(key(uri, sizePx)) }

    suspend fun load(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val k = key(uri, sizePx)
        synchronized(lock) {
            cache.get(k)?.let { return it }
            if (k in misses) return null
        }
        val bitmap = withContext(io) { fromThumbnail(context, uri, sizePx) ?: fromEmbedded(context, uri, sizePx) }
        synchronized(lock) {
            if (bitmap != null) cache.put(k, bitmap) else misses.add(k)
        }
        return bitmap
    }

    private fun fromThumbnail(context: Context, uri: Uri, sizePx: Int): Bitmap? =
        try {
            context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } catch (_: Exception) {
            null
        }

    private fun fromEmbedded(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
