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
 */
object LobbySelection {

    data class Candidate(val name: String, val players: Int)

    fun pick(candidates: List<Candidate>): String? =
        candidates.minWithOrNull(compareBy({ it.players }, { it.name }))?.name
}
