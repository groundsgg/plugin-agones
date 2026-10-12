package gg.grounds.discovery

/**
 * Which lobby a joining player should land on.
 *
 * Least-occupied wins. Joins spread across the lobbies instead of filling one and then the next, so
 * no single instance holds the whole region: a lobby restart takes a share of the players with it
 * rather than all of them.
 *
 * This also gives a newly autoscaled lobby what it needs without any special case. A fresh lobby is
 * empty, so it is the least occupied, so it takes joins until it has caught up with the others —
 * priority filling falls out of the same rule that does the spreading.
 *
 * This replaces a fullest-first-below-a-cap policy. That one existed so a network of 50 would feel
 * like one lobby of 50 rather than five of ten, which is a real concern — but it meant that in
 * practice every player in a region sat in one process, and measured on stage the lobby was never
 * the reason to split: 228 players cost 0.29 cores and a 2 ms average tick against a 50 ms budget.
 * The reason to split is blast radius, and that argues for spreading always rather than for a
 * threshold nobody ever reached.
 *
 * Ties break on the name so that every proxy, working from the same counts, makes the same choice.
 *
 * An outdated lobby — one a Fleet rollout has already replaced, see [DiscoveryService.isOutdated] —
 * is only picked when no current lobby exists. Sending joins there would keep it allocated, and an
 * allocated GameServer is the one thing a rollout never deletes. Falling back to it instead of
 * picking nothing keeps a fleet that is scaled to 0 on purpose (every lobby outdated) joinable.
 */
object LobbySelection {

    data class Candidate(val name: String, val players: Int, val outdated: Boolean = false)

    fun pick(candidates: List<Candidate>): String? =
        candidates
            .filter { !it.outdated }
            .ifEmpty { candidates }
            .minWithOrNull(compareBy({ it.players }, { it.name }))
            ?.name
}
