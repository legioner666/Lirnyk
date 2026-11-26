package app.lirnyk.core.model

enum class RepeatMode { OFF, ALL, ONE }

/**
 * The playing order, independent of the player engine so it can be saved and tested. [order] is the play order over
 * [ids] (identity when shuffle is off); [position] points into [order].
 */
data class PlayQueue(
    val ids: List<String>,
    val order: List<Int>,
    val position: Int,
    val shuffle: Boolean,
    val repeat: RepeatMode,
) {
    init {
        require(order.size == ids.size) { "order and ids differ in size" }
        require(ids.isEmpty() || position in order.indices) { "position $position outside 0..${order.size - 1}" }
    }

    val currentId: String? get() = if (ids.isEmpty()) null else ids[order[position]]
    val isEmpty: Boolean get() = ids.isEmpty()

    /** Next position, or null at the end with repeat off. [userAction] = the next button (repeat ONE still moves on). */
    fun next(userAction: Boolean = true, available: (String) -> Boolean = { true }): PlayQueue? {
        if (ids.isEmpty()) return null
        if (!userAction && repeat == RepeatMode.ONE) return this
        var p = position
        repeat(order.size) {
            p += 1
            if (p >= order.size) { if (repeat == RepeatMode.OFF) return null; p = 0 }
            if (available(ids[order[p]])) return copy(position = p)
        }
        return null
    }

    fun previous(available: (String) -> Boolean = { true }): PlayQueue? {
        if (ids.isEmpty()) return null
        var p = position
        repeat(order.size) {
            p -= 1
            if (p < 0) { if (repeat == RepeatMode.OFF) return null; p = order.size - 1 }
            if (available(ids[order[p]])) return copy(position = p)
        }
        return null
    }

    /** Turns shuffle on or off keeping the current track playing; the rest is reshuffled with [random]. */
    fun withShuffle(on: Boolean, random: kotlin.random.Random = kotlin.random.Random.Default): PlayQueue {
        if (on == shuffle || ids.isEmpty()) return copy(shuffle = on)
        val current = order[position]
        return if (on) {
            val rest = ids.indices.filter { it != current }.shuffled(random)
            copy(order = listOf(current) + rest, position = 0, shuffle = true)
        } else {
            copy(order = ids.indices.toList(), position = current, shuffle = false)
        }
    }

    fun withRepeat(mode: RepeatMode) = copy(repeat = mode)

    /** Jumps to the track with [id]; unchanged when it is not in the queue. */
    fun jumpTo(id: String): PlayQueue {
        val idx = ids.indexOf(id)
        if (idx < 0) return this
        return copy(position = order.indexOf(idx))
    }

    /** Ids in play order, for handing to the player. */
    fun orderedIds(): List<String> = order.map { ids[it] }

    companion object {
        val EMPTY = PlayQueue(emptyList(), emptyList(), 0, false, RepeatMode.OFF)

        fun of(ids: List<String>, startId: String? = null, shuffle: Boolean = false, repeat: RepeatMode = RepeatMode.OFF,
               random: kotlin.random.Random = kotlin.random.Random.Default): PlayQueue {
            if (ids.isEmpty()) return EMPTY.copy(shuffle = shuffle, repeat = repeat)
            val start = startId?.let { ids.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            val q = PlayQueue(ids, ids.indices.toList(), start, false, repeat)
            return if (shuffle) q.withShuffle(true, random) else q
        }
    }
}
