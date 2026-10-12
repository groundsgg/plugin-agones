package gg.grounds.discovery

import gg.grounds.discovery.LobbySelection.Candidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutdatedLobbyMoverTest {

    @Test
    fun `only players on an outdated lobby are moved, to a current one`() {
        val moves =
            planOutdatedLobbyMoves(
                listOf("alex" to "lobby-old", "sam" to "lobby-new", "kim" to "duel-x1"),
                listOf(Candidate("lobby-old", 1, outdated = true), Candidate("lobby-new", 1)),
                limit = 10,
            )

        assertEquals(listOf("alex" to "lobby-new"), moves)
    }

    @Test
    fun `nobody moves while no current lobby exists`() {
        val moves =
            planOutdatedLobbyMoves(
                listOf("alex" to "lobby-old"),
                listOf(
                    Candidate("lobby-old", 1, outdated = true),
                    Candidate("lobby-older", 0, outdated = true),
                ),
                limit = 10,
            )

        assertTrue(moves.isEmpty())
    }

    @Test
    fun `nobody moves while no lobby is outdated`() {
        val moves =
            planOutdatedLobbyMoves(
                listOf("alex" to "lobby-a"),
                listOf(Candidate("lobby-a", 1), Candidate("lobby-b", 0)),
                limit = 10,
            )

        assertTrue(moves.isEmpty())
    }

    @Test
    fun `a tick moves at most the limit`() {
        val players = (1..25).map { "p$it" to "lobby-old" }
        val moves =
            planOutdatedLobbyMoves(
                players,
                listOf(Candidate("lobby-old", 25, outdated = true), Candidate("lobby-new", 0)),
                limit = 10,
            )

        assertEquals(players.take(10).map { it.first }, moves.map { it.first })
    }

    @Test
    fun `one tick spreads its moves across the current lobbies`() {
        val moves =
            planOutdatedLobbyMoves(
                (1..6).map { "p$it" to "lobby-old" },
                listOf(
                    Candidate("lobby-old", 6, outdated = true),
                    Candidate("lobby-a", 3),
                    Candidate("lobby-b", 0),
                ),
                limit = 10,
            )

        // 3+0 -> b, b, b, then a and b alternate: both end within one player of each other.
        assertEquals(
            mapOf("lobby-a" to 2, "lobby-b" to 4),
            moves.groupingBy { it.second }.eachCount(),
        )
    }
}
