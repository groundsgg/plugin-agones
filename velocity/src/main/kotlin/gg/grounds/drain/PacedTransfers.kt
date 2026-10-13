package gg.grounds.drain

/**
 * Hands players to [transfer] at most [perSecond] at a time: one batch when [tick] is first called,
 * the next on every later call (the caller ticks once a second).
 *
 * A drain used to send every transfer packet in the same instant, so a full proxy meant hundreds of
 * clients reconnecting in one second: hundreds of logins, session checks against Mojang and service
 * lookups landing on the replacement proxy at once. Paced, 400 players at 20/s take 20 s, well
 * inside the drain deadline.
 *
 * A player is checked again when their turn comes ([stillDue]): one who left, or walked into a
 * round while queued, is skipped and does not use up a slot of the batch. A transfer that throws is
 * reported to [onFailure] and does not stop the rest.
 */
internal class PacedTransfers<T>(
    players: Iterable<T>,
    private val perSecond: Int,
    private val stillDue: (T) -> Boolean,
    private val transfer: (T) -> Unit,
    private val onFailure: (T, Exception) -> Unit,
) {
    init {
        require(perSecond >= 1) { "perSecond must be at least 1, was $perSecond" }
    }

    private val queue = ArrayDeque(players.toList())

    val remaining: Int
        @Synchronized get() = queue.size

    /** Sends the next batch. True once nobody is left to send. */
    @Synchronized
    fun tick(): Boolean {
        var sent = 0
        while (sent < perSecond && queue.isNotEmpty()) {
            val player = queue.removeFirst()
            if (!stillDue(player)) continue
            try {
                transfer(player)
            } catch (error: Exception) {
                onFailure(player, error)
            }
            sent++
        }
        return queue.isEmpty()
    }
}

/** Runs a tick once a second until it reports it is done. Injected so tests can tick by hand. */
fun interface DrainTicker {
    fun everySecondUntilDone(tick: () -> Boolean)
}
