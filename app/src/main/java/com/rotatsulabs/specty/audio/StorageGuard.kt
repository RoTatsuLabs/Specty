package com.rotatsulabs.specty.audio

/**
 * Decides how much temporary PCM a decode may write. Decoded audio is far bigger than the file (a 3 hour mix is
 * several GB), and a nearly full phone becomes unstable, so a decode never takes the last of the free space.
 */
object StorageGuard {
    /** Most PCM written for one file. */
    const val MAX_DECODE_BYTES = 2L * 1024 * 1024 * 1024

    /** Free space left alone for the system and other apps. */
    const val MIN_FREE_BYTES = 512L * 1024 * 1024

    /** Cap for a file whose header gives no length. */
    const val UNKNOWN_LENGTH_BYTES = 1L shl 30

    private const val SLACK_BYTES = 64L * 1024 * 1024

    /**
     * The byte limit for the decode, or null when it cannot be done safely.
     * [estimatedBytes] is what the header implies (0 when unknown); [usableBytes] is the free space.
     */
    fun decodeLimit(estimatedBytes: Long, usableBytes: Long): Long? {
        val available = usableBytes - MIN_FREE_BYTES
        if (available <= 0) return null
        if (estimatedBytes <= 0) return minOf(UNKNOWN_LENGTH_BYTES, available)
        if (estimatedBytes > MAX_DECODE_BYTES || estimatedBytes > available) return null
        // A header can understate the length, so allow some room above the estimate, never past the caps.
        return minOf(estimatedBytes * 2 + SLACK_BYTES, MAX_DECODE_BYTES, available)
    }

    fun refusal(estimatedBytes: Long, usableBytes: Long): String = when {
        usableBytes - MIN_FREE_BYTES <= 0 -> "Storage is almost full. Free some space and try again."
        estimatedBytes > MAX_DECODE_BYTES -> "This file is too long to analyse (about ${estimatedBytes / 1_000_000} MB of audio)."
        else -> "Not enough free storage to decode this file (about ${estimatedBytes / 1_000_000} MB needed)."
    }
}
