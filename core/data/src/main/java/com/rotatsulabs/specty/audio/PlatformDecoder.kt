package com.rotatsulabs.specty.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes with the device's own codecs (MediaCodec) for formats FFmpeg cannot decode, such as Dolby AC-4.
 * Writes the same raw f32le PCM as [AudioDecoder]. Whether it works depends on the codecs the device ships.
 */
object PlatformDecoder {
    /** The device cannot decode this stream; the caller falls back to what the container alone shows. */
    class Unavailable(message: String, cause: Throwable? = null) : IOException(message, cause)

    private const val TIMEOUT_US = 10_000L

    /** Empty polls in a row, after the input ended, before the decoder is considered stuck (about 10 s). */
    private const val MAX_IDLE_POLLS = 1000

    fun decode(
        context: Context,
        uri: Uri,
        durationSec: Double,
        output: File,
        maxBytes: Long,
        ensureActive: () -> Unit,
        onProgress: (Float) -> Unit,
    ): DecodedFormat {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: Exception) {
                throw Unavailable("Android could not open this file", e)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw Unavailable("Android found no audio track in this file")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw Unavailable("The audio track has no type")
            val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
                ?: throw Unavailable("This device has no decoder for $mime")
            extractor.selectTrack(track)

            codec = open(name, format)
            var rate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, 0)
            var channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 0)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val buffers = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idle = 0
            var written = 0L

            output.outputStream().buffered(1 shl 16).use { out ->
                while (!outputDone) {
                    ensureActive()
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val input = codec.getInputBuffer(index) ?: throw Unavailable("The decoder gave no input buffer")
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val index = codec.dequeueOutputBuffer(buffers, TIMEOUT_US)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val changed = codec.outputFormat
                            rate = changed.intOr(MediaFormat.KEY_SAMPLE_RATE, rate)
                            channels = changed.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                            encoding = changed.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        }
                        index >= 0 -> {
                            idle = 0
                            val pcm = codec.getOutputBuffer(index)
                            if (pcm != null && buffers.size > 0) {
                                pcm.position(buffers.offset)
                                pcm.limit(buffers.offset + buffers.size)
                                written += write(out, pcm, encoding)
                            }
                            codec.releaseOutputBuffer(index, false)
                            if (buffers.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            if (maxBytes > 0 && written >= maxBytes) outputDone = true
                            if (durationSec > 0 && rate > 0 && channels > 0) {
                                val seconds = written / 4.0 / channels / rate
                                onProgress((seconds / durationSec).toFloat().coerceIn(0f, 1f))
                            }
                        }
                        else -> if (inputDone && ++idle > MAX_IDLE_POLLS) throw Unavailable("The decoder stopped producing audio")
                    }
                }
            }
            if (rate <= 0 || channels <= 0) throw Unavailable("The decoder did not report its output format")
            if (written == 0L) throw Unavailable("The decoder produced no audio")
            return DecodedFormat(rate, channels)
        } catch (e: Unavailable) {
            throw e
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Unavailable("The device decoder failed: ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Asks for float output; decoders that cannot give it are configured without and converted from 16-bit. */
    private fun open(name: String, format: MediaFormat): MediaCodec {
        for (float in listOf(true, false)) {
            val codec = MediaCodec.createByCodecName(name)
            try {
                val wanted = MediaFormat(format)
                if (float) wanted.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
                codec.configure(wanted, null, null, 0)
                codec.start()
                return codec
            } catch (e: Exception) {
                runCatching { codec.release() }
                if (!float) throw Unavailable("The device decoder could not start: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }
        throw Unavailable("The device decoder could not start")
    }

    /** Appends [pcm] to [out] as little-endian f32 and returns the bytes written. */
    private fun write(out: java.io.OutputStream, pcm: ByteBuffer, encoding: Int): Int {
        pcm.order(ByteOrder.LITTLE_ENDIAN)
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val bytes = ByteArray(pcm.remaining())
                pcm.get(bytes)
                out.write(bytes)
                bytes.size
            }
            AudioFormat.ENCODING_PCM_16BIT -> {
                val shorts = pcm.asShortBuffer()
                val bytes = ByteBuffer.allocate(shorts.remaining() * 4).order(ByteOrder.LITTLE_ENDIAN)
                while (shorts.hasRemaining()) bytes.putFloat(shorts.get() / 32768f)
                out.write(bytes.array(), 0, bytes.position())
                bytes.position()
            }
            else -> throw Unavailable("The decoder output format ($encoding) is not supported")
        }
    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback
}
