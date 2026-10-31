package gg.grounds.drain

/** A bounded-per-tick, deduplicated queue. A refused handoff remains at its source. */
internal class RetryingTransfers<T, K>(
    private val perSecond: Int,
    private val key: (T) -> K,
    private val stillDue: (T) -> Boolean,
    private val transfer: (T) -> Boolean,
) {
    private val queue = LinkedHashMap<K, T>()

    init {
        require(perSecond >= 1)
    }

    val remaining: Int
        @Synchronized get() = queue.size

    @Synchronized
    fun offer(player: T) {
        queue.putIfAbsent(key(player), player)
    }

    @Synchronized
    fun tick(): Boolean {
        // Snapshot also prevents the one refused entry from spinning in the same tick.
        val batch = queue.entries.take(perSecond).map { it.key to it.value }
        for ((id, player) in batch) {
            if (!stillDue(player) || transfer(player)) queue.remove(id)
        }
        return queue.isEmpty()
    }
}
