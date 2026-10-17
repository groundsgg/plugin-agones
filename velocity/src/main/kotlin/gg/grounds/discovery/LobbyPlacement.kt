package gg.grounds.discovery

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import java.time.Duration
import java.util.UUID

/**
 * Picks a lobby and remembers the pick until the player is on it.
 *
 * The counts a pick works from lag the joins it makes: a player this proxy just sent to a lobby is
 * not in Velocity's `playersConnected` until the connection completes, and not in the network count
 * until their session says so and the cache has refreshed. Without a memory of its own picks, a
 * burst of joins would all see the same 99 and all go to the same lobby.
 *
 * So each pick reserves a place for the player, and a candidate's count is its observed count plus
 * the reservations still open on it. The pick and the reservation happen under one lock, which
 * makes joins on this proxy exact: the 101st sees 100 and goes elsewhere. A reservation ends when
 * the player reaches a server (they are then in `playersConnected`), leaves the proxy, or after
 * [ttl] — a connection that fails quietly must not hold a place for ever.
 *
 * Joins on *another* proxy are only seen through the network count, a couple of seconds late, so
 * the cap is soft across proxies: two proxies filling the same lobby at once can overshoot it by
 * the joins each makes in that window. That costs a few players over 100, not a lobby of 200.
 */
class LobbyPlacement(
    private val cap: Int = LobbySelection.DEFAULT_PLAYER_CAP,
    private val ttl: Duration = DEFAULT_TTL,
    private val clock: () -> Long = System::nanoTime,
) {
    private data class Reservation(val server: String, val expiresAt: Long)

    private val reservations = HashMap<UUID, Reservation>()

    /**
     * The lobby for [playerId], never [exclude]; null when there is none. [observed] are the
     * lobbies with the players already counted on them. A null [playerId] only asks — nothing is
     * reserved.
     */
    @Synchronized
    fun place(
        observed: List<LobbySelection.Candidate>,
        playerId: UUID? = null,
        exclude: String? = null,
    ): String? {
        // A player placed again (kicked before arriving, say) must not count against themselves.
        if (playerId != null) reservations.remove(playerId)
        val candidates =
            withReservations(observed).filter {
                exclude == null || canonicalServerName(it.name) != canonicalServerName(exclude)
            }
        val chosen = LobbySelection.pick(candidates, cap) ?: return null
        if (playerId != null) {
            reservations[playerId] =
                Reservation(canonicalServerName(chosen), clock() + ttl.toNanos())
        }
        return chosen
    }

    /** [observed] with this proxy's open reservations added to each lobby's count. */
    @Synchronized
    fun withReservations(observed: List<LobbySelection.Candidate>): List<LobbySelection.Candidate> {
        val now = clock()
        reservations.values.removeIf { it.expiresAt - now <= 0 }
        val pending = reservations.values.groupingBy { it.server }.eachCount()
        return observed.map {
            it.copy(players = it.players + (pending[canonicalServerName(it.name)] ?: 0))
        }
    }

    @Synchronized
    fun release(playerId: UUID) {
        reservations.remove(playerId)
    }

    /** Ends a reservation once its player is on a server, or gone. */
    inner class Listener {
        // Post-connect, not ServerConnectedEvent: Velocity adds the player to the server's
        // `playersConnected` after that event, and releasing first would let the next pick see the
        // lobby one player short.
        @Subscribe
        fun onServerPostConnect(event: ServerPostConnectEvent) {
            release(event.player.uniqueId)
        }

        @Subscribe
        fun onDisconnect(event: DisconnectEvent) {
            release(event.player.uniqueId)
        }
    }

    companion object {
        /** Long enough for a slow join, short enough that a lost one frees its place quickly. */
        val DEFAULT_TTL: Duration = Duration.ofSeconds(15)
    }
}
