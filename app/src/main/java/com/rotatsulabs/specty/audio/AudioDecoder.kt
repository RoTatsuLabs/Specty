package com.rotatsulabs.specty.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Decodes the first audio stream to raw interleaved f32le PCM at the file's native rate and layout. */
object AudioDecoder {
    suspend fun decode(
        context: Context,
        uri: Uri,
        durationSec: Double,
        output: File,
        maxBytes: Long = 0L,
        onProgress: (Float) -> Unit,
    ) {
        val saf = FFmpegKitConfig.getSafParameterForRead(context, uri)
            ?: throw IOException("Cannot open this file")
        val args = buildList {
            addAll(listOf("-hide_banner", "-nostdin", "-y", "-i", saf, "-map", "0:a:0", "-vn", "-sn", "-dn"))
            addAll(listOf("-f", "f32le", "-acodec", "pcm_f32le"))
            if (maxBytes > 0) addAll(listOf("-fs", maxBytes.toString()))
            add(output.absolutePath)
        }.toTypedArray()

        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                args,
                { finished ->
                    if (!continuation.isActive) return@executeWithArgumentsAsync
                    if (ReturnCode.isSuccess(finished.returnCode)) {
                        continuation.resume(Unit)
                    } else {
                        val detail = finished.failStackTrace ?: finished.output?.takeLast(300).orEmpty()
                        continuation.resumeWithException(IOException("Decoding failed. $detail".trim()))
                    }
                },
                { /* log output is not needed */ },
                { statistics ->
                    if (durationSec > 0) {
                        val seconds = statistics.time.toDouble() / 1000.0
                        onProgress((seconds / durationSec).toFloat().coerceIn(0f, 1f))
                    }
                },
            )
            continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
        }
    }
}
