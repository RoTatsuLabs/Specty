package com.rotatsulabs.specty.audio

import java.io.IOException
import java.io.InputStream

/**
 * Detects Dolby Atmos in E-AC-3 tracks stored in MP4 / M4A. Atmos (JOC) is flagged in the `dec3` box
 * (`flag_ec3_extension_type_a`), which FFprobe does not surface as a profile.
 */
object AtmosProbe {
    private const val MAX_MOOV = 32 * 1024 * 1024L

    /** [open] returns a fresh stream at offset 0; boxes are read in order until `moov`. */
    fun isAtmos(open: () -> InputStream): Boolean = try {
        open().use { findMoov(it)?.let(::hasJoc) ?: false }
    } catch (_: IOException) {
        false
    }

    private fun findMoov(input: InputStream): ByteArray? {
        val header = ByteArray(16)
        while (true) {
            if (!readFully(input, header, 8)) return null
            var size = u32(header, 0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerLen = 8L
            if (size == 1L) {
                if (!readFully(input, header, 8)) return null
                size = (u32(header, 0) shl 32) or u32(header, 4)
                headerLen = 16L
            }
            if (size == 0L) return null
            val payload = size - headerLen
            if (payload < 0) return null
            if (type == "moov") {
                if (payload > MAX_MOOV) return null
                val buf = ByteArray(payload.toInt())
                return if (readFully(input, buf, buf.size)) buf else null
            }
            if (!skipFully(input, payload)) return null
        }
    }

    private fun hasJoc(moov: ByteArray): Boolean {
        var i = 4
        while (i + 4 <= moov.size) {
            if (moov[i] == 'd'.code.toByte() && moov[i + 1] == 'e'.code.toByte() &&
                moov[i + 2] == 'c'.code.toByte() && moov[i + 3] == '3'.code.toByte()
            ) {
                val size = u32(moov, i - 4).toInt()
                val start = i + 4
                val end = (i - 4 + size).coerceAtMost(moov.size)
                if (size >= 12 && end > start && parseJoc(moov, start, end)) return true
            }
            i++
        }
        return false
    }

    /** Walks the dec3 payload: 13-bit data rate, 3-bit substream count, per-substream fields, then the extension. */
    private fun parseJoc(b: ByteArray, start: Int, end: Int): Boolean {
        val bits = (end - start) * 8
        var pos = 13
        fun read(n: Int): Int {
            var v = 0
            repeat(n) {
                val byte = b[start + (pos shr 3)].toInt() and 0xFF
                v = (v shl 1) or ((byte shr (7 - (pos and 7))) and 1)
                pos++
            }
            return v
        }
        val substreams = read(3) + 1
        repeat(substreams) {
            if (pos + 23 > bits) return false
            pos += 19 // fscod, bsid, reserved, asvc, bsmod, acmod, lfeon, reserved
            val dependent = read(4)
            if (pos + (if (dependent > 0) 9 else 1) > bits) return false
            pos += if (dependent > 0) 9 else 1
        }
        if (pos + 16 > bits) return false
        pos += 7
        return read(1) == 1
    }

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun readFully(input: InputStream, buf: ByteArray, len: Int): Boolean {
        var n = 0
        while (n < len) {
            val r = input.read(buf, n, len - n)
            if (r < 0) return false
            n += r
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long): Boolean {
        var left = count
        val sink = ByteArray(8192)
        while (left > 0) {
            val s = input.skip(left)
            if (s > 0) {
                left -= s
            } else {
                val r = input.read(sink, 0, minOf(left, sink.size.toLong()).toInt())
                if (r < 0) return false
                left -= r
            }
        }
        return true
    }
}
