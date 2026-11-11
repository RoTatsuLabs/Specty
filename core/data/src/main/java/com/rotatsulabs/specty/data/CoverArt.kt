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
    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    // A cover that failed to load is not retried for a while, but not for ever: a busy device can fail a load that works a moment later.
    private val misses = HashMap<String, Long>()
    private val lock = Any()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(4)

    private fun key(uri: Uri, sizePx: Int) = "$uri@$sizePx"

    fun peek(uri: Uri, sizePx: Int): Bitmap? = synchronized(lock) { cache.get(key(uri, sizePx)) }

    private val sizes = intArrayOf(1024, 768, 512, 360, 256, 180, 156, 128, 96)

    /** The cover at the size asked for, else the largest one already decoded for any list, so a bigger view never starts empty. */
    fun peekBest(uri: Uri, sizePx: Int): Bitmap? {
        peek(uri, sizePx)?.let { return it }
        for (size in sizes) peek(uri, size)?.let { return it }
        return null
    }

    suspend fun load(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val k = key(uri, sizePx)
        synchronized(lock) {
            cache.get(k)?.let { return it }
            val missed = misses[k]
            if (missed != null && System.currentTimeMillis() - missed < RETRY_MS) return null
        }
        val bitmap = withContext(io) { fromThumbnail(context, uri, sizePx) ?: fromEmbedded(context, uri, sizePx) }
        synchronized(lock) {
            if (bitmap != null) {
                cache.put(k, bitmap)
                misses.remove(k)
            } else {
                misses[k] = System.currentTimeMillis()
            }
        }
        return bitmap
    }

    /** True once [uri] is known to have no art at [sizePx], so a view can show its own mark instead of a blank. */
    fun missed(uri: Uri, sizePx: Int): Boolean = synchronized(lock) { misses.containsKey(key(uri, sizePx)) }

    /** Decodes covers ahead of the scroll, a few at a time, so a fast fling meets them already in memory. */
    suspend fun prefetch(context: Context, uris: List<Uri>, sizePx: Int) {
        for (uri in uris) {
            if (peek(uri, sizePx) != null) continue
            load(context, uri, sizePx)
        }
    }

    private const val RETRY_MS = 15_000L

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
