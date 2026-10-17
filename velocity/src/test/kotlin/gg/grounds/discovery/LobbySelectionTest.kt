package gg.grounds.discovery

import gg.grounds.discovery.LobbySelection.Candidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LobbySelectionTest {

    @Test
    fun `joins go to the fullest lobby below the cap`() {
        val chosen =
            LobbySelection.pick(
                listOf(Candidate("lobby-a", 12), Candidate("lobby-b", 99), Candidate("lobby-c", 3))
            )
        assertEquals("lobby-b", chosen)
    }

    @Test
    fun `a lobby at the cap takes no more joins`() {
        val chosen =
            LobbySelection.pick(listOf(Candidate("lobby-a", 100), Candidate("lobby-b", 40)))
        assertEquals("lobby-b", chosen)
    }

    @Test
    fun `the empty spare only takes joins once every other lobby is full`() {
        val partly =
            LobbySelection.pick(listOf(Candidate("lobby-a", 1), Candidate("lobby-spare", 0)))
        val full =
            LobbySelection.pick(listOf(Candidate("lobby-a", 100), Candidate("lobby-spare", 0)))

        assertEquals("lobby-a", partly)
        assertEquals("lobby-spare", full)
    }

    @Test
    fun `when every lobby is full the least occupied takes the overflow`() {
        val chosen =
            LobbySelection.pick(
                listOf(
                    Candidate("lobby-a", 103),
                    Candidate("lobby-b", 100),
                    Candidate("lobby-c", 101),
                )
            )
        assertEquals("lobby-b", chosen)
    }

    @Test
    fun `repeated picks fill one lobby before the next`() {
        val counts = linkedMapOf("lobby-a" to 0, "lobby-b" to 0)
        val picks =
            (1..6).map {
                val chosen =
                    LobbySelection.pick(counts.map { Candidate(it.key, it.value) }, cap = 3)!!
                counts[chosen] = counts.getValue(chosen) + 1
                chosen
            }
        assertEquals(List(3) { "lobby-a" } + List(3) { "lobby-b" }, picks)
    }

    @Test
    fun `ties break on the name so every proxy makes the same choice`() {
        assertEquals(
            "lobby-a",
            LobbySelection.pick(listOf(Candidate("lobby-b", 50), Candidate("lobby-a", 50))),
        )
        assertEquals(
            "lobby-a",
            LobbySelection.pick(listOf(Candidate("lobby-b", 100), Candidate("lobby-a", 100))),
        )
    }

    @Test
    fun `the cap is configurable`() {
        val candidates = listOf(Candidate("lobby-a", 20), Candidate("lobby-b", 5))

        assertEquals("lobby-a", LobbySelection.pick(candidates, cap = 21))
        assertEquals("lobby-b", LobbySelection.pick(candidates, cap = 20))
    }

    @Test
    fun `no candidates means no lobby`() {
        assertNull(LobbySelection.pick(emptyList()))
    }

    @Test
    fun `an outdated lobby takes no joins while a current one exists`() {
        val chosen =
            LobbySelection.pick(
                listOf(Candidate("lobby-old", 60, outdated = true), Candidate("lobby-new", 4))
            )
        assertEquals("lobby-new", chosen)
    }

    @Test
    fun `outdated lobbies still take joins when no current lobby exists`() {
        val chosen =
            LobbySelection.pick(
                listOf(
                    Candidate("lobby-a", 9, outdated = true),
                    Candidate("lobby-b", 2, outdated = true),
                )
            )
        assertEquals("lobby-a", chosen)
    }
}
