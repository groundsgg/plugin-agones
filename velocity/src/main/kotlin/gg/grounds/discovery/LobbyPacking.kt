package gg.grounds.discovery

/**
 * Which lobby a joining player should land on.
 *
 * Fullest-first, below a soft cap: players go to the most-occupied lobby that still has room, so
 * lobbies fill one after another instead of every join being sprayed across all of them — a network
 * with 50 players should feel like one lobby with 50 people, not five with 10. Only when every
 * lobby is at or over the cap does the choice flip to the least-occupied one, spreading the
 * overflow evenly.
 *
 * The cap is soft on purpose: counts are a snapshot, joins race each other, and a lobby briefly at
 * 403/400 is fine. Ties break on the name so that every proxy, working from the same counts, packs
 * the same lobby.
 */
object LobbyPacking {

    data class Candidate(val name: String, val players: Int)

    fun pick(candidates: List<Candidate>, softCap: Int): String? {
        if (candidates.isEmpty()) return null
        val fullestFirst =
            candidates.sortedWith(compareByDescending<Candidate> { it.players }.thenBy { it.name })
        val belowCap = fullestFirst.firstOrNull { it.players < softCap }
        if (belowCap != null) return belowCap.name
        return candidates.sortedWith(compareBy({ it.players }, { it.name })).first().name
    }
}
