package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageGuardTest {
    private val mb = 1024L * 1024
    private val gb = 1024 * mb

    private fun pcm(seconds: Int, rate: Int, channels: Int) = seconds.toLong() * rate * channels * 4

    @Test
    fun anOrdinaryTrackDecodesWithRoomAboveItsEstimate() {
        val estimate = pcm(240, 44100, 2)
        val limit = StorageGuard.decodeLimit(estimate, 20 * gb)!!
        assertTrue(limit > estimate)
        assertTrue(limit <= StorageGuard.MAX_DECODE_BYTES)
    }

    @Test
    fun aThreeHourMixIsRefusedWhateverTheFreeSpace() {
        val estimate = pcm(3 * 3600, 96000, 2)
        assertTrue(estimate > StorageGuard.MAX_DECODE_BYTES)
        assertNull(StorageGuard.decodeLimit(estimate, 200 * gb))
    }

    @Test
    fun aDecodeNeverTakesTheLastOfTheFreeSpace() {
        for (usable in listOf(0L, 100 * mb, 600 * mb, 1 * gb, 3 * gb, 50 * gb)) {
            for (estimate in listOf(0L, 50 * mb, 400 * mb, 1 * gb, 4 * gb)) {
                val limit = StorageGuard.decodeLimit(estimate, usable) ?: continue
                assertTrue("usable=$usable estimate=$estimate", usable - limit >= StorageGuard.MIN_FREE_BYTES)
            }
        }
    }

    @Test
    fun aNearlyFullPhoneRefusesEveryDecode() {
        assertNull(StorageGuard.decodeLimit(10 * mb, StorageGuard.MIN_FREE_BYTES))
        assertNull(StorageGuard.decodeLimit(0, 100 * mb))
    }

    @Test
    fun aFileThatDoesNotFitIsRefused() {
        assertNull(StorageGuard.decodeLimit(1 * gb, StorageGuard.MIN_FREE_BYTES + 500 * mb))
        assertNotNull(StorageGuard.decodeLimit(400 * mb, StorageGuard.MIN_FREE_BYTES + 500 * mb))
    }

    @Test
    fun aFileWithNoKnownLengthIsStillCapped() {
        assertEquals(StorageGuard.UNKNOWN_LENGTH_BYTES, StorageGuard.decodeLimit(0, 100 * gb))
        assertEquals(300 * mb, StorageGuard.decodeLimit(0, StorageGuard.MIN_FREE_BYTES + 300 * mb))
    }

    @Test
    fun theRefusalSaysWhy() {
        val full = StorageGuard.refusal(10 * mb, 100 * mb)
        val long = StorageGuard.refusal(4 * gb, 200 * gb)
        val tight = StorageGuard.refusal(1 * gb, StorageGuard.MIN_FREE_BYTES + 500 * mb)
        assertTrue(full.contains("almost full"))
        assertTrue(long.contains("too long"))
        assertTrue(tight.contains("Not enough free storage"))
    }
}
