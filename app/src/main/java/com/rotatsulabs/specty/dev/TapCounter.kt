package com.rotatsulabs.specty.dev

/** Counts quick repeated taps; a pause longer than [windowMs] starts the count again. */
class TapCounter(private val needed: Int = 8, private val windowMs: Long = 2_000, private val hintFrom: Int = 3) {
    sealed interface Result {
        data object Idle : Result
        data class Remaining(val taps: Int) : Result
        data object Unlocked : Result
    }

    private var count = 0
    private var last = Long.MIN_VALUE

    fun tap(nowMs: Long): Result {
        count = if (last != Long.MIN_VALUE && nowMs - last > windowMs) 1 else count + 1
        last = nowMs
        return when {
            count >= needed -> { count = 0; last = Long.MIN_VALUE; Result.Unlocked }
            count >= hintFrom -> Result.Remaining(needed - count)
            else -> Result.Idle
        }
    }
}
