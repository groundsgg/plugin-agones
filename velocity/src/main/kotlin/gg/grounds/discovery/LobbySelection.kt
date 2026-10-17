package gg.grounds.discovery

/**
 * Which lobby a joining player should land on.
 *
 * Fullest first, below a per-lobby cap. A network of 50 players then feels like one lobby of 50
 * rather than fifty lobbies of one, and only when every lobby is at the cap does a join go to an
 * emptier one. The fleet keeps one empty lobby Ready on top of the allocated ones, so "an emptier
 * one" is normally that spare: it takes the overflow, fills, and the autoscaler starts the next.
 *
 * This replaces least-occupied-first. Together with that Ready buffer it put every player in a
 * lobby of their own: the spare was always the least occupied, the first join allocated it, the
 * autoscaler started another empty one, and the next join took that.
 *
 * When every lobby is at the cap the least occupied one takes the join, so the overflow spreads
 * instead of piling onto one lobby while the spare is still starting.
 *
 * Ties break on the name so that every proxy, working from the same counts, makes the same choice.
 *
 * An outdated lobby — one a Fleet rollout has already replaced, see [DiscoveryService.isOutdated] —
 * is only picked when no current lobby exists. Sending joins there would keep it allocated, and an
 * allocated GameServer is the one thing a rollout never deletes. Falling back to it instead of
 * picking nothing keeps a fleet that is scaled to 0 on purpose (every lobby outdated) joinable.
 */
object LobbySelection {

    const val DEFAULT_PLAYER_CAP = 100

    data class Candidate(val name: String, val players: Int, val outdated: Boolean = false)

    fun pick(candidates: List<Candidate>, cap: Int = DEFAULT_PLAYER_CAP): String? {
        val pool = candidates.filter { !it.outdated }.ifEmpty { candidates }
        val withRoom = pool.filter { it.players < cap }
        val chosen =
            if (withRoom.isNotEmpty()) {
                withRoom.minWithOrNull(compareBy({ -it.players }, { it.name }))
            } else {
                pool.minWithOrNull(compareBy({ it.players }, { it.name }))
            }
        return chosen?.name
    }
}
