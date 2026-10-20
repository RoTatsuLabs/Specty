package com.rotatsulabs.specty.batch

/**
 * How long a scan rests after each analysed file. A cool phone gets a short breather, a warming one the old pause, and
 * a hot one a longer pause well before the scan has to stop. The heat wait in the service still comes first.
 */
object Cooldown {
    const val COOL_MS = 50L
    const val WARM_MS = 150L
    const val HOT_MS = 600L

    /** [thermalStatus] is PowerManager's: 0 none, 1 light, 2 moderate and up. Null when the phone does not report it. */
    fun after(thermalStatus: Int?): Long = when {
        thermalStatus == null -> WARM_MS
        thermalStatus <= 0 -> COOL_MS
        thermalStatus == 1 -> WARM_MS
        else -> HOT_MS
    }
}
