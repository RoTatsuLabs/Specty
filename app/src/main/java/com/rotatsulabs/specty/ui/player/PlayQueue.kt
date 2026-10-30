package com.rotatsulabs.specty.ui.player

import kotlin.random.Random

/** The tracks lined up to play and which one is playing. Immutable: every move returns a new queue. */
class PlayQueue<T>(val items: List<T>, val index: Int) {
    init {
        require(items.isNotEmpty() && index in items.indices) { "index $index outside ${items.size} items" }
    }

    val current: T get() = items[index]

    /** The queue one track on, or null after the last track. */
    fun next(): PlayQueue<T>? = if (index + 1 < items.size) PlayQueue(items, index + 1) else null

    /** The queue one track back, or null before the first track. */
    fun previous(): PlayQueue<T>? = if (index > 0) PlayQueue(items, index - 1) else null

    /** The same tracks in random order with the playing one first, so shuffling never skips what is on. */
    fun shuffled(random: Random): PlayQueue<T> {
        val rest = items.filterIndexed { i, _ -> i != index }.shuffled(random)
        return PlayQueue(listOf(current) + rest, 0)
    }

    companion object {
        /** A queue over [items] starting at [start], or null when there is nothing to play. */
        fun <T> of(items: List<T>, start: Int = 0): PlayQueue<T>? =
            if (items.isEmpty()) null else PlayQueue(items, start.coerceIn(items.indices))
    }
}
