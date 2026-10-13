package com.rotatsulabs.specty.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

/**
 * Raw interleaved 32-bit float little-endian PCM on disk (what FFmpeg writes with `-f f32le`).
 * Supports sequential chunked reads and random-access frame reads.
 */
class PcmFile(val file: File, val channels: Int, val sampleRate: Int) : Closeable {
    val frames: Long = file.length() / (4L * channels)

    private var raf: RandomAccessFile? = null
    private var scratch: ByteBuffer? = null

    /** Calls [block] with interleaved samples; the array is reused between calls. */
    fun forEachChunk(framesPerChunk: Int = 1 shl 16, block: (samples: FloatArray, frames: Int) -> Unit) {
        RandomAccessFile(file, "r").use { handle ->
            val channel = handle.channel
            val bytes = ByteBuffer.allocate(framesPerChunk * channels * 4).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(framesPerChunk * channels)
            while (true) {
                bytes.clear()
                var total = 0
                while (bytes.hasRemaining()) {
                    val n = channel.read(bytes)
                    if (n < 0) break
                    total += n
                }
                val got = total / (4 * channels)
                if (got == 0) break
                bytes.flip()
                bytes.asFloatBuffer().get(floats, 0, got * channels)
                block(floats, got)
                if (got < framesPerChunk) break
            }
        }
    }

    /**
     * Reads [count] frames starting at [startFrame] into [out] (interleaved), zero-padding past EOF.
     * Returns the number of real frames read.
     */
    fun readFrames(startFrame: Long, count: Int, out: FloatArray): Int {
        val needBytes = count * channels * 4
        var buf = scratch
        if (buf == null || buf.capacity() < needBytes) {
            buf = ByteBuffer.allocate(needBytes).order(ByteOrder.LITTLE_ENDIAN)
            scratch = buf
        }
        val handle = raf ?: RandomAccessFile(file, "r").also { raf = it }
        val channel = handle.channel
        buf.clear()
        buf.limit(needBytes)
        val position = startFrame * channels * 4L
        var total = 0
        while (buf.hasRemaining()) {
            val n = channel.read(buf, position + total)
            if (n < 0) break
            total += n
        }
        val got = total / (4 * channels)
        buf.flip()
        buf.asFloatBuffer().get(out, 0, got * channels)
        Arrays.fill(out, got * channels, count * channels, 0f)
        return got
    }

    override fun close() {
        raf?.close()
        raf = null
    }
}
