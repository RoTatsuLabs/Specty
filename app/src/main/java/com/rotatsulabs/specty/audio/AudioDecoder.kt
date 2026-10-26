package com.rotatsulabs.specty.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Decodes the first audio stream to raw interleaved f32le PCM at the decoder's own rate and layout, and returns the
 * rate and layout FFmpeg reports for that output (null if its log does not show them).
 */
object AudioDecoder {
    suspend fun decode(
        context: Context,
        uri: Uri,
        durationSec: Double,
        output: File,
        maxBytes: Long = 0L,
        onProgress: (Float) -> Unit,
    ): DecodedFormat? {
        val saf = runCatching { FFmpegKitConfig.getSafParameterForRead(context, uri) }.getOrNull()
            ?: throw AnalysisFailure(Failure(FailKey.CANNOT_OPEN))
        val args = buildList {
            addAll(listOf("-hide_banner", "-nostdin", "-y", "-i", saf, "-map", "0:a:0", "-vn", "-sn", "-dn"))
            addAll(listOf("-f", "f32le", "-acodec", "pcm_f32le"))
            if (maxBytes > 0) addAll(listOf("-fs", maxBytes.toString()))
            add(output.absolutePath)
        }.toTypedArray()

        var running: com.arthenica.ffmpegkit.Session? = null
        try {
            return decodeSession(args, durationSec, onProgress) { running = it }
        } catch (e: CancellationException) {
            // FFmpeg stops asynchronously. Wait until it has, so it is no longer writing to the temp file the caller is
            // about to delete and the next file's decode never overlaps with this one.
            withContext(NonCancellable) {
                running?.let { session ->
                    FFmpegKit.cancel(session.getSessionId())
                    var waited = 0L
                    while (session.getState().let { it != SessionState.COMPLETED && it != SessionState.FAILED } && waited < STOP_WAIT_MS) {
                        delay(50)
                        waited += 50
                    }
                }
            }
            throw e
        } finally {
            // Sessions keep their log text; a library scan makes thousands of them.
            if (running != null) FFmpegKitConfig.clearSessions()
        }
    }

    private suspend fun decodeSession(
        args: Array<String>,
        durationSec: Double,
        onProgress: (Float) -> Unit,
        onSession: (com.arthenica.ffmpegkit.Session) -> Unit,
    ): DecodedFormat? {
        return suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                args,
                { finished ->
                    if (!continuation.isActive) return@executeWithArgumentsAsync
                    if (ReturnCode.isSuccess(finished.getReturnCode())) {
                        continuation.resume(DecodedFormat.parse(finished.getOutput()))
                    } else {
                        val detail = finished.getFailStackTrace() ?: finished.getOutput().takeLast(300)
                        continuation.resumeWithException(AnalysisFailure(Failure(FailKey.DECODE_FAILED, detail)))
                    }
                },
                { /* log output is not needed */ },
                { statistics ->
                    if (durationSec > 0) {
                        val seconds = statistics.time / 1000.0
                        onProgress((seconds / durationSec).toFloat().coerceIn(0f, 1f))
                    }
                },
            )
            onSession(session)
            continuation.invokeOnCancellation { FFmpegKit.cancel(session.getSessionId()) }
        }
    }

    private const val STOP_WAIT_MS = 5_000L
}
